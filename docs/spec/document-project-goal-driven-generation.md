# Document Project Goal-Driven Generation Specification

Status: normative for Phase 60.1, Step P600-06, Slice P600-06A

This specification is the authoritative P600-06 contract for the goal-driven
Document Project workflow described in [Phase 60.1](../phase/phase-60.1.md).
It is paired with the [Goal-Driven Generation design](../design/document-project-goal-driven-generation.md).
It preserves the v2 [Document and Summary Description specification](document-project-document-and-summary-description-v2.md)
and the implemented [Local Build Targets specification](document-project-local-build-targets.md).

## 1. Source authority

Core MUST remain the locale-independent logical-meaning authority. A localized
Document MUST remain the complete authority for organization and prose. A
localized Summary MUST remain a deliberate concise-content authority grounded
in its bound Core and Document. `index.dox` MUST remain the derived SmartDox
article representation of accepted Document prose; it MUST NOT become a
second prose authority.

Article wording feedback MUST update the Document. Direct article prose edits
MUST be reconciled back to the Document before acceptance. A syntax-only
SmartDox repair MAY remain only in `index.dox` when it preserves the accepted
prose and logical correspondence. A logical change MUST update Core and then
the applicable grounded sources. Reconciliation requirements MUST NOT be
represented as generation edges.

## 2. Request and graph contract

A generation request MUST explicitly identify the direct project, locale,
applicable profile, and selected product or products. A source-only editing
request MUST NOT select a renderer. A request to display a result MUST be
separate from generation and MUST NOT implicitly select a product, invoke a
renderer, open a browser, or notify a preview service.

For every selected product, planning MUST traverse declared direct inputs in
reverse to Core and explicit external prerequisites, then execute the deduplicated
required closure forward in topological order. Every node MUST declare:

1. its producer or explicit unavailable status;
2. direct inputs and input/output paths or product boundary;
3. locale and applicable profile scope;
4. validation and reuse condition; and
5. its Codex-authoring, Cozy-operation, or unavailable classification.

The resolver MUST deduplicate shared scoped prerequisites and execute each
shared action once. It MUST NOT infer a dependency from a filename, prose
similarity, a current output, or a reconciliation link. Assets, configuration,
and tool inputs that are not semantic DSLs MUST remain explicit external
prerequisites.

Node actions are `reuse`, `create`, `update`, and `render`. Reuse of a source
requires its declared valid admission and bindings. Reuse of an implemented
local target follows its own target-local timestamp contract. The workflow
MUST NOT reinterpret renderer freshness as prose equivalence, source
reflection, human approval, or publication approval.

## 3. Responsibility contract

The planned maintained entry skill `cozy-document-project-generation` MUST,
when P600-07 implements it, serve solely as the goal-driven resolver and
dispatcher owning request parsing, dependency planning, Codex dispatch, and
action sequencing. It MUST select and dispatch declared native routes without
creating or replacing them. This specification does not implement, install,
or expose that entry skill.

Codex MUST author content-bearing sources at their declared project paths.
Cozy MAY perform only independently admitted deterministic validation,
confirmation, target-local build, and rendering operations. Cozy MUST NOT be
treated as the overall content-authoring dependency planner. Completion of a
generation action MUST NOT approve prose or authorize publication, deployment,
or upload.

A dependent source-authoring or rendering action MUST wait for the prerequisite
action's declared valid output. A returned diagnostic MUST stop dependent
actions. Separately requested display consumes a successful selected product;
it does not change source authority or generation selection.

## 4. Current capability requirements

The capability matrix in the paired design is part of this specification. Its
following availability assertions are mandatory:

- The only implemented Phase 60 local HTML target IDs are
  `document-structure-html`, `document-reader-html`, and
  `smartdox-article-html`, invoked by the established `cozy document-project
  build <project> --target <target-id>` contract.
- `smartdox-article-html` consumes its declared target configuration and
  `index.dox`; a Document-only change MUST NOT make that local target stale.
- `cozy-document-project-article` is source-only and
  `cozy-document-project-preview` is explicit-only display/preview. Neither
  implicitly selects generation.
