# Visual Page Design

Status: NORMATIVE DESIGN; Phase 36 `VIS36-01` is DONE; `VIS36-04` remains
IN PROGRESS after the P36-04 implementation record

`P36-04-DEC-001` is consumed. Focused validation invocation
`90284-20260827T085957Z` (`testOnly cozy.video.CozyVideoStoryboardSpec`) reported
16 succeeded, 0 failed/aborted, SBT/wrapper 0, and the lock released.
Independent `P36-04-REREVIEW-002` is PASS and `CB-P36-04-RR-001` is resolved.
P36-04's implementation/validation record is included in this local acceptance
Step commit. No push, publish, or publication is claimed. VIS36-04 and Phase 36 remain IN PROGRESS because
renderer scene identity binding and evidence identity/proof remain open for
P36-05+. `P36-05-DEC-001` is consumed; P36-05 implementation is delivered,
while focused validation and acceptance evidence remain open. VIS36-05 remains
IN PROGRESS; VIS36-06 and Phase 37 remain NOT STARTED.

This design fixes the ownership and architecture boundary for the Visual Page
contract in [`docs/spec/visual-page.md`](../spec/visual-page.md). It admits the
strict direct core commands, the renderer-independent semantic Preview, the
separately discriminated P36-03C presentation route, and the P36-04
Storyboard-v2 reference route defined below. It creates no consumer-acceptance,
migration-result, or downstream-runtime claim.

## 1. One-screen semantic authority

The design chooses one `VisualPage` as the authority for exactly one displayed
semantic screen. It separates three values that must never be collapsed:

```text
logical graph and provenance
  -> selected compatible Visual Pattern and typed parameters
  -> binding-owned physical renderer/template slots
```

The logical graph owns meaning. The selected Visual Pattern owns a renderer-
independent projection choice. The binding owns physical layout. A renderer
therefore cannot infer a Logical Pattern from arrows or slide geometry, and a
template cannot become a source of semantic meaning.

The selected closed catalog revision has canonical root order `schema, version,
id, revision, relations, logicalPatterns, visualPatterns`. Its exact five
relation entries are `{id, direction}` with `direction: from-to` and IDs
`next`, `causes`, `depends-on`, `enables`, and `maps-to`. Its Logical Pattern
entries are exactly `{id, nodeRoles, relationRules}`; node roles are exactly
`{role, min, max}`, and relation rules are exactly `{relation, fromRoles,
toRoles, min, max, topology}`. Role arrays preserve sorted canonical token
order, cardinalities are integers, and topology is only `linear`, `acyclic`,
or `bipartite`. Its Visual Pattern entries are exactly `{id,
compatibleLogicalPatterns, parameters}`, with parameter entries exactly
`{name, type, required}`. Types are only `node-ref`, `boolean`, and `string`;
there are no defaults, aliases, nullable values, or undeclared forms.

The exact initial catalog constraints use literal inclusive integer fields:
`sequence` has `step` min=2, max=8 and `next` step->step min=1, max=7 with
`linear` topology; `causal-chain` has `cause` min=1, max=7, `effect` min=1,
max=7, total min=2, max=8, and `causes` then `enables` cause->effect each
min=1, max=16 with `acyclic` topology; `dependency-map` has `dependency`
min=1, max=7, `dependent` min=1, max=7, total min=2, max=8, and `depends-on`
dependent->dependency min=1, max=16 with `acyclic` topology; and `mapping`
has `source` min=1, max=7, `target` min=1, max=7, total min=2, max=8, and
`maps-to` source->target min=1, max=16 with `bipartite` topology. No catalog
field serializes a dynamic expression.

`linear` requires the exact `step` node set to be one directed connected chain
with one start, one end, one incoming and one outgoing relation for each
internal node, no duplicate endpoint pairs, and exactly step-count minus one
edges. `acyclic` and `bipartite` prohibit duplicate same-type endpoint pairs
and enforce all endpoints and the stated role/total cardinalities, so observed
edge count cannot exceed eligible endpoint-pair count despite the static
maximum of 16. No initial form admits extra nodes or relations.

Canonical arrays are identity-bearing: root order remains stated above;
relations use their listed order; logical patterns are exactly `sequence`,
`causal-chain`, `dependency-map`, `mapping`; visual patterns are exactly
`flow-horizontal`, `flow-vertical`, `mapping-columns`; node roles sort
lexicographically by role; relation rules sort by relation, then lexicographic
`fromRoles`, then `toRoles`; role arrays and compatible logical-pattern arrays
sort lexicographically; and parameter arrays sort lexicographically by name.
Duplicates reject. `flow-horizontal` permits `causal-chain` and `sequence`;
`flow-vertical` permits `causal-chain`, `dependency-map`, and `sequence`; both
have optional `emphasisNode: node-ref` and `showRelationLabels: boolean`.
`mapping-columns` permits only `mapping` and has optional
`showRelationLabels: boolean` plus required nonempty trimmed
`sourceColumnTitle` and `targetColumnTitle` strings. A node-ref is an exact
current-page node ID. A new pattern, relation, alias, or parameter form is an
explicit catalog revision rather than a renderer extension or best-effort
normalization rule.

