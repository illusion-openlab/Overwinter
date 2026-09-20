# 越冬 · 空间分层与近景飘雪 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把越冬的单张游戏 Canvas 拆成 4 张带 z 深度的 Canvas，并新增一层飘在窗面之前的近景雪，让平面窗口有真实的前后纵深。

**Architecture:** 绘制层按深度拆成 L0 窗面 / L1 玩法 / L2 小鸟 / L3 近景四张叠放的 `Canvas`，每张挂 `com.pico.spatial.ui.foundation.layout.offset(z = ...)`。雪场数学抽到一个不含任何 Compose 类型的纯文件里，用 JVM 单测钉住参数；深度本身无法在 JVM 或单目截图里验证，留给真机主观验收。

**Tech Stack:** Kotlin · Jetpack Compose（PICO 提供的构件）· SpatialUI 6.0.0 · JUnit4 · Gradle/AGP

**Spec:** [`docs/superpowers/specs/2026-09-20-overwinter-spatial-depth-snow-design.md`](../specs/2026-09-20-overwinter-spatial-depth-snow-design.md)

## Global Constraints

- 容器不变：单个 `WindowContainer`，`style=1`（Planar），1040 × 580 dp。**不准改成 Volumetric**。
- `GameEngine.kt` 一行都不许改。它是纯逻辑层，不 import 任何 Compose / Spatial 类型，现有 17 个 JUnit 测试必须持续全绿。
- 禁止 Material / Material3（`androidx.compose.material`、`material3`、`MaterialTheme`）。颜色写 `PicoTheme.colorScheme.*`，字体写 `PicoTheme.typography.*`。本计划的新代码不引入任何新 UI 组件，天然满足。
- z 偏移必须 import `com.pico.spatial.ui.foundation.layout.offset`。AndroidX 的 `androidx.compose.foundation.layout.offset` 只有 x/y 重载，写 `z =` 会编译报 "no parameter named z"（SpaceMerge 在 SDK 6.0.0 上验证过）。
- 每条 gradle 命令都要在同一次 Bash 调用里自己 export，环境变量不跨调用保留：
  ```bash
  export PICO_HOME='/Users/zohar/Library/PICO/sdk'
  export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
  ```
- 世界坐标常量（`tech.illusion.overwinter.game`）：`WORLD_W = 1040f`、`WORLD_H = 580f`。
- 上机只用 `emulator-5554`，且必须走 `.spatialsdk/tools/verify-ui.sh`（内含抢锁）。真机 `PB311XKGL4160087B` 留给用户，agent 不要碰。抢不到锁就跳过，不要死等、不要抢别人的锁。

---

## File Structure

| 文件 | 职责 | 动作 |
| --- | --- | --- |
| `app/src/main/java/tech/illusion/overwinter/content/SnowField.kt` | 两层雪的纯数学 + 通用噪声/取模助手。**不 import 任何 Compose / Android 类型**，所以能用普通 JUnit 测 | 新建 |
| `app/src/test/java/tech/illusion/overwinter/content/SnowFieldTest.kt` | 钉住设计文档「二、两层雪」的参数表 | 新建 |
| `app/src/main/java/tech/illusion/overwinter/content/SpatialDepth.kt` | 5 个 z 常量，真机调参只改这一个文件 | 新建 |
| `app/src/main/java/tech/illusion/overwinter/content/GameArt.kt` | 插画层。`drawGame` 拆成 4 个按深度分组的入口 | 修改 |
| `app/src/main/java/tech/illusion/overwinter/content/HomePage.kt` | 单个 `Canvas` → 4 个叠放的 `Canvas` + HUD 的 z | 修改 |
| `app/src/main/java/tech/illusion/overwinter/content/Cards.kt` | `Scrim` 加 z，让卡片压在小鸟之前 | 修改 |
| `.spatialsdk/ui-verification-log.md` | 追加本轮模拟器验证记录 | 修改 |
| `AGENTS.md` | 更新「当前状态」与「下一步」 | 修改 |

为什么把雪场数学单独拆一个文件：这个工程已经靠「`GameEngine.kt` 不 import Compose，所以每条规则都能用 JUnit 在 JVM 上测」拿到过收益（AGENTS.md 明确写了「测试比上机验证快两个数量级」）。深度效果在 JVM 和单目截图里都判不出来，雪场参数是这次改动里**唯一**能被自动化钉住的部分，值得为它保持同样的纯净边界。

---

## Task 1: SnowField.kt — 雪场纯数学与远雪降量

把 `GameArt.kt` 里的 `rnd` / `fmod` 两个助手和远雪公式搬进一个不含 Compose 的新文件，加上近雪公式，并把远雪数量从 `50 + k*140` 降到 `36 + k*100`（前面要多一层近雪，总量得让出来）。本任务结束时近雪还没有被画出来，只是数学就位且被测试钉死。

**Files:**
- Create: `app/src/main/java/tech/illusion/overwinter/content/SnowField.kt`
- Create: `app/src/test/java/tech/illusion/overwinter/content/SnowFieldTest.kt`
- Modify: `app/src/main/java/tech/illusion/overwinter/content/GameArt.kt`（删 `rnd`、`fmod`、重写 `snow`）

