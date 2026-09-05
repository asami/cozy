# Phase 33.2 - CML Action Algebra and Program Contract

Status: planned
Planned at: 2026-09-05
Depends on: Phase 33.1
Cross-repository consumer: `asami/goldenport-cncf` Phase 64.2

## Purpose

Freeze the smallest useful typed Action Algebra for CML StateMachine,
Composite StateMachine, and Workflow actions, and define the generated free
program contract consumed by CNCF planners/interpreters.

The governing pipeline is:

```text
CML Action
  -> typed logical ActionOp
  -> composable Action Program
  -> generated ABI/metadata
  -> CNCF Action Planner
  -> CNCF Interpreter
```

CML owns logical action meaning. It does not embed provider handles,
transaction-manager APIs, arbitrary Scala callbacks, or execution-engine code.

## Initial Algebra Families

Start with a deliberately small candidate set:

```text
ActionOp
  +-- EntityAction
  +-- EventAction
  +-- OperationAction
  +-- JobAction
  +-- RuntimeAction
```

The phase must justify each family and each primitive operation from concrete
StateMachine/Composite StateMachine use cases. Do not create a large catalog in
advance.

Candidate meanings:

- `EntityAction`: logical mutation/update admitted through CNCF entity/aggregate
  semantics;
- `EventAction`: logical event creation/publication intent;
- `OperationAction`: invoke a declared component Operation;
- `JobAction`: submit/coordinate asynchronous work through JobEngine;
- `RuntimeAction`: only genuinely generic runtime bookkeeping that must be part
  of the modeled program rather than hidden interpreter implementation detail.

`RuntimeAction` must remain especially small; domain semantics must not migrate
into CNCF by convenience.

## Action Metadata

Each logical action definition/operation should carry or resolve stable metadata
needed for planning and review, including where applicable:

- logical action identity;
- source/model element identity;
- target reference;
- input/result type references;
- transaction capability requirement;
- reversibility semantics;
- compensation action reference;
- idempotency semantics/key derivation contract;
- ordering/dependency identity;
- constituent/composite provenance; and
- source location.

Transaction capability and reversibility remain orthogonal as defined by Phase
33.1.

## Program Composition

The generated representation must support pure composition before execution.
The preferred implementation remains Free Monad or an equivalent free program.

Required semantics:

```text
program = actionA *> actionB *> actionC
```

Composition must preserve deterministic order and action provenance. A program
is logical intent, not an already planned transaction.

Constituent and composite actions use the same algebra and composition
mechanism. Nested composite levels append actions according to the accepted
causal ordering rule.

## Planner-facing Contract

The generated program must expose enough typed information for CNCF to classify
operations into execution segments without parsing opaque strings.

Typical planner output is expected to distinguish:

```text
Local Atomic Segment
Distributed Atomic / 2PC Segment
After-Commit Compensatable Segment
Irreversible Segment
```

The exact runtime planning algorithm belongs to CNCF Phase 64.2. CML must only
provide the semantic information necessary to make that decision safely.

## Validation and Static Review

Cozy/SimpleModeler should detect, where possible:

- unknown action references;
- missing target/model references;
- missing required compensation definitions;
- compensation signature/type incompatibility;
- impossible ordering dependencies;
- duplicate logical effects when identity is explicit;
- an action requiring a transaction capability that no declared target class
  can support statically;
- invalid idempotency declarations; and
- action cycles introduced by model-level composition.

Provider-specific runtime capability remains a CNCF admission concern.

## Representative Acceptance Model

Use an Order/Payment/Shipment composite example:

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

- lower and upper actions compile into one typed program model;
- provenance and order survive generation;
- compensation references survive generation;
- transaction/reversibility/idempotency metadata survive generation; and
- CNCF Phase 64.2 can plan the resulting program without CML-specific parsing.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| ALG-01 | Existing action inventory | Existing CML action/effect syntax, generated types, CNCF bindings, and representative uses are cataloged. | planned |
| ALG-02 | Minimal algebra freeze | Small ActionOp families and primitive logical operations are defined with explicit inclusion criteria. | planned |
| ALG-03 | Metadata contract | Identity, target, transaction, reversibility, compensation, idempotency, ordering, and provenance metadata are frozen. | planned |
| ALG-04 | Program composition | Free/equivalent program semantics and deterministic constituent/composite composition are defined. | planned |
| ALG-05 | Grammar/model mapping | Existing/new CML action declarations map to the algebra without embedding runtime code. | planned |
| ALG-06 | Generation/ABI | SimpleModeler emits stable typed ActionOp/program definitions and metadata. | planned |
| ALG-07 | Static validation | Reference, compensation, ordering, duplicate-effect, and type checks are implemented where tractable. | planned |
| ALG-08 | Cross-repository acceptance | The Order/Payment/Shipment fixture is generated and consumed by CNCF Phase 64.2 planner acceptance. | planned |

## Acceptance

- The canonical generated action representation is typed and inspectable.
- The minimal algebra is sufficient for the representative StateMachine and
  Composite StateMachine scenario without generic escape hatches.
- Lower and upper actions compose through one mechanism.
- Transaction/reversibility/compensation/idempotency semantics survive CML ->
  generated ABI unchanged in meaning.
- No required action is represented only by an opaque callback or raw string.
- CNCF can plan the generated program without understanding CML syntax.

## Non-Goals

- Implementing the CNCF planner/interpreter.
- Encoding XA/JTA/provider APIs in CML.
- Building a universal effect system.
- Creating domain-specific ActionOp families in CNCF.
- Hiding unknown actions behind an `Any`/script/callback escape hatch.

## References

- `phase-33.md`
- `phase-33.1.md`
- `../notes/cml-composite-statemachine-workflow-proposal.md`
- `../notes/cml-action-transaction-compensation-proposal.md`
- `asami/goldenport-cncf/docs/phase/phase-64.2.md`
