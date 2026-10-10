# Visual Page Specification

Status: NORMATIVE; Phase 36 `VIS36-01` through `VIS36-06` are DONE. P36-06's
closed Cross-media Review route and representative evidence are recorded in
the Phase ledger, with duplicate JSON object fields rejected before decoding
Cross-media proof or Storyboard v2 evidence/handoff. Phase 37 is NOT STARTED.
No external-consumer acceptance is claimed.

This is the normative contract for the Visual Page route. It defines semantic
values, compatibility boundaries, the independent direct core
parse/validation/canonicalization CLI, the renderer-independent direct semantic
Preview, the implemented separately discriminated P36-03C `visual-page-v1`
presentation route, the explicit legacy Slide IR migration map, and the P36-04
Storyboard v2 reference contract. It claims no video review acceptance or
external-consumer acceptance.

The words **MUST**, **MUST NOT**, **SHOULD**, and **MAY** are normative. The
ownership design is [`docs/design/visual-page.md`](../design/visual-page.md).

## 1. Scope and v1 coexistence

A `VisualPage` is one typed, one-screen semantic authority. It is not a
PowerPoint slide, video frame, template, renderer object graph, or generated
artifact. A `VisualPageSet` is the ordered unit consumed by the implemented
P36-03C `visual-page-v1` presentation route; a future video screen identifies
one page in such a set.

`cozy.slide-ir.v1`, `cozy.presentation.render.v1`,
`cozy.video.storyboard.v1`, `cozy.media.v1`, `cozy.media.receipt.v2`, and
`cozy.media.review-state.v1` retain their accepted behavior unchanged. No
parser or route may silently upgrade, reinterpret, or select this contract for
a v1 source. The distinct P36-03C implementation accepts the new v2 route
only through its explicit discriminator.

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
| `assets` | Ordered declarations requiring `{id, path, mediaType}`; ordinary extra asset fields are ignored. |
| `sources` | Ordered declarations of exactly `{id, path}`. |

`catalog` resolves a separately supplied versioned catalog document with
schema exactly `cozy.presentation-semantics.catalog.v1`, schema version
exactly `1`, matching catalog `id`, and positive catalog `revision`. Its
canonical root field order is exactly `schema, version, id, revision,
relations, logicalPatterns, visualPatterns`. Resolution validates the supplied
current catalog by ID/revision and its actual definitions. No resolved full or
logical catalog digest is calculated, retained, or compared. Missing catalogs,
invalid definitions, and unresolved references still reject.

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

Every asset declaration resolves from the actual Visual Page input's parent
to a direct regular file with neither symlinked ancestors nor a symlinked leaf.
Required IDs, paths, and media types remain validated. Current valid asset-byte
changes require no declaration digest or rebinding; material readers ignore
ordinary extras, including malformed obsolete metadata, without parsing,
validating, retaining, or re-emitting them. Missing or ill-typed required
fields and duplicate object keys still reject.

No Visual Page semantic field may contain coordinates, fonts, colors,
PowerPoint shapes, transitions, or renderer IDs.

`visual.pattern` selects a Visual Pattern. `visual.parameters` is the closed
typed object declared by that exact catalog entry. A parameter's JSON type or
reference type, optionality, and no-default behavior are catalog semantics.
There are no inferred defaults, unknown values, aliases, or renderer-specific
values. A missing required parameter, unknown parameter, ill-typed value,
alias, or implicit default fails closed.

A Visual Page Set root contains exactly `schema`, `version`, `id`, and `pages`.
It has schema exactly `cozy.visual-page-set.v1`, integer `version: 1`, a stable
`id`, and ordered nonempty `pages` containing full VisualPage values. Page
sequence is semantic, and every contained page `id` is unique. Every contained
page MUST declare the same catalog `{id, revision}` pair. Set resolution accepts
exactly one supplied resolved catalog matching that pair; a mixed pair,
unresolved catalog, or ID/revision mismatch fails closed.
Presentation therefore binds every page deterministically through its one
catalog/binding pair. A future v2 video screen identifies one page in a safe
referenced set by source path and page ID.

## 3. Closed core catalog

The core catalog is closed. New Relations, patterns, roles, cardinalities, or
parameter forms require a new catalog revision. A selected revision MUST NOT
be extended by convention, inference, or renderer behavior.

Revision 1 MUST remain accepted with its original exact catalog shape. Revision
2 MUST retain every Revision 1 array entry in the original order and append
only `standalone` and `standalone-card`: `standalone` has exactly one `item`
node role with `min: 1`, `max: 1`, and an empty `relationRules` array;
`standalone-card` is compatible only with `standalone` and has an empty
`parameters` array. Revision 2 MUST reject a relation, extra item, unknown
parameter, hybrid catalog shape, alias, or inferred pattern. No revision other
than 1 or 2 is admitted by this contract.

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

