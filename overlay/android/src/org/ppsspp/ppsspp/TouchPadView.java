package org.ppsspp.ppsspp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.SparseArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Touch controls for the games that don't run in PPSSPP (arcade through FBNeo, MUGEN through
 * Ikemen GO), drawn like the launcher's "Móvil" style: a big D-pad or joystick on the left, big
 * round buttons at the bottom right with the shortcut buttons (several buttons at once) above
 * them, coin / start at the bottom in the middle and a pause button at the top.
 *
 * The movement control has a fixed center, so tapping forward twice runs (dash) and the eight
 * directions don't drift. Each finger is tracked on its own; a finger can slide from one button
 * to another. Buttons that share a key (A and the A+B shortcut) are counted, so the key is only
 * released when no finger holds it.
 */
final class TouchPadView extends View {
	interface Listener {
		/** x: -1 left, 1 right; y: -1 up, 1 down. Only called when it changes. */
		void onDirection(int x, int y);

		/** A key the activity defined (Android key code); only on press / release changes. */
		void onKey(int code, boolean down);

		void onMenu();
	}

	/** One button: its label, color and the keys it holds down. */
	static final class Spec {
		final String label;
		final int color;
		final int[] codes;

		Spec(String label, int color, int... codes) {
			this.label = label;
			this.color = color;
			this.codes = codes;
		}
	}

	private static final int KIND_MAIN = 0, KIND_SHORTCUT = 1, KIND_SYSTEM = 2, KIND_MENU = 3;
	private static final int TARGET_MOVE = -2;

	private static final class Btn {
		final Spec spec;
		final int kind;
		float cx, cy, r;
		int holders;

		Btn(Spec spec, int kind) {
			this.spec = spec;
			this.kind = kind;
		}
	}

	private final List<Btn> buttons = new ArrayList<>();
	private Spec[] top = new Spec[0], bottom = new Spec[0], shortcuts = new Spec[0], system = new Spec[0];
	private final SparseArray<Integer> pointers = new SparseArray<>();
	private final Map<Integer, Integer> held = new HashMap<>();
	private Listener listener;

	private boolean stick;
	private int sizePercent = 100;
	private float opacity = 0.85f;
	private boolean haptics = true;

	// Movement control
	private float moveX, moveY, moveR;
	private int moveId = -1;
	private float headX, headY;
	private int dirX, dirY;

	private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Path path = new Path();
	private final RectF rect = new RectF();

	TouchPadView(Context context) {
		super(context);
		ring.setStyle(Paint.Style.STROKE);
		text.setTextAlign(Paint.Align.CENTER);
		text.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
		setHapticFeedbackEnabled(true);
	}

	void setListener(Listener l) {
		listener = l;
	}

	/** Big buttons in two rows (the top row sits between the ones below), shortcuts, coin / start. */
	void setButtons(Spec[] topRow, Spec[] bottomRow, Spec[] shortcutRow, Spec[] systemRow) {
		releaseAll();
		top = topRow;
		bottom = bottomRow;
		shortcuts = shortcutRow;
		system = systemRow;
		layoutButtons();
		invalidate();
	}

	void setStick(boolean stick) {
		this.stick = stick;
		invalidate();
	}

	void setSizePercent(int percent) {
		sizePercent = Math.max(60, Math.min(150, percent));
		layoutButtons();
		invalidate();
	}

	void setOpacity(float opacity) {
		this.opacity = Math.max(0.2f, Math.min(1f, opacity));
		invalidate();
	}

	void setHaptics(boolean on) {
		haptics = on;
	}

	/** Lets go of everything (before a menu, when the window loses focus). */
	void releaseAll() {
		pointers.clear();
		for (Btn b : buttons) {
			b.holders = 0;
		}
		for (Map.Entry<Integer, Integer> e : held.entrySet()) {
			if (e.getValue() > 0 && listener != null) {
				listener.onKey(e.getKey(), false);
			}
		}
		held.clear();
		moveId = -1;
		setDirection(0, 0);
		invalidate();
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		layoutButtons();
	}

