package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 13, 2026
 * @version Jul. 13, 2026
 * @author  ASAMI, Tomoharu
 */
class CarCmlSourceResolverSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "CAR CML source resolution" should {
    "select the publication source" which {
      "prefers an explicit project CML source over the canonical source" in {
        _with_temp_dir("cozy-car-cml-explicit") { dir =>
          Given("a CAR project with explicit and canonical CML sources")
          _write_project(dir, Some("src/main/cozy/ai.cml"))
          _write_cml(dir.resolve("src/main/cozy/sample.cml"), "Canonical")
          val explicit =
            _write_cml(dir.resolve("src/main/cozy/ai.cml"), "Explicit")

          When("Cozy resolves the CAR publication CML source")
          val resolved = _resolved(CarCmlSourceResolver.resolve(dir, "sample"))

          Then("the explicit project-relative CML source is selected")
          resolved.source shouldBe explicit.toAbsolutePath.normalize()
          resolved.projectrelativepath shouldBe "src/main/cozy/ai.cml"
        }
      }

      "prefers the canonical source when more than one CML source exists" in {
        _with_temp_dir("cozy-car-cml-canonical") { dir =>
          Given("a CAR project with canonical and supplementary CML sources")
          _write_project(dir)
          val canonical =
            _write_cml(dir.resolve("src/main/cozy/sample.cml"), "Canonical")
          _write_cml(dir.resolve("src/main/cozy/support.cml"), "Support")

          When("Cozy resolves the CAR publication CML source")
          val resolved = _resolved(CarCmlSourceResolver.resolve(dir, "sample"))

          Then("the artifact-named canonical source is selected")
          resolved.source shouldBe canonical.toAbsolutePath.normalize()
        }
      }

      "uses the only CML source as a noncanonical fallback" in {
        _with_temp_dir("cozy-car-cml-single") { dir =>
          Given("a CAR project with one noncanonical CML source")
          _write_project(dir)
          val source =
            _write_cml(dir.resolve("src/main/cozy/ai.cml"), "TextusAi")

          When("Cozy resolves the CAR publication CML source")
          val resolved = _resolved(CarCmlSourceResolver.resolve(dir, "sample"))

          Then(
            "the single source is selected without changing its source identity"
          )
          resolved.source shouldBe source.toAbsolutePath.normalize()
          resolved.projectrelativepath shouldBe "src/main/cozy/ai.cml"
        }
      }
    }

    "reject invalid source declarations" which {
      "reports a missing implicit source" in {
        _with_temp_dir("cozy-car-cml-missing") { dir =>
          Given("a CAR project with no CML source")
          _write_project(dir)

          When("Cozy resolves the CAR publication CML source")
          val issue = _issue(CarCmlSourceResolver.resolve(dir, "sample"))

          Then("the missing source is reported deterministically")
          issue.code shouldBe "car.cml.source.missing"
        }
      }

      "reports ambiguous noncanonical sources" in {
        _with_temp_dir("cozy-car-cml-ambiguous") { dir =>
          Given("a CAR project with two noncanonical CML sources")
          _write_project(dir)
          _write_cml(dir.resolve("src/main/cozy/a.cml"), "A")
          _write_cml(dir.resolve("src/main/cozy/b.cml"), "B")

          When("Cozy resolves the CAR publication CML source")
          val issue = _issue(CarCmlSourceResolver.resolve(dir, "sample"))

          Then("the caller is directed to select the source explicitly")
          issue.code shouldBe "car.cml.source.ambiguous"
          issue.message should include("src/main/cozy/a.cml")
          issue.message should include("src/main/cozy/b.cml")
        }
      }

      "rejects a source that escapes the project" in {
        _with_temp_dir("cozy-car-cml-traversal") { dir =>
          Given(
            "a CAR project whose explicit source traverses outside the project"
          )
          _write_project(dir, Some("../outside.cml"))

          When("Cozy resolves the explicit source")
          val issue = _issue(CarCmlSourceResolver.resolve(dir, "sample"))

          Then("the source is rejected as outside the project")
          issue.code shouldBe "car.cml.source.outside_project"
        }
      }

      "rejects an absolute source" in {
        _with_temp_dir("cozy-car-cml-absolute") { dir =>
          Given("a CAR project whose explicit source is absolute")
          val source =
            _write_cml(dir.resolve("src/main/cozy/sample.cml"), "Sample")
          _write_project(dir, Some(source.toString))

          When("Cozy resolves the explicit source")
          val issue = _issue(CarCmlSourceResolver.resolve(dir, "sample"))

          Then("the source is rejected as outside the project contract")
          issue.code shouldBe "car.cml.source.outside_project"
        }
      }

      "rejects a source with a non-CML extension" in {
        _with_temp_dir("cozy-car-cml-extension") { dir =>
          Given("a CAR project whose explicit source is not a CML file")
          _write_project(dir, Some("src/main/cozy/sample.dox"))

          When("Cozy resolves the explicit source")
          val issue = _issue(CarCmlSourceResolver.resolve(dir, "sample"))

          Then("the source is rejected without implicit fallback")
          issue.code shouldBe "car.cml.source.invalid_extension"
        }
      }

      "rejects a missing explicit source without fallback" in {
        _with_temp_dir("cozy-car-cml-explicit-missing") { dir =>
          Given(
            "a CAR project with a missing explicit source and a valid canonical source"
          )
          _write_project(dir, Some("src/main/cozy/missing.cml"))
          _write_cml(dir.resolve("src/main/cozy/sample.cml"), "Sample")

          When("Cozy resolves the explicit source")
          val issue = _issue(CarCmlSourceResolver.resolve(dir, "sample"))

          Then("the configured source failure remains authoritative")
          issue.code shouldBe "car.cml.source.not_found"
        }
      }
    }

