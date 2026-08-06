#!/bin/bash
# Assembles target/mousemaster.app around the native image, so the binary is relocatable and
# has one code identity: TCC keys the screen recording grant on that, and a bare rebuilt
# binary is a new identity that has to be granted again.
#
#   QT=~/Qt/6.8.2/macos ./macos/build-app.sh
#
# The qt frameworks come from the qt install and the qtjambi ones out of the native jar,
# which a native image cannot self-extract the way a jvm run does.
set -e
root=$(cd "$(dirname "$0")/.." && pwd)
QT=${QT:-$HOME/Qt/6.8.2/macos}
JAR=${JAR:-$HOME/.m2/repository/io/qtjambi/qtjambi-native-macos/6.8.2/qtjambi-native-macos-6.8.2.jar}
APP=$root/target/mousemaster.app
# Read rather than repeated, so the bundle cannot claim a version the build is not.
VERSION=$(sed -n 's/^version=//p' "$root/target/classes/application.properties")

[ -x "$root/target/mousemaster" ] || { echo "build the native image first"; exit 1; }

rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Frameworks" "$APP/Contents/Resources" \
         "$APP/Contents/PlugIns/platforms" "$APP/Contents/PlugIns/styles"

cp "$root/target/mousemaster" "$APP/Contents/MacOS/mousemaster"

# Only what the process actually loads, found with vmmap on a running instance.
for framework in QtCore QtDBus QtGui QtWidgets; do
    cp -R "$QT/lib/$framework.framework" "$APP/Contents/Frameworks/"
done
QTJAMBI=$root/target/qtjambi/lib
if [ ! -d "$QTJAMBI" ]; then
    QTJAMBI=$(mktemp -d)/lib
    mkdir -p "$(dirname "$QTJAMBI")"
    (cd "$(dirname "$QTJAMBI")" && unzip -q "$JAR" "lib/*")
fi
for framework in QtJambi QtJambiCore QtJambiGui QtJambiWidgets; do
    cp -R "$QTJAMBI/$framework.framework" "$APP/Contents/Frameworks/"
    # A jar cannot carry symlinks, and without them a framework is not a bundle that
    # codesign will accept - which leaves the whole app unsealed.
    inner=$APP/Contents/Frameworks/$framework.framework
    ln -sfn 6 "$inner/Versions/Current"
    ln -sfn "Versions/Current/$framework" "$inner/$framework"
    ln -sfn Versions/Current/Resources "$inner/Resources"
    ln -sfn Versions/Current/Headers "$inner/Headers"
    chmod +x "$inner/Versions/6/$framework"
done
cp "$QT/plugins/platforms/libqcocoa.dylib" "$APP/Contents/PlugIns/platforms/"
cp "$QT/plugins/styles/libqmacstyle.dylib" "$APP/Contents/PlugIns/styles/"

# Qt reads this relative to the executable, which is how it finds the cocoa platform plugin.
cat > "$APP/Contents/Resources/qt.conf" <<'CONF'
[Paths]
Plugins = PlugIns
CONF

cat > "$APP/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN"
        "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleExecutable</key><string>mousemaster</string>
    <key>CFBundleIdentifier</key><string>petoncle.mousemaster</string>
    <key>CFBundleName</key><string>mousemaster</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>CFBundleShortVersionString</key><string>$VERSION</string>
    <key>NSHighResolutionCapable</key><true/>
    <!-- An overlay, not an app with a dock icon or a menu bar. -->
    <key>LSUIElement</key><true/>
</dict>
</plist>
PLIST

# Nested code has to be signed before the bundle that contains it, and a signing failure is
# reported rather than aborting: the bundle still runs unsigned, it just has no stable identity.
set +e
for nested in "$APP"/Contents/Frameworks/*.framework "$APP"/Contents/PlugIns/*/*.dylib; do
    codesign --force --sign - "$nested" 2>&1 | sed 's/^/  /'
done
# Ad hoc signing identifies the bundle by its own hash, so a rebuilt binary is a new identity
# and has to be granted screen recording again. A Developer ID signature is what fixes that.
codesign --force --sign - "$APP" 2>&1 | sed 's/^/  /'
set -e
echo "built $APP ($(du -sh "$APP" | cut -f1))"
codesign -dvv "$APP" 2>&1 | grep -E "Identifier=|Signature=|CDHash=" | sed 's/^/  /'
