# CML Operation Value Refactoring Discussion

Date: 2026-07-15

Status: recorded

## Context

The discussion started while reviewing the generated CML and Scala contract of
`textus-user-notification`.

The review first identified `UserNotificationScalarDatatypes.scala` as a bad
smell. It introduced project-local scalar wrappers, but generated Entity fields
continued to use strings and the project specifications passed after the
unreferenced wrapper source was removed. That exposed a broader risk: a CML
declaration can look semantically strong in source without being represented by
the normalized model and generated contract.

The project contained top-level `# COMMAND` and `# QUERY` sections even though
the project owner did not recall choosing those sections as the CML design.
Inspection of Cozy showed that they were not project-local additions:

- the current Modeler treats `QUERY`, `COMMAND`, `VALUE`, `ENTITY`, and
  `DATATYPE` as declared type sections;
- the current operation normalization derives `inputValueKind` from a matching
  command/query input definition or, when one is not found, from the operation
  kind;
- current grammar notes describe top-level `# COMMAND` and `# QUERY` as
  operation input value definitions;
- current Cozy scaffold output emits those sections.

Repository history shows that Cozy commit `94da76c` froze the operation grammar
direction on 2026-03-22 and that the initial `textus-user-notification` commit
already contained the top-level sections. This establishes implementation
history, but does not establish that the project owner explicitly selected the
design.

The review also found that the model has no field named `inputKind`. The active
model and generated metadata use `inputValueKind`, with `CommandValue` and
`QueryValue` represented at the generator boundary as `COMMAND_VALUE` and
`QUERY_VALUE`.

This made the main modeling problem visible: command/query is a characteristic
of an input Value, but the current source grammar presents it as a top-level
type category. That makes `# COMMAND` and `# QUERY` look like operation
declarations even though they define payload structures.

## Discussion Result

The preferred CML direction is to make `VALUE` the single structural concept.

Reusable operation input Values are defined under top-level `# VALUE` and carry
an explicit `input-kind` property:

```cml
# VALUE

## SearchNotifications
- input-kind :: QUERY

### ATTRIBUTE
| name  | type   | multiplicity |
|-------|--------|--------------|
| text  | string | ?            |
| limit | int    | ?            |
```

The operation kind and the referenced Value's `input-kind` must agree. A
`COMMAND` operation referencing a query input Value, or a `QUERY` operation
referencing a command input Value, is a model error.

Values used by only one operation should be definable inside that operation.
For an anonymous local input, an `ATTRIBUTE` section directly below `INPUT`
creates a deterministic local type:

- `QUERY` creates `<OperationName>Query`;
- `COMMAND` creates `<OperationName>Command`.

The input kind is inferred from the enclosing operation. A `VALUE` leaf below
`INPUT` allows the author to replace the generated name with an explicit local
name.

Output follows the same structural rule. An `ATTRIBUTE` section directly below
`OUTPUT` creates `<OperationName>Result`, while a `VALUE` leaf supplies an
explicit local result name.

Simple results do not require a local Value definition. `OUTPUT TYPE` may
reference a Result supplied by the CML/CNCF standard Result catalog, including
`UnitResult` and `IntResult`. Cozy resolves these as known types; CNCF owns
their runtime definitions and payload contracts.

## Compatibility Decision

Top-level `# COMMAND` and `# QUERY` remain accepted compatibility syntax. They
normalize internally to `# VALUE` with `input-kind=COMMAND` or
`input-kind=QUERY`. New scaffold output uses the canonical `# VALUE` form.

Existing named inline `VALUE` trees also remain accepted. Phase 16 introduces a
shorter local form without removing the currently parsed nested form.

The existing `PARAMETER` convenience syntax must be handled as compatibility
input syntax during implementation. Its generated-name migration from
`<OperationName>Input` to the command/query-specific canonical name requires
explicit executable specifications and diagnostics so that ABI changes are not
silent.

## Current Implementation Gap

