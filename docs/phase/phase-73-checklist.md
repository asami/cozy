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
      declarations with typed producer diagnostics, including CB-73-02's
      CML-key normalization and null normalized-IR defensive validation.
- [x] Preserve Phase 62.3 behavior through an explicit compatibility/versioning
      decision, not an undocumented interpretation of its generic Action ABI.

## CAM-73-03: Producer evidence

- [x] Add focused executable specifications for positive construction,
      deterministic admission shape, provenance, determinism, rejection, and
      compatibility behavior, including CB-73-02 lowerer rejection coverage
      for CML-key normalization and null references/collections/elements.
- [x] Add one representative CAM fixture only after the contract is frozen;
      keep the released Phase 62.3 fixture as the baseline unless an accepted
      versioned change explicitly includes it.
- [x] Record focused validation evidence without claiming consumer runtime
      acceptance.
- [x] Retain the representative canonical JSON sidecar as a source-tracked
      handoff fixture and verify that both public lowering routes reproduce its
      bytes exactly.
- [ ] Record the Phase's accepted final validation, independent review, and
      explicitly authorized release evidence.

## CAM-73-04: Consumer handoff

- [x] Freeze the exact CML source, generated ABI/schema identities,
      compatibility decision, diagnostics, and fixture evidence.
- [x] Freeze the exact local handoff artifact: the source-tracked
      `candidate-admission-producer-abi.json` fixture, its schema and SHA-256,
      typed diagnostics, and Phase 62.3 additive compatibility decision.
- [ ] Record acceptance by the canonical CNCF admission receiver. Current CNCF
      Phase 77 is closed for Cozy 62.1–62.3, while `sm-workflow` requires a
      compatible CNCF Phase 90 release; the recipient record and its fail-closed
      provenance/compatibility acceptance are not yet present.
- [x] Do not claim CNCF runtime admission, progression, commitment,
      persistence, provider dispatch, or `sm-workflow` completion.

## Focused validation evidence

Historical `PHASE-73-CB-73-01-VAL-004` passed on 2026-09-22 with
`testOnly cozy.modeler.CompositeStateMachineCmlSpec
cozy.modeler.WorkflowCmlSpec cozy.modeler.StateMachineWorkflowAbiGenerationSpec`.
The verified command-execution receipt is
`d1e0f8e5cbfd09b82b5c87ca32547f504bcf376f8cda2ad4a5cfbcc11292fd54`; its
validated implementation-tree identity is
`d74a9f4508a16e284118bef7156fc1b9e2f121509ebeccef1638aa4e6abb84f1`.
The receipt records `sbt_exit=0`, `wrapper_exit=0`, and `lock=released`.

CB-73-01 was resolved before this validation: a source with no CAM Action
emits no CAM ABI, bootstrap, or sidecar files, retaining the exact Phase 62.3
generation tree. The subsequent required-rationale repair makes this earlier
receipt historical rather than current release evidence. CB-73-02 was then
repaired with both public lowering routes covered by focused validation
`PHASE-73-CB-73-02-VAL-001` (receipt
`bb9559a86f2bb2050b052e0cbfc6b3267bc63c517072271c9be72a6efd30f4f6`) and
full-suite validation `PHASE-73-CB-73-02-FINAL-VAL-001` (receipt
`af8a447e98b1c15ffaf033043c97789bed0200c142bacb18bbc172d822ceb276`). Both
record `sbt_exit=0`, `wrapper_exit=0`, and `lock=released`.

The independent re-review then found a CML-key-normalization and null-IR
follow-up. Its final full-suite validation
`PHASE-73-CB-73-02-FOLLOWUP-FINAL-VAL-002` passed with receipt
`d76f99e6c6b4e3c3e3d08a6bd660133990554be5c0fd3e16ee7587bd9d29ded0`,
validated implementation-tree identity
`8dfe9345c3377d2e2122ae359511028ba51e95d0f1ba9114be11a1857d2f7252`,
and `sbt_exit=0`, `wrapper_exit=0`, `lock=released`.

Fresh independent CB-73-02 re-review returned `CLEAN`: it confirmed exact CML
normalized-key duplicate rejection, null-safe malformed normalized-IR handling,
both Scala and JSON lowering paths, unchanged valid CAM output, Phase 62.3
no-CAM compatibility, and the provider-neutral CNCF Phase 77 handoff boundary.

The local producer handoff is frozen. Independent review and an explicitly
authorized release commit remain Phase-level closure work; neither is a CNCF
runtime or `sm-workflow` completion claim.

`PHASE-73-HANDOFF-FIXTURE-VAL-001` subsequently ran
`testOnly cozy.modeler.StateMachineWorkflowAbiGenerationSpec` on the tree that
adds the tracked fixture. Receipt
`5948655920a73ab9c8079b88673fa8f6c55ac889089bc338158f49f2a02a5a25`
records `sbt_exit=0`, `wrapper_exit=0`, and `lock=released`, with validated
tree identity `8e1c9665b36e72eb31af7ba1866d8393ccf0ab48daf44f376d8b1fd9a3047816`.
It proves byte identity between both public lowering routes and
`src/test/resources/modeler/candidate-admission-producer-abi.json`; it is
producer evidence, not external consumer acceptance.
