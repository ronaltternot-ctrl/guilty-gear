#!/usr/bin/env bash
# Builds the Dreamcast emulator, Flycast, as a libretro core for Android (arm64), the way the
# libretro build bots do (Flycast's .gitlab-ci.yml, "core-defs-android"), but with OpenMP on:
# it's what Flycast's xBRZ texture upscaling needs. ArcadeActivity runs it through LibretroDroid
# ("dc" system). LemuroidCores has no Dreamcast core, so it's built here from a pinned release.
#
# Usage: scripts/build_flycast.sh [ppsspp-dir]      (default: build/ppsspp)
# Needs the Android NDK (29 preferred, installed with sdkmanager when missing) and CMake. The
# core is cached in build/engine-cache (ENGINE_CACHE).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-$HERE/build/ppsspp}"
CACHE="${ENGINE_CACHE:-$HERE/build/engine-cache}"
ABI="arm64-v8a"
LIBS="$TARGET/android/extlibs/$ABI"
OUT_NAME="libflycast_libretro_android.so"

REPO="https://github.com/flyinghead/flycast.git"
VERSION="v2.7"
COMMIT="5aa091fde632fb332c8d8c34e280d62dc951954c"
NDK_VERSION="29.0.14206865"

SCRIPT_ID="$(sha256sum "${BASH_SOURCE[0]}" | cut -c1-12)"
CACHED="$CACHE/flycast-$VERSION-$SCRIPT_ID.so"

mkdir -p "$CACHE" "$LIBS"

fail() {   # message [log file]
	echo "$1" >&2
	if [ -n "${2:-}" ] && [ -f "$2" ]; then
		tail -n 60 "$2" >&2
	fi
	if [ -n "${GITHUB_ACTIONS:-}" ]; then
		local body="$1"
		if [ -n "${2:-}" ] && [ -f "$2" ]; then
			body="$body
$(grep -iE "error|fatal|failed|not found" "$2" | tail -n 25)"
		fi
		body="${body//'%'/'%25'}"
		body="${body//$'\n'/'%0A'}"
		echo "::error title=Flycast::$body"
	fi
	exit 1
}

if [ ! -f "$CACHED" ]; then
	SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
	NDK="$SDK/ndk/$NDK_VERSION"
	if [ ! -f "$NDK/build/cmake/android.toolchain.cmake" ] && [ -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
		echo "==> Installing NDK $NDK_VERSION"
		yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" "ndk;$NDK_VERSION" > /dev/null || true
	fi
	if [ ! -f "$NDK/build/cmake/android.toolchain.cmake" ]; then
		NDK="${ANDROID_NDK_LATEST_HOME:-${ANDROID_NDK_HOME:-}}"
	fi
	[ -n "$NDK" ] && [ -f "$NDK/build/cmake/android.toolchain.cmake" ] || fail "Android NDK not found"
	CMAKE="$(ls -d "$SDK"/cmake/*/bin/cmake 2>/dev/null | sort -V | tail -n 1 || true)"
	[ -n "$CMAKE" ] || CMAKE="$(command -v cmake || true)"
	[ -n "$CMAKE" ] || fail "CMake not found"
	PATH="$(dirname "$CMAKE"):$PATH"
	echo "==> Building Flycast $VERSION (libretro, $ABI) with NDK $(basename "$NDK"), $("$CMAKE" --version | head -n 1)"

	WORK="$(mktemp -d)"
	trap 'rm -rf "$WORK"' EXIT
	git -C "$WORK" init -q src
	git -C "$WORK/src" fetch -q --depth 1 "$REPO" "$COMMIT" || fail "Could not download Flycast $VERSION"
	git -C "$WORK/src" checkout -q FETCH_HEAD
	git -C "$WORK/src" submodule update -q --init --recursive --depth 1 --jobs 8 || fail "Could not download Flycast's submodules"

	GENERATOR="Unix Makefiles"
	if command -v ninja > /dev/null; then
		GENERATOR="Ninja"
	fi
	"$CMAKE" -S "$WORK/src" -B "$WORK/build" -G "$GENERATOR" \
		-DLIBRETRO=ON \
		-DUSE_OPENMP=ON \
		-DCMAKE_BUILD_TYPE=Release \
		-DANDROID_PLATFORM=android-21 \
		-DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
		-DANDROID_STL=c++_static \
		-DANDROID_ABI="$ABI" \
		-DANDROID_ARM_MODE=arm \
		-DANDROID_WEAK_API_DEFS=ON \
		-DCMAKE_SHARED_LINKER_FLAGS="-Wl,-z,max-page-size=16384" \
		-DCMAKE_POLICY_VERSION_MINIMUM=3.5 > "$WORK/cmake.log" 2>&1 || fail "CMake could not configure Flycast" "$WORK/cmake.log"
	"$CMAKE" --build "$WORK/build" --target flycast_libretro -j "$(nproc)" > "$WORK/build.log" 2>&1 \
		|| fail "Flycast did not compile" "$WORK/build.log"
	SO="$(find "$WORK/build" -maxdepth 2 -name 'flycast_libretro*.so' | head -n 1)"
	[ -n "$SO" ] || fail "flycast_libretro.so was not built" "$WORK/build.log"
	"$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-unneeded -o "$CACHED.part" "$SO"
	mv "$CACHED.part" "$CACHED"
fi

cp "$CACHED" "$LIBS/$OUT_NAME"
echo "    Flycast: $(du -h "$LIBS/$OUT_NAME" | cut -f1)"
