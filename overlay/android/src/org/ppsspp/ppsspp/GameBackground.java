package org.ppsspp.ppsspp;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import java.util.Random;

/**
 * The home screen's background, in the style of the chosen game (GameTheme): a dark gradient with
 * a soft light from above, and one picture drawn with a handful of shapes:
 * - PS2: the faint towers of light of the boot screen along the bottom, and specks of light.
 * - Guilty Gear: big diagonal slashes, thin lines and scratches.
 * - BlazBlue: the rings of a magic seal, with ticks and a six-pointed star.
 * - KOF: fire glowing from below, flames and rising embers.
 * - Street Fighter Alpha: a sunburst from the top and specks of gold.
 * - Yu-Gi-Oh! ARC-V: the pillars of the pendulum scales on both sides, a holographic field of
 *   hexagons, cards floating over it and sparkles; and, as an emblem above the title (a second
 *   GameBackground made with emblem = true), a pendulum of light swinging over a rainbow arc.
 *   The most detailed one: each is drawn once into a bitmap.
 * Static (it doesn't move with the page, and the shapes are fixed), so scrolling stays smooth.
 */
final class GameBackground extends Drawable {
	private final GameTheme t;
	private final float density;
	private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint shape = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint extra = new Paint(Paint.ANTI_ALIAS_FLAG);

	/** Random numbers for the picture, fixed per pattern: the same picture every time. */
	private final float[] items;
	private final float[] dots;
	private int lastW, lastH;
	private Shader[] shaders = new Shader[0];
	private final Path path = new Path();
	private DashPathEffect dash;
	/** The pendulum picture, drawn once per size. */
	private Bitmap cache;

	/** Only the emblem (transparent), for the stage above the title; see drawEmblem. */
	private final boolean emblem;

	GameBackground(GameTheme theme, float density) {
		this(theme, density, false);
	}

	GameBackground(GameTheme theme, float density, boolean emblem) {
		this.emblem = emblem;
		this.t = theme;
		this.density = density;
		Random r = new Random(2000 + theme.pattern);
		int n;
		switch (theme.pattern) {
			case GameTheme.BG_SLASHES: n = 18; break;   // scratches: x, y, length, alpha
			case GameTheme.BG_EMBERS: n = 10; break;    // flames: x, width, height, alpha
			case GameTheme.BG_PENDULUM: n = 14; break;  // sparkles: x, y, size, alpha
			default: n = 15; break;                     // towers: x, width, height, alpha
		}
		items = new float[n * 4];
		for (int i = 0; i < n; i++) {
			items[i * 4] = theme.pattern == GameTheme.BG_SLASHES || theme.pattern == GameTheme.BG_PENDULUM ? r.nextFloat() : (i + 0.5f) / n + (r.nextFloat() - 0.5f) * 0.04f;
			items[i * 4 + 1] = r.nextFloat();
			items[i * 4 + 2] = r.nextFloat();
			items[i * 4 + 3] = r.nextFloat();
		}
		int m = theme.pattern == GameTheme.BG_EMBERS ? 64 : theme.pattern == GameTheme.BG_SLASHES ? 0 : theme.pattern == GameTheme.BG_TOWERS ? 70 : theme.pattern == GameTheme.BG_PENDULUM ? 46 : 40;
		dots = new float[m * 4];   // x, y, radius (dp), alpha
		for (int i = 0; i < m; i++) {
			dots[i * 4] = r.nextFloat();
			float y = r.nextFloat();
			// Embers gather near the fire; specks of light stay in the upper part.
			dots[i * 4 + 1] = theme.pattern == GameTheme.BG_EMBERS ? 0.3f + 0.7f * (float) Math.sqrt(y) : y * 0.8f;
			dots[i * 4 + 2] = 0.5f + r.nextFloat() * 1.2f;
			dots[i * 4 + 3] = 0.08f + r.nextFloat() * r.nextFloat() * 0.5f;
		}
		dot.setColor(theme.hi);
		line.setStyle(Paint.Style.STROKE);
	}

