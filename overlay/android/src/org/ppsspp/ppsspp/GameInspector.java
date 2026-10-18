package org.ppsspp.ppsspp;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Identifies a PSP disc image by reading PARAM.SFO from it.
 * Supports plain ISO and CSO (v1, deflate). Other formats are reported but not inspected.
 */
final class GameInspector {
	private GameInspector() {}

	static final int SECTOR = 2048;

	enum Match { TARGET, BLAZBLUE, KOF, STREET_FIGHTER, OTHER_GUILTY_GEAR, OTHER_GAME, UNKNOWN }

	/** Games with their own controls in the app. */
	static final int KIND_GG = 0;
	static final int KIND_BB = 1;
	static final int KIND_KOF = 2;
	static final int KIND_SF = 3;

	static int kind(String discId, String title) {
		if (isBlazBlue(discId, title)) {
			return KIND_BB;
		}
		if (isStreetFighter(discId, title)) {
			return KIND_SF;
		}
		if (isKof(discId, title)) {
			return KIND_KOF;
		}
		return KIND_GG;
	}

	/** Street Fighter Alpha 3 MAX (US, EU) / Street Fighter Zero 3 Double Upper (JP). */
	static boolean isStreetFighter(String discId, String title) {
		if (discId != null) {
			String id = discId.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
			if (id.equals("ULUS10062") || id.equals("ULES00235") || id.equals("ULJM05082")) {
				return true;
			}
		}
		if (title == null) {
			return false;
		}
		String t = title.toUpperCase(Locale.ROOT);
		return t.contains("STREET FIGHTER ALPHA 3") || t.contains("STREET FIGHTER ZERO 3");
	}

	/** The King of Fighters Collection: The Orochi Saga (US, EU), or any title naming KOF. */
	static boolean isKof(String discId, String title) {
		if (discId != null) {
			String id = discId.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
			if (id.equals("ULUS10360") || id.equals("ULES01191")) {
				return true;
			}
		}
		if (title == null) {
			return false;
		}
		String t = title.toUpperCase(Locale.ROOT);
		return t.contains("KING OF FIGHTERS") || t.contains("KOF");
	}

	/** BlazBlue: Calamity Trigger (US, EU, JP) and Continuum Shift II (US, EU, JP, Asia). */
	private static final String[] BLAZBLUE_IDS = { "ULUS10519", "ULES01497", "ULJM05613", "ULUS10579", "ULES01526", "ULJM05850", "UCAS40337" };

	static boolean isBlazBlue(String discId, String title) {
		if (discId != null) {
			String id = discId.replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
			for (String b : BLAZBLUE_IDS) {
				if (b.equals(id)) {
					return true;
				}
			}
		}
		return title != null && title.toUpperCase(Locale.ROOT).contains("BLAZBLUE");
	}

	static final class Result {
		final String format;
		final String discId;
		final String title;

		Result(String format, String discId, String title) {
			this.format = format;
			this.discId = discId;
			this.title = title;
		}

		Match match() {
			if (isBlazBlue(discId, title)) {
				return Match.BLAZBLUE;
			}
			if (isStreetFighter(discId, title)) {
				return Match.STREET_FIGHTER;
			}
			if (isKof(discId, title)) {
				return Match.KOF;
			}
			if (title == null) {
				return Match.UNKNOWN;
			}
			String t = title.toUpperCase(Locale.ROOT);
			if (t.contains("GUILTY GEAR")) {
				return t.contains("CORE") ? Match.TARGET : Match.OTHER_GUILTY_GEAR;
			}
			return Match.OTHER_GAME;
		}

		/** "ULUS10409" -> "ULUS-10409". */
		String prettyDiscId() {
			if (discId == null) {
				return null;
			}
			String id = discId.trim();
			if (id.length() == 9 && id.indexOf('-') < 0) {
				return id.substring(0, 4) + "-" + id.substring(4);
			}
			return id;
		}
	}

