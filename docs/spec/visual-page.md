# Visual Page Specification

Status: NORMATIVE; Phase 36 `VIS36-01` is DONE

This is the normative contract for the future Visual Page route. It defines
semantic values, compatibility boundaries, the independent direct core
parse/validation/canonicalization CLI, and the explicit legacy Slide IR
migration map. It claims no renderer, receipt, Presentation-route integration,
Storyboard-route integration, review, or external-consumer acceptance.

The words **MUST**, **MUST NOT**, **SHOULD**, and **MAY** are normative. The
ownership design is [`docs/design/visual-page.md`](../design/visual-page.md).

## 1. Scope and v1 coexistence

A `VisualPage` is one typed, one-screen semantic authority. It is not a
PowerPoint slide, video frame, template, renderer object graph, or generated
artifact. A `VisualPageSet` is the ordered unit consumed by the future
presentation route; a future video screen identifies one page in such a set.

`cozy.slide-ir.v1`, `cozy.presentation.render.v1`,
`cozy.video.storyboard.v1`, `cozy.media.v1`, `cozy.media.receipt.v2`, and
`cozy.media.review-state.v1` retain their accepted behavior unchanged. No
parser or route may silently upgrade, reinterpret, or select this contract for
a v1 source. A distinct later implementation must accept the new v2 route.

## 2. VisualPage and VisualPageSet values

The Visual Page schema is exactly `cozy.visual-page.v1` with integer
`version: 1`. Its normalized root contains exactly the following fields in
this canonical order:

```text
schema, version, id, knowledge, language, catalog, logical, visual, assets, sources
```

| Field | Contract |
| --- | --- |
| `schema` | Exactly `cozy.visual-page.v1`. |
| `version` | Integer exactly `1`. |
| `id` | Stable page identity. |
| `knowledge` | Required semantic knowledge identity. |
| `language` | Required semantic language identity. |
| `catalog` | Exactly `{id, revision}`; selects a separately supplied resolved catalog. |
| `logical` | Exactly `{pattern, nodes, relations}`. |
| `visual` | Exactly `{pattern, parameters}`. |
| `assets` | Ordered declarations of exactly `{id, path, mediaType, sha256}`. |
| `sources` | Ordered declarations of exactly `{id, path}`. |

`catalog` resolves a separately supplied versioned catalog document with
schema exactly `cozy.presentation-semantics.catalog.v1`, schema version
exactly `1`, matching catalog `id`, and positive catalog `revision`. Its
canonical root field order is exactly `schema, version, id, revision,
relations, logicalPatterns, visualPatterns`. The resolved catalog identity is
the SHA-256 of canonical JSON for that complete resolved catalog; its logical
catalog identity is the SHA-256 of canonical JSON containing only `schema`,
`version`, `id`, `revision`, `relations`, and `logicalPatterns`. Resolution
failure, mismatch, or stale identity fails closed.

Every logical node has exactly `id`, `role`, `label`, and `sourceRefs`; every
logical relation has exactly `id`, `type`, `from`, `to`, and `sourceRefs`.
Node and relation IDs are unique stable tokens. `sourceRefs` resolve exactly
once to the declared source IDs; `from` and `to` resolve exactly once to
declared nodes. Unknown, duplicate, or unbound references fail closed.

Source and asset IDs are unique stable tokens. Their paths are safe project- or
descriptor-relative POSIX references. Validation rejects an absolute path,
traversal, URI-like spelling, control character, backslash, symlink-escaping
resolution, and unknown reference. A lexically safe path that resolves through
a symlink outside its allowed root is unsafe.

Every asset declaration's `sha256` is the lowercase SHA-256 digest of its
resolved direct regular asset-file bytes. Validation reads those resolved bytes
and rejects a digest mismatch. Consequently, a same-path asset byte change
without a corresponding declared digest update fails closed; a correct digest
update changes `visualPageIdentity` and makes dependent visual evidence stale.

No Visual Page semantic field may contain coordinates, fonts, colors,
PowerPoint shapes, transitions, or renderer IDs.

