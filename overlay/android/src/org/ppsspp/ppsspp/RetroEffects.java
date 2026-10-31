package org.ppsspp.ppsspp;

import android.content.Context;
import android.util.Log;

import com.swordfish.libretrodroid.ShaderConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Visual effects of the arcade and PS1 screen (ArcadeActivity): the same ones as the PSP games
 * (assets/shaders/: Arcade, CRT, Scanlines, xBR, IA / NNEDI3, AMD FSR), plus LibretroDroid's own.
 *
 * The PSP effects are PPSSPP post-processing shaders. LibretroDroid (rebuilt with
 * patches/libretrodroid/) runs a chain of passes sent as a SHADER_CUSTOM GLRetroShader; each PSP
 * shader is adapted with {@link #port}: PPSSPP's inputs (sampler0, v_texcoord0, u_texelDelta,
 * u_pixelDelta, u_setting) become LibretroDroid's (texture or previousPass, coords, textureSize,
 * screenDensity) through a few #defines, so the shader's own code is the same one the PSP uses.
 */
final class RetroEffects {
	private RetroEffects() {}

	private static final String TAG = "RetroEffects";

	/** LibretroDroid's SHADER_CUSTOM (patches/libretrodroid/0001). */
	static final int SHADER_CUSTOM = 100;

	static final class Effect {
		final String id;
		final String label;
		final String description;
		/** Preview image of the PSP's effects screen (0: none). */
		final int preview;

		Effect(String id, String label, String description, int preview) {
			this.id = id;
			this.label = label;
			this.description = description;
			this.preview = preview;
		}
	}

	static final Effect[] EFFECTS = {
		new Effect("sharp", "Nítida", "Píxeles limpios y cuadrados, sin bordes borrosos. La de siempre.", R.drawable.fx_original),
		new Effect("none", "Original", "La imagen tal cual sale del juego, estirada a la pantalla (algo borrosa).", R.drawable.fx_original),
		new Effect("arcade", "Arcade", "Monitor de recreativa: scanlines marcadas, rejilla RGB, brillo y una curvatura suave.", R.drawable.fx_arcade),
		new Effect("crt", "CRT", "Televisor de tubo: imagen más suave, máscara de puntos y cristal curvo.", R.drawable.fx_crt),
		new Effect("scanlines", "Scanlines", "Píxeles nítidos con líneas de barrido, sin curvatura.", R.drawable.fx_scanlines),
		new Effect("xbr", "xBR", "Reescalado clásico para pixel art: contornos limpios y redondeados. Ligero.", R.drawable.fx_xbr),
		new Effect("ia", "IA", "Red neuronal NNEDI3: duplica la resolución suavizando los bordes en escalera. Pide más al teléfono.", R.drawable.fx_ia),
		new Effect("fsr", "AMD FSR", "AMD FidelityFX Super Resolution 1.0: escala la imagen a tu pantalla y la afina (RCAS).", R.drawable.fx_fsr),
		new Effect("cut", "Suavizada", "Bordes redondeados con el filtro de Lemuroid (CUT).", 0),
		new Effect("lcd", "LCD", "Rejilla fina como la pantalla de una consola portátil.", 0),
	};

	static Effect find(String id) {
		for (Effect e : EFFECTS) {
			if (e.id.equals(id)) {
				return e;
			}
		}
		return EFFECTS[0];
	}

	/** LibretroDroid's own shader for this effect, or null when it's one of the PSP's. */
	static ShaderConfig builtin(String id) {
		switch (id) {
			case "none":
				return ShaderConfig.Default.INSTANCE;
			case "cut":
				return new ShaderConfig.CUT2();
			case "lcd":
				return ShaderConfig.LCD.INSTANCE;
			case "sharp":
				return ShaderConfig.Sharp.INSTANCE;
			default:
				return null;
		}
	}

	/** True when the effect needs OpenGL ES 3 (passes drawn into framebuffers). */
	static boolean needsGles3(String id) {
		return "ia".equals(id) || "fsr".equals(id);
	}

	// -------------------------------------------------------------------------------------------
	// Chains

	static final String VERTEX =
		"attribute vec4 vPosition;\n"
			+ "attribute vec2 vCoordinate;\n"
			+ "varying vec2 coords;\n"
			+ "void main() {\n"
			+ "  coords = vCoordinate;\n"
			+ "  gl_Position = vPosition;\n"
			+ "}\n";

	/** One pass: a PSP shader and how it's fed. */
	static final class Pass {
		final String shader;      // file in assets/shaders/
		final boolean fromPrevious;
		final String inScale;     // the input's size, times the game's frame
		final String outScale;    // this pass's output size, times the game's frame ("screenDensity" on screen)
		final float scale;        // LibretroDroid's framebuffer scale (ignored for the last pass)
		final boolean linear;     // the next pass samples this one with linear filtering
		final float[] settings;

		Pass(String shader, boolean fromPrevious, String inScale, String outScale, float scale, boolean linear, float... settings) {
			this.shader = shader;
			this.fromPrevious = fromPrevious;
			this.inScale = inScale;
			this.outScale = outScale;
			this.scale = scale;
			this.linear = linear;
			this.settings = settings;
		}
	}

	/** Size of the FSR image, times the game's frame: about the size of the game on the screen. */
	static float fsrScale(int screenWidth, int screenHeight) {
		float s = Math.min(screenWidth / 320f, screenHeight / 240f);
		return Math.max(1.5f, Math.min(4f, Math.round(s * 4f) / 4f));
	}

