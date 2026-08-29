# Phase 40: Article and Summary Slide PDF Generation and Currentness

Status: COMPLETE — LOCAL RELEASE COMMIT PENDING

Plan date: 2026-08-29

Dependencies:

- Phase 38 and Phase 39.1 closure, unless the active Phase order is changed by
  explicit authorization; and
- the accepted SmartDox Phase 9 article-media PDF contract and usable
  development or release coordinate.

Phase 40 has passed its fresh final validation and awaits its distinct local
release commit. This plan does not alter the closed Phase 38, Phase 39, or
Phase 39.1 boundaries.

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

Phase 40's generation contract contains PDF documents only. Cozy may retain a
generated PPTX as an internal presentation artifact when required by the
renderer, but must not register or publish it as an article download. A
successful generation does not imply a BoK publication or SmartDox registration;
those are separate operations that may consume an accepted artifact receipt.

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
- Current status: COMPLETE
- Owner: Cozy Phase 40
- Update rule: Update when the PDF40-03 checklist state changes.

#### P40-03-DEC-001: Presentation-owned Summary PDF Contract

Status: ACCEPTED on 2026-08-29 — consumed once for Phase 40/P40-03.

The current `summary_slides_pdf` role is deliberately a generic `prebuilt`
resource. The current presentation renderer emits only PPTX, slide PNGs,
montage, and its renderer manifest; it has no PDF operand or PDF artifact
evidence. Further, a full media build admits prebuilt resources before
presentations. A PDF generated as an undeclared presentation side effect would
therefore lack a closed dependency, ordering, and receipt boundary.

The accepted decision is PDF-first direct rendering: extend the approved
presentation-renderer contract with a direct PDF operand and PDF artifact
evidence; support both the accepted Visual Page and legacy Slide-IR
authorities; and introduce a closed presentation-owned `summary_slides_pdf`
resource binding. A summary-slides PDF is the standard generated output. PPTX
is an optional renderer-owned internal artifact, generated only when the media
descriptor explicitly requests it; it is never publicly published or
registered on this route. Cozy will verify PDF page count/order,
shared-infographic evidence, direct-file safety, and stale-input rejection.
This decision does not add a second PDF converter, alter SmartDox
schema/projection, or begin Phase 40.1 registration.

Decision Resolution Record:

- decision_id: `P40-03-DEC-001`
- answer: user instruction on 2026-08-29: “pptxは要求されたら作る、という扱いにして。”
- selected option: direct PDF as the standard summary-slide output; optional
  internal PPTX only on explicit descriptor request
- affected scope: Cozy Phase 40 / P40-03 only
- authorized next state: PLAN
- consumed: true

#### P40-03-DEC-002: BoK Publication and Direct-SmartDox Boundary

Status: ACCEPTED on 2026-08-29 — consumed once for Phase 40/P40-03.

The normal BoK-public artifact set is article HTML, article PDF, video,
summary-slides PDF, and infographic. PPTX is a useful optional presentation
export, generated only on explicit descriptor request; it is not a normally
published BoK artifact and is not a standard SmartDox site registration target.
Article-slide HTML derived from `deck.md` and video-slide HTML derived from
`storyboard.md` are internal review evidence, not public BoK artifacts.

The stated BoK-public artifact set is a consumer operating profile, not a Cozy
media-generation contract. Cozy generates exactly the artifacts explicitly
requested by a media descriptor and validates their declared dependencies. A
later BoK or direct-SmartDox operation may consume accepted receipts for its
own registration/publication workflow; it does not redefine, imply, or trigger
generation. SimpleModeling.org is one local driver of such an operation.

Decision Resolution Record:

- decision_id: `P40-03-DEC-002`
- answer: user instructions on 2026-08-29 defining direct-SmartDox registration
  for article HTML, article PDF, video, summary-slides PDF, and infographic,
  and stating that ordinary BoK operation does not publish PowerPoint
- selected option: PDF-first Phase 40 output; optional non-public PPTX export;
  later BoK/direct-SmartDox consumer operation for the normal BoK-public
  artifact set
- affected scope: Cozy Phase 40 / P40-03 only; no Phase 40.1 execution
- authorized next state: PLAN
- consumed: true

