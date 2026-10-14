package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import play.api.libs.json._
import CmlCapabilityJsonPart._
import CmlCapabilityReferenceJsonPart._
import CmlCapabilityDeclarationJsonPart._component_id
import CmlCapabilitySource._
import CmlCapabilityGenerationContext._
import CmlCapabilityValidationScope._
import CmlCapabilityProductionRequest._
import CmlSemanticFoundation.{ModelElementId, TermId, RelationId, ProfileId}
import org.goldenport.cncf.component.identity.ComponentId

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlCapabilityProductionRequestJsonPart {
  def decode(text: String, requestPath: Path): Either[Vector[Issue], ProductionRequest] = {
    val location = Option(requestPath).map(_.toString).getOrElse("request")
    val context = Context(SourceScope("cozy.capability.production.request", location), Vector.empty, None, "")
    val result = if (text == null || text.getBytes(StandardCharsets.UTF_8).length > REQUEST_LIMIT)
      _failure[ProductionRequest](context, "Request JSON exceeds the 1MiB bound or is missing.")
    else _json(text, context).flatMap(_request(_, context))
    result.left.map(_.map(x => Issue(IssueKind.Malformed, location + ":" + x.fieldPath, None, Vector.empty, x.message)))
  }

  private def _one(value: JsValue, context: Context): Result[Int] = value match {
    case JsNumber(number) if number == 1 && number.bigDecimal.scale() == 0 => Right(1)
    case _ => _failure(context, "Exactly integer schema version 1 is required.")
  }
  private def _producer(value: JsValue, context: Context): Result[Producer] =
    _record(value, context, Vector(_entry("id", _text), _entry("version", _text)))
      .map(x => Producer(x._get[String]("id"), x._get[String]("version")))
  private def _component(value: JsValue, context: Context): Result[ComponentCoordinate] =
    _record(value, context, Vector(_entry("namespace", _text), _entry("id", _text), _entry("version", _text))).flatMap { fields =>
      _component_id(Json.obj("namespace" -> fields._get[String]("namespace"), "id" -> fields._get[String]("id")), context)
        .map(x => ComponentCoordinate(x, fields._get[String]("version")))
    }
  private def _package(value: JsValue, context: Context): Result[PackageCoordinate] =
    _record(value, context, Vector(_entry("coordinate", _text), _entry("version", _text)))
      .map(x => PackageCoordinate(x._get[String]("coordinate"), x._get[String]("version")))
  private def _source_scope(value: JsValue, context: Context): Result[SourceScope] =
    _record(value, context, Vector(_entry("modelAuthority", _text), _entry("path", _text))).flatMap { x =>
      val path = x._get[String]("path")
      if (_valid_path(path)) Right(SourceScope(x._get[String]("modelAuthority"), path))
      else _failure(context._child("path"), "Canonical project-relative POSIX source path is required.")
    }
  private def _snapshot(value: JsValue, context: Context): Result[SnapshotKey] =
    _record(value, context, Vector(_entry("source", _source_scope), _entry("component", _component_id)))
      .map(x => SnapshotKey(x._get[SourceScope]("source"), x._get[ComponentId]("component")))
  private def _context(value: JsValue, context: Context): Result[GenerationContext] =
    _record(value, context, Vector(_entry("projectionGeneration", _one), _entry("schemaVersion", _one),
      _entry("generatedAbiVersion", _text), _entry("producer", _producer), _entry("component", _component),
      _entry("package", _package), _entry("source", _foreign_source))).map(x =>
      GenerationContext(x._get[Int]("projectionGeneration"), x._get[Int]("schemaVersion"), x._get[String]("generatedAbiVersion"),
        x._get[Producer]("producer"), x._get[ComponentCoordinate]("component"), x._get[PackageCoordinate]("package"), x._get[CapabilitySource]("source")))
  private def _resource(value: JsValue, context: Context): Result[ResourceOutput] =
    _record(value, context, Vector(_entry("snapshot", _snapshot), _entry("filename", _text)))
      .map(x => ResourceOutput(x._get[SnapshotKey]("snapshot"), x._get[String]("filename")))
  private def _output(value: JsValue, context: Context): Result[PackageOutput] =
    _record(value, context, Vector(_entry("component", _component), _entry("package", _package),
      _entry("path", _text), _entry("resources", _array(_resource)))).map(x =>
      PackageOutput(x._get[ComponentCoordinate]("component"), x._get[PackageCoordinate]("package"),
        x._get[String]("path"), x._get[Vector[ResourceOutput]]("resources")))
  private def _scala_output(value: JsValue, context: Context): Result[ScalaOutput] =
    _record(value, context, Vector(_entry("snapshot", _snapshot), _entry("path", _text),
      _entry("packageName", _array(_text)), _entry("objectName", _text))).map(x =>
      ScalaOutput(x._get[SnapshotKey]("snapshot"), x._get[String]("path"), x._get[Vector[String]]("packageName"), x._get[String]("objectName")))

  private val _model_kinds: Vector[(String, ModelKind)] = Vector(ModelKind.DomainConcept, ModelKind.Goal,
    ModelKind.UseCase, ModelKind.Scenario, ModelKind.UseCaseSlice, ModelKind.Component, ModelKind.ComponentCapability,
    ModelKind.ApplicationCapability, ModelKind.Specification, ModelKind.Evidence, ModelKind.Operation,
    ModelKind.Workflow, ModelKind.StateMachine).map(x => x.tag -> x)
  private def _model_fact(value: JsValue, context: Context): Result[ModelFact] =
    _record(value, context, Vector(_entry("identity", _model_id), _entry("kind", _tag(_, _, _model_kinds)),
      _entry("source", _foreign_source))).map(x => ModelFact(x._get[ModelElementId]("identity"), x._get[ModelKind]("kind"), x._get[CapabilitySource]("source")))
  private def _term_fact(value: JsValue, context: Context): Result[TermFact] =
    _record(value, context, Vector(_entry("identity", _term_id), _entry("source", _foreign_source)))
      .map(x => TermFact(x._get[TermId]("identity"), x._get[CapabilitySource]("source")))
  private def _relation_fact(value: JsValue, context: Context): Result[RelationFact] =
    _record(value, context, Vector(_entry("identity", _relation_id), _entry("source", _foreign_source)))
      .map(x => RelationFact(x._get[RelationId]("identity"), x._get[CapabilitySource]("source")))
  private def _profile_fact(value: JsValue, context: Context): Result[ProfileFact] =
    _record(value, context, Vector(_entry("identity", _profile_id), _entry("source", _foreign_source)))
      .map(x => ProfileFact(x._get[ProfileId]("identity"), x._get[CapabilitySource]("source")))
  private def _application_contract(value: JsValue, context: Context): Result[ApplicationContract] =
    _record(value, context, Vector(_entry("identity", _model_id), _entry("contractVersion", _text), _entry("source", _foreign_source)))
      .map(x => ApplicationContract(x._get[ModelElementId]("identity"), x._get[String]("contractVersion"), x._get[CapabilitySource]("source")))
  private def _component_contract(value: JsValue, context: Context): Result[ComponentContract] =
    _record(value, context, Vector(_entry("identity", _model_id), _entry("contractVersion", _text), _entry("source", _foreign_source)))
      .map(x => ComponentContract(x._get[ModelElementId]("identity"), x._get[String]("contractVersion"), x._get[CapabilitySource]("source")))
  private def _target_contract(value: JsValue, context: Context): Result[TargetContract] = {
    val kinds: Vector[(String, CmlCapabilityModel.TargetKind)] = Vector("OPERATION" -> CmlCapabilityModel.Operation,
      "WORKFLOW" -> CmlCapabilityModel.Workflow, "STATE_MACHINE" -> CmlCapabilityModel.StateMachine)
    _record(value, context, Vector(_entry("kind", _tag(_, _, kinds)), _entry("identity", _model_id),
      _entry("component", _component_id), _entry("contractVersion", _text), _entry("generatedAbiVersion", _text),
      _entry("source", _foreign_source))).map(x => TargetContract(x._get[CmlCapabilityModel.TargetKind]("kind"),
      x._get[ModelElementId]("identity"), x._get[ComponentId]("component"), x._get[String]("contractVersion"),
      x._get[String]("generatedAbiVersion"), x._get[CapabilitySource]("source")))
  }
  private def _scope(value: JsValue, context: Context): Result[ScopeInput] =
    _record(value, context, Vector(_entry("scopeId", _text), _entry("source", _foreign_source),
      _entry("modelFacts", _array(_model_fact)), _entry("termFacts", _array(_term_fact)),
      _entry("relationFacts", _array(_relation_fact)), _entry("profileFacts", _array(_profile_fact)),
      _entry("applicationContracts", _array(_application_contract)), _entry("componentContracts", _array(_component_contract)),
      _entry("targetContracts", _array(_target_contract)))).map(x => ScopeInput.Ready(Scope(x._get[String]("scopeId"),
      x._get[CapabilitySource]("source"), x._get[Vector[ModelFact]]("modelFacts"), x._get[Vector[TermFact]]("termFacts"),
      x._get[Vector[RelationFact]]("relationFacts"), x._get[Vector[ProfileFact]]("profileFacts"),
      x._get[Vector[ApplicationContract]]("applicationContracts"), x._get[Vector[ComponentContract]]("componentContracts"),
      x._get[Vector[TargetContract]]("targetContracts"))))
  private def _request(value: JsValue, context: Context): Result[ProductionRequest] =
    _record(value, context, Vector(_entry("schemaVersion", _one), _entry("producer", _producer),
      _entry("sources", _array(_foreign_source)), _entry("scope", _scope), _entry("contexts", _array(_context)),
      _entry("packages", _array(_output)), _entry("scalaOutputs", _array(_scala_output)),
      _entry("applicationOutput", _text))).map(x => ProductionRequest(x._get[Producer]("producer"),
      x._get[Vector[CapabilitySource]]("sources"), x._get[ScopeInput]("scope"), x._get[Vector[GenerationContext]]("contexts"),
      x._get[Vector[PackageOutput]]("packages"), x._get[Vector[ScalaOutput]]("scalaOutputs"), x._get[String]("applicationOutput")))
}
