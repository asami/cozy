#!/usr/bin/env sh
set -eu

cd out.d

sbt --batch compile > compile.log 2>&1

grep -q "compile" compile.log
if grep -q "Conflicting definitions" compile.log; then
  echo "Expected compile success, but found conflicting definitions."
  exit 1
fi

echo "KEYWORD_DUPLICATE_COMPILE_OK"
