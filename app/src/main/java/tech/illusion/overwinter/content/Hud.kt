package tech.illusion.overwinter.content

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material
import kotlin.math.roundToInt

private val TEMP_WARM = Color(0xFFF5BE4B)
private val TEMP_COLD = Color(0xFF63A9DE)

/** 契约 §2：体温胶囊 + 得分胶囊，压在画面最上一条 */
@Composable
fun Hud(temp: Float, score: Int, best: Int, invincible: Float, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 26.dp, end = 26.dp, top = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TempChip(temp)
            // 只在无敌期间出现，其余时刻不占位（隐藏，不是灰态）
            if (invincible > 0f) {
                Box(Modifier.padding(start = 11.dp)) { InvinChip(invincible) }
            }
        }
        ScoreChip(score, best)
    }
}

/** 吃到花朵后的 3 秒无敌：倒计时环 + 剩余秒数 */
@Composable
private fun InvinChip(remain: Float) {
    val ring = PicoTheme.colorScheme.labelPrimary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .backgroundMaterial(true, Material.Thin)
            .background(GlassScrim)
            .padding(start = 9.dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(30.dp)) {
            val w = size.width
            val sw = w * 0.11f
            drawCircle(ring.copy(alpha = 0.20f), w / 2 - sw / 2, style = Stroke(sw))
            drawArc(
                color = ring,
                startAngle = -90f,
                sweepAngle = 360f * (remain / 3f).coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(sw / 2, sw / 2),
                size = androidx.compose.ui.geometry.Size(w - sw, w - sw),
            )
            drawCircle(ring.copy(alpha = 0.9f), w * 0.15f)
        }
        Text(
            text = String.format("%.1fs", remain),
            modifier = Modifier.padding(start = 9.dp),
            color = PicoTheme.colorScheme.labelPrimary,
            style = PicoTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TempChip(temp: Float) {
    val ratio = (temp / 100f).coerceIn(0f, 1f)
    val fill = lerp(TEMP_COLD, TEMP_WARM, ratio)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .backgroundMaterial(true, Material.Thin)
            .background(GlassScrim)
            .padding(start = 14.dp, end = 18.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thermometer(PicoTheme.colorScheme.labelPrimary)
        Box(
            modifier = Modifier
                .padding(start = 12.dp, end = 12.dp)
                .width(184.dp).height(9.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(PicoTheme.colorScheme.labelPrimary.copy(alpha = 0.19f)),
        ) {
            Box(
                Modifier.fillMaxWidth(ratio).height(9.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(fill)
            )
        }
        // 契约 §4：{n}°，等宽定宽，避免数字跳动时胶囊抖
        Text(
            text = "${temp.roundToInt()}°",
            modifier = Modifier.width(52.dp),
            color = PicoTheme.colorScheme.labelPrimary,
            style = PicoTheme.typography.titleMedium,
        )
    }
}

/** 19x19dp 线性温度计。用 Canvas 画，省掉一个图标素材。 */
@Composable
private fun Thermometer(tint: Color) {
    Canvas(Modifier.size(19.dp)) {
        val w = size.width
        val stroke = w * 0.10f
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.36f, w * 0.08f),
            size = Size(w * 0.28f, w * 0.56f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.14f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
        )
        drawCircle(tint, w * 0.21f, Offset(w * 0.5f, w * 0.74f), style =
            androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        drawLine(tint, Offset(w * 0.5f, w * 0.30f), Offset(w * 0.5f, w * 0.60f), stroke)
    }
}

@Composable
private fun ScoreChip(score: Int, best: Int) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .backgroundMaterial(true, Material.Thin)
            .background(GlassScrim)
            .padding(horizontal = 20.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = "$score",
            color = PicoTheme.colorScheme.labelPrimary,
            style = PicoTheme.typography.headlineLarge,
        )
        Text(
            text = "最高 $best",
            color = PicoTheme.colorScheme.labelSecondary,
            style = PicoTheme.typography.labelSmall,
        )
    }
}
