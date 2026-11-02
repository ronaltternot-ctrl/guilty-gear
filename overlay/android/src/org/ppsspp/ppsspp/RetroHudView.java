package org.ppsspp.ppsspp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;

/**
 * The custom HUD over a libretro game (ArcadeActivity): reads the game's RAM about 30 times a
 * second on its own thread (RetroMemory), and draws RetroHud's design over the picture, which
 * LibretroDroid fits in the middle of the screen with the game's aspect ratio.
 */
final class RetroHudView extends View {
	private static final String TAG = "RetroHudView";

	private final SnesHud.Game game;
	private final RetroHud.Tracker tracker = new RetroHud.Tracker();
	private final RetroHud.Frame frame = new RetroHud.Frame();
	private final Handler main = new Handler(Looper.getMainLooper());
	private final Typeface face;
	private int design = RetroHud.DESIGN_NEON;
	private float opacity = 1f;
	private float aspect;
	private volatile boolean running;
	private Thread thread;

	RetroHudView(Context c, SnesHud.Game game, float aspect, Typeface face) {
		super(c);
		this.game = game;
		this.aspect = aspect;
		this.face = face;
		setWillNotDraw(false);
		setClickable(false);
		setFocusable(false);
	}

	void setDesign(int design) {
		this.design = design;
		setVisibility(design == RetroHud.DESIGN_OFF ? GONE : VISIBLE);
		invalidate();
	}

	/** The picture's width / height (4:3, or 8:7 for the SNES's exact pixels). */
	void setAspect(float aspect) {
		this.aspect = aspect;
		invalidate();
	}

	void setOpacity(float opacity) {
		this.opacity = opacity;
		invalidate();
	}

	/** Starts reading (once the game runs: the library that reads is loaded with it). */
	void start() {
		if (running) {
			return;
		}
		running = true;
		thread = new Thread(this::loop, "RetroHud");
		thread.setPriority(Thread.NORM_PRIORITY - 1);
		thread.start();
	}

	void stop() {
		running = false;
		if (thread != null) {
			thread.interrupt();
			thread = null;
		}
	}

	private void loop() {
		byte[] ram = new byte[game.windowSize];
		while (running) {
			final RetroHud.Snapshot s = new RetroHud.Snapshot();
			try {
				if (RetroMemory.read(RetroMemory.SYSTEM_RAM, game.windowStart, ram)) {
					game.read(ram, s);
				}
			} catch (UnsatisfiedLinkError e) {
				// A LibretroDroid without patches/libretrodroid/0002: no HUD.
				Log.w(TAG, "No memory access", e);
				running = false;
				main.post(() -> setVisibility(GONE));
				return;
			}
			final double t = SystemClock.uptimeMillis() / 1000.0;
			main.post(() -> {
				tracker.update(s, t);
				invalidate();
			});
			try {
				Thread.sleep(33);
			} catch (InterruptedException e) {
				return;
			}
		}
	}

	@Override
	protected void onDetachedFromWindow() {
		stop();
		super.onDetachedFromWindow();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		float w = getWidth(), h = getHeight();
		if (w <= 0 || h <= 0) {
			return;
		}
		float gw = Math.min(w, h * aspect);
		float gh = gw / aspect;
		frame.gx = (w - gw) / 2f;
		frame.gy = (h - gh) / 2f;
		frame.gw = gw;
		frame.gh = gh;
		frame.sw = w;
		frame.sh = h;
		RetroHud.draw(canvas, design, tracker, frame, opacity, face);
	}
}
