

问题：

deepseek的api虽然名义上兼容openai，但并不是完全兼容的，最主要的差异点在于deepseek的api要求tool调用的reasoning_content 必须回传，但android studio自带的agent请求时会忽略reasoning_content，导致一旦涉及到工具调用就会出现下面的错误

Please try again later.

Error: com.openai.errors.BadRequestException: 400: The reasoning_content in the thinking mode must be passed back to the API.

虽然有很多插件能够在android studio里面接入deepseek，但作为一个有洁癖的人忍不了，决定尝试解决一下。

解决方案：

问题的原因很明显，解决的思路也很清晰，如果能够截获agent的请求，再封装成deepseek api要求的形态是不是问题就能解决呢？

经过两天的调试，最终的deepseek_proxy.py代理脚本OK了
脚本内容如下：

from flask import Flask, request, Response, stream_with_context
import requests
import json
import logging
import os
import threading

LOG_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "deepseek_proxy.log")
logging.basicConfig(
filename=LOG_FILE,
level=logging.DEBUG,
format="%(asctime)s [%(levelname)s] %(message)s",
force=True,
)

log = logging.getLogger("proxy")
http://log.info("--- Proxy started ---")

app = Flask(__name__)
DEEPSEEK_BASE = "https://api.deepseek.com"

upstream_kv_hit = 0
upstream_kv_miss = 0

reasoning_cache = {}
reasoning_cache_hits = 0
reasoning_cache_misses = 0


def _msg_signature(msg):
tcs = msg.get("tool_calls")
if tcs and isinstance(tcs, list):
ids = tuple(sorted(tc.get("id", "") for tc in tcs if isinstance(tc, dict) and tc.get("id")))
if ids:
content = msg.get("content", "")
return ("tool", content, ids)
return None


def _cache_reasoning(msg):
sig = _msg_signature(msg)
rc = msg.get("reasoning_content")
if sig and rc:
reasoning_cache[sig] = rc
return True
return False


def _inject_reasoning_content(messages):
global reasoning_cache_hits, reasoning_cache_misses
for msg in messages:
sig = _msg_signature(msg)
if sig and "reasoning_content" not in msg:
cached = reasoning_cache.get(sig)
if cached:
msg["reasoning_content"] = cached
reasoning_cache_hits += 1
log.debug("Injected cached reasoning_content (hit) for tool_calls assistant message")
else:
reasoning_cache_misses += 1
log.debug("Cache miss for tool_calls assistant message")


def _enable_thinking(body):
if "thinking" not in body:
body["thinking"] = {"type": "enabled"}
if "reasoning_effort" not in body:
body["reasoning_effort"] = "high"


def _disable_thinking(body):
body["thinking"] = {"type": "disabled"}
body.pop("reasoning_effort", None)


@app.route("/chat/completions", methods=["POST"])
@app.route("/v1/chat/completions", methods=["POST"])
def chat():
body = request.get_json(silent=True) or {}
log.debug("=== REQUEST %s/chat/completions ===", DEEPSEEK_BASE)
log.debug("Original body: %s", json.dumps(body, ensure_ascii=False, indent=2))

_enable_thinking(body)

messages = body.get("messages", [])
_inject_reasoning_content(messages)

log.debug("Modified body: %s", json.dumps(body, ensure_ascii=False, indent=2))

headers = {k: v for k, v in request.headers if k.lower() != "host"}
streaming = body.get("stream", False)

endpoint = f"{DEEPSEEK_BASE}/chat/completions"
log.debug("Calling endpoint: %s", endpoint)
resp = requests.post(
endpoint,
headers=headers, json=body, stream=True
)
log.debug("DeepSeek response status: %d", resp.status_code)

# retry with thinking disabled on reasoning_content 400
if resp.status_code == 400:
err_body = resp.text
if "reasoning_content" in err_body:
log.warning("upstream rejected reasoning_content, retrying with thinking disabled")
resp.close()
_disable_thinking(body)
resp = requests.post(
endpoint,
headers=headers, json=body, stream=True
)
log.debug("Retry response status: %d", resp.status_code)

extra_headers = {}

if resp.status_code != 200:
error_body = resp.text
log.warning("Upstream error (HTTP %d): %s", resp.status_code, error_body)
return Response(error_body, status=resp.status_code,
content_type="application/json",
headers=extra_headers)

def _extract_kv_cache(data_obj):
usage = data_obj.get("usage") if isinstance(data_obj, dict) else None
if usage:
global upstream_kv_hit, upstream_kv_miss
h = usage.get("prompt_cache_hit_tokens", 0)
m = usage.get("prompt_cache_miss_tokens", 0)
if h or m:
upstream_kv_hit += h
upstream_kv_miss += m
return h, m
return None, None

if not streaming:
try:
data = resp.json()
except Exception as e:
log.warning("Failed to parse response JSON: %s", e)
return Response(resp.text, status=resp.status_code,
headers=dict(resp.headers) | extra_headers)

kv_hit, kv_miss = _extract_kv_cache(data)
if kv_hit is not None:
extra_headers["X-Upstream-KV"] = f"hit={kv_hit} miss={kv_miss}"

msg = data.get("choices", [{}])[0].get("message", {})
_cache_reasoning(msg)

