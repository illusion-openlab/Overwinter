# Overwinter（越冬）— Project Agent Guidance

## 这是什么

**越冬**：PICO OS 6 平面窗口小游戏。一只小鸟在寒冬枯枝林里飞行，体温每秒流失，撞上枝干掉得更快，吃到浆果回温。Flappy 类玩法，但生命是一条**持续流失的体温槽**而不是一击死。

- 容器：单个 `WindowContainer`，**1040 × 580 dp**，`style=1`（Planar），`materialbackground=0`
- 输入：点击窗口任意处 = 扇翅一次
- 美术：水彩位图素材 + 程序合成（不是 3D，不用 ECS）

**设计契约是 UI 的唯一基准**：[.spatialsdk/design-contract.md](.spatialsdk/design-contract.md) — 元素表、状态表、文案表、数值契约、碰撞契约、截图验证计划。改任何 UI 前先读它。

- 视觉稿（五帧合成，已发布 Artifact）：[.spatialsdk/design-ref/design-mockup.html](.spatialsdk/design-ref/design-mockup.html)
- 素材规格清单（27 项，尚未产出）：[.spatialsdk/design-ref/asset-manifest.md](.spatialsdk/design-ref/asset-manifest.md)
- 验证日志：[.spatialsdk/ui-verification-log.md](.spatialsdk/ui-verification-log.md) — **接手前先读**

## 当前状态（2026-09-20）

已完成：

- `pico-cli project create --template planar` 基线，`assembleDebug` 通过（32MB APK）
- `game/GameEngine.kt` 纯逻辑层 + **35 个 JUnit 测试全绿**（`GameEngineTest` 26 + `SnowFieldTest` 8 + `ExampleUnitTest` 1）
- `content/OverwinterTheme.kt` 固定配色（暖金主色 #E3B44A）+ 深色玻璃 `GlassScrim`
- 插画层、HUD、开始卡、结算卡全部实现，Material 零引用（release classpath 也已清零）
- DEBUG 截图入口（`--es ow_debug cold|over|invin|live`）
- **空间深度分层与近景雪**（见「结构与分层」一节）：原单张 Canvas 拆成四张按深度分组
  （`drawFar`/`drawPlay`/`drawBird`/`drawNear`），各自挂 `offset(z = ...)`；新增
  `SnowField.kt`（两层雪的纯数学，8 个 JUnit 测试）和 `SpatialDepth.kt`（五个 z 常量集中调参入口）。
  frost 与 farSnow 的绘制顺序各挪动了一处（详见 `SpatialDepth.kt`/`GameArt.kt` 顶部注释）。

**验证进度**：已上机多轮（见 `.spatialsdk/ui-verification-log.md`）。历史轮次拍到三态并修完
若干 Blocker/Major；花朵无敌（`invin`）态第 9 轮验证通过。**门禁 B · depth1**（2026-09-20）：
四层/近雪/构图/无崩溃四项全部通过（含一次"空房间"陈旧截图误判的排查，已用复核截图纠正，
详见验证日志）。**深度效果本身（小鸟/枝干/近雪的前后关系）单目模拟器截图无法判定，待用户
戴真机 `PB311XKGL4160087B` 主观验收**——这是当前唯一还没有任何证据的可见性问题。

未决项（契约 §0 有记录）：小鸟飞行动作待改、前景雪地层与截面帽/积雪冠缺素材。
花朵无敌已于 2026-08-18 实现（契约 §5c）：3 秒，只挡碰撞不保暖，触地当地板钳住。

## 结构与分层

```
game/GameEngine.kt      纯逻辑：物理、碰撞、体温、计分、障碍生成。不含任何 Compose/SDK 类型
content/SnowField.kt    两层雪的纯数学，不含 Compose 类型，能用 JUnit 测
content/SpatialDepth.kt 各层 z 深度常量。真机调参只改这里
content/GameArt.kt      插画层：素材加载 + WinterPalette + 体温染色，四个按深度分组的绘制入口
                        （drawFar/drawPlay/drawBird/drawNear，见下方「结构与分层」之后一节）
content/Hud.kt          体温胶囊 + 得分胶囊
content/Cards.kt        开始卡 / 结算卡（含 Scrim）
content/GameAudio.kt    音效：短音走 SoundPool，循环环境音走 MediaPlayer
content/HomePage.kt     Box 堆叠 + 帧循环 + 点击输入 + 音效边沿检测
Main.kt                 DefaultWindowContainer { PicoTheme { HomePage() } }
```