`visual.pattern` selects a Visual Pattern. `visual.parameters` is the closed
typed object declared by that exact catalog entry. A parameter's JSON type or
reference type, optionality, and no-default behavior are catalog identity.
There are no inferred defaults, unknown values, aliases, or renderer-specific
values. A missing required parameter, unknown parameter, ill-typed value,
alias, or implicit default fails closed.

A Visual Page Set root contains exactly `schema`, `version`, `id`, and `pages`.
It has schema exactly `cozy.visual-page-set.v1`, integer `version: 1`, a stable
`id`, and ordered nonempty `pages` containing full VisualPage values. Page
sequence is semantic, and every contained page `id` is unique. Every contained
page MUST declare the same catalog `{id, revision}` pair. Set resolution accepts
exactly one supplied resolved catalog whose pair and full resolved identity match
that pair; a mixed pair, unresolved catalog, or mismatch fails closed.
Presentation therefore binds every page deterministically through its one
catalog/binding pair. A future v2 video screen identifies one page in a safe
referenced set by source path and page ID.

## 3. Closed core catalog

The core catalog is closed. New Relations, patterns, roles, cardinalities, or
parameter forms require a new catalog revision. A selected revision MUST NOT
be extended by convention, inference, or renderer behavior.

### 3.1 Relations

The closed `relations` array contains exactly five entries, each exactly
`{id, direction}` with `direction: from-to`, in this canonical relation order:

```json
[
  { "id": "next", "direction": "from-to" },
  { "id": "causes", "direction": "from-to" },
  { "id": "depends-on", "direction": "from-to" },
  { "id": "enables", "direction": "from-to" },
  { "id": "maps-to", "direction": "from-to" }
]
```

Inverse spellings and aliases are rejected rather than rewritten. A renderer
may draw identical arrows, but it must not erase distinct relation identities.

### 3.2 Logical Patterns

Each `logicalPatterns` entry is exactly `{id, nodeRoles, relationRules}`. Each
`nodeRoles` entry is exactly `{role, min, max}`. Each `relationRules` entry is
exactly `{relation, fromRoles, toRoles, min, max, topology}`. `min` and `max`
are literal inclusive integer cardinalities; `fromRoles` and `toRoles` preserve
sorted canonical token order; and `topology` is exactly one of `linear`,
`acyclic`, or `bipartite`. Dynamic expressions are not serialized catalog
values.

The initial closed logical entries permit no extra nodes or relations:

| Pattern | Exact node roles | Exact relation rules |
| --- | --- | --- |
| `sequence` | `step` min=2, max=8 | `next`, `step` -> `step`, min=1, max=7, `linear`. |
| `causal-chain` | `cause` min=1, max=7; `effect` min=1, max=7; total min=2, max=8 | `causes`, then `enables`, `cause` -> `effect`, each min=1, max=16, `acyclic`. |
| `dependency-map` | `dependency` min=1, max=7; `dependent` min=1, max=7; total min=2, max=8 | `depends-on`, `dependent` -> `dependency`, min=1, max=16, `acyclic`. |
| `mapping` | `source` min=1, max=7; `target` min=1, max=7; total min=2, max=8 | `maps-to`, `source` -> `target`, min=1, max=16, `bipartite`. |

For `linear`, the exact `step` node set is one directed connected chain with
one start, one end, one incoming and one outgoing relation for each internal
node, no duplicate endpoint pairs, and exactly step-count minus one `next`
relations. For `acyclic` and `bipartite`, validators prohibit duplicate
same-type endpoint pairs and enforce all endpoints and the stated role/total
cardinalities; consequently, observed relation count cannot exceed the eligible
endpoint-pair count even though the static maximum is 16. A relation, role,
cardinality, topology, or required exact topology invalid for the selected
pattern fails closed.

