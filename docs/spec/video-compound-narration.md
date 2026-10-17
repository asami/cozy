# Video Compound Narration Specification

Status: NORMATIVE description of existing speech semantics; PHASE-71.2 P710-F02 is accepted and CLOSED.

The executable chapter is [CozyVideoCompoundNarrationSpec](../../src/test/scala/cozy/video/CozyVideoCompoundNarrationSpec.scala),
“An author preserving compound terms on screen should supply their intended
reading through public narration synthesis.” The [design](../design/video-compound-narration.md)
defines the provider, display and regeneration responsibilities. This contract
preserves the existing legacy script and native Storyboard routes.

## Speech and display

The script pronunciation map supplies readings for provider speech. The shared
dictionary and script overrides operate in a single pass over the original
speech after the existing whitespace policy. Longest original matches win;
generated readings are not recursively processed. Storyboard pronunciation
notes reuse the existing projection into this map.

`voiceTextNormalization.removeMiddleDots` is optional and defaults to false.
When enabled, it removes only U+30FB from final provider speech, including
middle dots produced by dictionary readings. It adds no general punctuation
policy. Omitted or false preserves those dots. A malformed option fails before
provider I/O, as covered by the existing narration specification.

Narration, line, caption, headings, visual declarations, voice tuning and other
configuration remain authored. In particular, display `モデル・アップ・ダウン`
may coexist with provider speech `モデルアップダウン`. Enabling a global option
is unnecessary when the authored dictionary already provides that reading.

## Regeneration and actual artifacts

An explicit public `CozyVideo.synthesize` request invokes the selected provider
and writes current scene WAVs, combined WAV and manifest. Repeating the request
at the same script and save paths after changing only pronunciation must use
the new speech while preserving all other declarations. Actual successful
provider responses and their association with current normalized audio prove
regeneration. Equivalent audio bytes are permitted; timestamps, hash changes
or an invented byte-difference requirement do not establish generation.

The generated Cozy PCM contract is format 1, 24000 Hz, mono, 16 bits. Actual
audio durations are positive, and the native target must contain non-silent
audio. Requested scene timing retains its established effective-tail behavior.

## Executable scenarios and private acceptance

The property scenario interprets at least twenty ScalaCheck contexts with
numeric ASCII prefix/suffix and requested timing. Each covers omitted, false
and true normalization, a repeated dictionary-only request and a
dictionary-produced middle dot. Its existing recording fixture provides
deterministic audio; it does not establish actual provider acceptance.

The native scenario requires `cozy.video.narration.integration=true` and an
explicit safe repository-relative `cozy.video.narration.driver`. Ordinary
standalone runs may cancel when not opted in; Phase validation enables it and
requires zero canceled, ignored, pending, failed or aborted results. It consumes
the retained full 34-scene Model Harness script, including `updown-question`
and `モデル・アップ・ダウンは、どんな考え方なの？`.

Only the original compound mapping is removed for the baseline, then the full
original map is restored. Both requests use the same working script and output
directory. All scenes, sections, display, timing, provider and voices remain
identical. A transparent test decorator synchronously delegates speakers,
audio queries and synthesis to `VoicevoxClient.default` at the declared
`http://127.0.0.1:50021`. Each pass requires 34 real queries and syntheses,
the same ordered speakers and authored tuning, and equal non-target speech.
The corrected target is exactly `モデルアップダウンは、どんな考え方なの？`.

Separate baseline/corrected directories retain provider responses, tuned
queries, provider WAVs, scene and combined WAVs and manifests. The native
`observations.json` records the source, all scene IDs, working JSON before and
after, preservation, audio observations and actual call counts under a unique
`target/test-generated/video-compound-narration/native-*` directory. Completed
artifacts remain available even if a later expectation fails.

The corrected provider query must begin with nine spoken moras
`モ / デ / ル / ア / ッ / プ / ダ / ウ / ン`, without a pause mora before
that prefix ends. Authored punctuation may introduce pauses afterward. These
phonetic observations and non-silence are separate from actual listening.
`listening=pending` remains recorded until the parent obtains listening evidence.

Original 26 source/private inputs and F01 outputs remain read-only and retained.
Parent-admitted focused validation passed on 2026-10-03: five suites, 165/165
tests (compound chapter 2/2, four accumulator suites 163/163), zero failed,
canceled, ignored, pending or aborted results and no compiler warning lines.
The actual full-34-scene driver completed both passes with 68 successful audio
queries and 68 syntheses, preserved source/display/other JSON and voice tuning,
and regenerated audio after the dictionary-only edit. Its corrected target
query contains the required nine moras with `pauseBeforePrefixEnd=false`.
The [implementation journal](../journal/2026/10/2026-10-03-phase-71.2-compound-narration.md)
records the actual observations, parent WAV inspection and the actual user's
2026-10-03 clarification: U+30FB (`・`) was not pronounced in the corrected
target WAV, which satisfies the requirement to omit its pronunciation. This
clarification resolves the earlier ambiguous message without extending it to
a broader listening verdict. The corrected pass restores the authored
dictionary mapping; global middle-dot removal is not enabled, and display text
is preserved.

Independent PASS complete-F02 Step review
`68b2ef62-714e-435a-8987-541059fc3dd1` revision 1 and exact local acceptance
commit `2c5e9402096528dc1ecd6558470e59ee7363016d` bind F02 acceptance and
close its three checklist items. Phase 71.2 is CLOSED by the distinct local release containing the [normal closure record](../journal/2026/10/2026-10-03-phase-71.2-normal-closure.md). The sole independent full Phase review and its accepted M0 clarification amendment leave zero current blockers. Repository-full validation remains deferred-not-run to PHASE-71.4; no successor execution is claimed.
