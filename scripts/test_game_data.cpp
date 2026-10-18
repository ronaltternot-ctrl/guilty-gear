// Tests what the HUD and the per-character profiles read from each game (AcPlusGameData.h)
// with a simulated PSP memory: Guilty Gear XX AC+, BlazBlue CT / CS II and the five KOF games
// of the Orochi Saga. Also checks the KOF addresses against the CWCheat codes they come from,
// and the combo estimate of the HUD for games without a combo counter.
//   g++ -std=c++17 -O2 -Wall -Wextra -I overlay/UI scripts/test_game_data.cpp -o /tmp/t && /tmp/t

#include <cstdio>
#include <string>
#include <vector>

#include "AcPlusGameData.h"

using namespace AcGame;
using AcHud::Tracker;

const u32 kRamStart = 0x08800000, kRamEnd = 0x0A000000;

struct SimMem : Mem {
	std::vector<u8> ram;
	SimMem() : ram(kRamEnd - kRamStart) {}
	bool Valid(u32 a, u32 size) const override { return a >= kRamStart && (uint64_t)a + size <= kRamEnd; }
	const u8 *Ptr(u32 a) const override { return &ram[a - kRamStart]; }
	void W8(u32 a, u8 v) { ram[a - kRamStart] = v; }
	void W16(u32 a, u16 v) { memcpy(&ram[a - kRamStart], &v, 2); }
	void W32(u32 a, u32 v) { memcpy(&ram[a - kRamStart], &v, 4); }
	void Str(u32 a, const char *s) { memcpy(&ram[a - kRamStart], s, strlen(s)); }
	u8 R8(u32 a) const { return ram[a - kRamStart]; }
	void Clear() { std::fill(ram.begin(), ram.end(), 0); }
};

static int failures = 0;
static void Expect(const char *what, bool ok, const std::string &detail = "") {
	printf("%-62s %s %s\n", what, ok ? "OK   " : "WRONG", detail.c_str());
	if (!ok)
		failures++;
}

static std::string Hex(u32 v) {
	char b[16];
	snprintf(b, sizeof(b), "%08X", v);
	return b;
}

// ---------------------------------------------------------------------------------------------

static void TestGuiltyGear(SimMem &m) {
	m.Clear();
	using namespace GG;
	m.W8(kSelect[0], 1);
	m.W8(kSelect[1], 21);
	m.W16(kTimer, 5940);
	for (int p = 0; p < 2; p++) {
		m.W16(kP1Object + p * kObjectStride, (u16)(p + 2));
		m.W16(kP1Object + p * kObjectStride + kObjectHealth, (u16)(p == 0 ? 420 : 210));
		m.W16(kP1Block + p * kBlockStride, 5000);
		m.W16(kP1Block + p * kBlockStride + kBlockBurst, 15000);
	}
	Reader r;
	r.Init("ULUS-10409");
	Snapshot s;
	Expect("GG: supported, read ok", r.GetTitle() == TITLE_GG && r.Read(m, 0, &s) && s.valid);
	Expect("GG: health, maximum, meter, burst", s.health[1] == 210 && s.healthMax[1] == 420 && s.meter[0] == 0.5f && s.gauge[0] == 1.0f);
	Expect("GG: timer in seconds, names", s.timer == 99 && s.name[0] == "SOL" && s.name[1] == "ROBO-KY", std::to_string(s.timer) + " " + s.name[1]);
	Fighter f;
	Expect("GG: profile key is the character number", r.PlayerFighter(m, 0, 1, &f) && f.key == "21" && f.name == "Robo-Ky", f.key);
	m.W16(kP1Object + kObjectHealth, 9999);
	Expect("GG: health out of range: not valid", !r.Read(m, 0, &s));
}

// ---------------------------------------------------------------------------------------------

