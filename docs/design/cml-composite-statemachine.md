# CML Composite StateMachine Semantics

status=accepted
phase=47
slice=CSM-02
updated_at=2026-09-07

## Authority and scope

This document is the accepted Phase 47 design authority for the document-level
semantic contract of a CML Composite StateMachine. It freezes meaning only; it
does not add parser syntax, implementation, generated output, runtime behavior,
or an executable specification.

The existing local/simple StateMachine remains a StateMachine. Composite
StateMachine is the primary construct for coordinating constituent
StateMachines. Workflow is only a future profile specialization of Composite
StateMachine, not a parallel language or a second set of StateMachine
semantics.

The accepted [CML Workflow Specialization
Classification](cml-workflow-specialization.md) records that CSM-05 retained
no mandatory Workflow-only semantic residual. The accepted CSM-06 [CML
Composite StateMachine Grammar and
Validation](cml-composite-statemachine-grammar-validation.md) admits no
Workflow keyword or profile syntax and does not alter this constituent, role, subject,
configuration, or derived-transition contract.

## Constituent bindings

A Composite StateMachine has a set of constituent bindings. Each binding has:

- a role identifier that is unique within the composite;
- a reference to one StateMachine definition; and
- an optional reference to a subject.

The role identifier names the constituent's position in this composite. The
referenced StateMachine definition identity names the machine definition. These
identities remain distinct even when multiple roles reference the same machine
definition. A subject reference, when present, is likewise a reference in the
binding contract.

Binding coordinates by reference and does not transfer ownership of a
constituent StateMachine or subject. The composite therefore does not imply
that it creates, contains, or controls the lifetime of either referenced
object.

## State configuration

The composite state configuration is a complete mapping from every declared
constituent role to that role's current constituent State. A configuration has
one current State for each declared role, has no unbound declared role, and is
the authoritative input to composite-state derivation.

The composite State is derived from the configuration. It is not a separately
mutable business state and must not become a second independent source of
truth.

## Composite-state derivation

A derivation rule consists of typed predicates over role-qualified constituent
States and a declared composite State. Rule evaluation is pure: it depends
only on the supplied configuration, has no script or arbitrary executable-code
step, and does not perform effects.

For every reachable configuration, exactly one derivation rule must match.
The following are diagnostics, not states to be silently resolved:

- zero matching rules is an uncovered configuration diagnostic; and
- more than one matching rule is an ambiguous configuration diagnostic.

Rule priority and default rules do not resolve either condition. A consumer
must not choose a winner by declaration order, priority, or fallback state.

## Derived transition and causation semantics

After a constituent has committed a transition, the composite forms the
post-transition configuration and derives the composite State before and after
that committed transition.

- If the derived State is unchanged, no composite transition is emitted.
- If the derived State differs, exactly one derived composite transition is
  emitted.

The derived transition preserves the identity of the committed constituent
transition as its causation. The causation relation records what produced the
derived transition; it does not replace the constituent transition's identity
or imply that the composite owns the constituent transition.

## Projection boundary

A future abstract projection IR must retain, without losing semantic identity:

- constituent bindings, including role identifiers, StateMachine definition
  references, and optional subject references;
- typed derivation rules;
- the complete state configuration;
- derived-transition causation; and
- source identity.

This slice does not change existing projection APIs. No parser, model,
generator, runtime, or external consumer is admitted by this document merely
because a future projection IR is required to preserve these values.

## Explicit deferrals

The following subjects are outside this accepted semantic contract and remain
deferred:

- CML grammar and parser syntax;
- a Workflow keyword or Workflow profile syntax;
- action syntax, resolution, and diagnostics; accepted action ordering and
  algebra are defined by the [CML Composite StateMachine Action
  Algebra](cml-composite-statemachine-action-algebra.md) authority;
- transaction, compensation, or recovery semantics;
- static-analysis mechanics. Static-analysis semantics are defined by the
  accepted [CML Composite StateMachine Static Analysis](cml-composite-statemachine-static-analysis.md)
  authority; implementation and integration mechanics remain deferred;
- generators and CNCF integration;
- visualization; and
- external repositories or cross-repository acceptance.

No statement in this document decides or implies a design for a deferred
subject. In particular, no CML syntax example is normative here.
