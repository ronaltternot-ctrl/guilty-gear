// GGXX AC+ launcher build: what the custom HUD and the per-character profiles read from the
// memory of each supported game. No PPSSPP headers (tested on a PC: scripts/test_game_data.cpp).
//
// Guilty Gear XX ΛCore Plus, US ULUS-10409 (CWCheat addresses, base 0x08800000):
//   health   u16 fighter object + 0x1E     P1 0x0913D01E, P2 + 0x124     (max 420)
//   tension  u16 player block              P1 0x08C6BB7C, P2 + 0x148     (max 10000)
//   burst    u16 player block + 0xF8                                      (max 15000)
//   combo    u16 player block + 0x100: hits the OTHER player is landing
//   timer    u16 0x08C6CC14, frames (6000 = full)
//   fighters u8  0x08C6BB58 / 0x08C6BB5A (character select)
//
// BlazBlue (CWCheat pointer codes: a fixed address holds each player's object):
//   Calamity Trigger Portable  US ULUS-10519  0x08C0938C / 0x08C09390
//                              EU ULES-01497  0x08C0B80C / 0x08C0B810
//     health s32 +0x798, maximum +0x79C, Heat +0x2214 (max 10000)
//   Continuum Shift II         US ULUS-10579  0x08C32B70 / 0x08C32B74
//     health s32 +0x810, maximum +0x814, Heat +0x422C
//   Who is playing is in no code. The engine names sprites after the character ("rg000_00" is
//   Ragna; codes from dantarion/bbtools), so the object is searched for such names.
//   No timer and no combo counter: the combo is estimated by the HUD.
//
// The King of Fighters Collection: The Orochi Saga, US ULUS-10360. The five games are Neo Geo
//   games in an emulator. Each one keeps the Neo Geo work RAM (0x100000-0x10FFFF) at a fixed
//   PSP address, as 16-bit words: Neo Geo byte b is at base + ((b - 0x100000) ^ 1).
//   - base of each game: from the CWCheat health code (P1 health, P2 = + 0x200), confirmed by
//     the CWCheat timer code landing on the Neo Geo timer of the same game, and 16-byte aligned.
//   - Neo Geo addresses: FinalBurn Neo's cheat files (kof94.ini ... kof98.ini): health, POW /
//     power bar, stocks, team members, how many members each player has lost, their order.
//   - which game runs: the program word the CWCheat "Hit Anywhere" code checks, and its RAM
//     changing from frame to frame.
//   - '97: the CWCheat timer code is 0x1000 past the Neo Geo timer of the arcade version (the
//     health code is not), so the timer and team data are looked for at both places and the
//     one that makes sense is used.
//   The team member fighting is known in '96, '97 and '98; '94 and '95 only get the HUD.
//
// Street Fighter Alpha 3 MAX (CWCheat, base 0x08800000):
//   US ULUS-10062: P1 health u16 0x092E3F10 (full 0x90), P2 + 0x498 (the "Player 2" health and
//     guard codes are both 0x498 after player 1's), super meter u8 + 0xFE (full 0x90), guard
//     u16 + 0x1BC, timer u8 0x092E1231 (seconds, 99).
//   JP ULJM-05082: health 0x092E3890, timer 0x092E0BB1 (0x680 before the US ones), same offsets.
//   EU ULES-00235: some codes 0x20000 after the US addresses, others at the US ones: both are
//     tried and the one that holds a running fight is used.
//   Who is playing is in no code: no profiles yet. The report dumps both fighters' data so the
//   character byte can be found.

#pragma once

#include <cstring>
#include <string>

#include "AcPlusHudCore.h"

