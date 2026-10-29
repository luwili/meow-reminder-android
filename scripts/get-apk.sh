#!/usr/bin/env bash
set -euo pipefail
command -v gh >/dev/null || { echo 'Установите GitHub CLI: https://cli.github.com'; exit 1; }
repo='luwili/meow-reminder-android'
release="$(gh release list --repo "$repo" --limit 50 --json tagName,isDraft --jq '[.[] | select(.isDraft == false) | select(.tagName | test("^v[0-9]+\\.[0-9]+\\.[0-9]+-debug$"))][0].tagName')"
if [[ -z "$release" || "$release" == null ]]; then
  echo 'Опубликованный APK пока не найден. Проверьте раздел Releases.'
  exit 1
fi
dest="${1:-./apk-download}"
gh release download "$release" --repo "$repo" --pattern 'meow-reminder-*-debug.apk' --dir "$dest"
echo "APK версии $release скачан в $dest. Установите поверх старой версии, не удаляя приложение."