	/** The passes of one of the PSP's effects, or null. */
	static Pass[] passes(String id, float fsrScale) {
		String fsr = fmt(fsrScale);
		switch (id) {
			case "arcade":
				return new Pass[] { new Pass("acplus_crt_arcade.fsh", false, "1.0", "screenDensity", 1f, true, 0.2f, 0.8f, 0.35f, 0.35f) };
			case "crt":
				return new Pass[] { new Pass("acplus_crt_tv.fsh", false, "1.0", "screenDensity", 1f, true, 0.6f, 0.6f, 0.45f, 0.5f) };
			case "scanlines":
				return new Pass[] { new Pass("acplus_scanlines.fsh", false, "1.0", "screenDensity", 1f, true, 0.6f, 0.5f, 1.0f, 0f) };
			case "xbr":
				return new Pass[] { new Pass("5xBR-lv2.fsh", false, "1.0", "screenDensity", 1f, true) };
			case "ia":
				// Rows x2, then columns x2 (into 2x framebuffers), then Catmull-Rom to the screen.
				return new Pass[] {
					new Pass("acplus_nnedi3_v.fsh", false, "1.0", "2.0", 2f, false),
					new Pass("acplus_nnedi3_h.fsh", true, "2.0", "2.0", 2f, false),
					new Pass("acplus_upscale_final.fsh", true, "2.0", "screenDensity", 1f, true),
				};
			case "fsr":
				// EASU to about the screen's size, RCAS at that size, then Catmull-Rom to the screen.
				return new Pass[] {
					new Pass("acplus_fsr1_easu.fsh", false, "1.0", fsr, fsrScale, false),
					new Pass("acplus_fsr1_rcas.fsh", true, fsr, fsr, fsrScale, false, 0.85f, 0f, 0f, 0f),
					new Pass("acplus_upscale_final.fsh", true, fsr, "screenDensity", 1f, true),
				};
			default:
				return null;
		}
	}

	/** The game's frame sampled with linear filtering (the effects that want soft pixels). */
	private static boolean linearInput(String id) {
		return "arcade".equals(id) || "crt".equals(id) || "scanlines".equals(id);
	}

	/**
	 * The params of LibretroDroid's SHADER_CUSTOM for one of the PSP's effects, or null (not one of
	 * them, or a shader file missing).
	 */
	static Map<String, String> chain(Context c, String id, float fsrScale) {
		Pass[] passes = passes(id, fsrScale);
		if (passes == null) {
			return null;
		}
		Map<String, String> params = new HashMap<>();
		params.put("PASSES", String.valueOf(passes.length));
		params.put("LINEAR", linearInput(id) ? "1" : "0");
		for (int i = 0; i < passes.length; i++) {
			Pass p = passes[i];
			String source = asset(c, "shaders/" + p.shader);
			if (source == null) {
				return null;
			}
			params.put("P" + i + "_VERTEX", VERTEX);
			params.put("P" + i + "_FRAGMENT", port(source, p));
			params.put("P" + i + "_SCALE", fmt(p.scale));
			params.put("P" + i + "_LINEAR", p.linear ? "1" : "0");
		}
		return params;
	}

	/** A PPSSPP post-processing shader with LibretroDroid's inputs. */
	static String port(String source, Pass p) {
		String src = p.fromPrevious ? "previousPass" : "texture";
		float[] s = p.settings;
		String setting = String.format(Locale.ROOT, "vec4(%s, %s, %s, %s)",
			fmt(s.length > 0 ? s[0] : 0f), fmt(s.length > 1 ? s[1] : 0f), fmt(s.length > 2 ? s[2] : 0f), fmt(s.length > 3 ? s[3] : 0f));
		StringBuilder out = new StringBuilder();
		out.append("#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n")
			.append("uniform sampler2D ").append(src).append(";\n")
			.append("uniform vec2 textureSize;\n")
			.append("uniform float screenDensity;\n")
			.append("varying vec2 coords;\n")
			.append("#define sampler0 ").append(src).append('\n')
			.append("#define v_texcoord0 coords\n")
			.append("#define u_texelDelta (1.0 / (textureSize * ").append(p.inScale).append("))\n")
			.append("#define u_pixelDelta (1.0 / (textureSize * ").append(p.outScale).append("))\n")
			.append("#define u_setting ").append(setting).append('\n');
		for (String line : source.split("\n", -1)) {
			String t = line.trim();
			if (t.matches("uniform\\s+sampler2D\\s+sampler0\\s*;.*") || t.matches("varying\\s+vec2\\s+v_texcoord0\\s*;.*")
				|| t.matches("uniform\\s+vec2\\s+u_(texelDelta|pixelDelta)\\s*;.*") || t.matches("uniform\\s+vec4\\s+u_setting\\s*;.*")) {
				continue;
			}
			if (t.matches("const\\s+vec2\\s+GAME_SIZE\\s*=.*")) {
				// The PSP's 480 x 272: here the game's frame, whatever its size.
				out.append("#define GAME_SIZE (1.0 / u_texelDelta)\n");
				continue;
			}
			out.append(line).append('\n');
		}
		return out.toString();
	}

	private static String fmt(float v) {
		String s = String.format(Locale.ROOT, "%.4f", v);
		return s.contains(".") ? s : s + ".0";
	}

	private static String asset(Context c, String path) {
		try (InputStream in = c.getAssets().open(path)) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buf = new byte[16384];
			int n;
			while ((n = in.read(buf)) > 0) {
				out.write(buf, 0, n);
			}
			return out.toString("UTF-8");
		} catch (IOException e) {
			Log.w(TAG, "Missing " + path, e);
			return null;
		}
	}
}
