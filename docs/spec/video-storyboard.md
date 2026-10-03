# Cozy Video Storyboard Specification

Status: NORMATIVE; Phase 30 v1 remains accepted; Phase 36 `VIS36-04` through
`VIS36-06` are DONE.

This document is the authoritative functional specification for accepted
`cozy.video.storyboard.v1` and the separately versioned
`cozy.video.storyboard.v2` screen contract. `P36-04-DEC-001` is consumed.
P36-04 focused validation
invocation `90284-20260827T085957Z` passed with 16 succeeded/0 failed/0
aborted, SBT/wrapper 0, and the lock released; independent
`P36-04-REREVIEW-002` is PASS and `CB-P36-04-RR-001` is resolved.
P36-04 through P36-06 are accepted as complete; their review proof retains the
separate semantic, visual, and audiovisual authority boundary. P36-06 rejects
duplicate JSON object fields before Cross-media proof or Storyboard v2
evidence/handoff decoding. No push, publish, or external-consumer acceptance
is claimed; Phase 37 remains NOT STARTED.

The words **MUST**, **MUST NOT**, **SHOULD**, and **MAY** are normative. The
corresponding responsibility and ownership design is in
[`docs/design/video-storyboard.md`](../design/video-storyboard.md).

## 2026-09-29 Phase 71 generation-boundary amendment

Authority: [Phase 71 P710-02](../phase/phase-71.md) and its
[file-update boundary](file-update-management.md). This amendment changes only
generation and confirmation prerequisites. A build validates each declared
`parts[].storyboard` source, section, tools, credits, and safe output path; it
does not read or require `storyboardReview`, visual evidence, or
`confirmationReview` approval. Confirmation and final remain explicitly
selected, independent modes with separate outputs. `storyboardReview` remains
a review-only contract. The DM14 contract in Section 9.1 supersedes its former
approval/hash decoding; actual declaration syntax is still validated.

### 2026-09-29 P710-02C native currentness amendment

Native generated records are `cozy.video.storyboard-build-handoff.v2`,
`cozy.video.confirmation.v2`, `cozy.video.final.v2`, and
`cozy.video.storyboard-part-artifact.v2`. They carry descriptive paths,
selected typed Storyboard content, mode/settings, declared artifacts, and a
successful ffprobe summary; they MUST NOT carry source, projected, handoff,
cache, credit, output, or manifest hash identities. General Storyboard v1/v2 semantic hashes are subsequently removed by S04.
Separately owned review/audio/integrity mechanisms are outside that removal.

Currentness is a `CozyFileUpdatePolicy` generation decision over direct,
declared safe inputs and `FileTime` observations. Missing, invalid, symlinked,
or timestamp-unknown inputs fail closed or choose conservative regeneration;
ordinary unknown fields in a current v2 generated record are ignored. Reuse
requires valid mode-local metadata and artifacts, a valid direct output, and a
read-only ffprobe summary with a finite positive duration and video stream.
No current product may be written, rendered, synthesized, or muxed.

On regeneration Cozy stages the output, generated records, and applicable
three credit files, probes the staged output, then atomically replaces the
bounded declared destination set. A normal installation error restores each
already replaced file from a staged byte/mode/mtime backup or removes only a
new absent-before file. This is a single-writer ordinary-I/O-error guarantee;
it is not a crash-safe multi-file transaction or a new receipt/lock framework.

## 2026-10-02 Phase 71.2 S04 typed Storyboard amendment

Authority: [Phase 71.2](../phase/phase-71.2.md) and the approved
[file-update boundary](file-update-management.md). S04 removes the general
Storyboard semantic SHA API, result property, helper and CLI identity output,
plus the immediate Explanation Projection Storyboard selector and receipt
proof. There is no replacement identity or canonical-content currentness
comparison. This supersedes only the hash purpose of earlier v1/v2 decisions;
their accepted historical validation evidence and typed schema contracts remain.

