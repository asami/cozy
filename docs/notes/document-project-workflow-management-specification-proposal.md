# Document Project Workflow Management Specification Proposal

Date: 2026-08-30

Status: specification proposal; non-normative; Phase 42 planning input

## Purpose

Define a Cozy Document Project workflow model that makes intermediate Work
Products, their completion coverage, currentness, review state, and executable
next operations visible without turning a dashboard or a mutable status field
into authority.

The proposal refines
`docs/journal/2026/08/2026-08-30-document-project-content-core-direction.md`.
Phase 42 owns its first implementation boundary. Normative design and
specification must be frozen in Phase 42 before implementation.

2026-09-04 refinement: the original Work Product model does not by itself
specify how Content Core carries the complete Story Flow and each local
Explanation Structure into slides and video. That successor contract is
proposed in
`document-project-logical-presentation-projection-specification-proposal.md`
and planned for Phase 46/46.1. Where the older text describes a page-oriented
Visual Page source as article-review authority, the successor proposal makes
Content Core the shared semantic authority and Visual Page/Storyboard typed
medium projections.

## Selected model

A Document Project selects a reusable Workflow Definition and derives one
Workflow Instance from its requested deliverables, profile, workspace, and
logical-operation provider bindings.

```text
Document Project descriptor
  + Workflow Definition
  + authored authorities
  + receipts and review decisions
  + append-only Operation Attempts
      -> derived Workflow Instance Snapshot
          -> inspect / plan projections
          -> self-contained project dashboard
```

The reusable workflow remains separate from project execution state. A
project references a common DAG; it does not copy and privately edit that DAG.
The current snapshot may be cached for fast display, but deleting the cache
and inspecting the same evidence must reconstruct the same state.

## Core authoring and feedback loop

Phase 42 admits a human-in-the-loop authoring cycle in which a person develops
an idea through dialogue with a configured generative-AI provider and accepts
the useful result into Content Core. Raw conversation is provenance and attempt
evidence; it is not Content Core authority by itself.

```text
human idea and source material
  -> AI-assisted core dialogue
  -> Content Core candidate
  -> core-review.html (内容確認HTML)
  -> semantic feedback
  -> revised Content Core candidate
  -> accepted Content Core identity
  -> selected artifact branches
```

The review projection must support two different feedback dispositions:

- shared meaning, claim, evidence, terminology, relation, narrative, or
  representation intent returns to Content Core; and
- wording, timing, layout, or another medium-local expression returns only to
  the corresponding article, slide, infographic, or video authority.

The provider selection, model/engine identity, prompt/response identity,
sensitive-data handling, accepted/rejected proposal IDs, and resulting Content
Core identity belong in append-only Operation Attempt and review evidence.
Phase 42 does not accept an autonomous provider response as approved semantic
authority and does not require raw prompt text to be copied into Content Core.

The apparent lifecycle labels `IDEATION`, `CORE_DRAFT`, `CORE_REVIEW`,
`CORE_ACCEPTED`, `ARTIFACT_PLANNED`, `ARTIFACT_GENERATING`,
`ARTIFACT_REVIEW`, `ARTIFACT_ACCEPTED`, and `PROJECT_COMPLETE` are derived
workflow views over Work Products and receipts. They are not editable project
status fields.

## Candidate contract identities

| Identity | Role |
| --- | --- |
| `cozy.document-project.v1` | Authored project, profile, deliverable, workspace, authority, and provider-binding declaration |
| `cozy.document-workflow.v1` | Reusable logical-operation DAG, Work Product roles, dependencies, criteria catalogs, and gates |
| `cozy.document-project-state.v1` | Generated current Workflow Instance Snapshot; never writable authority |
| `cozy.document-operation-attempt.v1` | Append-only evidence for one attempted logical operation |

Existing media, presentation, video, and review receipts remain authoritative
for their current contracts. The project snapshot references those receipts;
it does not reinterpret them or replace them with a generic receipt.

## Work Products

Every intermediate result is a first-class Work Product with a stable
project-local ID and these declared properties:

- role and kind;
- authority level;
- producer and consumer logical operations;
- required, optional, and not-applicable completion criteria;
- current artifact identity when materialized;
- consumed input identities;
- applicable review gate and decision evidence; and
- last operation-attempt and receipt references.

The initial closed role vocabulary is:

- `authority`: Content Core or medium-specific editable source;
- `plan`: outline, composition, Visual Page Set, Storyboard, or equivalent;
- `candidate`: materialized representation awaiting acceptance;
- `review-projection`: generated HTML or PDF used to inspect a candidate;
- `deliverable`: selected final project output; and
- `receipt`: deterministic identity/currentness evidence.

Role is independent of file extension. HTML and PDF may each be either review
evidence or a deliverable, so the descriptor or workflow must say which.

Every artifact branch has one explicit selection disposition:

- `required`: its acceptance criteria participate in project completion;
- `optional`: it may be generated and reviewed but does not block completion
  until activated for the current project; or
