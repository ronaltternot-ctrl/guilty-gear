// AMD FidelityFX Super Resolution 1.0 (FSR 1) - EASU: Edge Adaptive Spatial Upsampling.
//
// Port of FsrEasuF() (32-bit path, no gather) from ffx_fsr1.h,
// https://github.com/GPUOpen-Effects/FidelityFX-FSR, as a PPSSPP post-processing pass for the
// GGXX AC+ launcher. The pass runs at the size of its input times the "Scale" setting
// (OutputScaleSetting=1 in acplus.ini, see patches/0005), and samples the input at texel
// centers, so it works at any source size and on the PSP framebuffer's sub-rectangle.
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
// Positions are computed in input pixels (up to 3840 at 8x): needs full precision.
precision highp float;
#else
precision mediump float;
#endif
#endif

uniform sampler2D sampler0;
uniform vec2 u_texelDelta;   // 1 / input texture size
uniform vec2 u_pixelDelta;   // 1 / output size
varying vec2 v_texcoord0;

// Approximate luma times 2 (as in FSR: B * 0.5 + (R * 0.5 + G)).
float FsrLuma(vec3 c) {
	return c.b * 0.5 + (c.r * 0.5 + c.g);
}

vec3 FsrTap(vec2 fp, vec2 offset) {
	return texture2D(sampler0, (fp + offset + vec2(0.5)) * u_texelDelta).rgb;
}

// Accumulates direction and length for one of the 4 bilinear corners.
//     a
//   b c d
//     e
void FsrEasuSet(inout vec2 dir, inout float len, float w,
		float lA, float lB, float lC, float lD, float lE) {
	float dc = lD - lC;
	float cb = lC - lB;
	float lenX = max(abs(dc), abs(cb));
	lenX = 1.0 / max(lenX, 1.0 / 32768.0);
	float dirX = lD - lB;
	dir.x += dirX * w;
	lenX = clamp(abs(dirX) * lenX, 0.0, 1.0);
	lenX *= lenX;
	len += lenX * w;

	float ec = lE - lC;
	float ca = lC - lA;
	float lenY = max(abs(ec), abs(ca));
	lenY = 1.0 / max(lenY, 1.0 / 32768.0);
	float dirY = lE - lA;
	dir.y += dirY * w;
	lenY = clamp(abs(dirY) * lenY, 0.0, 1.0);
	lenY *= lenY;
	len += lenY * w;
}

// Filters one tap with the approximated, direction-stretched Lanczos-2 kernel.
void FsrEasuTap(inout vec3 aC, inout float aW, vec2 off, vec2 dir, vec2 len,
		float lob, float clp, vec3 c) {
	// Rotate the offset by the direction, then apply the anisotropy.
	vec2 v;
	v.x = off.x * dir.x + off.y * dir.y;
	v.y = off.x * (-dir.y) + off.y * dir.x;
	v *= len;
	// Distance^2, limited to the window.
	float d2 = min(v.x * v.x + v.y * v.y, clp);
	// (25/16 * (2/5 * x^2 - 1)^2 - (25/16 - 1)) * (lob * x^2 - 1)^2
	float wB = 0.4 * d2 - 1.0;
	float wA = lob * d2 - 1.0;
	wB *= wB;
	wA *= wA;
	wB = 1.5625 * wB - 0.5625;
	float w = wB * wA;
	aC += c * w;
	aW += w;
}

