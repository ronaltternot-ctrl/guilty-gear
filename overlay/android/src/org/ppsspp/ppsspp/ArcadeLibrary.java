package org.ppsspp.ppsspp;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Arcade games for FBNeo (ArcadeActivity): the ROM zips the player adds are copied into
 * files/arcade/roms/, next to each other, because FBNeo looks for a game's BIOS (neogeo.zip for
 * the Neo Geo) and its parent set (kof2002.zip for kf2k2mp.zip) in the same folder.
 */
final class ArcadeLibrary {
	private ArcadeLibrary() {}

	static final String SYSTEM = "arcade";

	/** BIOS and device sets: needed by the games, not games themselves. */
	private static final String[] BIOS = { "neogeo", "pgm", "skns", "decocass", "isgsm", "nmk004", "ym2608", "cchip",
		"bubsys", "namcoc69", "namcoc70", "namcoc75", "qsound", "midssio", "konamigx", "cps3", "hng64", "aleck64", "fdsbios",
		"coleco", "msx", "spectrum", "spec128", "spec1282a" };

	private static final Map<String, String> TITLES = new HashMap<>();

	static {
		String[][] t = {
			{ "kof94", "The King of Fighters '94" }, { "kof95", "The King of Fighters '95" },
			{ "kof96", "The King of Fighters '96" }, { "kof97", "The King of Fighters '97" },
			{ "kof97pls", "The King of Fighters '97 Plus" }, { "kof97oro", "The King of Fighters '97 Chongchu Jianghu Plus" },
			{ "kof98", "The King of Fighters '98" }, { "kof98h", "The King of Fighters '98 (AES)" },
			{ "kof99", "The King of Fighters '99" }, { "kof2000", "The King of Fighters 2000" },
			{ "kof2001", "The King of Fighters 2001" }, { "kof2002", "The King of Fighters 2002" },
			{ "kf2k2pls", "The King of Fighters 2002 Plus" }, { "kf2k2pla", "The King of Fighters 2002 Plus (set 2)" },
			{ "kf2k2mp", "The King of Fighters 2002 Magic Plus" }, { "kf2k2mp2", "The King of Fighters 2002 Magic Plus II" },
			{ "kof2002b", "The King of Fighters 2002 (bootleg)" }, { "kof2003", "The King of Fighters 2003" },
			{ "kof2003h", "The King of Fighters 2003 (AES)" }, { "kof10th", "The King of Fighters 10th Anniversary" },
			{ "kf10thep", "The King of Fighters 10th Anniversary Extra Plus" }, { "kf2k5uni", "The King of Fighters 10th Anniversary 2005 Unique" },
			{ "kof2k4se", "The King of Fighters Special Edition 2004" }, { "garou", "Garou: Mark of the Wolves" },
			{ "rbff1", "Real Bout Fatal Fury" }, { "rbffspec", "Real Bout Fatal Fury Special" }, { "rbff2", "Real Bout Fatal Fury 2" },
			{ "fatfury1", "Fatal Fury" }, { "fatfury2", "Fatal Fury 2" }, { "fatfursp", "Fatal Fury Special" }, { "fatfury3", "Fatal Fury 3" },
			{ "aof", "Art of Fighting" }, { "aof2", "Art of Fighting 2" }, { "aof3", "Art of Fighting 3" },
			{ "samsho", "Samurai Shodown" }, { "samsho2", "Samurai Shodown II" }, { "samsho3", "Samurai Shodown III" },
			{ "samsho4", "Samurai Shodown IV" }, { "samsho5", "Samurai Shodown V" }, { "samsh5sp", "Samurai Shodown V Special" },
			{ "lastblad", "The Last Blade" }, { "lastbld2", "The Last Blade 2" }, { "matrim", "Matrimelee" },
			{ "svc", "SNK vs. Capcom: SVC Chaos" }, { "svcplus", "SVC Chaos Plus" }, { "rotd", "Rage of the Dragons" },
			{ "kizuna", "Kizuna Encounter" }, { "wh2j", "World Heroes 2 Jet" }, { "whp", "World Heroes Perfect" },
			{ "mslug", "Metal Slug" }, { "mslug2", "Metal Slug 2" }, { "mslugx", "Metal Slug X" }, { "mslug3", "Metal Slug 3" },
			{ "mslug4", "Metal Slug 4" }, { "mslug5", "Metal Slug 5" }, { "sengoku3", "Sengoku 3" }, { "blazstar", "Blazing Star" },
			{ "sf2", "Street Fighter II" }, { "sf2ce", "Street Fighter II': Champion Edition" }, { "ssf2t", "Super Street Fighter II Turbo" },
			{ "sfa", "Street Fighter Alpha" }, { "sfa2", "Street Fighter Alpha 2" }, { "sfa3", "Street Fighter Alpha 3" },
			{ "mvsc", "Marvel vs. Capcom" }, { "msh", "Marvel Super Heroes" }, { "xmvsf", "X-Men vs. Street Fighter" },
			{ "vsav", "Vampire Savior" }, { "dstlk", "Darkstalkers" }, { "cybots", "Cyberbots" },
		};
		for (String[] row : t) {
			TITLES.put(row[0], row[1]);
		}
	}

	static File romDir(Context c) {
		return new File(new File(c.getFilesDir(), "arcade"), "roms");
	}

