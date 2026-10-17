package org.ppsspp.ppsspp;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Random;

/**
 * Quick test of the phone, run on first launch (and on demand), to suggest the resolution,
 * graphics engine and visual effect.
 *
 * GPU: an offscreen OpenGL ES context draws a fighting-game-like scene (textured, blended
 * layers and sprites) at every internal resolution from 1x to 8x, then runs the app's real
 * post-processing shaders (from assets/shaders) at the size the game takes on this screen.
 * CPU: core count, top clock and a short interpreter-style loop. Plus RAM and Vulkan support.
 *
 * Times are GPU milliseconds per frame measured with glFinish(): an estimate, not the
 * emulator itself, so recommendations keep plenty of margin.
 */
final class DeviceBenchmark {
	private static final String TAG = "AcPlusBenchmark";

	static final int PSP_W = 480;
	static final int PSP_H = 272;
	static final int MAX_RES = 8;

	/** Budget (ms of GPU per 16.7 ms frame) for the game picture and for the whole chain. */
	static final float SCENE_BUDGET = 6.0f;
	static final float TOTAL_SMOOTH = 8.0f;
	static final float TOTAL_TIGHT = 12.0f;

	interface Progress {
		void onProgress(String step, int percent);
	}

	/** Effect ids, as in LauncherActivity.EFFECTS (plus "fsr"). */
	static final String[] EFFECT_IDS = { "arcade", "ia", "ia_rgb", "xbr", "fsr" };
	static final String[] EFFECT_NAMES = { "Arcade / CRT", "IA", "IA máx.", "xBR", "AMD FSR (2× → pantalla)" };

	static final class Result {
		String gpu = "";
		String glVersion = "";
		int cores;
		long maxFreqKHz;
		long ramMB;
		int vulkanVersion;      // 0 = none, else e.g. 0x401000 for 1.1
		int screenW, screenH, gameW, gameH;
		float refresh;
		final float[] sceneMs = new float[MAX_RES + 1];   // index = internal resolution
		final float[] effectMs = new float[EFFECT_IDS.length];
		float cpuMops;
		String error = "";
		long time;

		// Recommendation.
		int recRes = 2;
		String recBackend = "opengl";
		String recEffect = "none";
		boolean recFsr;
		int recFsrScale = 20;
		String tier = "";

		Result() {
			java.util.Arrays.fill(sceneMs, Float.NaN);
			java.util.Arrays.fill(effectMs, Float.NaN);
		}

		String toJson() {
			try {
				JSONObject o = new JSONObject();
				o.put("gpu", gpu).put("gl", glVersion).put("cores", cores).put("freq", maxFreqKHz)
					.put("ram", ramMB).put("vk", vulkanVersion).put("sw", screenW).put("sh", screenH)
					.put("gw", gameW).put("gh", gameH).put("hz", (double) refresh).put("cpu", (double) cpuMops)
					.put("err", error).put("time", time).put("recRes", recRes).put("recBackend", recBackend)
					.put("recEffect", recEffect).put("recFsr", recFsr).put("recFsrScale", recFsrScale).put("tier", tier);
				JSONArray s = new JSONArray();
				for (float v : sceneMs) {
					s.put(Float.isNaN(v) ? -1.0 : (double) v);
				}
				o.put("scene", s);
				JSONArray e = new JSONArray();
				for (float v : effectMs) {
					e.put(Float.isNaN(v) ? -1.0 : (double) v);
				}
				o.put("fx", e);
				return o.toString();
			} catch (Exception e) {
				return "";
			}
		}

