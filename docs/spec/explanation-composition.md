# Logical Explanation Composition and Media Projection Specification

Document role: normative behavior contract for the versioned explanation
catalog, authored explanation Composition, deterministic Explanation Plan,
authored ProjectionMap, and independent media Projection.

The stable architecture and ownership boundary are defined in
[`docs/design/explanation-composition.md`](../design/explanation-composition.md).
The words **MUST**, **MUST NOT**, **SHOULD**, and **MAY** are normative. This
document defines a contract for later implementation; it does not claim that
the commands, models, or executable specifications exist.

## 1. Scope and authority

The accepted v1 pipeline is:

```text
named explanation catalog + named P36 presentation catalog + authored Composition
  -> cozy.explanation-plan.v1
  -> cozy.explanation-projection-map.v1
  -> named VisualPageSet + named Storyboard-v2
  -> cozy.explanation-projection.v1
```

The subject, explanation strategy, facts, claims, logical graph, parameters,
and page/scene mappings are authored inputs. Cozy MUST validate, normalize,
copy, project, and reject invalid or semantically mismatched
input. Cozy MUST NOT infer a subject, select a pattern, summarize or
paraphrase meaning, write claims or narration, generate a logical graph,
generate a ProjectionMap, or make semantic, visual, audiovisual, publication,
or consumer-acceptance decisions.

Phase 36 remains authoritative for `cozy.presentation-semantics.catalog.v1`,
Visual Page, Logical Pattern and Relation semantics, Visual Pattern
compatibility, presentation pages, and the Storyboard-v2 screen reference.
Phase 30 remains authoritative for Storyboard narration and production
semantics. This specification does not define or alter those schemas.

## 2. Closed documents and canonical values

### 2.1 Schema envelope

Each P37 document MUST be a closed root object with the schema and version
shown here:

| Document | `schema` | `version` |
| --- | --- | --- |
| Explanation Catalog | `cozy.explanation.catalog.v1` | `1` |
| Composition | `cozy.explanation-composition.v1` | `1` |
| Plan | `cozy.explanation-plan.v1` | `1` |
| ProjectionMap | `cozy.explanation-projection-map.v1` | `1` |
| Projection | `cozy.explanation-projection.v1` | `1` |

Unknown root or nested fields, duplicate JSON object fields, malformed UTF-8,
malformed JSON, `null`, non-finite numbers, unsupported scalar types, and a
wrong schema/version MUST be rejected before a typed value is exposed.
Source and asset declaration objects retain their existing ordinary extra-metadata
loading; their required fields and duplicate-key checks remain strict. Catalog
selectors contain exactly `id` and `revision` with no extra-field adapter.

### 2.2 Canonical JSON

Canonical JSON MUST be UTF-8, contain no insignificant whitespace, use the
field order declared in this specification, preserve semantic array order,
and emit all and only defined fields. Input object key order does not change
meaning, but duplicate and unknown keys are errors. Canonical serialization
follows strict parsing and typed normalization.

For the explanation catalog, strict parsing and duplicate validation MUST
complete before array normalization. The canonical `subjectPatterns` and
`explanationPatterns` arrays are ordered ascending by `(id,version)`; each
pattern's `roles` array is ordered ascending by `order`; `factDefinitions` and
`parameterDefinitions` arrays are ordered by `name`; `compatibleSubjects` is
ordered ascending by `(id,version)`; and
`reservedNarrativeArgumentIds` preserves its specified fixed Narrative /
Argument order. These catalog input arrays are normalized to those canonical
orders only after strict parsing and duplicate validation, so equivalent
catalog values have the same canonical serialization. Semantic authored
arrays—subject facts, explanation parameters and steps, claims, logical
nodes/Relations, references, and mapping targets—retain their authored or
contract-defined order and MUST NOT be reordered by catalog normalization.

Semantic arrays—facts, parameters, authored steps, claims, nodes, Relations,
references, and mapping targets—retain authored or normalized order. Catalog
definition arrays are emitted in the deterministic order specified below.
Duplicate IDs and duplicate names MUST be
rejected rather than silently deduplicated.

