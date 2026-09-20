package tech.illusion.overwinter.content

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.illusion.overwinter.game.COL_W
import tech.illusion.overwinter.game.GROUND_Y
import tech.illusion.overwinter.game.GameEngine
import tech.illusion.overwinter.game.ICE
import tech.illusion.overwinter.game.Phase
import tech.illusion.overwinter.game.WORLD_H
import tech.illusion.overwinter.game.WORLD_W
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * 插画层。这一层运行时不经过主题系统——颜色来自水彩位图本身加一层按体温计算的染色。
 * 见设计契约 §2b。
 */
object WinterPalette {
    val Cold = Color(0xFF283A54)        // 低温染色目标
    val Icicle = Color(0xFFE2F0FC)
    val IcicleTip = Color(0xFFFFFFFF)
    val Stem = Color(0xB35A4430)
    val BerryGlow = Color(0xFFFF7C68)
    val InvinGlow = Color(0xFFFFF6D6)      // 无敌光晕，暖白
    val HazeWarm = Color(0xFFE9F1F8)
    val HazeCold = Color(0xFF1A283E)
    val Frost = Color(0xFFD4ECFB)
    val Snow = Color(0xFFFFFFFF)
}

private val ART = listOf(
    "mtn_0", "mtn_1", "mtn_2", "mtn_3", "mtn_4",
    "trunk_tall", "trunk_a", "trunk_b", "trunk_c", "trunk_d",
    "berry_a", "berry_b", "berry_c", "bloom_a", "bloom_b",
    "bird_body", "bird_wing", "grain",
)

class Art(val img: Map<String, ImageBitmap>) {
    operator fun get(k: String): ImageBitmap = img.getValue(k)
    val moods = listOf(img.getValue("mtn_0"), img.getValue("mtn_1"),
        img.getValue("mtn_2"), img.getValue("mtn_3"), img.getValue("mtn_4"))
}

@Composable
fun rememberArt(): Art? {
    val ctx = LocalContext.current
    var art by remember { mutableStateOf<Art?>(null) }
    LaunchedEffect(Unit) { art = withContext(Dispatchers.IO) { loadArt(ctx) } }
    return art
}

private fun loadArt(ctx: Context): Art {
    val m = HashMap<String, ImageBitmap>(ART.size)
    ART.forEach { name ->
        ctx.assets.open("art/$name.png").use { s ->
            m[name] = BitmapFactory.decodeStream(s).asImageBitmap()
        }
    }
    return Art(m)
}

// —— 小鸟贴图的锚点（padding 后的画布坐标，见 asset-manifest）——
private const val BIRD_ANCHOR_X = 142f
private const val BIRD_ANCHOR_Y = 126f
private const val BIRD_PIVOT_X = 128f
private const val BIRD_PIVOT_Y = 104f
private const val BIRD_SRC_BODY_W = 138f     // 原始机身宽，用来换算显示比例
private const val BIRD_DISPLAY_W = 86f       // 契约 §2b

private const val TAU = 6.28318f
private const val BG_H = 820f
private const val BG_Y = -186f

/** 体温 → 冷度 0..1（1 = 极寒） */
private fun coldness(warmth: Float): Float {
    val w = ((warmth * 100f - 12f) / 62f).coerceIn(0f, 1f)
    return 1f - w * w * (3f - 2f * w)
}

/** 非对称拍打：下扑占 30% 且加速，回收占 70% 且减速。契约 §2b */
fun flapAngle(phase: Float): Float {
    val p = phase - floor(phase)
    return if (p < DOWN_FRAC) {
        val u = p / DOWN_FRAC                                   // 下扑：加速
        WING_UP - WING_TRAVEL * u * u
    } else {
        val v = (p - DOWN_FRAC) / (1f - DOWN_FRAC)              // 回收：减速
        val eased = 1f - Math.pow((1f - v).toDouble(), 2.4).toFloat()
        WING_DOWN + WING_TRAVEL * eased
    }
}

