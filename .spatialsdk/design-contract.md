# 越冬 · 设计契约 v4

## 0. 元信息

- 应用名 / applicationId：越冬（Overwinter）/ `tech.illusion.overwinter`
- 设计源类型：用户图片（水彩素材图集）+ Claude 生成视觉稿
- 设计源位置：
  - `design-ref/ref-spritesheet.png` — 水彩素材图集（造型与配色的唯一来源）
  - `design-ref/ref-artboard.png` — 主视觉 / 氛围参考
  - `design-ref/design-mockup.html` — 五帧合成视觉稿，已发布 Artifact
  - `design-ref/asset-manifest.md` — 素材规格清单（27 项）
- 契约版本：v3（v2 → v3 变更：花朵无敌从 V2 提前实现，见 §5c）
- 用户确认时间：2026-08-18（用户在对话中回复「好的」，此前已逐条确认：花朵无敌移 V2、契约不写 PicoTheme 角色名、小鸟飞行动作后续再改）

### 未决项（用户已明确延后，不阻塞本契约）

| 项 | 状态 | 说明 |
|---|---|---|
| 小鸟飞行动作 | **后续再改** | 当前采用「机身 + 单翅绕肩点旋转」，行程 +16°→−76°。图集那 6 帧翼尖行程仅 19°、无下扑帧、机身极差 25%，不可用。若之后重出带下扑帧的序列，本契约元素 `bird` 的动画描述随之走增量，其余不变 |
| 前景雪地层 L5 | 缺素材 | `snowfield.png` 未产出，暂由背景图自带雪地代替 |
| 锯断截面帽 / 积雪冠 | 缺素材 | `cap_top_*` / `crown_bot_*` 未产出，暂用树干贴图自带的雪端 |

---

## 1. 面板清单

| 面板 id | 容器类型 | 面板尺寸 | 层级/父面板 | 出现时机 |
|---|---|---|---|---|
| `main` | WindowContainer | 1040×580dp | 根 | 启动即显示 |
| `game_canvas` | Canvas（非容器） | fill×fill | main · 最底层 | 全程 |
| `hud` | Row（非容器） | fill×64dp，顶部 | main · 覆盖层 | Playing |
| `start_card` | Surface（非容器） | 452dp×wrap，居中 | main · 覆盖层 | NotStarted |
| `result_card` | Surface（非容器） | 470dp×wrap，居中 | main · 覆盖层 | GameOver |

**容器类型是架构决策。** 全应用只有 `main` 一个 `WindowContainer`，其余是它内部 `Box` 里的 Compose 子项，不是独立容器。不得改成 Stage —— 本作是纯 2D 平面窗口应用，不需要 Full Space，也不使用手部追踪。

`AndroidManifest.xml` 关键项：`windowcontainer.defaultsize=1040x580`、`defaultsize.unit=dp`、`resizetype=1`。

---

## 2. 元素表 · UI 层（SpatialUI）

UI 一律用 SpatialUI 组件并包在 `PicoTheme` 里，禁止 Material / Material3 —— 这是项目硬规则，不在契约里重复约束。

**本表不写 PicoTheme 角色名。** 理由：角色名截图核对不了（验证清单明确规定颜色只判相对关系——色相、明暗层级、前后景对比——不判绝对色值），写进来只会制造一堆「不可判定」行。具体用哪个角色在实现时按 SDK 实际提供的选，判据是下面这列「视觉描述」能不能在截图里成立。

