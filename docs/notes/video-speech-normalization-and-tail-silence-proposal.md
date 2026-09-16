# Video Speech Normalization and Post-Utterance Timing Proposal

Date: 2026-09-14
Status: exploratory; non-normative
Tracking: [Phase 61](../phase/phase-61.md), DEV-029

## Purpose and review driver

Separate displayed terminology from its spoken form, and let authors specify
a short pause after an utterance independently of synthesized speech length.

Article 9 Japanese confirmation feedback supplied two concrete requirements:

- Keep the displayed spelling アプリケーション・モデル, while speaking
  アプリケーションモデル without a pause at the middle dot.
- Keep the final conversation screen briefly after speech ends, then transition
  to the shared infographic. A requested 0.8 seconds is the initial example;
  the infographic's existing silent hold is a separate interval.

This note proposes implementation contracts. P610-01 promotes the admitted
contract into design/spec and executable specifications before code changes.

## Confirmed current behavior

- `CozyVideoNarration._spoken_text` chooses narration/line/caption, applies
  voice text normalization, then applies pronunciation substitutions.
- `_apply_voice_text_normalization` supports space removal, but has no
  middle-dot removal option.
- `CozyVideoPronunciations.applyTo` supports per-script overrides, including
  an empty replacement. It is a single-pass substitution: dots introduced in
  a replacement, such as テキスタス・ボック, are not processed again.
- `VideoScene` admits duration, targetDuration and leadSilence, but no
  authored tailSilence. Nested scenes already inherit leadSilence.
- Synthesis derives the output manifest's tailSilence from the target duration
  minus leading silence and actual WAV duration. Rendering/evidence consume
  that manifest and effective frame timing.

Existing reading overrides or narration text can handle individual terms.
An explicit silent scene or post-synthesis target-duration adjustment can
provide a one-off pause. These are usable workarounds, not the common authoring
contract proposed here.

Source pointers:

- [Speech text and normalization](../../src/main/scala/cozy/video/CozyVideoNarration.scala)
- [Pronunciation substitution](../../src/main/scala/cozy/video/CozyVideoPronunciations.scala)
- [Scene and audio manifest models](../../src/main/scala/cozy/video/CozyVideoModel.scala)
- [Renderer timing](../../src/main/scala/cozy/video/CozyVideoRenderTemplates.scala)
- [Deterministic review evidence](../../src/main/scala/cozy/video/CozyVideoReviewEvidence.scala)
- [Storyboard conversion](../../src/main/scala/cozy/video/CozyVideoStoryboardBuild.scala)

## Proposed speech-only middle-dot policy

Add an opt-in boolean `voiceTextNormalization.removeMiddleDots`, default false.
When true, remove U+30FB (・) from the final speech text delivered to the
configured narration provider.

Apply existing space normalization and pronunciation substitutions first, then
this middle-dot policy, so dots introduced by dictionary readings are covered.
Do not run recursive dictionary replacement. Preserve existing behavior when
the option is omitted or false.

Example authoring shape, proposed rather than currently implemented:

```json
{
  "voiceTextNormalization": {
    "removeSpaces": true,
    "removeMiddleDots": true
  }
}
```

Displayed line, caption, heading, article, infographic and glossary spelling
remain unchanged. The option affects speech preparation only. It does not
remove sentence punctuation, dashes, hyphens or English U+00B7. Half-width U+FF65
variants and broader punctuation normalization are outside this bounded
proposal. Japanese production opts in explicitly; English defaults do not
change. Malformed/non-boolean options have structured configuration failures.

Expected examples:

| Display or reading source | Final spoken text when enabled |
| --- | --- |
| アプリケーション・モデル | アプリケーションモデル |
| ドメイン・モデル | ドメインモデル |
| オブジェクト・モデル | オブジェクトモデル |
| A dictionary reading テキスタス・ボック | テキスタスボック |
| 注文を確定する。 | 注文を確定する。 |

## Proposed authored post-utterance silence

Add optional `VideoScene.tailSilence`, in seconds, default zero.
A provided value is finite and nonnegative; malformed, negative, NaN or infinite
values fail validation before provider execution. Nested scenes inherit the
parent value like leadSilence; an explicit child value, including zero,
overrides it. Script/Storyboard conversions preserve the authored value.

