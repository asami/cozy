# Logical Explanation Composition and Media Projection Design

Document role: stable design intent for the logical explanation composition,
its deterministic plan, the authored projection mapping request, and the
independent presentation and video projections.

The normative behavior contract is
[`docs/spec/explanation-composition.md`](../spec/explanation-composition.md).
This document defines ownership, layering, and boundaries. It does not define
Phase work status and does not claim implementation, validation, review,
publication, deployment, or downstream-consumer acceptance.

## 1. Design intent

An explanation has two independent authored choices:

```text
Subject Pattern
  -> the typed structure of what is explained

Explanation Pattern (Narrative / Argument Pattern)
  -> the typed way that subject is developed for an audience and purpose
```

The selected pair and explicitly authored explanation steps are normalized
into one ordered, medium-neutral Explanation Plan. A step retains authored
claims, emphasis, a P36-compatible Logical Pattern and typed Relation graph,
source references, asset references, and parameter provenance before any
visual or audiovisual choice is made.

The pipeline is deliberately one-way:

```text
authored subject facts + selected patterns + authored explanation steps
  -> named explanation-catalog and P36 presentation-catalog validation
  -> canonical Composition
  -> named-catalog deterministic Plan
  -> authored ProjectionMap
  -> named VisualPageSet/Storyboard deterministic Projection and receipt
```

The Plan is the authority for composition semantics. A Visual Page remains
the one-screen semantic authority defined by Phase 36, and a Storyboard
remains the authority for narration and audiovisual production fields defined
by the existing Storyboard contract. PPTX, MP4, renderer manifests, and
review records remain derived artifacts or separate acceptance evidence.

## 2. Ownership and responsibility

### 2.1 Human and AI authors

The human or an AI author owns:

- subject facts and their source and asset references;
- selection of the exact explanation catalog, Subject Pattern, and
  Explanation Pattern;
- typed per-step parameter selections;
- ordered explanation steps, claims, claim emphasis, logical nodes and
  Relations, labels, and references; and
- the independent mapping from each Plan step to page IDs and, separately,
  to Storyboard scene IDs.

Authoring does not mean that Cozy accepts arbitrary values. Every authored
value is an input to strict validation against the explicitly supplied
catalog files. Cozy never fills an omitted value or authors a semantic claim,
logical graph, page mapping, scene mapping, or narration.

### 2.2 Cozy

Cozy owns deterministic model operations only:

- direct-file parsing with closed-field and duplicate-field rejection;
- exact explanation-catalog and Subject/Explanation Pattern resolution;
- direct `--explanation-catalog <file>` and
  `--presentation-catalog <file>` companions for every Composition
  `validate`, `inspect`, and `convert` operation, including validation of each
  authored Logical Pattern/Relation graph against the named P36 catalog;
- exact typed parameter and subject-fact validation;
- canonical JSON normalization and UTF-8 SHA-256 semantic identities;
- deterministic normalization and copying of authored steps into an
  Explanation Plan;
- validation of Phase-36 Logical Pattern and typed Relation structures;
- validation of the separately authored ProjectionMap;
- deterministic projection into the existing Visual Page and Storyboard-v2
  boundaries; and
- fail-closed diagnostics when an input is malformed, stale, incompatible,
  incomplete, unsafe, or unsupported.

Cozy does not discover a catalog or resource, infer a subject, select a
pattern, summarize authored meaning, repair missing values, generate a
ProjectionMap, generate or modify a VisualPageSet or Storyboard, render,
publish, or approve anything.

### 2.3 Existing media owners

Phase 36 owns the existing `cozy.presentation-semantics.catalog.v1`, Visual
Page, Logical Pattern, Relation, Visual Pattern, catalog/binding, and
presentation projection semantics. Its supplied catalog has two existing
identity results: the full presentation-catalog identity and the existing
logical-catalog identity. P37 does not define or change that schema.

