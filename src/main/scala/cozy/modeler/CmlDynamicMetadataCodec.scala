package cozy.modeler

import play.api.libs.json.{JsArray, JsObject, JsString, JsValue, Json}

import CmlSemanticFoundation._
import CmlDynamicMetadata._

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlDynamicMetadataCodec {
  private[modeler] type Result[A] = Either[DynamicDiagnostic, A]
  private val _path = "extensions.cozy.cml.dynamic"

  def dynamicJson(
    envelope: CmlSemanticMetadata.Envelope,
    workflows: Vector[WorkflowProjection],
    activities: Vector[ActivityProjection],
    flows: Vector[FlowProjection],
    stateMachines: Vector[StateMachineProjection],
    states: Vector[StateProjection],
    transitions: Vector[TransitionProjection],
    causalLinks: Vector[CausalLinkProjection]
  ): JsObject = {
    val records = _record_pairs(envelope).map { case (json, record) => record -> json }.toMap
    Json.obj(
      "schemaVersion" -> schemaVersion,
      "workflows" -> JsArray(workflows.map(value => CmlDynamicWorkflowCodec.workflowJson(records, value))),
      "activities" -> JsArray(activities.map(value => CmlDynamicWorkflowCodec.activityJson(records, value))),
      "flows" -> JsArray(flows.map(value => CmlDynamicWorkflowCodec.flowJson(records, value))),
      "stateMachines" -> JsArray(stateMachines.map(value => CmlDynamicStateMachineCodec.stateMachineJson(records, value))),
      "states" -> JsArray(states.map(value => CmlDynamicStateMachineCodec.stateJson(records, value))),
      "transitions" -> JsArray(transitions.map(value => CmlDynamicStateMachineCodec.transitionJson(records, value))),
      "causalLinks" -> JsArray(causalLinks.map(value => _causal_json(records, value)))
    )
  }

  def read(value: JsValue): Either[Vector[DynamicDiagnostic], Graph] = {
    CmlSemanticMetadata.read(value) match {
      case Left(diagnostics) => Left(diagnostics.map(_publication_failure))
      case Right(envelope) => envelope.extensions.value.get(namespace) match {
        case Some(child: JsObject) => _read_child(envelope, child) match {
          case Left(diagnostic) => Left(Vector(diagnostic))
          case Right((workflows, activities, flows, machines, states, transitions, links)) =>
            CmlDynamicMetadata.build(envelope.catalog, workflows, activities, flows, machines,
              states, transitions, links, envelope.extensions - namespace)
        }
        case _ => Left(Vector(_invalid_shape(_path)))
      }
    }
  }

  private def _read_child(
    envelope: CmlSemanticMetadata.Envelope,
    value: JsObject
  ): Result[(Vector[WorkflowProjection], Vector[ActivityProjection], Vector[FlowProjection],
    Vector[StateMachineProjection], Vector[StateProjection], Vector[TransitionProjection], Vector[CausalLinkProjection])] = {
    val records = _record_pairs(envelope).toMap
    for {
      _ <- _schema(value)
      _ <- _exact(value, Vector("schemaVersion", "workflows", "activities", "flows", "stateMachines", "states", "transitions", "causalLinks"), _path)
      workflows <- _array_field(value, "workflows", _path, CmlDynamicWorkflowCodec.workflow(records, _, _))
      activities <- _array_field(value, "activities", _path, CmlDynamicWorkflowCodec.activity(records, _, _))
      flows <- _array_field(value, "flows", _path, CmlDynamicWorkflowCodec.flow(records, _, _))
      machines <- _array_field(value, "stateMachines", _path, CmlDynamicStateMachineCodec.stateMachine(records, _, _))
      states <- _array_field(value, "states", _path, CmlDynamicStateMachineCodec.state(records, _, _))
      transitions <- _array_field(value, "transitions", _path, CmlDynamicStateMachineCodec.transition(records, _, _))
      links <- _array_field(value, "causalLinks", _path, _causal(records, _, _))
    } yield (workflows, activities, flows, machines, states, transitions, links)
  }

  private def _record_pairs(envelope: CmlSemanticMetadata.Envelope): Vector[(JsValue, ElementRecord)] = {
    (CmlSemanticMetadata.toJson(envelope) \ "elements").as[Vector[JsValue]].zip(envelope.catalog.elements)
  }

  private def _schema(value: JsObject): Result[Unit] = {
    _field(value, "schemaVersion", _path + ".schemaVersion").flatMap {
      case JsString(version) if version == schemaVersion => Right(())
      case JsString(_) => Left(_diagnostic(UnsupportedSchemaVersion, _path + ".schemaVersion", "The Dynamic schema version is not supported."))
      case _ => Left(_invalid_shape(_path + ".schemaVersion"))
    }
  }

  private def _causal(records: Map[JsValue, ElementRecord], value: JsValue, path: String): Result[CausalLinkProjection] = {
    _projection(value, Vector("element", "kind", "source", "target", "rule", "affectedElements"), path).flatMap { objectvalue =>
      for {
        record <- _record_field(records, objectvalue, path)
        kind <- _field(objectvalue, "kind", path + ".kind").flatMap(_causal_kind(_, path + ".kind"))
        source <- _presence_field(objectvalue, "source", path, _reference)
        target <- _presence_field(objectvalue, "target", path, _reference)
        rule <- _presence_field(objectvalue, "rule", path, _reference)
        affected <- _presence_field(objectvalue, "affectedElements", path, _references)
      } yield CausalLinkProjection(record, kind, source, target, rule, affected)
    }
  }

  private[modeler] def _projection(value: JsValue, fields: Vector[String], path: String): Result[JsObject] = {
    _object(value, path).flatMap(objectvalue => _exact(objectvalue, fields, path).map(_ => objectvalue))
  }

  private[modeler] def _record_field(records: Map[JsValue, ElementRecord], value: JsObject, path: String): Result[ElementRecord] = {
    _field(value, "element", path + ".element").flatMap { json =>
      records.get(json) match {
        case Some(record) => Right(record)
        case None => Left(_diagnostic(InvalidCoreBinding, path + ".element", "The embedded Dynamic record does not exactly match admitted core JSON."))
      }
    }
  }

  private[modeler] def _reference(value: JsValue, path: String): Result[ModelReference] = {
    _projection(value, Vector("identity", "boundary"), path).flatMap { objectvalue =>
      for {
        identityvalue <- _field(objectvalue, "identity", path + ".identity")
        identity <- _identity(identityvalue, path + ".identity")
        boundaryvalue <- _field(objectvalue, "boundary", path + ".boundary")
        boundary <- _boundary(boundaryvalue, path + ".boundary")
      } yield ModelReference(identity, boundary)
    }
  }

  private def _identity(value: JsValue, path: String): Result[ModelElementId] = {
    _projection(value, Vector("modelId", "elementId"), path).flatMap { objectvalue =>
      for {
        model <- _field(objectvalue, "modelId", path + ".modelId").flatMap(_string(_, path + ".modelId"))
        element <- _field(objectvalue, "elementId", path + ".elementId").flatMap(_string(_, path + ".elementId"))
      } yield ModelElementId(model, element)
    }
  }

  private[modeler] def _references(value: JsValue, path: String): Result[Vector[ModelReference]] = {
    _array(value, path, _reference)
  }

  private[modeler] def _expression(value: JsValue, path: String): Result[DeclaredExpression] = {
    _projection(value, Vector("text", "references"), path).flatMap { objectvalue =>
      for {
        text <- _field(objectvalue, "text", path + ".text").flatMap(_string(_, path + ".text"))
        refs <- _presence_field(objectvalue, "references", path, _references)
      } yield DeclaredExpression(text, refs)
    }
  }

  private[modeler] def _expressions(value: JsValue, path: String): Result[Vector[DeclaredExpression]] = {
    _array(value, path, _expression)
  }

  private[modeler] def _presence_field[A](value: JsObject, name: String, path: String, decode: (JsValue, String) => Result[A]): Result[Presence[A]] = {
    _field(value, name, path + "." + name).flatMap(_presence(_, path + "." + name, decode))
  }

  private def _presence[A](value: JsValue, path: String, decode: (JsValue, String) => Result[A]): Result[Presence[A]] = {
    _object(value, path).flatMap { objectvalue =>
      _field(objectvalue, "status", path + ".status").flatMap {
        case JsString("present") =>
          _exact(objectvalue, Vector("status", "value"), path).flatMap { _ =>
            _field(objectvalue, "value", path + ".value").flatMap(decode(_, path + ".value")).map(Present(_))
          }
        case JsString("absent") =>
          _exact(objectvalue, Vector("status", "reason", "detail"), path).flatMap { _ =>
            for {
              reasonvalue <- _field(objectvalue, "reason", path + ".reason")
              reason <- _absence(reasonvalue, path + ".reason")
              detail <- _field(objectvalue, "detail", path + ".detail").flatMap(_string(_, path + ".detail"))
            } yield Absent(reason, detail)
          }
        case _ => Left(_invalid_shape(path + ".status"))
      }
    }
  }

  private[modeler] def _array_field[A](value: JsObject, name: String, path: String, decode: (JsValue, String) => Result[A]): Result[Vector[A]] = {
    _field(value, name, path + "." + name).flatMap(_array(_, path + "." + name, decode))
  }

  private def _array[A](value: JsValue, path: String, decode: (JsValue, String) => Result[A]): Result[Vector[A]] = {
    value match {
      case JsArray(values) => values.zipWithIndex.foldLeft[Result[Vector[A]]](Right(Vector.empty)) {
        case (Right(accumulator), (item, index)) => decode(item, s"$path[$index]").map(accumulator :+ _)
        case (left @ Left(_), _) => left
      }
      case _ => Left(_invalid_shape(path))
    }
  }

  private[modeler] def _activity_kind(value: JsValue, path: String): Result[ActivityKind] = {
    value match {
      case JsString("action") => Right(Action)
      case JsString("branch") => Right(Branch)
      case JsString("merge") => Right(Merge)
      case JsString("fork") => Right(Fork)
      case JsString("join") => Right(Join)
      case JsString("start") => Right(Start)
      case JsString("end") => Right(End)
      case _ => Left(_invalid_shape(path))
    }
  }

  private def _causal_kind(value: JsValue, path: String): Result[CausalKind] = {
    value match {
      case JsString("cause") => Right(Cause)
      case JsString("reaction") => Right(Reaction)
      case _ => Left(_invalid_shape(path))
    }
  }

  private def _boundary(value: JsValue, path: String): Result[ReferenceBoundary] = {
    value match {
      case JsString("local") => Right(Local)
      case JsString("external") => Right(External)
      case _ => Left(_invalid_shape(path))
    }
  }

  private def _absence(value: JsValue, path: String): Result[AbsenceReason] = {
    value match {
      case JsString("not-declared") => Right(NotDeclared)
      case JsString("not-represented") => Right(NotRepresented)
      case JsString("unsupported") => Right(Unsupported)
      case JsString("not-applicable") => Right(NotApplicable)
      case _ => Left(_invalid_shape(path))
    }
  }

  private[modeler] def _string(value: JsValue, path: String): Result[String] = {
    value match {
      case JsString(text) => Right(text)
      case _ => Left(_invalid_shape(path))
    }
  }

  private def _object(value: JsValue, path: String): Result[JsObject] = {
    value match {
      case objectvalue: JsObject => Right(objectvalue)
      case _ => Left(_invalid_shape(path))
    }
  }

  private def _field(value: JsObject, name: String, path: String): Result[JsValue] = {
    value.value.get(name).toRight(_invalid_shape(path))
  }

  private def _exact(value: JsObject, expected: Vector[String], path: String): Result[Unit] = {
    if (value.keys.exists(key => !expected.contains(key))) Left(_invalid_shape(path)) else {
      expected.find(name => !value.value.contains(name)) match {
        case Some(name) => Left(_invalid_shape(path + "." + name))
        case None => Right(())
      }
    }
  }

  private[modeler] def _reference_json(value: ModelReference): JsObject = {
    Json.obj(
      "identity" -> Json.obj("modelId" -> value.identity.modelId, "elementId" -> value.identity.elementId),
      "boundary" -> (value.boundary match { case Local => "local"; case External => "external" })
    )
  }

  private[modeler] def _references_json(value: Vector[ModelReference]): JsArray = {
    JsArray(value.map(_reference_json))
  }

  private[modeler] def _expression_json(value: DeclaredExpression): JsObject = {
    Json.obj("text" -> value.text, "references" -> _presence_json(value.references, _references_json))
  }

  private[modeler] def _expressions_json(value: Vector[DeclaredExpression]): JsArray = {
    JsArray(value.map(_expression_json))
  }

  private[modeler] def _presence_json[A](value: Presence[A], render: A => JsValue): JsObject = {
    value match {
      case Present(present) => Json.obj("status" -> "present", "value" -> render(present))
      case Absent(reason, detail) => Json.obj("status" -> "absent", "reason" -> _absence_name(reason), "detail" -> detail)
    }
  }

  private[modeler] def _activity_kind_json(value: ActivityKind): JsString = {
    JsString(value match {
      case Action => "action"
      case Branch => "branch"
      case Merge => "merge"
      case Fork => "fork"
      case Join => "join"
      case Start => "start"
      case End => "end"
    })
  }

  private def _causal_json(records: Map[ElementRecord, JsValue], value: CausalLinkProjection): JsObject = {
    Json.obj(
      "element" -> records(value.element),
      "kind" -> (value.kind match { case Cause => "cause"; case Reaction => "reaction" }),
      "source" -> _presence_json(value.source, _reference_json),
      "target" -> _presence_json(value.target, _reference_json),
      "rule" -> _presence_json(value.rule, _reference_json),
      "affectedElements" -> _presence_json(value.affectedElements, _references_json)
    )
  }

  private def _absence_name(value: AbsenceReason): String = {
    value match {
      case NotDeclared => "not-declared"
      case NotRepresented => "not-represented"
      case Unsupported => "unsupported"
      case NotApplicable => "not-applicable"
    }
  }

  private def _invalid_shape(path: String): DynamicDiagnostic = {
    _diagnostic(InvalidShape, path, "The Dynamic value does not match the required v1 shape.")
  }
}
