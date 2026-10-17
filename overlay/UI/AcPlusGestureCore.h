// GGXX AC+ launcher build: touch gestures in the style of Skullgirls Mobile.
//
// Buttons are Guilty Gear's (P K S HS D) or BlazBlue's (A B C D): see ButtonMap.
//
//   Tap ........................ attack chain P -> K -> S -> HS (taps close together)
//   Swipe left / right ......... dash / back dash (keep the finger down to keep running)
//   Swipe up (or diagonally) ... jump, straight or toward that side
//   Swipe down ................. sweep (2D)
//   Two-finger tap ............. Dust (D)
//   Two-finger swipe ........... throw toward that side (HS); upward = high jump
//   Two-finger hold ............ block (drag down while holding = crouching block)
//   Hold one finger and drag ... floating stick: walk, crouch, block, jump
//   Buttons .................... special moves and Overdrive of the character (the motion
//                                is entered for you), Roman Cancel, Burst, Start, X and O
//
// The recognizer turns touches into "steps" (PSP buttons held for a number of frames). The
// sequencer plays them back one emulated frame at a time, so motions like 236 or 632146 come
// out exactly as the game expects. Directions that depend on which way the character faces
// (forward/back) are resolved on every frame.
//
// No PPSSPP headers: tested on a PC by scripts/test_gesture_core.cpp.

#pragma once

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <deque>
#include <map>
#include <mutex>
#include <string>
#include <vector>

