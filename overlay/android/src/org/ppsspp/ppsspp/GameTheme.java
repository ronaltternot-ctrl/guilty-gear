package org.ppsspp.ppsspp;

import android.content.res.AssetManager;
import android.graphics.Typeface;

import java.util.HashMap;
import java.util.Map;

/**
 * The home screen's look for each game: colors, background picture, title letters and how sharp
 * the corners are. LauncherActivity rebuilds its screen with the theme of the game that is chosen
 * (with no game, or a game without a theme of its own, the PS2 one).
 */
final class GameTheme {
	static final int PS2 = 0;
	static final int GUILTY_GEAR = 1;
	static final int BLAZBLUE = 2;
	static final int KOF = 3;
	static final int STREET_FIGHTER = 4;
	static final int YUGIOH = 5;

	/** Background pictures (GameBackground). */
	static final int BG_TOWERS = 0;   // PS2: towers of light on a horizon, specks of light
	static final int BG_SLASHES = 1;  // Guilty Gear: big diagonal slashes and thin lines
	static final int BG_RINGS = 2;    // BlazBlue: rings of a magic seal, with ticks
	static final int BG_EMBERS = 3;   // KOF: fire glowing from below, rising embers
	static final int BG_RAYS = 4;     // Street Fighter Alpha: a sunburst from the top
	static final int BG_PENDULUM = 5; // Yu-Gi-Oh! ARC-V: a pendulum of light and its arc, the
	                                  // scales' pillars, a holographic field, cards, sparkles

	/** Shape of the small light before each section's title. */
	static final int MARK_DOT = 0;
	static final int MARK_DIAMOND = 1;
	static final int MARK_SQUARE = 2;
	static final int MARK_CRYSTAL = 3;

	final int id;

	// Palette (see LauncherActivity's C_* fields).
	int bg, card, stroke, accent, hi, text, muted, section, chipStroke, glow, track, inset;
	/** The lit buttons: gradient top to bottom and their thin edge. */
	int glassTop, glassMid, glassBottom, glassEdge;

	// Background.
	int pattern;
	/** Vertical gradient, top to bottom (4 colors). */
	int[] sky;
	/** Soft light from above, at full strength (it fades out). */
	int skyGlow;
	/** The pattern's main color and its bright edge (alpha ignored). */
	int ink, inkHi;
	/** Second and third colors of the picture, and a sweep of colors (only some pictures). */
	int ink2, ink3;
	int[] spectrum;

	// Letters and shapes.
	String titleFamily;
	int titleStyle;
	float titleSpacing;
	float titleSize = 30;
	/** Height of the emblem above the title (dp), 0 for none (only BG_PENDULUM has one). */
	float emblemHeight;
	/** Letters of the big buttons (Play, Catalog). */
	String buttonFamily;
	int buttonStyle;
	/**
	 * The same letters as fonts bundled in assets/launcher_fonts, used first: phones with a font
	 * theme replace the system's fonts (all of them), and the menu would lose its style. The
	 * families above are the fallback. labelAsset (kicker and section names) is optional.
	 */
	String titleAsset, buttonAsset, labelAsset;
	/** Corner radius of the cards, and the most a button or chip can have (dp). */
	float cardRadius, buttonRadius;
	int mark;
	/** Cards drawn as panels, with corner brackets and a line of color on top (DuelFrame). */
	boolean cardDetail;
	/** The play button's word, when not "JUGAR". */
	String playLabel;

	private GameTheme(int id) {
		this.id = id;
	}

	private static final Map<String, Typeface> FONTS = new HashMap<>();

	/** A bundled font (cached), or the system family when it can't be loaded. */
	static Typeface font(AssetManager assets, String asset, String family, int style) {
		if (asset != null && assets != null) {
			Typeface t;
			synchronized (FONTS) {
				if (!FONTS.containsKey(asset)) {
					try {
						FONTS.put(asset, Typeface.createFromAsset(assets, "launcher_fonts/" + asset));
					} catch (RuntimeException e) {
						FONTS.put(asset, null);
					}
				}
				t = FONTS.get(asset);
			}
			if (t != null) {
				return t;
			}
		}
		return Typeface.create(family, style);
	}

