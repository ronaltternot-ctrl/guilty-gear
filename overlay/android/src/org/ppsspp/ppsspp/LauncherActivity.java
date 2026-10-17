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
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
	private static final String K_NET_MODE = "net_mode";
	private static final String K_JOIN_CODE = "net_join_code";
	private static final String K_NICK = "net_nickname";
	private static final String K_NET_SERVER = "net_server";
	private static final String K_HB_ENABLED = "hitbox_enabled";
	private static final String K_HB_HURT = "hitbox_hurt";
	private static final String K_HB_HIT = "hitbox_hit";
	private static final String K_HB_PIVOT = "hitbox_pivot";
	private static final String K_HB_PROJ = "hitbox_projectiles";
	private static final String K_HB_MANUAL = "hitbox_manual";
	private static final String K_GESTURES = "gestures";
	private static final String K_GES_SCALE = "gesture_scale";
	private static final String K_GES_ALPHA = "gesture_opacity";
	private static final String K_GES_MOVE = "gesture_move_";
	// AMD FSR 1.0 (beta), saved per game: key + disc ID (e.g. "fsr_on_ULUS10409").
	private static final String K_FSR_ON = "fsr_on_";
	private static final String K_FSR_SCALE = "fsr_scale_";   // factor x 10: 15, 20, 30
	private static final String K_FSR_SHARP = "fsr_sharp_";   // RCAS sharpness, 0-100
	private static final int[] FSR_SCALES = { 15, 20, 30 };
	private static final String[] FSR_SCALE_LABELS = { "1.5×", "2×", "3×" };
	private static final int FSR_DEFAULT_SCALE = 20;
	private static final int FSR_DEFAULT_SHARP = 85;
	// Device test (DeviceBenchmark) and custom HUD (UI/AcPlusHud.h).
	private static final String K_BENCH = "bench_result";
	private static final String K_BENCH_ASKED = "bench_asked";
	private static final String K_HUD_ON = "hud_enabled";
	private static final String K_HUD_DESIGN = "hud_design";
	private static final String K_HUD_ALPHA = "hud_opacity";
	private static final String[] HUD_NAMES = { "Neón", "Arcade", "Lateral", "Datos" };
	private static final int[] HUD_PREVIEWS = { R.drawable.hud_neon, R.drawable.hud_arcade, R.drawable.hud_side, R.drawable.hud_data };
	private static final String[] HUD_DESCRIPTIONS = {
		"Barras inclinadas con brillo: cian para el jugador 1 y rosa para el 2. Tensión con marcas cada 25 % y el combo en grande en el lado de quien lo hace.",
		"Barras amarillas con el daño reciente en rojo, sobre franjas oscuras como una recreativa de los 90. Combo «12 HITS!» con el daño.",
		"Barras verticales en las franjas negras de los lados de los teléfonos alargados: la imagen del juego queda despejada.",
		"Para entrenar: vida exacta, tensión y burst en %, y los golpes y el daño de cada combo (el último se queda en pantalla).",
	};
	private static final int[] HUD_ALPHAS = { 60, 80, 100 };

	private static final int[] GES_SCALES = { 80, 100, 125 };
	private static final String[] GES_SCALE_LABELS = { "Pequeños", "Normales", "Grandes" };
	private static final int[] GES_ALPHAS = { 35, 60, 85 };
	private static final String[] GES_ALPHA_LABELS = { "Suave", "Media", "Fuerte" };

	private static final String GESTURE_GUIDE =
		"• Toca: golpe. Toques seguidos hacen la cadena P → K → S → HS.\n"
		+ "• Desliza → o ←: dash hacia ese lado (hacia el rival es dash, hacia atrás es backdash). Si dejas el dedo, sigues corriendo.\n"
		+ "• Desliza ↑, ↗ o ↖: saltar.\n"
		+ "• Desliza ↓: barrida (2D).\n"
		+ "• Toca con dos dedos: Dust (D).\n"
		+ "• Desliza con dos dedos → o ←: agarre. Hacia arriba: supersalto.\n"
		+ "• Mantén dos dedos: bloquear. Bájalos un poco para bloquear agachado.\n"
		+ "• Mantén un dedo y arrástralo: stick para caminar, agacharte, bloquear o saltar.\n"
		+ "• Botones de la derecha: los especiales y el Overdrive de tu personaje (el movimiento sale solo), RC (Roman Cancel) y Burst.\n"
		+ "• Arriba a la izquierda: Start, ✕ y ○ para los menús, y una flecha que indica hacia dónde miras. Si los especiales salen al revés, tócala.";

	private static final String GESTURE_GUIDE_BB =
		"• Toca: golpe. Toques seguidos hacen la cadena A → B → C.\n"
		+ "• Desliza → o ←: dash hacia ese lado (hacia el rival es dash, hacia atrás es backdash). Si dejas el dedo, sigues corriendo.\n"
		+ "• Desliza ↑, ↗ o ↖: saltar.\n"
		+ "• Desliza ↓: 2C.\n"
		+ "• Toca con dos dedos: D (Drive).\n"
		+ "• Desliza con dos dedos → o ←: agarre (B+C). Hacia arriba: supersalto.\n"
		+ "• Mantén dos dedos: bloquear. Bájalos un poco para bloquear agachado.\n"
		+ "• Mantén un dedo y arrástralo: stick para caminar, agacharte, bloquear o saltar.\n"
		+ "• Botones de la derecha: tres especiales y un Distortion Drive (el movimiento sale solo), RC (Rapid Cancel, A+B+C) y Burst (Barrier Burst, A+B+C+D).\n"
		+ "• Arriba a la izquierda: Start, ✕ y ○ para los menús, y una flecha que indica hacia dónde miras. Si los especiales salen al revés, tócala.";

	/** Gesture specials of BlazBlue: one set for every character (stored as "bb"). */
	private static final int GESTURE_BB = -1;
	/** Games added in "Mis juegos": JSON array of {uri, name, title, id, format}. */
	private static final String K_LIBRARY = "game_library";

	/** Hitbox viewer parts, in the order of the switches; ini keys read by UI/AcPlusHitboxes.h. */
	private static final String[] HB_KEYS = { K_HB_HURT, K_HB_HIT, K_HB_PIVOT, K_HB_PROJ };
	private static final String[] HB_INI_KEYS = { "Hurtboxes", "Hitboxes", "Pivots", "Projectiles" };
	/** Manual detection values that can be pasted in the launcher (see AcHitbox::Settings). */
	private static final String[] HB_MANUAL_KEYS = { "P1", "Stride", "BoxPtr", "BoxCount", "Pivot", "CameraCenter", "Scale", "GroundY" };

	private static final String NET_OFF = "off";
	private static final String NET_HOST = "host";
	private static final String NET_JOIN = "join";
	private static final String NET_INTERNET = "internet";
	private static final String[] NET_MODES = { NET_OFF, NET_HOST, NET_JOIN, NET_INTERNET };
	private static final String[] NET_LABELS = { "Apagado", "Crear sala", "Unirme", "Internet" };

	/** Public PRO ad hoc servers with relay (from PPSSPP's adhoc-servers.json). */
	private static final String[][] NET_SERVERS = {
		{ "socom.cc", "Socom", "Francia" },
		{ "psp.mgn.pub", "MGN", "EE. UU." },
		{ "jpa36a7.glddns.com", "Relay BR", "Brasil" },
	};

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
	private Switch hitboxSwitch;
	private LinearLayout hitboxOptions;
	private final Switch[] hitboxParts = new Switch[HB_KEYS.length];
	private TextView hitboxStatus;
	private EditText hitboxManual;
	private Switch gestureSwitch;
	private LinearLayout gestureOptions;
	private final TextView[] gestureSizeChips = new TextView[GES_SCALES.length];
	private final TextView[] gestureAlphaChips = new TextView[GES_ALPHAS.length];
	private TextView gestureSide1p;
	private TextView gestureSide2p;
	private TextView gestureCharButton;
	private LinearLayout gestureMoveRows;
	private int gestureChar = 1;
	private TextView gestureGuide;
	private TextView gestureMovesNote;
	private TextView headerKicker;
	private TextView headerTitle;
	private TextView headerSubtitle;
	private TextView padNote;
	private LinearLayout libraryBox;
	private ScrollView mainScroll;
	private TextView amdButtonStatus;
	private LinearLayout fsrPanel;
	private Switch fsrSwitch;
	private LinearLayout fsrOptions;
	private final TextView[] fsrScaleChips = new TextView[FSR_SCALES.length];
	private SeekBar fsrSeek;
	private TextView fsrSharpValue;
	private TextView fsrInfo;
	private TextView fsrWarn;
	private TextView fsrNote;
	private final List<View> effectRows = new ArrayList<>();
	private TextView benchSummary;
	private TextView benchDetail;
	private TextView benchApply;
	private TextView benchRun;
	private Switch hudSwitch;
	private LinearLayout hudOptions;
	private final TextView[] hudChips = new TextView[HUD_NAMES.length];
	private final TextView[] hudAlphaChips = new TextView[HUD_ALPHAS.length];
	private ImageView hudPreview;
	private TextView hudDesc;
	private final TextView[] netChips = new TextView[NET_MODES.length];
	private EditText nickInput;
	private LinearLayout netHostPanel;
	private LinearLayout netJoinPanel;
	private LinearLayout netInternetPanel;
	private TextView hostCode;
	private TextView hostDetail;
	private EditText joinInput;
	private TextView joinStatus;
	private final TextView[] serverChips = new TextView[NET_SERVERS.length];
	private TextView netSteps;
	private LinearLayout runningCard;
	private TextView playButton;

	private final Runnable refreshRunningTask = this::refreshRunning;

	// -------------------------------------------------------------------------------------------
	// Lifecycle

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
		migrateFsrEffect();
		recommendedRes = computeRecommendedResolution();
		if (!prefs.contains(K_RES)) {
			prefs.edit().putInt(K_RES, recommendedRes).apply();
		}
		if (!prefs.contains(K_BACKEND)) {
			prefs.edit().putString(K_BACKEND, defaultBackend()).apply();
		}
		setContentView(buildUi());
		// First launch: test the phone once and suggest settings.
		if (!prefs.contains(K_BENCH) && !prefs.getBoolean(K_BENCH_ASKED, false)) {
			prefs.edit().putBoolean(K_BENCH_ASKED, true).apply();
			handler.postDelayed(() -> runBenchmark(true), 800);
		}
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

		// The previous game keeps its permission: it stays in "Mis juegos".

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
		refreshPerGame();
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
		String netMode = prefs.getString(K_NET_MODE, NET_OFF);
		if (NET_JOIN.equals(netMode) && RoomCode.decode(prefs.getString(K_JOIN_CODE, "")) == null) {
			showMessage("Falta el código de la sala",
				"Escribe en «Multijugador» el código que ve tu amigo en su pantalla, o pon el multijugador en «Apagado».");
			return;
		}
		if (NET_HOST.equals(netMode) && RoomCode.localAddresses().isEmpty()) {
			new AlertDialog.Builder(this)
				.setTitle("Sin red local")
				.setMessage("No estás conectado a una red Wi-Fi ni compartiendo zona Wi-Fi, así que tu amigo no podrá entrar a la sala.")
				.setPositiveButton("Jugar igualmente", (d, w) -> launch(uri))
				.setNegativeButton("Cancelar", null)
				.show();
			return;
		}
		launch(uri);
	}

	private void launch(Uri uri) {
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
		// Gesture controls keep the pause button of the touch controls.
		boolean touch = prefs.getBoolean(K_TOUCH, true) || prefs.getBoolean(K_GESTURES, false);
		boolean fps = prefs.getBoolean(K_FPS, false);

		String style = prefs.getString(K_STYLE, STYLE_PSP);
		boolean arcade = STYLE_ARCADE.equals(style);
		// Only rewrite the button layout when the button type changes, so that buttons moved
		// with the game's own layout editor stay where the player put them.
		// The arcade labels depend on the game (P K S HS D or A B C D).
		boolean blazblue = isBlazBlue();
		String appliedKey = style + (blazblue ? "|bb" : "");
		boolean writeLayout = !appliedKey.equals(prefs.getString(K_APPLIED_STYLE, ""));

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
			appendArcadeButtonDefinitions(ini, blazblue);
		}
		ini.append("[TouchControls.Landscape]\n");
		// Fast-forward isn't a PSP or arcade button: never show it.
		ini.append("ShowTouchUnthrottle = False\n");
		if (writeLayout) {
			appendTouchLayout(ini, arcade, blazblue);
		}
		ini.append("[TouchControls.Portrait]\n")
			.append("ShowTouchUnthrottle = False\n");

		// Post-processing (needs patches/0003 so the appended config can set it). AMD FSR, when on
		// for this game, replaces the visual effect and keeps the internal resolution (patches/0005).
		Effect effect = currentEffect();
		ini.append("[PostShaderList]\n");
		if (fsrOn()) {
			ini.append("PostShader1 = AcFsrEasu\n")
				.append("PostShader2 = AcFsrRcas\n")
				.append("[PostShaderSetting]\n")
				.append(String.format(Locale.ROOT, "AcFsrEasuSettingCurrentValue1 = %.2f\n", fsrScale() / 10f))
				.append(String.format(Locale.ROOT, "AcFsrRcasSettingCurrentValue1 = %.2f\n", fsrSharp() / 100f));
		} else {
			if (effect.shaders.length == 0) {
				ini.append("PostShader1 = Off\n");
			}
			for (int i = 0; i < effect.shaders.length; i++) {
				ini.append("PostShader").append(i + 1).append(" = ").append(effect.shaders[i]).append('\n');
			}
		}
		appendNetworkConfig(ini);
		writeText(configFile(), ini.toString());
		prefs.edit().putString(K_APPLIED_STYLE, appliedKey).apply();

		writeProfileConfig();
	}

	/**
	 * The game's "Network" mode is PSP ad hoc (two consoles nearby). PPSSPP carries it over the
	 * network through a PRO ad hoc server: the host runs PPSSPP's built-in one, the guest
	 * connects to the host's address (taken from the room code).
	 */
	private void appendNetworkConfig(StringBuilder ini) {
		String mode = prefs.getString(K_NET_MODE, NET_OFF);
		ini.append("[Network]\n");
		switch (mode) {
			case NET_HOST: {
				// Must be this phone's own Wi-Fi address (the one in the room code): with
				// "localhost" PPSSPP switches to a same-device test mode that LAN players can't reach.
				List<RoomCode.LocalAddress> addresses = RoomCode.localAddresses();
				String self = addresses.isEmpty() ? "localhost" : addresses.get(0).ip;
				ini.append("EnableWlan = True\n")
					.append("EnableAdhocServer = True\n")
					.append("proAdhocServer = ").append(self).append('\n')
					.append("AdhocServerRelayMode = 2\n");  // Direct (P2P) on the local network.
				break;
			}
			case NET_JOIN:
				ini.append("EnableWlan = True\n")
					.append("EnableAdhocServer = False\n")
					.append("proAdhocServer = ").append(RoomCode.decode(prefs.getString(K_JOIN_CODE, ""))).append('\n')
					.append("AdhocServerRelayMode = 2\n");
				break;
			case NET_INTERNET:
				ini.append("EnableWlan = True\n")
					.append("EnableAdhocServer = False\n")
					.append("proAdhocServer = ").append(currentServer()).append('\n')
					.append("AdhocServerRelayMode = 0\n");  // Auto: these servers relay the traffic.
				break;
			default:
				ini.append("EnableWlan = False\n")
					.append("EnableAdhocServer = False\n");
				break;
		}
		ini.append("EnableUPnP = False\n")
			.append("[SystemParam]\n")
			.append("NickName = ").append(nickname()).append('\n');
	}

	private String nickname() {
		String n = prefs.getString(K_NICK, "");
		n = n.replaceAll("[^A-Za-z0-9 _\\-.]", "").trim();
		if (n.isEmpty()) {
			n = "Jugador";
		}
		return n.length() > 16 ? n.substring(0, 16) : n;
	}

	private String currentServer() {
		String s = prefs.getString(K_NET_SERVER, NET_SERVERS[0][0]);
		for (String[] server : NET_SERVERS) {
			if (server[0].equals(s)) {
				return s;
			}
		}
		return NET_SERVERS[0][0];
	}

	/** Settings and the two layout presets the emulator uses for per-character profiles. */
	private void writeProfileConfig() throws IOException {
		for (String preset : new String[] { STYLE_PSP, STYLE_ARCADE }) {
			boolean arcade = STYLE_ARCADE.equals(preset);
			StringBuilder layout = new StringBuilder("[Control]\n");
			if (arcade) {
				appendArcadeButtonDefinitions(layout, false);
			}
			layout.append("[TouchControls.Landscape]\n").append("ShowTouchUnthrottle = False\n");
			appendTouchLayout(layout, arcade, false);
			writeText(new File(getFilesDir(), "acplus_layout_" + preset + ".ini"), layout.toString());
		}

		StringBuilder ini = new StringBuilder();
		ini.append("[Profiles]\n")
			// With gestures there are no buttons to swap.
			.append("Enabled = ").append(prefs.getBoolean(K_PROFILES, true) && !prefs.getBoolean(K_GESTURES, false) ? "True" : "False").append('\n')
			.append("Side = ").append(prefs.getInt(K_SIDE, 0)).append('\n')
			.append("DefaultStyle = ").append(prefs.getString(K_STYLE, STYLE_PSP)).append('\n')
			.append("[Characters]\n");
		for (int id = 1; id < CHARACTERS.length; id++) {
			String s = prefs.getString(K_CHAR_STYLE + id, null);
			if (STYLE_PSP.equals(s) || STYLE_ARCADE.equals(s)) {
				ini.append(id).append(" = ").append(s).append('\n');
			}
		}
		ini.append("[Hitboxes]\n")
			.append("Enabled = ").append(prefs.getBoolean(K_HB_ENABLED, false) ? "True" : "False").append('\n');
		for (int i = 0; i < HB_KEYS.length; i++) {
			ini.append(HB_INI_KEYS[i]).append(" = ").append(prefs.getBoolean(HB_KEYS[i], true) ? "True" : "False").append('\n');
		}
		ini.append(hitboxManualLines(prefs.getString(K_HB_MANUAL, "")));
		ini.append("[Hud]\n")
			.append("Enabled = ").append(prefs.getBoolean(K_HUD_ON, false) ? "True" : "False").append('\n')
			.append("Design = ").append(hudDesign()).append('\n')
			.append("Opacity = ").append(prefs.getInt(K_HUD_ALPHA, 100)).append('\n');
		ini.append("[Fsr]\n")
			.append("Enabled = ").append(fsrOn() ? "True" : "False").append('\n')
			.append("GameId = ").append(fsrGameKey().equals("default") ? "" : fsrGameKey()).append('\n')
			.append(String.format(Locale.ROOT, "Scale = %.2f\n", fsrScale() / 10f))
			.append(String.format(Locale.ROOT, "Sharpness = %.2f\n", fsrSharp() / 100f));
		ini.append("[Gestures]\n")
			.append("Enabled = ").append(prefs.getBoolean(K_GESTURES, false) ? "True" : "False").append('\n')
			.append("Scale = ").append(prefs.getInt(K_GES_SCALE, 100)).append('\n')
			.append("Opacity = ").append(prefs.getInt(K_GES_ALPHA, 60)).append('\n');
		ini.append("MovesBB = ");
		for (int slot = 0; slot < GestureMoves.SLOTS; slot++) {
			ini.append(slot == 0 ? "" : ",").append(gestureMove(GESTURE_BB, slot));
		}
		ini.append('\n');
		for (int id = 0; id < CHARACTERS.length; id++) {
			ini.append("Moves").append(id).append(" = ");
			for (int slot = 0; slot < GestureMoves.SLOTS; slot++) {
				ini.append(slot == 0 ? "" : ",").append(gestureMove(id, slot));
			}
			ini.append('\n');
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

	/** PPSSPP's own letter images (UI/GamepadEmu.h customKeyImages): I_A to I_D. */
	private static final int IMG_A = 6;
	private static final int IMG_B = 7;
	private static final int IMG_C = 8;
	private static final int IMG_LETTER_D = 9;

	/** BlazBlue cabinet: A, B and C on the top row, D (Drive) below. */
	static final float[][] BB_ARCADE_POSITIONS = {
		{ 0.745f, 0.575f },  // A
		{ 0.845f, 0.545f },  // B
		{ 0.945f, 0.575f },  // C
		{ 0.790f, 0.815f },  // D
	};

	/** BlazBlue's PSP mapping: square A, triangle B, circle C, cross D. */
	private static final ArcadeButton[] BB_ARCADE_BUTTONS = {
		new ArcadeButton(IMG_A, KEY_SQUARE, BB_ARCADE_POSITIONS[0][0], BB_ARCADE_POSITIONS[0][1]),
		new ArcadeButton(IMG_B, KEY_TRIANGLE, BB_ARCADE_POSITIONS[1][0], BB_ARCADE_POSITIONS[1][1]),
		new ArcadeButton(IMG_C, KEY_CIRCLE, BB_ARCADE_POSITIONS[2][0], BB_ARCADE_POSITIONS[2][1]),
		new ArcadeButton(IMG_LETTER_D, KEY_CROSS, BB_ARCADE_POSITIONS[3][0], BB_ARCADE_POSITIONS[3][1]),
	};

	private static final float BUTTON_SCALE = 1.15f;

	private static void appendArcadeButtonDefinitions(StringBuilder ini, boolean blazblue) {
		ArcadeButton[] buttons = blazblue ? BB_ARCADE_BUTTONS : ARCADE_BUTTONS;
		for (int i = 0; i < buttons.length; i++) {
			ArcadeButton b = buttons[i];
			ini.append("Custom").append(i).append("Mapping = ")
				.append(String.format(Locale.ROOT, "0x%016x", b.key)).append('\n')
				.append("Custom").append(i).append("Image = ").append(b.image).append('\n')
				.append("Custom").append(i).append("Shape = 0\n")  // Round
				.append("Custom").append(i).append("Toggle = False\n")
				.append("Custom").append(i).append("Repeat = False\n");
		}
	}

	private static void appendTouchLayout(StringBuilder ini, boolean arcade, boolean blazblue) {
		ArcadeButton[] buttons = blazblue ? BB_ARCADE_BUTTONS : ARCADE_BUTTONS;
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
				if (i >= buttons.length) {
					ini.append("ShowComboKey").append(i).append(" = False\n");
					continue;
				}
				ArcadeButton b = buttons[i];
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
				refreshPerGame();
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
		refreshNetwork();
		refreshHitboxes();
		refreshGestures();
		refreshFsr();
		refreshBenchmark();
		refreshHud();
		touchSwitch.setChecked(prefs.getBoolean(K_TOUCH, true));
		fpsSwitch.setChecked(prefs.getBoolean(K_FPS, false));
		refreshRunning();
	}

	private void refreshGame() {
		refreshHeader();
		refreshLibrary();
		String stored = prefs.getString(K_URI, null);
		if (stored == null) {
			gameStatus.setText("Elige tu copia del juego");
			gameStatus.setTextColor(C_TEXT);
			gameDetail.setText("Archivo ISO o CSO de Guilty Gear XX ΛCore Plus o de BlazBlue: Calamity Trigger para PSP. Guárdalo en la memoria del teléfono o en la microSD, no en la nube.");
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
			case BLAZBLUE:
				gameStatus.setText("✓ " + r.title);
				gameStatus.setTextColor(C_OK);
				detail.append("\nBlazBlue: botones A, B, C y D, gestos y efectos listos. El visor de hitboxes, el HUD propio y los perfiles por personaje son solo de Guilty Gear.");
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
		addCurrentToLibrary();
		refreshLibrary();
	}

	// -------------------------------------------------------------------------------------------
	// Several games: header and "Mis juegos"

	private boolean isBlazBlue() {
		return GameInspector.isBlazBlue(prefs.getString(K_ID, null), prefs.getString(K_TITLE, null));
	}

	/** Short name for messages ("Guilty Gear XX ΛC+", "BlazBlue"). */
	private String gameShortName() {
		return isBlazBlue() ? "BlazBlue" : "Guilty Gear XX ΛC+";
	}

	private void refreshHeader() {
		if (headerKicker == null) {
			return;
		}
		if (isBlazBlue()) {
			String t = prefs.getString(K_TITLE, "");
			boolean cs = t != null && t.toUpperCase(Locale.ROOT).contains("CONTINUUM");
			headerKicker.setText("BLAZBLUE");
			headerTitle.setText(cs ? "CONTINUUM SHIFT II" : "CALAMITY TRIGGER");
			headerSubtitle.setText("Edición PSP · en la misma app que Guilty Gear");
		} else {
			headerKicker.setText("GUILTY GEAR XX");
			headerTitle.setText("ΛCORE PLUS");
			headerSubtitle.setText("Edición PSP · listo para jugar en Android");
		}
	}

	private org.json.JSONArray loadLibrary() {
		try {
			return new org.json.JSONArray(prefs.getString(K_LIBRARY, "[]"));
		} catch (org.json.JSONException e) {
			return new org.json.JSONArray();
		}
	}

	/** Keeps the current game in the list (once it has been identified). */
	private void addCurrentToLibrary() {
		String uri = prefs.getString(K_URI, null);
		if (uri == null || !prefs.getBoolean(K_INSPECTED, false)) {
			return;
		}
		org.json.JSONArray lib = loadLibrary();
		org.json.JSONArray out = new org.json.JSONArray();
		try {
			org.json.JSONObject entry = new org.json.JSONObject()
				.put("uri", uri)
				.put("name", prefs.getString(K_NAME, ""))
				.put("title", prefs.getString(K_TITLE, ""))
				.put("id", prefs.getString(K_ID, ""))
				.put("format", prefs.getString(K_FORMAT, ""));
			out.put(entry);
			for (int i = 0; i < lib.length() && out.length() < 12; i++) {
				org.json.JSONObject e = lib.optJSONObject(i);
				if (e != null && !uri.equals(e.optString("uri"))) {
					out.put(e);
				}
			}
		} catch (org.json.JSONException e) {
			return;
		}
		if (!out.toString().equals(lib.toString())) {
			prefs.edit().putString(K_LIBRARY, out.toString()).apply();
		}
	}

	private void refreshLibrary() {
		if (libraryBox == null) {
			return;
		}
		libraryBox.removeAllViews();
		String current = prefs.getString(K_URI, null);
		org.json.JSONArray lib = loadLibrary();
		int shown = 0;
		for (int i = 0; i < lib.length(); i++) {
			final org.json.JSONObject e = lib.optJSONObject(i);
			if (e == null || e.optString("uri").equals(current)) {
				continue;
			}
			if (shown == 0) {
				TextView label = text("Mis juegos · toca para cambiar", 13, C_MUTED, true);
				label.setPadding(0, dp(16), 0, dp(2));
				libraryBox.addView(label);
			}
			shown++;
			LinearLayout row = new LinearLayout(this);
			row.setOrientation(LinearLayout.VERTICAL);
			row.setPadding(dp(14), dp(10), dp(14), dp(10));
			row.setBackground(ripple(Color.TRANSPARENT, C_CHIP_STROKE, 12));
			makeClickable(row);
			String title = e.optString("title");
			row.addView(text(title.isEmpty() ? e.optString("name") : title, 15, C_TEXT, true));
			GameInspector.Result r = new GameInspector.Result(e.optString("format"), e.optString("id"), title);
			String id = r.prettyDiscId();
			row.addView(text((id == null || id.isEmpty() ? "" : id + " · ") + e.optString("name"), 12, C_MUTED, false));
			row.setOnClickListener(v -> switchGame(e));
			row.setOnLongClickListener(v -> {
				confirmRemoveFromLibrary(e);
				return true;
			});
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.topMargin = dp(8);
			libraryBox.addView(row, lp);
		}
		if (shown > 0) {
			TextView hint = text("Mantén pulsado un juego para quitarlo de la lista (no se borra el archivo).", 11, 0xFF6C6C78, false);
			hint.setPadding(0, dp(6), 0, 0);
			libraryBox.addView(hint);
		}
	}

	private void switchGame(org.json.JSONObject e) {
		if (findEmuPid() > 0) {
			showMessage("Hay una partida abierta", "Cierra la partida en curso (botón «Cerrar partida» de arriba) antes de cambiar de juego.");
			return;
		}
		Uri uri = Uri.parse(e.optString("uri"));
		if (!hasReadAccess(uri)) {
			showMessage("No tengo acceso a ese archivo", "Puede que se haya movido o borrado. Vuelve a añadirlo con «Añadir o cambiar de juego».");
			return;
		}
		String title = e.optString("title");
		String id = e.optString("id");
		prefs.edit()
			.putString(K_URI, uri.toString())
			.putString(K_NAME, e.optString("name"))
			.putString(K_TITLE, title.isEmpty() ? null : title)
			.putString(K_ID, id.isEmpty() ? null : id)
			.putString(K_FORMAT, e.optString("format"))
			.putBoolean(K_INSPECTED, !id.isEmpty() || !title.isEmpty())
			.apply();
		refreshAll();
		refreshPerGame();
		if (mainScroll != null) {
			mainScroll.smoothScrollTo(0, 0);
		}
	}

	private void confirmRemoveFromLibrary(org.json.JSONObject e) {
		String title = e.optString("title");
		new AlertDialog.Builder(this)
			.setTitle("¿Quitar de «Mis juegos»?")
			.setMessage((title.isEmpty() ? e.optString("name") : title) + "\n\nEl archivo no se borra; puedes volver a añadirlo cuando quieras.")
			.setPositiveButton("Quitar", (d, w) -> {
				org.json.JSONArray lib = loadLibrary();
				org.json.JSONArray out = new org.json.JSONArray();
				for (int i = 0; i < lib.length(); i++) {
					org.json.JSONObject x = lib.optJSONObject(i);
					if (x != null && !x.optString("uri").equals(e.optString("uri"))) {
						out.put(x);
					}
				}
				prefs.edit().putString(K_LIBRARY, out.toString()).apply();
				try {
					getContentResolver().releasePersistableUriPermission(Uri.parse(e.optString("uri")), Intent.FLAG_GRANT_READ_URI_PERMISSION);
				} catch (SecurityException ignored) {
					// Not persisted anymore.
				}
				refreshLibrary();
			})
			.setNegativeButton("Cancelar", null)
			.show();
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
		boolean fsr = fsrOn();
		boolean applies = effect.shaders.length == 0 || fsr;
		if (fsr) {
			hint += "\nCon AMD FSR activo el juego se dibuja a esta resolución y FSR la escala ("
				+ fsrScaleLabel(fsrScale()) + "). Mira «Gráficos».";
		} else if (!applies) {
			hint = "Con el efecto «" + effect.label + "» el juego se dibuja a su resolución original "
				+ "(480 × 272) y el efecto lo amplía hasta la de tu pantalla, sin tocar los sprites. "
				+ "Para usar esta resolución elige el efecto «Original».";
		}
		for (TextView chip : resChips) {
			chip.setAlpha(applies ? 1f : 0.45f);
		}
		resInfo.setAlpha(applies ? 1f : 0.45f);
		resHint.setText(hint);
		if (fsrInfo != null) {
			refreshFsr();
		}
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
		if (fsrOn()) {
			effectPreview.setImageResource(R.drawable.fx_fsr);
			effectPreview.setContentDescription("Vista previa de AMD FSR 1.0");
			effectDesc.setText("BETA · AMD GRAPHICS: FSR 1.0 escala la imagen " + fsrScaleLabel(fsrScale())
				+ " con EASU y la afina con RCAS, sobre la resolución interna que elegiste. El efecto «"
				+ current.label + "» queda en pausa mientras FSR esté activo.");
			return;
		}
		effectPreview.setImageResource(current.preview);
		effectPreview.setContentDescription("Vista previa del efecto " + current.label);
		effectDesc.setText(current.description);
	}

	/** Settings and texts that depend on the game: show the ones of the game just chosen. */
	private void refreshPerGame() {
		refreshFsr();
		refreshEffect();
		refreshResolution();
		refreshStyle();
		refreshGestures();
		refreshProfiles();
		refreshHitboxes();
		refreshHud();
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
		if (gestureSide1p != null) {
			styleChip(gestureSide1p, side == 0);
			styleChip(gestureSide2p, side == 1);
		}
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
			s.append(isBlazBlue()
				? "⚠ Con BlazBlue no se usa: los perfiles por personaje son solo de Guilty Gear XX ΛC+ (versión americana ULUS-10409).\n\n"
				: "⚠ Tu copia es " + prefs.getString(K_ID, "") + ". El cambio automático solo funciona con la versión americana (ULUS-10409); con otras versiones se usa siempre el tipo de botones general.\n\n");
		}
		s.append(custom == 0 ? "Todos usan el tipo de botones general." : custom + (custom == 1 ? " personaje tiene" : " personajes tienen") + " un tipo de botones propio.");
		s.append(saved == 0 ? "" : "\n" + saved + (saved == 1 ? " personaje tiene" : " personajes tienen") + " una distribución guardada.");
		profilesStatus.setText(s.toString());
	}

	// -------------------------------------------------------------------------------------------
	// Custom HUD

	private int hudDesign() {
		int d = prefs.getInt(K_HUD_DESIGN, 1);
		return d >= 1 && d <= HUD_NAMES.length ? d : 1;
	}

	private void refreshHud() {
		boolean on = prefs.getBoolean(K_HUD_ON, false);
		hudSwitch.setChecked(on);
		hudOptions.setAlpha(on ? 1f : 0.45f);
		int design = hudDesign();
		for (int i = 0; i < HUD_NAMES.length; i++) {
			styleChip(hudChips[i], i + 1 == design);
		}
		hudPreview.setImageResource(HUD_PREVIEWS[design - 1]);
		hudPreview.setContentDescription("Vista previa del HUD " + HUD_NAMES[design - 1]);
		String desc = HUD_DESCRIPTIONS[design - 1];
		if (!isProfileGame()) {
			desc = (isBlazBlue()
				? "⚠ Con BlazBlue no se usa: el HUD propio lee la memoria de Guilty Gear XX ΛC+ (versión americana ULUS-10409).\n\n"
				: "⚠ Tu copia es " + prefs.getString(K_ID, "") + ": el HUD propio solo funciona con la versión americana (ULUS-10409).\n\n") + desc;
		}
		hudDesc.setText(desc);
		int alpha = prefs.getInt(K_HUD_ALPHA, 100);
		for (int i = 0; i < HUD_ALPHAS.length; i++) {
			styleChip(hudAlphaChips[i], HUD_ALPHAS[i] == alpha);
		}
	}

	// -------------------------------------------------------------------------------------------
	// Device test

	private void refreshBenchmark() {
		DeviceBenchmark.Result r = DeviceBenchmark.Result.fromJson(prefs.getString(K_BENCH, null));
		if (r == null) {
			benchSummary.setText("Aún no has probado tu equipo");
			benchDetail.setText("Una prueba de unos segundos mide tu GPU dibujando una escena tipo pelea de 1× a 8× y con los efectos de la app, y te sugiere la resolución, el motor gráfico y el efecto ideales.");
			benchApply.setVisibility(View.GONE);
			benchRun.setText("Probar mi equipo");
			return;
		}
		benchSummary.setText("Gama " + r.tier.toLowerCase(Locale.ROOT) + " · " + recommendationShort(r));
		benchDetail.setText(benchmarkReport(r));
		benchApply.setVisibility(View.VISIBLE);
		benchRun.setText("Repetir prueba");
	}

	private String recommendationShort(DeviceBenchmark.Result r) {
		StringBuilder s = new StringBuilder();
		s.append(r.recRes).append("×");
		if (r.recFsr) {
			s.append(" + FSR ").append(fsrScaleLabel(r.recFsrScale));
		}
		s.append(" · ").append(BACKEND_VULKAN.equals(r.recBackend) ? "Vulkan" : "OpenGL");
		return s.toString();
	}

	private String benchmarkReport(DeviceBenchmark.Result r) {
		StringBuilder s = new StringBuilder();
		s.append("Recomendado: resolución ").append(r.recRes).append("× (").append(PSP_W * r.recRes).append(" × ").append(PSP_H * r.recRes).append(")");
		if (r.recFsr) {
			s.append(" con AMD FSR ").append(fsrScaleLabel(r.recFsrScale)).append(" para llegar a tu pantalla");
		} else {
			s.append(", sin filtro");
		}
		s.append(", motor ").append(BACKEND_VULKAN.equals(r.recBackend) ? "Vulkan" : "OpenGL").append(".\n\n");
		s.append("GPU: ").append(r.gpu.isEmpty() ? "?" : r.gpu);
		if (r.vulkanVersion > 0) {
			s.append(" · Vulkan ").append(r.vulkanVersion >> 22).append('.').append((r.vulkanVersion >> 12) & 0x3FF);
		}
		s.append("\nCPU: ").append(r.cores).append(" núcleos");
		if (r.maxFreqKHz > 0) {
			s.append(String.format(Locale.ROOT, " hasta %.1f GHz", r.maxFreqKHz / 1e6));
		}
		if (r.ramMB > 0) {
			s.append(String.format(Locale.ROOT, " · %.0f GB de RAM", r.ramMB / 1024.0));
		}
		s.append(String.format(Locale.ROOT, "\nPantalla: %d × %d (el juego ocupa %d × %d) · %.0f Hz", r.screenW, r.screenH, r.gameW, r.gameH, r.refresh));
		s.append("\n\nDibujar el juego (ms por imagen; a 60 FPS hay 16,7 ms):\n");
		for (int k = 1; k <= DeviceBenchmark.MAX_RES; k++) {
			if (Float.isNaN(r.sceneMs[k])) {
				continue;
			}
			s.append(k == 1 ? "" : " · ").append(k).append("× ").append(DeviceBenchmark.ms(r.sceneMs[k]));
		}
		s.append("\n\nEfectos en tu pantalla (con el juego incluido):");
		for (int i = 0; i < DeviceBenchmark.EFFECT_IDS.length; i++) {
			float total = r.effectTotal(i, r.recRes);
			s.append("\n").append(DeviceBenchmark.EFFECT_NAMES[i]).append(": ").append(DeviceBenchmark.ms(total)).append(" ms  ").append(DeviceBenchmark.rating(total));
		}
		if (!r.error.isEmpty()) {
			s.append("\n\nNo se pudo medir la GPU (").append(r.error).append("): la recomendación sale de tu pantalla y tu RAM.");
		}
		s.append("\n\nEs una estimación con una escena de prueba, no con el juego: si algo va lento, baja un nivel.");
		return s.toString();
	}

	private void applyBenchmark(DeviceBenchmark.Result r) {
		SharedPreferences.Editor e = prefs.edit()
			.putInt(K_RES, clampRes(r.recRes))
			.putString(K_BACKEND, r.recBackend)
			.putString(K_EFFECT, "none")
			.putBoolean(K_FSR_ON + fsrGameKey(), r.recFsr);
		if (r.recFsr) {
			e.putInt(K_FSR_SCALE + fsrGameKey(), r.recFsrScale);
		}
		e.apply();
		refreshAll();
		showMessage("Ajustes aplicados", "Resolución " + r.recRes + "×" + (r.recFsr ? " con AMD FSR " + fsrScaleLabel(r.recFsrScale) : " sin filtro")
			+ " y motor " + (BACKEND_VULKAN.equals(r.recBackend) ? "Vulkan" : "OpenGL") + ". Puedes cambiarlos cuando quieras.");
	}

	private void runBenchmark(boolean firstRun) {
		if (isFinishing()) {
			return;
		}
		final DeviceBenchmark bench = new DeviceBenchmark();
		LinearLayout box = new LinearLayout(this);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setPadding(dp(24), dp(12), dp(24), dp(4));
		TextView intro = text(firstRun
			? "Es la primera vez que abres la app: pruebo tu equipo unos segundos para sugerirte los mejores ajustes."
			: "Midiendo tu equipo…", 14, C_TEXT, false);
		box.addView(intro);
		final ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
		bar.setMax(100);
		if (Build.VERSION.SDK_INT >= 21) {
			bar.setProgressTintList(ColorStateList.valueOf(C_ACCENT));
		}
		LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		barParams.topMargin = dp(14);
		box.addView(bar, barParams);
		final TextView step = text("Preparando…", 13, C_MUTED, false);
		step.setPadding(0, dp(6), 0, 0);
		box.addView(step);
		final AlertDialog dialog = new AlertDialog.Builder(this)
			.setTitle("Probando tu equipo")
			.setView(box)
			.setCancelable(false)
			.setNegativeButton("Cancelar", (d, w) -> bench.cancel())
			.show();
		new Thread(() -> {
			DeviceBenchmark.Result r = bench.run(this, (text, percent) -> handler.post(() -> {
				step.setText(text);
				bar.setProgress(percent);
			}));
			handler.post(() -> {
				if (dialog.isShowing()) {
					dialog.dismiss();
				}
				if (bench.isCancelled() || isFinishing()) {
					return;
				}
				prefs.edit().putString(K_BENCH, r.toJson()).apply();
				refreshBenchmark();
				new AlertDialog.Builder(this)
					.setTitle("Tu equipo: gama " + r.tier.toLowerCase(Locale.ROOT))
					.setMessage(benchmarkReport(r))
					.setPositiveButton("Aplicar recomendados", (d, w) -> applyBenchmark(r))
					.setNegativeButton("Ahora no", null)
					.show();
			});
		}, "DeviceBenchmark").start();
	}

	// -------------------------------------------------------------------------------------------
	// AMD FSR 1.0 (beta)

	/** FSR settings are kept per game, by disc ID ("default" until a game is chosen). */
	private String fsrGameKey() {
		String id = prefs.getString(K_ID, null);
		return id == null || id.isEmpty() ? "default" : id.replace("-", "").toUpperCase(Locale.ROOT);
	}

	private boolean fsrOn() {
		return prefs.getBoolean(K_FSR_ON + fsrGameKey(), false);
	}

	/** Scale factor times 10. */
	private int fsrScale() {
		int v = prefs.getInt(K_FSR_SCALE + fsrGameKey(), FSR_DEFAULT_SCALE);
		for (int s : FSR_SCALES) {
			if (s == v) {
				return v;
			}
		}
		return FSR_DEFAULT_SCALE;
	}

	private int fsrSharp() {
		return Math.max(0, Math.min(100, prefs.getInt(K_FSR_SHARP + fsrGameKey(), FSR_DEFAULT_SHARP)));
	}

	private static String fsrScaleLabel(int scale10) {
		return scale10 % 10 == 0 ? (scale10 / 10) + "×" : String.format(Locale.ROOT, "%.1f×", scale10 / 10f);
	}

	private static String sharpLabel(int sharp) {
		return sharp + " %";
	}

	/** Earlier versions had FSR as one of the visual effects: carry it over to the new option. */
	private void migrateFsrEffect() {
		if ("fsr".equals(prefs.getString(K_EFFECT, null))) {
			prefs.edit().putString(K_EFFECT, "none").putBoolean(K_FSR_ON + fsrGameKey(), true).apply();
		}
	}

	/** Size of the game picture on this screen (aspect kept, filling the short side), landscape. */
	private int[] gameSizeOnScreen() {
		DisplayMetrics dm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getRealMetrics(dm);
		int longSide = Math.max(dm.widthPixels, dm.heightPixels);
		int shortSide = Math.min(dm.widthPixels, dm.heightPixels);
		if (longSide * PSP_H >= shortSide * PSP_W) {
			return new int[] { Math.round(shortSide * (float) PSP_W / PSP_H), shortSide };
		}
		return new int[] { longSide, Math.round(longSide * (float) PSP_H / PSP_W) };
	}

	private void refreshAmdButton() {
		if (amdButtonStatus == null) {
			return;
		}
		amdButtonStatus.setText(fsrOn()
			? "FSR 1.0 activo · " + fsrScaleLabel(fsrScale()) + " · nitidez " + sharpLabel(fsrSharp())
			: "AMD FSR 1.0 · apagado · toca para configurarlo");
		amdButtonStatus.setTextColor(fsrOn() ? C_OK : C_MUTED);
	}

	private void refreshFsr() {
		boolean on = fsrOn();
		fsrSwitch.setChecked(on);
		fsrOptions.setAlpha(on ? 1f : 0.45f);
		int scale = fsrScale();
		for (int i = 0; i < FSR_SCALES.length; i++) {
			styleChip(fsrScaleChips[i], FSR_SCALES[i] == scale);
		}
		int sharp = fsrSharp();
		if (fsrSeek.getProgress() != sharp) {
			fsrSeek.setProgress(sharp);
		}
		fsrSharpValue.setText(sharpLabel(sharp));

		// What FSR will do with the current internal resolution (same rule as patches/0005).
		int res = clampRes(prefs.getInt(K_RES, recommendedRes));
		int rw = PSP_W * res, rh = PSP_H * res;
		int[] screen = gameSizeOnScreen();
		float room = Math.min(screen[0] / (float) rw, screen[1] / (float) rh);
		float wanted = scale / 10f;
		float actual = Math.max(1f, Math.min(wanted, room));
		// A few pixels short of the full factor (e.g. 1.98x) is still "the factor you chose".
		boolean capped = actual < wanted * 0.97f;
		int ow = Math.round(rw * actual), oh = Math.round(rh * actual);
		fsrInfo.setText("Resolución interna " + res + "× (" + rw + " × " + rh + ") → FSR "
			+ (capped ? String.format(Locale.ROOT, "%.2f×", actual) : fsrScaleLabel(scale))
			+ " → " + ow + " × " + oh + "\nEl juego ocupa " + screen[0] + " × " + screen[1] + " en tu pantalla.");
		String warn = null;
		if (room <= 1.05f) {
			warn = "⚠ Tu resolución interna ya llena la pantalla: FSR aporta poco aquí (solo la nitidez de RCAS) y gasta batería. "
				+ "Para que FSR rinda, baja la resolución interna a " + Math.max(1, Math.round(screen[1] / (float) PSP_H / 2f)) + "× o menos.";
		} else if (room < 1.5f) {
			warn = String.format(Locale.ROOT, "⚠ La resolución interna ya es alta: FSR solo puede escalar ×%.2f antes de llegar al tamaño de tu pantalla, así que aporta poco. "
				+ "Baja la resolución interna para dejarle más trabajo a FSR.", room);
		} else if (capped) {
			warn = String.format(Locale.ROOT, "El factor se queda en ×%.2f: la imagen no puede ser más grande que tu pantalla.", actual);
		}
		fsrWarn.setText(warn == null ? "" : warn);
		fsrWarn.setVisibility(warn == null ? View.GONE : View.VISIBLE);

		String id = prefs.getString(K_ID, null);
		fsrNote.setText((id == null ? "Se guarda para cada juego." : "Se guarda solo para este juego (" + id + ").")
			+ " Compatible con los estados guardados: FSR cambia la imagen que ves, no la partida, así que tus estados cargan igual con FSR encendido o apagado."
			+ (on ? " Mientras FSR esté activo, el efecto visual elegido arriba queda en pausa." : ""));

		for (View row : effectRows) {
			row.setAlpha(on ? 0.45f : 1f);
		}
		refreshAmdButton();
	}

	/** The "BETA · AMD GRAPHICS" button: scrolls to the FSR settings and highlights them. */
	private void showFsrPanel() {
		if (mainScroll == null || fsrPanel == null) {
			return;
		}
		int y = 0;
		View v = fsrPanel;
		View content = mainScroll.getChildAt(0);
		while (v != null && v != content) {
			y += v.getTop();
			v = v.getParent() instanceof View ? (View) v.getParent() : null;
		}
		mainScroll.smoothScrollTo(0, Math.max(0, y - dp(16)));
		fsrPanel.animate().alpha(0.4f).setDuration(160).withEndAction(() -> fsrPanel.animate().alpha(1f).setDuration(320).start()).start();
	}

	private void showCredits() {
		ScrollView sv = new ScrollView(this);
		TextView t = text(CREDITS, 13, C_TEXT, false);
		t.setPadding(dp(20), dp(12), dp(20), dp(12));
		t.setTextIsSelectable(true);
		sv.addView(t);
		new AlertDialog.Builder(this)
			.setTitle("Créditos y licencias")
			.setView(sv)
			.setPositiveButton("Cerrar", null)
			.show();
	}

	private static final String AMD_MIT_LICENSE =
		"Copyright (c) 2021 Advanced Micro Devices, Inc. All rights reserved.\n\n"
		+ "Permission is hereby granted, free of charge, to any person obtaining a copy "
		+ "of this software and associated documentation files (the \"Software\"), to deal "
		+ "in the Software without restriction, including without limitation the rights "
		+ "to use, copy, modify, merge, publish, distribute, sublicense, and/or sell "
		+ "copies of the Software, and to permit persons to whom the Software is "
		+ "furnished to do so, subject to the following conditions:\n\n"
		+ "The above copyright notice and this permission notice shall be included in "
		+ "all copies or substantial portions of the Software.\n\n"
		+ "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR "
		+ "IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, "
		+ "FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE "
		+ "AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER "
		+ "LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, "
		+ "OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN "
		+ "THE SOFTWARE.";

	private static final String CREDITS =
		"GGXX ΛC+ para Android\n"
		+ "App no oficial para jugar tu copia de Guilty Gear XX Accent Core Plus (PSP).\n\n"
		+ "■ PPSSPP\n"
		+ "Motor de emulación de PSP, de Henrik Rydgård y colaboradores. Licencia GPL 2.0 o posterior. "
		+ "Esta app es PPSSPP con cambios, distribuida bajo la misma licencia. ppsspp.org\n\n"
		+ "■ AMD FidelityFX Super Resolution 1.0 (FSR 1)\n"
		+ "Los filtros EASU y RCAS de «BETA · AMD GRAPHICS» están adaptados del código de FSR 1.0 de AMD "
		+ "(github.com/GPUOpen-Effects/FidelityFX-FSR), publicado con la licencia MIT:\n\n"
		+ AMD_MIT_LICENSE + "\n\n"
		+ "AMD y FidelityFX son marcas de Advanced Micro Devices, Inc. Esta app no está afiliada ni respaldada por AMD.\n\n"
		+ "■ Efecto «IA» (NNEDI3)\n"
		+ "Pesos de la red NNEDI3 (tritical), tomados de los shaders de PPSSPP.\n\n"
		+ "■ Letras P, K, S, HS y D\n"
		+ "Dibujadas con Roboto Condensed (Google), licencia Apache 2.0, que ya incluye PPSSPP.\n\n"
		+ "■ Guilty Gear\n"
		+ "Guilty Gear es una marca de Arc System Works. Este proyecto no está afiliado ni respaldado por Arc System Works "
		+ "y no incluye ningún archivo del juego.";

	/** A special move button's input: chosen in the launcher, or the character's default. */
	private static String gestureKey(int id, int slot) {
		return K_GES_MOVE + (id == GESTURE_BB ? "bb" : String.valueOf(id)) + "_" + slot;
	}

	private String gestureMove(int id, int slot) {
		String custom = prefs.getString(gestureKey(id, slot), null);
		if (GestureMoves.valid(custom, id == GESTURE_BB)) {
			return custom;
		}
		return gestureDefault(id, slot);
	}

	private static String gestureDefault(int id, int slot) {
		if (id == GESTURE_BB) {
			return GestureMoves.GENERIC_BB[slot];
		}
		return id == 0 ? GestureMoves.GENERIC[slot] : GestureMoves.defaultMove(id, slot);
	}

	private String gestureCharName(int id) {
		return id == GESTURE_BB ? "BlazBlue" : id == 0 ? "General" : CHARACTERS[id];
	}

	private void refreshGestures() {
		boolean on = prefs.getBoolean(K_GESTURES, false);
		gestureSwitch.setChecked(on);
		gestureOptions.setAlpha(on ? 1f : 0.45f);
		int scale = prefs.getInt(K_GES_SCALE, 100);
		for (int i = 0; i < GES_SCALES.length; i++) {
			styleChip(gestureSizeChips[i], GES_SCALES[i] == scale);
		}
		int alpha = prefs.getInt(K_GES_ALPHA, 60);
		for (int i = 0; i < GES_ALPHAS.length; i++) {
			styleChip(gestureAlphaChips[i], GES_ALPHAS[i] == alpha);
		}
		int side = prefs.getInt(K_SIDE, 0);
		styleChip(gestureSide1p, side == 0);
		styleChip(gestureSide2p, side == 1);
		final boolean bb = isBlazBlue();
		final int shown = bb ? GESTURE_BB : gestureChar;
		gestureGuide.setText(bb ? GESTURE_GUIDE_BB : GESTURE_GUIDE);
		gestureCharButton.setText(bb ? "BlazBlue · los mismos para todos los personajes" : "Personaje: " + gestureCharName(gestureChar) + "  ▾");
		gestureCharButton.setEnabled(!bb);
		gestureMovesNote.setText(bb
			? "En BlazBlue los cuatro botones son los mismos con cualquier personaje (la app no lee qué personaje usas). Toca uno para cambiar su movimiento: 236 = ↓↘→, y los botones son A, B, C y D."
			: "Los botones cambian solos según tu personaje (versión ULUS-10409). Toca uno para cambiar su movimiento: la notación es la de los números del teclado, mirando a la derecha (236 = ↓↘→).");

		gestureMoveRows.removeAllViews();
		for (int slot = 0; slot < GestureMoves.SLOTS; slot++) {
			final int s = slot;
			String move = gestureMove(shown, slot);
			String name = shown <= 0 ? null : GestureMoves.name(shown, slot, move);
			boolean custom = prefs.getString(gestureKey(shown, slot), null) != null;
			LinearLayout row = new LinearLayout(this);
			row.setOrientation(LinearLayout.VERTICAL);
			row.setPadding(dp(14), dp(10), dp(14), dp(10));
			row.setBackground(ripple(Color.TRANSPARENT, C_CHIP_STROKE, 12));
			makeClickable(row);
			String slotName = bb && slot == 3 ? "Distortion Drive" : GestureMoves.SLOT_NAMES[slot];
			row.addView(text(slotName + "  ·  " + move, 15, C_TEXT, true));
			String detail = GestureMoves.arrows(move, bb) + (name != null ? "  ·  " + name : custom ? "  ·  elegido por ti" : "");
			row.addView(text(detail, 13, C_MUTED, false));
			row.setOnClickListener(v -> showMotionPicker(shown, s));
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			lp.topMargin = dp(8);
			gestureMoveRows.addView(row, lp);
		}
	}

	private void showGestureCharacterPicker() {
		String[] rows = new String[CHARACTERS.length];
		rows[0] = "General (otras versiones del juego o sin detectar)";
		for (int id = 1; id < CHARACTERS.length; id++) {
			rows[id] = CHARACTERS[id];
		}
		new AlertDialog.Builder(this)
			.setTitle("Especiales de…")
			.setItems(rows, (d, which) -> {
				gestureChar = which;
				refreshGestures();
			})
			.setNegativeButton("Cerrar", null)
			.show();
	}

	private void showMotionPicker(int id, int slot) {
		final String[][] motions = GestureMoves.MOTIONS;
		String[] rows = new String[motions.length + 1];
		for (int i = 0; i < motions.length; i++) {
			rows[i] = motions[i][1] + (motions[i][0].isEmpty() ? "" : "   (" + motions[i][0] + ")");
		}
		String def = gestureDefault(id, slot);
		rows[motions.length] = "Volver al predeterminado: " + def;
		new AlertDialog.Builder(this)
			.setTitle(gestureCharName(id) + " · " + GestureMoves.SLOT_NAMES[slot] + ": movimiento")
			.setItems(rows, (d, which) -> {
				if (which == motions.length) {
					prefs.edit().remove(gestureKey(id, slot)).apply();
					refreshGestures();
				} else {
					showButtonPicker(id, slot, motions[which][0]);
				}
			})
			.setNegativeButton("Cancelar", null)
			.show();
	}

	private void showButtonPicker(int id, int slot, String motion) {
		new AlertDialog.Builder(this)
			.setTitle((motion.isEmpty() ? "" : GestureMoves.arrows(motion) + " + ") + "¿qué botón?")
			.setItems(id == GESTURE_BB ? GestureMoves.BUTTON_NAMES_BB : GestureMoves.BUTTON_NAMES, (d, which) -> {
				String button = (id == GESTURE_BB ? GestureMoves.BUTTONS_BB : GestureMoves.BUTTONS)[which];
				prefs.edit().putString(gestureKey(id, slot), motion + button).apply();
				refreshGestures();
			})
			.setNegativeButton("Atrás", (d, w) -> showMotionPicker(id, slot))
			.show();
	}

	private void confirmResetGestureMoves(int id) {
		new AlertDialog.Builder(this)
			.setTitle("¿Restablecer los especiales de " + gestureCharName(id) + "?")
			.setMessage("Los cuatro botones vuelven a los movimientos predeterminados.")
			.setPositiveButton("Restablecer", (d, w) -> {
				SharedPreferences.Editor e = prefs.edit();
				for (int slot = 0; slot < GestureMoves.SLOTS; slot++) {
					e.remove(gestureKey(id, slot));
				}
				e.apply();
				refreshGestures();
			})
			.setNegativeButton("Cancelar", null)
			.show();
	}

	/** Diagnostics written by the emulator while the viewer is on (UI/AcPlusHitboxes.h). */
	private File hitboxReportFile() {
		return new File(memstickDir(), "PSP/SYSTEM/acplus_hitbox_report.txt");
	}

	/** The "status:" line of the last report, or null. */
	private String lastHitboxStatus() {
		File f = hitboxReportFile();
		if (!f.isFile()) {
			return null;
		}
		try (java.io.BufferedReader r = new java.io.BufferedReader(
				new java.io.InputStreamReader(new java.io.FileInputStream(f), StandardCharsets.UTF_8))) {
			for (int i = 0; i < 4; i++) {
				String line = r.readLine();
				if (line == null) {
					break;
				}
				if (line.startsWith("status:")) {
					return line.substring(7).replace("Hitboxes:", "").trim();
				}
			}
		} catch (IOException e) {
			Log.w(TAG, "Cannot read hitbox report", e);
		}
		return null;
	}

	private void refreshHitboxes() {
		boolean on = prefs.getBoolean(K_HB_ENABLED, false);
		hitboxSwitch.setChecked(on);
		for (int i = 0; i < HB_KEYS.length; i++) {
			hitboxParts[i].setChecked(prefs.getBoolean(HB_KEYS[i], true));
			hitboxParts[i].setEnabled(on);
		}
		hitboxManual.setEnabled(on);
		hitboxOptions.setAlpha(on ? 1f : 0.45f);

		StringBuilder s = new StringBuilder();
		if (!isProfileGame()) {
			s.append(isBlazBlue()
				? "⚠ Con BlazBlue no se usa: el visor lee la memoria de Guilty Gear XX ΛC+ (versión americana ULUS-10409).\n\n"
				: "⚠ Tu copia es " + prefs.getString(K_ID, "") + ". El visor solo funciona con la versión americana (ULUS-10409).\n\n");
		}
		s.append("Experimental. Al empezar cada combate tarda unos segundos en encontrar las cajas: el progreso sale abajo a la izquierda del juego. "
			+ "Muévete un poco hacia los lados para que encuentre la cámara. Funciona mejor en «Training».");
		String last = lastHitboxStatus();
		if (last != null) {
			s.append("\n\nÚltima partida: ").append(last.isEmpty() ? "funcionando" : last).append('.');
		}
		s.append("\n\nSi las cajas no coinciden con los personajes, guarda un estado en ese momento (Atrás → Guardar estado), sal y pulsa «Enviar informe del visor».");
		hitboxStatus.setText(s.toString());
	}

	/** Turns "P1=0x0913D000 BoxPtr=0x54; Scale=0.0075" into ini lines, keeping known keys only. */
	static String hitboxManualLines(String text) {
		StringBuilder out = new StringBuilder();
		if (text == null) {
			return "";
		}
		for (String token : text.split("[\\s;,]+")) {
			int eq = token.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			String key = token.substring(0, eq).trim();
			String value = token.substring(eq + 1).trim();
			if (!value.matches("-?(0[xX][0-9a-fA-F]+|[0-9]+(\\.[0-9]+)?)")) {
				continue;
			}
			for (String known : HB_MANUAL_KEYS) {
				if (known.equalsIgnoreCase(key)) {
					out.append(known).append(" = ").append(value).append('\n');
				}
			}
		}
		return out.toString();
	}

	private void refreshNetwork() {
		String mode = prefs.getString(K_NET_MODE, NET_OFF);
		for (int i = 0; i < NET_MODES.length; i++) {
			styleChip(netChips[i], NET_MODES[i].equals(mode));
		}
		netHostPanel.setVisibility(NET_HOST.equals(mode) ? View.VISIBLE : View.GONE);
		netJoinPanel.setVisibility(NET_JOIN.equals(mode) ? View.VISIBLE : View.GONE);
		netInternetPanel.setVisibility(NET_INTERNET.equals(mode) ? View.VISIBLE : View.GONE);

		if (NET_HOST.equals(mode)) {
			List<RoomCode.LocalAddress> addresses = RoomCode.localAddresses();
			if (addresses.isEmpty()) {
				hostCode.setText("— — — —");
				hostDetail.setText("No estás conectado a ninguna red. Conéctate al mismo Wi-Fi que tu amigo, o activa la zona Wi-Fi del teléfono y que tu amigo se conecte a ella.");
				hostDetail.setTextColor(C_WARN);
			} else {
				RoomCode.LocalAddress main = addresses.get(0);
				hostCode.setText(RoomCode.encode(main.bytes));
				StringBuilder d = new StringBuilder("Dile este código a tu amigo. ");
				d.append(main.isHotspot() ? "Estás compartiendo tu zona Wi-Fi: tu amigo debe conectarse a ella." : "Tu amigo debe estar en tu misma red Wi-Fi.");
				d.append("\nDirección: ").append(main.ip);
				for (int i = 1; i < addresses.size(); i++) {
					RoomCode.LocalAddress other = addresses.get(i);
					d.append("\nEn tu otra red (").append(other.isHotspot() ? "zona Wi-Fi" : other.iface).append("): ")
						.append(RoomCode.encode(other.bytes));
				}
				hostDetail.setText(d.toString());
				hostDetail.setTextColor(C_MUTED);
			}
		}
		if (NET_JOIN.equals(mode)) {
			refreshJoinStatus();
		}
		if (NET_INTERNET.equals(mode)) {
			String server = currentServer();
			for (int i = 0; i < NET_SERVERS.length; i++) {
				styleChip(serverChips[i], NET_SERVERS[i][0].equals(server));
			}
		}

		String steps;
		switch (mode) {
			case NET_HOST:
				steps = "1. Pulsa JUGAR primero (tu teléfono hace de sala).\n2. Tu amigo elige «Unirme», escribe el código y pulsa JUGAR.\n3. En el menú del juego entren los dos a «Network»: tú creas la partida y tu amigo se une.";
				break;
			case NET_JOIN:
				steps = "1. Espera a que tu amigo haya pulsado JUGAR en «Crear sala».\n2. Pulsa JUGAR.\n3. En el menú del juego entra a «Network» y únete a la partida de tu amigo.";
				break;
			case NET_INTERNET:
				steps = "Beta. Los dos eligen el mismo servidor y pulsan JUGAR. En el modo «Network» del juego verán a quien esté conectado a ese servidor con este juego. Son servidores públicos de la comunidad de PPSSPP: puede haber retraso.";
				break;
			default:
				steps = "Para jugar contra un amigo con otro teléfono: el modo «Network» del juego (2 jugadores), llevado a Wi-Fi por PPSSPP.";
				break;
		}
		netSteps.setText(steps);
	}

	private void refreshJoinStatus() {
		String code = joinInput.getText().toString();
		String ip = RoomCode.decode(code);
		if (ip == null) {
			String clean = code.replace("-", "").replace(" ", "");
			joinStatus.setText(clean.isEmpty() ? "Escribe el código que aparece en el teléfono de tu amigo."
				: clean.length() < RoomCode.LENGTH ? "Faltan " + (RoomCode.LENGTH - clean.length()) + " caracteres."
				: "Código no válido. Revísalo con tu amigo.");
			joinStatus.setTextColor(clean.length() >= RoomCode.LENGTH ? C_WARN : C_MUTED);
			return;
		}
		String text = "✓ Sala en " + ip;
		int color = C_OK;
		List<RoomCode.LocalAddress> mine = RoomCode.localAddresses();
		if (mine.isEmpty()) {
			text += "\n⚠ No estás conectado a ninguna red Wi-Fi.";
			color = C_WARN;
		} else {
			boolean sameNet = false;
			String prefix = ip.substring(0, ip.lastIndexOf('.') + 1);
			for (RoomCode.LocalAddress a : mine) {
				if (a.ip.startsWith(prefix)) {
					sameNet = true;
					break;
				}
			}
			if (!sameNet) {
				text += "\n⚠ Parece que estás en otra red (tu dirección es " + mine.get(0).ip + "). Conéctate al mismo Wi-Fi o a la zona Wi-Fi de tu amigo.";
				color = C_WARN;
			}
		}
		joinStatus.setText(text);
		joinStatus.setTextColor(color);
	}

	private void shareRoomCode() {
		String code = hostCode.getText().toString();
		if (RoomCode.decode(code) == null) {
			return;
		}
		Intent send = new Intent(Intent.ACTION_SEND);
		send.setType("text/plain");
		send.putExtra(Intent.EXTRA_TEXT, "Únete a mi sala de " + gameShortName() + " (GGXX AC+ en Android → Multijugador → Unirme): " + code);
		startActivity(Intent.createChooser(send, "Compartir código de sala"));
	}

	/**
	 * Shares the viewer report plus the newest save states (and their screenshots), so a wrong
	 * detection can be checked against the real game memory.
	 */
	private void shareHitboxReport() {
		ArrayList<File> files = new ArrayList<>();
		File report = hitboxReportFile();
		if (report.isFile()) {
			files.add(report);
		}
		File[] states = new File(memstickDir(), "PSP/PPSSPP_STATE").listFiles((d, name) -> name.endsWith(".ppst") || name.endsWith(".jpg"));
		if (states != null) {
			java.util.Arrays.sort(states, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
			for (int i = 0; i < states.length && i < 8; i++) {
				files.add(states[i]);
			}
		}
		if (files.isEmpty()) {
			showMessage("Todavía no hay informe",
				"Activa el visor, juega un combate unos segundos y vuelve aquí. Si las cajas salen mal, guarda también un estado en ese momento (Atrás → «Guardar estado»).");
			return;
		}
		shareFiles(files, "Informe del visor de hitboxes de GGXX AC+", "Enviar informe del visor");
	}

	private void shareFiles(List<File> files, String text, String title) {
		ArrayList<Uri> uris = new ArrayList<>();
		long total = 0;
		String authority = getPackageName() + ".acplus.files";
		for (File f : files) {
			if (uris.size() >= 12) {
				break;
			}
			try {
				uris.add(androidx.core.content.FileProvider.getUriForFile(this, authority, f));
				total += f.length();
			} catch (IllegalArgumentException e) {
				Log.w(TAG, "Cannot share " + f, e);
			}
		}
		if (uris.isEmpty()) {
			showMessage("Error", "No se pudieron preparar los archivos para enviar.");
			return;
		}
		Intent send = new Intent(Intent.ACTION_SEND_MULTIPLE);
		send.setType("*/*");
		send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
		send.putExtra(Intent.EXTRA_TEXT, text);
		send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		Log.i(TAG, "Sharing " + uris.size() + " files, " + total + " bytes");
		try {
			startActivity(Intent.createChooser(send, title + " (" + (total / (1024 * 1024) + 1) + " MB)"));
		} catch (ActivityNotFoundException e) {
			showMessage("Error", "No hay ninguna app para enviar archivos.");
		}
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
		boolean bb = isBlazBlue();
		controlsPreview.setBlazBlue(bb);
		if (bb) {
			styleDesc.setText(arcade
				? "Como en la máquina de BlazBlue: A, B y C arriba y D (Drive) abajo. La cruceta, el stick, L, R, Start y Select siguen ahí."
				: "Como en la consola: △ ○ ✕ □, L y R, cruceta, stick, Start y Select. En BlazBlue: □ = A, △ = B, ○ = C, ✕ = D.");
			padNote.setText("Los controles funcionan solos: X = A (□), Y = B (△), B = C (○) y A = D (✕). Puedes reasignarlos dentro del juego en sus opciones.");
		} else if (arcade) {
			styleDesc.setText("Como en la máquina de Guilty Gear: K, S y HS arriba; P y D abajo. "
				+ "La cruceta, el stick, L (Respeto), Start y Select siguen ahí.");
		} else {
			styleDesc.setText("Como en la consola: △ ○ ✕ □, L y R, cruceta, stick, Start y Select. "
				+ "En este juego: □ = P, ✕ = K, △ = S, ○ = HS, R = D.");
		}
		if (!bb) {
			padNote.setText("Los controles funcionan solos: X = P (□), A = K (✕), Y = S (△), B = HS (○), R1 = D y L1 = Respeto. Puedes reasignarlos dentro del juego en Ajustes → Controles.");
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
		mainScroll = scroll;
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
		headerKicker = text("GUILTY GEAR XX", 13, C_ACCENT, true);
		headerKicker.setLetterSpacing(0.3f);
		col.addView(headerKicker);
		headerTitle = text("ΛCORE PLUS", 38, C_TEXT, true);
		headerTitle.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
		headerTitle.setLetterSpacing(0.02f);
		col.addView(headerTitle);
		headerSubtitle = text("Edición PSP · listo para jugar en Android", 14, C_MUTED, false);
		col.addView(headerSubtitle);

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
		TextView pick = outlineButton("Añadir o cambiar de juego (ISO / CSO)");
		pick.setOnClickListener(v -> pickGame());
		game.addView(pick, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		libraryBox = new LinearLayout(this);
		libraryBox.setOrientation(LinearLayout.VERTICAL);
		game.addView(libraryBox);

		// Device test (DeviceBenchmark)
		LinearLayout bench = section(col, "TU EQUIPO");
		benchSummary = text("", 17, C_TEXT, true);
		bench.addView(benchSummary);
		benchDetail = text("", 13, C_MUTED, false);
		benchDetail.setPadding(0, dp(6), 0, dp(12));
		bench.addView(benchDetail);
		LinearLayout benchButtons = new LinearLayout(this);
		benchButtons.setOrientation(LinearLayout.HORIZONTAL);
		benchApply = filledButton("Aplicar recomendados", 15);
		benchApply.setOnClickListener(v -> {
			DeviceBenchmark.Result r = DeviceBenchmark.Result.fromJson(prefs.getString(K_BENCH, null));
			if (r != null) {
				applyBenchmark(r);
			}
		});
		benchRun = outlineButton("Probar mi equipo");
		benchRun.setOnClickListener(v -> runBenchmark(false));
		benchButtons.addView(benchApply, weighted(0, dp(4)));
		benchButtons.addView(benchRun, weighted(dp(4), 0));
		bench.addView(benchButtons);

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
					// Effects and FSR are one or the other.
					prefs.edit().putString(K_EFFECT, effect.id).putBoolean(K_FSR_ON + fsrGameKey(), false).apply();
					refreshEffect();
					refreshResolution();
					refreshFsr();
				});
				effectChips[index] = chip;
				LinearLayout.LayoutParams lp = weighted(c == 0 ? 0 : dp(4), c == 3 ? 0 : dp(4));
				lp.bottomMargin = dp(8);
				chips.addView(chip, lp);
			}
			effects.addView(chips);
			effectRows.add(chips);
		}

		// The one special option: AMD FSR 1.0 (its settings are under "Gráficos").
		LinearLayout amdButton = new LinearLayout(this);
		amdButton.setOrientation(LinearLayout.VERTICAL);
		amdButton.setGravity(Gravity.CENTER);
		amdButton.setPadding(dp(14), dp(12), dp(14), dp(12));
		GradientDrawable amdBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] { 0xFF2A0B10, 0xFF12090B });
		amdBg.setCornerRadius(dp(12));
		amdBg.setStroke(dp(1.5f), C_ACCENT);
		GradientDrawable amdMask = new GradientDrawable();
		amdMask.setColor(Color.WHITE);
		amdMask.setCornerRadius(dp(12));
		amdButton.setBackground(new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), amdBg, amdMask));
		makeClickable(amdButton);
		TextView amdTitle = text("BETA · AMD GRAPHICS", 16, Color.WHITE, true);
		amdTitle.setLetterSpacing(0.16f);
		amdTitle.setGravity(Gravity.CENTER);
		amdButton.addView(amdTitle);
		amdButtonStatus = text("", 12, C_MUTED, false);
		amdButtonStatus.setGravity(Gravity.CENTER);
		amdButtonStatus.setPadding(0, dp(2), 0, 0);
		amdButton.addView(amdButtonStatus);
		amdButton.setOnClickListener(v -> showFsrPanel());
		LinearLayout.LayoutParams amdParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		amdParams.topMargin = dp(4);
		amdParams.bottomMargin = dp(10);
		effects.addView(amdButton, amdParams);

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
		padNote = text("", 13, C_MUTED, false);
		padNote.setPadding(0, dp(12), 0, 0);
		controls.addView(padNote);

		// Gesture controls (UI/AcPlusGestures.h)
		LinearLayout gestures = section(col, "CONTROLES POR GESTOS");
		gestureSwitch = switchRow(gestures, "Jugar con gestos (estilo Skullgirls)",
			"Toques y deslizamientos en vez de botones, y cada especial con un solo botón. Sustituye a los botones en pantalla; el control Bluetooth sigue funcionando.");
		gestureSwitch.setOnCheckedChangeListener((b, checked) -> {
			prefs.edit().putBoolean(K_GESTURES, checked).apply();
			refreshGestures();
		});
		gestureOptions = new LinearLayout(this);
		gestureOptions.setOrientation(LinearLayout.VERTICAL);
		gestureOptions.setPadding(0, dp(14), 0, 0);
		gestures.addView(gestureOptions);
		gestureGuide = text(GESTURE_GUIDE, 13, C_TEXT, false);
		gestureOptions.addView(gestureGuide);

		TextView sizeLabel = text("Tamaño de los botones", 15, C_TEXT, true);
		sizeLabel.setPadding(0, dp(16), 0, 0);
		gestureOptions.addView(sizeLabel);
		LinearLayout sizeRow = new LinearLayout(this);
		sizeRow.setOrientation(LinearLayout.HORIZONTAL);
		sizeRow.setPadding(0, dp(10), 0, 0);
		for (int i = 0; i < GES_SCALES.length; i++) {
			final int value = GES_SCALES[i];
			gestureSizeChips[i] = chip(GES_SCALE_LABELS[i]);
			gestureSizeChips[i].setOnClickListener(v -> {
				prefs.edit().putInt(K_GES_SCALE, value).apply();
				refreshGestures();
			});
			sizeRow.addView(gestureSizeChips[i], weighted(i == 0 ? 0 : dp(4), i == GES_SCALES.length - 1 ? 0 : dp(4)));
		}
		gestureOptions.addView(sizeRow);

		TextView alphaLabel = text("Visibilidad", 15, C_TEXT, true);
		alphaLabel.setPadding(0, dp(16), 0, 0);
		gestureOptions.addView(alphaLabel);
		LinearLayout alphaRow = new LinearLayout(this);
		alphaRow.setOrientation(LinearLayout.HORIZONTAL);
		alphaRow.setPadding(0, dp(10), 0, 0);
		for (int i = 0; i < GES_ALPHAS.length; i++) {
			final int value = GES_ALPHAS[i];
			gestureAlphaChips[i] = chip(GES_ALPHA_LABELS[i]);
			gestureAlphaChips[i].setOnClickListener(v -> {
				prefs.edit().putInt(K_GES_ALPHA, value).apply();
				refreshGestures();
			});
			alphaRow.addView(gestureAlphaChips[i], weighted(i == 0 ? 0 : dp(4), i == GES_ALPHAS.length - 1 ? 0 : dp(4)));
		}
		gestureOptions.addView(alphaRow);

		TextView gestureSideLabel = text("Tú juegas como", 15, C_TEXT, true);
		gestureSideLabel.setPadding(0, dp(16), 0, 0);
		gestureOptions.addView(gestureSideLabel);
		LinearLayout gestureSideRow = new LinearLayout(this);
		gestureSideRow.setOrientation(LinearLayout.HORIZONTAL);
		gestureSideRow.setPadding(0, dp(10), 0, 0);
		gestureSide1p = chip("Jugador 1");
		gestureSide1p.setOnClickListener(v -> {
			prefs.edit().putInt(K_SIDE, 0).apply();
			refreshGestures();
			refreshProfiles();
		});
		gestureSide2p = chip("Jugador 2");
		gestureSide2p.setOnClickListener(v -> {
			prefs.edit().putInt(K_SIDE, 1).apply();
			refreshGestures();
			refreshProfiles();
		});
		gestureSideRow.addView(gestureSide1p, weighted(0, dp(4)));
		gestureSideRow.addView(gestureSide2p, weighted(dp(4), 0));
		gestureOptions.addView(gestureSideRow);
		TextView gestureSideNote = text("Sirve para saber cuál es tu personaje (y sus especiales) y hacia dónde miras al empezar.", 13, C_MUTED, false);
		gestureSideNote.setPadding(0, dp(8), 0, 0);
		gestureOptions.addView(gestureSideNote);

		TextView movesLabel = text("Especiales de cada personaje", 15, C_TEXT, true);
		movesLabel.setPadding(0, dp(18), 0, 0);
		gestureOptions.addView(movesLabel);
		gestureMovesNote = text("", 13, C_MUTED, false);
		gestureMovesNote.setPadding(0, dp(4), 0, dp(10));
		gestureOptions.addView(gestureMovesNote);
		gestureCharButton = outlineButton("Personaje");
		gestureCharButton.setOnClickListener(v -> showGestureCharacterPicker());
		gestureOptions.addView(gestureCharButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		gestureMoveRows = new LinearLayout(this);
		gestureMoveRows.setOrientation(LinearLayout.VERTICAL);
		gestureOptions.addView(gestureMoveRows);
		TextView resetMoves = outlineButton("Restablecer este personaje");
		resetMoves.setOnClickListener(v -> confirmResetGestureMoves(isBlazBlue() ? GESTURE_BB : gestureChar));
		LinearLayout.LayoutParams resetMovesParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		resetMovesParams.topMargin = dp(12);
		gestureOptions.addView(resetMoves, resetMovesParams);
		TextView gestureNote = text("Algunos personajes solo traen especiales genéricos: si uno no sale, cámbialo por el movimiento correcto. Con gestos no se usan los perfiles de botones por personaje.", 13, C_MUTED, false);
		gestureNote.setPadding(0, dp(12), 0, 0);
		gestureOptions.addView(gestureNote);

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

		// Multiplayer (the game's ad hoc "Network" mode over Wi-Fi)
		LinearLayout net = section(col, "MULTIJUGADOR");
		net.addView(text("Tu nombre en el juego", 15, C_TEXT, true));
		nickInput = input("Jugador", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME, 16);
		nickInput.setText(prefs.getString(K_NICK, ""));
		nickInput.addTextChangedListener(new SimpleWatcher(s -> prefs.edit().putString(K_NICK, s).apply()));
		LinearLayout.LayoutParams nickParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		nickParams.topMargin = dp(8);
		nickParams.bottomMargin = dp(14);
		net.addView(nickInput, nickParams);

		LinearLayout netRow = new LinearLayout(this);
		netRow.setOrientation(LinearLayout.HORIZONTAL);
		for (int i = 0; i < NET_MODES.length; i++) {
			final String mode = NET_MODES[i];
			TextView chip = chip(NET_LABELS[i]);
			chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
			chip.setOnClickListener(v -> {
				prefs.edit().putString(K_NET_MODE, mode).apply();
				refreshNetwork();
			});
			netChips[i] = chip;
			netRow.addView(chip, weighted(i == 0 ? 0 : dp(3), i == NET_MODES.length - 1 ? 0 : dp(3)));
		}
		net.addView(netRow);

		// Host: shows the room code.
		netHostPanel = new LinearLayout(this);
		netHostPanel.setOrientation(LinearLayout.VERTICAL);
		netHostPanel.setPadding(0, dp(16), 0, 0);
		netHostPanel.addView(text("Código de tu sala", 13, C_MUTED, true));
		hostCode = text("", 34, C_TEXT, true);
		hostCode.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
		hostCode.setLetterSpacing(0.12f);
		hostCode.setTextIsSelectable(true);
		hostCode.setPadding(0, dp(4), 0, dp(4));
		netHostPanel.addView(hostCode);
		hostDetail = text("", 13, C_MUTED, false);
		netHostPanel.addView(hostDetail);
		TextView shareCode = outlineButton("Compartir código");
		shareCode.setOnClickListener(v -> shareRoomCode());
		LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		shareParams.topMargin = dp(10);
		netHostPanel.addView(shareCode, shareParams);
		net.addView(netHostPanel);

		// Guest: types the room code.
		netJoinPanel = new LinearLayout(this);
		netJoinPanel.setOrientation(LinearLayout.VERTICAL);
		netJoinPanel.setPadding(0, dp(16), 0, 0);
		netJoinPanel.addView(text("Código de la sala de tu amigo", 13, C_MUTED, true));
		joinInput = input("XXXX-XXX", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, 9);
		joinInput.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
		joinInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
		joinInput.setLetterSpacing(0.1f);
		joinInput.setFilters(new InputFilter[] { new InputFilter.AllCaps(), new InputFilter.LengthFilter(9) });
		joinInput.setText(prefs.getString(K_JOIN_CODE, ""));
		joinInput.addTextChangedListener(new SimpleWatcher(s -> {
			prefs.edit().putString(K_JOIN_CODE, s).apply();
			refreshJoinStatus();
		}));
		LinearLayout.LayoutParams joinParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		joinParams.topMargin = dp(6);
		netJoinPanel.addView(joinInput, joinParams);
		joinStatus = text("", 13, C_MUTED, false);
		joinStatus.setPadding(0, dp(6), 0, 0);
		netJoinPanel.addView(joinStatus);
		net.addView(netJoinPanel);

		// Internet (beta): public relay servers.
		netInternetPanel = new LinearLayout(this);
		netInternetPanel.setOrientation(LinearLayout.VERTICAL);
		netInternetPanel.setPadding(0, dp(16), 0, 0);
		netInternetPanel.addView(text("Servidor", 13, C_MUTED, true));
		LinearLayout serverRow = new LinearLayout(this);
		serverRow.setOrientation(LinearLayout.HORIZONTAL);
		serverRow.setPadding(0, dp(8), 0, 0);
		for (int i = 0; i < NET_SERVERS.length; i++) {
			final String host = NET_SERVERS[i][0];
			TextView chip = chip(NET_SERVERS[i][1] + "\n" + NET_SERVERS[i][2]);
			chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
			chip.setOnClickListener(v -> {
				prefs.edit().putString(K_NET_SERVER, host).apply();
				refreshNetwork();
			});
			serverChips[i] = chip;
			serverRow.addView(chip, weighted(i == 0 ? 0 : dp(3), i == NET_SERVERS.length - 1 ? 0 : dp(3)));
		}
		netInternetPanel.addView(serverRow);
		net.addView(netInternetPanel);

		netSteps = text("", 13, C_MUTED, false);
		netSteps.setPadding(0, dp(14), 0, 0);
		net.addView(netSteps);
		TextView netNote = text("Mientras estén conectados no se pueden guardar ni cargar estados rápidos.", 12, 0xFF6C6C78, false);
		netNote.setPadding(0, dp(8), 0, 0);
		net.addView(netNote);

		// Hitbox viewer (drawn by the emulator, UI/AcPlusHitboxes.h)
		LinearLayout hitbox = section(col, "VISOR DE HITBOXES");
		hitboxSwitch = switchRow(hitbox, "Mostrar hitboxes y hurtboxes",
			"Dibuja encima del juego las cajas de golpe y de daño de cada personaje mientras juegas.");
		hitboxSwitch.setOnCheckedChangeListener((b, checked) -> {
			prefs.edit().putBoolean(K_HB_ENABLED, checked).apply();
			refreshHitboxes();
		});
		hitboxOptions = new LinearLayout(this);
		hitboxOptions.setOrientation(LinearLayout.VERTICAL);
		hitboxOptions.setPadding(0, dp(16), 0, 0);
		hitbox.addView(hitboxOptions);
		String[][] hitboxPartLabels = {
			{ "Hurtboxes · verde", "Donde el personaje recibe daño (en azul claro, otras zonas de daño)." },
			{ "Hitboxes · rojo", "Lo que golpea durante un ataque." },
			{ "Posición · cruz blanca", "El punto de apoyo de cada personaje." },
			{ "Proyectiles", "Bolas de fuego, invocaciones y otros objetos: rojo si golpean, naranja si se pueden golpear." },
		};
		for (int i = 0; i < HB_KEYS.length; i++) {
			final String key = HB_KEYS[i];
			hitboxParts[i] = switchRow(hitboxOptions, hitboxPartLabels[i][0], hitboxPartLabels[i][1]);
			hitboxParts[i].setOnCheckedChangeListener((b, checked) -> prefs.edit().putBoolean(key, checked).apply());
			hitboxOptions.addView(new View(this), new LinearLayout.LayoutParams(1, dp(12)));
		}
		hitboxStatus = text("", 13, C_MUTED, false);
		hitboxStatus.setPadding(0, dp(4), 0, dp(12));
		hitboxOptions.addView(hitboxStatus);
		TextView sendReport = outlineButton("Enviar informe del visor");
		sendReport.setOnClickListener(v -> shareHitboxReport());
		hitboxOptions.addView(sendReport, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		hitboxManual = input("Ajuste manual (solo si te lo piden)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, 160);
		hitboxManual.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		hitboxManual.setText(prefs.getString(K_HB_MANUAL, ""));
		hitboxManual.addTextChangedListener(new SimpleWatcher(s -> prefs.edit().putString(K_HB_MANUAL, s.trim()).apply()));
		LinearLayout.LayoutParams manualParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		manualParams.topMargin = dp(12);
		hitboxOptions.addView(hitboxManual, manualParams);

		// Custom HUD (UI/AcPlusHud.h)
		LinearLayout hud = section(col, "HUD PROPIO");
		hudSwitch = switchRow(hud, "Mostrar mi HUD",
			"Barras de vida, tensión y burst, y contador de combos con su daño, leídos de la memoria del juego y dibujados encima. Versión americana (ULUS-10409).");
		hudSwitch.setOnCheckedChangeListener((b, checked) -> {
			prefs.edit().putBoolean(K_HUD_ON, checked).apply();
			refreshHud();
		});
		hudOptions = new LinearLayout(this);
		hudOptions.setOrientation(LinearLayout.VERTICAL);
		hudOptions.setPadding(0, dp(14), 0, 0);
		hud.addView(hudOptions);
		hudOptions.addView(text("Diseño", 15, C_TEXT, true));
		LinearLayout hudRow = new LinearLayout(this);
		hudRow.setOrientation(LinearLayout.HORIZONTAL);
		hudRow.setPadding(0, dp(10), 0, dp(10));
		for (int i = 0; i < HUD_NAMES.length; i++) {
			final int design = i + 1;
			hudChips[i] = chip(HUD_NAMES[i]);
			hudChips[i].setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
			hudChips[i].setOnClickListener(v -> {
				prefs.edit().putInt(K_HUD_DESIGN, design).apply();
				refreshHud();
			});
			hudRow.addView(hudChips[i], weighted(i == 0 ? 0 : dp(4), i == HUD_NAMES.length - 1 ? 0 : dp(4)));
		}
		hudOptions.addView(hudRow);
		hudPreview = new ImageView(this);
		hudPreview.setAdjustViewBounds(true);
		hudPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
		GradientDrawable hudBg = new GradientDrawable();
		hudBg.setColor(Color.BLACK);
		hudBg.setCornerRadius(dp(10));
		hudPreview.setBackground(hudBg);
		hudPreview.setClipToOutline(true);
		hudOptions.addView(hudPreview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		TextView hudPreviewNote = text("Vista previa en un teléfono alargado, con una imagen de ejemplo (no es del juego).", 11, 0xFF6C6C78, false);
		hudPreviewNote.setPadding(0, dp(4), 0, 0);
		hudOptions.addView(hudPreviewNote);
		hudDesc = text("", 13, C_MUTED, false);
		hudDesc.setPadding(0, dp(8), 0, 0);
		hudOptions.addView(hudDesc);
		TextView hudAlphaLabel = text("Visibilidad", 15, C_TEXT, true);
		hudAlphaLabel.setPadding(0, dp(14), 0, 0);
		hudOptions.addView(hudAlphaLabel);
		LinearLayout hudAlphaRow = new LinearLayout(this);
		hudAlphaRow.setOrientation(LinearLayout.HORIZONTAL);
		hudAlphaRow.setPadding(0, dp(10), 0, 0);
		for (int i = 0; i < HUD_ALPHAS.length; i++) {
			final int value = HUD_ALPHAS[i];
			hudAlphaChips[i] = chip(value + " %");
			hudAlphaChips[i].setOnClickListener(v -> {
				prefs.edit().putInt(K_HUD_ALPHA, value).apply();
				refreshHud();
			});
			hudAlphaRow.addView(hudAlphaChips[i], weighted(i == 0 ? 0 : dp(4), i == HUD_ALPHAS.length - 1 ? 0 : dp(4)));
		}
		hudOptions.addView(hudAlphaRow);
		TextView hudNote = text("Aparece en los combates y se esconde en los menús. Va encima del HUD del juego: «Neón» y «Arcade» lo tapan con franjas oscuras, «Lateral» y «Datos» lo dejan a la vista.", 13, C_MUTED, false);
		hudNote.setPadding(0, dp(12), 0, 0);
		hudOptions.addView(hudNote);

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

		// AMD FSR 1.0 (beta): shaders in assets/shaders (acplus_fsr1_*.fsh), sizing in patches/0005.
		fsrPanel = new LinearLayout(this);
		fsrPanel.setOrientation(LinearLayout.VERTICAL);
		fsrPanel.setPadding(dp(14), dp(14), dp(14), dp(14));
		GradientDrawable fsrBg = new GradientDrawable();
		fsrBg.setColor(0xFF1A0E11);
		fsrBg.setCornerRadius(dp(12));
		fsrBg.setStroke(dp(1), 0xFF5A1F28);
		fsrPanel.setBackground(fsrBg);
		LinearLayout.LayoutParams fsrParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		fsrParams.topMargin = dp(18);
		graphics.addView(fsrPanel, fsrParams);
		TextView fsrBadge = text("BETA · AMD GRAPHICS", 12, C_ACCENT, true);
		fsrBadge.setLetterSpacing(0.18f);
		fsrPanel.addView(fsrBadge);
		TextView fsrTitle = text("AMD FidelityFX Super Resolution 1.0", 16, C_TEXT, true);
		fsrTitle.setPadding(0, dp(4), 0, dp(12));
		fsrPanel.addView(fsrTitle);
		fsrSwitch = switchRow(fsrPanel, "Activar FSR",
			"EASU escala la imagen del juego y RCAS le devuelve la nitidez. Solo trabaja sobre la imagen final: los sprites no se tocan.");
		fsrSwitch.setOnCheckedChangeListener((b, checked) -> {
			if (checked == fsrOn()) {
				return;
			}
			prefs.edit().putBoolean(K_FSR_ON + fsrGameKey(), checked).apply();
			refreshFsr();
			refreshEffect();
			refreshResolution();
		});
		fsrOptions = new LinearLayout(this);
		fsrOptions.setOrientation(LinearLayout.VERTICAL);
		fsrOptions.setPadding(0, dp(14), 0, 0);
		fsrPanel.addView(fsrOptions);

		fsrOptions.addView(text("Factor de escala", 15, C_TEXT, true));
		LinearLayout fsrScaleRow = new LinearLayout(this);
		fsrScaleRow.setOrientation(LinearLayout.HORIZONTAL);
		fsrScaleRow.setPadding(0, dp(10), 0, 0);
		for (int i = 0; i < FSR_SCALES.length; i++) {
			final int value = FSR_SCALES[i];
			fsrScaleChips[i] = chip(FSR_SCALE_LABELS[i]);
			fsrScaleChips[i].setOnClickListener(v -> {
				prefs.edit().putInt(K_FSR_SCALE + fsrGameKey(), value).apply();
				refreshFsr();
				refreshEffect();
				refreshResolution();
			});
			fsrScaleRow.addView(fsrScaleChips[i], weighted(i == 0 ? 0 : dp(4), i == FSR_SCALES.length - 1 ? 0 : dp(4)));
		}
		fsrOptions.addView(fsrScaleRow);

		LinearLayout sharpRow = new LinearLayout(this);
		sharpRow.setOrientation(LinearLayout.HORIZONTAL);
		sharpRow.setPadding(0, dp(16), 0, 0);
		sharpRow.addView(text("Nitidez RCAS", 15, C_TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		fsrSharpValue = text("", 15, C_ACCENT, true);
		sharpRow.addView(fsrSharpValue);
		fsrOptions.addView(sharpRow);
		fsrSeek = new SeekBar(this);
		fsrSeek.setMax(100);
		if (Build.VERSION.SDK_INT >= 21) {
			fsrSeek.setProgressTintList(ColorStateList.valueOf(C_ACCENT));
			fsrSeek.setThumbTintList(ColorStateList.valueOf(C_ACCENT));
			fsrSeek.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF4A4A55));
		}
		fsrSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
				if (!fromUser) {
					return;
				}
				prefs.edit().putInt(K_FSR_SHARP + fsrGameKey(), progress).apply();
				fsrSharpValue.setText(sharpLabel(progress));
				refreshAmdButton();
			}

			@Override
			public void onStartTrackingTouch(SeekBar bar) {}

			@Override
			public void onStopTrackingTouch(SeekBar bar) {}
		});
		LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		seekParams.topMargin = dp(6);
		fsrOptions.addView(fsrSeek, seekParams);
		TextView sharpHint = text("0 % = sin afinar · 100 % = el máximo de AMD. Lo normal es 70–90 %.", 12, C_MUTED, false);
		fsrOptions.addView(sharpHint);

		fsrInfo = text("", 13, C_TEXT, false);
		fsrInfo.setPadding(0, dp(14), 0, 0);
		fsrOptions.addView(fsrInfo);
		fsrWarn = text("", 13, C_WARN, false);
		fsrWarn.setPadding(0, dp(8), 0, 0);
		fsrOptions.addView(fsrWarn);
		fsrNote = text("", 12, C_MUTED, false);
		fsrNote.setPadding(0, dp(10), 0, 0);
		fsrPanel.addView(fsrNote);
		TextView fsrCredits = outlineButton("Créditos y licencia de AMD");
		fsrCredits.setOnClickListener(v -> showCredits());
		LinearLayout.LayoutParams fsrCreditsParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		fsrCreditsParams.topMargin = dp(12);
		fsrPanel.addView(fsrCredits, fsrCreditsParams);

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
		TextView credits = text("Créditos y licencias", 13, C_MUTED, true);
		credits.setGravity(Gravity.CENTER_HORIZONTAL);
		credits.setPadding(dp(8), dp(10), dp(8), dp(10));
		makeClickable(credits);
		credits.setOnClickListener(v -> showCredits());
		col.addView(credits);

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

	private EditText input(String hint, int inputType, int maxLength) {
		EditText e = new EditText(this);
		e.setHint(hint);
		e.setHintTextColor(0xFF5A5A66);
		e.setTextColor(C_TEXT);
		e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
		e.setInputType(inputType);
		e.setSingleLine(true);
		e.setFilters(new InputFilter[] { new InputFilter.LengthFilter(maxLength) });
		e.setPadding(dp(14), dp(12), dp(14), dp(12));
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(0xFF101015);
		bg.setCornerRadius(dp(12));
		bg.setStroke(dp(1.5f), C_CHIP_STROKE);
		e.setBackground(bg);
		return e;
	}

	/** TextWatcher that only cares about the final text. */
	private static final class SimpleWatcher implements TextWatcher {
		interface Listener {
			void onText(String text);
		}

		private final Listener listener;

		SimpleWatcher(Listener listener) {
			this.listener = listener;
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {}

		@Override
		public void afterTextChanged(Editable s) {
			listener.onText(s.toString());
		}
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
