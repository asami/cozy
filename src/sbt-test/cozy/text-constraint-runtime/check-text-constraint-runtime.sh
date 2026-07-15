#!/usr/bin/env sh
set -eu

grep 'scalaVersion: "3.3.8"' out.d/project.yaml
mkdir -p out.d/src/test/scala
cp verify/GeneratedTextConstraintSpec.scala out.d/src/test/scala/GeneratedTextConstraintSpec.scala
mkdir -p out.d/src/main/scala/domain
cp -R generated.d/target/scala-3.3.8/src_managed/main/scala/domain/entity out.d/src/main/scala/domain/entity
rm -f out.d/src/main/cozy/sample.cml
(
  cd out.d
  sbt --batch test
)
