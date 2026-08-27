# Cozy Video Storyboard Design

Status: NORMATIVE DESIGN; Phase 30 v1 remains accepted; Phase 36 `VIS36-04`
and `VIS36-05` are DONE, while Phase-level P36-06 closure work remains.

`P36-04-DEC-001` is consumed. Focused validation invocation
`90284-20260827T085957Z` (`testOnly cozy.video.CozyVideoStoryboardSpec`) reported
16 succeeded, 0 failed/aborted, SBT/wrapper 0, and the lock released.
Independent `P36-04-REREVIEW-002` is PASS and `CB-P36-04-RR-001` is resolved.
P36-04 and P36-05 are accepted as complete. P36-06 has representative
Cross-media evidence but remains open for final Phase validation/review. No
push, publish, or external-consumer acceptance is claimed; Phase 37 remains
NOT STARTED.

This design defines the architecture, responsibility boundaries, and stable
invariants for `cozy.video.storyboard.v1`. The functional contract is
[`docs/spec/video-storyboard.md`](../spec/video-storyboard.md). This
documentation-only foundation does not claim implementation, executable
specifications, validation, review, commit, or completion.

## 1. Design intent

Cozy needs one content identity across human review, external interchange,
optional visual inspection, confirmation rendering, and final rendering. The
design therefore treats Storyboard content as a typed semantic value and treats
Markdown, JSON, review evidence, build hand-offs, and caches as projections or
derived records around that value.

The delivery shape is one explicitly authorized Phase 30 with internal Steps
`P30-00` through `P30-03`. This design does not create or require child Phases
30.1, 30.2, or 30.3, does not open a successor Phase, and does not broaden the
Phase 30 product boundary. The Phase remains incomplete until its internal
ledger and ordinary closure gates converge.

## 2. Responsibility ownership

### 2.1 Cozy owns

Cozy owns the following responsibilities for the Storyboard boundary:

- the typed `Storyboard`, `Scene`, `Screen`, `ProductionInsert`, and
  `PronunciationNote` semantic model;
- strict Markdown and JSON parsers and their source-location diagnostics;
- normalization, canonical JSON serialization, and semantic identity;
- schema/version, identity/order, speaker/role, timing, field, and safe
  path/reference validation;
- explicit legacy-dialogue adapter selection and migration diagnostics;
- normalized content-review and optional visual-story review evidence;
- approval identity checks and stale-input rejection before build;
- confirmation/final build gating and their distinct lifecycle/output records;
- artifact identity calculation, deterministic invalidation, and cache metadata;
  and
- generated hand-off, manifest, evidence, and cache data under
  `target/cozy-video`.

`target/cozy-video` is generated data only. It is never a source of authored
Storyboard meaning and must not be used to repair, replace, or silently select
a source file.

### 2.2 Production configuration remains separate

`video/video.yaml` is a separate production-configuration boundary. It owns
renderer and effects configuration, narration provider and voice selection,
credits, project-owned asset policy, and the selected Storyboard input. It does
not duplicate the Storyboard’s scene narration, captions, screen content,
timing, or direction. Storyboard conversion MUST NOT rewrite `video.yaml`, and
`video.yaml` MUST NOT become a second content source.

### 2.3 External responsibility

Dox/PPTX/SmartDox/Textus consumers may consume normalized Storyboard evidence
or an explicit hand-off record. They remain external owners of presentation
generation, publication, and consumer/runtime acceptance. Cozy does not author
or validate an external Dox/PPTX artifact in this Phase, mutate an external
repository, or claim external runtime acceptance.

## 3. Semantic pipeline and identity

The architecture has one directional pipeline:

```text
storyboard.md / storyboard.json
        -> strict parser
        -> typed Storyboard
        -> common validation
        -> canonical normalization and identity
        -> approval/review evidence
        -> confirmation or final build gate
        -> target/cozy-video derived artifacts and cache
```

The Markdown parser and JSON parser MUST converge before any downstream
behavior. No renderer, narration provider, review generator, or cache may
branch on the source representation. A source path is provenance/diagnostic
context only; it is not part of the Storyboard semantic identity.

The canonical JSON field order, array order, normalized decimal timing, and
UTF-8 encoding defined by the specification form the identity input. The
`sha256:` identity therefore changes when any semantic field, scene order,
pronunciation note, direction, or admitted reference changes, and remains
unchanged when only Markdown formatting or JSON key order changes.

