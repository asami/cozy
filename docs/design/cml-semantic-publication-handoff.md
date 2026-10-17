# CML semantic publication producer handoff

Cozy's producer boundary is the external versioned JSON protocol described by
the [publication contract](../spec/cml-semantic-publication.md) and
[publication design](cml-semantic-publication.md). `CmlSemanticPublication`,
Foundation and the family Scala APIs are internal `private[cozy]` components;
these names describe Cozy's implementation and executable examples, not a public
Scala library ABI promised to an external consumer. Textus CBD Support consumes
the JSON protocol and owns its integration, views, navigation and acceptance.

## Versions and independent admission

The outer version is `cozy.cml.semantic-metadata.v1` with exactly `schemaVersion`,
`elements`, `terms` and `extensions`. Contract version
`cozy.cml.semantic-strength.v1` documents semantic strength and adds no wire field.
Workflow and StateMachine are Dynamic carriers, not additional namespaces.

| Order | Namespace | Child version | Independent whole-root reader | Accepted handoff |
| --- | --- | --- | --- | --- |
| 1 | `cozy.cml.structure` | `cozy.cml.structure.v1` | `CmlStructureMetadata.read` | [Structure](cml-structure-metadata-handoff.md) |
| 2 | `cozy.cml.classification` | `cozy.cml.classification.v1` | `CmlClassificationMetadata.read` | [Classification](cml-classification-metadata-handoff.md) |
| 3 | `cozy.cml.dynamic` | `cozy.cml.dynamic.v1` | `CmlDynamicMetadata.read` | [Dynamic: Workflow and StateMachine](cml-dynamic-metadata-handoff.md) |
| 4 | `cozy.cml.use-case` | `cozy.cml.use-case.v1` | `CmlUseCaseMetadata.read` | [Use Case and Actor](cml-use-case-metadata-handoff.md) |
| 5 | `cozy.cml.terminology` | `cozy.cml.terminology.v1` | `CmlTerminologyMetadata.read` | [Terminology](cml-terminology-metadata-handoff.md) |
| 6 | `cozy.cml.causal` | `cozy.cml.causal.v1` | `CmlCausalMetadata.read` | [Causal](cml-causal-metadata-handoff.md) |

[Foundation](cml-semantic-foundation-handoff.md) owns exact qualified identities,
full ElementRecords/Terms, reference boundaries, provenance and occurrence
correspondence. Each family owns its unchanged child meaning and version. The
final publication gate independently admits every known supplied family, including
Classification and Terminology. The narrower Causal peer gate can admit an envelope
with an inconsistent embedded Classification/Terminology record; that success
does not substitute for final all-family admission.

An outer core change requires a new schema and separately designed migration.
A child change requires that supplier's version and design decision. No implicit
upgrade, prefix registration or legacy `CmlModelMetadata` conversion is defined.
Unknown namespaced objects, including `cozy.cml.future`, remain opaque objects:
apparent child versions, nested foreign schema versions, forged elements/Terms,
capability flags and shared names grant no admission, core entry or capability.
Opaque arrays, duplicates and `JsNull` survive unchanged. A malformed known
namespace is a failure, never an absent or opaque supplier.

## Reproducible producer and consumer recipe

1. Construct a Foundation catalog from complete caller-supplied records and Terms;
   handle its typed `Left` before constructing any Envelope. Preserve exact
   ModelElementId/TermId domains, all full reference fields and authored occurrences.
2. Use unchanged family builders to publish their admitted extensions. Build Causal
   from the exact admitted base containing its actual supplied peers. Do not remove
   populated peers and repair an existing Causal summary to make it admissible.
3. Build the base Envelope, then call `CmlSemanticPublication.build(envelope)`.
   For received UTF-8 JSON, parse it and call `CmlSemanticPublication.read(root)`.
   Both paths perform base admission followed by the six independent family gates.
4. Handle every `Left` as failure before render or query. Invalid base produces
   `PublicationFailure`, path `publication`, detail
   `The semantic metadata publication is invalid.` before supplier failures.
   A rejected supplied family produces `InvalidSupplier`, path
   `extensions.<namespace>`, detail `The supplied <label> metadata is not admitted.`
   Fixed order above selects the first invalid sibling irrespective of object order;
   diagnostics disclose no hostile payload, exception text or repair guess.
