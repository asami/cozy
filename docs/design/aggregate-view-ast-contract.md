# Aggregate/View AST Contract (Cozy)

status=work-in-progress
published_at=2026-03-21

## Purpose
Define the Cozy-side CML/AST contract for `Entity -> Aggregate/View`.

## Entity AST
`EntityDef` (`EntityModel.EntityClass`) includes:
- `aggregate: Option[SchemaModel.AggregateDef]`
- `view: Option[SchemaModel.ViewDef]`

## Aggregate AST
`AggregateDef(commands, state, invariants)`

- `commands`: command-oriented definitions.
- `state`: aggregate state fields.
- `invariants`: state invariants.

## View AST
`ViewDef(attributes, queries)`

- `attributes`: projection shape.
- `queries`: read-side query definitions.

## Semantic Constraints
- Aggregate is command-side only.
- View is read-side only.
- Event must not depend on View metadata.
- View query must not declare command-side mutation.

## Breaking Change Notes
- `SchemaModel.SchemaClass` now has `aggregate` and `view`.
- `EntityModel.EntityClass` now has `aggregate` and `view`.
- Positional constructors in downstream code must be updated.
