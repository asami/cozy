# ArtScene Value Object Datastore Shape Handoff

Date: 2026-07-09

## Summary

ArtScene exposed a generated persistence-shape problem that should be handled in the Cozy/simple-modeler generation path, not in ArtScene application code.

Single-field datatype-backed Value Objects should emit scalar values from generated `toDataStore()`. They should not emit `Record.data("value" -> ...)` for datastore persistence unless the model intentionally uses a structured/JSON datastore representation.

## Observed Problem

ArtScene fetched exhibitions can currently end up in SQLite with JSON object strings in searchable columns:

```text
period_end = {"value":"2026-09-21"}
fetch_source = {"value":"nact_english"}
title = {"value":"Picasso, through the Eyes of Paul Smith"}
```

This shape breaks or weakens generated entity search, date comparison/sorting, indexing, REST/search projection, and smoke verification. It also forces bad workarounds such as unwrapping JSON strings from SQLite rows.

## Responsibility Split

`toRecord()` and `toDataStore()` have different responsibilities:

```text
toRecord()
  external/API/presentation/generic Record boundary

toDataStore()
  canonical queryable datastore representation
```

For API/external projection, this is acceptable:

```json
{ "value": "2026-09-21" }
```

For datastore storage, a datatype-backed single-value object should become its Scala scalar value:

```text
ExhibitionDate("2026-09-21")      -> "2026-09-21"
FetchSource("nact_english")       -> "nact_english"
SourceConfidence(73)              -> 73
ExhibitionTitle("Some title")     -> "Some title"
WebsiteUrl("https://example.jp")  -> "https://example.jp"
```

`SqlDataStore` can keep storing `Record` values as JSON. The bug is that generated scalar Value Objects should not provide `Record` as their datastore value.

## Likely Fix Area

This is likely in simple-modeler Scala generation used by Cozy/sbt-cozy.

Generation should distinguish:

- scalar datatype-backed Value Object, usually one field named `value`: `toDataStore()` returns the inner scalar datastore value;
- structured Value Object: `toDataStore()` may return a structured record/JSON-compatible value when the model intentionally requires it;
- generated entity `toDataStore()`: uses each field's datastore representation, so entity columns for scalar Value Objects are scalar.

## Non-Goals

Do not fix this by adding ArtScene-local custom `EntityPersistentCreate` / `EntityPersistentUpdate` adapters.

Do not fix this by teaching Stage5B smoke scripts to parse JSON strings out of SQLite columns.

Do not special-case arbitrary JSON strings in `SqlDataStore` as a substitute for correct generated datastore shape.

## Required Regression Coverage

Add generator or Cozy scripted coverage for a minimal model:

```text
ENTITY Exhibition
  title: ExhibitionTitle
  period_end: ExhibitionDate
  fetch_source: FetchSource
  source_confidence: SourceConfidence ?

VALUE ExhibitionDate
  value: string

VALUE ExhibitionTitle
  value: string

VALUE FetchSource
  value: string

VALUE SourceConfidence
  value: int
```

Expected generated behavior:

- `ExhibitionDate("2026-09-21").toRecord()` returns `{ value: "2026-09-21" }`.
- `ExhibitionDate("2026-09-21").toDataStore()` returns `"2026-09-21"`.
- `SourceConfidence(73).toDataStore()` returns `73`.
- generated `Exhibition.toDataStore()` emits scalar columns:
  - `period_end = "2026-09-21"`
  - `fetch_source = "nact_english"`
  - `source_confidence = 73`

If feasible, add an integration-style scripted check:

1. generate the model from CML;
2. create an entity through generated `EntityStoreCreate`;
3. inspect SQLite;
4. confirm scalar Value Object columns do not contain JSON strings like `{"value":...}`.

## ArtScene Implication

ArtScene should still remove raw `EntityStoreSave(Record, ...)` paths that bypass generated entity/value persistence.

However, once ArtScene uses generated `ExhibitionCreate` / generated update patch paths, any remaining JSON value-wrapper storage belongs to the generator/Cozy path, not to ArtScene application code.

## Acceptance Criteria

- Single-field datatype-backed Value Objects generate scalar `toDataStore()` output.
- Generated entities persist those values as scalar datastore columns.
- API/external `toRecord()` shape remains unchanged.
- Structured/JSON Value Objects can still explicitly produce structured datastore values when intended.
- ArtScene Stage5B no longer needs SQLite JSON-string unwrap fallback once ArtScene uses generated persistence paths.

## Resolution Note

The original handoff captured the observed persistence-shape problem from the Value Object side. The follow-up implementation clarified the modeling direction: ArtScene scalar concepts such as exhibition dates, titles, fetch sources, and confidence scores should be modeled as `DATATYPE` first.

Single-field `VALUE` scalarization remains part of the generator contract, but it is a compatibility and safety rule for existing or intentionally value-object-centered models. It is not the preferred ArtScene modeling route for scalar concepts.

The resulting responsibility split is:

- scalar domain concepts: prefer `DATATYPE`;
- single-field datatype-backed `VALUE`: `toRecord()` stays record-shaped, `toDataStore()` returns the inner scalar;
- multi-field `VALUE`: datastore representation remains structured;
- complex `DATATYPE`: parsed by Kaleidox, but Cozy Scala generation rejects it explicitly until structured datastore semantics are defined.

## Resolution Update

The Complex `DATATYPE` boundary has since been defined for v1: Cozy/simple-modeler generate a structured datatype class under `<domain>.datatype`, and its datastore representation is `Record`.

This means Complex `DATATYPE` is no longer rejected by Cozy Scala generation. JSON/string persistence, when required by a concrete datastore adapter, remains below the generated domain class boundary.