namespace AcGesture {

typedef uint32_t u32;

// PSP buttons (same values as Core/HLE/sceCtrl.h).
enum : u32 {
	PSP_SELECT = 0x0001,
	PSP_START = 0x0008,
	PSP_UP = 0x0010,
	PSP_RIGHT = 0x0020,
	PSP_DOWN = 0x0040,
	PSP_LEFT = 0x0080,
	PSP_LTRIGGER = 0x0100,
	PSP_RTRIGGER = 0x0200,
	PSP_TRIANGLE = 0x1000,
	PSP_CIRCLE = 0x2000,
	PSP_CROSS = 0x4000,
	PSP_SQUARE = 0x8000,
};
const u32 kDirs = PSP_UP | PSP_RIGHT | PSP_DOWN | PSP_LEFT;

// The game's default PSP mapping: square P, cross K, triangle S, circle HS, R Dust.
const u32 BTN_P = PSP_SQUARE, BTN_K = PSP_CROSS, BTN_S = PSP_TRIANGLE, BTN_HS = PSP_CIRCLE, BTN_D = PSP_RTRIGGER;
const u32 kAttacks = BTN_P | BTN_K | BTN_S | BTN_HS | BTN_D;

// Forward / back, turned into left or right when the frame is sent.
const u32 REL_FORWARD = 1u << 30;
const u32 REL_BACK = 1u << 29;

// Every button this module may press (it only releases what it pressed itself).
const u32 kOwned = kDirs | kAttacks | PSP_START;

// What each gesture presses, per game.
struct ButtonMap {
	u32 chain[4];             // taps in a row
	int chainLength;
	const char *chainNames[4];
	u32 drive;                // two-finger tap
	const char *driveName;
	u32 sweep;                // swipe down (held with down)
	const char *sweepName;
	u32 throwButtons;         // two-finger swipe, with that direction
	u32 rapidCancel;
	u32 burst;
	bool blazblue;            // move letters A B C D instead of P K S HS D
};

// Guilty Gear (PSP defaults): square P, cross K, triangle S, circle HS, R Dust.
inline ButtonMap GuiltyGearMap() {
	return ButtonMap{ { BTN_P, BTN_K, BTN_S, BTN_HS }, 4, { "P", "K", "S", "HS" },
		BTN_D, "D", BTN_D, "2D", BTN_HS, BTN_P | BTN_K | BTN_S, BTN_D | BTN_HS, false };
}

// BlazBlue (PSP defaults): square A, triangle B, circle C, cross D (Drive).
// Throw B+C, Rapid Cancel A+B+C, Barrier Burst A+B+C+D.
inline ButtonMap BlazBlueMap() {
	return ButtonMap{ { PSP_SQUARE, PSP_TRIANGLE, PSP_CIRCLE, 0 }, 3, { "A", "B", "C", "" },
		PSP_CROSS, "D", PSP_CIRCLE, "2C", PSP_TRIANGLE | PSP_CIRCLE,
		PSP_SQUARE | PSP_TRIANGLE | PSP_CIRCLE, PSP_SQUARE | PSP_TRIANGLE | PSP_CIRCLE | PSP_CROSS, true };
}

inline u32 CancelOpposites(u32 m) {
	if ((m & (PSP_LEFT | PSP_RIGHT)) == (PSP_LEFT | PSP_RIGHT))
		m &= ~(PSP_LEFT | PSP_RIGHT);
	if ((m & (PSP_UP | PSP_DOWN)) == (PSP_UP | PSP_DOWN))
		m &= ~PSP_UP;   // down wins: crouching block beats an accidental jump
	return m;
}

inline u32 Resolve(u32 m, bool facingRight) {
	u32 out = m & ~(REL_FORWARD | REL_BACK);
	if (m & REL_FORWARD)
		out |= facingRight ? PSP_RIGHT : PSP_LEFT;
	if (m & REL_BACK)
		out |= facingRight ? PSP_LEFT : PSP_RIGHT;
	return CancelOpposites(out);
}

// Numpad notation (6 = forward), relative to the facing.
inline u32 NumpadMask(int digit) {
	switch (digit) {
	case 1: return PSP_DOWN | REL_BACK;
	case 2: return PSP_DOWN;
	case 3: return PSP_DOWN | REL_FORWARD;
	case 4: return REL_BACK;
	case 6: return REL_FORWARD;
	case 7: return PSP_UP | REL_BACK;
	case 8: return PSP_UP;
	case 9: return PSP_UP | REL_FORWARD;
	default: return 0;
	}
}

// ---------------------------------------------------------------------------------------------
// Motions such as "236P", "632146HS", "[4]6S", "214214S", "22K" or "PKS".

struct Motion {
	std::string text;
	int charge = 0;           // direction held first ([4]6S -> 4), 0 if none
	std::vector<int> dirs;    // numpad digits
	u32 buttons = 0;
	bool valid = false;
};

// blazblue: buttons are A B C D (square, triangle, circle, cross) instead of P K S HS D.
inline Motion ParseMotion(const std::string &spec, bool blazblue = false) {
	Motion m;
	std::string s;
	for (char c : spec) {
		if (c == ' ' || c == '+' || c == '.' || c == '\t')
			continue;
		s += (char)((c >= 'a' && c <= 'z') ? c - 32 : c);
	}
	m.text = s;
	size_t i = 0;
	if (s.size() >= 3 && s[0] == '[' && s[2] == ']' && s[1] >= '1' && s[1] <= '9') {
		m.charge = s[1] - '0';
		i = 3;
	}
	while (i < s.size() && s[i] >= '1' && s[i] <= '9') {
		m.dirs.push_back(s[i] - '0');
		i++;
	}
	while (i < s.size()) {
		if (blazblue) {
			switch (s[i]) {
			case 'A': m.buttons |= PSP_SQUARE; break;
			case 'B': m.buttons |= PSP_TRIANGLE; break;
			case 'C': m.buttons |= PSP_CIRCLE; break;
			case 'D': m.buttons |= PSP_CROSS; break;
			default: return m;   // invalid
			}
			i++;
			continue;
		}
		if (s.compare(i, 2, "HS") == 0) { m.buttons |= BTN_HS; i += 2; continue; }
		switch (s[i]) {
		case 'P': m.buttons |= BTN_P; break;
		case 'K': m.buttons |= BTN_K; break;
		case 'S': m.buttons |= BTN_S; break;
		case 'H': m.buttons |= BTN_HS; break;
		case 'D': m.buttons |= BTN_D; break;
		default: return m;   // invalid
		}
		i++;
	}
	if (m.charge && m.dirs.empty())
		return m;
	m.valid = (!m.dirs.empty() || m.buttons) && m.dirs.size() <= 12;
	return m;
}

// ---------------------------------------------------------------------------------------------
// Steps and the frame sequencer.

struct Step {
	u32 mask = 0;          // buttons and directions held during these frames
	int frames = 1;
	bool ownDirs = false;  // directions come only from this step (the stick is ignored)
	u32 charge = 0;        // a charge: skipped if the stick already held this long
};

const int kDirFrames = 2;      // each direction of a motion
const int kPressFrames = 3;    // the button at the end
const int kChargeFrames = 48;  // charge moves ([4]6, [2]8)
const int kMaxQueuedFrames = 120;

inline std::vector<Step> MotionSteps(const Motion &m) {
	std::vector<Step> out;
	if (!m.valid)
		return out;
	if (m.charge)
		out.push_back(Step{ NumpadMask(m.charge), kChargeFrames, true, NumpadMask(m.charge) });
	for (size_t i = 0; i < m.dirs.size(); i++) {
		bool last = i + 1 == m.dirs.size();
		out.push_back(Step{ NumpadMask(m.dirs[i]) | (last ? m.buttons : 0), last ? kPressFrames : kDirFrames, true, 0 });
	}
	if (m.dirs.empty())
		out.push_back(Step{ m.buttons, kPressFrames, false, 0 });
	out.push_back(Step{ 0, 1, false, 0 });   // release, so the next press is a new press
	return out;
}

inline std::vector<Step> PressSteps(u32 buttons, int frames = kPressFrames) {
	return { Step{ buttons, frames, false, 0 }, Step{ 0, 1, false, 0 } };
}

// Touch side (UI thread) pushes steps and holds; the emulator calls Tick() once per frame.
class Sequencer {
public:
	void Push(const std::vector<Step> &steps) {
		std::lock_guard<std::mutex> guard(mutex_);
		int queued = 0;
		for (const Step &s : queue_)
			queued += s.frames;
		if (queued > kMaxQueuedFrames)
			return;
		for (const Step &s : steps)
			queue_.push_back(s);
	}