		static Result fromJson(String json) {
			if (json == null || json.isEmpty()) {
				return null;
			}
			try {
				JSONObject o = new JSONObject(json);
				Result r = new Result();
				r.gpu = o.optString("gpu");
				r.glVersion = o.optString("gl");
				r.cores = o.optInt("cores");
				r.maxFreqKHz = o.optLong("freq");
				r.ramMB = o.optLong("ram");
				r.vulkanVersion = o.optInt("vk");
				r.screenW = o.optInt("sw");
				r.screenH = o.optInt("sh");
				r.gameW = o.optInt("gw");
				r.gameH = o.optInt("gh");
				r.refresh = (float) o.optDouble("hz", 60);
				r.cpuMops = (float) o.optDouble("cpu", 0);
				r.error = o.optString("err");
				r.time = o.optLong("time");
				r.recRes = o.optInt("recRes", 2);
				r.recBackend = o.optString("recBackend", "opengl");
				r.recEffect = o.optString("recEffect", "none");
				r.recFsr = o.optBoolean("recFsr");
				r.recFsrScale = o.optInt("recFsrScale", 20);
				r.tier = o.optString("tier");
				JSONArray s = o.optJSONArray("scene");
				for (int i = 0; s != null && i < s.length() && i < r.sceneMs.length; i++) {
					double v = s.optDouble(i, -1);
					r.sceneMs[i] = v < 0 ? Float.NaN : (float) v;
				}
				JSONArray e = o.optJSONArray("fx");
				for (int i = 0; e != null && i < e.length() && i < r.effectMs.length; i++) {
					double v = e.optDouble(i, -1);
					r.effectMs[i] = v < 0 ? Float.NaN : (float) v;
				}
				return r;
			} catch (Exception e) {
				return null;
			}
		}

		/** GPU time of an effect including the game picture it needs (1x for most, chosen res for FSR). */
		float effectTotal(int index, int res) {
			float fx = effectMs[index];
			if (Float.isNaN(fx)) {
				return Float.NaN;
			}
			boolean fsr = "fsr".equals(EFFECT_IDS[index]);
			float scene = sceneMs[fsr ? Math.max(1, res) : 1];
			return Float.isNaN(scene) ? Float.NaN : scene + fx;
		}
	}

	private volatile boolean cancelled;

	void cancel() {
		cancelled = true;
	}

	boolean isCancelled() {
		return cancelled;
	}

	// ---------------------------------------------------------------------------------------------

	Result run(Context context, Progress progress) {
		Result r = new Result();
		r.time = System.currentTimeMillis();
		collectDeviceInfo(context, r);
		progress.onProgress("CPU", 5);
		r.cpuMops = cpuTest();
		if (!cancelled) {
			try {
				gpuTests(context, r, progress);
			} catch (Throwable t) {
				Log.w(TAG, "GPU test failed", t);
				r.error = t.getClass().getSimpleName() + ": " + t.getMessage();
			}
		}
		recommend(r);
		progress.onProgress("Listo", 100);
		return r;
	}

	private static void collectDeviceInfo(Context context, Result r) {
		r.cores = Runtime.getRuntime().availableProcessors();
		long best = 0;
		for (int i = 0; i < 16; i++) {
			long f = readLong(new File("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq"));
			best = Math.max(best, f);
		}
		r.maxFreqKHz = best;
		ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		if (am != null) {
			ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
			am.getMemoryInfo(mi);
			r.ramMB = mi.totalMem / (1024 * 1024);
		}
		PackageManager pm = context.getPackageManager();
		if (Build.VERSION.SDK_INT >= 24) {
			for (FeatureInfo f : pm.getSystemAvailableFeatures()) {
				if (PackageManager.FEATURE_VULKAN_HARDWARE_VERSION.equals(f.name)) {
					r.vulkanVersion = f.version;
				}
			}
		}
		WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		DisplayMetrics dm = new DisplayMetrics();
		wm.getDefaultDisplay().getRealMetrics(dm);
		r.screenW = Math.max(dm.widthPixels, dm.heightPixels);
		r.screenH = Math.min(dm.widthPixels, dm.heightPixels);
		r.refresh = wm.getDefaultDisplay().getRefreshRate();
		// The game keeps its shape and fills the short side.
		if ((long) r.screenW * PSP_H >= (long) r.screenH * PSP_W) {
			r.gameH = r.screenH;
			r.gameW = Math.round(r.screenH * (float) PSP_W / PSP_H);
		} else {
			r.gameW = r.screenW;
			r.gameH = Math.round(r.screenW * (float) PSP_H / PSP_W);
		}
	}

	private static long readLong(File f) {
		try (InputStream in = new FileInputStream(f)) {
			byte[] buf = new byte[32];
			int n = in.read(buf);
			return n > 0 ? Long.parseLong(new String(buf, 0, n, StandardCharsets.US_ASCII).trim()) : 0;
		} catch (Exception e) {
			return 0;
		}
	}

