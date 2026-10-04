# BoK Build Target Ownership Design

Status: normative PHASE-71.3 / P710-04-SITE / S18-replan02 design

The [target ownership specification](../spec/bok-build-target-ownership.md)
fixes the producer boundary at `target/cozy-bok`. `_clear_build_target` still
checks the direct target root and enumerates its direct children.
`_cleanup_build_target_children` dispatches only the `cozy-bok` child to the
existing guarded cleanup. Other project target children are neither traversed
nor unlinked. No protection metadata or special `target/media` whitelist is
introduced.

Inside `cozy-bok`, existing obsolete-child deletion and direct `article-media`
retention continue. Direct-directory guards reject target/cache symlinks.
The ordinary build's explicit output cleanup, bibliography and configured SIE
stash/restore, publication snapshot leases and integrity proofs are unchanged.
This corrects BoK ownership without transferring Media/Video producer duties.

The focused ownership property and symlink scenarios exercise the real cleanup
with actual configuration; the existing handoff specifications exercise normal
build orchestration with recorded external consumers. Neither replaces the
unchanged complete native entry or its input-retention acceptance checks.
