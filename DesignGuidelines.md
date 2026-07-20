# UI 设计规范（Apple HIG 视角）

> 本文档汇总了以苹果首席设计师视角审查项目时发现的问题及对应规范。每条规则都附「为什么」和「检查清单」，供后续开发逐条对照。

---

## 一、总则：三条不可违背的原则

1. **单一色彩语义** — 整个 App 只有一套色彩语义系统，所有颜色来自主题，禁止硬编码。
2. **一致的节奏** — 间距、圆角、字号必须遵循阶梯，不允许魔法数字。
3. **可感知的层级** — 每个页面有锚点（标题栏），每个可点击元素有反馈。

---

## 二、颜色系统 🎨

### 原则
颜色是语义化的，不是装饰。Apple 的设置 App 不会突然变粉色，你的 App 也不应该引导页紫色、主界面蓝色。

### 规则
| 场景 | 正确做法 | 错误做法 |
|------|---------|---------|
| 按钮主色 | `MaterialTheme.colorScheme.primary` | `Color(0xFF8B5CF6)` 硬编码 |
| 次级文字 | `onSurfaceVariant` | `Color(0xFF6B6B7B)` 硬编码 |
| 分组标题 | `onSurfaceVariant`（灰色） | `primary`（主色，会抢注意力） |
| 卡片背景 | `surfaceVariant.copy(alpha = 0.5f)` | `Color(0xFFFEF7FF)` 硬编码 |
| 透明度 | 定义语义变量，避免随手 0.3f | 魔法数字透明度 |

### 核心判断
- **主色只用于可交互元素**（按钮、选中态、链接），不用于标题/正文。
- **分组标题用次级灰色**：iOS 的 section header 是小字灰色，不是主色。
- **引导页必须继承主题色**，不能为了"好看"单独换一套色系。

### ✅ 检查清单
- [ ] 全局搜索 `Color(0xFF` — 仅允许在 `Color.kt` 和颜色选项数据中出现
- [ ] 按钮颜色全部来自 `colorScheme`
- [ ] 分组标题颜色是 `onSurfaceVariant` 而非 `primary`

---

## 三、间距与节奏 📐

### 原则
苹果遵循 8pt 网格，每个间距值都是设计过的，不是随手写的。

### 间距阶梯（必须遵循）
```
xs   = 4.dp    // 紧凑元素间距（图标与文字）
sm   = 8.dp    // 小间距
md   = 12.dp   // 中间距
lg   = 16.dp   // 标准内边距、卡片内边距
xl   = 20.dp   // 大内边距
xxl  = 24.dp   // 区块间距
xxxl = 32.dp   // 大段落间距
huge = 48.dp   // 空状态留白
```

### 规则
- **卡片内边距统一**：所有内容卡片用 `16.dp`，不允许 8/12/16/20 混用。
- **页面水平边距统一**：主内容区 `16.dp`，引导页/空状态可 `32.dp`。
- **元素间距用 `Arrangement.spacedBy`**，不要手写一堆 Spacer。

### ❌ 反例
```
Spacer(modifier = Modifier.height(8.dp))
Spacer(modifier = Modifier.height(12.dp))
Spacer(modifier = Modifier.height(16.dp))
Spacer(modifier = Modifier.height(20.dp))  // 为什么是 20 不是 24？
```

### ✅ 检查清单
- [ ] 卡片内边距全部一致（16.dp）
- [ ] 没有出现 10.dp、14.dp、18.dp 这类非阶梯值
- [ ] 优先用 `spacedBy` 替代连续 Spacer

---

## 四、圆角与形状层级 ⭕

### 原则
圆角有层级语义：小元素小圆角，大元素大圆角。同一层级的元素圆角必须一致。

### 圆角阶梯
| 层级 | 圆角 | 用途 |
|------|------|------|
| 小 | 8-12.dp | Chip、小按钮、输入框 |
| 中 | 14-16.dp | 卡片、标准按钮 |
| 大 | 20-24.dp | 大卡片、底部弹窗顶部 |
| 胶囊 | 999.dp | FilterChip、标签 |

### 规则
- 同类元素圆角必须相同：所有按钮 14.dp，所有卡片 16.dp。
- 底部弹窗顶部圆角统一 24.dp。
- 胶囊形（FilterChip）统一 999.dp，不用 20.dp 冒充。

### ✅ 检查清单
- [ ] 所有按钮圆角一致
- [ ] 所有卡片圆角一致
- [ ] 底部弹窗顶部圆角 24.dp

---

## 五、排版系统 ✍️

### 原则
用语义化 typography 样式，不直接写 `fontSize`。这样全局改字号只改一处，且支持字体缩放。

