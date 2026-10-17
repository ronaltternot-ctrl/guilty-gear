#!/usr/bin/env python3
"""Draws the HUD designs from scripts/test_hud_core.cpp's primitive dump over a sample picture,
to make the previews the launcher shows (res/drawable-nodpi/hud_*.webp).

Usage: python3 scripts/render_hud_previews.py prims.json background.png font.ttf out_dir
"""
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFont

SS = 2  # supersampling


def argb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >> 24) & 255)


def gradient(w, h, top, bottom):
    img = Image.new("RGBA", (max(1, w), max(1, h)))
    px = img.load()
    t, b = argb(top), argb(bottom)
    for y in range(img.height):
        f = y / max(1, img.height - 1)
        c = tuple(int(t[i] + (b[i] - t[i]) * f + 0.5) for i in range(4))
        for x in range(img.width):
            px[x, y] = c
    return img


def paste_shape(canvas, shape_mask, fill_img, box):
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    layer.paste(fill_img, box)
    alpha = Image.new("L", canvas.size, 0)
    alpha.paste(shape_mask, (0, 0))
    la = layer.split()[3]
    from PIL import ImageChops
    layer.putalpha(ImageChops.multiply(la, alpha))
    canvas.alpha_composite(layer)


def render(prims, size, game, bg, font_path):
    W, H = int(size[0] * SS), int(size[1] * SS)
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    gx, gy, gw, gh = [v * SS for v in game]
    canvas.alpha_composite(bg.resize((int(round(gw)), int(round(gh))), Image.BILINEAR).convert("RGBA"), (int(round(gx)), int(round(gy))))
    fonts = {}
    for p in prims:
        k = p["k"]
        if k in (0, 1):  # rect / vertical gradient
            x, y, w, h = p["x"] * SS, p["y"] * SS, p["w"] * SS, p["h"] * SS
            if w < 0.5 or h < 0.5:
                continue
            top, bottom = p["c1"], p["c2"] if k == 1 else p["c1"]
            img = gradient(int(round(w)), int(round(h)), top, bottom)
            mask = Image.new("L", (W, H), 0)
            ImageDraw.Draw(mask).rectangle([x, y, x + w - 1, y + h - 1], fill=255)
            paste_shape(canvas, mask, img, (int(round(x)), int(round(y))))
        elif k == 2:  # quad
            q = [v * SS for v in p["q"]]
            pts = [(q[0], q[1]), (q[2], q[3]), (q[4], q[5]), (q[6], q[7])]
            minx, maxx = min(a for a, _ in pts), max(a for a, _ in pts)
            miny, maxy = min(b for _, b in pts), max(b for _, b in pts)
            img = gradient(int(maxx - minx) + 2, int(maxy - miny) + 2, p["c1"], p["c2"])
            mask = Image.new("L", (W, H), 0)
            ImageDraw.Draw(mask).polygon(pts, fill=255)
            paste_shape(canvas, mask, img, (int(minx), int(miny)))
        elif k == 3:  # text
            px = max(4, int(p["s"] * SS / 1.17))
            if px not in fonts:
                fonts[px] = ImageFont.truetype(font_path, px)
            anchor = {-1: "lm", 0: "mm", 1: "rm"}[p["a"]]
            layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
            d = ImageDraw.Draw(layer)
            x, y = p["x"] * SS, p["y"] * SS
            col = argb(p["c1"])
            if p["sh"]:
                off = max(1, px // 14)
                d.text((x + off, y + off), p["t"], font=fonts[px], fill=(0, 0, 0, int(col[3] * 0.7)), anchor=anchor)
            d.text((x, y), p["t"], font=fonts[px], fill=col, anchor=anchor)
            canvas.alpha_composite(layer)
    return canvas.resize((int(size[0]), int(size[1])), Image.LANCZOS).convert("RGB")


def main():
    prims_path, bg_path, font_path, out_dir = sys.argv[1:5]
    data = json.load(open(prims_path))
    bg = Image.open(bg_path).convert("RGB")
    os.makedirs(out_dir, exist_ok=True)
    for name, prims in data["designs"].items():
        img = render(prims, data["screen"], data["game"], bg, font_path)
        img.save(os.path.join(out_dir, "hud_%s.png" % name))
        img.resize((800, 360), Image.LANCZOS).save(os.path.join(out_dir, "hud_%s.webp" % name), "WEBP", quality=88, method=6)
        print(name, img.size)


if __name__ == "__main__":
    main()