namespace AcGame {

using AcHud::Mem;
using AcHud::Snapshot;
using AcHud::Style;
typedef uint8_t u8;
typedef uint16_t u16;
typedef uint32_t u32;

enum Title { TITLE_NONE = 0, TITLE_GG = 1, TITLE_BB = 2, TITLE_KOF = 3, TITLE_SF = 4 };

// A character, for the profiles: key names the saved layout ("12" in Guilty Gear, "bb_rg",
// "kof_kyo"), name is shown on screen.
struct Fighter {
	std::string key;
	std::string name;
};

inline std::string Upper(const char *s) {
	std::string out = s ? s : "";
	for (char &c : out) {
		if (c >= 'a' && c <= 'z')
			c = (char)(c - 'a' + 'A');
	}
	return out;
}

inline bool Lower(u8 c) { return c >= 'a' && c <= 'z'; }
inline bool Digit(u8 c) { return c >= '0' && c <= '9'; }
inline bool AlNum(u8 c) { return Lower(c) || Digit(c) || (c >= 'A' && c <= 'Z'); }

inline Style MakeStyle(const char *meter, const char *meterWord, const char *gauge, const char *gaugeWord, bool timer) {
	Style s;
	s.meter = meter;
	s.meterWord = meterWord;
	s.gauge = gauge;
	s.gaugeWord = gaugeWord;
	s.timer = timer;
	return s;
}

inline std::string NormalizeId(const std::string &id) {
	std::string out;
	for (char c : id) {
		if (c == '-' || c == ' ')
			continue;
		out += (c >= 'a' && c <= 'z') ? (char)(c - 'a' + 'A') : c;
	}
	return out;
}

// ---------------------------------------------------------------------------------------------
// Guilty Gear XX ΛCore Plus

namespace GG {

const u32 kP1Object = 0x0913D000;
const u32 kObjectStride = 0x124;
const u32 kObjectHealth = 0x1E;
const u32 kP1Block = 0x08C6BB7C;
const u32 kBlockStride = 0x148;
const u32 kBlockBurst = 0xF8;
const u32 kBlockCombo = 0x100;
const u32 kTimer = 0x08C6CC14;
const u32 kSelect[2] = { 0x08C6BB58, 0x08C6BB5A };

const int kMaxHealth = 420;
const int kMaxTension = 10000;
const int kMaxBurst = 15000;
const int kTimerFrames = 6000;

// Internal character order of the GGXX series (1 = Sol ... 25 = Justice).
static const char *const kNames[] = {
	"", "Sol", "Ky", "May", "Millia", "Axl", "Potemkin", "Chipp", "Eddie", "Baiken", "Faust",
	"Testament", "Jam", "Anji", "Johnny", "Venom", "Dizzy", "Slayer", "I-No", "Zappa",
	"Bridget", "Robo-Ky", "A.B.A", "Order-Sol", "Kliff", "Justice",
};
const int kMaxCharacter = 25;

inline bool Read(const Mem &m, Snapshot *s) {
	bool ok = true;
	for (int p = 0; p < 2; p++) {
		const u32 block = kP1Block + (u32)p * kBlockStride;
		const u32 other = kP1Block + (u32)(1 - p) * kBlockStride;
		int tension = 0, burst = 0, character = 0, id = 0;
		ok &= AcHud::Read16(m, kP1Object + (u32)p * kObjectStride + kObjectHealth, &s->health[p]);
		ok &= AcHud::Read16(m, block, &tension);
		ok &= AcHud::Read16(m, block + kBlockBurst, &burst);
		ok &= AcHud::Read16(m, other + kBlockCombo, &s->combo[p]);
		ok &= AcHud::Read8(m, kSelect[p], &character);
		ok &= AcHud::Read16(m, kP1Object + (u32)p * kObjectStride, &id);
		ok &= id >= 1 && id <= 26;
		ok &= s->health[p] <= kMaxHealth && tension <= kMaxTension && burst <= kMaxBurst && s->combo[p] <= 999;
		ok &= character >= 1 && character <= kMaxCharacter;
		s->healthMax[p] = kMaxHealth;
		s->meter[p] = tension / (float)kMaxTension;
		s->gauge[p] = burst / (float)kMaxBurst;
		s->stocks[p] = -1;
		if (character >= 1 && character <= kMaxCharacter)
			s->name[p] = Upper(kNames[character]);
	}
	int frames = 0;
	ok &= AcHud::Read16(m, kTimer, &frames);
	s->timer = frames <= 0 ? 0 : frames > kTimerFrames + 60 ? AcHud::kTimerUnknown : (frames + 59) / 60;
	if (ok)
		s->objectsHash = AcHud::Hash(m, kP1Object, kObjectStride * 2);
	s->style = &AcHud::DefaultStyle();
	s->valid = ok;
	return ok;
}

inline bool PlayerFighter(const Mem &m, int player, Fighter *f) {
	int id = 0;
	if (!AcHud::Read8(m, kSelect[player & 1], &id) || id < 1 || id > kMaxCharacter)
		return false;
	f->key = std::to_string(id);
	f->name = kNames[id];
	return true;
}

}  // namespace GG

// ---------------------------------------------------------------------------------------------
// BlazBlue

namespace BB {

struct Character {
	const char *code;   // sprite prefix
	const char *name;
};

// Calamity Trigger, then the ones Continuum Shift added.
static const Character kCharacters[] = {
	{ "rg", "Ragna" }, { "jn", "Jin" }, { "no", "Noel" }, { "rc", "Rachel" }, { "tk", "Taokaka" },
	{ "tg", "Tager" }, { "lc", "Litchi" }, { "ar", "Arakune" }, { "bn", "Bang" }, { "ca", "Carl" },
	{ "ha", "Hakumen" }, { "ny", "Nu-13" }, { "tb", "Tsubaki" }, { "hz", "Hazama" }, { "mu", "Mu-12" },
	{ "mk", "Makoto" }, { "vh", "Valkenhayn" }, { "pt", "Platinum" }, { "rl", "Relius" }, { "rm", "Lambda-11" },
};
const int kCharacterCount = (int)(sizeof(kCharacters) / sizeof(kCharacters[0]));

struct Version {
	const char *discId;
	const char *label;
	u32 player[2];      // where the object pointers are
	u32 health, healthMax, heat;
	u32 objectSize;     // searched for sprite names
};

static const Version kVersions[] = {
	{ "ULUS10519", "BlazBlue: Calamity Trigger (US)", { 0x08C0938C, 0x08C09390 }, 0x798, 0x79C, 0x2214, 0x2400 },
	{ "ULES01497", "BlazBlue: Calamity Trigger (EU)", { 0x08C0B80C, 0x08C0B810 }, 0x798, 0x79C, 0x2214, 0x2400 },
	{ "ULUS10579", "BlazBlue: Continuum Shift II (US)", { 0x08C32B70, 0x08C32B74 }, 0x810, 0x814, 0x422C, 0x4400 },
};

inline const Version *FindVersion(const std::string &discId) {
	const std::string id = NormalizeId(discId);
	for (const Version &v : kVersions) {
		if (id == v.discId)
			return &v;
	}
	return nullptr;
}

inline const Style &GetStyle() {
	static const Style style = MakeStyle("HEAT", "Heat", nullptr, nullptr, false);
	return style;
}

// "rg000_00": two lowercase letters, three digits, underscore, two digits.
inline bool IsSpriteName(const u8 *p) {
	return Lower(p[0]) && Lower(p[1]) && Digit(p[2]) && Digit(p[3]) && Digit(p[4]) && p[5] == '_' && Digit(p[6]) && Digit(p[7]);
}

// The character of a fighter object: the known code that most of its sprite names start with.
// Returns an index in kCharacters, or -1. seen collects the names found (for the report).
inline int FindCharacter(const Mem &m, u32 object, u32 size, std::string *seen = nullptr) {
	if (!m.Valid(object, size))
		return -1;
	const u8 *p = m.Ptr(object);
	int counts[kCharacterCount] = {};
	int found = 0;
	for (u32 i = 0; i + 9 <= size; i++) {
		if (!IsSpriteName(p + i) || (i > 0 && AlNum(p[i - 1])) || AlNum(p[i + 8]))
			continue;
		for (int k = 0; k < kCharacterCount; k++) {
			if (p[i] == (u8)kCharacters[k].code[0] && p[i + 1] == (u8)kCharacters[k].code[1])
				counts[k]++;
		}
		if (seen && found < 12) {
			char buf[16];
			snprintf(buf, sizeof(buf), "%.8s@%X ", (const char *)(p + i), i);
			*seen += buf;
		}
		found++;
		i += 7;
	}
	int best = -1;
	for (int k = 0; k < kCharacterCount; k++) {
		if (counts[k] > 0 && (best < 0 || counts[k] > counts[best]))
			best = k;
	}
	return best;
}

}  // namespace BB

// ---------------------------------------------------------------------------------------------
// The King of Fighters Collection: The Orochi Saga

namespace KOF {

struct Character {
	const char *key;
	const char *name;
	const char *hud;
};

// Every fighter of '96, '97 and '98 once (the profiles are shared between the games).
static const Character kCharacters[] = {
	{ "kof_kyo", "Kyo Kusanagi", "KYO" },               // 0
	{ "kof_benimaru", "Benimaru Nikaido", "BENIMARU" },
	{ "kof_daimon", "Goro Daimon", "DAIMON" },
	{ "kof_terry", "Terry Bogard", "TERRY" },
	{ "kof_andy", "Andy Bogard", "ANDY" },
	{ "kof_joe", "Joe Higashi", "JOE" },                 // 5
	{ "kof_ryo", "Ryo Sakazaki", "RYO" },
	{ "kof_robert", "Robert Garcia", "ROBERT" },
	{ "kof_yuri", "Yuri Sakazaki", "YURI" },
	{ "kof_leona", "Leona", "LEONA" },
	{ "kof_ralf", "Ralf Jones", "RALF" },                // 10
	{ "kof_clark", "Clark Steel", "CLARK" },
	{ "kof_athena", "Athena Asamiya", "ATHENA" },
	{ "kof_kensou", "Sie Kensou", "KENSOU" },
	{ "kof_chin", "Chin Gentsai", "CHIN" },
	{ "kof_chizuru", "Chizuru Kagura", "CHIZURU" },      // 15
	{ "kof_mai", "Mai Shiranui", "MAI" },
	{ "kof_king", "King", "KING" },
	{ "kof_kim", "Kim Kaphwan", "KIM" },
	{ "kof_chang", "Chang Koehan", "CHANG" },
	{ "kof_choi", "Choi Bounge", "CHOI" },               // 20
	{ "kof_yashiro", "Yashiro Nanakase", "YASHIRO" },
	{ "kof_shermie", "Shermie", "SHERMIE" },
	{ "kof_chris", "Chris", "CHRIS" },
	{ "kof_yamazaki", "Ryuji Yamazaki", "YAMAZAKI" },
	{ "kof_mary", "Blue Mary", "MARY" },                 // 25
	{ "kof_billy", "Billy Kane", "BILLY" },
	{ "kof_iori", "Iori Yagami", "IORI" },
	{ "kof_mature", "Mature", "MATURE" },
	{ "kof_vice", "Vice", "VICE" },
	{ "kof_heidern", "Heidern", "HEIDERN" },             // 30
	{ "kof_takuma", "Takuma Sakazaki", "TAKUMA" },
	{ "kof_saisyu", "Saisyu Kusanagi", "SAISYU" },
	{ "kof_heavyd", "Heavy D!", "HEAVY D!" },
	{ "kof_lucky", "Lucky Glauber", "LUCKY" },
	{ "kof_brian", "Brian Battler", "BRIAN" },           // 35
	{ "kof_rugal", "Rugal Bernstein", "RUGAL" },
	{ "kof_shingo", "Shingo Yabuki", "SHINGO" },
	{ "kof_kasumi", "Kasumi Todo", "KASUMI" },
	{ "kof_geese", "Geese Howard", "GEESE" },
	{ "kof_krauser", "Wolfgang Krauser", "KRAUSER" },    // 40
	{ "kof_mrbig", "Mr. Big", "MR. BIG" },
	{ "kof_goenitz", "Goenitz", "GOENITZ" },
	{ "kof_oiori", "Orochi Iori", "O. IORI" },
	{ "kof_oleona", "Orochi Leona", "O. LEONA" },
	{ "kof_orochi", "Orochi", "OROCHI" },                // 45
};
const int kCharacterCount = (int)(sizeof(kCharacters) / sizeof(kCharacters[0]));

// Each game's character numbers (FinalBurn Neo's "Select Character" cheats) -> kCharacters.
static const u8 kRoster96[] = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 38, 16, 17, 18, 19, 20, 27, 28, 29, 39, 40, 41, 15, 42 };
static const u8 kRoster97[] = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 43, 44, 45, 37 };
static const u8 kRoster98[] = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37 };

