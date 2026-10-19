package org.ppsspp.ppsspp;

import java.util.ArrayList;
import java.util.List;

/**
 * The "Móvil" button type: big round buttons grouped at the bottom right like the touch controls
 * of mobile fighting games, smaller shortcut buttons above them (two or three buttons at once),
 * and a big D-pad on the left. Each game gets its own buttons. Drawn by PPSSPP with dark filled
 * buttons and white labels (patches/0006).
 *
 * Sizes are worked out in dp for the real screen and given to PPSSPP as fractions of it.
 */
final class MobileLayout {
	private MobileLayout() {}

	// PSP buttons, in PPSSPP's CustomKeyData::g_customKeyList bit order.
	static final long SQUARE = 1L, TRIANGLE = 1L << 1, CIRCLE = 1L << 2, CROSS = 1L << 3, L = 1L << 10, R = 1L << 11;

	// Label images in PPSSPP's customKeyImages: its own letters, then patches/0002 and 0006.
	static final int IMG_A = 6, IMG_B = 7, IMG_C = 8, IMG_D_LETTER = 9;
	static final int IMG_P = 36, IMG_K = 37, IMG_S = 38, IMG_HS = 39, IMG_D = 40;
	static final int IMG_LP = 41, IMG_MP = 42, IMG_HP = 43, IMG_LK = 44, IMG_MK = 45, IMG_HK = 46;
	static final int IMG_RC = 47, IMG_BURST = 48, IMG_ROLL = 49, IMG_CD = 50, IMG_MAX = 51, IMG_VISM = 52;

	/** Size of PPSSPP's round button image at scale 1, in dp. */
	static final float ROUND_DP = 56f;
	/** PPSSPP's D-pad: arms at 50 dp × spacing from the center, arm image about 61 × 45 dp. */
	private static final float DPAD_RADIUS_DP = 50f;
	private static final float DPAD_ARM_DP = 61f;

	static final class Button {
		final String label;
		final long keys;
		final int image;
		final boolean shortcut;
		float x, y;       // center, fraction of the screen
		float scale;      // PPSSPP button scale
		float sizeDp;     // diameter

		Button(String label, long keys, int image, boolean shortcut) {
			this.label = label;
			this.keys = keys;
			this.image = image;
			this.shortcut = shortcut;
		}
	}

	static final class Layout {
		final List<Button> buttons = new ArrayList<>();
		float dpadX, dpadY, dpadScale, dpadSpacing, dpadSizeDp;
		float startX, startY, selectX, selectY;
		boolean showL, showR;
	}

	private static Button b(String label, long keys, int image) {
		return new Button(label, keys, image, false);
	}

	private static Button sc(String label, long keys, int image) {
		return new Button(label, keys, image, true);
	}

	/** Top row, bottom row and shortcuts of each game (left to right). */
	private static Button[][] rows(int kind) {
		switch (kind) {
			case GameInspector.KIND_BB:
				// □ A, △ B, ○ C, ✕ D (Drive). RC = A+B+C, Barrier Burst = A+B+C+D.
				return new Button[][] {
					{ b("D", CROSS, IMG_D_LETTER) },
					{ b("A", SQUARE, IMG_A), b("B", TRIANGLE, IMG_B), b("C", CIRCLE, IMG_C) },
					{ sc("RC", SQUARE | TRIANGLE | CIRCLE, IMG_RC), sc("BURST", SQUARE | TRIANGLE | CIRCLE | CROSS, IMG_BURST) },
				};
			case GameInspector.KIND_KOF:
				// Punches on top, kicks below: □ A, △ C / ✕ B, ○ D. Roll A+B, CD, MAX A+B+C.
				return new Button[][] {
					{ b("A", SQUARE, IMG_A), b("C", TRIANGLE, IMG_C) },
					{ b("B", CROSS, IMG_B), b("D", CIRCLE, IMG_D_LETTER) },
					{ sc("ROLL", SQUARE | CROSS, IMG_ROLL), sc("CD", TRIANGLE | CIRCLE, IMG_CD), sc("MAX", SQUARE | CROSS | TRIANGLE, IMG_MAX) },
				};
			case GameInspector.KIND_SF:
				// Capcom: punches on top, kicks below. V-ISM = MP+MK.
				return new Button[][] {
					{ b("LP", SQUARE, IMG_LP), b("MP", TRIANGLE, IMG_MP), b("HP", R, IMG_HP) },
					{ b("LK", CROSS, IMG_LK), b("MK", CIRCLE, IMG_MK), b("HK", L, IMG_HK) },
					{ sc("V-ISM", TRIANGLE | CIRCLE, IMG_VISM) },
				};
			default:
				// Guilty Gear: □ P, ✕ K, △ S below; ○ HS, R D above. RC = P+K+S, Burst = D+HS.
				return new Button[][] {
					{ b("HS", CIRCLE, IMG_HS), b("D", R, IMG_D) },
					{ b("P", SQUARE, IMG_P), b("K", CROSS, IMG_K), b("S", TRIANGLE, IMG_S) },
					{ sc("RC", SQUARE | CROSS | TRIANGLE, IMG_RC), sc("BURST", R | CIRCLE, IMG_BURST) },
				};
		}
	}

