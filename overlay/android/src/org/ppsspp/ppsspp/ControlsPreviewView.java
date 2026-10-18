package org.ppsspp.ppsspp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/**
 * Small landscape "phone screen" showing where the touch buttons go, for the PSP layout
 * or the arcade (P K S HS D) layout. Arcade positions come from LauncherActivity so the
 * preview always matches what is written to the emulator config.
 */
final class ControlsPreviewView extends View {
	// Guilty Gear's usual button colors: P pink, K blue, S green, HS red, D orange.
	private static final int C_P = 0xFFFF6FA5;
	private static final int C_K = 0xFF4DA3FF;
	private static final int C_S = 0xFF4CD07D;
	private static final int C_HS = 0xFFFF5A4E;
	private static final int C_D = 0xFFFFB547;

	// PSP symbol colors.
	private static final int C_TRIANGLE = 0xFF3FD69B;
	private static final int C_CIRCLE = 0xFFFF5A64;
	private static final int C_CROSS = 0xFF6FA8FF;
	private static final int C_SQUARE = 0xFFF27FD0;

	private static final int C_SCREEN = 0xFF0A0A0E;
	private static final int C_GAME = 0xFF17171F;
	private static final int C_LINE = 0x99FFFFFF;
	private static final int C_FILL = 0x22FFFFFF;

	private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final RectF rect = new RectF();
	private final Path path = new Path();
	private boolean arcade;
	private int kind = GameInspector.KIND_GG;

	ControlsPreviewView(Context context) {
		super(context);
		stroke.setStyle(Paint.Style.STROKE);
		label.setTextAlign(Paint.Align.CENTER);
		label.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
		updateDescription();
	}

	void setArcade(boolean arcade) {
		if (this.arcade != arcade) {
			this.arcade = arcade;
			updateDescription();
			invalidate();
		}
	}

	/** BlazBlue's and KOF's arcade buttons are A, B, C and D, each game with its own layout. */
	void setKind(int kind) {
		if (this.kind != kind) {
			this.kind = kind;
			updateDescription();
			invalidate();
		}
	}

	private void updateDescription() {
		setContentDescription(arcade
			? (kind == GameInspector.KIND_BB ? "Vista previa: botones arcade A, B y C arriba, D abajo"
				: kind == GameInspector.KIND_KOF ? "Vista previa: botones Neo Geo A, B, C y D en arco"
				: kind == GameInspector.KIND_SF ? "Vista previa: botones Capcom LP, MP y HP arriba, LK, MK y HK abajo"
				: "Vista previa: botones arcade K, S y HS arriba, P y D abajo")
			: "Vista previa: botones de PSP triángulo, círculo, equis y cuadrado");
	}

