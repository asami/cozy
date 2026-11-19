# Phase Proposal: LexiDox Typed Sections and Knowledge Space Semantics

Status: proposal / non-normative input to Phase 92
Proposed: 2026-11-16
Phase number: 92 (allocated 2026-11-16; implementation unstarted)
Plan: [Phase 92](phase-92.md) / [checklist](phase-92-checklist.md)
Primary planning repository: Cozy
Implementation owner: verify SmartDox vs Cozy boundaries before implementation

## Goal

Evolve LexiDox vocabulary definitions to support typed standard sections, unrestricted supplemental sections, Knowledge Space and Context semantics, and explicit SimpleModeling methodology linkage while retaining existing SmartDox/BoK compatibility.

**Canonical requirements:** [SimpleModeling LexiDox design note](https://github.com/asami/simplemodelingorg/blob/main/docs/notes/lexidox-knowledge-space-context-schema.md). Do not duplicate or diverge from its definitions.

## Phase entry gate / ownership investigation

- Inspect current LexiDox parsing, schema validation, RDF/JSON-LD export, and BoK rendering code in SmartDox, Cozy and consumers.
- Assign source parsing and semantic validation to the actual owning repository (likely SmartDox, subject to verification); Cozy should not create a competing parser.
- Identify existing type metadata, section handling, term/concept IRI and glossary profiles; confirm backward-compatibility fixtures.
- Phase 92 owns Cozy coordination and consumption. Confirm producer ownership and its implementation handoff before consumer implementation; any SmartDox producer Phase retains that repository's numbering and authority.

## Proposed work packages

1. **Schema model** — type-specific standard sections, required/optional/cardinality, registered common sections, BoK-specific profiles; `SIMPLEMODELING` denotes development methodology connection.
2. **Section recognition** — uppercase standard-section attempt with validation; non-uppercase free sections, including lowercase, mixed-case and Japanese, retained without restrictions; clear unknown-uppercase diagnostics.
3. **Knowledge semantics** — `space` identity and `CONTEXT` interpretation; preserve Term/Concept distinction, clarify cross-space concept identity and IRI mapping.
4. **Pattern specialization** — `type=pattern`, required `DEFINITION/CONTEXT/PROBLEM/SOLUTION`, optional consequences and free narrative sections.
5. **Legacy migration** — continue reading `# SimpleModeling`, normalize semantically to `SIMPLEMODELING` while retaining source/render fidelity; no mass rewrite before validation.
6. **Exports and BoK** — RDF/JSON-LD/SKOS mapping, multilingual output, glossary index, links and free-section preservation.
7. **Documentation and verification** — examples, migration guidance, executable fixtures, parser tests, end-to-end SimpleModeling BoK smoke tests.

## Acceptance criteria

- Existing glossary documents parse and render without loss or forced immediate migration.
- Type-specific mandatory sections and unknown uppercase section diagnostics work as specified.
- Arbitrary non-uppercase sections, including lowercase, mixed-case and Japanese, survive parsing, rendering, and relevant exports.
- `SIMPLEMODELING` and `CONTEXT` have distinct documented semantics; legacy spelling remains readable.
- Stable Knowledge Space and Term/Concept identity mapping is specified and tested before RDF changes.
- Pattern entries validate under the Pattern schema without requiring a separate document system.
- No new execution authority is granted to Executable Knowledge or schema-defined content.
- Focused and repository-level tests and downstream SimpleModeling smoke tests pass before closure.

## Out of scope

- Rewriting all existing glossary entries at once.
- New knowledge graph storage engine.
- Automatic AI-to-program conversion.
- Runtime execution of glossary content.
- Redefining the SECI knowledge-conversion model.

## Dependencies and references

- [SimpleModeling canonical design](https://github.com/asami/simplemodelingorg/blob/main/docs/notes/lexidox-knowledge-space-context-schema.md)
- [Executable Knowledge concept](https://github.com/asami/simplemodelingorg/blob/main/docs/notes/executable-knowledge.md)
- Existing SmartDox/LexiDox parser and Cozy BoK publishing pipeline.
- Existing Cozy Phase 90/91 plans must be reconciled before numbering or scheduling.

## Phase 92/93 alignment — 2026-11-18

The canonical proposal now includes combined LX-J01–LX-J08 knowledge-processing/presentation requirements. Follow the [joint delivery plan](../notes/lexidox-phase92-93-integration.md) and both current Phase checklists for their allocation. Phase 93 is a compatible extension of the same grammar/metadata lineage, not an independent dialect. This original proposal remains historical planning input.

## Handoff

This file retains the original capability proposal as input to the numbered [Phase 92](phase-92.md). The Phase and its checklist own current scope and progress; this proposal makes no implementation claim. Confirm and assign producer implementation under its owning repository's convention without duplicating the canonical concept definitions here.
