package org.ppsspp.ppsspp;

/**
 * The libretro core's memory, through liblibretrodroid.so built with
 * patches/libretrodroid/0002-read-core-memory.patch (loaded by LibretroDroid with the game).
 */
final class RetroMemory {
	private RetroMemory() {}

	/** RETRO_MEMORY_SYSTEM_RAM: the console's work RAM (the SNES's 128 KB). */
	static final int SYSTEM_RAM = 2;

	/**
	 * Copies out.length bytes from offset; false when the core has no such memory, or isn't
	 * running. Waits for the frame being emulated to end. Throws UnsatisfiedLinkError with a
	 * LibretroDroid without the patch.
	 */
	static native boolean read(int id, int offset, byte[] out);
}
