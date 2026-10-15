#!/usr/bin/env bash
# Downloads PPSSPP (the version pinned in PPSSPP_VERSION), applies this project's
# patches and copies the launcher files on top. Used by GitHub Actions and for local builds.
#
# Usage: scripts/prepare.sh [target-dir]      (default: build/ppsspp)
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${1:-$HERE/build/ppsspp}"
TAG="$(tr -d ' \r\n' < "$HERE/PPSSPP_VERSION")"
REPO="https://github.com/hrydgard/ppsspp.git"

if [ ! -d "$TARGET/.git" ]; then
	echo "==> Downloading PPSSPP $TAG"
	git clone --depth 1 --branch "$TAG" "$REPO" "$TARGET"
	echo "==> Downloading submodules"
	if ! git -C "$TARGET" submodule update --init --recursive --depth 1 --jobs 8; then
		echo "Shallow submodule fetch failed, retrying with full history"
		git -C "$TARGET" submodule update --init --recursive --jobs 8
	fi
fi

cd "$TARGET"
echo "==> Resetting PPSSPP sources to $TAG"
git reset --hard -q
git clean -fdq

echo "==> Applying patches"
for patch in "$HERE"/patches/*.patch; do
	echo "    $(basename "$patch")"
	git apply --whitespace=nowarn "$patch"
done

echo "==> Copying launcher files"
cp -R "$HERE/overlay/." "$TARGET/"

echo "==> Ready: $TARGET"
