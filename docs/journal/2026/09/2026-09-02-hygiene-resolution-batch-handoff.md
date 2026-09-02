# Hygiene Resolution Batch Handoff

Status: COMPLETE
Created: 2026-09-02
Source Repository: /Users/asami/src/dev2025/cozy
Target Repositories: /Users/asami/src/dev2025/cozy
Suggested Invocation: `$cncf-goal-hygiene /Users/asami/src/dev2025/cozy/docs/journal/2026/09/2026-09-02-hygiene-resolution-batch-handoff.md`

## Purpose

Resolve the recorded Cozy maintenance backlog without changing product
behavior, public contracts, schemas, diagnostics, persistence, transport,
security, lifecycle, or any completed Phase boundary. Source-size work is a
physical, package-private decomposition only; every executable-specification
change is navigation or deterministic-fixture containment only.

## Included Hygiene

| ID | Source | Work Package | Required outcome |
| --- | --- | --- | --- |
| HYG-001 | `2026-08-26-phase-30-hygiene-follow-up.md` | HP-001 | Refresh only the stale source-history header. |
| HYG-42-001 | `2026-08-31-phase-42-hygiene-follow-up.md` | HP-001 | Refresh only the two stale source-history headers. |
| HYG-P37-RR-001 | `2026-08-28-phase-37-hygiene-follow-up.md` | HP-002 | Remove only proven-unused imports. |
| HYG-P38-004, HYG-P38-005 | `2026-08-28-phase-38-hygiene-follow-up.md` | HP-002 | Delete only proven-unreferenced private helpers. |
| HYG-P38-002, HYG-P38-003, HYG-P38-006, HYG-P38-007, HYG-P391-001, HYG-P401-001 | Phase 38/39.1/40.1 Hygiene journals | HP-003 | Reconcile stale or misleading documentation/reference records without changing normative meaning; retire the obsolete absent LauncherSpec record. |
| HYG-P36-02A-001, HYG-P40-001, HYG-P42.1-001, HYG-LUI43-RR-002 | Phase 36/40/42.1/43 Hygiene journals | HP-004 | Add navigation-only `which` grouping while retaining every scenario, GWT boundary, and assertion. |
| HYG-LUI43-RR-001 | `2026-09-02-phase-43-hygiene-follow-up.md` | HP-005 | Make review-spec temporary fixture retention/cleanup deterministic under `target/`. |
| HYG-P36-02B-001, HYG-P36-02B-003 | `2026-08-27-phase-36-hygiene-follow-up.md` | HP-006 | Split Visual Page parsing/validation physically below the source-size threshold. |
| HYG-P36-02B-002 | `2026-08-27-phase-36-hygiene-follow-up.md` | HP-007 | Split the Media dispatcher physically below the source-size threshold. |
| HYG-36-04-001 | `2026-08-27-phase-36-hygiene-follow-up.md` | HP-008 | Split Storyboard parsing physically below the source-size threshold. |
| HYG-LUI43-02-001 | `2026-09-02-phase-43-hygiene-follow-up.md` | HP-009 | Split Logical UI support code physically below the source-size threshold. |

## Frozen Boundary

- Allowed repository: `/Users/asami/src/dev2025/cozy`.
- Preserve paths: `build.sbt` and
  `src/test/scala/cozy/compatibility/Phase51Cv07DevelopmentReleaseAcceptanceSpec.scala`, plus every path outside the Work Package targets below.
- Allowed behavior change: none.
- Prohibited expansion: feature work, public API/contract change, schema,
  persistence, transport, security, lifecycle, diagnostic vocabulary, Phase
  execution, external operation, publication, deployment, upload, or push.

## HP-001 — Source-history headers

- Hygiene IDs: HYG-001, HYG-42-001.
- Repository: `/Users/asami/src/dev2025/cozy`.
- Targets: `src/main/scala/cozy/video/CozyVideoReviewEvidence.scala`,
  `src/main/scala/cozy/Cozy.scala`, and
  `src/main/scala/cozy/scaffold/CozyHelpText.scala`.
