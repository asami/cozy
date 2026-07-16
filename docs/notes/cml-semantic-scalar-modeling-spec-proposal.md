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
extend the channel vocabulary. Notification type is an open
application-extensible registry key with a 1..255 boundary, so it remains a
constrained nominal scalar rather than a closed powertype.

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
| stable `name` | `Name` | `NameAttributes.name` | Accepted nonlocalized 1..256 contract |
| display `label` | `I18nLabel` | `DescriptiveAttributes.tooltip` uses the same compact-label family | Accepted locale-aware 1..256 contract |
| `title` | `I18nTitle` | `NameAttributes.title` | Accepted locale-aware 1..256 contract |
| `headline`, `brief` | `I18nBrief` | `DescriptiveAttributes.headline` and `brief` | Accepted locale-aware 1..512 contracts |
| `summary`, `lead`, `abstract`, `remarks` | `I18nSummary` | Corresponding `DescriptiveAttributes` fields | Accepted locale-aware 1..2048 contracts |
| `description` | `I18nDescription` | `DescriptiveAttributes.description` | Accepted locale-aware 1..8192 contract |
| plain narrative `text` | `I18nText` | Not a `ContentBody` replacement | Implemented locale-aware predefined type; 1..8192 per locale entry |
| user-facing `message` | `I18nMessage` legacy behavior | No canonical `DescriptiveAttributes` field | Legacy runtime evidence only; canonical codec and range open |
| document body | `ContentBody` | `ContentAttributes.content` | Accepted single-document-body boundary; implicit `I18nText` display conversion removed |

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

Opaque text is also outside the descriptive-text matrix. Its domain type owns
identity and length constraints, while CML `confidentiality` owns disclosure
policy. Hash, credential, and token values therefore do not enter
`DescriptiveAttributes` and never use `effective*` display fallback. The
accepted v1 policy is:

| Confidentiality | Generic display and diagnostics | Input control |
|---|---|---|
| `public` | visible | datatype-derived |
| `internal` | visible by default; authorization remains surface-owned | datatype-derived |
| `personal` | redacted by default | datatype-derived |
| `sensitive` | redacted by default | datatype-derived |
| `secret` | redacted by default and never emitted as a generic value preview | password-style |

This policy is metadata-driven. Naming a field `token` or backing a Datatype
with `string` is not the canonical classification mechanism. Name heuristics
may remain defensive protection, but generated CML confidentiality is the
source of truth. User Account `passwordHash` and `tokenHash` fields are the
Phase 16 driver evidence.

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

The accepted catalog locality matrix is complete rather than inferred from the
runtime representation:

| Locality | Accepted CML concepts | Reason |
|---|---|---|
| locale-aware | `label`, `title`, `headline`, `brief`, `summary`, `lead`, `abstract`, `remarks`, `description`, `text` | User-visible descriptive or narrative meaning may vary by locale. A plain value remains the concise one-entry input form. |
| nonlocalized | `name`, `identifier`, `token`, `url`, `uri`, `urn`, `locale`, `timezone`, `ip-address`, `email`, `phone` | Identity, protocol, locator, locale selector, and parser-backed technical meaning must not change with display locale. |

Driver-owned nominal scalars follow the same semantic rule. Stable subject and
message identifiers, registry keys, powertype/state values, hashes, secrets,
session references, client IDs, device/user-agent diagnostics, and provider
codes are intentionally nonlocalized. A localized label for one of those
values is separate presentation metadata; it does not change the stored
identity. Notification title/body and account title use the accepted
locale-aware families. Audit or provider diagnostic text is not localized
merely because a human may read it: localization requires authored alternative
locale values, while source diagnostics must preserve the provider's exact
text.

`locale` itself is nonlocalized. It identifies the locale used to interpret or
select other values; it is not text translated into that locale. The same
distinction applies to `timezone` and other execution or presentation policy
selectors.

This is the natural-I18N rule:

- a user-visible semantic text type is locale-aware unless its accepted type
  contract explicitly says otherwise;
