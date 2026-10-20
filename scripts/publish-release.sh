#!/usr/bin/env bash
# Собирает приложения и публикует релиз GitHub, из которого SyncTheme обновляется сам.
# Использование: scripts/publish-release.sh owner/repo "Что нового"
set -euo pipefail
REPO="${1:?Укажите owner/repo}"
NOTES="${2:-}"
cd "$(dirname "$0")/.."

./gradlew :mobile:assembleRelease :wear:assembleRelease -PupdateRepo="$REPO"

APK=mobile/build/outputs/apk/release/mobile-release.apk
read -r CODE NAME < <(
  "${ANDROID_HOME:?}"/build-tools/*/aapt2 dump badging "$APK" 2>/dev/null |
    sed -n "s/^package: name='[^']*' versionCode='\([0-9]*\)' versionName='\([^']*\)'.*/\1 \2/p" | head -1
)
TAG="v$NAME"

mkdir -p dist
cp "$APK" dist/SyncTheme-phone.apk
cp wear/build/outputs/apk/release/wear-release.apk dist/SyncTheme-watch.apk
python3 - "$CODE" "$NAME" "$NOTES" "$REPO" "$TAG" > dist/update.json <<'PY'
import json, sys
code, name, notes, repo, tag = sys.argv[1:]
print(json.dumps({
    "versionCode": int(code),
    "versionName": name,
    "notes": notes,
    "apk": f"https://github.com/{repo}/releases/download/{tag}/SyncTheme-phone.apk",
}, ensure_ascii=False, indent=2))
PY

gh release create "$TAG" dist/SyncTheme-phone.apk dist/SyncTheme-watch.apk dist/update.json \
  --repo "$REPO" --title "SyncTheme $NAME" --notes "${NOTES:-SyncTheme $NAME}"
echo "Опубликовано: https://github.com/$REPO/releases/tag/$TAG"
