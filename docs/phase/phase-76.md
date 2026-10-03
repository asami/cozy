# Phase 76: Logical UI Generated ABI Producer

status=IN_PROGRESS
planned_at=2026-10-03
checklist=phase-76-checklist.md
development_branch=codex/phase-76-logical-ui-producer
consumer_handoff=LUI-ABI-01
generated_handoff=LUI-GEN-01
acceptance_handoff=LUI-ACCEPT-01
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
- CNCF Phase 96 LUI-ABI-01: fixed consumer ABI/provider contract, hand-written expected-generated fixture and focused projection proof; this is an intermediate development handoff, not a prerequisite that Phase 96 has closed;
- existing Cozy generated ABI/source-generation mechanisms, especially StateMachine/Workflow producer patterns.

## Development ledger and handoffs

The [Phase 76 checklist](phase-76-checklist.md) owns current progress.
P76-01's documentary contract is accepted after retained independent Step
review PASS: 4/24 obligations accepted, P76-01 CLOSED, and local Step commit
pending. P76-02..06 remain OPEN with 20 obligations remaining. The received
fixed LUI-ABI-01 source contract is transcribed in the
[producer spec](../spec/logical-ui-generated-producer.md) and
[design](../design/logical-ui-generated-producer.md). Implementation verification,
actual generation and generated consumer acceptance remain pending; Phase 76
remains IN_PROGRESS.
The [contract journal](../journal/2026/10/2026-10-04-phase-76-producer-contract.md)
records source coordinates, existing consumer focused evidence and documentary
acceptance. The [ABI proposal](../notes/logical-ui-cncf-producer-abi-proposal.md)
and [reconciliation journal](../journal/2026/10/2026-10-03-phase-76-cncf96-plan-reconciliation.md)
record the handoff details.

| Handoff | Producer -> consumer | Required result |
| --- | --- | --- |
| LUI-ABI-01 | CNCF P96-01..03 -> Cozy P76-01 | Versioned ABI/provider contract, expected-generated Scala 3 fixture and focused admission/projection proof |
| LUI-GEN-01 | Cozy P76-02..04 -> CNCF P96-03 | Actual generated source/provider bound to LUI-ABI-01, producer revision and deterministic generation evidence |
| LUI-ACCEPT-01 | CNCF P96-03 -> Cozy P76-05 | Scala 3 compilation, ComponentFactory admission and equivalent List/Detail projection for the same generated fixture |

Neither Phase waits for the other's whole-Phase closure. Cozy starts producer
implementation after LUI-ABI-01; CNCF accepts its output before continuing
protocol/read/mutation work. LUI-ACCEPT-01 can be referenced by both ledgers.
Cozy closure requires its own checklist/validation and this consumer proof,
not CNCF's later REST/mutation work or app Phase 5 E2E.
Intermediate handoffs use focused specs and affected compilation; they do not
require a full-test run or public publication. Repository release validation
remains separate.

## Scope

1. Inventory Phase 74 semantic values and the CNCF Phase 96 consumer ABI one-to-one/derived mapping.
2. Define a versioned producer projection that carries only the admitted minimum List/Detail/Section/Field/Value/Action/presentation-role definition data, stable identity and provenance required by the CNCF ABI.
3. Add deterministic source generation targeting the CNCF Scala 3 Logical UI ABI.
4. Generate bounded metadata-provider/bootstrap code discoverable through the CNCF ComponentFactory path.
5. Preserve deterministic ordering and exact stable identities; reject duplicate/unsupported/malformed producer input before source emission.
6. Add generated-source fixtures whose expected shape is pinned to the CNCF Phase 96 consumer fixture.
7. Supply the generated fixture for Scala 3 compilation in CNCF's consumer fixture/project and retain that exact consumer evidence, without a Cozy runtime dependency.
8. Hand the generated artifact/fixture to CNCF Phase 96 for admission/bootstrap/display-projection acceptance.
9. Preserve the Phase 51 Flutter Target Projection plan and existing target-neutral compile-time Logical UI semantics; Phase 51 is PLANNED and no Flutter source/suites exist in this checkout. Phase 76 does not start Phase 51.
10. Document compatibility/versioning rules for future additive Logical UI vocabulary evolution.

