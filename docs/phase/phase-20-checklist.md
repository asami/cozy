# Phase 20 Checklist

This checklist is the authoritative progress ledger for Phase 20:
Multi-Provider Narration and Cozy-Complete Video Build.

## VID20-01: Provider-Neutral Narration Contract

Status: NOT STARTED

- [ ] Add a narration-provider abstraction around provider-specific synthesis.
- [ ] Keep pronunciation conversion, scene timing, silence, WAV normalization,
      combined audio, and manifest generation in shared Cozy code.
- [ ] Define canonical `narration.provider` authoring.
- [ ] Read existing `voice.engine` only as compatibility input, emit an explicit
      deprecation diagnostic, and reject conflicts with `narration.provider`.
- [ ] Keep canonical output, scaffold templates, and documentation free of
      legacy `voice.engine` authoring.
- [ ] Preserve VOICEVOX as the default for existing scripts without an explicit
      provider.
- [ ] Record provider, voice/model identity, and execution mode in synthesis
      provenance.

## VID20-02: CLI, Configuration, and Diagnostics

Status: NOT STARTED

- [ ] Extend `cozy video synthesize` with the execution settings required by
      host and Docker providers.
- [ ] Resolve Docker image settings through the existing Cozy Video precedence.
- [ ] Add selected-provider checks to `video inspect --check-tools`.
- [ ] Reject unsupported provider names and invalid provider/mode combinations
      before creating output files.
- [ ] Preserve provider and model provenance in video RDF and publication
      metadata.
- [ ] Update CLI help and video authoring documentation.

## VID20-03: macOS Say Host Provider

Status: NOT STARTED

- [ ] Add a host-only `macos-say` provider.
- [ ] Support configured macOS voice names and speaking rates.
- [ ] Invoke `say` and audio normalization through argument-vector process
      execution rather than shell command strings.
- [ ] Diagnose non-macOS hosts, missing `say`, missing ffmpeg, and Docker mode.
- [ ] Cover Samantha/Karen-style two-character synthesis with executable specs.

## VID20-04: Portable Docker TTS Provider

Status: NOT STARTED

- [ ] Pin Piper as the Linux TTS runtime for offline English synthesis.
- [ ] Select two voice models whose redistribution licenses are explicitly
      acceptable for the Textus toolchain image.
- [ ] Add runtime and model SHA-256 verification to the image build.
- [ ] Add a machine-readable model provenance and license manifest under
      `/opt/textus/models`.
- [ ] Add `textus-toolchain check tts` and integrate the required TTS checks
      with the video toolchain contract.
- [ ] Run a real Docker smoke that creates canonical WAV output without runtime
      network access.
- [ ] Build and test only a snapshot image until the provider contract is
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

## VID20-06: Overview Migration and Closure

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
- [ ] Run `git diff --check`, complete post-implementation review, record
      verification evidence, and close Phase 20.
