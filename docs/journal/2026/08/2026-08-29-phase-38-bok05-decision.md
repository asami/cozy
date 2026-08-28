# Phase 38 BOK38-05 Decision Resolution

## Decision Resolution Record

- Decision ID: `P38-BOK05-DEC-001`
- Phase / Step: Phase 38 / BOK38-05 Optional Extensions and Legacy Migration
- Answer: the developer authorized `bok.extensions.rdf` as the explicit list
  of declarations below `src/main/extensions/rdf`, and authorized
  `cozy.bok.rdf-extension.v1` as the implementation schema.
- Selected option: implement the explicit declaration admission, deterministic
  validation, collision rejection, and integration boundary under the accepted
  generated-knowledge contract.
- Legacy-reader disposition: do not add a legacy source reader before the
  BOK38-06 driver acceptance. Existing legacy source paths remain neither
  scaffolded nor source authority.
- Affected authoritative records: `docs/design/bok-generated-knowledge-boundary.md`,
  `docs/spec/bok-generated-knowledge-boundary.md`, `docs/phase/phase-38.md`,
  and `docs/phase/phase-38-checklist.md`.
- Authorized next state: `PARENT_CAPABILITY_CHECK`, then BOK38-05 planning.
- Consumed: `true`

This record authorizes no publication, upload, push, deployment, external
project mutation, or Phase closure.
