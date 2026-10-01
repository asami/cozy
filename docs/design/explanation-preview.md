# Explanation Preview Design

## Purpose

`cozy media explanation preview` is a deterministic, self-contained HTML
projection for reviewing one complete Explanation Plan together with its
explicit presentation Step-to-Page mapping and the selected Visual Pages. It
is a read-only review consumer. Composition, Plan, Projection Map, Visual Page
Set, and their catalogs remain the semantic authorities.

## Command Boundary

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

Every input is an explicitly named direct file. The command neither discovers
inputs nor accepts a Storyboard, PDF, PPTX, presentation route, remote URL, or
Web service. `--save` is one direct `.html` file in an existing direct output
directory and is replaced atomically. Every value option accepts either the
separate `--option <value>` form or the equivalent `--option=<value>` form;
each single-valued option occurs at most once. Repeated `--source` and
`--asset` use the same two forms but retain the existing distinct-identifier
rule. Before replacement, `--save` MUST NOT alias any named direct document,
explicit source or asset binding, or resolved descriptor-relative source or
asset resource. Both normalized same-path aliases and existing filesystem
aliases are rejected while the direct regular-file safety rules remain in
force.

## Validation and Review Model

The command reuses the accepted Explanation, Explanation Plan, Projection Map,
and Visual Page parsers and validators. It validates these joins without
constructing a video Projection:

1. the named Plan is current for the named Composition and catalogs;
2. the named Projection Map is current for that Plan and contains every Plan
   Step once, in Plan order;
3. the named Visual Page Set's typed full Presentation Catalog equals the Plan's
   Presentation Catalog, and its catalog ID/revision agrees with the Plan selector;
4. every mapped Page ID resolves exactly once in that set; and
5. every mapped page preserves the mapped Plan Step's logical value and
   required source and asset provenance; and
6. every required source/asset ID is declared in the Composition and resolves
   to an explicit binding; source descriptor bytes agree with their binding's
   actual bytes, while asset descriptor media types agree with the
   AssetDeclaration and descriptor bytes agree with the explicit asset binding.

The review model retains ordinary document and catalog IDs/revisions, ordered steps, claims, sources,
assets, Logical Pattern nodes and typed Relations, Visual Pattern, and typed
parameters verbatim. It does not infer claims, relations, logical patterns,
visual patterns, or physical layout.

## HTML Projection

The generated UTF-8 document embeds all CSS and schematic inline SVG. It has:

1. a provenance header with renderer profile, Composition ID, Explanation
   Catalog ID/revision, Presentation Catalog ID/revision, Visual Page Set ID,
   Step count, and reviewed Page count;
2. a static explanation-flow overview containing Subject Pattern, Explanation
   Pattern, ordered Steps, and every Step-to-Page mapping in order;
3. one ordered card per mapped Page with its semantic role, claims, Logical
   Pattern, typed Relations, Visual Pattern, and parameters; and
4. static page details for nodes, Relations, sources, assets, parameters, and
   source/asset references. Asset details retain ID, mediaType, and path.

The whole overview remains readable and printable without JavaScript. CSS,
HTML structure, schematic SVG, and optional interaction are renderer details;
they cannot write back to source documents. Header string values use the same
HTML escaping as authored content. Catalog IDs and revisions identify the
declared immutable catalog revisions; no Plan or Projection Map ID is invented.

## Success Report and Regeneration

The deterministic command report uses `cozy.explanation-preview.v1`
whose lines occur in this exact order:

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

The lines are joined by LF. The renderer profile is the exact constant shown
above. Step count comes from the Plan; reviewed Page count is the number of
rendered mapped-Page occurrences, including repeated mappings to one Page and
excluding unmapped Pages. Report data comes from the validated Composition,
catalogs, Visual Page Set, and ordered rendering closure. It has no artifact
digest, output target path, or generated time. A valid visual parameter edit
under unchanged semantic IDs/revisions/counts changes the rendered HTML while
leaving this report unchanged.

Any missing, unsafe, malformed, duplicate, unknown, incompatible,
semantically mismatched, or stale input fails closed with structured diagnostics.
Identical accepted inputs and preview renderer/profile produce byte-identical
HTML. Output aliases of consumed inputs fail before atomic replacement, so the
preview remains read-only and cannot write back to any input. The producer
always renders validated inputs and computes HTML and report before atomic
replacement. It introduces no timestamp/cache/adoption logic and does not
touch inputs. Rejected grammar and input preserve the prior selected output.
The existing `cozy media visual-page preview` command, schema, and semantic
HTML remain available with its current semantic report: `schema`, `version`,
`generator`, `documentId`, `catalogId`, `catalogRevision`, `pageCount`, `html`,
and `status` in order for HTML-only output.

## Phase 71.1 Migration Boundary

Phase 71.1 supersedes this consumer's former artifact hash checks and output.
Typed catalog equality, selector ID/revision, declared resource IDs, mediaType,
and actual descriptor/binding byte agreement establish the explicit joins.
Byte comparison validates agreement between the two supplied resources; it
does not decide generated-output reuse or establish content identities.
The reused Explanation and Visual Page models/codecs still carry upstream
hash requirements pending their unfinished same-Phase migration. They are not
published-integrity exceptions or the accepted final state of Preview; this
bounded consumer neither bypasses their validity checks nor adds hash calls.
Timestamp currentness and explicit adoption remain separate same-Phase work.

## Non-goals

- Storyboard/video validation, rendering, or approval.
- PDF/PPTX generation or inspection.
- Presentation authoring, semantic authority, or write-back editing.
- External hosting, registration, deploy, upload, or publication.
- Coordinates, fonts, colors, CSS selectors, or renderer object IDs in
  Visual Page or presentation semantics.
