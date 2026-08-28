# Phase 38: Generated BoK Knowledge Boundary

Status: IN PROGRESS

Plan date: 2026-08-28

## Goal

Make `src/main/doxsite` the public human-readable SmartDox knowledge source,
derive RDF, graph metadata, the standard Manual, the History dashboard, and the
standard site UI during the Cozy build, and keep durable media and publication
state in their own `src/main` roots. Adopt the reorganized
`bok-knowledgehub` repository as the driver project for the final source
boundary.

## Start gate

- Phase 37 must close or the user must explicitly authorize changing the
  active-Phase order.
- The source, generated-output, publication-registry, and optional-extension
  authorities must be fixed in paired design and specification documents
  before implementation.
- The reorganized `/Users/asami/src/Project2026/bok-knowledgehub` tree is the
  driver input. Phase 38 must not restore removed legacy source directories in
  that repository as a workaround.

## Final source contract

```text
src/main/
├── doxsite/       public SmartDox knowledge and adjacent source metadata
├── media/         durable article-media packages
├── publication/   Cozy-managed durable publication registry
└── extensions/    optional explicit non-derived machine-readable extensions
```

The default `doxsite` source must not contain `manual`, `history`, `rdf`, or
`metadata` source directories. Public subject categories must be semantic
topics rather than notes/design/spec lifecycle stages.

Cozy and SmartDox derive effective RDF and graph metadata from `site.conf`,
category metadata, articles, glossary, bibliography, scenarios, projects,
publication records, CNCF component-reference metadata, and SIE handoffs.
The standard Manual, History dashboard, and standard UI are Cozy-owned build
outputs. A public project guide is an ordinary source category. Repository
operation rules remain outside the public source tree.

## Stages

### BOK38-01: Source-Boundary Design and Specification

Stage Status:

- Current status: COMPLETE
- Owner: Cozy BoK source and generated-knowledge contracts
- Update rule: complete only when source authority, output authority,
  compatibility, diagnostics, and extension behavior are specified.

Define the exact paths, authorities, generated-resource identities, failure
semantics, and migration contract. Keep `site.conf` and category metadata as
adjacent public-source metadata while excluding generated machine metadata
from the source authority.

BOK38-01 is COMPLETE: the paired design/specification documents now fix the
source-boundary contract. Its earlier statement that BOK38-02 through BOK38-07
remained NOT STARTED was a historical status snapshot; BOK38-02 is now COMPLETE
as recorded below, while BOK38-03 through BOK38-07 remain NOT STARTED. The
canonical Phase Hygiene journal
`docs/journal/2026/08/2026-08-28-phase-38-hygiene-follow-up.md` records
resolved `HYG-P38-001` and open nonblocking documentation follow-ups; it is not
Phase release closure. No Phase closure, driver acceptance, full validation,
publication, upload, push, downstream consumer acceptance, or BOK38-02 Step
commit is claimed here.

### BOK38-02: Scaffold, Doctor, and Configuration Alignment

Stage Status:

- Current status: COMPLETE
- Owner: Cozy BoK project lifecycle
- Update rule: complete only when new scaffolds and diagnostics enforce the
  final boundary without generating legacy source directories.

BOK38-02 is COMPLETE for the accepted scaffold, doctor, and configuration
alignment scope. New scaffolds omit default `manual`, `history`, `rdf`, and
`metadata` source directories and project-local standard UI source, while
creating durable `media` and `publication` roots. The default UI is
target-owned, with an explicit existing-project override. `create-category`,
doctor, fix, guide, README, structure, help, and Executable Specification
surfaces align with this boundary. Missing legacy roots remain valid and are
not re-created. The focused evidence is final serial `testOnly
cozy.bok.CozyBokSpec` invocation `65022-20260828T090426Z`, which passed 63 tests
with 0 failures; `CPB-P38-02A-001` was resolved and its focused re-review
passed. This does not claim full Cozy validation, Phase full review, Phase
release, driver acceptance, `bok-knowledgehub` mutation, publication,
upload/push, downstream consumer acceptance, or a BOK38-02 Step commit.

### BOK38-03: Dynamic RDF and Graph Generation

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy/SmartDox metadata finalization
- Update rule: complete only when effective Turtle, JSON-LD, and graph-summary
  outputs are derived, merged, validated, versioned, and repeatable.

Generate `doxsite.d/site.ttl`, `doxsite.d/site.jsonld`, and
`doxsite.d/metadata/rdf/graph.json`, then publish their validated effective
forms below `website.d/rdf` and `website.d/metadata/rdf`. Generate component
reference nodes from registered project/component evidence instead of
requiring the driver project to maintain a duplicate graph overlay.

### BOK38-04: Generated Manual, History, and Standard UI

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy BoK generated site surfaces
- Update rule: complete only when all three surfaces build without their
  former project-local source files and preserve deterministic navigation.

Generate the standard Manual and History dashboard from Cozy-owned content and
durable project/publication evidence. Treat project-user guidance as an
ordinary `guide` category and project operation rules as repository
documentation. Supply the default site UI from Cozy rather than a copied
project bundle.

### BOK38-05: Optional Extensions and Legacy Migration

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy BoK extension admission and migration
- Update rule: complete only when optional extensions are explicit,
  path-safe, deterministic, and cannot silently replace generated authority.

Admit an absent-by-default `src/main/extensions/rdf` boundary only for
non-derived project ontology, schema, or graph declarations. If a transitional
legacy reader is retained, it must emit deterministic deprecation diagnostics,
must never be scaffolded, and must have an explicit removal condition.

### BOK38-06: KnowledgeHub Driver Migration Acceptance

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy BoK end-to-end acceptance
- Update rule: complete only when the reorganized driver builds without
  restoring legacy paths or duplicated article-media assets.

Run doctor, build, metadata finalization, preview-oriented inspection, and
publication dry-run against `bok-knowledgehub`. Verify subject categories,
special knowledge types, media/publication roots, generated RDF/graph,
generated Manual/History/UI, component references, and stable repeat output.

### BOK38-07: Review, Validation, and Closure

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy validation and independent review
- Update rule: complete only when focused and full validation, driver
  acceptance, independent review, and ledger synchronization are complete.

## Dependencies and exclusions

- Phase 38 changes Cozy and the Cozy-owned driver integration only. It does
  not redesign SmartDox syntax, SIE runtime semantics, media composition, or
  publication hosting.
- `src/main/media` and `src/main/publication` remain durable inputs; generated
  work, caches, rendered site files, and metadata snapshots remain outside the
  public knowledge source.
- No generated RDF, graph, Manual, History, or standard UI artifact becomes a
  second hand-maintained source of truth.
- Phase planning does not claim implementation, validation, publication,
  upload, push, or downstream consumer acceptance.

## References

- `docs/phase/phase-38-checklist.md`
- `docs/strategy/cozy-development-strategy.md`
- `/Users/asami/src/Project2026/bok-knowledgehub/STRUCTURE.md`
- `docs/design/bok-metadata-finalization.md`
- `docs/spec/bok-metadata-finalization.md`
- `docs/design/bok-sie-integration-contract.md`
- `docs/design/bok-sie-information-handoff.md`
