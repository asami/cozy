# Phase 18 Checklist

This checklist is the authoritative progress ledger for Phase 18:
Shared Video Pronunciation Dictionary.

## VID18-01: Dictionary Contract

Status: DONE

- [x] Add a bundled UTF-8 pronunciation definition file.
- [x] Define `値` as `あたい` and `BoK` as `ボック`.
- [x] Keep script-local pronunciation entries as higher-priority overrides.

## VID18-02: Synthesis Boundary

Status: DONE

- [x] Apply common readings immediately before the VOICEVOX audio query.
- [x] Preserve authored narration, captions, and generated metadata.
- [x] Use deterministic longest matching against the original text.
- [x] Prevent generated readings from being converted a second time.

## VID18-03: Specification and Closure

Status: DONE

- [x] Cover canonical readings and script overrides with Given/When/Then specs.
- [x] Cover longest-match and non-cascading behavior.
- [x] Document the dictionary and override contract in the video guide.
- [x] Run the complete Cozy test suite and `git diff --check`.
- [x] Complete Phase 18 closure documentation.
