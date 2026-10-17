// GGXX AC+ launcher build: a custom HUD drawn over the game, from values read in its memory.
//
// What is read (US version ULUS-10409; addresses from the CWCheat codes for that disc, base
// 0x08800000, and the layout they share with the PS2 version):
//   health   u16  fighter object + 0x1E      P1 0x0913D01E, P2 = + 0x124        (max 420)
//   tension  u16  player block               P1 0x08C6BB7C, P2 = + 0x148        (max 10000)
//   burst    u16  player block + 0xF8                                            (max 15000)
//   combo    u16  player block + 0x100: hits the OTHER player is landing ("Max Beat" code:
//                 P1's combo at P2 block + 0x100 = 0x08C6BDC4)
//   timer    u16  0x08C6CC14, in frames (6000 = full)
//   fighters u8   0x08C6BB58 / 0x08C6BB5A (character select)
//
// This file has no PPSSPP headers: it reads through an interface and outputs simple primitives
// (rectangles, gradients, quads, text), so it can be tested and its designs previewed on a PC
// (scripts/test_hud_core.cpp). AcPlusHud.h draws the primitives with PPSSPP's UI renderer.

#pragma once

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

namespace AcHud {

typedef uint8_t u8;
typedef uint16_t u16;
typedef uint32_t u32;

struct Mem {
	virtual ~Mem() {}
	virtual bool Valid(u32 addr, u32 size) const = 0;
	virtual const u8 *Ptr(u32 addr) const = 0;
};

const u32 kP1Object = 0x0913D000;
const u32 kObjectStride = 0x124;
const u32 kObjectHealth = 0x1E;
const u32 kP1Block = 0x08C6BB7C;
const u32 kBlockStride = 0x148;
const u32 kBlockBurst = 0xF8;
const u32 kBlockCombo = 0x100;
const u32 kTimer = 0x08C6CC14;
const u32 kSelect[2] = { 0x08C6BB58, 0x08C6BB5A };

const int kMaxHealth = 420;
const int kMaxTension = 10000;
const int kMaxBurst = 15000;
const int kTimerFrames = 6000;

static const char *const kNames[] = {
	"", "SOL", "KY", "MAY", "MILLIA", "AXL", "POTEMKIN", "CHIPP", "EDDIE", "BAIKEN", "FAUST",
	"TESTAMENT", "JAM", "ANJI", "JOHNNY", "VENOM", "DIZZY", "SLAYER", "I-NO", "ZAPPA",
	"BRIDGET", "ROBO-KY", "A.B.A", "ORDER-SOL", "KLIFF", "JUSTICE",
};

inline bool Read16(const Mem &m, u32 addr, int *out) {
	if (!m.Valid(addr, 2))
		return false;
	u16 v;
	memcpy(&v, m.Ptr(addr), 2);
	*out = v;
	return true;
}

inline bool Read8(const Mem &m, u32 addr, int *out) {
	if (!m.Valid(addr, 1))
		return false;
	*out = *m.Ptr(addr);
	return true;
}

struct Snapshot {
	bool valid = false;
	int health[2] = {};
	int tension[2] = {};
	int burst[2] = {};
	int combo[2] = {};      // hits each player is landing right now
	int timer = 0;          // frames
	int character[2] = {};
	u32 objectsHash = 0;    // changes every frame while a fight runs
};

// Reads the values; valid = all of them in range (a fight is loaded).
inline Snapshot Read(const Mem &m) {
	Snapshot s;
	bool ok = true;
	for (int p = 0; p < 2; p++) {
		const u32 block = kP1Block + (u32)p * kBlockStride;
		const u32 other = kP1Block + (u32)(1 - p) * kBlockStride;
		ok &= Read16(m, kP1Object + (u32)p * kObjectStride + kObjectHealth, &s.health[p]);
		ok &= Read16(m, block, &s.tension[p]);
		ok &= Read16(m, block + kBlockBurst, &s.burst[p]);
		ok &= Read16(m, other + kBlockCombo, &s.combo[p]);
		ok &= Read8(m, kSelect[p], &s.character[p]);
		int id = 0;
		ok &= Read16(m, kP1Object + (u32)p * kObjectStride, &id);
		ok &= id >= 1 && id <= 26;
		ok &= s.health[p] <= kMaxHealth && s.tension[p] <= kMaxTension && s.burst[p] <= kMaxBurst && s.combo[p] <= 999;
		ok &= s.character[p] >= 1 && s.character[p] <= 25;
	}
	ok &= Read16(m, kTimer, &s.timer);
	if (ok && m.Valid(kP1Object, kObjectStride * 2)) {
		// FNV-1a over both fighter objects: in menus they stop changing.
		u32 h = 2166136261u;
		const u8 *p = m.Ptr(kP1Object);
		for (u32 i = 0; i < kObjectStride * 2; i++)
			h = (h ^ p[i]) * 16777619u;
		s.objectsHash = h;
	}
	s.valid = ok;
	return s;
}

// ---------------------------------------------------------------------------------------------
// Values smoothed over time for drawing.

struct PlayerView {
	float health = kMaxHealth;    // shown health
	float trail = kMaxHealth;     // recent damage, drains after a moment
	double trailHold = 0;
	float tension = 0, burst = 0;
	int comboHits = 0;            // combo shown (current or the one that just ended)
	int comboDamage = 0;
	double comboChanged = -10;    // when the hit count last went up
	double comboEnded = -10;      // when it went back to 0 (-10 = still running / none)
	bool comboShown = false;
	int lastComboHits = 0, lastComboDamage = 0;   // for the "Datos" design
	int character = 0;
};

class Tracker {
public:
	PlayerView p[2];
	int timer = 0;
	float visible = 0;   // 0..1 fade
	double now = 0;

