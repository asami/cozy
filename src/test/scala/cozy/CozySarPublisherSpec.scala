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
import org.scalacheck.{Gen, Prop, Test}

/*
 * @since   May. 20, 2026
 *  version Jul. 21, 2026
 *  version Aug. 28, 2026
 * @version Oct. 10, 2026
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

      When("Cozy publishes the release without an explicit recommendation flag")
      CozySarPublisher.publish(List(
        projectdir.toString,
        "--warehouse", warehouse.toString,
        "--name", "sample-application",
        "--version", "0.1.0",
        "--sar", sar.toString
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

      val index = ComponentRepositoryIndex.load(warehouse.resolve("repository/catalog/index.json"))
      index.artifacts.map(_.identity) shouldBe Vector(("sar", "", "sample-application"))
      index.artifacts.head.catalog shouldBe "sar/sample-application.yaml"
    }
  }

  "public SAR publication" which {
    "reject a symbolic or directory metadata output leaf" in {
      Vector("symbolic", "directory").foreach { kind =>
        _with_temp_dir(s"cozy-publish-sar-metadata-$kind") { dir =>
          Given(s"a prebuilt SAR release with a $kind selected warehouse metadata leaf")
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          val sar = _write(dir.resolve("input/sample.sar"), "sar-body")
          _write_project_yaml(projectdir, "sample-application")
          val metadata = warehouse.resolve("repository/sar/sample-application/maven-metadata.xml")
          Files.createDirectories(metadata.getParent)
          if (kind == "symbolic") {
            val outside = _write(dir.resolve("outside-warehouse/selected-file.xml"), "task-owned file")
            Files.createSymbolicLink(metadata, outside)
          } else {
            Files.createDirectory(metadata)
          }

          When("the SAR publisher reaches the selected metadata destination")
          val error = intercept[Throwable] {
            CozySarPublisher.publish(List(
              projectdir.toString,
              "--warehouse", warehouse.toString,
              "--name", "sample-application",
              "--version", "0.1.0",
              "--sar", sar.toString
            ))
          }

          Then("publication rejects the nonregular metadata output with its selected path diagnostic")
          error.getMessage should include("Repository publication output")
          error.getMessage should include("symbolic link or nonregular leaf")
          error.getMessage should include(metadata.toString)
        }
      }
    }

    "publishes varied actual payloads with schema1 release metadata" in {
      Given("a generator of varied SAR payloads and public publication scenarios")
      val payloads: Gen[Array[Byte]] = Gen.choose(1, 200).flatMap(size => Gen.listOfN(size, Gen.choose(-128, 127))).map { values =>
        ("SAR-\u65e5\u672c\u8a9e-".getBytes(StandardCharsets.UTF_8).toVector ++ values.map(_.toByte)).toArray
      }
      val property = Prop.forAll(payloads) { payload =>
        _with_temp_dir("sar-public-byte-property") { dir =>
          Given("a SAR project and a varied actual payload containing non-ASCII bytes")
          val project = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          _write_project_yaml(project, "sample-application")
          val input = dir.resolve("input/sample.sar")
          Files.createDirectories(input.getParent)
          Files.write(input, payload)
          When("the public SAR publisher installs the release")
          CozySarPublisher.publish(List(project.toString, "--warehouse", warehouse.toString,
            "--name", "sample-application", "--version", "0.1.0", "--sar", input.toString))
          val archive = warehouse.resolve("repository/sar/sample-application/0.1.0/sample-application-0.1.0.sar")
          val catalog = RepositoryArtifactCatalog.load(warehouse.resolve("repository/catalog/sar/sample-application.yaml"))
          val indexpath = warehouse.resolve(ComponentRepositoryIndex.PUBLIC_PATH)
          val admitted = ComponentRepositoryIndex.validateCatalogs(ComponentRepositoryIndex.load(indexpath), indexpath)
          Then("the published payload, input, SAR schema, and discovery identity are preserved")

          Files.readAllBytes(archive).toVector shouldBe payload.toVector
          Files.readAllBytes(input).toVector shouldBe payload.toVector
          catalog.schemaVersion shouldBe "1"
          catalog.namespace shouldBe empty
          catalog.id shouldBe empty
          admitted.artifacts.map(_.identity) shouldBe Vector(("sar", "", "sample-application"))
          admitted.artifacts.head.namespace shouldBe empty
          admitted.artifacts.head.id shouldBe empty
          true
        }
      }
      When("ScalaCheck exercises at least fifty successful public publication trials")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)
      Then("every trial preserves actual payload publication and schema1 release metadata")
      result.passed shouldBe true
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

  "isolate local snapshot publication from the project source catalog" in {
    _with_temp_dir("cozy-publish-sar-local-catalog") { dir =>
      Given("a source catalog containing an unavailable stable SAR release")
      val projectdir = dir.resolve("project")
      val warehouse = dir.resolve("warehouse")
      val sar = _write(dir.resolve("input/sample.sar"), "local-snapshot")
      _write_project_yaml(projectdir, "sample-application")
      val sourcecatalogpath = projectdir.resolve(
        "src/main/catalog/sar/sample-application.yaml"
      )
      _write(
        sourcecatalogpath,
        """schemaVersion: 1
          |kind: sar
          |artifactId: sample-application
          |recommended: 0.1.0
          |latestStable: 0.1.0
          |status: active
          |aliases: []
          |versions:
          |  - version: 0.1.0
          |    channel: stable
          |    status: active
          |    file: repository/sar/sample-application/0.1.0/sample-application-0.1.0.sar
          |""".stripMargin
      )
      val sourcebytes = Files.readString(sourcecatalogpath, StandardCharsets.UTF_8)

      When("Cozy publishes a local SAR snapshot")
      CozySarPublisher.publish(List(
        projectdir.toString,
        "--warehouse", warehouse.toString,
        "--name", "sample-application",
        "--version", "0.1.1-SNAPSHOT",
        "--sar", sar.toString,
        "--local-publication"
      ))

      Then("the source catalog is untouched and the local index retains no unavailable history")
      Files.readString(sourcecatalogpath, StandardCharsets.UTF_8) shouldBe sourcebytes
      Files.isRegularFile(
        warehouse.resolve(
          "repository/sar/sample-application/0.1.1-SNAPSHOT/sample-application-0.1.1-SNAPSHOT.sar"
        )
      ) shouldBe true
      Files.exists(warehouse.resolve("repository/catalog/sar/sample-application.yaml")) shouldBe false
      val indexpath = warehouse.resolve(ComponentRepositoryIndex.PUBLIC_PATH)
      ComponentRepositoryIndex.validateCatalogs(
        ComponentRepositoryIndex.load(indexpath),
        indexpath
      ).artifacts shouldBe empty
    }
  }

  "remove existing snapshot entries from a catalog" in {
    _with_temp_dir("cozy-publish-sar-snapshot-cleanup") { dir =>
      Given("a release catalog contaminated by an older snapshot entry")
      val projectdir = dir.resolve("project")
      val warehouse = dir.resolve("warehouse")
      val sar = _write(dir.resolve("input/sample.sar"), "snapshot-sar-body")
      _write_project_yaml(projectdir, "sample-application")
      _materialize_release(warehouse, "0.1.0", "retained-release")
      _write(
        projectdir.resolve("src/main/catalog/sar/sample-application.yaml"),
        s"""schemaVersion: 1
          |kind: sar
          |artifactId: sample-application
          |recommended: 0.1.0
          |latestStable: 0.1.0
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
      sourcecatalog.recommended shouldBe Some("0.1.0")
      sourcecatalog.latestStable shouldBe Some("0.1.0")
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
      _materialize_release(warehouse, "0.0.9", "deprecated-release")
      _materialize_release(warehouse, "0.1.0", "current-release")
      _write(
        projectdir.resolve("src/main/catalog/sar/sample-application.yaml"),
        s"""schemaVersion: 1
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
          |""".stripMargin
      )

      When("Cozy republishes the current release without retaining the older recommendation")
      CozySarPublisher.publish(List(
        projectdir.toString,
        "--warehouse", warehouse.toString,
        "--name", "sample-application",
        "--version", "0.1.0",
        "--sar", sar.toString
      ))

      Then("history is retained while the current stable release becomes recommended")
      val catalog = RepositoryArtifactCatalog.load(projectdir.resolve("src/main/catalog/sar/sample-application.yaml"))
      catalog.versions.map(_.version) shouldBe Vector("0.0.9", "0.1.0")
      catalog.versions.find(_.version == "0.0.9").flatMap(_.status) shouldBe Some("deprecated")
      catalog.recommended shouldBe Some("0.1.0")
      catalog.latestStable shouldBe Some("0.1.0")
      catalog.aliases shouldBe Vector("sample-old")
      val metadata = Files.readString(warehouse.resolve("repository/sar/sample-application/maven-metadata.xml"))
      metadata should include("<latest>0.1.0</latest>")
      metadata should include("<release>0.1.0</release>")
      metadata should include("<version>0.0.9</version>")
      metadata should include("<version>0.1.0</version>")
    }
  }

  "retain an existing recommendation on explicit request" in {
    _with_temp_dir("cozy-publish-sar-keep-recommended") { dir =>
      Given("a SAR catalog with an older valid stable recommendation")
      val projectdir = dir.resolve("project")
      val warehouse = dir.resolve("warehouse")
      val sar = _write(dir.resolve("input/sample.sar"), "new-sar-body")
      _write_project_yaml(projectdir, "sample-application")
      _materialize_release(warehouse, "0.0.9", "deprecated-release")
      _materialize_release(warehouse, "0.1.0", "current-release")
      _write(
        projectdir.resolve("src/main/catalog/sar/sample-application.yaml"),
        s"""schemaVersion: 1
          |kind: sar
          |artifactId: sample-application
          |recommended: 0.0.9
          |latestStable: 0.1.0
          |status: active
          |aliases: []
          |versions:
          |  - version: 0.0.9
          |    channel: stable
          |    status: deprecated
          |    file: repository/sar/sample-application/0.0.9/sample-application-0.0.9.sar
          |  - version: 0.1.0
          |    channel: stable
          |    status: active
          |    file: repository/sar/sample-application/0.1.0/sample-application-0.1.0.sar
          |""".stripMargin
      )

      When("Cozy republishes the stable SAR with --keep-recommended")
      CozySarPublisher.publish(List(
        projectdir.toString,
        "--warehouse", warehouse.toString,
        "--name", "sample-application",
        "--version", "0.1.0",
        "--sar", sar.toString,
        "--keep-recommended"
      ))

      Then("the older stable recommendation remains while latestStable is current")
      val sourcecatalog = RepositoryArtifactCatalog.load(
        projectdir.resolve("src/main/catalog/sar/sample-application.yaml")
      )
      val publiccatalog = RepositoryArtifactCatalog.load(
        warehouse.resolve("repository/catalog/sar/sample-application.yaml")
      )
      sourcecatalog shouldBe publiccatalog
      sourcecatalog.recommended shouldBe Some("0.0.9")
      sourcecatalog.latestStable shouldBe Some("0.1.0")
      val metadata = Files.readString(
        warehouse.resolve("repository/sar/sample-application/maven-metadata.xml")
      )
      metadata should include("<latest>0.0.9</latest>")
      metadata should include("<release>0.1.0</release>")
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

  private def _materialize_release(warehouse: Path, version: String, text: String): Path =
    _write(warehouse.resolve(s"repository/sar/sample-application/$version/sample-application-$version.sar"), text)

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
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
