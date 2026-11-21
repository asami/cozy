# Phase 93 — Glossary Preferred Labels and Term/Pattern Presentation

status=in-progress
execution_started=true

Planned: 2026-11-17
Development item: DEV-043
Purpose: Carry glossary-owned preferred labels and original-language pattern names into consistent HTML and LaTeX/PDF term presentation.
Progress: [Phase 93 checklist](phase-93-checklist.md)
Requirements: [term and pattern presentation](../notes/term-pattern-presentation-requirements.md)
Origin: [DDD review request](../journal/2026/11/2026-11-17-term-pattern-presentation-plan.md)

## Scope and ownership

Support glossary-owned labels and display preferences while retaining stable identities and canonical definitions. Provide three term-presentation profiles: original-language preference, established-Japanese-translation preference and Japanese preference. The middle profile uses glossary-identified established Japanese translations and otherwise prefers original-language names; the Japanese profile uses glossary-defined Japanese names as far as possible.

Choose the PDF profile at generation time. Choose the HTML default at generation time and allow readers to switch among all three profiles through a menu or equivalent control without regeneration. Switching term labels preserves identity, scope, links and style roles; it does not translate the article prose or rewrite code identifiers.

Named design patterns retain an original-definition-language baseline. P93-01 settles its precedence with profile selection and per-term preferences, including absent-label behavior. Distinguish ordinary terms, named patterns and code identifiers in color and typography through declared style configuration.

P93-01 defines the contracts and compatibility rules before implementation. SimpleModeling.org retains canonical glossary and writing-policy authority; SmartDox retains parsing and renderer ownership; Cozy owns its normal metadata admission, generation configuration and integrated consumer delivery. Confirm required producer contracts and accepted revisions rather than inventing fields in the existing DSL or adding a parallel parser.

## LexiDox grammar scope

Include the LexiDox source grammar/schema extension for original-language names, localized labels, preferred display policy, established-translation designation and ordinary-term/pattern semantics. P93-01 defines exact syntax, existing-field reuse, precedence and diagnostics. The SmartDox producer owner supplies grammar documentation/examples, parsing/validation, serialization/metadata and executable specifications with legacy compatibility; Cozy consumes that accepted delivery.

Reuse the relevant Phase 92 contracts and explicitly assign additional Phase 93 producer work. Acceptance follows authored LexiDox through metadata, Core/Document and actual HTML/LaTeX outputs, rather than starting from hand-built metadata alone.

## Core-side capability

This Phase includes supported Core-side terminology and presentation modeling. Define a versioned extension or successor to the current closed Core, with compatible existing inputs, typed glossary identity bindings, ordinary-term/pattern roles and label/style intent propagated into localized Documents and output projections. English pattern names, bold emphasis and optional color/style roles must be expressible; concrete colors/fonts remain renderer configuration.

The live DDD source currently uses English names as plain text. It does not add unsupported Core fields or manual bold/color markup. Accept the new Core-originating behavior before downstream adoption.

## Shared LexiDox grammar alignment — 2026-11-18

Follow the [joint Phase 92/93 delivery plan](../notes/lexidox-phase92-93-integration.md) and canonical LX-J01–LX-J08 requirements. P92-01 and P93-01 coordinate one SmartDox producer grammar/schema and metadata decision record with a named baseline and compatible extension. Do not define Phase-local dialects or reparse sources in Cozy.

Resolve Space and Term/Concept identity before display-label selection. Knowledge classification, knowledge form and display role remain distinct; profile switching preserves page/link targets, canonical definitions, provenance, relations and RDF knowledge. Shared source fixtures verify these invariants across both delivery routes.

Joint contract alignment precedes shared-boundary implementation. Phase 92 accepts the semantic baseline without waiting for all Phase 93 rendering; Phase 93 consumes the accepted required subset and verifies cumulative compatibility. Existing priorities and unrelated work are unchanged.

## Planned Steps

1. P93-01: Confirm current glossary/HTML/LaTeX paths and owners; define LexiDox grammar/schema and producer delivery, versioned Core-side term/presentation support, the three profiles, established-translation metadata, label-selection precedence, semantic roles and style contracts with compatibility and diagnostics.
2. P93-02: Implement/adopt the agreed Core-side contract and integrate accepted glossary/SmartDox metadata into normal admission, localized Document propagation and three-profile resolution, preserving identity and links.
3. P93-03: Connect semantic styles and output-time profile selection to native HTML and LaTeX/PDF generation, including an HTML reader switch.
4. P93-04: Accept portable DDD-derived fixtures for all profiles, PDF generation choices, HTML defaults and reader switching; record producer and Cozy versions, usage and downstream adoption evidence.

P93-01 design work is recorded in the checklist; Step acceptance and later implementation remain open.

## Dependencies and boundaries

Coordinate the required glossary format and public-writing policy changes with SimpleModeling.org and consume actual SmartDox producer delivery. Reuse Phase 92 contracts when relevant; do not make unrelated Phase 92 work a blanket dependency. Named design patterns are distinct from Phase 91 explanation patterns.

Specific colors/fonts remain configuration; accepted metadata fields and missing-input behavior are specified by P93-01. Existing entries and document-language behavior require an explicit compatibility contract. Color and font choices are renderer projections of semantic roles, not embedded per-occurrence formatting in the manuscript.

## Completion and DDD adoption

Closure requires all checklist outcomes, actual HTML and LaTeX PDF evidence, relevant producer delivery, Cozy focused/integration validation, independent review and final ordinary Cozy validation. Record the accepted versions and configuration needed by the documentation consumer.

The DDD guide adopts this feature only after Phase 93 completion and usable delivery. Live Core, localized Documents, SmartDox and generated DDD outputs are not changed by registration or by the isolated acceptance fixture. Preserve existing Phase priorities and work. No implementation, commit, publication or deployment is selected by this plan registration.

## Historical P93-01 proposal and dependency evidence — 2026-11-21

Before owner delivery, the presentation contract was a proposal for precedence, diagnostics and producer and Core/Document contracts. The [historical boundary record](../design/lexidox-producer-consumer-boundary.md#historical-p93-01-proposal-and-outstanding-owner-decisions--2026-11-21) recorded the accepted Phase 92 baseline and outstanding SmartDox and SimpleModeling.org decisions. The [current presentation contract](../spec/term-pattern-presentation.md) incorporates the subsequently adopted decisions described below.


## P93-01 design adoption — 2026-11-21

The [presentation contract](../spec/term-pattern-presentation.md) and [joint boundary decision](../design/lexidox-producer-consumer-boundary.md#p93-01-adopted-compatible-extension--2026-11-21) adopt the SmartDox producer design/spec at `bed732ff8e7df0339b74d96e2452fa2b103fbae6` and SimpleModeling.org glossary and public-writing rules at `8cc123c887b73393f8ae40db793a0c4d8d0a1e49`. They specified the compatible selector extension and a draft Cozy Core contract while preserving Phase 92 identities. The P93-02 contract supersedes the historical minimal-Core successor draft with the existing recursive `cozy.content-core.logic-tree.v2` envelope; see the presentation contract. Japanese public output defaults to `japanese`; editor-enabled HTML alternatives and explicitly identified alternative PDF editions follow output acceptance. The producer extension, Core v2, renderer integration, validation, review and closure remain future work.