**Interfaces:**
- Consumes: `tech.illusion.overwinter.game.WORLD_W`（`1040f`）、`WORLD_H`（`580f`）。
- Produces，Task 2 会用到：
  - `internal class Flake { var x: Float; var y: Float; var r: Float; var alpha: Float }` —— 可复用的输出容器，避免每帧给每片雪分配对象
  - `internal const val NEAR_SNOW_COUNT: Int = 22`
  - `internal fun farSnowCount(k: Float): Int`
  - `internal fun farFlake(i: Int, t: Float, k: Float, out: Flake)`
  - `internal fun nearFlake(i: Int, t: Float, out: Flake)`
  - `internal fun rnd(i: Int, seed: Float): Float`（从 GameArt.kt 移来，同包，调用点不用改）
  - `internal fun fmod(v: Float, m: Float): Float`（同上）

- [ ] **Step 1: 写失败的测试**

创建 `app/src/test/java/tech/illusion/overwinter/content/SnowFieldTest.kt`：

```kotlin
package tech.illusion.overwinter.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.illusion.overwinter.game.WORLD_H

/**
 * 钉住设计文档「二、两层雪」那张参数表。
 *
 * 深度本身在 JVM 和单目截图里都判不出来，雪场参数是这次改动里唯一能自动化钉住的部分——
 * 远近两层必须真的拉开，否则分层白做，而"拉开了没有"肉眼在模拟器截图上也看不准。
 */
class SnowFieldTest {

    private fun near(i: Int, t: Float): Flake = Flake().also { nearFlake(i, t, it) }
    private fun far(i: Int, t: Float, k: Float): Flake = Flake().also { farFlake(i, t, k, it) }

    /** 每秒下落距离。雪只往下掉，所以环绕造成的负差值用 fmod 就能还原成正的小步长。 */
    private fun fallPerSecond(sample: (Float) -> Float): Float {
        val dt = 0.01f
        var t = 0f
        var travelled = 0f
        repeat(100) {
            travelled += fmod(sample(t + dt) - sample(t), WORLD_H)
            t += dt
        }
        return travelled / (100 * dt)
    }

    @Test fun `far snow count scales with coldness`() {
        assertEquals(36, farSnowCount(0f))
        assertEquals(136, farSnowCount(1f))
    }

    @Test fun `near snow count is fixed at 22`() {
        assertEquals(22, NEAR_SNOW_COUNT)
    }

    @Test fun `near flakes are the big ones`() {
        for (i in 0 until NEAR_SNOW_COUNT) {
            val r = near(i, 1.7f).r
            assertTrue("i=$i r=$r 不在 3..7", r >= 3f && r <= 7f)
        }
    }

    @Test fun `near snow falls at least twice as fast as far snow`() {
        val nearAvg = (0 until NEAR_SNOW_COUNT)
            .map { i -> fallPerSecond { t -> near(i, t).y } }.average()
        val farAvg = (0 until farSnowCount(0f))
            .map { i -> fallPerSecond { t -> far(i, t, 0f).y } }.average()
        assertTrue("near=$nearAvg far=$farAvg 没拉开", nearAvg > farAvg * 2.0)
    }

    @Test fun `near snow sways at least three times wider`() {
        assertTrue("NEAR_SWAY=$NEAR_SWAY FAR_SWAY=$FAR_SWAY", NEAR_SWAY >= FAR_SWAY * 3f)
    }

    @Test fun `flake y always wraps inside the world`() {
        var t = 0f
        while (t < 30f) {
            for (i in 0 until NEAR_SNOW_COUNT) {
                val y = near(i, t).y
                // 用 <= 不用 <：fmod 对极小负数会浮点舍入到正好 m，那是无害的边界，
                // 卡死成 < 会做出一个偶发失败的测试。这里要抓的是"环绕坏了"，不是这个。
                assertTrue("near i=$i t=$t y=$y 出界", y >= 0f && y <= WORLD_H)
            }
            for (i in 0 until farSnowCount(1f)) {
                val y = far(i, t, 1f).y
                assertTrue("far i=$i t=$t y=$y 出界", y >= 0f && y <= WORLD_H)
            }
            t += 0.37f
        }
    }

    @Test fun `fmod wraps negatives into range`() {
        assertEquals(9f, fmod(-1f, 10f), 1e-4f)
        assertEquals(3f, fmod(13f, 10f), 1e-4f)
    }

    @Test fun `rnd is deterministic and in unit range`() {
        for (i in 0 until 200) {
            val a = rnd(i, 1.1f)
            assertEquals(a, rnd(i, 1.1f), 0f)
            assertTrue("i=$i a=$a 不在 [0,1)", a >= 0f && a < 1f)
        }
    }
}
```

- [ ] **Step 2: 跑测试确认它失败**

```bash
export PICO_HOME='/Users/zohar/Library/PICO/sdk'
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
cd /Users/zohar/WorkSpace/Project/PicoProjects/Overwinter
./gradlew testDebugUnitTest --tests '*SnowFieldTest*'
```

Expected：编译失败，报 `Unresolved reference: Flake` / `nearFlake` / `farSnowCount` / `NEAR_SWAY` 等。

> 如果报的是 `Cannot access 'rnd': it is internal`，说明这个模块的单元测试源集没有拿到 main 的 friend-path。此时把 `SnowField.kt` 里的 `internal` 全部改成 public（去掉修饰符），不要去动 Gradle 配置。

- [ ] **Step 3: 写实现**

创建 `app/src/main/java/tech/illusion/overwinter/content/SnowField.kt`：

