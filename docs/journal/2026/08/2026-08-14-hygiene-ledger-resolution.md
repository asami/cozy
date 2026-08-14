# Hygiene Ledger Resolution

status=resolved-record
date=2026-08-14
classification=historical-ledger-reconciliation

This journal records the later resolution of two hygiene follow-ups whose
original journals correctly recorded the open state at the time. It does not
rewrite their original observations or reopen their closed phases.

## Resolved: P26-HYG-001 — Type-modeling Authority Reference

- Original record:
  `docs/journal/2026/08/2026-08-04-phase-26-hygiene-follow-up.md`.
- Original observation: `AGENT.md` named a missing repository-local
  `docs/rules/type-modeling.md` authority.
- Resolution: the rule is now maintained once in ai-directive core as
  `core/type-modeling.md`. ai-directive commit
  `a48fcf1d9abf5cba069b815e2a7d42a6f533ecdb` added the shared Scala type
  modeling rule; Cozy integration commit
  `a6a60eb9f6021e655ffb41f0583c7d8d37e4e6fe` adopted it.
- Verification: the current Cozy `ai/directive/core/AGENT.md` rule map refers
  to `type-modeling.md`, and `ai/directive/core/type-modeling.md` exists.
- Final status: RESOLVED. A repository-local duplicate is neither required nor
  desired.

## Resolved: P27-HYG-001 — Arcadia ScalaTest Baseline

- Original record:
  `docs/journal/2026/08/2026-08-11-phase-27-hygiene-follow-up.md`.
- Original observation: Arcadia Test compilation failed on a ScalaTest
  API/dependency mismatch before suites could run.
- Resolution: Arcadia commit
  `62408564db638409723c00e7383d06591b3c0bb3`
  (`Prepare Arcadia 1.0.3-SNAPSHOT test hygiene`) aligned the test baseline,
  imports, and affected fixtures.
- Verification: the subsequent serialized Arcadia full test completed five
  suites with 17 successful tests, no failures, and one ignored test.
- Final status: RESOLVED. It is no longer a current Phase 27 or Phase 28.2
  hygiene follow-up.

## Follow-up

Closed phase documents retain their contemporary validation history. Future
maintenance summaries should cite this resolution record rather than describe
P26-HYG-001 or P27-HYG-001 as open work.