**纯逻辑层与 SDK 层严格分离**：`GameEngine.kt` 不 import 任何 Compose / Spatial 类型，所以它的每条规则都能用普通 JUnit 在 JVM 上测。后续加机制请延续这个分层——测试比上机验证快两个数量级。

**为什么用 Compose 帧回调而不是 ECS**：本作是纯 2D 平面窗口应用，没有 3D 场景内容。CLAUDE.md 里"3D 行为用 ECS"的规则针对的是 Spatial 场景，不适用于这里。

**空间深度分层（2026-09-20 新增）**：`HomePage.kt` 把原来一张 Canvas 拆成四张，各自挂
`com.pico.spatial.ui.foundation.layout.offset(z = ...)`，z 值取自 `SpatialDepth.kt`：

- L0（z=0，`drawFar`）：远景林、雾、远雪、四角结霜
- L1（z=Z_PLAY）：枝干与浆果/花（`drawPlay`）
- L2（z=Z_BIRD）：小鸟（`drawBird`），只比 L1 前 6dp——碰撞仍在 2D 平面算，浮太前会看起来撞了空气
- L3（z=Z_NEAR，最靠近玩家）：近景雪（`SnowField.kt` 的 `nearFlake`）、胶片颗粒、结算压暗（`drawNear`）
- HUD 胶囊挂 `Z_HUD`，开始卡/结算卡挂 `Z_CARD`（全局最前，模态可读性优先于"雪飘在前面"）

单目模拟器截图判不出深度是否合适，只能验证"没有一层被裁掉""近雪确实在画""构图没跑偏"。
真机主观验收后如果要调，**只改 `SpatialDepth.kt` 里那五个常量**，不要动分层结构或雪场公式。
详见 `.spatialsdk/ui-verification-log.md` 「门禁 B · depth1」一节。

## UI 硬规则

- 所有 2D UI 用 SpatialUI（`com.pico.spatial.ui.design.*`），根部包 `PicoTheme`
- **禁止 Material / Material3**。`ui-tooling` 已移到 `debugImplementation`，否则它的传递依赖 `androidx.compose.material` 会进 release 包
- 颜色写 `PicoTheme.colorScheme.*`，字体写 `PicoTheme.typography.*`，不写死色值

## 这个工程踩过的坑（省得再踩一遍）

### 构建

- **`local.properties` 必须自己建**，`project create` 不生成。内容见兄弟项目：
  `sdk.dir=/Users/zohar/Library/Android/sdk` + `spatial.tools.dir=/Users/zohar/Library/PICO/sdk`
- **每条 gradle / pico-cli 命令都要自己 export** `PICO_HOME` 和 `JAVA_HOME`，Bash 工具跨调用不保留 shell state

### 可用 API（已编译验证，别再猜）

`build.gradle.kts` 里 `resolutionStrategy` 排除了 `androidx.compose.ui:ui`、`ui-graphics`、`ui-text`、`foundation:foundation`——但**这些包的类照样能用**，由 PICO 自己的构件提供。实测可用：

`androidx.compose.foundation.Canvas` / `background` / `border` / `shape.RoundedCornerShape` /
`gestures.detectTapGestures` / `input.pointer.pointerInput` /
`ui.graphics.ImageBitmap` / `asImageBitmap` / `drawscope.{translate,rotate,scale}` / `drawImage` /
`runtime.withFrameNanos` / `ui.platform.LocalContext`

**已核实存在的 typography 角色**：`headlineLarge` `headlineSmall` `titleLarge` `titleMedium` `titleSmall` `bodyMedium` `labelLarge` `labelMedium` `labelSmall`
**已核实存在的 color 角色**：`fillPrimary` `fillSecondary` `fillTertiary` `fillLight` `labelPrimary` `labelPrimaryLight` `labelSecondary` `labelTertiary` `dividerLine` `interaction` `error` `alert` `passable`

磨砂玻璃：`Modifier.clip(shape).backgroundMaterial(true, Material.Thin | Material.Regular)`

### 覆盖层必须自己消费指针事件

开始卡和结算卡盖在带 `detectTapGestures` 的游戏画布上。`Modifier.background` 不挂 pointer-input node，射线落在卡片**之外**的遮罩区域会穿透到画布触发扇翅。`Cards.kt` 的 `Scrim` 里那段 `awaitPointerEvent().changes.forEach { it.consume() }` 就是干这个的，**不是死代码，别删**。

### 音效

- 素材在 `app/src/main/assets/audio/`，**必须保持未压缩**：`build.gradle.kts` 里的
  `androidResources { noCompress += listOf("wav","mp3") }`。压缩了 `assets.openFd()` 就拿不到
  可用的 `AssetFileDescriptor`，运行时抛异常。改动后用
  `unzip -lv <apk> | grep assets/audio` 确认是 `Stored / 0%`
