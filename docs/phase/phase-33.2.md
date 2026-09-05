# Phase 33.2 - CML Action Compilation and Testability Contract

Status: planned
Planned at: 2026-09-05
Revised at: 2026-09-05
Depends on: Phase 33.1
Cross-repository consumer: `asami/goldenport-cncf` Phase 64.2

## Purpose

Define how CML StateMachine, Composite StateMachine, and Workflow logical
actions compile into CNCF's existing Free × UnitOfWork execution model, and
make the resulting models directly testable without production infrastructure.

This phase does **not** introduce a second execution algebra.

The governing pipeline is:

```text
CML StateMachine / Composite StateMachine / Workflow
  -> pure transition/rule model
  -> logical CML Action references
  -> Action Resolver / Compiler
  -> ExecProgram[A]
       = Program[UnitOfWorkOp, A]
  -> generated ABI/metadata
  -> CNCF UnitOfWork analysis / interpreter
```

CML owns logical model/action meaning. CNCF already owns the canonical execution
algebra through `UnitOfWorkOp[A]` and the Free × UnitOfWork model.

## Existing CNCF Authority

The existing CNCF execution contract is authoritative:

```text
UnitOfWorkOp[A]
  = canonical executable-intent algebra

ExecProgram[A]
  = Program[UnitOfWorkOp, A]

ExecUowM[A]
  = UowM[UnitOfWorkOp, A]
```

Therefore CML must not create parallel `ActionOp` families such as
`EntityAction`, `EventAction`, `OperationAction`, `JobAction`, or
`RuntimeAction` as a second runtime algebra unless later evidence proves a
strictly model-level IR is required and it cannot compile directly to
`ExecProgram`.

The default is:

```text
CML Logical Action
      |
      v
Action Binding / Compiler
      |
      v
ExecProgram[UnitOfWorkOp]
```

## CML Logical Action Boundary

CML actions remain model elements, for example:

```text
ACTION recordAuthorization
ACTION reserveShipment
ACTION releaseShipment
```

A logical action is not itself a `UnitOfWorkOp` case class. It names and types a
model-level intent that must resolve to an executable UnitOfWork program.

The binding/compilation contract must preserve, where applicable:

- logical action identity;
- source/model element identity;
- target/model references;
- input/result type references;
- transaction capability requirement;
- reversibility semantics;
- compensation action reference;
- idempotency semantics/key derivation contract;
- ordering/dependency identity;
- constituent/composite provenance;
- source location; and
- generated ABI/version identity.

Transaction capability and reversibility remain orthogonal as defined by Phase
33.1.

## Program Composition

StateMachine actions compile to existing CNCF Free/UnitOfWork programs.

Conceptually:

```text
recordAuthorization
  -> ExecProgram[Unit]

reserveShipment
  -> ExecProgram[Unit]

constituentProgram *> compositeProgram
  -> one composed ExecProgram[Unit]
```

The program is still structured executable intent, not immediate side effect.
Composition must preserve deterministic order and provenance.

No required CML action may rely on opaque callbacks, raw script strings, or
provider handles as the canonical path.

## StateMachine / Composite StateMachine Continuity

The same compilation path is used for all levels:

```text
simple StateMachine transition action
      -> ExecProgram

constituent StateMachine action
      -> ExecProgram

composite derived-transition action
      -> ExecProgram

Workflow specialization action
      -> ExecProgram
```

This preserves one executable-intent language across StateMachine, Composite
StateMachine, Workflow, ordinary CNCF Actions, and direct/declarative execution.

## Transaction and Effect Semantics

`ExecProgram` does not mean "one database transaction".

Existing `UnitOfWorkOp` already includes local datastore/entity operations and
externally observable operations such as HTTP/process execution. Therefore CML
must preserve logical transaction/reversibility requirements and let CNCF
analyze the resulting Free structure.

Expected runtime planning remains conceptually:

```text
ExecProgram
   -> UnitOfWork analysis / planner
      +-- local atomic segment
      +-- distributed atomic / 2PC segment
      +-- after-commit compensatable segment
      +-- irreversible segment
   -> interpreter / drivers
```

CML expresses required semantics; it does not encode XA/JTA/provider APIs.

## Testability Principle

Testability is a first-class semantic requirement.

A generated StateMachine/Composite StateMachine/Workflow must support at least:

1. **Pure model tests**
   - transition selection;
   - guard/predicate evaluation;
   - composite-state derivation;
   - derived transition graph;
   - rule coverage/ambiguity/reachability.
2. **Program tests**
   - resolved `ExecProgram` structure;
   - constituent/composite ordering and provenance;
   - compensation/idempotency/transaction metadata;
   - no real external effect required.
