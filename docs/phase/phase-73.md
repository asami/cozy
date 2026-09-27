# Phase 73: CML Candidate-Admission Model Producer ABI

status=closed
execution_priority=cncf_phase_77_development_prerequisite
entry_condition=user_selected_upstream_phase_77_design_authority
depends_on=phase-62.3.md

Status: closed on 2026-09-23 by explicit owner direction. CB-73-02's
CML-key-normalization and null normalized-IR follow-up repair passed fresh
full-suite validation and independent re-review. CAM-73-01 through CAM-73-03
and compatibility blocker CB-73-01 are locally complete. The generated ABI
has a source-tracked, byte-verified handoff fixture. CNCF Phase 77 has since
closed for Cozy 62.1–62.3, so its historical open-stage mapping is not a
current receiver. CWF-77-04C in CNCF Phase 77.1 received and accepted that
producer ABI in commit `3006494cdd279c44c37831387184554f7a202f23`. Any later
compatible-release consumption remains Future Development Candidate `DP-73-01`;
it is not claimed complete by this Cozy Phase closure.
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
| CAM-73-01 | Freeze the provider-neutral CML/IR vocabulary for a candidate-producing semantic Action, typed Candidate/Result/Rationale/Evidence, and the deterministic admission boundary. Focused validation `PHASE-73-CAM-73-01-VAL-002` passed; the producer fails closed until CAM-73-02 lowering is implemented. | completed |
| CAM-73-02 | Lower the vocabulary into a versioned, source-attributed generated ABI with explicit compatibility handling for the closed Phase 62.3 producer contract. | completed locally: CB-73-02 rejects CML-key-normalized duplicate and null normalized-IR Judgment semantics before artifacts are emitted |
| CAM-73-03 | Add isolated positive, rejection, determinism, provenance, and compatibility executable specifications and a representative fixture; preserve the released Phase 62.3 fixture as the baseline unless a separately accepted compatibility change says otherwise. | completed locally: Scala-plus-JSON lowerer rejection coverage passed fresh full-suite validation |
| CAM-73-04 | Freeze producer evidence and a precise CNCF consumer handoff boundary. The versioned ABI is source-tracked and byte-verified locally. The historical Phase 77 open-stage mapping is superseded because current Phase 77 is closed for Cozy 62.1–62.3. CWF-77-04C in CNCF Phase 77.1 accepted the ABI in commit `3006494cdd279c44c37831387184554f7a202f23`; any later compatible-release consumer remains separate. | producer handoff artifact and CNCF receiver acceptance complete; later compatible-release consumption remains `DP-73-01` |

## Acceptance

- A CML declaration can explicitly classify a semantic candidate-producing
  Action without relying on its display name or a provider identity.
- The generated ABI exposes typed candidate/result alternatives, rationale, evidence and
  provenance, plus the declared deterministic admission boundary. It does not
  expose a semantic result as a state-transition command.
- Invalid or incomplete alternative/evidence/admission declarations fail with
  typed, attributable producer diagnostics before artifact publication.
- Repeated generation from the same admitted source and generator version is
  deterministic for the selected ABI encoding.
- Existing Phase 62.3 source, generated ABI, and fixture behavior remain
  compatible through an explicit versioning decision; no compatibility is
  assumed merely because an Action has a similar name.
- The local completion handoff records the exact source, generated ABI/schema
  identity, compatibility decision, fixture evidence, current canonical CNCF
  recipient, and remaining consumer-owned runtime responsibilities. Cozy's
  local Phase closure did not claim external receipt; CNCF Phase 77.1 later
  accepted the named producer ABI in CWF-77-04C without CML reparsing or a
  handwritten Cozy replacement. `DP-73-01` remains only for later
  compatible-release consumption, not for creating that receiver.

## Deferred Development Candidate

- `DP-73-01` **(Future Development Candidate)** — a later compatible release
  consumer, including any `sm-workflow` integration, must consume the accepted
  CWF-77-04C receiver contract. The producer ABI has already been received and
  accepted by CNCF Phase 77.1 in commit
  `3006494cdd279c44c37831387184554f7a202f23`. That later consumer scope is
  deliberately outside closed Cozy Phase 73.

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
- CNCF Phase 77 implementation or closure. CWF-77-04C receiver acceptance is
  recorded separately; `DP-73-01` remains only for a later compatible-release
  consumer. Phase 73 neither performs nor claims that consumer work.

## References

- [Phase 73 checklist](phase-73-checklist.md)
- [Candidate-Admission Model design principle](../notes/candidate-admission-model.md)
- [Candidate-Admission Model decision record](../journal/2026/09/2026-09-21-candidate-admission-model.md)
- [Candidate-Admission producer ABI](../design/cml-candidate-admission-producer-abi.md)
- [Closed Phase 62.3 producer baseline](phase-62.3.md)
- [Deferred generalized Composite artifact](phase-70.md)
