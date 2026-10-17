// Tests the hitbox detector against a simulated PSP memory image.
//   g++ -std=c++17 -O2 -I overlay/UI scripts/test_hitbox_core.cpp -o /tmp/test_hitbox && /tmp/test_hitbox
// Variants: the PC +R layout at the cheat-code address, different offsets, and the fighters
// at another address (so the memory scan is exercised). Memory is filled with noise and decoys.

#include <cmath>
#include <cstdio>
#include <random>
#include <vector>

#include "AcPlusHitboxCore.h"

using namespace AcHitbox;

struct SimMem : Mem {
	std::vector<u8> ram;
	SimMem() : ram(kRamEnd - kRamStart) {}
	bool Valid(u32 a, u32 size) const override { return a >= kRamStart && (uint64_t)a + size <= kRamEnd; }
	const u8 *Ptr(u32 a) const override { return &ram[a - kRamStart]; }
	void W8(u32 a, u8 v) { ram[a - kRamStart] = v; }
	void W16(u32 a, u16 v) { memcpy(&ram[a - kRamStart], &v, 2); }
	void W32(u32 a, u32 v) { memcpy(&ram[a - kRamStart], &v, 4); }
};

struct Variant {
	const char *name;
	u32 p1, stride;
	int boxPtr, boxCount, pivot;
	bool idPlusOne;
	bool camera = true;
	bool manual = false;   // pass the right values as launcher overrides
};

struct SimBox { s16 x, y, w, h; u8 type; };