- Allowed repair: canonical `@version` history-only updates using repository
  header rules.
- Prohibited expansion: code, import, or semantic documentation changes.
- Focused validation: full-file version-marker scan and `git diff --check`.
- Dependencies: None.

## HP-002 — Proven dead source cleanup

- Hygiene IDs: HYG-P37-RR-001, HYG-P38-004, HYG-P38-005.
- Repository: `/Users/asami/src/dev2025/cozy`.
- Targets: `src/main/scala/cozy/media/CozyExplanation.scala`,
  `src/main/scala/cozy/bok/CozyBokSiteDocument.scala`, and
  `src/main/scala/cozy/bok/CozyBokGlossaryPages.scala`.
- Allowed repair: remove only the recorded unused imports and helpers.
- Prohibited expansion: output, parser, codec, page, or package reorganization.
- Focused validation: no-reference scan and serialized `compile`.
- Dependencies: HP-001.

## HP-003 — Documentation and obsolete-record reconciliation

- Hygiene IDs: HYG-P38-002, HYG-P38-003, HYG-P38-006, HYG-P38-007,
  HYG-P391-001, HYG-P401-001.
- Repository: `/Users/asami/src/dev2025/cozy`.
- Targets: `docs/phase/README.md`, `docs/strategy/cozy-development-strategy.md`,
  `docs/design/bok-metadata-finalization.md`,
  `docs/design/bok-sie-information-handoff.md`,
  `docs/design/bok-sie-integration-contract.md`,
  `docs/spec/bok-metadata-finalization.md`,
  `docs/spec/bok-generated-knowledge-boundary.md`,
  `docs/spec/smartdox-site-media-registration.md`, and the Phase 38/39.1/40.1
  source journals.
- Allowed repair: factual status/reference/table wording and a source-record
  closure that records the absent `CozyLauncherSpec.scala` target.
- Prohibited expansion: changing a normative rule, diagnostic, field name,
  or external dependency contract.
- Focused validation: exact wording/reference scans and `git diff --check`.
- Dependencies: HP-001.

## HP-004 — Executable-specification navigation

- Hygiene IDs: HYG-P36-02A-001, HYG-P40-001, HYG-P42.1-001,
  HYG-LUI43-RR-002.
- Repository: `/Users/asami/src/dev2025/cozy`.
- Targets: `src/test/scala/cozy/media/CozyVisualPageSpec.scala`,
  `src/test/scala/cozy/media/CozyMediaPdfSpec.scala`,
  `src/test/scala/cozy/media/CozyMediaPdfReviewStateSpec.scala`,
  `src/test/scala/cozy/media/CozyMediaReceiptSpec.scala`,
  `src/test/scala/cozy/document/CozyDocumentProjectSpec.scala`, and
  `src/test/scala/cozy/ui/CozyLogicalUiReviewSpec.scala`.
- Allowed repair: `which` grouping and local documentary structure only.
- Prohibited expansion: scenario/fixture/assertion/GWT/behavior changes.
- Focused validation: the six named executable specifications.
- Dependencies: HP-002.

## HP-005 — Deterministic Logical UI review fixtures

- Hygiene IDs: HYG-LUI43-RR-001.
- Repository: `/Users/asami/src/dev2025/cozy`.
- Targets: `src/test/scala/cozy/ui/CozyLogicalUiReviewSpec.scala`.
- Allowed repair: fixture containment and deterministic cleanup below `target/`.
- Prohibited expansion: review output, currentness, security, or assertion changes.
- Focused validation: `cozy.ui.CozyLogicalUiReviewSpec`.
- Dependencies: HP-004.

## HP-006 — Visual Page physical split

- Hygiene IDs: HYG-P36-02B-001, HYG-P36-02B-003.
- Repository: `/Users/asami/src/dev2025/cozy`.
- Targets: `src/main/scala/cozy/media/CozyVisualPage.scala` and new
  `src/main/scala/cozy/media/CozyVisualPageParsing.scala`.
