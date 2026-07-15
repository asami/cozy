#!/usr/bin/env sh
set -eu

cd out.d
sbt --batch compile
component="target/scala-3.3.8/src_managed/main/domain/DomainComponent.scala"
[ -f "$component" ]
grep 'object DomainComponent' "$component"
grep 'Person' "$component"

cd ../custom.d
sbt --batch compile
component="target/scala-3.3.8/src_managed/main/org/simplemodeling/textus/artscene/ArtSceneComponent.scala"
[ -f "$component" ]
grep 'object ArtSceneComponent' "$component"
grep 'ExhibitionCandidateService' "$component"
grep 'RegisterFacilityActionCall' "$component"
grep 'ListCandidatesActionCall' "$component"
