# Cozy Security Responsibility

status=initial
updated_at=2026-10-14
canonical_architecture=goldenport-cncf/docs/design/textus-security-architecture.md

## Role

Cozy preserves the Textus Security Architecture when compiling models and
generating applications/CAR artifacts. CNCF owns the canonical trust and runtime
security model; Cozy must not invent a parallel authorization model.

Generated Web/API surfaces invoke declared CNCF Operations and preserve
Capability/Guard and Entity/DataStore boundaries. Generated code must not gain
arbitrary database, filesystem, process or network authority as a shortcut.

Cozy should detect deterministically knowable unsafe generation patterns and
surface them through lint/review evidence. Domain-specific authorization and
deployment policy remain application/CNCF concerns.

Security is not justification for restoring the management-hash, generated
artifact protection, whole-tree copy, speculative corruption detection or
bespoke rollback machinery removed by Phase 71.5. Immutable/reproducible
generation plus replacement from authoritative inputs is preferred where
applicable.

## Follow-up

Future design should map CML security declarations to CNCF authorization
contracts, define lintable exposure/authority mistakes, and ensure generated
public surfaces cannot bypass runtime authorization.