The current parser/modeler already supports named inline Values, but it does not
support the proposed anonymous `INPUT/ATTRIBUTE` and `OUTPUT/ATTRIBUTE` forms.
Top-level input kind is currently encoded by the containing `# COMMAND` or
`# QUERY` section rather than an `input-kind` property on `# VALUE`.

The current Cozy built-in operation type set recognizes `OperationResult`,
`CommandAction`, and `QueryAction`. `UnitResult` and `IntResult` are not yet
present in Cozy or the inspected CNCF source tree, so Phase 16 must coordinate
the Result catalog with CNCF rather than adding parser-only names.

## Phase Decision

Cozy Phase 16 is CML Operation Value Refactoring. Its focus is this operation
Value contract:

- unify reusable operation inputs under `# VALUE` plus `input-kind`;
- add anonymous and named local input/output Value definitions;
- add CNCF-owned predefined Result resolution;
- preserve explicitly identified compatibility grammar;
- update scaffolding, diagnostics, metadata, and executable specifications;
- migrate representative CAR CML, beginning with
  `textus-user-notification`, after the new Cozy grammar is available;
- use `textus-user-account` as the second driver for the larger authentication,
  session, credential, profile, and management operation surface and for
  regression of existing literate contract metadata.

The driver order is intentional. Notification provides a smaller migration
surface for establishing the canonical Value grammar and ABI comparison.
Account then tests the same model against a broader real component; it is not a
second parser fixture and must retain its use-case, precondition,
postcondition, rule, and scenario projections.

## Semantic Scalar Extension

The driver review also exposed many `DATATYPE` definitions whose only member is
`value: string`. The source name gives these wrappers an appearance of domain
precision, but the generated contract does not gain a finite vocabulary,
lifecycle transition, normalization rule, value range, privacy policy, or
composite structure merely from that wrapper.

Phase 16 therefore also classifies these definitions from their model meaning:

- finite categories, selectors, priorities, channels, and similar closed
  vocabularies normally become powertypes;
- entity lifecycle state becomes statemachine-owned when allowed transitions,
  guards, or events are part of the domain;
- names, titles, descriptive text, URLs, locale, timezone, and identifiers use
  precise CML predefined datatypes where their contracts fit;
- domain-specific Values or Datatypes remain only when they define narrower
  constraints, normalization, privacy/redaction, opaque representation, or
  composite behavior.

For text values, length is part of the value range. Phase 16 must define
unambiguous minimum/maximum length metadata, preserve it in the normalized AST,
and project it into generated validation, datastore schema, forms,
REST/OpenAPI, and Help. Existing `name = Name` and `title = I18nTitle`
semantics are the baseline. Remaining `string`, `text`, label, description,
message, and related contracts still require specification; names alone must
not imply undocumented limits.

Text classification also has a localization dimension, but it is not a rule
that every semantic text type must have scalar and I18N variants. The existing
model intentionally treats `name` as a stable nonlocalized `Name` and `title`
as one `I18nTitle` structure that can hold either one locale entry or multiple
locale entries. A plain string constructs or decodes one entry; the structured
codec preserves multiple entries. Other display labels, descriptions,
messages, and body text must be classified consistently with this structure.
I18N values preserve all locale entries; display fallback selects an effective
entry but does not replace the stored multilingual value. Length constraints
apply to each locale entry, with separate rules for required/default locale,
allowed locales, and duplicate locale tags.

The provisional grammar is recorded in
`docs/notes/cml-operation-value-refactoring-spec-proposal.md`. Phase progress is
tracked by `docs/phase/phase-16-checklist.md`.

## First Implementation Slice

The first implementation slice started on 2026-07-15 with top-level Value
input-kind normalization.

The first failing executable specification exposed a common parser gap before
the intended Cozy validation ran. A description-list property directly below a
Value was represented by the SmartDox `Dl` AST node, but Kaleidox
`SchemaClass.createOption(Section)` did not accept that direct child. Adding a
Cozy text scanner would have hidden that AST contract failure, so the fix was
made in Kaleidox instead:

- Kaleidox moved from release `0.6.17` to development
  `0.6.18-SNAPSHOT` before source modification;