## 2. Identity and provenance

The contract has two purposeful identities:

- `logicalIdentity` binds page identity, knowledge/language, resolved catalog
  logical catalog identity, logical graph, sources, and source bindings. The
  logical catalog identity is SHA-256 of canonical JSON for catalog `schema`,
  `version`, `id`, `revision`, `relations`, and `logicalPatterns`. It excludes
  projection selection, parameters, and assets.
- `visualPageIdentity` binds the complete normalized page, including selected
  projection and each declared asset `sha256`, to the resolved full catalog
  identity, which is SHA-256 of complete canonical catalog JSON.

Compatible horizontal and vertical projections can therefore retain one
logical identity, while a Relation or provenance change cannot be hidden by a
visually identical arrow. Every asset is exactly `{id, path, mediaType,
sha256}`; its lowercase digest validates the resolved direct regular asset
bytes. Same-path byte changes without a digest update fail closed, and a
correct digest update changes `visualPageIdentity`. Asset and projection
changes do not rewrite logical content but do stale dependent visual evidence.

`VisualPageSet` permits ordered presentation consumption without making a
single page multi-screen. Its sequence is identity-bearing and every contained
page ID is unique. Every page declares the same catalog `{id, revision}` pair,
and resolution accepts exactly one supplied resolved catalog matching that pair
and its full identity; a mixed pair, unresolved catalog, or mismatch fails
closed. The one media-route catalog/binding pair therefore binds every page
deterministically. A presentation route cannot silently reorder pages and a
Storyboard v2 `pageId` resolves exactly once. Duplicate page IDs and zero or
multiple resolution matches fail closed.

## 3. Representation architecture

Markdown is review-oriented, YAML is human-authored structured input, and JSON
is machine interchange. None is a different semantic model. They converge
before identity calculation:

```text
restricted Markdown / YAML / JSON
  -> strict representation parser
  -> typed VisualPage or VisualPageSet
  -> catalog resolution and validation
  -> canonical JSON and identities
```

Strict rejection of unknown information, YAML aliases/merges, duplicate keys,
unsafe references, and non-lossless Markdown is intentional. It prevents a
serializer from claiming an identity for content it dropped or rewrote.

## 4. Fast semantic preview

The direct Preview command is deliberately outside the implemented separate
Presentation route. Its exact public form is:

```text
cozy media visual-page preview <input> --catalog <catalog> --save <output.html> [--png <output.png>]
```

Preview first takes the same strict `VisualPage.load` path as the direct core
commands. It neither infers, repairs, selects, nor alters semantic content.
Its semantic input is limited to the validated Visual Page or ordered Visual
Page Set and resolved catalog. It accepts no binding, template, renderer,
PPTX, PowerPoint, receipt, review, video, or other generated-artifact input.

The required UTF-8 HTML file is a deterministic semantic review artifact. It
shows ordered page IDs; each page's Logical and Visual Pattern IDs; ordered
logical nodes; Relations with ID, type, `from-to` direction, endpoints, and
source references; typed visual parameters; and document/catalog identities.
Every page-supplied value is HTML-escaped. The HTML has no physical layout or
renderer vocabulary in its input contract.

`--png` is optional. When requested, Preview generates a deterministic
logical-structure PNG solely from that same validated value. Its physical
diagram arrangement is generated output only. It is not an input to a PPT,
business binding, renderer, receipt, review, video, or another reusable
physical-layout contract.

`--save` occurs exactly once and targets exactly one `.html` output; `--png`
occurs zero or one time and targets exactly one `.png` output. Missing,
duplicate, unsupported, valueless, or ambiguous arguments reject, as do
normalized-identical HTML and PNG targets. Preview validates arguments,
outputs, input, and catalog and renders all selected bytes before it atomically
replaces either target through same-directory moves. Invalid input, catalog, or
arguments leave all pre-existing selected output bytes unchanged.

The deterministic text report has this ordered form, with `pngIdentity` absent
when `--png` is absent:

```text
schema: cozy.visual-page.preview.v1
version: 1
generator: cozy media visual-page preview
documentIdentity: <validated-document-identity>
catalogIdentity: <resolved-catalog-identity>
htmlIdentity: <sha256-html-bytes>
pngIdentity: <sha256-png-bytes>
previewIdentity: <preview-provenance-identity>
status: generated
```