For Revision 2, `standalone-card` is the fourth complete binding target. A
Revision 2 binding MUST include it exactly once with the existing ordered five
semantic slots `knowledge`, `nodes`, `relations`, `assets`, and `parameters`.
Revision 1 bindings remain complete with their original three pattern entries.

## 4. Typed values and lossless serializations

DM14 removes VisualPage-owned catalog, logical-catalog, logical-page,
visual-page, and PageSet digests. `ValidatedDocument` contains exactly
`document`, `catalog`, and `canonicalJson`. No complete JSON copy, canonical
string, fingerprint, or substitute identity proves that historical evidence
matches the current input. A valid compatible projection or content update is
processed as current input rather than rejected by a historical proof.

Canonical JSON preserves deterministic field order, array order, and exact
normalized typed values. It emits all and only defined fields and supports
lossless conversion; it is not an operational historical-current proof gate.

Restricted Markdown, YAML, and JSON are lossless admitted serializations of
the same typed value. A parser rejects content it cannot preserve and reports
complete field, duplicate, schema, and type diagnostics with a field path or
source location and concise reason.

JSON rejects duplicate keys and unknown keys outside asset declarations;
canonical output uses the root and nested field order declared here. YAML has
the same required-field and closed-object rules and rejects anchors, aliases,
merge keys, and duplicate keys. Only ordinary extra asset fields are ignored.

Restricted Markdown has fixed order: root metadata; catalog; logical pattern,
nodes, and relations; visual pattern and parameters; assets; then sources. It
allows only canonical inline JSON for arrays and typed parameter values. It
does not admit arbitrary prose, additional headings, extension fields, aliases,
or alternate field spellings. Ordinary extra asset fields may normalize only
inside `assets:` or `pages:` inline JSON. The original inline JSON must equal
its compact canonical AST before filtering; filtering preserves required-key
order, and the complete normalized text must equal canonical Markdown. Thus
prose, envelope order, line count, required-key order, and trailing LF remain
strict. Canonical Markdown emits that grammar deterministically. Conversion parses and validates a typed value before writing
canonical JSON, YAML, or Markdown; equivalent admitted inputs produce the
same canonical typed value and JSON.

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
completes parsing and validation before writing its canonical UTF-8 serialization
directly to the normalized selected destination. The output parent must be an
existing direct directory and the destination must not be a symbolic link.
An invalid source, catalog, or output suffix leaves selected output untouched
because no write has begun. Ordinary I/O failures are reported as
`VISUAL_PAGE_SAVE`; writing may leave partial output and regeneration is recovery.
These commands neither render nor select a
Presentation, binding, Storyboard, receipt, or review state.

### 4.2 Direct semantic Preview

The exact direct semantic Preview command is:

```text
cozy media visual-page preview <input> --catalog <catalog> --save <output.html> [--png <output.png>]
```

`input` and `catalog` use the same direct regular non-symlink UTF-8 and
suffix rules as the direct core command. Preview MUST invoke the strict
`VisualPage.load` validation path before it writes either output. It MUST NOT
infer, repair, normalize beyond the validated semantic value, or alter a
Logical Pattern, Relation, selected Visual Pattern, parameter, source, asset,
or identity.

Preview's semantic input contract is only that validated document and its
resolved catalog. It MUST NOT parse or consume a binding, template, renderer,
PPTX, PowerPoint coordinate/font/color/shape/object-ID, receipt, review state,
video, or generated media artifact. The required `.html` output is a
deterministic UTF-8 semantic review artifact, not a physical-layout or
renderer-binding artifact. It MUST display the ordered page IDs,
Logical/Visual Pattern IDs, ordered logical nodes, Relations with their type
and catalog direction, typed visual parameters, document schema and ID, and
catalog ID and revision. Every page-supplied text value MUST be HTML-escaped.

`--png` MAY occur once. When selected, it writes one deterministic `.png`
logical-structure image generated only from the same validated input. Any
physical arrangement in that image is generated Preview output only; it MUST
NOT become a PPT input, reusable renderer binding, or semantic authority.

`--save` MUST occur once and target exactly a `.html` file. `--png` MUST occur
zero or once and target exactly a `.png` file. Missing, duplicate,
unsupported, valueless, extra positional, or otherwise ambiguous arguments
MUST reject. Normalized-identical HTML and PNG targets MUST reject. Preview
MUST initially admit all arguments and targets, load the input and catalog, and
render every selected output byte before writing directly to the selected HTML
target followed by the optional PNG target. Output parents must be existing
direct directories and destinations must be absent or direct regular non-symlink
files. An invalid input, catalog, or argument MUST leave each pre-existing
selected output byte sequence unchanged because no write has begun.
Ordinary I/O failures are reported as `VISUAL_PAGE_PREVIEW_SAVE`; a failed write
may leave partial output or an earlier completed output, and regeneration is
recovery. The success report is emitted only after all selected writes succeed.
Preview invokes no
external process or renderer.

