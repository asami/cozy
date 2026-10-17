package cozy.modeler

import scala.util.Try
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.collection.VectorMap
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.kaleidox.model.{ComponentSubsystemModel, OperationModel, ServiceModel, ValueModel}
import org.goldenport.realm.Realm

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
class DartPackageGenerationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Canonical Dart packages" should {
    "reuse actual DTO identities and complete the public layout (DA-14, DA-16, DA-17)" which {
      "join parsed qualified shared DTOs, declared error roots, inline values and source void" in {
        Given("parsed canonical CML with one explicit owner, shared qualified DTOs, a declared failure value and inline values")
        val model = DartPackageSpecFixtures.model
        val options = DartPackageSpecFixtures.options
        When("the pure entry generates a package and the canonical operation selection is observed")
        val products = DartPackageSpecFixtures.products(DartPackageGenerator.generate(model, options))
        val builder = Modeler.ModelBuilder(model)
        val operations = DartOperationProjection.select(model, builder, builder.buildValue(), Vector.empty)
        Then("the six products reuse shared typed DTOs and preserve exact canonical identity, kind and void signatures")
        products.keySet shouldBe DartPackageSpecFixtures.paths("phase65_api").toSet
        val api = products("lib/src/api.dart")
        api should include("abstract interface class SampleComponentCatalogApi")
        api should include("Future<_models.SharedResponse> lookup(_models.SharedRequest request);")
        api should include("Future<_models.SharedResponse> class_(_models.SharedRequest request);")
        api should include("Future<_models.InlineResponse> inline(_models.InlineRequest request);")
        api should include("Future<void> ping();")
        api should include("Future<_models.SharedResponse> refresh();")
        api should include("Future<void> consume(_models.SharedRequest request);")
        api should include("\"identity\":\"SampleComponent.Catalog.lookup\"")
        api should include("\"kind\":\"QUERY\"")
        api should include("\"input\":\"dart.api.SharedRequest\"")
        products("lib/src/models.dart").split("class SharedRequest \\{", -1).length shouldBe 2
        products("lib/src/models.dart").split("class SharedResponse \\{", -1).length shouldBe 2
        products("lib/src/models.dart") should include("class DeclaredFailure")
        products("lib/src/models.dart") should include("final String class_;")
        products("lib/src/models.dart") should include("'some-key'")
        operations.find(_.definition.name == "lookup").get.input.get.qualifiedName shouldBe "dart.api.SharedRequest"
        operations.find(_.definition.name == "class").get.input.get shouldBe operations.find(_.definition.name == "lookup").get.input.get
        operations.find(_.definition.name == "inline").get.input.get.qualifiedName shouldBe "dart.api.value.InlineRequest"
        operations.find(_.definition.name == "ping").get.input shouldBe None
        operations.find(_.definition.name == "ping").get.output shouldBe None
        products("pubspec.yaml") shouldBe "name: phase65_api\nenvironment:\n  sdk: '>=3.0.0 <4.0.0'\n"
        products("lib/phase65_api.dart") should include("export 'src/api.dart';")
        products("lib/phase65_api.dart") should include("export 'src/json.dart';")
      }

      "union explicit error roots with the selected operation closure and isolate unrelated unsupported data" in {
        Given("a parsed model with an unrelated decimal value and selected shared operations plus the declared error root")
        val model = DartPackageSpecFixtures.parse(DartPackageSpecFixtures.source + DartPackageSpecFixtures.unsupported)
        val options = DartPackageGenerator.Options("phase65_api", Vector("dart.api.DeclaredFailure"),
          Vector("SampleComponent.Catalog.lookup", "SampleComponent.Catalog.class"))
        When("the selected canonical closure is generated")
        val products = DartPackageSpecFixtures.products(DartPackageGenerator.generate(model, options))
        Then("shared request, response and error are emitted once, and unrelated unsupported and inline values are excluded")
        products("lib/src/models.dart") should include("class SharedRequest")
        products("lib/src/models.dart") should include("class SharedResponse")
        products("lib/src/models.dart") should include("class DeclaredFailure")
        products("lib/src/models.dart") should not include "Unsupported"
        products("lib/src/models.dart") should not include "InlineRequest"
      }

      "keep all-void selection deliberately empty while rejecting an invalid sealed root" in {
        Given("an unsupported unselected value, a selected all-void operation, and no explicit data root")
        val model = DartPackageSpecFixtures.parse(DartPackageSpecFixtures.source + DartPackageSpecFixtures.unsupported)
        val options = DartPackageGenerator.Options("phase65_void", operationNames = Vector("SampleComponent.Catalog.ping"))
        When("the void closure and a sealed selection outside that closure are requested")
        val products = DartPackageSpecFixtures.products(DartPackageGenerator.generate(model, options))
        val invalid = Try(DartPackageGenerator.generate(model, options.copy(sealedTypeNames = Set("dart.api.SharedResponse"))))
        Then("the complete package contains no model import or unit DTO and the sealed root fails")
        products.keySet shouldBe DartPackageSpecFixtures.paths("phase65_void").toSet
        products("lib/src/api.dart") should include("Future<void> ping();")
        products("lib/src/api.dart") should not include "import "
        products("lib/src/models.dart") should not include "class "
        invalid.isFailure shouldBe true
        invalid.failed.get.getMessage should include("sealed selection requires a selected finite powertype")
      }

      "emit a complete package for an empty canonical model" in {
        Given("an actual parsed empty CML model")
        val model = DartPackageSpecFixtures.parse("")
        When("the empty package is generated")
        val products = DartPackageSpecFixtures.products(DartPackageGenerator.generate(model, DartPackageGenerator.Options("empty_api")))
        Then("all six paths and technical JSON contracts exist without fabricated model or operation classes")
        products.keySet shouldBe DartPackageSpecFixtures.paths("empty_api").toSet
        products("lib/src/models.dart") should not include "class "
        products("lib/src/api.dart") should not include "class "
        products("lib/src/json.dart") should include("class DartJsonDecodingException")
      }

      "reject malformed CML with its canonical parse diagnostics" in {
        Given("actual malformed CML with an unterminated feature schema")
        val model = DartPackageSpecFixtures.parse("# ENTITY\n\n## Broken\n\n### FEATURES\n\nschema = [\n")
        When("the pure entry is asked to generate a package from that parsed model")
        val result = Try(DartPackageGenerator.generate(model, DartPackageGenerator.Options("invalid_api")))
        Then("canonical parse errors prevent generation and each diagnostic is retained in the failure")
        model.errors.nonEmpty shouldBe true
        result.isFailure shouldBe true
        model.errors.foreach { error => result.failed.get.getMessage should include(error.en) }
      }

      "retain the all-data default when both root selections are empty" in {
        Given("the parsed representative model and default operation/data selections")
        val model = DartPackageSpecFixtures.model
        When("default generation and default generation with unrelated unsupported data are requested")
        val products = DartPackageSpecFixtures.products(DartPackageGenerator.generate(model, DartPackageGenerator.Options("phase65_api")))
        val invalid = Try(DartPackageGenerator.generate(DartPackageSpecFixtures.parse(DartPackageSpecFixtures.source + DartPackageSpecFixtures.unsupported),
          DartPackageGenerator.Options("phase65_api")))
        Then("the default includes declared data values and still rejects a selected unsupported default-all type")
        products("lib/src/models.dart") should include("class DeclaredFailure")
        invalid.isFailure shouldBe true
        invalid.failed.get.getMessage should include("unsupported primitive")
      }
    }

    "produce stable trees from equivalent canonical selections (DA-13)" which {
      "repeat and permute actual source divisions plus selected roots with a ScalaCheck property" in {
        Given("a parsed source model, its permuted divisions, and unique canonical type/operation roots")
        val model = DartPackageSpecFixtures.parse(DartPackageSpecFixtures.source + DartPackageSpecFixtures.unsupported)
        val types = Vector("dart.api.DeclaredFailure", "dart.api.SharedRequest", "dart.api.SharedResponse")
        val operations = Vector("SampleComponent.Catalog.lookup", "SampleComponent.Catalog.class", "SampleComponent.Catalog.refresh")
        val options = DartPackageGenerator.Options("phase65_api", types, operations)
        val selections = for {
          selectedtypes <- Gen.pick(types.size, types).map(_.toVector)
          selectedoperations <- Gen.pick(operations.size, operations).map(_.toVector)
          reversed <- Gen.oneOf(true, false)
        } yield (selectedtypes, selectedoperations, reversed)
        When("repeated generation and every ScalaCheck permutation generate their actual package trees")
        val expected = DartPackageSpecFixtures.products(DartPackageGenerator.generate(model, options))
        val repeated = DartPackageSpecFixtures.products(DartPackageGenerator.generate(model, options))
        val property = Prop.forAll(selections) { case (selectedtypes, selectedoperations, reversed) =>
          val source = if (reversed) model.copy(divisions = model.divisions.reverse) else model
          DartPackageSpecFixtures.products(DartPackageGenerator.generate(source,
            options.copy(typeNames = selectedtypes, operationNames = selectedoperations))) == expected
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(30).withInitialSeed(Seed(653L)), property)
        Then("all equivalent root/source iteration orders have identical LF contents and the same complete paths")
        repeated shouldBe expected
        withClue(result.toString) { result.passed shouldBe true }
        result.succeeded should be >= 30
        expected.values.foreach { source => source should not include "\r" }
      }
    }

    "reject unresolved selection, metadata and ownership (DA-15, DA-18)" which {
      "reject an unknown full operation identity" in {
        Given("a parsed representative source and an exact identity that is not declared")
        val model = DartPackageSpecFixtures.model
        val options = DartPackageGenerator.Options("phase65_api", operationNames = Vector("SampleComponent.Catalog.missing"))
        When("that exact selected operation is generated")
        val result = Try(DartPackageGenerator.generate(model, options))
        Then("generation fails with the selected identity")
        result.isFailure shouldBe true
        result.failed.get.getMessage should include("Unknown selected operation 'SampleComponent.Catalog.missing'")
      }

      "reject an unknown canonical type root" in {
        Given("a parsed model and a nonexistent qualified data root")
        val options = DartPackageSpecFixtures.options.copy(typeNames = Vector("dart.api.Missing"))
        When("the unknown canonical root is generated")
        val result = Try(DartPackageGenerator.generate(DartPackageSpecFixtures.model, options))
        Then("the root fails canonical resolution before a Realm is returned")
        result.isFailure shouldBe true
        result.failed.get.getMessage should include("unresolved canonical identity 'dart.api.Missing'")
      }

      "reject duplicate selected identities" in {
        Given("a valid source operation repeated in the explicit selection")
        val options = DartPackageGenerator.Options("phase65_api", operationNames = Vector.fill(2)("SampleComponent.Catalog.lookup"))
        When("the duplicated selection is generated")
        val result = Try(DartPackageGenerator.generate(DartPackageSpecFixtures.model, options))
        Then("duplicate selection cannot choose an arbitrary operation winner")
        result.isFailure shouldBe true
        result.failed.get.getMessage should include("operation selection: duplicate")
      }

      Vector("kind", "input", "output", "untyped").foreach { missing =>
        s"reject a declared source operation with missing $missing metadata" in {
          Given(s"the actual source lookup operation with $missing metadata removed")
          val original = DartPackageSpecFixtures.model
          val service = original.getServiceModel.get.classes.values.head
          val operation = service.operations.operations.get("lookup").get
          val changed = missing match {
            case "kind" => operation.copy(kind = None)
            case "input" => operation.copy(input = operation.input.copy(tpe = None))
            case "output" => operation.copy(output = operation.output.copy(tpe = None))
            case _ => operation.copy(kind = None, input = operation.input.copy(tpe = None), output = operation.output.copy(tpe = None))
          }
          val services = ServiceModel(service.copy(operations = ServiceModel.ServiceClass.Operations(Vector(changed))))
          val model = DartPackageSpecFixtures.sourceModel(original.copy(divisions = original.divisions.filterNot(_.isInstanceOf[KaleidoxModel.ServiceDivision])), services = Some(services))
          When("default operation selection includes that declared source operation")
          val result = Try(DartPackageGenerator.generate(model, DartPackageGenerator.Options("phase65_api")))
          Then("the incomplete or entirely untyped operation fails instead of disappearing")
          result.isFailure shouldBe true
          result.failed.get.getMessage should include (if (missing == "untyped") "incomplete operation" else "requires")
        }
      }

      Vector("absent", "implicit", "explicit", "unowned", "duplicate").foreach { ownership =>
        s"reject $ownership component ownership" in {
          Given(s"canonical component definitions with $ownership ownership for Catalog")
          val original = DartPackageSpecFixtures.model
          val source = original.takeComponentSubsystemModel
          val component = source.components.head
          val components = ownership match {
            case "absent" => Vector.empty
            case "implicit" => Vector(component.copy(services = Vector.empty), component.copy(name = "Other", services = Vector.empty))
            case "explicit" => Vector(component, component.copy(name = "Other"))
            case "unowned" => Vector(component.copy(services = Vector(ComponentSubsystemModel.ComponentServiceDefinition("OtherService"))))
            case _ => Vector(component, component)
          }
          val model = DartPackageSpecFixtures.sourceModel(original, components = Some(source.copy(components = components)))
          When("the source operation is selected with unresolved ownership")
          val result = Try(DartPackageGenerator.generate(model, DartPackageGenerator.Options("phase65_api", operationNames = Vector("SampleComponent.Catalog.lookup"))))
          Then("generation fails with the canonical owner boundary")
          result.isFailure shouldBe true
          val diagnostic = ownership match {
            case "absent" => "SERVICE requires COMPONENT"
            case "implicit" => "ambiguous implicit component ownership"
            case "explicit" => "multiple explicit component owners"
            case "unowned" => "no explicit component owner"
            case _ => "component identity: duplicate"
          }
          result.failed.get.getMessage should include(diagnostic)
        }
      }
    }

    "retain canonical input-kind and parameter semantics through opt-in bindings" which {
      for {
        operationkind <- Vector("QUERY", "COMMAND")
        qualified <- Vector(false, true)
        marker <- Vector("property", "parent")
      } {
        s"admit $operationkind ${if (qualified) "qualified" else "unqualified"} VALUE input with $marker kind metadata" in {
          Given("a parsed canonical input Value with explicit kind or the QueryAction/CommandAction compatibility marker")
          val property = if (marker == "property") s"input-kind = $operationkind" else
            s"extends = ${if (operationkind == "QUERY") "QueryAction" else "CommandAction"}"
          val model = DartPackageSpecFixtures.parse(DartPackageSpecFixtures.kindSource(property, operationkind, qualified))
          When("the actual opt-in normalizer and typed package generator consume the original ValueClass")
          val definition = DartPackageSpecFixtures.normalized(model, optIn = true).head
          val products = DartPackageSpecFixtures.products(DartPackageGenerator.generate(model, DartPackageGenerator.Options("kind_api")))
          Then("inputValueKind, exact type spelling and original wire fields/multiplicities remain canonical")
          definition.inputValueKind shouldBe (if (operationkind == "QUERY") OperationModel.InputValueKind.QueryValue else OperationModel.InputValueKind.CommandValue)
          definition.inputType shouldBe (if (qualified) "dart.kinds.InputDto" else "InputDto")
          definition.parameters.map(field => field.name -> field.multiplicity) shouldBe Vector("class" -> "1", "some-key" -> "?", "tags" -> "*")
          products("lib/src/api.dart") should include("Future<_models.OutputDto> invoke(_models.InputDto request);")
          products("lib/src/models.dart") should include("class InputDto")
          products("lib/src/models.dart") should include("'some-key'")
        }
      }

      for {
        operationkind <- Vector("QUERY", "COMMAND")
        qualified <- Vector(false, true)
        failure <- Vector("missing", "invalid", "opposite", "opposite-parent")
      } {
        s"reject $failure kind on a ${if (qualified) "qualified" else "unqualified"} top-level VALUE used by $operationkind" in {
          Given(s"a referenced original ValueClass with $failure input-kind metadata")
          val property = failure match {
            case "missing" => ""
            case "invalid" => "input-kind = EVENT"
            case "opposite-parent" => s"extends = ${if (operationkind == "QUERY") "CommandAction" else "QueryAction"}"
            case _ => s"input-kind = ${if (operationkind == "QUERY") "COMMAND" else "QUERY"}"
          }
          val model = DartPackageSpecFixtures.parse(DartPackageSpecFixtures.kindSource(property, operationkind, qualified))
          When("the opt-in normalizer and package generator validate the actual original ValueClass")
          val normalized = Try(DartPackageSpecFixtures.normalized(model, optIn = true))
          val generated = Try(DartPackageGenerator.generate(model, DartPackageGenerator.Options("kind_api")))
          Then("both paths retain the actual canonical kind diagnostic")
          val diagnostic = failure match {
            case "missing" => "INPUT Value 'InputDto' requires input-kind=COMMAND|QUERY"
            case "invalid" => "Top-level VALUE 'InputDto' input-kind must be COMMAND or QUERY: EVENT"
            case _ => if (operationkind == "QUERY") "TYPE=QUERY cannot use command-value input" else "TYPE=COMMAND cannot use query-value input"
          }
          Vector(normalized, generated).foreach { result =>
            result.isFailure shouldBe true
            result.failed.get.getMessage should include(diagnostic)
          }
        }
      }

      "keep legacy top-level QUERY fields in the qualified opt-in path" in {
        Given("parsed legacy QUERY input with its actual canonical ValueClass and legacy definition")
        val model = DartPackageSpecFixtures.parse(DartPackageSpecFixtures.kindSource("", "QUERY", qualified = true) +
          "\n# QUERY\n\n## InputDto\n\n### ATTRIBUTE\n\n| name | type | multiplicity |\n| --- | --- | --- |\n| class | string | 1 |\n| some-key | string | ? |\n| tags | string | * |\n")
        When("qualified canonical bindings normalize the input and generate its package")
        val definition = DartPackageSpecFixtures.normalized(model, optIn = true).head
        val products = DartPackageSpecFixtures.products(DartPackageGenerator.generate(model, DartPackageGenerator.Options("legacy_api")))
        Then("the resolved original Value name owns the legacy query kind and original field precedence")
        definition.inputValueKind shouldBe OperationModel.InputValueKind.QueryValue
        definition.parameters.map(_.name) shouldBe Vector("class", "some-key", "tags")
        products("lib/src/api.dart") should include("_models.InputDto request")
      }

      "preserve explicit operation parameters over original Value fields" in {
        Given("an actual source operation with explicit canonical parameter metadata")
        val original = DartPackageSpecFixtures.parse(DartPackageSpecFixtures.kindSource("input-kind = QUERY", "QUERY", qualified = true))
        val service = original.getServiceModel.get.classes.values.head
        val operation = service.operations.operations.values.head.copy(parameters = Vector(OperationModel.FieldDefinition("explicit-wire", "string", "?")))
        val model = DartPackageSpecFixtures.sourceModel(original, services = Some(ServiceModel(service.copy(operations = ServiceModel.ServiceClass.Operations(Vector(operation))))))
        When("normalization and package generation consume the original operation parameters and canonical DTO separately")
        val definition = DartPackageSpecFixtures.normalized(model, optIn = true).head
        val products = DartPackageSpecFixtures.products(DartPackageGenerator.generate(model, DartPackageGenerator.Options("kind_api")))
        Then("parameter precedence is retained while rendered DTO fields still come from the canonical MObject")
        definition.parameters.map(_.name) shouldBe Vector("explicit-wire")
        products("lib/src/models.dart") should include("final String class_;")
        products("lib/src/models.dart") should not include "explicitWire"
      }

      "retain the original two-argument normalizer's qualified rejection and short-name input-kind admission" in {
        Given("equivalent parsed qualified and unqualified VALUE input contracts")
        val qualified = DartPackageSpecFixtures.parse(DartPackageSpecFixtures.kindSource("input-kind = QUERY", "QUERY", qualified = true))
        val short = DartPackageSpecFixtures.parse(DartPackageSpecFixtures.kindSource("input-kind = QUERY", "QUERY", qualified = false))
        val invalidshort = DartPackageSpecFixtures.parse(DartPackageSpecFixtures.kindSource("", "QUERY", qualified = false))
        When("the unchanged two-argument constructor performs actual canonical normalization")
        val rejected = Try(DartPackageSpecFixtures.normalized(qualified, optIn = false))
        val accepted = DartPackageSpecFixtures.normalized(short, optIn = false).head
        val missingkind = Try(DartPackageSpecFixtures.normalized(invalidshort, optIn = false))
        Then("qualified references stay unsupported by default and the existing short-name semantics remain enforced")
        rejected.isFailure shouldBe true
        rejected.failed.get.getMessage should include("INPUT TYPE 'dart.kinds.InputDto' is not defined")
        accepted.inputValueKind shouldBe OperationModel.InputValueKind.QueryValue
        accepted.parameters.map(_.name) shouldBe Vector("class", "some-key", "tags")
        missingkind.isFailure shouldBe true
        missingkind.failed.get.getMessage should include("requires input-kind=COMMAND|QUERY")
      }
    }

    "retain exact per-service source leaf ownership" which {
      "select equal short operation names using each service's own leaf contract" in {
        Given("two parsed services with shared short operation names but distinct source kind and DTO leaves")
        val original = DartPackageSpecFixtures.parse(DartPackageSpecFixtures.twoServicesSource)
        val services = original.getServiceModel.get
        val stripped = services.copy(classes = VectorMap(services.classes.toVector.map { case (name, service) =>
          name -> service.copy(operations = ServiceModel.ServiceClass.Operations(service.operations.operations.values.toVector.map { operation =>
            operation.copy(kind = None, input = operation.input.copy(tpe = None), output = operation.output.copy(tpe = None))
          }))
        }))
        val model = DartPackageSpecFixtures.sourceModel(original, services = Some(stripped))
        val builder = Modeler.ModelBuilder(model)
        When("both full identities and then only Alpha's full identity consume their actual source leaves")
        val both = DartOperationProjection.select(model, builder, builder.buildValue(), Vector("Owners.Alpha.shared", "Owners.Beta.shared"))
        val one = DartOperationProjection.select(model, builder, builder.buildValue(), Vector("Owners.Alpha.shared"))
        val products = DartPackageSpecFixtures.products(DartPackageGenerator.generate(model,
          DartPackageGenerator.Options("owners_api", operationNames = Vector("Owners.Alpha.shared"))))
        Then("the two operations retain separate QUERY/COMMAND DTOs and a single selection cannot consume Beta metadata")
        both.map(operation => (operation.identity, operation.definition.kind, operation.input.get.qualifiedName, operation.output.get.qualifiedName)) shouldBe Vector(
          ("Owners.Alpha.shared", OperationModel.OperationKind.Query, "dart.owners.AlphaRequest", "dart.owners.AlphaResponse"),
          ("Owners.Beta.shared", OperationModel.OperationKind.Command, "dart.owners.BetaRequest", "dart.owners.BetaResponse"))
        one.map(_.identity) shouldBe Vector("Owners.Alpha.shared")
        one.head.definition.inputValueKind shouldBe OperationModel.InputValueKind.QueryValue
        products("lib/src/api.dart") should include("Future<_models.AlphaResponse> shared(_models.AlphaRequest request);")
        products("lib/src/models.dart") should not include "BetaRequest"
      }

      "reject actual direct versus section metadata conflicts" in {
        Given("a source operation whose direct input conflicts with its retained original service leaf")
        val original = DartPackageSpecFixtures.parse(DartPackageSpecFixtures.twoServicesSource)
        val services = original.getServiceModel.get
        val alpha = services.classes.get("Alpha").get
        val operation = alpha.operations.operations.values.head
        val changed = alpha.copy(operations = ServiceModel.ServiceClass.Operations(Vector(operation.copy(input = operation.input.copy(tpe = Some("dart.owners.BetaRequest"))))))
        val model = DartPackageSpecFixtures.sourceModel(original, services = Some(services.copy(classes = VectorMap(Vector("Alpha" -> changed, "Beta" -> services.classes.get("Beta").get)))))
        When("Alpha's exact source operation is selected")
        val result = Try(DartPackageGenerator.generate(model, DartPackageGenerator.Options("owners_api", operationNames = Vector("Owners.Alpha.shared"))))
        Then("opt-in type admission does not rewrite either spelling or hide the canonical merge conflict")
        result.isFailure shouldBe true
        result.failed.get.getMessage should include("conflicts with INPUT section")
      }

      "reject duplicate selected leaf identities including an untyped duplicate" in {
        Given("two same-name service sections contributing a typed shared operation and an untyped duplicate leaf")
        val model = DartPackageSpecFixtures.parse(DartPackageSpecFixtures.twoServicesSource + "\n# SERVICE\n\n## Alpha\n\n### OPERATION\n\n#### shared\n\n##### INPUT\n\n##### OUTPUT\n\n")
        When("the exact selected-service leaf map is extracted")
        val result = Try(ModelerRelationshipCml.serviceOperationLeafContractsForService(model, "Alpha", Set("shared")))
        Then("the duplicate source diagnostic appears before any toMap winner")
        result.isFailure shouldBe true
        result.failed.get.getMessage should include("Service 'Alpha' has duplicate selected operation 'shared'")
      }

      "allow same-name service sections to contribute distinct selected operations" in {
        Given("two actual Alpha service sections contributing different operation names")
        val model = DartPackageSpecFixtures.parse(DartPackageSpecFixtures.twoServicesSource +
          "\n# SERVICE\n\n## Alpha\n\n### OPERATION\n\n#### second\n\ntype = QUERY\ninput = dart.owners.AlphaRequest\noutput = dart.owners.AlphaResponse\n")
        When("the exact service's two selected leaf contracts are extracted")
        val leaves = ModelerRelationshipCml.serviceOperationLeafContractsForService(model, "Alpha", Set("shared", "second"))
        Then("both distinct source leaves survive without taking Beta's shared leaf")
        leaves.keySet shouldBe Set("shared", "second")
        leaves("shared").inputType shouldBe Some("dart.owners.AlphaRequest")
        leaves("second").kind shouldBe Some(OperationModel.OperationKind.Query)
      }

      "reject a wrong qualified package without falling back to the matching simple name" in {
        Given("a parsed contract with wrong.package.InputDto and an actual dart.kinds.InputDto declaration")
        val model = DartPackageSpecFixtures.parse(DartPackageSpecFixtures.kindSource("input-kind = QUERY", "QUERY", qualified = true).
          replace("input = dart.kinds.InputDto", "input = wrong.package.InputDto"))
        When("the qualified source reference is generated")
        val result = Try(DartPackageGenerator.generate(model, DartPackageGenerator.Options("kind_api")))
        Then("canonical admission rejects the exact wrong package instead of selecting InputDto by short name")
        result.isFailure shouldBe true
        result.failed.get.getMessage should include("INPUT TYPE 'wrong.package.InputDto' is not defined")
      }

      "reject ambiguous canonical short names while preserving exact qualified references" in {
        Given("two original ValueClass objects named InputDto in different canonical packages")
        val original = DartPackageSpecFixtures.parse(DartPackageSpecFixtures.kindSource("input-kind = QUERY", "QUERY", qualified = false))
        val values = original.getValueModel.get
        val input = values.classes.get("InputDto").get
        val duplicate = input.copy(packageName = Some("other.kinds"))
        val model = DartPackageSpecFixtures.sourceModel(original, values = Some(values.copy(classes = VectorMap(values.classes.toVector :+ ("other-input" -> duplicate)))))
        val exactsource = DartPackageSpecFixtures.parse(DartPackageSpecFixtures.kindSource("input-kind = QUERY", "QUERY", qualified = true))
        val exact = DartPackageSpecFixtures.sourceModel(exactsource, values = Some(values.copy(classes = VectorMap(values.classes.toVector :+ ("other-input" -> duplicate)))))
        When("ambiguous unqualified input and exact qualified input select only their operation DTO closure")
        val rejected = Try(DartPackageGenerator.generate(model, DartPackageGenerator.Options("kind_api", operationNames = Vector("Kinds.InputService.invoke"))))
        val accepted = DartPackageSpecFixtures.products(DartPackageGenerator.generate(exact, DartPackageGenerator.Options("kind_api", operationNames = Vector("Kinds.InputService.invoke"))))
        Then("short-name ambiguity fails while a unique exact qualified identity wins")
        rejected.isFailure shouldBe true
        rejected.failed.get.getMessage should include("ambiguous canonical DTO 'InputDto'")
        accepted("lib/src/models.dart").split("class InputDto \\{", -1).length shouldBe 2
      }

      "isolate an unselected service's incomplete source behavior" in {
        Given("a complete source service and another service with an untyped operation")
        val original = DartPackageSpecFixtures.model
        val service = original.getServiceModel.get.classes.values.head
        val operation = service.operations.operations.get("lookup").get
        val other = service.copy(name = "Broken", operations = ServiceModel.ServiceClass.Operations(Vector(operation.copy(name = "untyped", kind = None, input = operation.input.copy(tpe = None), output = operation.output.copy(tpe = None)))))
        val components = original.takeComponentSubsystemModel
        val owner = components.components.head
        val model = DartPackageSpecFixtures.sourceModel(original,
          services = Some(ServiceModel(VectorMap(Vector(service.name -> service, other.name -> other)))),
          components = Some(components.copy(components = Vector(owner.copy(services = owner.services :+ ComponentSubsystemModel.ComponentServiceDefinition("Broken"))))))
        When("only the full valid lookup identity is generated")
        val products = DartPackageSpecFixtures.products(DartPackageGenerator.generate(model, DartPackageGenerator.Options("phase65_api", operationNames = Vector("SampleComponent.Catalog.lookup"))))
        Then("the unselected service cannot contaminate the selected package")
        products("lib/src/api.dart") should include("lookup(_models.SharedRequest request)")
        products("lib/src/api.dart") should not include "Broken"
      }
    }

    "reject public namespace collisions before emission" which {
      Vector("source", "normalized", "object", "interface", "model", "sealed", "runtime").foreach { collision =>
        s"reject the $collision collision boundary" in {
          Given(s"actual canonical source objects or CML with a $collision collision")
          val original = DartPackageSpecFixtures.model
          val service = original.getServiceModel.get.classes.values.head
          val lookup = service.operations.operations.get("lookup").get
          val options = DartPackageGenerator.Options("phase65_api", operationNames = Vector("SampleComponent.Catalog.lookup"))
          val (model, selected) = collision match {
            case "source" =>
              val operations = ServiceModel.ServiceClass.Operations(VectorMap(Vector("left" -> lookup, "right" -> lookup)))
              DartPackageSpecFixtures.sourceModel(original, services = Some(ServiceModel(service.copy(operations = operations)))) -> options
            case "normalized" | "object" =>
              val name = if (collision == "normalized") "look-up" else "to-string"
              val first = if (collision == "normalized") lookup.copy(name = "lookUp") else lookup
              val operations = ServiceModel.ServiceClass.Operations(Vector(first, lookup.copy(name = name)))
              DartPackageSpecFixtures.sourceModel(original, services = Some(ServiceModel(service.copy(operations = operations)))) -> options.copy(operationNames = Vector.empty)
            case "interface" =>
              val other = service.copy(name = "catalog", operations = ServiceModel.ServiceClass.Operations(Vector(lookup.copy(name = "other"))))
              val components = original.takeComponentSubsystemModel
              val owner = components.components.head
              DartPackageSpecFixtures.sourceModel(original, services = Some(ServiceModel(VectorMap(Vector(service.name -> service, other.name -> other)))),
                components = Some(components.copy(components = Vector(owner.copy(services = owner.services :+ ComponentSubsystemModel.ComponentServiceDefinition(other.name)))))) -> options.copy(operationNames = Vector.empty)
            case "sealed" =>
              val model = DartPackageSpecFixtures.parse(DartPackageSpecFixtures.source + "\n# POWERTYPE\n\n## SampleComponentCatalog\npackage = dart.api\n\n### api\nvalue = 1\n")
              model -> options.copy(typeNames = Vector("dart.api.SampleComponentCatalog"), sealedTypeNames = Set("dart.api.SampleComponentCatalog"))
            case _ =>
              val name = if (collision == "model") "SampleComponentCatalogApi" else "DartJsonCodec"
              val model = DartPackageSpecFixtures.parse(DartPackageSpecFixtures.source + s"\n# VALUE\n\n## $name\npackage = dart.api\n\n### ATTRIBUTE\n\n| name | type | multiplicity |\n| --- | --- | --- |\n| value | string | 1 |\n")
              model -> options.copy(typeNames = Vector(s"dart.api.$name"))
          }
          When("the complete selected package namespace is constructed")
          val result = Try(DartPackageGenerator.generate(model, selected))
          Then("the source, normalized, inherited or public symbol collision is rejected")
          result.isFailure shouldBe true
          val diagnostic = collision match {
            case "source" => "operation identity: duplicate"
            case "normalized" => "duplicate normalized method"
            case "object" => "reserved Object method"
            case _ => "collides with"
          }
          result.failed.get.getMessage should include(diagnostic)
        }
      }

      "reject distinct selected canonical types with the same normalized Dart name" in {
        Given("an actual Shared_Response Value declaration alongside SharedResponse")
        val model = DartPackageSpecFixtures.parse(DartPackageSpecFixtures.source +
          "\n# VALUE\n\n## Shared_Response\npackage = dart.api\n\n### ATTRIBUTE\n\n| name | type | multiplicity |\n| --- | --- | --- |\n| value | string | 1 |\n")
        val options = DartPackageGenerator.Options("phase65_api", Vector("dart.api.Shared_Response"), Vector("SampleComponent.Catalog.lookup"))
        When("the operation closure is unioned with that explicit canonical type root")
        val result = Try(DartPackageGenerator.generate(model, options))
        Then("Dart type normalization cannot discard either canonical identity")
        result.isFailure shouldBe true
        result.failed.get.getMessage should include("Dart symbol 'SharedResponse' collides")
      }
    }

    "admit exact Dart pub package spellings" which {
      Vector("phase65_api", "async", "_api", "api_2").foreach { name =>
        s"admit contextual or underscore spelling '$name' unchanged" in {
          Given("a parsed empty source and a valid exact pub package identifier")
          val model = DartPackageSpecFixtures.parse("")
          When("the exact package name is admitted and its package generated")
          val admitted = DartNaming.requirePackageName(name)
          val products = DartPackageSpecFixtures.products(DartPackageGenerator.generate(model, DartPackageGenerator.Options(name)))
          Then("admission preserves the original identifier and complete package path")
          admitted shouldBe name
          products.keySet shouldBe DartPackageSpecFixtures.paths(name).toSet
          products("pubspec.yaml") should startWith(s"name: $name\n")
        }
      }
      Vector("", "_", "__", "class", "void", "1api", "Api", "api/name", "../api", "api-name", " api ").foreach { name =>
        s"reject invalid exact package spelling '${name.replace(" ", "[space]")}'" in {
          Given("an empty source and an invalid keyword, numeric, uppercase, path or empty pub identifier")
          val model = DartPackageSpecFixtures.parse("")
          When("the invalid spelling is admitted and passed to package generation")
          val admitted = Try(DartNaming.requirePackageName(name))
          val generated = Try(DartPackageGenerator.generate(model, DartPackageGenerator.Options(name)))
          Then("both boundaries reject it without silent normalization")
          admitted.isFailure shouldBe true
          generated.isFailure shouldBe true
          generated.failed.get.getMessage should include("Invalid Dart package name")
        }
      }
    }
  }
}

