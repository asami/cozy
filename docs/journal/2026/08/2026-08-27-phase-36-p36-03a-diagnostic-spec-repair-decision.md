# Phase 36 P36-03A Diagnostic Spec Repair Decision

Decision ID: `P36-03A-TEST-002`

## Pending boundary

After `P36-03A-TEST-001`, focused validation invocation
`65101-20260827T001507Z` still failed the same ScalaCheck conjunction at
`CozyVisualPagePreviewSpec.scala:156`. The property reports only its final
boolean, so the remaining false predicate cannot be determined from the
existing persistent test evidence.

## Attributable answer

The developer authorized: `P36-03A-TEST-002 の diagnostic spec repair を許可する`.

## Resolution

- Selected option: add diagnostic labels to the existing, unchanged boolean
  predicates in the one property, so a failing generated case identifies the
  false predicate without changing Preview behavior or accepted semantics.
- Affected identity: Phase 36 / P36-03A / `P36-03A-TEST-002`, at committed
  base `e4d76b996c54cf8237c0e2b8fc1d8aa7216ef1e6`, with the uncommitted
  P36-03A preview delta and the three separately preserved Phase records.
- Frozen boundary: no production source, output contract, documentation,
  generator semantics, renderer, receipt, review workflow, video, or
  external-repository change is admitted.
- Required follow-up: rerun the unchanged focused command
  `testOnly cozy.media.CozyVisualPageSpec cozy.media.CozyVisualPagePreviewSpec`
  through the serialized SBT runner, then classify the identified predicate
  before any semantic or further test change.
- Authorized next state: `FIX`.
- Consumed: `true`.
