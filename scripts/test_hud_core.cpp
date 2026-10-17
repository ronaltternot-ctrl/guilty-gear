// Tests the custom HUD's memory reading and tracking, and dumps the 4 designs as primitives
// (JSON) for scripts/render_hud_previews.py.
//   g++ -std=c++17 -O2 -I overlay/UI scripts/test_hud_core.cpp -o /tmp/test_hud && /tmp/test_hud [out.json]

#include <cstdio>
#include <string>
#include <vector>

#include "AcPlusHudCore.h"

using namespace AcHud;

const u32 kRamStart = 0x08800000, kRamEnd = 0x0A000000;

struct SimMem : Mem {
	std::vector<u8> ram;
	SimMem() : ram(kRamEnd - kRamStart) {}
	bool Valid(u32 a, u32 size) const override { return a >= kRamStart && (uint64_t)a + size <= kRamEnd; }
	const u8 *Ptr(u32 a) const override { return &ram[a - kRamStart]; }
	void W8(u32 a, u8 v) { ram[a - kRamStart] = v; }
	void W16(u32 a, u16 v) { memcpy(&ram[a - kRamStart], &v, 2); }
};

struct Game {
	SimMem m;
	int frame = 0;
	void Set(int p, int hp, int tension, int burst, int comboOnOther) {
		m.W16(kP1Object + p * kObjectStride, (u16)(p == 0 ? 2 : 3));   // fighter ids
		m.W16(kP1Object + p * kObjectStride + kObjectHealth, (u16)hp);
		m.W16(kP1Block + p * kBlockStride, (u16)tension);
		m.W16(kP1Block + p * kBlockStride + kBlockBurst, (u16)burst);
		// Combo landed BY player p is stored in the other player's block.
		m.W16(kP1Block + (1 - p) * kBlockStride + kBlockCombo, (u16)comboOnOther);
	}
	void Tick(bool animate) {
		frame++;
		if (animate)
			m.W16(kP1Object + 0x40, (u16)frame);   // animation counters change in a fight
	}
};

static int failures = 0;
static void Expect(const char *what, bool ok, const std::string &detail = "") {
	printf("%-52s %s %s\n", what, ok ? "OK   " : "WRONG", detail.c_str());
	if (!ok)
		failures++;
}

