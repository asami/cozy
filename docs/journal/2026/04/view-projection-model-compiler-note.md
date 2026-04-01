# CML View Projection Model / Compiler Note

- date: 2026-04-02
- status: working spec
- scope: Cozy / CML grammar / ModelCompiler

## Purpose

This note defines the formal Cozy-side model for view projection.

The concern here is:

- CML grammar
- AST / model normalization
- code generation namespace rules

Runtime semantics are intentionally left to the CNCF-side note.

## Position

`VIEW` is a read-model definition under `ENTITY`.

Current implemented line:

- default generated view
- predefined projection generation
  - `view.<Type>`
  - `view.summary.<Type>`
  - `view.detail.<Type>`
- named view aliases
- `VIEW > QUERY`
- `EVENTS`
- `REBUILDABLE`

## Formal Projection Model

The formal Cozy-side `VIEW` model is:

- one canonical full projection per entity
- zero or more named projections
- optional query metadata under the view

So the formal shape is:

- `view.<Type>`
- `view.<projection>.<Type>`

Examples:

- `view.Item`
- `view.summary.Item`
- `view.detail.Item`
- `view.search_result.Item`

## Predefined Projections

The predefined projection set is:

- `all`
- `summary`
- `detail`

Their canonical type mapping is:

- `all`
  - `view.<Type>`
- `summary`
  - `view.summary.<Type>`
- `detail`
  - `view.detail.<Type>`

`all` is implicit and canonical.

So:

- `view.Item`

is canonical, while:

- `view.all.Item`

is not generated.

## CML Grammar

Current accepted `VIEW` structure is:

```text
### VIEW

- VIEWS :: summary, detail
- EVENTS :: person.created, person.updated
- REBUILDABLE :: true

#### ATTRIBUTE

| name | type | multiplicity |
|------+------|--------------|
| ...  | ...  | ...          |

#### QUERY

##### searchByCity

- EXPRESSION :: person.city == query.city
```

Accepted metadata fields:

- `VIEWS`
- `EVENTS`
- `REBUILDABLE`
- `QUERY`

`QUERY` currently carries:

- `name`
- `expression`

## Normalized Model

The normalized Cozy-side view model is:

- `name`
- `entityName`
- `viewNames`
- `queries`
- `sourceEvents`
- `rebuildable`

Where:

- `viewNames`
  - named view aliases such as `summary`, `detail`
- `queries`
  - named query aliases under the view
- `sourceEvents`
  - source event names for projection lifecycle metadata
- `rebuildable`
  - whether the projection may be rebuilt

In addition, the generated entity-value layer now supports:

- canonical full projection
- predefined named projection
- generic named projection

through `MEntityValue` variants for:

- `View`
- `Summary`
- `Detail`
- `Projection(name)`

## Namespace Rule

The canonical generated Scala namespace for view projection is:

- full projection
  - `view.<Type>`
- named projection
  - `view.<projection>.<Type>`

Examples:

- `view.Item`
- `view.Person`
- `view.summary.Item`
- `view.detail.Item`

This aligns view generation with the namespace-first style already used for:

- `query.<Type>`
- `update.<Type>`

It replaces the older entity-rooted view package shape:

- old: `entity.view.<Type>`
- canonical: `view.<Type>`
- canonical named projection: `view.<projection>.<Type>`

## EntityObject / SimpleObject Rule

`EntityObject` / `SimpleObject` standard parts are defined only for the predefined projection set:

- `all`
- `summary`
- `detail`

So, for these standard model parts, the compiler only needs to prepare:

- `view.<Type>`
- `view.summary.<Type>`
- `view.detail.<Type>`

Custom projection names defined in domain models are outside this standard part set.

They may exist in the formal projection namespace, but `EntityObject` / `SimpleObject` themselves do not need to predefine them.

Current implemented line:

- `EntityObject` / `SimpleObject`
  - generate:
    - `view.<Type>`
    - `view.summary.<Type>`
    - `view.detail.<Type>`
- custom projection names
  - are represented in metadata/model
  - but are not yet auto-generated as standard part families

## Domain Model Selection Rule

Domain model definitions may define additional projection names.

When a domain model definition uses `EntityObject` / `SimpleObject`, it specifies which view projection of that standard part is used.

So the responsibility split is:

- standard part
  - provide `all / summary / detail`
- domain model definition
  - select which projection is used
  - optionally define additional projection names

The compiler therefore distinguishes:

- predefined projection support
- domain-specific projection definition

## Transformer Design Rule

Because projection names are extensible at the domain-model level, the compiler cannot rely only on fixed per-projection transformer classes such as:

- `EntityValueSummaryScalaModelTransformer`
- `EntityValueDetailScalaModelTransformer`

Those may exist as convenience implementations for common predefined projections, but they are not sufficient as the primary design.

The primary compiler design must be projection-parameterized.

In other words, the real abstraction is:

- entity value projection transformer with a projection name parameter

rather than:

- one transformer class per projection name

So the formal compiler direction is:

- generic projection-aware transformer
- optional thin wrappers for common predefined projections

Current implemented line:

- generic transformer
  - `EntityValueProjectionScalaModelTransformer(projectionName)`
- thin wrappers
  - `EntityValueViewScalaModelTransformer`
  - `EntityValueSummaryScalaModelTransformer`
  - `EntityValueDetailScalaModelTransformer`

## MEntityValue Direction

For the same reason, the entity-value model should not be interpreted as a permanently fixed closed set of:

- `View`
- `Summary`
- `Detail`

Those names may remain as convenience/sugar for predefined projections, but the formal model direction is:

- projection-bearing entity value

That means the model/compiler should be able to represent:

- full projection
- predefined named projection
- custom named projection

without requiring a brand-new transformer class for each additional projection name.

Current implemented line:

- predefined `all/summary/detail`
  - implemented end-to-end in the generator
- arbitrary custom projection names
  - representable in the model/compiler direction
  - not yet fully auto-generated as standard entity-value families

## Name Normalization

Named aliases are normalized to package/runtime tokens.

Examples:

- `searchByCity` -> `search_by_city`
- `Summary` -> `summary`

This normalization applies to:

- `viewNames`
- `queries`

## ModelCompiler Responsibilities

The ModelCompiler line is responsible for:

1. parsing `VIEW` metadata from CML
2. normalizing names into runtime-safe tokens
3. emitting the canonical full-projection package as `view.<Type>`
4. emitting the canonical named-projection packages as `view.<projection>.<Type>`
5. emitting `viewDefinitions` metadata into the generated component
6. emitting query metadata under each `ViewDefinition`
7. treating `EntityObject` / `SimpleObject` standard projection parts as `all / summary / detail`
8. using a projection-parameterized code generation model rather than a fixed transformer-per-projection-only model

The ModelCompiler is not yet responsible for:

1. lowering `QUERY.expression` into executable query logic

## Formal Status

As of this note, the Cozy-side formal spec is:

- full projection is `view.<Type>`
- named projection is `view.<projection>.<Type>`
- package root is `view`
- `VIEW > QUERY` is preserved and propagated as metadata
- predefined standard projections are `all / summary / detail`
- `EntityObject` / `SimpleObject` standard parts only need those three
- domain models may define additional projection names separately

## Implementation Status

Current implementation status is split:

- implemented
  - canonical root package `view.<Type>`
  - named aliases in `viewDefinitions.viewNames`
  - `VIEW > QUERY` metadata propagation
- not yet implemented
  - distinct generated classes for `view.<projection>.<Type>`

## Next Line

The next ModelCompiler line is:

- implement distinct generated classes for named views

for example:

- `view.summary.Item`
- `view.detail.Item`
