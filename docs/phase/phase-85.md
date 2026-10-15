# Phase 85: CML Sensitive Information Modeling and Safe Generation

Status: PLANNED
Plan date: 2026-10-16
Depends on: CNCF Phase 111
Related: CNCF Sensitive Information Architecture
Consumers: Flutter Application Framework Phase 3, CBD Support follow-up

## Goal

Define how CML expresses sensitive-information intent and how Cozy preserves it
in generated CNCF/application artifacts.

## Planned work

1. Refine CML representation for sensitive personal data, external identity,
   credentials/secrets, excluded highly sensitive values and external
   references/results.
2. Decide which concepts are semantic types, property metadata, annotations or
   model-level declarations.
3. Define diagnostics for obviously unsafe ordinary-domain modeling such as
   provider credentials or excluded payment verification values.
4. Generate CNCF SubjectId/ExternalIdentity mapping use rather than
   application-local provider identity propagation.
5. Preserve no-log/no-implicit-display/no-implicit-serialization semantics where
   CNCF Phase 111 defines them.
6. Generate provider-delegated interaction/reference contracts where declared.
7. Keep escape cases explicit and reviewable.

## Initial direction

CML should model the business fact owned by the Textus domain. For payment this
normally means PaymentMethodReference/PaymentTransactionReference and status,
not PAN/CVV. For ownership this normally means Textus SubjectId, not a Google
provider subject.

Do not freeze syntax until CNCF Phase 111 has refined the minimal runtime
contract.
