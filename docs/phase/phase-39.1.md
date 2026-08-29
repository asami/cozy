# Phase 39.1: Cozy Launcher PDF Portability

Status: COMPLETE

Plan date: 2026-08-29

## Split From Phase 39

This ordered child was created on 2026-08-29 under the user-approved decision:
`Phase 39 を分割し、cozy-launcher portability を別 Phase にする`.
It starts only after Phase 39 has closed and consumes its accepted Cozy PDF
command contract. Phase 40 starts only after Phase 39.1 closes, unless the
active Phase order is changed by explicit authorization.

## Goal

Make `cozy pdf` select and configure the accepted Cozy runtime consistently
from every documented project working directory. Remove the package-root
workaround without duplicating configuration or changing Phase 39 command
semantics.

## Ownership and Evidence Boundary

- Primary implementation ownership is `/Users/asami/src/dev2026/cozy-launcher`.
- This repository may receive only the necessary Cozy integration changes after
  the Phase 39 handoff.
- `/Users/asami/src/Project2026/bok-knowledgehub` is a read-only acceptance
  driver. Evidence uses isolated temporary outputs only; it must not rewrite
  driver source or add local configuration adapters.
- Textus BoK verification, publication, upload, and downstream consumer
  acceptance are out of scope.

## Execution Scope Decision

On 2026-08-29, the user authorized
`/Users/asami/src/dev2026/cozy-launcher` as an admitted update root and the
primary implementation repository for Phase 39.1. This authorization does not
admit any other external repository: `/Users/asami/src/Project2026/bok-knowledgehub`
remains read-only, with isolated temporary outputs only.

## Runtime Path Semantics Decision

On 2026-08-29, the user approved `P391-DEC-RUNTIME-PATH-001`. A relative
`launcher.dev-dir`, `runtime.dev-dir`, `development.launcher.dev-dir`, or
`development.runtime.dev-dir` declared in `launcher.yaml` resolves from the
directory containing that declaration file. Absolute declaration paths remain
unchanged. The explicit `--runtime-dev-dir` selector, `COZY_RUNTIME_DEV_DIR`,
and `COZY_LAUNCHER_DEV_DIR` retain their invocation-working-directory semantics.
This decision changes no runtime-selection precedence.

## Stages

### PDF39.1-01: Launcher Runtime Discovery

Status: COMPLETE

Stage Status:

- Current status: COMPLETE
- Owner: `cozy-launcher` runtime configuration and discovery
- Update rule: the closure basis is `phase-39.1-checklist.md`; mark work
  complete only when selected-runtime discovery, configuration precedence, and
  failure diagnostics are specified and covered.

- Define the runtime configuration discovery order from each documented
  working directory.
- Select the configured Cozy runtime and classpath without an invocation-root
  dependency.
- Diagnose absent, invalid, and conflicting configuration deterministically.

Accepted evidence: launcher commit `7fedab6e00c146fcdcadc0089ac90e8a6517d097`
was accepted locally, and the post-fix `CozyLauncherSpec` passed 28 tests.

### PDF39.1-02: Working-Directory Path Semantics

Status: COMPLETE

Stage Status:

- Current status: COMPLETE
- Owner: `cozy-launcher` path-resolution portability
- Update rule: the closure basis is `phase-39.1-checklist.md`; mark work
  complete only when every documented CWD resolves the Phase 39 source/output
  contract identically and no package-root workaround is required.

- Reproduce and eliminate the project-root `Runtime / fullClasspath` failure.
- Support the repository root, media package root, and each documented project
  working directory through the same runtime-selection rules.
- Verify Phase 39 source identity and output semantics are preserved across the
  supported routes.

Accepted evidence: Cozy PDF focused Executable Specifications passed 8 tests;
successful isolated KnowledgeHub runs from the project root and package root
produced PDFs with equivalent content.

### PDF39.1-03: Read-Only Driver Acceptance and Closure

Status: COMPLETE

Stage Status:

- Current status: COMPLETE
- Owner: launcher portability validation and Phase closure
- Update rule: the closure basis is `phase-39.1-checklist.md`; mark work
  complete only when launcher specifications, isolated driver evidence,
  serialized validation, and independent review closure acceptance bullets are
  complete.

- Generate the KnowledgeHub architecture article PDF from each supported CWD
  using read-only driver inputs and isolated outputs.
- Verify the accepted Phase 39 command contract and equivalent artifact/output
  semantics without source rewrites or configuration adapters.
- Run serialized validation and independent Phase review before closure.

Current evidence includes the accepted launcher commit, its 28 passing
post-fix launcher tests, 8 passing Cozy PDF focused specifications, and
equivalent successful isolated project-root/package-root KnowledgeHub PDFs. The
initial serialized focused validation, comprehensive Phase review, and focused
closure re-review have completed. Final repository-full validation passed with
28 launcher tests before this release candidate is committed.

## Exclusions

- Image syntax, command help, format/profile semantics, and receipt meaning;
  Phase 39 owns and freezes those contracts first.
- Driver source or configuration mutation, Textus BoK verification, publish,
  upload, and downstream consumer acceptance.
- General launcher redesign beyond runtime discovery and path portability for
  this accepted PDF command contract.

## Structural Phase Plan Gate

State: PROCEED

- planning demand: bounded settled work after the Phase 39 handoff
- recommended parent profile: `gpt-5.6-terra / high`
- profile-cost role: lower-cost execution
- expensive reasoning kernel: none; the public PDF command contract is frozen
  by the completed predecessor.
- frozen handoff: accepted Phase 39 command grammar, source/output authority,
  and effective format/profile semantics.
- sub-four-hour merge analysis: not applicable; this Phase is estimated at
  5–7 hours.
- profile-cost-only split rejection: satisfied; distinct launcher repository
  ownership and a predecessor contract handoff require this ordered Phase.

## References

- `docs/phase/phase-39.md`
- `docs/phase/phase-39.1-checklist.md`
- `docs/phase/phase-40.md`
- `docs/strategy/cozy-development-strategy.md`
- `/Users/asami/src/Project2026/bok-knowledgehub/src/main/media/architecture/knowledgehub-component-architecture/review/cozy-gaps.yaml`