void main() {
	vec2 inputSize = vec2(1.0) / u_texelDelta;
	// Position of this output pixel in input pixels, relative to texel centers ('f' below).
	vec2 pp = v_texcoord0 * inputSize - vec2(0.5);
	vec2 fp = floor(pp);
	pp -= fp;

	// 12-tap kernel.
	//     b c
	//   e f g h
	//   i j k l
	//     n o
	vec3 b = FsrTap(fp, vec2(0.0, -1.0));
	vec3 c = FsrTap(fp, vec2(1.0, -1.0));
	vec3 e = FsrTap(fp, vec2(-1.0, 0.0));
	vec3 f = FsrTap(fp, vec2(0.0, 0.0));
	vec3 g = FsrTap(fp, vec2(1.0, 0.0));
	vec3 h = FsrTap(fp, vec2(2.0, 0.0));
	vec3 i = FsrTap(fp, vec2(-1.0, 1.0));
	vec3 j = FsrTap(fp, vec2(0.0, 1.0));
	vec3 k = FsrTap(fp, vec2(1.0, 1.0));
	vec3 l = FsrTap(fp, vec2(2.0, 1.0));
	vec3 n = FsrTap(fp, vec2(0.0, 2.0));
	vec3 o = FsrTap(fp, vec2(1.0, 2.0));

	float bL = FsrLuma(b);
	float cL = FsrLuma(c);
	float eL = FsrLuma(e);
	float fL = FsrLuma(f);
	float gL = FsrLuma(g);
	float hL = FsrLuma(h);
	float iL = FsrLuma(i);
	float jL = FsrLuma(j);
	float kL = FsrLuma(k);
	float lL = FsrLuma(l);
	float nL = FsrLuma(n);
	float oL = FsrLuma(o);

	// Direction and length, bilinearly interpolated from the 4 nearest.
	vec2 dir = vec2(0.0);
	float len = 0.0;
	FsrEasuSet(dir, len, (1.0 - pp.x) * (1.0 - pp.y), bL, eL, fL, gL, jL);
	FsrEasuSet(dir, len, pp.x * (1.0 - pp.y), cL, fL, gL, hL, kL);
	FsrEasuSet(dir, len, (1.0 - pp.x) * pp.y, fL, iL, jL, kL, nL);
	FsrEasuSet(dir, len, pp.x * pp.y, gL, jL, kL, lL, oL);

	// Normalize, with cleanup close to zero.
	vec2 dir2 = dir * dir;
	float dirR = dir2.x + dir2.y;
	bool zro = dirR < 1.0 / 32768.0;
	dirR = inversesqrt(max(dirR, 1.0 / 32768.0));
	dirR = zro ? 1.0 : dirR;
	dir.x = zro ? 1.0 : dir.x;
	dir *= dirR;

	// From {0 to 2} to {0 to 1}, shaped with a square.
	len = len * 0.5;
	len *= len;
	// Stretch the kernel {1.0 vert|horz, to sqrt(2.0) on diagonal}.
	float stretch = (dir.x * dir.x + dir.y * dir.y) / max(abs(dir.x), abs(dir.y));
	// Anisotropic length after rotation: x 1.0 -> stretch on edges, y 1.0 -> 0.5 on edges.
	vec2 len2 = vec2(1.0 + (stretch - 1.0) * len, 1.0 - 0.5 * len);
	// The window goes from +/-sqrt(2.0) to slightly beyond 2.0 depending on the edge amount.
	float lob = 0.5 + ((1.0 / 4.0 - 0.04) - 0.5) * len;
	// Distance^2 clipping point at the end of the adjustable window.
	float clp = 1.0 / lob;

	// Min and max of the 4 nearest, for deringing.
	vec3 min4 = min(min(f, g), min(j, k));
	vec3 max4 = max(max(f, g), max(j, k));

	vec3 aC = vec3(0.0);
	float aW = 0.0;
	FsrEasuTap(aC, aW, vec2(0.0, -1.0) - pp, dir, len2, lob, clp, b);
	FsrEasuTap(aC, aW, vec2(1.0, -1.0) - pp, dir, len2, lob, clp, c);
	FsrEasuTap(aC, aW, vec2(-1.0, 1.0) - pp, dir, len2, lob, clp, i);
	FsrEasuTap(aC, aW, vec2(0.0, 1.0) - pp, dir, len2, lob, clp, j);
	FsrEasuTap(aC, aW, vec2(0.0, 0.0) - pp, dir, len2, lob, clp, f);
	FsrEasuTap(aC, aW, vec2(-1.0, 0.0) - pp, dir, len2, lob, clp, e);
	FsrEasuTap(aC, aW, vec2(1.0, 1.0) - pp, dir, len2, lob, clp, k);
	FsrEasuTap(aC, aW, vec2(2.0, 1.0) - pp, dir, len2, lob, clp, l);
	FsrEasuTap(aC, aW, vec2(2.0, 0.0) - pp, dir, len2, lob, clp, h);
	FsrEasuTap(aC, aW, vec2(1.0, 0.0) - pp, dir, len2, lob, clp, g);
	FsrEasuTap(aC, aW, vec2(1.0, 2.0) - pp, dir, len2, lob, clp, o);
	FsrEasuTap(aC, aW, vec2(0.0, 2.0) - pp, dir, len2, lob, clp, n);

	// Normalize and dering.
	vec3 color = min(max4, max(min4, aC / aW));
	gl_FragColor = vec4(color, 1.0);
}
