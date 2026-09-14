# Phase 60 Checklist

Phase status: PLANNED
Updated: 2026-09-14
Scope: Subphase 60A local build
Ledger for: [Phase 60](phase-60.md)

All Phase 60 implementation items are OPEN. Planning does not complete these
items. The unresolved P600-06 through P600-08 ledger is owned by
[Phase 60.1](phase-60.1-checklist.md).

## P600-01: Source, views and dependency contract admission

Stage Status:

- Current status: OPEN
- Owner: Cozy Document Project implementation owner
- Update rule: close only against promoted contracts and executable specifications

- [ ] Design/spec distinguishes Core meaning, Document Description prose, derived SmartDox representation and the three local confirmation targets.
- [ ] CLI/help and executable specifications distinguish renderer freshness against `index.dox` from reflection of the latest Document prose; HTML generation alone makes no prose-equivalence or approval claim.
- [ ] Exact target IDs, output roots, CLI/view selectors and default compatibility are frozen without requiring article annotations or hash receipts.
- [ ] The admitted Cozy operation set exposes independently callable skill-facing CLI/API contracts with explicit parameters, consumed inputs, outputs/assets and success/failure behavior; target-local build dependency handling remains distinct from overall skill orchestration.
- [ ] Structure- and document-centered reference HTML fixes prose order, secondary information, nested Step/Flow/Structure and multi-target navigation.
- [ ] Given/When/Then specifications fix absent-output, newer-input, equal-time, missing-input, force-build and configuration dependency behavior.
- [ ] Transitive/generated dependencies, cycle diagnostics, multiple outputs and timestamp resolution/future-time limitations are specified.

## P600-02: Document-centered DSL confirmation

Stage Status:

- Current status: OPEN
- Owner: Cozy confirmation projection owner
- Update rule: close from reference equivalence and source-preservation evidence

- [ ] Both views consume the same validated Document Description and preserve its headings, prose and nesting without renderer paraphrasing.
- [ ] The document-centered main column reads in document order; Core and diagrams are secondary, collapsible or in a side panel.
- [ ] Passage-to-Core and Step-to-prose navigation uses explicit existing references and retains multi-target correspondence.
- [ ] Local Structure, direct-child Flow and recursive containment remain distinct and keyboard-operable.
- [ ] Omitted selector preserves the current structure view; Summary and legacy table article review keep their supported contracts.

## P600-03: Actual article HTML target through SmartDox

Stage Status:

- Current status: OPEN
- Owner: Cozy Document Project integration owner
- Update rule: close from actual SmartDox rendering and CLI evidence

- [ ] A Document Project entry point renders existing `index.dox` through admitted normal SmartDox CLI/API with a tested version and output shape.
- [ ] Article headings, paragraphs, lists and supported links/figures survive into actual article HTML, rather than narrative review tables.
- [ ] Required article inputs exclude Core annotations, Visual Pages and infographic files unless the actual article renderer consumes them.
- [ ] Declared local output and associated renderer assets are installed only after successful isolated rendering and path checks.
- [ ] The target is distinguished from site publication and from the legacy `article-review-html` product in help and workflow surfaces.

## P600-04: Make-level target management

Stage Status:

- Current status: OPEN
- Owner: Cozy Document Project build owner
- Update rule: close from dependency graph and filesystem-time specifications

- [ ] Missing outputs build; strictly newer dependencies rebuild; current outputs reuse without renderer execution or timestamp changes.
- [ ] Actual consumed source/config/template/vocabulary/asset files are declared; generated prerequisites build first and affected dependents rebuild.
- [ ] Missing required inputs and dependency cycles fail before rendering; explicit force rebuild handles otherwise-current targets.
- [ ] Multi-output freshness and dependency timestamp resolution satisfy the admitted ordinary-make contract.
- [ ] Failed rendering returns failure, preserves previous successful outputs and leaves their modification times unchanged.
- [ ] No hashes, digest manifests or acceptance receipts are required by these local build targets; existing unrelated acceptance/export contracts remain intact.
- [ ] Target/source/output decisions are visible without starting a persistent server or treating freshness as human approval.

## P600-05: Isolated acceptance and closure

Stage Status:

- Current status: OPEN
- Owner: Cozy Phase owner; independent reviewer separate from implementation
- Update rule: close only with preceding items complete and validation/review evidence

- [ ] An isolated Article-9-shaped fixture demonstrates both DSL views and a genuine SmartDox article-rendering target.
- [ ] With only Document prose changed and `index.dox` unchanged, both DSL views rebuild while an otherwise-current article HTML target may reuse its unchanged output; the source boundary and separate authoring action are visible without claiming latest-Document reflection.
- [ ] Controlled file-time specifications prove target reuse, selective/transitive rebuild, absent output, changed config, equal-time and force behavior.
- [ ] Failure and safety specifications prove old-output preservation, path confinement and dependency-cycle rejection.
- [ ] Focused validation, independent Phase 60 review and bounded closure work settle local-build blockers; repository-full SBT validation is deferred to aggregate final owner Phase 60.1.
- [ ] CLI/help, design/spec and work ledgers describe implemented behavior without claiming external project, skill, media or publication acceptance.

## Split transfer record

P600-06 through P600-08 remain OPEN and were transferred once to the sole
active ledger, [Phase 60.1 checklist](phase-60.1-checklist.md), by the
2026-09-14 applied split. They are not Phase 60 checklist items and this record
does not claim their completion.
