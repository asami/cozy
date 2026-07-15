#!/usr/bin/env sh
set -eu

grep 'scalaVersion: "3.3.8"' out.d/project.yaml
mkdir -p out.d/src/test/scala
cp verify/GeneratedModelKindContractSpec.scala out.d/src/test/scala/GeneratedModelKindContractSpec.scala
mkdir -p out.d/src/main/scala/domain
cp -R generated.d/target/scala-3.3.8/src_managed/main/scala/domain/. out.d/src/main/scala/domain/
rm -f out.d/src/main/cozy/sample.cml
(
  cd out.d
  sbt --batch test
)
