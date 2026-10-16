// Scanlines
// Part of the GGXX AC+ launcher build. Original shader for this project.
// Crisp pixels (sharp bilinear) with smooth scanlines on every one of the PSP's 272 lines.
//
// u_setting.x  scanline strength
// u_setting.y  line thickness
// u_setting.z  brightness

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
uniform vec2 u_pixelDelta;
uniform vec4 u_setting;

// PPSSPP renders the game at exactly 480x272 when this shader is selected.
const vec2 GAME_SIZE = vec2(480.0, 272.0);

vec3 Src(vec2 pixel) {
	vec2 c = clamp(pixel, vec2(0.0), GAME_SIZE - 1.0) + 0.5;
	return texture2D(sampler0, c * u_texelDelta).rgb;
}

vec3 ToLinear(vec3 c) {
	return c * c * (c * 0.2 + 0.8);   // cheap gamma 2.2-ish, exact at 0 and 1
}

vec3 ToGamma(vec3 c) {
	return sqrt(max(c, 0.0)) * (1.0 + 0.05 * (1.0 - c));   // inverse-ish of ToLinear
}

vec3 Bilinear(vec2 p) {
	vec2 q = p - 0.5;
	vec2 base = floor(q);
	vec2 f = q - base;
	vec3 a = Src(base);
	vec3 b = Src(base + vec2(1.0, 0.0));
	vec3 c = Src(base + vec2(0.0, 1.0));
	vec3 d = Src(base + vec2(1.0, 1.0));
	return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

void main() {
	vec2 outSize = 1.0 / u_pixelDelta;
	vec2 p = v_texcoord0 / u_texelDelta;
	vec2 scale = max(outSize / GAME_SIZE, vec2(1.0));

	// Sharp bilinear: nearest inside each pixel, smooth only on the boundary.
	vec2 texel = floor(p);
	vec2 s = p - texel;
	vec2 region = 0.5 - 0.5 / scale;
	vec2 d = s - 0.5;
	vec2 f = (d - clamp(d, -region, region)) * scale + 0.5;
	vec3 color = ToLinear(Bilinear(texel + f));

	// Scanline: a smooth dark band between the lines, antialiased for the screen size.
	float y = fract(p.y);
	float thickness = mix(0.25, 0.75, u_setting.y);
	float dist = abs(y - 0.5) * 2.0;   // 0 at the line center, 1 at the gap
	float aa = 1.5 / scale.y;
	float lineMask = 1.0 - smoothstep(thickness - aa, thickness + aa, dist);
	float shade = mix(1.0, lineMask, u_setting.x * 0.85);
	color *= shade * (1.0 + u_setting.x * 0.35) * u_setting.z;

	gl_FragColor = vec4(ToGamma(color), 1.0);
}