### 2.3 Semantic selectors and direct inputs

The catalog `id` and `revision`, authored Composition `id`, pattern references,
and step/page/scene/resource IDs describe actual authored meaning. Plan contains
exactly the two catalog selectors, Subject and Explanation Pattern references,
and ordered steps. ProjectionMap contains the two catalog selectors and ordered
presentation/video mapping sections. Projection contains those selectors,
presentation `{visualPageSet:{id},stepMappings}`, and video `{stepMappings}`.
Parameter provenance contains only the ordered selected `values`.

Dependencies are supplied as explicitly named direct files. Plan loading checks
catalog ID/revision and pattern references, then compares its typed steps with
`expand` of the selected Composition. Projection loading compares meaningful
selectors, PageSet ID, and mapping values with the resolved named inputs. These
checks validate authored meaning; they are not unchanged-content certificates.
No hash-only receipt, content identity, replacement proof, schema bump, or
legacy protection adapter belongs to these v1 contracts.

## 3. Explanation Catalog contract

### 3.1 Root and definitions

The canonical explanation-catalog root has exactly this field order and has no
root `identity` field:

```json
{
  "schema": "cozy.explanation.catalog.v1",
  "version": 1,
  "id": "<catalog-id>",
  "revision": 1,
  "subjectPatterns": [],
  "explanationPatterns": [],
  "reservedNarrativeArgumentIds": []
}
```

`id` is a nonempty stable token, `revision` is a positive integer, and both
pattern arrays are nonempty. The reserved array contains unique stable tokens
in the order defined in Section 3.4.

Each `subjectPatterns` entry has exactly:

```json
{
  "id": "<subject-pattern-id>",
  "version": 1,
  "factDefinitions": [
    {"name":"<fact-name>","type":"text","required":true}
  ]
}
```

Each `explanationPatterns` entry has exactly:

```json
{
  "id": "<explanation-pattern-id>",
  "version": 1,
  "compatibleSubjects": [{"id":"<subject-pattern-id>","version":1}],
  "parameterDefinitions": [
    {"name":"<parameter-name>","type":"text","required":true}
  ],
  "roles": [{"id":"<role>","order":1,"required":true}]
}
```

Definition names and role IDs are unique and nonempty. `roles.order` is a
positive contiguous integer beginning at `1`; pattern arrays are emitted by
`(id,version)`, role arrays by ascending `order`, definition arrays by name,
and compatible-subject arrays by `(id,version)`. The reserved
Narrative / Argument ID array is emitted in its specified fixed order. A
definition has no default, alias, nullable form, or dynamic expression.
Optional definitions, if introduced in a later catalog revision, remain
absent when omitted; no omission requests an inferred default.

### 3.2 Closed v1 value vocabulary

The closed v1 type vocabulary used by the accepted `software-product` catalog
is exactly the following; no placeholder type or additional type is accepted:

| Type | Exact value |
| --- | --- |
| `text` | A nonempty UTF-8 string. |
| `goal-list` | A nonempty ordered array of entries exactly `{id,label,sourceRefs,assetRefs}`. |
| `use-case-list` | A nonempty ordered array of entries exactly `{id,label,sourceRefs,assetRefs}`. |
| `scenario` | An object exactly `{id,label,steps}`. `steps` is a nonempty ordered array whose entries are exactly `{id,label,sourceRefs,assetRefs}`. |
| `mechanism-list` | A nonempty ordered array of entries exactly `{id,label,sourceRefs,assetRefs}`. |
| `mechanism-link-list` | A nonempty ordered array of entries exactly `{id,goalId,useCaseId,mechanismId,sourceRefs,assetRefs}`. |

For every list entry, `id` and `label` are nonempty text values and
`sourceRefs` and `assetRefs` are ordered arrays of nonempty declared IDs.
Scenario and list IDs are unique within their value. A mechanism-link
`goalId`, `useCaseId`, and `mechanismId` are nonempty IDs; their exact
resolution against the selected facts is required for `product-mechanism`.

