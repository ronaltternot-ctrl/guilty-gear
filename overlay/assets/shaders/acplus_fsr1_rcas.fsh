// AMD FidelityFX Super Resolution 1.0 (FSR 1) - RCAS: Robust Contrast Adaptive Sharpening.
//
// Port of FsrRcasF() (32-bit path) from ffx_fsr1.h,
// https://github.com/GPUOpen-Effects/FidelityFX-FSR, as a PPSSPP post-processing pass for the
// GGXX AC+ launcher. Runs at the size of its input (the EASU output), one output pixel per
// input pixel.
//
// Setting 1 is the sharpening amount as RCAS's lobe scale: 1.0 = AMD's maximum (0 stops),
// 0.5 = 1 stop, 0.25 = 2 stops, 0.0 = no sharpening (FsrRcasCon uses exp2(-stops)).
//
// Copyright (c) 2021 Advanced Micro Devices, Inc. All rights reserved.
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
uniform vec4 u_setting;      // x = sharpness (lobe scale, 0 to 1)
varying vec2 v_texcoord0;

// Limit of the negative lobe: 0.25 - (1.0 / 16.0).
#define FSR_RCAS_LIMIT 0.1875

float FsrLuma(vec3 c) {
	return c.b * 0.5 + (c.r * 0.5 + c.g);
}

void main() {
	// Minimal 3x3 neighborhood.
	//     b
	//   d e f
	//     h
	vec2 dx = vec2(u_texelDelta.x, 0.0);
	vec2 dy = vec2(0.0, u_texelDelta.y);
	vec3 b = texture2D(sampler0, v_texcoord0 - dy).rgb;
	vec3 d = texture2D(sampler0, v_texcoord0 - dx).rgb;
	vec3 e = texture2D(sampler0, v_texcoord0).rgb;
	vec3 f = texture2D(sampler0, v_texcoord0 + dx).rgb;
	vec3 h = texture2D(sampler0, v_texcoord0 + dy).rgb;

	// Min and max of the ring.
	vec3 mn4 = min(min(b, d), min(f, h));
	vec3 mx4 = max(max(b, d), max(f, h));

	// Limiters: how far the lobe can go before clipping, per channel.
	vec2 peakC = vec2(1.0, -4.0);
	vec3 hitMin = min(mn4, e) / max(4.0 * mx4, vec3(1.0 / 4096.0));
	vec3 hitMax = (peakC.x - max(mx4, e)) / min(4.0 * mn4 + peakC.y, vec3(-1.0 / 4096.0));
	vec3 lobeRGB = max(-hitMin, hitMax);
	float lobe = max(-FSR_RCAS_LIMIT, min(max(lobeRGB.r, max(lobeRGB.g, lobeRGB.b)), 0.0));
	lobe *= clamp(u_setting.x, 0.0, 1.0);

#ifdef FSR_RCAS_DENOISE
	// Optional in FSR: less sharpening on isolated (noisy) pixels.
	float bL = FsrLuma(b);
	float dL = FsrLuma(d);
	float eL = FsrLuma(e);
	float fL = FsrLuma(f);
	float hL = FsrLuma(h);
	float nz = 0.25 * bL + 0.25 * dL + 0.25 * fL + 0.25 * hL - eL;
	float range = max(max(max(bL, dL), max(eL, fL)), hL) - min(min(min(bL, dL), min(eL, fL)), hL);
	nz = clamp(abs(nz) / max(range, 1.0 / 4096.0), 0.0, 1.0);
	nz = -0.5 * nz + 1.0;
	lobe *= nz;
#endif

	// Resolve.
	float rcpL = 1.0 / (4.0 * lobe + 1.0);
	vec3 color = (lobe * b + lobe * d + lobe * h + lobe * f + e) * rcpL;
	gl_FragColor = vec4(color, 1.0);
}
