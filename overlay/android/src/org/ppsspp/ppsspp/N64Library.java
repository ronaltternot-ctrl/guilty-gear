package org.ppsspp.ppsspp;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Nintendo 64 games for Mupen64Plus-Next (ArcadeActivity with the "n64" system), through
 * DiscLibrary: a .z64, .n64 or .v64 ROM, or a .zip / .7z with one (the core doesn't open
 * archives, so they're unpacked). No BIOS: the N64 doesn't have one.
 */
final class N64Library {
	private N64Library() {}

	static final String SYSTEM = "n64";

	static final DiscLibrary.Spec SPEC = new DiscLibrary.Spec(SYSTEM, "Nintendo 64", "N64",
		new String[] { "z64", "n64", "v64" },
		new String[0],
		new String[0], "",
		false, ".z64, .n64, .v64, .zip, .7z");

	static File gamesDir(Context c) {
		return SPEC.gamesDir(c);
	}

	static String title(String fileName) {
		return DiscLibrary.title(fileName, "Nintendo 64");
	}

	static DiscLibrary.Result importFiles(Context c, List<Uri> uris, DiscLibrary.Progress progress) {
		return DiscLibrary.importFiles(c, SPEC, uris, progress);
	}

	static File findMain(File dir) throws IOException {
		return DiscLibrary.findMain(SPEC, dir);
	}
}