const u32 kNeoRam = 0x100000;
const u32 kPlayerStride = 0x200;   // player 2's object, after player 1's

struct Game {
	const char *label;
	u32 base;            // PSP address of the Neo Geo work RAM
	u32 sigAddr;         // PSP address of a program word ("Hit Anywhere" code)
	u16 sig;
	u32 object;          // Neo Geo address of player 1's object (hashed: both objects)
	u32 health;          // Neo Geo addresses for player 1 (player 2: + kPlayerStride)
	int healthMax;
	u32 power;
	int powerMax;
	u32 stocks;          // 0: none
	u32 timer;           // BCD seconds
	// Team (0: not known): members lost, characters A B C, order of A B C; player 2 + stride.
	u32 lost, chars, order, teamStride;
	const u8 *roster;
	int rosterSize;
	bool power2;         // "POWER" bar ('96-'98) instead of "POW"
	u32 shift;           // other place to try for the timer and team data (0: none)
};

static const Game kGames[] = {
	{ "KOF '94", 0x08EB1610, 0x09A0EDEE, 0x668E, 0x108200, 0x108221, 0xCF, 0x108219, 0x7F, 0, 0x10882E, 0, 0, 0, 0, nullptr, 0, false, 0 },
	{ "KOF '95", 0x09745420, 0x094FE5E0, 0x66BE, 0x108200, 0x108221, 0xCF, 0x108219, 0x7F, 0, 0x10A836, 0, 0, 0, 0, nullptr, 0, false, 0 },
	{ "KOF '96", 0x0983F720, 0x094F8B50, 0x6600, 0x108100, 0x108239, 0x67, 0x1081EA, 0x3C, 0, 0x10A836, 0x10A845, 0x10A846, 0x10A84C, 0x11, kRoster96, (int)sizeof(kRoster96), true, 0 },
	{ "KOF '97", 0x09B3B8A0, 0x095F730E, 0x6600, 0x108100, 0x108239, 0x67, 0x1081EA, 0x40, 0x1082E3, 0x10A83A, 0x10A84A, 0x10A84B, 0x10A851, 0x11, kRoster97, (int)sizeof(kRoster97), true, 0x1000 },
	{ "KOF '98", 0x08F1DA50, 0x093A5314, 0x6600, 0x108100, 0x108239, 0x67, 0x1081EA, 0x40, 0x1082E3, 0x10A83A, 0x10A84D, 0x10A84E, 0x10A854, 0x11, kRoster98, (int)sizeof(kRoster98), true, 0 },
};
const int kGameCount = (int)(sizeof(kGames) / sizeof(kGames[0]));

