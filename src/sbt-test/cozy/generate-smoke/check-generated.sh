#!/usr/bin/env sh
set -eu

cd out.d
sbt --batch compile
component="target/scala-3.3.8/src_managed/main/domain/DomainComponent.scala"
[ -f "$component" ]
grep 'object DomainComponent' "$component"
grep 'Person' "$component"

cd ../custom.d
sbt --batch test cozyBuildCAR
component="target/scala-3.3.8/src_managed/main/org/simplemodeling/textus/artscene/ArtSceneComponent.scala"
[ -f "$component" ]
grep 'object ArtSceneComponent' "$component"
grep 'ExhibitionCandidateService' "$component"
grep 'RegisterFacilityActionCall' "$component"
grep 'ListCandidatesActionCall' "$component"
[ -f target/cozy/model-metadata.json ]
[ -f target/cozy/abi-manifest.json ]
[ -f target/textus-art-scene-0.1.0-SNAPSHOT.car ]
