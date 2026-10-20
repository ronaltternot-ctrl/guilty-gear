package org.ppsspp.ppsspp;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;

import com.swordfish.libretrodroid.GLRetroView;
import com.swordfish.libretrodroid.GLRetroViewData;
import com.swordfish.libretrodroid.ShaderConfig;
import com.swordfish.libretrodroid.Variable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;

import kotlin.Unit;
import kotlinx.coroutines.GlobalScope;
import kotlinx.coroutines.flow.FlowKt;

/**
 * Arcade / Neo Geo games with the FBNeo libretro core (Lemuroid's build, in the APK as
 * libfbneo_libretro_android.so) through LibretroDroid. Runs in its own process (":arcade"), which
 * ends with the game, so each game starts with a clean core.
 */
public class ArcadeActivity extends ComponentActivity implements TouchPadView.Listener {
	private static final String TAG = "ArcadeActivity";

	static final String EXTRA_ROM = "rom";
	static final String EXTRA_TITLE = "title";

	private static final String CORE = "libfbneo_libretro_android.so";

	// FBNeo's "classic" RetroPad for the Neo Geo: A, B, C, D are the RetroPad's B, A, Y, X.
	// LibretroDroid turns Android's BUTTON_A..Y into the RetroPad's A..Y.
	private static final int KEY_A = KeyEvent.KEYCODE_BUTTON_B;
	private static final int KEY_B = KeyEvent.KEYCODE_BUTTON_A;
	private static final int KEY_C = KeyEvent.KEYCODE_BUTTON_Y;
	private static final int KEY_D = KeyEvent.KEYCODE_BUTTON_X;
	private static final int KEY_COIN = KeyEvent.KEYCODE_BUTTON_SELECT;
	private static final int KEY_START = KeyEvent.KEYCODE_BUTTON_START;

	private static final int C_A = 0xFFFF5A4E, C_B = 0xFFFFB547, C_C = 0xFF4CD07D, C_D = 0xFF4DA3FF, C_SHORT = 0xFFB9B9C6;

	static final String[] SHADERS = { "sharp", "cut", "crt", "lcd", "none" };
	static final String[] SHADER_NAMES = { "Nítida (píxeles limpios)", "Suavizada (bordes redondeados)", "CRT (televisor)", "LCD", "Original" };

	private final Handler handler = new Handler(Looper.getMainLooper());
	private SharedPreferences prefs;
	private GLRetroView retro;
	private TouchPadView pad;
	private TextView status;
	private File rom, saves, states;
	private String set, title;
	private boolean started, failed, menuOpen, paused;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		prefs = getSharedPreferences("arcade", MODE_PRIVATE);
		goFullscreen();

		FrameLayout root = new FrameLayout(this);
		root.setBackgroundColor(Color.BLACK);
		setContentView(root);

		String path = getIntent().getStringExtra(EXTRA_ROM);
		title = getIntent().getStringExtra(EXTRA_TITLE);
		if (path == null || !new File(path).isFile()) {
			fatal("No encuentro el juego", "El archivo ya no está en la app. Vuelve a añadirlo en el catálogo.");
			return;
		}
		rom = new File(path);
		set = ArcadeLibrary.setName(rom.getName());
		if (title == null || title.isEmpty()) {
			title = ArcadeLibrary.title(set);
		}
		File base = new File(getFilesDir(), "arcade");
		File system = new File(base, "system");
		saves = new File(base, "saves");
		states = new File(base, "states");
		for (File d : new File[] { system, saves, states }) {
			if (!d.isDirectory() && !d.mkdirs()) {
				Log.w(TAG, "Could not create " + d);
			}
		}

		File coreFile = new File(getApplicationInfo().nativeLibraryDir, CORE);
		GLRetroViewData data = new GLRetroViewData(this);
		data.setCoreFilePath(coreFile.isFile() ? coreFile.getAbsolutePath() : CORE);
		data.setGameFilePath(rom.getAbsolutePath());
		data.setSystemDirectory(system.getAbsolutePath());
		data.setSavesDirectory(saves.getAbsolutePath());
		data.setVariables(variables());
		data.setShader(shader(prefs.getString("shader", "sharp")));
		data.setPreferLowLatencyAudio(true);
		data.setRumbleEventsEnabled(false);
		byte[] sram = readFile(sramFile());
		if (sram != null && sram.length > 0) {
			data.setSaveRAMState(sram);
		}

