#!/bin/sh
set -e

DIR="$(cd "$(dirname "$0")"; pwd -P)"
DEST_DIR="$DIR/dist"
mkdir -p "$DEST_DIR"
export NO_GS=true
TASK='apk'
TARGET='auto'

usage() {
    echo "Usage: $0 [auto|mobile] [-c] [-a] [-b]"
    echo "  auto|mobile  Build target (default: auto)"
    echo "  -c           Clean before building"
    echo "  -a           Also build armeabi-v7a APK (APK only)"
    echo "  -b           Build Android App Bundle instead of universal APK"
}

while [ "$1" != "" ]; do
    case "$1" in
        auto|mobile)
            TARGET="$1"
            ;;
        -c)
            CLEAN='clean'
            ;;
        -a)
            ARM=true
            ;;
        -b)
            TASK='aab'
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown argument: $1"
            usage
            exit 1
            ;;
    esac
    shift
done

case "$TARGET" in
    auto)
        TARGET_CAP='Auto'
        ;;
    mobile)
        TARGET_CAP='Mobile'
        ;;
esac

if [ -z "$ANDROID_SDK_ROOT" ]; then
    if [ -f "$DIR/local.properties" ]; then
        ANDROID_SDK_ROOT="$(grep sdk.dir= "$DIR/local.properties" | cut -d = -f2)"
    fi
fi

if [ -z "$ANDROID_SDK_ROOT" ]; then
    echo 'ANDROID_SDK_ROOT environment variable is not set'
    exit 1
else
    echo "ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"
fi

CMAKE_PATH="$(find "$ANDROID_SDK_ROOT"/cmake/* -maxdepth 1 -type d -name bin | sort -V | tail -1)"
echo "CMAKE_PATH=$CMAKE_PATH"
export PATH="$CMAKE_PATH:$PATH"
cd "$DIR"

build() {
  local ext="$TASK"
  local app_flavor=${APP_ID_SFX:-$(grep -oP "${TASK}Flavor=\K.+" "$DIR/local.properties" 2>/dev/null || true)}
  local app_sfx=${APP_ID_SFX:-$(grep -oP "${TASK}IdSfx=\K.+" "$DIR/local.properties" 2>/dev/null || true)}
  [ -z "$app_sfx" ] || local app_sfx="-PAPP_ID_SFX=$app_sfx"
  if [ "$TASK" = 'apk' ]; then
    local task="package${app_flavor}${TARGET_CAP}ReleaseUniversalApk"
    local abi="-PABI=$1"
    [ "$1" = 'arm64-v8a' ] && local sfx='-arm64' || local sfx='-arm'
  else
    local task="bundle${app_flavor}${TARGET_CAP}Release"
    local abi=''
    local sfx=''
  fi

  ./gradlew $CLEAN "fermata:$task" $abi $app_sfx
  found=false
  for path in fermata/build/outputs/*/*/fermata*."$ext"; do
    [ -f "$path" ] || continue
    case "$path" in
      *"${TARGET}Release"*|*"${TARGET_CAP}Release"*)
        version=${path##*fermata-}
        version=${version%%-*}
        dst="$DEST_DIR/fermata-${TARGET}-${version}${sfx}.${ext}"
        mv "$path" "$dst"
        echo "Built $dst"
        found=true
        ;;
    esac
  done
  if [ "$found" != true ]; then
    echo "No ${TARGET} ${TASK} artifact found under fermata/build/outputs"
    exit 1
  fi
}

[ "$ARM" = true ] && [ "$TASK" = 'apk' ] && build 'armeabi-v7a' || true
build 'arm64-v8a'
