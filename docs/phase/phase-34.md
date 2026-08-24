# Phase 34: BoK Metadata Input Admission Hardening

Status: CLOSED

Plan date: 2026-08-23

## Goal

Harden the public BoK metadata finalization boundary so every configured source
and every published glossary or component-reference resource is admitted and
validated before it can affect a KnowledgeSource manifest.

This is the explicitly separated correction Phase for Phase 29 review findings
`CPB-29-01` and `CPB-29-02`. No correction is implemented by this planning
record.

## Boundary and invariants

- Canonically resolve configured BoK source paths relative to the selected
  project and reject unsafe, outside-root, or symlinked source paths before
  glossary or RDF decisions are made.
- Admit the configured source at the ordinary `bok build` public entry before
  any source read, runner call, or output mutation, and route build consumers
  through the admitted project-relative configuration.
- At configuration time, permit only a safely absent in-project source as
  authorized by `CB-P34-CFB2-001`; an actual Build remains strict and requires
  its configured source to be an existing, non-symbolic directory before any
  source read, runner call, or output mutation.
- Validate glossary metadata with its canonical decoder before staging or
  declaring the resource.
- Validate every component-reference resource before staging or declaring it,
  even when no current RDF graph node refers to a component.
- Preserve the public `cozy bok finalize-metadata` command, its metadata
  allowlist, deterministic output, and failure-atomic update behavior.
- Preserve the Phase 29 boundary: do not invoke SmartDox, Dox, Antora,
  Arcadia, Docker, media processing, project workflows, publication, upload,
  or deployment.

## Stages

### BOK34-01: Configured Source-Root Admission

Stage Status:

- Current status: DONE
- Owner: Cozy BoK configuration and SIE metadata finalization
- Update rule: mark work complete only from the Phase 34 checklist.
- Checklist basis: `BOK34-01`

Specify, implement, and cover canonical project-root source admission so an
outside-root or symlinked configured source cannot influence finalization or
ordinary build orchestration.

The normal closure repair `CPB-BOK34-001` corrected the ordinary direct-copy
build path. Exceptional closure repair `CB-P34-CFB2-001` (2026-08-24) permits a
safely absent in-project `bok.source` only while `BuildConfig.create` resolves
configuration, before reading that source's `site.conf`; actual Build remains
strict. It also adds the public command/config-path regression. The official
focused evidence after both repairs is invocation `52873-20260824T043314Z`,
`testOnly cozy.CozyBokSpec cozy.CozyBokMetadataFinalizationSpec`, with 16 succeeded, 0 failed, and 0
aborted; SBT and wrapper exit codes were 0 and the lock was released. The fresh
focused re-review returned `FOCUSED_PASS` with no Current Boundary Blocker,
Hygiene, or Development Candidate finding. CFB3 then added local
`src/main/doxsite` fixtures to the five actual-Build scenarios in
`cozy.bok.CozyBokSpec`, without changing production behavior or adding an
external project dependency. Its valid focused evidence is invocation
`85555-20260824T054332Z`, exact command `testOnly cozy.bok.CozyBokSpec`, with
60 succeeded, 0 failed, 0 canceled, one suite, SBT and wrapper exit codes 0,
and the lock released. The fresh focused re-review returned `FOCUSED_PASS`
with no findings and preserved CFB2 safe absence at configuration time plus
strict actual-Build admission. The candidate final full Cozy `test` receipt
`22693-20260824T094231Z` reported 1,376 succeeded, 0 failed, 8 canceled, 100
suites, and 0 aborted; SBT and wrapper exit codes were 0 and the lock was
released. This closes BOK34-01 and Phase 34. No Textus/SmartDox consumer
execution or acceptance is claimed.

### BOK34-02: Published Resource Validation

Stage Status:

- Current status: DONE
- Owner: Cozy BoK/SIE metadata
- Update rule: mark work complete only from the Phase 34 checklist.
- Checklist basis: `BOK34-02`

Specify, implement, and cover unconditional glossary and component-reference
resource validation before staging or manifest declaration.
Validation covers both staged finalization and the shared direct-copy build route
before copying or manifest declaration, even without RDF component references.
The focused resource-validation acceptance remains provisional pending the
Phase 34 final full-suite gate and does not release this Phase.

## Completion criteria

