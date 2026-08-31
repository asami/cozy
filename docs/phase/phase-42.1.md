# Phase 42.1: Document Project Evidence, Review Projection, and Driver Acceptance

Status: IN PROGRESS

Plan date: 2026-08-31

Split from: Phase 42 (approved 2026-08-31)

Dependencies:

- Phase 42 closure and its frozen handoff: accepted Document Project and
  Workflow contracts, command/scaffold grammar, Content Core authority
  boundary, Workflow Instance and Work Product roles, criteria, gates, and
  Executable Specifications;
- the accepted Cozy media, receipt, Visual Page, explanation-composition, and
  review-state contracts from Phases 19, 30, 36, and 37;
- Phase 41 closure for the reusable Explanation Structure Review projection.

This is the second planned child of the approved Phase 42 split. It consumes
the Phase 42 public-contract handoff without reopening that kernel.

## Goal

Use the accepted Phase 42 model to derive completion, currentness, review
state, and readiness from exact evidence; retain append-only attempts; render
deterministic review projections and a self-contained dashboard; and accept
the resulting workflow for one standalone directory and one non-Article-8
BoK-hosted or isolated driver.

## DP42-03: Evidence-Derived State and Attempt History

Stage Status:

- Current status: IN PROGRESS
- Owner: Cozy project-state and execution evidence
- Update rule: complete only when state reconstruction, stale propagation,
  append-only attempts, and exact diagnostics pass focused tests.

- Derive completion coverage, artifact currentness, review decision, and
  operation readiness independently from exact source and receipt evidence.
- Report satisfied/total criteria and retain exact missing/not-applicable
  reasons rather than storing an editable percentage.
- Propagate staleness through declared dependency identities, including shared
  infographic consumers, without timestamp inference.
- Record append-only Operation Attempts with exact input, provider/profile,
  output, diagnostic, and receipt identities.
- Prove deleting generated state caches and re-inspecting accepted inputs
  reconstructs the same snapshot.
- Dispatch only one selected registered logical operation; do not infer
  downstream publication or workspace-wide execution.

## DP42-04: Review Projections and Self-Contained Project Dashboard

Stage Status:

- Current status: IN PROGRESS
- Owner: Cozy deterministic Document Project review projection
- Update rule: complete only when all three views, accessibility,
  deterministic output, and read-only authority boundaries pass.

- Generate `core-review.html` that exposes the accepted/candidate core and
  classifies feedback as shared-semantic or artifact-local before write-back.
- Integrate Phase 41's page-flow, page-semantic, and page-display-structure
  logical-chart projection as a selectable Work Product without redefining it.
- Generate `video-review.html` for storyboard/scene intent and rendered
  video review evidence where the video branch is active.
- Generate one self-contained project dashboard with Workflow view, Work
  Product matrix, and Work Product detail.
- Show active/omitted branches, provider bindings, gates, coverage,
  currentness, review, readiness, stale/blocking reasons, and next operations.
- Show shared Work Products once with all producer/consumer edges.
- Separate current snapshot from failed, successful, and superseded attempt
  history.
- Separate project production, workspace integration, aggregate build, and
  external delivery for hosted projects.
- Keep dashboard HTML/CSS/inline-SVG/interaction outside semantic and workflow
  authority and produce deterministic, atomic output.

## DP42-05: Directory and BoK Driver Acceptance and Closure

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy Document Project acceptance
- Update rule: complete only when both drivers, compatibility, full
  validation, focused Phase review, and ledger synchronization pass.

- Accept one standalone directory with local deliverable assembly and explicit
  non-applicable hosted operations.
- Accept one non-Article-8 BoK-hosted or isolated driver while preserving
  existing public article identities and legacy behavior.
- Keep Article 8 out of Phase 42.1 driver acceptance. Its operational rollout
  and acceptance remain the post-Phase-42 `P401-DC-001` follow-up.
- Prove safe SmartDox/public source projection excludes Content Core internals,
  raw media, reviews, receipts, and generated target data.
- Cover partial/current/stale/missing/failed/not-applicable state, omitted
  video, stale propagation, failed attempt retention, and deterministic cache
  reconstruction.
