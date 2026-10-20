// AMD FidelityFX CAS (Contrast Adaptive Sharpening) and a color boost: the last pass of the
// launcher's "BETA · AMD GRAPHICS" chain, after FSR 1.0 (EASU + RCAS). Runs at the size of its
// input, one output pixel per input pixel.
//
// CAS: port of the non-scaling path of CasFilter() (FP32, CAS_BETTER_DIAGONALS) from ffx_cas.h,
// https://github.com/GPUOpen-Effects/FidelityFX-CAS. Each channel is sharpened by an amount
// that adapts to the local contrast, so flat areas and edges near black or white are left
// alone: it adds clarity on top of RCAS without the halos of a plain sharpen.
//
// Setting 1: CAS sharpness, 0 (off) to 1 (CAS's maximum).
// Setting 2: color, 0 (off) to 1: vibrance (more saturation where color is weak) and a soft
//            S-curve of contrast. Not part of AMD's code.
//
// CAS: Copyright (c) 2020 Advanced Micro Devices, Inc. All rights reserved.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#endif

uniform sampler2D sampler0;
uniform vec2 u_texelDelta;   // 1 / input size
uniform vec2 u_pixelDelta;
uniform vec4 u_setting;      // x = CAS sharpness (0 to 1), y = color (0 to 1)
varying vec2 v_texcoord0;

void main() {
	vec2 dx = vec2(u_texelDelta.x, 0.0);
	vec2 dy = vec2(0.0, u_texelDelta.y);
	vec3 e = texture2D(sampler0, v_texcoord0).rgb;
	vec3 color = e;

	float sharp = clamp(u_setting.x, 0.0, 1.0);
	if (sharp > 0.001) {
		// 3x3 neighborhood:
		//  a b c
		//  d e f
		//  g h i
		vec3 a = texture2D(sampler0, v_texcoord0 - dx - dy).rgb;
		vec3 b = texture2D(sampler0, v_texcoord0 - dy).rgb;
		vec3 c = texture2D(sampler0, v_texcoord0 + dx - dy).rgb;
		vec3 d = texture2D(sampler0, v_texcoord0 - dx).rgb;
		vec3 f = texture2D(sampler0, v_texcoord0 + dx).rgb;
		vec3 g = texture2D(sampler0, v_texcoord0 - dx + dy).rgb;
		vec3 h = texture2D(sampler0, v_texcoord0 + dy).rgb;
		vec3 i = texture2D(sampler0, v_texcoord0 + dx + dy).rgb;

		// Soft min and max: the cross plus the whole 3x3 (CAS_BETTER_DIAGONALS).
		vec3 mnRGB = min(min(min(d, e), min(f, b)), h);
		vec3 mnRGB2 = min(mnRGB, min(min(a, c), min(g, i)));
		mnRGB += mnRGB2;
		vec3 mxRGB = max(max(max(d, e), max(f, b)), h);
		vec3 mxRGB2 = max(mxRGB, max(max(a, c), max(g, i)));
		mxRGB += mxRGB2;

		// Distance to the signal limit over the max: how much room there is to sharpen.
		vec3 ampRGB = clamp(min(mnRGB, 2.0 - mxRGB) / max(mxRGB, vec3(1.0 / 4096.0)), 0.0, 1.0);
		ampRGB = sqrt(ampRGB);

		// Filter shape:
		//  0 w 0
		//  w 1 w
		//  0 w 0
		float peak = -1.0 / mix(8.0, 5.0, sharp);
		vec3 wRGB = ampRGB * peak;
		vec3 rcpWeight = 1.0 / (1.0 + 4.0 * wRGB);
		color = clamp((b * wRGB + d * wRGB + f * wRGB + h * wRGB + e) * rcpWeight, 0.0, 1.0);
	}

	float boost = clamp(u_setting.y, 0.0, 1.0);
	if (boost > 0.001) {
		// Vibrance: weak colors gain more saturation than strong ones (skin stays natural).
		float luma = dot(color, vec3(0.299, 0.587, 0.114));
		float sat = max(color.r, max(color.g, color.b)) - min(color.r, min(color.g, color.b));
		color = mix(vec3(luma), color, 1.0 + boost * 0.6 * (1.0 - sat));
		// Soft S-curve: a little more contrast in the midtones.
		color = clamp(color, 0.0, 1.0);
		color = mix(color, color * color * (3.0 - 2.0 * color), 0.3 * boost);
	}

	gl_FragColor = vec4(color, 1.0);
}
