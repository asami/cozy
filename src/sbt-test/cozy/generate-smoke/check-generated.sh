#!/usr/bin/env sh
set -eu

cd out.d
sbt --batch compile
component="target/scala-3.3.7/src_managed/main/domain/DomainComponent.scala"
[ -f "$component" ]
grep 'object DomainComponent' "$component"
grep 'Person' "$component"