Canonical catalog arrays have exact order: root order remains as stated above;
`relations` use the listed order; `logicalPatterns` are `sequence`,
`causal-chain`, `dependency-map`, `mapping`; and `visualPatterns` are
`flow-horizontal`, `flow-vertical`, `mapping-columns`. `nodeRoles` sort
lexicographically by `role`; `relationRules` sort by `relation`, then
lexicographic `fromRoles`, then `toRoles`; each role array and each
`compatibleLogicalPatterns` array sort lexicographically; parameter arrays sort
lexicographically by `name`. Duplicate array entries reject. These orders are
identity-bearing canonical output order.

### 3.3 Visual Patterns

| Pattern | Compatible Logical Patterns | Closed parameters |
| --- | --- | --- |
| `flow-horizontal` | `causal-chain`, `sequence` | Optional `emphasisNode: node-ref`; optional `showRelationLabels: boolean`. |
| `flow-vertical` | `causal-chain`, `dependency-map`, `sequence` | Optional `emphasisNode: node-ref`; optional `showRelationLabels: boolean`. |
| `mapping-columns` | `mapping` only | Optional `showRelationLabels: boolean`; required `sourceColumnTitle: string`, `targetColumnTitle: string`. |

Each `visualPatterns` entry is exactly `{id, compatibleLogicalPatterns,
parameters}`. Each parameter is exactly `{name, type, required}`; `type` is
exactly `node-ref`, `boolean`, or `string`, `required` is boolean, and
canonical parameter objects sort by `name`. There are no defaults, aliases,
nullable values, or undeclared parameter forms. A `node-ref` is an exact
current-page node ID. Each string value is nonempty and trimmed. The table's
parameters are the complete exact entries for the initial catalog.

Visual Patterns are projection choices, not semantic authority. Compatibility
is selected from the catalog, never inferred from visible shape.

## 4. Identity and lossless serializations

`logicalIdentity` is canonical SHA-256 identity of page `id`, `knowledge`,
`language`, the resolved logical catalog identity, logical graph, source
declarations, and source bindings. It excludes visual choice, parameters, and
assets. Replacing one compatible Visual Pattern preserves `logicalIdentity`;
changing a Relation or provenance changes it.

`visualPageIdentity` is canonical SHA-256 identity of the complete normalized
VisualPage, including each declared asset digest, and the resolved full catalog
identity. Asset changes change `visualPageIdentity` and derived visual evidence.
A Visual Page Set identity includes its complete normalized set and preserves
page sequence.

Canonical JSON is the identity source: UTF-8, deterministic field order,
preserved array order, no insignificant whitespace, and exact normalized typed
values. It emits all and only defined fields.

Restricted Markdown, YAML, and JSON are lossless admitted serializations of
the same typed value. A parser rejects content it cannot preserve and reports
complete field, duplicate, schema, and type diagnostics with a field path or
source location and concise reason.

JSON has no duplicate or unknown keys and canonical output uses the root and
nested field order declared here. YAML has the same closed-key/type rules and
rejects anchors, aliases, merge keys, duplicate keys, and unknown keys.

Restricted Markdown has fixed order: root metadata; catalog; logical pattern,
nodes, and relations; visual pattern and parameters; assets; then sources. It
allows only canonical inline JSON for arrays and typed parameter values. It
does not admit arbitrary prose, additional headings, extension fields, aliases,
or alternate field spellings. Canonical Markdown emits that grammar
deterministically. Conversion parses and validates a typed value before writing
canonical JSON, YAML, or Markdown; equivalent admitted inputs produce the
same canonical JSON and identities.

### 4.1 Direct core command and restricted-Markdown grammar

The independent core command surface is deliberately non-generating except for
its explicit serialization target:

```text
cozy media visual-page validate <input> --catalog <catalog>
cozy media visual-page inspect <input> --catalog <catalog>
cozy media visual-page convert <input> --catalog <catalog> --save <output>
```

