package cozy.media

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, Paths, StandardCopyOption}
import java.security.MessageDigest

import com.fasterxml.jackson.core.{JsonFactory, JsonToken}

import scala.collection.mutable
import scala.util.control.NonFatal

/*
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyExplanation {
  sealed trait JsonValue
  final case class JsonObject(fields: Vector[(String, JsonValue)]) extends JsonValue
  final case class JsonArray(values: Vector[JsonValue]) extends JsonValue
  final case class JsonString(value: String) extends JsonValue
  final case class JsonNumber(value: Long) extends JsonValue
  final case class JsonBoolean(value: Boolean) extends JsonValue

  final case class PatternReference(id: String, version: Int)
  final case class Definition(name: String, valueType: String, required: Boolean)
  final case class Role(id: String, order: Int, required: Boolean)
  final case class SubjectPattern(id: String, version: Int, factDefinitions: Vector[Definition])
  final case class ExplanationPattern(
    id: String,
    version: Int,
    compatibleSubjects: Vector[PatternReference],
    parameterDefinitions: Vector[Definition],
    roles: Vector[Role]
  )
  final case class Catalog(
    id: String,
    revision: Int,
    subjectPatterns: Vector[SubjectPattern],
    explanationPatterns: Vector[ExplanationPattern],
    reservedNarrativeArgumentIds: Vector[String]
  )
  final case class CatalogSelector(id: String, revision: Int, identity: String)
  final case class SourceDeclaration(id: String, sha256: String)
  final case class AssetDeclaration(id: String, mediaType: String, sha256: String)
  final case class Fact(name: String, value: JsonValue, sourceRefs: Vector[String], assetRefs: Vector[String])
  final case class Parameter(name: String, value: JsonValue)
  final case class Claim(id: String, text: String, emphasis: String, sourceRefs: Vector[String], assetRefs: Vector[String])
  final case class ParameterSelection(name: String)
  final case class CompositionStep(
    id: String,
    order: Int,
    semanticRole: String,
    claims: Vector[Claim],
    logical: CozyVisualPage.Logical,
    sourceRefs: Vector[String],
    assetRefs: Vector[String],
    parameterSelection: Vector[ParameterSelection]
  )
  final case class Subject(pattern: PatternReference, facts: Vector[Fact])
  final case class Explanation(pattern: PatternReference, parameters: Vector[Parameter], steps: Vector[CompositionStep])
  final case class Composition(
    id: String,
    explanationCatalog: CatalogSelector,
    subject: Subject,
    explanation: Explanation,
    sources: Vector[SourceDeclaration],
    assets: Vector[AssetDeclaration]
  )
  final case class PatternProvenance(subjectPattern: PatternReference, explanationPattern: PatternReference)
  final case class ParameterProvenance(values: Vector[Parameter], identity: String)
  final case class PlanStep(
    id: String,
    order: Int,
    semanticRole: String,
    claims: Vector[Claim],
    logical: CozyVisualPage.Logical,
    sourceRefs: Vector[String],
    assetRefs: Vector[String],
    patternProvenance: PatternProvenance,
    parameterProvenance: ParameterProvenance,
    identity: String
  )
  final case class Plan(
    compositionIdentity: String,
    explanationCatalog: CatalogSelector,
    presentationCatalog: CatalogSelector,
    subjectPattern: PatternReference,
    explanationPattern: PatternReference,
    steps: Vector[PlanStep],
    identity: String
  )
  final case class ResourceBindings(sources: Map[String, Path] = Map.empty, assets: Map[String, Path] = Map.empty)
  final case class PresentationCatalog(
    catalog: CozyVisualPage.Catalog,
    catalogIdentity: String,
    logicalCatalogIdentity: String
  )
  final case class ValidatedCatalog(catalog: Catalog, canonicalJson: String, identity: String)
  final case class ValidatedComposition(
    composition: Composition,
    canonicalJson: String,
    identity: String,
    catalog: ValidatedCatalog,
    presentationCatalog: PresentationCatalog
  )
  final case class ValidatedPlan(
    plan: Plan,
    canonicalJson: String,
    catalog: ValidatedCatalog,
    presentationCatalog: PresentationCatalog
  )

  final case class ExplanationFault(code: String, path: String, reason: String)
    extends IllegalArgumentException(s"$code path=$path reason=$reason")

  private val _catalog_schema = "cozy.explanation.catalog.v1"
  private val _composition_schema = "cozy.explanation-composition.v1"
  private val _plan_schema = "cozy.explanation-plan.v1"
  private val _presentation_catalog_schema = "cozy.presentation-semantics.catalog.v1"
  private val _token_pattern = "[A-Za-z0-9][A-Za-z0-9._-]*".r
  private val _sha256_pattern = "[0-9a-f]{64}".r
  private val _identity_pattern = "sha256:[0-9a-f]{64}".r
  private val _value_types = Set("text", "goal-list", "use-case-list", "scenario", "mechanism-list", "mechanism-link-list")
  private val _reserved_ids = Vector(
    "problem-solution", "problem-cause-solution", "current-target", "before-after", "challenge-approach-result",
    "observation-insight-implication", "fact-interpretation-action", "why-what-how", "input-process-output",
    "concept-example", "claim-evidence", "claim-reasons", "question-answer", "principle-mechanism-effect",
    "strategy-execution-outcome", "past-present-future"
  )
  private val _software_product_facts = Vector(
    Definition("context", "text", required = true),
    Definition("goals", "goal-list", required = true),
    Definition("mainScenario", "scenario", required = true),
    Definition("mechanisms", "mechanism-list", required = true),
    Definition("name", "text", required = true),
    Definition("useCases", "use-case-list", required = true),
    Definition("vision", "text", required = true)
  )
  private val _product_overview_roles = Vector(
    Role("vision", 1, required = true), Role("goal", 2, required = true), Role("context", 3, required = true),
    Role("use-case", 4, required = true), Role("main-scenario", 5, required = true)
  )
  private val _product_mechanism_roles = Vector(Role("mechanism", 1, required = true))
  private val _problem_solution_roles = Vector(Role("problem", 1, required = true), Role("solution", 2, required = true))

  def parseCatalogJson(text: String): Catalog = _parse_catalog(_parse_json(text, "$"), "$")

  def parseCompositionJson(text: String): Composition = _parse_composition(_parse_json(text, "$"), "$")

  def parsePlanJson(text: String): Plan = _parse_plan(_parse_json(text, "$"), "$")

  def loadCatalog(input: Path): ValidatedCatalog = {
    val path = _direct_input(input, "input")
    val catalog = _parse_catalog(_parse_json(_read_utf8(path, "input"), "input"), "$")
    val canonical = canonicalCatalogJson(catalog)
    ValidatedCatalog(catalog, canonical, catalogIdentity(catalog))
  }

  def loadComposition(
    input: Path,
    explanationCatalog: Path,
    presentationCatalog: Path,
    bindings: ResourceBindings = ResourceBindings()
  ): ValidatedComposition = {
    val path = _direct_input(input, "input")
    val catalog = loadCatalog(explanationCatalog)
    val presentation = loadPresentationCatalog(presentationCatalog)
    val composition = _parse_composition(_parse_json(_read_utf8(path, "input"), "input"), "$")
    val normalized = _validate_composition(composition, catalog, presentation, bindings)
    val canonical = canonicalCompositionJson(normalized)
    ValidatedComposition(normalized, canonical, compositionIdentity(normalized), catalog, presentation)
  }

  def loadPlan(
    input: Path,
    composition: Path,
    explanationCatalog: Path,
    presentationCatalog: Path,
    bindings: ResourceBindings = ResourceBindings()
  ): ValidatedPlan = {
    val path = _direct_input(input, "input")
    val validatedcomposition = loadComposition(composition, explanationCatalog, presentationCatalog, bindings)
    val parsed = _parse_plan(_parse_json(_read_utf8(path, "input"), "input"), "$")
    val expected = expand(validatedcomposition)
    if (parsed.identity != planIdentity(parsed))
      _fail("EXPLANATION_EXPANSION_INCOMPLETE", "$.identity", "Plan identity does not match its canonical content")
    if (parsed.compositionIdentity != validatedcomposition.identity)
      _fail("EXPLANATION_CATALOG_MISMATCH", "$.compositionIdentity", "Plan Composition selector is not current")
    if (parsed.explanationCatalog != _catalog_selector(validatedcomposition.catalog))
      _fail("EXPLANATION_CATALOG_MISMATCH", "$.explanationCatalog", "Plan explanation catalog selector does not match the named catalog")
    if (parsed.presentationCatalog != _presentation_selector(validatedcomposition.presentationCatalog))
      _fail("EXPLANATION_CATALOG_MISMATCH", "$.presentationCatalog", "Plan presentation catalog selector does not match the named catalog")
    if (canonicalPlanJson(parsed) != canonicalPlanJson(expected))
      _fail("EXPLANATION_EXPANSION_INCOMPLETE", "$", "Plan does not exactly preserve the current validated Composition expansion")
    ValidatedPlan(parsed, canonicalPlanJson(parsed), validatedcomposition.catalog, validatedcomposition.presentationCatalog)
  }

  def loadPresentationCatalog(input: Path): PresentationCatalog = {
    val path = _direct_input(input, "presentation-catalog")
    val catalog = _parse_presentation_catalog(_parse_json(_read_utf8(path, "presentation-catalog"), "presentation-catalog"), "$catalog")
    try {
      CozyVisualPage.validateEmbeddedPageSet(
        CozyVisualPage.canonicalJson(_presentation_validation_probe(catalog)),
        path,
        path.getParent
      )
    } catch {
      case fault: CozyVisualPage.VisualPageFault =>
        _fail("EXPLANATION_PRESENTATION_CATALOG_INVALID", "presentation-catalog", fault.getMessage)
    }
    PresentationCatalog(catalog, CozyVisualPage.catalogIdentity(catalog), CozyVisualPage.logicalCatalogIdentity(catalog))
  }

  private def _presentation_validation_probe(catalog: CozyVisualPage.Catalog): CozyVisualPage.PageSet =
    CozyVisualPage.PageSet(
      "presentation-catalog-probe",
      Vector(CozyVisualPage.Page(
        "presentation-catalog-probe-page",
        "probe",
        "en",
        CozyVisualPage.CatalogReference(catalog.id, catalog.revision),
        CozyVisualPage.Logical(
          "causal-chain",
          Vector(
            CozyVisualPage.Node("probe-cause", "cause", "Cause", Vector.empty),
            CozyVisualPage.Node("probe-effect", "effect", "Effect", Vector.empty)
          ),
          Vector(
            CozyVisualPage.Relation("probe-causes", "causes", "probe-cause", "probe-effect", Vector.empty),
            CozyVisualPage.Relation("probe-enables", "enables", "probe-cause", "probe-effect", Vector.empty)
          )
        ),
        CozyVisualPage.Visual("flow-horizontal", Vector.empty),
        Vector.empty,
        Vector.empty
      ))
    )

  def canonicalCatalogJson(catalog: Catalog): String = _canonical(_catalog_value(catalog))

  def catalogIdentity(catalog: Catalog): String = _identity(_catalog_value(catalog))

  def canonicalCompositionJson(composition: Composition): String = _canonical(_composition_value(composition))

  def compositionIdentity(composition: Composition): String = _identity(_composition_value(composition))

  def canonicalPlanJson(plan: Plan): String = _canonical(_plan_value(plan, includeidentity = true))

  def planIdentity(plan: Plan): String = _identity(_plan_value(plan, includeidentity = false))

  def expand(validated: ValidatedComposition): Plan = {
    val composition = validated.composition
    val parameters = composition.explanation.parameters.map(parameter => parameter.name -> parameter).toMap
    val provenance = PatternProvenance(composition.subject.pattern, composition.explanation.pattern)
    val steps = composition.explanation.steps.map { step =>
      val values = step.parameterSelection.map(selection => parameters(selection.name))
      val parameterprovenance = ParameterProvenance(values, _parameter_provenance_identity(values))
      val provisional = PlanStep(
        step.id,
        step.order,
        step.semanticRole,
        step.claims,
        step.logical,
        step.sourceRefs,
        step.assetRefs,
        provenance,
        parameterprovenance,
        ""
      )
      provisional.copy(identity = _plan_step_identity(provisional))
    }
    val provisional = Plan(
      validated.identity,
      _catalog_selector(validated.catalog),
      _presentation_selector(validated.presentationCatalog),
      composition.subject.pattern,
      composition.explanation.pattern,
      steps,
      ""
    )
    provisional.copy(identity = planIdentity(provisional))
  }

  def execute(args: List[String]): String = args match {
    case ("validate" | "inspect" | "convert") :: rest if CozyExplanationProjection.isProjectionDocumentCommand(rest) =>
      CozyExplanationProjection.execute(args)
    case ("project" | "verify-projection") :: _ => CozyExplanationProjection.execute(args)
    case "validate" :: rest => _execute_document("validate", _command_config(rest, requiresave = false))
    case "inspect" :: rest => _execute_document("inspect", _command_config(rest, requiresave = false))
    case "convert" :: rest => _execute_document("convert", _command_config(rest, requiresave = true))
    case "expand" :: rest => _execute_expand(_command_config(rest, requiresave = true))
    case Nil => _fail("EXPLANATION_COMMAND_INVALID", "$command", "missing explanation action")
    case action :: _ => _fail("EXPLANATION_COMMAND_INVALID", "$command", s"unsupported explanation action: $action")
  }

  private final case class CommandConfig(
    input: Path,
    explanationcatalog: Option[Path],
    presentationcatalog: Option[Path],
    composition: Option[Path],
    save: Option[Path],
    bindings: ResourceBindings
  )

  private def _execute_document(action: String, config: CommandConfig): String = {
    _schema_kind(config.input) match {
      case "catalog" =>
        _require_catalog_only(config, action)
        val catalog = loadCatalog(config.input)
        action match {
          case "validate" => _catalog_summary(catalog)
          case "inspect" => _catalog_inspection(catalog)
          case "convert" =>
            _atomic_write(config.save.getOrElse(_missing_save()), catalog.canonicalJson + "\n")
            _catalog_summary(catalog)
        }
      case "composition" =>
        val validated = _load_command_composition(config)
        action match {
          case "validate" => _composition_summary(validated)
          case "inspect" => _composition_inspection(validated)
          case "convert" =>
            _atomic_write(config.save.getOrElse(_missing_save()), validated.canonicalJson + "\n")
            _composition_summary(validated)
        }
      case "plan" =>
        val validated = _load_command_plan(config)
        action match {
          case "validate" => _plan_summary(validated)
          case "inspect" => _plan_inspection(validated)
          case "convert" =>
            _atomic_write(config.save.getOrElse(_missing_save()), validated.canonicalJson + "\n")
            _plan_summary(validated)
        }
    }
  }

  private def _execute_expand(config: CommandConfig): String = {
    if (config.composition.nonEmpty)
      _fail("EXPLANATION_COMMAND_INVALID", "$command.composition", "expand accepts the Composition only as its positional input")
    val validated = _load_command_composition(config)
    val plan = expand(validated)
    _atomic_write(config.save.getOrElse(_missing_save()), canonicalPlanJson(plan) + "\n")
    _plan_summary(ValidatedPlan(plan, canonicalPlanJson(plan), validated.catalog, validated.presentationCatalog))
  }

  private def _load_command_composition(config: CommandConfig): ValidatedComposition = {
    val catalog = config.explanationcatalog.getOrElse(_missing_companion("--explanation-catalog"))
    val presentation = config.presentationcatalog.getOrElse(_missing_companion("--presentation-catalog"))
    if (config.composition.nonEmpty)
      _fail("EXPLANATION_COMMAND_INVALID", "$command.composition", "--composition is admitted only for Plan operations")
    loadComposition(config.input, catalog, presentation, config.bindings)
  }

  private def _load_command_plan(config: CommandConfig): ValidatedPlan = {
    val composition = config.composition.getOrElse(_missing_companion("--composition"))
    val catalog = config.explanationcatalog.getOrElse(_missing_companion("--explanation-catalog"))
    val presentation = config.presentationcatalog.getOrElse(_missing_companion("--presentation-catalog"))
    loadPlan(config.input, composition, catalog, presentation, config.bindings)
  }

  private def _require_catalog_only(config: CommandConfig, action: String): Unit = {
    if (config.explanationcatalog.nonEmpty || config.presentationcatalog.nonEmpty || config.composition.nonEmpty ||
      config.bindings.sources.nonEmpty || config.bindings.assets.nonEmpty)
      _fail("EXPLANATION_COMMAND_INVALID", "$command", s"$action catalog accepts no companions")
  }

  private def _catalog_summary(catalog: ValidatedCatalog): String = Vector(
    s"schema: ${_catalog_schema}",
    "version: 1",
    s"id: ${catalog.catalog.id}",
    s"revision: ${catalog.catalog.revision}",
    s"identity: ${catalog.identity}"
  ).mkString("\n")

  private def _catalog_inspection(catalog: ValidatedCatalog): String =
    (_catalog_summary(catalog).split("\n").toVector ++
      Vector(s"subjectPatterns: ${catalog.catalog.subjectPatterns.map(pattern => s"${pattern.id}@${pattern.version}").mkString(",")}") ++
      catalog.catalog.explanationPatterns.map(pattern =>
        s"explanationPattern: ${pattern.id}@${pattern.version} roles=${pattern.roles.map(_.id).mkString(",")}"))
      .mkString("\n")

  private def _composition_summary(validated: ValidatedComposition): String = Vector(
    s"schema: ${_composition_schema}",
    "version: 1",
    s"id: ${validated.composition.id}",
    s"identity: ${validated.identity}",
    s"steps: ${validated.composition.explanation.steps.size}"
  ).mkString("\n")

  private def _composition_inspection(validated: ValidatedComposition): String =
    (_composition_summary(validated).split("\n").toVector ++ validated.composition.explanation.steps.map(step =>
      s"step: ${step.order}:${step.id} role=${step.semanticRole} claims=${step.claims.size} logical=${step.logical.pattern}"))
      .mkString("\n")

  private def _plan_summary(validated: ValidatedPlan): String = Vector(
    s"schema: ${_plan_schema}",
    "version: 1",
    s"identity: ${validated.plan.identity}",
    s"compositionIdentity: ${validated.plan.compositionIdentity}",
    s"presentationLogicalCatalogIdentity: ${validated.plan.presentationCatalog.identity}",
    s"steps: ${validated.plan.steps.size}"
  ).mkString("\n")

  private def _plan_inspection(validated: ValidatedPlan): String =
    (_plan_summary(validated).split("\n").toVector ++ validated.plan.steps.map(step =>
      s"step: ${step.order}:${step.id} role=${step.semanticRole} identity=${step.identity} claims=${step.claims.size}"))
      .mkString("\n")

  private def _command_config(args: List[String], requiresave: Boolean): CommandConfig = {
    var input = Option.empty[Path]
    var explanationcatalog = Option.empty[Path]
    var presentationcatalog = Option.empty[Path]
    var composition = Option.empty[Path]
    var save = Option.empty[Path]
    var sources = Map.empty[String, Path]
    var assets = Map.empty[String, Path]
    var rest = args
    while (rest.nonEmpty) {
      rest match {
        case option :: value :: tail if Set("--explanation-catalog", "--presentation-catalog", "--composition", "--save", "--source", "--asset").contains(option) =>
          option match {
            case "--explanation-catalog" =>
              if (explanationcatalog.nonEmpty) _fail("EXPLANATION_COMMAND_INVALID", "$command.explanationCatalog", "--explanation-catalog may appear once")
              explanationcatalog = Some(_cli_path(value, "$command.explanationCatalog"))
            case "--presentation-catalog" =>
              if (presentationcatalog.nonEmpty) _fail("EXPLANATION_COMMAND_INVALID", "$command.presentationCatalog", "--presentation-catalog may appear once")
              presentationcatalog = Some(_cli_path(value, "$command.presentationCatalog"))
            case "--composition" =>
              if (composition.nonEmpty) _fail("EXPLANATION_COMMAND_INVALID", "$command.composition", "--composition may appear once")
              composition = Some(_cli_path(value, "$command.composition"))
            case "--save" =>
              if (!requiresave) _fail("EXPLANATION_COMMAND_INVALID", "$command.save", "--save is supported only by convert or expand")
              if (save.nonEmpty) _fail("EXPLANATION_COMMAND_INVALID", "$command.save", "--save may appear once")
              save = Some(_cli_path(value, "$command.save"))
            case "--source" => sources = _add_binding(sources, value, "$command.source")
            case "--asset" => assets = _add_binding(assets, value, "$command.asset")
          }
          rest = tail
        case option :: tail if option.startsWith("--explanation-catalog=") =>
          if (explanationcatalog.nonEmpty) _fail("EXPLANATION_COMMAND_INVALID", "$command.explanationCatalog", "--explanation-catalog may appear once")
          explanationcatalog = Some(_cli_path(option.drop("--explanation-catalog=".length), "$command.explanationCatalog"))
          rest = tail
        case option :: tail if option.startsWith("--presentation-catalog=") =>
          if (presentationcatalog.nonEmpty) _fail("EXPLANATION_COMMAND_INVALID", "$command.presentationCatalog", "--presentation-catalog may appear once")
          presentationcatalog = Some(_cli_path(option.drop("--presentation-catalog=".length), "$command.presentationCatalog"))
          rest = tail
        case option :: tail if option.startsWith("--composition=") =>
          if (composition.nonEmpty) _fail("EXPLANATION_COMMAND_INVALID", "$command.composition", "--composition may appear once")
          composition = Some(_cli_path(option.drop("--composition=".length), "$command.composition"))
          rest = tail
        case option :: tail if option.startsWith("--save=") =>
          if (!requiresave) _fail("EXPLANATION_COMMAND_INVALID", "$command.save", "--save is supported only by convert or expand")
          if (save.nonEmpty) _fail("EXPLANATION_COMMAND_INVALID", "$command.save", "--save may appear once")
          save = Some(_cli_path(option.drop("--save=".length), "$command.save"))
          rest = tail
        case option :: tail if option.startsWith("--source=") =>
          sources = _add_binding(sources, option.drop("--source=".length), "$command.source")
          rest = tail
        case option :: tail if option.startsWith("--asset=") =>
          assets = _add_binding(assets, option.drop("--asset=".length), "$command.asset")
          rest = tail
        case option :: _ if option.startsWith("--") =>
          _fail("EXPLANATION_COMMAND_INVALID", "$command", s"unsupported or valueless option: $option")
        case value :: tail =>
          if (input.nonEmpty) _fail("EXPLANATION_COMMAND_INVALID", "$command.input", "exactly one direct input file is required")
          input = Some(_cli_path(value, "$command.input"))
          rest = tail
      }
    }
    if (requiresave && save.isEmpty) _missing_save()
    CommandConfig(
      input.getOrElse(_fail("EXPLANATION_COMMAND_INVALID", "$command.input", "missing direct input file")),
      explanationcatalog,
      presentationcatalog,
      composition,
      save,
      ResourceBindings(sources, assets)
    )
  }

  private def _add_binding(bindings: Map[String, Path], value: String, path: String): Map[String, Path] = {
    val index = Option(value).map(_.indexOf('=')).getOrElse(-1)
    if (index <= 0 || index == value.length - 1)
      _fail("EXPLANATION_REFERENCE_INVALID", path, "binding must be exactly <id>=<file>")
    val id = value.take(index)
    val file = value.drop(index + 1)
    _token(id, s"$path.id")
    if (bindings.contains(id)) _fail("EXPLANATION_REFERENCE_INVALID", path, s"duplicate binding id: $id")
    bindings + (id -> _cli_path(file, path))
  }

  private def _schema_kind(input: Path): String = {
    val path = _direct_input(input, "input")
    val fields = _object(_parse_json(_read_utf8(path, "input"), "input"), "$")
    _string(_field(fields, "schema", "$"), "$.schema") match {
      case `_catalog_schema` => "catalog"
      case `_composition_schema` => "composition"
      case `_plan_schema` => "plan"
      case schema => _fail("EXPLANATION_SCHEMA_INVALID", "$.schema", s"unsupported explanation schema: $schema")
    }
  }

  private def _missing_companion(option: String): Nothing =
    _fail("EXPLANATION_COMMAND_INVALID", "$command", s"missing required $option")

  private def _missing_save(): Nothing =
    _fail("EXPLANATION_COMMAND_INVALID", "$command.save", "selected operation requires exactly one --save")

  private def _parse_catalog(value: JsonValue, path: String): Catalog = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("schema", "version", "id", "revision", "subjectPatterns", "explanationPatterns", "reservedNarrativeArgumentIds"), path)
    _schema_version(fields, _catalog_schema, path)
    val catalog = Catalog(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _positive_int(_field(fields, "revision", path), s"$path.revision"),
      _array(_field(fields, "subjectPatterns", path), s"$path.subjectPatterns").zipWithIndex.map { case (item, index) => _parse_subject_pattern(item, s"$path.subjectPatterns[$index]") },
      _array(_field(fields, "explanationPatterns", path), s"$path.explanationPatterns").zipWithIndex.map { case (item, index) => _parse_explanation_pattern(item, s"$path.explanationPatterns[$index]") },
      _references(_field(fields, "reservedNarrativeArgumentIds", path), s"$path.reservedNarrativeArgumentIds")
    )
    if (catalog.subjectPatterns.isEmpty || catalog.explanationPatterns.isEmpty)
      _fail("EXPLANATION_SCHEMA_INVALID", path, "catalog pattern arrays must be nonempty")
    _unique(catalog.subjectPatterns.map(pattern => s"${pattern.id}@${pattern.version}"), s"$path.subjectPatterns", "Subject Pattern")
    _unique(catalog.explanationPatterns.map(pattern => s"${pattern.id}@${pattern.version}"), s"$path.explanationPatterns", "Explanation Pattern")
    val normalized = Catalog(
      catalog.id,
      catalog.revision,
      catalog.subjectPatterns.map(pattern => pattern.copy(factDefinitions = _normalize_definitions(pattern.factDefinitions, s"$path.subjectPatterns.${pattern.id}.factDefinitions"))).sortBy(pattern => pattern.id -> pattern.version),
      catalog.explanationPatterns.map { pattern =>
        pattern.copy(
          compatibleSubjects = _normalize_pattern_references(pattern.compatibleSubjects, s"$path.explanationPatterns.${pattern.id}.compatibleSubjects"),
          parameterDefinitions = _normalize_definitions(pattern.parameterDefinitions, s"$path.explanationPatterns.${pattern.id}.parameterDefinitions"),
          roles = _normalize_roles(pattern.roles, s"$path.explanationPatterns.${pattern.id}.roles")
        )
      }.sortBy(pattern => pattern.id -> pattern.version),
      catalog.reservedNarrativeArgumentIds
    )
    if (normalized.reservedNarrativeArgumentIds != _reserved_ids)
      _fail("EXPLANATION_SCHEMA_INVALID", s"$path.reservedNarrativeArgumentIds", "reserved Narrative / Argument IDs must be the fixed v1 order")
    _validate_catalog_contract(normalized, path)
    normalized
  }

  private def _parse_subject_pattern(value: JsonValue, path: String): SubjectPattern = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "version", "factDefinitions"), path)
    SubjectPattern(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _positive_int(_field(fields, "version", path), s"$path.version"),
      _array(_field(fields, "factDefinitions", path), s"$path.factDefinitions").zipWithIndex.map { case (item, index) => _parse_definition(item, s"$path.factDefinitions[$index]") }
    )
  }

  private def _parse_explanation_pattern(value: JsonValue, path: String): ExplanationPattern = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "version", "compatibleSubjects", "parameterDefinitions", "roles"), path)
    ExplanationPattern(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _positive_int(_field(fields, "version", path), s"$path.version"),
      _array(_field(fields, "compatibleSubjects", path), s"$path.compatibleSubjects").zipWithIndex.map { case (item, index) => _parse_pattern_reference(item, s"$path.compatibleSubjects[$index]") },
      _array(_field(fields, "parameterDefinitions", path), s"$path.parameterDefinitions").zipWithIndex.map { case (item, index) => _parse_definition(item, s"$path.parameterDefinitions[$index]") },
      _array(_field(fields, "roles", path), s"$path.roles").zipWithIndex.map { case (item, index) => _parse_role(item, s"$path.roles[$index]") }
    )
  }

  private def _parse_definition(value: JsonValue, path: String): Definition = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("name", "type", "required"), path)
    val valuetype = _token(_string(_field(fields, "type", path), s"$path.type"), s"$path.type")
    if (!_value_types.contains(valuetype)) _fail("EXPLANATION_SCHEMA_INVALID", s"$path.type", s"unsupported v1 definition type: $valuetype")
    Definition(
      _token(_string(_field(fields, "name", path), s"$path.name"), s"$path.name"),
      valuetype,
      _boolean(_field(fields, "required", path), s"$path.required")
    )
  }

  private def _parse_role(value: JsonValue, path: String): Role = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "order", "required"), path)
    Role(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _positive_int(_field(fields, "order", path), s"$path.order"),
      _boolean(_field(fields, "required", path), s"$path.required")
    )
  }

  private def _parse_pattern_reference(value: JsonValue, path: String): PatternReference = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "version"), path)
    PatternReference(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _positive_int(_field(fields, "version", path), s"$path.version")
    )
  }

  private def _normalize_definitions(values: Vector[Definition], path: String): Vector[Definition] = {
    if (values.isEmpty) Vector.empty
    else {
      _unique(values.map(_.name), path, "definition name")
      values.sortBy(_.name)
    }
  }

  private def _normalize_pattern_references(values: Vector[PatternReference], path: String): Vector[PatternReference] = {
    if (values.isEmpty) _fail("EXPLANATION_PATTERN_INCOMPATIBLE", path, "compatibleSubjects must be nonempty")
    _unique(values.map(value => s"${value.id}@${value.version}"), path, "compatible Subject Pattern")
    values.sortBy(value => value.id -> value.version)
  }

  private def _normalize_roles(values: Vector[Role], path: String): Vector[Role] = {
    if (values.isEmpty) _fail("EXPLANATION_SCHEMA_INVALID", path, "roles must be nonempty")
    _unique(values.map(_.id), path, "role id")
    _unique(values.map(_.order.toString), path, "role order")
    val normalized = values.sortBy(_.order)
    if (normalized.map(_.order) != (1 to normalized.size).toVector)
      _fail("EXPLANATION_SCHEMA_INVALID", path, "role order must be contiguous from 1")
    normalized
  }

  private def _validate_catalog_contract(catalog: Catalog, path: String): Unit = {
    val expectedsubject = Vector(SubjectPattern("software-product", 1, _software_product_facts))
    val expectedexplanation = Vector(
      ExplanationPattern("problem-solution", 1, Vector(PatternReference("software-product", 1)), Vector(Definition("problem", "text", true), Definition("solution", "text", true)), _problem_solution_roles),
      ExplanationPattern("product-mechanism", 1, Vector(PatternReference("software-product", 1)), Vector(Definition("mechanismLinks", "mechanism-link-list", true)), _product_mechanism_roles),
      ExplanationPattern("product-overview", 1, Vector(PatternReference("software-product", 1)), Vector.empty, _product_overview_roles)
    )
    if (catalog.subjectPatterns != expectedsubject)
      _fail("EXPLANATION_SCHEMA_INVALID", s"$path.subjectPatterns", "catalog must define exactly the accepted software-product Subject Pattern")
    if (catalog.explanationPatterns != expectedexplanation)
      _fail("EXPLANATION_SCHEMA_INVALID", s"$path.explanationPatterns", "catalog must define exactly the accepted v1 Explanation Patterns")
  }

  private def _parse_composition(value: JsonValue, path: String): Composition = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("schema", "version", "id", "explanationCatalog", "subject", "explanation", "sources", "assets"), path)
    _schema_version(fields, _composition_schema, path)
    val composition = Composition(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _parse_catalog_selector(_field(fields, "explanationCatalog", path), s"$path.explanationCatalog"),
      _parse_subject(_field(fields, "subject", path), s"$path.subject"),
      _parse_explanation(_field(fields, "explanation", path), s"$path.explanation"),
      _array(_field(fields, "sources", path), s"$path.sources").zipWithIndex.map { case (item, index) => _parse_source_declaration(item, s"$path.sources[$index]") },
      _array(_field(fields, "assets", path), s"$path.assets").zipWithIndex.map { case (item, index) => _parse_asset_declaration(item, s"$path.assets[$index]") }
    )
    _unique(composition.sources.map(_.id), s"$path.sources", "source id")
    _unique(composition.assets.map(_.id), s"$path.assets", "asset id")
    composition
  }

  private def _parse_catalog_selector(value: JsonValue, path: String): CatalogSelector = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "revision", "identity"), path)
    CatalogSelector(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _positive_int(_field(fields, "revision", path), s"$path.revision"),
      _identity_text(_string(_field(fields, "identity", path), s"$path.identity"), s"$path.identity")
    )
  }

  private def _parse_subject(value: JsonValue, path: String): Subject = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("pattern", "facts"), path)
    Subject(
      _parse_pattern_reference(_field(fields, "pattern", path), s"$path.pattern"),
      _array(_field(fields, "facts", path), s"$path.facts").zipWithIndex.map { case (item, index) => _parse_fact(item, s"$path.facts[$index]") }
    )
  }

  private def _parse_fact(value: JsonValue, path: String): Fact = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("name", "value", "sourceRefs", "assetRefs"), path)
    Fact(
      _token(_string(_field(fields, "name", path), s"$path.name"), s"$path.name"),
      _normalize_json_value(_field(fields, "value", path)),
      _references(_field(fields, "sourceRefs", path), s"$path.sourceRefs"),
      _references(_field(fields, "assetRefs", path), s"$path.assetRefs")
    )
  }

  private def _parse_explanation(value: JsonValue, path: String): Explanation = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("pattern", "parameters", "steps"), path)
    Explanation(
      _parse_pattern_reference(_field(fields, "pattern", path), s"$path.pattern"),
      _array(_field(fields, "parameters", path), s"$path.parameters").zipWithIndex.map { case (item, index) => _parse_parameter(item, s"$path.parameters[$index]") },
      _array(_field(fields, "steps", path), s"$path.steps").zipWithIndex.map { case (item, index) => _parse_composition_step(item, s"$path.steps[$index]") }
    )
  }

  private def _parse_parameter(value: JsonValue, path: String): Parameter = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("name", "value"), path)
    Parameter(_token(_string(_field(fields, "name", path), s"$path.name"), s"$path.name"), _normalize_json_value(_field(fields, "value", path)))
  }

  private def _parse_composition_step(value: JsonValue, path: String): CompositionStep = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "order", "semanticRole", "claims", "logical", "sourceRefs", "assetRefs", "parameterSelection"), path)
    val step = CompositionStep(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _positive_int(_field(fields, "order", path), s"$path.order"),
      _token(_string(_field(fields, "semanticRole", path), s"$path.semanticRole"), s"$path.semanticRole"),
      _array(_field(fields, "claims", path), s"$path.claims").zipWithIndex.map { case (item, index) => _parse_claim(item, s"$path.claims[$index]") },
      _parse_logical(_field(fields, "logical", path), s"$path.logical"),
      _references(_field(fields, "sourceRefs", path), s"$path.sourceRefs"),
      _references(_field(fields, "assetRefs", path), s"$path.assetRefs"),
      _array(_field(fields, "parameterSelection", path), s"$path.parameterSelection").zipWithIndex.map { case (item, index) => _parse_parameter_selection(item, s"$path.parameterSelection[$index]") }
    )
    if (step.claims.isEmpty) _fail("EXPLANATION_EXPANSION_INCOMPLETE", s"$path.claims", "each authored step requires nonempty claims")
    _unique(step.claims.map(_.id), s"$path.claims", "claim id")
    _unique(step.parameterSelection.map(_.name), s"$path.parameterSelection", "parameter selection")
    step
  }

  private def _parse_claim(value: JsonValue, path: String): Claim = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "text", "emphasis", "sourceRefs", "assetRefs"), path)
    val emphasis = _token(_string(_field(fields, "emphasis", path), s"$path.emphasis"), s"$path.emphasis")
    if (!Set("supporting", "primary").contains(emphasis)) _fail("EXPLANATION_SCHEMA_INVALID", s"$path.emphasis", "emphasis must be supporting or primary")
    Claim(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _text(_string(_field(fields, "text", path), s"$path.text"), s"$path.text"),
      emphasis,
      _references(_field(fields, "sourceRefs", path), s"$path.sourceRefs"),
      _references(_field(fields, "assetRefs", path), s"$path.assetRefs")
    )
  }

  private def _parse_logical(value: JsonValue, path: String): CozyVisualPage.Logical = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("pattern", "nodes", "relations"), path)
    CozyVisualPage.Logical(
      _token(_string(_field(fields, "pattern", path), s"$path.pattern"), s"$path.pattern"),
      _array(_field(fields, "nodes", path), s"$path.nodes").zipWithIndex.map { case (item, index) => _parse_node(item, s"$path.nodes[$index]") },
      _array(_field(fields, "relations", path), s"$path.relations").zipWithIndex.map { case (item, index) => _parse_relation(item, s"$path.relations[$index]") }
    )
  }

  private def _parse_node(value: JsonValue, path: String): CozyVisualPage.Node = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "role", "label", "sourceRefs"), path)
    CozyVisualPage.Node(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _token(_string(_field(fields, "role", path), s"$path.role"), s"$path.role"),
      _text(_string(_field(fields, "label", path), s"$path.label"), s"$path.label"),
      _references(_field(fields, "sourceRefs", path), s"$path.sourceRefs")
    )
  }

  private def _parse_relation(value: JsonValue, path: String): CozyVisualPage.Relation = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "type", "from", "to", "sourceRefs"), path)
    CozyVisualPage.Relation(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _token(_string(_field(fields, "type", path), s"$path.type"), s"$path.type"),
      _token(_string(_field(fields, "from", path), s"$path.from"), s"$path.from"),
      _token(_string(_field(fields, "to", path), s"$path.to"), s"$path.to"),
      _references(_field(fields, "sourceRefs", path), s"$path.sourceRefs")
    )
  }

  private def _parse_parameter_selection(value: JsonValue, path: String): ParameterSelection = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("name"), path)
    ParameterSelection(_token(_string(_field(fields, "name", path), s"$path.name"), s"$path.name"))
  }

  private def _parse_source_declaration(value: JsonValue, path: String): SourceDeclaration = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "sha256"), path)
    SourceDeclaration(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _sha256_text(_string(_field(fields, "sha256", path), s"$path.sha256"), s"$path.sha256")
    )
  }

  private def _parse_asset_declaration(value: JsonValue, path: String): AssetDeclaration = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "mediaType", "sha256"), path)
    AssetDeclaration(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _text(_string(_field(fields, "mediaType", path), s"$path.mediaType"), s"$path.mediaType"),
      _sha256_text(_string(_field(fields, "sha256", path), s"$path.sha256"), s"$path.sha256")
    )
  }

  private def _validate_composition(
    composition: Composition,
    catalog: ValidatedCatalog,
    presentation: PresentationCatalog,
    bindings: ResourceBindings
  ): Composition = {
    if (composition.explanationCatalog != _catalog_selector(catalog))
      _fail("EXPLANATION_CATALOG_MISMATCH", "$.explanationCatalog", "Composition catalog selector does not match the named catalog")
    _validate_bindings(composition.sources, composition.assets, bindings)
    val sourceids = composition.sources.map(_.id).toSet
    val assetids = composition.assets.map(_.id).toSet
    val subjectpattern = catalog.catalog.subjectPatterns.find(_ == SubjectPattern(composition.subject.pattern.id, composition.subject.pattern.version, _software_product_facts)).getOrElse(
      _fail("EXPLANATION_CATALOG_MISMATCH", "$.subject.pattern", "selected Subject Pattern does not resolve in the named catalog")
    )
    val explanationpattern = catalog.catalog.explanationPatterns.find(pattern => PatternReference(pattern.id, pattern.version) == composition.explanation.pattern).getOrElse(
      _fail("EXPLANATION_CATALOG_MISMATCH", "$.explanation.pattern", "selected Explanation Pattern does not resolve in the named catalog")
    )
    if (!explanationpattern.compatibleSubjects.contains(composition.subject.pattern))
      _fail("EXPLANATION_PATTERN_INCOMPATIBLE", "$.explanation.pattern", "selected Explanation Pattern is incompatible with the selected Subject Pattern")
    _unique(composition.subject.facts.map(_.name), "$.subject.facts", "fact name")
    val facts = composition.subject.facts.map(fact => fact.name -> fact).toMap
    val definitions = subjectpattern.factDefinitions.map(definition => definition.name -> definition).toMap
    composition.subject.facts.foreach { fact =>
      val definition = definitions.getOrElse(fact.name,
        _fail("EXPLANATION_FACT_INVALID", s"$$.subject.facts.${fact.name}", "fact is not defined by the selected Subject Pattern")
      )
      _validate_fact_value(fact.value, definition.valueType, sourceids, assetids, s"$$.subject.facts.${fact.name}.value")
      _validate_references(fact.sourceRefs, sourceids, s"$$.subject.facts.${fact.name}.sourceRefs")
      _validate_references(fact.assetRefs, assetids, s"$$.subject.facts.${fact.name}.assetRefs")
    }
    subjectpattern.factDefinitions.filter(_.required).foreach { definition =>
      if (!facts.contains(definition.name)) _fail("EXPLANATION_FACT_INVALID", "$.subject.facts", s"missing required fact: ${definition.name}")
    }
    _unique(composition.explanation.parameters.map(_.name), "$.explanation.parameters", "parameter name")
    val parameters = composition.explanation.parameters.map(parameter => parameter.name -> parameter).toMap
    val parameterdefinitions = explanationpattern.parameterDefinitions.map(definition => definition.name -> definition).toMap
    composition.explanation.parameters.foreach { parameter =>
      val definition = parameterdefinitions.getOrElse(parameter.name,
        _fail("EXPLANATION_PARAMETER_INVALID", s"$$.explanation.parameters.${parameter.name}", "parameter is not defined by the selected Explanation Pattern")
      )
      _validate_fact_value(parameter.value, definition.valueType, sourceids, assetids, s"$$.explanation.parameters.${parameter.name}.value")
    }
    explanationpattern.parameterDefinitions.filter(_.required).foreach { definition =>
      if (!parameters.contains(definition.name)) _fail("EXPLANATION_PARAMETER_INVALID", "$.explanation.parameters", s"missing required parameter: ${definition.name}")
    }
    if (composition.explanation.steps.isEmpty) _fail("EXPLANATION_EXPANSION_INCOMPLETE", "$.explanation.steps", "authored steps must be nonempty")
    _unique(composition.explanation.steps.map(_.id), "$.explanation.steps", "step id")
    if (composition.explanation.steps.map(_.order) != (1 to composition.explanation.steps.size).toVector)
      _fail("EXPLANATION_EXPANSION_INCOMPLETE", "$.explanation.steps", "step order must be contiguous and match authored array order")
    if (composition.explanation.steps.map(_.semanticRole) != explanationpattern.roles.map(_.id))
      _fail("EXPLANATION_EXPANSION_INCOMPLETE", "$.explanation.steps", "authored roles must exactly match the selected Explanation Pattern order")
    composition.explanation.steps.zipWithIndex.foreach { case (step, index) =>
      _validate_step(step, explanationpattern, parameters, sourceids, assetids, presentation, s"$$.explanation.steps[$index]")
    }
    if (explanationpattern.id == "product-mechanism")
      _validate_mechanism_links(parameters("mechanismLinks").value, facts, s"$$.explanation.parameters.mechanismLinks.value")
    composition
  }

  private def _validate_step(
    step: CompositionStep,
    pattern: ExplanationPattern,
    parameters: Map[String, Parameter],
    sourceids: Set[String],
    assetids: Set[String],
    presentation: PresentationCatalog,
    path: String
  ): Unit = {
    step.claims.foreach { claim =>
      _validate_references(claim.sourceRefs, sourceids, s"$path.claims.${claim.id}.sourceRefs")
      _validate_references(claim.assetRefs, assetids, s"$path.claims.${claim.id}.assetRefs")
    }
    _validate_references(step.sourceRefs, sourceids, s"$path.sourceRefs")
    _validate_references(step.assetRefs, assetids, s"$path.assetRefs")
    _validate_p36_logical(step.logical, presentation.catalog, sourceids, s"$path.logical")
    step.parameterSelection.foreach { selection =>
      if (!parameters.contains(selection.name))
        _fail("EXPLANATION_PARAMETER_INVALID", s"$path.parameterSelection", s"selected parameter is absent: ${selection.name}")
    }
    val selected = step.parameterSelection.map(_.name)
    val expected = pattern.id match {
      case "product-overview" => Vector.empty[String]
      case "product-mechanism" => Vector("mechanismLinks")
      case "problem-solution" => step.semanticRole match {
        case "problem" => Vector("problem")
        case "solution" => Vector("solution")
        case _ => _fail("EXPLANATION_EXPANSION_INCOMPLETE", s"$path.semanticRole", "problem-solution has only problem and solution roles")
      }
      case _ => _fail("EXPLANATION_EXPANSION_INCOMPLETE", s"$path.semanticRole", s"unsupported selected Explanation Pattern: ${pattern.id}")
    }
    if (selected != expected)
      _fail("EXPLANATION_PARAMETER_INVALID", s"$path.parameterSelection", "step parameter selections must be exactly the selected pattern role requirements")
  }

  private def _validate_fact_value(
    value: JsonValue,
    valuetype: String,
    sourceids: Set[String],
    assetids: Set[String],
    path: String
  ): Unit = valuetype match {
    case "text" => _text(_string(value, path), path)
    case "goal-list" | "use-case-list" | "mechanism-list" => _validate_labeled_list(value, sourceids, assetids, path)
    case "scenario" => _validate_scenario(value, sourceids, assetids, path)
    case "mechanism-link-list" => _validate_mechanism_link_list(value, sourceids, assetids, path)
    case _ => _fail("EXPLANATION_FACT_INVALID", path, s"unsupported selected value type: $valuetype")
  }

  private def _validate_labeled_list(value: JsonValue, sourceids: Set[String], assetids: Set[String], path: String): Unit = {
    val values = _nonempty_array(value, path)
    val ids = values.zipWithIndex.map { case (item, index) =>
      val fields = _object(item, s"$path[$index]")
      _exact_fields(fields, Vector("id", "label", "sourceRefs", "assetRefs"), s"$path[$index]")
      val id = _token(_string(_field(fields, "id", s"$path[$index]"), s"$path[$index].id"), s"$path[$index].id")
      _text(_string(_field(fields, "label", s"$path[$index]"), s"$path[$index].label"), s"$path[$index].label")
      _validate_references(_references(_field(fields, "sourceRefs", s"$path[$index]"), s"$path[$index].sourceRefs"), sourceids, s"$path[$index].sourceRefs")
      _validate_references(_references(_field(fields, "assetRefs", s"$path[$index]"), s"$path[$index].assetRefs"), assetids, s"$path[$index].assetRefs")
      id
    }
    _unique(ids, path, "list entry id")
  }

  private def _validate_scenario(value: JsonValue, sourceids: Set[String], assetids: Set[String], path: String): Unit = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "label", "steps"), path)
    _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id")
    _text(_string(_field(fields, "label", path), s"$path.label"), s"$path.label")
    _validate_labeled_list(_field(fields, "steps", path), sourceids, assetids, s"$path.steps")
  }

  private def _validate_mechanism_link_list(value: JsonValue, sourceids: Set[String], assetids: Set[String], path: String): Unit = {
    val values = _nonempty_array(value, path)
    val ids = values.zipWithIndex.map { case (item, index) =>
      val itempath = s"$path[$index]"
      val fields = _object(item, itempath)
      _exact_fields(fields, Vector("id", "goalId", "useCaseId", "mechanismId", "sourceRefs", "assetRefs"), itempath)
      val id = _token(_string(_field(fields, "id", itempath), s"$itempath.id"), s"$itempath.id")
      _token(_string(_field(fields, "goalId", itempath), s"$itempath.goalId"), s"$itempath.goalId")
      _token(_string(_field(fields, "useCaseId", itempath), s"$itempath.useCaseId"), s"$itempath.useCaseId")
      _token(_string(_field(fields, "mechanismId", itempath), s"$itempath.mechanismId"), s"$itempath.mechanismId")
      _validate_references(_references(_field(fields, "sourceRefs", itempath), s"$itempath.sourceRefs"), sourceids, s"$itempath.sourceRefs")
      _validate_references(_references(_field(fields, "assetRefs", itempath), s"$itempath.assetRefs"), assetids, s"$itempath.assetRefs")
      id
    }
    _unique(ids, path, "mechanism link id")
  }

  private def _validate_mechanism_links(value: JsonValue, facts: Map[String, Fact], path: String): Unit = {
    val goals = _labeled_ids(facts("goals").value, "$.subject.facts.goals.value")
    val usecases = _labeled_ids(facts("useCases").value, "$.subject.facts.useCases.value")
    val mechanisms = _labeled_ids(facts("mechanisms").value, "$.subject.facts.mechanisms.value")
    _array(value, path).zipWithIndex.foreach { case (item, index) =>
      val itempath = s"$path[$index]"
      val fields = _object(item, itempath)
      val goal = _string(_field(fields, "goalId", itempath), s"$itempath.goalId")
      val usecase = _string(_field(fields, "useCaseId", itempath), s"$itempath.useCaseId")
      val mechanism = _string(_field(fields, "mechanismId", itempath), s"$itempath.mechanismId")
      if (!goals.contains(goal) || !usecases.contains(usecase) || !mechanisms.contains(mechanism))
        _fail("EXPLANATION_REFERENCE_INVALID", itempath, "product-mechanism link must resolve declared goal, use case, and mechanism IDs")
    }
  }

  private def _labeled_ids(value: JsonValue, path: String): Set[String] =
    _array(value, path).map { item =>
      val fields = _object(item, path)
      _string(_field(fields, "id", path), s"$path.id")
    }.toSet

  private def _validate_bindings(
    sources: Vector[SourceDeclaration],
    assets: Vector[AssetDeclaration],
    bindings: ResourceBindings
  ): Unit = {
    val declaredsources = sources.map(_.id).toSet
    val declaredassets = assets.map(_.id).toSet
    if (bindings.sources.keySet != declaredsources)
      _fail("EXPLANATION_REFERENCE_INVALID", "$command.source", "each declared source requires exactly one direct --source binding and no extras")
    if (bindings.assets.keySet != declaredassets)
      _fail("EXPLANATION_REFERENCE_INVALID", "$command.asset", "each declared asset requires exactly one direct --asset binding and no extras")
    sources.foreach { source =>
      if (_sha256_file(_direct_input(bindings.sources(source.id), s"source.${source.id}")) != source.sha256)
        _fail("EXPLANATION_ASSET_STALE", s"$$.sources.${source.id}.sha256", "direct source bytes do not match the declared SHA-256")
    }
    assets.foreach { asset =>
      if (_sha256_file(_direct_input(bindings.assets(asset.id), s"asset.${asset.id}")) != asset.sha256)
        _fail("EXPLANATION_ASSET_STALE", s"$$.assets.${asset.id}.sha256", "direct asset bytes do not match the declared SHA-256")
    }
  }

  private def _validate_references(references: Vector[String], declared: Set[String], path: String): Unit =
    references.foreach { reference =>
      if (!declared.contains(reference)) _fail("EXPLANATION_REFERENCE_INVALID", path, s"reference does not resolve exactly once: $reference")
    }

  private def _validate_p36_logical(
    logical: CozyVisualPage.Logical,
    catalog: CozyVisualPage.Catalog,
    sourceids: Set[String],
    path: String
  ): Unit = {
    _unique(logical.nodes.map(_.id), s"$path.nodes", "node id")
    _unique(logical.relations.map(_.id), s"$path.relations", "Relation id")
    logical.nodes.foreach(node => _validate_references(node.sourceRefs, sourceids, s"$path.nodes.${node.id}.sourceRefs"))
    val nodes = logical.nodes.map(node => node.id -> node).toMap
    logical.relations.foreach { relation =>
      if (!nodes.contains(relation.from) || !nodes.contains(relation.to))
        _fail("EXPLANATION_REFERENCE_INVALID", s"$path.relations.${relation.id}", "Relation endpoints must resolve declared nodes")
      _validate_references(relation.sourceRefs, sourceids, s"$path.relations.${relation.id}.sourceRefs")
    }
    val pattern = catalog.logicalPatterns.find(_.id == logical.pattern).getOrElse(
      _fail("EXPLANATION_REFERENCE_INVALID", s"$path.pattern", s"unknown Phase-36 Logical Pattern: ${logical.pattern}")
    )
    val allowedroles = pattern.nodeRoles.map(_.role).toSet
    logical.nodes.foreach { node =>
      if (!allowedroles.contains(node.role)) _fail("EXPLANATION_REFERENCE_INVALID", s"$path.nodes.${node.id}.role", s"node role is not admitted by ${pattern.id}: ${node.role}")
    }
    pattern.nodeRoles.foreach { role =>
      val count = logical.nodes.count(_.role == role.role)
      if (count < role.min || count > role.max)
        _fail("EXPLANATION_REFERENCE_INVALID", s"$path.nodes", s"role ${role.role} requires ${role.min}..${role.max} nodes, found $count")
    }
    if (Set("causal-chain", "dependency-map", "mapping").contains(pattern.id) && (logical.nodes.size < 2 || logical.nodes.size > 8))
      _fail("EXPLANATION_REFERENCE_INVALID", s"$path.nodes", s"${pattern.id} requires total node cardinality 2..8")
    val rules = pattern.relationRules.map(rule => rule.relation -> rule).toMap
    logical.relations.foreach { relation =>
      val rule = rules.getOrElse(relation.relationType,
        _fail("EXPLANATION_REFERENCE_INVALID", s"$path.relations.${relation.id}.type", s"Relation is not admitted by ${pattern.id}: ${relation.relationType}")
      )
      if (!rule.fromRoles.contains(nodes(relation.from).role) || !rule.toRoles.contains(nodes(relation.to).role))
        _fail("EXPLANATION_REFERENCE_INVALID", s"$path.relations.${relation.id}", "Relation endpoints do not satisfy the Phase-36 direction rule")
    }
    pattern.relationRules.foreach { rule =>
      val relations = logical.relations.filter(_.relationType == rule.relation)
      if (relations.size < rule.min || relations.size > rule.max)
        _fail("EXPLANATION_REFERENCE_INVALID", s"$path.relations", s"Relation ${rule.relation} requires ${rule.min}..${rule.max}, found ${relations.size}")
      val duplicates = relations.groupBy(relation => relation.from -> relation.to).collect { case (endpoint, values) if values.size > 1 => endpoint }
      if (duplicates.nonEmpty)
        _fail("EXPLANATION_REFERENCE_INVALID", s"$path.relations", s"duplicate Relation endpoints: ${duplicates.head._1}->${duplicates.head._2}")
      rule.topology match {
        case "linear" => _validate_linear(logical, path)
        case "acyclic" => _validate_acyclic(logical, path)
        case "bipartite" => ()
        case topology => _fail("EXPLANATION_REFERENCE_INVALID", path, s"unsupported Phase-36 topology: $topology")
      }
    }
  }

  private def _validate_linear(logical: CozyVisualPage.Logical, path: String): Unit = {
    val outgoing = logical.relations.groupBy(_.from).map { case (id, values) => id -> values.size }.withDefaultValue(0)
    val incoming = logical.relations.groupBy(_.to).map { case (id, values) => id -> values.size }.withDefaultValue(0)
    if (logical.relations.size != logical.nodes.size - 1 || logical.nodes.exists(node => incoming(node.id) > 1 || outgoing(node.id) > 1))
      _fail("EXPLANATION_REFERENCE_INVALID", path, "linear Logical Pattern requires one directed chain")
    val starts = logical.nodes.filter(node => incoming(node.id) == 0)
    val ends = logical.nodes.filter(node => outgoing(node.id) == 0)
    if (starts.size != 1 || ends.size != 1) _fail("EXPLANATION_REFERENCE_INVALID", path, "linear Logical Pattern requires one start and one end")
    val next = logical.relations.map(relation => relation.from -> relation.to).toMap
    var seen = Set.empty[String]
    var current = starts.head.id
    while (current.nonEmpty && !seen.contains(current)) {
      seen += current
      current = next.getOrElse(current, "")
    }
    if (seen.size != logical.nodes.size || current.nonEmpty)
      _fail("EXPLANATION_REFERENCE_INVALID", path, "linear Logical Pattern must be connected and acyclic")
  }

  private def _validate_acyclic(logical: CozyVisualPage.Logical, path: String): Unit = {
    val adjacency = logical.relations.groupBy(_.from).map { case (id, values) => id -> values.map(_.to) }.withDefaultValue(Vector.empty)
    var visiting = Set.empty[String]
    var visited = Set.empty[String]
    def _visit_(id: String): Unit = {
      if (visiting.contains(id)) _fail("EXPLANATION_REFERENCE_INVALID", s"$path.relations", "Logical Relation graph contains a cycle")
      if (!visited.contains(id)) {
        visiting += id
        adjacency(id).foreach(_visit_)
        visiting -= id
        visited += id
      }
    }
    logical.nodes.foreach(node => _visit_(node.id))
  }

  private def _parse_presentation_catalog(value: JsonValue, path: String): CozyVisualPage.Catalog = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("schema", "version", "id", "revision", "relations", "logicalPatterns", "visualPatterns"), path)
    _schema_version(fields, _presentation_catalog_schema, path)
    val catalog = CozyVisualPage.Catalog(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _positive_int(_field(fields, "revision", path), s"$path.revision"),
      _array(_field(fields, "relations", path), s"$path.relations").zipWithIndex.map { case (item, index) => _parse_p36_relation_definition(item, s"$path.relations[$index]") },
      _array(_field(fields, "logicalPatterns", path), s"$path.logicalPatterns").zipWithIndex.map { case (item, index) => _parse_p36_logical_pattern(item, s"$path.logicalPatterns[$index]") },
      _array(_field(fields, "visualPatterns", path), s"$path.visualPatterns").zipWithIndex.map { case (item, index) => _parse_p36_visual_pattern(item, s"$path.visualPatterns[$index]") }
    )
    _unique(catalog.relations.map(_.id), s"$path.relations", "relation id")
    _unique(catalog.logicalPatterns.map(_.id), s"$path.logicalPatterns", "Logical Pattern id")
    _unique(catalog.visualPatterns.map(_.id), s"$path.visualPatterns", "Visual Pattern id")
    catalog
  }

  private def _parse_p36_relation_definition(value: JsonValue, path: String): CozyVisualPage.RelationDefinition = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "direction"), path)
    CozyVisualPage.RelationDefinition(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _token(_string(_field(fields, "direction", path), s"$path.direction"), s"$path.direction")
    )
  }

  private def _parse_p36_logical_pattern(value: JsonValue, path: String): CozyVisualPage.LogicalPattern = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "nodeRoles", "relationRules"), path)
    CozyVisualPage.LogicalPattern(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _array(_field(fields, "nodeRoles", path), s"$path.nodeRoles").zipWithIndex.map { case (item, index) => _parse_p36_node_role(item, s"$path.nodeRoles[$index]") },
      _array(_field(fields, "relationRules", path), s"$path.relationRules").zipWithIndex.map { case (item, index) => _parse_p36_relation_rule(item, s"$path.relationRules[$index]") }
    )
  }

  private def _parse_p36_node_role(value: JsonValue, path: String): CozyVisualPage.NodeRole = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("role", "min", "max"), path)
    CozyVisualPage.NodeRole(
      _token(_string(_field(fields, "role", path), s"$path.role"), s"$path.role"),
      _nonnegative_int(_field(fields, "min", path), s"$path.min"),
      _nonnegative_int(_field(fields, "max", path), s"$path.max")
    )
  }

  private def _parse_p36_relation_rule(value: JsonValue, path: String): CozyVisualPage.RelationRule = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("relation", "fromRoles", "toRoles", "min", "max", "topology"), path)
    CozyVisualPage.RelationRule(
      _token(_string(_field(fields, "relation", path), s"$path.relation"), s"$path.relation"),
      _references(_field(fields, "fromRoles", path), s"$path.fromRoles"),
      _references(_field(fields, "toRoles", path), s"$path.toRoles"),
      _nonnegative_int(_field(fields, "min", path), s"$path.min"),
      _nonnegative_int(_field(fields, "max", path), s"$path.max"),
      _token(_string(_field(fields, "topology", path), s"$path.topology"), s"$path.topology")
    )
  }

  private def _parse_p36_visual_pattern(value: JsonValue, path: String): CozyVisualPage.VisualPattern = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "compatibleLogicalPatterns", "parameters"), path)
    CozyVisualPage.VisualPattern(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _references(_field(fields, "compatibleLogicalPatterns", path), s"$path.compatibleLogicalPatterns"),
      _array(_field(fields, "parameters", path), s"$path.parameters").zipWithIndex.map { case (item, index) => _parse_p36_parameter_definition(item, s"$path.parameters[$index]") }
    )
  }

  private def _parse_p36_parameter_definition(value: JsonValue, path: String): CozyVisualPage.ParameterDefinition = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("name", "type", "required"), path)
    CozyVisualPage.ParameterDefinition(
      _token(_string(_field(fields, "name", path), s"$path.name"), s"$path.name"),
      _token(_string(_field(fields, "type", path), s"$path.type"), s"$path.type"),
      _boolean(_field(fields, "required", path), s"$path.required")
    )
  }

  private def _parse_plan(value: JsonValue, path: String): Plan = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("schema", "version", "compositionIdentity", "explanationCatalog", "presentationCatalog", "subjectPattern", "explanationPattern", "steps", "identity"), path)
    _schema_version(fields, _plan_schema, path)
    val plan = Plan(
      _identity_text(_string(_field(fields, "compositionIdentity", path), s"$path.compositionIdentity"), s"$path.compositionIdentity"),
      _parse_catalog_selector(_field(fields, "explanationCatalog", path), s"$path.explanationCatalog"),
      _parse_catalog_selector(_field(fields, "presentationCatalog", path), s"$path.presentationCatalog"),
      _parse_pattern_reference(_field(fields, "subjectPattern", path), s"$path.subjectPattern"),
      _parse_pattern_reference(_field(fields, "explanationPattern", path), s"$path.explanationPattern"),
      _array(_field(fields, "steps", path), s"$path.steps").zipWithIndex.map { case (item, index) => _parse_plan_step(item, s"$path.steps[$index]") },
      _identity_text(_string(_field(fields, "identity", path), s"$path.identity"), s"$path.identity")
    )
    if (plan.steps.isEmpty) _fail("EXPLANATION_EXPANSION_INCOMPLETE", s"$path.steps", "Plan steps must be nonempty")
    _unique(plan.steps.map(_.id), s"$path.steps", "Plan step id")
    if (plan.steps.map(_.order) != (1 to plan.steps.size).toVector)
      _fail("EXPLANATION_EXPANSION_INCOMPLETE", s"$path.steps", "Plan step order must be contiguous and ordered")
    plan
  }

  private def _parse_plan_step(value: JsonValue, path: String): PlanStep = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "order", "semanticRole", "claims", "logical", "sourceRefs", "assetRefs", "patternProvenance", "parameterProvenance", "identity"), path)
    PlanStep(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _positive_int(_field(fields, "order", path), s"$path.order"),
      _token(_string(_field(fields, "semanticRole", path), s"$path.semanticRole"), s"$path.semanticRole"),
      _array(_field(fields, "claims", path), s"$path.claims").zipWithIndex.map { case (item, index) => _parse_claim(item, s"$path.claims[$index]") },
      _parse_logical(_field(fields, "logical", path), s"$path.logical"),
      _references(_field(fields, "sourceRefs", path), s"$path.sourceRefs"),
      _references(_field(fields, "assetRefs", path), s"$path.assetRefs"),
      _parse_pattern_provenance(_field(fields, "patternProvenance", path), s"$path.patternProvenance"),
      _parse_parameter_provenance(_field(fields, "parameterProvenance", path), s"$path.parameterProvenance"),
      _identity_text(_string(_field(fields, "identity", path), s"$path.identity"), s"$path.identity")
    )
  }

  private def _parse_pattern_provenance(value: JsonValue, path: String): PatternProvenance = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("subjectPattern", "explanationPattern"), path)
    PatternProvenance(
      _parse_pattern_reference(_field(fields, "subjectPattern", path), s"$path.subjectPattern"),
      _parse_pattern_reference(_field(fields, "explanationPattern", path), s"$path.explanationPattern")
    )
  }

  private def _parse_parameter_provenance(value: JsonValue, path: String): ParameterProvenance = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("values", "identity"), path)
    val values = _array(_field(fields, "values", path), s"$path.values").zipWithIndex.map { case (item, index) => _parse_parameter(item, s"$path.values[$index]") }
    _unique(values.map(_.name), s"$path.values", "parameter provenance name")
    ParameterProvenance(values, _identity_text(_string(_field(fields, "identity", path), s"$path.identity"), s"$path.identity"))
  }

  private def _catalog_selector(catalog: ValidatedCatalog): CatalogSelector =
    CatalogSelector(catalog.catalog.id, catalog.catalog.revision, catalog.identity)

  private def _presentation_selector(catalog: PresentationCatalog): CatalogSelector =
    CatalogSelector(catalog.catalog.id, catalog.catalog.revision, catalog.logicalCatalogIdentity)

  private def _catalog_value(catalog: Catalog): JsonValue = JsonObject(Vector(
    "schema" -> JsonString(_catalog_schema),
    "version" -> JsonNumber(1),
    "id" -> JsonString(catalog.id),
    "revision" -> JsonNumber(catalog.revision),
    "subjectPatterns" -> JsonArray(catalog.subjectPatterns.map(_subject_pattern_value)),
    "explanationPatterns" -> JsonArray(catalog.explanationPatterns.map(_explanation_pattern_value)),
    "reservedNarrativeArgumentIds" -> JsonArray(catalog.reservedNarrativeArgumentIds.map(JsonString))
  ))

  private def _subject_pattern_value(pattern: SubjectPattern): JsonValue = JsonObject(Vector(
    "id" -> JsonString(pattern.id),
    "version" -> JsonNumber(pattern.version),
    "factDefinitions" -> JsonArray(pattern.factDefinitions.map(_definition_value))
  ))

  private def _explanation_pattern_value(pattern: ExplanationPattern): JsonValue = JsonObject(Vector(
    "id" -> JsonString(pattern.id),
    "version" -> JsonNumber(pattern.version),
    "compatibleSubjects" -> JsonArray(pattern.compatibleSubjects.map(_pattern_reference_value)),
    "parameterDefinitions" -> JsonArray(pattern.parameterDefinitions.map(_definition_value)),
    "roles" -> JsonArray(pattern.roles.map(_role_value))
  ))

  private def _definition_value(value: Definition): JsonValue = JsonObject(Vector(
    "name" -> JsonString(value.name), "type" -> JsonString(value.valueType), "required" -> JsonBoolean(value.required)
  ))

  private def _role_value(value: Role): JsonValue = JsonObject(Vector(
    "id" -> JsonString(value.id), "order" -> JsonNumber(value.order), "required" -> JsonBoolean(value.required)
  ))

  private def _composition_value(composition: Composition): JsonValue = JsonObject(Vector(
    "schema" -> JsonString(_composition_schema),
    "version" -> JsonNumber(1),
    "id" -> JsonString(composition.id),
    "explanationCatalog" -> _catalog_selector_value(composition.explanationCatalog),
    "subject" -> JsonObject(Vector(
      "pattern" -> _pattern_reference_value(composition.subject.pattern),
      "facts" -> JsonArray(composition.subject.facts.map(_fact_value))
    )),
    "explanation" -> JsonObject(Vector(
      "pattern" -> _pattern_reference_value(composition.explanation.pattern),
      "parameters" -> JsonArray(composition.explanation.parameters.map(_parameter_value)),
      "steps" -> JsonArray(composition.explanation.steps.map(_composition_step_value))
    )),
    "sources" -> JsonArray(composition.sources.map(_source_declaration_value)),
    "assets" -> JsonArray(composition.assets.map(_asset_declaration_value))
  ))

  private def _fact_value(fact: Fact): JsonValue = JsonObject(Vector(
    "name" -> JsonString(fact.name), "value" -> _normalize_json_value(fact.value),
    "sourceRefs" -> _references_value(fact.sourceRefs), "assetRefs" -> _references_value(fact.assetRefs)
  ))

  private def _parameter_value(parameter: Parameter): JsonValue = JsonObject(Vector(
    "name" -> JsonString(parameter.name), "value" -> _normalize_json_value(parameter.value)
  ))

  private def _normalize_json_value(value: JsonValue): JsonValue = value match {
    case JsonObject(fields) => _dynamic_field_order(fields) match {
      case Some(order) =>
        val members = fields.toMap
        JsonObject(order.map(name => name -> _normalize_json_value(members(name))))
      case None => JsonObject(fields)
    }
    case JsonArray(values) => JsonArray(values.map(_normalize_json_value))
    case scalar => scalar
  }

  private def _dynamic_field_order(fields: Vector[(String, JsonValue)]): Option[Vector[String]] = {
    val fieldnames = fields.map(_._1)
    Vector(
      Vector("id", "label", "sourceRefs", "assetRefs"),
      Vector("id", "label", "steps"),
      Vector("id", "goalId", "useCaseId", "mechanismId", "sourceRefs", "assetRefs")
    ).find(order => fieldnames.size == order.size && fieldnames.toSet == order.toSet)
  }

  private def _composition_step_value(step: CompositionStep): JsonValue = JsonObject(Vector(
    "id" -> JsonString(step.id),
    "order" -> JsonNumber(step.order),
    "semanticRole" -> JsonString(step.semanticRole),
    "claims" -> JsonArray(step.claims.map(_claim_value)),
    "logical" -> _logical_value(step.logical),
    "sourceRefs" -> _references_value(step.sourceRefs),
    "assetRefs" -> _references_value(step.assetRefs),
    "parameterSelection" -> JsonArray(step.parameterSelection.map(selection => JsonObject(Vector("name" -> JsonString(selection.name)))))
  ))

  private def _claim_value(claim: Claim): JsonValue = JsonObject(Vector(
    "id" -> JsonString(claim.id), "text" -> JsonString(claim.text), "emphasis" -> JsonString(claim.emphasis),
    "sourceRefs" -> _references_value(claim.sourceRefs), "assetRefs" -> _references_value(claim.assetRefs)
  ))

  private def _logical_value(logical: CozyVisualPage.Logical): JsonValue = JsonObject(Vector(
    "pattern" -> JsonString(logical.pattern),
    "nodes" -> JsonArray(logical.nodes.map(node => JsonObject(Vector(
      "id" -> JsonString(node.id), "role" -> JsonString(node.role), "label" -> JsonString(node.label), "sourceRefs" -> _references_value(node.sourceRefs)
    )))),
    "relations" -> JsonArray(logical.relations.map(relation => JsonObject(Vector(
      "id" -> JsonString(relation.id), "type" -> JsonString(relation.relationType), "from" -> JsonString(relation.from), "to" -> JsonString(relation.to), "sourceRefs" -> _references_value(relation.sourceRefs)
    ))))
  ))

  private def _source_declaration_value(source: SourceDeclaration): JsonValue = JsonObject(Vector(
    "id" -> JsonString(source.id), "sha256" -> JsonString(source.sha256)
  ))

  private def _asset_declaration_value(asset: AssetDeclaration): JsonValue = JsonObject(Vector(
    "id" -> JsonString(asset.id), "mediaType" -> JsonString(asset.mediaType), "sha256" -> JsonString(asset.sha256)
  ))

  private def _plan_value(plan: Plan, includeidentity: Boolean): JsonValue = {
    val fields = Vector[(String, JsonValue)](
      "schema" -> JsonString(_plan_schema),
      "version" -> JsonNumber(1),
      "compositionIdentity" -> JsonString(plan.compositionIdentity),
      "explanationCatalog" -> _catalog_selector_value(plan.explanationCatalog),
      "presentationCatalog" -> _catalog_selector_value(plan.presentationCatalog),
      "subjectPattern" -> _pattern_reference_value(plan.subjectPattern),
      "explanationPattern" -> _pattern_reference_value(plan.explanationPattern),
      "steps" -> JsonArray(plan.steps.map(_plan_step_value))
    ) ++ (if (includeidentity) Vector("identity" -> JsonString(plan.identity)) else Vector.empty)
    JsonObject(fields)
  }

  private def _plan_step_value(step: PlanStep): JsonValue = JsonObject(Vector(
    "id" -> JsonString(step.id),
    "order" -> JsonNumber(step.order),
    "semanticRole" -> JsonString(step.semanticRole),
    "claims" -> JsonArray(step.claims.map(_claim_value)),
    "logical" -> _logical_value(step.logical),
    "sourceRefs" -> _references_value(step.sourceRefs),
    "assetRefs" -> _references_value(step.assetRefs),
    "patternProvenance" -> JsonObject(Vector(
      "subjectPattern" -> _pattern_reference_value(step.patternProvenance.subjectPattern),
      "explanationPattern" -> _pattern_reference_value(step.patternProvenance.explanationPattern)
    )),
    "parameterProvenance" -> JsonObject(Vector(
      "values" -> JsonArray(step.parameterProvenance.values.map(_parameter_value)),
      "identity" -> JsonString(step.parameterProvenance.identity)
    )),
    "identity" -> JsonString(step.identity)
  ))

  private def _catalog_selector_value(selector: CatalogSelector): JsonValue = JsonObject(Vector(
    "id" -> JsonString(selector.id), "revision" -> JsonNumber(selector.revision), "identity" -> JsonString(selector.identity)
  ))

  private def _pattern_reference_value(reference: PatternReference): JsonValue = JsonObject(Vector(
    "id" -> JsonString(reference.id), "version" -> JsonNumber(reference.version)
  ))

  private def _references_value(values: Vector[String]): JsonValue = JsonArray(values.map(JsonString))

  private def _parameter_provenance_identity(values: Vector[Parameter]): String =
    _identity(JsonObject(Vector("values" -> JsonArray(values.map(_parameter_value)))))

  private def _plan_step_identity(step: PlanStep): String = {
    val fields = _plan_step_value(step).asInstanceOf[JsonObject].fields.dropRight(1)
    _identity(JsonObject(fields))
  }

  private def _parse_json(text: String, source: String): JsonValue = {
    if (text == null || text.isEmpty) _fail("EXPLANATION_SCHEMA_INVALID", source, "JSON input is required")
    val parser = new JsonFactory().createParser(text)
    try {
      if (parser.nextToken() == null) _fail("EXPLANATION_SCHEMA_INVALID", source, "JSON input is required")
      val value = _parse_json_value(parser, source)
      if (parser.nextToken() != null) _fail("EXPLANATION_SCHEMA_INVALID", source, "trailing JSON tokens are not admitted")
      value
    } catch {
      case fault: ExplanationFault => throw fault
      case NonFatal(e) => _fail("EXPLANATION_SCHEMA_INVALID", source, Option(e.getMessage).getOrElse("invalid JSON"))
    } finally parser.close()
  }

  private def _parse_json_value(parser: com.fasterxml.jackson.core.JsonParser, source: String): JsonValue = parser.getCurrentToken match {
    case JsonToken.START_OBJECT =>
      val fields = Vector.newBuilder[(String, JsonValue)]
      val seen = mutable.Set.empty[String]
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        if (parser.getCurrentToken != JsonToken.FIELD_NAME) _fail("EXPLANATION_SCHEMA_INVALID", source, "JSON object member name is required")
        val name = parser.getCurrentName
        if (seen.contains(name)) _fail("EXPLANATION_DUPLICATE_FIELD", source, s"duplicate JSON field: $name")
        seen += name
        if (parser.nextToken() == null) _fail("EXPLANATION_SCHEMA_INVALID", source, s"missing JSON value for field: $name")
        fields += name -> _parse_json_value(parser, source)
      }
      JsonObject(fields.result())
    case JsonToken.START_ARRAY =>
      val values = Vector.newBuilder[JsonValue]
      while (parser.nextToken() != JsonToken.END_ARRAY) values += _parse_json_value(parser, source)
      JsonArray(values.result())
    case JsonToken.VALUE_STRING => JsonString(parser.getText)
    case JsonToken.VALUE_TRUE => JsonBoolean(true)
    case JsonToken.VALUE_FALSE => JsonBoolean(false)
    case JsonToken.VALUE_NUMBER_INT =>
      try JsonNumber(parser.getLongValue) catch { case NonFatal(_) => _fail("EXPLANATION_SCHEMA_INVALID", source, "integer is outside supported range") }
    case JsonToken.VALUE_NUMBER_FLOAT => _fail("EXPLANATION_SCHEMA_INVALID", source, "non-finite or floating-point JSON numbers are not admitted")
    case JsonToken.VALUE_NULL => _fail("EXPLANATION_SCHEMA_INVALID", source, "JSON null is not admitted")
    case _ => _fail("EXPLANATION_SCHEMA_INVALID", source, s"unsupported JSON token: ${parser.getCurrentToken}")
  }

  private def _canonical(value: JsonValue): String = value match {
    case JsonObject(fields) => fields.map { case (name, member) => _quote(name) + ":" + _canonical(member) }.mkString("{", ",", "}")
    case JsonArray(values) => values.map(_canonical).mkString("[", ",", "]")
    case JsonString(text) => _quote(text)
    case JsonNumber(number) => number.toString
    case JsonBoolean(value) => value.toString
  }

  private def _quote(value: String): String = {
    val escaped = Option(value).getOrElse("").flatMap {
      case '"' => "\\\""
      case '\\' => "\\\\"
      case '\b' => "\\b"
      case '\f' => "\\f"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case character if character < ' ' => "\\u%04x".format(character.toInt)
      case character => character.toString
    }
    "\"" + escaped + "\""
  }

  private def _identity(value: JsonValue): String = "sha256:" + _sha256_bytes(_canonical(value).getBytes(StandardCharsets.UTF_8))

  private def _sha256_file(path: Path): String = _sha256_bytes(Files.readAllBytes(path))

  private def _sha256_bytes(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(value => f"${value & 0xff}%02x").mkString

  private def _object(value: JsonValue, path: String): Vector[(String, JsonValue)] = value match {
    case JsonObject(fields) => fields
    case _ => _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a JSON object")
  }

  private def _array(value: JsonValue, path: String): Vector[JsonValue] = value match {
    case JsonArray(values) => values
    case _ => _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a JSON array")
  }

  private def _nonempty_array(value: JsonValue, path: String): Vector[JsonValue] = {
    val values = _array(value, path)
    if (values.isEmpty) _fail("EXPLANATION_FACT_INVALID", path, "must be a nonempty ordered array")
    values
  }

  private def _string(value: JsonValue, path: String): String = value match {
    case JsonString(text) => text
    case _ => _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a JSON string")
  }

  private def _boolean(value: JsonValue, path: String): Boolean = value match {
    case JsonBoolean(result) => result
    case _ => _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a JSON boolean")
  }

  private def _positive_int(value: JsonValue, path: String): Int = value match {
    case JsonNumber(number) if number > 0 && number <= Int.MaxValue => number.toInt
    case JsonNumber(_) => _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a positive 32-bit integer")
    case _ => _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a JSON integer")
  }

  private def _nonnegative_int(value: JsonValue, path: String): Int = value match {
    case JsonNumber(number) if number >= 0 && number <= Int.MaxValue => number.toInt
    case JsonNumber(_) => _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a nonnegative 32-bit integer")
    case _ => _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a JSON integer")
  }

  private def _field(fields: Vector[(String, JsonValue)], name: String, path: String): JsonValue =
    fields.find(_._1 == name).map(_._2).getOrElse(_fail("EXPLANATION_SCHEMA_INVALID", s"$path.$name", "required field is missing"))

  private def _exact_fields(fields: Vector[(String, JsonValue)], expected: Vector[String], path: String): Unit = {
    val actual = fields.map(_._1)
    actual.find(name => !expected.contains(name)).foreach(name => _fail("EXPLANATION_UNKNOWN_FIELD", s"$path.$name", "unknown field is not admitted"))
    expected.find(name => !actual.contains(name)).foreach(name => _fail("EXPLANATION_SCHEMA_INVALID", s"$path.$name", "required field is missing"))
  }

  private def _schema_version(fields: Vector[(String, JsonValue)], schema: String, path: String): Unit = {
    if (_string(_field(fields, "schema", path), s"$path.schema") != schema)
      _fail("EXPLANATION_SCHEMA_INVALID", s"$path.schema", s"must be exactly $schema")
    _field(fields, "version", path) match {
      case JsonNumber(1) => ()
      case _ => _fail("EXPLANATION_SCHEMA_INVALID", s"$path.version", "must be integer 1")
    }
  }

  private def _token(value: String, path: String): String = {
    if (value == null || !_token_pattern.pattern.matcher(value).matches)
      _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a nonempty stable token")
    value
  }

  private def _text(value: String, path: String): String = {
    if (value == null || value.isEmpty || value != value.trim || value.exists(_.isControl))
      _fail("EXPLANATION_SCHEMA_INVALID", path, "must be nonempty trimmed text without control characters")
    value
  }

  private def _sha256_text(value: String, path: String): String = {
    if (value == null || !_sha256_pattern.pattern.matcher(value).matches)
      _fail("EXPLANATION_SCHEMA_INVALID", path, "must be 64 lowercase hexadecimal SHA-256 characters")
    value
  }

  private def _identity_text(value: String, path: String): String = {
    if (value == null || !_identity_pattern.pattern.matcher(value).matches)
      _fail("EXPLANATION_SCHEMA_INVALID", path, "must be sha256:<64-lowercase-hex>")
    value
  }

  private def _references(value: JsonValue, path: String): Vector[String] = {
    val references = _array(value, path).zipWithIndex.map { case (item, index) => _token(_string(item, s"$path[$index]"), s"$path[$index]") }
    _unique(references, path, "reference")
    references
  }

  private def _unique(values: Vector[String], path: String, label: String): Unit =
    values.groupBy(identity).collectFirst { case (value, duplicates) if duplicates.size > 1 => value }.foreach { value =>
      _fail("EXPLANATION_SCHEMA_INVALID", path, s"duplicate $label: $value")
    }

  private def _direct_input(path: Path, label: String): Path = {
    if (path == null) _fail("EXPLANATION_REFERENCE_INVALID", label, "direct file path is required")
    val normalized = try path.toAbsolutePath.normalize() catch { case NonFatal(_) => _fail("EXPLANATION_REFERENCE_INVALID", label, "direct file path is invalid") }
    if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized))
      _fail("EXPLANATION_REFERENCE_INVALID", label, "must be an existing direct regular non-symlink file")
    normalized
  }

  private def _read_utf8(path: Path, label: String): String = try {
    val decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
    decoder.decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString
  } catch {
    case fault: ExplanationFault => throw fault
    case NonFatal(e) => _fail("EXPLANATION_SCHEMA_INVALID", label, Option(e.getMessage).getOrElse("cannot read UTF-8 file"))
  }

  private def _atomic_write(path: Path, text: String): Unit = {
    val output = try path.toAbsolutePath.normalize() catch { case NonFatal(_) => _fail("EXPLANATION_COMMAND_INVALID", "$command.save", "output path is invalid") }
    val parent = Option(output.getParent).getOrElse(_fail("EXPLANATION_COMMAND_INVALID", "$command.save", "output parent is required"))
    if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(parent))
      _fail("EXPLANATION_COMMAND_INVALID", "$command.save", "output parent must be an existing direct directory")
    if (Files.exists(output, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(output))
      _fail("EXPLANATION_COMMAND_INVALID", "$command.save", "output must not be a symbolic link")
    val temporary = Files.createTempFile(parent, ".cozy-explanation-", ".tmp")
    try {
      Files.write(temporary, text.getBytes(StandardCharsets.UTF_8))
      try Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      catch {
        case _: AtomicMoveNotSupportedException => _fail("EXPLANATION_COMMAND_INVALID", "$command.save", "filesystem does not support same-directory atomic output replacement")
      }
    } catch {
      case fault: ExplanationFault => throw fault
      case NonFatal(e) => _fail("EXPLANATION_COMMAND_INVALID", "$command.save", Option(e.getMessage).getOrElse("cannot write output"))
    } finally {
      try Files.deleteIfExists(temporary) catch { case NonFatal(_) => () }
    }
  }

  private def _cli_path(value: String, path: String): Path = {
    if (value == null || value.isEmpty || value != value.trim) _fail("EXPLANATION_COMMAND_INVALID", path, "path must be nonempty and trimmed")
    try Paths.get(value) catch { case NonFatal(_) => _fail("EXPLANATION_COMMAND_INVALID", path, "path is invalid") }
  }

  private def _fail(code: String, path: String, reason: String): Nothing = throw ExplanationFault(code, path, reason)
}
