# Document Project Summary Slide PDF Projection Design

Status: NORMATIVE DESIGN; Phase 59, Step P590-01, Slice P590-01A

This design defines the explicit `cozy.summary-slide-projection.v1` profile
that connects admitted Document Project v2 Core, Document Description, and
Summary Description inputs to the accepted Visual Page and `summary_slides_pdf`
routes. It is paired with
[`docs/spec/document-project-summary-slide-pdf-projection.md`](../spec/document-project-summary-slide-pdf-projection.md).
It changes neither the v1/v2 source schemas nor the Visual Page, binding,
catalog, or Phase 40 renderer contracts.

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
Document and Summary locale. `core`, `document`, and `summary` are each exactly
`{id, identity, path}`. Their identities are raw admitted-byte identities and
their paths are safe project-relative paths.

`media` is exactly `{id, identity, path, summarySlidesPdf}`. Its `id` equals
the direct `media.yaml` descriptor's `knowledge.id`; `identity` is that
descriptor's exact raw-byte identity; `path` is its safe project-relative
direct-file path; and `summarySlidesPdf` identifies one resource in that
descriptor. It is a closed binding to the descriptor and target, not a copy of
the descriptor's configuration. `catalog` is exactly `{id, revision, identity,
path}`. `binding` is exactly `{id, identity, profile, path}`, where `profile`
is exactly `business`.

Every path is safe project-relative POSIX spelling. Absolute paths, traversal,
URI-like values, control characters, backslashes, non-normalized spellings,
nonregular files, final symlinks, and symlinked ancestors below the allowed root
are rejected. The profile's own raw identity is retained only in operation
provenance, never copied into the profile.

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

## 3. Article 9 overview projection

The Article 9 `application-overview` is an explicit, contiguous three-page
projection, not an automatic expansion rule:

1. A `mapping` page with a compatible mapping visual pattern maps
   `root-domain-model` to `root-application-model` through the
   `maps-to` relation.
2. A `dependency-map` page with `flow-vertical` maps
   `use-case-realization` to `application-foundation` through
   `depends-on`, with roles `dependent` -> `dependency`.
3. A `dependency-map` page with `flow-vertical` maps
   `application-conclusion` to `use-case-realization` through
   `depends-on`, with roles `dependent` -> `dependency`.

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

Successful projection produces exactly one existing canonical
`cozy.visual-page-set.v1`, written as one direct regular file at the bound media
descriptor project root—the parent directory of bound `media.yaml`—never under
`presentation/`, `target/`, or another subdirectory. Its output filename is the
safe basename named by the selected Phase 40 target resource's `source`, with no
separator. That target `source` must equal this root-level PageSet filename or
projection rejects before output. PageSet `id` equals profile `id`; each Page
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
`{id,path,mediaType,sha256}`, resolved from the declared existing media resource;
its path is likewise project-root-relative to that PageSet parent. Each page
references that same sole asset, so the Phase 40 required-asset check succeeds.

All Summary raw data remains source-bound for provenance and currentness.
Existing Visual Page output contains only selected diagram nodes/edges,
catalog-supported relation labels, and the projector-derived eligible emphasis
node. It does not
contain `heading`, `message`, `emphasis`, `navigationLabel`, retained points,
omissions, arbitrary CSS, physical layout, or new tag text. Semantic
tag/structure marking beyond that unchanged Visual Page/renderer contract is
out of scope and is not silently encoded.

Operation provenance retains exact IDs, raw identities, safe paths, locale, and
the media target for profile, Core, Document, Summary, media, catalog, and
binding. It adds no Visual Page fields. Phase 40 alone owns renderer invocation,
physical layout, PDF generation, verification, receipt construction, and review
currentness. The projector outputs only the canonical PageSet and connects it,
with the pre-existing binding, to that route; it writes no PDF, PPTX, renderer
manifest, receipt, catalog, or binding.

## 5. Failure and non-goals

Validation fails before PageSet output or receipt visibility for missing,
unknown, duplicate, stale, malformed, unsafe, or mismatched inputs; a media
target that is not the required existing Phase 40 target; a target source that
does not name the required root-level PageSet file; unresolved labels;
duplicate/missing edge or required-endpoint coverage; unrelated items; a
profile-supplied `emphasisNode`; invalid roles, relation types, endpoints,
directions, pattern compatibility, or parameters; and loss, reversal, aliasing,
or synthesis of Core meaning. Failure preserves prior output and exposes no
fresh receipt.

This design neither changes source schemas, Visual Page schemas, catalog,
binding, renderer, receipt, nor Phase 40/58 behavior; adds layout to Summary;
invents semantics; or publishes/deploys/registers external media.