	// A held input, kept until changed (0 removes it). Owner = a finger or a group.
	void SetHold(int owner, u32 mask) {
		std::lock_guard<std::mutex> guard(mutex_);
		if (mask)
			holds_[owner] = mask;
		else
			holds_.erase(owner);
	}

	void ClearAll() {
		std::lock_guard<std::mutex> guard(mutex_);
		holds_.clear();
		queue_.clear();
		begun_ = false;
	}

	bool Busy() {
		std::lock_guard<std::mutex> guard(mutex_);
		return !queue_.empty();
	}

	// Buttons for this frame.
	u32 Tick(bool facingRight) {
		std::lock_guard<std::mutex> guard(mutex_);
		u32 raw = 0;
		for (const auto &h : holds_)
			raw |= h.second;
		const u32 held = Resolve(raw, facingRight);
		static const u32 dirs[4] = { PSP_UP, PSP_DOWN, PSP_LEFT, PSP_RIGHT };
		for (int i = 0; i < 4; i++)
			heldFrames_[i] = (held & dirs[i]) ? heldFrames_[i] + 1 : 0;

		// Charge already held with the stick: go straight to the release part.
		while (!queue_.empty() && queue_.front().charge && !begun_) {
			u32 c = Resolve(queue_.front().charge, facingRight);
			if (HeldFor(c) >= queue_.front().frames)
				queue_.pop_front();
			else
				break;
		}

		u32 out = held;
		if (!queue_.empty()) {
			Step &s = queue_.front();
			const u32 m = Resolve(s.mask, facingRight);
			out = s.ownDirs ? ((held & ~kDirs) | m) : CancelOpposites(held | m);
			begun_ = true;
			if (--s.frames <= 0) {
				queue_.pop_front();
				begun_ = false;
			}
		}
		return out & kOwned;
	}

private:
	int HeldFor(u32 dirMask) const {
		static const u32 dirs[4] = { PSP_UP, PSP_DOWN, PSP_LEFT, PSP_RIGHT };
		int best = 1 << 30;
		bool any = false;
		for (int i = 0; i < 4; i++) {
			if (dirMask & dirs[i]) {
				best = std::min(best, heldFrames_[i]);
				any = true;
			}
		}
		return any ? best : 0;
	}

