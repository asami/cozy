# Library generation contract

## Scope

`modeler-scala-value --generation-target library` produces pure Scala models for a Maven library. The target is explicit and does not infer its mode from the classpath, a descriptor, or the absence of a descriptor.

## Invocation

Library generation requires all of the following:

- the `modeler-scala-value` command;
- `--generation-target library`;
- `--cozy-generator-version` equal to the executing Cozy version; and
- canonical component namespace and ID when called through the sbt bridge.

It rejects unknown targets, the `modeler-scala` command, any CNCF version, runtime descriptor, or descriptor digest. The bridge accepts library dispatch only when `generation.target=library` agrees between project configuration and the owning build.

## Output

The library target retains generated values, datatypes, powertypes, plain state-machine descriptors, declarations, package ownership, and model metadata. `COMPONENT` and `PACKAGE` headers provide namespace and type ownership only.

The target does not emit component APIs, runtime bootstrap files, composite state-machine artifacts, logical-action programs, workflow or provided-operation ABI, candidate-admission ABI, runtime metadata, or generation provenance.

## Runtime boundary

Library generation rejects entities, services, operations, events, actors, composite state machines, actions, workflows, provided operations, and candidate admission with `LIBRARY_GENERATION_UNSUPPORTED_RUNTIME`. The diagnostic names each detected category and directs callers to `--generation-target cncf`. Schemas remain valid type declarations, and standalone plain state machines remain supported.

## sbt policy

`cozyGenerationTarget` defaults to `library` only for `project.kind: library` with Maven packaging; all other projects default to `cncf`. CAR and SAR projects can never select `library`. A library target has no CNCF runtime descriptor extraction and no CNCF generation provenance requirement, but still requires model metadata side output. Its generation state includes `generation.target` so a target switch invalidates prior output.
