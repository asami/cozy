# CML Opaque Text Confidentiality Policy

date=2026-07-16
phase=16
status=decided

## Context

Phase 16 classified account password hashes, token hashes, session references,
and related provider values as intentional opaque text. Their value constraints
were already defined, but their relationship to display-oriented
`DescriptiveAttributes` and CNCF redaction remained open.

## Decision

Opaque representation does not itself determine disclosure. CML
`confidentiality` is the canonical disclosure metadata and remains independent
of the Datatype's identity and range.

Hashes, credentials, raw secrets, and token material do not belong in
`DescriptiveAttributes` and never participate in localized `effective*`
fallback. Generated schema and operation metadata preserve confidentiality.
CNCF projects `secret` inputs to password-style controls and redacts
`personal`, `sensitive`, and `secret` values from generic diagnostics,
observability, execution debug output, and result previews by default.

`public` and `internal` are not redacted by default. Authorization of internal
business data remains a surface and domain responsibility; confidentiality is
not an authorization policy.

## Driver Evidence

User Account marks persisted `Credential.passwordHash` and session `tokenHash`
fields as `secret`. An executable generated-boundary specification confirms
that the nominal hash type and secret metadata survive schema generation, that
the generic disclosure policy requires redaction, and that Web projection uses
a password control without moving the value into descriptive metadata.
