package org.ppsspp.ppsspp;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
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

	GameBackground(GameTheme theme, float density) {
		this.t = theme;
		this.density = density;
		Random r = new Random(2000 + theme.pattern);
		int n;
		switch (theme.pattern) {
			case GameTheme.BG_SLASHES: n = 18; break;   // scratches: x, y, length, alpha
			case GameTheme.BG_EMBERS: n = 10; break;    // flames: x, width, height, alpha
			default: n = 15; break;                     // towers: x, width, height, alpha
		}
		items = new float[n * 4];
		for (int i = 0; i < n; i++) {
			items[i * 4] = theme.pattern == GameTheme.BG_SLASHES ? r.nextFloat() : (i + 0.5f) / n + (r.nextFloat() - 0.5f) * 0.04f;
			items[i * 4 + 1] = r.nextFloat();
			items[i * 4 + 2] = r.nextFloat();
			items[i * 4 + 3] = r.nextFloat();
		}
		int m = theme.pattern == GameTheme.BG_EMBERS ? 64 : theme.pattern == GameTheme.BG_SLASHES ? 0 : theme.pattern == GameTheme.BG_TOWERS ? 70 : 40;
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
		for (int i = 0; i < dots.length; i += 4) {
			dot.setColor(t.pattern == GameTheme.BG_EMBERS && (i / 4) % 3 != 0 ? t.inkHi : t.hi);
			dot.setAlpha((int) (Math.min(1f, dots[i + 3] * (t.pattern == GameTheme.BG_EMBERS ? 1.6f : 1f)) * 255));
			canvas.drawCircle(dots[i] * w, dots[i + 1] * h, dots[i + 2] * density, dot);
		}
		canvas.restore();
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
		return PixelFormat.OPAQUE;
	}
}