	private void layoutButtons() {
		buttons.clear();
		final float w = getWidth(), h = getHeight();
		if (w <= 0 || h <= 0) {
			return;
		}
		final float density = getResources().getDisplayMetrics().density;
		final float f = sizePercent / 100f;
		final float d = Math.max(52f * density, Math.min(0.3f * h, 0.215f * h * f));   // main button
		final float small = 0.62f * d;
		final float m = 0.035f * h;
		final float step = 1.1f * d;
		final float yBottom = h - m - d / 2f;
		final float yTop = yBottom - 0.93f * d;
		final float yShort = yTop - d / 2f - 0.12f * d - small / 2f;

		for (int i = 0; i < bottom.length; i++) {
			add(bottom[i], KIND_MAIN, w - m - d / 2f - (bottom.length - 1 - i) * step, yBottom, d / 2f);
		}
		for (int i = 0; i < top.length; i++) {
			add(top[i], KIND_MAIN, w - m - d / 2f - step / 2f - (top.length - 1 - i) * step, yTop, d / 2f);
		}
		for (int i = 0; i < shortcuts.length; i++) {
			add(shortcuts[i], KIND_SHORTCUT, w - m - small / 2f - (shortcuts.length - 1 - i) * 1.18f * small, yShort, small / 2f);
		}
		// Coin / start at the bottom in the middle, pause at the top in the middle.
		final float sys = 0.42f * d;
		for (int i = 0; i < system.length; i++) {
			float x = w / 2f + (i - (system.length - 1) / 2f) * 0.12f * w;
			add(system[i], KIND_SYSTEM, x, h - m - sys / 2f, sys / 2f);
		}
		add(new Spec("❚❚", 0xFFFFFFFF), KIND_MENU, w / 2f, m + sys / 2f, sys / 2f);

		// D-pad / joystick: about half the screen height wide.
		final float size = Math.max(110f * density, Math.min(0.62f * h, 0.5f * h * f));
		moveR = size / 2f;
		moveX = m + moveR + 0.02f * w;
		moveY = h - m - moveR;
		headX = moveX;
		headY = moveY;
	}

	private void add(Spec spec, int kind, float x, float y, float r) {
		Btn b = new Btn(spec, kind);
		b.cx = x;
		b.cy = y;
		b.r = r;
		buttons.add(b);
	}

	// Touch

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(MotionEvent e) {
		final int action = e.getActionMasked();
		switch (action) {
			case MotionEvent.ACTION_DOWN:
			case MotionEvent.ACTION_POINTER_DOWN: {
				int i = e.getActionIndex();
				down(e.getPointerId(i), e.getX(i), e.getY(i));
				break;
			}
			case MotionEvent.ACTION_MOVE:
				for (int i = 0; i < e.getPointerCount(); i++) {
					move(e.getPointerId(i), e.getX(i), e.getY(i));
				}
				break;
			case MotionEvent.ACTION_UP:
			case MotionEvent.ACTION_POINTER_UP: {
				int i = e.getActionIndex();
				up(e.getPointerId(i));
				break;
			}
			case MotionEvent.ACTION_CANCEL:
				releaseAll();
				break;
			default:
				break;
		}
		return true;
	}

	private int hit(float x, float y, boolean pressable) {
		int best = -1;
		float bestDist = Float.MAX_VALUE;
		for (int i = 0; i < buttons.size(); i++) {
			Btn b = buttons.get(i);
			if (pressable && b.kind == KIND_MENU) {
				continue;
			}
			float dx = x - b.cx, dy = y - b.cy;
			float dist = (float) Math.sqrt(dx * dx + dy * dy);
			// A little more room than drawn, so fat fingers between two buttons still hit one.
			float reach = b.r * (b.kind == KIND_MAIN ? 1.12f : 1.2f);
			if (dist <= reach && dist / reach < bestDist) {
				bestDist = dist / reach;
				best = i;
			}
		}
		return best;
	}

	private void down(int id, float x, float y) {
		int b = hit(x, y, false);
		if (b >= 0) {
			Btn btn = buttons.get(b);
			if (btn.kind == KIND_MENU) {
				pointers.put(id, b);
				if (listener != null) {
					releaseAll();
					listener.onMenu();
				}
				return;
			}
			pointers.put(id, b);
			press(btn);
			return;
		}
		// Anywhere on the left part of the screen moves (with the fixed center of the control).
		if (moveId < 0 && x < getWidth() * 0.45f) {
			moveId = id;
			pointers.put(id, TARGET_MOVE);
			moveTo(x, y);
		}
	}

	private void move(int id, float x, float y) {
		Integer t = pointers.get(id);
		if (t == null) {
			return;
		}
		if (t == TARGET_MOVE) {
			moveTo(x, y);
			return;
		}
		Btn cur = t >= 0 && t < buttons.size() ? buttons.get(t) : null;
		if (cur == null || cur.kind == KIND_MENU || cur.kind == KIND_SYSTEM) {
			return;
		}
		// Slide onto another attack button.
		int b = hit(x, y, true);
		if (b >= 0 && b != t) {
			Btn next = buttons.get(b);
			if (next.kind == KIND_MAIN || next.kind == KIND_SHORTCUT) {
				release(cur);
				press(next);
				pointers.put(id, b);
			}
		}
	}

