package org.ppsspp.ppsspp;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * PlayStation 1 games for PCSX ReARMed (ArcadeActivity with the "psx" system): each game the
 * player adds is copied into its own folder (a .cue needs its .bin files next to it), from a
 * .cue with its .bin, a lone .bin (the app writes the .cue), .chd, .pbp, .iso / .img, .m3u, or a
 * .zip / .7z with any of those. BIOS files (scph1001.bin…) go to the system folder, where the
 * emulator uses them instead of its built-in one.
 */
final class PsxLibrary {
	private PsxLibrary() {}

	private static final String TAG = "PsxLibrary";
	static final String SYSTEM = "psx";

	/** What the emulator opens, in order of preference when a folder holds several. */
	private static final String[] MAIN = { "m3u", "cue", "chd", "pbp", "ccd", "mds", "iso", "img", "bin" };

	static File gamesDir(Context c) {
		File base = c.getExternalFilesDir(null);
		if (base == null) {
			base = c.getFilesDir();
		}
		return new File(new File(base, "psx"), "games");
	}

	/** The emulator's system folder (BIOS), next to its saves (see ArcadeActivity). */
	static File systemDir(Context c) {
		return new File(new File(c.getFilesDir(), "psx"), "system");
	}

	static boolean hasBios(Context c) {
		String[] names = systemDir(c).list();
		if (names != null) {
			for (String n : names) {
				if (isBiosName(n)) {
					return true;
				}
			}
		}
		return false;
	}

