# Phase 87: Claim-Linked Document Figures and Tables

status=closed
closed_at=2026-10-25
planned_at=2026-10-25
checklist=[Phase 87 Checklist](phase-87-checklist.md)
spec=[Explanatory Figures and Tables](../spec/document-project-explanatory-figures-and-tables.md)

## Goal

Represent figures and tables as authored body explanations of explicit Core
claims. Keep logic-tree labels concise and show the corresponding figure/table
in the explanation region. Carry the same localized content through Document
confirmation/reader HTML, the derived SmartDox article and article PDF.

The initial user direction on 2026-10-25 authorized specification and Phase
planning only. The later explicit `cncf-goal-phase87` invocation authorizes
current execution under the frozen parent plan. P87-01 source authoring and
P87-02 admission/materialization are accepted. P87-02 passed 76 specifications
across seven suites and, after its claims-only backlink repair, 23 specifications
across the two affected suites. Protected Step review and independent focused
closure `cozy-p87-02-focused-source-review-20261025@1` accepted the implementation.
P87-03 native CLI/local-build integration passed 51/51 specifications across
five suites and protected review `cozy-p87-03-review-20261025@1` PASS.
P87-04 native article/PDF continuity passed 44/44 specifications across six
suites, including actual JA/EN PDFs, used English raster rebuild and current reuse.
Both final PDFs were visually inspected by the parent and independent reviewer;
Step review `cozy-p87-04-review-20261025@1` PASS. P87-05 integrated acceptance passed 47/47 specifications across four suites
and independent Step review `cozy-p87-05-review-20261025@1` PASS. Its ordinary
FULL [executable specification](../../src/test/scala/cozy/document/CozyDocumentFigureTableAcceptanceSpec.scala)
connects the three native HTML targets for JA and EN; accepted P87-04 native PDF
evidence remains separately attributed. All 24 checklist criteria are satisfied
under the local release binding `cozy-p87-release-20261025@1`; the closed
projection becomes effective only after its distinct native release commit and
closure receipt are verified.

## Existing foundation and capability gap

- [Phase 58.2](phase-58.2.md) supplies Document v2, typed Core references,
  localized labels and interactive confirmation; Summary diagrams are a
  separate explicit selection and are not body figure/table blocks.
- [Phases 60 and 60.1](phase-60.1.md) supply the native structure/reader/article
  HTML targets and the Core -> Document -> derived article authority boundary.
- [Phase 40](phase-40.md) supplies the existing native article-PDF route.
- At Phase entry the Document loaders admitted five block kinds. P87-02 adds
  Figure/Table to unreleased v2, strict claim/table admission, contained raster
  materialization, pure HTML output and Summary omission traversal. Existing
  v1 remains unchanged. P87-03 supplies native command/build connections and
  used-image dependency handling. P87-04 supplies native article image
  dependencies and accepted paired article/PDF continuity; existing
  five-category `coreRefs` retain meaning.

These are capability dependencies, not instructions to reopen their closed
Phases or to modify existing priorities.

## Scope and ownership

Cozy owns the additive unreleased Document v2 extension, strict
admission, used-asset handoff, target dependency declarations, HTML projections
and integration through the existing article/PDF routes. Existing v1 behavior
and v2 documents without new blocks remain unchanged.

Core retains locale-independent meaning; Documents retain localized figures,
captions, alternative text, table headings/rows/cells and explicit references.
No image paths, table layout or renderer configuration is added to Core.
No new public build command, parallel authoring DSL or PDF engine is planned.

Only Cozy is the planned implementation repository. Confirm upstream readers'
actual image/table capabilities before fixing their syntax. A genuine
SmartDox/toolchain gap requires an explicit owned handoff, not an application
workaround or implicit external edit. This Phase does not authorize shared
skill changes or external delivery.

## Delivery Steps

| Step | Observable outcome | Checklist |
| --- | --- | --- |
| P87-01 | Freeze the minimal Figure/Table shape and actual asset/SmartDox/PDF capabilities using one paired JA/EN representative source set. | P87-01 |
| P87-02 | Admit both block kinds with exact claim references, table shape and declared asset inputs; preserve existing traversal/coverage/omission contracts. | P87-02 |
| P87-03 | Read both explanations in native confirmation/reader HTML and navigate between Core and their exact body blocks. | P87-03 |
| P87-04 | Reflect the accepted figure/table into the ordinary SmartDox article and render actual JA/EN article PDFs with correct used-asset dependencies. | P87-04 |
| P87-05 | Accept the complete representative route, existing-document regression, required validation/review and final canonical closure. | P87-05 |

