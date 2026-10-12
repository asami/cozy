# Phase 76: Logical UI Generated ABI Producer

status=planned
planned_at=2026-10-03
depends_on=Phase 74
consumer=goldenport-cncf Phase 96
driver=KnowledgeHubProject/nict-editing-studio-app

## Goal

Extend the ordinary CML -> generated Scala -> CNCF producer path to the Logical UI semantics frozen by Phase 74.

Cozy remains a Scala 2.12 compiler/tool. It does not provide a runtime binary library to Scala 3 CNCF. Phase 76 maps the Phase 74 semantic authority to the CNCF Phase 96 Logical UI generated ABI and emits deterministic Scala 3 source/metadata plus ComponentFactory-discoverable bootstrap/provider data.

## Architecture

```text
CML / Cozy Logical UI semantic model
  -> normalized producer projection
  -> CNCF Logical UI ABI mapping
  -> generated Scala 3 definitions + metadata provider/bootstrap
  -> CNCF ComponentFactory discovery/admission
  -> Display Projection / Display Model
```

## Inputs

- closed Phase 74 Logical UI semantic/runtime contract and fixtures;
- CNCF Phase 96 released/hand-written consumer ABI fixture;
- existing Cozy generated ABI/source-generation mechanisms, especially StateMachine/Workflow producer patterns.

## Scope

1. Inventory Phase 74 semantic values and the CNCF Phase 96 consumer ABI one-to-one/derived mapping.
2. Define a versioned producer projection that carries only the admitted minimum List/Detail/Section/Field/Value/Action/presentation-role definition data, stable identity and provenance required by the CNCF ABI.
3. Add deterministic source generation targeting the CNCF Scala 3 Logical UI ABI.
4. Generate bounded metadata-provider/bootstrap code discoverable through the CNCF ComponentFactory path.
5. Preserve deterministic ordering and exact stable identities; reject duplicate/unsupported/malformed producer input before source emission.
6. Add generated-source fixtures whose expected shape is pinned to the CNCF Phase 96 consumer fixture.
7. Compile the generated fixture in a Scala 3 consumer fixture/project against CNCF without a Cozy runtime dependency.
8. Hand the generated artifact/fixture to CNCF Phase 96 for admission/bootstrap/display-projection acceptance.
9. Preserve existing compile-time Flutter Logical UI projection semantics; Phase 76 adds the runtime producer path and does not replace Phase 51.
10. Document compatibility/versioning rules for future additive Logical UI vocabulary evolution.

## Producer representation

Do not emit Display Model instances. Emit Logical UI definition/configuration values consumed by CNCF runtime.

The generated source should conceptually contain a component-owned vector of CNCF ABI definitions plus provider metadata. Exact source shape is dictated by the CNCF Phase 96 hand-written expected-generated fixture.

Generated data may include:

- ABI/schema version;
- Logical UI definition identity/version;
- source/provenance identity;
- List/Detail identities;
- Section/Field identities and ordering;
- presentation roles;
- typed display-value definition/converter references admitted by the ABI;
- Action definition/target identity;
- editable/constraint projection metadata where the consumer ABI assigns it to Logical UI definition.

Do not generate runtime endpoint URLs, Flutter widgets, callbacks, datastore details, client navigation objects or CNCF implementation classes beyond the public ABI.

## Source-generation implementation direction

Follow existing modeler/generator layering:

1. Phase 74 semantic model -> normalized producer model.
2. normalized model -> CNCF ABI projection model.
3. projection model -> deterministic Scala source.
4. generated component/provider metadata -> ComponentFactory discovery.

Reuse existing source-generation utilities and naming/provenance conventions. Do not create a second standalone generator executable solely for Logical UI.

## Executable specifications

At minimum prove:

- the Phase 74 reference List/Detail scenario maps to the expected CNCF ABI values;
- repeated generation is byte/deterministically equivalent apart from already-authorized generated headers if any;
- malformed/duplicate identities fail before output;
- generated source contains no Cozy runtime type reference;
- generated source contains no Flutter/TFAF type reference;
- generated Scala 3 consumer fixture compiles against CNCF;
- ComponentFactory discovers/admit the generated definitions in the CNCF acceptance fixture;
- generated definitions drive the same Display Projection result as the CNCF hand-written fixture;
- existing compile-time Logical UI/Flutter projection tests remain unchanged.

## Acceptance criteria

Phase 76 completes when a real Cozy-generated Logical UI fixture is accepted by CNCF Phase 96 through the standard generated ABI/ComponentFactory path and produces the reference List/Detail Display Projection without CNCF depending on cozy_2.12.

## Non-goals

- changing Phase 74 semantic authority;
- cross-building the Cozy runtime;
- creating a shared binary Logical UI runtime library;
- CNCF Display Model/protocol implementation;
- Flutter/TFAF runtime implementation;
- application-specific Editing Studio UI;
- runtime CML parsing by CNCF.
