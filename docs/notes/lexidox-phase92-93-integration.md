# LexiDox Phase 92/93 Joint Delivery Plan

Date: 2026-11-18
Status: coordinated planning; implementation unstarted
Meaning authority: [canonical combined requirements](https://github.com/asami/simplemodelingorg/blob/main/docs/notes/lexidox-knowledge-space-context-schema.md#combined-knowledge-processing-and-presentation-requirements--2026-11-18)
Knowledge delivery: [Phase 92](../phase/phase-92.md) / [checklist](../phase/phase-92-checklist.md)
Presentation delivery: [Phase 93](../phase/phase-93.md) / [checklist](../phase/phase-93-checklist.md)

## Purpose

Deliver one compatible LexiDox grammar for knowledge processing and presentation. LX-J01–LX-J08 in the canonical proposal own the shared requirements; this plan assigns work and verification. Exact executable syntax is promoted into the owning producer's design/spec before implementation.

## Responsibility map

| Boundary | Phase 92 | Phase 93 | Common contract |
| --- | --- | --- | --- |
| Source grammar and metadata | Knowledge Space, Term/Concept, concept classification, knowledge form and standard/free sections | Original-language/translation/preferences and required presentation metadata as a compatible extension | One SmartDox producer grammar/parser/schema/serialization lineage; no duplicate fields or dialects |
| Identity and reference resolution | Canonical Space lookup, semantic identities, page targets, ambiguity/undefined diagnostics | Choose a label and style only after consuming the resolved reference | LX-J02; no language-driven re-resolution or identity changes |
| Pattern meaning and presentation | Concept classification and knowledge form remain independent | An explicit accepted metadata mapping selects a display role | LX-J03; style never changes knowledge form or its section requirements |
| Knowledge and output | BoK indexes, canonical metadata and RDF/JSON-LD/SKOS | Core/Document propagation, three display profiles, PDF selection and HTML switching | LX-J04–LX-J07; preserve canonical meaning and labels |
| Acceptance | Existing/source-authored semantic cases and legacy compatibility | The same semantic cases with presentation extensions and actual outputs | LX-J08; knowledge regression and presentation acceptance share source fixtures |

## Joint decisions and delivery order

P92-01 and P93-01 align one producer design/spec decision record. It identifies the canonical proposal revision, owner, existing field reuse, exact grammar/types/cardinality, reference and label bindings, diagnostics, serialization and baseline/extension compatibility. Both Phases cite that record and its accepted revisions. P93-01 records profile/entry/pattern precedence and missing-label behavior; no separate Phase-local syntax is an implementation substitute.

The required order is joint contract alignment, accepted Phase 92 semantic baseline, then the compatible Phase 93 extension and integrated presentation acceptance. This orders the shared boundary rather than blocking Phase 92 on all rendering or Phase 93 on unrelated Phase 92 work. Existing Phase priorities remain unchanged. Unknown producer contracts/revisions remain unresolved until obtained; this planning record does not claim they exist.

## Shared verification

The producer owns source grammar specifications and valid/invalid cases. Cozy reuses a portable source fixture family for its two downstream routes, without another parser or reconstructed metadata.

- Reuse the Phase 92 Space/page/Term/Concept fixtures, including same-label different meanings, explicit shared concepts, reference/document Space precedence and ambiguity/undefined diagnostics.
- Retain a process described with Pattern sections, ordinary terms, SOURCE/REFERENCES and CONTEXT/SIMPLEMODELING distinctions, uppercase/free sections, aliases and legacy entries.
- Add source-language labels, established and other Japanese translations and entry preferences to the same semantic cases under the accepted extension.
- In Phase 93, exercise all three PDF/HTML selections and HTML reader switching. Verify unchanged identity, Space/page links, definitions, relations and RDF knowledge alongside the selected labels, emphasis/color and intact prose/code.
- Verify accepted producer/Core/Document versions and legacy compatibility; neither hand-built metadata nor successful rendering alone proves grammar or knowledge acceptance.

## Completion boundary

Both Phase checklists include this joint alignment and their own attributable validation/review/closure. Phase 92 closure binds its accepted baseline and extension handoff; Phase 93 closure binds cumulative compatibility, actual output evidence and usable consumer delivery. All new criteria remain OPEN. DDD adoption of the new presentation feature follows Phase 93 completion and usable delivery.
