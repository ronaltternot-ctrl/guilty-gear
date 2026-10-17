// GGXX AC+ launcher build: hitbox / hurtbox viewer, detection core.
//
// No PPSSPP dependencies here, so it can be tested on its own with a simulated memory image
// (see scripts/test_hitbox_core.cpp). AcPlusHitboxes.h connects it to the emulator.
//
// What is known (US version, ULUS-10409):
//   * CWCheat "1P Max Health" writes 0x0913D01E and the 1P/2P codes are 0x124 bytes apart,
//     so the fighters live at 0x0913D000 and 0x0913D124. Health at +0x1E is exactly where
//     Guilty Gear XX AC+R (PC) keeps it, so the PSP layout follows the same engine.
//   * Player object layout in the PC versions (from kof-combo-hitboxes by odabugs):
//       +R:      +0x00 character id, +0x02 facing, +0x54 box list pointer, +0x84 box count,
//                +0xB0 / +0xB4 pivot x / y (1/100 pixel; y = 0 on the ground, negative up).
//       #Reload: +0x4C box pointer, +0x78 count, +0xA4 pivot.
//     Each box: s16 x, s16 y, s16 width, s16 height (pixels from the pivot), u8 type, 12 bytes.
//     Type 1 = attack (hitbox), 2 = vulnerable (hurtbox), 0x17 = vulnerable anywhere.
// What is not known for the PSP build is exactly where the box pointer, the pivot and the
// camera sit, so they are found at run time: the +R values are tried first, then nearby
// offsets, and only data that looks like real box lists / positions over many frames is kept.

#pragma once

#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <algorithm>
#include <string>
#include <utility>
#include <vector>

namespace AcHitbox {

typedef uint8_t u8;
typedef uint16_t u16;
typedef int16_t s16;
typedef uint32_t u32;
typedef int32_t s32;

// Read access to PSP memory.
struct Mem {
	virtual ~Mem() {}
	virtual bool Valid(u32 addr, u32 size) const = 0;
	virtual const u8 *Ptr(u32 addr) const = 0;   // only called after Valid()
};

inline u8 R8(const Mem &m, u32 a) { return m.Valid(a, 1) ? *m.Ptr(a) : 0; }
inline u16 R16(const Mem &m, u32 a) { u16 v = 0; if (m.Valid(a, 2)) memcpy(&v, m.Ptr(a), 2); return v; }
inline s16 R16s(const Mem &m, u32 a) { return (s16)R16(m, a); }
inline u32 R32(const Mem &m, u32 a) { u32 v = 0; if (m.Valid(a, 4)) memcpy(&v, m.Ptr(a), 4); return v; }
inline s32 R32s(const Mem &m, u32 a) { return (s32)R32(m, a); }

const u32 kRamStart = 0x08800000;
const u32 kRamEnd = 0x0A000000;
const u32 kPriorP1 = 0x0913D000;
const u32 kPriorStride = 0x124;
const int kPriorBoxPtr = 0x54;
const int kPriorBoxCount = 0x84;
const int kPriorPivot = 0xB0;
const int kBoxSize = 12;
const int kMaxBoxes = 32;
const int kMaxObjects = 40;

enum BoxType { BOX_OTHER = 0, BOX_ATTACK = 1, BOX_HURT = 2, BOX_HURT_ANY = 0x17 };

struct Box {
	// World coordinates (1/100 pixel), already flipped for facing.
	float x1, y1, x2, y2;
	int type;
	int owner;   // 0 = P1, 1 = P2, 2+ = other objects (projectiles)
};

struct Pivot {
	s32 x, y;
	int owner;
};

// Screen projection: PSP screen pixels (480 x 272).
struct Camera {
	bool fromMemory = false;     // camera struct found in memory
	bool centerFound = false;    // at least the camera center was found
	float left = 0, bottom = 0, width = 0, height = 0;
	float center = 0;
	float scale = 0.0075f;       // screen pixels per world unit
	float groundY = 248.0f;
};

struct Settings {
	// Manual overrides (0 / -1 = detect).
	u32 p1 = 0, stride = 0;
	int boxPtr = -1, boxCount = -1, pivot = -1;
	u32 camCenter = 0;
	float scale = 0, groundY = 0;
};

class Detector {
public:
	enum Phase { NO_ANCHOR, FIND_PLAYERS, FIND_BOXES, FIND_PIVOT, RUNNING };