Canonical serialization and actual validate/inspect/convert/migrate behavior
are specified by [CozyVideoStoryboardSpec](../../src/test/scala/cozy/video/CozyVideoStoryboardSpec.scala).
Actual planning and source preservation are specified by
[CozyVideoStoryboardBuildSpec](../../src/test/scala/cozy/video/CozyVideoStoryboardBuildSpec.scala)
and [CozyVideoStoryboardBuildModeSpec](../../src/test/scala/cozy/video/CozyVideoStoryboardBuildModeSpec.scala).
The immediate named-media consumer is specified by
[CozyExplanationProjectionSpec](../../src/test/scala/cozy/media/CozyExplanationProjectionSpec.scala).
S04 validation and independent review were subsequently accepted. The complete
hash-removal Step passed 331 focused tests in 15 suites and independent Step
review, with exact acceptance commit `00e73d9`. Phase 71.2 is CLOSED by the distinct local release containing the [normal closure record](../journal/2026/10/2026-10-03-phase-71.2-normal-closure.md). The sole independent full Phase review and its accepted M0 clarification amendment leave zero current blockers. Repository-full validation remains deferred-not-run to PHASE-71.4; no successor execution is claimed.

## 1. Scope and typed semantics

The Storyboard is the single content contract for Cozy video production. A
human-authored restricted `storyboard.md` and an external machine-authored
`storyboard.json` are two serializations of one typed semantic model. They
MUST normalize to the same model when their defined values are equivalent.
Formatting, input JSON key order, line endings and representation choice do
not change the typed values.

The public v1 schema identifier is exactly `cozy.video.storyboard.v1`. The root
model MUST contain both `schema` and integer `version` fields with values
`cozy.video.storyboard.v1` and `1`. There is no second v1 Storyboard schema.
Both fields remain required typed source data.

Canonical JSON has the field order specified in Section 3, preserves array
order, uses no insignificant whitespace, and writes timing decimals in their
normalized decimal form. It defines lossless serialization and round trips,
not an identity or currentness token. Referenced path strings remain literal
typed data; source filename and filesystem location are provenance.

## 2. Typed semantic model

The implementation MUST use a typed model rather than an unvalidated generic
JSON tree. Every field below is required to be present. An empty string or
empty array is a value where explicitly allowed; omission is never an implicit
empty value.

### 2.1 Storyboard

| Field | Type and invariant |
| --- | --- |
| `schema` | String, exactly `cozy.video.storyboard.v1`. |
| `version` | Integer, exactly `1`. |
| `scenes` | Non-empty ordered sequence of `Scene` values. The sequence is in ascending `order`. |

### 2.2 Scene

| Field | Type and invariant |
| --- | --- |
| `id` | Non-empty stable identity matching `[A-Za-z][A-Za-z0-9_-]*`; unique across the Storyboard. |
| `order` | Positive integer, unique and contiguous from `1`; it MUST agree with sequence order. |
| `section` | Required non-empty semantic section name. It uses the same stable-token grammar as `id`. |
| `speaker` | Required non-empty speaker identity matching `[A-Za-z][A-Za-z0-9_-]*`; `narrator` is the reserved narration identity. |
| `role` | Required enum: `narration`, `dialogue`, `direction`, or `system`. Other role values are invalid in v1. |
| `narration` | Required authored narration string. It MAY be empty for a deliberately silent scene; conversion MUST preserve it exactly as a semantic string. |
| `screen` | Required `Screen` value. |
| `caption` | Required caption string; it MAY be empty. |
| `duration` | Required finite decimal seconds greater than zero, with no more than six fractional digits. |
| `leadSilence` | Required finite decimal seconds from zero through `duration`, with no more than six fractional digits. |
| `transition` | Required enum: `none`, `cut`, `fade`, `dissolve`, or `wipe`. |
| `productionInserts` | Required ordered sequence of `ProductionInsert` values; it MAY be empty. |
| `diagramRefs` | Required ordered sequence of safe project-relative reference strings; it MAY be empty. |
| `assetRefs` | Required ordered sequence of safe project-relative reference strings; it MAY be empty. |
| `pronunciationNotes` | Required ordered sequence of `PronunciationNote` values; it MAY be empty. |
| `direction` | Required production-direction string; it MAY be empty. |

`narration`, `caption`, and `direction` are authored semantic values. Cozy
MUST NOT rewrite their meaning during conversion or use an AI conversion to
silently add, remove, or paraphrase them.

### 2.3 Nested values

`Screen` contains two required strings:

| Field | Type and invariant |
| --- | --- |
| `heading` | Required screen heading string; it MAY be empty. |
| `content` | Required screen content string; it MAY be empty. |

`ProductionInsert` contains three required fields:

