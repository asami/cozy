# Library generation design

`ModelGenerationTarget` is a sealed value passed from Cozy command intake to the Kaleidox `Modeler` and then to `ScalaGenerator`. Its default is `Cncf`, preserving the existing public generation route and all CNCF runtime emitters.

For `Library`, command intake validates the explicit target and exact running Cozy version, then preflights the source categories before Kaleidox evaluation. The value projection also checks the `ModelBuilder` fields and parsed composite/workflow definitions before transforming. This prevents a runtime model from being silently reduced to a partial library result or reported only as an SError inside evaluation.

`ScalaGenerator` branches before transformation. The library branch checks the SimpleModel input before transforming and returns only the transformer realm. The CNCF branch retains the existing composite-state-machine, logical-action, workflow, provided API, candidate-admission, and component metadata producers. Model metadata remains Cozy command-side output in both modes.

The sbt bridge carries `generation.target` as part of the project/owning-build agreement. Library dispatch selects `modeler-scala-value` and constructs only canonical component identity, `--generation-target library`, and the exact Cozy version. The bridge never calls the CNCF version-argument builder in this branch. The plugin includes the target in its generation state and requires metadata in either mode, while requiring provenance only for CNCF output.
