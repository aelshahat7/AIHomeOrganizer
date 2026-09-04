#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SDK_CANDIDATES=()
[ -n "${ANDROID_HOME:-}" ] && SDK_CANDIDATES+=("$ANDROID_HOME")
[ -n "${ANDROID_SDK_ROOT:-}" ] && SDK_CANDIDATES+=("$ANDROID_SDK_ROOT")
SDK_CANDIDATES+=("$HOME/lib/android-sdk-9123335" "$HOME/lib/android-sdk" "$HOME/android-sdk" "$PREFIX/opt/android-sdk" "$PREFIX/share/android-sdk")
ANDROID_HOME=""
for candidate in "${SDK_CANDIDATES[@]}"; do if [ -f "$candidate/platforms/android-36/android.jar" ]; then ANDROID_HOME="$candidate"; break; fi; done
if [ -z "$ANDROID_HOME" ] && [ -d "$HOME/lib" ]; then while IFS= read -r jar; do ANDROID_HOME="${jar%/platforms/android-36/android.jar}"; break; done < <(find "$HOME/lib" -type f -path '*/platforms/android-36/android.jar' -print 2>/dev/null | head -n 1); fi
if [ -z "$ANDROID_HOME" ]; then echo "ERROR: Android API 36 platform (android-36/android.jar) was not found."; exit 1; fi
PLATFORM="$ANDROID_HOME/platforms/android-36"; AAPT2="${AAPT2:-$PREFIX/bin/aapt2}"; D8="${D8:-$PREFIX/bin/d8}"; APKSIGNER="${APKSIGNER:-$PREFIX/bin/apksigner}"; ANDROID_JAR="$PLATFORM/android.jar"
require_file(){ [ -e "$1" ] || { echo "Missing required build component: $1"; exit 1; }; }
require_exec(){ require_file "$1"; [ -x "$1" ] || { echo "Build component is not executable: $1"; exit 1; }; }
require_exec "$AAPT2"; require_exec "$D8"; require_exec "$APKSIGNER"; require_file "$ANDROID_JAR"
command -v javac >/dev/null 2>&1 || { echo "javac not found. Install OpenJDK 21 first."; exit 1; }
command -v keytool >/dev/null 2>&1 || { echo "keytool not found. Install OpenJDK 21 first."; exit 1; }
command -v zip >/dev/null 2>&1 || { echo "zip not found. Run: pkg install zip"; exit 1; }
OUT="$PROJECT_DIR/build"; LOCAL="$PROJECT_DIR/.local"; KEYSTORE="$LOCAL/debug.keystore"
rm -rf "$OUT"; mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex" "$LOCAL"
if [ ! -f "$KEYSTORE" ]; then keytool -genkeypair -v -keystore "$KEYSTORE" -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1; fi
"$AAPT2" compile -o "$OUT/res/resources.zip" --dir "$PROJECT_DIR/res"
"$AAPT2" link -o "$OUT/base.apk" -I "$ANDROID_JAR" --manifest "$PROJECT_DIR/AndroidManifest.xml" --java "$OUT/gen" --min-sdk-version 24 --target-sdk-version 36 "$OUT/res/resources.zip"
# Active implementation lives in com/. Legacy/support sources remain under src/.
# Exclude duplicate class names from src so javac sees one implementation of each class.
find "$PROJECT_DIR/com" -name '*.java' -print > "$OUT/sources.txt"
find "$PROJECT_DIR/src" -name '*.java' ! -path '*/AppMetadataResolver.java' ! -path '*/FolderController.java' ! -path '*/HomeShortcut.java' ! -path '*/LauncherAdapter.java' ! -path '*/MainActivity.java' ! -path '*/OrganizationPlan.java' ! -print >> "$OUT/sources.txt"
javac -source 8 -target 8 -encoding UTF-8 -classpath "$ANDROID_JAR" -d "$OUT/classes" @"$OUT/sources.txt"
find "$OUT/classes" -name '*.class' -print0 | xargs -0 "$D8" --min-api 24 --output "$OUT/dex"
cp "$OUT/base.apk" "$OUT/AIHomeOrganizer.apk"; (cd "$OUT" && zip -q -j AIHomeOrganizer.apk dex/classes.dex)
"$APKSIGNER" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android --ks-key-alias androiddebugkey "$OUT/AIHomeOrganizer.apk"
"$APKSIGNER" verify "$OUT/AIHomeOrganizer.apk"
echo; echo "BUILD SUCCESSFUL"; echo "APK: $OUT/AIHomeOrganizer.apk"; echo "TARGET SDK: 36"
