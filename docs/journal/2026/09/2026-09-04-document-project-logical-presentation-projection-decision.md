# Document Project Logical Presentation Projection Decision

Date: 2026-09-04

Related development item: `DEV-017`

Planned phases: Phase 46 and Phase 46.1

## Trigger

The SimpleModeling.org Article 8 Document Project produced an
`article-review.html` whose file identity and receipt were current but whose
page cards displayed placeholder messages instead of the intended content.

The real Content Core used `visibleText` arrays, `visualIntent`, and
`articleHeading`. The review renderer reparsed a generic JSON object and
looked for a different hand-maintained set of string aliases. Its executable
specification used those renderer aliases rather than the Article 8 canonical
input. Unsupported semantic fields were therefore ignored without failing the
generation operation.

## Recovered original intent

The intended feature was not merely a page-by-page text preview. Content Core
was to make two kinds of logic explicit:

- the **Story Flow** across the complete document, slides, and video; and
- the **Explanation Structure** inside each explanation unit.

Those structures were to be patternized and connected to deterministic visual
generation. Slides and video were to share the same meaning while applying
different pagination, scene, timing, narration, and renderer decisions.

Phase 36/37 already contain most of the required semantic vocabulary:
Explanation Pattern and ordered Steps for the global flow, and Logical Pattern,
semantic nodes, and typed Relations for each local structure. Phase 41 already
defines an integrated review view. Phase 45 introduced Content Core and
Document Project workflow. The missing part is a typed, executable connection
among those accepted contracts.

## Why the earlier requirement did not prevent the defect

The article-review requirement was present in the journal, but it was not
closed through all four links below:

```text
requirement
  -> canonical typed input vocabulary
  -> renderer consuming the same type
  -> executable spec using the real vocabulary and driver
```

Currentness and receipt checks proved that the renderer consumed the recorded
files. They did not prove semantic completeness. The synthetic test proved
HTML mechanics using `intent`, `media`, and `emphasis`; it did not prove that
every declared Article 8 Story Step, reader-facing text, visual intent, and
article relation appeared in the output.

The permissive generic-map implementation turned a contract mismatch into a
successful placeholder result. This is the same failure class previously
observed when several loosely related parameters were copied across workflow
boundaries: the compiler could not protect the aggregate invariant.

## Decision

Add a successor development sequence instead of reopening or rewriting the
history of closed Phases 36, 37, 41, or 45.*.

1. Content Core becomes the shared semantic authoring authority for Story Flow
   and Explanation Structures.
2. Existing Phase 36/37 typed values are reused; no parallel loose page grammar
   is introduced.
3. One immutable typed aggregate crosses normalization, projection, receipt,
   and review boundaries.
4. Unknown or lossy semantic input fails with a structured diagnostic. A
   declared semantic value is never silently replaced by success-path
   placeholder text.
5. A versioned policy deterministically selects compatible Visual Patterns
   without putting layout coordinates or medium-specific objects in Content
   Core.
6. Slides and video are separate projections from the same accepted semantics.
7. One integrated confirmation HTML shows Story Flow, per-unit Explanation
   Structure, selected Visual Pattern, and article/slide/video mappings.
8. Executable Specifications use both canonical typed fixtures and the real
   Article 8 vocabulary and prove semantic coverage, not only output identity.

## Phase split

The work is split around the accepted public-contract boundary and the target
six-hour planning unit.

- Phase 46 owns the expensive semantic contract and typed normalization
  kernel. Recommended parent profile: `gpt-5.6-terra / high`.
- Phase 46.1 consumes the frozen kernel for deterministic slide/video/review
  projection and Article 8 acceptance. Recommended parent profile:
  `gpt-5.6-luna / xhigh`, with a pinpoint Terra high review only if a public
  contract ambiguity is discovered.

The split concentrates high-cost reasoning in Phase 46 and keeps projection
execution in the less expensive successor without weakening validation or
review guarantees.

## Phase 46 contract-boundary resolution

On 2026-09-04, the user selected
`P46-DEC-CONTRACT-001: new-versioned-content-core`.

Phase 46 therefore defines `cozy.content-core.presentation-semantics.v2` as
the new, closed versioned input and normalized-aggregate boundary. The closed
`cozy.content-core.v1` shape remains historical authority for its exact
identity fields; it does not gain semantic fields. Its adapter is directional:
it binds the exact v1 identity to an explicitly supplied v2 semantic document,
and rejects unknown or unmapped semantic values rather than providing aliases,
defaults, or a permanent permissive reader.

The v2 boundary must use the existing Phase 37 `CompositionStep` identity for
Story Flow and the existing Phase 36 logical Pattern/node/typed-Relation graph
for every bound Explanation Structure. A Structure may be explicitly bound to
one Story Step, but it may not carry a second independently interpreted graph.
Story transitions are explicit typed `StoryTransition` values between Story
Step identities; they use the reused closed Relation vocabulary as a
`relationType`, rather than becoming Phase 36 local Relation instances or a
new string catalog. The exact field spelling and Scala representation remain Phase 46
design outputs, but these ownership and identity rules are fixed before code.

## Non-decisions

This decision does not select final YAML field spelling, reopen prior accepted
schemas, approve automatic semantic inference, publish an Article 8 artifact,
or authorize deployment, upload, push, or external-service mutation. Exact
normative schema and command grammar are Phase 46 design outputs.
