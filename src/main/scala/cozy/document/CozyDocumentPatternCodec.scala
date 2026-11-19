package cozy.document

import io.circe.{Json, JsonObject}
import java.nio.charset.StandardCharsets

/** Typed v2 envelope. Structural role and graph correspondence checks belong to P91-03. */
private[cozy] object CozyDocumentPatternCodec {
  import CozyDocumentLogicTree.{Core, Format, LogicTreeFault, ValidatedCore}
  sealed trait Target
  final case class ClaimTarget(id: String) extends Target
  final case class ChildTarget(id: String, explainsClaim: String) extends Target
  final case class DocumentRole(id: String, role: String, step: String)
  final case class SectionRole(id: String, role: String, target: Target)
  final case class InheritedContext(ancestor: String, role: String, target: String)
  final case class Section(step: String, pattern: String, question: String, answer: String, roles: Vector[SectionRole], inheritedContext: Vector[InheritedContext])
  final case class Envelope(source: ValidatedCore, documentPattern: String, documentRoles: Vector[DocumentRole], sections: Vector[Section]) {
    def core: Core = source.core
  }
  final case class QuestionBinding(id: String, text: String)
  final case class LocalizedFormat(coreId: String, locale: String, base: Format, questionBindings: Vector[QuestionBinding])

  private def _fail(code: String, path: String, reason: String): Nothing = throw LogicTreeFault(code, path, reason)
  private def _object(value: Json, path: String, keys: Set[String]): JsonObject = {
    val obj = value.asObject.getOrElse(_fail("PATTERN_SHAPE", path, "expected object"))
    if (obj.keys.toSet != keys) _fail("PATTERN_FIELDS", path, s"expected exactly ${keys.toVector.sorted.mkString(", ")}")
    obj
  }
  private def _field(obj: JsonObject, key: String): Json = obj(key).get
  private def _string(obj: JsonObject, key: String, path: String): String =
    _field(obj, key).asString.getOrElse(_fail("PATTERN_SHAPE", s"$path.$key", "expected string"))
  private def _id(obj: JsonObject, key: String, path: String): String = {
    val value = _string(obj, key, path)
    if (!value.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) _fail("PATTERN_ID", s"$path.$key", "expected stable identifier")
    value
  }
  private def _array(obj: JsonObject, key: String, path: String): Vector[Json] =
    _field(obj, key).asArray.map(_.toVector).getOrElse(_fail("PATTERN_SHAPE", s"$path.$key", "expected array"))
  private def _unique(values: Vector[String], path: String): Unit =
    values.groupBy(identity).collectFirst { case (value, items) if items.size > 1 => value }.foreach(value => _fail("PATTERN_IDENTITY", path, s"duplicate id: $value"))
  private def _pattern(value: String, patterns: Vector[CozyDocumentPatternCatalog.Pattern], path: String): Unit =
    if (!patterns.exists(_.id == value)) _fail("PATTERN_CATALOG", path, s"unknown pattern: $value")
  private def _target(value: Json, path: String): Target = {
    val obj = value.asObject.getOrElse(_fail("PATTERN_SHAPE", path, "expected target object"))
    obj("kind").flatMap(_.asString) match {
      case Some("claim") =>
        val fields = _object(value, path, Set("kind", "id"))
        ClaimTarget(_id(fields, "id", path))
      case Some("child") =>
        val fields = _object(value, path, Set("kind", "id", "explainsClaim"))
        ChildTarget(_id(fields, "id", path), _id(fields, "explainsClaim", path))
      case _ => _fail("PATTERN_REFERENCE", s"$path.kind", "expected claim or child")
    }
  }
  def decode(value: Json): Envelope = {
    val obj = _object(value, "$", Set("schema", "core", "catalog", "documentPattern", "sections"))
    if (_string(obj, "schema", "$") != "cozy.content-core.logic-tree.v2") _fail("PATTERN_SCHEMA", "$.schema", "expected v2 Core envelope")
    val cat = _object(_field(obj, "catalog"), "$.catalog", Set("id", "version"))
    if (_string(cat, "id", "$.catalog") != CozyDocumentPatternCatalog.id || _field(cat, "version").asNumber.flatMap(_.toInt) != Some(CozyDocumentPatternCatalog.version))
      _fail("PATTERN_CATALOG", "$.catalog", "unknown catalog identity or version")
    val corejson = _field(obj, "core")
    val core = CozyDocumentLogicTree.validateCore(corejson.noSpaces.getBytes(StandardCharsets.UTF_8), corejson)
    val doc = _object(_field(obj, "documentPattern"), "$.documentPattern", Set("pattern", "roles"))
    val docpattern = _id(doc, "pattern", "$.documentPattern")
    _pattern(docpattern, CozyDocumentPatternCatalog.documents, "$.documentPattern.pattern")
    val docroles = _array(doc, "roles", "$.documentPattern").zipWithIndex.map { case (item, index) =>
      val path = s"$$.documentPattern.roles[$index]"
      val fields = _object(item, path, Set("id", "role", "step"))
      DocumentRole(_id(fields, "id", path), _id(fields, "role", path), _id(fields, "step", path))
    }
    val docdefinition = CozyDocumentPatternCatalog.documents.find(_.id == docpattern).get
    docroles.foreach { role =>
      if (!docdefinition.roles.exists(_.id == role.role)) _fail("PATTERN_CATALOG", "$.documentPattern.roles", s"unknown document role: ${role.role}")
      if (!core.core.root.steps.exists(_.id == role.step)) _fail("PATTERN_REFERENCE", "$.documentPattern.roles", s"not a root child: ${role.step}")
    }
    val sections = _array(obj, "sections", "$").zipWithIndex.map { case (item, index) =>
      val path = s"$$.sections[$index]"
      val fields = _object(item, path, Set("step", "pattern", "question", "answer", "roles", "inheritedContext"))
      val pattern = _id(fields, "pattern", path)
      _pattern(pattern, CozyDocumentPatternCatalog.sections, s"$path.pattern")
      val roles = _array(fields, "roles", path).zipWithIndex.map { case (item, index) =>
        val rolepath = s"$path.roles[$index]"
        val role = _object(item, rolepath, Set("id", "role", "target"))
        SectionRole(_id(role, "id", rolepath), _id(role, "role", rolepath), _target(_field(role, "target"), s"$rolepath.target"))
      }
      val definition = CozyDocumentPatternCatalog.sections.find(_.id == pattern).get
      roles.foreach { role =>
        if (!definition.roles.exists(_.id == role.role)) _fail("PATTERN_CATALOG", s"$path.roles", s"unknown section role: ${role.role}")
      }
      val inherited = _array(fields, "inheritedContext", path).zipWithIndex.map { case (item, index) =>
        val inheritpath = s"$path.inheritedContext[$index]"
        val context = _object(item, inheritpath, Set("ancestor", "role", "target"))
        InheritedContext(_id(context, "ancestor", inheritpath), _id(context, "role", inheritpath), _id(context, "target", inheritpath))
      }
      Section(_id(fields, "step", path), pattern, _id(fields, "question", path), _id(fields, "answer", path), roles, inherited)
    }
    _unique(docroles.map(_.id) ++ sections.flatMap(_.roles.map(_.id)), "$.roles")
    _unique(sections.map(_.step), "$.sections")
    _unique(sections.map(_.question), "$.sections.question")
    if (sections.map(_.step) != core.depthFirstSteps.map(_.id))
      _fail("PATTERN_REFERENCE", "$.sections", "expected one section per Core Step in depth-first order")
    sections.zipWithIndex.foreach { case (section, index) =>
      val path = s"$$.sections[$index]"
      val step = core.depthFirstSteps(index)
      val claims = step.claims.map(_.id).toSet
      val children = step.steps.map(_.id).toSet
      if (!claims.contains(section.answer)) _fail("PATTERN_REFERENCE", s"$path.answer", s"not a claim of ${step.id}: ${section.answer}")
      section.roles.foreach { role =>
        role.target match {
          case ClaimTarget(id) if !claims.contains(id) => _fail("PATTERN_REFERENCE", s"$path.roles", s"not a local claim: $id")
          case ChildTarget(id, claim) if !children.contains(id) || !claims.contains(claim) =>
            _fail("PATTERN_REFERENCE", s"$path.roles", s"not an immediate child and local claim: $id, $claim")
          case _ => ()
        }
      }
    }
    Envelope(core, docpattern, docroles, sections)
  }
  def decodeFormat(value: Json, envelope: Envelope): LocalizedFormat = {
    val obj = _object(value, "$", Set("schema", "coreId", "locale", "base", "questionBindings"))
    if (_string(obj, "schema", "$") != "cozy.content-core.logic-tree-format.v2") _fail("PATTERN_SCHEMA", "$.schema", "expected v2 Format")
    val coreid = _id(obj, "coreId", "$")
    val locale = _string(obj, "locale", "$")
    if (!Set("ja", "en").contains(locale)) _fail("PATTERN_LOCALE", "$.locale", "expected ja or en")
    val base = CozyDocumentLogicTree.validateFormat(envelope.source.core, _field(obj, "base"))
    if (coreid != envelope.core.id || base.coreId != coreid || base.locale != locale) _fail("PATTERN_FORMAT", "$", "Core or locale mismatch")
    val bindings = _array(obj, "questionBindings", "$").zipWithIndex.map { case (item, index) =>
      val path = s"$$.questionBindings[$index]"
      val fields = _object(item, path, Set("id", "text"))
      val content = _string(fields, "text", path)
      if (content.isEmpty || content != content.trim) _fail("PATTERN_WORDING", s"$path.text", "expected nonblank trimmed wording")
      QuestionBinding(_id(fields, "id", path), content)
    }
    _unique(bindings.map(_.id), "$.questionBindings")
    if (bindings.map(_.id).toSet != envelope.sections.map(_.question).toSet) _fail("PATTERN_WORDING", "$.questionBindings", "question bindings must cover all questions")
    LocalizedFormat(coreid, locale, base, bindings)
  }
}