private[modeler] object DartPackageSpecFixtures {
  def source: String = {
    val resource = getClass.getResourceAsStream("/modeler/dart-api.cml")
    require(resource != null, "Missing canonical Dart API fixture")
    val input = scala.io.Source.fromInputStream(resource, "UTF-8")
    try input.mkString finally input.close()
  }
  def parse(source: String): KaleidoxModel = KaleidoxModel.parse(KaleidoxConfig.default.withoutLocation, source)
  def model: KaleidoxModel = parse(source)
  def options: DartPackageGenerator.Options = DartPackageGenerator.Options("phase65_api", Vector("dart.api.DeclaredFailure"))
  def paths(packageName: String): Vector[String] =
    Vector("README.md", s"lib/$packageName.dart", "lib/src/api.dart", "lib/src/json.dart", "lib/src/models.dart", "pubspec.yaml").sorted
  def products(realm: Realm): Map[String, String] = {
    val pubspec = text(realm, "pubspec.yaml")
    val packagename = pubspec.linesIterator.find(_.startsWith("name: ")).get.stripPrefix("name: ")
    paths(packagename).map(path => path -> text(realm, path)).toMap
  }
  def text(realm: Realm, path: String): String = realm.get(path).collect {
    case data: Realm.StringData => data.string
  }.getOrElse(throw new IllegalStateException(s"Missing generated product: $path"))

  def sourceModel(
    model: KaleidoxModel,
    services: Option[ServiceModel] = None,
    components: Option[ComponentSubsystemModel] = None,
    values: Option[ValueModel] = None
  ): KaleidoxModel = new KaleidoxModel(model.config, model.divisions, model.libraries) {
    override lazy val getServiceModel: Option[ServiceModel] = services.orElse(model.getServiceModel)
    override lazy val getComponentSubsystemModel: Option[ComponentSubsystemModel] = components.orElse(model.getComponentSubsystemModel)
    override lazy val getValueModel: Option[ValueModel] = values.orElse(model.getValueModel)
  }

  def normalized(model: KaleidoxModel, optIn: Boolean): Vector[OperationModel.NormalizedOperationDefinition] = {
    val builder = Modeler.ModelBuilder(model)
    val projector = new ModelTypeProjector(builder)
    val normalizer = if (optIn)
      new ModelServiceOperationProjector(builder, projector, Some(DartOperationProjection.canonicalBindings(builder, builder.buildValue())))
    else new ModelServiceOperationProjector(builder, projector)
    normalizer.normalizedServiceOperationDefinitions
  }

  def unsupported: String = "\n# VALUE\n\n## Unsupported\npackage = dart.api\n\n### ATTRIBUTE\n\n| name | type | multiplicity |\n| --- | --- | --- |\n| value | decimal | 1 |\n"

  def kindSource(property: String, kind: String, qualified: Boolean): String = {
    val input = if (qualified) "dart.kinds.InputDto" else "InputDto"
    val output = if (qualified) "dart.kinds.OutputDto" else "OutputDto"
    s"""# COMPONENT
       |
       |## Kinds
       |
       |### PACKAGE
       |dart.kinds
       |
       |# VALUE
       |
       |## InputDto
       |package = dart.kinds
       |$property
       |
       |### ATTRIBUTE
       |
       || name | type | multiplicity |
       || --- | --- | --- |
       || class | string | 1 |
       || some-key | string | ? |
       || tags | string | * |
       |
       |## OutputDto
       |package = dart.kinds
       |
       |### ATTRIBUTE
       |
       || name | type | multiplicity |
       || --- | --- | --- |
       || result | string | 1 |
       |
       |# SERVICE
       |
       |## InputService
       |
       |### OPERATION
       |
       |#### invoke
       |type = $kind
       |input = $input
       |output = $output
       |""".stripMargin
  }

  def twoServicesSource: String = {
    val values = Vector("AlphaRequest" -> "QUERY", "AlphaResponse" -> "", "BetaRequest" -> "COMMAND", "BetaResponse" -> "").map {
      case (name, kind) =>
        s"## $name\npackage = dart.owners\n" + (if (kind.isEmpty) "" else s"input-kind = $kind\n") +
          "\n### ATTRIBUTE\n\n| name | type | multiplicity |\n| --- | --- | --- |\n| value | string | 1 |\n"
    }.mkString("\n")
    "# COMPONENT\n\n## Owners\n\n### PACKAGE\ndart.owners\n\n### SERVICE\n\n#### Alpha\n\n#### Beta\n\n# VALUE\n\n" + values +
      "\n# SERVICE\n\n## Alpha\n\n### OPERATION\n\n#### shared\ntype = QUERY\ninput = dart.owners.AlphaRequest\noutput = dart.owners.AlphaResponse\n" +
      "\n## Beta\n\n### OPERATION\n\n#### shared\ntype = COMMAND\ninput = dart.owners.BetaRequest\noutput = dart.owners.BetaResponse\n"
  }
}
