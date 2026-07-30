#!/usr/bin/env sh
set -eu

mkdir -p out.d/src/main/scala/domain
cp -R generated.d/target/scala-3.3.8/src_managed/main/scala/domain/. out.d/src/main/scala/domain/
cp -R generated.d/src/main/scala/domain/. out.d/src/main/scala/domain/
rm -f out.d/src/main/cozy/sample.cml

mkdir -p out.d/src/main/scala/domain
cat > out.d/src/main/scala/domain/Main.scala <<'EOF'
package domain

import domain.entity.Facility
import org.goldenport.cncf.entity.EntityPersistent
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

object Main {
  def main(args: Array[String]): Unit = {
    val facilitycollection = EntityCollectionId("textus", "artscene", "facility")
    val exhibitcollection = EntityCollectionId("textus", "collection", "exhibit")
    val archivecollection = EntityCollectionId("textus", "archive", "exhibit")
    val original = Facility(
      EntityId("entry", "facility_1", facilitycollection),
      Some(EntityId("entry", "primary", exhibitcollection)),
      Vector(
        EntityId("entry", "collection", exhibitcollection),
        EntityId("entry", "archive", archivecollection)
      )
    )
    val persistent = summon[EntityPersistent[Facility]]
    val stored = persistent.toStoreRecord(original)
    val decoded = persistent.fromStoreRecord(stored).TAKE

    require(stored.getString("primaryExhibitId").contains(original.primaryExhibitId.get.value))
    require(stored.getString("id").contains(original.id.value))
    require(stored.getVector("relatedExhibitIds").exists(_.map(_.toString) == original.relatedExhibitIds.map(_.value)))
    require(decoded == original)
    require(EntityId("entry", "same", exhibitcollection).value != EntityId("entry", "same", archivecollection).value)
    println("EID03_GENERATED_ENTITY_EXACT_ROUNDTRIP_OK")
  }
}
EOF

cd out.d
sbt --batch compile
output="$(sbt --batch 'runMain domain.Main' 2>&1)"
printf '%s\n' "$output"
printf '%s\n' "$output" | grep -q 'EID03_GENERATED_ENTITY_EXACT_ROUNDTRIP_OK'