## Producer representation

Do not emit Display Model instances. Emit Logical UI definition/configuration values consumed by CNCF runtime.

The fixed source shape is `cncf.logical-ui-generated.v1`, version 1, in
`org.goldenport.cncf.display`: a component-owned definitions vector and public
provider mixin, using the exact constructors in the producer spec. The CNCF
hand-written expected-generated fixture supplies reference shape, not proof
of actual Cozy generation.

Generated data may include:

- ABI/schema version;
- Logical UI definition identity/version;
- source/provenance identity;
- List/Detail identities;
- Section/Field identities and ordering;
- presentation roles;
- explicit value binding IDs and ordered allowed kinds, without sample-value inference;
- Action definition/target identity;
- explicit View/value/action registration keys admitted by the fixed LUI-ABI-01; entity constraints and mutation targets remain application/runtime-owned.

This minimum consumes the received fixed LUI-ABI-01 source contract, not a
published ABI release or independently accepted P96-03 accumulator. Current
resource values/revisions, authorization and capability availability remain
runtime-owned; a future static metadata extension requires explicit consumer
contract admission and must not create a second validation language.

Do not generate runtime endpoint URLs, Flutter widgets, callbacks, datastore details, client navigation objects or CNCF implementation classes beyond the public ABI.

## Source-generation implementation direction

Follow existing modeler/generator layering:

1. Ordinary LogicalSection AST/source component declarations -> immutable static producer definitions using shared Phase 74 semantics.
2. normalized model -> CNCF ABI projection model.
3. projection model -> deterministic Scala source.
4. generated component/provider metadata -> ComponentFactory discovery.

Both modeler-scala and modeler-scala-value CNCF routes validate before STree
output; library target rejects present Logical UI definitions. Emit component
package plus .logicalui definitions object and metadata-provider trait. The
application component or actual factory explicitly mixes it in and supplies
bindings; generation does not rewrite user factory files. Reuse existing
source-generation utilities and naming/provenance conventions. Do not create
a second standalone generator executable solely for Logical UI.

## Executable specifications

At minimum prove:

- the Phase 74 reference List/Detail scenario maps to the expected CNCF ABI values;
- repeated generation is byte-equivalent UTF-8 LF with final newline, preserving declaration order and adding no generated-time headers;
- malformed/duplicate identities fail before output;
- generated source contains no Cozy runtime type reference;
- generated source contains no Flutter/TFAF type reference;
- generated Scala 3 consumer fixture compiles against CNCF;
- ComponentFactory discovers and admits the generated definitions in the CNCF acceptance fixture;
- generated definitions drive the same Display Projection result as the CNCF hand-written fixture;
- existing Logical UI/runtime, Workflow, ProvidedApi and library-generation regression semantics remain unchanged; no Flutter generator/widget suite execution is claimed.

The producer spec enumerates future Given/When/Then/PBT obligations. P76-01A
authors no executable specification or fixture and runs no tests.

## Acceptance criteria

Phase 76 completes when its checklist is satisfied, including LUI-ACCEPT-01:
a real Cozy-generated Logical UI fixture compiles against the agreed CNCF ABI,
is accepted through ComponentFactory and produces the same reference List/Detail
Display Projection as the hand-written fixture without CNCF depending on
`cozy_2.12`. The receipt binds the exact producer, consumer and fixture revisions;
a hand-written fixture alone or producer-local text comparison is insufficient.

## Non-goals

- changing Phase 74 semantic authority;
- cross-building the Cozy runtime;
- creating a shared binary Logical UI runtime library;
- CNCF Display Model/protocol implementation;
- Flutter/TFAF runtime implementation;
- application-specific Editing Studio UI;
- runtime CML parsing by CNCF.
