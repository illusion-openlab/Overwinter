package tech.illusion.overwinter.content

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.pico.spatial.ui.foundation.haptic.controllerHapticFeedback
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.pico.spatial.ui.graphics.SpatialHoverStyle
import tech.illusion.overwinter.game.debugForce
import tech.illusion.overwinter.game.GameEngine
import tech.illusion.overwinter.game.Phase
import kotlin.math.roundToInt

@Composable
fun HomePage() {
    val engine = remember { GameEngine() }
    val art = rememberArt()
    val audio = rememberGameAudio()

    // 截图验证入口（契约 §7）。只认 intent extra，正常启动不受影响。
    //   cold / over —— 首次组合前直接把局面设好并冻结（拍静态构图用）
    //   live        —— 停在开始页，2.5s 后自动开局并自动扇翅。
    //                  这一档专门用来验「phase 变化能不能触发重组」：
    //                  cold/over 是首次组合前就设好状态的，恰好绕开了这条链路。
    val ctx = LocalContext.current
    val dbg = remember { (ctx as? android.app.Activity)?.intent?.getStringExtra("ow_debug") }
    val autoPlay = dbg == "live"
    remember(dbg) {
        if (dbg != null && !autoPlay) engine.debugForce(dbg)
        true
    }

    // 逐帧驱动。这是纯 2D 平面窗口应用，用 Compose 帧回调是对的；
    // 契约里的 ECS 规则针对 3D 场景内容，本作不涉及。
    var clock by remember { mutableFloatStateOf(0f) }
    var tick by remember { mutableIntStateOf(0) }

    // GameEngine 是纯逻辑类，字段是普通 var，不是 snapshot state。
    // 把 UI 真正依赖的那几项镜像到组合层——否则 `when (phase)` 只在首次组合求值一次，
    // 点了开始按钮画面也不会切（第 4 轮验证前发现的 Blocker）。
    // 相同值写入 snapshot state 不会触发失效，所以这里无条件赋值即可，不会每帧重组。
    var phase by remember { mutableStateOf(engine.phase) }
    var tempShown by remember { mutableIntStateOf(engine.temp.roundToInt()) }
    var scoreShown by remember { mutableIntStateOf(engine.score) }
    var bestShown by remember { mutableIntStateOf(engine.best) }
    var invinShown by remember { mutableFloatStateOf(engine.invincible) }

    // 主循环用 delay 驱动，不用 withFrameNanos。
    // 实测：在 DefaultWindowContainer 里 withFrameNanos 不会恢复，协程停在第一次挂起，
    // 整个游戏循环一帧都不跑（日志里 autoPlay=true 但循环体从没执行过）。
    // 音效边沿检测：引擎只给纯计数器，表现层比对上一帧的值决定放不放。
    // 这样引擎依旧不认识 Android，JUnit 照样能测。
    LaunchedEffect(Unit) {
        var last = System.nanoTime()
        var lastFlap = engine.flapEvents
        var lastPickup = engine.pickupEvents
        var lastHit = engine.hitEvents
        while (isActive) {
            delay(11L)                                   // ~90Hz 上限，实际由调度决定
            val now = System.nanoTime()
            val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.05f)
            last = now
            clock += dt
            if (autoPlay) {
                if (clock > 2.5f && engine.phase == Phase.NotStarted) engine.start()
                if (engine.phase == Phase.Playing && engine.birdVy > 150f) engine.flap()
            }
            engine.update(dt)
            tick++                       // 只订阅绘制阶段，用于刷新 Canvas

            if (engine.flapEvents != lastFlap) { lastFlap = engine.flapEvents; audio.flap() }
            if (engine.pickupEvents != lastPickup) { lastPickup = engine.pickupEvents; audio.pickup() }
            if (engine.hitEvents != lastHit) { lastHit = engine.hitEvents; audio.hit() }
            if (phase != Phase.GameOver && engine.phase == Phase.GameOver) audio.gameOver()

            phase = engine.phase
            tempShown = engine.temp.roundToInt()
            scoreShown = engine.score
            bestShown = engine.best
            // 量化到 0.1s，避免每帧都重组 HUD
            invinShown = kotlin.math.ceil(engine.invincible * 10f) / 10f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .spatialHoverEffect(SpatialHoverStyle.Highlight)
            .controllerHapticFeedback()
            .pointerInput(Unit) { detectTapGestures { engine.flap() } }
    ) {
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

        when (phase) {
            Phase.NotStarted -> StartCard(best = bestShown, onStart = { engine.start() })
            Phase.Playing -> Hud(
                temp = tempShown.toFloat(),
                score = scoreShown,
                best = bestShown,
                invincible = invinShown,
                modifier = Modifier.fillMaxSize(),
            )
            Phase.GameOver -> ResultCard(
                score = engine.score,
                best = engine.best,
                branches = engine.branchesPassed,
                berries = engine.berries,
                blooms = engine.blooms,
                seconds = engine.elapsed,
                isRecord = engine.lastRunWasRecord,
                onRetry = { engine.start() },
                onHome = { engine.reset() },
            )
        }
    }
}
