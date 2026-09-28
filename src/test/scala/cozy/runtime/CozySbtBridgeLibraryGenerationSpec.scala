package cozy.runtime

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.Comparator
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozySbtBridgeLibraryGenerationSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()

  "sbt-bridge library generation" should {
    "dispatch both library fixtures through the actual JSON bridge without CNCF runtime input" in {
      Given("the copied library fixtures and a Maven library project contract")
      val root = _base.resolve("target/test-generated/sbt-bridge-library-generation")
      _delete_tree(root)
      val inputs = Vector("address.cml", "user-profile-values.cml").map(
        _base.resolve("src/test/resources/modeler/library").resolve
      )

      When("native and sbt-bridge library generation process each fixture")
      val generated = inputs.map { input =>
        val name = input.getFileName.toString.stripSuffix(".cml")
        val nativeout = root.resolve("native").resolve(name)
        val bridgeproject = root.resolve("bridge-project").resolve(name)
        val bridgeout = root.resolve("bridge-output").resolve(name)
        _write(bridgeproject.resolve("project.yaml"), _library_project_yaml)
        _generate_native(input, nativeout)
        val request = _write_request(input, bridgeout, bridgeproject)
        cozy.Cozy.main(Array("sbt-bridge", "v1", "--request", request.toString))
        nativeout -> bridgeout
      }

      Then("all eleven pure value sources match native output and carry no CNCF reference")
      val bridgefiles = generated.flatMap { case (_, bridgeout) => _scala_files(bridgeout) }
      bridgefiles should have size 11
      bridgefiles.foreach { file =>
        Files.readString(file, StandardCharsets.UTF_8) should not include "org.goldenport.cncf"
      }
      generated.foreach { case (nativeout, bridgeout) =>
        _scala_bytes(nativeout) shouldBe _scala_bytes(bridgeout)
      }
    }

    "reject a library runtime contradiction and an inexact Cozy runtime" in {
      Given("contradictory library bridge configurations")
      val base = Map(
        "generation.target" -> "library",
        "component.namespace" -> "org.simplemodeling",
        "component.id" -> "SimpleModelingModel"
      )

      When("the bridge validates the request")
      val runtimeerror = intercept[Exception] {
        CozySbtBridge._library_modeler_args_for_settings_for_test(
          base ++ Map(
            "generation.versions.cozy" -> org.simplemodeling.cozy.BuildInfo.version,
            "generation.versions.cncf" -> "0.5.2"
          )
        )
      }
      val versionerror = intercept[Exception] {
        CozySbtBridge._library_modeler_args_for_settings_for_test(
          base + ("generation.versions.cozy" -> "0.0.0")
        )
      }
      val emptydescriptorerror = intercept[Exception] {
        CozySbtBridge._library_modeler_args_for_settings_for_test(
          base ++ Map(
            "generation.versions.cozy" -> org.simplemodeling.cozy.BuildInfo.version,
            "runtime.cncf.descriptor" -> ""
          )
        )
      }

      Then("both errors are deterministic before native generation")
      runtimeerror.getMessage should include("LIBRARY_GENERATION_RUNTIME_CONTRADICTION")
      versionerror.getMessage should include("LIBRARY_GENERATION_COZY_VERSION_MISMATCH")
      emptydescriptorerror.getMessage should include("LIBRARY_GENERATION_RUNTIME_CONTRADICTION")
    }

    "reject CAR and SAR project contracts before library code generation" in {
      Given("archive project metadata carried through the bridge project directory")
      val root = _base.resolve("target/test-generated/sbt-bridge-library-archive-guard")
      _delete_tree(root)

      When("each archive declares generation.target=library")
      val errors = Vector("car", "sar").map { kind =>
        val projectdir = root.resolve(kind)
        _write(
          projectdir.resolve("project.yaml"),
          s"""project:
             |  kind: $kind
             |packaging:
             |  kind: $kind
             |""".stripMargin
        )
        intercept[Exception] {
          CozySbtBridge._library_modeler_args_for_settings_for_test(
            _library_settings + ("sbt.project_dir" -> projectdir.toString)
          )
        }
      }

      Then("both archive kinds fail with the deterministic archive-target diagnostic")
      errors.foreach { error =>
        error.getMessage should include("LIBRARY_GENERATION_ARCHIVE_TARGET_REJECTED")
      }
    }
  }

  private def _library_settings: Map[String, String] = Map(
    "generation.target" -> "library",
    "generation.versions.cozy" -> org.simplemodeling.cozy.BuildInfo.version,
    "component.namespace" -> "org.simplemodeling",
    "component.id" -> "SimpleModelingModel"
  )

  private def _generate_native(input: Path, out: Path): Unit =
    cozy.Cozy.main(Array(
      "modeler-scala-value", input.toString,
      "--save", out.toString,
      "--generation-target", "library",
      "--cozy-generator-version", org.simplemodeling.cozy.BuildInfo.version
    ))

  private def _write_request(input: Path, out: Path, projectdir: Path): Path = {
    val request = projectdir.resolve("library-generation-request.json")
    _write(
      request,
      s"""{
         |  "version": "v1",
         |  "action": "generate",
         |  "arguments": ["modeler-scala-value", "${input.toString}", "--save", "${out.toString}"],
         |  "settings": {
         |    "generation.target": "library",
         |    "generation.versions.cozy": "${org.simplemodeling.cozy.BuildInfo.version}",
         |    "component.namespace": "org.simplemodeling",
         |    "component.id": "SimpleModelingModel",
         |    "sbt.project_dir": "${projectdir.toString}"
         |  }
         |}
         |""".stripMargin
    )
  }

  private def _scala_files(root: Path): Vector[Path] = {
    val stream = Files.walk(root)
    try stream.iterator().asScala.filter(path => path.toString.endsWith(".scala")).toVector.sortBy(_.toString)
    finally stream.close()
  }

  private def _scala_bytes(root: Path): Vector[(String, Vector[Byte])] =
    _scala_files(root).map { path =>
      root.relativize(path).toString.replace('\\', '/') -> Files.readAllBytes(path).toVector
    }

  private def _write(path: Path, text: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, text, StandardCharsets.UTF_8)
    path
  }

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.sorted(Comparator.reverseOrder()).iterator().asScala.foreach(Files.deleteIfExists)
      finally stream.close()
    }

  private val _library_project_yaml =
    """project:
      |  kind: library
      |packaging:
      |  kind: maven
      |""".stripMargin
}
