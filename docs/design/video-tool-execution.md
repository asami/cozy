# Video tool execution design

Status: frozen PHASE-71.6 S01 design; implementation is not acceptance.

CozyVideoOperatingConfig owns the finite tool identities, layered field
resolution, validation and source projection. VideoExecutionConfig appends
effective selections and encoder settings while retaining three-argument source
construction. The global fields remain compatibility summaries. Command and
path dispatch query the same logical tool, including separate ffprobe.

CozyVideoFrameEncoding owns the versioned workspace-relative frame/audio
handoff and external software encoder argv. The existing renderer workspace
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

Hardware is a later S02 increment after actual software native evidence.
Transcription model and replay compatibility work remain explicitly S03;
remaining combinations, benchmark and full-suite ownership remain PHASE-71.7.
