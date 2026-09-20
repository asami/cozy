# Phase 66 Checklist: Generalized Composite StateMachine Semantic Artifact

Status: planned; execution deferred before implementation
Phase: [Phase 66](phase-66.md)
Development item: DEV-033

This checklist preserves the expected future capability. It is not executable
until Phase 66's entry condition is satisfied and a new bounded plan is
accepted.

## Entry

- [ ] The first `sm-workflow` vertical slice has reached a stable result.
- [ ] A concrete consumer requirement identifies missing semantic fields and
      their use.
- [ ] CNCF Phase 89 records that requirement as its admission and runtime-
      projection entry, without reopening CNCF Phase 64.
- [ ] The current Cozy implementation and released artifacts have been
      inventoried against that requirement.
- [ ] The Phase has been re-estimated without inheriting the former split or
      twelve-hour estimate.

## CSA-66-01: Artifact contract

- [ ] Freeze the typed semantic artifact domain model.
- [ ] Freeze schema, generator, definition, constituent, rule, and action
      identity/version rules.
- [ ] Keep the canonical domain model distinct from its sidecar encoding.
- [ ] Define fail-closed compatibility and version-transition behavior.

## CSA-66-02: IR projection and provenance

- [ ] Project exact constituents, roles, pinned definitions, subjects, and
      configuration from the Cozy IR.
- [ ] Project a complete finite typed rule set with stable identities, exact
      inputs, explicit composite-state outputs, and declaration order.
- [ ] Project typed logical actions with ownership, phase/order,
      correlation/causation, and producer-owned execution metadata.
- [ ] Preserve source identity, model identity/version, and exact
      source-location provenance for every declaration.

## CSA-66-03: Diagnostics and deterministic encoding

- [ ] Produce typed reachability, coverage, overlap, ambiguity, and
      incompleteness diagnostics.
- [ ] Reject missing, duplicate, foreign, unpinned, opaque, incomplete,
      ambiguous, or nondeterministic artifacts before publication.
- [ ] Prove byte-deterministic generation for the same admitted source and
      generator version.
- [ ] Require no consumer CML parsing, name inference, or handwritten
      replacement data.

## CSA-66-04: Acceptance and handoff

- [ ] Add positive, rejection, determinism, provenance, and compatibility
      executable specifications.
- [ ] Preserve existing Workflow/StateMachine ABI behavior or introduce an
      explicitly versioned compatibility change.
- [ ] Run validation and independent review required by the newly accepted
      execution plan.
- [ ] Record exact source, generator, artifact schema, contract digest,
      revision, and validation evidence in the consumer handoff.
- [ ] Hand the closed producer artifact to CNCF Phase 89 without consumer CML
      reparsing, name inference, or handwritten completion of missing data.

## Separation checks

- [ ] Do not absorb commits `ca3a031`, `4561c02`, or current Cozy
      StateMachine working-tree changes without separate authority.
- [ ] Do not make Phase 66 a prerequisite for CNCF Phase 64, Phase 77, or
      `sm-workflow` Phase 1.
- [ ] Keep schema admission, compatibility diagnostics, `ComponentFactory`
      discovery, and runtime projection in CNCF Phase 89.
- [ ] Do not recreate Phase 66.1 unless the future re-estimate independently
      requires a split.
