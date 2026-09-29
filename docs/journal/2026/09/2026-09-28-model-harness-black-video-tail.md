# Model Harness video: reported black ending

Date: 2026-09-28
Disposition: Phase 71 P710-F04, planned; no Cozy implementation in this task.

The user reports a black screen at the end of the Japanese Model Harness
video, and requests Japanese and English deliveries with the tail physically
cut, plus a Cozy development-plan item. Preserve the uncut originals. This
immediate media postprocess must not be represented as a Cozy product fix.

## Evidence

SimpleModeling.org source artifact:
`src/main/media/development-process/model-harness/video/ja/build/model-harness-ja.mp4`.
SHA-256: `30ceb6859c3a48c1b5c4bc48ed809a6f9f382b7e35e230ea6573d83ac205431e`.
Runtime: locally published Cozy 0.3.3-SNAPSHOT.

- Video: 8077 frames at 18 fps, duration 448.722222 s.
- Audio: duration 448.768 s, 0.045778 s beyond video.
- Final-card sequence: frames 8041 through 8076, opaque SVG.
- Existing evidence at 447.722 s has zero near-black pixels. It samples the
  card midpoint, not the literal last frame.
- Generated Root.tsx sets the final asset opacity to 1, with no final fade.
- Final assembly uses stream-copy concatenation.

These observations do not establish the cause. Investigate frame boundaries,
audio encoder padding/container timestamps, and player behavior separately.
The acceptance target is a decodable final card at the common media end with
all narration retained. Add final-frame and timestamp evidence to regression
coverage. No new approval or hash gate is requested.

## 2026-09-29 split ownership

[Phase 71.4](../../../phase/phase-71.4.md) exclusively owns P710-F04.
The 0.045778 s overhang and midpoint end-card sample remain observations, not
an established root cause. That Phase must diagnose last frames/render frame
count/mux timestamps/player EOF, preserve all narration and the opaque end card,
and prove Japanese/English direct-render/final-assembly behavior.
No temporary external tail cut is credited as Cozy implementation. Existing
outputs remain intact; this planning split starts no repair or media execution.
