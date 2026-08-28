package cozy.media

import CozyExplanation._
import CozyExplanationJson._

/*
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[media] object CozyExplanationCodec {
  private[media] def _parse_catalog(value: JsonValue, path: String): Catalog = {
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

  private[media] def _parse_subject_pattern(value: JsonValue, path: String): SubjectPattern = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "version", "factDefinitions"), path)
    SubjectPattern(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _positive_int(_field(fields, "version", path), s"$path.version"),
      _array(_field(fields, "factDefinitions", path), s"$path.factDefinitions").zipWithIndex.map { case (item, index) => _parse_definition(item, s"$path.factDefinitions[$index]") }
    )
  }

  private[media] def _parse_explanation_pattern(value: JsonValue, path: String): ExplanationPattern = {
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

  private[media] def _parse_definition(value: JsonValue, path: String): Definition = {
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

  private[media] def _parse_role(value: JsonValue, path: String): Role = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "order", "required"), path)
    Role(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _positive_int(_field(fields, "order", path), s"$path.order"),
      _boolean(_field(fields, "required", path), s"$path.required")
    )
  }

  private[media] def _parse_pattern_reference(value: JsonValue, path: String): PatternReference = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "version"), path)
    PatternReference(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _positive_int(_field(fields, "version", path), s"$path.version")
    )
  }

  private[media] def _normalize_definitions(values: Vector[Definition], path: String): Vector[Definition] = {
    if (values.isEmpty) Vector.empty
    else {
      _unique(values.map(_.name), path, "definition name")
      values.sortBy(_.name)
    }
  }

  private[media] def _normalize_pattern_references(values: Vector[PatternReference], path: String): Vector[PatternReference] = {
    if (values.isEmpty) _fail("EXPLANATION_PATTERN_INCOMPATIBLE", path, "compatibleSubjects must be nonempty")
    _unique(values.map(value => s"${value.id}@${value.version}"), path, "compatible Subject Pattern")
    values.sortBy(value => value.id -> value.version)
  }

  private[media] def _normalize_roles(values: Vector[Role], path: String): Vector[Role] = {
    if (values.isEmpty) _fail("EXPLANATION_SCHEMA_INVALID", path, "roles must be nonempty")
    _unique(values.map(_.id), path, "role id")
    _unique(values.map(_.order.toString), path, "role order")
    val normalized = values.sortBy(_.order)
    if (normalized.map(_.order) != (1 to normalized.size).toVector)
      _fail("EXPLANATION_SCHEMA_INVALID", path, "role order must be contiguous from 1")
    normalized
  }

  private[media] def _validate_catalog_contract(catalog: Catalog, path: String): Unit = {
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

  private[media] def _parse_composition(value: JsonValue, path: String): Composition = {
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

  private[media] def _parse_catalog_selector(value: JsonValue, path: String): CatalogSelector = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "revision", "identity"), path)
    CatalogSelector(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _positive_int(_field(fields, "revision", path), s"$path.revision"),
      _identity_text(_string(_field(fields, "identity", path), s"$path.identity"), s"$path.identity")
    )
  }

  private[media] def _parse_subject(value: JsonValue, path: String): Subject = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("pattern", "facts"), path)
    Subject(
      _parse_pattern_reference(_field(fields, "pattern", path), s"$path.pattern"),
      _array(_field(fields, "facts", path), s"$path.facts").zipWithIndex.map { case (item, index) => _parse_fact(item, s"$path.facts[$index]") }
    )
  }

  private[media] def _parse_fact(value: JsonValue, path: String): Fact = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("name", "value", "sourceRefs", "assetRefs"), path)
    Fact(
      _token(_string(_field(fields, "name", path), s"$path.name"), s"$path.name"),
      _normalize_json_value(_field(fields, "value", path)),
      _references(_field(fields, "sourceRefs", path), s"$path.sourceRefs"),
      _references(_field(fields, "assetRefs", path), s"$path.assetRefs")
    )
  }

  private[media] def _parse_explanation(value: JsonValue, path: String): Explanation = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("pattern", "parameters", "steps"), path)
    Explanation(
      _parse_pattern_reference(_field(fields, "pattern", path), s"$path.pattern"),
      _array(_field(fields, "parameters", path), s"$path.parameters").zipWithIndex.map { case (item, index) => _parse_parameter(item, s"$path.parameters[$index]") },
      _array(_field(fields, "steps", path), s"$path.steps").zipWithIndex.map { case (item, index) => _parse_composition_step(item, s"$path.steps[$index]") }
    )
  }

  private[media] def _parse_parameter(value: JsonValue, path: String): Parameter = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("name", "value"), path)
    Parameter(_token(_string(_field(fields, "name", path), s"$path.name"), s"$path.name"), _normalize_json_value(_field(fields, "value", path)))
  }

  private[media] def _parse_composition_step(value: JsonValue, path: String): CompositionStep = {
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

  private[media] def _parse_claim(value: JsonValue, path: String): Claim = {
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

  private[media] def _parse_logical(value: JsonValue, path: String): CozyVisualPage.Logical = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("pattern", "nodes", "relations"), path)
    CozyVisualPage.Logical(
      _token(_string(_field(fields, "pattern", path), s"$path.pattern"), s"$path.pattern"),
      _array(_field(fields, "nodes", path), s"$path.nodes").zipWithIndex.map { case (item, index) => _parse_node(item, s"$path.nodes[$index]") },
      _array(_field(fields, "relations", path), s"$path.relations").zipWithIndex.map { case (item, index) => _parse_relation(item, s"$path.relations[$index]") }
    )
  }

  private[media] def _parse_node(value: JsonValue, path: String): CozyVisualPage.Node = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "role", "label", "sourceRefs"), path)
    CozyVisualPage.Node(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _token(_string(_field(fields, "role", path), s"$path.role"), s"$path.role"),
      _text(_string(_field(fields, "label", path), s"$path.label"), s"$path.label"),
      _references(_field(fields, "sourceRefs", path), s"$path.sourceRefs")
    )
  }

  private[media] def _parse_relation(value: JsonValue, path: String): CozyVisualPage.Relation = {
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

  private[media] def _parse_parameter_selection(value: JsonValue, path: String): ParameterSelection = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("name"), path)
    ParameterSelection(_token(_string(_field(fields, "name", path), s"$path.name"), s"$path.name"))
  }

  private[media] def _parse_source_declaration(value: JsonValue, path: String): SourceDeclaration = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "sha256"), path)
    SourceDeclaration(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _sha256_text(_string(_field(fields, "sha256", path), s"$path.sha256"), s"$path.sha256")
    )
  }

  private[media] def _parse_asset_declaration(value: JsonValue, path: String): AssetDeclaration = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "mediaType", "sha256"), path)
    AssetDeclaration(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _text(_string(_field(fields, "mediaType", path), s"$path.mediaType"), s"$path.mediaType"),
      _sha256_text(_string(_field(fields, "sha256", path), s"$path.sha256"), s"$path.sha256")
    )
  }

  private[media] def _validate_composition(
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

  private[media] def _validate_step(
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

  private[media] def _validate_fact_value(
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

  private[media] def _validate_labeled_list(value: JsonValue, sourceids: Set[String], assetids: Set[String], path: String): Unit = {
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

  private[media] def _validate_scenario(value: JsonValue, sourceids: Set[String], assetids: Set[String], path: String): Unit = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "label", "steps"), path)
    _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id")
    _text(_string(_field(fields, "label", path), s"$path.label"), s"$path.label")
    _validate_labeled_list(_field(fields, "steps", path), sourceids, assetids, s"$path.steps")
  }

  private[media] def _validate_mechanism_link_list(value: JsonValue, sourceids: Set[String], assetids: Set[String], path: String): Unit = {
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

  private[media] def _validate_mechanism_links(value: JsonValue, facts: Map[String, Fact], path: String): Unit = {
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

  private[media] def _labeled_ids(value: JsonValue, path: String): Set[String] =
    _array(value, path).map { item =>
      val fields = _object(item, path)
      _string(_field(fields, "id", path), s"$path.id")
    }.toSet

  private[media] def _validate_bindings(
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

  private[media] def _validate_references(references: Vector[String], declared: Set[String], path: String): Unit =
    references.foreach { reference =>
      if (!declared.contains(reference)) _fail("EXPLANATION_REFERENCE_INVALID", path, s"reference does not resolve exactly once: $reference")
    }

  private[media] def _validate_p36_logical(
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

  private[media] def _validate_linear(logical: CozyVisualPage.Logical, path: String): Unit = {
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

  private[media] def _validate_acyclic(logical: CozyVisualPage.Logical, path: String): Unit = {
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

  private[media] def _parse_presentation_catalog(value: JsonValue, path: String): CozyVisualPage.Catalog = {
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

  private[media] def _parse_p36_relation_definition(value: JsonValue, path: String): CozyVisualPage.RelationDefinition = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "direction"), path)
    CozyVisualPage.RelationDefinition(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _token(_string(_field(fields, "direction", path), s"$path.direction"), s"$path.direction")
    )
  }

  private[media] def _parse_p36_logical_pattern(value: JsonValue, path: String): CozyVisualPage.LogicalPattern = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "nodeRoles", "relationRules"), path)
    CozyVisualPage.LogicalPattern(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _array(_field(fields, "nodeRoles", path), s"$path.nodeRoles").zipWithIndex.map { case (item, index) => _parse_p36_node_role(item, s"$path.nodeRoles[$index]") },
      _array(_field(fields, "relationRules", path), s"$path.relationRules").zipWithIndex.map { case (item, index) => _parse_p36_relation_rule(item, s"$path.relationRules[$index]") }
    )
  }

  private[media] def _parse_p36_node_role(value: JsonValue, path: String): CozyVisualPage.NodeRole = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("role", "min", "max"), path)
    CozyVisualPage.NodeRole(
      _token(_string(_field(fields, "role", path), s"$path.role"), s"$path.role"),
      _nonnegative_int(_field(fields, "min", path), s"$path.min"),
      _nonnegative_int(_field(fields, "max", path), s"$path.max")
    )
  }

  private[media] def _parse_p36_relation_rule(value: JsonValue, path: String): CozyVisualPage.RelationRule = {
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

  private[media] def _parse_p36_visual_pattern(value: JsonValue, path: String): CozyVisualPage.VisualPattern = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "compatibleLogicalPatterns", "parameters"), path)
    CozyVisualPage.VisualPattern(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _references(_field(fields, "compatibleLogicalPatterns", path), s"$path.compatibleLogicalPatterns"),
      _array(_field(fields, "parameters", path), s"$path.parameters").zipWithIndex.map { case (item, index) => _parse_p36_parameter_definition(item, s"$path.parameters[$index]") }
    )
  }

  private[media] def _parse_p36_parameter_definition(value: JsonValue, path: String): CozyVisualPage.ParameterDefinition = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("name", "type", "required"), path)
    CozyVisualPage.ParameterDefinition(
      _token(_string(_field(fields, "name", path), s"$path.name"), s"$path.name"),
      _token(_string(_field(fields, "type", path), s"$path.type"), s"$path.type"),
      _boolean(_field(fields, "required", path), s"$path.required")
    )
  }

  private[media] def _parse_plan(value: JsonValue, path: String): Plan = {
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

  private[media] def _parse_plan_step(value: JsonValue, path: String): PlanStep = {
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

  private[media] def _parse_pattern_provenance(value: JsonValue, path: String): PatternProvenance = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("subjectPattern", "explanationPattern"), path)
    PatternProvenance(
      _parse_pattern_reference(_field(fields, "subjectPattern", path), s"$path.subjectPattern"),
      _parse_pattern_reference(_field(fields, "explanationPattern", path), s"$path.explanationPattern")
    )
  }

  private[media] def _parse_parameter_provenance(value: JsonValue, path: String): ParameterProvenance = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("values", "identity"), path)
    val values = _array(_field(fields, "values", path), s"$path.values").zipWithIndex.map { case (item, index) => _parse_parameter(item, s"$path.values[$index]") }
    _unique(values.map(_.name), s"$path.values", "parameter provenance name")
    ParameterProvenance(values, _identity_text(_string(_field(fields, "identity", path), s"$path.identity"), s"$path.identity"))
  }

  private[media] def _catalog_selector(catalog: ValidatedCatalog): CatalogSelector =
    CatalogSelector(catalog.catalog.id, catalog.catalog.revision, catalog.identity)

  private[media] def _presentation_selector(catalog: PresentationCatalog): CatalogSelector =
    CatalogSelector(catalog.catalog.id, catalog.catalog.revision, catalog.logicalCatalogIdentity)

  private[media] def _catalog_value(catalog: Catalog): JsonValue = JsonObject(Vector(
    "schema" -> JsonString(_catalog_schema),
    "version" -> JsonNumber(1),
    "id" -> JsonString(catalog.id),
    "revision" -> JsonNumber(catalog.revision),
    "subjectPatterns" -> JsonArray(catalog.subjectPatterns.map(_subject_pattern_value)),
    "explanationPatterns" -> JsonArray(catalog.explanationPatterns.map(_explanation_pattern_value)),
    "reservedNarrativeArgumentIds" -> JsonArray(catalog.reservedNarrativeArgumentIds.map(JsonString))
  ))

  private[media] def _subject_pattern_value(pattern: SubjectPattern): JsonValue = JsonObject(Vector(
    "id" -> JsonString(pattern.id),
    "version" -> JsonNumber(pattern.version),
    "factDefinitions" -> JsonArray(pattern.factDefinitions.map(_definition_value))
  ))

  private[media] def _explanation_pattern_value(pattern: ExplanationPattern): JsonValue = JsonObject(Vector(
    "id" -> JsonString(pattern.id),
    "version" -> JsonNumber(pattern.version),
    "compatibleSubjects" -> JsonArray(pattern.compatibleSubjects.map(_pattern_reference_value)),
    "parameterDefinitions" -> JsonArray(pattern.parameterDefinitions.map(_definition_value)),
    "roles" -> JsonArray(pattern.roles.map(_role_value))
  ))

  private[media] def _definition_value(value: Definition): JsonValue = JsonObject(Vector(
    "name" -> JsonString(value.name), "type" -> JsonString(value.valueType), "required" -> JsonBoolean(value.required)
  ))

  private[media] def _role_value(value: Role): JsonValue = JsonObject(Vector(
    "id" -> JsonString(value.id), "order" -> JsonNumber(value.order), "required" -> JsonBoolean(value.required)
  ))

  private[media] def _composition_value(composition: Composition): JsonValue = JsonObject(Vector(
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

  private[media] def _fact_value(fact: Fact): JsonValue = JsonObject(Vector(
    "name" -> JsonString(fact.name), "value" -> _normalize_json_value(fact.value),
    "sourceRefs" -> _references_value(fact.sourceRefs), "assetRefs" -> _references_value(fact.assetRefs)
  ))

  private[media] def _parameter_value(parameter: Parameter): JsonValue = JsonObject(Vector(
    "name" -> JsonString(parameter.name), "value" -> _normalize_json_value(parameter.value)
  ))

  private[media] def _normalize_json_value(value: JsonValue): JsonValue = value match {
    case JsonObject(fields) => _dynamic_field_order(fields) match {
      case Some(order) =>
        val members = fields.toMap
        JsonObject(order.map(name => name -> _normalize_json_value(members(name))))
      case None => JsonObject(fields)
    }
    case JsonArray(values) => JsonArray(values.map(_normalize_json_value))
    case scalar => scalar
  }

  private[media] def _dynamic_field_order(fields: Vector[(String, JsonValue)]): Option[Vector[String]] = {
    val fieldnames = fields.map(_._1)
    Vector(
      Vector("id", "label", "sourceRefs", "assetRefs"),
      Vector("id", "label", "steps"),
      Vector("id", "goalId", "useCaseId", "mechanismId", "sourceRefs", "assetRefs")
    ).find(order => fieldnames.size == order.size && fieldnames.toSet == order.toSet)
  }

  private[media] def _composition_step_value(step: CompositionStep): JsonValue = JsonObject(Vector(
    "id" -> JsonString(step.id),
    "order" -> JsonNumber(step.order),
    "semanticRole" -> JsonString(step.semanticRole),
    "claims" -> JsonArray(step.claims.map(_claim_value)),
    "logical" -> _logical_value(step.logical),
    "sourceRefs" -> _references_value(step.sourceRefs),
    "assetRefs" -> _references_value(step.assetRefs),
    "parameterSelection" -> JsonArray(step.parameterSelection.map(selection => JsonObject(Vector("name" -> JsonString(selection.name)))))
  ))

  private[media] def _claim_value(claim: Claim): JsonValue = JsonObject(Vector(
    "id" -> JsonString(claim.id), "text" -> JsonString(claim.text), "emphasis" -> JsonString(claim.emphasis),
    "sourceRefs" -> _references_value(claim.sourceRefs), "assetRefs" -> _references_value(claim.assetRefs)
  ))

  private[media] def _logical_value(logical: CozyVisualPage.Logical): JsonValue = JsonObject(Vector(
    "pattern" -> JsonString(logical.pattern),
    "nodes" -> JsonArray(logical.nodes.map(node => JsonObject(Vector(
      "id" -> JsonString(node.id), "role" -> JsonString(node.role), "label" -> JsonString(node.label), "sourceRefs" -> _references_value(node.sourceRefs)
    )))),
    "relations" -> JsonArray(logical.relations.map(relation => JsonObject(Vector(
      "id" -> JsonString(relation.id), "type" -> JsonString(relation.relationType), "from" -> JsonString(relation.from), "to" -> JsonString(relation.to), "sourceRefs" -> _references_value(relation.sourceRefs)
    ))))
  ))

  private[media] def _source_declaration_value(source: SourceDeclaration): JsonValue = JsonObject(Vector(
    "id" -> JsonString(source.id), "sha256" -> JsonString(source.sha256)
  ))

  private[media] def _asset_declaration_value(asset: AssetDeclaration): JsonValue = JsonObject(Vector(
    "id" -> JsonString(asset.id), "mediaType" -> JsonString(asset.mediaType), "sha256" -> JsonString(asset.sha256)
  ))

  private[media] def _plan_value(plan: Plan, includeidentity: Boolean): JsonValue = {
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

  private[media] def _plan_step_value(step: PlanStep): JsonValue = JsonObject(Vector(
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

  private[media] def _catalog_selector_value(selector: CatalogSelector): JsonValue = JsonObject(Vector(
    "id" -> JsonString(selector.id), "revision" -> JsonNumber(selector.revision), "identity" -> JsonString(selector.identity)
  ))

  private[media] def _pattern_reference_value(reference: PatternReference): JsonValue = JsonObject(Vector(
    "id" -> JsonString(reference.id), "version" -> JsonNumber(reference.version)
  ))

  private[media] def _references_value(values: Vector[String]): JsonValue = JsonArray(values.map(JsonString))

  private[media] def _parameter_provenance_identity(values: Vector[Parameter]): String =
    _identity(JsonObject(Vector("values" -> JsonArray(values.map(_parameter_value)))))

  private[media] def _plan_step_identity(step: PlanStep): String = {
    val fields = _plan_step_value(step).asInstanceOf[JsonObject].fields.dropRight(1)
    _identity(JsonObject(fields))
  }

}