	Typeface titleTypeface(AssetManager assets) {
		return font(assets, titleAsset, titleFamily, titleStyle);
	}

	Typeface buttonTypeface(AssetManager assets) {
		return font(assets, buttonAsset, buttonFamily, buttonStyle);
	}

	/** The kicker above the title and the sections' names. */
	Typeface labelTypeface(AssetManager assets) {
		return font(assets, labelAsset, "sans-serif-light", Typeface.NORMAL);
	}

	/** A color with another alpha (0-255). */
	static int alpha(int color, int alpha) {
		return (color & 0x00FFFFFF) | (alpha << 24);
	}

	private static final GameTheme[] THEMES = { ps2(), guiltyGear(), blazBlue(), kof(), streetFighter(), yugioh() };

	static GameTheme get(int id) {
		return id >= 0 && id < THEMES.length ? THEMES[id] : THEMES[PS2];
	}

	/** Theme for a game kind (GameInspector.KIND_*). */
	static int forKind(int kind) {
		switch (kind) {
			case GameInspector.KIND_GG:
				return GUILTY_GEAR;
			case GameInspector.KIND_BB:
				return BLAZBLUE;
			case GameInspector.KIND_KOF:
				return KOF;
			case GameInspector.KIND_SF:
				return STREET_FIGHTER;
			default:
				return PS2;
		}
	}

	/** Minimal and retro, after the PlayStation 2's menus (deep blue, black, soft blue light). */
	private static GameTheme ps2() {
		GameTheme t = new GameTheme(PS2);
		t.bg = 0xFF02040B;
		t.card = 0x590A1430;
		t.stroke = 0x332F4E8F;
		t.accent = 0xFF6F9BFF;
		t.hi = 0xFFBFD4FF;
		t.text = 0xFFE9EEF9;
		t.muted = 0xFF8792AE;
		t.section = 0xFFB8C6E6;
		t.chipStroke = 0x553D5A99;
		t.glow = 0x996F9BFF;
		t.track = 0xFF2A3350;
		t.inset = 0xFF070B18;
		t.glassTop = 0xFF3D6AE0;
		t.glassMid = 0xFF2346A8;
		t.glassBottom = 0xFF16307A;
		t.glassEdge = 0xFF8FB2FF;
		t.pattern = BG_TOWERS;
		t.sky = new int[] { 0xFF0B1638, 0xFF060B1C, 0xFF020409, 0xFF000000 };
		t.skyGlow = 0xFF3A63D0;
		t.ink = 0xFF3A63D0;
		t.inkHi = 0xFF9FBFFF;
		t.titleFamily = "sans-serif-thin";
		t.titleStyle = Typeface.NORMAL;
		t.titleSpacing = 0.06f;
		t.buttonFamily = "sans-serif-light";
		t.buttonStyle = Typeface.NORMAL;
		t.titleAsset = "Inter-Thin.otf";
		t.buttonAsset = "Inter-Light.otf";
		t.cardRadius = 8;
		t.buttonRadius = 6;
		t.mark = MARK_DOT;
		return t;
	}

	/** Guilty Gear: crimson and black, sharp corners, slashes, heavy slanted letters. */
	private static GameTheme guiltyGear() {
		GameTheme t = new GameTheme(GUILTY_GEAR);
		t.bg = 0xFF080203;
		t.card = 0x661A0609;
		t.stroke = 0x447A1E2A;
		t.accent = 0xFFFF3D52;
		t.hi = 0xFFFFC2C9;
		t.text = 0xFFF7ECEE;
		t.muted = 0xFFA89196;
		t.section = 0xFFEBC5CA;
		t.chipStroke = 0x66992A3A;
		t.glow = 0x99FF3D52;
		t.track = 0xFF3A1820;
		t.inset = 0xFF140507;
		t.glassTop = 0xFFD9283D;
		t.glassMid = 0xFFA3142A;
		t.glassBottom = 0xFF5E0915;
		t.glassEdge = 0xFFFF8E9B;
		t.pattern = BG_SLASHES;
		t.sky = new int[] { 0xFF2B070C, 0xFF140306, 0xFF070102, 0xFF000000 };
		t.skyGlow = 0xFFB0122C;
		t.ink = 0xFFC8162F;
		t.inkHi = 0xFFFF6B7C;
		t.titleFamily = "sans-serif-condensed";
		t.titleStyle = Typeface.BOLD_ITALIC;
		t.titleSpacing = 0.02f;
		t.buttonFamily = "sans-serif-condensed";
		t.buttonStyle = Typeface.BOLD_ITALIC;
		t.titleAsset = "DejaVuSansCondensed-BoldOblique.ttf";
		t.buttonAsset = "DejaVuSansCondensed-BoldOblique.ttf";
		t.cardRadius = 2;
		t.buttonRadius = 2;
		t.mark = MARK_DIAMOND;
		return t;
	}