	private void shaders(int w, int h) {
		if (w == lastW && h == lastH) {
			return;
		}
		lastW = w;
		lastH = h;
		fill.setShader(new LinearGradient(0, 0, 0, h, t.sky, new float[] { 0f, 0.35f, 0.7f, 1f }, Shader.TileMode.CLAMP));
		float radius = Math.max(w, h) * 0.7f;
		glow.setShader(new RadialGradient(w * 0.5f, -h * 0.08f, radius,
			new int[] { GameTheme.alpha(t.skyGlow, 0x66), GameTheme.alpha(t.skyGlow, 0x22), 0x00000000 },
			new float[] { 0f, 0.5f, 1f }, Shader.TileMode.CLAMP));
		switch (t.pattern) {
			case GameTheme.BG_TOWERS: {
				float base = h * 0.985f;
				shaders = new Shader[items.length / 4 + 1];
				for (int i = 0; i < items.length; i += 4) {
					float th = towerHeight(i) * h;
					int a = towerAlpha(i);
					shaders[i / 4] = new LinearGradient(0, base - th, 0, base,
						GameTheme.alpha(t.inkHi, a), GameTheme.alpha(t.ink, a / 4), Shader.TileMode.CLAMP);
				}
				shaders[shaders.length - 1] = new LinearGradient(0, 0, w, 0,
					new int[] { 0x00000000, GameTheme.alpha(t.inkHi, 0x33), 0x00000000 }, null, Shader.TileMode.CLAMP);
				break;
			}
			case GameTheme.BG_SLASHES:
				// The slashes fade towards the bottom, where the screen is busiest.
				shaders = new Shader[] {
					new LinearGradient(0, 0, 0, h, GameTheme.alpha(t.ink, 0x48), GameTheme.alpha(t.ink, 0x0C), Shader.TileMode.CLAMP),
					new LinearGradient(0, 0, 0, h, GameTheme.alpha(t.inkHi, 0x80), GameTheme.alpha(t.ink, 0x18), Shader.TileMode.CLAMP),
					new LinearGradient(0, 0, 0, h, GameTheme.alpha(t.ink, 0x20), GameTheme.alpha(t.ink, 0x06), Shader.TileMode.CLAMP),
				};
				break;
			case GameTheme.BG_RINGS:
			case GameTheme.BG_PENDULUM:
				shaders = new Shader[0];
				break;
			case GameTheme.BG_EMBERS: {
				shaders = new Shader[items.length / 4 + 1];
				shaders[0] = new LinearGradient(0, h, 0, h * 0.5f,
					new int[] { GameTheme.alpha(t.ink, 0x70), GameTheme.alpha(t.ink, 0x20), 0x00000000 },
					new float[] { 0f, 0.45f, 1f }, Shader.TileMode.CLAMP);
				for (int i = 0; i < items.length; i += 4) {
					float fh = (0.10f + items[i + 2] * 0.22f) * h;
					boolean inner = (i / 4) % 2 == 1;
					shaders[i / 4 + 1] = new LinearGradient(0, h, 0, h - fh,
						GameTheme.alpha(inner ? t.inkHi : t.ink, inner ? 0x50 : 0x40), 0x00000000, Shader.TileMode.CLAMP);
				}
				break;
			}
			case GameTheme.BG_RAYS: {
				float cx = w * 0.5f;
				float cy = -h * 0.06f;
				float far = (float) Math.hypot(w, h) * 1.1f;
				shaders = new Shader[] {
					new RadialGradient(cx, cy, far,
						new int[] { GameTheme.alpha(t.inkHi, 0x34), GameTheme.alpha(t.ink, 0x16), 0x00000000 },
						new float[] { 0f, 0.4f, 0.85f }, Shader.TileMode.CLAMP),
					new RadialGradient(cx, cy, w * 0.55f,
						new int[] { GameTheme.alpha(t.skyGlow, 0x60), GameTheme.alpha(t.skyGlow, 0x18), 0x00000000 },
						new float[] { 0f, 0.5f, 1f }, Shader.TileMode.CLAMP),
				};
				path.reset();
				int wedges = 28;
				for (int i = 0; i < wedges; i += 2) {
					double a0 = Math.PI * 2 * i / wedges;
					double a1 = Math.PI * 2 * (i + 1) / wedges;
					path.moveTo(cx, cy);
					path.lineTo(cx + (float) Math.cos(a0) * far, cy + (float) Math.sin(a0) * far);
					path.lineTo(cx + (float) Math.cos(a1) * far, cy + (float) Math.sin(a1) * far);
					path.close();
				}
				break;
			}
			default:
				shaders = new Shader[0];
				break;
		}
	}

	private float towerHeight(int i) {
		return 0.06f + items[i + 2] * items[i + 3] * 0.3f;
	}

	private int towerAlpha(int i) {
		return (int) ((0.35f + (items[i + 1] + items[i + 2]) * 0.325f) * 46);
	}

