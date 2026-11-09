# Phase 89 checklist — Strict Scala 3 generation integration

status=planned

Plan: [Phase 89](phase-89.md).
Owner: Cozy development.
The original delivery IDs and criteria are preserved. All items remain OPEN.

## Stage: Producer delivery

Stage Status:
- Current status: OPEN
- Owner: Cozy development
- Update rule: Record actual generator selection and generated-consumer evidence.
- Closure basis: COZY-01–COZY-05 below; no implementation is claimed.

- [ ] COZY-01: Inventory the real CML -> model -> generator route and Cozy-owned
      Scala emitters; define selected output scope and existing strictEquality
      coverage without inferring it from a single template.
      Include structural braces for class/trait/object/enum/given/extension and
      domain/framework DSL hierarchy, with indentation limited to terminal
      implementation expressions. Qualify both SimpleModeler and direct Cozy
      output; a structural DSL lambda is not an allowed leaf merely by being a lambda.
- [ ] COZY-02: Consume the accepted strict-output SLP-GEN subset and qualified
      SLP-BUILD compiler settings. Generate and compile tiny representative
      library/CNCF output for applicable paths, with correct same-type compares
      and negative incompatible ID/value cases; retain actual selected versions.
- [ ] COZY-03: Qualify explicit-null/initialization/warning settings applicable
      to generated output; fix the owning generator/adapter only for demonstrated
      output gaps. No broad .nn/unsafeNulls/CanEqual/Wconf suppression fallback.
- [ ] COZY-04: After SLP-CORE and the generator's C handoff, qualify actual safe
      collection API use in representative output consumers and relevant
      Cozy-owned emitters. Preserve declared value/ID semantics; never emit a
      proposed but unavailable method or guess domain identity from field names.
- [ ] COZY-05: Return SLP-COZY with CML inputs, selected generator/Core/model
      versions, output scopes, normal generated compilation and independent
      review results, existing CAR-lint evidence and publication/adoption limits.