	void Update(const Snapshot &s, double t) {
		const double dt = std::min(0.1, std::max(0.0, t - now));
		now = t;
		bool live = s.valid;
		if (live) {
			if (s.objectsHash != lastHash_) {
				lastHash_ = s.objectsHash;
				lastChange_ = t;
			}
			// Frozen fighters (menus, the game's pause) for a while: hide.
			live = t - lastChange_ < 1.5;
		}
		visible = std::max(0.0f, std::min(1.0f, visible + (float)(live ? dt / 0.25 : -dt / 0.25)));
		if (!s.valid)
			return;

		timer = s.timer;
		const float prevHealth[2] = { p[0].health, p[1].health };
		for (int i = 0; i < 2; i++) {
			PlayerView &v = p[i];
			v.character = s.character[i];
			const float hp = (float)s.health[i];
			if (!started_ || hp > v.health + 0.5f) {
				v.trail = hp;   // new round, or healed
			} else if (hp < v.health - 0.5f) {
				v.trail = std::max(v.trail, v.health);
				v.trailHold = t + 0.7;
			}
			v.health = hp;
			if (t > v.trailHold && v.trail > v.health)
				v.trail = std::max(v.health, v.trail - (float)(dt * 260.0));
			v.tension = (float)s.tension[i];
			v.burst = (float)s.burst[i];
		}
		if (!started_) {
			healthBefore_[0] = s.health[0];
			healthBefore_[1] = s.health[1];
		}

		// Combos: player i hitting player j. The game's counter goes back to 0 when a combo
		// ends; if it ever kept its value instead, the combo counts as over after 2.5 s.
		for (int i = 0; i < 2; i++) {
			PlayerView &v = p[i];
			const int j = 1 - i;
			const int hits = s.combo[i];
			if (hits == 0) {
				healthBefore_[j] = s.health[j];
				if (v.comboHits > 0 && v.comboEnded < 0)
					v.comboEnded = t;
			} else if (hits != v.comboHits || v.comboHits == 0) {
				if (v.comboHits > 0 && hits < v.comboHits)
					healthBefore_[j] = (int)prevHealth[j];   // restarted without passing through 0
				v.comboChanged = t;
				v.comboEnded = -1;
				v.comboHits = hits;
			} else if (v.comboEnded < 0 && t - v.comboChanged > 2.5) {
				v.comboEnded = t;
			}
			if (v.comboEnded < 0 && v.comboHits > 0) {
				v.comboDamage = std::max(0, healthBefore_[j] - s.health[j]);
				if (v.comboHits >= 2) {
					v.lastComboHits = v.comboHits;
					v.lastComboDamage = v.comboDamage;
				}
			}
			if (v.comboEnded >= 0 && t - v.comboEnded > 1.3 && hits == 0)
				v.comboHits = 0;
			v.comboShown = v.comboHits >= 2 && (v.comboEnded < 0 || t - v.comboEnded <= 1.3);
		}
		started_ = true;
	}

private:
	u32 lastHash_ = 0;
	double lastChange_ = -100;
	bool started_ = false;
	int healthBefore_[2] = { kMaxHealth, kMaxHealth };   // each player's health before the combo against them
};

// ---------------------------------------------------------------------------------------------
// Primitives. Colors are 0xAARRGGBB.

enum PrimKind { PRIM_RECT, PRIM_VGRAD, PRIM_QUAD, PRIM_TEXT };
enum { ALIGN_L = -1, ALIGN_C = 0, ALIGN_R = 1 };

struct Prim {
	PrimKind kind = PRIM_RECT;
	float x = 0, y = 0, w = 0, h = 0;
	float qx[4] = {}, qy[4] = {};   // quad corners: top-left, top-right, bottom-right, bottom-left
	u32 c1 = 0, c2 = 0;             // rect: c1; gradient and quad: top c1, bottom c2
	std::string text;
	float size = 0;                 // text height
	int align = ALIGN_L;            // text: horizontal anchor at x; vertically centered on y
	bool shadow = false;
};

enum Design { DESIGN_OFF = 0, DESIGN_NEON = 1, DESIGN_ARCADE = 2, DESIGN_SIDE = 3, DESIGN_DATA = 4 };

// Where the game picture is, and the whole screen (same units, e.g. dp or pixels).
struct Frame {
	float gx, gy, gw, gh;
	float sw, sh;
};

inline u32 Alpha(u32 c, float a) {
	u32 al = (u32)std::max(0.0f, std::min(255.0f, ((c >> 24) & 0xFF) * a));
	return (al << 24) | (c & 0x00FFFFFF);
}

inline u32 Mix(u32 a, u32 b, float t) {
	t = std::max(0.0f, std::min(1.0f, t));
	u32 out = 0;
	for (int sh = 0; sh < 32; sh += 8) {
		float ca = (float)((a >> sh) & 0xFF), cb = (float)((b >> sh) & 0xFF);
		out |= ((u32)(ca + (cb - ca) * t + 0.5f) & 0xFF) << sh;
	}
	return out;
}

class Painter {
public:
	Painter(std::vector<Prim> *out, float opacity) : out_(out), opacity_(opacity) {}