The Preview report is deterministic and has these exact ordered fields; the
`png` line occurs only when `--png` was selected:

```text
schema: cozy.visual-page.preview.v1
version: 1
generator: cozy media visual-page preview
documentId: <validated-document-id>
catalogId: <resolved-catalog-id>
catalogRevision: <resolved-catalog-revision>
pageCount: <validated-page-count>
html: generated
png: generated
status: generated
```

Phase 71.1 supersedes Preview hash-output provenance. Preview MUST NOT compute
or emit artifact digests or document/catalog digest metadata in its report or
HTML. It preserves semantic identifiers, ordering, strict input validity, and
PNG format integrity through CRC32. Equivalent current inputs produce identical
selected output bytes and reports; a valid label change reaches the selected
output without identity rebinding. Optional PNG presence follows command
selection only.

Direct Preview provenance and DM12 material-field removal alone do not complete
the subsequent core/catalog/binding and Presentation/receipt migrations. Their
current contracts are specified below, including the migrated SummarySlidesPdf
renderer subsystem. Publisher/rsync and the final named-integrity audit remain pending. This section does not claim
full inventory removal, timestamp currentness/adoption, Step or Phase completion,
or downstream consumer acceptance.

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

## 5. Versioned business binding

The first business binding is a strict JSON-only direct-file value with schema
`cozy.visual-page.binding.v1`. Its canonical root field order is exactly:

```text
schema, version, id, profile, catalog, patterns
```

`schema` is exactly `cozy.visual-page.binding.v1`; `version` is integer `1`;
and `id` and `profile` are nonempty canonical identity tokens matching the
Visual Page token convention. `profile` is exactly `business`. `catalog` is
exactly `{id,revision}` and must exactly equal the resolved Visual Page
catalog pair in the supplied `CozyVisualPage.ValidatedDocument`.

`patterns` is a nonempty array of entries exactly `{visualPattern,slots}`.
`visualPattern` must be a Visual Pattern ID in the selected resolved catalog.
Entries are unique; canonical output sorts them lexicographically by
`visualPattern`, so incidental input entry order does not affect the normalized value. The
binding is complete: every Visual Pattern in the selected catalog is
represented exactly once, and the supplied validated Visual Page document may
use only those bound pattern IDs.

`slots` is an ordered array of exactly five entries, each exactly
`{semanticSlot,physicalSlot}`. Its semantic slot set and order are exactly
`knowledge`, `nodes`, `relations`, `assets`, `parameters`. Each
`physicalSlot` is a nonempty canonical identity token and is unique within its
Visual Pattern entry. A physical slot is opaque: it identifies only a
template/renderer slot and carries no coordinate, font, color, PowerPoint
Shape kind, renderer object ID, template path, or executable command.

The direct loader reads only an existing regular UTF-8 `.json` file with
neither a final symlink nor a symlinked ancestor below its allowed root. It
rejects unsupported suffixes, malformed UTF-8 or JSON, duplicate or
unknown fields, unsafe/nonregular/symlink input, wrong schema/version/type or
token, a profile other than `business`, a catalog mismatch, unknown or
incomplete patterns, and duplicate/missing/unknown semantic or physical slots.
Every rejection is a structured `VISUAL_PAGE_BINDING_*` diagnostic carrying a
field path and concise reason. It does not modify or reinterpret the supplied
Visual Page and does not invoke a renderer.

The validated binding exposes its typed `binding` and normalized `canonicalJson`.
`Binding.canonicalValue` is the complete JSON object obtained by parsing that
canonical JSON, with exactly `schema,version,id,profile,catalog,patterns`.
Conversion failure uses the existing `VISUAL_PAGE_BINDING_JSON` diagnostic.
Equivalent admitted key and pattern-entry orderings converge to the same typed
Binding and canonical JSON value. No Binding digest is calculated or exposed.
Ordinary IDs, complete catalog patterns, and ordered physical-slot mappings remain.

## 6. Binding, receipts, and projection

Template/business binding is the only authored place in this route where a
mapping to physical renderer/template slots may be recorded. Coordinates,
fonts, colors, PowerPoint Shape kinds, and object IDs may occur only in a
later binding or generated evidence, never in Visual Page or catalog logical
semantics.

The implemented presentation route selects a VisualPageSet explicitly in a
`cozy.media.v1` presentation resource using
`presentation.contract: visual-page-v1`. Its `source`, `catalog`, and `binding`
are each safe descriptor-relative direct regular files under the descriptor
root, with neither a final symlink nor a symlinked ancestor. Each rejects an
empty, absolute, traversal, URI-like, control-character, or backslash path;
normalization changes; symlink escapes; and a missing or nonregular resolved
file. The `source` must parse as exactly
`cozy.visual-page-set.v1` with integer version `1`. It invokes a renderer with
`--visual-page-set`, `--catalog`, and `--binding`, and expects
 `cozy.presentation.render.v2`. Actual current PageSet, catalog, and Binding
