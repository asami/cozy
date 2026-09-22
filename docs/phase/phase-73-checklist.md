# Phase 73 Checklist: CML Candidate-Admission Model Producer ABI

Phase status: in-progress
Ledger for: [Phase 73](phase-73.md)
Compatibility baseline: [Phase 62.3](phase-62.3.md)

This ledger records the bounded Cozy-side producer work. It neither authorizes
nor claims CNCF runtime or `sm-workflow` changes.

## Entry

- [x] Record the immutable upstream CNCF Phase 77 design reference that
      defines this Phase's consumer seam; do not request a duplicate,
      hand-authored Phase 73 ABI intake.
- [x] Inventory the released Phase 62.3 source, generated ABI, and fixture
      against the inherited upstream semantic requirement.
- [x] Record any required change as a versioned Cozy producer decision; do not
      infer CAM semantics from Action names or complete them in the consumer.

## CAM-73-01: Candidate-admission semantics (completed 2026-09-22)

- [x] Define the typed CML/IR classification for a candidate-producing
      semantic Action, using `JudgmentAction` as the direct reference case.
- [x] Define typed candidate/result alternatives and evidence/rationale
      requirements, including identity, scope, freshness, and provenance.
- [x] Define the distinct deterministic admission action/boundary and declared
      progression relation.
- [x] Prohibit a semantic result from directing the next StateMachine state.
- [x] Keep provider, transport, AI/Codex/human identity, runtime commitment,
      and application payload semantics outside the general CAM contract.

Focused validation: `PHASE-73-CAM-73-01-VAL-002` passed on 2026-09-22;
receipt `4cb66da9685d4184fea1c79ba0ef2e677bb427b0e5d29a7a498d58a3e27ef107`.
The preceding failed attempt remains retained as immutable repair evidence.

## CAM-73-02: Lowering, ABI, and diagnostics

- [x] Lower the frozen CAM declarations into a versioned generated producer
      ABI with source/model/generator identity and source-location provenance.
- [x] Generate enough typed information for a consumer to admit the declared
      contract without CML reparsing, display-name inference, or handwritten
      replacement data.
- [x] Reject absent, incomplete, ambiguous, contradictory, or unpinned CAM
      declarations with typed producer diagnostics.
- [x] Preserve Phase 62.3 behavior through an explicit compatibility/versioning
      decision, not an undocumented interpretation of its generic Action ABI.

## CAM-73-03: Producer evidence

- [x] Add focused executable specifications for positive construction,
      deterministic admission shape, provenance, determinism, rejection, and
      compatibility behavior.
- [x] Add one representative CAM fixture only after the contract is frozen;
      keep the released Phase 62.3 fixture as the baseline unless an accepted
      versioned change explicitly includes it.
- [x] Record focused validation evidence without claiming consumer runtime
      acceptance.
- [ ] Record the Phase's accepted final validation, independent review, and
      explicitly authorized release evidence.

## CAM-73-04: Consumer handoff

- [x] Freeze the exact CML source, generated ABI/schema identities,
      compatibility decision, diagnostics, and fixture evidence.
- [x] Freeze the exact local handoff for the CNCF Phase 77 successor line.
      Closed CWF-77-01/02 retain their Phase 62.3-only admission baseline; a
      versioned CAM-admission successor first admits this ABI and exposes its
      ComponentFactory discovery, CWF-77-04 through CWF-77-06 then consume the
      admitted form for runtime execution and Continuation/resume, and
      CWF-77-07/09 consume it for Skill projection and consumer handoff. The
      handoff must be sufficient for that work to proceed without CML reparsing,
      name inference, or a handwritten Cozy replacement.
- [x] Do not claim CNCF runtime admission, progression, commitment,
      persistence, provider dispatch, or `sm-workflow` completion.

## Focused validation evidence

`PHASE-73-CB-73-01-VAL-004` passed on 2026-09-22 with
`testOnly cozy.modeler.CompositeStateMachineCmlSpec
cozy.modeler.WorkflowCmlSpec cozy.modeler.StateMachineWorkflowAbiGenerationSpec`.
The verified command-execution receipt is
`d1e0f8e5cbfd09b82b5c87ca32547f504bcf376f8cda2ad4a5cfbcc11292fd54`; its
validated implementation-tree identity is
`d74a9f4508a16e284118bef7156fc1b9e2f121509ebeccef1638aa4e6abb84f1`.
The receipt records `sbt_exit=0`, `wrapper_exit=0`, and `lock=released`.

CB-73-01 was resolved before this validation: a source with no CAM Action
emits no CAM ABI, bootstrap, or sidecar files, retaining the exact Phase 62.3
generation tree. `PHASE-73-CAM-73-02-VAL-003` remains retained as earlier
successful evidence before that compatibility blocker was found.

The local producer handoff is frozen. Independent review and an explicitly
authorized release commit remain Phase-level closure work; neither is a CNCF
runtime or `sm-workflow` completion claim.
