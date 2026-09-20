package tech.illusion.overwinter.content

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 各绘制层与 UI 节点的 z 深度。正值朝向玩家。
 *
 * **真机调参只改这个文件。** 深度效果在单目截图里基本不可见，模拟器门禁判不出"浮起来了没有"，
 * 唯一可靠的验收是用户戴头显看，然后说"再往前/往后"，改这里的数字重出包。
 *
 * 取值依据（本项目没有实测数据，全部来自兄弟项目）：
 *  · SpaceMerge 在同类**平面**窗口里 `offset(z = 24.dp)` 拿到可见的浮起效果
 *  · SpaceVinyl 在**体积**窗口里实测 200dp 安全、280dp 被前边界裁掉
 * 平面窗口的深度上限没人量过，可能远小于体积窗口，所以最前一档压在 40dp，
 * 只比已知有效值高一档。**失败症状是某一层或卡片整个消失**，不是渐变模糊。
 *
 * 必须配 `import com.pico.spatial.ui.foundation.layout.offset`。
 * AndroidX 的同名 offset 只有 x/y 重载，写 z 会报 "no parameter named z"。
 */

/** L1 玩法层：枝干与浆果。 */
val Z_PLAY: Dp = 14.dp

/** L2 小鸟层。只比 Z_PLAY 前 6dp——碰撞在 2D 平面算，浮太前撞枝会看起来像撞了空气。 */
val Z_BIRD: Dp = 20.dp

/** L3 近景层：近雪、颗粒、结算压暗。最靠近玩家。 */
val Z_NEAR: Dp = 36.dp

/** HUD 胶囊。与小鸟同档，好让近雪从它前面飘过。 */
val Z_HUD: Dp = 20.dp

/** 开始卡 / 结算卡。压在最前——模态 UI 的可读性优先于"雪飘在前面"，不能让雪片糊住按钮。 */
val Z_CARD: Dp = 40.dp
