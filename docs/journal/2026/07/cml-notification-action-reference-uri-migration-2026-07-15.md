# CML Notification Action Reference URI Migration

date=2026-07-15
phase=16
status=implemented

## Context

`UserNotificationActionReference` was a nominal wrapper around an unconstrained
string. Real notification actions include both application-relative Web routes
and absolute external targets, so the value is a URI but not necessarily an
absolute URL.

## Decision

`Notification.actionUrl` and the matching `CreateNotification.actionUrl` field
use the predefined CML `uri` type directly. Generated Scala uses
`java.net.URI`. Relative references such as `/web/tasks/123` and absolute URIs
share one parser-backed external and datastore contract.

The field name remains `actionUrl` because it is the established notification
API role. Target authorization, allowed schemes, and external-navigation policy
remain application/runtime concerns and are not inferred from URI syntax.

## Evidence

- the string-backed `UserNotificationActionReference` Datatype was removed;
- entity and operation Value fields now share predefined `uri`;
- generated Scala 3.3.8 uses `URI` and rejects malformed input;
- executable component behavior persists and returns a relative route while
  generated input construction also accepts an absolute URI.

## Remaining Work

- define deployment policy for allowed absolute URI schemes and hosts;
- replace JSON-in-string audience and metadata fields with structured Values.
