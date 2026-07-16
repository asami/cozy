# CML Notification Account Subject Identity Boundary

Date: 2026-07-16

## Context

`textus-user-notification` uses the authenticated CNCF
`SecuritySubject.subjectId` for direct recipients, senders, per-user state, and
notification preferences. The CML nominal Datatype already prevented these
fields from falling back to an unclassified Scala `String`, but its underlying
text had no accepted range.

This identifier is not a `UserAccount` entity id. It may identify a principal
owned by an authentication provider or another CNCF security source, so
replacing it with predefined `entityid` would merge distinct identity
contracts.

## Decision

`UserNotificationAccountSubjectId` remains a nonlocalized nominal scalar over
`string` with the same 1..512 character boundary as the User Account external
subject identifier. This avoids rejecting a valid provider-owned subject that
can cross from CNCF security context into notification targeting.

The value is opaque to User Notification. Construction and projection preserve
case, punctuation, provider namespace syntax, and all other accepted text
exactly. User Notification does not trim, case-fold, localize, or otherwise
normalize it. Equality and datastore queries continue to use the exact CNCF
subject identifier.

The CML constraint is the single source for generated constructor validation,
Record decoding, datastore input, operation metadata, REST/OpenAPI, and Web
validation hints. No handwritten notification validation is added.

## Verification Direction

Executable coverage must prove:

- exact identity survives constructor, Record, and datastore projection;
- 1 and 512 character identifiers are accepted;
- empty and 513 character identifiers are rejected;
- generated operation metadata publishes the same nominal type and range.