		retro = new GLRetroView(this, data);
		getLifecycle().addObserver(retro);
		root.addView(retro, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

		pad = new TouchPadView(this);
		pad.setListener(this);
		applyPadSettings();
		root.addView(pad, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		status = new TextView(this);
		status.setText("Cargando " + title + "…");
		status.setTextColor(0xFFE8E8EE);
		status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
		status.setGravity(Gravity.CENTER);
		root.addView(status, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

		FlowKt.launchIn(FlowKt.onEach(retro.getGLRetroErrors(), (code, cont) -> {
			handler.post(() -> onRetroError(code == null ? GLRetroView.ERROR_GENERIC : code));
			return Unit.INSTANCE;
		}), GlobalScope.INSTANCE);
		FlowKt.launchIn(FlowKt.onEach(retro.getGLRetroEvents(), (event, cont) -> {
			if (event instanceof GLRetroView.GLRetroEvents.FrameRendered && !started) {
				handler.post(this::onFirstFrame);
			}
			return Unit.INSTANCE;
		}), GlobalScope.INSTANCE);
	}

	private Variable[] variables() {
		boolean aes = "aes".equals(prefs.getString("neogeo_mode", "mvs"));
		return new Variable[] {
			// MVS (arcade, with coins) or AES (the home console: no coins, saves on the memory card).
			new Variable("fbneo-neogeo-mode", aes ? "AES_EUR" : "DIPSWITCH", null),
			new Variable("fbneo-memcard-mode", aes ? "per-game" : "disabled", null),
			// Holding Start must not open the cabinet's service menu.
			new Variable("fbneo-diagnostic-input", "None", null),
		};
	}

	static ShaderConfig shader(String id) {
		switch (id) {
			case "cut":
				return new ShaderConfig.CUT2();
			case "crt":
				return ShaderConfig.CRT.INSTANCE;
			case "lcd":
				return ShaderConfig.LCD.INSTANCE;
			case "none":
				return ShaderConfig.Default.INSTANCE;
			default:
				return ShaderConfig.Sharp.INSTANCE;
		}
	}

	private void applyPadSettings() {
		pad.setButtons(
			new TouchPadView.Spec[] { new TouchPadView.Spec("A", C_A, KEY_A), new TouchPadView.Spec("C", C_C, KEY_C) },
			new TouchPadView.Spec[] { new TouchPadView.Spec("B", C_B, KEY_B), new TouchPadView.Spec("D", C_D, KEY_D) },
			new TouchPadView.Spec[] {
				new TouchPadView.Spec("AB", C_SHORT, KEY_A, KEY_B),
				new TouchPadView.Spec("CD", C_SHORT, KEY_C, KEY_D),
				new TouchPadView.Spec("BC", C_SHORT, KEY_B, KEY_C),
				new TouchPadView.Spec("ABC", C_SHORT, KEY_A, KEY_B, KEY_C),
			},
			new TouchPadView.Spec[] {
				new TouchPadView.Spec("COIN", 0xFFE8E8EE, KEY_COIN),
				new TouchPadView.Spec("START", 0xFFE8E8EE, KEY_START),
			});
		pad.setStick("stick".equals(prefs.getString("move", "dpad")));
		pad.setSizePercent(prefs.getInt("size", 100));
		pad.setOpacity(prefs.getFloat("opacity", 0.85f));
		pad.setVisibility(prefs.getBoolean("hide_pad", false) ? View.GONE : View.VISIBLE);
	}

	private void onFirstFrame() {
		if (started || failed) {
			return;
		}
		started = true;
		status.setVisibility(View.GONE);
	}

	private void onRetroError(int code) {
		if (code == GLRetroView.ERROR_SERIALIZATION) {
			Toast.makeText(this, "No se pudo cargar el estado guardado", Toast.LENGTH_LONG).show();
			return;
		}
		if (failed) {
			return;
		}
		failed = true;
		String msg;
		if (code == GLRetroView.ERROR_LOAD_LIBRARY) {
			msg = "No se pudo cargar el emulador FBNeo en este teléfono.";
		} else if (code == GLRetroView.ERROR_LOAD_GAME) {
			msg = "FBNeo no pudo abrir «" + rom.getName() + "».\n\n"
				+ "• El ZIP tiene que ser el romset que espera FBNeo (las versiones actuales de FBNeo o MAME suelen servir; los muy viejos no).\n"
				+ (ArcadeLibrary.looksNeoGeo(set) && !ArcadeLibrary.hasNeoGeoBios(this)
					? "• Falta la BIOS de Neo Geo: añade también neogeo.zip en el catálogo.\n"
					: "• Si es de Neo Geo, neogeo.zip tiene que estar en el catálogo.\n")
				+ "• Si es un hack o una versión (Magic Plus, Plus…), añade también el juego original (por ejemplo kof2002.zip).";
		} else if (code == GLRetroView.ERROR_GL_NOT_COMPATIBLE) {
			msg = "La GPU de este teléfono no es compatible con el visor del emulador.";
		} else {
			msg = "El emulador se detuvo con un error.";
		}
		fatal("No se pudo abrir el juego", msg);
	}

	private void fatal(String t, String msg) {
		failed = true;
		if (status != null) {
			status.setVisibility(View.GONE);
		}
		new AlertDialog.Builder(this)
			.setTitle(t)
			.setMessage(msg)
			.setCancelable(false)
			.setPositiveButton("Volver", (d, w) -> exit(false))
			.show();
	}

	// Touch controls

	@Override
	public void onDirection(int x, int y) {
		if (retro != null) {
			retro.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, x, y, 0);
		}
	}

	@Override
	public void onKey(int code, boolean down) {
		if (retro != null) {
			retro.sendKeyEvent(down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, code, 0);
		}
	}

	@Override
	public void onMenu() {
		showMenu();
	}

	// Menu

	private void pauseGame() {
		if (retro != null && !paused) {
			paused = true;
			retro.setAudioEnabled(false);
			retro.onPause();
		}
	}

	private void resumeGame() {
		if (retro != null && paused) {
			paused = false;
			retro.onResume();
			retro.setAudioEnabled(true);
		}
	}

	private File stateFile() {
		return new File(states, set + ".state");
	}

	private File sramFile() {
		return new File(saves, set + ".srm");
	}

	private void showMenu() {
		if (menuOpen || failed || retro == null) {
			return;
		}
		menuOpen = true;
		pad.releaseAll();
		pauseGame();
		File state = stateFile();
		final boolean hasState = state.isFile();
		final boolean stick = "stick".equals(prefs.getString("move", "dpad"));
		final int size = prefs.getInt("size", 100);
		final boolean aes = "aes".equals(prefs.getString("neogeo_mode", "mvs"));
		final boolean hidden = prefs.getBoolean("hide_pad", false);
		String shaderId = prefs.getString("shader", "sharp");
		String shaderName = SHADER_NAMES[0];
		for (int i = 0; i < SHADERS.length; i++) {
			if (SHADERS[i].equals(shaderId)) {
				shaderName = SHADER_NAMES[i];
			}
		}
		final String[] items = {
			"▶  Continuar",
			"💾  Guardar estado",
			hasState ? "📂  Cargar estado (" + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(state.lastModified())) + ")" : "📂  Cargar estado (no hay)",
			"🕹  Movimiento: " + (stick ? "joystick" : "flechas"),
			"🔘  Tamaño de los botones: " + (size < 95 ? "pequeño" : size > 110 ? "grande" : "normal"),
			"👁  Controles en pantalla: " + (hidden ? "ocultos" : "visibles"),
			"🖼  Imagen: " + shaderName,
			"🎮  Neo Geo: " + (aes ? "AES (consola, sin monedas)" : "MVS (arcade, con monedas)"),
			"🔄  Reiniciar el juego",
			"✕  Salir al catálogo",
		};
		new AlertDialog.Builder(this)
			.setTitle(title)
			.setItems(items, (d, which) -> {
				switch (which) {
					case 1:
						saveState();
						break;
					case 2:
						if (hasState) {
							loadState();
						} else {
							Toast.makeText(this, "Todavía no guardaste un estado de este juego", Toast.LENGTH_SHORT).show();
						}
						break;
					case 3:
						prefs.edit().putString("move", stick ? "dpad" : "stick").apply();
						applyPadSettings();
						break;
					case 4:
						prefs.edit().putInt("size", size < 95 ? 100 : size > 110 ? 85 : 120).apply();
						applyPadSettings();
						break;
					case 5:
						prefs.edit().putBoolean("hide_pad", !hidden).apply();
						applyPadSettings();
						if (!hidden) {
							Toast.makeText(this, "Para volver a verlos, abre este menú con el botón Atrás", Toast.LENGTH_LONG).show();
						}
						break;
					case 6:
						pickShader();
						return;
					case 7:
						prefs.edit().putString("neogeo_mode", aes ? "mvs" : "aes").apply();
						Toast.makeText(this, "Se aplica la próxima vez que abras el juego", Toast.LENGTH_LONG).show();
						break;
					case 8:
						retro.reset(false);
						break;
					case 9:
						exit(true);
						return;
					default:
						break;
				}
				closeMenu();
			})
			.setOnCancelListener(d -> closeMenu())
			.show();
	}