Each Step is a usable integrated outcome, not an independent helper review.
The frozen execution plan retains these connected Steps. P87-01 freezes the
contract and representative source set; it makes no renderer or test PASS claim.

## Representative acceptance

1. A tier comparison figure is explicitly associated with the Core claim that
   each tier balances performance and security; the authored figure supplies
   its chosen asset, meaningful caption and alternative text in each locale.
2. A protection table is explicitly associated with the coverage claim and
   carries protection target, mechanism and application-location columns.
   Comparisons and relationships retain their authored Core identities.
3. Selecting the corresponding Core Step highlights/reveals the figure/table
   in its body explanation; tree nodes remain short. Reader backlinks are exact.
4. JA and EN confirmation/reader HTML, derived articles and actual article
   PDFs preserve each locale's selected images, captions, headings, rows and
   cells. There is no inferred translation, content, or asset fallback.
5. A used figure change rebuilds the targets that actually consume it;
   unrelated assets and independently current article inputs remain unaffected.
6. Existing sources without figure/table blocks retain their behavior. No
   hash-based control or new asset-preservation protocol is introduced.

## Validation and closure obligations

Use focused executable specifications for admission, traversal, asset
materialization, both HTML projections, dependencies and article handoff.
Actually render and inspect the selected paired JA/EN article PDFs; do not
substitute a source oracle or hand-assembled output for that route.

Keep fast regressions in ordinary FULL and expensive native/large cases under
the existing HEAVY tags. The selected representative PDF route remains an
explicit acceptance obligation. At implementation closure, retain proportional
Step reviews, the sole comprehensive Phase review with any focused repair
closure, required final Cozy FULL validation, exact commits and canonical
checklist/status updates. The accepted closure below records their satisfaction;
source authoring alone waives none.

## Non-goals

No technical claims about unspecified actual tiers, generic chart engine,
table formulas/merged-cell editor, Summary/slide/video feature expansion,
automatic translation, publication, public-URL deployment, upstream code edits,
or changes to the closed Phase 71/78 acceptance history.

## Accepted local closure — 2026-10-25

All five delivery Steps are accepted and locally committed:

| Step | Local acceptance commit |
| --- | --- |
| P87-01 | `aadb4b1b9ec65c493c3624b28bb1e7f9ffd6cff2` |
| P87-02 | `d73bc9b3e2d58934b9ac740de1f3203f777e512f` |
| P87-03 | `6a82240b1b01b2a040b43ffae8d4a38098e93434` |
| P87-04 | `a23ec0f0bd36571f6115847c2c09754e2a233f03` |
| P87-05 | `a310f23b5628858fab40fdab3da43457b392b431` |

The sole comprehensive review `cozy-p87-full-review-20261025@1` accepted the
complete original-base-to-Step-commit boundary with no blockers. Final ordinary
Cozy `test` succeeded: 3,972 passed, zero failed, 268 completed suites, no aborted
suites, SBT/wrapper exits 0 and released serial lock. Its 23 existing cancellations
are 18 opt-in warehouse upload/declaration cases and five legacy version-inventory
ownership handoffs; none is claimed as executed acceptance. No Phase 87 case was
cancelled. The selected native PDF HEAVY scenario passed separately, including
actual JA/EN PDFs, used English image rebuild and final visual inspection. No
other unexecuted HEAVY combination is claimed.

The original base remains `172ac2f1dbf619a448af32097d39f3bfc7c2a699`; the same
Goal, PLAN epoch, review lineage, failed attempts, permission refusal and later
explicit approval remain recorded. Phase closure is the distinct local release
binding `cozy-p87-release-20261025@1`, effective after its native commit and
closure receipt are verified. No successor is selected by this closure.

Nonblocking accepted follow-ups remain open in the canonical
[Hygiene journal](../journal/2026/10/2026-10-25-phase-87-hygiene-follow-up.md)
(HYG-P87-001/002) and
[Development Candidate journal](../journal/2026/10/2026-10-25-phase-87-development-candidate-follow-up.md)
(DEV-P87-001). The latter owns SmartDox's English automatic figure/table
prefixes; authored localized captions and table content are accepted unchanged.

Inspection evidence remains outside Git. Planned local archive location:
`asamiair:~/backup/journal/2026/10/cozy/phase-87/cozy-p87-start-6f520c5b-b570-46cf-8e95-beda95f136ee/`.
Default retention is through 2026-11-24; actual archive availability is recorded
by the local storage index after the producing workflow completes.
