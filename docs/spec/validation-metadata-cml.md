# Validation metadata CML

`VALIDATION-METADATA` is an ordinary Kaleidox Model DocumentDivision root.
Its immediate, repeated `DECLARATION` children each contain exactly two
single-line assignments and no child sections:

```cml
# VALIDATION-METADATA

## DECLARATION
provider = "org.example.validation.SmokeDeclaration"
properties = {"purposes":["SMOKE"],"features":[{"scope":"component:example","identity":"feature-a"}]}

## DECLARATION
provider = "org.example.validation.EmptyDeclaration"
properties = {}
```

The root has no assignments. Other roots are absent metadata. Names are exact
and case-sensitive. A provider is an explicit qualified Scala class name:
one or more identifier package segments followed by an UpperCamel class name.
Scala keywords, duplicate full provider names, duplicate direct assignments,
unknown or missing assignments, malformed AST and invalid JSON are rejected
atomically with `Diagnostic(code, path, reason)` and a structured Conclusion.
JSON is decoded by the normal JSON library, including its ordinary object-key
semantics; a properties value is data, never executable provider source.

The properties object accepts only these normalized CNCF ABI fields:

| Field | Shape and default |
| --- | --- |
| `abiVersion` | exact Int JSON number, default `1` |
| `purposes` | optional string array; absent is `None`, `[]` is `Some(Set.empty)` |
| `features` | array of exact `{scope,identity}` string objects; default empty set |
| `expectedDuration` | optional exact `{amount,unit}`; amount an exact Long JSON number |
| `durationClass` | optional string |
| `executionRequirement` | optional requirement object below |
| `requiredSemantics` | string array, default empty set |

Duration units are exactly `NANOSECONDS`, `MICROSECONDS`, `MILLISECONDS`,
`SECONDS`, `MINUTES`, `HOURS`, `DAYS`. A requirement has required `capabilities`
(string array), `risk` (string), `reasoning` (`ROUTINE`, `STANDARD`, `DEEP`,
`CRITICAL`), and `reviewRequired` (boolean). Optional `reasoningClass` is a
string. Optional `disposition` is an exact object with string members
`placement` (`INLINE`, `DELEGATED`), `independence` (`OPTIONAL`, `REQUIRED`),
and `originContextIdentity`. Unknown keys, explicit nulls and wrong shapes
are rejected at every selected level. Strings retain their exact decoded
content; set-valued fields eliminate duplicate values.

No root or a root with no declarations generates nothing. Explicit
`properties = {}` creates a real empty declaration provider. Both ordinary
Modeler `generateScala` and `generateScalaValue` compose the generated Realm.
Each declaration emits one public concrete no-arg class under
`target/scala-3.3.8/src_managed/test/scala/<package>/<Class>.scala`.
Library generation rejects actual declarations with category
`validation-metadata`; absence keeps existing generation behavior.

The generated class extends CNCF `ValidationDeclarationProvider`, constructs
`ValidationCmlMappingV1.Properties` using typed expressions, and returns
`.declarationC.take`. CNCF owns semantic validity of purposes, feature
identities, duration values/classes, risks and required semantics. A
well-shaped semantic error remains an ordinary invalid provider/discovery
diagnostic when CNCF interprets the provider; generation does not fabricate
or recover a declaration.

The consumer authors existing `ValidationSpecification` / `ValidationScenario`
Java annotation anchors with `declaration = classOf[SmokeDeclaration]`.
Providers and anchors must compile into the same executable test artifact.
There is no ServiceLoader registration, generated test body or handwritten
suite modification. The existing
[sm-workflow annotation fixture](../../../sm-workflow/src/test/scala/org/simplemodeling/textus/smworkflow/admission/metafixture/SelectedMetadataFixtureSpec.scala)
illustrates consumer-owned anchors. The sm-workflow P6-TS01
consumer/discovery/UoW connection is a subsequent consumer boundary, not
delivered by this producer change.

Executable specifications:
[ValidationMetadataCmlSpec](../../src/test/scala/cozy/modeler/ValidationMetadataCmlSpec.scala),
[ValidationMetadataScalaGeneratorSpec](../../src/test/scala/cozy/modeler/ValidationMetadataScalaGeneratorSpec.scala).
See the [design](../design/validation-metadata-cml.md) and the ordinary
[fixture](../../src/test/resources/validation/metadata.cml). These describe
authoring contracts and make no execution or acceptance claim.
