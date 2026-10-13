# Document Project Summary Slide PDF Projection Design

Status: NORMATIVE DESIGN; Phase 59 contract reconciled by Phase 71.1 / P710-03-DM-09

This design defines the explicit `cozy.summary-slide-projection.v1` profile
that connects admitted Document Project v2 Core, Document Description, and
Summary Description inputs to the accepted Visual Page and `summary_slides_pdf`
routes. It is paired with
[`docs/spec/document-project-summary-slide-pdf-projection.md`](../spec/document-project-summary-slide-pdf-projection.md).
It changes neither the v1/v2 source schemas nor the Phase 40 renderer contract;
it preserves the accepted Visual Page Revision 1 catalog/binding shape and
admits only the closed Revision 2 standalone extension defined here.

## 1. Ownership and input boundary

The Content Core remains the locale-independent authority for Steps, Nodes,
Relations, and Flows. The v2 Document Description remains the localized
document authority. The v2 Summary Description remains the localized concise
selection authority: it owns wording, order, emphasis, retained points,
omissions, and authored diagram item/edge selections.

The Projection Profile is a fourth, direct regular non-symlink YAML authority.
It is an authored presentation selection, not `content/<locale>/summary.yaml`,
a renderer layout, or a template. The profile root is closed and contains
exactly, in this order:

```text
schema, version, id, core, document, summary, locale,
media, catalog, binding, pages
```

`schema` is exactly `cozy.summary-slide-projection.v1`; `version` is integer
`1`; `id` is a stable nonempty profile ID; and `locale` equals the bound v2
Document and Summary locale. Canonical `core`, `document`, and `summary` are
`{id, path}`. Their logical IDs match the loaded sources and nested bindings;
their paths are safe project-relative paths.

Canonical `media` is `{id, path, summarySlidesPdf}`. Its `id` equals the direct
`media.yaml` descriptor's `knowledge.id`; `path` is its safe project-relative
direct-file path; and `summarySlidesPdf` identifies one resource in that
descriptor. These required fields bind the descriptor and target without
copying configuration. Canonical `catalog` is `{id, revision, path}`, with a
positive admitted fixed revision (1 or 2). Canonical `binding` is
`{id, profile, path}`, where `profile` is exactly `business`. Loaded catalog ID
and revision and binding ID and profile must match these records.

The six input-binding objects read only their required typed fields and ignore
ordinary extra metadata of any scalar, object, array, or null shape. Such
metadata is not retained or re-emitted. Historical hash properties follow this
same ordinary rule without a hash-specific branch or codec. The enclosing
objects and required field types remain mandatory; root/page/item/edge/parameter
closure, schema/version, ordered root fields, and strict UTF-8/YAML admission
remain unchanged.

Every path is safe project-relative POSIX spelling. Absolute paths, traversal,
URI-like values, control characters, backslashes, non-normalized spellings,
nonregular files, final symlinks, and symlinked ancestors below the allowed root
are rejected. The projector loads current source bytes and validates semantics
without a raw-byte identity gate. A valid whitespace change needs no rebinding;
a current authored localized label becomes the projected label.

The projector admits and uses one pre-existing fixed catalog/binding pair. It
does not generate, copy, or change either. The descriptor target named by
`media.summarySlidesPdf` must be an existing Phase 40 `summary_slides_pdf`
resource with `contract: visual-page-v1`, `profile: business`, language equal to
the profile locale, knowledge equal to the Core ID, and catalog/binding
references exactly matching the profile records. Its configured infographic is
the sole selected asset.

## 2. Ordered page projection

`pages` is a nonempty ordered array. Each entry contains exactly required
`id`, `summaryUnitId`, `logicalPattern`, `visualPattern`, `items`, `edges`, and
`visualParameters`, plus optional `emphasisItem`:

```text
id, summaryUnitId, logicalPattern, visualPattern,
items, edges, [emphasisItem], visualParameters
```

