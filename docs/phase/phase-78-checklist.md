# Phase 78 Checklist: DataStore Logical Type Generation and Storage Mapping Metadata

status=in-progress
phase=[Phase 78](phase-78.md)

Delivery grouping (2026-10-18): P78-PRODUCER integrates P78-01 through P78-04;
P78-ACCEPTANCE owns P78-05. All five canonical sections and every obligation
remain. Parent-owned focused validation and independent review have accepted
P78-PRODUCER, so P78-01 through P78-04 are DONE. P78-05 remains OPEN until
its own acceptance, final validation and Phase closure gates complete. External-format selection is consumed
from the frozen Core/CNCF decoder contract; unsupported/incompatible declaration
diagnostics remain inherited generator responsibilities within the supported
bounds in [the producer specification](../spec/datastore-logical-type-generation.md).

P78-05 uses CNCF `src/test/cozy/phase100-store.cml` and existing
`Test / sourceGenerators` -> compiled generated source ->
`Phase100GeneratedStoreSpec`, with the explicit current Cozy development root
and CNCF's generated current runtime descriptor/version. Cozy helper
0.5.2-SNAPSHOT source fixtures remain separate. No current runtime acceptance
is claimed by this documentation intake.

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

- [ ] Compile generated fixtures against the CNCF Phase 100 producer-facing ABI.
- [ ] Hand String, XJson, XSemiStructuredData, class and array fixtures to CNCF DSM-100-03/04.
- [ ] Prove generated metadata selects distinct declared semantics without payload inference.
- [ ] Obtain CNCF DSM-100-06 acceptance for a Cozy-generated restart roundtrip fixture.
- [ ] Complete Cozy focused/full validation and independent review.
- [ ] Record closure only after the CNCF acceptance handoff is evidenced.
