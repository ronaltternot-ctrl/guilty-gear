// GGXX AC+ launcher build: per-character touch control profiles.
//
// While a match runs, this watches which character the player is using (read from the
// game's memory, US version ULUS-10409 only) and swaps the on-screen button layout:
//   - Each character remembers its own layout. If you move or resize buttons in the in-game
//     layout editor while playing a character, that layout is saved for that character.
//   - A character with no saved layout gets the button type chosen for it in the launcher
//     (PSP or Arcade), or the general one.
//
// Settings come from acplus.ini, written by the launcher into the app's internal storage.
// Saved layouts live in <memstick>/PSP/SYSTEM/acplus_profiles/char_NN.ini.
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
#include "Core/Config.h"
#include "Core/MemMap.h"
#include "Core/System.h"
#include "Core/ELF/ParamSFO.h"
#include "Core/Util/PathUtil.h"

namespace AcPlusProfiles {

// Character byte of the "single" fighter per player (from the CWCheat database, ULUS-10409:
// "1P Char. Single" 0x0046BB58 and "2P Char. Single" 0x0046BB5A, base 0x08800000).
static const char *const kGameId = "ULUS10409";
static const u32 kCharAddress[2] = { 0x08C6BB58, 0x08C6BB5A };

// Internal character order of the GGXX series (1 = Sol ... 25 = Justice). Only used for the
// on-screen name: profiles are keyed by the raw number, so they work even if a name is off.
static const char *const kNames[] = {
	"", "Sol", "Ky", "May", "Millia", "Axl", "Potemkin", "Chipp", "Eddie", "Baiken", "Faust",
	"Testament", "Jam", "Anji", "Johnny", "Venom", "Dizzy", "Slayer", "I-No", "Zappa",
	"Bridget", "Robo-Ky", "A.B.A", "Order-Sol", "Kliff", "Justice",
};
static const int kMaxCharacter = 25;

// How many consecutive identical reads before switching (checked every kPollFrames frames).
static const int kPollFrames = 10;
static const int kStableReads = 4;

struct State {
	bool initialized = false;
	bool active = false;            // enabled and running the right game
	int side = 0;                    // 0 = 1P, 1 = 2P
	std::string defaultStyle = "psp";
	std::map<int, std::string> styles;  // character -> "psp" / "arcade"
	Path launcherDir;                // where acplus.ini and the layout presets live
	Path profileDir;                 // saved per-character layouts
	int current = -1;                // character whose layout is applied
	std::string appliedSnapshot;     // layout as applied, to detect edits
	int candidate = -1;
	int candidateReads = 0;
	int frame = 0;
	bool needsRecreate = false;      // layout changed at startup, views must be rebuilt
};

inline State &Get() {
	static State state;
	return state;
}

inline std::string CharacterName(int id) {
	if (id >= 1 && id <= kMaxCharacter)
		return kNames[id];
	return StringFromFormat("#%d", id);
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

inline Path ProfilePath(int id) {
	return Get().profileDir / StringFromFormat("char_%02d.ini", id);
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
		for (const auto &[key, value] : chars->ToMap()) {
			int id = 0;
			if (TryParse(key, &id))
				s.styles[id] = value;
		}
	}
	const std::string gameId = g_paramSFO.GetValueString("DISC_ID");
	s.active = enabled && gameId == kGameId;
	s.side = s.side == 1 ? 1 : 0;
	if (s.defaultStyle != "psp" && s.defaultStyle != "arcade")
		s.defaultStyle = "psp";
	s.profileDir = GetSysDirectory(DIRECTORY_SYSTEM) / "acplus_profiles";
	INFO_LOG(Log::System, "AcPlus: character profiles %s (game %s, side %d)", s.active ? "on" : "off", gameId.c_str(), s.side + 1);
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
	if (!s.active || s.current < 0)
		return;
	std::string now = Snapshot();
	if (now == s.appliedSnapshot)
		return;
	IniFile ini;
	g_Config.SaveTouchLayout(ini);
	if (ini.Save(ProfilePath(s.current))) {
		s.appliedSnapshot = now;
		INFO_LOG(Log::System, "AcPlus: saved layout for character %d", s.current);
	}
}

inline void Apply(int id) {
	State &s = Get();
	IniFile ini;
	bool fromProfile = ini.Load(ProfilePath(id));
	if (!fromProfile) {
		auto it = s.styles.find(id);
		std::string style = it != s.styles.end() ? it->second : s.defaultStyle;
		if (style != "psp" && style != "arcade")
			style = s.defaultStyle;
		if (!ini.Load(s.launcherDir / ("acplus_layout_" + style + ".ini"))) {
			WARN_LOG(Log::System, "AcPlus: missing layout preset '%s'", style.c_str());
			return;
		}
	}
	g_Config.LoadTouchLayout(ini, true);
	s.current = id;
	s.appliedSnapshot = Snapshot();
	INFO_LOG(Log::System, "AcPlus: character %d, layout from %s", id, fromProfile ? "saved profile" : "preset");
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

	const u32 addr = kCharAddress[s.side];
	if (!Memory::IsValidAddress(addr))
		return false;
	const int id = Memory::Read_U8(addr);
	if (id < 1 || id > kMaxCharacter)
		return false;   // Not chosen yet (or not in a fight): leave the layout alone.

	if (id != s.candidate) {
		s.candidate = id;
		s.candidateReads = 1;
		return false;
	}
	if (++s.candidateReads < kStableReads || id == s.current)
		return false;

	SaveIfModified();
	Apply(id);
	g_OSD.Show(OSDType::MESSAGE_INFO, "Controles: " + CharacterName(id), 1.5f, "acplus_profile");
	return true;
}

}  // namespace AcPlusProfiles