	std::mutex mutex_;
	std::deque<Step> queue_;
	std::map<int, u32> holds_;
	int heldFrames_[4] = {};
	bool begun_ = false;
};

// ---------------------------------------------------------------------------------------------
// Gesture recognizer. Coordinates in dp (any unit works if the parameters match).

enum Action {
	ACT_NONE = 0,
	ACT_SPECIAL1, ACT_SPECIAL2, ACT_SPECIAL3, ACT_OVERDRIVE,
	ACT_RC, ACT_BURST,
	ACT_START, ACT_CONFIRM, ACT_BACK,
	ACT_FLIP,
};

struct Button {
	int action = ACT_NONE;
	float x = 0, y = 0, r = 0;
	bool down = false;
};

// A spot left to other controls (the emulator's pause button).
struct Zone {
	float x = 0, y = 0, r = 0;
};

enum FxKind { FX_TAP, FX_SWIPE, FX_TWO, FX_BUTTON };

struct Fx {
	FxKind kind;
	float x, y, x2, y2;
	std::string label;
	double t;
};

struct StickView {
	float ox, oy, x, y;
	u32 mask;
};

enum TouchType { T_DOWN, T_MOVE, T_UP, T_CANCEL };

struct Params {
	float swipeDist = 30.0f;       // movement that makes a swipe
	float swipeMaxTime = 0.40f;    // a slower drag becomes the stick
	float holdTime = 0.18f;        // finger still this long = stick (one) / block (two)
	float groupWindow = 0.16f;     // second finger this soon = two-finger gesture
	float stickDead = 14.0f;
	float stickRadius = 52.0f;
	float crouchDrag = 20.0f;      // two-finger block: drag down this much to crouch
	float chainWindow = 0.60f;     // taps closer than this continue the chain
};

class Recognizer {
public:
	static const int kMaxPointers = 10;
	static const int kGroupOwner = 100;

	explicit Recognizer(Sequencer *seq) : seq_(seq) {}

	Params params;

	void SetLayout(const std::vector<Button> &buttons, const std::vector<Zone> &zones) {
		buttons_ = buttons;
		zones_ = zones;
	}
	const std::vector<Button> &Buttons() const { return buttons_; }

	void SetButtonMap(const ButtonMap &map) { map_ = map; }
	const ButtonMap &Map() const { return map_; }

	void SetMove(int slot, const Motion &m) {
		if (slot >= 0 && slot < 4)
			moves_[slot] = m;
	}
	const Motion &Move(int slot) const { return moves_[slot]; }

	bool Flipped() const { return flip_; }
	bool Blocking() const { return group_.active && group_.state == G_BLOCK; }
	void BlockCenter(float *x, float *y) const {
		*x = (ptr_[group_.a].x + ptr_[group_.b].x) * 0.5f;
		*y = (ptr_[group_.a].y + ptr_[group_.b].y) * 0.5f;
	}
	const std::vector<Fx> &Effects() const { return fx_; }

	std::vector<StickView> Sticks() const {
		std::vector<StickView> out;
		for (int i = 0; i < kMaxPointers; i++) {
			if (ptr_[i].state == P_STICK)
				out.push_back(StickView{ ptr_[i].ox, ptr_[i].oy, ptr_[i].x, ptr_[i].y, ptr_[i].stickMask });
		}
		return out;
	}

	void Touch(int id, float x, float y, TouchType type, double t) {
		if (id < 0 || id >= kMaxPointers)
			return;
		Pointer &p = ptr_[id];
		switch (type) {
		case T_DOWN:
			if (p.state != P_FREE)
				End(id, t, false);
			Down(id, x, y, t);
			break;
		case T_MOVE:
			if (p.state == P_FREE)
				return;
			p.x = x;
			p.y = y;
			Evaluate(id, t);
			break;
		case T_UP:
		case T_CANCEL:
			if (p.state == P_FREE)
				return;
			p.x = x;
			p.y = y;
			End(id, t, type == T_UP);
			break;
		}
	}

	// Call every frame: holds turn into the stick or a block even without movement.
	void Update(double t) {
		for (int i = 0; i < kMaxPointers; i++) {
			if (ptr_[i].state == P_PENDING)
				Evaluate(i, t);
		}
		if (group_.active && group_.state == G_PENDING)
			EvaluateGroup(t);
		fx_.erase(std::remove_if(fx_.begin(), fx_.end(), [t](const Fx &f) { return t - f.t > 0.45; }), fx_.end());
	}