	/**
	 * @param wDp landscape screen width in dp
	 * @param hDp landscape screen height in dp
	 * @param sizePercent 85, 100 or 120
	 */
	static Layout build(int kind, float wDp, float hDp, int sizePercent) {
		Layout out = new Layout();
		final float f = Math.max(0.6f, Math.min(1.5f, sizePercent / 100f));
		final float d = Math.max(56f, Math.min(140f, 0.215f * hDp * f));   // main button diameter
		final float small = 0.62f * d;                                      // shortcut diameter
		final float m = 0.035f * hDp;                                       // margin
		final float step = 1.1f * d;                                        // between centers in a row
		final float yBottom = hDp - m - d / 2f;
		final float yTop = yBottom - 0.93f * d;
		final float yShort = yTop - d / 2f - 0.12f * d - small / 2f;

		Button[][] r = rows(kind);
		Button[] top = r[0], bottom = r[1], shortcuts = r[2];
		for (int i = 0; i < bottom.length; i++) {
			place(out, bottom[i], wDp - m - d / 2f - (bottom.length - 1 - i) * step, yBottom, d, wDp, hDp);
		}
		// The top row sits between the buttons below it, like the mobile games.
		for (int i = 0; i < top.length; i++) {
			place(out, top[i], wDp - m - d / 2f - step / 2f - (top.length - 1 - i) * step, yTop, d, wDp, hDp);
		}
		for (int i = 0; i < shortcuts.length; i++) {
			place(out, shortcuts[i], wDp - m - small / 2f - (shortcuts.length - 1 - i) * 1.18f * small, yShort, small, wDp, hDp);
		}

		// D-pad: about half the screen height wide.
		final float k = Math.max(0.9f, Math.min(1.8f, 0.5f * hDp * f / (2f * DPAD_RADIUS_DP + DPAD_ARM_DP)));
		out.dpadScale = k;
		out.dpadSpacing = k;
		out.dpadSizeDp = (2f * DPAD_RADIUS_DP + DPAD_ARM_DP) * k;
		out.dpadX = (m + out.dpadSizeDp / 2f + 0.02f * wDp) / wDp;
		out.dpadY = (hDp - m - out.dpadSizeDp / 2f) / hDp;

		// Start and Select at the bottom, in the middle.
		final float yStartSelect = (hDp - 0.055f * hDp) / hDp;
		out.selectX = 0.44f;
		out.startX = 0.56f;
		out.selectY = yStartSelect;
		out.startY = yStartSelect;

		// L (Respect in Guilty Gear) and R stay where they are when a game uses them on their own.
		out.showL = kind != GameInspector.KIND_SF;
		out.showR = kind == GameInspector.KIND_BB || kind == GameInspector.KIND_KOF;
		return out;
	}

	private static void place(Layout out, Button btn, float xDp, float yDp, float sizeDp, float wDp, float hDp) {
		btn.x = xDp / wDp;
		btn.y = yDp / hDp;
		btn.sizeDp = sizeDp;
		btn.scale = sizeDp / ROUND_DP;
		out.buttons.add(btn);
	}
}
