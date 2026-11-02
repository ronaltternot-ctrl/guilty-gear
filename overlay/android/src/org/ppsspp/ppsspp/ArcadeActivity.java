package org.ppsspp.ppsspp;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
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

import com.swordfish.libretrodroid.GLRetroShader;
import com.swordfish.libretrodroid.GLRetroView;
import com.swordfish.libretrodroid.GLRetroViewData;
import com.swordfish.libretrodroid.LibretroDroid;
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
import java.util.Map;

import kotlin.Unit;
import kotlinx.coroutines.GlobalScope;
import kotlinx.coroutines.flow.FlowKt;

/**
 * Games that run on a libretro core through LibretroDroid: arcade / Neo Geo with FBNeo and
 * PlayStation 1 with PCSX ReARMed (Lemuroid's builds, in the APK as lib*_libretro_android.so).
 * Runs in its own process (":arcade"), which ends with the game, so each game starts with a
 * clean core.
 */
public class ArcadeActivity extends ComponentActivity implements TouchPadView.Listener {
	private static final String TAG = "ArcadeActivity";

	static final String EXTRA_ROM = "rom";
	static final String EXTRA_TITLE = "title";
	/** ArcadeLibrary.SYSTEM (default), PsxLibrary.SYSTEM, DreamcastLibrary.SYSTEM or N64Library.SYSTEM. */
	static final String EXTRA_SYSTEM = "system";

	private static final String CORE = "libfbneo_libretro_android.so";
	private static final String CORE_PSX = "libpcsx_rearmed_libretro_android.so";
	/** Flycast, built by scripts/build_flycast.sh. */
	private static final String CORE_DC = "libflycast_libretro_android.so";

	// Dreamcast pad (Flycast): Y X B A are the RetroPad's X Y A B, the triggers L2 / R2.
	private static final int DC_A = KeyEvent.KEYCODE_BUTTON_B, DC_B = KeyEvent.KEYCODE_BUTTON_A;
	private static final int DC_X = KeyEvent.KEYCODE_BUTTON_Y, DC_Y = KeyEvent.KEYCODE_BUTTON_X;
	private static final int C_DC_A = 0xFFFF4F4F, C_DC_B = 0xFF4F8DFF, C_DC_X = 0xFFFFD23F, C_DC_Y = 0xFF3FD27A;
	/** Flycast's internal resolutions (the Dreamcast draws at 640 x 480) and xBRZ texture factors. */
	static final String[] DC_RES = { "640x480", "1280x960", "1920x1440" };
	static final String[] DC_RES_NAMES = { "640 × 480 (original)", "1280 × 960 (×2)", "1920 × 1440 (×3)" };
	static final String[] DC_TEX = { "1", "2", "4" };
	static final String[] DC_TEX_NAMES = { "originales", "xBRZ ×2", "xBRZ ×4" };

	/** Mupen64Plus-Next with GLideN64 on OpenGL ES 3 (Lemuroid's build). */
	private static final String CORE_N64 = "libmupen64plus_next_gles3_libretro_android.so";
	// Nintendo 64 pad on the core's standard RetroPad: A = B, B = Y, Z = L2, L = L, R = R,
	// the stick is the left analog stick and the C buttons the right one.
	private static final int N64_A = KeyEvent.KEYCODE_BUTTON_B, N64_B = KeyEvent.KEYCODE_BUTTON_Y;
	private static final int N64_Z = KeyEvent.KEYCODE_BUTTON_L2, N64_L = KeyEvent.KEYCODE_BUTTON_L1, N64_R = KeyEvent.KEYCODE_BUTTON_R1;
	/** The C buttons, kept here and sent as the right stick (not Android keys). */
	private static final int N64_C_UP = -101, N64_C_LEFT = -102, N64_C_RIGHT = -103, N64_C_DOWN = -104;
	private static final int C_N64_A = 0xFF4F7BFF, C_N64_B = 0xFF3FD27A, C_N64_C = 0xFFFFD23F, C_N64_START = 0xFFFF5A5A;
	/** GLideN64's output size (4:3 and, with the wide screen, 16:9): the N64 draws at 320 x 240. */
	static final String[] N64_RES = { "320x240", "640x480", "960x720", "1280x960" };
	static final String[] N64_RES_WIDE = { "640x360", "960x540", "1280x720", "1920x1080" };
	static final String[] N64_RES_NAMES = { "320 × 240 (original)", "640 × 480 (×2)", "960 × 720 (×3)", "1280 × 960 (×4)" };
	/** GLideN64's texture enhancement: xBRZ, like the Dreamcast's. */
	static final String[] N64_TEX = { "None", "2xBRZ", "4xBRZ" };

	/** Snes9x 1.63 (Lemuroid's build). The RetroPad is the SNES pad: A, B, X, Y are its own. */
	private static final String CORE_SNES = "libsnes9x_libretro_android.so";
	private static final int SNES_A = KeyEvent.KEYCODE_BUTTON_A, SNES_B = KeyEvent.KEYCODE_BUTTON_B;
	private static final int SNES_X = KeyEvent.KEYCODE_BUTTON_X, SNES_Y = KeyEvent.KEYCODE_BUTTON_Y;
	// The Super Famicom's colors.
	private static final int C_SNES_A = 0xFFE8433F, C_SNES_B = 0xFFF2C230, C_SNES_X = 0xFF4A7FE8, C_SNES_Y = 0xFF3BB56A;

	// PlayStation: the RetroPad is the PlayStation pad (✕ = B, ○ = A, □ = Y, △ = X).
	private static final int PS_CROSS = KeyEvent.KEYCODE_BUTTON_B, PS_CIRCLE = KeyEvent.KEYCODE_BUTTON_A;
	private static final int PS_SQUARE = KeyEvent.KEYCODE_BUTTON_Y, PS_TRIANGLE = KeyEvent.KEYCODE_BUTTON_X;
	private static final int C_TRIANGLE = 0xFF3FD69B, C_CIRCLE = 0xFFFF5A64, C_CROSS = 0xFF6FA8FF, C_SQUARE = 0xFFF27FD0;