The seven required `software-product` facts are exactly:

| Fact | Type | Required |
| --- | --- | --- |
| `name` | `text` | `true` |
| `vision` | `text` | `true` |
| `goals` | `goal-list` | `true` |
| `context` | `text` | `true` |
| `useCases` | `use-case-list` | `true` |
| `mainScenario` | `scenario` | `true` |
| `mechanisms` | `mechanism-list` | `true` |

The selected catalog MUST define exactly these seven facts for
`software-product`, with no additional v1 fact; its canonical
`factDefinitions` array is ordered by `name` as specified in Section 2.2.

### 3.3 Accepted v1 patterns

The accepted v1 catalog subset MUST define these exact entries:

| Layer | `id` | Compatibility | Parameters | Exact ordered roles |
| --- | --- | --- | --- | --- |
| Subject Pattern | `software-product` | — | — | — |
| Explanation Pattern | `product-overview` | `software-product` version `1` | none | `vision`, `goal`, `context`, `use-case`, `main-scenario` |
| Explanation Pattern | `product-mechanism` | `software-product` version `1` | required `mechanismLinks:mechanism-link-list` | `mechanism` |
| Explanation Pattern | `problem-solution` | `software-product` version `1` | required `problem:text`, `solution:text` | `problem`, `solution` |

`product-overview` has an empty `parameterDefinitions` array. Its authored
steps MUST have an empty `parameterSelection` array. `product-mechanism` has
one required `mechanismLinks` definition, and its `mechanism` step MUST select
that parameter. `problem-solution` requires both `problem` and `solution`
text parameters; its `problem` and `solution` steps select the corresponding
parameter by name. Parameter values are never defaulted, coerced, or inferred.

An Explanation Pattern is compatible only when its `compatibleSubjects`
contains the exact selected Subject Pattern `id`/`version` pair in the same
explicit explanation catalog `id`/`revision`. A same-ID different-version,
other-catalog, or other-revision pattern is incompatible.

### 3.4 Reserved Narrative / Argument IDs

The catalog's reserved list is exactly these sixteen unique IDs:

```text
problem-solution
problem-cause-solution
current-target
before-after
challenge-approach-result
observation-insight-implication
fact-interpretation-action
why-what-how
input-process-output
concept-example
claim-evidence
claim-reasons
question-answer
principle-mechanism-effect
strategy-execution-outcome
past-present-future
```

Only `problem-solution` is defined and accepted in v1. A reserved but
unimplemented ID, an unknown ID, or an ID absent from the selected catalog
revision MUST be rejected. A later catalog revision is required to define a
new entry; a caller or renderer cannot extend a selected revision.

## 4. Composition contract

### 4.1 Root shape

The canonical Composition root has exactly this field order and has no root
`identity` field:

```json
{
  "schema": "cozy.explanation-composition.v1",
  "version": 1,
  "id": "<composition-id>",
  "explanationCatalog": {
    "id":"<catalog-id>",
    "revision":1
  },
  "subject": {
    "pattern":{"id":"software-product","version":1},
    "facts":[]
  },
  "explanation": {
    "pattern":{"id":"product-overview","version":1},
    "parameters":[],
    "steps":[]
  },
  "sources":[],
  "assets":[]
}
```

`explanationCatalog` is exactly `{id,revision}` and resolves the explicitly
supplied catalog. `subject`
has exactly `pattern` and `facts`; `explanation` has exactly `pattern`,
`parameters`, and `steps`.

Each fact has exactly `{name,value,sourceRefs,assetRefs}`. Each explanation
parameter has exactly `{name,value}`. Names are unique and MUST be declared by
the selected catalog definitions. The value MUST have its exact catalog type;
there is no string-to-number, scalar-to-list, record, default, or nullable
coercion.

