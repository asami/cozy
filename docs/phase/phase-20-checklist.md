# Phase 20 Checklist

This checklist is the authoritative progress ledger for Phase 20:
Multi-Provider Narration and Cozy-Complete Video Build.

## VID20-01: Provider-Neutral Narration Contract

Status: DONE

- [x] Add a narration-provider abstraction around provider-specific synthesis.
- [x] Keep pronunciation conversion, scene timing, silence, WAV normalization,
      combined audio, and manifest generation in shared Cozy code.
- [x] Define canonical `narration.provider` authoring.
- [x] Read existing `voice.engine` only as compatibility input, emit an explicit
      deprecation diagnostic, and reject conflicts with `narration.provider`.
- [x] Keep canonical output, scaffold templates, and documentation free of
      legacy `voice.engine` authoring.
- [x] Preserve VOICEVOX as the default for existing scripts without an explicit
      provider.
- [x] Record provider, voice/model identity, and execution mode in synthesis
      provenance.

## VID20-02: CLI, Configuration, and Diagnostics

Status: DONE

- [x] Extend `cozy video synthesize` with the execution settings required by
      host and Docker providers.
- [x] Resolve Docker image settings through the existing Cozy Video precedence.
- [x] Add selected-provider checks to `video inspect --check-tools`.
- [x] Reject unsupported provider names and invalid provider/mode combinations
      before creating output files.
- [x] Preserve provider and model provenance in video RDF and publication
      metadata.
- [x] Update CLI help and video authoring documentation.

## VID20-03: macOS Say Host Provider

Status: DONE

- [x] Add a host-only `macos-say` provider.
- [x] Support configured macOS voice names and speaking rates.
- [x] Invoke `say` and audio normalization through argument-vector process
      execution rather than shell command strings.
- [x] Diagnose non-macOS hosts, missing `say`, missing ffmpeg, and Docker mode.
- [x] Cover Samantha/Karen-style two-character synthesis with executable specs.

## VID20-04: Portable Docker TTS Provider

Status: DONE

- [x] Pin Piper as the Linux TTS runtime for offline English synthesis.
- [x] Select two voice models whose redistribution licenses are explicitly
      acceptable for the Textus toolchain image.
- [x] Add runtime and model SHA-256 verification to the image build.
- [x] Add a machine-readable model provenance and license manifest under
      `/opt/textus/models`.
- [x] Add `textus-toolchain check tts` and integrate the required TTS checks
      with the video toolchain contract.
- [x] Run a real Docker smoke that creates canonical WAV output without runtime
      network access.
- [x] Build and test only a snapshot image until the provider contract is
      accepted.

## VID20-05: Cozy Rendering and Assembly Completion

Status: NOT STARTED

- [ ] Remove the need for project-local visual-effect executables.
- [ ] Represent the Overview opening title hold and subtle motion through a
      Cozy-owned renderer/effect contract.
- [ ] Keep section-start, summary, and final-page behavior in named profiles.
- [ ] Render every part through `cozy video render`.
- [ ] Assemble and validate the final MP4 through `cozy video build` and its
      managed ffmpeg/ffprobe route.
- [ ] Keep generated audio, intermediate media, and final MP4 files outside
      source control.

## VID20-06: Overview Migration and Validation

Status: NOT STARTED

- [ ] Migrate the Japanese Overview package to canonical Cozy Video authoring
      and VOICEVOX narration.
- [ ] Migrate the English Overview package to canonical Cozy Video authoring
      and host `macos-say` narration.
- [ ] Verify the English package with the portable Docker narration provider.
- [ ] Remove the package-local `synthesize_macos_say.py` after the Cozy provider
      replaces it.
- [ ] Remove absolute `videotools` references and manual ffmpeg instructions
      from both package READMEs and production metadata.
- [ ] Run focused Cozy narration/rendering specs and the full Cozy test suite.
- [ ] Run Textus Toolchain Runner tests and the real snapshot-image TTS smoke.
- [ ] Run both real Overview source-to-MP4 workflows and inspect the generated
      opening, summary, and final page.
- [ ] Record the source-to-MP4 verification evidence for both Overview videos.

## VID20-07: Credit Profiles and Publication Projection

Status: NOT STARTED

- [ ] Add a generic structured credit item, catalog, selector, profile, and
      resolver model without Asami-specific rules in Cozy core.
- [ ] Discover credit profiles through user and project Cozy configuration
      layers, with explicit video, project, and user default precedence.
- [ ] Support one-time selection of Asami's personal default profile under
      `~/.cozy` so normal video projects need no credit-specific setting.
- [ ] Resolve character-material credits from script character IDs and
      semantic asset metadata, with explicit include/exclude overrides.
- [ ] Resolve voice credits from authoritative audio-manifest provider and
      voice identity rather than publication locale or stale script metadata.
- [ ] Generate one structured effective credit set plus localized publication
      Markdown and renderer-neutral credit-page properties.
- [ ] Insert a non-empty credit page before the final URL page and omit it when
      no credit applies.
- [ ] Propagate the selected profile, evidence, terms links, and effective
      credit digest through inspect, build, verification, and RDF metadata.
- [ ] Fail verification for unresolved required credits and warn for unresolved
      recommended credits.
- [ ] Cover all eight Reimu/Marisa, Zundamon, and Japanese/English baseline
      combinations, including image-only, voice-only, and non-VOICEVOX English
      production cases.
- [ ] Update scaffold examples and the profile-driven video guide so another
      user or organization can define its own profile.
- [ ] Run focused and full tests, `git diff --check`, post-implementation
      review, record Phase 20 verification evidence, and close Phase 20.
