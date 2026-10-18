# Phase 78 Checklist: DataStore Logical Type Generation and Storage Mapping Metadata

status=closed
closed_at=2026-10-18
phase=[Phase 78](phase-78.md)

Delivery grouping (2026-10-18): P78-PRODUCER integrates P78-01 through P78-04;
P78-ACCEPTANCE owns P78-05. All five sections and every declared obligation are
DONE. Both delivery Steps are accepted and committed. Current generated-source
compilation, provider reopen and separate-JVM restart/readback are accepted.
The sole comprehensive Phase review, focused CPB-P78-001 repair closure and
final Cozy FULL validation are accepted; the final release closes Phase 78.
External-format selection retains the frozen Core/CNCF decoder contract and
unsupported/incompatible declaration diagnostics retain inherited generator
responsibility within the [producer specification](../spec/datastore-logical-type-generation.md).
The [consumer handoff](../journal/2026/10/2026-10-18-phase-78-consumer-acceptance.md)
distinguishes current regenerated CML, existing generated nested semantic-AST
fixtures, provider reopen and the additional process-restart executable resource.

## P78-01: Producer inventory and contract intake

- [x] Consume the frozen CNCF Phase 100 DSM-100-02 producer-facing contract and representative expected metadata fixture.
- [x] Inventory CML/Cozy `DataType.Named("record")` producers and all supported behavior they currently carry.
- [x] Inventory JSON, String, class/application and array declaration/generation paths relevant to the selected Phase 100 scope.
- [x] Record the selected generated artifacts and downstream CNCF acceptance fixtures.

## P78-02: Canonical logical datatype model

- [x] Represent `XJson` and `XSemiStructuredData` as distinct canonical semantic datatypes.
- [x] Preserve String independently of JSON-looking content.
- [x] Preserve class/application type identity and array/element identities.
- [x] Represent explicit XSemiStructuredData external-format selection required by DSM-100-02 without payload inference.
- [x] Define structured Cozy diagnostics for unsupported or incompatible declarations.

## P78-03: Generated Scala and schema metadata

- [x] Generate canonical XJson and XSemiStructuredData identities through the existing Scala generation route.
- [x] Generate schema/entity/storage-mapping metadata required for CNCF codec selection.
- [x] Preserve nested and array element logical identities where required by the CNCF contract.
- [x] Keep SQL physical type separate from logical datatype metadata.
- [x] Do not add runtime-value, payload-shape or string-name codec-selection logic.

## P78-04: Record workaround retirement

- [x] Map every supported selected-scope `DataType.Named("record")` behavior to its formal replacement responsibility.
- [x] Regenerate affected authoritative artifacts from their models.
- [x] Prove no active selected producer output/schema depends on `DataType.Named("record")`.
- [x] Prove no `Named("json")` or equivalent name-only workaround replaces it.
- [x] Retain historical/rejection fixtures only where clearly non-runtime.

## P78-05: Cross-project acceptance

- [x] Compile generated fixtures against the CNCF Phase 100 producer-facing ABI.
- [x] Hand String, XJson, XSemiStructuredData, class and array fixtures to CNCF DSM-100-03/04.
- [x] Prove generated metadata selects distinct declared semantics without payload inference.
- [x] Obtain CNCF DSM-100-06 acceptance for a Cozy-generated restart roundtrip fixture, including terminated writer JVM and independent fresh reader JVM; CPB-P78-001 is resolved.
- [x] Complete Cozy focused/full validation and independent review.
- [x] Record closure only after the CNCF acceptance handoff is evidenced.
