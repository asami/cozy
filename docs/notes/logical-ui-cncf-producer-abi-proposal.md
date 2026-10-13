# Logical UI CNCF Producer ABI Proposal

Status: non-normative proposal/history; reconciled with received fixed LUI-ABI-01
Current contract: [producer spec](../spec/logical-ui-generated-producer.md) and
[design](../design/logical-ui-generated-producer.md); documentary contract accepted after independent PASS; implementation verification pending
Date: 2026-10-03
Consumer: goldenport-cncf Phase 96

## Principle

Logical UI follows the same producer ABI mechanism as other CML model elements. Cozy semantic/compiler code is Scala 2.12, but generated application source targets the Scala 3 CNCF public ABI. Binary compatibility between Cozy and CNCF is not required.

## Semantic source

Phase 74 remains authoritative for target-neutral Logical UI semantics. Phase 76 must project from that authority; it must not create a second Logical UI vocabulary merely to match CNCF implementation details.

## Producer pipeline

```text
Phase 74 semantic values
  -> LogicalUiProducerProjection
  -> CNCF ABI projection values
  -> Scala source generation
  -> generated metadata provider/bootstrap
  -> CNCF ComponentFactory
```

The original pipeline sketch remains proposal history. The fixed contract now
uses ordinary LogicalSection AST/source COMPONENT ownership, immutable static
shared-semantics definitions, pure admitted consumer-shape projection and Realm
source rendering in both CNCF modeler routes, with library isolation.

## Mapping discipline

Maintain an explicit mapping table in implementation/tests for every admitted Phase 74 concept:

- Purpose/Display/InteractionPattern subset where required by consumer ABI;
- List/ListItem/Detail;
- Section/Field;
- display Value semantics;
- Action;
- presentation roles;
- selection/detail identity only if CNCF runtime definition requires it;
- contract/source provenance.

If CNCF does not need a Phase 74 value at runtime, do not emit it merely for completeness. If CNCF needs runtime information not represented by Phase 74 semantics, resolve ownership before adding producer data.

## Generated provider

The received interface is org.goldenport.cncf.display.GeneratedLogicalUiMetadataProvider,
with generatedLogicalUiDefinitions: Vector[GeneratedLogicalUiAbi.Definition].
The fixed output is a component-package .logicalui definitions object plus
public metadata-provider mixin trait. The application component or actual
factory explicitly mixes it in and supplies View/value/action bindings; the
generator does not rewrite user factory files. Provider code is inert metadata.

## Versioning

Producer output pins the CNCF Logical UI ABI version. Unknown/incompatible ABI versions fail at CNCF admission. Future additive vocabulary changes require explicit producer/consumer compatibility rules; generator output must not guess support from classpath reflection.

## Cross-repository fixture

CNCF owns the expected generated shape first. Cozy has two fixture layers:

1. producer-local fixture comparing normalized semantics and generated source to the expected shape;
2. actual generated fixture handed to CNCF Phase 96 for Scala 3 compile/admission/runtime acceptance, using fixed development source/artifact coordinates.

This keeps producer correctness and consumer runtime acceptance separate while binding them to one ABI.

## Handoff evidence and sequencing

Use the same handoff IDs as CNCF Phase 96:

- LUI-ABI-01: CNCF supplies the fixed consumer revision/coordinates, ABI version,
  public types/provider signature, semantic mapping, unsupported subset,
  expected-generated fixture and focused admission/projection evidence.
- LUI-GEN-01: Cozy supplies the producer revision, reproducible generator command,
  Phase 74 input fixture/provenance, target LUI-ABI-01 and actual generated
  Scala/provider files with deterministic producer evidence.
- LUI-ACCEPT-01: CNCF records compilation of those generated files, normal
  ComponentFactory discovery/admission, negative admission cases and equivalent
  List/Detail values/IDs/order without Cozy on the consumer runtime classpath.

Producer-local tests establish generation behavior; the consumer receipt
establishes runtime acceptance. Both ledgers may reference the same receipt for
unchanged inputs. Neither receipt means the other Phase is closed. A fixed
development handoff is sufficient; no public release is implied or required.

Generated definitions carry static configuration and bounded converter/action/
field-binding references as admitted by the CNCF ABI. They do not embed current
resource values, Entity revisions, authorization, executable callbacks or a
ready-made Display instance. The Phase 74 semantic contract, CNCF ABI version,
Logical UI definition version and later Display wire version are distinct.

Earlier P96-01B candidate wording is superseded by the received fixed
[LUI-ABI-01 source handoff](../../../cloud-native-component-framework-phase96/docs/journal/2026/10/2026-10-04-phase-96-logical-ui-abi-handoff.md)
and [consumer spec](../../../cloud-native-component-framework-phase96/docs/spec/logical-ui-generated-abi.md):
cncf.logical-ui-generated.v1, integer version 1. It carries explicit
View/value/action binding keys and ordered allowedKinds, without entity
constraints, mutation targets or editable-metadata maps. Keys identify
application registrations, without setter/inverse-write inference. Future
extensions require consumer admission. This received working-tree source
contract includes dirty A/B1/B2 focused evidence; it is not public publication,
independent P96-03 acceptance or actual producer generation.

Use the [Phase 76 checklist](../phase/phase-76-checklist.md) for current work.
Exact names and version spelling are fixed by LUI-ABI-01 and transcribed in the
producer spec. The [contract journal](../journal/2026/10/2026-10-04-phase-76-producer-contract.md)
discloses source coordinates, handwritten sample identity and pending actual
generation/consumer proof. Phase 51 is PLANNED with no Flutter source/suites;
preserve its plan and existing target-neutral semantics without claiming
Flutter execution. This note remains non-normative history.

## Prohibitions

- no Cozy runtime class reference in generated Scala 3 source;
- no dependency on cozy_2.12 from CNCF;
- no JSON bridge as a substitute for the generated ABI;
- no runtime CML parsing;
- no Flutter/TFAF type in generated CNCF ABI source;
- no duplicated handwritten semantic authority.
