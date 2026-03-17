#!/usr/bin/env sh
set -eu

cd out.d
sbt --batch compile
