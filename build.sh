#!/bin/bash
# Builds Aangan without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Usage: ./build.sh [install|bundle]     (install: adb install -r; set ANDROID_SERIAL when several TVs are connected)
#                                        (bundle: also build/aangan.aab for Google Play, via bundletool)
#
# Builds are signed with a local development key, created on first run. For a release, use your own:
#   AANGAN_KEYSTORE=~/keys/aangan-release.jks AANGAN_KEY_PASS=... ./build.sh
set -euo pipefail
cd "$(dirname "$0")"
case "${1:-}" in
"" | install | bundle) ;;
*) echo "build.sh: unknown command '$1'; use install or bundle." >&2; exit 1 ;;
esac

# The release version. Raising VERSION_NAME on main publishes it (see .github/workflows/ci.yml);
# VERSION_CODE must go up with every release, and CHANGELOG.md needs a section for the new name.
VERSION_CODE=9
VERSION_NAME=1.3
# The Android versions built for: Android 9 and up, targeting Android 16's behaviour, compiled
# against the newest SDK. (Targeting 17 would need a local-network permission prompt for the phone page.)
MIN_SDK=28
TARGET_SDK=36
COMPILE_SDK=37.2
BUILD_TOOLS=37.0.0

# ANDROID_HOME and JAVA_HOME are honoured when set (CI sets both); otherwise the usual local installs.
SDK="${ANDROID_HOME:-$HOME/.local/share/android}"
BT="$SDK/build-tools/$BUILD_TOOLS"
JAR="$SDK/platforms/android-$COMPILE_SDK/android.jar"
if [ -z "${JAVA_HOME:-}" ]; then
    # The first JDK 17 under ~/.local/share/jdk, laid out for macOS (Contents/Home) or Linux.
    for candidate in "$HOME"/.local/share/jdk/jdk-17*/Contents/Home "$HOME"/.local/share/jdk/jdk-17*; do
        if [ -x "$candidate/bin/javac" ]; then
            JAVA_HOME="$candidate"
            break
        fi
    done
fi
JAVA_MAJOR="$("${JAVA_HOME:-/nonexistent}/bin/javac" -version 2>&1 | sed -nE 's/^javac ([0-9]+).*/\1/p' || true)"
if [ -z "$JAVA_MAJOR" ] || [ "$JAVA_MAJOR" -lt 17 ]; then
    echo "build.sh: no JDK 17 or later found. Set JAVA_HOME to one (it has bin/javac)." >&2
    echo "          JAVA_HOME is currently '${JAVA_HOME:-}'." >&2
    exit 1
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
KEYSTORE="${AANGAN_KEYSTORE:-keystore/dev.jks}"
KEY_PASS="${AANGAN_KEY_PASS:-development}"
BUNDLETOOL="$SDK/bundletool.jar"
BUNDLETOOL_VERSION=1.18.3
BUNDLETOOL_SHA256=a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29
LINK=(-I "$JAR" --manifest AndroidManifest.xml
    --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK"
    --version-code "$VERSION_CODE" --version-name "$VERSION_NAME"
    -A assets)

rm -rf build && mkdir -p build/gen build/classes build/dex

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link "${LINK[@]}" --java build/gen -o build/unsigned.apk build/res.zip

javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 \
    -bootclasspath "$JAR" -classpath "$BT/core-lambda-stubs.jar" -d build/classes \
    $(find src build/gen -name '*.java')

"$BT/d8" --release --min-api "$MIN_SDK" --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
(cd build/dex && zip -q ../unsigned.apk classes.dex)

"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk

# The signing key must stay the same for future updates to install over this one. The password
# reaches each tool through the environment, never the command line, where other processes could see it.
export KEY_PASS
if [ ! -f "$KEYSTORE" ]; then
    mkdir -p "$(dirname "$KEYSTORE")"
    keytool -genkeypair -keystore "$KEYSTORE" -storepass:env KEY_PASS -keypass:env KEY_PASS \
        -alias aangan -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Aangan" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass env:KEY_PASS --out build/aangan.apk build/aligned.apk
echo "built build/aangan.apk ($(du -h build/aangan.apk | cut -f1))"

case "${1:-}" in
bundle)
    # bundletool, fetched once, and checked against the release's published digest every time it runs.
    if [ ! -f "$BUNDLETOOL" ]; then
        curl -fsSL -o "$BUNDLETOOL.part" \
            "https://github.com/google/bundletool/releases/download/$BUNDLETOOL_VERSION/bundletool-all-$BUNDLETOOL_VERSION.jar"
        mv "$BUNDLETOOL.part" "$BUNDLETOOL"
    fi
    if ! echo "$BUNDLETOOL_SHA256  $BUNDLETOOL" | shasum -a 256 -c - >/dev/null 2>&1; then
        echo "build.sh: $BUNDLETOOL doesn't match bundletool $BUNDLETOOL_VERSION's digest; delete it to fetch it again." >&2
        exit 1
    fi

    # Same resources in protobuf form, laid out as a base module: manifest/, dex/, res/, assets/, resources.pb.
    "$BT/aapt2" link "${LINK[@]}" --proto-format -o build/proto.apk build/res.zip
    mkdir -p build/base/manifest build/base/dex
    (cd build/base && unzip -q ../proto.apk && mv AndroidManifest.xml manifest/)
    cp build/dex/classes.dex build/base/dex/
    (cd build/base && zip -qr ../base.zip .)

    java -jar "$BUNDLETOOL" build-bundle --modules=build/base.zip --output=build/unsigned.aab
    # Signed with the APK's key: the upload key, and the Play app-signing key too.
    jarsigner -keystore "$KEYSTORE" -storepass:env KEY_PASS -digestalg SHA-256 -sigalg SHA256withRSA \
        -signedjar build/aangan.aab build/unsigned.aab aangan >/dev/null
    echo "built build/aangan.aab ($(du -h build/aangan.aab | cut -f1))"
    ;;
install)
    "$SDK/platform-tools/adb" install -r build/aangan.apk
    ;;
esac
