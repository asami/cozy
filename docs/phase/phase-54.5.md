# Phase 54.5: Terminology and BoK Semantic References

Status: CLOSED

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
- Current status: CLOSED
- Current step: all three Steps CLOSED; Phase closure
- Owner: parent workflow
- Update rule: parent records actual validation, independent review, and acceptance evidence; the [checklist](phase-54.5-checklist.md) remains the closure basis.

| ID | Outcome | Status |
| --- | --- | --- |
| MMD-545-01 | Publish stable semantic-element to terminology references with vocabulary/profile identity, relation kind, source attribution, and supplied localized/preferred labels. | CLOSED; supplier, navigation, fixtures and Phase acceptance |
| MMD-545-02 | Keep BoK Mono/Koto classification distinct from CML Entity/Event classification. | CLOSED; independent classifications and fixture acceptance |
| MMD-545-03 | Record unsupported conceptual grouping or terminology links as explicit capability gaps. | CLOSED; supplier, navigation, fixtures and Phase acceptance |
| MMD-545-04 | Freeze Terminology/BoK fixtures and handoff for Event Storming and final consumer fixtures. | CLOSED; fixtures, handoff, validation, sole full review and release binding |

## Planned Steps and current evidence

Each class S Step has one Slice. S54.5.1 is CLOSED with validation, independent
acceptance and commit `81cd3bbcbaaa293c1cc7a7a33901aa99b8d7c2cc`. S54.5.2 is
CLOSED at acceptance commit `350175851dfa267aaa35c4cae865ad67f15f0cd8`.
S54.5.3 is CLOSED at `09d9ca8cc0d6188dc3d009764ce8ded24825b4ca` after
214 tests in 16 suites and independent complete Step review PASS.
All five checklist obligations are accepted; the final release binds Phase closure.

| Step / Slice | Observable boundary | Evidence status |
| --- | --- | --- |
| S54.5.1 / S54.5.1A | Complete typed supplier, strict versioned wire, independent executable specification, paired spec/design. | CLOSED; 195 tests in 14 suites passed; independent protected review and focused closure passed; acceptance commit `81cd3bbcbaaa293c1cc7a7a33901aa99b8d7c2cc`. |
| S54.5.2 / S54.5.2A | Exact qualified consumer navigation with full Local/External resolution and ordered links. | CLOSED; 205 tests in 15 suites passed; independent Step review and focused repair closure PASS; acceptance commit `350175851dfa267aaa35c4cae865ad67f15f0cd8`. |
| S54.5.3 / S54.5.3A | Fixed declared/absence fixtures, peer compatibility and supplier handoff. | 214 tests in 16 suites passed; independent complete Step review PASS with no blockers; acceptance commit `09d9ca8cc0d6188dc3d009764ce8ded24825b4ca`. |

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
remaining S2 Step blockers. S2 acceptance commit is
`350175851dfa267aaa35c4cae865ad67f15f0cd8`. Fixed resources and TF-01..09 in
[CmlTerminologyMetadataFixtureSpec](../../src/test/scala/cozy/modeler/CmlTerminologyMetadataFixtureSpec.scala)
are authored with complete independent raw/prefix/wire expectations and separate
peer gates. The [supplier handoff](../design/cml-terminology-metadata-handoff.md)
records the fixed 55/7 declarations and reproducible sixteen-suite recipe.
S3 passed 214 tests in 16 suites and independent complete Step review with no
blockers. TM-09, TN-08 and TF-09 each passed 100 samples with zero discards and
all seven actual coverage tags positive; TF-09 exercised both modes per sample.
The sole independent full Phase review passed with no blockers, Hygiene or
Development Candidates. Ordinary `sbt --batch test` passed 2835 tests in 198
suites, with zero failures or aborted suites and 9 pre-existing optional/deferred
canceled cases. SBT and wrapper exited zero; the serial lock was released.
The distinct parent manual local release binds `P545-CLOSURE-001@1`; its native
commit and closure receipt are verified after committing these final records.
External consumer acceptance is outside scope.

## Closure criteria

- CBD Support can cite an admitted term reference from a stable semantic ID.
- `Mono != Entity` and `Koto != Event`; no synonym candidate is silently
  resolved by Cozy.
- Missing terminology semantics remain explicit absence.

The internal supplier, accepted navigation and accepted fixtures expose this capability;
external CBD Support integration is outside the current supplier boundary.

## Non-goals

- Curating BoK vocabulary or synonym policy.
- Inventing conceptual groups for a view.
- Event Storming traversal or final consumer fixture acceptance.

## References

- [Phase 54.4](phase-54.4.md)
- [Phase 54.5 checklist](phase-54.5-checklist.md)
- [Phase 54.6](phase-54.6.md)
