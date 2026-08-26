# Phase 36 Visual Page Contract Foundation

Date: 2026-08-26

Status: `P36-01A` documentation foundation; DONE

## Purpose

This journal records the frozen evidence inputs and scope boundary for the
Phase 36 Visual Page contract foundation. Normative authority is
[`docs/spec/visual-page.md`](../../../spec/visual-page.md) and
[`docs/design/visual-page.md`](../../../design/visual-page.md), not this journal.
This record does not claim Scala/CLI implementation, executable
specifications, renderer work, receipt execution, validation, review, commit,
publication, deployment, migration execution, or external-consumer acceptance.

## Frozen decisions recorded

- One `cozy.visual-page.v1` / version `1` value is the typed authority for one
  screen. Its closed normalized root separates semantic logical graph and
  provenance from selected Visual Pattern/parameters and physical binding.
- The separately resolved
  `cozy.presentation-semantics.catalog.v1` / version `1` catalog is identity-
  bearing. Canonical catalog JSON has root order `schema, version, id,
  revision, relations, logicalPatterns, visualPatterns`; full catalog identity
  is its SHA-256, while logical catalog identity includes only schema/version/
  id/revision plus relations and logicalPatterns. Its initial closed relation
  vocabulary is the five exact `{id, direction: from-to}` entries `next`,
  `causes`, `depends-on`, `enables`, and `maps-to`; its Logical Patterns are
  `sequence`, `causal-chain`, `dependency-map`, and `mapping`; its Visual
  Patterns are `flow-horizontal`, `flow-vertical`, and `mapping-columns`.
- Logical Pattern entries are closed `{id, nodeRoles, relationRules}` values:
  node roles are `{role, min, max}` and relation rules are `{relation,
  fromRoles, toRoles, min, max, topology}`, with sorted role tokens, literal
  inclusive integer cardinalities, and only `linear`, `acyclic`, or `bipartite`
  topology. The frozen constraints are sequence step min=2, max=8 with linear
  `next` min=1, max=7; causal cause/effect min=1, max=7, total min=2, max=8,
  acyclic cause->effect `causes` then `enables`, each min=1, max=16; dependency
  dependency/dependent min=1, max=7, total min=2, max=8, acyclic
  `depends-on` min=1, max=16; and mapping source/target min=1, max=7, total
  min=2, max=8, bipartite `maps-to` min=1, max=16. No catalog field serializes
  a dynamic expression. `linear` is the exact step-node directed connected
  chain with one start, one end, one incoming/outgoing relation per internal
  node, no duplicate endpoint pairs, and exactly step-count minus one edges.
  `acyclic`/`bipartite` prohibit duplicate same-type endpoint pairs and enforce
  all endpoints and stated role/total cardinalities, so observed edge count
  cannot exceed eligible endpoint-pair count despite static max=16. Canonical
  arrays preserve root and relation order; logical patterns are `sequence`,
  `causal-chain`, `dependency-map`, `mapping`; visual patterns are
  `flow-horizontal`, `flow-vertical`, `mapping-columns`; node roles sort by
  role; relation rules by relation, `fromRoles`, then `toRoles`; role and
  compatible-pattern arrays lexicographically; and parameter arrays by name.
  Duplicates reject. No initial form admits extra nodes or relations.
- Visual Pattern entries are closed `{id, compatibleLogicalPatterns,
  parameters}` values. Parameters are `{name, type, required}` with only
  `node-ref`, `boolean`, or `string`, sorted names, no defaults/aliases/
  nullable forms, exact current-page node references, and nonempty trimmed
  strings. Horizontal permits sequence/causal-chain and vertical additionally
  dependency-map with optional emphasis node and relation labels; mapping
  columns permits only mapping with required source/target column titles and
  optional relation labels.
- Canonical JSON supplies `logicalIdentity` and `visualPageIdentity`; restricted
  Markdown, YAML, and JSON are admitted only when lossless. A compatible
  Visual Pattern replacement preserves logical identity, while a Relation or
  provenance change does not. Each asset is exactly `{id, path, mediaType,
  sha256}`, where lowercase SHA-256 validates resolved direct regular bytes.
  Same-path byte changes without an updated digest fail closed; an updated
  digest changes visual identity and stales visual evidence. Logical identity
  excludes assets; visual identity binds declared asset digests and full catalog
  identity.
- Binding is a separate `cozy.visual-page.binding.v1` / version `1` boundary.
  Coordinates, fonts, colors, PowerPoint Shape kinds, and renderer object IDs
  remain in binding or generated evidence, never Visual Page semantics.
- The future presentation route is explicit
  `presentation.contract: visual-page-v1` and expects
  `cozy.presentation.render.v2`; its discriminated object has exactly
  `contract`, `profile`, `catalog`, `binding`, `slideImages`, `montage`,
  `rendererManifest`, `reviewManifest`, `reviewState`, `articlePdf`, and
  `infographic`. The resource `source` is the VisualPageSet input, catalog and
  binding are safe descriptor-relative direct regular non-symlink files, and
  all three reject empty/absolute/traversal/URI-like/control/backslash paths,
  normalization changes, symlink escapes, and missing/nonregular resolution;
  `source` must parse as `cozy.visual-page-set.v1` / integer `1`. Every set
  page shares one catalog `{id, revision}` pair, and exactly one supplied
  resolved catalog must match that pair and its full identity; mixed,
  unresolved, or mismatched catalog resolution fails closed. The one media
  catalog/binding pair thus binds every page deterministically. v2 argv
  replaces `--slide-ir <source>` with `--visual-page-set <source> --catalog
  <catalog> --binding <binding>`. Its canonical manifest fields are
  `schema`, `target`, `profile`, `renderer`, `visualPageSetSha256`,
  `catalogSha256`, `bindingSha256`, `templateSha256`, `pptx`, `slides`, and
  `montage`. The legacy untagged object cannot carry contract/catalog/binding;
  existing receipt v2/review-state v1 shapes remain unchanged. Only explicit
  later implementation may add named visual page/catalog/binding/asset receipt
  inputs and its own verified v2 renderer/review evidence. The existing
  `cozy.slide-ir.v1` / `--slide-ir` /
  `cozy.presentation.render.v1` route is unchanged.
- The future `cozy.video.storyboard.v2` screen can be text or a safe Visual
  Page Set reference. It applies the same safe `source` restrictions as the
  media route. Every VisualPageSet page ID is unique and a v2 visual-page
  `pageId` resolves exactly once; duplicate IDs and zero/multiple matches fail
  closed. V1 remains exactly accepted; only explicit text-screen migration may
  create v2, and a visual-page screen cannot downgrade.
- Legacy Slide IR migration is explicit, digest-checked, complete, bijective,
  diagnostic, and non-inferential. An old slide is not a Logical Pattern.

## Evidence inputs and unchanged owners

The design/spec foundation uses the accepted current contracts as coexistence
inputs: `docs/spec/media-package.md`,
`docs/design/media-package-operation.md`,
`docs/spec/video-storyboard.md`, and
`docs/design/video-storyboard.md`. The documented current owner boundaries are
the legacy presentation route, Phase 30 Storyboard semantics, and current Cozy
receipt/review-state behavior. No source, test, renderer, media descriptor,
template, generated artifact, or external repository was changed by this
foundation.

## Deferred and out of scope

Phase 37 remains deferred for argument/subject patterns, explanation
development, AI selection, and deterministic multi-page/multi-scene expansion.
SmartDox, Textus BoK, SimpleModeling.org, renderer implementations, source or
template migrations, receipt execution, and all external consumer acceptance
remain outside this slice. VIS36-02 through VIS36-06 are NOT STARTED.