loading remains required for rendering; historical copies do not.

The v2 renderer's required relative field order is
`schema,target,profile,renderer,pptx,slides,montage`. Renderer requires
`name,version`; PPTX and montage require `path`; page-ordered slides require
relative order `id,path,assets` with ordered unique `{id}` assets. Ordinary
generated-evidence readers positively select those known fields and ignore
arbitrary extra metadata at every object level. No template/output/slide/montage
hash, copied semantic value or replacement content proof is required or emitted.
Actual paths, PPTX relationship/media structure, PDF/PNG formats and required
outputs remain validated. Both Presentation routes retain safe direct selected
asset path admission and existence of every actually linked ZIP image entry;
they perform no copied-image byte, multiset or cardinality comparison. The direct
Summary PDF route also admits selected assets without copied-byte agreement.

Presentation review's `visualPageSet`, `catalog`, `binding`, `template` and
`pptx` wrappers are `{path}`; slides are `{id,path}`, assets are
`{id,path,mediaType}`, montage is `{id,role,path}`, article PDF and infographic
are `{id}`. Root schema/target/knowledge/language, renderer `name,version`, and
verification `status:valid,findings:[]` remain required. Recursive positive
known-field projection verifies those current required semantics and array
order while ignoring arbitrary nested extras. Missing or wrong paths, page
IDs, assets, media types or status reject. No PageSet/catalog/logical/page/Binding snapshot is
copied into VisualPage evidence. Renderer argv is unchanged, and external
renderer implementation or acceptance is not claimed. Presentation receipt
artifacts contain only `id,role,path`; review-state snapshots contain only UTC
ISO-8601 `acceptedAt`, with generic extras ignored and the root state grammar
closed. Ordinary dependency/modification-time and generation-companion checks
remain. Unchanged reuse preserves explicit alignment; a newer review generation
requires realignment even with identical bytes. Structurally valid edits at
preserved timestamps have no historical-content-detection guarantee. Actual
PNG/PPTX/OOXML structure, relationships, titles and required page mappings still
reject invalid same-time edits. Renderer files remain direct outputs. Shared
acceptance admits normalized selected targets, symlink/regular-file restrictions
and distinct non-aliased ownership before writing prepared review states directly
in order, followed by the final receipt. Input/format/preparation failures before
writes leave acceptance records untouched; first-state-parent failure precedes
receipt writing. I/O failure during writes may leave earlier or partial documents,
including a started receipt write, and reports no success. There is no atomic
installation, rollback or prior-record preservation promise. CrossReview's
separate failure preservation remains pending removal. The SummarySlidesPdf
renderer subsystem is migrated under S07; publication/site follow-ups remain
pending without a Phase closure claim.

The direct SummarySlidesPdf v1/v2 readers require relative root order
`schema,target,profile,renderer,pdf,slides,montage[,pptx]` and positively select
known fields at every object, ignoring arbitrary extra metadata. Renderer
`name,version` are exact profile identities; `pdf.pageCount` is a positive
integer agreeing with both current authority and actual PDFBox pages, with no
required PDF path. Ordered unique slide/page IDs and asset IDs remain required;
v1 additionally requires the matching title. Both routes require slide paths
and montage `path`; configured PPTX requires `path`, while an unconfigured
`pptx` field rejects. Missing/null/wrong known fields and unsafe paths reject.
The authored configuration grammar is unchanged.

The renderer receives the declared primary output in `--pdf` and writes it
directly. One pre-render admission requires selected actual inputs and dependency
files to be direct regular files. Visual Page declared asset paths and selected
resource paths remain safely admitted without copied-byte agreement. The actual
direct PDF must have its PDF header and parse in PDFBox. Slide and montage files
retain their PNG-signature and safe direct-file floor. The optional PPTX retains
only its configured direct-file sidecar floor, with no new OOXML or
render-equivalence promise. Ordinary dependency/modification-time reuse remains;
no operation-local or receipt input observations are compared across rendering.
Actual invalid PDF/page-count/required structure still rejects.

The primary PDF and renderer sidecars are direct outputs. Renderer, structure
or I/O failure creates no fresh acceptance or successful report and may leave
partial or changed generated outputs, with recovery by regeneration. There is
no atomic install, staged cleanup, rollback or prior-primary preservation
promise. S10 replaces shared receipt installation with ordered direct writes and
removes Presentation embedded-byte comparison. S11 migrates the selected
PresentationMigration, CrossReview and article scaffold direct writers.
Publication/prepared-input observations and other finite removals remain explicitly unfinished
Phase 71.5 work. This subsystem migration does not claim Phase inventory,
Step/Phase closure, EnglishPDF or isolated-site proof. ProducerSource/BoK
metadata, generated rsync and the final named-integrity audit remain unfinished.

