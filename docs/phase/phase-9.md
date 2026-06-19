# Phase 9: Video Publication Registration and BoK Integration

Status: closed

Start date: 2026-06-19
Close date: 2026-06-19

## Goal

Complete the video publication registry boundary started in Phase 8.

Phase 8 made `.video/` packages buildable and publishable, and proved that
SmartDox can embed the published video player from Cozy-generated metadata.
Phase 9 makes `src/main/publication` the complete BoK-facing source of truth
for video metadata, RDF, and provenance. SmartDox and BoK rendering must not
scan `target/`, `build/`, arbitrary RDF output directories, or warehouse
directories to discover video knowledge.

## Scope

In scope:

- `publish-video` registry entries for video RDF and provenance
- stable `metadata/video/...` paths under `src/main/publication`
- SmartDox consumption of registered video metadata
- preservation of the existing `publish-video` command surface
- preservation of sbt-bridge and help compatibility

Out of scope:

- production upload or CDN invalidation
- SmartDox execution of video generation
- new video generation commands
- warehouse scanning by SmartDox

## Phase Items

- [x] VPR-01: Phase 9 documentation opened
- [x] VPR-02: `publish-video` registers video RDF/provenance metadata in the
      publication registry
- [x] VPR-03: SmartDox consumes registered video metadata without scanning
      generated/work directories
- [x] VPR-04: CLI/help/sbt-bridge compatibility preserved
- [x] VPR-05: Video sidecar registry and SmartDox RDF reference integration

## Acceptance Criteria

- `cozy publish-video <slug>.video --save <publication-dir> --warehouse
  <warehouse-dir>` keeps the Phase 8 command surface.
- The publication bundle includes registry entries under:
  - `metadata/video/<video>/<version>/manifest.json`
  - `metadata/video/<video>/<version>/rdf.json`
  - `metadata/video/<video>/latest.json`
- BoK-facing video metadata references registry and warehouse paths instead of
  workspace-local RDF paths.
- Warehouse sidecars remain next to the published MP4 artifact.
- Caption and transcript sidecars, when present, are registered as repository
  artifacts and never exposed as workspace-local paths.
- SmartDox site rendering still embeds the video player from publication
  metadata.
- SmartDox site RDF records references to video, RDF, caption, and transcript
  repository artifacts without parsing or merging external RDF bodies.
- Generated MP4, RDF, captions, and transcript files are not written under the
  `.video/` source package.

## Progress Notes

- 2026-06-19: Opened Phase 9 with Video Publication Registration as the first
  workstream. The initial implementation target is completing
  `publish-video` registry output for RDF/provenance metadata while preserving
  the existing command surface.
- 2026-06-19: Completed the initial Video Publication Registration slice.
  `publish-video` now adds `metadata/video/<video>/<version>/manifest.json`,
  `metadata/video/<video>/<version>/rdf.json`, and
  `metadata/video/<video>/latest.json` bundle entries. BoK-facing metadata now
  points to registry and warehouse paths instead of workspace-local RDF output.
- 2026-06-19: Added sidecar registry completion and SmartDox RDF references.
  `captions.srt` and `transcript.json` are registered as repository sidecars
  when present. SmartDox embeds caption tracks and adds site RDF reference
  triples for video/RDF/caption/transcript artifacts. RDF body parse/merge is
  intentionally deferred.
- 2026-06-19: Closed Phase 9. All VPR items are complete, and there is no
  active Cozy phase after this closure.

## References

- `docs/strategy/cozy-development-strategy.md`
- `docs/phase/phase-9-checklist.md`
- `docs/journal/2026/06/video-rdf-bok-registration-handoff-2026-06-19.md`
