# Phase 45 Checklist: Document Project Authoring Contract and Content Core Acceptance

This checklist is the authoritative progress ledger for Phase 45. It is not a
normative behavior contract.

Phase Status: IN_PROGRESS

Predecessor: Phase 44 closure. Semantic dependencies: accepted Phases 41, 42,
and 42.1. Successor: Phase 45.1 after this Phase closes.

The approved split retains P45-01 and P45-02 here. P45-03/P45-04 are owned by
`phase-45.1-checklist.md`; P45-05/P45-06 are owned by
`phase-45.2-checklist.md`.

## P45-01: Versioned Authoring Contract

Stage Status:

- Current status: COMPLETE
- Owner: Document Project public v2 authoring contract
- Update rule: update this block from the checklist state below.

- [x] Reconcile every promoted Phase 42.* omission against the original
  Document Project direction and preserve its requirement traceability.
- [x] Freeze `article-review-html` as a first-class Work Product role, without
  implementing its review projection in this Phase. The pre-existing read-only
  dashboard may show its selected contract-only status as
  `No action: contract-only in Phase 45`; this is neither a renderer/CLI/output
  nor an action capability.
- [x] Freeze authored-state separation between the expanded user
  dashboard/action surface owned by Phase 45.1 and diagnostic projections.
  Phase 45 changes no dashboard behavior other than the contract-only status
  above.
- [x] Freeze explicit optional-deliverable activation and selection semantics
  for the successor dashboard/action surface.
- [x] Freeze authored hooks for Japanese/English parity and
  per-artifact alignment without implementing their synchronization here.
- [x] Replace the unoperated v1 kernel with a closed
  `cozy.document-project.v2` descriptor that owns optional Work Product
  selection plus stable semantic/locale and per-artifact-alignment reference
  hooks; do not retain v1 compatibility or migration behavior.
- [x] Add behavior-oriented Executable Specifications for each admitted Phase
  45 contract requirement before implementation.
- [x] Require focused validation to pass for each admitted Executable
  Specification change before the corresponding Phase 45 contract item is
  complete.

## P45-02: Content Core Candidate, Feedback, and Acceptance Loop

Stage Status:

- Current status: PLANNED
- Owner: AI-assisted Content Core authoring and review workflow
- Update rule: update this block from the checklist state below.

- [ ] Produce and display a candidate before acceptance.
- [ ] Route reviewer feedback into a revised candidate.
- [ ] Keep candidate, rejected, superseded, and accepted revisions distinct.
- [ ] Record complete provenance: input-source/idea identity; provider/model
  identities; request/response identities; candidate/resulting-revision
  identities; reviewer-decision identity; operation identity; outcome identity;
  and diagnostic identity.
- [ ] Execute only an explicitly selected registered provider operation.
- [ ] Preserve append-only attempt and review history.
- [ ] Require explicit human acceptance before Content Core authority changes.

P45-01 is `COMPLETE` under `$cncf-goal-phase cozy 45`: its v2 descriptor and
contract-only article-review boundary passed `P45-01A-VAL-008` (49 tests) and
focused closure review 3 closed `CB-P45-RR-001` with no actionable finding.
P45-02 remains `PLANNED`. This does not reopen Phase 42/42.1, start Phase
45.1/45.2, or authorize Article 8 publication.
