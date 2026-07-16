# CML Semantic Scalar Modeling Specification Proposal

status=proposal
updated_at=2026-07-16
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

For the v1 lint contract, a string-backed plain `DATATYPE` is an accepted
narrower domain scalar when its normalized value declaration contains at least
one typed domain constraint: `min-length`, `max-length`, `pattern`, or
`format`. The lint reads these constraints from the Kaleidox AST. A type name,
suffix, or prose description alone is not an exemption. Typed privacy,
redaction, normalization, and opaque-representation metadata can extend this
contract later; until such metadata exists, those cases remain review items.

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

Apply the boundary in this order:

1. If the model owns valid state changes, use a `STATEMACHINE`. Its states may
   look like a finite vocabulary, but transition semantics are the deciding
   contract.
2. Otherwise, if the accepted values form a closed vocabulary, use a
   `POWERTYPE`. Ordering and localized labels may be powertype metadata; they
   do not turn the selector into lifecycle state.
3. If values are registered by applications, providers, tenants, or future
   extensions, keep an open identifier or constrained domain scalar. Do not
   encode the currently observed registry as a closed powertype.
4. If an external system owns the lifecycle, model the observed external state
   as an external value unless this model also owns a local transition policy.

The class name `Status`, `Type`, `Kind`, or `Provider` is not enough to choose a
construct. Vocabulary closure and transition ownership are required evidence.

The implemented notification driver application fixes three closed selector
contracts:

- audience kind: `direct`, `multicast`, `broadcast`;
- channel: `in_app`, `email`, `sms`, `push`;
- priority: `low`, `normal`, `high`, `urgent`.

These are generated CML powertypes and reject undeclared values. The declared
snake_case name is their external and datastore representation. Channel
providers remain an open, independent contract, so adding a provider does not
extend the channel vocabulary. Notification type remains open pending its
registry decision.

The notification lifecycle uses the closed states `Queued`, `Sending`, `Sent`,
`Delivered`, `Failed`, and `Canceled` plus the `notificationLifecycle`
statemachine. It owns delivery progression and retry/cancel rules. Generated
transition rules retain the machine, state field, source state, target state,
and their storage values. CNCF compares current and proposed records at the
entity update boundary, permits only a declared transition, and leaves the
persisted record unchanged when a transition is rejected. Recipient read and
dismissal state is not a notification lifecycle transition and stays in
`NotificationUserState`. Delivery attempts use a separate closed
`UserNotificationDeliveryResultStatus` vocabulary with `Pending`, `Succeeded`,
and `Failed`; an attempt result must not be passed as a notification lifecycle
state.

## 4. Text Semantic Axis

Raw `string` is the least precise text contract. CML should expose semantic
predefined types where the runtime contract exists or can be established.

The existing baseline is:

- `name`: stable nonlocalized `Name`;
- `title`: one locale-aware `I18nTitle` that stores either a single locale
  entry or multiple locale entries.

`simplemodeling-lib` executable specifications fix both sides of this
baseline. `NameSpec` accepts lengths 1 through 256, rejects values outside
that range through `Consequence`, and preserves the nonlocalized source value.
`I18nTitleSpec` covers single- and multi-locale behavior separately.

This is intentionally asymmetric. CML does not need a scalar and I18N version
of every semantic text type. The type's domain meaning determines whether it is
localized.

The nonlocalized `Text` runtime remains historical runtime evidence and is not
the CML `text` contract. Canonical CML `text` resolves to locale-aware
`I18nText`, accepts plain input as one root-locale entry, accepts structured
multi-locale input through the shared `I18nString` codec, and applies a default
length range of 1 through 8192 independently to every locale entry.

Consequently, model authors normally select meaning (`title`, `label`,
`description`, `message`, or another accepted display-text type), not storage
cardinality. The generator supplies the locale-aware structure. Explicit
nonlocalized types remain appropriate for stable names, identifiers, protocol
values, hashes, tokens, and other text whose identity must not vary by locale.

The semantic text family is classified by role rather than storage shape:

| CML meaning | Runtime baseline | Attribute integration | Phase 16 status |
|---|---|---|---|
| stable `name` | `Name` | `NameAttributes.name` | Accepted nonlocalized baseline |
| display `label` | `I18nLabel` | `DescriptiveAttributes.tooltip` uses the same compact-label family | Accepted locale-aware runtime baseline; range open |
| `title` | `I18nTitle` | `NameAttributes.title` | Accepted locale-aware runtime baseline; range open |
| `headline`, `brief` | `I18nBrief` | `DescriptiveAttributes.headline` and `brief` | Accepted shared runtime family; role-specific ranges open |
| `summary`, `lead`, `abstract`, `remarks` | `I18nSummary` | Corresponding `DescriptiveAttributes` fields | Accepted shared runtime family; role-specific ranges open |
| `description` | `I18nDescription` | `DescriptiveAttributes.description` | Accepted locale-aware runtime baseline; range open |
| plain narrative `text` | `I18nText` | Not a `ContentBody` replacement | Implemented locale-aware predefined type; 1..8192 per locale entry |
| user-facing `message` | `I18nMessage` legacy behavior | No canonical `DescriptiveAttributes` field | Legacy runtime evidence only; canonical codec and range open |
| document body | `ContentBody` | `ContentAttributes.content` | Accepted single-document-body boundary |

