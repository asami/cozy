package cozy.document

import io.circe.{Json, JsonObject}

/** Explicit, direct correspondence between authoring roles and reader content. */
private[cozy] object CozyDocumentPatternCorrespondence {
  import CozyDocumentDescriptionV2._
  import CozyDocumentPatternCodec._

  private final case class Target(kind: String, id: String, steps: Vector[String], claims: Vector[String])

  def validate(value: Json, envelope: Envelope, format: LocalizedFormat, document: ValidatedDocument): Unit = {
    def _fail_(path: String, reason: String): Nothing =
      throw CozyDocumentLogicTree.LogicTreeFault("PATTERN_CORRESPONDENCE", path, reason)
    def _object_(value: Json, path: String, keys: Set[String]): JsonObject = {
      val obj = value.asObject.getOrElse(_fail_(path, "expected object"))
      if (obj.keys.toSet != keys) _fail_(path, s"expected exactly ${keys.toVector.sorted.mkString(", ")}")
      obj
    }
    def _string_(obj: JsonObject, key: String, path: String): String =
      obj(key).flatMap(_.asString).filter(value => value.nonEmpty && value == value.trim)
        .getOrElse(_fail_(s"$path.$key", "expected nonblank trimmed string"))
    val obj = _object_(value, "$", Set("schema", "coreId", "documentId", "locale", "assignments"))
    if (_string_(obj, "schema", "$") != "cozy.document-pattern-correspondence.v1") _fail_("$.schema", "unknown correspondence schema")
    if (_string_(obj, "coreId", "$") != envelope.core.id || document.core != envelope.source || format.coreId != envelope.core.id)
      _fail_("$.coreId", "must bind the admitted Core")
    if (_string_(obj, "documentId", "$") != document.description.id) _fail_("$.documentId", "must bind the admitted Document")
    if (_string_(obj, "locale", "$") != document.description.locale || format.locale != document.description.locale)
      _fail_("$.locale", "must bind the Document and Format locale")
    def _targets_(sections: Vector[CozyDocumentDescriptionV2.Section]): Vector[Target] = sections.flatMap { section =>
      val self = Target("section", section.id, section.coreRefs.steps, section.coreRefs.claims)
      val blocks = section.blocks.flatMap {
        case value: Paragraph => Vector(Target("block", value.id, value.coreRefs.steps, value.coreRefs.claims))
        case value: ListBlock => Vector(Target("block", value.id, value.coreRefs.steps, value.coreRefs.claims)) ++
          value.items.map(item => Target("list-item", item.id, item.coreRefs.steps, item.coreRefs.claims))
        case value: Example => Vector(Target("block", value.id, value.coreRefs.steps, value.coreRefs.claims))
        case value: Note => Vector(Target("block", value.id, value.coreRefs.steps, value.coreRefs.claims))
        case value: Figure => Vector(Target("block", value.id, value.coreRefs.steps, value.coreRefs.claims))
        case value: Table => Vector(Target("block", value.id, value.coreRefs.steps, value.coreRefs.claims))
        case value: LogicalStructure => Vector(Target("block", value.id, Vector(value.stepRef), Vector.empty))
      }
      Vector(self) ++ blocks ++ _targets_(section.sections)
    }
    val targets = _targets_(document.description.document.sections)
    val roles = envelope.documentRoles.map(role => role.id -> ("step", role.step)) ++ envelope.sections.flatMap(_.roles.map { role =>
      role.target match {
        case ClaimTarget(id) => role.id -> ("claim", id)
        case ChildTarget(id, _) => role.id -> ("step", id)
      }
    })
    val items = obj("assignments").flatMap(_.asArray).getOrElse(_fail_("$.assignments", "expected array"))
    val seen = scala.collection.mutable.Set.empty[(String, String, String)]
    val covered = scala.collection.mutable.Set.empty[String]
    items.zipWithIndex.foreach { case (item, index) =>
      val path = s"$$.assignments[$index]"
      val entry = _object_(item, path, Set("roleAssignment", "targetKind", "targetId"))
      val role = _string_(entry, "roleAssignment", path)
      val kind = _string_(entry, "targetKind", path)
      val id = _string_(entry, "targetId", path)
      if (!seen.add((role, kind, id))) _fail_(path, "duplicate mapping")
      val reference = roles.find(_._1 == role).map(_._2).getOrElse(_fail_(s"$path.roleAssignment", s"unknown or stale role: $role"))
      val target = targets.find(value => value.kind == kind && value.id == id).getOrElse(_fail_(s"$path.targetId", s"unknown target $kind: $id"))
      if (!(if (reference._1 == "step") target.steps.contains(reference._2) else target.claims.contains(reference._2)))
        _fail_(path, s"target lacks direct ${reference._1} reference: ${reference._2}")
      covered += role
    }
    roles.foreach { case (id, _) => if (!covered.contains(id)) _fail_("$.assignments", s"missing mapping for role: $id") }
  }
}
