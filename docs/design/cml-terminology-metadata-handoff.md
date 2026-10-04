# CML Terminology and BoK Metadata Supplier Handoff

Date: 2026-10-04
Status: Phase 54.5 CLOSED; S54.5.3 CLOSED at `09d9ca8cc0d6188dc3d009764ce8ded24825b4ca`; final release binding `P545-CLOSURE-001@1`.
Owner: Cozy metadata supplier; glossary/BoK curators own meaning and synonym decisions.

## Artifacts and admission boundary

The [supplier specification](../spec/cml-terminology-metadata.md),
[design](cml-terminology-metadata.md), private
[supplier](../../src/main/scala/cozy/modeler/CmlTerminologyMetadata.scala) and
[navigation](../../src/main/scala/cozy/modeler/CmlTerminologyNavigation.scala)
define the internal API. External handoff uses UTF-8 versioned JSON:
[terminology-metadata-v1-declared.json](../../src/test/resources/cozy/modeler/terminology-metadata-v1-declared.json)
and [terminology-metadata-v1-absence.json](../../src/test/resources/cozy/modeler/terminology-metadata-v1-absence.json).
The [fixture executable specification](../../src/test/scala/cozy/modeler/CmlTerminologyMetadataFixtureSpec.scala)
authors TF-01..09. Its focused validation and independent Step review passed.

Both resources retain the complete first 49 ElementRecords and first four
TermRecords of the accepted [declared Use Case fixture](../../src/test/resources/cozy/modeler/use-case-metadata-v1-declared.json),
in original order and with every field intact. All 49 prefix core reference
values are `Absent(NotDeclared, "No semantic references are declared.")`.
Total counts are 55 core occurrences, seven Terms, 55 Terminology element
projections and seven Term projections. Equal anonymous occurrences count twice.

The outer version remains `cozy.cml.semantic-metadata.v1`. The only new child is
`cozy.cml.terminology`, with exactly `schemaVersion: cozy.cml.terminology.v1`,
`elements`, and `terms`. Element projections have exactly `element`,
`termReferences`, `conceptualGroups`; Term projections have exactly `term`,
`termType`, `bokKind`, `labels`, `preferredLabel`. Every core/source/reference
field is embedded completely. Presence has explicit present or absent branches;
source `line` and label `language` require a number/string or explicit null.

The eight prior namespaces remain: `cozy.cml.structure`,
`cozy.cml.classification`, `cozy.cml.dynamic`, `cozy.cml.use-case`, and
`org.example.structure-handoff`, `org.example.classification-handoff`,
`org.example.dynamic-handoff`, `org.example.use-case-handoff`. Structure alone
appends two Entity projections, with each mode's complete new Entity record
and `Absent(NotDeclared, "No aggregate boundary is declared.")`. Its original
three elements and all relations survive; total Structure elements are five.
Every other extension value is unchanged. There is no fifth opaque peer.
Each existing reader must independently admit the same expanded envelope.
Terminology admission cannot certify peer semantics.

## Complete new literal core and Term facts

All new core names are exactly `共有`. Let D be the literal digest
`dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd`.
S(owner,line) is `SourceAttribution(owner,"src/main/cml/terminology-handoff.cml",D,Some(line))`.
Every path, digest, authority and line is synthetic caller provenance. There
is no parser, I/O verification, computed hash or currentness claim.

| New ordinal | Kind | Complete identity | Source |
| --- | --- | --- | --- |
| 0 | entity | Present(ModelElementId("terminology.a","shared")) | S("terminology.a",50) |
| 1 | entity | Present(ModelElementId("terminology.b","shared")) | S("terminology.b",51) |
| 2 | event | Present(ModelElementId("terminology.a","event:shared")) | S("terminology.a",52) |
| 3 | event | Present(ModelElementId("terminology.b","event:shared")) | S("terminology.b",53) |
| 4 | event | Absent(NotRepresented,"No stable Terminology element identity is represented.") | S("terminology.anonymous",54) |
| 5 | event | Same complete value as ordinal 4 | Same complete source as ordinal 4 |

TA is `TermId("terminology.a","shared")`, TB is
`TermId("terminology.b","shared")`, TU is `TermId("terminology.a","unused")`.
Their full TermRecord sources use `glossary/terminology.dox`, D, authorities
`terminology.a`, `terminology.b`, `terminology.a`, and lines `Some(60)`,
`Some(61)`, `None`, respectively. Foreign External TF is
`TermId("external.terminology","shared")`; it is not among the seven Terms.
Matching model and Term strings never coerce identity domains.

## Complete literal reference declarations

Each Q(owner,line,target,boundary,token) has the full target and boundary,
`relation=Present(RelationId("relations.example","synonym-candidate"))`,
`profile=Present(ProfileId("profiles.example","view:literal"))`,
`context=Present("Terminology "+token+".")`,
`preferredLabel=Present(LocalizedLabel("参照 "+token,None))`,
`source=S(owner,line)` and
`origin=Derived("rule:literal",Vector(S("input.a",70),S("input.b",71)))`.
RModel instead has `origin=Declared`; all its other Q fields remain intact.

