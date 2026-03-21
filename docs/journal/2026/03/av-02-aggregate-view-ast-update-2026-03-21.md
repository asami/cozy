# AV-02 Aggregate/View AST Update

status=done
published_at=2026-03-21

## Scope
- Cozy side CML/AST update for `Entity -> Aggregate/View` contract.
- Parser and AST source of truth are implemented in Kaleidox model consumed by Cozy.

## Implemented AST Contract
- `EntityDef` (=`EntityModel.EntityClass`) now has:
  - `aggregate: Option[SchemaModel.AggregateDef]`
  - `view: Option[SchemaModel.ViewDef]`
- Added definitions:
  - `AggregateDef(commands, state, invariants)`
  - `ViewDef(attributes, queries)`

## CML Support Added
Inside `ENTITY`:
- `AGGREGATE`
  - `COMMAND`
  - `STATE`
  - `INVARIANT`
- `VIEW`
  - `ATTRIBUTE`
  - `QUERY`

## Semantics and Validation
- Aggregate definitions can represent command input/validation/events/new state.
- View definitions can represent projection attributes/queries and rebuild metadata.
- Event must not depend on View:
  - Event definition keys `view/viewName/view_name` are rejected.
- Cross-side mutation in View Query is rejected:
  - `ACTION/WRITE/MUTATION/MUTATES` keys trigger parser failure.

## Breaking Changes
- `SchemaModel.SchemaClass` constructor shape changed:
  - added `aggregate`, `view` fields.
- `EntityModel.EntityClass` constructor shape changed:
  - added `aggregate`, `view` fields.
- Consumers constructing these classes positionally must update.

## Sample CML and AST (for CNCF consume)
Input CML excerpt:

```text
## Person
### AGGREGATE
#### COMMAND
##### createPerson
- EVENT :: person.created
### VIEW
#### QUERY
##### searchPublished
- EXPRESSION :: poststatus == "published"
```

Parsed AST excerpt (conceptual):

```scala
EntityDef(
  name = "Person",
  aggregate = Some(
    AggregateDef(
      commands = Vector(AggregateCommandDefinition(name = "createPerson", ...)),
      state = Vector(...),
      invariants = Vector(...)
    )
  ),
  view = Some(
    ViewDef(
      attributes = Vector(...),
      queries = Vector(ViewQueryDefinition(name = "searchPublished", ...))
    )
  )
)
```

## Verification
- Cozy parser/modeler test suite includes:
  - Aggregate/View parse success
  - invalid View Query mutation rejection