	private float dp(float v) {
		return v * getResources().getDisplayMetrics().density;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int width = MeasureSpec.getSize(widthMeasureSpec);
		int height = (int) Math.min(width * 0.46f, dp(230));
		setMeasuredDimension(width, height);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		float pad = dp(1.5f);
		float h = getHeight() - 2 * pad;
		float w = Math.min(getWidth() - 2 * pad, h / 0.46f);
		float left = (getWidth() - w) / 2f;
		float top = pad;

		// Phone screen.
		rect.set(left, top, left + w, top + h);
		fill.setColor(C_SCREEN);
		canvas.drawRoundRect(rect, dp(14), dp(14), fill);
		stroke.setStrokeWidth(dp(1.5f));
		stroke.setColor(0xFF2E2E38);
		canvas.drawRoundRect(rect, dp(14), dp(14), stroke);

		// The game picture (PSP aspect ratio) in the middle.
		float gameH = h * 0.86f;
		float gameW = Math.min(gameH * 480f / 272f, w * 0.78f);
		rect.set(left + (w - gameW) / 2f, top + (h - gameH) / 2f, left + (w + gameW) / 2f, top + (h + gameH) / 2f);
		fill.setColor(C_GAME);
		canvas.drawRect(rect, fill);

		float r = w * 0.042f;
		stroke.setStrokeWidth(Math.max(dp(1.2f), r * 0.08f));

		// Pause button, top center.
		drawRound(canvas, left + w * 0.5f, top + h * 0.09f, r * 0.62f, C_LINE);
		label.setColor(C_LINE);
		label.setTextSize(r * 0.8f);
		drawCenteredText(canvas, "≡", left + w * 0.5f, top + h * 0.09f);

		// D-pad and analog stick on the left.
		drawDpad(canvas, left + w * 0.135f, top + h * 0.50f, w * 0.068f);
		drawRound(canvas, left + w * 0.135f, top + h * 0.83f, r * 0.95f, C_LINE);
		fill.setColor(0x55FFFFFF);
		canvas.drawCircle(left + w * 0.135f, top + h * 0.83f, r * 0.45f, fill);

		// Shoulder buttons. In Guilty Gear's arcade layout R (Dust) becomes the D button; in
		// Street Fighter's, L and R are the heavy kick and punch.
		boolean sfArcade = arcade && kind == GameInspector.KIND_SF;
		if (!sfArcade) {
			drawShoulder(canvas, left + w * 0.075f, top + h * 0.15f, r, "L");
		}
		if (!sfArcade && (!arcade || kind != GameInspector.KIND_GG)) {
			drawShoulder(canvas, left + w * 0.925f, top + h * 0.15f, r, "R");
		}

		// Select / Start.
		float pillY = top + h * (arcade ? 0.91f : 0.86f);
		drawPill(canvas, left + w * (arcade ? 0.44f : 0.50f), pillY, r, "SELECT");
		drawPill(canvas, left + w * (arcade ? 0.56f : 0.64f), pillY, r, "START");

		if (arcade) {
			boolean sf = kind == GameInspector.KIND_SF;
			boolean letters = kind == GameInspector.KIND_BB || kind == GameInspector.KIND_KOF;
			String[] names = sf ? new String[] { "LP", "MP", "HP", "LK", "MK", "HK" }
				: letters ? new String[] { "A", "B", "C", "D" } : new String[] { "K", "S", "HS", "P", "D" };
			int[] colors = sf ? new int[] { C_K, C_K, C_K, C_P, C_P, C_P }
				: kind == GameInspector.KIND_KOF ? new int[] { C_HS, C_D, C_S, C_K }
				: letters ? new int[] { C_P, C_S, C_HS, C_D } : new int[] { C_K, C_S, C_HS, C_P, C_D };
			float[][] positions = kind == GameInspector.KIND_BB ? LauncherActivity.BB_ARCADE_POSITIONS
				: kind == GameInspector.KIND_KOF ? LauncherActivity.KOF_ARCADE_POSITIONS
				: sf ? LauncherActivity.SF_ARCADE_POSITIONS : LauncherActivity.ARCADE_POSITIONS;
			for (int i = 0; i < names.length; i++) {
				float cx = left + w * positions[i][0];
				float cy = top + h * positions[i][1];
				drawRound(canvas, cx, cy, r, colors[i]);
				label.setColor(colors[i]);
				label.setTextSize(r * (names[i].length() > 1 ? 0.82f : 1.0f));
				drawCenteredText(canvas, names[i], cx, cy);
			}
		} else {
			float cx = left + w * 0.85f;
			float cy = top + h * 0.60f;
			float d = w * 0.07f;
			drawRound(canvas, cx, cy - d, r, C_LINE);
			drawRound(canvas, cx + d, cy, r, C_LINE);
			drawRound(canvas, cx, cy + d, r, C_LINE);
			drawRound(canvas, cx - d, cy, r, C_LINE);
			drawTriangle(canvas, cx, cy - d, r * 0.5f);
			drawCircleSymbol(canvas, cx + d, cy, r * 0.42f);
			drawCrossSymbol(canvas, cx, cy + d, r * 0.4f);
			drawSquareSymbol(canvas, cx - d, cy, r * 0.38f);
		}
	}