- a direct plain CML scalar literal is one language-neutral entry, while a
  context-aware external decode uses the active execution locale;
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
JSON. Locale identity is a canonical BCP 47 tag, malformed tags and duplicate
canonical identities are rejected, and optional execution-context locale
restrictions are applied without changing the stored value.

`I18nMessage` is currently a legacy exception to that shared wrapper model. It
owns `NonEmptyVector[(Locale, String)]` directly, has no `I18nString` codec,
and selects display text through a fixed root, English, Japanese preference
before falling back to the first entry. Phase 16 records this behavior for
compatibility analysis but does not accept it as the canonical CML `message`
contract.

`I18nLabel` follows the shared wrapper model: plain construction stores one
root-locale entry, plain encoding remains concise, and structured encoding
round-trips all locale/value entries through `I18nString`. Existing schema and
model metadata already use this type for labels. Canonical CML `label` preserves
the authored text and applies 1..256 independently to each locale entry.

`I18nDescription` also follows the shared wrapper model. Plain construction
stores one root-locale entry, structured encoding round-trips every ordered
locale/value entry, and `DescriptiveAttributes` selects effective description
text through the preserved `I18nString` value without collapsing its entries.
Canonical CML `description` preserves authored multiline text and applies
1..8192 independently to each locale entry.

`I18nBrief` follows the same shared wrapper model and currently backs both the
`headline` and `brief` fields in `DescriptiveAttributes`. Plain construction and
structured round-trip preserve its `I18nString`, while effective headline and
brief accessors select display locales without collapsing stored entries. The
canonical `headline` and `brief` roles share the wrapper and each apply a
1..512 per-locale range without implicit text normalization.

`I18nSummary` is the shared runtime wrapper for the `summary`, `lead`,
`abstract`, and `remarks` fields in `DescriptiveAttributes`. Its plain and
structured forms preserve `I18nString`, and effective summary fallback can
select a localized `lead` without collapsing the stored entries. The wrapper
is shared, while canonical `summary`, `lead`, `abstract`, and `remarks` each
apply the same 1..2048 per-locale range and preserve the authored text.

`I18nText` also follows the shared wrapper model. Plain construction keeps one
root-locale entry, and structured encoding round-trips every ordered locale
entry. It is the runtime baseline for localized plain narrative text, not for a
rich document body. CNCF SD-01B already fixes `ContentBody` as one document
body and reserves rich multilingual bodies for the future SmartDox Textus
profile. `I18nText` is not accepted by `ContentBody` ValueReader or Builder
boundaries because selecting one display locale would discard authored locale
entries. A CML field that owns localized plain text may use the `I18nText`
family, while Blog, article, and document body fields remain `ContentBody`.

`text` is now a canonical CML predefined type. The remaining family decisions
concern `message` and domain-specific text contracts, not the storage shape of
plain narrative text.

Stable symbolic names, login names, identifiers, hashes, and protocol tokens
are normally nonlocalized. User-visible labels, titles, descriptions,
messages, and narrative body text may be localized when the owning domain
requires it. A field is not made I18N solely because it is displayed in a UI;
the domain must own the translations.

The accepted locale policy is:

- locale identity is a well-formed BCP 47 tag, canonicalized and serialized by
  `Locale.toLanguageTag`; `und` represents language-neutral `Locale.ROOT`;
- a direct untagged constructor creates one `Locale.ROOT` entry, while
  context-aware string decoding binds an untagged value to
  `ExecutionContext.locale`;
- no allowed-locale set means all well-formed locale identities are accepted;
  `I18nContext.allowedLocales` supplies an optional exact canonical set, and a
  value containing any nonmember is invalid;
- duplicate canonical locale identities are invalid at constructor, JSON, and
  Record boundaries, so display lookup cannot silently apply last-value-wins;
- display fallback checks requested exact locales, requested language-only
  locales, `Locale.ROOT`, English, Japanese, and finally the first authored
  entry, in that order;
