package cozy.ui

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/*
 * @since   Sep. 1, 2026
 * @version Sep. 1, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyLogicalUi {
  sealed trait UseCaseLayer {
    def id: String
  }

  case object Business extends UseCaseLayer {
    val id = "business"
  }

  case object System extends UseCaseLayer {
    val id = "system"
  }

  case object Ui extends UseCaseLayer {
    val id = "ui"
  }

  final case class LogicalUiError(code: String, path: String, reason: String)

  final case class ComponentCoordinate(namespace: String, id: String, version: String) {
    def qualifiedName: String = s"$namespace.$id"
    def canonicalIdentity: String = s"$qualifiedName@$version"
  }

  final case class ComponentSurface(component: ComponentCoordinate, exportIds: Vector[String])
  final case class ComponentBinding(component: ComponentCoordinate, exportId: String)
  final case class UseCaseReference(layer: UseCaseLayer, id: String)
  final case class UseCaseRealization(source: UseCaseReference, target: UseCaseReference)
  final case class UseCaseLayers(
    business: UseCaseReference,
    system: UseCaseReference,
    ui: UseCaseReference,
    realizations: Vector[UseCaseRealization]
  )
  final case class CandidateInput(
    componentSurfaces: Vector[ComponentSurface],
    componentBindings: Vector[ComponentBinding],
    useCases: UseCaseLayers
  )

  final case class AcceptanceDecision(decisionId: String, candidateIdentity: String) {
    def decisionIdentity: String = _decision_identity(this)
  }

  final class LogicalUiCandidate private[CozyLogicalUi] (
    val identity: String,
    val inputIdentity: String,
    val input: CandidateInput
  ) {
    def canonicalContent: String = _canonical_logical_content(input)
    def canonicalJson: String = _canonical_candidate_document(this)
  }

  final class AcceptedLogicalUi private[CozyLogicalUi] (
    val identity: String,
    val candidate: LogicalUiCandidate,
    val decision: AcceptanceDecision
  )

  private val _schema = "cozy.logical-ui.v1"
  private val _version = 1
  private val _candidate_kind = "candidate"
  private val _consumed_input_kind = "consumed-input"
  private val _acceptance_decision_kind = "acceptance-decision"
  private val _accepted_kind = "accepted"
  private val _component_segment_pattern = "[A-Za-z][A-Za-z0-9_-]*".r

  def schema: String = _schema
  def version: Int = _version

  def candidate(input: CandidateInput): Either[LogicalUiError, LogicalUiCandidate] =
    _normalize_input(input) match {
      case Left(error) => Left(error)
      case Right(normalized) =>
        val content = _canonical_logical_content(normalized)
        Right(new LogicalUiCandidate(_identity(content), _input_identity(content), normalized))
    }

  def accept(
    candidate: LogicalUiCandidate,
    decision: AcceptanceDecision
  ): Either[LogicalUiError, AcceptedLogicalUi] = {
    if (candidate == null)
      Left(LogicalUiError("LUI43_ACCEPTANCE_CANDIDATE_MISSING", "candidate", "candidate is required"))
    else
      _acceptance_error(candidate, decision) match {
        case Some(error) => Left(error)
        case None =>
          val identity = _accepted_identity(candidate, decision)
          if (identity == candidate.identity)
            Left(LogicalUiError("LUI43_ACCEPTANCE_IDENTITY_INVALID", "acceptance", "accepted identity must differ from candidate identity"))
          else
            Right(new AcceptedLogicalUi(identity, candidate, decision))
      }
  }

  private def _normalize_input(input: CandidateInput): Either[LogicalUiError, CandidateInput] = {
    if (input == null)
      Left(LogicalUiError("LUI43_INPUT_MISSING", "input", "candidate input is required"))
    else
      _normalize_surfaces(input.componentSurfaces) match {
        case Left(error) => Left(error)
        case Right(surfaces) =>
          _normalize_bindings(input.componentBindings, surfaces) match {
            case Left(error) => Left(error)
            case Right(bindings) =>
              _normalize_use_cases(input.useCases) match {
                case Left(error) => Left(error)
                case Right(usecases) => Right(CandidateInput(surfaces, bindings, usecases))
              }
          }
      }
  }

  private def _normalize_surfaces(values: Vector[ComponentSurface]): Either[LogicalUiError, Vector[ComponentSurface]] = {
    val surfaces = Option(values).getOrElse(Vector.empty)
    if (surfaces.isEmpty)
      Left(LogicalUiError("LUI43_COMPONENT_SURFACE_MISSING", "componentSurfaces", "at least one public Component surface is required"))
    else {
      val error = surfaces.zipWithIndex.flatMap { case (surface, index) =>
        _surface_error(surface, s"componentSurfaces[$index]")
      }.headOption
      error match {
        case Some(value) => Left(value)
        case None =>
          val identities = surfaces.map(_.component.canonicalIdentity)
          if (identities.distinct.size != identities.size)
            Left(LogicalUiError("LUI43_COMPONENT_SURFACE_DUPLICATE", "componentSurfaces", "Component surface identity must be unique"))
          else {
            val normalized = surfaces.map { surface =>
              surface.copy(exportIds = surface.exportIds.sorted)
            }.sortBy(_.component.canonicalIdentity)
            Right(normalized)
          }
      }
    }
  }

  private def _normalize_bindings(
    values: Vector[ComponentBinding],
    surfaces: Vector[ComponentSurface]
  ): Either[LogicalUiError, Vector[ComponentBinding]] = {
    val bindings = Option(values).getOrElse(Vector.empty)
    if (bindings.isEmpty)
      Left(LogicalUiError("LUI43_COMPONENT_BINDING_MISSING", "componentBindings", "at least one direct public export binding is required"))
    else {
      val surfacebyidentity = surfaces.map(surface => surface.component.canonicalIdentity -> surface).toMap
      val error = bindings.zipWithIndex.flatMap { case (binding, index) =>
        _binding_error(binding, surfacebyidentity, s"componentBindings[$index]")
      }.headOption
      error match {
        case Some(value) => Left(value)
        case None =>
          val keys = bindings.map(_binding_key)
          if (keys.distinct.size != keys.size)
            Left(LogicalUiError("LUI43_COMPONENT_BINDING_DUPLICATE", "componentBindings", "Component binding must be unique"))
          else
            Right(bindings.sortBy(_binding_key))
      }
    }
  }

  private def _normalize_use_cases(value: UseCaseLayers): Either[LogicalUiError, UseCaseLayers] = {
    if (value == null)
      Left(LogicalUiError("LUI43_USE_CASES_MISSING", "useCases", "Business, System, and UI UseCase identities are required"))
    else {
      val referenceerrors = Vector(
        _reference_error(value.business, Business, "useCases.business"),
        _reference_error(value.system, System, "useCases.system"),
        _reference_error(value.ui, Ui, "useCases.ui")
      ).flatten
      referenceerrors.headOption match {
        case Some(error) => Left(error)
        case None =>
          val references = Vector(value.business, value.system, value.ui)
          if (references.map(_.id).distinct.size != references.size)
            Left(LogicalUiError("LUI43_USE_CASE_IDENTITY_DUPLICATE", "useCases", "Business, System, and UI identities must be distinct"))
          else {
            val realizations = Option(value.realizations).getOrElse(Vector.empty)
            if (realizations.exists(_ == null))
              Left(LogicalUiError("LUI43_USE_CASE_REALIZATION_INVALID", "useCases.realizations", "realization must be an explicit relation"))
            else {
              val expected = Vector(
                UseCaseRealization(value.business, value.system),
                UseCaseRealization(value.system, value.ui)
              )
              if (realizations.size != expected.size || realizations.toSet != expected.toSet)
                Left(LogicalUiError("LUI43_USE_CASE_REALIZATION_INVALID", "useCases.realizations", "v1 requires exactly Business -> System and System -> UI realizations"))
              else
                Right(value.copy(realizations = realizations.sortBy(_realization_key)))
            }
          }
      }
    }
  }

  private def _surface_error(surface: ComponentSurface, path: String): Option[LogicalUiError] = {
    if (surface == null)
      Some(LogicalUiError("LUI43_COMPONENT_SURFACE_INVALID", path, "Component surface is required"))
    else
      _coordinate_error(surface.component, s"$path.component").orElse {
        val exports = Option(surface.exportIds).getOrElse(Vector.empty)
        if (exports.isEmpty)
          Some(LogicalUiError("LUI43_COMPONENT_EXPORT_MISSING", s"$path.exportIds", "at least one explicit public export ID is required"))
        else if (exports.exists(value => _required_text_error(value, s"$path.exportIds").isDefined))
          Some(LogicalUiError("LUI43_COMPONENT_EXPORT_INVALID", s"$path.exportIds", "export ID must be nonempty and trimmed"))
        else if (exports.distinct.size != exports.size)
          Some(LogicalUiError("LUI43_COMPONENT_EXPORT_DUPLICATE", s"$path.exportIds", "export ID must be unique within its Component surface"))
        else
          None
      }
  }

  private def _binding_error(
    binding: ComponentBinding,
    surfaces: Map[String, ComponentSurface],
    path: String
  ): Option[LogicalUiError] = {
    if (binding == null)
      Some(LogicalUiError("LUI43_COMPONENT_BINDING_INVALID", path, "Component binding is required"))
    else
      _coordinate_error(binding.component, s"$path.component").orElse {
        _required_text_error(binding.exportId, s"$path.exportId")
      }.orElse {
        surfaces.get(binding.component.canonicalIdentity) match {
          case None => Some(LogicalUiError("LUI43_COMPONENT_SURFACE_CLOSED", s"$path.component", "Component binding must use an exact declared public surface identity"))
          case Some(surface) if !surface.exportIds.contains(binding.exportId) =>
            Some(LogicalUiError("LUI43_COMPONENT_EXPORT_CLOSED", s"$path.exportId", "export ID is not declared by the exact public Component surface"))
          case Some(_) => None
        }
      }
  }

  private def _reference_error(
    reference: UseCaseReference,
    expected: UseCaseLayer,
    path: String
  ): Option[LogicalUiError] = {
    if (reference == null)
      Some(LogicalUiError("LUI43_USE_CASE_IDENTITY_MISSING", path, "UseCase identity is required"))
    else if (reference.layer != expected)
      Some(LogicalUiError("LUI43_USE_CASE_LAYER_INVALID", s"$path.layer", s"expected ${expected.id} layer"))
    else
      _required_text_error(reference.id, s"$path.id")
  }

  private def _coordinate_error(value: ComponentCoordinate, path: String): Option[LogicalUiError] = {
    if (value == null)
      Some(LogicalUiError("LUI43_COMPONENT_COORDINATE_INVALID", path, "Component coordinate is required"))
    else
      _required_text_error(value.namespace, s"$path.namespace").orElse {
        _required_text_error(value.id, s"$path.id")
      }.orElse {
        _required_text_error(value.version, s"$path.version")
      }.orElse {
        if (value.namespace.split("\\.", -1).exists(segment => !_component_segment_pattern.pattern.matcher(segment).matches))
          Some(LogicalUiError("LUI43_COMPONENT_COORDINATE_INVALID", s"$path.namespace", "namespace must be dot-separated canonical segments"))
        else if (!_component_segment_pattern.pattern.matcher(value.id).matches)
          Some(LogicalUiError("LUI43_COMPONENT_COORDINATE_INVALID", s"$path.id", "id must be one canonical Component segment"))
        else
          None
      }
  }

  private def _required_text_error(value: String, path: String): Option[LogicalUiError] =
    if (value == null || value.isEmpty || value != value.trim)
      Some(LogicalUiError("LUI43_INPUT_TEXT_INVALID", path, "value must be nonempty and trimmed"))
    else
      None

  private def _acceptance_error(candidate: LogicalUiCandidate, decision: AcceptanceDecision): Option[LogicalUiError] = {
    if (decision == null)
      Some(LogicalUiError("LUI43_ACCEPTANCE_DECISION_MISSING", "decision", "an explicit acceptance decision is required"))
    else
      _required_text_error(decision.decisionId, "decision.decisionId").orElse {
        _required_text_error(decision.candidateIdentity, "decision.candidateIdentity")
      }.orElse {
        if (decision.candidateIdentity != candidate.identity)
          Some(LogicalUiError("LUI43_ACCEPTANCE_CANDIDATE_MISMATCH", "decision.candidateIdentity", "acceptance decision must bind exactly the supplied candidate identity"))
        else
          None
      }
  }

  private def _binding_key(value: ComponentBinding): (String, String, String, String) =
    (value.component.namespace, value.component.id, value.component.version, value.exportId)

  private def _realization_key(value: UseCaseRealization): String =
    s"${value.source.layer.id}:${value.source.id}->${value.target.layer.id}:${value.target.id}"

  private def _canonical_candidate_document(candidate: LogicalUiCandidate): String =
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

  private def _canonical_logical_content(input: CandidateInput): String =
    _json_object(
      Vector(
        "schema" -> _json_string(_schema),
        "version" -> _version.toString
      ) ++ _logical_content_fields(input)
    )

  private def _logical_content_fields(input: CandidateInput): Vector[(String, String)] =
    Vector(
      "componentSurfaces" -> _json_array(input.componentSurfaces.map(_component_surface_json)),
      "componentBindings" -> _json_array(input.componentBindings.map(_component_binding_json)),
      "useCases" -> _use_cases_json(input.useCases)
    )

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

  private def _input_identity(content: String): String =
    _identity(_json_object(Vector(
      "schema" -> _json_string(_schema),
      "version" -> _version.toString,
      "kind" -> _json_string(_consumed_input_kind),
      "logicalContent" -> _json_string(content)
    )))

  private def _decision_identity(value: AcceptanceDecision): String =
    _identity(_json_object(Vector(
      "schema" -> _json_string(_schema),
      "version" -> _version.toString,
      "kind" -> _json_string(_acceptance_decision_kind),
      "decisionId" -> _json_string(value.decisionId),
      "candidateIdentity" -> _json_string(value.candidateIdentity)
    )))

  private def _accepted_identity(candidate: LogicalUiCandidate, decision: AcceptanceDecision): String =
    _identity(_json_object(Vector(
      "schema" -> _json_string(_schema),
      "version" -> _version.toString,
      "kind" -> _json_string(_accepted_kind),
      "candidateIdentity" -> _json_string(candidate.identity),
      "inputIdentity" -> _json_string(candidate.inputIdentity),
      "decisionIdentity" -> _json_string(decision.decisionIdentity)
    )))

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
    s""""$escaped""""
  }

  private def _identity(value: String): String =
    "sha256:" + MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)).map(byte => f"${byte & 0xff}%02x").mkString
}
