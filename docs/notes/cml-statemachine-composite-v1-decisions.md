# CML StateMachine / Composite StateMachine / Workflow v1 Decisions

Status: design baseline
Date: 2026-09-05
Runtime consumer: `asami/goldenport-cncf`

## 1. Sequential Composite Semantics

Composite StateMachine v1 is sequential by default. One committed constituent
transition is processed, the composite configuration is re-evaluated, and zero
or one derived composite transition results before the next constituent
transition is processed.

Parallel semantics are future explicit opt-in CML semantics, not a runtime
performance switch.

## 2. Stable Definition and Occurrence Identity

CML/Cozy owns stable definition identities for StateMachines, Composite
StateMachines, states, transitions, rules, actions, and relevant bindings.

CNCF owns runtime occurrence identities. Generated metadata must preserve enough
stable definition identity for CNCF to record a causal chain from constituent
transition occurrence through derived composite transition and resulting
execution.

The sequential v1 model permits one direct `causedBy` transition occurrence for
each derived composite transition. Future parallel semantics may generalize this
relationship.

## 3. Minimal Action Metadata

Use the following v1 semantic metadata:

```text
ActionMetadata
  actionId
  effectClass
  transactionRequirement
  idempotency
  compensationHandlerRef?
  ordering / provenance
```

Do not put retry/backoff/timeout/circuit-breaker/provider configuration into CML
core semantics.

Logical actions compile through an Action binding/compiler onto CNCF's existing
`ExecProgram[UnitOfWorkOp, A]`; Cozy does not define a second executable Action
algebra.

## 4. Atomicity, Compensation, and Recovery Boundary

Actions admitted inside one CNCF UnitOfWork inherit complete rollback semantics.
Optional 2PC may enlarge the atomic boundary when runtime capability supports the
required semantics.

External business compensation is application code registered as a compensation
handler. CML may preserve the stable handler reference but does not synthesize
or orchestrate automatic compensation chains in v1.

If the handler fails, CNCF durably emits/persists `RecoveryRequired` containing
the original `UnitOfWorkId`. CML does not model the subsequent administrator
repair procedure as core StateMachine semantics.

A committed StateMachine transition remains historical fact. Business reversal
uses another explicit transition/action.

## 5. Definition Version Pinning

The behavior of a StateMachine, Composite StateMachine, or Workflow instance is
fixed at instance creation.

Generated/runtime instance binding includes:

```text
definitionId
definitionVersion
```

Existing instances remain on their pinned version after a new CML definition is
published. New instances may use the new version.

The pinned semantic version covers states/transitions, guards, composite-state
rules, constituent bindings, action bindings/metadata, and compensation handler
association relevant to behavior.

No silent migration is permitted. Explicit instance migration is a future
feature and must validate current configuration and pending/recovery state before
changing definition semantics.

## Design Consequence

The v1 contract prioritizes deterministic, testable, explainable behavior over
implicit concurrency or live semantic mutation. These decisions should be
reflected by Phase 33/33.1/33.2 generation, diagnostics, fixtures, and the CNCF
Phase 64 family acceptance tests.
