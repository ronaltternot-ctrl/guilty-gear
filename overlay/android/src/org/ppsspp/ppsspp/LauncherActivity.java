package org.ppsspp.ppsspp;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
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
import android.view.Window;
import android.view.WindowInsets;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
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
import android.widget.Toast;

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
	private static final int REQUEST_ADD_GAMES = 4202;
	private static final int REQUEST_ADD_ARCADE = 4203;
	private static final int REQUEST_ADD_MUGEN_FOLDER = 4204;
	private static final int REQUEST_ADD_MUGEN_ZIP = 4205;

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
	private static final int[] FSR_SCALES = { 15, 20, 30, 40 };
	private static final String K_FSR_SPRITES = "fsr_sprites_";   // xBR on the 480x272 picture first
	private static final String K_FSR_DETAIL = "fsr_detail_";     // AMD CAS after RCAS, 0-100
	private static final String K_FSR_COLOR = "fsr_color_";       // vibrance + soft contrast, 0-100
	private static final int FSR_DEFAULT_DETAIL = 50;
	private static final int FSR_DEFAULT_COLOR = 30;
	private static final String[] FSR_SCALE_LABELS = { "1.5×", "2×", "3×", "4×" };
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

	private static final String GESTURE_GUIDE_KOF =
		"• Toca: golpe. Toques seguidos hacen la cadena A → B → C → D.\n"
		+ "• Desliza → o ←: correr o dash hacia ese lado (hacia el rival corres, hacia atrás saltas atrás). Si dejas el dedo, sigues corriendo.\n"
		+ "• Desliza ↑, ↗ o ↖: saltar.\n"
		+ "• Desliza ↓: barrida (2D).\n"
		+ "• Toca con dos dedos: CD (golpe de rechazo).\n"
		+ "• Desliza con dos dedos → o ←: agarre (C). Hacia arriba: supersalto.\n"
		+ "• Mantén dos dedos: bloquear. Bájalos un poco para bloquear agachado.\n"
		+ "• Mantén un dedo y arrástralo: stick para caminar, agacharte, bloquear o saltar.\n"
		+ "• Botones de la derecha: tres especiales y un súper (el movimiento sale solo), Roll (A+B, rodar) y MAX (A+B+C: modo MAX o cargar poder, según la entrega).\n"
		+ "• Arriba a la izquierda: Start, ✕ y ○ para los menús, y una flecha que indica hacia dónde miras. Si los especiales salen al revés, tócala.\n"
		+ "Cada entrega de la colección tiene sus reglas: por ejemplo, en '94 no se puede rodar.";

	private static final String GESTURE_GUIDE_SF =
		"• Toca: golpe. Toques seguidos hacen la cadena LP → MP → HP.\n"
		+ "• Desliza → o ←: dash hacia ese lado. Si dejas el dedo, sigues avanzando.\n"
		+ "• Desliza ↑, ↗ o ↖: saltar.\n"
		+ "• Desliza ↓: barrida (2HK).\n"
		+ "• Toca con dos dedos: HK (patada fuerte).\n"
		+ "• Desliza con dos dedos → o ←: agarre (→ + HP). Hacia arriba: salto alto.\n"
		+ "• Mantén dos dedos: bloquear. Bájalos un poco para bloquear agachado.\n"
		+ "• Mantén un dedo y arrástralo: stick para caminar, agacharte, bloquear o saltar.\n"
		+ "• Botones de la derecha: tres especiales y un Super Combo (el movimiento sale solo), V-ISM (MP+MK: activa el Custom Combo en V-ism) y Alpha (→ + MP+MK: Alpha Counter mientras bloqueas).\n"
		+ "• Arriba a la izquierda: Start, ✕ y ○ para los menús, y una flecha que indica hacia dónde miras. Si los especiales salen al revés, tócala.\n"
		+ "Los botones son los del diseño Arcade de la app: si en el juego cambiaste la configuración de botones, ponla como se indica en «Controles».";

	/** Gesture specials of BlazBlue, KOF and Street Fighter: one set for every character. */
	private static final int GESTURE_BB = -1;
	private static final int GESTURE_KOF = -2;
	private static final int GESTURE_SF = -3;
	/** Games in the catalog: JSON array of {uri, name, title, id, format}. */
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
	/** Big buttons grouped like the mobile fighting games (MobileLayout, patches/0006). */
	static final String STYLE_MOBILE = "mobile";
	private static final String K_MOBILE_SIZE = "mobile_size";
	/** Movement on the touch screen: the D-pad ("Flechas") or a joystick that presses the D-pad. */
	private static final String K_MOVE = "move_control";
	private static final String MOVE_DPAD = "dpad";
	private static final String MOVE_STICK = "stick";
	private static final int[] MOBILE_SIZES = { 85, 100, 120 };
	private static final String[] MOBILE_SIZE_LABELS = { "Medianos", "Grandes", "Enormes" };
	/** Custom buttons this app may use (the mobile layout uses up to 9). */
	private static final int MAX_CUSTOM_BUTTONS = 10;

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
	private TextView styleMobile;
	private TextView moveDpad;
	private TextView moveStick;
	private TextView moveDesc;
	private LinearLayout mobileSizeRow;
	private final TextView[] mobileSizeChips = new TextView[MOBILE_SIZES.length];
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
	private TextView gameReportButton;
	private TextView perCharacterButton;
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
	private TextView catalogButton;
	private ImageView gameIcon;
	private String gameIconKey;
	private Dialog catalogDialog;
	private LinearLayout catalogBody;
	private int catalogFilter = -1;   // -1: every game, else GameCatalog.BADGE_*
	private int addingGames;          // files still being read after "Añadir juegos"
	private ScrollView mainScroll;
	private TextView amdButtonStatus;
	private LinearLayout fsrPanel;
	private Switch fsrSwitch;
	private LinearLayout fsrOptions;
	private final TextView[] fsrScaleChips = new TextView[FSR_SCALES.length];
	private SeekBar fsrSeek;
	private Switch fsrSpritesSwitch;
	private SeekBar fsrDetailSeek;
	private SeekBar fsrColorSeek;
	private TextView fsrDetailValue;
	private TextView fsrColorValue;
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
		handler.removeCallbacks(lastGameCheck);
		handler.postDelayed(lastGameCheck, 900);
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
		if (requestCode == REQUEST_ADD_GAMES) {
			if (resultCode == RESULT_OK && data != null) {
				onGamesAdded(data);
			}
			return;
		}
		if (requestCode == REQUEST_ADD_ARCADE) {
			if (resultCode == RESULT_OK && data != null) {
				onArcadeAdded(data);
			}
			return;
		}
		if (requestCode == REQUEST_ADD_MUGEN_FOLDER || requestCode == REQUEST_ADD_MUGEN_ZIP) {
			if (resultCode == RESULT_OK && data != null && data.getData() != null) {
				onMugenPicked(data.getData(), requestCode == REQUEST_ADD_MUGEN_FOLDER);
			}
			return;
		}
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

		// The previous game keeps its permission: it stays in the catalog.

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

	/** "12" -> char_12.ini (Guilty Gear, by number); "bb_rg" -> bb_rg.ini. */
	private File savedProfile(String key) {
		try {
			return new File(profilesDir(), String.format(Locale.ROOT, "char_%02d.ini", Integer.parseInt(key)));
		} catch (NumberFormatException e) {
			return new File(profilesDir(), key + ".ini");
		}
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

		String style = currentStyle();
		boolean mobile = STYLE_MOBILE.equals(style);
		// Only rewrite the button layout when the button type changes, so that buttons moved
		// with the game's own layout editor stay where the player put them.
		// The arcade labels depend on the game (P K S HS D or A B C D); the mobile layout also on
		// the button size and the screen.
		int kind = gameKind();
		String appliedKey = style + (kind == GameInspector.KIND_BB ? "|bb" : kind == GameInspector.KIND_KOF ? "|kof" : kind == GameInspector.KIND_SF ? "|sf" : "");
		appliedKey += "|" + (stickMovement() ? MOVE_STICK : MOVE_DPAD);
		if (mobile) {
			float[] screen = landscapeDp();
			appliedKey += "|" + mobileSize() + "|" + Math.round(screen[0]) + "x" + Math.round(screen[1]);
		}
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
		if (mobile) {
			// The mobile look draws the filled and the outline shapes: needs an outline style.
			ini.append("TouchButtonStyle = 1\n");
		}
		// The joystick keeps its center: touching its right side presses right at once, so
		// tapping forward twice dashes (with a center that follows the thumb, a tap pressed
		// nothing). patches/0007 turns it into the D-pad.
		ini.append("AutoCenterTouchAnalog = False\n");
		if (writeLayout) {
			appendButtonDefinitions(ini, style, kind);
		}
		ini.append("[TouchControls.Landscape]\n");
		// Fast-forward isn't a PSP or arcade button: never show it.
		ini.append("ShowTouchUnthrottle = False\n");
		ini.append(STICK_HEAD_LINES);
		if (writeLayout) {
			appendTouchLayout(ini, style, kind);
		}
		ini.append("[TouchControls.Portrait]\n")
			.append("ShowTouchUnthrottle = False\n");

		// Post-processing (needs patches/0003 so the appended config can set it). AMD FSR, when on
		// for this game, replaces the visual effect and keeps the internal resolution (patches/0005).
		Effect effect = currentEffect();
		ini.append("[PostShaderList]\n");
		if (fsrOn()) {
			// [xBR for the sprites] -> EASU -> RCAS -> [AMD CAS + color]
			List<String> chain = new ArrayList<>();
			if (fsrSprites()) {
				chain.add("AcXbrSprites");
			}
			chain.add("AcFsrEasu");
			chain.add("AcFsrRcas");
			boolean finish = fsrDetail() > 0 || fsrColor() > 0;
			if (finish) {
				chain.add("AcFsrFinish");
			}
			for (int i = 0; i < chain.size(); i++) {
				ini.append("PostShader").append(i + 1).append(" = ").append(chain.get(i)).append('\n');
			}
			ini.append("[PostShaderSetting]\n")
				.append(String.format(Locale.ROOT, "AcFsrEasuSettingCurrentValue1 = %.2f\n", fsrScale() / 10f))
				.append(String.format(Locale.ROOT, "AcFsrRcasSettingCurrentValue1 = %.2f\n", fsrSharp() / 100f));
			if (fsrSprites()) {
				ini.append("AcXbrSpritesSettingCurrentValue1 = 2.00\n");
			}
			if (finish) {
				ini.append(String.format(Locale.ROOT, "AcFsrFinishSettingCurrentValue1 = %.2f\n", fsrDetail() / 100f))
					.append(String.format(Locale.ROOT, "AcFsrFinishSettingCurrentValue2 = %.2f\n", fsrColor() / 100f));
			}
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
		for (String preset : new String[] { STYLE_PSP, STYLE_ARCADE, STYLE_MOBILE }) {
			StringBuilder layout = new StringBuilder("[Control]\n");
			// The game being launched decides the labels (P K S HS D, A B C D, LP ... HK).
			appendButtonDefinitions(layout, preset, gameKind());
			layout.append("[TouchControls.Landscape]\n").append("ShowTouchUnthrottle = False\n").append(STICK_HEAD_LINES);
			appendTouchLayout(layout, preset, gameKind());
			writeText(new File(getFilesDir(), "acplus_layout_" + preset + ".ini"), layout.toString());
		}

		StringBuilder ini = new StringBuilder();
		ini.append("[Profiles]\n")
			// With gestures there are no buttons to swap.
			.append("Enabled = ").append(prefs.getBoolean(K_PROFILES, true) && !prefs.getBoolean(K_GESTURES, false) ? "True" : "False").append('\n')
			.append("Side = ").append(prefs.getInt(K_SIDE, 0)).append('\n')
			.append("DefaultStyle = ").append(prefs.getString(K_STYLE, STYLE_PSP)).append('\n')
			.append("[Characters]\n");
		for (GameRosters.Roster roster : GameRosters.ALL) {
			for (String key : roster.keys) {
				String s = prefs.getString(K_CHAR_STYLE + key, null);
				if (STYLE_PSP.equals(s) || STYLE_ARCADE.equals(s) || STYLE_MOBILE.equals(s)) {
					ini.append(key).append(" = ").append(s).append('\n');
				}
			}
		}
		ini.append("[Hitboxes]\n")
			.append("Enabled = ").append(prefs.getBoolean(K_HB_ENABLED, false) ? "True" : "False").append('\n');
		for (int i = 0; i < HB_KEYS.length; i++) {
			ini.append(HB_INI_KEYS[i]).append(" = ").append(prefs.getBoolean(HB_KEYS[i], true) ? "True" : "False").append('\n');
		}
		ini.append(hitboxManualLines(prefs.getString(K_HB_MANUAL, "")));
		ini.append("[Touch]\n")
			.append("Mobile = ").append(STYLE_MOBILE.equals(currentStyle()) ? "True" : "False").append('\n')
			.append("StickAsDpad = ").append(stickMovement() ? "True" : "False").append('\n');
		ini.append("[Hud]\n")
			.append("Enabled = ").append(prefs.getBoolean(K_HUD_ON, false) ? "True" : "False").append('\n')
			.append("Design = ").append(hudDesign()).append('\n')
			.append("Opacity = ").append(prefs.getInt(K_HUD_ALPHA, 100)).append('\n');
		ini.append("[Fsr]\n")
			.append("Enabled = ").append(fsrOn() ? "True" : "False").append('\n')
			.append("GameId = ").append(fsrGameKey().equals("default") ? "" : fsrGameKey()).append('\n')
			.append(String.format(Locale.ROOT, "Scale = %.2f\n", fsrScale() / 10f))
			.append(String.format(Locale.ROOT, "Sharpness = %.2f\n", fsrSharp() / 100f))
			.append("Sprites = ").append(fsrSprites() ? "True" : "False").append('\n')
			.append(String.format(Locale.ROOT, "Detail = %.2f\n", fsrDetail() / 100f))
			.append(String.format(Locale.ROOT, "Color = %.2f\n", fsrColor() / 100f));
		ini.append("[Gestures]\n")
			.append("Enabled = ").append(prefs.getBoolean(K_GESTURES, false) ? "True" : "False").append('\n')
			.append("Scale = ").append(prefs.getInt(K_GES_SCALE, 100)).append('\n')
			.append("Opacity = ").append(prefs.getInt(K_GES_ALPHA, 60)).append('\n');
		ini.append("MovesBB = ");
		for (int slot = 0; slot < GestureMoves.SLOTS; slot++) {
			ini.append(slot == 0 ? "" : ",").append(gestureMove(GESTURE_BB, slot));
		}
		ini.append('\n');
		ini.append("MovesKOF = ");
		for (int slot = 0; slot < GestureMoves.SLOTS; slot++) {
			ini.append(slot == 0 ? "" : ",").append(gestureMove(GESTURE_KOF, slot));
		}
		ini.append('\n');
		ini.append("MovesSF = ");
		for (int slot = 0; slot < GestureMoves.SLOTS; slot++) {
			ini.append(slot == 0 ? "" : ",").append(gestureMove(GESTURE_SF, slot));
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
	private static final long KEY_L = 1L << 10;
	private static final long KEY_R = 1L << 11;

	/** Label image indices appended to PPSSPP's customKeyImages (patches/0002). */
	private static final int IMG_P = 36;
	private static final int IMG_K = 37;
	private static final int IMG_S = 38;
	private static final int IMG_HS = 39;
	private static final int IMG_D = 40;
	private static final int IMG_LP = 41;
	private static final int IMG_MP = 42;
	private static final int IMG_HP = 43;
	private static final int IMG_LK = 44;
	private static final int IMG_MK = 45;
	private static final int IMG_HK = 46;


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

	/** Neo Geo cabinet: A, B, C and D in one arched row. */
	static final float[][] KOF_ARCADE_POSITIONS = {
		{ 0.705f, 0.735f },  // A
		{ 0.787f, 0.665f },  // B
		{ 0.870f, 0.625f },  // C
		{ 0.952f, 0.625f },  // D
	};

	/** KOF's PSP mapping (SNK's PlayStation default): square A, cross B, triangle C, circle D. */
	private static final ArcadeButton[] KOF_ARCADE_BUTTONS = {
		new ArcadeButton(IMG_A, KEY_SQUARE, KOF_ARCADE_POSITIONS[0][0], KOF_ARCADE_POSITIONS[0][1]),
		new ArcadeButton(IMG_B, KEY_CROSS, KOF_ARCADE_POSITIONS[1][0], KOF_ARCADE_POSITIONS[1][1]),
		new ArcadeButton(IMG_C, KEY_TRIANGLE, KOF_ARCADE_POSITIONS[2][0], KOF_ARCADE_POSITIONS[2][1]),
		new ArcadeButton(IMG_LETTER_D, KEY_CIRCLE, KOF_ARCADE_POSITIONS[3][0], KOF_ARCADE_POSITIONS[3][1]),
	};

	/** Capcom cabinet: LP MP HP on top, LK MK HK below, each row slightly arched. */
	static final float[][] SF_ARCADE_POSITIONS = {
		{ 0.732f, 0.585f },  // LP
		{ 0.834f, 0.555f },  // MP
		{ 0.936f, 0.585f },  // HP
		{ 0.732f, 0.800f },  // LK
		{ 0.834f, 0.770f },  // MK
		{ 0.936f, 0.800f },  // HK
	};

	/**
	 * Street Fighter Alpha 3 MAX: square LP, triangle MP, R HP, cross LK, circle MK, L HK (the
	 * PlayStation layout of the series, with the heavy attacks on the shoulder buttons).
	 */
	private static final ArcadeButton[] SF_ARCADE_BUTTONS = {
		new ArcadeButton(IMG_LP, KEY_SQUARE, SF_ARCADE_POSITIONS[0][0], SF_ARCADE_POSITIONS[0][1]),
		new ArcadeButton(IMG_MP, KEY_TRIANGLE, SF_ARCADE_POSITIONS[1][0], SF_ARCADE_POSITIONS[1][1]),
		new ArcadeButton(IMG_HP, KEY_R, SF_ARCADE_POSITIONS[2][0], SF_ARCADE_POSITIONS[2][1]),
		new ArcadeButton(IMG_LK, KEY_CROSS, SF_ARCADE_POSITIONS[3][0], SF_ARCADE_POSITIONS[3][1]),
		new ArcadeButton(IMG_MK, KEY_CIRCLE, SF_ARCADE_POSITIONS[4][0], SF_ARCADE_POSITIONS[4][1]),
		new ArcadeButton(IMG_HK, KEY_L, SF_ARCADE_POSITIONS[5][0], SF_ARCADE_POSITIONS[5][1]),
	};

	private static ArcadeButton[] arcadeButtons(int kind) {
		return kind == GameInspector.KIND_BB ? BB_ARCADE_BUTTONS : kind == GameInspector.KIND_KOF ? KOF_ARCADE_BUTTONS
			: kind == GameInspector.KIND_SF ? SF_ARCADE_BUTTONS : ARCADE_BUTTONS;
	}

	private static final float BUTTON_SCALE = 1.15f;

	/**
	 * PPSSPP reads a missing stick head scale as 0: the stick then has no head and nothing to
	 * touch. Always written, also into the layouts of the per-character profiles.
	 */
	private static final String STICK_HEAD_LINES = "LeftStickHeadScale = 1.0\nRightStickHeadScale = 1.0\nHideStickBackground = False\n";

	private void appendButtonDefinitions(StringBuilder ini, String style, int kind) {
		if (STYLE_ARCADE.equals(style)) {
			appendArcadeButtonDefinitions(ini, kind);
		} else if (STYLE_MOBILE.equals(style)) {
			List<MobileLayout.Button> buttons = mobileLayout(kind).buttons;
			for (int i = 0; i < buttons.size(); i++) {
				MobileLayout.Button b = buttons.get(i);
				ini.append("Custom").append(i).append("Mapping = ")
					.append(String.format(Locale.ROOT, "0x%016x", b.keys)).append('\n')
					.append("Custom").append(i).append("Image = ").append(b.image).append('\n')
					.append("Custom").append(i).append("Shape = 0\n")  // Round
					.append("Custom").append(i).append("Toggle = False\n")
					.append("Custom").append(i).append("Repeat = False\n");
			}
		}
	}

	private static void appendArcadeButtonDefinitions(StringBuilder ini, int kind) {
		ArcadeButton[] buttons = arcadeButtons(kind);
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

	private void appendTouchLayout(StringBuilder ini, String style, int kind) {
		if (STYLE_MOBILE.equals(style)) {
			appendMobileLayout(ini, mobileLayout(kind));
			return;
		}
		boolean arcade = STYLE_ARCADE.equals(style);
		// The mobile layout moves the D-pad: put it back. Movement: the D-pad or a joystick.
		boolean stick = stickMovement();
		ini.append("DPadX = -1.0\nDPadY = -1.0\nDPadSpacing = 1.0\n")
			.append("ShowTouchDpad = ").append(stick ? "False" : "True").append('\n')
			.append("ShowAnalogStick = ").append(stick ? "True" : "False").append('\n')
			.append("LKeyX = -1.0\nLKeyY = -1.0\n")
			.append("RKeyX = -1.0\nRKeyY = -1.0\n");
		if (stick) {
			// Where the D-pad goes, bigger than PPSSPP's own stick.
			float[] screen = landscapeDp();
			float size = MobileLayout.STICK_DP * 1.45f;
			appendPos(ini, "AnalogStick", (0.035f * screen[1] + 0.02f * screen[0] + size / 2f) / screen[0],
				(screen[1] - 0.05f * screen[1] - size / 2f) / screen[1], 1.45f);
		} else {
			ini.append("AnalogStickX = -1.0\nAnalogStickY = -1.0\n");
		}
		appendTouchLayout(ini, arcade, kind);
	}

	private void appendMobileLayout(StringBuilder ini, MobileLayout.Layout l) {
		ini.append("ShowTouchCircle = False\nShowTouchCross = False\nShowTouchSquare = False\nShowTouchTriangle = False\n")
			.append("ShowAnalogStick = ").append(l.stick ? "True" : "False").append('\n')
			.append("ShowTouchDpad = ").append(l.stick ? "False" : "True").append('\n')
			.append("LKeyX = -1.0\nLKeyY = -1.0\n")
			.append("RKeyX = -1.0\nRKeyY = -1.0\n")
			.append("ShowTouchLTrigger = ").append(l.showL ? "True" : "False").append('\n')
			.append("ShowTouchRTrigger = ").append(l.showR ? "True" : "False").append('\n')
			.append(String.format(Locale.ROOT, "DPadSpacing = %.4f\n", l.dpadSpacing));
		appendPos(ini, "DPad", l.dpadX, l.dpadY, l.dpadScale);
		appendPos(ini, "AnalogStick", l.dpadX, l.dpadY, l.stickScale);
		appendPos(ini, "StartKey", l.startX, l.startY, 1.0f);
		appendPos(ini, "SelectKey", l.selectX, l.selectY, 1.0f);
		for (int i = 0; i < MAX_CUSTOM_BUTTONS; i++) {
			if (i >= l.buttons.size()) {
				ini.append("ShowComboKey").append(i).append(" = False\n");
				continue;
			}
			MobileLayout.Button b = l.buttons.get(i);
			ini.append(String.format(Locale.ROOT, "fcombo%dX = %.4f\n", i, b.x))
				.append(String.format(Locale.ROOT, "fcombo%dY = %.4f\n", i, b.y))
				.append(String.format(Locale.ROOT, "comboKeyScale%d = %.4f\n", i, b.scale))
				.append("ShowComboKey").append(i).append(" = True\n");
		}
	}

	/**
	 * Landscape size of the screen in PPSSPP's dp: { width, height }. PPSSPP counts 240 dpi as
	 * one dp per pixel (Android counts 160), so its dp are 1.5 times Android's.
	 */
	@SuppressWarnings("deprecation")
	private float[] landscapeDp() {
		DisplayMetrics dm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getRealMetrics(dm);
		float perPixel = 240f / Math.max(1, dm.densityDpi);
		float w = Math.max(dm.widthPixels, dm.heightPixels) * perPixel;
		float h = Math.min(dm.widthPixels, dm.heightPixels) * perPixel;
		return new float[] { w, h };
	}

	private boolean stickMovement() {
		return MOVE_STICK.equals(prefs.getString(K_MOVE, MOVE_DPAD));
	}

	private int mobileSize() {
		int v = prefs.getInt(K_MOBILE_SIZE, 100);
		for (int size : MOBILE_SIZES) {
			if (size == v) {
				return v;
			}
		}
		return 100;
	}

	private MobileLayout.Layout mobileLayout(int kind) {
		float[] screen = landscapeDp();
		return MobileLayout.build(kind, screen[0], screen[1], mobileSize(), stickMovement());
	}

	private String currentStyle() {
		String s = prefs.getString(K_STYLE, STYLE_PSP);
		return STYLE_ARCADE.equals(s) || STYLE_MOBILE.equals(s) ? s : STYLE_PSP;
	}

	private static void appendTouchLayout(StringBuilder ini, boolean arcade, int kind) {
		ArcadeButton[] buttons = arcadeButtons(kind);
		String psp = arcade ? "False" : "True";
		ini.append("ShowTouchCircle = ").append(psp).append('\n')
			.append("ShowTouchCross = ").append(psp).append('\n')
			.append("ShowTouchSquare = ").append(psp).append('\n')
			.append("ShowTouchTriangle = ").append(psp).append('\n')
			// In Guilty Gear's arcade mode R (Dust) becomes the D button. L (Respect) stays.
			// BlazBlue and KOF have D on a face button, so R stays too. In Street Fighter L and R
			// are the heavy kick and punch, both on the arcade buttons.
			.append("ShowTouchRTrigger = ").append(arcade && (kind == GameInspector.KIND_GG || kind == GameInspector.KIND_SF) ? "False" : "True").append('\n')
			.append("ShowTouchLTrigger = ").append(arcade && kind == GameInspector.KIND_SF ? "False" : "True").append('\n');
		if (arcade) {
			// Move Start/Select toward the middle so they don't crowd the P button.
			appendPos(ini, "StartKey", 0.56f, 0.91f, BUTTON_SCALE);
			appendPos(ini, "SelectKey", 0.44f, 0.91f, BUTTON_SCALE);
			for (int i = 0; i < MAX_CUSTOM_BUTTONS; i++) {
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
			for (int i = 0; i < MAX_CUSTOM_BUTTONS; i++) {
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
				result = GameInspector.inspect(getApplicationContext(), uri, GameCatalog.iconFile(this, key));
				GameCatalog.inspected(this, key);
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
			gameDetail.setText("Archivo ISO o CSO de PSP: Guilty Gear XX ΛCore Plus, BlazBlue: Calamity Trigger, The King of Fighters (Orochi Saga) o Street Fighter Alpha 3 MAX. Guárdalo en la memoria del teléfono o en la microSD, no en la nube. En «Catálogo» puedes añadir varios a la vez.");
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
				detail.append("\nBlazBlue: botones A, B, C y D, gestos, HUD propio, perfiles por personaje y efectos listos. El visor de hitboxes es solo de Guilty Gear.");
				break;
			case STREET_FIGHTER:
				gameStatus.setText("✓ " + r.title);
				gameStatus.setTextColor(C_OK);
				detail.append("\nStreet Fighter Alpha 3: botones arcade de 6 (LP MP HP / LK MK HK), gestos, HUD propio y efectos listos. Los perfiles por personaje y el visor de hitboxes no están disponibles en este juego.");
				break;
			case KOF:
				gameStatus.setText("✓ " + r.title);
				gameStatus.setTextColor(C_OK);
				detail.append("\nKOF: botones Neo Geo A, B, C y D, gestos, HUD propio, perfiles por personaje ('96–'98) y efectos listos. El visor de hitboxes es solo de Guilty Gear.");
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
	// Several games: header and catalog

	private int gameKind() {
		return GameInspector.kind(prefs.getString(K_ID, null), prefs.getString(K_TITLE, null));
	}

	private boolean isBlazBlue() {
		return gameKind() == GameInspector.KIND_BB;
	}

	private boolean isKof() {
		return gameKind() == GameInspector.KIND_KOF;
	}

	private boolean isStreetFighter() {
		return gameKind() == GameInspector.KIND_SF;
	}

	/** Short name for messages ("Guilty Gear XX ΛC+", "BlazBlue", "KOF"). */
	private String gameShortName() {
		return isBlazBlue() ? "BlazBlue" : isKof() ? "The King of Fighters" : isStreetFighter() ? "Street Fighter Alpha 3" : "Guilty Gear XX ΛC+";
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
		} else if (isKof()) {
			String t = prefs.getString(K_TITLE, "");
			boolean portable = t != null && t.toUpperCase(Locale.ROOT).contains("PORTABLE");
			headerKicker.setText("THE KING OF FIGHTERS");
			headerTitle.setText(portable ? "PORTABLE '94~'98" : "OROCHI SAGA");
			headerSubtitle.setText("Edición PSP · en la misma app que Guilty Gear");
		} else if (isStreetFighter()) {
			String t = prefs.getString(K_TITLE, "");
			boolean zero = t != null && t.toUpperCase(Locale.ROOT).contains("ZERO");
			headerKicker.setText("STREET FIGHTER");
			headerTitle.setText(zero ? "ZERO 3 ↑↑" : "ALPHA 3 MAX");
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

	/** Keeps the current game in the catalog (once it has been identified), first in the list. */
	private void addCurrentToLibrary() {
		String uri = prefs.getString(K_URI, null);
		if (uri == null || !prefs.getBoolean(K_INSPECTED, false)) {
			return;
		}
		try {
			putInLibrary(new org.json.JSONObject()
				.put("uri", uri)
				.put("name", prefs.getString(K_NAME, ""))
				.put("title", prefs.getString(K_TITLE, ""))
				.put("id", prefs.getString(K_ID, ""))
				.put("format", prefs.getString(K_FORMAT, "")), true);
		} catch (org.json.JSONException e) {
			Log.w(TAG, "Library entry", e);
		}
	}

	/**
	 * Adds or updates a game. New games go first; with toFront an existing one moves there too.
	 * Past GameCatalog.MAX_GAMES the oldest ones leave the catalog (and their file permission).
	 */
	private void putInLibrary(org.json.JSONObject entry, boolean toFront) {
		String uri = entry.optString("uri");
		org.json.JSONArray lib = loadLibrary();
		List<org.json.JSONObject> list = new ArrayList<>();
		boolean found = false;
		for (int i = 0; i < lib.length(); i++) {
			org.json.JSONObject e = lib.optJSONObject(i);
			if (e == null) {
				continue;
			}
			if (uri.equals(e.optString("uri"))) {
				found = true;
				if (!toFront) {
					list.add(entry);
				}
				continue;
			}
			list.add(e);
		}
		if (toFront || !found) {
			list.add(0, entry);
		}
		String current = prefs.getString(K_URI, null);
		org.json.JSONArray out = new org.json.JSONArray();
		for (int i = 0; i < list.size(); i++) {
			org.json.JSONObject e = list.get(i);
			if (out.length() < GameCatalog.MAX_GAMES || e.optString("uri").equals(current)) {
				out.put(e);
			} else {
				releaseGame(e.optString("uri"));
			}
		}
		if (!out.toString().equals(lib.toString())) {
			prefs.edit().putString(K_LIBRARY, out.toString()).apply();
		}
	}

	/** Gives back the permission of a file that is no longer in the catalog. */
	private void releaseGame(String uri) {
		if (uri == null || uri.isEmpty() || uri.equals(prefs.getString(K_URI, null))) {
			return;
		}
		GameCatalog.forget(this, uri);
		try {
			getContentResolver().releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION);
		} catch (SecurityException ignored) {
			// Not persisted anymore.
		}
	}

	private void refreshLibrary() {
		int n = loadLibrary().length();
		if (catalogButton != null) {
			catalogButton.setText(n == 0 ? "CATÁLOGO" : "CATÁLOGO  ·  " + n + (n == 1 ? " juego" : " juegos"));
		}
		refreshGameIcon();
		if (catalogDialog != null && catalogDialog.isShowing()) {
			fillCatalog();
		}
	}

	/** The current game's icon next to its name in «JUEGO». */
	private void refreshGameIcon() {
		if (gameIcon == null) {
			return;
		}
		final String uri = prefs.getString(K_URI, null);
		if (uri == null || !prefs.getBoolean(K_INSPECTED, false)) {
			gameIcon.setVisibility(View.GONE);
			gameIconKey = null;
			return;
		}
		gameIcon.setVisibility(View.VISIBLE);
		String id = prefs.getString(K_ID, null);
		String title = prefs.getString(K_TITLE, null);
		final String key = uri + "|" + id + "|" + title;
		Bitmap hit = GameCatalog.cached(uri);
		if (key.equals(gameIconKey) && hit == null) {
			return;   // the cover is shown and the icon is on its way (or there is none)
		}
		gameIconKey = key;
		gameIcon.setImageBitmap(hit != null ? hit : GameCatalog.cover(GameCatalog.badge(id, title), title, dp(112)));
		if (hit == null) {
			GameCatalog.load(this, uri, handler, (u, bmp) -> {
				if (bmp != null && key.equals(gameIconKey)) {
					gameIcon.setImageBitmap(bmp);
				}
			});
		}
	}

	// -------------------------------------------------------------------------------------------
	// Catalog: every game added, with its icon

	private static final String[][] SUPPORTED_GAMES = {
		{ "Guilty Gear XX ΛCore Plus", "ULUS-10409 (América) · también otras versiones",
			"Botones arcade P K S HS D, gestos con especiales por personaje, perfiles por personaje, visor de hitboxes, HUD propio, efectos y FSR. Lo que lee la memoria del juego (perfiles, visor, HUD y especiales por personaje) necesita la versión ULUS-10409." },
		{ "BlazBlue: Calamity Trigger Portable", "ULUS-10519 · ULES-01497 · ULJM-05613 · también Continuum Shift II",
			"Botones arcade A B C D, gestos (cadena A → B → C, Drive con ✕), HUD propio (vida, Heat y combos) y perfiles por personaje (experimental; CT americano y europeo, CS II americano), efectos y FSR." },
		{ "The King of Fighters Collection: The Orochi Saga", "ULUS-10360 · ULES-01191",
			"Botones Neo Geo A B C D, gestos (cadena A → B → C → D, CD, Roll y MAX), HUD propio (vida, POW y tiempo) en las cinco entregas, perfiles por personaje en '96, '97 y '98 (versión americana), efectos y FSR." },
		{ "Street Fighter Alpha 3 MAX", "ULUS-10062 · ULES-00235 · ULJM-05082 (Zero 3 Double Upper)",
			"Botones arcade de 6 (LP MP HP / LK MK HK), gestos (cadena LP → MP → HP, V-ISM y Alpha Counter), HUD propio (vida, súper, guardia y tiempo), efectos y FSR. Sin perfiles por personaje por ahora." },
		{ "The King of Fighters 2002 (Arcade / Neo Geo)", "kof2002.zip + neogeo.zip · también Magic Plus, Plus y otros juegos de FBNeo",
			"Emulador FBNeo con controles táctiles A B C D y atajos AB, CD, BC y ABC, flechas o joystick, estados guardados, filtros de imagen y modo MVS (arcade) o AES (consola). Las versiones hack necesitan también kof2002.zip." },
		{ "Juegos MUGEN (KOF 2012 fan made y otros)", "Carpeta o .zip del juego · motor Ikemen GO 1.0",
			"Ikemen GO abre los juegos hechos para MUGEN 1.0 / 1.1 con su pantalla de título, selección y peleas. Controles táctiles en diseño KOF (A B C D) o de 6 botones. Necesita Android 13 o más nuevo y una GPU con OpenGL ES 3.2." },
	};
	private static final int[] SUPPORTED_BADGES = { GameCatalog.BADGE_GG, GameCatalog.BADGE_BB, GameCatalog.BADGE_KOF, GameCatalog.BADGE_SF,
		GameCatalog.BADGE_ARCADE, GameCatalog.BADGE_MUGEN };

	/** ImageView with the shape of a PSP game icon (144 x 80). */
	private static final class IconView extends ImageView {
		IconView(android.content.Context c) {
			super(c);
			setScaleType(ScaleType.CENTER_CROP);
		}

		@Override
		protected void onMeasure(int widthSpec, int heightSpec) {
			int w = MeasureSpec.getSize(widthSpec);
			if (MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED) {
				super.onMeasure(widthSpec, heightSpec);
				return;
			}
			setMeasuredDimension(w, Math.round(w * 80f / 144f));
		}
	}

	private void showCatalog() {
		if (catalogDialog != null && catalogDialog.isShowing()) {
			return;
		}
		Dialog d = new Dialog(this, android.R.style.Theme_DeviceDefault_NoActionBar);
		ScrollView scroll = new ScrollView(this);
		scroll.setBackgroundColor(C_BG);
		scroll.setFillViewport(true);
		if (Build.VERSION.SDK_INT >= 35) {
			scroll.setOnApplyWindowInsetsListener((v, insets) -> {
				applyInsets(v, insets);
				return insets;
			});
		}
		FrameLayout center = new FrameLayout(this);
		scroll.addView(center, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		LinearLayout col = new LinearLayout(this);
		col.setOrientation(LinearLayout.VERTICAL);
		col.setPadding(dp(20), dp(20), dp(20), dp(28));
		int screenDp = (int) (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
		int width = screenDp > 940 ? dp(900) : ViewGroup.LayoutParams.MATCH_PARENT;
		center.addView(col, new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));
		d.setContentView(scroll);
		Window w = d.getWindow();
		if (w != null) {
			w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
			w.setBackgroundDrawable(new ColorDrawable(C_BG));
		}
		d.setOnDismissListener(x -> {
			catalogDialog = null;
			catalogBody = null;
		});
		catalogDialog = d;
		catalogBody = col;
		fillCatalog();
		d.show();
	}

	private void fillCatalog() {
		final LinearLayout col = catalogBody;
		if (col == null) {
			return;
		}
		col.removeAllViews();
		org.json.JSONArray lib = loadLibrary();
		String current = prefs.getString(K_URI, null);
		java.util.Set<String> readable = new java.util.HashSet<>();
		for (UriPermission perm : getContentResolver().getPersistedUriPermissions()) {
			if (perm.isReadPermission()) {
				readable.add(perm.getUri().toString());
			}
		}

		// Header
		LinearLayout top = new LinearLayout(this);
		top.setOrientation(LinearLayout.HORIZONTAL);
		top.setGravity(Gravity.CENTER_VERTICAL);
		LinearLayout titles = new LinearLayout(this);
		titles.setOrientation(LinearLayout.VERTICAL);
		TextView kicker = text("CATÁLOGO", 13, C_ACCENT, true);
		kicker.setLetterSpacing(0.3f);
		titles.addView(kicker);
		TextView title = text("Tus juegos", 34, C_TEXT, true);
		title.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
		titles.addView(title);
		top.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		TextView close = text("✕", 20, C_TEXT, true);
		close.setGravity(Gravity.CENTER);
		close.setBackground(ripple(Color.TRANSPARENT, C_CHIP_STROKE, 24));
		close.setContentDescription("Cerrar el catálogo");
		makeClickable(close);
		close.setOnClickListener(v -> {
			if (catalogDialog != null) {
				catalogDialog.dismiss();
			}
		});
		top.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
		col.addView(top);

		int total = lib.length();
		String sub = total == 0 ? "Aún no has añadido juegos."
			: total + (total == 1 ? " juego" : " juegos") + " · toca uno para elegirlo (los de Arcade y MUGEN se abren al tocarlos), mantén pulsado para quitarlo del catálogo.";
		if (addingGames > 0) {
			sub = "Leyendo " + addingGames + (addingGames == 1 ? " archivo…" : " archivos…") + "\n" + sub;
		}
		TextView subtitle = text(sub, 13, C_MUTED, false);
		subtitle.setPadding(0, dp(4), 0, 0);
		col.addView(subtitle);

		TextView add = filledButton("+   Añadir juegos (ISO / CSO)", 15);
		add.setOnClickListener(v -> pickGames());
		LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		addLp.topMargin = dp(16);
		col.addView(add, addLp);
		TextView addHint = text("Puedes elegir varios archivos a la vez. Se quedan en su sitio: la app solo guarda el permiso para abrirlos.", 12, 0xFF6C6C78, false);
		addHint.setPadding(0, dp(6), 0, 0);
		col.addView(addHint);

		// Other systems: arcade (FBNeo) and MUGEN (Ikemen GO).
		LinearLayout others = new LinearLayout(this);
		others.setOrientation(LinearLayout.HORIZONTAL);
		TextView addArcade = outlineButton("+  Arcade / Neo Geo");
		addArcade.setOnClickListener(v -> pickArcade());
		TextView addMugen = outlineButton("+  Juego MUGEN");
		addMugen.setOnClickListener(v -> pickMugen());
		others.addView(addArcade, weighted(0, dp(6)));
		others.addView(addMugen, weighted(dp(6), 0));
		LinearLayout.LayoutParams othersLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		othersLp.topMargin = dp(12);
		col.addView(others, othersLp);
		TextView othersHint = text("Arcade: los ZIP del juego (kof2002.zip…) y la BIOS neogeo.zip, con el emulador FBNeo. "
			+ "MUGEN: la carpeta o el .zip de un juego MUGEN (por ejemplo un KOF 2012 fan made), con el motor Ikemen GO. "
			+ "Se copian dentro de la app.", 12, 0xFF6C6C78, false);
		othersHint.setPadding(0, dp(6), 0, 0);
		col.addView(othersHint);

		// Filter by game
		int[] counts = new int[GameCatalog.BADGE_NAMES.length];
		for (int i = 0; i < total; i++) {
			org.json.JSONObject e = lib.optJSONObject(i);
			if (e != null) {
				counts[GameCatalog.badge(e)]++;
			}
		}
		if (catalogFilter >= 0 && counts[catalogFilter] == 0) {
			catalogFilter = -1;
		}
		int kinds = 0;
		for (int c : counts) {
			kinds += c > 0 ? 1 : 0;
		}
		if (kinds > 1) {
			android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(this);
			hs.setHorizontalScrollBarEnabled(false);
			LinearLayout chips = new LinearLayout(this);
			chips.setOrientation(LinearLayout.HORIZONTAL);
			hs.addView(chips);
			addFilterChip(chips, -1, "Todos · " + total);
			for (int b : GameCatalog.BADGE_ORDER) {
				if (counts[b] > 0) {
					addFilterChip(chips, b, (b == GameCatalog.BADGE_GG ? "Guilty Gear" : b == GameCatalog.BADGE_BB ? "BlazBlue" : b == GameCatalog.BADGE_KOF ? "KOF"
						: b == GameCatalog.BADGE_SF ? "Street Fighter" : b == GameCatalog.BADGE_ARCADE ? "Arcade" : b == GameCatalog.BADGE_MUGEN ? "MUGEN" : "Otros") + " · " + counts[b]);
				}
			}
			LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
			hlp.topMargin = dp(16);
			col.addView(hs, hlp);
		}

		// Grid of games
		int screenDp = (int) (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
		int columns = screenDp >= 900 ? 4 : screenDp >= 600 ? 3 : 2;
		LinearLayout row = null;
		int n = 0;
		for (int i = 0; i < total; i++) {
			org.json.JSONObject e = lib.optJSONObject(i);
			if (e == null || (catalogFilter >= 0 && GameCatalog.badge(e) != catalogFilter)) {
				continue;
			}
			if (n % columns == 0) {
				row = new LinearLayout(this);
				row.setOrientation(LinearLayout.HORIZONTAL);
				LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
				rlp.topMargin = dp(12);
				col.addView(row, rlp);
			}
			String uri = e.optString("uri");
			int k = n % columns;
			if (!GameCatalog.isPsp(e)) {
				row.addView(otherSystemTile(e), weighted(k == 0 ? 0 : dp(6), k == columns - 1 ? 0 : dp(6)));
				n++;
				continue;
			}
			boolean access = !"content".equals(Uri.parse(uri).getScheme()) || readable.contains(uri);
			row.addView(catalogTile(e, uri.equals(current), access), weighted(k == 0 ? 0 : dp(6), k == columns - 1 ? 0 : dp(6)));
			n++;
		}
		while (row != null && n % columns != 0) {
			int k = n % columns;
			row.addView(new View(this), weighted(k == 0 ? 0 : dp(6), k == columns - 1 ? 0 : dp(6)));
			n++;
		}
		if (total == 0) {
			LinearLayout empty = card(C_STROKE);
			empty.addView(text("Tu catálogo está vacío", 16, C_TEXT, true));
			TextView et = text("Toca «Añadir juegos» y elige tus ISO o CSO de PSP. Aparecerán aquí con su icono, y podrás cambiar de juego con un toque.", 13, C_MUTED, false);
			et.setPadding(0, dp(4), 0, 0);
			empty.addView(et);
			col.addView(empty, cardParams());
		}

		// Games with their own controls
		TextView label = text("JUEGOS COMPATIBLES", 12, C_MUTED, true);
		label.setLetterSpacing(0.18f);
		label.setPadding(0, dp(28), 0, 0);
		col.addView(label);
		for (int g = 0; g < SUPPORTED_GAMES.length; g++) {
			col.addView(supportedCard(g, counts[SUPPORTED_BADGES[g]] > 0), cardParams());
		}
		TextView otherPsp = text("Cualquier otro juego de PSP también arranca, con los botones de PSP, los efectos visuales y FSR.", 12, 0xFF6C6C78, false);
		otherPsp.setPadding(0, dp(12), 0, 0);
		col.addView(otherPsp);
	}

	private void addFilterChip(LinearLayout chips, final int badge, String label) {
		TextView c = chip(label);
		c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		c.setPadding(dp(14), dp(8), dp(14), dp(8));
		styleChip(c, catalogFilter == badge);
		c.setOnClickListener(v -> {
			catalogFilter = badge;
			fillCatalog();
		});
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.rightMargin = dp(8);
		chips.addView(c, lp);
	}

	private View catalogTile(final org.json.JSONObject e, final boolean selected, boolean access) {
		final String uri = e.optString("uri");
		final String title = e.optString("title");
		String id = e.optString("id");
		int badge = GameCatalog.badge(id, title);

		LinearLayout tile = new LinearLayout(this);
		tile.setOrientation(LinearLayout.VERTICAL);
		tile.setPadding(dp(8), dp(8), dp(8), dp(10));
		tile.setBackground(ripple(C_CARD, selected ? C_ACCENT : C_STROKE, 14));
		makeClickable(tile);

		FrameLayout pic = new FrameLayout(this);
		final IconView img = new IconView(this);
		GradientDrawable round = new GradientDrawable();
		round.setColor(0xFF101015);
		round.setCornerRadius(dp(10));
		img.setBackground(round);
		img.setClipToOutline(true);
		img.setTag(uri);
		Bitmap hit = GameCatalog.cached(uri);
		img.setImageBitmap(hit != null ? hit : GameCatalog.cover(badge, title.isEmpty() ? e.optString("name") : title, dp(120)));
		if (hit == null) {
			GameCatalog.load(this, uri, handler, (u, bmp) -> {
				if (bmp != null && u.equals(img.getTag())) {
					img.setImageBitmap(bmp);
				}
			});
		}
		pic.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		if (selected) {
			TextView inUse = text("▶ ELEGIDO", 10, Color.WHITE, true);
			inUse.setLetterSpacing(0.1f);
			inUse.setPadding(dp(7), dp(3), dp(7), dp(3));
			GradientDrawable pill = new GradientDrawable();
			pill.setColor(C_ACCENT);
			pill.setCornerRadius(dp(8));
			inUse.setBackground(pill);
			FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
			plp.setMargins(dp(6), dp(6), 0, 0);
			pic.addView(inUse, plp);
		}
		tile.addView(pic);

		TextView name = text(title.isEmpty() ? e.optString("name") : title, 14, C_TEXT, true);
		name.setMaxLines(2);
		name.setEllipsize(TextUtils.TruncateAt.END);
		name.setPadding(0, dp(8), 0, 0);
		tile.addView(name);

		TextView kind = text("●  " + GameCatalog.BADGE_NAMES[badge], 10, GameCatalog.BADGE_COLORS[badge] == 0xFF6C6C78 ? C_MUTED : GameCatalog.BADGE_COLORS[badge], true);
		kind.setLetterSpacing(0.12f);
		kind.setPadding(0, dp(4), 0, 0);
		tile.addView(kind);

		GameInspector.Result r = new GameInspector.Result(e.optString("format"), id, title);
		String pretty = r.prettyDiscId();
		String format = e.optString("format");
		String line = (pretty == null || pretty.isEmpty() ? "" : pretty) + (format.isEmpty() ? "" : (pretty == null || pretty.isEmpty() ? "" : " · ") + format);
		TextView detail = text(access ? (line.isEmpty() ? e.optString("name") : line) : "⚠ Sin acceso: vuelve a añadirlo", 11, access ? C_MUTED : C_WARN, false);
		detail.setMaxLines(1);
		detail.setEllipsize(TextUtils.TruncateAt.END);
		detail.setPadding(0, dp(2), 0, 0);
		tile.addView(detail);

		tile.setOnClickListener(v -> {
			if (selected || switchGame(e)) {
				if (catalogDialog != null) {
					catalogDialog.dismiss();
				}
			}
		});
		tile.setOnLongClickListener(v -> {
			if (selected) {
				showMessage("Es el juego elegido", "Elige otro juego antes de quitar este del catálogo.");
			} else {
				confirmRemoveFromLibrary(e);
			}
			return true;
		});
		tile.setContentDescription((title.isEmpty() ? e.optString("name") : title) + (selected ? ", elegido" : ""));
		return tile;
	}

	// -------------------------------------------------------------------------------------------
	// Other systems: arcade games (FBNeo, ArcadeActivity) and MUGEN games (Ikemen GO, MugenActivity)

	private View otherSystemTile(final org.json.JSONObject e) {
		final boolean arcade = ArcadeLibrary.SYSTEM.equals(GameCatalog.system(e));
		final String title = e.optString("title", e.optString("name"));
		int badge = GameCatalog.badge(e);
		LinearLayout tile = new LinearLayout(this);
		tile.setOrientation(LinearLayout.VERTICAL);
		tile.setPadding(dp(8), dp(8), dp(8), dp(10));
		tile.setBackground(ripple(C_CARD, C_STROKE, 14));
		makeClickable(tile);

		IconView img = new IconView(this);
		GradientDrawable round = new GradientDrawable();
		round.setColor(0xFF101015);
		round.setCornerRadius(dp(10));
		img.setBackground(round);
		img.setClipToOutline(true);
		img.setImageBitmap(GameCatalog.cover(badge, title, dp(120)));
		FrameLayout pic = new FrameLayout(this);
		pic.addView(img, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		TextView play = text("▶ JUGAR", 10, Color.WHITE, true);
		play.setLetterSpacing(0.1f);
		play.setPadding(dp(7), dp(3), dp(7), dp(3));
		GradientDrawable pill = new GradientDrawable();
		pill.setColor(GameCatalog.BADGE_COLORS[badge]);
		pill.setCornerRadius(dp(8));
		play.setBackground(pill);
		FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
		plp.setMargins(dp(6), dp(6), 0, 0);
		pic.addView(play, plp);
		tile.addView(pic);

		TextView name = text(title, 14, C_TEXT, true);
		name.setMaxLines(2);
		name.setEllipsize(TextUtils.TruncateAt.END);
		name.setPadding(0, dp(8), 0, 0);
		tile.addView(name);
		TextView kind = text("●  " + GameCatalog.BADGE_NAMES[badge], 10, GameCatalog.BADGE_COLORS[badge], true);
		kind.setLetterSpacing(0.12f);
		kind.setPadding(0, dp(4), 0, 0);
		tile.addView(kind);
		File file = otherFile(e);
		boolean there = file != null && file.exists();
		TextView detail = text(there ? (arcade ? e.optString("name") + " · FBNeo" : "Ikemen GO · " + e.optString("name"))
			: "⚠ Ya no está en la app: vuelve a añadirlo", 11, there ? C_MUTED : C_WARN, false);
		detail.setMaxLines(1);
		detail.setEllipsize(TextUtils.TruncateAt.END);
		detail.setPadding(0, dp(2), 0, 0);
		tile.addView(detail);

		tile.setOnClickListener(v -> playOther(e));
		tile.setOnLongClickListener(v -> {
			confirmRemoveOther(e);
			return true;
		});
		tile.setContentDescription(title + ", " + GameCatalog.BADGE_NAMES[badge]);
		return tile;
	}

	/** The copy inside the app: the ROM zip, or the MUGEN game's folder. */
	private static File otherFile(org.json.JSONObject e) {
		String uri = e.optString("uri");
		if (uri.isEmpty()) {
			return null;
		}
		String path = Uri.parse(uri).getPath();
		return path == null ? null : new File(path);
	}

	private void playOther(org.json.JSONObject e) {
		if (findEmuPid() > 0) {
			showMessage("Hay una partida de PSP abierta", "Cierra la partida en curso (botón «Cerrar partida» de arriba) antes de abrir otro juego.");
			return;
		}
		File f = otherFile(e);
		if (f == null || !f.exists()) {
			showMessage("No encuentro el juego", "La copia que guardó la app ya no está. Quítalo del catálogo (mantén pulsado) y vuelve a añadirlo.");
			return;
		}
		String title = e.optString("title", e.optString("name"));
		if (ArcadeLibrary.SYSTEM.equals(GameCatalog.system(e))) {
			ArcadeCheck.Report r = ArcadeCheck.check(this, f);
			String problems = r.ok() ? "" : r.describe(f.getName()).trim();
			if (r.info == null && !ArcadeLibrary.looksNeoGeo(r.set)) {
				problems = "";   // Another board (CPS…): nothing to check here.
			}
			if (!problems.isEmpty()) {
				new AlertDialog.Builder(this)
					.setTitle("FBNeo no va a poder abrirlo")
					.setMessage(title + "\n\n" + problems)
					.setPositiveButton("Añadir archivos", (d, w) -> pickArcade())
					.setNeutralButton("Probar igual", (d, w) -> startArcade(f, title))
					.setNegativeButton("Cancelar", null)
					.show();
				return;
			}
			startArcade(f, title);
		} else {
			startMugen(f, title);
		}
	}

	private void startArcade(File rom, String title) {
		Intent i = new Intent(this, ArcadeActivity.class);
		i.putExtra(ArcadeActivity.EXTRA_ROM, rom.getAbsolutePath());
		i.putExtra(ArcadeActivity.EXTRA_TITLE, title);
		try {
			startActivity(i);
		} catch (RuntimeException ex) {
			Log.w(TAG, "Arcade", ex);
			showMessage("No se pudo abrir el juego", String.valueOf(ex.getMessage()));
		}
	}

	private void startMugen(final File root, final String title) {
		if (!MugenInstaller.engineBundled(this)) {
			showMessage("Falta el motor MUGEN", "Esta versión de la app no trae Ikemen GO. Instala la última versión.");
			return;
		}
		ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		int gles = am == null ? 0 : am.getDeviceConfigurationInfo().reqGlEsVersion;
		String problem = null;
		if (Build.VERSION.SDK_INT < 29) {
			showMessage("Android demasiado viejo", "Ikemen GO, el motor de los juegos MUGEN, necesita Android 13 o más nuevo (tu teléfono tiene Android "
				+ Build.VERSION.RELEASE + ").");
			return;
		} else if (Build.VERSION.SDK_INT < 33) {
			problem = "Ikemen GO pide Android 13 o más nuevo y tu teléfono tiene Android " + Build.VERSION.RELEASE + ". Puede que no arranque.";
		} else if (gles != 0 && gles < 0x30002) {
			problem = "Ikemen GO necesita una GPU con OpenGL ES 3.2 y tu teléfono indica OpenGL ES " + (gles >> 16) + "." + (gles & 0xFFFF) + ". Puede que no arranque.";
		}
		Runnable go = () -> {
			Intent i = new Intent(this, MugenActivity.class);
			i.putExtra(MugenActivity.EXTRA_PATH, root.getAbsolutePath());
			i.putExtra(MugenActivity.EXTRA_TITLE, title);
			try {
				startActivity(i);
			} catch (RuntimeException ex) {
				Log.w(TAG, "MUGEN", ex);
				showMessage("No se pudo abrir el juego", String.valueOf(ex.getMessage()));
			}
		};
		if (problem != null) {
			new AlertDialog.Builder(this)
				.setTitle("Tu teléfono podría no ser compatible")
				.setMessage(problem)
				.setPositiveButton("Probar igual", (d, w) -> go.run())
				.setNegativeButton("Cancelar", null)
				.show();
		} else {
			go.run();
		}
	}

	private void confirmRemoveOther(final org.json.JSONObject e) {
		final File f = otherFile(e);
		final boolean arcade = ArcadeLibrary.SYSTEM.equals(GameCatalog.system(e));
		// The whole folder the import created (the game can be a folder or two inside it).
		File top = f;
		if (!arcade && f != null) {
			File games = MugenInstaller.gamesDir(this);
			File cur = f;
			while (cur != null && cur.getParentFile() != null && !games.equals(cur.getParentFile())) {
				cur = cur.getParentFile();
			}
			top = cur != null && games.equals(cur.getParentFile()) ? cur : f;
		}
		final File remove = top;
		long bytes = remove == null ? 0 : (arcade ? remove.length() : MugenInstaller.sizeOf(remove));
		String title = e.optString("title", e.optString("name"));
		new AlertDialog.Builder(this)
			.setTitle("¿Quitar del catálogo?")
			.setMessage(title + "\n\nSe borra la copia que guardó la app (" + String.format(Locale.ROOT, "%.0f MB", bytes / 1048576.0)
				+ ")" + (arcade ? "" : ", con sus partidas guardadas") + ". Tu archivo original no se toca.")
			.setPositiveButton("Quitar y borrar", (d, w) -> {
				org.json.JSONArray lib = loadLibrary();
				org.json.JSONArray out = new org.json.JSONArray();
				for (int i = 0; i < lib.length(); i++) {
					org.json.JSONObject x = lib.optJSONObject(i);
					if (x != null && !x.optString("uri").equals(e.optString("uri"))) {
						out.put(x);
					}
				}
				prefs.edit().putString(K_LIBRARY, out.toString()).apply();
				refreshLibrary();
				if (remove != null) {
					new Thread(() -> MugenInstaller.deleteTree(remove), "RemoveGame").start();
				}
			})
			.setNegativeButton("Cancelar", null)
			.show();
	}

	/** After an arcade / MUGEN game closed by itself: what GameRun saw. */
	private final Runnable lastGameCheck = () -> {
		if (isFinishing() || isDestroyed()) {
			return;
		}
		final String report;
		try {
			report = GameRun.report(this);
		} catch (RuntimeException ex) {
			Log.w(TAG, "Last game", ex);
			return;
		}
		if (report == null) {
			return;
		}
		new AlertDialog.Builder(this)
			.setTitle("El juego se cerró solo")
			.setMessage("Esto es lo que pasó (mándamelo en una captura para corregirlo):\n\n" + report)
			.setPositiveButton("Copiar", (d, w) -> {
				android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
				if (cm != null) {
					cm.setPrimaryClip(ClipData.newPlainText("Informe del juego", report));
					Toast.makeText(this, "Informe copiado", Toast.LENGTH_SHORT).show();
				}
			})
			.setNegativeButton("Cerrar", null)
			.show();
	};

	private void pickArcade() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
		try {
			startActivityForResult(intent, REQUEST_ADD_ARCADE);
		} catch (ActivityNotFoundException ex) {
			showMessage("Sin selector de archivos", "Tu teléfono no tiene un selector de archivos disponible.");
		}
	}

	private android.app.ProgressDialog progressDialog(String title) {
		android.app.ProgressDialog pd = new android.app.ProgressDialog(this);
		pd.setTitle(title);
		pd.setMessage("Un momento…");
		pd.setCancelable(false);
		pd.show();
		getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		return pd;
	}

	private void doneProgress(android.app.ProgressDialog pd) {
		getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		try {
			pd.dismiss();
		} catch (RuntimeException ignored) {
			// Activity going away.
		}
	}

	/** «+ Arcade / Neo Geo»: copies each ZIP into the app; games go to the catalog, BIOS sets stay next to them. */
	private void onArcadeAdded(Intent data) {
		final List<Uri> uris = new ArrayList<>();
		ClipData clip = data.getClipData();
		if (clip != null) {
			for (int i = 0; i < clip.getItemCount(); i++) {
				Uri u = clip.getItemAt(i).getUri();
				if (u != null) {
					uris.add(u);
				}
			}
		} else if (data.getData() != null) {
			uris.add(data.getData());
		}
		if (uris.isEmpty()) {
			return;
		}
		final android.app.ProgressDialog pd = progressDialog("Copiando juegos de arcade");
		final android.content.Context app = getApplicationContext();
		new Thread(() -> {
			final List<org.json.JSONObject> added = new ArrayList<>();
			final List<String> bios = new ArrayList<>();
			final List<String> notes = new ArrayList<>();
			final List<String> skipped = new ArrayList<>();
			for (Uri u : uris) {
				final String name = ArcadeLibrary.displayName(app, u);
				if (!ArcadeLibrary.isArchive(name)) {
					skipped.add(name + " (no es .zip ni .7z)");
					continue;
				}
				handler.post(() -> pd.setMessage("Copiando " + name + "…"));
				try {
					File f = ArcadeLibrary.copyIn(app, u, name);
					addArcadeArchive(app, f, name, true, added, bios, notes, skipped);
				} catch (Throwable t) {
					Log.w(TAG, "Arcade copy " + name, t);
					skipped.add(name + " (" + t.getMessage() + ")");
				}
			}
			// What FBNeo will be missing, now that every file is in place.
			final List<String> problems = new ArrayList<>();
			for (org.json.JSONObject e : added) {
				File f = otherFile(e);
				if (f == null) {
					continue;
				}
				ArcadeCheck.Report r = ArcadeCheck.check(app, f);
				if (!r.ok()) {
					String d = r.describe(f.getName()).trim();
					if (!d.isEmpty()) {
						problems.add(e.optString("title") + ":\n" + d);
					}
				}
			}
			handler.post(() -> {
				doneProgress(pd);
				if (isFinishing() || isDestroyed()) {
					return;
				}
				for (int i = added.size() - 1; i >= 0; i--) {
					putInLibrary(added.get(i), true);
				}
				refreshLibrary();
				StringBuilder msg = new StringBuilder();
				if (!added.isEmpty()) {
					msg.append(added.size() == 1 ? "Se añadió 1 juego de arcade." : "Se añadieron " + added.size() + " juegos de arcade.");
				}
				if (!bios.isEmpty()) {
					msg.append(msg.length() > 0 ? "\n" : "").append("BIOS guardada: ").append(TextUtils.join(", ", bios)).append('.');
				}
				for (String n : notes) {
					msg.append(msg.length() > 0 ? "\n" : "").append(n);
				}
				if (!problems.isEmpty()) {
					msg.append(msg.length() > 0 ? "\n\n" : "").append("⚠ Para que FBNeo los abra:\n\n").append(TextUtils.join("\n\n", problems));
				}
				if (!skipped.isEmpty()) {
					msg.append(msg.length() > 0 ? "\n\n" : "").append("No se añadieron:\n").append(TextUtils.join("\n", skipped));
				}
				if (msg.length() > 0) {
					showMessage("Arcade / Neo Geo", msg.toString());
				}
			});
		}, "ArcadeAdd").start();
	}

	/**
	 * One archive copied into the ROM folder: unpacks packs (a ZIP of ZIPs), gives a ZIP its
	 * FBNeo set name when its files say which game it is, keeps BIOS sets out of the catalog.
	 */
	private void addArcadeArchive(android.content.Context app, File f, String shownName, boolean unpack, List<org.json.JSONObject> added,
		List<String> bios, List<String> notes, List<String> skipped) throws IOException, org.json.JSONException {
		boolean zip = f.getName().endsWith(".zip");
		java.util.Set<Integer> crcs = ArcadeCheck.zipCrcs(f);   // ZIP directory or 7z header
		if (zip && crcs == null) {
			skipped.add(shownName + " (el ZIP está dañado o incompleto)");
			if (!f.delete()) {
				Log.w(TAG, "Could not delete " + f);
			}
			return;
		}
		if (zip && unpack) {
			List<String> inner = ArcadeCheck.innerArchives(f);
			if (!inner.isEmpty() && inner.size() * 2 >= crcs.size()) {
				// A pack: each archive inside is a romset (or the BIOS).
				for (String entry : inner) {
					File in = ArcadeLibrary.extractInner(app, f, entry);
					addArcadeArchive(app, in, entry, false, added, bios, notes, skipped);
				}
				if (!f.delete()) {
					Log.w(TAG, "Could not delete " + f);
				}
				return;
			}
		}
		String set = ArcadeLibrary.setName(f.getName());
		if (crcs != null && ArcadeCheck.info(app, set) == null && !ArcadeLibrary.isBios(set)) {
			ArcadeCheck.SetInfo match = ArcadeCheck.identify(app, crcs);
			if (match != null) {
				f = ArcadeLibrary.renameTo(f, match.name);
				notes.add("«" + shownName + "» es " + ("neogeo".equals(match.name) ? "la BIOS de Neo Geo" : "«" + ArcadeCheck.title(app, match.name) + "»")
					+ ": se guardó como " + f.getName() + ", el nombre que busca FBNeo.");
				set = match.name;
			}
		}
		if (ArcadeLibrary.isBios(set)) {
			bios.add(f.getName());
			return;
		}
		added.add(new org.json.JSONObject()
			.put("system", ArcadeLibrary.SYSTEM)
			.put("uri", Uri.fromFile(f).toString())
			.put("name", f.getName())
			.put("title", ArcadeCheck.title(app, set))
			.put("id", set)
			.put("format", zip ? "ZIP" : "7Z"));
	}

	private void pickMugen() {
		new AlertDialog.Builder(this)
			.setTitle("Añadir un juego MUGEN")
			.setMessage("Elige la carpeta del juego (la que tiene «chars», «data» y «stages») o su archivo .zip o .7z. "
				+ "Si viene en .rar o en varias partes (.7z.001…), descomprímelo primero (por ejemplo con ZArchiver) y elige la carpeta.\n\n"
				+ "Se copia dentro de la app; los juegos grandes tardan unos minutos.")
			.setPositiveButton("Elegir carpeta", (d, w) -> {
				Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
				try {
					startActivityForResult(intent, REQUEST_ADD_MUGEN_FOLDER);
				} catch (ActivityNotFoundException ex) {
					showMessage("Sin selector de carpetas", "Tu teléfono no tiene un selector de carpetas disponible.");
				}
			})
			.setNeutralButton("Elegir .zip / .7z", (d, w) -> {
				Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
				intent.addCategory(Intent.CATEGORY_OPENABLE);
				intent.setType("*/*");
				try {
					startActivityForResult(intent, REQUEST_ADD_MUGEN_ZIP);
				} catch (ActivityNotFoundException ex) {
					showMessage("Sin selector de archivos", "Tu teléfono no tiene un selector de archivos disponible.");
				}
			})
			.setNegativeButton("Cancelar", null)
			.show();
	}

	private void onMugenPicked(final Uri uri, final boolean folder) {
		final android.content.Context app = getApplicationContext();
		final String name = folder ? null : ArcadeLibrary.displayName(app, uri);
		final boolean sevenZip = name != null && name.toLowerCase(Locale.ROOT).endsWith(".7z");
		if (!folder && name != null && !name.toLowerCase(Locale.ROOT).endsWith(".zip") && !sevenZip) {
			showMessage("Ese archivo no es .zip ni .7z", "Elige un .zip o un .7z, o descomprime el juego (por ejemplo con ZArchiver) y elige su carpeta. "
				+ "Si viene en varias partes (.7z.001…) o en .rar, descomprímelo primero.");
			return;
		}
		final android.app.ProgressDialog pd = progressDialog("Copiando el juego MUGEN");
		final MugenInstaller.Progress progress = (what, done, total) -> handler.post(() -> {
			if (total > 0) {
				pd.setMessage(String.format(Locale.ROOT, "%d %%  ·  %s", Math.min(100, done * 100 / total), what));
			} else {
				pd.setMessage(done + " archivos  ·  " + what);
			}
		});
		new Thread(() -> {
			File root = null;
			String error = null;
			try {
				root = folder ? MugenInstaller.importTree(app, uri, progress)
					: sevenZip ? MugenInstaller.import7z(app, uri, name, progress) : MugenInstaller.importZip(app, uri, name, progress);
			} catch (Throwable t) {
				Log.w(TAG, "MUGEN import", t);
				error = t.getMessage() == null ? t.toString() : t.getMessage();
			}
			final File gameRoot = root;
			final String err = error;
			handler.post(() -> {
				doneProgress(pd);
				if (isFinishing() || isDestroyed()) {
					return;
				}
				if (gameRoot == null) {
					showMessage("No se pudo añadir el juego", err == null ? "Error desconocido." : err);
					return;
				}
				String title = gameRoot.getName();
				try {
					org.json.JSONObject entry = new org.json.JSONObject()
						.put("system", MugenInstaller.SYSTEM)
						.put("uri", Uri.fromFile(gameRoot).toString())
						.put("name", gameRoot.getName())
						.put("title", title)
						.put("id", "MUGEN")
						.put("format", "MUGEN");
					putInLibrary(entry, true);
				} catch (org.json.JSONException ex) {
					Log.w(TAG, "MUGEN entry", ex);
				}
				refreshLibrary();
				Toast.makeText(this, "«" + title + "» está en el catálogo", Toast.LENGTH_LONG).show();
			});
		}, "MugenAdd").start();
	}

	private View supportedCard(int g, boolean owned) {
		int badge = SUPPORTED_BADGES[g];
		LinearLayout c = card(C_STROKE);
		c.setOrientation(LinearLayout.HORIZONTAL);
		IconView img = new IconView(this);
		GradientDrawable round = new GradientDrawable();
		round.setCornerRadius(dp(8));
		img.setBackground(round);
		img.setClipToOutline(true);
		img.setImageBitmap(GameCatalog.cover(badge, SUPPORTED_GAMES[g][0], dp(96)));
		LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(88), ViewGroup.LayoutParams.WRAP_CONTENT);
		ilp.rightMargin = dp(14);
		c.addView(img, ilp);
		LinearLayout texts = new LinearLayout(this);
		texts.setOrientation(LinearLayout.VERTICAL);
		texts.addView(text(SUPPORTED_GAMES[g][0], 15, C_TEXT, true));
		TextView ids = text(SUPPORTED_GAMES[g][1], 11, GameCatalog.BADGE_COLORS[badge], true);
		ids.setPadding(0, dp(2), 0, 0);
		texts.addView(ids);
		TextView what = text(SUPPORTED_GAMES[g][2], 12, C_MUTED, false);
		what.setPadding(0, dp(6), 0, 0);
		texts.addView(what);
		if (owned) {
			TextView yes = text("✓ En tu catálogo", 12, C_OK, true);
			yes.setPadding(0, dp(6), 0, 0);
			texts.addView(yes);
		}
		c.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		return c;
	}

	private void pickGames() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
		try {
			startActivityForResult(intent, REQUEST_ADD_GAMES);
		} catch (ActivityNotFoundException e) {
			showMessage("Sin selector de archivos",
				"Tu teléfono no tiene un selector de archivos disponible. Instala o activa la app «Archivos» e inténtalo de nuevo.");
		}
	}

	private static boolean looksLikeGameFile(String name) {
		String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
		for (String ext : new String[] { ".iso", ".cso", ".chd", ".zso", ".pbp", ".elf", ".prx" }) {
			if (n.endsWith(ext)) {
				return true;
			}
		}
		return false;
	}

	/** «Añadir juegos»: reads every file chosen (name, disc id, title, icon) and adds it. */
	private void onGamesAdded(Intent data) {
		final List<Uri> uris = new ArrayList<>();
		ClipData clip = data.getClipData();
		if (clip != null) {
			for (int i = 0; i < clip.getItemCount(); i++) {
				Uri u = clip.getItemAt(i).getUri();
				if (u != null) {
					uris.add(u);
				}
			}
		} else if (data.getData() != null) {
			uris.add(data.getData());
		}
		if (uris.isEmpty()) {
			return;
		}
		for (Uri u : uris) {
			try {
				getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
			} catch (SecurityException e) {
				Log.w(TAG, "Could not persist permission for " + u, e);
			}
		}
		addingGames += uris.size();
		fillCatalog();
		final android.content.Context app = getApplicationContext();
		new Thread(() -> {
			final List<org.json.JSONObject> added = new ArrayList<>();
			final List<String> skippedNames = new ArrayList<>();
			final List<String> skippedUris = new ArrayList<>();
			for (Uri u : uris) {
				String key = u.toString();
				String name = queryDisplayName(u);
				try {
					GameInspector.Result r = GameInspector.inspect(app, u, GameCatalog.iconFile(app, key));
					GameCatalog.inspected(app, key);
					if (r.discId == null && r.title == null && !looksLikeGameFile(name)) {
						skippedNames.add(name);
						skippedUris.add(key);
						continue;
					}
					added.add(new org.json.JSONObject()
						.put("uri", key)
						.put("name", name)
						.put("title", r.title == null ? "" : r.title)
						.put("id", r.discId == null ? "" : r.discId)
						.put("format", r.format == null ? "" : r.format));
				} catch (Throwable t) {
					Log.w(TAG, "Could not add " + key, t);
					skippedNames.add(name);
					skippedUris.add(key);
				}
			}
			handler.post(() -> {
				addingGames = Math.max(0, addingGames - uris.size());
				if (isFinishing() || isDestroyed()) {
					return;
				}
				// Newest last in the loop, so the first file chosen ends up first.
				for (int i = added.size() - 1; i >= 0; i--) {
					putInLibrary(added.get(i), false);
				}
				for (String u : skippedUris) {
					boolean inLibrary = false;
					org.json.JSONArray lib = loadLibrary();
					for (int i = 0; i < lib.length(); i++) {
						org.json.JSONObject x = lib.optJSONObject(i);
						inLibrary |= x != null && u.equals(x.optString("uri"));
					}
					if (!inLibrary) {
						releaseGame(u);
					}
				}
				if (prefs.getString(K_URI, null) == null && !added.isEmpty()) {
					switchGame(added.get(0));
				}
				refreshLibrary();
				if (!added.isEmpty()) {
					Toast.makeText(this, added.size() == 1 ? "Se añadió 1 juego al catálogo" : "Se añadieron " + added.size() + " juegos al catálogo", Toast.LENGTH_SHORT).show();
				}
				if (!skippedNames.isEmpty()) {
					showMessage(skippedNames.size() == 1 ? "Un archivo no se añadió" : skippedNames.size() + " archivos no se añadieron",
						TextUtils.join("\n", skippedNames) + "\n\nNo parecen juegos de PSP (ISO o CSO) o no se pudieron abrir.");
				}
			});
		}, "GameCatalogAdd").start();
	}

	private boolean switchGame(org.json.JSONObject e) {
		if (findEmuPid() > 0) {
			showMessage("Hay una partida abierta", "Cierra la partida en curso (botón «Cerrar partida» de arriba) antes de cambiar de juego.");
			return false;
		}
		Uri uri = Uri.parse(e.optString("uri"));
		if (!hasReadAccess(uri)) {
			showMessage("No tengo acceso a ese archivo", "Puede que se haya movido o borrado. Vuelve a añadirlo en el catálogo con «Añadir juegos».");
			return false;
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
		return true;
	}

	private void confirmRemoveFromLibrary(org.json.JSONObject e) {
		String title = e.optString("title");
		new AlertDialog.Builder(this)
			.setTitle("¿Quitar del catálogo?")
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
				releaseGame(e.optString("uri"));
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
		boolean spriteMode = fsr && fsrSprites();
		boolean applies = (effect.shaders.length == 0 || fsr) && !spriteMode;
		if (spriteMode) {
			hint = "Con AMD FSR en «Modo sprites» el juego se dibuja a 480 × 272, xBR lo dobla y FSR lo escala ("
				+ fsrScaleLabel(fsrScale()) + "): esta resolución no se usa. Para usarla, apaga «Modo sprites» en «Gráficos».";
		} else if (fsr) {
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

	/** The HUD and the profiles can read this copy (or no game chosen yet). */
	private boolean isMemoryGame() {
		String id = prefs.getString(K_ID, null);
		return id == null || GameRosters.memoryKnown(id);
	}

	private String prettyGameId() {
		String id = new GameInspector.Result(null, prefs.getString(K_ID, ""), null).prettyDiscId();
		return id == null ? "" : id;
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
		int kind = gameKind();
		GameRosters.Roster roster = GameRosters.forKind(kind);
		int saved = 0;
		int custom = 0;
		for (String key : roster.keys) {
			if (savedProfile(key).isFile()) {
				saved++;
			}
			if (prefs.getString(K_CHAR_STYLE + key, null) != null) {
				custom++;
			}
		}
		StringBuilder s = new StringBuilder();
		if (!isMemoryGame()) {
			s.append("⚠ Tu copia es ").append(prettyGameId()).append(". El cambio automático funciona con ").append(GameRosters.knownVersions(kind))
				.append("; con otras versiones se usa siempre el tipo de botones general.\n\n");
		} else if (kind == GameInspector.KIND_BB) {
			s.append("En BlazBlue la app reconoce a tu personaje por los nombres de sus sprites en la memoria del juego (experimental).\n\n");
		} else if (kind == GameInspector.KIND_KOF) {
			s.append("En KOF funciona en '96, '97 y '98: los botones cambian cuando entra el siguiente miembro de tu equipo. En '94 y '95 se usa el tipo de botones general.\n\n");
		} else if (kind == GameInspector.KIND_SF) {
			s.append("Con Street Fighter Alpha 3 todavía no: no se sabe en qué parte de la memoria guarda el juego qué personaje usas, así que se usa siempre el tipo de botones general. "
				+ "Si juegas un par de combates con esto activado y mandas el informe del juego (abajo), con él se puede encontrar.\n\n");
		}
		if (perCharacterButton != null) {
			perCharacterButton.setVisibility(roster.keys.length == 0 ? View.GONE : View.VISIBLE);
		}
		if (gameReportButton != null) {
			gameReportButton.setVisibility(kind == GameInspector.KIND_GG ? View.GONE : View.VISIBLE);
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
		int kind = gameKind();
		if (!isMemoryGame()) {
			desc = "⚠ Tu copia es " + prettyGameId() + ": el HUD propio funciona con " + GameRosters.knownVersions(kind) + ".\n\n" + desc;
		} else if (kind == GameInspector.KIND_BB) {
			desc = "En BlazBlue: vida, Heat y combos (los golpes se cuentan por las bajadas de vida). Los nombres salen cuando la app reconoce al personaje; BlazBlue no tiene tiempo en el HUD propio.\n\n" + desc;
		} else if (kind == GameInspector.KIND_KOF) {
			desc = "En KOF: vida, POW o POWER con sus stocks, y tiempo. Los combos se cuentan por las bajadas de vida y los nombres salen en '96, '97 y '98.\n\n" + desc;
		} else if (kind == GameInspector.KIND_SF) {
			desc = "En Street Fighter Alpha 3: vida, barra de súper, guardia y tiempo. Los combos se cuentan por las bajadas de vida; sin nombres (no se sabe dónde guarda el personaje).\n\n" + desc;
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

	/** Sprite mode: drawn at 480x272, xBR doubles it, then FSR (on by default: all four games are 2D). */
	private boolean fsrSprites() {
		return prefs.getBoolean(K_FSR_SPRITES + fsrGameKey(), true);
	}

	private int fsrDetail() {
		return Math.max(0, Math.min(100, prefs.getInt(K_FSR_DETAIL + fsrGameKey(), FSR_DEFAULT_DETAIL)));
	}

	private int fsrColor() {
		return Math.max(0, Math.min(100, prefs.getInt(K_FSR_COLOR + fsrGameKey(), FSR_DEFAULT_COLOR)));
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
			? "FSR 1.0 activo · " + (fsrSprites() ? "sprites xBR · " : "") + fsrScaleLabel(fsrScale()) + " · nitidez " + sharpLabel(fsrSharp())
				+ " · detalle " + sharpLabel(fsrDetail())
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
		boolean sprites = fsrSprites();
		fsrSpritesSwitch.setChecked(sprites);
		int detail = fsrDetail(), color = fsrColor();
		if (fsrDetailSeek.getProgress() != detail) {
			fsrDetailSeek.setProgress(detail);
		}
		if (fsrColorSeek.getProgress() != color) {
			fsrColorSeek.setProgress(color);
		}
		fsrDetailValue.setText(detail == 0 ? "apagado" : sharpLabel(detail));
		fsrColorValue.setText(color == 0 ? "apagado" : sharpLabel(color));

		// What FSR will do with the current internal resolution (same rule as patches/0005). In
		// sprite mode the game is drawn at 1x and xBR doubles it before FSR.
		int res = sprites ? 2 : clampRes(prefs.getInt(K_RES, recommendedRes));
		int rw = PSP_W * res, rh = PSP_H * res;
		int[] screen = gameSizeOnScreen();
		float room = Math.min(screen[0] / (float) rw, screen[1] / (float) rh);
		float wanted = scale / 10f;
		float actual = Math.max(1f, Math.min(wanted, room));
		// A few pixels short of the full factor (e.g. 1.98x) is still "the factor you chose".
		boolean capped = actual < wanted * 0.97f;
		int ow = Math.round(rw * actual), oh = Math.round(rh * actual);
		fsrInfo.setText((sprites ? "Modo sprites: el juego se dibuja a 480 × 272 → xBR 960 × 544 → FSR " : "Resolución interna " + res + "× (" + rw + " × " + rh + ") → FSR ")
			+ (capped ? String.format(Locale.ROOT, "%.2f×", actual) : fsrScaleLabel(scale))
			+ " → " + ow + " × " + oh + (detail > 0 ? " → CAS" : "") + "\nEl juego ocupa " + screen[0] + " × " + screen[1] + " en tu pantalla."
			+ (sprites ? " La resolución interna elegida arriba no se usa en este modo." : ""));
		String warn = null;
		if (sprites && capped) {
			warn = String.format(Locale.ROOT, "El factor se queda en ×%.2f: la imagen no puede ser más grande que tu pantalla.", actual);
		} else if (sprites) {
			warn = null;
		} else if (room <= 1.05f) {
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
		+ "(github.com/GPUOpen-Effects/FidelityFX-FSR), y «Detalle extra» del de AMD FidelityFX CAS "
		+ "(github.com/GPUOpen-Effects/FidelityFX-CAS), ambos publicados con la licencia MIT:\n\n"
		+ AMD_MIT_LICENSE + "\n\n"
		+ "AMD y FidelityFX son marcas de Advanced Micro Devices, Inc. Esta app no está afiliada ni respaldada por AMD.\n\n"
		+ "■ Efecto «IA» (NNEDI3)\n"
		+ "Pesos de la red NNEDI3 (tritical), tomados de los shaders de PPSSPP.\n\n"
		+ "■ Letras P, K, S, HS y D\n"
		+ "Dibujadas con Roboto Condensed (Google), licencia Apache 2.0, que ya incluye PPSSPP.\n\n"
		+ "■ FBNeo (Arcade / Neo Geo)\n"
		+ "FinalBurn Neo, del equipo de FBNeo (github.com/finalburnneo/FBNeo), en su versión libretro compilada por Lemuroid "
		+ "(github.com/Swordfish90/LemuroidCores). Licencia de FBNeo: uso no comercial. La app es gratis.\n\n"
		+ "■ LibretroDroid\n"
		+ "Frontend libretro para Android de Filippo Scognamiglio (github.com/Swordfish90/LibretroDroid). Licencia GPL 3.0.\n\n"
		+ "■ Ikemen GO (MUGEN)\n"
		+ "Motor de juegos de pelea compatible con MUGEN, del equipo de Ikemen GO (github.com/ikemen-engine/Ikemen-GO), "
		+ "versión 1.0.0 para Android. Licencia MIT; sus archivos de pantalla bajo Creative Commons. Usa SDL 2 (licencia zlib, "
		+ "con el código Java de ikemen-droid, modificado), FFmpeg (LGPL 2.1, enlazado dinámicamente) y libxmp. "
		+ "La lista completa está en assets/ikemen/LICENSES.txt dentro de la app.\n\n"
		+ "■ Juegos\n"
		+ "Guilty Gear es una marca de Arc System Works; BlazBlue, de Arc System Works; The King of Fighters, Metal Slug y Neo Geo, de SNK; "
		+ "Street Fighter, de Capcom. Este proyecto no está afiliado ni respaldado por ninguna de ellas y no incluye ningún archivo "
		+ "de sus juegos ni BIOS: cada jugador usa sus propias copias. Los juegos MUGEN son de sus autores.";

	/** A special move button's input: chosen in the launcher, or the character's default. */
	private static String gestureKey(int id, int slot) {
		return K_GES_MOVE + (id == GESTURE_BB ? "bb" : id == GESTURE_KOF ? "kof" : id == GESTURE_SF ? "sf" : String.valueOf(id)) + "_" + slot;
	}

	/** The button names a game's moves are written with. */
	private static String[] gestureButtons(int id) {
		return id == GESTURE_SF ? GestureMoves.BUTTONS_SF : id < 0 ? GestureMoves.BUTTONS_BB : GestureMoves.BUTTONS_GG_SPLIT;
	}

	private String gestureMove(int id, int slot) {
		String custom = prefs.getString(gestureKey(id, slot), null);
		if (GestureMoves.validWith(custom, gestureButtons(id))) {
			return custom;
		}
		return gestureDefault(id, slot);
	}

	private static String gestureDefault(int id, int slot) {
		if (id == GESTURE_BB) {
			return GestureMoves.GENERIC_BB[slot];
		}
		if (id == GESTURE_KOF) {
			return GestureMoves.GENERIC_KOF[slot];
		}
		if (id == GESTURE_SF) {
			return GestureMoves.GENERIC_SF[slot];
		}
		return id == 0 ? GestureMoves.GENERIC[slot] : GestureMoves.defaultMove(id, slot);
	}

	private String gestureCharName(int id) {
		return id == GESTURE_BB ? "BlazBlue" : id == GESTURE_KOF ? "KOF" : id == GESTURE_SF ? "Street Fighter" : id == 0 ? "General" : CHARACTERS[id];
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
		final boolean kof = isKof();
		final boolean sf = isStreetFighter();
		final boolean oneSet = bb || kof || sf;
		final int shown = bb ? GESTURE_BB : kof ? GESTURE_KOF : sf ? GESTURE_SF : gestureChar;
		gestureGuide.setText(bb ? GESTURE_GUIDE_BB : kof ? GESTURE_GUIDE_KOF : sf ? GESTURE_GUIDE_SF : GESTURE_GUIDE);
		gestureCharButton.setText(oneSet ? gameShortName() + " · los mismos para todos los personajes" : "Personaje: " + gestureCharName(gestureChar) + "  ▾");
		gestureCharButton.setEnabled(!oneSet);
		gestureMovesNote.setText(oneSet
			? "En " + gameShortName() + " los cuatro botones son los mismos con cualquier personaje (la app no lee qué personaje usas). Toca uno para cambiar su movimiento: 236 = ↓↘→, "
				+ (sf ? "[4]6 = carga ← y luego →, y los botones son LP, MP, HP, LK, MK y HK." : "y los botones son A, B, C y D.")
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
			String slotName = gestureSlotName(shown, slot);
			row.addView(text(slotName + "  ·  " + move, 15, C_TEXT, true));
			String detail = GestureMoves.arrowsWith(move, gestureButtons(shown)) + (name != null ? "  ·  " + name : custom ? "  ·  elegido por ti" : "");
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
			.setTitle(gestureCharName(id) + " · " + gestureSlotName(id, slot) + ": movimiento")
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
			.setItems(id == GESTURE_BB ? GestureMoves.BUTTON_NAMES_BB : id == GESTURE_KOF ? GestureMoves.BUTTON_NAMES_KOF
				: id == GESTURE_SF ? GestureMoves.BUTTON_NAMES_SF : GestureMoves.BUTTON_NAMES, (d, which) -> {
				String button = (id == GESTURE_SF ? GestureMoves.BUTTONS_SF : id < 0 ? GestureMoves.BUTTONS_BB : GestureMoves.BUTTONS)[which];
				prefs.edit().putString(gestureKey(id, slot), motion + button).apply();
				refreshGestures();
			})
			.setNegativeButton("Atrás", (d, w) -> showMotionPicker(id, slot))
			.show();
	}

	private static String gestureSlotName(int id, int slot) {
		if (slot == 3 && id == GESTURE_BB) {
			return "Distortion Drive";
		}
		if (slot == 3 && id == GESTURE_KOF) {
			return "Súper";
		}
		if (slot == 3 && id == GESTURE_SF) {
			return "Super Combo";
		}
		return GestureMoves.SLOT_NAMES[slot];
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
			s.append(gameKind() != GameInspector.KIND_GG
				? "⚠ Con " + gameShortName() + " no se usa: el visor lee la memoria de Guilty Gear XX ΛC+ (versión americana ULUS-10409).\n\n"
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

	/** What the emulator read in BlazBlue / KOF (UI/AcPlusProfiles.h writes it while playing). */
	private void shareGameReport() {
		File report = new File(memstickDir(), "PSP/SYSTEM/acplus_game_report.txt");
		if (!report.isFile()) {
			showMessage("Todavía no hay informe",
				"Con «Cambiar los botones según el personaje» activado (y sin gestos), juega un combate unos segundos y vuelve aquí.");
			return;
		}
		ArrayList<File> files = new ArrayList<>();
		files.add(report);
		shareFiles(files, "Informe de " + gameShortName() + " para GGXX AC+ (qué leyó la app en la memoria del juego)", "Enviar informe del juego");
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

	private String characterStyleLabel(String key) {
		String style = prefs.getString(K_CHAR_STYLE + key, null);
		String label = STYLE_ARCADE.equals(style) ? "Arcade" : STYLE_MOBILE.equals(style) ? "Móvil" : STYLE_PSP.equals(style) ? "PSP" : "General";
		if (savedProfile(key).isFile()) {
			label += " · distribución guardada";
		}
		return label;
	}

	/** The characters of the chosen game. */
	private void showCharacterList() {
		final GameRosters.Roster roster = GameRosters.forKind(gameKind());
		String[] rows = new String[roster.keys.length];
		for (int i = 0; i < roster.keys.length; i++) {
			rows[i] = roster.names[i] + "  —  " + characterStyleLabel(roster.keys[i]);
		}
		new AlertDialog.Builder(this)
			.setTitle("Botones por personaje · " + gameShortName())
			.setItems(rows, (d, which) -> showCharacterOptions(roster, which))
			.setNegativeButton("Cerrar", null)
			.show();
	}

	private void showCharacterOptions(GameRosters.Roster roster, int index) {
		final String key = roster.keys[index];
		final boolean hasSaved = savedProfile(key).isFile();
		String[] options = hasSaved
			? new String[] { "General (el de la sección Controles)", "PSP", "Arcade", "Móvil", "Borrar la distribución guardada" }
			: new String[] { "General (el de la sección Controles)", "PSP", "Arcade", "Móvil" };
		new AlertDialog.Builder(this)
			.setTitle(roster.names[index])
			.setItems(options, (d, which) -> {
				if (which == 4) {
					deleteSavedProfile(key);
				} else {
					String style = which == 1 ? STYLE_PSP : which == 2 ? STYLE_ARCADE : which == 3 ? STYLE_MOBILE : null;
					String old = prefs.getString(K_CHAR_STYLE + key, null);
					SharedPreferences.Editor e = prefs.edit();
					if (style == null) {
						e.remove(K_CHAR_STYLE + key);
					} else {
						e.putString(K_CHAR_STYLE + key, style);
					}
					e.apply();
					// A new button type replaces the layout saved for this character.
					boolean changed = old == null ? style != null : !old.equals(style);
					if (changed) {
						deleteSavedProfile(key);
					}
				}
				refreshProfiles();
				showCharacterList();
			})
			.setNegativeButton("Atrás", (d, w) -> showCharacterList())
			.show();
	}

	private void deleteSavedProfile(String key) {
		File f = savedProfile(key);
		if (f.isFile() && !f.delete()) {
			Log.w(TAG, "Could not delete " + f);
		}
	}

	private void confirmResetProfiles() {
		final GameRosters.Roster roster = GameRosters.forKind(gameKind());
		new AlertDialog.Builder(this)
			.setTitle("¿Restablecer los personajes de " + gameShortName() + "?")
			.setMessage("Todos volverán al tipo de botones general y se borrarán las distribuciones guardadas por personaje de este juego.")
			.setPositiveButton("Restablecer", (d, w) -> {
				SharedPreferences.Editor e = prefs.edit();
				for (String key : roster.keys) {
					e.remove(K_CHAR_STYLE + key);
					deleteSavedProfile(key);
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
		String style = currentStyle();
		boolean arcade = STYLE_ARCADE.equals(style);
		boolean mobile = STYLE_MOBILE.equals(style);
		styleChip(stylePsp, !arcade && !mobile);
		styleChip(styleArcade, arcade);
		styleChip(styleMobile, mobile);
		mobileSizeRow.setVisibility(mobile ? View.VISIBLE : View.GONE);
		int size = mobileSize();
		for (int i = 0; i < MOBILE_SIZES.length; i++) {
			styleChip(mobileSizeChips[i], MOBILE_SIZES[i] == size);
		}
		int kind = gameKind();
		boolean bb = kind == GameInspector.KIND_BB;
		controlsPreview.setKind(kind);
		controlsPreview.setArcade(arcade);
		float[] screen = landscapeDp();
		boolean stick = stickMovement();
		styleChip(moveDpad, !stick);
		styleChip(moveStick, stick);
		moveDesc.setText(stick
			? "Joystick: arrástralo o toca el lado hacia donde quieres ir (dos toques rápidos hacia el rival = correr). Pulsa la cruceta del juego en 8 direcciones, así que sirve igual para los movimientos especiales (↓↘→)."
			: "Flechas: la cruceta de la PSP, con las diagonales.");
		controlsPreview.setStick(stick);
		controlsPreview.setMobile(mobile ? MobileLayout.build(kind, screen[0], screen[1], size, stick) : null, screen[1]);
		if (mobile) {
			String common = " Botones grandes y oscuros como en los juegos de pelea de móvil, con atajos que pulsan varios botones a la vez, y una cruceta grande a la izquierda.";
			switch (kind) {
				case GameInspector.KIND_BB:
					styleDesc.setText("A, B y C abajo y D (Drive) arriba; atajos RC (A+B+C) y Burst (A+B+C+D)." + common);
					break;
				case GameInspector.KIND_KOF:
					styleDesc.setText("A y C (puños) arriba, B y D (patadas) abajo; atajos Roll (A+B), CD y MAX (A+B+C)." + common);
					break;
				case GameInspector.KIND_SF:
					styleDesc.setText("LP, MP y HP arriba, LK, MK y HK abajo; atajo V-ISM (MP+MK)." + common
						+ " Pulsan □ = LP, △ = MP, R = HP, ✕ = LK, ○ = MK y L = HK: si uno no hace el golpe que dice, pon esa configuración en las opciones del juego.");
					break;
				default:
					styleDesc.setText("P, K y S abajo, HS y D arriba; atajos RC (P+K+S) y Burst (D+HS). L (Respeto) sigue arriba." + common);
					break;
			}
		} else if (kind == GameInspector.KIND_SF) {
			styleDesc.setText(arcade
				? "Como en la máquina de Capcom: LP, MP y HP arriba; LK, MK y HK abajo. La cruceta, el stick, Start y Select siguen ahí. Pulsan □ = LP, △ = MP, R = HP, ✕ = LK, ○ = MK y L = HK: si un botón no hace el golpe que dice, pon esa configuración en las opciones del juego."
				: "Como en la consola: △ ○ ✕ □, L y R, cruceta, stick, Start y Select. Los golpes son los que tenga el juego en su configuración de botones.");
			padNote.setText("Los controles funcionan solos: X = □, Y = △, A = ✕, B = ○, R1 = R y L1 = L. Qué golpe es cada uno lo decide la configuración de botones del juego.");
		} else if (kind == GameInspector.KIND_KOF) {
			styleDesc.setText(arcade
				? "Como en la máquina Neo Geo: A, B, C y D en arco. La cruceta, el stick, L, R, Start y Select siguen ahí."
				: "Como en la consola: △ ○ ✕ □, L y R, cruceta, stick, Start y Select. En KOF: □ = A, ✕ = B, △ = C, ○ = D.");
			padNote.setText("Los controles funcionan solos: X = A (□), A = B (✕), Y = C (△) y B = D (○). Puedes reasignarlos dentro del juego en sus opciones.");
		} else if (bb) {
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
		padNote.setText(padNoteText(kind));
	}

	/** How a Bluetooth / USB controller maps to each game. */
	private static String padNoteText(int kind) {
		switch (kind) {
			case GameInspector.KIND_BB:
				return "Los controles funcionan solos: X = A (□), Y = B (△), B = C (○) y A = D (✕). Puedes reasignarlos dentro del juego en sus opciones.";
			case GameInspector.KIND_KOF:
				return "Los controles funcionan solos: X = A (□), A = B (✕), Y = C (△) y B = D (○). Puedes reasignarlos dentro del juego en sus opciones.";
			case GameInspector.KIND_SF:
				return "Los controles funcionan solos: X = □, Y = △, A = ✕, B = ○, R1 = R y L1 = L. Qué golpe es cada uno lo decide la configuración de botones del juego.";
			default:
				return "Los controles funcionan solos: X = P (□), A = K (✕), Y = S (△), B = HS (○), R1 = D y L1 = Respeto. Puedes reasignarlos dentro del juego en Ajustes → Controles.";
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
		LinearLayout gameRow = new LinearLayout(this);
		gameRow.setOrientation(LinearLayout.HORIZONTAL);
		gameIcon = new IconView(this);
		GradientDrawable iconBg = new GradientDrawable();
		iconBg.setColor(0xFF101015);
		iconBg.setCornerRadius(dp(10));
		gameIcon.setBackground(iconBg);
		gameIcon.setClipToOutline(true);
		gameIcon.setVisibility(View.GONE);
		makeClickable(gameIcon);
		gameIcon.setOnClickListener(v -> showCatalog());
		LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(104), ViewGroup.LayoutParams.WRAP_CONTENT);
		iconLp.rightMargin = dp(14);
		iconLp.topMargin = dp(2);
		gameRow.addView(gameIcon, iconLp);
		LinearLayout gameTexts = new LinearLayout(this);
		gameTexts.setOrientation(LinearLayout.VERTICAL);
		gameStatus = text("", 17, C_TEXT, true);
		gameTexts.addView(gameStatus);
		gameDetail = text("", 13, C_MUTED, false);
		gameDetail.setPadding(0, dp(4), 0, 0);
		gameTexts.addView(gameDetail);
		gameRow.addView(gameTexts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		game.addView(gameRow);
		catalogButton = text("CATÁLOGO", 15, C_TEXT, true);
		catalogButton.setGravity(Gravity.CENTER);
		catalogButton.setLetterSpacing(0.08f);
		catalogButton.setPadding(dp(16), dp(13), dp(16), dp(13));
		catalogButton.setBackground(ripple(0x22D7263D, C_ACCENT, 14));
		makeClickable(catalogButton);
		catalogButton.setOnClickListener(v -> showCatalog());
		LinearLayout.LayoutParams catLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		catLp.topMargin = dp(14);
		game.addView(catalogButton, catLp);
		TextView pick = outlineButton("Añadir o cambiar de juego (ISO / CSO)");
		pick.setOnClickListener(v -> pickGame());
		LinearLayout.LayoutParams pickLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		pickLp.topMargin = dp(8);
		game.addView(pick, pickLp);

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
		styleMobile = chip("Móvil");
		styleMobile.setOnClickListener(v -> {
			prefs.edit().putString(K_STYLE, STYLE_MOBILE).apply();
			refreshStyle();
		});
		styleRow.addView(stylePsp, weighted(0, dp(4)));
		styleRow.addView(styleArcade, weighted(dp(4), dp(4)));
		styleRow.addView(styleMobile, weighted(dp(4), 0));
		controls.addView(styleRow);
		mobileSizeRow = new LinearLayout(this);
		mobileSizeRow.setOrientation(LinearLayout.VERTICAL);
		mobileSizeRow.setPadding(0, 0, 0, dp(10));
		mobileSizeRow.addView(text("Tamaño de los botones", 15, C_TEXT, true));
		LinearLayout sizeChips = new LinearLayout(this);
		sizeChips.setOrientation(LinearLayout.HORIZONTAL);
		sizeChips.setPadding(0, dp(10), 0, 0);
		for (int i = 0; i < MOBILE_SIZES.length; i++) {
			final int size = MOBILE_SIZES[i];
			mobileSizeChips[i] = chip(MOBILE_SIZE_LABELS[i]);
			mobileSizeChips[i].setOnClickListener(v -> {
				prefs.edit().putInt(K_MOBILE_SIZE, size).apply();
				refreshStyle();
			});
			sizeChips.addView(mobileSizeChips[i], weighted(i == 0 ? 0 : dp(4), i == MOBILE_SIZES.length - 1 ? 0 : dp(4)));
		}
		mobileSizeRow.addView(sizeChips);
		controls.addView(mobileSizeRow);

		controls.addView(text("Movimiento", 15, C_TEXT, true));
		LinearLayout moveRow = new LinearLayout(this);
		moveRow.setOrientation(LinearLayout.HORIZONTAL);
		moveRow.setPadding(0, dp(10), 0, dp(6));
		moveDpad = chip("Flechas");
		moveDpad.setOnClickListener(v -> {
			prefs.edit().putString(K_MOVE, MOVE_DPAD).apply();
			refreshStyle();
		});
		moveStick = chip("Joystick");
		moveStick.setOnClickListener(v -> {
			prefs.edit().putString(K_MOVE, MOVE_STICK).apply();
			refreshStyle();
		});
		moveRow.addView(moveDpad, weighted(0, dp(4)));
		moveRow.addView(moveStick, weighted(dp(4), 0));
		controls.addView(moveRow);
		moveDesc = text("", 13, C_MUTED, false);
		moveDesc.setPadding(0, 0, 0, dp(12));
		controls.addView(moveDesc);

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
		resetMoves.setOnClickListener(v -> confirmResetGestureMoves(isBlazBlue() ? GESTURE_BB : isKof() ? GESTURE_KOF : isStreetFighter() ? GESTURE_SF : gestureChar));
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
		perCharacterButton = perCharacter;
		profilesOptions.addView(perCharacter, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
		TextView reset = outlineButton("Restablecer todos");
		reset.setOnClickListener(v -> confirmResetProfiles());
		LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		resetParams.topMargin = dp(8);
		profilesOptions.addView(reset, resetParams);
		gameReportButton = outlineButton("Enviar informe del juego");
		gameReportButton.setOnClickListener(v -> shareGameReport());
		LinearLayout.LayoutParams reportParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		reportParams.topMargin = dp(8);
		profilesOptions.addView(gameReportButton, reportParams);
		TextView profileNote = text("Al empezar el combate verás «Controles: nombre». Funciona con Guilty Gear XX ΛC+ (versión americana, combates individuales), BlazBlue (experimental) y KOF '96, '97 y '98. Cada juego tiene su propia lista de personajes. En Street Fighter Alpha 3 todavía no.", 13, C_MUTED, false);
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
			"Barras de vida, el medidor de poder (tensión, Heat, POW o súper) y contador de combos con su daño, leídos de la memoria del juego y dibujados encima. Guilty Gear, BlazBlue, KOF y Street Fighter Alpha 3.");
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
			"EASU escala la imagen del juego, RCAS le devuelve la nitidez y AMD CAS saca más detalle. Solo trabaja sobre la imagen final.");
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

		fsrSpritesSwitch = switchRow(fsrOptions, "Modo sprites (xBR + FSR)",
			"Para juegos 2D como estos: el juego se dibuja a 480 × 272, xBR redondea los bordes de los sprites al doble y FSR lo lleva a tu pantalla. Bordes limpios, sin dientes de sierra, y gasta menos que subir la resolución interna.");
		fsrSpritesSwitch.setOnCheckedChangeListener((b, checked) -> {
			if (checked == fsrSprites()) {
				return;
			}
			prefs.edit().putBoolean(K_FSR_SPRITES + fsrGameKey(), checked).apply();
			refreshFsr();
			refreshResolution();
		});
		TextView scaleLabel = text("Factor de escala", 15, C_TEXT, true);
		scaleLabel.setPadding(0, dp(16), 0, 0);
		fsrOptions.addView(scaleLabel);
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

		fsrDetailValue = text("", 15, C_ACCENT, true);
		fsrDetailSeek = fsrSlider(fsrOptions, "Detalle extra (AMD CAS)", fsrDetailValue, K_FSR_DETAIL);
		fsrOptions.addView(text("Un segundo afinado de AMD que se adapta al contraste: más detalle sin halos. 40–70 % se ve bien; 0 % lo apaga.", 12, C_MUTED, false));
		fsrColorValue = text("", 15, C_ACCENT, true);
		fsrColorSeek = fsrSlider(fsrOptions, "Color más vivo", fsrColorValue, K_FSR_COLOR);
		fsrOptions.addView(text("Más saturación donde el color es débil y un poco más de contraste. 0 % deja los colores del juego.", 12, C_MUTED, false));
		TextView fsrBest = outlineButton("Poner calidad máxima");
		fsrBest.setOnClickListener(v -> {
			// The smallest factor that takes xBR's 960 x 544 to the full size of the game on screen.
			int[] screen = gameSizeOnScreen();
			float room = Math.min(screen[0] / (PSP_W * 2f), screen[1] / (PSP_H * 2f));
			int best = FSR_SCALES[FSR_SCALES.length - 1];
			for (int value : FSR_SCALES) {
				if (value / 10f >= room * 0.97f) {
					best = value;
					break;
				}
			}
			String k = fsrGameKey();
			prefs.edit().putBoolean(K_FSR_ON + k, true).putBoolean(K_FSR_SPRITES + k, true).putInt(K_FSR_SCALE + k, best).putInt(K_FSR_SHARP + k, 100)
				.putInt(K_FSR_DETAIL + k, 70).putInt(K_FSR_COLOR + k, 40).apply();
			refreshFsr();
			refreshEffect();
			refreshResolution();
		});
		LinearLayout.LayoutParams bestParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		bestParams.topMargin = dp(14);
		fsrOptions.addView(fsrBest, bestParams);

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

	/** A 0-100 % slider of the AMD panel, saved per game under key + game. */
	private SeekBar fsrSlider(LinearLayout parent, String title, TextView value, final String key) {
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setPadding(0, dp(16), 0, 0);
		row.addView(text(title, 15, C_TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
		row.addView(value);
		parent.addView(row);
		SeekBar seek = new SeekBar(this);
		seek.setMax(100);
		if (Build.VERSION.SDK_INT >= 21) {
			seek.setProgressTintList(ColorStateList.valueOf(C_ACCENT));
			seek.setThumbTintList(ColorStateList.valueOf(C_ACCENT));
			seek.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF4A4A55));
		}
		seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
				if (!fromUser) {
					return;
				}
				prefs.edit().putInt(key + fsrGameKey(), progress).apply();
				value.setText(progress == 0 ? "apagado" : sharpLabel(progress));
				refreshAmdButton();
			}

			@Override
			public void onStartTrackingTouch(SeekBar bar) {}

			@Override
			public void onStopTrackingTouch(SeekBar bar) {
				refreshFsr();
			}
		});
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		lp.topMargin = dp(6);
		parent.addView(seek, lp);
		return seek;
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