| Reference | Owner / line | Target | Boundary | Token |
| --- | --- | --- | --- | --- |
| RA | terminology.a / 50 | TermTarget(TA) | Local | a-local |
| RAE | terminology.a / 50 | TermTarget(TA) | External | a-external |
| RModel | terminology.a / 50 | ModelTarget(ModelElementId("terminology.b","shared")) | Local | a-model |
| RB | terminology.b / 51 | TermTarget(TB) | Local | b-local |
| RForeign | terminology.b / 51 | TermTarget(TF) | External | b-foreign |
| REvent | terminology.a / 52 | TermTarget(TB) | Local | event-local |
| REventExternal | terminology.a / 52 | TermTarget(TB) | External | event-external |
| RAnonymous | terminology.anonymous / 54 | TermTarget(TA) | Local | anonymous-local |

Declared core reference vectors for ordinals 0..5 are, in order,
`Present(Vector(RA,RA,RAE,RModel))`, `Present(Vector(RB,RForeign))`,
`Present(Vector(REvent,REventExternal))`, `Present(Vector.empty)`,
`Present(Vector(RAnonymous,RAnonymous))`, and the same complete value again.
Each Terminology termReferences is the exact ordered TermTarget filter, with
every reference field and duplicate retained. Conceptual groups for 0..5 are
`Present(Vector(RModel,RA,RA))`, `Present(Vector.empty)`,
`Present(Vector(REvent))`, `Present(Vector.empty)`,
`Absent(Unsupported,"The conceptual group is unsupported.")`, and the same gap.
These groups are explicitly selected bounded sub-multisets. Candidate relation
names do not create synonym equivalence, clusters or inferred membership.

Every prefix element gets its exact core reference absence as termReferences,
and `Absent(NotRepresented,"No conceptual group is represented.")` as its group.
Each prefix Term's four supplemental fields are independently
`Absent(NotRepresented,"No Terminology fact is represented.")`.
Existing kind/name strings never infer supplemental facts.

## Supplied supplemental classification and labels

| Term | termType | bokKind | Ordered labels: text / language | preferredLabel: text / language |
| --- | --- | --- | --- | --- |
| TA | Present("event-type") | Present(Koto) | Present(共有/ja, Shared/en, 共有/ja, 無言語/None) | Present(優先外/ja) |
| TB | Present("entity-type") | Present(Mono) | Present(共有/ja, Shared/None) | Present(Preferred B/en) |
| TU | Present("opaque-unused") | Absent(NotApplicable,"The unused Term has no BoK classification.") | Present(Vector.empty) | Absent(NotDeclared,"No preferred label is declared.") |

Every label is a complete LocalizedLabel. Order, duplicate TA labels and
explicit language None survive. Preferred labels are independently supplied
and intentionally may be outside the labels vector. Entity 0 reaches TA/Koto;
Event 2 reaches TB/Mono. The opaque termType tokens do not classify either
record. BoK curators retain vocabulary and synonym authority.

## Explicit absence and stopping facts

The absence resource retains all six identities and sources, including two
complete equal anonymous records. New addressed core references use these
exact four gaps in ordinal order:

| Ordinal | Reason | Exact detail |
| --- | --- | --- |
| 0 | NotDeclared | No Terminology link is declared. |
| 1 | NotRepresented | The Terminology link is not represented. |
| 2 | Unsupported | The Terminology link is unsupported. |
| 3 | NotApplicable | The Terminology link is not applicable. |

Anonymous ordinals 4/5 have `Present(Vector.empty)` references. Each projection
retains its exact gap or present-empty filter; all six groups use
`Absent(Unsupported,"The conceptual group is unsupported.")`.

For new Term ordinal i=0..2 and field ordinal j=0..3 in termType, bokKind,
labels, preferredLabel order, the supplemental gap is G((i+j)%4):

| G | Reason | Exact detail |
| --- | --- | --- |
| 0 | NotDeclared | No Terminology fact is declared. |
| 1 | NotRepresented | The Terminology fact is not represented. |
| 2 | Unsupported | The Terminology fact is unsupported. |
| 3 | NotApplicable | The Terminology fact is not applicable. |

Present-empty, absence and unknown remain distinct. Names, paths, source lines,
digests, Term strings or array ordinals supply no anonymous identity or aliases.
Absent outbound facts stop navigation without a guessed link or network fallback.

## Reproducible JSON-only consumer questions

1. Parse a fixed resource as UTF-8 and call `CmlTerminologyMetadata.read`.
   Build `CmlTerminologyNavigation` from that admitted Graph. The consumer
   needs no CML parser or typed fixture builder.
