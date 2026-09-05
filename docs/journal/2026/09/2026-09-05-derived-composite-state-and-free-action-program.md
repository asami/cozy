# Derived Composite State and Free Action Program Direction

Date: 2026-09-05
Status: design discussion record

## Context

The Composite StateMachine discussion progressed from a simple composition of
multiple StateMachines to a stronger model:

1. constituent StateMachines remain authoritative for local business state;
2. higher-level composite state is derived from their role-qualified state
   configuration;
3. composite transitions are derived from committed constituent transitions and
   the state-derivation rules; and
4. actions may exist at both constituent and composite levels.

This avoids maintaining duplicate mutable business status at the composite
level and enables substantial model validation before runtime.

## Derived composite state

The preferred abstraction is:

```text
CompositeState = derive(constituent state configuration)
```

For example:

```text
order    = Accepted
payment  = Authorized
shipment = Waiting
        |
        v
ReadyToShip
```

Composite-state rules are therefore part of the CML semantic model rather than
runtime-only code.

The discussion identified static checks that become possible at definition
time:

- uncovered reachable configurations;
- overlapping/ambiguous rules;
- impossible rules;
- redundant rules;
- unreachable/dead composite states;
- unexpected derived composite transitions; and
- configuration-space complexity.

The derived composite transition graph should be treated as a generated model
artifact useful to CNCF, CBD Support, and visualization/review tooling.

## Actions at multiple abstraction levels

A constituent StateMachine transition and the derived Composite StateMachine
transition may both carry actions.

Example:

```text
Payment.Pending -> Payment.Authorized
  action: recordAuthorization

OrderFulfillment.WaitingForPayment -> ReadyToShip
  action: requestShipment
```

The actions coexist because they express behavior at different abstraction
levels. Their provenance and causal order must be preserved.

## Free action program

Rather than compiling CML actions into opaque callbacks, actions should be
represented as typed logical operations that compose into a free program.

Conceptually:

```text
CML action
  -> typed ActionOp
  -> Free Action Program
  -> CNCF planner/interpreter
```

This allows constituent and composite actions to use one composition model and
supports multiple interpreters for production, testing, simulation, model
review, and visualization.

The exact Scala representation may use a Free Monad or equivalent free-program
encoding. The architectural property, not a specific library, is the key
requirement.

## Runtime boundary

The action program describes logical intent. It does not itself define the
transaction.

CNCF is expected to classify and interpret effects according to its runtime
boundaries, especially:

```text
Local / UnitOfWork effects
After-commit / external effects
```

External effects must not execute before the commit whose transition fact
causes the higher-level action.

This preserves the Phase 63 StateMachine rule that external I/O is separated
from the atomic local transition boundary.

## Consequence for Workflow

As Composite StateMachine gains:

- constituent machine composition;
- derived composite states;
- derived transitions;
- guards/actions;
- typed action composition; and
- static analysis,

Workflow becomes thinner as an independent semantic category.

The governing rule remains to use Composite StateMachine semantics to the
maximum extent and add Workflow-only semantics only when a mandatory residual
requirement is demonstrated.

## Documentation update

This decision is incorporated into:

- `docs/phase/phase-33.md`
- `docs/notes/cml-composite-statemachine-workflow-proposal.md`

The corresponding CNCF runtime/interpreter direction is maintained in CNCF
Phase 64 and its provisional specification.
