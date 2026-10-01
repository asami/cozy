# Document Project Summary Slide PDF Projection Specification

Status: NORMATIVE; Phase 59 contract reconciled by Phase 71.1 / P710-03-DM-09

This specification freezes strict `cozy.summary-slide-projection.v1` admission
and canonical projection into the accepted Visual Page and Phase 40 summary-slide
PDF routes. It is paired with
[`docs/design/document-project-summary-slide-pdf-projection.md`](../design/document-project-summary-slide-pdf-projection.md).
Existing v1/v2 source and Phase 40 PDF contracts remain unchanged. Visual Page
Revision 1 catalog/binding behavior remains unchanged; this specification admits
only the closed Revision 2 standalone extension. MUST and MUST NOT are
normative.

## 1. Direct profile admission

The profile is one direct regular non-symlink UTF-8 YAML file outside
`content/<locale>/summary.yaml`. Duplicate keys, anchors, aliases, merge keys,
explicit tags, malformed UTF-8, unknown root/mapping fields, missing required
fields, and wrong required-field types MUST reject before a typed profile
exists. Its root is closed and ordered exactly:

```text
schema, version, id, core, document, summary, locale,
media, catalog, binding, pages
```

`schema` MUST equal `cozy.summary-slide-projection.v1`; `version` MUST be
integer `1`; `id` MUST be a nonempty stable ID; and `locale` MUST equal both v2
source locales. Canonical `core`, `document`, and `summary` records are
`{id, path}`. Each MUST contain a typed valid ID and safe project-relative POSIX
path to the corresponding direct regular source. Loaded logical IDs, including
the Summary's Core/Document bindings, MUST match profile records.

Canonical `media` is `{id, path, summarySlidesPdf}`. `id` MUST equal the resolved
`media.yaml` descriptor `knowledge.id`; `path` MUST safely resolve to that
direct regular descriptor; and `summarySlidesPdf` MUST name one descriptor
resource. Canonical `catalog` is `{id, revision, path}` and MUST select one
resolved `cozy.presentation-semantics.catalog.v1` with a positive admitted fixed
revision (1 or 2). Canonical `binding` is `{id, profile, path}` and MUST select
one resolved `cozy.visual-page.binding.v1` with `profile: business`.
Catalog/binding IDs, revisions, business profile, and every selected pattern
MUST agree with the loaded semantic contracts.

These six input-binding objects MUST admit ordinary unknown metadata without
retaining, validating, or re-emitting it, regardless of scalar, object, array,
or null shape. Historical hash properties are ordinary extra metadata; no
hash-specific codec, computation, comparison, or rebinding is permitted here.
Their enclosing values MUST still be mappings and all required fields MUST
remain present and correctly typed. The profile root and page/item/edge/parameter
grammar remain closed; this rule does not admit extra root or mapping fields.

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
reject. Profile and bound-source admission MUST load current bytes and validate
their actual semantics without a raw-byte identity gate. Valid whitespace or
authored localized label changes require no profile rebinding.

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

A relationless mapping is valid only when it selects Revision 2 `standalone`
with `standalone-card`, has exactly one Summary diagram item in role `item`,
has zero edges, no `emphasisItem`, and empty `visualParameters`. That item MUST
not be an endpoint of any declared Summary edge and MUST occur in exactly one
mapping. The projector MUST NOT synthesize a Core Relation or alternative Core
meaning for it. Every edge-bearing mapping MUST retain the existing resolved
relation type, endpoints, direction, and roles and MUST NOT include that
relationless item. A relation, extra item, parameter, emphasis, hybrid
logical/visual pair, or edge mix MUST reject.

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
contain exactly the configured infographic as `{id,path,mediaType}`, resolved
from its declared existing media resource, with its path project-root-relative
to that same PageSet parent. Every page MUST reference that same asset,
satisfying the current Phase 40 required asset check. DM12 removes the material
hash field and computation. Safe direct-file admission, media-type agreement,
and actual-byte agreement with the selected media resource remain required;
current valid material bytes need no declaration digest rebinding.

All Summary data remains bound to its logical source and safe path. Existing
Visual Page output includes only selected diagram nodes/edges, catalog-supported
relation labels, and an eligible projector-derived emphasis node. It MUST NOT encode Summary
`heading`, `message`, `emphasis`, `navigationLabel`, retained points, omissions,
arbitrary CSS, physical layout, or new tag text. Structure/tag marking beyond
the existing Visual Page/renderer contract is out of scope and MUST NOT be
silently encoded.