2. Query the complete `ModelElementId("terminology.a","shared")` and exact TA.
   `links` returns RA, RA, RAE with full owning records and reference facts.
   Resolve the first Local link to the full TA TermProjection; resolve RAE to
   the unchanged ExternalTarget even though TA is registered locally.
3. Query `ModelElementId("terminology.a","event:shared")`. Its Local REvent
   resolves the full TB/Mono projection while the element kind remains `event`.
   Retain supplied labels, preferred label, opaque type, relation, profile,
   context, primary source and ordered Derived sources. Do not merge candidates.
4. Declared `allLinks` contains exactly 11 complete ordered occurrences.
   Local-only reverse TA returns six and TB two; TU is known and returns empty.
   Three External links are excluded. Each equal anonymous record contributes
   two complete RAnonymous links. Foreign TF remains UnknownTerm.
5. Absence `allLinks` is empty. The four addressed `links` values are the exact
   gaps above; anonymous records add no links. Unknown pairs return the fixed
   safe diagnostics, and equivalent full link copies retain membership.
6. Independently admit Structure, Classification, Dynamic and Use Case using
   the same full resource. Preserve their complete catalogs and carriers.
   The accepted requester → complete-order → fulfillment Workflow questions
   continue through qualified Use Case and Dynamic APIs, with independent
   Structure order Entity/Aggregate continuation. This is metadata navigation.

## Executable oracle and reproducible validation recipe

TF-01 declared and TF-02 absence compare complete catalog, projections,
envelope, producer/read/reconsumption JSON and an independent canonical string.
TF-03/04 ask JSON-only qualified semantic and ordered reverse questions.
TF-05 independently admits all peers. TF-06/07 use literal strict shape,
version, full-core/reference and occurrence failure expectations. TF-08 rebuilds
keys recursively while retaining authored arrays and required nulls.
TF-09 uses seed 42, at least 100 successes and zero discards; each sample runs
both modes, independently permuting reference occurrences, Catalog records,
Terms and both projection arrays with remaining indices without replacement.
Collected coverage reports actual vector changes and both modes.

Only the separately admitted accepted predecessor supplies prefix expected
facts. Every new typed fact is raw literal data; pure test-owned renderers encode
all fields and recursively sort keys to form the complete independent wire and
canonical oracles. New resource decoding and production rendering never supply
expected facts. This is semantic equality, without a saved-output or hash gate;
there is no RFC 8785 claim. Legacy compatibility remains in CmlModelMetadataSpec.

The parent-selected sixteen-suite logical recipe, from the Cozy root, is:

```text
sbt --batch "testOnly cozy.modeler.CmlSemanticFoundationSpec cozy.modeler.CmlSemanticMetadataSpec cozy.modeler.CmlStructureMetadataSpec cozy.modeler.CmlClassificationMetadataSpec cozy.modeler.CmlClassificationTopologySpec cozy.modeler.CmlClassificationMetadataFixtureSpec cozy.modeler.CmlDynamicMetadataSpec cozy.modeler.CmlDynamicTopologySpec cozy.modeler.CmlDynamicMetadataFixtureSpec cozy.modeler.CmlModelMetadataSpec cozy.modeler.CmlUseCaseMetadataSpec cozy.modeler.CmlUseCaseNavigationSpec cozy.modeler.CmlUseCaseMetadataFixtureSpec cozy.modeler.CmlTerminologyMetadataSpec cozy.modeler.CmlTerminologyNavigationSpec cozy.modeler.CmlTerminologyMetadataFixtureSpec"
```

Execute through registered `cncf_command_runner` and
`/Users/asami/.agents/skills/cncf-sbt-serial-execution/scripts/run-sbt-serial.sh`,
with scoped first-attempt escalation and normal caches. Wait for actual terminal
`lock=released` before another top-level SBT process. `testOnly` includes
root/Test compilation. S3 passed 214 tests in 16 suites with no failures or
aborted suites, SBT and wrapper exit zero, and the serial lock released.
TM-09, TN-08 and TF-09 each passed 100 samples with zero discards and all seven
actual coverage tags positive; TF-09 exercised both modes in every sample.
Independent complete Step review passed with no blockers. The parent manual
Step acceptance commit is `09d9ca8cc0d6188dc3d009764ce8ded24825b4ca`.

Phase 54.6 owns Event Storming traversal; Phase 54.7 owns final consumer fixture
acceptance. CNCF owns runtime realization and Textus CBD Support owns external
integration, views and acceptance. None is claimed completed here. The
[Phase](../phase/phase-54.5.md) and [checklist](../phase/phase-54.5-checklist.md)
record the completed Cozy supplier boundary.

The sole independent full Phase review passed with no blockers, Hygiene or
Development Candidates. Ordinary `sbt --batch test` passed 2835 tests in 198
suites, with zero failures or aborted suites and 9 pre-existing optional/deferred
canceled cases. SBT and wrapper exited zero; the serial lock was released.
The distinct parent manual local release binds `P545-CLOSURE-001@1`; its native
commit and closure receipt are verified after committing these final records.
