# ADB / Android Skill 能力扫描清单

目标：尽可能枚举当前设备上可以通过 ADB Shell、Shizuku Shell 或系统接口封装成 AI Skill 的能力。

说明：这不是“百分百完整清单”。Android 能力受 ROM、Android 版本、设备型号、root 状态、SELinux、user/userdebug 构建、厂商私有服务影响。本文用于高覆盖率扫描和记录。

---

## 0. 连接与环境确认

```bash
adb version
adb devices -l
adb get-state
adb shell whoami
adb shell id
adb shell getenforce
adb shell uname -a
adb shell getprop ro.build.type
adb shell getprop ro.build.tags
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.brand
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.fingerprint
```

记录字段：

| 项 | 结果 |
|---|---|
| 设备型号 | |
| Android 版本 | |
| SDK | |
| build type | |
| shell 用户 | |
| SELinux | |
| 是否 root | |
| 是否 Shizuku 可用 | |

---

## 1. ADB 主命令能力

```bash
adb help
adb shell help
adb shell toybox
adb shell busybox
adb shell ls /system/bin
adb shell ls /system/xbin
adb shell ls /vendor/bin
adb shell ls /product/bin
adb shell ls /odm/bin
```

重点整理：

| 命令 | 是否存在 | 说明 | 可封装 Skill |
|---|---:|---|---:|
| am | | Activity/广播/服务 | |
| pm | | 包管理 | |
| cmd | | 系统服务命令入口 | |
| dumpsys | | 系统状态查询 | |
| settings | | 设置数据库 | |
| svc | | WiFi/蓝牙/数据/电源等 | |
| input | | 模拟输入 | |
| wm | | 屏幕窗口 | |
| media | | 音量/媒体 | |
| screencap | | 截屏 | |
| screenrecord | | 录屏 | |
| logcat | | 日志 | |
| getprop | | 系统属性 | |
| setprop | | 系统属性写入，通常受限 | |
| appops | | AppOps 权限控制 | |
| content | | ContentProvider 访问 | |

---

## 2. cmd 服务枚举

```bash
adb shell cmd -l
adb shell cmd activity help
adb shell cmd package help
adb shell cmd window help
adb shell cmd wifi help
adb shell cmd bluetooth_manager help
adb shell cmd notification help
adb shell cmd deviceidle help
adb shell cmd jobscheduler help
adb shell cmd appops help
adb shell cmd role help
adb shell cmd shortcut help
adb shell cmd statusbar help
adb shell cmd uimode help
adb shell cmd power help
adb shell cmd battery help
adb shell cmd location help
adb shell cmd sensorservice help
adb shell cmd media_session help
adb shell cmd connectivity help
adb shell cmd netpolicy help
adb shell cmd network_stack help
adb shell cmd stats help
```

记录格式：

| cmd service | help 是否可用 | 关键子命令 | 权限情况 | Skill 价值 |
|---|---:|---|---|---|

---

## 3. Binder Service 枚举

```bash
adb shell service list
adb shell dumpsys -l
adb shell dumpsys activity services
```

重点服务：

| Service | 来源 | 能力方向 | 是否可 dumpsys | 是否可 cmd | Skill 价值 |
|---|---|---|---:|---:|---|
| activity | AMS | 启动/任务/进程 | | | |
| package | PMS | 应用/权限/安装包 | | | |
| window | WMS | 窗口/显示 | | | |
| power | PowerManager | 电源/亮屏/省电 | | | |
| battery | BatteryService | 电池模拟/状态 | | | |
| wifi | WiFi | WiFi 状态/扫描 | | | |
| bluetooth_manager | Bluetooth | 蓝牙 | | | |
| connectivity | 网络 | 网络连接 | | | |
| notification | 通知 | 通知管理 | | | |
| statusbar | 状态栏 | 下拉/折叠 | | | |
| alarm | 闹钟/定时 | alarm 状态 | | | |
| location | 定位 | 定位服务 | | | |
| sensorservice | 传感器 | 传感器列表 | | | |
| media_session | 媒体会话 | 播放控制 | | | |
| audio | 音频 | 音量/音频路由 | | | |
| input | 输入 | 输入设备 | | | |
| display | 显示 | 屏幕信息 | | | |

---

## 4. 系统状态查询类 Skill

### 设备信息