	private void up(int id) {
		Integer t = pointers.get(id);
		pointers.remove(id);
		if (t == null) {
			return;
		}
		if (t == TARGET_MOVE) {
			moveId = -1;
			headX = moveX;
			headY = moveY;
			setDirection(0, 0);
			invalidate();
			return;
		}
		if (t >= 0 && t < buttons.size() && buttons.get(t).kind != KIND_MENU) {
			release(buttons.get(t));
		}
	}

	private void press(Btn b) {
		b.holders++;
		for (int code : b.spec.codes) {
			Integer n = held.get(code);
			int count = n == null ? 0 : n;
			held.put(code, count + 1);
			if (count == 0 && listener != null) {
				listener.onKey(code, true);
			}
		}
		if (haptics) {
			performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
		}
		invalidate();
	}

	private void release(Btn b) {
		if (b.holders <= 0) {
			return;
		}
		b.holders--;
		for (int code : b.spec.codes) {
			Integer n = held.get(code);
			int count = n == null ? 0 : n;
			if (count <= 1) {
				held.remove(code);
				if (count == 1 && listener != null) {
					listener.onKey(code, false);
				}
			} else {
				held.put(code, count - 1);
			}
		}
		invalidate();
	}

	private void moveTo(float x, float y) {
		float dx = x - moveX, dy = y - moveY;
		float dist = (float) Math.sqrt(dx * dx + dy * dy);
		// The joystick head follows the finger, up to the edge of the base.
		float lim = moveR * 0.62f;
		if (dist > lim) {
			headX = moveX + dx / dist * lim;
			headY = moveY + dy / dist * lim;
		} else {
			headX = x;
			headY = y;
		}
		int nx = 0, ny = 0;
		if (dist > moveR * 0.28f) {
			// Eight sectors: a component counts beyond 22.5 degrees from the other axis.
			nx = Math.abs(dx) / dist > 0.383f ? (dx > 0 ? 1 : -1) : 0;
			ny = Math.abs(dy) / dist > 0.383f ? (dy > 0 ? 1 : -1) : 0;
		}
		setDirection(nx, ny);
		invalidate();
	}

	private void setDirection(int x, int y) {
		if (x == dirX && y == dirY) {
			return;
		}
		dirX = x;
		dirY = y;
		if (haptics && (x != 0 || y != 0)) {
			performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
		}
		if (listener != null) {
			listener.onDirection(x, y);
		}
	}

	// Drawing

	private int alpha(int color, float a) {
		int base = (color >>> 24) & 0xFF;
		return (color & 0x00FFFFFF) | (Math.round(base * a * opacity) << 24);
	}

	@Override
	protected void onDraw(Canvas c) {
		super.onDraw(c);
		if (buttons.isEmpty()) {
			layoutButtons();
		}
		final float density = getResources().getDisplayMetrics().density;
		if (stick) {
			drawStick(c, density);
		} else {
			drawDpad(c, density);
		}
		for (Btn b : buttons) {
			boolean pressed = b.holders > 0;
			fill.setStyle(Paint.Style.FILL);
			fill.setColor(pressed ? alpha(b.spec.color, 0.75f) : alpha(0xFF101015, 0.62f));
			c.drawCircle(b.cx, b.cy, b.r, fill);
			ring.setStrokeWidth(Math.max(2f * density, b.r * (b.kind == KIND_MAIN ? 0.07f : 0.08f)));
			ring.setColor(alpha(b.spec.color, pressed ? 1f : 0.9f));
			c.drawCircle(b.cx, b.cy, b.r - ring.getStrokeWidth() / 2f, ring);
			String label = b.spec.label;
			float size = b.kind == KIND_MAIN ? b.r * 0.82f : b.r * (label.length() > 3 ? 0.5f : label.length() > 2 ? 0.62f : 0.78f);
			if (b.kind == KIND_SYSTEM) {
				size = b.r * 0.48f;
			}
			text.setTextSize(size);
			text.setColor(alpha(pressed ? 0xFFFFFFFF : (b.kind == KIND_MAIN ? b.spec.color : 0xFFFFFFFF), 1f));
			c.drawText(label, b.cx, b.cy - (text.descent() + text.ascent()) / 2f, text);
		}
	}

