# Phase 33 Checklist: Declared Cozy Runtime Selection for CAR Publication

This checklist is the authoritative progress ledger for Phase 33. It is not a
normative contract.

## RT33-01: Runtime-Selection Contract

Status: DONE

- [x] RT33-01A: remove the generated scaffold's direct runtime command and
      select a CAR/SAR Coursier delegate from `project.yaml build.cozyVersion`,
      rejecting a project-local `.cozy generation.delegate.command`; accepted
      in `sbt-cozy` commit `68e03101dabbef75662ad74859ecbcb114e7b207` and
      Cozy commit `4e1539c9d54532fed7180cd6d00efc4581c08e04`.

- [x] RT33-01B: standard `publish` and `publishLocal` evaluate the selected
      Coursier command in the no-`.cozy` CAR fixture; accepted in sbt-cozy
      commit `2f306c119474103dc442ac0e521ba0bda97d1f0d`.

- [x] Define the exact handoff from `project.yaml build.cozyVersion` to the
      Cozy Launcher runtime-selection argument for every CAR publish delegate.
- [x] Ensure a CAR's declared version wins over project-local and ambient
      `.cozy` runtime defaults without deleting or mutating those defaults.
- [x] Reject an unavailable or mismatched requested runtime before generation,
      package, or publication output is created.
- [x] Retain explicit diagnostics for contradictory CAR declaration and
      delegate configuration values.

## RT33-02: CAR Publication Acceptance

Status: DONE

- [x] RT33-02C: a host/child scripted fixture accepts the rejected local
      delegate command and proves the child warehouse remains absent; accepted
      in sbt-cozy commit `1513b7de9272d0a15fb71f22b1bf75597e9d1063`.

- [x] Add focused launcher and sbt-cozy evidence for a development CAR
      `publishLocal` path with no `.cozy` runtime override.
- [x] Add focused launcher and sbt-cozy evidence for an admitted release CAR
      `publish` path with no `.cozy` runtime override.
- [x] Prove that contradictory local runtime defaults cannot select a different
      Cozy runtime, and that no warehouse output is written on selection
      failure.
- [x] CB-P33-003: update the CAR archive-admission executable specification
      from the removed scaffold-local delegate command to the Phase 33
      `project.yaml`-owned runtime contract; focused validation
      `64828-20260821T024826Z` passed 11 of 11 and the focused re-review
      cleared the blocker.
- [x] Preserve existing CNCF descriptor, generation-provenance, CAR archive,
      and catalog admission checks in both paths; `BridgeContractSpec`,
      `Phase51Cv05GenerationProvenanceSpec`, and the repaired
      `CozyArchivePackagerCv06Spec` provide the Cozy-side proof, while
      `sbt-cozy` descriptor/catalog suites and scripted fixtures remain green.
- [x] CB-P33-004: update the current `@version` metadata in the modified
      `sbt-cozy` production source and executable specification according to
      the repository source-history rule, without changing their runtime
      contract; accepted by static verification and focused re-review.
- [x] Complete the final Phase review and release gate. The accepted
      cross-repository coordinates are CAR runtime `0.3.1-SNAPSHOT` from
      `project.yaml` and `sbt-cozy` `0.1.17-SNAPSHOT`; the normal scripted
      fixture proved them for both `publish` and `publishLocal` without a
      project-local `.cozy` directory. The final full suites are frozen as
      the immediate acceptance gate for the Phase's repository-local commits.

Phase 33 closes only when CAR publication selects the exact declared Cozy
runtime without `.cozy` version overrides, and the focused cross-repository
evidence proves that no compatibility guard was weakened.