inline bool IsSupported(const std::string &discId) {
	return NormalizeId(discId) == "ULUS10360";
}

inline const Style &GetStyle(const Game &g) {
	static const Style pow = MakeStyle("POW", "POW", nullptr, nullptr, true);
	static const Style power = MakeStyle("POWER", "Power", nullptr, nullptr, true);
	return g.power2 ? power : pow;
}

// PSP address of a Neo Geo byte.
inline u32 Addr(const Game &g, u32 neo) {
	return g.base + ((neo - kNeoRam) ^ 1);
}

inline bool Byte(const Mem &m, const Game &g, u32 neo, int *out) {
	return AcHud::Read8(m, Addr(g, neo), out);
}

inline bool SignatureOk(const Mem &m, const Game &g) {
	int w = 0;
	return AcHud::Read16(m, g.sigAddr, &w) && w == g.sig;
}

inline u32 ObjectsHash(const Mem &m, const Game &g) {
	return AcHud::Hash(m, g.base + (g.object - kNeoRam), kPlayerStride * 2);
}

inline bool HealthOk(const Mem &m, const Game &g) {
	for (int p = 0; p < 2; p++) {
		int hp = 0;
		if (!Byte(m, g, g.health + p * kPlayerStride, &hp) || hp > g.healthMax)
			return false;
	}
	return true;
}

