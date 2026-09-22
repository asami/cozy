# Phase 73: CML Candidate-Admission Model Producer ABI

status=in-progress
execution_priority=cncf_phase_77_development_prerequisite
entry_condition=user_selected_upstream_phase_77_design_authority
depends_on=phase-62.3.md

Status: in progress; CAM-73-01 through CAM-73-04 and compatibility blocker
CB-73-01 are locally complete and focused-validated; independent review and
an authorized release commit remain open
Planned at: 2026-09-22
Development item: DEV-035
Primary owner: Cozy
Downstream development enabled: CNCF Phase 77 / `sm-workflow`

## Purpose

Enable the remaining CNCF Phase 77 / `sm-workflow` development by turning
the existing Candidate-Admission Model (CAM) design direction into the minimum
bounded, provider-neutral CML StateMachine / Workflow producer ABI that its
consumer lacks. The smallest reference is a semantic `JudgmentAction`: it
constructs a typed candidate, result, and evidence, while a deterministic
StateMachine admission step alone decides whether that candidate may progress.

This Phase supplies the Cozy declarative semantics, validation, lowering,
generated ABI, and producer evidence needed for that CNCF development to
proceed. CNCF retains its runtime admission, progression, commitment, and
application implementation; Phase 73 neither performs those runtime concerns
nor claims their completion.

The closed Phase 62.3 fixture and generated Workflow ABI are the compatibility
baseline. They do not already define `JudgmentAction`, `Judgment`, or a CAM
candidate/admission contract, and this Phase does not retroactively claim that
they do.

## Entry condition

Phase 73 consumes the user-selected upstream CNCF Phase 77 design authority that
defines its consumer seam. It must not require a separate hand-authored ABI
intake for a contract already defined by that authority. The first task records
the immutable upstream source reference, inventories the released Phase 62.3
ABI and fixture against it, and selects a versioned producer compatibility path
before modifying a generated contract.

The present planning record establishes the demonstrated absence of the
required CAM vocabulary in Cozy's current Scala source and executable
specifications. It does not itself authorize a CNCF runtime change.

## Producer contract to freeze during execution

The exact CML spelling and Scala hierarchy remain an execution-time design
decision, but the admitted contract must make all of the following explicit
and typed:

- an Action classification for semantic candidate construction, with
  `JudgmentAction` as the direct reference case rather than a name-inference
  convention;
- candidate/result alternatives, the candidate identity, and the associated
  rationale/evidence requirement;
- evidence scope, freshness, and source/provenance sufficient for deterministic
  admission without a provider-specific payload;
- the deterministic admission boundary and declared progression relation;
- source/model/generator identity, schema version, and source-location
  provenance in the generated producer ABI; and
- diagnostics for absent, incomplete, ambiguous, or contradictory CAM
  declarations.

A semantic result must not carry a next-state directive. A deterministic
admission action remains distinct from semantic work. Domain-specific payloads
remain application-owned, and the generated ABI must not encode an AI, Codex,
human, transport, or provider identity as CAM semantics.

## Work outline

| ID | Observable outcome | Status |
| --- | --- | --- |
| CAM-73-01 | Freeze the provider-neutral CML/IR vocabulary for a candidate-producing semantic Action, typed Candidate/Result/Evidence, and the deterministic admission boundary. Focused validation `PHASE-73-CAM-73-01-VAL-002` passed; the producer fails closed until CAM-73-02 lowering is implemented. | completed |
| CAM-73-02 | Lower the vocabulary into a versioned, source-attributed generated ABI with explicit compatibility handling for the closed Phase 62.3 producer contract. | completed; CB-73-01 retained the exact no-CAM Phase 62.3 generation tree; focused validation `PHASE-73-CB-73-01-VAL-004` |
| CAM-73-03 | Add isolated positive, rejection, determinism, provenance, and compatibility executable specifications and a representative fixture; preserve the released Phase 62.3 fixture as the baseline unless a separately accepted compatibility change says otherwise. | completed; focused validation `PHASE-73-CB-73-01-VAL-004` |
| CAM-73-04 | Freeze producer evidence and an exact CNCF consumer handoff. Closed CWF-77-01/02 remain the Phase 62.3 admission baseline and do not consume this new ABI; a versioned CAM-admission successor first admits it, then CWF-77-04–06 consume it for runtime execution and CWF-77-07/09 consume its admitted form for Skill projection and consumer handoff. This work proceeds without name inference or a handwritten Cozy replacement. | completed locally; external delivery, review, and release remain separate |

## Acceptance

- A CML declaration can explicitly classify a semantic candidate-producing
  Action without relying on its display name or a provider identity.
- The generated ABI exposes typed candidate/result alternatives, evidence and
  provenance, plus the declared deterministic admission boundary. It does not
  expose a semantic result as a state-transition command.
- Invalid or incomplete alternative/evidence/admission declarations fail with
  typed, attributable producer diagnostics before artifact publication.
- Repeated generation from the same admitted source and generator version is
  deterministic for the selected ABI encoding.
- Existing Phase 62.3 source, generated ABI, and fixture behavior remain
  compatible through an explicit versioning decision; no compatibility is
  assumed merely because an Action has a similar name.
- The completion handoff records the exact source, generated ABI/schema
  identity, compatibility decision, fixture evidence, and remaining
  consumer-owned runtime responsibilities. It is sufficient for CNCF Phase 77
  to resume its separately owned admitted development.

## Non-goals

- CNCF runtime admission, progression, commitment, persistence, provider
  dispatch, transport, Continuation execution, or `sm-workflow` application
  changes.
- An AI/Codex/human-specific language construct, Skill/JSON protocol, or
  handwritten consumer-side replacement for Cozy semantics.
- Reopening or silently changing closed Phase 62 through Phase 62.3 evidence.
- The generalized Composite StateMachine semantic artifact reserved for Phase
  70, or CNCF Phase 89's consumer admission/runtime projection.
- Scheduling/lifecycle, failure-model, UI, REST, Flutter, publication, or
  unrelated media work.
- CNCF Phase 77 implementation or closure. Phase 73 enables that separately
  owned development with an accepted producer handoff; it does not perform or
  automatically close it.

## References

- [Phase 73 checklist](phase-73-checklist.md)
- [Candidate-Admission Model design principle](../notes/candidate-admission-model.md)
- [Candidate-Admission Model decision record](../journal/2026/09/2026-09-21-candidate-admission-model.md)
- [Candidate-Admission producer ABI](../design/cml-candidate-admission-producer-abi.md)
- [Closed Phase 62.3 producer baseline](phase-62.3.md)
- [Deferred generalized Composite artifact](phase-70.md)