	void Configure(const Settings &s) { settings_ = s; }

	// Character numbers from the select screen (1..25), 0 if unknown.
	void SetAnchor(int p1, int p2) {
		if (p1 != anchor1_ || p2 != anchor2_) {
			anchor1_ = p1;
			anchor2_ = p2;
			if (phase_ == NO_ANCHOR && p1 > 0 && p2 > 0)
				phase_ = FIND_PLAYERS;
		}
	}

	Phase GetPhase() const { return phase_; }
	u32 P1() const { return p1_; }
	u32 Stride() const { return stride_; }
	int BoxPtrOffset() const { return boxPtr_; }
	int BoxCountOffset() const { return boxCount_; }
	int PivotOffset() const { return pivot_; }
	u32 CameraAddress() const { return camAddr_; }
	bool CameraFromMemory() const { return camStruct_; }

	// Incremental work; call once per emulated frame.
	void Step(const Mem &m) {
		frame_++;
		switch (phase_) {
		case NO_ANCHOR: break;
		case FIND_PLAYERS: StepFindPlayers(m); break;
		case FIND_BOXES: if (frame_ % 4 == 0) StepFindBoxes(m); break;
		case FIND_PIVOT: if (frame_ % 4 == 0) StepFindPivot(m); break;
		case RUNNING: StepCamera(m); break;
		}
	}

	std::string Status() const {
		switch (phase_) {
		case NO_ANCHOR: return "Hitboxes: esperando a que empiece un combate";
		case FIND_PLAYERS: return "Hitboxes: buscando a los personajes...";
		case FIND_BOXES: return "Hitboxes: buscando las cajas...";
		case FIND_PIVOT: return "Hitboxes: buscando la posicion...";
		case RUNNING:
			if (!camCenterFound_) return "Hitboxes: buscando la camara (muevete un poco)";
			return "";
		}
		return "";
	}

	// Current boxes and pivots (world coordinates).
	void Collect(const Mem &m, bool projectiles, std::vector<Box> *boxes, std::vector<Pivot> *pivots) const {
		boxes->clear();
		pivots->clear();
		if (phase_ != RUNNING)
			return;
		int count = projectiles ? kMaxObjects : 2;
		for (int i = 0; i < count; i++) {
			u32 obj = p1_ + (u32)i * stride_;
			if (!m.Valid(obj, stride_))
				break;
			if (i < 2) {
				// Fighters: skip when the slot holds no character (menus, loading).
				int id = R16(m, obj);
				if (id == 0 || id > 26)
					continue;
			} else {
				// Other objects: only if active and with a believable box list and position.
				if (R16(m, obj) == 0 || BoxListScore(m, obj, boxPtr_, boxCount_, false) <= 0)
					continue;
				if (R32s(m, obj + 0x08) == -1)   // +R: projectile hitbox inactive
					continue;
			}
			s32 px = R32s(m, obj + pivot_);
			s32 py = R32s(m, obj + pivot_ + 4);
			if (!PlausiblePivot(px, py))
				continue;
			pivots->push_back(Pivot{px, py, i});
			float facing = R8(m, obj + 2) == 0 ? 1.0f : -1.0f;
			u32 list = R32(m, obj + boxPtr_);
			int n = R8(m, obj + boxCount_);
			if (n > kMaxBoxes || !m.Valid(list, (u32)n * kBoxSize))
				continue;
			for (int b = 0; b < n; b++) {
				u32 e = list + (u32)b * kBoxSize;
				int bx = R16s(m, e), by = R16s(m, e + 2), bw = R16s(m, e + 4), bh = R16s(m, e + 6);
				int type = R8(m, e + 8);
				if (bw <= 0 || bh <= 0)
					continue;
				Box box;
				box.x1 = px + bx * 100.0f * facing;
				box.x2 = box.x1 + bw * 100.0f * facing;
				box.y1 = py + by * 100.0f;
				box.y2 = box.y1 + bh * 100.0f;
				box.type = type;
				box.owner = i;
				boxes->push_back(box);
			}
		}
	}

