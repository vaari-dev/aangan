#!/bin/bash
# Builds Aangan without Gradle: aapt2 -> javac -> d8 -> zipalign -> apksigner.
# Usage: ./build.sh [install]     (install: adb install -r; set ANDROID_SERIAL when several TVs are connected)
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

rm -rf build && mkdir -p build/gen build/classes build/dex

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -I "$JAR" --manifest AndroidManifest.xml \
    --min-sdk-version 28 --target-sdk-version 34 \
    --version-code 7 --version-name 1.1 \
    -A assets \
    --java build/gen -o build/unsigned.apk build/res.zip

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

if [ "${1:-}" = install ]; then
    "$SDK/platform-tools/adb" install -r build/aangan.apk
fi
