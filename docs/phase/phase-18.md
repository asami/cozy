# Phase 18: Shared Video Pronunciation Dictionary

Status: closed

Start date: 2026-07-18

## Goal

Provide one source-managed UTF-8 pronunciation dictionary for readings shared
by Cozy video projects, while preserving script-local overrides and keeping
authored narration, captions, and metadata unchanged.

## Scope

In scope:

- a bundled `cozy/video/pronunciations.properties` definition file;
- shared readings for `値` and `BoK`;
- script-local `pronunciations` precedence for the same source spelling;
- deterministic longest-match replacement against the original narration;
- application only at the VOICEVOX request boundary;
- executable specifications and authoring documentation.

Out of scope:

- automatic pronunciation discovery;
- locale-specific dictionary selection;
- rewriting authored script or caption text;
- a user-editable global dictionary outside the Cozy distribution.

## Stage 18.1: Shared Reading Contract

Stage Status:

- Current status: DONE
- Owner: cozy-video
- Checklist basis: `VID18-01` through `VID18-03`

Verification evidence:

- the bundled UTF-8 resource is copied into the Cozy runtime classpath;
- focused video specifications cover canonical readings, script override
  priority, longest matching, and non-cascading conversion;
- `CozyVideoSpec` passed 57 tests;
- the complete Cozy suite passed 581 tests with two gated Docker integration
  tests canceled by default;
- `git diff --check` completed without errors.

## Completion Criteria

Phase 18 closes when common readings are loaded from the bundled definition
file, applied only to synthesized speech, safely combined with script-local
readings, documented, and validated by focused and complete Cozy tests.

These criteria were satisfied on July 18, 2026. Phase 18 is closed.
