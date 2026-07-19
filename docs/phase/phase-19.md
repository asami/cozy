# Phase 19: Knowledge-Centered Media Packages

Status: closed

Start date: 2026-07-19

## Goal

Provide a portable Media Package for the image and video representations of one
BoK knowledge unit, while separating AI-assisted semantic work from Cozy-owned
deterministic build, verification, and publication.

## Scope

In scope:

- the `cozy.media.v1` descriptor contract;
- `inspect`, `plan`, `build`, `verify`, and `publish` commands;
- relative source and output paths with profile-based publication roots;
- environment-backed roots for machine-specific artifact archives;
- SVG-to-PNG conversion, copy, prebuilt, and Cozy Video project resources;
- dimensions, source presence, publication equality, and provenance hashes;
- Cozy Video manifest placement alongside an external final output;
- a migrated bilingual SimpleModeling.org article-media package.

Out of scope:

- semantic copy generation or translation inside Cozy;
- audiovisual quality decisions;
- YouTube upload;
- a new video renderer or narration engine.

## Stage 19.1: Media Package Contract

Stage Status:

- Current status: DONE
- Owner: cozy-media
- Checklist basis: `MED19-01` through `MED19-04`

Verification evidence:

- focused `CozyMediaSpec` passed nine executable specifications;
- the complete Cozy suite passed 592 tests with two gated integration tests
  canceled by default;
- the pilot package completed CLI inspect, plan, build, verify, site publication,
  and publication verification;
- the targeted SmartDox site build completed successfully;
- `git diff --check` completed without errors in Cozy and the pilot site.

## Completion Criteria

Phase 19 closes when one knowledge unit can own bilingual image and video
representations in a portable package, Cozy can reproduce and verify local
representations, publication destinations remain profile-driven, large media
stays outside source control, and the contract is validated by executable
specifications and one real SmartDox package.

These criteria were satisfied on July 20, 2026. Phase 19 is closed. External
Dropbox publication remains an operator action because the execution environment
does not permit artifact disclosure to that destination.
