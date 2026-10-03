package cozy.modeler

import CmlSemanticFoundation._
import CmlClassificationMetadata._

/*
 * @since   Oct. 3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlClassificationMetadataValidation {
  private val _node_kinds = Set("entity", "value", "aggregate", "trait", "powertype", "powertype-kind")

  def validate(
    catalog: Catalog,
    traits: Vector[TraitProjection],
    powertypes: Vector[PowertypeProjection],
    generalizations: Vector[GeneralizationProjection],
    traitRealizations: Vector[TraitRealizationProjection],
    powertypeClassifications: Vector[PowertypeClassificationProjection]
  ): Either[Vector[ClassificationDiagnostic], Unit] = {
    val diagnostics = if (catalog == null) Vector(_invalid_shape("catalog")) else
      _projections(catalog, traits, "traits", "trait", (value: TraitProjection) => value.element,
        (_: TraitProjection, _: String) => Vector.empty) ++
      _projections(catalog, powertypes, "powertypes", "powertype", (value: PowertypeProjection) => value.element,
        (value: PowertypeProjection, path: String) => _members(catalog, value.members, path + ".members")) ++
      _projections(catalog, generalizations, "generalizations", "generalization", (value: GeneralizationProjection) => value.element,
        (value: GeneralizationProjection, path: String) =>
          _reference_presence(catalog, value.subtype, path + ".subtype", _node_kinds) ++
            _reference_presence(catalog, value.supertype, path + ".supertype", _node_kinds)) ++
      _projections(catalog, traitRealizations, "traitRealizations", "trait-realization", (value: TraitRealizationProjection) => value.element,
        (value: TraitRealizationProjection, path: String) =>
          _reference_presence(catalog, value.subject, path + ".subject", _node_kinds) ++
            _reference_presence(catalog, value.traitTarget, path + ".traitTarget", Set("trait"))) ++
      _projections(catalog, powertypeClassifications, "powertypeClassifications", "powertype-classification", (value: PowertypeClassificationProjection) => value.element,
        (value: PowertypeClassificationProjection, path: String) =>
          _reference_presence(catalog, value.subject, path + ".subject", _node_kinds) ++
            _reference_presence(catalog, value.powertype, path + ".powertype", Set("powertype")))
    if (diagnostics.nonEmpty) Left(diagnostics) else Right(())
  }

  private def _projections[A](
    catalog: Catalog,
    values: Vector[A],
    path: String,
    kind: String,
    record: A => ElementRecord,
    payload: (A, String) => Vector[ClassificationDiagnostic]
  ): Vector[ClassificationDiagnostic] =
    if (values == null) Vector(_invalid_shape(path)) else {
      val bindings = values.zipWithIndex.flatMap { case (value, index) =>
        val itempath = s"$path[$index]"
        if (value == null) Vector(_invalid_shape(itempath)) else
          _core_binding(catalog, record(value), kind, itempath + ".element") ++ payload(value, itempath)
      }
      val core = catalog.elements.filter(_.kind == kind).groupBy(identity).map { case (value, occurrences) => value -> occurrences.size }
      val projected = values.filter(_ != null).map(record).groupBy(identity).map { case (value, occurrences) => value -> occurrences.size }
      val missing = catalog.elements.filter(_.kind == kind).distinct.zipWithIndex.flatMap { case (value, index) =>
        if (projected.getOrElse(value, 0) < core(value)) Vector(_diagnostic(
          MissingProjection, s"$path[$index]", "An admitted Classification record lacks its required projection occurrence."
        )) else Vector.empty
      }
      val duplicate = values.filter(_ != null).map(record).distinct.zipWithIndex.flatMap { case (value, index) =>
        if (core.contains(value) && projected(value) > core(value)) Vector(_diagnostic(
          DuplicateProjection, s"$path[$index]", "A Classification record has excess projection occurrences."
        )) else Vector.empty
      }
      bindings ++ missing ++ duplicate
    }

  private def _core_binding(catalog: Catalog, record: ElementRecord, kind: String, path: String): Vector[ClassificationDiagnostic] =
    if (record == null) Vector(_invalid_shape(path))
    else if (record.kind != kind || !catalog.elements.contains(record)) Vector(_invalid_core(path))
    else record.identity match {
      case Present(identity: ModelElementId) => catalog.element(identity) match {
        case Right(admitted) if admitted == record => Vector.empty
        case _ => Vector(_invalid_core(path))
      }
      case _: Absent => Vector.empty
      case _ => Vector(_invalid_core(path))
    }

  private def _members(catalog: Catalog, value: Presence[Vector[ModelReference]], path: String): Vector[ClassificationDiagnostic] =
    value match {
      case Present(members) if members != null => members.zipWithIndex.flatMap { case (member, index) =>
        _reference(catalog, member, s"$path.value[$index]", Set("powertype-kind"))
      }
      case absence: Absent => _absence(absence, path)
      case _ => Vector(_invalid_shape(path))
    }

  private def _reference_presence(catalog: Catalog, value: Presence[ModelReference], path: String, kinds: Set[String]): Vector[ClassificationDiagnostic] =
    value match {
      case Present(reference) => _reference(catalog, reference, path + ".value", kinds)
      case absence: Absent => _absence(absence, path)
      case _ => Vector(_invalid_shape(path))
    }

  private def _reference(catalog: Catalog, value: ModelReference, path: String, kinds: Set[String]): Vector[ClassificationDiagnostic] =
    if (value == null || value.identity == null) Vector(_invalid_shape(path))
    else if (!_valid_text(value.identity.modelId) || !_valid_text(value.identity.elementId)) Vector(_invalid_semantics(path + ".identity"))
    else value.boundary match {
      case External => Vector.empty
      case Local => catalog.element(value.identity) match {
        case Right(record) if kinds(record.kind) => Vector.empty
        case _ => Vector(_invalid_semantics(path))
      }
      case _ => Vector(_invalid_shape(path + ".boundary"))
    }

  private def _absence(value: Absent, path: String): Vector[ClassificationDiagnostic] =
    if (!_valid_reason(value.reason)) Vector(_invalid_shape(path + ".reason"))
    else if (!_valid_text(value.detail)) Vector(_invalid_semantics(path + ".detail"))
    else Vector.empty

  private def _valid_reason(value: AbsenceReason): Boolean =
    value == NotDeclared || value == NotRepresented || value == Unsupported || value == NotApplicable

  private def _valid_text(value: String): Boolean =
    value != null && value.nonEmpty && !_whitespace(value.codePointAt(0)) &&
      !_whitespace(value.codePointBefore(value.length)) && !_contains_control(value)

  private def _whitespace(codepoint: Int): Boolean =
    Character.isWhitespace(codepoint) || Character.isSpaceChar(codepoint)

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

  private def _invalid_shape(path: String): ClassificationDiagnostic =
    _diagnostic(InvalidShape, path, "The Classification value does not match the required v1 shape.")

  private def _invalid_core(path: String): ClassificationDiagnostic =
    _diagnostic(InvalidCoreBinding, path, "The Classification projection does not bind exactly to an admitted core record.")

  private def _invalid_semantics(path: String): ClassificationDiagnostic =
    _diagnostic(InvalidClassificationSemantics, path, "The declared Classification reference or absence is invalid.")
}
