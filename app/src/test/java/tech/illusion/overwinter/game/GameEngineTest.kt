package tech.illusion.overwinter.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 钉住设计契约 §5 数值契约与 §2c 碰撞契约里的规则。 */
class GameEngineTest {

    private fun playing() = GameEngine().apply { start() }
    private fun run(e: GameEngine, seconds: Float, step: Float = 1f / 90f) {
        var t = 0f
        while (t < seconds) { e.update(step); t += step }
    }

    @Test fun `starts at full temperature and not playing`() {
        val e = GameEngine()
        assertEquals(100f, e.temp, 0.01f)
        assertEquals(Phase.NotStarted, e.phase)
    }

    @Test fun `temperature drains 2 per second`() {
        val e = playing()
        // 边扇翅边跑，保证这段时间里既没落地也没撞到东西
        var t = 0f; var since = 0f
        while (t < 2.5f) {
            if (since >= 0.3f) { e.flap(); since = 0f }
            e.update(1f / 90f); t += 1f / 90f; since += 1f / 90f
        }
        assertEquals(Phase.Playing, e.phase)
        assertEquals(95f, e.temp, 0.6f)   // 2.5s * 2 = 5
    }

    @Test fun `first obstacle is far enough to give a runway`() {
        val e = playing()
        val first = e.obstacles.minByOrNull { it.index }!!
        val runway = (first.worldX - BIRD_X) / e.scrollSpeed
        assertTrue("开局跑道只有 ${runway}s，玩家会一开局就撞", runway >= 2.5f)
    }

    @Test fun `flap gives upward velocity`() {
        val e = playing()
        e.flap()
        assertTrue("扇翅后应向上", e.birdVy < 0f)
    }

    @Test fun `tap on NotStarted begins the game`() {
        val e = GameEngine()
        e.flap()
        assertEquals(Phase.Playing, e.phase)
    }

    @Test fun `gap centres stay inside the visible band`() {
        val e = GameEngine()
        run(e, 0f)
        repeat(400) { e.update(1f / 60f) }
        assertTrue("至少要生成若干障碍", e.obstacles.size >= 2)
        e.obstacles.forEach {
            assertTrue("缝隙上沿不能顶出画面: ${it.gapTop}", it.gapTop > 40f)
            assertTrue("缝隙下沿不能埋进地面: ${it.gapBottom}", it.gapBottom < GROUND_Y - 40f)
        }
    }

    @Test fun `adjacent gaps never jump more than 92dp`() {
        val e = GameEngine()
        repeat(600) { e.update(1f / 60f) }
        val sorted = e.obstacles.sortedBy { it.index }
        sorted.zipWithNext { a, b ->
            val d = kotlin.math.abs(b.gapCenter - a.gapCenter)
            assertTrue("相邻缝隙落差 $d 超过 92dp，会出现不可达跳跃", d <= 92.01f)
        }
    }

    @Test fun `collectibles clear the judgment line by at least SAFE`() {
        val e = GameEngine()
        repeat(600) { e.update(1f / 60f) }
        e.obstacles.forEach { o ->
            if (o.hasBerry) {
                assertTrue("浆果越过上判定线", o.berryY - o.gapTop >= SAFE)
                assertTrue("浆果越过下判定线", o.gapBottom - o.berryY >= SAFE)
            }
            if (o.hasBloom) {
                assertTrue("花朵越过上判定线", o.bloomY - o.gapTop >= SAFE - 0.01f)
                assertTrue("花朵越过下判定线", o.gapBottom - o.bloomY >= SAFE - 0.01f)
            }
        }
    }

    @Test fun `bloom is much rarer than berry`() {
        val e = GameEngine()
        repeat(1200) { e.update(1f / 60f) }
        // 按 index 规则统计前 70 组
        var b = 0; var f = 0
        for (i in 0 until 70) {
            if (Math.floorMod(i, 2) == 0) b++
            if (Math.floorMod(i, 7) == 2) f++
        }
        assertEquals(35, b)
        assertEquals(10, f)
        assertTrue("花朵必须明显少于浆果", f * 3 < b)
    }

    @Test fun `ground contact ends the game without a temperature penalty`() {
        val e = playing()
        val before = e.temp
        run(e, 6f)                                   // 不扇翅，必然落地
        assertEquals(Phase.GameOver, e.phase)
        assertTrue("触地不应额外扣 25 度", before - e.temp < 25f)
    }

    @Test fun `ceiling is a soft clamp and costs nothing`() {
        val e = playing()
        repeat(40) { e.flap(); e.update(1f / 90f) }
        assertTrue("触顶应被钳住", e.birdY >= 24f - 0.01f)
        assertTrue("触顶不结束游戏", e.phase == Phase.Playing)
    }

