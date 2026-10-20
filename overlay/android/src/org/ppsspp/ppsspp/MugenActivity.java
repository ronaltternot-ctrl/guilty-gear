package org.ppsspp.ppsspp;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.libsdl.app.SDLActivity;

import java.io.File;

/**
 * MUGEN games with Ikemen GO (v1.0.0's Android build: libmain.so + SDL2 in the APK, its Java
 * glue in org.libsdl.app). Runs in its own process (":mugen"): the engine can't be started
 * twice in one process, and it ends the process when it quits.
 *
 * The touch controls press keyboard keys, which Ikemen GO reads as player 1 (the keys are set
 * in the game's save/config.ini by MugenInstaller): arrows, A = x, B = a, C = y, D = b in the
 * KOF layout (how KOF characters for MUGEN use the buttons), or the six MUGEN buttons.
 */
public class MugenActivity extends SDLActivity implements TouchPadView.Listener {
	static final String EXTRA_PATH = "path";
	static final String EXTRA_TITLE = "title";

	private static final int C_A = 0xFFFF5A4E, C_B = 0xFFFFB547, C_C = 0xFF4CD07D, C_D = 0xFF4DA3FF, C_SHORT = 0xFFB9B9C6;
	private static final int C_X = 0xFFFF6FA5, C_Y = 0xFFB98CFF, C_Z = 0xFF3FD6C6;

	// Ikemen GO's keyboard keys for player 1: x = a, y = s, z = d, a = z, b = x, c = c.
	private static final int MUGEN_X = KeyEvent.KEYCODE_A, MUGEN_Y = KeyEvent.KEYCODE_S, MUGEN_Z = KeyEvent.KEYCODE_D;
	private static final int MUGEN_A = KeyEvent.KEYCODE_Z, MUGEN_B = KeyEvent.KEYCODE_X, MUGEN_C = KeyEvent.KEYCODE_C;
	private static final int KEY_START = KeyEvent.KEYCODE_ENTER, KEY_PAUSE = KeyEvent.KEYCODE_ESCAPE;

