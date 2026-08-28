# Phase 38 Checklist: Generated BoK Knowledge Boundary

This checklist is the authoritative progress ledger for Phase 38. It is not a
normative behavior contract.

Phase Status: IN PROGRESS

## BOK38-01: Source-Boundary Design and Specification

Status: COMPLETE

- [x] Define the final `doxsite`, `media`, `publication`, and optional
      `extensions` authorities in paired design and specification documents.
- [x] Specify generated RDF, graph, Manual, History, and standard UI authority.
- [x] Specify deterministic diagnostics, migration compatibility, and removal
      conditions for every admitted legacy path.
- [x] Specify that lifecycle documentation categories do not replace public
      subject categories.

BOK38-01 is COMPLETE: the paired design/specification documents now fix the
source-boundary contract. Its earlier statement that BOK38-02 through BOK38-07
remained NOT STARTED was a historical status snapshot; BOK38-02 and BOK38-03
are now COMPLETE below, while BOK38-04 through BOK38-07 remain NOT STARTED.
The canonical Phase
Hygiene journal
`docs/journal/2026/08/2026-08-28-phase-38-hygiene-follow-up.md` records
resolved `HYG-P38-001` and open nonblocking documentation follow-ups; it is not
Phase release closure. No Phase closure, driver acceptance, full validation,
publication, upload, push, or downstream consumer acceptance is claimed here.

## BOK38-02: Scaffold, Doctor, and Configuration Alignment

Status: COMPLETE

- [x] Stop scaffolding `doxsite/manual`, `history`, `rdf`, and `metadata`.
- [x] Stop scaffolding a project-local standard Antora UI bundle.
- [x] Update create, create-category, doctor, fix, guide, README, structure,
      configuration defaults, and Executable Specifications.
- [x] Make missing generated-source legacy directories valid rather than
      repairing them back into the project.

BOK38-02 is COMPLETE for the accepted scaffold, doctor, and configuration
alignment scope. New scaffolds omit default `manual`, `history`, `rdf`, and
`metadata` source directories and project-local standard UI source, while
creating durable `media` and `publication` roots. The default UI is
target-owned, with an explicit existing-project override. `create-category`,
doctor, fix, guide, README, structure, help, and Executable Specification
surfaces align with this boundary. Missing legacy roots remain valid and are
not re-created. Final serial `testOnly cozy.bok.CozyBokSpec` invocation
`39331-20260828T121546Z` passed 63 tests with 0 failures; `CPB-P38-02A-001`
was resolved, its focused re-review passed, and the local BOK38-02 acceptance
commit is `7c8b2bbef9c82a3d4dd3cb68536c971c0f26fa0e`. This does not claim full
Cozy validation, Phase full review, Phase release, driver acceptance,
`bok-knowledgehub` mutation, publication, upload/push, or downstream consumer
acceptance.

## BOK38-03: Dynamic RDF and Graph Generation

Status: COMPLETE

- [x] Derive effective Turtle and JSON-LD from admitted BoK semantic inputs.
- [x] Derive graph-summary nodes and edges from SmartDox, project,
      publication, CNCF component-reference, and SIE evidence.
- [x] Generate registered component-reference nodes without a duplicate source
      graph overlay.
- [x] Preserve deterministic merge, validation, schema versioning, and repeat
      output.
- [x] Publish only finalized resources below `website.d/rdf` and
      `website.d/metadata`.

BOK38-03 is COMPLETE after its independent lightweight review accepted the
implementation and focused validation; `CB-BOK38-03-RECEIPT-001` was resolved
by the M0 receipt-wording correction. Effective Turtle and JSON-LD continue to
be built from generated `doxsite.d` working resources and admitted semantic
inputs. The graph summary is now built only from the generated working graph
and configured SIE handoff: an authored
`src/main/doxsite/metadata/rdf/graph.json` neither augments a valid generated
graph nor substitutes for a missing one. Registered component references,
deterministic merge and validation, schema versioning, and the final-output
allowlist remain unchanged. Focused serial `testOnly
cozy.CozyBokMetadataFinalizationSpec` invocation
`48104-20260828T123347Z` passed 18 tests with 0 failures after the bounded
test-fixture repair `P38-BOK03-TEST-001`. This is not Phase closure, full Cozy
validation, driver acceptance, publication, upload/push, or downstream
consumer acceptance.

## BOK38-04: Generated Manual, History, and Standard UI

Status: NOT STARTED

- [ ] Generate the standard Manual without project-local Manual source.
- [ ] Generate History from durable project/publication evidence without a
      project-local History category.
- [ ] Supply the standard UI from Cozy and retain only an explicit optional
      project override.
- [ ] Treat a public project guide as an ordinary category and repository
      operation rules as non-public documentation.

## BOK38-05: Optional Extensions and Legacy Migration

Status: NOT STARTED

- [ ] Keep `src/main/extensions` absent from the default scaffold.
- [ ] Admit only explicit ontology, schema, and graph declarations that cannot
      be derived from normal BoK inputs.
- [ ] Reject extensions that silently replace generated authority or escape
      their admitted root.
- [ ] If legacy input is read temporarily, emit deterministic deprecation
      diagnostics and prove the removal condition.

## BOK38-06: KnowledgeHub Driver Migration Acceptance

Status: NOT STARTED

- [ ] Run `cozy bok doctor` against the reorganized `bok-knowledgehub` source.
- [ ] Build the driver without restoring `manual`, `history`, `rdf`,
      `metadata`, or a standard UI bundle below `doxsite`.
- [ ] Verify concept, architecture, technology, glossary, bibliography,
      scenario, project, media, and publication behavior.
- [ ] Verify generated RDF, graph, Manual, History, UI, component references,
      and stable repeated output.
- [ ] Verify publication dry-run without upload or external mutation.

## BOK38-07: Review, Validation, and Closure

Status: NOT STARTED

- [ ] Run focused Cozy Executable Specifications for every changed contract.
- [ ] Run the full Cozy validation gate through serialized SBT execution.
- [ ] Complete independent Phase review and any bounded blocker repair.
- [ ] Synchronize Strategy, Phase, checklist, design, specification, and driver
      receipts before closure.

Phase 38 is IN PROGRESS. BOK38-01 through BOK38-03 are COMPLETE, while
BOK38-04 through BOK38-07 remain NOT STARTED. No Phase closure, driver
acceptance, full validation, publication, upload, push, or downstream consumer
acceptance is claimed.
