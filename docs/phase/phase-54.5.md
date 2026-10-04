# Phase 54.5: Terminology and BoK Semantic References

Status: IN_PROGRESS

Plan date: 2026-09-17
Implementation start: 2026-10-04
Split from: [Phase 54](phase-54.md)
Depends on: Phase 54.4
Successor: [Phase 54.6](phase-54.6.md)
Primary downstream consumer: Textus CBD Support

## Purpose

Publish admitted references between Cozy semantic elements and glossary/BoK
terminology so Textus CBD Support can navigate terminology without treating
display names or synonyms as identity.

Glossary/BoK remains the authority for curated terms and synonym decisions.
Cozy publishes only declared or otherwise admitted references and records
missing conceptual links explicitly.

## In-scope work

Stage Status:
- Current status: IN_PROGRESS
- Current step: S54.5.2 / S54.5.2A
- Owner: parent workflow
- Update rule: parent records actual validation, independent review, and acceptance evidence; the [checklist](phase-54.5-checklist.md) remains the closure basis.

| ID | Outcome | Status |
| --- | --- | --- |
| MMD-545-01 | Publish stable semantic-element to terminology references with vocabulary/profile identity, relation kind, source attribution, and supplied localized/preferred labels. | supplier and navigation accepted; fixture and Phase closure pending |
| MMD-545-02 | Keep BoK Mono/Koto classification distinct from CML Entity/Event classification. | supplier accepted; fixture and Phase closure pending |
| MMD-545-03 | Record unsupported conceptual grouping or terminology links as explicit capability gaps. | supplier and navigation accepted; fixture and Phase closure pending |
| MMD-545-04 | Freeze Terminology/BoK fixtures and handoff for Event Storming and final consumer fixtures. | planned |

## Planned Steps and current evidence

Each class S Step has one Slice. S54.5.1 is CLOSED with validation, independent
acceptance and commit `81cd3bbcbaaa293c1cc7a7a33901aa99b8d7c2cc`. S54.5.2 is
validated and independently accepted; its manual Step commit is pending.
Phase closure is pending.

| Step / Slice | Observable boundary | Evidence status |
| --- | --- | --- |
| S54.5.1 / S54.5.1A | Complete typed supplier, strict versioned wire, independent executable specification, paired spec/design. | CLOSED; 195 tests in 14 suites passed; independent protected review and focused closure passed; acceptance commit `81cd3bbcbaaa293c1cc7a7a33901aa99b8d7c2cc`. |
| S54.5.2 / S54.5.2A | Exact qualified consumer navigation with full Local/External resolution and ordered links. | 205 tests in 15 suites passed; independent Step review and focused repair closure accepted; manual Step commit pending. |
| S54.5.3 / S54.5.3A | Fixed declared/absence fixtures, peer compatibility and supplier handoff. | Planned; resources, validation and acceptance pending. |

The accepted supplier contract is in [spec](../spec/cml-terminology-metadata.md)
and [design](../design/cml-terminology-metadata.md). TM-01..09 are exercised in
[CmlTerminologyMetadataSpec](../../src/test/scala/cozy/modeler/CmlTerminologyMetadataSpec.scala).
TM-09 passed 100 property samples with zero discards and positive actual coverage
for both availability modes and all five permutation dimensions. The independent
focused closure resolved the initial permutation-coverage finding.
Navigation TN-01..08 passed within the 205 tests in 15 suites in
[CmlTerminologyNavigationSpec](../../src/test/scala/cozy/modeler/CmlTerminologyNavigationSpec.scala),
with complete qualified queries, ordered duplicate links, Local/External
resolution, explicit gaps, safe shape/membership diagnostics and the fixed-seed
independent occurrence permutation property. TN-08 and TM-09 each passed 100
samples with zero discards and all seven actual coverage tags positive.
Independent Step review and focused repair closure are accepted, with no
remaining Step blockers. The manual Step commit, fixed fixtures, sole full Phase
review, ordinary full validation and release closure remain pending. External
consumer acceptance is outside scope.

## Closure criteria

- CBD Support can cite an admitted term reference from a stable semantic ID.
- `Mono != Entity` and `Koto != Event`; no synonym candidate is silently
  resolved by Cozy.
- Missing terminology semantics remain explicit absence.

The internal supplier and planned navigation/fixtures expose this capability;
external CBD Support integration is outside the current supplier boundary.

## Non-goals

- Curating BoK vocabulary or synonym policy.
- Inventing conceptual groups for a view.
- Event Storming traversal or final consumer fixture acceptance.

## References

- [Phase 54.4](phase-54.4.md)
- [Phase 54.5 checklist](phase-54.5-checklist.md)
- [Phase 54.6](phase-54.6.md)
