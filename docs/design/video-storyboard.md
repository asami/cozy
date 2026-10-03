# Cozy Video Storyboard Design

Status: NORMATIVE DESIGN; Phase 30 v1 remains accepted; Phase 36 `VIS36-04`
through `VIS36-06` are DONE.

`P36-04-DEC-001` is consumed. Focused validation invocation
`90284-20260827T085957Z` (`testOnly cozy.video.CozyVideoStoryboardSpec`) reported
16 succeeded, 0 failed/aborted, SBT/wrapper 0, and the lock released.
Independent `P36-04-REREVIEW-002` is PASS and `CB-P36-04-RR-001` is resolved.
P36-04 through P36-06 are accepted as complete. P36-06's closed Cross-media
Review route strictly rejects duplicate JSON object fields before decoding
Cross-media proof or Storyboard v2 evidence/handoff. No push, publish, or
external-consumer acceptance is claimed; Phase 37 remains NOT STARTED.

This design defines the architecture, responsibility boundaries, and stable
invariants for `cozy.video.storyboard.v1`. The functional contract is
[`docs/spec/video-storyboard.md`](../spec/video-storyboard.md). This
documentation-only foundation does not claim implementation, executable
specifications, validation, review, commit, or completion.

## 2026-09-29 Phase 71 generation-boundary amendment

Authority is [Phase 71 P710-02](../phase/phase-71.md) and the approved
[file-update boundary](../spec/file-update-management.md). Generation uses the
safe typed Storyboard source declared by each part and its optional section.
Review approval, saved visual evidence, and confirmation acceptance remain
explicit review responsibilities and never gate confirmation or final build.
The root review/confirmation schemas and identity/hash records remain
transitional in this Slice; their native removal, model/codec/scaffold work,
and future currentness contract are still assigned before Step acceptance.

### 2026-09-29 P710-02C native assembly amendment

The native Storyboard branch separates typed Storyboard source content
from generated local artifacts; S04 removes its general semantic identity API.
It records only v2 descriptive handoffs,
mode/setting projections, mode-local part artifacts, direct paths, and stored
successful ffprobe summaries. Native currentness is declared-input `FileTime`
policy plus independent direct-path and format checks, rather than canonical
body equality or any generated digest. Immediate final review reads that
metadata and its declared inputs without launching a hidden subprocess; the
existing review command retains its separately owned frame/audio/hash evidence.

Native assembly never invokes the legacy project build path. A regeneration
uses a unique `target/cozy-video/staging/<mode>/attempt-*` directory, writes
credits only there, muxes and probes there, and installs the exact prepared
set with atomic per-file replacement. The installer snapshots prior direct
destinations before its first move and retains recovery evidence if rollback
cannot complete. Confirmation and final share successful handoffs but neither
reads or mutates the other's output subtree.

This supersedes historical build/cache hash wording for native v2 generated
records only. Design section 12's generation-evidence reconstruction remains
an explicit-review historical contract; accepted source v1/v2 and historical
review contracts are preserved. Implementation, executable validation, review,
and Phase closure remain pending.

## 2026-10-02 Phase 71.2 S04 typed-content amendment

The approved [file-update boundary](../spec/file-update-management.md) and
[Phase 71.2](../phase/phase-71.2.md) remove the general Storyboard semantic
SHA API/helper and CLI emission, and the immediate Projection selector and
receipt field. Typed content, canonical lossless serialization, declared safe
inputs and actual media validity remain the boundaries. No replacement hash,
canonical-content equality or currentness framework is introduced. This
supersedes historical hash-purpose decisions only; the prior accepted
validation evidence, ordinary scene/speaker/insert IDs, renderer/provider/mode
ownership and separate distributed-resource integrity remain intact.
S04 focused validation and independent review are pending; this amendment
claims no Step, Phase or actual-private-driver acceptance.

## 1. Design intent

