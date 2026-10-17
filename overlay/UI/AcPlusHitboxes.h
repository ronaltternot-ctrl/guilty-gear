// GGXX AC+ launcher build: hitbox / hurtbox viewer drawn over the game.
//
// Turned on from the launcher (acplus.ini, section [Hitboxes]). Detection lives in
// AcPlusHitboxCore.h; this file feeds it PSP memory, draws the result and writes a small
// diagnostics report to <memstick>/PSP/SYSTEM/acplus_hitbox_report.txt.
//
// Included only from EmuScreen.cpp.

#pragma once

#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <string>
#include <vector>

#include "Common/Data/Format/IniFile.h"
#include "Common/File/FileUtil.h"
#include "Common/File/Path.h"
#include "Common/Log.h"
#include "Common/Render/DrawBuffer.h"
#include "Common/System/Display.h"
#include "Common/TimeUtil.h"
#include "Common/UI/Context.h"
#include "Common/UI/View.h"
#include "Core/Config.h"
#include "Core/MemMap.h"
#include "Core/System.h"
#include "Core/ELF/ParamSFO.h"
#include "Core/Util/PathUtil.h"
#include "GPU/Common/PresentationCommon.h"
#include "UI/AcPlusHitboxCore.h"

namespace AcPlusHitboxes {

// Select-screen character bytes (US version), used to recognise the fighters in memory.
static const u32 kSelectP1 = 0x08C6BB58;
static const u32 kSelectP2 = 0x08C6BB5A;
static const char *const kGameId = "ULUS10409";

struct PspMem : AcHitbox::Mem {
	bool Valid(u32 addr, u32 size) const override { return Memory::IsValidRange(addr, size); }
	const u8 *Ptr(u32 addr) const override { return Memory::GetPointerUnchecked(addr); }
};

struct State {
	bool initialized = false;
	bool active = false;     // draw the boxes
	bool detect = false;     // find the fighters (also for the gesture controls' facing)
	bool showHurt = true, showHit = true, showPivot = true, showProjectiles = true;
	AcHitbox::Detector detector;
	std::vector<AcHitbox::Box> boxes;
	std::vector<AcHitbox::Pivot> pivots;
	AcHitbox::Camera camera;
	int lastPhase = -1;
	bool lastCamera = false;
	double lastReport = 0.0;
	Path reportPath;
};

inline State &Get() {
	static State state;
	return state;
}

inline u32 ParseU32(const Section *s, const char *key) {
	std::string v;
	if (!s || !s->Get(key, &v) || v.empty())
		return 0;
	return (u32)strtoul(v.c_str(), nullptr, 0);
}

inline void Init() {
	State &s = Get();
	if (s.initialized)
		return;
	s.initialized = true;

	IniFile ini;
	if (!ini.Load(g_Config.internalDataDirectory / "acplus.ini"))
		return;
	bool gestures = false;
	if (const Section *g = ini.GetSection("Gestures"))
		g->Get("Enabled", &gestures);
	const Section *sec = ini.GetSection("Hitboxes");
	bool enabled = false;
	if (!sec) {
		const std::string gameId = g_paramSFO.GetValueString("DISC_ID");
		s.detect = gestures && gameId == kGameId;
		return;
	}
	sec->Get("Enabled", &enabled);
	sec->Get("Hurtboxes", &s.showHurt);
	sec->Get("Hitboxes", &s.showHit);
	sec->Get("Pivots", &s.showPivot);
	sec->Get("Projectiles", &s.showProjectiles);

	// Optional manual values, to fix a wrong detection without a new app version.
	AcHitbox::Settings set;
	set.p1 = ParseU32(sec, "P1");
	set.stride = ParseU32(sec, "Stride");
	std::string v;
	if (sec->Get("BoxPtr", &v) && !v.empty()) set.boxPtr = (int)strtol(v.c_str(), nullptr, 0);
	if (sec->Get("BoxCount", &v) && !v.empty()) set.boxCount = (int)strtol(v.c_str(), nullptr, 0);
	if (sec->Get("Pivot", &v) && !v.empty()) set.pivot = (int)strtol(v.c_str(), nullptr, 0);
	set.camCenter = ParseU32(sec, "CameraCenter");
	float f = 0.0f;
	if (sec->Get("Scale", &f)) set.scale = f;
	f = 0.0f;
	if (sec->Get("GroundY", &f)) set.groundY = f;
	s.detector.Configure(set);

	const std::string gameId = g_paramSFO.GetValueString("DISC_ID");
	s.active = enabled && gameId == kGameId;
	s.detect = (enabled || gestures) && gameId == kGameId;
	s.reportPath = GetSysDirectory(DIRECTORY_SYSTEM) / "acplus_hitbox_report.txt";
	INFO_LOG(Log::System, "AcPlus: hitbox viewer %s, detection %s (game %s)", s.active ? "on" : "off", s.detect ? "on" : "off", gameId.c_str());
}

inline void WriteReport(const PspMem &m) {
	State &s = Get();
	std::string text = "GGXX AC+ hitbox viewer report\n";
	const std::string status = s.detector.Status();
	// Empty status = detection finished; the launcher shows this line.
	text += "status: " + (status.empty() ? std::string(s.camera.fromMemory ? "funcionando" : "funcionando (camara aproximada)") : status) + "\n";
	char line[160];
	snprintf(line, sizeof(line), "select bytes: %d %d\n", Memory::IsValidAddress(kSelectP1) ? Memory::Read_U8(kSelectP1) : -1,
		Memory::IsValidAddress(kSelectP2) ? Memory::Read_U8(kSelectP2) : -1);
	text += line;
	snprintf(line, sizeof(line), "camera: memory=%d center=%.0f left=%.0f bottom=%.0f width=%.0f height=%.0f scale=%.6f\n",
		(int)s.camera.fromMemory, s.camera.center, s.camera.left, s.camera.bottom, s.camera.width, s.camera.height, s.camera.scale);
	text += line;
	for (const AcHitbox::Pivot &p : s.pivots) {
		float sx, sy;
		AcHitbox::Detector::Project(s.camera, (float)p.x, (float)p.y, &sx, &sy);
		snprintf(line, sizeof(line), "pivot obj %d: world %d,%d -> screen %.1f,%.1f\n", p.owner, p.x, p.y, sx, sy);
		text += line;
	}
	text += s.detector.Report(m);
	File::WriteStringToFile(true, text, s.reportPath);
}

// Call once per frame while the game runs (UI thread).
inline void Update() {
	Init();
	State &s = Get();
	if (!s.detect)
		return;
	PspMem m;
	int c1 = Memory::IsValidAddress(kSelectP1) ? Memory::Read_U8(kSelectP1) : 0;
	int c2 = Memory::IsValidAddress(kSelectP2) ? Memory::Read_U8(kSelectP2) : 0;
	if (c1 >= 1 && c1 <= 25 && c2 >= 1 && c2 <= 25)
		s.detector.SetAnchor(c1, c2);
	s.detector.Step(m);
	s.detector.Collect(m, s.showProjectiles, &s.boxes, &s.pivots);
	if (s.detector.GetPhase() == AcHitbox::Detector::RUNNING)
		s.camera = s.detector.GetCamera(m);

	// Report when something changes, and every 10 seconds.
	const int phase = (int)s.detector.GetPhase();
	const bool cam = s.detector.CameraAddress() != 0;
	const double now = time_now_d();
	if (phase != s.lastPhase || cam != s.lastCamera || now - s.lastReport > 10.0) {
		s.lastPhase = phase;
		s.lastCamera = cam;
		s.lastReport = now;
		WriteReport(m);
	}
}

// World x of a fighter (0 = player 1, 1 = player 2) once the detector found them.
inline bool FighterX(int player, int *x) {
	State &s = Get();
	if (!s.detect)
		return false;
	for (const AcHitbox::Pivot &p : s.pivots) {
		if (p.owner == player) {
			*x = p.x;
			return true;
		}
	}
	return false;
}

// Draws over the game picture. Call from EmuScreen::renderUI() before the touch controls.
inline void Draw(UIContext *ctx, const DisplayLayoutConfig &config) {
	State &s = Get();
	if (!s.active)
		return;

	FRect frame = GetScreenFrame(config.bIgnoreScreenInsets, g_display.pixel_xres, g_display.pixel_yres);
	FRect rc;
	CalculateDisplayOutputRect(config, &rc, 480.0f, 272.0f, frame, config.iInternalScreenRotation);
	const float ox = rc.x * g_display.dpi_scale_x;
	const float oy = rc.y * g_display.dpi_scale_y;
	const float kx = rc.w * g_display.dpi_scale_x / 480.0f;
	const float ky = rc.h * g_display.dpi_scale_y / 272.0f;
	DrawBuffer *draw = ctx->Draw();
	// Plain colored shapes need the untextured mode (in the normal one, Rect samples the UI atlas).
	ctx->Flush();
	ctx->BeginNoTex();

	// Colors are 0xAABBGGRR.
	for (const AcHitbox::Box &b : s.boxes) {
		uint32_t line;
		if (b.type == AcHitbox::BOX_ATTACK) {
			if (!s.showHit) continue;
			line = 0x003030FF;   // red
		} else if (b.type == AcHitbox::BOX_HURT) {
			if (!s.showHurt) continue;
			line = b.owner >= 2 ? 0x0030C0FF : 0x0030FF30;   // green (fighters), orange (projectiles)
		} else if (b.type == AcHitbox::BOX_HURT_ANY) {
			if (!s.showHurt) continue;
			line = 0x00FFD030;   // light blue
		} else {
			continue;
		}
		float x1, y1, x2, y2;
		AcHitbox::Detector::Project(s.camera, b.x1, b.y1, &x1, &y1);
		AcHitbox::Detector::Project(s.camera, b.x2, b.y2, &x2, &y2);
		float left = ox + std::min(x1, x2) * kx;
		float top = oy + std::min(y1, y2) * ky;
		float w = std::fabs(x2 - x1) * kx;
		float h = std::fabs(y2 - y1) * ky;
		draw->Rect(left, top, w, h, line | 0x38000000);
		draw->RectOutline(left, top, w, h, line | 0xF0000000);
	}
	if (s.showPivot) {
		for (const AcHitbox::Pivot &p : s.pivots) {
			float px, py;
			AcHitbox::Detector::Project(s.camera, (float)p.x, (float)p.y, &px, &py);
			float x = ox + px * kx, y = oy + py * ky;
			const float r = 6.0f;
			draw->Rect(x - r, y - 1.0f, r * 2, 2.0f, 0xFFFFFFFF);
			draw->Rect(x - 1.0f, y - r, 2.0f, r * 2, 0xFFFFFFFF);
		}
	}

	ctx->Flush();
	ctx->Begin();

	std::string status = s.detector.Status();
	if (!status.empty()) {
		ctx->SetFontStyle(ctx->GetTheme().uiFontSmall);
		ctx->DrawTextShadow(status, ox + 8.0f, oy + rc.h * g_display.dpi_scale_y - 28.0f, 0xFFFFFFFF, ALIGN_LEFT);
	}
}

}  // namespace AcPlusHitboxes
