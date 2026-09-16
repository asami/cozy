# Phase 63.1 - sbt-cozy Multi-CML Bridge and Driver Acceptance

status=closed
split_full_test_policy=each-phase
split_full_validation_method=sbt-full-suite
split_validation_bootstrap=none
depends_on=phase-63.md

Status: CLOSED
Planned at: 2026-09-16
Closed: 2026-09-17
Development item: DEV-031 (split child)
Split from Phase 63: 2026-09-16
Predecessor: [Phase 63](phase-63.md)
Primary implementation owner: sbt-cozy
Downstream acceptance driver: simplemodeling-model Phase 74.1

## Phase Plan Gate

Phase Plan Gate: PROCEED

- target: calibrated expected duration centered on 6h; allowed ceiling 8h
- estimate_calibration: Phase 63's 600-minute total was partitioned into a
  330-minute contract/Cozy authority interval and this 270-minute bridge and
  driver interval. The latter is an unavoidable 4-5h balanced remainder after
  preserving the independent authority handoff.
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none; Phase 63 owns the unresolved contract
  decisions before this bridge is admitted.
- frozen_profile_transition_handoff: accepted Phase 63 multi-CML contract,
  Cozy aggregate/rebind API and focused executable-specification receipts.
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 4.0-5.0h (270-minute calibrated expected);
  an unavoidable balanced remainder below the 6h target and within the 8h
  ceiling.
- incoming_semantic_handoffs: [{ from_child: PHASE-63, to_child: PHASE-63.1,
  kind: authority, input: accepted project-level multi-CML contract and Cozy
  aggregate/rebind receipt set, action: consume the frozen contract without
  source-order selection, output: admissible sbt-cozy bridge implementation
  and downstream driver receipt, owner: Cozy PHASE-63,
  invalidation_reason: changed source identity, compatibility, failure or
  output-conflict semantics }]
- merge_attempts_for_every_sub_4h_child: none
- rebalance_attempts_for_every_sub_5h_child: moving MCML-63-02 here would leave
  Phase 63 unable to close its contract with executable evidence; moving the
  real driver back creates a bridge closure without its required consumer
  receipt. No remaining item can cross the authority boundary.
- adjacent_merge_structural_rejection_evidence: merging with Phase 63 restores
  a 600-minute, two-mutation-repository delivery and removes the independently
  committed authority handoff; profile cost alone is not the reason.
- profile_cost_only_rejection_forbidden: true
- short_child_basis: unavoidable balanced 4-5h remainder after the authority
  boundary
- overhead_tradeoff: a second review, release ledger, commit and full-suite
  gate add overhead, but permit a settled Terra-high bridge execution task and
  satisfy the user's explicit each-phase validation choice for multiple
  repositories.
- agent_reasoning_mode_policy: default standard; consider pro only at an
  eligible agent launch when the active interface supports it and frozen
  quality-first evidence justifies it.
- runtime_suitability: re-evaluate in the Phase execution task
- source: applied split from Phase 63 on 2026-09-16

## Purpose and boundary

Consume Phase 63's accepted contract to collect every delegated provenance
manifest in sbt-cozy, call only the accepted Cozy aggregate/rebind operation,
install the project-level side output atomically and preserve incremental
availability. Then prove the real two-CML `simplemodeling-model` driver through
normal generation and transfer its producer receipt to Phase 74.1.

Source edits in `/Users/asami/src/dev2026/sbt-cozy` require that exact root to
be explicitly added to the later Phase goal's allowed update roots. The
`simplemodeling-model` repository remains a validation driver except for the
narrow, separately authorized `project/plugins.sbt` coordinate update recorded
below; this child never closes or publishes Phase 74.1.

## Work stack

| ID | Outcome | Status |
| --- | --- | --- |
| MCML-63-03 | Implement the accepted Cozy contract in sbt-cozy, including multi-manifest collection, side-output installation, incremental checks and plugin integration specifications. | accepted |
| MCML-63-04 | Prove the real two-CML `simplemodeling-model` driver can pass its blocked focused producer test, then transfer only that acceptance signal back to Phase 74.1. | CLOSED |

## MCML-63-04 constrained authorization

The direct user authorization `authorize-release-and-driver-coordinate` admits
only these two continuation actions:

- Publish the exact `sbt-cozy` `0.1.18-SNAPSHOT` from accepted producer commit
  `d22d0c7b0f70d9129fd00392d9663158be48862c` by its existing Ivy-local
  `publishLocal` route.
