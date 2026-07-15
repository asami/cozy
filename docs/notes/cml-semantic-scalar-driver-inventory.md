# CML Semantic Scalar Driver Inventory

status=provisional
updated_at=2026-07-15
target_phase=16

## 1. Purpose

This note is the current Phase 16 inventory and provisional classification of
string-only `VALUE` and `DATATYPE` declarations in the two development driver
CARs. It complements
`cml-semantic-scalar-modeling-spec-proposal.md`; that note defines the general
rules, while this note records their application to the current driver models.

The inventory is evidence for implementation planning, not an instruction to
rewrite the dirty driver worktrees immediately. Final migration decisions must
still define concrete value ranges, localization, ABI impact, and datastore
compatibility.

## 2. Extraction Contract

The inventory is extracted through `CmlModelInspection`, which loads each CML
source with `KaleidoxModel.load` and inspects the normalized `ValueModel` and
`DataTypeModel`. It does not scan headings, split Markdown tables, or reparse
rendered description-list text.

Baseline sources:

- `textus-user-notification` commit `d6ec49c`, including the preserved dirty
  `src/main/cozy/user-notification.cml` working-tree state, SHA-256
  `f79c065043ffc5e69ea081b5c08c0c4b0c3a04b5bbe2814d691c8546f53b6343`;
- `textus-user-account` commit `a3e1958`, including the preserved dirty
  `src/main/cozy/user-account.cml` working-tree state, SHA-256
  `3546f4afa5b63a0ac23e45833d7cdf6abd82f9b591e482ede244a177991be5ba`.

The 2026-07-15 AST inventory found:

| Driver | String-only VALUE | String-only DATATYPE | Total |
|---|---:|---:|---:|
| textus-user-notification | 0 | 19 | 19 |
| textus-user-account | 0 | 16 | 16 |
| Total | 0 | 35 | 35 |

All listed datatypes currently have one `value: string` constituent.

## 3. Classification Vocabulary

The provisional class column uses the Phase 16 categories:

- `predefined scalar`: an accepted CML semantic datatype should own the
  contract;
- `constrained domain scalar`: the driver owns narrower validation or
  normalization not covered by a predefined type;
- `powertype`: a finite vocabulary with no transition behavior;
- `statemachine-owned state`: lifecycle state whose valid transitions belong
  to the model;
- `intentional opaque text`: identity, secret, hash, token, provider payload,
  or technical text whose string representation is not display text.

No current item is provisionally classified as a composite `VALUE`. If later
analysis finds multiple meaningful fields or cross-field invariants, the item
must be redesigned as a composite Value rather than retained as a scalar
wrapper.

`Confirmed direction` means the structural category is sufficiently clear for
the next executable specification. `Domain decision` means the row has a
useful provisional category but still requires domain vocabulary, transition,
localization, or representation evidence before source migration.

Powertype/statemachine rows use the Phase 16 ownership rule: a closed selector
without model-owned transitions is a powertype, a lifecycle with model-owned
transitions is statemachine state, and an application/provider-extensible
registry remains an open identifier or constrained scalar. A type name alone
does not decide the category.

## 4. textus-user-notification

| Model concept | Classification | Localization | Decision state | Required follow-up |
|---|---|---|---|---|
| `UserNotificationAudienceKind` | powertype | nonlocalized | Implemented | Uses the closed `direct`, `multicast`, and `broadcast` vocabulary. |
| `UserNotificationAccountSubjectId` | constrained domain scalar | nonlocalized | Confirmed direction | Bind to the account subject identifier contract and length. |
| `UserNotificationAudienceQueryJson` | intentional opaque text | nonlocalized | Domain decision | Prefer structured `record`/JSON when the query schema is stable; otherwise constrain payload size. |
| `UserNotificationType` | powertype or constrained domain scalar | nonlocalized | Domain decision | Decide whether notification types are closed, versioned, or application-extensible. |
| `UserNotificationChannel` | powertype | nonlocalized | Implemented | Uses `in_app`, `email`, `sms`, and `push`; provider variability remains in the open provider contract. |
| `UserNotificationTitle` | predefined `title` | locale-aware | Implemented | Uses the single/multi-locale `I18nTitle` contract and catalog length constraints. |
| `UserNotificationBody` | predefined `text` | locale-aware | Implemented | Wrapper removed; notification body uses `I18nText` with 1..8192 characters per locale entry. |
| `UserNotificationPriority` | powertype | nonlocalized | Implemented | Uses the ordered `low`, `normal`, `high`, and `urgent` vocabulary. |
| `UserNotificationStatus` | statemachine-owned state | nonlocalized | Domain decision | Separate notification and delivery-attempt lifecycles if their transitions differ. |
| `UserNotificationDedupeKey` | intentional opaque text | nonlocalized | Confirmed direction | Define normalization, uniqueness scope, and maximum length. |
| `UserNotificationActionReference` | predefined `uri` | nonlocalized | Implemented | Relative application routes and absolute URIs share the parser-backed `java.net.URI` contract; deployment authorization remains separate. |
| `UserNotificationMetadataJson` | intentional opaque text | nonlocalized | Domain decision | Prefer structured `record`/JSON when stable; otherwise constrain payload size and exposure. |
| `UserNotificationDeliveryProvider` | constrained domain scalar | nonlocalized | Domain decision | Keep open when providers are extensible; use a powertype only for a closed registry. |
| `UserNotificationProviderMessageId` | intentional opaque text | nonlocalized | Confirmed direction | Define provider-scoped identity and maximum length. |
| `UserNotificationErrorCode` | constrained domain scalar | nonlocalized | Confirmed direction | Define provider/code namespace and syntax. |
| `UserNotificationErrorMessage` | constrained technical text | nonlocalized | Confirmed direction | Define bounded diagnostic length and confidentiality; do not localize provider text automatically. |
| `UserNotificationQuietHours` | predefined `localtime` | nonlocalized | Implemented | Entity and operation fields use parser-backed `LocalTime`; interval policy remains application-owned. |
| `UserNotificationLocale` | predefined locale scalar | nonlocalized | Implemented | Entity and operation fields use predefined `locale`; deployment policy still defines the allowed locale set. |
| `UserNotificationTimeZone` | predefined timezone scalar | nonlocalized | Implemented | Entity and operation fields use predefined `timezone`; deployment policy still defines the allowed zone set. |