	// ---------------------------------------------------------------------------------------
	// Byte sources

	private interface ByteSource {
		void readFully(long pos, byte[] dst, int off, int len) throws IOException;
	}

	private static final class ChannelSource implements ByteSource {
		private final FileChannel channel;

		ChannelSource(FileChannel channel) {
			this.channel = channel;
		}

		@Override
		public void readFully(long pos, byte[] dst, int off, int len) throws IOException {
			ByteBuffer buffer = ByteBuffer.wrap(dst, off, len);
			while (buffer.hasRemaining()) {
				long at = pos + (buffer.position() - off);
				int n = channel.read(buffer, at);
				if (n < 0) {
					throw new IOException("Unexpected end of file at " + at);
				}
			}
		}
	}

	/** CSO ("CISO") image: blocks of deflate-compressed data with an index table. */
	private static final class CsoSource implements ByteSource {
		private static final int HEADER_SIZE = 24;
		private final ChannelSource raw;
		private final long totalBytes;
		private final int blockSize;
		private final int align;
		private final int version;
		private final Inflater inflater = new Inflater(true);
		private final byte[] block;
		private final byte[] compressed;
		private final byte[] indexBuf = new byte[8];
		private long cachedBlock = -1;

		CsoSource(ChannelSource raw, byte[] header) throws IOException {
			this.raw = raw;
			totalBytes = le64(header, 8);
			blockSize = (int) le32(header, 16);
			version = header[20] & 0xFF;
			align = header[21] & 0xFF;
			if (blockSize < SECTOR || blockSize > (1 << 20) || totalBytes <= 0 || align > 16) {
				throw new IOException("Unsupported CSO header");
			}
			block = new byte[blockSize];
			compressed = new byte[blockSize * 2 + 1024];
		}

		private void loadBlock(long index) throws IOException {
			raw.readFully(HEADER_SIZE + index * 4L, indexBuf, 0, 8);
			long e0 = le32(indexBuf, 0);
			long e1 = le32(indexBuf, 4);
			boolean topBit = (e0 & 0x80000000L) != 0;
			long offset = (e0 & 0x7FFFFFFFL) << align;
			long next = (e1 & 0x7FFFFFFFL) << align;
			long size = next - offset;
			if (size <= 0) {
				throw new IOException("Corrupt CSO index");
			}
			boolean plain;
			if (version >= 2) {
				// In v2, a full-size block is stored raw and the top bit means LZ4.
				plain = size >= blockSize;
				if (!plain && topBit) {
					throw new IOException("LZ4 CSO blocks are not supported for inspection");
				}
			} else {
				plain = topBit;
			}
			if (plain) {
				raw.readFully(offset, block, 0, blockSize);
			} else {
				int toRead = (int) Math.min(size, compressed.length);
				raw.readFully(offset, compressed, 0, toRead);
				inflater.reset();
				inflater.setInput(compressed, 0, toRead);
				int produced = 0;
				try {
					while (produced < blockSize) {
						int n = inflater.inflate(block, produced, blockSize - produced);
						if (n == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) {
							break;
						}
						produced += n;
					}
				} catch (DataFormatException e) {
					throw new IOException("Corrupt CSO block", e);
				}
				for (int i = produced; i < blockSize; i++) {
					block[i] = 0;
				}
			}
			cachedBlock = index;
		}

		@Override
		public void readFully(long pos, byte[] dst, int off, int len) throws IOException {
			while (len > 0) {
				if (pos >= totalBytes) {
					throw new IOException("Read past end of CSO image");
				}
				long index = pos / blockSize;
				int inBlock = (int) (pos % blockSize);
				if (index != cachedBlock) {
					loadBlock(index);
				}
				int n = Math.min(len, blockSize - inBlock);
				System.arraycopy(block, inBlock, dst, off, n);
				pos += n;
				off += n;
				len -= n;
			}
		}
	}