```kotlin
package tech.illusion.overwinter.content

import tech.illusion.overwinter.game.WORLD_H
import tech.illusion.overwinter.game.WORLD_W
import kotlin.math.floor
import kotlin.math.sin

/**
 * 两层雪的纯数学。
 *
 * 这个文件**刻意不 import 任何 Compose / Android 类型**，理由和 `game/GameEngine.kt` 一样：
 * 不含平台类型才能用普通 JUnit 在 JVM 上跑，而上机验证比单测慢两个数量级。
 * 深度分层本身在 JVM 和单目截图里都判不出来，这张参数表是本次改动里唯一能自动化钉住的部分。
 *
 * 远雪画在 L0 窗面层（z=0），近雪画在 L3 近景层（z=Z_NEAR，最靠近玩家）。
 * 两层用**互不重叠的 rnd 种子**，否则它们会同相下落成一根根可见的柱子。
 */

private const val TWO_PI = 6.28318f

/** 水平摆幅。远近差 ~3 倍是"近的东西摆得更开"这条景深线索的主要载体。 */
internal const val FAR_SWAY = 7f
internal const val NEAR_SWAY = 22f

/** 哈希噪声，0..1。原先住在 GameArt.kt，搬来这里好让雪场数学自成一体；同包，调用点不用改。 */
internal fun rnd(i: Int, seed: Float): Float {
    val x = sin(i * 127.1f + seed * 311.7f) * 43758.55f
    return x - floor(x)
}

/** 永远返回 [0, m) 的取模。Kotlin 的 % 对负数返回负值，雪片环绕会直接飞出画布。 */
internal fun fmod(v: Float, m: Float): Float { val r = v % m; return if (r < 0f) r + m else r }

/** 一片雪的当前状态。复用同一个实例：这东西每帧被调用上百次，不能每次都分配。 */
internal class Flake {
    var x = 0f
    var y = 0f
    var r = 0f
    var alpha = 0f
}

/** 近雪固定 22 片，**不随体温加密**——它在最前面，密了会挡玩法。"冷"交给远雪和霜表达。 */
internal const val NEAR_SNOW_COUNT = 22

/** 远雪数量。原为 50 + k*140，因为前面新增了一层近雪，总量让出一档。 */
internal fun farSnowCount(k: Float): Int = (36f + k * 100f).toInt()

/**
 * 远雪：小、慢、稀，随体温变密变快变亮。公式除数量外与改动前逐字一致
 * （包括那个 6.28f 而不是 TWO_PI 的相位常数——保持像素级不变，别"顺手修正"）。
 */
internal fun farFlake(i: Int, t: Float, k: Float, out: Flake) {
    val speed = 16f + rnd(i, 4.4f) * 46f
    val x = fmod(rnd(i, 1.1f) * WORLD_W - t * (8f + speed * 0.12f), WORLD_W)
    val y = fmod(rnd(i, 2.2f) * WORLD_H + t * speed * (1f + k * 1.5f), WORLD_H)
    out.x = x + sin(t * 1.3f + rnd(i, 6.6f) * 6.28f) * FAR_SWAY
    out.y = y
    out.r = 0.9f + rnd(i, 3.3f) * 2.2f
    out.alpha = (0.3f + rnd(i, 5.5f) * 0.6f) * (0.5f + 0.5f * (1f - k * 0.4f))
}

/**
 * 近雪：大（3~7 vs 远雪 0.9~3.1）、快（~2.5×）、摆得开（±22 vs ±7），数量固定。
 * 种子全部避开远雪用过的 1.1/2.2/3.3/4.4/5.5/6.6。
 */
internal fun nearFlake(i: Int, t: Float, out: Flake) {
    val speed = 40f + rnd(i, 7.7f) * 115f
    val x = fmod(rnd(i, 8.8f) * WORLD_W - t * (20f + speed * 0.12f), WORLD_W)
    val y = fmod(rnd(i, 9.9f) * WORLD_H + t * speed, WORLD_H)
    out.x = x + sin(t * 1.1f + rnd(i, 10.1f) * TWO_PI) * NEAR_SWAY
    out.y = y
    out.r = 3f + rnd(i, 11.2f) * 4f
    out.alpha = 0.35f + rnd(i, 12.3f) * 0.40f
}
```

- [ ] **Step 4: 从 GameArt.kt 删掉搬走的两个助手，并改写 snow()**

在 `app/src/main/java/tech/illusion/overwinter/content/GameArt.kt` 里做三处编辑。

4a. 删掉 `rnd`（当前在第 125~128 行附近）：

```kotlin
private fun rnd(i: Int, seed: Float): Float {
    val x = sin(i * 127.1f + seed * 311.7f) * 43758.55f
    return x - floor(x)
}
```

整段删除。其余 10 处 `rnd(...)` 调用不用动 —— `SnowField.kt` 同包。

4b. 删掉 `fmod`（当前在第 362 行）：

```kotlin
private fun fmod(v: Float, m: Float): Float { val r = v % m; return if (r < 0f) r + m else r }
```

整段删除。

4c. 把 `snow` 整个换掉（当前第 364~373 行），顺便改名为 `farSnow`：