### 规则
| 场景 | 用什么 | 不要用 |
|------|--------|--------|
| 大标题 | `typography.headlineMedium` + Bold | `fontSize = 28.sp` |
| 页面标题 | `typography.titleLarge` + Bold | `fontSize = 22.sp` |
| 分组标题 | `typography.titleSmall` + SemiBold + onSurfaceVariant | `fontSize = 14.sp` |
| 正文 | `typography.bodyLarge` | `fontSize = 16.sp` |
| 次要文字 | `typography.bodyMedium` + onSurfaceVariant | `fontSize = 14.sp` |
| 标签 | `typography.labelSmall` | `fontSize = 11.sp` |

### 字重规范
- 大标题/页面标题：`Bold`
- 次级标题/分组标题：`SemiBold`
- 正文：`Normal`（不写就是默认）
- 次要文字：`Normal` + 灰色

### ❌ 反例
```
Text(text = ..., fontSize = 14.sp)  // 这个 14 和那个 14 是一个意思吗？
Text(text = ..., fontSize = 22.sp, fontWeight = FontWeight.Bold)  // 应该用 titleLarge
```

### ✅ 检查清单
- [ ] 全局搜索 `fontSize =` — 仅允许 emoji 图标（`fontSize = 40.sp`）和特殊装饰
- [ ] 字重使用统一：标题 Bold，次级 SemiBold，正文 Normal
- [ ] 字号全部通过 `MaterialTheme.typography.*` 引用

---

## 六、导航与层级结构 🧭

### 原则
用户永远要知道自己在哪、能回哪去。导航栏是页面层级的锚点。

### 规则
1. **每个主页面必须有 TopAppBar**：显示当前页面标题，是用户的视觉锚点。
2. **底部导航用 `NavigationBar`**，不是 `BottomAppBar`（后者是放 FAB/操作按钮的）。
3. **平板侧边栏要有品牌区**：NavigationRail 顶部放 App Logo + 名称 + 副标题，不能让导航项从最顶部挤着排。
4. **页面切换要有过渡动画**：用 `AnimatedContent` + fadeIn/fadeOut，不要直接 `when` 切换。

### 手机 vs 平板
| 设备 | 导航 | 顶栏 |
|------|------|------|
| 手机 | 底部 `NavigationBar` | `LargeTopAppBar`（可折叠大标题） |
| 平板 | 侧边 `NavigationRail` + 品牌头 | 同上 |

### ✅ 检查清单
- [ ] 每个主页面有 TopAppBar 显示标题
- [ ] 底部导航是 `NavigationBar` 组件
- [ ] NavigationRail 有 `header` 品牌区
- [ ] tab 切换有 `AnimatedContent` 过渡

---

## 七、组件一致性 🧩

### 原则
同样的功能只写一次。重复代码是 bug 的温床。

### 规则
1. **颜色选择器只写一次**：提取为 `ColorPicker` 公共组件。
2. **分类选择只写一次**：提取为 `CategoryChips` 公共组件。
3. **按钮高度统一**：主按钮内边距 `vertical = 14.dp`，最小点击区域 44dp（Apple HIG）。
4. **空状态只写一次**：提取为 `EmptyState` 组件，所有页面复用。

### 提取清单（必须复用，禁止重复实现）
- `ColorPicker` — 颜色选择
- `CategoryChips` — 分类筛选/选择
- `EmptyState` — 空状态
- `SettingsGroup` — 分组列表
- `SettingsRow` — 设置项行
- `SettingsDivider` — 组内分隔线

### ✅ 检查清单
- [ ] 全局搜索 `colorOptions` — 只在 CommonComponents 定义一次
- [ ] 全局搜索 `categories` — 只在 CommonComponents 定义一次
- [ ] 按钮内边距一致

---

## 八、列表与分组 📋

### 原则
iOS 的 InsetGrouped 风格：圆角卡片 + 小字灰色标题 + 组内细分隔线。

### 规则
1. **分组标题**：`titleSmall` + `SemiBold` + `onSurfaceVariant`（灰色小字），在卡片上方左对齐。
2. **组内分隔线**：行与行之间用 `HorizontalDivider`（0.5dp，`outlineVariant` 色），不用空格分隔。
3. **分隔线缩进**：iOS 的分隔线左对齐到文字位置（不是卡片边缘）。
4. **卡片间距**：组与组之间 `16-24.dp`，组内行间无间距（靠分隔线区分）。

### 结构示例
```
分组标题（灰色小字）
┌─────────────────────────┐
│  设置项 1               │
│  ─────────────────────  │  ← 0.5dp 分隔线
│  设置项 2               │
│  ─────────────────────  │
│  设置项 3               │
└─────────────────────────┘
```

