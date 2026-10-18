# Phase 78 consumer acceptance handoff

On October 18, 2026, [Cozy Phase 78](../../../phase/phase-78.md) records CNCF
DSM-100-06 acceptance of its selected producer fixture. The producer is Cozy
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

This handoff satisfies the first four P78-05 items in the
[Phase checklist](../../../phase/phase-78-checklist.md). Downstream acceptance
Step criteria are distinct from the final Phase gates: final Cozy FULL
validation, independent Phase review and closure remain pending. The independent
acceptance Step review is accepted; so Phase 78 remains in progress. This record does not close CNCF
Phase 100 or Workbench work; source-only migration ancestry integration remains
outside this Phase. Existing behavioral scenarios supply the evidence; this
documentation projection changes no scenario, product behavior or API.
