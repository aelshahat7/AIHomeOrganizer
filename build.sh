#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$HOME/lib/android-sdk-9123335}"
PLATFORM="${ANDROID_HOME}/platforms/android-35"
BUILD_TOOLS="${ANDROID_HOME}/build-tools/37.0.0"
AAPT2="${AAPT2:-$(command -v aapt2 || true)}"
D8="${D8:-$(command -v d8 || true)}"
APKSIGNER="${APKSIGNER:-${BUILD_TOOLS}/apksigner}"
ZIPALIGN="${ZIPALIGN:-${BUILD_TOOLS}/zipalign}"
ANDROID_JAR="${PLATFORM}/android.jar"

for tool in "$AAPT2" "$D8" "$APKSIGNER" "$ZIPALIGN" "$ANDROID_JAR"; do
    if [ ! -e "$tool" ] || [ ! -x "$tool" ] && [[ "$tool" != *.jar ]]; then
        echo "Missing required build component: $tool"
        exit 1
    fi
done

if ! command -v javac >/dev/null 2>&1; then
    echo "javac not found. Install OpenJDK first."
    exit 1
fi
if ! command -v zip >/dev/null 2>&1; then
    echo "zip command not found. Run: pkg install zip"
    exit 1
fi

OUT="$PROJECT_DIR/build"
rm -rf "$OUT"
mkdir -p "$OUT/res" "$OUT/gen" "$OUT/obj" "$OUT/classes" "$OUT/dex"

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

"$D8" --min-api 24 --output "$OUT/dex" "$OUT/classes"/*.class "$OUT/classes"/com/aelshahat/homeorganizer/*.class 2>/dev/null || \
"$D8" --min-api 24 --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

cp "$OUT/base.apk" "$OUT/unsigned-aligned.apk"
(cd "$OUT" && zip -q -j unsigned-aligned.apk dex/classes.dex)

"$ZIPALIGN" -f 4 "$OUT/unsigned-aligned.apk" "$OUT/AIHomeOrganizer-unsigned.apk"

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

cp "$OUT/AIHomeOrganizer-unsigned.apk" "$OUT/AIHomeOrganizer.apk"
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
