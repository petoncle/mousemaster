#!/bin/sh
# Builds libdriverkit.dylib, the Karabiner-DriverKit-VirtualHIDDevice client that mousemaster
# grabs the keyboard with on macos, into the architecture-specific resource directory where JNA
# finds it on the classpath. The sources are the unmodified karabiner-driverkit crate: driverkit.cpp
# plus the Karabiner headers it vendors. Needs the xcode command line tools. The compile flags
# follow the crate's own build.rs, dext branch (macos 11 and later).
set -e
VERSION=0.4.0
WORK=${1:-/tmp/driverkit-build}
CXX=${CXX:-clang++}
MACOS_SDK=${MACOS_SDK:-$(xcrun --sdk macosx --show-sdk-path)}
case $(uname -m) in
    arm64) JNA_ARCH=aarch64 ;;
    x86_64) JNA_ARCH=x86-64 ;;
    *) echo "unsupported macOS architecture: $(uname -m)" >&2; exit 1 ;;
esac
OUT=$(cd "$(dirname "$0")/.." && pwd)/src/main/resources/darwin-$JNA_ARCH
mkdir -p "$WORK" "$OUT"
cd "$WORK"
# crates.io answers 403 without a user agent.
if [ ! -f dk.crate ]; then
    curl -fsSL -A mousemaster-build -o dk.crate \
        "https://crates.io/api/v1/crates/karabiner-driverkit/$VERSION/download"
fi
rm -rf "karabiner-driverkit-$VERSION"
tar xzf dk.crate
cd "karabiner-driverkit-$VERSION"
# The client narrates its connection handshake on stdout, which lands in the middle of
# mousemaster's own output. Those lines are sent to a discarded stream rather than deleted, since
# some of them are the whole body of a braceless if. What list_keyboards prints is left alone.
sed -i '' '1i\
#include <ostream>\
static std::ostream mm_quiet(nullptr);
' c_src/driverkit.cpp
sed -i '' -E '/"(connected|closed|release called|connect_failed |error_occurred |virtual_hid_keyboard_ready |virtual_hid_pointing_ready |driver activated|driver connected|driver version matched)/ s/std::cout/mm_quiet/' c_src/driverkit.cpp
"$CXX" -std=c++23 -w -shared -fPIC -O2 -isysroot "$MACOS_SDK" \
    -I c_src/Karabiner-DriverKit-VirtualHIDDevice/include/pqrs/karabiner/driverkit \
    -I c_src/Karabiner-DriverKit-VirtualHIDDevice/vendor/vendor/include \
    -I c_src/Karabiner-DriverKit-VirtualHIDDevice/src/Daemon/vendor/include \
    -framework IOKit -framework CoreFoundation \
    -o "$OUT/libdriverkit.dylib" c_src/driverkit.cpp
echo "built $OUT/libdriverkit.dylib"