	/** The set name of a zip ("kof2002" for "KOF2002.zip"). */
	static String setName(String fileName) {
		String n = fileName == null ? "" : fileName.trim();
		int slash = n.lastIndexOf('/');
		if (slash >= 0) {
			n = n.substring(slash + 1);
		}
		int dot = n.lastIndexOf('.');
		if (dot > 0) {
			n = n.substring(0, dot);
		}
		return n.toLowerCase(Locale.ROOT);
	}

	static boolean isArchive(String fileName) {
		String n = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
		return n.endsWith(".zip") || n.endsWith(".7z");
	}

	static boolean isBios(String set) {
		for (String b : BIOS) {
			if (b.equals(set)) {
				return true;
			}
		}
		return false;
	}

	static String title(String set) {
		String t = TITLES.get(set);
		return t != null ? t : set.toUpperCase(Locale.ROOT);
	}

	/** The title in the launcher's own list, or null. */
	static String knownTitle(String set) {
		return TITLES.get(set);
	}

	/** Copies one archive out of a pack ZIP into the ROM folder. */
	static File extractInner(Context c, File zip, String entryName) throws IOException {
		String base = entryName.replace('\\', '/');
		base = base.substring(base.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
		File dest = new File(romDir(c), base);
		File tmp = new File(romDir(c), base + ".part");
		try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(zip)) {
			java.util.zip.ZipEntry e = z.getEntry(entryName);
			if (e == null) {
				throw new IOException("No se encontró " + entryName);
			}
			try (InputStream in = z.getInputStream(e); OutputStream os = new FileOutputStream(tmp)) {
				byte[] buf = new byte[1 << 16];
				int n;
				while ((n = in.read(buf)) > 0) {
					os.write(buf, 0, n);
				}
			}
		} catch (IOException e) {
			//noinspection ResultOfMethodCallIgnored
			tmp.delete();
			throw e;
		}
		if (dest.exists() && !dest.delete()) {
			throw new IOException("No se pudo reemplazar " + dest.getName());
		}
		if (!tmp.renameTo(dest)) {
			throw new IOException("No se pudo guardar " + dest.getName());
		}
		return dest;
	}

	/** Gives a ZIP its romset name (kof2002.zip). Returns the renamed file. */
	static File renameTo(File f, String set) throws IOException {
		String ext = f.getName().toLowerCase(Locale.ROOT).endsWith(".7z") ? ".7z" : ".zip";
		File dest = new File(f.getParentFile(), set + ext);
		if (dest.equals(f)) {
			return f;
		}
		if (dest.exists() && !dest.delete()) {
			throw new IOException("No se pudo reemplazar " + dest.getName());
		}
		if (!f.renameTo(dest)) {
			throw new IOException("No se pudo renombrar " + f.getName());
		}
		return dest;
	}

	/** SNK's Neo Geo games (they need neogeo.zip): the known ones, by name. */
	static boolean looksNeoGeo(String set) {
		String[] prefixes = { "kof", "kf2k", "kf10", "garou", "rbff", "fatfur", "aof", "samsh", "lastbl", "mslug", "matrim", "svc",
			"rotd", "kizuna", "wh", "sengoku", "blazstar" };
		for (String p : prefixes) {
			if (set.startsWith(p)) {
				return true;
			}
		}
		return false;
	}

	static boolean hasNeoGeoBios(Context c) {
		File dir = romDir(c);
		return new File(dir, "neogeo.zip").isFile() || new File(dir, "neogeo.7z").isFile();
	}

	static String displayName(Context c, Uri uri) {
		try (Cursor cursor = c.getContentResolver().query(uri, new String[] { OpenableColumns.DISPLAY_NAME }, null, null, null)) {
			if (cursor != null && cursor.moveToFirst()) {
				String name = cursor.getString(0);
				if (name != null) {
					return name;
				}
			}
		} catch (RuntimeException ignored) {
			// Fall back to the path below.
		}
		String last = uri.getLastPathSegment();
		return last == null ? "" : last;
	}

	/** Copies one zip into the ROM folder (replacing an older copy). Returns the file. */
	static File copyIn(Context c, Uri uri, String fileName) throws IOException {
		File dir = romDir(c);
		if (!dir.isDirectory() && !dir.mkdirs()) {
			throw new IOException("No se pudo crear " + dir);
		}
		String clean = fileName.replace('/', '_').replace('\\', '_');
		// FBNeo matches the set name in lower case.
		int dot = clean.lastIndexOf('.');
		String out = dot > 0 ? clean.substring(0, dot).toLowerCase(Locale.ROOT) + clean.substring(dot).toLowerCase(Locale.ROOT) : clean.toLowerCase(Locale.ROOT);
		File dest = new File(dir, out);
		File tmp = new File(dir, out + ".part");
		try (InputStream in = c.getContentResolver().openInputStream(uri); OutputStream os = new FileOutputStream(tmp)) {
			if (in == null) {
				throw new IOException("No se pudo abrir " + fileName);
			}
			byte[] buf = new byte[1 << 16];
			int n;
			while ((n = in.read(buf)) > 0) {
				os.write(buf, 0, n);
			}
		} catch (IOException e) {
			//noinspection ResultOfMethodCallIgnored
			tmp.delete();
			throw e;
		}
		if (dest.exists() && !dest.delete()) {
			throw new IOException("No se pudo reemplazar " + dest.getName());
		}
		if (!tmp.renameTo(dest)) {
			throw new IOException("No se pudo guardar " + dest.getName());
		}
		return dest;
	}
}
