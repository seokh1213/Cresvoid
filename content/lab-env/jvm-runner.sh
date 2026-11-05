#!/bin/sh
# usage: jvm-runner.sh build|run, called from a lab Kotlin project's build.sh or run.sh.
# The project names its library set in .lab-libs; the libraries live in the runner image, not on this machine.
set -e
here=$(cd "$(dirname "$0")" && pwd)
image="cresvoid-lab/jvm-runner:2"
project=$(pwd)
libs=$(cat "$project/.lab-libs")
if ! docker info >/dev/null 2>&1; then
  echo "Docker 가 실행 중이 아닙니다. Docker Desktop 을 켠 뒤 다시 실행하세요." >&2
  exit 2
fi
if ! docker image inspect "$image" >/dev/null 2>&1; then
  echo "lab 실행 이미지를 처음 한 번 만듭니다." >&2
  docker build -q -t "$image" "$here/images/jvm-runner" >/dev/null
fi
case "$1" in
  build)
    if [ -f build/app.jar ] && [ -z "$(find src .lab-libs -newer build/app.jar | head -n 1)" ]; then
      exit 0
    fi
    exec docker run --rm -v "$project:/work" -w /work "$image" lab-build "$libs"
    ;;
  run)
    port="${PORT:-8080}"
    exec docker run --rm -i --init -p "127.0.0.1:$port:$port" -e PORT="$port" -v "$project:/work:ro" -w /work "$image" lab-run "$libs"
    ;;
  *)
    echo "usage: jvm-runner.sh build|run" >&2
    exit 2
    ;;
esac
