#!/usr/bin/env python3
"""
校验《越冬》素材，并从 alpha 通道算出碰撞判定线。

用法：
    python3 validate-assets.py <素材目录>

它做三件事：
 1. 检查画布尺寸、透明通道是否真的存在（不是把纸底画成实色）
 2. 检查 trunk_* 的最大内容宽度 <= 152px（碰撞矩形宽 76dp @2x），以及上下端是否硬切、枝头端是否留出无节疤区
 3. 从 cap_top_* / crown_bot_* 的 alpha 算出判定线，写进 assets-collision.json

约定：设计基准 1040x580dp，素材按 2x 出图（1dp = 2px）。
"""
import sys, os, json, glob

try:
    from PIL import Image
except ImportError:
    sys.exit("需要 Pillow：pip3 install Pillow")

SCALE = 2                 # 素材密度
COLLIDER_DP = 76          # 碰撞矩形宽度
TRUNK_CORE_DP = 64        # 主干宽度
ALPHA_MIN = 24            # 低于这个 alpha 视为羽化尾巴，不算内容

COLLIDER_PX = COLLIDER_DP * SCALE      # 152
TRUNK_CORE_PX = TRUNK_CORE_DP * SCALE  # 128

# 文件名 glob -> (画布宽, 画布高, 期望张数)
SPEC = [
    ("bird_body.png",     256,  256, 1),
    ("bird_wing.png",     192,  192, 1),
    ("bird_frame_*.png",  256,  256, 3),
    ("berry_*.png",       160,  160, 3),
    ("blossom_*.png",     160,  160, 3),
    ("trunk_*.png",       160,  768, 3),
    ("cap_top_*.png",     192,  160, 2),
    ("crown_bot_*.png",   192,  160, 2),
    ("mountains_*.png",  2400,  700, 5),
    ("pines.png",        2400,  520, 1),
    ("snowfield.png",    2400,  220, 1),
    ("flake_*.png",        64,   64, 3),
    ("frost_corner.png",  512,  512, 1),
    ("paper_grain.png",   256,  256, 1),
]
OPAQUE_OK = ("mountains_",)   # 这几类允许不透明


def rows_span(im):
    """逐行返回 (最左, 最右, 宽度)，无内容的行返回 None。"""
    a = im.getchannel("A").load()
    w, h = im.size
    out = []
    for y in range(h):
        lo, hi = None, None
        for x in range(w):
            if a[x, y] >= ALPHA_MIN:
                if lo is None:
                    lo = x
                hi = x
        out.append(None if lo is None else (lo, hi, hi - lo + 1))
    return out


class Report:
    def __init__(self):
        self.errors, self.warns, self.notes = [], [], []

    def err(self, f, m):  self.errors.append((f, m))
    def warn(self, f, m): self.warns.append((f, m))
    def note(self, f, m): self.notes.append((f, m))


def check_basics(path, name, exp_w, exp_h, rep):
    im = Image.open(path)
    w, h = im.size
    if (w, h) != (exp_w, exp_h):
        rep.err(name, f"画布 {w}x{h}，应为 {exp_w}x{exp_h}")
    needs_alpha = not name.startswith(OPAQUE_OK)
    if needs_alpha:
        if im.mode != "RGBA":
            rep.err(name, f"色彩模式 {im.mode}，需要 RGBA")
            return None
        lo, hi = im.getchannel("A").getextrema()
        if lo >= 250:
            rep.err(name, "alpha 全不透明——背景是实色画进去的，需要透明底重出")
            return None
        return im.convert("RGBA")
    return im.convert("RGBA") if im.mode == "RGBA" else im


def check_trunk(im, name, rep):
    spans = rows_span(im)
    filled = [s for s in spans if s]
    if not filled:
        rep.err(name, "整张是空的")
        return
    max_w = max(s[2] for s in filled)
    if max_w > COLLIDER_PX:
        worst = max(range(len(spans)), key=lambda y: spans[y][2] if spans[y] else -1)
        rep.err(name, f"最大内容宽 {max_w}px > 碰撞矩形 {COLLIDER_PX}px（第 {worst} 行）"
                      f"——节疤超界，会出现「看着穿过树枝却没死」")
    else:
        rep.note(name, f"最大内容宽 {max_w}px，余量 {COLLIDER_PX - max_w}px")

    # 上下端硬切：首末行必须有接近主干宽度的内容
    for label, y in (("顶端", 0), ("底端", len(spans) - 1)):
        s = spans[y]
        if not s or s[2] < TRUNK_CORE_PX * 0.8:
            got = 0 if not s else s[2]
            rep.err(name, f"{label}不是硬切（该行内容宽 {got}px，应 ≥ {int(TRUNK_CORE_PX*0.8)}px）"
                          f"——障碍要从这一端按需裁切，收口了会露出断头")

    # 枝头端（顶部 200px）无节疤
    head = [s[2] for s in spans[:200] if s]
    if head and max(head) > TRUNK_CORE_PX + 8:
        rep.err(name, f"枝头端 200px 内有节疤（最宽 {max(head)}px）——那一段会被帽/冠盖住")


def first_gapped_row(im):
    """从上往下找第一行出现断口的行——那就是帽体结束、冰柱开始的地方。"""
    a = im.getchannel("A").load()
    w, h = im.size
    for y in range(h):
        runs, run = 0, False
        gap = 0
        for x in range(w):
            on = a[x, y] >= ALPHA_MIN
            if on:
                if not run:
                    if runs and gap < 3:
                        pass          # 太窄的缝当作同一段
                    else:
                        runs += 1
                    run = True
                gap = 0
            else:
                if run:
                    run = False
                gap += 1
        if runs >= 2:
            return y
    return None


