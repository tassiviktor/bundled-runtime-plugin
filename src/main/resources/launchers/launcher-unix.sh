#!/usr/bin/env bash
set -euo pipefail
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
RUNTIME="$DIR/../runtime"
APP="$DIR/../app"
FLAGS="${FLAGS_PLACEHOLDER}"
exec "$RUNTIME/bin/java" ${FLAGS} -jar "$APP/app.jar" "$@"