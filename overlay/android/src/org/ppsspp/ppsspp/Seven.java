package org.ppsspp.ppsspp;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * .7z archives (Apache Commons Compress with XZ for Java): the CRCs inside an arcade ROM .7z,
 * and unpacking a MUGEN game that comes as .7z. Needs Android 8 (java.nio.file).
 */
final class Seven {
	private Seven() {}

	private static final String TAG = "Seven";

	static boolean supported() {
		return Build.VERSION.SDK_INT >= 26;
	}

	/** The CRCs of the files in a .7z (from its header), or null. */
	@SuppressWarnings("deprecation")
	static Set<Integer> crcs(File f) {
		if (!supported()) {
			return null;
		}
		Set<Integer> out = new HashSet<>();
		try (FileInputStream in = new FileInputStream(f); SevenZFile z = new SevenZFile(in.getChannel())) {
			for (SevenZArchiveEntry e : z.getEntries()) {
				if (!e.isDirectory() && e.getHasCrc()) {
					out.add((int) e.getCrcValue());
				}
			}
		} catch (IOException | RuntimeException | LinkageError e) {
			Log.w(TAG, "Not a readable 7z: " + f, e);
			return null;
		}
		return out;
	}

	/** Unpacks a .7z the player picked into dest. */
	@SuppressWarnings("deprecation")
	static void extract(Context c, Uri uri, File dest, MugenInstaller.Progress p) throws IOException {
		if (!supported()) {
			throw new IOException("Para abrir .7z hace falta Android 8 o más nuevo. Descomprímelo con ZArchiver y elige la carpeta.");
		}
		String destPath = dest.getCanonicalPath() + File.separator;
		File temp = null;
		try (ParcelFileDescriptor pfd = c.getContentResolver().openFileDescriptor(uri, "r")) {
			if (pfd == null) {
				throw new IOException("No se pudo abrir el archivo");
			}
			if (pfd.getStatSize() < 0) {
				// Not a real file (a cloud provider's pipe): 7z needs to jump around, copy it first.
				temp = new File(c.getCacheDir(), "import.7z");
				try (FileInputStream src = new FileInputStream(pfd.getFileDescriptor()); OutputStream os = new FileOutputStream(temp)) {
					byte[] b = new byte[1 << 16];
					int k;
					while ((k = src.read(b)) > 0) {
						os.write(b, 0, k);
					}
				}
			}
			try (FileInputStream in = temp != null ? new FileInputStream(temp) : new FileInputStream(pfd.getFileDescriptor());
				SevenZFile z = new SevenZFile(in.getChannel())) {
				long total = 0;
				for (SevenZArchiveEntry e : z.getEntries()) {
					if (!e.isDirectory()) {
						total += Math.max(0, e.getSize());
					}
				}
				long done = 0;
				int files = 0;
				byte[] buf = new byte[1 << 16];
				SevenZArchiveEntry e;
				while ((e = z.getNextEntry()) != null) {
					String n = e.getName().replace('\\', '/');
					File out = new File(dest, n);
					if (!out.getCanonicalPath().startsWith(destPath)) {
						continue;
					}
					if (e.isDirectory()) {
						if (!out.isDirectory() && !out.mkdirs()) {
							throw new IOException("No se pudo crear " + n);
						}
						continue;
					}
					File parent = out.getParentFile();
					if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
						throw new IOException("No se pudo crear " + parent.getName());
					}
					try (OutputStream os = new FileOutputStream(out)) {
						if (e.hasStream()) {
							int k;
							while ((k = z.read(buf, 0, buf.length)) > 0) {
								os.write(buf, 0, k);
								done += k;
							}
						}
					}
					files++;
					if (p != null && files % 20 == 0) {
						p.onProgress(n, done, total);
					}
				}
			} catch (LinkageError err) {
				throw new IOException("Este teléfono no puede abrir .7z: descomprímelo con ZArchiver y elige la carpeta.", err);
			}
		} finally {
			if (temp != null && !temp.delete()) {
				Log.w(TAG, "Could not delete " + temp);
			}
		}
	}
}