print(_fmt_stats(), flush=True)
return Response(json.dumps(data), status=resp.status_code,
content_type="application/json",
headers=extra_headers)
else:
def generate():
chunk_count = 0
reasoning_acc = None
content_acc = None
tool_calls_acc = {}
for chunk in resp.iter_lines():
if chunk:
line = chunk.decode("utf-8", errors="replace")
else:
line = ""
if line.startswith("data: ") and line != "data: [DONE]":
chunk_count += 1
try:
parsed = json.loads(line[6:])
usage = parsed.get("usage")
if usage:
h = usage.get("prompt_cache_hit_tokens", 0)
m = usage.get("prompt_cache_miss_tokens", 0)
if h or m:
global upstream_kv_hit, upstream_kv_miss
upstream_kv_hit += h
upstream_kv_miss += m
delta = parsed.get("choices", [{}])[0].get("delta", {})
rc = delta.get("reasoning_content")
if rc:
reasoning_acc = (reasoning_acc or "") + rc
dc = delta.get("content")
if dc:
content_acc = (content_acc or "") + dc
tcs = delta.get("tool_calls")
if tcs:
for tc in tcs:
idx = tc.get("index")
if idx is not None:
if idx not in tool_calls_acc:
tool_calls_acc[idx] = {}
for key in ("id", "type"):
if key in tc:
tool_calls_acc[idx][key] = tc[key]
fn = tc.get("function")
if fn:
cur_fn = tool_calls_acc[idx].setdefault("function", {})
for fk in ("name", "arguments"):
if fk in fn:
cur_fn[fk] = cur_fn.get(fk, "") + fn[fk]
except Exception as e:
log.warning("Stream parse error: %s", e)
yield line + "\n"

log.debug("Stream ended, total chunks=%d", chunk_count)
if reasoning_acc and tool_calls_acc:
indices = sorted(tool_calls_acc.keys())
ids = [tool_calls_acc[i].get("id", "") for i in indices]
msg = {"content": content_acc or "", "tool_calls": [{"id": tid} for tid in ids], "reasoning_content": reasoning_acc}
_cache_reasoning(msg)
print(_fmt_stats(), flush=True)

return Response(
stream_with_context(generate()),
status=resp.status_code,
headers={k: v for k, v in resp.headers.items()
if k.lower() not in ("content-length",)} | extra_headers
)


@app.route("/<path:path>", methods=["GET", "POST", "PUT", "DELETE"])
def proxy_all(path):
headers = {k: v for k, v in request.headers if k.lower() != "host"}
body = request.get_json(silent=True) if request.method in ("POST", "PUT") else None
endpoint = f"{DEEPSEEK_BASE}/{path}"
log.debug("Calling endpoint: [%s] %s", request.method, endpoint)
resp = requests.request(
request.method, endpoint,
headers=headers, json=body, stream=True
)
if resp.status_code != 200:
error_body = resp.text
log.warning("Upstream error (HTTP %d) on [%s] %s: %s",
resp.status_code, request.method, endpoint, error_body)
return Response(error_body, status=resp.status_code,
content_type="application/json")
return Response(resp.iter_content(), status=resp.status_code, headers=dict(resp.headers))


def _fmt_stats():
rc_total = reasoning_cache_hits + reasoning_cache_misses
rc_ratio = round(reasoning_cache_hits / rc_total, 4) if rc_total else 0
kv_total = upstream_kv_hit + upstream_kv_miss
kv_ratio = round(upstream_kv_hit / kv_total, 4) if kv_total else 0
return (
f"[reasoning_cache] entries={len(reasoning_cache)} hits={reasoning_cache_hits} misses={reasoning_cache_misses} ratio={rc_ratio} "
f"[kv_cache] hit={upstream_kv_hit} miss={upstream_kv_miss} ratio={kv_ratio}"
)


@app.route("/stats", methods=["GET"])
def stats():
rc_total = reasoning_cache_hits + reasoning_cache_misses
rc_ratio = round(reasoning_cache_hits / rc_total, 4) if rc_total else 0
kv_total = upstream_kv_hit + upstream_kv_miss
kv_ratio = round(upstream_kv_hit / kv_total, 4) if kv_total else 0
return Response(json.dumps({
"reasoning_cache": {
"entries": len(reasoning_cache),
"hits": reasoning_cache_hits,
"misses": reasoning_cache_misses,
"hit_ratio": rc_ratio,
},
"upstream_kv_cache": {
"hit_tokens": upstream_kv_hit,
"miss_tokens": upstream_kv_miss,
"hit_ratio": kv_ratio,
},
}, ensure_ascii=False), content_type="application/json")


@app.route("/", methods=["GET", "POST", "PUT", "DELETE"])
def root():
return proxy_all("")


def _periodic_stats():
while True:
threading.Event().wait(30)
print(_fmt_stats(), flush=True)


if __name__ == "__main__":
print(f"Log file: {LOG_FILE}", flush=True)
threading.Thread(target=_periodic_stats, daemon=True).start()
print(_fmt_stats(), flush=True)
http://log.info("Listening on 127.0.0.1:8081")
app.run(host="127.0.0.1", port=8081)



使用方法：

先启动代理脚本，打开一个cmd窗口，输入 python deepseek_proxy.py 回车。