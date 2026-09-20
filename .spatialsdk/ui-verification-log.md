# 越冬 · UI 验证日志

## 第 1 轮 — 2026-08-18 — **跳过（设备被占用）**

### 锁外准备

| 项 | 结果 |
|---|---|
| `./gradlew assembleDebug` | ✅ 通过，`app/build/outputs/apk/debug/app-debug.apk`（32 MB） |
| `./gradlew testDebugUnitTest` | ✅ 17 个纯逻辑测试全绿 |
| applicationId / 启动 Activity | `tech.illusion.overwinter` / `.platform.LaunchActivity` |
| 设计契约与参考图 | ✅ 已读入上下文 |
| 本轮检查清单 | ✅ 契约 §7 的 3 张：`start` / `cold` / `over` |
| `pico-env-doctor` 预检 | ✅ 本会话已做，故障项全在已知不阻塞名单内 |
| SpatialUI 自检 | ✅ 源码零 Material 引用；`PicoTheme` 包裹在 `Main.kt`；release classpath 的 `androidx.compose.material` 已清零 |

### 锁内

**没有进入。** `device-lock.sh acquire` 在等待中耗尽 180s 超时——锁被另一个会话 `spaceflightchess-centre` 持有（735s / 900s TTL）。

按 `spatial-design-first-build` 的规则：抢不到锁不死等、不重试第二遍、不抢别人的锁。本轮门禁 B 记为跳过。

事后确认：本会话**没有**留下残留锁，模拟器上**没有**安装本应用（`pm list packages` 无命中），对占用方无影响。

### 结论

**UI 完全未经运行验证。** 目前只有构建证据（编译通过 + 单测全绿），没有任何一帧真实渲染画面。

以下全部处于「未验证」状态，不得据此声称完成：

- 五层插画的实际叠加效果与体温染色
- HUD 两枚胶囊的磨砂玻璃、体温槽填充色
- 开始卡 / 结算卡的排版与磨砂效果
- 缝隙上下沿是否清晰可辨（碰撞判定的可读性）
- 覆盖层指针消费是否真的挡住了穿透（**这条只能真机/模拟器扫射线才发现**，编译和单测都测不出）
- 帧率与 Canvas 绘制开销

### 补跑指令

模拟器空出来后，在工作区根目录执行：

```bash
cd /Users/zohar/WorkSpace/Project/PicoProjects
bash .claude/skills/spatial-design-first-build/scripts/device-lock.sh status emulator-5554
```

显示 `free` 之后再走门禁 B 的锁内序列（install → 3 次 launch/截图/stop → release）。
三个状态的启动命令见项目 `AGENTS.md` 的「怎么构建 / 装 / 跑」。

---

## 第 2 轮 — 2026-08-18 — **已上机，发现 2 Blocker / 5 Major**

锁内 **29s**（目标 90s），三张图全部拿到，锁已释放。截图：`ui-verify/r1-{start,cold,over}.png`

### 通过

- **元素存在性**：三个状态里契约元素表的每个 id 都出现了
- **文案**：与契约 §4 逐条一致
- **五档氛围交叉淡入生效**：start 晴日 / cold(18°) 紫暮 / over(0°) 蓝夜，三张图背景明显不同
- **染色生效**：夜色下树干被染成蓝灰
- **收集物不参与染色**：浆果和花朵在夜空背景下仍然扎眼，规则成立
- **缝隙上下沿在 cold 图里可辨**，浆果在中线、花朵贴下沿，分带摆放成立
- **结算卡主次层级正确**：再飞一次深实心 / 回到开始页浅填充，弱得很明显
- **小鸟机身+单翅渲染正确**，翅膀姿态自然，没有"沸腾"

### Blocker

1. **纸纹平铺出规则暗纹（全屏）**
   预期：`paper_grain` 是无结构的纸张颗粒。
   实际：1:1 放大可见规则排列的钩状暗痕，每 128dp 重复一次，整屏都是。
   根因：我挑"最干净纸底"用的判据是「暗像素占比 < 0.4%」，一条细线结构没被筛掉，平铺后被复制满屏。
   修复点：`design-ref/cutouts/grain.png` 重新生成。

