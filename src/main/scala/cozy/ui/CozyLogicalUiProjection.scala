package cozy.ui

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import cozy.ui.CozyLogicalUi._

/*
 * @since   Sep. 2, 2026
 * @version Sep. 2, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyLogicalUiProjection {
  private val _schema = "cozy.logical-ui.v1"
  private val _version = 1
  private val _candidate_kind = "candidate"
  private val _consumed_input_kind = "consumed-input"
  private val _acceptance_decision_kind = "acceptance-decision"
  private val _accepted_kind = "accepted"
  private val _projection_schema = "cozy.usecase-screen-projection.v1"
  private val _projection_version = 1

  def canonicalProjectionContent(candidateIdentity: String, input: UseCaseScreenProjection): String =
    _json_object(Vector(
      "schema" -> _json_string(_projection_schema),
      "version" -> _projection_version.toString,
      "candidateIdentity" -> _json_string(candidateIdentity),
      "catalog" -> _json_array(input.catalog.map(_use_case_layers_json)),
      "steps" -> _json_array(input.steps.map(_step_json)),
      "screens" -> _json_array(input.screens.map(_screen_json)),
      "mappings" -> _json_array(input.mappings.map(_mapping_json)),
      "aggregateBoundaries" -> _json_array(input.aggregateBoundaries.map(_boundary_json))
    ))

  def projectionComponentError(candidate: LogicalUiCandidate, input: UseCaseScreenProjection): Option[LogicalUiError] = {
    val candidatebindings = candidate.input.componentBindings.toSet
    val references = input.screens.flatMap { screen =>
      val subject = Vector(screen.subject.binding)
      val usages = screen.interactions.flatMap(_.componentUsages.map(_.binding))
      val mutations = screen.interactions.flatMap(_.mutation.toVector.flatMap(action => Vector(action.target, action.operation)))
      subject ++ usages ++ mutations
    }
    val boundaryreferences = input.aggregateBoundaries.flatMap(boundary =>
      Vector(boundary.aggregate, boundary.root) ++ boundary.members ++ boundary.publicOperations
    )
    (references ++ boundaryreferences).zipWithIndex.collectFirst {
      case (binding, index) if binding == null =>
        LogicalUiError("LUI43_PROJECTION_COMPONENT_CLOSED", s"projection.componentBindings[$index]", "every Component reference must be an exact public candidate binding")
      case (binding, index) if !candidatebindings.contains(binding) =>
        LogicalUiError("LUI43_PROJECTION_COMPONENT_CLOSED", s"projection.componentBindings[$index]", "unknown or unexported Component binding is not admitted")
    }.orElse {
      val used = references.filter(_ != null).toSet
      candidate.input.componentBindings.find(binding => !used.contains(binding)).map { binding =>
        LogicalUiError("LUI43_PROJECTION_COMPONENT_UNJUSTIFIED", s"candidate.input.componentBindings.${binding.exportId}", "every candidate binding must be used by a subject, interaction usage, or mutation action")
      }
    }
  }

  def canonicalCandidateDocument(candidate: LogicalUiCandidate): String =
    _json_object(
      Vector(
        "schema" -> _json_string(_schema),
        "version" -> _version.toString,
        "kind" -> _json_string(_candidate_kind),
        "inputIdentity" -> _json_string(candidate.inputIdentity)
      ) ++ _logical_content_fields(candidate.input) ++ Vector(
        "identity" -> _json_string(candidate.identity)
      )
    )

  def canonicalLogicalContent(input: CandidateInput): String =
    _json_object(
      Vector(
        "schema" -> _json_string(_schema),
        "version" -> _version.toString
      ) ++ _logical_content_fields(input)
    )

  def inputIdentity(content: String): String =
    identity(_json_object(Vector(
      "schema" -> _json_string(_schema),
      "version" -> _version.toString,
      "kind" -> _json_string(_consumed_input_kind),
      "logicalContent" -> _json_string(content)
    )))

  def decisionIdentity(value: AcceptanceDecision): String =
    identity(_json_object(Vector(
      "schema" -> _json_string(_schema),
      "version" -> _version.toString,
      "kind" -> _json_string(_acceptance_decision_kind),
      "decisionId" -> _json_string(value.decisionId),
      "candidateIdentity" -> _json_string(value.candidateIdentity)
    )))

  def acceptedIdentity(candidate: LogicalUiCandidate, decision: AcceptanceDecision): String =
    identity(_json_object(Vector(
      "schema" -> _json_string(_schema),
      "version" -> _version.toString,
      "kind" -> _json_string(_accepted_kind),
      "candidateIdentity" -> _json_string(candidate.identity),
      "inputIdentity" -> _json_string(candidate.inputIdentity),
      "decisionIdentity" -> _json_string(decision.decisionIdentity)
    )))

  def identity(value: String): String =
    "sha256:" + MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)).map(byte => f"${byte & 0xff}%02x").mkString

  private def _logical_content_fields(input: CandidateInput): Vector[(String, String)] =
    Vector(
      "componentSurfaces" -> _json_array(input.componentSurfaces.map(_component_surface_json)),
      "componentBindings" -> _json_array(input.componentBindings.map(_component_binding_json)),
      "useCases" -> _use_cases_json(input.useCases)
    )

  private def _use_case_layers_json(value: UseCaseLayers): String = _use_cases_json(value)

  private def _step_json(value: UiUseCaseStep): String =
    _json_object(Vector(
      "uiUseCase" -> _use_case_reference_json(value.uiUseCase),
      "stepId" -> _json_string(value.stepId),
      "path" -> _json_string(value.path.id)
    ))

  private def _mapping_json(value: ScreenInteractionMapping): String =
    _json_object(Vector(
      "uiUseCase" -> _use_case_reference_json(value.uiUseCase),
      "stepId" -> _json_string(value.stepId),
      "screenId" -> _json_string(value.screenId),
      "interactionId" -> _json_string(value.interactionId)
    ))

  private def _screen_json(value: LogicalScreen): String =
    _json_object(Vector(
      "id" -> _json_string(value.id),
      "primaryPurpose" -> _json_string(value.primaryPurpose),
      "secondaryPurposes" -> _json_array(value.secondaryPurposes.map(_json_string)),
      "subject" -> _subject_json(value.subject),
      "regions" -> _json_array(value.regions.map(_region_json)),
      "interactions" -> _json_array(value.interactions.map(_interaction_json)),
      "feedbackStates" -> _json_array(value.feedbackStates.map(state => _json_string(state.id)))
    ))

  private def _subject_json(value: ScreenSubject): String =
    _json_object(Vector(
      "role" -> _json_string(value.role.id),
      "binding" -> _component_binding_json(value.binding)
    ))

  private def _region_json(value: SemanticRegion): String =
    _json_object(Vector(
      "id" -> _json_string(value.id),
      "parentId" -> value.parentId.map(_json_string).getOrElse("null"),
      "order" -> value.order.toString
    ))

  private def _interaction_json(value: ScreenInteraction): String =
    _json_object(Vector(
      "id" -> _json_string(value.id),
      "kind" -> _json_string(value.kind.id),
      "componentUsages" -> _json_array(value.componentUsages.map(_usage_json)),
      "mutation" -> value.mutation.map(_mutation_json).getOrElse("null"),
      "navigation" -> value.navigation.map(_navigation_json).getOrElse("null")
    ))

  private def _usage_json(value: ComponentUsage): String =
    _json_object(Vector(
      "role" -> _json_string(value.role.id),
      "binding" -> _component_binding_json(value.binding)
    ))

  private def _mutation_json(value: MutationAction): String =
    _json_object(Vector(
      "target" -> _component_binding_json(value.target),
      "operation" -> _component_binding_json(value.operation)
    ))

  private def _navigation_json(value: NavigationEndpoint): String =
    _json_object(Vector(
      "endpointId" -> _json_string(value.endpointId),
      "targetScreenId" -> _json_string(value.targetScreenId)
    ))

  private def _boundary_json(value: AggregateBoundary): String =
    _json_object(Vector(
      "aggregate" -> _component_binding_json(value.aggregate),
      "root" -> _component_binding_json(value.root),
      "members" -> _json_array(value.members.map(_component_binding_json)),
      "publicOperations" -> _json_array(value.publicOperations.map(_component_binding_json))
    ))

  private def _component_surface_json(value: ComponentSurface): String =
    _json_object(Vector(
      "component" -> _component_coordinate_json(value.component),
      "exportIds" -> _json_array(value.exportIds.map(_json_string))
    ))

  private def _component_binding_json(value: ComponentBinding): String =
    _json_object(Vector(
      "component" -> _component_coordinate_json(value.component),
      "exportId" -> _json_string(value.exportId)
    ))

  private def _component_coordinate_json(value: ComponentCoordinate): String =
    _json_object(Vector(
      "namespace" -> _json_string(value.namespace),
      "id" -> _json_string(value.id),
      "version" -> _json_string(value.version)
    ))

  private def _use_cases_json(value: UseCaseLayers): String =
    _json_object(Vector(
      "business" -> _use_case_reference_json(value.business),
      "system" -> _use_case_reference_json(value.system),
      "ui" -> _use_case_reference_json(value.ui),
      "realizations" -> _json_array(value.realizations.map(_use_case_realization_json))
    ))

  private def _use_case_reference_json(value: UseCaseReference): String =
    _json_object(Vector(
      "layer" -> _json_string(value.layer.id),
      "id" -> _json_string(value.id)
    ))

  private def _use_case_realization_json(value: UseCaseRealization): String =
    _json_object(Vector(
      "source" -> _use_case_reference_json(value.source),
      "target" -> _use_case_reference_json(value.target)
    ))

  private def _json_object(fields: Vector[(String, String)]): String =
    fields.map { case (name, value) => s"${_json_string(name)}:$value" }.mkString("{", ",", "}")

  private def _json_array(values: Vector[String]): String = values.mkString("[", ",", "]")

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
}
