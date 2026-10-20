// GGXX AC+ launcher build: AMD FidelityFX Super Resolution 1.0 (beta), saved per game.
//
// The launcher keeps the FSR settings for each game (by disc ID) and writes the ones of the
// selected game to acplus.ini, section [Fsr], and to the appended config (post-shader chain
// [AcXbrSprites] + AcFsrEasu + AcFsrRcas + [AcFsrFinish], see assets/shaders/acplus.ini):
//   - Sprites: the game is drawn at 480x272 and xBR doubles it before FSR (2D games).
//   - Detail: AMD CAS after RCAS; Color: vibrance and a soft contrast curve (same pass). A PPSSPP game-specific config, if the
// player ever made one, is loaded when the game boots and would replace that chain: so once the
// game runs, this puts the launcher's FSR settings back (only for the game they were saved for)
// and shows what is active.
//
// FSR only processes the final picture: save states don't contain it, so states work the same
// with FSR on or off, and with any factor.
//
// Included only from EmuScreen.cpp.

#pragma once

#include <cmath>
#include <string>
#include <vector>

#include "Common/Data/Format/IniFile.h"
#include "Common/Log.h"
#include "Common/StringUtils.h"
#include "Common/System/OSD.h"
#include "Common/System/System.h"
#include "Core/Config.h"
#include "Core/ELF/ParamSFO.h"
#include "GPU/GPU.h"
#include "GPU/GPUCommon.h"

namespace AcPlusFsr {

static const char *const kXbr = "AcXbrSprites";
static const char *const kEasu = "AcFsrEasu";
static const char *const kRcas = "AcFsrRcas";
static const char *const kFinish = "AcFsrFinish";

// Call every frame once the game runs; does its work the first time.
inline void ApplyOnce() {
	static bool done = false;
	if (done)
		return;
	done = true;

	IniFile ini;
	if (!ini.Load(g_Config.internalDataDirectory / "acplus.ini"))
		return;
	const Section *sec = ini.GetSection("Fsr");
	if (!sec)
		return;
	bool enabled = false;
	sec->Get("Enabled", &enabled);
	if (!enabled)
		return;
	std::string gameId;
	sec->Get("GameId", &gameId);
	const std::string booted = g_paramSFO.GetValueString("DISC_ID");
	if (!gameId.empty() && gameId != booted) {
		INFO_LOG(Log::System, "AcPlus: FSR settings are for %s, not %s", gameId.c_str(), booted.c_str());
		return;
	}
	float scale = 2.0f, sharpness = 0.85f, detail = 0.0f, color = 0.0f;
	bool sprites = false;
	sec->Get("Scale", &scale);
	sec->Get("Sharpness", &sharpness);
	sec->Get("Detail", &detail);
	sec->Get("Color", &color);
	sec->Get("Sprites", &sprites);
	scale = std::min(std::max(scale, 1.0f), 4.0f);
	sharpness = std::min(std::max(sharpness, 0.0f), 1.0f);
	detail = std::min(std::max(detail, 0.0f), 1.0f);
	color = std::min(std::max(color, 0.0f), 1.0f);
	const bool finish = detail > 0.001f || color > 0.001f;

	bool changed = false;
	std::vector<std::string> chain;
	if (sprites)
		chain.push_back(kXbr);
	chain.push_back(kEasu);
	chain.push_back(kRcas);
	if (finish)
		chain.push_back(kFinish);
	if (g_Config.vPostShaderNames != chain) {
		g_Config.vPostShaderNames = chain;
		changed = true;
	}
	auto set = [&](const std::string &key, float value) {
		auto it = g_Config.mPostShaderSetting.find(key);
		if (it == g_Config.mPostShaderSetting.end() || std::fabs(it->second - value) > 0.0001f) {
			g_Config.mPostShaderSetting[key] = value;
			changed = true;
		}
	};
	set(std::string(kEasu) + "SettingCurrentValue1", scale);
	set(std::string(kRcas) + "SettingCurrentValue1", sharpness);
	if (sprites)
		set(std::string(kXbr) + "SettingCurrentValue1", 2.0f);
	if (finish) {
		set(std::string(kFinish) + "SettingCurrentValue1", detail);
		set(std::string(kFinish) + "SettingCurrentValue2", color);
	}
	if (changed) {
		INFO_LOG(Log::System, "AcPlus: FSR settings re-applied after the game-specific config");
		System_PostUIMessage(UIMessage::GPU_CONFIG_CHANGED);
		System_PostUIMessage(UIMessage::GPU_RENDER_RESIZED);
		System_PostUIMessage(UIMessage::POSTSHADER_UPDATED);
		if (gpu)
			gpu->NotifyConfigChanged();
	}
	g_OSD.Show(OSDType::MESSAGE_INFO,
		StringFromFormat("AMD FSR 1.0 (beta): %sx%.1f, nitidez %d%%, detalle %d%%, color %d%%", sprites ? "sprites xBR + " : "", scale,
			(int)(sharpness * 100.0f + 0.5f), (int)(detail * 100.0f + 0.5f), (int)(color * 100.0f + 0.5f)), 3.0f, "acplus_fsr");
}

}  // namespace AcPlusFsr
