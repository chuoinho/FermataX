#!/bin/bash

umask 077

FERMATAX_REPO_URL="${FERMATAX_REPO_URL:-https://github.com/chuoinho/FermataX.git}"
FERMATAX_PROJ_DIR="${FERMATAX_PROJ_DIR:-$HOME/FermataX}"

create_local_props() {
  KEYSTORE_PATH="${KEYSTORE_PATH:-$HOME/.android/fermatax-local-build.p12}"

  if [ -z "$KEY_ALIAS" ]; then
    read -p "Enter key alias [default - fermatax]: " KEY_ALIAS
    : ${KEY_ALIAS:='fermatax'}
  fi

  read -s -p "Enter password: " PASSWORD
  echo
  if [ ${#PASSWORD} -lt 6 ]; then
    echo "The password must be at least 6 characters long!"
    return 1
  fi

  read -s -p "Confirm password: " PASSWORD2
  echo
  if [ "$PASSWORD" != "$PASSWORD2" ]; then
    echo "Passwords do not match!"
    return 1
  fi

  if [ ! -f "$KEYSTORE_PATH" ]; then
    mkdir -p "$(dirname "$KEYSTORE_PATH")"
    keytool -genkeypair -noprompt -keystore "$KEYSTORE_PATH" -storetype PKCS12 \
      -alias "$KEY_ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
      -dname "CN=FermataX Local Build, OU=Release, O=FermataX" \
      -storepass "$PASSWORD" -keypass "$PASSWORD" || return 1
    echo "Generated a random signing key at $KEYSTORE_PATH. Back it up before rebuilding elsewhere."
  fi

  echo "storeFile=$KEYSTORE_PATH" > "$1"
  echo "keyAlias=$KEY_ALIAS" >> "$1"
  echo "keyPassword=$PASSWORD" >> "$1"
  echo "storePassword=$PASSWORD" >> "$1"
  chmod 600 "$KEYSTORE_PATH" "$1"
}

[ -d "$FERMATAX_PROJ_DIR" ] || git clone --recurse-submodules "$FERMATAX_REPO_URL" "$FERMATAX_PROJ_DIR"
cd "$FERMATAX_PROJ_DIR"

while [ ! -f local.properties ]; do
  create_local_props local.properties || true
done

[ -z "$1" ] || exec "$@"
