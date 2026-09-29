# CML Constraint Language (CCL)

## Status
Design note. CCL is the embedded expression language for constraints over CML semantic models.

## Architecture
CML literary syntax (#, ##, lists, :: properties, tables, etc.) is first converted by the CML meta-grammar to the generic AST and then semantically interpreted. CCL is used inside the semantic Constraint model; it does not impose a single literary surface form.

CCL adopts OCL as its primary syntactic and semantic reference for constraint expressions, but does not adopt the OCL type system.

```
CML Literary Meta Grammar -> Generic AST -> CML Semantic Model -> Constraint -> CCL Expression
```

## Type System
CCL SHALL NOT introduce a parallel primitive or object type hierarchy. All expression types are resolved against the existing CML Semantic Type System, including the rich CML DataType set, Entity, Value, Enumeration, and project-defined types.

Principle:

```
CML model type = CCL expression type
```

CCL owns expression typing rules, not the underlying type universe.

## Multiplicity
CML multiplicity is preserved as first-class expression information:

- `1`: exactly one
- `?`: zero or one
- `+`: one or more
- `*`: zero or more
- `[m..n]`: explicit range

Conceptually:

```
ExpressionInfo
  type         : CmlTypeRef
  multiplicity : CmlMultiplicity
```

CCL does not introduce Optional[T] or an OCL-style Collection type hierarchy merely to represent multiplicity.

## OCL-Derived Core
Initial candidates are logical expressions, equality/comparison, property navigation, `forAll`, `exists`, `select`, `reject`, `collect`, and basic multi-valued operations. Operation-contract concepts such as result and pre-state are also candidates.

Complete OCL compatibility is not a goal. OCL-specific type machinery, unrestricted `allInstances`, and the complete OCL null/invalid model are outside the initial core.

## Navigation
Use `.` for model navigation and OCL-style `->` for operations over multi-valued expressions. Implicit navigation across multi-valued expressions is allowed; explicit `collect` remains available.

Multiplicity propagates conservatively. Examples:

```
A.b : B ?
B.c : C 1
A.b.c : C ?

A.bs : B +
B.c  : C ?
A.bs.c : C *
```

Filtering can reduce lower bounds, e.g. `T + ->select(...) : T *`. `forAll` and `exists` return CML Boolean with multiplicity `1`.

## Constraint Context
Constraint is a CML semantic concept; CCL defines its expression. The same expression mechanism can therefore be used by invariant, precondition, postcondition, guard, workflow condition, admission condition, validation rule, and capability satisfaction conditions.

A generalized evaluation context may expose `self`, parameters, variables, `result`, pre-state, and model-specific workflow/state-machine bindings.

## Predicate Logic and Evidence
CCL obtains practical predicate-logic expressiveness through predicates/relations over model values, navigation, `forAll`, and `exists`. It is not a theorem prover.

Normal evaluation produces CML Boolean for a Constraint. Higher layers may optionally preserve successful validation as Evidence, a refined value, or another typed artifact. Mathematical proof-system completeness is not a goal.

## CCL Core Responsibilities
Phase 1 should provide parsing/AST, name resolution, CML type resolution, multiplicity propagation, navigation, logical/comparison expressions, quantification, core multi-valued transformations, basic semantic validation, and source-location preservation.

## CAR Lint
Phase 1 lint should cover inexpensive semantic errors: unresolved references, invalid navigation, incompatible operands, invalid multiplicity use, non-Boolean quantifier predicates, and non-Boolean Constraint expressions.

Advanced reasoning (always true/false, redundancy, contradiction, impossible cardinality, cross-constraint analysis) is desirable but explicitly deferred. CCL Core must preserve the semantic information needed to add it later.

## Projection
Scala is an important first projection target, not the semantic owner. Typical mappings are `forAll -> forall`, `exists -> exists`, `select -> filter`, `reject -> filterNot`, and `collect -> map/flatMap` according to multiplicity. Other target languages use the same CML semantic types.

## Principle
CCL should make constraints feel like expressions over the CML model already defined, not like entry into a second type universe. OCL provides the expression-language precedent; CML provides the authoritative semantic type system.


## Absence and Failure Semantics
CCL excludes `null` from its value model.

Absence is represented exclusively by CML multiplicity. For example, `Customer ?` means zero or one Customer; it is not `Customer | null`. Navigation preserves that meaning, so navigating a `T ?` value can produce another optional multiplicity without introducing a null value.

CCL also does not use an OCL-style `invalid` value. Evaluation failure is represented by the existing Consequence mechanism at the execution boundary.

This establishes a strict separation:

```
absence            -> CML Multiplicity
successful value   -> CML Type + Multiplicity
evaluation failure -> Consequence
```

An optional Boolean is therefore `Boolean ?`, not a three-valued Boolean containing null. A CML Constraint requires a final expression of `Boolean 1`; when an optional value affects the condition, the model must explicitly state how existence is interpreted, for example through `exists`.

Constraint non-satisfaction and evaluation failure are distinct. A successfully evaluated `false` means the Constraint is not satisfied; a failed Consequence means the Constraint could not be evaluated.


## ConstraintContext and Implicit self
CCL expressions are resolved within a ConstraintContext derived from the CML model placement. The context is both the static name-resolution contract and the schema for later runtime binding.

An unqualified name may resolve to a member of the context subject. Thus an Order invariant may write `amount > 0`; semantic resolution normalizes it to the equivalent of `self.amount > 0`. Explicit `self` remains available for disambiguation.

Recommended resolution precedence is: lexical bindings (iterator/let), explicit context bindings (parameters/variables), subject members through implicit self, then model symbols. Ambiguity is a semantic error rather than an arbitrary choice.

Source-level abbreviation disappears after resolution: generators, evaluators, and lint consume a fully resolved semantic AST.

## Constraint Semantic Model
Constraint should remain small and placement-bound in Phase 1:

```
Constraint
  name        : String ?
  expression  : CclExpression   // must resolve to Boolean 1
  description : String ?
  source      : SourceLocation
```

Invariant, precondition, postcondition, guard, workflow/admission conditions, and similar concepts are primarily meanings of the owning CML placement rather than a growing Constraint subtype hierarchy.

Examples: Entity.invariants, Operation.preconditions, Operation.postconditions, Transition.guard, Workflow condition, and Admission condition all reference Constraint. The placement determines the ConstraintContextSchema.

Typical derived schemas are: Entity invariant -> subject=Entity; Operation precondition -> subject=owner plus parameters; Operation postcondition -> the same plus result and pre-state; StateMachine guard -> state-machine owner plus event parameters; Workflow condition -> workflow subject plus workflow variables; Admission condition -> candidate plus admission bindings.

ConstraintContext is normally an internal semantic model, not literary syntax users must define manually. Reusable parameterized Constraint definitions may be added later; Phase 1 constraints are placement-bound.