// Index in kCharacters of the team member fighting for a player, or -1. shift: 0 or g.shift.
inline int Member(const Mem &m, const Game &g, int player, u32 shift = 0) {
	if (!g.lost || !g.roster)
		return -1;
	const u32 off = (u32)player * g.teamStride + shift;
	int lost = 0, slot = 0, id = 0;
	if (!Byte(m, g, g.lost + off, &lost) || lost < 0 || lost > 2)
		return -1;
	if (!Byte(m, g, g.order + off + (u32)lost, &slot) || slot < 0 || slot > 2)
		return -1;
	if (!Byte(m, g, g.chars + off + (u32)slot, &id) || id < 0 || id >= g.rosterSize)
		return -1;
	return g.roster[id];
}

inline int Bcd(int v) {
	const int hi = v >> 4, lo = v & 15;
	return hi <= 9 && lo <= 9 ? hi * 10 + lo : AcHud::kTimerUnknown;
}

// The whole team of a player looks right: three different slots in the order, known fighters.
inline bool TeamOk(const Mem &m, const Game &g, int player, u32 shift) {
	if (!g.lost || !g.roster)
		return false;
	const u32 off = (u32)player * g.teamStride + shift;
	int lost = 0;
	if (!Byte(m, g, g.lost + off, &lost) || lost > 2)
		return false;
	int seen = 0;
	for (u32 k = 0; k < 3; k++) {
		int slot = 0, id = 0;
		if (!Byte(m, g, g.order + off + k, &slot) || slot > 2 || (seen & (1 << slot)))
			return false;
		seen |= 1 << slot;
		if (!Byte(m, g, g.chars + off + k, &id) || id >= g.rosterSize)
			return false;
	}
	return true;
}

// Which place holds the timer and team data: 0 or g.shift (the one that makes more sense).
inline u32 PickShift(const Mem &m, const Game &g) {
	if (!g.shift)
		return 0;
	auto score = [&](u32 sh) {
		int tm = 0, sc = 0;
		if (Byte(m, g, g.timer + sh, &tm) && Bcd(tm) >= 0)
			sc++;
		sc += TeamOk(m, g, 0, sh) ? 2 : 0;
		sc += TeamOk(m, g, 1, sh) ? 2 : 0;
		return sc;
	};
	// On a tie, the place the PSP timer code uses.
	return score(0) > score(g.shift) ? 0 : g.shift;
}

}  // namespace KOF

// ---------------------------------------------------------------------------------------------
// Street Fighter Alpha 3 MAX

namespace SF {

struct Place {
	u32 health;   // player 1's health (player 2: + kStride)
	u32 timer;
};

struct Version {
	const char *discId;
	const char *label;
	Place places[2];
	int placeCount;
};

static const Version kVersions[] = {
	{ "ULUS10062", "Street Fighter Alpha 3 MAX (US)", { { 0x092E3F10, 0x092E1231 }, { 0, 0 } }, 1 },
	{ "ULJM05082", "Street Fighter Zero 3 Double Upper (JP)", { { 0x092E3890, 0x092E0BB1 }, { 0, 0 } }, 1 },
	{ "ULES00235", "Street Fighter Alpha 3 MAX (EU)", { { 0x09303F10, 0x09301231 }, { 0x092E3F10, 0x092E1231 } }, 2 },
};

const u32 kStride = 0x498;
const u32 kSuper = 0xFE;
const u32 kGuard = 0x1BC;
const int kFullHealth = 0x90;
const int kFullSuper = 0x90;
const int kMaxHealth = 999;     // the CWCheat "v2" health code writes 999

inline const Version *FindVersion(const std::string &discId) {
	const std::string id = NormalizeId(discId);
	for (const Version &v : kVersions) {
		if (id == v.discId)
			return &v;
	}
	return nullptr;
}

inline const Style &GetStyle() {
	static const Style style = MakeStyle("SUPER", "Súper", "GUARD", "Guardia", true);
	return style;
}

// Health and super of both players are in range.
inline bool ValuesOk(const Mem &m, const Place &pl) {
	for (int p = 0; p < 2; p++) {
		int hp = 0, sup = 0;
		const u32 h = pl.health + (u32)p * kStride;
		if (!AcHud::Read16(m, h, &hp) || hp > kMaxHealth || !AcHud::Read8(m, h + kSuper, &sup))
			return false;
	}
	return true;
}

inline u32 ObjectsHash(const Mem &m, const Place &pl) {
	return AcHud::Hash(m, pl.health, kStride * 2);
}

}  // namespace SF

// ---------------------------------------------------------------------------------------------
// One reader per running game: Init() with the disc ID, then Read() / PlayerFighter().

class Reader {
public:
	void Init(const std::string &discId) {
		discId_ = NormalizeId(discId);
		title_ = TITLE_NONE;
		bb_ = BB::FindVersion(discId_);
		sf_ = SF::FindVersion(discId_);
		if (discId_ == "ULUS10409")
			title_ = TITLE_GG;
		else if (bb_)
			title_ = TITLE_BB;
		else if (KOF::IsSupported(discId_))
			title_ = TITLE_KOF;
		else if (sf_)
			title_ = TITLE_SF;
	}

	Title GetTitle() const { return title_; }
	bool Supported() const { return title_ != TITLE_NONE; }

