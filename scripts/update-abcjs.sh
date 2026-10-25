#!/usr/bin/env bash
# Vendors abcjs (MIT) into app/src/main/assets/web/.
#
# We ship the unminified dist bundle so F-Droid reviewers can read what is in
# the APK. To upgrade: bump VERSION, run with the new tarball's sha256 printed
# by the failure message, review the upstream changelog, then update SHA256.
#
# Run: scripts/update-abcjs.sh
set -euo pipefail

VERSION="6.7.2"
SHA256="0cfad45dd6bc87626db9b7dd2b0d992b3a04967b73d6324cf3efd22cc0bf0868"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="$ROOT/app/src/main/assets/web"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

curl -fsSL "https://registry.npmjs.org/abcjs/-/abcjs-$VERSION.tgz" -o "$TMP/abcjs.tgz"
ACTUAL="$(sha256sum "$TMP/abcjs.tgz" | cut -d' ' -f1)"
if [[ "$ACTUAL" != "$SHA256" ]]; then
    echo "sha256 mismatch for abcjs $VERSION: got $ACTUAL" >&2
    exit 1
fi

tar -xzf "$TMP/abcjs.tgz" -C "$TMP"
cp "$TMP/package/dist/abcjs-basic.js" "$DEST/abcjs-basic.js"
cp "$TMP/package/LICENSE.md" "$DEST/abcjs-LICENSE.md"
echo "$VERSION" > "$DEST/abcjs-VERSION"
echo "Vendored abcjs $VERSION into $DEST"
