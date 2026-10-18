# CML POWERTYPE fixed persistence codes

Decision date: 2026-10-19
Status: adopted specification; connected implementation accepted under Workbench STEP-2
local binding `tkw-step2-connected-acceptance-commit-20261019-01@1`
Delivery: [Workbench-driven implementation plan](../phase/powertype-persistence-code-plan.md)

## Scope and declaration

A finite POWERTYPE used as a persisted Entity attribute MUST declare an explicit
fixed integer code for every kind. A POWERTYPE explicitly declared by its owning
model contract as a numeric persistence type has the same obligation even before
an Entity uses it. A display-only or transient vocabulary MAY omit codes.
Being a POWERTYPE alone does not require database persistence.

Use the existing `value` column; `name` remains the stable symbolic value and
`label` is presentation. This decision introduces no new CML keyword or flag.
Persistence use is determined from resolved model declarations, not runtime
payloads or SQL column inspection. A standalone type may declare its complete
numeric mapping without a new marker syntax.

```text
# POWERTYPE

## ProposalPreparationState

| name | value | label |
| --- | --- | --- |
| prepared | 1 | Prepared |
| unprepared | 2 | Unprepared |
```

For persistence, `value` is an explicitly written base-10 integer in the signed
16-bit range `-32768..32767`. The declared database mapping is `XShort`, shared
with ordinary Short attributes. The common persistence bridge retains Scala
`Short` through SQL value transport so PostgreSQL and MySQL select signed
`SMALLINT`; SQLite uses `INTEGER` with the same range checked before storage
and before typed decoding. Fractional, nonnumeric and out-of-range persisted
values are invalid, not equivalent to omitted values. No code, including zero,
is implicitly reserved as absence; an explicitly declared code denotes a real
kind. Optional attributes use the established Option/storage-absence contract.

The declaration parser and existing model-owned `dbValue` / `fromDbValue` and
Core `NumericCodeCodec` correspondence remain Int-valued APIs. That does not
select the physical SQL width. Explicit transient codes may occupy the Int
range; a later persistent use must satisfy the narrower XShort contract.
Arbitrary-precision integer/decimal storage retains its separate exactness
contract and does not inherit this finite-code range.

## Semantic validation before generation

- Resolve POWERTYPE references through the actual persistent attribute graph,
  including optional/repeated wrappers and nested persisted model values.
  Imported types require their authoritative declaration/mapping; do not guess
  from a type name or inspect companion methods with reflection.
- Reject a missing code for any kind when the type participates in numeric
  persistence. Reject duplicate numeric codes within one POWERTYPE and invalid
  explicit numeric declarations. Codes may repeat across distinct POWERTYPEs.
- Diagnose the owning type/kind and the persistent use that makes a missing
  or out-of-Short-range code invalid. Return the existing structured model/generation failure before
  emitting a successful usable product for the invalid model.
- A transient/display-only type with omitted codes remains valid. It does not
  acquire a database code implicitly. If later used in persistence, the same
  pre-generation validation requires a complete mapping.
- Never derive storage codes from declaration order, ordinal, name or label.
  Never change the storage representation to text to work around missing codes.
  This replaces the previous declaration-order fallback in the grammar notes;
  it does not claim that the current implementation has already removed it.

## Code lifetime and conversion

Reordering kinds or changing labels MUST NOT change codes. Once published or
persisted, a code MUST NOT be reassigned to another meaning or reused after its
kind is retired. Keep retired assignments documented in the owning model's
evolution/migration record. This is a schema-evolution obligation; it does not
require a new runtime history registry, hash or copied-state comparison system.

For each supported declared value, decoding its encoded code returns that value.
Unknown stored codes fail explicitly through the existing failure model. They
must not become None, the first kind, a default kind or a successful raw integer.
Existing stored ordinal/text representations need an explicit migration decision;
do not reinterpret or rewrite live data automatically. A database-backed retired
value needs an explicit retained-read or migration policy.

## Ownership and separation from external presentation

- Core owns a typed value/code conversion contract without model, SQL or provider
  dependencies. A missing common contract is a Core implementation dependency.
- Each POWERTYPE's CML declaration owns the mapping; SimpleModeler emits its
  typed implementation and inverse mapping using that Core contract.
- Cozy preserves explicit declaration versus omission and performs/coordinates
  resolved-model validation before invoking successful generation. Information
  must not be lost by turning malformed or missing codes into defaults.
- CNCF's declared persistence mapping selects the codec and performs database
  write/read conversion while type information is still available.
- ExternalValueEncoder and ModelValueEncoder retain their one-way external
  Record/presentation roles. JSON/CLI symbolic strings need not equal numeric
  storage values. Do not pass through a generic string projection first and
  then infer the storage codec. Update/Condition semantics remain separately owned.

This contract concerns finite POWERTYPE codes. It does not add StateMachine
transition behavior or redesign open classification vocabularies.

## Executable acceptance scenarios

1. Generate and compile the example through the ordinary CML route; save/read
   both values via the declared EntityStore path and reopen the provider.
   Assert signed16 XShort stored codes and the original typed values, while JSON/CLI
   still expose `prepared` / `unprepared`.
2. Reorder the source kinds and change labels; generated conversions retain 1/2.
3. Accept `-32768`, zero and `32767`, and reject `-32769`, `32768`, missing,
   duplicate or malformed codes before successful generation, including a
   persistent reference through a wrapper/nested value or imported declaration.
4. Accept a transient/display-only vocabulary without codes; reject its reuse
   as a persistent attribute until a complete explicit mapping is supplied.
5. Report an unknown stored code as failure; distinguish an absent optional
   attribute from a present declared value. Exercise encode/decode correspondence
   through the typed codec, not only source-text assertions.

These scenarios are one connected delivery target, not separate per-helper
review cycles. No implementation, test success or Phase completion is asserted
by this specification change.
