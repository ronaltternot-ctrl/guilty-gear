// GGXX AC+ launcher build: touch gestures in the style of Skullgirls Mobile.
//
// Turned on from the launcher (acplus.ini, section [Gestures]). The gestures themselves are in
// AcPlusGestureCore.h. This file:
//   - adds a full-screen view over the game that receives the touches and draws the special
//     move buttons, the floating stick and short feedback marks;
//   - plays the resulting input on every emulated vblank (hook in Core/HLE/sceCtrl.cpp,
//     patches/0004), right before the game samples the controller;
//   - loads each character's special moves (read from the game's memory, US version) and
//     works out which way the player faces, from the fighters' positions found by the hitbox
//     detector (AcPlusHitboxes.h). Until those are found: player 1 faces right, player 2 left,
//     and the arrow button flips it.
//
// Included only from EmuScreen.cpp.

#pragma once

#include <algorithm>
#include <atomic>
#include <cmath>
#include <string>
#include <vector>

#include "Common/Data/Format/IniFile.h"
#include "Common/Input/InputState.h"
#include "Common/Log.h"
#include "Common/Render/DrawBuffer.h"
#include "Common/Render/TextureAtlas.h"
#include "Common/StringUtils.h"
#include "Common/System/OSD.h"
#include "Common/TimeUtil.h"
#include "Common/UI/Context.h"
#include "Common/UI/View.h"
#include "Common/UI/ViewGroup.h"
#include "Core/Config.h"
#include "Core/MemMap.h"
#include "Core/System.h"
#include "Core/ELF/ParamSFO.h"
#include "Core/HLE/sceCtrl.h"
#include "UI/AcPlusGestureCore.h"
#include "UI/GamepadEmu.h"
#include "UI/AcPlusGameData.h"
#include "UI/AcPlusHitboxes.h"
#include "UI/AcPlusProfiles.h"

namespace AcPlusGestures {

static const int kCharacters = 25;
static const u32 kSelectAddress[2] = { 0x08C6BB58, 0x08C6BB5A };
static const char *const kGameId = "ULUS10409";

// BlazBlue: Calamity Trigger (US, EU, JP) and Continuum Shift II (US, EU, JP, Asia): same buttons.
// The King of Fighters Collection: The Orochi Saga (US, EU); also any title naming KOF.
inline bool IsKof(const std::string &id, const std::string &title) {
	if (id == "ULUS10360" || id == "ULES01191")
		return true;
	std::string t;
	for (char c : title)
		t += (char)((c >= 'a' && c <= 'z') ? c - 32 : c);
	return t.find("KING OF FIGHTERS") != std::string::npos || t.find("KOF") != std::string::npos;
}

// Street Fighter Alpha 3 MAX (US, EU) / Street Fighter Zero 3 Double Upper (JP).
inline bool IsStreetFighter(const std::string &id, const std::string &title) {
	if (id == "ULUS10062" || id == "ULES00235" || id == "ULJM05082")
		return true;
	std::string t;
	for (char c : title)
		t += (char)((c >= 'a' && c <= 'z') ? c - 32 : c);
	return t.find("STREET FIGHTER ALPHA 3") != std::string::npos || t.find("STREET FIGHTER ZERO 3") != std::string::npos;
}

inline bool IsBlazBlue(const std::string &id) {
	static const char *const ids[] = { "ULUS10519", "ULES01497", "ULJM05613", "ULUS10579", "ULES01526", "ULJM05850", "UCAS40337" };
	for (const char *b : ids) {
		if (id == b)
			return true;
	}
	return false;
}

struct State {
	bool loaded = false;
	bool active = false;
	bool booted = false;
	bool usGame = false;
	int side = 0;
	float opacity = 0.6f;
	float scale = 1.0f;
	std::string moves[kCharacters + 1][4];
	std::string movesBlazBlue[4];   // BlazBlue: one set for all characters
	std::string movesKof[4];        // KOF: one set for all characters
	std::string movesSf[4];         // Street Fighter Alpha 3: one set for all characters
	bool blazblue = false;
	bool kof = false;
	bool sf = false;