	Camera GetCamera(const Mem &m) const {
		Camera c;
		if (settings_.scale > 0) c.scale = settings_.scale;
		if (settings_.groundY > 0) c.groundY = settings_.groundY;
		// Fallback center: midway between the fighters, like the game's camera.
		s32 x1 = R32s(m, p1_ + pivot_), x2 = R32s(m, p1_ + stride_ + pivot_);
		c.center = (x1 / 2.0f) + (x2 / 2.0f);
		if (camCenterFound_) {
			c.centerFound = true;
			c.center = (float)R32s(m, camAddr_);
		}
		if (camStruct_) {
			u32 base = camAddr_ - 0x10;
			c.fromMemory = true;
			c.bottom = (float)R32s(m, base + 0x14);
			c.left = (float)R32s(m, base + 0x20);
			c.width = (float)R32s(m, base + 0x28);
			c.height = (float)R32s(m, base + 0x2C);
			if (c.width > 1000.0f && settings_.scale <= 0)
				c.scale = 480.0f / c.width;
		}
		return c;
	}

	// World -> PSP screen pixels.
	static void Project(const Camera &c, float x, float y, float *sx, float *sy) {
		if (c.fromMemory && c.width > 1000.0f) {
			*sx = (x - c.left) * c.scale;
			*sy = 272.0f + (y - c.bottom) * c.scale;
		} else {
			*sx = 240.0f + (x - c.center) * c.scale;
			*sy = c.groundY + y * c.scale;
		}
	}

	std::string Report(const Mem &m) const {
		std::string r;
		char line[256];
		snprintf(line, sizeof(line), "phase=%d frame=%d anchor=%d,%d\n", (int)phase_, frame_, anchor1_, anchor2_);
		r += line;
		snprintf(line, sizeof(line), "p1=%08x stride=%x boxPtr=%x boxCount=%x pivot=%x\n", p1_, stride_, boxPtr_, boxCount_, pivot_);
		r += line;
		snprintf(line, sizeof(line), "camera center=%08x found=%d struct=%d candidates=%d\n", camAddr_, (int)camCenterFound_, (int)camStruct_, (int)camCands_.size());
		r += line;
		r += "best box combos:\n";
		for (size_t i = 0; i < reportBoxCombos_.size(); i++) {
			r += "  " + reportBoxCombos_[i] + "\n";
		}
		r += "best pivots:\n";
		for (size_t i = 0; i < reportPivots_.size(); i++) {
			r += "  " + reportPivots_[i] + "\n";
		}
		u32 base = p1_ ? p1_ : kPriorP1;
		u32 stride = stride_ ? stride_ : kPriorStride;
		for (int p = 0; p < 2; p++) {
			u32 obj = base + (u32)p * stride;
			snprintf(line, sizeof(line), "player %d @%08x\n", p + 1, obj);
			r += line;
			r += HexDump(m, obj, 0x140);
			if (boxPtr_ >= 0 && boxCount_ >= 0) {
				u32 list = R32(m, obj + boxPtr_);
				int n = R8(m, obj + boxCount_);
				snprintf(line, sizeof(line), " boxes @%08x n=%d\n", list, n);
				r += line;
				r += HexDump(m, list, (u32)(n > 8 ? 8 : n) * kBoxSize);
			}
		}
		if (camAddr_) {
			r += "camera area:\n";
			r += HexDump(m, camAddr_ - 0x20, 0x80);
		}
		for (size_t i = 0; i < camCands_.size() && i < 16; i++) {
			snprintf(line, sizeof(line), " camcand %08x d0=%d\n", camCands_[i].addr, camCands_[i].d0);
			r += line;
		}
		return r;
	}

private:
	struct Combo {
		u32 p1, stride;
		int ptrOff, cntOff;
		int valid = 0, total = 0;
		std::vector<u32> signatures;
	};
	struct PivotCand {
		int off;
		int plausible = 0, standing = 0, total = 0, moving = 0;
		s32 lastX1 = 0;
		bool haveLast = false;
	};
	struct CamCand {
		u32 addr;
		s32 d0;        // value - fighters' midpoint when first seen
		s32 mid0;      // midpoint when first seen
		int confirmations = 0;
	};