```kotlin
private fun snow(p: Painter, k: Float, t: Float) {
    val n = (50 + k * 140).toInt()
    for (i in 0 until n) {
        val speed = 16f + rnd(i, 4.4f) * 46f
        val x = fmod(rnd(i, 1.1f) * WORLD_W - t * (8f + speed * 0.12f), WORLD_W)
        val y = fmod(rnd(i, 2.2f) * WORLD_H + t * speed * (1f + k * 1.5f), WORLD_H)
        p.circle(WinterPalette.Snow, x + sin(t * 1.3f + rnd(i, 6.6f) * 6.28f) * 7f, y,
            0.9f + rnd(i, 3.3f) * 2.2f, (0.3f + rnd(i, 5.5f) * 0.6f) * (0.5f + 0.5f * (1f - k * 0.4f)))
    }
}
```

替换为：

```kotlin
/** L0 窗面层的远雪。公式在 SnowField.kt，这里只负责画。 */
private fun farSnow(p: Painter, k: Float, t: Float) {
    val f = Flake()
    val n = farSnowCount(k)
    for (i in 0 until n) {
        farFlake(i, t, k, f)
        p.circle(WinterPalette.Snow, f.x, f.y, f.r, f.alpha)
    }
}

/** L3 近景层的近雪。大、快、摆得开，数量固定，不随体温加密。 */
private fun nearSnow(p: Painter, t: Float) {
    val f = Flake()
    for (i in 0 until NEAR_SNOW_COUNT) {
        nearFlake(i, t, f)
        p.circle(WinterPalette.Snow, f.x, f.y, f.r, f.alpha)
    }
}
```

4d. `drawGame` 里的调用改名（当前第 165 行 `snow(p, k, t)`）：

```kotlin
    snow(p, k, t)
```

改为：

```kotlin
    farSnow(p, k, t)
```

此时 `nearSnow` 暂时没有调用点，编译会给一个 unused 警告 —— Task 2 会用上它。如果构建把警告当错误而失败，在 `nearSnow` 上加 `@Suppress("unused")`，Task 2 再删掉这个注解。

- [ ] **Step 5: 跑测试确认通过**

```bash
export PICO_HOME='/Users/zohar/Library/PICO/sdk'
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
cd /Users/zohar/WorkSpace/Project/PicoProjects/Overwinter
./gradlew testDebugUnitTest
```

Expected：PASS。SnowFieldTest 8 个 + GameEngineTest 17 个，**共 25 个全绿**。GameEngineTest 一个都不许挂 —— 挂了说明动到了不该动的东西。

- [ ] **Step 6: 确认编译通过**

```bash
export PICO_HOME='/Users/zohar/Library/PICO/sdk'
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
cd /Users/zohar/WorkSpace/Project/PicoProjects/Overwinter
./gradlew assembleDebug
```

Expected：BUILD SUCCESSFUL。

- [ ] **Step 7: 提交**

```bash
cd /Users/zohar/WorkSpace/Project/PicoProjects/Overwinter
git add app/src/main/java/tech/illusion/overwinter/content/SnowField.kt \
        app/src/test/java/tech/illusion/overwinter/content/SnowFieldTest.kt \
        app/src/main/java/tech/illusion/overwinter/content/GameArt.kt
git commit -m "$(cat <<'EOF'
雪场数学抽成可测的纯文件，远雪降量、近雪公式就位

rnd / fmod 从 GameArt.kt 搬进 SnowField.kt，该文件不 import 任何 Compose 类型，
所以两层雪的参数能用普通 JUnit 钉住。远雪 50+k*140 降到 36+k*100 给近雪让量。
近雪此时只有公式没有调用点。

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: GameArt 拆成 4 个按深度分组的绘制入口

把 `drawGame` 拆成 `drawFar` / `drawPlay` / `drawBird` / `drawNear`，并把近雪接进 `drawNear`。**本任务 HomePage 仍然只用一张 Canvas**，只是改成顺序调用四个入口 —— 这样这一步的视觉结果可以在模拟器截图上直接比对（近雪应该出现、霜的位置应该变化、其余不变），把「拆分有没有拆错」和「z 有没有生效」两件事分开验收。

**Files:**
- Modify: `app/src/main/java/tech/illusion/overwinter/content/GameArt.kt`（`drawGame` → 4 个入口，`drawDim` 并入 `drawNear`）
- Modify: `app/src/main/java/tech/illusion/overwinter/content/HomePage.kt:124-134`（Canvas 内容改成调 4 个入口）

**Interfaces:**
- Consumes（Task 1 产出）：`Flake`、`NEAR_SNOW_COUNT`、`nearFlake`、`farSnowCount`、`farFlake`。
- Produces，Task 3 会用到 —— 四个 `DrawScope` 扩展函数，签名完全一致：
  - `fun DrawScope.drawFar(e: GameEngine, art: Art, t: Float)`
  - `fun DrawScope.drawPlay(e: GameEngine, art: Art, t: Float)`
  - `fun DrawScope.drawBird(e: GameEngine, art: Art, t: Float)`
  - `fun DrawScope.drawNear(e: GameEngine, art: Art, t: Float)`
  - `drawGame` 和 `drawDim` 被删除，不再存在。

- [ ] **Step 1: 替换 GameArt.kt 里的 drawGame**

把 `drawGame` 那一整段（Task 1 改过之后行号已漂，按内容找）：

```kotlin
fun DrawScope.drawGame(e: GameEngine, art: Art, t: Float) {
    val p = Painter(this, size.width / WORLD_W)
    val k = coldness(e.warmth)
    background(p, art, e.scroll, e.warmth)
    haze(p, k)
    obstacles(p, art, e, k, t)
    pickups(p, art, e, t)
    bird(p, art, e, t)
    farSnow(p, k, t)
    frost(p, if (e.invincible > 0f) k * 0.38f else k)
    grain(p, art)
}
```

换成四个入口：

```kotlin
/**
 * 按深度分组的四个绘制入口。每个入口画到自己那张 Canvas 上，Canvas 再各自挂 z 偏移
 * （见 SpatialDepth.kt）。同一张 Canvas 内部表达不了深度，这就是要拆开的唯一原因。
 *
 * 图元顺序相对改动前有**两处**变化，都是 L0 分组的必然结果：
 *  1. frost 从最前挪到 L0。语义上"这扇窗户结霜了"成立，顺带不再糊住左上/右上的 HUD 胶囊。
 *  2. (farSnow, frost) 这一对整体从 bird 之后挪到了 obstacles 之前，所以远雪现在画在
 *     枝干和小鸟**之下**而不是之上。这不是拆错了——远雪属于 L0，本来就该在最后面。
 * 外加 nearSnow 是新画的，在近景层最前。
 */