	@Override
	public void draw(Canvas canvas) {
		Rect b = getBounds();
		int w = b.width();
		int h = b.height();
		if (w <= 0 || h <= 0) {
			return;
		}
		if (t.pattern == GameTheme.BG_PENDULUM) {
			drawCached(canvas, b, w, h);
			return;
		}
		shaders(w, h);
		canvas.save();
		canvas.translate(b.left, b.top);
		canvas.drawRect(0, 0, w, h, fill);
		canvas.drawRect(0, 0, w, h, glow);
		switch (t.pattern) {
			case GameTheme.BG_SLASHES: drawSlashes(canvas, w, h); break;
			case GameTheme.BG_RINGS: drawRings(canvas, w, h); break;
			case GameTheme.BG_EMBERS: drawEmbers(canvas, w, h); break;
			case GameTheme.BG_RAYS: drawRays(canvas, w, h); break;
			default: drawTowers(canvas, w, h); break;
		}
		drawDots(canvas, w, h);
		canvas.restore();
	}

	private void drawDots(Canvas canvas, int w, int h) {
		for (int i = 0; i < dots.length; i += 4) {
			dot.setColor(t.pattern == GameTheme.BG_EMBERS && (i / 4) % 3 != 0 ? t.inkHi : t.hi);
			dot.setAlpha((int) (Math.min(1f, dots[i + 3] * (t.pattern == GameTheme.BG_EMBERS ? 1.6f : 1f)) * 255));
			canvas.drawCircle(dots[i] * w, dots[i + 1] * h, dots[i + 2] * density, dot);
		}
	}

	/** The pendulum picture from its bitmap (drawn again only when the size changes). */
	private void drawCached(Canvas canvas, Rect b, int w, int h) {
		if (cache == null || cache.getWidth() != w || cache.getHeight() != h) {
			if (cache != null) {
				cache.recycle();
				cache = null;
			}
			try {
				cache = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
				paintPendulumScene(new Canvas(cache), w, h);
			} catch (OutOfMemoryError e) {
				cache = null;
			}
		}
		if (cache != null) {
			canvas.drawBitmap(cache, b.left, b.top, null);
		} else {
			// No memory for the bitmap: draw it every time.
			canvas.save();
			canvas.translate(b.left, b.top);
			paintPendulumScene(canvas, w, h);
			canvas.restore();
		}
	}

	private void paintPendulumScene(Canvas c, int w, int h) {
		if (emblem) {
			drawEmblem(c, w, h);
			return;
		}
		shaders(w, h);
		c.drawRect(0, 0, w, h, fill);
		c.drawRect(0, 0, w, h, glow);
		drawField(c, w, h);
		drawDots(c, w, h);
	}

	private static void hexagon(Path p, float cx, float cy, float r) {
		for (int i = 0; i < 6; i++) {
			double a = Math.PI / 3 * i;
			float x = cx + (float) Math.cos(a) * r;
			float y = cy + (float) Math.sin(a) * r;
			if (i == 0) {
				p.moveTo(x, y);
			} else {
				p.lineTo(x, y);
			}
		}
		p.close();
	}

	/** A rod from the pivot (above the emblem) to the crystal: clear until the top edge, then fading in. */
	private static Shader rodFade(float px, float py, float ex, float ey, int color) {
		float edge = py < 0 && ey > py ? Math.min(0.85f, -py / (ey - py)) : 0f;
		return new LinearGradient(px, py, ex, ey, new int[] { 0x00000000, 0x00000000, color },
			new float[] { 0f, edge, 1f }, Shader.TileMode.CLAMP);
	}

	/** A four-pointed sparkle. */
	private void sparkle(Canvas c, Paint p, float x, float y, float size, int color) {
		float d = density;
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(Math.max(1f, 0.9f * d));
		p.setColor(color);
		c.drawLine(x - size, y, x + size, y, p);
		c.drawLine(x, y - size * 1.3f, x, y + size * 1.3f, p);
		p.setColor(GameTheme.alpha(color, Math.max(0, (color >>> 24) / 2)));
		float s2 = size * 0.4f;
		c.drawLine(x - s2, y - s2, x + s2, y + s2, p);
		c.drawLine(x - s2, y + s2, x + s2, y - s2, p);
		p.setStyle(Paint.Style.FILL);
		p.setColor(GameTheme.alpha(0xFFFFFF, Math.min(255, (color >>> 24) + 0x30)));
		c.drawCircle(x, y, Math.max(1f, 1.1f * d), p);
	}

