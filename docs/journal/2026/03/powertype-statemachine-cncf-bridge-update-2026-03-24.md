# Powertype/StateMachine Bridge Update

status=implemented
published_at=2026-03-24
owner=cozy

## Summary

Implemented state machine metadata bridging from Cozy modeler to CNCF runtime contract.

This update extends component metadata generation with:

- `stateMachineDefinitions` emission
- state and event extraction from CML state machine definitions
- integration-compatible output for generated `DomainComponent`

## Main Changes

### 1. Modeler component-core metadata

File:
- `/Users/asami/src/dev2025/cozy/src/main/scala/cozy/modeler/Modeler.scala`

Added generation of:

- `MComponent.Core.stateMachineDefinitions`

Definition payload:

- `name`
- `states` (deduplicated, declaration order preserved)
- `events` (declared + transition-referenced, deduplicated)

### 2. Generation expectation tests

File:
- `/Users/asami/src/dev2025/cozy/src/test/scala/cozy/modeler/ModelerGenerationSpec.scala`

Updated assertions for generated `DomainComponent.scala`:

- `stateMachineDefinitions` method exists
- state machine definition record is emitted with expected `name/states/events`

## Validation

Executed:

- `sbt --batch "testOnly cozy.modeler.ModelerGenerationSpec"`

Result:

- pass (30/30)

## Notes

This change is additive and does not alter existing transition rule generation (`stateMachineTransitionRules`).