	void Rect(float x, float y, float w, float h, u32 c) {
		if (w <= 0 || h <= 0)
			return;
		Prim p;
		p.kind = PRIM_RECT;
		p.x = x; p.y = y; p.w = w; p.h = h;
		p.c1 = Alpha(c, opacity_);
		out_->push_back(p);
	}
	void VGrad(float x, float y, float w, float h, u32 top, u32 bottom) {
		if (w <= 0 || h <= 0)
			return;
		Prim p;
		p.kind = PRIM_VGRAD;
		p.x = x; p.y = y; p.w = w; p.h = h;
		p.c1 = Alpha(top, opacity_);
		p.c2 = Alpha(bottom, opacity_);
		out_->push_back(p);
	}
	// Parallelogram: top edge from x1 to x2 at y, bottom edge shifted by -skew.
	void Slant(float x1, float x2, float y, float h, float skew, u32 top, u32 bottom) {
		if (x2 - x1 <= 0.01f || h <= 0)
			return;
		Prim p;
		p.kind = PRIM_QUAD;
		p.qx[0] = x1; p.qy[0] = y;
		p.qx[1] = x2; p.qy[1] = y;
		p.qx[2] = x2 - skew; p.qy[2] = y + h;
		p.qx[3] = x1 - skew; p.qy[3] = y + h;
		p.c1 = Alpha(top, opacity_);
		p.c2 = Alpha(bottom, opacity_);
		out_->push_back(p);
	}
	void Text(const std::string &s, float x, float y, float size, u32 c, int align, bool shadow = true) {
		Prim p;
		p.kind = PRIM_TEXT;
		p.text = s;
		p.x = x; p.y = y;
		p.size = size;
		p.c1 = Alpha(c, opacity_);
		p.align = align;
		p.shadow = shadow;
		out_->push_back(p);
	}

private:
	std::vector<Prim> *out_;
	float opacity_;
};

inline std::string Name(int id) {
	return id >= 1 && id <= 25 ? kNames[id] : "?";
}

inline std::string Num(int v) {
	char buf[16];
	snprintf(buf, sizeof(buf), "%d", v);
	return buf;
}

inline std::string Pct(float v, float max) {
	char buf[16];
	snprintf(buf, sizeof(buf), "%d%%", (int)(v * 100.0f / max + 0.0001f));
	return buf;
}

inline std::string TimerText(int frames) {
	if (frames <= 0)
		return "0";
	if (frames > kTimerFrames + 60)
		return "--";
	return Num(std::min(99, (frames + 59) / 60));
}

// "Pop" when the hit count goes up.
inline float Pop(const PlayerView &v, double now) {
	const double a = now - v.comboChanged;
	return a < 0.14 ? 1.0f + 0.35f * (float)(1.0 - a / 0.14) : 1.0f;
}

inline float ComboFade(const PlayerView &v, double now) {
	if (v.comboEnded < 0)
		return 1.0f;
	return std::max(0.0f, std::min(1.0f, (float)(1.3 - (now - v.comboEnded)) / 0.3f));
}

// ---------------------------------------------------------------------------------------------
// Design 1: Neon. Slanted glowing bars, cyan for player 1 and pink for player 2.

inline void DrawNeon(Painter &d, const Tracker &tr, const Frame &f) {
	const float u = f.gh / 272.0f;
	const float cx = f.gx + f.gw * 0.5f;
	const u32 side[2] = { 0xFF22E5FF, 0xFFFF3EA5 };
	const u32 sideDark[2] = { 0xFF0B6C8C, 0xFF8C1556 };
	const float barY = f.gy + 7 * u, barH = 10 * u, skew = 5 * u;
	const float outer = 12 * u, inner = 26 * u;   // from the screen edge / the center
	// Dark shades over the top and bottom, so the game's own HUD stays in the background.
	d.VGrad(f.gx, f.gy, f.gw, 30 * u, 0xE6000000, 0xB0000000);
	d.VGrad(f.gx, f.gy + 30 * u, f.gw, 16 * u, 0xB0000000, 0x00000000);
	d.VGrad(f.gx, f.gy + f.gh - 40 * u, f.gw, 16 * u, 0x00000000, 0xB0000000);
	d.VGrad(f.gx, f.gy + f.gh - 24 * u, f.gw, 24 * u, 0xB0000000, 0xE6000000);
	for (int i = 0; i < 2; i++) {
		const PlayerView &v = tr.p[i];
		const float len = f.gw * 0.5f - outer - inner;
		const float hp = v.health / kMaxHealth, trail = v.trail / kMaxHealth;
		const bool low = hp < 0.3f;
		const float pulse = low ? 0.5f + 0.5f * (float)std::sin(tr.now * 9.0) : 0.0f;
		u32 top = low ? Mix(0xFFFFC23E, 0xFFFF4D4D, pulse) : Mix(side[i], 0xFFFFFFFF, 0.25f);
		u32 bottom = low ? 0xFFB3261E : sideDark[i];
		// Outer edge to the left (P1) or right (P2); the bar recedes toward that edge.
		auto span = [&](float from, float to, u32 ct, u32 cb, float grow) {
			float a = i == 0 ? f.gx + outer + from * len : f.gx + f.gw - outer - to * len;
			float b = i == 0 ? f.gx + outer + to * len : f.gx + f.gw - outer - from * len;
			float s = i == 0 ? skew : -skew;
			d.Slant(a - grow, b + grow, barY - grow, barH + grow * 2, s, ct, cb);
		};
		span(0, 1, Alpha(side[i], 0.35f), Alpha(side[i], 0.15f), 1.6f * u);   // glow
		span(0, 1, 0xE6070B18, 0xE6070B18, 0);
		span(hp, trail, 0xF2FFFFFF, 0xF2C8D6FF, 0);
		span(0, hp, top, bottom, 0);
		// Name and burst under the bar.
		const float nameX = i == 0 ? f.gx + outer + 2 * u : f.gx + f.gw - outer - 2 * u;
		d.Text(Name(v.character), nameX, barY + barH + 8 * u, 9.5f * u, side[i], i == 0 ? ALIGN_L : ALIGN_R);
		const float bw = 42 * u, bx = i == 0 ? nameX + 56 * u : nameX - 56 * u - bw;
		const float burst = v.burst / kMaxBurst;
		d.Rect(bx, barY + barH + 7 * u, bw, 3 * u, 0xFF1A2236);
		d.Rect(i == 0 ? bx : bx + bw * (1 - burst), barY + barH + 7 * u, bw * burst, 3 * u, burst >= 1.0f ? 0xFFFFFFFF : Alpha(side[i], 0.8f));
		d.Text("BURST", i == 0 ? bx + bw + 3 * u : bx - 3 * u, barY + barH + 8.5f * u, 6 * u, burst >= 1.0f ? 0xFFFFFFFF : 0xFF7A86A8, i == 0 ? ALIGN_L : ALIGN_R);

		// Tension at the bottom, with marks every 25%.
		const float tw = 128 * u, th = 6 * u, ty = f.gy + f.gh - 13 * u;
		const float tx = i == 0 ? f.gx + outer + 4 * u : f.gx + f.gw - outer - 4 * u - tw;
		const float ten = v.tension / kMaxTension;
		const float ts = i == 0 ? skew * 0.6f : -skew * 0.6f;
		d.Slant(tx - u, tx + tw + u, ty - u, th + 2 * u, ts, Alpha(side[i], 0.3f), Alpha(side[i], 0.1f));
		d.Slant(tx, tx + tw, ty, th, ts, 0xE6070B18, 0xE6070B18);
		const u32 tc = ten >= 1.0f ? 0xFFFFFFFF : ten >= 0.5f ? 0xFFFFD34A : 0xFF4DFFB0;
		if (i == 0)
			d.Slant(tx, tx + tw * ten, ty, th, ts, tc, Mix(tc, 0xFF000000, 0.35f));
		else
			d.Slant(tx + tw * (1 - ten), tx + tw, ty, th, ts, tc, Mix(tc, 0xFF000000, 0.35f));
		for (int k = 1; k < 4; k++)
			d.Rect(tx + tw * k / 4.0f - 0.5f * u, ty, u, th, k == 2 ? 0xCCFFFFFF : 0x66FFFFFF);
		d.Text("TENSION " + Pct(v.tension, kMaxTension), i == 0 ? tx : tx + tw, ty - 5 * u, 6.5f * u, tc, i == 0 ? ALIGN_L : ALIGN_R);

		// Combo, on the attacker's side.
		if (v.comboShown) {
			const float fade = ComboFade(v, tr.now), pop = Pop(v, tr.now);
			const float x = i == 0 ? f.gx + 16 * u : f.gx + f.gw - 16 * u;
			const int al = i == 0 ? ALIGN_L : ALIGN_R;
			d.Text(Num(v.comboHits), x, f.gy + 78 * u, 30 * u * pop, Alpha(side[i], fade), al);
			d.Text("HITS", x, f.gy + 97 * u, 9 * u, Alpha(0xFFFFFFFF, fade), al);
			d.Text(Num(v.comboDamage) + " DMG", x, f.gy + 108 * u, 7.5f * u, Alpha(0xFFFFD34A, fade), al);
		}
	}
	// Timer.
	d.Slant(cx - 14 * u, cx + 14 * u, f.gy + 4 * u, 18 * u, 0, 0xE6070B18, 0xE6070B18);
	d.Rect(cx - 14 * u, f.gy + 4 * u, 28 * u, u, 0xFF22E5FF);
	d.Rect(cx - 14 * u, f.gy + 21 * u, 28 * u, u, 0xFFFF3EA5);
	d.Text(TimerText(tr.timer), cx, f.gy + 13 * u, 13 * u, 0xFFFFFFFF, ALIGN_C);
}

// ---------------------------------------------------------------------------------------------
// Design 2: Arcade. Chunky yellow bars on solid bands, like an arcade cabinet of the 90s.

inline void DrawArcade(Painter &d, const Tracker &tr, const Frame &f) {
	const float u = f.gh / 272.0f;
	const float cx = f.gx + f.gw * 0.5f;
	const u32 gold = 0xFFE8B830;
	d.VGrad(f.gx, f.gy, f.gw, 34 * u, 0xF8101018, 0xF0000000);
	d.Rect(f.gx, f.gy + 34 * u, f.gw, u, gold);
	d.VGrad(f.gx, f.gy + 35 * u, f.gw, 12 * u, 0xC0000000, 0x00000000);
	d.VGrad(f.gx, f.gy + f.gh - 24 * u, f.gw, 24 * u, 0xE0000000, 0xF0101018);
	d.Rect(f.gx, f.gy + f.gh - 25 * u, f.gw, u, gold);
	for (int i = 0; i < 2; i++) {
		const PlayerView &v = tr.p[i];
		const float bw = f.gw * 0.5f - 30 * u, bh = 13 * u, by = f.gy + 6 * u;
		const float bx = i == 0 ? f.gx + 8 * u : cx + 22 * u;
		const float hp = v.health / kMaxHealth, trail = v.trail / kMaxHealth;
		d.Rect(bx - u, by - u, bw + 2 * u, bh + 2 * u, gold);
		d.Rect(bx, by, bw, bh, 0xFF2A0A0A);
		// Bars recede toward the outer edge.
		auto fill = [&](float from, float to, u32 top, u32 bottom) {
			float a = i == 0 ? bx + bw * from : bx + bw * (1 - to);
			d.VGrad(a, by, bw * (to - from), bh, top, bottom);
		};
		fill(hp, trail, 0xFFFF5A4A, 0xFFB3140F);
		const bool low = hp < 0.3f;
		const float blink = low && std::fmod(tr.now, 0.5) < 0.25 ? 1.0f : 0.0f;
		fill(0, hp, Mix(0xFFFFF27A, 0xFFFFFFFF, blink), Mix(0xFFF2A100, 0xFFFF6A00, low ? 1.0f : 0.0f));
		d.Rect(i == 0 ? bx : bx + bw * (1 - hp), by + 2 * u, bw * hp, 2 * u, 0x66FFFFFF);
		const float nx = i == 0 ? bx : bx + bw;
		const int al = i == 0 ? ALIGN_L : ALIGN_R;
		d.Text(Name(v.character), nx, by + bh + 8.5f * u, 9 * u, 0xFFFFFFFF, al);
		// Burst: small bar next to the name.
		const float burst = v.burst / kMaxBurst;
		const float rw = 40 * u, rx = i == 0 ? bx + bw - rw : bx;
		d.Rect(rx, by + bh + 6 * u, rw, 5 * u, 0xFF202030);
		d.Rect(i == 0 ? rx + rw * (1 - burst) : rx, by + bh + 6 * u, rw * burst, 5 * u, burst >= 1.0f ? 0xFFFFFFFF : 0xFF59B3FF);
		d.Text("BURST", i == 0 ? rx - 3 * u : rx + rw + 3 * u, by + bh + 8.5f * u, 6 * u, burst >= 1.0f ? 0xFFFFFFFF : 0xFF8890A8, i == 0 ? ALIGN_R : ALIGN_L);

		// Tension.
		const float tw = 150 * u, th = 8 * u, ty = f.gy + f.gh - 13 * u;
		const float tx = i == 0 ? f.gx + 8 * u : f.gx + f.gw - 8 * u - tw;
		const float ten = v.tension / kMaxTension;
		d.Rect(tx - u, ty - u, tw + 2 * u, th + 2 * u, 0xFF8890A8);
		d.Rect(tx, ty, tw, th, 0xFF101028);
		const u32 tc = ten >= 1.0f ? 0xFFFFFFFF : ten >= 0.5f ? 0xFFFF7A1A : 0xFF4FC3FF;
		d.VGrad(i == 0 ? tx : tx + tw * (1 - ten), ty, tw * ten, th, tc, Mix(tc, 0xFF000000, 0.45f));
		d.Rect(tx + tw * 0.5f - 0.5f * u, ty, u, th, 0xFFFFFFFF);
		d.Text("TENSION", i == 0 ? tx : tx + tw, ty - 6 * u, 6.5f * u, 0xFF8890A8, al);
		d.Text(Pct(v.tension, kMaxTension), i == 0 ? tx + tw + 4 * u : tx - 4 * u, ty + th * 0.5f, 9 * u, tc, i == 0 ? ALIGN_L : ALIGN_R);

		if (v.comboShown) {
			const float fade = ComboFade(v, tr.now), pop = Pop(v, tr.now);
			const float x = i == 0 ? f.gx + f.gw * 0.22f : f.gx + f.gw * 0.78f;
			d.Text(Num(v.comboHits) + " HITS!", x + 1.5f * u, f.gy + 62 * u + 1.5f * u, 22 * u * pop, Alpha(0xFFB3140F, fade), ALIGN_C, false);
			d.Text(Num(v.comboHits) + " HITS!", x, f.gy + 62 * u, 22 * u * pop, Alpha(0xFFFFE04A, fade), ALIGN_C);
			d.Text(Num(v.comboDamage) + " DAMAGE", x, f.gy + 80 * u, 8 * u, Alpha(0xFFFFFFFF, fade), ALIGN_C);
		}
	}
	d.Rect(cx - 16 * u, f.gy + 3 * u, 32 * u, 26 * u, gold);
	d.VGrad(cx - 15 * u, f.gy + 4 * u, 30 * u, 24 * u, 0xFF202030, 0xFF000000);
	d.Text(TimerText(tr.timer), cx, f.gy + 16 * u, 18 * u, 0xFFFFFFFF, ALIGN_C);
}

// ---------------------------------------------------------------------------------------------
// Design 3: Side. Vertical gauges in the black bars of wide phones (inside the picture if
// there is no room).

inline void DrawSide(Painter &d, const Tracker &tr, const Frame &f) {
	const float u = f.gh / 272.0f;
	const u32 side[2] = { 0xFF2DE1C2, 0xFFFF8A3D };
	const float room = std::min(f.gx, f.sw - (f.gx + f.gw));
	const bool outside = room >= 44 * u;
	const float pw = outside ? std::min(room - 6 * u, 64 * u) : 34 * u;
	for (int i = 0; i < 2; i++) {
		const PlayerView &v = tr.p[i];
		float px;
		if (outside)
			px = i == 0 ? f.gx - room * 0.5f - pw * 0.5f : f.gx + f.gw + room * 0.5f - pw * 0.5f;
		else
			px = i == 0 ? f.gx + 4 * u : f.gx + f.gw - 4 * u - pw;
		const float top = f.gy + 8 * u, bottom = f.gy + f.gh - 8 * u;
		d.VGrad(px, top, pw, bottom - top, outside ? 0xFF15151E : 0xA0000000, outside ? 0xFF0B0B10 : 0x80000000);
		d.Rect(px, top, pw, u, side[i]);
		const float cx = px + pw * 0.5f;
		d.Text(Name(v.character), cx, top + 8 * u, std::min(8.5f * u, pw / 5.5f), side[i], ALIGN_C);
		// Health: tall bar, empties from the top.
		const float hx = px + pw * 0.18f, hw = pw * 0.34f, hy = top + 18 * u, hh = (bottom - top) * 0.62f;
		const float hp = v.health / kMaxHealth, trail = v.trail / kMaxHealth;
		d.Rect(hx, hy, hw, hh, 0xFF05050A);
		d.Rect(hx, hy + hh * (1 - trail), hw, hh * (trail - hp), 0xFFFFFFFF);
		const bool low = hp < 0.3f;
		d.VGrad(hx, hy + hh * (1 - hp), hw, hh * hp, low ? 0xFFFF6B5A : Mix(side[i], 0xFFFFFFFF, 0.3f), low ? 0xFF8C1A12 : Mix(side[i], 0xFF000000, 0.5f));
		for (int k = 1; k < 4; k++)
			d.Rect(hx, hy + hh * k / 4.0f, hw, 0.6f * u, 0x80000000);
		// Tension: thinner bar next to it.
		const float tx = px + pw * 0.60f, tw = pw * 0.18f;
		const float ten = v.tension / kMaxTension;
		const u32 tc = ten >= 1.0f ? 0xFFFFFFFF : ten >= 0.5f ? 0xFFFFD34A : 0xFF6E8BFF;
		d.Rect(tx, hy, tw, hh, 0xFF05050A);
		d.VGrad(tx, hy + hh * (1 - ten), tw, hh * ten, tc, Mix(tc, 0xFF000000, 0.4f));
		d.Rect(tx - 0.5f * u, hy + hh * 0.5f, tw + u, 0.8f * u, 0xFFFFFFFF);
		const float ly = hy + hh + 7 * u;
		d.Text(Pct(v.health, kMaxHealth), hx + hw * 0.5f, ly, std::min(7 * u, pw / 6.0f), 0xFFFFFFFF, ALIGN_C);
		d.Text(Pct(v.tension, kMaxTension), tx + tw * 0.5f, ly, std::min(7 * u, pw / 6.0f), tc, ALIGN_C);
		// Burst: a short bar at the bottom.
		const float burst = v.burst / kMaxBurst;
		const float by = ly + 7 * u;
		d.Rect(px + pw * 0.15f, by, pw * 0.7f, 3 * u, 0xFF05050A);
		d.Rect(px + pw * 0.15f, by, pw * 0.7f * burst, 3 * u, burst >= 1.0f ? 0xFFFFFFFF : side[i]);
		if (v.comboShown) {
			const float fade = ComboFade(v, tr.now), pop = Pop(v, tr.now);
			const float cy = std::min(bottom - 14 * u, by + 22 * u);
			d.Text(Num(v.comboHits), cx, cy, std::min(20 * u, pw / 2.2f) * pop, Alpha(side[i], fade), ALIGN_C);
			d.Text("HITS", cx, cy + 12 * u, std::min(6.5f * u, pw / 6.0f), Alpha(0xFFFFFFFF, fade), ALIGN_C);
		}
	}
}

// ---------------------------------------------------------------------------------------------
// Design 4: Data. Small boxes with exact numbers, for training (last combo stays on screen).

inline void DrawData(Painter &d, const Tracker &tr, const Frame &f) {
	const float u = f.gh / 272.0f;
	const u32 side[2] = { 0xFF5AC8FA, 0xFFFF6B6B };
	for (int i = 0; i < 2; i++) {
		const PlayerView &v = tr.p[i];
		const float bw = 124 * u, bh = 50 * u, by = f.gy + 40 * u;
		const float bx = i == 0 ? f.gx + 6 * u : f.gx + f.gw - 6 * u - bw;
		d.Rect(bx, by, bw, bh, 0xB8000000);
		d.Rect(bx, by, 2 * u, bh, side[i]);
		const float x = bx + 6 * u, line = 9.5f * u;
		float y = by + 6.5f * u;
		d.Text(Name(v.character), x, y, 8 * u, side[i], ALIGN_L);
		d.Text(Num((int)v.health) + "/" + Num(kMaxHealth) + "  " + Pct(v.health, kMaxHealth), bx + bw - 5 * u, y, 7.5f * u, 0xFFFFFFFF, ALIGN_R);
		auto bar = [&](float yy, float frac, u32 c) {
			d.Rect(bx + 56 * u, yy - 2 * u, bw - 61 * u, 4 * u, 0xFF262630);
			d.Rect(bx + 56 * u, yy - 2 * u, (bw - 61 * u) * frac, 4 * u, c);
		};
		y += line;
		const float ten = v.tension / kMaxTension;
		char buf[64];
		snprintf(buf, sizeof(buf), "Tensión %.1f%%", ten * 100.0f);
		d.Text(buf, x, y, 7 * u, 0xFFE0E0E8, ALIGN_L, false);
		bar(y, ten, ten >= 0.5f ? 0xFFFFD34A : 0xFF6E8BFF);
		y += line;
		const float burst = v.burst / kMaxBurst;
		snprintf(buf, sizeof(buf), "Burst %d%%", (int)(burst * 100.0f));
		d.Text(buf, x, y, 7 * u, 0xFFE0E0E8, ALIGN_L, false);
		bar(y, burst, burst >= 1.0f ? 0xFFFFFFFF : 0xFF59B3FF);
		y += line;
		if (v.comboShown) {
			snprintf(buf, sizeof(buf), "Combo %d hits  %d daño", v.comboHits, v.comboDamage);
			d.Text(buf, x, y, 7.5f * u, 0xFFFFD34A, ALIGN_L, false);
		} else if (v.lastComboHits >= 2) {
			snprintf(buf, sizeof(buf), "Último: %d hits  %d daño", v.lastComboHits, v.lastComboDamage);
			d.Text(buf, x, y, 7 * u, 0xFFA0A0B0, ALIGN_L, false);
		} else {
			d.Text("Sin combos todavía", x, y, 7 * u, 0xFF70707C, ALIGN_L, false);
		}
	}
}

inline void Build(int design, const Tracker &tr, const Frame &f, float opacity, std::vector<Prim> *out) {
	out->clear();
	if (tr.visible <= 0.0f)
		return;
	Painter d(out, opacity * tr.visible);
	switch (design) {
	case DESIGN_NEON: DrawNeon(d, tr, f); break;
	case DESIGN_ARCADE: DrawArcade(d, tr, f); break;
	case DESIGN_SIDE: DrawSide(d, tr, f); break;
	case DESIGN_DATA: DrawData(d, tr, f); break;
	default: break;
	}
}

}  // namespace AcHud