    @Test fun `best score updates on game over`() {
        val e = playing()
        run(e, 6f)
        assertEquals(Phase.GameOver, e.phase)
        assertEquals(e.score, e.best)
    }

    @Test fun `record flag is false when the run does not beat best`() {
        val e = playing()
        run(e, 6f)                                   // 第一局：0 分也算首次纪录吗？
        val firstScore = e.score
        val firstFlag = e.lastRunWasRecord
        assertEquals("第一局分数应等于 best", firstScore, e.best)
        // 第二局打成一样的分数 —— 不算破纪录
        e.start(); run(e, 6f)
        assertEquals("同分不算破纪录", false, e.lastRunWasRecord)
        assertTrue("第一局若得分>0 应记为破纪录", !firstFlag || firstScore > 0)
    }

    @Test fun `record flag is computed before best is updated`() {
        val e = GameEngine()
        e.best = 999                                 // 人为设一个打不破的纪录
        e.start(); run(e, 6f)
        assertEquals(Phase.GameOver, e.phase)
        assertEquals("打不破纪录时不能显示新纪录", false, e.lastRunWasRecord)
        assertEquals("best 不应被更低的分数覆盖", 999, e.best)
    }

    @Test fun `flapEvents counts only real flaps`() {
        val e = GameEngine()
        assertEquals(0, e.flapEvents)
        e.flap()                                     // NotStarted 下点击即开局，也算一次扇翅
        assertEquals(1, e.flapEvents)
        e.flap(); e.flap()
        assertEquals(3, e.flapEvents)
        run(e, 6f)                                   // 摔死
        assertEquals(Phase.GameOver, e.phase)
        val before = e.flapEvents
        e.flap()                                     // 结算态点击不复活，也不该出声
        assertEquals("结算态不应产生扇翅事件", before, e.flapEvents)
    }

    @Test fun `eating a berry raises pickupEvents and temperature`() {
        val e = playing()
        // 把小鸟直接放到缝隙中线（浆果就在中线 ±18dp，收集半径 34dp），不冻结
        e.debugPlace(scrollTo = FIRST_X - BIRD_X + SPACING * 2f, temperature = 50f,
                     passed = 0, eaten = 0, picked = 0, freeze = false)
        val t0 = e.temp
        val p0 = e.pickupEvents
        repeat(3) { e.update(1f / 90f) }
        assertTrue("应吃到浆果", e.pickupEvents > p0)
        assertTrue("吃到后体温应回升", e.temp > t0)
    }

    @Test fun `hitting a branch raises hitEvents once per collision`() {
        val e = playing()
        e.debugPlace(scrollTo = FIRST_X - BIRD_X, temperature = 100f,
                     passed = 0, eaten = 0, picked = 0, freeze = false)
        val o = e.obstacles.minByOrNull { kotlin.math.abs(it.worldX - e.scroll - BIRD_X) }!!
        e.debugPutBird(o.gapTop - 40f)          // 缝隙上沿再往上 = 塞进树干实体里
        e.update(1f / 90f)
        assertEquals("撞上应记一次", 1, e.hitEvents)
        repeat(20) { e.update(1f / 90f) }       // 0.8s 硬直期间不该重复计数
        assertEquals("硬直期间不应重复计数", 1, e.hitEvents)
    }

    @Test fun `picking a bloom grants three seconds of invincibility`() {
        val e = playing()
        val o = e.obstacles.first { it.hasBloom }
        e.debugPlace(scrollTo = o.worldX - BIRD_X, temperature = 80f,
                     passed = 0, eaten = 0, picked = 0, freeze = false)
        e.debugPutBird(o.bloomY)
        e.update(1f / 90f)
        assertTrue("应采到花朵", e.blooms > 0)
        assertEquals("应获得 3 秒无敌", 3f, e.invincible, 0.05f)
    }