- Prove one AI-assisted core dialogue and `core-review.html` feedback cycle,
  including provider/model evidence and explicit human acceptance.
- Exercise independent branch selection for SmartDox/article PDF,
  summary-slides PDF, infographic PNG, video plus `video-review.html`, and
  Phase 41 Explanation Structure Review HTML.
- Run focused and full serialized Cozy validation and complete one focused
  independent Phase review before closure.

## Split Provenance and Handoff

- Split from Phase 42 by the 2026-08-31 approval. It follows Phase 42 and
  precedes the former strategy successor without renumbering prior phases.
- Cost role: lower-cost execution child. It must consume the frozen Phase 42
  model; it may not redefine its public schemas, Content Core authority, or
  command/scaffold grammar.
- Estimated planning overhead was 1–1.5 hours, while the split avoids 3–5
  hours of likely rework if evidence/dashboard work starts before the public
  kernel stabilizes.
- No under-four-hour child is created, so no merge attempt is required.

## Exclusions

- Revising the Phase 42 public contract kernel, command/scaffold grammar,
  Content Core authority boundary, or Workflow Instance/Work Product model.
- Fully autonomous claim, evidence, relation, narrative, or Visual Pattern
  creation and acceptance. AI-assisted proposal generation with explicit
  review and accepted write-back remains included.
- A general scheduler, daemon, remote workflow service, or arbitrary command
  execution descriptor.
- Mutable workflow status or percentage fields.
- Replacing SmartDox, SVG, Visual Page, Slide, Storyboard, receipt, or review
  authorities.
- Dashboard editing or write-back.
- Implicit BoK registration, hosted PDF-registration evidence, aggregate build,
  publication, deployment, upload, or migration of existing articles.
- Retrofitting Article 7 or earlier articles into Document Projects.
- Expanding or redefining Phase 41.

## Completion Criteria

Phase 42.1 completes only when the same Document Project workflow can be
scaffolded, inspected, planned, verified, selectively executed, and visualized
for both a standalone directory and one non-Article-8 BoK-hosted or isolated
driver;
every intermediate Work Product exposes evidence-derived coverage,
currentness, review, and readiness; the AI-dialogue/`core-review.html` loop
produces only explicitly accepted Content Core authority; artifact feedback is
routed to the correct shared or medium-local authority; required/optional/
disabled branches are exact; SmartDox/article PDF, slides PDF, infographic
PNG, video/`video-review.html`, and Phase 41 Explanation Structure Review HTML
are independently selectable; state and dashboard projections reconstruct
deterministically; stale and omitted branches are exact; existing authorities
and legacy articles remain compatible; full Cozy validation succeeds; and
focused independent Phase review closes all Current Boundary Blockers. Article
8 remains only the post-Phase-42 `P401-DC-001` operational rollout.

## Structural Phase Plan Gate

State: PROCEED

- planning demand: evidence reconstruction, append-only attempts, deterministic
  review/dashboard projection, and bounded two-driver acceptance against a
  frozen public kernel
- parent profile: `gpt-5.6-terra / high`
- implementation profile target: bounded lower-cost execution workers under a
  Terra/high parent when the approved Phase is started
- cost role: lower-cost execution; no expensive public-contract reasoning is
  admitted outside the received Phase 42 handoff
- estimate: 5–7 hours
- predecessor: accepted Phase 42 closure
- successor: the former strategy successor after Phase 42.1 closes

## References

- `docs/phase/phase-42.md`
- `docs/phase/phase-42-checklist.md`
- `docs/phase/phase-42.1-checklist.md`
- `docs/notes/document-project-workflow-management-specification-proposal.md`
- `docs/journal/2026/08/2026-08-30-document-project-content-core-direction.md`
- `docs/spec/media-package.md`
- `docs/design/media-package-operation.md`
- `docs/phase/phase-36.md`
- `docs/phase/phase-37.md`
- `docs/phase/phase-40.md`
- `docs/phase/phase-40.1.md`
- `docs/phase/phase-41.md`
