# Phase 33.2 - CML Action Algebra, Program, and Testability Contract

Status: planned
Planned at: 2026-09-05
Depends on: Phase 33.1
Cross-repository consumer: `asami/goldenport-cncf` Phase 64.2

## Purpose

Freeze the smallest useful typed Action Algebra for CML StateMachine,
Composite StateMachine, and Workflow actions, define the generated free program
contract consumed by CNCF planners/interpreters, and make StateMachine and
Workflow models directly testable without production infrastructure.

The governing pipeline is:

```text
CML StateMachine / Workflow
  -> pure transition/rule model
  -> typed logical ActionOp
  -> composable Action Program
  -> generated ABI/metadata
  -> Test/Simulation Interpreter
  -> Production Planner / Interpreter
```

CML owns logical model meaning. It does not embed provider handles,
transaction-manager APIs, arbitrary Scala callbacks, or execution-engine code.

## Testability Principle

Testability is a first-class semantic requirement, not a by-product of runtime
implementation.

A generated StateMachine/Composite StateMachine/Workflow must be testable in at
least three layers:

1. **Pure model test**
   - transition selection;
   - guard/predicate evaluation;
   - composite-state derivation;
   - derived transition graph;
   - rule coverage/ambiguity/reachability.
2. **Action-program test**
   - generated ActionOp sequence;
   - constituent/composite provenance;
   - compensation/idempotency/transaction metadata;
   - no real external effects.
3. **Interpreter/runtime contract test**
   - deterministic fake/test interpreter;
   - injected success/failure at any ActionOp;
   - abort/rollback expectation for atomic segments;
   - compensation-plan expectation for non-atomic segments;
   - runtime integration tests through CNCF when needed.

A unit/model test must not require a real database, network service, scheduler,
clock, random generator, or external provider unless the test explicitly chooses
an integration interpreter.

## Deterministic Environment

Any nondeterministic input required by the model/action program must be explicit
and injectable through typed runtime capabilities, not read implicitly from
process globals.

Candidate capabilities include:

```text
Clock
IdGenerator
RandomSource
ExternalResultStub
Subject/Tenant Context
```

The exact set should remain minimal. Time, generated ids, randomness, and
external responses must be controllable in tests where they affect transition
or action outcomes.

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
needed for planning, testing, and review, including where applicable:

- logical action identity;
- source/model element identity;
- target reference;
- input/result type references;
- transaction capability requirement;
- reversibility semantics;
- compensation action reference;
- idempotency semantics/key derivation contract;
- ordering/dependency identity;
- constituent/composite provenance;
- expected effect/result type where useful to tests; and
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

Because the program is data, tests must be able to inspect it directly without
running production effects.

## Test Interpreter Contract

The generated program must support a deterministic test interpreter that can:

- record every interpreted ActionOp in order;
- return configured typed results;
- inject a configured failure at a selected ActionOp/occurrence;
- simulate retryable/non-retryable errors;
- expose the planned compensation sequence;
- expose whether the logical transition would commit or abort;
- avoid real I/O by default; and
- preserve correlation/occurrence identity used by production runtime.

A simulation interpreter may additionally explore multiple outcomes or paths,
but exhaustive simulation is not required for the initial contract.

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
provide the semantic information necessary to make that decision safely and to
make the same plan inspectable in tests.

## Model-based / Property Testing

Finite StateMachine definitions should support generated or property-based test
strategies where practical.

Candidate properties include:

- every admitted transition ends in a declared state;
- rejected transitions do not mutate model state;
- exactly-one composite-state rule matches each reachable configuration unless
  partial mapping is explicitly declared;
- derived composite transitions agree with constituent transition/configuration
  changes;
- no transition/action path bypasses required guard semantics;
- compensation references resolve for every compensatable action;
- action order is deterministic for the same model input; and
- model serialization/generation is stable for the same CML source.

The phase should define test metadata/fixtures needed to make these properties
available to generated tests without forcing one particular Scala test library.

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
- invalid idempotency declarations;
- action cycles introduced by model-level composition; and
- nondeterministic/opaque action semantics that cannot be controlled by a test
  interpreter.

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
- transaction/reversibility/idempotency metadata survive generation;
- pure tests can derive the expected composite state/transition;
- a test interpreter can inspect the program without I/O;
- injected failure at `recordAuthorization` aborts the atomic transition;
- injected failure after `reserveShipment` exposes the correct compensation
  expectation where applicable; and
- CNCF Phase 64.2 can plan the resulting program without CML-specific parsing.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| ALG-01 | Existing action inventory | Existing CML action/effect syntax, generated types, CNCF bindings, and representative uses are cataloged. | planned |
| ALG-02 | Minimal algebra freeze | Small ActionOp families and primitive logical operations are defined with explicit inclusion criteria. | planned |
| ALG-03 | Metadata contract | Identity, target, transaction, reversibility, compensation, idempotency, ordering, provenance, and test-observable metadata are frozen. | planned |
| ALG-04 | Program composition | Free/equivalent program semantics and deterministic constituent/composite composition are defined. | planned |
| ALG-05 | Testability contract | Pure model API, deterministic environment capabilities, test interpreter, failure injection, and property-test hooks are defined. | planned |
| ALG-06 | Grammar/model mapping | Existing/new CML action declarations map to the algebra without embedding runtime code. | planned |
| ALG-07 | Generation/ABI | SimpleModeler emits stable typed ActionOp/program definitions, model-test metadata, and runtime metadata. | planned |
| ALG-08 | Static validation | Reference, compensation, ordering, duplicate-effect, type, and controllable-determinism checks are implemented where tractable. | planned |
| ALG-09 | Cross-repository acceptance | The Order/Payment/Shipment fixture is generated and consumed by CNCF Phase 64.2 planner/test-interpreter acceptance. | planned |

## Acceptance

- The canonical generated action representation is typed and inspectable.
- The minimal algebra is sufficient for the representative StateMachine and
  Composite StateMachine scenario without generic escape hatches.
- StateMachine and Workflow behavior can be tested without production I/O.
- Transition/rule evaluation is deterministic under explicit test inputs.
- Lower and upper actions compose through one mechanism.
- A test interpreter can record effects and inject typed failures at arbitrary
  action points.
- Transaction/reversibility/compensation/idempotency semantics survive CML ->
  generated ABI unchanged in meaning.
- No required action is represented only by an opaque callback or raw string.
- CNCF can plan and test the generated program without understanding CML syntax.

## Non-Goals

- Implementing the production CNCF planner/interpreter.
- Encoding XA/JTA/provider APIs in CML.
- Building a universal effect system.
- Creating domain-specific ActionOp families in CNCF.
- Hiding unknown actions behind an `Any`/script/callback escape hatch.
- Requiring one specific Scala testing framework.
- Requiring real external services for ordinary StateMachine/Workflow tests.

## References

- `phase-33.md`
- `phase-33.1.md`
- `../notes/cml-composite-statemachine-workflow-proposal.md`
- `../notes/cml-action-transaction-compensation-proposal.md`
- `asami/goldenport-cncf/docs/phase/phase-64.2.md`
