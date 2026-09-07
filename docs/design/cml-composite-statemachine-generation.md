# CML Composite StateMachine Generated ABI

Status: accepted
Scope: Phase 47 CSM-07 only
Implemented at: 2026-09-07

## Purpose

CSM-07 defines the immutable Cozy-side normalized Composite StateMachine IR and
the deterministic Scala source ABI generated from it. It is the boundary between
the CSM-06 CML grammar/validation surface and later consumers. It does not
introduce an interpreter, runtime policy, visualization projection, or a CNCF
API contract.

The ABI version is exactly:

```text
cozy.cml.composite-statemachine.v1
```

## Normalized IR

`cozy.modeler.CompositeStateMachineCml.definitions(model)` projects validated
CML into `Vector[CompositeStateMachineDefinition]`. `validate(model)` follows
that same normalization path and discards the resulting vector, so validation
and projection cannot diverge.

Each definition retains the following typed values:

- canonical definition identity and name;
- definition, constituent, declared-state, derivation, logical-action, and
  action-occurrence source identities;
- constituent bindings in CML declaration order, including role, StateMachine
  reference, flat direct-state universe, and optional typed subject;
- declared composite states;
- complete derivation configurations as role-qualified ordered bindings;
- optional initial configuration in that same constituent-role order;
- logical Action identity, `OPERATION` kind, resolved service/operation
  reference, resolved optional input type, and optional CML input binding;
- distinct constituent action occurrences with role/from/to/on/placement/action
  provenance; and
- distinct derived action occurrences with from/to/derived-transition/action
  provenance.

The IR boundary uses `Vector` for every ordered or map-like semantic collection.
No unordered map crosses the CSM-07 ABI boundary. A `SourceIdentity` holds the
`LogicalSection` line number when it is available; it is `None` for parsers that
deliberately omit locations. It never invents a source file path.

## Generated Scala ABI

Both `modeler-scala` and `modeler-scala-value` pass normalized definitions to
the Cozy Scala generator. CSM-08 refines this generated output's presence and
typed discovery surface through [CML Composite StateMachine Bootstrap Registry](cml-composite-statemachine-bootstrap.md).
Every generation writes the following shared source tree in addition to the
ordinary SimpleModeler Realm output:

```text
target/scala-3.3.8/src_managed/main/scala/domain/composite/statemachine/
  CompositeStateMachineAbi.scala
  CompositeStateMachineBootstrap.scala
  <definition>CompositeStateMachine<n>.scala
```

All files are in package `domain.composite.statemachine`. The shared
`CompositeStateMachineAbi` exposes versioned case classes for source identity,
StateMachine references, subjects, constituent bindings, configurations and
derivations, resolved logical actions, constituent action occurrences, derived
action occurrences, and the enclosing definition. Each generated definition
object exposes:

```scala
val definition: CompositeStateMachineAbi.Definition
```

`CompositeStateMachineBootstrap.scala` and its fixed typed registry are owned
by CSM-08. Per-definition files are emitted only for normalized Composite
StateMachine definitions.

Generated source uses Scala string escaping for all CML text. Definitions retain
CML declaration order; configurations are reordered to the constituent-role
order. The object/file suffix is deterministic source order, which keeps source
names stable even when two CML identities would normalize to the same Scala
identifier.

The Composite StateMachine Realm is additive. Existing SimpleModeler Scala
output and optional `target/cozy/component-api-model.json` metadata are retained
and are not overwritten by this generated ABI.

## Explicit Deferrals

CSM-07 does not decide or implement:

- CNCF ComponentFactory mapping, runtime admission, or action interpretation
  (CSM-08);
- visualization, diagram, BoK, CBD Support, or diagnostics projection metadata
  beyond the preserved source identity (CSM-09);
- cross-repository generated-model acceptance (CSM-10);
- Workflow grammar or a Workflow-specific model;
- transaction, retry, compensation, recovery, persistence, concurrency, or
  lifecycle policy; or
- source-file identity when the parser did not provide one.

The CSM-06 [grammar and validation authority](cml-composite-statemachine-grammar-validation.md)
continues to own CML syntax and diagnostics. The CSM-04 [action algebra](cml-composite-statemachine-action-algebra.md)
continues to own the semantic meaning of logical actions; this document owns
their concrete generated ABI representation only.
