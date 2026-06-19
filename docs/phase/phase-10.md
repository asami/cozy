# Phase 10: One-Stop BoK Build and Operation Integration

Status: active

Start date: 2026-06-19

## Goal

Integrate the Cozy BoK, video publication, publication registry, warehouse, and
SmartDox site outputs so BoK construction, verification, and publication can be
operated through the `cozy bok` command family.

Phase 10 keeps source, metadata, artifacts, and generated site output separated:

- `src/main/doxsite` is the BoK source tree.
- `src/main/publication` is the BoK-facing metadata registry.
- `warehouse` is the artifact source of truth.
- `website.d` and `doxsite.d` are generated outputs.

`bok build` remains a site build operation. It may merge registered RDF into the
site RDF graph, but it must not generate or transcode video artifacts.

## Scope

In scope:

- BoK publication config under `bok.*`.
- `cozy bok publish-video`, `update-publication`, and `publish` commands.
- `.video/` package discovery inside BoK source trees.
- SmartDox RDF artifact merge from registered publication metadata.
- SmartDox video article enhancement from publication metadata.
- Compatibility with existing BoK, video, publication, and sbt-bridge behavior.

Out of scope:

- SmartDox execution of heavy video generation.
- Warehouse scanning by SmartDox.
- Production upload/CDN policy beyond the existing configured upload workflow.
- JSON-LD body merge when Turtle is available.

## Phase Items

- [ ] BK10-01: Phase 10 documentation
- [ ] BK10-02: BoK publication config model
- [ ] BK10-03: BoK video publication commands
- [ ] BK10-04: `.video/` package discovery
- [ ] BK10-05: One-stop BoK publish flow
- [ ] BK10-06: SmartDox RDF artifact merge
- [ ] BK10-07: SmartDox video article enhancement
- [ ] BK10-08: BoK build integration
- [ ] BK10-09: Compatibility preservation
- [ ] BK10-10: Tests and smoke
- [ ] BK10-11: Phase closure

## Acceptance Criteria

- `cozy bok publish-video <project-dir>` publishes all registered `.video/`
  packages into `src/main/publication` and `warehouse/repository/video`.
- `cozy bok update-publication <project-dir>` provides the future publication
  update entry point and handles video packages in v1.
- `cozy bok publish <project-dir>` runs publication update, production build,
  and configured upload workflow in deterministic order.
- `cozy bok build` passes publication metadata and repository root information
  to SmartDox but does not generate or transcode videos.
- SmartDox merges registered Turtle RDF artifacts into `site.ttl` and
  `site.jsonld` when configured artifacts are available.
- Missing RDF artifacts warn in preview/draft and fail in production.
- Existing BoK, video, publication, and sbt-bridge behavior remains compatible.

## Progress Notes


- 2026-06-19: Implemented initial one-stop BoK integration slice.
  `cozy bok` now has `publish-video`, `update-publication`, and `publish` entry points.
  `bok build` passes publication registry and repository context to SmartDox site generation.
  SmartDox resolves registered video RDF Turtle artifacts from publication metadata, merges
  them into the site graph, preserves video artifact reference triples, and renders video
  sidecar links from publication metadata. Metadata-only tests do not require media tools.

- 2026-06-19: Opened Phase 10 with one-stop BoK build and operation integration
  as the active Cozy workstream.

## References

- `docs/strategy/cozy-development-strategy.md`
- `docs/phase/phase-10-checklist.md`
- `docs/phase/phase-8.md`
- `docs/phase/phase-9.md`
