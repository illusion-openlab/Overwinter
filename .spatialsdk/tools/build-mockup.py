#!/usr/bin/env python3
"""
把 design-ref/cutouts/ 里的素材内联成 data URI，生成可发布的 design-mockup.html。

    python3 tools/build-mockup.py

源文件是 design-ref/design-mockup.src.html，其中脚本里的 /*__ASSETS__*/{}
会被替换成 {名字: dataURI} 字典。不透明的背景图转 JPEG，其余保持 PNG。
"""
import base64, io, json, os, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)                       # .spatialsdk/
REF  = os.path.join(ROOT, "design-ref")
CUTS = os.path.join(REF, "cutouts")
SRC  = os.path.join(REF, "design-mockup.src.html")
OUT  = os.path.join(REF, "design-mockup.html")

JPEG = ("mtn_", "grain")        # 不需要透明的，转 JPEG 省体积
JPEG_Q = 86


def encode(path):
    name = os.path.splitext(os.path.basename(path))[0]
    im = Image.open(path)
    buf = io.BytesIO()
    if name.startswith(JPEG):
        im.convert("RGB").save(buf, "JPEG", quality=JPEG_Q, optimize=True)
        mime = "jpeg"
    else:
        im.convert("RGBA").save(buf, "PNG", optimize=True)
        mime = "png"
    raw = buf.getvalue()
    return name, "data:image/%s;base64,%s" % (mime, base64.b64encode(raw).decode()), len(raw)


def main():
    if not os.path.isdir(CUTS):
        sys.exit("没有 %s" % CUTS)
    if not os.path.isfile(SRC):
        sys.exit("没有 %s" % SRC)

    assets, total = {}, 0
    rows = []
    for f in sorted(os.listdir(CUTS)):
        if not f.lower().endswith((".png", ".jpg", ".jpeg")):
            continue
        name, uri, n = encode(os.path.join(CUTS, f))
        assets[name] = uri
        total += n
        rows.append((name, n))

    src = open(SRC, encoding="utf-8").read()

    # 说明用插图：把 figures/*.png 内联到 __FIG_<名字>__ 标记
    figs = os.path.join(REF, "figures")
    if os.path.isdir(figs):
        for f in sorted(os.listdir(figs)):
            if not f.lower().endswith(".png"):
                continue
            name = os.path.splitext(f)[0]
            buf = io.BytesIO()
            Image.open(os.path.join(figs, f)).convert("RGB").save(buf, "JPEG", quality=88, optimize=True)
            uri = "data:image/jpeg;base64," + base64.b64encode(buf.getvalue()).decode()
            tag = "__FIG_%s__" % name
            if tag in src:
                src = src.replace(tag, uri)
                total += len(buf.getvalue())
                rows.append(("fig:" + name, len(buf.getvalue())))
    marker = "/*__ASSETS__*/{}"
    if marker not in src:
        sys.exit("源文件里找不到 %s 占位符" % marker)
    html = src.replace(marker, "/*__ASSETS__*/" + json.dumps(assets, separators=(",", ":")))
    open(OUT, "w", encoding="utf-8").write(html)

    for name, n in rows:
        print("  %-14s %7.1f KB" % (name, n / 1024))
    print("\n素材 %d 件，压缩后 %.0f KB" % (len(assets), total / 1024))
    print("产物 %s  %.0f KB" % (os.path.relpath(OUT), os.path.getsize(OUT) / 1024))


if __name__ == "__main__":
    main()