```bash
adb shell getprop
adb shell getprop ro.product.model
adb shell getprop ro.product.cpu.abi
adb shell getprop ro.hardware
adb shell getprop ro.serialno
adb shell cat /proc/cpuinfo
adb shell cat /proc/meminfo
adb shell df -h
adb shell mount
adb shell uptime
adb shell date
```

可封装：

| Skill | 参数 | 底层命令 | 风险 |
|---|---|---|---|
| get_device_info | {} | getprop + uname | 低 |
| get_cpu_info | {} | cat /proc/cpuinfo | 低 |
| get_memory_info | {} | cat /proc/meminfo | 低 |
| get_storage_info | {} | df -h | 低 |
| get_uptime | {} | uptime / cat /proc/uptime | 低 |

### 电池

```bash
adb shell dumpsys battery
adb shell cmd battery help
adb shell cmd battery get level
adb shell cmd battery get status
adb shell cmd battery unplug
adb shell cmd battery reset
```

可封装：

| Skill | 参数 | 底层命令 | 风险 |
|---|---|---|---|
| get_battery_info | {} | dumpsys battery | 低 |
| get_battery_level | {} | cmd battery get level | 低 |
| simulate_battery_unplug | {} | cmd battery unplug | 中，仅测试 |
| reset_battery_simulation | {} | cmd battery reset | 中，仅测试 |

---

## 5. 网络能力

```bash
adb shell ip addr
adb shell ip route
adb shell cat /proc/net/arp
adb shell getprop net.dns1
adb shell getprop net.dns2
adb shell dumpsys connectivity
adb shell dumpsys wifi
adb shell cmd wifi help
adb shell svc wifi enable
adb shell svc wifi disable
adb shell svc data enable
adb shell svc data disable
adb shell ping -c 4 8.8.8.8
```

可封装：

| Skill | 参数 | 底层命令 | 风险 |
|---|---|---|---|
| get_network_info | {} | dumpsys connectivity | 低 |
| get_ip_address | {} | ip addr | 低 |
| get_route_table | {} | ip route | 低 |
| get_dns_info | {} | getprop net.dns1/net.dns2 | 低 |
| ping_host | {"host":"8.8.8.8","count":4} | ping | 低 |
| set_wifi_enabled | {"enabled":true} | svc wifi enable/disable | 中 |
| set_mobile_data_enabled | {"enabled":true} | svc data enable/disable | 中 |

---

## 6. 蓝牙 / NFC / 连接

```bash
adb shell dumpsys bluetooth_manager
adb shell dumpsys bluetooth_manager | head -100
adb shell settings get global bluetooth_on
adb shell svc bluetooth enable
adb shell svc bluetooth disable
adb shell dumpsys nfc
adb shell svc nfc enable
adb shell svc nfc disable
```

可封装：

| Skill | 参数 | 底层命令 | 风险 |
|---|---|---|---|
| get_bluetooth_state | {} | settings get global bluetooth_on | 低 |
| set_bluetooth_enabled | {"enabled":true} | svc bluetooth enable/disable | 中 |
| get_nfc_state | {} | dumpsys nfc | 低 |
| set_nfc_enabled | {"enabled":true} | svc nfc enable/disable | 中，设备相关 |

---

## 7. 显示 / 亮度 / 窗口

```bash
adb shell dumpsys display
adb shell dumpsys window
adb shell wm size
adb shell wm density
adb shell wm overscan
adb shell settings get system screen_brightness
adb shell settings put system screen_brightness 120
adb shell settings get system screen_brightness_mode
adb shell settings put system screen_brightness_mode 0
adb shell cmd window help
```

可封装：

| Skill | 参数 | 底层命令 | 风险 |
|---|---|---|---|
| get_display_info | {} | dumpsys display | 低 |
| get_window_info | {} | dumpsys window | 低 |
| get_screen_size | {} | wm size | 低 |
| get_screen_density | {} | wm density | 低 |
| set_brightness | {"level":1-255} | settings put system screen_brightness | 中 |
| set_auto_brightness | {"enabled":true} | settings put system screen_brightness_mode | 中 |

---

## 8. 音频 / 媒体

```bash
adb shell dumpsys audio
adb shell dumpsys media_session
adb shell media volume --help
adb shell media volume --stream 3 --get
adb shell media volume --stream 3 --set 5
adb shell input keyevent KEYCODE_VOLUME_UP
adb shell input keyevent KEYCODE_VOLUME_DOWN
adb shell input keyevent KEYCODE_VOLUME_MUTE
adb shell input keyevent KEYCODE_MEDIA_PLAY_PAUSE
adb shell input keyevent KEYCODE_MEDIA_NEXT
adb shell input keyevent KEYCODE_MEDIA_PREVIOUS
```

