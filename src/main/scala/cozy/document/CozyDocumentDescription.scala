package cozy.document

import io.circe.{Json, JsonObject}
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{Files, LinkOption, Path}
import java.security.MessageDigest
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import org.yaml.snakeyaml.{LoaderOptions, Yaml}
import scala.collection.mutable
import scala.util.control.NonFatal

/*
 * @since   Sep. 12, 2026
 * @version Sep. 12, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentDescription {
  final case class CoreBinding(id: String, identity: String)
  final case class DocumentBinding(id: String, identity: String)
  final case class References(
    steps: Vector[String],
    claims: Vector[String],
    nodes: Vector[String],
    relations: Vector[String],
    flows: Vector[String]
  )
  final case class ListItem(id: String, text: String, coreRefs: References)
  sealed trait Block { def id: String }
  final case class Paragraph(id: String, text: String, coreRefs: References) extends Block
  final case class ListBlock(id: String, items: Vector[ListItem], coreRefs: References) extends Block
  final case class Example(id: String, title: String, text: String, coreRefs: References) extends Block
  final case class Note(id: String, title: String, text: String, coreRefs: References) extends Block
  final case class LogicalStructure(id: String, stepRef: String) extends Block
  final case class Section(
    id: String,
    heading: String,
    coreRefs: References,
    blocks: Vector[Block],
    sections: Vector[Section]
  )
  final case class Document(title: String, sections: Vector[Section])
  final case class DocumentDescription(schema: String, id: String, core: CoreBinding, locale: String, document: Document)
  final case class SummaryUnit(id: String, heading: String, message: String, emphasis: String, coreRefs: References)
  final case class Summary(title: String, units: Vector[SummaryUnit])
  final case class SummaryDescription(
    schema: String,
    id: String,
    core: CoreBinding,
    document: DocumentBinding,
    locale: String,
    summary: Summary
  )
  final case class ValidatedDocument(
    core: CozyDocumentLogicTree.ValidatedCore,
    description: DocumentDescription,
    coreIdentity: String,
    documentIdentity: String
  )
  final case class ValidatedSummary(
    document: ValidatedDocument,
    description: SummaryDescription,
    summaryIdentity: String
  )

  final case class DescriptionFault(code: String, path: String, reason: String)
    extends IllegalArgumentException(s"$code path=$path reason=$reason")

  private final case class Coverage(
    steps: Set[String] = Set.empty,
    claims: Set[String] = Set.empty,
    nodes: Set[String] = Set.empty,
    relations: Set[String] = Set.empty,
    flows: Set[String] = Set.empty
  ) {
    def ++(rhs: Coverage): Coverage = Coverage(
      steps ++ rhs.steps,
      claims ++ rhs.claims,
      nodes ++ rhs.nodes,
      relations ++ rhs.relations,
      flows ++ rhs.flows
    )
  }

  private val _document_schema = "cozy.document-description.v1"
  private val _summary_schema = "cozy.summary-description.v1"
  private val _id_pattern = "[A-Za-z0-9][A-Za-z0-9._-]*".r
  private val _locale_pattern = "[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*".r
  private val _identity_pattern = "sha256:[0-9a-f]{64}".r
  private val _reference_fields = Set("steps", "claims", "nodes", "relations", "flows")

  def loadDocument(corePath: Path, documentPath: Path): ValidatedDocument = {
    val core = CozyDocumentLogicTree.loadCore(corePath)
    val documentpath = _admit_localized(documentPath, "document", "document.yaml")
    val documentbytes = _read_bytes(documentpath, "document")
    val description = _document_description(_load_document(documentpath, documentbytes, "document"), core)
    val identity = _identity(documentbytes)
    if (description.core.id != core.core.id)
      _fail("DESCRIPTION_DOCUMENT_CORE", "$.core.id", "must exactly equal the directly admitted Core id")
    if (description.core.identity != core.coreIdentity)
      _fail("DESCRIPTION_DOCUMENT_CORE", "$.core.identity", "must bind the direct Core byte SHA-256 identity")
    _validate_locale(documentpath, description.locale, "$.locale")
    ValidatedDocument(core, description, core.coreIdentity, identity)
  }

  def loadSummary(corePath: Path, documentPath: Path, summaryPath: Path): ValidatedSummary = {
    val document = loadDocument(corePath, documentPath)
    val summarypath = _admit_localized(summaryPath, "summary", "summary.yaml")
    val summarybytes = _read_bytes(summarypath, "summary")
    val description = _summary_description(_load_document(summarypath, summarybytes, "summary"), document.core)
    if (description.core.id != document.description.core.id || description.core.identity != document.coreIdentity)
      _fail("DESCRIPTION_SUMMARY_CORE", "$.core", "must bind the directly admitted Core id and byte identity")
    if (description.document.id != document.description.id || description.document.identity != document.documentIdentity)
      _fail("DESCRIPTION_SUMMARY_DOCUMENT", "$.document", "must bind the directly admitted Document id and byte identity")
    _validate_locale(summarypath, description.locale, "$.locale")
    ValidatedSummary(document, description, _identity(summarybytes))
  }

  private def _document_description(value: Json, core: CozyDocumentLogicTree.ValidatedCore): DocumentDescription = {
    val fields = _object(value, "$")
    _exact_fields(fields, Set("schema", "id", "core", "locale", "document"), "$")
    if (_string(fields, "schema", "$") != _document_schema)
      _fail("DESCRIPTION_DOCUMENT_SCHEMA", "$.schema", s"must be exactly ${_document_schema}")
    val ids = mutable.Set.empty[String]
    val content = _object(_field(fields, "document", "$"), "$.document")
    _exact_fields(content, Set("title", "sections"), "$.document")
    val parsed = _sections(_array(_field(content, "sections", "$.document"), "$.document.sections"), "$.document.sections", core, ids)
    val coverage = parsed.map(_._2).foldLeft(Coverage())(_ ++ _)
    _validate_document_coverage(coverage, core)
    DocumentDescription(
      _document_schema,
      _id(_string(fields, "id", "$"), "$.id"),
      _core_binding(_field(fields, "core", "$"), "$.core"),
      _locale(_string(fields, "locale", "$"), "$.locale"),
      Document(_text(_string(content, "title", "$.document"), "$.document.title"), parsed.map(_._1))
    )
  }

  private def _summary_description(value: Json, core: CozyDocumentLogicTree.ValidatedCore): SummaryDescription = {
    val fields = _object(value, "$")
    _exact_fields(fields, Set("schema", "id", "core", "document", "locale", "summary"), "$")
    if (_string(fields, "schema", "$") != _summary_schema)
      _fail("DESCRIPTION_SUMMARY_SCHEMA", "$.schema", s"must be exactly ${_summary_schema}")
    val content = _object(_field(fields, "summary", "$"), "$.summary")
    _exact_fields(content, Set("title", "units"), "$.summary")
    val units = _array(_field(content, "units", "$.summary"), "$.summary.units").zipWithIndex.map { case (item, index) =>
      _summary_unit(item, s"$$.summary.units[$index]", core)
    }
    _unique(units.map(_.id), "$.summary.units", "Summary unit id")
    SummaryDescription(
      _summary_schema,
      _id(_string(fields, "id", "$"), "$.id"),
      _core_binding(_field(fields, "core", "$"), "$.core"),
      _document_binding(_field(fields, "document", "$"), "$.document"),
      _locale(_string(fields, "locale", "$"), "$.locale"),
      Summary(_text(_string(content, "title", "$.summary"), "$.summary.title"), units)
    )
  }

  private def _sections(
    values: Vector[Json],
    path: String,
    core: CozyDocumentLogicTree.ValidatedCore,
    ids: mutable.Set[String]
  ): Vector[(Section, Coverage)] =
    values.zipWithIndex.map { case (value, index) => _section(value, s"$path[$index]", core, ids) }

  private def _section(
    value: Json,
    path: String,
    core: CozyDocumentLogicTree.ValidatedCore,
    ids: mutable.Set[String]
  ): (Section, Coverage) = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("id", "heading", "coreRefs", "blocks", "sections"), path)
    val id = _id(_string(fields, "id", path), s"$path.id")
    _add_id(ids, id, s"$path.id")
    val refs = _references(_field(fields, "coreRefs", path), s"$path.coreRefs", core, required = true)
    val blocks = _array(_field(fields, "blocks", path), s"$path.blocks").zipWithIndex.map { case (item, index) =>
      _block(item, s"$path.blocks[$index]", core, ids)
    }
    val sections = _sections(_array(_field(fields, "sections", path), s"$path.sections"), s"$path.sections", core, ids)
    val coverage = (Coverage() ++ _coverage(refs)) ++ blocks.map(_._2).foldLeft(Coverage())(_ ++ _) ++ sections.map(_._2).foldLeft(Coverage())(_ ++ _)
    (Section(id, _text(_string(fields, "heading", path), s"$path.heading"), refs, blocks.map(_._1), sections.map(_._1)), coverage)
  }

  private def _block(
    value: Json,
    path: String,
    core: CozyDocumentLogicTree.ValidatedCore,
    ids: mutable.Set[String]
  ): (Block, Coverage) = {
    val fields = _object(value, path)
    val id = _id(_string(fields, "id", path), s"$path.id")
    _add_id(ids, id, s"$path.id")
    _string(fields, "kind", path) match {
      case "paragraph" =>
        _exact_fields(fields, Set("id", "kind", "text", "coreRefs"), path)
        val refs = _references(_field(fields, "coreRefs", path), s"$path.coreRefs", core, required = true)
        (Paragraph(id, _text(_string(fields, "text", path), s"$path.text"), refs), _coverage(refs))
      case "list" =>
        _exact_fields(fields, Set("id", "kind", "items", "coreRefs"), path)
        val refs = _references(_field(fields, "coreRefs", path), s"$path.coreRefs", core, required = true)
        val items = _array(_field(fields, "items", path), s"$path.items").zipWithIndex.map { case (item, index) =>
          _list_item(item, s"$path.items[$index]", core, ids)
        }
        (ListBlock(id, items.map(_._1), refs), _coverage(refs) ++ items.map(_._2).foldLeft(Coverage())(_ ++ _))
      case "example" | "note" =>
        _exact_fields(fields, Set("id", "kind", "title", "text", "coreRefs"), path)
        val refs = _references(_field(fields, "coreRefs", path), s"$path.coreRefs", core, required = true)
        val title = _text(_string(fields, "title", path), s"$path.title")
        val text = _text(_string(fields, "text", path), s"$path.text")
        if (_string(fields, "kind", path) == "example") (Example(id, title, text, refs), _coverage(refs))
        else (Note(id, title, text, refs), _coverage(refs))
      case "logical-structure" =>
        _exact_fields(fields, Set("id", "kind", "stepRef"), path)
        val step = _id(_string(fields, "stepRef", path), s"$path.stepRef")
        if (!core.stepsById.contains(step)) _fail("DESCRIPTION_REFERENCE", s"$path.stepRef", "must resolve to a Core Step")
        (LogicalStructure(id, step), Coverage(steps = Set(step)))
      case _ => _fail("DESCRIPTION_BLOCK_KIND", s"$path.kind", "must be paragraph, list, example, note, or logical-structure")
    }
  }

  private def _list_item(
    value: Json,
    path: String,
    core: CozyDocumentLogicTree.ValidatedCore,
    ids: mutable.Set[String]
  ): (ListItem, Coverage) = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("id", "text", "coreRefs"), path)
    val id = _id(_string(fields, "id", path), s"$path.id")
    _add_id(ids, id, s"$path.id")
    val refs = _references(_field(fields, "coreRefs", path), s"$path.coreRefs", core, required = true)
    (ListItem(id, _text(_string(fields, "text", path), s"$path.text"), refs), _coverage(refs))
  }

  private def _summary_unit(value: Json, path: String, core: CozyDocumentLogicTree.ValidatedCore): SummaryUnit = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("id", "heading", "message", "emphasis", "coreRefs"), path)
    val emphasis = _string(fields, "emphasis", path)
    if (!Set("primary", "supporting", "conclusion").contains(emphasis))
      _fail("DESCRIPTION_EMPHASIS", s"$path.emphasis", "must be primary, supporting, or conclusion")
    SummaryUnit(
      _id(_string(fields, "id", path), s"$path.id"),
      _text(_string(fields, "heading", path), s"$path.heading"),
      _text(_string(fields, "message", path), s"$path.message"),
      emphasis,
      _references(_field(fields, "coreRefs", path), s"$path.coreRefs", core, required = true)
    )
  }

  private def _core_binding(value: Json, path: String): CoreBinding = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("id", "identity"), path)
    CoreBinding(_id(_string(fields, "id", path), s"$path.id"), _identity_value(_string(fields, "identity", path), s"$path.identity"))
  }

  private def _document_binding(value: Json, path: String): DocumentBinding = {
    val fields = _object(value, path)
    _exact_fields(fields, Set("id", "identity"), path)
    DocumentBinding(_id(_string(fields, "id", path), s"$path.id"), _identity_value(_string(fields, "identity", path), s"$path.identity"))
  }

  private def _references(
    value: Json,
    path: String,
    core: CozyDocumentLogicTree.ValidatedCore,
    required: Boolean
  ): References = {
    val fields = _object(value, path)
    _exact_fields(fields, _reference_fields, path)
    val steps = _ids(_field(fields, "steps", path), s"$path.steps")
    val claims = _ids(_field(fields, "claims", path), s"$path.claims")
    val nodes = _ids(_field(fields, "nodes", path), s"$path.nodes")
    val relations = _ids(_field(fields, "relations", path), s"$path.relations")
    val flows = _ids(_field(fields, "flows", path), s"$path.flows")
    _resolve(steps, core.stepsById.keySet, s"$path.steps", "Step")
    _resolve(claims, core.claimsById.keySet, s"$path.claims", "claim")
    _resolve(nodes, core.nodesById.keySet, s"$path.nodes", "node")
    _resolve(relations, core.relationsById.keySet, s"$path.relations", "Relation")
    _resolve(flows, core.flowsById.keySet, s"$path.flows", "Flow")
    val refs = References(steps, claims, nodes, relations, flows)
    if (required && _coverage(refs) == Coverage()) _fail("DESCRIPTION_REFERENCE", path, "must contain at least one resolved Core reference")
    refs
  }

  private def _ids(value: Json, path: String): Vector[String] = {
    val ids = _array(value, path).zipWithIndex.map { case (item, index) => _id(_string_value(item, s"$path[$index]"), s"$path[$index]") }
    _unique(ids, path, "reference id")
    ids
  }

  private def _resolve(values: Vector[String], admitted: Set[String], path: String, label: String): Unit =
    values.find(value => !admitted.contains(value)).foreach(value => _fail("DESCRIPTION_REFERENCE", path, s"$label does not resolve in the bound Core: $value"))

  private def _coverage(refs: References): Coverage =
    Coverage(refs.steps.toSet, refs.claims.toSet, refs.nodes.toSet, refs.relations.toSet, refs.flows.toSet)

  private def _validate_document_coverage(coverage: Coverage, core: CozyDocumentLogicTree.ValidatedCore): Unit =
    if (coverage.steps != core.stepsById.keySet || coverage.claims != core.claimsById.keySet || coverage.nodes != core.nodesById.keySet ||
      coverage.relations != core.relationsById.keySet || coverage.flows != core.flowsById.keySet)
      _fail("DESCRIPTION_COVERAGE", "$.document", "must explicitly cover every Core Step, claim, node, Relation, and Flow")

  private def _admit_localized(value: Path, label: String, basename: String): Path = {
    val path = _admit_file(value, label)
    if (path.getFileName.toString != basename)
      _fail("DESCRIPTION_PATH", s"$$.$label", s"must be a direct regular file named exactly $basename")
    path
  }

  private def _admit_file(value: Path, label: String): Path = {
    val path = try value.toAbsolutePath.normalize() catch { case NonFatal(_) => _fail("DESCRIPTION_PATH", s"$$.$label", "path is invalid") }
    if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      _fail("DESCRIPTION_PATH", s"$$.$label", "must be a direct regular non-symlink file")
    val parent = Option(path.getParent).getOrElse(_fail("DESCRIPTION_PATH", s"$$.$label", "has no safe parent"))
    if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS))
      _fail("DESCRIPTION_PATH", s"$$.$label", "parent must be a direct non-symlink directory")
    path
  }

  private def _validate_locale(path: Path, locale: String, fieldpath: String): Unit =
    if (path.getParent.getFileName.toString != locale)
      _fail("DESCRIPTION_LOCALE", fieldpath, "must exactly equal the direct locale-directory basename")

  private[cozy] def _load_document(path: Path, bytes: Array[Byte], label: String): Json =
    try {
      val text = _decode_utf8(bytes, s"$$.$label")
      val options = new LoaderOptions()
      options.setAllowDuplicateKeys(false)
      options.setAllowRecursiveKeys(false)
      options.setMaxAliasesForCollections(0)
      _reject_yaml_indirection(new Yaml(options).parse(new StringReader(text)), s"$$.$label")
      new Yaml(options).load(new StringReader(text))
      StructuredDocumentLoader.loadJson(InputSource(text, path.toUri)).take
    } catch {
      case fault: DescriptionFault => throw fault
      case NonFatal(_) => _fail("DESCRIPTION_SOURCE", s"$$.$label", "must be a well-formed YAML document without duplicate or lossy structure")
    }

  private def _reject_yaml_indirection(events: java.lang.Iterable[org.yaml.snakeyaml.events.Event], path: String): Unit = {
    val iterator = events.iterator
    while (iterator.hasNext) {
      iterator.next() match {
        case _: org.yaml.snakeyaml.events.AliasEvent =>
          _fail("DESCRIPTION_SOURCE", path, "YAML aliases are not admitted")
        case value: org.yaml.snakeyaml.events.NodeEvent if value.getAnchor != null =>
          _fail("DESCRIPTION_SOURCE", path, "YAML anchors are not admitted")
        case value: org.yaml.snakeyaml.events.ScalarEvent if value.getTag != null =>
          _fail("DESCRIPTION_SOURCE", path, "explicit YAML tags are not admitted")
        case value: org.yaml.snakeyaml.events.CollectionStartEvent if value.getTag != null =>
          _fail("DESCRIPTION_SOURCE", path, "explicit YAML tags are not admitted")
        case _ =>
      }
    }
  }

  private def _read_bytes(path: Path, label: String): Array[Byte] =
    try Files.readAllBytes(path) catch { case NonFatal(_) => _fail("DESCRIPTION_SOURCE", s"$$.$label", "cannot be read") }

  private def _decode_utf8(bytes: Array[Byte], path: String): String =
    try StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString catch {
      case NonFatal(_) => _fail("DESCRIPTION_SOURCE", path, "must be valid UTF-8")
    }

  private def _identity(bytes: Array[Byte]): String = "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).map(value => f"${value & 0xff}%02x").mkString
  private def _object(value: Json, path: String): JsonObject = value.asObject.getOrElse(_fail("DESCRIPTION_STRUCTURE", path, "must be an object"))
  private def _array(value: Json, path: String): Vector[Json] = value.asArray.map(_.toVector).getOrElse(_fail("DESCRIPTION_STRUCTURE", path, "must be an array"))
  private def _field(fields: JsonObject, name: String, path: String): Json = fields(name).getOrElse(_fail("DESCRIPTION_STRUCTURE", path, s"missing required field: $name"))
  private def _string(fields: JsonObject, name: String, path: String): String = _string_value(_field(fields, name, path), s"$path.$name")
  private def _string_value(value: Json, path: String): String = value.asString.getOrElse(_fail("DESCRIPTION_STRUCTURE", path, "must be a string"))
  private def _exact_fields(fields: JsonObject, expected: Set[String], path: String): Unit = if (fields.keys.toSet != expected) _fail("DESCRIPTION_FIELDS", path, s"must contain exactly: ${expected.toVector.sorted.mkString(", ")}")
  private def _id(value: String, path: String): String = if (_id_pattern.pattern.matcher(value).matches()) value else _fail("DESCRIPTION_ID", path, "must be a nonempty stable identifier")
  private def _text(value: String, path: String): String = if (value.nonEmpty && value == value.trim) value else _fail("DESCRIPTION_WORDING", path, "must be nonempty and trimmed")
  private def _locale(value: String, path: String): String = if (_locale_pattern.pattern.matcher(value).matches()) value else _fail("DESCRIPTION_LOCALE", path, "must be a declared BCP-47 language tag")
  private def _identity_value(value: String, path: String): String = if (_identity_pattern.pattern.matcher(value).matches()) value else _fail("DESCRIPTION_IDENTITY", path, "must be a sha256:<64 lowercase hexadecimal characters> identity")
  private def _add_id(ids: mutable.Set[String], id: String, path: String): Unit = if (!ids.add(id)) _fail("DESCRIPTION_IDENTITY", path, s"duplicate document identity: $id")
  private def _unique(values: Vector[String], path: String, label: String): Unit = values.groupBy(identity).collectFirst { case (value, duplicates) if duplicates.size > 1 => value }.foreach(value => _fail("DESCRIPTION_IDENTITY", path, s"duplicate $label: $value"))
  private def _fail(code: String, path: String, reason: String): Nothing = throw DescriptionFault(code, path, reason)
}
