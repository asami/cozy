# SPI-only Component Generation Handoff

date=2026-07-28
status=proposed
source=textus-supervisor standalone supervisor integration
owner=Cozy

## Trigger

`textus-supervisor` is a CAR whose externally consumable boundary is the CNCF
`Supervisor` SPI. It needs a generated component identity and bundle root, but
it does not need a parallel Service, Operation, Entity, or Value API for
lifecycle authority.

A component-only CML model with a participant `COMPONENTLET` currently produces
no Scala artifact. Generation provenance then rejects the result with
`GENERATION_PROVENANCE_OUTPUT_TAMPERED` because
`GenerationProvenance._write` unconditionally requires at least one generated
Scala file:

```text
expected: at least one generated Scala artifact
actual:   none
message:  Generation provenance cannot describe an empty output.
```

Adding an unrelated `VALUE`, or a descriptive query Service solely to force
source emission, makes provenance advance but changes the domain contract. It
is not an acceptable workaround. In particular, a fabricated
`SupervisorIdentity` value neither represents a required public operation nor
solves the missing generated component contract by itself.

## Required Boundary

The CML model must be allowed to say only what the CAR actually is:

- one `COMPONENT` with stable package and component identity;
- one participant `COMPONENTLET` representing the embedded SPI provider;
- no Service, Operation, Entity, or Value unless the component has that real
  domain surface;
- lifecycle authority remains exclusively on the CNCF `Supervisor` SPI.

Cozy must not require a synthetic domain declaration as a generation trigger.

## Cozy Ownership

Cozy owns the model interpretation, generated component root, source-emission
contract, and generation-provenance semantics for this case.

The preferred outcome is that a valid component or participant declaration
emits the real generated component root required by the CAR implementation,
such as `TextusSupervisorComponent`. If a supported SPI-only CAR legitimately
requires no generated Scala at all, Cozy must instead model zero-output
generation explicitly and distinguish it from missing or tampered expected
output. The provenance layer must not infer the domain workaround.

The investigation should determine separately:

1. whether component/participant declarations are being lost or ignored before
   Scala generation;
2. which CML declaration owns generation of the component root used by a CAR
   `ComponentFactory`;
3. whether an empty artifact set is a supported successful result;
4. how provenance records a supported empty result without weakening detection
   of removed, changed, or unexpectedly absent generated files.

## Acceptance Evidence

The handoff is complete only when Executable Specifications prove:

1. a minimal component plus participant `COMPONENTLET` fixture needs no
   fabricated Service, Operation, Entity, or Value;
2. generation succeeds for that fixture;
3. when the CAR runtime contract requires a generated component root, the
   expected component type and factory-facing contract are emitted and compile;
4. if zero generated Scala files are an admitted result, provenance records and
   validates that result deterministically;
5. provenance still rejects deletion or mutation of every artifact that the
   model is expected to generate;
6. cold and repeated generation produce identical artifact identities and
   provenance bytes;
7. a downstream `textus-supervisor` driver can implement the CNCF `Supervisor`
   SPI and compile without adding a fake public domain surface.

## Out of Scope

- Adding lifecycle commands or descriptive status queries to
  `textus-supervisor`.
- Creating a `SupervisorIdentity` value solely to satisfy the generator.
- Moving standalone lifecycle authority away from the CNCF `Supervisor` SPI.
- Weakening provenance validation for ordinary models that are expected to
  generate Scala sources.

## Downstream Handoff Back to textus-supervisor

After the corrected Cozy development version is available,
`textus-supervisor` should remove any synthetic generation-trigger declarations,
regenerate from the minimal SPI-only CML, compile its `ComponentFactory`, and
run its driver specifications. Textus Control Center can then consume the
standalone supervisor through the existing CNCF SPI without operating the
launcher-private supervisor.