	/** The pendulum's crystal, its tip at (0, 0) and pointing down (+y), in dp-scaled units. */
	private void crystal(Canvas c, Paint p, boolean main, int alpha) {
		float d = density;
		Path cr = new Path();
		cr.moveTo(0, 6.4f * d);
		cr.lineTo(11 * d, 24 * d);
		cr.lineTo(0, 52 * d);
		cr.lineTo(-11 * d, 24 * d);
		cr.close();
		if (main) {
			p.setStyle(Paint.Style.FILL);
			p.setAlpha(255);   // a color set before would fade the gradient
			p.setShader(new LinearGradient(0, 6.4f * d, 0, 52 * d,
				new int[] { GameTheme.alpha(t.hi, 0xF5), GameTheme.alpha(t.accent, 0xE6), 0xF0104A3C },
				new float[] { 0f, 0.45f, 1f }, Shader.TileMode.CLAMP));
			c.drawPath(cr, p);
			p.setShader(null);
			// Facets.
			p.setStyle(Paint.Style.STROKE);
			p.setStrokeWidth(Math.max(1f, 0.8f * d));
			p.setColor(GameTheme.alpha(0xFFFFFF, 0x66));
			c.drawLine(0, 6.4f * d, -4 * d, 28 * d, p);
			c.drawLine(-4 * d, 28 * d, 0, 52 * d, p);
			c.drawLine(0, 6.4f * d, 4 * d, 28 * d, p);
			c.drawLine(4 * d, 28 * d, 0, 52 * d, p);
			c.drawLine(-11 * d, 24 * d, -4 * d, 28 * d, p);
			c.drawLine(11 * d, 24 * d, 4 * d, 28 * d, p);
		}
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(Math.max(1f, (main ? 1.2f : 0.9f) * d));
		p.setColor(GameTheme.alpha(t.hi, alpha));
		c.drawPath(cr, p);
		c.drawCircle(0, 3.2f * d, 3.2f * d, p);
	}

