package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 13, 2026
 * @version Aug. 19, 2026
 * @author  ASAMI, Tomoharu
 */
class CarCmlSourceResolverSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private def _metadata(example: String) = afterWord(
    s"in spec:phase-56-component-identity-project-contract, example:$example, rules:CID07-R1, phase:56, slice:CID-07C"
  )

  "CAR CML source resolution" should {
    "select the publication source" which {
      "E1 canonical artifact projection" must _metadata("E1") {
      "derives the CML artifact name from canonical project identity" in {
        _with_temp_dir("cozy-car-cml-canonical-identity") { dir =>
          Given("a canonical CAR project and its Maven-artifact-named CML source")
          _write_canonical_project(dir)
          val canonical =
            _write_cml(dir.resolve("src/main/cozy/textus-sample.cml"), "Canonical")

          When("Cozy resolves the CAR publication CML source")
          val resolved = _resolved(CarCmlSourceResolver.resolve(dir, "textus-sample"))

          Then("the shared canonical artifact projection selects the source")
          resolved.source shouldBe canonical.toAbsolutePath.normalize()
        }
      }
      }

      "E2 explicit project source precedence" must _metadata("E2") {
      "prefers an explicit project CML source over the canonical source" in {
        _with_temp_dir("cozy-car-cml-explicit") { dir =>
          Given("a CAR project with explicit and canonical CML sources")
          _write_canonical_project(dir, Some("src/main/cozy/ai.cml"))
          _write_cml(dir.resolve("src/main/cozy/textus-sample.cml"), "Canonical")
          val explicit =
            _write_cml(dir.resolve("src/main/cozy/ai.cml"), "Explicit")

          When("Cozy resolves the CAR publication CML source")
          val resolved = _resolved(CarCmlSourceResolver.resolve(dir, "textus-sample"))

          Then("the explicit project-relative CML source is selected")
          resolved.source shouldBe explicit.toAbsolutePath.normalize()
          resolved.projectRelativePath shouldBe "src/main/cozy/ai.cml"
        }
      }
      }

      "E3 canonical source precedence" must _metadata("E3") {
      "prefers the canonical source when more than one CML source exists" in {
        _with_temp_dir("cozy-car-cml-canonical") { dir =>
          Given("a CAR project with canonical and supplementary CML sources")
          _write_canonical_project(dir)
          val canonical =
            _write_cml(dir.resolve("src/main/cozy/textus-sample.cml"), "Canonical")
          _write_cml(dir.resolve("src/main/cozy/support.cml"), "Support")

          When("Cozy resolves the CAR publication CML source")
          val resolved = _resolved(CarCmlSourceResolver.resolve(dir, "textus-sample"))

          Then("the artifact-named canonical source is selected")
          resolved.source shouldBe canonical.toAbsolutePath.normalize()
        }
      }
      }

      "E4 noncanonical implicit source" must _metadata("E4") {
      "rejects the only noncanonical CML source without an explicit declaration" in {
        _with_temp_dir("cozy-car-cml-single") { dir =>
          Given("a CAR project with one noncanonical CML source")
          _write_canonical_project(dir)
          val source =
            _write_cml(dir.resolve("src/main/cozy/ai.cml"), "TextusAi")

          When("Cozy resolves the CAR publication CML source")
          val issue = _issue(CarCmlSourceResolver.resolve(dir, "textus-sample"))

          Then("the source is rejected with an explicit selection instruction")
          issue.code shouldBe "car.cml.source.noncanonical"
          issue.path shouldBe source.toAbsolutePath.normalize()
          issue.message should include("src/main/cozy/ai.cml")
          issue.message should include("set cml.source")
        }
      }
      }
    }

    "reject invalid source declarations" which {
      "E5 missing implicit source" must _metadata("E5") {
      "reports a missing implicit source" in {
        _with_temp_dir("cozy-car-cml-missing") { dir =>
          Given("a CAR project with no CML source")
          _write_canonical_project(dir)

          When("Cozy resolves the CAR publication CML source")
          val issue = _issue(CarCmlSourceResolver.resolve(dir, "textus-sample"))

          Then("the missing source is reported deterministically")
          issue.code shouldBe "car.cml.source.missing"
        }
      }
      }

      "E6 ambiguous noncanonical sources" must _metadata("E6") {
      "reports ambiguous noncanonical sources" in {
        _with_temp_dir("cozy-car-cml-ambiguous") { dir =>
          Given("a CAR project with two noncanonical CML sources")
          _write_canonical_project(dir)
          _write_cml(dir.resolve("src/main/cozy/a.cml"), "A")
          _write_cml(dir.resolve("src/main/cozy/b.cml"), "B")

          When("Cozy resolves the CAR publication CML source")
          val issue = _issue(CarCmlSourceResolver.resolve(dir, "textus-sample"))

          Then("the caller is directed to select the source explicitly")
          issue.code shouldBe "car.cml.source.ambiguous"
          issue.message should include("src/main/cozy/a.cml")
          issue.message should include("src/main/cozy/b.cml")
        }
      }
      }

      "E7 traversal outside the project" must _metadata("E7") {
      "rejects a source that escapes the project" in {
        _with_temp_dir("cozy-car-cml-traversal") { dir =>
          Given(
            "a CAR project whose explicit source traverses outside the project"
          )
          _write_canonical_project(dir, Some("../outside.cml"))

          When("Cozy resolves the explicit source")
          val issue = _issue(CarCmlSourceResolver.resolve(dir, "textus-sample"))

          Then("the source is rejected as outside the project")
          issue.code shouldBe "car.cml.source.outside_project"
        }
      }
      }

      "E8 absolute source rejection" must _metadata("E8") {
      "rejects an absolute source" in {
        _with_temp_dir("cozy-car-cml-absolute") { dir =>
          Given("a CAR project whose explicit source is absolute")
          val source =
            _write_cml(dir.resolve("src/main/cozy/textus-sample.cml"), "Sample")
          _write_canonical_project(dir, Some(source.toString))

          When("Cozy resolves the explicit source")
          val issue = _issue(CarCmlSourceResolver.resolve(dir, "textus-sample"))

          Then("the source is rejected as outside the project contract")
          issue.code shouldBe "car.cml.source.outside_project"
        }
      }
      }

      "E9 non-CML source rejection" must _metadata("E9") {
      "rejects a source with a non-CML extension" in {
        _with_temp_dir("cozy-car-cml-extension") { dir =>
          Given("a CAR project whose explicit source is not a CML file")
          _write_canonical_project(dir, Some("src/main/cozy/textus-sample.dox"))

          When("Cozy resolves the explicit source")
          val issue = _issue(CarCmlSourceResolver.resolve(dir, "textus-sample"))

          Then("the source is rejected without implicit fallback")
          issue.code shouldBe "car.cml.source.invalid_extension"
        }
      }
      }

      "E10 missing explicit source" must _metadata("E10") {
      "rejects a missing explicit source without fallback" in {
        _with_temp_dir("cozy-car-cml-explicit-missing") { dir =>
          Given(
            "a CAR project with a missing explicit source and a valid canonical source"
          )
          _write_canonical_project(dir, Some("src/main/cozy/missing.cml"))
          _write_cml(dir.resolve("src/main/cozy/textus-sample.cml"), "Sample")

          When("Cozy resolves the explicit source")
          val issue = _issue(CarCmlSourceResolver.resolve(dir, "textus-sample"))

          Then("the configured source failure remains authoritative")
          issue.code shouldBe "car.cml.source.not_found"
        }
      }
      }

      "E14 canonical identity admission" must _metadata("E14") {
      "rejects a legacy project name without canonical component identity" in {
        _with_temp_dir("cozy-car-cml-legacy-identity") { dir =>
          Given("a project that declares only its retired legacy name")
          _write(dir.resolve("project.yaml"), "project:\n  name: legacy-sample\n")

          When("Cozy resolves the project CML identity")
          val issue = _issue(CarCmlSourceResolver.resolve(dir, "textus-sample"))

          Then("the missing canonical identity is reported deterministically")
          issue.code shouldBe "car.cml.artifact_id.missing"
          issue.message should include("project.namespace")
          issue.message should include("project.id")
          issue.message should include("project.component.version")
        }
      }
      }

      "E15 partial canonical identity admission" must _metadata("E15") {
      "rejects a partial canonical component identity" in {
        _with_temp_dir("cozy-car-cml-partial-identity") { dir =>
          Given("a project with namespace and id but no component version")
          _write(
            dir.resolve("project.yaml"),
            "project:\n  namespace: org.example.textus\n  id: Sample\n"
          )

          When("Cozy admits the project artifact identity")
          val issue = CarCmlSourceResolver.projectArtifactId(dir).fold(
            identity,
            artifactid => fail(s"Expected canonical identity rejection but admitted $artifactid")
          )

          Then("the partial canonical identity remains invalid")
          issue.code shouldBe "car.cml.artifact_id.invalid"
          issue.message should include("project.namespace")
          issue.message should include("project.id")
          issue.message should include("project.component.version")
        }
      }
      }
    }

    "protect the publication lifecycle" which {
      "E11 explicit source publication evidence" must _metadata("E11") {
      "returns a noncanonical explicit source to the publication boundary" in {
        _with_temp_dir("cozy-car-cml-publish-explicit") { dir =>
          Given(
            "a target-isolated canonical CAR project, prebuilt archive, and explicit noncanonical CML source"
          )
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          _write_canonical_project(projectdir, Some("src/main/cozy/ai.cml"))
          val source = _write_cml(projectdir.resolve("src/main/cozy/ai.cml"), "TextusAi")
          _write_cml(projectdir.resolve("src/main/cozy/textus-sample.cml"), "Canonical")
          val archive = _canonical_car(dir.resolve("textus-sample.car"))

          When("the actual Cozy publisher traverses RepositoryArtifactPublisher")
          CozyCarPublisher.publish(List(
            projectdir.toString,
            "--warehouse", warehouse.toString,
            "--car", archive.toString,
            "--name", "textus-sample",
            "--version", "0.1.0",
            "--published-at", "2026-08-08T00:00:00Z"
          ))

          Then(
            "the published CML sidecar contains the explicitly selected source"
          )
          Files.readString(
            warehouse.resolve("repository/catalog/car/org/example/textus/textus-sample.cml"),
            StandardCharsets.UTF_8
          ) shouldBe Files.readString(source, StandardCharsets.UTF_8)
        }
      }
      }

      "E12 ambiguous-source publication preflight" must _metadata("E12") {
      "fails before warehouse mutation when CML resolution is ambiguous" in {
        _with_temp_dir("cozy-car-cml-publish-ambiguous") { dir =>
          Given("a target-isolated CAR project with ambiguous CML sources and a canonical prebuilt CAR")
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          _write_canonical_project(projectdir)
          _write_cml(projectdir.resolve("src/main/cozy/a.cml"), "A")
          _write_cml(projectdir.resolve("src/main/cozy/b.cml"), "B")
          val archive = _canonical_car(dir.resolve("textus-sample.car"))
          _write(warehouse.resolve(".cozy/locks/component-repository-index.lock"), "")
          val before = _tree_snapshot(warehouse)

          When("the actual Cozy publisher preflights CML sidecars before staging publication output")
          val thrown = intercept[Throwable] {
            CozyCarPublisher.publish(List(
              projectdir.toString,
              "--warehouse", warehouse.toString,
              "--car", archive.toString,
              "--name", "textus-sample",
              "--version", "0.1.0",
              "--published-at", "2026-08-08T00:00:00Z"
            ))
          }

          Then("the resolver failure is reported before any warehouse path or byte changes")
          Option(thrown.getMessage).getOrElse("") should include("car.cml.source.ambiguous")
          _tree_snapshot(warehouse) shouldBe before
        }
      }
      }

      "E13 publication identity preflight" must _metadata("E13") {
      "fails before warehouse mutation when publication name differs from project identity" in {
        _with_temp_dir("cozy-car-cml-publish-name-mismatch") { dir =>
          Given(
            "a CAR project and publication request with different artifact identities"
          )
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          _write_canonical_project(projectdir)
          _write_cml(projectdir.resolve("src/main/cozy/textus-sample.cml"), "Sample")

          When("Cozy resolves the project under a different artifact name")
          val issue = _issue(CarCmlSourceResolver.resolve(projectdir, "other"))

          Then("the identity mismatch is reported before warehouse mutation")
          issue.code shouldBe "car.cml.artifact_id.mismatch"
          Files.exists(warehouse.resolve("repository/car/other")) shouldBe false
          Files.exists(
            warehouse.resolve("repository/catalog/car/other.yaml")
          ) shouldBe false
        }
      }
      }
    }
  }

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

  private def _write_canonical_project(
      dir: Path,
      cmlsource: Option[String] = None
  ): Path = {
    val cml = cmlsource.map(source => s"cml:\n  source: ${source}\n").getOrElse("")
    _write(
      dir.resolve("project.yaml"),
      s"""project:
         |  namespace: org.example.textus
         |  id: Sample
         |  component:
         |    version: 0.1.0
         |${cml}""".stripMargin
    )
  }

  private def _write_cml(path: Path, name: String): Path =
    _write(path, s"# COMPONENT\n\n## ${name}\n")

  private def _canonical_car(path: Path): Path = {
    val entries = Vector(
      "component-descriptor.json" ->
        """{"schemaVersion":3,"component":{"namespace":"org.example.textus","id":"Sample","version":"0.1.0"}}""",
      "abi-manifest.json" ->
        """{"format":"cozy.car.abi-manifest.v2","component":{"namespace":"org.example.textus","id":"Sample","version":"0.1.0"},"abi":{"version":1,"exports":{"components":[{"namespace":"org.example.textus","id":"Sample"}]},"dependencies":[]}}""",
      "component/main.jar" -> "fixture"
    )
    val output = new ZipOutputStream(Files.newOutputStream(path))
    try entries.foreach { case (name, content) =>
      output.putNextEntry(new ZipEntry(name))
      output.write(content.getBytes(StandardCharsets.UTF_8))
      output.closeEntry()
    } finally output.close()
    path
  }

  private def _tree_snapshot(root: Path): Map[String, Option[Vector[Byte]]] = {
    val stream = Files.walk(root)
    try stream.iterator().asScala.toVector.map { path =>
      val key = root.relativize(path).iterator().asScala.map(_.toString).mkString("/")
      key -> (if (Files.isRegularFile(path))
        Some(Files.readAllBytes(path).toVector)
      else
        None)
    }.toMap
    finally stream.close()
  }

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    path.toAbsolutePath.normalize()
  }

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val workroot = Path.of("target/cozy-test/work/car-cml-source-resolver-spec").toAbsolutePath.normalize()
    Files.createDirectories(workroot)
    val dir = Files.createTempDirectory(workroot, s"$prefix-")
    try body(dir)
    finally {
      _delete(dir)
      Files.deleteIfExists(workroot)
    }
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
