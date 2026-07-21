package cozy.lint

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import cozy.{Cozy, CozyCliPreflight}

/*
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyRepositoryLintSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Component Repository lint" should {
    "provide the command surface" which {
      "documents and preflights JSON repository lint" in {
        Given("the Cozy CLI help and a JSON repository lint command")

        When("the command surface is inspected")
        val command = CozyCliPreflight.parse(Array("lint", "repository", ".", "--format", "json"))

        Then("the canonical command and machine-output route are available")
        Cozy.helpText should include("lint repository <repository-root>")
        command.jsonLintCommand.map(_._1) shouldBe Some("repository")
      }
    }

    "validate the public discovery boundary" which {
      "accepts a consistent local index without network access" in {
        _with_temp_dir("cozy-repository-lint-valid") { dir =>
          Given("a repository index and matching detailed CAR catalog")
          _write(dir.resolve("repository/catalog/car/sample.yaml"), _catalog("1.0.0"))
          _write(dir.resolve("repository/catalog/index.json"), _index("1.0.0"))

          When("Cozy lints the local repository root")
          val findings = CozyRepositoryLint.lint(dir)

          Then("the repository has no findings")
          findings shouldBe empty
          CozyRepositoryLint.toText(dir, findings) should include("OK repository.no-findings")
        }
      }

      "reports a stale selector deterministically" in {
        _with_temp_dir("cozy-repository-lint-stale") { dir =>
          Given("an index selector that differs from its detailed catalog")
          _write(dir.resolve("repository/catalog/car/sample.yaml"), _catalog("1.0.0"))
          _write(dir.resolve("repository/catalog/index.json"), _index("0.9.0"))

          When("Cozy lints the local repository root")
          val findings = CozyRepositoryLint.lint(dir)

          Then("the mismatch is a repository index failure")
          findings.map(_.code) shouldBe Vector("repository.index.invalid")
          findings.head.message should include("selector is stale")
        }
      }
    }
  }

  private def _index(recommended: String): String =
    s"""{
       |  "schemaVersion": "cncf.component-repository-index.v1",
       |  "generatedAt": "2026-07-21T00:00:00Z",
       |  "artifacts": [{
       |    "kind": "car",
       |    "artifactId": "sample",
       |    "catalog": "car/sample.yaml",
       |    "status": "active",
       |    "recommended": "$recommended",
       |    "latestStable": "1.0.0"
       |  }]
       |}
       |""".stripMargin

  private def _catalog(recommended: String): String =
    s"""schemaVersion: 1
       |kind: car
       |artifactId: sample
       |recommended: $recommended
       |latestStable: 1.0.0
       |status: active
       |aliases: []
       |versions:
       |  - version: 1.0.0
       |    channel: stable
       |    status: active
       |    file: repository/car/sample/1.0.0/sample-1.0.0.car
       |""".stripMargin

  private def _write(path: Path, text: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, text.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val dir = Files.createTempDirectory(prefix)
    try body(dir)
    finally {
      val stream = Files.walk(dir)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
      finally stream.close()
    }
  }
}
