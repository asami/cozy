# Document Project Cross-Media Projection

Status: Phase 46.1 normative specification with Phase 71.1 declarations and Phase 71.5 S05 typed semantics

## Input boundary

The cross-media projector MUST accept only an already validated
`cozy.content-core.presentation-semantics.v2` aggregate. It MUST NOT accept
generic renderer maps, page aliases, loose scalar parameters, or an unvalidated
Content Core document.

## Material declarations in Phase 71.1

The typed Projection MUST retain source and asset declarations in stable ID
order. Its canonical JSON MUST serialize each source as exactly `{id}` and
each asset as exactly `{id, mediaType}`, retaining the declared ID and media
type. The projector MUST NOT calculate, parse, compare, or emit material
hashes. Actual safe direct material inputs are admitted upstream; these fields describe
their semantic declarations.

## Slide and scene derivation

For every Structure, the projector MUST create one slide page and one video
scene for every element of its ordered, nonempty `article.visibleText` vector.
Each derived value MUST retain:

- the stable Structure and Story Step identities;
- the Structure's Logical Pattern, nodes, and typed Relations;
- the Plan Step's claims, source references, and asset references; and
- the selected `slides` or `video` Visual Pattern and typed parameters.

The generated slide ID is `slide-<structure-id>-<one-based-ordinal>`; the
generated scene ID is `scene-<structure-id>-<one-based-ordinal>`. Projection
order MUST be Plan Step order, Structure ID, then visible-text ordinal.

Slide-page text and scene caption MUST exactly equal the corresponding visible
text. The projector MUST NOT infer narration, duration, timing, transition,
animation, layout, or production instructions.

## Mapping and coverage

The result MUST expose separate mappings from every Plan Step to its slide IDs
and to its scene IDs. One Structure may map to multiple pages and scenes;
those values share semantic authority rather than duplicating it. Coverage is
also Structure-level: every Structure MUST resolve to at least one slide page
and one storyboard scene independently of any sibling Structure bound to the
same Plan Step.

Projection MUST fail if a Plan Step lacks a Structure, a Structure does not
produce at least one page and scene, or a retained mapping does not resolve to
the exact derived value. An empty-output Structure MUST fail atomically with
`DP-PROJ-001` before a successful partial projection is returned. It MUST NOT
emit an empty mapping or a successful placeholder.

## DM14 catalog proof removal

Projection contains no `presentationCatalogIdentity`,
`presentationLogicalCatalogIdentity`, `presentationCatalogValue`, or
`presentationLogicalCatalogValue`. These deleted historical catalog proofs
are not replaced by copied JSON, canonical strings, fingerprints, or alternate
identity fields. Actual named catalog validation is performed upstream on
current inputs; a valid same-ID/revision definition update is not independently
vetoed by a historical catalog copy.

## Deterministic typed result

Projection contains exactly Content Core id, sorted sources and assets,
StoryFlow, ordered slide pages and storyboard scenes, and their separate Step
mappings. Its canonical JSON is ordinary deterministic serialization of those
real values. Repeated equivalent input produces the same typed result and
serialization; no aggregate digest or output-binding field is carried.

## Later Phase 46.1 stages

This contract supplies typed inputs for confirmation HTML and in-operation coverage. The
HTML and coverage stages MUST consume this value; they may not independently
recreate mappings from an untyped source.

## Phase 71.5 implementation and validation status

S05 implements the current semantic association, typed projection, coverage,
confirmation HTML, and direct scaffold contracts under
`P715-02-S05-PRESENTATION-SEMANTIC-COVERAGE`; its focused validation is pending.
S04's three existing suites completed with 120 successes, zero failures, zero
canceled cases, no warnings, exit 0, and terminal shared-lock release, as recorded
in `p715-02-s04-focused-validation-result.json`. The first 119-success/one-failure
run and the bounded existing MediaReceipt fixture correction remain retained
history. This is implementation progress, not Step or Phase acceptance.

External E715-01 through E715-04 reconciliation and the same full 34-scene JA
ModelHarness VOICEVOX generation, final assembly, audible correct end card, and
player EOF remain open. Repository-full validation is deferred-not-run to
Phase 71.7. Independent Alignment, upstream Explanation hashes, delegated
MediaReceipt, and Core/Description/Confirmation writer removal remain on the
finite frontier; no exception or replacement certificate is introduced.
