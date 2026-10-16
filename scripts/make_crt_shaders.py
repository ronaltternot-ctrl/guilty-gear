#!/usr/bin/env python3
"""Writes the arcade-monitor / CRT TV / scanline post-processing shaders.

All of them run as the first shader with Upscaling=True, so PPSSPP renders the game at its
original 480x272 and the shader draws every screen pixel itself: the picture is rebuilt
scanline by scanline from the real PSP pixels, the way a CRT would show them. Only the final
image is processed; sprites and textures are untouched.

The four sliders (u_setting) can be tuned in game: Pause -> Settings -> Graphics -> shader.

Usage: python3 scripts/make_crt_shaders.py <output_dir>
"""
import os
import sys

COMMON = r"""
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
"""

CRT = r"""// {title}
// Part of the GGXX AC+ launcher build. Original shader for this project.
//
// u_setting.x  curvature           (0 = flat)
// u_setting.y  scanline strength
// u_setting.z  {mask_name} strength
// u_setting.w  glow
{common}
vec2 Warp(vec2 uv, float amount) {{
	vec2 c = uv * 2.0 - 1.0;
	vec2 k = vec2(0.031, 0.041) * amount;
	c.x *= 1.0 + c.y * c.y * k.x * 4.0;
	c.y *= 1.0 + c.x * c.x * k.y * 4.0;
	return c * 0.5 + 0.5;
}}

// One horizontal line of the picture, reconstructed with a sharp gaussian beam.
vec3 Line(float x, float line) {{
	float px = x - 0.5;
	float x0 = floor(px);
	float f = px - x0;
	vec3 c0 = ToLinear(Src(vec2(x0 - 1.0, line)));
	vec3 c1 = ToLinear(Src(vec2(x0, line)));
	vec3 c2 = ToLinear(Src(vec2(x0 + 1.0, line)));
	vec3 c3 = ToLinear(Src(vec2(x0 + 2.0, line)));
	float w0 = exp2(HARD_PIX * (f + 1.0) * (f + 1.0));
	float w1 = exp2(HARD_PIX * f * f);
	float w2 = exp2(HARD_PIX * (1.0 - f) * (1.0 - f));
	float w3 = exp2(HARD_PIX * (2.0 - f) * (2.0 - f));
	return (c0 * w0 + c1 * w1 + c2 * w2 + c3 * w3) / (w0 + w1 + w2 + w3);
}}

// Brightness profile of the electron beam: bright lines get wider, like a real tube.
vec3 Beam(vec3 color, float dist) {{
	vec3 sigma = mix(vec3(BEAM_MIN), vec3(BEAM_MAX), sqrt(clamp(color, 0.0, 1.0)));
	return color * exp(-(dist * dist) / (2.0 * sigma * sigma));
}}

vec3 Mask(vec2 screenPixel, float strength) {{
	float dim = 1.0 - strength;
{mask_code}
}}

void main() {{
	vec2 outSize = 1.0 / u_pixelDelta;
	vec2 uv = (v_texcoord0 / u_texelDelta) / GAME_SIZE;
	vec2 wuv = Warp(uv, u_setting.x);

	// Outside the curved tube: black, with a soft rounded edge.
	vec2 edge = min(wuv, 1.0 - wuv) * outSize / {edge_softness};
	float border = clamp(min(edge.x, edge.y), 0.0, 1.0);
	if (border <= 0.0) {{
		gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
		return;
	}}

	vec2 p = wuv * GAME_SIZE;
	float py = p.y - 0.5;
	float line = floor(py);
	float f = py - line;

	vec3 a = Line(p.x, line);
	vec3 b = Line(p.x, line + 1.0);
	vec3 beam = Beam(a, f) + Beam(b, 1.0 - f);
	// Without scanlines: plain linear blend of the two lines.
	vec3 soft = mix(a, b, f);
	vec3 color = mix(soft, beam * BEAM_GAIN, u_setting.y);

	// Glow: soft light bleeding from bright areas.
	vec3 glow = ToLinear(Src(floor(p) + vec2(-1.0, -1.0))) + ToLinear(Src(floor(p) + vec2(1.0, -1.0)))
		+ ToLinear(Src(floor(p) + vec2(-1.0, 1.0))) + ToLinear(Src(floor(p) + vec2(1.0, 1.0)))
		+ 2.0 * (ToLinear(Src(floor(p) + vec2(-2.0, 0.0))) + ToLinear(Src(floor(p) + vec2(2.0, 0.0)))
		+ ToLinear(Src(floor(p) + vec2(0.0, -2.0))) + ToLinear(Src(floor(p) + vec2(0.0, 2.0))));
	color += glow * (1.0 / 12.0) * u_setting.w * GLOW_GAIN;

	// Phosphor mask, drawn at the phone's own pixel grid.
	vec2 screenPixel = floor(uv * outSize);
	color *= Mask(screenPixel, u_setting.z);
	// Compensate the light the mask takes away.
	color *= 1.0 + u_setting.z * MASK_BOOST;

	// Vignette.
	vec2 vc = wuv - 0.5;
	color *= 1.0 - dot(vc, vc) * VIGNETTE;

	gl_FragColor = vec4(ToGamma(color) * border, 1.0);
}}
"""

