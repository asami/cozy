# Phase 61 Checklist

Phase status: IN_PROGRESS
Updated: 2026-09-15
Scope: Subphase 61A, Contract Admission and Speech Normalization
Ledger for: [Phase 61](phase-61.md)
Successor ledger: [Phase 61.1](phase-61.1-checklist.md)
Repository-full validation: aggregate deferred to Phase 61.1

P610-01 is CLOSED and P610-02 remains OPEN.
Completion requires the linked artifacts and executable/observational
evidence, not planning text.

## P610-01: Contract and executable-spec admission

Stage Status:

- Current status: CLOSED
- Owner: Cozy video implementation owner
- Update rule: close only when every item in this section is checked with evidence

- [x] Promoted design/spec fixes speech-only middle-dot semantics, opt-in/default behavior, dictionary ordering and the exact admitted authoring fields.
- [x] Promoted timing contract distinguishes requested from effective trailing silence, preserves existing duration precedence, and excludes double padding and summary-hold reuse.
- [x] Nested-scene inheritance/explicit-zero override, supported round trips, validation and historical-manifest compatibility have executable specifications.
- [x] Given/When/Then and appropriate property-based specifications fix formula, zero/default, malformed-input and frame-quantization behavior before code changes.

Evidence: The verified focused SBT receipt locators are `P610-01A-VAL-003`:
`/tmp/skill.cncf.d/cncf-command-execution-e8ad8daef30a2df21f49c9d6983472a66708eed352f9d98b1c90b9f0be647c52-3b3e3a10acc9f79f4f7f4d773d9b3019/command-execution-sha256-f2ad240243af962b3454299309a4bd4e8255e42f832a2f0c2780d85331c021a3`
and `P610-01B-VAL-001`:
`/tmp/skill.cncf.d/cncf-command-execution-7a2de839ba98644eb8a6c59257516e4f34cbdcad891f2dd088214eb63ee73e60-a0c118ccee8a7e297735d3512f2b6b6d/command-execution-sha256-7cc57d1d55795d0cb58e0ecdb0b25fa262d9474a9c713466ba14b54c346321c7`.
The independent focused Step review passed with Review Disposition Bundle
`12b16f32735797b2737049ad0a3a0270c9246a48fc3326d6fbd5cc5bc311e4e8`.
This Step acceptance commit records the closure. No Phase closure or successor
implementation is claimed.

## P610-02: Speech-only middle-dot normalization

Stage Status:

- Current status: OPEN
- Owner: Cozy video implementation owner
- Update rule: close from provider-input and source-preservation specification evidence

- [ ] Enabled middle-dot policy removes U+30FB from original speech text and dictionary-generated readings without recursive substitution.
- [ ] Displayed line/caption/headings and article/glossary spelling are unchanged.
- [ ] Omitted/false settings preserve current Japanese/English behavior, other punctuation and existing whitespace policies.
- [ ] Invalid settings produce structured diagnostics before provider I/O.

## Successor handoff

P610-03 through P610-05 move exactly once to
[Phase 61.1](phase-61.1.md). They consume the committed P610-01/P610-02
contract handoff; no successor implementation begins through this checklist.
