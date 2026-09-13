# Document Project Summary Slide PDF Projection Specification

Status: NORMATIVE; Phase 59, Step P590-01, Slice P590-01A

This specification freezes strict `cozy.summary-slide-projection.v1` admission
and canonical projection into the accepted Visual Page and Phase 40 summary-slide
PDF routes. It is paired with
[`docs/design/document-project-summary-slide-pdf-projection.md`](../design/document-project-summary-slide-pdf-projection.md).
Existing v1/v2 source, Visual Page, catalog, binding, and Phase 40 PDF contracts
remain unchanged. MUST and MUST NOT are normative.

## 1. Direct profile admission

The profile is one direct regular non-symlink UTF-8 YAML file outside
`content/<locale>/summary.yaml`. Duplicate keys, anchors, aliases, merge keys,
explicit tags, malformed UTF-8, unknown/missing fields, and wrong types MUST
reject before a typed profile exists. Its root is closed and ordered exactly:

```text
schema, version, id, core, document, summary, locale,
media, catalog, binding, pages
```

`schema` MUST equal `cozy.summary-slide-projection.v1`; `version` MUST be
integer `1`; `id` MUST be a nonempty stable ID; and `locale` MUST equal both v2
source locales. `core`, `document`, and `summary` MUST each be exactly
`{id, identity, path}`. Their identities MUST be lowercase raw-byte
`sha256:<64 hexadecimal>` values and their paths safe project-relative POSIX
paths to the corresponding direct regular source. Loaded IDs and identities,
including the Summary's Core/Document binding, MUST match profile records.

`media` MUST be exactly `{id, identity, path, summarySlidesPdf}`. `id` MUST
equal the resolved `media.yaml` descriptor `knowledge.id`; `identity` MUST be
that descriptor's raw-byte SHA-256 identity; `path` MUST safely resolve to that
direct regular descriptor; and `summarySlidesPdf` MUST name one descriptor
resource. `catalog` MUST be exactly `{id, revision, identity, path}` and select
one resolved `cozy.presentation-semantics.catalog.v1`. `binding` MUST be exactly
`{id, identity, profile, path}`, select one resolved
`cozy.visual-page.binding.v1`, and have `profile: business`. Catalog/binding
IDs, revisions, and identities MUST match each other and every selected pattern.

The named media target MUST be an existing `summary_slides_pdf` resource with
`build: summary-slides-pdf`, `summarySlidesPdf.contract: visual-page-v1`, and
`summarySlidesPdf.profile: business`. Its language MUST equal locale, its
descriptor knowledge ID MUST equal the Core ID, and its configured catalog and
binding references MUST equal the profile records. Its configured infographic
MUST resolve to one existing declared resource and is the sole selected asset.
The projector MUST only admit/use this fixed catalog/binding pair; it MUST NOT
generate, copy, or modify a binding.

Absolute/traversal/URI-like/control-character/backslash paths, normalization
changes, missing/nonregular files, final symlinks, and symlinked ancestors MUST
reject. The profile's own raw identity is retained as provenance only and MUST
NOT become a profile field.

## 2. Closed page mapping and coverage

`pages` MUST be a nonempty ordered array. Each page object MUST contain exactly
`id`, `summaryUnitId`, `logicalPattern`, `visualPattern`, `items`, `edges`, and
`visualParameters`, and MAY contain `emphasisItem`:

```text
id, summaryUnitId, logicalPattern, visualPattern,
items, edges, [emphasisItem], visualParameters
```

`id` MUST be unique and stable; `summaryUnitId` MUST resolve once in the v2
Summary; and the selected logical/visual patterns MUST be catalog IDs with
declared compatibility. `items` MUST be a nonempty ordered array of unique
exact `{diagramItemId, role}` values. `edges` MUST be an ordered array of unique
exact `{diagramEdgeId}` values. Every value MUST resolve once within the selected
Summary unit's declared diagram. The resulting Visual Page role, relation type,
endpoints, and direction MUST resolve exactly from the selected Summary diagram
edge and its existing Core target. There is no profile field for alternate
relation, endpoint, direction, or reading; presentation inversion is forbidden.