APERTURE_GRILLE = r"""	// Aperture grille: vertical red / green / blue phosphor stripes.
	float i = mod(screenPixel.x, 3.0);
	if (i < 1.0) return vec3(1.0, dim, dim);
	if (i < 2.0) return vec3(dim, 1.0, dim);
	return vec3(dim, dim, 1.0);"""

SHADOW_MASK = r"""	// Shadow mask: phosphor triads, offset on alternate rows like a TV's dot mask.
	float row = floor(screenPixel.y / 2.0);
	float i = mod(screenPixel.x + mod(row, 2.0) * 1.5, 3.0);
	vec3 m;
	if (i < 1.0) m = vec3(1.0, dim, dim);
	else if (i < 2.0) m = vec3(dim, 1.0, dim);
	else m = vec3(dim, dim, 1.0);
	// Thin dark gap between dot rows.
	float gap = mod(screenPixel.y, 2.0) < 1.0 ? 1.0 : mix(1.0, dim, 0.5);
	return m * gap;"""

SCANLINES = r"""// Scanlines
// Part of the GGXX AC+ launcher build. Original shader for this project.
// Crisp pixels (sharp bilinear) with smooth scanlines on every one of the PSP's 272 lines.
//
// u_setting.x  scanline strength
// u_setting.y  line thickness
// u_setting.z  brightness
{common}
vec3 Bilinear(vec2 p) {{
	vec2 q = p - 0.5;
	vec2 base = floor(q);
	vec2 f = q - base;
	vec3 a = Src(base);
	vec3 b = Src(base + vec2(1.0, 0.0));
	vec3 c = Src(base + vec2(0.0, 1.0));
	vec3 d = Src(base + vec2(1.0, 1.0));
	return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}}

void main() {{
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
}}
"""

VARIANTS = {
    "acplus_crt_arcade.fsh": dict(
        title="Arcade monitor (aperture grille, bright glow)",
        mask_name="aperture grille", mask_code=APERTURE_GRILLE, edge_softness="2.0",
        defines={"HARD_PIX": "-5.0", "BEAM_MIN": "0.22", "BEAM_MAX": "0.36",
                 "BEAM_GAIN": "1.55", "GLOW_GAIN": "0.6", "MASK_BOOST": "0.9", "VIGNETTE": "0.35"}),
    "acplus_crt_tv.fsh": dict(
        title="CRT TV (shadow mask, softer picture, curved glass)",
        mask_name="shadow mask", mask_code=SHADOW_MASK, edge_softness="6.0",
        defines={"HARD_PIX": "-1.6", "BEAM_MIN": "0.25", "BEAM_MAX": "0.42",
                 "BEAM_GAIN": "1.4", "GLOW_GAIN": "0.8", "MASK_BOOST": "1.2", "VIGNETTE": "0.8"}),
}


def main(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    for name, v in VARIANTS.items():
        defines = "\n".join("#define %s %s" % kv for kv in v["defines"].items())
        text = CRT.format(title=v["title"], mask_name=v["mask_name"], mask_code=v["mask_code"],
                          edge_softness=v["edge_softness"], common=COMMON + "\n" + defines + "\n")
        with open(os.path.join(out_dir, name), "w", encoding="utf-8") as f:
            f.write(text)
        print("wrote", name)
    with open(os.path.join(out_dir, "acplus_scanlines.fsh"), "w", encoding="utf-8") as f:
        f.write(SCANLINES.format(common=COMMON))
    print("wrote acplus_scanlines.fsh")


if __name__ == "__main__":
    main(sys.argv[1])