可封装：

| Skill | 参数 | 底层命令 | 风险 |
|---|---|---|---|
| get_audio_info | {} | dumpsys audio | 低 |
| get_media_sessions | {} | dumpsys media_session | 低 |
| set_volume | {"stream":"music","level":0-15} | media volume | 中 |
| volume_up | {} | input keyevent | 低 |
| volume_down | {} | input keyevent | 低 |
| media_play_pause | {} | input keyevent | 低 |

---

## 9. 输入模拟

```bash
adb shell input help
adb shell input keyevent KEYCODE_HOME
adb shell input keyevent KEYCODE_BACK
adb shell input keyevent KEYCODE_APP_SWITCH
adb shell input keyevent KEYCODE_POWER
adb shell input tap 500 500
adb shell input swipe 500 1200 500 300 300
adb shell input text hello
```

可封装：

| Skill | 参数 | 底层命令 | 风险 |
|---|---|---|---|
| press_home | {} | input keyevent HOME | 低 |
| press_back | {} | input keyevent BACK | 低 |
| open_recent_apps | {} | input keyevent APP_SWITCH | 低 |
| tap_screen | {"x":500,"y":500} | input tap | 中 |
| swipe_screen | {"x1":...} | input swipe | 中 |
| input_text | {"text":"..."} | input text | 中 |

---

## 10. 应用管理

```bash
adb shell pm list packages
adb shell pm list packages -3
adb shell pm list packages -s
adb shell pm list packages -d
adb shell pm path com.android.settings
adb shell dumpsys package com.android.settings
adb shell cmd package help
adb shell am start -n package/activity
adb shell monkey -p com.android.settings 1
adb shell am force-stop package.name
adb shell pm clear package.name
adb shell pm disable-user package.name
adb shell pm enable package.name
adb shell appops get package.name
```

可封装：

| Skill | 参数 | 底层命令 | 风险 |
|---|---|---|---|
| list_installed_apps | {"type":"all/third/system"} | pm list packages | 低 |
| get_app_info | {"package":"..."} | dumpsys package | 低 |
| open_app | {"package":"..."} | monkey / am start | 中 |
| force_stop_app | {"package":"..."} | am force-stop | 中 |
| clear_app_data | {"package":"..."} | pm clear | 高，必须确认 |
| disable_app | {"package":"..."} | pm disable-user | 高，必须确认 |
| enable_app | {"package":"..."} | pm enable | 中 |

---

## 11. Intent / Activity 能力

```bash
adb shell cmd package resolve-activity --brief android.intent.action.MAIN
adb shell am start -a android.settings.SETTINGS
adb shell am start -a android.settings.WIFI_SETTINGS
adb shell am start -a android.settings.BLUETOOTH_SETTINGS
adb shell am start -a android.settings.APPLICATION_SETTINGS
adb shell am start -a android.settings.DEVICE_INFO_SETTINGS
adb shell am start -a android.settings.BATTERY_SAVER_SETTINGS
adb shell am start -a android.settings.DISPLAY_SETTINGS
adb shell am start -a android.settings.SOUND_SETTINGS
adb shell am start -a android.settings.ACCESSIBILITY_SETTINGS
adb shell am start -a android.settings.LOCATION_SOURCE_SETTINGS
adb shell am start -a android.settings.NFC_SETTINGS
adb shell am start -a android.settings.DATA_ROAMING_SETTINGS
```

可封装：

| Skill | 参数 | 底层 Intent | 风险 |
|---|---|---|---|
| open_settings | {} | android.settings.SETTINGS | 低 |
| open_wifi_settings | {} | android.settings.WIFI_SETTINGS | 低 |
| open_bluetooth_settings | {} | android.settings.BLUETOOTH_SETTINGS | 低 |
| open_app_settings | {} | android.settings.APPLICATION_SETTINGS | 低 |
| open_battery_settings | {} | android.settings.BATTERY_SAVER_SETTINGS | 低 |
| open_display_settings | {} | android.settings.DISPLAY_SETTINGS | 低 |
| open_sound_settings | {} | android.settings.SOUND_SETTINGS | 低 |

---

## 12. 通知 / 状态栏