/** L0 窗面层（z = 0）：远景林、雾、远雪、四角结霜。 */
fun DrawScope.drawFar(e: GameEngine, art: Art, t: Float) {
    val p = Painter(this, size.width / WORLD_W)
    val k = coldness(e.warmth)
    background(p, art, e.scroll, e.warmth)
    haze(p, k)
    farSnow(p, k, t)
    // 无敌期间减轻结霜，和改动前的实参逐字一致
    frost(p, if (e.invincible > 0f) k * 0.38f else k)
}

/** L1 玩法层（z = Z_PLAY）：枝干与浆果/花。 */
fun DrawScope.drawPlay(e: GameEngine, art: Art, t: Float) {
    val p = Painter(this, size.width / WORLD_W)
    obstacles(p, art, e, coldness(e.warmth), t)
    pickups(p, art, e, t)
}

/** L2 小鸟层（z = Z_BIRD）：只比枝干前 6dp——碰撞是在 2D 平面算的，浮太前撞枝会像撞了空气。 */
fun DrawScope.drawBird(e: GameEngine, art: Art, t: Float) {
    bird(Painter(this, size.width / WORLD_W), art, e, t)
}

/** L3 近景层（z = Z_NEAR，最靠近玩家）：近雪、胶片颗粒、结算压暗。 */
fun DrawScope.drawNear(e: GameEngine, art: Art, t: Float) {
    val p = Painter(this, size.width / WORLD_W)
    nearSnow(p, t)
    grain(p, art)
    // 原 drawDim。必须留在最前面这一层，放 L0 就只压得暗背景。
    if (e.phase == Phase.GameOver) p.d.drawRect(Color(0xFF0C1420), alpha = 0.40f)
}
```

- [ ] **Step 2: 删掉 GameArt.kt 末尾的 drawDim**

把 GameArt.kt 末尾的 `drawDim` 整段删除：

```kotlin
/** 结算态：原地压暗，不切场景 */
fun DrawScope.drawDim(e: GameEngine) {
    if (e.phase == Phase.GameOver) drawRect(Color(0xFF0C1420), alpha = 0.40f)
}
```

它的逻辑已经内联进 `drawNear` 了。

- [ ] **Step 3: 如果 Task 1 给 nearSnow 加过 @Suppress("unused")，现在删掉**

`nearSnow` 现在被 `drawNear` 调用了，注解不再需要。

- [ ] **Step 4: 改 HomePage.kt 的 Canvas 内容**

把 HomePage.kt 里那张 Canvas 整段：

```kotlin
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION") tick        // 订阅重组
            val a = art
            if (a == null) {
                drawRect(Color(0xFF8FBEDB))            // 素材加载中的底色
            } else {
                drawGame(engine, a, clock)
                drawDim(engine)
            }
        }
```

换成：

```kotlin
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION") tick        // 订阅重组
            val a = art
            if (a == null) {
                drawRect(Color(0xFF8FBEDB))            // 素材加载中的底色
            } else {
                // Task 3 会把这四行拆到四张各自带 z 偏移的 Canvas 上。
                // 现在先单层顺序调用，好让"拆分有没有拆错"能独立于"z 有没有生效"验收。
                drawFar(engine, a, clock)
                drawPlay(engine, a, clock)
                drawBird(engine, a, clock)
                drawNear(engine, a, clock)
            }
        }
```

- [ ] **Step 5: 编译并跑测试**

```bash
export PICO_HOME='/Users/zohar/Library/PICO/sdk'
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
cd /Users/zohar/WorkSpace/Project/PicoProjects/Overwinter
./gradlew assembleDebug testDebugUnitTest
```

Expected：BUILD SUCCESSFUL，25 个测试全绿。

如果报 `Unresolved reference: drawRect` 在 `drawNear` 里 —— 检查用的是 `p.d.drawRect(...)` 而不是裸 `drawRect(...)`；`Painter.d` 才是 `DrawScope`。（两种写法在 `DrawScope` 扩展函数里其实都能编译，但统一走 `p.d` 和同层其他图元一致。）

- [ ] **Step 6: 提交**

```bash
cd /Users/zohar/WorkSpace/Project/PicoProjects/Overwinter
git add app/src/main/java/tech/illusion/overwinter/content/GameArt.kt \
        app/src/main/java/tech/illusion/overwinter/content/HomePage.kt
