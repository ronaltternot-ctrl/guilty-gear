// GGXX AC+ launcher build: the player's own music instead of the game's (see
// Core/HLE/AcPlusMusicMute.h). The launcher writes acplus.ini [Music] MuteGameMusic (0, 1 or 2)
// before it starts the game; this reads it when the game screen opens, before the game boots.
//
// Included only from EmuScreen.cpp.

#pragma once

#include "Common/Data/Format/IniFile.h"
#include "Common/Log.h"
#include "Core/Config.h"
#include "Core/HLE/AcPlusMusicMute.h"

namespace AcPlusMusic {

inline void Apply() {
	int mode = 0;
	IniFile ini;
	if (ini.Load(g_Config.internalDataDirectory / "acplus.ini")) {
		const Section *sec = ini.GetSection("Music");
		if (sec)
			sec->Get("MuteGameMusic", &mode);
	}
	AcPlusMusicMute::SetMode(mode);
	if (mode != 0)
		INFO_LOG(Log::Audio, "Launcher music: the game's %s is off", mode == 2 ? "sound" : "music");
}

}  // namespace AcPlusMusic
