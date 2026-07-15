# CML Account Semantic Scalar Migration

Date: 2026-07-15

## Context

Phase 16 identified string-only Datatypes in `textus-user-account`. Some were
nominal wrappers around semantics already suitable for a shared predefined CML
type, while others carry unresolved account-domain policy. Replacing every
wrapper mechanically would erase distinctions around identity, secrets,
redaction, lifecycle, and structured device information.

## Decision

The first account migration replaces only the low-ambiguity scalar families:

- display title -> `title` / `I18nTitle`;
- email address -> `email` / `EmailAddress`;
- phone number -> `phone` / `PhoneNumber`;
- locale -> `locale` / `java.util.Locale`;
- timezone -> `timezone` / `java.util.TimeZone`;
- IP address -> `ip-address` / `IpAddress`.

The same semantic type is used at entity, Create/Update, query, and operation
boundaries. `title` remains one locale-aware value that can contain one or many
locale entries. Stored locale entries are preserved; effective display fallback
does not rewrite the stored value.

Phone numbers remove visual separators and require explicit E.164 international
identity; the model does not guess a country code. Locale external and
datastore values use BCP 47 language tags, and timezone values use canonical
zone IDs.

## Deferred Domain Decisions

The migration intentionally keeps domain-specific wrappers for login name,
external subject ID, suspended actor/reason, password and token hashes, session
references, client ID, device information, and user agent. Their contracts need
decisions about namespaces, normalization, redaction, display, bounds, or
structured representation. A broad predefined type must not be selected from a
class name alone.

Finite vocabulary and lifecycle migrations remain separate powertype and
statemachine work. They are not inferred by the scalar catalog.

## Implementation Evidence

- Cozy classification reads the normalized Kaleidox CML model rather than
  reparsing rendered description text.
- The predefined catalog projects canonical length/format constraints to model
  generation and Web validation hints.
- simplemodeling-lib provides parsers, ValueReaders, and codecs required by the
  generated Scala 3.3.8 boundary.
- generated external/datastore conversion emits BCP 47 locale tags and timezone
  IDs.
- text constraints execute against every locale entry for Create and Update
  models, including title and `DescriptiveAttributes` fields.

## Verification

- simplemodeling-lib: 356 tests passed.
- simple-modeler: 30 tests passed.
- Cozy: 533 tests passed.
- textus-user-account: 86 tests passed after clean generation.
- Account CAR build succeeded and emitted a valid component descriptor and ABI
  manifest.
- Account and notification CAR lint produced no deterministic FAIL.
- textus-sample-app, cwitter, notice-board-event-driven, and
  notice-board-static-form compile with the resolved sbt-cozy 0.1.14 plugin.
- `git diff --check` passed across all touched repositories.

Warnings that remain are not hidden: release ABI baselines and SNAPSHOT tooling
are release-state concerns, while direct datastore/config access in the account
ComponentFactory is existing internal-DSL migration debt outside this scalar
slice.
