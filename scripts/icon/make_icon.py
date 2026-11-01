#!/usr/bin/env python3
"""GBS++ launcher icon, PS2 style: deep blue night, light towers, glowing white letters.

Writes, into <res dir> (overlay/android/normal/res):
  mipmap-<dpi>/ic_launcher_background.png, ic_launcher_foreground.png, ic_launcher_monochrome.png
  mipmap-<dpi>/ic_launcher.png (legacy rounded square), ic_launcher_round.png (legacy circle)
  drawable-xhdpi/ic_banner.png (Android TV, 320x180)
Usage: scripts/icon/make_icon.py overlay/android/normal/res [preview dir]   (needs Pillow and the Inter font)
"""
import math
import os
import random
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

FONT_DIR = "/usr/share/fonts/opentype/inter/"
BOLD = FONT_DIR + "InterDisplay-Bold.otf"
BLACK = FONT_DIR + "InterDisplay-Black.otf"
LIGHT = FONT_DIR + "InterDisplay-Light.otf"

BG_TOP = (11, 22, 56)        # #0B1638, like Ps2Background
BG_BOTTOM = (2, 4, 11)       # #02040B, the launcher's background
ACCENT = (111, 155, 255)     # #6F9BFF
ACCENT_HI = (150, 185, 255)
WHITE = (240, 244, 252)

HORIZON = 0.665            # the line of light under the letters, as a fraction of the height
SS = 4                       # supersampling
DP = 16                      # px per dp at xxxhdpi (4) x SS (4)
CANVAS = 108 * DP            # adaptive icon layer: 108dp

DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def lerp(a, b, t):
	return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def background(size):
	"""Vertical gradient, a soft glow behind the letters and PS2 'towers' of light."""
	w = h = size
	img = Image.new("RGB", (w, h))
	d = ImageDraw.Draw(img)
	for y in range(h):
		d.line([(0, y), (w, y)], fill=lerp(BG_TOP, BG_BOTTOM, (y / (h - 1)) ** 0.9))
	img = img.convert("RGBA")

	# Radial glow, slightly above the centre.
	glow = Image.new("L", (w, h), 0)
	gd = ImageDraw.Draw(glow)
	cx, cy = w * 0.5, h * 0.46
	for i in range(60, 0, -1):
		r = w * 0.48 * i / 60
		v = int(150 * (1 - i / 60) ** 1.6)
		gd.ellipse([cx - r, cy - r * 0.8, cx + r, cy + r * 0.8], fill=v)
	glow = glow.filter(ImageFilter.GaussianBlur(w * 0.03))
	blue = Image.new("RGBA", (w, h), (40, 80, 200, 255))
	img = Image.composite(blue, img, glow.point(lambda v: int(v * 0.55)))

	# Towers: thin translucent columns rising from a horizon, like the PS2 boot screen.
	rnd = random.Random(2000)
	towers = Image.new("RGBA", (w, h), (0, 0, 0, 0))
	td = ImageDraw.Draw(towers)
	horizon = h * HORIZON
	n = 11
	for i in range(n):
		x = w * (0.18 + 0.64 * i / (n - 1)) + rnd.uniform(-w * 0.012, w * 0.012)
		cw = w * rnd.uniform(0.010, 0.022)
		top = horizon - h * rnd.uniform(0.10, 0.30) * (1.2 - abs(i - (n - 1) / 2) / n)
		col = Image.new("L", (1, 256))
		for yy in range(256):
			col.putpixel((0, yy), int(95 * (yy / 255) ** 1.6))
		col = col.resize((max(1, int(cw)), max(1, int(horizon - top))))
		fill = Image.new("RGBA", col.size, ACCENT + (255,))
		towers.paste(fill, (int(x - cw / 2), int(top)), col)
	towers = towers.filter(ImageFilter.GaussianBlur(w * 0.002))
	img = Image.alpha_composite(img, towers)

	# Horizon line of light.
	line = Image.new("RGBA", (w, h), (0, 0, 0, 0))
	ld = ImageDraw.Draw(line)
	for x in range(w):
		t = 1 - abs(x - w / 2) / (w / 2)
		a = int(170 * t ** 1.5)
		ld.line([(x, horizon), (x, horizon + max(1, h * 0.004))], fill=ACCENT_HI + (a,))
	glow_line = line.filter(ImageFilter.GaussianBlur(h * 0.012))
	img = Image.alpha_composite(img, glow_line)
	img = Image.alpha_composite(img, line)

	# The floor below the horizon is darker, so the line reads as the edge of a stage.
	floor = Image.new("RGBA", (w, h), (0, 0, 0, 0))
	fd = ImageDraw.Draw(floor)
	for y in range(int(horizon + h * 0.006), h):
		t = (y - horizon) / (h - horizon)
		fd.line([(0, y), (w, y)], fill=(1, 3, 9, int(120 + 100 * min(1, t * 2))))
	img = Image.alpha_composite(img, floor)

	# A few small dots of light (stars).
	dots = Image.new("RGBA", (w, h), (0, 0, 0, 0))
	dd = ImageDraw.Draw(dots)
	for _ in range(16):
		x, y = rnd.uniform(w * 0.1, w * 0.9), rnd.uniform(h * 0.12, horizon * 0.95)
		r = w * rnd.uniform(0.002, 0.005)
		dd.ellipse([x - r, y - r, x + r, y + r], fill=(190, 210, 255, rnd.randint(60, 170)))
	img = Image.alpha_composite(img, dots)
	return img


