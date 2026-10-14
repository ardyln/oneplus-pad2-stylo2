#!/usr/bin/env bash
# Builds the app with Gradle and packs the module zip.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=$ROOT/build
export JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
export ANDROID_HOME=${ANDROID_HOME:-/usr/lib/android-sdk}
# Personal signing key, kept out of the repo. Created on first build if missing.
# Keep a copy: updates must be signed with the same key to install over the old app.
export KEYSTORE=${KEYSTORE:-$HOME/tools/stylo2-release.jks}
export KS_PASS=${KS_PASS:?set KS_PASS to the keystore password}
VERSION=$(grep '^version=' "$ROOT/module/module.prop" | cut -d= -f2)

if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" -alias stylo2 \
        -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=stylo2 personal" >/dev/null 2>&1
fi
(cd "$ROOT" && ./gradlew -q --console=plain :app:assembleRelease)

rm -rf "$OUT"; mkdir -p "$OUT"
cp "$ROOT/app/build/outputs/apk/release/app-release.apk" "$OUT/Stylo2.apk"
STAGE=$OUT/stage; cp -r "$ROOT/module" "$STAGE"
cp "$OUT/Stylo2.apk" "$STAGE/Stylo2.apk"
find "$STAGE" -name '*.sh' -exec sed -i 's/\r$//' {} + -exec chmod 0755 {} +
chmod 0755 "$STAGE/META-INF/com/google/android/update-binary"
ZIP=$OUT/OnePlus_Pad2_Stylo2-v$VERSION.zip
rm -f "$ZIP"; (cd "$STAGE" && zip -q -r -X "$ZIP" .)
echo "built $ZIP"
