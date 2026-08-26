# Cozy Video Storyboard Specification

Status: NORMATIVE; Phase 30 is IN PROGRESS

This document is the authoritative functional specification for
`cozy.video.storyboard.v1`. It freezes the semantic contract for the later
internal Phase 30 Steps; it does not claim Scala code, CLI implementation,
executable specifications, validation, review, commit, or completion.

The words **MUST**, **MUST NOT**, **SHOULD**, and **MAY** are normative. The
corresponding responsibility and ownership design is in
[`docs/design/video-storyboard.md`](../design/video-storyboard.md).

## 1. Scope and semantic identity

The Storyboard is the single content contract for Cozy video production. A
human-authored restricted `storyboard.md` and an external machine-authored
`storyboard.json` are two serializations of one typed semantic model. They
MUST normalize to the same model and semantic identity when their defined
values are equivalent. Formatting, key order in input JSON, line endings, and
the choice of representation MUST NOT change that identity.

The public schema identifier is exactly `cozy.video.storyboard.v1`. The root
model MUST contain both `schema` and integer `version` fields with values
`cozy.video.storyboard.v1` and `1`. There is no second v1 Storyboard schema.
`schema` and `version` are part of the semantic identity.

The normalized identity is the `sha256:`-prefixed SHA-256 digest of the UTF-8
canonical JSON serialization of the typed model. Canonical JSON has the field
order specified in Section 3, preserves array order, uses no insignificant
whitespace, and writes timing decimals in their normalized decimal form. The
source filename, Markdown formatting, and filesystem location are not part of
the identity; referenced path strings are part of it.

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
             -> validation -> canonical normalization -> semantic identity
