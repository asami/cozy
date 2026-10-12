# Logical UI CNCF Producer ABI Proposal

Status: proposed Phase 76 specification
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

Names are candidates; existing Cozy projector/generator naming conventions take precedence.

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

Use the CNCF-provided generated metadata-provider interface exactly. Cozy should emit component code that implements/exposes the provider in the same style as existing generated ABI providers. Provider code is metadata/bootstrap only; it contains no runtime execution logic.

## Versioning

Producer output pins the CNCF Logical UI ABI version. Unknown/incompatible ABI versions fail at CNCF admission. Future additive vocabulary changes require explicit producer/consumer compatibility rules; generator output must not guess support from classpath reflection.

## Cross-repository fixture

CNCF owns the expected generated shape first. Cozy has two fixture layers:

1. producer-local fixture comparing normalized semantics and generated source to the expected shape;
2. released/generated fixture consumed by CNCF Phase 96 for actual Scala 3 compile/admission/runtime acceptance.

This keeps producer correctness and consumer runtime acceptance separate while binding them to one ABI.

## Prohibitions

- no Cozy runtime class reference in generated Scala 3 source;
- no dependency on cozy_2.12 from CNCF;
- no JSON bridge as a substitute for the generated ABI;
- no runtime CML parsing;
- no Flutter/TFAF type in generated CNCF ABI source;
- no duplicated handwritten semantic authority.