Projection provenance MUST retain exact logical IDs and safe actual relative
paths for profile, Core, Document, Summary, media, catalog, and binding, plus
locale and selected media target. Each input provenance entry is exactly
`{id, path}`; no artifact hash is calculated, stored, parsed, compared, or emitted
for these entries. It MUST NOT add fields to Visual Page or v2 source schemas.
Phase 40 owns rendering, layout, PDF/PPTX/manifest output, verification,
receipts, and review currentness. Projection MUST output only the
canonical PageSet and MUST NOT write a PDF, PPTX, renderer manifest, receipt,
catalog, or binding.

## 4. Article 9 overview acceptance example

`application-overview` MUST be represented by the following three contiguous
explicit mappings in Summary order:

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
4. If the authored Root Step DiagramItem is explicitly selected once and
   edge-free, a Revision 2 mapping MAY append as
   `overview-application-modeling-standalone`: `standalone` plus
   `standalone-card`; the existing root Step `overview-application-modeling`;
   role `item`; zero relations and zero visual parameters. Omitting that
   selection MUST preserve the three-page result.

Each listed edge MUST retain its declared Summary `forward` direction.
`overview-realization` is the same source item on both dependency pages:
`dependent` for `use-case-realization -> application-foundation` and
`dependency` for `application-conclusion -> use-case-realization`. This is a
permitted page-local reappearance compelled by the distinct edges, not cloning;
every edge still occurs in one mapping. No inverse reading, relation alias,
visual chain, or catalog change is permitted.

The copied Article 9 descriptor currently declares bytes-normalized receipt
inputs for projection profile, Core, Document, Summary, media descriptor,
catalog, and binding. Its existing Phase 40 receipt-currentness scenario remains
an executable description of that unfinished consumer, not the desired final
timestamp contract. Dedicated SAME-Phase migration must remove these local
hashes along with Visual Page canonical/catalog/binding identities and renderer
manifest hashes. Their current contracts remain only until that migration.

Pure projection and coordinator `prepare` MUST accept independently
whitespace-changed valid current inputs with unchanged canonical semantics and
logical provenance, preserving existing PageSet/PDF/manifest/receipt bytes.
Current authored Document label changes MUST appear in the selected projected
node while retaining IDs, order, relations, and provenance. Declared producers,
timestamp-driven generation/currentness, conservative regeneration, and explicit
prebuilt adoption remain unfinished Phase integration obligations under
[file-update-management.md](file-update-management.md); they do not belong in
the pure projector.

## 5. Fail-closed conditions

Before a PageSet is created or a receipt is visible, projection MUST reject
unknown closed-grammar fields, missing required fields, duplicate keys,
malformed, unsafe, or mismatched required profile/source/catalog/binding/media
semantics; invalid locale/target;
unresolved localized labels;
duplicate/missing/out-of-unit edges or required
endpoints; unrelated items; a profile-supplied `emphasisNode`; invalid Core
endpoint/role/relation/direction/pattern/parameter/focus mapping; and loss,
reversal, aliasing, or synthesis of Core meaning. A failure MUST preserve
previous derived output and expose no fresh PageSet, PDF, receipt, or currentness
evidence. X rejects an invalid connection-source grammar; the explicit writer
reports missing output arguments and ordinary filesystem write failures. It
does not promise protection against a misconfigured output overwriting an input.

## Phase 71.1 DM08 boundary reconciliation

Historical DM08 account; its profile/provenance hash statements are superseded
by DM09 below and MUST NOT be read as current admission authority.

Phase 71.1 / P710-03-DM-08 removes only the nested V2 model binding hash
comparisons. `CozySummarySlideProjection` now checks the exact logical Core,
Document, Summary, nested binding IDs, and locale. Its independent profile-byte,
provenance, media, catalog, asset, receipt, and Phase 40 currentness hashes
remain existing unfinished SAME-Phase work; this is neither an integrity
exception nor completion of D02, application currentness, or this Phase.

## Phase 71.1 DM09 boundary reconciliation

Phase 71.1 / P710-03-DM-09 removes the raw-identity layer from all six
profile input bindings and all seven provenance entries. Required logical IDs,
safe paths, locale, catalog revision, business profile, media target, strict
YAML, and mapping semantics remain authority. Ordinary extra binding metadata
cannot affect the complete canonical result or non-hash provenance. Invalid or
missing required semantics still reject before the writer replaces prior bytes.

This Slice does not complete D02/D04/M05, timestamp/adoption integration, Step
or Phase acceptance. DM12 supersedes the material asset fields described by
DM09; Visual Page canonical/catalog/binding identities, renderer and output
evidence hashes, and media receipt/currentness hashes remain unfinished SAME-Phase
layers requiring dedicated migration. Repository-full tests remain
deferred-not-run to Phase 71.4; no successor acceptance is implied.
