#!/usr/bin/env bash
# Builds the PlayStation 2 emulator: the native core of PSX2 (github.com/izzy2lost/PSX2, GPL 3.0),
# an Android port of PCSX2 with arm64 recompilers, at a pinned commit plus the launcher's changes
# (patches/psx2). Ps2Activity runs it through PSX2's own Java bridge (com.izzy2lost.psx2.NativeApp,
# rewritten in overlay/) and SDL's controller classes, which are copied from PSX2 as they are.
#
# What lands in the PPSSPP tree prepared by prepare.sh:
#   - android/extlibs/arm64-v8a/libemucore.so (and any other library it needs)
#   - assets/ps2/resources: PCSX2's game database, patches, shaders and fonts
#   - android/src/com/izzy2lost/psx2/{SDLControllerManager,HIDDevice*}.java
#
# Usage: scripts/build_ps2.sh [ppsspp-dir]      (default: build/ppsspp)
# Needs the Android NDK and CMake. The result is cached in build/ps2-cache (PS2_CACHE).
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-$HERE/build/ppsspp}"
CACHE="${PS2_CACHE:-$HERE/build/ps2-cache}"
ABI="arm64-v8a"
LIBS="$TARGET/android/extlibs/$ABI"

REPO="https://github.com/izzy2lost/PSX2.git"
VERSION="1.3.2"
COMMIT="ab0562659d9d2e807528aaf3a7c81bb7ac399f5e"
NDK_VERSION="29.0.14206865"
JAVA_FILES="SDLControllerManager.java HIDDevice.java HIDDeviceManager.java HIDDeviceUSB.java HIDDeviceBLESteamController.java"

SCRIPT_ID="$( (cat "${BASH_SOURCE[0]}"; cat "$HERE"/patches/psx2/*.patch 2>/dev/null) | sha256sum | cut -c1-12)"
CACHED="$CACHE/psx2-$VERSION-$SCRIPT_ID.tar"

mkdir -p "$CACHE" "$LIBS"

fail() {   # message [log file]
	echo "$1" >&2
	if [ -n "${2:-}" ] && [ -f "$2" ]; then
		tail -n 80 "$2" >&2
	fi
	if [ -n "${GITHUB_ACTIONS:-}" ]; then
		local body="$1"
		if [ -n "${2:-}" ] && [ -f "$2" ]; then
			body="$body
$(grep -iE "error|fatal|failed|not found|undefined" "$2" | tail -n 30)"
		fi
		body="${body//'%'/'%25'}"
		body="${body//$'\n'/'%0A'}"
		echo "::error title=PS2::$body"
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
	echo "==> Building PSX2 $VERSION ($ABI) with NDK $(basename "$NDK"), $("$CMAKE" --version | head -n 1)"

	WORK="$(mktemp -d)"
	trap 'rm -rf "$WORK"' EXIT
	git -C "$WORK" init -q src
	git -C "$WORK/src" fetch -q --depth 1 "$REPO" "$COMMIT" || fail "Could not download PSX2 $VERSION"
	git -C "$WORK/src" checkout -q FETCH_HEAD
	for p in "$HERE"/patches/psx2/*.patch; do
		[ -f "$p" ] || continue
		git -C "$WORK/src" apply --whitespace=nowarn "$p" || fail "$(basename "$p") doesn't apply to PSX2 $VERSION"
	done

	GENERATOR="Unix Makefiles"
	if command -v ninja > /dev/null; then
		GENERATOR="Ninja"
	fi
	LAUNCHER=()
	if command -v ccache > /dev/null; then
		LAUNCHER=(-DCMAKE_C_COMPILER_LAUNCHER=ccache -DCMAKE_CXX_COMPILER_LAUNCHER=ccache)
	fi
	# The same arguments PSX2's Gradle build gives CMake (app/build.gradle.kts).
	"$CMAKE" -S "$WORK/src/app/src/main/cpp" -B "$WORK/build" -G "$GENERATOR" \
		-DANDROID=true \
		-DCMAKE_BUILD_TYPE=Release \
		-DANDROID_STL=c++_static \
		-DPCSX2_PROFILER=OFF \
		-DANDROID_PLATFORM=android-26 \
		-DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
		-DANDROID_ABI="$ABI" \
		"${LAUNCHER[@]}" > "$WORK/cmake.log" 2>&1 || fail "CMake could not configure PSX2" "$WORK/cmake.log"
	"$CMAKE" --build "$WORK/build" --target PCSX2 -j "$(nproc)" > "$WORK/build.log" 2>&1 \
		|| fail "PSX2 did not compile" "$WORK/build.log"
	SO="$(find "$WORK/build" -name 'libemucore.so' | head -n 1)"
	[ -n "$SO" ] || fail "libemucore.so was not built" "$WORK/build.log"

	BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
	PACK="$WORK/pack"
	mkdir -p "$PACK/libs" "$PACK/java"
	"$BIN/llvm-strip" --strip-unneeded -o "$PACK/libs/libemucore.so" "$SO"
	# Other libraries of the build that libemucore.so loads (none when everything is static).
	for need in $("$BIN/llvm-readelf" -d "$SO" | sed -n 's/.*Shared library: \[\(.*\)\]/\1/p'); do
		OTHER="$(find "$WORK/build" -name "$need" | head -n 1)"
		if [ -n "$OTHER" ]; then
			"$BIN/llvm-strip" --strip-unneeded -o "$PACK/libs/$need" "$OTHER"
		fi
	done
	cp -r "$WORK/src/app/src/main/assets/resources" "$PACK/resources"
	for f in $JAVA_FILES; do
		cp "$WORK/src/app/src/main/java/com/izzy2lost/psx2/$f" "$PACK/java/" || fail "PSX2 has no $f"
	done
	cp "$WORK/src/LICENSE" "$PACK/LICENSE-PSX2.txt" 2>/dev/null || true
	tar -cf "$CACHED.part" -C "$PACK" .
	mv "$CACHED.part" "$CACHED"
	rm -f "$CACHE"/psx2-*.tar.part
	find "$CACHE" -name 'psx2-*.tar' ! -name "$(basename "$CACHED")" -delete
fi

UNPACK="$(mktemp -d)"
tar -xf "$CACHED" -C "$UNPACK"
cp "$UNPACK"/libs/*.so "$LIBS/"
rm -rf "$TARGET/assets/ps2"
mkdir -p "$TARGET/assets/ps2"
cp -r "$UNPACK/resources" "$TARGET/assets/ps2/resources"
# A stamp so Ps2Activity copies the resources again only when they change.
echo "$VERSION-$SCRIPT_ID" > "$TARGET/assets/ps2/version.txt"
JAVA_DIR="$TARGET/android/src/com/izzy2lost/psx2"
mkdir -p "$JAVA_DIR"
cp "$UNPACK"/java/*.java "$JAVA_DIR/"
rm -rf "$UNPACK"
echo "    PSX2: $(ls "$LIBS" | grep -c . ) libraries in extlibs, libemucore.so $(du -h "$LIBS/libemucore.so" | cut -f1), resources $(du -sh "$TARGET/assets/ps2/resources" | cut -f1)"
