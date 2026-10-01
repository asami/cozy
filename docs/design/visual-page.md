# Visual Page Design

Status: NORMATIVE DESIGN; Phase 36 `VIS36-01` through `VIS36-06` are DONE.

`P36-04-DEC-001` is consumed. Focused validation invocation
`90284-20260827T085957Z` (`testOnly cozy.video.CozyVideoStoryboardSpec`) reported
16 succeeded, 0 failed/aborted, SBT/wrapper 0, and the lock released.
Independent `P36-04-REREVIEW-002` is PASS and `CB-P36-04-RR-001` is resolved.
P36-04 through P36-06 are accepted as complete. P36-06's closed Cross-media
Review route strictly rejects duplicate JSON object fields before decoding
Cross-media proof or Storyboard v2 evidence/handoff. No push, publish, or
external-consumer acceptance is claimed; Phase 37 remains NOT STARTED.

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

Canonical arrays preserve normalized semantics: root order remains stated above;
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

Revision 1 remains admitted unchanged with exactly those five relation entries,
four logical patterns, and three visual patterns. Revision 2 is the one closed
additive shape: it retains every Revision 1 array entry in its existing order,
then appends `standalone` as exactly one `item` node (`min=1`, `max=1`) with
zero relation rules, and appends `standalone-card` as compatible only with
`standalone` and with zero parameters. Revision 2 admits no hybrid shape,
relation alias, extra `item`, visual parameter, or inferred rendering meaning.
The relationless form is still an explicit logical/visual selection, never a
renderer-side reading of an empty graph.

## 2. Current typed inputs and provenance

DM14 removes the five VisualPage-owned digest APIs and calculations without
replacement proof APIs. `ValidatedDocument` is exactly
`(document,catalog,canonicalJson)`. Compatible projection changes and valid
content updates are processed by the current typed model. Canonical serializers
support normalization and lossless interchange, not historical-current proof.

Every asset requires `{id,path,mediaType}` and resolves to a safe direct regular
file beneath the actual input parent, without symlinked ancestors or leaf.
Current valid bytes require no digest rebinding. PageSet order remains semantic,
page IDs are unique, every page selects the same catalog ID/revision, and each
reference resolves exactly once. Actual graph, role, parameter, catalog, and
path validation remains strict.

## 3. Representation architecture

Markdown is review-oriented, YAML is human-authored structured input, and JSON
is machine interchange. None is a different semantic model. They converge through typed normalization:

```text
restricted Markdown / YAML / JSON
  -> strict representation parser
  -> typed VisualPage or VisualPageSet
  -> catalog resolution and validation
  -> canonical JSON
```

YAML aliases/merges, duplicate keys, unsafe references, and non-lossless
Markdown reject. Asset readers require the three current fields and ignore
ordinary extra asset fields without parsing, validating, retaining, or
re-emitting them, including malformed obsolete metadata. All other object
shapes remain closed. Restricted Markdown permits this normalization only in
`assets:` or `pages:` inline JSON: the original inline JSON must already be
compact canonical JSON, required-key order is preserved while extras are
removed, and the entire normalized envelope must equal canonical Markdown.
Heading, prose, field order, line-count, and trailing-LF constraints remain.

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
source references; typed visual parameters; document schema and ID; and catalog
ID and revision.
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

The deterministic text report has this ordered form, with `png` absent
when `--png` is absent:

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

Phase 71.1 supersedes Preview hash-output provenance with these semantic
identifiers and generation results. The report and HTML compute and emit no
artifact digest or document/catalog digest metadata. Optional PNG presence is
selected by the command. Deterministic rendering, semantic ordering and strict
validity remain, including PNG CRC32 format integrity; a valid label change is
rendered directly without identity rebinding.

The direct Preview, DM12 material, and DM14 VisualPage proof removals leave
Explanation/Document, renderer, receipt, review-state, and output identities as
unfinished same-Phase migration obligations, not
permanent integrity exemptions. It claims neither full inventory removal nor
timestamp currentness/adoption, Step or Phase completion, or downstream
consumer acceptance.

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
canonical JSON value. `ValidatedBinding` carries the typed `binding` and
`canonicalJson`; `Binding.canonicalValue` parses that canonical serialization
into the exact complete object `schema,version,id,profile,catalog,patterns`,
using existing BindingFault vocabulary on conversion failure. The typed value
preserves the actual physical mapping; consumers do not copy it into a
historical-current proof or require historical slot equality.

