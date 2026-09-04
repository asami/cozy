# Document Project Logical Presentation Projection Specification Proposal

Date: 2026-09-04

Status: specification proposal; non-normative; Phase 46/46.1 planning input

## Purpose

Define how a Cozy Document Project records two distinct levels of explanation
semantics in Content Core and projects the same accepted meaning into summary
slides, video, and one integrated confirmation HTML:

1. **Story Flow**: how the whole explanation develops for its audience and
   purpose; and
2. **Explanation Structure**: how one explanation unit is organized through a
   typed Logical Pattern, semantic nodes, and typed Relations.

This proposal reconnects the accepted Phase 36 Visual Page, Phase 37 Logical
Explanation Composition, Phase 41 Explanation Structure Review HTML, and
Phase 45 Document Project contracts. It does not make generated HTML, slide
layout, or video timing a semantic authority.

## Problem being corrected

The first Article 8 Document Project stored useful page text and visual intent,
but its article-review projection consumed the YAML through a permissive
string-key map. The renderer and its synthetic specification recognized
different field names from the real driver. Unsupported semantic fields were
silently replaced by placeholder text, so currentness and receipt checks
passed while the intended explanation was absent from the HTML.

The defect is broader than three missing aliases. The implementation treated a
page-oriented representation as the source of meaning instead of consuming a
typed, traceable projection from Content Core. Phase 46 must close that
authority and traceability gap rather than merely teaching one renderer more
field names.

## Authority model

The selected authority chain is:

```text
Content Core
  |- Story Flow
  |    `- Explanation Pattern + ordered Story Steps + transitions
  `- Explanation Structures
       `- Logical Pattern + semantic nodes + typed Relations
                  |
                  v
       accepted typed Explanation Plan
                  |
      +-----------+------------------+
      |                              |
      v                              v
Slide Projection                Video Projection
Visual Pages                    Storyboard Scenes
      |                              |
      +--------------+---------------+
                     v
       Integrated Confirmation HTML
```

Content Core is the shared semantic authoring authority. The accepted typed
Explanation Plan is its normalized presentation-semantics projection. Visual
Pages remain the one-screen presentation authority defined by Phase 36, and
Storyboard remains the audiovisual authority. Slides and video do not acquire
independent copies of shared claims, logical nodes, or Relations.

The generated confirmation HTML is a read-only joined view. It cannot become
an input authority and must not write back into Content Core, Visual Pages, or
Storyboard.

## Reuse of existing contracts

Phase 46 must reuse the existing typed contracts rather than inventing a
parallel page grammar:

| Concern | Existing authority reused by Phase 46 |
| --- | --- |
| Narrative/Argument structure | Phase 37 Explanation Pattern and ordered Explanation Steps |
| Per-unit explanation structure | Phase 36 Logical Pattern, semantic nodes, and typed Relations |
| One-screen presentation semantics | Phase 36 Visual Page |
| Video narration and audiovisual production | Existing Storyboard contract |
| Integrated read-only inspection | Phase 41 Explanation Structure Review HTML principles |
| Project authority/currentness/review | Phase 45 Document Project v2 and Content Core identity |

Content Core may provide an authoring-friendly YAML surface, but parsing must
produce these shared typed values. A second set of loosely equivalent maps or
renderer-specific aliases is not accepted.

## Content Core presentation semantics

The provisional Content Core presentation section has two required logical
parts when slides, video, or logical-chart output is selected.

### Story Flow

Story Flow describes the complete explanation and contains:

- a stable flow identity;
- audience and purpose references;
- one exact Subject Pattern and one exact Explanation Pattern;
- ordered Story Step identities and semantic roles;
- explicit typed `StoryTransition` values between Steps, with a closed
  `relationType` vocabulary; and
- shared claims, terminology, source, and asset references; and
- exact links from each Story Step to one or more Explanation Structures.

Order alone is not enough. A `StoryTransition` must retain one of the closed
Phase 36 relation meanings: `next`, `causes`, `depends-on`, `enables`, or
`maps-to`. It is not a Phase 36 local `Relation` instance, and a rendered arrow
is a Visual Pattern decision rather than a transition type. Contrast,
elaboration, and conclusion are not admitted transition IDs in this contract.

### Explanation Structure

Each Explanation Structure describes one locally understandable explanation
unit and contains:

- a stable structure identity and owning Story Step identity;
- one typed Logical Pattern;
- semantic nodes with stable identities and reader-facing labels;
- typed Relations between those nodes;
- claims, evidence, terminology, source, and asset references;
- optional authored visual intent that constrains but does not define layout;
  and
- exact article-section correspondence where an article is selected.

Examples include comparison, classification, decomposition, causality,
sequence, transition, mapping, convergence, and cycle. Pattern identity and
Relation type must remain distinct even when their renderings look similar.

## Typed implementation boundary

The implementation must normalize input into immutable typed values equivalent
to Scala case classes and closed enums before projection. It must not pass
seven or eight independent string parameters or generic `Map[String, Json]`
values between workflow, renderer, receipt, and review code.

The typed aggregate must keep at least:

```text
ContentCorePresentationSemantics
  storyFlow: StoryFlow
  explanationStructures: Vector[ExplanationStructure]
  articleBindings: Vector[ArticleBinding]
  projectionPolicy: ProjectionPolicyRef