	static bool PlausiblePivot(s32 x, s32 y) {
		return x > -400000 && x < 400000 && y <= 0 && y > -300000;
	}

	// > 0: looks like a real box list (number of good boxes, +32 if there is a hurtbox).
	static int BoxListScore(const Mem &m, u32 obj, int ptrOff, int cntOff, bool requireHurt = true) {
		u32 list = R32(m, obj + ptrOff);
		if (list < kRamStart || list >= kRamEnd || (list & 1))
			return 0;
		int n = R8(m, obj + cntOff);
		if (n < 1 || n > kMaxBoxes || !m.Valid(list, (u32)n * kBoxSize))
			return 0;
		int good = 0, hurt = 0;
		for (int b = 0; b < n; b++) {
			u32 e = list + (u32)b * kBoxSize;
			int bx = R16s(m, e), by = R16s(m, e + 2), bw = R16s(m, e + 4), bh = R16s(m, e + 6);
			int type = R8(m, e + 8);
			bool ok = bw > 0 && bh > 0 && bw < 1000 && bh < 1000 && bx > -1000 && bx < 1000 && by > -1000 && by < 1000 && type < 0x40;
			if (ok) {
				good++;
				if (type == BOX_HURT || type == BOX_HURT_ANY)
					hurt++;
			}
		}
		// Allow one odd entry (the PC tools note the count may be off by one).
		if (good < n - 1 || good == 0 || (requireHurt && hurt == 0))
			return 0;
		return good + 32;
	}

	static u32 BoxSignature(const Mem &m, u32 obj, int ptrOff, int cntOff) {
		u32 list = R32(m, obj + ptrOff);
		return list * 31u + R8(m, obj + cntOff);
	}

	void StepFindPlayers(const Mem &m) {
		if (settings_.p1) {
			AddPlayerCandidate(settings_.p1, settings_.stride ? settings_.stride : kPriorStride);
			StartBoxSearch();
			return;
		}
		// The address the cheat codes point to, checked by the character numbers.
		if (MatchesAnchor(R16(m, kPriorP1), R16(m, kPriorP1 + kPriorStride))) {
			AddPlayerCandidate(kPriorP1, kPriorStride);
			StartBoxSearch();
			return;
		}
		// Otherwise scan memory (1 MB per frame) for both character numbers a fighter apart.
		static const u32 strides[] = { 0x124, 0x130, 0x104, 0x120, 0x128, 0x12C, 0x118, 0x11C, 0x134, 0x140 };
		u32 end = scanPos_ + 0x100000;
		if (scanPos_ < kRamStart) scanPos_ = kRamStart;
		for (u32 a = scanPos_; a < end && a + 0x200 < kRamEnd; a += 4) {
			if (!m.Valid(a, 0x200))
				continue;
			u16 first = R16(m, a);
			if (first != anchor1_ && first != anchor1_ + 1)
				continue;
			for (u32 s : strides) {
				if (MatchesAnchor(first, R16(m, a + s)) && playerCands_.size() < 8)
					AddPlayerCandidate(a, s);
			}
		}
		scanPos_ = end;
		if (scanPos_ >= kRamEnd) {
			scanPos_ = kRamStart;
			if (!playerCands_.empty())
				StartBoxSearch();
		}
	}

