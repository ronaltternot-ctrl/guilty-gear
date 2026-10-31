// GGXX AC+ launcher build: the player's own music replaces the game's ("TU MÚSICA" in the
// launcher, MusicPlayer.java plays the songs).
//
// UI/AcPlusMusic.h sets the mode from acplus.ini [Music] MuteGameMusic when a game starts:
//   1 = the game's music off: what the PSP decodes from compressed audio, which is where games
//       keep their music tracks (ATRAC3 / ATRAC3+ through sceAtrac and sceSas, MP3 and AAC
//       through sceMp3, sceAac and sceAudiocodec). Effects and voices are usually short PCM or
//       VAG samples that sceSas plays, so they stay. Video sound (sceMpeg) stays too.
//   2 = all of the game's sound off (__sceAudio mixes silence).
// The decoders still run and the game gets the same timings and sample counts, only the PCM
// it receives is silent; nothing of this goes into save states.

#pragma once

#include <atomic>
#include <cstddef>
#include <cstring>

namespace AcPlusMusicMute {

enum Mode {
	OFF = 0,
	MUSIC = 1,
	ALL = 2,
};

inline std::atomic<int> g_mode{OFF};

inline void SetMode(int mode) {
	g_mode.store(mode < OFF ? OFF : (mode > ALL ? ALL : mode), std::memory_order_relaxed);
}

// The game's music is off (modes 1 and 2).
inline bool Music() {
	return g_mode.load(std::memory_order_relaxed) != OFF;
}

// All of the game's sound is off.
inline bool All() {
	return g_mode.load(std::memory_order_relaxed) == ALL;
}

// Clears decoded music when the game's music is off.
inline void Silence(void *pcm, size_t bytes) {
	if (pcm && bytes > 0 && Music())
		memset(pcm, 0, bytes);
}

}  // namespace AcPlusMusicMute
