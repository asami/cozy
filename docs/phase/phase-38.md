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
remained NOT STARTED was a historical status snapshot; BOK38-02 through
BOK38-04 are now COMPLETE as recorded below, BOK38-05 through BOK38-06 are
now COMPLETE, and BOK38-07 remains NOT STARTED. The
canonical Phase Hygiene journal
`docs/journal/2026/08/2026-08-28-phase-38-hygiene-follow-up.md` records
resolved `HYG-P38-001` and open nonblocking documentation follow-ups; it is not
Phase release closure. No Phase closure, driver acceptance, full validation,
publication, upload, push, or downstream consumer acceptance is claimed here.

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
not re-created. Final serial `testOnly cozy.bok.CozyBokSpec` invocation
`39331-20260828T121546Z` passed 63 tests with 0 failures; `CPB-P38-02A-001`
was resolved, its focused re-review passed, and the local BOK38-02 acceptance
commit is `7c8b2bbef9c82a3d4dd3cb68536c971c0f26fa0e`. This does not claim full
Cozy validation, Phase full review, Phase release, driver acceptance,
`bok-knowledgehub` mutation, publication, upload/push, or downstream consumer
acceptance.

### BOK38-03: Dynamic RDF and Graph Generation

Stage Status:

- Current status: COMPLETE
- Owner: Cozy/SmartDox metadata finalization
- Update rule: complete only when effective Turtle, JSON-LD, and graph-summary
  outputs are derived, merged, validated, versioned, and repeatable.

Generate `doxsite.d/site.ttl`, `doxsite.d/site.jsonld`, and
`doxsite.d/metadata/rdf/graph.json`, then publish their validated effective
forms below `website.d/rdf` and `website.d/metadata/rdf`. Generate component
reference nodes from registered project/component evidence instead of
requiring the driver project to maintain a duplicate graph overlay.

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

### BOK38-04: Generated Manual, History, and Standard UI

Stage Status:

- Current status: COMPLETE
- Owner: Cozy BoK generated site surfaces
- Update rule: complete only when all three surfaces build without their
  former project-local source files and preserve deterministic navigation.

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

### BOK38-05: Optional Extensions and Legacy Migration

Stage Status:

- Current status: COMPLETE
- Owner: Cozy BoK extension admission and migration
- Update rule: complete only when optional extensions are explicit,
  path-safe, deterministic, and cannot silently replace generated authority.

Implement the explicitly configured `bok.extensions.rdf` JSON v1 declaration
admission at the absent-by-default `src/main/extensions/rdf` boundary for
non-derived project ontology, schema, or supplemental-graph declarations.
Declarations are path-safe, deterministic, collision-rejecting supplements to
the generated graph after SIE merge and before graph-summary versioning. No
legacy source reader is installed or used before BOK38-06. Focused
`CozyBokMetadataFinalizationSpec` validation (24 successful, 0 failed) and a
lightweight independent Step review are accepted. This stage is COMPLETE; no
driver acceptance, Phase completion, publication, upload, push, or deployment
is claimed.

### BOK38-06: KnowledgeHub Driver Migration Acceptance

Stage Status:

- Current status: COMPLETE
- Owner: Cozy BoK end-to-end acceptance
- Update rule: complete only when the reorganized driver builds without
  restoring legacy paths or duplicated article-media assets.

The driver acceptance completed with explicit Cozy development-runtime
selection (`--runtime-dev-dir /Users/asami/src/dev2025/cozy`): `bok doctor`,
two preview-strategy builds, and explicit metadata finalization succeeded. The
repeated generated RDF/metadata and Manual/History/root-page artifact digests
are stable; expected generated UI, RDF graph, KnowledgeSource, and CAR
component-reference artifacts exist; and deleted legacy source paths remain
absent. The dry-run publication manifest is `dryRun: true` and lives only in
the driver's ignored `target/` output. The driver's pre-existing source changes
were preserved. This stage is COMPLETE; Phase completion, actual publication,
upload, push, deployment, and downstream Textus BoK consumer acceptance remain
unclaimed.

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

High-level tracker: BOK38-01 through BOK38-06 are COMPLETE; BOK38-07 is NOT
STARTED. Phase 38 remains IN PROGRESS.

## References

- `docs/phase/phase-38-checklist.md`
- `docs/strategy/cozy-development-strategy.md`
- `/Users/asami/src/Project2026/bok-knowledgehub/STRUCTURE.md`
- `docs/design/bok-metadata-finalization.md`
- `docs/spec/bok-metadata-finalization.md`
- `docs/design/bok-sie-integration-contract.md`
- `docs/design/bok-sie-information-handoff.md`