### ✅ 检查清单
- [ ] 分组标题是灰色 `onSurfaceVariant`，不是主色
- [ ] 组内多行之间有 `SettingsDivider`
- [ ] 单行组不需要分隔线

---

## 九、空状态设计 📭

### 原则
克制。图标不大，有描述，有行动引导。

### 规则
1. **emoji/图标尺寸**：`40.sp`（约 40dp），不要 64.sp/80.sp。
2. **必须有副标题**：解释为什么是空的。
3. **必须有行动按钮**：引导用户下一步操作（如"点击 + 添加衣物"）。
4. **风格统一**：所有空状态用同一个 `EmptyState` 组件。

### ❌ 反例
```
Text(text = "👔", fontSize = 64.sp)  // 太大，喧宾夺主
// 没有副标题，没有行动按钮
```

### ✅ 正例
```
EmptyState(
    icon = "👔",
    title = "衣橱还是空的",
    subtitle = "添加你的第一件衣物，开始管理穿搭",
    actionText = "添加衣物",
    onAction = { showAddClothing = true }
)
```

### ✅ 检查清单
- [ ] emoji 尺寸 ≤ 40.sp
- [ ] 有副标题说明
- [ ] 有行动按钮引导
- [ ] 所有空状态用同一组件

---

## 十、交互与反馈 👆

### 原则
iOS 中每个可点击元素都有清晰的按下反馈。用户必须知道"我点到了"。

### 规则
1. **可点击元素必须有反馈**：缩放、透明度、ripple 至少一种。
2. **颜色选择圆点**：按下时缩放至 0.85 + ripple。
3. **卡片点击**：用 `Modifier.clickable` 自带 ripple，或加按下缩放。
4. **页面切换**：`AnimatedContent` 过渡，禁止硬切。

### 实现模板（按下缩放反馈）
```kotlin
val interactionSource = remember { MutableInteractionSource() }
val isPressed by interactionSource.collectIsPressedAsState()
val scale by animateFloatAsState(if (isPressed) 0.85f else 1f)

Box(
    modifier = Modifier
        .scale(scale)
        .clickable(interactionSource = interactionSource, indication = LocalIndication.current) { ... }
)
```

### ✅ 检查清单
- [ ] 所有 `clickable` 元素有视觉反馈
- [ ] 颜色选择器有按下缩放
- [ ] tab 切换有过渡动画

---

## 十一、响应式适配 📱💻

### 原则
一套代码，两种布局。手机竖向浏览，平板横向分区。

### 规则
1. **用 `WindowWidthSizeClass` 判断**：`EXPANDED` 走平板布局，其余走手机布局。
2. **手机**：底部 `NavigationBar` + 单栏内容。
3. **平板**：侧边 `NavigationRail`（带品牌头）+ `VerticalDivider` + 内容区。
4. **两套布局共享同一套屏幕组件**，只传不同的 `paddingValues`。

### ✅ 检查清单
- [ ] 平板宽度下显示 NavigationRail
- [ ] NavigationRail 有品牌头
- [ ] 手机/平板共用屏幕组件

---

## 十二、设计令牌系统

### 原则
间距、圆角、阴影定义为令牌，通过 `CompositionLocal` 提供，不在业务代码写字面量。

### 已定义令牌（DesignSystem.kt）
```
AppSpacing:  xs(4) sm(8) md(12) lg(16) xl(20) xxl(24) xxxl(32) huge(48)
AppShape:    small(8) medium(12) large(16) extraLarge(20) pill(999) sheet(24) card(18) button(14)
AppElevation: none(0) sm(1) md(2) lg(4) card(0)
```

### 使用方式
```kotlin
val spacing = LocalAppSpacing.current
Modifier.padding(spacing.lg)  // 而不是 padding(16.dp)
```

### 迁移原则
- 新代码必须用令牌
- 旧代码逐步迁移，确保数值符合阶梯即可

---

## 快速自查清单（每次提交前过一遍）

- [ ] **颜色**：无硬编码 `Color(0xFF...)`（除 Color.kt 和颜色数据）
- [ ] **间距**：所有 dp 值在阶梯内（4/8/12/16/20/24/32/48）
- [ ] **圆角**：同类元素圆角一致
- [ ] **排版**：用 `typography.*`，不直接写 `fontSize`（emoji 除外）
- [ ] **导航**：有 TopAppBar，底部用 NavigationBar，平板有品牌头
- [ ] **组件**：ColorPicker/CategoryChips/EmptyState 无重复实现
- [ ] **分组**：标题灰色，组内有分隔线
- [ ] **空状态**：emoji ≤40sp，有副标题和行动按钮
- [ ] **交互**：可点击元素有反馈，页面切换有动画
- [ ] **适配**：手机/平板两套布局
