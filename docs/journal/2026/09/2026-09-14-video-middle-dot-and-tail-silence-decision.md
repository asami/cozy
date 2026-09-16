# Video Middle-Dot Reading and Post-Utterance Pause Planning Decision

Date: 2026-09-14
Development item: DEV-029

## Review context

The user reviewed the rebuilt Article 9 Japanese confirmation video and said
its content was basically acceptable. Two final production adjustments were
requested:

> 最後に霊夢が話終わってから、infographicに移る前には少し間が欲しい。
> アプリケーション・モデルのように「・」がある場合は、発音的には無視して「アプリケーションモデル」と呼ぶようにして。

The speaker name in the feedback is not a rule for selecting a character:
the requested interval follows the final actual utterance regardless of cast.
Displayed Japanese terminology retains its middle dot.

The subsequent user request explicitly asked to add both this reading policy
and a scene-authored `tailSilence: 0.8` extension as Cozy notes, journal and
Phase development items.

## Current implementation evidence

Read-only inspection found:

- Speech preparation currently normalizes spaces, then applies a single-pass
  pronunciation dictionary. There is no middle-dot normalization option.
- Per-script reading overrides can handle an individual term. Empty
  replacements are accepted, but a one-character removal override does not
  remove dots newly introduced by another dictionary replacement.
- VideoScene has leadSilence and target duration, but no authored tailSilence.
- Synthesis computes generated tailSilence as unused target-duration time;
  the audio manifest, rendering and review-evidence routes already use it.

Sources are linked in the proposal. These observations describe the current
local implementation, not a newly implemented capability or test receipt.

## Recorded development direction

Create **Phase 61: Video Speech Normalization and Post-Utterance Timing**,
with both requested capabilities as one bounded video-authoring integration
boundary.

1. Propose opt-in `voiceTextNormalization.removeMiddleDots`, removing U+30FB
   only from final provider speech text after dictionary substitution. This
   also handles replacement readings without changing displayed terms.
2. Propose authored per-scene tailSilence in seconds, including nested
   inheritance/override and a zero default for old scripts.
3. Base the trailing interval on synthesized WAV length, and preserve the
   same conversation picture with an idle character until that interval ends.
4. Preserve existing target-duration minimums, with requested and target-derived
   trailing time overlapping rather than being padded twice. The proposal
   records the formula to freeze in design/spec.
5. Keep infographic/credits holds independent. A 0.8-second trailing example
   is separate from the existing silent five-second summary.

An explicit silent scene and post-synthesis duration adjustment remain
one-off workarounds. The new authoring fields make the rule reusable when
wording or audio length changes.

## Handoff and work boundaries

- [Exploratory proposal](../../../notes/video-speech-normalization-and-tail-silence-proposal.md)
- [Phase 61](../../../phase/phase-61.md)
- [Sole completion ledger](../../../phase/phase-61-checklist.md)

P610-01 is the implementation restart point: freeze and promote the text,
nested-scene and timing contracts, then add executable specifications before
implementation. Continue through normalization, timing, pipeline evidence,
and isolated acceptance/closure.

The actual field names and evidence schema treatment are proposal inputs until
the contract gate is completed. The journal is historical trace, the notes are
non-normative exploration, and the Phase/checklist is work management.

Phase 59's existing priority and Phase 60's separate article/Core boundary
remain unchanged. Article 9 is a fixture driver; Phase 61 does not authorize
editing that project, generating its video, publishing, registering media,
deploying, committing or pushing.

This entry records documentation only. No product implementation, SBT
validation, audio/video regeneration, commit or publication was performed.
