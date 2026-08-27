# Phase 36 P36-03A Spec Repair Decision

Decision ID: `P36-03A-TEST-001`

## Pending boundary

Focused validation invocation `57868-20260827T000040Z` compiled successfully
but failed one ScalaCheck property at
`CozyVisualPagePreviewSpec.scala:156`. The property rejected the substring
`review`, although every generated Preview HTML document necessarily contains
the word `Preview`, and `preview` contains that substring. The source and the
normative Preview contract both intentionally describe the output as a semantic
review artifact.

## Attributable answer

The developer authorized: `P36-03A-TEST-001 の spec-only repair を許可する`.

## Resolution

- Selected option: change only the contradictory property token from `review`
  to the precise prohibited integration term `review-state`.
- Affected identity: Phase 36 / P36-03A / `P36-03A-TEST-001`, at committed
  base `e4d76b996c54cf8237c0e2b8fc1d8aa7216ef1e6`, with the uncommitted
  P36-03A preview delta and the three separately preserved Phase records.
- Frozen boundary: no production source, output contract, documentation,
  renderer, receipt, review workflow, video, or external-repository change is
  admitted.
- Required follow-up: rerun the unchanged focused command
  `testOnly cozy.media.CozyVisualPageSpec cozy.media.CozyVisualPagePreviewSpec`
  through the serialized SBT runner, then continue the normal Step review
  route only on a clean result.
- Authorized next state: `FIX`.
- Consumed: `true`.
