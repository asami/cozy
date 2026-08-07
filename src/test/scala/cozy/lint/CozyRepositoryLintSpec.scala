package cozy.lint

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import cozy.{Cozy, CozyCliPreflight}

/*
 * @since   Jul. 21, 2026
 * @version Aug.  7, 2026
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
        Cozy._help_text should include("lint repository <repository-root>")
        command.jsonLintCommand.map(_._1) shouldBe Some("repository")
      }
    }

    "validate the public discovery boundary" which {
      "accepts a consistent local index without network access" in {
        _with_temp_dir("cozy-repository-lint-valid") { dir =>
          Given("a repository index and matching detailed CAR catalog")
          val digest = _write_archive(dir)
          _write(dir.resolve("repository/catalog/car/org/sample/sample-component.yaml"), _catalog("1.0.0", digest))
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
          val digest = _write_archive(dir)
          _write(dir.resolve("repository/catalog/car/org/sample/sample-component.yaml"), _catalog("1.0.0", digest))
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
       |  "schemaVersion": "cncf.component-repository-index.v2",
       |  "generatedAt": "2026-07-21T00:00:00Z",
       |  "artifacts": [{
       |    "kind": "car",
       |    "namespace": "org.sample",
       |    "id": "Component",
       |    "artifactId": "sample-component",
       |    "catalog": "car/org/sample/sample-component.yaml",
       |    "status": "active",
       |    "recommended": "$recommended",
       |    "latestStable": "1.0.0"
       |  }]
       |}
       |""".stripMargin

  private def _catalog(recommended: String, digest: String): String =
    s"""schemaVersion: 2
       |kind: car
       |namespace: org.sample
       |id: Component
       |artifactId: sample-component
       |recommended: $recommended
       |latestStable: 1.0.0
       |status: active
       |aliases: []
       |versions:
       |  - version: 1.0.0
       |    channel: stable
       |    status: active
       |    component: org.sample.Component
       |    file: repository/car/org/sample/sample-component/1.0.0/sample-component-1.0.0.car
       |    checksum:
       |      sha256: $digest
       |    integrityKey: org.sample:sample-component:1.0.0@sha256:$digest
       |""".stripMargin

  private def _write_archive(root: Path): String = {
    val archive = root.resolve("repository/car/org/sample/sample-component/1.0.0/sample-component-1.0.0.car")
    Option(archive.getParent).foreach(Files.createDirectories(_))
    val output = new ZipOutputStream(Files.newOutputStream(archive))
    try {
      Vector(
        "component-descriptor.json" -> """{"schemaVersion":3,"component":{"namespace":"org.sample","id":"Component","version":"1.0.0"}}""",
        "abi-manifest.json" -> """{"format":"cozy.car.abi-manifest.v2","component":{"namespace":"org.sample","id":"Component","version":"1.0.0"},"abi":{"version":1,"exports":{"components":[{"namespace":"org.sample","id":"Component"}],"operations":[],"entities":[]},"dependencies":[]}}""",
        "component/main.jar" -> "lint"
      ).foreach { case (entryname, value) =>
        output.putNextEntry(new ZipEntry(entryname))
        output.write(value.getBytes(StandardCharsets.UTF_8))
        output.closeEntry()
      }
    } finally output.close()
    val digest = _sha256(Files.readAllBytes(archive))
    _write(archive.resolveSibling(archive.getFileName.toString + ".sha256"), digest + "\n")
    digest
  }

  private def _sha256(bytes: Array[Byte]): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.digest(bytes).map(bytevalue => "%02x".format(bytevalue & 0xff)).mkString
  }

  private def _write(path: Path, text: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, text.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val workroot = Path.of("target/cozy-test/work/cozy-repository-lint-spec").toAbsolutePath.normalize()
    Files.createDirectories(workroot)
    val dir = Files.createTempDirectory(workroot, s"$prefix-")
    try body(dir)
    finally {
      val stream = Files.walk(dir)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
      finally stream.close()
    }
  }
}
