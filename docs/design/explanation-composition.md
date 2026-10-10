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
  -> named VisualPageSet/Storyboard deterministic Projection
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

- direct-file parsing with duplicate-field rejection and closed fields outside
  the narrowly defined source/asset material declarations;
- exact explanation-catalog and Subject/Explanation Pattern resolution;
- direct `--explanation-catalog <file>` and
  `--presentation-catalog <file>` companions for every Composition
  `validate`, `inspect`, and `convert` operation, including validation of each
  authored Logical Pattern/Relation graph against the named P36 catalog;
- exact typed parameter and subject-fact validation;
- canonical JSON normalization and deterministic UTF-8 serialization;
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
presentation projection semantics. The supplied current catalog resolves by
ID/revision and validates its actual graph, role, relation, and Visual
definitions. DM14 deletes both full/logical catalog digests and selector hash
comparisons without replacement snapshots. P37 does not redefine that schema.

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

The v1 contracts have separate schemas and typed responsibilities:

| Layer | Schema | Responsibility |
| --- | --- | --- |
| Explanation catalog | `cozy.explanation.catalog.v1` | Closed Subject and Explanation Pattern definitions and typed vocabulary. |
| Composition | `cozy.explanation-composition.v1` | Authored subject selection, explanation selection, facts, steps, parameters, and references. |
| Plan | `cozy.explanation-plan.v1` | Deterministic ordered semantic steps before visual selection. |
| ProjectionMap | `cozy.explanation-projection-map.v1` | Authored independent page and scene step mappings plus catalog/input selectors. |
| Projection | `cozy.explanation-projection.v1` | Copied mappings and actual named catalog and PageSet selectors. |

Catalog `id`/`revision`, Composition `id`, pattern references, and step/page/scene/
resource IDs identify authored semantic values. Plan contains two catalog
selectors, two pattern references, and ordered steps. Each step's parameter
provenance contains only ordered selected values. ProjectionMap contains the two
catalog selectors and independent presentation/video step mappings. Projection
contains those selectors, presentation `{visualPageSet:{id},stepMappings}`, and
video `{stepMappings}`. There is no content identity or hash-only receipt.

Cross-document dependencies are supplied as explicitly named direct files.
Plan loading validates selectors and pattern references and compares typed steps
with the selected Composition expansion. Projection loading compares actual
selectors, PageSet ID, and mappings with the resolved named inputs. These checks
retain authored meaning rather than certify unchanged source content.
The existing Storyboard-v2 literal `source` and `catalog` screen fields resolve
to the named P36 pair without introducing a Storyboard descriptor or derived ID.

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

Composition sources require `{id}` and assets require `{id,mediaType}`. Readers
ignore ordinary extra material fields without parsing, retaining, validating,
or re-emitting them; required field types, uniqueness, and duplicate-key
rejection remain. Exact declaration IDs must have explicit safe direct regular
file bindings, with no symlinked ancestors or leaf. Current valid material
bytes need no declaration digest rebinding. Required IDs, media types, graph
semantics, and direct binding resolution remain independently meaningful.

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
catalog ID/revision remains in Plan and Projection selectors; selectors contain only those semantic fields.

The `expand` operation means deterministic validation, normalization, and
copying of every explicitly authored step, claim, emphasis, Logical
Pattern/Relation value, reference, and parameter selection; it does not
generate or infer a graph, role, claim, or step. Each Plan step contains
`parameterProvenance.values` as the ordered `{name,value}` values used for
that step; names alone are not sufficient provenance. Plan contains selectors
for both named catalogs, exactly `{id,revision}`. Required fields, ordinary
JSON syntax, duplicate keys, and closed fields stay strict with no selector
proof adapter.
The `product-overview`, `product-mechanism`, and `problem-solution` cases
therefore preserve their authored typed roles and Relations in Plan steps;
none generates or infers them.

## 6. Authored ProjectionMap

`cozy.explanation-projection-map.v1` is a distinct closed document between
Plan and final Projection. It is authored and contains only catalog selectors
and independent ordered step mappings:

- presentation mappings contain `stepId` and nonempty ordered `pageIds`;
- video mappings contain `stepId` and nonempty ordered `sceneIds`.

Each section contains only `stepMappings`; every Plan step appears once in
Plan order. Target arrays are nonempty, ordered, unique safe IDs.

ProjectionMap has no resource paths, VisualPageSet or Storyboard content,
renderer state, review/approval state, layout, coordinates, timing,
transitions, narration, media bytes, or generated artifact fields. A mapping
request never discovers or generates its targets. The parent author supplies
the named VisualPageSet and Storyboard only to the later `project` operation.

## 7. Independent Projection and command boundary