`input` and `catalog` are direct regular non-symlink UTF-8 files. `input` is
exactly one `.json`, `.yaml`, `.yml`, or `.md` document; the separate catalog
is exactly one `.json`, `.yaml`, or `.yml` document. `validate` emits only
schema, version, page count, and resolved identities. `inspect` adds the
declared ordered logical nodes, Relations, selected patterns, and typed
parameters. `convert` accepts only `.json`, `.yaml`, `.yml`, or `.md` output,
completes parsing and validation before creating its output, and replaces the
output through a same-directory atomic move. An invalid source, catalog, or
output suffix creates no output. These commands neither render nor select a
Presentation, binding, Storyboard, receipt, or review state.

Canonical Markdown is the following no-prose, line-oriented grammar. Each
`<json>` is one compact canonical JSON value: no insignificant whitespace,
canonical object field order, and preserved declared array order. The final
newline is required; CRLF and LF are equivalent input line endings.

```text
# Cozy Visual Page
schema: <json-string>
version: <json-integer>
id: <json-string>
knowledge: <json-string>
language: <json-string>
## Catalog
id: <json-string>
revision: <json-integer>
## Logical
pattern: <json-string>
nodes: <json-array>
relations: <json-array>
## Visual
pattern: <json-string>
parameters: <json-object>
## Assets
assets: <json-array>
## Sources
sources: <json-array>
```

The only admitted Visual Page Set Markdown form is the following ordered form;
`pages` is one compact canonical JSON array of complete canonical Visual Page
objects.

```text
# Cozy Visual Page Set
schema: <json-string>
version: <json-integer>
id: <json-string>
## Pages
pages: <json-array>
```

Canonical YAML is a root mapping with the required root fields in canonical
order. Each right-hand-side value is the same compact canonical JSON value
used by the Markdown grammar; YAML input may additionally use the equivalent
two-space-indented mapping and sequence form. YAML anchors, aliases, merge
keys, duplicate keys, comments carrying semantic fields, and scalar forms
outside the normalized model are rejected.

## 5. Binding, receipts, and projection

Template/business binding is a separate `cozy.visual-page.binding.v1`
document with integer `version: 1`, an identity, and selected catalog `id` and
`revision`. It maps semantic Visual Pattern slots to physical renderer/template
slots. Coordinates, fonts, colors, PowerPoint Shape kinds, and object IDs may
occur only in that binding or generated evidence, never in VisualPage or
catalog logical semantics.

The future presentation route selects a VisualPageSet explicitly in a
`cozy.media.v1` presentation resource using
`presentation.contract: visual-page-v1`. Its `source`, `catalog`, and `binding`
are each safe descriptor-relative direct regular non-symlink files under the
descriptor root. Each rejects an empty, absolute, traversal, URI-like, control-
character, or backslash path; normalization changes; symlink escapes; and a
missing or nonregular resolved file. The `source` must parse as exactly
`cozy.visual-page-set.v1` with integer version `1`. It invokes a renderer with
`--visual-page-set`, `--catalog`, and `--binding`, and expects
`cozy.presentation.render.v2`. Its renderer and review receipts bind Visual
Page Set/page identities, catalog, binding, template, renderer, and asset
hashes. Changed page/catalog/binding/template/renderer/asset inputs make
dependent visual output and evidence stale.

This future route leaves the existing slide-IR source, `--slide-ir`, and
`cozy.presentation.render.v1` contract untouched.

## 6. Future Storyboard v2 screen

The future route is separately versioned as `cozy.video.storyboard.v2` with
integer `version: 2`. All v1 Scene fields remain semantic in v2. Its `screen`
is discriminated exactly as either:

```json
{ "kind": "text", "heading": "...", "content": "..." }
```

or:

```json
{ "kind": "visual-page", "source": "safe/page-set-path", "pageId": "page-id" }
```

The latter requires its `source` to satisfy the same safe VisualPageSet source
rules as the future presentation route and its `pageId` to resolve exactly once
in that referenced set. Duplicate page IDs and zero or multiple matches reject
fail closed.
A v2 Storyboard identity contains the screen reference, not a substituted or
guessed page identity. Scene-screen visual evidence and receipts bind resolved
page, catalog, binding, renderer, and asset identities. A page change stales
visual evidence; it does not rewrite narration, speaker, duration, silence,
transition, confirmation/final separation, or audiovisual acceptance, and it
does not make audiovisual acceptance implicit.