| id | 类型 | 父容器 | 锚点 | 偏移 | 尺寸 | 内边距 | 外边距 | 视觉描述（截图判据） | 圆角/形状 |
|---|---|---|---|---|---|---|---|---|---|
| `temp_chip` | Surface | hud | 左上 | 26,22 | wrap×wrap | 9/18/9/14 | - | 磨砂玻璃，明显亮于其后画面，能看出背景被模糊 | 999dp 胶囊 |
| `temp_icon` | Icon | temp_chip | 左中 | 0,0 | 19×19dp | - | 右 12 | 主文本色，线性温度计图标 | - |
| `temp_track` | Canvas | temp_chip | 左中 | 0,0 | 184×9dp | - | 右 12 | 槽底半透明白；**填充是整个 HUD 里唯一的有色块面**，色相随体温从暖橙移到冷蓝 | 999dp |
| `temp_value` | Text | temp_chip | 右中 | 0,0 | 52dp×wrap | - | - | 主文本色，等宽数字，高度约等于 `temp_icon` | - |
| `score_chip` | Surface | hud | 右上 | 26,22 | wrap×wrap | 7/20/7/20 | - | 与 `temp_chip` 同一种磨砂玻璃 | 20dp |
| `score_value` | Text | score_chip | 上中 | 0,0 | wrap×wrap | - | - | 主文本色，**HUD 里最大号的数字** | - |
| `score_best` | Text | score_chip | 下中 | 0,0 | wrap×wrap | - | - | 次级文本色，明显比 `score_value` 小且暗 | - |
| `start_card` | Surface | main | 居中 | 0,0 | 452dp×wrap | 38/44/30/44 | - | 磨砂玻璃卡片，背后场景可辨但已模糊 | 26dp |
| `start_title` | Text | start_card | 上中 | 0,0 | wrap×wrap | - | 下 7 | **全卡最大、最亮的文本**，字距明显宽于其它文本 | - |
| `start_subtitle` | Text | start_card | 上中 | 0,53 | wrap×wrap | - | 下 16 | 次级文本色，极小号，字距极宽 | - |
| `start_tagline` | Text | start_card | 上中 | 0,80 | wrap×wrap | - | 下 20 | 次级文本色，两行居中，明显小于标题 | - |
| `start_best` | Text | start_card | 上中 | 0,136 | wrap×wrap | - | 下 22 | 次级文本色；其中数字比同行文字大一档 | - |
| `start_button` | Button | start_card | 下中 | 0,36 | fill×48dp | 14/0/14/0 | - | **实心暖色填充，全卡唯一高饱和块面**，文字为深色 | 16dp |
| `start_hint` | Text | start_card | 下中 | 0,0 | wrap×wrap | - | 上 14 | 全卡最暗、最小的文本 | - |
| `result_card` | Surface | main | 居中 | 0,0 | 470dp×wrap | 32/42/28/42 | - | 与 `start_card` 同一种磨砂玻璃 | 26dp |
| `result_title` | Text | result_card | 上中 | 0,0 | wrap×wrap | - | 下 10 | 主文本色，大号，字距偏宽 | - |
| `result_score` | Text | result_card | 上中 | 0,44 | wrap×wrap | - | 下 22 | **全卡最大的数字，且是卡内唯一的暖色文本**，与 `retry_button` 同色系 | 等宽数字 |
| `result_stats` | Row | result_card | 上中 | 0,132 | fill×wrap | - | 下 22 | 4 等分列，列宽一致 | - |
| `result_stat_label` ×4 | Text | result_stats | 上中 | 0,0 | wrap×wrap | - | 下 4 | 次级文本色，小号 | - |
| `result_stat_value` ×4 | Text | result_stats | 上中 | 0,16 | wrap×wrap | - | - | 主文本色，比 label 大一档，等宽数字 | - |
| `result_divider` | Divider | result_card | 上中 | 0,196 | fill×1dp | - | 下 14 | 极细分隔线，亮度介于文本与卡背景之间 | - |
| `result_best` | Text | result_card | 上中 | 0,211 | wrap×wrap | - | 下 22 | 次级文本色，小号 | - |
| `retry_button` | Button | result_card | 下中 | 0,58 | fill×48dp | 14/0/14/0 | - | 实心暖色填充，与 `start_button` 同款 | 16dp |
| `home_button` | Button | result_card | 下中 | 0,0 | fill×48dp | 14/0/14/0 | 上 10 | 半透明填充 + 细描边，**明显弱于 `retry_button`** | 16dp |