Sources require `{id}` and assets require `{id,mediaType}`. IDs are unique and
`mediaType` is nonempty text. Ordinary extra material fields, including malformed
obsolete metadata, are ignored without parsing, validating, retaining, or
re-emitting them; missing or ill-typed required fields and duplicate keys
reject. Other Composition object shapes remain closed. No source or asset
path is serialized. For every operation whose validated direct-file input
closure includes declared sources or assets, the caller MUST provide each
declaration exactly once using the repeatable bindings `--source <id>=<file>`
and `--asset <id>=<file>`. The ID MUST match the declaration, and `<file>` MUST
name a safe direct regular file with neither symlinked ancestors nor a
symlinked leaf. Current valid bytes require no declaration digest rebinding.
These flags are explicit call inputs, MUST NOT be serialized into any P37
document, and MUST NOT be discovered.

### 4.2 Authored explanation steps

`explanation.steps` is an explicitly authored, nonempty ordered array. Each
step has exactly these fields, in this order:

```json
{
  "id":"<step-id>",
  "order":1,
  "semanticRole":"<catalog-role>",
  "claims":[
    {
      "id":"<claim-id>",
      "text":"<authored-claim>",
      "emphasis":"supporting",
      "sourceRefs":[],
      "assetRefs":[]
    }
  ],
  "logical":{
    "pattern":"<logical-pattern-id>",
    "nodes":[
      {
        "id":"<node-id>",
        "role":"<node-role>",
        "label":"<node-label>",
        "sourceRefs":[]
      }
    ],
    "relations":[
      {
        "id":"<relation-id>",
        "type":"<typed-relation>",
        "from":"<node-id>",
        "to":"<node-id>",
        "sourceRefs":[]
      }
    ]
  },
  "sourceRefs":[],
  "assetRefs":[],
  "parameterSelection":[{"name":"<parameter-name>"}]
}
```

`logical` is exactly `{pattern,nodes,relations}`. Its `pattern` is the stable
string P36 logical pattern ID, not an `{id,version}` object; P37 makes no
version claim for a P36 logical pattern entry. Each node is exactly
`{id,role,label,sourceRefs}`, and each Relation is exactly
`{id,type,from,to,sourceRefs}`. Logical nodes and Relations have no
`assetRefs`; the `assetRefs` fields on claims, facts, and P37 step/root
provenance remain separate P37 data. The P36 catalog ID/revision
binding is carried by the Plan and Projection selectors.

`claims` is nonempty. Claim `emphasis` is the closed enum exactly
`supporting|primary`; no other spelling or value is accepted. IDs, roles,
claim text, node labels, Relation types, and all references are nonempty and
stable. Node and Relation arrays preserve authored order. Logical pattern,
node roles, Relation endpoints, direction, topology, and cardinality MUST
resolve against the explicitly supplied Phase-36 presentation catalog.

The step `order` values are contiguous positive integers beginning at `1`.
Step IDs, claim IDs, node IDs, and Relation IDs are unique in their defined
scope. Each source or asset reference resolves exactly once. Each step's
`parameterSelection` contains unique parameter names declared in
`explanation.parameters`, in authored order. The selected Explanation Pattern
roles and the authored steps MUST agree exactly in ordered role and required
presence; missing, extra, or inferred steps are rejected.

The Composition loader MUST receive both explicitly named
`--explanation-catalog <file>` and `--presentation-catalog <file>` companions.
It MUST validate every authored `logical` value against that named P36
presentation catalog before exposing the Composition. It MUST resolve exactly
one selected catalog, Subject Pattern, and Explanation Pattern. It MUST reject
missing required facts or parameters, unknown names, duplicate names, unknown
pattern versions, ill-typed values, incompatible patterns, invalid mechanism
links, malformed logical graphs, and unbound references. It MUST preserve
authored claims, emphasis, logical nodes and Relations, references, and
parameter selections; it MUST NOT fill, sort, merge, rewrite, summarize,
paraphrase, or infer them.

## 5. Explanation Plan contract

### 5.1 Root shape