For each Summary unit selected by at least one mapping, every declared diagram
edge of that unit MUST occur in exactly one of that unit's contiguous mappings.
Each mapping MUST list the exact selected edge's endpoint items. A diagram item
MAY occur in more than one contiguous mapping only when it is a shared endpoint
of distinct selected edges; every occurrence MUST retain the same
`diagramItemId` and Core reference, never a clone or invented element. Its
catalog role is page-local and MAY differ only as compelled by the selected
existing logical pattern and relation rule; it MUST NOT change the item's Core
meaning. An item with no selected edge MUST occur exactly once, and a mapping
MUST NOT include an unrelated item. A selected unit MUST map all declared edges
and their endpoint items under these rules; a unit may have no mapping only when
it is not selected for slide projection. Duplicate/missing edges, missing
endpoints, unrelated items, out-of-unit values, or inventions MUST reject. Page
order MUST preserve Summary-unit order followed by the source order of each
unit's explicit mappings, with no interleaving between units. There is no
implicit pagination, overflow split, fitting, or renderer-side selection.

`emphasisItem` is the sole permitted semantic emphasis authority. If present,
it MUST equal the selected unit's `focusItem` and occur in the same page's
items. Only the projector MAY derive the existing catalog `emphasisNode`
parameter, and only when that `emphasisItem` is a Node and the selected visual
pattern supports it. If `emphasisItem` is absent, is not a Node, or the pattern
does not support it, no `emphasisNode` is emitted. A Step focus MUST NOT gain a
synthetic Node. A profile-supplied `emphasisNode` in `visualParameters` MUST
reject. Apart from that reserved parameter, `visualParameters` MUST contain each
required selected-catalog parameter once and optional parameters only when
explicit; aliases, defaults, nulls, renderer IDs, coordinates, CSS, fonts,
sizes, PDF settings, and physical slots MUST reject.

## 3. Canonical projection

The projector MUST load and validate all bound sources, mappings, catalog,
binding, and media target, and return one validated canonical
`cozy.visual-page-set.v1` with provenance without creating, replacing, or
deleting files. A separate writer takes an already validated projection and a
required explicit `pageSetOutput` path. The converter/writer (A) MUST NOT read
the target resource's `source`, infer its output from that field, or independently
check agreement with the PDF consumer's input configuration.

The coordinator (X), `CozySummarySlidePdf`, reads the selected `media.yaml`
resource's `source` and resolves the connection once against the media descriptor
root. It passes that same path to A as its output and to the existing Phase 40
PDF route (B) as its input. The connection retains the existing root-level,
normalized project-relative PageSet source grammar so PageSet source and asset
references keep their existing media-root base. The PageSet need not exist before
A generates it. B MUST read the passed input path rather than independently
choosing a different input from the resource's raw configuration.

A MUST NOT enumerate input/downstream output paths to protect its destination
or perform special collision, ancestor-overlap, same-file, case-alias, hard-link,
or symlink destination checks. X MUST NOT reintroduce those withdrawn checks.
The writer retains ordinary required-argument and filesystem write-error
handling and atomic replacement of derived bytes. A materially incorrect output
configuration is not guaranteed safe by this operation. These output-policy
changes do not weaken input admission, DSL semantic validation, or the existing
Phase 40 renderer's own validation/receipt/currentness contracts.

PageSet `id` MUST equal profile `id`, page order MUST equal
mappings order, and every Page `id` MUST equal mapping `id`, `knowledge` MUST
equal Core `id`, and `language` MUST equal locale. The projector MUST use—not
generate—the pre-existing selected binding when connecting this PageSet to
Phase 40.

