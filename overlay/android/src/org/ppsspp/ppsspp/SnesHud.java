package org.ppsspp.ppsspp;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The SNES fighting games the custom HUD (RetroHud) knows, and where each keeps its values in the
 * console's work RAM (offsets from $7E0000). The addresses come from the games' Pro Action Replay
 * codes and RetroArch's "rumble on energy loss" files (libretro-database), which agree:
 * - Ultimate Mortal Kombat 3: energy $36D4 / $3898 (full: $A6), run meter $36D8 / $389C ($30).
 * - Mortal Kombat 3: energy $3634 / $37F6 ($A6, the same engine).
 * - Mortal Kombat II: energy $2EFC / $30AA ($A1).
 * - Street Fighter II Turbo: health $052F / $072F ($B0), timer $18F3 (BCD).
 * - Super Street Fighter II: health $0530 / $0770 ($B0), timer $1929 (BCD).
 * - Street Fighter Alpha 2: health $073E / $09BE ($90), super $07BE / $0A3E ($90, three levels),
 *   timer $1B7D (BCD).
 * - Killer Instinct: two bars each, $0D24 + $0D26 / $0D28 + $0D2A ($78 each).
 * The names aren't read (1P / 2P), and combos are counted from the health drops.
 */
final class SnesHud {
	private SnesHud() {}

	static final class Game {
		final String id, name;
		final int[] p1, p2;           // health: the bytes add up (Killer Instinct's two bars)
		final int max;
		final int meter1, meter2, meterMax, meterLevels;
		final int timer;              // BCD seconds, or -1
		final RetroHud.Style style;
		/** The RAM read each time: every address, with room for the fight's hash around the players. */
		final int windowStart, windowSize;

		Game(String id, String name, int[] p1, int[] p2, int max, int meter1, int meter2, int meterMax, int meterLevels, int timer,
			RetroHud.Style style) {
			this.id = id;
			this.name = name;
			this.p1 = p1;
			this.p2 = p2;
			this.max = max;
			this.meter1 = meter1;
			this.meter2 = meter2;
			this.meterMax = meterMax;
			this.meterLevels = meterLevels;
			this.timer = timer;
			this.style = style;
			int lo = Integer.MAX_VALUE, hi = 0;
			for (int a : new int[] { p1[0], p2[0], p1[p1.length - 1], p2[p2.length - 1], meter1, meter2, timer }) {
				if (a >= 0) {
					lo = Math.min(lo, a);
					hi = Math.max(hi, a);
				}
			}
			windowStart = Math.max(0, lo - HASH_RANGE);
			windowSize = Math.min(0x20000, hi + HASH_RANGE + 1) - windowStart;
		}

		private int u8(byte[] ram, int addr) {
			return ram[addr - windowStart] & 0xFF;
		}

		private int health(byte[] ram, int[] addrs) {
			int sum = 0;
			for (int a : addrs) {
				sum += u8(ram, a);
			}
			return sum;
		}

		/** Fills s from the window read at windowStart; s.valid only when the values make sense. */
		void read(byte[] ram, RetroHud.Snapshot s) {
			s.style = style;
			int h1 = health(ram, p1), h2 = health(ram, p2);
			// Out of range: not in a fight (or another version of the game).
			if (h1 > max * 5 / 4 || h2 > max * 5 / 4) {
				s.valid = false;
				return;
			}
			s.health[0] = h1;
			s.health[1] = h2;
			s.healthMax[0] = Math.max(max, h1);
			s.healthMax[1] = Math.max(max, h2);
			if (meter1 >= 0) {
				meter(u8(ram, meter1), s, 0);
				meter(u8(ram, meter2), s, 1);
			}
			if (timer >= 0) {
				int v = u8(ram, timer);
				int tens = v >> 4, ones = v & 15;
				s.timer = tens <= 9 && ones <= 9 ? tens * 10 + ones : RetroHud.TIMER_UNKNOWN;
			}
			// The fighters' data around their health changes every frame while a fight runs.
			s.objectsHash = hash(ram, p1[0], hash(ram, p2[0], 0x811C9DC5));
			s.valid = true;
		}

		private void meter(int v, RetroHud.Snapshot s, int i) {
			if (meterLevels > 0) {
				int per = Math.max(1, meterMax / meterLevels);
				int level = Math.min(meterLevels, v / per);
				s.stocks[i] = level;
				s.meter[i] = v >= meterMax ? 1f : (v % per) / (float) per;
			} else {
				s.meter[i] = Math.min(1f, v / (float) meterMax);
			}
		}

		private int hash(byte[] ram, int addr, int h) {
			int from = Math.max(windowStart, addr - HASH_RANGE), to = Math.min(windowStart + windowSize, addr + HASH_RANGE);
			for (int a = from; a < to; a++) {
				h = (h ^ (ram[a - windowStart] & 0xFF)) * 16777619;
			}
			return h;
		}
	}

	private static final int HASH_RANGE = 0x80;

