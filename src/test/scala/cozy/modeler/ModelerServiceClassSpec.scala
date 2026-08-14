package cozy.modeler

import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import org.goldenport.cli.{ConclusionResponse, FileRealmResponse, FileResponse, Request}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 14, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class ModelerServiceClassSpec extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  "Modeler service facade" should {
    "expose class and project as explicit typed operations" in {
      Given("the registered Cozy modeler service and its operation specifications")
      val workspace = _workspace("typed-success")
      try {
        Files.createDirectories(workspace)
        val model = _model_file
        val classoutput = workspace.resolve("class-output")
        val projectoutput = workspace.resolve("project-output")
        val classargs = Array("modeler", "class", model.toString, "--save", classoutput.toString)
        val projectargs = Array("modeler", "project", model.toString, "--save", projectoutput.toString)

        When("the class and project operations are invoked through the registered CLI service")
        val diagram = cozy.Cozy.build(classargs).execute(classargs)
        val project = cozy.Cozy.build(projectargs).execute(projectargs)

        Then("class returns SVG and project returns a generated Realm")
        ModelerServiceClass.defaultOperation shouldBe None
        ModelerServiceClass.ClassOperationClass.specification.name shouldBe "class"
        ModelerServiceClass.ProjectOperationClass.specification.name shouldBe "project"
        ModelerServiceClass.ClassOperationClass.request.parameters.map(_.name) shouldBe List("model", "save", "charset")
        diagram shouldBe a[FileResponse]
        val diagramresponse = diagram.asInstanceOf[FileResponse]
        diagramresponse.bag.mimetype.name shouldBe "image/svg+xml"
        Files.isRegularFile(classoutput.resolve(diagramresponse.bag.filename)) shouldBe true
        project shouldBe a[FileRealmResponse]
        val projectsource = projectoutput.resolve("target/scala-3.3.8/src_managed/main/scala")
        Files.isDirectory(projectsource) shouldBe true
        val projectfiles = Files.walk(projectsource)
        try {
          projectfiles.iterator().asScala.exists(path => Files.isRegularFile(path)) shouldBe true
        } finally {
          projectfiles.close()
        }
        val repositorybase = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        Files.exists(repositorybase.resolve("image.svg")) shouldBe false
        Files.exists(repositorybase.resolve("src/main/scala/domain")) shouldBe false
      } finally {
        delete_recursively(workspace)
      }
    }

    "return typed success and structured failures for charset and model errors" in {
      Given("a target-contained workspace with valid, malformed, and missing model inputs")
      val workspace = _workspace("failure-and-charset")
      try {
        val modeltext = Files.readString(_model_file, StandardCharsets.UTF_8).
          replace("## Person\n", "## Person\n\n### SUMMARY\nCafé\n")
        val latinmodel = workspace.resolve("latin1-model.dox")
        Files.createDirectories(workspace)
        Files.write(latinmodel, modeltext.getBytes(Charset.forName("ISO-8859-1")))
        val malformed = workspace.resolve("malformed-model.dox")
        Files.writeString(malformed, "# ENTITY\n\n## Person\n\nATTRIBUTE", StandardCharsets.UTF_8)
        val missingmodel = workspace.resolve("missing-model.dox")
        val validoutput = workspace.resolve("valid-output")
        val invalidcharsetoutput = workspace.resolve("invalid-charset-output")
        val missingoutput = workspace.resolve("missing-output")
        val malformedoutput = workspace.resolve("malformed-output")

        When("the public CLI receives valid charset, invalid charset, missing, and malformed model requests")
        val validargs = Array("modeler", "class", latinmodel.toString, "--charset", "ISO-8859-1", "--save", validoutput.toString)
        val invalidcharsetargs = Array("modeler", "class", latinmodel.toString, "--charset", "not-a-charset", "--save", invalidcharsetoutput.toString)
        val missingargs = Array("modeler", "class", missingmodel.toString, "--save", missingoutput.toString)
        val malformedargs = Array("modeler", "class", malformed.toString, "--save", malformedoutput.toString)
        val valid = cozy.Cozy.build(validargs).execute(validargs)
        val invalidcharset = cozy.Cozy.build(invalidcharsetargs).execute(invalidcharsetargs)
        val missingfile = cozy.Cozy.build(missingargs).execute(missingargs)
        val malformedresponse = cozy.Cozy.build(malformedargs).execute(malformedargs)
        val missingrequest = ModelerServiceClass.ClassOperationClass.apply(
          cozy.Cozy.build(Array.empty).environment,
          Request("class")
        )

        Then("valid charset yields a saved SVG while each invalid request yields a structured conclusion")
        valid shouldBe a[FileResponse]
        val validresponse = valid.asInstanceOf[FileResponse]
        validresponse.bag.mimetype.name shouldBe "image/svg+xml"
        Files.isRegularFile(validoutput.resolve(validresponse.bag.filename)) shouldBe true
        invalidcharset shouldBe a[ConclusionResponse]
        missingfile shouldBe a[ConclusionResponse]
        malformedresponse shouldBe a[ConclusionResponse]
        missingrequest shouldBe a[ConclusionResponse]
        Files.exists(invalidcharsetoutput) shouldBe false
        Files.exists(missingoutput) shouldBe false
        Files.exists(malformedoutput) shouldBe false
        val repositorybase = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        Files.exists(repositorybase.resolve("image.svg")) shouldBe false
        Files.exists(repositorybase.resolve("src/main/scala/domain")) shouldBe false
      } finally {
        delete_recursively(workspace)
      }
    }
  }

  private def _workspace(name: String): Path =
    Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().
      resolve("target/test-generated/modeler-service").resolve(name)

  private def _model_file: Path =
    Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("src/test/resources/modeler/test.dox")
}