def layout(draw, size, width_dp, cy_frac):
	"""Fonts and positions for 'GBS' with a superscript '++', fitted to width_dp (in 1/108ths of size)."""
	unit = size / 108.0
	f = 30.0
	for _ in range(3):
		main = ImageFont.truetype(BLACK, int(f * unit))
		plus = ImageFont.truetype(BLACK, int(f * 0.64 * unit))
		tb = draw.textbbox((0, 0), "GBS", font=main)
		pb = draw.textbbox((0, 0), "+", font=plus)
		tw, th = tb[2] - tb[0], tb[3] - tb[1]
		pw, ph = pb[2] - pb[0], pb[3] - pb[1]
		gap, pgap = f * 0.07 * unit, f * 0.04 * unit
		total = tw + gap + 2 * pw + pgap
		f *= width_dp * unit / total
	x0 = (size - total) / 2
	y0 = size * cy_frac - th / 2
	return dict(main=main, plus=plus, tb=tb, pb=pb, tw=tw, th=th, pw=pw, ph=ph, gap=gap, pgap=pgap, x0=x0, y0=y0, total=total)


def draw_mark(size, width_dp, cy_frac, w=None, h=None):
	"""Masks for the letters and the '++'."""
	w = w or size
	h = h or size
	letters = Image.new("L", (w, h), 0)
	pluses = Image.new("L", (w, h), 0)
	ld, pd = ImageDraw.Draw(letters), ImageDraw.Draw(pluses)
	L = layout(ld, size, width_dp, cy_frac)
	x0 = (w - L["total"]) / 2
	y0 = h * cy_frac - L["th"] / 2
	ld.text((x0 - L["tb"][0], y0 - L["tb"][1]), "GBS", font=L["main"], fill=255)
	px = x0 + L["tw"] + L["gap"]
	py = y0 - L["ph"] * 0.12          # superscript: top-aligned with the capitals, a little above
	for i in range(2):
		pd.text((px + i * (L["pw"] + L["pgap"]) - L["pb"][0], py - L["pb"][1]), "+", font=L["plus"], fill=255)
	return letters, pluses, L


def text_mask(size, mono=False):
	letters, pluses, _ = draw_mark(size, 56, 0.47)
	return letters, pluses


