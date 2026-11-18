#!/usr/bin/env bash
# Build Zoo for Zotero inside Docker.
# Lives in the docker-build/ folder of the Zoo repository and builds that repository.
#
# Usage (from anywhere):
#   docker-build/zoo-build.sh                       # run unit tests, then build the debug APK
#   docker-build/zoo-build.sh assembleDebug         # any Gradle tasks you like
#   PROJECT=/path/to/other/checkout docker-build/zoo-build.sh assembleDebug
#
# Two named Docker volumes keep things between runs:
#   zoo-gradle-cache  - downloaded libraries (later builds are much faster)
#   zoo-android-home  - the debug signing key; keeping it means each new APK installs as an UPDATE
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
PROJECT="${PROJECT:-$(cd "$HERE/.." && pwd)}"   # the repository this folder is in
IMAGE="zoo-builder"

if [[ ! -f "$PROJECT/gradlew" ]]; then
  echo "No Zoo project found at: $PROJECT" >&2
  echo "Keep this script in the docker-build/ folder of the Zoo repository, or set PROJECT=/path/to/Zoo-For-Zotero" >&2
  exit 1
fi
if [[ ! -f "$PROJECT/apikeys.properties" ]]; then
  echo "Missing $PROJECT/apikeys.properties (your Zotero OAuth key and secret)." >&2
  exit 1
fi

docker build -t "$IMAGE" \
  --build-arg UID="$(id -u)" \
  --build-arg GID="$(id -g)" \
  "$HERE"

TASKS=("$@")
if [[ ${#TASKS[@]} -eq 0 ]]; then
  TASKS=(testDebugUnitTest assembleDebug)
fi

TTY_FLAGS=()
if [[ -t 1 ]]; then TTY_FLAGS=(-it); fi

docker run --rm "${TTY_FLAGS[@]}" \
  -v "$PROJECT":/project \
  -v zoo-gradle-cache:/home/builder/.gradle \
  -v zoo-android-home:/home/builder/.android \
  "$IMAGE" ./gradlew "${TASKS[@]}"

APK="$PROJECT/app/build/outputs/apk/debug/app-debug.apk"
if [[ -f "$APK" ]]; then
  echo
  echo "APK ready: $APK"
fi