## 2b. 元素表 · 插画层（Compose Canvas）

**这一层运行时根本不经过主题系统。** 和 UI 层不同（UI 层仍然跑在 `PicoTheme` 里，只是契约不写角色名），插画层的颜色来自水彩位图本身加一层按体温计算的染色，用 `WinterPalette` 命名常量集中管理，取值是 `ref-spritesheet.png` 的实测色卡。语义色角色在这里没有意义——它会丢掉「温度」这层含义。

| id | 素材 / 画法 | 层 | 视差 | 位置 | 尺寸 | 备注 |
|---|---|---|---|---|---|---|
| `bg_mood` | `mountains_*.png` ×5 | L1 | 0.10 | 铺满，镜像平铺 | 高 820dp，Y 偏移 −186dp | 按体温在五档间交叉淡入 |
| `bg_haze` | 程序绘制 | L1 上 | - | 铺满 | fill | 暖端浅雾 / 冷端暗雾，随体温插值 |
| `obstacle_top` | `trunk_*.png` 竖直翻转 | L3 | 1.00 | 枝头端对齐判定线 | 宽 76dp | 参与碰撞 |
| `obstacle_bottom` | `trunk_*.png` | L3 | 1.00 | 雪冠顶对齐判定线 | 宽 76dp | 参与碰撞 |
| `icicles` | 程序绘制 | L3 | 1.00 | 上障碍枝头下缘 | 5 根，等长 12dp | **最长一根的尖端 = 上障碍判定线** |
| `berry` | `berry_*.png` ×3 | L4 | 1.00 | 缝隙中线 ±18dp，长梗连到枝干 | 46dp 宽 | 收集物，不参与碰撞 |
| `berry_glow` | 程序绘制径向渐变 | L4 | 1.00 | berry 中心 | r 40dp | 暖色，2s 一次呼吸 |
| `bloom` | `blossom_*.png` ×3 | L4 | 1.00 | 判定线内侧 **30dp**，短梗 | 36dp 宽 | 收集物，不参与碰撞，无辉光 |
| `bird` | `bird_body.png` + `bird_wing.png` | L4 | - | 屏幕 x=300dp 固定 | 机身 86dp 宽 | 翅膀绕肩点旋转，见 §3 |
| `bird_shadow` | 程序绘制 | L4 | - | 机身下方 | blur 14dp / 下偏 6dp | 不进素材 |
| `snow` | `flake_*.png` ×3 | O1 | - | 全屏 | r 1~3dp | 50~190 粒，随体温 |
| `frost` | `frost_corner.png` 四角旋转复用 | O2 | - | 四角 | 512dp | 体温 < 约 45° 起显影 |
| `paper_grain` | `paper_grain.png` 平铺 | O3 | - | 铺满 | 256dp | overlay 混合，恒定 |

**染色规则**：L1 靠五档氛围图交叉淡入；`obstacle_*` / `icicles` 全强度冷色染色；**`berry` / `bloom` / `bird` 不参与染色**——收集物和玩家必须在任何温度下都最扎眼。

---

## 2c. 碰撞契约（截图不可判定，靠 DEBUG 描边核对）

| 项 | 值 |
|---|---|
| 碰撞体形状 | 单个矩形，宽 **76dp** |
| 上障碍判定下沿 | 冰柱最长一根的尖端（由素材 alpha 自动算出，写入 `assets-collision.json`） |
| 下障碍判定上沿 | 积雪冠最高点（同上） |
| 缝隙高度 | 206dp |
| 枝干水平间距 | 330dp |
| 收集物 | 不参与碰撞；中心距判定线 **≥ 30dp**，整体不得越过判定线 |
| 已知宽容度偏差 | ① 主干最宽 58dp < 矩形 76dp（无害方向）② 冰柱之间的空隙被矩形算入，最多 12dp（刻意偏严） |
| 验证方式 | `DEBUG` 开关描出碰撞矩形，逐帧核对轮廓与矩形对齐 |

