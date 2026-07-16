# CML Semantic Scalar Driver Inventory

status=provisional
updated_at=2026-07-16
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
| `Notification.audienceQuery` | structured `record` | nonlocalized | Implemented | Replaces `UserNotificationAudienceQueryJson`; generated and REST boundaries require a Record, while the explicit Web `json` control decodes one JSON object before dispatch. |
| `UserNotificationType` | constrained domain scalar | nonlocalized | Implemented | Uses an open application-extensible 1..255 type key; values such as `cncf.job` and `artscene.exhibition.new` are owned outside this component and are therefore not a powertype. |
| `UserNotificationChannel` | powertype | nonlocalized | Implemented | Uses `in_app`, `email`, `sms`, and `push`; provider variability remains in the open provider contract. |
| `UserNotificationTitle` | predefined `title` | locale-aware | Implemented | Uses the single/multi-locale `I18nTitle` contract and catalog length constraints. |
| `UserNotificationBody` | predefined `text` | locale-aware | Implemented | Wrapper removed; notification body uses `I18nText` with 1..8192 characters per locale entry. |
| `UserNotificationPriority` | powertype | nonlocalized | Implemented | Uses the ordered `low`, `normal`, `high`, and `urgent` vocabulary. |
| `UserNotificationStatus` | statemachine-owned state | nonlocalized | Implemented | Uses the `notificationLifecycle` transition contract; recipient read/dismiss state remains separate. |
| `UserNotificationDeliveryResultStatus` | powertype | nonlocalized | Implemented | Uses the independent closed `Pending`, `Succeeded`, and `Failed` attempt-result vocabulary. |
| `UserNotificationDedupeKey` | intentional opaque text | nonlocalized | Implemented length boundary | Uses a 1..255 application-scoped opaque contract; normalization and uniqueness scope remain application-owned. |
| `UserNotificationActionReference` | predefined `uri` | nonlocalized | Implemented | Relative application routes and absolute URIs share the parser-backed `java.net.URI` contract; deployment authorization remains separate. |
| `Notification.metadata` | structured `record` | nonlocalized | Implemented | Replaces `UserNotificationMetadataJson`; metadata remains structurally typed through generated, provider, datastore, REST, and Web form boundaries. |
| `UserNotificationDeliveryProvider` | constrained domain scalar | nonlocalized | Implemented length boundary | Remains an open 1..64 provider key rather than a closed powertype; canonical syntax remains a registry decision. |
| `UserNotificationProviderMessageId` | intentional opaque text | nonlocalized | Implemented | Uses a provider-scoped opaque 1..512 identity contract. |
| `UserNotificationErrorCode` | constrained domain scalar | nonlocalized | Implemented length boundary | Uses a 1..128 provider/code boundary; canonical namespace syntax remains a provider-contract decision. |
| `UserNotificationErrorMessage` | constrained technical text | nonlocalized | Implemented | Uses a nonlocalized 1..4096 diagnostic boundary; provider text is not translated automatically. |
| `UserNotificationQuietHours` | predefined `localtime` | nonlocalized | Implemented | Entity and operation fields use parser-backed `LocalTime`; interval policy remains application-owned. |
| `UserNotificationLocale` | predefined locale scalar | nonlocalized | Implemented | Entity and operation fields use predefined `locale`; deployment policy still defines the allowed locale set. |
| `UserNotificationTimeZone` | predefined timezone scalar | nonlocalized | Implemented | Entity and operation fields use predefined `timezone`; deployment policy still defines the allowed zone set. |

The notification audience kind, channel, priority, lifecycle states, and
delivery-attempt outcomes are implemented as generated closed vocabularies.
`notificationLifecycle` owns the allowed delivery transitions, while
`NotificationUserState` independently owns recipient read and dismissal data.
Notification type is an open application-extensible key rather than a closed
powertype; this component preserves the authored value and enforces only the
accepted 1..255 boundary. Audience matching and metadata use structured
Records rather than JSON-in-string wrappers. Body uses the
canonical locale-aware `text` contract. Quiet-hour fields use the predefined
parser-backed `localtime` contract. Delivery provider remains open and
provider-extensible, with provider-facing identifiers and diagnostics bounded
by canonical CML constraints. The seven remaining domain-scalar rows are CML-owned and
generate their nominal Scala types; no parallel handwritten wrapper source is
kept in the notification component.

## 5. textus-user-account

