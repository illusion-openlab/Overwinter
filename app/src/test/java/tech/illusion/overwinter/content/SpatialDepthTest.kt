package tech.illusion.overwinter.content

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住 `SpatialDepth.kt` 五个 z 常量的相对顺序。
 *
 * 这五个数字的存在意义就是一个顺序：真机反馈调参的既定流程是"只改这五个数字，不改别的"，
 * 一旦允许顺序被悄悄改乱（比如两个值被调成相等，或者调反了），没有任何编译错误能拦住，
 * 只会在头显上表现为某一层盖错了别的层——这正是本轮 Fix 3（Z_HUD 曾和 Z_BIRD 字面量
 * 撞在一起）暴露出的那类问题。这个测试就是给这条顺序上的一道回归锁。
 */
class SpatialDepthTest {

    @Test fun `z depth order is strictly increasing from play to card`() {
        assertTrue(
            "Z_PLAY(${Z_PLAY.value}) 应该小于 Z_BIRD(${Z_BIRD.value})，" +
                "否则枝干会盖住小鸟",
            Z_PLAY < Z_BIRD,
        )
        assertTrue(
            "Z_BIRD(${Z_BIRD.value}) 应该小于 Z_HUD(${Z_HUD.value})，" +
                "否则小鸟会盖住 HUD 胶囊（两者曾经是相等的字面量，绘制顺序未定义）",
            Z_BIRD < Z_HUD,
        )
        assertTrue(
            "Z_HUD(${Z_HUD.value}) 应该小于 Z_NEAR(${Z_NEAR.value})，" +
                "否则 HUD 胶囊会挡住本该飘在最前面的近雪",
            Z_HUD < Z_NEAR,
        )
        assertTrue(
            "Z_NEAR(${Z_NEAR.value}) 应该小于 Z_CARD(${Z_CARD.value})，" +
                "否则开始卡/结算卡会被近雪片糊住",
            Z_NEAR < Z_CARD,
        )
    }

    @Test fun `bird stays close to the play layer because collision is computed in 2D`() {
        // 碰撞检测完全在 2D 世界坐标里算，z 偏移只影响视觉呈现。如果小鸟的 z 被调得
        // 远远超前于枝干层，头显里看起来小鸟会飘在半空、离枝干很远，但判定框依旧是
        // 2D 坐标系下的重叠——玩家会看到"明明离得老远却撞上了"或者反过来"贴脸了却
        // 判定没撞上"，观感和判定完全对不上。8dp 是一个留有余量但仍然"贴近"的上限。
        val gap = Z_BIRD.value - Z_PLAY.value
        assertTrue(
            "Z_BIRD 比 Z_PLAY 深了 ${gap}dp（上限 8dp）——碰撞是 2D 判定，" +
                "小鸟视觉上飘得太靠前会让撞枝看起来像撞了空气",
            gap <= 8f,
        )
    }
}