```bash
adb shell dumpsys notification
adb shell cmd notification help
adb shell cmd statusbar help
adb shell cmd statusbar expand-notifications
adb shell cmd statusbar expand-settings
adb shell cmd statusbar collapse
```

可封装：

| Skill | 参数 | 底层命令 | 风险 |
|---|---|---|---|
| get_notification_info | {} | dumpsys notification | 低 |
| expand_notifications | {} | cmd statusbar expand-notifications | 低 |
| expand_quick_settings | {} | cmd statusbar expand-settings | 低 |
| collapse_statusbar | {} | cmd statusbar collapse | 低 |

---

## 13. 电源 / 省电 / 休眠

```bash
adb shell dumpsys power
adb shell cmd power help
adb shell dumpsys deviceidle
adb shell cmd deviceidle help
adb shell settings get global low_power
adb shell settings put global low_power 1
adb shell input keyevent KEYCODE_POWER
adb shell svc power stayon true
adb shell svc power stayon false
adb shell reboot
adb shell reboot recovery
adb shell reboot bootloader
adb shell reboot -p
```

可封装：

| Skill | 参数 | 底层命令 | 风险 |
|---|---|---|---|
| get_power_info | {} | dumpsys power | 低 |
| get_device_idle_info | {} | dumpsys deviceidle | 低 |
| set_battery_saver | {"enabled":true} | settings put global low_power | 中，设备相关 |
| keep_screen_awake | {"enabled":true} | svc power stayon | 中 |
| lock_screen | {} | input keyevent POWER | 中 |
| reboot_device | {} | reboot | 高，必须确认 |
| shutdown_device | {} | reboot -p | 高，必须确认 |

---

## 14. 位置 / 传感器 / 硬件

```bash
adb shell dumpsys location
adb shell cmd location help
adb shell dumpsys sensorservice
adb shell cmd sensorservice help
adb shell dumpsys vibrator
adb shell cmd vibrator help
adb shell dumpsys input
adb shell getevent -p
```

可封装：

| Skill | 参数 | 底层命令 | 风险 |
|---|---|---|---|
| get_location_info | {} | dumpsys location | 中，隐私 |
| get_sensor_list | {} | dumpsys sensorservice | 低 |
| get_input_devices | {} | getevent -p | 低 |
| vibrate | {"duration_ms":300} | cmd vibrator | 低，设备相关 |

---

## 15. 文件与存储

```bash
adb shell pwd
adb shell ls -lah /sdcard
adb shell ls -lah /sdcard/Download
adb shell du -h -d 1 /sdcard
adb shell stat /sdcard/Download
adb shell cat file
adb shell cp source target
adb shell mv source target
adb shell rm file
adb push local remote
adb pull remote local
```

可封装：

| Skill | 参数 | 底层命令 | 风险 |
|---|---|---|---|
| list_files | {"path":"/sdcard/Download"} | ls -lah | 中，隐私 |
| get_file_info | {"path":"..."} | stat | 中，隐私 |
| read_text_file | {"path":"..."} | cat/head | 高，隐私 |
| delete_file | {"path":"..."} | rm | 高，必须确认 |

---

## 16. 日志 / 调试

```bash
adb logcat -d -t 200
adb logcat -c
adb shell dmesg
adb shell dumpsys dropbox
adb shell dumpsys activity crashes
adb shell dumpsys activity anr
adb bugreport
adb shell dumpsys meminfo
adb shell dumpsys cpuinfo
adb shell top -b -n 1
adb shell ps -A
```

可封装：

| Skill | 参数 | 底层命令 | 风险 |
|---|---|---|---|
| get_recent_logs | {"lines":200} | logcat -d -t | 高，隐私 |
| clear_logs | {} | logcat -c | 中 |
| get_crash_info | {} | dumpsys activity crashes | 高，隐私 |
| get_anr_info | {} | dumpsys activity anr | 高，隐私 |
| get_top_processes | {} | top / ps | 低 |

---

## 17. 权限 / AppOps

```bash
adb shell pm list permissions
adb shell pm list permissions -g -d
adb shell dumpsys package package.name | grep permission
adb shell appops get package.name
adb shell appops set package.name OP allow
adb shell cmd appops help
```

可封装：

| Skill | 参数 | 底层命令 | 风险 |
|---|---|---|---|
| list_permissions | {} | pm list permissions | 低 |
| get_app_permissions | {"package":"..."} | dumpsys package | 中 |
| get_app_ops | {"package":"..."} | appops get | 中 |
| set_app_op | {"package":"...","op":"...","mode":"allow"} | appops set | 高，必须确认 |

