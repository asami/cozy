#!/bin/sh
set -eu

export COZY_USE_DEVELOPMENT="${COZY_USE_DEVELOPMENT:-true}"
export CNCF_VERSION="${CNCF_VERSION:-0.5.2-SNAPSHOT}"
export SBT_COZY_VERSION="${SBT_COZY_VERSION:-0.1.17-SNAPSHOT}"
export COZY_GENERATOR_VERSION="${COZY_GENERATOR_VERSION:-0.3.1-SNAPSHOT}"
export PROJECT_VERSION="${PROJECT_VERSION:-0.1.0-SNAPSHOT}"

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
  if [ -n "${COZY_GENERATOR_VERSION:-}" ]; then
    set -- "$@" --cozy-generator-version "$COZY_GENERATOR_VERSION"
  fi
  if [ -n "${PROJECT_VERSION:-}" ]; then
    set -- "$@" --version "$PROJECT_VERSION"
  fi
  exec cozy car-sbt-project "$@"
else
  exec cozy "$@"
fi
