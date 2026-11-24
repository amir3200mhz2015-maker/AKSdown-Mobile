#!/usr/bin/env bash
# Downloads the official yt-dlp zipimport build (bundles yt-dlp-ejs) and verifies its SHA-256
# against the hash pinned in engine/versions.env AND the release's SHA2-256SUMS file.
set -euo pipefail
cd "$(dirname "$0")/.."
source engine/versions.env
out=app/src/main/res/raw/ytdlp
mkdir -p "$(dirname "$out")"
base="https://github.com/yt-dlp/yt-dlp/releases/download/${YTDLP_VERSION}"
curl -fsSL --retry 3 -o /tmp/yt-dlp "$base/yt-dlp"
curl -fsSL --retry 3 -o /tmp/SUMS "$base/SHA2-256SUMS"
actual=$(sha256sum /tmp/yt-dlp | cut -d' ' -f1)
published=$(grep -E ' yt-dlp$' /tmp/SUMS | cut -d' ' -f1)
[ "$actual" = "$YTDLP_SHA256" ] || { echo "yt-dlp hash mismatch with pinned hash: $actual"; exit 1; }
[ "$actual" = "$published" ] || { echo "yt-dlp hash mismatch with release SUMS"; exit 1; }
cp /tmp/yt-dlp "$out"
echo "yt-dlp $YTDLP_VERSION verified ($actual)"