git commit -m "$(cat <<'EOF'
drawGame 拆成按深度分组的四个入口，近雪接上画面

drawFar / drawPlay / drawBird / drawNear。frost 挪到 L0 窗面层（唯一的刻意观感变化），
drawDim 内联进 drawNear。HomePage 仍是单张 Canvas 顺序调用，z 偏移下一步再加。

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: 上 z —— 四层 Canvas 与 HUD / 卡片的深度归位

**Files:**
- Create: `app/src/main/java/tech/illusion/overwinter/content/SpatialDepth.kt`
- Modify: `app/src/main/java/tech/illusion/overwinter/content/HomePage.kt`（4 张 Canvas + HUD 的 z）
- Modify: `app/src/main/java/tech/illusion/overwinter/content/Cards.kt`（`Scrim` 的 z）

**Interfaces:**
- Consumes（Task 2 产出）：`drawFar` / `drawPlay` / `drawBird` / `drawNear`，签名都是 `(e: GameEngine, art: Art, t: Float)`。
- Produces：`Z_PLAY` / `Z_BIRD` / `Z_NEAR` / `Z_HUD` / `Z_CARD`，类型都是 `androidx.compose.ui.unit.Dp`。真机调参只改这五个值。

HUD 和卡片**必须**在这一步一起改。它们是 Canvas 的兄弟节点，现在靠绘制顺序压在最上面；一旦 Canvas 有了 z，绘制顺序就不再决定前后 —— 小鸟在 +20dp 而卡片还在 0，小鸟会穿过结算卡浮在它前面。

- [ ] **Step 1: 建 SpatialDepth.kt**

创建 `app/src/main/java/tech/illusion/overwinter/content/SpatialDepth.kt`：

```kotlin
package tech.illusion.overwinter.content

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 各绘制层与 UI 节点的 z 深度。正值朝向玩家。
 *
 * **真机调参只改这个文件。** 深度效果在单目截图里基本不可见，模拟器门禁判不出"浮起来了没有"，
 * 唯一可靠的验收是用户戴头显看，然后说"再往前/往后"，改这里的数字重出包。
 *
 * 取值依据（本项目没有实测数据，全部来自兄弟项目）：
 *  · SpaceMerge 在同类**平面**窗口里 `offset(z = 24.dp)` 拿到可见的浮起效果
 *  · SpaceVinyl 在**体积**窗口里实测 200dp 安全、280dp 被前边界裁掉
 * 平面窗口的深度上限没人量过，可能远小于体积窗口，所以最前一档压在 40dp，
 * 只比已知有效值高一档。**失败症状是某一层或卡片整个消失**，不是渐变模糊。
 *
 * 必须配 `import com.pico.spatial.ui.foundation.layout.offset`。
 * AndroidX 的同名 offset 只有 x/y 重载，写 z 会报 "no parameter named z"。
 */

/** L1 玩法层：枝干与浆果。 */
val Z_PLAY: Dp = 14.dp

/** L2 小鸟层。只比 Z_PLAY 前 6dp——碰撞在 2D 平面算，浮太前撞枝会看起来像撞了空气。 */
val Z_BIRD: Dp = 20.dp

/** L3 近景层：近雪、颗粒、结算压暗。最靠近玩家。 */
val Z_NEAR: Dp = 36.dp

/** HUD 胶囊。与小鸟同档，好让近雪从它前面飘过。 */
val Z_HUD: Dp = 20.dp

/** 开始卡 / 结算卡。压在最前——模态 UI 的可读性优先于"雪飘在前面"，不能让雪片糊住按钮。 */
val Z_CARD: Dp = 40.dp
```

- [ ] **Step 2: HomePage.kt 换成 4 张 Canvas**

先加 import（放在现有的 `import com.pico.spatial.ui.foundation.hover.spatialHoverEffect` 附近，保持 import 块按包名排序）：

```kotlin
import com.pico.spatial.ui.foundation.layout.offset
```

然后把 Task 2 留下的那张 Canvas 整段：

```kotlin
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION") tick        // 订阅重组
            val a = art
            if (a == null) {
                drawRect(Color(0xFF8FBEDB))            // 素材加载中的底色
            } else {
                // Task 3 会把这四行拆到四张各自带 z 偏移的 Canvas 上。
                // 现在先单层顺序调用，好让"拆分有没有拆错"能独立于"z 有没有生效"验收。
                drawFar(engine, a, clock)
                drawPlay(engine, a, clock)
                drawBird(engine, a, clock)
                drawNear(engine, a, clock)
            }
        }
```

换成四张：

```kotlin
        // 四张叠放的 Canvas，各自挂 z 偏移。同一张 Canvas 内部表达不了深度，这是拆开的唯一原因。
        // 每张都要读 tick 订阅重组——漏掉哪张，哪一层就不动了。
        // offset 必须是 com.pico.spatial.ui.foundation.layout 那个，AndroidX 的没有 z 参数。
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION") tick
            val a = art
            if (a == null) drawRect(Color(0xFF8FBEDB)) else drawFar(engine, a, clock)
        }
        Canvas(Modifier.fillMaxSize().offset(z = Z_PLAY)) {
            @Suppress("UNUSED_EXPRESSION") tick
            art?.let { drawPlay(engine, it, clock) }
        }
        Canvas(Modifier.fillMaxSize().offset(z = Z_BIRD)) {
            @Suppress("UNUSED_EXPRESSION") tick
            art?.let { drawBird(engine, it, clock) }
        }
        Canvas(Modifier.fillMaxSize().offset(z = Z_NEAR)) {
            @Suppress("UNUSED_EXPRESSION") tick
            art?.let { drawNear(engine, it, clock) }
        }
```

