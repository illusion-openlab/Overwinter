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