| Field | Type and invariant |
| --- | --- |
| `id` | Non-empty stable token unique within its scene. |
| `kind` | Required enum: `overlay`, `cutaway`, `pause`, `marker`, or `custom`. |
| `value` | Required non-empty scalar payload with no control characters. Path-like content belongs in `diagramRefs` or `assetRefs`, not an untyped insert payload. |

`PronunciationNote` contains two required non-empty strings:
`surface` is the authored expression and `reading` is the intended provider
reading. Notes preserve sequence and exact text. They do not change the
authored `narration` value.

A reference in `diagramRefs` or `assetRefs` is a project-relative POSIX path
with one or more non-empty segments. A segment contains only ASCII letters,
digits, `.`, `_`, and `-`, and starts with a letter or digit. References MUST
not begin with `/`, contain `\\`, `..`, an empty segment, a NUL/control
character, a URI scheme, a query or fragment, or a path that normalizes to a
different string. Cozy records a safe reference even when the referenced file
is not present; later evidence/build gates diagnose a missing resource without
weakening path safety.

## 3. Canonical JSON representation

The canonical `storyboard.json` representation is UTF-8 JSON with exactly the
following root and scene fields. The displayed order is the canonical output
order; input object key order does not affect parsing, but unknown keys and
duplicate keys are rejected. `null`, non-finite numbers, and untyped values
are not permitted.

```json
{
  "schema": "cozy.video.storyboard.v1",
  "version": 1,
  "scenes": [
    {
      "id": "intro",
      "order": 1,
      "section": "opening",
      "speaker": "narrator",
      "role": "narration",
      "narration": "Welcome to the video.",
      "screen": {
        "heading": "Welcome",
        "content": "The opening screen."
      },
      "caption": "",
      "duration": 5.0,
      "leadSilence": 0.25,
      "transition": "cut",
      "productionInserts": [],
      "diagramRefs": [],
      "assetRefs": ["assets/title.png"],
      "pronunciationNotes": [],
      "direction": "Keep the title readable."
    }
  ]
}
```

Canonical output MUST use the field spelling above (`leadSilence`,
`productionInserts`, `diagramRefs`, `assetRefs`, and `pronunciationNotes`).
The canonical serializer MUST emit all required fields, including empty values,
and MUST preserve scene and nested-array order. A serializer MUST NOT emit
fields that the typed model cannot preserve.

## 4. Restricted human Markdown representation

`storyboard.md` is a restricted Markdown serialization, not a general Markdown
document. It MUST contain one `# Storyboard` heading, the root `schema` and
`version` lines, and one or more `## scene` blocks. The exact field order below
is required. Blank lines are permitted only between complete records. There
are no links, tables, lists, HTML, front matter, arbitrary headings, or fenced
code blocks in the grammar.

String scalar values on ordinary lines MUST be JSON string literals. Timing
values MUST be finite decimal seconds followed by `s`, with no more than six
fractional digits. Array values MUST be one-line JSON arrays using the typed
JSON shapes from Section 3. Multiline values use a `|` literal marker followed
by zero or more lines indented by two spaces; line endings normalize to LF and
the resulting string value, including internal blank lines, is preserved.

The grammar is:

```text
# Storyboard
schema: "cozy.video.storyboard.v1"
version: 1

## scene
id: "<scene-id>"
order: <positive-integer>
section: "<section>"
speaker: "<speaker>"
role: "<narration|dialogue|direction|system>"
narration: |
  <zero or more indented lines>
screen:
  heading: "<screen-heading>"
  content: |
    <zero or more indented lines>
caption: "<caption>"
duration: <decimal-seconds>s
lead-silence: <decimal-seconds>s
transition: "<none|cut|fade|dissolve|wipe>"
production-inserts: <JSON array of ProductionInsert>
diagram-refs: <JSON array of safe reference strings>
asset-refs: <JSON array of safe reference strings>
pronunciation-notes: <JSON array of PronunciationNote>
direction: |
  <zero or more indented lines>
```

The parser MUST reject a missing line, duplicate line, misplaced line,
unsupported heading, malformed literal block, malformed JSON array, unknown
key, or additional content. The Markdown names `lead-silence`,
`production-inserts`, `diagram-refs`, `asset-refs`, and
`pronunciation-notes` map only to their canonical JSON names; no other aliases
are accepted. A canonical Markdown serializer MUST emit the grammar in this
order, with deterministic quoting and indentation.

## 5. Normalization, conversion, and losslessness

Both inputs MUST follow this pipeline:

```text
source bytes -> representation parser -> typed Storyboard
             -> validation -> canonical lossless serialization
```

