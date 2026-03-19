# StateMachine Feature Update Log (Cozy)

status=working-draft
published_at=2026-03-20
owner=cozy-modeler

---

## Overview

Implemented feature updates to connect StateMachine CML definitions from Cozy Modeler to the CNCF runtime execution path.  
This log summarizes implementation details, verification results, and known gaps as of 2026-03-20.

---

## Implemented Changes

### 1. StateMachine linkage for Entity generation

- Updated entity generation to include `stateMachines` references as `MStateMachineRef`.
- Added schema handling to map `StateMachine` columns into generated attributes.

Target:
- `src/main/scala/cozy/modeler/Modeler.scala`

### 2. Transition rule generation (CML -> CNCF Rule)

- Generated `MComponent.StateMachineTransitionRule` from each entity's StateMachine definition.
- Added `declarationOrder` to ensure deterministic ordering under the same priority.
- Classified guards (`EventNameGuard`, composite guards, identifier guards, expression guards) and mapped them to `Ref` or `Expression`.
- Mapped triggers to save/update categories.
- Built execution plans as `exit -> transition -> entry`.

Target:
- `src/main/scala/cozy/modeler/Modeler.scala`

### 2.1 SM-05 completion (`Modeler._statemachine`)

- Replaced placeholder implementation with full graph construction:
  - state nodes
  - nested state-machine nodes
  - transition edges
  - event/guard mapping
- Added Cozy-side explicit validation in generation path:
  - missing `on`
  - unknown transition target
  - undeclared event
- Kept deterministic selection support explicit via emitted `declarationOrder`.

### 3. Generated code integration improvements (SimpleModeler)

- Updated generated `build.sbt` CNCF dependency to `0.3.8-SNAPSHOT`.
- Fixed null conversion behavior under Scala 3 `strictEquality`.
- Fixed a bug where `Update.noop` was stringified and stored in DB:
  - Preserve `Update` directive in `toDataStore`
  - Generate `EntityPersistentUpdate` using `toDataStore()`

Related commit:
- simple-modeler: `59e6ad2` (`Fix generated Scala3 update/datastore compatibility`)

---

## Verification

### Modeler generation tests

- `src/test/scala/cozy/modeler/ModelerGenerationSpec.scala`
  - StateMachine heading parsing
  - guardExpression / guardRef emission
  - mixed guard coverage (`guardRef`, expression, composite-like expression text)
  - deterministic declaration-order emission
  - detection of missing `on`, unknown transition target, and undeclared events

### scripted (surrounding regression)

- `scripted cozy/entity-sqlite-crud` : pass  
- `scripted cozy/entity-sqlite-search-memory` : pass  
- `scripted cozy/entity-simpleentity-action` : pass
- `scripted cozy/entity-statemachine-guard-order` : pass

---

## Known Gaps

- StateMachine slot datatype (`Statemachine`) is not yet a first-class generated runtime datatype; the scripted scenario currently adds a local shim for compile-time compatibility.
- Transition rule `priority` is currently emitted as `0` because explicit priority input is not yet exposed by upstream DSL/model. Determinism is ensured by `declarationOrder`.

---

## References

- `docs/journal/2026/03/cozy-statemachine-integration-handoff-2026-03-19.md` (CNCF-side handoff)
- `src/test/resources/modeler/statemachine-cml.dox`
- `src/test/resources/modeler/statemachine-cml-guard-ref.dox`
- `src/test/resources/modeler/statemachine-cml-guard-composite-order.dox`
