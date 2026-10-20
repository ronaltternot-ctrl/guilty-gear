#!/usr/bin/env bash
# Downloads the engines of the other systems into the PPSSPP tree prepared by prepare.sh:
#   - FBNeo libretro core (arcade / Neo Geo games), Lemuroid's Android build, pinned commit.
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

FBNEO_REPO="Swordfish90/LemuroidCores"
FBNEO_COMMIT="fee2e824525daa22bcf318f96127fe43fa8a15ad"
FBNEO_PATH="lemuroid_core_fbneo/src/main/jniLibs/$ABI/libfbneo_libretro_android.so"
FBNEO_SHA256="88fd9f4aa9d8f235b58e64e78947ede0965c1e7ff85caf03dbcfcfe914b2e583"

IKEMEN_VERSION="v1.0.0"
IKEMEN_URL="https://github.com/ikemen-engine/Ikemen-GO/releases/download/$IKEMEN_VERSION/Ikemen_GO-$IKEMEN_VERSION-android.zip"
IKEMEN_SHA256="915ee0c8ce96e860e0aab71c505e9ddd2940fe3e1c339e3f30ab4bd69880d47c"

mkdir -p "$CACHE" "$LIBS"

check() {   # file sha256
	echo "$2  $1" | sha256sum -c --quiet -
}

# --- FBNeo -------------------------------------------------------------------------------------
FBNEO="$CACHE/libfbneo_libretro_android-$FBNEO_COMMIT.so"
if [ ! -f "$FBNEO" ] || ! check "$FBNEO" "$FBNEO_SHA256"; then
	echo "==> Downloading the FBNeo core"
	rm -f "$FBNEO"
	if ! curl -fsSL --retry 3 -o "$FBNEO.part" "https://raw.githubusercontent.com/$FBNEO_REPO/$FBNEO_COMMIT/$FBNEO_PATH"; then
		# raw.githubusercontent.com unreachable: fetch the blob with git.
		TMPGIT="$(mktemp -d)"
		git -C "$TMPGIT" init -q
		git -C "$TMPGIT" fetch -q --depth 1 --filter=blob:none "https://github.com/$FBNEO_REPO.git" "$FBNEO_COMMIT"
		git -C "$TMPGIT" show "FETCH_HEAD:$FBNEO_PATH" > "$FBNEO.part"
		rm -rf "$TMPGIT"
	fi
	check "$FBNEO.part" "$FBNEO_SHA256"
	mv "$FBNEO.part" "$FBNEO"
fi
cp "$FBNEO" "$LIBS/libfbneo_libretro_android.so"
echo "    FBNeo: $(du -h "$LIBS/libfbneo_libretro_android.so" | cut -f1)"

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
for lib in libSDL2 libxmp libavformat libavcodec libavutil libswscale libswresample libavfilter; do
	cp "$WORK/zip/lib/$lib.so" "$LIBS/"
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
echo "    Ikemen GO: $(du -sh "$LIBS" | cut -f1) of libraries, $(wc -l < "$ASSETS/files.txt") engine files ($(du -sh "$ASSETS" | cut -f1))"

echo "==> Engines ready in $LIBS and $ASSETS"
