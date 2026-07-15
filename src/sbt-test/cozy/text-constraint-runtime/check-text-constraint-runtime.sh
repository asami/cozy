#!/usr/bin/env sh
set -eu

grep 'scalaVersion: "3.3.8"' out.d/project.yaml
mkdir -p out.d/src/test/scala
cp verify/GeneratedTextConstraintSpec.scala out.d/src/test/scala/GeneratedTextConstraintSpec.scala
mkdir -p out.d/src/main/scala/domain
cp -R generated.d/target/scala-3.3.8/src_managed/main/scala/domain/. out.d/src/main/scala/domain/
mkdir -p out.d/src/main/scala/domain/impl
cp generated.d/src/main/scala/domain/impl/ComponentFactory.scala out.d/src/main/scala/domain/impl/ComponentFactory.scala
rm -f out.d/src/main/cozy/sample.cml
(
  cd out.d
  sbt --batch test
)