	private static String ext(String name) {
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private static String base(String name) {
		int dot = name.lastIndexOf('.');
		return dot <= 0 ? name : name.substring(0, dot);
	}

	static boolean isBiosName(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		return n.endsWith(".bin") && (n.startsWith("scph") || n.startsWith("psxonpsp") || n.startsWith("ps1_rom") || n.startsWith("openbios"));
	}

	/** A readable title from a file name: no extension, no "(Track 1)". */
	static String title(String fileName) {
		String t = base(fileName).replaceAll("(?i)\\s*\\(track \\d+\\)", "").replace('_', ' ').trim();
		return t.isEmpty() ? "PlayStation" : t;
	}

	interface Progress {
		void onProgress(String what, long done, long total);
	}

	static final class Result {
		final List<File> games = new ArrayList<>();      // the file the emulator opens, one per game
		final List<String> bios = new ArrayList<>();
		final List<String> problems = new ArrayList<>();
	}

	private static final class Picked {
		final Uri uri;
		final String name;
		final long size;
		boolean used;

		Picked(Uri uri, String name, long size) {
			this.uri = uri;
			this.name = name;
			this.size = size;
		}
	}

	private static Picked describe(Context c, Uri u) {
		String name = null;
		long size = -1;
		try (Cursor cur = c.getContentResolver().query(u, new String[] { OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE }, null, null, null)) {
			if (cur != null && cur.moveToFirst()) {
				name = cur.getString(0);
				size = cur.isNull(1) ? -1 : cur.getLong(1);
			}
		} catch (RuntimeException ignored) {
			// Fall back below.
		}
		if (name == null) {
			name = u.getLastPathSegment() == null ? "juego" : u.getLastPathSegment();
		}
		return new Picked(u, name.replace('/', '_'), size);
	}

	private static File newGameDir(Context c, String name) throws IOException {
		File dir = gamesDir(c);
		String slug = base(name).replaceAll("[^A-Za-z0-9 ._()\\-\\[\\]]", "_").trim();
		if (slug.isEmpty()) {
			slug = "PS1";
		}
		if (slug.length() > 80) {
			slug = slug.substring(0, 80);
		}
		File out = new File(dir, slug);
		for (int i = 2; out.exists(); i++) {
			out = new File(dir, slug + " (" + i + ")");
		}
		if (!out.mkdirs()) {
			throw new IOException("No se pudo crear la carpeta del juego");
		}
		return out;
	}

	private static void copy(Context c, Picked p, File dest, Progress progress) throws IOException {
		try (InputStream in = c.getContentResolver().openInputStream(p.uri); OutputStream os = new FileOutputStream(dest)) {
			if (in == null) {
				throw new IOException("No se pudo abrir " + p.name);
			}
			byte[] buf = new byte[1 << 17];
			long done = 0;
			long last = 0;
			int n;
			while ((n = in.read(buf)) > 0) {
				os.write(buf, 0, n);
				done += n;
				if (progress != null && done - last > (8 << 20)) {
					last = done;
					progress.onProgress(p.name, done, p.size);
				}
			}
		}
	}

	/** The files a .cue names (FILE "x.bin" BINARY). */
	static List<String> cueFiles(String cue) {
		List<String> out = new ArrayList<>();
		Matcher m = Pattern.compile("(?im)^\\s*FILE\\s+(?:\"([^\"]+)\"|(\\S+))").matcher(cue);
		while (m.find()) {
			String f = m.group(1) != null ? m.group(1) : m.group(2);
			f = f.replace('\\', '/');
			out.add(f.substring(f.lastIndexOf('/') + 1));
		}
		return out;
	}

	/** A .cue for a lone .bin (one data track: what most single-track games are). */
	static String cueFor(String bin) {
		return "FILE \"" + bin + "\" BINARY\r\n  TRACK 01 MODE2/2352\r\n    INDEX 01 00:00:00\r\n";
	}

	private static String readText(Context c, Uri u) throws IOException {
		try (InputStream in = c.getContentResolver().openInputStream(u)) {
			if (in == null) {
				throw new IOException("No se pudo leer el .cue");
			}
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int n;
			while ((n = in.read(buf)) > 0 && out.size() < 1 << 20) {
				out.write(buf, 0, n);
			}
			return out.toString("ISO-8859-1");
		}
	}

	private static Picked find(List<Picked> picked, String name) {
		for (Picked p : picked) {
			if (p.name.equalsIgnoreCase(name)) {
				return p;
			}
		}
		return null;
	}

	/** Copies everything the player picked: games, BIOS files, archives. */
	static Result importFiles(Context c, List<Uri> uris, Progress progress) {
		Result r = new Result();
		List<Picked> picked = new ArrayList<>();
		for (Uri u : uris) {
			picked.add(describe(c, u));
		}
		// BIOS
		for (Picked p : picked) {
			if (isBiosName(p.name) && (p.size < 0 || p.size <= 4L << 20)) {
				p.used = true;
				try {
					File sys = systemDir(c);
					if (!sys.isDirectory() && !sys.mkdirs()) {
						throw new IOException("No se pudo crear la carpeta de la BIOS");
					}
					copy(c, p, new File(sys, p.name.toLowerCase(Locale.ROOT)), progress);
					r.bios.add(p.name);
				} catch (IOException e) {
					r.problems.add(p.name + ": " + e.getMessage());
				}
			}
		}
		// .cue with their .bin files
		for (Picked p : picked) {
			if (p.used || !"cue".equals(ext(p.name))) {
				continue;
			}
			p.used = true;
			File dir = null;
			try {
				String cue = readText(c, p.uri);
				List<String> missing = new ArrayList<>();
				Map<String, Picked> parts = new LinkedHashMap<>();
				for (String f : cueFiles(cue)) {
					Picked bin = find(picked, f);
					if (bin == null) {
						missing.add(f);
					} else {
						parts.put(f, bin);
					}
				}
				if (!missing.isEmpty()) {
					r.problems.add(p.name + ": faltan " + android.text.TextUtils.join(", ", missing) + ". Elígelos junto con el .cue.");
					continue;
				}
				dir = newGameDir(c, p.name);
				File cueFile = new File(dir, p.name);
				try (OutputStream os = new FileOutputStream(cueFile)) {
					os.write(cue.getBytes(StandardCharsets.ISO_8859_1));
				}
				for (Map.Entry<String, Picked> e : parts.entrySet()) {
					e.getValue().used = true;
					copy(c, e.getValue(), new File(dir, e.getKey()), progress);
				}
				r.games.add(cueFile);
			} catch (IOException | RuntimeException e) {
				Log.w(TAG, "cue " + p.name, e);
				MugenInstaller.deleteTree(dir);
				r.problems.add(p.name + ": " + e.getMessage());
			}
		}
		// Archives, single-file images and lone .bin files
		for (Picked p : picked) {
			if (p.used) {
				continue;
			}
			p.used = true;
			String x = ext(p.name);
			File dir = null;
			try {
				if (x.equals("zip") || x.equals("7z")) {
					dir = newGameDir(c, p.name);
					if (x.equals("zip")) {
						unzip(c, p.uri, dir, progress);
					} else {
						Seven.extract(c, p.uri, dir, progress == null ? null : progress::onProgress);
					}
					File main = findMain(dir);
					if (main == null) {
						throw new IOException("adentro no hay un juego de PS1 (.cue, .bin, .chd, .pbp, .iso…)");
					}
					r.games.add(main);
				} else if (x.equals("bin")) {
					if (p.size >= 0 && p.size < 10L << 20) {
						r.problems.add(p.name + ": es muy pequeño para ser un juego. Si es una BIOS, se llama scph1001.bin, scph5501.bin…");
						continue;
					}
					dir = newGameDir(c, p.name);
					File bin = new File(dir, p.name);
					copy(c, p, bin, progress);
					File cue = new File(dir, base(p.name) + ".cue");
					try (OutputStream os = new FileOutputStream(cue)) {
						os.write(cueFor(p.name).getBytes(StandardCharsets.ISO_8859_1));
					}
					r.games.add(cue);
				} else if (java.util.Arrays.asList(MAIN).contains(x)) {
					dir = newGameDir(c, p.name);
					File f = new File(dir, p.name);
					copy(c, p, f, progress);
					r.games.add(f);
				} else {
					r.problems.add(p.name + ": no es un formato de PS1 (.cue + .bin, .chd, .pbp, .iso, .img, .zip, .7z).");
				}
			} catch (IOException | RuntimeException e) {
				Log.w(TAG, "import " + p.name, e);
				MugenInstaller.deleteTree(dir);
				r.problems.add(p.name + ": " + e.getMessage());
			}
		}
		return r;
	}

	/** The file to open in a folder (and one level down); writes a .cue for a lone .bin. */
	static File findMain(File dir) throws IOException {
		List<File> all = new ArrayList<>();
		collect(dir, all, 0);
		for (String x : MAIN) {
			for (File f : all) {
				if (ext(f.getName()).equals(x) && !isBiosName(f.getName())) {
					if (x.equals("bin")) {
						// A .bin without its .cue: write one.
						File cue = new File(f.getParentFile(), base(f.getName()) + ".cue");
						try (OutputStream os = new FileOutputStream(cue)) {
							os.write(cueFor(f.getName()).getBytes(StandardCharsets.ISO_8859_1));
						}
						return cue;
					}
					return f;
				}
			}
		}
		return null;
	}

	private static void collect(File dir, List<File> out, int depth) {
		File[] kids = dir.listFiles();
		if (kids == null) {
			return;
		}
		java.util.Arrays.sort(kids);
		for (File k : kids) {
			if (k.isFile()) {
				out.add(k);
			}
		}
		if (depth < 2) {
			for (File k : kids) {
				if (k.isDirectory()) {
					collect(k, out, depth + 1);
				}
			}
		}
	}

	private static void unzip(Context c, Uri uri, File dest, Progress p) throws IOException {
		try {
			unzip(c, uri, dest, StandardCharsets.UTF_8, p);
		} catch (IllegalArgumentException badName) {
			unzip(c, uri, dest, Charset.forName("IBM437"), p);
		}
	}

	private static void unzip(Context c, Uri uri, File dest, Charset cs, Progress p) throws IOException {
		String destPath = dest.getCanonicalPath() + File.separator;
		try (InputStream raw = c.getContentResolver().openInputStream(uri)) {
			if (raw == null) {
				throw new IOException("No se pudo abrir el archivo");
			}
			try (ZipInputStream zin = new ZipInputStream(new BufferedInputStream(raw, 1 << 16), cs)) {
				ZipEntry e;
				byte[] buf = new byte[1 << 17];
				long done = 0;
				long last = 0;
				while ((e = zin.getNextEntry()) != null) {
					String n = e.getName().replace('\\', '/');
					File out = new File(dest, n);
					if (!out.getCanonicalPath().startsWith(destPath)) {
						continue;
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
							done += k;
							if (p != null && done - last > (8 << 20)) {
								last = done;
								p.onProgress(n, done, -1);
							}
						}
					}
				}
			}
		}
	}
}
