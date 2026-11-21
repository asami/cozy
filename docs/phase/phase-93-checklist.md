# Phase 93 checklist — Glossary Preferred Labels and Term/Pattern Presentation

status=in-progress
execution_started=true
Plan: [Phase 93](phase-93.md)
Development item: DEV-043
Owner: Cozy development; glossary and producer delivery remain owner-specific

## Stage Status

- Current status: OPEN
- Owner: Cozy development
- Update rule: Record actual contract, delivery and verification evidence per criterion.
- Closure basis: All P93-01–P93-04 criteria. Plan registration is not implementation or acceptance.

## P93-01: Contracts, requirements and owners

- [x] Current glossary labels/public-writing rules, HTML term styles and native LaTeX/PDF paths are identified with their owners and adopted revisions.
- [x] Design/spec defines document-language display preferences, original definition language/name, precedence among selected profile, entry preferences and pattern defaults, and compatibility for existing entries.
- [x] The three profiles are specified: original-language preference, established-Japanese-translation preference and Japanese preference; established translations are explicitly identified in glossary metadata rather than inferred from label presence.
- [x] Missing labels/language and conflicting preferences have explicit resolution or diagnostic rules; no language is guessed from spelling.
- [x] Canonical glossary/policy changes and SmartDox producer schema, semantic roles, handoff version and implementation ownership are agreed for consumption.
- [x] Ordinary term, named design pattern and code roles, style configuration and HTML/LaTeX mappings are defined; Phase 91 explanation patterns and Phase 92 knowledge forms remain distinct.

- [x] A supported versioned Core extension/successor defines typed glossary term/concept bindings, term/pattern roles and label/presentation intent, with compatibility for current Core and localized Documents.
- [x] Core-originating English pattern names, bold emphasis and optional color/style roles have an explicit Document/renderer handoff; concrete fonts/colors remain configuration.

- [x] The LexiDox grammar/schema explicitly defines source syntax for original-language names, localized labels, preferences, established translations and ordinary-term/pattern semantics, reusing existing fields where applicable.
- [x] Grammar documentation, valid/invalid source examples, parser/validator behavior, diagnostics and serialization/metadata contracts have named producer ownership and accepted revisions, coordinated with Phase 92.

- [x] The [joint plan](../notes/lexidox-phase92-93-integration.md) and canonical LX-J01–LX-J08 are reconciled with P92-01 in one producer design/spec decision record, with shared grammar/metadata baseline, compatible extension, ownership and revisions.
- [x] Exact label/provenance fields and display-role mapping preserve Phase 92 concept/knowledge-form semantics; schema profiles and presentation profiles have distinct documented meanings.


