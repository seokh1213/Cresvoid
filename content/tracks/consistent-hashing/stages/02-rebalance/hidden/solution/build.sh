#!/bin/sh
set -e
cd "$(dirname "$0")"
inputs=src
[ -d lib ] && inputs="src lib"
if [ -f build/app.jar ] && [ -z "$(find $inputs \( -name '*.kt' -o -name '*.jar' -o -type d \) -newer build/app.jar | head -n 1)" ]; then
  exit 0
fi
mkdir -p build
partial="build/.app-$$.jar"
manifest="build/.manifest-$$"
trap 'rm -f "$partial" "$manifest"' EXIT
classpath=""
manifest_classpath=""
for jar in lib/*.jar; do
  [ -f "$jar" ] || continue
  classpath="${classpath:+$classpath:}$jar"
  manifest_classpath="$manifest_classpath ../$jar"
done
kotlinc src/main/kotlin ${classpath:+-classpath "$classpath"} -include-runtime -d "$partial"
if [ -n "$classpath" ]; then
  printf 'Class-Path:%s\n' "$manifest_classpath" > "$manifest"
  jar ufm "$partial" "$manifest"
fi
mv -f "$partial" build/app.jar