	private static final RetroHud.Style NONE = new RetroHud.Style(null, null, false);
	private static final RetroHud.Style TIMER = new RetroHud.Style(null, null, true);

	static final Game UMK3 = new Game("umk3", "Ultimate Mortal Kombat 3", new int[] { 0x36D4 }, new int[] { 0x3898 }, 0xA6,
		0x36D8, 0x389C, 0x30, 0, -1, new RetroHud.Style("RUN", "Carrera", false));
	static final Game MK3 = new Game("mk3", "Mortal Kombat 3", new int[] { 0x3634 }, new int[] { 0x37F6 }, 0xA6,
		-1, -1, 1, 0, -1, NONE);
	static final Game MK2 = new Game("mk2", "Mortal Kombat II", new int[] { 0x2EFC }, new int[] { 0x30AA }, 0xA1,
		-1, -1, 1, 0, -1, NONE);
	static final Game SF2T = new Game("sf2t", "Street Fighter II Turbo", new int[] { 0x052F }, new int[] { 0x072F }, 0xB0,
		-1, -1, 1, 0, 0x18F3, TIMER);
	static final Game SSF2 = new Game("ssf2", "Super Street Fighter II", new int[] { 0x0530 }, new int[] { 0x0770 }, 0xB0,
		-1, -1, 1, 0, 0x1929, TIMER);
	static final Game SFA2 = new Game("sfa2", "Street Fighter Alpha 2", new int[] { 0x073E }, new int[] { 0x09BE }, 0x90,
		0x07BE, 0x0A3E, 0x90, 3, 0x1B7D, new RetroHud.Style("SUPER", "Súper", true));
	static final Game KI = new Game("ki", "Killer Instinct", new int[] { 0x0D24, 0x0D26 }, new int[] { 0x0D28, 0x0D2A }, 0xF0,
		-1, -1, 1, 0, -1, NONE);

	static final Game[] GAMES = { UMK3, MK3, MK2, SF2T, SSF2, SFA2, KI };

	/** Uppercase letters and digits only. */
	static String flat(String s) {
		return s == null ? "" : s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
	}

	/** The game for a catalog title, a file name and the ROM's own title (any of them can say). */
	static Game identify(String... names) {
		for (String n : names) {
			String f = flat(n);
			if (f.isEmpty()) {
				continue;
			}
			// (The header has 21 characters: "ULTIMATE MORTAL KOMBA".)
			if (f.contains("ULTIMATEMORTALKOMB") || f.contains("UMK3") || f.contains("ULTIMATEMK3")) {
				return UMK3;
			}
			if (f.contains("MORTALKOMBATIII") || f.contains("MORTALKOMBAT3") || f.startsWith("MK3")) {
				return MK3;
			}
			if (f.contains("MORTALKOMBATII") || f.contains("MORTALKOMBAT2") || f.startsWith("MK2")) {
				return MK2;
			}
			if (f.contains("SUPERSTREETFIGHTER")) {
				return SSF2;
			}
			if (f.contains("STREETFIGHTERALPHA2") || f.contains("STREETFIGHTERZERO2")) {
				return SFA2;
			}
			if ((f.contains("STREETFIGHTERII") || f.contains("STREETFIGHTER2")) && f.contains("TURBO")) {
				return SF2T;
			}
			if (f.contains("KILLERINSTINCT")) {
				return KI;
			}
		}
		return null;
	}

	static Game identify(File rom, String title) {
		return identify(title, rom.getName(), headerTitle(rom));
	}

	/**
	 * The title in the ROM's header ("ULTIMATE MORTAL KOMBA…"): LoROM at $7FC0, HiROM at $FFC0,
	 * after a copier's 512-byte header if there is one. The checksum and its complement tell
	 * which one is real.
	 */
	static String headerTitle(File rom) {
		long len = rom.length();
		int skip = len % 1024 == 512 ? 512 : 0;
		String fallback = null;
		try (RandomAccessFile f = new RandomAccessFile(rom, "r")) {
			for (int off : new int[] { 0x7FC0, 0xFFC0 }) {
				if (skip + off + 32 > len) {
					continue;
				}
				byte[] h = new byte[32];
				f.seek(skip + off);
				f.readFully(h);
				int complement = (h[0x1C] & 0xFF) | (h[0x1D] & 0xFF) << 8;
				int checksum = (h[0x1E] & 0xFF) | (h[0x1F] & 0xFF) << 8;
				boolean printable = true;
				for (int i = 0; i < 21; i++) {
					int b = h[i] & 0xFF;
					if (b < 0x20 || b > 0x7E) {
						printable = false;
						break;
					}
				}
				String title = new String(h, 0, 21, StandardCharsets.US_ASCII).trim();
				if (printable && (complement ^ checksum) == 0xFFFF) {
					return title;
				}
				if (printable && fallback == null) {
					fallback = title;
				}
			}
		} catch (IOException | RuntimeException e) {
			return null;
		}
		return fallback;
	}
}
