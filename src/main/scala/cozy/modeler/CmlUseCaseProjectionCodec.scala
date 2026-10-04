package cozy.modeler

import play.api.libs.json.{JsObject, JsString, JsValue, Json}

import CmlSemanticFoundation._
import CmlUseCaseMetadata._
import CmlUseCaseMetadataCodec._

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlUseCaseProjectionCodec {
  def actor(records: Map[JsValue, ElementRecord], value: JsValue, path: String): Result[ActorProjection] = {
    for {
      objectvalue <- _projection(value, Vector("element", "actorKind", "summary", "description", "useCases",
        "workflows", "operations", "events", "domainElements"), path)
      record <- _record_field(records, objectvalue, path)
      kind <- _presence_field(objectvalue, "actorKind", path, _string)
      summary <- _presence_field(objectvalue, "summary", path, _string)
      description <- _presence_field(objectvalue, "description", path, _string)
      usecases <- _presence_field(objectvalue, "useCases", path, _references)
      workflows <- _presence_field(objectvalue, "workflows", path, _references)
      operations <- _presence_field(objectvalue, "operations", path, _references)
      events <- _presence_field(objectvalue, "events", path, _references)
      domain <- _presence_field(objectvalue, "domainElements", path, _references)
    } yield ActorProjection(record, kind, summary, description, usecases, workflows, operations, events, domain)
  }

  def useCase(records: Map[JsValue, ElementRecord], value: JsValue, path: String): Result[UseCaseProjection] = {
    for {
      objectvalue <- _projection(value, Vector("element", "declaredId", "summary", "description", "goal", "trigger",
        "preconditions", "postconditions", "priority", "lifecycleStatus", "actorReferences", "flows", "domainElements",
        "operations", "events", "collaborators", "realizingWorkflows"), path)
      record <- _record_field(records, objectvalue, path)
      id <- _presence_field(objectvalue, "declaredId", path, _string)
      summary <- _presence_field(objectvalue, "summary", path, _string)
      description <- _presence_field(objectvalue, "description", path, _string)
      goal <- _presence_field(objectvalue, "goal", path, _string)
      trigger <- _presence_field(objectvalue, "trigger", path, _expression)
      preconditions <- _presence_field(objectvalue, "preconditions", path, _expressions)
      postconditions <- _presence_field(objectvalue, "postconditions", path, _expressions)
      priority <- _presence_field(objectvalue, "priority", path, _string)
      status <- _presence_field(objectvalue, "lifecycleStatus", path, _string)
      actors <- _presence_field(objectvalue, "actorReferences", path, CmlUseCaseFlowCodec.participations)
      flows <- _presence_field(objectvalue, "flows", path, CmlUseCaseFlowCodec.flows)
      domain <- _presence_field(objectvalue, "domainElements", path, _references)
      operations <- _presence_field(objectvalue, "operations", path, _references)
      events <- _presence_field(objectvalue, "events", path, _references)
      collaborators <- _presence_field(objectvalue, "collaborators", path, _references)
      workflows <- _presence_field(objectvalue, "realizingWorkflows", path, _references)
    } yield UseCaseProjection(record, id, summary, description, goal, trigger, preconditions, postconditions,
      priority, status, actors, flows, domain, operations, events, collaborators, workflows)
  }

  def actorJson(records: Map[ElementRecord, JsValue], value: ActorProjection): JsObject = {
    Json.obj("element" -> records(value.element),
      "actorKind" -> _presence_json(value.actorKind, JsString),
      "summary" -> _presence_json(value.summary, JsString),
      "description" -> _presence_json(value.description, JsString),
      "useCases" -> _presence_json(value.useCases, _references_json),
      "workflows" -> _presence_json(value.workflows, _references_json),
      "operations" -> _presence_json(value.operations, _references_json),
      "events" -> _presence_json(value.events, _references_json),
      "domainElements" -> _presence_json(value.domainElements, _references_json))
  }

  def useCaseJson(records: Map[ElementRecord, JsValue], value: UseCaseProjection): JsObject = {
    Json.obj("element" -> records(value.element),
      "declaredId" -> _presence_json(value.declaredId, JsString),
      "summary" -> _presence_json(value.summary, JsString),
      "description" -> _presence_json(value.description, JsString),
      "goal" -> _presence_json(value.goal, JsString),
      "trigger" -> _presence_json(value.trigger, _expression_json),
      "preconditions" -> _presence_json(value.preconditions, _expressions_json),
      "postconditions" -> _presence_json(value.postconditions, _expressions_json),
      "priority" -> _presence_json(value.priority, JsString),
      "lifecycleStatus" -> _presence_json(value.lifecycleStatus, JsString),
      "actorReferences" -> _presence_json(value.actorReferences, CmlUseCaseFlowCodec.participationsJson),
      "flows" -> _presence_json(value.flows, CmlUseCaseFlowCodec.flowsJson),
      "domainElements" -> _presence_json(value.domainElements, _references_json),
      "operations" -> _presence_json(value.operations, _references_json),
      "events" -> _presence_json(value.events, _references_json),
      "collaborators" -> _presence_json(value.collaborators, _references_json),
      "realizingWorkflows" -> _presence_json(value.realizingWorkflows, _references_json))
  }
}