	private void drawRound(Canvas canvas, float cx, float cy, float radius, int outline) {
		fill.setColor(C_FILL);
		canvas.drawCircle(cx, cy, radius, fill);
		stroke.setColor(outline);
		canvas.drawCircle(cx, cy, radius, stroke);
	}

	private void drawDpad(Canvas canvas, float cx, float cy, float size) {
		float arm = size * 0.42f;
		path.reset();
		path.addRoundRect(new RectF(cx - arm, cy - size, cx + arm, cy + size), arm * 0.4f, arm * 0.4f, Path.Direction.CW);
		path.addRoundRect(new RectF(cx - size, cy - arm, cx + size, cy + arm), arm * 0.4f, arm * 0.4f, Path.Direction.CW);
		fill.setColor(C_FILL);
		canvas.drawPath(path, fill);
		stroke.setColor(C_LINE);
		canvas.drawRoundRect(new RectF(cx - arm, cy - size, cx + arm, cy + size), arm * 0.4f, arm * 0.4f, stroke);
		canvas.drawRoundRect(new RectF(cx - size, cy - arm, cx + size, cy + arm), arm * 0.4f, arm * 0.4f, stroke);
	}

	private void drawShoulder(Canvas canvas, float cx, float cy, float r, String text) {
		rect.set(cx - r * 1.3f, cy - r * 0.62f, cx + r * 1.3f, cy + r * 0.62f);
		fill.setColor(C_FILL);
		canvas.drawRoundRect(rect, r * 0.6f, r * 0.6f, fill);
		stroke.setColor(C_LINE);
		canvas.drawRoundRect(rect, r * 0.6f, r * 0.6f, stroke);
		label.setColor(C_LINE);
		label.setTextSize(r * 0.85f);
		drawCenteredText(canvas, text, cx, cy);
	}

	private void drawPill(Canvas canvas, float cx, float cy, float r, String text) {
		rect.set(cx - r * 1.25f, cy - r * 0.38f, cx + r * 1.25f, cy + r * 0.38f);
		fill.setColor(C_FILL);
		canvas.drawRoundRect(rect, r * 0.38f, r * 0.38f, fill);
		stroke.setColor(0x66FFFFFF);
		canvas.drawRoundRect(rect, r * 0.38f, r * 0.38f, stroke);
		label.setColor(0x99FFFFFF);
		label.setTextSize(r * 0.42f);
		drawCenteredText(canvas, text, cx, cy);
	}

	private void drawCenteredText(Canvas canvas, String text, float cx, float cy) {
		Paint.FontMetrics fm = label.getFontMetrics();
		canvas.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, label);
	}

	private void drawTriangle(Canvas canvas, float cx, float cy, float s) {
		path.reset();
		path.moveTo(cx, cy - s);
		path.lineTo(cx + s * 0.95f, cy + s * 0.65f);
		path.lineTo(cx - s * 0.95f, cy + s * 0.65f);
		path.close();
		stroke.setColor(C_TRIANGLE);
		canvas.drawPath(path, stroke);
	}

	private void drawCircleSymbol(Canvas canvas, float cx, float cy, float s) {
		stroke.setColor(C_CIRCLE);
		canvas.drawCircle(cx, cy, s, stroke);
	}

	private void drawCrossSymbol(Canvas canvas, float cx, float cy, float s) {
		stroke.setColor(C_CROSS);
		canvas.drawLine(cx - s, cy - s, cx + s, cy + s, stroke);
		canvas.drawLine(cx - s, cy + s, cx + s, cy - s, stroke);
	}

	private void drawSquareSymbol(Canvas canvas, float cx, float cy, float s) {
		stroke.setColor(C_SQUARE);
		canvas.drawRect(cx - s, cy - s, cx + s, cy + s, stroke);
	}
}