## 4. Representation responsibilities

### 4.1 Restricted Markdown

`storyboard.md` is the human authoring and review representation. Its grammar
is intentionally narrow: one title, exact root metadata, repeated `## scene`
blocks, fixed field order, JSON-string scalar lines, typed inline JSON arrays,
and indented literal text blocks. There are no arbitrary Markdown paragraphs,
links, tables, extension keys, or embedded executable content.

The restriction makes a human-readable document deterministic without making
Markdown a second semantic language. Formatting normalization may change
indentation or quoting, but the parser preserves every defined string, empty
value, array item, and order. Malformed or additional Markdown is rejected
before a typed value is produced.

### 4.2 Canonical JSON

`storyboard.json` is the equivalent machine-interchange representation. It is
canonical only after parsing and normalization; input object key order is not
semantic. Canonical output emits every required field, including empty arrays
and strings, in the specification’s field order. JSON has no untyped extension
map in v1. Unknown keys are rejected because they cannot be preserved by the
typed model.

Markdown-to-JSON and JSON-to-Markdown conversion are both semantic and
lossless. The canonical JSON identity is the common comparison point; a
conversion MUST validate and normalize before writing output, and a failed
conversion MUST NOT leave a claimed accepted Storyboard artifact.

## 5. Validation and safe input boundaries

Validation is a shared service called by validation, inspect, convert, review
evidence, and build gating. It rejects unsupported schema/version, missing or
duplicate fields, duplicate or out-of-order scene identities, invalid
speaker/role values, malformed timing, unsafe references, duplicate insert
identities, malformed representation syntax, and unknown information that
cannot be preserved.

References are safe project-relative POSIX paths. Cozy validates their lexical
form and normalized containment before evidence or build code can use them.
Existence is a separate resource/evidence check; a missing but safe reference
is not permission to accept an absolute, escaping, URI-like, or symlink-
substituted path. Diagnostics retain a field path and source location so a
caller can correct the authored document without guessing which semantic
value was rejected.

## 6. Approval and optional visual-story evidence

The normal review path approves the normalized Storyboard identity. Optional
image-backed visual-story evidence is a derived projection for cases where
PNG frames, diagrams, layout, or image selection need separate inspection; it
is not a second source and is not mandatory for ordinary Storyboard or
confirmation-video review.

When optional visual-story evidence is requested, its evidence identity MUST
record at least:

- the approved Storyboard identity;
- each admitted diagram and asset reference identity;
- the visual-story generator/schema identity; and
- the evidence output identity.

Evidence is stale when any recorded input identity differs, an admitted input
is missing, or the Storyboard is no longer the approved identity. Cozy MUST
reject a confirmation or final build that relies on stale visual evidence and
MUST require the optional review to be refreshed. If no optional visual review
was requested, the ordinary Storyboard approval remains sufficient for the
non-visual review path.

Dox/PPTX hand-off data may carry these normalized identities and evidence
locations. Cozy does not decide whether an external consumer accepts the
result.

### 6.1 P30-02 projection boundary

P30-02 uses a root `storyboardReview` declaration in the existing video
project only to select an already approved Storyboard and, when requested, a
bounded visual-input subset. `source` and `approvedIdentity` prove the normal
content gate. The optional `visualStory` declaration supplies a deterministic
derived evidence directory, an ordered subset of declared Storyboard
references, and a human-recorded `approvedEvidenceIdentity` after visual
inspection. It does not add renderer, narration, character, credit, or
PPTX-generation policy to Storyboard content.

The evidence generator loads the current typed Storyboard, verifies the normal
approval identity, validates every requested visual input as a direct regular
file below the project root, copies only those admitted inputs into the derived
package, and writes canonical review and hand-off JSON. The package identity
is a digest of its canonical payload; no artifact identifies itself by a
filesystem timestamp or an external consumer result. Revalidation compares the
current Storyboard and input identities with the package and optional human
approval record. A requested but stale or unapproved visual package is a
fail-closed build-gate input. An absent optional declaration remains the normal
non-visual path.

The hand-off JSON is deliberately sufficient for Dox/PPTX consumers to locate
and compare the normalized evidence but insufficient to claim that a deck was
generated or accepted. P30-03 owns actual Storyboard-to-build integration,
confirmation/final modes, and the call site that applies this gate to those
new modes; P30-02 applies the same validator to the existing build path when
the declaration is present so a configured stale visual review cannot be
silently bypassed.

