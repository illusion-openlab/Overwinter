package tech.illusion.overwinter.content

import androidx.compose.ui.graphics.Color
import com.pico.spatial.ui.design.ColorScheme
import com.pico.spatial.ui.design.defaultColorScheme

/**
 * 固定配色，不用系统自适应方案。
 *
 * 第 2 轮上机验证发现：直接用 `PicoTheme` 默认方案，读起来是"没有主题色的通用浅色 UI"——
 * 主按钮和结算分数都是中性深灰，违反契约 §2 里「实心暖色填充，全卡唯一高饱和块面」
 * 和「卡内唯一的暖色文本」。同工作区 SpaceLianliankan 记录过同一个坑。
 *
 * 暖色取自 `ref-spritesheet.png` 色卡的小鸟金黄 #E3B44A —— 让按钮和小鸟同色系，
 * 是"画面里最暖的东西"这条设计意图在 UI 上的延续。
 */
val OverwinterColorScheme: ColorScheme = defaultColorScheme(
    // 主按钮 / 结算大分数。全卡唯一的高饱和暖色块面。
    fillPrimary = Color(0xFFE3B44A),
    fillSecondary = Color(0xFF17222E),
    // 次按钮：半透明白，浮在深色玻璃上，明显弱于主按钮
    fillLight = Color(0x2EFFFFFF),
    fillTertiary = Color(0x1AFFFFFF),
    // 深色玻璃上的文字层级
    labelPrimary = Color(0xFFF2F7FA),
    labelPrimaryLight = Color(0xFF2A1E08),   // 压在暖色填充上的深字
    labelSecondary = Color(0xC2D8E6F0),
    labelTertiary = Color(0x8CBFD2DE),
    labelQuaternary = Color(0x59A9BECB),
    dividerLine = Color(0x33FFFFFF),
)

/** 磨砂玻璃之上再压一层暗色，才能得到视觉稿里的深色玻璃。 */
val GlassScrim = Color(0xB00D1520)
