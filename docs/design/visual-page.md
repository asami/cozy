# Visual Page Design

Status: NORMATIVE DESIGN; Phase 36 `VIS36-01` is DONE

This design fixes the ownership and architecture boundary for the Visual Page
contract in [`docs/spec/visual-page.md`](../spec/visual-page.md). It admits the
strict direct core commands and the renderer-independent semantic Preview
defined below. It creates no Presentation-route integration, business binding,
renderer contract, receipt, review acceptance, migration result, or
consumer-acceptance claim.

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

The direct Preview command is deliberately outside the future Presentation
route. Its exact public form is:

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

## 5. Binding and generated evidence

`cozy.visual-page.binding.v1` belongs between selected visual semantics and a
physical template. It is the only authored place in this route where mapping
to coordinates, fonts, colors, PowerPoint Shape kinds, or renderer object IDs
is permitted. Generated renderer manifests may repeat physical evidence, but
neither binding nor output changes Visual Page logical authority.

Cozy's future responsibility is strict validation, identity calculation, safe
resolution, orchestration, stale-output rejection, and evidence/receipt
serialization. The external renderer owns rendering and runtime; SmartDox and
Textus consumers own consumer/runtime acceptance. A PPTX, PNG, montage, video
frame, cache, or manifest is derived delivery/review evidence, never semantic
input that can repair a page.

## 6. Presentation and video coexistence

The legacy `presentation` object remains its accepted untagged closed object.
It never permits `contract`, `catalog`, or `binding`. The future presentation
route is a distinct discriminated `presentation` object whose exact keys are
`contract`, `profile`, `catalog`, `binding`, `slideImages`, `montage`,
`rendererManifest`, `reviewManifest`, `reviewState`, `articlePdf`, and
`infographic`; `contract` is exactly `visual-page-v1`, the resource's existing
required `source` is the VisualPageSet input, and `source`, `catalog`, and
`binding` are safe descriptor-relative direct regular non-symlink files under
the descriptor root. They reject empty, absolute, traversal, URI-like, control-
character, and backslash paths; normalization changes; symlink escapes; and
missing or nonregular resolved files. `source` must parse as exactly
`cozy.visual-page-set.v1` with integer version `1`. The profile remains the
accepted exact template/renderer shape. This object accepts no untagged legacy
shape and never infers `--slide-ir`.

Its fixed v2 renderer argv replaces legacy `--slide-ir <source>` with
`--visual-page-set <source> --catalog <catalog> --binding <binding>`. Its exact
v2 manifest canonical top-level order is `schema, target, profile, renderer,
visualPageSetSha256, catalogSha256, bindingSha256, templateSha256, pptx,
slides, montage`. It expects v2 renderer evidence. It coexists with, but does
not replace, the existing `--slide-ir` / `cozy.presentation.render.v1` route.

The future Storyboard route is independently versioned. A v2 scene can point
to exactly one page in a safe Visual Page Set while retaining Storyboard
ownership of narration, speaker, duration, silence, transition, confirmation,
final, and audiovisual-review semantics. The page reference participates in
Storyboard identity; resolved page/catalog/binding/renderer/asset identities
belong to visual evidence and are never guessed into Storyboard content.

The current v1 presentation and Storyboard contracts remain exactly accepted
until a distinct later implementation takes the v2 routes. Existing
`cozy.media.receipt.v2` and review-state v1 schema shapes remain unchanged.
Only an implemented visual-page route may record named page/catalog/binding/
asset inputs inside the existing ordered receipt input evidence and its own
verified v2 renderer/review evidence. No v1 parser, receipt, review state, or
renderer path acquires new behavior from this design.

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