```

Parsing and validation happen before conversion output is written. Conversion
between Markdown and JSON is semantic and lossless: every required field,
empty value, scene order, nested-array order, and safe reference is retained.
Formatting is allowed to change, but no semantic value may be silently
discarded, inferred from another field, or rewritten. Converting an already
normalized file twice MUST produce byte-identical canonical output and the
same semantic identity. Equivalent Markdown and JSON MUST yield byte-identical
canonical JSON and the same identity.

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

## 8. Intended Cozy command contracts

These command contracts define the later CLI surface; they do not claim that
the commands exist in the current implementation.

| Command | Contract |
| --- | --- |
| `cozy video storyboard validate <storyboard.md\|storyboard.json>` | Parse, validate, normalize, and report the semantic identity and structured diagnostics. It MUST not build video or mutate a source Storyboard. |
| `cozy video storyboard inspect <storyboard.md\|storyboard.json>` | Perform the same safe parse/validation and display normalized schema/version, scene order, field summary, references, and identity without changing semantic content. |
| `cozy video storyboard convert <input> --save <output.md\|output.json>` | Validate first, then write deterministic canonical output. A legacy input requires an explicit adapter selection and diagnostic migration report. Failed validation MUST not produce a claimed Storyboard output. |
| `cozy video storyboard review-evidence <video-project> --save <dir>` | Create deterministic content-review evidence from the approved normalized Storyboard, recording its identity and any optional visual-story/diagram/asset identities. It MUST reject stale or unapproved input. |
| `cozy video build <video-project> --mode confirmation` | Consume the approved normalized Storyboard selected by the project and create a confirmation output with a distinct identity/output location. It MUST reject a missing, stale, changed, or unapproved Storyboard. |
| `cozy video build <video-project> --mode final` | Consume the approved normalized Storyboard and accepted confirmation state, create a separate final output, and never overwrite confirmation output. It MUST apply the same identity and stale-input gates. |

The project’s `video.yaml` remains separate production configuration. It may
select the Storyboard and configure characters, narration provider, renderer,
effects, credits, and project-owned assets, but those concerns are not copied
into each Storyboard scene. Generated handoff, manifests, evidence, and caches
belong under `target/cozy-video`; they are not a second source-managed
Storyboard.

## 9. Review, build, and external boundaries

Storyboard approval is a content gate, not audiovisual acceptance. Optional
image-backed visual-story evidence is a projection of normalized data and is
required only when diagrams, assets, frames, layout, or image selection need
separate inspection. When requested, its input identity MUST include the
Storyboard identity and the identities of admitted visual inputs; changed
inputs make the evidence stale.

### 9.1 Project declaration and P30-02 evidence contract

A video project MAY declare one root `storyboardReview` object. Its required
`source` is a safe project-relative direct regular Storyboard file and its
required `approvedIdentity` is the exact `sha256:<lowercase-hex>` identity of
the normalized Storyboard that a human has approved. The declaration is an
explicit approval record; a current source whose identity differs from
`approvedIdentity` is unapproved and MUST be rejected before evidence or a
build gate can claim it current.

`storyboardReview.visualStory` is optional. Its presence means that the
project explicitly requests image-backed visual review. It contains:

- `evidenceDirectory`: a safe project-relative directory below
  `target/cozy-video` where Cozy writes the derived package;
- `inputRefs`: an ordered, duplicate-free subset of the selected Storyboard's
  `diagramRefs` and `assetRefs`; and
- optional `approvedEvidenceIdentity`: the exact identity of a previously
  inspected visual-story evidence package.

`cozy video storyboard review-evidence <video-project> --save <dir>` requires
the current Storyboard identity to equal `approvedIdentity`. When
`visualStory` is present, `--save` MUST equal its `evidenceDirectory`; every
selected input reference MUST be a present, direct regular non-symlink file
contained by the project. Cozy writes `review-evidence.json`, copied visual
inputs below `visual-inputs/`, and `handoff.json`. `review-evidence.json` has
schema `cozy.video.storyboard-review-evidence.v1`, status `validated`, the
Storyboard source path and identity, ordered scene review data (section,
speaker, role, narration, screen heading/content, caption, timing, direction),
and visual-input records only when requested. Its `identity` is the
`sha256:` digest of the canonical evidence payload excluding that identity
field. `handoff.json` has schema `cozy.video.storyboard-handoff.v1` and carries
only the evidence path, evidence identity, Storyboard identity, and optional
visual-input identities for a Dox/PPTX consumer; Cozy neither creates nor
accepts that consumer's artifact.

A requested visual review is current only when the evidence package exists,
revalidates against the current approved Storyboard and selected input hashes,
and its identity equals `approvedEvidenceIdentity`. A missing approval, missing
evidence, changed input, missing input, malformed record, or identity mismatch
MUST fail closed. The confirmation/final build gate introduced in P30-03 uses
this validator; the existing build path also applies it whenever a project
already declares `storyboardReview`. Without `visualStory`, a valid normal
Storyboard approval is sufficient and no visual evidence or handoff is
required.

### 9.2 P30-03 Storyboard build and confirmation contract

A `parts[]` entry MAY use `storyboard` instead of the legacy `script` field.
The value is one safe project-relative `storyboard.md` or `storyboard.json`
path. A part MUST NOT declare both fields. A Storyboard part MAY declare
`storyboardSection`, a non-empty stable section token. When present, Cozy MUST
select exactly the scenes whose `section` equals that value, preserving their
Storyboard order, and MUST reject a selection with no scenes. When absent, it
selects all scenes. `storyboardSection` MUST NOT be used without
`storyboard`. This lets one source-managed Storyboard bind distinct sections
to distinct video parts without duplicating source content. Cozy parses the
selected source as the typed v1 `Storyboard`, validates it, and derives its
renderer/narration execution projection only under
`target/cozy-video/storyboard/<part-id>/`. That generated projection is not a
source-managed `script.json` and does not authorize the legacy adapter.
Existing `parts[].script` remains the unchanged legacy route.

For a project containing one or more `parts[].storyboard` entries, `cozy
video build` requires `--mode confirmation` or `--mode final`. `confirmation`
writes its video and manifest below `target/cozy-video/confirmation/`; it
MUST NOT write the project final output. Its canonical manifest has schema
`cozy.video.confirmation.v1`, status `validated`, an identity, the normalized
Storyboard identities in part order, the effective renderer/narration and
production configuration identities, and the confirmation video hash. The
manifest identity is the `sha256:` digest of its canonical payload excluding
its `identity` field.

`video.yaml` MAY declare one root `confirmationReview` object with the exact
single field `approvedIdentity`. It is a human approval record for the current
confirmation manifest identity, not an external consumer result. `final`
MUST fail closed unless the confirmation video and manifest are present,
current for the selected approved Storyboard and production inputs, and their
manifest identity equals `confirmationReview.approvedIdentity`. A malformed,
missing, stale, or mismatched record is not an implicit approval.

`final` writes the project `output` and a mode-specific manifest below
`target/cozy-video/final/`; it never overwrites confirmation output or its
manifest. Each mode manifest records its mode, output hash, normalized
Storyboard identities, and complete cache-input identities. Audio and render
cache reuse is permitted only when that complete identity matches exactly;
otherwise the affected generated handoff, audio, or render chunk is
invalidated deterministically.

`cozy video review-evidence` remains the independent final-MP4 evidence
command. A video-derived PPTX is an optional external Dox/PPTX consumer
artifact: Cozy may provide the deterministic evidence and handoff inputs, but
does not generate, accept, or use that PPTX as a confirmation or final gate.

Confirmation and final output are separate lifecycle states. Audio and render
chunks MAY be reused only when their scene inputs, Storyboard identity,
narration inputs, renderer settings, and relevant asset identities all match;
otherwise they MUST be deterministically invalidated. Final rendered-video
review evidence is an independent gate. A video-derived review PPTX is an
optional external hand-off/distribution artifact and never changes Storyboard
identity or final-review state by itself.

Dox/PPTX generation, SmartDox/Textus consumer acceptance, publication,
deployment, and external repository mutation are outside Cozy’s ownership and
outside this Phase 30 specification Slice. Cozy may emit normalized evidence
and hand-off data for those consumers, but no external runtime acceptance is
claimed here.

## 10. Acceptance properties for later implementation

Later implementation and executable specifications MUST establish that:

1. equivalent restricted Markdown and canonical JSON normalize to the same
   typed model and semantic identity;
2. every field in Section 2, including empty values and ordering, survives
   both conversion directions without semantic loss;
3. every rejection in Section 6 is deterministic and diagnostic;
4. legacy `script.json` and `parts[].script` remain distinct unless an
   explicitly accepted migration adapter is selected;
5. approved Storyboard identity and optional visual-input identities gate
   evidence, confirmation, final build, and cache reuse; and
6. no source-managed `script.json` is required on the new Storyboard path,
   while the legacy path remains available pending migration acceptance.