---

## 3. 状态清单

| 元素 id | 状态 | 视觉变化 |
|---|---|---|
| — | 游戏阶段 | `NotStarted` → `Playing` → `GameOver`，三态互斥 |
| `start_card` | NotStarted | 显示；其余阶段隐藏。背后场景持续运转（飘雪 / 视差 / 扇翅） |
| `hud` | Playing | 显示；NotStarted 与 GameOver 隐藏 |
| `result_card` | GameOver | 显示。场景不切走、不淡出黑屏，原地压暗 40% |
| `temp_track` | 体温 100→0 | 填充宽度线性收缩；填充色从暖橙插值到冷蓝 |
| `bg_mood` | 体温 100/75/50/25/0 | 晴日 → 阴雪 → 金色黄昏 → 紫暮 → 蓝夜，相邻两档交叉淡入 |
| `frost` | 体温 < 约 45° | 开始显影，随体温线性加深 |
| `snow` | 体温下降 | 粒子数 50→190，下落速度同步加快 |
| `bird` | 上升 / 下落 | 俯仰角由竖直速度换算，钳在 −22°~+36°；上升时拍打加快，下落收敛到滑翔角 +1° |
| `bird` | 撞击硬直（0.8s） | 以 8Hz 闪烁（透明度 1↔0.35），无其它附加表现 |
| `bird` | GameOver | 倒伏在雪地上，旋转约 132°，翅膀停在收起位 |
| `start_button` / `retry_button` / `home_button` | hover | `Modifier.spatialHoverEffect`，背景提亮一档 |
| `result_title` | 破纪录 | 文案改为「新纪录！」，`result_score` 走一段计数动画；其余元素不变 |

---

## 4. 文案清单

| 元素 id | 文案 |
|---|---|
| `start_title` | 越冬 |
| `start_subtitle` | OVERWINTER |
| `start_tagline` | 体温在流失\n吃到浆果，才撑得过这片林子 |
| `start_best` | 历史最高 {n} 分 |
| `start_button` | 开始飞行 |
| `start_hint` | 点击窗口任意处 · 扇动翅膀 |
| `temp_value` | {n}° |
| `score_value` | {n} |
| `score_best` | 最高 {n} |
| `result_title` | 冻僵了 / 新纪录！ |
| `result_score` | {n} |
| `result_stat_label` ×4 | 穿过枝干 / 吃到浆果 / 采到花朵 / 坚持 |
| `result_stat_value` ×4 | {n} / {n} / {n} / {n.n}s |
| `result_best` | 历史最高 {n} 分 |
| `retry_button` | 再飞一次 |
| `home_button` | 回到开始页 |

---

## 5. 数值契约

| 项 | 值 |
|---|---|
| 起始体温 | 100° |
| 自然流失 | **−1.2°/s**（2026-08-18 用户要求调慢，原 −2°/s）。纯挨冻可撑 83s |
| 撞枝 | −25°，**并进入 0.8s 撞击硬直**（小鸟闪烁，此期间不再扣血）——否则同一次碰撞会逐帧连续扣血 |
| 体温归零 | 立即 GameOver |
| 触地 | 立即 GameOver，不走扣体温流程 |
| 触顶 | 软限制：y 钳在画面上沿，不扣体温、不结束 |
| 吃浆果 | +18°，上限 100° |
| 吃花朵 | +5 分 + **3 秒无敌**（**不回体温**），见 §5c |
| 浆果出现率 | 约每 2 组枝干 |
| 花朵出现率 | 约每 7 组枝干 |
| 分数 | 穿过枝干 ×1 + 采到花朵 ×5 |
| 输入 | 点击窗口任意处 = 扇翅一次；无长按、无连发 |

---

## 5b. 音效（v2 新增）