Cozy needs one typed content contract across human review, external interchange,
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
- normalization and canonical lossless JSON serialization;
- schema/version, stable scene ID/order, speaker/role, timing, field, and safe
  path/reference validation;
- explicit legacy-dialogue adapter selection and migration diagnostics;
- normalized content-review and optional visual-story review evidence;
- explicit review input/format/reference validation without historical approval hashes;
- independently selectable confirmation/final modes and their distinct lifecycle/output records;
- declared-input FileTime generation decisions and valid mode-local artifact metadata;
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

## 3. Typed semantic pipeline

The architecture has one directional pipeline:

```text
storyboard.md / storyboard.json
        -> strict parser
        -> typed Storyboard
        -> common validation
        -> canonical lossless serialization
        -> optional explicit approval/review evidence
        -> independently selected confirmation or final build
        -> target/cozy-video derived artifacts and cache
```

The Markdown parser and JSON parser MUST converge before any downstream
behavior. No renderer, narration provider, review generator, or cache may
branch on the source representation. A source path is provenance/diagnostic
context only; it does not define authored Storyboard meaning.

Canonical JSON preserves the specified field order, array order, normalized
decimal timing and UTF-8 encoding. Conversion preserves all typed fields,
including narration, pronunciation notes, direction and literal references.
Formatting and input key order normalize without altering those values;
canonical bytes serve serialization, never product currentness.

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
lossless. Both representations preserve the same typed model and canonical
JSON. A conversion MUST validate and normalize before writing output, and a failed
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

Deterministic review projects current declared Storyboard/visual inputs into
reviewable evidence. Semantic/visual/audiovisual approval remains an external
decision; no generated digest or review declaration represents that decision.
Historical approval/content identity gates are removed under the DM14 policy.
Actual safe inputs, formats and declared references remain independently valid.
Missing or invalid actual files still report failures; valid changes do not
require equality with an old proof. Dox/PPTX handoffs carry locations and
operational review data, not digest identities or consumer acceptance.

### 6.1 P30-02 projection boundary

P30-02 uses `storyboardReview.source` and optional visual declarations as
review-input selection, not build selection or semantic approval. The decoder
ignores arbitrary old approval properties. The generator validates current
inputs, copies admitted visual files and writes deterministic review/handoff
data without hash calculation. Explicit review validation checks required
non-hash structure and actual inputs/copies, not historical whole-payload,
renderer, source, material, or approval equality. Ordinary generated-evidence
readers select known fields positively and ignore arbitrary unspecified JSON
metadata at root, scenes/screens, visual inputs, visual pages/assets and
effective renderers/configurations. They retain required fields/types, nonempty
scenes, safe references, unique IDs, existing page/asset relative key order and
VideoRenderer decoding/domain checks. All nine configuration fields remain
required even where their existing values may be null. Unknown root collections
are excluded before row validation, so handoff extras cannot become evidence
rows. This is a generated-data reader boundary: authored Storyboard/Scene/Screen,
configuration parsers, review declarations, source/copy admission, result/current
APIs and approval ownership do not change. Result/current APIs expose
locations only. This supersedes the prior hash-based P30-02 review contract;
it does not alter independent confirmation or audiovisual review authority.

## 7. Confirmation/final separation and cache design

Confirmation and final builds are separate lifecycle states and output
locations. Confirmation is a reviewable rendered flow and MUST never overwrite
the final MP4 or its evidence. Final build consumes each part's declared valid
Storyboard and applicable production configuration independently of confirmation.

The native confirmation/final assembly produces a bounded set: the mode MP4,
mode manifest, selected Storyboard handoffs, mode-local part-artifact records,
and applicable credit records. The unchanged `FileTime` generation policy is
applied to each member against the same declared safe input closure, including
descriptor-relative selected Visual Page source/catalog/assets and recursive
record directories. Equal-or-older inputs permit reuse only when all products
are valid; newer, absent, invalid, unsafe, unknown-time, or old-schema inputs
cause regeneration. Ordinary unrecognized fields in a current v2 record do
not alone invalidate it.