	/** A small interpreter loop (decode, branch, ALU, memory), like an emulator's hot path. */
	private static float cpuTest() {
		int[] mem = new int[1 << 16];
		int[] code = new int[256];
		Random rnd = new Random(1234);
		for (int i = 0; i < code.length; i++) {
			code[i] = rnd.nextInt();
		}
		long ops = 0;
		int a = 1, b = 2, c = 3, pc = 0;
		long start = System.nanoTime();
		long end = start + 300_000_000L;
		while (System.nanoTime() < end) {
			for (int k = 0; k < 20000; k++) {
				int op = code[pc & 255];
				switch (op & 7) {
					case 0: a += b ^ op; break;
					case 1: b = (b << 3) | (a >>> 29); break;
					case 2: mem[(a + c) & 0xFFFF] = b; break;
					case 3: c ^= mem[(b + op) & 0xFFFF]; break;
					case 4: a = a * 1103515245 + 12345; break;
					case 5: if ((a & 1) != 0) pc += 3; break;
					case 6: b -= c >> 2; break;
					default: c += a & op; break;
				}
				pc++;
			}
			ops += 20000;
		}
		long ns = System.nanoTime() - start;
		if (a + b + c == 42) {
			Log.d(TAG, "unlikely");
		}
		return ops / (ns / 1000.0f);   // millions of ops per second
	}

	// ---------------------------------------------------------------------------------------------
	// GPU

	private static final String SCENE_VS =
		"attribute vec2 a_pos;\nattribute vec2 a_uv;\nattribute vec4 a_col;\n"
		+ "varying vec2 v_uv;\nvarying vec4 v_col;\n"
		+ "void main() { v_uv = a_uv; v_col = a_col; gl_Position = vec4(a_pos, 0.0, 1.0); }\n";
	private static final String SCENE_FS =
		"precision mediump float;\nuniform sampler2D s;\nvarying vec2 v_uv;\nvarying vec4 v_col;\n"
		+ "void main() { gl_FragColor = texture2D(s, v_uv) * v_col; }\n";

	private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
	private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
	private EGLSurface surface = EGL14.EGL_NO_SURFACE;