	// The object stores the character as the select-screen number, or that number + 1.
	bool MatchesAnchor(int id1, int id2) const {
		return (id1 == anchor1_ && id2 == anchor2_) || (id1 == anchor1_ + 1 && id2 == anchor2_ + 1);
	}

	void AddPlayerCandidate(u32 p1, u32 stride) {
		playerCands_.push_back(p1);
		playerStrides_.push_back(stride);
	}

	void StartBoxSearch() {
		combos_.clear();
		for (size_t i = 0; i < playerCands_.size(); i++) {
			if (settings_.boxPtr >= 0 && settings_.boxCount >= 0) {
				AddCombo(playerCands_[i], playerStrides_[i], settings_.boxPtr, settings_.boxCount);
				continue;
			}
			AddCombo(playerCands_[i], playerStrides_[i], kPriorBoxPtr, kPriorBoxCount);
			for (int p = 0x3C; p <= 0x70; p += 4) {
				for (int c = 0x68; c <= 0x98; c++) {
					if (p == kPriorBoxPtr && c == kPriorBoxCount)
						continue;
					AddCombo(playerCands_[i], playerStrides_[i], p, c);
				}
			}
		}
		phase_ = FIND_BOXES;
		boxSamples_ = 0;
	}

	void AddCombo(u32 p1, u32 stride, int ptrOff, int cntOff) {
		Combo c;
		c.p1 = p1;
		c.stride = stride;
		c.ptrOff = ptrOff;
		c.cntOff = cntOff;
		combos_.push_back(c);
	}

	void StepFindBoxes(const Mem &m) {
		boxSamples_++;
		for (Combo &c : combos_) {
			c.total++;
			int s1 = BoxListScore(m, c.p1, c.ptrOff, c.cntOff);
			int s2 = BoxListScore(m, c.p1 + c.stride, c.ptrOff, c.cntOff);
			if (s1 > 0 && s2 > 0) {
				c.valid++;
				u32 sig = BoxSignature(m, c.p1, c.ptrOff, c.cntOff) ^ (BoxSignature(m, c.p1 + c.stride, c.ptrOff, c.cntOff) * 7u);
				bool seen = false;
				for (u32 v : c.signatures) seen |= v == sig;
				if (!seen && c.signatures.size() < 64)
					c.signatures.push_back(sig);
			}
		}
		if (boxSamples_ < 24)
			return;
		// Best: valid in nearly every sample, and the box lists change (the fighters animate).
		const Combo *best = nullptr;
		double bestScore = 0;
		for (const Combo &c : combos_) {
			double ratio = (double)c.valid / c.total;
			if (ratio < 0.85)
				continue;
			double score = ratio * 10 + (double)c.signatures.size();
			if (c.ptrOff == kPriorBoxPtr && c.cntOff == kPriorBoxCount)
				score += 5;   // Same as the PC game.
			if (score > bestScore) {
				bestScore = score;
				best = &c;
			}
		}
		RememberBoxCombos();
		bool settle = best && (best->signatures.size() >= 3 || boxSamples_ >= 150);
		if (settle) {
			p1_ = best->p1;
			stride_ = best->stride;
			boxPtr_ = best->ptrOff;
			boxCount_ = best->cntOff;
			StartPivotSearch();
		} else if (boxSamples_ >= 600) {
			// Nothing believable in ~40 s: start over (maybe it was not a fight yet).
			Reset();
		}
	}

	void RememberBoxCombos() {
		reportBoxCombos_.clear();
		std::vector<const Combo *> sorted;
		for (const Combo &c : combos_) if (c.valid > 0) sorted.push_back(&c);
		for (size_t i = 0; i < sorted.size() && i < 8; i++) {
			size_t bi = i;
			for (size_t j = i + 1; j < sorted.size(); j++)
				if (sorted[j]->valid > sorted[bi]->valid) bi = j;
			std::swap(sorted[i], sorted[bi]);
			char line[128];
			snprintf(line, sizeof(line), "p1=%08x stride=%x ptr=%x cnt=%x valid=%d/%d variants=%d",
				sorted[i]->p1, sorted[i]->stride, sorted[i]->ptrOff, sorted[i]->cntOff, sorted[i]->valid, sorted[i]->total, (int)sorted[i]->signatures.size());
			reportBoxCombos_.push_back(line);
		}
	}

