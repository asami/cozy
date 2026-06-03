package cozy

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.ZipFile

import scala.collection.JavaConverters._

import org.scalatest.funsuite.AnyFunSuite

/*
 * @since   May. 20, 2026
 * @version Jun.  3, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyCarPublisherSpec extends AnyFunSuite {
  test("cozy publish-car command dispatches publisher and help lists command") {
    _with_temp_dir("cozy-publish-car-cli") { dir =>
      val projectdir = dir.resolve("project")
      val warehouse = dir.resolve("warehouse")
      val car = _write(dir.resolve("input/sample.car"), "car-body")
      _write_project_yaml(projectdir, "sample-component")

      Cozy.main(Array(
        "publish-car",
        projectdir.toString,
        s"--warehouse=$warehouse",
        "--name=sample-component",
        "--version=0.1.0",
        s"--car=$car"
      ))

      assert(Files.isRegularFile(warehouse.resolve("repository/car/sample-component/0.1.0/sample-component-0.1.0.car")))

      val out = new ByteArrayOutputStream()
      Console.withOut(new PrintStream(out, true, StandardCharsets.UTF_8.name())) {
        Cozy.main(Array("--help"))
      }
      val help = out.toString(StandardCharsets.UTF_8.name())
      assert(help.contains("publish-car <project-dir>"))
      assert(help.contains("Publish a CAR archive and CAR catalog"))
    }
  }

  test("publish-car publishes a prebuilt CAR and creates source and warehouse catalogs") {
    _with_temp_dir("cozy-publish-car-prebuilt") { dir =>
      val projectdir = dir.resolve("project")
      val warehouse = dir.resolve("warehouse")
      val car = _write(dir.resolve("input/sample.car"), "car-body")
      _write_project_yaml(projectdir, "sample-component")

      CozyCarPublisher.publish(List(
        projectdir.toString,
        s"--warehouse=$warehouse",
        "--name=sample-component",
        "--version=0.1.0",
        s"--car=$car",
        "--recommended=true"
      ))

      val target = warehouse.resolve("repository/car/sample-component/0.1.0/sample-component-0.1.0.car")
      assert(Files.readString(target) == "car-body")
      val metadata = Files.readString(warehouse.resolve("repository/car/sample-component/maven-metadata.xml"))
      assert(metadata.contains("<groupId>org.simplemodeling.repository.car</groupId>"))
      assert(metadata.contains("<artifactId>sample-component</artifactId>"))
      assert(metadata.contains("<latest>0.1.0</latest>"))
      assert(metadata.contains("<release>0.1.0</release>"))
      assert(metadata.contains("<version>0.1.0</version>"))
      assert(!Files.exists(projectdir.resolve("src/main/catalog/car/maven-metadata.xml")))
      val sourcecatalog = RepositoryArtifactCatalog.load(projectdir.resolve("src/main/catalog/car/sample-component.yaml"))
      val publiccatalog = RepositoryArtifactCatalog.load(warehouse.resolve("repository/catalog/car/sample-component.yaml"))
      assert(sourcecatalog == publiccatalog)
      assert(publiccatalog.recommended == Some("0.1.0"))
      assert(publiccatalog.latestStable == Some("0.1.0"))
      assert(publiccatalog.versions.head.runtime.flatMap(_.minimum) == Some("0.4.8"))
      assert(publiccatalog.versions.head.file == Some("repository/car/sample-component/0.1.0/sample-component-0.1.0.car"))
      assert(publiccatalog.versions.head.checksumSha256.nonEmpty)
    }
  }

  test("publish-car builds a snapshot CAR without adding snapshot to catalog") {
    _with_temp_dir("cozy-publish-car-build") { dir =>
      val projectdir = dir.resolve("project")
      val warehouse = dir.resolve("warehouse")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      _write_project_yaml(projectdir, "sample-component")
      _write(projectdir.resolve("src/main/car/web/web.yaml"), "apps:\n  - name: sample\n")

      CozyCarPublisher.publish(List(
        projectdir.toString,
        s"--warehouse=$warehouse",
        "--name=sample-component",
        "--version=0.1.1-SNAPSHOT",
        s"--main-jar=$mainjar",
        "--component=sample-component"
      ))

      val target = warehouse.resolve("repository/car/sample-component/0.1.1-SNAPSHOT/sample-component-0.1.1-SNAPSHOT.car")
      val entries = _zip_entries(target)
      assert(entries.contains("component/main.jar"))
      assert(entries.contains("component-descriptor.json"))
      assert(entries.contains("web/web.yaml"))
      assert(!Files.exists(projectdir.resolve("src/main/catalog/car/sample-component.yaml")))
      assert(!Files.exists(warehouse.resolve("repository/catalog/car/sample-component.yaml")))
      assert(!Files.exists(warehouse.resolve("repository/car/sample-component/maven-metadata.xml")))
    }
  }

  test("publish-car removes existing snapshot entries from catalog") {
    _with_temp_dir("cozy-publish-car-snapshot-cleanup") { dir =>
      val projectdir = dir.resolve("project")
      val warehouse = dir.resolve("warehouse")
      val car = _write(dir.resolve("input/sample.car"), "snapshot-car-body")
      _write_project_yaml(projectdir, "sample-component")
      _write(
        projectdir.resolve("src/main/catalog/car/sample-component.yaml"),
        """schemaVersion: 1
          |kind: car
          |artifactId: sample-component
          |latestSnapshot: 0.1.1-SNAPSHOT
          |status: active
          |aliases: []
          |versions:
          |  - version: 0.1.0
          |    channel: stable
          |    status: active
          |    component: sample-component
          |    file: repository/car/sample-component/0.1.0/sample-component-0.1.0.car
          |  - version: 0.1.1-SNAPSHOT
          |    channel: snapshot
          |    status: active
          |    component: sample-component
          |    file: repository/car/sample-component/0.1.1-SNAPSHOT/sample-component-0.1.1-SNAPSHOT.car
          |""".stripMargin
      )

      CozyCarPublisher.publish(List(
        projectdir.toString,
        s"--warehouse=$warehouse",
        "--name=sample-component",
        "--version=0.1.2-SNAPSHOT",
        s"--car=$car"
      ))

      val sourcecatalog = RepositoryArtifactCatalog.load(projectdir.resolve("src/main/catalog/car/sample-component.yaml"))
      val publiccatalog = RepositoryArtifactCatalog.load(warehouse.resolve("repository/catalog/car/sample-component.yaml"))
      assert(sourcecatalog == publiccatalog)
      assert(sourcecatalog.latestSnapshot.isEmpty)
      assert(sourcecatalog.versions.map(_.version) == Vector("0.1.0"))
      assert(sourcecatalog.versions.forall(_.channel != Some("snapshot")))
    }
  }

  test("publish-car preserves existing catalog versions and replaces same version") {
    _with_temp_dir("cozy-publish-car-merge") { dir =>
      val projectdir = dir.resolve("project")
      val warehouse = dir.resolve("warehouse")
      val car = _write(dir.resolve("input/sample.car"), "new-car-body")
      _write_project_yaml(projectdir, "sample-component")
      _write(
        projectdir.resolve("src/main/catalog/car/sample-component.yaml"),
        """schemaVersion: 1
          |kind: car
          |artifactId: sample-component
          |recommended: 0.0.9
          |latestStable: 0.1.0
          |status: active
          |aliases:
          |  - sample-old
          |versions:
          |  - version: 0.0.9
          |    channel: stable
          |    status: deprecated
          |    component: sample-component
          |    file: repository/car/sample-component/0.0.9/sample-component-0.0.9.car
          |  - version: 0.1.0
          |    channel: stable
          |    status: active
          |    component: sample-component
          |    file: repository/car/sample-component/0.1.0/sample-component-0.1.0.car
          |    checksum:
          |      sha256: old
          |""".stripMargin
      )

      CozyCarPublisher.publish(List(
        projectdir.toString,
        s"--warehouse=$warehouse",
        "--name=sample-component",
        "--version=0.1.0",
        s"--car=$car"
      ))

      val catalog = RepositoryArtifactCatalog.load(projectdir.resolve("src/main/catalog/car/sample-component.yaml"))
      assert(catalog.versions.map(_.version) == Vector("0.0.9", "0.1.0"))
      assert(catalog.versions.find(_.version == "0.0.9").flatMap(_.status) == Some("deprecated"))
      assert(catalog.versions.find(_.version == "0.1.0").flatMap(_.checksumSha256) != Some("old"))
      assert(catalog.recommended == Some("0.0.9"))
      assert(catalog.latestStable == Some("0.1.0"))
      assert(catalog.aliases == Vector("sample-old"))
      val metadata = Files.readString(warehouse.resolve("repository/car/sample-component/maven-metadata.xml"))
      assert(metadata.contains("<latest>0.0.9</latest>"))
      assert(metadata.contains("<release>0.1.0</release>"))
      assert(metadata.contains("<version>0.0.9</version>"))
      assert(metadata.contains("<version>0.1.0</version>"))
    }
  }

  private def _write_project_yaml(projectdir: Path, name: String): Unit = {
    _write(
      projectdir.resolve("project.yaml"),
      s"""project:
         |  name: $name
         |packaging:
         |  car:
         |    runtime:
         |      cncf:
         |        minimum: 0.4.8
         |        excluded: []
         |        tested:
         |          - 0.4.8
         |""".stripMargin
    )
    _write(
      projectdir.resolve("repository/textus/runtime-catalog.yaml"),
      """schemaVersion: 1
        |baseProvided:
        |  - org.goldenport:goldenport-cncf_3
        |""".stripMargin
    )
  }

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