	const char *Label() const {
		switch (title_) {
		case TITLE_GG: return "Guilty Gear XX AC+ (US)";
		case TITLE_BB: return bb_->label;
		case TITLE_KOF: return kof_ >= 0 ? KOF::kGames[kof_].label : "KOF Orochi Saga (US)";
		case TITLE_SF: return sf_->label;
		default: return "";
		}
	}

	// The KOF game running (-1 none), checked at most every 1/30 s.
	int KofGame(const Mem &m, double t) {
		if (title_ != TITLE_KOF)
			return -1;
		if (t - kofChecked_ < 1.0 / 30.0 && t >= kofChecked_)
			return kof_;
		kofChecked_ = t;
		int live = -1;
		for (int g = 0; g < KOF::kGameCount; g++) {
			const KOF::Game &game = KOF::kGames[g];
			kofSig_[g] = KOF::SignatureOk(m, game);
			if (!kofSig_[g])
				continue;
			const u32 h = KOF::ObjectsHash(m, game);
			if (h != kofHash_[g]) {
				kofHash_[g] = h;
				kofChanged_[g] = t;
			}
			const bool ok = KOF::HealthOk(m, game) && t - kofChanged_[g] < 2.0;
			if (ok && (live < 0 || g == kof_))
				live = g;
		}
		if (live >= 0) {
			if (live != kof_ || t - kofShiftChecked_ > 1.0 || t < kofShiftChecked_) {
				kofShift_[live] = KOF::PickShift(m, KOF::kGames[live]);
				kofShiftChecked_ = t;
			}
			kof_ = live;
		} else if (kof_ >= 0 && !(kofSig_[kof_] && KOF::HealthOk(m, KOF::kGames[kof_])))
			kof_ = -1;   // keep the game while it is paused; forget it once it is gone
		return kof_;
	}

	// Values for the HUD. False (and s->valid false) when no fight is loaded.
	bool Read(const Mem &m, double t, Snapshot *s) {
		*s = Snapshot();
		switch (title_) {
		case TITLE_GG:
			return GG::Read(m, s);
		case TITLE_BB:
			return ReadBB(m, t, s);
		case TITLE_KOF:
			return ReadKof(m, t, s);
		case TITLE_SF:
			return ReadSf(m, t, s);
		default:
			return false;
		}
	}

	// Street Fighter: where the fight is (index in sf_->places, -1 none), checked every 1/30 s.
	int SfPlace(const Mem &m, double t) {
		if (title_ != TITLE_SF)
			return -1;
		if (t - sfChecked_ < 1.0 / 30.0 && t >= sfChecked_)
			return sfPlace_;
		sfChecked_ = t;
		int live = -1;
		for (int i = 0; i < sf_->placeCount && i < 2; i++) {
			const SF::Place &pl = sf_->places[i];
			if (!SF::ValuesOk(m, pl))
				continue;
			const u32 h = SF::ObjectsHash(m, pl);
			if (h != sfHash_[i]) {
				sfHash_[i] = h;
				sfChanged_[i] = t;
			}
			if (t - sfChanged_[i] < 2.0 && (live < 0 || i == sfPlace_))
				live = i;
		}
		if (live >= 0)
			sfPlace_ = live;
		else if (sfPlace_ >= 0 && !SF::ValuesOk(m, sf_->places[sfPlace_]))
			sfPlace_ = -1;
		else if (sfPlace_ < 0 && sf_->placeCount == 1 && SF::ValuesOk(m, sf_->places[0]))
			sfPlace_ = 0;   // only one place: use it (the HUD hides while nothing moves)
		return sfPlace_;
	}

	// The character a player (0 = 1P) is using, for the profiles.
	bool PlayerFighter(const Mem &m, double t, int player, Fighter *f) {
		player &= 1;
		switch (title_) {
		case TITLE_GG:
			return GG::PlayerFighter(m, player, f);
		case TITLE_BB: {
			int hp[2], max[2];
			u32 obj[2];
			if (!BBObjects(m, obj, hp, max))
				return false;
			const int c = BBCharacter(m, t, player, obj[player]);
			if (c < 0)
				return false;
			f->key = std::string("bb_") + BB::kCharacters[c].code;
			f->name = BB::kCharacters[c].name;
			return true;
		}
		case TITLE_KOF: {
			const int g = KofGame(m, t);
			if (g < 0)
				return false;
			const int c = KOF::Member(m, KOF::kGames[g], player, kofShift_[g]);
			if (c < 0)
				return false;
			f->key = KOF::kCharacters[c].key;
			f->name = KOF::kCharacters[c].name;
			return true;
		}
		default:
			return false;
		}
	}

	// Whether this game version can tell who is playing (for the profiles).
	bool KnowsFighters() const {
		return title_ == TITLE_GG || title_ == TITLE_BB || title_ == TITLE_KOF;
	}