	void StartPivotSearch() {
		pivots_.clear();
		if (settings_.pivot >= 0) {
			pivot_ = settings_.pivot;
			phase_ = RUNNING;
			return;
		}
		PivotCand prior;
		prior.off = kPriorPivot;
		pivots_.push_back(prior);
		for (int o = 0x90; o <= 0xD0; o += 4) {
			if (o == kPriorPivot) continue;
			PivotCand c;
			c.off = o;
			pivots_.push_back(c);
		}
		phase_ = FIND_PIVOT;
		pivotSamples_ = 0;
	}

	void StepFindPivot(const Mem &m) {
		pivotSamples_++;
		for (PivotCand &c : pivots_) {
			s32 x1 = R32s(m, p1_ + c.off), y1 = R32s(m, p1_ + c.off + 4);
			s32 x2 = R32s(m, p1_ + stride_ + c.off), y2 = R32s(m, p1_ + stride_ + c.off + 4);
			c.total++;
			if (PlausiblePivot(x1, y1) && PlausiblePivot(x2, y2) && x1 != x2 && std::abs(x1 - x2) < 300000) {
				c.plausible++;
				if (y1 == 0 || y2 == 0) c.standing++;
				if (c.haveLast && x1 != c.lastX1) c.moving++;
			}
			c.lastX1 = x1;
			c.haveLast = true;
		}
		if (pivotSamples_ < 24)
			return;
		const PivotCand *best = nullptr;
		double bestScore = 0;
		reportPivots_.clear();
		for (const PivotCand &c : pivots_) {
			double plaus = (double)c.plausible / c.total;
			double stand = (double)c.standing / c.total;
			char line[96];
			snprintf(line, sizeof(line), "off=%x plausible=%d standing=%d moving=%d of %d", c.off, c.plausible, c.standing, c.moving, c.total);
			if (c.plausible > 0) reportPivots_.push_back(line);
			if (plaus < 0.9 || stand < 0.25)
				continue;
			double score = plaus + stand + (c.moving > 0 ? 1.0 : 0.0) + (c.off == kPriorPivot ? 0.5 : 0.0);
			if (score > bestScore) {
				bestScore = score;
				best = &c;
			}
		}
		if (best && (best->moving > 0 || pivotSamples_ >= 150)) {
			pivot_ = best->off;
			phase_ = RUNNING;
		} else if (pivotSamples_ >= 600) {
			Reset();
		}
	}

