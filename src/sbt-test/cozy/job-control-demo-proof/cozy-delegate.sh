#!/bin/sh
set -eu

export COZY_PROJECT_DIR="${COZY_PROJECT_DIR:-/Users/asami/src/dev2025/cozy}"

if [ "${1-}" = "modeler-scala" ]; then
  shift
  exec /Users/asami/src/dev2026/cncf-samples/bin/cozy car-sbt-project "$@"
else
  exec /Users/asami/src/dev2026/cncf-samples/bin/cozy "$@"
fi
