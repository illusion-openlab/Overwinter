# 越冬 — 空间分层与近景飘雪 设计

日期：2026-09-20
状态：已与用户确认，待实现

## 目标

给这个平面窗口小游戏增加真实的空间纵深：

1. 小鸟和障碍物从背景里"浮"出来，戴头显时能看出前后关系
2. 增加一层飘到窗面**之前**的近景雪，让雪从玩家和画面之间穿过

## 背景与约束

- 容器是单个 `WindowContainer`，`style=1`（Planar），1040 × 580 dp。**本次不改容器类型** —— 兄弟项目 SpaceMerge 在同类平面窗口里用 `offset(z = 24.dp)` 已经拿到可见的浮起效果，没有证据表明必须升到 Volumetric。
- 现状：`GameArt.kt` 的 `drawGame` 把 8 个绘制步骤画进**同一张 `Canvas`**。同一张 Canvas 内部无法表达深度，这是本次改动的根本原因。
- 现状：`snow()` 已存在（`GameArt.kt:364`），50~190 片，随体温 `k` 变密变快，三个阶段都在画。本次不删它，降量后留作远雪。

## 一、分层架构

把单张 Canvas 拆成 4 张叠放的 Canvas，每张挂 `com.pico.spatial.ui.foundation.layout.offset(z = ...)`。

| 层 | 内容（绘制顺序不变） | z |
| --- | --- | --- |
| L0 窗面层 | `background` → `haze` → 远雪 → `frost` | 0 |
| L1 玩法层 | `obstacles` → `pickups` | +14dp |
| L2 小鸟层 | `bird`（含穿枝雪雾 `snowBurst`） | +20dp |
| L3 近景层 | 近雪 → `grain` → `drawDim` | +36dp |

### 关键决策

- **小鸟只比枝干前 6dp**。碰撞是在 2D 平面算的；小鸟浮得越靠前，撞枝就越像"撞了空气"。6dp 足够让小鸟不"贴"在枝干上，又小到不破坏碰撞可信度。空间感主要由「背景远 / 玩法中 / 近雪前」三档拉开，而不是靠小鸟单独前冲。
- **`frost` 从最前挪到 L0**。语义上"这扇窗户结霜了"成立，并且顺带解决霜糊住左上/右上 HUD 胶囊的问题。这是本次唯一一处会改变现有观感的改动，已与用户确认。
- **`drawDim` 必须跟到 L3**。它是结算态的整屏压暗；留在 L0 就只能压暗背景。

### 不改的东西

`offset(z=)` 只改绘制位置，不改测量和布局占位。因此碰撞判定、点击命中区域、窗口尺寸全部不变，`GameEngine.kt` 一行不改，现有 17 个 JUnit 测试继续通过。

### 已知 API 陷阱

必须 import PICO 自己的 `offset`：

```kotlin
import com.pico.spatial.ui.foundation.layout.offset   // 有 z 参数
```

AndroidX 的 `androidx.compose.foundation.layout.offset` 只有 x/y 重载，写 `z =` 编译报 "no parameter named z"。这条是 SpaceMerge 在 SDK 6.0.0 上踩过并写进 `ResultPanel.kt` 注释的。

## 二、两层雪

### 远雪（L0）

沿用现有 `snow()`，只降量：粒子数 `50 + k * 140` → `36 + k * 100`。前面多了一层，总量要让出来。其余参数不动。

### 近雪（L3，新增）

| 参数 | 远雪（L0） | 近雪（L3） |
| --- | --- | --- |
| 数量 | 36 ~ 136 | 固定 22 |
| 半径 | 0.9 ~ 3.1 | 3 ~ 7 |
| 下落速度 | 1× | ~2.5× |
| 水平摆幅 | ±7 | ±22 |
| 随体温加密 | 是 | **否** |

近雪不随体温加密是刻意的：它在最前面，密了会挡玩法。氛围的"冷"交给远雪和霜表达。

## 三、HUD 与卡片的深度归位

这是必须做的连带改动。卡片和 HUD 是 Canvas 的兄弟节点，现在靠绘制顺序压在最上面；一旦 Canvas 有了 z，绘制顺序不再决定前后 —— 小鸟在 +20dp 而卡片在 0，小鸟会穿过结算卡浮在它前面。

