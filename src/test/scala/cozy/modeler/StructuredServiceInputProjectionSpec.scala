package cozy.modeler

import java.nio.file.{Files, Paths}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.simplemodeling.model._

/*
 * @since   Oct. 19, 2026
 * @version Oct. 26, 2026
 */
final class StructuredServiceInputProjectionSpec
    extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  "Structured service COMMAND inputs" should {
    "preserve the selected semantic Value" which {
      "carry every inline field in source order with scalar Json cardinality" in {
        Given("an inline CommandAction Value with version, requestId, payload, extensions and materials")
        val source = _contract(_book_fields)

        When("Kaleidox parses CML and the normal component model builder projects the command")
        val command = _operation(source)
        val input = _semantic_input(command)

        Then("one real Value replaces the Record placeholder and retains all ordered field identities")
        command.parameters.size shouldBe 1
        input.attributes.map(_.name) shouldBe List("version", "requestId", "payload", "extensions", "materials")
        input.attributes.take(2).map(_.attributeType) shouldBe List(MDataType(org.goldenport.record.v2.XInt), MDataType.string)
        input.attributes.drop(2).foreach { field =>
          field.attributeType shouldBe MBuiltinStructuredDataType.Json
          field.multiplicity shouldBe MOne
        }
        command.descriptor shouldBe MOperation.Descriptor.command
        command.result shouldBe MResult.unit
        command.body.isDefined shouldBe true
      }

      "resolve the canonical top-level Value selected by a COMMAND compatibility marker" in {
        Given("a referenced top-level BookMaterialInput extending CommandAction")
        val source = _contract(_book_fields, inline = false)

        When("the normal model builder and the canonical type binding entry project that selected Value")
        val normal = _semantic_input(_operation(source))
        val model = KaleidoxModel.parseWitoutLocation(KaleidoxConfig.default.withoutLocation, source)
        val catalog = PredefinedResultCatalog.loadRuntimeDescriptor(test_cncf_runtime_descriptor, "0.5.2-SNAPSHOT")
        val builder = Modeler.ModelBuilder(model).copy(predefinedResultCatalog = catalog)
        val data = builder.buildValue()
        val bindings = DartOperationProjection.canonicalBindings(builder, data)
        val projector = new ModelServiceOperationProjector(builder, new ModelTypeProjector(builder), Some(bindings))
        val canonical = _semantic_input(projector.definedServices(data.root, Vector.empty).
          find(_.name == "BookMaterial").get.operations.find(_.name == "saveMaterial").get)

        Then("both resolution paths preserve the actual declaration and builtin Json identity")
        canonical shouldBe normal
        canonical.name shouldBe "BookMaterialInput"
        canonical.attributes.map(_.name) shouldBe List("version", "requestId", "payload", "extensions", "materials")
        canonical.attributes.drop(2).map(_.attributeType).distinct shouldBe List(MBuiltinStructuredDataType.Json)
        canonical.attributes.map(_.multiplicity).distinct shouldBe List(MOne)
      }

      "carry the semistructured builtin without promoting it to Json" in {
        Given("a normal inline command with an explicitly declared SemiStructuredData field")
        val source = _contract(Vector(("evidence", "SemiStructuredData", "1"), ("caption", "string", "1")))

        When("the real CML parser and model builder project the semantic input")
        val input = _semantic_input(_operation(source))

        Then("the builtin remains SemiStructuredData with its declared cardinality and neighboring String")
        input.attributes.map(_.name) shouldBe List("evidence", "caption")
        input.attributes.map(_.attributeType) shouldBe List(MBuiltinStructuredDataType.SemiStructuredData, MDataType.string)
        input.attributes.map(_.multiplicity) shouldBe List(MOne, MOne)
      }

      "preserve identity order and multiplicity across independently shrinking CML samples" in {
        Given("independent safe field-name suffixes and scalar, optional and repeated source multiplicities")
        val suffixes = Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.take(10).mkString)
        val multiplicities = Gen.oneOf("1", "?", "*", "+")
        val property = Prop.forAll(suffixes, suffixes, multiplicities) { (first, second, cardinality) =>
          Given(s"a Json field a$first with multiplicity $cardinality, a String field b$second and scalar Json materials")
          val fields = Vector(("a" + first, "json", cardinality), ("b" + second, "string", "1"), ("materials", "json", "1"))
          val source = _contract(fields)

          When("each sampled CML declaration is parsed and projected through the normal service boundary")
          val input = _semantic_input(_operation(source))

          Then("each sampled semantic field has the declared name, position, builtin identity and multiplicity")
          input.attributes.map(_.name) shouldBe fields.map(_._1).toList
          input.attributes.map(_.attributeType) shouldBe List(MBuiltinStructuredDataType.Json, MDataType.string, MBuiltinStructuredDataType.Json)
          input.attributes.map(_.multiplicity) shouldBe List(MMultiplicity.create(cardinality), MOne, MOne)
          true
        }

        When("ScalaCheck evaluates the property without implication filters")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)

        Then("the executable contract requires at least one hundred successes and no discarded samples")
        withClue(result.toString) { result.passed shouldBe true }
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }

    "generate matching request schemas and action fields" which {
      "emit canonical XJson and one Json field for an array-valued materials document" in {
        Given("the inline BookMaterial command whose three Json documents each have multiplicity one")
        val source = _contract(_book_fields)

        When("Cozy runs real Scala generation from the CML model")
        val component = _generate("inline-json", source)

        Then("all request fields retain canonical schema datatypes and the action carries Json directly")
        _request_names(component) shouldBe Vector("version", "requestId", "payload", "extensions", "materials")
        _action_fields(component, "BookMaterialInput") shouldBe Vector(
          "request" -> "Request", "version" -> "Int", "requestId" -> "String",
          "payload" -> "io.circe.Json", "extensions" -> "io.circe.Json", "materials" -> "io.circe.Json")
        _request_field(component, "version") should include("datatype = org.goldenport.schema.XInt,")
        _request_field(component, "requestId") should include("datatype = org.goldenport.schema.XString,")
        Vector("payload", "extensions", "materials").foreach { name =>
          val field = _request_field(component, name)
          field should include("datatype = org.goldenport.schema.XJson,")
          field should include("multiplicity = org.goldenport.schema.Multiplicity.One")
          component should include(s"$name: io.circe.Json")
          component should not include s"$name: Vector[io.circe.Json]"
          component should not include s"$name: String"
          component should not include s"$name: Record"
        }
        component should include("version: Int")
        component should include("requestId: String")
        component should include("uowmNotImplemented[")
        component should include("OperationResponse.void")
        component should not include "DataType.Named(\"json\")"
        _action_fields(component, "BookMaterialInput").map(_._1) should not contain "record"
      }

      "generate referenced inputs and semistructured schemas through their actual identities" in {
        Given("a top-level Json input and an inline SemiStructuredData input")
        val top = _contract(_book_fields, inline = false)
        val semi = _contract(Vector(("evidence", "SemiStructuredData", "1")))

        When("Cozy generates both normal service command models")
        val topcomponent = _generate("top-level-json", top)
        val semicomponent = _generate("inline-semistructured", semi)

        Then("the referenced Json schema and semistructured schema select distinct canonical builtins")
        _action_fields(topcomponent, "BookMaterialInput").map(_._1) shouldBe
          Vector("request", "version", "requestId", "payload", "extensions", "materials")
        _request_field(topcomponent, "materials") should include("datatype = org.goldenport.schema.XJson,")
        topcomponent should include("materials: io.circe.Json")
        _request_field(semicomponent, "evidence") should include("datatype = org.goldenport.schema.XSemiStructuredData,")
        _action_fields(semicomponent, "BookMaterialInput") shouldBe
          Vector("request" -> "Request", "evidence" -> "org.goldenport.record.Record")
        semicomponent should not include "org.goldenport.schema.XJson"
      }

      "generate keyed readers for mixed optional repeated and nonempty structured command leaves" in {
        Given("a realistic command with a String sibling and required optional repeated and nonempty Json and Record fields")
        val fields = Vector(
          ("label", "string", "1"), ("requiredJson", "json", "1"), ("optionalJson", "json", "?"),
          ("jsons", "json", "*"), ("nonemptyJsons", "json", "+"),
          ("requiredRecord", "SemiStructuredData", "1"), ("optionalRecord", "SemiStructuredData", "?"),
          ("records", "SemiStructuredData", "*"), ("nonemptyRecords", "SemiStructuredData", "+"))
        val source = _contract(fields)

        When("normal CML parsing semantic projection and Scala generation process the actual command")
        val input = _semantic_input(_operation(source))
        val component = _generate("mixed-structured-cardinality", source)

        Then("the declared field order semantic types schema cardinalities and action types agree")
        input.attributes.map(_.name) shouldBe fields.map(_._1).toList
        input.attributes.map(_.multiplicity) shouldBe List(MOne, MOne, MZeroOne, MZeroMore, MOneMore,
          MOne, MZeroOne, MZeroMore, MOneMore)
        input.attributes.map(_.attributeType) shouldBe List(MDataType.string,
          MBuiltinStructuredDataType.Json, MBuiltinStructuredDataType.Json,
          MBuiltinStructuredDataType.Json, MBuiltinStructuredDataType.Json,
          MBuiltinStructuredDataType.SemiStructuredData, MBuiltinStructuredDataType.SemiStructuredData,
          MBuiltinStructuredDataType.SemiStructuredData, MBuiltinStructuredDataType.SemiStructuredData)
        _request_names(component) shouldBe fields.map(_._1)
        _action_fields(component, "BookMaterialInput") shouldBe Vector(
          "request" -> "Request", "label" -> "String", "requiredJson" -> "io.circe.Json",
          "optionalJson" -> "Option[io.circe.Json]", "jsons" -> "Vector[io.circe.Json]",
          "nonemptyJsons" -> "cats.data.NonEmptyVector[io.circe.Json]",
          "requiredRecord" -> "org.goldenport.record.Record", "optionalRecord" -> "Option[org.goldenport.record.Record]",
          "records" -> "Vector[org.goldenport.record.Record]", "nonemptyRecords" -> "cats.data.NonEmptyVector[org.goldenport.record.Record]")
        fields.drop(1).foreach { case (name, datatype, multiplicity) =>
          _request_field(component, name) should include(
            if (datatype == "json") "datatype = org.goldenport.schema.XJson," else "datatype = org.goldenport.schema.XSemiStructuredData,")
          val expected = Map("1" -> "One", "?" -> "ZeroOne", "*" -> "ZeroMore", "+" -> "OneMore")(multiplicity)
          _request_field(component, name) should include(s"multiplicity = org.goldenport.schema.Multiplicity.$expected)")
        }

        And("each named leaf uses the selected existing keyed reader for its actual cardinality")
        component should include("requiredJson <- Consequence.successOrRecordNotFound[io.circe.Json](\"requiredJson\", request.toRecord)")
        component should include("optionalJson <- request.toRecord.getAsC[io.circe.Json](\"optionalJson\")")
        component should include("jsons <- request.toRecord.getAsC[scala.Vector[io.circe.Json]](\"jsons\").map(_.getOrElse(Vector.empty))")
        component should include("nonemptyJsons <- Consequence.successOrRecordNotFound[cats.data.NonEmptyVector[io.circe.Json]](\"nonemptyJsons\", request.toRecord)")
        component should include("requiredRecord <- Consequence.successOrRecordNotFound[org.goldenport.record.Record](\"requiredRecord\", request.toRecord)")
        component should include("optionalRecord <- request.toRecord.getAsC[org.goldenport.record.Record](\"optionalRecord\")")
        component should include("records <- request.toRecord.getAsC[scala.Vector[org.goldenport.record.Record]](\"records\").map(_.getOrElse(Vector.empty))")
        component should include("nonemptyRecords <- Consequence.successOrRecordNotFound[cats.data.NonEmptyVector[org.goldenport.record.Record]](\"nonemptyRecords\", request.toRecord)")
      }

      "retain event effect body access and result when the action gains typed fields" in {
        Given("a structured event-effect-record command with manager access and name/title fallback fields")
        val fields = Vector(("name", "string", "1"), ("title", "string", "1"), ("payload", "json", "1"))
        val source = _contract(fields, implementation = Some("event-effect-record"), access = true)

        When("the normal model projection and Scala generator carry the command's actual input")
        val command = _operation(source)
        val component = _generate("event-effect", source)

        Then("the command preserves body access and unit result while reading the original request record")
        command.access.map(_.policy) shouldBe Some("manager_only")
        command.descriptor shouldBe MOperation.Descriptor.command
        command.result shouldBe MResult.unit
        command.body.isDefined shouldBe true
        _semantic_input(command).attributes.map(_.name) shouldBe List("name", "title", "payload")
        component should include("payload: io.circe.Json")
        component should include("name: String")
        component should include("title: String")
        component should include("action.request.toRecord.getString(\"name\").filter(_.nonEmpty).orElse(previous.getString(\"name\")).getOrElse(\"\")")
        component should include("action.request.toRecord.getString(\"title\").filter(_.nonEmpty).orElse(previous.getString(\"title\")).getOrElse(\"\")")
        component should include("core.component.foreach(_.recordEventEffect(record))")
        component should include("OperationResponse.void")
        component should include("manager_only")
        component should not include "action.record"
        _action_fields(component, "BookMaterialInput").map(_._1) should not contain "record"
      }
    }

    "preserve explicitly authored command parameters" which {
      Vector(("top-level-record", false, "record"), ("inline-record", true, "record"),
        ("inline-json", true, "json")).foreach { case (name, inline, datatype) =>
        s"retain optional request parameters and the opaque action envelope for $name input" in {
          Given(s"a $name CommandAction Value containing target and optional input and inputResource parameters")
          val source = _explicit_parameter_contract(inline, datatype)

          When("Kaleidox parses the authored parameters and Cozy projects and generates the command")
          val model = KaleidoxModel.parseWitoutLocation(KaleidoxConfig.default.withoutLocation, source)
          val raw = model.getServiceModel.get.classes.values.head.operations.operations.values.head
          val command = _operation(source)
          val component = _generate(s"explicit-parameters-$name", source)

          Then("the authored optional parameters remain the entire ordered request schema")
          raw.parameters.map(field => (field.name, field.datatype, field.multiplicity)) shouldBe
            Vector(("input", "BookMaterialInput", "?"), ("inputResource", "string", "?"))
          command.parameters shouldBe List(MParameter.record)
          _request_names(component) shouldBe Vector("input", "inputResource")
          _request_field(component, "input") should include("datatype = org.goldenport.schema.DataType.Named(\"bookmaterialinput\"),")
          _request_field(component, "inputResource") should include("datatype = org.goldenport.schema.XString,")
          Vector("input", "inputResource").foreach { field =>
            _request_field(component, field) should include("multiplicity = org.goldenport.schema.Multiplicity.ZeroOne)")
          }

          And("the action keeps its existing Record transport reader without a required nominal record or flattened target")
          _action_fields(component, "BookMaterialInput") shouldBe
            Vector("request" -> "Request", "record" -> "Record")
          component should include("Consequence.success(request.toRecord).")
          component should include("map(BookMaterialInput(request, _))")
          component should not include "record: value.BookMaterialInput"
          component should not include "BookMaterialInput.createC(request.toRecord)"
          _request_names(component) should not contain "record"
          _request_names(component) should not contain "target"
        }
      }
    }

    "retain legacy and named datatype boundaries" which {
      "keep pure String command and HealthQuery Record constructors" in {
        Given("a pure String command and the legacy HealthQuery Value extending QueryAction")
        val command = _contract(Vector(("message", "string", "1")))
        val health = _health_contract

        When("the existing model and Scala generator project the nonstructured commands and query")
        val commandmodel = _operation(command)
        val healthmodel = _operation(health, "health")
        val commandcomponent = _generate("legacy-string-command", command)
        val healthcomponent = _generate("legacy-health", health)

        Then("both operations retain exactly the established Record parameter and generated constructor")
        commandmodel.parameters shouldBe List(MParameter.record)
        healthmodel.parameters shouldBe List(MParameter.record)
        healthmodel.descriptor shouldBe MOperation.Descriptor.query
        commandcomponent should include("record: Record")
        healthcomponent should include("final case class HealthQuery private (")
        healthcomponent should include("record: Record")
        healthcomponent should include("HealthQuery(request, record)")
        commandcomponent should not include "org.goldenport.schema.XJson"
        healthcomponent should not include "org.goldenport.schema.XJson"
      }

      "leave a named custom datatype under its existing Record-only input boundary" in {
        Given("a custom PayloadToken Value type used by an input field named payload")
        val source = _contract(Vector(("payload", "PayloadToken", "1"))) +
          "\n# VALUE\n\n## PayloadToken\n\n### ATTRIBUTE\n\n| name | type | multiplicity |\n|------+------|--------------|\n| text | string | 1 |\n"

        When("Cozy parses projects and generates that named custom input")
        val command = _operation(source)
        val component = _generate("named-custom", source)

        Then("a field name never fabricates a builtin Json semantic identity")
        command.parameters shouldBe List(MParameter.record)
        component should include("record: Record")
        component should include("org.goldenport.schema.DataType.Named(\"payloadtoken\")")
        component should not include "org.goldenport.schema.XJson"
        component should not include "payload: io.circe.Json"
      }
    }
  }

  private def _operation(source: String, name: String = "saveMaterial"): MOperation = {
    val model = KaleidoxModel.parseWitoutLocation(KaleidoxConfig.default.withoutLocation, source)
    val catalog = PredefinedResultCatalog.loadRuntimeDescriptor(test_cncf_runtime_descriptor, "0.5.2-SNAPSHOT")
    val projected = Modeler.ModelBuilder(model).copy(predefinedResultCatalog = catalog).build()
    projected.elements.collect { case component: MComponent => component }.
      flatMap(_.services).flatMap(_.operations).find(_.name == name).
      getOrElse(fail(s"projected operation missing: $name"))
  }

  private def _semantic_input(command: MOperation): MValue =
    command.parameters match {
      case List(MParameter(_, MParameter.MObjectParameterType(input: MValue), MOne, Nil)) => input
      case other => fail(s"one semantic input Value required, got $other")
    }

  private def _generate(name: String, source: String): String = {
    val work = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().
      resolve(s"target/test-generated/structured-service-input/$name")
    val input = work.resolve("model.cml")
    val out = work.resolve("out")
    delete_recursively(work)
    write_file(input, source)
    val log = run_modeler_scala(input, out)
    val component = out.resolve("target/scala-3.3.8/src_managed/main/scala/domain/DomainComponent.scala")
    if (!Files.exists(component)) fail(s"generated component missing: $component\n$log")
    Files.readString(component)
  }

  private def _request_field(component: String, name: String): String = {
    val request = component.linesIterator.find(_.contains("request = RequestDefinition(")).getOrElse(fail("request schema missing"))
    request.split("ParameterDefinition\\(content = ").find(
      _.startsWith(s"""org.goldenport.value.BaseContent.simple("$name")""")
    ).getOrElse(fail(s"request field missing: $name"))
  }

  private def _request_names(component: String): Vector[String] = {
    val request = component.linesIterator.find(_.contains("request = RequestDefinition(")).getOrElse(fail("request schema missing"))
    "BaseContent\\.simple\\(\"([^\"]+)\"\\)".r.findAllMatchIn(request).map(_.group(1)).toVector
  }

  private def _action_fields(component: String, name: String): Vector[(String, String)] = {
    val marker = s"final case class $name private ("
    val start = component.indexOf(marker)
    if (start < 0) fail(s"generated action declaration missing: $name")
    val end = component.indexOf(") extends ", start)
    if (end < 0) fail(s"generated action declaration end missing: $name")
    component.substring(start + marker.length, end).linesIterator.
      map(_.trim.stripSuffix(",")).filter(_.contains(": ")).map { line =>
        val parts = line.split(": ", 2)
        parts(0) -> parts(1)
      }.toVector
  }

  private def _contract(
    fields: Vector[(String, String, String)],
    inline: Boolean = true,
    implementation: Option[String] = None,
    access: Boolean = false
  ): String = {
    val attributes = fields.map { case (name, datatype, multiplicity) => s"| $name | $datatype | $multiplicity |" }.mkString("\n")
    val table = s"| name | type | multiplicity |\n|------+------|--------------|\n$attributes\n"
    val input = if (inline)
      s"###### VALUE\nBookMaterialInput\n\n###### EXTENDS\nCommandAction\n\n###### ATTRIBUTE\n$table"
    else
      "###### TYPE\nBookMaterialInput\n"
    val top = if (inline) "" else s"\n# VALUE\n\n## BookMaterialInput\n\n### EXTENDS\nCommandAction\n\n### ATTRIBUTE\n$table"
    val impl = implementation.map(x => s"##### IMPLEMENTATION\n$x\n\n").getOrElse("")
    val policy = if (access) "### ACCESS\n\n#### POLICY\nmanager_only\n\n" else ""
    s"# COMPONENT\n\n## Domain\n\n### PACKAGE\ndomain\n\n# SERVICE\n\n## BookMaterial\n\n${policy}### OPERATION\n\n#### saveMaterial\n\n##### TYPE\nCOMMAND\n\n${impl}##### INPUT\n\n$input\n##### OUTPUT\n\n###### TYPE\nOperationResult\n$top"
  }

  private def _explicit_parameter_contract(inline: Boolean, datatype: String): String =
    _contract(Vector(("target", datatype, "1")), inline = inline).replace(
      "##### INPUT\n",
      "##### PARAMETER\n\n| name | type | multiplicity |\n|------+------|--------------|\n" +
        "| input | BookMaterialInput | ? |\n| inputResource | string | ? |\n\n##### INPUT\n"
    )

  private val _book_fields = Vector(
    ("version", "int", "1"), ("requestId", "string", "1"),
    ("payload", "json", "1"), ("extensions", "json", "1"), ("materials", "json", "1")
  )

  private val _health_contract =
    "# COMPONENT\n\n## Domain\n\n### PACKAGE\ndomain\n\n# SERVICE\n\n## BookMaterial\n\n### OPERATION\n\n#### health\n\n##### TYPE\nQUERY\n\n##### INPUT\n\n###### TYPE\nHealthQuery\n\n##### OUTPUT\n\n###### TYPE\nOperationResult\n\n# VALUE\n\n## HealthQuery\n\n### EXTENDS\nQueryAction\n\n### ATTRIBUTE\n\n| name | type | multiplicity |\n|------+------|--------------|\n| message | string | 1 |\n"
}
