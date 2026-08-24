# Phase 34 Development Candidates

This journal records the development candidate transferred from the blocked
Phase 34 acceptance boundary. It is not a normative specification.

## DEV-P34-001: Executable-spec source-fixture self-containment

- Status: RESOLVED
- Discovery: Phase 34 final official full test, 2026-08-24
- Repository: Cozy
- Evidence: focused acceptance invocation `52873-20260824T043314Z`
  (`testOnly cozy.CozyBokSpec cozy.CozyBokMetadataFinalizationSpec`) reported 16 succeeded, 0
  failed, and 0 aborted; the fresh focused re-review returned `FOCUSED_PASS`
  with no Current Boundary Blocker, Hygiene, or Development Candidate finding.
  Final official full-test wrapper invocation `58874-20260824T044406Z` reported
  1,369 total, 1,364 succeeded, 5 failed, 8 canceled, 99 suites completed,
  and 0 aborted; SBT and wrapper exit codes were 1 and the lock was released.
  All 5 failures are `cozy.bok.CozyBokSpec` actual-Build scenarios using the
  default `src/main/doxsite` without making its local fixture: configured
  Textus image, Arcadia, production direct assets, unrelated YAML/direct
  assets, and the default production RDF missing-artifact policy.
- Resolution evidence: CFB3 added local `src/main/doxsite` fixtures in all five
  actual-Build scenarios without changing production behavior or adding an
  external project dependency. Valid focused invocation
  `85555-20260824T054332Z`, exact command `testOnly cozy.bok.CozyBokSpec`,
  reported 60 succeeded, 0 failed, 0 canceled, one suite, SBT and wrapper exit
  codes 0, and the lock released. The fresh focused re-review returned
  `FOCUSED_PASS` with no Current Boundary Blocker, Hygiene, or Development
  Candidate finding and preserved CFB2 safe absence at configuration time plus
  strict actual-Build admission. The earlier full-test fixture failure is
  superseded; no new full-suite test has been run or passed.
- Required future behavior: preserve `CB-P34-CFB2-001` safe in-project source
  admission at configuration time and strict actual-Build admission. Correct
  only the executable-spec source fixtures to be self-contained local safe
  sources, or revise the stated behavior after rules/spec/design work.
- Scope and risk: this is an executable-spec source-fixture self-containment
  boundary, not a relaxation of Cozy `BuildConfig.create` actual-Build
  admission. The regression currently prevents five actual-Build scenarios from
  reaching their intended checks.
- Boundary: do not weaken lexical, canonical, symlink, outside-root, or strict
  actual-Build rejection; do not invoke SmartDox, Textus, a downstream site
  build, external SimpleModeling.org sources, or a project workflow as a
  substitute. SmartDox/Textus consumer acceptance stays separate.
- Target: Phase 34 final validation; the former Phase 35 target is superseded
  by CFB3.
- Execution order: rules -> spec -> design -> code, followed by focused and
  final validation when this candidate is admitted for implementation.
- Current action: CFB3 resolves this candidate in Phase 34. Phase 34 remains
  pending its final full validation and release decision. No Phase 35
  implementation, separate workaround, commit, publication, or deployment is
  made here.
