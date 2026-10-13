# Phase 78 Checklist: DataStore Logical Type Generation and Storage Mapping Metadata

status=planned
phase=[Phase 78](phase-78.md)

## P78-01: Producer inventory and contract intake

- [ ] Consume the frozen CNCF Phase 100 DSM-100-02 producer-facing contract and representative expected metadata fixture.
- [ ] Inventory CML/Cozy `DataType.Named("record")` producers and all supported behavior they currently carry.
- [ ] Inventory JSON, String, class/application and array declaration/generation paths relevant to the selected Phase 100 scope.
- [ ] Record the selected generated artifacts and downstream CNCF acceptance fixtures.

## P78-02: Canonical logical datatype model

- [ ] Represent `XJson` and `XSemiStructuredData` as distinct canonical semantic datatypes.
- [ ] Preserve String independently of JSON-looking content.
- [ ] Preserve class/application type identity and array/element identities.
- [ ] Represent explicit XSemiStructuredData external-format selection required by DSM-100-02 without payload inference.
- [ ] Define structured Cozy diagnostics for unsupported or incompatible declarations.

## P78-03: Generated Scala and schema metadata

- [ ] Generate canonical XJson and XSemiStructuredData identities through the existing Scala generation route.
- [ ] Generate schema/entity/storage-mapping metadata required for CNCF codec selection.
- [ ] Preserve nested and array element logical identities where required by the CNCF contract.
- [ ] Keep SQL physical type separate from logical datatype metadata.
- [ ] Do not add runtime-value, payload-shape or string-name codec-selection logic.

## P78-04: Record workaround retirement

- [ ] Map every supported selected-scope `DataType.Named("record")` behavior to its formal replacement responsibility.
- [ ] Regenerate affected authoritative artifacts from their models.
- [ ] Prove no active selected producer output/schema depends on `DataType.Named("record")`.
- [ ] Prove no `Named("json")` or equivalent name-only workaround replaces it.
- [ ] Retain historical/rejection fixtures only where clearly non-runtime.

## P78-05: Cross-project acceptance

- [ ] Compile generated fixtures against the CNCF Phase 100 producer-facing ABI.
- [ ] Hand String, XJson, XSemiStructuredData, class and array fixtures to CNCF DSM-100-03/04.
- [ ] Prove generated metadata selects distinct declared semantics without payload inference.
- [ ] Obtain CNCF DSM-100-06 acceptance for a Cozy-generated restart roundtrip fixture.
- [ ] Complete Cozy focused/full validation and independent review.
- [ ] Record closure only after the CNCF acceptance handoff is evidenced.
