# Document Project Goal-Driven Generation Design

Status: normative for Phase 60.1, Step P600-06, Slice P600-06A

This design records the stable responsibility boundary for goal-driven Document
Project generation. It implements the contract-admission portion of
[Phase 60.1](../phase/phase-60.1.md) only. It complements the v2
[Document and Summary Description design](document-project-document-and-summary-description-v2.md)
and the completed [Local Build Targets design](document-project-local-build-targets.md).
It neither creates a skill nor changes a Cozy operation.

## Authority and representation boundary

Content Core is the locale-independent logical-meaning authority. The localized
Document Description is the complete authority for organization and prose. The
localized Summary Description is a deliberate concise-content selection grounded
in its Document and Core; it is not a shortened formatting intermediate
representation. `index.dox` is the derived SmartDox article representation of
the accepted Document prose. It remains the SmartDox syntax, markup, and
declared-asset representation, not an independent prose authority.

Article wording feedback returns to the localized Document. A direct article
prose edit is reconciled into the Document before it is accepted. A
SmartDox-syntax-only repair may remain in `index.dox` when it preserves the
accepted prose and logical correspondence. A logical-meaning change updates
Core and then the affected grounded sources; a Summary-only edit changes only
the deliberate concise selection and its valid binding.

These reconciliation links are source-authority obligations. They are not
generation edges and therefore do not create a cyclic producer graph.

## Declared dependency graph

A request names an existing direct Document Project, requested locale, any
applicable profile, and one or more selected products. Its resolver walks each
selected product's declared direct inputs in reverse until it reaches Core or
an explicit external prerequisite. It then executes the resulting directed
acyclic graph forward in topological order. It does not select a fixed
Document-first pipeline and does not infer an edge from a filename, prose
similarity, or the presence of a file.

Every graph node declares its producer, direct inputs, locale and profile
scope, validation/reuse condition, and a supported route or an explicit
unavailable status. Shared prerequisite nodes are deduplicated by their
declared node and scope and are acted on once. A node action is one of
`reuse`, `create`, `update`, or `render`; reuse requires valid current source
admission or the declared target-local freshness condition. External assets,
configuration, and required tool inputs remain named prerequisites. They are
never fabricated as Core-derived DSL nodes.

The source graph contains at least these semantic edges:

```text
Core -> localized Document -> index.dox -> existing article HTML target
index.dox + declared article-PDF resource inputs -> existing article-PDF media route
Core -> localized Document -> localized Summary
Core/Document/Summary + P590-03 projection profile/catalog/pre-existing binding/
  media descriptor -> VisualPageSet -> existing Phase 40 summary-slide PDF route
authored infographic SVG -> existing svg-to-png media target
Core + declared presentation/visual sources -> storyboard -> review -> deliverable
  (optional video branch only when the selected profile enables it)
```

These edges inventory existing native product routes as well as the planned
goal-driven branches. They do not claim that P600-07 is implemented: P600-07
is planned solely as the resolver/dispatcher that will select and dispatch
these declared routes. The completed Phase 60 local-build graph remains
target-local: its SmartDox HTML target consumes `index.dox`, not a Document
merely because the Document is an upstream authoring source. A native route is
available only when its own declared inputs, profile/target selection, and
validation or currentness prerequisites exist.

## Responsibility and action handoff

`cozy-document-project-generation` is the planned maintained goal-driven
resolver/dispatcher for P600-07. When implemented, it will own request
parsing, graph planning, Codex dispatch, action sequencing, and propagation of
declared outcomes; it does not create or replace the native product routes
listed below. It is not implemented or installed by this slice.

Codex authors or updates content-bearing Core, Document, Summary, SmartDox
article, and separately declared visual/storyboard source nodes at their
declared project paths. It must not invent missing Core meaning. Cozy is
limited to independently admitted deterministic source validation,
confirmation, local-build, and rendering operations. It is not the overall AI
authoring planner and a Cozy operation does not approve prose, accept a human
decision, publish, deploy, or upload a product.

Each planned action hands its stable output or diagnostic to the skill before a
dependent action starts. A source-authoring result must satisfy its declared
source validation before dependent authoring or rendering. A selected
rendering action hands back its product result only; separately requested
display is not generation and does not select or rebuild a product.

## Capability matrix

The matrix distinguishes existing routes from planned orchestration and
unavailable capabilities. `Failure / resume` describes the contract boundary,
not acceptance evidence.

