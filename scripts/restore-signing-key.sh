#!/usr/bin/env bash
set -euo pipefail
if [[ -z "${MEOW_UPDATE_KEYSTORE_BASE64:-}" ]]; then
  echo 'Ключ обновлений не настроен в GitHub Secrets. Сборка для распространения остановлена.' >&2
  exit 1
fi
umask 077
mkdir -p "$HOME/.android"
printf '%s' "$MEOW_UPDATE_KEYSTORE_BASE64" | base64 --decode > "$HOME/.android/debug.keystore"
chmod 600 "$HOME/.android/debug.keystore"
echo 'Постоянный ключ обновлений восстановлен из защищённого секрета.'