	void ReleaseAll() {
		for (int i = 0; i < kMaxPointers; i++)
			ptr_[i] = Pointer();
		for (Button &b : buttons_)
			b.down = false;
		group_ = Group();
		seq_->ClearAll();
	}

private:
	enum PState { P_FREE, P_PENDING, P_STICK, P_DONE, P_BUTTON, P_GROUP, P_IGNORED };
	enum GState { G_PENDING, G_BLOCK, G_DONE };

	struct Pointer {
		PState state = P_FREE;
		float x0 = 0, y0 = 0, x = 0, y = 0, ox = 0, oy = 0;
		double t0 = 0;
		int button = -1;
		u32 stickMask = 0;
	};
	struct Group {
		bool active = false;
		int a = -1, b = -1;
		double t0 = 0;
		float cx0 = 0, cy0 = 0;
		GState state = G_PENDING;
	};

	static float AngleDeg(float dx, float dy) {
		return std::atan2(-dy, dx) * 57.29578f;   // 0 = right, 90 = up
	}

	void AddFx(FxKind kind, float x, float y, float x2, float y2, const std::string &label, double t) {
		if (fx_.size() > 16)
			fx_.erase(fx_.begin());
		fx_.push_back(Fx{ kind, x, y, x2, y2, label, t });
	}

	void Down(int id, float x, float y, double t) {
		Pointer &p = ptr_[id];
		p = Pointer();
		p.x0 = p.x = x;
		p.y0 = p.y = y;
		p.t0 = t;
		for (const Zone &z : zones_) {
			if (Dist(x, y, z.x, z.y) <= z.r) {
				p.state = P_IGNORED;
				return;
			}
		}
		for (size_t i = 0; i < buttons_.size(); i++) {
			const Button &b = buttons_[i];
			if (Dist(x, y, b.x, b.y) <= b.r * 1.12f) {
				p.state = P_BUTTON;
				p.button = (int)i;
				buttons_[i].down = true;
				PressButton(id, b, t);
				return;
			}
		}
		// Second finger right after a first one that hasn't done anything yet: two-finger gesture.
		if (!group_.active) {
			int partner = -1;
			for (int j = 0; j < kMaxPointers; j++) {
				if (j != id && ptr_[j].state == P_PENDING && t - ptr_[j].t0 <= params.groupWindow)
					partner = j;
			}
			if (partner >= 0) {
				group_ = Group();
				group_.active = true;
				group_.a = partner;
				group_.b = id;
				group_.t0 = ptr_[partner].t0;
				group_.cx0 = (ptr_[partner].x0 + x) * 0.5f;
				group_.cy0 = (ptr_[partner].y0 + y) * 0.5f;
				ptr_[partner].state = P_GROUP;
				p.state = P_GROUP;
				return;
			}
		}
		p.state = P_PENDING;
	}

	void Evaluate(int id, double t) {
		Pointer &p = ptr_[id];
		switch (p.state) {
		case P_PENDING: {
			const float d = Dist(p.x, p.y, p.x0, p.y0);
			const double dt = t - p.t0;
			if (d >= params.swipeDist) {
				if (dt <= params.swipeMaxTime)
					Swipe(id, t);
				else
					BecomeStick(id);
			} else if (dt >= params.holdTime) {
				BecomeStick(id);
			}
			break;
		}
		case P_STICK:
			UpdateStick(id);
			break;
		case P_GROUP:
			if (group_.active && (group_.a == id || group_.b == id))
				EvaluateGroup(t);
			break;
		default:
			break;
		}
	}

	void End(int id, double t, bool released) {
		Pointer &p = ptr_[id];
		switch (p.state) {
		case P_PENDING:
			if (released) {
				const float d = Dist(p.x, p.y, p.x0, p.y0);
				if (d >= params.swipeDist && t - p.t0 <= params.swipeMaxTime)
					Swipe(id, t);
				else
					Tap(id, t);
			}
			break;
		case P_STICK:
			seq_->SetHold(id, 0);
			break;
		case P_BUTTON:
			if (p.button >= 0 && p.button < (int)buttons_.size()) {
				buttons_[p.button].down = false;
				seq_->SetHold(id, 0);
			}
			break;
		case P_GROUP:
			GroupMemberUp(id, t, released);
			break;
		default:
			break;
		}
		p = Pointer();
	}

