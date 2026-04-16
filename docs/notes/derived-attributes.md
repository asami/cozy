# Derived Attributes

status=note
updated_at=2026-04-17
target=/Users/asami/src/dev2025/cozy

## Scope

This note records the current derived attribute behavior used by Cozy
`modeler-scala`.

The immediate driver is a `SimpleEntity` based Notice model where application
terms are exposed as:

- `subject`, derived from `title`
- `body`, derived from `content`

The model keeps `title` and `content` as the SimpleEntity semantic attributes,
while the application can use `subject` and `body` as its domain vocabulary.

## Current Implemented Behavior

An `ATTRIBUTE` table may include a `derived` column.

Example:

```text
### ATTRIBUTE

| name          | type   | multiplicity | label      | derived |
|---------------+--------+--------------+------------+---------|
| senderName    | string | 1            | Sender     |         |
| recipientName | string | ?            | Recipient  |         |
| subject       | string | 1            | Subject    | title   |
| body          | string | 1            | Body       | content |
```

If `derived` has a non-empty value, the attribute is not emitted as a case
class constructor parameter.

For entity value classes, the current alias patterns are:

- `derived=title`
  - emits `def subject: String = title`
  - emits `def subject(locale: java.util.Locale): String = title(locale)`
  - emits `def withSubject(value: String): Notice`
- `derived=content`
  - emits `def body: Option[I18nText] = content`
  - emits `def body(locale: java.util.Locale): Option[String] = content(locale)`
  - emits `def withBody(value: String): Notice`

The derived attributes remain schema-visible. This is important for Web form,
table, and view generation because the application-facing field name should
still be available in schema metadata.

## Projection Classes

Query and update projection classes do not emit concrete alias methods for
derived attributes.

Reason:

- query/update classes carry `Condition` / `Update` values, not entity values
- `subject = title` is an entity-level value alias, not a query/update value
  expression

The generated query classes currently refer to the canonical entity schema
instead of duplicating derived schema columns locally.

## SimpleEntity Interaction

`SimpleEntity` already owns `id` and SimpleObject attribute groups such as
`nameAttributes` and `descriptiveAttributes`.

Application models extending `SimpleEntity` should not redefine `id` in their
own `ATTRIBUTE` table.

For Notice:

- `subject` is an application alias for SimpleEntity `title`
- `body` is an application alias for SimpleEntity `content`
- persistence keeps the underlying SimpleEntity attributes

## Parser Notes

The intended path is column-name based reading.

There is still a positional fallback for older or partially normalized tables.
The fallback is compatibility behavior, not the preferred authoring style.

Future improvement:

- warn when a headed table cannot resolve expected columns by name
- emit structured errors for missing required columns

## Open Items

Computed derived attributes are not finalized.

Potential shape:

```text
| fullName | string | 1 | Full Name | firstName,lastName |
```

Open design questions:

- Should comma-separated `derived` mean default string composition?
- Should computed derived attributes use a separate expression field?
- Should computed behavior use an `IMPLEMENTATION` style pattern?
- How should locale-sensitive values participate in computation?

Until those questions are settled, the stable implemented scope is alias-style
derived attributes such as `derived=title` and `derived=content`.