`project` receives a ProjectionMap, Composition, Plan, explicitly named
explanation and P36 presentation catalogs, an explicitly named VisualPageSet,
and an explicitly named Storyboard. It verifies catalog selector and authored semantic
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
`--source <id>=<file>` and `--asset <id>=<file>` bindings must preserve
required declaration presence and safe direct resolution in both P37 and P36.
Asset media types must agree. Required declarations and independently resolved
descriptor-relative resources and explicit bindings must be safe direct files.
Disagreement retains `EXPLANATION_PROJECTION_STALE` and field-path diagnostics
and fails before writing output. Additional P36 page provenance remains owned by
P36; copied-byte certification is removed.

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
meaning; `project` verifies graph and provenance linkage.
It writes only the final Projection selected by `--save`. It does
not discover, generate, modify, render, publish, or approve VisualPageSet,
Storyboard, or media.

The final Projection copies both independent mapping sections, catalog
ID/revision selectors, and actual PageSet ID. Named input validity, semantic
value agreement, and operational page/scene resolution remain required. No
receipt, whole-catalog certificate, or replacement snapshot gate is introduced.
Storyboard narration, caption, direction, and formatting remain independent of
valid mapping admission. Projection is not semantic, visual, audiovisual,
publication, or consumer approval.

Whenever an operation's validated direct-file input closure includes declared
sources or assets, the caller supplies each declaration as a repeatable direct
binding exactly once: `--source <id>=<file>` and `--asset <id>=<file>`. The ID
must match the declaration, and the file must be a current safe direct regular
input; no declaration digest rebinding is required. These flags are explicit call inputs; they are never
serialized and are never discovered.

Every operation reads only its explicitly named direct-file inputs. The
minimum command/input matrix is defined normatively in the specification.
Selected outputs use ordinary UTF-8 direct writes with canonical newlines after
validation. Validation errors before writing leave the previous output untouched.
Ordinary write failures may leave partial output; regeneration is recovery.
Operations neither discover alternative inputs nor silently repair mismatches.

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

## 9. Historical decision record

`P37-04/05-DEC-001` (historical, superseded by the current contract) — On 2026-08-28, the developer authorized removal of
`video.storyboard.id` from the P37 Projection contract. The affected Phase is
37, the source tree is based on `73130f0d7111cdef3f2988ee041ad8e97fdb4362`,
the selected option is to bind only the existing Storyboard-v2 canonical
identity, the authorized next state is PLAN, and this decision is consumed.


## Phase 71.5 bounded implementation status

S07 implements the Explanation domain, Projection, Preview, and immediate writer
removal described here. On 2026-10-08 its four existing focused suites completed
with 41 successes, zero failures, canceled, ignored, or pending cases, no warnings,
SBT/wrapper exit 0, and terminal shared serial-lock release. Independent review
and Step/Phase acceptance remain pending. S08 implements the bounded VisualPage conversion and direct semantic Preview
writers. On 2026-10-08 its two existing focused suites completed with 14 successes,
zero failures, canceled, ignored, or pending cases, zero warnings/errors,
SBT/wrapper exit 0, and terminal shared serial-lock release. S09 implements bounded
direct Article/Summary PDF generation and immediate build-input capture removal.
On 2026-10-08 its six existing focused suites completed with 114 successes,
zero failed/canceled/ignored/pending cases, zero aborted suites, zero
warnings/errors, SBT/wrapper exit 0, and terminal shared serial-lock release.
S10 removes Presentation copied-image byte/multiset checks and shared acceptance
staging/backup/rollback, retaining initial asset/target ownership and actual
structural semantics with ordered direct review-state then receipt writes.
On 2026-10-08 its six existing focused suites completed with 113 successes,
zero failed/canceled/ignored/pending cases, zero aborted suites, zero SBT
warnings/errors, SBT/wrapper exit 0, and terminal shared serial-lock release.
Five hostile-XML/DOCTYPE diagnostics are intentional existing negative-fixture
output, not SBT compilation or test failures.
S11 implements direct PresentationMigration and CrossReview output writes and
direct creation of the absent article source package. Its five-suite focused
validation completed on 2026-10-08 with 58 successes, zero
failed/canceled/ignored/pending cases, zero aborted suites, zero SBT
warnings/errors, SBT/wrapper exit 0, and terminal shared serial-lock release.
S11 emitted five intentional hostile-XML/DOCTYPE negative-fixture diagnostics,
separate from SBT compilation or test failures. S12's current JA native route is
planned/not-run; generation, assembly, native inspection, browser EOF and current
listening remain OPEN ([journal](../journal/2026/10/2026-10-08-phase-71.5-ja-main-route.md)).
Remaining Receipt publication/prepared-input
observations and finite publication/runtime/CAR groups, independent
review/Step/Phase acceptance, the same full 34-scene native route, and external
E715-01..04 consumer reconciliation remain pending without an exception or
whole-Phase closure claim.