素材加载中的底色 `Color(0xFF8FBEDB)` 只留在 L0：另外三层在 `art == null` 时什么都不画，让底色透出来。

- [ ] **Step 3: HomePage.kt 给 HUD 加 z**

把 `Phase.Playing ->` 分支里 `Hud(...)` 的这一行：

```kotlin
                modifier = Modifier.fillMaxSize(),
```

换成：

```kotlin
                // 和小鸟同档。不给 z 的话 HUD 会沉到枝干后面去——它是 Canvas 的兄弟节点，
                // 一旦 Canvas 有了 z，绘制顺序就不再决定前后了。
                modifier = Modifier.fillMaxSize().offset(z = Z_HUD),
```

`Hud.kt` 一行都不用改 —— 它已经收一个 `modifier` 参数了。

- [ ] **Step 4: Cards.kt 给 Scrim 加 z**

先加 import（放在 `import androidx.compose.ui.unit.dp` 之后）：

```kotlin
import com.pico.spatial.ui.foundation.layout.offset
```

然后把 `Scrim` 里的 modifier 链：

```kotlin
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
```

改成：

```kotlin
        modifier = Modifier
            .fillMaxSize()
            // 压在所有绘制层之前。不加这个，小鸟（Z_BIRD=20dp）会穿过结算卡浮在它前面。
            // 比 Z_NEAR 还靠前是刻意的：模态 UI 的可读性优先于"雪飘在最前面"，
            // 不能让近雪片糊住"开始飞行"按钮。
            .offset(z = Z_CARD)
            .pointerInput(Unit) {
```

`StartCard` 和 `ResultCard` 都走 `Scrim`，所以改这一处就够了。**不要动**下面那段 `awaitPointerEvent().changes.forEach { it.consume() }` —— 那是防止射线穿透到画布触发扇翅的，不是死代码。

- [ ] **Step 5: 编译并跑测试**

```bash
export PICO_HOME='/Users/zohar/Library/PICO/sdk'
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
cd /Users/zohar/WorkSpace/Project/PicoProjects/Overwinter
./gradlew assembleDebug testDebugUnitTest
```

Expected：BUILD SUCCESSFUL，25 个测试全绿。

如果报 `No parameter named 'z' found` —— import 拿错了，检查是不是 IDE 自动补成了 `androidx.compose.foundation.layout.offset`。必须是 `com.pico.spatial.ui.foundation.layout.offset`。

- [ ] **Step 6: 确认 release classpath 没被 Material 污染**

这个工程有过 `ui-tooling` 把 `androidx.compose.material` 带进 release 包的历史，本次新增了 import，顺手复查一次：

```bash
export PICO_HOME='/Users/zohar/Library/PICO/sdk'
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
cd /Users/zohar/WorkSpace/Project/PicoProjects/Overwinter
grep -rn "androidx.compose.material" app/src/main/java/ || echo "源码零引用 ✓"
```

Expected：`源码零引用 ✓`。

- [ ] **Step 7: 提交**

