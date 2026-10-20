#!/usr/bin/env bash
# Ключи подписи хранятся в репозитории только в зашифрованном виде (AES-256, keystore.tar.gz.enc).
#   scripts/keys.sh unlock   — расшифровать в keystore/ (пароль: SYNCTHEME_KEYS_PASSWORD или ввод с клавиатуры)
#   scripts/keys.sh lock     — заново зашифровать keystore/ в keystore.tar.gz.enc
set -euo pipefail
cd "$(dirname "$0")/.."
PASS="${SYNCTHEME_KEYS_PASSWORD:-}"
if [ -z "$PASS" ]; then read -rsp "Пароль от ключей: " PASS; echo; fi
case "${1:-}" in
  unlock)
    openssl enc -d -aes-256-cbc -pbkdf2 -iter 200000 -pass "pass:$PASS" -in keystore.tar.gz.enc | tar xzf -
    echo "Ключи расшифрованы в keystore/" ;;
  lock)
    tar czf - keystore | openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -pass "pass:$PASS" -out keystore.tar.gz.enc
    echo "keystore/ зашифрован в keystore.tar.gz.enc" ;;
  *) echo "Использование: $0 unlock|lock" >&2; exit 1 ;;
esac