素材由用户提供，2026-08-18 直接指定四个用途。存放在 `app/src/main/assets/audio/`。

| 触发 | 文件 | 源文件 | 时长 | 播放方式 |
|---|---|---|---|---|
| 扇翅 | `flap.wav` | 飞翔.mp3 | 0.36s | SoundPool，**连点时掐掉上一声**再起新的 |
| 吃到浆果 **或** 花朵 | `pickup.mp3` | 欢喜.mp3 | 1.03s | SoundPool，两种收集物共用一个音 |
| 撞枝 | `hit.wav` | 撞击.mp3 | 0.68s | SoundPool。引擎有 0.8s 硬直，天然不连发 |
| 进入结算态 | `fall.mp3` | 坠落.mp3 | 5.04s | SoundPool，一次 |
| 环境音 | `wind.mp3` | 风声.mp3 | 19.03s | MediaPlayer 循环，音量 0.35 |

**对 v1 的偏离，逐条记明**：

1. **v1 说「不做背景音乐」，现在有了循环环境音。** 风声是环境音不是器乐，且用户直接指定。§6 那条改成只排除器乐。
2. **v1 把浆果和花朵列为两个音，现在合并成一个。** 按用户给的用途表（"吃到奖品"）。
3. **撞击音已补齐（2026-08-18 用户追加 `撞击.mp3`）。** v1 的四个音效现在是五个：多出循环环境音，且浆果与花朵合并。撞枝与结算各有各的音，前者短促、后者长。
4. **两个音做了裁剪，原文件未入库**：
   - `飞翔.mp3` 是**三次连续振翅共 2.4s**，每次点击播完整段会听到三下、连点还会糊，只取第 2 次振翅（0.855s~1.215s）+ 两端淡入淡出 → `flap.wav` 0.36s
   - `撞击.mp3` **起音前有 0.151s 静音**，碰撞反馈慢半拍会明显和画面脱节，裁掉头尾（0.125s~0.800s）+ 淡出 → `hit.wav` 0.68s，起音 0.026s

**验证方式**：音效截图判不出来，靠 `adb shell dumpsys audio` 看系统里有没有本应用的活跃播放器，以及 logcat 无 SoundPool / MediaPlayer 异常。

## 5c. 花朵无敌（v3 新增，原计划 V2）

吃到花朵获得 **3 秒无敌**。

| 规则 | 值 |
|---|---|
| 时长 | 3.0s，采到即刷满（不叠加） |
| 挡什么 | 撞枝不扣血、不进硬直，**直接穿过去** |
| 不挡什么 | **体温照常流失** —— 无敌不保暖，这是它不能当免死金牌用的原因 |
| 触地 | 无敌期间地面当**地板**钳住，不判死。否则「三秒内不会死」是句假话 |
| 与撞击硬直的关系 | 两者互斥且视觉不同：无敌是暖白光晕 + 倒计时环，硬直只是 8Hz 闪烁 |

表现（截图判据）：

- 小鸟外圈**暖白加色光晕**，2 秒周期呼吸
- 身后三重递减余晖残影
- 穿过枝干时接触点炸**白闪 + 雪雾**——告诉玩家"穿过去了"而不是"卡住了"
- **四角霜华压到 38%**，低温下能明显感到"松了一口气"
- HUD 在体温胶囊右侧出现**倒计时环胶囊**，显示剩余秒数；其余时刻隐藏不占位

设计意图：无敌不是免死金牌，是三秒钟"可以走直线"的通行证。这段时间最优解从上下起伏穿缝隙变成压直线多穿几组，节奏对比本身就是奖励。

**已知瑕疵**：余晖残影基本被光晕盖住，实机上看不出来。不影响可读性，暂不修。

## 6. 不做清单（YAGNI 边界）

