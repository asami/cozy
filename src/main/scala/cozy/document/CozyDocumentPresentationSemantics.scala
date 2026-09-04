package cozy.document

import cozy.media.{CozyExplanation, CozyVisualPage}
import io.circe.{Json, JsonObject}
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{Files, LinkOption, Path}
import java.security.MessageDigest
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.{InputSource, StringInputSource}
import scala.util.control.NonFatal

/*
 * @since   Sep.  4, 2026
 * @version Sep.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentPresentationSemantics {
  final case class ContentCoreBinding(id: String, language: String, identity: String)
  final case class StoryTransition(id: String, relationType: String, fromStepId: String, toStepId: String)
  final case class StoryFlow(id: String, transitions: Vector[StoryTransition])
  final case class Article(articleHeading: String, visibleText: Vector[String], visualIntent: String)
  final case class VisualOverride(medium: String, visual: CozyVisualPage.Visual)
  final case class ProjectionBinding(medium: String, logicalPattern: String, visual: CozyVisualPage.Visual)
  final case class ProjectionPolicy(id: String, revision: Int, bindings: Vector[ProjectionBinding], identity: String)
  final case class VisualSelection(medium: String, visual: CozyVisualPage.Visual, overridden: Boolean)
  final case class Structure(
    id: String,
    storyStepId: String,
    article: Article,
    visualOverrides: Vector[VisualOverride],
    logical: CozyVisualPage.Logical,
    visualSelections: Vector[VisualSelection]
  )
  final case class Catalogs(
    explanation: CozyExplanation.ValidatedCatalog,
    presentation: CozyExplanation.PresentationCatalog
  )
  final case class Validated(
    id: String,
    contentCore: ContentCoreBinding,
    semanticIdentity: String,
    composition: CozyExplanation.ValidatedComposition,
    plan: CozyExplanation.Plan,
    explanationCatalogIdentity: String,
    presentationCatalogIdentity: String,
    presentationLogicalCatalogIdentity: String,
    policy: ProjectionPolicy,
    sources: Vector[CozyExplanation.SourceDeclaration],
    assets: Vector[CozyExplanation.AssetDeclaration],
    storyFlow: StoryFlow,
    structures: Vector[Structure],
    currentnessIdentity: String
  )
  final case class PresentationSemanticsFault(code: String, path: String, reason: String)
    extends IllegalArgumentException(s"$code path=$path reason=$reason")

  private final case class ParsedStructure(
    id: String,
    storyStepId: String,
    article: Article,
    visualOverrides: Vector[VisualOverride],
    logical: CozyVisualPage.Logical
  )
  private final case class CoreSnapshot(bytes: Vector[Byte], value: Json)

  private val _schema = "cozy.content-core.presentation-semantics.v2"
  private val _policy_schema = "cozy.content-core.projection-policy.v1"
  private val _root_fields = Vector("schema", "id", "contentCore", "composition", "storyFlow", "structures", "projectionPolicy")
  private val _media = Vector("article", "slides", "video")
  private val _relation_types = Set("next", "causes", "depends-on", "enables", "maps-to")
  private val _token_pattern = "[A-Za-z0-9][A-Za-z0-9._-]*".r
  private val _identity_pattern = "sha256:[0-9a-f]{64}".r

  def load(
    core: Path,
    semantics: Path,
    explanationCatalog: Path,
    presentationCatalog: Path,
    bindings: CozyExplanation.ResourceBindings = CozyExplanation.ResourceBindings()
  ): Validated = {
    val coresnapshot = _load_core_snapshot(core, "$core")
    val semanticsvalue = _load_document(semantics, "$")
    val catalogs = try {
      Catalogs(CozyExplanation.loadCatalog(explanationCatalog), CozyExplanation.loadPresentationCatalog(presentationCatalog))
    } catch {
      case fault: CozyExplanation.ExplanationFault => _fail("DP-SEM-006", "$catalog", fault.getMessage)
      case fault: CozyVisualPage.VisualPageFault => _fail("DP-SEM-010", "$catalog", fault.getMessage)
      case NonFatal(e) => _fail("DP-SEM-006", "$catalog", _message(e))
    }
    _validate(coresnapshot, semanticsvalue, catalogs, bindings)
  }

  def validate(
    coreBytes: Array[Byte],
    coreValue: Json,
    semanticsValue: Json,
    catalogs: Catalogs,
    bindings: CozyExplanation.ResourceBindings = CozyExplanation.ResourceBindings()
  ): Validated = {
    val coresnapshot = _parse_core_snapshot(coreBytes, new java.net.URI("memory:///core.json"), "$core")
    if (coresnapshot.value != coreValue)
      _fail("DP-SEM-005", "$core", "submitted v1 Core value must match the direct-byte snapshot")
    _validate(coresnapshot, semanticsValue, catalogs, bindings)
  }

  private def _validate(
    coresnapshot: CoreSnapshot,
    semanticsValue: Json,
    catalogs: Catalogs,
    bindings: CozyExplanation.ResourceBindings
  ): Validated = {
    val core = try {
      CozyDocumentProject.validateContentCore(coresnapshot.value)
    } catch {
      case NonFatal(e) => _fail("DP-SEM-005", "$core", _message(e))
    }
    val root = _object(semanticsValue, "$")
    _exact_ordered_fields(root, _root_fields, "$")
    if (_string(root, "schema", "$") != _schema)
      _fail("DP-SEM-003", "$.schema", s"must be exactly ${_schema}")
    val id = _token(_string(root, "id", "$"), "$.id")
    val binding = _content_core(_field(root, "contentCore", "$"), "$.contentCore")
    val coreidentity = "sha256:" + _sha256(coresnapshot.bytes.toArray)
    if (binding.id != core.id || binding.language != core.language || binding.identity != coreidentity)
      _fail("DP-SEM-005", "$.contentCore", "must exactly bind the admitted v1 Core id, language, and direct-byte identity")

    val composition = _composition(_field(root, "composition", "$"), catalogs, bindings)
    val plan = CozyExplanation.expand(composition)
    val steps = plan.steps.map(step => step.id -> step).toMap
    val storyflow = _story_flow(_field(root, "storyFlow", "$"), steps.keySet)
    val parsedstructures = _structures(_field(root, "structures", "$"), steps)
    val policy = _policy(_field(root, "projectionPolicy", "$"), catalogs.presentation.catalog)
    val structures = parsedstructures.map { structure =>
      val selections = _select_visuals(structure, policy, catalogs.presentation.catalog)
      Structure(structure.id, structure.storyStepId, structure.article, structure.visualOverrides, structure.logical, selections)
    }.sortBy(_.id)
    val semanticidentity = _identity(_semantic_value(id, binding, composition.identity, storyflow, parsedstructures, policy))
    val currentnessidentity = _identity(_currentness_value(
      id,
      binding,
      semanticidentity,
      composition,
      plan,
      catalogs,
      policy,
      structures
    ))
    Validated(
      id,
      binding,
      semanticidentity,
      composition,
      plan,
      catalogs.explanation.identity,
      catalogs.presentation.catalogIdentity,
      catalogs.presentation.logicalCatalogIdentity,
      policy,
      composition.composition.sources.sortBy(_.id),
      composition.composition.assets.sortBy(_.id),
      storyflow,
      structures,
      currentnessidentity
    )
  }

  def canonicalJson(validated: Validated): String = _semantic_value(
    validated.id,
    validated.contentCore,
    validated.composition.identity,
    validated.storyFlow,
    validated.structures.map(structure => ParsedStructure(
      structure.id,
      structure.storyStepId,
      structure.article,
      structure.visualOverrides,
      structure.logical
    )),
    validated.policy
  ).noSpaces

  private def _composition(
    value: Json,
    catalogs: Catalogs,
    bindings: CozyExplanation.ResourceBindings
  ): CozyExplanation.ValidatedComposition = {
    val composition = try {
      CozyExplanation.parseCompositionJson(value.noSpaces)
    } catch {
      case fault: CozyExplanation.ExplanationFault => _fail("DP-SEM-006", "$.composition", fault.getMessage)
      case NonFatal(e) => _fail("DP-SEM-006", "$.composition", _message(e))
    }
    try {
      CozyExplanation.validateComposition(composition, catalogs.explanation, catalogs.presentation, bindings)
    } catch {
      case fault: CozyExplanation.ExplanationFault => _fail("DP-SEM-006", "$.composition", fault.getMessage)
      case fault: CozyVisualPage.VisualPageFault => _fail("DP-SEM-010", "$.composition", fault.getMessage)
      case NonFatal(e) => _fail("DP-SEM-006", "$.composition", _message(e))
    }
  }

  private def _content_core(value: Json, path: String): ContentCoreBinding = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "language", "identity"), path)
    val id = _text(_string(fields, "id", path), s"$path.id")
    val language = _text(_string(fields, "language", path), s"$path.language")
    val identity = _identity_text(_string(fields, "identity", path), s"$path.identity")
    ContentCoreBinding(id, language, identity)
  }

  private def _story_flow(value: Json, stepids: Set[String]): StoryFlow = {
    val fields = _object(value, "$.storyFlow")
    _exact_fields(fields, Vector("id", "transitions"), "$.storyFlow")
    val id = _token(_string(fields, "id", "$.storyFlow"), "$.storyFlow.id")
    val transitions = _array(_field(fields, "transitions", "$.storyFlow"), "$.storyFlow.transitions").zipWithIndex.map {
      case (item, index) => _story_transition(item, s"$$.storyFlow.transitions[$index]", stepids)
    }
    _unique(transitions.map(_.id), "$.storyFlow.transitions", "transition id", "DP-SEM-007")
    _unique(transitions.map(value => s"${value.relationType}\u0000${value.fromStepId}\u0000${value.toStepId}"), "$.storyFlow.transitions", "semantic transition", "DP-SEM-007")
    StoryFlow(id, transitions.sortBy(_.id))
  }

  private def _story_transition(value: Json, path: String, stepids: Set[String]): StoryTransition = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "relationType", "fromStepId", "toStepId"), path)
    val id = _token(_string(fields, "id", path), s"$path.id")
    val relationtype = _string(fields, "relationType", path)
    if (!_relation_types.contains(relationtype))
      _fail("DP-SEM-007", s"$path.relationType", "must be one of next, causes, depends-on, enables, maps-to")
    val from = _token(_string(fields, "fromStepId", path), s"$path.fromStepId")
    val to = _token(_string(fields, "toStepId", path), s"$path.toStepId")
    if (!stepids.contains(from) || !stepids.contains(to))
      _fail("DP-SEM-007", path, "Story Transition endpoints must resolve CompositionStep ids")
    if (from == to)
      _fail("DP-SEM-007", path, "Story Transition self-links are not admitted")
    StoryTransition(id, relationtype, from, to)
  }

  private def _structures(value: Json, steps: Map[String, CozyExplanation.PlanStep]): Vector[ParsedStructure] = {
    val values = _array(value, "$.structures")
    if (values.isEmpty)
      _fail("DP-SEM-008", "$.structures", "must be a nonempty array")
    val structures = values.zipWithIndex.map { case (item, index) =>
      val path = s"$$.structures[$index]"
      val fields = _object(item, path)
      _exact_fields(fields, Vector("id", "storyStepId", "article", "visualOverrides"), path)
      val id = _token(_string(fields, "id", path), s"$path.id")
      val stepid = _token(_string(fields, "storyStepId", path), s"$path.storyStepId")
      val step = steps.getOrElse(stepid, _fail("DP-SEM-008", s"$path.storyStepId", "must resolve one CompositionStep id"))
      ParsedStructure(
        id,
        stepid,
        _article(_field(fields, "article", path), s"$path.article"),
        _overrides(_field(fields, "visualOverrides", path), s"$path.visualOverrides"),
        step.logical
      )
    }
    _unique(structures.map(_.id), "$.structures", "Structure id", "DP-SEM-008")
    structures
  }

  private def _article(value: Json, path: String): Article = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("articleHeading", "visibleText", "visualIntent"), path)
    val heading = _text(_string(fields, "articleHeading", path), s"$path.articleHeading")
    val visible = _array(_field(fields, "visibleText", path), s"$path.visibleText")
    if (visible.isEmpty)
      _fail("DP-SEM-008", s"$path.visibleText", "must be a nonempty array")
    val text = visible.zipWithIndex.map { case (item, index) => _text_value(item, s"$path.visibleText[$index]") }
    val intent = _text(_string(fields, "visualIntent", path), s"$path.visualIntent")
    Article(heading, text, intent)
  }

  private def _overrides(value: Json, path: String): Vector[VisualOverride] = {
    val values = _array(value, path).zipWithIndex.map { case (item, index) =>
      val itempath = s"$path[$index]"
      val fields = _object(item, itempath)
      _exact_fields(fields, Vector("medium", "visual"), itempath)
      VisualOverride(_medium(_string(fields, "medium", itempath), s"$itempath.medium"), _visual(_field(fields, "visual", itempath), s"$itempath.visual"))
    }
    _unique(values.map(_.medium), path, "override medium", "DP-SEM-008")
    values.sortBy(_.medium)
  }

  private def _policy(value: Json, catalog: CozyVisualPage.Catalog): ProjectionPolicy = {
    val fields = _object(value, "$.projectionPolicy")
    _exact_fields(fields, Vector("schema", "id", "revision", "bindings"), "$.projectionPolicy")
    if (_string(fields, "schema", "$.projectionPolicy") != _policy_schema)
      _fail("DP-SEM-009", "$.projectionPolicy.schema", s"must be exactly ${_policy_schema}")
    val id = _token(_string(fields, "id", "$.projectionPolicy"), "$.projectionPolicy.id")
    val revision = _positive_int(_field(fields, "revision", "$.projectionPolicy"), "$.projectionPolicy.revision")
    val bindings = _array(_field(fields, "bindings", "$.projectionPolicy"), "$.projectionPolicy.bindings").zipWithIndex.map { case (item, index) =>
      val path = s"$$.projectionPolicy.bindings[$index]"
      val itemfields = _object(item, path)
      _exact_fields(itemfields, Vector("medium", "logicalPattern", "visual"), path)
      val medium = _medium(_string(itemfields, "medium", path), s"$path.medium")
      val logical = _token(_string(itemfields, "logicalPattern", path), s"$path.logicalPattern")
      if (!catalog.logicalPatterns.exists(_.id == logical))
        _fail("DP-SEM-009", s"$path.logicalPattern", "must be an existing Phase-36 Logical Pattern")
      val visual = _visual(_field(itemfields, "visual", path), s"$path.visual")
      if (!catalog.visualPatterns.exists(_.id == visual.pattern))
        _fail("DP-SEM-009", s"$path.visual.pattern", "must be an existing Phase-36 Visual Pattern")
      ProjectionBinding(medium, logical, visual)
    }
    if (bindings.isEmpty)
      _fail("DP-SEM-009", "$.projectionPolicy.bindings", "must be a nonempty array")
    _unique(bindings.map(value => s"${value.medium}\u0000${value.logicalPattern}"), "$.projectionPolicy.bindings", "medium/logicalPattern binding", "DP-SEM-009")
    val provisional = ProjectionPolicy(id, revision, bindings.sortBy(value => value.medium -> value.logicalPattern), "")
    provisional.copy(identity = _identity(_policy_value(provisional)))
  }

  private def _select_visuals(
    structure: ParsedStructure,
    policy: ProjectionPolicy,
    catalog: CozyVisualPage.Catalog
  ): Vector[VisualSelection] = _media.map { medium =>
    val matches = policy.bindings.filter(binding => binding.medium == medium && binding.logicalPattern == structure.logical.pattern)
    if (matches.size != 1)
      _fail("DP-SEM-011", s"$$.structures.${structure.id}", s"$medium requires exactly one base Projection Policy binding for ${structure.logical.pattern}")
    val base = _validated_visual(structure.logical, matches.head.visual, catalog, s"$$.projectionPolicy.bindings.$medium.${structure.logical.pattern}")
    structure.visualOverrides.find(_.medium == medium) match {
      case Some(overridevalue) =>
        VisualSelection(medium, _validated_visual(structure.logical, overridevalue.visual, catalog, s"$$.structures.${structure.id}.visualOverrides.$medium"), overridden = true)
      case None => VisualSelection(medium, base, overridden = false)
    }
  }

  private def _visual(value: Json, path: String): CozyVisualPage.Visual = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("pattern", "parameters"), path)
    val pattern = _token(_string(fields, "pattern", path), s"$path.pattern")
    val parameters = _object(_field(fields, "parameters", path), s"$path.parameters").toVector.map { case (name, item) =>
      val parameter = item.asString match {
        case Some(text) => CozyVisualPage.StringParameter(_text(text, s"$path.parameters.$name"))
        case None => item.asBoolean match {
          case Some(boolean) => CozyVisualPage.BooleanParameter(boolean)
          case None => _fail("DP-SEM-010", s"$path.parameters.$name", "must be a Phase-36 string or boolean Visual parameter")
        }
      }
      CozyVisualPage.VisualParameter(_token(name, s"$path.parameters.$name"), parameter)
    }
    _unique(parameters.map(_.name), s"$path.parameters", "Visual parameter", "DP-SEM-010")
    CozyVisualPage.Visual(pattern, parameters.sortBy(_.name))
  }

  private def _validated_visual(
    logical: CozyVisualPage.Logical,
    visual: CozyVisualPage.Visual,
    catalog: CozyVisualPage.Catalog,
    path: String
  ): CozyVisualPage.Visual = try {
    CozyVisualPage.validateVisual(logical, visual, catalog)
  } catch {
    case fault: CozyVisualPage.VisualPageFault => _fail("DP-SEM-010", path, fault.getMessage)
    case NonFatal(e) => _fail("DP-SEM-010", path, _message(e))
  }

  private def _semantic_value(
    id: String,
    core: ContentCoreBinding,
    compositionidentity: String,
    storyflow: StoryFlow,
    structures: Vector[ParsedStructure],
    policy: ProjectionPolicy
  ): Json = Json.fromFields(Vector(
    "schema" -> Json.fromString(_schema),
    "id" -> Json.fromString(id),
    "contentCore" -> _core_value(core),
    "compositionIdentity" -> Json.fromString(compositionidentity),
    "storyFlow" -> _story_flow_value(storyflow),
    "structures" -> Json.fromValues(structures.sortBy(_.id).map(_structure_value)),
    "projectionPolicy" -> _policy_value(policy)
  ))

  private def _currentness_value(
    id: String,
    core: ContentCoreBinding,
    semanticidentity: String,
    composition: CozyExplanation.ValidatedComposition,
    plan: CozyExplanation.Plan,
    catalogs: Catalogs,
    policy: ProjectionPolicy,
    structures: Vector[Structure]
  ): Json = Json.fromFields(Vector(
    "id" -> Json.fromString(id),
    "contentCoreIdentity" -> Json.fromString(core.identity),
    "semanticIdentity" -> Json.fromString(semanticidentity),
    "compositionIdentity" -> Json.fromString(composition.identity),
    "planIdentity" -> Json.fromString(plan.identity),
    "explanationCatalogIdentity" -> Json.fromString(catalogs.explanation.identity),
    "presentationCatalogIdentity" -> Json.fromString(catalogs.presentation.catalogIdentity),
    "presentationLogicalCatalogIdentity" -> Json.fromString(catalogs.presentation.logicalCatalogIdentity),
    "policyIdentity" -> Json.fromString(policy.identity),
    "sources" -> Json.fromValues(composition.composition.sources.sortBy(_.id).map(source => Json.fromFields(Vector(
      "id" -> Json.fromString(source.id), "sha256" -> Json.fromString(source.sha256)
    )))),
    "assets" -> Json.fromValues(composition.composition.assets.sortBy(_.id).map(asset => Json.fromFields(Vector(
      "id" -> Json.fromString(asset.id), "mediaType" -> Json.fromString(asset.mediaType), "sha256" -> Json.fromString(asset.sha256)
    )))),
    "structures" -> Json.fromValues(structures.sortBy(_.id).map(_selected_structure_value))
  ))

  private def _core_value(value: ContentCoreBinding): Json = Json.fromFields(Vector(
    "id" -> Json.fromString(value.id),
    "language" -> Json.fromString(value.language),
    "identity" -> Json.fromString(value.identity)
  ))

  private def _story_flow_value(value: StoryFlow): Json = Json.fromFields(Vector(
    "id" -> Json.fromString(value.id),
    "transitions" -> Json.fromValues(value.transitions.sortBy(_.id).map(transition => Json.fromFields(Vector(
      "id" -> Json.fromString(transition.id),
      "relationType" -> Json.fromString(transition.relationType),
      "fromStepId" -> Json.fromString(transition.fromStepId),
      "toStepId" -> Json.fromString(transition.toStepId)
    ))))
  ))

  private def _structure_value(value: ParsedStructure): Json = Json.fromFields(Vector(
    "id" -> Json.fromString(value.id),
    "storyStepId" -> Json.fromString(value.storyStepId),
    "article" -> Json.fromFields(Vector(
      "articleHeading" -> Json.fromString(value.article.articleHeading),
      "visibleText" -> Json.fromValues(value.article.visibleText.map(Json.fromString)),
      "visualIntent" -> Json.fromString(value.article.visualIntent)
    )),
    "visualOverrides" -> Json.fromValues(value.visualOverrides.sortBy(_.medium).map(overridevalue => Json.fromFields(Vector(
      "medium" -> Json.fromString(overridevalue.medium),
      "visual" -> _visual_value(overridevalue.visual)
    )))),
    "logicalIdentity" -> Json.fromString(_logical_identity(value.logical))
  ))

  private def _selected_structure_value(value: Structure): Json = Json.fromFields(Vector(
    "id" -> Json.fromString(value.id),
    "storyStepId" -> Json.fromString(value.storyStepId),
    "logicalIdentity" -> Json.fromString(_logical_identity(value.logical)),
    "visualSelections" -> Json.fromValues(value.visualSelections.sortBy(_.medium).map(selection => Json.fromFields(Vector(
      "medium" -> Json.fromString(selection.medium),
      "visual" -> _visual_value(selection.visual),
      "overridden" -> Json.fromBoolean(selection.overridden)
    ))))
  ))

  private def _policy_value(value: ProjectionPolicy): Json = Json.fromFields(Vector(
    "schema" -> Json.fromString(_policy_schema),
    "id" -> Json.fromString(value.id),
    "revision" -> Json.fromInt(value.revision),
    "bindings" -> Json.fromValues(value.bindings.sortBy(binding => binding.medium -> binding.logicalPattern).map(binding => Json.fromFields(Vector(
      "medium" -> Json.fromString(binding.medium),
      "logicalPattern" -> Json.fromString(binding.logicalPattern),
      "visual" -> _visual_value(binding.visual)
    ))))
  ))

  private def _visual_value(value: CozyVisualPage.Visual): Json = Json.fromFields(Vector(
    "pattern" -> Json.fromString(value.pattern),
    "parameters" -> Json.fromFields(value.parameters.sortBy(_.name).map { parameter =>
      val item = parameter.value match {
        case CozyVisualPage.NodeReference(text) => Json.fromString(text)
        case CozyVisualPage.StringParameter(text) => Json.fromString(text)
        case CozyVisualPage.BooleanParameter(boolean) => Json.fromBoolean(boolean)
      }
      parameter.name -> item
    })
  ))

  private def _logical_identity(logical: CozyVisualPage.Logical): String = _identity(Json.fromFields(Vector(
    "pattern" -> Json.fromString(logical.pattern),
    "nodes" -> Json.fromValues(logical.nodes.map(node => Json.fromFields(Vector(
      "id" -> Json.fromString(node.id), "role" -> Json.fromString(node.role), "label" -> Json.fromString(node.label),
      "sourceRefs" -> Json.fromValues(node.sourceRefs.sorted.map(Json.fromString))
    )))),
    "relations" -> Json.fromValues(logical.relations.map(relation => Json.fromFields(Vector(
      "id" -> Json.fromString(relation.id), "type" -> Json.fromString(relation.relationType),
      "from" -> Json.fromString(relation.from), "to" -> Json.fromString(relation.to),
      "sourceRefs" -> Json.fromValues(relation.sourceRefs.sorted.map(Json.fromString))
    ))))
  )))

  private def _load_document(path: Path, label: String): Json = {
    _direct_file(path, label)
    try StructuredDocumentLoader.loadJson(InputSource(path.toFile)).take
    catch { case NonFatal(e) => _fail("DP-SEM-001", label, _message(e)) }
  }

  private def _load_core_snapshot(path: Path, label: String): CoreSnapshot = {
    val direct = _direct_file(path, label)
    val bytes = try Files.readAllBytes(direct)
    catch { case NonFatal(e) => _fail("DP-SEM-001", label, _message(e)) }
    _parse_core_snapshot(bytes, direct.toUri, label)
  }

  private def _parse_core_snapshot(bytes: Array[Byte], uri: java.net.URI, label: String): CoreSnapshot = {
    if (bytes == null || bytes.isEmpty)
      _fail("DP-SEM-001", label, "direct v1 Core bytes are required")
    val snapshotbytes = bytes.toVector
    val value = try {
      val text = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(java.nio.ByteBuffer.wrap(snapshotbytes.toArray))
        .toString
      StructuredDocumentLoader.loadJson(StringInputSource(text, uri)).take
    }
    catch { case NonFatal(e) => _fail("DP-SEM-001", label, _message(e)) }
    CoreSnapshot(snapshotbytes, value)
  }

  private def _direct_file(path: Path, label: String): Path = {
    val normalized = try Option(path).map(_.toAbsolutePath.normalize()).getOrElse(_fail("DP-SEM-001", label, "direct file path is required"))
    catch { case NonFatal(_) => _fail("DP-SEM-001", label, "direct file path is invalid") }
    if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized))
      _fail("DP-SEM-001", label, "must be an existing direct regular non-symlink file")
    normalized
  }

  private def _object(value: Json, path: String): JsonObject =
    value.asObject.getOrElse(_fail("DP-SEM-004", path, "must be an object"))

  private def _array(value: Json, path: String): Vector[Json] =
    value.asArray.getOrElse(_fail("DP-SEM-004", path, "must be an array"))

  private def _field(fields: JsonObject, name: String, path: String): Json =
    fields(name).getOrElse(_fail("DP-SEM-002", s"$path.$name", "required field is missing"))

  private def _string(fields: JsonObject, name: String, path: String): String =
    _field(fields, name, path).asString.getOrElse(_fail("DP-SEM-004", s"$path.$name", "must be a string"))

  private def _text_value(value: Json, path: String): String =
    value.asString.map(_text(_, path)).getOrElse(_fail("DP-SEM-004", path, "must be a string"))

  private def _text(value: String, path: String): String = {
    if (value == null || value.isEmpty || value != value.trim || value.exists(_.isControl))
      _fail("DP-SEM-004", path, "must be nonempty trimmed text without control characters")
    value
  }

  private def _token(value: String, path: String): String = {
    if (value == null || !_token_pattern.pattern.matcher(value).matches())
      _fail("DP-SEM-004", path, "must be a stable token")
    value
  }

  private def _identity_text(value: String, path: String): String = {
    if (value == null || !_identity_pattern.pattern.matcher(value).matches())
      _fail("DP-SEM-004", path, "must be sha256:<64-lowercase-hex>")
    value
  }

  private def _medium(value: String, path: String): String = {
    if (!_media.contains(value))
      _fail("DP-SEM-004", path, "must be article, slides, or video")
    value
  }

  private def _positive_int(value: Json, path: String): Int = value.asNumber.flatMap(_.toBigDecimal) match {
    case Some(number) if number.isValidInt && number.scale <= 0 && number.toInt > 0 => number.toInt
    case _ => _fail("DP-SEM-004", path, "must be a positive integer")
  }

  private def _exact_fields(fields: JsonObject, expected: Vector[String], path: String): Unit = {
    if (fields.keys.toSet != expected.toSet)
      _fail("DP-SEM-002", path, s"must have exactly fields: ${expected.mkString(", ")}")
  }

  private def _exact_ordered_fields(fields: JsonObject, expected: Vector[String], path: String): Unit = {
    if (fields.keys.toVector != expected)
      _fail("DP-SEM-002", path, s"must have exactly ordered fields: ${expected.mkString(", ")}")
  }

  private def _unique(values: Vector[String], path: String, label: String, code: String): Unit =
    values.groupBy(identity).collectFirst { case (value, entries) if entries.size > 1 => value }.foreach { value =>
      _fail(code, path, s"duplicate $label: $value")
    }

  private def _identity(value: Json): String = "sha256:" + _sha256(value.noSpaces.getBytes(StandardCharsets.UTF_8))

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString

  private def _message(throwable: Throwable): String =
    Option(throwable.getMessage).filter(_.nonEmpty).getOrElse(throwable.getClass.getSimpleName)

  private def _fail(code: String, path: String, reason: String): Nothing =
    throw PresentationSemanticsFault(code, path, reason)
}
