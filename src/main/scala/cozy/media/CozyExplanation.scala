package cozy.media

import java.nio.file.Path

import CozyExplanationCodec._
import CozyExplanationJson._

/*
 * @since   Aug. 28, 2026
 *  version Aug. 28, 2026
 * @version Sep.  2, 2026
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

  private[media] val _catalog_schema = "cozy.explanation.catalog.v1"
  private[media] val _composition_schema = "cozy.explanation-composition.v1"
  private[media] val _plan_schema = "cozy.explanation-plan.v1"
  private[media] val _presentation_catalog_schema = "cozy.presentation-semantics.catalog.v1"
  private[media] val _token_pattern = "[A-Za-z0-9][A-Za-z0-9._-]*".r
  private[media] val _sha256_pattern = "[0-9a-f]{64}".r
  private[media] val _identity_pattern = "sha256:[0-9a-f]{64}".r
  private[media] val _value_types = Set("text", "goal-list", "use-case-list", "scenario", "mechanism-list", "mechanism-link-list")
  private[media] val _reserved_ids = Vector(
    "problem-solution", "problem-cause-solution", "current-target", "before-after", "challenge-approach-result",
    "observation-insight-implication", "fact-interpretation-action", "why-what-how", "input-process-output",
    "concept-example", "claim-evidence", "claim-reasons", "question-answer", "principle-mechanism-effect",
    "strategy-execution-outcome", "past-present-future"
  )
  private[media] val _software_product_facts = Vector(
    Definition("context", "text", required = true),
    Definition("goals", "goal-list", required = true),
    Definition("mainScenario", "scenario", required = true),
    Definition("mechanisms", "mechanism-list", required = true),
    Definition("name", "text", required = true),
    Definition("useCases", "use-case-list", required = true),
    Definition("vision", "text", required = true)
  )
  private[media] val _product_overview_roles = Vector(
    Role("vision", 1, required = true), Role("goal", 2, required = true), Role("context", 3, required = true),
    Role("use-case", 4, required = true), Role("main-scenario", 5, required = true)
  )
  private[media] val _product_mechanism_roles = Vector(Role("mechanism", 1, required = true))
  private[media] val _problem_solution_roles = Vector(Role("problem", 1, required = true), Role("solution", 2, required = true))

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
    case "preview" :: rest => CozyExplanationPreview.execute(rest)
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

}
