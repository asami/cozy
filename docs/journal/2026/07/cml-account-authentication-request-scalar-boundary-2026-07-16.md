# CML Account Authentication Request Scalar Boundary

date=2026-07-16
phase=16
status=implemented

## Context

The account command Values still exposed request-only authentication material
as primitive `string` even after entity and matching operation fields had been
aligned to their semantic types. This left passwords, proof/reset/refresh
tokens, login identifiers, two-factor challenge IDs, and verification codes
without one generated runtime type or explicit request-size boundary.

## Decision

CML `password` is the canonical nonlocalized runtime type for plaintext
password material. `org.goldenport.datatype.Password` preserves the exact
printable value, rejects empty/control-character input, and accepts at most
1024 characters. This range is an input-size boundary, not an account password
strength policy.

CML `token` is the canonical nonlocalized runtime type for opaque protocol
material. Its runtime maximum is 8192 characters so that the catalog can carry
UUIDs, opaque proof values, and encoded authentication tokens without falling
back to `String`. Individual account fields narrow that range:

| Role | Range |
|---|---:|
| login identifier | 1..320 |
| two-factor challenge ID | 1..255 |
| two-factor verification code | 1..256 |
| proof/reset/refresh token | 1..8192 |

The login `identifier` field intentionally uses `token`, not the narrower
predefined `identifier`. It accepts either a login name or an email address;
the `Identifier` lexical contract excludes `@`, `.`, and `-`. The same rule
applies to UUID-shaped challenge IDs.

## Generated Contract

SimpleModeler treats `password` as a text-constrained semantic type and emits:

- `Password` or `Token` fields instead of primitive `String`;
- constructor validation for authored `min-length` and `max-length`;
- operation metadata with `password` or `token` datatype names;
- the same bounds in `WebValidationHints` for form, Help, and automatic API
  projection.

The runtime types provide `ValueReader` and Circe codecs so plain request text
enters the semantic type at the generated construction boundary.

## Deferred Boundaries

- Password strength, breach checks, and deployment policy remain account
  application policy.
- Secret redaction remains the responsibility of CNCF confidentiality-aware
  observability and presentation boundaries.
- Stored password/token hashes remain domain-owned nominal scalars; they are
  not replaced by plaintext `Password` or protocol `Token`.
- Notification domain scalar ranges and JSON-to-structured Value migration
  remain separate Phase 16 slices.
