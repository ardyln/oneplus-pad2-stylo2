#!/usr/bin/env bash
# Builds Stylo2.apk (javac -> d8 -> aapt2 -> zipalign -> apksigner) and packs the module zip.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=$ROOT/build
SDK=/usr/lib/android-sdk
JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
ANDROID_JAR=$(ls -d $SDK/platforms/android-3*/android.jar | sort -V | tail -n1)
BT=$(ls -d $SDK/build-tools/3* | sort -V | tail -n1)
# Personal signing key, kept out of the repo. Created on first build if missing.
# Keep a copy: updates must be signed with the same key to install over the old app.
KEYSTORE=${KEYSTORE:-$HOME/tools/stylo2-release.jks}
KS_PASS=${KS_PASS:?set KS_PASS to the keystore password}
VERSION=$(grep '^version=' $ROOT/module/module.prop | cut -d= -f2)

rm -rf "$OUT"; mkdir -p "$OUT"/{gen,classes,dex}

aapt2 compile --dir "$ROOT/app/res" -o "$OUT/res.zip"
aapt2 link -o "$OUT/unsigned.apk" -I "$ANDROID_JAR" --manifest "$ROOT/app/AndroidManifest.xml" \
    --java "$OUT/gen" "$OUT/res.zip"
$JAVA_HOME/bin/javac --release 11 -Xlint:-options -Xlint:-deprecation -classpath "$ANDROID_JAR" \
    -d "$OUT/classes" $(find "$ROOT/app/src" "$OUT/gen" -name '*.java')
$BT/d8 --release --min-api 31 --lib "$ANDROID_JAR" --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')
(cd "$OUT/dex" && zip -q -j "$OUT/unsigned.apk" classes.dex)
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -keystore "$KEYSTORE" -storepass "$KS_PASS" -keypass "$KS_PASS" -alias stylo2 \
        -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=stylo2 personal" >/dev/null 2>&1
fi
apksigner sign --ks "$KEYSTORE" --ks-pass env:KS_PASS --key-pass env:KS_PASS \
    --out "$OUT/Stylo2.apk" "$OUT/aligned.apk" 2>/dev/null
apksigner verify "$OUT/Stylo2.apk" 2>/dev/null

STAGE=$OUT/stage; cp -r "$ROOT/module" "$STAGE"
cp "$OUT/Stylo2.apk" "$STAGE/Stylo2.apk"
find "$STAGE" -name '*.sh' -exec sed -i 's/\r$//' {} + -exec chmod 0755 {} +
chmod 0755 "$STAGE/META-INF/com/google/android/update-binary"
ZIP=$OUT/OnePlus_Pad2_Stylo2-v$VERSION.zip
rm -f "$ZIP"; (cd "$STAGE" && zip -q -r -X "$ZIP" .)
echo "built $ZIP"
