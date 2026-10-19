# Phase 78 consumer acceptance handoff

## Final Cozy disposition — 2026-10-18

Cozy Phase 78 is CLOSED. Both delivery Steps are accepted and committed.
Current generated compilation and declared typed consumer semantics are
accepted. The additional Cozy-owned executable resource proves that a writer
JVM terminates before a distinct reader JVM reconstructs the same persisted
generated Entity. Independent focused closure resolves CPB-P78-001; the sole
comprehensive Phase review, final Cozy FULL validation and release closure are
accepted. The [canonical checklist](../../../phase/phase-78-checklist.md) is complete.
This closes only Cozy Phase 78; CNCF Phase 100, Workbench and migration ancestry
integration retain their own owners and obligations. HYG-P78-001 remains a
separate structural-maintenance follow-up without current-boundary impact.

## Migration checkpoint history — 2026-10-18

The separate main-history integration incorporates migration checkpoint
`713965d6d96a80b383127fcea3a18e254723dd3b` while retaining the accepted Phase 78
state at `b1b2c760a24bb10a3aa14b2d444c9eab2eacb68d`. The checkpoint's two Scala
files already match that accepted state. The producer inventory retains the
original entries and later intake; the specification retains the original
pending evidence as history alongside its current accepted contract.

The checkpoint remains a non-acceptance preservation record. Its incorporation
adds ancestry without creating new Phase acceptance or reopening Phase 78.
The separate history-integration obligation noted above is thereby fulfilled;
CNCF Phase 100 and Workbench retain their own scope. No source, executable
specification or build configuration is changed by this integration.

## Acceptance stages before final closure

The following preserves the staged handoff and its then-pending status.
The final disposition above supersedes that pending wording; it does not
retroactively turn provider reopen into process-restart evidence.

On October 18, 2026, [Cozy Phase 78](../../../phase/phase-78.md) records CNCF
DSM-100-06 provider close/reopen evidence for its selected producer fixture. The producer is Cozy
0.3.3-SNAPSHOT; the consumer is CNCF 0.5.3-SNAPSHOT. The declared contract
remains the [logical type generation specification](../../../spec/datastore-logical-type-generation.md)
and the frozen CNCF DSM-100-02 ABI.

CNCF's authoritative `src/test/cozy/phase100-store.cml` was regenerated through
its existing `Test / sourceGenerators` with the current Cozy source route and
CNCF's official current runtime descriptor/version. Compilation of those actual
generated sources and CNCF-owned
`org.goldenport.cncf.entity.Phase100GeneratedStoreSpec` establish consumer
acceptance beyond Cozy's source-output assertions. The CML fixture covers
String, XJson, XSemiStructuredData, native scalar, application class and repeated
declarations. Nested collection acceptance also uses existing generated
semantic-AST fixtures; those are distinct from the newly regenerated CML fixture.
The existing Cozy helper's 0.5.2-SNAPSHOT source-oracle fixtures are unchanged
and remain a separate evidence boundary.

The consumer accepts typed EntityStore memory/SQLite behavior and SQLite
close/reopen readback with the declared String, structured, native, class and
ordered collection identities. JSON-looking String retains String semantics;
XJson and XSemiStructuredData remain distinct, with class codecs and recursive
element bindings selected by declaration. Explicit JSON/YAML/HOCON decoder
selection belongs to Core/CNCF and remains independent of physical JSON text
mapping. No payload, runtime-value or SQL-type inference is introduced.

CPB-P78-001 distinguishes this accepted provider shutdown/reopen evidence within
one JVM from the frozen OS-process restart requirement. The first three P78-05
items remain satisfied, while only the DSM-100-06 acceptance checkbox is reopened
in the [Phase checklist](../../../phase/phase-78-checklist.md). Both accepted Step
commits and the independent acceptance Step review remain historical facts;
neither is rewritten as new process-restart evidence.

The Cozy-owned Scala3 executable fixture
`src/test/resources/cozy/modeler/phase78/GeneratedStoreProcessRestartSpec.scala`
is authored for consumer compilation using CNCF's existing generated Proposal
and public store APIs. Its writer must save, close its provider and terminate
before an independent reader JVM loads the same SQLite database/entity identity.
The reader compares generated typed values to independently authored fixed
literals, including exact whitespace/Unicode JSON-looking String, empty/nested/
null Json and Record, precise BigDecimal, native fields and required/optional/
repeated application values. It performs no save and consumes no writer values.
Distinct writer/reader/parent PIDs, successful exits and readback markers are
required. Child output uses test-owned logs and finite waits with cleanup only
of fixture-owned children; outputs live under CNCF `target/phase78-process-restart`.
No CNCF source, index or build file is changed. Parent-run cross-JVM validation
and independent focused closure remain pending; authoring records no new success.

Downstream acceptance Step criteria are distinct from the final Phase gates:
final Cozy FULL validation, independent Phase review and closure remain pending,
and Phase 78 remains in progress. This record does not close CNCF
Phase 100 or Workbench work; source-only migration ancestry integration remains
outside this Phase. Existing behavioral scenarios supply the evidence; this
original documentation projection changed no scenario, product behavior or API.
The CPB-P78-001 repair adds only the pending consumer-compiled executable fixture
and corrects current acceptance status.