- Allowed repair: package-private parser/validation extraction that leaves the
  accepted public and package-visible behavior intact and reduces the original
  source below 1,000 lines.
- Prohibited expansion: grammar, validation, diagnostic, identity, CLI, or
  Visual Page contract changes.
- Focused validation: `cozy.media.CozyVisualPageSpec`,
  `cozy.media.CozyVisualPageBindingSpec`, and
  `cozy.media.CozyVisualPagePreviewSpec`.
- Dependencies: HP-002.

## HP-007 — Media dispatcher physical split

- Hygiene IDs: HYG-P36-02B-002.
- Repository: `/Users/asami/src/dev2025/cozy`.
- Targets: `src/main/scala/cozy/media/CozyMedia.scala` and new
  `src/main/scala/cozy/media/CozyMediaDispatcher.scala`.
- Allowed repair: package-private dispatch extraction that reduces the original
  source below 1,000 lines.
- Prohibited expansion: command, migration, diagnostic, or output behavior.
- Focused validation: `cozy.media.CozyMediaSpec` and
  `cozy.media.CozyMediaPresentationMigrationSpec`.
- Dependencies: HP-006.

## HP-008 — Storyboard physical split

- Hygiene IDs: HYG-36-04-001.
- Repository: `/Users/asami/src/dev2025/cozy`.
- Targets: `src/main/scala/cozy/video/CozyVideoStoryboard.scala` and new
  `src/main/scala/cozy/video/CozyVideoStoryboardParsing.scala`.
- Allowed repair: package-private parser extraction that reduces the original
  source below 1,000 lines.
- Prohibited expansion: Storyboard v1/v2 grammar, review evidence, or diagnostics.
- Focused validation: `cozy.video.CozyVideoStoryboardSpec`,
  `cozy.video.CozyVideoStoryboardBuildSpec`, and
  `cozy.video.CozyVideoStoryboardReviewSpec`.
- Dependencies: HP-007.

## HP-009 — Logical UI physical split

- Hygiene IDs: HYG-LUI43-02-001.
- Repository: `/Users/asami/src/dev2025/cozy`.
- Targets: `src/main/scala/cozy/ui/CozyLogicalUi.scala` and new
  `src/main/scala/cozy/ui/CozyLogicalUiProjection.scala`.
- Allowed repair: package-private projection/support extraction that reduces
  the original source below 1,000 lines.
- Prohibited expansion: ComponentRole, Workflow, screen, review HTML,
  currentness, authority, security, or diagnostic behavior.
- Focused validation: `cozy.ui.CozyLogicalUiSpec`,
  `cozy.ui.CozyLogicalUiSemanticsSpec`,
  `cozy.ui.CozyLogicalUiReviewSpec`, and
  `cozy.ui.CozyLogicalUiPhase43AcceptanceSpec`.
- Dependencies: HP-005.

## Final Focused Review

- Exact target set: every HP-001 through HP-009 target and every included
  source journal plus this batch journal.
- Required checks: every ID/outcome, whole-target naming/header/spec/document
  hygiene, behavioral preservation, focused evidence, preserved user paths,
  and absence of protected-boundary expansion.
- Failure policy: stop without commit; no automatic review-fix/re-review loop.

## Final Full-Validation Gate

1. `/Users/asami/src/dev2025/cozy: sbt --batch test`

Run the full suite once on the reviewed tree through the serialized SBT route.
Stop on failure.

## Validation Blocker and Phase Separation

- Final validation receipt `68771-20260901T211250Z` ran
  `sbt --batch test` on the reviewed tree: 1,676 succeeded, 1 failed, 8
  canceled, and 126 suites completed. The failing specification is
  `cozy.modeler.ModelerScalaGenerationSpec`.
