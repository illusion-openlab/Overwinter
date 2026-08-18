package tech.illusion.overwinter.content

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 音效。短音走 SoundPool（低延迟、可并发），循环环境音走 MediaPlayer。
 *
 * 素材来自用户提供的 `~/Downloads/越冬/`：
 *   flap.wav   ← 飞翔.mp3 的第 2 次振翅（0.855s~1.215s）+ 两端淡入淡出。
 *               原文件是**三次连续振翅**共 2.4s，每次点击播完整段会听到三下，
 *               连点还会糊成一片，所以只取单次。
 *   pickup.mp3 ← 欢喜.mp3   吃到浆果或花朵，两者共用一个音
 *   fall.mp3   ← 坠落.mp3   进入结算态
 *   wind.mp3   ← 风声.mp3   19s 循环环境音
 */
class GameAudio private constructor(ctx: Context) {

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val pool = SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build()
    private val flapId = load(ctx, "audio/flap.wav")
    private val pickupId = load(ctx, "audio/pickup.mp3")
    private val fallId = load(ctx, "audio/fall.mp3")

    private var flapStream = 0
    private var wind: MediaPlayer? = null
    private var released = false

    private fun load(ctx: Context, path: String): Int =
        ctx.assets.openFd(path).use { pool.load(it, 1) }

    /** 扇翅。连点时掐掉上一声再起新的，否则 0.36s 的尾巴会互相叠。 */
    fun flap() {
        if (released) return
        if (flapStream != 0) pool.stop(flapStream)
        flapStream = pool.play(flapId, 0.9f, 0.9f, 1, 0, 1f)
    }

    fun pickup() { if (!released) pool.play(pickupId, 1f, 1f, 1, 0, 1f) }

    fun gameOver() { if (!released) pool.play(fallId, 1f, 1f, 1, 0, 1f) }

    fun startAmbience(ctx: Context) {
        if (released || wind != null) return
        wind = MediaPlayer().apply {
            setAudioAttributes(attrs)
            ctx.assets.openFd("audio/wind.mp3").use {
                setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            isLooping = true                 // mp3 循环点会有极短的编码器留白，19s 一次，环境音可接受
            setVolume(AMBIENCE_VOL, AMBIENCE_VOL)
            prepare()
            start()
        }
    }

    fun pauseAmbience() { wind?.takeIf { it.isPlaying }?.pause() }
    fun resumeAmbience() { wind?.takeIf { !it.isPlaying }?.start() }

    fun release() {
        if (released) return
        released = true
        wind?.run { if (isPlaying) stop(); release() }
        wind = null
        pool.release()
    }

    companion object {
        private const val AMBIENCE_VOL = 0.35f
        fun create(ctx: Context) = GameAudio(ctx.applicationContext)
    }
}

/**
 * 生命周期由 DisposableEffect 兜底：面板关闭 / 组合销毁时释放 SoundPool 与 MediaPlayer。
 * 这是 CLAUDE.md 「启动了就要有对应清理路径」那条的落点。
 */
@Composable
fun rememberGameAudio(): GameAudio {
    val ctx = LocalContext.current
    val audio = remember { GameAudio.create(ctx) }
    DisposableEffect(audio) {
        audio.startAmbience(ctx)
        onDispose { audio.release() }
    }
    return audio
}