private const val WING_UP = 0.28f        // +16°，举翅
private const val WING_DOWN = -1.30f     // −76°，下扑到底
private const val WING_TRAVEL = WING_UP - WING_DOWN
private const val DOWN_FRAC = 0.30f

/** 世界 dp → 画布 px 的绘制上下文 */
class Painter(val d: DrawScope, val s: Float) {
    fun img(b: ImageBitmap, x: Float, y: Float, w: Float, h: Float,
            alpha: Float = 1f, cf: ColorFilter? = null, flipX: Boolean = false) {
        val dx = (x * s).toInt(); val dy = (y * s).toInt()
        val dw = (w * s).toInt().coerceAtLeast(1); val dh = (h * s).toInt().coerceAtLeast(1)
        if (flipX) {
            d.scale(-1f, 1f, Offset((dx + dw / 2).toFloat(), (dy + dh / 2).toFloat())) {
                drawImage(b, IntOffset.Zero, IntSize(b.width, b.height),
                    IntOffset(dx, dy), IntSize(dw, dh), alpha, colorFilter = cf)
            }
        } else {
            d.drawImage(b, IntOffset.Zero, IntSize(b.width, b.height),
                IntOffset(dx, dy), IntSize(dw, dh), alpha, colorFilter = cf)
        }
    }
    fun imgSrc(b: ImageBitmap, sy: Int, sh: Int, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f) {
        d.drawImage(b, IntOffset(0, sy), IntSize(b.width, sh),
            IntOffset((x * s).toInt(), (y * s).toInt()),
            IntSize((w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1)), alpha)
    }
    fun circle(c: Color, cx: Float, cy: Float, r: Float, alpha: Float = 1f) =
        d.drawCircle(c, r * s, Offset(cx * s, cy * s), alpha)
    fun rect(c: Color, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f) =
        d.drawRect(c, Offset(x * s, y * s), Size(w * s, h * s), alpha)
}