- 不做皮肤 / 金币 / 内购
- 不做排行榜与联网，最高分只存本地
- 不做多关卡与主题切换，难度靠单一曲线爬升
- 不做暂停菜单
- 不做背景音乐（**器乐**）。循环环境音见 §5b，那不是音乐
- 不做运行时图集打包，素材按文件读，构建期只做 WebP 压缩
- 不引入游戏引擎或第三方渲染库
- 不用 3D / ECS
- 不用手部追踪（`WindowContainer` 下拿不到数据）
- 不做骨骼动画
- 不为五档氛围各做一套雪松 / 雪地素材，那几层统一走运行时染色
- 不在缝隙里放任何非收集物

---

## 7. 截图验证计划

总计 3 张，全部在门禁 B 的 90 秒锁内拍完。

| 序号 | 状态描述 | 到达方式 | 本图核对的元素 id |
|---|---|---|---|
| 1 | 冷启动默认态（暖端氛围） | 启动后等待渲染 6s | `start_title` `start_subtitle` `start_tagline` `start_best` `start_button` `start_hint`；`bg_mood` 为晴日档；`bird` 在卡片后可见 |
| 2 | 游玩中 · 低温（冷端氛围） | 点击 `start_button` 进入 Playing，用 `DEBUG` 把体温强制设为 18° | `temp_chip` `temp_track` `temp_value` `score_chip` `score_value` `score_best`；`obstacle_top` `obstacle_bottom` `icicles` `berry` `bloom` `bird`；`bg_mood` 已切到紫暮/蓝夜档；`frost` 已显影；缝隙上下沿清晰可辨 |
| 3 | 结算态 | `DEBUG` 强制体温归零进入 GameOver | `result_title` `result_score` `result_stats`（4 列全在）`result_divider` `result_best` `retry_button` `home_button`；场景未切走且已压暗 |

**本轮不验证**（功能延后或缺素材，留待契约 v2 增量）：花朵无敌态整帧、锯断截面帽、积雪冠、前景雪地层 L5、`paper_grain` 的实际观感。

**预期不可判定项**（透视截图判不出，报告中须显式列出）：所有精确 dp 间距与尺寸（含 `temp_track` 的 184×9dp）、碰撞矩形与轮廓的对齐、冰柱 12dp 等长、磨砂玻璃的模糊半径。

---

# 越冬 · 设计契约 v3 → v4 增量（新增"玩法"入口按钮 + 玩法说明弹层）

## 0. 元信息（追加）

- 契约版本：v4（v3 → v4 变更：开始页新增"玩法"入口按钮与配套的玩法说明弹层；不改动任何既有元素的位置/逻辑）
- 变更范围：仅 `StartCard`（`Cards.kt`）新增一个浮动按钮 + 一层可关闭的说明浮层；`ResultCard`、`Hud`、`GameEngine` 均不受影响
- 说明：本次改动没有独立的 Figma/截图设计源，本增量本身即为该功能的设计依据（门禁 A 已用户确认生效）

## 1. 面板清单（追加）

| 面板 id | 容器类型 | 面板尺寸 | 层级/父面板 | 出现时机 |
|---|---|---|---|---|
| `howto_card` | Surface（非容器，`BasicSheet` 承载） | 480dp×wrap（内部滚动，可视高度上限约 340dp） | main · 覆盖层，浮于 `start_card` 之上 | NotStarted 且 `showHowTo=true` |

容器类型不变：仍是 `main` 这一个 `WindowContainer` 内部的 Compose 覆盖层，不新增独立容器，不引入 Stage。`howto_card` 走的是 `ResultCard` 已经在用的 `BasicSheet`（`SpatialDialogDelegate`），不是手写 Scrim。

## 2. 元素表 · UI 层（追加）

