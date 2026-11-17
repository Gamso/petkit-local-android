#!/bin/sh
# Generates the live-video test pattern the mock streams (src/main/resources/media/live.mp4):
# 6 s of ffmpeg's `testsrc2` at 320x240 and 16 fps, H.264 Constrained Baseline, one keyframe every
# 8 frames, written as a fragmented MP4 with one fragment per keyframe (init segment, then twelve
# 0.5 s moof+mdat pairs). The mock splits it into those boxes and loops them with continuous
# timestamps (media/LivePattern.kt). Run it once, from anywhere; it needs only Docker:
#
#   mock-server/tools/make_live_pattern.sh
#
# (Git Bash on Windows: MSYS_NO_PATHCONV=1 mock-server/tools/make_live_pattern.sh)
set -eu
cd "$(dirname "$0")/../src/main/resources/media"
# Docker Desktop on Windows wants a Windows path; `pwd -W` gives one in Git Bash.
out_dir="$(pwd -W 2>/dev/null || pwd)"
docker run --rm -v "$out_dir:/out" alpine:3.22 sh -c '
  apk add --no-cache ffmpeg >/dev/null &&
  ffmpeg -hide_banner -loglevel error -y \
    -f lavfi -i "testsrc2=size=320x240:rate=16" -t 6 \
    -c:v libx264 -profile:v baseline -level:v 3.0 -pix_fmt yuv420p \
    -g 8 -keyint_min 8 -sc_threshold 0 -bf 0 \
    -b:v 160k -maxrate 200k -bufsize 200k \
    -fflags +bitexact -flags:v +bitexact -map_metadata -1 \
    -movflags +frag_keyframe+empty_moov+default_base_moof+skip_trailer \
    -f mp4 /out/live.mp4 &&
  ffmpeg -version | head -1'
