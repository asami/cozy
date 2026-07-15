# CML Notification Delivery Domain Scalar Boundary

Date: 2026-07-16

## Scope

This slice defines the canonical constraints for notification delivery values
whose representation is one nonlocalized string but whose domain meaning is
narrower than generic text.

## Decision

The notification driver keeps these values as CML-owned nominal Datatypes:

| Type | Contract |
|---|---|
| `UserNotificationDedupeKey` | application-scoped opaque value of 1..255 characters |
| `UserNotificationDeliveryProvider` | open provider key of 1..64 characters |
| `UserNotificationProviderMessageId` | opaque provider-scoped identity of 1..512 characters |
| `UserNotificationErrorCode` | provider/code value of 1..128 characters |
| `UserNotificationErrorMessage` | nonlocalized provider diagnostic of 1..4096 characters |

The delivery-provider registry remains extensible, so it is not modeled as a
powertype. Provider message IDs remain opaque because their internal syntax is
owned by each provider. Error messages are technical evidence, not translated
presentation text.

This slice does not invent syntax that is not yet owned by an application or
provider registry. Dedupe normalization and uniqueness scope, provider-key
canonicalization, and error-code namespace syntax remain follow-up decisions.

## Generated Boundary

The CML `min-length` and `max-length` constraints authored in this slice are
authoritative. Generated nominal scalar construction, Record decoding,
datastore input, and projected schemas use the same constraints. The driver
does not add parallel handwritten validation or Web-only length properties.

## Deferred Decisions

- `UserNotificationAccountSubjectId` must be aligned with the accepted account
  subject identity contract.
- `UserNotificationType` still needs an explicit open-versus-closed registry
  decision.
- audience query and metadata JSON move to structured Values in a separate
  slice.
- confidentiality-aware rendering of provider diagnostics remains a runtime
  policy concern.