	private void pickShader() {
		String current = prefs.getString("shader", "sharp");
		int checked = 0;
		for (int i = 0; i < SHADERS.length; i++) {
			if (SHADERS[i].equals(current)) {
				checked = i;
			}
		}
		new AlertDialog.Builder(this)
			.setTitle("Imagen")
			.setSingleChoiceItems(SHADER_NAMES, checked, (d, which) -> {
				prefs.edit().putString("shader", SHADERS[which]).apply();
				retro.setShader(shader(SHADERS[which]));
				d.dismiss();
				closeMenu();
			})
			.setOnCancelListener(d -> closeMenu())
			.show();
	}

	private void closeMenu() {
		menuOpen = false;
		resumeGame();
		goFullscreen();
	}

	private void saveState() {
		if (!started) {
			return;
		}
		try {
			// The game is paused (GL thread stopped), so the core can be called from here.
			byte[] data = retro.serializeState(false);
			if (data == null || data.length == 0) {
				throw new IOException("estado vacío");
			}
			writeFile(stateFile(), data);
			Toast.makeText(this, "Estado guardado", Toast.LENGTH_SHORT).show();
		} catch (Throwable t) {
			Log.w(TAG, "Save state", t);
			Toast.makeText(this, "No se pudo guardar el estado", Toast.LENGTH_LONG).show();
		}
	}

