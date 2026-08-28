# Phase 38 Checklist: Generated BoK Knowledge Boundary

This checklist is the authoritative progress ledger for Phase 38. It is not a
normative behavior contract.

Phase Status: PLANNED / NOT STARTED

## BOK38-01: Source-Boundary Design and Specification

Status: NOT STARTED

- [ ] Define the final `doxsite`, `media`, `publication`, and optional
      `extensions` authorities in paired design and specification documents.
- [ ] Specify generated RDF, graph, Manual, History, and standard UI authority.
- [ ] Specify deterministic diagnostics, migration compatibility, and removal
      conditions for every admitted legacy path.
- [ ] Specify that lifecycle documentation categories do not replace public
      subject categories.

## BOK38-02: Scaffold, Doctor, and Configuration Alignment

Status: NOT STARTED

- [ ] Stop scaffolding `doxsite/manual`, `history`, `rdf`, and `metadata`.
- [ ] Stop scaffolding a project-local standard Antora UI bundle.
- [ ] Update create, create-category, doctor, fix, guide, README, structure,
      configuration defaults, and Executable Specifications.
- [ ] Make missing generated-source legacy directories valid rather than
      repairing them back into the project.

## BOK38-03: Dynamic RDF and Graph Generation

Status: NOT STARTED

- [ ] Derive effective Turtle and JSON-LD from admitted BoK semantic inputs.
- [ ] Derive graph-summary nodes and edges from SmartDox, project,
      publication, CNCF component-reference, and SIE evidence.
- [ ] Generate registered component-reference nodes without a duplicate source
      graph overlay.
- [ ] Preserve deterministic merge, validation, schema versioning, and repeat
      output.
- [ ] Publish only finalized resources below `website.d/rdf` and
      `website.d/metadata`.

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

Phase 38 is planned and not started. No implementation, validation,
publication, upload, push, downstream consumer acceptance, or Phase closure is
claimed.
