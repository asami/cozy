package cozy.modeler

import play.api.libs.json.{JsObject, JsValue, Json}

import CmlSemanticFoundation._
import CmlDynamicMetadata._
import CmlDynamicMetadataCodec._

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlDynamicStateMachineCodec {
  def stateMachine(records: Map[JsValue, ElementRecord], value: JsValue, path: String): Result[StateMachineProjection] = {
    _projection(value, Vector("element", "owner", "affectedElements", "states", "transitions", "workflows"), path).flatMap { objectvalue =>
      for {
        record <- _record_field(records, objectvalue, path)
        owner <- _presence_field(objectvalue, "owner", path, _reference)
        affected <- _presence_field(objectvalue, "affectedElements", path, _references)
        states <- _presence_field(objectvalue, "states", path, _references)
        transitions <- _presence_field(objectvalue, "transitions", path, _references)
        workflows <- _presence_field(objectvalue, "workflows", path, _references)
      } yield StateMachineProjection(record, owner, affected, states, transitions, workflows)
    }
  }

  def state(records: Map[JsValue, ElementRecord], value: JsValue, path: String): Result[StateProjection] = {
    _projection(value, Vector("element", "stateMachine", "entryActions", "exitActions"), path).flatMap { objectvalue =>
      for {
        record <- _record_field(records, objectvalue, path)
        machine <- _presence_field(objectvalue, "stateMachine", path, _reference)
        entry <- _presence_field(objectvalue, "entryActions", path, _expressions)
        exit <- _presence_field(objectvalue, "exitActions", path, _expressions)
      } yield StateProjection(record, machine, entry, exit)
    }
  }

  def transition(records: Map[JsValue, ElementRecord], value: JsValue, path: String): Result[TransitionProjection] = {
    _projection(value, Vector("element", "stateMachine", "fromState", "toState", "triggers", "guard", "actions",
      "activities", "workflows", "affectedElements", "stateEffects"), path).flatMap { objectvalue =>
      for {
        record <- _record_field(records, objectvalue, path)
        machine <- _presence_field(objectvalue, "stateMachine", path, _reference)
        from <- _presence_field(objectvalue, "fromState", path, _reference)
        to <- _presence_field(objectvalue, "toState", path, _reference)
        triggers <- _presence_field(objectvalue, "triggers", path, _expressions)
        guard <- _presence_field(objectvalue, "guard", path, _expression)
        actions <- _presence_field(objectvalue, "actions", path, _expressions)
        activities <- _presence_field(objectvalue, "activities", path, _references)
        workflows <- _presence_field(objectvalue, "workflows", path, _references)
        affected <- _presence_field(objectvalue, "affectedElements", path, _references)
        effects <- _presence_field(objectvalue, "stateEffects", path, _references)
      } yield TransitionProjection(record, machine, from, to, triggers, guard, actions, activities,
        workflows, affected, effects)
    }
  }

  def stateMachineJson(records: Map[ElementRecord, JsValue], value: StateMachineProjection): JsObject = {
    Json.obj(
      "element" -> records(value.element),
      "owner" -> _presence_json(value.owner, _reference_json),
      "affectedElements" -> _presence_json(value.affectedElements, _references_json),
      "states" -> _presence_json(value.states, _references_json),
      "transitions" -> _presence_json(value.transitions, _references_json),
      "workflows" -> _presence_json(value.workflows, _references_json)
    )
  }

  def stateJson(records: Map[ElementRecord, JsValue], value: StateProjection): JsObject = {
    Json.obj(
      "element" -> records(value.element),
      "stateMachine" -> _presence_json(value.stateMachine, _reference_json),
      "entryActions" -> _presence_json(value.entryActions, _expressions_json),
      "exitActions" -> _presence_json(value.exitActions, _expressions_json)
    )
  }

  def transitionJson(records: Map[ElementRecord, JsValue], value: TransitionProjection): JsObject = {
    Json.obj(
      "element" -> records(value.element),
      "stateMachine" -> _presence_json(value.stateMachine, _reference_json),
      "fromState" -> _presence_json(value.fromState, _reference_json),
      "toState" -> _presence_json(value.toState, _reference_json),
      "triggers" -> _presence_json(value.triggers, _expressions_json),
      "guard" -> _presence_json(value.guard, _expression_json),
      "actions" -> _presence_json(value.actions, _expressions_json),
      "activities" -> _presence_json(value.activities, _references_json),
      "workflows" -> _presence_json(value.workflows, _references_json),
      "affectedElements" -> _presence_json(value.affectedElements, _references_json),
      "stateEffects" -> _presence_json(value.stateEffects, _references_json)
    )
  }
}
