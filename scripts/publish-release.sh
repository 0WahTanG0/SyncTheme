#!/usr/bin/env bash
# Собирает приложения и публикует релиз GitHub, из которого SyncTheme обновляется сам.
# Использование: scripts/publish-release.sh "Что нового" [owner/repo]
# Нужны: расшифрованные ключи (scripts/keys.sh unlock) и gh с доступом к репозиторию.
set -euo pipefail
cd "$(dirname "$0")/.."
NOTES="${1:-}"
REPO="${2:-$(sed -n 's/^updateRepo=//p' gradle.properties)}"
[ -f keystore/app.p12 ] || { echo "Нет ключей: выполните scripts/keys.sh unlock" >&2; exit 1; }

./gradlew :buildtools:test :mobile:assembleRelease :wear:assembleRelease -PupdateRepo="$REPO"

APK=mobile/build/outputs/apk/release/mobile-release.apk
AAPT2=$(ls "${ANDROID_HOME:?}"/build-tools/*/aapt2 | sort -V | tail -1)
read -r CODE NAME < <(
  "$AAPT2" dump badging "$APK" 2>/dev/null |
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

# Только REST API (GraphQL в облачных сессиях недоступен).
ID=$(gh api "repos/$REPO/releases" -f tag_name="$TAG" -f target_commitish=main \
  -f name="SyncTheme $NAME" -f body="${NOTES:-SyncTheme $NAME}" --jq .id)
upload() {
  gh api --method POST -H "Content-Type: $2" \
    "https://uploads.github.com/repos/$REPO/releases/$ID/assets?name=$(basename "$1")" --input "$1" --jq .browser_download_url
}
upload dist/SyncTheme-phone.apk application/vnd.android.package-archive
upload dist/SyncTheme-watch.apk application/vnd.android.package-archive
upload dist/update.json application/json
echo "Опубликовано: https://github.com/$REPO/releases/tag/$TAG"
