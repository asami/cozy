# Phase 40: Article and Summary Slide PDF Generation and Currentness

Status: IN PROGRESS

Plan date: 2026-08-29

Dependencies:

- Phase 38 and Phase 39.1 closure, unless the active Phase order is changed by
  explicit authorization; and
- the accepted SmartDox Phase 9 article-media PDF contract and usable
  development or release coordinate.

Phase 40 is active. This plan does not alter the closed Phase 38, Phase 39,
or Phase 39.1 boundaries.

## Split Provenance

On 2026-08-29, the original Phase 40 plan received a pre-split planning gate
of `SPLIT_REQUIRED`. Its conservative whole-Phase estimate was 11.5–14.5
hours: PDF resource contract and deterministic rendering/currentness form one
coherent delivery boundary, while registry mutation and cross-repository
driver acceptance form a second boundary. The user approved the exact ordered
split by invoking `$cncf-split-phase Phase 40`.

1. Phase 40 retains PDF role binding, localized article and summary-slide PDF
   generation, and media receipt/review currentness.
2. Phase 40.1 owns normal and WIP site registration plus SimpleModeling.org
   driver acceptance.

No Stage, Slice, validation receipt, or commit had completed before this
split. `PDF40-01` through `PDF40-04` therefore remain here exactly once;
`PDF40-05` and the registration portion of the original `PDF40-04` move once
to Phase 40.1. This split adds one planning/handoff, validation, review, and
commit boundary. It is justified by the independent atomic-registration and
external-driver acceptance closure, not by profile cost: the expected saving
is a smaller diagnostic surface and a lower-cost execution Phase after the
PDF handoff. The profile distribution is Phase 40 on `gpt-5.6-terra / xhigh`
for the protected PDF boundary, followed by Phase 40.1 on
`gpt-5.6-terra / high` for bounded registration and driver execution.

Phase Plan Gate: PROCEED

- target: approximate-6h packing target; preferred 4–8h band
- planning_demand: protected-decision
- recommended_parent_profile: `gpt-5.6-terra / xhigh`
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: Cozy PDF media-resource role, public-output,
  receipt-identity, and presentation-rendering boundary against the accepted
  SmartDox Phase 9 roles.
- frozen_profile_transition_handoff: Phase 40.1 consumes the accepted PDF
  resource contract, locale/output identities, current media receipts, and
  stale-input rejection semantics.
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 6.0–7.5 hours; within the preferred 4–8h
  band.
- merge_attempts_for_every_sub_4h_child: none; this child is not sub-four-hour.
- adjacent_merge_structural_rejection_evidence: none; this child is not
  sub-four-hour.
- profile_cost_only_rejection_forbidden: true
- short_child_exception: none
- overhead_tradeoff: one extra Phase handoff/review/commit is outweighed by
  separating deterministic PDF construction/currentness from site mutation and
  external driver acceptance.
- agent_reasoning_mode_policy: standard
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 40

## Goal

Make article PDF and summary-slides PDF first-class Cozy media-package outputs
with deterministic generation, review evidence, receipt identity, and
freshness validation.

The public delivery contract contains PDF documents only. Cozy may retain a
generated PPTX as an internal presentation artifact when required by the
renderer, but must not register or publish it as an article download.

## Origin

SimpleModeling.org already uses Cozy media packages to register localized
article infographics and videos with SmartDox. The requested extension adds
two localized documents to the same article-media package:

- an article PDF generated from the SmartDox article authority; and
- a summary-slides PDF generated from the public summary-slide authority.

The summary slides are not the video storyboard or a review deck. Their
semantic authority is a Visual Page set or other accepted Cozy presentation
source aligned with the article and shared infographic.

## Stages

### PDF40-01: Media Contract and SmartDox Binding

Stage Status:
- Current status: COMPLETE
- Owner: Cozy Phase 40
- Update rule: Update when the PDF40-01 checklist state changes.
- Checklist basis: `docs/phase/phase-40-checklist.md#pdf40-01-media-contract-and-smartdox-binding`