On 2026-10-08 S09's six existing focused suites completed with 114 successes,
zero failed/canceled/ignored/pending cases, zero aborted suites, zero
warnings/errors, SBT/wrapper exit 0, and terminal shared serial-lock release.
S10's bounded Presentation/shared-acceptance removal is implemented. On
2026-10-08 its six existing focused suites completed with 113 successes,
zero failed/canceled/ignored/pending cases, zero aborted suites, zero SBT
warnings/errors, SBT/wrapper exit 0, and terminal shared serial-lock release.
Five hostile-XML/DOCTYPE diagnostics are intentional existing negative-fixture
output, not SBT compilation or test failures. S11 implements direct
PresentationMigration and CrossReview output writes and direct creation of an
absent article source package. On 2026-10-08 S11's five existing focused suites
completed with 58 successes, zero failed/canceled/ignored/pending cases, zero
aborted suites, zero SBT warnings/errors, SBT/wrapper exit 0, and terminal shared
serial-lock release. S11 emitted five intentional hostile-XML/DOCTYPE
negative-fixture diagnostics, separate from SBT compilation or test failures.
S12's current JA native route is planned/not-run; generation, assembly, native
inspection, browser EOF and current listening remain OPEN
([journal](../journal/2026/10/2026-10-08-phase-71.5-ja-main-route.md)).
Independent Step/Phase acceptance, the same full 34-scene native route, external
E715-01..04 reconciliation, Receipt publication/prepared-input observations and
remaining finite publication/runtime/CAR groups remain pending without a
whole-Phase closure claim.

CrossReview retains resolved current page/scene references and equal ordered asset
IDs/media types, with safe actual files resolved independently for each side and
no cross-root asset-byte equality. It writes canonical aggregate JSON plus newline
directly after existing semantic/path/input-and-evidence ownership checks.
Pre-write failures preserve prior aggregate bytes; ordinary I/O failures may leave
partial output and report no success, without atomic installation or cleanup.
Verification remains read-only and format/reference-based, with approvals unrecorded.
Article scaffolding retains exact slug/business/language grammar and an existing
parent, directly creates an absent destination and writes unchanged payloads,
review state and subdirectories in order. It never merges or overwrites an existing
package; a later ordinary creation/write error may leave a partial new package and
reports no success, without staging or failure cleanup.

The existing slide-IR source and `--slide-ir` route remain separate. DM12 also
requires only ordered unique asset IDs from `cozy.presentation.render.v1`,
ignoring ordinary extras; all other v1 renderer and OOXML validity gates remain.

## 7. Storyboard v2 screen

The separately versioned `cozy.video.storyboard.v2` route has integer
`version: 2` and is JSON only. All v1 Scene fields remain semantic in v2. Its
`screen` is discriminated exactly as either:

```json
{ "kind": "text", "heading": "...", "content": "..." }
```

or:

```json
{ "kind": "visual-page", "source": "safe/page-set-path", "catalog": "safe/catalog-path", "pageId": "page-id" }
```

The latter requires both `source` and `catalog` to be nonempty normalized
descriptor-relative forward-slash paths resolved from the Storyboard source
directory. They reject absolute, traversal, URI/query/fragment/control,
backslash, non-normalized, final-symlink, ancestor-symlink, and nonregular
values. Cozy loads the `source` as a VisualPageSet using the exact supplied
catalog and requires `pageId` to resolve exactly once. Duplicate page IDs and
zero or multiple matches reject fail closed. Catalog, binding, renderer, and
resolved identity inference are prohibited.
A v2 Storyboard identity contains the screen reference. Generated v2
`visualPages` entries are exactly `sceneId,kind,source,catalog,pageId,assets`;
assets retain `{id,path,mediaType,modifiedAt}` observations. DM14 deletes the
four VisualPage digests and copied Binding proof. Ordinary evidence readers
select only known root fields, scenes/screens, visual inputs, effective
renderers/configurations and visual pages/assets, ignoring every unspecified
property without a named legacy mask. Required fields/types, renderer decoding,
existing relative page/asset key order, unique IDs and direct-file safety remain.
These generated objects do not widen authored Storyboard or configuration
grammar, and do not compare old VisualPage/assets/Binding snapshots with
current values. Actual current referenced PageSet/catalog/page/Binding and safe
files are validated separately. Narration, timing, and audiovisual authority
are unchanged. Storyboard/approval/evidence self-identities, optional visual-input
hashes and effective-renderer identities are removed under the Storyboard review
contract. Independent confirmation/runtime/publication ownership is unchanged.