| 节点 | z | 理由 |
| --- | --- | --- |
| `Hud`（体温 / 得分胶囊） | +20dp | 与小鸟同档。近雪从它前面飘过，正是想要的效果 |
| `StartCard` / `ResultCard`（含 `Scrim`） | +40dp | 最前。模态 UI 可读性优先，不能让雪片糊住「开始飞行」按钮 |

`BasicSheet` 起的玩法弹层是独立模态窗口，深度由系统管，不动。

## 四、代码组织

新增 `content/SpatialDepth.kt`，集中 5 个 z 常量：

```kotlin
val Z_PLAY  = 14.dp   // L1 玩法层
val Z_BIRD  = 20.dp   // L2 小鸟层
val Z_NEAR  = 36.dp   // L3 近景层
val Z_HUD   = 20.dp   // HUD 胶囊
val Z_CARD  = 40.dp   // 开始卡 / 结算卡
```

真机调参时只改这一个文件。

`GameArt.kt` 的 `drawGame(e, art, t)` 拆成 4 个入口：`drawFar` / `drawPlay` / `drawBird` / `drawNear`。各私有绘制函数（`background`/`haze`/`obstacles`/…）签名不变，只是被分派到不同入口。`drawGame` 本身删除；`drawDim` 并入 `drawNear`。

`HomePage.kt` 里单个 `Canvas` 换成 4 个叠放的 `Canvas`，每个都读 `tick` 订阅重组。外层 `Box` 的 `pointerInput` / `spatialHoverEffect` / `controllerHapticFeedback` 保持原样不动。

## 五、风险

三条，都只能真机判：

1. **Planar 窗口的深度余量未知**。已知：SpaceMerge 在平面窗口 24dp 有效；SpaceVinyl 在**体积**窗口测到 200dp 安全、280dp 被前边界裁掉。平面窗口的上限没人量过，可能远小于体积窗口。最前一档取 40dp，比已知有效值只高一档。失败症状：近景层或卡片整个消失。回退：调小 `SpatialDepth.kt` 里的值。
2. **4 层全屏合成的性能**。绘制图元总量基本不变，多出来的是 4 次全窗合成，循环跑 ~90Hz。失败症状：掉帧、手感变钝。回退：合并 L1/L2（小鸟与枝干同档），或整体 revert。
3. **指针穿透**。最前多了三张全屏 Canvas，射线要穿过它们才能到达底层 `Box` 的 `detectTapGestures`。Canvas 不挂 pointer-input node，理论上不拦截，但 `Cards.kt` 中"Scrim 自己消费指针"的既有逻辑在分层后是否仍然生效没有证据。失败症状：点窗口不扇翅，或卡片外遮罩区域又开始穿透。

## 六、验证计划

分两道，职责不重叠：

**A. 模拟器（agent 执行）** — 走现成的 `.spatialsdk/tools/verify-ui.sh <轮次>`，判四件事：

- `assembleDebug` 通过，`testDebugUnitTest` 17 个仍全绿
- 三态截图都拍到内容，没有任何层整个消失（对应风险 1）
- 构图与改动前一致（除霜的位置变化外）
- 扇翅仍可触发（对应风险 3）

单目截图**判不出深度**。这道门禁不对"浮起来了没有"给任何结论。

**B. 真机（用户执行）** — 用户戴 PB311XKGL4160087B 主观判断前后关系和近雪观感，用户反馈后 agent 调 `SpatialDepth.kt` 的常量重出包。这是深度效果唯一可靠的验收路径。

## 七、检索声明

`pico-dev-knowledge` MCP 在本次会话连接失败（CONNECTION_CLOSED）。本文中关于 `offset(z = Dp)` 的全部事实来自：

- 兄弟项目 `SpaceMerge/app/src/main/java/tech/illusion/spacemerge/content/ResultPanel.kt` 的已编译验证代码与注释
- 兄弟项目 `SpaceVinyl/app/src/main/java/tech/illusion/spacevinyl/content/HomeVolume.kt` 记录的体积窗口深度实测数据
- 插件 bundled reference `skills/spatial-ui-ability/references/ability-z-offset.md` 与 `ability-depth-layout.md`

未经知识图谱交叉核对。
