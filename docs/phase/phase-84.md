# Phase 84: CAR Dependency and Security Metadata Generation

Status: PLANNED
Plan date: 2026-10-14
Depends on: CNCF Phase 109
Consumer: Textus Control Center through CNCF runtime projection

## Goal

Produce the deterministic CAR release/dependency metadata required by CNCF CAR
Security and Vulnerability Metadata without making Cozy a vulnerability
database.

## Work

- emit stable CAR identity/version and declared CAR dependency information;
- collect/reference packaged/runtime dependency inventory needed for an SBOM;
- emit or attach a standard SBOM representation/reference where the build
  boundary can do so deterministically;
- bind metadata to the released CAR/admission identity;
- preserve enough provenance/freshness information for CNCF/provider advisory
  matching;
- expose deterministic CAR lint findings for missing/inconsistent release
  metadata where required by the release profile.

Cozy does not decide CVE severity, fetch arbitrary advisory databases during
ordinary generation, or decide deployment policy.

## Acceptance

A released representative CAR carries/references sufficient dependency/SBOM
metadata for CNCF Phase 109 to identify an advisory affecting a transitive
runtime dependency without scanning the running container filesystem.
