package tech.illusion.overwinter.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.HorizontalDivider
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material

/**
 * 模态覆盖层。两件必做的事：
 *  1. 自己消费指针事件——否则射线落在卡片之外的区域会穿透到下层的游戏画布，
 *     触发扇翅，同时还会让画布下的东西响应。挂在遮罩层而不是卡片层，
 *     用默认 Main pass，卡片自己的按钮先拿到事件，照常可点。
 *  2. PicoTheme 的颜色角色是不透明的，要半透明必须显式 copy(alpha)。
 */
@Composable
private fun Scrim(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) { awaitPointerEvent().changes.forEach { it.consume() } }
                }
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Card(width: Int, padTop: Int, padBottom: Int, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .width(width.dp)
            .clip(RoundedCornerShape(26.dp))
            .backgroundMaterial(true, Material.Regular)
            .background(GlassScrim)
            .padding(start = 44.dp, end = 44.dp, top = padTop.dp, bottom = padBottom.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
fun StartCard(best: Int, onStart: () -> Unit) = Scrim {
    Card(width = 452, padTop = 38, padBottom = 30) {
        Text(
            text = "越 冬",
            color = PicoTheme.colorScheme.labelPrimary,
            style = PicoTheme.typography.headlineLarge,
        )
        Text(
            text = "O V E R W I N T E R",
            modifier = Modifier.padding(top = 7.dp),
            color = PicoTheme.colorScheme.labelSecondary,
            style = PicoTheme.typography.labelSmall,
        )
        Text(
            text = "体温在流失\n吃到浆果，才撑得过这片林子",
            modifier = Modifier.padding(top = 16.dp),
            color = PicoTheme.colorScheme.labelSecondary,
            style = PicoTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Row(
            modifier = Modifier.padding(top = 20.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "历史最高 ",
                color = PicoTheme.colorScheme.labelSecondary,
                style = PicoTheme.typography.bodyMedium,
            )
            Text(
                text = "$best",
                color = PicoTheme.colorScheme.labelPrimary,
                style = PicoTheme.typography.titleMedium,
            )
            Text(
                text = " 分",
                color = PicoTheme.colorScheme.labelSecondary,
                style = PicoTheme.typography.bodyMedium,
            )
        }
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PicoTheme.colorScheme.fillPrimary,
                contentColor = PicoTheme.colorScheme.labelPrimaryLight,
            ),
        ) {
            Text(text = "开始飞行", style = PicoTheme.typography.labelLarge)
        }
        Text(
            text = "点击窗口任意处 · 扇动翅膀",
            modifier = Modifier.padding(top = 14.dp),
            color = PicoTheme.colorScheme.labelTertiary,
            style = PicoTheme.typography.labelSmall,
        )
    }
}

@Composable
fun ResultCard(
    score: Int, best: Int, branches: Int, berries: Int, blooms: Int, seconds: Float,
    isRecord: Boolean, onRetry: () -> Unit, onHome: () -> Unit,
) = Scrim {
    Card(width = 470, padTop = 32, padBottom = 28) {
        Text(
            text = if (isRecord) "新纪录！" else "冻僵了",
            color = PicoTheme.colorScheme.labelPrimary,
            style = PicoTheme.typography.headlineSmall,
        )
        Text(
            text = "$score",
            modifier = Modifier.padding(top = 10.dp),
            color = PicoTheme.colorScheme.fillPrimary,
            // 契约要求「全卡最大的数字」：headlineLarge 和标题的 headlineSmall 差得不够明显，
            // 显式放大，让它在截图里一眼就是最大的那个
            style = PicoTheme.typography.headlineLarge.copy(fontSize = 62.sp, lineHeight = 66.sp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Stat("穿过枝干", "$branches")
            Stat("吃到浆果", "$berries")
            Stat("采到花朵", "$blooms")
            Stat("坚持", String.format("%.1fs", seconds))
        }
        HorizontalDivider(modifier = Modifier.padding(top = 22.dp))
        Text(
            text = "历史最高 $best 分",
            modifier = Modifier.padding(top = 14.dp),
            color = PicoTheme.colorScheme.labelSecondary,
            style = PicoTheme.typography.labelSmall,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PicoTheme.colorScheme.fillPrimary,
                contentColor = PicoTheme.colorScheme.labelPrimaryLight,
            ),
        ) {
            Text(text = "再飞一次", style = PicoTheme.typography.labelLarge)
        }
        Button(
            onClick = onHome,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PicoTheme.colorScheme.fillLight),
        ) {
            Text(text = "回到开始页", style = PicoTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = PicoTheme.colorScheme.labelSecondary,
            style = PicoTheme.typography.labelSmall,
        )
        Text(
            text = value,
            modifier = Modifier.padding(top = 4.dp),
            color = PicoTheme.colorScheme.labelPrimary,
            style = PicoTheme.typography.titleMedium,
        )
    }
}
