package org.ppsspp.ppsspp;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * File helpers shared by the importers (arcade ROMs, PS1 games, MUGEN games, the player's music):
 * what the system picker returned, copies that never leave half a file behind, and a clear message
 * when the phone runs out of space.
 */
final class FileOps {
	private FileOps() {}

	/** Bytes copied so far. */
	interface Progress {
		void onProgress(long done);
	}

	/** A file from the system picker: its name and size (-1 when the provider doesn't say). */
	static final class Picked {
		final Uri uri;
		final String name;
		final long size;

		Picked(Uri uri, String name, long size) {
			this.uri = uri;
			this.name = name;
			this.size = size;
		}
	}

	/** The files the picker returned: one (getData) or several (getClipData). */
	static List<Uri> uris(Intent data) {
		List<Uri> out = new ArrayList<>();
		if (data == null) {
			return out;
		}
		ClipData clip = data.getClipData();
		if (clip != null) {
			for (int i = 0; i < clip.getItemCount(); i++) {
				Uri u = clip.getItemAt(i).getUri();
				if (u != null && !out.contains(u)) {
					out.add(u);
				}
			}
		}
		if (out.isEmpty() && data.getData() != null) {
			out.add(data.getData());
		}
		return out;
	}

	static Picked describe(Context c, Uri u) {
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
		if (name == null || name.trim().isEmpty()) {
			String last = u.getLastPathSegment();
			name = last == null ? "archivo" : last;
		}
		name = name.replace('\\', '/');
		name = name.substring(name.lastIndexOf('/') + 1);
		return new Picked(u, name.isEmpty() ? "archivo" : name, size);
	}

	/** Copies a stream; reports progress about every 8 MB. Returns the bytes copied. */
	static long copy(InputStream in, OutputStream out, Progress p) throws IOException {
		byte[] buf = new byte[1 << 17];
		long done = 0;
		long last = 0;
		int n;
		while ((n = in.read(buf)) > 0) {
			out.write(buf, 0, n);
			done += n;
			if (p != null && done - last >= (8 << 20)) {
				last = done;
				p.onProgress(done);
			}
		}
		return done;
	}

	/** Copies a picked file to dest, through dest.part: a failed copy leaves no half file. */
	static void copyUri(Context c, Uri uri, File dest, Progress p) throws IOException {
		File parent = dest.getParentFile();
		if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
			throw new IOException("No se pudo crear " + parent.getName());
		}
		File tmp = new File(dest.getPath() + ".part");
		try (InputStream in = c.getContentResolver().openInputStream(uri); OutputStream os = new FileOutputStream(tmp)) {
			if (in == null) {
				throw new IOException("No se pudo abrir " + dest.getName());
			}
			copy(in, os, p);
		} catch (IOException | RuntimeException e) {
			//noinspection ResultOfMethodCallIgnored
			tmp.delete();
			throw friendly(e);
		}
		replace(tmp, dest);
	}

	/** Puts tmp where dest is (replacing an older dest). */
	static void replace(File tmp, File dest) throws IOException {
		if (dest.exists() && !dest.delete()) {
			//noinspection ResultOfMethodCallIgnored
			tmp.delete();
			throw new IOException("No se pudo reemplazar " + dest.getName());
		}
		if (!tmp.renameTo(dest)) {
			//noinspection ResultOfMethodCallIgnored
			tmp.delete();
			throw new IOException("No se pudo guardar " + dest.getName());
		}
	}

	/**
	 * Fails before copying when the folder can't hold `bytes` more (keeps 64 MB free for the
	 * system). Does nothing when the size isn't known.
	 */
	static void requireSpace(File dir, long bytes) throws IOException {
		if (bytes <= 0) {
			return;
		}
		File probe = dir;
		while (probe != null && !probe.exists()) {
			probe = probe.getParentFile();
		}
		if (probe == null) {
			return;
		}
		long free = probe.getUsableSpace();
		long margin = 64L << 20;
		if (free > 0 && free < bytes + margin) {
			throw new IOException("no hay espacio en el teléfono: hacen falta " + mb(bytes + margin) + " y quedan " + mb(free)
				+ ". Libera espacio (fotos, videos, apps) y vuelve a intentarlo.");
		}
	}

	/** The same error, in words the player understands when the phone is full. */
	static IOException friendly(Exception e) {
		String m = e.getMessage() == null ? "" : e.getMessage();
		if (m.contains("ENOSPC") || m.toLowerCase(Locale.ROOT).contains("no space left")) {
			return new IOException("el teléfono se quedó sin espacio. Libera espacio y vuelve a intentarlo.", e);
		}
		if (e instanceof IOException) {
			return (IOException) e;
		}
		return new IOException(m.isEmpty() ? e.getClass().getSimpleName() : m, e);
	}

	static String mb(long bytes) {
		if (bytes >= 1L << 30) {
			return String.format(Locale.ROOT, "%.1f GB", bytes / (double) (1L << 30));
		}
		return Math.max(1, (bytes + (1 << 19)) >> 20) + " MB";
	}
}
