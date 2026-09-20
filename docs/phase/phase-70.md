# Phase 70: Generalized Composite StateMachine Semantic Artifact

status=planned
execution_priority=deferred_until_sm_workflow_vertical_slice
entry_condition=concrete_consumer_requirement
consumer=[CNCF Phase 89](https://github.com/asami/goldenport-cncf/blob/main/docs/phase/phase-89.md)

Status: planned; execution deferred before implementation
Planned at: 2026-09-20
Reframed at: 2026-09-20
Development item: DEV-033
Primary owner: Cozy

## Purpose

Generate a closed, typed, deterministic semantic artifact for a CML Composite
StateMachine so that consumers can use the declared model without reparsing
CML, inferring meaning from names, or reconstructing missing semantics.

This is a reusable Cozy producer feature. It is not a repair for CNCF Phase 64,
not an entry condition for `sm-workflow` Phase 1, and not an acceptance bucket
for unrelated StateMachine changes already present in the Cozy worktree.

Its downstream consumer is CNCF Phase 89. Phase 70 owns production of the
generalized semantic artifact; CNCF Phase 89 owns its admission, compatibility
diagnostics, `ComponentFactory` discovery, and runtime projection. Neither
Phase starts merely because CNCF Phase 64 contains the broader SWF-06 design.

## Entry condition

Phase 70 remains planned but must not start until both conditions hold:

1. the first `sm-workflow` vertical slice has reached a stable result; and
2. a concrete consumer demonstrates a requirement that the released Cozy
   Workflow/StateMachine artifacts do not already satisfy.

The consumer requirement must identify the required semantic fields and their
use. General architectural usefulness alone is insufficient to start the
Phase.

At entry, re-estimate the work from the then-current Cozy implementation. Do
not reuse the former twelve-hour estimate or automatically recreate Phase
70.1. Split only if the new bounded plan requires it.

## Target artifact

One admitted CML generation occurrence produces one versioned Composite
StateMachine semantic artifact containing:

- artifact schema identity and version;
- generator identity and version;
- Composite StateMachine definition identity and pinned version;
- exact constituent roles, definition references, versions, subjects, and
  configuration;
- a complete finite set of typed derivation rules with stable identities,
  exact input references, explicit composite-state outputs, and declaration
  order;
- typed logical action descriptors with owning machine/role, transition or
  phase, deterministic order, correlation/causation, and execution-relevant
  metadata that belongs to the producer contract;
- source identity, model identity/version, and exact source-location
  provenance for every represented declaration; and
- producer diagnostics for reachability, coverage, overlap, ambiguity, and
  incompleteness.

The semantic artifact is an output of Cozy's model pipeline. JSON or another
sidecar encoding may transport it, but the encoding is not the canonical
domain model.

## Required behavior

- Repeated generation from the same admitted source and generator version is
  byte-deterministic for the selected encoding.
- Every identity and version pin is explicit.
- Rule and action references are typed references, not display-name matching.
- Diagnostics are typed and attributable to the affected definition, rule,
  action, and source location.
- Missing, duplicate, foreign, unpinned, opaque, incomplete, ambiguous, or
  nondeterministic input fails before artifact publication.
- Consumers need neither CML parsing nor handwritten replacement data.
- Existing generated Workflow/StateMachine ABI behavior remains compatible
  unless the concrete entry requirement explicitly authorizes a versioned
  change.

## Work outline

| ID | Outcome | Status |
| --- | --- | --- |
| CSA-70-01 | Freeze the semantic artifact domain model, identity/version rules, and encoding boundary from a concrete consumer requirement. | planned |
| CSA-70-02 | Project constituents, configuration, typed rules, actions, and exact provenance from the Cozy IR. | planned |
| CSA-70-03 | Produce deterministic artifact encoding and typed producer diagnostics. | planned |
| CSA-70-04 | Add positive, rejection, determinism, and compatibility executable specifications plus a consumer handoff. | planned |

These items describe the preserved future capability. They do not authorize
implementation before the entry condition is satisfied.

CSA-70-04 produces the exact input accepted by CNCF Phase 89. CNCF must not
complete missing producer semantics by reparsing CML, inferring names, or
supplying handwritten replacement data.

## Explicit separation from current work

Cozy commits `ca3a031` and `4561c02`, together with the currently preserved
StateMachine projection and SalesOrder fixture changes, are separate existing
work. Phase 70 neither accepts nor rejects those changes and must not absorb
them automatically. They require their own bounded review or development
authority.

The closed Cozy Phase 62.3 generated Workflow ABI and fixture remain the
producer handoff for CNCF Phase 77 and `sm-workflow` Phase 1. CNCF Phase 64
must use that released handoff first and may not block on Phase 70.

The later sequence is deliberately separate:

```text
stable first sm-workflow vertical slice
  + concrete consumer requirement
  -> Cozy Phase 70 producer artifact
  -> CNCF Phase 89 consumer admission and runtime projection
```

## Non-goals

- Repairing or closing CNCF Phase 64.
- Reopening Cozy Phase 62 through Phase 63.1.
- Runtime Composite StateMachine derivation or Workflow execution in Cozy.
- CNCF Provider, Continuation, persistence, API/SPI admission, or protocol work.
- Generic orchestration, transport, REST, MCP, UI, or Flutter behavior.
- Pre-authorizing a Phase 70.1 split or a repository-full validation schedule.

## Completion

When the entry condition is eventually satisfied, completion requires every
item in [Phase 70 Checklist](phase-70-checklist.md), focused and full validation
appropriate to the newly frozen plan, independent review, and an exact
consumer handoff to CNCF Phase 89. Until then, this document is a durable
future specification, not an active execution ledger.

## References

- [Phase 70 future checklist](phase-70-checklist.md)
- [Superseded Phase 70.1 split record](phase-70.1.md)
- [Producer extraction and reprioritization history](../journal/2026/09/2026-09-20-cncf-phase-64-cozy-producer-extraction.md)
- [Closed Cozy Phase 62.3 producer handoff](phase-62.3.md)
- [CNCF Phase 64](https://github.com/asami/goldenport-cncf/blob/main/docs/phase/phase-64.md)
- [CNCF Phase 77](https://github.com/asami/goldenport-cncf/blob/main/docs/phase/phase-77.md)
- [CNCF Phase 89 consumer admission](https://github.com/asami/goldenport-cncf/blob/main/docs/phase/phase-89.md)
