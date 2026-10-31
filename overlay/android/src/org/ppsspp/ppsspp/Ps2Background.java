package org.ppsspp.ppsspp;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import java.util.Random;

/**
 * The launcher's background, after the PlayStation 2's menus: deep blue fading to black, a soft
 * glow from above, the faint light towers of the boot screen along the bottom and a few specks of
 * light. Static (it doesn't move with the page) and drawn with a handful of shapes, so scrolling
 * stays smooth.
 */
final class Ps2Background extends Drawable {
	private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint tower = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint horizon = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final float density;

	private float[] towers = new float[0];   // x, width, height, alpha (fractions of the bounds)
	private float[] dots = new float[0];     // x, y, radius (dp), alpha
	private int lastW, lastH;
	private Shader[] towerShaders = new Shader[0];

	Ps2Background(float density) {
		this.density = density;
		Random r = new Random(2000);   // fixed: the same picture every time
		int n = 15;
		towers = new float[n * 4];
		for (int i = 0; i < n; i++) {
			towers[i * 4] = (i + 0.5f) / n + (r.nextFloat() - 0.5f) * 0.04f;
			towers[i * 4 + 1] = 0.012f + r.nextFloat() * 0.03f;
			towers[i * 4 + 2] = 0.06f + r.nextFloat() * r.nextFloat() * 0.3f;
			towers[i * 4 + 3] = 0.35f + r.nextFloat() * 0.65f;
		}
		int m = 70;
		dots = new float[m * 4];
		for (int i = 0; i < m; i++) {
			dots[i * 4] = r.nextFloat();
			dots[i * 4 + 1] = r.nextFloat() * 0.8f;
			dots[i * 4 + 2] = 0.5f + r.nextFloat() * 1.2f;
			dots[i * 4 + 3] = 0.08f + r.nextFloat() * r.nextFloat() * 0.5f;
		}
		dot.setColor(0xFFCFE0FF);
		edge.setColor(0xFFBFD4FF);
	}

	private void shaders(int w, int h) {
		if (w == lastW && h == lastH) {
			return;
		}
		lastW = w;
		lastH = h;
		fill.setShader(new LinearGradient(0, 0, 0, h,
			new int[] { 0xFF0B1638, 0xFF060B1C, 0xFF020409, 0xFF000000 },
			new float[] { 0f, 0.35f, 0.7f, 1f }, Shader.TileMode.CLAMP));
		float radius = Math.max(w, h) * 0.7f;
		glow.setShader(new RadialGradient(w * 0.5f, -h * 0.08f, radius,
			new int[] { 0x663A63D0, 0x221B3A8C, 0x00000000 }, new float[] { 0f, 0.5f, 1f }, Shader.TileMode.CLAMP));
		horizon.setShader(new LinearGradient(0, 0, w, 0,
			new int[] { 0x00000000, 0x335B8CFF, 0x00000000 }, null, Shader.TileMode.CLAMP));
		float base = h * 0.985f;
		towerShaders = new Shader[towers.length / 4];
		for (int i = 0; i < towers.length; i += 4) {
			float th = towers[i + 2] * h;
			int a = (int) (towers[i + 3] * 46);
			towerShaders[i / 4] = new LinearGradient(0, base - th, 0, base,
				new int[] { (a << 24) | 0x9FBFFF, ((a / 4) << 24) | 0x3A63D0 }, null, Shader.TileMode.CLAMP);
		}
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

		// The towers of light, standing on a faint horizon.
		float base = h * 0.985f;
		for (int i = 0; i < towers.length; i += 4) {
			float x = towers[i] * w;
			float tw = Math.max(2f * density, towers[i + 1] * w);
			float th = towers[i + 2] * h;
			int a = (int) (towers[i + 3] * 46);
			tower.setShader(towerShaders[i / 4]);
			canvas.drawRect(x - tw / 2, base - th, x + tw / 2, base, tower);
			edge.setAlpha(Math.min(255, a * 2));
			canvas.drawRect(x - tw / 2, base - th, x + tw / 2, base - th + Math.max(1f, density * 0.8f), edge);
		}
		canvas.drawRect(0, base, w, base + Math.max(1f, density), horizon);

		for (int i = 0; i < dots.length; i += 4) {
			dot.setAlpha((int) (dots[i + 3] * 255));
			canvas.drawCircle(dots[i] * w, dots[i + 1] * h, dots[i + 2] * density, dot);
		}
		canvas.restore();
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
