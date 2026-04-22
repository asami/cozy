#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
CONTRACT_DIR="$ROOT_DIR/bridge/sbt-bridge/v1"
BASE_REF=${BRIDGE_CHECK_BASE:-HEAD}

required_files="README.md contract.json request-generate.json request-package-car.json request-package-sar.json response-success.json response-error.json"
for rel in $required_files; do
  if [ ! -f "$CONTRACT_DIR/$rel" ]; then
    echo "missing contract file: $CONTRACT_DIR/$rel" >&2
    exit 1
  fi
done

for json_file in \
  "$CONTRACT_DIR/contract.json" \
  "$CONTRACT_DIR/request-generate.json" \
  "$CONTRACT_DIR/request-package-car.json" \
  "$CONTRACT_DIR/request-package-sar.json" \
  "$CONTRACT_DIR/response-success.json" \
  "$CONTRACT_DIR/response-error.json"
 do
  python3 -m json.tool "$json_file" >/dev/null
 done

if git -C "$ROOT_DIR" rev-parse --verify "$BASE_REF" >/dev/null 2>&1; then
  tracked_changed=false
  if ! git -C "$ROOT_DIR" diff --quiet "$BASE_REF" -- bridge/sbt-bridge/v1; then
    tracked_changed=true
  fi
  untracked_changed=false
  if git -C "$ROOT_DIR" status --short --untracked-files=all -- bridge/sbt-bridge/v1 | grep -q '^?? '; then
    untracked_changed=true
  fi
  if [ "$tracked_changed" = true ] || [ "$untracked_changed" = true ]; then
    echo "BRIDGE_CHANGED=true"
  else
    echo "BRIDGE_CHANGED=false"
  fi
else
  echo "BRIDGE_CHANGED=false"
fi
