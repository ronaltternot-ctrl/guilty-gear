package org.ppsspp.ppsspp;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Checks arcade ZIPs against FBNeo's Neo Geo romsets (assets/arcade/fbneo_neogeo.txt, made by
 * scripts/make_fbneo_db.py) before FBNeo opens them. FBNeo finds a game by the ZIP's name and its
 * files by CRC, in the game's ZIP, its parent's and the BIOS (neogeo.zip). So a ZIP with the
 * right files but another name ("King of Fighters 2002.zip") can be renamed to its set, and
 * missing files (an old neogeo.zip without sp-s3.sp1, a hack without kof2002.zip) can be named
 * before the game is started.
 */
final class ArcadeCheck {
	private ArcadeCheck() {}

	private static final String TAG = "ArcadeCheck";

	static final class SetInfo {
		final String name, parent, board, title;
		final String[] romNames;
		final int[] crcs;
		final boolean[] optional;

		SetInfo(String name, String parent, String board, String title, String[] romNames, int[] crcs, boolean[] optional) {
			this.name = name;
			this.parent = parent;
			this.board = board;
			this.title = title;
			this.romNames = romNames;
			this.crcs = crcs;
			this.optional = optional;
		}
	}

	private static Map<String, SetInfo> sets;

	static synchronized Map<String, SetInfo> sets(Context c) {
		if (sets != null) {
			return sets;
		}
		try (java.io.Reader in = new InputStreamReader(c.getAssets().open("arcade/fbneo_neogeo.txt"), StandardCharsets.UTF_8)) {
			sets = parse(in);
		} catch (IOException | RuntimeException e) {
			Log.w(TAG, "Romset table", e);
			sets = new HashMap<>();
		}
		return sets;
	}

	/** Reads the table made by scripts/make_fbneo_db.py. */
	static Map<String, SetInfo> parse(java.io.Reader in) throws IOException {
		Map<String, SetInfo> out = new HashMap<>();
		BufferedReader r = new BufferedReader(in);
		String line;
		while ((line = r.readLine()) != null) {
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			String[] f = line.split("\t", -1);
			if (f.length < 5) {
				continue;
			}
			String[] roms = f[4].isEmpty() ? new String[0] : f[4].split(",");
			String[] names = new String[roms.length];
			int[] crcs = new int[roms.length];
			boolean[] opt = new boolean[roms.length];
			for (int i = 0; i < roms.length; i++) {
				String[] p = roms[i].split(":");
				names[i] = p[0];
				crcs[i] = p.length > 1 ? (int) Long.parseLong(p[1], 16) : 0;
				opt[i] = p.length > 2 && "o".equals(p[2]);
			}
			out.put(f[0], new SetInfo(f[0], f[1], f[2], f[3], names, crcs, opt));
		}
		return out;
	}

	/** For tests on a computer: the table without an Android Context. */
	static synchronized void setTable(Map<String, SetInfo> table) {
		sets = table;
	}

	static SetInfo info(Context c, String set) {
		return sets(c).get(set);
	}