`id` is a unique stable page ID. `summaryUnitId` resolves exactly once to a v2
Summary unit. `logicalPattern` and `visualPattern` are exact IDs in the selected
catalog and the catalog declares their compatibility. `items` is a nonempty
ordered unique array of exactly `{diagramItemId, role}`. `edges` is an ordered
unique array of exactly `{diagramEdgeId}`. Each item and edge resolves to the
selected Summary unit's authored diagram. The item role, relation type,
endpoints, and forward direction are resolved only from that Summary diagram
edge and its existing Core target; the profile supplies no alternative relation,
endpoint, direction, or presentation-reading field.

For a selected Summary unit, every declared diagram edge occurs in exactly one
of that unit's contiguous mappings. Each mapping lists that selected edge's
exact endpoint items. A diagram item may occur in more than one contiguous
mapping only when it is a shared endpoint of distinct selected diagram edges;
every occurrence retains the same `diagramItemId` and Core reference, never a
clone or invented element. Its catalog role is page-local and may differ only as
compelled by the selected existing logical pattern and relation rule; it never
changes Core meaning. An item with no selected edge occurs exactly once, and a
mapping may not include an unrelated item. A unit selected for projection maps
all its declared edges and their endpoint items under these rules; a unit has no
mapping only when it is not selected for slide projection at all. There is no
implicit pagination, fitting, or renderer-side expansion. Page order first
follows Summary-unit order and then the source order of that unit's explicit
mappings; mappings for different units must not interleave.

`emphasisItem` is the sole semantic emphasis authority. When present, it is the
selected unit's exact `focusItem` and is in that page's `items`. Only the
projector may derive the existing catalog `emphasisNode` parameter, and only
when that item is a Node and the selected Visual Pattern supports it. A Step
focus has no fabricated Node substitute. When `emphasisItem` is absent, is not a
Node, or the pattern does not support it, no `emphasisNode` is emitted.
`visualParameters` must reject a profile-supplied `emphasisNode`; for all other
selected catalog parameters, each required parameter occurs once and optional
parameters occur only when explicit. There are no aliases, defaults, CSS,
coordinates, fonts, page sizes, PDF settings, or physical slots.

Revision 2 adds one closed relationless mapping form only: `logicalPattern:
standalone` with `visualPattern: standalone-card`, exactly one Summary diagram
item in role `item`, zero edges, no `emphasisItem`, and empty
`visualParameters`. Its item is not an endpoint of any declared Summary edge
and occurs in exactly one mapping. No Core Relation, alternative Core meaning,
or renderer interpretation is synthesized. Every edge-bearing mapping retains
the existing edge resolution, roles, endpoints, and direction rules unchanged;
it cannot include that relationless item.

## 3. Article 9 overview projection

The Article 9 `application-overview` requires the following explicit,
contiguous three-page projection in Summary order; it is not an automatic
expansion rule. A Revision 2 relationless standalone mapping MAY append only
after the authored Root Step DiagramItem is explicitly selected once and
edge-free. Omitting that selection preserves the three-page result:

1. A `mapping` page with a compatible mapping visual pattern maps
   `root-domain-model` to `root-application-model` through the
   `maps-to` relation.
2. A `dependency-map` page with `flow-vertical` maps
   `use-case-realization` to `application-foundation` through
   `depends-on`, with roles `dependent` -> `dependency`.
3. A `dependency-map` page with `flow-vertical` maps
   `application-conclusion` to `use-case-realization` through
   `depends-on`, with roles `dependent` -> `dependency`.
4. When the authored Root Step DiagramItem is selected once and edge-free, a
   relationless `standalone` page with `standalone-card` MAY append and map the
   existing root Step `application-modeling` as
   `overview-application-modeling`, role `item`, with zero relations and zero
   visual parameters.