The existing Storyboard contract owns speaker, narration, duration, silence,
transition, production inserts, captions, direction, and audiovisual review
semantics. P37 may validate an existing Storyboard-v2 visual screen reference
only through its literal P36/P30 fields:

```json
{"kind":"visual-page","source":"<named-visual-page-set>",
 "catalog":"<named-presentation-catalog>","pageId":"<page-id>"}
```

P37 does not rewrite those fields or add a new Storyboard, renderer, timing,
narration, PPTX, MP4, or review-approval model.

## 3. Versioned contract layers

The v1 contracts have separate schemas and identity results:

| Layer | Schema | Responsibility |
| --- | --- | --- |
| Explanation catalog | `cozy.explanation.catalog.v1` | Closed Subject and Explanation Pattern definitions and typed vocabulary. |
| Composition | `cozy.explanation-composition.v1` | Authored subject selection, explanation selection, facts, steps, parameters, and references. |
| Plan | `cozy.explanation-plan.v1` | Deterministic ordered semantic steps before visual selection. |
| ProjectionMap | `cozy.explanation-projection-map.v1` | Authored independent page and scene step mappings plus catalog/input selectors. |
| Projection | `cozy.explanation-projection.v1` | Copied mappings, named media identities, and a currentness receipt. |

The explanation catalog and Composition have derived identity results and do
not serialize an `identity` self field in their roots. Plan, ProjectionMap,
and Projection do serialize an `identity` self field as the final root field;
each such field is excluded from the canonical bytes hashed for that root.
Mapping identities and the receipt identity likewise exclude only their own
identity field. Every P37 identity result is the lowercase SHA-256 digest of
canonical UTF-8 JSON bytes, serialized as
`sha256:<64-lowercase-hex>`.

No filesystem path is serialized into a P37 semantic identity. Cross-document
dependencies are supplied as explicitly named command inputs. The only
literal path-like screen fields in scope are the existing Storyboard-v2
`source` and `catalog` fields, which P37 validates as the named P36 pair and
does not change.

## 4. Catalog architecture

The explanation catalog has separate `subjectPatterns` and
`explanationPatterns` collections. A Subject Pattern defines the typed fact
vocabulary for one kind of subject. An Explanation Pattern defines its
ordered semantic roles, typed per-step parameters, and exact compatible
Subject Pattern `id`/`version` pairs. Compatibility is an exact pair match
within the explicitly supplied explanation catalog `id` and `revision`; there
is no nearest-version fallback, alias, coercion, or inference.

For canonical catalog bytes, `subjectPatterns` and `explanationPatterns` are
ordered ascending by `(id,version)`; each pattern's `roles` are ordered
ascending by `order`; `factDefinitions` and `parameterDefinitions` are
ordered by `name`; `compatibleSubjects` are ordered ascending by
`(id,version)`; and `reservedNarrativeArgumentIds` retain their specified
fixed Narrative / Argument order. These catalog input arrays are normalized
to those orders only after strict parsing and duplicate validation. Semantic
authored arrays in a Composition or Plan remain authored or contract-defined
order and are not changed by catalog normalization.

The closed v1 `software-product` vocabulary is exactly:

- `text`: a nonempty string;
- `goal-list`: a nonempty ordered array of entries exactly
  `{id,label,sourceRefs,assetRefs}`;
- `use-case-list`: a nonempty ordered array with the same entry shape;
- `scenario`: an object exactly `{id,label,steps}`, where `steps` is a
  nonempty ordered array of entries exactly
  `{id,label,sourceRefs,assetRefs}`;
- `mechanism-list`: a nonempty ordered array of entries exactly
  `{id,label,sourceRefs,assetRefs}`; and
- `mechanism-link-list`: a nonempty ordered array of entries exactly
  `{id,goalId,useCaseId,mechanismId,sourceRefs,assetRefs}`.