For the final speaking scene, an author can specify:

```json
{
  "id": "closing-answer",
  "duration": 1,
  "leadSilence": 0.28,
  "tailSilence": 0.8
}
```

This requests an interval after the actual synthesized utterance, not another
leading pause or an extra infographic hold. The 0.8-second example is not a
global default or a speech-duration estimate.

### Timing proposal to freeze

Let T be the existing selected target duration, L the leading silence, A the
actual normalized WAV duration, and E the authored tailSilence.

```text
effective scene duration = max(T, L + A + E)
effective trailing silence = max(E, T - L - A, 0)
```

T retains the existing duration/targetDuration precedence and default behavior.
A long target supplies a minimum scene duration; E supplies a minimum
post-utterance interval. They overlap rather than add two trailing pads.
Omitted E reproduces the current formula.

The synthesized WAV's own phoneme padding is included in A. E is additional
silence after that WAV completes; this proposal does not reinterpret VOICEVOX
postPhonemeLength as a visual hold.

### Audio, picture and evidence alignment

Synthesis writes the effective trailing silence into combined audio and
records the actual trailing interval in the existing audio-manifest
tailSilence field. Distinguish requested and effective timing in authoring and
review evidence; do not silently repurpose the existing generated field.

Renderers retain the same scene image and caption while speech finishes and
the trailing interval runs. Characters are idle/mouth-closed during that
interval; no utterance is replayed. The next scene, infographic, or end card
starts only after the effective interval ends.

Use the established FPS quantization across rendering and review evidence.
Record authored requested tail and observed effective timing with compatible
handling for historical manifests. Do not append the same interval again at
assembly or visual-effect boundaries. Opening/summary/final holds remain
independent and unchanged unless explicitly authored elsewhere.

## Pipeline and currentness boundary

The ordinary script, supported Storyboard conversions, synthesis, renderer,
assembly and review-evidence routes must carry the admitted fields consistently.
No new publication command, external provider, custom parser or source rewrite
is implied.

Existing input/currentness identities must account for both policies. Reading
changes invalidate incompatible generated speech; timing changes invalidate
incompatible manifests, renders and evidence. Bind speech/voice/provider
identity through the applicable current mechanism rather than reusing an old
WAV solely because the displayed line is unchanged. Preserve the previous
successful artifacts on validation or generation failure, under existing run
isolation/checkpoint contracts.

## Executable acceptance scenarios

Use Given/When/Then and appropriate property-based specifications:

- Middle dots in original Japanese text and dictionary-generated readings are
  absent from provider input when enabled; source/display strings stay equal.
- Omitted/false options preserve old Japanese/English behavior and other
  punctuation; normalization is idempotent for the admitted operation.
- Absent/zero tails preserve legacy timing; short/long target durations and
  changing A satisfy the formula without double padding.
- Nested tail inheritance and explicit zero override survive supported
  round trips.
- Invalid values fail before provider I/O and leave the last successful output.
- Audio-manifest effective silence, rendered scene boundaries, idle character
  state and evidence agree within the admitted frame quantization contract.
- An isolated Article-9-shaped Japanese fixture retains the conversation
  screen after the last utterance, then holds the shared infographic silently
  for its separately configured five seconds and shows the credits card.
- Changed reading/timing settings cannot leave incompatible artifacts current.

Fixtures use local/fake providers for deterministic specifications. Optional
real local VOICEVOX and browser/video evidence are separate observations.
No test depends on publishing or editing the external Article 9 project.

## Scope and restart

Phase 61 owns these two video authoring policies and their end-to-end timing
and evidence integration. Existing Phase 30 workflow and Phase 31 encoding
contracts are reused; Phase 59/60 work and priorities remain independent.

Global punctuation cleanup, alternative voices, caption-layout polish,
PDF/slides, automatic content authoring, site registration, YouTube upload,
deployment and external-project edits are excluded.

Restart at P610-01 after reading this proposal, the decision journal, the Phase
checklist and the affected executable specifications.

- [Decision journal](../journal/2026/09/2026-09-14-video-middle-dot-and-tail-silence-decision.md)
- [Phase ledger](../phase/phase-61.md)
- [Checklist](../phase/phase-61-checklist.md)
