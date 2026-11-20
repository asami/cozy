# Term and Pattern Presentation — Requirements

Status: planning input; not an implemented schema
Date: 2026-11-17
Plan: [Phase 93](../phase/phase-93.md)
Origin: [user request](../journal/2026/11/2026-11-17-term-pattern-presentation-plan.md)

## Purpose

DDD manuscript review identified a need to choose term labels in the canonical glossary and carry that choice into HTML and LaTeX/PDF. Ordinary terms and named design patterns should have distinguishable, restrained color and typography.

## Requested outcomes

- Keep stable term/concept identities and both canonical Japanese and English labels. Record the preferred display label for the document language in the glossary.
- Retain the original definition language as the baseline for named patterns, normally English for the selected DDD/PoEAA patterns. Define how this baseline and per-term preferences interact with the selected presentation profile before implementation.
- Define how original-language names, profile selection, explicit preferences and existing entries without the new metadata are resolved. The accepted design/spec must settle precedence, missing-label and conflicting-input behavior before implementation.
- Carry semantic distinctions for ordinary terms and named design patterns through the normal source and generation contracts. Preserve code identifiers as a separate presentation role.
- Apply those roles to HTML and LaTeX/PDF color and typography from shared style configuration. Start from the existing HTML term treatment; specific colors and fonts are not yet selected.
- Preserve glossary links, identity, scope and meaning when the displayed label or style changes. Do not identify concepts by matching the rendered label alone.

## Three term-presentation profiles — 2026-11-17 refinement

The document selects one of three profiles for glossary-bound terms. These profiles select labels within an article; they do not translate the surrounding prose or change concept definitions.

| Profile | Intended term-label selection |
| --- | --- |
| Original-language preference | Use labels close to the original definition language, commonly English. |
| Established-translation preference | Use an established Japanese translation when the glossary identifies one; otherwise prefer the original-language label. |
| Japanese preference | Use glossary-defined Japanese labels as far as possible. |

The glossary owns original-language labels, Japanese labels and whether a translation is established. A Japanese label alone does not establish that it is a conventional translation. Exact metadata, precedence with entry preferences and pattern defaults, and behavior when a requested label is absent are P93-01 design/spec decisions. Do not invent translations during rendering.

For PDF, choose the profile at generation time. For HTML, choose the default profile at generation time and provide a reader-facing menu or equivalent control to switch among all three profiles without regeneration. The initial view uses the selected default. Switching changes term labels while retaining concept identity, scope, link targets and term/pattern styling; ordinary text and code identifiers remain intact.

## Core-side terminology and presentation support — 2026-11-17 refinement

Include native Core-side support in this Phase. The current closed recursive Core cannot carry term identity or display/style intent. Define a supported versioned extension or successor contract, preserve existing Core/Document compatibility and bind glossary term/concept identities and ordinary-term/pattern roles through Core, localized Document and renderer projection.

The Core-side contract must express the term-label selection and presentation intent needed for the three profiles and named patterns, including English pattern names, bold emphasis and an optional color/style role. Actual colors and fonts belong to renderer/style configuration. Do not add undefined fields to the current Core, hard-code renderer syntax in business claims or recover term identities by scanning prose.

The current DDD source update uses plain English names only. Bold and color remain requested future capabilities; they must be accepted through Core-originating HTML/LaTeX fixtures before document adoption.

## LexiDox grammar extension — 2026-11-18 clarification

Phase 93 explicitly includes the LexiDox vocabulary-definition grammar/schema extension needed by this feature. It is not limited to consuming unspecified metadata or adding Core fields.

Define supported source syntax for original definition language/names, canonical localized labels, per-document-language display preferences, established-Japanese-translation designation and the semantic distinction needed for ordinary terms and named design patterns. Reuse existing fields where available. P93-01 specifies exact field names, values, placement, precedence and diagnostics; this requirement does not invent executable syntax.

The owning SmartDox producer delivers grammar/schema documentation, valid/invalid authoring examples, parser and validation support, serialization/metadata handoff and executable specifications. Existing LexiDox definitions retain compatibility. Verify authoring → parsing/validation → metadata → Core/Document → HTML/LaTeX continuity.

Coordinate with Phase 92 typed sections and Term/Concept identity contracts. Reuse its accepted syntax and ownership without duplicating its work; explicitly allocate any additional producer work required by Phase 93. Renderer colors/fonts remain style configuration, not glossary definition content.

## Alignment with knowledge-processing requirements

The [joint Phase 92/93 plan](lexidox-phase92-93-integration.md) maps canonical LX-J01–LX-J08 into one producer grammar/schema lineage. The SimpleModeling.org LexiDox proposal remains the meaning authority; this presentation note does not define a second grammar.

Resolve the Phase 92 Space/Term/Concept reference before label/style selection. Presentation fallback does not retry another Space. Keep concept classification, knowledge form and display roles distinct, and preserve canonical definitions, source provenance, relations and RDF knowledge under every profile. P92-01/P93-01 share a coordinated design/spec record and source fixtures; Phase 93 consumes and regression-checks the accepted semantic baseline.

## Ownership and dependencies

SimpleModeling.org owns canonical glossary definitions, labels and public-writing policy. Its current glossary format requires Japanese labels in Japanese public artifacts and forbids English parenthetical additions. Preferred-label support requires coordinated format/policy changes; this plan does not silently waive those rules.

SmartDox owns parsing and renderer production; Cozy owns admitted metadata, generation coordination, configuration and integration acceptance at its existing boundaries. Confirm the actual owners and accepted producer revisions in P93-01. Do not add a second glossary parser or reconstruct missing producer metadata in Cozy.

Coordinate with Phase 92 where typed glossary/knowledge metadata is needed. A named design pattern is distinct from a document/section explanation pattern in Phase 91. A knowledge-form value of pattern does not by itself establish the intended semantic classification; settle the mapping explicitly.

## Acceptance and document adoption

Use a portable fixture derived from the DDD guide, without changing the live manuscript. Exercise all three profiles, original-language pattern defaults, explicit term preferences, established and non-established Japanese labels, existing entries, Japanese/English documents, links, typography and actual HTML/LaTeX PDF output. Verify each PDF generation choice, each HTML initial default and reader switching in the generated HTML.

After Phase 93 is complete and the accepted producer/Cozy versions and configuration are available, the DDD documentation task adopts the capability and regenerates selected outputs. That document update remains downstream work. Phase registration starts no implementation, glossary migration, manuscript rewrite or publication.
