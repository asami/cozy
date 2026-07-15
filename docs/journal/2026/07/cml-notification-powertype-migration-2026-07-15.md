# CML Notification Powertype Migration

date=2026-07-15
phase=16
status=implemented

## Context

`textus-user-notification` modeled audience kind, delivery channel, and
priority as nominal string-backed Datatypes. These fields are selectors with
closed vocabularies and no model-owned transition history, so the Phase 16
powertype/statemachine boundary classifies them as powertypes.

## Decision

The notification model uses these canonical vocabularies:

- `UserNotificationAudienceKind`: `direct`, `multicast`, `broadcast`;
- `UserNotificationChannel`: `in_app`, `email`, `sms`, `push`;
- `UserNotificationPriority`: `low`, `normal`, `high`, `urgent`.

The external and datastore spelling is the declared snake_case powertype
value. The former `in-app` spelling is not retained as an alias because Phase
16 explicitly permits the generated-source and wire-contract break.

Channel extension is not represented by accepting arbitrary channel strings.
Provider-specific variability remains owned by the open
`UserNotificationDeliveryProvider` contract. Notification type also remains
open pending its registry decision.

Notification lifecycle status is not part of this migration. It requires a
statemachine once notification transitions are specified. Delivery-attempt
result status requires a separate closed contract rather than reusing the
notification lifecycle type.

## Implementation Evidence

- the three string-backed `DATATYPE` declarations were replaced by CML
  `POWERTYPE` declarations;
- generated Scala 3.3.8 types expose constants, value lookup, database-value
  lookup, labels, and rejecting `ValueReader` instances;
- handwritten notification logic continues to consume the generated values
  through their typed fields and explicit external `.value` projection;
- executable specification rejects undeclared audience, channel, and priority
  values, including the former `in-app` spelling;
- test-only arbitrary channel names were removed because a channel is no
  longer a test partition key.

## Remaining Work

- define and implement the notification lifecycle statemachine;
- define a distinct delivery-attempt result status;
- decide whether notification type is closed, versioned, or application
  extensible;
- compare generated ABI, REST/OpenAPI, datastore, form, and Help contracts as
  part of the complete driver migration.