	/**
	 * Yu-Gi-Oh! ARC-V's background (into the cache; allocations are fine, it runs once): the
	 * holographic field, cards, the scales' pillars and sparkles. The pendulum is the emblem.
	 */
	private void drawField(Canvas c, int w, int h) {
		float d = density;
		Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
		Random rnd = new Random(77);

		// 1. The holographic field: hexagons over the bottom, fading upwards, a few of them lit.
		float fieldTop = h * 0.58f;
		float hr = 15 * d;
		float hx = hr * 1.5f;
		float hy = (float) (Math.sqrt(3) * hr);
		Path grid = new Path();
		p.setStyle(Paint.Style.FILL);
		for (int col = 0; col * hx < w + hr; col++) {
			float cx = col * hx;
			for (float cy = fieldTop - hy + (col % 2) * hy / 2; cy < h + hy; cy += hy) {
				hexagon(grid, cx, cy, hr);
				if (cy > fieldTop + hy && rnd.nextFloat() < 0.07f) {
					Path one = new Path();
					hexagon(one, cx, cy, hr * 0.86f);
					float k = (cy - fieldTop) / (h - fieldTop);
					p.setColor(GameTheme.alpha(rnd.nextBoolean() ? t.ink : t.ink3, (int) (0x0C + 0x26 * k)));
					c.drawPath(one, p);
				}
			}
		}
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(Math.max(1f, 0.8f * d));
		p.setAlpha(255);   // a color set before would fade the gradient
		p.setShader(new LinearGradient(0, fieldTop, 0, h,
			new int[] { 0x00000000, GameTheme.alpha(t.ink, 0x2C), GameTheme.alpha(t.ink, 0x50) },
			new float[] { 0f, 0.45f, 1f }, Shader.TileMode.CLAMP));
		c.drawPath(grid, p);
		p.setStyle(Paint.Style.FILL);
		p.setAlpha(255);   // a color set before would fade the gradient
		p.setShader(new LinearGradient(0, 0, w, 0,
			new int[] { 0x00000000, GameTheme.alpha(t.inkHi, 0x60), 0x00000000 }, null, Shader.TileMode.CLAMP));
		c.drawRect(0, fieldTop, w, fieldTop + Math.max(1f, d), p);
		p.setShader(null);

		// 2. Cards floating over the field (plain outlines: a frame, a picture box, lines of text).
		float cw = 46 * d;
		float ch = 66 * d;
		float[][] cards = { { 0.15f, 0.70f, -14f, 0.95f }, { 0.87f, 0.77f, 11f, 0.8f }, { 0.55f, 0.93f, -5f, 1.1f } };
		for (float[] cd : cards) {
			c.save();
			c.translate(cd[0] * w, cd[1] * h);
			c.rotate(cd[2]);
			c.scale(cd[3], cd[3]);
			RectF o = new RectF(-cw / 2, -ch / 2, cw / 2, ch / 2);
			p.setStyle(Paint.Style.FILL);
			p.setColor(GameTheme.alpha(t.inset, 0xA0));
			c.drawRoundRect(o, 3 * d, 3 * d, p);
			p.setStyle(Paint.Style.STROKE);
			p.setStrokeWidth(Math.max(1f, d));
			p.setColor(GameTheme.alpha(t.inkHi, 0x48));
			c.drawRoundRect(o, 3 * d, 3 * d, p);
			float ax = -cw / 2 + 5 * d;
			float ay = -ch / 2 + 11 * d;
			p.setColor(GameTheme.alpha(t.ink, 0x38));
			c.drawRect(ax, ay, cw / 2 - 5 * d, ay + cw - 10 * d, p);
			p.setColor(GameTheme.alpha(t.inkHi, 0x38));
			c.drawLine(ax, -ch / 2 + 6 * d, cw / 2 - 13 * d, -ch / 2 + 6 * d, p);
			for (int k = 0; k < 2; k++) {
				float ly = ay + cw - 10 * d + (4.5f + k * 4) * d;
				c.drawLine(ax, ly, cw / 2 - (k == 1 ? 16 : 5) * d, ly, p);
			}
			c.restore();
		}

		// 3. The pillars of the pendulum scales, one on each side, with marks like a scale.
		float pTop = h * 0.11f;
		float pBot = h * 0.60f;
		for (int side = 0; side < 2; side++) {
			float x = side == 0 ? w * 0.035f : w * 0.965f;
			p.setStyle(Paint.Style.FILL);
			p.setAlpha(255);   // a color set before would fade the gradient
			p.setShader(new LinearGradient(0, pTop, 0, pBot,
				new int[] { 0x00000000, GameTheme.alpha(t.ink3, 0x48), GameTheme.alpha(t.ink3, 0x40), 0x00000000 },
				new float[] { 0f, 0.18f, 0.8f, 1f }, Shader.TileMode.CLAMP));
			c.drawRect(x - 6 * d, pTop, x + 6 * d, pBot, p);
			p.setAlpha(255);   // a color set before would fade the gradient
			p.setShader(new LinearGradient(0, pTop, 0, pBot,
				new int[] { 0x00000000, 0xD0DDEEFF, 0x90BFE0FF, 0x00000000 },
				new float[] { 0f, 0.15f, 0.85f, 1f }, Shader.TileMode.CLAMP));
			c.drawRect(x - 0.75f * d, pTop, x + 0.75f * d, pBot, p);
			p.setShader(null);
			p.setStyle(Paint.Style.STROKE);
			p.setStrokeWidth(Math.max(1f, 0.8f * d));
			p.setColor(GameTheme.alpha(t.ink3, 0x80));
			for (int k = 1; k < 8; k++) {
				float y = pTop + (pBot - pTop) * k / 8f;
				float len = (k % 2 == 0 ? 7 : 4) * d;
				c.drawLine(x - len, y, x + len, y, p);
			}
			Path cap = new Path();
			float cy = pTop - 7 * d;
			cap.moveTo(x, cy - 7 * d);
			cap.lineTo(x + 5 * d, cy);
			cap.lineTo(x, cy + 7 * d);
			cap.lineTo(x - 5 * d, cy);
			cap.close();
			p.setStyle(Paint.Style.FILL);
			p.setColor(GameTheme.alpha(t.ink3, 0x50));
			c.drawPath(cap, p);
			p.setStyle(Paint.Style.STROKE);
			p.setColor(GameTheme.alpha(t.ink3, 0xD0));
			c.drawPath(cap, p);
		}

		// 4. Sparkles in the four colors.
		int[] colors = { t.hi, t.ink2, t.spectrum[1], t.ink3 };
		for (int i = 0; i < items.length; i += 4) {
			float x = items[i] * w;
			float y = (0.06f + items[i + 1] * 0.9f) * h;
			float size = (3 + items[i + 2] * 5) * d;
			int alpha = (int) (0x50 + items[i + 3] * 0x80);
			sparkle(c, p, x, y, size, GameTheme.alpha(colors[(i / 4) % colors.length], alpha));
		}
	}

	/** PS2: towers of light standing on a faint horizon. */
	private void drawTowers(Canvas canvas, int w, int h) {
		float base = h * 0.985f;
		extra.setColor(t.hi);
		for (int i = 0; i < items.length; i += 4) {
			float x = items[i] * w;
			float tw = Math.max(2f * density, (0.012f + items[i + 1] * 0.03f) * w);
			float th = towerHeight(i) * h;
			shape.setShader(shaders[i / 4]);
			canvas.drawRect(x - tw / 2, base - th, x + tw / 2, base, shape);
			extra.setAlpha(Math.min(255, towerAlpha(i) * 2));
			canvas.drawRect(x - tw / 2, base - th, x + tw / 2, base - th + Math.max(1f, density * 0.8f), extra);
		}
		shape.setShader(shaders[shaders.length - 1]);
		canvas.drawRect(0, base, w, base + Math.max(1f, density), shape);
		shape.setShader(null);
	}

