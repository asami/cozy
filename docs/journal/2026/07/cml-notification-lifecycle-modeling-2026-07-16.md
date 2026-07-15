# CML Notification Lifecycle Modeling

date=2026-07-16
phase=16
status=implemented

## Context

`textus-user-notification` used one nominal string-backed
`UserNotificationStatus` for both notification delivery lifecycle state and a
delivery attempt's provider outcome. That erased two different semantics and
allowed arbitrary values without a model-owned transition contract.

Recipient read and dismissal data is already stored separately in
`NotificationUserState`. It is not part of delivery progression and must not
be added to the notification lifecycle merely because clients display it as
status-like information.

## Decision

Notification delivery uses the closed states `Queued`, `Sending`, `Sent`,
`Delivered`, `Failed`, and `Canceled`. The `notificationLifecycle`
statemachine owns these transitions:

- `Queued` begins delivery or is canceled;
- `Sending` is accepted as `Sent`, confirmed as `Delivered`, fails, or is
  canceled;
- `Sent` is confirmed as `Delivered` or fails;
- `Failed` may retry from `Queued` or be canceled;
- `Delivered` and `Canceled` are terminal in this contract.

The external values retain their established title-case spelling. The source
break is structural: the former nominal Datatype becomes a generated closed
state vocabulary backed by a generated statemachine contract.

A delivery attempt records an outcome rather than the parent notification's
lifecycle. `UserNotificationDeliveryResultStatus` is therefore a separate
closed type with `Pending`, `Succeeded`, and `Failed` values. Entity, create
command, and search query fields use that type consistently.

## Verification Contract

Executable driver specifications must verify:

- undeclared notification states and delivery outcomes are rejected;
- notification and delivery-attempt values cannot be interchanged;
- the generated component exposes the exact lifecycle states and transition
  events;
- generated transition rules retain machine, state-field, source-state, and
  target-state topology;
- the entity update boundary permits declared state changes and rejects
  undeclared changes before persistence;
- delivery-attempt operation metadata names the dedicated result type;
- clean Scala 3.3.8 generation, compilation, full driver tests, and CAR
  packaging succeed.

## Remaining Work

Notification type and delivery provider still require closed-versus-open
registry decisions. Generic string-backed identity, diagnostic, and structured
JSON fields remain separate Phase 16 classification work.
