# Phase 42: Document Project Contract Kernel and Workflow Instance

Status: COMPLETE

Plan date: 2026-08-30

Split approval date: 2026-08-31

Dependencies:

- the accepted Cozy media, receipt, Visual Page, explanation-composition, and
  review-state contracts from Phases 19, 30, 36, and 37;
- Phase 41 closure for the reusable Explanation Structure Review projection.

This is the closed first child of the approved Phase 42 split. DP42-01 and
DP42-02, the required review/closure evidence, and final serialized Cozy
validation are complete. Phase 42.1 stays planned and does not activate
implementation or alter an active, closed, or earlier planned Phase boundary.

## Goal

Establish the reusable Document Project contract: the Content Core authority
boundary, the public command and scaffold grammar, and the normalized Workflow
Instance and Work Product model. The resulting accepted contract is the frozen
handoff that Phase 42.1 consumes when it adds evidence-derived state, review
projections, dashboard output, and driver acceptance.

The Phase supports the primary authoring loop in which a user develops an idea
through AI-assisted dialogue, accumulates accepted proposals in Content Core,
reviews that core through `core-review.html` (内容確認HTML), and returns semantic
feedback to the core before activating the required or optional artifact
branches. A raw model response never becomes Content Core authority without
accepted review evidence.

## DP42-01: Contract Kernel and Command Surface

Stage Status:

- Current status: COMPLETE; release closure pending
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
  acceptance, and feedback-write-back boundaries.
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

- Current status: COMPLETE; release closure pending
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

## Approved Split (2026-08-31)

- Approval evidence: `Phase 42 を Phase 42 と Phase 42.1 に、この提案どおり分割することを許可する`.
- Child sequence: Phase 42 first, then Phase 42.1. The original unsuffixed
  number remains the first child; no completed history exists to move.
- Phase 42 owns DP42-01 and DP42-02. Phase 42.1 owns DP42-03, DP42-04, and
  DP42-05 exactly once.
- Reason: the former 10–14 hour proposal combined the protected public
  contract/model kernel with evidence reconstruction, deterministic review
  projection, and two-driver acceptance. The approved split keeps the
  expensive reasoning kernel coherent while giving the evidence/projection
  work a closed, lower-cost successor boundary.
- Estimated planning overhead: 1–1.5 hours for the extra phase/checklist,
  frozen handoff, and second closure; the saved rework risk is 3–5 hours from
  avoiding a redesign of state/dashboard work if the public kernel changes.
- No under-four-hour child is created, so no merge attempt is required.
- Frozen handoff to Phase 42.1: accepted `cozy.document-project.v1` and
  `cozy.document-workflow.v1` contract/spec/design, command and scaffold
  grammar, Content Core authority boundary, normalized Workflow Instance and
  Work Product roles/criteria/gates, and their Executable Specifications.

## Exclusions

- Evidence-derived state reconstruction, append-only attempt persistence,
  stale propagation, review/dashboard rendering, and external driver
  acceptance; these are Phase 42.1 work.
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

Phase 42 completes only when the Document Project schemas, Content Core
authority boundary, command/scaffold grammar, and Workflow Instance/Work
Product model are specified and implemented with Executable Specification;
required/optional/disabled branches and all rejected ambiguous bindings are
exact; existing Project knowledge-package and `cozy media` behavior remains
compatible; localized and full serialized Cozy validation succeeds; and a
focused independent Phase review closes all Current Boundary Blockers. Its
release supplies the frozen handoff above and makes no claim about
evidence-derived state, dashboard output, external driver acceptance, or
Article 8 rollout.

## Closure Evidence in Progress (2026-08-31)

- DP42-01 is accepted in local commit
  `6fa01ac3687d66a8a24b0f20ec6c1d8b3b597729`.
- DP42-02 is accepted in local commit
  `a26f32d4c96fc1c35404f91ad1b18ee57cf99406`.
- The mandatory independent Phase review found `CPB-42-001`. Its bounded
  source/spec repair passed focused serialized Cozy validation
  `26428-20260831T095703Z` (21 succeeded, 0 failed), and the focused closure
  re-review resolved the blocker with no remaining Current Boundary Blocker.
- `HYG-42-001` is recorded separately in the Phase 42 hygiene follow-up
  journal. It does not alter the accepted Document Project boundary.
- Final repository-wide Cozy validation passed as
  `33960-20260831T101434Z`: 1,619 succeeded, 0 failed, 8 canceled, and 122
  suites completed; SBT and wrapper exited 0 and the serial lock was released.
- This distinct local-only Phase release commit records the completed closure.
  No push, publication, deployment, upload, or Phase 42.1 work is included.

## Structural Phase Plan Gate

State: PROCEED

- planning demand: protected public contract, command/scaffold grammar, and
  reusable Workflow Instance/Work Product modeling
- parent profile: `gpt-5.6-terra / xhigh`
- implementation profile target: bounded workers under a Terra/xhigh parent
  when the approved Phase is started
- cost role: expensive reasoning kernel; it remains entirely in this first
  child to avoid splitting its public-contract decisions
- estimate: 5–7 hours
- predecessor: Phase 41 closure
- successor: Phase 42.1, which may start only after this Phase closes with its
  frozen handoff

## Pre-Split Gate Evidence (2026-08-30; superseded)

The original unsplit plan gate was `PROCEED`, estimated 10–14 hours, and
recommended `gpt-5.6-sol / medium` with bounded Luna xhigh implementation
workers. Its then-current disposition was to retain one Phase unless DP42-01
discovered an external SmartDox contract. The 2026-08-31 approved split
supersedes that disposition; it is retained only as dated planning evidence,
not as the current Phase gate.

## References

- `docs/phase/phase-42-checklist.md`
- `docs/phase/phase-42.1.md`
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