```

Unknown fields, duplicate identities, missing Step/Structure bindings,
unsupported Logical Patterns, invalid Relation endpoints, and lossy scalar or
array coercion are structured diagnostics. Declared semantic content must
never be replaced silently with `No ... is declared` or another success-path
placeholder.

An absent optional display annotation may receive a neutral display fallback.
An unreadable or unsupported semantic value is an error and has no accepted
identity or current receipt.

## Visual Pattern policy

Logical meaning and visual realization remain separate. A versioned
Presentation Projection Policy maps an accepted logical context to compatible
Visual Patterns and renderer templates.

```text
Logical Pattern + Relation graph + medium + profile
  -> compatible Visual Pattern selection
  -> typed visual parameters
  -> renderer binding
```

Selection must be deterministic. A missing or ambiguous mapping produces a
diagnostic instead of an arbitrary visual choice. An authored override may be
accepted only when it names a compatible catalog entry and is preserved in the
receipt. Coordinates, fonts, colors, PowerPoint Shape IDs, CSS selectors, and
animation frame data remain below this policy boundary.

## Slide projection

The slide projection maps Story Steps and Explanation Structures to Visual
Pages. It must support one Step to one or more pages and must preserve:

- Story Step identity and order;
- Explanation Structure and Logical Pattern identity;
- semantic nodes and typed Relations;
- selected Visual Pattern and typed parameters;
- article/core traceability; and
- source and asset references.

Pagination is a projection decision. Splitting one explanation across pages
must not duplicate or mutate its logical authority.

## Video projection

The video projection maps the same Story Steps and Explanation Structures to
Storyboard scenes. It preserves the same semantic identities while adding
video-owned narration, duration, timing, transition, caption, and production
fields.

One Story Step may map to multiple scenes, and a scene may reuse a Visual Page
screen. Video pacing must not reorder or rewrite Story Flow silently. A
deliberate reordering requires an explicit projection mapping that remains
visible in review evidence.

## Integrated confirmation HTML

The confirmation HTML must make the following visible together without
requiring the reviewer to read YAML:

1. the complete Story Flow and typed transitions;
2. each Story Step's purpose, semantic role, and claims;
3. each Explanation Structure's Logical Pattern, nodes, and Relations;
4. selected Visual Pattern and important typed parameters;
5. article section, slide page, and video scene mappings;
6. shared versus medium-local content;
7. missing, incompatible, stale, or unprojected content; and
8. exact Content Core, plan, policy, catalog, renderer, and output identities.

The overview must expose Story Flow before page/scene details. A detail view
may show source references, diagnostics, and production metadata. Reader-facing
content and internal production instructions must remain visually distinct.

## Projection and receipt identity

Each generated projection records the exact identities of:

- accepted Content Core and its presentation-semantics section;
- normalized Explanation Plan;
- Visual Page and Storyboard projection mappings;
- explanation, presentation, and projection-policy catalogs;
- renderer/profile binding;
- consumed sources and assets; and
- generated output bytes.

A semantic, policy, mapping, catalog, renderer, or consumed-asset change makes
only the dependent outputs stale through declared edges. A receipt proves
identity and currentness; it does not by itself prove that the projection is
semantically complete. Executable Specifications must prove that every
declared Story Step and Explanation Structure appears in each selected
projection.

## Executable Specification requirements

The implementation is not accepted using only synthetic aliases. It must cover
at least:

- canonical Content Core input containing Story Flow and multiple distinct
  Explanation Structures;
- sequence and causality using visually similar arrows while retaining
  different Relation semantics;
- one Story Step projected to multiple slide pages and multiple video scenes;
- deterministic Visual Pattern selection from a versioned policy;
- complete core-to-article/slide/video/HTML traceability;
- rejection of unknown semantic fields, missing bindings, invalid endpoints,
  incompatible patterns, and ambiguous policy results;
- rejection rather than placeholder success when declared reader text,
  visual intent, or article binding cannot be normalized;
- deterministic bytes and exact stale propagation; and
- the real Article 8 canonical vocabulary and driver, not only a reduced
  repository-local fixture.

## Migration and compatibility

The Article 8 `pages` shorthand is useful draft material but is not sufficient
evidence of the two-level contract. Phase 46 may provide an explicit,
deterministic promotion operation into the typed Story Flow and Explanation
Structure model. It must not retain a permanent permissive alias reader or
infer missing logical graphs.

Earlier accepted Phase 36/37 files remain valid under their own contracts.
Phase 46 consumes them through explicit typed adapters; it does not rewrite
closed historical artifacts or claim that Phase 45.2 had already proved this
stronger semantic-completeness contract.

## Delivery plan

- **Phase 46** freezes and implements the Content Core two-level authority,
  shared typed aggregate, strict normalization, policy reference, diagnostics,
  identity, and currentness kernel.
- **Phase 46.1** implements slide/video projections, integrated confirmation
  HTML, receipts, and Article 8 end-to-end acceptance using the frozen Phase
  46 contract.

Publication, deployment, upload, push, automatic semantic acceptance, video
rendering, and external consumer acceptance remain outside both phases.
