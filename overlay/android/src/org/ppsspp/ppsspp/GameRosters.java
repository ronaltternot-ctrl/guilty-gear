package org.ppsspp.ppsspp;

import java.util.Locale;

/**
 * The characters of each game with per-character profiles, and which versions of each game the
 * custom HUD and the profiles can read (UI/AcPlusGameData.h on the emulator side). The keys are
 * the ones the emulator uses for the saved layouts: "12" (Guilty Gear, by character number),
 * "bb_rg" (BlazBlue, by sprite code), "kof_kyo" (KOF, shared by '96, '97 and '98).
 */
final class GameRosters {
	private GameRosters() {}

	static final class Roster {
		final String[] keys;
		final String[] names;

		Roster(String[] keys, String[] names) {
			this.keys = keys;
			this.names = names;
		}

		String name(String key) {
			for (int i = 0; i < keys.length; i++) {
				if (keys[i].equals(key)) {
					return names[i];
				}
			}
			return key;
		}
	}

	static final Roster GG;

	static {
		String[] keys = new String[LauncherActivity.CHARACTERS.length - 1];
		String[] names = new String[keys.length];
		for (int id = 1; id < LauncherActivity.CHARACTERS.length; id++) {
			keys[id - 1] = String.valueOf(id);
			names[id - 1] = LauncherActivity.CHARACTERS[id];
		}
		GG = new Roster(keys, names);
	}

	/** Calamity Trigger, then the ones Continuum Shift added. */
	static final Roster BB = new Roster(
		new String[] { "bb_rg", "bb_jn", "bb_no", "bb_rc", "bb_tk", "bb_tg", "bb_lc", "bb_ar", "bb_bn", "bb_ca",
			"bb_ha", "bb_ny", "bb_tb", "bb_hz", "bb_mu", "bb_mk", "bb_vh", "bb_pt", "bb_rl", "bb_rm" },
		new String[] { "Ragna", "Jin", "Noel", "Rachel", "Taokaka", "Tager", "Litchi", "Arakune", "Bang", "Carl",
			"Hakumen", "Nu-13", "Tsubaki (CS II)", "Hazama (CS II)", "Mu-12 (CS II)", "Makoto (CS II)", "Valkenhayn (CS II)",
			"Platinum (CS II)", "Relius (CS II)", "Lambda-11 (CS II)" });

	/** Every fighter of KOF '96, '97 and '98 once. */
	static final Roster KOF = new Roster(
		new String[] { "kof_kyo", "kof_benimaru", "kof_daimon", "kof_terry", "kof_andy", "kof_joe", "kof_ryo", "kof_robert",
			"kof_yuri", "kof_leona", "kof_ralf", "kof_clark", "kof_athena", "kof_kensou", "kof_chin", "kof_chizuru", "kof_mai",
			"kof_king", "kof_kim", "kof_chang", "kof_choi", "kof_yashiro", "kof_shermie", "kof_chris", "kof_yamazaki", "kof_mary",
			"kof_billy", "kof_iori", "kof_mature", "kof_vice", "kof_heidern", "kof_takuma", "kof_saisyu", "kof_heavyd", "kof_lucky",
			"kof_brian", "kof_rugal", "kof_shingo", "kof_kasumi", "kof_geese", "kof_krauser", "kof_mrbig", "kof_goenitz",
			"kof_oiori", "kof_oleona", "kof_orochi" },
		new String[] { "Kyo Kusanagi", "Benimaru Nikaido", "Goro Daimon", "Terry Bogard", "Andy Bogard", "Joe Higashi",
			"Ryo Sakazaki", "Robert Garcia", "Yuri Sakazaki", "Leona", "Ralf Jones", "Clark Steel", "Athena Asamiya", "Sie Kensou",
			"Chin Gentsai", "Chizuru Kagura", "Mai Shiranui", "King", "Kim Kaphwan", "Chang Koehan", "Choi Bounge",
			"Yashiro Nanakase", "Shermie", "Chris", "Ryuji Yamazaki", "Blue Mary", "Billy Kane", "Iori Yagami", "Mature", "Vice",
			"Heidern", "Takuma Sakazaki", "Saisyu Kusanagi", "Heavy D!", "Lucky Glauber", "Brian Battler", "Rugal Bernstein",
			"Shingo Yabuki", "Kasumi Todo ('96)", "Geese Howard ('96)", "Wolfgang Krauser ('96)", "Mr. Big ('96)", "Goenitz ('96)",
			"Orochi Iori ('97)", "Orochi Leona ('97)", "Orochi ('97)" });

	/** Street Fighter Alpha 3: where the game keeps the character is not known yet. */
	static final Roster SF = new Roster(new String[0], new String[0]);

	static final Roster[] ALL = { GG, BB, KOF };

	static Roster forKind(int kind) {
		return kind == GameInspector.KIND_BB ? BB : kind == GameInspector.KIND_KOF ? KOF : kind == GameInspector.KIND_SF ? SF : GG;
	}

	private static String norm(String id) {
		return id == null ? "" : id.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
	}

	/** Disc IDs whose memory the HUD and the profiles know. */
	private static final String[] MEMORY_IDS = { "ULUS10409", "ULUS10519", "ULES01497", "ULUS10579", "ULUS10360", "ULUS10062", "ULES00235", "ULJM05082" };

	static boolean memoryKnown(String discId) {
		String id = norm(discId);
		for (String known : MEMORY_IDS) {
			if (known.equals(id)) {
				return true;
			}
		}
		return false;
	}

	/** The versions that work, for the warning shown with another one. */
	static String knownVersions(int kind) {
		switch (kind) {
			case GameInspector.KIND_BB:
				return "Calamity Trigger americano (ULUS-10519) y europeo (ULES-01497), y Continuum Shift II americano (ULUS-10579)";
			case GameInspector.KIND_KOF:
				return "la versión americana (ULUS-10360)";
			case GameInspector.KIND_SF:
				return "Alpha 3 MAX americano (ULUS-10062) y europeo (ULES-00235), y Zero 3 Double Upper japonés (ULJM-05082)";
			default:
				return "la versión americana (ULUS-10409)";
		}
	}
}