P93-01 design evidence: [presentation contract](../spec/term-pattern-presentation.md), [producer boundary decision](../design/lexidox-producer-consumer-boundary.md#p93-01-adopted-compatible-extension--2026-11-21), and [handoff](../spec/lexidox-knowledge-space-handoff.md). P93-01 was accepted at Step commit `6fa22ce33be415dbba3ca34cc21cfa422019c598`; the later sections record implementation and validation.

## P93-02: Metadata and label resolution

- [x] Accepted producer delivery reaches Cozy through the normal metadata contracts without a second parser or reconstructed missing metadata.
- [x] Original-language pattern defaults and explicit glossary preferences resolve as specified for Japanese and English documents under each selected profile.
- [x] Accepted metadata resolves all three profiles, including established translations, other Japanese labels and missing-label behavior, without inventing translations or rewriting ordinary prose/code.
- [x] Stable term/concept identity, scope and glossary link destinations remain correct when the displayed label changes.
- [x] Legacy entries, absent optional preferences and invalid/conflicting inputs satisfy the compatibility and diagnostic contracts.

- [x] The agreed Core-side contract is implemented/adopted and validated through normal loading and localized Document propagation; existing closed Core inputs remain compatible.

- [x] The owning producer delivers the required LexiDox grammar/parser/validation/serialization changes and executable specifications, including existing-entry compatibility; Cozy consumes attributable accepted output.

- [x] Space lookup and Term/Concept resolution precede label selection; all profiles retain the resolved page/link target and ambiguity/undefined diagnostics without broader-Space retry or rendered-text lookup.

P93-02 was accepted as a Step at commit `ceb0615ed603a78f730af8883309d92c237ca3e2`: its independent re-review records successful compile and 122 focused tests, including normal build-path catalog resolution. The integrated Phase evidence and independent technical review additionally cover the Phase-level qualifications above.

## P93-03: HTML and LaTeX/PDF presentation

- [x] Shared semantic roles/configuration drive both HTML and LaTeX/PDF presentation while retaining independent renderer syntax.
- [x] PDF generation accepts a selected term-presentation profile and renders that selection through the native LaTeX route.
- [x] HTML generation accepts a default profile, displays it initially and supplies a reader menu or equivalent control for all three profiles without regeneration.
- [x] HTML switching preserves concept identities, scope, glossary links and term/pattern styling, including repeated switching; ordinary prose and code identifiers remain intact.
- [x] Ordinary terms and named patterns have restrained distinguishable colors and typography; code identifiers retain their own role.
- [x] The existing HTML term treatment is assessed and integrated; declared colors/fonts and escaping work for Japanese and English labels.
- [x] Links, text readability and existing unconfigured output behavior meet the accepted contract without per-occurrence manuscript styling.

- [x] HTML and LaTeX/PDF consume supported Core-originating label/style intent, including English named patterns, bold emphasis and configured color, without manual per-occurrence markup.

P93-03 was accepted as a Step at commit `b549b6a3893bc7cd95a3389258c072f52ca98d95`: its independent review records successful compile and 128 focused tests and verifies the renderer handoff. That Step review did not independently observe PDF output or browser switching; later actual observations are recorded below.

## P93-04: Integrated acceptance and downstream handoff

- [x] A portable DDD-derived fixture covers original-language patterns, explicit label preferences, ordinary terms, code and legacy entries without editing the live guide.
- [x] Actual native HTML and LaTeX PDF output verifies all three profiles, label choice, color, typography, links and Japanese/English behavior; producer tests remain attributable to their owner.
- [x] Actual PDFs cover each output-time profile; actual generated HTML covers each initial default and reader switching among all three profiles, including established and non-established Japanese translations.
- [ ] Cozy focused/integration specifications, independent review and final ordinary Cozy validation pass, and remaining work is recorded separately.
- [x] Accepted producer/Cozy versions, glossary/writing-policy revisions and usable style configuration are documented for the downstream consumer.
- [x] A Core-authored fixture proves term identity, three-profile selection, pattern emphasis/color and localized Document propagation in actual HTML/PDF while legacy Core fixtures remain compatible.
- [x] Actual authored LexiDox fixtures verify grammar acceptance/rejection, producer metadata, Core/Document propagation and HTML/LaTeX output; hand-built metadata alone does not establish grammar completion.
- [x] Reuse Phase 92 source fixtures under the accepted compatible extension and all presentation profiles; verify unchanged classification, knowledge form, definitions, provenance, relations, Space/page identity and canonical RDF/JSON-LD/SKOS knowledge.
- [ ] Phase/checklist/Strategy closure records agree with actual evidence and the prescribed completion procedure.
- [x] The documentation handoff states that live DDD adoption and selected regeneration follow Phase 93 completion; no manuscript or published-output migration is claimed by this Phase.

P93-04 was accepted at commit `c4b045ce54f2391fbe58b6fc21d1d24353401ef4`, with 129 focused tests in 13 suites. The [acceptance journal](../journal/2026/11/2026-11-21-phase-93-native-renderer-acceptance.md) records actual output, compatibility and Phase validation. The independent technical review found only the present ledger discrepancy; its repair requires focused re-review. The combined review criterion and closure record remain open until that review and native release closure occur.