	// ---------------------------------------------------------------------------------------
	// Entry point

	static Result inspect(Context context, Uri uri) throws IOException {
		return inspect(context, uri, null);
	}

	/** Also saves the game's icon (PSP_GAME/ICON0.PNG) to iconOut when given and present. */
	static Result inspect(Context context, Uri uri, java.io.File iconOut) throws IOException {
		try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r")) {
			if (pfd == null) {
				throw new IOException("Could not open file");
			}
			try (FileInputStream stream = new FileInputStream(pfd.getFileDescriptor())) {
				return inspect(new ChannelSource(stream.getChannel()), iconOut);
			}
		}
	}

	static Result inspect(FileChannel channel, java.io.File iconOut) throws IOException {
		return inspect(new ChannelSource(channel), iconOut);
	}

	private static Result inspect(ChannelSource raw, java.io.File iconOut) throws IOException {
		byte[] header = new byte[64];
		raw.readFully(0, header, 0, header.length);
		String magic4 = new String(header, 0, 4, StandardCharsets.US_ASCII);
		String magic8 = new String(header, 0, 8, StandardCharsets.US_ASCII);

		ByteSource source;
		String format;
		if ("CISO".equals(magic4)) {
			source = new CsoSource(raw, header);
			format = "CSO";
		} else if ("ZISO".equals(magic4)) {
			return new Result("ZSO", null, null);
		} else if ("MComprHD".equals(magic8)) {
			return new Result("CHD", null, null);
		} else {
			source = raw;
			format = "ISO";
		}

		byte[] pvd = readSector(source, 16);
		if (pvd[0] != 1 || !"CD001".equals(new String(pvd, 1, 5, StandardCharsets.US_ASCII))) {
			return new Result(format, null, null);
		}
		long rootLba = le32(pvd, 156 + 2);
		long rootSize = le32(pvd, 156 + 10);

		long[] pspGame = findEntry(source, rootLba, rootSize, "PSP_GAME");
		if (pspGame == null || pspGame[2] == 0) {
			return new Result(format, null, null);
		}
		long[] sfo = findEntry(source, pspGame[0], pspGame[1], "PARAM.SFO");
		if (sfo == null || sfo[2] != 0) {
			return new Result(format, null, null);
		}
		int len = (int) Math.min(sfo[1], 64 * 1024);
		byte[] data = new byte[len];
		source.readFully(sfo[0] * SECTOR, data, 0, len);
		String[] fields = parseSfo(data);
		if (fields == null) {
			return new Result(format, null, null);
		}
		if (iconOut != null) {
			try {
				saveIcon(source, pspGame, iconOut);
			} catch (IOException e) {
				// No icon: the catalog draws one.
			}
		}
		return new Result(format, fields[0], fields[1]);
	}

	/** Copies PSP_GAME/ICON0.PNG (144 x 80) out of the image. */
	private static void saveIcon(ByteSource source, long[] pspGame, java.io.File out) throws IOException {
		long[] icon = findEntry(source, pspGame[0], pspGame[1], "ICON0.PNG");
		if (icon == null || icon[2] != 0 || icon[1] < 8 || icon[1] > 1024 * 1024) {
			return;
		}
		byte[] png = new byte[(int) icon[1]];
		source.readFully(icon[0] * SECTOR, png, 0, png.length);
		if ((png[0] & 0xFF) != 0x89 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G') {
			return;
		}
		java.io.File dir = out.getParentFile();
		if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
			throw new IOException("Cannot create " + dir);
		}
		java.io.File tmp = new java.io.File(out.getPath() + ".tmp");
		try (java.io.FileOutputStream os = new java.io.FileOutputStream(tmp)) {
			os.write(png);
		}
		if (!tmp.renameTo(out)) {
			if (!out.delete() || !tmp.renameTo(out)) {
				throw new IOException("Cannot write " + out);
			}
		}
	}

	// ---------------------------------------------------------------------------------------
	// ISO 9660

	private static byte[] readSector(ByteSource source, long lba) throws IOException {
		byte[] sector = new byte[SECTOR];
		source.readFully(lba * SECTOR, sector, 0, SECTOR);
		return sector;
	}

	/** Returns {lba, size, isDirectory} for a name inside a directory extent, or null. */
	private static long[] findEntry(ByteSource source, long dirLba, long dirSize, String wanted) throws IOException {
		long sectors = Math.min((dirSize + SECTOR - 1) / SECTOR, 256);
		for (long s = 0; s < sectors; s++) {
			byte[] sector = readSector(source, dirLba + s);
			int off = 0;
			while (off + 33 <= SECTOR) {
				int recordLen = sector[off] & 0xFF;
				if (recordLen == 0 || off + recordLen > SECTOR) {
					break;  // Rest of this sector is padding.
				}
				int nameLen = sector[off + 32] & 0xFF;
				if (33 + nameLen <= recordLen) {
					String name = new String(sector, off + 33, nameLen, StandardCharsets.US_ASCII);
					int semicolon = name.indexOf(';');
					if (semicolon >= 0) {
						name = name.substring(0, semicolon);
					}
					if (name.endsWith(".")) {
						name = name.substring(0, name.length() - 1);
					}
					if (name.equalsIgnoreCase(wanted)) {
						long lba = le32(sector, off + 2);
						long size = le32(sector, off + 10);
						boolean isDir = (sector[off + 25] & 0x02) != 0;
						return new long[] { lba, size, isDir ? 1 : 0 };
					}
				}
				off += recordLen;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------------------------------
	// PARAM.SFO

	/** Returns {DISC_ID, TITLE} (either may be null), or null if this isn't an SFO. */
	private static String[] parseSfo(byte[] d) {
		if (d.length < 20 || le32(d, 0) != 0x46535000L) {
			return null;
		}
		long keyTable = le32(d, 8);
		long dataTable = le32(d, 12);
		long count = le32(d, 16);
		String discId = null;
		String title = null;
		for (int i = 0; i < count && i < 256; i++) {
			int e = 20 + i * 16;
			if (e + 16 > d.length) {
				break;
			}
			int keyOff = le16(d, e);
			int fmt = le16(d, e + 2);
			long len = le32(d, e + 4);
			long dataOff = le32(d, e + 12);
			String key = cString(d, keyTable + keyOff, 64);
			if (key == null || (fmt != 0x0204 && fmt != 0x0004)) {
				continue;
			}
			long start = dataTable + dataOff;
			if (start < 0 || start >= d.length) {
				continue;
			}
			int n = (int) Math.min(len, d.length - start);
			String value = new String(d, (int) start, n, StandardCharsets.UTF_8);
			int nul = value.indexOf('\0');
			if (nul >= 0) {
				value = value.substring(0, nul);
			}
			value = value.trim();
			if ("DISC_ID".equals(key)) {
				discId = value;
			} else if ("TITLE".equals(key)) {
				title = value;
			}
		}
		return new String[] { discId, title };
	}

	// ---------------------------------------------------------------------------------------
	// Little-endian helpers

	private static int le16(byte[] b, int o) {
		return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
	}

	private static long le32(byte[] b, int o) {
		return (b[o] & 0xFFL) | ((b[o + 1] & 0xFFL) << 8) | ((b[o + 2] & 0xFFL) << 16) | ((b[o + 3] & 0xFFL) << 24);
	}

	private static long le64(byte[] b, int o) {
		return le32(b, o) | (le32(b, o + 4) << 32);
	}

	private static String cString(byte[] b, long start, int max) {
		if (start < 0 || start >= b.length) {
			return null;
		}
		int s = (int) start;
		int end = s;
		while (end < b.length && end - s < max && b[end] != 0) {
			end++;
		}
		return new String(b, s, end - s, StandardCharsets.US_ASCII);
	}
}
