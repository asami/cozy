# Phase 17 Checklist

This checklist is the authoritative progress ledger for Phase 17:
Profile-Driven Video Scaffolding.

## VID17-01: Phase and Command Contract

Status: IN PROGRESS

- [x] Register Phase 17 in strategy and phase documents.
- [x] Add `cozy video scaffold <slug>` dispatch and CLI help.
- [x] Reject invalid slugs and existing destinations without overwriting them.
- [ ] Add the user-facing video scaffold authoring guide.

## VID17-02: Composition Profiles

Status: DONE

- [x] Add the `explanation` composition profile.
- [x] Add the `explanation-demo-explanation` composition profile.
- [x] Generate profile-specific part sequences in `video.yaml`.
- [x] Generate an initial `script.yaml` and demo step draft where required.
- [x] Reject unknown composition profiles with available-profile guidance.

## VID17-03: Visual Profiles and Safe Placeholder Assets

Status: IN PROGRESS

- [x] Persist independent section-start, summary, and final-page profiles.
- [x] Default to `line-sweep`, `overview-and-conclusion`, and `end-card`.
- [x] Generate project-local SVG placeholder frames for each initial asset slot.
- [x] State explicitly that `0714.techfirst.lt/assets` media is not copied or
      referenced by the scaffold.
- [ ] Record asset license and provenance through a machine-readable contract.

## VID17-04: Renderer-Neutral Effect Expansion

Status: DONE

- [x] Define the renderer-neutral visual-effect primitive schema.
- [x] Expand section-start profiles deterministically.
- [x] Expand summary profiles deterministically.
- [x] Expand final-page profiles deterministically.
- [x] Report unsupported renderer capabilities without silent substitution.
- [x] Cover profile expansion and diagnostics with executable specifications.

## VID17-05: Asset Resolution

Status: NOT STARTED

- [ ] Resolve project-owned files configured for asset slots.
- [ ] Use generated placeholders for unconfigured optional slots.
- [ ] Reject missing or unreadable configured required assets.
- [ ] Keep scaffold and build free of network asset fetching.
- [ ] Cover placeholder-only, configured-asset, and missing-asset behavior.

## VID17-06: Rendering and Closure

Status: NOT STARTED

- [ ] Render both composition profiles with placeholder-only assets.
- [ ] Declare renderer capabilities only after the adapter consumes each
      renderer-neutral primitive.
- [ ] Render configured project-owned assets through supported renderers.
- [ ] Verify summary and final-page timing/hold behavior.
- [ ] Verify video inspect, build, RDF, and publication compatibility.
- [ ] Run the full Cozy test suite and `git diff --check`.
- [ ] Complete post-implementation review and close Phase 17.
