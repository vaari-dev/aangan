#!/bin/bash
# Builds Aangan without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Usage: ./build.sh [install|bundle]     (install: adb install -r; set ANDROID_SERIAL when several TVs are connected)
#                                        (bundle: also build/aangan.aab for Google Play, via bundletool)
#
# Builds are signed with a local development key, created on first run. For a release, use your own:
#   AANGAN_KEYSTORE=~/keys/aangan-release.jks AANGAN_KEY_PASS=... ./build.sh
set -euo pipefail
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-$HOME/.local/share/android}"
BT="$SDK/build-tools/34.0.0"
JAR="$SDK/platforms/android-34/android.jar"
export JAVA_HOME="${JAVA_HOME:-$(ls -d "$HOME"/.local/share/jdk/jdk-17*/Contents/Home | head -1)}"
export PATH="$JAVA_HOME/bin:$PATH"
KEYSTORE="${AANGAN_KEYSTORE:-keystore/dev.jks}"
KEY_PASS="${AANGAN_KEY_PASS:-development}"
BUNDLETOOL="$SDK/bundletool.jar"
BUNDLETOOL_VERSION=1.18.3
BUNDLETOOL_SHA256=a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29
LINK=(-I "$JAR" --manifest AndroidManifest.xml
    --min-sdk-version 28 --target-sdk-version 34
    --version-code 8 --version-name 1.2
    -A assets)

rm -rf build && mkdir -p build/gen build/classes build/dex

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link "${LINK[@]}" --java build/gen -o build/unsigned.apk build/res.zip

javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 \
    -bootclasspath "$JAR" -classpath "$BT/core-lambda-stubs.jar" -d build/classes \
    $(find src build/gen -name '*.java')

"$BT/d8" --release --min-api 28 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
(cd build/dex && zip -q ../unsigned.apk classes.dex)

"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk

# The signing key must stay the same for future updates to install over this one.
if [ ! -f "$KEYSTORE" ]; then
    mkdir -p "$(dirname "$KEYSTORE")"
    keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KEY_PASS" -keypass "$KEY_PASS" \
        -alias aangan -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Aangan" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass "pass:$KEY_PASS" --out build/aangan.apk build/aligned.apk
echo "built build/aangan.apk ($(du -h build/aangan.apk | cut -f1))"

if [ "${1:-}" = bundle ]; then
    # bundletool, fetched once and checked against the release's published digest.
    if [ ! -f "$BUNDLETOOL" ]; then
        curl -fsSL -o "$BUNDLETOOL.part" \
            "https://github.com/google/bundletool/releases/download/$BUNDLETOOL_VERSION/bundletool-all-$BUNDLETOOL_VERSION.jar"
        echo "$BUNDLETOOL_SHA256  $BUNDLETOOL.part" | shasum -a 256 -c - >/dev/null
        mv "$BUNDLETOOL.part" "$BUNDLETOOL"
    fi

    # Same resources in protobuf form, laid out as a base module: manifest/, dex/, res/, assets/, resources.pb.
    "$BT/aapt2" link "${LINK[@]}" --proto-format -o build/proto.apk build/res.zip
    mkdir -p build/base/manifest build/base/dex
    (cd build/base && unzip -q ../proto.apk && mv AndroidManifest.xml manifest/)
    cp build/dex/classes.dex build/base/dex/
    (cd build/base && zip -qr ../base.zip .)

    java -jar "$BUNDLETOOL" build-bundle --modules=build/base.zip --output=build/unsigned.aab
    # Signed with the APK's key: the upload key, and the Play app-signing key too.
    export KEY_PASS
    jarsigner -keystore "$KEYSTORE" -storepass:env KEY_PASS -digestalg SHA-256 -sigalg SHA256withRSA \
        -signedjar build/aangan.aab build/unsigned.aab aangan >/dev/null
    echo "built build/aangan.aab ($(du -h build/aangan.aab | cut -f1))"
fi

if [ "${1:-}" = install ]; then
    "$SDK/platform-tools/adb" install -r build/aangan.apk
fi
