# Phase 71 P710-02C: Native currentness and failure preservation

Date: 2026-09-29

Stage Status:

- Current status: implementation and focused validation complete; independent review, real-driver proof, Step and Phase acceptance pending
- Owner: PHASE-71/P710-02/P710-02C
- Update rule: evidence-only; record validation, review, acceptance, or closure only from separately verified evidence.

## Frozen implementation contract

P710-02C is limited to Cozy's native Storyboard assembly path. Native generated
handoff, confirmation/final mode, and part-artifact records use descriptive
v2 fields and omit local hash identities. Currentness uses the approved
declared-input `FileTime` policy, independently checks direct paths and video
format, and performs a read-only ffprobe on reuse. A current product is not
rewritten.

Regeneration stages output, native records, and applicable credits in a unique
mode attempt directory. It probes the staged output before bounded atomic
per-file installation, snapshots existing direct destinations, and restores
ordinary I/O failures where possible. Incomplete rollback retains the staging
recovery location and its causes. This is not a crash-safe transaction,
concurrency protocol, receipt system, or producer planner.

Native final metadata validation has no runner. It requires the direct final,
stored valid ffprobe summary, matching known v2 settings, and current declared
inputs. The existing independent review evidence continues to own its
frame/audio/hash validations.

## Preserved evidence and remaining work

The prior P710-02A and P710-02B evidence remains unchanged. This record does
not alter accepted Storyboard v1/v2 source semantics, legacy BuildReplay,
public identity/review models, Credits digest serialization, renderer
integrity, or cross-media schemas; their assigned Phase 71.1/71.2/71.3/71.4
work remains separate.

The real private Core -> authored DSL -> nine-scene Storyboard -> generated
Japanese video/six-case driver, focused Step validation, independent Step
review, Step commit, full Phase review, release, and final-only full-suite
disposition remain mandatory and are not claimed here.

Focused validation evidence (2026-09-29): representative suites passed 55/55;
the eleven-suite P710-02 accumulator passed 239/239 on the same candidate tree.
`cozyExportRuntimeClasspath` completed in the accumulator invocation. Both SBT
runs terminated successfully with the shared serialization lock released.
These results do not establish independent review, actual-video/six-case proof,
Step acceptance, or Phase closure.