2. **主按钮与 `result_score` 不是暖色**
   预期（契约 §2）：`start_button`/`retry_button`「实心暖色填充，全卡唯一高饱和块面」；`result_score`「卡内唯一的暖色文本」。
   实际：全是中性深灰。根因是模板直接用 `PicoTheme` 系统自适应默认方案，没有主题色。
   同工作区的 `SpaceLianliankan/content/SpaceTheme.kt` 记录过同一个坑（用户原话「看起来没有主题色彩」），解法是 `defaultColorScheme(...)` 定制。
   修复点：新增 `content/OverwinterTheme.kt`。

### Major

3. **背景镜像对称轴肉眼可见** — 画面正中一棵大云杉左右完全对称。根因：154×208 缩略图放大到 820dp 高，一屏只有约 1.7 个贴片，镜像周期太短。真素材（2400×700）到位后自然消失，本轮先降低放大倍数缓解。
4. **冰柱读作"白色栅栏"** — 用矩形+圆点画的，太简陋。而它是上障碍的判定线，可读性直接关系到「缝隙边界必须所见即所判」。
5. **积雪冠辨识度不足** — 下障碍顶部只有很淡一点白，缝隙下边界不够明确。同上，属判定线可读性。
6. **`result_score` 与 `result_title` 字号差距不明显** — 契约要求「全卡最大的数字」。
7. **卡片是浅色玻璃，契约描述按深色写的** — 契约 §2 的「全卡最亮的文本」在浅色卡上方向相反。已批准的视觉稿是深色玻璃，因此按视觉稿修实现，而不是改契约。

### 不可判定（如实列出，既不计通过也不计失败）

- 所有精确 dp 间距与尺寸（透视截图判不出）
- 磨砂玻璃的模糊半径
- 碰撞矩形与视觉轮廓是否对齐（需要 `DEBUG` 描边，本轮未开）
- **覆盖层指针消费是否真的挡住了穿透** — 静态截图判不出，只能真机扫射线；`Cards.kt` 的 `Scrim` 里那段消费循环目前只有代码层面的正确性

### 结论

**需修复（第 2 轮，还剩 1 轮）。**

---

## 第 3 轮 — 2026-08-18 — **修复已验证，但只拿到 1/3 张**

锁内 25s，锁已释放。截图：`ui-verify/r2-{start,cold,over}.png`

### 截图有效性

`r2-start` 与 `r2-cold` **像素完全一致（平均差 0.0）**，内容是空房间——窗口没渲染出来，**这两张判为「截图无效」**，不用于判定。

根因不是崩溃：同一次运行的第三张 `r2-over` 完全正常。是 `adb shell am start -S` 强制重启后，固定等 6s 不够窗口起来。已改：新增 `.spatialsdk/tools/verify-ui.sh`，改成**轮询进程就位 + 额外沉降**，首次启动给 8s 预算。

### r2-over 判定 — 5 项修复全部落地

因为插画层与卡片样式是同一套代码路径，这一张同时验证了跨状态的修复：

| 第 2 轮问题 | 结果 |
|---|---|
| ① 纸纹规则暗纹 | ✅ **已消除**。1:1 观察天空区域，钩状暗痕全无。换成程序噪声（std 6.35），无结构故无图案 |
| ② 主按钮/分数不是暖色 | ✅ **已修**。`再飞一次` 是暖金实心配深字，全卡唯一高饱和块面；`57` 是卡内唯一暖色文本，与按钮同色系 |
| ④ 冰柱像白栅栏 | ✅ **已修**。锥形路径 + 竖直高光，读得出是冰柱 |
| ⑤ 积雪冠辨识度不足 | ✅ **已修**。新增 `crown()`，最高点在正中 = 判定上沿，下方带投影拉开层次 |
| ⑥ 分数字号差距不明显 | ✅ **已修**。`57` 明显是全卡最大 |
| ⑦ 卡片浅色玻璃 | ✅ **已修**。磨砂之上压 `GlassScrim`，得到视觉稿的深色玻璃；文字层级正确 |