```bash
cd /Users/zohar/WorkSpace/Project/PicoProjects/Overwinter
git add app/src/main/java/tech/illusion/overwinter/content/SpatialDepth.kt \
        app/src/main/java/tech/illusion/overwinter/content/HomePage.kt \
        app/src/main/java/tech/illusion/overwinter/content/Cards.kt
git commit -m "$(cat <<'EOF'
四层 Canvas 上 z 偏移，HUD 与卡片跟着归位

L0 窗面 0 / L1 玩法 14dp / L2 小鸟 20dp / L3 近景 36dp，HUD 20dp、卡片 40dp。
五个常量集中在 SpatialDepth.kt，真机调参只改这一个文件。
HUD 和卡片是必须的连带改动——Canvas 有了 z 之后绘制顺序不再决定前后，
不给它们 z 的话小鸟会穿过结算卡浮在前面。

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: 模拟器门禁 B 验证与文档更新

模拟器**只验四件事**，不对"浮起来了没有"给任何结论 —— 单目截图判不出深度。

**Files:**
- Modify: `.spatialsdk/ui-verification-log.md`
- Modify: `AGENTS.md`

**Interfaces:**
- Consumes：Task 3 产出的 debug APK（`app/build/outputs/apk/debug/app-debug.apk`）。
- Produces：无代码产出。

- [ ] **Step 1: 跑门禁 B**

```bash
export PICO_HOME='/Users/zohar/Library/PICO/sdk'
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
cd /Users/zohar/WorkSpace/Project/PicoProjects/Overwinter
./gradlew assembleDebug && bash .spatialsdk/tools/verify-ui.sh depth1
```

脚本自己抢锁、轮询就位、拍三态、释放锁。**抢不到锁它会干净退出并打印重跑命令 —— 那就等一会儿重跑，不要死等、不要去抢别人的锁、不要退回手写固定 sleep。**

- [ ] **Step 2: 看截图，逐条判这四件事**

```bash
cd /Users/zohar/WorkSpace/Project/PicoProjects/Overwinter
ls -la .spatialsdk/ui-verify/
```

用 Read 工具打开 `depth1` 那几张，逐条核对：

1. **没有任何一层整个消失**（对应风险 1，深度余量超限）。判据：枝干、小鸟、近雪、开始卡/结算卡，四样都能在对应状态的图里找到。少了哪样就是那一层被前边界裁掉了 —— 调小 `SpatialDepth.kt` 里对应的常量重跑。
2. **近雪出现了**。判据：画面上能看到一批明显比背景雪粒大的白点（近雪半径 3~7，远雪 0.9~3.1）。看不到就回头查 `drawNear` 有没有被调用、那张 Canvas 有没有读 `tick`。
3. **构图与改动前一致**，除了两处**预期内**的变化。判据：和 `.spatialsdk/ui-verify/` 里上一轮的同状态截图对比，小鸟、枝干、HUD 胶囊、卡片的位置尺寸都没动。预期内的两处差异是：
   - 霜从最前挪到了窗面层（四角结霜不再压在枝干和小鸟上）
   - **远雪从枝干/小鸟之前变到了之后**——远雪归 L0，本来就该在最后面。看到远雪被枝干挡住是对的，不是 bug
4. **扇翅仍可触发**（对应风险 3，指针穿透）。判据：
   ```bash
   export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"
   adb -s emulator-5554 logcat -c
   # 启动 live 档（2.5s 后自动开局并自动扇翅），跑 8 秒。
   # sleep 走 adb shell——宿主前台 sleep 被工具拦。
   adb -s emulator-5554 shell am start -S \
     -n tech.illusion.overwinter/.platform.LaunchActivity --es ow_debug live
   adb -s emulator-5554 shell sleep 8
   adb -s emulator-5554 logcat -a -d | grep -ai "SoundPool\|MediaPlayer\|FATAL\|AndroidRuntime" | tail -20
   ```
   Expected：没有 FATAL / AndroidRuntime 异常。**读 logcat 一律带 `-a`** —— 不带的话 grep 会把含控制字节的日志当二进制静默跳过，零输出会被误判成"应用没打日志"。
   
   这一步只证明"没崩、音效管线活着"。射线真的点不点得到按钮，仍然只有真机能判。

   > 门禁 B 的锁已经在 Step 1 结束时释放了，这一步是在锁外单独用设备。如果此时设备被别的会话占走，跳过这一步，在验证记录里写明"指针回归未跑"。

- [ ] **Step 3: 追加验证记录**

在 `.spatialsdk/ui-verification-log.md` 末尾追加一节，照文件里既有的格式写。必须包含：

- 轮次 `depth1`、日期、设备 `emulator-5554`、APK 的 git commit
- 上面四条的逐条结论（通过 / 不通过 + 判据是什么）
- **明确写一句**：「深度效果（小鸟/枝干/近雪的前后关系）本轮未验证 —— 单目截图判不出深度，待用户真机主观验收。」
- 如果 Step 2 的第 4 条因为设备被占跳过了，写明

- [ ] **Step 4: 更新 AGENTS.md**

两处改动：

a. 「结构与分层」那个代码块里补两行新文件：

```
content/SnowField.kt     两层雪的纯数学，不含 Compose 类型，能用 JUnit 测
content/SpatialDepth.kt  各层 z 深度常量。真机调参只改这里
```

并把 `content/GameArt.kt` 那行的说明改成提到四个绘制入口。

b. 「当前状态」和「下一步」：记下四层 z 分层与近景雪已实现、模拟器门禁 depth1 的结论、以及**待用户真机验收深度效果**这一条未决项。单测数量从 17 更新为 25。

- [ ] **Step 5: 提交**

```bash
cd /Users/zohar/WorkSpace/Project/PicoProjects/Overwinter
# 截图本身被 .gitignore 挡着（.spatialsdk/ui-verify/*.png），只提交文字记录
git add .spatialsdk/ui-verification-log.md AGENTS.md
git commit -m "$(cat <<'EOF'
门禁 B depth1：四层未被裁掉、近雪可见、构图未变、无崩溃

深度效果本轮未验证——单目截图判不出前后关系，待用户真机主观验收。

Co-Authored-By: Claude Opus 5 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## 交付后：等用户真机反馈

这功能的验收终点不在 agent 这边。把包给用户，等他戴 `PB311XKGL4160087B` 看完之后说「小鸟再往前」「雪太前了」之类，然后**只改 `SpatialDepth.kt` 里那五个常量**重出包。不要因为一句主观反馈去动分层结构或雪场公式。

已知的三条风险和它们的失败症状：

| 风险 | 症状 | 回退 |
| --- | --- | --- |
| Planar 窗口深度余量超限 | 某一层或卡片**整个消失**（不是模糊） | 调小 `SpatialDepth.kt` 的值 |
| 4 层全屏合成拖累帧率 | 掉帧、扇翅手感变钝 | 合并 L1/L2（小鸟与枝干同档），或整体 revert |
| 指针穿透 | 点窗口不扇翅，或卡片外遮罩区域又开始穿透 | 检查 `Cards.kt` 的 `Scrim` 消费逻辑；必要时把 `offset(z)` 挪到 `pointerInput` 之后 |
