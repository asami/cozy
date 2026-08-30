# Phase 42: Document Project Workflow Management and Visibility

Status: PLANNED; NOT STARTED

Plan date: 2026-08-30

Dependencies:

- the accepted Cozy media, receipt, Visual Page, explanation-composition, and
  review-state contracts from Phases 19, 30, 36, and 37;
- Phase 41 closure for the reusable Explanation Structure Review projection.

This plan assigns Phase 42 but does not activate it or alter any active or
earlier planned Phase boundary.

## Goal

Make a Document Project manageable as one evidence-derived workflow and make
the completion, currentness, review state, and readiness of every intermediate
Work Product visible through inspect/plan output and a deterministic,
self-contained dashboard.

Support the primary authoring loop in which a user develops an idea through
AI-assisted dialogue, accumulates accepted proposals in Content Core, reviews
that core through `core-review.html` (内容確認HTML), and returns semantic feedback to
the core before activating the required or optional artifact branches.

Phase 42 admits the minimum Content Core and Document Project contract needed
to establish shared authority, Work Product dependencies, and stale
propagation. It preserves medium-specific authoring authorities and existing
receipts.

## DP42-01: Contract Kernel and Command Surface

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy Document Project design and specification
- Update rule: complete only when schemas, authority boundaries, command
  grammar, diagnostics, persistence, and compatibility are frozen.

- Promote the accepted proposal into normative design and specification.
- Freeze `cozy.document-project.v1`, `cozy.document-workflow.v1`,
  `cozy.document-project-state.v1`, and
  `cozy.document-operation-attempt.v1` responsibilities.
- Define the minimal Content Core identity and dependency boundary required by
  the workflow without broad semantic automation.
- Freeze AI-assisted core proposal, provider/model provenance, review,
  acceptance, and feedback-write-back boundaries. A raw model response must
  never become Content Core authority without accepted review evidence.
- Freeze `required`, `optional`, and `disabled` artifact-branch dispositions
  and their completion/omission semantics.
- Freeze `cozy document-project inspect|plan|dashboard|verify|run` behavior,
  output grammar, atomicity, and failure semantics.
- Freeze `cozy document-project scaffold` so it atomically creates the
  canonical authored `*.dox/` package, resolves rather than copies the common
  workflow, and refuses merge/overwrite.
- Preserve existing software Project knowledge-package and `cozy media`
  command meanings without ambiguous dispatch.

## DP42-02: Workflow Instance and Work Product Model

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy Document Project normalization
- Update rule: complete only when the reusable DAG, profile activation,
  bindings, Work Products, criteria, and gates have Executable Specification.

- Resolve one reusable Workflow Definition into a project-specific Workflow
  Instance without copying the common DAG into the project descriptor.
- Admit the closed Work Product roles `authority`, `plan`, `candidate`,
  `review-projection`, `deliverable`, and `receipt`.
- Bind stable Work Product IDs to producer/consumer operations, typed
  completion criteria, dependencies, review gates, and provider/profile IDs.
- Model the derived core-authoring sequence `IDEATION -> CORE_DRAFT ->
  CORE_REVIEW -> CORE_ACCEPTED` and the artifact sequence
  `ARTIFACT_PLANNED -> ARTIFACT_GENERATING -> ARTIFACT_REVIEW ->
  ARTIFACT_ACCEPTED` without storing mutable lifecycle status.
- Admit the initial selectable families: SmartDox article and article PDF,
  summary-slides PDF, infographic PNG backed by editable SVG, video with
  `video-review.html` (動画確認HTML), and Phase 41 Explanation Structure Review
  HTML (論理チャートHTML).
- Generate only the selected profile's minimal authored source skeleton;
  never scaffold receipts, approvals, attempts, generated state, dashboard,
  registry mutations, or delivery evidence.
- Activate or omit branches from selected deliverables and profile with an
  exact visible reason.
- Reject duplicate identities, unknown operations/providers, dependency
  cycles, unsafe paths, undeclared outputs, and ambiguous workspace binding.

## DP42-03: Evidence-Derived State and Attempt History

Stage Status:

- Current status: NOT STARTED
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

- Current status: NOT STARTED
- Owner: Cozy deterministic Document Project review projection
- Update rule: complete only when all three views, accessibility,
  deterministic output, and read-only authority boundaries pass.

- Generate `core-review.html` that exposes the accepted/candidate core
  and classifies feedback as shared-semantic or artifact-local before
  write-back.
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
- Keep Article 8 out of Phase 42 driver acceptance. Its operational rollout
  and acceptance are the post-Phase-42 `P401-DC-001` follow-up.
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

## Exclusions

- Fully autonomous claim, evidence, relation, narrative, or Visual Pattern
  creation and acceptance. AI-assisted proposal generation with explicit
  review and accepted write-back is included.
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

Phase 42 completes only when the same Document Project workflow can be
scaffolded, inspected, planned, verified, selectively executed, and visualized
for both a standalone directory and one non-Article-8 BoK-hosted or isolated
driver; every
intermediate Work Product exposes evidence-derived coverage, currentness,
review, and readiness;
the AI-dialogue/`core-review.html` loop produces only explicitly accepted
Content Core authority; artifact feedback is routed to the correct shared or
medium-local authority; required/optional/disabled branches are exact;
SmartDox/article PDF, slides PDF, infographic PNG, video/`video-review.html`,
and Phase 41 Explanation Structure Review HTML are independently selectable;
state and dashboard projections reconstruct deterministically; stale and
omitted branches are exact; existing authorities and legacy articles remain
compatible; full Cozy validation succeeds; and focused independent Phase
review closes all Current Boundary Blockers. Article 8 is only the
post-Phase-42 `P401-DC-001` operational rollout and is not Phase 42 acceptance.

## Structural Phase Plan Gate

State: PROCEED

- planning demand: new public schemas, scaffold and command family, workflow
  projection, execution evidence, dashboard, and two workspace drivers
- recommended parent profile: `gpt-5.6-sol / medium`
- implementation profile target: bounded Luna xhigh workers for model,
  projection, dashboard, and Executable Specification packages; Sol medium
  retains Phase authority and integration decisions
- estimate: 10–14 hours
- split disposition: retain one Phase with five committed internal stages
  because schema, state derivation, dashboard, and drivers must close against
  one coherent evidence model; split only if DP42-01 discovers a new external
  SmartDox contract that cannot be accepted within this boundary

## References

- `docs/phase/phase-42-checklist.md`
- `docs/notes/document-project-workflow-management-specification-proposal.md`
- `docs/journal/2026/08/2026-08-30-document-project-content-core-direction.md`
- `docs/spec/media-package.md`
- `docs/design/media-package-operation.md`
- `docs/phase/phase-36.md`
- `docs/phase/phase-37.md`
- `docs/phase/phase-40.md`
- `docs/phase/phase-40.1.md`
- `docs/phase/phase-41.md`