v1 remains exactly as accepted. Only explicit
`cozy video storyboard migrate --from v1 --to v2 --screen text` produces a v2
text-screen equivalent. A `visual-page` v2 screen cannot downgrade to v1 and
fails `VISUAL_PAGE_SCREEN_LOSSY`. No parser auto-upgrades or reinterprets a v1
screen.

## 7. Explicit Slide IR migration

Legacy Slide IR conversion is explicit only:

```text
cozy media presentation migrate <legacy-slide-ir> --semantic-map <semantic-map> --catalog <catalog> --save <visual-page-set>
```

Each required option occurs exactly once and accepts either separated or
`--option=value` form. `legacy-slide-ir` remains the existing direct legacy
Slide IR input parser; its raw direct-file bytes are never normalized before
digest calculation. `semantic-map` is exactly one direct regular non-symlink
UTF-8 `.json` file. `catalog` is the separately supplied resolved Visual Page
catalog. `save` must end in `.json`.

The semantic map is a closed JSON object with exactly the following named
fields and no duplicate or unknown fields. JSON member order carries no
semantic meaning; only the deterministic report and saved VisualPageSet are
canonical ordered outputs:

```text
schema, version, legacySlideIrSha256, visualPageSet, bindings
```

`schema` is exactly `cozy.visual-page.migration-map.v1`; `version` is integer
`1`; and `legacySlideIrSha256` is lowercase 64-hex SHA-256 of the raw direct
legacy Slide IR file bytes. `visualPageSet` is a complete embedded
`cozy.visual-page-set.v1` object, never a path or one Page. It is parsed and
validated through the Visual Page contract against the separately supplied
catalog with the semantic-map directory as its safe source/asset root.

`bindings` is an array. Every entry is exactly
`{slideId, elementIndex, target}`, with a zero-based nonnegative
`elementIndex`; `target` is exactly `{pageId, kind, id}`. `kind` is exactly
one of `node-label`, `asset-id`, or `source-path`. Flattening the legacy source
uses each `(slideId, elementIndex)` address. Bindings cover that source set
exactly once, and target triples `(pageId, kind, id)` are also unique. A
`pageId` resolves exactly once in the embedded set. Its target `id` resolves
exactly once in the selected category: a logical node label, an asset ID, or a
declared source path.

A text source element binds only to a `node-label` or `source-path` whose
resolved typed value is the exact source text. An asset source element binds
only to an `asset-id` whose resolved ID is the exact source asset. Unknown,
duplicate, missing, out-of-range, unresolved, ambiguous, wrong-kind,
wrong-value, malformed UTF-8, non-JSON, symlink, schema, version, digest,
Page-vs-PageSet, inferred, or lossy mapping fails with a structured migration
diagnostic. Such failure creates no output and preserves any pre-existing
output bytes.

Only after all source, map, catalog, embedded Visual Page Set, and binding
checks succeed may Cozy replace `save` by a same-directory atomic move. The
file contains canonical VisualPageSet JSON followed by one newline. The
deterministic text report is exactly the ordered fields:

```text
schema: cozy.visual-page.migration-report.v1
version: 1
legacySlideIrSha256: <raw-legacy-digest>
sourceElementCount: <count>
bindingCount: <count>
catalogIdentity: <resolved-catalog-identity>
visualPageSetIdentity: <resolved-set-identity>
status: migrated
```

The map supplies complete human-selected semantics; it does not infer a
Logical Pattern or reinterpret old Slide IR. The command invokes no renderer
or downstream consumer.

## 8. Ownership and non-goals

Generated PPTX, slide images, montage, video frames, manifests, and caches are
delivery/review artifacts, not semantic authorities. Cozy owns validation,
identity, orchestration, and evidence. External renderer, SmartDox, and Textus
consumers own their own runtime and acceptance.

Phase 37 is deferred: argument/subject patterns, explanation development, AI
selection, and multi-page/multi-scene expansion are outside this contract.
