#!/bin/sh
set -e

DIR="$(cd "$(dirname "$0")"; pwd -P)"
DEST_DIR="$DIR/dist"
mkdir -p "$DEST_DIR"
export NO_GS=true
TASK='apk'
BUILD_TYPE='Release'

while [ "$1" != "" ]; do
    case "$1" in
        -c)
            CLEAN='clean'
            ;;
        -b)
            TASK='aab'
            ;;
        -d)
            BUILD_TYPE='Debug'
            ;;
        *)
            echo "Unknown argument: $1"
            exit 1
            ;;
    esac
    shift
done

if [ -z "$ANDROID_SDK_ROOT" ]; then
    if [ -f "$DIR/local.properties" ]; then
        ANDROID_SDK_ROOT="$(grep sdk.dir= local.properties | cut -d = -f2)"
    fi
fi

if [ -z "$ANDROID_SDK_ROOT" ]; then
    echo 'ANDROID_SDK_ROOT environment variable is not set'
    exit 1
else
    echo "ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"
fi

CMAKE_PATH="$(find $ANDROID_SDK_ROOT/cmake/* -maxdepth 1 -type d -name bin | sort -V | tail -1)"
echo "CMAKE_PATH=$CMAKE_PATH"
export PATH=$CMAKE_PATH:$PATH
cd "$DIR"

# Distribution contract: FermataX is one app/package for phone and Android Auto.
# Gradle keeps its platform source sets internally, but this script must never publish
# separate Mobile/Auto or ABI-specific APK products. APK builds are one universal package.
build() {
  local ext="$TASK"
  local app_flavor=${APP_ID_SFX:-$(grep -oP "${TASK}Flavor=\K.+" "$DIR/local.properties"  2>/dev/null || true)}
  local app_sfx=${APP_ID_SFX:-$(grep -oP "${TASK}IdSfx=\K.+" "$DIR/local.properties"  2>/dev/null || true)}
  [ -z "$app_sfx" ] || local app_sfx="-PAPP_ID_SFX=$app_sfx"
  if [ "$TASK" = 'apk' ]; then
    local task="package${app_flavor}Auto${BUILD_TYPE}UniversalApk"
    local output_root="fermata/build/outputs/apk_from_bundle"
  else
    local task="bundle${app_flavor}Auto${BUILD_TYPE}"
    local output_root="fermata/build/outputs/bundle"
  fi

  # Remove stale artifacts from this packaging family so an old build can never overwrite
  # the newly produced universal package in dist/.
  if [ -d "$output_root" ]; then
    find "$output_root" -type f -name "fermata*.$ext" -delete
  fi

  ./gradlew $CLEAN verifyWebOnlyProductionGraph fermata:$task $app_sfx

  set -- $(find "$output_root" -type f -name "fermata*.$ext" -print)
  if [ "$#" -ne 1 ]; then
    echo "Expected exactly one FermataX $ext artifact, found $#"
    exit 1
  fi

  local path="$1"
  if [ "$TASK" = 'apk' ]; then
    if ! command -v jar >/dev/null 2>&1; then
      echo 'The JDK jar tool is required to inspect the universal APK'
      exit 1
    fi
    if jar tf "$path" | grep -Eqi 'jlibtorrent|libtorrent'; then
      echo 'Universal APK contains a forbidden libtorrent artifact'
      exit 1
    fi
  fi
  local version=${path##*fermata-}
  version=${version%%-*}
  local dst="$DEST_DIR/FermataX-${version}.$ext"
  cp "$path" "$dst"
  echo "Built $dst"
}

build