static void TestBlazBlue(SimMem &m) {
	m.Clear();
	const u32 obj1 = 0x09200000, obj2 = 0x09210000;
	m.W32(0x08C0938C, obj1);
	m.W32(0x08C09390, obj2);
	m.W32(obj1 + 0x798, 9000);
	m.W32(obj1 + 0x79C, 11000);
	m.W16(obj1 + 0x2214, 5000);
	m.W32(obj2 + 0x798, 10500);
	m.W32(obj2 + 0x79C, 10500);
	m.W16(obj2 + 0x2214, 10000);
	// Sprite names inside the objects; the decoys must not count.
	m.Str(obj1 + 0x100, "rg010_00");
	m.Str(obj1 + 0x200, "rg010_01");
	m.Str(obj1 + 0x300, "xny003_00");   // part of a longer word
	m.Str(obj1 + 0x340, "ny003_005");   // longer than a sprite name
	m.Str(obj2 + 0x120, "ny003_00");
	m.Str(obj2 + 0x1A0, "zz100_00");    // not a character

	Reader r;
	r.Init("ULUS10519");
	Snapshot s;
	Expect("BB CT US: supported, read ok", r.GetTitle() == TITLE_BB && r.Read(m, 0, &s));
	Expect("BB: health and maximum from the object", s.health[0] == 9000 && s.healthMax[0] == 11000 && s.health[1] == 10500);
	Expect("BB: Heat as the meter, no timer, no second gauge", s.meter[0] == 0.5f && s.meter[1] == 1.0f && !s.style->timer && !s.style->gauge && s.gauge[0] < 0);
	Expect("BB: characters from the sprite names", s.name[0] == "RAGNA" && s.name[1] == "NU-13", s.name[0] + " " + s.name[1]);
	Fighter f;
	Expect("BB: profile key", r.PlayerFighter(m, 0, 0, &f) && f.key == "bb_rg" && f.name == "Ragna", f.key);
	Expect("BB: player 2", r.PlayerFighter(m, 0, 1, &f) && f.key == "bb_ny", f.key);

	// Object reused for another character: found again after a second.
	memset(&m.ram[obj1 - kRamStart], 0, 0x2400);
	m.W32(obj1 + 0x798, 11000);
	m.W32(obj1 + 0x79C, 11000);
	m.Str(obj1 + 0x80, "tg200_00");
	Expect("BB: same object, new character: kept for a moment", r.PlayerFighter(m, 0.5, 0, &f) && f.key == "bb_rg");
	Expect("BB: ... then found again", r.PlayerFighter(m, 1.6, 0, &f) && f.key == "bb_tg", f.key);
	memset(&m.ram[obj1 + 0x80 - kRamStart], 0, 16);
	Expect("BB: no sprite names: no character (profiles wait)", !r.PlayerFighter(m, 3.0, 0, &f));
	Expect("BB: ... but the HUD still works", r.Read(m, 3.0, &s) && s.name[0].empty());

	m.W32(obj2 + 0x798, 20000);
	Expect("BB: health above the maximum: not valid", !r.Read(m, 4, &s));
	m.W32(obj2 + 0x798, 100);
	m.W32(0x08C09390, 0x01234567);
	Expect("BB: bad pointer: not valid", !r.Read(m, 5, &s));

	// Continuum Shift II US: other pointers and offsets.
	m.Clear();
	m.W32(0x08C32B70, obj1);
	m.W32(0x08C32B74, obj2);
	m.W32(obj1 + 0x810, 13000);
	m.W32(obj1 + 0x814, 13000);
	m.W16(obj1 + 0x422C, 2500);
	m.W32(obj2 + 0x810, 6000);
	m.W32(obj2 + 0x814, 12000);
	m.Str(obj1 + 0x3000, "tb001_00");   // beyond CT's object size: only CS II looks there
	Reader cs;
	cs.Init("ULUS-10579");
	Expect("BB CS II US: read ok", cs.Read(m, 0, &s) && s.health[0] == 13000 && s.meter[0] == 0.25f && s.health[1] == 6000);
	Expect("BB CS II: Tsubaki", s.name[0] == "TSUBAKI", s.name[0]);

	Reader jp;
	jp.Init("ULJM05613");
	Expect("BB CT JP: no known addresses: not supported", !jp.Supported());
}

