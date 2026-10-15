package org.ppsspp.ppsspp;

/**
 * Shared constants between the launcher (main process) and the emulator activity
 * (":emu" process). Constants only: loading this class has no side effects.
 */
final class LaunchContract {
	private LaunchContract() {}

	/** Boolean extra: the intent comes from LauncherActivity. */
	static final String EXTRA_FROM_LAUNCHER = "org.ppsspp.ppsspp.launcher.FROM_LAUNCHER";
	/** String extra: command line flags for the emulator (already quoted where needed). */
	static final String EXTRA_FLAGS = "org.ppsspp.ppsspp.launcher.FLAGS";
	/** String extra: content:// or file path of the game image to boot. */
	static final String EXTRA_GAME = "org.ppsspp.ppsspp.launcher.GAME";

	/** Name suffix of the process the emulator runs in (see AndroidManifest.xml). */
	static final String EMU_PROCESS_SUFFIX = ":emu";

	/** Class name of the emulator activity, used so the launcher never initializes it. */
	static final String EMU_ACTIVITY = "org.ppsspp.ppsspp.PpssppActivity";
}
