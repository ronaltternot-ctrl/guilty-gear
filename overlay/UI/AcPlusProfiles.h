// GGXX AC+ launcher build: per-character touch control profiles.
//
// While a match runs, this watches which character the player is using (read from the game's
// memory through AcPlusGameData.h: Guilty Gear XX ΛC+ US, BlazBlue CT US/EU and CS II US,
// KOF '96 / '97 / '98 of the Orochi Saga US) and swaps the on-screen button layout:
//   - Each character remembers its own layout. If you move or resize buttons in the in-game
//     layout editor while playing a character, that layout is saved for that character.
//   - A character with no saved layout gets the button type chosen for it in the launcher
//     (PSP or Arcade), or the general one.
//
// Settings come from acplus.ini, written by the launcher into the app's internal storage.
// Saved layouts live in <memstick>/PSP/SYSTEM/acplus_profiles/: char_NN.ini (Guilty Gear, by
// character number), bb_xx.ini (BlazBlue, by sprite code), kof_name.ini (KOF).
// For BlazBlue and KOF a report of what was read goes to PSP/SYSTEM/acplus_game_report.txt.
//
// Included only from EmuScreen.cpp.

#pragma once

#include <map>
#include <string>

#include "Common/Data/Format/IniFile.h"
#include "Common/Data/Text/Parsers.h"
#include "Common/File/FileUtil.h"
#include "Common/File/Path.h"
#include "Common/Log.h"
#include "Common/StringUtils.h"
#include "Common/System/OSD.h"
#include "Common/TimeUtil.h"
#include "Core/Config.h"
#include "Core/MemMap.h"
#include "Core/System.h"
#include "Core/ELF/ParamSFO.h"
#include "Core/Util/PathUtil.h"
#include "UI/AcPlusGameData.h"

