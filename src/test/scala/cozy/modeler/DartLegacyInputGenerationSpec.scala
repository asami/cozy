package cozy.modeler

import scala.util.Try
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.kaleidox.model.OperationModel
import org.simplemodeling.model.MObject

/*
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 */
class DartLegacyInputGenerationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Canonical legacy Dart inputs" should {
    "reuse original QUERY and COMMAND DTO Sections" which {
      for {
        kind <- Vector("QUERY", "COMMAND")
        (property, input, identity) <- Vector(
          ("", "Request", "dart.legacy.value.Request"),
          ("package = explicit.inputs", "explicit.inputs.Request", "explicit.inputs.Request"),
          ("package-name = explicit.inputs", "explicit.inputs.Request", "explicit.inputs.Request"))
      } {
        s"generate a $kind request with canonical identity $identity from '$property'" in {
          Given("parsed canonical CML with only a legacy request, ordinary response and nested VALUE dependencies")
          val model = _parse(_source(kind, property, input))
          model.getValueModel.get.classes.get("Request") shouldBe None
          When("the public package entry generates the selected operation and its codec closure")
          val products = _generate(model)
          val operations = _operations(model)
          Then("API, README and models share the canonical source identity and the typed request is emitted once")
          products("lib/src/api.dart") should include("Future<_models.Response> invoke(_models.Request request);")
          products("lib/src/api.dart") should include(s""""input":"$identity"""")
          products("lib/src/api.dart") should include(s""""kind":"$kind"""")
          products("README.md") should include(s"`$identity`")
          products("lib/src/models.dart").split("class Request \\{", -1).length shouldBe 2
          operations.head.input.get.qualifiedName shouldBe identity
          operations.head.definition.inputValueKind shouldBe _input_kind(kind)
          operations.head.definition.inputType shouldBe input
          operations.head.definition.parameters.map(_.name) shouldBe Vector("class", "some-key", "tags", "flags", "ratio", "detail")
          And("canonical ATTRIBUTE multiplicity, original wire names, primitives and nested scalar wrappers survive")
          val rendered = products("lib/src/models.dart")
          rendered should include("final String class_;")
          rendered should include("final int? someKey;")
          rendered should include("final List<String> tags;")
          rendered should include("final List<bool> flags;")
          rendered should include("final double ratio;")
          rendered should include("final Detail? detail;")
          rendered should include("required String class_")
          rendered should include("List<String> tags = const []")
          rendered should include("required List<bool> flags")
          rendered should include("'some-key':")
          rendered should include("manyField<String>(_json, 'tags'")
          rendered should include("readList<bool>(")
          rendered should include("Detail.fromJson(_json['detail'])")
          rendered should include("class Detail")
          rendered should include("Object? toJson() => _dartJson.DartJsonCodec.string(value,")
          rendered should include("class Response")
        }
      }

      "keep distinct COMMAND and QUERY inputs and their normalized kinds in one source" in {
        Given("one source declaring a query SearchRequest and command SubmitRequest without duplicate VALUE inputs")
        val source = _component + _values +
          "# QUERY\n\n" + _dto("SearchRequest", _fields) +
          "# COMMAND\n\n" + _dto("SubmitRequest", _fields) +
          "# SERVICE\n\n## Gateway\n\n### OPERATION\n\n" +
          _operation("search", "QUERY", "SearchRequest") + _operation("submit", "COMMAND", "SubmitRequest")
        val model = _parse(source)
        When("both operations are generated through the public entry")
        val products = _generate(model, Vector.empty)
        val operations = _operations(model, Vector.empty)
        Then("each original request and source kind remains separately observable")
        products("lib/src/api.dart") should include("search(_models.SearchRequest request)")
        products("lib/src/api.dart") should include("submit(_models.SubmitRequest request)")
        operations.map(operation => operation.definition.name -> operation.definition.inputValueKind) shouldBe Vector(
          "search" -> OperationModel.InputValueKind.QueryValue,
          "submit" -> OperationModel.InputValueKind.CommandValue)
        products("README.md") should include("`dart.legacy.value.SearchRequest`")
        products("README.md") should include("`dart.legacy.value.SubmitRequest`")
      }

      "reject TYPE mismatch using the original legacy kind" in {
        Given("a canonical legacy QUERY request referenced by a COMMAND operation")
        val model = _parse(_source("QUERY").replace("type = QUERY", "type = COMMAND"))
        When("the public entry normalizes the operation against its canonical request")
        val result = Try(_generate(model))
        Then("the existing mismatch diagnostic rejects the request without changing its source kind")
        result.isFailure shouldBe true
        result.failed.get.getMessage should include("TYPE=COMMAND cannot use query-value input")
      }
    }

    "reject duplicate and missing original declarations" which {
      val duplicates = Vector(
        "one QUERY section" -> ("# QUERY\n\n" + _dto("Request", _fields) + _dto("Request", _fields)),
        "split QUERY sections" -> ("# QUERY\n\n" + _dto("Request", _fields) + "# QUERY\n\n" + _dto("Request", _fields)),
        "COMMAND and QUERY overlap" -> ("# COMMAND\n\n" + _dto("Request", _fields) + "# QUERY\n\n" + _dto("Request", _fields)),
        "different explicit packages" -> ("# QUERY\n\n" + _dto("Request", _fields, "package = first.inputs") +
          "# COMMAND\n\n" + _dto("Request", _fields, "package-name = second.inputs")))
      duplicates.foreach { case (label, declarations) =>
        s"reject $label deterministically before indexing" in {
          Given("original DTO Sections whose admitted legacy inventory repeats the exact Request name")
          val model = _parse(_component + _values + declarations + _service("QUERY", "Request"))
          When("the same canonical package is requested twice")
          val first = Try(_generate(model))
          val second = Try(_generate(model))
          Then("both source diagnostics identify the duplicate and never choose a declaration winner")
          first.isFailure shouldBe true
          second.isFailure shouldBe true
          first.failed.get.getMessage should include("Legacy input 'Request' is declared more than once")
          first.failed.get.getMessage should include("Section 'Request'")
          first.failed.get.getMessage shouldBe second.failed.get.getMessage
        }
      }

      "reject the wrong qualified package without a short-name fallback" in {
        Given("an explicit canonical inputs package and an operation that spells a different qualified package")
        val model = _parse(_source("QUERY", "package = explicit.inputs", "wrong.inputs.Request"))
        When("the public entry resolves that exact INPUT identity")
        val result = Try(_generate(model))
        Then("the wrong qualified identity fails instead of joining Request by its short name")
        result.isFailure shouldBe true
        result.failed.get.getMessage should include("INPUT TYPE 'wrong.inputs.Request' is not defined")
      }

      "reject an admitted inventory entry without an original DTO Section" in {
        Given("a parsed source with no Request Section and an actual legacy inventory borrowed from a parsed request")
        val original = _parse(_component + _values + _service("QUERY", "Request"))
        val inventory = _parse(_source("QUERY")).takeOperationModel
        val model = _with_inventory(original, inventory)
        When("the public entry attempts to join the original canonical source")
        val result = Try(_generate(model))
        Then("the missing source diagnostic names Request and no field-table inference succeeds")
        result.isFailure shouldBe true
        result.failed.get.getMessage should include("Legacy input 'Request' has no original canonical DTO Section")
      }

      "reject ambiguous original Sections even when the admitted inventory has only one entry" in {
        Given("two actual Request Sections and an inventory exposing one declaration")
        val original = _parse(_component + _values + duplicates.head._2 + _service("QUERY", "Request"))
        val inventory = original.takeOperationModel.copy(values = original.takeOperationModel.values.take(1))
        val model = _with_inventory(original, inventory)
        When("the public entry requires exactly one unchanged original Section")
        val result = Try(_generate(model))
        Then("the canonical-source ambiguity fails without choosing a Section")
        result.isFailure shouldBe true
        result.failed.get.getMessage should include("Legacy input 'Request' has ambiguous original canonical DTO Sections")
      }

      "reject a request for which the canonical factory supplies no ValueClass" in {
        Given("a parsed legacy Request with prose but no canonical schema")
        val model = _parse(_component + _values + "# QUERY\n\n## Request\n\nsummary = no schema\n\n" + _service("QUERY", "Request"))
        When("the public entry calls the canonical original-Section factory")
        val result = Try(_generate(model))
        Then("the diagnostic names Request and no empty or inferred shadow DTO is returned")
        result.isFailure shouldBe true
        result.failed.get.getMessage should include("Legacy input 'Request' has no canonical ValueClass")
      }

      "leave operation marker and ATTRIBUTE descendants outside the legacy DTO inventory" in {
        Given("an original operation marker containing a Request lookalike and an ATTRIBUTE descendant with a nested QUERY")
        val source = _component + _values + "# OPERATION\n\n## ignored\n\n### QUERY\n\n#### Request\n\n" +
          "##### ATTRIBUTE\n\n| name | type | multiplicity |\n| --- | --- | --- |\n| false-field | string | 1 |\n\n" +
          "# QUERY\n\n" + _dto("Actual", _fields) + "#### QUERY\n\n##### Request\n\n" + _service("QUERY", "Request")
        val original = _parse(source)
        val inventory = _parse(_source("QUERY")).takeOperationModel
        val model = _with_inventory(original, inventory)
        When("the public entry searches only top-level DTO children admitted by OperationModel's structural semantics")
        val result = Try(_generate(model))
        Then("the lookalike descendants cannot supply the missing original Request")
        result.isFailure shouldBe true
        result.failed.get.getMessage should include("Legacy input 'Request' has no original canonical DTO Section")
      }
    }

    "preserve existing VALUE and non-Dart contracts" which {
      "retain VALUE fields while legacy fields and explicit parameters keep their existing precedence" in {
        Given("a QUERY Request with legacy fields and an original VALUE Request with different fields, package and kind properties")
        val value = "# VALUE\n\n" + _dto("Request", "| rendered-field | string | 1 |", "package = original.values\ninput-kind = COMMAND")
        val source = _source("QUERY", input = "original.values.Request") + value
        val model = _parse(source)
        val explicit = _parse(source.replace("output = Response", "output = Response\n\n##### PARAMETER\n\n| name | type | multiplicity |\n| --- | --- | --- |\n| explicit-field | int | 1 |"))
        val before = model.getValueModel.get.classes.get("Request").get
        When("the packages render the original VALUE and normalize both legacy and explicit operation parameters")
        val products = _generate(model)
        val explicitproducts = _generate(explicit)
        val legacyoperation = _operations(model).head.definition
        val explicitoperation = _operations(explicit).head.definition
        val retained = DartCanonicalInputProjection.enrich(model, Modeler.ModelBuilder(model)).value.classes.get("Request").get
        Then("rendered fields come from VALUE while legacy QUERY kind and parameter precedence remain authoritative")
        products("README.md") should include("`original.values.Request`")
        products("lib/src/models.dart") should include("final String renderedField;")
        products("lib/src/models.dart") should not include "final String class_;"
        explicitproducts("lib/src/models.dart") shouldBe products("lib/src/models.dart")
        legacyoperation.inputValueKind shouldBe OperationModel.InputValueKind.QueryValue
        legacyoperation.parameters.map(_.name) shouldBe Vector("class", "some-key", "tags", "flags", "ratio", "detail")
        explicitoperation.parameters.map(_.name) shouldBe Vector("explicit-field")
        retained shouldBe before
        retained.getProperty("input-kind") shouldBe Some("COMMAND")
        retained.packageName shouldBe Some("original.values")
      }

      "retain ordinary VALUE-only package behavior and direct canonical data generation" in {
        Given("parsed ordinary VALUE inputs with the required input-kind and no legacy declarations")
        val source = _component + _values + "# VALUE\n\n" + _dto("Request", _fields, "input-kind = QUERY") + _service("QUERY", "Request")
        val model = _parse(source)
        val data = Modeler.ModelBuilder(model).buildValue()
        When("the package entry and unchanged direct data entry generate their products")
        val products = _generate(model)
        val direct = DartPackageSpecFixtures.text(DartModelGenerator.generate(data), "lib/src/models.dart")
        Then("both entries expose the same canonical VALUE models and codecs")
        products("lib/src/models.dart") shouldBe direct
        products("README.md") should include("`dart.legacy.value.Request`")
        products("lib/src/api.dart") should include("invoke(_models.Request request)")
        data.elements.collect { case source: MObject => source.qualifiedName }.toSet shouldBe
          Set("dart.legacy.value.Request", "dart.legacy.value.Response", "dart.legacy.value.Detail")
      }

      "leave the original non-Dart buildValue and direct generator free of legacy-only requests" in {
        Given("parsed legacy QUERY input and an original builder that projects only ordinary VALUE definitions")
        val model = _parse(_source("QUERY"))
        val builder = Modeler.ModelBuilder(model)
        val before = builder.buildValue()
        When("Dart package enrichment runs and the original canonical data entry is used again")
        val products = _generate(model)
        val after = builder.buildValue()
        val rebuilt = Modeler.ModelBuilder(model).buildValue()
        val direct = DartPackageSpecFixtures.text(DartModelGenerator.generate(rebuilt), "lib/src/models.dart")
        Then("only the Dart package contains Request while original data and direct data generation remain compatible")
        products("lib/src/models.dart") should include("class Request")
        before.elements.collect { case source: MObject => source.qualifiedName }.toSet shouldBe
          Set("dart.legacy.value.Response", "dart.legacy.value.Detail")
        after shouldBe before
        rebuilt shouldBe before
        direct should include("class Response")
        direct should include("class Detail")
        direct should not include "class Request"
      }
    }

    "select deterministic canonical dependency closures" which {
      "exclude unrelated supported legacy requests while retaining nested VALUE dependencies" in {
        Given("one selected request and a supported unrelated COMMAND request in the same canonical source")
        val model = _parse(_source("QUERY") + "# COMMAND\n\n" + _dto("Unrelated", "| other | string | 1 |"))
        When("the public entry selects only Legacy.Gateway.invoke")
        val products = _generate(model)
        Then("Request, Response and nested Detail are retained while Unrelated is excluded")
        products("lib/src/models.dart") should include("class Request")
        products("lib/src/models.dart") should include("class Response")
        products("lib/src/models.dart") should include("class Detail")
        products("lib/src/models.dart") should not include "class Unrelated"
        products("README.md") should not include "Unrelated"
      }

      "preserve source-order determinism and canonical kind, identity and field multiplicity with ScalaCheck" in {
        Given("canonical QUERY/COMMAND, exact unqualified/qualified INPUT spellings and supported field multiplicities")
        val cases = for {
          kind <- Gen.oneOf("QUERY", "COMMAND")
          qualified <- Gen.oneOf(true, false)
          multiplicity <- Gen.oneOf("1", "?", "*", "+")
        } yield (kind, qualified, multiplicity)
        When("the public entry generates independently reordered declarations for every canonical case")
        val property = Prop.forAll(cases) { case (kind, qualified, multiplicity) =>
          val input = if (qualified) "dart.legacy.value.Request" else "Request"
          val source = _source(kind, input = input).replace("| tags | string | * |", s"| tags | string | $multiplicity |")
          val model = _parse(source)
          val reordered = model.copy(divisions = model.divisions.reverse)
          val products = _generate(model)
          val operations = _operations(model)
          val fieldtype = multiplicity match {
            case "1" => "String"
            case "?" => "String?"
            case _ => "List<String>"
          }
          products == _generate(reordered) &&
            operations.head.input.get.qualifiedName == "dart.legacy.value.Request" &&
            operations.head.definition.inputValueKind == _input_kind(kind) &&
            operations.head.definition.parameters.find(_.name == "tags").exists(_.multiplicity == multiplicity) &&
            products("lib/src/models.dart").contains(s"final $fieldtype tags;") &&
            products("lib/src/api.dart").contains(s""""kind":"$kind"""")
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(40).withInitialSeed(Seed(654L)), property)
        Then("equivalent independent source orders emit identical six products and all varied semantics survive")
        withClue(result.toString) { result.passed shouldBe true }
        result.succeeded should be >= 40
      }
    }
  }

  private def _parse(source: String): KaleidoxModel =
    KaleidoxModel.parse(KaleidoxConfig.default.withoutLocation, source)

  private def _generate(model: KaleidoxModel, selected: Vector[String] = Vector("Legacy.Gateway.invoke")): Map[String, String] =
    DartPackageSpecFixtures.products(DartPackageGenerator.generate(model,
      DartPackageGenerator.Options("legacy_api", operationNames = selected)))

  private def _operations(model: KaleidoxModel, selected: Vector[String] = Vector("Legacy.Gateway.invoke")): Vector[DartOperationProjection.Operation] = {
    val builder = DartCanonicalInputProjection.enrich(model, Modeler.ModelBuilder(model))
    DartOperationProjection.select(model, builder, builder.buildValue(), selected)
  }

  private def _with_inventory(model: KaleidoxModel, inventory: OperationModel): KaleidoxModel =
    new KaleidoxModel(model.config, model.divisions, model.libraries) {
      override lazy val getOperationModel: Option[OperationModel] = Some(inventory)
    }

  private def _input_kind(kind: String): OperationModel.InputValueKind = kind match {
    case "COMMAND" => OperationModel.InputValueKind.CommandValue
    case _ => OperationModel.InputValueKind.QueryValue
  }

  private def _source(kind: String, property: String = "", input: String = "Request"): String =
    _component + _values + s"# $kind\n\n" + _dto("Request", _fields, property) + _service(kind, input)

  private def _dto(name: String, fields: String, property: String = ""): String =
    s"## $name\n$property\n\n### ATTRIBUTE\n\n| name | type | multiplicity |\n| --- | --- | --- |\n$fields\n\n"

  private def _service(kind: String, input: String): String =
    "# SERVICE\n\n## Gateway\n\n### OPERATION\n\n" + _operation("invoke", kind, input)

  private def _operation(name: String, kind: String, input: String): String =
    s"#### $name\n\ntype = $kind\ninput = $input\noutput = Response\n\n"

  private def _component: String = "# COMPONENT\n\n## Legacy\n\n### PACKAGE\n\ndart.legacy\n\n"

  private def _values: String = "# VALUE\n\n" +
    _dto("Response", "| count | int | 1 |\n| detail | Detail | ? |") +
    _dto("Detail", "| value | string | 1 |")

  private def _fields: String =
    "| class | string | 1 |\n| some-key | int | ? |\n| tags | string | * |\n| flags | boolean | + |\n| ratio | double | 1 |\n| detail | Detail | ? |"
}