	/** BlazBlue: ice blue and azure, rounded, the rings of a magic seal, light slanted letters. */
	private static GameTheme blazBlue() {
		GameTheme t = new GameTheme(BLAZBLUE);
		t.bg = 0xFF010910;
		t.card = 0x59072233;
		t.stroke = 0x3D2C8FB8;
		t.accent = 0xFF3FD2FF;
		t.hi = 0xFFC4F1FF;
		t.text = 0xFFEAF8FF;
		t.muted = 0xFF84A6B5;
		t.section = 0xFFB5E2F2;
		t.chipStroke = 0x55308AAD;
		t.glow = 0x993FD2FF;
		t.track = 0xFF1C3946;
		t.inset = 0xFF041119;
		t.glassTop = 0xFF1FA8E0;
		t.glassMid = 0xFF0F73A3;
		t.glassBottom = 0xFF094466;
		t.glassEdge = 0xFF8FE6FF;
		t.pattern = BG_RINGS;
		t.sky = new int[] { 0xFF06263D, 0xFF03131F, 0xFF01070C, 0xFF000000 };
		t.skyGlow = 0xFF1C8FC4;
		t.ink = 0xFF2BB5E8;
		t.inkHi = 0xFFA8EBFF;
		t.titleFamily = "sans-serif-light";
		t.titleStyle = Typeface.ITALIC;
		t.titleSpacing = 0.05f;
		t.buttonFamily = "sans-serif-light";
		t.buttonStyle = Typeface.ITALIC;
		t.titleAsset = "Inter-LightItalic.otf";
		t.buttonAsset = "Inter-LightItalic.otf";
		t.cardRadius = 12;
		t.buttonRadius = 12;
		t.mark = MARK_DIAMOND;
		return t;
	}

	/** KOF: fire orange on a warm black, square corners, embers, heavy condensed letters. */
	private static GameTheme kof() {
		GameTheme t = new GameTheme(KOF);
		t.bg = 0xFF080401;
		t.card = 0x611C0E04;
		t.stroke = 0x448A4A14;
		t.accent = 0xFFFF8A1F;
		t.hi = 0xFFFFD6AD;
		t.text = 0xFFFFF4EA;
		t.muted = 0xFFB29B88;
		t.section = 0xFFF2D3B8;
		t.chipStroke = 0x66A0581C;
		t.glow = 0x99FF8A1F;
		t.track = 0xFF3D2615;
		t.inset = 0xFF140A03;
		t.glassTop = 0xFFF0801E;
		t.glassMid = 0xFFC2540E;
		t.glassBottom = 0xFF782E06;
		t.glassEdge = 0xFFFFC28A;
		t.pattern = BG_EMBERS;
		t.sky = new int[] { 0xFF1E0D04, 0xFF0F0602, 0xFF0A0401, 0xFF1A0702 };
		t.skyGlow = 0xFFB34A0C;
		t.ink = 0xFFFF6A10;
		t.inkHi = 0xFFFFC04D;
		t.titleFamily = "sans-serif-condensed";
		t.titleStyle = Typeface.BOLD;
		t.titleSpacing = 0.08f;
		t.buttonFamily = "sans-serif-condensed";
		t.buttonStyle = Typeface.BOLD;
		t.titleAsset = "DejaVuSansCondensed-Bold.ttf";
		t.buttonAsset = "DejaVuSansCondensed-Bold.ttf";
		t.cardRadius = 4;
		t.buttonRadius = 3;
		t.mark = MARK_SQUARE;
		return t;
	}

