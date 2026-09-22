package cozy.modeler

import java.util.Locale
import org.goldenport.RAISE
import org.goldenport.realm.Realm

/*
 * @since   Sep. 22, 2026
 * @version Sep. 22, 2026
 * @author  ASAMI, Tomoharu
 */
/**
 * Emits the additive, provider-neutral Candidate-Admission producer ABI from
 * normalized CML source. It deliberately contains no StateMachine progression
 * or execution-placement instruction.
 */
private[modeler] object CandidateAdmissionProducerAbiGenerator {
  val schemaVersion: String = "cozy.cml.candidate-admission-producer-abi.v1"
  val bootstrapSchemaVersion: String = "cozy.cml.candidate-admission-producer-bootstrap.v1"
  val generatorIdentity: String = "cozy.modeler.CandidateAdmissionProducerAbiGenerator"
  val metadataPath: String = "target/cozy/candidate-admission-producer-abi.json"

  private val _root = "target/scala-3.3.8/src_managed/main/scala/domain/statemachine/candidateadmission"

  /**
   * Keeps the closed Phase 62.3 generation tree byte-for-byte unchanged when
   * a source model declares no Candidate-Admission semantics.
   */
  def hasCandidateAdmission(
    definitions: Vector[CompositeStateMachineDefinition],
    workflows: Vector[WorkflowDefinition]
  ): Boolean =
    definitions.exists(_.actions.exists(_.candidateAdmission.nonEmpty)) ||
      workflows.exists(_.compositeStateMachine.actions.exists(_.candidateAdmission.nonEmpty))

  private final case class ProducerModel(
    definition: CompositeStateMachineDefinition,
    workflow: Option[WorkflowDefinition]
  )

  private final case class JudgmentAdmissionPair(
    judgment: CompositeStateMachineLogicalAction,
    admission: CompositeStateMachineLogicalAction,
    value: CompositeStateMachineAdmissionAction
  )

  private final case class SourceReferenceDraft(
    value: String,
    source: CompositeStateMachineSourceIdentity
  )

  def generate(
    definitions: Vector[CompositeStateMachineDefinition],
    workflows: Vector[WorkflowDefinition]
  ): Realm = {
    val models = _producer_models(definitions, workflows)
    val builder = Realm.Builder()
    builder.set(s"${_root}/CandidateAdmissionProducerAbi.scala", _abi_source)
    builder.set(s"${_root}/CandidateAdmissionProducerComponentFactoryBootstrap.scala", _bootstrap_source(models))
    models.zipWithIndex.foreach { case (model, index) =>
      val objectname = _object_name(model, index)
      builder.set(s"${_root}/$objectname.scala", _model_source(model, objectname))
    }
    builder.build()
  }

  def canonicalJson(
    definitions: Vector[CompositeStateMachineDefinition],
    workflows: Vector[WorkflowDefinition]
  ): String = {
    val models = _producer_models(definitions, workflows)
    _json_object(Vector(
      "schemaVersion" -> _json_string(schemaVersion),
      "generator" -> _generator_json,
      "models" -> _json_array(models.map(_model_json))
    ))
  }

  private def _producer_models(
    definitions: Vector[CompositeStateMachineDefinition],
    workflows: Vector[WorkflowDefinition]
  ): Vector[ProducerModel] = {
    val direct = definitions.map(ProducerModel(_, None))
    val wrapped = workflows.map(workflow => ProducerModel(workflow.compositeStateMachine, Some(workflow)))
    (direct ++ wrapped).filter(model => model.definition.actions.exists(_.candidateAdmission.nonEmpty)).map { model =>
      model.workflow.foreach(_require_workflow_source)
      _validated_pairs(model.definition)
      model
    }
  }

  private def _require_workflow_source(value: WorkflowDefinition): Unit = {
    if (value.source == null ||
        value.source.root == null ||
        value.source.root.line.isEmpty ||
        value.source.definition == null ||
        value.source.definition.line.isEmpty)
      _malformed(s"Workflow '${value.identity}' is missing source provenance.")
    value.requiredOperations.foreach { operation =>
      if (operation.source == null ||
          operation.source.line.isEmpty ||
          operation.action == null ||
          operation.action.source == null ||
          operation.action.source.line.isEmpty)
        _malformed(s"Workflow '${value.identity}' Required SPI '${operation.capability}' is missing source provenance.")
    }
  }

  private def _validated_pairs(value: CompositeStateMachineDefinition): Vector[JudgmentAdmissionPair] = {
    _require_source(value.source, s"Composite StateMachine '${value.identity}' definition")
    val judgments = value.actions.flatMap { action =>
      action.candidateAdmission match {
        case Some(judgment: CompositeStateMachineJudgmentAction) =>
          _require_judgment_source(action, judgment)
          Some(action -> judgment)
        case _ => None
      }
    }
    val admissions = value.actions.flatMap { action =>
      action.candidateAdmission match {
        case Some(admission: CompositeStateMachineAdmissionAction) =>
          _require_admission_source(action, admission)
          _require_admission_metadata(action)
          Some(action -> admission)
        case _ => None
      }
    }
    value.actions.foreach { action =>
      action.candidateAdmission.foreach {
        case _: CompositeStateMachineJudgmentAction if !_same_key(action.kind, "JUDGMENT") =>
          _malformed(s"Action '${action.identity}' carries Judgment data but KIND is '${action.kind}'.")
        case _: CompositeStateMachineAdmissionAction if !_same_key(action.kind, "ADMISSION") =>
          _malformed(s"Action '${action.identity}' carries Admission data but KIND is '${action.kind}'.")
        case _ => ()
      }
    }
    val pairs = admissions.map { case (admissionaction, admission) =>
      val targets = judgments.filter { case (judgmentaction, _) =>
        _same_key(judgmentaction.identity, admission.candidateAction.value)
      }
      if (targets.size != 1)
        _malformed(
          s"Admission Action '${admissionaction.identity}' must identify exactly one Judgment Action '${admission.candidateAction.value}'."
        )
      val (judgmentaction, _) = targets.head
      JudgmentAdmissionPair(judgmentaction, admissionaction, admission)
    }
    judgments.foreach { case (judgment, _) =>
      val matches = pairs.filter(pair => _same_key(pair.judgment.identity, judgment.identity))
      if (matches.size != 1)
        _malformed(s"Judgment Action '${judgment.identity}' must have exactly one Admission Action.")
    }
    if (judgments.isEmpty || admissions.isEmpty)
      _malformed(s"Composite StateMachine '${value.identity}' has incomplete Candidate-Admission Actions.")
    pairs
  }

  private def _require_judgment_source(
    action: CompositeStateMachineLogicalAction,
    value: CompositeStateMachineJudgmentAction
  ): Unit = {
    _require_source(action.source, s"Judgment Action '${action.identity}'")
    _require_source(value.source, s"Judgment Action '${action.identity}' semantic data")
    val goal = _require_nonnull(value.goal, s"Judgment Action '${action.identity}' GOAL")
    val context = _require_nonnull(value.context, s"Judgment Action '${action.identity}' CONTEXT")
    val candidate = _require_nonnull(value.candidate, s"Judgment Action '${action.identity}' CANDIDATE")
    val alternatives = _require_nonnull_collection(value.alternatives, s"Judgment Action '${action.identity}' ALTERNATIVE")
    val criteria = _require_nonnull_collection(value.criteria, s"Judgment Action '${action.identity}' CRITERIA")
    val expectedresult = _require_nonnull(value.expectedResult, s"Judgment Action '${action.identity}' EXPECTED-RESULT")
    val rationale = _require_nonnull(value.rationale, s"Judgment Action '${action.identity}' RATIONALE")
    val evidence = _require_nonnull(value.evidence, s"Judgment Action '${action.identity}' EVIDENCE")
    val evidencescope = _require_nonnull(value.evidenceScope, s"Judgment Action '${action.identity}' EVIDENCE-SCOPE")
    val evidencefreshness = _require_nonnull(value.evidenceFreshness, s"Judgment Action '${action.identity}' EVIDENCE-FRESHNESS")
    val evidenceprovenance = _require_nonnull(value.evidenceProvenance, s"Judgment Action '${action.identity}' EVIDENCE-PROVENANCE")
    _require_reference_value(goal.value, goal.source, s"Judgment Action '${action.identity}' GOAL")
    _require_reference_value(context.value, context.source, s"Judgment Action '${action.identity}' CONTEXT")
    _require_reference_value(candidate.value, candidate.source, s"Judgment Action '${action.identity}' CANDIDATE")
    _require_alternatives(alternatives, s"Judgment Action '${action.identity}' ALTERNATIVE")
    _require_criteria(criteria, s"Judgment Action '${action.identity}' CRITERIA")
    _require_reference_value(expectedresult.value, expectedresult.source, s"Judgment Action '${action.identity}' EXPECTED-RESULT")
    _require_reference_value(rationale.value, rationale.source, s"Judgment Action '${action.identity}' RATIONALE")
    _require_reference_value(evidence.value, evidence.source, s"Judgment Action '${action.identity}' EVIDENCE")
    _require_reference_value(evidencescope.value, evidencescope.source, s"Judgment Action '${action.identity}' EVIDENCE-SCOPE")
    _require_reference_value(evidencefreshness.value, evidencefreshness.source, s"Judgment Action '${action.identity}' EVIDENCE-FRESHNESS")
    _require_reference_value(evidenceprovenance.value, evidenceprovenance.source, s"Judgment Action '${action.identity}' EVIDENCE-PROVENANCE")
  }

  private def _require_admission_source(
    action: CompositeStateMachineLogicalAction,
    value: CompositeStateMachineAdmissionAction
  ): Unit = {
    _require_source(action.source, s"Admission Action '${action.identity}'")
    _require_source(value.source, s"Admission Action '${action.identity}' semantic data")
    _require_source(value.candidateAction.source, s"Admission Action '${action.identity}' CANDIDATE-ACTION")
  }

  private def _require_source(value: CompositeStateMachineSourceIdentity, context: String): Unit =
    if (value == null || value.line.isEmpty)
      _malformed(s"$context is missing source provenance.")

  private def _require_reference_value(
    value: String,
    source: CompositeStateMachineSourceIdentity,
    context: String
  ): Unit = {
    if (value == null || value.trim.isEmpty)
      _malformed(s"$context requires a nonempty value.")
    _require_source(source, context)
  }

  private def _require_alternatives(
    values: Vector[CompositeStateMachineJudgmentAlternativeReference],
    context: String
  ): Unit = {
    if (values.isEmpty)
      _malformed(s"$context requires at least one value.")
    values.foreach { reference =>
      val alternative = _require_nonnull(reference, context)
      _require_reference_value(alternative.value, alternative.source, context)
    }
    if (values.map(reference => _normalize_key(reference.value)).distinct.size != values.size)
      _malformed(s"$context values must be unique.")
  }

  private def _require_criteria(
    values: Vector[CompositeStateMachineJudgmentCriterionReference],
    context: String
  ): Unit = {
    if (values.isEmpty)
      _malformed(s"$context requires at least one value.")
    values.foreach { reference =>
      val criterion = _require_nonnull(reference, context)
      _require_reference_value(criterion.value, criterion.source, context)
    }
    if (values.map(reference => _normalize_key(reference.value)).distinct.size != values.size)
      _malformed(s"$context values must be unique.")
  }

  private def _require_nonnull[A](value: A, context: String): A =
    if (value == null)
      _malformed(s"$context requires a nonnull reference.")
    else
      value

  private def _require_nonnull_collection[A](value: Vector[A], context: String): Vector[A] =
    if (value == null)
      _malformed(s"$context requires a nonnull collection.")
    else
      value

  private def _normalize_key(value: String): String =
    value.toLowerCase(Locale.ROOT).filter(_.isLetterOrDigit)

  private def _require_admission_metadata(value: CompositeStateMachineLogicalAction): Unit =
    value.metadata match {
      case Some(metadata) if
          metadata.effectClass == CompositeStateMachineEffectClass.Local &&
            metadata.transactionRequirement == CompositeStateMachineTransactionRequirement.Required => ()
      case _ => _malformed(s"Admission Action '${value.identity}' requires EFFECT=LOCAL and TRANSACTION=REQUIRED.")
    }

  private def _malformed(message: String): Nothing =
    RAISE.invalidArgumentFault(s"CAM-73-02 malformed normalized Candidate-Admission graph: $message")

  private def _abi_source: String =
    s"""package domain.statemachine.candidateadmission
       |
       |object CandidateAdmissionProducerAbi {
       |  val VERSION: String = ${_quote(schemaVersion)}
       |  val GENERATOR: String = ${_quote(generatorIdentity)}
       |
       |  final case class SourceIdentity(line: Option[Int])
       |  final case class SourceReference(value: String, source: SourceIdentity)
       |  final case class Operation(service: String, name: String, inputType: Option[String], resultType: Option[String])
       |  final case class WorkflowIdentity(identity: String, version: String, rootSource: SourceIdentity, definitionSource: SourceIdentity)
       |  final case class ModelIdentity(compositeStateMachineIdentity: String, compositeStateMachineName: String, compositeStateMachineSource: SourceIdentity, workflow: Option[WorkflowIdentity])
       |  final case class GeneratorProvenance(schemaVersion: String, generatorIdentity: String)
       |  final case class JudgmentDescriptor(actionIdentity: String, operation: Operation, inputBinding: Option[String], goal: SourceReference, context: SourceReference, candidate: SourceReference, alternatives: Vector[SourceReference], criteria: Vector[SourceReference], expectedResult: SourceReference, rationale: SourceReference, evidence: SourceReference, evidenceScope: SourceReference, evidenceFreshness: SourceReference, evidenceProvenance: SourceReference, actionSource: SourceIdentity, semanticSource: SourceIdentity)
       |  final case class AdmissionDescriptor(actionIdentity: String, candidateJudgmentActionIdentity: String, operation: Operation, inputBinding: Option[String], effectClass: String, transactionRequirement: String, actionSource: SourceIdentity, semanticSource: SourceIdentity, candidateActionSource: SourceIdentity)
       |  final case class JudgmentAdmission(judgmentActionIdentity: String, admissionActionIdentity: String, judgmentSource: SourceIdentity, admissionSource: SourceIdentity, candidateActionSource: SourceIdentity)
       |  final case class WorkflowRequiredSpiCorrelation(capability: String, actionIdentity: String, operation: Operation, capabilitySource: SourceIdentity, actionSource: SourceIdentity)
       |  final case class ProducerDescriptor(schemaVersion: String, model: ModelIdentity, generator: GeneratorProvenance, judgments: Vector[JudgmentDescriptor], admissions: Vector[AdmissionDescriptor], judgmentAdmissions: Vector[JudgmentAdmission], requiredSpi: Vector[WorkflowRequiredSpiCorrelation])
       |  final case class ComponentFactoryMetadata(producer: ProducerDescriptor)
       |}
       |""".stripMargin

  private def _bootstrap_source(values: Vector[ProducerModel]): String = {
    val metadata = _vector(values.zipWithIndex.map { case (value, index) =>
      s"${_object_name(value, index)}.componentFactoryMetadata"
    })
    s"""package domain.statemachine.candidateadmission
       |
       |object CandidateAdmissionProducerComponentFactoryBootstrap {
       |  val schemaVersion: String = ${_quote(bootstrapSchemaVersion)}
       |  val componentFactoryMetadata: Vector[CandidateAdmissionProducerAbi.ComponentFactoryMetadata] = $metadata
       |}
       |""".stripMargin
  }

  private def _model_source(value: ProducerModel, objectname: String): String =
    s"""package domain.statemachine.candidateadmission
       |
       |object $objectname {
       |  val producer: CandidateAdmissionProducerAbi.ProducerDescriptor = ${_producer_descriptor(value)}
       |  val componentFactoryMetadata: CandidateAdmissionProducerAbi.ComponentFactoryMetadata = CandidateAdmissionProducerAbi.ComponentFactoryMetadata(producer)
       |}
       |""".stripMargin

  private def _producer_descriptor(value: ProducerModel): String = {
    val pairs = _validated_pairs(value.definition)
    val judgments = value.definition.actions.collect {
      case action if action.candidateAdmission.exists(_.isInstanceOf[CompositeStateMachineJudgmentAction]) => _judgment_descriptor(action)
    }
    val admissions = value.definition.actions.collect {
      case action if action.candidateAdmission.exists(_.isInstanceOf[CompositeStateMachineAdmissionAction]) => _admission_descriptor(action)
    }
    s"""CandidateAdmissionProducerAbi.ProducerDescriptor(
       |  schemaVersion = ${_quote(schemaVersion)},
       |  model = ${_model_identity(value)},
       |  generator = ${_generator_source},
       |  judgments = ${_vector(judgments)},
       |  admissions = ${_vector(admissions)},
       |  judgmentAdmissions = ${_vector(pairs.map(_judgment_admission))},
       |  requiredSpi = ${_vector(value.workflow.toVector.flatMap(_.requiredOperations).map(_required_spi))}
       |)""".stripMargin
  }

  private def _model_identity(value: ProducerModel): String =
    s"""CandidateAdmissionProducerAbi.ModelIdentity(
       |  compositeStateMachineIdentity = ${_quote(value.definition.identity)},
       |  compositeStateMachineName = ${_quote(value.definition.name)},
       |  compositeStateMachineSource = ${_source(value.definition.source)},
       |  workflow = ${_option(value.workflow.map(_workflow_identity))}
       |)""".stripMargin

  private def _workflow_identity(value: WorkflowDefinition): String =
    s"""CandidateAdmissionProducerAbi.WorkflowIdentity(
       |  identity = ${_quote(value.identity)},
       |  version = ${_quote(value.version)},
       |  rootSource = ${_workflow_source(value.source.root)},
       |  definitionSource = ${_workflow_source(value.source.definition)}
       |)""".stripMargin

  private def _generator_source: String =
    s"CandidateAdmissionProducerAbi.GeneratorProvenance(${_quote(schemaVersion)}, ${_quote(generatorIdentity)})"

  private def _judgment_descriptor(value: CompositeStateMachineLogicalAction): String = {
    val judgment = value.candidateAdmission.collect { case x: CompositeStateMachineJudgmentAction => x }.getOrElse(
      _malformed(s"Action '${value.identity}' is not a Judgment Action.")
    )
    s"""CandidateAdmissionProducerAbi.JudgmentDescriptor(
       |  actionIdentity = ${_quote(value.identity)},
       |  operation = ${_operation(value.operation)},
       |  inputBinding = ${_option(value.inputBinding.map(_quote))},
      |  goal = ${_reference(_reference_draft(judgment.goal.value, judgment.goal.source))},
       |  context = ${_reference(_reference_draft(judgment.context.value, judgment.context.source))},
       |  candidate = ${_reference(_reference_draft(judgment.candidate.value, judgment.candidate.source))},
       |  alternatives = ${_vector(judgment.alternatives.map(x => _reference(_reference_draft(x.value, x.source))))},
       |  criteria = ${_vector(judgment.criteria.map(x => _reference(_reference_draft(x.value, x.source))))},
       |  expectedResult = ${_reference(_reference_draft(judgment.expectedResult.value, judgment.expectedResult.source))},
       |  rationale = ${_reference(_reference_draft(judgment.rationale.value, judgment.rationale.source))},
       |  evidence = ${_reference(_reference_draft(judgment.evidence.value, judgment.evidence.source))},
       |  evidenceScope = ${_reference(_reference_draft(judgment.evidenceScope.value, judgment.evidenceScope.source))},
       |  evidenceFreshness = ${_reference(_reference_draft(judgment.evidenceFreshness.value, judgment.evidenceFreshness.source))},
       |  evidenceProvenance = ${_reference(_reference_draft(judgment.evidenceProvenance.value, judgment.evidenceProvenance.source))},
       |  actionSource = ${_source(value.source)},
       |  semanticSource = ${_source(judgment.source)}
       |)""".stripMargin
  }

  private def _admission_descriptor(value: CompositeStateMachineLogicalAction): String = {
    val admission = value.candidateAdmission.collect { case x: CompositeStateMachineAdmissionAction => x }.getOrElse(
      _malformed(s"Action '${value.identity}' is not an Admission Action.")
    )
    val metadata = value.metadata.getOrElse(
      _malformed(s"Admission Action '${value.identity}' is missing local transaction metadata.")
    )
    s"""CandidateAdmissionProducerAbi.AdmissionDescriptor(
       |  actionIdentity = ${_quote(value.identity)},
       |  candidateJudgmentActionIdentity = ${_quote(admission.candidateAction.value)},
       |  operation = ${_operation(value.operation)},
       |  inputBinding = ${_option(value.inputBinding.map(_quote))},
       |  effectClass = ${_quote(metadata.effectClass.canonicalValue)},
       |  transactionRequirement = ${_quote(metadata.transactionRequirement.canonicalValue)},
       |  actionSource = ${_source(value.source)},
       |  semanticSource = ${_source(admission.source)},
       |  candidateActionSource = ${_source(admission.candidateAction.source)}
       |)""".stripMargin
  }

  private def _judgment_admission(value: JudgmentAdmissionPair): String =
    s"""CandidateAdmissionProducerAbi.JudgmentAdmission(
       |  judgmentActionIdentity = ${_quote(value.judgment.identity)},
       |  admissionActionIdentity = ${_quote(value.admission.identity)},
       |  judgmentSource = ${_source(value.judgment.source)},
       |  admissionSource = ${_source(value.admission.source)},
       |  candidateActionSource = ${_source(value.value.candidateAction.source)}
       |)""".stripMargin

  private def _required_spi(value: WorkflowRequiredOperation): String =
    s"""CandidateAdmissionProducerAbi.WorkflowRequiredSpiCorrelation(
       |  capability = ${_quote(value.capability)},
       |  actionIdentity = ${_quote(value.action.identity)},
       |  operation = ${_operation(value.action.operation)},
       |  capabilitySource = ${_workflow_source(value.source)},
       |  actionSource = ${_source(value.action.source)}
       |)""".stripMargin

  private def _operation(value: CompositeStateMachineOperation): String =
    s"CandidateAdmissionProducerAbi.Operation(${_quote(value.service)}, ${_quote(value.name)}, ${_option(value.inputType.map(_quote))}, ${_option(value.outputType.map(_quote))})"

  private def _reference(value: SourceReferenceDraft): String =
    s"CandidateAdmissionProducerAbi.SourceReference(${_quote(value.value)}, ${_source(value.source)})"

  private def _reference_draft(value: String, source: CompositeStateMachineSourceIdentity): SourceReferenceDraft =
    SourceReferenceDraft(value, source)

  private def _source(value: CompositeStateMachineSourceIdentity): String =
    s"CandidateAdmissionProducerAbi.SourceIdentity(${_option(Option(value).flatMap(_.line).map(_.toString))})"

  private def _workflow_source(value: WorkflowSourceIdentity): String =
    s"CandidateAdmissionProducerAbi.SourceIdentity(${_option(Option(value).flatMap(_.line).map(_.toString))})"

  private def _model_json(value: ProducerModel): String = {
    val pairs = _validated_pairs(value.definition)
    val judgments = value.definition.actions.collect {
      case action if action.candidateAdmission.exists(_.isInstanceOf[CompositeStateMachineJudgmentAction]) => _judgment_json(action)
    }
    val admissions = value.definition.actions.collect {
      case action if action.candidateAdmission.exists(_.isInstanceOf[CompositeStateMachineAdmissionAction]) => _admission_json(action)
    }
    _json_object(Vector(
      "model" -> _model_identity_json(value),
      "generator" -> _generator_json,
      "judgments" -> _json_array(judgments),
      "admissions" -> _json_array(admissions),
      "judgmentAdmissions" -> _json_array(pairs.map(_judgment_admission_json)),
      "requiredSpi" -> _json_array(value.workflow.toVector.flatMap(_.requiredOperations).map(_required_spi_json))
    ))
  }

  private def _model_identity_json(value: ProducerModel): String =
    _json_object(Vector(
      "compositeStateMachineIdentity" -> _json_string(value.definition.identity),
      "compositeStateMachineName" -> _json_string(value.definition.name),
      "compositeStateMachineSource" -> _source_json(value.definition.source),
      "workflow" -> _json_option(value.workflow.map(_workflow_identity_json))
    ))

  private def _workflow_identity_json(value: WorkflowDefinition): String =
    _json_object(Vector(
      "identity" -> _json_string(value.identity),
      "version" -> _json_string(value.version),
      "rootSource" -> _workflow_source_json(value.source.root),
      "definitionSource" -> _workflow_source_json(value.source.definition)
    ))

  private def _generator_json: String =
    _json_object(Vector(
      "schemaVersion" -> _json_string(schemaVersion),
      "generatorIdentity" -> _json_string(generatorIdentity)
    ))

  private def _judgment_json(value: CompositeStateMachineLogicalAction): String = {
    val judgment = value.candidateAdmission.collect { case x: CompositeStateMachineJudgmentAction => x }.getOrElse(
      _malformed(s"Action '${value.identity}' is not a Judgment Action.")
    )
    _json_object(Vector(
      "actionIdentity" -> _json_string(value.identity),
      "operation" -> _operation_json(value.operation),
      "inputBinding" -> _json_option(value.inputBinding.map(_json_string)),
      "goal" -> _reference_json(_reference_draft(judgment.goal.value, judgment.goal.source)),
      "context" -> _reference_json(_reference_draft(judgment.context.value, judgment.context.source)),
      "candidate" -> _reference_json(_reference_draft(judgment.candidate.value, judgment.candidate.source)),
      "alternatives" -> _json_array(judgment.alternatives.map(x => _reference_json(_reference_draft(x.value, x.source)))),
      "criteria" -> _json_array(judgment.criteria.map(x => _reference_json(_reference_draft(x.value, x.source)))),
      "expectedResult" -> _reference_json(_reference_draft(judgment.expectedResult.value, judgment.expectedResult.source)),
      "rationale" -> _reference_json(_reference_draft(judgment.rationale.value, judgment.rationale.source)),
      "evidence" -> _reference_json(_reference_draft(judgment.evidence.value, judgment.evidence.source)),
      "evidenceScope" -> _reference_json(_reference_draft(judgment.evidenceScope.value, judgment.evidenceScope.source)),
      "evidenceFreshness" -> _reference_json(_reference_draft(judgment.evidenceFreshness.value, judgment.evidenceFreshness.source)),
      "evidenceProvenance" -> _reference_json(_reference_draft(judgment.evidenceProvenance.value, judgment.evidenceProvenance.source)),
      "actionSource" -> _source_json(value.source),
      "semanticSource" -> _source_json(judgment.source)
    ))
  }

  private def _admission_json(value: CompositeStateMachineLogicalAction): String = {
    val admission = value.candidateAdmission.collect { case x: CompositeStateMachineAdmissionAction => x }.getOrElse(
      _malformed(s"Action '${value.identity}' is not an Admission Action.")
    )
    val metadata = value.metadata.getOrElse(
      _malformed(s"Admission Action '${value.identity}' is missing local transaction metadata.")
    )
    _json_object(Vector(
      "actionIdentity" -> _json_string(value.identity),
      "candidateJudgmentActionIdentity" -> _json_string(admission.candidateAction.value),
      "operation" -> _operation_json(value.operation),
      "inputBinding" -> _json_option(value.inputBinding.map(_json_string)),
      "effectClass" -> _json_string(metadata.effectClass.canonicalValue),
      "transactionRequirement" -> _json_string(metadata.transactionRequirement.canonicalValue),
      "actionSource" -> _source_json(value.source),
      "semanticSource" -> _source_json(admission.source),
      "candidateActionSource" -> _source_json(admission.candidateAction.source)
    ))
  }

  private def _judgment_admission_json(value: JudgmentAdmissionPair): String =
    _json_object(Vector(
      "judgmentActionIdentity" -> _json_string(value.judgment.identity),
      "admissionActionIdentity" -> _json_string(value.admission.identity),
      "judgmentSource" -> _source_json(value.judgment.source),
      "admissionSource" -> _source_json(value.admission.source),
      "candidateActionSource" -> _source_json(value.value.candidateAction.source)
    ))

  private def _required_spi_json(value: WorkflowRequiredOperation): String =
    _json_object(Vector(
      "capability" -> _json_string(value.capability),
      "actionIdentity" -> _json_string(value.action.identity),
      "operation" -> _operation_json(value.action.operation),
      "capabilitySource" -> _workflow_source_json(value.source),
      "actionSource" -> _source_json(value.action.source)
    ))

  private def _operation_json(value: CompositeStateMachineOperation): String =
    _json_object(Vector(
      "service" -> _json_string(value.service),
      "name" -> _json_string(value.name),
      "inputType" -> _json_option(value.inputType.map(_json_string)),
      "resultType" -> _json_option(value.outputType.map(_json_string))
    ))

  private def _reference_json(value: SourceReferenceDraft): String =
    _json_object(Vector(
      "value" -> _json_string(value.value),
      "source" -> _source_json(value.source)
    ))

  private def _source_json(value: CompositeStateMachineSourceIdentity): String =
    _json_object(Vector("line" -> _json_option(Option(value).flatMap(_.line).map(_.toString))))

  private def _workflow_source_json(value: WorkflowSourceIdentity): String =
    _json_object(Vector("line" -> _json_option(Option(value).flatMap(_.line).map(_.toString))))

  private def _object_name(value: ProducerModel, index: Int): String = {
    val identity = value.workflow.map(_.identity).getOrElse(value.definition.identity)
    val words = identity.split("[^A-Za-z0-9]+").toVector.filter(_.nonEmpty)
    val stem = words.map(word => word.take(1).toUpperCase(Locale.ROOT) + word.drop(1)).mkString
    val normalized = if (stem.isEmpty) "CandidateAdmission" else stem
    val prefixed = if (normalized.head.isDigit) s"CandidateAdmission$normalized" else normalized
    s"${prefixed}CandidateAdmissionProducer${index + 1}"
  }

  private def _same_key(a: String, b: String): Boolean =
    a != null && b != null && a.equalsIgnoreCase(b)

  private def _vector(values: Vector[String]): String =
    if (values.isEmpty) "Vector.empty" else values.mkString("Vector(", ", ", ")")

  private def _option(value: Option[String]): String = value.map(x => s"Some($x)").getOrElse("None")

  private def _json_object(fields: Vector[(String, String)]): String =
    fields.map { case (name, value) => s"${_json_string(name)}:$value" }.mkString("{", ",", "}")

  private def _json_array(values: Vector[String]): String = values.mkString("[", ",", "]")

  private def _json_option(value: Option[String]): String = value.getOrElse("null")

  private def _json_string(value: String): String = _quote(value)

  private def _quote(value: String): String = {
    val escaped = value.flatMap {
      case '\\' => "\\\\"
      case '"' => "\\\""
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case character if Character.isISOControl(character) => f"\\u${character.toInt}%04x"
      case character => character.toString
    }
    "\"" + escaped + "\""
  }
}
