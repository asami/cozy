package cozy.document

import io.circe.{Json, JsonObject}

/** Closed, content-free scaffold wire format. */
private[cozy] object CozyDocumentPatternDraft {
  private val _schema = "cozy.content-core.logic-tree-draft.v1"
  private def _fail(code: String, path: String, reason: String): Nothing =
    throw CozyDocumentLogicTree.LogicTreeFault(code, path, reason)
  private def _object(value: Json, path: String, keys: Set[String]): JsonObject = {
    val obj = value.asObject.getOrElse(_fail("PATTERN_SHAPE", path, "expected object"))
    if (obj.keys.toSet != keys) _fail("PATTERN_FIELDS", path, s"expected exactly ${keys.toVector.sorted.mkString(", ")}")
    obj
  }
  private def _string(obj: JsonObject, key: String, path: String): String =
    obj(key).flatMap(_.asString).getOrElse(_fail("PATTERN_SHAPE", s"$path.$key", "expected string"))
  private def _pattern(id: String, patterns: Vector[CozyDocumentPatternCatalog.Pattern], path: String): CozyDocumentPatternCatalog.Pattern =
    patterns.find(_.id == id).getOrElse(_fail("PATTERN_CATALOG", path, s"unknown pattern: $id"))
  private def _slots(pattern: CozyDocumentPatternCatalog.Pattern): Json = Json.fromValues(pattern.roles.map { role =>
    Json.obj("role" -> Json.fromString(role.id), "occurrence" -> Json.fromInt(1), "state" -> Json.fromString("unwritten"))
  })
  def create(catalog: String, version: String, document: String, section: String): Json = {
    if (catalog != CozyDocumentPatternCatalog.id || version != CozyDocumentPatternCatalog.version.toString)
      _fail("PATTERN_CATALOG", "$.catalog", "unknown catalog identity or version")
    val doc = _pattern(document, CozyDocumentPatternCatalog.documents, "$.documentPattern.pattern")
    val sec = _pattern(section, CozyDocumentPatternCatalog.sections, "$.sections[0].pattern")
    Json.obj(
      "schema" -> Json.fromString(_schema),
      "catalog" -> Json.obj("id" -> Json.fromString(catalog), "version" -> Json.fromInt(CozyDocumentPatternCatalog.version)),
      "documentPattern" -> Json.obj("pattern" -> Json.fromString(doc.id), "slots" -> _slots(doc)),
      "sections" -> Json.arr(Json.obj("path" -> Json.fromString("root"), "pattern" -> Json.fromString(sec.id),
        "question" -> Json.fromString("unwritten"), "answer" -> Json.fromString("unwritten"), "slots" -> _slots(sec)))
    )
  }
  def isDraft(value: Json): Boolean = value.hcursor.get[String]("schema").toOption.contains(_schema)
  def validate(value: Json): Unit = {
    val obj = _object(value, "$", Set("schema", "catalog", "documentPattern", "sections"))
    if (_string(obj, "schema", "$") != _schema) _fail("PATTERN_SCHEMA", "$.schema", "expected draft schema")
    val cat = _object(obj("catalog").get, "$.catalog", Set("id", "version"))
    if (_string(cat, "id", "$.catalog") != CozyDocumentPatternCatalog.id || cat("version").flatMap(_.asNumber).flatMap(_.toInt) != Some(CozyDocumentPatternCatalog.version))
      _fail("PATTERN_CATALOG", "$.catalog", "unknown catalog identity or version")
    val doc = _object(obj("documentPattern").get, "$.documentPattern", Set("pattern", "slots"))
    val docpattern = _pattern(_string(doc, "pattern", "$.documentPattern"), CozyDocumentPatternCatalog.documents, "$.documentPattern.pattern")
    if (doc("slots").get != _slots(docpattern)) _fail("PATTERN_SHAPE", "$.documentPattern.slots", "expected ordered initial slots")
    val sections = obj("sections").flatMap(_.asArray).getOrElse(_fail("PATTERN_SHAPE", "$.sections", "expected array"))
    if (sections.isEmpty) _fail("PATTERN_SHAPE", "$.sections", "expected nonempty array")
    val paths = sections.zipWithIndex.map { case (item, index) =>
      val path = s"$$.sections[$index]"
      val fields = _object(item, path, Set("path", "pattern", "question", "answer", "slots"))
      val name = _string(fields, "path", path)
      if (!name.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) _fail("PATTERN_ID", s"$path.path", "expected stable path")
      val pattern = _pattern(_string(fields, "pattern", path), CozyDocumentPatternCatalog.sections, s"$path.pattern")
      if (_string(fields, "question", path) != "unwritten" || _string(fields, "answer", path) != "unwritten" || fields("slots").get != _slots(pattern))
        _fail("PATTERN_SHAPE", path, "expected unwritten markers and ordered initial slots")
      name
    }
    if (paths.distinct.size != paths.size) _fail("PATTERN_IDENTITY", "$.sections", "duplicate path")
  }
  def rejectIncomplete(value: Json): Unit = {
    validate(value)
    _fail("draft-incomplete", "$.schema", "draft requires authored Core, assignments and wording")
  }
}
