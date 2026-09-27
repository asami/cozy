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