Physical slots are opaque template/renderer slot identifiers only. They carry
no coordinate, font, color, PowerPoint Shape kind, renderer object ID,
template path, or executable command. P36-03B validates and identifies the
business binding; P36-03C consumes it only in the separately discriminated
presentation route below. Neither binding nor output changes Visual Page
semantic authority. SmartDox and Textus consumer acceptance remains separate.

Accordingly, a Revision 2 business binding contains the preserved three
Revision 1 visual-pattern entries and one `standalone-card` entry. That entry
uses the same complete five semantic slots; it introduces no sixth slot,
physical-layout meaning, or binding-generation path.

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

Its fixed v2 renderer argv replaces `--slide-ir <source>` with
`--visual-page-set <source> --catalog <catalog> --binding <binding>`.
After filtering the five obsolete PageSet/catalog/Binding proof properties,
renderer fields are `schema,target,profile,renderer,templateSha256,pptx,slides,montage`.
Missing or any-valued obsolete properties do not veto generation. Slides retain
ordered required IDs/assets and actual PPTX/PNG paths and structure; linked
OOXML media are validated against current safe declared bytes. Summary-PDF v2
applies the same deletion while retaining actual PDF page count and PNG checks.

Review wrappers for `visualPageSet`, `catalog`, and `binding` contain only
`{path}`. The private page evidence is `(id,assets)` and the aggregate evidence
is `(reviewPath,pages)`; neither contains semantic snapshots. Failed generation
keeps prior output. Template/output/artifact hashes, receipt input-set checks,
and review-state currentness remain unfinished later same-Phase work.

Storyboard v2 keeps the literal `{kind,source,catalog,pageId}` screen reference.
Its review loads the actual current set, named catalog, selected page, and
Binding. Generated entries are `sceneId,kind,source,catalog,pageId,assets`, with
`{id,path,mediaType,modifiedAt}` asset observations and no VisualPage digest or
Binding copy. Legacy removed proof fields are ignored; old assets or Binding
snapshots are not compared with current inputs. The non-VisualPage Storyboard,
approval, self-identity, optional visual-input, and effective-renderer checks
remain distinct unfinished obligations, not substitutes for removed proof.

CrossReview resolves current per-side page references and already-declared
asset IDs/media types/actual bytes. It neither compares semantic snapshots nor
demands a saved aggregate equal a freshly reconstructed whole value. It validates
saved operational structure and ID references. Build constructs once and may
atomically replace a safe direct regular aggregate, but never actual input or
review evidence. It records `not-recorded` approvals and invokes no renderer.
Existing media receipt admission and outer evidence/artifact hashes remain
later work; this bounded change claims no Step/Phase closure or external
renderer/consumer acceptance.

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
the named fields `schema, version, visualPageSet, bindings`, and no duplicate
or unknown fields. JSON member order carries no semantic meaning; only the
deterministic report and saved VisualPageSet are canonical ordered outputs.
`visualPageSet` is a complete embedded `cozy.visual-page-set.v1`, not a
path or Page; the Visual Page contract resolves it through the separately
supplied catalog using the semantic-map directory as its source/asset root.

Every binding is exactly `{slideId, elementIndex, target}` and every target is
exactly `{pageId, kind, id}`. `elementIndex` is nonnegative and zero-based;
`kind` is only `node-label`, `asset-id`, or `source-path`. The complete
flattened legacy `(slideId, elementIndex)` source set and target
`(pageId, kind, id)` set are each bijective. Page IDs and selected node-label,
asset-ID, or source-path target values resolve exactly once. Text binds only
to an equal node-label/source-path value; assets bind only to an equal asset
ID. Any ambiguity, loss, inference, malformed/unsafe input, schema/version,
or incomplete/duplicate/unresolved/wrong-kind binding fails
without an output claim.

Only a validated map atomically replaces the `.json` output in its directory
with canonical VisualPageSet JSON plus newline, then emits a deterministic
`cozy.visual-page.migration-report.v1` text report containing exactly schema,
version, source/binding count, and `status: migrated`. This boundary invokes no renderer, receipt,
Storyboard, Media Package, review state, or external consumer. It has no
inference path and does not declare an old slide to be a Logical Pattern.

## 8. Deferred boundary

Subject Patterns, Explanation or Argument Patterns, explanation development,
AI pattern/parameter selection, and deterministic multi-page or multi-scene
expansion remain Phase 37 concerns. This foundation admits a `VisualPageSet`
only as an ordered set of already-complete one-screen values; it does not
choose how values are authored or selected.