	private void gpuTests(Context context, Result r, Progress progress) throws IOException {
		if (!initEgl()) {
			r.error = "No se pudo crear un contexto de OpenGL ES";
			return;
		}
		try {
			r.gpu = String.valueOf(GLES20.glGetString(GLES20.GL_RENDERER));
			r.glVersion = String.valueOf(GLES20.glGetString(GLES20.GL_VERSION));
			int[] maxTex = new int[1];
			GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTex, 0);

			Scene scene = new Scene();
			// Game picture at every internal resolution.
			for (int res = 1; res <= MAX_RES && !cancelled; res++) {
				progress.onProgress("Dibujando el juego a " + res + "×", 8 + res * 6);
				int w = PSP_W * res, h = PSP_H * res;
				if (w > maxTex[0] || h > maxTex[0]) {
					break;
				}
				Target t = new Target(w, h);
				r.sceneMs[res] = measure(() -> scene.draw(t), 30, 350);
				t.release();
				if (r.sceneMs[res] > 45.0f) {
					break;   // far too slow already, no point going higher
				}
			}

			// Effects at the size the game takes on this screen.
			Target src1 = new Target(PSP_W, PSP_H);
			scene.draw(src1);
			Target src2 = new Target(PSP_W * 2, PSP_H * 2);
			scene.draw(src2);
			String vsFxaa = asset(context, "shaders/fxaa.vsh");
			for (int i = 0; i < EFFECT_IDS.length && !cancelled; i++) {
				progress.onProgress("Probando " + EFFECT_NAMES[i], 60 + i * 8);
				try {
					r.effectMs[i] = measureEffect(context, EFFECT_IDS[i], vsFxaa, src1, src2, r.gameW, r.gameH);
				} catch (Throwable t) {
					Log.w(TAG, "Effect " + EFFECT_IDS[i] + " failed", t);
				}
			}
			src1.release();
			src2.release();
			scene.release();
		} finally {
			releaseEgl();
		}
	}

	private boolean initEgl() {
		display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
		int[] version = new int[2];
		if (display == EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(display, version, 0, version, 1)) {
			return false;
		}
		for (int clientVersion : new int[] { 3, 2 }) {
			int renderable = clientVersion == 3 ? 0x40 /* EGL_OPENGL_ES3_BIT_KHR */ : EGL14.EGL_OPENGL_ES2_BIT;
			int[] attribs = {
				EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
				EGL14.EGL_RENDERABLE_TYPE, renderable, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE };
			EGLConfig[] configs = new EGLConfig[1];
			int[] num = new int[1];
			if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0) || num[0] == 0) {
				continue;
			}
			eglContext = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
				new int[] { EGL14.EGL_CONTEXT_CLIENT_VERSION, clientVersion, EGL14.EGL_NONE }, 0);
			if (eglContext == EGL14.EGL_NO_CONTEXT) {
				continue;
			}
			surface = EGL14.eglCreatePbufferSurface(display, configs[0], new int[] { EGL14.EGL_WIDTH, 16, EGL14.EGL_HEIGHT, 16, EGL14.EGL_NONE }, 0);
			if (surface != EGL14.EGL_NO_SURFACE && EGL14.eglMakeCurrent(display, surface, surface, eglContext)) {
				return true;
			}
			releaseEgl();
			display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
			EGL14.eglInitialize(display, version, 0, version, 1);
		}
		return false;
	}

	private void releaseEgl() {
		if (display == EGL14.EGL_NO_DISPLAY) {
			return;
		}
		EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
		if (surface != EGL14.EGL_NO_SURFACE) {
			EGL14.eglDestroySurface(display, surface);
		}
		if (eglContext != EGL14.EGL_NO_CONTEXT) {
			EGL14.eglDestroyContext(display, eglContext);
		}
		EGL14.eglReleaseThread();
		EGL14.eglTerminate(display);
		display = EGL14.EGL_NO_DISPLAY;
		eglContext = EGL14.EGL_NO_CONTEXT;
		surface = EGL14.EGL_NO_SURFACE;
	}

	private float measure(Runnable frame, int maxFrames, long maxMillis) {
		for (int i = 0; i < 3; i++) {
			frame.run();
		}
		GLES20.glFinish();
		long start = System.nanoTime();
		long limit = start + maxMillis * 1_000_000L;
		int n = 0;
		while (n < maxFrames && (n < 4 || System.nanoTime() < limit) && !cancelled) {
			frame.run();
			GLES20.glFinish();
			n++;
		}
		return (System.nanoTime() - start) / 1e6f / Math.max(1, n);
	}

	/** An offscreen color buffer. */
	private static final class Target {
		final int w, h;
		final int tex, fbo;

		Target(int w, int h) {
			this.w = w;
			this.h = h;
			int[] ids = new int[1];
			GLES20.glGenTextures(1, ids, 0);
			tex = ids[0];
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
			GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
			setFilter(true);
			GLES20.glGenFramebuffers(1, ids, 0);
			fbo = ids[0];
			GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
			GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, tex, 0);
		}

		void bind() {
			GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
			GLES20.glViewport(0, 0, w, h);
		}

		void release() {
			GLES20.glDeleteFramebuffers(1, new int[] { fbo }, 0);
			GLES20.glDeleteTextures(1, new int[] { tex }, 0);
		}
	}

	private static void setFilter(boolean linear) {
		int f = linear ? GLES20.GL_LINEAR : GLES20.GL_NEAREST;
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, f);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, f);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
		GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
	}

	/**
	 * A fighting-game-like frame: 3 full-screen background layers, 2 large fighters, 24 effect
	 * sprites, all textured and alpha-blended (about 6 layers of overdraw).
	 */
	private static final class Scene {
		final int program, texture, vbo, vertexCount;

		Scene() {
			program = buildProgram(SCENE_VS, SCENE_FS, new String[] { "a_pos", "a_uv", "a_col" });
			// Texture: noise with soft round alpha, like sprite sheets with effects.
			int size = 256;
			ByteBuffer pixels = ByteBuffer.allocateDirect(size * size * 4);
			Random rnd = new Random(7);
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					float dx = (x % 64) - 32, dy = (y % 64) - 32;
					int alpha = Math.max(0, Math.min(255, (int) (300 - Math.sqrt(dx * dx + dy * dy) * 8)));
					pixels.put((byte) (128 + rnd.nextInt(128))).put((byte) rnd.nextInt(256)).put((byte) (64 + rnd.nextInt(192))).put((byte) alpha);
				}
			}
			pixels.position(0);
			int[] ids = new int[1];
			GLES20.glGenTextures(1, ids, 0);
			texture = ids[0];
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
			GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, size, size, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels);
			setFilter(true);
			GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
			GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);

			// Quads: x, y, u, v, r, g, b, a per vertex, 6 vertices each.
			int quads = 3 + 2 + 24;
			FloatBuffer v = ByteBuffer.allocateDirect(quads * 6 * 8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
			Random q = new Random(42);
			for (int i = 0; i < quads; i++) {
				float x0, y0, w, h, alpha;
				if (i < 3) {
					x0 = -1; y0 = -1; w = 2; h = 2; alpha = i == 0 ? 1.0f : 0.6f;
				} else if (i < 5) {
					w = 0.55f; h = 1.3f; x0 = i == 3 ? -0.75f : 0.2f; y0 = -0.85f; alpha = 1.0f;
				} else {
					w = 0.2f + q.nextFloat() * 0.5f; h = 0.2f + q.nextFloat() * 0.6f;
					x0 = -1 + q.nextFloat() * (2 - w); y0 = -1 + q.nextFloat() * (2 - h); alpha = 0.7f;
				}
				float u0 = q.nextFloat(), v0 = q.nextFloat(), us = 1 + q.nextFloat() * 2;
				float[][] corners = { { x0, y0, 0, 0 }, { x0 + w, y0, 1, 0 }, { x0 + w, y0 + h, 1, 1 },
					{ x0, y0, 0, 0 }, { x0 + w, y0 + h, 1, 1 }, { x0, y0 + h, 0, 1 } };
				for (float[] c : corners) {
					v.put(c[0]).put(c[1]).put(u0 + c[2] * us).put(v0 + c[3] * us)
						.put(0.6f + q.nextFloat() * 0.4f).put(0.6f + q.nextFloat() * 0.4f).put(0.6f + q.nextFloat() * 0.4f).put(alpha);
				}
			}
			v.position(0);
			vertexCount = quads * 6;
			GLES20.glGenBuffers(1, ids, 0);
			vbo = ids[0];
			GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
			GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexCount * 8 * 4, v, GLES20.GL_STATIC_DRAW);
		}

		void draw(Target t) {
			t.bind();
			GLES20.glClearColor(0, 0, 0, 1);
			GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
			GLES20.glEnable(GLES20.GL_BLEND);
			GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
			GLES20.glUseProgram(program);
			GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
			GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "s"), 0);
			GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
			GLES20.glEnableVertexAttribArray(0);
			GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 32, 0);
			GLES20.glEnableVertexAttribArray(1);
			GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 32, 8);
			GLES20.glEnableVertexAttribArray(2);
			GLES20.glVertexAttribPointer(2, 4, GLES20.GL_FLOAT, false, 32, 16);
			GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);
			GLES20.glDisableVertexAttribArray(2);
			GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
			GLES20.glDisable(GLES20.GL_BLEND);
		}

		void release() {
			GLES20.glDeleteProgram(program);
			GLES20.glDeleteTextures(1, new int[] { texture }, 0);
			GLES20.glDeleteBuffers(1, new int[] { vbo }, 0);
		}
	}

	private static int compile(int type, String src) {
		int s = GLES20.glCreateShader(type);
		GLES20.glShaderSource(s, src);
		GLES20.glCompileShader(s);
		int[] ok = new int[1];
		GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
		if (ok[0] == 0) {
			String log = GLES20.glGetShaderInfoLog(s);
			GLES20.glDeleteShader(s);
			throw new IllegalStateException("shader: " + log);
		}
		return s;
	}

	private static int buildProgram(String vs, String fs, String[] attributes) {
		int p = GLES20.glCreateProgram();
		int v = compile(GLES20.GL_VERTEX_SHADER, vs);
		int f = compile(GLES20.GL_FRAGMENT_SHADER, fs);
		GLES20.glAttachShader(p, v);
		GLES20.glAttachShader(p, f);
		for (int i = 0; i < attributes.length; i++) {
			GLES20.glBindAttribLocation(p, i, attributes[i]);
		}
		GLES20.glLinkProgram(p);
		GLES20.glDeleteShader(v);
		GLES20.glDeleteShader(f);
		int[] ok = new int[1];
		GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
		if (ok[0] == 0) {
			String log = GLES20.glGetProgramInfoLog(p);
			GLES20.glDeleteProgram(p);
			throw new IllegalStateException("link: " + log);
		}
		return p;
	}

	private static String asset(Context context, String path) throws IOException {
		try (InputStream in = context.getAssets().open(path)) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[16384];
			int n;
			while ((n = in.read(buf)) > 0) {
				out.write(buf, 0, n);
			}
			return out.toString("UTF-8");
		}
	}

	/** One post-processing pass, like PPSSPP runs them (same uniforms and attributes). */
	private static final class Pass {
		final int program;
		final Target out;
		final float[] settings;

		Pass(String vs, String fs, int w, int h, float[] settings) {
			program = buildProgram(vs, fs, new String[] { "a_position", "a_texcoord0" });
			out = new Target(w, h);
			this.settings = settings;
		}

		void run(int inTex, int inW, int inH, FloatBuffer quad) {
			out.bind();
			GLES20.glUseProgram(program);
			GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inTex);
			uniform1i("sampler0", 0);
			uniform2f("u_texelDelta", 1.0f / inW, 1.0f / inH);
			uniform2f("u_pixelDelta", 1.0f / out.w, 1.0f / out.h);
			int loc = GLES20.glGetUniformLocation(program, "u_setting");
			if (loc >= 0) {
				GLES20.glUniform4fv(loc, 1, settings, 0);
			}
			loc = GLES20.glGetUniformLocation(program, "u_time");
			if (loc >= 0) {
				GLES20.glUniform4f(loc, 1.0f, 0.5f, 30.0f, 30.0f);
			}
			GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
			quad.position(0);
			GLES20.glEnableVertexAttribArray(0);
			GLES20.glVertexAttribPointer(0, 4, GLES20.GL_FLOAT, false, 24, quad);
			quad.position(4);
			GLES20.glEnableVertexAttribArray(1);
			GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 24, quad);
			GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
		}

		private void uniform1i(String name, int v) {
			int loc = GLES20.glGetUniformLocation(program, name);
			if (loc >= 0) {
				GLES20.glUniform1i(loc, v);
			}
		}

		private void uniform2f(String name, float a, float b) {
			int loc = GLES20.glGetUniformLocation(program, name);
			if (loc >= 0) {
				GLES20.glUniform2f(loc, a, b);
			}
		}

		void release() {
			GLES20.glDeleteProgram(program);
			out.release();
		}
	}

	private float measureEffect(Context context, String id, String vsFxaa, Target src1, Target src2, int gw, int gh) throws IOException {
		final FloatBuffer quad = ByteBuffer.allocateDirect(4 * 6 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
		quad.put(new float[] { -1, -1, 0, 1, 0, 0, 1, -1, 0, 1, 1, 0, -1, 1, 0, 1, 0, 1, 1, 1, 0, 1, 1, 1 });
		final Pass[] passes;
		final Target src;
		switch (id) {
			case "arcade":
				src = src1;
				passes = new Pass[] { new Pass(vsFxaa, asset(context, "shaders/acplus_crt_arcade.fsh"), gw, gh, new float[] { 0.2f, 0.8f, 0.35f, 0.35f }) };
				break;
			case "ia":
			case "ia_rgb": {
				src = src1;
				String v = id.equals("ia") ? "shaders/acplus_nnedi3_v.fsh" : "shaders/acplus_nnedi3_rgb_v.fsh";
				String h = id.equals("ia") ? "shaders/acplus_nnedi3_h.fsh" : "shaders/acplus_nnedi3_rgb_h.fsh";
				passes = new Pass[] {
					new Pass(vsFxaa, asset(context, v), PSP_W * 2, PSP_H * 2, new float[4]),
					new Pass(vsFxaa, asset(context, h), PSP_W * 2, PSP_H * 2, new float[4]),
					new Pass(vsFxaa, asset(context, "shaders/acplus_upscale_final.fsh"), gw, gh, new float[4]) };
				break;
			}
			case "xbr":
				src = src1;
				passes = new Pass[] { new Pass(asset(context, "shaders/5xBR.vsh"), asset(context, "shaders/5xBR-lv2.fsh"), gw, gh, new float[4]) };
				break;
			default: {   // fsr: from 2x to the screen, like FSR 2x on a ~1080p phone
				src = src2;
				int ow = Math.min(gw, src2.w * 2), oh = Math.min(gh, src2.h * 2);
				passes = new Pass[] {
					new Pass(vsFxaa, asset(context, "shaders/acplus_fsr1_easu.fsh"), ow, oh, new float[] { 2.0f, 0, 0, 0 }),
					new Pass(vsFxaa, asset(context, "shaders/acplus_fsr1_rcas.fsh"), ow, oh, new float[] { 0.85f, 0, 0, 0 }) };
				break;
			}
		}
		try {
			return measure(() -> {
				int tex = src.tex, w = src.w, h = src.h;
				for (Pass p : passes) {
					p.run(tex, w, h, quad);
					tex = p.out.tex;
					w = p.out.w;
					h = p.out.h;
				}
			}, 30, 350);
		} finally {
			for (Pass p : passes) {
				p.release();
			}
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Recommendations

	/** Highest resolution worth using on this screen (same rule as the launcher). */
	static int screenRes(Result r) {
		return Math.max(2, Math.min(MAX_RES, Math.round(r.screenW / (float) PSP_W)));
	}

	static void recommend(Result r) {
		recommend(r, Build.VERSION.SDK_INT);
	}

	static void recommend(Result r, int sdk) {
		int screenRes = screenRes(r);
		boolean gpuMeasured = !Float.isNaN(r.sceneMs[1]);

		// Resolution: the highest the GPU draws comfortably, up to what the screen can show.
		int res = 1;
		if (gpuMeasured) {
			for (int k = 1; k <= screenRes; k++) {
				if (!Float.isNaN(r.sceneMs[k]) && r.sceneMs[k] <= SCENE_BUDGET) {
					res = k;
				}
			}
		} else {
			res = Math.min(screenRes, r.ramMB >= 4000 ? 4 : 2);
		}
		boolean weakCpu = r.maxFreqKHz > 0 && r.maxFreqKHz < 1_700_000;
		if (weakCpu) {
			res = Math.min(res, 2);
		}
		r.recRes = res;

		// Visual: full resolution if affordable; otherwise FSR to get close to it.
		r.recFsr = false;
		r.recEffect = "none";
		if (gpuMeasured && res < screenRes && res <= 3) {
			int fsr = indexOf("fsr");
			float total = r.effectTotal(fsr, res);
			if (!Float.isNaN(total) && total <= TOTAL_SMOOTH) {
				r.recFsr = true;
				float want = screenRes / (float) res;
				r.recFsrScale = want >= 2.5f ? 30 : want >= 1.75f ? 20 : 15;
			}
		}

		// Engine: Vulkan when the phone has Vulkan 1.1 or newer and the GPU isn't an old one.
		String gpu = r.gpu.toLowerCase(Locale.ROOT);
		boolean oldGpu = gpu.contains("mali-4") || gpu.contains("mali-t") || gpu.contains("sgx") || gpu.matches(".*adreno \\(tm\\) [34]\\d\\d.*");
		r.recBackend = r.vulkanVersion >= 0x401000 && sdk >= 28 && !oldGpu ? "vulkan" : "opengl";

		// Tier from the game picture at 4x (about 1080p).
		float at4 = r.sceneMs[4];
		if (!gpuMeasured) {
			r.tier = "Sin medir";
		} else if (Float.isNaN(at4) || at4 > 8.0f) {
			r.tier = "Básica";
		} else if (at4 > 4.0f) {
			r.tier = "Media";
		} else if (at4 > 2.0f) {
			r.tier = "Alta";
		} else {
			r.tier = "Muy alta";
		}
		if (weakCpu && !r.tier.equals("Básica")) {
			r.tier = r.tier + " (CPU justa)";
		}
	}

	static int indexOf(String effectId) {
		for (int i = 0; i < EFFECT_IDS.length; i++) {
			if (EFFECT_IDS[i].equals(effectId)) {
				return i;
			}
		}
		return -1;
	}

	/** ✓ smooth, ~ tight, ✗ heavy, for an effect's total GPU time. */
	static String rating(float totalMs) {
		if (Float.isNaN(totalMs)) {
			return "—";
		}
		return totalMs <= TOTAL_SMOOTH ? "✓ fluido" : totalMs <= TOTAL_TIGHT ? "≈ justo" : "✗ pesado";
	}

	static String ms(float v) {
		return Float.isNaN(v) ? "—" : String.format(Locale.ROOT, v < 10 ? "%.1f" : "%.0f", v);
	}
}
