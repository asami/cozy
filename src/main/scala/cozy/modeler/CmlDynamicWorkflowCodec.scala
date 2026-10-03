package cozy.modeler

import play.api.libs.json.{JsObject, JsString, JsValue, Json}

import CmlSemanticFoundation._
import CmlDynamicMetadata._
import CmlDynamicMetadataCodec._

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlDynamicWorkflowCodec {
  def workflow(records: Map[JsValue, ElementRecord], value: JsValue, path: String): Result[WorkflowProjection] = {
    _projection(value, Vector("element", "purpose", "activities", "flows", "participants", "affectedElements",
      "stateMachines", "operations", "events", "rules", "stateEffects"), path).flatMap { objectvalue =>
      for {
        record <- _record_field(records, objectvalue, path)
        purpose <- _presence_field(objectvalue, "purpose", path, _string)
        activities <- _presence_field(objectvalue, "activities", path, _references)
        flows <- _presence_field(objectvalue, "flows", path, _references)
        participants <- _presence_field(objectvalue, "participants", path, _references)
        affected <- _presence_field(objectvalue, "affectedElements", path, _references)
        machines <- _presence_field(objectvalue, "stateMachines", path, _references)
        operations <- _presence_field(objectvalue, "operations", path, _references)
        events <- _presence_field(objectvalue, "events", path, _references)
        rules <- _presence_field(objectvalue, "rules", path, _references)
        effects <- _presence_field(objectvalue, "stateEffects", path, _references)
      } yield WorkflowProjection(record, purpose, activities, flows, participants, affected, machines,
        operations, events, rules, effects)
    }
  }

  def activity(records: Map[JsValue, ElementRecord], value: JsValue, path: String): Result[ActivityProjection] = {
    _projection(value, Vector("element", "workflow", "activityKind", "participants", "affectedElements",
      "operations", "events", "rules", "stateEffects"), path).flatMap { objectvalue =>
      for {
        record <- _record_field(records, objectvalue, path)
        workflow <- _presence_field(objectvalue, "workflow", path, _reference)
        kind <- _presence_field(objectvalue, "activityKind", path, _activity_kind)
        participants <- _presence_field(objectvalue, "participants", path, _references)
        affected <- _presence_field(objectvalue, "affectedElements", path, _references)
        operations <- _presence_field(objectvalue, "operations", path, _references)
        events <- _presence_field(objectvalue, "events", path, _references)
        rules <- _presence_field(objectvalue, "rules", path, _references)
        effects <- _presence_field(objectvalue, "stateEffects", path, _references)
      } yield ActivityProjection(record, workflow, kind, participants, affected, operations, events, rules, effects)
    }
  }

  def flow(records: Map[JsValue, ElementRecord], value: JsValue, path: String): Result[FlowProjection] = {
    _projection(value, Vector("element", "workflow", "source", "target", "guard"), path).flatMap { objectvalue =>
      for {
        record <- _record_field(records, objectvalue, path)
        workflow <- _presence_field(objectvalue, "workflow", path, _reference)
        source <- _presence_field(objectvalue, "source", path, _reference)
        target <- _presence_field(objectvalue, "target", path, _reference)
        guard <- _presence_field(objectvalue, "guard", path, _expression)
      } yield FlowProjection(record, workflow, source, target, guard)
    }
  }

  def workflowJson(records: Map[ElementRecord, JsValue], value: WorkflowProjection): JsObject = {
    Json.obj(
      "element" -> records(value.element),
      "purpose" -> _presence_json(value.purpose, JsString),
      "activities" -> _presence_json(value.activities, _references_json),
      "flows" -> _presence_json(value.flows, _references_json),
      "participants" -> _presence_json(value.participants, _references_json),
      "affectedElements" -> _presence_json(value.affectedElements, _references_json),
      "stateMachines" -> _presence_json(value.stateMachines, _references_json),
      "operations" -> _presence_json(value.operations, _references_json),
      "events" -> _presence_json(value.events, _references_json),
      "rules" -> _presence_json(value.rules, _references_json),
      "stateEffects" -> _presence_json(value.stateEffects, _references_json)
    )
  }

  def activityJson(records: Map[ElementRecord, JsValue], value: ActivityProjection): JsObject = {
    Json.obj(
      "element" -> records(value.element),
      "workflow" -> _presence_json(value.workflow, _reference_json),
      "activityKind" -> _presence_json(value.activityKind, _activity_kind_json),
      "participants" -> _presence_json(value.participants, _references_json),
      "affectedElements" -> _presence_json(value.affectedElements, _references_json),
      "operations" -> _presence_json(value.operations, _references_json),
      "events" -> _presence_json(value.events, _references_json),
      "rules" -> _presence_json(value.rules, _references_json),
      "stateEffects" -> _presence_json(value.stateEffects, _references_json)
    )
  }

  def flowJson(records: Map[ElementRecord, JsValue], value: FlowProjection): JsObject = {
    Json.obj(
      "element" -> records(value.element),
      "workflow" -> _presence_json(value.workflow, _reference_json),
      "source" -> _presence_json(value.source, _reference_json),
      "target" -> _presence_json(value.target, _reference_json),
      "guard" -> _presence_json(value.guard, _expression_json)
    )
  }
}
