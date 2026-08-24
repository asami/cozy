# Phase 29 Development Candidates

This journal records non-blocking follow-up work transferred from the closed
Phase 29 acceptance boundary. It is not a normative specification.

## DEV-P29-001: BoK metadata input admission hardening

- Status: RESOLVED
- Discovery: Phase 29 full review, 2026-08-23
- Repository: Cozy
- Evidence: `CPB-29-01` found that configured `bok.source` admission did not
  yet prove canonical project-root and symlink-safe containment before it
  influenced glossary/RDF decisions. `CPB-29-02` found that glossary and
  component-reference resources were not unconditionally validated before
  staging or manifest declaration. Phase 34 provisionally addressed both
  findings in Cozy commits `e1a6416457d5692513741f686c7e1a34a56d390e` and
  `c464d7788e939d4987c79250c27df76ee40b626d`, with normal closure repair
  `CPB-BOK34-001` and exceptional closure repair `CB-P34-CFB2-001` accepted in
  the current working tree. This focused evidence is provisional pending final
  full-suite acceptance.
- Resolution evidence: official focused invocation
  `52873-20260824T043314Z`, `testOnly cozy.CozyBokSpec cozy.CozyBokMetadataFinalizationSpec`, 16
  succeeded, 0 failed, 0 aborted; SBT 0, wrapper 0, lock released. The fresh
  focused re-review returned `FOCUSED_PASS` with no Current Boundary Blocker,
  Hygiene, or Development Candidate finding and accepted the CFB2 distinction:
  safe in-project absence is allowed only at configuration time, while actual
  Build admission remains strict. The final official full Cozy test wrapper
  invocation `58874-20260824T044406Z` then reported 1,369 total, 1,364
  succeeded, 5 failed, 8 canceled, 99 suites completed, and 0 aborted; SBT and
  wrapper exit codes were 1 and the lock was released. All 5 failures are
  `cozy.bok.CozyBokSpec` actual-Build scenarios using the default
  `src/main/doxsite` without making its local fixture: configured Textus image,
  Arcadia, production direct assets, unrelated YAML/direct assets, and the
  default production RDF missing-artifact policy. CFB3 then added local
  `src/main/doxsite` fixtures in all five actual-Build scenarios without
  changing production behavior or adding an external project dependency.
  Valid focused evidence is invocation `85555-20260824T054332Z`, exact command
  `testOnly cozy.bok.CozyBokSpec`, with 60 succeeded, 0 failed, 0 canceled,
  one suite, SBT and wrapper exit codes 0, and the lock released. The fresh
  focused re-review returned `FOCUSED_PASS` with no findings and preserved the
  CFB2 configuration-time safe absence and strict actual-Build admission. The
  earlier full-test fixture failure is superseded. Candidate final full Cozy
  `test` receipt `22693-20260824T094231Z` reported 1,376 succeeded, 0 failed,
  8 canceled, 100 suites, and 0 aborted; SBT and wrapper exit codes were 0 and
  the lock was released.
- Boundary retained: Phase 29 remains closed with its no-site-build,
  no-SmartDox-workaround, and no downstream acceptance claim unchanged. The
  candidate final full Cozy `test` receipt
  `22693-20260824T094231Z` passed with 1,376 succeeded, 0 failed, 8 canceled,
  100 suites, and 0 aborted; SBT and wrapper exit codes were 0 and the lock was
  released.
- Owner and target: Cozy Phase 34, `BOK34-01` and `BOK34-02` (closed after CFB3
  and the candidate final full validation; the former Phase 35 fixture scope
  is superseded by CFB3).
- Dependency: Phase 29 public finalization contract; no SmartDox generated-site
  result is a substitute for the Cozy-side admission checks.
- Risk: malformed or unsafe configured source/resource input can influence a
  metadata handoff before rejection.
- Resolution: invoke Phase 34 with this record and the sealed Phase 29 review
  ledger; focused implementation, CFB3 fixture correction, review, and the
  candidate final full-suite receipt close the transferred correction in Phase
  34. Phase 29 remains closed. No SmartDox/Textus consumer execution or
  acceptance is claimed. External SimpleModeling.org, SmartDox, and Textus
  sources are not substitutes.
- Prohibited local workaround: do not relax finalizer validation, reconstruct
  missing data, or invoke a site build to hide the admission failures.