	// Diagnostics for PSP/SYSTEM/acplus_game_report.txt.
	std::string Report(const Mem &m, double t) {
		std::string out = std::string("game: ") + Label() + " (" + discId_ + ")\n";
		char line[200];
		if (title_ == TITLE_BB) {
			for (int p = 0; p < 2; p++) {
				u32 obj = 0;
				int hp = -1, max = -1, heat = -1;
				AcHud::Read32(m, bb_->player[p], &obj);
				if (m.Valid(obj, bb_->objectSize)) {
					u32 v = 0;
					AcHud::Read32(m, obj + bb_->health, &v);
					hp = (int)v;
					AcHud::Read32(m, obj + bb_->healthMax, &v);
					max = (int)v;
					AcHud::Read16(m, obj + bb_->heat, &heat);
				}
				std::string seen;
				const int c = m.Valid(obj, bb_->objectSize) ? BB::FindCharacter(m, obj, bb_->objectSize, &seen) : -1;
				snprintf(line, sizeof(line), "P%d object %08X health %d/%d heat %d character %s\n", p + 1, obj, hp, max, heat, c >= 0 ? BB::kCharacters[c].name : "?");
				out += line;
				out += "  sprites: " + (seen.empty() ? std::string("(none found)") : seen) + "\n";
			}
		} else if (title_ == TITLE_SF) {
			const int place = SfPlace(m, t);
			for (int i = 0; i < sf_->placeCount && i < 2; i++) {
				const SF::Place &pl = sf_->places[i];
				int hp1 = -1, hp2 = -1, s1 = -1, s2 = -1, g1 = -1, g2 = -1, tm = -1;
				AcHud::Read16(m, pl.health, &hp1);
				AcHud::Read16(m, pl.health + SF::kStride, &hp2);
				AcHud::Read8(m, pl.health + SF::kSuper, &s1);
				AcHud::Read8(m, pl.health + SF::kStride + SF::kSuper, &s2);
				AcHud::Read16(m, pl.health + SF::kGuard, &g1);
				AcHud::Read16(m, pl.health + SF::kStride + SF::kGuard, &g2);
				AcHud::Read8(m, pl.timer, &tm);
				snprintf(line, sizeof(line), "%splace %08X: health %d %d, super %d %d, guard %d %d, timer %d\n",
					i == place ? "> " : "  ", pl.health, hp1, hp2, s1, s2, g1, g2, tm);
				out += line;
			}
			// Both fighters' data around the health, to find the character byte later.
			if (place >= 0) {
				for (int p = 0; p < 2; p++) {
					const u32 from = sf_->places[place].health + (u32)p * SF::kStride - 0x100;
					snprintf(line, sizeof(line), "P%d data from %08X:\n", p + 1, from);
					out += line;
					for (u32 row = 0; row < 0x598 && m.Valid(from + row, 16); row += 16) {
						const u8 *b = m.Ptr(from + row);
						snprintf(line, sizeof(line), "%04X:", row);
						out += line;
						for (int k = 0; k < 16; k++) {
							snprintf(line, sizeof(line), " %02X", b[k]);
							out += line;
						}
						out += "\n";
					}
				}
			}
		} else if (title_ == TITLE_KOF) {
			const int running = KofGame(m, t);
			for (int g = 0; g < KOF::kGameCount; g++) {
				const KOF::Game &game = KOF::kGames[g];
				int hp1 = -1, hp2 = -1, pw = -1, tm = -1, lost = -1;
				const u32 sh = kofShift_[g];
				KOF::Byte(m, game, game.health, &hp1);
				KOF::Byte(m, game, game.health + KOF::kPlayerStride, &hp2);
				KOF::Byte(m, game, game.power, &pw);
				KOF::Byte(m, game, game.timer + sh, &tm);
				if (game.lost)
					KOF::Byte(m, game, game.lost + sh, &lost);
				const int c1 = KOF::Member(m, game, 0, sh), c2 = KOF::Member(m, game, 1, sh);
				snprintf(line, sizeof(line), "%s%s: program %s, health %d %d, power %d, timer %02X, lost %d, shift %X, P1 %s, P2 %s\n",
					g == running ? "> " : "  ", game.label, KOF::SignatureOk(m, game) ? "yes" : "no", hp1, hp2, pw, tm & 0xFF, lost, sh,
					c1 >= 0 ? KOF::kCharacters[c1].name : "?", c2 >= 0 ? KOF::kCharacters[c2].name : "?");
				out += line;
			}
		}
		return out;
	}

private:
	bool BBObjects(const Mem &m, u32 obj[2], int hp[2], int max[2]) const {
		for (int p = 0; p < 2; p++) {
			u32 v = 0;
			if (!AcHud::Read32(m, bb_->player[p], &obj[p]) || !m.Valid(obj[p], bb_->objectSize))
				return false;
			AcHud::Read32(m, obj[p] + bb_->health, &v);
			hp[p] = (int)v;
			AcHud::Read32(m, obj[p] + bb_->healthMax, &v);
			max[p] = (int)v;
			if (max[p] < 1000 || max[p] > 40000 || hp[p] < 0 || hp[p] > max[p])
				return false;
		}
		return true;
	}

	// Character of a BlazBlue object, searched again every second (objects are reused).
	int BBCharacter(const Mem &m, double t, int p, u32 obj) {
		if (obj != bbObject_[p] || t - bbScanned_[p] > 1.0 || t < bbScanned_[p]) {
			bbObject_[p] = obj;
			bbScanned_[p] = t;
			bbCharacter_[p] = BB::FindCharacter(m, obj, bb_->objectSize);
		}
		return bbCharacter_[p];
	}