---

## 18. Settings 数据库

```bash
adb shell settings list system
adb shell settings list secure
adb shell settings list global
adb shell settings get system screen_brightness
adb shell settings get global airplane_mode_on
adb shell settings get global bluetooth_on
adb shell settings get global low_power
```

写入测试前必须记录原值：

```bash
adb shell settings get namespace key
adb shell settings put namespace key value
adb shell settings get namespace key
```

可封装：

| Skill | 参数 | 底层命令 | 风险 |
|---|---|---|---|
| get_setting | {"namespace":"global","key":"..."} | settings get | 中 |
| set_setting | {"namespace":"system","key":"...","value":"..."} | settings put | 高，必须确认 |
| list_settings | {"namespace":"global"} | settings list | 中 |

---

## 19. 截屏 / 录屏

```bash
adb shell screencap -p /sdcard/Pictures/screen.png
adb pull /sdcard/Pictures/screen.png .
adb shell screenrecord --time-limit 10 /sdcard/Movies/record.mp4
adb pull /sdcard/Movies/record.mp4 .
```

可封装：

| Skill | 参数 | 底层命令 | 风险 |
|---|---|---|---|
| take_screenshot | {"path":"..."} | screencap | 高，隐私 |
| record_screen | {"seconds":10,"path":"..."} | screenrecord | 高，隐私 |

---

## 20. 安全分级建议

| 等级 | 定义 | 示例 | 是否需要用户确认 |
|---|---|---|---|
| 低 | 只读、无隐私或轻隐私 | 电量、内存、屏幕尺寸 | 否 |
| 中 | 改变设备状态但可恢复 | WiFi、亮度、音量、打开设置 | 视情况 |
| 高 | 隐私、删除、清数据、重启 | 读文件、logcat、pm clear、reboot | 必须 |
| 禁止 | 高破坏或越权 | rm -rf /、修改系统分区、绕过锁屏 | 禁止 |

---

## 21. 建议优先封装的 Skill

第一批，适合你的 Hsiaopu 项目：

| Skill | 类型 | 推荐原因 |
|---|---|---|
| get_battery_info | 查询 | 稳定、低风险 |
| get_memory_info | 查询 | 稳定、低风险 |
| get_storage_info | 查询 | 稳定、低风险 |
| get_network_info | 查询 | 有实用价值 |
| ping_host | 查询 | 参数简单 |
| set_volume | 控制 | 参数边界清晰 |
| set_brightness | 控制 | 参数边界清晰 |
| open_settings | Intent | 很像手机助手能力 |
| open_wifi_settings | Intent | 实用且低风险 |
| expand_notifications | 控制 | 体验明显 |

第二批：

| Skill | 类型 | 注意 |
|---|---|---|
| set_wifi_enabled | 控制 | 部分 ROM 受限 |
| set_bluetooth_enabled | 控制 | Android 版本差异 |
| force_stop_app | 应用管理 | 需要确认 |
| list_installed_apps | 应用查询 | 有隐私风险 |
| take_screenshot | 多模态 | 高隐私，必须确认 |

---

## 22. 每个 Skill 的落地模板

```json
{
  "name": "set_volume",
  "description": "设置系统音量",
  "parameters": {
    "stream": {
      "type": "string",
      "enum": ["music", "ring", "alarm", "notification"]
    },
    "level": {
      "type": "integer",
      "minimum": 0,
      "maximum": 15
    }
  },
  "risk": "medium",
  "requires_confirmation": false,
  "executor": "media volume --stream <streamCode> --set <level>",
  "verify": "media volume --stream <streamCode> --get"
}
```

---

## 23. 扫描结果汇总表

| 类别 | 已扫描 | 可用数量 | 高价值 Skill | 受限点 |
|---|---:|---:|---|---|
| 基础命令 | | | | |
| cmd 服务 | | | | |
| Binder 服务 | | | | |
| 设置项 | | | | |
| 网络 | | | | |
| 蓝牙/NFC | | | | |
| 显示/亮度 | | | | |
| 音频/媒体 | | | | |
| 输入 | | | | |
| 应用管理 | | | | |
| Intent | | | | |
| 通知/状态栏 | | | | |
| 电源 | | | | |
| 传感器 | | | | |
| 文件 | | | | |
| 日志 | | | | |
| 权限/AppOps | | | | |

