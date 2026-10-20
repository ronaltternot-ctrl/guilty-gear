package org.ppsspp.ppsspp;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Remembers how far the last arcade / MUGEN game got (their processes are separate from the
 * launcher's), so when one closes by itself the launcher can say what happened: the step it
 * reached, why Android ended the process (ApplicationExitInfo, Android 11+), the Java error or,
 * for a native crash, the libraries in the crash report (Android 12+).
 */
final class GameRun {
	private GameRun() {}

	private static final String TAG = "GameRun";

	static final String STEP_PLAYING = "jugando";

	private static File file(Context c) {
		return new File(c.getFilesDir(), "last_game_run.properties");
	}

	private static Properties load(Context c) {
		Properties p = new Properties();
		File f = file(c);
		if (f.isFile()) {
			try (InputStream in = new FileInputStream(f)) {
				p.load(in);
			} catch (IOException | IllegalArgumentException e) {
				Log.w(TAG, "Read", e);
			}
		}
		return p;
	}

	private static void save(Context c, Properties p) {
		try (OutputStream out = new FileOutputStream(file(c))) {
			p.store(out, null);
		} catch (IOException e) {
			Log.w(TAG, "Write", e);
		}
	}

	/** At the start of a game, in its own process (":arcade", ":mugen"). */
	static void begin(Context c, String process, String title, String detail) {
		Properties p = new Properties();
		p.setProperty("process", process);
		p.setProperty("title", title == null ? "" : title);
		p.setProperty("detail", detail == null ? "" : detail);
		p.setProperty("start", String.valueOf(System.currentTimeMillis()));
		p.setProperty("step", "abriendo");
		save(c, p);
		final Context app = c.getApplicationContext();
		final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
			try {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				String trace = sw.toString();
				Properties q = load(app);
				q.setProperty("java", trace.length() > 3000 ? trace.substring(0, 3000) : trace);
				save(app, q);
			} catch (Throwable ignored) {
				// Never fail inside the crash handler.
			}
			if (previous != null) {
				previous.uncaughtException(t, e);
			}
		});
	}

	static void step(Context c, String step) {
		Properties p = load(c);
		if (p.isEmpty()) {
			return;
		}
		p.setProperty("step", step);
		save(c, p);
	}

	/** The game ended normally (the player left it): nothing to report. */
	static void end(Context c) {
		File f = file(c);
		if (f.exists() && !f.delete()) {
			Log.w(TAG, "Could not delete " + f);
		}
	}

	private static boolean running(Context c, String processName) {
		ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
		if (am == null) {
			return false;
		}
		List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
		if (procs == null) {
			return false;
		}
		for (ActivityManager.RunningAppProcessInfo p : procs) {
			if (processName.equals(p.processName)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * In the launcher: if the last game didn't end normally and its process is gone, a text that
	 * says what happened (and forgets the run); otherwise null.
	 */
	static String report(Context c) {
		Properties p = load(c);
		if (p.isEmpty()) {
			return null;
		}
		String processName = c.getPackageName() + p.getProperty("process", "");
		if (running(c, processName)) {
			return null;
		}
		long start = 0;
		try {
			start = Long.parseLong(p.getProperty("start", "0"));
		} catch (NumberFormatException ignored) {
			// Unknown start.
		}
		String step = p.getProperty("step", "");
		String java = p.getProperty("java", "");
		StringBuilder why = new StringBuilder();
		boolean crashed = !java.isEmpty() || "1".equals(p.getProperty("unexpected"));
		if (Build.VERSION.SDK_INT >= 30) {
			ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
			List<ApplicationExitInfo> exits = am == null ? null : am.getHistoricalProcessExitReasons(c.getPackageName(), 0, 16);
			ApplicationExitInfo exit = null;
			if (exits != null) {
				for (ApplicationExitInfo e : exits) {
					if (processName.equals(e.getProcessName()) && e.getTimestamp() >= start - 2000) {
						exit = e;
						break;
					}
				}
			}
			if (exit != null) {
				int reason = exit.getReason();
				switch (reason) {
					case ApplicationExitInfo.REASON_CRASH:
						crashed = true;
						why.append("Error de Java");
						break;
					case ApplicationExitInfo.REASON_CRASH_NATIVE:
						crashed = true;
						why.append("Error nativo (dentro del emulador)");
						break;
					case ApplicationExitInfo.REASON_ANR:
						crashed = true;
						why.append("Dejó de responder");
						break;
					case ApplicationExitInfo.REASON_LOW_MEMORY:
						crashed = true;
						why.append("Android lo cerró por falta de memoria");
						break;
					case ApplicationExitInfo.REASON_SIGNALED:
						crashed |= exit.getStatus() != 9;
						why.append("Señal ").append(exit.getStatus());
						break;
					case ApplicationExitInfo.REASON_EXIT_SELF:
						crashed |= exit.getStatus() != 0;
						why.append("Salió con código ").append(exit.getStatus());
						break;
					case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE:
						crashed = true;
						why.append("No se pudo iniciar el proceso");
						break;
					default:
						why.append("Motivo ").append(reason);
						break;
				}
				String desc = exit.getDescription();
				if (desc != null && !desc.isEmpty()) {
					why.append(" · ").append(desc);
				}
				if (crashed && Build.VERSION.SDK_INT >= 31 && reason == ApplicationExitInfo.REASON_CRASH_NATIVE) {
					String trace = nativeTrace(exit);
					if (!trace.isEmpty()) {
						why.append("\n").append(trace);
					}
				}
			} else if (!STEP_PLAYING.equals(step)) {
				crashed = true;
			}
		} else if (!STEP_PLAYING.equals(step)) {
			crashed = true;
		}
		end(c);
		if (!crashed) {
			return null;
		}
		StringBuilder out = new StringBuilder();
		out.append("Juego: ").append(p.getProperty("title", "")).append('\n');
		String detail = p.getProperty("detail", "");
		if (!detail.isEmpty()) {
			out.append("Archivo: ").append(detail).append('\n');
		}
		out.append("Último paso: ").append(step).append('\n');
		if (why.length() > 0) {
			out.append("Motivo: ").append(why).append('\n');
		}
		out.append("Android ").append(Build.VERSION.RELEASE).append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
		if (!java.isEmpty()) {
			String[] lines = java.split("\n");
			out.append('\n');
			for (int i = 0; i < lines.length && i < 12; i++) {
				out.append(lines[i].trim()).append('\n');
			}
		}
		return out.toString().trim();
	}

	/** The library names and messages in a native crash report (a tombstone, Android 12+). */
	private static String nativeTrace(ApplicationExitInfo exit) {
		if (Build.VERSION.SDK_INT < 31) {
			return "";
		}
		Set<String> found = new LinkedHashSet<>();
		try (InputStream in = exit.getTraceInputStream()) {
			if (in == null) {
				return "";
			}
			byte[] buf = new byte[256 * 1024];
			int len = 0;
			int n;
			while (len < buf.length && (n = in.read(buf, len, buf.length - len)) > 0) {
				len += n;
			}
			StringBuilder run = new StringBuilder();
			for (int i = 0; i <= len; i++) {
				int ch = i < len ? buf[i] & 0xFF : 0;
				if (ch >= 0x20 && ch < 0x7F) {
					run.append((char) ch);
					continue;
				}
				String s = run.toString().trim();
				run.setLength(0);
				if (s.length() >= 6 && (s.contains(".so") || s.startsWith("Abort message") || s.contains("SIG")
					|| s.startsWith("Cause") || s.contains("assert"))) {
					found.add(s.length() > 160 ? s.substring(0, 160) : s);
				}
				if (found.size() >= 14) {
					break;
				}
			}
		} catch (IOException | RuntimeException e) {
			Log.w(TAG, "Native trace", e);
		}
		StringBuilder b = new StringBuilder();
		for (String s : found) {
			b.append(s).append('\n');
		}
		return b.toString().trim();
	}

	/** The game's screen was closed by Android without the player leaving it. */
	static void unexpected(Context c, String step) {
		Properties p = load(c);
		if (p.isEmpty()) {
			return;
		}
		p.setProperty("step", step);
		p.setProperty("unexpected", "1");
		save(c, p);
	}
}
