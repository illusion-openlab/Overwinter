package tech.illusion.overwinter.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.pico.spatial.ui.design.windows.BasicSheet
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
fun StartCard(best: Int, onStart: () -> Unit) {
    // 门禁 A 增量：玩法说明弹层。状态放在 StartCard 自己身上而不是 HomePage——
    // 一旦 phase 离开 NotStarted，`when(phase)` 会把整个 StartCard 移出组合，
    // 这份 remember 状态跟着销毁，天然满足「切阶段前强制关闭弹层」，不用手动清。
    // 同时 howto_button 只在 NotStarted 才可见，弹层开着时它又是 BasicSheet
    // 起的独立模态窗口，挡住了「开始飞行」，所以也不存在弹层开着还能进 Playing 的路径。
    var showHowTo by remember { mutableStateOf(false) }
    Scrim {
        Card(width = 452, padTop = 38, padBottom = 30) {
            // howto_button 是 Card 自己 Column 的第一个子项（不是外层 Box 里叠加的兄弟节点）——
            // 早先试过把它挪到 Card 外面单独一行，虽然绕开了 backgroundMaterial 的合成器遮挡
            // （同一个 Box 里后画的兄弟节点会被它盖住，见 memory
            // backgroundmaterial-is-compositor-layer），但这个 WindowContainer 本身是全透明的
            // Scrim，Card 之外没有任何背景托底，按钮飘在卡片外面就会直接露出雪林背景，
            // 看着像"跑到窗口外面了"。改成 Card 内部第一个子项后，跟标题这些既有元素同属一个
            // 已经带 backgroundMaterial 的 Column，不会被自己的玻璃层挡住，也不会跑出窗口。
            Box(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { showHowTo = true },
                    modifier = Modifier.align(Alignment.TopEnd),
                    size = ButtonDefaults.Min,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PicoTheme.colorScheme.fillPrimary.copy(alpha = 0.3f),
                        contentColor = PicoTheme.colorScheme.fillPrimary,
                    ),
                ) {
                    HowToBadge()
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "玩法", style = PicoTheme.typography.labelSmall)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp),
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
    if (showHowTo) {
        HowToSheet(onDismiss = { showHowTo = false })
    }
}

/**
 * howto_button 前置的小圆形徽标，里面是"?"字形。
 * 项目里目前没有 Icon() 组件调用先例，唯一的图标（HUD 温度计）是 Canvas 手绘的，
 * 不引入未验证过的 Icon API，直接退化成文字字形——和契约里 §2b 的手法一致。
 */
@Composable
private fun HowToBadge() {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(PicoTheme.colorScheme.fillPrimary.copy(alpha = 0.68f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "?",
            color = PicoTheme.colorScheme.labelPrimaryLight,
            style = PicoTheme.typography.labelSmall,
        )
    }
}

/**
 * 玩法说明弹层。直接复用 `ResultCard` 已经在用的 `BasicSheet`——
 * 反编译确认它底层走 `SpatialDialogDelegate`，是独立的模态弹窗：外部点击自动
 * 触发 onDismissRequest、窗口内部的点击不会下穿到外面的 start_card，天然满足
 * "点 scrim 空白处关闭 / 点卡片内部空白处不误触发关闭" 这两条，不用再手写一遍
 * Scrim + pointerInput 消费。
 */