Parsing and validation happen before conversion output is written. Conversion
between Markdown and JSON is semantic and lossless: every required field,
empty value, scene order, nested-array order, and safe reference is retained.
Formatting is allowed to change, but no semantic value may be silently
discarded, inferred from another field, or rewritten. Converting an already
normalized file twice MUST produce byte-identical canonical output.
Equivalent Markdown and JSON MUST yield the same typed fields and
byte-identical canonical JSON.

Unknown fields are rejected because v1 has no extension container that can
preserve them. A future extension requires a separately versioned, explicitly
lossless contract; a parser MUST NOT retain unknown data in an untyped map just
to avoid a diagnostic.

## 6. Validation and rejection diagnostics

Validation MUST fail closed and return structured diagnostics containing at
least a stable code, source location or field path, and a concise reason. It
MUST reject:

- missing or duplicate required fields;
- duplicate scene IDs, duplicate scene orders, non-contiguous orders, or an
  order that disagrees with sequence order;
- unsupported `schema` or `version` values;
- malformed JSON, duplicate JSON keys, malformed Markdown, invalid string
  encodings, or values of the wrong type;
- negative, zero-duration, non-finite, over-precise, or otherwise malformed
  timing, and lead silence outside `0 <= leadSilence <= duration`;
- an invalid speaker token, an unsupported role, an invalid transition, an
  invalid production-insert kind, or duplicate insert identity;
- malformed, absolute, escaping, URI-like, control-character, backslash, or
  otherwise unsafe diagram/asset references; and
- an unknown field or input construct that cannot be preserved by the typed
  v1 model.

Diagnostics MUST identify the offending path (for example,
`scenes[0].assetRefs[1]`) and MUST NOT silently choose a duplicate, truncate a
value, drop a field, or reinterpret an unsafe reference. Missing external
assets are a later evidence/build concern, not permission to accept an unsafe
path.

## 7. Legacy dialogue boundary

`script.json` is a separate legacy dialogue format. It is not
`storyboard.json`, is not a v1 representation, and MUST NOT be treated as
semantically equivalent merely because both contain a `scenes` array. Legacy
fields such as `title`, `line`, `subscenes`, or `parts[].script` remain under
their existing legacy contract.

The new Storyboard path does not require a source-managed `script.json`. Legacy
`parts[].script` support is retained pending a separately accepted migration;
removing or changing that support is outside this specification Slice. A
migration MUST be explicit, for example through a dedicated legacy adapter
selection, and MUST emit diagnostics describing every mapped, defaulted,
ambiguous, and unmapped field. It MUST fail rather than silently claim a
lossless Storyboard when a legacy value cannot be represented. No automatic
filename convention or schema omission may silently select the adapter.

Same-visual legacy dialogue is specified by the [Video Dialogue Continuity contract](video-dialogue-continuity.md).

## 8. Intended Cozy command contracts

These command contracts define the later CLI surface; they do not claim that
the commands exist in the current implementation.

| Command | Contract |
| --- | --- |
| `cozy video storyboard validate <storyboard.md\|storyboard.json>` | Parse, validate, normalize, and report schema/version, scene count and structured diagnostics. It MUST not build video or mutate a source Storyboard. |
| `cozy video storyboard inspect <storyboard.md\|storyboard.json>` | Perform the same safe parse/validation and display normalized schema/version, scene order, field summary and references without changing semantic content. |
| `cozy video storyboard convert <input> --save <output.md\|output.json>` | Validate first, then write deterministic canonical output. A legacy input requires an explicit adapter selection and diagnostic migration report. Failed validation MUST not produce a claimed Storyboard output. |
| `cozy video storyboard review-evidence <video-project> --save <dir>` | Create deterministic content-review data from the selected current Storyboard and optional declared safe visual inputs, without digest-based approval or historical currentness gates. |
| `cozy video build <video-project> --mode confirmation` | Validate and consume each declared Storyboard part source and create a confirmation output with a distinct location. Optional review state is not a build prerequisite. |
| `cozy video build <video-project> --mode final` | Validate and consume each declared Storyboard part source, create a separate final output, and never overwrite confirmation output. Final does not require a confirmation artifact or approval record. |

The project’s `video.yaml` remains separate production configuration. It may
select the Storyboard and configure characters, narration provider, renderer,
effects, credits, and project-owned assets, but those concerns are not copied
into each Storyboard scene. Generated handoff, manifests, evidence, and caches
belong under `target/cozy-video`; they are not a second source-managed
Storyboard.