`DescriptiveAttributes` is the integration contract for descriptive metadata.
Its field types preserve the complete locale-tagged values. Its `effective*`
methods select a display fallback as `I18nString`; they do not replace one
stored field with another and do not make the roles interchangeable. For
example, summary may provide an effective description when description is
absent, but summary and description may still receive different accepted
length constraints.

Other parser-backed families such as `identifier`, `url`, `uri`, `locale`, and
`timezone` remain non-generic scalar concepts rather than members of this
descriptive-text matrix.

The implemented low-ambiguity account catalog additionally fixes these
provider-independent runtime boundaries:

- `email` uses `EmailAddress`, preserves the local part, and normalizes the
  domain;
- `phone` uses `PhoneNumber`, removes visual separators, normalizes an explicit
  `00` international prefix, and requires an E.164 country code;
- `locale` uses `java.util.Locale` and serializes as a BCP 47 language tag;
- `timezone` uses `java.util.TimeZone` and serializes as its canonical ID;
- `ip-address` uses `IpAddress` and canonical IPv4/IPv6 serialization.

These types are nonlocalized semantic scalars. They do not participate in
`DescriptiveAttributes` fallback and they do not imply account verification,
allowed-locale, or deployment-policy decisions.

The notification driver also uses predefined `localtime` directly for quiet
hours. Generated Scala uses `java.time.LocalTime`; external and datastore forms
use ISO local-time text, and malformed clock values are rejected at generated
input construction. The pair defines a same-day or midnight-crossing interval
in the separately declared preference time zone. Interval completeness and
daylight-saving policy remain domain/application decisions rather than parser
behavior.

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

These baseline behaviors are executable in `simplemodeling-lib`
`I18nTitleSpec`: plain construction keeps one locale entry, structured codec
round-trips every locale entry, and display fallback does not mutate the
stored multilingual value.

The underlying `I18nStringSpec` fixes the shared codec boundary independently
of `title`: plain decoding creates one execution-locale entry, structured JSON
preserves all ordered locale/value entries, display fallback leaves storage
unchanged, and a leading brace in plain text is escaped rather than parsed as
JSON. Duplicate-locale acceptance and normalization remain catalog decisions.

`I18nMessage` is currently a legacy exception to that shared wrapper model. It
owns `NonEmptyVector[(Locale, String)]` directly, has no `I18nString` codec,
and selects display text through a fixed root, English, Japanese preference
before falling back to the first entry. Phase 16 records this behavior for
compatibility analysis but does not accept it as the canonical CML `message`
contract.

`I18nLabel` follows the shared wrapper model: plain construction stores one
root-locale entry, plain encoding remains concise, and structured encoding
round-trips all locale/value entries through `I18nString`. Existing schema and
model metadata already use this type for labels. Phase 16 therefore accepts it
as the runtime baseline for locale-aware labels, while CML field-type exposure,
length, and normalization policy remain open.

`I18nDescription` also follows the shared wrapper model. Plain construction
stores one root-locale entry, structured encoding round-trips every ordered
locale/value entry, and `DescriptiveAttributes` selects effective description
text through the preserved `I18nString` value without collapsing its entries.
The current runtime wrapper does not define a description-specific length,
multiline, normalization, or empty-value policy. Phase 16 accepts this behavior
as runtime evidence, not yet as the canonical CML `description` contract.

`I18nBrief` follows the same shared wrapper model and currently backs both the
`headline` and `brief` fields in `DescriptiveAttributes`. Plain construction and
structured round-trip preserve its `I18nString`, while effective headline and
brief accessors select display locales without collapsing stored entries. The
runtime wrapper does not yet distinguish headline and brief ranges or define
their length, normalization, or empty-value policy, so this remains baseline
evidence rather than the accepted CML field contract.

`I18nSummary` is the shared runtime wrapper for the `summary`, `lead`,
`abstract`, and `remarks` fields in `DescriptiveAttributes`. Its plain and
structured forms preserve `I18nString`, and effective summary fallback can
select a localized `lead` without collapsing the stored entries. The wrapper
does not currently distinguish the ranges or normalization rules of these four
roles. Their canonical CML classification and constraints therefore remain
open even though the runtime baseline is shared.

`I18nText` also follows the shared wrapper model. Plain construction keeps one
root-locale entry, and structured encoding round-trips every ordered locale
entry. It is the runtime baseline for localized plain narrative text, not for a
rich document body. CNCF SD-01B already fixes `ContentBody` as one document
body and reserves rich multilingual bodies for the future SmartDox Textus
profile. The existing `I18nText` to `ContentBody` conversion selects one
`displayMessage`; it is a display projection or compatibility input, not a
canonical multilingual storage contract. A CML field that owns localized plain
text may use the `I18nText` family, while Blog, article, and document body fields
remain `ContentBody`.

