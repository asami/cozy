# CML Value and Datatype Contract

status=accepted
updated_at=2026-07-16
target_phase=16

## Scope

This specification defines the implemented CML contract for operation Values,
Results, Datatypes, model-kind separation, semantic text, and domain constraint
projection. It applies to CML parsing, normalized model metadata, generated
Scala 3.3.8 source, CNCF schema metadata, and generated runtime boundaries.

Locale-set policy, secret redaction, driver-specific normalization, and text
families without an accepted range are outside this specification.

## Operation Input

A reusable operation input MUST be a top-level `VALUE` with an explicit
`input-kind` of `COMMAND` or `QUERY`. The declared input kind MUST match every
operation that references it.

An operation-local input is defined below `INPUT`:

- `INPUT/ATTRIBUTE` defines an anonymous local Value;
- `INPUT/VALUE` plus `ATTRIBUTE` defines a named local Value;
- `INPUT/TYPE` references an existing input Value or the reserved `void` type.

The enclosing operation determines the input kind of a local Value. A local
Value MUST NOT declare `input-kind`. `INPUT/TYPE` MUST NOT be combined with a
local `VALUE` or `ATTRIBUTE` definition. An empty `INPUT` section is invalid.

Anonymous names are deterministic:

- command input: `<PascalOperationName>Command`;
- query input: `<PascalOperationName>Query`.

Two source definitions that normalize to the same generated identity MUST be
rejected rather than renamed.

## Operation Output

An operation output is a Result Value:

- `OUTPUT/ATTRIBUTE` defines an anonymous local Result named
  `<PascalOperationName>Result`;
- `OUTPUT/VALUE` plus `ATTRIBUTE` defines a named local Result;
- `OUTPUT/TYPE` references a declared Result or a Result in the selected CNCF
  predefined catalog.

A local output has implicit `OperationResult` semantics. `OUTPUT/TYPE` MUST NOT
be combined with a local output definition. Empty output sections, undefined
output types, and raw scalar outputs are invalid.

The minimum CNCF-owned predefined Result catalog contains:

- `OperationResult`;
- `UnitResult` with no payload fields;
- `IntResult` with one `value: int` field.

Cozy MUST resolve predefined Results from the selected CNCF runtime descriptor
and MUST NOT maintain an independent catalog with different fields.

## Normalized Operation Contract

Every accepted operation form normalizes before generation to one contract
containing at least:

- operation name and command/query kind;
- input type and `COMMAND_VALUE` or `QUERY_VALUE` role;
- output Result type;
- ordered parameter metadata;
- ordered Result field metadata.

Normalization MUST preserve use-case, rule, scenario, precondition,
postcondition, descriptive, and narrative metadata. Implementations MUST read
structure from the CML AST and MUST NOT recover local schemas or metadata by
scanning rendered text.

## Model Kind Contract

The following CML constructs are distinct contracts:

| Construct | Required generated meaning |
|-----------|----------------------------|
| `VALUE` | Immutable payload or composite domain value |
| plain `DATATYPE` | Validated nominal scalar |
| complex `DATATYPE` | Structured datatype |
| `POWERTYPE` | Closed vocabulary |
| `STATEMACHINE` | Lifecycle states and owned transitions |

A plain Datatype MUST retain its declared name, underlying datatype, and typed
constraints. Generated Scala MUST provide nominal construction/parsing,
`ValueReader`, scalar codec, Record/datastore projection, and validation.

Nominal identity MUST be retained in required, optional, repeated, non-empty
repeated, Create, Read, Query, Update, operation, schema, Help, form, automatic
REST, and OpenAPI boundaries. A generator MUST NOT replace an accepted nominal
type with primitive `String`, `Condition[String]`, or `Update[String]`.

A finite vocabulary uses a Powertype. State whose transitions are owned by the
model uses a Statemachine. A provider-extensible registry or externally owned
lifecycle MUST NOT be inferred as closed from its current values or its name.

## Semantic Text Contract

Semantic role determines whether text is localized:

- `name` uses nonlocalized `Name`;
- `label`, `title`, `headline`, `brief`, `summary`, `lead`, `abstract`,
  `remarks`, `description`, and `text` are locale-aware;
- `title` uses `I18nTitle` and accepts one or multiple locale entries through
  the same type;
- `text` uses locale-aware `I18nText` with a default 1..8192 length contract
  for every locale entry;
- `name`, `identifier`, `token`, `url`, `uri`, `urn`, `locale`, `timezone`,
  `ip-address`, `email`, and `phone` are nonlocalized;
- document body uses `ContentBody` and is not interchangeable with
  `I18nText`.

Driver-owned identities, open registry keys, powertype and statemachine values,
hashes, secrets, session/client references, provider codes, and source
diagnostics MUST remain nonlocalized. A localized label for such a value MUST
be separate presentation metadata and MUST NOT alter its identity. A locale or
timezone value is a selector for interpretation and presentation, not localized
text itself.

Descriptive text fields retain their locale entries through
`DescriptiveAttributes`. Effective display methods select fallback for the
active locale without mutating, discarding, or merging the stored fields.
Record, API, and datastore projection MUST preserve the locale structure;
presentation code performs display selection.

## Domain Constraints

CML authors express string length with `min-length` and `max-length`. Numeric
`min` and `max` MUST NOT be interpreted as text length. `pattern`, `format`,
and other accepted typed constraints remain domain metadata.

For an I18N value, text length is evaluated independently for every locale
entry. Locale count and locale-set policy are separate concerns.

Domain constraints MUST be preserved through:

- normalized CML/model metadata;
- generated Scala construction and decoding;
- Record and datastore conversion;
- generated operation request validation;
- entity Create, Query, and Update schema;
- Help, form, automatic REST, and OpenAPI projection.

Web metadata controls presentation and input behavior. It MAY carry projected
validation hints, but MUST NOT create or override domain constraints.
`web-min-length`, `web-max-length`, and `web-pattern` are not accepted domain
constraint aliases.

## Multiplicity

Generated schema and operation parameters MUST retain these distinct authored
contracts:

- required scalar;
- optional scalar;
- repeated collection;
- non-empty repeated collection.

Requiredness in generated Web metadata is a projection of multiplicity and
does not replace it.

## Compatibility and Scaffold Output

Legacy top-level `COMMAND` and `QUERY`, compatibility `EXTENDS CommandAction`
or `QueryAction`, existing inline Value trees, and operation `PARAMETER` forms
remain readable where the accepted parser defines them. They normalize to the
same canonical operation model before generation.

New scaffold output MUST:

- emit top-level `VALUE` plus `input-kind` for reusable inputs;
- use operation-local Values for one-use input/output schemas;
- use predefined Results for simple output;
- avoid new top-level `COMMAND` and `QUERY` sections;
- avoid redundant local operation-base declarations.

Compatibility syntax does not authorize a primitive fallback or a second
runtime contract.

## Required Diagnostics

Cozy MUST reject deterministically:

- operation/input kind mismatch;
- missing or unsupported reusable `input-kind`;
- local input with an authored `input-kind`;
- simultaneous referenced and local input/output definitions;
- empty input/output sections;
- undefined non-`void` input or undefined output types;
- scalar output in place of a Result;
- predefined Result absent from the selected CNCF catalog;
- incompatible compatibility role metadata;
- duplicate normalized local identities;
- text values outside accepted length, pattern, or parser-backed contracts.

Diagnostics SHOULD identify the owning component when known, service,
operation, source direction, source type, and source location.
