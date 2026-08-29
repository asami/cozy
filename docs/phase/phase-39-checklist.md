# Phase 39 Checklist: PDF Command Contract and Semantics

This checklist is the authoritative progress ledger for Phase 39. It is not a
normative behavior contract.

Phase Status: COMPLETE

## Split Scope

Phase 39 retains PDF command semantics only. The original PDF39-03
working-directory/runtime scope and dual-working-directory driver acceptance
are exclusively owned by Phase 39.1. `P39-S1` is accepted in local commit
`56789812ea02e14b10c15ca571dbfd29418f3753`, and `P39-S2` is accepted in local
commit `8130605509956750b3d350a64ec33ed927b21927`; remaining command-contract
items stay open until their stated evidence is complete.

## Scope Removal Record: Image Syntax and PDF Receipts

Markdown image admission, image-model normalization, PDF image-output
acceptance, and PDF receipt persistence are not Phase 39 checklist items or
closure criteria. They are deferred as `DEV-012` to a future SmartDox
image-model and Cozy integration Phase. The decision record is
`docs/journal/2026/08/2026-08-29-phase-39-image-receipt-deferral.md`.

- [x] `DEV-012` (Future Development Candidate): defer Markdown image
      admission/image model normalization, PDF image-output acceptance, and a
      PDF receipt contract/persistence. This work is not a Phase 39 checklist
      item or closure criterion. See [docs/journal/2026/08/2026-08-29-phase-39-image-receipt-deferral.md](../journal/2026/08/2026-08-29-phase-39-image-receipt-deferral.md).

## PDF39-02: Command Help Contract

Stage Status:

- Current status: COMPLETE
- Owner: Cozy PDF command help and launcher compatibility contract
- Update rule: mark work complete only when all PDF39-02 acceptance bullets
  below are checked.

- [x] Make `cozy pdf --help` display PDF command help. (`P39-S1`,
      `56789812ea02e14b10c15ca571dbfd29418f3753`)
- [x] Verify the equivalent PDF help route displays the same command help.
      (`P39-S1`, `56789812ea02e14b10c15ca571dbfd29418f3753`)
- [x] Document input, output, renderer, format, and profile options with
      representative help examples. (`P39-S2`,
      `8130605509956750b3d350a64ec33ed927b21927`)
- [x] Verify deterministic `--help` interpretation and error behavior for
      supported and invalid invocations. (`P39-S1`,
      `56789812ea02e14b10c15ca571dbfd29418f3753`)
- [x] Verify top-level help and the established launcher invocation surface.
      (`P39-S1`, `56789812ea02e14b10c15ca571dbfd29418f3753`)

## PDF39-04: Format and Profile Semantics

Stage Status:

- Current status: COMPLETE
- Owner: Cozy PDF format/profile command model
- Update rule: mark work complete only when all PDF39-04 acceptance bullets
  below are checked.

- [x] Specify the relationship between `--latex-format` and media `--profile`.
      (`P39-S1`, `56789812ea02e14b10c15ca571dbfd29418f3753`; `P39-S2`,
      `8130605509956750b3d350a64ec33ed927b21927`)
- [x] Explicitly prohibit reuse of `business` across unrelated namespaces
      unless a declared mapping exists. (`P39-S1`,
      `56789812ea02e14b10c15ca571dbfd29418f3753`; `P39-S2`,
      `8130605509956750b3d350a64ec33ed927b21927`)
- [x] Add supported-value enumeration, validation, help, and diagnostics for
      each format/profile namespace. (`P39-S1`,
      `56789812ea02e14b10c15ca571dbfd29418f3753`; `P39-S2`,
      `8130605509956750b3d350a64ec33ed927b21927`)
- [x] Specify and test compatibility for existing commands. (`P39-S1`,
      `56789812ea02e14b10c15ca571dbfd29418f3753`; `P39-S2`,
      `8130605509956750b3d350a64ec33ed927b21927`)

## PDF39-05: Command Contract Validation and Closure

Stage Status:

- Current status: COMPLETE
- Owner: Cozy PDF command-contract validation and Phase closure
- Update rule: mark work complete only when all PDF39-05 acceptance bullets
  below are checked.

- [x] Run focused Executable Specifications for all Phase 39 command
      contracts. (`P39-S1`, focused receipt `65773-20260829T011025Z`: 6
      succeeded, 0 failed; `P39-S2`, focused receipt
      `94705-20260829T021750Z`: 8 succeeded, 0 failed)
- [x] Run the full Cozy gate through serialized SBT execution. Final receipt
      `12823-20260829T025348Z`: 1,532 succeeded, 0 failed, 8 canceled, 117
      suites completed, and 0 aborted; the distinct local release commit is
      therefore permitted.
- [x] Complete independent Phase review and synchronize Strategy, Phase, and
      checklist ledgers before closure.

Phase 39 is COMPLETE in its release binding. `P39-S1` and `P39-S2` are
accepted in their local commits, with focused validation recorded for both
Steps; the mandatory independent Phase review is sealed PASS. Final serialized
Cozy receipt `12823-20260829T025348Z` passed. Phase 39 neither publishes nor
pushes, and it does not execute, activate, or accept downstream work for
Phase 39.1.
