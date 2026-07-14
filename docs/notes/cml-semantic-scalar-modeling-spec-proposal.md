# CML Semantic Scalar Modeling Specification Proposal

status=proposal
updated_at=2026-07-15
target_phase=16

## 1. Problem

The `textus-user-notification` and `textus-user-account` models contain many
`DATATYPE` definitions with a single `value: string` attribute. A distinct type
name is useful only when the model also defines distinct semantics. A nominal
wrapper without vocabulary, transition, normalization, value range, privacy,
or composition semantics makes the source look more precise than the generated
contract.

Phase 16 classifies these types by model meaning and strengthens CML predefined
text and constraint support. This note is the implementation specification
proposal used to write executable specs and implementation. After
implementation and driver verification, the resulting stable contract is
promoted to design, specification, and accepted grammar documents.

One primary usability goal is that an ordinary CML model becomes naturally
I18N-capable without requiring every model author to design locale containers.
User-visible semantic types use locale-aware runtime contracts by default, and
a plain string remains the concise single-locale input form. Structured input
adds multiple locale entries without changing the field's CML type or its
generated contract.

## 2. Classification Rule

Use the narrowest structural model that represents the domain contract:

| Model meaning | CML construct | Required evidence |
|---|---|---|
| Finite vocabulary or selector | `POWERTYPE` | Declared stable values and optional labels |
| Lifecycle state with transitions | `STATEMACHINE` | States, transitions, guards, or events |
| Ordinary scalar text | Predefined datatype | Semantic text kind and value range |
| Localized display text | Predefined I18N datatype | Locale-tagged values and fallback policy |
| Narrower domain scalar | `DATATYPE` or scalar `VALUE` | Constraint, normalization, format, privacy, or opaque representation |
| Composite immutable concept | `VALUE` | Multiple meaningful fields or composite invariants |

A one-field wrapper is not rejected merely because it wraps text. It remains
valid when it carries a contract that the predefined type does not express.
Hashes, tokens, external subject identifiers, provider message identifiers,
and similar opaque values must not become powertypes simply because their
runtime representation is a string.

## 3. Powertype and Statemachine Boundary

Use a powertype when the important fact is membership in a finite vocabulary.
Examples include notification audience kind, channel, priority, and other
selectors whose transition history is irrelevant.

Use a statemachine when the important fact is an entity's lifecycle and the
model owns allowed transitions, guards, events, or transition actions. An
entity status must not be reduced to a powertype when doing so would discard
those rules.

An external or extensible provider registry may remain an identifier or open
domain scalar rather than a closed powertype. The driver inventory must record
whether each vocabulary is closed, versioned, or provider-extensible.

## 4. Text Semantic Axis

Raw `string` is the least precise text contract. CML should expose semantic
predefined types where the runtime contract exists or can be established.

The existing baseline is:

- `name`: stable nonlocalized `Name`;
- `title`: one locale-aware `I18nTitle` that stores either a single locale
  entry or multiple locale entries.

This is intentionally asymmetric. CML does not need a scalar and I18N version
of every semantic text type. The type's domain meaning determines whether it is
localized.

Consequently, model authors normally select meaning (`title`, `label`,
`description`, `message`, or another accepted display-text type), not storage
cardinality. The generator supplies the locale-aware structure. Explicit
nonlocalized types remain appropriate for stable names, identifiers, protocol
values, hashes, tokens, and other text whose identity must not vary by locale.

Additional semantic families to confirm include:

- `label`: compact display label;
- `text`: general body text;
- `brief`, `summary`, and `description`: bounded descriptive text when their
  distinct contracts are justified;
- `identifier`, `url`, `uri`, `locale`, and `timezone`: non-generic scalar
  concepts with existing parsing semantics.

The accepted catalog must define runtime type, normalization, empty-value
policy, minimum length, maximum length, serialization, datastore mapping, and
form/API schema. A type name alone must not imply undocumented limits.

## 5. Localization Axis

Localization is independent from text length, but it is part of a semantic
type's contract. The accepted catalog starts from nonlocalized `name` and the
single/multi-locale capable `title`; it must not manufacture symmetric pairs
without a modeling need. In particular, CML does not define separate
single-locale title and multi-locale title concepts.

This is the natural-I18N rule:

- a user-visible semantic text type is locale-aware unless its accepted type
  contract explicitly says otherwise;
- a plain CML scalar value is decoded as one locale entry using the applicable
  default-locale policy;
- a structured value preserves one or more explicitly tagged locale entries;
- generated datastore, form, API, and Help surfaces retain the same semantic
  type and must not collapse it to scalar storage;
- technical or identity text remains nonlocalized by semantic classification,
  not by a generator accident.