| id | 类型 | 父容器 | 锚点 | 偏移 | 尺寸 | 视觉描述（截图判据） | 圆角/形状 |
|---|---|---|---|---|---|---|---|
| `howto_button` | Button（`ButtonDefaults.Min` 预设，57×32dp） | `start_card` 外新包一层 `Box` | 右上 | 16,16 | wrap×32dp | 容器色 = `fillPrimary`（与 `start_button` 同一角色）alpha≈0.3 的浅淡填充；内容色（图标+文字）同一角色但满饱和；前置一个 16dp 圆形"?"徽标；不带描边；独立浮在卡片右上角，不落进标题/最高分/开始按钮那条纵向排列 | Min 预设自带胶囊圆角，不单独指定 shape |
| `howto_badge` | Box + Text（圆形"?"字形，Canvas 手绘图标同款退化手法） | `howto_button` | 内容首位 | - | 16×16dp | 底色 `fillPrimary` alpha≈0.68，文字用 `labelPrimaryLight`（压在暖色填充上的深字，与 `start_button` 文字同一角色） | 圆形 |
| `howto_card` | Surface（`BasicSheet` 内容） | `BasicSheet` | 居中 | 0,0 | 480dp×wrap（内部滚动） | 磨砂玻璃卡片，与 `start_card`/`result_card` 同一种玻璃质感（复用项目内 `Card` 私有组合项）；背后 `start_card` 被 `BasicSheet` 自带的模态遮罩压暗 | 26dp，与 `start_card`/`result_card` 同一圆角 |
| `howto_header` | Row | `howto_card` | 上，横跨卡片宽 | - | fill×wrap | 标题（"越冬 · 玩法说明"）靠左，"×"关闭按钮靠右，两端对齐 | - |
| `howto_close_button` | Button（`ButtonDefaults.Min`，"×"字符） | `howto_header` | 右中 | - | wrap×32dp | 半透明白填充（`fillLight`，与 `home_button` 同款次要按钮语言）+ 次级文本色 `labelSecondary`，明显弱于主操作 | Min 预设胶囊圆角 |
| `howto_intro_label` / `howto_controls_label` / `howto_rules_label` | Text | `howto_card` 内滚动区 | 各分节顶部 | - | wrap×wrap | 强调色 `fillPrimary`（与 `result_score`/`retry_button` 同一路暖色高亮），文案分别为"简介"/"操作"/"规则" | - |
| `howto_intro_body` / `howto_controls_item` | Text | 对应分节标题下方 | - | - | fill×wrap | 主文本色 `labelPrimary`，正文字号，自动换行；`howto_controls_item` 保留原文"-"前缀 | - |
| `howto_rules_item` ×8 | Text | `howto_rules_label` 下方，逐条纵向排列 | - | - | fill×wrap | 主文本色 `labelPrimary`，保留原文"1."……"8." 编号，8 个独立 `Text`，不合并成一段、不换成短横线/圆点 | - |

**实现结构说明：**

- `howto_button` 通过在 `StartCard` 里现有 `Card(...)` 调用外新包一层 `Box` 实现，`Card` 内部原有 Column 的六个子元素（标题/副标题/tagline/最高分/开始按钮/hint）一个未动；`howto_button` 是这层新 `Box` 的第二个子项，`Modifier.align(Alignment.TopEnd)`。
- `howto_card` 复用 `ResultCard` 已在用的 `BasicSheet(onDismissRequest = ...)`。反编译确认它底层走 `SpatialDialogDelegate`，是独立模态窗口：点击卡片外部自动触发 `onDismissRequest`，卡片内部点击不会下穿——不需要再手写一遍 `Scrim` + `pointerInput` 消费。
- `howto_card` 内部结构：标题行（`howto_header`）+ 一个 `Column(Modifier.heightIn(max=340.dp).verticalScroll(...))` 承载三个分节，保证 8 条规则不被裁切。

## 3. 状态清单（追加）

