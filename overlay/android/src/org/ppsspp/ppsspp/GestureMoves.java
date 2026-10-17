package org.ppsspp.ppsspp;

/**
 * Special moves behind the four buttons of the gesture controls (UI/AcPlusGestures.h), written
 * in numpad notation for a character facing right: 236P = down, down-forward, forward + P.
 * [4]6S is a charge (hold back, then forward + S).
 */
final class GestureMoves {
	private GestureMoves() {}

	static final int SLOTS = 4;
	static final String[] SLOT_NAMES = { "Especial 1", "Especial 2", "Especial 3", "Overdrive" };

	/** For characters without their own move in a slot, and for other versions of the game. */
	static final String[] GENERIC = { "236S", "623S", "214K", "632146HS" };

	/**
	 * Defaults per character number (1 = Sol ... 25 = Justice, same order as
	 * LauncherActivity.CHARACTERS): "input|name", or null to use the generic move.
	 */
	private static final String[][] DEFAULTS = {
		{},
		/* Sol */ { "236P|Gun Flame", "623S|Volcanic Viper", "236K|Bandit Revolver", "632146HS|Tyrant Rave" },
		/* Ky */ { "236S|Stun Edge", "623S|Vapor Thrust", "236K|Stun Dipper", "632146HS|Ride the Lightning" },
		/* May */ { "[4]6S|Mr. Dolphin horizontal", "[2]8S|Mr. Dolphin vertical", "623S|Restive Rolling", "236236S|Great Yamada Attack" },
		/* Millia */ { "236S|Tandem Top", "214P|Silent Force", "214K|Forward Roll", "236236S|Emerald Rain" },
		/* Axl */ { null, null, null, null },
		/* Potemkin */ { "632146P|Potemkin Buster", "623HS|Heat Knuckle", "[4]6HS|Hammer Fall", "632146HS|Giganter" },
		/* Chipp */ { "236P|Alpha Blade", "623S|Beta Blade", "236K|Resshou", "236236K|Banki Messai" },
		/* Eddie */ { null, null, null, null },
		/* Baiken */ { "236K|Tatami Gaeshi", null, null, null },
		/* Faust */ { "236P|What Could This Be?", null, null, "236236P|W-W-What Could This Be?" },
		/* Testament */ { "236S|Phantom Soul", null, null, null },
		/* Jam */ { "236K|Ryujin", "214K|Gekirin", null, null },
		/* Anji */ { "236HS|Fuujin", "236P|Shitsu", null, "632146HS|Issei Ougi: Sai" },
		/* Johnny */ { null, null, null, null },
		/* Venom */ { "[4]6S|Stinger Aim", "[2]8S|Carcass Raid", null, null },
		/* Dizzy */ { null, null, null, null },
		/* Slayer */ { "236P|Mappa Hunch", "214P|Dandy Step", null, null },
		/* I-No */ { "236S|Antidepressant Scale", "214K|Chemical Love", null, null },
		/* Zappa */ { null, null, null, null },
		/* Bridget */ { "236K|Kick Start My Heart", null, null, null },
		/* Robo-Ky */ { "236S|Don't Get Coc-KY!", "214S|Fun-KY", "623HS|Hun-KY Homerun", "236236S|What's Useless Will Always Be So" },
		/* A.B.A */ { null, null, null, null },
		/* Order-Sol */ { "236P|Block Head Buster", "214S|Gun Blaze", "623HS|Storm Viper", "632146HS|Tyrant Rave ver. Ω" },
		/* Kliff */ { null, null, null, null },
		/* Justice */ { null, null, null, null },
	};

	/** Motions offered in the editor: notation and how it looks. */
	static final String[][] MOTIONS = {
		{ "236", "↓↘→" },
		{ "214", "↓↙←" },
		{ "623", "→↓↘" },
		{ "421", "←↓↙" },
		{ "41236", "←↙↓↘→" },
		{ "63214", "→↘↓↙←" },
		{ "632146", "→↘↓↙←→" },
		{ "236236", "↓↘→ ↓↘→" },
		{ "214214", "↓↙← ↓↙←" },
		{ "22", "↓ ↓" },
		{ "[4]6", "carga ← y luego →" },
		{ "[2]8", "carga ↓ y luego ↑" },
		{ "66", "→ →" },
		{ "6", "→" },
		{ "4", "←" },
		{ "2", "↓" },
		{ "", "solo el botón" },
	};

	static final String[] BUTTONS = { "P", "K", "S", "HS", "D" };
	static final String[] BUTTON_NAMES = { "P · puño", "K · patada", "S · corte", "HS · corte fuerte", "D · Dust" };

	private static String entry(int id, int slot) {
		if (id <= 0 || id >= DEFAULTS.length || slot < 0 || slot >= SLOTS || DEFAULTS[id].length <= slot) {
			return null;
		}
		return DEFAULTS[id][slot];
	}

	/** Default input for a character's slot (the generic one if it has none). */
	static String defaultMove(int id, int slot) {
		String e = entry(id, slot);
		return e == null ? GENERIC[slot] : e.substring(0, e.indexOf('|'));
	}

	/** Move name for an input, if it is that character's default. */
	static String name(int id, int slot, String input) {
		String e = entry(id, slot);
		if (e != null && e.startsWith(input + "|")) {
			return e.substring(e.indexOf('|') + 1);
		}
		return null;
	}

	/** Splits "632146HS" into { "632146", "HS" }. */
	static String[] split(String input) {
		String button = "";
		for (String b : new String[] { "HS", "P", "K", "S", "D" }) {
			if (input.endsWith(b)) {
				button = b;
				break;
			}
		}
		return new String[] { input.substring(0, input.length() - button.length()), button };
	}

	/** "236P" -> "↓↘→ + P", "[4]6S" -> "carga ←, → + S". */
	static String arrows(String input) {
		String[] parts = split(input);
		String motion = parts[0];
		StringBuilder out = new StringBuilder();
		int i = 0;
		if (motion.length() >= 3 && motion.charAt(0) == '[' && motion.charAt(2) == ']') {
			out.append("carga ").append(arrow(motion.charAt(1))).append(", ");
			i = 3;
		}
		for (; i < motion.length(); i++) {
			out.append(arrow(motion.charAt(i)));
		}
		if (!parts[1].isEmpty()) {
			if (out.length() > 0) {
				out.append(" + ");
			}
			out.append(parts[1]);
		}
		return out.toString();
	}

	private static String arrow(char digit) {
		switch (digit) {
			case '1': return "↙";
			case '2': return "↓";
			case '3': return "↘";
			case '4': return "←";
			case '6': return "→";
			case '7': return "↖";
			case '8': return "↑";
			case '9': return "↗";
			default: return "·";
		}
	}

	/** Same check as AcGesture::ParseMotion, for inputs read back from preferences. */
	static boolean valid(String input) {
		return input != null && input.matches("(\\[[1-9]\\][1-9]+|[1-9]*)(HS|P|K|S|D)*") && !input.isEmpty();
	}
}