`回到开始页` 半透明填充，明显弱于主按钮 —— 主次层级成立。

### 仍未验证

- **HUD 两枚胶囊的深色玻璃**：HUD 只在 Playing 出现，而 `cold` 那张无效
- **低温氛围 + 加重后的雾**：`over` 用的是蓝夜档，`cold` 的紫暮档没拍到
- **问题 ③ 背景镜像对称轴**：本轮加了雾缓解，效果未验证。根治要等 2400×700 真素材
- 上一轮列出的全部「不可判定」项仍然不可判定（精确 dp、模糊半径、碰撞矩形对齐、覆盖层指针消费）

### 结论

**部分通过。** 5 项修复经 `r2-over` 确认落地；剩余 2 项需补拍 `start` / `cold`。

补拍（设备空闲后一条命令）：

```bash
cd Overwinter && bash .spatialsdk/tools/verify-ui.sh 4
```

本轮第二次尝试补拍时设备被 `spaceflightchess-turngate` 占用（等待 200s 后放弃），按规则跳过、未抢锁。

---

## 第 4–6 轮 — 2026-08-18 — **发现并修掉「游戏根本跑不起来」的 Blocker**

起因：用户问「现在点开始飞行就能玩了吗」。查代码发现不能，且**前三轮的验证方式恰好绕开了这个问题**。

### Blocker A — 帧循环一帧都没跑过

`GameEngine` 是纯逻辑类（零 Compose import），`phase`/`temp`/`score` 是普通 `var` 而非 snapshot state。
`tick` 只在 **Canvas 的 draw lambda** 里读——那是绘制阶段，只让画面重绘，**不触发重组**。
于是 `when (engine.phase)` 只在首次组合求值一次。

更严重的是，加日志后发现**根因还要深一层**：

```
dbg=live autoPlay=true      ← intent extra 读到了
frameClock tick=1           ← withFrameNanos 只触发了 1 次，之后再没有
（无 autostart 日志）
```

**`withFrameNanos` 在 `DefaultWindowContainer` 里不会持续恢复**，协程停在第一次挂起，整个 `LaunchedEffect` 游戏循环一帧都没跑过。画面之所以看起来有内容，是因为首次组合绘制了一帧。

修复两处：
1. 主循环改 `delay(11L)` 驱动，不再依赖 `MonotonicFrameClock`
2. 引擎状态镜像到组合层（`phase`/`tempShown`/`scoreShown`/`bestShown`），相同值写入 snapshot state 不会触发失效，所以逐帧无条件赋值也不会每帧重组

### Blocker B — 永远显示「新纪录！」

`isRecord = score >= best`，但 `die()` 里已先把 `best` 更新成 `score`。改成在更新 best 之前算 `lastRunWasRecord`，并补了 2 个单测钉住。

### 为什么前三轮没发现

`cold` / `over` 两档 DEBUG 都是**首次组合之前**就把状态设好的，`when` 第一次求值就选对分支——恰好绕开了「状态变化能否触发重组」这条链路，也绕开了帧循环。

新增 `ow_debug live` 档补上：停在开始页，2.5s 后自动开局并自动扇翅，走的是和真人点按钮完全一样的路径。

### 第 6 轮结论 — 五态全部通过

五张截图两两差异全部非零（最小 6.88，是两张结算卡之间的正常差异），无重复帧：

| 截图 | 内容 |
|---|---|
| `r6-start` | 开始页，深色玻璃卡 + 暖色按钮 |
| `r6-cold` | 体温 18°，紫暮氛围，HUD 深色玻璃胶囊，冰柱/积雪冠清晰 |
| `r6-over` | 结算卡，暖色大分数 |
| `r6-live-playing` | **真实游玩态**：HUD 95°、障碍滚动、小鸟在飞 |
| `r6-live-over` | **真实结算态**：走完整局后自动进入 |

