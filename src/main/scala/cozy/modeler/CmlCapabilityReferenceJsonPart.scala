package cozy.modeler

import play.api.libs.json._
import CmlSemanticFoundation.{ModelElementId, TermId, RelationId, ProfileId, SemanticTarget, ModelTarget, TermTarget, ReferenceBoundary, Local, External, LocalizedLabel, Presence}
import CmlCapabilitySource._
import CmlCapabilityModel._
import CmlCapabilityJsonPart._

/*
 * @since   Oct. 5, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[modeler] object CmlCapabilityReferenceJsonPart {
  def _model_id(value: JsValue, context: Context): Result[ModelElementId] =
    _record(value, context, Vector(_entry("modelId", _text), _entry("elementId", _text)))
      .map(x => ModelElementId(x._get[String]("modelId"), x._get[String]("elementId")))

  def _definition_id(value: JsValue, context: Context): Result[ModelElementId] =
    _model_id(value, context).flatMap { identity =>
      if (identity.modelId == context.scope.modelAuthority) Right(identity)
      else _failure(context._child("modelId"), "Definition authority must equal the input scope.", InvalidSource)
    }

  def _term_id(value: JsValue, context: Context): Result[TermId] =
    _record(value, context, Vector(_entry("vocabularyId", _text), _entry("termId", _text)))
      .map(x => TermId(x._get[String]("vocabularyId"), x._get[String]("termId")))

  def _relation_id(value: JsValue, context: Context): Result[RelationId] =
    _record(value, context, Vector(_entry("vocabularyId", _text), _entry("relationId", _text)))
      .map(x => RelationId(x._get[String]("vocabularyId"), x._get[String]("relationId")))

  def _profile_id(value: JsValue, context: Context): Result[ProfileId] =
    _record(value, context, Vector(_entry("vocabularyId", _text), _entry("profileId", _text)))
      .map(x => ProfileId(x._get[String]("vocabularyId"), x._get[String]("profileId")))

  def _boundary(value: JsValue, context: Context): Result[ReferenceBoundary] =
    _tag(value, context, Vector("LOCAL" -> Local, "EXTERNAL" -> External))

  def _model_reference(value: JsValue, context: Context): Result[ModelReference] =
    _record(value, context, Vector(_entry("identity", _model_id), _entry("boundary", _boundary)))
      .map(x => ModelReference(x._get[ModelElementId]("identity"), x._get[ReferenceBoundary]("boundary")))

  def _semantic_target(value: JsValue, context: Context): Result[SemanticTarget] = (value \ "kind").toOption match {
    case Some(JsString("MODEL")) =>
      _record(value, context, Vector(_entry("kind", _tag(_, _, Vector("MODEL" -> ()))), _entry("identity", _model_id)))
        .map(x => ModelTarget(x._get[ModelElementId]("identity")))
    case Some(JsString("TERM")) =>
      _record(value, context, Vector(_entry("kind", _tag(_, _, Vector("TERM" -> ()))), _entry("identity", _term_id)))
        .map(x => TermTarget(x._get[TermId]("identity")))
    case _ => _failure(context._child("kind"), "Semantic target requires MODEL or TERM.", UnsupportedSyntax)
  }

  def _label(value: JsValue, context: Context): Result[LocalizedLabel] = {
    def _language_(item: JsValue, location: Context): Result[Option[String]] = item match {
      case JsNull => Right(None)
      case _ => _text(item, location).map(Some(_))
    }
    _record(value, context, Vector(_entry("text", _text), _entry("language", _language_)))
      .map(x => LocalizedLabel(x._get[String]("text"), x._get[Option[String]]("language")))
  }

  def _source(value: JsValue, context: Context): Result[CapabilitySource] =
    _foreign_source(value, context).flatMap { source =>
      val issues = Vector(
        if (source.modelAuthority == context.scope.modelAuthority) None else Some(context._child("modelAuthority")._issue(InvalidSource, "Source authority must equal the input scope.")),
        if (source.path == context.scope.path) None else Some(context._child("path")._issue(InvalidSource, "Source path must equal the input scope."))
      ).flatten
      if (issues.isEmpty) Right(source) else Left(issues)
    }

  def _foreign_source(value: JsValue, context: Context): Result[CapabilitySource] = {
    def _source_text_(item: JsValue, location: Context): Result[String] =
      _text(item, location).left.map(_.map(_.copy(kind = InvalidSource)))
    def _path_(item: JsValue, location: Context): Result[String] = item match {
      case JsString(path) if _valid_path(path) => Right(path)
      case _ => _failure(location, "A canonical project-relative POSIX path is required.", InvalidSource)
    }
    def _line_(item: JsValue, location: Context): Result[Option[Int]] = item match {
      case JsNull => Right(None)
      case JsNumber(number) if number.isWhole && number.isValidInt && number > 0 => Right(Some(number.toInt))
      case _ => _failure(location, "Source line requires null or a positive whole Int.", InvalidSource)
    }
    _record(value, context, Vector(_entry("modelAuthority", _source_text_), _entry("path", _path_),
      _entry("sourceRevision", _source_text_), _entry("line", _line_)))
      .map(x => CapabilitySource(x._get[String]("modelAuthority"), x._get[String]("path"),
        x._get[String]("sourceRevision"), x._get[Option[Int]]("line")))
  }

  def _origin(value: JsValue, context: Context): Result[CapabilityOrigin] = (value \ "kind").toOption match {
    case Some(JsString("DECLARED")) =>
      _record(value, context, Vector(_entry("kind", _tag(_, _, Vector("DECLARED" -> ()))))).map(_ => Declared)
    case Some(JsString("DERIVED")) =>
      def _inputs_(item: JsValue, location: Context): Result[Vector[CapabilitySource]] =
        _array(_foreign_source)(item, location).flatMap { sources =>
          if (sources.nonEmpty) Right(sources) else _failure(location, "Derived inputs must be nonempty.")
        }
      _record(value, context, Vector(_entry("kind", _tag(_, _, Vector("DERIVED" -> ()))),
        _entry("ruleId", _text), _entry("inputs", _inputs_)))
        .map(x => Derived(x._get[String]("ruleId"), x._get[Vector[CapabilitySource]]("inputs")))
    case _ => _failure(context._child("kind"), "Origin requires DECLARED or DERIVED.", UnsupportedSyntax)
  }

  def _model_link(demand: Boolean): Decoder[ModelLink] = (value, context) => {
    val roles: Vector[(String, ModelRole)] = Vector("GOAL" -> Goal, "USE_CASE" -> UseCase, "SCENARIO" -> Scenario, "USE_CASE_SLICE" -> UseCaseSlice) ++
      (if (demand) Vector.empty else Vector("COMPONENT" -> Component, "SPECIFICATION" -> Specification, "EVIDENCE" -> Evidence))
    _record(value, context, Vector(_entry("role", _tag(_, _, roles)), _entry("target", _model_reference),
      _entry("source", _source), _entry("origin", _origin)))
      .map(x => ModelLink(x._get[ModelRole]("role"), x._get[ModelReference]("target"),
        x._get[CapabilitySource]("source"), x._get[CapabilityOrigin]("origin")))
  }

  def _traceability(value: JsValue, context: Context): Result[TraceabilityReference] = {
    val roles: Vector[(String, TraceabilityRole)] = Vector("SPECIFICATION" -> SpecificationTrace, "EVIDENCE" -> EvidenceTrace)
    _record(value, context, Vector(_entry("role", _tag(_, _, roles)), _entry("target", _model_reference),
      _entry("source", _source), _entry("origin", _origin)))
      .map(x => TraceabilityReference(x._get[TraceabilityRole]("role"), x._get[ModelReference]("target"),
        x._get[CapabilitySource]("source"), x._get[CapabilityOrigin]("origin")))
  }

  def _grounding(value: JsValue, context: Context): Result[GroundingReference] =
    _record(value, context, Vector(_entry("target", _semantic_target), _entry("boundary", _boundary),
      _entry("relation", _presence(_relation_id)), _entry("profile", _presence(_profile_id)),
      _entry("context", _presence(_text)), _entry("preferredLabel", _presence(_label)),
      _entry("source", _source), _entry("origin", _origin)))
      .map(x => GroundingReference(x._get[SemanticTarget]("target"), x._get[ReferenceBoundary]("boundary"),
        x._get[Presence[RelationId]]("relation"), x._get[Presence[ProfileId]]("profile"),
        x._get[Presence[String]]("context"), x._get[Presence[LocalizedLabel]]("preferredLabel"),
        x._get[CapabilitySource]("source"), x._get[CapabilityOrigin]("origin")))
}
