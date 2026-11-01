package org.ppsspp.ppsspp;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * PlayStation 1 games for PCSX ReARMed (ArcadeActivity with the "psx" system), through
 * DiscLibrary: a .cue with its .bin, a lone .bin (the app writes the .cue), .chd, .pbp, .iso /
 * .img, .m3u, or a .zip / .7z with any of those. BIOS files (scph1001.bin…) go to the system
 * folder, where the emulator uses them instead of its built-in one.
 */
final class PsxLibrary {
	private PsxLibrary() {}

	static final String SYSTEM = "psx";

	static final DiscLibrary.Spec SPEC = new DiscLibrary.Spec(SYSTEM, "PlayStation 1", "PS1",
		new String[] { "m3u", "cue", "chd", "pbp", "ccd", "mds", "iso", "img", "bin" },
		new String[] { "cue" },
		new String[] { "scph*", "psxonpsp*", "ps1_rom*", "openbios*" }, "",
		true, ".cue + .bin, .chd, .pbp, .iso, .img, .zip, .7z");

	static File gamesDir(Context c) {
		return SPEC.gamesDir(c);
	}

	static File systemDir(Context c) {
		return SPEC.systemDir(c);
	}

	static boolean hasBios(Context c) {
		return SPEC.hasBios(c);
	}

	static boolean isBiosName(String name) {
		return SPEC.isBiosName(name);
	}

	static String title(String fileName) {
		return DiscLibrary.title(fileName, "PlayStation");
	}

	static List<String> cueFiles(String cue) {
		return DiscLibrary.cueFiles(cue);
	}

	static String cueFor(String bin) {
		return DiscLibrary.cueFor(bin);
	}

	static DiscLibrary.Result importFiles(Context c, List<Uri> uris, DiscLibrary.Progress progress) {
		return DiscLibrary.importFiles(c, SPEC, uris, progress);
	}

	static File findMain(File dir) throws IOException {
		return DiscLibrary.findMain(SPEC, dir);
	}
}
