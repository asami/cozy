# Phase 35: BoK Executable-Spec Source Fixture Self-Containment

Status: SUPERSEDED

Plan date: 2026-08-24

## Goal

Correct the Phase 34 final-gate executable specifications so actual-Build
scenarios create self-contained local safe source fixtures, or revise the
stated behavior only after the rules/spec/design boundary is approved. Preserve
the CFB2 configuration-time safe-absence admission and strict actual-Build
source admission.

## Boundary and invariants

- Preserve CFB2's safely absent in-project source admission at configuration
  time only.
- Preserve strict actual-Build admission of an existing, non-symbolic source
  before source reads, runner calls, or output mutation.
- Make only executable-spec source fixtures self-contained local safe sources,
  unless rules/spec/design work first changes the stated behavior.
- Specify the exact future behavior in rules, spec, and design before any code
  work begins.
- Do not weaken lexical, canonical, symlink, or outside-root rejection.
- Do not use a downstream site build, SmartDox/Textus execution, or project
  workflow as a workaround.
- Keep SmartDox/Textus consumer acceptance separate from this Cozy boundary.

## Stages

### BOK35-01: Executable-Spec Fixture Specification

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy BoK configuration and specification
- Checklist basis: `BOK35-01`

Define the exact fixture requirements for actual-Build executable scenarios and
record the preserved CFB2 distinction between configuration-time safe absence
and strict actual-Build source admission. No implementation is started by this
stage record.

### BOK35-02: Fixture Design and Correction

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy BoK configuration and BuildConfig boundary
- Checklist basis: `BOK35-02`

Apply the approved rules/spec/design boundary to the Phase 34 executable-spec
fixture regression while retaining all unsafe, symlink, and outside-root
rejection invariants. This stage is not started.

### BOK35-03: Focused and Full Validation

Stage Status:

- Current status: NOT STARTED
- Owner: Cozy validation and review
- Checklist basis: `BOK35-03`

Prove self-contained actual-Build fixtures, preserved configuration-time
safe-absence admission, and strict actual-Build rejection, then run the
required focused and final full validation before any future closure or
release claim. This stage is not started.

## Scope and risk

The historical triggering evidence was final official full-test wrapper invocation
`58874-20260824T044406Z`: 1,369 total, 1,364 succeeded, 5 failed, 8 canceled,
99 suites completed, and 0 aborted; SBT and wrapper exit codes were 1 and the
lock was released. CFB3 subsequently added local `src/main/doxsite` fixtures
to all five actual-Build scenarios in P34, with no production behavior or
external project dependency change. Its valid focused evidence is
`85555-20260824T054332Z`, `testOnly cozy.bok.CozyBokSpec`, 60 succeeded, 0
failed, 0 canceled, one suite, SBT and wrapper 0, lock released; fresh focused
re-review was `FOCUSED_PASS` with no findings. The five historical failures
are superseded, and no new full-suite test is claimed.
The risk is that fixture correction could accidentally weaken strict source
admission or alter the CFB2 configuration-time exception.

## Dependencies and exclusions

- Phase 34 remains in progress pending its final full validation; CFB3
  superseded this proposed successor scope in P34.
- Phase 29 remains closed; its transferred correction is in progress in Phase
  34 awaiting final validation.
- SmartDox Phase 8 `LITERAL8-03` and Textus BoK consumer acceptance remain
  separate boundaries.
- No Phase 35 code, executable specification, commit, publication, or
  deployment was started. The former planning items remain unexecuted history;
  no successor implementation is authorized by this superseded record.

## References

- `docs/phase/phase-35-checklist.md`
- `docs/phase/phase-34.md`
- `docs/phase/phase-34-checklist.md`
- `docs/journal/2026/08/2026-08-24-phase-34-development-candidates.md`
