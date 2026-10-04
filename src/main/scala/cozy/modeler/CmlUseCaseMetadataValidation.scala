package cozy.modeler

import CmlSemanticFoundation._
import CmlDynamicMetadata.{DeclaredExpression, ModelReference}
import CmlUseCaseMetadata._

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlUseCaseMetadataValidation {
  private val _domain_kinds = Set("entity", "value", "aggregate", "trait", "powertype", "powertype-kind")
  private val _collaborator_kinds = Set("actor", "use-case", "component", "service", "external-system", "query", "view")

  def validate(catalog: Catalog, actors: Vector[ActorProjection], useCases: Vector[UseCaseProjection]): Either[Vector[UseCaseDiagnostic], Unit] = {
    val diagnostics = if (catalog == null) Vector(_invalid_shape("catalog")) else {
      _projections(catalog, actors, "actors", "actor", (value: ActorProjection) => value.element,
        (value: ActorProjection, path: String) => _actor(catalog, value, path)) ++
        _projections(catalog, useCases, "useCases", "use-case", (value: UseCaseProjection) => value.element,
          (value: UseCaseProjection, path: String) => _use_case(catalog, value, path))
    }
    if (diagnostics.nonEmpty) Left(diagnostics) else Right(())
  }

  private def _actor(catalog: Catalog, value: ActorProjection, path: String): Vector[UseCaseDiagnostic] = {
    _presence(value.actorKind, path + ".actorKind", _literal) ++
      _presence(value.summary, path + ".summary", _literal) ++
      _presence(value.description, path + ".description", _literal) ++
      _references(catalog, value.useCases, path + ".useCases", Set("use-case")) ++
      _references(catalog, value.workflows, path + ".workflows", Set("workflow")) ++
      _references(catalog, value.operations, path + ".operations", Set("operation", "command")) ++
      _references(catalog, value.events, path + ".events", Set("event")) ++
      _references(catalog, value.domainElements, path + ".domainElements", _domain_kinds)
  }

  private def _use_case(catalog: Catalog, value: UseCaseProjection, path: String): Vector[UseCaseDiagnostic] = {
    _presence(value.declaredId, path + ".declaredId", _literal) ++
      _presence(value.summary, path + ".summary", _literal) ++
      _presence(value.description, path + ".description", _literal) ++
      _presence(value.goal, path + ".goal", _literal) ++
      _presence(value.trigger, path + ".trigger", (expression: DeclaredExpression, coordinate: String) => _expression(catalog, expression, coordinate)) ++
      _expressions(catalog, value.preconditions, path + ".preconditions") ++
      _expressions(catalog, value.postconditions, path + ".postconditions") ++
      _presence(value.priority, path + ".priority", _literal) ++
      _presence(value.lifecycleStatus, path + ".lifecycleStatus", _literal) ++
      _presence(value.actorReferences, path + ".actorReferences", (values: Vector[ActorParticipation], coordinate: String) =>
        _items(values, coordinate, (participation: ActorParticipation, itempath: String) => _participation(catalog, participation, itempath))) ++
      _presence(value.flows, path + ".flows", (values: Vector[UseCaseFlow], coordinate: String) =>
        _items(values, coordinate, (flow: UseCaseFlow, itempath: String) => _flow(catalog, flow, itempath))) ++
      _references(catalog, value.domainElements, path + ".domainElements", _domain_kinds) ++
      _references(catalog, value.operations, path + ".operations", Set("operation", "command")) ++
      _references(catalog, value.events, path + ".events", Set("event")) ++
      _references(catalog, value.collaborators, path + ".collaborators", _collaborator_kinds) ++
      _references(catalog, value.realizingWorkflows, path + ".realizingWorkflows", Set("workflow"))
  }

  private def _participation(catalog: Catalog, value: ActorParticipation, path: String): Vector[UseCaseDiagnostic] = {
    if (value == null) Vector(_invalid_shape(path)) else {
      (if (value.role == Actor || value.role == Primary || value.role == Secondary || value.role == Supporting || value.role == Stakeholder) {
        Vector.empty
      } else Vector(_invalid_shape(path + ".role"))) ++ _reference(catalog, value.target, path + ".target", Set("actor"))
    }
  }

  private def _flow(catalog: Catalog, value: UseCaseFlow, path: String): Vector[UseCaseDiagnostic] = {
    if (value == null) Vector(_invalid_shape(path)) else {
      (if (value.kind == MainFlow || value.kind == AlternateFlow || value.kind == ExceptionFlow) Vector.empty
        else Vector(_invalid_shape(path + ".kind"))) ++
        _presence(value.name, path + ".name", _literal) ++
        _presence(value.summary, path + ".summary", _literal) ++
        _presence(value.description, path + ".description", _literal) ++
        _presence(value.condition, path + ".condition", (expression: DeclaredExpression, coordinate: String) => _expression(catalog, expression, coordinate)) ++
        _expressions(catalog, value.steps, path + ".steps") ++
        _expressions(catalog, value.alternates, path + ".alternates") ++
        _expressions(catalog, value.exceptions, path + ".exceptions")
    }
  }

  private def _projections[A](catalog: Catalog, values: Vector[A], path: String, kind: String,
    record: A => ElementRecord, payload: (A, String) => Vector[UseCaseDiagnostic]): Vector[UseCaseDiagnostic] = {
    if (values == null) Vector(_invalid_shape(path)) else {
      val bindings = values.zipWithIndex.flatMap { case (value, index) =>
        val itempath = s"$path[$index]"
        if (value == null) Vector(_invalid_shape(itempath)) else {
          _core_binding(catalog, record(value), kind, itempath + ".element") ++ payload(value, itempath)
        }
      }
      val admitted = catalog.elements.filter(_.kind == kind)
      val projected = values.filter(_ != null).map(record)
      val corecounts = admitted.groupBy(identity).map { case (value, occurrences) => value -> occurrences.size }
      val projectioncounts = projected.groupBy(identity).map { case (value, occurrences) => value -> occurrences.size }
      val missing = admitted.distinct.zipWithIndex.flatMap { case (value, index) =>
        if (projectioncounts.getOrElse(value, 0) < corecounts(value)) Vector(_diagnostic(
          MissingProjection, s"$path[$index]", "An admitted Use Case or Actor record lacks its required projection occurrence."
        )) else Vector.empty
      }
      val duplicate = projected.distinct.zipWithIndex.flatMap { case (value, index) =>
        if (corecounts.contains(value) && projectioncounts(value) > corecounts(value)) Vector(_diagnostic(
          DuplicateProjection, s"$path[$index]", "A Use Case or Actor record has excess projection occurrences."
        )) else Vector.empty
      }
      bindings ++ missing ++ duplicate
    }
  }

  private def _core_binding(catalog: Catalog, record: ElementRecord, kind: String, path: String): Vector[UseCaseDiagnostic] = {
    if (record == null) Vector(_invalid_shape(path))
    else if (record.kind != kind || !catalog.elements.contains(record)) Vector(_diagnostic(
      InvalidCoreBinding, path, "The Use Case projection does not bind exactly to an admitted core record."
    )) else Vector.empty
  }

  private def _presence[A](value: Presence[A], path: String, payload: (A, String) => Vector[UseCaseDiagnostic]): Vector[UseCaseDiagnostic] = {
    value match {
      case Present(present) => payload(present, path + ".value")
      case absence: Absent => _absence(absence, path)
      case _ => Vector(_invalid_shape(path))
    }
  }

  private def _items[A](values: Vector[A], path: String, payload: (A, String) => Vector[UseCaseDiagnostic]): Vector[UseCaseDiagnostic] = {
    if (values == null) Vector(_invalid_shape(path)) else {
      values.zipWithIndex.flatMap { case (value, index) => payload(value, s"$path[$index]") }
    }
  }

  private def _references(catalog: Catalog, value: Presence[Vector[ModelReference]], path: String, kinds: Set[String]): Vector[UseCaseDiagnostic] = {
    _presence(value, path, (references: Vector[ModelReference], coordinate: String) =>
      _items(references, coordinate, (reference: ModelReference, itempath: String) => _reference(catalog, reference, itempath, kinds)))
  }

  private[modeler] def _reference_diagnostic(catalog: Catalog, reference: ModelReference, path: String): Option[UseCaseDiagnostic] = {
    if (catalog == null || reference == null || reference.identity == null) Some(_invalid_shape(path))
    else if (!_valid_text(reference.identity.modelId) || !_valid_text(reference.identity.elementId)) Some(_invalid_semantics(path + ".identity"))
    else reference.boundary match {
      case External => None
      case Local => catalog.element(reference.identity) match {
        case Right(_) => None
        case _ => Some(_invalid_semantics(path))
      }
      case _ => Some(_invalid_shape(path + ".boundary"))
    }
  }

  private def _reference(catalog: Catalog, value: ModelReference, path: String, kinds: Set[String]): Vector[UseCaseDiagnostic] = {
    _reference_diagnostic(catalog, value, path) match {
      case Some(diagnostic) => Vector(diagnostic)
      case None if value.boundary == Local && !catalog.element(value.identity).right.toOption.exists(record => kinds(record.kind)) =>
        Vector(_invalid_semantics(path))
      case None => Vector.empty
    }
  }

  private def _expressions(catalog: Catalog, value: Presence[Vector[DeclaredExpression]], path: String): Vector[UseCaseDiagnostic] = {
    _presence(value, path, (expressions: Vector[DeclaredExpression], coordinate: String) =>
      _items(expressions, coordinate, (expression: DeclaredExpression, itempath: String) => _expression(catalog, expression, itempath)))
  }

  private def _expression(catalog: Catalog, value: DeclaredExpression, path: String): Vector[UseCaseDiagnostic] = {
    if (value == null) Vector(_invalid_shape(path)) else {
      _literal(value.text, path + ".text") ++
        _presence(value.references, path + ".references", (references: Vector[ModelReference], coordinate: String) =>
          _items(references, coordinate, (reference: ModelReference, itempath: String) => _reference_diagnostic(catalog, reference, itempath).toVector))
    }
  }

  private def _literal(value: String, path: String): Vector[UseCaseDiagnostic] = {
    if (value == null) Vector(_invalid_shape(path)) else Vector.empty
  }

  private def _absence(value: Absent, path: String): Vector[UseCaseDiagnostic] = {
    if (value.reason != NotDeclared && value.reason != NotRepresented && value.reason != Unsupported && value.reason != NotApplicable) {
      Vector(_invalid_shape(path + ".reason"))
    } else if (!_valid_text(value.detail)) Vector(_invalid_semantics(path + ".detail")) else Vector.empty
  }

  private def _valid_text(value: String): Boolean = {
    value != null && value.nonEmpty && !_whitespace(value.codePointAt(0)) &&
      !_whitespace(value.codePointBefore(value.length)) && !_contains_control(value)
  }

  private def _whitespace(codepoint: Int): Boolean = {
    Character.isWhitespace(codepoint) || Character.isSpaceChar(codepoint)
  }

  private def _contains_control(value: String): Boolean = {
    var offset = 0
    var result = false
    while (offset < value.length && !result) {
      val codepoint = value.codePointAt(offset)
      result = Character.isISOControl(codepoint)
      offset += Character.charCount(codepoint)
    }
    result
  }

  private def _invalid_shape(path: String): UseCaseDiagnostic = {
    _diagnostic(InvalidShape, path, "The Use Case value does not match the required v1 shape.")
  }

  private def _invalid_semantics(path: String): UseCaseDiagnostic = {
    _diagnostic(InvalidUseCaseSemantics, path, "The declared Use Case reference or absence is invalid.")
  }
}