def icicle_tips(im, cap_bottom_y):
    """返回冰柱区各根冰柱的尖端 y。"""
    a = im.getchannel("A").load()
    w, h = im.size
    cols = {}
    for x in range(w):
        lowest = None
        for y in range(cap_bottom_y, h):
            if a[x, y] >= ALPHA_MIN:
                lowest = y
        if lowest is not None:
            cols[x] = lowest
    tips, cur = [], []
    for x in sorted(cols):
        if cur and x - cur[-1] > 3:
            tips.append(max(cols[c] for c in cur)); cur = []
        cur.append(x)
    if cur:
        tips.append(max(cols[c] for c in cur))
    return tips


def check_cap(im, name, rep, out):
    spans = rows_span(im)
    filled = [(y, s) for y, s in enumerate(spans) if s]
    if not filled:
        rep.err(name, "整张是空的"); return
    max_w = max(s[2] for _, s in filled)
    if max_w > COLLIDER_PX:
        rep.err(name, f"帽宽 {max_w}px > 碰撞矩形 {COLLIDER_PX}px")
    line = filled[-1][0]                       # 判定线 = 最低不透明行
    # 帽体下沿 = 第一行出现断口的地方（冰柱是分开的，帽体是连续的）
    cap_bottom = first_gapped_row(im)
    if cap_bottom is None:                     # 退化：没有断口就用宽度骤降判断
        cap_bottom = line
        for y, s in filled:
            if s[2] < max_w * 0.55:
                cap_bottom = y; break
    band = line - cap_bottom
    tips = icicle_tips(im, cap_bottom)
    spread = (max(tips) - min(tips)) if len(tips) > 1 else 0
    if band > 32:
        rep.warn(name, f"冰柱带 {band}px（{band/SCALE:.0f}dp）偏长，会吃掉缝隙高度，建议 ≤ 24px")
    if spread > 12:
        rep.warn(name, f"{len(tips)} 根冰柱长度相差 {spread}px（{spread/SCALE:.0f}dp）"
                       f"——判定线取最长那根，差太多会让判定明显偏严")
    rep.note(name, f"判定线 y={line}（距底 {im.size[1]-1-line}px）· 帽宽 {max_w}px · "
                   f"冰柱 {len(tips)} 根 / 带高 {band}px / 参差 {spread}px")
    out[name] = {"edge": "bottom", "line_px": line, "line_dp": round(line / SCALE, 1),
                 "content_w_px": max_w, "icicle_band_px": band}


def check_crown(im, name, rep, out):
    spans = rows_span(im)
    filled = [(y, s) for y, s in enumerate(spans) if s]
    if not filled:
        rep.err(name, "整张是空的"); return
    max_w = max(s[2] for _, s in filled)
    if max_w > COLLIDER_PX:
        rep.err(name, f"冠宽 {max_w}px > 碰撞矩形 {COLLIDER_PX}px")
    line, top = filled[0]                      # 判定线 = 最高不透明行
    cx = (top[0] + top[1]) / 2
    lo = min(s[0] for _, s in filled); hi = max(s[1] for _, s in filled)
    mid = (lo + hi) / 2; half = (hi - lo) / 2
    if half and abs(cx - mid) > half * 0.30:
        rep.warn(name, f"最高点偏离中心 {abs(cx-mid):.0f}px（内容半宽 {half:.0f}px）"
                       f"——判定线取最高点，偏到一侧会让另一侧看起来能过其实过不去")
    rep.note(name, f"判定线 y={line} · 冠宽 {max_w}px · 最高点 x={cx:.0f}（中心 {mid:.0f}）")
    out[name] = {"edge": "top", "line_px": line, "line_dp": round(line / SCALE, 1),
                 "content_w_px": max_w}


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    root = sys.argv[1]
    if not os.path.isdir(root):
        sys.exit(f"目录不存在：{root}")

    rep, collision, total, found = Report(), {}, 0, 0
    for pattern, w, h, count in SPEC:
        hits = sorted(glob.glob(os.path.join(root, pattern)))
        total += count
        found += len(hits)
        if len(hits) != count:
            rep.err(pattern, f"找到 {len(hits)} 张，应为 {count} 张")
        for p in hits:
            name = os.path.basename(p)
            im = check_basics(p, name, w, h, rep)
            if im is None:
                continue
            if name.startswith("trunk_"):
                check_trunk(im, name, rep)
            elif name.startswith("cap_top_"):
                check_cap(im, name, rep, collision)
            elif name.startswith("crown_bot_"):
                check_crown(im, name, rep, collision)

    print(f"\n素材 {found}/{total} 张\n")
    for title, items, mark in (("阻塞", rep.errors, "✗"),
                               ("警告", rep.warns, "!"),
                               ("实测", rep.notes, "·")):
        if not items:
            continue
        print(f"── {title} ──")
        for f, m in items:
            print(f" {mark} {f:22s} {m}")
        print()

    if collision:
        out = os.path.join(root, "assets-collision.json")
        with open(out, "w", encoding="utf-8") as fh:
            json.dump(collision, fh, ensure_ascii=False, indent=2)
        print(f"判定线已写入 {out}\n")

    if rep.errors:
        print(f"有 {len(rep.errors)} 项阻塞，需要重出后再跑一次。")
        return 1
    print("全部通过。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