namespace AcPlusProfiles {

// How many consecutive identical reads before switching (checked every kPollFrames frames).
static const int kPollFrames = 10;
static const int kStableReads = 4;

struct PspMem : AcHud::Mem {
	bool Valid(u32 addr, u32 size) const override { return Memory::IsValidRange(addr, size); }
	const u8 *Ptr(u32 addr) const override { return Memory::GetPointerUnchecked(addr); }
};

struct State {
	bool initialized = false;
	bool active = false;            // enabled and running a game whose fighters can be read
	int side = 0;                    // 0 = 1P, 1 = 2P
	std::string defaultStyle = "psp";
	std::map<std::string, std::string> styles;  // character key -> "psp" / "arcade"
	Path launcherDir;                // where acplus.ini and the layout presets live
	Path profileDir;                 // saved per-character layouts
	Path reportPath;                 // BlazBlue / KOF diagnostics
	AcGame::Reader reader;
	std::string current;             // character whose layout is applied ("" none)
	std::string appliedSnapshot;     // layout as applied, to detect edits
	std::string candidate;
	int candidateReads = 0;
	int frame = 0;
	double lastReport = -100.0;
	bool needsRecreate = false;      // layout changed at startup, views must be rebuilt
};

inline State &Get() {
	static State state;
	return state;
}

inline std::string Snapshot() {
	IniFile ini;
	g_Config.SaveTouchLayout(ini);
	std::string out;
	for (const auto &section : ini.Sections()) {
		out += "[" + section->name() + "]\n";
		for (const auto &[key, value] : section->ToMap())
			out += key + "=" + value + "\n";
	}
	return out;
}

// "12" -> char_12.ini (Guilty Gear, as in earlier versions); "bb_rg" -> bb_rg.ini.
inline Path ProfilePath(const std::string &key) {
	int id = 0;
	if (TryParse(key, &id))
		return Get().profileDir / StringFromFormat("char_%02d.ini", id);
	return Get().profileDir / (key + ".ini");
}

inline void Init() {
	State &s = Get();
	if (s.initialized)
		return;
	s.initialized = true;

	s.launcherDir = g_Config.internalDataDirectory;
	IniFile ini;
	if (!ini.Load(s.launcherDir / "acplus.ini")) {
		INFO_LOG(Log::System, "AcPlus: no acplus.ini, character profiles off");
		return;
	}
	const Section *general = ini.GetSection("Profiles");
	bool enabled = false;
	if (general) {
		general->Get("Enabled", &enabled);
		general->Get("Side", &s.side);
		general->Get("DefaultStyle", &s.defaultStyle);
	}
	if (const Section *chars = ini.GetSection("Characters")) {
		for (const auto &[key, value] : chars->ToMap())
			s.styles[key] = value;
	}
	const std::string gameId = g_paramSFO.GetValueString("DISC_ID");
	s.reader.Init(gameId);
	s.active = enabled && s.reader.Supported();
	s.side = s.side == 1 ? 1 : 0;
	if (s.defaultStyle != "psp" && s.defaultStyle != "arcade")
		s.defaultStyle = "psp";
	s.profileDir = GetSysDirectory(DIRECTORY_SYSTEM) / "acplus_profiles";
	s.reportPath = GetSysDirectory(DIRECTORY_SYSTEM) / "acplus_game_report.txt";
	INFO_LOG(Log::System, "AcPlus: character profiles %s (game %s, %s, side %d)", s.active ? "on" : "off", gameId.c_str(), s.reader.Label(), s.side + 1);
	if (!s.active)
		return;
	File::CreateFullPath(s.profileDir);
	// Until a character is detected, use the general button type (the layout left over from
	// the last character played may still be in the saved settings).
	IniFile preset;
	if (preset.Load(s.launcherDir / ("acplus_layout_" + s.defaultStyle + ".ini"))) {
		g_Config.LoadTouchLayout(preset, true);
		s.needsRecreate = true;
	}
}

// Saves the current character's layout if it was changed since it was applied.
inline void SaveIfModified() {
	State &s = Get();
	if (!s.active || s.current.empty())
		return;
	std::string now = Snapshot();
	if (now == s.appliedSnapshot)
		return;
	IniFile ini;
	g_Config.SaveTouchLayout(ini);
	if (ini.Save(ProfilePath(s.current))) {
		s.appliedSnapshot = now;
		INFO_LOG(Log::System, "AcPlus: saved layout for character %s", s.current.c_str());
	}
}

inline void Apply(const std::string &key) {
	State &s = Get();
	IniFile ini;
	bool fromProfile = ini.Load(ProfilePath(key));
	if (!fromProfile) {
		auto it = s.styles.find(key);
		std::string style = it != s.styles.end() ? it->second : s.defaultStyle;
		if (style != "psp" && style != "arcade")
			style = s.defaultStyle;
		if (!ini.Load(s.launcherDir / ("acplus_layout_" + style + ".ini"))) {
			WARN_LOG(Log::System, "AcPlus: missing layout preset '%s'", style.c_str());
			return;
		}
	}
	g_Config.LoadTouchLayout(ini, true);
	s.current = key;
	s.appliedSnapshot = Snapshot();
	INFO_LOG(Log::System, "AcPlus: character %s, layout from %s", key.c_str(), fromProfile ? "saved profile" : "preset");
}

inline void WriteReport(const PspMem &m, double t, const std::string &fighter) {
	State &s = Get();
	if (s.reader.GetTitle() != AcGame::TITLE_BB && s.reader.GetTitle() != AcGame::TITLE_KOF)
		return;
	s.lastReport = t;
	std::string text = "GGXX AC+ game report\n";
	text += "your side: " + std::string(s.side == 0 ? "1P" : "2P") + ", detected: " + (fighter.empty() ? std::string("(nobody yet)") : fighter) + "\n";
	text += s.reader.Report(m, t);
	File::WriteStringToFile(true, text, s.reportPath);
}

// Call once per frame while the game runs. Returns true when the touch layout changed and
// the screen's views need to be recreated.
inline bool Update() {
	Init();
	State &s = Get();
	if (!s.active)
		return false;
	if (s.needsRecreate) {
		s.needsRecreate = false;
		return true;
	}
	if (++s.frame < kPollFrames)
		return false;
	s.frame = 0;

	PspMem m;
	const double t = time_now_d();
	AcGame::Fighter f;
	const bool known = s.reader.PlayerFighter(m, t, s.side, &f) && !f.key.empty();
	if (t - s.lastReport > 10.0)
		WriteReport(m, t, known ? f.name : "");
	if (!known)
		return false;   // Not chosen yet (or not in a fight): leave the layout alone.

	if (f.key != s.candidate) {
		s.candidate = f.key;
		s.candidateReads = 1;
		return false;
	}
	if (++s.candidateReads < kStableReads || f.key == s.current)
		return false;

	SaveIfModified();
	Apply(f.key);
	WriteReport(m, t, f.name);
	g_OSD.Show(OSDType::MESSAGE_INFO, "Controles: " + f.name, 1.5f, "acplus_profile");
	return true;
}

}  // namespace AcPlusProfiles