- Change only `simplemodeling-model/project/plugins.sbt` from
  `org.goldenport:sbt-cozy:0.1.16` to `0.1.18-SNAPSHOT`, retaining its existing
  resolver order: `Resolver.defaultLocal`, `Resolver.mavenLocal`, then the
  SimpleModeling repository.

MCML-63-03 is accepted with Cozy commit
`eb4743cd9f4c966ca6762f3ab0a83c37375ba752` and the producer commit above.
Its focused receipts and independent Step review are accepted Phase evidence.

This authorization does not admit a remote publication/upload, a driver source,
build, project, CML, or test change, any resolver or source-path workaround, or
any Phase 74.1 action. MCML-63-04 completed its normal driver acceptance with
the locally published producer artifact, and records the final V2 provenance
digest solely as the still-open Phase 74.1 handoff.

## Completion conditions

- sbt-cozy integration coverage proves two-CML generation, preserved one-CML
  behavior, atomic project-level side-output installation and rejected
  contradictory evidence using the frozen Phase 63 contract.
- The real `simplemodeling-model` two-CML `EntityIdSpec` driver completes
  through normal generation with no local source-selection workaround or
  provenance bypass.
- The exact producer artifact/receipt handoff is recorded for Phase 74.1;
  neither that downstream Phase nor its source/publication closure is claimed.
- This Phase performs required focused acceptance, independent review and its
  own configured full SBT suite in every admitted changed SBT repository under
  the user-selected `each-phase` policy.

## Non-goals

- Reopening Phase 63's source-identity, compatibility, failure or output
  conflict semantics.
- Editing `simplemodeling-model` sources, build, project, CML inputs or tests;
  changing its inputs to one source; remote publication/upload; or closing
  Phase 74.1. The sole publication exception is the authorized Ivy-local
  `sbt-cozy` `0.1.18-SNAPSHOT` producer artifact above.
- Suppressing provenance, selecting a manifest by enumeration order or creating
  a hash-derived identity/control mechanism.

## Split provenance

Phase 63.1 is the second and final child in the `PHASE-63 -> PHASE-63.1`
sequence applied on 2026-09-16. It receives the sole incoming authority handoff
recorded in its Phase Plan Gate. It uses `split_full_test_policy=each-phase`,
not the unavailable single-repository aggregate-validation mode.

## Release preparation evidence

MCML-63-04 is accepted in sbt-cozy repair commit
`3602386c534ffefa36c805d3bd779df3eb4b36f3` and the narrow driver-coordinate
commit `fa3e40ae7c6fd7c502ea4c7e88262b05488c2034`. Focused repair validation
`P631-MCML6304-LEGACY-OUTPUT-CLEANUP-VAL-004` has receipt
`ff75ee1ae0cbc290e1a590c8336eda1374d564d2410847c89c52101e7e9973c2`.
The exact local `publishLocal` artifact refresh is
`PUBLISH-005` / `2eb8812c4d67a2b3ae872c384620afa6702badaa0c45bef95b182fe9ada12001`,
and normal two-CML `EntityIdSpec` generation is
`P631-MCML6304-LEGACY-OUTPUT-CLEANUP-DRIVER-TEST-006` /
`adaad1b9e8da244e9463858240bc7484194d67a014bdae3abefc9c5231e536fb`.
The resulting two-input V2 provenance is
`61afa50c83b58029c99f083978242cc28ba2fa026b5fc6b6c40e60a01710a0cf`.

The independent Phase full review passed with disposition bundle
`89bb4d811173387cfd7c040c24c7f0b8339af212f8cf86c6e318269a00d97089`.
Each-phase full SBT validation succeeded for sbt-cozy with receipt
`1a2d59a1ca2eba1fb7cffbff8ed7a300917a8f078df24987997ddcc20712d333`, for
the simplemodeling-model driver with receipt
`7000984f014359ce2f8ef421a5649532be9bed1e5720f0faa7c04170f3367133`, and
for Cozy with receipt
`015c9019c7d9ba9a575aaf519a1ddb52c6afb4e03edb14ba1c5c5b4727e5698d`.
The final release binds these receipts and their verified documentation-only
freshness evidence. This record does not start, modify, publish, or close
Phase 74.1.

## References

- [Phase 63 contract predecessor](phase-63.md)
- [Phase 63.1 Checklist](phase-63.1-checklist.md)
- [DEV-031 journal record](../journal/2026/09/2026-09-16-multi-cml-generation-provenance-development-candidate.md)
- `sbt-cozy/src/main/scala/org/goldenport/cozy/CozyPlugin.scala`