Regeneration writes only a unique staging tree, probes there, and atomically
replaces the preflighted unique destination set. Existing direct destinations
are backed up with bytes, mode, and mtime; ordinary move failure restores the
completed subset, while a failed rollback retains the staging location and
both original and rollback diagnostics. This is explicitly not a crash-safe
multi-file transaction, lock, receipt, or concurrency design.

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
Storyboard identifier, or remove `parts[].script` as an incidental cleanup.

The new Storyboard scaffold therefore source-manages `storyboard.md` (and may
exchange `storyboard.json`) without requiring a source `script.json`. Any
serialized execution hand-off is generated under `target/cozy-video` and is
not a new legacy source contract.

## 9. Command and hand-off boundaries

The later Cozy CLI uses the following responsibility split:

```text
storyboard validate / inspect
  -> parse + common validation + schema/version and scene summaries

storyboard convert
  -> explicit source adapter + validated lossless serialization

storyboard review-evidence
  -> current typed source + optional safe visual inputs + derived evidence

video build --mode confirmation|final
  -> each part's safe typed source + mode-specific derived artifacts
```

These commands do not accept a source `script.json` as a Storyboard. Legacy
inputs require the explicit migration adapter and its diagnostics. Build code
consumes each declared normalized Storyboard source and separate `video.yaml`;
it does not reparse a second dialogue source to fill missing fields.

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

The literal reference is typed Storyboard meaning and is preserved in
canonical serialization. Actual page/catalog/Binding inputs and asset observations
are resolved by the review operation; no copied semantic proof is required.
Historical effective-renderer digests are removed by the review amendment. P36-04 neither creates a
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
`storyboardReview.visualPage` declaration has `binding` and `evidenceDirectory`; the binding is safe project-relative while
each screen's literal `source` and `catalog` remain rooted at its Storyboard
source directory. Cozy uses no discovery route: it loads that exact
VisualPageSet/catalog pair, resolves `pageId` exactly once, and validates the
one binding against every resolved set/catalog.

Evidence and handoff v2 entries are exactly
`sceneId,kind,source,catalog,pageId,assets`. Assets retain
`{id,path,mediaType,modifiedAt}` observations from safe current direct files.
The four VisualPage digest fields and complete Binding copy are deleted
without substitute JSON/fingerprint proof. Positive known-field selection ignores
arbitrary extras generically at page/asset objects while preserving required
relative field order, strings and unique IDs; actual named set/catalog/page/Binding validation is separate
from historical evidence, and old asset observations are not currentness gates.

Explicit v2 validation resolves actual current PageSet/catalog/page/Binding
and effective renderer selection; historical review data is structurally checked
but is not required to equal current inputs. Review-local approval, source,
self/evidence/handoff, visual-copy and renderer digests are removed in both v1
and v2. No new equality/currentness framework replaces them. S04 subsequently removes the general Storyboard identity APIs. Native
confirmation/final assembly contracts retain their separate ownership.

## 13. P36-06 Cross-media Review consumption

CrossReview validates actual current per-side PageSet/catalog/page/Binding and
safe files; its existing asset operation compares IDs/media types/bytes across
independent roots. It does not compare full catalog/page/Binding copies.
Presentation provenance wrappers are `{path}`; aggregate pages are
`{sceneId,pageId,assets}` and omit all removed proof properties.

CrossReview continues through public `storyboardReviewCurrent`; its ordinary
readers select known Presentation wrapper/slide, Storyboard root/page and
aggregate root/slide/page/asset/verification fields, retaining required relative
order where already enforced and ignoring every unspecified property. Generated
records remain deterministic and omit hashes. Saved verification checks operational structure, uniqueness, and reference
resolution, not reconstructed whole-value equality. Build constructs once and
may atomically replace a safe direct regular aggregate, never actual inputs or
participating review evidence. Invalid input and generation failure preserve
prior bytes. Approval states remain `not-recorded`; no renderer, publication,
deployment, or semantic/visual/audiovisual acceptance occurs. Media receipt
admission and non-review artifact integrity remain separate
responsibilities, not replacement proof gates.