The canonical Plan root has exactly this field order:

```json
{
  "schema": "cozy.explanation-plan.v1",
  "version": 1,
  "explanationCatalog": {
    "id": "<catalog-id>",
    "revision": 1
  },
  "presentationCatalog": {
    "id": "<p36-catalog-id>",
    "revision": 1
  },
  "subjectPattern": {
    "id": "<subject-pattern-id>",
    "version": 1
  },
  "explanationPattern": {
    "id": "<explanation-pattern-id>",
    "version": 1
  },
  "steps": []
}
```

Both Plan catalog selectors are exactly `{id,revision}` and resolve the
explicitly supplied catalogs. Pattern references MUST match the selected
Composition exactly. Typed `steps` MUST equal the actual validated Composition
expansion, preserving authored claims, graphs, references, and parameter values.
`steps` is nonempty and ordered by contiguous positive `order` values.

### 5.2 Plan step shape

Each Plan step has exactly these fields, in this order:

```json
{
  "id": "<step-id>",
  "order": 1,
  "semanticRole": "<catalog-role>",
  "claims": [
    {
      "id": "<claim-id>",
      "text": "<authored-claim>",
      "emphasis": "primary",
      "sourceRefs": [],
      "assetRefs": []
    }
  ],
  "logical": {
    "pattern": "<logical-pattern-id>",
    "nodes": [
      {
        "id": "<node-id>",
        "role": "<node-role>",
        "label": "<node-label>",
        "sourceRefs": []
      }
    ],
    "relations": [
      {
        "id": "<relation-id>",
        "type": "<typed-relation>",
        "from": "<node-id>",
        "to": "<node-id>",
        "sourceRefs": []
      }
    ]
  },
  "sourceRefs": [],
  "assetRefs": [],
  "patternProvenance": {
    "subjectPattern": {
      "id": "<subject-pattern-id>",
      "version": 1
    },
    "explanationPattern": {
      "id": "<explanation-pattern-id>",
      "version": 1
    }
  },
  "parameterProvenance": {
    "values": [
      {
        "name": "<parameter-name>",
        "value": "<typed-value>"
      }
    ]
  }
}
```

The example `value` above is schematic only; its actual JSON value MUST have
the exact catalog-declared type. `parameterProvenance.values` is an ordered
copy of the selected `{name,value}` values, not a names-only list. The `expand` operation means deterministic strict
validation, normalization, and copying of the explicitly authored Composition
steps, claims, logical Pattern/Relation values, references, and parameter
values into ordered Plan steps. It never generates or infers a logical graph,
semantic role, claim, or step. Plan expansion copies authored claim text,
emphasis, logical pattern, nodes, Relations, references, and parameter values
without inference or lossy conversion.

The Plan step `logical` value retains the exact P36-compatible shape
`{pattern,nodes,relations}`: the pattern is a stable string ID, and nodes and
Relations have only their P36 fields, including `sourceRefs` but no
`assetRefs`. No P36 logical-pattern version is asserted here; its catalog
ID/revision binding remains in the Plan `presentationCatalog` selector.

### 5.3 Expansion behavior

`expand` MUST be deterministic for one unchanged Composition and the two
explicitly supplied catalogs. It MUST produce identical canonical Plan bytes
on repeated execution. It MUST perform only deterministic
validation, normalization, and copying of explicitly authored steps and their
Phase-36 Logical Pattern and Relation graph before any visual selection. It
MUST reject unsupported, ambiguous, incomplete, non-deterministic, or lossy
input; it MUST NOT generate or infer any Logical Pattern, Relation, semantic
role, claim, or step.

The Plan contains no Visual Pattern, page ID, scene ID, coordinate, layout,
font, color, timing, transition, renderer ID, PPTX, MP4, or output-file field.
A Narrative / Argument pattern's explicitly authored semantic roles and typed
Relations are preserved by that deterministic validation, normalization, and
copying; `expand` never generates them and never maps directly to arrows,
cards, coordinates, or PowerPoint Shape kinds.
The v1 `product-overview`, `product-mechanism`, and `problem-solution` cases
follow this same rule: their authored typed roles and Relations are copied
unchanged into the corresponding Plan steps.

