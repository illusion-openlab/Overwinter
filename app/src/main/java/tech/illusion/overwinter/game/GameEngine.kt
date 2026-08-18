package tech.illusion.overwinter.game

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 纯逻辑层：不含任何 Compose / Spatial SDK 类型，可用普通 JUnit 在 JVM 上跑。
 * 所有单位都是 dp，与设计契约 1040x580dp 的世界坐标一致。
 */

// —— 世界尺寸（契约 §1 / §2c）——
const val WORLD_W = 1040f
const val WORLD_H = 580f
const val GROUND_Y = 520f

// —— 障碍（契约 §2c）——
const val SPACING = 330f      // 枝干水平间距
const val FIRST_X = 780f      // 第一组枝干的世界坐标：给开局约 3 秒空中跑道
const val GAP = 206f          // 缝隙高度
const val COL_W = 76f         // 碰撞矩形宽度
const val ICE = 12f           // 上障碍冰柱带
const val SAFE = 30f          // 收集物中心到判定线的最小距离

// —— 小鸟 ——
const val BIRD_X = 300f       // 屏幕 x 固定
const val BIRD_HALF_W = 26f
const val BIRD_HALF_H = 18f

// —— 手感 ——
private const val GRAVITY = 1450f
private const val FLAP_V = -440f
private const val MAX_FALL = 640f
private const val SCROLL_BASE = 150f
private const val SCROLL_MAX = 230f

// —— 数值契约 §5 ——
private const val TEMP_MAX = 100f
private const val TEMP_DRAIN = 2f       // 每秒
private const val TEMP_HIT = 25f        // 撞枝
private const val TEMP_BERRY = 18f      // 吃浆果
private const val HURT_TIME = 0.8f      // 撞击硬直
private const val BLOOM_SCORE = 5

// 缝隙中心范围：上下都留出足够余量，保证两端障碍都看得见
private const val GC_MIN = 200f
private const val GC_MAX = 336f

enum class Phase { NotStarted, Playing, GameOver }

class Obstacle(val index: Int, val gapCenter: Float) {
    val worldX = FIRST_X + index * SPACING
    val gapTop get() = gapCenter - GAP / 2f
    val gapBottom get() = gapCenter + GAP / 2f

    /** 浆果：约每 2 组一颗，落在缝隙中线附近（顺航线可得） */
    val hasBerry = Math.floorMod(index, 2) == 0
    val berryY get() = gapCenter + if (Math.floorMod(index, 3) == 1) -18f else 18f
    var berryEaten = false

    /** 花朵：约每 7 组一朵，贴判定线内侧 SAFE（要擦边才够得着） */
    val hasBloom = Math.floorMod(index, 7) == 2
    val bloomY get() = if (Math.floorMod(index, 2) == 0) gapBottom - SAFE else gapTop + SAFE
    var bloomPicked = false

    var passed = false
}

class GameEngine {

    var phase = Phase.NotStarted; private set
    var temp = TEMP_MAX; private set
    var score = 0; private set
    var berries = 0; private set
    var blooms = 0; private set
    var elapsed = 0f; private set
    var branchesPassed = 0; private set

    var birdY = 290f; private set
    var birdVy = 0f; private set
    var scroll = 0f; private set
    /** 音效用的纯计数器：只增不减，表现层比对上一帧的值来判边沿。引擎不认识音频。 */
    var flapEvents = 0; private set
    var pickupEvents = 0; private set
    var hitEvents = 0; private set

    var hurt = 0f; private set          // 撞击硬直剩余秒数，>0 时闪烁且不再扣血
    /** 仅供 DEBUG 截图验证：冻结物理但保留动画时钟 */
    var frozen = false; private set

    var best = 0
    /** 上一局是否破纪录。必须在 best 被更新之前算，否则永远为真。 */
    var lastRunWasRecord = false; private set

    val obstacles = ArrayList<Obstacle>()
    private var nextIndex = 0
    private var lastGc = 268f

    val scrollSpeed: Float
        get() = min(SCROLL_MAX, SCROLL_BASE + branchesPassed * 1.6f)

    /** 体温 0..1，供 HUD 与染色使用 */
    val warmth: Float get() = (temp / TEMP_MAX).coerceIn(0f, 1f)

    init { reset() }

    fun reset() {
        phase = Phase.NotStarted
        temp = TEMP_MAX; score = 0; berries = 0; blooms = 0
        elapsed = 0f; branchesPassed = 0
        birdY = 290f; birdVy = 0f; scroll = 0f; hurt = 0f
        flapEvents = 0; pickupEvents = 0; hitEvents = 0
        lastRunWasRecord = false; frozen = false      // 否则 DEBUG 启动后按「再飞一次」会一直卡在冻结态
        obstacles.clear(); nextIndex = 0; lastGc = 268f
        // 预铺满一屏，NotStarted 时背后就有景可看
        while (FIRST_X + nextIndex * SPACING < scroll + WORLD_W + SPACING * 2) spawnNext()
    }

    fun start() {
        if (phase == Phase.NotStarted || phase == Phase.GameOver) {
            reset(); phase = Phase.Playing
        }
    }

    /** 点击窗口任意处 = 扇翅一次。未开始时点击即开局。 */
    fun flap() {
        when (phase) {
            Phase.NotStarted -> { start(); birdVy = FLAP_V; flapEvents++ }
            Phase.Playing -> { birdVy = FLAP_V; flapEvents++ }
            Phase.GameOver -> Unit          // 结算态由按钮驱动，点击不复活
        }
    }