	// Camera: find the variable that follows the fighters' midpoint.
	void StepCamera(const Mem &m) {
		if (camCenterFound_)
			return;
		if (settings_.camCenter) {
			camAddr_ = settings_.camCenter;
			camCenterFound_ = true;
			CheckCameraStruct(m);
			return;
		}
		s32 x1 = R32s(m, p1_ + pivot_), x2 = R32s(m, p1_ + stride_ + pivot_);
		s32 mid = (s32)(((int64_t)x1 + x2) / 2);
		if (!camScanDone_) {
			// Start when the midpoint is far from 0, so small unrelated numbers don't match.
			if (camScanPos_ <= kRamStart && (std::abs(mid) < 15000 || std::abs(x1 - x2) < 5000))
				return;
			// Collect candidates, 1 MB per frame, skipping the fighters' own objects.
			if (camScanPos_ < kRamStart) camScanPos_ = kRamStart;
			u32 end = camScanPos_ + 0x100000;
			u32 objEnd = p1_ + stride_ * kMaxObjects;
			for (u32 a = camScanPos_; a < end && a + 4 <= kRamEnd; a += 4) {
				if (a >= p1_ && a < objEnd)
					continue;
				if (!m.Valid(a, 4))
					continue;
				s32 v = R32s(m, a);
				if (v == 0 || v == x1 || v == x2)
					continue;
				s32 d = v - mid;
				if (d > -6000 && d < 6000 && std::abs(v) > 2000 && camCands_.size() < 200000)
					camCands_.push_back(CamCand{a, d, mid, 0});
			}
			camScanPos_ = end;
			if (camScanPos_ >= kRamEnd)
				camScanDone_ = true;
			return;
		}
		if (frame_ % 15 != 0)
			return;
		// Keep candidates whose distance to the midpoint stays put while the fighters move.
		std::vector<CamCand> keep;
		keep.reserve(camCands_.size());
		for (CamCand c : camCands_) {
			s32 moved = mid - c.mid0;
			s32 d = R32s(m, c.addr) - mid;
			s32 drift = d - c.d0;
			s32 tol = 2500 + std::abs(moved) / 4;
			if (std::abs(drift) > tol)
				continue;
			if (std::abs(moved) >= 4000) {
				c.confirmations++;
				c.mid0 = mid;
				c.d0 = d;
			}
			keep.push_back(c);
		}
		camCands_.swap(keep);
		if (camCands_.empty()) {
			// Lost them all (e.g. the camera stopped at a wall): try again later.
			camScanDone_ = false;
			camScanPos_ = kRamStart;
			return;
		}
		const CamCand *best = nullptr;
		for (const CamCand &c : camCands_) {
			if (c.confirmations < 3)
				continue;
			if (!best || std::abs(c.d0) < std::abs(best->d0))
				best = &c;
		}
		if (best) {
			camAddr_ = best->addr;
			camCenterFound_ = true;
			CheckCameraStruct(m);
		}
	}

	// Same camera struct as the PC game? (x center at +0x10, left edge +0x20, width +0x28.)
	void CheckCameraStruct(const Mem &m) {
		u32 base = camAddr_ - 0x10;
		s32 center = R32s(m, camAddr_);
		s32 left = R32s(m, base + 0x20), width = R32s(m, base + 0x28), height = R32s(m, base + 0x2C);
		camStruct_ = width > 20000 && width < 200000 && height > 10000 && height < 200000 &&
			std::abs(left + width / 2 - center) < 3000;
	}

	void Reset() {
		phase_ = FIND_PLAYERS;
		playerCands_.clear();
		playerStrides_.clear();
		combos_.clear();
		pivots_.clear();
		scanPos_ = kRamStart;
	}

	static std::string HexDump(const Mem &m, u32 addr, u32 size) {
		std::string r;
		char line[128];
		for (u32 off = 0; off < size; off += 16) {
			int n = snprintf(line, sizeof(line), "  %08x:", addr + off);
			for (u32 i = 0; i < 16 && off + i < size; i++)
				n += snprintf(line + n, sizeof(line) - n, " %02x", R8(m, addr + off + i));
			r += line;
			r += "\n";
		}
		return r;
	}

	Settings settings_;
	Phase phase_ = NO_ANCHOR;
	int anchor1_ = 0, anchor2_ = 0;
	int frame_ = 0;
	u32 scanPos_ = kRamStart;
	std::vector<u32> playerCands_, playerStrides_;
	std::vector<Combo> combos_;
	int boxSamples_ = 0;
	std::vector<PivotCand> pivots_;
	int pivotSamples_ = 0;
	u32 p1_ = 0, stride_ = 0;
	int boxPtr_ = -1, boxCount_ = -1, pivot_ = -1;
	bool camScanDone_ = false, camCenterFound_ = false, camStruct_ = false;
	u32 camScanPos_ = kRamStart, camAddr_ = 0;
	std::vector<CamCand> camCands_;
	std::vector<std::string> reportBoxCombos_, reportPivots_;
};

}  // namespace AcHitbox
