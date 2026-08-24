# Phase 34 Checklist: BoK Metadata Input Admission Hardening

This checklist is the authoritative progress ledger for Phase 34. It is not a
normative contract.

## BOK34-01: Configured Source-Root Admission

Status: DONE

- [x] Define canonical project-root and symlink-safe admission for configured
      BoK source paths.
- [x] Reject an unsafe, outside-root, non-directory, or symlinked source before
      it can influence glossary or RDF finalization.
- [x] Reject an unsafe configured source at ordinary build entry before source
      reads, runner calls, or output mutation, with the admitted path carried
      through a project-relative build configuration.
- [x] Add Given/When/Then executable specifications for admitted and rejected
      source-path variants, including finalization and ordinary-build failure
      atomicity.
- [x] Validate normal closure repair `CPB-BOK34-001` and exceptional closure
      repair `CB-P34-CFB2-001` through the official focused M2 evidence and one
      permitted focused re-review, preserving safe-absence-at-configuration
      admission and strict actual-Build admission.
- [x] Pass the final official full Cozy test after CFB3: receipt
      `22693-20260824T094231Z`, command `test`, 1,376 succeeded, 0 failed,
      8 canceled, 100 suites, 0 aborted; SBT and wrapper exit codes 0, lock
      released.

## BOK34-02: Published Resource Validation

Status: DONE

- [x] Validate glossary metadata with its canonical decoder before staging or
      KnowledgeSource manifest declaration.
- [x] Validate every component-reference resource before staging or manifest
      declaration, including indexes without current RDF references.
- [x] Add Given/When/Then executable specifications for malformed glossary and
      component-reference resources and unchanged output after rejection.

## Acceptance and transfer record

`BOK34-01` and `BOK34-02` source/resource correction implementation was
provisionally accepted in Cozy commits
`e1a6416457d5692513741f686c7e1a34a56d390e` and
`c464d7788e939d4987c79250c27df76ee40b626d`. The normal closure repair
`CPB-BOK34-001` and exceptional closure repair `CB-P34-CFB2-001` are
provisionally accepted in the current working tree.

Official focused evidence after both repairs: invocation
`52873-20260824T043314Z`, `testOnly cozy.CozyBokSpec cozy.CozyBokMetadataFinalizationSpec`, 16
succeeded, 0 failed, 0 aborted; SBT 0, wrapper 0, lock released. The fresh
focused re-review returned `FOCUSED_PASS` with no Current Boundary Blocker,
Hygiene, or Development Candidate finding. It accepted that a safely absent
in-project source is allowed only during configuration resolution, while
actual Build admission remains strict; direct Build APIs admit before
bibliography/runner/mutation, and tests cover CLI/config plus direct-build
paths. `HYG-BOK34-02-001` (pre-existing flat executable-spec organization)
remains nonblocking and open for separate hygiene-only follow-up; it is not
changed.

CFB3 added local `src/main/doxsite` fixtures in the five
`cozy.bok.CozyBokSpec` actual-Build scenarios without changing production
behavior or adding an external project dependency. Valid focused evidence is
invocation `85555-20260824T054332Z`, exact command
`testOnly cozy.bok.CozyBokSpec`, with 60 succeeded, 0 failed, 0 canceled, one
suite, SBT and wrapper exit codes 0, and the lock released. The fresh focused
re-review returned `FOCUSED_PASS` with no Current Boundary Blocker, Hygiene, or
Development Candidate finding and preserved CFB2 safe absence at configuration
time plus strict actual-Build admission.

The earlier official full Cozy test wrapper invocation
`58874-20260824T044406Z` reported 1,369 total, 1,364 succeeded, 5 failed, 8
canceled, 99 suites completed, and 0 aborted; SBT and wrapper exit codes were
1 and the lock was released. All 5 failures are `cozy.bok.CozyBokSpec`
actual-Build scenarios using the default `src/main/doxsite` without making its
local fixture: configured Textus image, Arcadia, production direct assets,
unrelated YAML/direct assets, and the default production RDF missing-artifact
policy. Strict actual-Build admission remains correct. The earlier full-test
fixture failure is superseded by CFB3; no new full-suite test has been run or
passed. The former Phase 35 fixture scope is superseded in P34; its unexecuted
planning records are retained for history. The candidate
final full Cozy `test` receipt `22693-20260824T094231Z` reported 1,376
succeeded, 0 failed, 8 canceled, 100 suites, and 0 aborted; SBT and wrapper
exit codes were 0 and the lock was released. Phase 34 and BOK34-01 are closed;
BOK34-02 remains DONE. The Phase 34 hygiene journal is unchanged.

No Textus/SmartDox consumer execution or acceptance is claimed.