	/** One slash: a band slanted like "/", centered at x (fraction of the width) halfway down. */
	private void slash(Canvas canvas, float cx, float width, float dx, int h, Paint p) {
		path.reset();
		path.moveTo(cx - width / 2 + dx / 2, 0);
		path.lineTo(cx + width / 2 + dx / 2, 0);
		path.lineTo(cx + width / 2 - dx / 2, h);
		path.lineTo(cx - width / 2 - dx / 2, h);
		path.close();
		canvas.drawPath(path, p);
	}

	/** Guilty Gear: big diagonal slashes, a few thin lines along them and short scratches. */
	private void drawSlashes(Canvas canvas, int w, int h) {
		float dx = Math.min(h * 0.32f, w * 0.9f);   // how far the slashes lean over the screen
		shape.setShader(shaders[0]);
		slash(canvas, w * 0.80f, w * 0.30f, dx, h, shape);
		shape.setShader(shaders[1]);
		slash(canvas, w * 1.03f, w * 0.05f, dx, h, shape);
		shape.setShader(shaders[2]);
		slash(canvas, w * 0.10f, w * 0.11f, dx, h, shape);
		shape.setShader(null);
		line.setStrokeWidth(Math.max(1f, density * 0.75f));
		float[] lines = { 0.61f, 0.585f, 0.985f, 0.235f };
		int[] alphas = { 0x50, 0x28, 0x60, 0x30 };
		for (int i = 0; i < lines.length; i++) {
			line.setColor(GameTheme.alpha(t.inkHi, alphas[i]));
			float x = w * lines[i];
			canvas.drawLine(x + dx / 2, 0, x - dx / 2, h, line);
		}
		// Scratches: short strokes leaning the same way, like cuts in the dark.
		float slope = dx / h;
		for (int i = 0; i < items.length; i += 4) {
			float x = items[i] * w;
			float y = items[i + 1] * h * 0.9f;
			float len = (0.02f + items[i + 2] * 0.06f) * h;
			line.setColor(GameTheme.alpha(i % 8 == 0 ? t.hi : t.inkHi, (int) (0x14 + items[i + 3] * 0x30)));
			canvas.drawLine(x + len * slope / 2, y - len / 2, x - len * slope / 2, y + len / 2, line);
		}
	}

	/** BlazBlue: the rings of a magic seal in a corner, with ticks, a dashed ring and a star. */
	private void drawRings(Canvas canvas, int w, int h) {
		float m = Math.min(w, h);
		float s = Math.max(1f, density * 0.75f);
		line.setStrokeWidth(s);
		float cx = w * 0.86f;
		float cy = h * 0.10f;
		float[] radii = { 0.16f, 0.24f, 0.255f, 0.42f, 0.58f, 0.76f, 0.98f };
		int[] alphas = { 0x58, 0x48, 0x30, 0x40, 0x2C, 0x22, 0x16 };
		for (int i = 0; i < radii.length; i++) {
			line.setColor(GameTheme.alpha(i % 3 == 0 ? t.inkHi : t.ink, alphas[i]));
			if (dash == null) {
				dash = new DashPathEffect(new float[] { 6 * density, 7 * density }, 0);
			}
			line.setPathEffect(i == 4 ? dash : null);
			canvas.drawCircle(cx, cy, m * radii[i], line);
		}
		line.setPathEffect(null);
		// Ticks on one ring, like a dial: every 5°, longer every 30°.
		float r = m * 0.42f;
		for (int i = 0; i < 72; i++) {
			double a = Math.PI * 2 * i / 72;
			float len = (i % 6 == 0 ? 9 : 4) * density;
			line.setColor(GameTheme.alpha(t.inkHi, i % 6 == 0 ? 0x60 : 0x34));
			float c = (float) Math.cos(a);
			float sn = (float) Math.sin(a);
			canvas.drawLine(cx + c * r, cy + sn * r, cx + c * (r + len), cy + sn * (r + len), line);
		}
		// A six-pointed star inside the inner rings.
		float sr = m * 0.24f;
		line.setColor(GameTheme.alpha(t.inkHi, 0x3C));
		for (int k = 0; k < 2; k++) {
			path.reset();
			for (int i = 0; i < 3; i++) {
				double a = -Math.PI / 2 + k * Math.PI + Math.PI * 2 * i / 3;
				float x = cx + (float) Math.cos(a) * sr;
				float y = cy + (float) Math.sin(a) * sr;
				if (i == 0) {
					path.moveTo(x, y);
				} else {
					path.lineTo(x, y);
				}
			}
			path.close();
			canvas.drawPath(path, line);
		}
		// A second, fainter seal lower on the other side.
		float bx = w * 0.06f;
		float by = h * 0.80f;
		float[] radii2 = { 0.20f, 0.29f, 0.30f, 0.48f };
		for (int i = 0; i < radii2.length; i++) {
			line.setColor(GameTheme.alpha(t.ink, 0x22 - i * 3));
			canvas.drawCircle(bx, by, m * radii2[i], line);
		}
	}