	AcGesture::Sequencer seq;
	AcGesture::Recognizer rec{ &seq };
	std::atomic<bool> facingRight{ true };
	bool facingKnown = false;
	AcGesture::u32 lastSet = 0;   // emulator thread only

	int character = -1;           // whose special moves are loaded (0 = general set)
	int candidate = -1;
	int candidateReads = 0;
	int poll = 0;
};

inline State &Get() {
	static State state;
	return state;
}

// Emulator thread, every vblank before the controller is sampled.
inline void VblankHook() {
	State &s = Get();
	const AcGesture::u32 mask = s.seq.Tick(s.facingRight.load(std::memory_order_relaxed));
	if (mask != s.lastSet) {
		__CtrlUpdateButtons(mask, s.lastSet & ~mask);
		s.lastSet = mask;
	}
}

inline void LoadSettings() {
	State &s = Get();
	if (s.loaded)
		return;
	s.loaded = true;
	IniFile ini;
	if (!ini.Load(g_Config.internalDataDirectory / "acplus.ini"))
		return;
	if (const Section *profiles = ini.GetSection("Profiles"))
		profiles->Get("Side", &s.side);
	// The "Móvil" button look (patches/0006); read here because this runs before the touch
	// controls are created.
	if (const Section *touch = ini.GetSection("Touch"))
		touch->Get("Mobile", &g_acplusMobileTouch);
	s.side = s.side == 1 ? 1 : 0;
	const Section *sec = ini.GetSection("Gestures");
	if (!sec)
		return;
	bool enabled = false;
	sec->Get("Enabled", &enabled);
	int opacity = 60, scale = 100;
	sec->Get("Opacity", &opacity);
	sec->Get("Scale", &scale);
	s.opacity = std::clamp(opacity, 10, 100) / 100.0f;
	s.scale = std::clamp(scale, 60, 160) / 100.0f;
	auto split = [](const std::string &line, std::string *out) {
		size_t start = 0;
		for (int slot = 0; slot < 4 && start <= line.size(); slot++) {
			size_t comma = line.find(',', start);
			out[slot] = line.substr(start, comma == std::string::npos ? std::string::npos : comma - start);
			if (comma == std::string::npos)
				break;
			start = comma + 1;
		}
	};
	for (int id = 0; id <= kCharacters; id++) {
		std::string line;
		if (sec->Get(StringFromFormat("Moves%d", id).c_str(), &line))
			split(line, s.moves[id]);
	}
	std::string bbLine;
	if (sec->Get("MovesBB", &bbLine))
		split(bbLine, s.movesBlazBlue);
	std::string kofLine;
	if (sec->Get("MovesKOF", &kofLine))
		split(kofLine, s.movesKof);
	std::string sfLine;
	if (sec->Get("MovesSF", &sfLine))
		split(sfLine, s.movesSf);
	s.active = enabled;
	if (s.active)
		g_ctrlVblankHook = &VblankHook;
	INFO_LOG(Log::System, "AcPlus: gesture controls %s", s.active ? "on" : "off");
}

inline bool Active() {
	LoadSettings();
	return Get().active;
}

inline void ReleaseAll() {
	if (Active())
		Get().rec.ReleaseAll();
}

// Only the pause button stays from the normal touch controls.
inline void HidePadButtons(TouchControlConfig *c) {
	c->touchActionButtonCenter.show = false;
	c->touchDpad.show = false;
	c->touchStartKey.show = false;
	c->touchSelectKey.show = false;
	c->touchFastForwardKey.show = false;
	c->touchLKey.show = false;
	c->touchRKey.show = false;
	c->touchAnalogStick.show = false;
	c->touchRightAnalogStick.show = false;
	for (int i = 0; i < TouchControlConfig::CUSTOM_BUTTON_COUNT; i++)
		c->touchCustom[i].show = false;
	c->bShowTouchCircle = false;
	c->bShowTouchCross = false;
	c->bShowTouchTriangle = false;
	c->bShowTouchSquare = false;
}

inline void ApplyMoves(int id) {
	State &s = Get();
	s.character = id;
	if (s.blazblue || s.kof || s.sf) {
		// One set for every character (the app doesn't read who you play in these games).
		static const char *const bbDefaults[4] = { "236A", "623C", "214B", "632146C" };
		static const char *const kofDefaults[4] = { "236A", "623C", "214B", "236236A" };
		static const char *const sfDefaults[4] = { "236HP", "623HP", "214HK", "236236HP" };
		const std::string *moves = s.blazblue ? s.movesBlazBlue : s.kof ? s.movesKof : s.movesSf;
		const char *const *defaults = s.blazblue ? bbDefaults : s.kof ? kofDefaults : sfDefaults;
		const AcGesture::ButtonMap &map = s.rec.Map();
		for (int slot = 0; slot < 4; slot++)
			s.rec.SetMove(slot, AcGesture::ParseMotion(moves[slot].empty() ? defaults[slot] : moves[slot], &map));
		return;
	}
	for (int slot = 0; slot < 4; slot++) {
		std::string m = (id > 0 && !s.moves[id][slot].empty()) ? s.moves[id][slot] : s.moves[0][slot];
		s.rec.SetMove(slot, AcGesture::ParseMotion(m));
	}
}

// UI thread, once per frame while the game runs (after AcPlusHitboxes::Update()).
inline void Update() {
	if (!Active())
		return;
	State &s = Get();
	if (!s.booted) {
		s.booted = true;
		const std::string discId = g_paramSFO.GetValueString("DISC_ID");
		s.usGame = discId == kGameId;
		const std::string title = g_paramSFO.GetValueString("TITLE");
		s.blazblue = IsBlazBlue(discId);
		s.sf = !s.blazblue && IsStreetFighter(discId, title);
		s.kof = !s.blazblue && !s.sf && IsKof(discId, title);
		s.rec.SetButtonMap(s.blazblue ? AcGesture::BlazBlueMap() : s.kof ? AcGesture::KofMap() : s.sf ? AcGesture::StreetFighterMap() : AcGesture::GuiltyGearMap());
		ApplyMoves(0);
		INFO_LOG(Log::System, "AcPlus: gestures for %s (%s)", s.blazblue ? "BlazBlue" : s.kof ? "KOF" : s.sf ? "Street Fighter Alpha 3" : "Guilty Gear", discId.c_str());
	}

	// Whose special moves: the player's character, once it reads the same a few times.
	if (s.usGame && ++s.poll >= 10) {
		s.poll = 0;
		const u32 addr = kSelectAddress[s.side];
		int id = Memory::IsValidAddress(addr) ? Memory::Read_U8(addr) : 0;
		if (id >= 1 && id <= kCharacters) {
			if (id != s.candidate) {
				s.candidate = id;
				s.candidateReads = 1;
			} else if (++s.candidateReads >= 3 && id != s.character) {
				ApplyMoves(id);
				g_OSD.Show(OSDType::MESSAGE_INFO, "Especiales: " + std::string(AcGame::GG::kNames[id]), 1.5f, "acplus_gestures");
			}
		}
	}

	// Facing: from the fighters' positions when known.
	int mine = 0, other = 0;
	bool right = s.side == 0;
	s.facingKnown = AcPlusHitboxes::FighterX(s.side, &mine) && AcPlusHitboxes::FighterX(1 - s.side, &other) && mine != other;
	if (s.facingKnown)
		right = mine < other;
	if (s.rec.Flipped())
		right = !right;
	s.facingRight.store(right, std::memory_order_relaxed);
}

// Colors are 0xAABBGGRR.
inline uint32_t WithAlpha(uint32_t bgr, float a) {
	return ((uint32_t)(std::clamp(a, 0.0f, 1.0f) * 255.0f) << 24) | (bgr & 0x00FFFFFF);
}

class GestureView : public UI::View {
public:
	GestureView(const TouchControlConfig &touch, float w, float h)
		: UI::View(new UI::AnchorLayoutParams(UI::FILL_PARENT, UI::FILL_PARENT)) {
		BuildLayout(touch, w, h);
		Get().rec.ReleaseAll();
	}
	~GestureView() override {
		Get().rec.ReleaseAll();
	}

