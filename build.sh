#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$HOME/lib/android-sdk-9123335}"
PLATFORM="${ANDROID_HOME}/platforms/android-35"

# On Termux aarch64, prefer the native Termux build tools. Android SDK
# command-line binaries such as aapt2 may be x86_64 and cannot execute here.
AAPT2="${AAPT2:-$PREFIX/bin/aapt2}"
D8="${D8:-$PREFIX/bin/d8}"
APKSIGNER="${APKSIGNER:-$PREFIX/bin/apksigner}"
ANDROID_JAR="${PLATFORM}/android.jar"

for tool in "$AAPT2" "$D8" "$APKSIGNER" "$ANDROID_JAR"; do
    if [ ! -e "$tool" ] || { [ ! -x "$tool" ] && [[ "$tool" != *.jar ]]; }; then
        echo "Missing required build component: $tool"
        exit 1
    fi
done

command -v javac >/dev/null 2>&1 || { echo "javac not found. Install OpenJDK first."; exit 1; }
command -v zip >/dev/null 2>&1 || { echo "zip command not found. Run: pkg install zip"; exit 1; }

OUT="$PROJECT_DIR/build"
rm -rf "$OUT"
mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex"

"$AAPT2" compile --dir "$PROJECT_DIR/res" -o "$OUT/res/resources.zip"
"$AAPT2" link \
    -I "$ANDROID_JAR" \
    --manifest "$PROJECT_DIR/AndroidManifest.xml" \
    --java "$OUT/gen" \
    -o "$OUT/base.apk" \
    "$OUT/res/resources.zip"

find "$PROJECT_DIR/src" -name '*.java' -print > "$OUT/sources.txt"
javac -source 8 -target 8 -encoding UTF-8 \
    -classpath "$ANDROID_JAR" \
    -d "$OUT/classes" \
    @"$OUT/sources.txt"

find "$OUT/classes" -name '*.class' -print0 | xargs -0 "$D8" --min-api 24 --output "$OUT/dex"

cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT" && zip -q -j unsigned.apk dex/classes.dex)

# zipalign is optional for this debug APK. The SDK copy is x86_64 on
# this aarch64 Termux environment, so we intentionally skip it.
cp "$OUT/unsigned.apk" "$OUT/AIHomeOrganizer.apk"

KEYSTORE="$OUT/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -storepass android \
        -alias androiddebugkey \
        -keypass android \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi

"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --ks-key-alias androiddebugkey \
    "$OUT/AIHomeOrganizer.apk"

"$APKSIGNER" verify "$OUT/AIHomeOrganizer.apk"

echo
echo "BUILD SUCCESSFUL"
echo "APK: $OUT/AIHomeOrganizer.apk"