	bool ReadBB(const Mem &m, double t, Snapshot *s) {
		u32 obj[2];
		if (!BBObjects(m, obj, s->health, s->healthMax))
			return false;
		for (int p = 0; p < 2; p++) {
			int heat = 0;
			if (!AcHud::Read16(m, obj[p] + bb_->heat, &heat) || heat > 10000)
				return false;
			s->meter[p] = heat / 10000.0f;
			const int c = BBCharacter(m, t, p, obj[p]);
			if (c >= 0)
				s->name[p] = Upper(BB::kCharacters[c].name);
		}
		s->objectsHash = AcHud::Hash(m, obj[1], 0x400, AcHud::Hash(m, obj[0], 0x400));
		s->style = &BB::GetStyle();
		s->valid = true;
		return true;
	}

	bool ReadKof(const Mem &m, double t, Snapshot *s) {
		const int g = KofGame(m, t);
		if (g < 0)
			return false;
		const KOF::Game &game = KOF::kGames[g];
		for (int p = 0; p < 2; p++) {
			const u32 off = (u32)p * KOF::kPlayerStride;
			int pw = 0;
			if (!KOF::Byte(m, game, game.health + off, &s->health[p]) || s->health[p] > game.healthMax)
				return false;
			s->healthMax[p] = game.healthMax;
			if (KOF::Byte(m, game, game.power + off, &pw))
				s->meter[p] = std::min(1.0f, pw / (float)game.powerMax);
			int st = -1;
			if (game.stocks && KOF::Byte(m, game, game.stocks + off, &st) && st >= 0 && st <= 5)
				s->stocks[p] = st;
			const int c = KOF::Member(m, game, p, kofShift_[g]);
			if (c >= 0)
				s->name[p] = KOF::kCharacters[c].hud;
		}
		int tm = 0;
		s->timer = KOF::Byte(m, game, game.timer + kofShift_[g], &tm) ? KOF::Bcd(tm) : AcHud::kTimerUnknown;
		s->objectsHash = kofHash_[g];
		s->style = &KOF::GetStyle(game);
		s->valid = true;
		return true;
	}

	bool ReadSf(const Mem &m, double t, Snapshot *s) {
		const int place = SfPlace(m, t);
		if (place < 0)
			return false;
		const SF::Place &pl = sf_->places[place];
		for (int p = 0; p < 2; p++) {
			const u32 h = pl.health + (u32)p * SF::kStride;
			int sup = 0, guard = 0;
			if (!AcHud::Read16(m, h, &s->health[p]) || s->health[p] > SF::kMaxHealth || !AcHud::Read8(m, h + SF::kSuper, &sup))
				return false;
			// Full health is 0x90 (more only with World Tour stamina or the "v2" health code).
			sfHealthMax_[p] = std::max(sfHealthMax_[p], std::max(SF::kFullHealth, s->health[p]));
			s->healthMax[p] = sfHealthMax_[p];
			s->meter[p] = std::min(1.0f, sup / (float)SF::kFullSuper);
			// The guard meter's length depends on the character: its value at the start of each
			// round (full health) is taken as full.
			if (AcHud::Read16(m, h + SF::kGuard, &guard) && guard <= 0x400) {
				if (s->health[p] >= SF::kFullHealth && (sfLastHealth_[p] < s->health[p] || guard > sfGuardMax_[p]))
					sfGuardMax_[p] = std::max(1, guard);
				sfGuardMax_[p] = std::max(sfGuardMax_[p], guard);
				s->gauge[p] = sfGuardMax_[p] > 0 ? guard / (float)sfGuardMax_[p] : 0.0f;
			}
			sfLastHealth_[p] = s->health[p];
		}
		int tm = 0;
		s->timer = AcHud::Read8(m, pl.timer, &tm) && tm <= 99 ? tm : AcHud::kTimerUnknown;
		s->objectsHash = sfHash_[place];
		s->style = &SF::GetStyle();
		s->valid = true;
		return true;
	}

	std::string discId_;
	Title title_ = TITLE_NONE;
	const BB::Version *bb_ = nullptr;
	const SF::Version *sf_ = nullptr;
	// Street Fighter: where the fight is, running maxima.
	int sfPlace_ = -1;
	double sfChecked_ = -100;
	u32 sfHash_[2] = {};
	double sfChanged_[2] = { -100, -100 };
	int sfHealthMax_[2] = { SF::kFullHealth, SF::kFullHealth };
	int sfGuardMax_[2] = { 1, 1 };
	int sfLastHealth_[2] = { 0, 0 };
	// BlazBlue: character found in each object.
	u32 bbObject_[2] = {};
	double bbScanned_[2] = { -100, -100 };
	int bbCharacter_[2] = { -1, -1 };
	// KOF: which game runs.
	int kof_ = -1;
	double kofChecked_ = -100;
	bool kofSig_[KOF::kGameCount] = {};
	u32 kofHash_[KOF::kGameCount] = {};
	double kofChanged_[KOF::kGameCount] = { -100, -100, -100, -100, -100 };
	u32 kofShift_[KOF::kGameCount] = {};
	double kofShiftChecked_ = -100;
};

}  // namespace AcGame