All IDs are nonempty stable tokens, labels are `text`, and reference arrays
contain declared source or asset IDs in authored order. The seven required
`software-product` facts are exactly `name:text`, `vision:text`,
`goals:goal-list`, `context:text`, `useCases:use-case-list`,
`mainScenario:scenario`, and `mechanisms:mechanism-list`.

The accepted Explanation Patterns are:

- `product-overview`, with no parameters and exact ordered roles `vision`,
  `goal`, `context`, `use-case`, `main-scenario`;
- `product-mechanism`, with one required `mechanismLinks` parameter of type
  `mechanism-link-list`; and
- `problem-solution`, with required `problem` and `solution` parameters of
  type `text`.

`product-mechanism` accepts only links whose `goalId`, `useCaseId`, and
`mechanismId` exactly resolve to IDs declared by the selected product facts.
No default, scalar/list coercion, omitted-value inference, or relationship
inference is permitted. The catalog may reserve additional Narrative /
Argument IDs, but an unimplemented or unknown ID is rejected until an
explicit later catalog revision defines it.

## 5. Authored Composition and deterministic Plan

The Composition is an authored value, not a generated outline. It names an
explanation catalog selector, one Subject Pattern, one Explanation Pattern,
typed subject facts, and `explanation.steps`. Every step is authored and has
stable `id`, contiguous `order`, catalog-declared `semanticRole`, nonempty
claims, a typed `logical` value, step references, and a
`parameterSelection`. Claims have exactly `{id,text,emphasis,sourceRefs,assetRefs}`;
`emphasis` is exactly the closed enum `supporting|primary`.

The P36-compatible `logical` value has exactly `{pattern,nodes,relations}`:
`pattern` is the stable string pattern ID, not an `{id,version}` object; each
node is exactly `{id,role,label,sourceRefs}`; and each Relation is exactly
`{id,type,from,to,sourceRefs}`. P37 makes no version claim for a P36 logical
pattern entry. This removes `assetRefs` only from logical nodes and Relations;
claims, facts, and P37 step/root provenance retain their defined `assetRefs`
fields. The value is checked against the explicitly supplied P36 presentation
catalog; Cozy does not derive or repair a graph. Every reference must resolve
exactly once. A selected Explanation Pattern's roles and authored steps must
agree in order and identity; missing or extra steps are rejected. The P36
catalog identity/revision binding remains in the Plan and Projection
selectors and receipts.

The `expand` operation means deterministic validation, normalization, and
copying of every explicitly authored step, claim, emphasis, Logical
Pattern/Relation value, reference, and parameter selection; it does not
generate or infer a graph, role, claim, or step. Each Plan step contains
`parameterProvenance.values` as the ordered `{name,value}` values used for
that step and a derived identity over those values; names alone are not
sufficient provenance. Plan contains selectors for both named catalogs, with
the P36 `presentationCatalog` selector exactly
`{id,revision,identity}` where `identity` is the existing logical-catalog
identity.
The `product-overview`, `product-mechanism`, and `problem-solution` cases
therefore preserve their authored typed roles and Relations in Plan steps;
none generates or infers them.

## 6. Authored ProjectionMap

`cozy.explanation-projection-map.v1` is a distinct closed document between
Plan and final Projection. It is authored, has its own canonical identity,
and contains only composition/Plan/catalog selectors and independent ordered
step mappings:

- presentation mappings contain `stepId` and nonempty ordered `pageIds`;
- video mappings contain `stepId` and nonempty ordered `sceneIds`; and
- presentation and video mapping identities are independent and derived from
  their respective mapping values.

ProjectionMap has no resource paths, VisualPageSet or Storyboard content,
renderer state, review/approval state, layout, coordinates, timing,
transitions, narration, media bytes, or generated artifact fields. A mapping
request never discovers or generates its targets. The parent author supplies
the named VisualPageSet and Storyboard only to the later `project` operation.

