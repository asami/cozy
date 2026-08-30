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
3. the named Visual Page Set is current for the named Presentation Catalog and
   has the Plan's logical catalog identity;
4. every mapped Page ID resolves exactly once in that set; and
5. every mapped page preserves the mapped Plan Step's logical value and
   required source and asset provenance; and
6. every required source descriptor's bytes agree with its explicit binding
   and the binding digest agrees with the current SourceDeclaration, while
   every required asset descriptor's media type and digest agree with the
   current AssetDeclaration and its bytes agree with the explicit binding.

The review model retains input identities, ordered steps, claims, sources,
assets, Logical Pattern nodes and typed Relations, Visual Pattern, and typed
parameters verbatim. It does not infer claims, relations, logical patterns,
visual patterns, or physical layout.

## HTML Projection

The generated UTF-8 document embeds all CSS and schematic inline SVG. It has:

1. an identity and currentness header;
2. a static explanation-flow overview containing Subject Pattern, Explanation
   Pattern, ordered Steps, and every Step-to-Page mapping in order;
3. one ordered card per mapped Page with its semantic role, claims, Logical
   Pattern, typed Relations, Visual Pattern, and parameters; and
4. static page details for nodes, Relations, sources, assets, parameters, and
   consumed identities.

The whole overview remains readable and printable without JavaScript. CSS,
HTML structure, schematic SVG, and optional interaction are renderer details;
they are excluded from every semantic identity and cannot write back to source
documents.

## Receipt and Currentness

The deterministic command report is a `cozy.explanation-preview.v1` receipt
whose lines occur in this exact order:

```text
schema: cozy.explanation-preview.v1
version: 1
generator: cozy media explanation preview
rendererProfile: cozy.explanation-preview.renderer.v1
compositionIdentity: <sha256 identity>
planIdentity: <sha256 identity>
projectionMapIdentity: <sha256 identity>
explanationCatalogIdentity: <sha256 identity>
presentationCatalogIdentity: <sha256 identity>
visualPageSetIdentity: <sha256 identity>
htmlIdentity: <sha256 identity>
identity: <sha256 identity>
```

`identity` is SHA-256 over the preceding receipt lines through `htmlIdentity`,
joined by LF and encoded as UTF-8; the `identity` line is excluded. The
renderer profile is the exact constant shown above. The rendered identity
header repeats the consumed identities and renderer profile, but not the HTML
or receipt identity, so the HTML remains a non-self-referential deterministic
input to its receipt.

Any missing, unsafe, malformed, duplicate, unknown, incompatible,
identity-mismatched, or stale input fails closed with structured diagnostics.
Identical accepted inputs and preview renderer/profile produce byte-identical
HTML. Output aliases of consumed inputs fail before atomic replacement, so the
preview remains read-only and cannot write back to any input. The existing
`cozy media visual-page preview` command and schema remain unchanged.

## Non-goals

- Storyboard/video validation, rendering, or approval.
- PDF/PPTX generation or inspection.
- Presentation authoring, semantic authority, or write-back editing.
- External hosting, registration, deploy, upload, or publication.
- Coordinates, fonts, colors, CSS selectors, or renderer object IDs in
  Visual Page or presentation semantics.
