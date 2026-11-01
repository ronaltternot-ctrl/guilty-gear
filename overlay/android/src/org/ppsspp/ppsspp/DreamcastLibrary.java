package org.ppsspp.ppsspp;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Dreamcast games for Flycast (ArcadeActivity with the "dc" system), through DiscLibrary: a .gdi
 * with its track files, a .cdi or a .chd (one file), a .cue with its .bin, or a .zip / .7z with any
 * of those. The BIOS is optional (Flycast has its own, HLE): dc_boot.bin and dc_flash.bin go to
 * system/dc/, where Flycast looks for them.
 */
final class DreamcastLibrary {
	private DreamcastLibrary() {}

	static final String SYSTEM = "dc";

	static final DiscLibrary.Spec SPEC = new DiscLibrary.Spec(SYSTEM, "Dreamcast", "DC",
		new String[] { "m3u", "gdi", "chd", "cdi", "cue" },
		new String[] { "gdi", "cue" },
		new String[] { "dc_boot.bin", "dc_flash.bin", "dc_bios.bin", "dc_nvmem.bin" }, "dc",
		false, ".gdi con sus pistas, .cdi, .chd, .cue + .bin, .zip, .7z");

	static File gamesDir(Context c) {
		return SPEC.gamesDir(c);
	}

	static File systemDir(Context c) {
		return SPEC.systemDir(c);
	}

	/** dc_boot.bin is in system/dc (else Flycast uses its HLE BIOS). */
	static boolean hasBios(Context c) {
		return new File(SPEC.biosDir(c), "dc_boot.bin").isFile();
	}

	static String title(String fileName) {
		return DiscLibrary.title(fileName, "Dreamcast");
	}

	static DiscLibrary.Result importFiles(Context c, List<Uri> uris, DiscLibrary.Progress progress) {
		return DiscLibrary.importFiles(c, SPEC, uris, progress);
	}

	static File findMain(File dir) throws IOException {
		return DiscLibrary.findMain(SPEC, dir);
	}
}
