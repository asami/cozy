# Document Project Presentation Semantics

Status: Phase 46 normative specification amended by Phase 71.5 S05

## Boundary

`cozy.content-core.v1` remains closed historical Content Core. Its exact fields
are `schema`, `id`, `language`, and `accepted`; its candidate, feedback, and
accept workflows are unchanged. Phase 46 adds the strict sibling input
`cozy.content-core.presentation-semantics.v2`. It is directional: an explicit
v2 document binds an already admitted v1 Core and never extends, migrates,
scans, defaults, or compatibility-reads v1.

The existing Phase 48 public scaffold is the only scaffold/output
clarification at this boundary: it writes a strict v2 authored sibling at
`content/presentation-semantics-<language>.yaml` for exactly the public
profiles `standard`, `standard-video`, `bok`, and `bok-video`. The sibling's
`contentCore` associates the generated Core's semantic id and language. This generated file is an authoring-incomplete scaffold surface,
not accepted or validated semantics, and does not claim a valid V2 document.
It does not change the Phase 46 schema, diagnostics, or validation boundary,
or the fixed Phase 46.1 semantic renderer/profile and coverage boundary.

## V2 shape

The v2 root admits these ordered fields only:

```text
schema, id, contentCore, composition, storyFlow, structures, projectionPolicy
```

`contentCore` is exactly `{id, language}`. Both fields must exactly match the
admitted closed v1 Core's semantic id and language. Direct Core bytes are read
and parsed once with strict UTF-8 and ordinary structured-document loading;
there is no parallel parsed-Core argument or retained byte certificate.

`composition` is one exact `cozy.explanation-composition.v1` document. It is
parsed into existing Phase 37 `CozyExplanation.Composition`, validated against
the admitted explanation and presentation catalogs, and expanded to the
normalized Phase 37 `Plan`. There is no second Composition model.

`storyFlow` is exactly `{id, transitions}`. A transition is exactly
`{id, relationType, fromStepId, toStepId}` and normalizes to the distinct
`StoryTransition` type. Both endpoints are Phase 37 `CompositionStep` IDs.
The only relation types are `next`, `causes`, `depends-on`, `enables`, and
`maps-to`. IDs and semantic `(relationType, fromStepId, toStepId)` tuples are
unique; endpoints resolve and self-links are rejected. A `StoryTransition` is
not a Phase 36 local `Relation`.

`structures` is a nonempty vector. Each object is exactly
`{id, storyStepId, article, visualOverrides}`. It binds one CompositionStep and
reuses that step's Phase 36 Logical graph; it cannot carry an independent
pattern, node, or relation graph. `article` is exactly
`{articleHeading, visibleText, visualIntent}`. The heading and intent are
nonempty trimmed text; visibleText is a nonempty vector of nonempty trimmed
text. `intent`, `media`, `emphasis`, and all other renderer aliases are
unknown fields.

An override is exactly `{medium, visual}`. Medium is `article`, `slides`, or
`video`, with at most one override per medium. `visual` is a strict existing
Phase 36 `Visual` value and cannot introduce renderer material.

`projectionPolicy` is exactly `{schema, id, revision, bindings}` with schema
`cozy.content-core.projection-policy.v1`. A binding is exactly
`{medium, logicalPattern, visual}`. It supplies one compatible base visual for
each selected `(medium, inherited logicalPattern)`. Missing or duplicate base
bindings fail. A compatible override replaces its matching base only; it
cannot invent a medium or bypass Phase 36 compatibility.

## Typed normalization and workflow state

The immutable `Validated` aggregate retains id, semantic Core id/language,
validated Composition, normalized Plan, Projection Policy id/revision/bindings,
required source and asset declarations, StoryFlow, Structures, and selected
Visual values. These typed values represent actual admitted input semantics.
Nested Composition sources emit only `{id}` and assets only `{id,mediaType}`.

Unordered Structure, transition, override, policy-binding, and parameter
collections use stable ordering; reader-facing text and the Plan retain their
semantic order. Current named catalogs validate graph and Visual choices.
Upstream Explanation Composition/Plan/catalog hash removal remains separate
finite work; this aggregate adds no semantic, catalog, policy, or currentness
fingerprint.

Project state is `missing`, `authoring-incomplete`, `invalid`, or `current`.
Current means the actual input semantics were admitted. Authoring-incomplete
requires the closed scaffold shape: expected schema and semantic/Core ids and
language, empty composition, canonical StoryFlow id and empty transitions,
empty structures, and supported policy schema/id/revision 1 with empty bindings.
All relevant key sets remain closed. Text equality does not determine this
ordinary authoring stage. All other rejected candidates remain invalid.

## Closed diagnostics

All new failures are `PresentationSemanticsFault` diagnostics:

| Code | Meaning |
| --- | --- |
| `DP-SEM-001` | required direct input is missing, unsafe, unreadable, or malformed |
| `DP-SEM-002` | fields are missing, unknown, duplicate, or not in the required root order |
| `DP-SEM-003` | schema value is unsupported |
| `DP-SEM-004` | scalar type, token, text, medium, or revision is invalid |
| `DP-SEM-005` | v1 Core shape is malformed or semantic id/language does not match the v2 association |
| `DP-SEM-006` | nested Phase 37 Composition or admitted catalog validation fails |
| `DP-SEM-007` | StoryTransition is unsupported, duplicated, self-linked, or unresolved |
| `DP-SEM-008` | Structure, article vocabulary, override, or Step binding is invalid |
| `DP-SEM-009` | Projection Policy shape, vocabulary, or base-binding uniqueness is invalid |
| `DP-SEM-010` | a supplied Visual is not a strict compatible Phase 36 Visual value |
| `DP-SEM-011` | a required deterministic base selection is missing or ambiguous |

No declared semantics can become a successful placeholder.

## Phase 46.1 handoff limit

Phase 46.1 may consume only this normalized aggregate to implement slide,
video, and integrated confirmation projections. It owns renderer mapping,
pagination, scene timing, narration, HTML, PDF, and output bytes.
Phase 46 produces none of those artifacts: no HTML, slides, video, PDF,
or generated output is produced here.

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
