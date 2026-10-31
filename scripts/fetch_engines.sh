#!/usr/bin/env bash
# Downloads the engines of the other systems into the PPSSPP tree prepared by prepare.sh:
#   - FBNeo and PCSX ReARMed libretro cores (arcade / Neo Geo and PlayStation 1 games), Lemuroid's
#     Android builds, pinned commit.
#   - Ikemen GO v1.0.0 for Android (MUGEN games): libmain.so with SDL2, FFmpeg and libxmp,
#     and the engine files a MUGEN game doesn't have (external/, data/, font/).
# Native libraries go to android/extlibs/<abi> (patches/0008 adds it to jniLibs), the engine
# files to assets/ikemen (MugenInstaller copies them into each game).
#
# Usage: scripts/fetch_engines.sh [ppsspp-dir]      (default: build/ppsspp)
# Downloads are cached in build/engine-cache (override with ENGINE_CACHE).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-$HERE/build/ppsspp}"
CACHE="${ENGINE_CACHE:-$HERE/build/engine-cache}"
ABI="arm64-v8a"
LIBS="$TARGET/android/extlibs/$ABI"
ASSETS="$TARGET/assets/ikemen"

CORES_REPO="Swordfish90/LemuroidCores"
CORES_COMMIT="fee2e824525daa22bcf318f96127fe43fa8a15ad"
# name  module  sha256
CORES="
fbneo lemuroid_core_fbneo 88fd9f4aa9d8f235b58e64e78947ede0965c1e7ff85caf03dbcfcfe914b2e583
pcsx_rearmed lemuroid_core_pcsx_rearmed 1c32c21dcc488889db7556bd0bd11c72219228f8d35d36bc30c640472af21fd3
"

IKEMEN_VERSION="v1.0.0"
IKEMEN_URL="https://github.com/ikemen-engine/Ikemen-GO/releases/download/$IKEMEN_VERSION/Ikemen_GO-$IKEMEN_VERSION-android.zip"
IKEMEN_SHA256="915ee0c8ce96e860e0aab71c505e9ddd2940fe3e1c339e3f30ab4bd69880d47c"

mkdir -p "$CACHE" "$LIBS"

check() {   # file sha256
	echo "$2  $1" | sha256sum -c --quiet -
}

# --- Libretro cores --------------------------------------------------------------------------
while read -r NAME MODULE SHA; do
	[ -z "$NAME" ] && continue
	SO="lib${NAME}_libretro_android.so"
	CACHED="$CACHE/${NAME}-$CORES_COMMIT.so"
	if [ ! -f "$CACHED" ] || ! check "$CACHED" "$SHA"; then
		echo "==> Downloading the $NAME core"
		rm -f "$CACHED"
		CORE_PATH="$MODULE/src/main/jniLibs/$ABI/$SO"
		if ! curl -fsSL --retry 3 -o "$CACHED.part" "https://raw.githubusercontent.com/$CORES_REPO/$CORES_COMMIT/$CORE_PATH"; then
			# raw.githubusercontent.com unreachable: fetch the blob with git.
			TMPGIT="$(mktemp -d)"
			git -C "$TMPGIT" init -q
			git -C "$TMPGIT" fetch -q --depth 1 --filter=blob:none "https://github.com/$CORES_REPO.git" "$CORES_COMMIT"
			git -C "$TMPGIT" show "FETCH_HEAD:$CORE_PATH" > "$CACHED.part"
			rm -rf "$TMPGIT"
		fi
		check "$CACHED.part" "$SHA"
		mv "$CACHED.part" "$CACHED"
	fi
	cp "$CACHED" "$LIBS/$SO"
	echo "    $NAME: $(du -h "$LIBS/$SO" | cut -f1)"
done <<< "$CORES"

# --- Ikemen GO ---------------------------------------------------------------------------------
IKZIP="$CACHE/Ikemen_GO-$IKEMEN_VERSION-android.zip"
if [ ! -f "$IKZIP" ] || ! check "$IKZIP" "$IKEMEN_SHA256"; then
	echo "==> Downloading Ikemen GO $IKEMEN_VERSION for Android"
	rm -f "$IKZIP"
	curl -fsSL --retry 3 -o "$IKZIP.part" "$IKEMEN_URL"
	check "$IKZIP.part" "$IKEMEN_SHA256"
	mv "$IKZIP.part" "$IKZIP"
fi
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
unzip -q "$IKZIP" -d "$WORK/zip"
cp "$WORK/zip/libmain.so" "$LIBS/"
IKEMEN_LIBS="$LIBS/libmain.so"
for lib in libSDL2 libxmp libavformat libavcodec libavutil libswscale libswresample libavfilter; do
	cp "$WORK/zip/lib/$lib.so" "$LIBS/"
	IKEMEN_LIBS="$IKEMEN_LIBS $LIBS/$lib.so"
done

# Engine files from the official APK's assets: the Lua scripts (external/), the common data
# and the fonts. Not its sample characters, stages, intro videos or default screenpack
# (data/ikemen1): a MUGEN game brings its own.
unzip -q "$WORK/zip/ikemen-go.apk" 'assets/external/*' 'assets/data/*' 'assets/font/*' -d "$WORK/apk"
rm -rf "$ASSETS"
mkdir -p "$ASSETS"
cp -R "$WORK/apk/assets/external" "$WORK/apk/assets/data" "$WORK/apk/assets/font" "$ASSETS/"
rm -rf "$ASSETS/data/ikemen1" "$ASSETS/external/icons/icon-src"
cp "$WORK/zip/LICENSES.txt" "$ASSETS/LICENSES.txt"
echo "$IKEMEN_VERSION" > "$ASSETS/VERSION"
(cd "$ASSETS" && find external data font -type f | LC_ALL=C sort > files.txt)
# shellcheck disable=SC2086 # the list is file paths without spaces
echo "    Ikemen GO: $(du -ch $IKEMEN_LIBS | tail -1 | cut -f1) of libraries, $(wc -l < "$ASSETS/files.txt") engine files ($(du -sh "$ASSETS" | cut -f1))"

echo "==> Engines ready in $LIBS and $ASSETS"
