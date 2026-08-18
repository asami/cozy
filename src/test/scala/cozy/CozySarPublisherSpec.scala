package cozy

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.ZipFile

import scala.collection.JavaConverters._

import cozy.archive.ComponentRepositoryIndex

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 20, 2026
 *  version Jul. 21, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
class CozySarPublisherSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy SAR publication" should {
  "dispatch the publish-sar command and document it in help" in {
    _with_temp_dir("cozy-publish-sar-cli") { dir =>
      Given("a SAR project and a prebuilt SAR archive")
      val projectdir = dir.resolve("project")
      val warehouse = dir.resolve("warehouse")
      val sar = _write(dir.resolve("input/sample.sar"), "sar-body")
      _write_project_yaml(projectdir, "sample-application")

      When("Cozy publishes the SAR through the CLI")
      Cozy.main(Array(
        "publish-sar",
        projectdir.toString,
        "--warehouse", warehouse.toString,
        "--name", "sample-application",
        "--version", "0.1.0",
        "--sar", sar.toString
      ))

      Then("the SAR is stored")
      Files.isRegularFile(warehouse.resolve("repository/sar/sample-application/0.1.0/sample-application-0.1.0.sar")) shouldBe true

      When("Cozy renders command help")
      val out = new ByteArrayOutputStream()
      Console.withOut(new PrintStream(out, true, StandardCharsets.UTF_8.name())) {
        Cozy.main(Array("--help"))
      }
      val help = out.toString(StandardCharsets.UTF_8.name())
      Then("help documents SAR publication")
      help should include("publish-sar <project-dir>")
      help should include("Publish a SAR archive and SAR catalog")
    }
  }

  "publish a prebuilt SAR and create source and warehouse catalogs" in {
    _with_temp_dir("cozy-publish-sar-prebuilt") { dir =>
      Given("a SAR project and a release archive")
      val projectdir = dir.resolve("project")
      val warehouse = dir.resolve("warehouse")
      val sar = _write(dir.resolve("input/sample.sar"), "sar-body")
      _write_project_yaml(projectdir, "sample-application")

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
      Files.readString(target) shouldBe "sar-body"
      val metadata = Files.readString(warehouse.resolve("repository/sar/sample-application/maven-metadata.xml"))
      metadata should include("<groupId>org.simplemodeling.repository.sar</groupId>")
      metadata should include("<artifactId>sample-application</artifactId>")
      metadata should include("<latest>0.1.0</latest>")
      metadata should include("<release>0.1.0</release>")
      metadata should include("<version>0.1.0</version>")
      Files.exists(projectdir.resolve("src/main/catalog/sar/maven-metadata.xml")) shouldBe false
      val sourcecatalog = RepositoryArtifactCatalog.load(projectdir.resolve("src/main/catalog/sar/sample-application.yaml"))
      val publiccatalog = RepositoryArtifactCatalog.load(warehouse.resolve("repository/catalog/sar/sample-application.yaml"))
      sourcecatalog shouldBe publiccatalog
      publiccatalog.kind shouldBe "sar"
      publiccatalog.recommended shouldBe Some("0.1.0")
      publiccatalog.latestStable shouldBe Some("0.1.0")
      publiccatalog.versions.head.runtime shouldBe empty
      publiccatalog.versions.head.file shouldBe Some("repository/sar/sample-application/0.1.0/sample-application-0.1.0.sar")
      publiccatalog.versions.head.checksumSha256 should not be empty
      val index = ComponentRepositoryIndex.load(warehouse.resolve("repository/catalog/index.json"))
      index.artifacts.map(_.identity) shouldBe Vector(("sar", "", "sample-application"))
      index.artifacts.head.catalog shouldBe "sar/sample-application.yaml"
    }
  }

  "build a snapshot SAR without adding it to a release catalog" in {
    _with_temp_dir("cozy-publish-sar-build") { dir =>
      Given("a SAR source tree and a snapshot version")
      val projectdir = dir.resolve("project")
      val warehouse = dir.resolve("warehouse")
      val sourcedir = dir.resolve("src")
      val extension = _write(dir.resolve("ext/adapter.jar"), "adapter")
      _write_project_yaml(projectdir, "sample-application")
      _write(sourcedir.resolve("subsystem-descriptor.yaml"), "subsystem: sample\n")
      _write(sourcedir.resolve("ignored.txt"), "ignored")

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
      entries should contain("subsystem-descriptor.yaml")
      entries should contain("extension/adapter.jar")
      entries should not contain "ignored.txt"
      val staging = projectdir.resolve("target/cozy-publish-sar")
      Files.isDirectory(staging) shouldBe true
      val stagedfiles = Files.list(staging)
      try stagedfiles.iterator().asScala.toVector shouldBe empty
      finally stagedfiles.close()
      Files.exists(projectdir.resolve("src/main/catalog/sar/sample-application.yaml")) shouldBe false
      Files.exists(warehouse.resolve("repository/catalog/sar/sample-application.yaml")) shouldBe false
      Files.exists(warehouse.resolve("repository/sar/sample-application/maven-metadata.xml")) shouldBe false
    }
  }

  "remove existing snapshot entries from a catalog" in {
    _with_temp_dir("cozy-publish-sar-snapshot-cleanup") { dir =>
      Given("a release catalog contaminated by an older snapshot entry")
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
      sourcecatalog shouldBe publiccatalog
      sourcecatalog.latestSnapshot shouldBe empty
      sourcecatalog.versions.map(_.version) shouldBe Vector("0.1.0")
      sourcecatalog.versions.forall(_.channel != Some("snapshot")) shouldBe true
    }
  }

  "preserve existing catalog versions while replacing the same release" in {
    _with_temp_dir("cozy-publish-sar-merge") { dir =>
      Given("a SAR catalog with deprecated and current release versions")
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
      catalog.versions.map(_.version) shouldBe Vector("0.0.9", "0.1.0")
      catalog.versions.find(_.version == "0.0.9").flatMap(_.status) shouldBe Some("deprecated")
      catalog.versions.find(_.version == "0.1.0").flatMap(_.checksumSha256) should not be Some("old")
      catalog.recommended shouldBe Some("0.0.9")
      catalog.latestStable shouldBe Some("0.1.0")
      catalog.aliases shouldBe Vector("sample-old")
      val metadata = Files.readString(warehouse.resolve("repository/sar/sample-application/maven-metadata.xml"))
      metadata should include("<latest>0.0.9</latest>")
      metadata should include("<release>0.1.0</release>")
      metadata should include("<version>0.0.9</version>")
      metadata should include("<version>0.1.0</version>")
    }
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
    val workroot = Path.of("target/cozy-test/work/cozy-sar-publisher-spec").toAbsolutePath.normalize()
    Files.createDirectories(workroot)
    val dir = Files.createTempDirectory(workroot, s"$prefix-")
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
