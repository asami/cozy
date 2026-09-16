# Phase 63 - Multi-CML Provenance Contract and Cozy Aggregation

status=closed
split_full_test_policy=each-phase
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none

Status: closed
Planned at: 2026-09-16
Development item: DEV-031
Primary owner: Cozy
Split applied: 2026-09-16
Successor: [Phase 63.1](phase-63.1.md)

## Phase Plan Gate

Pre-split gate evidence (2026-09-16): `SPLIT_REQUIRED` from the typed Phase
Entry Gate. The complete Phase estimate was 600 minutes, with a 720-minute
upper bound; it exceeded the 480-minute ceiling and had
`[time-bound, reasoning-cost-isolation]` reasons.

Phase Plan Gate: PROCEED

- target: calibrated expected duration centered on 6h; allowed ceiling 8h
- estimate_calibration: Phase 61.1 supplied the available one-repository
  closure comparison. Phase 63's original 600-minute estimate adds a protected
  contract, a second mutation repository and an external driver; this first
  child retains the 330-minute Cozy contract/aggregation interval.
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: MCML-63-01 decides the project-level
  multi-source representation, v1 compatibility, stale/duplicate/ambiguous
  evidence failures and shared-output conflict semantics.
- frozen_profile_transition_handoff: accepted MCML-63-01 contract plus the
  MCML-63-02 Cozy aggregate/rebind API and focused executable-specification
  receipts for Phase 63.1.
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 5.0-6.0h (330-minute calibrated expected);
  centered near the 6h target and within the 8h ceiling.
- incoming_semantic_handoffs: []
- merge_attempts_for_every_sub_4h_child: none
- rebalance_attempts_for_every_sub_5h_child: none
- adjacent_merge_structural_rejection_evidence: merging MCML-63-03/04 back
  into this Phase restores the 600-minute cross-repository interval and mixes
  an unresolved contract authority with downstream bridge execution.
- profile_cost_only_rejection_forbidden: true
- short_child_basis: none
- overhead_tradeoff: one additional review, release ledger, commit and
  per-Phase full-validation gate isolate the authoritative contract and allow
  the successor to use a lower-cost settled-execution profile.
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch when the active interface supports it and frozen
  quality-first evidence justifies it.
- runtime_suitability: re-evaluate in the Phase execution task
- source: applied split from Phase 63 on 2026-09-16

## Purpose and boundary

Make project-level multi-CML provenance an accepted Cozy capability. This Phase
owns the contract and Cozy aggregate/rebind implementation that convert valid,
isolated per-CML evidence into one deterministic project result. It closes the
authority needed by [Phase 63.1](phase-63.1.md); it does not implement the
sbt-cozy bridge or start the downstream driver.

The current `cozy.generation-provenance.v1` format represents one CML source
and deliberately rejects arbitrary selection among multiple delegated
manifests. The accepted extension must preserve provenance, not disable it or
reinterpret one manifest as an entire project.

## Work stack

| ID | Outcome | Status |
| --- | --- | --- |
| MCML-63-01 | Freeze the multi-CML project-level provenance contract, including source identity, generated-output conflict semantics and one-CML compatibility. | DONE |
| MCML-63-02 | Implement Cozy aggregate/rebind validation and focused executable specifications. | DONE |

## Required behavior

- Every participating CML source remains explicitly represented by its
  project-relative identity; no source is selected by enumeration order.
- A project-level result covers all accepted delegated inputs deterministically.
- Shared generated paths with identical claimed bytes are represented without
  duplicate ambiguity; conflicting bytes for the same path fail explicitly.
- Missing source evidence, duplicate/ambiguous source identities, stale input,
  malformed delegated evidence and contradictory generated output fail before
  a project-level result is accepted.
- Existing one-CML projects remain supported under the accepted compatibility
  path.

## MCML-63-01 contract admission evidence

MCML-63-01 is DONE. The accepted contract is recorded in the
[generation compatibility specification](../spec/generation-compatibility-contract.md)
and [generation compatibility design](../design/generation-compatibility-contract.md).
It freezes `cozy.generation-provenance.v2` as Cozy's project aggregate result:
the aggregate API receives a non-empty explicit set of delegated
`(manifest path, output root)` pairs; it preserves canonical
project-relative source identity/digest, target/generator agreement, delegated
v1 source-output/evidence claims, a sorted source list, and a sorted aggregate
output/evidence result. Digest fields are integrity evidence only and never
derive source identity or select an input.

