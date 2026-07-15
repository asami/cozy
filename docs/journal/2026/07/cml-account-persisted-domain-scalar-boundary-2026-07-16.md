# CML Account Persisted Domain Scalar Boundary

Date: 2026-07-16

## Scope

This slice defines canonical length boundaries for persisted account values
whose one-string representation carries identity, audit, secret-hash, session,
client, or technical-request semantics.

## Decision

The account driver keeps these values as CML-owned nominal Datatypes:

| Type | Contract |
|---|---|
| `UserAccountExternalSubjectId` | issuer-scoped opaque value of 1..512 characters |
| `UserAccountSuspendedBy` | actor reference of 1..255 characters |
| `UserAccountSuspensionReason` | single-locale audit text of 1..4096 characters |
| `UserAccountPasswordHash` | algorithm-owned secret hash of 1..1024 characters |
| `UserAccountSessionReference` | opaque session reference of 1..255 characters |
| `UserAccountTokenHash` | algorithm-owned secret hash of 1..1024 characters |
| `UserAccountClientId` | client registry identifier of 1..255 characters |
| `UserAccountUserAgent` | nonlocalized technical text of 1..4096 characters |

The CML `min-length` and `max-length` constraints are authoritative. Generated
nominal scalar construction and Record decoding enforce the same boundaries;
the account driver does not add handwritten wrapper validation.

## Deferred Decisions

- external-subject issuer binding and normalization;
- suspended-by actor/account identity binding;
- translated presentation of suspension reasons;
- hash syntax, confidentiality-aware redaction, and no-display policy;
- session identity scope and entropy policy;
- client namespace and syntax;
- user-agent control-character policy;
- login-name normalization and the structured shape of device information.

These decisions may narrow representation or presentation later, but they do
not remove the current requirement that persisted nonempty values remain
bounded at generated construction and input boundaries.