v1 remains exactly as accepted. Only explicit
`cozy video storyboard migrate --from v1 --to v2 --screen text <input> --save
<output.json>` produces a canonical v2 JSON text-screen equivalent. A
`visual-page` v2 screen cannot downgrade to v1 and fails
`VISUAL_PAGE_SCREEN_LOSSY`. No parser auto-upgrades or reinterprets a v1
screen. P36-04 adds parsing, planning metadata, and this one-way migration
only; it does not implement video receipt/review evidence or renderer output.

## 8. Explicit Slide IR migration

Legacy Slide IR conversion is explicit only:

```text
cozy media presentation migrate <legacy-slide-ir> --semantic-map <semantic-map> --catalog <catalog> --save <visual-page-set>
```

Each required option occurs exactly once and accepts either separated or
`--option=value` form. `legacy-slide-ir` remains the existing direct legacy
Slide IR input parser. `semantic-map` is exactly one direct regular non-symlink
UTF-8 `.json` file. `catalog` is the separately supplied resolved Visual Page
catalog. `save` must end in `.json`.

The semantic map is a closed JSON object with exactly the following named
fields and no duplicate or unknown fields. JSON member order carries no
semantic meaning; only the deterministic report and saved VisualPageSet are
canonical ordered outputs:

```text
schema, version, visualPageSet, bindings
```

`schema` is exactly `cozy.visual-page.migration-map.v1`; `version` is integer
`1`. `visualPageSet` is a complete embedded
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
wrong-value, malformed UTF-8, non-JSON, symlink, schema, version,
Page-vs-PageSet, inferred, or lossy mapping fails with a structured migration
diagnostic. Such failure creates no output and preserves any pre-existing
output bytes.

Only after all source, map, catalog, embedded Visual Page Set, and binding
checks and existing selected-path admission succeed may Cozy directly write
`save`. The file contains canonical VisualPageSet JSON followed by one newline.
Ordinary write failures retain `MIGRATION_SAVE` diagnostics and emit no successful
report; they may leave partial output. No atomic replacement, preservation or
cleanup guarantee applies to writing failures; recovery is regeneration. The
deterministic text report is exactly the ordered fields:

```text
schema: cozy.visual-page.migration-report.v1
version: 1
sourceElementCount: <count>
bindingCount: <count>
status: migrated
```

The map supplies complete human-selected semantics; it does not infer a
Logical Pattern or reinterpret old Slide IR. The command invokes no renderer
or downstream consumer.

## 9. Ownership and non-goals

Generated PPTX, slide images, montage, video frames, manifests, and caches are
delivery/review artifacts, not semantic authorities. Cozy owns validation,
identity, orchestration, and evidence. External renderer, SmartDox, and Textus
consumers own their own runtime and acceptance.

Phase 37 is deferred: argument/subject patterns, explanation development, AI
selection, and multi-page/multi-scene expansion are outside this contract.

## 2026-10-09 S33 presentation production and explicit review facts

**P715-02-S33-PRESENTATION-EXPLICIT-REVIEW-FACTS** is
**implemented-unvalidated-unreviewed** at this initial 2026-10-09 editing
handoff, within original **R715-03/04/14/15**. The four parent-selected existing
suites `cozy.media.CozyMediaPresentationSpec`,
`cozy.media.CozyMediaPdfReviewStateSpec`, `cozy.media.CozyMediaCrossReviewSpec`,
and `cozy.media.CozyMediaSpec` are **pending**. Validation, parent admission and
independent review are not claimed.

The [actual S32 focused result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s32-focused-validation-result.json)
records **48 successes across four completed suites**, separately dated
**2026-10-09** and attributed to S32. It does not validate or accept S33.
Other original finite obligations, including R715-03/04/14/15 remainders and
concrete external **E715-01..05**, remain **OPEN**. Final current **JA34 native
generation, assembly, audible hearing, end-card and EOF**, independent
**M2 cycles 6/7/8/10**, whole-Step/full-Phase reviews and acceptance, exact local
Step commits and the distinct release remain **OPEN**. Repository-full SBT and
broader integration remain **deferred-not-run to PHASE-71.7**. Existing base,
authority, epoch, repair counters, stage/aggregate metadata and prior history
retain their state; this authoring handoff makes no workflow transition.

This normative amendment supersedes the earlier presentation review-state
promises that use the review manifest's modification time, generation/decision
date ordering, or a review-manifest date no newer than `current` to establish
approval or currentness. It applies to both the legacy slide-IR and
`visual-page-v1` presentation routes.

An ordinary media build that selects the existing `Action.Current` presentation
reuses its generated output and retains the recorded semantic review state
when `current` exists, including the existing explicit decision. An actual
successful render, including the explicit slide-build route, records the new
acceptance observation time as UTC ISO-8601 `acceptedAt`, preserves
`last_aligned` and `selected_authority`, and sets `sync` to `stale`. A first
acceptance without a current record also requires explicit alignment. Failed
or rejected rendering never records acceptance. The execution branch supplies
this production fact; no file equality or timestamp comparison establishes it.

