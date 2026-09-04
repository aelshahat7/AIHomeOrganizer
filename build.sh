#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Prefer an explicitly configured SDK, but do not assume the SDK directory name.
# This matters on Termux because the SDK may have been unpacked under a generated
# directory name and Android x86_64 SDK executables must never be used on ARM64.
SDK_CANDIDATES=()
[ -n "${ANDROID_HOME:-}" ] && SDK_CANDIDATES+=("$ANDROID_HOME")
[ -n "${ANDROID_SDK_ROOT:-}" ] && SDK_CANDIDATES+=("$ANDROID_SDK_ROOT")
SDK_CANDIDATES+=(
    "$HOME/lib/android-sdk-9123335"
    "$HOME/lib/android-sdk"
    "$HOME/android-sdk"
    "$PREFIX/opt/android-sdk"
    "$PREFIX/share/android-sdk"
)

ANDROID_HOME=""
for candidate in "${SDK_CANDIDATES[@]}"; do
    if [ -f "$candidate/platforms/android-36/android.jar" ]; then
        ANDROID_HOME="$candidate"
        break
    fi
done

# Also discover an installed API 36 platform under ~/lib without requiring the
# caller to know the SDK's generated directory name.
if [ -z "$ANDROID_HOME" ] && [ -d "$HOME/lib" ]; then
    while IFS= read -r jar; do
        ANDROID_HOME="${jar%/platforms/android-36/android.jar}"
        break
    done < <(find "$HOME/lib" -type f -path '*/platforms/android-36/android.jar' -print 2>/dev/null | head -n 1)
fi

if [ -z "$ANDROID_HOME" ]; then
    echo "ERROR: Android API 36 platform (android-36/android.jar) was not found."
    echo ""
    echo "This build requires the API 36 platform, but it does NOT require SDK x86 binaries."
    echo "Install/unpack android-36 into an ARM64-accessible SDK, then run this script again."
    echo "To locate an existing platform, run:"
    echo "  find \"$HOME\" -type f -path '*/platforms/android-36/android.jar' -print 2>/dev/null"
    echo ""
    echo "If the command prints a path, export ANDROID_HOME to the directory before /platforms/android-36."
    exit 1
fi

PLATFORM="$ANDROID_HOME/platforms/android-36"
AAPT2="${AAPT2:-$PREFIX/bin/aapt2}"
D8="${D8:-$PREFIX/bin/d8}"
APKSIGNER="${APKSIGNER:-$PREFIX/bin/apksigner}"
ANDROID_JAR="$PLATFORM/android.jar"

require_file() {
    local path="$1"
    if [ ! -e "$path" ]; then
        echo "Missing required build component: $path"
        exit 1
    fi
}

require_exec() {
    local path="$1"
    require_file "$path"
    if [ ! -x "$path" ]; then
        echo "Build component is not executable: $path"
        exit 1
    fi
}

# These are the ARM64-native Termux tools. Never fall back to SDK x86 binaries.
require_exec "$AAPT2"
require_exec "$D8"
require_exec "$APKSIGNER"
require_file "$ANDROID_JAR"
command -v javac >/dev/null 2>&1 || { echo "javac not found. Install OpenJDK 21 first."; exit 1; }
command -v keytool >/dev/null 2>&1 || { echo "keytool not found. Install OpenJDK 21 first."; exit 1; }
command -v zip >/dev/null 2>&1 || { echo "zip not found. Run: pkg install zip"; exit 1; }

OUT="$PROJECT_DIR/build"
LOCAL="$PROJECT_DIR/.local"
KEYSTORE="$LOCAL/debug.keystore"
rm -rf "$OUT"
mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex" "$LOCAL"

# Keep the debug signing key outside build/ so clean builds retain the same
# certificate and APK reinstall/upgrade does not fail because the signature changed.
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -storepass android \
        -alias androiddebugkey \
        -keypass android \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
    echo "Created persistent debug keystore: $KEYSTORE"
else
    echo "Using persistent debug keystore: $KEYSTORE"
fi

echo "Using Android SDK: $ANDROID_HOME"
echo "Using Android platform: $ANDROID_JAR"
echo "Using native Termux tools: $AAPT2 | $D8 | $APKSIGNER"

"$AAPT2" compile \
    -o "$OUT/res/resources.zip" \
    --dir "$PROJECT_DIR/res"

"$AAPT2" link \
    -o "$OUT/base.apk" \
    -I "$ANDROID_JAR" \
    --manifest "$PROJECT_DIR/AndroidManifest.xml" \
    --java "$OUT/gen" \
    --min-sdk-version 24 \
    --target-sdk-version 36 \
    "$OUT/res/resources.zip"

find "$PROJECT_DIR/src" -name '*.java' -print > "$OUT/sources.txt"
javac -source 8 -target 8 -encoding UTF-8 \
    -classpath "$ANDROID_JAR" \
    -d "$OUT/classes" \
    @"$OUT/sources.txt"

find "$OUT/classes" -name '*.class' -print0 \
    | xargs -0 "$D8" --min-api 24 --output "$OUT/dex"

cp "$OUT/base.apk" "$OUT/AIHomeOrganizer.apk"
(cd "$OUT" && zip -q -j AIHomeOrganizer.apk dex/classes.dex)

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
echo "SIGNING KEYSTORE: $KEYSTORE"
echo "TARGET SDK: 36"