- `disabled`: the branch is omitted with an exact visible reason.

The initial selectable artifact families are:

| Family | Authored or semantic authority | Review projection | Deliverable |
| --- | --- | --- | --- |
| Article | Content Core plus SmartDox article source | article/content review HTML as applicable | SmartDox article HTML and/or article PDF |
| Slides | Content Core plus Slide/Visual Page semantics | Explanation Structure Review HTML where selected | summary-slides PDF |
| Infographic | Content Core plus editable infographic SVG | visual review representation | infographic PNG |
| Video | Content Core plus Storyboard/Visual Page/video authority | `video-review.html` (動画確認HTML) | rendered video |
| Logical chart | Content Core plus Phase 41 Explanation Structure semantics | Explanation Structure Review HTML (論理チャートHTML) | the same HTML when selected as a retained project artifact |

`infographic.svg` remains the editable representation authority; PNG is the
delivery representation. Phase 41 owns the reusable page-flow, page-semantic,
and page-display-structure projection primitive. Phase 42 selects and records
that logical-chart projection as a Work Product without redefining its
semantics.

## State derivation

The dashboard and command projections retain four independent dimensions:

| Dimension | Initial states | Meaning |
| --- | --- | --- |
| Completion coverage | `not-started`, `partial`, `complete` plus satisfied/total | How much required content or assembly evidence exists |
| Currentness | `missing`, `current`, `stale`, `failed` | Whether exact inputs and producer binding still match |
| Review | `pending`, `accepted`, `rejected`, `stale` | Human or AI acceptance evidence |
| Readiness | `blocked`, `ready`, `running`, `succeeded`, `failed`, `omitted` | Whether a logical operation can execute now |

Completion is derived from typed criteria, never from a user-entered
percentage. The compact UI may show `4/6`, but it must retain the exact
satisfied, missing, and not-applicable criteria. `complete`, `current`, and
`accepted` are not synonyms.

An operation is `ready` only when its active dependencies, required Work
Products, currentness requirements, and gates are satisfied. A deliverable
branch excluded by the selected profile is `omitted`, with the reason visible;
omission is not counted as completion.

Staleness propagates through declared identities and dependency edges, not
timestamps or filenames. At minimum:

- Content Core changes stale all medium alignments and dependent outputs;
- infographic changes stale article outputs that include it, summary slides,
  and video;
- article-only changes stale only article review and outputs;
- presentation changes stale summary-slide review and output;
- video-authority changes stale video review and output; and
- provider/profile changes stale outputs produced by that binding.

## Operation attempts

Each attempt records exact input identities, logical operation, provider and
profile, start/completion time, outcome, diagnostics, output identities, and
receipt references. Attempts are append-only evidence. A failed or superseded
attempt remains inspectable but cannot make the current snapshot successful.

The first Phase does not introduce a general scheduler, background service,
or arbitrary shell-command workflow. Provider bindings resolve only admitted
logical operations to registered Cozy, SmartDox, BoK, or filesystem adapters.

## Provisional command surface

Phase 42 should freeze a command family distinct from the existing software
Project knowledge-package commands:

```text
cozy document-project inspect <project>
cozy document-project plan <project>
cozy document-project dashboard <project> --save <dashboard.html>
cozy document-project verify <project>
cozy document-project run <project> --operation <logical-operation> [--dry-run]
cozy document-project scaffold <slug> --profile <profile> \
  --language <tag> --workspace <directory|bok> --save <parent>
```

`inspect` emits the evidence-derived snapshot. `plan` reports active, omitted,
blocked, and next executable operations without mutation. `dashboard` writes a
deterministic self-contained review projection. `verify` rejects invalid,
unsafe, stale, or internally inconsistent project evidence. `run` dispatches
one exact admitted logical operation and records its attempt; it does not
silently run downstream publication or workspace-wide operations.

`scaffold` atomically creates the initial authored package and refuses to
merge with or overwrite an existing path. It resolves the selected reusable
Workflow Definition by identity and must not copy the common DAG into the
project. Its minimum candidate result is:

```text
<slug>.dox/
  document-project.yaml
  index.dox
  content/
    core-<language>.yaml
  infographic/
    infographic.svg
  presentation/
    visual-pages.yaml
  video/                         selected only by a video-bearing profile
    storyboard.md
  review/
    README.md
```

The exact initial files are profile-sensitive: omitted deliverable branches
must not receive fake completed artifacts. The descriptor declares the
workflow/profile, stable project and Content Core IDs, requested deliverables,
workspace kind, provider bindings, and relative source paths. Scaffold writes
no receipts, accepted review decisions, Operation Attempts, generated state,
dashboard, publication registry, or workspace delivery evidence. A BoK
workspace selection creates the same portable package shape and binds hosted
identity explicitly; it does not build, register, or publish the BoK.
Generated `target/` state is absent immediately after scaffold and is created
only by later projection/build operations.

