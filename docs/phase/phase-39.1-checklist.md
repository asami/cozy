# Phase 39.1 Checklist: Cozy Launcher PDF Portability

This checklist is the authoritative progress ledger for Phase 39.1. It is not
a normative behavior contract.

Phase Status: COMPLETE

Predecessor: Phase 39 must close with its accepted PDF command contract before
Phase 39.1 starts. Phase closure is authoritative only when its final release
commit succeeds.

Decision: `P391-DEC-RUNTIME-PATH-001` is accepted. Relative checkout
directories declared in `launcher.yaml` resolve from their declaring file;
absolute declaration paths and CLI/environment selector precedence remain
unchanged.

## PDF39.1-01: Launcher Runtime Discovery

Status: COMPLETE

Stage Status:

- Current status: COMPLETE
- Owner: `cozy-launcher` runtime configuration and discovery
- Update rule: mark work complete only when all PDF39.1-01 acceptance bullets
  below are checked.

- [x] Specify runtime-configuration discovery and precedence from every
      documented project working directory.
- [x] Select the configured Cozy runtime and classpath independent of the
      invocation root.
- [x] Cover missing, invalid, and conflicting configuration with deterministic
      diagnostics and Executable Specifications.

## PDF39.1-02: Working-Directory Path Semantics

Status: COMPLETE

Stage Status:

- Current status: COMPLETE
- Owner: `cozy-launcher` path-resolution portability
- Update rule: mark work complete only when all PDF39.1-02 acceptance bullets
  below are checked.

- [x] Reproduce the project-root `Runtime / fullClasspath` failure.
- [x] Eliminate the failure without a package-root workaround.
- [x] Verify repository root, media package root, and each other documented
      project CWD use the same accepted runtime-selection rule.
- [x] Verify Phase 39 source identity and output semantics remain equivalent
      across all supported CWD routes.

## PDF39.1-03: Read-Only Driver Acceptance and Closure

Status: COMPLETE

Stage Status:

- Current status: COMPLETE
- Owner: launcher portability validation and Phase closure
- Update rule: mark work complete only when all PDF39.1-03 acceptance bullets
  below are checked.

- [x] Run focused launcher and Cozy integration Executable Specifications.
- [x] Generate the KnowledgeHub architecture article PDF from each supported
      CWD using read-only inputs and isolated temporary outputs.
- [x] Verify the frozen Phase 39 command contract and equivalent artifact/output
      semantics without driver source rewrites or local adapters.
- [x] Run serialized validation and complete independent Phase review before
      closure.

Phase 39.1 is COMPLETE as a release candidate. Its final closure is
authoritative only in the successful release commit. No publication, push, or
downstream-consumer acceptance is claimed.
