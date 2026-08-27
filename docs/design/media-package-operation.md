# Media Package Operation

## Boundary

Codex skills own semantic transformation: thesis extraction, bilingual writing, localization, dialogue design, semantic comparison, and visual review. Cozy owns deterministic production: path resolution, strict schema/asset validation, profile/template resolution, explicit-argv renderer launch, resource conversion, content-identity planning, structural verification, publication projection, and provenance hashes.

`cozy media` is an orchestration layer above `cozy video`. It does not duplicate narration or rendering semantics.

## BoK Model

A SmartDox article is one representation of a `documentmodel:KnowledgeUnit`. Images and videos are additional representations associated with the same unit. A publication profile projects those representations into SimpleModeling.org, YouTube metadata, an artifact archive, or another BoK distribution.

## Portability

Media descriptors contain relative package paths and logical profile names. User- or machine-specific roots are supplied by repository configuration or environment variables. This keeps the package reusable outside SimpleModeling.org.

Cozy receipt v2 is the ownership boundary for deterministic acceptance. Cozy captures declared and automatic input identities, producer identity, selected operation context, validated output hashes, and optional presentation subordinate artifacts after structural verification. It owns receipt serialization, target-entry merge, current-evidence admission, and pre-destination-write input revalidation. Acceptance prepares review-state and receipt documents first, installs state documents before receipt, and rolls back in-process installation failures; the receipt is the final visibility record.

Presentation is an adapter boundary: Cozy owns semantic-IR validation and invokes an approved external renderer by a configured argv vector, but never designs a layout or interpolates a shell command. The only presentation profile is `business`. The renderer manifest is untrusted evidence until Cozy checks collision-free descriptor-relative paths, IR/template/PPTX hashes, secure relationship-driven OOXML structure/text/per-slide media, and PNG evidence. Cozy reconstructs the deterministic cross-artifact review manifest exactly before recording only the explicit `article` or `slide-ir` alignment decision. A build can refresh current evidence but cannot alter the last approved semantic alignment; changed inputs or artifacts make the state stale. This keeps PPTX a distribution product rather than a semantic authority.

## Visual Page presentation coexistence

The accepted presentation adapter remains the legacy
`cozy.slide-ir.v1` / `--slide-ir` / `cozy.presentation.render.v1` path. Its
existing untagged closed `presentation` object remains exactly the accepted
`profile`, `slideImages`, `montage`, `rendererManifest`, `reviewManifest`,
`reviewState`, `articlePdf`, and `infographic` shape. It cannot contain
`contract`, `catalog`, or `binding`.

The implemented Visual Page route described in
[`docs/spec/visual-page.md`](../spec/visual-page.md) is instead an explicit
parallel discriminated `presentation` object with exact keys `contract`,
`profile`, `catalog`, `binding`, `slideImages`, `montage`, `rendererManifest`,
`reviewManifest`, `reviewState`, `articlePdf`, and `infographic`. Its contract
is exactly `visual-page-v1`; the resource's existing required `source` is the
VisualPageSet input; and `source`, `catalog`, and `binding` are safe descriptor-
relative direct regular non-symlink files under the descriptor root. They
reject empty, absolute, traversal, URI-like, control-character, and backslash
paths; normalization changes; symlink escapes; and missing or nonregular
resolved files. `source` must parse as exactly `cozy.visual-page-set.v1` with
integer version `1`. Every set page declares one shared catalog `{id,
revision}` pair; resolution accepts exactly one supplied resolved catalog
matching that pair and its full identity, and a mixed pair, unresolved catalog,
or mismatch fails closed. The one media-route catalog/binding pair therefore
binds every page deterministically. All legacy artifact/dependency fields
retain their existing meanings, and profile retains its exact template/renderer
shape. This variant cannot take an untagged legacy shape or infer `--slide-ir`.

Its v2 renderer argv is the fixed legacy argv with `--slide-ir <source>`
replaced by `--visual-page-set <source> --catalog <catalog> --binding
<binding>`. Its `cozy.presentation.render.v2` manifest has exact canonical
top-level fields `schema`, `target`, `profile`, `renderer`,
`visualPageSetSha256`, `catalogSha256`, `bindingSha256`, `templateSha256`,
`pptx`, `slides`, and `montage`. `visualPageSetSha256`, `catalogSha256`, and
`bindingSha256` are canonical semantic 64-hex identities rather than raw input
bytes. Slides follow VisualPageSet page order and use exactly `id`, `path`,
`sha256`, `pptxSha256`, and page asset-ID-ordered `{id,sha256}` evidence.
Cozy verifies renderer output, OOXML slide order/media linkage, and exact
deterministic `cozy.media.presentation-review.v2` reconstruction; neither
renderer nor review evidence approves itself.

Existing `cozy.media.receipt.v2` and `cozy.media.review-state.v1` schema shapes
do not change. The route requires explicit receipt inputs for the VisualPageSet
and catalog as structured documents plus binding and template bytes; strict
Visual Page loading and v2 evidence prove assets without adding P36-05
cross-media receipt semantics. This does not transfer review-state acceptance
or semantic authority to generated evidence. Cozy owns validation, identity,
orchestration, and stale-output evidence; the external renderer owns runtime
rendering and external consumers own acceptance.