static int Run(const Variant &v, unsigned seed) {
	SimMem m;
	std::mt19937 rng(seed);
	// Noise: random small numbers, some pointers into RAM, zeros.
	for (u32 a = kRamStart; a < kRamEnd; a += 4) {
		u32 r = rng() % 10;
		u32 val;
		if (r < 4) val = 0;
		else if (r < 7) val = rng() % 2000;
		else if (r < 8) val = kRamStart + (rng() % (kRamEnd - kRamStart)) / 4 * 4;
		else val = rng();
		m.W32(a, val);
	}
	const int c1 = 1, c2 = 2;   // Sol vs Ky on the select screen
	m.W8(0x08C6BB58, c1);
	m.W8(0x08C6BB5A, c2);
	// Decoys: the same character pair elsewhere, at the right stride, with garbage around.
	for (int i = 0; i < 20; i++) {
		u32 a = kRamStart + (rng() % (kRamEnd - kRamStart - 0x400)) / 4 * 4;
		m.W16(a, c1 + (v.idPlusOne ? 1 : 0));
		m.W16(a + v.stride, c2 + (v.idPlusOne ? 1 : 0));
	}

	// Animation frames: box lists in RAM.
	std::vector<std::vector<SimBox>> anims = {
		{ {-20, -110, 40, 110, 2}, {-15, -130, 30, 25, 2} },
		{ {-22, -105, 44, 105, 2}, {-15, -128, 30, 25, 2}, {0, 0, 0, 0, 0} },
		{ {-25, -60, 50, 60, 2} },                                // crouch
		{ {-20, -110, 40, 110, 2}, {20, -90, 70, 20, 1} },         // attack
		{ {-18, -90, 36, 70, 2}, {-10, -100, 20, 20, 0x17} },      // air
	};
	std::vector<u32> animAddr;
	u32 dataBase = 0x09500000;
	for (size_t i = 0; i < anims.size(); i++) {
		u32 a = dataBase + (u32)i * 0x100;
		animAddr.push_back(a);
		for (size_t b = 0; b < anims[i].size(); b++) {
			u32 e = a + (u32)b * 12;
			m.W16(e, (u16)anims[i][b].x);
			m.W16(e + 2, (u16)anims[i][b].y);
			m.W16(e + 4, (u16)anims[i][b].w);
			m.W16(e + 6, (u16)anims[i][b].h);
			m.W8(e + 8, anims[i][b].type);
			m.W8(e + 9, 0);
			m.W16(e + 10, 0);
		}
	}
	// Camera struct (+R layout) somewhere in static data.
	const u32 cam = 0x08D20000;
	const s32 camWidth = 64000, camHeight = 36266;

	Detector d;
	if (v.manual) {
		Settings set;
		set.p1 = v.p1;
		set.stride = v.stride;
		set.boxPtr = v.boxPtr;
		set.boxCount = v.boxCount;
		set.pivot = v.pivot;
		d.Configure(set);
	}
	d.SetAnchor(c1, c2);
	s32 x1 = -40000, x2 = 40000, y1 = 0, y2 = 0;
	int ok = 0;
	for (int f = 0; f < 3000; f++) {
		// Movement: P1 walks forward and back, jumps sometimes; P2 drifts.
		double t = f / 60.0;
		x1 = (s32)(-40000 + 30000 * std::sin(t * 0.7));
		x2 = (s32)(40000 + 20000 * std::sin(t * 0.45 + 1.0));
		y1 = (std::fmod(t, 6.0) > 5.0) ? (s32)(-40000 * std::sin((std::fmod(t, 6.0) - 5.0) * M_PI)) : 0;
		y2 = 0;
		int a1 = (y1 < 0) ? 4 : ((f / 20) % 4 == 3 ? 3 : (f / 30) % 2);
		int a2 = ((f / 45) % 3 == 2) ? 2 : (f / 25) % 2;
		for (int p = 0; p < 2; p++) {
			u32 obj = v.p1 + (u32)p * v.stride;
			int anim = p == 0 ? a1 : a2;
			m.W16(obj, (u16)((p == 0 ? c1 : c2) + (v.idPlusOne ? 1 : 0)));
			m.W8(obj + 2, p == 0 ? 0 : 1);
			m.W16(obj + 0x1E, 420);
			m.W32(obj + v.boxPtr, animAddr[anim]);
			m.W8(obj + v.boxCount, (u8)anims[anim].size());
			m.W32(obj + v.pivot, (u32)(p == 0 ? x1 : x2));
			m.W32(obj + v.pivot + 4, (u32)(p == 0 ? y1 : y2));
		}
		s32 mid = (x1 + x2) / 2;
		if (v.camera)
		m.W32(cam + 0x10, (u32)mid);
		m.W32(cam + 0x14, 4000);
		m.W32(cam + 0x20, (u32)(mid - camWidth / 2));
		m.W32(cam + 0x28, (u32)camWidth);
		m.W32(cam + 0x2C, (u32)camHeight);
		d.Step(m);
		if (d.GetPhase() == Detector::RUNNING && (d.CameraAddress() != 0 || (!v.camera && f > 1500))) {
			ok = f;
			break;
		}
	}
	bool offsetsOk = d.P1() == v.p1 && d.Stride() == v.stride && d.BoxPtrOffset() == v.boxPtr &&
		d.BoxCountOffset() == v.boxCount && d.PivotOffset() == v.pivot;
	bool camOk = v.camera ? (d.CameraAddress() == cam + 0x10 && d.CameraFromMemory()) : !d.CameraFromMemory();
	// Projection check: P1's first hurtbox should land where the +R formula puts it.
	std::vector<Box> boxes;
	std::vector<Pivot> pivots;
	d.Collect(m, false, &boxes, &pivots);
	Camera c = d.GetCamera(m);
	bool projOk = false;
	if (!pivots.empty()) {
		float sx, sy;
		Detector::Project(c, (float)pivots[0].x, (float)pivots[0].y, &sx, &sy);
		float ex = (pivots[0].x - (float)((x1 + x2) / 2 - camWidth / 2)) * 480.0f / camWidth;
		float ey = 272.0f + (pivots[0].y - 4000) * 480.0f / camWidth;
		if (!v.camera) {
			// Fallback: centered between the fighters, default scale and ground line.
			ex = 240.0f + (pivots[0].x - ((x1 / 2.0f) + (x2 / 2.0f))) * 0.0075f;
			ey = 248.0f + pivots[0].y * 0.0075f;
		}
		projOk = std::fabs(sx - ex) < 0.5f && std::fabs(sy - ey) < 0.5f;
	}
	printf("%-28s found after %4d frames | offsets %s | camera %s | projection %s | boxes now %zu\n",
		v.name, ok, offsetsOk ? "OK" : "WRONG", camOk ? "OK" : "WRONG", projOk ? "OK" : "WRONG", boxes.size());
	if (!offsetsOk || !camOk || !projOk) {
		printf("  got p1=%08x stride=%x ptr=%x cnt=%x pivot=%x cam=%08x struct=%d\n%s\n", d.P1(), d.Stride(),
			d.BoxPtrOffset(), d.BoxCountOffset(), d.PivotOffset(), d.CameraAddress(), (int)d.CameraFromMemory(), d.Report(m).substr(0, 1500).c_str());
		return 1;
	}
	return 0;
}

int main() {
	Variant variants[] = {
		{ "PC +R layout at cheat addr", 0x0913D000, 0x124, 0x54, 0x84, 0xB0, true },
		{ "shifted offsets", 0x0913D000, 0x124, 0x50, 0x7C, 0xA8, true },
		{ "other address (scan)", 0x09241000, 0x124, 0x54, 0x84, 0xB0, false },
		{ "#Reload-like offsets", 0x0913D000, 0x124, 0x4C, 0x78, 0xA4, false },
		{ "no camera struct (fallback)", 0x0913D000, 0x124, 0x54, 0x84, 0xB0, true, false },
		{ "manual values (launcher)", 0x09300000, 0x130, 0x44, 0x90, 0xC0, false, true, true },
	};
	int failures = 0;
	unsigned seed = 1;
	for (const Variant &v : variants)
		failures += Run(v, seed++);
	printf("failures: %d\n", failures);
	return failures;
}