5. On `Right(publication)`, render with `CmlSemanticPublication.toJson` or
   `CmlSemanticPublication.canonicalJson`. Canonical text recursively sorts object
   keys; authored arrays, duplicates, exact scalars, nulls and full records retain
   their meaning. It is not RFC 8785 or a signature/currentness certification.
6. Parse canonical text as JSON and call `CmlSemanticPublication.read` again before
   querying. Treat the re-admitted full envelope and independently admitted family
   graphs as the consumer input. Read a typed `Present(graph)` before creating its
   navigation/topology/traversal; retain `Absent(reason, detail)` explicitly.
7. Query exact qualified identities through Catalog, TerminologyNavigation,
   UseCaseNavigation, ClassificationTopology, DynamicTopology and CausalTraversal.
   Handle query `Left` separately from publication and field availability. Explicit
   External links stop even when a matching target is locally resident.

For an internal Cozy caller the admission branch is, schematically:

```scala
CmlSemanticPublication.build(envelope) match {
  case Left(diagnostics) => handleLeft(diagnostics)
  case Right(publication) =>
    val text = CmlSemanticPublication.canonicalJson(publication)
    CmlSemanticPublication.read(Json.parse(text)) match {
      case Left(diagnostics) => handleLeft(diagnostics)
      case Right(consumer) => queryAdmittedFamilies(consumer)
    }
}
```

`handleLeft` and `queryAdmittedFamilies` denote caller responsibilities, not new
Cozy APIs. External consumers implement equivalent protocol admission and failure
handling under their own integration boundary; internal Scala visibility stays
unchanged. Rendering requires an admitted non-null Publication. Java null input,
keys or nested values fail safely; JSON null remains valid data.

## Eight valid empty availability cases

Use `Catalog.empty` and zero-carrier family builders. Each single-family publication
contains only its own actual accepted child. For all-admitted, combine the five
non-Causal children into an admitted base, then build Causal on that exact base.

| Case | Structure | Classification | Dynamic | Use Case | Terminology | Causal |
| --- | --- | --- | --- | --- | --- | --- |
| all-missing | Absent | Absent | Absent | Absent | Absent | Absent |
| structure-only | Present | Absent | Absent | Absent | Absent | Absent |
| classification-only | Absent | Present | Absent | Absent | Absent | Absent |
| dynamic-only | Absent | Absent | Present | Absent | Absent | Absent |
| use-case-only | Absent | Absent | Absent | Present | Absent | Absent |
| terminology-only | Absent | Absent | Absent | Absent | Present | Absent |
| causal-only | Absent | Absent | Absent | Absent | Absent | Present |
| all-admitted | Present | Present | Present | Present | Present | Present |

Every missing family is exactly
`Absent(NotDeclared, "No admitted <label> metadata is supplied.")`. Present
families retain their full empty carrier vectors and full Envelope. Causal-only
has empty `modelReferences`; `causalLinks` and `ruleContexts` have the Dynamic
absence, `actorOperations` and `collaboratorContexts` the Use Case absence, and
`aggregateBoundaries` the Structure absence. Its five owner-None gaps occur in
`causalLinks`, `actorOperations`, `aggregateBoundaries`, `ruleContexts`,
`collaboratorContexts` order, followed by the owner-None `causalInference` gap.
All-admitted has five `Present(Vector.empty)` fields, empty `modelReferences`,
and only the final gap. That final gap is exactly Unsupported with detail
`No causation is inferred from names, membership, generic relations or expression text.`

For either empty Causal graph, unknown `ModelElementId("empty.example", "missing")`
returns `UnknownElement`, path `node.identity`, detail
`The Causal element identity is unknown or unaddressable.` Missing family,
missing field, available-empty and unknown query remain four distinct outcomes.
The same diagnostic governs the absence input's unaddressable buyer, whose entire
caller source and absent core identity remain retained.

## Grounded fixtures, identity and semantic strength

