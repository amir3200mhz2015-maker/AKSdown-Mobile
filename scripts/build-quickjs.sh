#!/usr/bin/env bash
# Cross-compiles the QuickJS-NG command line engine (qjs) with the Android NDK.
# yt-dlp uses it to solve YouTube's JavaScript challenges fully on-device.
# Output is named libqjs.so so Android installs it into nativeLibraryDir (executable location).
set -euo pipefail
cd "$(dirname "$0")/.."
source engine/versions.env
: "${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME}"
ABIS="${1:-arm64-v8a}"
src=build/quickjs-src
if [ ! -d "$src" ]; then
  git clone -q --branch "$QJS_TAG" --depth 1 https://github.com/quickjs-ng/quickjs.git "$src"
fi
[ "$(git -C "$src" rev-parse HEAD)" = "$QJS_COMMIT" ] || { echo "QuickJS commit mismatch"; exit 1; }
IFS=',' read -ra list <<< "$ABIS"
for abi in "${list[@]}"; do
  bdir=build/quickjs-$abi
  cmake -S "$src" -B "$bdir" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$abi" -DANDROID_PLATFORM=android-26 \
    -DCMAKE_BUILD_TYPE=Release -DQJS_ENABLE_INSTALL=OFF -DBUILD_SHARED_LIBS=OFF
  cmake --build "$bdir" --target qjs_exe -j"$(nproc)"
  mkdir -p "app/src/main/jniLibs/$abi"
  cp "$bdir/qjs" "app/src/main/jniLibs/$abi/libqjs.so"
  "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" "app/src/main/jniLibs/$abi/libqjs.so"
  file "app/src/main/jniLibs/$abi/libqjs.so" || true
done
