# Powertype / StateMachine Generation and CNCF Integration

status=proposed
updated_at=2026-03-24
owner=cozy-modeler

---

## 1. Goal

Enable `POWERTYPE` and top-level `STATEMACHINE` to be handled as first-class model outputs in Cozy, and connect StateMachine metadata to CNCF in an additive way.

Target outcomes:

- `modeler-scala-value` accepts value-only CML that contains only `VALUE` / `POWERTYPE` / `STATEMACHINE`.
- Component generation is skipped in value-only mode.
- Generated artifacts include value-model outputs for `POWERTYPE` and `STATEMACHINE` (not silently dropped).
- CNCF can consume StateMachine definitions/metadata without redefining core primitives.

---

## 2. Current Snapshot

### Implemented

- CML parser side accepts `POWERTYPE` and `STATE-MACHINE` top-level aliases.
- Cozy `Modeler` builds `MPowertype` / `MStateMachine` objects from Kaleidox models.
- Entity-bound StateMachine rules are emitted as `stateMachineTransitionRules` and consumed by CNCF `ComponentFactory`.

### Gap

- `simple-modeler` Scala realm generation currently builds files only for:
  - `MEntity`
  - `MComponent`
- `MPowertype` / `MStateMachine` are currently ignored by the realm builder, so no Scala files are generated for them.
- CNCF has runtime hooks for transition rules, but no dedicated metadata hook for top-level StateMachine definitions.

---

## 3. Boundary Rule

- Core StateMachine primitive remains in `org.goldenport.statemachine` (no CNCF primitive redefinition).
- Cozy/CML is the source of truth for model declaration.
- CNCF integration is additive:
  - existing `stateMachineTransitionRules` path must remain backward compatible.
  - new metadata channels can be added without breaking existing generated components.

---

## 4. Design

### 4.1 Generation (Cozy + SimpleModeler)

1. Extend Scala realm build dispatch to include non-entity model objects:
- `MPowertype`
- `MStateMachine`

2. Add dedicated Scala model transformers for these object kinds:
- Powertype transformer
- StateMachine transformer

3. Value-mode policy:
- `modeler-scala-value` keeps `includeComponents = false`.
- If CML contains only `VALUE` / `POWERTYPE` / `STATEMACHINE`, output still contains generated value-model Scala sources (plus `build.sbt`).

4. Packaging defaults:
- Powertype default package: existing model package (e.g. `domain.value`).
- Top-level StateMachine default package: `domain.statemachine` unless explicitly provided later by DSL metadata.

### 4.2 CNCF Integration

1. Keep current runtime activation path:
- `CollectionTransitionRuleProvider` from generated component metadata.

2. Add optional StateMachine definition metadata hook (additive):
- generated component exposes canonical state machine definition metadata (names, states, declared events, transition descriptors).
- CNCF receives it for validation/introspection/projection (no behavior change required in first step).

3. Optional phase-2 runtime use:
- use metadata for consistency checks between reception/subscription and transition rules.

---

## 5. Rollout Plan

### Phase A (Now)

- parser/grammar already aligned for `POWERTYPE` / `STATE-MACHINE`.
- add/keep tests for parse acceptance and no-component value mode.

### Phase B

- implement SimpleModeler dispatch + transformer support for `MPowertype` / `MStateMachine`.
- add scripted/spec tests that assert generated source files exist for both.

### Phase C

- add component metadata hook for top-level StateMachine definitions.
- CNCF consumes metadata for validation/projection (non-breaking).

### Phase D

- optional deep runtime linkage (event route/subscription consistency checks using metadata).

---

## 6. Verification Matrix

Cozy:

- `testOnly cozy.modeler.ModelerGenerationSpec`
  - parse `POWERTYPE` / `STATE-MACHINE`
  - value-only no component generation
  - generated files existence for powertype/statemachine (Phase B)

SimpleModeler:

- scala realm generation tests for `MPowertype` / `MStateMachine` object output

CNCF:

- component metadata intake tests (Phase C)
- state machine projection/validation tests remain green

---

## 7. Open Decisions

1. Powertype output form:
- case class wrapper vs enum/ADT

2. StateMachine output form:
- state value wrapper only vs richer generated model class

3. Metadata schema for CNCF hook:
- minimal (name/states/events) vs full (guards/priority/declarationOrder/action)

Current recommendation:

- Phase B: minimal generated class output first (stability priority)
- Phase C: minimal metadata hook first, then enrich incrementally