`NotStarted → Playing → GameOver` 三态切换全部经真实路径验证。

### 仍然不可判定

- 精确 dp 间距与尺寸、磨砂模糊半径
- 碰撞矩形与视觉轮廓的对齐（需要 `DEBUG` 描边）
- **真人点击「开始飞行」按钮本身**：`live` 档验证的是按钮回调之后的链路，按钮的射线命中没验（模拟器 adb tap 打不准）
- **覆盖层指针消费是否真挡住穿透**：只能真机扫射线

---

## 第 7 轮 — 2026-08-18 — 音效上机验证

音效**截图判不出来**，改用系统状态与日志作为判据。

### 证据

```
AudioPlaybackConfiguration piid:2423 type:android.media.MediaPlayer state:started
  usage=USAGE_GAME content=CONTENT_TYPE_SONIFICATION sampleRate=48000
new player piid:2407 package:tech.illusion.overwinter type:android.media.SoundPool
media.audio_flinger: 8 Tracks of which 2 are active
```

- ✅ 环境音（MediaPlayer）**state:started**，确实在放
- ✅ SoundPool 已为本应用创建
- ✅ logcat 无崩溃、无 SoundPool / MediaPlayer 异常
- ✅ APK 内 `assets/audio/*` 四个文件均为 `Stored / 0%`（未压缩），`openFd()` 可用

### 不可判定

- **四个音各自听起来对不对、音量平衡如何** —— 只能人耳判断，建议真机试玩
- 环境音 19s 循环点的接缝是否可闻（mp3 编码器留白导致）
- 扇翅"掐掉上一声"的连点手感

### 已知缺口

**撞枝没有音效。** 用户给的四个用途里没有撞击音，`坠落.mp3` 对应的是结算态。撞枝 −25° 是本作最重要的负反馈，目前完全无声。

---

## 第 8 轮 — 2026-08-18 — 撞枝音效

补上第 7 轮记录的缺口。素材 `撞击.mp3` 由用户追加。

- ✅ APK 内 `assets/audio/` 五个文件全部 `Stored / 0%`
- ✅ 环境音 MediaPlayer **state:started**，SoundPool 已创建
- ✅ logcat 无崩溃、无 SoundPool / MediaPlayer 异常
- ✅ 单测 23 个全绿，含新增的「撞击只记一次、0.8s 硬直期间不重复」

**裁剪**：原文件起音前有 0.151s 静音，碰撞反馈慢半拍会明显和画面脱节。裁掉头尾（0.125s~0.800s）+ 淡出，起音降到 0.026s。

**仍不可判定**：五个音各自听感、音量平衡、撞击与结算音同时响起时（撞击致死那一下）会不会打架 —— 都只能人耳判断。

---

## 第 9 轮 — 2026-08-18 — 花朵无敌（原 V2，用户要求提前实现）

新增 `ow_debug invin` 档专门拍这一帧。六张截图两两差异全部非零。

### 通过

`r9-invin.png` 逐条核对契约 §5c 的表现：

- ✅ 小鸟**嵌在树干里穿过去**（不是卡住、不是死亡）
- ✅ 暖白加色光晕
- ✅ 接触点白闪 + 雪雾
- ✅ HUD 倒计时环胶囊显示 **2.0s**，位于体温胶囊右侧
- ✅ 四角霜华明显退去（同为低温，比 `r9-cold` 亮一档）
- ✅ 体温 30°、得分 17 正常显示

单测 27 个全绿，新增 4 个钉住规则：

- 采到花朵获得 3.0s 无敌
- 无敌期间**一直嵌在树干里也一次血都不掉**，但体温照常流失
- 无敌耗尽后撞枝重新扣血
- 无敌期间触地当地板钳住、不判死

