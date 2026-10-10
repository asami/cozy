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
directory and is written directly. Every value option accepts either the
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

1. the named Plan preserves the selected Composition expansion and catalogs;
2. the named Projection Map agrees with the catalog selectors and contains every Plan
   Step once, in Plan order;
3. the named Visual Page Set parses against the explicit Presentation Catalog
   and its catalog ID/revision agrees with the Plan selector;
4. every mapped Page ID resolves exactly once in that set; and
5. every mapped page preserves the mapped Plan Step's logical value and
   required source and asset provenance; and
6. every required source/asset ID is declared in the Composition and resolves
   to an explicit safe direct binding and a safe descriptor-relative resource;
   asset descriptor media types agree with the AssetDeclaration.

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
declared catalog revisions; no Plan or Projection Map ID is invented.

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
HTML. Output aliases of consumed inputs fail before writing, so the
preview remains read-only and cannot write back to any input. The producer
always renders validated inputs and computes HTML and report before the selected direct
write. It introduces no timestamp/cache/adoption logic and does not
touch inputs. Validation or rendering errors before writing preserve the prior selected output.
Ordinary write failures may leave partial output; regeneration is recovery.
The existing `cozy media visual-page preview` command, schema, and semantic
HTML remain available with its current semantic report: `schema`, `version`,
`generator`, `documentId`, `catalogId`, `catalogRevision`, `pageCount`, `html`,
and `status` in order for HTML-only output.

## Phase 71.5 bounded migration

S07 removes Preview's byte-copy agreement and redundant whole-catalog equality
certificate, and reconciles the Explanation/Projection models and direct writer.
Named catalog ID/revision, typed graphs, required declaration IDs, asset media
types, direct descriptor/binding resolution, actual input/output alias ownership,
HTML escaping, and the independent success report facts remain required.
On 2026-10-08 S07's four existing focused suites completed with 41 successes,
zero failures, canceled, ignored, or pending cases, no warnings, SBT/wrapper
exit 0, and terminal shared serial-lock release. Independent review and Step/Phase
acceptance remain pending. S08 implements the bounded VisualPage conversion and direct semantic Preview
writers. On 2026-10-08 its two existing focused suites completed with 14 successes,
zero failures, canceled, ignored, or pending cases, zero warnings/errors,
SBT/wrapper exit 0, and terminal shared serial-lock release. S09 implements bounded
direct Article/Summary PDF generation and immediate build-input capture removal.
On 2026-10-08 its six existing focused suites completed with 114 successes,
zero failed/canceled/ignored/pending cases, zero aborted suites, zero
warnings/errors, SBT/wrapper exit 0, and terminal shared serial-lock release.
S10 removes Presentation copied-image byte/multiset checks and shared acceptance
staging/backup/rollback, retaining initial asset/target ownership and actual
structural semantics with ordered direct review-state then receipt writes.
On 2026-10-08 its six existing focused suites completed with 113 successes,
zero failed/canceled/ignored/pending cases, zero aborted suites, zero SBT
warnings/errors, SBT/wrapper exit 0, and terminal shared serial-lock release.
Five hostile-XML/DOCTYPE diagnostics are intentional existing negative-fixture
output, not SBT compilation or test failures.
S11 implements direct PresentationMigration and CrossReview output writes and
direct creation of the absent article source package. Its five-suite focused
validation completed on 2026-10-08 with 58 successes, zero
failed/canceled/ignored/pending cases, zero aborted suites, zero SBT
warnings/errors, SBT/wrapper exit 0, and terminal shared serial-lock release.
S11 emitted five intentional hostile-XML/DOCTYPE negative-fixture diagnostics,
separate from SBT compilation or test failures. S12's current JA native route is
planned/not-run; generation, assembly, native inspection, browser EOF and current
listening remain OPEN ([journal](../journal/2026/10/2026-10-08-phase-71.5-ja-main-route.md)).
Remaining Receipt publication/prepared-input
observations and finite publication/runtime/CAR groups, independent
review/Step/Phase acceptance, the same full 34-scene native route, and external
E715-01..04 consumer reconciliation remain pending without an exception or
whole-Phase closure claim.

## Non-goals

- Storyboard/video validation, rendering, or approval.
- PDF/PPTX generation or inspection.
- Presentation authoring, semantic authority, or write-back editing.
- External hosting, registration, deploy, upload, or publication.
- Coordinates, fonts, colors, CSS selectors, or renderer object IDs in
  Visual Page or presentation semantics.
