package cozy.modeler

import java.nio.file.{Files, Paths}
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.simplemodeling.model._

/*
 * @since   Oct. 21, 2026
 * @version Oct. 21, 2026
 */
final class ServiceQualifiedOperationProjectionSpec
    extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  "Service-qualified operation projection" should {
    "carry actual service ownership beside normalized metadata" which {
      "preserve distinct readOriginal inputs ordered fields and output types through real CML projection" in {
        Given("two real CML services sharing readOriginal with distinct inline input and result Values")
        val source = _contract()
        val model = KaleidoxModel.parseWitoutLocation(KaleidoxConfig.default.withoutLocation, source)
        val catalog = PredefinedResultCatalog.loadRuntimeDescriptor(test_cncf_runtime_descriptor, "0.5.2-SNAPSHOT")
        val builder = Modeler.ModelBuilder(model).copy(predefinedResultCatalog = catalog)
        val projector = new ModelServiceOperationProjector(builder, new ModelTypeProjector(builder))

        When("the native owned and compatibility projections interpret the same parsed service definitions")
        val owned = projector.serviceOperationDefinitions
        val normalized = projector.normalizedServiceOperationDefinitions
        val component = _component(builder.build())

        Then("the owned sequence retains the actual service beside the unchanged normalized definition")
        normalized shouldBe owned.map(_._2)
        owned.map(_._1) shouldBe _owners
        owned.map(_._2.name) shouldBe Vector("readOriginal", "readOriginal")
        owned.map(_._2.inputType) shouldBe _inputs
        owned.map(_._2.outputType) shouldBe _outputs
        owned.map(_._2.parameters.map(_.name)) shouldBe _fields
        _owners.zipWithIndex.foreach { case (owner, index) =>
          val definition = component.operationDefinitions.find(_.serviceName.contains(owner)).get
          definition.name shouldBe "readOriginal"
          definition.inputType shouldBe _inputs(index)
          definition.outputType shouldBe _outputs(index)
          definition.parameters.map(_.name) shouldBe _fields(index)
          definition.parameters.map(_.datatype) shouldBe Vector("string", "string")
          definition.parameters.map(_.multiplicity) shouldBe Vector("1", "1")
          definition.resultFields.map(_.name) shouldBe Vector("content")
          definition.resultFields.map(_.datatype) shouldBe Vector("string")
          definition.kind shouldBe "QUERY"
          definition.inputValueKind shouldBe "QUERY_VALUE"
          definition.access.map(_.policy) shouldBe Some(_policies(index))
        }
      }

      "retain owner kind and access through reversed declarations for independent valid owner names" in {
        Given("independently generated safe service names with distinct prefixes and a same-name COMMAND/QUERY pair")
        val names = Gen.listOfN(8, Gen.alphaLowerChar).map(_.mkString)
        val property = Prop.forAll(names, names) { (first, second) =>
          Given("two distinct sampled service names and both source declaration orders")
          val owners = Vector("Book" + first, "Knowledge" + second)
          val sources = Vector(false, true).map(reversed => _contract(owners, reversed, command = true))

          When("Kaleidox parses and the normal model builder projects each declaration order")
          val components = sources.map(source => _project(source))

          Then("each service retains its kind input ordered fields output access and implementation independently")
          components.foreach { component =>
            owners.zipWithIndex.foreach { case (owner, index) =>
              val definition = component.operationDefinitions.find(_.serviceName.contains(owner)).get
              definition.name shouldBe "readOriginal"
              definition.kind shouldBe (if (index == 0) "COMMAND" else "QUERY")
              definition.inputValueKind shouldBe (if (index == 0) "COMMAND_VALUE" else "QUERY_VALUE")
              definition.inputType shouldBe _inputs(index)
              definition.outputType shouldBe _outputs(index)
              definition.parameters.map(_.name) shouldBe _fields(index)
              definition.parameters.map(_.multiplicity) shouldBe Vector("1", "1")
              definition.access.map(_.policy) shouldBe Some(_policies(index))
              definition.implementation shouldBe (if (index == 0) Some("echo-record") else None)
              val operation = component.services.find(_.name == owner).get.operations.head
              operation.descriptor.kind shouldBe (if (index == 0) MOperation.Kind.Command else MOperation.Kind.Query)
              operation.access.map(_.policy) shouldBe Some(_policies(index))
            }
          }
          true
        }

        When("ScalaCheck evaluates real parsing and projection with a deterministic seed and no implication filters")
        val result = Test.check(Test.Parameters.default.withInitialSeed(Seed(20261021L)).withMinSuccessfulTests(20), property)

        Then("at least twenty successful samples and zero discards document owner isolation")
        withClue(result.toString) { result.passed shouldBe true }
        result.succeeded should be >= 20
        result.discarded shouldBe 0
      }
    }

    "emit service-local contracts through the native Scala generation pipeline" which {
      "generate distinct query request action factory and response contracts from actual CML" in {
        Given("the original BookMaterial and KnowledgeMaterial readOriginal CML contracts")
        val source = _contract()

        When("the existing run_modeler_scala path generates source under its owned target work directory")
        val generated = _generate("read-original", source)
        val book = _block(generated, "object BookMaterialService extends ServiceDefinition")
        val knowledge = _block(generated, "object KnowledgeMaterialService extends ServiceDefinition")

        Then("each service has its exact ordered required fields and selected request response and action types")
        _request_names(book) shouldBe _fields.head
        _request_names(knowledge) shouldBe _fields(1)
        _require_fields(book, _fields.head)
        _require_fields(knowledge, _fields(1))
        book should include("final case class BookMaterialReadOriginalQuery private (")
        knowledge should include("final case class OriginalSelectionQuery private (")
        book should include("BookMaterialReadOriginalQuery.create(req)")
        knowledge should include("OriginalSelectionQuery.create(req)")
        book should include("DataType.Named(\"BookOriginalResult\")")
        knowledge should include("DataType.Named(\"KnowledgeOriginalResult\")")
        book should not include "materialRef"
        book should not include "OriginalSelectionQuery"
        knowledge should not include "originalId"
        knowledge should not include "BookMaterialReadOriginalQuery"
        _block(generated, "class BookMaterialServiceFactory()") should include("action: BookMaterialReadOriginalQuery")
        _block(generated, "class KnowledgeMaterialServiceFactory()") should include("action: OriginalSelectionQuery")
      }

      "keep same-name command query and access controls local when source declarations are reversed" in {
        Given("reversed CML declarations where BookMaterial alone is an echo-record COMMAND")
        val source = _contract(reversed = true, command = true)

        When("the existing native generation pipeline emits the command and query services")
        val generated = _generate("reversed-command-query", source)
        val component = _project(source)
        val book = _block(generated, "object BookMaterialService extends ServiceDefinition")
        val knowledge = _block(generated, "object KnowledgeMaterialService extends ServiceDefinition")

        Then("the generated action kinds input fields and command body and modeled access remain owned by their service")
        _request_names(book) shouldBe _fields.head
        _request_names(knowledge) shouldBe _fields(1)
        book should include("extends CommandAction")
        knowledge should include("extends QueryAction")
        _owners.zipWithIndex.foreach { case (owner, index) =>
          val definition = component.operationDefinitions.find(_.serviceName.contains(owner))
            .getOrElse(fail(s"projected operation metadata missing: $owner"))
          definition.access.map(_.policy) shouldBe Some(_policies(index))
          definition.serviceName shouldBe Some(owner)
        }
        book should include("Consequence.success(action.request.toRecord)")
        knowledge should not include "Consequence.success(action.request.toRecord)"
        book should include("final case class BookMaterialReadOriginalQuery private (")
        knowledge should include("final case class OriginalSelectionQuery private (")
      }
    }
  }

  private val _owners = Vector("BookMaterial", "KnowledgeMaterial")
  private val _inputs = Vector("BookMaterialReadOriginalQuery", "OriginalSelectionQuery")
  private val _outputs = Vector("BookOriginalResult", "KnowledgeOriginalResult")
  private val _fields = Vector(Vector("resourceId", "originalId"), Vector("materialRef", "resourceId"))
  private val _policies = Vector("manager_only", "owner_only")

  private def _contract(
    owners: Vector[String] = _owners,
    reversed: Boolean = false,
    command: Boolean = false
  ): String = {
    val services = owners.zipWithIndex.map { case (owner, index) =>
      val kind = if (command && index == 0) "COMMAND" else "QUERY"
      val implementation = if (command && index == 0) "##### IMPLEMENTATION\necho-record\n\n" else ""
      val attributes = _fields(index).map(name => s"| $name | string | 1 |").mkString("\n         |")
      s"""## $owner
         |
         |### ACCESS
         |
         |#### POLICY
         |${_policies(index)}
         |
         |### OPERATION
         |
         |#### readOriginal
         |
         |##### TYPE
         |$kind
         |
         |${implementation}##### INPUT
         |
         |###### VALUE
         |${_inputs(index)}
         |
         |###### ATTRIBUTE
         |
         || name | type | multiplicity |
         ||------+------|--------------|
         |$attributes
         |
         |##### OUTPUT
         |
         |###### VALUE
         |${_outputs(index)}
         |
         |###### ATTRIBUTE
         |
         || name | type | multiplicity |
         ||------+------|--------------|
         || content | string | 1 |
         |""".stripMargin
    }
    val ordered = if (reversed) services.reverse else services
    "# COMPONENT\n\n## Domain\n\n### PACKAGE\ndomain\n\n# SERVICE\n\n" + ordered.mkString("\n")
  }

  private def _project(source: String): MComponent with MComponent.Core.Holder = {
    val model = KaleidoxModel.parseWitoutLocation(KaleidoxConfig.default.withoutLocation, source)
    val catalog = PredefinedResultCatalog.loadRuntimeDescriptor(test_cncf_runtime_descriptor, "0.5.2-SNAPSHOT")
    _component(Modeler.ModelBuilder(model).copy(predefinedResultCatalog = catalog).build())
  }

  private def _component(model: SimpleModel): MComponent with MComponent.Core.Holder =
    model.elements.collectFirst { case component: MComponent with MComponent.Core.Holder => component }
      .getOrElse(fail("projected component metadata missing"))

  private def _generate(name: String, source: String): String = {
    val work = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      .resolve(s"target/test-generated/service-qualified-operation/$name")
    val input = work.resolve("model.cml")
    val out = work.resolve("out")
    delete_recursively(work)
    try {
      write_file(input, source)
      val log = run_modeler_scala(input, out)
      val component = out.resolve("target/scala-3.3.8/src_managed/main/scala/domain/DomainComponent.scala")
      if (!Files.exists(component)) fail(s"generated component missing: $component\n$log")
      Files.readString(component)
    } finally {
      delete_recursively(work)
    }
  }

  private def _block(source: String, marker: String): String = {
    val start = source.indexOf(marker)
    if (start < 0) fail(s"generated declaration missing: $marker")
    val opening = source.indexOf('{', start)
    var depth = 1
    var index = opening + 1
    while (index < source.length && depth > 0) {
      source.charAt(index) match {
        case '{' => depth += 1
        case '}' => depth -= 1
        case _ => ()
      }
      index += 1
    }
    if (depth != 0) fail(s"generated declaration not closed: $marker")
    source.substring(start, index)
  }

  private def _request_names(block: String): Vector[String] = {
    val request = block.linesIterator.find(_.contains("request = RequestDefinition(")).getOrElse(fail("request schema missing"))
    "BaseContent\\.simple\\(\"([^\"]+)\"\\)".r.findAllMatchIn(request).map(_.group(1)).toVector
  }

  private def _require_fields(block: String, names: Vector[String]): Unit = {
    val request = block.linesIterator.find(_.contains("request = RequestDefinition(")).getOrElse(fail("request schema missing"))
    names.foreach { name =>
      val field = request.split("ParameterDefinition\\(content = ").find(_.startsWith(s"""org.goldenport.value.BaseContent.simple("$name")"""))
        .getOrElse(fail(s"request field missing: $name"))
      field should include("multiplicity = org.goldenport.schema.Multiplicity.One")
      field should include("required = Some(true)")
    }
  }
}