	void Tap(int id, double t) {
		const int n = std::max(1, std::min(4, map_.chainLength));
		if (lastTap_ >= 0 && t - lastTap_ <= params.chainWindow)
			chainIndex_ = (chainIndex_ + 1) % n;
		else
			chainIndex_ = 0;
		lastTap_ = t;
		seq_->Push(PressSteps(map_.chain[chainIndex_]));
		AddFx(FX_TAP, ptr_[id].x, ptr_[id].y, 0, 0, map_.chainNames[chainIndex_], t);
	}

	void Swipe(int id, double t) {
		Pointer &p = ptr_[id];
		const float a = AngleDeg(p.x - p.x0, p.y - p.y0);
		const float fa = std::fabs(a);
		std::string label;
		if (fa <= 30.0f || fa >= 150.0f) {
			// Dash toward that side: the game turns right-right into a dash or a back dash.
			const u32 dir = fa <= 30.0f ? PSP_RIGHT : PSP_LEFT;
			seq_->Push({ Step{ dir, 2, true, 0 }, Step{ 0, 2, true, 0 }, Step{ dir, 2, true, 0 } });
			label = "Dash";
			// Keep the finger down to keep running; it now works as the stick.
			BecomeStick(id);
			p.ox = p.x0;
			p.oy = p.y0;
			UpdateStick(id);
		} else if (a > 0.0f) {
			u32 dir = PSP_UP;
			if (a < 70.0f)
				dir |= PSP_RIGHT;
			else if (a > 110.0f)
				dir |= PSP_LEFT;
			seq_->Push({ Step{ dir, 3, true, 0 }, Step{ 0, 1, false, 0 } });
			label = "Salto";
			p.state = P_DONE;
		} else {
			seq_->Push({ Step{ PSP_DOWN, 1, true, 0 }, Step{ PSP_DOWN | map_.sweep, kPressFrames, true, 0 }, Step{ 0, 1, false, 0 } });
			label = map_.sweepName;
			p.state = P_DONE;
		}
		AddFx(FX_SWIPE, p.x0, p.y0, p.x, p.y, label, t);
	}

	void BecomeStick(int id) {
		Pointer &p = ptr_[id];
		p.state = P_STICK;
		p.ox = p.x0;
		p.oy = p.y0;
		UpdateStick(id);
	}

	void UpdateStick(int id) {
		Pointer &p = ptr_[id];
		float vx = p.x - p.ox, vy = p.y - p.oy;
		float len = std::sqrt(vx * vx + vy * vy);
		if (len > params.stickRadius) {
			// Floating stick: the center follows the finger.
			p.ox = p.x - vx / len * params.stickRadius;
			p.oy = p.y - vy / len * params.stickRadius;
			vx = p.x - p.ox;
			vy = p.y - p.oy;
			len = params.stickRadius;
		}
		u32 mask = 0;
		if (len >= params.stickDead) {
			const float a = AngleDeg(vx, vy);
			const float fa = std::fabs(a);
			if (fa <= 60.0f)
				mask |= PSP_RIGHT;
			else if (fa >= 120.0f)
				mask |= PSP_LEFT;
			if (a > 30.0f && a < 150.0f)
				mask |= PSP_UP;
			else if (a < -30.0f && a > -150.0f)
				mask |= PSP_DOWN;
		}
		if (mask != p.stickMask) {
			p.stickMask = mask;
			seq_->SetHold(id, mask);
		}
	}

