package cozy.media

import java.nio.file.{Files, LinkOption, Path, Paths}

/*
 * @since   Aug. 26, 2026
 *  version Sep. 30, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyVisualPage extends CozyVisualPageParsing {
  sealed trait Document {
    def schema: String
    def id: String
    def pages: Vector[Page]
  }

  final case class CatalogReference(id: String, revision: Int)
  final case class SourceBinding(id: String, path: String)
  final case class Asset(id: String, path: String, mediaType: String)
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
    canonicalJson: String
  ) {
    def count: Int = document.pages.size
  }

  final case class CommandConfig(input: Path, catalog: Path, save: Option[Path] = None)

  final case class VisualPageFault(code: String, path: String, reason: String)
    extends IllegalArgumentException(s"$code path=$path reason=$reason")

  private[cozy] sealed trait Value
  private[cozy] final case class VObject(fields: Vector[(String, Value)]) extends Value
  private[cozy] final case class VArray(values: Vector[Value]) extends Value
  private[cozy] final case class VString(value: String) extends Value
  private[cozy] final case class VNumber(value: Long) extends Value
  private[cozy] final case class VBoolean(value: Boolean) extends Value
  private[cozy] case object VNull extends Value

  private val _page_schema = "cozy.visual-page.v1"
  private val _set_schema = "cozy.visual-page-set.v1"
  private val _catalog_schema = "cozy.presentation-semantics.catalog.v1"
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

  private val _revision_two_logical_patterns = _logical_patterns :+
    LogicalPattern("standalone", Vector(NodeRole("item", 1, 1)), Vector.empty)
  private val _revision_two_visual_patterns = _visual_patterns :+
    VisualPattern("standalone-card", Vector("standalone"), Vector.empty)

  private[cozy] def fixedCatalog: Catalog =
    Catalog("presentation", 1, _relations, _logical_patterns, _visual_patterns)

  private[cozy] def fixedCatalog(revision: Int): Catalog = revision match {
    case 1 => fixedCatalog
    case 2 => Catalog("presentation", 2, _relations, _revision_two_logical_patterns, _revision_two_visual_patterns)
    case _ => _fail("VISUAL_PAGE_CATALOG_CORE", "$catalog.revision", "catalog revision must be exactly 1 or 2")
  }

  def parseJson(text: String): Document = _parse_document(_parse_json(text, "$"), "$")
  def parseYaml(text: String): Document = _parse_document(_parse_yaml(text, "$"), "$")
  def parseMarkdown(text: String): Document = {
    val document = _parse_document(_parse_markdown(text, "$"), "$")
    if (_material_markdown(text) != canonicalMarkdown(document))
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
    if (_extension(inputpath) == ".md" && _material_markdown(inputtext) != canonicalMarkdown(validated.document))
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

  private[cozy] def validatePageSet(document: PageSet, catalog: Path, sourceRoot: Path): ValidatedDocument =
    _validate_document_source(document, catalog, sourceRoot)

  private[cozy] def validateVisual(logical: Logical, visual: Visual, catalog: Catalog): Visual = {
    val pattern = catalog.visualPatterns.find(_.id == visual.pattern).getOrElse(
      _fail("VISUAL_PAGE_VISUAL_PATTERN", "$.visual.pattern", s"unknown visual pattern: ${visual.pattern}")
    )
    if (!pattern.compatibleLogicalPatterns.contains(logical.pattern))
      _fail("VISUAL_PAGE_VISUAL_COMPATIBILITY", "$.visual.pattern", s"visual pattern ${visual.pattern} is incompatible with ${logical.pattern}")
    val probe = Page(
      "presentation-semantics-probe", "presentation-semantics", "en",
      CatalogReference(catalog.id, catalog.revision), logical, visual, Vector.empty, Vector.empty
    )
    _validate_parameters(probe, pattern, logical.nodes.map(_.id).toSet, "$.visual")
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
    ValidatedDocument(normalized, parsedcatalog, canonical)
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

  def execute(args: List[String]): String = args match {
    case "validate" :: rest => validate(CommandConfig.create(rest, allowSave = false))
    case "inspect" :: rest => inspect(CommandConfig.create(rest, allowSave = false))
    case "convert" :: rest => convert(CommandConfig.create(rest, allowSave = true))
    case "preview" :: rest => CozyVisualPagePreview.execute(rest)
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
      s"catalogId: ${validated.catalog.id}",
      s"catalogRevision: ${validated.catalog.revision}"
    )
    val pages = validated.document.pages.flatMap { page =>
      Vector(s"page: ${page.id}", s"  logicalPattern: ${page.logical.pattern}", s"  visualPattern: ${page.visual.pattern}") ++
        page.logical.nodes.map(node => s"  node: ${node.id} role=${node.role} label=${node.label} sources=${node.sourceRefs.mkString(",")}") ++
        page.logical.relations.map(relation => s"  relation: ${relation.id} type=${relation.relationType} from=${relation.from} to=${relation.to} sources=${relation.sourceRefs.mkString(",")}") ++
        page.visual.parameters.sortBy(_.name).map(parameter => s"  parameter: ${parameter.name}=${_parameter_text(parameter.value)}")
    }
    (header ++ pages).mkString("\n")
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
    val pages = validated.document.pages.flatMap { page =>
      Vector(
        s"page: ${page.id}",
        s"  knowledge: ${page.knowledge}",
        s"  language: ${page.language}",
        s"  logicalPattern: ${page.logical.pattern}",
        s"  visualPattern: ${page.visual.pattern}"
      )
    }
    (Vector(
      s"schema: ${validated.document.schema}",
      "version: 1",
      s"count: ${validated.count}",
      s"catalogId: ${validated.catalog.id}",
      s"catalogRevision: ${validated.catalog.revision}"
    ) ++ pages).mkString("\n")
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
    _unique(fields.map(_._1), path, "field")
    Asset(
      _stable_id(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _required_text(_string(_field(fields, "path", path), s"$path.path"), s"$path.path"),
      _required_text(_string(_field(fields, "mediaType", path), s"$path.mediaType"), s"$path.mediaType")
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
    val expected = catalog.revision match {
      case 1 => (_logical_patterns, _visual_patterns)
      case 2 => (_revision_two_logical_patterns, _revision_two_visual_patterns)
      case _ => _fail("VISUAL_PAGE_CATALOG_CORE", s"$path.revision", "catalog revision must be exactly 1 or 2")
    }
    if (catalog.relations != _relations || catalog.logicalPatterns != expected._1 || catalog.visualPatterns != expected._2)
      _fail("VISUAL_PAGE_CATALOG_CORE", path, "catalog must be the exact closed revision-specific relation, logical-pattern, and visual-pattern catalog in canonical array order")
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
    page.assets.foreach(asset => _safe_direct_file(root, asset.path, s"$path.assets.${asset.id}.path"))
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

  private def _parse_json(text: String, source: String): Value =
    CozyVisualPageSyntax.parseJson(text, source)

  private def _parse_yaml(text: String, source: String): Value =
    CozyVisualPageSyntax.parseYaml(text, source)

  private def _material_markdown(text: String): String =
    CozyVisualPageSyntax.materialMarkdown(text)

  private def _parse_markdown(text: String, source: String): Value =
    CozyVisualPageSyntax.parseMarkdown(text, source)

  private[cozy] def _fail(code: String, path: String, reason: String): Nothing = throw VisualPageFault(code, path, reason)
}
