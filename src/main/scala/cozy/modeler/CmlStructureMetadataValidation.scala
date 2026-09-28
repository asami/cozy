package cozy.modeler

import CmlSemanticFoundation._
import CmlStructureMetadata._

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlStructureMetadataValidation {
  def validate(
    catalog: Catalog,
    elements: Vector[ElementProjection],
    relations: Vector[RelationProjection]
  ): Either[Vector[StructureDiagnostic], Unit] = {
    val diagnostics = if (catalog == null)
      _catalog_diagnostics(catalog)
    else
      _element_diagnostics(catalog, elements) ++
        _relation_diagnostics(catalog, relations) ++
        _coverage_diagnostics(catalog, elements, relations)
    if (diagnostics.nonEmpty) Left(diagnostics) else Right(())
  }

  private def _catalog_diagnostics(catalog: Catalog): Vector[StructureDiagnostic] =
    if (catalog == null) Vector(_invalid_shape("catalog")) else Vector.empty

  private def _element_diagnostics(catalog: Catalog, values: Vector[ElementProjection]): Vector[StructureDiagnostic] =
    if (values == null) Vector(_invalid_shape("elements"))
    else values.zipWithIndex.flatMap { case (value, index) =>
      if (value == null) Vector(_invalid_shape(s"elements[$index]"))
      else _core_binding(catalog, value.element, _element_kind_name(value.kind), s"elements[$index].element") ++
        _aggregate_boundary(catalog, value.aggregateBoundary, s"elements[$index].aggregateBoundary")
    }

  private def _relation_diagnostics(catalog: Catalog, values: Vector[RelationProjection]): Vector[StructureDiagnostic] =
    if (values == null) Vector(_invalid_shape("relations"))
    else values.zipWithIndex.flatMap { case (value, index) =>
      if (value == null) Vector(_invalid_shape(s"relations[$index]"))
      else _core_binding(catalog, value.element, _relation_kind_name(value.kind), s"relations[$index].element") ++
        _endpoint(catalog, value.sourceEndpoint, s"relations[$index].sourceEndpoint") ++
        _endpoint(catalog, value.targetEndpoint, s"relations[$index].targetEndpoint") ++
        _semantics(catalog, value.semantics, s"relations[$index].semantics")
    }

  private def _coverage_diagnostics(
    catalog: Catalog,
    elements: Vector[ElementProjection],
    relations: Vector[RelationProjection]
  ): Vector[StructureDiagnostic] =
    if (catalog == null || elements == null || relations == null) Vector.empty
    else {
      val core = catalog.elements.filter(record => _supported_kind(record.kind))
      val projected = elements.filter(_ != null).map(_.element) ++ relations.filter(_ != null).map(_.element)
      val missing = core.filter(record => projected.count(_ == record) < core.count(_ == record))
      val duplicate = projected.filter(record => core.count(_ == record) < projected.count(_ == record))
      missing.zipWithIndex.map { case (_, index) => _diagnostic(MissingProjection, s"projections[$index]", "A supported core record has no matching Structure projection.") } ++
        duplicate.zipWithIndex.map { case (_, index) => _diagnostic(DuplicateProjection, s"projections[$index]", "A Structure projection does not match exactly one supported core record.") }
    }

  private def _core_binding(catalog: Catalog, record: ElementRecord, expected: Option[String], path: String): Vector[StructureDiagnostic] =
    (record, expected) match {
      case (value: ElementRecord, Some(expectedkind)) =>
        val identity = value.identity match {
          case Present(identity: ModelElementId) => catalog.element(identity) match {
            case Right(admitted) if admitted == record => Vector.empty
            case _ => Vector(_invalid_core(path))
          }
          case _: Absent => Vector.empty
          case _ => Vector(_invalid_core(s"$path.identity"))
        }
        identity ++ (if (value.kind == expectedkind) Vector.empty else Vector(_invalid_core(s"$path.kind")))
      case _ => Vector(_invalid_core(path))
    }

  private def _endpoint(catalog: Catalog, value: Endpoint, path: String): Vector[StructureDiagnostic] =
    if (value == null) Vector(_invalid_shape(path))
    else _model_reference(catalog, value.target, path + ".target", false) ++
      _text_presence(value.role, path + ".role") ++
      _cardinality(value.cardinality, path + ".cardinality") ++
      _boolean_presence(value.navigable, path + ".navigable")

  private def _semantics(catalog: Catalog, value: RelationSemantics, path: String): Vector[StructureDiagnostic] =
    if (value == null) Vector(_invalid_shape(path))
    else _text_presence(value.ownership, path + ".ownership") ++
      _boolean_presence(value.independentExistence, path + ".independentExistence") ++
      _text_presence(value.createPolicy, path + ".createPolicy") ++
      _text_presence(value.deletePolicy, path + ".deletePolicy") ++
      _boolean_presence(value.reassignment, path + ".reassignment") ++
      _boolean_presence(value.reparenting, path + ".reparenting") ++
      _text_vector_presence(value.lifecyclePropagation, path + ".lifecyclePropagation") ++
      _aggregate_boundary(catalog, value.aggregateBoundary, path + ".aggregateBoundary")

  private def _aggregate_boundary(catalog: Catalog, value: Presence[AggregateBoundary], path: String): Vector[StructureDiagnostic] =
    value match {
      case Present(boundary: AggregateBoundary) => _model_reference(catalog, boundary.aggregate, path + ".aggregate", true) ++ _text_presence(boundary.membership, path + ".membership")
      case absence: Absent => _absence(absence, path)
      case _ => Vector(_invalid_shape(path))
    }

  private def _model_reference(catalog: Catalog, value: Presence[ModelReference], path: String, aggregate: Boolean): Vector[StructureDiagnostic] =
    value match {
      case Present(reference: ModelReference) if _model_element_id(reference.identity) => reference.boundary match {
        case External => Vector.empty
        case Local => catalog.element(reference.identity) match {
          case Right(record) if _static_kind(record.kind) && (!aggregate || record.kind == "aggregate") => Vector.empty
          case _ => Vector(_invalid_semantics(path))
        }
        case _ => Vector(_invalid_shape(path + ".boundary"))
      }
      case absence: Absent => _absence(absence, path)
      case _ => Vector(_invalid_shape(path))
    }

  private def _cardinality(value: Presence[Cardinality], path: String): Vector[StructureDiagnostic] =
    value match {
      case Present(cardinality: Cardinality) if _valid_cardinality(cardinality) => Vector.empty
      case absence: Absent => _absence(absence, path)
      case _ => Vector(_invalid_semantics(path))
    }

  private def _text_presence(value: Presence[String], path: String): Vector[StructureDiagnostic] =
    value match {
      case Present(text) if _valid_text(text) => Vector.empty
      case absence: Absent => _absence(absence, path)
      case _ => Vector(_invalid_semantics(path))
    }

  private def _text_vector_presence(value: Presence[Vector[String]], path: String): Vector[StructureDiagnostic] =
    value match {
      case Present(texts) if texts != null && texts.forall(_valid_text) => Vector.empty
      case absence: Absent => _absence(absence, path)
      case _ => Vector(_invalid_semantics(path))
    }

  private def _boolean_presence(value: Presence[Boolean], path: String): Vector[StructureDiagnostic] = {
    val widened: Presence[Any] = value
    widened match {
      case Present(_: java.lang.Boolean) => Vector.empty
      case absence: Absent => _absence(absence, path)
      case _ => Vector(_invalid_shape(path))
    }
  }

  private def _absence(value: Absent, path: String): Vector[StructureDiagnostic] =
    if (_valid_absence_reason(value.reason) && _valid_text(value.detail)) Vector.empty
    else Vector(_invalid_semantics(path))

  private def _valid_absence_reason(value: AbsenceReason): Boolean =
    value == NotDeclared || value == NotRepresented || value == Unsupported || value == NotApplicable

  private def _model_element_id(value: ModelElementId): Boolean =
    value != null && _valid_text(value.modelId) && _valid_text(value.elementId)

  private def _valid_cardinality(value: Cardinality): Boolean = {
    val upper: Option[Any] = value.upper
    value.lower >= 0 && (upper match {
      case None => true
      case Some(number: java.lang.Integer) => number.intValue >= value.lower
      case _ => false
    })
  }

  private def _valid_text(value: String): Boolean =
    value != null && value.nonEmpty && value == value.trim && !value.exists(_.isControl)

  private def _element_kind_name(value: ElementKind): Option[String] = value match {
    case Entity => Some("entity")
    case Value => Some("value")
    case Aggregate => Some("aggregate")
    case _ => None
  }

  private def _relation_kind_name(value: RelationKind): Option[String] = value match {
    case Composition => Some("composition")
    case Aggregation => Some("aggregation")
    case Association => Some("association")
    case _ => None
  }

  private def _static_kind(value: String): Boolean = value == "entity" || value == "value" || value == "aggregate"
  private def _supported_kind(value: String): Boolean = _static_kind(value) || value == "composition" || value == "aggregation" || value == "association"
  private def _invalid_shape(path: String): StructureDiagnostic = _diagnostic(InvalidShape, path, "The Structure value does not match the required v1 shape.")
  private def _invalid_core(path: String): StructureDiagnostic = _diagnostic(InvalidCoreBinding, path, "The Structure projection does not bind exactly to an admitted core record.")
  private def _invalid_semantics(path: String): StructureDiagnostic = _diagnostic(InvalidRelationSemantics, path, "The declared Structure relation semantics are invalid.")
}
