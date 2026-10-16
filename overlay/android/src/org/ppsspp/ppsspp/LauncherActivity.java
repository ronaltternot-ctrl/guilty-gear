package org.ppsspp.ppsspp;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Home screen of the app: pick the game image, choose rendering resolution (up to 4K),
 * controls and graphics options, then boot straight into the game.
 *
 * Runs in the main process. The emulator (PpssppActivity) runs in the ":emu" process and is
 * never initialized here, so this screen stays light and can tell whether a game is open.
 */
public class LauncherActivity extends Activity {
	private static final String TAG = "AcPlusLauncher";
	private static final int REQUEST_PICK_GAME = 4201;

	private static final String PREFS = "launcher";
	private static final String K_URI = "game_uri";
	private static final String K_NAME = "game_name";
	private static final String K_TITLE = "game_title";
	private static final String K_ID = "game_id";
	private static final String K_FORMAT = "game_format";
	private static final String K_INSPECTED = "game_inspected";
	private static final String K_RES = "resolution";
	private static final String K_TOUCH = "touch_controls";
	private static final String K_BACKEND = "backend";
	private static final String K_FPS = "show_fps";
	private static final String K_STYLE = "button_style";
	private static final String K_APPLIED_STYLE = "applied_button_style";
	private static final String K_EFFECT = "visual_effect";
	private static final String K_PROFILES = "character_profiles";
	private static final String K_SIDE = "player_side";
	private static final String K_CHAR_STYLE = "char_style_";

	private static final String BACKEND_VULKAN = "vulkan";
	private static final String BACKEND_OPENGL = "opengl";

	static final String STYLE_PSP = "psp";
	static final String STYLE_ARCADE = "arcade";

	/** Character auto-detection reads memory addresses of the US version only. */
	private static final String PROFILE_GAME_ID = "ULUS10409";

	/**
	 * Internal character numbers of the GGXX series (index = number). Profiles are saved by
	 * number, so they keep working even if a name here doesn't match the game's order.
	 */
	static final String[] CHARACTERS = {
		"", "Sol", "Ky", "May", "Millia", "Axl", "Potemkin", "Chipp", "Eddie", "Baiken", "Faust",
		"Testament", "Jam", "Anji", "Johnny", "Venom", "Dizzy", "Slayer", "I-No", "Zappa",
		"Bridget", "Robo-Ky", "A.B.A", "Order-Sol", "Kliff", "Justice",
	};

	/** A post-processing look: PPSSPP shader section names, applied as a chain. */
	private static final class Effect {
		final String id;
		final String label;
		final String description;
		final String[] shaders;
		final int preview;

		Effect(String id, String label, String description, int preview, String... shaders) {
			this.id = id;
			this.label = label;
			this.description = description;
			this.preview = preview;
			this.shaders = shaders;
		}
	}

	private static final Effect[] EFFECTS = {
		new Effect("none", "Original",
			"Sin filtro. El juego se dibuja a la resolución que elijas arriba.",
			R.drawable.fx_original),
		new Effect("arcade", "Arcade",
			"Monitor de recreativa: scanlines marcadas, rejilla RGB, brillo y una curvatura suave.",
			R.drawable.fx_arcade, "AcArcade"),
		new Effect("crt", "CRT",
			"Televisor de tubo: imagen más suave, máscara de puntos y cristal curvo.",
			R.drawable.fx_crt, "AcCrtTv"),
		new Effect("scanlines", "Scanlines",
			"Píxeles nítidos con líneas de barrido, sin curvatura.",
			R.drawable.fx_scanlines, "AcScanlines"),
		new Effect("ia", "IA",
			"Red neuronal NNEDI3: duplica la resolución suavizando los bordes en escalera. Exigente: para teléfonos de gama media-alta.",
			R.drawable.fx_ia, "AcNnedi3"),
		new Effect("ia_rgb", "IA máx.",
			"NNEDI3 en cada color por separado: la mejor calidad, unas 3 veces más pesado que «IA». Solo para teléfonos potentes.",
			R.drawable.fx_ia_rgb, "AcNnedi3Rgb"),
		new Effect("xbr", "xBR",
			"Reescalado clásico para pixel art: contornos limpios y bien definidos. Ligero.",
			R.drawable.fx_xbr, "5xBR-lv2"),
		new Effect("fsr", "FSR",
			"AMD FidelityFX: reescalado nítido y muy ligero.",
			R.drawable.fx_fsr, "FSR-EASU", "FSR-RCAS"),
	};

	/** PSP screen is 480x272; 8x gives 3840x2176 (4K). */
	private static final int PSP_W = 480;
	private static final int PSP_H = 272;
	private static final int MAX_RES = 8;

	// Palette
	private static final int C_BG = 0xFF0D0D11;
	private static final int C_CARD = 0xFF16161C;
	private static final int C_STROKE = 0xFF272730;
	private static final int C_ACCENT = 0xFFD7263D;
	private static final int C_TEXT = 0xFFF2F2F5;
	private static final int C_MUTED = 0xFF9A9AA8;
	private static final int C_OK = 0xFF3FB950;
	private static final int C_WARN = 0xFFE3B341;
	private static final int C_CHIP_STROKE = 0xFF3A3A46;

	private final Handler handler = new Handler(Looper.getMainLooper());
	private SharedPreferences prefs;
	private int recommendedRes;
	private String inspectingUri;

	private TextView gameStatus;
	private TextView gameDetail;
	private final TextView[] resChips = new TextView[MAX_RES];
	private TextView resInfo;
	private TextView resHint;
	private TextView backendVulkan;
	private TextView backendOpenGl;
	private Switch touchSwitch;
	private Switch fpsSwitch;
	private TextView stylePsp;
	private TextView styleArcade;
	private TextView styleDesc;
	private ControlsPreviewView controlsPreview;
	private final TextView[] effectChips = new TextView[EFFECTS.length];
	private ImageView effectPreview;
	private TextView effectDesc;
	private Switch profilesSwitch;
	private LinearLayout profilesOptions;
	private TextView side1p;
	private TextView side2p;
	private TextView profilesStatus;
	private LinearLayout runningCard;
	private TextView playButton;

	private final Runnable refreshRunningTask = this::refreshRunning;

