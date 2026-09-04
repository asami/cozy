# Document Project Presentation Semantics

Status: Phase 46 normative specification

## Boundary

`cozy.content-core.v1` remains closed historical Content Core. Its exact fields
are `schema`, `id`, `language`, and `accepted`; its candidate, feedback, and
accept workflows are unchanged. Phase 46 adds the strict sibling input
`cozy.content-core.presentation-semantics.v2`. It is directional: an explicit
v2 document binds an already admitted v1 Core and never extends, migrates,
scans, defaults, or compatibility-reads v1.

There is no descriptor, CLI, scaffold, renderer, receipt, or output change at
this boundary.

## V2 shape

The v2 root admits these ordered fields only:

```text
schema, id, contentCore, composition, storyFlow, structures, projectionPolicy
```

`contentCore` is exactly `{id, language, identity}`. `identity` is
`sha256:<64 lowercase hexadecimal characters>` over the direct v1 Core file
bytes, and every field must exactly equal the admitted v1 Core binding.

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

## Normalization and currentness

The immutable `Validated` aggregate retains the admitted v1 binding, canonical
v2 semantic identity, validated Composition, normalized Plan, explanation and
presentation catalog identities, policy identity, declared source and asset
identities, structures, visual selections, and currentness identity.

Canonical identities are derived from fixed-order normalized content, not YAML
mapping order. Unordered structure, transition, override, policy-binding, and
parameter collections use stable canonical ordering; reader-facing text and
the ordered Phase 37 Plan retain their semantic order. A v1 binding, v2
semantics, Composition, catalog, policy, declared source, or declared asset
identity change changes the relevant aggregate/currentness identity.

## Closed diagnostics

All new failures are `PresentationSemanticsFault` diagnostics:

| Code | Meaning |
| --- | --- |
| `DP-SEM-001` | required direct input is missing, unsafe, unreadable, or malformed |
| `DP-SEM-002` | fields are missing, unknown, duplicate, or not in the required root order |
| `DP-SEM-003` | schema value is unsupported |
| `DP-SEM-004` | scalar type, token, text, identity, medium, or revision is invalid |
| `DP-SEM-005` | v1 Core shape/binding does not exactly match the v2 binding |
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
pagination, scene timing, narration, HTML, PDF, output bytes, and receipts.
Phase 46 produces none of those artifacts: no HTML, slides, video, PDF,
receipt, or generated output is produced here.
