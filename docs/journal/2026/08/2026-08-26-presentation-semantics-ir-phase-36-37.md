# Presentation Semantics IR Direction for Phases 36 and 37

Date: 2026-08-26

Status: planning rationale; non-normative

## Context

The slide and video generation discussion identified that a reusable Cozy IR
must preserve the logic of a claim rather than encode the visible PowerPoint
layout. An arrow is a renderer choice. The semantic input must still say
whether two nodes are ordered, causal, dependent, transformational, or merely
mapped to each other.

This observation refines the planned Phase 36 Common Visual Page work and
Phase 37 Logical Explanation Composition work. It does not change the active
Phase 30 boundary or authorize implementation.

## Direction selected

Use four explicit layers:

```text
Narrative / Argument Pattern
  -> Presentation Semantics IR: Logical Pattern + semantic Relation graph
  -> Visual Pattern
  -> renderer binding and PowerPoint/HTML/SVG/video-frame objects
```

Phase 36 starts at the one-screen Presentation Semantics IR and owns its
projection through Visual Pattern and renderer binding. Phase 37 owns the
Narrative / Argument layer and deterministic expansion into one or more
logical explanation steps before Phase 36 projection.

The common `Visual Page` is the one-screen envelope connecting these layers.
Its semantic payload is a typed logical graph. Its selected Visual Pattern is
a projection choice, not a substitute for the graph's meaning. Physical
coordinates, fonts, colors, PowerPoint Shape kinds, video timing, and renderer
object identifiers remain below the Visual Pattern boundary.

## Pattern and relation separation

`Logical Pattern` describes the topology and overall intent of one semantic
unit. `Relation` describes the meaning of each edge. They must be separately
typed and versioned.

For example, Sequence and Causality may both be drawn as three horizontal
cards with arrows, but they are not the same IR:

```yaml
logicalPattern: causality
nodes:
  - id: knowledge
    label: Knowledge
  - id: model
    label: Model
  - id: program
    label: Program
relations:
  - from: knowledge
    to: model
    type: enables
  - from: model
    to: program
    type: realizes
visualProjection:
  pattern: three-horizontal-cards
```

A different profile may project the same semantic graph to three vertical
sections without changing the logical identity. Conversely, changing
`enables` to `next` changes semantic identity even when the rendered arrows
look identical.

## Candidate logical-pattern catalog

The initial catalog design should account for this bounded candidate set. The
Phase 36 design stage may select a smaller representative implementation
subset, but it must define deterministic catalog extension, versioning, and
unknown-pattern rejection.

| Family | Candidate Logical Patterns |
|---|---|
| Assertion | `statement`, `evidence` |
| Set and structure | `enumeration`, `classification`, `decomposition`, `hierarchy`, `layer`, `containment` |
| Order and change | `sequence`, `timeline`, `transition`, `cycle` |
| Comparison and position | `comparison`, `contrast`, `matrix`, `balance`, `spectrum`, `positioning` |
| Direction and flow | `causality`, `dependency`, `convergence`, `divergence` |
| Correspondence | `mapping`, `intersection` |
| Quantity | `quantification`, `trend` |

The point of the catalog is not to prescribe one diagram per name. It gives a
renderer enough semantic structure to select an admitted Visual Pattern while
retaining the original meaning for inspection, conversion, receipts, and
future renderers.

## Candidate semantic-relation vocabulary

The Relation vocabulary should be closed and versioned rather than accepting
arbitrary arrow labels. The Phase 36 design should normalize exact names and
directionality for at least these families:

| Family | Candidate relations |
|---|---|
| Structural | `contains`, `consists-of`, `is-a`, `grouped-by` |
| Ordering | `before`, `after`, `next` |
| Directional | `causes`, `produces`, `transforms-to`, `depends-on`, `enables`, `realizes` |
| Comparative | `compared-with`, `contrasts-with` |
| Correspondence | `maps-to`, `supports` |
| Quantitative | `greater-than`, `increases`, `decreases` |
| Spatial/conceptual | `positioned-on`, `overlaps` |

The normative specification must decide canonical spelling, inverses,
cardinality, permitted node roles, and extension behavior. This journal entry
records the design input rather than freezing those details prematurely.

## Narrative and Argument Patterns

Presentation generation also needs to preserve why a sequence of information
is being shown. Phase 37 therefore treats `Explanation Pattern` as the
Narrative / Argument Pattern layer, independently of `Subject Pattern`:

- Subject Pattern: the typed structure of what is explained;
- Explanation Pattern: the narrative or argument strategy used for a given
  audience and purpose;
- Explanation Step: one normalized medium-neutral unit carrying claims,
  nodes, semantic relations, sources, assets, and a selected Logical Pattern;
- Visual Page: the one-screen projection envelope consumed by presentation
  and video display paths.

Candidate Narrative / Argument Patterns are:

- `problem-solution`
- `problem-cause-solution`
- `current-target`
- `before-after`
- `challenge-approach-result`
- `observation-insight-implication`
- `fact-interpretation-action`
- `why-what-how`
- `input-process-output`
- `concept-example`
- `claim-evidence`
- `claim-reasons`
- `question-answer`
- `principle-mechanism-effect`
- `strategy-execution-outcome`
- `past-present-future`

For example, `problem-solution` does not directly mean three boxes and two
arrows. It expands to typed Problem, Solution, and Result/Benefit roles plus
their semantic relations. Phase 36 projection may then select horizontal
cards, vertical sections, or another compatible Visual Pattern.

The existing `product-overview` and `product-mechanism` plans remain useful
domain-oriented Explanation Patterns. They should use the same narrative,
logical, relation, and projection contracts rather than create a separate
product-only IR.

## Cross-media consequence

The architecture is intentionally a Presentation Semantics IR rather than a
PowerPoint-only IR. The accepted Phase 36 and 37 contracts should permit later
renderers for HTML presentations, infographics, article figures, Mermaid or
PlantUML projections, and SVG without adding coordinates or Shape types above
the renderer-binding boundary.

Phase 36 and 37 acceptance remains limited to the planned presentation and
video paths. Other renderer implementations are future work, not additional
acceptance requirements for these Phases.

## Phase allocation

- Phase 36 specifies the typed one-screen logical graph, closed relation
  vocabulary, Logical Pattern Catalog, Visual Pattern Catalog, compatibility
  rules, renderer binding, Visual Page envelope, receipts, and presentation /
  video-screen projections.
- Phase 37 specifies Subject Pattern plus Narrative / Argument-oriented
  Explanation Pattern, deterministic Explanation Step expansion, and
  independent multi-page / multi-scene projection through Phase 36.
- Phase 30 remains unchanged and must close before Phase 36 starts.

## Acceptance implications

Phase 36 should prove that:

- identical-looking arrows with `next`, `causes`, and `depends-on` remain
  semantically distinct;
- one semantic graph can use two compatible Visual Patterns without changing
  its logical identity;
- coordinates, colors, fonts, and Shape kinds occur only in renderer binding
  or renderer output; and
- PPTX and video frame output remain derived evidence.

Phase 37 should prove that:

- one Narrative / Argument Pattern expands deterministically into typed
  logical steps and relations;
- the same accepted narrative composition can project into different slide
  and scene counts; and
- human/AI-authored meaning remains distinguishable from Cozy-owned
  validation, expansion, and rendering evidence.

## Non-decisions

This record does not select exact schema names, canonical relation spellings,
the first complete catalog contents, renderer algorithms, automatic pattern
selection, AI authoring behavior, or a migration date for
`cozy.slide-ir.v1`. Those decisions belong to the Phase 36 and Phase 37 design
and specification stages.
