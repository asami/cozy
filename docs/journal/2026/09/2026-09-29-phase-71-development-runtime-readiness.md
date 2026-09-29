# Phase 71: development runtime readiness and pre-start failure boundary

Date: 2026-09-29

Status: P710-F03 readiness accepted; Phase 71 remains in progress

Tracking: Phase 71 / P710-F03 / P710-F03A

## Failure chronology

The original external pre-start runner failure occurred before the Cozy CLI
invocation. It is recorded as a separate boundary from the later Cozy launcher
observation; this entry does not assign a new cause or ownership to that
external failure.

A subsequent `video synthesize` reached the Cozy launcher and exited with
status 2 because the development classpath was stale at
`target/cozy.d/runtime-classpath.txt`. The launcher requested the normal
`cozyExportRuntimeClasspath` development export. Synthesis and rendering had
not started, and no article task built or changed Cozy.

## Preparation and native inspection evidence

The task-private preparation utility is
`scripts/test/prepare-phase71-private-driver.sh`. It created the 155 selected
Document Project and media files without overwriting an existing destination or
accepting generated build/target outputs as input. The first rsync copy lost
fractional mtimes; that loss was diagnosed and corrected. Exact bytes, modes,
and mtimes, together with unchanged originals, were then proven. A normal
existing-destination refusal also passed.

The first native development JA video inspection exited 1 with
`Unknown video credit profile: simplemodeling-org` because the private project
marker and credit catalog were missing. There was no timeout; the terminal
session completed and was idle.

The frozen context repair then completed successfully with exit status 0. It
added only these original files to the existing private context:

- `conf/cozy/config.yaml`
- `conf/cozy/video/credit-profiles/simplemodeling-org.yaml`

The private copy now contains exactly 157 selected files. All 157 copied files
match their original bytes, modes, and precise mtimes; the previous 155 files
and all 157 source files remained unchanged. No media build output was copied,
the Document build configuration remained present, and no launcher configuration
was added. A subsequent default invocation still refused the existing target
with exit status 1 as expected and performed no writes.

The following development-runtime readiness operations completed successfully
after the stale-classpath observation:

- `cozyExportRuntimeClasspath` exited 0, with the serial SBT terminal lock
  released.
- `cozy --runtime-dev-dir <Cozy root> version` exited 0 and reported Cozy
  `0.3.3-SNAPSHOT`.
- `cozy --runtime-dev-dir <Cozy root> runtime config show` exited 0 and showed
  the explicit development Cozy selection and enabled development runtime.

These results establish development-runtime readiness only. After context
repair, the fresh native development JA video inspection exited 0 with empty
stderr and no timeout. Native inspection discovered the copied project marker
and credit catalog. It performed no synthesis, rendering, or provider transfer.
The current descriptor still reports a `dialogue` part backed by `script.json`
with 20 scenes. This is readiness evidence only; it does not prove the
authoritative 9-scene Storyboard/Core graph or generation acceptance, and
P710-01/P710-02 remain pending. No code fix, generated video, full-suite test result,
renderer result, Step acceptance, or Phase acceptance is claimed by this
chronology.

## P710-F03 Step acceptance record

Completed Slice: P710-F03A. Work class: C. The independent Step-lightweight
review accepted the complete readiness accumulator with PASS and no Current
Boundary Blocker, Hygiene, or Development Candidate. Its typed Review
Disposition V3 bundle and goal-state verification both succeeded.

The accepted result is the development classpath refresh, exact private-input
preparation, native version/configuration/JA inspection, and separate failure
attribution recorded above. The preparation utility and this record form the
P710-F03 Step acceptance commit. Existing Phase and successor planning remains
in the working tree for its own closure boundary; it is not included in this
Step commit. No Scala source changed, and Cozy remains `0.3.3-SNAPSHOT`.

This Step does not accept Core/Storyboard propagation, generated video,
digest-purpose inventory, or Phase closure. P710-01 and P710-02 remain pending;
repository-full SBT remains explicitly deferred-not-run to PHASE-71.4.