## 6. Authored ProjectionMap contract

### 6.1 Root shape

The canonical ProjectionMap root has exactly this field order:

```json
{
  "schema": "cozy.explanation-projection-map.v1",
  "version": 1,
  "explanationCatalog": {
    "id": "<catalog-id>",
    "revision": 1
  },
  "presentationCatalog": {
    "id": "<p36-catalog-id>",
    "revision": 1
  },
  "presentation": {
    "stepMappings": []
  },
  "video": {
    "stepMappings": []
  }
}
```

The presentation and video sections contain only `stepMappings`. Their exact
closed mapping entries are:

```json
{"stepId":"<plan-step-id>","pageIds":["<page-id>"]}
```

and

```json
{"stepId":"<plan-step-id>","sceneIds":["<scene-id>"]}
```

Each mapping array contains exactly one entry per Plan step in Plan order;
target arrays are nonempty, ordered, duplicate-free stable IDs. Presentation
and video target counts are independent. `stepId` values MUST match Plan
steps exactly. Both catalog selectors are exactly `{id,revision}` and validate
named catalog references.

ProjectionMap carries only catalog selectors and independently authored
page/scene step mappings. It MUST NOT contain a resource path, VisualPageSet,
Storyboard or media content, renderer state, review/approval state, layout,
coordinate, timing, transition, narration, generated artifact, or publication
field. It MUST NOT discover, generate, modify, render, publish, or approve a
target.

### 6.2 ProjectionMap validation

The loader MUST validate the named Composition and Plan against both explicit
catalog files and require exact catalog selector agreement. It MUST reject a
missing, extra, reordered, duplicate, or unknown step mapping, a target ID with
unsafe syntax, or a mapping that changes the authored semantic plan. No
best-effort mapping is returned after rejection.

## 7. Final Projection contract

### 7.1 Root shape

The canonical final Projection root has exactly this field order:

```json
{
  "schema": "cozy.explanation-projection.v1",
  "version": 1,
  "explanationCatalog": {
    "id": "<catalog-id>",
    "revision": 1
  },
  "presentationCatalog": {
    "id": "<p36-catalog-id>",
    "revision": 1
  },
  "presentation": {
    "visualPageSet": {
      "id": "<set-id>"
    },
    "stepMappings": []
  },
  "video": {
    "stepMappings": []
  }
}
```

The final Projection copies the ProjectionMap's ordered presentation and video
`stepMappings` exactly. Catalog selectors are `{id,revision}`; the PageSet selector
is `{id}`. All objects remain closed with the existing v1 schema/version.

The Storyboard is an explicitly named direct input validated against its actual
v2 schema, scene IDs, and visual-page screens. Its literal screen source/catalog
paths resolve to the named files. Projection introduces no Storyboard descriptor
or derived ID.

### 7.2 Project behavior and media agreement

`project` receives exactly these named direct-file inputs: a ProjectionMap,
Composition, Plan, explanation catalog, P36 presentation catalog, VisualPageSet,
and Storyboard. It MUST validate the Composition and Plan's actual authored
meaning, ProjectionMap mappings, and both catalog ID/revision selectors before
writing only the final Projection selected by `--save`.

Every page ID MUST resolve exactly once in the named VisualPageSet. For every
Plan step -> mapped page ID, that exact page MUST resolve in the explicitly
named VisualPageSet and its P36 `logical` value MUST equal the Plan step's
P36-compatible `logical` value exactly. Every source or asset reference
carried anywhere by that Plan step—the step itself, its claims, and its
logical nodes or Relations as applicable—MUST occur in the resolved P36 page's
`sources` or `assets` declarations. The existing P37 explicit
`--source <id>=<file>` and `--asset <id>=<file>` bindings MUST be used to
require declaration presence and safe direct resolution in both P37 bindings
and the resolved P36 declarations. Asset media types MUST agree. Required descriptor resources and explicit bindings
must independently resolve to safe direct files. Missing declarations, invalid
paths, or disagreeing media types remain `EXPLANATION_PROJECTION_STALE` with
field-path diagnostics and fail before writing output. Extra P36 page provenance
remains governed by P36.

