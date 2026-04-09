#!/bin/sh
set -eu

if [ "${1-}" = "modeler-scala" ]; then
  shift
  exec /Users/asami/src/dev2026/cncf-samples/bin/cozy car-sbt-project "$@"
else
  exec /Users/asami/src/dev2026/cncf-samples/bin/cozy "$@"
fi
