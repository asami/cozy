# Phase 58.1: Document and Summary Description DSL Vertical Slice

Status: IN_PROGRESS

Plan date: 2026-09-12

Development item: DEV-025

Predecessor: [Phase 58](phase-58.md)

## Goal

Establish media-independent Document Description and Summary Description DSLs
over the recursive Content Core, then prove with the real SimpleModeling.org
Article 9 content that complete prose and summary-slide-level content can be
authored, traced, reviewed, and rendered without treating an HTML format
binding as the document authority.

## Phase Plan Gate

Phase Plan Gate: PROCEED

- target: one executable vertical slice; preferred 4–8 h band
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / high
- expensive_reasoning_kernel: authority separation among Core, complete
  document prose, concise summary, and medium-specific projection
- estimated_at_recommended_profile: 7–8 h
- incoming_handoff: completed Phase 58 recursive Core plus the 2026-09-12
  Document/Summary DSL decision
- frozen_outcome: two typed DSL authorities, two Article 9 Japanese drivers,
  and two deterministic review HTMLs
- short_child_exception: none
- source: user-approved Phase 58.1 creation on 2026-09-12

## In-scope work

| ID | Outcome | Status |
| --- | --- | --- |
| P581-01 | Define paired design/specification contracts for the Document Description and Summary Description DSLs, including exact Core/Document identities, locale, stable references, closed block/unit vocabularies, and the renderer/chrome boundary. | complete |
| P581-02 | Implement strict typed loading, validation, canonical identity, reference coverage, and deterministic rejection for both DSLs without a permissive Phase 58 format adapter. | planned |
| P581-03 | Author the real Article 9 Japanese Document Description and generate one self-contained deterministic document-review HTML with exact Core traceability. | planned |
| P581-04 | Author the real Article 9 Japanese Summary Description and generate one self-contained deterministic summary-review HTML with exact Core and Document traceability. | planned |
| P581-05 | Prove semantically distinct Logical Patterns, typed Relations, child-Step Flow, currentness, and deterministic output through focused specifications, independent review, full validation, and release closure. | planned |

## Authority boundary

- `content/core.yaml` remains the locale-independent recursive logic-tree
  authority accepted by Phase 58.
- `content/<locale>/document.yaml` is the complete localized document
  authority. It owns section organization, prose blocks, and exact references
  to the Core meaning it realizes.
- `content/<locale>/summary.yaml` is the localized concise-content authority.
  It owns selection, order, wording, and emphasis appropriate for a
  summary-slide-level explanation.
- Summary binds the exact Core and Document Description identities. It is not
  silently inferred or automatically accepted from either authority.
- The locale directory is an organization convention. Each localized DSL
  declares a validated BCP-47 locale explicitly.
- Renderer chrome, CSS, coordinates, fonts, navigation, print rules, and
  medium-specific parameters do not belong in either authoring DSL.
- Generated review HTML is evidence and inspection output, never semantic
  authority.

## Semantic structure requirements

- Document sections and content blocks have stable identities and exact Core
  Step/claim/node/Relation/Flow references where applicable.
- Summary units have stable identities and exact selected Core references;
  one unit may normally map to one summary slide, but pagination remains a
  projection decision.
- Article 9 uses available Logical Patterns and typed Relations according to
  meaning. Acceptance must not flatten every Structure and transition to
  `sequence` and `next` merely to satisfy coverage.
- Child-Step Flow and Step-local Structure remain different semantic levels
  and receive visibly different reader-facing projections.
- Pattern, Relation, node-role, and semantic-role identifiers remain stable
  structural values; reader-facing labels come from validated locale-owned
  content or Cozy locale resources rather than raw identifiers.

## Required outputs

1. Paired design and Markdown specification documents for both DSLs.
2. Strict typed codecs and reference/identity validation.
3. Article 9 `content/ja/document.yaml`.
4. Article 9 `content/ja/summary.yaml`.
5. A deterministic self-contained document-review HTML.
6. A deterministic self-contained summary-review HTML.
7. Executable specifications demonstrating multiple semantically warranted
   Logical Patterns and Relation types in the real driver.

## Closure criteria

- Core, Document Description, Summary Description, and renderer/profile
  responsibilities are non-overlapping and specified consistently.
- Unknown fields, duplicate identities, invalid locales, stale upstream
  identities, unresolved Core references, incomplete bindings, and lossy
  source input fail closed.
- The document review exposes the complete composed prose and its Core
  traceability without presenting diagnostic tables as the primary content.
- The summary review exposes concise units, selection, order, emphasis, and
  exact Core/Document traceability without becoming slide-layout IR.
- The Article 9 acceptance driver visibly distinguishes Step Flow from local
  logical Structure and exercises more than `sequence`/`next` where the
  article meaning requires it.
- Focused validation, independent full Phase review, full Cozy validation,
  and a distinct Phase release commit close the Phase.

## Exclusions

- Reopening or rewriting completed Phase 46, Phase 46.1, or Phase 58 history.
- Renaming `format-ja.yaml` in place or admitting it as a second authority.
- Permissive compatibility conversion from existing localized Core or
  Presentation Semantics inputs.
- Migration of all Document Projects or scaffold/profile changes.
- SmartDox article generation or SimpleModeling.org site integration.
- Article PDF, summary-slide PDF, PPTX, infographic, video, publication,
  registration, deployment, upload, push, or external-service mutation.
- Automatic semantic inference or automatic human acceptance of generated
  prose and summaries.

## References

- [Phase 58.1 checklist](phase-58.1-checklist.md)
- `docs/notes/document-project-document-and-summary-description-dsl-proposal.md`
- `docs/journal/2026/09/2026-09-12-phase-58.1-document-summary-description-dsl-decision.md`
- `docs/phase/phase-58.md`
