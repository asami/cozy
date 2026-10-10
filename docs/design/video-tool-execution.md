# Video tool execution design

Status: frozen PHASE-71.6 S02 design after accepted S01; implementation is not acceptance.

CozyVideoOperatingConfig owns the finite tool identities, layered field
resolution, validation and source projection. VideoExecutionConfig appends
effective selections and encoder settings while retaining three-argument source
construction. The global fields remain compatibility summaries. Command and
path dispatch query the same logical tool, including separate ffprobe.

CozyVideoFrameEncoding owns the versioned workspace-relative frame/audio
handoff and external encoder argv. CozyVideoEncoder owns finite encoder
validation, shared video options and effective JSON projection. It accepts
resolved profile settings or existing composition props; both Remotion frame
encoding and simple-java2d use this policy. VideoExecutionConfig appends defaulted
bitrate and origin fields; hardware name forces the external route even in a
compatible internal constructor. OperatingConfig validates all authored layers,
resolves encoder fields independently and checks final host/software constraints.
The existing renderer workspace
writes unchanged composition props plus the explicit external-encoding switch.
The same bundle/composition is used for PNG frames and whole-composition PCM.
This keeps renderer timing, narration, effects and end-card ownership intact.
Remotion's internal audio mixing requires its own runtime dependencies; selected
external FFmpeg replaces video encoding, not that mixing responsibility.

The baseline H.264 renderMedia path is preserved. Only explicit encoder/FFmpeg
overrides or differing renderer/encoder environment select external encoding.
Simple-java2d, narration normalization, extraction, assembly and mux dispatch
through their selected tools. Planning and inspection expose origins and encoder
route. No environment discovery, fallback, protection, hash, staging transaction
or new renderer framework is introduced.

S01 software native evidence and local Step commit eb3664c unlock S02 hardware.
Hardware requires host FFmpeg and explicitly forbids software acceleration
fallback. Profile bitrate defaults are 2M/6M/12M; explicit bitrate retains its
origin. Authored CRF/preset remain visible beside effective hardware controls.
The same policy supplies argv and effective projection; hardwareRequired is
configured intent, while actual initialization and native output evidence remain
separate parent acceptance obligations. Assembly/extraction/normalization/mux
remain unchanged, including stream copy at build.
Transcription model and replay compatibility work remain explicitly S03;
remaining combinations, benchmark and full-suite ownership remain PHASE-71.7.
