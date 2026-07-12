package cozy

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.ZipFile

import scala.collection.JavaConverters._

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 20, 2026
 *  version Jun.  4, 2026
 * @version Jul. 13, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyCarPublisherSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Cozy CAR publication" should {
    "provide the command surface" which {
      "dispatches publish-car through the CLI" in {
        _with_temp_dir("cozy-publish-car-cli") { dir =>
          Given("a CAR project and a prebuilt CAR archive")
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          val car = _write(dir.resolve("input/sample.car"), "car-body")
          _write_project_yaml(projectdir, "sample-component")

          When("Cozy publishes the CAR through the CLI")
          Cozy.main(
            Array(
              "publish-car",
              projectdir.toString,
              "--warehouse",
              warehouse.toString,
              "--name",
              "sample-component",
              "--version",
              "0.1.0",
              "--car",
              car.toString
            )
          )

          Then("the CAR is stored in the warehouse")
          Files.isRegularFile(
            warehouse.resolve(
              "repository/car/sample-component/0.1.0/sample-component-0.1.0.car"
            )
          ) shouldBe true
        }
      }

      "lists publish-car in command help" in {
        Given("the Cozy command-line interface")

        When("the user opens Cozy help")
        val out = new ByteArrayOutputStream()
        Console.withOut(
          new PrintStream(out, true, StandardCharsets.UTF_8.name())
        ) {
          Cozy.main(Array("--help"))
        }
        val help = out.toString(StandardCharsets.UTF_8.name())

        Then("the publish-car command and purpose are visible")
        help should include("publish-car <project-dir>")
        help should include("Publish a CAR archive and CAR catalog")
      }

      "rejects unknown options through the metadata parser" in {
        _with_temp_dir("cozy-publish-car-unknown") { dir =>
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          val car = _write(dir.resolve("input/sample.car"), "car-body")
          _write_project_yaml(projectdir, "sample-component")

          Given("a valid CAR publication request with an unsupported option")

          When("Cozy parses the publication request")
          val error = intercept[Throwable] {
            CozyCarPublisher.publish(
              List(
                projectdir.toString,
                "--warehouse",
                warehouse.toString,
                "--name",
                "sample-component",
                "--version",
                "0.1.0",
                "--car",
                car.toString,
                "--unknown",
                "value"
              )
            )
          }
          Then("the unsupported option is rejected")
          error.getMessage should include("Too many arguments")
        }
      }

    }

    "publish release artifacts" which {
      "stores a prebuilt CAR and creates source and warehouse catalogs" in {
        _with_temp_dir("cozy-publish-car-prebuilt") { dir =>
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          val car = _write(dir.resolve("input/sample.car"), "car-body")
          _write_project_yaml(projectdir, "sample-component")

          Given(
            "a CAR project with runtime requirements and a prebuilt archive"
          )

          When("Cozy publishes the release as recommended")
          CozyCarPublisher.publish(
            List(
              projectdir.toString,
              "--warehouse",
              warehouse.toString,
              "--name",
              "sample-component",
              "--version",
              "0.1.0",
              "--car",
              car.toString,
              "--recommended"
            )
          )

          val target = warehouse.resolve(
            "repository/car/sample-component/0.1.0/sample-component-0.1.0.car"
          )
          Then("the artifact and Maven metadata are written")
          Files.readString(target) shouldBe "car-body"
          val metadata = Files.readString(
            warehouse.resolve(
              "repository/car/sample-component/maven-metadata.xml"
            )
          )
          metadata should include(
            "<groupId>org.simplemodeling.repository.car</groupId>"
          )
          metadata should include("<artifactId>sample-component</artifactId>")
          metadata should include("<latest>0.1.0</latest>")
          metadata should include("<release>0.1.0</release>")
          metadata should include("<version>0.1.0</version>")
          Files.exists(
            projectdir.resolve("src/main/catalog/car/maven-metadata.xml")
          ) shouldBe false

          And(
            "the source and public catalogs contain the same release contract"
          )
          val sourcecatalog = RepositoryArtifactCatalog.load(
            projectdir.resolve("src/main/catalog/car/sample-component.yaml")
          )
          val publiccatalog = RepositoryArtifactCatalog.load(
            warehouse.resolve("repository/catalog/car/sample-component.yaml")
          )
          sourcecatalog shouldBe publiccatalog
          publiccatalog.recommended shouldBe Some("0.1.0")
          publiccatalog.latestStable shouldBe Some("0.1.0")
          publiccatalog.versions.head.runtime.flatMap(_.minimum) shouldBe Some(
            "0.4.8"
          )
          publiccatalog.versions.head.file shouldBe Some(
            "repository/car/sample-component/0.1.0/sample-component-0.1.0.car"
          )
          publiccatalog.versions.head.checksumSha256 should not be empty
        }
      }

    }

    "manage snapshot publication" which {
      "builds a snapshot CAR without adding the snapshot to the catalog" in {
        _with_temp_dir("cozy-publish-car-build") { dir =>
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
          _write_project_yaml(projectdir, "sample-component")
          _write(
            projectdir.resolve("src/main/car/web/web.yaml"),
            "apps:\n  - name: sample\n"
          )

          Given("a CAR project with a main JAR and web metadata")

          When("Cozy publishes a snapshot CAR")
          CozyCarPublisher.publish(
            List(
              projectdir.toString,
              "--warehouse",
              warehouse.toString,
              "--name",
              "sample-component",
              "--version",
              "0.1.1-SNAPSHOT",
              "--main-jar",
              mainjar.toString,
              "--component",
              "sample-component"
            )
          )

          val target = warehouse.resolve(
            "repository/car/sample-component/0.1.1-SNAPSHOT/sample-component-0.1.1-SNAPSHOT.car"
          )
          val entries = _zip_entries(target)
          Then("the CAR contains its component payload")
          entries should contain allOf (
            "component/main.jar",
            "component-descriptor.json",
            "web/web.yaml"
          )
          And("snapshot selectors are not written to release catalogs")
          Files.exists(
            projectdir.resolve("src/main/catalog/car/sample-component.yaml")
          ) shouldBe false
          Files.exists(
            warehouse.resolve("repository/catalog/car/sample-component.yaml")
          ) shouldBe false
          Files.exists(
            warehouse.resolve(
              "repository/car/sample-component/maven-metadata.xml"
            )
          ) shouldBe false
        }
      }

      "removes existing snapshot entries from the release catalog" in {
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

          Given("a release catalog contaminated by an older snapshot entry")

          When("Cozy publishes the next snapshot")
          CozyCarPublisher.publish(
            List(
              projectdir.toString,
              "--warehouse",
              warehouse.toString,
              "--name",
              "sample-component",
              "--version",
              "0.1.2-SNAPSHOT",
              "--car",
              car.toString
            )
          )

          val sourcecatalog = RepositoryArtifactCatalog.load(
            projectdir.resolve("src/main/catalog/car/sample-component.yaml")
          )
          val publiccatalog = RepositoryArtifactCatalog.load(
            warehouse.resolve("repository/catalog/car/sample-component.yaml")
          )
          Then("source and public catalogs keep only release versions")
          sourcecatalog shouldBe publiccatalog
          sourcecatalog.latestSnapshot shouldBe empty
          sourcecatalog.versions.map(_.version) shouldBe Vector("0.1.0")
          sourcecatalog.versions.forall(
            _.channel != Some("snapshot")
          ) shouldBe true
        }
      }

    }

    "maintain release history" which {
      "preserves existing versions and replaces the requested version" in {
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

          Given("a catalog with deprecated and current release versions")

          When("Cozy republishes the current version metadata")
          CozyCarPublisher.publish(
            List(
              projectdir.toString,
              "--warehouse",
              warehouse.toString,
              "--name",
              "sample-component",
              "--version",
              "0.1.0",
              "--car",
              car.toString
            )
          )

          val catalog = RepositoryArtifactCatalog.load(
            projectdir.resolve("src/main/catalog/car/sample-component.yaml")
          )
          Then(
            "history and selectors remain stable while the checksum is replaced"
          )
          catalog.versions.map(_.version) shouldBe Vector("0.0.9", "0.1.0")
          catalog.versions
            .find(_.version == "0.0.9")
            .flatMap(_.status) shouldBe Some("deprecated")
          catalog.versions
            .find(_.version == "0.1.0")
            .flatMap(_.checksumSha256) should not be Some("old")
          catalog.recommended shouldBe Some("0.0.9")
          catalog.latestStable shouldBe Some("0.1.0")
          catalog.aliases shouldBe Vector("sample-old")
          val metadata = Files.readString(
            warehouse.resolve(
              "repository/car/sample-component/maven-metadata.xml"
            )
          )
          metadata should include("<latest>0.0.9</latest>")
          metadata should include("<release>0.1.0</release>")
          metadata should include("<version>0.0.9</version>")
          metadata should include("<version>0.1.0</version>")
        }
      }

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
    _write(
      projectdir.resolve(s"src/main/cozy/${name}.cml"),
      s"# COMPONENT\n\n## ${name}\n"
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
        stream
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(p => Files.deleteIfExists(p))
      } finally {
        stream.close()
      }
    }
}
