#!/usr/bin/env bash
# 无需 Gradle：直接用 Android SDK 构建工具编译并签名 APK
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-/opt/android-sdk}
BT=$SDK/build-tools/36.0.0
JAR=$SDK/platforms/android-36/android.jar
rm -rf build && mkdir -p build/{res,gen,classes,dex}

$BT/aapt2 compile --dir res -o build/res/
$BT/aapt2 link -o build/base.apk -I "$JAR" --manifest AndroidManifest.xml \
    --java build/gen build/res/*.flat --min-sdk-version 26 --target-sdk-version 36
javac -nowarn --release 8 -cp "$JAR" -encoding UTF-8 \
    -d build/classes $(find src build/gen -name '*.java')
$BT/d8 --release --min-api 26 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
$BT/zipalign -f -p 4 build/unsigned.apk build/aligned.apk

KS=${KEYSTORE:-release.keystore}
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass shangban -keypass shangban -alias shangban \
    -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=Shangban,O=Baide,C=CN" >/dev/null 2>&1
fi
$BT/apksigner sign --ks "$KS" --ks-pass pass:shangban --key-pass pass:shangban \
    --out ShangBan.apk build/aligned.apk
$BT/apksigner verify ShangBan.apk && echo "OK -> $(pwd)/ShangBan.apk"