| 元素 id | 状态 | 视觉变化 |
|---|---|---|
| — | 玩法弹层 | 新增布尔态 `showHowTo`，`remember` 在 `StartCard` 组合作用域内，只在 `NotStarted` 阶段存在；`when(phase)` 离开 `NotStarted` 时 `StartCard` 整体移出组合，该状态随之销毁，天然满足"切阶段前强制关闭"，且 `BasicSheet` 打开时挡住 `start_button`，不存在弹层开着还能进 `Playing` 的路径 |
| `howto_button` | NotStarted | 显示；随 `start_card` 一起，`Playing`/`GameOver` 隐藏 |
| `howto_card` | `showHowTo=true` | 显示，压在 `start_card` 之上；`start_card` 本身不隐藏、不改变（弹层是叠加，不是替换） |
| `howto_close_button` 点击 / 点击 `howto_card` 外部 | — | 两者效果相同（`BasicSheet` 的 `onDismissRequest`）：`showHowTo=false`，弹层消失，回到纯 `start_card` |

## 4. 文案清单（追加）

| 元素 id | 文案 |
|---|---|
| `howto_button` | 玩法 |
| `howto_title` | 越冬 · 玩法说明 |
| `howto_intro_label` | 简介 |
| `howto_intro_body` | 操控一只小鸟穿越结霜的枯枝林，靠点击不断振翅爬升，躲开树枝、吃浆果续命、采花朵加分，尽量在体温耗尽前飞得更远，刷新自己的历史最高分。 |
| `howto_controls_label` | 操作 |
| `howto_controls_item` | - 点击：点屏幕任意位置，小鸟就振一下翅膀往上冲；松手不点，它会持续往下坠。 |
| `howto_rules_label` | 规则 |
| `howto_rules_item` ×8（逐条原样，顺序不变） | 1. 你操控一只小鸟，在结霜的枯枝林里不停往前飞：点一下屏幕，它就振一下翅膀往上冲，点几下就飞几下，没有连发也没有蓄力。 2. 体温是你的生命线，从满格 100 开始就会随时间不断流逝——哪怕一根树枝都没撞到，它也会自己往下掉，迟早得靠拾取物续命。 3. 撞到树枝会让体温骤降一大截，比自然流逝快得多；小鸟会闪一下、下坠的势头被清空，但手感照旧，你可以立刻继续点击往上飞，不会被卡住输入。 4. 树枝丛里藏着两种拾取物：浆果能大幅回体温（顶多回满，不会超过 100），花朵能换来几秒无敌外加直接加分，但花朵本身不回体温——别把它当保命符。 5. 无敌时撞树枝会直接穿过去、完全不掉血，但体温该掉还是照掉；无敌快结束时如果小鸟正好贴着地面，一结束就会立刻按落地处理。 6. 飞太高会被轻轻托住，不扣分也不会出事；飞太低、机身碰到地面才是真的危险——只要那一刻没有无敌状态，游戏立刻结束。 7. 每完全飞过一组树枝记 1 分，空手飞过也算；每采到一朵花额外 +5 分；浆果只回体温、不计分。 8. 越往后飞，前进速度会越来越快（到一定距离后封顶，不会无限加速），但树枝间的空隙大小和间距始终不变——真正变难的是留给你反应的时间，不是关卡本身在变。 |

> 上表"简介/操作/规则"三段文案是唯一权威来源，逐字照抄，禁止改写、转述、精简或补充新内容。实现时 `howto_rules_item` 按原有 8 条编号逐条各占一个 Text（保留"1."……"8." 前缀），不合并成一整段，也不把编号换成短横线/圆点。

## 5. 数值契约 / 6. 不做清单（追加）

不新增、不变更任何数值或 YAGNI 边界；本次改动纯 UI，不涉及玩法数值、不涉及 `GameEngine`。

## 7. 截图验证计划（追加）

现有 3 张截图（冷启动 / 游玩中 / 结算）覆盖范围不含玩法弹层这一新状态。若纳入门禁 B 验证，可追加第 4 张截图：「点击 `howto_button` 后」，核对 `howto_card` `howto_title` `howto_intro_body` `howto_controls_item` `howto_rules_label`（分节标题可辨即可，8 条规则逐字判读不强求）、背景 `start_card` 已被压暗但仍可辨。