	private void drawDpad(Canvas c, float density) {
		final float arm = moveR * 0.36f;          // half the width of an arm
		final float len = moveR * 0.92f;          // center to the end of an arm
		fill.setStyle(Paint.Style.FILL);
		fill.setColor(alpha(0xFF101015, 0.55f));
		c.drawCircle(moveX, moveY, moveR, fill);
		path.reset();
		path.addRoundRect(new RectF(moveX - arm, moveY - len, moveX + arm, moveY + len), arm * 0.35f, arm * 0.35f, Path.Direction.CW);
		path.addRoundRect(new RectF(moveX - len, moveY - arm, moveX + len, moveY + arm), arm * 0.35f, arm * 0.35f, Path.Direction.CW);
		fill.setColor(alpha(0xFF2A2A34, 0.85f));
		c.drawPath(path, fill);
		// Lit arms for the direction held.
		fill.setColor(alpha(0xFFFFFFFF, 0.55f));
		if (dirY < 0) {
			rect.set(moveX - arm, moveY - len, moveX + arm, moveY - arm);
			c.drawRoundRect(rect, arm * 0.35f, arm * 0.35f, fill);
		} else if (dirY > 0) {
			rect.set(moveX - arm, moveY + arm, moveX + arm, moveY + len);
			c.drawRoundRect(rect, arm * 0.35f, arm * 0.35f, fill);
		}
		if (dirX < 0) {
			rect.set(moveX - len, moveY - arm, moveX - arm, moveY + arm);
			c.drawRoundRect(rect, arm * 0.35f, arm * 0.35f, fill);
		} else if (dirX > 0) {
			rect.set(moveX + arm, moveY - arm, moveX + len, moveY + arm);
			c.drawRoundRect(rect, arm * 0.35f, arm * 0.35f, fill);
		}
		ring.setStrokeWidth(Math.max(2f * density, moveR * 0.025f));
		ring.setColor(alpha(0xFFFFFFFF, 0.35f));
		c.drawPath(path, ring);
		// Arrows
		fill.setColor(alpha(0xFFFFFFFF, 0.8f));
		float t = arm * 0.45f, o = len - arm * 0.7f;
		triangle(c, moveX, moveY - o, 0, -1, t);
		triangle(c, moveX, moveY + o, 0, 1, t);
		triangle(c, moveX - o, moveY, -1, 0, t);
		triangle(c, moveX + o, moveY, 1, 0, t);
	}

	private void triangle(Canvas c, float x, float y, int dx, int dy, float t) {
		path.reset();
		path.moveTo(x + dx * t, y + dy * t);
		path.lineTo(x - dx * t * 0.6f + dy * t, y - dy * t * 0.6f + dx * t);
		path.lineTo(x - dx * t * 0.6f - dy * t, y - dy * t * 0.6f - dx * t);
		path.close();
		c.drawPath(path, fill);
	}

	private void drawStick(Canvas c, float density) {
		fill.setStyle(Paint.Style.FILL);
		fill.setColor(alpha(0xFF101015, 0.55f));
		c.drawCircle(moveX, moveY, moveR, fill);
		ring.setStrokeWidth(Math.max(2f * density, moveR * 0.03f));
		ring.setColor(alpha(0xFFFFFFFF, 0.35f));
		c.drawCircle(moveX, moveY, moveR - ring.getStrokeWidth() / 2f, ring);
		// The eight directions, lit when held.
		for (int i = 0; i < 8; i++) {
			double a = Math.PI / 4 * i;
			int sx = (int) Math.round(Math.cos(a)), sy = (int) Math.round(Math.sin(a));
			boolean on = sx == dirX && sy == dirY && (dirX != 0 || dirY != 0);
			fill.setColor(alpha(0xFFFFFFFF, on ? 0.9f : 0.3f));
			c.drawCircle(moveX + (float) Math.cos(a) * moveR * 0.82f, moveY + (float) Math.sin(a) * moveR * 0.82f, moveR * (on ? 0.07f : 0.045f), fill);
		}
		float head = moveR * 0.42f;
		fill.setColor(alpha(moveId >= 0 ? 0xFF3A3A48 : 0xFF2A2A34, 0.95f));
		c.drawCircle(headX, headY, head, fill);
		ring.setColor(alpha(0xFFFFFFFF, 0.6f));
		c.drawCircle(headX, headY, head - ring.getStrokeWidth() / 2f, ring);
	}
}
