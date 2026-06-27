#!/bin/sh
set -eu

export COZY_USE_DEVELOPMENT="${COZY_USE_DEVELOPMENT:-true}"

if [ "${1-}" = "modeler-scala" ]; then
  shift
  if [ -n "${CNCF_VERSION:-}" ]; then
    set -- "$@" --cncf-version "$CNCF_VERSION"
  fi
  if [ -n "${SIMPLEMODELING_MODEL_VERSION:-}" ]; then
    set -- "$@" --simplemodeling-model-version "$SIMPLEMODELING_MODEL_VERSION"
  fi
  if [ -n "${CNCF_COLLABORATOR_API_VERSION:-}" ]; then
    set -- "$@" --cncf-collaborator-api-version "$CNCF_COLLABORATOR_API_VERSION"
  fi
  exec cozy car-sbt-project "$@"
else
  exec cozy "$@"
fi
