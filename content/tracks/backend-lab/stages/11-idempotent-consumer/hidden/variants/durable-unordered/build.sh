#!/bin/sh
# Lab projects with a .lab-libs file build inside the pinned runner image (content/lab-env/README.md).
set -e
cd "$(dirname "$0")"
root="${CRESVOID_ROOT:-}"
if [ -z "$root" ]; then
  dir=$(pwd)
  while [ "$dir" != "/" ] && [ ! -d "$dir/content/lab-env" ]; do dir=$(dirname "$dir"); done
  root="$dir"
fi
if [ ! -f "$root/content/lab-env/jvm-runner.sh" ]; then
  echo "cresvoid 디렉터리를 찾지 못했습니다. CRESVOID_ROOT 를 cresvoid 경로로 설정하세요." >&2
  exit 2
fi
exec sh "$root/content/lab-env/jvm-runner.sh" build