	bool CanBeFocused() const override { return false; }
	std::string DescribeText() const override { return "Gestures"; }

	bool Touch(const TouchInput &input) override {
		State &s = Get();
		if (input.flags & TouchInputFlags::RELEASE_ALL) {
			s.rec.ReleaseAll();
			return false;
		}
		if (input.flags & (TouchInputFlags::HOVER | TouchInputFlags::WHEEL))
			return false;
		AcGesture::TouchType type;
		if (input.flags & TouchInputFlags::DOWN)
			type = AcGesture::T_DOWN;
		else if (input.flags & TouchInputFlags::UP)
			type = AcGesture::T_UP;
		else if (input.flags & TouchInputFlags::CANCEL)
			type = AcGesture::T_CANCEL;
		else if (input.flags & TouchInputFlags::MOVE) {
			if ((input.flags & TouchInputFlags::MOUSE) && !input.buttons)
				return false;
			type = AcGesture::T_MOVE;
		} else {
			return false;
		}
		s.rec.Touch(input.id, input.x, input.y, type, time_now_d());
		return true;
	}

	void Update() override {
		UI::View::Update();
		Get().rec.Update(time_now_d());
	}

	void Draw(UIContext &dc) override {
		State &s = Get();
		const float a = s.opacity;
		const double now = time_now_d();

		// Shapes (untextured).
		dc.Flush();
		dc.BeginNoTex();
		DrawBuffer *d = dc.Draw();
		for (const AcGesture::Button &b : s.rec.Buttons()) {
			const uint32_t color = ButtonColor(b.action);
			d->FillCircle(b.x, b.y, b.r, 40, WithAlpha(0x00101014, (b.down ? 0.75f : 0.45f) * a));
			d->Circle(b.x, b.y, b.r - 1.5f, 3.0f, 40, 0.0f, WithAlpha(color, (b.down ? 1.0f : 0.85f) * std::min(1.0f, a + 0.25f)), 1.0f);
		}
		const AcGesture::Params &pr = s.rec.params;
		for (const AcGesture::StickView &st : s.rec.Sticks()) {
			d->FillCircle(st.ox, st.oy, pr.stickRadius, 40, WithAlpha(0x00101014, 0.30f * a));
			d->Circle(st.ox, st.oy, pr.stickRadius, 2.5f, 40, 0.0f, WithAlpha(0x00FFFFFF, 0.55f * a), 1.0f);
			float vx = st.x - st.ox, vy = st.y - st.oy;
			float len = std::sqrt(vx * vx + vy * vy);
			if (len > pr.stickRadius) {
				vx *= pr.stickRadius / len;
				vy *= pr.stickRadius / len;
			}
			d->FillCircle(st.ox + vx, st.oy + vy, 17.0f, 32, WithAlpha(st.mask ? 0x0030C0FF : 0x00FFFFFF, 0.85f * a + 0.1f));
		}
		if (s.rec.Blocking()) {
			float bx, by;
			s.rec.BlockCenter(&bx, &by);
			d->Circle(bx, by, 46.0f, 5.0f, 40, 0.0f, WithAlpha(0x00FFAA3C, 0.8f), 1.0f);
		}
		for (const AcGesture::Fx &f : s.rec.Effects()) {
			const float age = (float)std::clamp((now - f.t) / 0.45, 0.0, 1.0);
			const float fade = 1.0f - age;
			switch (f.kind) {
			case AcGesture::FX_TAP:
				d->Circle(f.x, f.y, 14.0f + 26.0f * age, 3.0f, 32, 0.0f, WithAlpha(0x00FFFFFF, 0.8f * fade), 1.0f);
				break;
			case AcGesture::FX_SWIPE:
			case AcGesture::FX_TWO:
				if (f.x2 != 0.0f || f.y2 != 0.0f)
					d->Line(dc.GetTheme().whiteImage, f.x, f.y, f.x2, f.y2, 7.0f, WithAlpha(f.kind == AcGesture::FX_TWO ? 0x00FFAA3C : 0x00FFFFFF, 0.7f * fade));
				else
					d->Circle(f.x, f.y, 24.0f + 30.0f * age, 4.0f, 32, 0.0f, WithAlpha(0x00FFAA3C, 0.8f * fade), 1.0f);
				break;
			case AcGesture::FX_BUTTON:
				break;
			}
		}
		dc.Flush();
		dc.Begin();

		// Labels.
		dc.SetFontStyle(dc.GetTheme().uiFont);
		for (const AcGesture::Button &b : s.rec.Buttons())
			DrawLabel(dc, s, b, std::min(1.0f, a + 0.35f));
		for (const AcGesture::Fx &f : s.rec.Effects()) {
			if (f.kind == AcGesture::FX_BUTTON || f.label.empty())
				continue;
			const float age = (float)std::clamp((now - f.t) / 0.45, 0.0, 1.0);
			float x = f.x, y = f.y;
			if (f.kind != AcGesture::FX_TAP && (f.x2 != 0.0f || f.y2 != 0.0f)) {
				x = f.x2;
				y = f.y2;
			}
			dc.SetFontScale(0.8f, 0.8f);
			dc.DrawTextShadow(f.label, x, y - 34.0f - 12.0f * age, WithAlpha(0x00FFFFFF, 1.0f - age), ALIGN_CENTER);
			dc.SetFontScale(1.0f, 1.0f);
		}
		if (s.rec.Blocking()) {
			float bx, by;
			s.rec.BlockCenter(&bx, &by);
			dc.SetFontScale(0.75f, 0.75f);
			dc.DrawTextShadow("Bloqueo", bx, by - 62.0f, WithAlpha(0x00FFAA3C, 1.0f), ALIGN_CENTER);
			dc.SetFontScale(1.0f, 1.0f);
		}
	}

private:
	static uint32_t ButtonColor(int action) {
		switch (action) {
		case AcGesture::ACT_SPECIAL1:
		case AcGesture::ACT_SPECIAL2:
		case AcGesture::ACT_SPECIAL3: return 0x0030C0FF;   // gold
		case AcGesture::ACT_OVERDRIVE: return 0x003C28E6;  // red
		case AcGesture::ACT_RC: return 0x00DC3CDC;         // magenta
		case AcGesture::ACT_BURST: return 0x00FFAA3C;      // blue
		default: return 0x00E0E0E0;
		}
	}

