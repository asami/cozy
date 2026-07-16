# CML Notification Structured Record Boundary

date=2026-07-16
phase=16
status=implemented

## Context

`textus-user-notification` represented audience queries and notification
metadata as nominal datatypes wrapping JSON text. Component logic then parsed
audience JSON with string splitting and serialized provider metadata with
handwritten escaping. The generated model could therefore claim a nominal
type while the actual domain value remained an unvalidated string.

## Decision

The canonical CML fields are structured records:

- `Notification.audienceQuery: record ?`
- `Notification.metadata: record ?`
- the matching `CreateNotification` fields use the same names and types.

The old `UserNotificationAudienceQueryJson` and
`UserNotificationMetadataJson` declarations are removed. No compatibility
adapter accepts the old field names or JSON strings.

Multicast evaluation treats every audience-query field as a required match
against subject identity, group, role, or subject attributes. A sequence value
requires every declared value to be present. Notification-provider metadata
and correlation identity are persisted in one Record.

## Boundary Contract

Generated Scala values, direct operation requests, datastore records, and
automatic REST use `org.goldenport.record.Record`. Passing JSON text to these
structured fields is invalid.

An HTML form is a text transport, so CNCF Web descriptors may declare an
explicit `json` or `record` control. CNCF validates that text as one JSON
object and decodes it to a Record before operation dispatch. An empty optional
control is removed from the request. This is a reusable Web presentation
adapter, not a compatibility rule at the domain boundary.

## Verification

- generated `CreateNotification` preserves nested audience and metadata
  Records and rejects JSON strings;
- multicast search matches structured audience fields and excludes
  nonmatching records;
- notification-provider locale/time-zone metadata remains a nested Record
  after persistence and search;
- CNCF Web form decoding accepts JSON objects, removes empty optional values,
  rejects malformed JSON, and leaves ordinary controls unchanged.