    @Test fun `invincibility blocks branch damage but not the cold`() {
        val e = playing()
        val o = e.obstacles.first { it.hasBloom }
        e.debugPlace(scrollTo = o.worldX - BIRD_X, temperature = 80f,
                     passed = 0, eaten = 0, picked = 0, freeze = false)
        e.debugPutBird(o.bloomY)
        e.update(1f / 90f)
        val tempAfterPick = e.temp
        val hitsBefore = e.hitEvents

        // 场景每帧都在滚，所以塞进树干后必须**立刻**断言，晚一点障碍就移开了
        e.debugPutBird(o.gapTop - 40f)
        e.update(1f / 90f)
        assertTrue("穿过枝干时应给表现层信号", e.passingThrough)
        assertEquals("无敌期间撞枝不应扣血", hitsBefore, e.hitEvents)
        assertTrue("仍在无敌中", e.invincible > 0f)

        // 体温单独看：挪到两组枝干正中间，那里没有收集物，不会被回温干扰
        e.debugPlace(scrollTo = o.worldX - BIRD_X + SPACING / 2f, temperature = 80f,
                     passed = 0, eaten = 0, picked = 0, freeze = false)
        val t0 = e.temp
        repeat(90) { e.update(1f / 90f) }
        assertTrue("无敌不保暖，体温照常流失", e.temp < t0)
    }

    @Test fun `invincibility expires and damage resumes`() {
        val e = playing()
        val o = e.obstacles.first { it.hasBloom }
        e.debugPlace(scrollTo = o.worldX - BIRD_X, temperature = 90f,
                     passed = 0, eaten = 0, picked = 0, freeze = false)
        e.debugPutBird(o.bloomY)
        e.update(1f / 90f)
        assertTrue(e.invincible > 0f)

        // 整整 3 秒都按在 y=60（那一带只有树干、没有收集物），每帧重设高度抵消重力。
        // 顺带验证：无敌期间一直嵌在树干里也一次血都不掉。
        repeat(300) { e.debugPutBird(60f); e.update(1f / 90f) }
        assertEquals("无敌应已耗尽", 0f, e.invincible, 0.001f)
        assertEquals("无敌期间一直嵌在树干里也不该扣血", 0, e.hitEvents)

        // 再把当前身边的障碍对准小鸟，塞进树干
        val cur = e.obstacles.minByOrNull { kotlin.math.abs(it.worldX - e.scroll - BIRD_X) }!!
        e.debugPlace(scrollTo = cur.worldX - BIRD_X, temperature = 90f,
                     passed = 0, eaten = 0, picked = 0, freeze = false)
        e.debugPutBird(cur.gapTop - 40f)
        e.update(1f / 90f)
        assertTrue("失效后撞枝应重新扣血", e.hitEvents > 0)
    }

    @Test fun `invincibility turns the ground into a floor instead of death`() {
        val e = playing()
        val o = e.obstacles.first { it.hasBloom }
        e.debugPlace(scrollTo = o.worldX - BIRD_X, temperature = 90f,
                     passed = 0, eaten = 0, picked = 0, freeze = false)
        e.debugPutBird(o.bloomY)
        e.update(1f / 90f)
        e.debugPutBird(GROUND_Y - 10f)             // 塞到地面以下
        repeat(30) { e.update(1f / 90f) }
        assertEquals("无敌期间触地不应结束", Phase.Playing, e.phase)
        assertTrue("应被地面钳住", e.birdY <= GROUND_Y - BIRD_HALF_H + 0.5f)
    }

    @Test fun `reset clears the audio event counters`() {
        val e = GameEngine()
        e.flap(); e.flap()
        assertTrue(e.flapEvents > 0)
        e.reset()
        assertEquals(0, e.flapEvents)
        assertEquals(0, e.pickupEvents)
        assertEquals(0, e.hitEvents)
        assertEquals(0f, e.invincible, 0.001f)
    }

    @Test fun `reset clears run state but keeps best`() {
        val e = playing()
        run(e, 6f)
        val best = e.best
        e.reset()
        assertEquals(100f, e.temp, 0.01f)
        assertEquals(0, e.score)
        assertEquals(Phase.NotStarted, e.phase)
        assertEquals(best, e.best)
    }

    @Test fun `scroll speed ramps with progress but stays bounded`() {
        val e = playing()
        val v0 = e.scrollSpeed
        repeat(3000) { e.flap(); e.update(1f / 120f) }
        assertTrue("速度应随进度提高", e.scrollSpeed >= v0)
        assertTrue("速度必须有上限", e.scrollSpeed <= 230.01f)
    }

    @Test fun `spawned indices are deterministic across instances`() {
        val a = GameEngine(); val b = GameEngine()
        repeat(300) { a.update(1f / 60f); b.update(1f / 60f) }
        assertEquals(a.obstacles.map { it.gapCenter }, b.obstacles.map { it.gapCenter })
    }

    @Test fun `gap centres actually vary`() {
        val e = GameEngine()
        repeat(900) { e.update(1f / 60f) }
        val cs = e.obstacles.map { it.gapCenter }.distinct()
        assertNotEquals("缝隙高度不能是一条直线", 1, cs.size)
    }
}