	static void DrawImageFit(UIContext &dc, const char *name, float x, float y, float size, uint32_t color) {
		ImageID id(name);
		const AtlasImage *img = dc.Draw()->GetAtlas()->getImage(id);
		if (!img)
			return;
		const float sc = size / (float)std::max(1, std::max(img->w, img->h));
		dc.Draw()->DrawImage(id, x, y, sc, color, ALIGN_CENTER);
	}

	static void DrawLabel(UIContext &dc, State &s, const AcGesture::Button &b, float alpha) {
		const uint32_t text = WithAlpha(0x00FFFFFF, alpha);
		const float k = b.r / 32.0f;
		switch (b.action) {
		case AcGesture::ACT_SPECIAL1:
		case AcGesture::ACT_SPECIAL2:
		case AcGesture::ACT_SPECIAL3:
		case AcGesture::ACT_OVERDRIVE: {
			const AcGesture::Motion &m = s.rec.Move(b.action - AcGesture::ACT_SPECIAL1);
			std::string label = m.valid ? m.text : "-";
			float fs = (label.size() > 6 ? 0.52f : label.size() > 4 ? 0.62f : 0.75f) * k;
			if (b.action == AcGesture::ACT_OVERDRIVE) {
				dc.SetFontScale(0.6f * k, 0.6f * k);
				dc.DrawTextShadow("OD", b.x, b.y - b.r * 0.42f, WithAlpha(0x003C28E6 | 0x00808080, alpha), ALIGN_CENTER);
				dc.SetFontScale(fs * 0.85f, fs * 0.85f);
				dc.DrawTextShadow(label, b.x, b.y + b.r * 0.18f, text, ALIGN_CENTER);
			} else {
				dc.SetFontScale(fs, fs);
				dc.DrawTextShadow(label, b.x, b.y, text, ALIGN_CENTER);
			}
			dc.SetFontScale(1.0f, 1.0f);
			break;
		}
		case AcGesture::ACT_RC:
		case AcGesture::ACT_BURST:
			dc.SetFontScale(0.55f * k * 1.4f, 0.55f * k * 1.4f);
			dc.DrawTextShadow(b.action == AcGesture::ACT_RC ? s.rec.Map().rapidCancelName : s.rec.Map().burstName, b.x, b.y, text, ALIGN_CENTER);
			dc.SetFontScale(1.0f, 1.0f);
			break;
		case AcGesture::ACT_START:
			DrawImageFit(dc, "I_START", b.x, b.y, b.r * 1.15f, text);
			break;
		case AcGesture::ACT_CONFIRM:
			DrawImageFit(dc, "I_CROSS", b.x, b.y, b.r * 1.05f, text);
			break;
		case AcGesture::ACT_BACK:
			DrawImageFit(dc, "I_CIRCLE", b.x, b.y, b.r * 1.05f, text);
			break;
		case AcGesture::ACT_FLIP: {
			const bool right = s.facingRight.load(std::memory_order_relaxed);
			DrawImageFit(dc, right ? "I_ARROW_RIGHT" : "I_ARROW_LEFT", b.x, b.y, b.r * 1.1f,
				s.facingKnown ? text : WithAlpha(0x0050D8FF, alpha));
			break;
		}
		default:
			break;
		}
	}

