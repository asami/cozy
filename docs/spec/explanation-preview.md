# Explanation Preview Specification

## Scope

This specification defines the `cozy media explanation preview` command and
its generated-review receipt. It consumes authoritative Explanation and Visual
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
or positional options fail deterministically.

## Required Input Agreement

Before writing output, Cozy MUST validate all of the following:

- Composition, Plan, Explanation Catalog, and Presentation Catalog are current
  under the accepted Explanation contracts.
- Projection Map is current for the named Composition, Plan, and catalogs;
  its presentation mappings contain every Plan Step exactly once in Plan
  order, and every mapping has at least one Page ID.
- Visual Page Set is `cozy.visual-page-set.v1`, is current for the named full
  Presentation Catalog, and has the logical catalog identity selected by the
  Plan.
- Every mapped Page ID exists exactly once; a mapped Page's Logical Pattern is
  exactly the Plan Step's Logical Pattern; and source and asset provenance
  required by the Step are present on that Page.

Missing, duplicate, unsafe, malformed, unknown, incompatible,
identity-mismatched, or stale input MUST fail before output replacement.

## HTML Requirements

The output MUST be one UTF-8, self-contained HTML document. It MUST contain
the identity/currentness header, the full ordered explanation flow and
Step-to-Page mapping, and every mapped Page's semantic role, claims, Logical
Pattern, typed Relations, Visual Pattern, typed parameters, sources, assets,
and consumed identities. Static HTML MUST retain that information without
JavaScript. The page MUST use no CDN, remote asset, external font, external
script, or Web service.

The output MUST be written atomically. Identical accepted inputs and preview
renderer/profile MUST yield byte-identical output.

## Receipt

The success report MUST declare, in this exact order:

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

`identity` MUST be SHA-256 over every preceding receipt line through
`htmlIdentity`, joined by LF and UTF-8 encoded; it MUST exclude the `identity`
line itself. Generated
HTML is review evidence only and is never an input to Composition, Plan,
Projection Map, Visual Page Set, catalog, PDF, PPTX, or video generation.

## Compatibility

`cozy media visual-page preview` retains its current grammar, schemas, output,
and behavior. This command is an additional multi-page explanation-aware
consumer and does not replace it.
