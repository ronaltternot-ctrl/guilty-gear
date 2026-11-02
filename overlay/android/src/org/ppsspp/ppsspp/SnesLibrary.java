package org.ppsspp.ppsspp;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Super Nintendo games for Snes9x (ArcadeActivity with the "snes" system), through DiscLibrary:
 * a .sfc, .smc, .fig, .swc or .bs ROM, or a .zip / .7z with one (the core doesn't open archives,
 * so they're unpacked). No BIOS.
 */
final class SnesLibrary {
	private SnesLibrary() {}

	static final String SYSTEM = "snes";

	static final DiscLibrary.Spec SPEC = new DiscLibrary.Spec(SYSTEM, "Super Nintendo", "SNES",
		new String[] { "sfc", "smc", "fig", "swc", "bs" },
		new String[0],
		new String[0], "",
		false, ".sfc, .smc, .zip, .7z");

	static File gamesDir(Context c) {
		return SPEC.gamesDir(c);
	}

	static String title(String fileName) {
		return DiscLibrary.title(fileName, "Super Nintendo");
	}

	static DiscLibrary.Result importFiles(Context c, List<Uri> uris, DiscLibrary.Progress progress) {
		return DiscLibrary.importFiles(c, SPEC, uris, progress);
	}

	static File findMain(File dir) throws IOException {
		return DiscLibrary.findMain(SPEC, dir);
	}
}
