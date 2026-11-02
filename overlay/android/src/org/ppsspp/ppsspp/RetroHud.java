package org.ppsspp.ppsspp;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.util.Locale;

/**
 * The custom HUD of the PSP games (overlay/UI/AcPlusHudCore.h) for the libretro games
 * (ArcadeActivity), drawn on an Android Canvas: the same tracker (smoothed health with the
 * damage trail, combos counted from the game or from the health drops) and the same four
 * designs (Neón, Arcade, Lateral, Datos). The values come from SnesHud.
 *
 * Differences with the PSP one: a game may have no power meter (Style.meter null) or no timer
 * read (Style.timer false); those parts are left out.
 */
final class RetroHud {
	private RetroHud() {}

	static final int DESIGN_OFF = 0, DESIGN_NEON = 1, DESIGN_ARCADE = 2, DESIGN_SIDE = 3, DESIGN_DATA = 4;
	static final String[] DESIGN_NAMES = { "apagado", "Neón", "Arcade", "Lateral", "Datos" };

	static final int TIMER_UNKNOWN = -2;

	/** What the game calls its gauges. */
	static final class Style {
		/** Power meter, on the bars ("SUPER"); null when the game has none. */
		final String meter;
		/** The same in the "Datos" design ("Súper"). */
		final String meterWord;
		/** The timer is read. */
		final boolean timer;

		Style(String meter, String meterWord, boolean timer) {
			this.meter = meter;
			this.meterWord = meterWord;
			this.timer = timer;
		}
	}

	static final class Snapshot {
		boolean valid;
		final int[] health = new int[2];
		final int[] healthMax = { 1, 1 };
		final float[] meter = new float[2];
		final int[] stocks = { -1, -1 };
		/** Seconds; -1 none; TIMER_UNKNOWN. */
		int timer = -1;
		final String[] name = { "", "" };
		/** Changes every frame while a fight runs. */
		int objectsHash;
		Style style;
	}

	// -------------------------------------------------------------------------------------------
	// Values smoothed over time for drawing.

	/** Hits closer than this (seconds) belong to the same combo. */
	private static final double HIT_WINDOW = 0.8;

	static final class PlayerView {
		float health = 1, healthMax = 1;
		/** Recent damage, drains after a moment. */
		float trail = 1;
		double trailHold;
		float meter;
		int stocks = -1;
		int comboHits, comboDamage;
		double comboChanged = -10, comboEnded = -10;
		boolean comboShown;
		double lastHit = -10;
		int lastComboHits, lastComboDamage;
		String name = "";
	}

	static final class Tracker {
		final PlayerView[] p = { new PlayerView(), new PlayerView() };
		int timer = -1;
		/** 0..1 fade. */
		float visible;
		double now;
		Style style = new Style(null, null, false);

		private int lastHash;
		private double lastChange = -100;
		private boolean started;
		private final int[] healthBefore = new int[2];

