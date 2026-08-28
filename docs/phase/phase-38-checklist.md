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
remained NOT STARTED was a historical status snapshot; BOK38-02 through
BOK38-04 are now COMPLETE below, BOK38-05 through BOK38-06 are now COMPLETE,
and BOK38-07 remains NOT STARTED.
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

Status: COMPLETE

- [x] Generate the standard Manual without project-local Manual source.
- [x] Generate History from durable project/publication evidence without a
      project-local History category.
- [x] Supply the standard UI from Cozy and retain only an explicit optional
      project override.
- [x] Treat a public project guide as an ordinary category and repository
      operation rules as non-public documentation.

BOK38-04 is COMPLETE for the generated Manual, History, and standard UI
closure scope. Focused serialized `testOnly cozy.bok.CozyBokSpec` receipt
`63418-20260828T130808Z` recorded 63 succeeded, 0 failed, and 1 suite; the
independent Step review was PASS. Cozy always emits the standard History at
`website.d/history/index.html` while preserving deterministic annual-history
navigation. The standard Manual is Cozy-owned and does not expose
`manual/local-rules.html`; Guide remains an ordinary category, and the
standard Cozy UI remains the default with the explicit override unchanged.
This is BOK38-04 closure only and does not claim Phase 38 closure, full
validation, driver acceptance, downstream Textus BoK acceptance, publication,
upload, or push.

## BOK38-05: Optional Extensions and Legacy Migration

Status: COMPLETE

- [x] Keep `src/main/extensions` absent from the default scaffold.
- [x] Admit only explicit ontology, schema, and graph declarations that cannot
      be derived from normal BoK inputs.
- [x] Reject extensions that silently replace generated authority or escape
      their admitted root.
- [x] Keep BOK38-05 free of a legacy source reader through BOK38-06 driver
      acceptance.

BOK38-05 is COMPLETE for explicit `bok.extensions.rdf` JSON v1 declaration
admission under `src/main/extensions/rdf`. The implemented boundary admits only
listed path-safe declarations, validates deterministic identities and payloads,
and applies supplements after SIE merge without replacing generated authority.
No legacy reader is installed or used. Focused
`CozyBokMetadataFinalizationSpec` validation (24 successful, 0 failed) and a
lightweight independent Step review are accepted. Driver acceptance, Phase
completion, publication, upload, push, and deployment remain unclaimed.

## BOK38-06: KnowledgeHub Driver Migration Acceptance

Status: COMPLETE

- [x] Run `cozy bok doctor` against the reorganized `bok-knowledgehub` source.
- [x] Build the driver without restoring `manual`, `history`, `rdf`,
      `metadata`, or a standard UI bundle below `doxsite`.
- [x] Verify concept, architecture, technology, glossary, bibliography,
      scenario, project, media, and publication behavior.
- [x] Verify generated RDF, graph, Manual, History, UI, component references,
      and stable repeated output.
- [x] Verify publication dry-run without upload or external mutation.

BOK38-06 is COMPLETE. With
`cozy 0.3.3-SNAPSHOT` explicitly selected through
`--runtime-dev-dir /Users/asami/src/dev2025/cozy`, `bok doctor`, two preview
builds, and explicit metadata finalization all succeeded. The selected
RDF/metadata and Manual/History/root-page artifact digests were identical
across builds; expected generated UI, RDF graph, KnowledgeSource, and CAR
component-reference artifacts exist, while the absent legacy source paths
remain absent. `bok publish --dry-run` produced only
`target/cozy-bok/publish/latest/manifest.json` (`dryRun: true`), with no
publication, upload, deployment, remote write, or driver source change. The
driver was validation-only; downstream Textus BoK consumer acceptance remains
out of scope.

## BOK38-07: Review, Validation, and Closure

Status: NOT STARTED

- [ ] Run focused Cozy Executable Specifications for every changed contract.
- [ ] Run the full Cozy validation gate through serialized SBT execution.
- [ ] Complete independent Phase review and any bounded blocker repair.
- [ ] Synchronize Strategy, Phase, checklist, design, specification, and driver
      receipts before closure.

Phase 38 is IN PROGRESS. BOK38-01 through BOK38-06 are COMPLETE, and BOK38-07
remains NOT STARTED. The canonical Hygiene journal
records HYG-P38-001 as RESOLVED and HYG-P38-002 through HYG-P38-005 as open
nonblocking follow-ups. No Phase closure, driver acceptance, full validation,
publication, upload, push, or downstream consumer acceptance is claimed.