	/** KOF: fire glowing from below, flames standing on the bottom edge (embers are the dots). */
	private void drawEmbers(Canvas canvas, int w, int h) {
		shape.setShader(shaders[0]);
		canvas.drawRect(0, h * 0.5f, w, h, shape);
		for (int i = 0; i < items.length; i += 4) {
			float x = items[i] * w;
			float fw = (0.08f + items[i + 1] * 0.16f) * w;
			float fh = (0.10f + items[i + 2] * 0.22f) * h;
			shape.setShader(shaders[i / 4 + 1]);
			canvas.drawOval(x - fw / 2, h - fh, x + fw / 2, h + fh * 0.35f, shape);
		}
		shape.setShader(null);
		// Short streaks above some embers: they're rising.
		line.setStrokeWidth(Math.max(1f, density * 0.75f));
		for (int i = 0; i < dots.length; i += 16) {
			float x = dots[i] * w;
			float y = dots[i + 1] * h;
			line.setColor(GameTheme.alpha(t.inkHi, (int) (dots[i + 3] * 160)));
			canvas.drawLine(x, y + 2 * density, x, y + (5 + dots[i + 2] * 5) * density, line);
		}
		// A thin line of heat along the bottom.
		extra.setColor(GameTheme.alpha(t.inkHi, 0x40));
		canvas.drawRect(0, h - Math.max(1f, density), w, h, extra);
	}

	/** Street Fighter Alpha: a sunburst rising from the top, with a warm glow at its center. */
	private void drawRays(Canvas canvas, int w, int h) {
		shape.setShader(shaders[0]);
		canvas.drawPath(path, shape);
		shape.setShader(shaders[1]);
		canvas.drawRect(0, 0, w, h * 0.6f, shape);
		shape.setShader(null);
	}

