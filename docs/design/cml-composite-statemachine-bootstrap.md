# CML Composite StateMachine Bootstrap Registry

Status: accepted
Scope: Phase 47 CSM-08 typed bootstrap registry
Implemented at: 2026-09-07

## Purpose

CSM-08 refines CSM-07's generated-output-presence rule with one producer-side
source-level discovery boundary for a future CNCF `ComponentFactory`. CML
remains the semantic authority. Cozy emits typed Composite StateMachine
definitions; a future CNCF consumer owns admission and interpretation.

## Generated Source Contract

Both `modeler-scala` and `modeler-scala-value` always emit these fixed source
entrypoints beneath the CSM-07 root:

```text
target/scala-3.3.8/src_managed/main/scala/domain/composite/statemachine/
  CompositeStateMachineAbi.scala
  CompositeStateMachineBootstrap.scala
```

`CompositeStateMachineBootstrap` is in package
`domain.composite.statemachine` and exposes exactly these public typed members:

```scala
val bootstrapAbiVersion: String = "cozy.cml.composite-statemachine-bootstrap.v1"
val definitions: Vector[CompositeStateMachineAbi.Definition]
```

For normalized Composite StateMachine definitions, `definitions` is a
`Vector(...)` of direct references to the generated definition objects in
normalized CML declaration order. For example:

```scala
val definitions: Vector[CompositeStateMachineAbi.Definition] =
  Vector(OrderProgressCompositeStateMachine1.definition)
```

For a model with no Composite StateMachine definitions, it is exactly the
typed source expression:

```scala
val definitions: Vector[CompositeStateMachineAbi.Definition] = Vector.empty
```

Per-definition `<definition>CompositeStateMachine<n>.scala` files are emitted
only for normalized Composite StateMachine definitions. CSM-07's generated
definition ABI remains exactly `cozy.cml.composite-statemachine.v1`; a future
consumer verifies that ABI on every discovered definition. The Cozy-side
bootstrap ABI constant is exactly
`cozy.cml.composite-statemachine-bootstrap.v1`.

## Boundary and Non-Goals

The registry uses fixed package/object/member names and direct Scala references.
It does not use reflection, inferred names, class-name strings, JSON or resource
locators, callbacks, or untyped/raw strings. The ordinary component facade
difference between the two public Scala routes is unchanged, and existing
`component-api-model.json` output is unchanged.

This slice does not add a `ComponentFactory` implementation, a runtime
admission/interpreter, Workflow syntax or profile, a component API schema
extension, or transaction, retry, persistence, visualization, concurrency, or
lifecycle behavior. CSM-09 and CSM-10 retain their existing visualization and
cross-repository acceptance scope.