def foreground(size):
	w = h = size
	letters, pluses = text_mask(size)
	img = Image.new("RGBA", (w, h), (0, 0, 0, 0))

	# Glow behind everything.
	all_mask = ImageChops.lighter(letters, pluses)
	glow = all_mask.filter(ImageFilter.GaussianBlur(size * 0.024))
	glow_layer = Image.new("RGBA", (w, h), ACCENT + (255,))
	glow_layer.putalpha(glow.point(lambda v: int(min(255, v * 1.05))))
	img = Image.alpha_composite(img, glow_layer)

	# Letters: white with a faint top-to-bottom shading, like brushed light.
	shade = Image.new("RGBA", (w, h))
	sd = ImageDraw.Draw(shade)
	for y in range(h):
		t = y / (h - 1)
		sd.line([(0, y), (w, y)], fill=lerp((255, 255, 255), (196, 212, 245), max(0, min(1, (t - 0.35) / 0.25))) + (255,))
	letters_layer = shade.copy()
	letters_layer.putalpha(letters)
	img = Image.alpha_composite(img, letters_layer)

	plus_layer = Image.new("RGBA", (w, h), ACCENT_HI + (255,))
	plus_layer.putalpha(pluses)
	img = Image.alpha_composite(img, plus_layer)

	return img


def monochrome(size):
	letters, pluses = text_mask(size, mono=True)
	alpha = ImageChops.lighter(letters, pluses)
	img = Image.new("RGBA", (size, size), (255, 255, 255, 0))
	img.putalpha(alpha)
	return img


def rounded_mask(size, radius_frac):
	m = Image.new("L", (size, size), 0)
	ImageDraw.Draw(m).rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * radius_frac), fill=255)
	return m


def legacy(full, shape):
	"""48dp legacy icon from the 108dp layers: the inner 72dp, cut to a shape."""
	s = full.size[0]
	inset = s * 18 / 108 * 0.86     # a bit more than 72dp, so the letters aren't too small
	crop = full.crop((int(inset), int(inset), int(s - inset), int(s - inset)))
	cs = crop.size[0]
	if shape == "round":
		m = Image.new("L", (cs, cs), 0)
		ImageDraw.Draw(m).ellipse([0, 0, cs - 1, cs - 1], fill=255)
	else:
		m = rounded_mask(cs, 0.17)
	out = Image.new("RGBA", (cs, cs), (0, 0, 0, 0))
	out.paste(crop, (0, 0), m)
	# Hairline rim of light.
	rim = Image.new("RGBA", (cs, cs), (0, 0, 0, 0))
	rd = ImageDraw.Draw(rim)
	wdt = max(1, int(cs * 0.006))
	if shape == "round":
		rd.ellipse([wdt, wdt, cs - 1 - wdt, cs - 1 - wdt], outline=ACCENT + (90,), width=wdt)
	else:
		rd.rounded_rectangle([wdt, wdt, cs - 1 - wdt, cs - 1 - wdt], radius=int(cs * 0.17), outline=ACCENT + (90,), width=wdt)
	return Image.alpha_composite(out, rim)


def banner(w, h):
	"""320x180 Android TV banner."""
	W, H = w * SS, h * SS
	bgsq = background(W)
	top = int(W * HORIZON - H * 0.62)
	img = bgsq.crop((0, top, W, top + H))
	mask, pm, _ = draw_mark(H, 120, 0.36, W, H)
	glow = ImageChops.lighter(mask, pm).filter(ImageFilter.GaussianBlur(H * 0.035))
	gl = Image.new("RGBA", (W, H), ACCENT + (255,))
	gl.putalpha(glow.point(lambda v: int(min(255, v * 1.0))))
	img = Image.alpha_composite(img, gl)
	wl = Image.new("RGBA", (W, H), WHITE + (255,))
	wl.putalpha(mask)
	img = Image.alpha_composite(img, wl)
	pl = Image.new("RGBA", (W, H), ACCENT_HI + (255,))
	pl.putalpha(pm)
	img = Image.alpha_composite(img, pl)
	d = ImageDraw.Draw(img)
	sub = ImageFont.truetype(LIGHT, int(H * 0.068))
	label = "PSP · PS1 · DREAMCAST · ARCADE · MUGEN"
	sb = d.textbbox((0, 0), label, font=sub)
	d.text(((W - (sb[2] - sb[0])) / 2 - sb[0], H * 0.74), label, font=sub, fill=(170, 186, 222, 255))
	return img.resize((w, h), Image.LANCZOS)