	/** Street Fighter Alpha: gold on deep purple, a sunburst, purple buttons with a gold edge, black slanted letters. */
	private static GameTheme streetFighter() {
		GameTheme t = new GameTheme(STREET_FIGHTER);
		t.bg = 0xFF06020D;
		t.card = 0x5E1A0B33;
		t.stroke = 0x447A4FB0;
		t.accent = 0xFFFFC23D;
		t.hi = 0xFFFFE7A8;
		t.text = 0xFFFBF5EA;
		t.muted = 0xFFA898B8;
		t.section = 0xFFEAD9B8;
		t.chipStroke = 0x66845AB8;
		t.glow = 0x99FFB020;
		t.track = 0xFF33244A;
		t.inset = 0xFF0E0619;
		t.glassTop = 0xFF8A3FD6;
		t.glassMid = 0xFF5C22A0;
		t.glassBottom = 0xFF35115E;
		t.glassEdge = 0xFFFFD27A;
		t.pattern = BG_RAYS;
		t.sky = new int[] { 0xFF2A0F4A, 0xFF150829, 0xFF080312, 0xFF000000 };
		t.skyGlow = 0xFFE08A1C;
		t.ink = 0xFFFFB52E;
		t.inkHi = 0xFFFFE08A;
		t.titleFamily = "sans-serif-black";
		t.titleStyle = Typeface.ITALIC;
		t.titleSpacing = 0.03f;
		t.buttonFamily = "sans-serif-black";
		t.buttonStyle = Typeface.ITALIC;
		t.titleAsset = "Inter-BlackItalic.otf";
		t.buttonAsset = "Inter-BlackItalic.otf";
		t.cardRadius = 6;
		t.buttonRadius = 5;
		t.mark = MARK_DIAMOND;
		return t;
	}

	/**
	 * Yu-Gi-Oh! ARC-V Tag Force Special: emerald light on a night-teal field, a pendulum swinging
	 * over a rainbow arc, panels like a duel's, buttons that go from orange to green like a
	 * pendulum card's frame, serif letters like card names, and "DUELO" to start.
	 */
	private static GameTheme yugioh() {
		GameTheme t = new GameTheme(YUGIOH);
		t.bg = 0xFF02080B;
		t.card = 0x7306141C;
		t.stroke = 0x4434B892;
		t.accent = 0xFF3DF2B0;
		t.hi = 0xFFC8FFEA;
		t.text = 0xFFEEFBF6;
		t.muted = 0xFF8DAAA2;
		t.section = 0xFFBFEBDD;
		t.chipStroke = 0x5531A985;
		t.glow = 0x993DF2B0;
		t.track = 0xFF183A34;
		t.inset = 0xFF04110F;
		t.glassTop = 0xFFE8842C;
		t.glassMid = 0xFF6E8E3A;
		t.glassBottom = 0xFF137A58;
		t.glassEdge = 0xFFB8FFE4;
		t.pattern = BG_PENDULUM;
		t.sky = new int[] { 0xFF0B2633, 0xFF061822, 0xFF030A0F, 0xFF000000 };
		t.skyGlow = 0xFF139C86;
		t.ink = 0xFF3DF2B0;
		t.inkHi = 0xFFB8FFE6;
		t.ink2 = 0xFFFF4D6D;
		t.ink3 = 0xFF4FA8FF;
		t.spectrum = new int[] { 0xFFFF4D6D, 0xFFFFB347, 0xFF3DF2B0, 0xFF4FA8FF };
		t.titleFamily = "serif";
		t.titleStyle = Typeface.BOLD;
		t.titleSpacing = 0.03f;
		t.titleSize = 25;
		t.emblemHeight = 132;
		t.buttonFamily = "serif";
		t.buttonStyle = Typeface.BOLD;
		t.titleAsset = "Cinzel-Bold.ttf";
		t.buttonAsset = "Cinzel-Bold.ttf";
		t.labelAsset = "Cinzel-SemiBold.ttf";
		t.cardRadius = 6;
		t.buttonRadius = 4;
		t.mark = MARK_CRYSTAL;
		t.cardDetail = true;
		t.playLabel = "DUELO";
		return t;
	}
}
