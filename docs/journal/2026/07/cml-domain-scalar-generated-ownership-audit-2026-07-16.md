# CML Domain Scalar Generated Ownership Audit

Date: 2026-07-16

## Scope

This audit verifies the Phase 16 ownership boundary for domain-specific scalar
wrappers in `textus-user-notification` and `textus-user-account`.

## Notification Evidence

The accepted inventory retains nine notification domain scalars:

- `UserNotificationAccountSubjectId`
- `UserNotificationAudienceQueryJson`
- `UserNotificationType`
- `UserNotificationDedupeKey`
- `UserNotificationMetadataJson`
- `UserNotificationDeliveryProvider`
- `UserNotificationProviderMessageId`
- `UserNotificationErrorCode`
- `UserNotificationErrorMessage`

Each type is declared in the CML DATATYPE section and generated under
`target/scala-3.3.8/src_managed/main/org/simplemodeling/textus/usernotification/datatype`.
The handwritten source tree contains only component logic and no scalar-wrapper
implementation.

## Account Evidence

The accepted inventory retains ten account domain scalars:

- `UserAccountLoginName`
- `UserAccountExternalSubjectId`
- `UserAccountSuspendedBy`
- `UserAccountSuspensionReason`
- `UserAccountPasswordHash`
- `UserAccountSessionReference`
- `UserAccountTokenHash`
- `UserAccountClientId`
- `UserAccountDeviceInfo`
- `UserAccountUserAgent`

Each type is declared in the CML DATATYPE section and generated under
`target/scala-3.3.8/src_managed/main/org/simplemodeling/textus/useraccount/datatype`.
No corresponding handwritten wrapper remains under `src/main/scala`.

## Decision

CML is the sole source of truth for these nominal scalar types. Subsequent
normalization, length, redaction, or structured-data decisions modify the CML
contract and its generator behavior. They must not introduce parallel
handwritten wrappers.