	private TouchPadView pad;
	private SharedPreferences prefs;
	private boolean engineStarted;
	private boolean menuOpen;
	private int dirX, dirY;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		prefs = getSharedPreferences("mugen", MODE_PRIVATE);
		super.onCreate(savedInstanceState);
	}

	@Override
	protected String getGamePath() {
		String path = getIntent().getStringExtra(EXTRA_PATH);
		if (path == null || !new File(path).isDirectory()) {
			Toast.makeText(this, "No encuentro la carpeta del juego. Vuelve a añadirlo en el catálogo.", Toast.LENGTH_LONG).show();
			return null;
		}
		return path;
	}

	@Override
	protected void prepareGame(File baseDir) throws Exception {
		MugenInstaller.installEngine(this, baseDir);
		MugenInstaller.writeConfig(baseDir);
	}

	@Override
	protected void onCreateOverlay(FrameLayout root) {
		pad = new TouchPadView(this);
		pad.setListener(this);
		applyPadSettings();
		root.addView(pad, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
	}

	@Override
	protected void onEngineStarted() {
		engineStarted = true;
	}

	private void applyPadSettings() {
		if (pad == null) {
			return;
		}
		boolean six = "six".equals(prefs.getString("buttons", "kof"));
		if (six) {
			pad.setButtons(
				new TouchPadView.Spec[] { new TouchPadView.Spec("X", C_X, MUGEN_X), new TouchPadView.Spec("Y", C_Y, MUGEN_Y), new TouchPadView.Spec("Z", C_Z, MUGEN_Z) },
				new TouchPadView.Spec[] { new TouchPadView.Spec("A", C_A, MUGEN_A), new TouchPadView.Spec("B", C_B, MUGEN_B), new TouchPadView.Spec("C", C_C, MUGEN_C) },
				new TouchPadView.Spec[] {
					new TouchPadView.Spec("XA", C_SHORT, MUGEN_X, MUGEN_A),
					new TouchPadView.Spec("YB", C_SHORT, MUGEN_Y, MUGEN_B),
					new TouchPadView.Spec("XYZ", C_SHORT, MUGEN_X, MUGEN_Y, MUGEN_Z),
				},
				new TouchPadView.Spec[] { new TouchPadView.Spec("START", 0xFFE8E8EE, KEY_START) });
		} else {
			// KOF characters for MUGEN: A = x, B = a, C = y, D = b (z and c are extras).
			pad.setButtons(
				new TouchPadView.Spec[] { new TouchPadView.Spec("A", C_A, MUGEN_X), new TouchPadView.Spec("C", C_C, MUGEN_Y) },
				new TouchPadView.Spec[] { new TouchPadView.Spec("B", C_B, MUGEN_A), new TouchPadView.Spec("D", C_D, MUGEN_B) },
				new TouchPadView.Spec[] {
					new TouchPadView.Spec("AB", C_SHORT, MUGEN_X, MUGEN_A),
					new TouchPadView.Spec("CD", C_SHORT, MUGEN_Y, MUGEN_B),
					new TouchPadView.Spec("BC", C_SHORT, MUGEN_A, MUGEN_Y),
					new TouchPadView.Spec("z", C_Z, MUGEN_Z),
					new TouchPadView.Spec("c", C_Z, MUGEN_C),
				},
				new TouchPadView.Spec[] { new TouchPadView.Spec("START", 0xFFE8E8EE, KEY_START) });
		}
		pad.setStick("stick".equals(prefs.getString("move", "dpad")));
		pad.setSizePercent(prefs.getInt("size", 100));
		pad.setOpacity(prefs.getFloat("opacity", 0.85f));
		pad.setVisibility(prefs.getBoolean("hide_pad", false) ? View.GONE : View.VISIBLE);
	}

	// Touch controls -> keyboard

	private static void key(int code, boolean down) {
		if (down) {
			SDLActivity.onNativeKeyDown(code);
		} else {
			SDLActivity.onNativeKeyUp(code);
		}
	}

	@Override
	public void onDirection(int x, int y) {
		if (!engineStarted) {
			dirX = dirY = 0;
			return;
		}
		if (x != dirX) {
			if (dirX != 0) {
				key(dirX < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT, false);
			}
			if (x != 0) {
				key(x < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT, true);
			}
			dirX = x;
		}
		if (y != dirY) {
			if (dirY != 0) {
				key(dirY < 0 ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN, false);
			}
			if (y != 0) {
				key(y < 0 ? KeyEvent.KEYCODE_DPAD_UP : KeyEvent.KEYCODE_DPAD_DOWN, true);
			}
			dirY = y;
		}
	}

	@Override
	public void onKey(int code, boolean down) {
		if (engineStarted) {
			key(code, down);
		}
	}

	@Override
	public void onMenu() {
		showMenu();
	}

	private void showMenu() {
		if (menuOpen) {
			return;
		}
		menuOpen = true;
		if (pad != null) {
			pad.releaseAll();
		}
		final boolean stick = "stick".equals(prefs.getString("move", "dpad"));
		final boolean six = "six".equals(prefs.getString("buttons", "kof"));
		final int size = prefs.getInt("size", 100);
		final boolean hidden = prefs.getBoolean("hide_pad", false);
		String[] items = {
			"⏸  Pausa del juego (menú de Ikemen)",
			"🕹  Movimiento: " + (stick ? "joystick" : "flechas"),
			"🔘  Botones: " + (six ? "6 botones MUGEN (X Y Z / A B C)" : "KOF (A B C D)"),
			"📏  Tamaño de los botones: " + (size < 95 ? "pequeño" : size > 110 ? "grande" : "normal"),
			"👁  Controles en pantalla: " + (hidden ? "ocultos" : "visibles"),
			"✕  Salir al catálogo",
		};
		new AlertDialog.Builder(this)
			.setTitle(getIntent().getStringExtra(EXTRA_TITLE) == null ? "MUGEN" : getIntent().getStringExtra(EXTRA_TITLE))
			.setItems(items, (d, which) -> {
				menuOpen = false;
				switch (which) {
					case 0:
						if (engineStarted) {
							// Esc opens Ikemen GO's pause menu (EscOpensMenu = 1).
							key(KEY_PAUSE, true);
							pad.postDelayed(() -> key(KEY_PAUSE, false), 80);
						}
						break;
					case 1:
						prefs.edit().putString("move", stick ? "dpad" : "stick").apply();
						applyPadSettings();
						break;
					case 2:
						prefs.edit().putString("buttons", six ? "kof" : "six").apply();
						applyPadSettings();
						break;
					case 3:
						prefs.edit().putInt("size", size < 95 ? 100 : size > 110 ? 85 : 120).apply();
						applyPadSettings();
						break;
					case 4:
						prefs.edit().putBoolean("hide_pad", !hidden).apply();
						applyPadSettings();
						if (!hidden) {
							Toast.makeText(this, "Para volver a verlos, abre este menú con el botón Atrás", Toast.LENGTH_LONG).show();
						}
						break;
					case 5:
						quit();
						break;
					default:
						break;
				}
			})
			.setOnCancelListener(d -> menuOpen = false)
			.show();
	}

	private void quit() {
		finish();
		// SDLActivity.onDestroy ends this process (":mugen"); make sure even if the engine hangs.
		new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
			() -> android.os.Process.killProcess(android.os.Process.myPid()), 1500);
	}

	// The back button opens this menu instead of closing the game.

	@SuppressWarnings("deprecation")
	@Override
	public void onBackPressed() {
		showMenu();
	}

	@Override
	public void superOnBackPressed() {
		runOnUiThread(this::showMenu);
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && !isFromMouse(event)) {
			if (event.getAction() == KeyEvent.ACTION_UP) {
				showMenu();
			}
			return true;
		}
		return super.dispatchKeyEvent(event);
	}

	private static boolean isFromMouse(KeyEvent e) {
		return (e.getSource() & android.view.InputDevice.SOURCE_MOUSE) == android.view.InputDevice.SOURCE_MOUSE;
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (!hasFocus && pad != null) {
			pad.releaseAll();
		}
	}
}
