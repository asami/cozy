# Deferred Modeler, Service, and Web Implementation Follow-up

status=task-handoff
date=2026-08-14
classification=functional-implementation-debt-not-hygiene

This journal separates observed functional incompleteness from source hygiene.
None of these items is authorized as a whitespace, naming, header, or
mechanical source-size repair. Each requires an executable specification and a
bounded implementation task.

## COZY-FUNC-MODELER-001: Complete StateMachine Projection Semantics

### CML-HISTORY-001 disposition (2026-08-14)

- The bounded CML named shallow-history slice is implemented and validated.
  Its focused generation, diagnostics, CNCF runtime, and generated-runtime
  scripted gates passed; the independent re-review found no remaining finding.
- Implemented contract: `HISTORY-FIELD` identifies an existing persistent
  record attribute; `TO :: Review.HISTORY` addresses an outer composite and
  uses its stored direct leaf or first declared direct leaf fallback.
- Invalid bare, deep, unknown, nested-composite, malformed-record, and
  unrecognized-leaf forms are diagnostic rather than silent fallbacks.
- Transition proposals remain caller-owned: generated/CNCF metadata describes
  required history writes and runtime validates it without mutating a record.
- This disposition does not close the remaining non-history items below or any
  other functional ledger entry.

- Affected source: `src/main/scala/cozy/modeler/Modeler.scala`.
- Observed incomplete areas include entity StateMachine selection by
  `headOption`, StateMachine-state naming assumptions, duplicated transition
  construction, history-transition handling that uses an unchecked `head` or
  raises `notImplementedYetDefect`, absent `MAction` mapping, and empty diagram
  package derivation.
- Required direction: define accepted CML inputs, deterministic selection,
  diagnostics, history semantics, action semantics, and diagram identity in
  executable specifications before implementation.
- Relationship to `COZY-HYG-SIZE-003`: the structural split may isolate this
  responsibility, but must not silently invent or change these semantics.
- Remaining separate follow-up: deterministic multi-machine selection,
  general `MAction` mapping, and diagram package derivation remain open.

## COZY-FUNC-SERVICE-001: Implement Modeler Service Operations

- Affected source: `src/main/scala/cozy/modeler/ModelerServiceClass.scala`.
- Observed incomplete areas: `ClassOperationClass.apply` and
  `ProjectOperationClass.apply` each contain `???` before returning
  `VoidResponse`.
- Required direction: decide the request/response model, service behavior,
  error taxonomy, authorization, and generated/project artifacts through
  executable specifications. Do not replace the holes with an unstructured
  success response.

## COZY-FUNC-WEB-001: Decide Arcadia Engine Configuration and ID Semantics

- Affected source: `src/main/scala/cozy/web/arcadia/Engine.scala`.
- Observed incomplete areas: fixed `FormatterContext.default` is marked as
  needing customization, and the domain-object ID schema field is marked TODO.
- Required direction: define configuration ownership, request/locale behavior,
  identity representation, and compatibility expectations before changing
  rendering or schema behavior.

## Scope Boundary

These are functional requirements, not hygiene items. They require their own
design/specification and focused plus full validation; no current phase closure
or hygiene commit is evidence that they are complete.