- direct Value metadata is preserved in `ValueClass.properties` through
  `CmlSectionFormat.keyValues(Section)`;
- schema construction ignores the already-classified `Dl` metadata node rather
  than treating it as a field definition;
- `CmlSectionFormat.directKeyValues(LogicalSection)` provides the common
  LogicalSection-to-SmartDox AST route for callers such as Cozy.

Cozy now resolves a service operation input kind in this order:

1. legacy `# COMMAND` or `# QUERY` input definition;
2. explicit top-level Value `input-kind`;
3. compatibility top-level Value `EXTENDS CommandAction` or
   `EXTENDS QueryAction`;
4. operation-kind inference only for input types that are not top-level Value
   declarations.

A referenced top-level Value without any canonical or compatibility input-kind
metadata is rejected. Unsupported values and operation/input mismatches are
also rejected deterministically. Generated metadata remains `COMMAND_VALUE` or
`QUERY_VALUE`.

The working-tree baseline was classified before driver migration. In both
driver repositories, build/plugin/catalog/manual changes predate this slice and
remain untouched. The newly introduced string-only Datatype blocks are Phase
16 semantic-modeling debt, while the account ComponentFactory and datastore
spec changes belong to the existing account implementation work. Neither
driver CML is migrated in this first slice; current legacy command/query source
is used as compatibility regression input.

The completed implementation was verified with both focused and full suites:

- Kaleidox `CmlSectionFormatSpec`: 34 tests passed;
- Kaleidox full test suite: 114 tests passed and 2 tests were ignored;
- Cozy focused operation Value specifications: 31 tests passed;
- Cozy full test suite: 499 tests passed;
- the unchanged notification driver generated successfully with 13 operation
  metadata entries;
- the unchanged account driver generated successfully with 20 operation
  metadata entries.

Two existing Cozy fixtures were updated because they represent canonical
top-level operation Values and therefore now require explicit `input-kind`.
The driver working trees were not modified by this slice.

## Natural I18N Goal

The Phase 16 discussion established a higher-level usability goal: a normally
written CML model should become naturally I18N-capable. Model authors should
select semantic types such as title, label, description, or message instead of
manually designing locale containers. A plain string is the concise
single-locale form, while structured input carries multiple locale entries in
the same generated type.

This decision does not make every string localized. Stable names, identifiers,
protocol values, hashes, and tokens remain nonlocalized because that is their
semantic contract. The current implementation specification in notes records
this distinction and will be updated as the predefined text catalog is
verified.

## Local Operation Value Implementation

Phase 16.3 implemented operation-local input and output Values on 2026-07-15.
The parser reuses the SmartDox Section AST: Kaleidox can now build a
`SchemaClass` with an explicit local name from an existing operation Section.
No CML text or regular-expression reconstruction was introduced.

The implementation accepts anonymous `INPUT/ATTRIBUTE` and
`OUTPUT/ATTRIBUTE`, canonical named `VALUE` and `ATTRIBUTE` siblings, and the
existing nested inline Value tree. Anonymous inputs use the enclosing operation
kind to select the `Command` or `Query` suffix. Local inputs cannot declare
`input-kind`; compatibility `EXTENDS CommandAction` or `QueryAction` is checked
against the operation kind.

The implementation also established two projection decisions:

- local output Result semantics are carried by the normalized output role and
  generated `resultFields`, not by adding a Scala inheritance marker;
- the current Scala generator writes local Values into one component-level
  value package, so duplicate projected local names and local/top-level name
  collisions fail instead of receiving unstable numeric suffixes.

`PARAMETER` now normalizes to the same `Command` or `Query` naming rule. Its
previous `Input` suffix was parser-only because Cozy already rejects top-level
OPERATION as a generation source; no accepted CAR ABI was changed.

Focused verification passed with 36 Kaleidox tests and 37 Cozy tests. Full
verification passed with 116 Kaleidox tests (2 ignored) and 509 Cozy tests.
The unchanged notification and account CML sources generated successfully, and
both generated trees were identical to their Phase 16.2 baselines.