| Product or node | Producer / route | Inputs and output boundary | Current status | Failure / resume |
| --- | --- | --- | --- | --- |
| Core | Codex authoring dispatched by planned `cozy-document-project-generation` | Authorized authoring input or valid `content/core.yaml` -> `content/core.yaml` | Source authority exists; P600-07 dispatcher is planned | Missing meaning stops with authorized-input diagnostic; resume only with authorized authoring input. |
| Localized Document | Codex authoring dispatched by planned orchestration | valid Core + locale -> `content/<locale>/document.yaml` | v2 source contract exists; producer dispatch is planned | Invalid or stale Core binding stops source authoring; repair/re-author the affected source, then continue. |
| Localized Summary | Codex authoring dispatched by planned orchestration | valid Core, valid Document, locale -> `content/<locale>/summary.yaml` | v2 source contract exists; producer dispatch is planned | Invalid or stale Core/Document binding stops; update or reuse only after its bound sources validate. |
| SmartDox article source | Maintained `cozy-document-project-article` source-editing route | accepted Document prose and declared SmartDox inputs -> `index.dox` | Source-only; it does not implicitly request rendering | Preserve prior sources on failure; reconcile prose through Document and retry the source action. |
| Document structure HTML | `cozy document-project build <project> --target document-structure-html` | target configuration, Core, localized Document, confirmation vocabulary -> `target/document-project/local-build/document-structure-html/index.html` | Implemented Phase 60 local target | Existing target failure preserves prior output; retry the selected target after its declared inputs are ready. |
| Document reader HTML | `cozy document-project build <project> --target document-reader-html` | target configuration, Core, localized Document, confirmation vocabulary -> `target/document-project/local-build/document-reader-html/index.html` | Implemented Phase 60 local target | Existing target failure preserves prior output; retry the selected target after its declared inputs are ready. |
| SmartDox article HTML | `cozy document-project build <project> --target smartdox-article-html` | target configuration and `index.dox` -> `target/document-project/local-build/smartdox-article-html/index.html` | Implemented Phase 60 local target | Existing target failure preserves prior output; retry after `index.dox` is ready. Document-only change does not stale this output. |
| Browser display | Maintained `cozy-document-project-preview` route | Explicitly selected current generated HTML -> separately requested display/preview surface | Explicit-only preview; no implicit generation target | Display failure does not alter sources or products; explicitly request display again after a successful selected generation. |
| Article review projection | Legacy `cozy document-project run <project> --operation article.render-review [--dry-run]` | Content Core, `index.dox`, `presentation/visual-pages.yaml`, directly consumed `infographic/infographic.svg` -> `target/document-project/article-review.html` | Legacy native review provider only; not a general product-generation provider | Provider or prerequisite block leaves its existing boundary unchanged; it does not substitute for source authoring. |
| Goal resolver and dispatcher | Planned `cozy-document-project-generation` | request `(project, locale, profile, selected products)` -> ordered action plan and dispatched outcomes | Planned P600-07; not available in this slice | Missing DSL, unavailable capability, or cycle stops before dependent work; resume at the reported blocked prerequisite. |
| Article PDF | Existing `cozy media build <media-file> [--target <id>]` route for a declared `article-pdf` resource; P600-07 dispatch remains planned | Declared article source, same-locale infographic authority, resource configuration, and renderer inputs -> the declared PDF output | Existing native route; runs only when the selected target/profile and exact declared inputs and currentness checks are valid | An unavailable resource, input, profile/target, or external renderer prerequisite stops the route and preserves the prior output; resume at that prerequisite, with no fallback to HTML, review, or publication. |
| Summary-slide PDF | Completed P590-03 `CozySummarySlidePdf` connection through the existing Phase 40 `summary-slides-pdf` target; P600-07 dispatch remains planned | Admitted Core/Document/Summary, P590-03 projection profile, catalog, pre-existing binding, and media descriptor -> VisualPageSet -> the existing summary-slide PDF output | Existing native route reused as-is, including its Phase 40 renderer, verification, receipt, and currentness chain; runs only when the selected profile/target, exact declared projection, and currentness prerequisites are valid | A missing or invalid projection profile, catalog, binding, media descriptor, VisualPageSet, or currentness prerequisite stops the route and preserves the prior PDF; resume at the reported prerequisite, with no inferred projection or replacement renderer. |
| Infographic PNG | Existing `cozy media build <media-file> [--target <id>]` route for a declared `svg-to-png` target; P600-07 dispatch remains planned | Authored infographic SVG and its declared media target/configuration -> the declared PNG output | Existing native route; runs only when the selected target/profile and exact authored source, configuration, and currentness prerequisites are valid | A missing or unavailable SVG, target/profile, configuration, or external renderer prerequisite stops the route and preserves the prior output; resume at that prerequisite, with no automatic visual authoring or alternate renderer. |
| Optional video | Existing profile-gated `video-project` route or declared `prebuilt` video route; P600-07 dispatch remains planned | The selected profile's declared storyboard, review, deliverable, media, and other route inputs -> the declared video deliverable | Existing native route only where the selected profile enables the video branch and its exact declared route/currentness prerequisites are valid | A disabled profile branch, missing storyboard/review/deliverable route, or external prerequisite reports the explicit disabled/unavailable condition and preserves prior output; resume at the reported prerequisite, with no storyboard, rendering, upload, or publication fallback. |

The existing local targets use their own declared configuration, prerequisite,
timestamp, reuse, and failure rules. Their freshness is renderer-input
freshness only. It is never Document-to-article reflection, prose approval, or
publication approval.

## Diagnostics and preservation

The resolver reports these distinct blocking diagnostics:

- `missing-dsl`: a declared required source node is absent;
- `invalid-or-stale-binding`: an existing source is malformed or no longer
  binds its declared Core or Document authority;
- `missing-core-meaning`: the graph reaches absent or insufficient Core
  meaning and needs authorized authoring input or clarification;
- `unavailable-producer-or-capability`: a requested node's declared producer or
  native route is genuinely unavailable after its profile and external
  prerequisites are checked; this diagnostic is not implied merely because
  the P600-07 dispatcher is not implemented; and
- `dependency-cycle`: declared generation edges contain a cycle, reported with
  its dependency path.

No diagnostic authorizes invention of Core meaning. On any source or rendering
failure, the workflow preserves prior successful products and the resumable
source state. A retry resumes from the invalid, missing, or blocked prerequisite
rather than presenting an old product as newly generated.

## Scope boundary

This design admits no command, renderer, hash or receipt framework,
whole-site/Antora build, publication, deployment, upload, or external-project
behavior. The local-build contracts remain unchanged. P600-07 implements the
planned skill boundary and P600-08 supplies execution acceptance; neither is
completed by this document.