## 9. Review, build, and external boundaries

Storyboard semantic, visual, and audiovisual approval remains an explicit human/AI
decision outside deterministic generation. A review declaration selects inputs;
neither its presence nor generated evidence claims quality approval.

### 9.1 Project declaration and P30-02 evidence contract

A video project MAY declare one root `storyboardReview` object with a required
safe project-relative direct regular `source`, optional `visualStory`, and
optional `visualPage`. Historical `approvedIdentity` and
`approvedEvidenceIdentity` properties are ignored for any JSON value; they
are not required, parsed, compared, or re-emitted. No replacement whole-model
proof, approval fingerprint, or new approval metadata framework is introduced.

`visualStory` selects a safe `evidenceDirectory` below `target/cozy-video`
and ordered duplicate-free `inputRefs` from the Storyboard's declared
diagram/asset references. The review-evidence command validates current source
syntax and selected direct safe files, requires the declared save directory,
copies the selected inputs, and writes deterministic review/handoff data.
Evidence retains schema, `status:validated`, source, scenes and optional
`visualInputs` containing `reference,source,path`; no content digests are
computed. V1 handoff contains only schema and evidencePath.
The typed result/current APIs carry file locations only.

Explicit review validation checks supported schema/status, required non-hash
fields, current declared inputs and existing direct visual copies. It does not
compare old evidence bytes, copied-input digests, renderer configuration, or
the full historical payload with current inputs. Valid source/material updates
can proceed; malformed documents, missing/unsafe actual inputs, invalid
PageSet/catalog/page/Binding, and missing effective renderer/part still reject.
Generation does not turn optional review state into a build prerequisite.
No hash value, including a historical approval value, grants semantic quality
approval. External reviewers retain that responsibility.

Saved scene, visual-input, and renderer entries retain their required non-hash
field types. Scene arrays are nonempty; visual-input references, renderer part
IDs, visual-page scene IDs, and per-page asset IDs must be unique. Generated
evidence readers positively select their known semantic fields at every object
and ignore unspecified properties of any JSON type. They use no named
historical-field exclusion mask. Visual-copy safety
includes every parent component below the evidence directory. These checks do
not compare historical scene content or renderer settings with current values.

### 9.1.1 P36-05 Storyboard-v2 Visual Page proof

Every Storyboard v2 review writes `cozy.video.storyboard-review-evidence.v2`
and `cozy.video.storyboard-handoff.v2`; this dispatch never upgrades a v1
Storyboard or alters its v1 evidence/handoff bytes. A v2 Storyboard containing
one or more `kind: visual-page` screens MUST declare exactly one closed
`storyboardReview.visualPage` object with exactly these required fields:
`binding` and `evidenceDirectory`. Historical `approvedEvidenceIdentity` is ignored. A v1
Storyboard, or a v2 Storyboard with no visual-page screen, MUST reject that
declaration rather than ignore it. Existing `visualStory` grammar remains a
separate optional visual-input route; when both declarations occur in v2 their
evidence directories must be identical.

`binding` is a safe project-relative direct regular non-symlink binding file.
For every visual-page screen, Cozy resolves the literal `source` and `catalog`
from the Storyboard source directory, loads exactly that VisualPageSet with
exactly that catalog, selects `pageId` exactly once, and validates the one
declared binding against that resolved set/catalog. It never infers a catalog,
page, binding, renderer, path, or identity. Generated evidence and handoff
`visualPages` entries are exactly `sceneId,kind,source,catalog,pageId,assets`.
Assets retain ordered `{id,path,mediaType,modifiedAt}` declarations/observations,
with full `FileTime.toString` precision. Current referenced safe files are
resolved directly. DM14 deletes the four VisualPage digests and copied Binding
proof; ordinary readers select only `sceneId,kind,source,catalog,pageId,assets`
and `id,path,mediaType,modifiedAt` respectively, retaining their required relative
key order and exact string types while ignoring arbitrary extras. An old VisualPage/assets/Binding snapshot
is not required to equal current inputs.

DM14 removes the review-local Storyboard/approval/evidence/handoff digests,
visual-copy digests and renderer digests together with historical full-payload
comparison gates. S04 also removes the general Storyboard identity API;
separate confirmation/runtime/publication contracts retain their own ownership.

