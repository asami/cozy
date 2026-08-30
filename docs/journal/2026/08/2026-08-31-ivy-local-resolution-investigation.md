# Ivy Local Resolution Investigation

Date: 2026-08-31

Status: verified incident record; non-normative

## Context

SmartDox was moved to `org.goldenport:goldenport-scala-lib_2.12:2.3.32-SNAPSHOT`.
The development workflow publishes goldenport-scala-library with SBT
`publishLocal`; it does not normally invoke `publishM2` directly. Resolution
initially appeared to succeed after removing `useCoursier := false` and custom
Ivy resolver wiring while retaining Maven local. That result was misleading:
it did not yet prove that the current `publishLocal` artifact was being used.

The investigation covered the active local development chain:

```text
goldenport-scala-library
  -> SmartDox
  -> Cozy
```

## Evidence that obscured the incident

The same development coordinate existed in two local repositories:

| Repository | Origin | Relevant timestamp |
|---|---|---|
| `~/.ivy2/local` | `publishLocal` | 2026-08-30 18:45 JST |
| `~/.m2/repository` | an earlier `publishM2` | 2026-08-25 12:57 JST |

The Ivy-local and Maven-local JARs had the same SHA-256 at investigation time.
SmartDox also retained an older SBT update-cache record pointing to the Maven
local JAR. Consequently, a successful build could not distinguish these two
cases:

1. the newly published Ivy-local artifact was resolved correctly; or
2. the older Maven-local artifact happened to contain identical bytes.

This combination made a repository-selection problem look like either a
`publishLocal` metadata defect or a harmless resolver change.

## Root cause and rejected interpretation

The confirmed operational mismatch was:

- the producer command was `publishLocal`, whose local publication destination
  is SBT's Ivy local repository; while
- the active root builds and newly generated Cozy component builds explicitly
  selected Maven local.

The earlier interpretation that `Resolver.mavenLocal` was required for the
current goldenport-scala-library development artifact was incorrect. Maven
local only worked because a compatible older copy was still present.

The successful change was not a return to the legacy Ivy dependency manager.
Coursier remains enabled. Ivy local is the artifact repository, selected with
SBT's standard `Resolver.defaultLocal`; `useCoursier := false` and a manually
constructed `Resolver.file(...)(Resolver.ivyStylePatterns)` are unnecessary.

## Bounded correction

The active path was aligned as follows:

- goldenport-scala-library root build: `Resolver.defaultLocal`;
- SmartDox root build: `Resolver.defaultLocal`;
- Cozy root build: `Resolver.defaultLocal`;
- Cozy-generated CAR, CAR/SAR, and plugin builds: `Resolver.defaultLocal`;
- generated-build Executable Specifications: require
  `Resolver.defaultLocal`, reject `Resolver.mavenLocal`, and continue to reject
  `useCoursier := false` and manual `Local Ivy` wiring.

Versions and dependency coordinates were not changed. Pre-existing unrelated
SmartDox and Cozy worktree changes were preserved.

Legacy scripted fixture builds and the generated runtime-script repository
list were intentionally outside this bounded verification. They should not be
used as evidence for or against the active root/generator resolution contract.

## Verification

The following serial SBT path completed successfully:

1. goldenport-scala-library `publishLocal` published
   `goldenport-scala-lib_2.12:2.3.32-SNAPSHOT` to `~/.ivy2/local`;
2. SmartDox `publishLocal` published
   `smartdox_2.12:2.4.18-SNAPSHOT` to `~/.ivy2/local`;
3. SmartDox `reload; update` refreshed the prior resolver cache;
4. Cozy `testOnly cozy.modeler.ModelerScaffoldSpec` passed 14 of 14 tests.

After refresh, SmartDox's update record resolved goldenport-scala-library from:

```text
~/.ivy2/local/org.goldenport/goldenport-scala-lib_2.12/
  2.3.32-SNAPSHOT/jars/goldenport-scala-lib_2.12.jar
```

Cozy's update record resolved both local SNAPSHOTs from Ivy local:

```text
~/.ivy2/local/org.smartdox/smartdox_2.12/
  2.4.18-SNAPSHOT/jars/smartdox_2.12.jar

~/.ivy2/local/org.goldenport/goldenport-scala-lib_2.12/
  2.3.32-SNAPSHOT/jars/goldenport-scala-lib_2.12.jar
```

`git diff --check` also passed in all three repositories. This was a focused
incident verification; it was not a repository-wide full-test gate.

## Operational lesson

For this development chain, the intended local contract is:

```text
publishLocal
  -> ~/.ivy2/local
  -> Resolver.defaultLocal with Coursier enabled
```

When diagnosing a local SNAPSHOT, command success and artifact hashes are not
sufficient evidence if the same coordinate exists in multiple repositories.
Inspect the refreshed SBT update record and identify the actual artifact URL.
If a resolver or dependency declaration has changed, explicitly refresh
resolution before drawing a conclusion from an old update cache.

Avoid interpreting a surviving Maven-local copy as evidence that
`publishLocal` populates or requires Maven local. If Maven local is needed by a
separate workflow, that workflow should use an explicit publication and
consumption contract rather than silently sharing the same SNAPSHOT coordinate.
