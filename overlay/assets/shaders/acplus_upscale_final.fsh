// Last pass of the NNEDI3 chain: resamples the doubled (960x544) image to the screen with
// Catmull-Rom, undoing the half-pixel shift NNEDI3 introduces (source pixel k sits at 2k).
// Part of the GGXX AC+ launcher build.

#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif

uniform sampler2D sampler0;
varying vec2 v_texcoord0;

uniform vec2 u_texelDelta;

vec2 g_size;

vec3 Tex(vec2 index) {
	vec2 c = clamp(index, vec2(0.0), g_size - 1.0) + 0.5;
	return texture2D(sampler0, c * u_texelDelta).rgb;
}

vec3 Row(vec2 base, float dy, vec4 wx) {
	return Tex(base + vec2(-1.0, dy)) * wx.x
		+ Tex(base + vec2(0.0, dy)) * wx.y
		+ Tex(base + vec2(1.0, dy)) * wx.z
		+ Tex(base + vec2(2.0, dy)) * wx.w;
}

vec4 CatmullRom(float f) {
	return vec4(
		f * (-0.5 + f * (1.0 - 0.5 * f)),
		1.0 + f * f * (-2.5 + 1.5 * f),
		f * (0.5 + f * (2.0 - 1.5 * f)),
		f * f * (-0.5 + 0.5 * f));
}

void main() {
	g_size = 1.0 / u_texelDelta;
	// Fractional pixel index in the doubled image, shifted by half a doubled pixel.
	vec2 pos = v_texcoord0 * g_size - 1.0;
	vec2 base = floor(pos);
	vec2 f = pos - base;
	vec4 wx = CatmullRom(f.x);
	vec4 wy = CatmullRom(f.y);
	vec3 color = Row(base, -1.0, wx) * wy.x
		+ Row(base, 0.0, wx) * wy.y
		+ Row(base, 1.0, wx) * wy.z
		+ Row(base, 2.0, wx) * wy.w;

	// Anti-ringing: stay within the four nearest pixels so sprite outlines get no halos.
	vec3 a = Tex(base);
	vec3 b = Tex(base + vec2(1.0, 0.0));
	vec3 c = Tex(base + vec2(0.0, 1.0));
	vec3 d = Tex(base + vec2(1.0, 1.0));
	color = clamp(color, min(min(a, b), min(c, d)), max(max(a, b), max(c, d)));

	gl_FragColor = vec4(color, 1.0);
}