- 五个音：`flap.wav` 扇翅 / `pickup.mp3` 收集 / `hit.wav` 撞枝 / `fall.mp3` 结算 / `wind.mp3` 循环环境音
- **两个音是裁过的，换素材时要照做**：
  `flap.wav` 取自 `飞翔.mp3` 的单次振翅（原文件是三次连续振翅共 2.4s，整段播会听到三下、连点糊成一片）；
  `hit.wav` 裁掉了 `撞击.mp3` 前面 0.151s 的静音（碰撞反馈慢半拍会和画面脱节，裁后起音 0.026s）。
  换素材前先量一下起音延迟和是不是单次事件
- 引擎只暴露 `flapEvents` / `pickupEvents` 两个**纯计数器**，表现层比对上一帧判边沿——
  这样引擎依旧不含 Android 类型，JUnit 照跑
- 音效**截图判不出来**。验证靠 `adb shell dumpsys audio` 看有没有本应用的活跃播放器，
  外加 logcat 无 SoundPool / MediaPlayer 异常

### 三维启动图标别让工具重新编码

`res/drawable/icon_3d_*.png` 与 `mipmap-xxxhdpi/ic_spatial_launcher.png` 曾被某个工具
（疑似 Android Studio 同步）原地重新编码：**尺寸、位深、色彩类型全都没变，体积却从 848KB
涨到 5MB**。纯膨胀无收益。发现 `git status` 里这几个文件无故变更时，直接
`git checkout HEAD -- <路径>` 还原；构建本身不会改写它们。

### 设备是多会话共享的

工作区里常有多个会话并行抢同一台模拟器。上机验证走 `.claude/skills/spatial-design-first-build/scripts/device-lock.sh`，**抢不到就跳过，不要死等、不要抢别人的锁**。验证走 `emulator-5554`，真机 `PB311XKGL4160087B` 留给用户试玩。

## 怎么构建 / 装 / 跑

```bash
export PICO_HOME='/Users/zohar/Library/PICO/sdk'
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
cd Overwinter
./gradlew assembleDebug
./gradlew testDebugUnitTest                      # 35 个纯逻辑测试（GameEngine 26 + SnowField 8 + Example 1）

# 上机（先抢锁，见上）
pico-cli app install -d emulator-5554 -r app/build/outputs/apk/debug/app-debug.apk
pico-cli app launch  -d emulator-5554 tech.illusion.overwinter
pico-cli capture screenshot -d emulator-5554 -o .spatialsdk/ui-verify/x.png
pico-cli app stop    -d emulator-5554 tech.illusion.overwinter
```

**门禁 B 一条命令**（已封装抢锁、轮询就位、三态截图、释放锁）：

```bash
cd Overwinter && bash .spatialsdk/tools/verify-ui.sh <轮次>
```

抢不到锁会干净退出并打印重跑命令，不会死等。**不要退回手写固定 `sleep`**——第 3 轮就是因为固定等 6s 拍到了两张空房间。

**截图验证专用状态**（契约 §7，`pico-cli app launch` 不支持传参，只能用原生 adb）：

```bash
adb -s emulator-5554 shell am start -S \
  -n tech.illusion.overwinter/.platform.LaunchActivity --es ow_debug cold   # 游玩中·体温18°，冻结
adb -s emulator-5554 shell am start -S \
  -n tech.illusion.overwinter/.platform.LaunchActivity --es ow_debug over   # 结算态
```

`ow_debug` 会把局面强制到指定状态并**冻结物理**（`GameEngine.frozen`），这样 6 秒后截图拿到的还是同一帧局面；动画时钟不冻，飘雪和扇翅照常。正常启动完全不受影响。

## 下一步

1. **真机验收空间深度效果**：小鸟/枝干/近雪的前后层次感是否合适，全靠用户戴 `PB311XKGL4160087B`
   主观判断——单目模拟器截图完全看不出立体深度。反馈「再往前/再往后/雪太前了」之类后，
   **只改 `SpatialDepth.kt` 里那五个 z 常量**重出包，不要动分层结构或雪场公式
2. **真机扫一遍射线**：确认「开始飞行」按钮点得到、覆盖层不穿透。这是目前唯一一类还没有任何证据的问题——静态截图和单测都判不出来
3. **最高分持久化**：目前只在内存里，退出就没了
4. 素材到位后按 `asset-manifest.md` 替换，并跑 `.spatialsdk/tools/validate-assets.py`
5. 花朵无敌的余晖残影目前被光晕盖住看不见，想要的话调 alpha 或缩小光晕半径