	// FBNeo's "classic" RetroPad for the Neo Geo: A, B, C, D are the RetroPad's B, A, Y, X.
	// LibretroDroid turns Android's BUTTON_A..Y into the RetroPad's A..Y.
	private static final int KEY_A = KeyEvent.KEYCODE_BUTTON_B;
	private static final int KEY_B = KeyEvent.KEYCODE_BUTTON_A;
	private static final int KEY_C = KeyEvent.KEYCODE_BUTTON_Y;
	private static final int KEY_D = KeyEvent.KEYCODE_BUTTON_X;
	private static final int KEY_COIN = KeyEvent.KEYCODE_BUTTON_SELECT;
	private static final int KEY_START = KeyEvent.KEYCODE_BUTTON_START;

	private static final int C_A = 0xFFFF5A4E, C_B = 0xFFFFB547, C_C = 0xFF4CD07D, C_D = 0xFF4DA3FF, C_SHORT = 0xFFB9B9C6;

	// Capcom's six-button games (FBNeo's Street Fighter layout on the classic RetroPad):
	// punches Y, X, L and kicks B, A, R; all three punches L2, all three kicks R2.
	private static final int KEY_LP = KeyEvent.KEYCODE_BUTTON_Y, KEY_MP = KeyEvent.KEYCODE_BUTTON_X, KEY_HP = KeyEvent.KEYCODE_BUTTON_L1;
	private static final int KEY_LK = KeyEvent.KEYCODE_BUTTON_B, KEY_MK = KeyEvent.KEYCODE_BUTTON_A, KEY_HK = KeyEvent.KEYCODE_BUTTON_R1;
	private static final int KEY_3P = KeyEvent.KEYCODE_BUTTON_L2, KEY_3K = KeyEvent.KEYCODE_BUTTON_R2;
	private static final int C_PUNCH = 0xFFFF6B5A, C_KICK = 0xFF55A8FF;


	private final Handler handler = new Handler(Looper.getMainLooper());
	private SharedPreferences prefs;
	private GLRetroView retro;
	private TouchPadView pad;
	private TextView status;
	private File rom, saves, states;
	private String set, title;
	private boolean psx;
	/** Dreamcast (Flycast). */
	private boolean dc;
	/** Nintendo 64 (Mupen64Plus-Next). */
	private boolean n64;
	/** Super Nintendo (Snes9x). */
	private boolean snes;
	/** The custom HUD, for the SNES fighting games SnesHud knows (null otherwise). */
	private RetroHudView hud;
	private SnesHud.Game hudGame;
	private boolean cUp, cLeft, cRight, cDown;
	/** ArcadeLibrary.SYSTEM, PsxLibrary.SYSTEM or DreamcastLibrary.SYSTEM: also the prefs and folder name. */
	private String system;
	/** An arcade set of SNK's Neo Geo (four buttons, MVS / AES). */
	private boolean neoGeo;
	private boolean started, failed, menuOpen, paused;
	/** The player's songs instead of the game's music (null: the game keeps its own). */
	private MusicPlayer music;
	/** The game's own sound stays off: arcade boards mix music and effects, or the player asked. */
	private boolean muteGame;
	private boolean effectApplied;
	/** The touch controls went away because a controller is being used (they come back on a touch). */
	private boolean padAutoHidden;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		String sys = getIntent().getStringExtra(EXTRA_SYSTEM);
		psx = PsxLibrary.SYSTEM.equals(sys);
		dc = DreamcastLibrary.SYSTEM.equals(sys);
		n64 = N64Library.SYSTEM.equals(sys);
		snes = SnesLibrary.SYSTEM.equals(sys);
		system = psx ? PsxLibrary.SYSTEM : dc ? DreamcastLibrary.SYSTEM : n64 ? N64Library.SYSTEM
			: snes ? SnesLibrary.SYSTEM : ArcadeLibrary.SYSTEM;
		prefs = getSharedPreferences(system, MODE_PRIVATE);

		FrameLayout root = new FrameLayout(this);
		root.setBackgroundColor(Color.BLACK);
		setContentView(root);
		// After setContentView: before it the window has no decor view yet, and on Android 11+
		// getInsetsController() then fails (that closed every arcade and PS1 game at once).
		goFullscreen();

		String path = getIntent().getStringExtra(EXTRA_ROM);
		title = getIntent().getStringExtra(EXTRA_TITLE);
		if (path == null || !new File(path).isFile()) {
			fatal("No encuentro el juego", "El archivo ya no está en la app. Vuelve a añadirlo en el catálogo.");
			return;
		}
		rom = new File(path);
		// The name saves and states go by: the ROM set, or the PS1 game's folder.
		set = psx || dc || n64 || snes ? rom.getParentFile().getName().replaceAll("[^A-Za-z0-9._()\\-]", "_") : ArcadeLibrary.setName(rom.getName());
		if (title == null || title.isEmpty()) {
			title = psx ? PsxLibrary.title(rom.getName()) : dc ? DreamcastLibrary.title(rom.getName())
				: n64 ? N64Library.title(rom.getName()) : snes ? SnesLibrary.title(rom.getName()) : ArcadeCheck.title(this, set);
		}
		GameRun.begin(this, ":arcade", title, rom.getName() + " (" + (rom.length() / 1048576) + " MB)");
		neoGeo = !psx && !dc && !n64 && !snes && (ArcadeCheck.info(this, set) != null || ArcadeLibrary.looksNeoGeo(set));
		String musicSystem = psx ? MusicPlayer.PSX : dc ? MusicPlayer.DC : n64 ? MusicPlayer.N64
			: snes ? MusicPlayer.SNES : MusicPlayer.ARCADE;
		music = MusicPlayer.create(this, musicSystem);
		muteGame = music != null && MusicPlayer.mutesAllSound(this, musicSystem);
		File base = new File(getFilesDir(), system);
		File system = new File(base, "system");
		saves = new File(base, "saves");
		states = new File(base, "states");
		for (File d : new File[] { system, saves, states }) {
			if (!d.isDirectory() && !d.mkdirs()) {
				Log.w(TAG, "Could not create " + d);
			}
		}

