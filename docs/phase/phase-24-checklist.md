# Phase 24 Checklist

This checklist is the authoritative progress ledger for Phase 24: Component
Skill Distribution.

## SK24-01: CNCF Skill Bundle Contract

Status: PLANNED

- [ ] Define the versioned `SkillBundleManifest` schema and canonical media
      type when applicable.
- [ ] Define bundle/skill identity, descriptions, compatibility, dependency,
      and optional MCP requirement semantics.
- [ ] Define canonical development-source and CAR archive locations.
- [ ] Define normalized relative paths, SHA-256 digest input, deterministic
      ordering, and source/archive equivalence.
- [ ] Define collision, unsupported schema, incompatible Codex/runtime, and
      missing requirement outcomes.
- [ ] Ensure a manifest grants no runtime or installer authority by itself.
- [ ] Publish normative valid and invalid fixtures usable by Cozy and both
      launchers.

## SK24-02: Cozy Lint and CAR Packaging

Status: PLANNED

- [ ] Read the CNCF manifest from its canonical component source location.
- [ ] Validate every declared file and reject missing or undeclared content.
- [ ] Reject absolute paths, traversal, symlink escape, duplicate identity,
      digest mismatch, and unsupported contract versions.
- [ ] Add integrated skill checks to `cozy lint` and focused `cozy lint skill`.
- [ ] Package only declared files at the canonical CAR archive location.
- [ ] Record bundle, CAR, source, and content-digest provenance.
- [ ] Prove deterministic CAR output and source/archive equivalence.
- [ ] Keep Cozy and Cozy Launcher free of Codex installation side effects.

## SK24-03: CNCF Launcher Development Installation

Status: PLANNED

- [ ] Implement the documented `cncf skill` command grammar.
- [ ] Admit a component development directory or explicit CAR safely.
- [ ] Default development installation to project scope and require explicit
      user scope.
- [ ] Validate the common manifest and all declared file digests before staging.
- [ ] Diagnose source freshness, stale generated output, and package divergence.
- [ ] Implement staged install, status, update, uninstall, rollback, and
      interrupted-install recovery.
- [ ] Preserve unrelated skills and Codex configuration.
- [ ] Perform MCP configuration merge only with `--configure-mcp` and reject
      conflicts before activation.

## SK24-04: Textus Launcher Published Installation

Status: PLANNED

- [ ] Implement the documented `textus skill` command grammar.
- [ ] Resolve the selected CAR through public, cache, and local repository
      precedence from Phase 21.
- [ ] Default released installation to user scope while supporting explicit
      project scope.
- [ ] Validate the packaged manifest and all file digests before staging.
- [ ] Implement staged install, status, update, uninstall, rollback, and
      interrupted-install recovery.
- [ ] Record artifact, version, repository source, bundle, scope, and digest
      provenance.
- [ ] Preserve unrelated skills and Codex configuration.
- [ ] Perform MCP configuration merge only with `--configure-mcp` and reject
      conflicts before activation.

## SK24-05: Component Repository Skill Knowledge

Status: PLANNED

- [ ] Project skill-bundle summary metadata into Component Repository entries.
- [ ] Show skill names, descriptions, compatibility, MCP requirements, and CAR
      version provenance without exposing private install paths.
- [ ] Link to CNCF development and Textus published installation guidance.
- [ ] Diagnose a catalog entry whose declared CAR skill metadata is missing or
      inconsistent.

## SK24-06: Cross-Repository Verification and Closure

Status: PLANNED

- [ ] Select one real component CAR as the driver bundle.
- [ ] Package it through Cozy and inspect the archive contract.
- [ ] Install the source bundle through CNCF Launcher into an isolated project
      Codex scope.
- [ ] Install the packaged CAR through Textus Launcher into another isolated
      project Codex scope.
- [ ] Prove both routes install byte-equivalent declared skill content.
- [ ] Verify collision, incompatibility, digest mismatch, stale source,
      interrupted install, and MCP merge refusal cases.
- [ ] Run focused and full tests in every modified repository.
- [ ] Run `git diff --check` in every modified repository.
- [ ] Complete a read-only post-implementation review.
- [ ] Fix all actionable findings, including naming and executable-spec debt.
- [ ] Complete a clean read-only re-review after the fixes.
- [ ] Commit validated changes with required version updates.
- [ ] Record operational evidence and close Phase 24 from checklist results.
