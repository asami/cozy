# Phase 9 Checklist

This checklist is the authoritative progress tracker for Video Publication
Registration and BoK Integration work from 2026-06-19 onward.

## VPR-01: Phase 9 Documentation

Status: DONE

- [x] Add `docs/phase/phase-9.md`.
- [x] Add `docs/phase/phase-9-checklist.md`.
- [x] Add Phase 9 to `docs/strategy/cozy-development-strategy.md`.
- [x] Set `docs/phase/README.md` current phase to Phase 9.

## VPR-02: Video RDF/Provenance Registry Entries

Status: DONE

- [x] Keep the existing `publish-video` command surface.
- [x] Add registry entries under `metadata/video/<video>/<version>/`.
- [x] Add `metadata/video/<video>/latest.json`.
- [x] Replace workspace-local RDF references in BoK-facing video metadata with
      registry and warehouse references.
- [x] Include published MP4 and sidecar metadata when present.
- [x] Keep generated artifacts outside `.video/` source packages.

## VPR-03: SmartDox Metadata-Driven Consumption

Status: DONE

- [x] Confirm SmartDox renders video articles from publication metadata.
- [x] Ensure SmartDox does not scan `target/`, `build/`, arbitrary RDF output
      directories, or warehouse directories for video knowledge.
- [x] Preserve video player embedding from registered publication metadata.

## VPR-04: Compatibility Preservation

Status: DONE

- [x] Preserve `cozy help` output for `publish-video`.
- [x] Preserve sbt-bridge `publish-video` action compatibility.
- [x] Preserve existing `publish-video` failure behavior for missing package,
      duplicate artifact, and invalid descriptor inputs.
- [x] Run focused and full Cozy test validation.

## VPR-05: Video Sidecar Registry and SmartDox RDF References

Status: DONE

- [x] Register caption and transcript sidecars as repository artifacts when
      present.
- [x] Remove workspace-local caption/transcript paths from BoK-facing metadata.
- [x] Add caption track embedding from publication metadata.
- [x] Add SmartDox site RDF reference triples for video, RDF, caption, and
      transcript artifacts.
- [x] Defer RDF body parse/merge to a later slice.