		String coreName = psx ? CORE_PSX : dc ? CORE_DC : n64 ? CORE_N64 : snes ? CORE_SNES : CORE;
		File coreFile = new File(getApplicationInfo().nativeLibraryDir, coreName);
		GLRetroViewData data = new GLRetroViewData(this);
		data.setCoreFilePath(coreFile.isFile() ? coreFile.getAbsolutePath() : coreName);
		data.setGameFilePath(rom.getAbsolutePath());
		data.setSystemDirectory(system.getAbsolutePath());
		data.setSavesDirectory(saves.getAbsolutePath());
		data.setVariables(variables());
		// LibretroDroid's own shader to start with; the PSP's effects go in at onStart (applyEffect).
		ShaderConfig builtin = RetroEffects.builtin(prefs.getString("shader", "sharp"));
		data.setShader(builtin != null ? builtin : ShaderConfig.Sharp.INSTANCE);
		data.setPreferLowLatencyAudio(true);
		data.setRumbleEventsEnabled(false);
		byte[] sram = readFile(sramFile());
		if (sram != null && sram.length > 0) {
			data.setSaveRAMState(sram);
		}

		retro = new GLRetroView(this, data);
		getLifecycle().addObserver(retro);
		GameRun.step(this, "emulador creado");
		root.addView(retro, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));

		if (snes) {
			// The custom HUD (between the game and the touch controls), for the fighting games it knows.
			hudGame = SnesHud.identify(rom, title);
			if (hudGame != null) {
				hud = new RetroHudView(this, hudGame, snesAspect(),
					GameTheme.font(getAssets(), "DejaVuSansCondensed-Bold.ttf", "sans-serif-condensed", Typeface.BOLD));
				hud.setDesign(prefs.getInt("hud_design", RetroHud.DESIGN_NEON));
				root.addView(hud, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
			}
		}

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
			} else if (event instanceof GLRetroView.GLRetroEvents.SurfaceCreated) {
				handler.post(() -> GameRun.step(this, "juego cargado, esperando el primer cuadro"));
			}
			return Unit.INSTANCE;
		}), GlobalScope.INSTANCE);
	}

	private int n64Res() {
		return Math.max(0, java.util.Arrays.asList(N64_RES).indexOf(prefs.getString("n64_res", N64_RES[1])));
	}

	/** 4:3 like a TV, or 8:7: the SNES's 256 x 224 pixels, square. */
	private float snesAspect() {
		return "uncorrected".equals(prefs.getString("snes_aspect", "4:3")) ? 256f / 224f : 4f / 3f;
	}

	private Variable[] variables() {
		if (snes) {
			return new Variable[] {
				new Variable("snes9x_aspect", prefs.getString("snes_aspect", "4:3"), null),
				// The graphic improvements of a 2D console: less sprite flicker, the hi-res modes'
				// two pictures blended (the image effects do the rest).
				new Variable("snes9x_reduce_sprite_flicker", prefs.getBoolean("snes_flicker", true) ? "enabled" : "disabled", null),
				new Variable("snes9x_hires_blend", prefs.getBoolean("snes_blend", false) ? "merge" : "disabled", null),
			};
		}
		if (n64) {
			int res = n64Res();
			return new Variable[] {
				new Variable("mupen64plus-rdp-plugin", "gliden64", null),
				new Variable("mupen64plus-rsp-plugin", "hle", null),
				new Variable("mupen64plus-cpucore", "dynamic_recompiler", null),
				// The resolution: GLideN64 draws the game this big (4:3, or 16:9 with the wide screen).
				new Variable("mupen64plus-EnableNativeResFactor", "0", null),
				new Variable("mupen64plus-43screensize", N64_RES[res], null),
				new Variable("mupen64plus-169screensize", N64_RES_WIDE[res], null),
				new Variable("mupen64plus-aspect", prefs.getBoolean("n64_wide", false) ? "16:9 adjusted" : "4:3", null),
				// xBRZ on the game's textures: the N64's own "texture changes".
				new Variable("mupen64plus-txEnhancementMode", prefs.getString("n64_tex", N64_TEX[0]), null),
				new Variable("mupen64plus-txFilterMode", "None", null),
				new Variable("mupen64plus-FrameDuping", "True", null),
				// Standard controls: C buttons on the right stick (a Bluetooth controller's too).
				new Variable("mupen64plus-alt-map", "False", null),
				new Variable("mupen64plus-pak1", "memory", null),
			};
		}
		if (dc) {
			return new Variable[] {
				// Without dc_boot.bin in system/dc, Flycast boots with its own BIOS (HLE) by itself.
				new Variable("reicast_internal_resolution", prefs.getString("dc_res", DC_RES[0]), null),
				// xBRZ on the game's textures: the Dreamcast's own "texture changes".
				new Variable("reicast_texupscale", prefs.getString("dc_tex", DC_TEX[0]), null),
				new Variable("reicast_widescreen_hack", prefs.getBoolean("dc_wide", false) ? "enabled" : "disabled", null),
				new Variable("reicast_threaded_rendering", "enabled", null),
			};
		}
		if (psx) {
			return new Variable[] {
				// The real BIOS when the player added one (system folder), else the built-in one.
				new Variable("pcsx_rearmed_bios", "auto", null),
				new Variable("pcsx_rearmed_drc", "enabled", null),
				new Variable("pcsx_rearmed_neon_enhancement_enable", prefs.getBoolean("hires", false) ? "enabled" : "disabled", null),
				// The player's music instead of the game's: no CD audio, no XA (where PS1 games keep their music).
				new Variable("pcsx_rearmed_nocdaudio", music != null ? "disabled" : "enabled", null),
				new Variable("pcsx_rearmed_noxadecoding", music != null ? "disabled" : "enabled", null),
			};
		}
		boolean aes = "aes".equals(prefs.getString("neogeo_mode", "mvs"));
		return new Variable[] {
			// MVS (arcade, with coins) or AES (the home console: no coins, saves on the memory card).
			new Variable("fbneo-neogeo-mode", aes ? "AES_EUR" : "DIPSWITCH", null),
			new Variable("fbneo-memcard-mode", aes ? "per-game" : "disabled", null),
			// Holding Start must not open the cabinet's service menu.
			new Variable("fbneo-diagnostic-input", "None", null),
		};
	}

	/**
	 * The visual effect (RetroEffects): LibretroDroid's own shader, or one of the PSP's effects as a
	 * custom chain of passes. Called once the core exists, or while the game is paused.
	 */
	private void applyEffect(String id) {
		if (retro == null) {
			return;
		}
		ShaderConfig builtin = RetroEffects.builtin(id);
		if (builtin != null) {
			retro.setShader(builtin);
			return;
		}
		Map<String, String> chain = null;
		if (!RetroEffects.needsGles3(id) || glesVersion() >= 0x30000) {
			android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
			int w = Math.max(dm.widthPixels, dm.heightPixels);
			int h = Math.min(dm.widthPixels, dm.heightPixels);
			// The Dreamcast's frame is 640 x 480 (or more), twice the size the scale is worked out for.
			// The N64's frame is 320 x 240 times the resolution chosen.
			int k = dc ? 2 : n64 ? n64Res() + 1 : 1;
			chain = RetroEffects.chain(this, id, RetroEffects.fsrScale(w / k, h / k));
		}
		if (chain == null) {
			retro.setShader(ShaderConfig.Sharp.INSTANCE);
			return;
		}
		try {
			// LibretroDroid rebuilt with patches/libretrodroid/0001: if the GPU can't compile the
			// chain, it falls back to the sharp shader by itself.
			LibretroDroid.setShaderConfig(new GLRetroShader(RetroEffects.SHADER_CUSTOM, chain));
		} catch (Throwable t) {
			Log.w(TAG, "Effect " + id, t);
			retro.setShader(ShaderConfig.Sharp.INSTANCE);
		}
	}

	private int glesVersion() {
		android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
		return am == null ? 0 : am.getDeviceConfigurationInfo().reqGlEsVersion;
	}

	private void applyPadSettings() {
		if (snes) {
			pad.setDiamond(
				new TouchPadView.Spec("X", C_SNES_X, SNES_X),
				new TouchPadView.Spec("Y", C_SNES_Y, SNES_Y),
				new TouchPadView.Spec("A", C_SNES_A, SNES_A),
				new TouchPadView.Spec("B", C_SNES_B, SNES_B),
				new TouchPadView.Spec[] { new TouchPadView.Spec("L", C_SHORT, KeyEvent.KEYCODE_BUTTON_L1) },
				new TouchPadView.Spec[] { new TouchPadView.Spec("R", C_SHORT, KeyEvent.KEYCODE_BUTTON_R1) },
				new TouchPadView.Spec[] {
					new TouchPadView.Spec("SELECT", 0xFFE8E8EE, KEY_COIN),
					new TouchPadView.Spec("START", 0xFFE8E8EE, KEY_START),
				});
			pad.setStick("stick".equals(prefs.getString("move", "dpad")));
			pad.setSizePercent(prefs.getInt("size", 100));
			pad.setOpacity(prefs.getFloat("opacity", 0.85f));
			pad.setVisibility(prefs.getBoolean("hide_pad", false) ? View.GONE : View.VISIBLE);
			return;
		}
		if (n64) {
			pad.setN64(
				new TouchPadView.Spec("A", C_N64_A, N64_A),
				new TouchPadView.Spec("B", C_N64_B, N64_B),
				new TouchPadView.Spec[] {
					new TouchPadView.Spec("▲", C_N64_C, N64_C_UP),
					new TouchPadView.Spec("◀", C_N64_C, N64_C_LEFT),
					new TouchPadView.Spec("▶", C_N64_C, N64_C_RIGHT),
					new TouchPadView.Spec("▼", C_N64_C, N64_C_DOWN),
				},
				new TouchPadView.Spec[] { new TouchPadView.Spec("Z", C_SHORT, N64_Z), new TouchPadView.Spec("L", C_SHORT, N64_L) },
				new TouchPadView.Spec[] { new TouchPadView.Spec("R", C_SHORT, N64_R) },
				new TouchPadView.Spec[] { new TouchPadView.Spec("START", C_N64_START, KEY_START) });
			pad.setAnalog(true);
			pad.setSizePercent(prefs.getInt("size", 100));
			pad.setOpacity(prefs.getFloat("opacity", 0.85f));
			pad.setVisibility(prefs.getBoolean("hide_pad", false) ? View.GONE : View.VISIBLE);
			return;
		}
		if (dc) {
			pad.setDiamond(
				new TouchPadView.Spec("Y", C_DC_Y, DC_Y),
				new TouchPadView.Spec("X", C_DC_X, DC_X),
				new TouchPadView.Spec("B", C_DC_B, DC_B),
				new TouchPadView.Spec("A", C_DC_A, DC_A),
				new TouchPadView.Spec[] { new TouchPadView.Spec("L", C_SHORT, KeyEvent.KEYCODE_BUTTON_L2) },
				new TouchPadView.Spec[] { new TouchPadView.Spec("R", C_SHORT, KeyEvent.KEYCODE_BUTTON_R2) },
				new TouchPadView.Spec[] { new TouchPadView.Spec("START", 0xFFE8E8EE, KEY_START) });
			pad.setStick("stick".equals(prefs.getString("move", "dpad")));
			pad.setSizePercent(prefs.getInt("size", 100));
			pad.setOpacity(prefs.getFloat("opacity", 0.85f));
			pad.setVisibility(prefs.getBoolean("hide_pad", false) ? View.GONE : View.VISIBLE);
			return;
		}
		if (psx) {
			pad.setDiamond(
				new TouchPadView.Spec("△", C_TRIANGLE, PS_TRIANGLE),
				new TouchPadView.Spec("□", C_SQUARE, PS_SQUARE),
				new TouchPadView.Spec("○", C_CIRCLE, PS_CIRCLE),
				new TouchPadView.Spec("✕", C_CROSS, PS_CROSS),
				new TouchPadView.Spec[] {
					new TouchPadView.Spec("L1", C_SHORT, KeyEvent.KEYCODE_BUTTON_L1),
					new TouchPadView.Spec("L2", C_SHORT, KeyEvent.KEYCODE_BUTTON_L2),
				},
				new TouchPadView.Spec[] {
					new TouchPadView.Spec("R1", C_SHORT, KeyEvent.KEYCODE_BUTTON_R1),
					new TouchPadView.Spec("R2", C_SHORT, KeyEvent.KEYCODE_BUTTON_R2),
				},
				new TouchPadView.Spec[] {
					new TouchPadView.Spec("SELECT", 0xFFE8E8EE, KEY_COIN),
					new TouchPadView.Spec("START", 0xFFE8E8EE, KEY_START),
				});
			pad.setStick("stick".equals(prefs.getString("move", "dpad")));
			pad.setSizePercent(prefs.getInt("size", 100));
			pad.setOpacity(prefs.getFloat("opacity", 0.85f));
			pad.setVisibility(prefs.getBoolean("hide_pad", false) ? View.GONE : View.VISIBLE);
			return;
		}
		TouchPadView.Spec[] system = {
			new TouchPadView.Spec("COIN", 0xFFE8E8EE, KEY_COIN),
			new TouchPadView.Spec("START", 0xFFE8E8EE, KEY_START),
		};
		if (sixButtons()) {
			pad.setButtons(
				new TouchPadView.Spec[] { new TouchPadView.Spec("LP", C_PUNCH, KEY_LP), new TouchPadView.Spec("MP", C_PUNCH, KEY_MP), new TouchPadView.Spec("HP", C_PUNCH, KEY_HP) },
				new TouchPadView.Spec[] { new TouchPadView.Spec("LK", C_KICK, KEY_LK), new TouchPadView.Spec("MK", C_KICK, KEY_MK), new TouchPadView.Spec("HK", C_KICK, KEY_HK) },
				new TouchPadView.Spec[] {
					new TouchPadView.Spec("3P", C_SHORT, KEY_3P),
					new TouchPadView.Spec("3K", C_SHORT, KEY_3K),
					new TouchPadView.Spec("LP+LK", C_SHORT, KEY_LP, KEY_LK),
					new TouchPadView.Spec("MP+MK", C_SHORT, KEY_MP, KEY_MK),
				},
				system);
		} else {
			pad.setButtons(
				new TouchPadView.Spec[] { new TouchPadView.Spec("A", C_A, KEY_A), new TouchPadView.Spec("C", C_C, KEY_C) },
				new TouchPadView.Spec[] { new TouchPadView.Spec("B", C_B, KEY_B), new TouchPadView.Spec("D", C_D, KEY_D) },
				new TouchPadView.Spec[] {
					new TouchPadView.Spec("AB", C_SHORT, KEY_A, KEY_B),
					new TouchPadView.Spec("CD", C_SHORT, KEY_C, KEY_D),
					new TouchPadView.Spec("BC", C_SHORT, KEY_B, KEY_C),
					new TouchPadView.Spec("ABC", C_SHORT, KEY_A, KEY_B, KEY_C),
				},
				system);
		}
		pad.setStick("stick".equals(prefs.getString("move", "dpad")));
		pad.setSizePercent(prefs.getInt("size", 100));
		pad.setOpacity(prefs.getFloat("opacity", 0.85f));
		pad.setVisibility(prefs.getBoolean("hide_pad", false) ? View.GONE : View.VISIBLE);
	}

	/** Six buttons (Capcom) or four (Neo Geo and the rest), per game; Capcom's fighting games start with six. */
	private boolean sixButtons() {
		return !psx && !dc && !n64 && !snes && !neoGeo && "six".equals(prefs.getString("buttons_" + set, ArcadeLibrary.looksSixButton(set) ? "six" : "four"));
	}

	private void onFirstFrame() {
		if (started || failed) {
			return;
		}
		started = true;
		status.setVisibility(View.GONE);
		GameRun.step(this, GameRun.STEP_PLAYING);
		if (hud != null) {
			hud.start();
		}
		applyGameAudio();
		if (music != null) {
			music.setRunning(!menuOpen);
		}
	}

	/** The game's own sound: on, or off while paused or replaced by the player's music. */
	private void applyGameAudio() {
		if (retro != null) {
			retro.setAudioEnabled(!paused && !muteGame);
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		// The core is created now (the view's ON_CREATE), and it starts with its sound on.
		applyGameAudio();
		if (!effectApplied) {
			effectApplied = true;
			applyEffect(prefs.getString("shader", "sharp"));
		}
	}

	private void onRetroError(int code) {
		if (isFinishing() || isDestroyed()) {
			// While closing: no dialog over a screen that is going away.
			return;
		}
		if (code == GLRetroView.ERROR_SERIALIZATION) {
			Toast.makeText(this, "No se pudo cargar el estado guardado", Toast.LENGTH_LONG).show();
			return;
		}
		if (failed) {
			return;
		}
		failed = true;
		GameRun.step(this, "error " + code + " del emulador");
		String msg;
		if (code == GLRetroView.ERROR_LOAD_LIBRARY) {
			msg = "No se pudo cargar el emulador " + (psx ? "de PS1 (PCSX ReARMed)" : dc ? "de Dreamcast (Flycast)"
				: n64 ? "de Nintendo 64 (Mupen64Plus-Next)" : snes ? "de Super Nintendo (Snes9x)" : "FBNeo") + " en este teléfono.";
		} else if (code == GLRetroView.ERROR_LOAD_GAME && snes) {
			msg = "El emulador de Super Nintendo no pudo abrir «" + rom.getName() + "».\n\n"
				+ "• Formatos que abre: .sfc, .smc, .fig, .swc (o un .zip / .7z con uno de ellos).\n"
				+ "• El archivo puede estar incompleto o dañado: vuelve a añadirlo.";
		} else if (code == GLRetroView.ERROR_LOAD_GAME && n64) {
			msg = "El emulador de Nintendo 64 no pudo abrir «" + rom.getName() + "».\n\n"
				+ "• Formatos que abre: .z64, .n64 y .v64 (o un .zip / .7z con uno de ellos).\n"
				+ "• El archivo puede estar incompleto o dañado: vuelve a añadirlo.";
		} else if (code == GLRetroView.ERROR_GL_NOT_COMPATIBLE && n64) {
			msg = "La GPU de este teléfono no tiene lo que pide el emulador de Nintendo 64 (OpenGL ES 3).";
		} else if (code == GLRetroView.ERROR_LOAD_GAME && dc) {
			msg = "El emulador de Dreamcast no pudo abrir «" + rom.getName() + "».\n\n"
				+ "• Si es un .gdi, tienen que estar todas sus pistas (track01.bin, track02.raw…): añade el .zip o .7z del juego entero.\n"
				+ "• Formatos que abre: .gdi, .cdi, .chd, .cue + .bin.\n"
				+ "• Algunos juegos piden la BIOS real (dc_boot.bin y dc_flash.bin): añádela con «+ Dreamcast».";
		} else if (code == GLRetroView.ERROR_GL_NOT_COMPATIBLE && dc) {
			msg = "La GPU de este teléfono no tiene lo que pide el emulador de Dreamcast (OpenGL ES 3).";
		} else if (code == GLRetroView.ERROR_LOAD_GAME && psx) {
			msg = "El emulador de PS1 no pudo abrir «" + rom.getName() + "».\n\n"
				+ "• Si es un .cue, tienen que estar todos los .bin que nombra (elígelos juntos al añadirlo).\n"
				+ "• Formatos que abre: .cue + .bin, .chd, .pbp, .iso, .img.\n"
				+ "• El archivo puede estar incompleto o dañado: vuelve a añadirlo.";
		} else if (code == GLRetroView.ERROR_LOAD_GAME) {
			String found = "";
			try {
				found = ArcadeCheck.check(this, rom).describe(rom.getName()).trim();
			} catch (RuntimeException e) {
				Log.w(TAG, "Check", e);
			}
			msg = "FBNeo no pudo abrir «" + rom.getName() + "».\n\n"
				+ (!found.isEmpty() ? found : "• El ZIP tiene que ser el romset que espera FBNeo (las versiones actuales de FBNeo o MAME suelen servir; los muy viejos no).\n"
					+ "• Los juegos de Neo Geo necesitan neogeo.zip en el catálogo, y las versiones hack (Magic Plus, Plus…) también el juego original.");
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
		if (isFinishing() || isDestroyed()) {
			return;
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
	public void onAnalog(float x, float y) {
		if (retro != null) {
			retro.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, x, y, 0);
		}
	}

	/** The N64's C buttons as the right stick: each pair (up / down, left / right) is one axis. */
	private void sendCButtons() {
		if (retro != null) {
			float x = (cRight ? 1f : 0f) - (cLeft ? 1f : 0f);
			float y = (cDown ? 1f : 0f) - (cUp ? 1f : 0f);
			retro.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, x, y, 0);
		}
	}

	@Override
	public void onKey(int code, boolean down) {
		if (code <= N64_C_UP && code >= N64_C_DOWN) {
			if (code == N64_C_UP) {
				cUp = down;
			} else if (code == N64_C_LEFT) {
				cLeft = down;
			} else if (code == N64_C_RIGHT) {
				cRight = down;
			} else {
				cDown = down;
			}
			sendCButtons();
			return;
		}
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
			applyGameAudio();
			retro.onPause();
		}
		if (music != null) {
			music.setRunning(false);
		}
	}

	private void resumeGame() {
		if (retro != null && paused) {
			paused = false;
			retro.onResume();
			applyGameAudio();
		}
		if (music != null) {
			music.setRunning(started);
		}
	}

	private File stateFile() {
		return new File(states, set + ".state");
	}

	private File sramFile() {
		return new File(saves, set + ".srm");
	}

	/** One line of the game's menu. run() returns true when the menu closes after it. */
	private interface MenuAction {
		boolean run();
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
		final boolean hires = prefs.getBoolean("hires", false);
		final boolean hidden = prefs.getBoolean("hide_pad", false);
		final boolean six = sixButtons();
		String shaderName = RetroEffects.find(prefs.getString("shader", "sharp")).label;
		final java.util.List<String> labels = new java.util.ArrayList<>();
		final java.util.List<MenuAction> actions = new java.util.ArrayList<>();
		labels.add("▶  Continuar");
		actions.add(() -> true);
		labels.add("💾  Guardar estado");
		actions.add(() -> {
			saveState();
			return true;
		});
		labels.add(hasState ? "📂  Cargar estado (" + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(state.lastModified())) + ")" : "📂  Cargar estado (no hay)");
		actions.add(() -> {
			if (hasState) {
				loadState();
			} else {
				Toast.makeText(this, "Todavía no guardaste un estado de este juego", Toast.LENGTH_SHORT).show();
			}
			return true;
		});
		if (!n64) {
			// (The N64 moves with its analog stick.)
			labels.add("🕹  Movimiento: " + (stick ? "joystick" : "flechas"));
			actions.add(() -> {
				prefs.edit().putString("move", stick ? "dpad" : "stick").apply();
				applyPadSettings();
				return true;
			});
		}
		if (!psx && !dc && !n64 && !snes && !neoGeo) {
			labels.add("🔘  Botones: " + (six ? "6 (LP MP HP / LK MK HK, Capcom)" : "4 (A B C D)"));
			actions.add(() -> {
				prefs.edit().putString("buttons_" + set, six ? "four" : "six").apply();
				applyPadSettings();
				return true;
			});
		}
		labels.add("📏  Tamaño de los botones: " + (size < 95 ? "pequeño" : size > 110 ? "grande" : "normal"));
		actions.add(() -> {
			prefs.edit().putInt("size", size < 95 ? 100 : size > 110 ? 85 : 120).apply();
			applyPadSettings();
			return true;
		});
		labels.add("👁  Controles en pantalla: " + (hidden ? "ocultos" : "visibles"));
		actions.add(() -> {
			prefs.edit().putBoolean("hide_pad", !hidden).apply();
			applyPadSettings();
			if (!hidden) {
				Toast.makeText(this, "Para volver a verlos, abre este menú con el botón Atrás", Toast.LENGTH_LONG).show();
			}
			return true;
		});
		labels.add("🖼  Imagen: " + shaderName);
		actions.add(() -> {
			pickShader();
			return false;
		});
		if (snes) {
			final boolean exact = "uncorrected".equals(prefs.getString("snes_aspect", "4:3"));
			final boolean flicker = prefs.getBoolean("snes_flicker", true);
			final boolean blend = prefs.getBoolean("snes_blend", false);
			if (hudGame != null) {
				final int design = prefs.getInt("hud_design", RetroHud.DESIGN_NEON);
				labels.add("📊  HUD propio: " + RetroHud.DESIGN_NAMES[design]);
				actions.add(() -> {
					int next = (design + 1) % RetroHud.DESIGN_NAMES.length;
					prefs.edit().putInt("hud_design", next).apply();
					hud.setDesign(next);
					Toast.makeText(this, next == RetroHud.DESIGN_OFF ? "HUD propio apagado"
						: "HUD " + RetroHud.DESIGN_NAMES[next] + " (" + hudGame.name + "): aparece durante las peleas", Toast.LENGTH_SHORT).show();
					return true;
				});
			}
			labels.add("🖥  Proporción: " + (exact ? "8:7 (píxeles exactos)" : "4:3 (como en la tele)"));
			actions.add(() -> {
				String next = exact ? "4:3" : "uncorrected";
				prefs.edit().putString("snes_aspect", next).apply();
				retro.updateVariables(new Variable("snes9x_aspect", next, null));
				if (hud != null) {
					hud.setAspect(snesAspect());
				}
				Toast.makeText(this, "Si no cambia, vuelve a abrir el juego", Toast.LENGTH_SHORT).show();
				return true;
			});
			labels.add("✨  Menos parpadeo de sprites: " + (flicker ? "sí" : "no"));
			actions.add(() -> {
				prefs.edit().putBoolean("snes_flicker", !flicker).apply();
				retro.updateVariables(new Variable("snes9x_reduce_sprite_flicker", !flicker ? "enabled" : "disabled", null));
				return true;
			});
			labels.add("🌫  Mezclar la alta resolución: " + (blend ? "sí" : "no"));
			actions.add(() -> {
				prefs.edit().putBoolean("snes_blend", !blend).apply();
				retro.updateVariables(new Variable("snes9x_hires_blend", !blend ? "merge" : "disabled", null));
				Toast.makeText(this, "Para los juegos que usan el modo de alta resolución (sus transparencias se ven como en la tele)", Toast.LENGTH_LONG).show();
				return true;
			});
		} else if (n64) {
			final int res = n64Res();
			final int tex = Math.max(0, java.util.Arrays.asList(N64_TEX).indexOf(prefs.getString("n64_tex", N64_TEX[0])));
			final boolean wide = prefs.getBoolean("n64_wide", false);
			labels.add("✨  Resolución: " + N64_RES_NAMES[res]);
			actions.add(() -> {
				int next = (res + 1) % N64_RES.length;
				prefs.edit().putString("n64_res", N64_RES[next]).apply();
				Toast.makeText(this, "Resolución " + N64_RES_NAMES[next] + ": se aplica la próxima vez que abras el juego. Si va lento, bájala.", Toast.LENGTH_LONG).show();
				return true;
			});
			labels.add("🧩  Texturas: " + DC_TEX_NAMES[tex]);
			actions.add(() -> {
				int next = (tex + 1) % N64_TEX.length;
				prefs.edit().putString("n64_tex", N64_TEX[next]).apply();
				retro.updateVariables(new Variable("mupen64plus-txEnhancementMode", N64_TEX[next], null));
				Toast.makeText(this, "Texturas " + DC_TEX_NAMES[next]
					+ ": si no cambian, vuelve a abrir el juego. xBRZ suaviza los dibujos; pide más al teléfono.", Toast.LENGTH_LONG).show();
				return true;
			});
			labels.add("🖥  Pantalla ancha: " + (wide ? "sí (16:9)" : "no (4:3)"));
			actions.add(() -> {
				prefs.edit().putBoolean("n64_wide", !wide).apply();
				Toast.makeText(this, "Se aplica la próxima vez que abras el juego. En algunos juegos se ven fallos en los bordes.", Toast.LENGTH_LONG).show();
				return true;
			});
		} else if (dc) {
			final int res = Math.max(0, java.util.Arrays.asList(DC_RES).indexOf(prefs.getString("dc_res", DC_RES[0])));
			final int tex = Math.max(0, java.util.Arrays.asList(DC_TEX).indexOf(prefs.getString("dc_tex", DC_TEX[0])));
			final boolean wide = prefs.getBoolean("dc_wide", false);
			labels.add("✨  Resolución: " + DC_RES_NAMES[res]);
			actions.add(() -> {
				String next = DC_RES[(res + 1) % DC_RES.length];
				prefs.edit().putString("dc_res", next).apply();
				retro.updateVariables(new Variable("reicast_internal_resolution", next, null));
				Toast.makeText(this, "Resolución " + DC_RES_NAMES[(res + 1) % DC_RES.length] + ". Si va lento, baja la resolución.", Toast.LENGTH_SHORT).show();
				return true;
			});
			labels.add("🧩  Texturas: " + DC_TEX_NAMES[tex]);
			actions.add(() -> {
				String next = DC_TEX[(tex + 1) % DC_TEX.length];
				prefs.edit().putString("dc_tex", next).apply();
				retro.updateVariables(new Variable("reicast_texupscale", next, null));
				Toast.makeText(this, "Texturas " + DC_TEX_NAMES[(tex + 1) % DC_TEX.length]
					+ ": se ven al cargar texturas nuevas (o al reiniciar el juego). xBRZ es para juegos 2D.", Toast.LENGTH_LONG).show();
				return true;
			});
			labels.add("🖥  Pantalla ancha: " + (wide ? "sí (puede mostrar fallos en los bordes)" : "no (4:3)"));
			actions.add(() -> {
				prefs.edit().putBoolean("dc_wide", !wide).apply();
				retro.updateVariables(new Variable("reicast_widescreen_hack", !wide ? "enabled" : "disabled", null));
				Toast.makeText(this, "Si no cambia, reinicia el juego", Toast.LENGTH_SHORT).show();
				return true;
			});
		} else if (psx) {
			labels.add("✨  Resolución: " + (hires ? "doble (más nítida, pide más al teléfono)" : "normal de PS1"));
			actions.add(() -> {
				prefs.edit().putBoolean("hires", !hires).apply();
				retro.updateVariables(new Variable("pcsx_rearmed_neon_enhancement_enable", !hires ? "enabled" : "disabled", null));
				Toast.makeText(this, !hires ? "Resolución doble: si va lento, vuelve a la normal" : "Resolución normal", Toast.LENGTH_SHORT).show();
				return true;
			});
		} else if (neoGeo) {
			labels.add("🎮  Neo Geo: " + (aes ? "AES (consola, sin monedas)" : "MVS (arcade, con monedas)"));
			actions.add(() -> {
				prefs.edit().putString("neogeo_mode", aes ? "mvs" : "aes").apply();
				Toast.makeText(this, "Se aplica la próxima vez que abras el juego", Toast.LENGTH_LONG).show();
				return true;
			});
		}
		labels.add("🔄  Reiniciar el juego");
		actions.add(() -> {
			retro.reset(false);
			return true;
		});
		labels.add("✕  Salir al catálogo");
		actions.add(() -> {
			exit(true);
			return false;
		});
		new AlertDialog.Builder(this)
			.setTitle(title)
			.setItems(labels.toArray(new String[0]), (d, which) -> {
				if (actions.get(which).run()) {
					closeMenu();
				}
			})
			.setOnCancelListener(d -> closeMenu())
			.show();
	}

	private void pickShader() {
		String current = RetroEffects.find(prefs.getString("shader", "sharp")).id;
		final RetroEffects.Effect[] effects = RetroEffects.EFFECTS;
		String[] names = new String[effects.length];
		int checked = 0;
		for (int i = 0; i < effects.length; i++) {
			names[i] = effects[i].label;
			if (effects[i].id.equals(current)) {
				checked = i;
			}
		}
		new AlertDialog.Builder(this)
			.setTitle("Imagen (efectos)")
			.setSingleChoiceItems(names, checked, (d, which) -> {
				prefs.edit().putString("shader", effects[which].id).apply();
				applyEffect(effects[which].id);
				Toast.makeText(this, effects[which].description, Toast.LENGTH_SHORT).show();
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
		if (hud != null) {
			hud.stop();
		}
		if (save) {
			pauseGame();
			saveSram();
		}
		if (music != null) {
			music.release();
			music = null;
		}
		GameRun.end(this);
		finish();
	}

	@Override
	protected void onPause() {
		if (pad != null) {
			pad.releaseAll();
		}
		if (hud != null) {
			// No reading in the background (it would only use battery).
			hud.stop();
		}
		if (music != null) {
			music.setForeground(false);
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
		if (music != null) {
			music.setForeground(true);
		}
		// The view resumes itself with the activity: keep the game paused while the menu is open.
		handler.post(() -> {
			if (menuOpen && retro != null) {
				paused = false;
				pauseGame();
			}
		});
		if (hud != null && started && !failed) {
			hud.start();
		}
	}

	// Controllers (Bluetooth / USB). GLRetroView reads them itself, but only when it has the focus,
	// which a SurfaceView doesn't take: hand it every event from a controller (the Back and Mode
	// buttons still open the menu).

	private static boolean fromController(InputEvent e) {
		int s = e.getSource();
		return (s & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
			|| (s & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
			|| ((s & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD && e.getDevice() != null && e.getDevice().getControllerNumber() > 0);
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		int code = event.getKeyCode();
		if (retro != null && started && !failed && code != KeyEvent.KEYCODE_BACK && code != KeyEvent.KEYCODE_BUTTON_MODE && fromController(event)) {
			boolean handled = false;
			if (event.getAction() == KeyEvent.ACTION_DOWN) {
				controllerUsed();
				handled = retro.onKeyDown(code, event);
			} else if (event.getAction() == KeyEvent.ACTION_UP) {
				handled = retro.onKeyUp(code, event);
			}
			if (handled) {
				return true;
			}
		}
		return super.dispatchKeyEvent(event);
	}

	@Override
	public boolean dispatchGenericMotionEvent(MotionEvent event) {
		if (retro != null && started && !failed && (event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
			&& event.getActionMasked() == MotionEvent.ACTION_MOVE) {
			controllerUsed();
			retro.onGenericMotionEvent(event);
			return true;
		}
		return super.dispatchGenericMotionEvent(event);
	}

	/** Playing with a controller: the touch controls go away until the screen is touched. */
	private void controllerUsed() {
		if (pad != null && !padAutoHidden && pad.getVisibility() == View.VISIBLE) {
			padAutoHidden = true;
			pad.releaseAll();
			pad.setVisibility(View.GONE);
		}
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent event) {
		if (padAutoHidden && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
			padAutoHidden = false;
			if (pad != null && !prefs.getBoolean("hide_pad", false)) {
				pad.setVisibility(View.VISIBLE);
			}
		}
		return super.dispatchTouchEvent(event);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (music != null) {
			music.release();
			music = null;
		}
		if (!isFinishing()) {
			// Android is recreating the screen (it shouldn't, configChanges covers it): keep the
			// process, the new screen starts the core again.
			GameRun.unexpected(this, "Android volvió a crear la pantalla del juego");
			return;
		}
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
			// Through the decor view, which getDecorView() creates if needed (Window.getInsetsController()
			// throws when there's no decor yet).
			WindowInsetsController c = w.getDecorView().getWindowInsetsController();
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
