# Phase 88 checklist — Application Function Subcomponent Generation

status=planned
execution_started=false
Plan: [Phase 88](phase-88.md)
Owner: Cozy development
Registered: 2026-11-16

## Stage Status

- Current status: OPEN
- Owner: Cozy development
- Update rule: Record actual delivery and verification for the corresponding item.
- Closure basis: All P88-01–P88-06 items and the final verification below. Checklist registration is not implementation or acceptance.

## P88-01: Function projection plan

- [ ] Consume the accepted CNCF Application purpose/statefulness and Function candidacy contracts.
- [ ] Produce a deterministic plan with parent Component, complete Operation set, eligible subset, logical selectors, Function ABI version and selected platform profiles.
- [ ] Preserve provider-neutral CML and the complete Container realization, including ineligible Operations.

## P88-02: Provider-neutral TypeScript core

- [ ] Generate the eligible Application logic against the admitted CNCF Function ABI.
- [ ] Keep provider APIs, credentials, deployment objects and lifecycle logic outside the common core.
- [ ] Verify representative generated TypeScript behavior without moving Domain logic into the core.

## P88-03: Local, AWS and Google adapters

- [ ] Generate all three adapters with request, context, identity, tracing, error and response mappings to the common ABI.
- [ ] Exercise deterministic local Function execution and local CNCF Domain integration without a cloud emulator.
- [ ] Verify bounded provider adapter contracts; distinguish them from separately authorized live deployment evidence.

## P88-04: Function Subcomponent CARs

- [ ] Package self-contained AWS and Google CARs and a local/test realization from the same admitted common core.
- [ ] Preserve platform adapter/metadata ownership, logical Operation identities and the complete primary Container.
- [ ] Verify CNCF Phase 92 distribution metadata without introducing a common-function CAR runtime dependency.

## P88-05: Integration and equivalence

- [ ] Verify generated core behavior, local adapter boundaries and representative command/query calls to local Domain Components.
- [ ] Detect observable semantic drift between Container and Function realizations.
- [ ] Exercise Active/Standby and read/cache routing where the selected Domain profile requires them.

## P88-06: AuthHandle security boundary

- [ ] Admit external credential evidence and bounded ingress prechecks; resolve/reuse AuthHandle through the central identity API.
- [ ] Verify cache hit, miss, expiry and rejected credentials with a deterministic local identity/AuthHandle provider.
- [ ] Verify one valid handle across multi-Domain orchestration, including Function/service identity and observability context.
- [ ] Preserve Domain-side SecurityContext/ExecutionContext construction and local Capability/Guard authorization. Reject interpreting an opaque handle or a local cache hit as authority; require no User DB credentials for Function-side context construction.
- [ ] Keep provider ingress/service identity outside common TypeScript and expose supported downstream concurrency/admission limits in generated metadata.

## Final verification and closure

- [ ] Complete focused and local integration specifications for all six Steps, including the AuthHandle negative paths.
- [ ] Complete independent review, final ordinary Cozy validation and the repository's completion procedure.
- [ ] Record any separately selected live AWS/Google evidence with its actual scope; do not claim it from local adapter results.
- [ ] Update Phase/checklist/Strategy from actual results and retain separate follow-up work.