// ---------------------------------------------------------------------------------------------

struct KofSetup {
	int game;
	SimMem *m;
	u32 shift = 0;
	const KOF::Game &G() const { return KOF::kGames[game]; }
	void Byte(u32 neo, u8 v) { m->W8(KOF::Addr(G(), neo), v); }
	void Program() { m->W16(G().sigAddr, G().sig); }
	void Health(int p, int hp) { Byte(G().health + p * KOF::kPlayerStride, (u8)hp); }
	void Team(int p, u8 a, u8 b, u8 c, u8 o0, u8 o1, u8 o2, u8 lost) {
		const u32 off = p * G().teamStride + shift;
		Byte(G().chars + off, a); Byte(G().chars + off + 1, b); Byte(G().chars + off + 2, c);
		Byte(G().order + off, o0); Byte(G().order + off + 1, o1); Byte(G().order + off + 2, o2);
		Byte(G().lost + off, lost);
	}
	void Timer(u8 bcd) { Byte(G().timer + shift, bcd); }
	void Animate(int frame) { Byte(G().object + 0x40, (u8)frame); }
};

static void TestKofAddresses() {
	// CWCheat (ULUS-10360): health P1 / P2, and the timer code (a 32-bit write whose top byte is
	// the BCD seconds, 0x60).
	struct Code { u32 hp1, hp2, timerWrite; };
	const Code codes[] = {
		{ 0x206B9830, 0x206B9A30, 0x206B9E3C },
		{ 0x20F4D640, 0x20F4D840, 0x20F4FC54 },
		{ 0x21047958, 0x21047B58, 0x21049F54 },
		{ 0x21343AD8, 0x21343CD8, 0x213470D8 },
		{ 0x20725C88, 0x20725E88, 0x20728288 },
	};
	const u32 sigCodes[] = { 0x0120EDEE, 0x00CFE5E0, 0x00CF8B50, 0x00DF730E, 0x00BA5314 };
	for (int g = 0; g < KOF::kGameCount; g++) {
		const KOF::Game &G = KOF::kGames[g];
		const u32 hp1 = (codes[g].hp1 & 0x0FFFFFFF) + 0x08800000;
		const u32 hp2 = (codes[g].hp2 & 0x0FFFFFFF) + 0x08800000;
		const u32 timerTop = (codes[g].timerWrite & 0x0FFFFFFF) + 0x08800000 + 3;
		const bool ok = KOF::Addr(G, G.health) == hp1 && KOF::Addr(G, G.health + KOF::kPlayerStride) == hp2 &&
			KOF::Addr(G, G.timer + G.shift) == timerTop && G.sigAddr == sigCodes[g] + 0x08800000 && (G.base & 15) == 0;
		std::string what = std::string(G.label) + ": health, timer and program word match CWCheat";
		Expect(what.c_str(), ok, Hex(KOF::Addr(G, G.health)) + " " + Hex(KOF::Addr(G, G.timer + G.shift)));
	}
}