### 已知瑕疵

**余晖残影看不出来**——三重残影的 alpha 最高只有 0.255，被半径 86dp 的加色光晕盖住了。不影响可读性，未修。

### 仍不可判定

- 无敌 3 秒的手感是否合适（太长/太短）
- 光晕亮度在真机 HDR 下是否过曝
- 采到花朵那一瞬间没有专属音效（目前只有通用的 `pickup.mp3`）

## 门禁 B · 2026-08-26 「玩法」入口按钮 + 说明弹层（v3 → v4）

### 第 1 轮：不通过（Blocker）

`howtoB-01-start.png`：开始页卡片右上角**完全看不到「玩法」按钮**——不是变淡/裁切，是彻底不可见。
代码走查确认按钮确实在 `Box { Card(...); Button(...align(TopEnd)) }` 里、逻辑和坐标都对，问题是
`Card` 的 `backgroundMaterial(Material.Regular)` 是合成器层，不吃 Compose 兄弟节点绘制顺序——
叠在玻璃卡片上的悬浮徽章这个思路在这套 SDK 下直接不通（详见 memory
`backgroundmaterial-is-compositor-layer`）。

**修复**：外层从 `Box`（按钮叠在卡片上）改成 `Column`（按钮独立占一行，卡片上方右对齐，
跟卡片矩形完全不重叠）。重新编译，`BUILD SUCCESSFUL`。

### 第 2 轮：通过

`howtoB-03-start-fixed.png`：

- ✅ 「?玩法」胶囊按钮清晰可见，位于卡片正上方右侧，与卡片本身不重叠
- ✅ 容器色为暖金色柔和填充（约 0.3 alpha，与 `开始飞行` 同色系但明显更浅）
- ✅ 图标徽标 + 「玩法」文字为高饱和暖金色，清晰可辨，无描边
- ✅ 卡片其余元素（标题、副标题、tagline、历史最高分、开始飞行按钮、提示文字）与改动前完全一致

### 仍不可判定

- 点击「玩法」后弹层实际打开效果（BasicSheet 遮罩深浅、正文可滚动性）——受模拟器 adb tap
  命中小按钮不可靠所限，本轮未做点击验证，只验证了关闭态的按钮可见性；弹层内部结构已代码走查
  确认符合契约，建议后续用真机手势验证一次实际打开效果。

## 门禁 B · depth1 · 2026-09-20 — 空间分层与近景雪（四层拆分）

**设备**：`emulator-5554`　**APK commit**：`b2d268c`（四层 Canvas 上 z 偏移，HUD 与卡片跟着归位）

模拟器**只验四件事**，不对"浮起来了没有"给任何结论——单目截图判不出深度。

```
./gradlew assembleDebug && bash .spatialsdk/tools/verify-ui.sh depth1
```

锁内 68s（目标 90s / 硬上限 120s）。四态截图：`rdepth1-start.png` `rdepth1-cold.png`
`rdepth1-over.png` `rdepth1-invin.png`，另附 `rdepth1-live-playing.png` / `rdepth1-live-over.png`
（live 档自动开局 + 自动扇翅两次重组）。

### 逐条结论

1. **没有任何一层整个消失——通过。** 枝干、小鸟、近雪、开始卡/结算卡在对应状态截图里都能找到：
   `rdepth1-start.png` 有卡片+小鸟+枝干，`rdepth1-cold.png` 有枝干+小鸟+浆果+近雪，
   `rdepth1-invin.png` 小鸟嵌在树干里（无敌态正确表现）+ HUD 倒计时胶囊，结算卡见下方"截图有效性"。

2. **近雪出现了——通过。** `rdepth1-cold.png` 放大截图（裁剪见验证过程）能看到多枚明显大于
   背景雪粒的白色圆点，混在大量细小雪粒里，尺寸差异清晰可辨，符合近雪半径 3~7 / 远雪 0.9~3.1
   的设计比例。`drawNear` 在 `HomePage.kt` 里挂在独立的第四张 `Canvas(z = Z_NEAR)` 上，
   四个状态都无条件调用，不受 `phase` 影响。

