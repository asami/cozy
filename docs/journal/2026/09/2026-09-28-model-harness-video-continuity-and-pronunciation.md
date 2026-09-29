# Model Harness video: continuity and compound-term speech

2026-09-28. User requested development-plan recording, with no Cozy repair in
this iteration. The article package may adjust its narration source now.

## Observations and ownership

The Model Harness Japanese video repeats a visual entrance when the speaker
changes on the same review/static-check/direct-execution screen. Existing Cozy
`DialogueVideo.jsx` supports `visual.baseSceneId` to retain the base scene at its
last frame. The consumer script now uses it for 17 question/answer pairs and
keeps their effects presets equal. This configuration has not yet been rendered;
it does not establish a product renderer defect or completed visual correction.

The previous build stopped before Cozy process creation during approval review.
The command-runner claim remains busy without a CLI result. This is an external
execution-management prerequisite, not an observed Cozy product failure. Do not
merge it into the approval-hash defect as if the causes were the same.

## Reading requested by the user

Display `モデル・アップ・ダウン`; speak `モデルアップダウン` continuously, without
an intentional pause at either middle dot. The consumer script uses the existing
`pronunciations` map so article text, captions and headings keep their spelling.
No synthesized audio or video is replaced in this iteration.

Phase 61 P610-02 already supplies speech-only U+30FB removal under opt-in
`voiceTextNormalization.removeMiddleDots`; Phase 61.1 closed its timing/pipeline
work. The follow-up is reliable propagation, authoring guidance and regression
coverage on the selected generation route, not a new global text-rewriting rule.

## Plan disposition

Recorded as open P710-F01–F03 in [Phase 71](../../../phase/phase-71.md): visual
continuity, pronunciation propagation/regeneration, and external execution
failure classification. Product fixes require reproduction and a later execution
request. Preserve existing output files, source identities, and historical phase
closure. No code changes, build, publication or upload is authorized by this memo.

## Regeneration attempt after execution-manager recovery

The user authorized recovery of the external execution manager. The confirmed
unstarted request was archived as cancelled-before-start, with no invented CLI
result. A fresh synthesis invocation then exited 2 in the Cozy launcher because
`target/cozy.d/runtime-classpath.txt` was stale. The launcher requests
`cozyExportRuntimeClasspath`. This is a development-runtime prerequisite;
pronunciation synthesis and video rendering have not run. Phase 71 records the
resume condition. Per the user's boundary, the article task did not build or
modify Cozy, and existing media remain intact.

## 2026-09-29 split ownership

The incident and failed regeneration above stay historical; no new synthesis
or render is claimed. [Phase 71](../../../phase/phase-71.md) retains P710-F03
development-classpath readiness and external-pre-start/Cozy-CLI attribution.
[Phase 71.2](../../../phase/phase-71.2.md) exclusively owns P710-F01 continuity
and P710-F02 pronunciation/payload/regeneration/actual-listening acceptance.
The existing Phase 61/61.1 baseline remains closed; a configured pronunciation
or baseSceneId is not proof of rendered/listened behavior.
[Phase 71.4](../../../phase/phase-71.4.md) verifies their integrated effect.
Split planning does not reset external runner ownership, alter skills or start
any child Phase or video regeneration.