The exact JSON/text output grammar, atomic-write behavior, exit status, and
operation selection syntax remain Phase 42 design decisions.

## Dashboard contract

The self-contained project dashboard contains three coordinated views:

1. **Workflow view**: logical DAG, active/omitted branches, provider bindings,
   gates, readiness, and next executable operations.
2. **Work Product matrix**: every authority, plan, candidate, review
   projection, deliverable, and receipt with coverage, currentness, review,
   producer, consumers, and last accepted evidence.
3. **Work Product detail**: exact criteria, missing items, consumed identities,
   stale reasons, attempts, diagnostics, reviews, and available operations.

The default node remains compact. Shared Work Products, especially the
infographic, appear once with visible consumer edges. Current state and attempt
history are separate views. For BoK-hosted projects the dashboard also
separates project production, workspace registration, aggregate build, and
external delivery.

The dashboard is read-only generated evidence. Its HTML, CSS, inline SVG, and
interaction data never become semantic, workflow, or renderer authority.

The project dashboard is distinct from `core-review.html` (内容確認HTML),
`video-review.html` (動画確認HTML), and Phase 41 Explanation Structure Review
HTML (論理チャートHTML). The dashboard answers
workflow/currentness questions; the content review projection collects
semantic feedback for Content Core; the video projection reviews audiovisual
composition and render evidence; and the logical chart inspects page flow,
meaning structure, and display structure.

## Project and workspace boundary

Phase 42 accepts the same canonical Document Project contract in:

- an ordinary directory where project root is also workspace root; and
- a BoK-hosted `src/main/doxsite/<category>/<slug>.dox/` package whose
  `index.dox` preserves the legacy article identity.

The directory driver closes locally at verified deliverable assembly. The BoK
driver exposes separate production, integration, workspace-build, and delivery
surfaces. Project completion never implies registration, aggregate BoK build,
upload, or publication.

The Phase 42 driver must prove the new package can coexist with flat legacy
`.dox` articles and existing split media packages. It must not migrate Article
7 or earlier content. Article 8 is the first intended hosted pilot.

## Content Core boundary

Phase 42 needs a minimal validated Content Core identity because it is the
shared semantic authority and the root of stale propagation. The Phase does
not attempt to solve automatic claim extraction, semantic inference, complete
multilingual alignment, or every future Content Core vocabulary. Medium-
specific SmartDox, SVG, Visual Page, and Storyboard sources remain their own
expression authorities.

## Required acceptance cases

- identical accepted evidence reconstructs byte-identical state and dashboard;
- scaffold creates the same canonical authored package shape for directory and
  BoK hosting, differs only in explicit workspace binding, and never merges or
  overwrites a destination;
- deleting generated snapshot/dashboard caches does not lose workflow state;
- `current`, `stale`, `missing`, `failed`, `partial`, and `not-applicable`
  appear with exact reasons and criteria;
- an omitted video branch is not counted as completed work;
- a Content Core change and an infographic change propagate staleness through
  only their declared dependency edges;
- an article-only editorial change leaves infographic, slides, and video
  current;
- failed and superseded attempts remain historical without changing current
  success state;
- the directory and BoK drivers expose the same logical workflow while showing
  their different provider and workspace operations;
- the BoK driver preserves public article identity and excludes project-
  internal files from SmartDox/public source projection; and
- Article 8 pilot work cannot mutate Article 7 or trigger publication, upload,
  deployment, or workspace-wide build implicitly;
- one AI-assisted dialogue records provider/model and proposal evidence,
  produces a Content Core candidate, and requires an explicit accepted review
  before that candidate becomes Content Core authority;
- semantic feedback from `core-review.html` changes the Content Core identity,
  while an artifact-local feedback item changes only its medium authority;
- `required`, activated `optional`, and `disabled` artifact branches produce
  exact completion and omission behavior; and
- article/SmartDox source and PDF, slides PDF, infographic PNG, video plus
  `video-review.html`, and Phase 41 Explanation Structure Review HTML can each be selected
  independently without copying the reusable workflow.

## Explicit exclusions

- Editable status percentages or manually asserted `done` fields.
- A generic job scheduler, daemon, remote workflow service, or arbitrary
  executable-command descriptor.
- Fully autonomous semantic authoring or inference that bypasses explicit
  human review. AI-assisted dialogue and proposal capture are admitted.
- Replacing existing media receipts or review-state schemas.
- Dashboard write-back into authored sources.
- Implicit registration, aggregate build, publication, deployment, upload, or
  migration of existing articles.
- Expanding Phase 41's Explanation Structure Review HTML boundary.

## Phase 42 handoff

Phase 42 should proceed in five internal stages: freeze the contract, scaffold,
and commands; implement Workflow Instance and Work Product normalization;
derive state and append-only attempts; generate the dashboard; then accept
directory and BoK drivers with full validation and focused Phase review.