#### P40-03-DEC-003: Generation and Publication Separation

Status: ACCEPTED on 2026-08-29 — consumed once for Phase 40/P40-03.

Cozy must generate and verify exactly the artifacts requested by its descriptor.
It must not infer a BoK workflow, a public release, or SmartDox registration
from an artifact type. BoK publication and direct-SmartDox registration are
separate consumer operations over already accepted artifacts and receipts.
This Phase implements only the requested article-PDF, summary-slides-PDF, and
optional internal-PPTX generation behavior.

Decision Resolution Record:

- decision_id: `P40-03-DEC-003`
- answer: user instruction on 2026-08-29: “Cozyが各種成果物を作れる、という話と、
  それぞれの成果物をBoKとして公開する運用が想定されている、という話は分けて考えて。”
- selected option: descriptor-driven generation without implied BoK publication
  or SmartDox registration
- affected scope: Cozy Phase 40 / P40-03 only; no publication or registration
  operation is started
- authorized next state: PLAN
- consumed: true

- Extend the Cozy presentation route to emit and verify a summary-slides PDF
  from the accepted Visual Page or slide-IR authority.
- Permit PPTX only as a renderer-owned internal artifact; exclude it from
  SmartDox registration and public delivery.
- Verify PDF page count, page order, article/infographic alignment, visual
  legibility, and stale-input rejection.

P40-03A implementation is confined to descriptor-driven Cozy generation: a
`summary-slides-pdf` resource uses the existing business presentation renderer
to create a direct staged PDF and only requests an internal PPTX when the
descriptor names its sidecar path. It does not call BoK publication or
SmartDox registration. The direct descriptor-driven implementation is complete:
`P40-03A-TEST-028` passed 45 executable specs (3 suites, 0 failed), and focused
closure re-review `P40-03A-RE-REVIEW-003` passed with all P40-03A blockers closed.
The P40-03 Step acceptance commit
`fa7233d4cbf24dcb33b3abe69f01b975796c84b3` completed the implementation, so
P40-03 is complete. Phase 40 remains in progress pending its final full
validation, independent Phase review, and release closure.

### PDF40-04: Package Verification and Currentness

Stage Status:
- Current status: COMPLETE
- Owner: Cozy Phase 40
- Update rule: Update when the PDF40-04 checklist state changes.

#### P40-04-DEC-001: Closed PDF Package Review State

Status: ACCEPTED on 2026-08-30 — consumed once for Phase 40/P40-04.

Cozy records the current `article_pdf` and `summary_slides_pdf` document
resources in a deterministic package-level `cozy.media.pdf-review-state.v1`
state reconstructed from the existing media-package receipt and accepted
artifacts. It does not introduce a new PDF receipt. The state binds each PDF's
role, locale, public PDF path, output hash, and receipt identity; a direct
summary-slides entry additionally binds its already-verified renderer-manifest
identity. Existing `cozy.media.review-state.v1` and
`cozy.media.cross-review.v1` remain presentation/video-only and unchanged.
The new state performs currentness verification only: it neither publishes,
registers with SmartDox, exposes PPTX, nor records semantic, visual, or
audiovisual approval.

Decision Resolution Record:

- decision_id: `P40-04-DEC-001`
- answer: user instruction on 2026-08-30: “review stateに記録するようにして”
- selected option: add the deterministic receipt-derived PDF package review
  state without expanding the presentation/video review-state or cross-review
  APIs
- affected scope: Cozy Phase 40 / P40-04 only
- authorized next state: PLAN
- consumed: true

#### P40-04-CONT-001: Receipt-derived PDF Review-State Boundary

The `article_pdf` and `summary_slides_pdf` resources remain current document
resources whose receipt-v2 evidence is authoritative. Cozy reconstructs the
closed PDF review state from the accepted candidate receipt; selected public
PDF verification and publication preflight require that state without creating
a second receipt. Direct summary-PDF entries bind the already verified
renderer-manifest identity; prebuilt summaries keep ordinary receipt-derived
currentness without direct renderer evidence. The existing
`cozy.media.cross-review.v1` remains a closed presentation/Storyboard API and
is not broadened to PDF inspection.