The three pages bind, respectively, the Summary item/edge IDs
`overview-domain-model`, `overview-application-model`, and
`overview-domain-to-application`; `overview-realization`,
`overview-foundation`, and `overview-realization-to-foundation`; and
`overview-conclusion`, `overview-realization`, and
`overview-conclusion-to-realization`. `overview-realization` is the same source
item on both dependency pages: `dependent` in the
`use-case-realization -> application-foundation` page and `dependency` in the
`application-conclusion -> use-case-realization` page. This required page-local
reappearance is not cloning. Every edge remains in exactly one mapping and
retains its existing `forward` direction. There is no inverse-reading field,
relation alias, visual chain, or catalog revision.

## 4. Canonical output and provenance

The transformation returns one validated canonical `cozy.visual-page-set.v1`
and provenance without filesystem mutation. Its media descriptor path is returned
as in-process coordination context, not a new PageSet/provenance schema field.
A distinct writer takes the validated projection and required explicit
`pageSetOutput` path, creates its parent as needed, and directly writes the
validated canonical PageSet JSON followed by a newline. Ordinary I/O failures
report `SUMMARY_SLIDE_PROJECTION_OUTPUT` and may leave partial output; there
is no staging, rollback, or prior-output preservation promise.

`CozySummarySlidePdf` is the coordinator (X). It reads the selected media
resource's `source`, resolves one connection path against the descriptor root,
and passes it to the converter/writer (A) as output and the existing Phase 40
route (B) as input. A may validate the bound media descriptor's semantic target,
catalog, binding, and asset declarations, but never reads the target's `source`
or compares its output argument with B's input configuration. B consumes the
explicit path passed through the existing media build flow, retaining its
renderer, structural verification, receipts, and currentness handling.

The connection retains the root-level normalized project-relative PageSet input
grammar; this is the existing reference-base contract, not an output-protection
policy. A does not enumerate protected/generated paths or perform special
collision, overlap, filesystem-alias, or destination-symlink checks. These checks
are removed, not relocated to X. Ordinary required-output and write-error handling
remain. The PageSet need not exist before transformation, and a materially wrong
configuration is not guaranteed safe against overwriting another file. Semantic
input validation and Phase 40's existing renderer checks remain independent.

PageSet `id` equals profile `id`; each Page
`id` equals mapping `id`; Page `knowledge` equals Core `id`; and Page `language`
equals profile locale. A Visual Page node `id` equals `diagramItemId`; its label
is the existing localized label resolved for the bound Core reference from the
admitted v2 Document/Summary selection. Missing source-defined localized labels
fail closed rather than synthesize text. A relation `id` equals `diagramEdgeId`;
its type, `from`, `to`, and source references resolve exactly from the selected
Summary edge and Core target.

Every generated Page declares sources, in deterministic order, for `core`,
`document`, `summary`, `profile`, and `media`; each source `{id,path}` is the
corresponding bound safe path, project-root-relative to the PageSet parent.
Page assets contain exactly the descriptor target's configured infographic as
`{id,path,mediaType}`, resolved from the declared existing media resource;
its path is likewise project-root-relative to that PageSet parent. Each page
references that same sole asset, so the current Phase 40 required-asset check
succeeds. DM12 removes the material hash field and computation. Safe direct
files, media types, and actual-byte agreement with the selected resource remain
the connection authority; current valid bytes require no digest rebinding.

All Summary data remains bound to its logical source and safe path.
Existing Visual Page output contains only selected diagram nodes/edges,
catalog-supported relation labels, and the projector-derived eligible emphasis
node. It does not
contain `heading`, `message`, `emphasis`, `navigationLabel`, retained points,
omissions, arbitrary CSS, physical layout, or new tag text. Semantic
tag/structure marking beyond that unchanged Visual Page/renderer contract is
out of scope and is not silently encoded.