	private void loadState() {
		if (!started) {
			return;
		}
		byte[] data = readFile(stateFile());
		boolean ok = false;
		try {
			ok = data != null && retro.unserializeState(data, false);
		} catch (Throwable t) {
			Log.w(TAG, "Load state", t);
		}
		Toast.makeText(this, ok ? "Estado cargado" : "No se pudo cargar el estado", Toast.LENGTH_SHORT).show();
	}

	private void saveSram() {
		if (!started || failed || retro == null) {
			return;
		}
		try {
			byte[] sram = retro.serializeSRAM(false);
			if (sram != null && sram.length > 0) {
				writeFile(sramFile(), sram);
			}
		} catch (Throwable t) {
			Log.w(TAG, "SRAM", t);
		}
	}

	private void exit(boolean save) {
		if (save) {
			pauseGame();
			saveSram();
		}
		finish();
	}

	@Override
	protected void onPause() {
		if (pad != null) {
			pad.releaseAll();
		}
		super.onPause();
		if (started && !failed) {
			saveSram();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		goFullscreen();
		// The view resumes itself with the activity: keep the game paused while the menu is open.
		handler.post(() -> {
			if (menuOpen && retro != null) {
				paused = false;
				pauseGame();
			}
		});
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		// The core keeps global state: this process (":arcade") ends with the game.
		handler.postDelayed(() -> android.os.Process.killProcess(android.os.Process.myPid()), 400);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			goFullscreen();
		} else if (pad != null) {
			pad.releaseAll();
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
			showMenu();
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@SuppressWarnings("deprecation")
	@Override
	public void onBackPressed() {
		showMenu();
	}

	@SuppressWarnings("deprecation")
	private void goFullscreen() {
		Window w = getWindow();
		w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		if (Build.VERSION.SDK_INT >= 28) {
			WindowManager.LayoutParams lp = w.getAttributes();
			lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
			w.setAttributes(lp);
		}
		if (Build.VERSION.SDK_INT >= 30) {
			w.setDecorFitsSystemWindows(false);
			WindowInsetsController c = w.getInsetsController();
			if (c != null) {
				c.hide(WindowInsets.Type.systemBars());
				c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
			}
		} else {
			w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
				| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
				| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
		}
	}

	static byte[] readFile(File f) {
		if (f == null || !f.isFile()) {
			return null;
		}
		try (InputStream in = new FileInputStream(f)) {
			byte[] out = new byte[(int) f.length()];
			int off = 0;
			while (off < out.length) {
				int n = in.read(out, off, out.length - off);
				if (n < 0) {
					break;
				}
				off += n;
			}
			return out;
		} catch (IOException e) {
			Log.w(TAG, "Read " + f, e);
			return null;
		}
	}

	static void writeFile(File f, byte[] data) throws IOException {
		File tmp = new File(f.getPath() + ".tmp");
		try (OutputStream out = new FileOutputStream(tmp)) {
			out.write(data);
		}
		if (f.exists() && !f.delete()) {
			throw new IOException("No se pudo reemplazar " + f);
		}
		if (!tmp.renameTo(f)) {
			throw new IOException("No se pudo guardar " + f);
		}
	}
}
