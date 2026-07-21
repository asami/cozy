package cozy

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.ZipFile

import scala.collection.JavaConverters._

import cozy.archive.ComponentRepositoryIndex

import org.scalatest.GivenWhenThen
import org.scalatest.funsuite.AnyFunSuite

/*
 * @since   May. 20, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
class CozySarPublisherSpec extends AnyFunSuite with GivenWhenThen {
  test("cozy publish-sar command dispatches publisher and help lists command") {
    _with_temp_dir("cozy-publish-sar-cli") { dir =>
      val projectdir = dir.resolve("project")
      val warehouse = dir.resolve("warehouse")
      val sar = _write(dir.resolve("input/sample.sar"), "sar-body")
      _write_project_yaml(projectdir, "sample-application")

      Given("a SAR project and a prebuilt SAR archive")

      When("Cozy publishes the SAR through the CLI")
      Cozy.main(Array(
        "publish-sar",
        projectdir.toString,
        "--warehouse", warehouse.toString,
        "--name", "sample-application",
        "--version", "0.1.0",
        "--sar", sar.toString
      ))

      Then("the SAR is stored and command help documents publication")
      assert(Files.isRegularFile(warehouse.resolve("repository/sar/sample-application/0.1.0/sample-application-0.1.0.sar")))

      val out = new ByteArrayOutputStream()
      Console.withOut(new PrintStream(out, true, StandardCharsets.UTF_8.name())) {
        Cozy.main(Array("--help"))
      }
      val help = out.toString(StandardCharsets.UTF_8.name())
      assert(help.contains("publish-sar <project-dir>"))
      assert(help.contains("Publish a SAR archive and SAR catalog"))
    }
  }

  test("publish-sar publishes a prebuilt SAR and creates source and warehouse catalogs") {
    _with_temp_dir("cozy-publish-sar-prebuilt") { dir =>
      val projectdir = dir.resolve("project")
      val warehouse = dir.resolve("warehouse")
      val sar = _write(dir.resolve("input/sample.sar"), "sar-body")
      _write_project_yaml(projectdir, "sample-application")

      Given("a SAR project and a release archive")

      When("Cozy publishes the release as recommended")
      CozySarPublisher.publish(List(
        projectdir.toString,
        "--warehouse", warehouse.toString,
        "--name", "sample-application",
        "--version", "0.1.0",
        "--sar", sar.toString,
        "--recommended"
      ))

      Then("the archive, detailed catalogs, Maven metadata, and discovery index are published")
      val target = warehouse.resolve("repository/sar/sample-application/0.1.0/sample-application-0.1.0.sar")
      assert(Files.readString(target) == "sar-body")
      val metadata = Files.readString(warehouse.resolve("repository/sar/sample-application/maven-metadata.xml"))
      assert(metadata.contains("<groupId>org.simplemodeling.repository.sar</groupId>"))
      assert(metadata.contains("<artifactId>sample-application</artifactId>"))
      assert(metadata.contains("<latest>0.1.0</latest>"))
      assert(metadata.contains("<release>0.1.0</release>"))
      assert(metadata.contains("<version>0.1.0</version>"))
      assert(!Files.exists(projectdir.resolve("src/main/catalog/sar/maven-metadata.xml")))
      val sourcecatalog = RepositoryArtifactCatalog.load(projectdir.resolve("src/main/catalog/sar/sample-application.yaml"))
      val publiccatalog = RepositoryArtifactCatalog.load(warehouse.resolve("repository/catalog/sar/sample-application.yaml"))
      assert(sourcecatalog == publiccatalog)
      assert(publiccatalog.kind == "sar")
      assert(publiccatalog.recommended == Some("0.1.0"))
      assert(publiccatalog.latestStable == Some("0.1.0"))
      assert(publiccatalog.versions.head.runtime.isEmpty)
      assert(publiccatalog.versions.head.file == Some("repository/sar/sample-application/0.1.0/sample-application-0.1.0.sar"))
      assert(publiccatalog.versions.head.checksumSha256.nonEmpty)
      val index = ComponentRepositoryIndex.load(warehouse.resolve("repository/catalog/index.json"))
      assert(index.artifacts.map(_.identity) == Vector("sar" -> "sample-application"))
      assert(index.artifacts.head.catalog == "sar/sample-application.yaml")
    }
  }

  test("publish-sar builds a snapshot SAR without adding snapshot to catalog") {
    _with_temp_dir("cozy-publish-sar-build") { dir =>
      val projectdir = dir.resolve("project")
      val warehouse = dir.resolve("warehouse")
      val sourcedir = dir.resolve("src")
      val extension = _write(dir.resolve("ext/adapter.jar"), "adapter")
      _write_project_yaml(projectdir, "sample-application")
      _write(sourcedir.resolve("subsystem-descriptor.yaml"), "subsystem: sample\n")
      _write(sourcedir.resolve("ignored.txt"), "ignored")

      Given("a SAR source tree and a snapshot version")

      When("Cozy builds and publishes the snapshot")
      CozySarPublisher.publish(List(
        projectdir.toString,
        "--warehouse", warehouse.toString,
        "--name", "sample-application",
        "--version", "0.1.1-SNAPSHOT",
        "--source-dir", sourcedir.toString,
        "--source-files", "subsystem-descriptor.yaml",
        "--extension-jars", extension.toString
      ))

      Then("the archive is built without adding a snapshot release catalog entry")
      val target = warehouse.resolve("repository/sar/sample-application/0.1.1-SNAPSHOT/sample-application-0.1.1-SNAPSHOT.sar")
      val entries = _zip_entries(target)
      assert(entries.contains("subsystem-descriptor.yaml"))
      assert(entries.contains("extension/adapter.jar"))
      assert(!entries.contains("ignored.txt"))
      assert(!Files.exists(projectdir.resolve("src/main/catalog/sar/sample-application.yaml")))
      assert(!Files.exists(warehouse.resolve("repository/catalog/sar/sample-application.yaml")))
      assert(!Files.exists(warehouse.resolve("repository/sar/sample-application/maven-metadata.xml")))
    }
  }

  test("publish-sar removes existing snapshot entries from catalog") {
    _with_temp_dir("cozy-publish-sar-snapshot-cleanup") { dir =>
      val projectdir = dir.resolve("project")
      val warehouse = dir.resolve("warehouse")
      val sar = _write(dir.resolve("input/sample.sar"), "snapshot-sar-body")
      _write_project_yaml(projectdir, "sample-application")
      _write(
        projectdir.resolve("src/main/catalog/sar/sample-application.yaml"),
        """schemaVersion: 1
          |kind: sar
          |artifactId: sample-application
          |latestSnapshot: 0.1.1-SNAPSHOT
          |status: active
          |aliases: []
          |versions:
          |  - version: 0.1.0
          |    channel: stable
          |    status: active
          |    file: repository/sar/sample-application/0.1.0/sample-application-0.1.0.sar
          |  - version: 0.1.1-SNAPSHOT
          |    channel: snapshot
          |    status: active
          |    file: repository/sar/sample-application/0.1.1-SNAPSHOT/sample-application-0.1.1-SNAPSHOT.sar
          |""".stripMargin
      )

      Given("a release catalog contaminated by an older snapshot entry")

      When("Cozy publishes the next snapshot")
      CozySarPublisher.publish(List(
        projectdir.toString,
        "--warehouse", warehouse.toString,
        "--name", "sample-application",
        "--version", "0.1.2-SNAPSHOT",
        "--sar", sar.toString
      ))

      Then("source and public catalogs retain only release versions")
      val sourcecatalog = RepositoryArtifactCatalog.load(projectdir.resolve("src/main/catalog/sar/sample-application.yaml"))
      val publiccatalog = RepositoryArtifactCatalog.load(warehouse.resolve("repository/catalog/sar/sample-application.yaml"))
      assert(sourcecatalog == publiccatalog)
      assert(sourcecatalog.latestSnapshot.isEmpty)
      assert(sourcecatalog.versions.map(_.version) == Vector("0.1.0"))
      assert(sourcecatalog.versions.forall(_.channel != Some("snapshot")))
    }
  }

  test("publish-sar preserves existing catalog versions and replaces same version") {
    _with_temp_dir("cozy-publish-sar-merge") { dir =>
      val projectdir = dir.resolve("project")
      val warehouse = dir.resolve("warehouse")
      val sar = _write(dir.resolve("input/sample.sar"), "new-sar-body")
      _write_project_yaml(projectdir, "sample-application")
      _write(
        projectdir.resolve("src/main/catalog/sar/sample-application.yaml"),
        """schemaVersion: 1
          |kind: sar
          |artifactId: sample-application
          |recommended: 0.0.9
          |latestStable: 0.1.0
          |status: active
          |aliases:
          |  - sample-old
          |versions:
          |  - version: 0.0.9
          |    channel: stable
          |    status: deprecated
          |    file: repository/sar/sample-application/0.0.9/sample-application-0.0.9.sar
          |  - version: 0.1.0
          |    channel: stable
          |    status: active
          |    file: repository/sar/sample-application/0.1.0/sample-application-0.1.0.sar
          |    checksum:
          |      sha256: old
          |""".stripMargin
      )

      Given("a SAR catalog with deprecated and current release versions")

      When("Cozy republishes the current release")
      CozySarPublisher.publish(List(
        projectdir.toString,
        "--warehouse", warehouse.toString,
        "--name", "sample-application",
        "--version", "0.1.0",
        "--sar", sar.toString
      ))

      Then("history and selectors remain stable while current metadata is replaced")
      val catalog = RepositoryArtifactCatalog.load(projectdir.resolve("src/main/catalog/sar/sample-application.yaml"))
      assert(catalog.versions.map(_.version) == Vector("0.0.9", "0.1.0"))
      assert(catalog.versions.find(_.version == "0.0.9").flatMap(_.status) == Some("deprecated"))
      assert(catalog.versions.find(_.version == "0.1.0").flatMap(_.checksumSha256) != Some("old"))
      assert(catalog.recommended == Some("0.0.9"))
      assert(catalog.latestStable == Some("0.1.0"))
      assert(catalog.aliases == Vector("sample-old"))
      val metadata = Files.readString(warehouse.resolve("repository/sar/sample-application/maven-metadata.xml"))
      assert(metadata.contains("<latest>0.0.9</latest>"))
      assert(metadata.contains("<release>0.1.0</release>"))
      assert(metadata.contains("<version>0.0.9</version>"))
      assert(metadata.contains("<version>0.1.0</version>"))
    }
  }

  private def _write_project_yaml(projectdir: Path, name: String): Unit =
    _write(
      projectdir.resolve("project.yaml"),
      s"""project:
         |  name: $name
         |packaging:
         |  kind: sar
         |""".stripMargin
    )

  private def _zip_entries(path: Path): Vector[String] = {
    val zip = new ZipFile(path.toFile)
    try {
      zip.entries.asScala.map(_.getName).toVector.sorted
    } finally {
      zip.close()
    }
  }

  private def _with_temp_dir(prefix: String)(body: Path => Unit): Unit = {
    val dir = Files.createTempDirectory(prefix)
    try {
      body(dir)
    } finally {
      _delete_recursively(dir)
    }
  }

  private def _write(path: Path, text: String): Path = {
    Files.createDirectories(path.getParent)
    Files.write(path, text.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _delete_recursively(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.sorted(java.util.Comparator.reverseOrder()).forEach(p => Files.deleteIfExists(p))
      } finally {
        stream.close()
      }
    }
}
