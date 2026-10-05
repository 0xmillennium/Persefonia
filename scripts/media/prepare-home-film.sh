#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source_film="${1:-$project_root/.local/media/home/hero/hero-master-4k.mp4}"
output_dir="$project_root/frontend/src/media/home"
expected_sha="bc572ca5d9483d8ede6187c6fe842acbe1543940a31312e5c458fa8b9fe10a53"
actual_sha="$(sha256sum "$source_film" | cut -d ' ' -f 1)"
if [[ "$actual_sha" != "$expected_sha" ]]; then
  printf 'Source does not match the approved film. No derivatives written.\n' >&2
  exit 1
fi
mkdir -p "$output_dir"
# Dense keyframes make short, reversible seeks inexpensive. Both sizes use
# the complete, unchanged film. Strip audio; put the MP4 index before media.
for width in 720 1280; do
  ffmpeg -hide_banner -loglevel error -y -i "$source_film" \
    -map 0:v:0 -an -vf "scale=$width:-2:flags=lanczos" \
    -c:v libx264 -preset slow -crf 25 -pix_fmt yuv420p \
    -g 8 -keyint_min 8 -sc_threshold 0 -movflags +faststart \
    "$output_dir/coast-$width.mp4"
done
for frame in opening settled; do
  film_time=0
  if [[ "$frame" == settled ]]; then film_time=7.5; fi
  ffmpeg -hide_banner -loglevel error -y -ss "$film_time" -i "$source_film" \
    -frames:v 1 -vf 'scale=1600:-2:flags=lanczos' -c:v libwebp -quality 85 \
    "$output_dir/coast-$frame.webp"
done
[[ "$(sha256sum "$source_film" | cut -d ' ' -f 1)" == "$expected_sha" ]]
