# Phase 63.1 Checklist: sbt-cozy Multi-CML Bridge and Driver Acceptance

Phase status: CLOSED
Updated: 2026-09-17
Ledger for: [Phase 63.1](phase-63.1.md)
Predecessor: [Phase 63](phase-63.md)
Development item: DEV-031
Repository-full validation: each-phase / SBT full suite in every admitted
changed SBT repository

This is the sole completion ledger for the accepted-contract bridge and real
driver transfer. It consumes, but does not reopen, the Phase 63 contract.

## MCML-63-03: sbt-cozy bridge implementation

Stage Status:

- Current status: DONE (accepted Step evidence)
- Owner: sbt-cozy, coordinated by Cozy Phase 63.1
- Update rule: Mark DONE only after the plugin invokes only the accepted Cozy
  contract and proves installation/incremental behavior for one- and two-CML
  projects.

- [x] Collect all isolated delegated provenance manifests; do not select one by
      order.
- [x] Invoke Cozy's accepted aggregate/rebind operation and install its
      project-level side output atomically.
- [x] Make incremental side-output availability correct for multi-CML projects.
- [x] Add plugin integration coverage for valid two-CML generation, preserved
      one-CML behavior and conflicting-evidence rejection.

Accepted evidence: Cozy commit
`eb4743cd9f4c966ca6762f3ab0a83c37375ba752`; sbt-cozy producer commit
`d22d0c7b0f70d9129fd00392d9663158be48862c`; focused receipts and independent
Step review accepted.

## MCML-63-04: Real driver transfer

Stage Status:

- Current status: CLOSED
- Owner: Cozy Phase 63.1, with simplemodeling-model as external driver
- Update rule: Mark DONE only after normal generation permits the existing
  blocked focused downstream command and its receipt is recorded without
  claiming the downstream Phase's closure.

- [x] Run the existing `simplemodeling-model` focused `EntityIdSpec` through
      normal two-CML generation after MCML-63-03 acceptance.
- [x] Consume the narrow direct authorization
      `authorize-release-and-driver-coordinate` by changing only
      `project/plugins.sbt` from `sbt-cozy` `0.1.16` to `0.1.18-SNAPSHOT`;
      retain the existing Ivy-local-first resolver order.
- [x] Publish the exact accepted sbt-cozy `0.1.18-SNAPSHOT` producer through
      its existing Ivy-local `publishLocal` route, then retain artifact
      checksums and the command receipt.
- [x] Record the exact source/artifact/receipt handoff required for Phase 74.1
      to resume its own producer acceptance: producer commit
      `d22d0c7b0f70d9129fd00392d9663158be48862c`, local artifact checksums,
      command receipt, and final two-source V2 provenance digest.
- [x] Complete focused validation, independent review and each admitted changed
      SBT repository's configured full suite.
- [x] Do not publish, commit or close Phase 74.1 as part of this Phase. The
      authorized Ivy-local sbt-cozy snapshot producer artifact is not a Phase
      74.1 publication and does not close that Phase.

Focused evidence: repair validation receipt
`ff75ee1ae0cbc290e1a590c8336eda1374d564d2410847c89c52101e7e9973c2`, local
artifact refresh receipt
`2eb8812c4d67a2b3ae872c384620afa6702badaa0c45bef95b182fe9ada12001`, normal
driver receipt
`adaad1b9e8da244e9463858240bc7484194d67a014bdae3abefc9c5231e536fb`, and V2
provenance digest
`61afa50c83b58029c99f083978242cc28ba2fa026b5fc6b6c40e60a01710a0cf` are
accepted. The Phase full-review disposition is
`89bb4d811173387cfd7c040c24c7f0b8339af212f8cf86c6e318269a00d97089`.

## Phase closure evidence

- [x] MCML-63-03 and MCML-63-04 have accepted Step commits, focused validation
      and independent review evidence.
- [x] The frozen Phase 63 contract/receipt handoff is consumed unchanged.
- [x] The required SBT full suite passes for every admitted changed SBT
      repository under `split_full_test_policy=each-phase`.

Final full-suite evidence: sbt-cozy
`1a2d59a1ca2eba1fb7cffbff8ed7a300917a8f078df24987997ddcc20712d333`,
simplemodeling-model
`7000984f014359ce2f8ef421a5649532be9bed1e5720f0faa7c04170f3367133`, and
Cozy `015c9019c7d9ba9a575aaf519a1ddb52c6afb4e03edb14ba1c5c5b4727e5698d`.
All are serialized `sbt --batch test` receipts; the closure binding retains
their verified documentation-only freshness evidence. Phase 74.1 remains
outside this closed Phase.
