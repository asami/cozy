# Cozy test policy

Status: normative

## Execution categories

`src/test/scala` contains Executable Specifications. Keep Given / When / Then,
behavior assertions and meaningful property coverage in every category.

- **FULL** (`test`): basic success and failure routes of the major features,
  lightweight regressions and representative integrations. A test is FULL
  unless explicitly tagged HEAVY.
- **Focused** (`testOnly <suite> ...`): select specifications affected by the
  change. Without tag arguments this runs all cases in the selected suites,
  including HEAVY.
- **HEAVY**: high-volume generation, large permutation/property matrices and
  native media toolchain integration. Run affected HEAVY cases when their
  contracts change and before releases requiring that coverage. HEAVY is not
  an automatic follow-up to every FULL run.

HEAVY uses ScalaTest's existing `org.scalatest.tags.Slow`, as in CNCF.
Mixed suites tag individual leaves with `taggedAs org.scalatest.tagobjects.Slow`.
Only wholly native-toolchain suites use `@org.scalatest.tags.Slow`.
Tags select execution cost; they do not replace specification identifiers.
Keep property sample counts, seed requirements, assertions and negative cases
intact. Do not speed up FULL by excluding whole feature packages, dropping
basic integration paths or enabling parallel execution over JVM-global Locale.

## Selection

Use the standard SBT commands through the prescribed serialized execution route:

```text
test
testOnly * -- -n org.scalatest.tags.Slow
testOnly cozy.modeler.CmlSemanticPublicationCompatibilitySpec
testOnly cozy.modeler.CmlSemanticPublicationCompatibilitySpec -- -l org.scalatest.tags.Slow
testOnly cozy.modeler.CmlSemanticPublicationCompatibilitySpec -- -n org.scalatest.tags.Slow
```

The exclusion is scoped to `Test / test / testOptions`; `testOnly` has no
default exclusion. `-n` includes the selected tag and `-l` excludes it.
A successful command running zero tests is not HEAVY validation. Report actual
counts and report omitted HEAVY as NOT RUN. Opt-in Docker/VOICEVOX settings,
available SDKs, warehouse settings and other existing prerequisites still apply;
a tag never grants external execution or converts a canceled test into a pass.

## Retained basic coverage

| Surface | FULL coverage retained | HEAVY extension |
| --- | --- | --- |
| Semantic publication and JSON-only consumers | Complete declared/absence resources, strict supplier versions, opaque peers, canonical reconsumption, all fixed question answers | 100-sample recursive reconstruction, adversarial and execution-permutation matrices |
| Causal, Terminology, Use Case and Dynamic fixtures | Complete fixed resources, independent expected facts, references, traversal and rejection routes | 100-sample multi-carrier permutation matrices |
| CAR lint | Integrated build/CML/ABI/documentation findings, accepted and excluded identities, canonical/legacy entry points | Repeated generated compatibility identities and entry-point combinations |
| Capability package and Failure Model ABI | Ordinary generated products, mixed Workflow products, selected files and path rejection | Repeated location and package permutations |
| Presentation, cross-review, slides PDF and article video | Public build/verify, actual artifacts, reuse and malformed-input routes | At least 50 independently reset generated trials |
| Video | Public staging, narration with controlled runners, scene/timing/PCM boundaries and failure routes | Actual Docker Remotion rendering and VOICEVOX synthesis |

Other existing suites remain in FULL, including ordinary source generation,
Logical UI, Workflow, BoK/site, publication, archives and Dart consumer checks.
Small pure properties remain FULL. A quick bounded external-process smoke may
remain FULL when it is the representative integration route.

## Fixture lifecycle and measurement

Reuse immutable classpath resources and derived immutable expected data when
their contents are identical across cases. Keep mutable output directories,
processes and generated evidence independent with their existing cleanup.
Reconstructed property inputs and producer/consumer actions whose variation is
under test must still execute for every sample; never replace those with a cache.

The semantic publication question fixture shares one lazy immutable baseline
within its suite. Its HEAVY property still constructs fresh contexts from each
reordered JSON input and evaluates every question occurrence.

Use retained SBT logs and JUnit suite/testcase times to select expensive cases.
Distinguish SBT build/startup overhead from test duration, and identify report
timestamps: reports from different runs cannot provide an exact breakdown of
an older run. Retain detailed execution evidence outside Git. Revisit duplicate
setup/generation before expanding exclusions; do not create a custom selector
or a separate classification registry.