The notification audience kind, channel, and priority are implemented as
generated powertypes. The primary unresolved notification decisions are
whether notification type is closed, how notification lifecycle transitions
are modeled, and the separate delivery-attempt result contract. Body uses the
canonical locale-aware `text` contract. Quiet-hour fields use the predefined
parser-backed `localtime` contract. Delivery provider remains open and
provider-extensible.

## 5. textus-user-account

| Model concept | Classification | Localization | Decision state | Required follow-up |
|---|---|---|---|---|
| `UserAccountTitle` | predefined `title` | locale-aware | Implemented | Uses the single/multi-locale `I18nTitle` contract and catalog length constraints. |
| `UserAccountEmailAddress` | predefined email scalar | nonlocalized | Implemented | Uses `EmailAddress` parsing, domain normalization, and catalog length constraints without conflating identity verification. |
| `UserAccountLoginName` | predefined `name` or constrained domain scalar | nonlocalized | Domain decision | Define case, allowed characters, uniqueness, and whether generic `Name` is sufficiently narrow. |
| `UserAccountExternalSubjectId` | intentional opaque text | nonlocalized | Confirmed direction | Define issuer scope, normalization policy, and maximum length. |
| `UserAccountPhoneNumber` | predefined phone scalar | nonlocalized | Implemented | Removes visual separators and requires canonical international E.164 identity. |
| `UserAccountLocale` | predefined locale scalar | nonlocalized | Implemented | Uses `Locale` and serializes its external/datastore form as a BCP 47 language tag; allowed-locale policy remains deployment-owned. |
| `UserAccountTimeZone` | predefined timezone scalar | nonlocalized | Implemented | Uses `TimeZone`, validates known identifiers, and serializes the canonical zone ID. |
| `UserAccountSuspendedBy` | intentional opaque identifier | nonlocalized | Confirmed direction | Bind to the actor/account identifier contract. |
| `UserAccountSuspensionReason` | constrained domain text | single-locale record text | Domain decision | Define audit-text length and whether translated display is a separate concept. |
| `UserAccountPasswordHash` | intentional opaque secret text | nonlocalized | Confirmed direction | Define algorithm-aware format/length, redaction, and no-display policy. |
| `UserAccountSessionReference` | intentional opaque text | nonlocalized | Confirmed direction | Define session identity scope, entropy assumptions, and length. |
| `UserAccountTokenHash` | intentional opaque secret text | nonlocalized | Confirmed direction | Define algorithm-aware format/length, redaction, and no-display policy. |
| `UserAccountClientId` | constrained domain identifier | nonlocalized | Confirmed direction | Define client namespace, syntax, and maximum length. |
| `UserAccountDeviceInfo` | constrained technical text or structured Value | nonlocalized | Domain decision | Decide whether bounded raw device text is sufficient or stable fields justify a Value. |
| `UserAccountIpAddress` | predefined IP address scalar | nonlocalized | Implemented | Uses `IpAddress` with IPv4/IPv6 parsing and canonical serialization. |
| `UserAccountUserAgent` | constrained technical text | nonlocalized | Confirmed direction | Define bounded length, control-character policy, and no-I18N behavior. |

The primary unresolved account decisions are login-name normalization,
suspension-reason localization, and whether device information remains bounded
technical text or becomes a structured Value. Hash/token redaction and actor,
session, and client identifier contracts also remain domain-specific work; they
must not be collapsed to a broader predefined scalar merely because their
current representation is one string.

## 6. Next Implementation Boundary

The first CML16-07 implementation slice established the executable predefined
catalog and migrated the low-ambiguity account scalars: `title`, `email`,
`phone`, `locale`, `timezone`, and `ip-address`. The notification audience,
channel, and priority vocabularies are now generated powertypes. Subsequent
slices should resolve lifecycle state and the remaining driver-specific
contracts. State-machine design, open provider/type registries, structured JSON
payloads, secret redaction, and account device information remain explicit
domain decisions.

Driver migration starts only after the selected type contracts define:

- runtime representation and normalization;
- minimum and maximum length or structured size;
- localization behavior;
- redaction/confidentiality behavior;
- datastore and API representation;
- compatibility and versioning impact.
