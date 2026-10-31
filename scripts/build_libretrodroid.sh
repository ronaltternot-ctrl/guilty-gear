#!/usr/bin/env bash
# Builds LibretroDroid's native library (liblibretrodroid.so) from the sources of the version the
# app uses (0.14.0, the same as the JitPack AAR), with this project's changes in
# patches/libretrodroid/: custom shader chains, so the arcade and PS1 screen can use the same
# visual effects as the PSP games (overlay/assets/retro_shaders/). The library goes to
# android/extlibs/<abi>, and patches/0010 makes the APK carry it instead of the AAR's copy.
#
# Usage: scripts/build_libretrodroid.sh [ppsspp-dir]      (default: build/ppsspp)
# Needs the Android NDK (ANDROID_NDK_LATEST_HOME, ANDROID_NDK_HOME, or the newest one in
# $ANDROID_HOME/ndk) and CMake. The result is cached in build/engine-cache (ENGINE_CACHE).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-$HERE/build/ppsspp}"
CACHE="${ENGINE_CACHE:-$HERE/build/engine-cache}"
ABI="arm64-v8a"
LIBS="$TARGET/android/extlibs/$ABI"

REPO="https://github.com/Swordfish90/LibretroDroid.git"
VERSION="0.14.0"
COMMIT="8835c3098514390a271e36983957f7bb5f40abf1"

PATCH_ID="$(cat "$HERE"/patches/libretrodroid/*.patch "${BASH_SOURCE[0]}" | sha256sum | cut -c1-16)"
CACHED="$CACHE/liblibretrodroid-$VERSION-$PATCH_ID.so"

mkdir -p "$CACHE" "$LIBS"

if [ ! -f "$CACHED" ]; then
	NDK="${ANDROID_NDK_LATEST_HOME:-${ANDROID_NDK_HOME:-}}"
	if [ -z "$NDK" ] || [ ! -d "$NDK" ]; then
		NDK="$(ls -d "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"/ndk/* 2>/dev/null | sort -V | tail -n 1 || true)"
	fi
	if [ -z "$NDK" ] || [ ! -f "$NDK/build/cmake/android.toolchain.cmake" ]; then
		echo "Android NDK not found (set ANDROID_NDK_HOME)" >&2
		exit 1
	fi
	echo "==> Building LibretroDroid $VERSION with the launcher's changes (NDK: $NDK)"

	WORK="$(mktemp -d)"
	trap 'rm -rf "$WORK"' EXIT
	git -C "$WORK" init -q src
	git -C "$WORK/src" fetch -q --depth 1 "$REPO" "$COMMIT"
	git -C "$WORK/src" checkout -q FETCH_HEAD
	# oboe and libretro-common at the commits this version pins.
	git -C "$WORK/src" submodule update -q --init --depth 1 --jobs 2
	for patch in "$HERE"/patches/libretrodroid/*.patch; do
		echo "    $(basename "$patch")"
		git -C "$WORK/src" apply "$patch"
	done

	GENERATOR="Unix Makefiles"
	if command -v ninja > /dev/null; then
		GENERATOR="Ninja"
	fi
	# Same settings as LibretroDroid's own build (libretrodroid/build.gradle).
	cmake -S "$WORK/src/libretrodroid/src/main/cpp" -B "$WORK/build" -G "$GENERATOR" \
		-DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
		-DANDROID_ABI="$ABI" \
		-DANDROID_PLATFORM=android-21 \
		-DANDROID_STL=c++_static \
		-DCMAKE_BUILD_TYPE=Release > "$WORK/cmake.log"
	cmake --build "$WORK/build" --target libretrodroid -j "$(nproc)" > "$WORK/build.log" 2>&1 || {
		tail -n 60 "$WORK/build.log" >&2
		exit 1
	}
	SO="$(find "$WORK/build" -name liblibretrodroid.so | head -n 1)"
	if [ -z "$SO" ]; then
		echo "liblibretrodroid.so was not built" >&2
		exit 1
	fi
	"$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded -o "$CACHED.part" "$SO"
	mv "$CACHED.part" "$CACHED"
fi

cp "$CACHED" "$LIBS/liblibretrodroid.so"
echo "    LibretroDroid: $(du -h "$LIBS/liblibretrodroid.so" | cut -f1), sha256 $(sha256sum "$LIBS/liblibretrodroid.so" | cut -c1-16)"