Each Visual Page node `id` MUST equal its `diagramItemId`; its label MUST be the
existing source-defined localized label resolved for that bound Core reference
from admitted v2 Document/Summary selection. If no such label exists, projection
MUST reject. Each relation `id` MUST equal its `diagramEdgeId`; its type, `from`,
`to`, and source references MUST exactly resolve from the bound Summary diagram
edge and existing Core target.

Each page's sources MUST be exactly the deterministic ordered bindings for
`core`, `document`, `summary`, `profile`, and `media`, using their bound safe
paths project-root-relative to the PageSet parent. Each page's assets MUST
contain exactly the configured infographic as `{id,path,mediaType,sha256}`,
resolved from its declared existing media resource, with its path
project-root-relative to that same PageSet parent. Every page MUST reference that
same asset, satisfying Phase 40's required asset check.

All Summary raw data remains source-bound for provenance/currentness. Existing
Visual Page output includes only selected diagram nodes/edges, catalog-supported
relation labels, and an eligible projector-derived emphasis node. It MUST NOT encode Summary
`heading`, `message`, `emphasis`, `navigationLabel`, retained points, omissions,
arbitrary CSS, physical layout, or new tag text. Structure/tag marking beyond
the existing Visual Page/renderer contract is out of scope and MUST NOT be
silently encoded.

Provenance/receipt input evidence MUST retain exact IDs, raw identities, safe
paths, locale, and selected media target for profile, Core, Document, Summary,
media, catalog, and binding. It MUST NOT add fields to Visual Page or v2 source
schemas. Phase 40 alone owns rendering, layout, PDF/PPTX/manifest output,
verification, receipts, and review currentness. Projection MUST output only the
canonical PageSet and MUST NOT write a PDF, PPTX, renderer manifest, receipt,
catalog, or binding.

## 4. Article 9 overview acceptance example

`application-overview` MUST be represented by three contiguous explicit mappings
in Summary order:

1. `overview-domain-model`, `overview-application-model`, and
   `overview-domain-to-application`: `mapping` plus a compatible mapping visual
   pattern; `root-domain-model` -> `root-application-model`; `maps-to`.
2. `overview-realization`, `overview-foundation`, and
   `overview-realization-to-foundation`: `dependency-map` plus `flow-vertical`;
   `use-case-realization` -> `application-foundation`; `depends-on`; roles
   `dependent` -> `dependency`.
3. `overview-conclusion`, `overview-realization`, and
   `overview-conclusion-to-realization`: `dependency-map` plus `flow-vertical`;
   `application-conclusion` -> `use-case-realization`; `depends-on`; roles
   `dependent` -> `dependency`.

Each listed edge MUST retain its declared Summary `forward` direction.
`overview-realization` is the same source item on both dependency pages:
`dependent` for `use-case-realization -> application-foundation` and
`dependency` for `application-conclusion -> use-case-realization`. This is a
permitted page-local reappearance compelled by the distinct edges, not cloning;
every edge still occurs in one mapping. No inverse reading, relation alias,
visual chain, or catalog change is permitted.

## 5. Fail-closed conditions

Before a PageSet is created or a receipt is visible, projection MUST reject
unknown, missing, duplicate, stale, malformed, unsafe, or mismatched profile,
source, catalog, binding, or media fields/identities; invalid locale/target;
unresolved localized labels;
duplicate/missing/out-of-unit edges or required
endpoints; unrelated items; a profile-supplied `emphasisNode`; invalid Core
endpoint/role/relation/direction/pattern/parameter/focus mapping; and loss,
reversal, aliasing, or synthesis of Core meaning. A failure MUST preserve
previous derived output and expose no fresh PageSet, PDF, receipt, or currentness
evidence. X rejects an invalid connection-source grammar; the explicit writer
reports missing output arguments and ordinary filesystem write failures. It
does not promise protection against a misconfigured output overwriting an input.