	/** The CRCs of the files in a ZIP (from its directory, without unpacking), or null. */
	static Set<Integer> zipCrcs(File f) {
		if (f == null || !f.isFile()) {
			return null;
		}
		if (f.getName().toLowerCase(Locale.ROOT).endsWith(".7z")) {
			return Seven.crcs(f);
		}
		if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
			return null;
		}
		Set<Integer> out = new HashSet<>();
		try (ZipFile z = new ZipFile(f)) {
			Enumeration<? extends ZipEntry> en = z.entries();
			while (en.hasMoreElements()) {
				ZipEntry e = en.nextElement();
				if (!e.isDirectory() && e.getCrc() != -1) {
					out.add((int) e.getCrc());
				}
			}
		} catch (IOException | RuntimeException e) {
			Log.w(TAG, "Not a readable zip: " + f, e);
			return null;
		}
		return out;
	}

	/** Archives inside a ZIP (packs that bring kof2002.zip and neogeo.zip together). */
	static List<String> innerArchives(File f) {
		List<String> out = new ArrayList<>();
		try (ZipFile z = new ZipFile(f)) {
			Enumeration<? extends ZipEntry> en = z.entries();
			while (en.hasMoreElements()) {
				ZipEntry e = en.nextElement();
				String n = e.getName().toLowerCase(Locale.ROOT);
				if (!e.isDirectory() && (n.endsWith(".zip") || n.endsWith(".7z"))) {
					out.add(e.getName());
				}
			}
		} catch (IOException | RuntimeException e) {
			return out;
		}
		return out;
	}

	/** The set whose files these are (best match), or null. */
	static SetInfo identify(Context c, Set<Integer> crcs) {
		if (crcs == null || crcs.isEmpty()) {
			return null;
		}
		SetInfo bios = info(c, "neogeo");
		Set<Integer> biosCrcs = new HashSet<>();
		if (bios != null) {
			for (int crc : bios.crcs) {
				biosCrcs.add(crc);
			}
		}
		int gameFiles = 0;
		int biosFiles = 0;
		for (int crc : crcs) {
			if (biosCrcs.contains(crc)) {
				biosFiles++;
			} else {
				gameFiles++;
			}
		}
		if (gameFiles == 0 && biosFiles >= 2) {
			return bios;
		}
		SetInfo best = null;
		int bestCount = 0;
		float bestFit = 0f;
		// A parent set that is complete in the ZIP wins (a merged ZIP also holds its clones).
		SetInfo fullParent = null;
		int fullCount = 0;
		for (SetInfo s : sets(c).values()) {
			if (s == bios) {
				continue;
			}
			int count = 0;
			int required = 0;
			for (int i = 0; i < s.crcs.length; i++) {
				if (!s.optional[i]) {
					required++;
				}
				if (crcs.contains(s.crcs[i]) && !biosCrcs.contains(s.crcs[i])) {
					count++;
				}
			}
			// The set that explains most of the ZIP; among equals, the one the ZIP covers best
			// (a clone's own files, not just the parent's that it shares).
			float fit = required == 0 ? 0f : count / (float) required;
			if (s.parent.isEmpty() && required > 0 && count >= required && count > fullCount) {
				fullParent = s;
				fullCount = count;
			}
			boolean better = count > bestCount || (count == bestCount && fit > bestFit)
				|| (count == bestCount && fit == bestFit && best != null && !best.parent.isEmpty() && s.parent.isEmpty());
			if (better) {
				best = s;
				bestCount = count;
				bestFit = fit;
			}
		}
		if (fullParent != null) {
			return fullParent;
		}
		if (best == null || bestCount < 2 || bestCount < gameFiles * 0.5f) {
			return null;
		}
		return best;
	}

	/** What FBNeo will be missing for one game. */
	static final class Report {
		String set;
		SetInfo info;
		boolean checked;                                   // false for .7z (not readable here)
		final List<String> missing = new ArrayList<>();    // the game's own files
		final List<String> missingParents = new ArrayList<>();
		final List<String> missingBios = new ArrayList<>();
		boolean biosZipMissing;

		boolean ok() {
			return info != null && missing.isEmpty() && missingParents.isEmpty() && missingBios.isEmpty() && !biosZipMissing;
		}

		/** For the player, in Spanish; empty when there is nothing to say. */
		String describe(String fileName) {
			StringBuilder b = new StringBuilder();
			if (info == null) {
				b.append("• «").append(fileName).append("» no es un nombre que FBNeo conozca para un juego de Neo Geo. ")
					.append("FBNeo reconoce los juegos por el nombre del ZIP (por ejemplo kof2002.zip). Si es de otra placa (CPS…), puede funcionar igual.\n");
				return b.toString();
			}
			if (!missingParents.isEmpty()) {
				b.append("• Es una versión de otro juego y necesita también: ").append(join(missingParents, ".zip")).append(" (añádelo con «+ Arcade / Neo Geo»).\n");
			}
			if (!missing.isEmpty()) {
				b.append("• Al ZIP le faltan ").append(missing.size()).append(missing.size() == 1 ? " archivo" : " archivos")
					.append(" que esta versión de FBNeo pide (").append(shortList(missing)).append("). ")
					.append("Suele pasar con ZIP de versiones viejas de MAME: hace falta el romset actual de «").append(set).append("».\n");
			}
			if (biosZipMissing) {
				b.append("• Falta la BIOS ").append(info.board.isEmpty() ? "neogeo" : info.board).append(".zip.\n");
			} else if (!missingBios.isEmpty()) {
				b.append("• Tu neogeo.zip es de una versión vieja: le falta ").append(shortList(missingBios))
					.append(", que FBNeo necesita. Hace falta un neogeo.zip actual (de FBNeo o de un MAME reciente).\n");
			}
			if (!checked) {
				b.append("• La app no pudo revisar lo que tiene adentro (¿archivo dañado?).\n");
			}
			return b.toString();
		}

		private static String join(List<String> l, String suffix) {
			StringBuilder b = new StringBuilder();
			for (int i = 0; i < l.size(); i++) {
				b.append(i == 0 ? "" : ", ").append(l.get(i)).append(suffix);
			}
			return b.toString();
		}

		private static String shortList(List<String> l) {
			StringBuilder b = new StringBuilder();
			for (int i = 0; i < l.size() && i < 4; i++) {
				b.append(i == 0 ? "" : ", ").append(l.get(i));
			}
			if (l.size() > 4) {
				b.append("…");
			}
			return b.toString();
		}
	}

	private static File archive(File dir, String set) {
		File zip = new File(dir, set + ".zip");
		if (zip.isFile()) {
			return zip;
		}
		File sz = new File(dir, set + ".7z");
		return sz.isFile() ? sz : null;
	}

	static Report check(Context c, File rom) {
		Report r = new Report();
		r.set = ArcadeLibrary.setName(rom.getName());
		r.info = info(c, r.set);
		Set<Integer> have = zipCrcs(rom);
		r.checked = have != null;
		if (r.info == null) {
			return r;
		}
		if (have == null) {
			have = new HashSet<>();
		}
		File dir = rom.getParentFile();
		// Parents (a clone of a clone takes files from both).
		Set<String> seen = new HashSet<>();
		SetInfo cur = r.info;
		boolean parentsReadable = true;
		while (cur != null && !cur.parent.isEmpty() && seen.add(cur.parent)) {
			File p = dir == null ? null : archive(dir, cur.parent);
			if (p == null) {
				r.missingParents.add(cur.parent);
			} else {
				Set<Integer> pc = zipCrcs(p);
				if (pc == null) {
					parentsReadable = false;
				} else {
					have.addAll(pc);
				}
			}
			cur = info(c, cur.parent);
		}
		if (r.checked && parentsReadable && r.missingParents.isEmpty()) {
			for (int i = 0; i < r.info.crcs.length; i++) {
				if (!r.info.optional[i] && !have.contains(r.info.crcs[i])) {
					r.missing.add(r.info.romNames[i]);
				}
			}
		}
		// The BIOS.
		String board = r.info.board.isEmpty() ? null : r.info.board;
		SetInfo bios = board == null ? null : info(c, board);
		if (bios != null && dir != null) {
			File bz = archive(dir, board);
			if (bz == null) {
				r.biosZipMissing = true;
			} else {
				Set<Integer> bc = zipCrcs(bz);
				if (bc != null) {
					for (int i = 0; i < bios.crcs.length; i++) {
						if (!bios.optional[i] && !bc.contains(bios.crcs[i]) && !have.contains(bios.crcs[i])) {
							r.missingBios.add(bios.romNames[i]);
						}
					}
				}
			}
		}
		return r;
	}

	/** A readable title: the launcher's short list, else FBNeo's name without the board codes. */
	static String title(Context c, String set) {
		String known = ArcadeLibrary.knownTitle(set);
		if (known != null) {
			return known;
		}
		SetInfo i = info(c, set);
		if (i != null && !i.title.isEmpty()) {
			return i.title.replaceAll("\\s*\\((NGM|NGH|ADM|SAM)[^)]*\\)", "").trim();
		}
		return set.toUpperCase(Locale.ROOT);
	}
}
