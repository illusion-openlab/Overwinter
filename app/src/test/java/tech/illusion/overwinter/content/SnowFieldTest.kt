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