/**
 * 按深度分组的四个绘制入口。每个入口画到自己那张 Canvas 上，Canvas 再各自挂 z 偏移
 * （见 SpatialDepth.kt）。同一张 Canvas 内部表达不了深度，这就是要拆开的唯一原因。
 *
 * 四个函数**加起来**的图元顺序相比改动前的 drawGame 有两处变化：
 * 1. (farSnow, frost) 配对从 bird 之后挪到 obstacles 之前，远雪现在画在枝干和小鸟后面
 *    而非前面。这是 L0 分组的必然结果，也是深度模型想要的效果——"窗外的远雪"确实应该
 *    在后景。Task 3 加上 z 偏移后，这个顺序就是对的。
 * 2. frost 从最终覆盖层移到 L0 窗面层。语义上"这扇窗户结霜了"成立，并且顺带不再糊住
 *    左上/右上的 HUD 胶囊。
 * 3. nearSnow 是新增的，画在 L3 最前面。
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

private fun background(p: Painter, art: Art, scroll: Float, warmth: Float) {
    val f = (1f - warmth.coerceIn(0f, 1f)) * (art.moods.size - 1)
    val i = min(art.moods.size - 2, floor(f).toInt())
    val a = art.moods[i]; val b = art.moods[i + 1]; val mix = f - i
    tileMirror(p, a, scroll * 0.10f, BG_Y, BG_H, 1f)
    if (mix > 0.002f) tileMirror(p, b, scroll * 0.10f, BG_Y, BG_H, mix)
}

private fun tileMirror(p: Painter, b: ImageBitmap, off: Float, y: Float, h: Float, alpha: Float) {
    val w = b.width * (h / b.height)
    var j = floor(off / w).toInt()
    val jEnd = floor((off + WORLD_W) / w).toInt() + 1
    while (j <= jEnd) {
        p.img(b, j * w - off, y, w, h, alpha, flipX = Math.floorMod(j, 2) == 1)
        j++
    }
}

private fun haze(p: Painter, k: Float) {
    // 比第 2 轮加重约 40%：远山是 154x208 缩略图放大来的，镜像对称轴肉眼可见，
    // 加雾让它后退是真素材（2400x700）到位前的缓解手段，不是根治。
    p.rect(WinterPalette.HazeWarm, 0f, 0f, WORLD_W, WORLD_H, (0.42f - k * 0.30f).coerceAtLeast(0f))
    if (k > 0.5f) p.rect(WinterPalette.HazeCold, 0f, 0f, WORLD_W, WORLD_H, (k - 0.5f) * 0.40f)
}

private fun obstacles(p: Painter, art: Art, e: GameEngine, k: Float, t: Float) {
    val trunk = art["trunk_tall"]
    val hh = trunk.height * (COL_W / trunk.width)
    val cf = if (k > 0.01f)
        ColorFilter.tint(WinterPalette.Cold.copy(alpha = k * 0.42f), BlendMode.SrcAtop) else null
    for (o in e.obstacles) {
        val x = o.worldX - e.scroll
        if (x < -COL_W * 3 || x > WORLD_W + COL_W * 3) continue
        val capY = o.gapTop - ICE
        // 上障碍：贴图竖直翻转，雪端朝下当枝头
        p.d.scale(1f, -1f, Offset(((x) * p.s), (capY * p.s))) {
            drawImage(trunk, IntOffset.Zero, IntSize(trunk.width, trunk.height),
                IntOffset(((x - COL_W / 2) * p.s).toInt(), (capY * p.s).toInt()),
                IntSize((COL_W * p.s).toInt(), (hh * p.s).toInt()), 1f, colorFilter = cf)
        }
        icicles(p, x, capY, k)
        p.img(trunk, x - COL_W / 2, o.gapBottom, COL_W, hh, cf = cf)
        crown(p, x, o.gapBottom)
    }
}

/** 上障碍的判定线 = 最长那根冰柱的尖端。契约 §2c，这条边必须一眼看得见。 */
private fun icicles(p: Painter, x: Float, capY: Float, k: Float) {
    val s = p.s
    for (n in 0 until 5) {
        val ix = x - COL_W / 2 + 8f + (COL_W - 16f) / 4f * n
        val len = if (n == 2) ICE else ICE - rnd(n, x * 0.07f) * 2.6f
        val w = 3.6f
        val path = Path().apply {
            moveTo((ix - w) * s, (capY - 4f) * s)
            lineTo((ix + w) * s, (capY - 4f) * s)
            lineTo((ix + w * 0.42f) * s, (capY + len * 0.62f) * s)
            lineTo(ix * s, (capY + len) * s)
            lineTo((ix - w * 0.42f) * s, (capY + len * 0.62f) * s)
            close()
        }
        p.d.drawPath(path, WinterPalette.Icicle.copy(alpha = 0.92f))
        // 竖直高光，让它有体积不是平片
        p.rect(WinterPalette.IcicleTip, ix - 0.7f, capY - 3f, 1.4f, len * 0.6f, 0.75f)
    }
}

/** 下障碍的判定上沿 = 积雪冠最高点。同样必须一眼看得见。 */
private fun crown(p: Painter, x: Float, y: Float) {
    val s = p.s
    val hw = COL_W / 2f
    val path = Path().apply {
        moveTo((x - hw) * s, (y + 17f) * s)
        lineTo((x - hw + 7f) * s, (y + 4f) * s)
        lineTo((x - 12f) * s, (y + 2f) * s)
        lineTo(x * s, y * s)                       // 最高点在正中 = 判定线
        lineTo((x + hw - 9f) * s, (y + 3f) * s)
        lineTo((x + hw) * s, (y + 16f) * s)
        lineTo((x + hw - 3f) * s, (y + 26f) * s)
        lineTo((x - hw + 3f) * s, (y + 27f) * s)
        close()
    }
    p.rect(Color(0xFF6E88A2), x - hw, y + 20f, COL_W, 7f, 0.35f)   // 冠下投影，拉开与树干的层次
    p.d.drawPath(path, WinterPalette.Snow.copy(alpha = 0.94f))
    p.rect(WinterPalette.Snow, x - hw + 10f, y + 6f, COL_W * 0.42f, 4f, 0.55f)
}