		void update(Snapshot s, double t) {
			final double dt = Math.min(0.1, Math.max(0.0, t - now));
			now = t;
			boolean live = s.valid;
			if (live) {
				if (s.objectsHash != lastHash) {
					lastHash = s.objectsHash;
					lastChange = t;
				}
				// Frozen fighters (menus, the game's pause) for a while: hide.
				live = t - lastChange < 1.5;
			}
			visible = (float) Math.max(0.0, Math.min(1.0, visible + (live ? dt / 0.25 : -dt / 0.25)));
			if (!s.valid) {
				return;
			}
			if (s.style != null) {
				style = s.style;
			}
			timer = s.timer;
			final float[] prevHealth = { p[0].health, p[1].health };
			for (int i = 0; i < 2; i++) {
				PlayerView v = p[i];
				v.name = s.name[i];
				v.healthMax = Math.max(1, s.healthMax[i]);
				final float hp = Math.min(s.health[i], s.healthMax[i]);
				if (!started || hp > v.health + 0.5f) {
					v.trail = hp;   // new round, or healed
				} else if (hp < v.health - 0.5f) {
					v.trail = Math.max(v.trail, v.health);
					v.trailHold = t + 0.7;
				}
				v.health = hp;
				if (t > v.trailHold && v.trail > v.health) {
					v.trail = Math.max(v.health, v.trail - (float) (dt * v.healthMax * 0.62));
				}
				v.meter = Math.max(0f, Math.min(1f, s.meter[i]));
				v.stocks = s.stocks[i];
			}
			if (!started) {
				healthBefore[0] = s.health[0];
				healthBefore[1] = s.health[1];
			}
			// Combos (player i hitting player j), from the health drops: every drop is a hit, and
			// hits close together make a combo.
			for (int i = 0; i < 2; i++) {
				PlayerView v = p[i];
				final int j = 1 - i;
				if (started && s.health[j] < prevHealth[j] - 0.5f) {
					if (v.comboHits > 0 && v.comboEnded < 0 && t - v.lastHit <= HIT_WINDOW) {
						v.comboHits++;
					} else {
						v.comboHits = 1;
						healthBefore[j] = (int) prevHealth[j];
					}
					v.lastHit = t;
					v.comboChanged = t;
					v.comboEnded = -1;
				} else if (v.comboHits > 0 && v.comboEnded < 0 && t - v.lastHit > HIT_WINDOW) {
					v.comboEnded = t;
				}
				if (!started || s.health[j] > prevHealth[j] + 0.5f) {
					// New round (or healed): whatever was running is over.
					healthBefore[j] = s.health[j];
					if (started && v.comboEnded < 0 && v.comboHits > 0) {
						v.comboHits = 0;
						v.comboEnded = -10;
					}
				}
				if (v.comboEnded < 0 && v.comboHits > 0) {
					v.comboDamage = Math.max(0, healthBefore[j] - s.health[j]);
					if (v.comboHits >= 2) {
						v.lastComboHits = v.comboHits;
						v.lastComboDamage = v.comboDamage;
					}
				}
				if (v.comboEnded >= 0 && t - v.comboEnded > 1.3) {
					v.comboHits = 0;
				}
				v.comboShown = v.comboHits >= 2 && (v.comboEnded < 0 || t - v.comboEnded <= 1.3);
			}
			started = true;
		}
	}

	// -------------------------------------------------------------------------------------------
	// Drawing

	/** Where the game picture is, and the whole screen (pixels). */
	static final class Frame {
		float gx, gy, gw, gh, sw, sh;
	}

	static final int ALIGN_L = -1, ALIGN_C = 0, ALIGN_R = 1;

	static int alpha(int c, float a) {
		int al = (int) Math.max(0f, Math.min(255f, ((c >>> 24) & 0xFF) * a));
		return (al << 24) | (c & 0x00FFFFFF);
	}