static void TestKof(SimMem &m) {
	TestKofAddresses();

	// '98: P1 team Iori, Kyo, Yamazaki in the order C? no: order 2, 0, 1 and one member lost
	// -> the second in order, slot 0 = Iori. P2: Terry, Andy, Joe in order, none lost -> Terry.
	m.Clear();
	KofSetup k{ 4, &m };
	k.Program();
	k.Health(0, 0x50);
	k.Health(1, 0x30);
	k.Byte(0x1081EA, 0x20);
	k.Byte(0x1082E3, 2);
	k.Timer(0x45);
	k.Team(0, 0x1B, 0x00, 0x18, 2, 0, 1, 1);
	k.Team(1, 0x03, 0x04, 0x05, 0, 1, 2, 0);
	Reader r;
	r.Init("ULUS-10360");
	Snapshot s;
	double t = 0;
	int frame = 0;
	auto tick = [&]() { k.Animate(++frame); t += 1.0 / 60.0; };
	tick();
	Expect("KOF: supported", r.GetTitle() == TITLE_KOF && r.Supported());
	Expect("KOF '98: running game found", r.Read(m, t, &s) && r.KofGame(m, t) == 4, std::to_string(r.KofGame(m, t)));
	Expect("KOF '98: health 0x50 / 0x67, power half, 2 stocks, timer 45",
		s.health[0] == 0x50 && s.healthMax[0] == 0x67 && s.meter[0] == 0.5f && s.stocks[0] == 2 && s.timer == 45, std::to_string(s.timer));
	Expect("KOF '98: fighters Iori (second of his team) and Terry", s.name[0] == "IORI" && s.name[1] == "TERRY", s.name[0] + " " + s.name[1]);
	Fighter f;
	Expect("KOF '98: profile key", r.PlayerFighter(m, t, 0, &f) && f.key == "kof_iori" && f.name == "Iori Yagami", f.key);
	// Iori loses: the third one in order (slot 1 = Kyo) comes in.
	k.Byte(0x10A84D, 2);
	tick();
	Expect("KOF '98: next member after losing one more", r.PlayerFighter(m, t, 0, &f) && f.key == "kof_kyo", f.key);
	Expect("KOF '98: style POWER with timer", s.style && std::string(s.style->meter) == "POWER" && s.style->timer);

	// Menus: the RAM stops changing -> not a fight any more (after 2 s).
	for (int i = 0; i < 150; i++)
		t += 1.0 / 60.0;
	Expect("KOF '98: frozen RAM: the game is kept (paused)...", r.KofGame(m, t) == 4);
	Snapshot frozen;
	r.Read(m, t, &frozen);
	Tracker tr;
	for (int i = 0; i < 120; i++)
		tr.Update(frozen, 10 + i / 60.0);
	Expect("KOF '98: ... and the HUD hides (fighters not moving)", tr.visible == 0.0f);
	// Another program loaded: gone.
	m.W16(k.G().sigAddr, 0x1234);
	t += 0.1;
	Expect("KOF: program word gone: no game", r.KofGame(m, t) < 0 && !r.Read(m, t, &s));

	// '94: health out of 0xCF, POW out of 0x7F, no names.
	m.Clear();
	KofSetup k94{ 0, &m };
	k94.Program();
	k94.Health(0, 0xCF);
	k94.Health(1, 0x10);
	k94.Byte(0x108219, 0x7F);
	k94.Timer(0x60);
	Reader r94;
	r94.Init("ULUS10360");
	k94.Animate(1);
	Expect("KOF '94: running game found", r94.Read(m, 50, &s) && r94.KofGame(m, 50) == 0);
	Expect("KOF '94: health of 0xCF, POW full, timer 60, no names",
		s.healthMax[0] == 0xCF && s.health[1] == 0x10 && s.meter[0] == 1.0f && s.timer == 60 && s.name[0].empty() && std::string(s.style->meter) == "POW");
	Expect("KOF '94: no profiles (team member unknown)", !r94.PlayerFighter(m, 50, 0, &f));
	k94.Health(1, 0xE0);
	Expect("KOF '94: health above the maximum: not valid", !r94.Read(m, 50.1, &s));

	// '97: the team data at the arcade addresses, or 0x1000 further like the PSP timer code.
	for (int pass = 0; pass < 2; pass++) {
		m.Clear();
		KofSetup k97{ 3, &m, pass == 0 ? 0x1000u : 0u };
		k97.Program();
		k97.Health(0, 0x67);
		k97.Health(1, 0x67);
		k97.Timer(0x59);
		k97.Team(0, 0x15, 0x16, 0x17, 1, 2, 0, 0);   // Yashiro, Shermie, Chris; Shermie first
		k97.Team(1, 0x1F, 0x0F, 0x1B, 0, 1, 2, 0);   // Shingo first
		// Garbage at the other place.
		KofSetup other{ 3, &m, pass == 0 ? 0u : 0x1000u };
		other.Timer(0xAB);
		other.Byte(k97.G().lost + other.shift, 7);
		Reader r97;
		r97.Init("ULUS10360");
		k97.Animate(1);
		const bool ok = r97.Read(m, 70, &s) && s.name[0] == "SHERMIE" && s.name[1] == "SHINGO" && s.timer == 59;
		Expect(pass == 0 ? "KOF '97: team data where the PSP timer code points" : "KOF '97: team data at the arcade addresses", ok, s.name[0] + " " + std::to_string(s.timer));
	}

	// Two games' program words at once (impossible, but): the one with sane health wins.
	m.Clear();
	KofSetup a{ 2, &m }, b{ 4, &m };
	a.Program();
	b.Program();
	a.Health(0, 0x90);   // above '96's maximum
	b.Health(0, 0x40);
	b.Animate(3);
	a.Animate(3);
	Reader rr;
	rr.Init("ULUS10360");
	Expect("KOF: a game with impossible health is not taken", rr.KofGame(m, 80) == 4, std::to_string(rr.KofGame(m, 80)));
}