`previewIdentity` binds `cozy.visual-page.preview.v1`, validated document
identity, catalog identity, HTML digest, and optional PNG digest in that order.
It is output provenance only: it does not change a Visual Page, Media Package
receipt, binding, renderer, or review identity.

## 5. Versioned business binding

`cozy.visual-page.binding.v1` is the separate business-binding value between
selected Visual Page semantics and a physical template. Its canonical JSON
root is exactly `schema`, `version`, `id`, `profile`, `catalog`, and `patterns`.
`schema` is exactly `cozy.visual-page.binding.v1`, `version` is integer `1`,
`id` and `profile` are nonempty canonical identity tokens, and this first
business binding requires `profile: business`. `catalog` is exactly
`{id,revision}` and must equal the resolved Visual Page catalog pair supplied
to the strict loader.

`patterns` is nonempty and contains exactly `{visualPattern,slots}` entries.
Each `visualPattern` is a catalog Visual Pattern ID; entries are unique and
canonical output sorts them lexicographically. The binding is complete: every
Visual Pattern in the selected resolved catalog occurs exactly once, and every
pattern selected by the supplied validated Visual Page document is therefore
bound. `slots` contains exactly five entries, each exactly
`{semanticSlot,physicalSlot}`. The semantic slots are exactly
`knowledge`, `nodes`, `relations`, `assets`, and `parameters`, in that order;
each `physicalSlot` is a nonempty identity token and is unique within its
Visual Pattern entry.

The loader reads only a direct regular UTF-8 `.json` file with neither a final
symlink nor a symlinked ancestor below its allowed root. It
rejects unsupported suffixes, malformed UTF-8 or JSON, duplicate or unknown
fields, unsafe/nonregular/symlink input, wrong schema/version/profile, invalid
tokens, catalog mismatches, unknown or incomplete patterns, and invalid slot
sets with structured `VISUAL_PAGE_BINDING_*` diagnostics carrying a field path
and reason. Equivalent JSON key or pattern-entry ordering normalizes to one
canonical JSON value. `bindingIdentity` is the deterministic SHA-256 identity
of those canonical UTF-8 binding bytes; it does not alter Visual Page logical,
visual-page, or document identity.

Physical slots are opaque template/renderer slot identifiers only. They carry
no coordinate, font, color, PowerPoint Shape kind, renderer object ID,
template path, or executable command. P36-03B validates and identifies the
business binding; P36-03C consumes it only in the separately discriminated
presentation route below. Neither binding nor output changes Visual Page
semantic authority. SmartDox and Textus consumer acceptance remains separate.

## 6. Presentation and video coexistence

The legacy `presentation` object remains its accepted untagged closed object.
It never permits `contract`, `catalog`, or `binding`. P36-03C implements a
distinct discriminated `presentation` object whose exact keys are
`contract`, `profile`, `catalog`, `binding`, `slideImages`, `montage`,
`rendererManifest`, `reviewManifest`, `reviewState`, `articlePdf`, and
`infographic`; `contract` is exactly `visual-page-v1`, the resource's existing
required `source` is the VisualPageSet input, and `source`, `catalog`, and
`binding` are safe descriptor-relative direct regular files under the
descriptor root, with neither a final symlink nor a symlinked ancestor. They
reject empty, absolute, traversal, URI-like, control-character, and backslash
paths; normalization changes; symlink escapes; and missing or nonregular
resolved files. `source` must parse as exactly
`cozy.visual-page-set.v1` with integer version `1`. The profile remains the
accepted exact template/renderer shape. This object accepts no untagged legacy
shape and never infers `--slide-ir`.

Its fixed v2 renderer argv replaces legacy `--slide-ir <source>` with
`--visual-page-set <source> --catalog <catalog> --binding <binding>`. Its exact
v2 manifest canonical top-level order is `schema, target, profile, renderer,
visualPageSetSha256, catalogSha256, bindingSha256, templateSha256, pptx,
slides, montage`. Its three semantic identity fields are lowercase 64-hex
canonical identities, not raw source bytes; template and generated artifacts
use raw byte SHA-256. Each page-ordered slide entry is exactly `id`, `path`,
`sha256`, `pptxSha256`, and page-asset-ordered `assets` entries of exactly
`id`, `sha256`. Cozy reconstructs the separate
`cozy.media.presentation-review.v2` evidence from the trusted VisualPageSet,
catalog, binding, template, renderer, artifacts, article PDF, and infographic;
that reconstruction verifies freshness only and never records semantic
approval. The route coexists with, but does not replace, the existing
`--slide-ir` / `cozy.presentation.render.v1` route.