- The legacy `article.render-review` provider is not a general
  product-generation provider.
- `cozy-document-project-generation` is planned only as the P600-07
  goal-driven resolver/dispatcher and is not available under P600-06. Its
  unavailable status MUST NOT be interpreted as the absence of an existing
  native product route.
- Article PDF MUST be inventoried as the existing native `article-pdf` media
  route selected through the established `cozy media build <media-file>
  [--target <id>]` operation. It consumes its declared article source,
  same-locale infographic authority, resource configuration, renderer inputs,
  and currentness evidence; it MUST run only when those exact inputs and the
  selected target/profile are available.
- Summary-slide PDF MUST be inventoried as the completed P590-03
  `CozySummarySlidePdf` connection to the existing Phase 40
  `summary-slides-pdf` target. Its declared Core/Document/Summary, projection
  profile, catalog, pre-existing binding, media descriptor, VisualPageSet, and
  receipt/currentness prerequisites and selected profile/target MUST be valid
  before that native route runs; the existing Phase 40 renderer and evidence
  chain are reused.
- Infographic PNG MUST be inventoried as the existing `svg-to-png` media
  target consuming its authored infographic SVG and declared target
  configuration, with the selected target/profile and currentness prerequisites
  valid before the route runs.
- Optional video MUST be inventoried as the declared profile-gated
  `video-project` or `prebuilt` video route, consuming its declared
  storyboard/review/deliverable and other media inputs only when the selected
  profile enables that branch and its exact route/currentness prerequisites are
  valid. This inventory does not admit uncommitted Phase 61 changes.
- P600-07 goal dispatch remains unavailable in this slice. A requested product
  MUST fail with the appropriate unavailable-producer/capability, disabled
  profile, or missing external-prerequisite diagnostic when its exact native
  route, profile, or prerequisites are unavailable; it MUST NOT be substituted
  by an analogous route.

No command, renderer, receipt/hash framework, whole-site or Antora build,
publication, deploy, or upload operation is admitted by this specification.

## 5. Required diagnostics and failure preservation

The planner MUST distinguish `missing-dsl`, `invalid-or-stale-binding`,
`missing-core-meaning`, `unavailable-producer-or-capability`, and
`dependency-cycle`. A cycle diagnostic MUST include the declared dependency
path. `missing-core-meaning` MUST request authorized authoring input or
clarification; it MUST NOT invent logical meaning. The
`unavailable-producer-or-capability` diagnostic applies only when the requested
declared producer or native route is genuinely unavailable after profile and
external prerequisites are checked; the absence of the planned P600-07
dispatcher alone MUST NOT produce that diagnosis for an existing native route.

Before a dependent action runs, a missing required DSL, invalid or stale
binding, unavailable producer/capability, external prerequisite failure, or
cycle MUST stop that dependent action. A failure MUST preserve prior successful
products and resumable source state. Resume MUST start at the reported missing,
invalid, or unavailable prerequisite; it MUST NOT represent an old product as
the result of a new generation request.

## 6. Contract scenarios for P600-08

The following scenarios define future acceptance behavior; they are not
execution evidence under P600-06:

| Scenario | Required contract outcome |
| --- | --- |
| Source-only editing | Update only selected authorities and necessary source validation; do not render, display, or notify preview. |
| Explicit selected generation | Resolve only selected product closure and run only available declared producers; display remains separately requested. |
| Core-only initial input | Walk the requested product back to Core, then create declared missing downstream sources in forward dependency order only after authorized Core meaning is present. |
| Current reuse | Reuse valid current scoped sources and current selected target outputs without rewriting prose or rerendering. |
| Selective update | Update only affected nodes in the selected dependency closure; do not regenerate unrelated products. |
| Multi-product request | Deduplicate shared prerequisites and execute them once before the relevant product branches. |
| Unavailable producer or cycle | Stop before dependent generation, report the unavailable route or dependency path, preserve prior state, and leave unrelated products untouched. |

P600-07 owns implementation of the planned maintained skill and producer
integration. P600-08 owns isolated end-to-end acceptance of these scenarios.
Neither status is changed by this specification.
