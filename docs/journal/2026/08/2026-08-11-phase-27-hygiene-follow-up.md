# Phase 27 Hygiene Follow-up

This journal is a non-normative ledger for existing debt found while closing
Phase 27. Entries here do not expand the completed phase or its closure
criteria.

## Open

### P27-HYG-001: Restore the Arcadia full-test dependency baseline

- Source: Phase 27 final gate on 2026-08-11, full-test invocation
  `45353-20260811T080703Z`.
- Classification: pre-existing Arcadia test/dependency hygiene; not caused by
  Phase 27.
- Observation: full `sbt --batch test` stops during Test compilation with 97
  ScalaTest API/dependency errors (`junit`, `WordSpec`, `Matchers`,
  `GivenWhenThen`, `JUnitRunner`, and DSL); no suites run.
- Separation evidence: Phase 27 main compiles under the focused command,
  final-tree `OptionalTagSpec` invocation `45635-20260811T080727Z` passes 4/4,
  SmartDox/Cozy full suites and the actual Part 5 Dox/Arcadia end-to-end
  acceptance pass, and independent review is PASS.
- Follow-up: align Arcadia's declared/effective ScalaTest/Scalactic baseline
  and existing test package/API imports in separate maintenance work.
- Phase impact: none; this item is not Phase 27 scope.
