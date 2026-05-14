# Phase 5: Engineering Publication Compiler

Status: active

Start date: 2026-05-12

## Goal

Build the first Cozy-native Engineering Publication Compiler.

The compiler reads an sbt project and generates knowledge sources used by a
SmartDox site BoK.

The expected target site structure follows the SimpleModeling.org AI-era
engineering knowledge platform plan:

- `/catalog`
- `/samples`
- `/repository`
- `/maven`
- existing BoK layers such as `/ja`, `/en`, `/glossary`, `/ontology`,
  `/schema`, and `/smartdox`

## Scope

Phase 5 focuses on generated `src/main/publication` knowledge sources.

In scope:

- inspect an sbt project as an engineering knowledge source
- extract project identity and build metadata
- extract component/publication metadata when available
- generate AI-facing catalog source files
- generate sample publication metadata
- generate source snapshot metadata
- generate repository/artifact metadata from warehouse indexing
- generate cross-layer release metadata for Maven/CAR/SAR artifacts
- provide deterministic output for tests and review

Out of scope for this phase:

- full SmartDox site rendering
- binary artifact upload
- Maven repository hosting
- warehouse retention policy
- full source code semantic analysis

## Working Model

```text
sbt project
  -> Cozy Engineering Publication Compiler
  -> publication registry
      /catalog
      /samples
      /repository
      /maven
      /releases
      /source-manifest
warehouse
  -> Cozy Warehouse Indexer
  -> publication registry
      /maven
      /repository
      /releases
  -> SmartDox site
  -> website.d
```

## Phase Items

- [x] EPC-01: Define `src/main/publication` output contract for sbt project knowledge
- [x] EPC-02: Add Cozy command for engineering publication compilation
- [x] EPC-03: Implement sbt project metadata extraction
- [x] EPC-04: Generate catalog and sample metadata aligned with SimpleModeling.org
- [x] EPC-05: Generate source snapshot manifest for AI-facing source retrieval
- [x] EPC-06: Add tests with a minimal sbt fixture project
- [x] EPC-07: Document handoff from Cozy `src/main/publication` output to SmartDox site
- [x] EPC-08: Index warehouse artifacts into `src/main/publication` release metadata

## Acceptance Criteria

- A command can read a local sbt project and write a deterministic `src/main/publication`
  directory.
- Generated metadata has stable paths and stable IDs.
- Generated output maps clearly to `/catalog`, `/samples`, `/repository`, and
  `/maven` layers.
- Warehouse indexing generates `/repository`, `/maven`, and `/releases`
  metadata without requiring SmartDox site to scan the warehouse directly.
- Tests verify at least one minimal sbt project fixture.
- The phase checklist records completed and remaining EPC work.

## References

- `docs/strategy/cozy-development-strategy.md`
- `docs/journal/2026/05/engineering-publication-compiler.md`
- `/Users/asami/src/dev2025/simplemodeling-org/docs/journal/2026/05/site-structure-expansion-for-ai-era-engineering-knowledge-platform.md`
