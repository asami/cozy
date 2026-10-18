# Validation metadata producer design

The frozen P62-FIX-K1 producer boundary projects ordinary Kaleidox
`DocumentDivision` / `LogicalSection` AST into object-owned typed source
models, then into the existing CNCF normalized source ABI. Cozy does not
link the Scala 3 CNCF artifact into its Scala 2.12 frontend. It emits typed
Scala 3 source instead. The [public syntax specification](../spec/validation-metadata-cml.md)
defines the closed source shape.

`ValidationMetadataCml` selects exact `VALIDATION-METADATA` roots and reads
only immediate prologue property lines and `DECLARATION` children. The
ordinary JSON library decodes each assignment literal, including nested
properties objects. Shape/type checks and explicit Scala provider-name
admission precede rendering. A complete extraction returns either ordered
declarations or a structured diagnostic, so malformed later input cannot
publish an earlier partial provider Realm.

`ValidationMetadataScalaGenerator.fromModel` returns `None` for absence and
`Some(Realm)` for actual declarations, including an explicitly empty
properties object. Each explicit qualified name determines its Test source
path; no component lookup, namespace inference or output-path override is
involved. Strings are quoted as ordinary Scala literals. Set fields render
in a deterministic order, and provider files follow declaration order.

Both Modeler Scala entry points extract validation metadata before their
existing model generation. ScalaGenerator accepts an additional default
`None` Realm option, composes it into ordinary CNCF output, and rejects
nonempty metadata for Library output using the existing unsupported-runtime
diagnostic style and category `validation-metadata`. Existing callers retain
the default absence behavior.

After normal Modeler shape validation, Composite and Workflow extraction
exclude the separately owned `VALIDATION-METADATA` root. Every other workflow
source remains subject to the existing declaration-ownership and execution-safety
guards.

The generated no-arg provider constructs
`ValidationCmlMappingV1.Properties` and returns its `.declarationC.take`.
Only finite labels needed to construct the current typed ABI are interpreted
by Cozy. CNCF continues to own normalized declaration validation, annotation
discovery, merge policy and execution semantics. Semantic errors therefore
surface when the consumer interprets the provider through ordinary CNCF
discovery, without a fallback declaration.

Consumers compile generated sources and their explicitly authored Java
annotation anchors into the same executable test artifact. This producer
does not inject annotations, create registrations or execute tests. The
subsequent sm-workflow P6-TS01 work owns the real compiled discovery and UoW
connection. Its existing
[annotation fixture](../../../sm-workflow/src/test/scala/org/simplemodeling/textus/smworkflow/admission/metafixture/SelectedMetadataFixtureSpec.scala)
shows the consumer-owned anchoring boundary.

The executable contracts are
[ValidationMetadataCmlSpec](../../src/test/scala/cozy/modeler/ValidationMetadataCmlSpec.scala)
and [ValidationMetadataScalaGeneratorSpec](../../src/test/scala/cozy/modeler/ValidationMetadataScalaGeneratorSpec.scala).
They include ordinary fixture parsing, syntax rejection, literal/order
properties and both normal Modeler paths. Validation and acceptance results
remain the parent workflow's responsibility.