Presentation receipt artifacts are `id,role,path`; deterministic review wrappers
carry paths and actual required slide/page/asset/renderer/status values are
reconstructed recursively. Review-state snapshots require only UTC ISO-8601
`acceptedAt` with generic extras ignored and the top-level lifecycle grammar
closed. The current review modification time is the generation boundary;
unchanged reuse preserves alignment, and a newer generation requires explicit
realignment even for identical bytes. Structurally valid timestamp-preserved
edits have no historical-content-detection promise; safe files, actual OOXML/PNG
and cross-root selected material-byte QA remain. Presentation renderer files are
direct outputs; no new primary-output transaction is claimed. SummarySlidesPdf
S07, Publisher/rsync, final named-integrity audit, English-PDF and complete
isolated-site drivers remain pending. This amendment claims no Step/Phase closure.

## 14. Phase 61 legacy authoring compatibility boundary

The legacy dialogue adapter remains separate from both Storyboard adapters.
Phase 61 admits one legacy source-model field, `VideoScene.tailSilence`, without
changing Storyboard v1/v2 strict schemas or canonical serialization. Its
historical general identity API is subsequently removed by S04.
The field is an optional finite nonnegative request in seconds. Omission and
legacy `null` retain no explicit request and mean semantic zero; child omission
inherits a parent request, while explicit child `0` overrides it. The optional
field is appended with a default so existing positional source construction
remains compatible. Supplied negative or non-finite values fail the legacy
decoder rather than being normalized or clamped.

This source-model admission preserves the existing `duration`,
`targetDuration`, and eight-second default precedence. It does not interpret
the author request as a generated audio-manifest value. For the later timing
owner, `T` is that established target, `L` is lead silence, `A` is actual
normalized WAV duration, and `E` is authored requested tail silence:

```text
effectiveSceneDuration = max(T, L + A + E)
effectiveTrailing = max(E, T - L - A, 0)
```

Existing generated audio-manifest `tailSilence` remains the effective output
interval, not requested input. Phase 61.1 realizes the effective interval from
the actual normalized WAV and projects both requested and effective values into
Remotion props and deterministic review evidence.

P610-02 implements
`voiceTextNormalization.removeMiddleDots` as an optional false-default boolean
for final provider speech only. Its behavior is after existing whitespace
normalization and the single-pass pronunciation dictionary, where it removes
only U+30FB (`・`). It does not mutate narration, line, caption, headings, or
any other source/display string; malformed nonboolean configuration fails
before provider I/O. It neither changes dictionary pass count nor introduces
general punctuation normalization.

Phase 61.1 exclusively owns Storyboard/pipeline conversion, authored
trailing-silence timing realization, generated audio-manifest writing, evidence,
and currentness. Its Cozy-only deterministic acceptance fixture uses
Article-9-shaped Japanese source/display terms containing U+30FB with the
local provider seam: provider speech loses only U+30FB, while source, line, and
caption remain unchanged. A requested 0.8-second final tail remains distinct
from its effective generated interval across the audio manifest, renderer
props, and review evidence. The final visual/caption covers that effective
tail, while actual audio and character mouth activity end at the WAV boundary.
The selected tail still frame proves visual/timing state only, never audible
listening acceptance. Independently configured five-second summary infographic
and credits holds remain separate and acquire no duplicate final-tail interval.
This fixture makes no external Article 9 project claim or external provider,
publication, upload, or deployment call; Storyboard v1/v2 typed fields,
canonical serialization and schemas remain unchanged by that historical fixture.
S04 subsequently removes the general identity API.
