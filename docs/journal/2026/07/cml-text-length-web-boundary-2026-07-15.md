# CML Text Length Constraint and Web Boundary

date=2026-07-15
phase=16
status=implementation

## Context

`MAttribute.Web` previously parsed `web-min-length`, `web-max-length`, and
related validation properties and appended them to general
`MAttribute.constraints`. That made Web form hints an accidental source of
domain validation. Generated Web schema received length metadata, while Scala
construction did not implement the corresponding `min_length` and
`max_length` constraints.

## Decision

The canonical CML properties are domain constraints:

- `min-length`;
- `max-length`;
- `pattern` and other semantic constraints where applicable.

The normalized constraint model is the source for Scala validation and Web
schema projection. `MAttribute.Web` retains only presentation and
input-control metadata and no longer injects validation constraints. Kaleidox
does not retain or interpret the former Web-prefixed validation properties as
compatibility aliases.

For localized values, minimum and maximum length are checked independently for
every locale entry. Validation never measures a wrapper's `toString`
representation and never collapses locale entries to an effective display
value.

## Implementation

- generated constrained classes contain one private text-value extractor for
  plain strings, semantic string datatypes, localized wrappers, optional
  values, repeated values, update directives, and `ContentBody`;
- schema-owned `title` and `content` constraints follow the existing
  `NameAttributes` and `ContentAttributes` delegated accessors instead of
  validating a flattened display value;
- generated Scala validation uses that extractor for normalized `min_length`
  and `max_length` constraints, as well as text `pattern` and `format`
  constraints, without adding a new runtime-library API;
- generated `WebValidationHints` continue to project the same normalized
  constraints;
- the modeler fixture now uses canonical `min-length`, `max-length`, and
  `pattern` columns instead of Web-prefixed validation properties.

## Remaining Verification

Driver migration must still verify datastore, operation request,
REST/OpenAPI, form, and Help behavior through the notification and account CAR
surfaces before Phase 16 closes the end-to-end projection item.
