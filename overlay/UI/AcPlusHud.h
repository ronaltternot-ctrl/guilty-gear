// GGXX AC+ launcher build: custom HUD (health, power meter, burst, timer, combo counter) drawn
// over the game from values read in its memory. The values come from AcPlusGameData.h (Guilty
// Gear XX ΛC+ US, BlazBlue CT US/EU and CS II US, KOF Orochi Saga US); tracking and the designs
// are in AcPlusHudCore.h. This file reads PSP memory and draws the primitives with PPSSPP's UI
// renderer.
//
// Turned on from the launcher: acplus.ini, section [Hud] (Enabled, Design 1-4, Opacity).
//
// Included only from EmuScreen.cpp.

#pragma once

#include <algorithm>
#include <string>
#include <vector>

#include "Common/Data/Format/IniFile.h"
#include "Common/Log.h"
#include "Common/Render/DrawBuffer.h"
#include "Common/Render/Text/Font.h"
#include "Common/System/Display.h"
#include "Common/TimeUtil.h"
#include "Common/UI/Context.h"
#include "Core/Config.h"
#include "Core/MemMap.h"
#include "Core/ELF/ParamSFO.h"
#include "GPU/Common/PresentationCommon.h"
#include "UI/AcPlusHudCore.h"
#include "UI/AcPlusGameData.h"

namespace AcPlusHud {

struct PspMem : AcHud::Mem {
	bool Valid(u32 addr, u32 size) const override { return Memory::IsValidRange(addr, size); }
	const u8 *Ptr(u32 addr) const override { return Memory::GetPointerUnchecked(addr); }
};

struct State {
	bool initialized = false;
	bool active = false;
	int design = AcHud::DESIGN_NEON;
	float opacity = 1.0f;
	AcGame::Reader reader;
	AcHud::Tracker tracker;
	std::vector<AcHud::Prim> prims;
	float fontUnit = 0.0f;   // height of the HUD font at scale 1
};

inline State &Get() {
	static State state;
	return state;
}

inline void Init() {
	State &s = Get();
	if (s.initialized)
		return;
	s.initialized = true;
	IniFile ini;
	if (!ini.Load(g_Config.internalDataDirectory / "acplus.ini"))
		return;
	const Section *sec = ini.GetSection("Hud");
	if (!sec)
		return;
	bool enabled = false;
	int opacity = 100;
	sec->Get("Enabled", &enabled);
	sec->Get("Design", &s.design);
	sec->Get("Opacity", &opacity);
	s.opacity = std::clamp(opacity, 20, 100) / 100.0f;
	const std::string gameId = g_paramSFO.GetValueString("DISC_ID");
	s.reader.Init(gameId);
	s.active = enabled && s.design >= AcHud::DESIGN_NEON && s.design <= AcHud::DESIGN_DATA && s.reader.Supported();
	INFO_LOG(Log::System, "AcPlus: custom HUD %s (design %d, game %s, %s)", s.active ? "on" : "off", s.design, gameId.c_str(), s.reader.Label());
}

// Once per frame while the game runs.
inline void Update() {
	Init();
	State &s = Get();
	if (!s.active)
		return;
	PspMem m;
	const double t = time_now_d();
	AcHud::Snapshot snap;
	s.reader.Read(m, t, &snap);
	s.tracker.Update(snap, t);
}

// 0xAARRGGBB -> PPSSPP's 0xAABBGGRR.
inline uint32_t ToAbgr(uint32_t c) {
	return (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
}

inline void Draw(UIContext *ctx, const DisplayLayoutConfig &config) {
	State &s = Get();
	if (!s.active || s.tracker.visible <= 0.0f)
		return;

	FRect frame = GetScreenFrame(config.bIgnoreScreenInsets, g_display.pixel_xres, g_display.pixel_yres);
	FRect rc;
	CalculateDisplayOutputRect(config, &rc, 480.0f, 272.0f, frame, config.iInternalScreenRotation);
	AcHud::Frame f;
	f.gx = rc.x * g_display.dpi_scale_x;
	f.gy = rc.y * g_display.dpi_scale_y;
	f.gw = rc.w * g_display.dpi_scale_x;
	f.gh = rc.h * g_display.dpi_scale_y;
	f.sw = g_display.dp_xres;
	f.sh = g_display.dp_yres;
	AcHud::Build(s.design, s.tracker, f, s.opacity, &s.prims);
	if (s.prims.empty())
		return;

	static const FontStyle hudFont(FontFamily::SansSerif, 24, FontStyleFlags::Bold);
	if (s.fontUnit <= 0.0f) {
		float w = 0.0f, h = 0.0f;
		ctx->MeasureText(hudFont, 1.0f, 1.0f, "0", &w, &h);
		s.fontUnit = h > 0.0f ? h : 24.0f;
	}

	// Shapes are untextured; text needs the normal (textured) mode. Switch only when needed.
	DrawBuffer *d = ctx->Draw();
	bool noTex = false;
	auto shapes = [&]() {
		if (!noTex) {
			ctx->Flush();
			ctx->BeginNoTex();
			noTex = true;
		}
	};
	for (const AcHud::Prim &p : s.prims) {
		switch (p.kind) {
		case AcHud::PRIM_RECT:
			shapes();
			d->Rect(p.x, p.y, p.w, p.h, ToAbgr(p.c1));
			break;
		case AcHud::PRIM_VGRAD:
			shapes();
			d->RectVGradient(p.x, p.y, p.x + p.w, p.y + p.h, ToAbgr(p.c1), ToAbgr(p.c2));
			break;
		case AcHud::PRIM_QUAD: {
			shapes();
			const uint32_t top = ToAbgr(p.c1), bottom = ToAbgr(p.c2);
			d->V(p.qx[0], p.qy[0], 0.0f, top, 0.0f, 0.0f);
			d->V(p.qx[1], p.qy[1], 0.0f, top, 1.0f, 0.0f);
			d->V(p.qx[2], p.qy[2], 0.0f, bottom, 1.0f, 1.0f);
			d->V(p.qx[0], p.qy[0], 0.0f, top, 0.0f, 0.0f);
			d->V(p.qx[2], p.qy[2], 0.0f, bottom, 1.0f, 1.0f);
			d->V(p.qx[3], p.qy[3], 0.0f, bottom, 0.0f, 1.0f);
			break;
		}
		case AcHud::PRIM_TEXT: {
			if (noTex) {
				ctx->Flush();
				ctx->Begin();
				noTex = false;
			}
			const float scale = p.size / s.fontUnit;
			ctx->SetFontStyle(hudFont);
			ctx->SetFontScale(scale, scale);
			int align = ALIGN_VCENTER | (p.align < 0 ? ALIGN_LEFT : p.align > 0 ? ALIGN_RIGHT : ALIGN_HCENTER);
			if (p.shadow)
				ctx->DrawTextShadow(p.text, p.x, p.y, ToAbgr(p.c1), align);
			else
				ctx->DrawText(p.text, p.x, p.y, ToAbgr(p.c1), align);
			ctx->SetFontScale(1.0f, 1.0f);
			break;
		}
		}
	}
	if (noTex) {
		ctx->Flush();
		ctx->Begin();
	}
	ctx->SetFontStyle(ctx->GetTheme().uiFont);
}

}  // namespace AcPlusHud
