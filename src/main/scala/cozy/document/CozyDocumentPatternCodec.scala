package cozy.document

import io.circe.{Json, JsonObject}
import java.nio.charset.StandardCharsets

/** Typed v2 envelope and structural validation. */
private[cozy] object CozyDocumentPatternCodec {
  import CozyDocumentLogicTree.{Core, Format, LogicTreeFault, ValidatedCore}
  sealed trait Target
  final case class ClaimTarget(id: String) extends Target
  final case class ChildTarget(id: String, explainsClaim: String) extends Target
  final case class DocumentRole(id: String, role: String, step: String)
  final case class SectionRole(id: String, role: String, target: Target)
  final case class InheritedContext(ancestor: String, role: String, target: String)
  final case class Section(step: String, pattern: String, question: String, answer: String, roles: Vector[SectionRole], inheritedContext: Vector[InheritedContext])
  final case class TermBinding(id: String, claimRef: String, space: String, term: String, concept: String, page: String, role: String, emphasis: Option[String], styleRole: Option[String])
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
  private def _roles(definition: CozyDocumentPatternCatalog.Pattern, values: Vector[String], path: String): Unit = {
    val positions = definition.roles.map(_.id)
    values.foreach { value =>
      if (!positions.contains(value)) _fail("PATTERN_CATALOG", path, s"unknown role: $value")
    }
    val indexes = values.map(positions.indexOf)
    if (indexes != indexes.sorted) _fail("PATTERN_ROLE_ORDER", path, "roles must follow catalog order and repetitions must be contiguous")
    definition.roles.foreach { role =>
      val count = values.count(_ == role.id)
      if (count < role.min || role.max.exists(count > _))
        _fail("PATTERN_ROLE_CARDINALITY", path, s"role ${role.id} requires ${role.min}..${role.max.fold("unbounded")(_.toString)}, found $count")
    }
  }
  def decode(value: Json): Envelope = {
    val keys = Set("schema", "core", "catalog", "documentPattern", "sections")
    val obj = _object(value, "$", if (value.hcursor.downField("terminology").succeeded) keys + "terminology" else keys)
    if (_string(obj, "schema", "$") != "cozy.content-core.logic-tree.v2") _fail("PATTERN_SCHEMA", "$.schema", "expected v2 Core envelope")
    val cat = _object(_field(obj, "catalog"), "$.catalog", Set("id", "version"))
    if (_string(cat, "id", "$.catalog") != CozyDocumentPatternCatalog.id || _field(cat, "version").asNumber.flatMap(_.toInt) != Some(CozyDocumentPatternCatalog.version))
      _fail("PATTERN_CATALOG", "$.catalog", "unknown catalog identity or version")
    val corejson = _field(obj, "core")
    val core = CozyDocumentLogicTree.validateCore(corejson.noSpaces.getBytes(StandardCharsets.UTF_8), corejson)
    val bindings = obj("terminology").toVector.flatMap { value =>
      val block = _object(value, "$.terminology", Set("version", "bindings"))
      if (_field(block, "version").asNumber.flatMap(_.toInt) != Some(1))
        _fail("PATTERN_TERMINOLOGY", "$.terminology.version", "expected version 1")
      _array(block, "bindings", "$.terminology").zipWithIndex.map { case (item, index) =>
        val path = s"$$.terminology.bindings[$index]"
        val fields = item.asObject.getOrElse(_fail("PATTERN_SHAPE", path, "expected object"))
        val required = Set("id", "claimRef", "space", "term", "concept", "page", "role")
        if (!required.subsetOf(fields.keys.toSet) || !(fields.keys.toSet -- required).subsetOf(Set("emphasis", "styleRole")))
          _fail("PATTERN_FIELDS", path, "invalid terminology binding fields")
        val id = _id(fields, "id", path)
        val claim = _id(fields, "claimRef", path)
        if (!core.claimsById.contains(claim)) _fail("PATTERN_REFERENCE", s"$path.claimRef", "unknown Core claim")
        val space = _string(fields, "space", path)
        if (!space.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) _fail("PATTERN_TERMINOLOGY", s"$path.space", "invalid Space ID")
        def _iri_(key: String): String = {
          val text = _string(fields, key, path)
          val valid = try { val uri = new java.net.URI(text); uri.isAbsolute && Set("http", "https").contains(uri.getScheme) && uri.getHost != null } catch { case _: java.net.URISyntaxException => false }
          if (!valid) _fail("PATTERN_TERMINOLOGY", s"$path.$key", "expected absolute HTTP(S) IRI")
          text
        }
        val role = _string(fields, "role", path)
        if (!Set("ordinary-term", "named-pattern").contains(role)) _fail("PATTERN_TERMINOLOGY", s"$path.role", "unknown role")
        val emphasis = fields("emphasis").map(_ => _string(fields, "emphasis", path))
        if (emphasis.exists(_ != "bold")) _fail("PATTERN_TERMINOLOGY", s"$path.emphasis", "expected bold")
        val style = fields("styleRole").map(_ => _id(fields, "styleRole", path))
        TermBinding(id, claim, space, _iri_("term"), _iri_("concept"), _iri_("page"), role, emphasis, style)
      }
    }
    _unique(bindings.map(_.id), "$.terminology.bindings")
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
    _roles(docdefinition, docroles.map(_.role), "$.documentPattern.roles")
    docroles.groupBy(_.role).foreach { case (role, assignments) =>
      val indexes = assignments.map(value => core.core.root.steps.indexWhere(_.id == value.step))
      if (indexes.distinct.size != indexes.size || indexes != indexes.sorted)
        _fail("PATTERN_ROLE_ORDER", "$.documentPattern.roles", s"role $role must reference distinct children in child order")
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
    val sectionmap = sections.map(value => value.step -> value).toMap
    val ancestors = scala.collection.mutable.Map.empty[String, Set[String]]
    def _collect_(step: CozyDocumentLogicTree.Step, parents: Set[String]): Unit = {
      ancestors.update(step.id, parents)
      step.steps.foreach(child => _collect_(child, parents + step.id))
    }
    _collect_(core.core.root, Set.empty)
    sections.zipWithIndex.foreach { case (section, index) =>
      val path = s"$$.sections[$index]"
      val step = core.depthFirstSteps(index)
      val definition = CozyDocumentPatternCatalog.sections.find(_.id == section.pattern).get
      _roles(definition, section.roles.map(_.role), s"$path.roles")
      val answerrole = definition.answerRole.get
      if (!section.roles.exists(role => role.role == answerrole && role.target == ClaimTarget(section.answer)))
        _fail("PATTERN_REFERENCE", s"$path.roles", s"answer role $answerrole must reference the local answer claim ${section.answer}")
      val eligible = Set("context", "problem", "purpose", "goal")
      _unique(section.inheritedContext.map(_.role), s"$path.inheritedContext")
      section.inheritedContext.foreach { context =>
        val allowed = definition.roles.exists(role => role.id == context.role && role.min == 0) && eligible.contains(context.role)
        val ancestor = sectionmap.get(context.ancestor)
        if (!allowed || section.roles.exists(_.role == context.role) || !ancestors(step.id).contains(context.ancestor) ||
            !ancestor.exists(_.roles.exists(role => role.role == context.role && role.target == ClaimTarget(context.target))))
          _fail("PATTERN_REFERENCE", s"$path.inheritedContext", s"invalid inherited context ${context.role} from ${context.ancestor}: ${context.target}")
      }
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
    Envelope(core.copy(terminology = bindings), docpattern, docroles, sections)
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
