# Phase 36 P36-02B Decision Resolution

Date: 2026-08-27

Status: decision record; implementation not started by this record

## Decision Resolution Record

- Decision ID: `P36-02B-DEC-001`
- Pending decision: public compatibility contract for
  `cozy media presentation migrate --semantic-map`
- Affected Phase/Step: Phase 36 / `P36-02B` explicit legacy Slide IR
  migration
- Affected tree: committed HEAD
  `60ec0b0ac4c3fe9c0f97f2440be443d0859eee72`; the separately owned current
  Phase-36 planning-document diff has SHA-256
  `ca3f723babbd2007e3034d19a20b6369ca4b2bc3445284ddc7115c45f827de0a` and is
  excluded from this decision
- Attributable answer: 2026-08-27 user approval of `P36-02B-DEC-001`
- Selected action: define and implement the recommended versioned public
  `cozy.visual-page.migration-map.v1` contract.  It carries the SHA-256 of the
  raw legacy Slide IR input, a fully human-authored target Visual Page Set,
  and a bijective binding from every legacy element, addressed by `slideId`
  and zero-based `elementIndex`, to represented Visual Page semantic or
  provenance material.
- Required failure and output boundary: a source-digest mismatch, missing,
  duplicate, out-of-range, ambiguous, or lossy binding fails without a claimed
  output.  Only a fully validated map may atomically save the Visual Page Set
  and emit its deterministic diagnostic migration report.
- Authority boundary: P36-02 typed Visual Page and explicit Slide IR migration
  only.  Existing Slide IR, presentation renderer, Storyboard, Media Package,
  receipt, review-state, external-consumer, and repository boundaries remain
  unchanged.
- Authorized next state: `PARENT_CAPABILITY_CHECK`
- Consumed: `true`

The next state must turn this accepted public behavior into the normative
specification, executable specification, and implementation manifest before
any implementation begins.  This record does not itself change status,
implement the command, validate it, commit a Step, or close VIS36-02 or Phase
36.

## Decision Resolution Record

- Decision ID: `P36-02B-DEC-002`
- Pending decision: the P36-02B public command was dispatched but its help
  entry and its existing help assertion were outside the frozen Step paths.
- Finding: `CPB-P36-02B-001` from the sealed P36-02B lightweight review.
- Affected Phase/Step: Phase 36 / `P36-02B` explicit legacy Slide IR
  migration.
- Affected tree: committed Step accumulator base
  `60ec0b0ac4c3fe9c0f97f2440be443d0859eee72`; before this resolution the
  admitted help paths had SHA-256
  `2f3b0f50576aafda74d4fe7357c83946fc7f68fdd67a5a06f1dd11ad30d6f9c1`
  (`CozyHelpText.scala`) and
  `37915ce45355b28ad3d8a58bef0333f2589bd52f57e7e65206e8653577c22c12`
  (`CozyVisualPageSpec.scala`).  The other P36-02B owned paths remain the
  frozen migration accumulator; the Phase status documents and Fast Semantic
  Preview decision remain excluded concurrent work.
- Attributable answer: 2026-08-27 user authorization:
  `P36-02B-DEC-002 を許可する`.
- Selected action: expand P36-02B only to add the approved migration command
  usage and behavior description to `CozyHelpText.scala`, and assert that
  help entry in the existing `CozyVisualPageSpec.scala` help scenario.
- Authority boundary: this resolution changes neither the dispatched command
  grammar nor the `cozy.visual-page.migration-map.v1` contract accepted in
  `P36-02B-DEC-001`; it makes that already-approved public command
  discoverable.  No renderer, Storyboard, Media Package, receipt,
  review-state, external-consumer, or repository boundary is admitted.
- Authorized next state: `PLAN`.
- Consumed: `true`.

The resumed PLAN must freeze the two added paths, preserve all unrelated
working-tree changes, and select proportionate focused validation before any
edit.  This decision resolution itself does not implement, validate, stage,
commit, or close the Step or Phase.

## Decision Resolution Record

- Decision ID: `P36-02B-DEC-003`
- Pending decision: local acceptance commit of the reviewed P36-02B Step on
  the current `main` branch.
- Affected Phase/Step: Phase 36 / `P36-02B` explicit legacy Slide IR
  migration and public-help closure.
- Affected tree: committed Step accumulator base
  `60ec0b0ac4c3fe9c0f97f2440be443d0859eee72`; exact owned paths are the
  frozen ten-path P36-02B commit set recorded in manifest
  `cozy-p36-02b-step-commit-20260826T213337Z`.  The three preserved concurrent
  paths remain excluded and unstaged.
- Attributable answer: 2026-08-27 user authorization:
  `P36-02B の10所有パスを main に local acceptance commit する。push・publish はしない。`.
- Selected action: create exactly the approved local acceptance commit on
  `main` after final-tree validation receipts are rebound.  Do not push,
  publish, deploy, or mutate the preserved paths.
- Authority boundary: stage only the frozen P36-02B ten-path set.  This record
  authorizes no Phase release, status-ledger change, remote write, or work in
  Phase 37 or any successor Phase.
- Authorized next state: `STEP_COMMIT`.
- Consumed: `true`.

The resumed STEP_COMMIT must rebind validation to the resulting final
decision-record tree before delegating the already reviewed manifest to the
leaf commit executor.
