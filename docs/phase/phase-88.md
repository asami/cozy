# Phase 88: Application Function Subcomponent Generation

Status: PLANNED

Plan date: 2026-10-24

Renumbered: 2026-10-25 from the incoming planned Phase 87 to preserve the
closed [Phase 87](phase-87.md) figure/table delivery. Scope and planned status
are unchanged; no Function generation work is started by repository sync.

Upstream contracts:
- CNCF Phase 75: Application Operation function candidacy
- CNCF Phase 92: Platform Subcomponent CAR Distribution

## Goal

Generate Function-first Application-tier realizations from ordinary CML
Service/Operation definitions without introducing cloud-provider concepts into
the CML semantic model.

For every Application Service, retain the complete CNCF Container realization
containing all Operations. In addition, project the function-eligible Operation
subset into lightweight TypeScript Function Subcomponents.

## P88-01: Function projection plan

Consume the effective Application purpose/statefulness and function-candidacy
contract from the accepted CNCF handoff.

Produce one deterministic projection plan that records:
- parent Application Component identity;
- complete Operation set;
- function-eligible Operation subset;
- logical Operation identities/selectors;
- common Function ABI version; and
- selected platform profiles.

CML remains ordinary Service/Operation modeling plus admitted semantic
properties. AWS Lambda, Google Cloud functions, and local execution are not CML
domain concepts.

## P88-02: Provider-neutral TypeScript core generation

Generate a provider-neutral TypeScript core for every eligible Operation.

The core owns only the Application Function logic and CNCF Function ABI
boundary required by the accepted model. Provider invocation APIs, provider
credentials, provider deployment objects, and provider-specific lifecycle
logic must not leak into the common core.

TypeScript is the initial preferred Function implementation target because the
Function realization is optimized for small artifacts and spin-up. This is a
generator realization choice, not a CML semantic constraint.

## P88-03: Local, AWS, and Google adapters

Generate thin adapters for:
- local/test execution;
- AWS function execution; and
- Google Cloud function execution.

Adapters translate provider/local invocation, execution context,
identity/authentication handoff, tracing, error and response conventions to the
common CNCF Function ABI.

The local adapter is not a cloud emulator. It must support deterministic local
Function execution and Function-to-local-CNCF-Domain integration tests.

## P88-04: Platform Function Subcomponent CARs

Package self-contained Function Subcomponent CARs for the selected platform
profiles.

Source/build sharing and distribution are intentionally different:
- the TypeScript common core is generated and maintained once;
- packaging copies the same admitted common core into each platform-specific
  Function Subcomponent CAR;
- each CAR also contains its own thin adapter and deployment metadata;
- no separate common-function CAR is introduced solely for code
  deduplication.

The initial normal generation profile SHOULD be capable of producing both AWS
and Google Function Subcomponents from the same accepted Application model.
The local/test realization is produced for development and acceptance use.

The primary CNCF Container remains complete and authoritative as the fallback
realization. Function Subcomponents contain only the eligible subset and
preserve the same logical Operation identities and admitted semantics.

## P88-05: Local integration and realization equivalence

Provide executable evidence for:
1. generated TypeScript core unit tests;
2. local adapter boundary tests;
3. local Function-to-CNCF-Domain integration;
4. representative command/query behavior against the local Domain tier; and
5. where the selected Domain profile requires it, Active/Standby and
   read/cache routing integration.

For an Operation available through both Container and Function realizations,
representative executable specifications must detect externally observable
semantic drift. This does not require identical internal implementation.

Provider adapter contract tests remain bounded. Live AWS/Google acceptance is a
small separate deployment evidence set and is not replaced by the local
adapter.

## Non-goals

- cloud deployment orchestration;
- embedding AWS/Google concepts in CML;
- emulating AWS or Google Cloud locally;
- moving Domain logic into generated TypeScript;
- making Function availability a prerequisite for the complete CNCF Container;
- creating a runtime dependency between provider Function CARs and a common
  Function CAR.

## Completion Criteria

Phase 88 completes when one representative Application Service defined with
ordinary CML generates:
- its complete CNCF Container realization;
- a provider-neutral TypeScript Function core for the eligible subset;
- local, AWS, and Google adapters;
- self-contained AWS and Google Function Subcomponent CARs;
- a local/test realization; and
- passing local Function-to-Domain integration/equivalence evidence.

The generated CAR metadata must remain compatible with the accepted CNCF Phase
92 Platform Subcomponent distribution contract.

## P88-06: AuthHandle-aware Function security generation

Generate the Function-side security boundary required by the CNCF AuthHandle
contract without embedding the full CNCF authentication runtime in TypeScript.

Generated common Function code and adapters must support this flow:

1. receive external OAuth/OIDC credential evidence;
2. perform only admitted cheap prechecks suitable for ingress/load shedding;
3. resolve or reuse a bounded cached AuthHandle through the centralized User
   Management/identity API;
4. reuse the same valid AuthHandle while one Application Operation orchestrates
   multiple Domain Components;
5. call each Domain Function API with the Operation request, opaque AuthHandle,
   generated Function/service identity and required observability context.

Generated Function code must not:
- decode AuthHandle into User/role/capability authority;
- synthesize an authoritative CNCF SecurityContext/ExecutionContext;
- require User DB credentials solely for context construction; or
- treat a Function-local cache hit as Domain authorization.

The local adapter must provide a deterministic test identity/AuthHandle provider
that exercises the same Function ABI and Domain Function API contract without
becoming a cloud emulator. Tests must cover cache hit/miss/expiry, rejected
credential evidence, one-handle multi-Domain orchestration, Domain-side context
construction, and preservation of Domain-local Capability/Guard authorization.

AWS and Google adapters must keep provider-specific ingress/service identity and
edge/deployment concerns outside the common TypeScript logic. Generated
deployment metadata must expose bounded downstream concurrency/admission knobs
where the platform profile supports them so Function autoscaling does not imply
unbounded load on User Management or Domain tiers.