### 6.2 P30-03 execution and confirmation boundary

`parts[].storyboard` is a source-selection adapter, not a second rendering
model. Its optional `storyboardSection` binds the part to the identically
named section of that one normalized source; omission deliberately selects the
whole Storyboard. The adapter loads the same normalized typed `Storyboard`
used by review and projects the selected scenes into a generated execution handoff under
`target/cozy-video/storyboard/<part-id>/`. Existing narration and renderer
adapters consume that projection; a legacy `parts[].script` continues through
its existing path and is never silently selected for a Storyboard part.

The confirmation build produces a deterministic mode manifest and a separate
generated confirmation video. A human records acceptance by copying only the
manifest identity into `video.yaml` as
`confirmationReview.approvedIdentity`. This keeps approval explicit and
reviewable while avoiding a timestamped or external-consumer-owned state.
Before final build, Cozy re-reads the confirmation manifest and output, proves
their current identities against the normalized Storyboard and effective
production inputs, and compares the manifest identity to that source approval
record. Any missing, stale, or mismatched input fails before final output is
opened.

Mode-specific output directories and manifests prevent confirmation from
overwriting final artifacts. Cache keys include mode, normalized Storyboard
identity, scene projection identity, narration selection, renderer settings,
and relevant admitted assets. A cache hit is therefore an exact identity
decision; it cannot reuse a chunk after any of those inputs changes. Final
rendered-video evidence remains a distinct post-build operation, and any
PPTX derived from that evidence remains a Dox/PPTX consumer concern.

## 7. Confirmation/final separation and cache design

Confirmation and final builds are separate lifecycle states and output
locations. Confirmation is a reviewable rendered flow and MUST never overwrite
the final MP4 or its evidence. Final build consumes the approved Storyboard,
the applicable production configuration, and the accepted confirmation state
required by the later Step contract.

Every derived audio, render, review, and hand-off artifact has an identity
derived from its complete relevant inputs. At minimum, scene-level reuse
records include the normalized Storyboard identity or scene identity, scene
content and order, narration input/provider settings, renderer settings,
production configuration, and relevant diagram/asset identities. Confirmation
and final mode are separate identity inputs even when their scene content is
unchanged.

Cache reuse is allowed only on exact identity match. A changed scene, timing,
pronunciation note, asset, renderer setting, narration input, or mode MUST
invalidate the dependent artifact deterministically. Unchanged independent
artifacts MAY be reused when their own complete identity matches. Generated
manifests under `target/cozy-video` record why an artifact was reused or
invalidated; they do not override approval or validation.

Final rendered-video evidence is independent from Storyboard approval and is
produced after final rendering. A video-derived review PPTX is optional and
special-purpose for distribution, meeting, handoff, or archive use; it is not
a build prerequisite or a substitute for rendered-video review.

## 8. Explicit legacy migration boundary

The legacy dialogue `script.json` parser and the v1 Storyboard parser are
different adapters with different typed outputs. A file is not a Storyboard
because it is named `script.json`, omits `schema`, or happens to contain a
`scenes` array. Existing `parts[].script` support remains available on the
legacy path pending a separately accepted migration.

Migration is an explicit boundary selected by a caller (for example, a
dedicated `--from legacy-script` adapter mode). The adapter must produce a
diagnostic mapping report for each source field, identify defaults and
ambiguities, and reject values that cannot be represented losslessly. It must
never silently treat `script.json` as `storyboard.json`, silently infer a
Storyboard identity, or remove `parts[].script` as an incidental cleanup.

The new Storyboard scaffold therefore source-manages `storyboard.md` (and may
exchange `storyboard.json`) without requiring a source `script.json`. Any
serialized execution hand-off is generated under `target/cozy-video` and is
not a new legacy source contract.

## 9. Command and hand-off boundaries

The later Cozy CLI uses the following responsibility split:

```text
storyboard validate / inspect
  -> parse + common validation + normalized identity

storyboard convert
  -> explicit source adapter + validated lossless serialization

storyboard review-evidence
  -> approved identity + optional visual-input identity + derived evidence

video build --mode confirmation|final
  -> approved identity + stale gates + mode-specific derived artifacts
```

These commands do not accept a source `script.json` as a Storyboard. Legacy
inputs require the explicit migration adapter and its diagnostics. Build code
consumes the approved normalized Storyboard and separate `video.yaml`; it does
not reparse a second dialogue source to fill missing fields.