For every project part selecting `storyboardReview.source`, Cozy selects its
effective `VideoRenderer` from the part override or project renderer. At least
one such part and one selected renderer per part are required for v2
visual-page review. Evidence and handoff record `effectiveRenderers` ordered
by part ID; each entry carries only partId and the selected renderer configuration. Renderer execution is not part of this proof route.
Generation parts need not share that review source: each build part instead
uses its own declared safe Storyboard source under the Phase 71 boundary.

The v2 evidence fields are `schema,status,source,scenes,visualInputs,
visualPages,effectiveRenderers`. Handoff fields are
`schema,status,evidencePath,visualInputs,visualPages,effectiveRenderers`.
Root reading selects the semantic names for the schema/label: v1 evidence
requires schema/status/source/scenes with optional visualInputs; v2 evidence and
handoff require their listed fields. An unspecified root collection, including
`scenes` on a handoff, is ignored and is not passed into row validation. Missing
required collections still reject. No top-level key-order constraint is added.
Scene rows require section/speaker/role/narration/caption/transition/direction
strings, numeric duration/leadSilence, and the existing discriminated screen
fields: v1 heading/content; v2 text kind/heading/content or visual-page
kind/source/catalog/pageId. Visual inputs require safe relative reference/source/
path strings and unique references. Effective renderers require unique nonempty
partId and configuration with every existing field present: engine, strategy,
policy, fps, width, height, crf, x264Preset, effectProfile. Positive projection
precedes the unchanged VideoRenderer decoding and domain checks, including its
existing null/value types. Extras do not make a configuration field optional.
Arbitrary extras are ignored at all these generated row/configuration levels.
Generated records remain deterministic and omit hashes; authored Storyboard,
Scene/Screen and configuration parsing, review declarations and approval
ownership are unchanged. VisualPage operational entries remain
structurally validated, but are not compared as historical currentness proofs.

The public CrossReview consumer still invokes `storyboardReviewCurrent`, resolves
actual live selected assets, compares material bytes across roots, protects all
participating inputs and atomically preserves its prior aggregate on failure.
Its generated root/slides/pages/assets/verification and Presentation
wrappers/slides use the same ordinary positive known-field principle while
retaining their existing required relative order and `not-recorded` approval
values. Timestamp-preserved valid Presentation edits are not promised historical
content detection; actual path/PNG/PPTX/OOXML/material QA remains. This direct
reader amendment adds no feature or Phase closure. SummarySlidesPdf S07,
Publisher/rsync, final named-integrity audit, English-PDF and complete isolated-site
drivers remain pending.

### 9.2 P30-03 Storyboard build and confirmation contract

A `parts[]` entry MAY use `storyboard` instead of the legacy `script` field.
The value is one safe project-relative `storyboard.md` or `storyboard.json`
path. A part MUST NOT declare both fields. A Storyboard part MAY declare
`storyboardSection`, a non-empty stable section token. When present, Cozy MUST
select exactly the scenes whose `section` equals that value, preserving their
Storyboard order, and MUST reject a selection with no scenes. When absent, it
selects all scenes. `storyboardSection` MUST NOT be used without
`storyboard`. Each part selects its own declared source; parts need not share a
source. Cozy parses the selected source as the typed v1 `Storyboard`, validates it, and derives its
renderer/narration execution projection only under
`target/cozy-video/storyboard/<part-id>/`. That generated projection is not a
source-managed `script.json` and does not authorize the legacy adapter.
Existing `parts[].script` remains the unchanged legacy route.

For a project containing one or more `parts[].storyboard` entries, `cozy
video build` requires `--mode confirmation` or `--mode final`. `confirmation`
writes its video and manifest below `target/cozy-video/confirmation/`; it
MUST NOT write the project final output. Its canonical manifest has schema
`cozy.video.confirmation.v2`, status `validated`, a descriptive output path,
selected part/source paths, declared handoff and part-artifact paths, frozen
mode/settings projections, and a successful ffprobe summary. Native mode
records and handoffs MUST NOT carry local cache, source, handoff, output, or
manifest hash identities. S04 removes general v1/v2 Storyboard semantic
hashes; separately owned audio/integrity contracts retain their own scope.

`video.yaml` MAY retain one root `confirmationReview` object with the exact
single field `approvedIdentity` as historical review metadata. `final` does
not read it and does not require confirmation output or a confirmation manifest.
Well-formed optional stale or mismatched confirmation metadata is not an
implicit approval and is irrelevant to generation. A malformed legacy
declaration may still be rejected by the transitional project decoder until
the separately assigned codec work removes that schema handling.