private fun pickups(p: Painter, art: Art, e: GameEngine, t: Float) {
    for (o in e.obstacles) {
        val x = o.worldX - e.scroll
        if (x < -120f || x > WORLD_W + 120f) continue
        if (o.hasBerry && !o.berryEaten) {
            val b = art["berry_" + "abc"[Math.floorMod(o.index, 3)]]
            val w = 46f; val h = b.height * (w / b.width)
            val fromTop = o.berryY < o.gapCenter
            p.rect(WinterPalette.Stem, x - 1.1f, if (fromTop) o.gapTop else o.berryY, 2.2f,
                if (fromTop) o.berryY - o.gapTop else o.gapBottom - o.berryY, 0.75f)
            val a = 0.16f + 0.06f * sin(t * 2.6f + x * 0.01f)
            p.d.drawCircle(
                Brush.radialGradient(
                    listOf(WinterPalette.BerryGlow.copy(alpha = a), Color.Transparent),
                    Offset(x * p.s, o.berryY * p.s), 40f * p.s
                ), 40f * p.s, Offset(x * p.s, o.berryY * p.s)
            )
            p.img(b, x - w / 2, o.berryY - h * 0.52f, w, h)
        }
        if (o.hasBloom && !o.bloomPicked) {
            val b = art[if (Math.floorMod(o.index, 2) == 0) "bloom_a" else "bloom_b"]
            val w = 36f; val h = b.height * (w / b.width)
            val line = if (Math.floorMod(o.index, 2) == 0) o.gapBottom else o.gapTop
            p.rect(WinterPalette.Stem, x - 1.1f, min(line, o.bloomY), 2.2f, abs(o.bloomY - line), 0.75f)
            p.img(b, x - w / 2, o.bloomY - h / 2, w, h)
        }
    }
}

/** 无敌期间穿过枝干时，接触点炸一团雪雾——告诉玩家"穿过去了"而不是"卡住了" */
private fun snowBurst(p: Painter, x: Float, y: Float, t: Float) {
    val fl = 0.5f + 0.5f * sin(t * 9f)
    p.d.drawCircle(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.30f * fl + 0.18f), Color.Transparent),
            Offset(x * p.s, y * p.s), 54f * p.s
        ), 54f * p.s, Offset(x * p.s, y * p.s)
    )
    for (i in 0 until 20) {
        val a = rnd(i, 3.1f) * TAU
        val r = 8f + ((t * 54f + rnd(i, 7.3f) * 92f) % 92f)
        val al = (1f - r / 98f).coerceAtLeast(0f)
        p.circle(WinterPalette.Snow, x + kotlin.math.cos(a) * r * 1.7f,
            y + kotlin.math.sin(a) * r * 0.9f, 1.8f + rnd(i, 9.9f) * 3.2f, al * 0.9f)
    }
}

private fun birdBody(p: Painter, art: Art, x: Float, y: Float, rot: Float, wingA: Float, alpha: Float) {
    val body = art["bird_body"]; val wing = art["bird_wing"]
    val kk = BIRD_DISPLAY_W / BIRD_SRC_BODY_W
    val w = body.width * kk; val h = body.height * kk
    val ox = -BIRD_ANCHOR_X * kk; val oy = -BIRD_ANCHOR_Y * kk
    val pvx = ox + BIRD_PIVOT_X * kk; val pvy = oy + BIRD_PIVOT_Y * kk
    p.d.translate(x * p.s, y * p.s) {
        rotate(Math.toDegrees(rot.toDouble()).toFloat(), Offset.Zero) {
            drawImage(body, IntOffset.Zero, IntSize(body.width, body.height),
                IntOffset((ox * p.s).toInt(), (oy * p.s).toInt()),
                IntSize((w * p.s).toInt(), (h * p.s).toInt()), alpha)
            rotate(Math.toDegrees(wingA.toDouble()).toFloat(), Offset(pvx * p.s, pvy * p.s)) {
                drawImage(wing, IntOffset.Zero, IntSize(wing.width, wing.height),
                    IntOffset((ox * p.s).toInt(), (oy * p.s).toInt()),
                    IntSize((w * p.s).toInt(), (h * p.s).toInt()), alpha)
            }
        }
    }
}

