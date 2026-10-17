# Video Compound Narration Design

Status: NORMATIVE description of existing responsibilities; no production correction selected.

The [compound-narration specification](../spec/video-compound-narration.md)
and [executable chapter](../../src/test/scala/cozy/video/CozyVideoCompoundNarrationSpec.scala)
describe one author use case: retain a compound's display spelling while
synthesizing its intended reading and regenerating audio after a reading edit.

## Existing responsibility boundaries

The author owns script pronunciation overrides, Storyboard pronunciation notes,
source/display strings, timing and voice settings. Existing planning projects
Storyboard notes into the same pronunciation map. The narration path chooses
speech, applies the existing whitespace policy and single-pass dictionary, then
optionally removes U+30FB from final provider speech. Display strings do not
pass through this speech transformation. No alternate normalizer or notes
adapter is introduced.

`CozyVideo.synthesize` selects the existing provider, resolves voice identity,
obtains its audio query, applies authored tuning and requests actual audio.
The existing WAV normalizer writes the Cozy PCM format; scene timing, combined
WAV and manifest derive from those actual outputs. An explicit repeated request
generates again rather than relying on a local hash or artificially touched
source. Equal engine audio is compatible with genuine regeneration.

## Specification responsibilities

The property scenario uses the existing `RecordingVoicevoxClient` solely as a
deterministic fixture through public synthesis. Generated contexts and timing
exercise both compatibility and enabled normalization, including a dotted
dictionary reading, while observing source preservation, provider speech,
actual written WAVs and manifest. The existing narration and Storyboard
consumer chapters retain longest-match, nonrecursive, malformed-input and
projection coverage.

The opt-in native scenario consumes the actual full private Model Harness
script. It retains source bytes, mode and FileTime, removes only the compound
dictionary entry for baseline speech, and restores the full original map at
the same working path. Both passes use the same save directory. It does not
change scenes, voices, punctuation, visuals, provider or endpoint and does not
enable global normalization.

A test-only synchronous decorator forwards every method to
`VoicevoxClient.default`. Immutable observations retain exact text/speaker,
returned audio query, tuned synthesis query, successful provider WAV bytes and
per-call outcomes. Companion-owned fixture types avoid spec-instance outer
references. This consumes the existing HTTP client, with no replacement
transport, proxy, fake service or fallback.

Each unique `target/test-generated/video-compound-narration/native-*` directory
retains original and working JSON, baseline/corrected provider WAVs, complete
audio directories and observations. Baseline artifacts are retained before
the correction; completed corrected artifacts and observations precede final
expectations. Current normalized scene data is associated with the successful
corrected provider response. Actual call counts, ordered speakers, voice tuning,
source preservation, positive duration and target non-silence are observable.
No audio byte difference is required.

The corrected query's prefix mora sequence and absence of internal pause
provide phonetic evidence. They do not prove the listener's experience.
Observations keep listening pending; the parent independently inspects retained
artifacts and obtains actual listening acceptance. Endpoint failure or a
phonetic failure is a failed proof requiring parent diagnosis, never a mocked
or skipped substitute for Phase acceptance.

Production source, resources, API and configuration remain unchanged. Actual
parent-admitted focused validation completed five suites and 165/165 tests,
including both compound scenarios and the four consumer accumulator suites.
The actual full-34-scene passes produced 68 successful queries and 68 successful
syntheses; retained observations prove the dictionary-only same-path edit,
preserved authored declarations and corrected mora prefix without an internal
pause. The parent inspected the target and combined WAVs as positive,
non-silent 24000 Hz mono 16-bit PCM. See the
[actual-results journal](../journal/2026/10/2026-10-03-phase-71.2-compound-narration.md).
The actual user's 2026-10-03 clarification confirms that U+30FB (`・`) was not
pronounced in the corrected target WAV and that this satisfies the requirement
to omit its pronunciation. It resolves the earlier ambiguous message without
extending it to a broader listening verdict. This corrected pass uses the authored dictionary mapping,
preserves display text and does not enable global middle-dot removal.
Independent PASS complete-F02 Step review
`68b2ef62-714e-435a-8987-541059fc3dd1` revision 1 and exact acceptance commit
`2c5e9402096528dc1ecd6558470e59ee7363016d` bind F02 acceptance; F02 is CLOSED
with its three checklist items checked. Accepted F01 evidence and artifacts
remain intact. Phase 71.2 is CLOSED by the distinct local release containing the [normal closure record](../journal/2026/10/2026-10-03-phase-71.2-normal-closure.md). The sole independent full Phase review and its accepted M0 clarification amendment leave zero current blockers. Repository-full validation remains deferred-not-run to PHASE-71.4; no successor execution is claimed.
