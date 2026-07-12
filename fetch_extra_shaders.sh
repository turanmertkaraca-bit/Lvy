#!/usr/bin/env bash
#
# AnimeStream: download optional upscaling shaders that aren't bundled
# by default. Run this from your build machine (Project IDX terminal).
#
# Usage:
#   cd mpv-android
#   bash fetch_extra_shaders.sh
#
# After running, rebuild the APK so the new shaders are packaged into
# assets/shaders/.

set -e
DEST="app/src/main/assets/shaders"
mkdir -p "$DEST"

echo "=== Downloading FSRCNNX x2 8-0-4-1 ==="
# igv's repo is the canonical source for FSRCNNX
curl -fsSL -o "$DEST/FSRCNNX_x2_8-0-4-1.glsl" \
  "https://raw.githubusercontent.com/igv/FSRCNN-TensorFlow/master/FSRCNNX_x2_8-0-4-1.glsl" \
  && echo "OK: FSRCNNX_x2_8-0-4-1.glsl" \
  || echo "SKIP: FSRCNNX (repo may have moved — check https://github.com/igv/FSRCNN-TensorFlow)"

echo "=== Downloading FSRCNNX x2 16-0-4-1 (heavier, higher quality) ==="
curl -fsSL -o "$DEST/FSRCNNX_x2_16-0-4-1.glsl" \
  "https://raw.githubusercontent.com/igv/FSRCNN-TensorFlow/master/FSRCNNX_x2_16-0-4-1.glsl" \
  && echo "OK: FSRCNNX_x2_16-0-4-1.glsl" \
  || echo "SKIP: FSRCNNX 16 variant"

echo "=== Downloading ArtCNN C4F32 (TianZerL's Anime4KCPP) ==="
curl -fsSL -o "$DEST/ArtCNN_C4F32_Depth_toToSpace.glsl" \
  "https://raw.githubusercontent.com/TianZerL/Anime4KCPP/master/shaders/glsl/ArtCNN/ArtCNN_C4F32_Depth_toToSpace.glsl" \
  && echo "OK: ArtCNN_C4F32_Depth_toToSpace.glsl" \
  || echo "SKIP: ArtCNN (path may differ — check https://github.com/TianZerL/Anime4KCPP)"

echo "=== Downloading KrigBilateral (chroma upscaler) ==="
curl -fsSL -o "$DEST/KrigBilateral.hook" \
  "https://raw.githubusercontent.com/bjin/mpv-prescalers/master/KrigBilateral.hook" \
  && echo "OK: KrigBilateral.hook" \
  || echo "SKIP: KrigBilateral"

echo "=== Downloading SSimDownscaler (high-quality downscaler) ==="
curl -fsSL -o "$DEST/SSimDownscaler.hook" \
  "https://raw.githubusercontent.com/bjin/mpv-prescalers/master/SSimDownscaler.hook" \
  && echo "OK: SSimDownscaler.hook" \
  || echo "SKIP: SSimDownscaler"

echo "=== Downloading adaptive-sharpen ==="
curl -fsSL -o "$DEST/adaptive-sharpen.hook" \
  "https://raw.githubusercontent.com/bjin/mpv-prescalers/master/adaptive-sharpen.hook" \
  && echo "OK: adaptive-sharpen.hook" \
  || echo "SKIP: adaptive-sharpen"

echo ""
echo "=== Final shader inventory ==="
ls -la "$DEST"
echo ""
echo "Done. Now rebuild the APK with ./gradlew assembleDefaultDebug"