## 10. Non-goals and current status

P30-01 has implemented the typed Storyboard classes, strict Markdown/JSON
parsers, canonical conversion, direct schema commands, and executable
specifications. P30-02 now freezes only the optional evidence, hand-off, and
stale-input boundary above. It does not implement narration changes, renderer
changes, Dox/PPTX generation or consumer acceptance, external repository
changes, publication, deployment, or runtime acceptance. P30-03 remains
responsible for new Storyboard build integration and confirmation/final modes.

Phase 30 is currently IN PROGRESS. The one-Phase authorization changes only
delivery shape; it does not claim that any implementation or acceptance gate
has passed.

## 11. Storyboard v2 Visual Page coexistence

The accepted v1 Storyboard remains the semantic and lifecycle authority for
its exact `{heading,content}` Screen shape. No v1 parser, Markdown path,
review, build, cache, confirmation/final record, or external consumer path is
reinterpreted by v2.

P36-04 adds a separate JSON-only `cozy.video.storyboard.v2` route. Its screen
is a closed choice between a text value and the literal
`{kind,source,catalog,pageId}` Visual Page reference. `source` and `catalog`
are direct, safe, descriptor-relative files rooted at the Storyboard source
directory. Cozy validates the referenced VisualPageSet with precisely that
catalog and resolves `pageId` exactly once. It never chooses catalog, binding,
renderer, or a page by inference.

The literal reference is Storyboard meaning and therefore participates in the
Storyboard identity. The resolved page/catalog/binding/renderer/asset
identities remain future visual-evidence inputs; P36-04 neither creates a
renderer invocation nor advances receipt/review evidence responsibility.
Planning carries the same closed reference as scene metadata while preserving
narration, speaker, timing, silence, transition, confirmation/final, and
audiovisual semantics in the existing Storyboard workflow.

`cozy video storyboard migrate --from v1 --to v2 --screen text <input> --save
<output.json>` is the explicit one-way compatibility adapter. It writes only
canonical v2 JSON text screens. A visual-page v2 screen cannot downgrade to v1
and is rejected with `VISUAL_PAGE_SCREEN_LOSSY`; `convert` retains its existing
representation-conversion responsibility. P36-04 excludes P36-05 receipts and
evidence, P36-06 cross-media acceptance, SmartDox/Textus work, and external
consumer acceptance.

## 12. P36-05 Storyboard-v2 visual evidence boundary

`P36-05-DEC-001` is implemented as a separately versioned proof route only
for a Storyboard v2 that contains a Visual Page screen. Its one closed
`storyboardReview.visualPage` declaration has `binding`, `evidenceDirectory`,
and `approvedEvidenceIdentity`; the binding is safe project-relative while
each screen's literal `source` and `catalog` remain rooted at its Storyboard
source directory. Cozy uses no discovery route: it loads that exact
VisualPageSet/catalog pair, resolves `pageId` exactly once, and validates the
one binding against every resolved set/catalog.

Evidence and handoff v2 preserve the literal
`{kind,source,catalog,pageId}` with VisualPageSet, catalog, logical-page,
visual-page, selected page-asset, binding, and canonical effective-renderer
identities. Each part selecting the reviewed Storyboard source must resolve a
part override or project renderer; the sorted renderer proof is a canonical
configuration plus SHA-256 identity. The handoff has a separate canonical
self identity. Confirmation/final build validation reconstructs both values
from direct current inputs before cache reuse or output work. Any change,
absence, unsafe path, unresolved page, missing part/renderer, approval
mismatch, evidence mismatch, or handoff mismatch rejects closed.

The v1 Storyboard and v1 evidence/handoff remain unchanged, as do legacy video
projects. This boundary does not execute a renderer, generate a video, accept
external consumers, claim focused validation, or close VIS36-05.

## 13. P36-06 Cross-media Review consumption

A later Cozy-only Cross-media Review may read, but never rewrite, an existing
current P36-05 evidence/handoff pair. It proves that the literal Storyboard
screen resolves to one page of the presentation's current VisualPageSet and
that catalog, binding, selected-page, and selected-asset identities agree.
This is a structural currentness check between generated evidence boundaries;
it leaves Storyboard narration, timing, transition, confirmation/final, and
audiovisual review authority intact. It records no semantic, visual, or
audiovisual acceptance decision and excludes SmartDox/Textus consumer work,
renderer execution, publication, and deployment.
