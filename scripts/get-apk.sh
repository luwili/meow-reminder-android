#!/usr/bin/env bash
set -euo pipefail
command -v gh >/dev/null || { echo 'Установите GitHub CLI: https://cli.github.com'; exit 1; }
repo='luwili/meow-reminder-android'
run="$(gh run list --repo "$repo" --workflow android.yml --status success --limit 1 --json databaseId --jq '.[0].databaseId')"
if [[ -z "$run" || "$run" == null ]]; then
  echo 'Успешная сборка пока не найдена. Проверьте вкладку Actions.'
  exit 1
fi
dest="${1:-./apk-download}"
gh run download "$run" --repo "$repo" --name meow-reminder-debug-apk --dir "$dest"
echo "APK скачан: $dest/app-debug.apk"