P40-01 defines the explicit `article_pdf` and `summary_slides_pdf` resource
roles, exact public paths, locale, and PDF media type without filename or
locale inference. It promotes those SmartDox role bindings into the Cozy
media-package specification and design before the bounded article-PDF adapter.
Focused receipt `P40-01-TEST-005` passed 38 executable specifications, and
the focused closure re-review found no Current Boundary Blocker.

### PDF40-02: Localized Article PDF Build

Stage Status:
- Current status: COMPLETE
- Owner: Cozy Phase 40
- Update rule: Update when the PDF40-02 checklist state changes.
- Checklist basis: `docs/phase/phase-40-checklist.md#pdf40-02-localized-article-pdf-build`

P40-02 binds a closed same-locale infographic authority to each article-PDF
resource without adding an infographic renderer operand. Existing receipt-v2
automatic source evidence makes either authority stale; a source race rejects
the staged PDF before receipt visibility. Focused receipt `P40-02-TEST-003`
passed 42 executable specifications, and the focused closure re-review passed
with no Current Boundary Blocker.

### PDF40-03: Summary Slide PDF Build

Stage Status:
- Current status: OPEN
- Owner: Cozy Phase 40
- Update rule: Update when the PDF40-03 checklist state changes.

- Extend the Cozy presentation route to emit and verify a summary-slides PDF
  from the accepted Visual Page or slide-IR authority.
- Permit PPTX only as a renderer-owned internal artifact; exclude it from
  SmartDox registration and public delivery.
- Verify PDF page count, page order, article/infographic alignment, visual
  legibility, and stale-input rejection.

### PDF40-04: Package Verification and Currentness

Stage Status:
- Current status: OPEN
- Owner: Cozy Phase 40
- Update rule: Update when the PDF40-04 checklist state changes.

- Add both PDFs to media receipts, manifests, review state, and cross-artifact
  verification.
- Verify deterministic receipt identity, exact locale, public-PDF-only
  delivery, and stale-input rejection without mutating a site registry.
- Produce the frozen handoff that Phase 40.1 uses for normal and WIP site
  registration.

## Exclusions

- SmartDox schema or projection implementation, which belongs to SmartDox
  Phase 9.
- [DEV-012](../strategy/cozy-development-strategy.md#9-development-item-status),
  including future SmartDox Markdown-image admission and any standalone Cozy
  PDF-receipt contract; those future contracts are separate from Phase 40's
  scoped media-package receipts, manifests, review state, and currentness
  checks.
- Article prose changes or summary-slide editorial authoring outside the
  selected driver package.
- Public PPTX registration or distribution.
- Any normal or WIP SmartDox site-registration mutation, registry atomicity,
  or SimpleModeling.org driver acceptance; these belong exclusively to Phase
  40.1 after this Phase closes.
- YouTube changes, video regeneration, site deployment, upload, or push.
- Retrofitting every existing SimpleModeling.org article inside the common
  Cozy Phase.

## Handoff to Phase 41

Phase 40 does not implement the integrated explanation-structure confirmation
HTML. Phase 40.1 completes registration and driver acceptance before Phase 41
uses the accepted summary-slide Visual Page package as its representative
driver. Phase 41 may compare its review page identities and order with the
accepted Phase 40 outputs, but must not treat the summary-slide PDF or internal
PPTX as semantic authority.

## Completion Criteria

Phase 40 completes only when both localized PDFs are reproducible current
media-package outputs, PPTX is not exposed as a public article download,
receipts and review state reject stale inputs, focused and full Cozy validation
pass, and independent Phase review closes all Current Boundary Blockers.
Normal/WIP registration and the SimpleModeling.org driver acceptance are
explicitly Phase 40.1 closure criteria, not evidence for this Phase.

## References

- `docs/phase/phase-40-checklist.md`
- `docs/phase/phase-40.1.md`
- `docs/phase/phase-40.1-checklist.md`
- `docs/phase/phase-41.md`
- `docs/spec/media-package.md`
- `docs/design/article-media-publication.md`
- `docs/spec/article-media-publication.md`
- `docs/design/smartdox-site-media-registration.md`
- `docs/spec/smartdox-site-media-registration.md`
- SmartDox `docs/phase/phase-9.md`
