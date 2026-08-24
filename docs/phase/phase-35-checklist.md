# Phase 35 Checklist: BoK Executable-Spec Source Fixture Self-Containment

This checklist is the authoritative progress ledger for superseded Phase 35. It
is not a normative contract.

## BOK35-01: Executable-Spec Fixture Specification

Status: SUPERSEDED

- [ ] Record the exact local fixture rules for actual-Build executable
      scenarios.
- [ ] Record the preserved CFB2 distinction: safe in-project absence is allowed
      only at configuration time, while actual Build remains strict.
- [ ] Add or revise the Given/When/Then executable specification boundary only
      after the rules and specification are approved.

## BOK35-02: Fixture Design and Correction

Status: NOT STARTED

- [ ] Record the design boundary for the Phase 34 actual-Build fixture
      regression without prescribing a solution in this planning record.
- [ ] Implement only self-contained local safe fixtures, or an approved
      rules/spec/design behavior correction, without weakening lexical,
      canonical, symlink, outside-root, or strict actual-Build rejection.

## BOK35-03: Focused and Full Validation

Status: NOT STARTED

- [ ] Validate local self-contained actual-Build fixtures, preserved
      configuration-time safe absence, and strict unsafe-source rejection with
      focused evidence.
- [ ] Run the final official full Cozy test and record exact counts, exit
      codes, and lock state.
- [ ] Complete independent review and release gating before changing any phase
      status to closed or released.

Phase 35 is superseded by CFB3, which resolved its proposed fixture scope in
Phase 34. All items remain intentionally unchecked and unexecuted; no
successor implementation was started by this checklist. SmartDox/Textus
consumer acceptance remains outside this Phase.
