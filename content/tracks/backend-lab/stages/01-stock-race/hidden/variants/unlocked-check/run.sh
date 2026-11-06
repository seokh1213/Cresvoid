#!/bin/sh
cd "$(dirname "$0")"
exec java -jar build/app.jar