3. **构图与改动前一致，两处预期内差异符合设计——通过，但范围要更正。**
   **更正（task-4，见 task-4-report.md）：本条实际只比对了位置和尺寸，没有比对色调/亮度**
   **（灰度直方图、动态范围等）。这正是 Fix 1 那个 `grain()` Overlay 退化成整屏蒙灰雾的
   回归能连过三轮评审的原因——色调没有一次被纳入这条对比。task-4 用
   `r9-cold.png` vs `rdepth1-cold.png` 的灰度 min/p1/p99/std 补做了色调测量，
   证实了这个回归；本条从这一轮起把"色调对比"也列为本门禁该检查的项目，
   不再只看构图位置。**
   - 与基线 `r6-start.png` / `r9-cold.png` / `r9-invin.png` / `r6-over.png` 逐张对比：
     小鸟、枝干、HUD 胶囊（体温条、得分、无敌倒计时）、卡片的位置与尺寸均未变化。
   - 预期差异 (a) 霜挪到窗面层：四个调试态的体温均不足以触发 `frost()` 的
     `k > 0.42` 阈值（`cold`=18°、`invin`=30°，同改动前的基线一致，均看不到霜），
     本轮截图无法直接看到"霜不再压住枝干/小鸟"的视觉差异，但已用
     `git show 4023451` 核对代码：frost 从旧 `drawGame` 里排在 `bird()` 之后（压在最前）
     挪进了新 `drawFar`（L0，最先画），语义上和契约描述的挪动完全吻合。
   - 预期差异 (b) 远雪从枝干/小鸟之前变到之后：同一次 `git show 4023451` diff 证实，
     旧版 `farSnow` 排在 `bird()` 之后（画在最前），新版挪进 `drawFar`（L0，最先画，
     被 `drawPlay`/`drawBird` 盖住）。`rdepth1-cold.png` 放大树干区域可见一枚被树干
     纹理蒙灰、不再是纯白圆点的雪粒——即远雪穿过树干时被遮挡变暗，视觉上验证了这一变化。
     这是设计要求的正确行为，不是缺陷。

4. **扇翅仍可触发，无崩溃——通过。指针穿透路径未覆盖，已更正。**
   **更正（task-4，见 task-4-report.md）：下面的 `live` 档证据只证明了帧循环/物理/碰撞/
   计分/结算这条链路没崩，不覆盖指针路径——`live` 档的自动扇翅是
   `HomePage.kt` 的 `LaunchedEffect` 直接调用 `engine.flap()`，从未经过
   `detectTapGestures`，所以它不能证明一次真实的点击/射线能穿过新增的三层
   `offset(z)` 全窗口碰撞体、落到父 `Box` 的 tap handler 上。这正是四层拆分改动
   在命中测试拓扑上引入的风险点，之前的说法把这条证据用错了地方。
   task-4 补做了 `adb -s emulator-5554 shell input tap` 的直接验证：结果是**没有拿到
   证据**——两次真实 tap（一次在 live 档 Playing/GameOver 附近，一次在 `ow_debug cold`
   冻结的 Playing 帧上）都没有触发预埋的探测日志。进一步查 logcat 找到了根因：
   `PvrVirtualInput` 服务反复报 `couldn't open uinput (r=-1 errno=13)` /
   `virtual touchpad service not added: -129`，SELinux 拒绝了它对 `uinput` 设备的
   `write`——也就是说这台模拟器上，adb 注入的触摸事件根本到不了空间输入管线，
   不是坐标猜错了。这条指针穿透证据目前只能标记为**未验证**，需要真机或者
   androidTest 探针（参考项目记忆 `androidtest-probe-for-untappable-ui`）来补。**
   `verify-ui.sh` 自带的 `live` 步骤在门禁窗口内完整跑通一次真实开局：
   `rdepth1-live-playing.png` 拍到自动扇翅后小鸟离地起飞（体温 98°，说明帧循环已在推进）；
   `rdepth1-live-over.png` 拍到 9s 后已经撞枝结算（穿过枝干 1、坚持 5.8s），证明输入→物理→
   碰撞→计分→结算这条链路整体存活。
   随后额外做了一次干净复核（`adb logcat -c` → `am start --es ow_debug live` → 等 8s →
   `logcat -d | grep -ai "SoundPool\|MediaPlayer\|FATAL\|AndroidRuntime"`）：
   全量 508 行 `overwinter` 相关日志里，`grep -ai "overwinter" | grep -ai "FATAL\|AndroidRuntime\|Exception\|crash"`
   **零命中**，只有正常的 `am start -S` 强停旧进程 + `pico-cli app stop` 收尾时的
   `AMS.Freeze` / `WorldSpaceNative destroy` 生命周期日志，没有崩溃堆栈。
   未见 SoundPool/MediaPlayer 的"新建播放器"日志（可能是缓冲区窗口太短或多会话日志量把它们
   挤掉），但没有异常堆栈这一条已经满足"没崩、管线活着"的验收线；音效表现本身按 AGENTS.md
   一贯说明仍需人耳/真机判断。