def main():
	res = sys.argv[1]
	preview = sys.argv[2] if len(sys.argv) > 2 else None
	bg = background(CANVAS)
	fg = foreground(CANVAS)
	mono = monochrome(CANVAS)
	full = Image.alpha_composite(bg, fg)
	for name, scale in DENSITIES.items():
		folder = os.path.join(res, "mipmap-" + name)
		os.makedirs(folder, exist_ok=True)
		layer = int(round(108 * scale))
		icon = int(round(48 * scale))
		bg.resize((layer, layer), Image.LANCZOS).convert("RGB").save(os.path.join(folder, "ic_launcher_background.png"), optimize=True)
		fg.resize((layer, layer), Image.LANCZOS).save(os.path.join(folder, "ic_launcher_foreground.png"), optimize=True)
		mono.resize((layer, layer), Image.LANCZOS).save(os.path.join(folder, "ic_launcher_monochrome.png"), optimize=True)
		legacy(full, "square").resize((icon, icon), Image.LANCZOS).save(os.path.join(folder, "ic_launcher.png"), optimize=True)
		legacy(full, "round").resize((icon, icon), Image.LANCZOS).save(os.path.join(folder, "ic_launcher_round.png"), optimize=True)
	os.makedirs(os.path.join(res, "drawable-xhdpi"), exist_ok=True)
	banner(320, 180).convert("RGB").save(os.path.join(res, "drawable-xhdpi", "ic_banner.png"), optimize=True)

	if preview:
		os.makedirs(preview, exist_ok=True)
		# How launchers show it: the adaptive layers under a squircle and a circle mask, 72dp visible.
		s = 432
		lay = full.resize((s, s), Image.LANCZOS)
		vis = lay.crop((72, 72, 360, 360))
		sheet = Image.new("RGBA", (1180, 420), (226, 230, 238, 255))
		shapes = []
		m1 = rounded_mask(288, 0.30)
		m2 = Image.new("L", (288, 288), 0)
		ImageDraw.Draw(m2).ellipse([0, 0, 287, 287], fill=255)
		m3 = rounded_mask(288, 0.12)
		for i, m in enumerate([m1, m2, m3]):
			sheet.paste(vis, (40 + i * 310, 30), m)
		small = Image.alpha_composite(bg, fg).crop((int(CANVAS / 6), int(CANVAS / 6), int(CANVAS * 5 / 6), int(CANVAS * 5 / 6)))
		mm = Image.new("L", small.size, 0)
		ImageDraw.Draw(mm).ellipse([0, 0, small.size[0] - 1, small.size[1] - 1], fill=255)
		for j, sz in enumerate([144, 96, 48]):
			sm = small.resize((sz, sz), Image.LANCZOS)
			sheet.paste(sm, (980, 30 + sum([144, 96, 48][:j]) + j * 20), mm.resize((sz, sz), Image.LANCZOS))
		sheet.convert("RGB").save(os.path.join(preview, "icon_sheet.png"))
		banner(320, 180).resize((640, 360), Image.LANCZOS).convert("RGB").save(os.path.join(preview, "banner.png"))
		mono.resize((216, 216), Image.LANCZOS).save(os.path.join(preview, "mono.png"))


if __name__ == "__main__":
	main()
