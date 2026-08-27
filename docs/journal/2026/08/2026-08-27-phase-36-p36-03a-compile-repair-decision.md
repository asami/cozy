# Phase 36 P36-03A Compile Repair Decision

Decision ID: `P36-03A-COMP-003`

## Pending boundary

Focused validation invocation `41502-20260826T231648Z` stopped before the
target specs ran. The preceding, consumed repair decision
`P36-03A-DEC-001` admitted only two interpolation literal repairs. The
compiler then exposed the same Scala 2.12 escaped-string form at three
additional locations in `CozyVisualPagePreview.scala`.

## Attributable answer

The developer authorized: `P36-03A-COMP-003 の source-only compile repair を許可する`.

## Resolution

- Selected option: one source-only compile repair of the remaining three
  escaped interpolation literals, at the ordered-page, logical-node, and
  typed-parameter HTML entries.
- Affected identity: Phase 36 / P36-03A / `P36-03A-COMP-003`, at committed
  base `e4d76b996c54cf8237c0e2b8fc1d8aa7216ef1e6`, with the uncommitted
  P36-03A preview delta and the three separately preserved Phase records.
- Frozen boundary: no output contract, documentation, executable
  specification, renderer, receipt, review, video, or external-repository
  change is admitted.
- Required follow-up: rerun the unchanged focused command
  `testOnly cozy.media.CozyVisualPageSpec cozy.media.CozyVisualPagePreviewSpec`
  through the serialized SBT runner, then continue the normal Step review
  route only on a clean result.
- Authorized next state: `FIX`.
- Consumed: `true`.