	void EvaluateGroup(double t) {
		if (!group_.active)
			return;
		const Pointer &a = ptr_[group_.a];
		const Pointer &b = ptr_[group_.b];
		const float cx = (a.x + b.x) * 0.5f, cy = (a.y + b.y) * 0.5f;
		const float dx = cx - ((a.x0 + b.x0) * 0.5f), dy = cy - ((a.y0 + b.y0) * 0.5f);
		const float d = std::sqrt(dx * dx + dy * dy);
		const double dt = t - group_.t0;
		if (group_.state == G_PENDING) {
			if (d >= params.swipeDist && dt <= params.swipeMaxTime + 0.1) {
				const float an = AngleDeg(dx, dy);
				const float fa = std::fabs(an);
				if (fa <= 45.0f || fa >= 135.0f) {
					const u32 dir = fa <= 45.0f ? PSP_RIGHT : PSP_LEFT;
					seq_->Push({ Step{ dir, 1, true, 0 }, Step{ dir | map_.throwButtons, kPressFrames, true, 0 }, Step{ 0, 1, false, 0 } });
					AddFx(FX_TWO, cx - dx, cy - dy, cx, cy, "Agarre", t);
				} else if (an > 0.0f) {
					seq_->Push({ Step{ PSP_DOWN, 2, true, 0 }, Step{ PSP_UP, 3, true, 0 }, Step{ 0, 1, false, 0 } });
					AddFx(FX_TWO, cx - dx, cy - dy, cx, cy, "Supersalto", t);
				}
				group_.state = G_DONE;
			} else if (dt >= params.holdTime) {
				group_.state = G_BLOCK;
				group_.cx0 = cx;
				group_.cy0 = cy;
			}
		}
		if (group_.state == G_BLOCK) {
			const bool crouch = cy - group_.cy0 >= params.crouchDrag;
			seq_->SetHold(kGroupOwner, REL_BACK | (crouch ? (u32)PSP_DOWN : 0u));
		}
	}

	void GroupMemberUp(int id, double t, bool released) {
		if (!group_.active || (group_.a != id && group_.b != id))
			return;
		if (group_.state == G_PENDING && released) {
			const Pointer &a = ptr_[group_.a];
			const Pointer &b = ptr_[group_.b];
			const float dx = (a.x + b.x - a.x0 - b.x0) * 0.5f, dy = (a.y + b.y - a.y0 - b.y0) * 0.5f;
			if (std::sqrt(dx * dx + dy * dy) < params.swipeDist) {
				seq_->Push(PressSteps(map_.drive));
				AddFx(FX_TWO, (a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f, 0, 0, map_.driveName, t);
			}
		}
		if (group_.state == G_BLOCK)
			seq_->SetHold(kGroupOwner, 0);
		group_.state = G_DONE;
		const int other = group_.a == id ? group_.b : group_.a;
		if (ptr_[other].state != P_GROUP) {
			group_ = Group();
		} else {
			// The other finger does nothing more until it is lifted.
			ptr_[other].state = P_DONE;
			group_ = Group();
		}
	}

	void PressButton(int id, const Button &b, double t) {
		switch (b.action) {
		case ACT_SPECIAL1:
		case ACT_SPECIAL2:
		case ACT_SPECIAL3:
		case ACT_OVERDRIVE: {
			const Motion &m = moves_[b.action - ACT_SPECIAL1];
			if (m.valid)
				seq_->Push(MotionSteps(m));
			AddFx(FX_BUTTON, b.x, b.y, 0, 0, m.text, t);
			break;
		}
		case ACT_RC:
			seq_->Push(PressSteps(map_.rapidCancel, 2));
			AddFx(FX_BUTTON, b.x, b.y, 0, 0, "RC", t);
			break;
		case ACT_BURST:
			seq_->Push(PressSteps(map_.burst, 2));
			AddFx(FX_BUTTON, b.x, b.y, 0, 0, "Burst", t);
			break;
		case ACT_START:
			seq_->SetHold(id, PSP_START);
			break;
		case ACT_CONFIRM:
			seq_->SetHold(id, PSP_CROSS);
			break;
		case ACT_BACK:
			seq_->SetHold(id, PSP_CIRCLE);
			break;
		case ACT_FLIP:
			flip_ = !flip_;
			break;
		default:
			break;
		}
	}

	static float Dist(float x1, float y1, float x2, float y2) {
		return std::sqrt((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2));
	}

	Sequencer *seq_;
	Pointer ptr_[kMaxPointers];
	Group group_;
	std::vector<Button> buttons_;
	std::vector<Zone> zones_;
	Motion moves_[4];
	ButtonMap map_ = GuiltyGearMap();
	std::vector<Fx> fx_;
	double lastTap_ = -1.0;
	int chainIndex_ = 0;
	bool flip_ = false;
};

}  // namespace AcGesture