Every scene ID MUST resolve exactly once in the named Storyboard. For every
Plan step -> mapped scene ID, the resolved scene MUST contain an existing
Storyboard-v2 `visual-page` screen. Its literal `source`, `catalog`, and
`pageId` fields MUST resolve to the named VisualPageSet and named P36 catalog
pair, and its `pageId` MUST be one of that same Plan step's presentation
`pageIds`. Multiple scenes MAY reference the same page, and a Plan step MAY
retain pages that no video scene uses. Page and scene cardinalities therefore
remain independent; this contract introduces no one-to-one requirement. Each
supplied Storyboard-v2 visual screen reference MUST retain its literal P36/P30
fields, and P37 MUST NOT rewrite or infer them.

P37 preserves authored claims in the Plan. It MUST NOT add claim text to a P36
VisualPage, which has no claim field, and it MUST NOT self-approve semantic
meaning; `project` verifies graph and provenance linkage.

`project` MUST NOT discover, generate, modify, render, publish, or approve the
VisualPageSet, Storyboard, or any media. It only writes the selected final
Projection. Page and scene cardinalities remain independent.

Storyboard narration, caption, direction, or formatting changes alone MUST NOT
veto valid actual media mappings. Malformed inputs, unresolved page/scene mappings,
and missing required files still fail. Projection is not semantic, visual,
audiovisual, publication, deployment, or consumer approval.

## 8. Direct-file command/input matrix

Every command reads only the explicitly named files below. There is no
`.cozy/config/discovery` and no fallback, crawling, or alternative catalog
lookup. `<file>` denotes the exact path supplied by the caller; the path is a
command input, not a P37 semantic identity field.

Common declared-resource companion rule: every row below whose validated
input closure includes one or more declared sources or assets MUST also
receive one `--source <id>=<file>` for each declared source and one
`--asset <id>=<file>` for each declared asset, each exactly once. Each binding names a safe direct
regular file. These flags are explicit call inputs, are not serialized, and are never
discovered. Catalog-only rows have no declared-resource bindings; this rule
is in addition to the named catalog, Composition, Plan, ProjectionMap,
VisualPageSet, and Storyboard companions shown in the matrix.

| Operation and input | Required named companions | Selected output |
| --- | --- | --- |
| `validate <explanation-catalog>` | none | none |
| `inspect <explanation-catalog>` | none | none |
| `convert <explanation-catalog> --save <catalog-out>` | none | `<catalog-out>` |
| `validate <composition>` | `--explanation-catalog <file> --presentation-catalog <file>` | none |
| `inspect <composition>` | `--explanation-catalog <file> --presentation-catalog <file>` | none |
| `convert <composition> --explanation-catalog <file> --presentation-catalog <file> --save <composition-out>` | `--explanation-catalog <file> --presentation-catalog <file>` | `<composition-out>` |
| `validate <plan>` | `--composition <file> --explanation-catalog <file> --presentation-catalog <file>` | none |
| `inspect <plan>` | `--composition <file> --explanation-catalog <file> --presentation-catalog <file>` | none |
| `convert <plan> --composition <file> --explanation-catalog <file> --presentation-catalog <file> --save <plan-out>` | same three named companions | `<plan-out>` |
| `expand <composition> --explanation-catalog <file> --presentation-catalog <file> --save <plan-out>` | both catalogs | `<plan-out>` |
| `validate <projection-map>` | `--composition <file> --plan <file> --explanation-catalog <file> --presentation-catalog <file>` | none |
| `inspect <projection-map>` | `--composition <file> --plan <file> --explanation-catalog <file> --presentation-catalog <file>` | none |
| `convert <projection-map> --composition <file> --plan <file> --explanation-catalog <file> --presentation-catalog <file> --save <map-out>` | same four named companions | `<map-out>` |
| `validate <projection>` | `--composition <file> --plan <file> --projection-map <file> --explanation-catalog <file> --presentation-catalog <file> --visual-page-set <file> --storyboard <file>` | none |
| `inspect <projection>` | same seven named companions | none |
| `convert <projection> --composition <file> --plan <file> --projection-map <file> --explanation-catalog <file> --presentation-catalog <file> --visual-page-set <file> --storyboard <file> --save <projection-out>` | same seven named companions | `<projection-out>` |
| `project --projection-map <file> --composition <file> --plan <file> --explanation-catalog <file> --presentation-catalog <file> --visual-page-set <file> --storyboard <file> --save <projection-out>` | ProjectionMap, Composition, Plan, both catalogs, named VisualPageSet, named Storyboard | `<projection-out>` only |
| `verify-projection <projection>` | `--composition <file> --plan <file> --projection-map <file> --explanation-catalog <file> --presentation-catalog <file> --visual-page-set <file> --storyboard <file>` | none |