### 截图有效性（一个陷阱）

`verify-ui.sh` 首次跑出的 `rdepth1-over.png` 是一张**空房间**——没有任何窗口内容，
和 `r6-over.png` 基线完全对不上。没有直接判定为"结算卡消失"，而是先查了当时的 logcat：
19:33 前后 `tech.illusion.spacecube`（另一个会话的应用）与 `tech.illusion.overwinter` 之间有
`AppsFilter BLOCKED` 交互记录，怀疑是共享模拟器上另一会话的全空间应用短暂抢占导致这一帧拍到的
是空场景（而不是我们自己的窗口内容）。等设备锁转为 `free` 后，单独复核了一次
（`am start --es ow_debug over` → 等 10s → 截图 → `app stop`），拿到的
`rdepth1-over-recheck.png` 与 `r6-over.png` 构图、分数（27/9/6/约 49s、历史最高 57）完全一致。
**结论**：原始 `rdepth1-over.png` 是共享设备的陈旧/空场景伪影，不是本次改动引入的回归；
以 `rdepth1-over-recheck.png` 作为本轮"结算卡未消失"的有效证据。

### 深度效果本轮未验证

**深度效果（小鸟/枝干/近雪的前后关系）本轮未验证——单目截图判不出深度，待用户真机主观验收。**
五个 z 常量（`Z_PLAY=14dp` `Z_BIRD=20dp` `Z_NEAR=36dp` `Z_HUD=20dp` `Z_CARD=40dp`，**更正**：
上一版这里错写成"四层"——是五个常量，四个绘制层加一个 HUD 挂点）均取自兄弟
项目的经验值，本项目没有实测数据。真机验收后如需调参，只改 `SpatialDepth.kt` 里的五个常量。

### 单元测试

`./gradlew testDebugUnitTest --rerun-tasks` 全绿：`GameEngineTest` 26 + `SnowFieldTest` 8 +
`ExampleUnitTest` 1 = **35 个**（`AGENTS.md` 此前的"17 个"是两轮改动前的旧值，已一并更正）。

### 不可判定 / 未跑

- 深度效果本身（见上）。
- 撞枝/扇翅/结算音效的听感与音量平衡——只能人耳判断。
- SoundPool/MediaPlayer 的"新建播放器"日志本轮没有在 8s 窗口内直接抓到（见第 4 条），
  建议下次验证音效时把 `adb logcat -c` 到截图之间的窗口拉长，或者在 app 仍运行时执行
  `dumpsys audio` 而不是等它退出后再看 logcat。
- 「玩法」弹层的真实点击命中——不在本轮范围内，沿用第 v3→v4 轮的已知缺口。