The accepted V2 union deduplicates a shared project-output-relative path only
when its claimed digest agrees; differing claimed digests fail explicitly.
Missing/unreadable or malformed delegated evidence, duplicate/ambiguous source
identity, stale source/output, expected target/generator disagreement, and
contradictory evidence all fail before atomic V2 publication. Existing direct
one-CML v1 generation and v1 package admission continue to work, while one
accepted delegated v1 input may produce V2 without migration. Cozy owns the
aggregate/rebind API and preserves the legacy single-rebind path as a wrapper
or one-source equivalent. The sbt-cozy bridge request grammar, collection,
installation, incremental behavior, plugin integration, and downstream driver
remain unstarted Phase 63.1 work.

## MCML-63-02 implementation evidence

MCML-63-02 is DONE with accepted fresh representative V2 receipt
`P63-MCML-63-02-VAL-009`, V1 accumulator receipt `P63-MCML-63-02-VAL-010`,
and clean focused re-review `P63-MCML-63-02-FOCUSED-REREVIEW-001`.
`GenerationProvenance` now validates an explicit,
nonempty collection of delegated V1 manifest/output-root pairs before it
atomically publishes the sorted `cozy.generation-provenance.v2` aggregate.
It preserves the legacy one-input rebind API as a V2 wrapper, retains direct
V1 production and admission, and admits either validated representation at the
existing package-validation APIs. The focused executable specification
`Phase63McmlGenerationProvenanceSpec` records two-source sorting and output
deduplication, one-source compatibility, V2 package admission, and every
frozen rejection boundary. Focused SBT receipt execution remains parent-owned
Step evidence. Phase closure is supported by independent full review
`P63-FULL-REVIEW-001` (disposition
`323b7c5d29146473dbf71a6c24b8e2c616ef13ef4b31ff3d1d696119b4ddddae`)
and repository-full SBT receipt `P63-FULL-VALIDATION-001`
(`593d37205c74c26372213bd48a59abe8563635e584cc8dd204900028f8f9d926`).

## Completion conditions

- The accepted contract specifies every input, output, compatibility and
  rejection boundary listed in MCML-63-01.
- Cozy executable specifications prove accepted two-source aggregation and each
  required rejection boundary while preserving one-CML behavior.
- This Phase performs its own configured Cozy repository-full SBT validation,
  independent review, closure ledger and release commit under the user-selected
  `each-phase` policy.
- The committed handoff names the exact aggregate/rebind contract and receipts
  that Phase 63.1 may consume without reopening the contract.

All completion conditions are satisfied by the accepted MCML-63-01 commit
`2322c9057470cf339fa9028a37a4fcf9094d54a8`, MCML-63-02 commit
`ba0912e1335c0b5c788b66b53fa052c6d6392ca6`, their focused receipts and
independent review, the sealed Phase full-review disposition, and the
repository-full SBT receipt above. `HYG-P63-001` is retained as nonblocking
Phase-owned documentation follow-up; it changes neither contract nor test
result. Phase 63.1 remains planned and unstarted.

## Non-goals

- sbt-cozy manifest collection, side-output installation, incremental behavior
  and plugin integration; these belong to [Phase 63.1](phase-63.1.md).
- Running the `simplemodeling-model` driver or claiming Phase 74.1 closure;
  these remain Phase 63.1's handoff and the downstream Phase's ownership.
- Suppressing provenance, choosing a manifest by order, or inventing a
  hash-derived source identity or generic internal hash-control mechanism.
- EntityId, JobDefinition, CNCF Phase 74.2 or runtime-consumer implementation.

## Split handoff and provenance

The user invoked `$cncf-split-phase Phase 63 --full-test-each-phase` on
2026-09-16. The applied sequence is `PHASE-63 -> PHASE-63.1`; the source goal
was `none / no-goal-pre-entry`. MCML-63-01/02 remain here and MCML-63-03/04
move exactly once to Phase 63.1. The receiving child consumes an authority
handoff consisting of the accepted project-level multi-CML contract, the Cozy
aggregate/rebind operation and its focused receipt set. It is invalidated by a
changed source-identity, compatibility, failure or output-conflict rule.

The explicit `each-phase` validation policy replaces the unavailable
single-repository final-only aggregate route: each child performs its own
configured full SBT validation. No completed history, product source, external
project edit, publication, deployment, push or child goal was created by this
planning operation.

## References

- [DEV-031 journal record](../journal/2026/09/2026-09-16-multi-cml-generation-provenance-development-candidate.md)
- [Phase 63 Checklist](phase-63-checklist.md)
- [Phase 63.1 bridge successor](phase-63.1.md)
- [Cozy generation compatibility contract](../spec/generation-compatibility-contract.md)