- The observed generated `Exhibition` Entity contained an empty
  `_store_record_attributes` vector because the pre-Phase-44 SimpleModeler
  process-global declared-type registry could be cleared and replaced by
  another parallel model transformation. The same pre-repair tree passed the
  focused specification and the complete 1,677-test suite when suite
  parallelism was disabled (diagnostic receipt `85815-20260901T214627Z`). This
  confirmed a shared-state isolation defect rather than a persistence-metadata
  classification defect.
- The issue was transferred to [Phase 44](../../../phase/phase-44.md),
  `DEV-015`; it was not absorbed into this behavior-preserving Hygiene batch.
- The independent final focused Hygiene review found only stale current-task
  Scala version headers. The required comment-only repair and fresh focused
  re-review passed without behavioral, contract, fixture, or source-split drift.
- This batch's final normal-parallel gate passed after that review and repair:
  `P44-HYG-FINAL-VAL-001` / `86195-20260902T004624Z` — 1,677 tests / 126
  suites / 0 failures. Disabling parallel execution remains diagnostic evidence
  only and was not used for closure.

## Completion Contract

- The focused review, its bounded header re-review, and final full validation
  passed before this closure record.
- Every included source record is `RESOLVED` with batch, validation, and local
  acceptance-commit linkage.
- This `COMPLETE` state is authoritative only in the accepted local closure
  commit; no push, publication, deployment, upload, or external operation is
  part of the batch.
- Do not absorb Development Candidates or new unrelated Hygiene.

## Non-goals

- Any behavior or contract change.
- Work outside Cozy, publication, deployment, upload, push, and external
  consumer/driver execution.

## Source Marker Verification

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-001
Handoff Journal: cozy:docs/journal/2026/09/2026-09-02-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-09-02

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-42-001
Handoff Journal: cozy:docs/journal/2026/09/2026-09-02-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-09-02

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P37-RR-001
Handoff Journal: cozy:docs/journal/2026/09/2026-09-02-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-09-02

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P38-002
Handoff Journal: cozy:docs/journal/2026/09/2026-09-02-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-09-02

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P38-003
Handoff Journal: cozy:docs/journal/2026/09/2026-09-02-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-09-02

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P38-004
Handoff Journal: cozy:docs/journal/2026/09/2026-09-02-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-09-02

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P38-005
Handoff Journal: cozy:docs/journal/2026/09/2026-09-02-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-09-02

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P38-006
Handoff Journal: cozy:docs/journal/2026/09/2026-09-02-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-09-02

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P38-007
Handoff Journal: cozy:docs/journal/2026/09/2026-09-02-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-09-02

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P391-001
Handoff Journal: cozy:docs/journal/2026/09/2026-09-02-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-09-02

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P40-001
Handoff Journal: cozy:docs/journal/2026/09/2026-09-02-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-09-02

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P401-001
Handoff Journal: cozy:docs/journal/2026/09/2026-09-02-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-09-02

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P42.1-001
Handoff Journal: cozy:docs/journal/2026/09/2026-09-02-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-09-02

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-LUI43-RR-001
Handoff Journal: cozy:docs/journal/2026/09/2026-09-02-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-09-02

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-LUI43-RR-002
Handoff Journal: cozy:docs/journal/2026/09/2026-09-02-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-09-02

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-LUI43-02-001
Handoff Journal: cozy:docs/journal/2026/09/2026-09-02-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-09-02

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P36-02A-001
Handoff Journal: cozy:docs/journal/2026/09/2026-09-02-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-09-02

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P36-02B-001
Handoff Journal: cozy:docs/journal/2026/09/2026-09-02-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-09-02

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P36-02B-002
Handoff Journal: cozy:docs/journal/2026/09/2026-09-02-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-09-02

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-P36-02B-003
Handoff Journal: cozy:docs/journal/2026/09/2026-09-02-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-09-02

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-36-04-001
Handoff Journal: cozy:docs/journal/2026/09/2026-09-02-hygiene-resolution-batch-handoff.md
Handed Off On: 2026-09-02