Explicit alignment first requires the ordinary current presentation validity
checks and an existing `current` record. It records the actual decision time,
selects `article` or `slide-ir`, and sets `sync` to `aligned`. An aligned state
requires `current`, `last_aligned`, and `selected_authority` plus `sync: aligned`.
Date fields record acceptance and decision facts; they do not prove current
approval, unchanged content, or race-free execution. No temporal ordering is
required to load those facts. The exact root grammar and required UTC string
type/syntax remain, with arbitrary snapshot extras ignored generically.

The ordinary presentation verifier still checks actual renderer/review
schemas, semantic IDs and mappings, selected paths, PPTX/OOXML structure,
relationships, legacy titles, and PNG format. Ordinary dependency existence
and modification times continue to select rebuild/reuse; they are not approval
or race evidence. Direct writes, acceptance order and ordinary input, output,
reuse and I/O failures retain their existing boundaries. The obsolete
review-state refresh seam and timestamp approval comparisons are removed.

The existing presentation scenarios specify actual reuse, explicit rendering,
and explicit re-alignment on both routes, plus retained XML/relationship/title/
PNG/page-mapping failures and arbitrary annotations. The existing PDF review
fixture emits the canonical summary-renderer schema, target/profile/renderer,
PDF page count, slide IDs/titles/paths and asset IDs, and montage path; retired
digest transport and protection-field absence expectations are removed.

See [the frozen S33 Implementation Manifest](../../.codex-workflow/phases/PHASE-71.5/p715-02-s33-implementation-manifest.json) and [the dated implementation handoff](../journal/2026/10/2026-10-09-phase-71.5-presentation-explicit-review-facts.md). At the initial handoff, selected validation and independent review remained pending.

### Actual S33 focused validation failure — 2026-10-09

**P715-02-S33-PRESENTATION-EXPLICIT-REVIEW-FACTS** is currently
**implemented-focused-validation-failed-unreviewed**. The initial
**implemented-unvalidated-unreviewed** handoff and its pending-suite record
remain dated history. The [actual failure result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s33-focused-validation-failure01.json)
and [raw execution log](/private/tmp/skill.cncf.d/a-ede4e1d9-7928-4d8e-9170-635b2dbaa193/73204-20261009T043126Z.log)
record the first group, `cozy.media.CozyMediaPresentationSpec` plus
`cozy.media.CozyMediaPdfReviewStateSpec`: **31 tests, 30 succeeded, 1 failed,
2 suites completed**, with **0 aborted, canceled, ignored or pending**.
The second group, `cozy.media.CozyMediaCrossReviewSpec` plus
`cozy.media.CozyMediaSpec`, was **NOT RUN** after the first group's failure.
There is no four-suite success or acceptance result.

Implicit compilation completed for **3 Main and 2 Test Scala sources**, with
**zero compiler warnings**. The **three existing duplicate `logback.xml`
runtime WARN lines** remain under **HYG-P715-S19-LOGBACK-001**, unsuppressed and
pending independent classification. Separately, the hostile-XML negative
fixture emitted **DOCTYPE disallowed Fatal Error on stderr**; that diagnostic
is retained unsuppressed, with independent classification still open. Zero
compiler warnings does not establish clean runtime diagnostics.

The failed existing scenario is **preserve explicit alignment on ordinary
reuse and require realignment after explicit rendering**. Reuse and the stale
review-state facts were reached successfully. After `_slide_build`, ordinary
media verification reports missing accepted receipt evidence for
`article-pdf` and `infographic`; the expected `stale` substring assertion fails
at **CozyMediaPresentationSpec.scala:576**. Repair design and validation remain
pending. This factual record changes no product behavior or executable
expectation. The [parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s33-parent-edit-admission.json)
is parent inspection, not independent review or acceptance.

The [quote-stop correction completion](../../target/phase715-s33-quote-skill-repair-completion.json)
records the minimal shared correction to **two canonical instruction files**
and **one deployed TOML**, **55 focused tests passed** and **independent PASS**,
with deployed contract **revision 20**. The existing stopped child retained
loaded **revision 19** and used **one same-child, one-use correction** for the
first original submission; no new request, attempt or permission and no retry
were created. Actual **SBT exit 1, wrapper exit 1 and serial lock released**
establish a terminal validation failure. Fresh registered revision-20
operational acceptance remains pending; shared correction evidence does not
accept the Cozy product.

