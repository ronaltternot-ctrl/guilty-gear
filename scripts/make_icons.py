#!/usr/bin/env python3
"""Generates the app's original launcher icons and TV banner (no game artwork used).

Usage: python3 scripts/make_icons.py overlay/android/normal/res
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

CRIMSON = (176, 18, 44, 255)
CRIMSON_DARK = (110, 10, 28, 255)
INK = (13, 13, 17, 255)
WHITE = (255, 255, 255, 255)
GOLD = (255, 196, 64, 255)

FONT_CANDIDATES = [
    '/usr/share/fonts/opentype/noto/NotoSansCJK-Black.ttc',
    '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',
]


def font(size):
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def draw_mark(size):
    """Foreground artwork on a transparent square canvas (adaptive icon, 108dp grid)."""
    s = size * 4  # supersample
    img = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # Diagonal slash band behind the letters.
    band = [(s * 0.20, s * 0.78), (s * 0.44, s * 0.78), (s * 0.80, s * 0.22), (s * 0.56, s * 0.22)]
    d.polygon(band, fill=CRIMSON_DARK)

    # "ΛC" lettering plus a small "+", centered as one block inside the 66dp safe zone.
    f = font(int(s * 0.29))
    text = 'ΛC'
    box = d.textbbox((0, 0), text, font=f)
    tw, th = box[2] - box[0], box[3] - box[1]
    plus_w = s * 0.13
    block_w = tw + plus_w
    x = (s - block_w) / 2 - box[0]
    y = (s - th) / 2 - box[1] + s * 0.03

    shadow = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).text((x + s * 0.012, y + s * 0.016), text, font=f, fill=(0, 0, 0, 170))
    shadow = shadow.filter(ImageFilter.GaussianBlur(s * 0.01))
    img.alpha_composite(shadow)
    d = ImageDraw.Draw(img)
    d.text((x, y), text, font=f, fill=WHITE)

    # Gold "+" at the upper right of the lettering.
    p = s * 0.055
    cx, cy = x + box[0] + tw + plus_w / 2, y + box[1] + s * 0.03
    w = s * 0.021
    d.rectangle([cx - p, cy - w, cx + p, cy + w], fill=GOLD)
    d.rectangle([cx - w, cy - p, cx + w, cy + p], fill=GOLD)
    return img.resize((size, size), Image.LANCZOS)


def background(size, shape):
    s = size * 4
    img = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if shape == 'round':
        d.ellipse([0, 0, s - 1, s - 1], fill=CRIMSON)
    else:
        d.rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.18), fill=CRIMSON)
    return img.resize((size, size), Image.LANCZOS)


def legacy_icon(size, shape):
    bg = background(size, shape)
    # The adaptive foreground is drawn for a 108dp canvas; legacy icons show the 72dp center.
    mark = draw_mark(int(round(size * 108 / 72)))
    off = (mark.width - size) // 2
    mark = mark.crop((off, off, off + size, off + size))
    if shape == 'round':
        mask = Image.new('L', (size, size), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
        clipped = Image.new('RGBA', (size, size), (0, 0, 0, 0))
        clipped.paste(mark, (0, 0), mask)
        mark = clipped
    bg.alpha_composite(mark)
    return bg


def banner(w=320, h=180):
    s = 4
    img = Image.new('RGBA', (w * s, h * s), INK)
    d = ImageDraw.Draw(img)
    d.polygon([(w * s * 0.05, h * s), (w * s * 0.30, h * s), (w * s * 0.48, 0), (w * s * 0.23, 0)], fill=CRIMSON)
    f_small = font(int(h * s * 0.105))
    f_big = font(int(h * s * 0.34))
    d.text((w * s * 0.42, h * s * 0.22), 'GUILTY GEAR XX', font=f_small, fill=(215, 38, 61, 255))
    d.text((w * s * 0.42, h * s * 0.36), 'ΛC+', font=f_big, fill=WHITE)
    return img.resize((w, h), Image.LANCZOS)


def main(res_dir):
    densities = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
    for name, size in densities.items():
        out = os.path.join(res_dir, 'mipmap-' + name)
        os.makedirs(out, exist_ok=True)
        draw_mark(int(size * 108 / 48)).save(os.path.join(out, 'ic_launcher_foreground.png'))
        legacy_icon(size, 'square').save(os.path.join(out, 'ic_launcher.png'))
        legacy_icon(size, 'round').save(os.path.join(out, 'ic_launcher_round.png'))
    os.makedirs(os.path.join(res_dir, 'drawable-xhdpi'), exist_ok=True)
    banner().convert('RGB').save(os.path.join(res_dir, 'drawable-xhdpi', 'ic_banner.png'))
    os.makedirs(os.path.join(res_dir, 'values'), exist_ok=True)
    with open(os.path.join(res_dir, 'values', 'ic_launcher_background.xml'), 'w') as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n'
                '    <color name="ic_launcher_background">#B0122C</color>\n</resources>\n')
    print('icons written to', res_dir)


if __name__ == '__main__':
    main(sys.argv[1])
