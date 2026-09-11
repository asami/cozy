# Phase 58.1 Checklist: Document and Summary Description DSL Vertical Slice

Phase Status: IN_PROGRESS

Development item: DEV-025

Predecessor: [Phase 58](phase-58.md)

phase=[Phase 58.1](phase-58.1.md)

Planning rule: one executable vertical slice; preferred 4–8 h band.

## P581-01: DSL design and specification

Stage Status:
- Current status: DONE
- Owner: Cozy Document Project semantic authoring
- Update rule: Do not mark DONE until both DSLs and the Core/content/projection
  authority boundary are accepted in paired design and specification docs.

- [x] Define `cozy.document-description.v1` as a complete document-authoring
      DSL rather than HTML or SmartDox output IR.
- [x] Define `cozy.summary-description.v1` as deliberate concise content
      rather than automatic truncation or slide-layout IR.
- [x] Define exact Core identity/reference contracts for Document Description.
- [x] Define exact Core and Document identity/reference contracts for Summary
      Description.
- [x] Define explicit BCP-47 locale admission and the
      `content/<locale>/document.yaml` / `summary.yaml` organization rule.
- [x] Separate reader content from renderer-owned chrome, CSS, navigation,
      print, and physical layout.
- [x] Decide the closed document block and summary unit vocabularies.

## P581-02: Typed authority and validation

Stage Status:
- Current status: DONE
- Owner: Cozy Document Project codecs and validators
- Update rule: Do not mark DONE until both source authorities fail closed and
  canonical identities cover every admitted semantic field.

- [x] Implement strict typed loaders for Document and Summary Description.
- [x] Reject duplicate keys and malformed or lossy UTF-8/YAML before
      normalization.
- [x] Reject unknown fields, duplicate identities, invalid locale, unresolved
      Core references, and stale Core/Document identities.
- [x] Prove deterministic canonical identities and repeated-load equality.
- [x] Do not add a permissive `format-ja.yaml` compatibility reader.

## P581-03: Article 9 document vertical slice

Stage Status:
- Current status: DONE
- Owner: Cozy Document Project document description and review
- Update rule: Do not mark DONE until the real Article 9 prose is represented
  and reviewable with exact Core traceability.

- [x] Author `content/ja/document.yaml` for the real Article 9 project.
- [x] Represent sections, headings, paragraphs, lists/examples/notes as needed,
      and exact Core references.
- [x] Generate one deterministic self-contained document-review HTML.
- [x] Keep diagnostic identities secondary to the readable composed document.
- [x] Prove complete document-block and selected Core-reference coverage.

## P581-04: Article 9 summary vertical slice

Stage Status:
- Current status: TODO
- Owner: Cozy Document Project summary description and review
- Update rule: Do not mark DONE until a summary-slide-level explanation is
  represented independently of slide coordinates and traced to both upstream
  authorities.

- [ ] Author `content/ja/summary.yaml` for the real Article 9 project.
- [ ] Represent selected Step/claim/node/Relation/Flow references, concise
      headings/messages, order, and emphasis.
- [ ] Bind the exact accepted Core and Document Description identities.
- [ ] Generate one deterministic self-contained summary-review HTML.
- [ ] Prove summary-unit coverage without requiring one physical slide per
      unit in the semantic authority.

## P581-05: Semantic richness and closure

Stage Status:
- Current status: TODO
- Owner: Phase 58.1 acceptance
- Update rule: Do not mark DONE until the real driver, validation, review, and
  release evidence close on one settled tree.

- [ ] Use multiple Logical Patterns and typed Relations in Article 9 where
      semantically warranted; do not manufacture variety only for coverage.
- [ ] Make child-Step Flow and Step-local Structure visibly distinct.
- [ ] Localize reader-facing semantic labels without replacing stable IDs.
- [ ] Add focused executable specifications covering both DSLs, both review
      outputs, identity/currentness, relationship semantics, and determinism.
- [ ] Complete one independent full Phase review with no Current Phase
      Blocker.
- [ ] Complete full Cozy validation through the shared SBT lock.
- [ ] Close Phase 58.1 through a distinct release commit.

## Closure boundary

The following remain outside Phase 58.1 and do not appear as OPEN work:

- migration or retirement of Phase 46/46.1/58 products;
- all-project scaffold or profile migration;
- SmartDox and SimpleModeling.org production integration;
- article/summary PDF, PPTX, infographic, video, publication, registration,
  deployment, upload, push, or external-service work; and
- automatic acceptance of AI-generated document or summary content.