3. **Interpreter/runtime contract tests**
   - deterministic fake/test UnitOfWork interpreter/drivers;
   - injected success/failure at selected executable intents;
   - atomic abort expectation;
   - compensation-plan expectation;
   - runtime integration tests only where required.

Ordinary model/unit tests must not require a real database, network service,
scheduler, clock, randomness source, or external provider.

## Deterministic Environment

Nondeterministic inputs affecting observable behavior must remain injectable.
Candidate capabilities include:

```text
Clock
IdGenerator
RandomSource
ExternalResultStub
Subject/Tenant Context
```

Where CNCF already has execution-context abstractions, reuse them instead of
creating CML-specific capability systems.

## Static Validation

Cozy/SimpleModeler should detect, where possible:

- unknown logical action references;
- missing action binding/compiler target;
- missing target/model references;
- missing required compensation action;
- compensation signature/type incompatibility;
- impossible ordering dependencies;
- duplicate logical effects where identity is explicit;
- invalid idempotency declarations;
- action cycles introduced by model composition;
- a required transaction semantic that is internally contradictory; and
- opaque/non-testable action bindings that bypass the Free × UnitOfWork path.

Provider/runtime capability remains CNCF admission responsibility.

## Representative Acceptance Model

Use the Order/Payment/Shipment composite example:

```text
Payment.Pending -> Authorized
  action: recordAuthorization

OrderFulfillment.WaitingForPayment -> ReadyToShip
  action: reserveShipment

reserveShipment
  reversibility = compensatable
  compensation = releaseShipment
```

The fixture must prove:

- logical action references resolve deterministically;
- lower and upper actions compile into composable `ExecProgram` values;
- provenance and order survive generation;
- compensation references survive generation;
- transaction/reversibility/idempotency semantics survive generation;
- pure tests derive the expected composite state/transition;
- a test UnitOfWork interpreter can inspect/execute the program without real
  external I/O;
- injected failure at `recordAuthorization` aborts the atomic transition; and
- CNCF Phase 64.2 can analyze/plan the resulting `ExecProgram` without CML
  syntax parsing.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| ACP-01 | Existing execution inventory | Existing CML action/effect syntax plus CNCF `UnitOfWorkOp`, `ExecProgram`, Free/UoW DSLs, interpreter, and metadata are cataloged. | planned |
| ACP-02 | Logical-action boundary | CML action identity, typing, metadata, compensation, idempotency, and binding semantics are frozen without introducing a second runtime algebra. | planned |
| ACP-03 | Resolver/compiler contract | CML logical actions resolve/compile deterministically to `ExecProgram[UnitOfWorkOp, A]`. | planned |
| ACP-04 | Composition contract | Constituent/composite/Workflow programs compose with deterministic causal order and provenance. | planned |
| ACP-05 | Testability contract | Pure model APIs, deterministic environment inputs, program inspection, failure injection, and property-test hooks are defined. | planned |
| ACP-06 | Generation/ABI | SimpleModeler emits stable action binding/program metadata compatible with CNCF UnitOfWork execution. | planned |
| ACP-07 | Static validation | Binding, compensation, ordering, idempotency, type, and Free/UoW-path validation are implemented where tractable. | planned |
| ACP-08 | Cross-repository acceptance | Shared Order/Payment/Shipment CML fixture compiles to `ExecProgram` and passes CNCF Phase 64.2 analysis/test-interpreter acceptance. | planned |

## Acceptance

- No parallel canonical execution algebra is introduced for StateMachine or
  Workflow.
- CML logical actions compile to CNCF `ExecProgram` / `UnitOfWorkOp`.
- Lower and upper StateMachine actions compose through the existing Free/UoW
  mechanism.
- StateMachine and Workflow behavior is testable without production I/O.
- Transaction/reversibility/compensation/idempotency semantics survive CML ->
  generated program unchanged in meaning.
- CNCF can analyze and execute the resulting program without understanding CML
  syntax.

## Non-Goals

- Replacing or duplicating `UnitOfWorkOp`.
- Creating a new CNCF `ActionOp` hierarchy for StateMachine/Workflow.
- Encoding XA/JTA/provider APIs in CML.
- Building a universal effect system.
- Hiding unknown actions behind `Any`, scripts, callbacks, or provider handles.
- Requiring one specific Scala testing framework.

## References

- `phase-33.md`
- `phase-33.1.md`
- `../notes/cml-composite-statemachine-workflow-proposal.md`
- `../notes/cml-action-transaction-compensation-proposal.md`
- `asami/goldenport-cncf/docs/design/free-unitofwork-execution-model.md`
- `asami/goldenport-cncf/src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkOp.scala`
- `asami/goldenport-cncf/docs/phase/phase-64.2.md`
