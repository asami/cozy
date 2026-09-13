package cozy.document

import io.circe.{Json, JsonObject}
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{Files, LinkOption, Path}
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import org.yaml.snakeyaml.{LoaderOptions, Yaml}
import scala.util.control.NonFatal

/*
 * @since   Sep. 12, 2026
 * @version Sep. 13, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentConfirmationVocabulary {
  final case class VocabularyFault(code: String, path: String, reason: String)
    extends IllegalArgumentException(s"$code path=$path reason=$reason")

  private final case class Resource(
    locale: String,
    documentchrome: Map[String, String],
    logicalpatterns: Map[String, String],
    noderoles: Map[String, String],
    documentrelationtypes: Map[String, String],
    documentflowtypes: Map[String, String],
    summarychrome: Map[String, String],
    sourcecategories: Map[String, String],
    diagramitemkinds: Map[String, String],
    summaryrelationtypes: Map[String, String],
    summaryflowtypes: Map[String, String],
    inverserelationtypes: Map[String, String],
    inverseflowtypes: Map[String, String],
    documenttargetkinds: Map[String, String],
    omissiondispositions: Map[String, String],
    directions: Map[String, String]
  )

  private val _schema = "cozy.document-confirmation-vocabulary.v1"
  private val _locale_pattern = "[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*".r
  private val _document_chrome_keys = Set(
    "pageHeading", "statusHeading", "coverageComplete", "currentSources", "admittedState", "noUnresolvedReferences",
    "containmentHeading", "flowHeading", "structureHeading", "proseHeading", "logicalPatternHeading",
    "nodesHeading", "nodeRoleHeading", "relationsHeading", "noDirectChildFlowTransitions",
    "logicalStructureReference", "identitiesHeading", "coreIdentityLabel", "documentIdentityLabel",
    "outputIdentityLabel"
  )
  private val _summary_chrome_keys = Set(
    "pageHeading", "statusHeading", "selectedSourcesCurrentAndAdmitted", "noUnresolvedSelectedReferences", "navigationHeading",
    "semanticPanelHeading", "emphasisHeading", "retainedPointsHeading", "diagramHeading", "diagramItemsHeading",
    "diagramEdgesHeading", "emptyDiagramMessage", "sourcesHeading", "omissionsHeading", "rationaleHeading",
    "identitiesHeading", "coreIdentityLabel", "documentIdentityLabel", "summaryIdentityLabel", "outputIdentityLabel"
  )
  private val _logical_pattern_keys = Set("sequence", "mapping", "dependency-map", "causal-chain")
  private val _node_role_keys = Set("step", "source", "target", "dependency", "dependent", "cause", "effect")
  private val _relation_type_keys = Set("next", "maps-to", "depends-on", "causes", "enables")
  private val _source_category_keys = Set("steps", "claims", "nodes", "relations", "flows")
  private val _diagram_item_kind_keys = Set("step", "node")
  private val _document_target_kind_keys = Set("section", "block", "list-item")
  private val _omission_disposition_keys = Set("omitted", "condensed")
  private val _direction_keys = Set("forward", "inverse")

  def loadDocument(vocabularyPath: Path, locale: String): CozyDocumentConfirmationProjectionV2.Vocabulary = {
    val resource = _resource(vocabularyPath, locale)
    CozyDocumentConfirmationProjectionV2.Vocabulary(
      CozyDocumentConfirmationProjectionV2.Chrome(
        resource.documentchrome("pageHeading"),
        resource.documentchrome("statusHeading"), resource.documentchrome("coverageComplete"),
        resource.documentchrome("currentSources"), resource.documentchrome("admittedState"),
        resource.documentchrome("noUnresolvedReferences"), resource.documentchrome("containmentHeading"),
        resource.documentchrome("flowHeading"), resource.documentchrome("structureHeading"),
        resource.documentchrome("proseHeading"), resource.documentchrome("logicalPatternHeading"),
        resource.documentchrome("nodesHeading"), resource.documentchrome("nodeRoleHeading"),
        resource.documentchrome("relationsHeading"), resource.documentchrome("noDirectChildFlowTransitions"),
        resource.documentchrome("logicalStructureReference"), resource.documentchrome("identitiesHeading"),
        resource.documentchrome("coreIdentityLabel"), resource.documentchrome("documentIdentityLabel"),
        resource.documentchrome("outputIdentityLabel")
      ),
      resource.logicalpatterns,
      resource.noderoles,
      resource.documentrelationtypes,
      resource.documentflowtypes
    )
  }

  def loadSummary(vocabularyPath: Path, locale: String): CozySummaryConfirmationProjectionV2.Vocabulary = {
    val resource = _resource(vocabularyPath, locale)
    CozySummaryConfirmationProjectionV2.Vocabulary(
      CozySummaryConfirmationProjectionV2.Chrome(
        resource.summarychrome("pageHeading"),
        resource.summarychrome("statusHeading"), resource.summarychrome("selectedSourcesCurrentAndAdmitted"),
        resource.summarychrome("noUnresolvedSelectedReferences"), resource.summarychrome("navigationHeading"),
        resource.summarychrome("semanticPanelHeading"), resource.summarychrome("emphasisHeading"),
        resource.summarychrome("retainedPointsHeading"), resource.summarychrome("diagramHeading"),
        resource.summarychrome("diagramItemsHeading"), resource.summarychrome("diagramEdgesHeading"),
        resource.summarychrome("emptyDiagramMessage"), resource.summarychrome("sourcesHeading"),
        resource.summarychrome("omissionsHeading"), resource.summarychrome("rationaleHeading"),
        resource.summarychrome("identitiesHeading"), resource.summarychrome("coreIdentityLabel"),
        resource.summarychrome("documentIdentityLabel"), resource.summarychrome("summaryIdentityLabel"),
        resource.summarychrome("outputIdentityLabel")
      ),
      resource.sourcecategories,
      resource.diagramitemkinds,
      resource.summaryrelationtypes,
      resource.summaryflowtypes,
      resource.documenttargetkinds,
      resource.omissiondispositions,
      resource.directions,
      resource.logicalpatterns,
      resource.inverserelationtypes,
      resource.inverseflowtypes
    )
  }

  private def _resource(vocabularypath: Path, locale: String): Resource = {
    val path = _admit(vocabularypath)
    val bytes = _read_bytes(path)
    val resource = _parse(_load_document(path, bytes))
    val directorylocale = path.getParent.getFileName.toString
    if (directorylocale != resource.locale)
      _fail("CONFIRMATION_VOCABULARY_LOCALE", "$.locale", "must exactly equal the admitted resource directory locale")
    if (resource.locale != locale)
      _fail("CONFIRMATION_VOCABULARY_LOCALE", "$.locale", "must exactly equal the admitted v2 source locale")
    resource
  }

  private def _admit(value: Path): Path = {
    val path = try value.toAbsolutePath.normalize() catch { case NonFatal(_) => _fail("CONFIRMATION_VOCABULARY_PATH", "$.vocabulary", "path is invalid") }
    val parent = Option(path.getParent).getOrElse(_fail("CONFIRMATION_VOCABULARY_PATH", "$.vocabulary", "has no safe parent"))
    if (parent.getFileName == null)
      _fail("CONFIRMATION_VOCABULARY_PATH", "$.vocabulary", "parent must have a basename")
    if (path.getFileName.toString != "confirmation-vocabulary.yaml")
      _fail("CONFIRMATION_VOCABULARY_PATH", "$.vocabulary", "must be a direct regular file named exactly confirmation-vocabulary.yaml")
    if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      _fail("CONFIRMATION_VOCABULARY_PATH", "$.vocabulary", "must be a direct regular non-symlink file")
    if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS))
      _fail("CONFIRMATION_VOCABULARY_PATH", "$.vocabulary", "parent must be a direct non-symlink directory")
    path
  }

  private def _parse(value: Json): Resource = {
    val fields = _object(value, "$")
    _exact_fields(fields, Set("schema", "locale", "document", "summary"), "$")
    if (_string(fields, "schema", "$") != _schema)
      _fail("CONFIRMATION_VOCABULARY_SCHEMA", "$.schema", s"must be exactly ${_schema}")
    val locale = _locale(_string(fields, "locale", "$"), "$.locale")
    val document = _object(_field(fields, "document", "$"), "$.document")
    _exact_fields(document, Set("chrome", "logicalPatterns", "nodeRoles", "relationTypes", "flowTypes"), "$.document")
    val summary = _object(_field(fields, "summary", "$"), "$.summary")
    _exact_fields(summary, Set("chrome", "sourceCategories", "diagramItemKinds", "relationTypes", "flowTypes", "inverseRelationTypes", "inverseFlowTypes", "documentTargetKinds", "omissionDispositions", "directions"), "$.summary")
    Resource(
      locale,
      _wording_map(_field(document, "chrome", "$.document"), "$.document.chrome", _document_chrome_keys),
      _wording_map(_field(document, "logicalPatterns", "$.document"), "$.document.logicalPatterns", _logical_pattern_keys),
      _wording_map(_field(document, "nodeRoles", "$.document"), "$.document.nodeRoles", _node_role_keys),
      _wording_map(_field(document, "relationTypes", "$.document"), "$.document.relationTypes", _relation_type_keys),
      _wording_map(_field(document, "flowTypes", "$.document"), "$.document.flowTypes", _relation_type_keys),
      _wording_map(_field(summary, "chrome", "$.summary"), "$.summary.chrome", _summary_chrome_keys),
      _wording_map(_field(summary, "sourceCategories", "$.summary"), "$.summary.sourceCategories", _source_category_keys),
      _wording_map(_field(summary, "diagramItemKinds", "$.summary"), "$.summary.diagramItemKinds", _diagram_item_kind_keys),
      _wording_map(_field(summary, "relationTypes", "$.summary"), "$.summary.relationTypes", _relation_type_keys),
      _wording_map(_field(summary, "flowTypes", "$.summary"), "$.summary.flowTypes", _relation_type_keys),
      _wording_map(_field(summary, "inverseRelationTypes", "$.summary"), "$.summary.inverseRelationTypes", _relation_type_keys),
      _wording_map(_field(summary, "inverseFlowTypes", "$.summary"), "$.summary.inverseFlowTypes", _relation_type_keys),
      _wording_map(_field(summary, "documentTargetKinds", "$.summary"), "$.summary.documentTargetKinds", _document_target_kind_keys),
      _wording_map(_field(summary, "omissionDispositions", "$.summary"), "$.summary.omissionDispositions", _omission_disposition_keys),
      _wording_map(_field(summary, "directions", "$.summary"), "$.summary.directions", _direction_keys)
    )
  }

  private def _wording_map(value: Json, path: String, expected: Set[String]): Map[String, String] = {
    val fields = _object(value, path)
    _exact_fields(fields, expected, path)
    fields.toMap.map { case (key, wording) => key -> _wording(_string_value(wording, s"$path.$key"), s"$path.$key") }
  }

  private def _load_document(path: Path, bytes: Array[Byte]): Json =
    try {
      val text = _decode_utf8(bytes)
      val options = new LoaderOptions()
      options.setAllowDuplicateKeys(false)
      options.setAllowRecursiveKeys(false)
      options.setMaxAliasesForCollections(0)
      _reject_yaml_indirection(new Yaml(options).parse(new StringReader(text)))
      new Yaml(options).load(new StringReader(text))
      StructuredDocumentLoader.loadJson(InputSource(text, path.toUri)).take
    } catch {
      case fault: VocabularyFault => throw fault
      case NonFatal(_) => _fail("CONFIRMATION_VOCABULARY_SOURCE", "$.vocabulary", "must be a well-formed YAML document without duplicate or lossy structure")
    }

  private def _reject_yaml_indirection(events: java.lang.Iterable[org.yaml.snakeyaml.events.Event]): Unit = {
    val iterator = events.iterator
    while (iterator.hasNext) {
      iterator.next() match {
        case _: org.yaml.snakeyaml.events.AliasEvent => _fail("CONFIRMATION_VOCABULARY_SOURCE", "$.vocabulary", "YAML aliases are not admitted")
        case value: org.yaml.snakeyaml.events.NodeEvent if value.getAnchor != null => _fail("CONFIRMATION_VOCABULARY_SOURCE", "$.vocabulary", "YAML anchors are not admitted")
        case value: org.yaml.snakeyaml.events.ScalarEvent if value.getTag != null => _fail("CONFIRMATION_VOCABULARY_SOURCE", "$.vocabulary", "explicit YAML tags are not admitted")
        case value: org.yaml.snakeyaml.events.CollectionStartEvent if value.getTag != null => _fail("CONFIRMATION_VOCABULARY_SOURCE", "$.vocabulary", "explicit YAML tags are not admitted")
        case _ =>
      }
    }
  }

  private def _read_bytes(path: Path): Array[Byte] =
    try Files.readAllBytes(path) catch { case NonFatal(_) => _fail("CONFIRMATION_VOCABULARY_SOURCE", "$.vocabulary", "cannot be read") }

  private def _decode_utf8(bytes: Array[Byte]): String =
    try StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString catch {
      case NonFatal(_) => _fail("CONFIRMATION_VOCABULARY_SOURCE", "$.vocabulary", "must be valid UTF-8")
    }

  private def _object(value: Json, path: String): JsonObject = value.asObject.getOrElse(_fail("CONFIRMATION_VOCABULARY_STRUCTURE", path, "must be an object"))
  private def _field(fields: JsonObject, name: String, path: String): Json = fields(name).getOrElse(_fail("CONFIRMATION_VOCABULARY_STRUCTURE", path, s"missing required field: $name"))
  private def _string(fields: JsonObject, name: String, path: String): String = _string_value(_field(fields, name, path), s"$path.$name")
  private def _string_value(value: Json, path: String): String = value.asString.getOrElse(_fail("CONFIRMATION_VOCABULARY_STRUCTURE", path, "must be a string"))
  private def _exact_fields(fields: JsonObject, expected: Set[String], path: String): Unit = if (fields.keys.toSet != expected) _fail("CONFIRMATION_VOCABULARY_FIELDS", path, s"must contain exactly: ${expected.toVector.sorted.mkString(", ")}")
  private def _wording(value: String, path: String): String = if (value.nonEmpty && value == value.trim) value else _fail("CONFIRMATION_VOCABULARY_WORDING", path, "must be nonempty and trimmed")
  private def _locale(value: String, path: String): String = if (_locale_pattern.pattern.matcher(value).matches()) value else _fail("CONFIRMATION_VOCABULARY_LOCALE", path, "must be a declared BCP-47 language tag")
  private def _fail(code: String, path: String, reason: String): Nothing = throw VocabularyFault(code, path, reason)
}