`text` is now a canonical CML predefined type. The remaining family decisions
concern `message` and domain-specific text contracts, not the storage shape of
plain narrative text.

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

The implemented boundary representations are intentionally distinct:

- API `Record` values use either a plain string input or a locale map such as
  `{ "ja": "通知", "en": "Notification" }`; generated `toRecord` preserves
  every locale instead of applying `displayMessage`;
- datastore values use the shared `StringCodex` storage form, including the
  ordered `entries` representation when more than one locale must be stored;
- display and template logic reads the typed I18N value and calls
  `displayMessage` with the current `ExecutionContext` locale explicitly;
- generated entity `toDataStore` applies the datastore conversion to
  `NameAttributes` and `DescriptiveAttributes` fields as well as direct model
  fields, so title, headline, summary, and description cannot be collapsed by
  a generic external-value conversion.

The API locale map and datastore codec are structural contracts. `Record.getString`
is not a valid way to read them for presentation because it stringifies the
container rather than selecting an effective locale.

## 6. Text Value Range

Length is part of the value range for scalar and I18N text. Phase 16 uses the
canonical domain constraints `min-length` and `max-length`, rather than relying
on numeric `min` and `max` interpretation. The normalized model may use
`min_length` and `max_length` internally, but CML authoring uses the hyphenated
property names.

Additional constraints may include:

- `pattern` for a domain syntax;
- `format` for a standard parser-backed format;
- whitespace normalization;
- case normalization where identity semantics require it;
- prohibited control characters;
- redaction or secret-display policy.

For an I18N value, minimum and maximum length apply to each locale entry. Entry
count and locale-set constraints are separate from text length.

Web validation is a projection of the domain constraint. `MAttribute.Web`
owns presentation and input-control metadata such as label, control type,
placeholder, help, required, hidden, and readonly. It does not contribute
validation constraints to `MAttribute.constraints`. In particular,
`web-min-length`, `web-max-length`, and `web-pattern` are not canonical model
properties and are not parsed as compatibility aliases. Generated
`WebValidationHints` are derived from the normalized domain constraints.

The normalized AST and model metadata must retain typed constraints. Generator
and runtime boundaries must project them consistently to:

- Scala construction and validation;
- datastore column or structured-value schema;
- generated forms and validation messages;
- automatic REST and OpenAPI schema;
- generated Help and manual metadata.

## 7. Driver Inventory Direction

The complete AST-backed inventory and current per-item classification are
maintained in `cml-semantic-scalar-driver-inventory.md`. It records the original
semantic-scalar concepts together with their implemented or unresolved state.
The following are the remaining investigation themes, not accepted migrations.

### textus-user-notification

- notification type and delivery provider require a closed-versus-extensible
  vocabulary decision;
- error message and other remaining user-visible message fields require scalar
  versus I18N classification and length contracts;
- account subject ID, dedupe key, provider message ID, and error code require
  identifier or opaque-data classification.

Audience kind, channel, priority, lifecycle state, and delivery-attempt result
use generated closed types. `notificationLifecycle` provides the transition
contract for lifecycle state and CNCF enforces that contract at entity update.
Notification title uses predefined `title` and body uses predefined `text`;
quiet-hour, locale, timezone, and
action-reference fields use predefined `localtime`, `locale`, `timezone`, and
`uri` contracts respectively. An action reference is a URI rather than an
absolute-only URL because application-relative routes are part of the
notification contract. Audience queries and metadata are structured `record`
fields. Generated and REST requests must supply Records; an explicit Web
`json` control is the presentation adapter that decodes a JSON object before
operation dispatch.

### textus-user-account

The low-ambiguity migration is implemented for display title, email address,
phone number, locale, timezone, and IP address. Generated Create, Update,
datastore, and operation paths use the semantic runtime types, and external
locale/timezone values use canonical string forms. The remaining bullets are
domain decisions rather than incomplete aliases for those implemented types.

- account status uses the generated `UserAccountStatus` powertype and the CML
  `status` state machine; entity, create/update/list Values, datastore values,
  and transition rules share the same contract;
- access and refresh sessions use issue, expiry, revocation, and rotation
  timestamps rather than a finite string state, so no session-state powertype
  is inferred;
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

- a string-only wrapper with no normalized domain constraint;
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

A constrained nominal scalar is not warned solely because its representation
is string-backed. A predefined-scalar replacement is an error only when the
wrapper has no narrower normalized constraint contract.

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

## 10. Breaking Migration

Phase 16 does not retain compatibility aliases for removed string-only wrappers.
Migration is explicit and evidence-driven; Cozy does not infer a powertype,
statemachine, or I18N contract from a class name alone.

For each driver type, the migration ledger records:

- current source and generated representation;
- selected semantic classification;
- value-range and localization contract;
- generated API/ABI and datastore impact;
- compatibility or versioning decision.
