package cozy.modeler

import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import org.goldenport.cli.{Argument, ConclusionResponse, FileRealmResponse, Property, Request}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
class DartModelerServiceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "The explicit modeler dart service" should {
    "save a complete response-owned package (DA-17, DA-18)" which {
      Vector("equals", "separate").foreach { syntax =>
        s"save the actual selected canonical Realm using $syntax property syntax and a declared charset" in {
          Given("a parsed canonical fixture saved in ISO-8859-1 and explicit type/operation/charset properties")
          val workspace = _scratch(s"success-$syntax")
          val model = workspace.resolve("latin1.cml")
          val source = DartPackageSpecFixtures.source.replaceFirst("### ATTRIBUTE", "### SUMMARY\n\nCafé\n\n### ATTRIBUTE")
          Files.write(model, source.getBytes(Charset.forName("ISO-8859-1")))
          val output = workspace.resolve("package")
          val properties = Vector("package" -> "phase65_api", "save" -> output.toString,
            "types" -> " dart.api.DeclaredFailure ",
            "operations" -> " SampleComponent.Catalog.lookup , SampleComponent.Catalog.class ", "charset" -> "ISO-8859-1")
          val arguments = Array("modeler", "dart", model.toString) ++ properties.flatMap { case (key, value) =>
            if (syntax == "equals") Vector(s"--$key=$value") else Vector(s"--$key", value)
          }
          When("the actual registered Cozy CLI executes that explicit Dart request")
          val response = cozy.Cozy.build(arguments).execute(arguments)
          val saved = _snapshot(output)
          Then("a typed FileRealmResponse owns all six saved products and selector trimming retains only the selected API/DTO closure")
          response shouldBe a[FileRealmResponse]
          val products = DartPackageSpecFixtures.products(response.asInstanceOf[FileRealmResponse].realm)
          saved shouldBe products
          saved.keySet shouldBe DartPackageSpecFixtures.paths("phase65_api").toSet
          saved("lib/src/models.dart") should include("class SharedRequest")
          saved("lib/src/models.dart") should include("class SharedResponse")
          saved("lib/src/models.dart") should include("class DeclaredFailure")
          saved("lib/src/models.dart") should not include "InlineRequest"
          saved("lib/src/api.dart") should include("class_(_models.SharedRequest request)")
          saved("lib/src/api.dart") should not include "ping()"
          ModelerServiceClass.defaultOperation shouldBe None
          ModelerServiceClass.DartOperationClass.specification.name shouldBe "dart"
          ModelerServiceClass.ClassOperationClass.request.parameters.map(_.name) shouldBe List("model", "save", "charset")
          ModelerServiceClass.ProjectOperationClass.request.parameters.map(_.name) shouldBe List("model", "save", "charset")
        }
      }

      "return a complete direct response without generator or facade filesystem writes" in {
        Given("a direct typed request with model, package and a save destination that does not exist")
        val workspace = _scratch("direct-response")
        val model = _model_file(workspace, DartPackageSpecFixtures.source)
        val output = workspace.resolve("package")
        val request = ModelerServiceClass.DartOperationClass.request.buildStrict(Request("dart"),
          Vector(model.toString, "--package", "phase65_api", "--save", output.toString))
        val environment = cozy.Cozy.build(Array.empty).environment
        When("the operation is applied directly without Environment's response-saving lifecycle")
        val response = ModelerServiceClass.DartOperationClass.apply(environment, request)
        Then("the complete typed Realm exists in memory and the save destination remains absent")
        response shouldBe a[FileRealmResponse]
        DartPackageSpecFixtures.products(response.asInstanceOf[FileRealmResponse].realm).keySet shouldBe DartPackageSpecFixtures.paths("phase65_api").toSet
        Files.exists(output) shouldBe false
      }

      "save a valid selected sealed classification alongside its selected API" in {
        Given("canonical finite classification CML and an explicit sealed selection in the Dart request")
        val workspace = _scratch("sealed-selection")
        val model = _model_file(workspace, DartPackageSpecFixtures.source +
          "\n# POWERTYPE\n\n## ApiState\npackage = dart.api\n\n### ready\nvalue = 1\n\n### pending\nvalue = 2\n")
        val output = workspace.resolve("package")
        val arguments = Array("modeler", "dart", model.toString, "--package=phase65_api", s"--save=$output",
          "--types=dart.api.ApiState", "--operations=SampleComponent.Catalog.lookup", "--sealed-types=dart.api.ApiState")
        When("the actual CLI parses all three selector properties and saves the successful response")
        val response = cozy.Cozy.build(arguments).execute(arguments)
        val saved = _snapshot(output)
        Then("the six-file typed response retains the selected sealed data representation and API")
        response shouldBe a[FileRealmResponse]
        saved shouldBe DartPackageSpecFixtures.products(response.asInstanceOf[FileRealmResponse].realm)
        saved.keySet shouldBe DartPackageSpecFixtures.paths("phase65_api").toSet
        saved("lib/src/models.dart") should include("sealed class ApiState")
        saved("lib/src/api.dart") should include("lookup(_models.SharedRequest request)")
      }
    }