// ---------------------------------------------------------------------------------------------
// The HUD's combo estimate (BlazBlue and KOF have no combo counter in memory).

static void TestEstimatedCombos() {
	Snapshot s;
	s.valid = true;
	s.healthMax[0] = s.healthMax[1] = 11000;
	s.health[0] = s.health[1] = 11000;
	s.style = &BB::GetStyle();
	Tracker tr;
	double t = 0;
	u32 hash = 1;
	auto frames = [&](int n) {
		for (int i = 0; i < n; i++) {
			s.objectsHash = ++hash;
			t += 1.0 / 60.0;
			tr.Update(s, t);
		}
	};
	frames(30);
	// P1 hits P2 four times, 0.25 s apart, 500 damage each.
	for (int h = 0; h < 4; h++) {
		s.health[1] -= 500;
		frames(15);
	}
	Expect("estimate: 4 hits close together = a 4-hit combo", tr.p[0].comboShown && tr.p[0].comboHits == 4 && tr.p[0].estimated, std::to_string(tr.p[0].comboHits));
	Expect("estimate: 2000 damage", tr.p[0].comboDamage == 2000, std::to_string(tr.p[0].comboDamage));
	frames(150);
	Expect("estimate: combo over after the pause", !tr.p[0].comboShown && tr.p[0].lastComboHits == 4);
	// Two single hits 1.5 s apart: no combo.
	s.health[1] -= 800;
	frames(90);
	s.health[1] -= 800;
	frames(10);
	Expect("estimate: separate hits are not a combo", !tr.p[0].comboShown && tr.p[0].comboHits == 1, std::to_string(tr.p[0].comboHits));
	Expect("estimate: P2 got no combo credit", tr.p[1].comboHits == 0);
	// New round: health back up, no fake damage.
	s.health[1] = 11000;
	frames(10);
	s.health[1] -= 300;
	frames(5);
	s.health[1] -= 300;
	frames(5);
	Expect("estimate: after a new round, damage counts from full health", tr.p[0].comboHits == 2 && tr.p[0].comboDamage == 600, std::to_string(tr.p[0].comboDamage));
}

int main() {
	SimMem m;
	TestGuiltyGear(m);
	TestBlazBlue(m);
	TestKof(m);
	TestEstimatedCombos();
	printf("failures: %d\n", failures);
	return failures;
}