Prior **S32 48 successes / 4 completed suites** remain separately attributed.
Original **R715-03/04/14/15** finite remainders, concrete external
**E715-01..05**, final current **JA34 native generation, assembly, audible
hearing, end-card and EOF**, independent **M2 cycles 6/7/8/10**, whole-Step and
full-Phase review, exact local Step commits and the distinct release remain
**OPEN**. Repository-full SBT and broader integration remain
**deferred-not-run to PHASE-71.7**. Original authority/base/goal binding/epoch,
repair history, stage and aggregate metadata and closure checkboxes retain
their state. This addition records facts without a workflow transition.

### Actual S33 repair11 focused validation result — 2026-10-09

**P715-02-S33-PRESENTATION-EXPLICIT-REVIEW-FACTS** is currently
**implemented-focused-validated-unreviewed**. The [current typed repair11 result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s33-repair11-focused-validation-result.json)
records the fresh repaired-subject run at **2026-10-09T04:51:35.719977+00:00**.
The initial unvalidated handoff, [original failed result](../../.codex-workflow/phases/PHASE-71.5/p715-02-s33-focused-validation-failure01.json)
(**31 tests, 30 succeeded, 1 failed, 2 completed suites**) and earlier second
suite group **NOT RUN** remain dated history, rather than current failures.
The prior partial-receipt handoff's pending-rerun statement is also historical.

The first group, `cozy.media.CozyMediaPresentationSpec` and
`cozy.media.CozyMediaPdfReviewStateSpec`, reports **31 successes / 2 completed
suites**. The second group, `cozy.media.CozyMediaReceiptSpec`,
`cozy.media.CozyMediaCrossReviewSpec` and `cozy.media.CozyMediaSpec`, reports
**52 successes / 3 completed suites**. The total is **83 successes / 5 completed
suites**, with **0 failed, aborted, canceled, ignored or pending**. All five
selected suites and the unchanged explicit alignment/reuse/render lifecycle,
target-build stale-unselected retention and full-build obsolete-entry
replacement scenarios passed. No test, fixture or expectation was edited for
repair11. Implicit compilation completed for **1 Main Scala source**, with
**zero compiler warnings**.

The **three known duplicate `logback.xml` runtime warnings** remain retained
unsuppressed under **HYG-P715-S19-LOGBACK-001**. One exact hostile-XML rejection
was also retained on stderr:
`[Fatal Error] :1:10: DOCTYPE is disallowed when the feature "http://apache.org/xml/features/disallow-doctype-decl" set to true.`
The parent classified it from the actual source and log as deliberate hostile
XML fixture rejection: the existing unsafe-OOXML and legacy/VisualPage cases
use `hostiledtd`, and the real parser's `disallow-doctype-decl` maps rejection
to failure. The typed result records the source attribution at
`CozyMediaPresentationSpec.scala:426,638,1192` and
`CozyMediaPresentation.scala:769`. Independent Step classification of these
diagnostics remains **OPEN**. Zero compiler warnings is not a claim of zero
runtime diagnostics or independent acceptance.

The [repair11 parent edit admission](../../.codex-workflow/phases/PHASE-71.5/p715-02-s33-repair11-parent-edit-admission.json)
records the actual partial-selection receipt repair. Partial slide builds
retain unselected accepted receipts without reaccepting dependencies; target
merge and untargeted full replacement retain their meanings. Repair cycle
**11** was consumed on the actual source edit, and the current focused
validation converges: **cumulative 11 / nonconverging streak 0**. The six prior
repair-history entries and independent **M2 cycles 6/7/8/10** remain preserved;
independent **M2 cycle 11** also remains **OPEN**. Editing and passing tests do
not complete independent review.

Actual **SBT exit 0 / wrapper exit 0 / serial lock released** establish terminal
execution. Fresh registered **revision-20** runner operation is now observed
as `/root/phase715_s33_repair11_sbt_ba34cd9e`; the earlier quote-repair completion
note about pending fresh operation remains historical. This new repaired-subject
attempt needed no recovery, correction or repeated approval. The actual log is
`/private/tmp/skill.cncf.d/a-396741e5-182f-4ae5-a34a-7bf8efb9d6dd/82193-20261009T044958Z.log`.

The original Goal, base `fc640ec25e443d00c22b2c26b437a205d39910eb`, authority,
Goal binding, PLAN epoch, finite work list, aggregate metadata, Stage Status
triples and closure checkboxes retain their state, together with prior refusals
and reviews. Prior **S32 48 successes / 4 completed suites** and earlier native
evidence remain separately dated. Concrete external **E715-01..05**, other
original finite work, final current **JA34 native generation, assembly, audible
hearing, end-card and EOF**, whole-Step and full-Phase reviews, exact local Step
commits and the distinct release remain **OPEN**. Repository-full SBT and
broader integration remain **deferred-not-run to PHASE-71.7**. This factual
addition performs no workflow transition and claims no Step acceptance, Phase
closure or commit.