    "protect the publication lifecycle" which {
      "publishes sidecars from a noncanonical explicit source" in {
        _with_temp_dir("cozy-car-cml-publish-explicit") { dir =>
          Given(
            "a CAR project with an explicit noncanonical CML source and a prebuilt CAR"
          )
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          _write_project(projectdir, Some("src/main/cozy/ai.cml"))
          _write_cml(projectdir.resolve("src/main/cozy/ai.cml"), "TextusAi")
          val car = _write(dir.resolve("input/sample.car"), "car-body")

          When("Cozy publishes the CAR")
          _publish(projectdir, warehouse, car, "sample")

          Then(
            "artifact-named sidecars preserve the actual project source path"
          )
          val catalogdir = warehouse.resolve("repository/catalog/car")
          Files.isRegularFile(catalogdir.resolve("sample.cml")) shouldBe true
          Files.isRegularFile(
            catalogdir.resolve("sample.model-metadata.json")
          ) shouldBe true
          Files.isRegularFile(
            catalogdir.resolve("sample.model-metadata.yaml")
          ) shouldBe true
          _read(
            catalogdir.resolve("sample.model-metadata.json")
          ) should include("\"path\" : \"src/main/cozy/ai.cml\"")
        }
      }

      "fails before warehouse mutation when CML resolution is ambiguous" in {
        _with_temp_dir("cozy-car-cml-publish-ambiguous") { dir =>
          Given("a CAR project with ambiguous CML sources and a prebuilt CAR")
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          _write_project(projectdir)
          _write_cml(projectdir.resolve("src/main/cozy/a.cml"), "A")
          _write_cml(projectdir.resolve("src/main/cozy/b.cml"), "B")
          val car = _write(dir.resolve("input/sample.car"), "car-body")

          When("Cozy attempts to publish the CAR")
          val error = intercept[RuntimeException] {
            _publish(projectdir, warehouse, car, "sample")
          }

          Then("publication fails before CAR or catalog files are written")
          error.getMessage should include("car.cml.source.ambiguous")
          Files.isRegularFile(
            warehouse.resolve("repository/car/sample/0.1.0/sample-0.1.0.car")
          ) shouldBe false
          Files.isRegularFile(
            warehouse.resolve("repository/catalog/car/sample.yaml")
          ) shouldBe false
        }
      }

      "fails before warehouse mutation when publication name differs from project identity" in {
        _with_temp_dir("cozy-car-cml-publish-name-mismatch") { dir =>
          Given(
            "a CAR project and publication request with different artifact identities"
          )
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          _write_project(projectdir)
          _write_cml(projectdir.resolve("src/main/cozy/sample.cml"), "Sample")
          val car = _write(dir.resolve("input/sample.car"), "car-body")

          When("Cozy attempts to publish under the different name")
          val error = intercept[RuntimeException] {
            _publish(projectdir, warehouse, car, "other")
          }

          Then("the identity mismatch is reported before warehouse mutation")
          error.getMessage should include("car.cml.artifact_id.mismatch")
          Files.exists(warehouse.resolve("repository/car/other")) shouldBe false
          Files.exists(
            warehouse.resolve("repository/catalog/car/other.yaml")
          ) shouldBe false
        }
      }
    }
  }

  private def _publish(
      projectdir: Path,
      warehouse: Path,
      car: Path,
      name: String
  ): Unit =
    CozyCarPublisher.publish(
      List(
        projectdir.toString,
        "--warehouse",
        warehouse.toString,
        "--name",
        name,
        "--version",
        "0.1.0",
        "--car",
        car.toString
      )
    )

  private def _resolved(
      result: Either[CarCmlSourceResolver.Issue, CarCmlSourceResolver.Resolved]
  ): CarCmlSourceResolver.Resolved =
    result.fold(issue => fail(issue.message), identity)

  private def _issue(
      result: Either[CarCmlSourceResolver.Issue, CarCmlSourceResolver.Resolved]
  ): CarCmlSourceResolver.Issue =
    result.fold(
      identity,
      resolved => fail(s"Expected failure but resolved ${resolved.source}")
    )

  private def _write_project(
      dir: Path,
      cmlsource: Option[String] = None
  ): Path = {
    val cml =
      cmlsource.map(source => s"cml:\n  source: ${source}\n").getOrElse("")
    _write(dir.resolve("project.yaml"), s"project:\n  name: sample\n${cml}")
  }

  private def _write_cml(path: Path, name: String): Path =
    _write(path, s"# COMPONENT\n\n## ${name}\n")

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    path.toAbsolutePath.normalize()
  }

  private def _read(path: Path): String =
    new String(Files.readAllBytes(path), StandardCharsets.UTF_8)

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val dir = Files.createTempDirectory(prefix)
    try body(dir)
    finally _delete(dir)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try
        stream
          .iterator()
          .asScala
          .toVector
          .sortBy(_.getNameCount)
          .reverse
          .foreach(Files.deleteIfExists)
      finally stream.close()
    }
}