    "contain invalid argument, source and selected-contract failures (DA-18)" which {
      Vector("package", "missing-model", "missing-package", "missing-save", "empty-types", "empty-operations", "empty-sealed-types",
        "malformed-types", "malformed-operations", "malformed-sealed-types", "charset", "missing-file", "malformed-model", "selected-contract").foreach { failure =>
        s"return a structured conclusion for $failure without creating output" in {
          Given(s"a canonical CLI fixture and a request with $failure input")
          val workspace = _scratch(s"invalid-$failure")
          val valid = _model_file(workspace, DartPackageSpecFixtures.source)
          val malformed = workspace.resolve("malformed.cml")
          Files.write(malformed, "# ENTITY\n\n## Broken\n\n### FEATURES\n\nschema = [\n".getBytes(StandardCharsets.UTF_8))
          val invalidcontract = workspace.resolve("invalid-contract.cml")
          Files.write(invalidcontract, DartPackageSpecFixtures.source.replace("dart.api.SharedResponse", "dart.api.MissingResponse").getBytes(StandardCharsets.UTF_8))
          val output = workspace.resolve("package")
          val arguments = _invalid_arguments(failure, valid, malformed, invalidcontract, workspace.resolve("missing.cml"), output)
          When("the actual Cozy build/execute boundary processes the invalid Dart request")
          val response = cozy.Cozy.build(arguments).execute(arguments)
          Then("the failure is a typed ConclusionResponse and no package directory or partial file exists")
          response shouldBe a[ConclusionResponse]
          Files.exists(output) shouldBe false
        }
      }

      "preserve an existing save destination when the selected contract is invalid" in {
        Given("an existing destination and a parsed model whose selected output DTO reference is undefined")
        val workspace = _scratch("existing-destination")
        val model = _model_file(workspace, DartPackageSpecFixtures.source.replace("dart.api.SharedResponse", "dart.api.MissingResponse"))
        val output = workspace.resolve("package")
        Files.createDirectories(output)
        Files.write(output.resolve("sentinel.txt"), "existing output\n".getBytes(StandardCharsets.UTF_8))
        val before = _snapshot(output)
        val arguments = Array("modeler", "dart", model.toString, "--package=phase65_api", s"--save=$output", "--operations=SampleComponent.Catalog.lookup")
        When("the invalid selected contract reaches the actual CLI generation boundary")
        val response = cozy.Cozy.build(arguments).execute(arguments)
        val after = _snapshot(output)
        Then("the structured failure leaves every existing destination file unchanged")
        response shouldBe a[ConclusionResponse]
        after shouldBe before
      }

      Vector("model", "package", "save").foreach { missing =>
        s"enforce required $missing for a direct apply caller" in {
          Given(s"a direct request omitting $missing and an absent save destination")
          val workspace = _scratch(s"direct-missing-$missing")
          val model = _model_file(workspace, DartPackageSpecFixtures.source)
          val output = workspace.resolve("package")
          val withmodel = if (missing == "model") Request("dart") else Request("dart").add(Argument(model.toString))
          val withpackage = if (missing == "package") withmodel else withmodel.add(Property("package", "phase65_api", None))
          val request = if (missing == "save") withpackage else withpackage.add(Property("save", output.toString, None))
          val environment = cozy.Cozy.build(Array.empty).environment
          When("the operation is applied directly outside request construction")
          val response = ModelerServiceClass.DartOperationClass.apply(environment, request)
          Then("required arguments remain enforced by a structured failure and no output is created")
          response shouldBe a[ConclusionResponse]
          Files.exists(output) shouldBe false
        }
      }

      "apply the Dart request parser only to the explicit dart operation" in {
        Given("a request for another operation and the existing class/project public request shapes")
        val request = Request("class")
        When("the Dart operation's explicit applicability check considers that request")
        val result = ModelerServiceClass.DartOperationClass.makeRequest(request, Vector("unused"))
        Then("it declines the other operation and leaves the existing delegation surface in place")
        result shouldBe None
        ModelerServiceClass.ClassOperationClass.specification.name shouldBe "class"
        ModelerServiceClass.ProjectOperationClass.specification.name shouldBe "project"
      }
    }
  }

  private def _scratch(prefix: String): Path = {
    val root = Paths.get("target/phase65/s653/cli").toAbsolutePath
    Files.createDirectories(root)
    Files.createTempDirectory(root, prefix + "-")
  }

  private def _model_file(workspace: Path, source: String): Path = {
    val path = workspace.resolve("model.cml")
    Files.write(path, source.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _snapshot(directory: Path): Map[String, String] = {
    if (!Files.exists(directory)) Map.empty else {
      val stream = Files.walk(directory)
      try stream.iterator().asScala.filter(path => Files.isRegularFile(path)).map { path =>
        directory.relativize(path).toString.replace('\\', '/') -> new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
      }.toMap finally stream.close()
    }
  }

  private def _invalid_arguments(
    failure: String,
    valid: Path,
    malformed: Path,
    invalidcontract: Path,
    missing: Path,
    output: Path
  ): Array[String] = {
    val model = failure match {
      case "missing-file" => missing
      case "malformed-model" => malformed
      case "selected-contract" => invalidcontract
      case _ => valid
    }
    val modelarguments = if (failure == "missing-model") Vector.empty else Vector(model.toString)
    val packagearguments = if (failure == "missing-package") Vector.empty else
      Vector(if (failure == "package") "--package=Bad-Package" else "--package=phase65_api")
    val savearguments = if (failure == "missing-save") Vector.empty else Vector(s"--save=$output")
    val additional = failure match {
      case "charset" => Vector("--charset=invalid-charset")
      case name if name.startsWith("empty-") => Vector(s"--${name.stripPrefix("empty-")}=")
      case name if name.startsWith("malformed-") && name != "malformed-model" => Vector(s"--${name.stripPrefix("malformed-")}=one,,two")
      case "selected-contract" => Vector("--operations=SampleComponent.Catalog.lookup")
      case _ => Vector.empty
    }
    (Vector("modeler", "dart") ++ modelarguments ++ packagearguments ++ savearguments ++ additional).toArray
  }
}
