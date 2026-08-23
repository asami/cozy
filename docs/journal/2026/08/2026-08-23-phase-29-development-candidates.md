# Phase 29 Development Candidates

This journal records non-blocking follow-up work transferred from the closed
Phase 29 acceptance boundary. It is not a normative specification.

## DEV-P29-001: BoK metadata input admission hardening

- Status: PLANNED
- Discovery: Phase 29 full review, 2026-08-23
- Repository: Cozy
- Evidence: `CPB-29-01` found that configured `bok.source` admission did not
  yet prove canonical project-root and symlink-safe containment before it
  influenced glossary/RDF decisions. `CPB-29-02` found that glossary and
  component-reference resources were not unconditionally validated before
  staging or manifest declaration.
- Current-Phase reason: the developer directed that required corrections be
  added as a separate Phase rather than repaired in Phase 29. Phase 29 records
  no claim that either finding is resolved.
- Owner and target: Cozy Phase 34, `BOK34-01` and `BOK34-02`.
- Dependency: Phase 29 public finalization contract; no SmartDox generated-site
  result is a substitute for the Cozy-side admission checks.
- Risk: malformed or unsafe configured source/resource input can influence a
  metadata handoff before rejection.
- Resume condition: invoke Phase 34 with this record and the sealed Phase 29
  review ledger.
- Prohibited local workaround: do not relax finalizer validation, reconstruct
  missing data, or invoke a site build to hide the admission failures.
