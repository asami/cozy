# CCL: OCL-Based Constraint Language with CML Types

Date: 2026-09-28

## Background
The discussion started from Curry-Howard, intuitionistic propositional logic, and what practical parts of predicate logic could be incorporated into Scala and CML. The design converged on extending the semantic power of CML Constraint rather than adding a separate logic-oriented literary grammar.

CML already separates literary representation from semantic interpretation: headings, lists, :: properties, and tables become generic AST structures before semantic interpretation. Predicate logic therefore does not require one mandatory surface representation.

For the expression inside a Constraint, however, a programming-language-like notation is clearer. OCL is the natural precedent.

## Decision
Introduce CCL: CML Constraint Language.

CCL selectively adopts OCL expression syntax and semantics and extends them where CML requires it. It does not adopt the OCL type system.

Every CCL expression is typed directly against the existing CML Semantic Type System and DataType system.

```
CML model type = CCL expression type
```

## Why Not the OCL Type System
A practical difficulty with OCL is that UML modelers cross into a distinct OCL type universe when writing constraints. CML already has a rich semantic type system, so duplicating it for constraints would obscure the connection between model, constraint, and generated implementation.

## Multiplicity
CML already defines `1`, `?`, `+`, `*`, and `[m..n]`. CCL preserves these on expressions rather than immediately translating them into Optional or Collection types.

This permits direct typing such as:

```
Order.customer : Customer ?
Order.items    : OrderItem +
```

and retains information useful for future static analysis.

## Navigation
Use OCL-like `.` for model navigation and `->` for multi-valued operations/quantification. Implicit navigation across multi-valued expressions is allowed for readability; the semantic analyzer propagates multiplicity and projection can choose map/flatMap or equivalent operations. Explicit `collect` remains available.

## Constraint versus CCL
Constraint remains a CML semantic model concept. CCL only defines the expression.

This separates:
- what condition is expressed: CCL
- where/why it is required: CML Constraint context
- how success/failure is handled: runtime/projection

Invariant, precondition, postcondition, guard, workflow condition, admission condition, validation, and capability-related conditions can therefore reuse CCL.

## Constructive Interpretation
The intuitionistic-logic motivation remains useful, but CCL is not a proof assistant. Normal constraints evaluate to Boolean. Where useful, runtime or generated Scala may retain successful validation as Evidence, a refined value, a given, or another typed artifact. This is above CCL Core.

## CAR Lint
Static model verification through CAR lint is desirable. Phase 1 should implement only the semantic checks needed for reliable CCL: name resolution, type compatibility, multiplicity validity, and Boolean predicate requirements.

Type, multiplicity, and source-location information should be retained so later lint can add always-true/false, redundancy, contradiction, and cardinality inconsistency reasoning. Advanced reasoning is not a Phase 1 closure condition.

## Next Work
Refine the exact OCL subset, pre-state/result semantics, absence and evaluation-failure semantics, detailed multiplicity transfer rules, DataType/operator resolution, Scala projection policies, and later CAR lint reasoning.


## Null and Failure Decision
CCL will not have a `null` value. Value absence is already modeled by CML multiplicity, especially `?`, and should not be duplicated by a special value.

Likewise, CCL will not reproduce OCL's `invalid` value semantics. Evaluation failure is handled internally through the existing Consequence abstraction.

The resulting model is:

```
absence            = multiplicity
value               = CML type + multiplicity
evaluation failure  = Consequence
```

This also avoids three-valued Boolean semantics. `Boolean ?` means zero or one Boolean value, not true/false/null. Since a Constraint requires `Boolean 1`, optionality must be resolved explicitly in the expression, for example by an existence condition.

A false Constraint result and an evaluation failure remain semantically different.
