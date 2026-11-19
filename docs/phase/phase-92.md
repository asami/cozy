# Phase 92 — Shared Bibliography and PDF References

status=planned
execution_started=false

Planned: 2026-11-20
Purpose: Reuse curated BoK bibliography definitions across Document Projects, and render document-local cited references at the end of generated article PDFs.

Progress: [Phase 92 checklist](phase-92-checklist.md)
Dependencies: [Phase 12](phase-12.md), [Phase 40](phase-40.md), [Phase 87](phase-87.md)

## Goal

Provide LexiDox-like reusable bibliography definitions with stable citation IDs. Authors cite by existing SmartDox `bib:[key]` syntax, while Cozy resolves shared definitions and produces an automatically collected References section at the end of each PDF. The initial presentation is numeric; the architecture must permit later author–year styles without changing citation IDs or source documents.

## Scope and boundaries

- Inspect Phase 12's curated `*.bib.dox` / `*.bib.md` and BibTeX supplementary resolution, article-local References generation, and Phase 40's actual native PDF route before defining a new contract.
- Bibliographic metadata has one authoritative shared definition; PDF-local citation numbering and formatting are projections, not duplicated metadata.
- Allow Document Projects to explicitly select a shared bibliography catalog or component, analogous to shared LexiDox resources. Specify resolution precedence, identity, missing entries, conflicting keys and reproducible offline builds.
- Preserve the existing `bib:[key]` syntax and existing BoK/SmartDox citation behavior; do not create a competing bibliography DSL or PDF renderer.
- Initial style: numeric [1], [2], numbered in first-citation order, repeated citations reuse numbers. Only cited entries appear at the document end.
- Design an extensible citation-style boundary for future author–year presentation, but do not implement that style in this Phase unless separately authorized.
- Ensure JA/EN labels, deterministic output, link handling where supported, and diagnostics for unresolved entries.
- Keep reference resolution independent from network access during normal PDF generation; external lookup/update remains explicit.
- If an upstream SmartDox/LaTeX capability is genuinely missing, record the owning repository and handoff rather than silently modifying unrelated components.

## Planned Steps

1. P92-01: Inspect existing Phase 12 and native PDF behavior; freeze shared catalog resolution, citation model, numeric rendering, end-of-document placement, and representative JA/EN acceptance fixtures.
2. P92-02: Integrate shared bibliography catalogs with Document Project inputs, explicit selection, precedence and deterministic diagnostics.
3. P92-03: Carry resolved citation identities from Document/SmartDox into the existing article/PDF pipeline, with deduplication and first-use numbering.
4. P92-04: Render the final References section in real JA/EN PDFs using the existing PDF route; preserve unchanged HTML/BoK behavior.
5. P92-05: Add executable acceptance tests for shared reuse across two documents, duplicate citations, missing/conflicting keys, ordering, offline reproducibility and regressions; perform review and closeout.

## Acceptance

- Two Document Projects reuse the same authoritative bibliography entry without copying its metadata.
- A citation written as `bib:[gamma1994]` produces a numbered in-text reference and a complete corresponding bibliographic item at the end of each generated PDF.
- Repeated references have one number and one list entry; uncited catalog entries are absent.
- Changing a shared entry updates regenerated PDFs without changing document source citations.
- Missing or ambiguous references yield actionable diagnostics, never fabricated metadata.
- Both JA and EN real PDFs are verified, and legacy BoK bibliography behavior remains intact.

## Execution policy

This is planning only. All Steps remain OPEN. Do not change the status, priority, or goals of existing Phases; do not start implementation, publish, or deploy.