## 7. Independent Projection and command boundary

`project` receives a ProjectionMap, Composition, Plan, explicitly named
explanation and P36 presentation catalogs, an explicitly named VisualPageSet,
and an explicitly named Storyboard. It verifies selector and identity
agreement and resolves every page and scene mapping. The direct-file
`validate <composition>`, `inspect <composition>`, and
`convert <composition>` operations likewise require both explicitly named
`--explanation-catalog <file>` and `--presentation-catalog <file>` companions
before parsing or validating the Composition's P36 Logical Pattern/Relation
graphs; they also require the common declared source/asset bindings whenever
those declarations are in the input closure. For every authored Plan
step -> presentation `pageId` mapping, the exact page must resolve in the
explicitly named VisualPageSet, and that P36 page's `logical` value must equal
the Plan step's P36-compatible `logical` value exactly. The union of every
source and asset reference carried by the Plan step, its claims, and its
logical nodes and Relations as applicable must occur in that resolved P36
page's `sources` and `assets` declarations. The existing P37 explicit
`--source <id>=<file>` and `--asset <id>=<file>` bindings must verify current
direct bytes against the P37 declarations and the resolved P36 declarations
under their existing path/digest rules. Additional P36 page provenance remains
owned by P36 and does not change Plan identity.

For every authored Plan step -> video `sceneId` mapping, the exact scene must
resolve in the explicitly named Storyboard and must contain an existing
Storyboard-v2 `visual-page` screen. Its literal `source`, `catalog`, and
`pageId` must resolve to the named VisualPageSet and named P36 catalog pair,
and its `pageId` must be one of that same Plan step's presentation `pageIds`.
Multiple scenes may reference one page, and a Plan step may retain a page that
no video scene uses; page and scene cardinalities therefore remain
independent, with no one-to-one requirement.

P37 preserves authored claims in the Plan. It does not add claim text to a
P36 VisualPage, which has no claim field, and it does not self-approve semantic
meaning; `project` verifies graph and provenance linkage and currentness only.
It writes only the final Projection and receipt selected by `--save`. It does
not discover, generate, modify, render, publish, or approve VisualPageSet,
Storyboard, or media.

The final Projection copies both independent mapping sections and their
mapping identities. Its receipt binds Composition, Plan, explanation catalog,
P36 logical-catalog, full P36 presentation-catalog, VisualPageSet,
Storyboard, presentation mapping, and video mapping identities. A receipt
proves identity/currentness only; it is not semantic, visual, audiovisual,
publication, or consumer approval.

Whenever an operation's validated direct-file input closure includes declared
sources or assets, the caller supplies each declaration as a repeatable direct
binding exactly once: `--source <id>=<file>` and `--asset <id>=<file>`. The ID
must match the declaration, and the direct regular-file bytes must match its
declared SHA-256. These flags are explicit call inputs; they are never
serialized into identities and are never discovered.

Every operation reads only its explicitly named direct-file inputs. The
minimum command/input matrix is defined normatively in the specification.
Atomic same-directory replacement applies to selected outputs, but no
operation discovers an alternative input or silently repairs a mismatch.

## 8. Explicit non-goals

This contract does not:

- modify `cozy.presentation-semantics.catalog.v1`, `cozy.visual-page.*`,
  `cozy.video.storyboard.*`, `cozy.media.cross-review.v1`, Slide IR, media
  receipts, or review state;
- select a Subject Pattern, Explanation Pattern, Visual Pattern, renderer, or
  audiovisual policy on behalf of an author;
- infer, summarize, paraphrase, or author claims, logical graphs, mappings, or
  narration;
- define coordinates, shapes, fonts, colors, timing, transitions, PPTX, MP4,
  HTML, infographic, Mermaid, PlantUML, SVG, or renderer behavior above the
  existing boundaries; or
- record semantic, visual, audiovisual, publication, deployment, or external
  consumer approval.