int main(int argc, char **argv) {
	Game g;
	g.m.W8(kSelect[0], 1);
	g.m.W8(kSelect[1], 2);
	g.m.W16(kTimer, 5940);
	g.Set(0, 420, 0, 15000, 0);
	g.Set(1, 420, 0, 15000, 0);
	Tracker tr;
	double t = 0;
	auto step = [&](bool animate) {
		g.Tick(animate);
		t += 1.0 / 60.0;
		tr.Update(Read(g.m), t);
	};

	Snapshot s0 = Read(g.m);
	Expect("snapshot valid with sane values", s0.valid);
	Expect("addresses: P1 combo at 0x08C6BDC4 (CWCheat)", kP1Block + kBlockStride + kBlockCombo == 0x08C6BDC4);
	Expect("addresses: P1 burst at 0x08C6BC74 (CWCheat)", kP1Block + kBlockBurst == 0x08C6BC74);
	Expect("addresses: P2 health at 0x0913D142", kP1Object + kObjectStride + kObjectHealth == 0x0913D142);

	for (int i = 0; i < 120; i++)
		step(false);
	Expect("frozen fighters (menu): hidden", tr.visible == 0.0f);
	for (int i = 0; i < 30; i++)
		step(true);
	Expect("fight running: shown", tr.visible == 1.0f);

	// P1 lands a 5-hit combo, 20 damage per hit, one hit every 6 frames.
	for (int h = 1; h <= 5; h++) {
		g.Set(1, 420 - 20 * h, 0, 15000, 0);
		g.Set(0, 420, 0, 15000, h);
		for (int i = 0; i < 6; i++)
			step(true);
	}
	Expect("combo: 5 hits", tr.p[0].comboHits == 5 && tr.p[0].comboShown, std::to_string(tr.p[0].comboHits));
	Expect("combo: 100 damage", tr.p[0].comboDamage == 100, std::to_string(tr.p[0].comboDamage));
	Expect("trail holds the old health right after the hits", tr.p[1].trail > tr.p[1].health + 50, std::to_string(tr.p[1].trail));
	g.Set(0, 420, 0, 15000, 0);   // combo over
	for (int i = 0; i < 30; i++)
		step(true);
	Expect("combo stays on screen a moment after it ends", tr.p[0].comboShown);
	for (int i = 0; i < 90; i++)
		step(true);
	Expect("then it goes away", !tr.p[0].comboShown);
	Expect("last combo kept for the Data design", tr.p[0].lastComboHits == 5 && tr.p[0].lastComboDamage == 100);
	Expect("trail drained to the health", tr.p[1].trail == tr.p[1].health, std::to_string(tr.p[1].trail));

	// Second combo by P1: damage counts from the health before it.
	for (int h = 1; h <= 3; h++) {
		g.Set(1, 320 - 10 * h, 0, 15000, 0);
		g.Set(0, 420, 0, 15000, h);
		for (int i = 0; i < 6; i++)
			step(true);
	}
	Expect("second combo: 3 hits, 30 damage", tr.p[0].comboHits == 3 && tr.p[0].comboDamage == 30, std::to_string(tr.p[0].comboDamage));

	// A counter that keeps its value: the combo still ends.
	for (int i = 0; i < 60 * 4; i++)
		step(true);
	Expect("held counter: combo hidden after a while", !tr.p[0].comboShown);

	g.m.W16(kP1Object + kObjectHealth, 9999);
	Expect("health out of range: invalid", !Read(g.m).valid);
	g.Set(0, 420, 0, 15000, 0);

	// Demo state for the previews.
	if (argc > 1) {
		Tracker demo;
		demo.visible = 1.0f;
		demo.now = 100.0;
		demo.timer = 57 * 60;
		demo.p[0].character = 1;
		demo.p[1].character = 2;
		demo.p[0].health = 294; demo.p[0].trail = 294;
		demo.p[1].health = 104; demo.p[1].trail = 168;
		demo.p[0].tension = 5230; demo.p[1].tension = 10000;
		demo.p[0].burst = 15000; demo.p[1].burst = 6200;
		demo.p[0].comboHits = 12; demo.p[0].comboDamage = 143; demo.p[0].comboShown = true;
		demo.p[0].comboChanged = 99.0; demo.p[0].comboEnded = -1;
		demo.p[1].lastComboHits = 7; demo.p[1].lastComboDamage = 96;
		demo.p[0].lastComboHits = 12; demo.p[0].lastComboDamage = 143;
		FILE *f = fopen(argv[1], "w");
		// 20:9 phone, game picture centered with black bars (half of 2400x1080).
		const float sw = 1200, sh = 540, gh = 540, gw = gh * 480.0f / 272.0f;
		Frame fr{ (sw - gw) * 0.5f, 0, gw, gh, sw, sh };
		fprintf(f, "{\"screen\":[%g,%g],\"game\":[%g,%g,%g,%g],\"designs\":{", sw, sh, fr.gx, fr.gy, fr.gw, fr.gh);
		const char *names[] = { "", "neon", "arcade", "side", "data" };
		for (int dsg = 1; dsg <= 4; dsg++) {
			std::vector<Prim> prims;
			Build(dsg, demo, fr, 1.0f, &prims);
			fprintf(f, "%s\"%s\":[", dsg > 1 ? "," : "", names[dsg]);
			for (size_t i = 0; i < prims.size(); i++) {
				const Prim &p = prims[i];
				fprintf(f, "%s{\"k\":%d,\"x\":%g,\"y\":%g,\"w\":%g,\"h\":%g,\"q\":[%g,%g,%g,%g,%g,%g,%g,%g],\"c1\":%u,\"c2\":%u,\"t\":\"%s\",\"s\":%g,\"a\":%d,\"sh\":%d}",
					i ? "," : "", (int)p.kind, p.x, p.y, p.w, p.h, p.qx[0], p.qy[0], p.qx[1], p.qy[1], p.qx[2], p.qy[2], p.qx[3], p.qy[3],
					p.c1, p.c2, p.text.c_str(), p.size, p.align, p.shadow ? 1 : 0);
			}
			fprintf(f, "]");
			printf("design %s: %zu primitives\n", names[dsg], prims.size());
		}
		fprintf(f, "}}\n");
		fclose(f);
	}
	printf("failures: %d\n", failures);
	return failures;
}