	// -------------------------------------------------------------------------------------------
	// Lifecycle

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
		recommendedRes = computeRecommendedResolution();
		if (!prefs.contains(K_RES)) {
			prefs.edit().putInt(K_RES, recommendedRes).apply();
		}
		if (!prefs.contains(K_BACKEND)) {
			prefs.edit().putString(K_BACKEND, defaultBackend()).apply();
		}
		setContentView(buildUi());
	}

	@Override
	protected void onResume() {
		super.onResume();
		refreshAll();
		// When coming back from a game, the emulator process takes a moment to exit.
		handler.removeCallbacks(refreshRunningTask);
		handler.postDelayed(refreshRunningTask, 700);
	}

	@Override
	protected void onDestroy() {
		handler.removeCallbacksAndMessages(null);
		super.onDestroy();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode != REQUEST_PICK_GAME || resultCode != RESULT_OK || data == null || data.getData() == null) {
			return;
		}
		Uri uri = data.getData();
		ContentResolver resolver = getContentResolver();
		try {
			resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
		} catch (SecurityException e) {
			Log.w(TAG, "Could not persist permission for " + uri, e);
		}

		String old = prefs.getString(K_URI, null);
		if (old != null && !old.equals(uri.toString())) {
			try {
				resolver.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION);
			} catch (SecurityException e) {
				// It was not persisted anymore. Nothing to release.
			}
		}

		String name = queryDisplayName(uri);
		prefs.edit()
			.putString(K_URI, uri.toString())
			.putString(K_NAME, name)
			.remove(K_TITLE)
			.remove(K_ID)
			.remove(K_FORMAT)
			.putBoolean(K_INSPECTED, false)
			.apply();
		inspectingUri = null;
		refreshGame();
	}

	// -------------------------------------------------------------------------------------------
	// Actions

	private void pickGame() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
		try {
			startActivityForResult(intent, REQUEST_PICK_GAME);
		} catch (ActivityNotFoundException e) {
			showMessage("Sin selector de archivos",
				"Tu teléfono no tiene un selector de archivos disponible. Instala o activa la app «Archivos» e inténtalo de nuevo.");
		}
	}

	private void onPlayPressed() {
		if (findEmuPid() > 0) {
			resumeGame();
			return;
		}
		String stored = prefs.getString(K_URI, null);
		if (stored == null) {
			pickGame();
			return;
		}
		Uri uri = Uri.parse(stored);
		if (!hasReadAccess(uri) || !canOpen(uri)) {
			new AlertDialog.Builder(this)
				.setTitle("No encuentro el juego")
				.setMessage("No puedo abrir el archivo que elegiste. Puede que se haya movido o borrado. Elígelo de nuevo.")
				.setPositiveButton("Elegir archivo", (d, w) -> pickGame())
				.setNegativeButton("Cancelar", null)
				.show();
			return;
		}
		try {
			writeEmulatorConfig();
		} catch (IOException e) {
			Log.e(TAG, "Failed to write config", e);
			showMessage("Error", "No se pudo preparar la configuración del juego: " + e.getMessage());
			return;
		}
		startActivity(emulatorIntent(uri));
	}

	private void resumeGame() {
		String stored = prefs.getString(K_URI, null);
		Intent intent = emulatorIntent(stored == null ? null : Uri.parse(stored));
		startActivity(intent);
	}

	private void confirmCloseGame() {
		new AlertDialog.Builder(this)
			.setTitle("¿Cerrar la partida?")
			.setMessage("Se cerrará el juego abierto. Lo que no hayas guardado se perderá.")
			.setPositiveButton("Cerrar partida", (d, w) -> {
				int pid = findEmuPid();
				if (pid > 0) {
					android.os.Process.killProcess(pid);
				}
				handler.removeCallbacks(refreshRunningTask);
				handler.postDelayed(refreshRunningTask, 400);
			})
			.setNegativeButton("Cancelar", null)
			.show();
	}

	private Intent emulatorIntent(Uri game) {
		Intent intent = new Intent();
		intent.setClassName(getPackageName(), LaunchContract.EMU_ACTIVITY);
		intent.putExtra(LaunchContract.EXTRA_FROM_LAUNCHER, true);
		intent.putExtra(LaunchContract.EXTRA_FLAGS,
			"--pause-menu-exit \"--appendconfig=" + configFile().getAbsolutePath() + "\"");
		if (game != null) {
			intent.putExtra(LaunchContract.EXTRA_GAME, game.toString());
		}
		return intent;
	}

	// -------------------------------------------------------------------------------------------
	// Emulator configuration

	private File configFile() {
		return new File(getFilesDir(), "launcher.ini");
	}

	/**
	 * Points the emulator's memory stick at app storage (so it never asks for a folder) and
	 * writes the settings chosen here as an ini that PPSSPP applies on top of its own config.
	 */
	private File memstickDir() {
		File memstick = getExternalFilesDir(null);
		return memstick != null ? memstick : new File(getFilesDir(), "memstick");
	}

	/** Per-character layouts saved by the emulator (see UI/AcPlusProfiles.h). */
	private File profilesDir() {
		return new File(memstickDir(), "PSP/SYSTEM/acplus_profiles");
	}

	private File savedProfile(int character) {
		return new File(profilesDir(), String.format(Locale.ROOT, "char_%02d.ini", character));
	}

	private void writeEmulatorConfig() throws IOException {
		File memstick = memstickDir();
		String[] dirs = { "PSP/SYSTEM", "PSP/SAVEDATA", "PSP/PPSSPP_STATE", "PSP/GAME" };
		for (String dir : dirs) {
			File f = new File(memstick, dir);
			if (!f.isDirectory() && !f.mkdirs()) {
				throw new IOException("No se pudo crear " + f.getAbsolutePath());
			}
		}
		writeText(new File(getFilesDir(), "memstick_dir.txt"), memstick.getAbsolutePath());

		int res = clampRes(prefs.getInt(K_RES, recommendedRes));
		boolean vulkan = BACKEND_VULKAN.equals(prefs.getString(K_BACKEND, defaultBackend()));
		boolean touch = prefs.getBoolean(K_TOUCH, true);
		boolean fps = prefs.getBoolean(K_FPS, false);

		String style = prefs.getString(K_STYLE, STYLE_PSP);
		boolean arcade = STYLE_ARCADE.equals(style);
		// Only rewrite the button layout when the button type changes, so that buttons moved
		// with the game's own layout editor stay where the player put them.
		boolean writeLayout = !style.equals(prefs.getString(K_APPLIED_STYLE, ""));

		StringBuilder ini = new StringBuilder();
		ini.append("[General]\n")
			.append("CheckForNewVersion = False\n")
			.append("[Graphics]\n")
			.append("InternalResolution = ").append(res).append('\n')
			.append("GraphicsBackend = ").append(vulkan ? 3 : 0).append('\n')
			.append("iShowStatusFlags = ").append(fps ? 2 : 0).append('\n')
			.append("[Control]\n")
			.append("ShowTouchControls = ").append(touch ? "True" : "False").append('\n');
		if (writeLayout && arcade) {
			appendArcadeButtonDefinitions(ini);
		}
		ini.append("[TouchControls.Landscape]\n");
		// Fast-forward isn't a PSP or arcade button: never show it.
		ini.append("ShowTouchUnthrottle = False\n");
		if (writeLayout) {
			appendTouchLayout(ini, arcade);
		}
		ini.append("[TouchControls.Portrait]\n")
			.append("ShowTouchUnthrottle = False\n");

		// Post-processing effect (needs patches/0003 so the appended config can set it).
		Effect effect = currentEffect();
		ini.append("[PostShaderList]\n");
		if (effect.shaders.length == 0) {
			ini.append("PostShader1 = Off\n");
		}
		for (int i = 0; i < effect.shaders.length; i++) {
			ini.append("PostShader").append(i + 1).append(" = ").append(effect.shaders[i]).append('\n');
		}
		writeText(configFile(), ini.toString());
		prefs.edit().putString(K_APPLIED_STYLE, style).apply();

		writeProfileConfig();
	}

	/** Settings and the two layout presets the emulator uses for per-character profiles. */
	private void writeProfileConfig() throws IOException {
		for (String preset : new String[] { STYLE_PSP, STYLE_ARCADE }) {
			boolean arcade = STYLE_ARCADE.equals(preset);
			StringBuilder layout = new StringBuilder("[Control]\n");
			if (arcade) {
				appendArcadeButtonDefinitions(layout);
			}
			layout.append("[TouchControls.Landscape]\n").append("ShowTouchUnthrottle = False\n");
			appendTouchLayout(layout, arcade);
			writeText(new File(getFilesDir(), "acplus_layout_" + preset + ".ini"), layout.toString());
		}

		StringBuilder ini = new StringBuilder();
		ini.append("[Profiles]\n")
			.append("Enabled = ").append(prefs.getBoolean(K_PROFILES, true) ? "True" : "False").append('\n')
			.append("Side = ").append(prefs.getInt(K_SIDE, 0)).append('\n')
			.append("DefaultStyle = ").append(prefs.getString(K_STYLE, STYLE_PSP)).append('\n')
			.append("[Characters]\n");
		for (int id = 1; id < CHARACTERS.length; id++) {
			String s = prefs.getString(K_CHAR_STYLE + id, null);
			if (STYLE_PSP.equals(s) || STYLE_ARCADE.equals(s)) {
				ini.append(id).append(" = ").append(s).append('\n');
			}
		}
		writeText(new File(getFilesDir(), "acplus.ini"), ini.toString());
	}

	// -------------------------------------------------------------------------------------------
	// Touch button layouts

	/**
	 * The game's own PSP mapping: □ = P, ✕ = K, △ = S, ○ = HS, R = Dust, L = Respect.
	 * Bits follow PPSSPP's CustomKeyData::g_customKeyList order.
	 */
	private static final long KEY_SQUARE = 1L;
	private static final long KEY_TRIANGLE = 1L << 1;
	private static final long KEY_CIRCLE = 1L << 2;
	private static final long KEY_CROSS = 1L << 3;
	private static final long KEY_R = 1L << 11;

	/** Label image indices appended to PPSSPP's customKeyImages (patches/0002). */
	private static final int IMG_P = 36;
	private static final int IMG_K = 37;
	private static final int IMG_S = 38;
	private static final int IMG_HS = 39;
	private static final int IMG_D = 40;

	/** One arcade button: label, PSP button it presses, and its spot on screen (fractions). */
	private static final class ArcadeButton {
		final int image;
		final long key;
		final float x;
		final float y;

		ArcadeButton(int image, long key, float x, float y) {
			this.image = image;
			this.key = key;
			this.x = x;
			this.y = y;
		}
	}

	/**
	 * Classic Guilty Gear cabinet panel: K S HS on the top row, P and D on the bottom row
	 * (the empty middle spot is where the arcade had Respect).
	 */
	static final float[][] ARCADE_POSITIONS = {
		{ 0.745f, 0.575f },  // K
		{ 0.845f, 0.545f },  // S
		{ 0.945f, 0.575f },  // HS
		{ 0.730f, 0.815f },  // P
		{ 0.930f, 0.815f },  // D
	};

	private static final ArcadeButton[] ARCADE_BUTTONS = {
		new ArcadeButton(IMG_K, KEY_CROSS, ARCADE_POSITIONS[0][0], ARCADE_POSITIONS[0][1]),
		new ArcadeButton(IMG_S, KEY_TRIANGLE, ARCADE_POSITIONS[1][0], ARCADE_POSITIONS[1][1]),
		new ArcadeButton(IMG_HS, KEY_CIRCLE, ARCADE_POSITIONS[2][0], ARCADE_POSITIONS[2][1]),
		new ArcadeButton(IMG_P, KEY_SQUARE, ARCADE_POSITIONS[3][0], ARCADE_POSITIONS[3][1]),
		new ArcadeButton(IMG_D, KEY_R, ARCADE_POSITIONS[4][0], ARCADE_POSITIONS[4][1]),
	};

	private static final float BUTTON_SCALE = 1.15f;

	private static void appendArcadeButtonDefinitions(StringBuilder ini) {
		for (int i = 0; i < ARCADE_BUTTONS.length; i++) {
			ArcadeButton b = ARCADE_BUTTONS[i];
			ini.append("Custom").append(i).append("Mapping = ")
				.append(String.format(Locale.ROOT, "0x%016x", b.key)).append('\n')
				.append("Custom").append(i).append("Image = ").append(b.image).append('\n')
				.append("Custom").append(i).append("Shape = 0\n")  // Round
				.append("Custom").append(i).append("Toggle = False\n")
				.append("Custom").append(i).append("Repeat = False\n");
		}
	}

	private static void appendTouchLayout(StringBuilder ini, boolean arcade) {
		String psp = arcade ? "False" : "True";
		ini.append("ShowTouchCircle = ").append(psp).append('\n')
			.append("ShowTouchCross = ").append(psp).append('\n')
			.append("ShowTouchSquare = ").append(psp).append('\n')
			.append("ShowTouchTriangle = ").append(psp).append('\n')
			// In arcade mode R (Dust) becomes the D button. L (Respect) stays.
			.append("ShowTouchRTrigger = ").append(psp).append('\n');
		if (arcade) {
			// Move Start/Select toward the middle so they don't crowd the P button.
			appendPos(ini, "StartKey", 0.56f, 0.91f, BUTTON_SCALE);
			appendPos(ini, "SelectKey", 0.44f, 0.91f, BUTTON_SCALE);
			for (int i = 0; i < ARCADE_BUTTONS.length; i++) {
				ArcadeButton b = ARCADE_BUTTONS[i];
				ini.append(String.format(Locale.ROOT, "fcombo%dX = %.4f\n", i, b.x))
					.append(String.format(Locale.ROOT, "fcombo%dY = %.4f\n", i, b.y))
					.append(String.format(Locale.ROOT, "comboKeyScale%d = %.4f\n", i, BUTTON_SCALE))
					.append("ShowComboKey").append(i).append(" = True\n");
			}
		} else {
			// -1 tells PPSSPP to put these back in their default spots.
			ini.append("StartKeyX = -1.0\nStartKeyY = -1.0\n")
				.append("SelectKeyX = -1.0\nSelectKeyY = -1.0\n");
			for (int i = 0; i < ARCADE_BUTTONS.length; i++) {
				ini.append("ShowComboKey").append(i).append(" = False\n");
			}
		}
	}

	private static void appendPos(StringBuilder ini, String name, float x, float y, float scale) {
		ini.append(String.format(Locale.ROOT, "%sX = %.4f\n", name, x))
			.append(String.format(Locale.ROOT, "%sY = %.4f\n", name, y))
			.append(String.format(Locale.ROOT, "%sScale = %.4f\n", name, scale));
	}

	private static void writeText(File file, String text) throws IOException {
		File tmp = new File(file.getPath() + ".tmp");
		try (OutputStream out = new FileOutputStream(tmp)) {
			out.write(text.getBytes(StandardCharsets.UTF_8));
		}
		if (!tmp.renameTo(file)) {
			// renameTo can fail if the target exists on some filesystems.
			if (!file.delete() || !tmp.renameTo(file)) {
				throw new IOException("No se pudo escribir " + file.getName());
			}
		}
	}

	// -------------------------------------------------------------------------------------------
	// State helpers

	private int findEmuPid() {
		ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		if (am == null) {
			return -1;
		}
		List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
		if (processes == null) {
			return -1;
		}
		String name = getPackageName() + LaunchContract.EMU_PROCESS_SUFFIX;
		for (ActivityManager.RunningAppProcessInfo p : processes) {
			if (name.equals(p.processName)) {
				return p.pid;
			}
		}
		return -1;
	}

	private boolean hasReadAccess(Uri uri) {
		if (!"content".equals(uri.getScheme())) {
			return true;
		}
		for (UriPermission p : getContentResolver().getPersistedUriPermissions()) {
			if (p.getUri().equals(uri) && p.isReadPermission()) {
				return true;
			}
		}
		return false;
	}

	private boolean canOpen(Uri uri) {
		try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r")) {
			return pfd != null;
		} catch (Exception e) {
			Log.w(TAG, "Cannot open " + uri, e);
			return false;
		}
	}

	private String queryDisplayName(Uri uri) {
		try (Cursor c = getContentResolver().query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
			if (c != null && c.moveToFirst() && !c.isNull(0)) {
				return c.getString(0);
			}
		} catch (Exception e) {
			Log.w(TAG, "Could not query name for " + uri, e);
		}
		String last = uri.getLastPathSegment();
		return last != null ? last : "juego";
	}

	@SuppressWarnings("deprecation")
	private int computeRecommendedResolution() {
		DisplayMetrics dm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getRealMetrics(dm);
		int longSide = Math.max(dm.widthPixels, dm.heightPixels);
		return Math.max(2, Math.min(MAX_RES, Math.round(longSide / (float) PSP_W)));
	}

	private static String defaultBackend() {
		// Same rule PPSSPP uses: Vulkan on Android 8.1+ with a 64-bit CPU.
		boolean is64 = Build.SUPPORTED_64_BIT_ABIS != null && Build.SUPPORTED_64_BIT_ABIS.length > 0;
		return (Build.VERSION.SDK_INT >= 27 && is64) ? BACKEND_VULKAN : BACKEND_OPENGL;
	}

	private static int clampRes(int r) {
		return Math.max(1, Math.min(MAX_RES, r));
	}

	private void startInspection(final Uri uri) {
		final String key = uri.toString();
		if (key.equals(inspectingUri)) {
			return;
		}
		inspectingUri = key;
		new Thread(() -> {
			GameInspector.Result result;
			try {
				result = GameInspector.inspect(getApplicationContext(), uri);
			} catch (Throwable t) {
				Log.w(TAG, "Inspection failed", t);
				result = new GameInspector.Result(null, null, null);
			}
			final GameInspector.Result r = result;
			handler.post(() -> {
				if (isFinishing() || isDestroyed() || !key.equals(prefs.getString(K_URI, null))) {
					return;
				}
				prefs.edit()
					.putString(K_TITLE, r.title)
					.putString(K_ID, r.discId)
					.putString(K_FORMAT, r.format)
					.putBoolean(K_INSPECTED, true)
					.apply();
				inspectingUri = null;
				refreshGame();
			});
		}, "GameInspector").start();
	}

	// -------------------------------------------------------------------------------------------
	// Refreshing the UI from preferences

	private void refreshAll() {
		refreshGame();
		refreshResolution();
		refreshBackend();
		refreshStyle();
		refreshEffect();
		refreshProfiles();
		touchSwitch.setChecked(prefs.getBoolean(K_TOUCH, true));
		fpsSwitch.setChecked(prefs.getBoolean(K_FPS, false));
		refreshRunning();
	}

	private void refreshGame() {
		String stored = prefs.getString(K_URI, null);
		if (stored == null) {
			gameStatus.setText("Elige tu copia del juego");
			gameStatus.setTextColor(C_TEXT);
			gameDetail.setText("Archivo ISO o CSO de Guilty Gear XX ΛCore Plus para PSP. Guárdalo en la memoria del teléfono o en la microSD, no en la nube.");
			refreshPlayLabel();
			return;
		}
		Uri uri = Uri.parse(stored);
		String name = prefs.getString(K_NAME, "juego");
		if (!hasReadAccess(uri)) {
			gameStatus.setText("⚠ No tengo acceso al archivo");
			gameStatus.setTextColor(C_WARN);
			gameDetail.setText(name + "\nVuelve a elegirlo con el botón de abajo.");
			refreshPlayLabel();
			return;
		}
		if (!prefs.getBoolean(K_INSPECTED, false)) {
			gameStatus.setText("Comprobando el archivo…");
			gameStatus.setTextColor(C_MUTED);
			gameDetail.setText(name);
			startInspection(uri);
			refreshPlayLabel();
			return;
		}

		GameInspector.Result r = new GameInspector.Result(
			prefs.getString(K_FORMAT, null), prefs.getString(K_ID, null), prefs.getString(K_TITLE, null));
		String id = r.prettyDiscId();
		String format = r.format != null ? r.format : "archivo";
		StringBuilder detail = new StringBuilder();
		if (id != null) {
			detail.append(id).append(" · ");
		}
		detail.append(format).append(" · ").append(name);

		switch (r.match()) {
			case TARGET:
				gameStatus.setText("✓ " + r.title);
				gameStatus.setTextColor(C_OK);
				break;
			case OTHER_GUILTY_GEAR:
				gameStatus.setText("⚠ Otro Guilty Gear: " + r.title);
				gameStatus.setTextColor(C_WARN);
				detail.append("\nEsta app está pensada para ΛCore Plus, pero puedes intentarlo.");
				break;
			case OTHER_GAME:
				gameStatus.setText("⚠ Este archivo es «" + r.title + "»");
				gameStatus.setTextColor(C_WARN);
				detail.append("\nEsta app está pensada para Guilty Gear XX ΛCore Plus, pero puedes intentarlo.");
				break;
			case UNKNOWN:
			default:
				gameStatus.setText("Archivo listo");
				gameStatus.setTextColor(C_TEXT);
				detail.append("\nNo pude identificar el juego dentro del archivo, pero puedes intentarlo.");
				break;
		}
		gameDetail.setText(detail.toString());
		refreshPlayLabel();
	}

	private void refreshResolution() {
		int res = clampRes(prefs.getInt(K_RES, recommendedRes));
		for (int i = 0; i < MAX_RES; i++) {
			styleChip(resChips[i], i + 1 == res);
		}
		resInfo.setText((PSP_W * res) + " × " + (PSP_H * res) + resolutionTag(res));
		String hint;
		if (res == 1) {
			hint = "Resolución original de la PSP. Máximo rendimiento, imagen más pixelada.";
		} else if (res <= 3) {
			hint = "Más nítido y muy ligero. Va bien en casi cualquier teléfono.";
		} else if (res == 4) {
			hint = "Full HD. Buen equilibrio para la mayoría de teléfonos.";
		} else if (res <= 6) {
			hint = "Muy nítido. Ideal para teléfonos de gama media-alta.";
		} else {
			hint = "Ultra, hasta 4K. Necesita un teléfono potente: si notas lentitud, baja un nivel.";
		}
		hint += "\nRecomendada para tu pantalla: " + recommendedRes + "×.";
		Effect effect = currentEffect();
		boolean applies = effect.shaders.length == 0;
		if (!applies) {
			hint = "Con el efecto «" + effect.label + "» el juego se dibuja a su resolución original "
				+ "(480 × 272) y el efecto lo amplía hasta la de tu pantalla, sin tocar los sprites. "
				+ "Para usar esta resolución elige el efecto «Original».";
		}
		for (TextView chip : resChips) {
			chip.setAlpha(applies ? 1f : 0.45f);
		}
		resInfo.setAlpha(applies ? 1f : 0.45f);
		resHint.setText(hint);
	}

	private Effect currentEffect() {
		String id = prefs.getString(K_EFFECT, "none");
		for (Effect e : EFFECTS) {
			if (e.id.equals(id)) {
				return e;
			}
		}
		return EFFECTS[0];
	}

	private void refreshEffect() {
		Effect current = currentEffect();
		for (int i = 0; i < EFFECTS.length; i++) {
			styleChip(effectChips[i], EFFECTS[i] == current);
		}
		effectPreview.setImageResource(current.preview);
		effectPreview.setContentDescription("Vista previa del efecto " + current.label);
		effectDesc.setText(current.description);
	}

	private boolean isProfileGame() {
		String id = prefs.getString(K_ID, null);
		return id == null || PROFILE_GAME_ID.equalsIgnoreCase(id.replace("-", ""));
	}

	private void refreshProfiles() {
		boolean on = prefs.getBoolean(K_PROFILES, true);
		profilesSwitch.setChecked(on);
		int side = prefs.getInt(K_SIDE, 0);
		styleChip(side1p, side == 0);
		styleChip(side2p, side == 1);
		profilesOptions.setAlpha(on ? 1f : 0.45f);
		int saved = 0;
		int custom = 0;
		for (int id = 1; id < CHARACTERS.length; id++) {
			if (savedProfile(id).isFile()) {
				saved++;
			}
			if (prefs.getString(K_CHAR_STYLE + id, null) != null) {
				custom++;
			}
		}
		StringBuilder s = new StringBuilder();
		if (!isProfileGame()) {
			s.append("⚠ Tu copia es ").append(prefs.getString(K_ID, "")).append(". El cambio automático solo funciona con la versión americana (ULUS-10409); con otras versiones se usa siempre el tipo de botones general.\n\n");
		}
		s.append(custom == 0 ? "Todos usan el tipo de botones general." : custom + (custom == 1 ? " personaje tiene" : " personajes tienen") + " un tipo de botones propio.");
		s.append(saved == 0 ? "" : "\n" + saved + (saved == 1 ? " personaje tiene" : " personajes tienen") + " una distribución guardada.");
		profilesStatus.setText(s.toString());
	}

	private String characterStyleLabel(int id) {
		String style = prefs.getString(K_CHAR_STYLE + id, null);
		String label = STYLE_ARCADE.equals(style) ? "Arcade" : STYLE_PSP.equals(style) ? "PSP" : "General";
		if (savedProfile(id).isFile()) {
			label += " · distribución guardada";
		}
		return label;
	}

	private void showCharacterList() {
		String[] rows = new String[CHARACTERS.length - 1];
		for (int id = 1; id < CHARACTERS.length; id++) {
			rows[id - 1] = CHARACTERS[id] + "  —  " + characterStyleLabel(id);
		}
		new AlertDialog.Builder(this)
			.setTitle("Botones por personaje")
			.setItems(rows, (d, which) -> showCharacterOptions(which + 1))
			.setNegativeButton("Cerrar", null)
			.show();
	}

	private void showCharacterOptions(int id) {
		final boolean hasSaved = savedProfile(id).isFile();
		String[] options = hasSaved
			? new String[] { "General (el de la sección Controles)", "PSP", "Arcade", "Borrar la distribución guardada" }
			: new String[] { "General (el de la sección Controles)", "PSP", "Arcade" };
		new AlertDialog.Builder(this)
			.setTitle(CHARACTERS[id])
			.setItems(options, (d, which) -> {
				if (which == 3) {
					deleteSavedProfile(id);
				} else {
					String style = which == 1 ? STYLE_PSP : which == 2 ? STYLE_ARCADE : null;
					String old = prefs.getString(K_CHAR_STYLE + id, null);
					SharedPreferences.Editor e = prefs.edit();
					if (style == null) {
						e.remove(K_CHAR_STYLE + id);
					} else {
						e.putString(K_CHAR_STYLE + id, style);
					}
					e.apply();
					// A new button type replaces the layout saved for this character.
					boolean changed = old == null ? style != null : !old.equals(style);
					if (changed) {
						deleteSavedProfile(id);
					}
				}
				refreshProfiles();
				showCharacterList();
			})
			.setNegativeButton("Atrás", (d, w) -> showCharacterList())
			.show();
	}

	private void deleteSavedProfile(int id) {
		File f = savedProfile(id);
		if (f.isFile() && !f.delete()) {
			Log.w(TAG, "Could not delete " + f);
		}
	}

	private void confirmResetProfiles() {
		new AlertDialog.Builder(this)
			.setTitle("¿Restablecer todos los personajes?")
			.setMessage("Todos volverán al tipo de botones general y se borrarán las distribuciones guardadas por personaje.")
			.setPositiveButton("Restablecer", (d, w) -> {
				SharedPreferences.Editor e = prefs.edit();
				for (int id = 1; id < CHARACTERS.length; id++) {
					e.remove(K_CHAR_STYLE + id);
					deleteSavedProfile(id);
				}
				e.apply();
				refreshProfiles();
			})
			.setNegativeButton("Cancelar", null)
			.show();
	}

	private static String resolutionTag(int res) {
		switch (res) {
			case 1: return " · PSP original";
			case 3: return " · ~720p";
			case 4: return " · ~1080p";
			case 6: return " · ~1440p";
			case 8: return " · 4K";
			default: return "";
		}
	}

	private void refreshBackend() {
		boolean vulkan = BACKEND_VULKAN.equals(prefs.getString(K_BACKEND, defaultBackend()));
		styleChip(backendVulkan, vulkan);
		styleChip(backendOpenGl, !vulkan);
	}

	private void refreshStyle() {
		boolean arcade = STYLE_ARCADE.equals(prefs.getString(K_STYLE, STYLE_PSP));
		styleChip(stylePsp, !arcade);
		styleChip(styleArcade, arcade);
		controlsPreview.setArcade(arcade);
		if (arcade) {
			styleDesc.setText("Como en la máquina de Guilty Gear: K, S y HS arriba; P y D abajo. "
				+ "La cruceta, el stick, L (Respeto), Start y Select siguen ahí.");
		} else {
			styleDesc.setText("Como en la consola: △ ○ ✕ □, L y R, cruceta, stick, Start y Select. "
				+ "En este juego: □ = P, ✕ = K, △ = S, ○ = HS, R = D.");
		}
	}

	private void refreshRunning() {
		if (runningCard == null) {
			return;
		}
		boolean running = findEmuPid() > 0;
		runningCard.setVisibility(running ? View.VISIBLE : View.GONE);
		refreshPlayLabel();
	}

	private void refreshPlayLabel() {
		if (playButton == null) {
			return;
		}
		if (findEmuPid() > 0) {
			playButton.setText("CONTINUAR PARTIDA");
		} else if (prefs.getString(K_URI, null) == null) {
			playButton.setText("ELEGIR JUEGO");
		} else {
			playButton.setText("JUGAR");
		}
	}

	// -------------------------------------------------------------------------------------------
	// UI construction (plain Android views, no XML layouts)

	private View buildUi() {
		final ScrollView scroll = new ScrollView(this);
		scroll.setBackgroundColor(C_BG);
		scroll.setFillViewport(true);
		if (Build.VERSION.SDK_INT >= 35) {
			// Android 15+ draws apps edge-to-edge; keep content clear of the system bars.
			scroll.setOnApplyWindowInsetsListener((v, insets) -> {
				applyInsets(v, insets);
				return insets;
			});
		}

		FrameLayout center = new FrameLayout(this);
		scroll.addView(center, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		LinearLayout col = new LinearLayout(this);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(dp(20), dp(24), dp(20), dp(28));
		int screenDp = (int) (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
		int width = screenDp > 680 ? dp(640) : ViewGroup.LayoutParams.MATCH_PARENT;
		center.addView(col, new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

		// Header
		TextView kicker = text("GUILTY GEAR XX", 13, C_ACCENT, true);
		kicker.setLetterSpacing(0.3f);
		col.addView(kicker);
		TextView title = text("ΛCORE PLUS", 38, C_TEXT, true);
		title.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
		title.setLetterSpacing(0.02f);
		col.addView(title);
		TextView subtitle = text("Edición PSP · listo para jugar en Android", 14, C_MUTED, false);
		col.addView(subtitle);

		// Running game banner
		runningCard = card(C_WARN);
		runningCard.setVisibility(View.GONE);
		runningCard.addView(text("Partida en curso", 16, C_TEXT, true));
		TextView runningText = text("El juego sigue abierto en segundo plano. Los cambios de abajo se aplican la próxima vez que lo inicies.", 13, C_MUTED, false);
		runningText.setPadding(0, dp(4), 0, dp(12));
		runningCard.addView(runningText);
		LinearLayout runningRow = new LinearLayout(this);
		runningRow.setOrientation(LinearLayout.HORIZONTAL);
		TextView resume = filledButton("Continuar", 15);
		resume.setOnClickListener(v -> resumeGame());
		TextView close = outlineButton("Cerrar partida");
		close.setOnClickListener(v -> confirmCloseGame());
		runningRow.addView(resume, weighted(0, dp(6)));
		runningRow.addView(close, weighted(dp(6), 0));
		runningCard.addView(runningRow);
		col.addView(runningCard, cardParams());

		// Game
		LinearLayout game = section(col, "JUEGO");
		gameStatus = text("", 17, C_TEXT, true);
		game.addView(gameStatus);
		gameDetail = text("", 13, C_MUTED, false);
		gameDetail.setPadding(0, dp(4), 0, dp(14));
		game.addView(gameDetail);
		TextView pick = outlineButton("Elegir archivo ISO / CSO");
		pick.setOnClickListener(v -> pickGame());
		game.addView(pick, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

		// Resolution
		LinearLayout resolution = section(col, "RESOLUCIÓN DE RENDERIZADO");
		for (int row = 0; row < 2; row++) {
			LinearLayout chips = new LinearLayout(this);
			chips.setOrientation(LinearLayout.HORIZONTAL);
			for (int c = 0; c < 4; c++) {
				final int level = row * 4 + c + 1;
				TextView chip = chip(level + "×");
				chip.setOnClickListener(v -> {
					prefs.edit().putInt(K_RES, level).apply();
					refreshResolution();
				});
				resChips[level - 1] = chip;
				LinearLayout.LayoutParams lp = weighted(c == 0 ? 0 : dp(4), c == 3 ? 0 : dp(4));
				lp.bottomMargin = dp(8);
				chips.addView(chip, lp);
			}
			resolution.addView(chips);
		}
		resInfo = text("", 18, C_TEXT, true);
		resInfo.setPadding(0, dp(6), 0, 0);
		resolution.addView(resInfo);
		resHint = text("", 13, C_MUTED, false);
		resHint.setPadding(0, dp(4), 0, 0);
		resolution.addView(resHint);

		// Visual effects (post-processing only)
		LinearLayout effects = section(col, "EFECTOS VISUALES");
		for (int row = 0; row * 4 < EFFECTS.length; row++) {
			LinearLayout chips = new LinearLayout(this);
			chips.setOrientation(LinearLayout.HORIZONTAL);
			for (int c = 0; c < 4; c++) {
				int index = row * 4 + c;
				if (index >= EFFECTS.length) {
					chips.addView(new View(this), weighted(dp(4), 0));
					continue;
				}
				final Effect effect = EFFECTS[index];
				TextView chip = chip(effect.label);
				chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
				chip.setOnClickListener(v -> {
					prefs.edit().putString(K_EFFECT, effect.id).apply();
					refreshEffect();
					refreshResolution();
				});
				effectChips[index] = chip;
				LinearLayout.LayoutParams lp = weighted(c == 0 ? 0 : dp(4), c == 3 ? 0 : dp(4));
				lp.bottomMargin = dp(8);
				chips.addView(chip, lp);
			}
			effects.addView(chips);
		}
		effectPreview = new ImageView(this);
		effectPreview.setAdjustViewBounds(true);
		effectPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
		GradientDrawable previewBg = new GradientDrawable();
		previewBg.setColor(Color.BLACK);
		previewBg.setCornerRadius(dp(10));
		effectPreview.setBackground(previewBg);
		effectPreview.setClipToOutline(true);
		LinearLayout.LayoutParams fxParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		fxParams.topMargin = dp(4);
		effects.addView(effectPreview, fxParams);
		TextView previewNote = text("Vista previa con una imagen de ejemplo (no es del juego).", 11, 0xFF6C6C78, false);
		previewNote.setPadding(0, dp(4), 0, 0);
		effects.addView(previewNote);
		effectDesc = text("", 13, C_MUTED, false);
		effectDesc.setPadding(0, dp(8), 0, 0);
		effects.addView(effectDesc);
		TextView fxNote = text("Solo cambian la imagen final: los sprites del juego no se tocan. Puedes afinar cada efecto en el juego: Pausa → Ajustes → Gráficos.", 13, C_MUTED, false);
		fxNote.setPadding(0, dp(8), 0, 0);
		effects.addView(fxNote);

		// Controls
		LinearLayout controls = section(col, "CONTROLES");
		controls.addView(text("Tipo de botones en pantalla", 15, C_TEXT, true));
		LinearLayout styleRow = new LinearLayout(this);
		styleRow.setOrientation(LinearLayout.HORIZONTAL);
		styleRow.setPadding(0, dp(10), 0, dp(10));
		stylePsp = chip("PSP");
		stylePsp.setOnClickListener(v -> {
			prefs.edit().putString(K_STYLE, STYLE_PSP).apply();
			refreshStyle();
		});
		styleArcade = chip("Arcade");
		styleArcade.setOnClickListener(v -> {
			prefs.edit().putString(K_STYLE, STYLE_ARCADE).apply();
			refreshStyle();
		});
		styleRow.addView(stylePsp, weighted(0, dp(4)));
		styleRow.addView(styleArcade, weighted(dp(4), 0));
		controls.addView(styleRow);

		controlsPreview = new ControlsPreviewView(this);
		LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		previewParams.bottomMargin = dp(8);
		controls.addView(controlsPreview, previewParams);
		styleDesc = text("", 13, C_MUTED, false);
		styleDesc.setPadding(0, 0, 0, dp(14));
		controls.addView(styleDesc);

		touchSwitch = switchRow(controls, "Mostrar botones en pantalla",
			"Desactívalo si juegas con un control Bluetooth o USB.");
		touchSwitch.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean(K_TOUCH, checked).apply());
		TextView padNote = text("Los controles funcionan solos: X = P (□), A = K (✕), Y = S (△), B = HS (○), R1 = D y L1 = Respeto. Puedes reasignarlos dentro del juego en Ajustes → Controles.", 13, C_MUTED, false);
		padNote.setPadding(0, dp(12), 0, 0);
		controls.addView(padNote);

		// Per-character profiles
		LinearLayout profiles = section(col, "PERFILES POR PERSONAJE");
		profilesSwitch = switchRow(profiles, "Cambiar los botones según el personaje",
			"Cada personaje recuerda su propia distribución. Si mueves o agrandas botones mientras juegas con alguien (Pausa → Ajustes → Controles → Editar distribución), quedan guardados para ese personaje.");
		profilesSwitch.setOnCheckedChangeListener((b, checked) -> {
			prefs.edit().putBoolean(K_PROFILES, checked).apply();
			refreshProfiles();
		});
		profilesOptions = new LinearLayout(this);
		profilesOptions.setOrientation(LinearLayout.VERTICAL);
		profilesOptions.setPadding(0, dp(14), 0, 0);
		profiles.addView(profilesOptions);

		profilesOptions.addView(text("Tú juegas como", 15, C_TEXT, true));
		LinearLayout sideRow = new LinearLayout(this);
		sideRow.setOrientation(LinearLayout.HORIZONTAL);
		sideRow.setPadding(0, dp(10), 0, dp(4));
		side1p = chip("Jugador 1");
		side1p.setOnClickListener(v -> {
			prefs.edit().putInt(K_SIDE, 0).apply();
			refreshProfiles();
		});
		side2p = chip("Jugador 2");
		side2p.setOnClickListener(v -> {
			prefs.edit().putInt(K_SIDE, 1).apply();
			refreshProfiles();
		});
		sideRow.addView(side1p, weighted(0, dp(4)));
		sideRow.addView(side2p, weighted(dp(4), 0));
		profilesOptions.addView(sideRow);
		TextView sideNote = text("Casi siempre es el jugador 1. Cambia a 2 solo si elegiste personaje con el control del jugador 2.", 13, C_MUTED, false);
		sideNote.setPadding(0, 0, 0, dp(12));
		profilesOptions.addView(sideNote);

		profilesStatus = text("", 13, C_MUTED, false);
		profilesStatus.setPadding(0, 0, 0, dp(12));
		profilesOptions.addView(profilesStatus);
		TextView perCharacter = outlineButton("Elegir botones por personaje");
		perCharacter.setOnClickListener(v -> showCharacterList());
		profilesOptions.addView(perCharacter, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		TextView reset = outlineButton("Restablecer todos");
		reset.setOnClickListener(v -> confirmResetProfiles());
		LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		resetParams.topMargin = dp(8);
		profilesOptions.addView(reset, resetParams);
		TextView profileNote = text("Al empezar el combate verás «Controles: nombre». Solo funciona con la versión americana del juego (ULUS-10409) y en combates individuales.", 13, C_MUTED, false);
		profileNote.setPadding(0, dp(12), 0, 0);
		profilesOptions.addView(profileNote);

		// Graphics
		LinearLayout graphics = section(col, "GRÁFICOS");
		graphics.addView(text("Motor gráfico", 15, C_TEXT, true));
		LinearLayout backendRow = new LinearLayout(this);
		backendRow.setOrientation(LinearLayout.HORIZONTAL);
		backendRow.setPadding(0, dp(10), 0, dp(6));
		backendVulkan = chip("Vulkan");
		backendVulkan.setOnClickListener(v -> {
			prefs.edit().putString(K_BACKEND, BACKEND_VULKAN).apply();
			refreshBackend();
		});
		backendOpenGl = chip("OpenGL");
		backendOpenGl.setOnClickListener(v -> {
			prefs.edit().putString(K_BACKEND, BACKEND_OPENGL).apply();
			refreshBackend();
		});
		backendRow.addView(backendVulkan, weighted(0, dp(4)));
		backendRow.addView(backendOpenGl, weighted(dp(4), 0));
		graphics.addView(backendRow);
		graphics.addView(text("Vulkan suele rendir mejor en los súper ataques llenos de efectos. Si ves fallos gráficos, prueba OpenGL.", 13, C_MUTED, false));
		View spacer = new View(this);
		graphics.addView(spacer, new LinearLayout.LayoutParams(1, dp(14)));
		fpsSwitch = switchRow(graphics, "Mostrar FPS", "Contador de fotogramas por segundo en una esquina.");
		fpsSwitch.setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean(K_FPS, checked).apply());

		// Play
		playButton = filledButton("JUGAR", 20);
		playButton.setLetterSpacing(0.15f);
		playButton.setPadding(dp(16), dp(18), dp(16), dp(18));
		playButton.setOnClickListener(v -> onPlayPressed());
		LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		playParams.topMargin = dp(22);
		col.addView(playButton, playParams);

		TextView tip = text("En el juego, el botón Atrás (o el icono de pausa) abre el menú: guardar o cargar estado, ajustes y salir.", 13, C_MUTED, false);
		tip.setGravity(Gravity.CENTER_HORIZONTAL);
		tip.setPadding(dp(8), dp(14), dp(8), 0);
		col.addView(tip);

		TextView footer = text("Necesitas tu propia copia del juego. Emulación con PPSSPP (licencia GPL 2.0 o posterior).", 11, 0xFF6C6C78, false);
		footer.setGravity(Gravity.CENTER_HORIZONTAL);
		footer.setPadding(dp(8), dp(18), dp(8), 0);
		col.addView(footer);

		return scroll;
	}

	@SuppressWarnings("deprecation")
	private static void applyInsets(View v, WindowInsets insets) {
		v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
			insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
	}

	private LinearLayout section(LinearLayout parent, String title) {
		LinearLayout card = card(C_STROKE);
		TextView label = text(title, 12, C_MUTED, true);
		label.setLetterSpacing(0.18f);
		label.setPadding(0, 0, 0, dp(10));
		card.addView(label);
		parent.addView(card, cardParams());
		return card;
	}

	private LinearLayout card(int strokeColor) {
		LinearLayout card = new LinearLayout(this);
		card.setOrientation(LinearLayout.VERTICAL);
		card.setPadding(dp(16), dp(16), dp(16), dp(16));
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(C_CARD);
		bg.setCornerRadius(dp(16));
		bg.setStroke(dp(1), strokeColor);
		card.setBackground(bg);
		return card;
	}

	private LinearLayout.LayoutParams cardParams() {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.topMargin = dp(16);
		return lp;
	}

	private Switch switchRow(LinearLayout parent, String title, String description) {
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout texts = new LinearLayout(this);
		texts.setOrientation(LinearLayout.VERTICAL);
		texts.addView(text(title, 15, C_TEXT, true));
		TextView desc = text(description, 13, C_MUTED, false);
		desc.setPadding(0, dp(2), dp(12), 0);
		texts.addView(desc);
		row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		Switch sw = new Switch(this);
		if (Build.VERSION.SDK_INT >= 23) {
			int[][] states = { { android.R.attr.state_checked }, {} };
			sw.setThumbTintList(new ColorStateList(states, new int[] { C_ACCENT, 0xFFBDBDC6 }));
			sw.setTrackTintList(new ColorStateList(states, new int[] { 0x99D7263D, 0xFF4A4A55 }));
		}
		row.addView(sw);
		// Tapping anywhere on the row toggles the switch.
		row.setOnClickListener(v -> sw.toggle());
		parent.addView(row);
		return sw;
	}

	private TextView text(String s, float sp, int color, boolean bold) {
		TextView t = new TextView(this);
		t.setText(s);
		t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
		t.setTextColor(color);
		if (bold) {
			t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
		}
		t.setLineSpacing(0, 1.12f);
		return t;
	}

	private TextView filledButton(String label, float sp) {
		TextView b = text(label, sp, Color.WHITE, true);
		b.setGravity(Gravity.CENTER);
		b.setPadding(dp(16), dp(12), dp(16), dp(12));
		b.setBackground(ripple(C_ACCENT, 0, 14));
		makeClickable(b);
		return b;
	}

	private TextView outlineButton(String label) {
		TextView b = text(label, 15, C_TEXT, true);
		b.setGravity(Gravity.CENTER);
		b.setPadding(dp(16), dp(12), dp(16), dp(12));
		b.setBackground(ripple(Color.TRANSPARENT, C_CHIP_STROKE, 14));
		makeClickable(b);
		return b;
	}

	private TextView chip(String label) {
		TextView c = text(label, 16, C_MUTED, true);
		c.setGravity(Gravity.CENTER);
		c.setPadding(dp(4), dp(11), dp(4), dp(11));
		makeClickable(c);
		styleChip(c, false);
		return c;
	}

	private void styleChip(TextView chip, boolean selected) {
		chip.setBackground(selected ? ripple(C_ACCENT, 0, 12) : ripple(Color.TRANSPARENT, C_CHIP_STROKE, 12));
		chip.setTextColor(selected ? Color.WHITE : C_MUTED);
		chip.setSelected(selected);
	}

	private static void makeClickable(View v) {
		v.setClickable(true);
		v.setFocusable(true);
	}

	private Drawable ripple(int fill, int stroke, float radiusDp) {
		GradientDrawable shape = new GradientDrawable();
		shape.setColor(fill);
		shape.setCornerRadius(dp(radiusDp));
		if (stroke != 0) {
			shape.setStroke(dp(1.5f), stroke);
		}
		GradientDrawable mask = new GradientDrawable();
		mask.setColor(Color.WHITE);
		mask.setCornerRadius(dp(radiusDp));
		return new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), shape, mask);
	}

	private LinearLayout.LayoutParams weighted(int leftMargin, int rightMargin) {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
		lp.leftMargin = leftMargin;
		lp.rightMargin = rightMargin;
		return lp;
	}

	private int dp(float v) {
		return Math.round(v * getResources().getDisplayMetrics().density);
	}

	private void showMessage(String title, String message) {
		new AlertDialog.Builder(this)
			.setTitle(title)
			.setMessage(message)
			.setPositiveButton("Aceptar", null)
			.show();
	}
}
