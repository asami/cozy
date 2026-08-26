package cozy.media

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, Paths, StandardCopyOption}
import java.security.MessageDigest
import com.fasterxml.jackson.core.{JsonFactory, JsonToken}
import scala.collection.mutable
import scala.util.control.NonFatal

/*
 * @since   Aug. 26, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyVisualPage {
  sealed trait Document {
    def schema: String
    def id: String
    def pages: Vector[Page]
  }

  final case class CatalogReference(id: String, revision: Int)
  final case class SourceBinding(id: String, path: String)
  final case class Asset(id: String, path: String, mediaType: String, sha256: String)
  final case class Node(id: String, role: String, label: String, sourceRefs: Vector[String])
  final case class Relation(id: String, relationType: String, from: String, to: String, sourceRefs: Vector[String])
  final case class Logical(pattern: String, nodes: Vector[Node], relations: Vector[Relation])

  sealed trait ParameterValue
  final case class NodeReference(value: String) extends ParameterValue
  final case class BooleanParameter(value: Boolean) extends ParameterValue
  final case class StringParameter(value: String) extends ParameterValue
  final case class VisualParameter(name: String, value: ParameterValue)
  final case class Visual(pattern: String, parameters: Vector[VisualParameter])

  final case class Page(
    id: String,
    knowledge: String,
    language: String,
    catalog: CatalogReference,
    logical: Logical,
    visual: Visual,
    assets: Vector[Asset],
    sources: Vector[SourceBinding]
  ) extends Document {
    val schema = _page_schema
    def pages: Vector[Page] = Vector(this)
  }

  final case class PageSet(id: String, pages: Vector[Page]) extends Document {
    val schema = _set_schema
  }

  final case class RelationDefinition(id: String, direction: String)
  final case class NodeRole(role: String, min: Int, max: Int)
  final case class RelationRule(
    relation: String,
    fromRoles: Vector[String],
    toRoles: Vector[String],
    min: Int,
    max: Int,
    topology: String
  )
  final case class LogicalPattern(id: String, nodeRoles: Vector[NodeRole], relationRules: Vector[RelationRule])
  final case class ParameterDefinition(name: String, parameterType: String, required: Boolean)
  final case class VisualPattern(id: String, compatibleLogicalPatterns: Vector[String], parameters: Vector[ParameterDefinition])
  final case class Catalog(
    id: String,
    revision: Int,
    relations: Vector[RelationDefinition],
    logicalPatterns: Vector[LogicalPattern],
    visualPatterns: Vector[VisualPattern]
  )

  final case class ValidatedDocument(
    document: Document,
    catalog: Catalog,
    canonicalJson: String,
    catalogIdentity: String,
    logicalCatalogIdentity: String,
    logicalIdentities: Vector[(String, String)],
    visualPageIdentities: Vector[(String, String)],
    documentIdentity: String
  ) {
    def count: Int = document.pages.size
  }

  final case class CommandConfig(input: Path, catalog: Path, save: Option[Path] = None)

  final case class VisualPageFault(code: String, path: String, reason: String)
    extends IllegalArgumentException(s"$code path=$path reason=$reason")

  private sealed trait Value
  private final case class VObject(fields: Vector[(String, Value)]) extends Value
  private final case class VArray(values: Vector[Value]) extends Value
  private final case class VString(value: String) extends Value
  private final case class VNumber(value: Long) extends Value
  private final case class VBoolean(value: Boolean) extends Value
  private case object VNull extends Value
  private final case class YamlLine(indent: Int, value: String, line: Int)

  private val _page_schema = "cozy.visual-page.v1"
  private val _set_schema = "cozy.visual-page-set.v1"
  private val _catalog_schema = "cozy.presentation-semantics.catalog.v1"
  private val _token_pattern = "[A-Za-z0-9][A-Za-z0-9._-]*".r
  private val _sha256_pattern = "[0-9a-f]{64}".r
  private val _supported_extensions = Set(".json", ".yaml", ".yml", ".md")
  private val _catalog_extensions = Set(".json", ".yaml", ".yml")
  private val _relations = Vector(
    RelationDefinition("next", "from-to"),
    RelationDefinition("causes", "from-to"),
    RelationDefinition("depends-on", "from-to"),
    RelationDefinition("enables", "from-to"),
    RelationDefinition("maps-to", "from-to")
  )
  private val _logical_patterns = Vector(
    LogicalPattern(
      "sequence",
      Vector(NodeRole("step", 2, 8)),
      Vector(RelationRule("next", Vector("step"), Vector("step"), 1, 7, "linear"))
    ),
    LogicalPattern(
      "causal-chain",
      Vector(NodeRole("cause", 1, 7), NodeRole("effect", 1, 7)),
      Vector(
        RelationRule("causes", Vector("cause"), Vector("effect"), 1, 16, "acyclic"),
        RelationRule("enables", Vector("cause"), Vector("effect"), 1, 16, "acyclic")
      )
    ),
    LogicalPattern(
      "dependency-map",
      Vector(NodeRole("dependency", 1, 7), NodeRole("dependent", 1, 7)),
      Vector(RelationRule("depends-on", Vector("dependent"), Vector("dependency"), 1, 16, "acyclic"))
    ),
    LogicalPattern(
      "mapping",
      Vector(NodeRole("source", 1, 7), NodeRole("target", 1, 7)),
      Vector(RelationRule("maps-to", Vector("source"), Vector("target"), 1, 16, "bipartite"))
    )
  )
  private val _visual_patterns = Vector(
    VisualPattern(
      "flow-horizontal",
      Vector("causal-chain", "sequence"),
      Vector(ParameterDefinition("emphasisNode", "node-ref", false), ParameterDefinition("showRelationLabels", "boolean", false))
    ),
    VisualPattern(
      "flow-vertical",
      Vector("causal-chain", "dependency-map", "sequence"),
      Vector(ParameterDefinition("emphasisNode", "node-ref", false), ParameterDefinition("showRelationLabels", "boolean", false))
    ),
    VisualPattern(
      "mapping-columns",
      Vector("mapping"),
      Vector(
        ParameterDefinition("showRelationLabels", "boolean", false),
        ParameterDefinition("sourceColumnTitle", "string", true),
        ParameterDefinition("targetColumnTitle", "string", true)
      )
    )
  )

  def parseJson(text: String): Document = _parse_document(_parse_json(text, "$"), "$")
  def parseYaml(text: String): Document = _parse_document(_parse_yaml(text, "$"), "$")
  def parseMarkdown(text: String): Document = {
    val document = _parse_document(_parse_markdown(text, "$"), "$")
    if (_normalize_newlines(text) != canonicalMarkdown(document))
      _fail("VISUAL_PAGE_MARKDOWN_LOSSY", "$", "restricted Markdown must use the canonical grammar")
    document
  }

  def load(input: Path, catalog: Path): ValidatedDocument = {
    val inputpath = _direct_input(input, "input")
    val inputtext = _read_utf8(inputpath, "input")
    val parseddocument = _parse_document(_parse_source(inputpath, inputtext, "input"), "$")
    val validated = _validate_document_source(
      parseddocument,
      catalog,
      Option(inputpath.getParent).getOrElse(Paths.get(".").toAbsolutePath.normalize())
    )
    if (_extension(inputpath) == ".md" && _normalize_newlines(inputtext) != canonicalMarkdown(validated.document))
      _fail("VISUAL_PAGE_MARKDOWN_LOSSY", "$", "restricted Markdown must use the canonical grammar")
    validated
  }

  private[media] def validateEmbeddedPageSet(json: String, catalog: Path, sourceRoot: Path): ValidatedDocument = {
    val document = _parse_document(_parse_json(json, "$.visualPageSet"), "$.visualPageSet")
    document match {
      case _: Page => _fail("VISUAL_PAGE_EMBEDDED_PAGE_SET", "$.visualPageSet", "embedded migration target must be a Visual Page Set")
      case _: PageSet => _validate_document_source(document, catalog, sourceRoot)
    }
  }

  private def _validate_document_source(document: Document, catalog: Path, sourceroot: Path): ValidatedDocument = {
    val catalogpath = _direct_input(catalog, "catalog")
    val root = Option(sourceroot).map(_.toAbsolutePath.normalize()).getOrElse(
      _fail("VISUAL_PAGE_INPUT", "sourceRoot", "source root is required")
    )
    if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root))
      _fail("VISUAL_PAGE_INPUT", "sourceRoot", "source root must be an existing direct directory")
    val parsedcatalog = _parse_catalog(_parse_catalog_source(catalogpath), "$catalog")
    _validate_catalog(parsedcatalog, "$catalog")
    val normalized = _validate_document(document, parsedcatalog, root)
    val canonical = canonicalJson(normalized)
    val fullcatalogidentity = catalogIdentity(parsedcatalog)
    val logicalcatalogidentity = logicalCatalogIdentity(parsedcatalog)
    val logicalidentities = normalized.pages.map(page => page.id -> logicalIdentity(page, parsedcatalog))
    val visualidentities = normalized.pages.map(page => page.id -> visualPageIdentity(page, parsedcatalog))
    val documentidentity = normalized match {
      case _: Page => visualidentities.head._2
      case set: PageSet => visualPageSetIdentity(set, parsedcatalog)
    }
    ValidatedDocument(normalized, parsedcatalog, canonical, fullcatalogidentity, logicalcatalogidentity, logicalidentities, visualidentities, documentidentity)
  }

  def canonicalJson(document: Document): String = _canonical(_document_value(document))
  def canonicalYaml(document: Document): String = _document_value(document).asInstanceOf[VObject].fields.map {
    case (key, value) => s"$key: ${_canonical(value)}"
  }.mkString("\n") + "\n"

  def canonicalMarkdown(document: Document): String = document match {
    case page: Page =>
      val value = _page_value(page).asInstanceOf[VObject]
      val root = value.fields.toMap
      Vector(
        "# Cozy Visual Page",
        _markdown_line("schema", root("schema")),
        _markdown_line("version", root("version")),
        _markdown_line("id", root("id")),
        _markdown_line("knowledge", root("knowledge")),
        _markdown_line("language", root("language")),
        "## Catalog",
        _markdown_line("id", root("catalog").asInstanceOf[VObject].fields.toMap.apply("id")),
        _markdown_line("revision", root("catalog").asInstanceOf[VObject].fields.toMap.apply("revision")),
        "## Logical",
        _markdown_line("pattern", root("logical").asInstanceOf[VObject].fields.toMap.apply("pattern")),
        _markdown_line("nodes", root("logical").asInstanceOf[VObject].fields.toMap.apply("nodes")),
        _markdown_line("relations", root("logical").asInstanceOf[VObject].fields.toMap.apply("relations")),
        "## Visual",
        _markdown_line("pattern", root("visual").asInstanceOf[VObject].fields.toMap.apply("pattern")),
        _markdown_line("parameters", root("visual").asInstanceOf[VObject].fields.toMap.apply("parameters")),
        "## Assets",
        _markdown_line("assets", root("assets")),
        "## Sources",
        _markdown_line("sources", root("sources")),
        ""
      ).mkString("\n")
    case set: PageSet =>
      Vector(
        "# Cozy Visual Page Set",
        _markdown_line("schema", VString(_set_schema)),
        _markdown_line("version", VNumber(1)),
        _markdown_line("id", VString(set.id)),
        "## Pages",
        _markdown_line("pages", VArray(set.pages.map(_page_value))),
        ""
      ).mkString("\n")
  }

  def canonicalCatalogJson(catalog: Catalog): String = _canonical(_catalog_value(catalog))

  def catalogIdentity(catalog: Catalog): String = _identity(_catalog_value(catalog))

  def logicalCatalogIdentity(catalog: Catalog): String =
    _identity(VObject(_catalog_value(catalog).asInstanceOf[VObject].fields.take(6)))

  def logicalIdentity(page: Page, catalog: Catalog): String =
    _identity(VObject(Vector(
      "id" -> VString(page.id),
      "knowledge" -> VString(page.knowledge),
      "language" -> VString(page.language),
      "logicalCatalogIdentity" -> VString(logicalCatalogIdentity(catalog)),
      "logical" -> _logical_value(page.logical),
      "sources" -> VArray(page.sources.map(_source_value))
    )))

  def visualPageIdentity(page: Page, catalog: Catalog): String =
    _identity(VObject(Vector("catalogIdentity" -> VString(catalogIdentity(catalog)), "page" -> _page_value(page))))

  def visualPageSetIdentity(set: PageSet, catalog: Catalog): String =
    _identity(VObject(Vector("catalogIdentity" -> VString(catalogIdentity(catalog)), "set" -> _set_value(set))))

  def execute(args: List[String]): String = args match {
    case "validate" :: rest => validate(CommandConfig.create(rest, allowSave = false))
    case "inspect" :: rest => inspect(CommandConfig.create(rest, allowSave = false))
    case "convert" :: rest => convert(CommandConfig.create(rest, allowSave = true))
    case Nil => _fail("VISUAL_PAGE_COMMAND", "$command", "missing visual-page action")
    case action :: _ => _fail("VISUAL_PAGE_COMMAND", "$command", s"unsupported visual-page action: $action")
  }

  def validate(config: CommandConfig): String = _summary(load(config.input, config.catalog))

  def inspect(config: CommandConfig): String = {
    val validated = load(config.input, config.catalog)
    val header = Vector(
      s"schema: ${validated.document.schema}",
      "version: 1",
      s"count: ${validated.count}",
      s"catalogIdentity: ${validated.catalogIdentity}",
      s"logicalCatalogIdentity: ${validated.logicalCatalogIdentity}"
    )
    val pages = validated.document.pages.flatMap { page =>
      Vector(s"page: ${page.id}", s"  logicalPattern: ${page.logical.pattern}", s"  visualPattern: ${page.visual.pattern}") ++
        page.logical.nodes.map(node => s"  node: ${node.id} role=${node.role} label=${node.label} sources=${node.sourceRefs.mkString(",")}") ++
        page.logical.relations.map(relation => s"  relation: ${relation.id} type=${relation.relationType} from=${relation.from} to=${relation.to} sources=${relation.sourceRefs.mkString(",")}") ++
        page.visual.parameters.sortBy(_.name).map(parameter => s"  parameter: ${parameter.name}=${_parameter_text(parameter.value)}") ++
        Vector(
          s"  logicalIdentity: ${validated.logicalIdentities.toMap.apply(page.id)}",
          s"  visualPageIdentity: ${validated.visualPageIdentities.toMap.apply(page.id)}"
        )
    }
    (header ++ pages ++ Vector(s"documentIdentity: ${validated.documentIdentity}")).mkString("\n")
  }

  def convert(config: CommandConfig): String = {
    val output = config.save.getOrElse(_fail("VISUAL_PAGE_SAVE", "$save", "convert requires --save"))
    val extension = _extension(output)
    if (!_supported_extensions.contains(extension))
      _fail("VISUAL_PAGE_OUTPUT_FORMAT", "$save", "output must end in .json, .yaml, .yml, or .md")
    val validated = load(config.input, config.catalog)
    val text = extension match {
      case ".json" => validated.canonicalJson + "\n"
      case ".yaml" | ".yml" => canonicalYaml(validated.document)
      case ".md" => canonicalMarkdown(validated.document)
    }
    _atomic_write(output, text)
    _summary(validated) + s"\nformat: ${extension.drop(1)}"
  }

  object CommandConfig {
    def create(args: List[String], allowSave: Boolean): CommandConfig = {
      var input = Option.empty[Path]
      var catalog = Option.empty[Path]
      var save = Option.empty[Path]
      var rest = args
      while (rest.nonEmpty) {
        rest match {
          case option :: value :: tail if option == "--catalog" || option == "--save" =>
            if (option == "--catalog") {
              if (catalog.nonEmpty) _fail("VISUAL_PAGE_COMMAND", "$command.catalog", "--catalog may appear once")
              catalog = Some(_cli_path(value, "$command.catalog"))
            } else {
              if (!allowSave) _fail("VISUAL_PAGE_COMMAND", "$command.save", "--save is supported only by convert")
              if (save.nonEmpty) _fail("VISUAL_PAGE_COMMAND", "$command.save", "--save may appear once")
              save = Some(_cli_path(value, "$command.save"))
            }
            rest = tail
          case option :: tail if option.startsWith("--catalog=") =>
            if (catalog.nonEmpty) _fail("VISUAL_PAGE_COMMAND", "$command.catalog", "--catalog may appear once")
            catalog = Some(_cli_path(option.drop("--catalog=".length), "$command.catalog"))
            rest = tail
          case option :: tail if option.startsWith("--save=") =>
            if (!allowSave) _fail("VISUAL_PAGE_COMMAND", "$command.save", "--save is supported only by convert")
            if (save.nonEmpty) _fail("VISUAL_PAGE_COMMAND", "$command.save", "--save may appear once")
            save = Some(_cli_path(option.drop("--save=".length), "$command.save"))
            rest = tail
          case option :: _ if option.startsWith("--") =>
            _fail("VISUAL_PAGE_COMMAND", "$command", s"unsupported or valueless option: $option")
          case value :: tail =>
            if (input.nonEmpty) _fail("VISUAL_PAGE_COMMAND", "$command.input", "exactly one input file is required")
            input = Some(_cli_path(value, "$command.input"))
            rest = tail
        }
      }
      CommandConfig(
        input.getOrElse(_fail("VISUAL_PAGE_COMMAND", "$command.input", "missing input file")),
        catalog.getOrElse(_fail("VISUAL_PAGE_COMMAND", "$command.catalog", "missing --catalog")),
        save
      )
    }
  }

  private def _summary(validated: ValidatedDocument): String = {
    val identities = validated.document.pages.flatMap { page =>
      Vector(
        s"logicalIdentity.${page.id}: ${validated.logicalIdentities.toMap.apply(page.id)}",
        s"visualPageIdentity.${page.id}: ${validated.visualPageIdentities.toMap.apply(page.id)}"
      )
    }
    (Vector(
      s"schema: ${validated.document.schema}",
      "version: 1",
      s"count: ${validated.count}",
      s"catalogIdentity: ${validated.catalogIdentity}",
      s"logicalCatalogIdentity: ${validated.logicalCatalogIdentity}"
    ) ++ identities ++ Vector(s"documentIdentity: ${validated.documentIdentity}")).mkString("\n")
  }

  private def _parse_source(path: Path, text: String, source: String): Value = _extension(path) match {
    case ".json" => _parse_json(text, source)
    case ".yaml" | ".yml" => _parse_yaml(text, source)
    case ".md" => _parse_markdown(text, source)
    case _ => _fail("VISUAL_PAGE_FORMAT", source, "input must end in .json, .yaml, .yml, or .md")
  }

  private def _parse_catalog_source(path: Path): Value = {
    val extension = _extension(path)
    if (!_catalog_extensions.contains(extension))
      _fail("VISUAL_PAGE_CATALOG_FORMAT", "$catalog", "catalog must end in .json, .yaml, or .yml")
    _parse_source(path, _read_utf8(path, "catalog"), "catalog")
  }

  private def _parse_document(value: Value, path: String): Document = {
    val fields = _object(value, path)
    _string(_field(fields, "schema", path), s"$path.schema") match {
      case `_page_schema` => _parse_page(value, path)
      case `_set_schema` => _parse_set(value, path)
      case "cozy.slide-ir.v1" => _fail("VISUAL_PAGE_SCHEMA_UNSUPPORTED", s"$path.schema", "legacy Slide IR is unsupported; no Logical Pattern is inferred")
      case schema => _fail("VISUAL_PAGE_SCHEMA_UNSUPPORTED", s"$path.schema", s"unsupported schema: $schema")
    }
  }

  private def _parse_page(value: Value, path: String): Page = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("schema", "version", "id", "knowledge", "language", "catalog", "logical", "visual", "assets", "sources"), path)
    _schema_version(fields, _page_schema, path)
    val catalogfields = _object(_field(fields, "catalog", path), s"$path.catalog")
    _exact_fields(catalogfields, Vector("id", "revision"), s"$path.catalog")
    val logicalfields = _object(_field(fields, "logical", path), s"$path.logical")
    _exact_fields(logicalfields, Vector("pattern", "nodes", "relations"), s"$path.logical")
    val visualfields = _object(_field(fields, "visual", path), s"$path.visual")
    _exact_fields(visualfields, Vector("pattern", "parameters"), s"$path.visual")
    Page(
      _stable_id(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _required_text(_string(_field(fields, "knowledge", path), s"$path.knowledge"), s"$path.knowledge"),
      _required_text(_string(_field(fields, "language", path), s"$path.language"), s"$path.language"),
      CatalogReference(
        _stable_id(_string(_field(catalogfields, "id", s"$path.catalog"), s"$path.catalog.id"), s"$path.catalog.id"),
        _positive_int(_field(catalogfields, "revision", s"$path.catalog"), s"$path.catalog.revision")
      ),
      Logical(
        _stable_id(_string(_field(logicalfields, "pattern", s"$path.logical"), s"$path.logical.pattern"), s"$path.logical.pattern"),
        _array(_field(logicalfields, "nodes", s"$path.logical"), s"$path.logical.nodes").zipWithIndex.map { case (item, index) => _parse_node(item, s"$path.logical.nodes[$index]") },
        _array(_field(logicalfields, "relations", s"$path.logical"), s"$path.logical.relations").zipWithIndex.map { case (item, index) => _parse_relation(item, s"$path.logical.relations[$index]") }
      ),
      Visual(
        _stable_id(_string(_field(visualfields, "pattern", s"$path.visual"), s"$path.visual.pattern"), s"$path.visual.pattern"),
        _parse_parameters(_object(_field(visualfields, "parameters", s"$path.visual"), s"$path.visual.parameters"), s"$path.visual.parameters")
      ),
      _array(_field(fields, "assets", path), s"$path.assets").zipWithIndex.map { case (item, index) => _parse_asset(item, s"$path.assets[$index]") },
      _array(_field(fields, "sources", path), s"$path.sources").zipWithIndex.map { case (item, index) => _parse_source_binding(item, s"$path.sources[$index]") }
    )
  }

  private def _parse_set(value: Value, path: String): PageSet = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("schema", "version", "id", "pages"), path)
    _schema_version(fields, _set_schema, path)
    val pages = _array(_field(fields, "pages", path), s"$path.pages").zipWithIndex.map { case (item, index) => _parse_page(item, s"$path.pages[$index]") }
    if (pages.isEmpty) _fail("VISUAL_PAGE_SET_EMPTY", s"$path.pages", "page set must be nonempty")
    PageSet(_stable_id(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"), pages)
  }

  private def _parse_node(value: Value, path: String): Node = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "role", "label", "sourceRefs"), path)
    Node(
      _stable_id(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _stable_id(_string(_field(fields, "role", path), s"$path.role"), s"$path.role"),
      _required_text(_string(_field(fields, "label", path), s"$path.label"), s"$path.label"),
      _string_array(_field(fields, "sourceRefs", path), s"$path.sourceRefs")
    )
  }

  private def _parse_relation(value: Value, path: String): Relation = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "type", "from", "to", "sourceRefs"), path)
    Relation(
      _stable_id(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _stable_id(_string(_field(fields, "type", path), s"$path.type"), s"$path.type"),
      _stable_id(_string(_field(fields, "from", path), s"$path.from"), s"$path.from"),
      _stable_id(_string(_field(fields, "to", path), s"$path.to"), s"$path.to"),
      _string_array(_field(fields, "sourceRefs", path), s"$path.sourceRefs")
    )
  }

  private def _parse_asset(value: Value, path: String): Asset = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "path", "mediaType", "sha256"), path)
    val sha256 = _string(_field(fields, "sha256", path), s"$path.sha256")
    if (!_sha256_pattern.pattern.matcher(sha256).matches)
      _fail("VISUAL_PAGE_ASSET_SHA256", s"$path.sha256", "must be 64 lowercase hexadecimal characters")
    Asset(
      _stable_id(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _required_text(_string(_field(fields, "path", path), s"$path.path"), s"$path.path"),
      _required_text(_string(_field(fields, "mediaType", path), s"$path.mediaType"), s"$path.mediaType"),
      sha256
    )
  }

  private def _parse_source_binding(value: Value, path: String): SourceBinding = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "path"), path)
    SourceBinding(
      _stable_id(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _required_text(_string(_field(fields, "path", path), s"$path.path"), s"$path.path")
    )
  }

  private def _parse_parameters(fields: Vector[(String, Value)], path: String): Vector[VisualParameter] = {
    _unique(fields.map(_._1), path, "parameter")
    fields.map { case (name, value) =>
      _stable_id(name, s"$path.$name")
      val parameter = value match {
        case VString(text) => StringParameter(text)
        case VBoolean(flag) => BooleanParameter(flag)
        case _ => _fail("VISUAL_PAGE_PARAMETER_TYPE", s"$path.$name", "parameter values must be string or boolean before catalog type validation")
      }
      VisualParameter(name, parameter)
    }
  }

  private def _parse_catalog(value: Value, path: String): Catalog = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("schema", "version", "id", "revision", "relations", "logicalPatterns", "visualPatterns"), path)
    _schema_version(fields, _catalog_schema, path)
    Catalog(
      _stable_id(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _positive_int(_field(fields, "revision", path), s"$path.revision"),
      _array(_field(fields, "relations", path), s"$path.relations").zipWithIndex.map { case (item, index) => _parse_relation_definition(item, s"$path.relations[$index]") },
      _array(_field(fields, "logicalPatterns", path), s"$path.logicalPatterns").zipWithIndex.map { case (item, index) => _parse_logical_pattern(item, s"$path.logicalPatterns[$index]") },
      _array(_field(fields, "visualPatterns", path), s"$path.visualPatterns").zipWithIndex.map { case (item, index) => _parse_visual_pattern(item, s"$path.visualPatterns[$index]") }
    )
  }

  private def _parse_relation_definition(value: Value, path: String): RelationDefinition = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "direction"), path)
    RelationDefinition(
      _stable_id(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _stable_id(_string(_field(fields, "direction", path), s"$path.direction"), s"$path.direction")
    )
  }

  private def _parse_logical_pattern(value: Value, path: String): LogicalPattern = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "nodeRoles", "relationRules"), path)
    LogicalPattern(
      _stable_id(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _array(_field(fields, "nodeRoles", path), s"$path.nodeRoles").zipWithIndex.map { case (item, index) => _parse_node_role(item, s"$path.nodeRoles[$index]") },
      _array(_field(fields, "relationRules", path), s"$path.relationRules").zipWithIndex.map { case (item, index) => _parse_relation_rule(item, s"$path.relationRules[$index]") }
    )
  }

  private def _parse_node_role(value: Value, path: String): NodeRole = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("role", "min", "max"), path)
    NodeRole(
      _stable_id(_string(_field(fields, "role", path), s"$path.role"), s"$path.role"),
      _positive_or_zero_int(_field(fields, "min", path), s"$path.min"),
      _positive_or_zero_int(_field(fields, "max", path), s"$path.max")
    )
  }

  private def _parse_relation_rule(value: Value, path: String): RelationRule = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("relation", "fromRoles", "toRoles", "min", "max", "topology"), path)
    RelationRule(
      _stable_id(_string(_field(fields, "relation", path), s"$path.relation"), s"$path.relation"),
      _string_array(_field(fields, "fromRoles", path), s"$path.fromRoles"),
      _string_array(_field(fields, "toRoles", path), s"$path.toRoles"),
      _positive_or_zero_int(_field(fields, "min", path), s"$path.min"),
      _positive_or_zero_int(_field(fields, "max", path), s"$path.max"),
      _stable_id(_string(_field(fields, "topology", path), s"$path.topology"), s"$path.topology")
    )
  }

  private def _parse_visual_pattern(value: Value, path: String): VisualPattern = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "compatibleLogicalPatterns", "parameters"), path)
    VisualPattern(
      _stable_id(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _string_array(_field(fields, "compatibleLogicalPatterns", path), s"$path.compatibleLogicalPatterns"),
      _array(_field(fields, "parameters", path), s"$path.parameters").zipWithIndex.map { case (item, index) => _parse_parameter_definition(item, s"$path.parameters[$index]") }
    )
  }

  private def _parse_parameter_definition(value: Value, path: String): ParameterDefinition = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("name", "type", "required"), path)
    ParameterDefinition(
      _stable_id(_string(_field(fields, "name", path), s"$path.name"), s"$path.name"),
      _stable_id(_string(_field(fields, "type", path), s"$path.type"), s"$path.type"),
      _boolean(_field(fields, "required", path), s"$path.required")
    )
  }

  private def _validate_catalog(catalog: Catalog, path: String): Unit = {
    if (catalog.relations != _relations || catalog.logicalPatterns != _logical_patterns || catalog.visualPatterns != _visual_patterns)
      _fail("VISUAL_PAGE_CATALOG_CORE", path, "catalog must be the exact closed core relation, logical-pattern, and visual-pattern catalog in canonical array order")
  }

  private def _validate_document(document: Document, catalog: Catalog, root: Path): Document = document match {
      case page: Page => _validate_page(page, catalog, root, "$")
      case set: PageSet =>
        _unique(set.pages.map(_.id), "$.pages", "page id")
        val pages = set.pages.zipWithIndex.map { case (page, index) =>
          if (page.catalog != set.pages.head.catalog)
            _fail("VISUAL_PAGE_SET_CATALOG", "$.pages", "all pages must select one identical catalog id and revision")
          _validate_page(page, catalog, root, s"$$.pages[$index]")
        }
        set.copy(pages = pages)
    }

  private def _validate_page(page: Page, catalog: Catalog, root: Path, path: String): Page = {
    if (page.catalog.id != catalog.id || page.catalog.revision != catalog.revision)
      _fail("VISUAL_PAGE_CATALOG_RESOLUTION", s"$path.catalog", "page catalog id and revision must match the separately supplied catalog")
    _unique(page.logical.nodes.map(_.id), s"$path.logical.nodes", "node id")
    _unique(page.logical.relations.map(_.id), s"$path.logical.relations", "relation id")
    _unique(page.sources.map(_.id), s"$path.sources", "source id")
    _unique(page.assets.map(_.id), s"$path.assets", "asset id")
    page.sources.foreach(source => _safe_direct_file(root, source.path, s"$path.sources.${source.id}.path"))
    page.assets.foreach { asset =>
      val resolved = _safe_direct_file(root, asset.path, s"$path.assets.${asset.id}.path")
      val digest = _sha256(resolved)
      if (digest != asset.sha256)
        _fail("VISUAL_PAGE_ASSET_DIGEST", s"$path.assets.${asset.id}.sha256", s"declared digest does not match direct file bytes")
    }
    val sourceids = page.sources.map(_.id).toSet
    val nodes = page.logical.nodes.map(node => node.id -> node).toMap
    page.logical.nodes.foreach { node =>
      _unique(node.sourceRefs, s"$path.logical.nodes.${node.id}.sourceRefs", "source reference")
      node.sourceRefs.foreach { ref => if (!sourceids.contains(ref)) _fail("VISUAL_PAGE_SOURCE_REFERENCE", s"$path.logical.nodes.${node.id}.sourceRefs", s"unknown source id: $ref") }
    }
    page.logical.relations.foreach { relation =>
      _unique(relation.sourceRefs, s"$path.logical.relations.${relation.id}.sourceRefs", "source reference")
      if (!nodes.contains(relation.from) || !nodes.contains(relation.to))
        _fail("VISUAL_PAGE_RELATION_ENDPOINT", s"$path.logical.relations.${relation.id}", "from and to must resolve exactly once to declared nodes")
      relation.sourceRefs.foreach { ref => if (!sourceids.contains(ref)) _fail("VISUAL_PAGE_SOURCE_REFERENCE", s"$path.logical.relations.${relation.id}.sourceRefs", s"unknown source id: $ref") }
    }
    val logicalpattern = catalog.logicalPatterns.find(_.id == page.logical.pattern).getOrElse(
      _fail("VISUAL_PAGE_LOGICAL_PATTERN", s"$path.logical.pattern", s"unknown logical pattern: ${page.logical.pattern}")
    )
    _validate_logical(page, logicalpattern, nodes, path)
    val visualpattern = catalog.visualPatterns.find(_.id == page.visual.pattern).getOrElse(
      _fail("VISUAL_PAGE_VISUAL_PATTERN", s"$path.visual.pattern", s"unknown visual pattern: ${page.visual.pattern}")
    )
    if (!visualpattern.compatibleLogicalPatterns.contains(page.logical.pattern))
      _fail("VISUAL_PAGE_VISUAL_COMPATIBILITY", s"$path.visual.pattern", s"visual pattern ${page.visual.pattern} is incompatible with ${page.logical.pattern}")
    page.copy(visual = _validate_parameters(page, visualpattern, nodes.keySet, path))
  }

  private def _validate_logical(page: Page, pattern: LogicalPattern, nodes: Map[String, Node], path: String): Unit = {
    val allowedroles = pattern.nodeRoles.map(_.role).toSet
    page.logical.nodes.foreach { node =>
      if (!allowedroles.contains(node.role)) _fail("VISUAL_PAGE_NODE_ROLE", s"$path.logical.nodes.${node.id}.role", s"role is not admitted by ${pattern.id}: ${node.role}")
    }
    pattern.nodeRoles.foreach { role =>
      val count = page.logical.nodes.count(_.role == role.role)
      if (count < role.min || count > role.max)
        _fail("VISUAL_PAGE_NODE_CARDINALITY", s"$path.logical.nodes", s"role ${role.role} requires ${role.min}..${role.max} nodes, found $count")
    }
    if (Set("causal-chain", "dependency-map", "mapping").contains(pattern.id) && (page.logical.nodes.size < 2 || page.logical.nodes.size > 8))
      _fail("VISUAL_PAGE_NODE_CARDINALITY", s"$path.logical.nodes", s"${pattern.id} requires total node cardinality 2..8")
    val rules = pattern.relationRules.map(rule => rule.relation -> rule).toMap
    page.logical.relations.foreach { relation =>
      val rule = rules.getOrElse(relation.relationType,
        _fail("VISUAL_PAGE_RELATION_TYPE", s"$path.logical.relations.${relation.id}.type", s"relation is not admitted by ${pattern.id}: ${relation.relationType}")
      )
      if (!rule.fromRoles.contains(nodes(relation.from).role) || !rule.toRoles.contains(nodes(relation.to).role))
        _fail("VISUAL_PAGE_RELATION_DIRECTION", s"$path.logical.relations.${relation.id}", s"relation endpoints do not satisfy ${relation.relationType} role direction")
    }
    pattern.relationRules.foreach { rule =>
      val count = page.logical.relations.count(_.relationType == rule.relation)
      if (count < rule.min || count > rule.max)
        _fail("VISUAL_PAGE_RELATION_CARDINALITY", s"$path.logical.relations", s"relation ${rule.relation} requires ${rule.min}..${rule.max}, found $count")
      val duplicates = page.logical.relations.filter(_.relationType == rule.relation).groupBy(relation => relation.from -> relation.to).collect { case (endpoint, values) if values.size > 1 => endpoint }
      if (duplicates.nonEmpty)
        _fail("VISUAL_PAGE_RELATION_DUPLICATE_ENDPOINT", s"$path.logical.relations", s"duplicate ${rule.relation} endpoint pair: ${duplicates.head._1}->${duplicates.head._2}")
    }
    pattern.relationRules.map(_.topology).distinct.foreach {
      case "linear" => _validate_linear(page.logical, path)
      case "acyclic" => _validate_acyclic(page.logical, path)
      case "bipartite" => ()
      case topology => _fail("VISUAL_PAGE_TOPOLOGY", s"$path.logical", s"unsupported topology: $topology")
    }
  }

  private def _validate_linear(logical: Logical, path: String): Unit = {
    val outgoing = logical.relations.groupBy(_.from).mapValues(_.size).withDefaultValue(0)
    val incoming = logical.relations.groupBy(_.to).mapValues(_.size).withDefaultValue(0)
    if (logical.relations.size != logical.nodes.size - 1 || logical.nodes.exists(node => incoming(node.id) > 1 || outgoing(node.id) > 1))
      _fail("VISUAL_PAGE_LINEAR_TOPOLOGY", s"$path.logical", "linear pattern requires one directed chain with one incoming/outgoing edge at most")
    val starts = logical.nodes.filter(node => incoming(node.id) == 0)
    val ends = logical.nodes.filter(node => outgoing(node.id) == 0)
    if (starts.size != 1 || ends.size != 1)
      _fail("VISUAL_PAGE_LINEAR_TOPOLOGY", s"$path.logical", "linear pattern requires exactly one start and one end")
    val next = logical.relations.map(relation => relation.from -> relation.to).toMap
    var seen = Set.empty[String]
    var current = starts.head.id
    while (current.nonEmpty && !seen.contains(current)) {
      seen += current
      current = next.getOrElse(current, "")
    }
    if (seen.size != logical.nodes.size || current.nonEmpty)
      _fail("VISUAL_PAGE_LINEAR_TOPOLOGY", s"$path.logical", "linear pattern must be one connected acyclic chain")
  }

  private def _validate_acyclic(logical: Logical, path: String): Unit = {
    val adjacency = logical.relations.groupBy(_.from).mapValues(_.map(_.to)).withDefaultValue(Vector.empty)
    var visiting = Set.empty[String]
    var visited = Set.empty[String]
    def _visit_(id: String): Unit = {
      if (visiting.contains(id)) _fail("VISUAL_PAGE_ACYCLIC_TOPOLOGY", s"$path.logical.relations", "relation graph contains a cycle")
      if (!visited.contains(id)) {
        visiting += id
        adjacency(id).foreach(_visit_)
        visiting -= id
        visited += id
      }
    }
    logical.nodes.foreach(node => _visit_(node.id))
  }

  private def _validate_parameters(page: Page, pattern: VisualPattern, nodeids: Set[String], path: String): Visual = {
    val supplied = page.visual.parameters.map(parameter => parameter.name -> parameter).toMap
    val definitions = pattern.parameters.map(definition => definition.name -> definition).toMap
    val resolved = page.visual.parameters.map { parameter =>
      val definition = definitions.getOrElse(parameter.name,
        _fail("VISUAL_PAGE_PARAMETER_UNKNOWN", s"$path.visual.parameters.${parameter.name}", s"parameter is not admitted by ${pattern.id}")
      )
      val value = (definition.parameterType, parameter.value) match {
        case ("node-ref", StringParameter(value)) if nodeids.contains(value) => NodeReference(value)
        case ("node-ref", NodeReference(value)) if nodeids.contains(value) => NodeReference(value)
        case ("node-ref", StringParameter(value)) => _fail("VISUAL_PAGE_PARAMETER_NODE_REF", s"$path.visual.parameters.${parameter.name}", s"node-ref does not resolve exactly once: $value")
        case ("node-ref", NodeReference(value)) => _fail("VISUAL_PAGE_PARAMETER_NODE_REF", s"$path.visual.parameters.${parameter.name}", s"node-ref does not resolve exactly once: $value")
        case ("node-ref", _) => _fail("VISUAL_PAGE_PARAMETER_TYPE", s"$path.visual.parameters.${parameter.name}", "node-ref requires a string node id")
        case ("boolean", value @ BooleanParameter(_)) => value
        case ("boolean", _) => _fail("VISUAL_PAGE_PARAMETER_TYPE", s"$path.visual.parameters.${parameter.name}", "boolean parameter requires JSON boolean")
        case ("string", value @ StringParameter(text)) if text.nonEmpty && text == text.trim => value
        case ("string", StringParameter(_)) => _fail("VISUAL_PAGE_PARAMETER_STRING", s"$path.visual.parameters.${parameter.name}", "string parameter must be nonempty and trimmed")
        case ("string", _) => _fail("VISUAL_PAGE_PARAMETER_TYPE", s"$path.visual.parameters.${parameter.name}", "string parameter requires JSON string")
        case _ => _fail("VISUAL_PAGE_PARAMETER_TYPE", s"$path.visual.parameters.${parameter.name}", "unsupported catalog parameter type")
      }
      VisualParameter(parameter.name, value)
    }
    pattern.parameters.filter(_.required).foreach { definition =>
      if (!supplied.contains(definition.name)) _fail("VISUAL_PAGE_PARAMETER_REQUIRED", s"$path.visual.parameters", s"missing required parameter: ${definition.name}")
    }
    Visual(page.visual.pattern, resolved)
  }

  private def _page_value(page: Page): Value = VObject(Vector(
    "schema" -> VString(_page_schema),
    "version" -> VNumber(1),
    "id" -> VString(page.id),
    "knowledge" -> VString(page.knowledge),
    "language" -> VString(page.language),
    "catalog" -> VObject(Vector("id" -> VString(page.catalog.id), "revision" -> VNumber(page.catalog.revision))),
    "logical" -> _logical_value(page.logical),
    "visual" -> _visual_value(page.visual),
    "assets" -> VArray(page.assets.map(_asset_value)),
    "sources" -> VArray(page.sources.map(_source_value))
  ))

  private def _set_value(set: PageSet): Value = VObject(Vector(
    "schema" -> VString(_set_schema),
    "version" -> VNumber(1),
    "id" -> VString(set.id),
    "pages" -> VArray(set.pages.map(_page_value))
  ))

  private def _document_value(document: Document): Value = document match {
    case page: Page => _page_value(page)
    case set: PageSet => _set_value(set)
  }

  private def _logical_value(logical: Logical): Value = VObject(Vector(
    "pattern" -> VString(logical.pattern),
    "nodes" -> VArray(logical.nodes.map(node => VObject(Vector(
      "id" -> VString(node.id), "role" -> VString(node.role), "label" -> VString(node.label), "sourceRefs" -> VArray(node.sourceRefs.map(VString))
    )))),
    "relations" -> VArray(logical.relations.map(relation => VObject(Vector(
      "id" -> VString(relation.id), "type" -> VString(relation.relationType), "from" -> VString(relation.from), "to" -> VString(relation.to), "sourceRefs" -> VArray(relation.sourceRefs.map(VString))
    )) )
  )))

  private def _visual_value(visual: Visual): Value = VObject(Vector(
    "pattern" -> VString(visual.pattern),
    "parameters" -> VObject(visual.parameters.sortBy(_.name).map(parameter => parameter.name -> _parameter_value(parameter.value)))
  ))

  private def _parameter_value(value: ParameterValue): Value = value match {
    case NodeReference(item) => VString(item)
    case BooleanParameter(item) => VBoolean(item)
    case StringParameter(item) => VString(item)
  }

  private def _asset_value(asset: Asset): Value = VObject(Vector(
    "id" -> VString(asset.id), "path" -> VString(asset.path), "mediaType" -> VString(asset.mediaType), "sha256" -> VString(asset.sha256)
  ))

  private def _source_value(source: SourceBinding): Value = VObject(Vector("id" -> VString(source.id), "path" -> VString(source.path)))

  private def _catalog_value(catalog: Catalog): Value = VObject(Vector(
    "schema" -> VString(_catalog_schema),
    "version" -> VNumber(1),
    "id" -> VString(catalog.id),
    "revision" -> VNumber(catalog.revision),
    "relations" -> VArray(catalog.relations.map(item => VObject(Vector("id" -> VString(item.id), "direction" -> VString(item.direction))))),
    "logicalPatterns" -> VArray(catalog.logicalPatterns.map(pattern => VObject(Vector(
      "id" -> VString(pattern.id),
      "nodeRoles" -> VArray(pattern.nodeRoles.map(role => VObject(Vector("role" -> VString(role.role), "min" -> VNumber(role.min), "max" -> VNumber(role.max))))),
      "relationRules" -> VArray(pattern.relationRules.map(rule => VObject(Vector(
        "relation" -> VString(rule.relation), "fromRoles" -> VArray(rule.fromRoles.map(VString)), "toRoles" -> VArray(rule.toRoles.map(VString)),
        "min" -> VNumber(rule.min), "max" -> VNumber(rule.max), "topology" -> VString(rule.topology)
      ))))
    )))),
    "visualPatterns" -> VArray(catalog.visualPatterns.map(pattern => VObject(Vector(
      "id" -> VString(pattern.id), "compatibleLogicalPatterns" -> VArray(pattern.compatibleLogicalPatterns.map(VString)),
      "parameters" -> VArray(pattern.parameters.map(parameter => VObject(Vector(
        "name" -> VString(parameter.name), "type" -> VString(parameter.parameterType), "required" -> VBoolean(parameter.required)
      ))))
    ))))
  ))

  private def _parse_json(text: String, source: String): Value = {
    val parser = new JsonFactory().createParser(text)
    try {
      val token = parser.nextToken()
      if (token == null) _fail("VISUAL_PAGE_JSON", source, "empty JSON input")
      val value = _json_value(parser, source)
      if (parser.nextToken() != null) _fail("VISUAL_PAGE_JSON", source, "trailing JSON tokens are not admitted")
      value
    } catch {
      case fault: VisualPageFault => throw fault
      case NonFatal(e) => _fail("VISUAL_PAGE_JSON", source, Option(e.getMessage).getOrElse("invalid JSON"))
    } finally parser.close()
  }

  private def _json_value(parser: com.fasterxml.jackson.core.JsonParser, source: String): Value = parser.getCurrentToken match {
    case JsonToken.START_OBJECT =>
      var fields = Vector.empty[(String, Value)]
      var seen = Set.empty[String]
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        if (parser.getCurrentToken != JsonToken.FIELD_NAME) _fail("VISUAL_PAGE_JSON", source, "object member name is required")
        val name = parser.getCurrentName
        if (seen.contains(name)) _fail("VISUAL_PAGE_DUPLICATE_FIELD", source, s"duplicate JSON field: $name")
        seen += name
        if (parser.nextToken() == null) _fail("VISUAL_PAGE_JSON", source, s"missing JSON value for field: $name")
        fields :+= name -> _json_value(parser, source)
      }
      VObject(fields)
    case JsonToken.START_ARRAY =>
      var values = Vector.empty[Value]
      while (parser.nextToken() != JsonToken.END_ARRAY) values :+= _json_value(parser, source)
      VArray(values)
    case JsonToken.VALUE_STRING => VString(parser.getText)
    case JsonToken.VALUE_NUMBER_INT =>
      try VNumber(parser.getLongValue) catch { case NonFatal(_) => _fail("VISUAL_PAGE_JSON", source, "integer is outside supported range") }
    case JsonToken.VALUE_TRUE => VBoolean(true)
    case JsonToken.VALUE_FALSE => VBoolean(false)
    case JsonToken.VALUE_NULL => VNull
    case JsonToken.VALUE_NUMBER_FLOAT => _fail("VISUAL_PAGE_JSON", source, "floating-point values are not admitted")
    case _ => _fail("VISUAL_PAGE_JSON", source, s"unsupported JSON token: ${parser.getCurrentToken}")
  }

  private def _parse_yaml(text: String, source: String): Value = {
    val lines = _yaml_lines(text, source)
    if (lines.isEmpty) _fail("VISUAL_PAGE_YAML", source, "empty YAML input")
    var index = 0
    def _parse_block_(indent: Int): Value = {
      if (index >= lines.size || lines(index).indent != indent) _fail("VISUAL_PAGE_YAML", source, s"expected indentation $indent")
      if (lines(index).value.startsWith("-")) _parse_array_(indent) else _parse_object_(indent)
    }
    def _parse_object_(indent: Int): VObject = {
      var fields = Vector.empty[(String, Value)]
      var seen = Set.empty[String]
      while (index < lines.size && lines(index).indent == indent && !lines(index).value.startsWith("-")) {
        val line = lines(index)
        val pair = _yaml_pair(line.value, source, line.line)
        if (seen.contains(pair._1)) _fail("VISUAL_PAGE_DUPLICATE_FIELD", s"$source:${line.line}", s"duplicate YAML field: ${pair._1}")
        seen += pair._1
        index += 1
        val value =
          if (pair._2.nonEmpty) _yaml_scalar(pair._2, s"$source:${line.line}")
          else if (index < lines.size && lines(index).indent > indent) {
            if (lines(index).indent != indent + 2) _fail("VISUAL_PAGE_YAML", s"$source:${lines(index).line}", "nested YAML indentation must advance by two spaces")
            _parse_block_(indent + 2)
          } else VNull
        fields :+= pair._1 -> value
      }
      VObject(fields)
    }
    def _parse_array_(indent: Int): VArray = {
      var values = Vector.empty[Value]
      while (index < lines.size && lines(index).indent == indent && lines(index).value.startsWith("-")) {
        val line = lines(index)
        val rest = line.value.drop(1).trim
        index += 1
        val value =
          if (rest.isEmpty) {
            if (index >= lines.size || lines(index).indent != indent + 2) _fail("VISUAL_PAGE_YAML", s"$source:${line.line}", "array item requires an indented value")
            _parse_block_(indent + 2)
          } else if (_has_yaml_pair(rest)) {
            val pair = _yaml_pair(rest, source, line.line)
            val first = if (pair._2.nonEmpty) _yaml_scalar(pair._2, s"$source:${line.line}") else VNull
            val following =
              if (index < lines.size && lines(index).indent > indent) {
                if (lines(index).indent != indent + 2) _fail("VISUAL_PAGE_YAML", s"$source:${lines(index).line}", "array object indentation must advance by two spaces")
                _parse_object_(indent + 2)
              } else VObject(Vector.empty)
            if (following.fields.exists(_._1 == pair._1)) _fail("VISUAL_PAGE_DUPLICATE_FIELD", s"$source:${line.line}", s"duplicate YAML field: ${pair._1}")
            VObject((pair._1 -> first) +: following.fields)
          } else {
            if (index < lines.size && lines(index).indent > indent) _fail("VISUAL_PAGE_YAML", s"$source:${line.line}", "scalar array items cannot have nested values")
            _yaml_scalar(rest, s"$source:${line.line}")
          }
        values :+= value
      }
      VArray(values)
    }
    val value = _parse_block_(lines.head.indent)
    if (index != lines.size) _fail("VISUAL_PAGE_YAML", s"$source:${lines(index).line}", "unexpected YAML indentation or sequence form")
    value
  }

  private def _yaml_lines(text: String, source: String): Vector[YamlLine] =
    _normalize_newlines(text).split("\n", -1).toVector.zipWithIndex.flatMap { case (raw, index) =>
      val trimmed = raw.trim
      if (trimmed.isEmpty) None
      else if (trimmed.startsWith("#")) {
        val comment = trimmed.drop(1).trim
        if (_has_yaml_pair(comment))
          _fail("VISUAL_PAGE_YAML_COMMENT", s"$source:${index + 1}", "comments must not carry semantic YAML mapping fields")
        None
      }
      else {
        if (raw.contains("\t")) _fail("VISUAL_PAGE_YAML", s"$source:${index + 1}", "tabs are not admitted")
        if (_has_yaml_anchor_or_alias(trimmed) || trimmed.startsWith("<<:")) _fail("VISUAL_PAGE_YAML_ALIAS", s"$source:${index + 1}", "anchors, aliases, and merge keys are not admitted")
        val indent = raw.takeWhile(_ == ' ').length
        if (indent % 2 != 0) _fail("VISUAL_PAGE_YAML", s"$source:${index + 1}", "indentation must use two-space steps")
        Some(YamlLine(indent, raw.drop(indent), index + 1))
      }
    }

  private def _has_yaml_anchor_or_alias(value: String): Boolean = {
    var index = 0
    var quoted = false
    var escaped = false
    while (index < value.length) {
      val char = value.charAt(index)
      if (quoted) {
        if (escaped) escaped = false
        else if (char == '\\') escaped = true
        else if (char == '"') quoted = false
      } else if (char == '"' && {
        var prior = index - 1
        while (prior >= 0 && value.charAt(prior) == ' ') prior -= 1
        prior < 0 ||
          value.charAt(prior) == ':' ||
          (prior == 0 && value.charAt(prior) == '-')
      }) quoted = true
      else if (char == '&' || char == '*') return true
      index += 1
    }
    false
  }

  private def _yaml_pair(value: String, source: String, line: Int): (String, String) = {
    val index = value.indexOf(':')
    if (index <= 0) _fail("VISUAL_PAGE_YAML", s"$source:$line", "mapping entry requires key: value")
    val key = value.take(index).trim
    if (!_token_pattern.pattern.matcher(key).matches) _fail("VISUAL_PAGE_YAML", s"$source:$line", s"invalid YAML key: $key")
    key -> value.drop(index + 1).trim
  }

  private def _has_yaml_pair(value: String): Boolean = {
    val index = value.indexOf(':')
    index > 0 && _token_pattern.pattern.matcher(value.take(index).trim).matches
  }

  private def _yaml_scalar(value: String, source: String): Value = value match {
    case "true" => VBoolean(true)
    case "false" => VBoolean(false)
    case "null" | "~" => VNull
    case item if item.startsWith("\"") || item.startsWith("[") || item.startsWith("{") => _parse_json(item, source)
    case item if item.matches("-?[0-9]+") =>
      try VNumber(item.toLong) catch { case NonFatal(_) => _fail("VISUAL_PAGE_YAML", source, "integer is outside supported range") }
    case item if item.nonEmpty && !item.contains(":") && !item.contains("#") => VString(item)
    case _ => _fail("VISUAL_PAGE_YAML", source, "unsupported or lossy YAML scalar")
  }

  private def _parse_markdown(text: String, source: String): Value = {
    val lines = _normalize_newlines(text).split("\n", -1).toVector match {
      case values if values.nonEmpty && values.last.isEmpty => values.dropRight(1)
      case values => values
    }
    if (lines.headOption.contains("# Cozy Visual Page")) _parse_page_markdown(lines, source)
    else if (lines.headOption.contains("# Cozy Visual Page Set")) _parse_set_markdown(lines, source)
    else _fail("VISUAL_PAGE_MARKDOWN", source, "restricted Markdown requires a Cozy Visual Page or Cozy Visual Page Set heading")
  }

  private def _parse_page_markdown(lines: Vector[String], source: String): Value = {
    val expected = Vector(
      "# Cozy Visual Page", "schema", "version", "id", "knowledge", "language", "## Catalog", "id", "revision",
      "## Logical", "pattern", "nodes", "relations", "## Visual", "pattern", "parameters", "## Assets", "assets", "## Sources", "sources"
    )
    if (lines.size != expected.size) _fail("VISUAL_PAGE_MARKDOWN", source, "restricted Markdown has an unexpected line count")
    val values = mutable.Map.empty[String, Vector[Value]].withDefaultValue(Vector.empty)
    expected.zipWithIndex.foreach { case (expectedline, index) =>
      val actual = lines(index)
      if (expectedline.startsWith("#")) {
        if (actual != expectedline) _fail("VISUAL_PAGE_MARKDOWN", s"$source:${index + 1}", s"expected heading: $expectedline")
      } else {
        val prefix = expectedline + ": "
        if (!actual.startsWith(prefix)) _fail("VISUAL_PAGE_MARKDOWN", s"$source:${index + 1}", s"expected canonical field: $expectedline")
        values.update(expectedline, values(expectedline) :+ _parse_json(actual.drop(prefix.length), s"$source:${index + 1}"))
      }
    }
    def _value_(name: String, occurrence: Int): Value = values(name)(occurrence)
    VObject(Vector(
      "schema" -> _value_("schema", 0), "version" -> _value_("version", 0), "id" -> _value_("id", 0),
      "knowledge" -> _value_("knowledge", 0), "language" -> _value_("language", 0),
      "catalog" -> VObject(Vector("id" -> _value_("id", 1), "revision" -> _value_("revision", 0))),
      "logical" -> VObject(Vector("pattern" -> _value_("pattern", 0), "nodes" -> _value_("nodes", 0), "relations" -> _value_("relations", 0))),
      "visual" -> VObject(Vector("pattern" -> _value_("pattern", 1), "parameters" -> _value_("parameters", 0))),
      "assets" -> _value_("assets", 0), "sources" -> _value_("sources", 0)
    ))
  }

  private def _parse_set_markdown(lines: Vector[String], source: String): Value = {
    val expected = Vector("# Cozy Visual Page Set", "schema", "version", "id", "## Pages", "pages")
    if (lines.size != expected.size) _fail("VISUAL_PAGE_MARKDOWN", source, "restricted Markdown set has an unexpected line count")
    val values = expected.zipWithIndex.collect {
      case (name, index) if !name.startsWith("#") =>
        val prefix = name + ": "
        if (!lines(index).startsWith(prefix)) _fail("VISUAL_PAGE_MARKDOWN", s"$source:${index + 1}", s"expected canonical field: $name")
        name -> _parse_json(lines(index).drop(prefix.length), s"$source:${index + 1}")
    }.toMap
    if (lines(0) != expected(0) || lines(4) != expected(4)) _fail("VISUAL_PAGE_MARKDOWN", source, "restricted Markdown set headings are out of order")
    VObject(Vector("schema" -> values("schema"), "version" -> values("version"), "id" -> values("id"), "pages" -> values("pages")))
  }

  private def _object(value: Value, path: String): Vector[(String, Value)] = value match {
    case VObject(fields) => fields
    case _ => _fail("VISUAL_PAGE_TYPE", path, "must be an object")
  }

  private def _array(value: Value, path: String): Vector[Value] = value match {
    case VArray(values) => values
    case _ => _fail("VISUAL_PAGE_TYPE", path, "must be an array")
  }

  private def _string(value: Value, path: String): String = value match {
    case VString(item) => item
    case _ => _fail("VISUAL_PAGE_TYPE", path, "must be a string")
  }

  private def _boolean(value: Value, path: String): Boolean = value match {
    case VBoolean(item) => item
    case _ => _fail("VISUAL_PAGE_TYPE", path, "must be a boolean")
  }

  private def _positive_int(value: Value, path: String): Int = {
    val number = _positive_or_zero_int(value, path)
    if (number <= 0) _fail("VISUAL_PAGE_RANGE", path, "must be a positive integer")
    number
  }

  private def _positive_or_zero_int(value: Value, path: String): Int = value match {
    case VNumber(number) if number >= 0 && number <= Int.MaxValue => number.toInt
    case VNumber(_) => _fail("VISUAL_PAGE_RANGE", path, "must be a nonnegative 32-bit integer")
    case _ => _fail("VISUAL_PAGE_TYPE", path, "must be an integer")
  }

  private def _string_array(value: Value, path: String): Vector[String] = {
    val items = _array(value, path).zipWithIndex.map { case (item, index) => _stable_id(_string(item, s"$path[$index]"), s"$path[$index]") }
    _unique(items, path, "array value")
    items
  }

  private def _field(fields: Vector[(String, Value)], name: String, path: String): Value =
    fields.find(_._1 == name).map(_._2).getOrElse(_fail("VISUAL_PAGE_FIELD_MISSING", s"$path.$name", "required field is missing"))

  private def _exact_fields(fields: Vector[(String, Value)], expected: Vector[String], path: String): Unit = {
    _unique(fields.map(_._1), path, "field")
    val actual = fields.map(_._1).toSet
    val required = expected.toSet
    if (actual != required) {
      val missing = (required -- actual).toVector.sorted
      val unknown = (actual -- required).toVector.sorted
      val reason = Vector(
        if (missing.nonEmpty) Some("missing=" + missing.mkString(",")) else None,
        if (unknown.nonEmpty) Some("unknown=" + unknown.mkString(",")) else None
      ).flatten.mkString(" ")
      _fail("VISUAL_PAGE_FIELDS", path, reason)
    }
  }

  private def _schema_version(fields: Vector[(String, Value)], schema: String, path: String): Unit = {
    if (_string(_field(fields, "schema", path), s"$path.schema") != schema)
      _fail("VISUAL_PAGE_SCHEMA", s"$path.schema", s"must be exactly $schema")
    if (_positive_or_zero_int(_field(fields, "version", path), s"$path.version") != 1)
      _fail("VISUAL_PAGE_VERSION", s"$path.version", "must be integer 1")
  }

  private def _stable_id(value: String, path: String): String = {
    if (value == null || value != value.trim || !_token_pattern.pattern.matcher(value).matches)
      _fail("VISUAL_PAGE_ID", path, "must be a nonempty trimmed stable token")
    value
  }

  private def _required_text(value: String, path: String): String = {
    if (value == null || value.isEmpty || value != value.trim || value.exists(_.isControl))
      _fail("VISUAL_PAGE_TEXT", path, "must be a nonempty trimmed text value without control characters")
    value
  }

  private def _unique(values: Vector[String], path: String, label: String): Unit =
    values.groupBy(identity).collectFirst { case (value, duplicates) if duplicates.size > 1 => value }.foreach { value =>
      _fail("VISUAL_PAGE_DUPLICATE", path, s"duplicate $label: $value")
    }

  private def _safe_direct_file(root: Path, value: String, path: String): Path = {
    if (value.isEmpty || value != value.trim || value.exists(_.isControl) || value.contains('\\') || value.startsWith("/") || value.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*"))
      _fail("VISUAL_PAGE_PATH", path, "must be a safe relative POSIX path")
    val segments = value.split("/", -1).toVector
    if (segments.exists(segment => segment.isEmpty || segment == "." || segment == ".."))
      _fail("VISUAL_PAGE_PATH", path, "must not contain empty, dot, or traversal segments")
    val documentroot = try root.toRealPath() catch {
      case NonFatal(_) => _fail("VISUAL_PAGE_PATH_ROOT", path, "input document root cannot be resolved")
    }
    val resolved = documentroot.resolve(value).normalize()
    if (!resolved.startsWith(documentroot)) _fail("VISUAL_PAGE_PATH", path, "path escapes input document root")
    var current = documentroot
    segments.foreach { segment =>
      current = current.resolve(segment)
      if (Files.isSymbolicLink(current)) _fail("VISUAL_PAGE_PATH_SYMLINK", path, "path traverses a symbolic link")
    }
    if (!Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)) _fail("VISUAL_PAGE_PATH_FILE", path, "must resolve to an existing direct regular file")
    resolved
  }

  private def _direct_input(path: Path, label: String): Path = {
    if (path == null) _fail("VISUAL_PAGE_INPUT", label, "file path is required")
    val normalized = path.toAbsolutePath.normalize()
    if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized))
      _fail("VISUAL_PAGE_INPUT", label, "must be an existing direct regular non-symlink file")
    normalized
  }

  private def _read_utf8(path: Path, label: String): String = try {
    StandardCharsets.UTF_8.newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT)
      .decode(ByteBuffer.wrap(Files.readAllBytes(path)))
      .toString
  } catch {
    case NonFatal(e) => _fail("VISUAL_PAGE_READ", label, Option(e.getMessage).getOrElse("cannot read UTF-8 file"))
  }

  private def _atomic_write(path: Path, text: String): Unit = {
    val output = path.toAbsolutePath.normalize()
    val parent = Option(output.getParent).getOrElse(_fail("VISUAL_PAGE_SAVE", "$save", "output parent is required"))
    if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(parent))
      _fail("VISUAL_PAGE_SAVE", "$save", "output parent must be an existing direct directory")
    if (Files.exists(output, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(output))
      _fail("VISUAL_PAGE_SAVE", "$save", "output must not be a symbolic link")
    var temporary = Option.empty[Path]
    try {
      val prefix = Option(output.getFileName).map(_.toString).filter(_.length >= 3).getOrElse("vpg")
      val staged = Files.createTempFile(parent, prefix, ".tmp")
      temporary = Some(staged)
      Files.write(staged, text.getBytes(StandardCharsets.UTF_8))
      try Files.move(staged, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      catch {
        case _: AtomicMoveNotSupportedException => _fail("VISUAL_PAGE_SAVE_ATOMIC", "$save", "filesystem does not support same-directory atomic output replacement")
      }
      temporary = None
    } catch {
      case fault: VisualPageFault => throw fault
      case NonFatal(e) => _fail("VISUAL_PAGE_SAVE", "$save", Option(e.getMessage).getOrElse("cannot write output"))
    } finally temporary.foreach(path => Files.deleteIfExists(path))
  }

  private def _cli_path(value: String, path: String): Path = try {
    if (value == null || value.isEmpty || value != value.trim) _fail("VISUAL_PAGE_COMMAND", path, "path must be nonempty and trimmed")
    Paths.get(value).toAbsolutePath.normalize()
  } catch {
    case fault: VisualPageFault => throw fault
    case NonFatal(_) => _fail("VISUAL_PAGE_COMMAND", path, "path is invalid")
  }

  private def _extension(path: Path): String = Option(path).flatMap(item => Option(item.getFileName)).map(_.toString).flatMap { name =>
    val index = name.lastIndexOf('.')
    if (index >= 0) Some(name.substring(index)) else None
  }.getOrElse("")

  private def _canonical(value: Value): String = value match {
    case VObject(fields) => fields.map { case (key, item) => _json_string(key) + ":" + _canonical(item) }.mkString("{", ",", "}")
    case VArray(values) => values.map(_canonical).mkString("[", ",", "]")
    case VString(item) => _json_string(item)
    case VNumber(item) => item.toString
    case VBoolean(item) => item.toString
    case VNull => "null"
  }

  private def _json_string(value: String): String = {
    val escaped = value.flatMap {
      case '"' => "\\\""
      case '\\' => "\\\\"
      case '\b' => "\\b"
      case '\f' => "\\f"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case character if character < ' ' => f"\\u${character.toInt}%04x"
      case character => character.toString
    }
    "\"" + escaped + "\""
  }

  private def _identity(value: Value): String = "sha256:" + MessageDigest.getInstance("SHA-256").digest(_canonical(value).getBytes(StandardCharsets.UTF_8)).map(byte => f"${byte & 0xff}%02x").mkString

  private def _sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(byte => f"${byte & 0xff}%02x").mkString

  private def _markdown_line(key: String, value: Value): String = s"$key: ${_canonical(value)}"
  private def _parameter_text(value: ParameterValue): String = _canonical(_parameter_value(value))
  private def _normalize_newlines(value: String): String = Option(value).getOrElse("").replace("\r\n", "\n").replace('\r', '\n')
  private def _fail(code: String, path: String, reason: String): Nothing = throw VisualPageFault(code, path, reason)
}