`final` writes the project `output` and a mode-specific manifest below
`target/cozy-video/final/`; it never overwrites confirmation output or its
manifest. Its native v2 record is descriptive only. Native assembly applies
the common generation `FileTime` policy independently to every declared safe
input and every required final, mode-manifest, handoff, part-artifact, and
applicable credit product. Reuse requires each product to be current, a safe
direct nonempty artifact set, and a read-only ffprobe with a finite positive
duration and video stream. Source-body, narration-body, pronunciation-note,
and generated-digest equality are not native cache criteria. Missing,
invalid, old-schema, unsafe, absent, or stale products regenerate.

`cozy video review-evidence` remains the independent final-MP4 evidence
command. A video-derived PPTX is an optional external Dox/PPTX consumer
artifact: Cozy may provide the deterministic evidence and handoff inputs, but
does not generate, accept, or use that PPTX as a confirmation or final gate.

Confirmation and final output are separate lifecycle states. Native
regeneration stages the complete bounded product set, validates the staged
video, then uses atomic per-file replacement only. For ordinary installation
errors it restores an existing destination's staged byte/mode/mtime backup or
removes an absent-before destination; unrecoverable rollback retains its
staging recovery evidence. This is neither a crash-safe transaction nor a
concurrency/receipt framework. Final rendered-video review evidence remains
an independent gate; a video-derived review PPTX is an optional external
hand-off/distribution artifact and never changes authored Storyboard content or
final-review state by itself.

Dox/PPTX generation, SmartDox/Textus consumer acceptance, publication,
deployment, and external repository mutation are outside Cozy’s ownership and
outside this Phase 30 specification Slice. Cozy may emit normalized evidence
and hand-off data for those consumers, but no external runtime acceptance is
claimed here.

## 10. Acceptance properties for later implementation

Later implementation and executable specifications MUST establish that:

1. equivalent restricted Markdown and canonical JSON normalize to the same
   typed model and canonical JSON;
2. every field in Section 2, including empty values and ordering, survives
   both conversion directions without semantic loss;
3. every rejection in Section 6 is deterministic and diagnostic;
4. legacy `script.json` and `parts[].script` remain distinct unless an
   explicitly accepted migration adapter is selected;
5. explicit review validates actual named Storyboard and optional visual inputs
   without historical approval hashes; declared source, tool, credit and output
   safety remain build validation; and
6. no source-managed `script.json` is required on the new Storyboard path,
   while the legacy path remains available pending migration acceptance.

## 11. Storyboard v2 Visual Page coexistence boundary

`cozy.video.storyboard.v1` / `version: 1` remains byte- and semantically
unchanged. Its Screen is exactly `{heading,content}`; all v1 JSON and
restricted Markdown behavior remain accepted, and no v1 parser auto-upgrades
or reinterprets a screen.

`cozy.video.storyboard.v2` / integer `version: 2` is JSON only. It retains
every v1 Scene field and their existing narration, speaker, duration, lead
silence, transition, confirmation/final, and audiovisual-review semantics.
Its closed `screen` discriminator is exactly one of:

```json
{ "kind": "text", "heading": "...", "content": "..." }
```

```json
{ "kind": "visual-page", "source": "pages.json", "catalog": "catalog.json", "pageId": "overview" }
```

Mixed, missing, unknown, and wrongly typed screen fields are rejected. The
second form requires nonempty normalized descriptor-relative forward-slash
`source` and `catalog` paths, resolved from the Storyboard source directory.
They reject absolute paths, traversal, URI/query/fragment/control characters,
backslashes, non-normalized forms, final symbolic links, symbolic-link
ancestors, and nonregular files. Cozy loads the source as a VisualPageSet using
the specified catalog and requires `pageId` to match exactly one page. It does
not infer a catalog, binding, renderer, or any resolved identity.

Canonical v2 JSON preserves the existing root and Scene field order. A v2
screen uses the displayed field order and preserves the literal
`source`, `catalog`, and `pageId` reference as typed source data. Video planning projects that same reference
into existing scene planning metadata; it does not invoke a renderer or change
the ownership of narration, timing, transition, confirmation/final, or
audiovisual review.

The sole cross-version adapter is:

```text
cozy video storyboard migrate --from v1 --to v2 --screen text <input> --save <output.json>
```

It accepts v1 JSON or restricted Markdown, writes only canonical v2 JSON text
screens, and preserves every other v1 Scene value. It neither overloads
`convert` nor accepts a downgrade. A v2 visual-page-to-v1 request fails with
`VISUAL_PAGE_SCREEN_LOSSY` before an output is written.

P36-04 does not create video renderer output, Visual Page binding, video
receipt/review evidence, external SmartDox/Textus work, publication, or
deployment. Those responsibilities remain separately scoped.

### Cross-media Review consumption

CrossReview consumes current presentation and validated Storyboard-v2 inputs.
Actual per-side references, declared ordered asset IDs/media types, and safe file
bytes are checked by the existing operation. No PageSet/catalog/logical/page/
Binding historical or cross-medium snapshot equality is required. Aggregate
assets remain `{id,mediaType}`; removed proof properties are ignored for any
JSON value. Saved verification enforces structure, uniqueness, and references,
not whole aggregate equality. Build may atomically replace a safe existing
aggregate while protecting actual inputs/review evidence and prior bytes on
failure. It does not render, publish, or approve semantic/visual/audiovisual
content. Media receipt admission and non-review artifact integrity remain
separate responsibilities.

## 12. Phase 61 legacy dialogue authoring compatibility

This Section defines only the legacy dialogue `script.json` authoring contract.
It does not make a legacy script a Storyboard or alter either accepted
Storyboard contract.

### 12.1 Requested post-utterance silence

Legacy `VideoScene.tailSilence` is an optional finite nonnegative JSON number
of seconds. It is an authored requested post-utterance interval. Omission and
legacy `null` mean that there is no explicit request and have the semantic
default of zero. A nested child that omits `tailSilence` inherits its parent
request; a child that explicitly supplies `0` overrides the inherited request.
Malformed, negative, or non-finite supplied values MUST fail decoding and MUST
NOT be silently coerced.

The established legacy target selection is unchanged: explicit `duration`
precedes `targetDuration`, which precedes the eight-second default. Appending
the optional field to `VideoScene` preserves existing positional source calls.

### 12.2 Speech-only middle-dot normalization contract

`voiceTextNormalization.removeMiddleDots` is an optional boolean whose default
is `false`. When enabled, it acts only on final provider speech after the
existing whitespace normalization and the single-pass pronunciation dictionary.
It removes only U+30FB (`・`). It MUST leave narration, line, caption,
headings, and every other source or display string unchanged. A malformed or
nonboolean configuration MUST fail before provider I/O. This contract does not
add recursive dictionary processing or any broader punctuation normalization.

The [compound-narration contract](video-compound-narration.md) specifies the
existing dictionary/display boundary and repeated public synthesis, with
actual private-driver provider evidence separate from listening acceptance.

### 12.3 Requested versus generated effective timing

Let `T` be the established selected target duration, `L` the lead silence,
`A` the actual normalized WAV duration, and `E` the authored requested
`tailSilence`. The successor timing implementation MUST use exactly:

```text
effectiveSceneDuration = max(T, L + A + E)
effectiveTrailing = max(E, T - L - A, 0)
```

The existing generated audio-manifest `tailSilence` remains the effective
output interval, not the authored requested input. Phase 61.1 realizes that
timing from the actual normalized WAV and carries both values through Remotion
props and deterministic review evidence.

The Cozy-only executable acceptance fixture uses Article-9-shaped Japanese
source and display text containing U+30FB (`・`) with a deterministic local
provider. It proves that only provider speech loses U+30FB, while source,
line, and caption remain unchanged. With a requested `0.8`-second final tail,
the fixture separately proves requested and effective values, final
visual/caption persistence through the effective tail, and audio/mouth
activity ending at the actual WAV boundary. Its selected tail still frame is
visual/timing evidence only; it is not listening acceptance.

The same fixture proves that the independently configured five-second summary
infographic and credits cards remain separate holds and do not inherit or
duplicate the final scene tail. It performs no external-provider call, external
Article 9 project acceptance, publication, upload, or deployment.

The historical Phase 61 fixture preserves Storyboard v1/v2 strict schemas
and canonical serialization; S04 subsequently removes the general identity API. This Slice also does not change their historical generated audio
manifests or any render, evidence, or currentness behavior. Storyboard and
pipeline conversion that carries this legacy contract is Phase 61.1 work.
