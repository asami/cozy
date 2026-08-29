# Phase 39: PDF Command Contract and Semantics

Status: COMPLETE

Plan date: 2026-08-29

Dependency: Phase 38 closure or explicit authorization to change the active
Phase order.

## Split Record

On 2026-08-29, the user approved: `Phase 39 を分割し、cozy-launcher
portability を別 Phase にする`. The original Phase 39 had no completed
history. Its former runtime/current-working-directory and driver-acceptance
scope moves exclusively to ordered child Phase 39.1.

The unsplit estimate was 11–16 hours. This source Phase is 6–8 hours and
Phase 39.1 is 5–7 hours. The split adds a plan, review, and release handoff,
but avoids conflating Cozy's public PDF command contract with the separate
`cozy-launcher` runtime-discovery responsibility. This is a boundary and time
split, not a profile-cost split.

## Goal

Make Cozy PDF command invocation predictable, with discoverable help and
unambiguous format/profile terminology. Phase 39 defines the accepted command
and source/output option contract that Phase 39.1 consumes without redefining
it.

## Origin

The KnowledgeHub architecture article PDF was generated successfully with the
LuaLaTeX business format, but the production workflow exposed usability gaps.
Manually written heading numbers are not included in this Phase: source
headings remain unnumbered and the renderer owns automatic numbering.

## Scope Removal Record: Image Syntax and PDF Receipts

Markdown image admission, image-model normalization, PDF image-output
acceptance, and a PDF receipt contract are outside Phase 39 by the user
decision on 2026-08-29. They are not a Phase 39 stage or acceptance condition.
The decision record is
`docs/journal/2026/08/2026-08-29-phase-39-image-receipt-deferral.md`; the
separate future Development Candidate is `DEV-012`.

## Stages

### PDF39-02: Command Help Contract

Stage Status:

- Current status: COMPLETE
- Checklist basis: docs/phase/phase-39-checklist.md#PDF39-02
- Owner: Cozy PDF command help and launcher compatibility contract
- Update rule: mark work complete only when the PDF help route, options,
  examples, deterministic errors, and compatibility acceptance bullets are
  complete.

- Make `cozy pdf --help` and the equivalent help route display PDF command
  usage instead of interpreting `--help` as an input document.
- Document required input, output, renderer, format, and profile options with
  representative examples and deterministic error behavior.
- Preserve top-level Cozy help and the established launcher invocation surface.

### PDF39-04: Format and Profile Semantics

Stage Status:

- Current status: COMPLETE
- Checklist basis: docs/phase/phase-39-checklist.md#PDF39-04
- Owner: Cozy PDF format/profile command model
- Update rule: mark work complete only when format/profile relationships, value
  validation, diagnostics, and compatibility acceptance bullets are complete.

- Separate or unify `--latex-format` and media `--profile` through one explicit
  command model; do not reuse a value such as `business` across unrelated
  namespaces without a declared mapping.
- Provide enumeration, validation, help, and diagnostics for supported values.
- Define compatibility behavior for existing commands before changing their
  accepted option surface.

### PDF39-05: Command Contract Validation and Closure

Stage Status:

- Current status: COMPLETE
- Checklist basis: docs/phase/phase-39-checklist.md#PDF39-05
- Owner: Cozy PDF command-contract validation and Phase closure
- Update rule: complete only with focused specifications, final serialized
  Cozy validation, independent Phase review, and the distinct local release
  commit. No release commit is permitted if the final validation fails.

- Add focused Executable Specifications for the Phase 39 command contracts.
- Verify the focused command help, option validation, and compatibility
  contracts through Cozy-owned evidence. Artifact-level image, layout, and
  receipt-identity acceptance is outside this Phase.
- Run full serialized Cozy validation and complete independent Phase review
  before closure. Final serialized invocation `12823-20260829T025348Z`
  recorded 1,532 successful tests, 0 failures, 8 canceled tests, 117 completed
  suites, and 0 aborted suites. The local release commit does not publish,
  push, or activate Phase 39.1.

## Handoff to Phase 39.1

After Phase 39 closes, Phase 39.1 consumes its accepted PDF grammar,
output-path contract, and effective format/profile semantics. Phase 39.1 alone
owns launcher runtime discovery and cross-working-directory execution. It must
not reopen or redefine this Phase's command semantics.

## Exclusions

- Working-directory portability, selected-runtime discovery, launcher
  classpath resolution, and the project-root `Runtime / fullClasspath` failure;
  these are exclusively Phase 39.1.
- KnowledgeHub source rewrites, local configuration adapters, or mutation of
  the external driver repository.
- Handwritten heading numbering in SmartDox or Markdown source.
- Markdown image admission, image-model normalization, PDF image-output
  acceptance, and a PDF receipt schema or persistence mechanism; these are
  deferred as `DEV-012` and must not be approximated with the existing Cozy
  Media receipt.
- Changes to article content, presentation generation, video production,
  publication, upload, or downstream consumer acceptance.

## Deferred Development Candidate

### DEV-012: SmartDox Image Admission and PDF Receipt Integration

Source: User scope decision on 2026-08-29 resolving `P39-DEC-IMAGE-RECEIPT-001`.

- Current-Phase disposition: deferred; not an acceptance condition for Phase
  39.
- Owner: SmartDox owns Markdown image parsing and the normalized image model;
  a later Cozy integration Phase may consume an accepted SmartDox contract.
- Dependency: an accepted SmartDox parser/image-model contract and a separately
  designed PDF receipt contract, if receipt persistence remains required.
- Prohibited local workaround: Cozy must not preprocess Markdown images, create
  a shadow image model, or represent PDF execution as a Cozy Media receipt.
- Resume condition: explicit invocation of the owner Phase after its scope,
  public contract, and repository boundary are planned.

## Structural Phase Plan Gate

State: PROCEED

- planning demand: protected decision
- recommended parent profile: `gpt-5.6-terra / xhigh`
- profile-cost role: expensive reasoning kernel
- frozen handoff: accepted Cozy PDF command contract for Phase 39.1
- sub-four-hour merge analysis: not applicable; this Phase is estimated at
  6–8 hours.
- profile-cost-only split rejection: satisfied; repository ownership and
  accepted public-contract handoff require the split.

## References

- `docs/phase/phase-39-checklist.md`
- `docs/phase/phase-39.1.md`
- `docs/strategy/cozy-development-strategy.md`
- `/Users/asami/src/Project2026/bok-knowledgehub/src/main/media/architecture/knowledgehub-component-architecture/review/cozy-gaps.yaml`
