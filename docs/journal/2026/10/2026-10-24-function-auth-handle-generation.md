# Function AuthHandle Generation Handoff

Date: 2026-10-24

Cozy Phase 88 consumes the CNCF Function AuthHandle security boundary.

The generated TypeScript Function is deliberately thinner than a CNCF
Container. It performs bounded ingress/pre-admission work, obtains or reuses an
opaque AuthHandle from centralized User Management, and passes that handle to
each Domain Function API involved in Application orchestration.

The Domain side remains responsible for authoritative SecurityContext and
ExecutionContext construction and Domain-local Capability/Guard authorization.

This keeps AWS, Google and local adapters on one Function ABI, avoids User DB
credentials in generated public Functions, and preserves Cloud Function as a
DDoS/load-shedding boundary rather than allowing autoscaling to amplify work
directly into bounded Domain resources.

Implementation is planned in `docs/phase/phase-88.md`, P88-06.

On 2026-10-25, this planned work was renumbered from Phase 87 to Phase 88
during repository synchronization to preserve the separately closed
figure/table Phase 87. The Function scope remains planned and unstarted.