Reproduce with the two unchanged complete input resources:
[declared](../../src/test/resources/cozy/modeler/causal-metadata-v1-declared.json)
and [absence](../../src/test/resources/cozy/modeler/causal-metadata-v1-absence.json).
The two test-owned question resources are
[declared questions](../../src/test/resources/cozy/modeler/semantic-publication-v1-declared-questions.json)
and [absence questions](../../src/test/resources/cozy/modeler/semantic-publication-v1-absence-questions.json).
The [seventeen-occurrence matrix](../spec/cml-semantic-publication.md#source-grounded-consumer-question-fixtures)
links complete Classification, cross-view, source and non-overreach answers.
Declared Q01..Q08 and absence Q01,Q02,Q05,Q06,Q08,A01..A04 are finite semantic
questions, not an external question language.

`ModelElementId(terminology.a, shared)` and
`ModelElementId(terminology.b, shared)` keep separate authorities and sources
at lines 50/51 despite the same `共有` name. `TermId(terminology.a, shared)`
is a different domain with glossary source line 60. All target, boundary, relation,
profile, context, preferredLabel, source and origin fields remain full facts.
Derived sources `input.a` line 70 and `input.b` line 71 retain that order.
Q01's Local/duplicate Local/resident External references keep full owning records;
six Local reverse occurrences survive, including four equal anonymous occurrences.
Q07's full nine-field generic External links stop for resident and nonresident
targets. Two equal anonymous causal core records at line 19 retain separate
ordered Dynamic Cause and Reaction payloads; source, name or ordinal grants no ID.

Only explicit Cause/Reaction assert direct causation. Affected subjects, aggregate
membership, RuleInput/RuleOutput and generic references provide contextual evidence.
Shared names, synonym candidates, conceptual groups, BoK labels, classification
dimensions, guards, expressions and workflow lists infer no equivalence, policy
result, runtime execution, transitive causation or read-model update.
Source authority/path/line values are synthetic caller declarations. These fixtures
claim no actual CML file, computed digest, source parsing or source currentness.

BoK remains the meaning authority; CNCF owns runtime; Textus CBD Support owns
external integration, views, navigation and acceptance. Those downstream
obligations are not Cozy producer blockers or completed consumer acceptance.

## Executable reproduction and observed acceptance boundary

The three publication specifications are
[supplier SP-01..08](../../src/test/scala/cozy/modeler/CmlSemanticPublicationSpec.scala),
[questions SF-01..11](../../src/test/scala/cozy/modeler/CmlSemanticPublicationFixtureSpec.scala),
and [compatibility PC-01..06](../../src/test/scala/cozy/modeler/CmlSemanticPublicationCompatibilitySpec.scala).
The legacy `CmlModelMetadataSpec` stays a separate validation suite; semantic
envelopes are not passed to its legacy parser.

The logical 22-suite reproduction command is:

```text
sbt --batch 'testOnly cozy.modeler.CmlSemanticPublicationCompatibilitySpec cozy.modeler.CmlSemanticPublicationSpec cozy.modeler.CmlSemanticPublicationFixtureSpec cozy.modeler.CmlSemanticFoundationSpec cozy.modeler.CmlSemanticMetadataSpec cozy.modeler.CmlStructureMetadataSpec cozy.modeler.CmlClassificationMetadataSpec cozy.modeler.CmlClassificationTopologySpec cozy.modeler.CmlClassificationMetadataFixtureSpec cozy.modeler.CmlDynamicMetadataSpec cozy.modeler.CmlDynamicTopologySpec cozy.modeler.CmlDynamicMetadataFixtureSpec cozy.modeler.CmlModelMetadataSpec cozy.modeler.CmlUseCaseMetadataSpec cozy.modeler.CmlUseCaseNavigationSpec cozy.modeler.CmlUseCaseMetadataFixtureSpec cozy.modeler.CmlTerminologyMetadataSpec cozy.modeler.CmlTerminologyNavigationSpec cozy.modeler.CmlTerminologyMetadataFixtureSpec cozy.modeler.CmlCausalMetadataSpec cozy.modeler.CmlCausalTraversalSpec cozy.modeler.CmlCausalMetadataFixtureSpec'
```

For managed agent execution the parent issues a fresh typed request to registered
`cncf_command_runner`, using
`/Users/asami/.agents/skills/cncf-sbt-serial-execution/scripts/run-sbt-serial.sh`.
The first attempt requests scoped escalation for normal boot locks, dependency
caches and local artifact repositories. Use normal caches and machine-wide
serialization; await terminal `lock=released` before another top-level SBT.
This reproducible recipe is supported by actual P547-S5473-VAL-001: compilation
succeeded and all 274 tests in the selected 22 suites passed. The actual runner
recorded sbt_exit=0, wrapper_exit=0 and lock=released.

Observed S1 acceptance: 257 tests/20 suites, SP-08 seed 42 with 100 successes,
zero discards and four coverage counts of 100, focused review PASS, verified native
commit `0b1db8996cc4979cbab029a801ae82f0611f7e43`, repair 1. Observed S2 acceptance:
268 tests/21 suites, SF-11 seed 42 with 100 successes, zero discards, all 21
coverage counts of 100 and 31 execution orders, focused review PASS, verified
native commit `2f7d1852b9d4b1e55e13d1200799673e00624606` with S1 as sole parent,
repair 1; SP-08 seed 42 also passed with 100 successes/zero discards/all four
coverage counts100.
The historical S2 failure, infrastructure stop, bounded repair and accepted fresh
validation remain recorded in the [journal](../journal/2026/10/2026-10-05-phase-54.7-semantic-publication.md).

PC-01..06 and this handoff have accepted behavior and whole-file conformance for
[S54.7.3](../phase/s54.7.3.md). The actual 274 tests in 22 suites include all six
PC and all eleven SF examples. PC-06, SP-08 and SF-11 each ran seed 42 with 100
successes, zero discards and passed=true; all six, four and 21 respective coverage
counts were 100, with 31 SF-11 question execution orders. Fresh independent
P547-S5473-REVIEW-001 returned PASS over all eighteen Step paths, covering full
typed/raw/canonical oracles, eight valid empty publications, nine strict adversaries,
qualified complete provenance/occurrences/External stopping and this stable handoff.
S3 is CLOSED at native Step commit `abe935c6e2144b04308e16f10e4f82fed92c471b`,
sole parent S2. Verified `s5473-parent-manual-commit-receipt-001.json` records the
exact eighteen accepted paths; all thirteen original entries in
[its checklist](../phase/s54.7.3-checklist.md) are accepted, with repair 0.

The sole full Phase review `P547-FULL-REVIEW-001 r1` is PASS over original base
`8099e3e3c16c98127df53b7344f5b54841105993` through accepted S3
`abe935c6e2144b04308e16f10e4f82fed92c471b`, all 24 paths/four whole programs/
four outcomes/five obligations, with no CPB, HYG or DEV. Actual ordinary
`P547-FINAL-VAL-001`, logical `sbt --batch test`, passed 2895 tests in 204 suites,
failed=0/aborted=0/ignored=0/pending=0 and nine existing optional/deferred
cancellations. Four real Docker Remotion tests require
`cozy.video.remotion.integration=true`; five historical Phase 51 CV-01 cases are
deferred to CV-04/CV-05/CV-06/SP-01, with sources unchanged. All 27 new publication
behaviors passed, none canceled. PC-06/SF-11/SP-08 each observed seed 42/100
successes/zero discards/pass, all six/21/four coverage counts100 and 31 SF-11
execution orders. Actual SBT=0, wrapper=0, lock=released and permission=consumed.
Log: `/private/tmp/skill.cncf.d/a-311bd647-e362-43a0-ba2d-4b9c8c53736b/32505-20261005T035755Z.log`;
summary: `/private/tmp/skill.cncf.d/a-311bd647-e362-43a0-ba2d-4b9c8c53736b/32505-20261005T035755Z.summary.json`.

The distinct final release contains canonical Phase CLOSED closure and binds
`P547-CLOSURE-001@1`; its native release OID, committed-tree receipt and final
requirement audit are verified after committing and retained in parent workflow
evidence. Cozy has completed the versioned consumer-neutral JSON contract,
source-grounded fixtures and reproducible handoff. Textus CBD Support owns external
integration/views/navigation/acceptance, BoK canonical meaning/synonyms, and CNCF
runtime. No external acceptance is claimed; Scala 2.12.18 and 0.3.3-SNAPSHOT are
unchanged.
