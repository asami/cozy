# Phase 16 Driver Breaking Version

Date: 2026-07-16

## Context

Phase 16 deliberately changes generated Scala and CAR contracts for operation
Values, nominal Datatypes, semantic predefined types, Powertypes,
Statemachines, structured Records, and locale-aware text. The driver CARs must
record that break in their development version instead of hiding it behind
String adapters or compatibility codecs.

## Decision

Both regression drivers use `0.6.0-SNAPSHOT` for Phase 16 development:

- `textus-user-notification` moved from published stable `0.1.1` in commit
  `6eb4004`;
- `textus-user-account` moved from published stable `0.1.4` in commit
  `3aceef9`.

In each repository, `build.sbt` and `project.yaml` declare the same component
version. The published CAR catalogs continue to describe the existing stable
releases until a `0.6.0` release is actually published; they are not rewritten
to advertise a development snapshot as stable.

No compatibility adapter restores the former generated primitive-String or
legacy operation-input contract. The generated ABI manifest and the
major/minor version change make the new contract explicit. Comparison against
a published baseline manifest remains a separate release-readiness check.

## Verification

`sbt --batch cozyBuildCar` succeeded for both drivers. The generated artifacts
are:

- `textus-user-notification-0.6.0-SNAPSHOT.car`;
- `textus-user-account-0.6.0-SNAPSHOT.car`.

Each CAR contains both `component-descriptor.json` and `abi-manifest.json`.
Their component/CAR name and version agree with the build and project metadata:

```text
textus-user-notification @ 0.6.0-SNAPSHOT
textus-user-account      @ 0.6.0-SNAPSHOT
```

The driver READMEs now distinguish the published stable version from the Phase
16 development version and require build, project, generated descriptor, and
ABI version consistency before publication.