The P40-04 implementation is complete. It records both PDF roles with exact
locale, public path, output hash, and receipt input-set identity; it rejects
stale output, locale, public-path, receipt, or direct-summary renderer
evidence without site mutation. It also rejects an article-PDF output that
escapes the descriptor root before renderer invocation or output replacement.
Focused receipt `P40-04-TEST-007` passed 63 executable specifications in five
suites with no failures, and focused closure re-review `P40-04-RE-REVIEW-001`
closed both P40-04 Current Boundary Blockers. The local Step acceptance commit
`77ab9d2dbf7aa58ca6563c33b1756396643c0566` contains the frozen implementation;
Phase 40.1 alone owns normal/WIP registration and driver acceptance.

## Phase Closure Record

The independent Phase 40 review found one Current Phase Blocker and one
nonblocking Hygiene item. `CPB-P40-001` found that an article-PDF output could
lexically remain below the descriptor root while an existing intermediate
ancestor symlink redirected rendering outside it. Phase repair cycle 1 rejects
every existing symlinked ancestor below the descriptor root before directory
creation, staging, renderer invocation, or output replacement. Its added
Executable Specification proves no renderer call occurs and the external prior
PDF remains unchanged. Focused receipt
`p40-cpb001-val-001-20260829t213016z` passed all 15
`CozyMediaPdfSpec` scenarios; the focused closure re-review sealed
`CPB-P40-001` as CLOSED.

`HYG-P40-001` remains intentionally OPEN in the canonical
`docs/journal/2026/08/2026-08-30-phase-40-hygiene-follow-up.md`: it records
only missing `which` chapter subdivisions in otherwise sound large Executable
Specifications. It does not change this Phase's behavior, containment, or
currentness guarantees.

### P40-FINAL-CPB-001: Publication Currentness Integration

Status: CLOSED — final full validation passed on 2026-08-30; local release commit pending.

The one permitted final serialized Cozy suite
`82822-20260829T214854Z` compiled successfully but failed 20 of 1,568 tests
(1,548 succeeded; 8 canceled; 120 suites completed). The failures are limited
to `CozyArticleMediaPublicationOrchestrationSpec`,
`CozyArticleMediaInfographicCommandSpec`, and `CozyMediaPublicationSpec`.
Their shared pre-commit path now rejects fixture resources such as `summary-ja`
or `summary-en` through `CozyMediaReceipt.requireCurrent` before the existing
publication checks can exercise their expected destination, symlink, stale,
and build-manifest diagnostics. Because Phase 40 directly added PDF
receipt/currentness state to this publication-adjacent boundary, the failure is
a Current Phase Blocker rather than Hygiene.

The user authorized `P40-FINAL-CPB-001` source/spec repair. The repair keeps
the authoritative receipt and `cozy.media.pdf-review-state.v1` gates for
selected qualifying document PDFs only: their article-media role is exactly
`article_pdf` or `summary_slides_pdf`. Image-only public resources retain the
prior publication-preparation path, while `CozyMedia.publish` remains
unchanged. Focused receipt `89900-20260829T220058Z` passed 70 specifications
in four suites with no failures, including the previously failing publication
specifications and a PDF-only preflight scenario.

The first focused review request was not a complete typed manifest and is a
pre-review governance result, not a semantic re-review or a consumed repair
cycle. The accepted `cncf.focused-rereview-manifest.v1` bundle
`fa82c84b46ef13787ce5f89b50f80620916a5268022d807184f0b06e4fdadc99`
then sealed this repair clean: `P40-FINAL-CPB-001` is CLOSED, no new Current
Phase Blocker was found, and no second full Phase review is required. Fresh
final receipt `18266-20260829T230400Z` then passed all 1,569 Cozy
specifications in 120 suites with zero failures. The distinct local release
commit is the remaining closure operation.

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

Phase 40 is complete when both localized PDFs are reproducible current
media-package outputs, PPTX is not exposed as a public article download,
receipts and review state reject stale inputs, focused and final full Cozy
validation pass, and independent Phase review closes all Current Boundary
Blockers. Normal/WIP registration and the SimpleModeling.org driver acceptance
are explicitly Phase 40.1 closure criteria, not evidence for this Phase.

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