Runtime-backed I18N families already present in `simplemodeling-lib` include
`I18nString`, `I18nLabel`, `I18nTitle`,
`I18nBrief`, `I18nSummary`, `I18nDescription`, `I18nText`, and
`I18nMessage`. `I18nTitle` is already the runtime contract for `title` and
wraps `I18nString`, whose non-empty entry vector represents both cardinalities:

- construction or decoding from a plain string produces one locale/value
  entry;
- structured JSON encodes and decodes one or more locale/value entries;
- display lookup chooses an effective entry without changing the stored entry
  vector.

Phase 16 must decide which remaining families are canonical CML types and which
are framework metadata concepts before changing driver source.

Stable symbolic names, login names, identifiers, hashes, and protocol tokens
are normally nonlocalized. User-visible labels, titles, descriptions,
messages, and narrative body text may be localized when the owning domain
requires it. A field is not made I18N solely because it is displayed in a UI;
the domain must own the translations.

I18N contracts must define:

- locale tag normalization;
- required or default locale behavior;
- allowed locale restrictions when configured;
- duplicate locale rejection;
- fallback order for display;
- preservation of all entries during update and serialization;
- per-entry text constraints.

Fallback returns an effective display value. It must not collapse or overwrite
the stored multilingual value.

## 6. Text Value Range

Length is part of the value range for scalar and I18N text. Phase 16 should use
unambiguous text-specific constraints, provisionally `min-length` and
`max-length`, rather than relying on numeric `min` and `max` interpretation.

Additional constraints may include:

- `pattern` for a domain syntax;
- `format` for a standard parser-backed format;
- whitespace normalization;
- case normalization where identity semantics require it;
- prohibited control characters;
- redaction or secret-display policy.

For an I18N value, minimum and maximum length apply to each locale entry. Entry
count and locale-set constraints are separate from text length.

The normalized AST and model metadata must retain typed constraints. Generator
and runtime boundaries must project them consistently to:

- Scala construction and validation;
- datastore column or structured-value schema;
- generated forms and validation messages;
- automatic REST and OpenAPI schema;
- generated Help and manual metadata.

## 7. Driver Inventory Direction

The complete AST-backed inventory and provisional per-item classification are
maintained in `cml-semantic-scalar-driver-inventory.md`. It records 19
string-only Datatypes in `textus-user-notification`, 16 in
`textus-user-account`, and no string-only Values in either current driver.
The following are the remaining investigation themes, not accepted migrations.

### textus-user-notification

- audience kind, channel, priority, and status require
  powertype/statemachine classification;
- notification type and delivery provider require a closed-versus-extensible
  vocabulary decision;
- title, body, error message, and user-visible message fields require scalar
  versus I18N classification and length contracts;
- action URL, locale, and timezone should use semantic predefined types;
- account subject ID, dedupe key, provider message ID, error code, and JSON
  payloads require identifier/opaque/structured-data classification.

### textus-user-account

- account status and session/credential lifecycle fields require
  powertype/statemachine classification;
- display title and profile-facing text require scalar versus I18N review;
- login name, email address, phone number, locale, timezone, client ID, and IP
  address should use parser-backed or constrained semantic types;
- external subject ID and session references require identifier semantics;
- password and token hashes require opaque storage, redaction, and strict
  length/format contracts rather than ordinary display text;
- suspension reason, device information, and user agent require explicit text
  range decisions.

## 8. Diagnostics

Cozy should diagnose likely modeling debt without blindly rewriting source.
Candidate diagnostics include:

- a string-only wrapper with no additional constraint or semantic metadata;
- a finite literal set modeled as unconstrained string;
- a lifecycle status modeled without its declared statemachine;
- a user-visible multilingual field collapsed to scalar string;
- an I18N field whose update path discards locale entries;
- text without a value-range decision where the selected predefined type has no
  default maximum;
- a secret or hash using a display-oriented text type.

Warnings become errors only when the accepted specification defines a
deterministic violation. Domain intent that cannot be inferred remains a review
item.

## 9. Executable Specification Matrix

Phase 16 should cover:

- predefined nonlocalized `name` normalization;
- predefined `title` single-locale construction, multi-locale codec,
  normalization, fallback, and entry preservation;
- accepted `text` and related family normalization;
- minimum, maximum, below-minimum, and above-maximum text lengths;
- per-locale I18N length validation;
- locale preservation and display fallback;
- duplicate and unsupported locale diagnostics;
- powertype field generation and invalid vocabulary values;
- statemachine-owned state and transition validation;
- opaque hash/token redaction;
- driver metadata, datastore, form, REST/OpenAPI, and Help projection.

## 10. Compatibility and Migration

Existing string-only wrappers remain readable during Phase 16. Migration is
explicit and evidence-driven; Cozy does not infer a powertype, statemachine, or
I18N contract from a class name alone.

For each driver type, the migration ledger records:

- current source and generated representation;
- selected semantic classification;
- value-range and localization contract;
- generated API/ABI and datastore impact;
- compatibility or versioning decision.
