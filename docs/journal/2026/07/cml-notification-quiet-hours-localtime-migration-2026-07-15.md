# CML Notification Quiet Hours Local-Time Migration

date=2026-07-15
phase=16
status=implemented

## Context

`UserNotificationQuietHours` was a nominal wrapper around an unconstrained
string. Both preference fields represented a wall-clock time interpreted in
the preference time zone, so their value semantics were already narrower than
the generated contract.

## Decision

`NotificationPreference.quietHoursStart` and `quietHoursEnd`, together with the
matching command fields, use the predefined CML `localtime` type directly.
Generated Scala uses `java.time.LocalTime`. The canonical external and
datastore representation is its ISO local-time form.

Quiet hours remain a pair of optional fields because interval completeness and
policy are application concerns. When both are present:

- equal start and end means no quiet interval;
- start before end defines a same-day interval;
- start after end defines an interval crossing midnight.

The preference time-zone field determines which local clock interprets the
interval. It remains the predefined `timezone` type.

## Evidence

- the string-backed `UserNotificationQuietHours` Datatype was removed;
- entity and operation Value fields now share the same `localtime` type;
- generated Scala 3.3.8 source uses `LocalTime` and rejects malformed input;
- an executable component specification stores `22:00` to `07:00` in UTC and
  defers a notification received at `23:30` until `07:00` the next day.

## Remaining Work

- decide whether incomplete quiet-hour pairs are rejected or treated as no
  interval;
- define deployment policy for daylight-saving gaps and overlaps;
- migrate JSON-in-string audience and metadata fields to structured Values.
