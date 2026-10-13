# Video and audio quality profile candidates

Date: 2026-10-11
Status: exploratory planning; not a normative specification
Receiving work: [Phase 71.8](../phase/phase-71.8.md)

The user requested one video quality profile that selects picture and audio
together, after the currently active Phase71.7. The user subsequently requested
that current lightweight become new standard, the existing upper settings move
one tier up, and a faster lower-quality practice profile occupy the lightweight
position. Quality remains distinct from presentation effects and tool/encoder
selection.

## Requested ladder and candidate practice/audio settings

| New profile | Picture source | fps / dimensions | Software CRF | Software preset | AAC candidate |
| --- | --- | --- | --- | --- | --- |
| lightweight | New practice candidate | 12 /854×480 | 36 | superfast | 96k |
| standard | Current lightweight | 18 /1280×720 | 32 | Existing default | 128k |
| quality | Current standard | 30 /1280×720 | 23 | Existing default | 192k |
| high-quality | Current quality | 30 /1920×1080 | 18 | Existing default | 320k |

The shifted standard/quality/upper picture values describe existing settings.
`high-quality` is the proposed name for the upper tier. The new lightweight
settings and all audio values are comparison candidates, not implemented or
tested defaults. Current code still has only the old three policies and resolves
omitted selection to old lightweight; all currently use fixed320k AAC.
After the planned change, omitted selection should use new standard so the
current default picture settings remain18fps,720p,CRF32.

The existing VideoToolbox policy defaults move with their picture settings:
old lightweight2M becomes new standard2M, old standard6M becomes new quality6M,
and old quality12M becomes the upper tier12M. A1M hardware bitrate is an initial
practice-profile candidate, subject to actual selected-hardware acceptance.

Practice speed comes primarily from fewer frames and lower resolution, with a
faster software encoding preset as a further candidate. Higher CRF mainly
targets picture quality/file size; it does not by itself prove faster rendering.
The proposed12fps/480p path processes roughly30% of new standard's pixel-frame
volume for equal duration, but actual wall time must be measured. Preserve
storyboard/layout usefulness, audible narration and the complete ending.

Start the audio customization surface with bitrate. Retain48kHz stereo and the
sample-based ending behavior, avoiding a simultaneous codec/sample-rate/channel
migration. Keep existing picture overrides and permit explicit audio bitrate
override without changing the selected picture profile.

## Decisions to settle in P718-01

- Select the canonical public quality-profile/configuration location and precedence, reusing the existing picture-policy mechanism.
- Specify migration from old lightweight/standard/quality selections to new standard/quality/upper selections, with no ambiguous old/new meaning or silent drop in intended quality.
- Specify new standard as the default label while retaining the current default picture settings; define how legacy `renderer.policy` and the new practice selection are distinguished.
- Keep presentation `profile` such as `explanation` semantically separate from encoding quality.
- Expose the effective audio bitrate and its origin alongside existing picture/encoder observations.
- Confirm or adjust candidate audio defaults through short JA/EN listening comparisons, including sibilants, quiet speech and phrase endings.
- Finalize the upper-tier name and practice-profile values through specs and comparable actual render/encode/total observations; no arbitrary speedup multiplier is required.

## Current implementation boundaries

The fixed320k/18000Hz audio arguments occur in
`CozyVideoFrameEncoding.scala`, `CozyVideoRenderTemplates.scala` and
`CozyVideoBuildReplay.scala`. A new setting must reach all three; changing only
the direct encoder allows final assembly to restore the old bitrate.
Update the affected video specifications/design and executable specifications
as part of implementation, retaining narration coverage and rational media ends.

Compare from the retained lossless narration/assembled PCM rather than repeatedly
compressing an existing AAC track. Audio-only comparisons require no picture
rerender. Record measured output size separately from bitrate-based estimates.
No implementation or production settings have changed through this note.