| Model concept | Classification | Localization | Decision state | Required follow-up |
|---|---|---|---|---|
| `UserAccountTitle` | predefined `title` | locale-aware | Implemented | Uses the single/multi-locale `I18nTitle` contract and catalog length constraints. |
| `UserAccountEmailAddress` | predefined email scalar | nonlocalized | Implemented | Uses `EmailAddress` parsing, domain normalization, and catalog length constraints without conflating identity verification. |
| `UserAccountLoginName` | constrained domain scalar | nonlocalized | Implemented | Uses an exact case-sensitive 1..255 account identity with no implicit normalization or lexical restriction; registration enforces exact-value uniqueness and lookup uses exact equality, so generic display-oriented `Name` is not used. |
| `UserAccountExternalSubjectId` | intentional opaque text | nonlocalized | Implemented length boundary | Uses a 1..512 issuer-scoped opaque boundary; issuer binding and normalization remain domain policy. |
| `UserAccountPhoneNumber` | predefined phone scalar | nonlocalized | Implemented | Removes visual separators and requires canonical international E.164 identity. |
| `UserAccountLocale` | predefined locale scalar | nonlocalized | Implemented | Uses `Locale` and serializes its external/datastore form as a BCP 47 language tag; allowed-locale policy remains deployment-owned. |
| `UserAccountTimeZone` | predefined timezone scalar | nonlocalized | Implemented | Uses `TimeZone`, validates known identifiers, and serializes the canonical zone ID. |
| `UserAccountSuspendedBy` | intentional opaque identifier | nonlocalized | Implemented length boundary | Uses a 1..255 actor reference boundary; binding to the actor/account identifier contract remains domain policy. |
| `UserAccountSuspensionReason` | constrained domain text | single-locale record text | Implemented length boundary | Entity and status-update fields share a 1..4096 audit-text boundary; translated display remains a separate decision. |
| `UserAccountPasswordHash` | intentional opaque secret text | nonlocalized | Implemented length boundary | Uses a 1..1024 algorithm-owned hash boundary; format, redaction, and no-display policy remain separate. |
| `UserAccountSessionReference` | intentional opaque text | nonlocalized | Implemented length boundary | Uses a 1..255 session reference boundary; identity scope and entropy remain domain policy. |
| `UserAccountTokenHash` | intentional opaque secret text | nonlocalized | Implemented length boundary | Uses a 1..1024 algorithm-owned hash boundary; format, redaction, and no-display policy remain separate. |
| `UserAccountClientId` | constrained domain identifier | nonlocalized | Implemented length boundary | Uses a 1..255 client identifier boundary; namespace and syntax remain registry policy. |
| `UserAccountDeviceInfo` | constrained technical text | nonlocalized | Implemented | Uses an opaque 1..4096 descriptor copied from the SecurityContext into access and refresh sessions; no stable component-owned subfields justify a structured Value. |
| `UserAccountIpAddress` | predefined IP address scalar | nonlocalized | Implemented | Uses `IpAddress` with IPv4/IPv6 parsing and canonical serialization. |
| `UserAccountUserAgent` | constrained technical text | nonlocalized | Implemented length boundary | Uses a nonlocalized 1..4096 technical-text boundary; control-character policy remains follow-up work. |
| `UserAccountStatus` | statemachine-owned state | nonlocalized | Implemented | Uses the closed `provisional`, `registered`, `formal`, and `suspended` vocabulary plus the CML `status` transition contract. |

Login name is an exact case-sensitive account identity with no implicit
normalization; registration enforces exact-value uniqueness and lookup uses the
same equality contract. Device information remains one bounded technical
descriptor because the component receives and returns it as an opaque
SecurityContext/session attribute rather than owning stable structured fields.
Persisted identity, audit, hash, session, client, device, and user-agent
scalars now have canonical CML length boundaries. Their issuer and
namespace binding, hash/token redaction, suspension-reason presentation,
control-character policy, and identity semantics remain domain-specific work;
they must not be collapsed to a broader predefined scalar merely because their
current representation is one string. Access and refresh sessions express
their lifecycle with issue, expiry, revocation, and rotation timestamps rather
than a finite string status, so this inventory does not invent a session-state
powertype. The ten remaining domain-scalar rows are likewise CML-owned and
generate their nominal Scala types; no parallel handwritten wrapper source is
kept in the account component.

Request-only authentication material is now modeled separately from those
persisted domain scalars. Plain passwords use predefined `password` with a
1..1024 request boundary. Login identifiers, challenge IDs, verification
codes, and proof/reset/refresh values use predefined `token` with role-specific
maximums from 255 through 8192. Login identifiers use `token` because the
command accepts either a login name or an email address, which is broader than
the lexical `identifier` contract. These values remain nonlocalized and secret
where authored; password-strength policy and redaction remain separate
responsibilities.

## 6. Next Implementation Boundary

The first CML16-07 implementation slice established the executable predefined
catalog and migrated the low-ambiguity account scalars: `title`, `email`,
`phone`, `locale`, `timezone`, and `ip-address`. The notification audience,
channel, and priority vocabularies and the account status vocabulary are now
generated powertypes. Notification and account lifecycle transitions are
generated from their CML state machines. Subsequent slices should resolve the
remaining driver-specific contracts. Open provider registry syntax and secret
redaction remain explicit domain decisions.

Driver migration starts only after the selected type contracts define:

- runtime representation and normalization;
- minimum and maximum length or structured size;
- localization behavior;
- redaction/confidentiality behavior;
- datastore and API representation;
- compatibility and versioning impact.
