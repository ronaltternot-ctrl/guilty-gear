package org.ppsspp.ppsspp;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * A card of the home screen drawn as a duel's panel (themes with cardDetail): the usual rounded
 * box, plus a line of color along the top (the theme's two colors fading out) and thin brackets in
 * the four corners, like a hologram's frame.
 */
final class DuelFrame extends Drawable {
	private final GameTheme t;
	private final float d;
	private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint top = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint corner = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF r = new RectF();
	private final Path path = new Path();
	private int lastW;

	DuelFrame(GameTheme theme, float density, int fillColor, int strokeColor) {
		t = theme;
		d = density;
		fill.setColor(fillColor);
		stroke.setStyle(Paint.Style.STROKE);
		stroke.setStrokeWidth(Math.max(1f, 0.75f * d));
		stroke.setColor(strokeColor);
		corner.setStyle(Paint.Style.STROKE);
		corner.setStrokeWidth(Math.max(1f, 1.2f * d));
		corner.setStrokeCap(Paint.Cap.SQUARE);
	}

	@Override
	public void draw(Canvas canvas) {
		Rect b = getBounds();
		if (b.width() <= 0 || b.height() <= 0) {
			return;
		}
		float half = stroke.getStrokeWidth() / 2;
		r.set(b.left + half, b.top + half, b.right - half, b.bottom - half);
		float rad = t.cardRadius * d;
		canvas.drawRoundRect(r, rad, rad, fill);
		canvas.drawRoundRect(r, rad, rad, stroke);

		// The line of color on top, from the second color to the accent, fading to the right.
		if (lastW != b.width()) {
			lastW = b.width();
			top.setShader(new LinearGradient(b.left + rad, 0, b.right - rad, 0,
				new int[] { GameTheme.alpha(t.ink2, 0xE0), GameTheme.alpha(t.accent, 0xB0), GameTheme.alpha(t.accent, 0x00) },
				new float[] { 0f, 0.45f, 1f }, Shader.TileMode.CLAMP));
		}
		float lh = Math.max(1f, 1.6f * d);
		canvas.drawRect(b.left + rad, b.top, b.right - rad, b.top + lh, top);

		// Brackets in the corners.
		float in = 4 * d;
		float len = Math.min(10 * d, Math.min(b.width(), b.height()) / 4f);
		float l = b.left + in, tp = b.top + in + lh, rt = b.right - in, bt = b.bottom - in;
		corner.setColor(GameTheme.alpha(t.accent, 0x99));
		bracket(canvas, l, tp + len, l, tp, l + len, tp);
		corner.setColor(GameTheme.alpha(t.ink2, 0x99));
		bracket(canvas, rt - len, tp, rt, tp, rt, tp + len);
		corner.setColor(GameTheme.alpha(t.ink3, 0x80));
		bracket(canvas, l, bt - len, l, bt, l + len, bt);
		corner.setColor(GameTheme.alpha(t.accent, 0x80));
		bracket(canvas, rt - len, bt, rt, bt, rt, bt - len);
	}

	private void bracket(Canvas canvas, float x0, float y0, float x1, float y1, float x2, float y2) {
		path.reset();
		path.moveTo(x0, y0);
		path.lineTo(x1, y1);
		path.lineTo(x2, y2);
		canvas.drawPath(path, corner);
	}

	@Override
	public void setAlpha(int alpha) {
		// Not used.
	}

	@Override
	public void setColorFilter(ColorFilter colorFilter) {
		// Not used.
	}

	@Override
	@SuppressWarnings("deprecation")
	public int getOpacity() {
		return PixelFormat.TRANSLUCENT;
	}

	/** The small light before a section's title as a pendulum's crystal: a long diamond with facets. */
	static final class Crystal extends Drawable {
		private final int light, color;
		private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Path path = new Path();
		private int lastH;

		Crystal(int light, int color) {
			this.light = light;
			this.color = color;
			edge.setStyle(Paint.Style.STROKE);
			edge.setStrokeWidth(1f);
			edge.setColor(GameTheme.alpha(0xFFFFFF, 0x90));
		}

		@Override
		public void draw(Canvas canvas) {
			Rect b = getBounds();
			float w = b.width(), h = b.height();
			if (w <= 0 || h <= 0) {
				return;
			}
			if (lastH != b.height()) {
				lastH = b.height();
				fill.setShader(new LinearGradient(0, b.top, 0, b.bottom, light, color, Shader.TileMode.CLAMP));
			}
			float cx = b.left + w / 2, mid = b.top + h * 0.4f;
			path.reset();
			path.moveTo(cx, b.top);
			path.lineTo(b.right, mid);
			path.lineTo(cx, b.bottom);
			path.lineTo(b.left, mid);
			path.close();
			canvas.drawPath(path, fill);
			// One facet: the top half catches the light.
			canvas.drawLine(cx, b.top, cx, b.bottom, edge);
		}

		@Override
		public void setAlpha(int alpha) {
			fill.setAlpha(alpha);
			edge.setAlpha(alpha * 0x90 / 255);
			invalidateSelf();
		}

		@Override
		public void setColorFilter(ColorFilter colorFilter) {
			// Not used.
		}

		@Override
		@SuppressWarnings("deprecation")
		public int getOpacity() {
			return PixelFormat.TRANSLUCENT;
		}
	}
}