Operation provenance retains exact logical IDs and safe actual relative paths
for profile, Core, Document, Summary, media, catalog, and binding, plus locale
and selected media target. Every input entry is exactly `{id, path}`. This
boundary computes, stores, parses, compares, and emits no profile or bound-input
artifact hash. It adds no Visual Page fields. Phase 40 owns renderer invocation,
physical layout, PDF generation, verification, receipt construction, and review
currentness. The projector outputs only the canonical PageSet and connects it,
with the pre-existing binding, to that route; it writes no PDF, PPTX, renderer
manifest, receipt, catalog, or binding.

The copied Article 9 media descriptor still records those seven authorities as
bytes-normalized receipt inputs. The existing receipt-currentness scenario
continues to document this unfinished Phase 40 consumer; it does not prove the
desired final timestamp behavior. Visual Page canonical/catalog/binding
identities, renderer/output manifest hashes, and media receipt/currentness
hashes remain unfinished SAME-Phase layers for dedicated migration, with current
contracts only until that migration. DM12 material-field removal does not
claim timestamp/adoption integration or Step/Phase completion.

Projection and coordinator `prepare` read current valid bytes. Independent
whitespace changes preserve the complete canonical semantics and non-hash
provenance, without replacing an existing PageSet, PDF, renderer manifest, or
receipt. A changed author label updates the selected node while retaining
logical IDs, page order, relations, and provenance. Generation/currentness is a
separate integration responsibility: declared producers, timestamps,
conservative regeneration, and explicit prebuilt adoption remain unfinished
Phase obligations under [file-update-management.md](../spec/file-update-management.md).
The pure projector introduces no cache, timestamp gate, receipt scheme,
collision protection, or generation operation.

## 5. Failure and non-goals

Validation fails before PageSet output or receipt visibility for missing,
unknown closed-grammar fields, duplicate keys, malformed required types,
unsafe paths, or mismatched required semantics; a media
target that is not the required existing Phase 40 target; unresolved labels;
duplicate/missing edge or required-endpoint coverage; unrelated items; a
profile-supplied `emphasisNode`; invalid roles, relation types, endpoints,
directions, pattern compatibility, or parameters; and loss, reversal, aliasing,
or synthesis of Core meaning. These validation failures precede writer
invocation and expose no fresh receipt. Ordinary write failure may leave
partial output and never reports a successful new result; recovery is
regeneration. X rejects invalid connection-source grammar; A reports ordinary
write failures without a separate output/input-protection guarantee.

This design neither changes source schemas, Visual Page schemas, the accepted
Revision 1 catalog/binding shape, renderer, receipt, nor Phase 40/58 behavior;
adds layout to Summary; invents semantics; or
publishes/deploys/registers external media.

## Phase 71.1 DM08 boundary reconciliation

Historical DM08 account; its profile/provenance hash statements are superseded
by DM09 below and do not define current admission authority.

Phase 71.1 / P710-03-DM-08 changes only V2 source-model admission: nested
Core, Document, and Summary relationships are checked by logical IDs and
locale, without model hashes. The profile's raw-byte identity, provenance,
media/catalog/asset verification, and receipt/currentness layers remain
unchanged and explicitly unfinished SAME-Phase responsibilities.

## Phase 71.1 DM09 boundary reconciliation

Phase 71.1 / P710-03-DM-09 removes raw identities from the six input-binding
records and seven input-provenance entries. Required logical IDs, safe paths,
locale, revision, business profile, media target, strict YAML, and mappings
continue to validate actual meaning. Generic ignored binding metadata cannot
change canonical projection or logical provenance; missing, malformed, or
mismatched required data still fails before prior output is replaced.

The projector remains one cohesive input-admission and deterministic mapping
responsibility; deletion of its raw-identity helpers requires no extraction or
new abstraction. D02/D04/M05, timestamp/adoption integration, Step/Phase closure,
and successor acceptance are not completed by this Slice. Remaining Visual
Page, renderer, and media receipt hashes require later admitted SAME-Phase
migration; repository-full tests remain deferred-not-run to Phase 71.4.
