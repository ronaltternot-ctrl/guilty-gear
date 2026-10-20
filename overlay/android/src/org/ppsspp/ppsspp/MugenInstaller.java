package org.ppsspp.ppsspp;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * MUGEN games for Ikemen GO (MugenActivity): copies a game the player picks (a folder or a .zip)
 * into the app's folder, adds the engine files Ikemen GO needs that a MUGEN game doesn't have
 * (assets/ikemen: external/ scripts, data/ common files, font/), and writes save/config.ini
 * (the game's motif and resolution from its mugen.cfg, the keys the touch controls press).
 */
final class MugenInstaller {
	private MugenInstaller() {}

	private static final String TAG = "MugenInstaller";
	static final String SYSTEM = "mugen";
	private static final String ENGINE_ASSETS = "ikemen";
	private static final String STAMP = "external/.launcher-engine";

	interface Progress {
		/** From any thread. */
		void onProgress(String what, long done, long total);
	}

	static File gamesDir(Context c) {
		File base = c.getExternalFilesDir(null);
		if (base == null) {
			base = c.getFilesDir();
		}
		return new File(base, "mugen");
	}

	// Import

	private static String slug(String name) {
		String s = name == null ? "" : name.trim();
		int dot = s.toLowerCase(Locale.ROOT).endsWith(".zip") ? s.length() - 4 : -1;
		if (dot > 0) {
			s = s.substring(0, dot);
		}
		s = s.replaceAll("[^A-Za-z0-9 ._()\\-]", "_").trim();
		if (s.isEmpty()) {
			s = "MUGEN";
		}
		return s.length() > 60 ? s.substring(0, 60) : s;
	}

	private static File freshDir(Context c, String name) throws IOException {
		File dir = gamesDir(c);
		String base = slug(name);
		File out = new File(dir, base);
		for (int i = 2; out.exists(); i++) {
			out = new File(dir, base + " (" + i + ")");
		}
		if (!out.mkdirs()) {
			throw new IOException("No se pudo crear la carpeta del juego");
		}
		return out;
	}

	/** The folder that holds data/ and chars/ (the game may be one or two folders down). */
	static File findRoot(File dir) {
		if (isGameRoot(dir)) {
			return dir;
		}
		File[] kids = dir.listFiles();
		if (kids == null) {
			return null;
		}
		for (File k : kids) {
			if (k.isDirectory() && isGameRoot(k)) {
				return k;
			}
		}
		for (File k : kids) {
			if (k.isDirectory()) {
				File[] grand = k.listFiles();
				if (grand == null) {
					continue;
				}
				for (File g : grand) {
					if (g.isDirectory() && isGameRoot(g)) {
						return g;
					}
				}
			}
		}
		return null;
	}

	private static boolean isGameRoot(File dir) {
		return child(dir, "data") != null && child(dir, "data").isDirectory() && child(dir, "chars") != null;
	}

	/** A file or folder by name, ignoring case (MUGEN games come from Windows). */
	static File child(File dir, String name) {
		File exact = new File(dir, name);
		if (exact.exists()) {
			return exact;
		}
		String[] names = dir.list();
		if (names != null) {
			for (String n : names) {
				if (n.equalsIgnoreCase(name)) {
					return new File(dir, n);
				}
			}
		}
		return null;
	}

	/** Resolves "data/kof/system.def" (or with backslashes) inside root, ignoring case. */
	static File resolve(File root, String rel) {
		List<String> parts = new ArrayList<>();
		for (String part : rel.replace('\\', '/').split("/")) {
			if (!part.isEmpty() && !part.equals(".")) {
				parts.add(part);
			}
		}
		return resolve(root, parts, 0);
	}

	/** Tries the exact name first, then every other spelling (a zip can hold Data/ and data/). */
	private static File resolve(File dir, List<String> parts, int i) {
		if (i == parts.size()) {
			return dir;
		}
		String part = parts.get(i);
		List<File> candidates = new ArrayList<>();
		File exact = new File(dir, part);
		if (exact.exists()) {
			candidates.add(exact);
		}
		String[] names = dir.list();
		if (names != null) {
			for (String n : names) {
				if (n.equalsIgnoreCase(part) && !n.equals(part)) {
					candidates.add(new File(dir, n));
				}
			}
		}
		for (File c : candidates) {
			if (i < parts.size() - 1 && !c.isDirectory()) {
				continue;
			}
			File found = resolve(c, parts, i + 1);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	/** Extracts a .zip into a new folder; returns the game's root folder. */
	static File importZip(Context c, Uri uri, String name, Progress p) throws IOException {
		File dest = freshDir(c, name);
		long total = 0;
		try (Cursor cur = c.getContentResolver().query(uri, new String[] { android.provider.OpenableColumns.SIZE }, null, null, null)) {
			if (cur != null && cur.moveToFirst() && !cur.isNull(0)) {
				total = cur.getLong(0);
			}
		} catch (RuntimeException ignored) {
			// Unknown size: the progress shows the files instead.
		}
		try {
			try {
				unzip(c, uri, dest, StandardCharsets.UTF_8, total, p);
			} catch (IllegalArgumentException badName) {
				// Names that aren't UTF-8 (zips made on Windows): start over as CP437.
				deleteTree(dest);
				if (!dest.mkdirs()) {
					throw new IOException("No se pudo crear la carpeta del juego");
				}
				unzip(c, uri, dest, Charset.forName("IBM437"), total, p);
			}
		} catch (IOException | RuntimeException e) {
			deleteTree(dest);
			throw e;
		}
		File root = findRoot(dest);
		if (root == null) {
			deleteTree(dest);
			throw new IOException("Ese .zip no parece un juego MUGEN: no tiene las carpetas «data» y «chars».");
		}
		return root;
	}

	private static void unzip(Context c, Uri uri, File dest, Charset charset, long total, Progress p) throws IOException {
		String destPath = dest.getCanonicalPath() + File.separator;
		final long[] read = { 0 };
		try (InputStream raw = c.getContentResolver().openInputStream(uri)) {
			if (raw == null) {
				throw new IOException("No se pudo abrir el archivo");
			}
			InputStream counting = new java.io.FilterInputStream(raw) {
				@Override
				public int read(byte[] b, int off, int len) throws IOException {
					int n = super.read(b, off, len);
					if (n > 0) {
						read[0] += n;
					}
					return n;
				}
			};
			try (ZipInputStream zin = new ZipInputStream(new java.io.BufferedInputStream(counting, 1 << 16), charset)) {
				ZipEntry e;
				byte[] buf = new byte[1 << 16];
				int files = 0;
				while ((e = zin.getNextEntry()) != null) {
					String n = e.getName().replace('\\', '/');
					File out = new File(dest, n);
					if (!out.getCanonicalPath().startsWith(destPath)) {
						continue;   // "../" in the name: skip
					}
					if (e.isDirectory()) {
						if (!out.isDirectory() && !out.mkdirs()) {
							throw new IOException("No se pudo crear " + n);
						}
						continue;
					}
					File parent = out.getParentFile();
					if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
						throw new IOException("No se pudo crear " + parent.getName());
					}
					try (OutputStream os = new FileOutputStream(out)) {
						int k;
						while ((k = zin.read(buf)) > 0) {
							os.write(buf, 0, k);
						}
					}
					files++;
					if (p != null && (files % 20 == 0)) {
						p.onProgress(n, read[0], total);
					}
				}
			}
		}
	}

	/** Copies a folder picked with the system's folder picker; returns the game's root folder. */
	static File importTree(Context c, Uri tree, Progress p) throws IOException {
		ContentResolver cr = c.getContentResolver();
		String rootId = DocumentsContract.getTreeDocumentId(tree);
		String name = queryName(cr, DocumentsContract.buildDocumentUriUsingTree(tree, rootId));
		File dest = freshDir(c, name == null ? "MUGEN" : name);
		long[] count = { 0 };
		try {
			copyTree(cr, tree, rootId, dest, p, count);
		} catch (IOException | RuntimeException e) {
			deleteTree(dest);
			throw e;
		}
		File root = findRoot(dest);
		if (root == null) {
			deleteTree(dest);
			throw new IOException("Esa carpeta no parece un juego MUGEN: no tiene las carpetas «data» y «chars».");
		}
		return root;
	}

	private static String queryName(ContentResolver cr, Uri doc) {
		try (Cursor cur = cr.query(doc, new String[] { DocumentsContract.Document.COLUMN_DISPLAY_NAME }, null, null, null)) {
			if (cur != null && cur.moveToFirst()) {
				return cur.getString(0);
			}
		} catch (RuntimeException ignored) {
			// No name.
		}
		return null;
	}

	private static void copyTree(ContentResolver cr, Uri tree, String docId, File dest, Progress p, long[] count) throws IOException {
		Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
		List<String[]> entries = new ArrayList<>();
		try (Cursor cur = cr.query(children, new String[] {
			DocumentsContract.Document.COLUMN_DOCUMENT_ID,
			DocumentsContract.Document.COLUMN_DISPLAY_NAME,
			DocumentsContract.Document.COLUMN_MIME_TYPE }, null, null, null)) {
			if (cur == null) {
				return;
			}
			while (cur.moveToNext()) {
				entries.add(new String[] { cur.getString(0), cur.getString(1), cur.getString(2) });
			}
		}
		byte[] buf = new byte[1 << 16];
		for (String[] e : entries) {
			String name = e[1] == null ? "file" : e[1].replace('/', '_');
			File out = new File(dest, name);
			if (DocumentsContract.Document.MIME_TYPE_DIR.equals(e[2])) {
				if (!out.isDirectory() && !out.mkdirs()) {
					throw new IOException("No se pudo crear " + name);
				}
				copyTree(cr, tree, e[0], out, p, count);
				continue;
			}
			Uri doc = DocumentsContract.buildDocumentUriUsingTree(tree, e[0]);
			try (InputStream in = cr.openInputStream(doc); OutputStream os = new FileOutputStream(out)) {
				if (in == null) {
					throw new IOException("No se pudo leer " + name);
				}
				int k;
				while ((k = in.read(buf)) > 0) {
					os.write(buf, 0, k);
				}
			}
			count[0]++;
			if (p != null && count[0] % 20 == 0) {
				p.onProgress(name, count[0], -1);
			}
		}
	}

	static void deleteTree(File f) {
		if (f == null || !f.exists()) {
			return;
		}
		File[] kids = f.listFiles();
		if (kids != null) {
			for (File k : kids) {
				deleteTree(k);
			}
		}
		if (!f.delete()) {
			Log.w(TAG, "Could not delete " + f);
		}
	}

	static long sizeOf(File f) {
		if (f == null || !f.exists()) {
			return 0;
		}
		if (f.isFile()) {
			return f.length();
		}
		long total = 0;
		File[] kids = f.listFiles();
		if (kids != null) {
			for (File k : kids) {
				total += sizeOf(k);
			}
		}
		return total;
	}

	// Engine files and config (each time the game starts, before Ikemen GO gets the folder)

	/** The engine version the APK carries (assets/ikemen/VERSION). */
	static String engineVersion(Context c) {
		try (InputStream in = c.getAssets().open(ENGINE_ASSETS + "/VERSION")) {
			return readAll(in).trim();
		} catch (IOException e) {
			return "";
		}
	}

	static boolean engineBundled(Context c) {
		return !engineVersion(c).isEmpty();
	}

	/**
	 * Copies Ikemen GO's files into the game: external/ (the engine's Lua scripts) always matches
	 * the engine in the APK; data/ and font/ files are only added when the game doesn't have them
	 * (never over the game's own screenpack, lifebars or fonts).
	 */
	static void installEngine(Context c, File root) throws IOException {
		AssetManager am = c.getAssets();
		String version = engineVersion(c);
		if (version.isEmpty()) {
			throw new IOException("Esta versión de la app no trae el motor Ikemen GO.");
		}
		File stamp = new File(root, STAMP);
		boolean refreshScripts = !stamp.isFile() || !version.equals(readText(stamp).trim());
		List<String> files = new ArrayList<>();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(am.open(ENGINE_ASSETS + "/files.txt"), StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				line = line.trim();
				if (!line.isEmpty()) {
					files.add(line);
				}
			}
		}
		byte[] buf = new byte[1 << 16];
		for (String rel : files) {
			boolean engineScript = rel.startsWith("external/");
			File out = engineScript ? new File(root, rel) : resolveOrNew(root, rel);
			if (out.exists() && !(engineScript && refreshScripts)) {
				continue;
			}
			File parent = out.getParentFile();
			if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
				throw new IOException("No se pudo crear " + parent);
			}
			try (InputStream in = am.open(ENGINE_ASSETS + "/" + rel); OutputStream os = new FileOutputStream(out)) {
				int k;
				while ((k = in.read(buf)) > 0) {
					os.write(buf, 0, k);
				}
			}
		}
		writeText(stamp, version + "\n");
	}

	/** The game's file if it exists under another case, else the path to create. */
	private static File resolveOrNew(File root, String rel) {
		File found = resolve(root, rel);
		return found != null ? found : new File(root, rel);
	}

	/** Motif and resolution the game asks for in data/mugen.cfg (MUGEN 1.0 / 1.1). */
	static final class GameConfig {
		String motif = "data/system.def";
		int width, height;
	}

	static GameConfig readMugenCfg(File root) {
		GameConfig g = new GameConfig();
		File cfg = resolve(root, "data/mugen.cfg");
		if (cfg == null || !cfg.isFile()) {
			return g;
		}
		String text = readText(cfg);
		Pattern motif = Pattern.compile("(?im)^\\s*motif\\s*=\\s*([^;\\r\\n]+)");
		Matcher m = motif.matcher(text);
		while (m.find()) {
			String rel = m.group(1).trim().replace("\"", "").replace('\\', '/');
			File f = resolve(root, rel);
			if (f != null && f.isFile()) {
				g.motif = relative(root, f);
				break;
			}
		}
		Matcher w = Pattern.compile("(?im)^\\s*GameWidth\\s*=\\s*(\\d+)").matcher(text);
		Matcher h = Pattern.compile("(?im)^\\s*GameHeight\\s*=\\s*(\\d+)").matcher(text);
		if (w.find() && h.find()) {
			try {
				g.width = Integer.parseInt(w.group(1));
				g.height = Integer.parseInt(h.group(1));
			} catch (NumberFormatException ignored) {
				g.width = g.height = 0;
			}
		}
		if (g.width < 320 || g.height < 240 || g.width > 3840 || g.height > 2160) {
			g.width = g.height = 0;
		}
		return g;
	}

	private static String relative(File root, File f) {
		String r = root.getAbsolutePath();
		String p = f.getAbsolutePath();
		return p.startsWith(r + "/") ? p.substring(r.length() + 1) : p;
	}

	/** Keyboard keys the touch controls press (Ikemen GO's defaults for player 1). */
	static final String[][] KEYS_P1 = {
		{ "up", "UP" }, { "down", "DOWN" }, { "left", "LEFT" }, { "right", "RIGHT" },
		{ "a", "z" }, { "b", "x" }, { "c", "c" }, { "x", "a" }, { "y", "s" }, { "z", "d" },
		{ "start", "RETURN" }, { "d", "q" }, { "w", "w" },
	};

	/** Writes or updates save/config.ini for Android and the touch controls. */
	static void writeConfig(File root) throws IOException {
		File save = new File(root, "save");
		if (!save.isDirectory() && !save.mkdirs()) {
			throw new IOException("No se pudo crear la carpeta save");
		}
		File ini = resolve(root, "save/config.ini");
		GameConfig game = readMugenCfg(root);
		List<String> lines = new ArrayList<>();
		boolean existed = ini != null && ini.isFile();
		if (existed) {
			for (String l : readText(ini).split("\r?\n", -1)) {
				lines.add(l);
			}
		} else {
			ini = new File(save, "config.ini");
			lines.add("; Ikemen GO config written by the GGXX AC+ launcher for this MUGEN game.");
		}
		// Keep the motif of a game made for Ikemen GO (its own config); otherwise the game's own.
		String motif = get(lines, "Config", "Motif");
		if (!existed || motif == null || resolve(root, motif.replace("\"", "")) == null) {
			set(lines, "Config", "Motif", game.motif);
		}
		set(lines, "Config", "FirstRun", "0");
		set(lines, "Config", "EscOpensMenu", "1");
		set(lines, "Video", "RenderMode", "OpenGL ES 3.2");
		if (!existed && game.width > 0) {
			set(lines, "Video", "GameWidth", String.valueOf(game.width));
			set(lines, "Video", "GameHeight", String.valueOf(game.height));
		}
		set(lines, "Keys_P1", "Joystick", "-1");
		for (String[] k : KEYS_P1) {
			set(lines, "Keys_P1", k[0], k[1]);
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < lines.size(); i++) {
			out.append(lines.get(i));
			if (i < lines.size() - 1) {
				out.append('\n');
			}
		}
		if (out.length() == 0 || out.charAt(out.length() - 1) != '\n') {
			out.append('\n');
		}
		writeText(ini, out.toString());
	}

	private static int sectionStart(List<String> lines, String section) {
		for (int i = 0; i < lines.size(); i++) {
			String t = lines.get(i).trim();
			if (t.startsWith("[") && t.endsWith("]") && t.substring(1, t.length() - 1).trim().equalsIgnoreCase(section)) {
				return i;
			}
		}
		return -1;
	}

	private static int sectionEnd(List<String> lines, int start) {
		for (int i = start + 1; i < lines.size(); i++) {
			if (lines.get(i).trim().startsWith("[")) {
				return i;
			}
		}
		return lines.size();
	}

	private static int keyLine(List<String> lines, int start, int end, String key) {
		for (int i = start + 1; i < end; i++) {
			String t = lines.get(i).trim();
			if (t.startsWith(";")) {
				continue;
			}
			int eq = t.indexOf('=');
			if (eq > 0 && t.substring(0, eq).trim().equalsIgnoreCase(key)) {
				return i;
			}
		}
		return -1;
	}

	static String get(List<String> lines, String section, String key) {
		int s = sectionStart(lines, section);
		if (s < 0) {
			return null;
		}
		int k = keyLine(lines, s, sectionEnd(lines, s), key);
		if (k < 0) {
			return null;
		}
		String t = lines.get(k);
		String v = t.substring(t.indexOf('=') + 1);
		int semi = v.indexOf(';');
		return (semi >= 0 ? v.substring(0, semi) : v).trim();
	}

	/** Sets the first "key = value" of a section (adds the key or the section if needed). */
	static void set(List<String> lines, String section, String key, String value) {
		int s = sectionStart(lines, section);
		if (s < 0) {
			if (!lines.isEmpty() && !lines.get(lines.size() - 1).trim().isEmpty()) {
				lines.add("");
			}
			lines.add("[" + section + "]");
			lines.add(key + " = " + value);
			return;
		}
		int end = sectionEnd(lines, s);
		int k = keyLine(lines, s, end, key);
		String line = key + " = " + value;
		if (k >= 0) {
			lines.set(k, line);
		} else {
			// At the end of the section, before its blank lines.
			int at = end;
			while (at > s + 1 && lines.get(at - 1).trim().isEmpty()) {
				at--;
			}
			lines.add(at, line);
		}
	}

	// Small file helpers

	private static String readAll(InputStream in) throws IOException {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int n;
		while ((n = in.read(buf)) > 0) {
			out.write(buf, 0, n);
		}
		// Latin-1 keeps every byte as it is (MUGEN files come in all kinds of Windows encodings).
		return out.toString("ISO-8859-1");
	}

	static String readText(File f) {
		try (InputStream in = new FileInputStream(f)) {
			return readAll(in);
		} catch (IOException e) {
			return "";
		}
	}

	private static void writeText(File f, String text) throws IOException {
		File parent = f.getParentFile();
		if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
			throw new IOException("No se pudo crear " + parent);
		}
		try (OutputStream out = new FileOutputStream(f)) {
			out.write(text.getBytes(StandardCharsets.ISO_8859_1));
		}
	}
}