	static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int out = 0;
		for (int sh = 0; sh < 32; sh += 8) {
			float ca = (a >>> sh) & 0xFF, cb = (b >>> sh) & 0xFF;
			out |= ((int) (ca + (cb - ca) * t + 0.5f) & 0xFF) << sh;
		}
		return out;
	}

	/** The PSP HUD's primitives, straight on a Canvas. */
	static final class Painter {
		private final Canvas c;
		private final float opacity;
		private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Path path = new Path();

		Painter(Canvas c, float opacity, Typeface face) {
			this.c = c;
			this.opacity = opacity;
			p.setTypeface(face);
		}

		void rect(float x, float y, float w, float h, int col) {
			if (w <= 0 || h <= 0) {
				return;
			}
			p.setShader(null);
			p.setColor(alpha(col, opacity));
			c.drawRect(x, y, x + w, y + h, p);
		}

		void vgrad(float x, float y, float w, float h, int top, int bottom) {
			if (w <= 0 || h <= 0) {
				return;
			}
			p.setColor(0xFFFFFFFF);
			p.setShader(new LinearGradient(0, y, 0, y + h, alpha(top, opacity), alpha(bottom, opacity), Shader.TileMode.CLAMP));
			c.drawRect(x, y, x + w, y + h, p);
			p.setShader(null);
		}

		/** Parallelogram: top edge from x1 to x2 at y, bottom edge shifted by -skew. */
		void slant(float x1, float x2, float y, float h, float skew, int top, int bottom) {
			if (x2 - x1 <= 0.01f || h <= 0) {
				return;
			}
			path.reset();
			path.moveTo(x1, y);
			path.lineTo(x2, y);
			path.lineTo(x2 - skew, y + h);
			path.lineTo(x1 - skew, y + h);
			path.close();
			p.setColor(0xFFFFFFFF);
			p.setShader(new LinearGradient(0, y, 0, y + h, alpha(top, opacity), alpha(bottom, opacity), Shader.TileMode.CLAMP));
			c.drawPath(path, p);
			p.setShader(null);
		}

		/** Text of height size, anchored at x (align), centered on y. */
		void text(String s, float x, float y, float size, int col, int align, boolean shadow) {
			if (s == null || s.isEmpty() || size <= 0) {
				return;
			}
			p.setShader(null);
			p.setTextSize(size * 1.3f);
			p.setTextAlign(align == ALIGN_L ? Paint.Align.LEFT : align == ALIGN_R ? Paint.Align.RIGHT : Paint.Align.CENTER);
			Paint.FontMetrics fm = p.getFontMetrics();
			float base = y - (fm.ascent + fm.descent) / 2f;
			if (shadow) {
				p.setColor(alpha(0xCC000000, ((col >>> 24) & 0xFF) / 255f * opacity));
				c.drawText(s, x + size * 0.08f, base + size * 0.08f, p);
			}
			p.setColor(alpha(col, opacity));
			c.drawText(s, x, base, p);
		}

		void text(String s, float x, float y, float size, int col, int align) {
			text(s, x, y, size, col, align, true);
		}
	}

	private static String name(PlayerView v, int player) {
		return v.name == null || v.name.isEmpty() ? (player == 0 ? "1P" : "2P") : v.name;
	}

	private static String pct(float v, float max) {
		return (int) (v * 100f / max + 0.0001f) + "%";
	}

	private static String meterText(Style st, PlayerView v, boolean withLabel) {
		String out = withLabel ? st.meter + " " : "";
		out += pct(v.meter, 1f);
		if (v.stocks >= 0) {
			out += " ×" + v.stocks;
		}
		return out;
	}

	private static String timerText(int seconds) {
		return seconds < 0 ? "--" : String.valueOf(Math.min(99, seconds));
	}

	private static float pop(PlayerView v, double now) {
		double a = now - v.comboChanged;
		return a < 0.14 ? 1f + 0.35f * (float) (1.0 - a / 0.14) : 1f;
	}

	private static float comboFade(PlayerView v, double now) {
		if (v.comboEnded < 0) {
			return 1f;
		}
		return Math.max(0f, Math.min(1f, (float) (1.3 - (now - v.comboEnded)) / 0.3f));
	}

	static void draw(Canvas canvas, int design, Tracker tr, Frame f, float opacity, Typeface face) {
		if (tr.visible <= 0f || design == DESIGN_OFF) {
			return;
		}
		Painter d = new Painter(canvas, opacity * tr.visible, face);
		switch (design) {
			case DESIGN_NEON: drawNeon(d, tr, f); break;
			case DESIGN_ARCADE: drawArcade(d, tr, f); break;
			case DESIGN_SIDE: drawSide(d, tr, f); break;
			case DESIGN_DATA: drawData(d, tr, f); break;
			default: break;
		}
	}

	// Design 1: Neon. Slanted glowing bars, cyan for player 1 and pink for player 2.
	private static void drawNeon(Painter d, Tracker tr, Frame f) {
		final float u = f.gh / 272f;
		final float cx = f.gx + f.gw * 0.5f;
		final int[] side = { 0xFF22E5FF, 0xFFFF3EA5 };
		final int[] sideDark = { 0xFF0B6C8C, 0xFF8C1556 };
		final float barY = f.gy + 7 * u, barH = 10 * u, skew = 5 * u;
		final float outer = 12 * u, inner = 26 * u;
		final Style st = tr.style;
		// Dark shades over the top and bottom, so the game's own HUD stays in the background.
		d.vgrad(f.gx, f.gy, f.gw, 30 * u, 0xE6000000, 0xB0000000);
		d.vgrad(f.gx, f.gy + 30 * u, f.gw, 16 * u, 0xB0000000, 0x00000000);
		if (st.meter != null) {
			d.vgrad(f.gx, f.gy + f.gh - 40 * u, f.gw, 16 * u, 0x00000000, 0xB0000000);
			d.vgrad(f.gx, f.gy + f.gh - 24 * u, f.gw, 24 * u, 0xB0000000, 0xE6000000);
		}
		for (int i = 0; i < 2; i++) {
			final PlayerView v = tr.p[i];
			final float len = f.gw * 0.5f - outer - inner;
			final float hp = v.health / v.healthMax, trail = v.trail / v.healthMax;
			final boolean low = hp < 0.3f;
			final float pulse = low ? 0.5f + 0.5f * (float) Math.sin(tr.now * 9.0) : 0f;
			final int top = low ? mix(0xFFFFC23E, 0xFFFF4D4D, pulse) : mix(side[i], 0xFFFFFFFF, 0.25f);
			final int bottom = low ? 0xFFB3261E : sideDark[i];
			final int fi = i;
			// Outer edge to the left (P1) or right (P2); the bar recedes toward that edge.
			Span span = (from, to, ct, cb, grow) -> {
				float a = fi == 0 ? f.gx + outer + from * len : f.gx + f.gw - outer - to * len;
				float b = fi == 0 ? f.gx + outer + to * len : f.gx + f.gw - outer - from * len;
				float s = fi == 0 ? skew : -skew;
				d.slant(a - grow, b + grow, barY - grow, barH + grow * 2, s, ct, cb);
			};
			span.draw(0, 1, alpha(side[i], 0.35f), alpha(side[i], 0.15f), 1.6f * u);   // glow
			span.draw(0, 1, 0xE6070B18, 0xE6070B18, 0);
			span.draw(hp, trail, 0xF2FFFFFF, 0xF2C8D6FF, 0);
			span.draw(0, hp, top, bottom, 0);
			final float nameX = i == 0 ? f.gx + outer + 2 * u : f.gx + f.gw - outer - 2 * u;
			d.text(name(v, i), nameX, barY + barH + 8 * u, 9.5f * u, side[i], i == 0 ? ALIGN_L : ALIGN_R);

			if (st.meter != null) {
				// Meter at the bottom, with marks every 25%.
				final float tw = 128 * u, th = 6 * u, ty = f.gy + f.gh - 13 * u;
				final float tx = i == 0 ? f.gx + outer + 4 * u : f.gx + f.gw - outer - 4 * u - tw;
				final float ten = v.meter;
				final float ts = i == 0 ? skew * 0.6f : -skew * 0.6f;
				d.slant(tx - u, tx + tw + u, ty - u, th + 2 * u, ts, alpha(side[i], 0.3f), alpha(side[i], 0.1f));
				d.slant(tx, tx + tw, ty, th, ts, 0xE6070B18, 0xE6070B18);
				final int tc = ten >= 1f ? 0xFFFFFFFF : ten >= 0.5f ? 0xFFFFD34A : 0xFF4DFFB0;
				if (i == 0) {
					d.slant(tx, tx + tw * ten, ty, th, ts, tc, mix(tc, 0xFF000000, 0.35f));
				} else {
					d.slant(tx + tw * (1 - ten), tx + tw, ty, th, ts, tc, mix(tc, 0xFF000000, 0.35f));
				}
				for (int k = 1; k < 4; k++) {
					d.rect(tx + tw * k / 4f - 0.5f * u, ty, u, th, k == 2 ? 0xCCFFFFFF : 0x66FFFFFF);
				}
				d.text(meterText(st, v, true), i == 0 ? tx : tx + tw, ty - 5 * u, 6.5f * u, tc, i == 0 ? ALIGN_L : ALIGN_R);
			}

			// Combo, on the attacker's side.
			if (v.comboShown) {
				final float fade = comboFade(v, tr.now), pop = pop(v, tr.now);
				final float x = i == 0 ? f.gx + 16 * u : f.gx + f.gw - 16 * u;
				final int al = i == 0 ? ALIGN_L : ALIGN_R;
				d.text(String.valueOf(v.comboHits), x, f.gy + 78 * u, 30 * u * pop, alpha(side[i], fade), al);
				d.text("HITS", x, f.gy + 97 * u, 9 * u, alpha(0xFFFFFFFF, fade), al);
				d.text(v.comboDamage + " DMG", x, f.gy + 108 * u, 7.5f * u, alpha(0xFFFFD34A, fade), al);
			}
		}
		if (st.timer) {
			d.slant(cx - 14 * u, cx + 14 * u, f.gy + 4 * u, 18 * u, 0, 0xE6070B18, 0xE6070B18);
			d.rect(cx - 14 * u, f.gy + 4 * u, 28 * u, u, 0xFF22E5FF);
			d.rect(cx - 14 * u, f.gy + 21 * u, 28 * u, u, 0xFFFF3EA5);
			d.text(timerText(tr.timer), cx, f.gy + 13 * u, 13 * u, 0xFFFFFFFF, ALIGN_C);
		}
	}

	private interface Span {
		void draw(float from, float to, int top, int bottom, float grow);
	}

	private interface Fill {
		void draw(float from, float to, int top, int bottom);
	}

	// Design 2: Arcade. Chunky yellow bars on solid bands, like an arcade cabinet of the 90s.
	private static void drawArcade(Painter d, Tracker tr, Frame f) {
		final float u = f.gh / 272f;
		final float cx = f.gx + f.gw * 0.5f;
		final int gold = 0xFFE8B830;
		final Style st = tr.style;
		d.vgrad(f.gx, f.gy, f.gw, 34 * u, 0xF8101018, 0xF0000000);
		d.rect(f.gx, f.gy + 34 * u, f.gw, u, gold);
		d.vgrad(f.gx, f.gy + 35 * u, f.gw, 12 * u, 0xC0000000, 0x00000000);
		if (st.meter != null) {
			d.vgrad(f.gx, f.gy + f.gh - 24 * u, f.gw, 24 * u, 0xE0000000, 0xF0101018);
			d.rect(f.gx, f.gy + f.gh - 25 * u, f.gw, u, gold);
		}
		for (int i = 0; i < 2; i++) {
			final PlayerView v = tr.p[i];
			final float bw = f.gw * 0.5f - 30 * u, bh = 13 * u, by = f.gy + 6 * u;
			final float bx = i == 0 ? f.gx + 8 * u : cx + 22 * u;
			final float hp = v.health / v.healthMax, trail = v.trail / v.healthMax;
			d.rect(bx - u, by - u, bw + 2 * u, bh + 2 * u, gold);
			d.rect(bx, by, bw, bh, 0xFF2A0A0A);
			final int fi = i;
			// Bars recede toward the outer edge.
			Fill fill = (from, to, top, bottom) -> {
				float a = fi == 0 ? bx + bw * from : bx + bw * (1 - to);
				d.vgrad(a, by, bw * (to - from), bh, top, bottom);
			};
			fill.draw(hp, trail, 0xFFFF5A4A, 0xFFB3140F);
			final boolean low = hp < 0.3f;
			final float blink = low && (tr.now % 0.5) < 0.25 ? 1f : 0f;
			fill.draw(0, hp, mix(0xFFFFF27A, 0xFFFFFFFF, blink), mix(0xFFF2A100, 0xFFFF6A00, low ? 1f : 0f));
			d.rect(i == 0 ? bx : bx + bw * (1 - hp), by + 2 * u, bw * hp, 2 * u, 0x66FFFFFF);
			final float nx = i == 0 ? bx : bx + bw;
			final int al = i == 0 ? ALIGN_L : ALIGN_R;
			d.text(name(v, i), nx, by + bh + 8.5f * u, 9 * u, 0xFFFFFFFF, al);

			if (st.meter != null) {
				// (Narrower on a 4:3 picture, so both percentages fit in the middle.)
				final float tw = Math.min(150 * u, f.gw * 0.5f - 70 * u), th = 8 * u, ty = f.gy + f.gh - 13 * u;
				final float tx = i == 0 ? f.gx + 8 * u : f.gx + f.gw - 8 * u - tw;
				final float ten = v.meter;
				d.rect(tx - u, ty - u, tw + 2 * u, th + 2 * u, 0xFF8890A8);
				d.rect(tx, ty, tw, th, 0xFF101028);
				final int tc = ten >= 1f ? 0xFFFFFFFF : ten >= 0.5f ? 0xFFFF7A1A : 0xFF4FC3FF;
				d.vgrad(i == 0 ? tx : tx + tw * (1 - ten), ty, tw * ten, th, tc, mix(tc, 0xFF000000, 0.45f));
				d.rect(tx + tw * 0.5f - 0.5f * u, ty, u, th, 0xFFFFFFFF);
				d.text(st.meter, i == 0 ? tx : tx + tw, ty - 6 * u, 6.5f * u, 0xFF8890A8, al);
				d.text(meterText(st, v, false), i == 0 ? tx + tw + 4 * u : tx - 4 * u, ty + th * 0.5f, 9 * u, tc, i == 0 ? ALIGN_L : ALIGN_R);
			}

			if (v.comboShown) {
				final float fade = comboFade(v, tr.now), pop = pop(v, tr.now);
				final float x = i == 0 ? f.gx + f.gw * 0.22f : f.gx + f.gw * 0.78f;
				d.text(v.comboHits + " HITS!", x + 1.5f * u, f.gy + 62 * u + 1.5f * u, 22 * u * pop, alpha(0xFFB3140F, fade), ALIGN_C, false);
				d.text(v.comboHits + " HITS!", x, f.gy + 62 * u, 22 * u * pop, alpha(0xFFFFE04A, fade), ALIGN_C);
				d.text(v.comboDamage + " DAMAGE", x, f.gy + 80 * u, 8 * u, alpha(0xFFFFFFFF, fade), ALIGN_C);
			}
		}
		if (st.timer) {
			d.rect(cx - 16 * u, f.gy + 3 * u, 32 * u, 26 * u, gold);
			d.vgrad(cx - 15 * u, f.gy + 4 * u, 30 * u, 24 * u, 0xFF202030, 0xFF000000);
			d.text(timerText(tr.timer), cx, f.gy + 16 * u, 18 * u, 0xFFFFFFFF, ALIGN_C);
		}
	}

	// Design 3: Side. Vertical gauges in the black bars of wide phones (inside the picture if
	// there is no room).
	private static void drawSide(Painter d, Tracker tr, Frame f) {
		final float u = f.gh / 272f;
		final int[] side = { 0xFF2DE1C2, 0xFFFF8A3D };
		final float room = Math.min(f.gx, f.sw - (f.gx + f.gw));
		final boolean outside = room >= 44 * u;
		final float pw = outside ? Math.min(room - 6 * u, 64 * u) : 34 * u;
		final Style st = tr.style;
		final boolean meter = st.meter != null;
		for (int i = 0; i < 2; i++) {
			final PlayerView v = tr.p[i];
			final float px = outside
				? (i == 0 ? f.gx - room * 0.5f - pw * 0.5f : f.gx + f.gw + room * 0.5f - pw * 0.5f)
				: (i == 0 ? f.gx + 4 * u : f.gx + f.gw - 4 * u - pw);
			final float top = f.gy + 8 * u, bottom = f.gy + f.gh - 8 * u;
			d.vgrad(px, top, pw, bottom - top, outside ? 0xFF15151E : 0xA0000000, outside ? 0xFF0B0B10 : 0x80000000);
			d.rect(px, top, pw, u, side[i]);
			final float cx = px + pw * 0.5f;
			d.text(name(v, i), cx, top + 8 * u, Math.min(8.5f * u, pw / 5.5f), side[i], ALIGN_C);
			// Health: tall bar, empties from the top (wider when the game has no meter).
			final float hx = meter ? px + pw * 0.18f : px + pw * 0.28f, hw = meter ? pw * 0.34f : pw * 0.44f;
			final float hy = top + 18 * u, hh = (bottom - top) * 0.62f;
			final float hp = v.health / v.healthMax, trail = v.trail / v.healthMax;
			d.rect(hx, hy, hw, hh, 0xFF05050A);
			d.rect(hx, hy + hh * (1 - trail), hw, hh * (trail - hp), 0xFFFFFFFF);
			final boolean low = hp < 0.3f;
			d.vgrad(hx, hy + hh * (1 - hp), hw, hh * hp, low ? 0xFFFF6B5A : mix(side[i], 0xFFFFFFFF, 0.3f), low ? 0xFF8C1A12 : mix(side[i], 0xFF000000, 0.5f));
			for (int k = 1; k < 4; k++) {
				d.rect(hx, hy + hh * k / 4f, hw, 0.6f * u, 0x80000000);
			}
			final float ly = hy + hh + 7 * u;
			d.text(pct(v.health, v.healthMax), hx + hw * 0.5f, ly, Math.min(7 * u, pw / 6f), 0xFFFFFFFF, ALIGN_C);
			int tc = 0xFF6E8BFF;
			if (meter) {
				final float tx = px + pw * 0.60f, tw = pw * 0.18f;
				final float ten = v.meter;
				tc = ten >= 1f ? 0xFFFFFFFF : ten >= 0.5f ? 0xFFFFD34A : 0xFF6E8BFF;
				d.rect(tx, hy, tw, hh, 0xFF05050A);
				d.vgrad(tx, hy + hh * (1 - ten), tw, hh * ten, tc, mix(tc, 0xFF000000, 0.4f));
				d.rect(tx - 0.5f * u, hy + hh * 0.5f, tw + u, 0.8f * u, 0xFFFFFFFF);
				d.text(pct(v.meter, 1f), tx + tw * 0.5f, ly, Math.min(7 * u, pw / 6f), tc, ALIGN_C);
			}
			final float by = ly + 7 * u;
			if (meter && v.stocks >= 0) {
				d.text("×" + v.stocks, cx, by + 1.5f * u, Math.min(7.5f * u, pw / 5f), tc, ALIGN_C);
			}
			if (v.comboShown) {
				final float fade = comboFade(v, tr.now), pop = pop(v, tr.now);
				final float cy = Math.min(bottom - 14 * u, by + 22 * u);
				d.text(String.valueOf(v.comboHits), cx, cy, Math.min(20 * u, pw / 2.2f) * pop, alpha(side[i], fade), ALIGN_C);
				d.text("HITS", cx, cy + 12 * u, Math.min(6.5f * u, pw / 6f), alpha(0xFFFFFFFF, fade), ALIGN_C);
			}
		}
		if (st.timer && outside) {
			// The timer in the left bar, under player 1's gauges.
			final float px = f.gx - room * 0.5f;
			d.text(timerText(tr.timer), px, f.gy + f.gh - 20 * u, Math.min(14 * u, pw / 3f), 0xFFFFFFFF, ALIGN_C);
		}
	}

	// Design 4: Data. Small boxes with exact numbers, for training (last combo stays on screen).
	private static void drawData(Painter d, Tracker tr, Frame f) {
		final float u = f.gh / 272f;
		final int[] side = { 0xFF5AC8FA, 0xFFFF6B6B };
		final Style st = tr.style;
		for (int i = 0; i < 2; i++) {
			final PlayerView v = tr.p[i];
			final float bw = 124 * u, bh = 50 * u, by = f.gy + 40 * u;
			final float bx = i == 0 ? f.gx + 6 * u : f.gx + f.gw - 6 * u - bw;
			d.rect(bx, by, bw, bh, 0xB8000000);
			d.rect(bx, by, 2 * u, bh, side[i]);
			final float x = bx + 6 * u, line = 9.5f * u;
			float y = by + 6.5f * u;
			d.text(name(v, i), x, y, 8 * u, side[i], ALIGN_L);
			d.text((int) v.health + "/" + (int) v.healthMax + "  " + pct(v.health, v.healthMax), bx + bw - 5 * u, y, 7.5f * u, 0xFFFFFFFF, ALIGN_R);
			y += line;
			if (st.meter != null) {
				final float ten = v.meter;
				d.text(String.format(Locale.ROOT, "%s %d%%", st.meterWord, (int) (ten * 100f)) + (v.stocks >= 0 ? " ×" + v.stocks : ""), x, y, 7 * u, 0xFFE0E0E8, ALIGN_L, false);
				d.rect(bx + 70 * u, y - 2 * u, bw - 75 * u, 4 * u, 0xFF262630);
				d.rect(bx + 70 * u, y - 2 * u, (bw - 75 * u) * ten, 4 * u, ten >= 0.5f ? 0xFFFFD34A : 0xFF6E8BFF);
			} else if (st.timer && i == 0) {
				d.text("Tiempo " + timerText(tr.timer), x, y, 7 * u, 0xFFA0A0B0, ALIGN_L, false);
			}
			y += line;
			if (st.meter != null && st.timer && i == 0) {
				d.text("Tiempo " + timerText(tr.timer), x, y, 7 * u, 0xFFA0A0B0, ALIGN_L, false);
			}
			y += line;
			// Combos are counted from the health drops: "≈".
			if (v.comboShown) {
				d.text("Combo ≈" + v.comboHits + " hits  " + v.comboDamage + " daño", x, y, 7.5f * u, 0xFFFFD34A, ALIGN_L, false);
			} else if (v.lastComboHits >= 2) {
				d.text("Último: ≈" + v.lastComboHits + " hits  " + v.lastComboDamage + " daño", x, y, 7 * u, 0xFFA0A0B0, ALIGN_L, false);
			} else {
				d.text("Sin combos todavía", x, y, 7 * u, 0xFF70707C, ALIGN_L, false);
			}
		}
	}
}