	void BuildLayout(const TouchControlConfig &touch, float w, float h) {
		State &s = Get();
		const float u = std::min(w, h);
		const float r = u * 0.078f * s.scale;          // special buttons
		const float rs = r * 0.66f;                     // small buttons
		const float col = w - r * 1.45f;
		std::vector<AcGesture::Button> buttons;
		auto add = [&](int action, float x, float y, float radius) {
			AcGesture::Button b;
			b.action = action;
			b.x = x;
			b.y = y;
			b.r = radius;
			buttons.push_back(b);
		};
		add(AcGesture::ACT_SPECIAL1, col, h * 0.84f, r);
		add(AcGesture::ACT_SPECIAL2, col, h * 0.65f, r);
		add(AcGesture::ACT_SPECIAL3, col, h * 0.46f, r);
		add(AcGesture::ACT_OVERDRIVE, col, h * 0.27f, r);
		const float topY = rs * 1.35f;
		add(AcGesture::ACT_BURST, w - rs * 1.5f, topY, rs);
		add(AcGesture::ACT_RC, w - rs * 1.5f - rs * 2.5f, topY, rs);
		add(AcGesture::ACT_START, rs * 1.5f, topY, rs);
		add(AcGesture::ACT_CONFIRM, rs * 1.5f + rs * 2.4f, topY, rs);
		add(AcGesture::ACT_BACK, rs * 1.5f + rs * 4.8f, topY, rs);
		add(AcGesture::ACT_FLIP, rs * 1.5f + rs * 7.2f, topY, rs);

		std::vector<AcGesture::Zone> zones;
		if (g_Config.bShowTouchControls && touch.touchPauseKey.show && touch.touchPauseKey.x >= 0.0f) {
			AcGesture::Zone z;
			z.x = touch.touchPauseKey.x * w;
			z.y = touch.touchPauseKey.y * h;
			z.r = 40.0f * std::max(0.5f, touch.touchPauseKey.scale);
			zones.push_back(z);
		}
		s.rec.SetLayout(buttons, zones);
		const float k = u / 400.0f;
		s.rec.params.swipeDist = 30.0f * std::max(0.8f, k);
		s.rec.params.stickRadius = 52.0f * std::max(0.8f, k);
		s.rec.params.stickDead = 14.0f * std::max(0.8f, k);
		s.rec.params.crouchDrag = 20.0f * std::max(0.8f, k);
	}
};

}  // namespace AcPlusGestures