The independently versioned JSON-only Storyboard v2 route uses a closed
`visual-page` screen reference of exactly `{kind,source,catalog,pageId}`. Both
paths are direct safe descriptor-relative files rooted at the Storyboard source
directory: neither permits empty, absolute, traversal, URI-like,
query/fragment/control, backslash, non-normalized, final-symlink,
ancestor-symlink, missing, or nonregular input. Cozy loads the VisualPageSet
through the supplied catalog and resolves `pageId` exactly once. A v2 scene
therefore retains Storyboard ownership of narration, speaker, duration,
silence, transition, confirmation, final, and audiovisual-review semantics.
The literal reference participates in Storyboard identity; resolved
page/catalog/binding/renderer/asset identities belong to later visual evidence
and are never guessed into Storyboard content.

P36-05 implements that later review-proof boundary without broadening the
Visual Page model. A v2 visual-page review owns one safe project-relative
binding, an evidence directory, and a required approval identity; it records
the literal screen reference with the resolved VisualPageSet, catalog,
logical-page, visual-page, selected asset, binding, and effective selected
renderer identities. The v2 handoff has its own canonical identity. Changed,
missing, unsafe, unresolved, or noncanonical inputs fail before confirmation,
final build, cache reuse, or output work. This route invokes no renderer and
does not accept any external consumer.

The current v1 presentation and Storyboard contracts remain exactly accepted.
P36-04 provides v2 parsing, planning metadata, and the explicit
`migrate --from v1 --to v2 --screen text <input> --save <output.json>`
adapter only. Existing `cozy.media.receipt.v2` and review-state v1 schema shapes remain unchanged.
The implemented visual-page presentation route requires explicit existing
receipt inputs for the VisualPageSet and catalog as structured documents and
for the binding and template as bytes. Assets are proved by strict Visual Page
loading plus v2 renderer/review evidence; this does not extend cross-media
receipt semantics. No v1 parser, receipt, review state, or renderer path
acquires new behavior from this design.

## 7. Explicit migration only

The v1-to-v2 Storyboard migration can produce only a text-screen equivalent; a
visual-page screen cannot downgrade because v1 would lose its semantic
reference. Legacy Slide IR conversion is one direct command:

```text
cozy media presentation migrate <legacy-slide-ir> --semantic-map <semantic-map> --catalog <catalog> --save <visual-page-set>
```

Each required option occurs once, in separated or `--option=value` form. Its
semantic map is a direct regular non-symlink UTF-8 `.json` file and a closed
`cozy.visual-page.migration-map.v1` object with integer `version: 1`, exactly
the named fields `schema, version, legacySlideIrSha256, visualPageSet,
bindings`, and no duplicate or unknown fields. JSON member order carries no
semantic meaning; only the deterministic report and saved VisualPageSet are
canonical ordered outputs. The digest is lowercase SHA-256 of unnormalized raw direct legacy Slide IR
bytes. `visualPageSet` is a complete embedded `cozy.visual-page-set.v1`, not a
path or Page; the Visual Page contract resolves it through the separately
supplied catalog using the semantic-map directory as its source/asset root.

Every binding is exactly `{slideId, elementIndex, target}` and every target is
exactly `{pageId, kind, id}`. `elementIndex` is nonnegative and zero-based;
`kind` is only `node-label`, `asset-id`, or `source-path`. The complete
flattened legacy `(slideId, elementIndex)` source set and target
`(pageId, kind, id)` set are each bijective. Page IDs and selected node-label,
asset-ID, or source-path target values resolve exactly once. Text binds only
to an equal node-label/source-path value; assets bind only to an equal asset
ID. Any ambiguity, loss, inference, malformed/unsafe input, schema/version or
digest mismatch, or incomplete/duplicate/unresolved/wrong-kind binding fails
without an output claim.

Only a validated map atomically replaces the `.json` output in its directory
with canonical VisualPageSet JSON plus newline, then emits a deterministic
`cozy.visual-page.migration-report.v1` text report containing its version, raw
legacy digest, source/binding count, catalog identity, VisualPageSet identity,
and `status: migrated`. This boundary invokes no renderer, receipt,
Storyboard, Media Package, review state, or external consumer. It has no
inference path and does not declare an old slide to be a Logical Pattern.

## 8. Deferred boundary

Subject Patterns, Explanation or Argument Patterns, explanation development,
AI pattern/parameter selection, and deterministic multi-page or multi-scene
expansion remain Phase 37 concerns. This foundation admits a `VisualPageSet`
only as an ordered set of already-complete one-screen values; it does not
choose how values are authored or selected.