- update and serialization preserve all ordered entries, and text constraints
  apply independently to each entry.

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
- generated execution-context-aware constructors use
  `ValueReader.readContextC`/`Record.getAsContextC`; context-free `readC`
  remains the deterministic storage and local-construction route.

The API locale map and datastore codec are structural contracts. `Record.getString`
is not a valid way to read them for presentation because it stringifies the
container rather than selecting an effective locale.

## 6. Text Value Range

Length is part of the value range for scalar and I18N text. Phase 16 uses the
canonical domain constraints `min-length` and `max-length`, rather than relying
on numeric `min` and `max` interpretation. The normalized model may use
`min_length` and `max_length` internally, but CML authoring uses the hyphenated
property names.

Narrower domain scalars may additionally declare:

- `pattern` for a domain syntax;
- `format` for a standard parser-backed format;
- whitespace normalization;
- case normalization where identity semantics require it;
- prohibited control characters;
- redaction or secret-display policy.

For an I18N value, minimum and maximum length apply to each locale entry. Entry
count and locale-set constraints are separate from text length.

The accepted semantic text defaults are:

| CML role | Present-value range | Contract kind | Normalization |
|---|---:|---|---|
| `name` | 1..256 | Runtime-required by `Name` | Preserve authored case, whitespace, and Unicode representation; reject non-printable control characters through `Name`. |
| `label`, `title` | 1..256 per locale entry | Catalog default | Preserve authored text exactly. |
| `headline`, `brief` | 1..512 per locale entry | Catalog default | Preserve authored text exactly. |
| `summary`, `lead`, `abstract`, `remarks` | 1..2048 per locale entry | Catalog default | Preserve authored text exactly. |
| `description`, `text` | 1..8192 per locale entry | Catalog default | Preserve authored text, including multiline representation, exactly. |

`min-length=1` governs a value that is present; field multiplicity separately
decides whether the field may be absent. An optional field may be absent but an
authored empty string is not a substitute for absence. The generator applies
catalog ranges as domain constraints at construction, Record/datastore, and
operation boundaries. Runtime wrappers remain lossless value containers and do
not silently trim, fold case, or apply Unicode normalization before that
validation. Explicit model constraints replace the corresponding catalog
default when the domain requires a different range. Runtime-owned invariants
such as the `Name` range remain authoritative and cannot be widened by
metadata.

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
locale/timezone values use canonical string forms. The remaining policy work
is narrower than the original classification inventory:

- account status uses the generated `UserAccountStatus` powertype and the CML
  `status` state machine; entity, create/update/list Values, datastore values,
  and transition rules share the same contract;
- access and refresh sessions use issue, expiry, revocation, and rotation
  timestamps rather than a finite string state, so no session-state powertype
  is inferred;
- display title is locale-aware `title`; login name is an exact case-sensitive
  constrained identity, and email, phone, locale, timezone, and IP address use
  parser-backed predefined types;
- external subject, actor, session, client, audit, device, and user-agent
  nominal scalars preserve authored text and enforce their recorded CML length
  boundaries;
- password and token hashes have opaque bounded storage contracts, while their
  CML `secret` confidentiality drives password controls and generic diagnostic,
  observability, Help, and Web redaction.

Policy beyond the implemented Phase 16 baseline is tracked in
`docs/notes/cml-post-phase-16-policy-backlog.md`.

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

Current executable coverage fixes:

- exact-preservation normalization for nonlocalized `name` and every accepted
  locale-aware text wrapper;
- `title` and related families across plain construction, multi-locale codec,
  fallback, and entry preservation;
- catalog default lengths plus minimum, maximum, below-minimum, and
  above-maximum generated validation;
- per-locale I18N length validation and locale preservation;
- powertype values, statemachine transitions, and driver metadata across
  datastore, form, REST/OpenAPI, and Help projections.

Remaining executable coverage belongs to the opaque secret policy: hash/token
redaction. Locale identity, duplicate diagnostics, explicit allowed-set
validation, default binding, and complete fallback order are executable.

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