private fun bird(p: Painter, art: Art, e: GameEngine, t: Float) {
    if (e.hurt > 0f && (t * 8f).toInt() % 2 == 0) return      // 撞击硬直：8Hz 闪烁
    val body = art["bird_body"]; val wing = art["bird_wing"]
    val kk = BIRD_DISPLAY_W / BIRD_SRC_BODY_W
    val w = body.width * kk; val h = body.height * kk
    val ox = -BIRD_ANCHOR_X * kk; val oy = -BIRD_ANCHOR_Y * kk
    val pvx = ox + BIRD_PIVOT_X * kk; val pvy = oy + BIRD_PIVOT_Y * kk
    val rot = (e.birdVy * 0.0009f).coerceIn(-0.38f, 0.62f)
    val rise = (-e.birdVy).coerceAtLeast(0f)
    val glide = 0.02f
    val beat = flapAngle(t * 1.75f + rise * 0.0016f)
    val mix = (rise * 0.0045f).coerceIn(0.30f, 1f)
    val wingA = glide + (beat - glide) * mix
    val bx = tech.illusion.overwinter.game.BIRD_X

    if (e.invincible > 0f) {
        // 穿过枝干的雪雾（在小鸟之下画，让小鸟压在雾上面）
        if (e.passingThrough) snowBurst(p, bx + 16f, e.birdY + 4f, t)
        // 余晖：身后三重递减残影
        for (i in 3 downTo 1) {
            birdBody(p, art, bx - i * 34f, e.birdY + sin(t * 2.1f - i * 0.55f) * 6f,
                rot * 0.7f, wingA + i * 0.20f, 0.34f * (4 - i) / 4f)
        }
        // 暖白光晕，加色混合
        val pl = 0.84f + 0.16f * sin(t * 7f)
        p.d.drawCircle(
            Brush.radialGradient(
                0f to WinterPalette.InvinGlow.copy(alpha = 0.92f),
                0.32f to Color(0xFFFFE4A8).copy(alpha = 0.40f),
                1f to Color.Transparent,
                center = Offset(bx * p.s, e.birdY * p.s), radius = 86f * pl * p.s
            ), 86f * pl * p.s, Offset(bx * p.s, e.birdY * p.s), blendMode = BlendMode.Plus
        )
    }

    birdBody(p, art, bx, e.birdY, rot, wingA, 1f)
}

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

private fun frost(p: Painter, k: Float) {
    if (k <= 0.42f) return
    val q = (k - 0.42f) / 0.58f
    val corners = listOf(0f to 0f, WORLD_W to 0f, 0f to WORLD_H, WORLD_W to WORLD_H)
    for ((cx, cy) in corners) {
        p.d.drawCircle(
            Brush.radialGradient(
                0f to WinterPalette.Frost.copy(alpha = 0.42f * q),
                0.55f to WinterPalette.Frost.copy(alpha = 0.11f * q),
                1f to Color.Transparent,
                center = Offset(cx * p.s, cy * p.s), radius = 330f * p.s
            ), 330f * p.s, Offset(cx * p.s, cy * p.s)
        )
    }
}

private fun grain(p: Painter, art: Art) {
    val g = art["grain"]
    val tile = 128f
    var x = 0f
    while (x < WORLD_W) {
        var y = 0f
        while (y < WORLD_H) {
            p.d.drawImage(g, IntOffset.Zero, IntSize(g.width, g.height),
                IntOffset((x * p.s).toInt(), (y * p.s).toInt()),
                IntSize((tile * p.s).toInt(), (tile * p.s).toInt()),
                0.42f, blendMode = BlendMode.Overlay)
            y += tile
        }
        x += tile
    }
}


@Suppress("unused")
private val groundRef = GROUND_Y