@Composable
private fun HowToSheet(onDismiss: () -> Unit) = BasicSheet(onDismissRequest = onDismiss) {
    Card(width = 480, padTop = 28, padBottom = 26) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "越冬 · 玩法说明",
                color = PicoTheme.colorScheme.labelPrimary,
                style = PicoTheme.typography.headlineSmall,
            )
            Button(
                onClick = onDismiss,
                size = ButtonDefaults.Min,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PicoTheme.colorScheme.fillLight,
                    contentColor = PicoTheme.colorScheme.labelSecondary,
                ),
            ) {
                Text(text = "×", style = PicoTheme.typography.labelLarge)
            }
        }
        // Column + verticalScroll，保证 8 条规则不被裁切。480dp 卡宽下，
        // 340dp 的可视高度上限是估算值——1040x580dp 的世界里给标题行和卡片自身
        // 内边距留出余量，实测如仍溢出需要现场调整这个数字。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
                .heightIn(max = 340.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "简介",
                color = PicoTheme.colorScheme.fillPrimary,
                style = PicoTheme.typography.labelLarge,
            )
            Text(
                text = HOW_TO_INTRO,
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
                color = PicoTheme.colorScheme.labelPrimary,
                style = PicoTheme.typography.bodyMedium,
            )
            Text(
                text = "操作",
                color = PicoTheme.colorScheme.fillPrimary,
                style = PicoTheme.typography.labelLarge,
            )
            Text(
                text = HOW_TO_CONTROLS,
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
                color = PicoTheme.colorScheme.labelPrimary,
                style = PicoTheme.typography.bodyMedium,
            )
            Text(
                text = "规则",
                color = PicoTheme.colorScheme.fillPrimary,
                style = PicoTheme.typography.labelLarge,
            )
            HOW_TO_RULES.forEachIndexed { index, rule ->
                Text(
                    text = rule,
                    modifier = Modifier.padding(top = if (index == 0) 6.dp else 8.dp),
                    color = PicoTheme.colorScheme.labelPrimary,
                    style = PicoTheme.typography.bodyMedium,
                )
            }
        }
    }
}

// 玩法说明文案：门禁 A 最终稿，逐字照抄，不做任何改写/精简/转述。
private const val HOW_TO_INTRO =
    "操控一只小鸟穿越结霜的枯枝林，靠点击不断振翅爬升，躲开树枝、吃浆果续命、采花朵加分，尽量在体温耗尽前飞得更远，刷新自己的历史最高分。"

private const val HOW_TO_CONTROLS =
    "- 点击：点屏幕任意位置，小鸟就振一下翅膀往上冲；松手不点，它会持续往下坠。"

private val HOW_TO_RULES = listOf(
    "1. 你操控一只小鸟，在结霜的枯枝林里不停往前飞：点一下屏幕，它就振一下翅膀往上冲，点几下就飞几下，没有连发也没有蓄力。",
    "2. 体温是你的生命线，从满格 100 开始就会随时间不断流逝——哪怕一根树枝都没撞到，它也会自己往下掉，迟早得靠拾取物续命。",
    "3. 撞到树枝会让体温骤降一大截，比自然流逝快得多；小鸟会闪一下、下坠的势头被清空，但手感照旧，你可以立刻继续点击往上飞，不会被卡住输入。",
    "4. 树枝丛里藏着两种拾取物：浆果能大幅回体温（顶多回满，不会超过 100），花朵能换来几秒无敌外加直接加分，但花朵本身不回体温——别把它当保命符。",
    "5. 无敌时撞树枝会直接穿过去、完全不掉血，但体温该掉还是照掉；无敌快结束时如果小鸟正好贴着地面，一结束就会立刻按落地处理。",
    "6. 飞太高会被轻轻托住，不扣分也不会出事；飞太低、机身碰到地面才是真的危险——只要那一刻没有无敌状态，游戏立刻结束。",
    "7. 每完全飞过一组树枝记 1 分，空手飞过也算；每采到一朵花额外 +5 分；浆果只回体温、不计分。",
    "8. 越往后飞，前进速度会越来越快（到一定距离后封顶，不会无限加速），但树枝间的空隙大小和间距始终不变——真正变难的是留给你反应的时间，不是关卡本身在变。",
)

@Composable
fun ResultCard(
    score: Int, best: Int, branches: Int, berries: Int, blooms: Int, seconds: Float,
    isRecord: Boolean, onRetry: () -> Unit, onHome: () -> Unit,
) = BasicSheet(onDismissRequest = onHome) {
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PicoTheme.colorScheme.fillPrimary,
                contentColor = PicoTheme.colorScheme.labelPrimaryLight,
            ),
        ) {
            Text(text = "再飞一次", style = PicoTheme.typography.labelLarge)
        }
        Button(
            onClick = onHome,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
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