- Unsafe, outside-root, or symlinked configured sources fail before any
  metadata mutation, ordinary build runner call, or ordinary build output
  mutation.
- Malformed glossary and component-reference resources fail even when no graph
  node currently references them.
- Every failure remains atomic and produces a diagnostic that identifies the
  rejected source or resource.
- Executable specifications cover success, failure, and unchanged-output cases
  with Given/When/Then structure.
- Focused Cozy validation and review converge without weakening Phase 29
  finalization or its non-site-build boundary.
- The candidate final full Cozy `test` after CFB3 is receipt
  `22693-20260824T094231Z`: 1,376 succeeded, 0 failed, 8 canceled, 100
  suites, 0 aborted; SBT and wrapper exit codes were 0 and the lock was
  released. This closes Phase 34 and BOK34-01; no SmartDox/Textus consumer
  execution or acceptance is claimed.

## Dependencies

- Phase 29 public finalization contract and full-review findings
  `CPB-29-01` / `CPB-29-02`.
- SmartDox Phase 8 `LITERAL8-03` remains a separate generated-site acceptance
  dependency and is not an input-admission workaround.

## Closure evidence

- BOK34-01 and BOK34-02 source/resource correction implementation was
  provisionally accepted in Cozy commits
  `e1a6416457d5692513741f686c7e1a34a56d390e` and
  `c464d7788e939d4987c79250c27df76ee40b626d`.
- Normal closure repair `CPB-BOK34-001` and exceptional closure repair
  `CB-P34-CFB2-001` are provisionally accepted in the current working tree.
  The official focused evidence after both repairs is invocation
`52873-20260824T043314Z`: `testOnly cozy.CozyBokSpec cozy.CozyBokMetadataFinalizationSpec`, 16
  succeeded, 0 failed, 0 aborted; SBT 0, wrapper 0, lock released. The fresh
  focused re-review returned `FOCUSED_PASS` with no Current Boundary Blocker,
  Hygiene, or Development Candidate finding. It accepted the CFB2 distinction:
  a safely absent in-project source is permitted only at configuration time,
  while actual Build admission remains strict.
- Final official full-test invocation `58874-20260824T044406Z` reported 1,369
  total, 1,364 succeeded, 5 failed, 8 canceled, 99 suites completed, and 0
  aborted; SBT and wrapper exit codes were 1 and the lock was released. All 5
  failures are `cozy.bok.CozyBokSpec` actual-Build scenarios using the default
  `src/main/doxsite` without making its local fixture: configured Textus image,
  Arcadia, production direct assets, unrelated YAML/direct assets, and the
  default production RDF missing-artifact policy. Strict actual-Build
  admission remains correct; the deferred correction is limited to making
  executable-spec source fixtures self-contained local safe sources, or
  revising the stated behavior only after rules/spec/design work. CFB3
  superseded that fixture-only failure in P34. The candidate final full test
  receipt below provides the closure validation.
- CFB3 added local `src/main/doxsite` fixtures in the five
  `cozy.bok.CozyBokSpec` actual-Build scenarios. It made no production behavior
  or external project dependency change. Valid focused evidence is invocation
  `85555-20260824T054332Z`, exact command `testOnly cozy.bok.CozyBokSpec`, with
  60 succeeded, 0 failed, 0 canceled, one suite, SBT and wrapper exit codes 0,
  and the lock released. The fresh focused re-review returned `FOCUSED_PASS`
  with no Current Boundary Blocker, Hygiene, or Development Candidate finding;
  it preserved CFB2 safe absence at configuration time and strict actual-Build
  admission.
- The candidate full Cozy `test` receipt `22693-20260824T094231Z`, command
  `test`, reported 1,376 succeeded, 0 failed, 8 canceled, 100 suites, and 0
  aborted; SBT and wrapper exit codes were 0 and the lock was released. Phase
  34 and BOK34-01 are closed; BOK34-02 remains DONE. No SmartDox/Textus
  consumer execution or acceptance is claimed.

## References

- `docs/phase/phase-34-checklist.md`
- `docs/phase/phase-29.md`
- `docs/spec/bok-metadata-finalization.md`
- `docs/design/bok-metadata-finalization.md`
- `src/main/scala/cozy/bok/CozyBokSieMetadata.scala`