	/**
	 * Yu-Gi-Oh! ARC-V's emblem above the title, on a transparent background: a pendulum of light
	 * swinging over a rainbow arc with ticks, its trail and the ghosts of where it was.
	 */
	private void drawEmblem(Canvas c, int w, int h) {
		float d = density;
		Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
		// The arc the pendulum swings over: a sweep of colors, glowing, with ticks.
		// Sized to the emblem: the arc spans the width and its lowest point is at the bottom.
		float span = 44f;
		float len = w * 0.70f;
		float px = w * 0.5f;
		float py = h - 8 * d - len;
		float sx = (float) (len * Math.sin(Math.toRadians(span)));
		RectF oval = new RectF(px - len, py - len, px + len, py + len);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeCap(Paint.Cap.ROUND);
		p.setAlpha(255);   // a color set before would fade the gradient
		p.setShader(new LinearGradient(px - sx, 0, px + sx, 0, t.spectrum, null, Shader.TileMode.CLAMP));
		float[] widths = { 14, 5, 1.6f };
		int[] alphas = { 0x1C, 0x40, 0xE0 };
		for (int i = 0; i < widths.length; i++) {
			p.setStrokeWidth(widths[i] * d);
			p.setAlpha(alphas[i]);
			c.drawArc(oval, 90 - span, span * 2, false, p);
		}
		float in = 14 * d;
		RectF inner = new RectF(px - len + in, py - len + in, px + len - in, py + len - in);
		p.setStrokeWidth(Math.max(1f, d));
		p.setAlpha(0x58);
		p.setStrokeCap(Paint.Cap.BUTT);
		p.setPathEffect(new DashPathEffect(new float[] { 3 * d, 5 * d }, 0));
		c.drawArc(inner, 90 - span + 3, span * 2 - 6, false, p);
		p.setPathEffect(null);
		p.setShader(null);
		for (int k = 0; k <= 22; k++) {
			double a = Math.toRadians(-span + k * span * 2 / 22);
			boolean big = k % 11 == 0;
			float r0 = len + 3 * d;
			float r1 = len + (big ? 13 : k % 2 == 0 ? 8 : 5) * d;
			float sn = (float) Math.sin(a);
			float cs = (float) Math.cos(a);
			p.setStrokeWidth(Math.max(1f, (big ? 1.4f : 0.9f) * d));
			p.setColor(GameTheme.alpha(t.hi, big ? 0xB0 : 0x5C));
			c.drawLine(px + sn * r0, py + cs * r0, px + sn * r1, py + cs * r1, p);
		}

		// The pendulum: a glowing trail along its path, ghosts of where it was, and the
		// crystal, lit, swinging to the right.
		float rod = len - 64 * d;
		float now = 12f;
		float[] ghosts = { -20f, -10f, 1f };
		int[] ghostAlpha = { 0x16, 0x24, 0x36 };
		RectF path = new RectF(px - rod - 26 * d, py - rod - 26 * d, px + rod + 26 * d, py + rod + 26 * d);
		float gx0 = px + (float) Math.sin(Math.toRadians(ghosts[0])) * rod;
		float gx1 = px + (float) Math.sin(Math.toRadians(now)) * rod;
		p.setStrokeCap(Paint.Cap.ROUND);
		p.setAlpha(255);   // a color set before would fade the gradient
		p.setShader(new LinearGradient(gx0, 0, gx1, 0, 0x00000000, GameTheme.alpha(t.accent, 0x70), Shader.TileMode.CLAMP));
		p.setStrokeWidth(10 * d);
		c.drawArc(path, 90 - now, now - ghosts[0], false, p);
		p.setStrokeWidth(2 * d);
		p.setAlpha(255);   // a color set before would fade the gradient
		p.setShader(new LinearGradient(gx0, 0, gx1, 0, 0x00000000, GameTheme.alpha(t.hi, 0xA0), Shader.TileMode.CLAMP));
		c.drawArc(path, 90 - now, now - ghosts[0], false, p);
		p.setShader(null);
		p.setStrokeCap(Paint.Cap.BUTT);
		for (int i = 0; i < ghosts.length; i++) {
			double a = Math.toRadians(ghosts[i]);
			float ex = px + (float) Math.sin(a) * rod;
			float ey = py + (float) Math.cos(a) * rod;
			p.setStyle(Paint.Style.STROKE);
			p.setStrokeWidth(Math.max(1f, d));
			p.setAlpha(255);
			p.setShader(rodFade(px, py, ex, ey, GameTheme.alpha(t.hi, ghostAlpha[i])));
			c.drawLine(px, py, ex, ey, p);
			p.setShader(null);
			c.save();
			c.translate(ex, ey);
			c.rotate(-ghosts[i]);
			crystal(c, p, false, ghostAlpha[i]);
			c.restore();
		}
		double a = Math.toRadians(now);
		float ex = px + (float) Math.sin(a) * rod;
		float ey = py + (float) Math.cos(a) * rod;
		float gcx = ex + (float) Math.sin(a) * 28 * d;
		float gcy = ey + (float) Math.cos(a) * 28 * d;
		p.setStyle(Paint.Style.FILL);
		p.setAlpha(255);   // a color set before would fade the gradient
		p.setShader(new RadialGradient(gcx, gcy, 84 * d,
			new int[] { GameTheme.alpha(t.accent, 0x70), GameTheme.alpha(t.accent, 0x1C), 0x00000000 },
			new float[] { 0f, 0.42f, 1f }, Shader.TileMode.CLAMP));
		c.drawCircle(gcx, gcy, 84 * d, p);
		p.setShader(null);
		p.setStyle(Paint.Style.STROKE);
		p.setStrokeWidth(6 * d);
		p.setAlpha(255);
		p.setShader(rodFade(px, py, ex, ey, GameTheme.alpha(t.accent, 0x24)));
		c.drawLine(px, py, ex, ey, p);
		p.setStrokeWidth(Math.max(1f, 1.6f * d));
		p.setShader(rodFade(px, py, ex, ey, GameTheme.alpha(t.hi, 0xD0)));
		c.drawLine(px, py, ex, ey, p);
		p.setShader(null);
		c.save();
		c.translate(ex, ey);
		c.rotate(-now);
		crystal(c, p, true, 0xE0);
		sparkle(c, p, 13 * d, 14 * d, 6 * d, GameTheme.alpha(0xFFFFFF, 0xE0));
		c.restore();

		// A few sparkles around the crystal.
		sparkle(c, p, px - w * 0.33f, py + len * 0.80f, 4 * d, GameTheme.alpha(t.ink2, 0xB0));
		sparkle(c, p, px + w * 0.40f, py + len * 0.74f, 3.5f * d, GameTheme.alpha(t.ink3, 0xB0));
		sparkle(c, p, px - w * 0.12f, py + len * 0.93f, 3 * d, GameTheme.alpha(t.hi, 0x90));
	}

	@Override
	public void setAlpha(int alpha) {
		// Always opaque.
	}

	@Override
	public void setColorFilter(ColorFilter colorFilter) {
		// Not used.
	}

	@Override
	@SuppressWarnings("deprecation")
	public int getOpacity() {
		return emblem ? PixelFormat.TRANSLUCENT : PixelFormat.OPAQUE;
	}
}