`validate`, `inspect`, and `convert` use the same companion requirements for
each document kind. Selected outputs use ordinary UTF-8 direct writes with a
canonical trailing newline after validation. Validation errors before writing
leave previous output untouched; ordinary write failures may leave partial output
and regeneration is recovery. No command starts a server, invokes a renderer, builds PPTX or
MP4, modifies media, publishes, or records approval.

## 9. Diagnostics, invariants, and non-goals

Diagnostics MUST be structured records containing a stable category, field
path, and concise reason. At minimum, implementations MUST distinguish:

- `EXPLANATION_SCHEMA_INVALID` for wrong schema/version or malformed input;
- `EXPLANATION_DUPLICATE_FIELD` and `EXPLANATION_UNKNOWN_FIELD` for closed
  object violations;
- `EXPLANATION_CATALOG_MISMATCH` and `EXPLANATION_PATTERN_INCOMPATIBLE` for
  catalog or exact pair failures;
- `EXPLANATION_PARAMETER_INVALID` and `EXPLANATION_FACT_INVALID` for missing,
  unknown, or ill-typed values;
- `EXPLANATION_REFERENCE_INVALID` and `EXPLANATION_ASSET_STALE` for unsafe or
  changed references;
- `EXPLANATION_EXPANSION_INCOMPLETE` for ambiguous, unsupported, or lossy
  plans; and
- `EXPLANATION_PROJECTION_STALE` for missing or semantically mismatched
  projection inputs.

The following remain unchanged outside this contract:

- `cozy.presentation-semantics.catalog.v1`, `cozy.visual-page.*`,
  `cozy.video.storyboard.*`, `cozy.media.cross-review.v1`, Slide IR, media
  receipts, and review-state schemas and behavior;
- Storyboard speaker, narration, timing, transition, render, and audiovisual
  review authority; and
- the separation of logical Composition, Plan, ProjectionMap, Visual Page,
  Storyboard, renderer, PPTX, MP4, publication, and external-consumer
  acceptance layers.

This v1 contract does not define Scala models, executable specifications,
renderers, layout, coordinates, fonts, colors, Shape kinds, timing,
transitions, narration synthesis, PPTX, MP4, HTML, infographic, Mermaid,
PlantUML, SVG, publication, deployment, or review approval. Those concerns
remain separate contracts and later work.


## Phase 71.5 bounded implementation status

S07 reconciles Explanation, Plan, ProjectionMap, Projection, and Preview with
these protection-free typed shapes and direct writes. On 2026-10-08 its four
existing focused suites completed with 41 successes, zero failures, canceled,
ignored, or pending cases, no warnings, SBT/wrapper exit 0, and terminal shared
serial-lock release. Independent review and Step/Phase acceptance remain pending.
S08 implements the bounded VisualPage conversion and direct semantic Preview
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
