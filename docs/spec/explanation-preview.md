# Explanation Preview Specification

## Scope

This specification defines the `cozy media explanation preview` command and
its generated-review success report. It consumes authoritative Explanation and Visual
Page documents but does not define a new semantic model.

## Invocation

```text
cozy media explanation preview <plan> \
  --composition <composition> \
  --projection-map <projection-map> \
  --explanation-catalog <explanation-catalog> \
  --presentation-catalog <presentation-catalog> \
  --visual-page-set <visual-page-set> \
  --save <review.html> \
  [--source <id>=<file>] [--asset <id>=<file>]
```

`<plan>` precedes all options. Each required option occurs exactly once.
Every value option accepts both `--option <value>` and `--option=<value>`.
`--source` and `--asset` follow the existing Explanation direct-binding
contract and may repeat only for distinct declared identifiers. `--save` is
required and names a direct `.html` file. Unsupported, duplicated, valueless,
or positional options fail deterministically. `--save` MUST NOT alias any
named direct document, explicit source or asset binding, or resolved mapped-Page
descriptor resource. Normalized same-path aliases and existing filesystem
aliases MUST be rejected before output replacement.

## Required Input Agreement

Before writing output, Cozy MUST validate all of the following:

- Composition, Plan, Explanation Catalog, and Presentation Catalog are current
  under the accepted Explanation contracts.
- Projection Map is current for the named Composition, Plan, and catalogs;
  its presentation mappings contain every Plan Step exactly once in Plan
  order, and every mapping has at least one Page ID.
- Visual Page Set is `cozy.visual-page-set.v1`; its typed Presentation Catalog
  equals the Plan's full Presentation Catalog, and its catalog ID and revision
  equal the Plan's presentation catalog selector.
- Every mapped Page ID exists exactly once; a mapped Page's Logical Pattern is
  exactly the Plan Step's Logical Pattern; and source and asset provenance
  required by the Step are present on that Page.
- Every required source ID is declared in the Composition and resolves to an
  explicit source binding; its descriptor-relative resource bytes equal that
  binding's actual bytes.
- Every required asset ID is declared in the Composition and resolves to an
  explicit asset binding; its descriptor mediaType equals the AssetDeclaration
  mediaType, and its descriptor-relative resource bytes equal the binding's
  actual bytes.

Missing, duplicate, unsafe, malformed, unknown, incompatible,
semantically mismatched, or stale input MUST fail before output replacement.

## HTML Requirements

The output MUST be one UTF-8, self-contained HTML document. It MUST contain
the provenance header, the full ordered explanation flow and
Step-to-Page mapping, and every mapped Page's semantic role, claims, Logical
Pattern, typed Relations, Visual Pattern, typed parameters, sources, assets,
and source/asset references. The header MUST show the renderer profile,
Composition ID, Explanation Catalog ID and revision, Presentation Catalog ID
and revision, Visual Page Set ID, Step count, and reviewed Page count. Catalog
IDs and revisions refer to the declared immutable catalog revisions. String
values MUST be HTML-escaped. Asset details contain ID, mediaType, and path.
Static HTML MUST retain that information without
JavaScript. The page MUST use no CDN, remote asset, external font, external
script, or Web service.

The output MUST be written atomically. Identical accepted inputs and preview
renderer/profile MUST yield byte-identical output.

## Success Report

The success report MUST declare, in this exact order:

```text
schema: cozy.explanation-preview.v1
version: 1
generator: cozy media explanation preview
rendererProfile: cozy.explanation-preview.renderer.v1
compositionId: <Composition ID>
explanationCatalogId: <Explanation Catalog ID>
explanationCatalogRevision: <Explanation Catalog revision>
presentationCatalogId: <Presentation Catalog ID>
presentationCatalogRevision: <Presentation Catalog revision>
visualPageSetId: <Visual Page Set ID>
stepCount: <Plan Step count>
pageCount: <rendered mapped-Page occurrence count>
html: generated
status: generated
```

The fields MUST be joined by LF in the order above. `pageCount` counts rendered
mapped-Page occurrences, including a Page reviewed for more than one Step;
unmapped Pages do not contribute. The report MUST contain no output target
path, generated time, or artifact digest. It remains identical when a valid
presentation change preserves the reported semantic IDs, revisions, and counts,
even though the actual HTML bytes change. The command always renders the
validated current inputs and computes HTML and report before atomic output
replacement. Rejected input, grammar, or output aliases preserve the prior
selected output. Generated HTML is review evidence only and is never an input to Composition, Plan,
Projection Map, Visual Page Set, catalog, PDF, PPTX, or video generation.

## Compatibility

`cozy media visual-page preview` retains its current grammar, schemas, semantic
HTML, and current success report with `schema`, `version`, `generator`,
`documentId`, `catalogId`, `catalogRevision`, `pageCount`, `html`, and `status`
in order for HTML-only output. This command is an additional multi-page
explanation-aware consumer and does not replace it.

## Phase 71.1 Migration Status

Phase 71.1 supersedes Preview-owned artifact hash checks, calculations, and
output fields. Catalog joins use typed semantic equality and selector ID/revision;
explicit resource agreement uses declared IDs, media types, and actual bytes.
The reused Explanation and Visual Page loaders still require their upstream
model/codec hashes during this bounded migration. Their removal is unfinished
same-Phase work, not a published-integrity exception or an accepted final state
of Preview. Preview neither adds upstream hash calls nor bypasses those loaders.
Timestamp currentness and explicit prebuilt adoption remain separate same-Phase
obligations; this producer does not cache, adopt, touch, or write back to inputs.