    private fun spawnNext() {
        // 伪随机 + 限制相邻落差，避免出现人力不可达的连续大跳
        val r = frac(sin(nextIndex * 127.1f + 11.7f) * 43758.55f)
        val raw = GC_MIN + r * (GC_MAX - GC_MIN)
        val gc = (lastGc + (raw - lastGc).coerceIn(-92f, 92f)).coerceIn(GC_MIN, GC_MAX)
        obstacles.add(Obstacle(nextIndex, gc))
        lastGc = gc
        nextIndex++
    }

    fun update(dt: Float) {
        if (frozen) { elapsed += dt; return }
        if (phase != Phase.Playing) {
            // 未开始 / 已结束时场景仍缓慢滚动，但不做物理与判定
            if (phase == Phase.NotStarted) {
                scroll += 52f * dt
                cull()
                birdY = 290f + sin(elapsed * 2.1f) * 26f
                elapsed += dt
            }
            return
        }

        elapsed += dt
        scroll += scrollSpeed * dt
        if (hurt > 0f) hurt = max(0f, hurt - dt)

        birdVy = min(MAX_FALL, birdVy + GRAVITY * dt)
        birdY += birdVy * dt

        // 触顶：软钳，不惩罚（契约 §5）
        if (birdY < 24f) { birdY = 24f; if (birdVy < 0f) birdVy = 0f }

        temp -= TEMP_DRAIN * dt
        cull()

        collide()

        // 触地：立即结束，不走扣体温流程（契约 §5）
        if (birdY + BIRD_HALF_H >= GROUND_Y) { birdY = GROUND_Y - BIRD_HALF_H; die(); return }
        if (temp <= 0f) { temp = 0f; die() }
    }

    private fun cull() {
        while (FIRST_X + nextIndex * SPACING < scroll + WORLD_W + SPACING * 2) spawnNext()
        obstacles.removeAll { it.worldX - scroll < -SPACING }
    }

    private fun collide() {
        val bx = BIRD_X
        for (o in obstacles) {
            val ox = o.worldX - scroll
            if (abs(ox - bx) > COL_W / 2f + BIRD_HALF_W + 60f) continue

            // 计分：穿过枝干中线
            if (!o.passed && ox + COL_W / 2f < bx - BIRD_HALF_W) {
                o.passed = true; branchesPassed++; score++
            }

            val overlapX = abs(ox - bx) < COL_W / 2f + BIRD_HALF_W
            if (overlapX && hurt <= 0f) {
                val hitTop = birdY - BIRD_HALF_H < o.gapTop
                val hitBottom = birdY + BIRD_HALF_H > o.gapBottom
                if (hitTop || hitBottom) {
                    temp -= TEMP_HIT
                    hurt = HURT_TIME
                    hitEvents++
                    birdVy = min(birdVy, 0f)
                }
            }

            // 收集物：圆形判定，不参与碰撞
            if (o.hasBerry && !o.berryEaten &&
                near(bx, birdY, ox, o.berryY, 34f)) {
                o.berryEaten = true; berries++; pickupEvents++
                temp = min(TEMP_MAX, temp + TEMP_BERRY)
            }
            if (o.hasBloom && !o.bloomPicked &&
                near(bx, birdY, ox, o.bloomY, 30f)) {
                o.bloomPicked = true; blooms++; score += BLOOM_SCORE; pickupEvents++
            }
        }
    }

    private fun near(ax: Float, ay: Float, bx: Float, by: Float, r: Float): Boolean {
        val dx = ax - bx; val dy = ay - by
        return dx * dx + dy * dy < r * r
    }

    /** 见 debugForce */
    internal fun debugPlace(scrollTo: Float, temperature: Float,
                            passed: Int, eaten: Int, picked: Int, freeze: Boolean = true) {
        scroll = scrollTo
        cull()
        val o = obstacles.minByOrNull { kotlin.math.abs(it.worldX - scroll - BIRD_X) }
        birdY = o?.gapCenter ?: 290f
        birdVy = -60f
        temp = temperature
        branchesPassed = passed; berries = eaten; blooms = picked
        score = passed + picked * BLOOM_SCORE
        frozen = freeze
    }

    /** 仅测试用：把小鸟放到指定高度 */
    internal fun debugPutBird(y: Float) { birdY = y; birdVy = 0f }

    internal fun debugEnd(seconds: Float) {
        frozen = false
        elapsed = seconds
        die()
        frozen = true
    }

    private fun die() {
        if (phase != Phase.Playing) return
        phase = Phase.GameOver
        lastRunWasRecord = score > best
        if (score > best) best = score
    }
}

internal fun frac(v: Float): Float = v - floor(v)

/**
 * 仅供截图验证使用（设计契约 §7）。通过 intent extra `ow_debug` 触发：
 *   adb shell am start -n <pkg>/.platform.LaunchActivity --es ow_debug cold|over
 * 把局面强制到某个状态并冻结物理，这样 6 秒后截图拿到的还是同一帧局面。
 */
fun GameEngine.debugForce(state: String) {
    when (state) {
        "cold" -> {
            start()
            debugPlace(scrollTo = FIRST_X - BIRD_X + SPACING * 1.6f, temperature = 18f,
                passed = 12, eaten = 5, picked = 1)
        }
        "over" -> {
            start()
            debugPlace(scrollTo = FIRST_X - BIRD_X + SPACING * 1.6f, temperature = 0f,
                passed = 27, eaten = 9, picked = 6)
            debugEnd(seconds = 48.6f)
        }
    }
}
