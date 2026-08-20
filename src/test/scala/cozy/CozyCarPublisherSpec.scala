package cozy

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}

import scala.collection.JavaConverters._

import cozy.archive.ComponentRepositoryIndex
import cozy.compatibility.{
  GenerationCompatibility,
  GenerationCompatibilityBoundary,
  GenerationCompatibilityEvidence,
  GenerationEvidenceOwner,
  GenerationPairEvidence,
  GenerationPairStatus
}

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import cozy.modeler.GenerationProvenance

/*
 * @since   May. 20, 2026
 *  version Jun.  4, 2026
 *  version Jul. 28, 2026
 * @version Aug. 21, 2026
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
          val car = _write_canonical_car(
            dir.resolve("input/sample.car"),
            "0.1.0",
            "cli"
          )
          _write_project_yaml(projectdir, "sample-component", "0.1.0")

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
              "repository/car/org/sample/sample-component/0.1.0/sample-component-0.1.0.car"
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
          Given("a valid CAR publication request with an unsupported option")
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          val car = _write(dir.resolve("input/sample.car"), "car-body")
          _write_project_yaml(projectdir, "sample-component", "0.1.0")

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
          Given(
            "a CAR project with runtime requirements and a prebuilt archive"
          )
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          _write_strict_project_yaml(
            projectdir,
            "sample-component",
            excluded = false,
            componentversion = "0.1.0"
          )
          val car = _write_admitted_release_car(
            projectdir,
            dir.resolve("input/sample.car"),
            "sample-component",
            "0.1.0",
            "0.1.0",
            "0.5.17",
            "0.3.1"
          )

          When("Cozy publishes the release as recommended")
          _publish_with_immutable_evidence(
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
            ),
            "0.5.17",
            "0.3.1"
          )

          val target = warehouse.resolve(
            "repository/car/org/sample/sample-component/0.1.0/sample-component-0.1.0.car"
          )
          Then("the artifact and Maven metadata are written")
          _zip_text(target, "component/main.jar") shouldBe "main"
          Files.isRegularFile(
            target.resolveSibling(target.getFileName.toString + ".sha256")
          ) shouldBe true
          val metadata = Files.readString(
            warehouse.resolve(
              "repository/car/org/sample/sample-component/maven-metadata.xml"
            )
          )
          metadata should include(
            "<groupId>org.sample</groupId>"
          )
          metadata should include("<artifactId>sample-component</artifactId>")
          metadata should include("<latest>0.1.0</latest>")
          metadata should include("<release>0.1.0</release>")
          metadata should include("<version>0.1.0</version>")
          Files.exists(
            projectdir.resolve("src/main/catalog/car/org/sample/sample-component/maven-metadata.xml")
          ) shouldBe false

          And(
            "the source and public catalogs contain the same release contract"
          )
          val sourcecatalog = RepositoryArtifactCatalog.load(
            projectdir.resolve("src/main/catalog/car/org/sample/sample-component.yaml")
          )
          val publiccatalog = RepositoryArtifactCatalog.load(
            warehouse.resolve("repository/catalog/car/org/sample/sample-component.yaml")
          )
          sourcecatalog shouldBe publiccatalog
          publiccatalog.recommended shouldBe Some("0.1.0")
          publiccatalog.latestStable shouldBe Some("0.1.0")
          publiccatalog.versions.head.runtime.flatMap(_.minimum) shouldBe Some(
            "0.5.17"
          )
          publiccatalog.versions.head.file shouldBe Some(
            "repository/car/org/sample/sample-component/0.1.0/sample-component-0.1.0.car"
          )
          publiccatalog.versions.head.checksumSha256 should not be empty

          And("the public discovery index exposes the CAR")
          val index = ComponentRepositoryIndex.load(
            warehouse.resolve("repository/catalog/index.json")
          )
          index.artifacts.map(_.identity) shouldBe Vector(
            ("car", "org.sample", "Component")
          )
          index.artifacts.head.catalog shouldBe "car/org/sample/sample-component.yaml"
        }
      }

      "projects the accepted unmerged project decision despite ambient defaults" in {
        _with_temp_dir("cozy-publish-car-compatibility") { dir =>
          Given("a declared CAR contract and contradictory operation-default runtime metadata")
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          _write_strict_project_yaml(projectdir, "sample-component", excluded = false, componentversion = "0.1.0")
          val car = _write_admitted_release_car(
            projectdir,
            dir.resolve("input/sample.car"),
            "sample-component",
            "0.1.0",
            "0.1.0",
            "0.5.17",
            "0.3.1"
          )
          _write(
            projectdir.resolve(".cozy/config.yaml"),
            """packaging:
              |  car:
              |    runtime:
              |      cncf:
              |        minimum: 9.9.9
              |        maximum: 9.9.9
              |        excluded: []
              |        tested:
              |          - 9.9.9
              |""".stripMargin
          )

          When("Cozy publishes the prebuilt CAR")
          _publish_with_immutable_evidence(
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
            ),
            "0.5.17",
            "0.3.1"
          )

          Then("the publication catalog reports the same project-owned runtime decision")
          val catalog = RepositoryArtifactCatalog.load(
            warehouse.resolve("repository/catalog/car/org/sample/sample-component.yaml")
          )
          val runtime = catalog.versions.head.runtime
          runtime.flatMap(_.minimum) shouldBe Some("0.5.17")
          runtime.flatMap(_.maximum) shouldBe Some("0.5.19")
          runtime.toVector.flatMap(_.excluded) shouldBe empty
          runtime.toVector.flatMap(_.tested) shouldBe Vector("0.5.17")
        }
      }

      "projects generated compatible runtime versions through publication" in {
        Given("generated exact CNCF versions represented by strict CAR projects")
        val property = Prop.forAll(Gen.chooseNum(1, 19)) { patch =>
          _with_temp_dir("cozy-publish-car-property") { dir =>
            val cncfversion = s"0.5.$patch"
            val projectdir = dir.resolve("project")
            val warehouse = dir.resolve("warehouse")
            _write_strict_project_yaml(
              projectdir,
              "sample-component",
              excluded = false,
              cncfversion = cncfversion,
              componentversion = s"0.1.$patch"
            )
            val car = _write_admitted_release_car(
              projectdir,
              dir.resolve("input/sample.car"),
              "sample-component",
              s"0.1.$patch",
              s"0.1.$patch",
              cncfversion,
              "0.3.1"
            )

            When("Cozy publishes each compatible generated project")
            _publish_with_immutable_evidence(
              List(
                projectdir.toString,
                "--warehouse",
                warehouse.toString,
                "--name",
                "sample-component",
                "--version",
                s"0.1.$patch",
                "--car",
                car.toString
              ),
              cncfversion,
              "0.3.1"
            )

            Then("the catalog preserves the generated exact runtime identity")
            val catalog = RepositoryArtifactCatalog.load(
              warehouse.resolve("repository/catalog/car/org/sample/sample-component.yaml")
            )
            catalog.versions.head.runtime.flatMap(_.minimum).contains(
              cncfversion
            )
          }
        }

        Test.check(
          Test.Parameters.default.withMinSuccessfulTests(50),
          property
        ).passed shouldBe true
      }

      "rejects a prebuilt release CAR that bypassed package admission" in {
        _with_temp_dir("cozy-publish-car-prebuilt-admission") { dir =>
          Given("a canonical release project and a tampered archive coordinate")
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          _write_project_yaml(projectdir, "sample-component", "0.1.0")
          val car = _write_canonical_car(
            dir.resolve("input/sample.car"),
            "0.1.0",
            "car-body"
          )
          _archive(
            car,
            Vector(
              "component-descriptor.json" -> """{"schemaVersion":3,"component":{"namespace":"org.sample","id":"Other","version":"0.1.0"}}""",
              "abi-manifest.json" -> """{"format":"cozy.car.abi-manifest.v2","component":{"namespace":"org.sample","id":"Component","version":"0.1.0"},"abi":{"version":1,"exports":{"components":[{"namespace":"org.sample","id":"Component"}],"operations":[],"entities":[]},"dependencies":[]}}""",
              "component/main.jar" -> "car-body"
            )
          )

          When("publication receives the unadmitted prebuilt archive")
          val error = intercept[Throwable] {
            _publish_with_immutable_evidence(
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
              ),
              "0.5.17",
              "0.3.1"
            )
          }

          Then("publication rejects its mismatched canonical coordinate before warehouse state")
          error.getMessage should include("component.release-coordinate.mismatch")
          Files.exists(warehouse) shouldBe false
        }
      }

      "rejects a prebuilt release CAR after owning generated output changes" in {
        _with_temp_dir("cozy-publish-car-stale-generated-output") { dir =>
          Given("a release CAR and matching owning-project generation evidence")
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          _write_strict_project_yaml(
            projectdir,
            "sample-component",
            excluded = false,
            componentversion = "0.1.0"
          )
          val car = _write_admitted_release_car(
            projectdir,
            dir.resolve("input/sample.car"),
            "sample-component",
            "0.1.0",
            "0.1.0",
            "0.5.17",
            "0.3.1"
          )

          And("the owning generated Scala output changes after CAR creation")
          _write(
            _generated_fixture_path(projectdir, "sample-component"),
            "final class ReplacedOutput\n"
          )

          When("publication revalidates the prebuilt release CAR")
          val error = intercept[Throwable] {
            _publish_with_immutable_evidence(
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
              ),
              "0.5.17",
              "0.3.1"
            )
          }

          Then("the stale generation boundary is rejected before repository writes")
          error.getMessage should include(
            "GENERATION_PROVENANCE_OUTPUT_TAMPERED"
          )
          Files.exists(warehouse) shouldBe false
        }
      }

      "rejects a publish version that differs from the project lifecycle" in {
        _with_temp_dir("cozy-publish-car-version-lifecycle") { dir =>
          Given("a SNAPSHOT CAR project and an attempted release publication")
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          val car = _write(dir.resolve("input/sample.car"), "car-body")
          _write_strict_project_yaml(
            projectdir,
            "sample-component",
            excluded = false,
            componentversion = "0.1.1-SNAPSHOT",
            cozyversion = org.simplemodeling.cozy.BuildInfo.version
          )

          When("publish-car is asked to publish the same project as a release")
          val error = intercept[Throwable] {
            CozyCarPublisher.publish(
              List(
                projectdir.toString,
                "--warehouse",
                warehouse.toString,
                "--name",
                "sample-component",
                "--version",
                "0.1.1",
                "--car",
                car.toString
              )
            )
          }

          Then("publication rejects the lifecycle contradiction before repository writes")
          error.getMessage should include("component.release-coordinate.projection-mismatch")
          error.getMessage should include("field=version")
          error.getMessage should include("expected=0.1.1-SNAPSHOT")
          error.getMessage should include("actual=0.1.1")
          Files.exists(warehouse) shouldBe false
        }
      }

      "rejects a contradictory project decision before publication output" in {
        _with_temp_dir("cozy-publish-car-compatibility-rejected") { dir =>
          Given("a declared CAR whose runtime exclusions contain its compile target")
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          val car = _write(dir.resolve("input/sample.car"), "car-body")
          _write_strict_project_yaml(projectdir, "sample-component", excluded = true, componentversion = "0.1.0")

          When("Cozy attempts to publish the prebuilt CAR")
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
                car.toString
              )
            )
          }

          Then("the package diagnostic is preserved and the warehouse remains untouched")
          error.getMessage should include(
            "CAR_METADATA_CNCF_COMPILE_TARGET_EXCLUDED"
          )
          Files.exists(warehouse) shouldBe false
        }
      }

    }

    "manage snapshot publication" which {
      "builds a snapshot CAR and records canonical discovery metadata" in {
        _with_temp_dir("cozy-publish-car-build") { dir =>
          Given("a CAR project with a main JAR and web metadata")
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
          val modelmetadata = _write(
            dir.resolve("target/cozy/model-metadata.json"),
            """{
              |  "schema": "cozy.cml.model-metadata.v1",
              |  "surface": {"component": {"services": []}},
              |  "modelElements": []
              |}
              |""".stripMargin
          )
          _write_strict_project_yaml(
            projectdir,
            "sample-component",
            excluded = false,
            componentversion = "0.1.1-SNAPSHOT",
            cozyversion = org.simplemodeling.cozy.BuildInfo.version
          )
          val cncfjar = _write_runtime_jar(
            dir.resolve("artifacts/goldenport-cncf_3.jar"),
            "0.5.17"
          )
          _write(
            projectdir.resolve("src/main/car/web/web.yaml"),
            "apps:\n  - name: sample\n"
          )

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
              "--lib-jars",
              cncfjar.toString,
              "--model-metadata",
              modelmetadata.toString,
              "--component",
              "Component"
            )
          )

          val target = warehouse.resolve(
            "repository/car/org/sample/sample-component/0.1.1-SNAPSHOT/sample-component-0.1.1-SNAPSHOT.car"
          )
          val entries = _zip_entries(target)
          Then("the CAR archive and checksum are published")
          Files.isRegularFile(target) shouldBe true
          entries should contain allOf (
            "component/main.jar",
            "component-descriptor.json",
            "web/web.yaml"
          )
          val digest = cozy.archive.RepositoryArtifactPublisher.sha256(target)
          Files.readString(
            target.resolveSibling(target.getFileName.toString + ".sha256")
          ).trim shouldBe digest

          And("source and public schema2 catalogs expose the current snapshot")
          val sourcecatalog = RepositoryArtifactCatalog.load(
            projectdir.resolve("src/main/catalog/car/org/sample/sample-component.yaml")
          )
          val publiccatalog = RepositoryArtifactCatalog.load(
            warehouse.resolve("repository/catalog/car/org/sample/sample-component.yaml")
          )
          sourcecatalog shouldBe publiccatalog
          sourcecatalog.schemaVersion shouldBe "2"
          sourcecatalog.versions.map(_.version) shouldBe Vector("0.1.1-SNAPSHOT")
          val snapshotentry = sourcecatalog.versions.head
          snapshotentry.channel shouldBe Some("snapshot")
          snapshotentry.component shouldBe Some("org.sample.Component")
          snapshotentry.checksumSha256 shouldBe Some(digest)
          snapshotentry.integrityKey shouldBe Some(
            s"org.sample:sample-component:0.1.1-SNAPSHOT@sha256:$digest"
          )
          sourcecatalog.latestSnapshot shouldBe Some("0.1.1-SNAPSHOT")

          And("Maven metadata and the v2 repository index expose the snapshot")
          val metadata = Files.readString(
            warehouse.resolve(
              "repository/car/org/sample/sample-component/maven-metadata.xml"
            )
          )
          metadata should include("<latest>0.1.1-SNAPSHOT</latest>")
          metadata should include("<version>0.1.1-SNAPSHOT</version>")
          val index = ComponentRepositoryIndex.load(
            warehouse.resolve("repository/catalog/index.json")
          )
          index.schemaVersion shouldBe ComponentRepositoryIndex.SCHEMA_VERSION
          index.artifacts.map(_.identity) shouldBe Vector(
            ("car", "org.sample", "Component")
          )
          val indexentry = index.artifacts.head
          indexentry.namespace shouldBe Some("org.sample")
          indexentry.id shouldBe Some("Component")
          indexentry.latestSnapshot shouldBe Some("0.1.1-SNAPSHOT")
        }
      }

      "replaces the prior snapshot entry while retaining stable history" in {
        _with_temp_dir("cozy-publish-car-snapshot-cleanup") { dir =>
          Given("a canonical catalog with stable release history and one prior snapshot entry")
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          val car = _write_canonical_car(
            dir.resolve("input/sample.car"),
            "0.1.2-SNAPSHOT",
            "snapshot"
          )
          _write_project_yaml(projectdir, "sample-component", "0.1.2-SNAPSHOT")
          val releasedigest = _write_retained_car(warehouse, "0.1.0", "retained-release")
          val snapshotdigest = _write_retained_car(warehouse, "0.1.1-SNAPSHOT", "retained-snapshot")
          _write_car_catalog(
            projectdir,
            Vector(
              ("0.1.0", "stable", "active", releasedigest),
              ("0.1.1-SNAPSHOT", "snapshot", "active", snapshotdigest)
            ),
            lateststable = Some("0.1.0"),
            latestsnapshot = Some("0.1.1-SNAPSHOT")
          )

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
            projectdir.resolve("src/main/catalog/car/org/sample/sample-component.yaml")
          )
          val publiccatalog = RepositoryArtifactCatalog.load(
            warehouse.resolve("repository/catalog/car/org/sample/sample-component.yaml")
          )
          Then("source and public catalogs retain stable history and replace the snapshot")
          sourcecatalog shouldBe publiccatalog
          sourcecatalog.versions.map(_.version) shouldBe Vector(
            "0.1.0",
            "0.1.2-SNAPSHOT"
          )
          sourcecatalog.latestStable shouldBe Some("0.1.0")
          sourcecatalog.latestSnapshot shouldBe Some("0.1.2-SNAPSHOT")
          sourcecatalog.versions.exists(_.version == "0.1.1-SNAPSHOT") shouldBe false
          val snapshotentry = sourcecatalog.versions.find(
            _.version == "0.1.2-SNAPSHOT"
          )
          snapshotentry.flatMap(_.channel) shouldBe Some("snapshot")
          snapshotentry.flatMap(_.checksumSha256) should not be Some(snapshotdigest)
        }
      }

    }

    "maintain release history" which {
      "preserves stable and deprecated releases, replaces the requested version, and excludes stale snapshots" in {
        _with_temp_dir("cozy-publish-car-merge") { dir =>
          Given("a canonical catalog with stable and deprecated releases plus a stale local snapshot absent from the new public warehouse")
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          val car = _write_canonical_car(
            dir.resolve("input/sample.car"),
            "0.1.0",
            "replacement"
          )
          _write_project_yaml(projectdir, "sample-component", "0.1.0")
          val deprecateddigest = _write_retained_car(warehouse, "0.0.9", "deprecated-release")
          val currentdigest = _write_retained_car(warehouse, "0.1.0", "current-release")
          val snapshotdigest = "0" * 64
          _write_car_catalog(
            projectdir,
            Vector(
              ("0.0.9", "stable", "deprecated", deprecateddigest),
              ("0.1.0", "stable", "active", currentdigest),
              ("0.1.1-SNAPSHOT", "snapshot", "active", snapshotdigest)
            ),
            recommended = Some("0.0.9"),
            lateststable = Some("0.1.0"),
            latestsnapshot = Some("0.1.1-SNAPSHOT"),
            aliases = Vector("sample-old"),
            tags = Vector("platform.component"),
            terms = Vector("Sample Component")
          )

          When("Cozy publishes the stable canonical CAR to the new public warehouse")
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
            projectdir.resolve("src/main/catalog/car/org/sample/sample-component.yaml")
          )
          val publiccatalog = RepositoryArtifactCatalog.load(
            warehouse.resolve("repository/catalog/car/org/sample/sample-component.yaml")
          )
          Then(
            "stable history remains, the requested release is replaced, and the stale snapshot is excluded"
          )
          catalog.versions.map(_.version) shouldBe Vector(
            "0.0.9",
            "0.1.0"
          )
          catalog.versions
            .find(_.version == "0.0.9")
            .flatMap(_.status) shouldBe Some("deprecated")
          catalog.versions
            .find(_.version == "0.1.0")
            .flatMap(_.checksumSha256) should not be Some("old")
          catalog.recommended shouldBe Some("0.0.9")
          catalog.latestStable shouldBe Some("0.1.0")
          catalog.latestSnapshot shouldBe None
          catalog.versions.find(_.version == "0.1.1-SNAPSHOT") shouldBe None
          publiccatalog.versions.find(_.version == "0.1.1-SNAPSHOT") shouldBe None
          Files.exists(
            warehouse.resolve(
              "repository/car/org/sample/sample-component/0.1.1-SNAPSHOT/sample-component-0.1.1-SNAPSHOT.car"
            )
          ) shouldBe false
          catalog.aliases shouldBe Vector("sample-old")
          catalog.tags shouldBe Vector("platform.component")
          catalog.terms shouldBe Vector("Sample Component")
          val metadata = Files.readString(
            warehouse.resolve(
              "repository/car/org/sample/sample-component/maven-metadata.xml"
            )
          )
          metadata should include("<latest>0.0.9</latest>")
          metadata should include("<release>0.1.0</release>")
          metadata should include("<version>0.0.9</version>")
          metadata should include("<version>0.1.0</version>")
          metadata should not include "<version>0.1.1-SNAPSHOT</version>"
        }
      }

    }

    "migrate an explicit legacy component warehouse" which {
      "copies mapped CAR releases, retains SAR discovery, and continues the requested publication" in {
        _with_temp_dir("cozy-publish-car-legacy-migration") { dir =>
          Given("a v1 warehouse with one mapped CAR, one SAR, and legacy CML sidecars")
          val warehouse = dir.resolve("warehouse")
          val legacybytes = _write_legacy_warehouse(warehouse)
          val projectdir = dir.resolve("project")
          val car = _write_canonical_car(
            dir.resolve("input/sample-new-component.car"), "0.1.0", "new-release", componentid = "NewComponent"
          )
          _write_project_yaml(projectdir, "sample-new-component", "0.1.0", componentid = "NewComponent")

          When("Cozy publishes a distinct new CAR through the locked repository boundary")
          CozyCarPublisher.publish(
            List(projectdir.toString, "--warehouse", warehouse.toString, "--name", "sample-new-component", "--version", "0.1.0", "--car", car.toString)
          )

          Then("the v2 index is final, canonical CAR files are integrity-protected, and legacy bytes remain")
          val index = ComponentRepositoryIndex.load(warehouse.resolve("repository/catalog/index.json"))
          index.schemaVersion shouldBe ComponentRepositoryIndex.SCHEMA_VERSION
          index.artifacts.map(_.identity) should contain allOf (
            ("car", "org.sample", "Component"),
            ("car", "org.sample", "NewComponent"),
            ("sar", "", "sample-server")
          )
          val canonical = warehouse.resolve("repository/car/org/sample/sample-component/0.0.9/sample-component-0.0.9.car")
          java.util.Arrays.equals(Files.readAllBytes(canonical), legacybytes) shouldBe true
          Files.isRegularFile(canonical.resolveSibling("sample-component-0.0.9.car.sha256")) shouldBe true
          Files.readString(warehouse.resolve("repository/catalog/car/org/sample/sample-component.cml")) shouldBe "# legacy component\n"
          val catalog = RepositoryArtifactCatalog.load(warehouse.resolve("repository/catalog/car/org/sample/sample-component.yaml"))
          catalog.schemaVersion shouldBe "2"
          catalog.versions.map(_.version) shouldBe Vector("0.0.9")
          catalog.versions.find(_.version == "0.0.9").flatMap(_.integrityKey) should not be empty
          Files.isRegularFile(warehouse.resolve("repository/car/org/sample/sample-new-component/0.1.0/sample-new-component-0.1.0.car")) shouldBe true
          Files.readAllBytes(warehouse.resolve("repository/car/sample-component/0.0.9/sample-component-0.0.9.car")) shouldBe legacybytes
        }
      }

      "rejects absent, incomplete, and duplicate mappings before a final canonical path is created" in {
        _with_temp_dir("cozy-publish-car-legacy-mapping") { dir =>
          Given("a v1 warehouse and a requested new CAR publication")
          val warehouse = dir.resolve("warehouse")
          _write_legacy_warehouse(warehouse, mapping = None)
          val projectdir = dir.resolve("project")
          val car = _write_canonical_car(dir.resolve("input/sample.car"), "0.1.0", "new-release")
          _write_project_yaml(projectdir, "sample-component", "0.1.0")
          val indexpath = warehouse.resolve("repository/catalog/index.json")
          val original = Files.readAllBytes(indexpath)

          When("the mapping is absent, incomplete, and then duplicate")
          val missing = intercept[Throwable] {
            CozyCarPublisher.publish(List(projectdir.toString, "--warehouse", warehouse.toString, "--name", "sample-component", "--version", "0.1.0", "--car", car.toString))
          }
          _write_mapping(warehouse, """{"schema":"cozy.component-repository-migration.v1","cars":[]}""")
          val incomplete = intercept[Throwable] {
            CozyCarPublisher.publish(List(projectdir.toString, "--warehouse", warehouse.toString, "--name", "sample-component", "--version", "0.1.0", "--car", car.toString))
          }
          _write_mapping(warehouse, """{"schema":"cozy.component-repository-migration.v1","cars":[{"artifactId":"sample-component","namespace":"org.sample","id":"Component"},{"artifactId":"sample-component","namespace":"org.example","id":"Example"}]}""")
          val duplicate = intercept[Throwable] {
            CozyCarPublisher.publish(List(projectdir.toString, "--warehouse", warehouse.toString, "--name", "sample-component", "--version", "0.1.0", "--car", car.toString))
          }

          Then("each mapping error preserves v1 bytes and publishes no canonical or requested CAR")
          missing.getMessage should startWith("component.repository.migration.mapping.missing")
          incomplete.getMessage should startWith("component.repository.migration.mapping.missing")
          duplicate.getMessage should startWith("component.repository.migration.mapping.duplicate")
          Files.readAllBytes(indexpath) shouldBe original
          Files.exists(warehouse.resolve("repository/catalog/car/org/sample/sample-component.yaml")) shouldBe false
          Files.exists(warehouse.resolve("repository/car/org/sample/sample-component/0.1.0/sample-component-0.1.0.car")) shouldBe false
        }
      }

      "rejects checksum mismatch and canonical collisions without mutating the legacy warehouse" in {
        _with_temp_dir("cozy-publish-car-legacy-integrity") { dir =>
          Given("a mapped v1 warehouse whose stored digest does not match its archive")
          val warehouse = dir.resolve("warehouse")
          _write_legacy_warehouse(warehouse, checksum = Some("0" * 64))
          val projectdir = dir.resolve("project")
          val car = _write_canonical_car(dir.resolve("input/sample.car"), "0.1.0", "new-release")
          _write_project_yaml(projectdir, "sample-component", "0.1.0")
          val original = Files.readAllBytes(warehouse.resolve("repository/catalog/index.json"))

          When("Cozy encounters the invalid checksum")
          val checksumerror = intercept[Throwable] {
            CozyCarPublisher.publish(List(projectdir.toString, "--warehouse", warehouse.toString, "--name", "sample-component", "--version", "0.1.0", "--car", car.toString))
          }
          _write_legacy_warehouse(warehouse, checksum = None)
          _write(warehouse.resolve("repository/car/org/sample/sample-component/0.0.9/sample-component-0.0.9.car"), "different")
          val collisionerror = intercept[Throwable] {
            CozyCarPublisher.publish(List(projectdir.toString, "--warehouse", warehouse.toString, "--name", "sample-component", "--version", "0.1.0", "--car", car.toString))
          }

          Then("both guarded failures leave the v1 index and legacy archive untouched")
          checksumerror.getMessage should startWith("component.repository.migration.integrity.mismatch")
          collisionerror.getMessage should startWith("component.repository.migration.collision")
          Files.readAllBytes(warehouse.resolve("repository/catalog/index.json")) shouldBe original
          Files.readString(warehouse.resolve("repository/car/org/sample/sample-component/0.0.9/sample-component-0.0.9.car")) shouldBe "different"
        }
      }
    }
  }

  private def _write_legacy_warehouse(
    warehouse: Path,
    mapping: Option[String] = Some("default"),
    checksum: Option[String] = None
  ): Array[Byte] = {
    val archive = _write(warehouse.resolve("repository/car/sample-component/0.0.9/sample-component-0.0.9.car"), "legacy-car")
    val digest = checksum.getOrElse(cozy.archive.RepositoryArtifactPublisher.sha256(archive))
    _write(archive.resolveSibling("sample-component-0.0.9.car.sha256"), digest + "\n")
    _write(
      warehouse.resolve("repository/catalog/car/sample-component.yaml"),
      s"""schemaVersion: 1
         |kind: car
         |artifactId: sample-component
         |recommended: 0.0.9
         |latestStable: 0.0.9
         |status: active
         |aliases:
         |  - legacy-component
         |tags:
         |  - legacy
         |terms:
         |  - Legacy Component
         |versions:
         |  - version: 0.0.9
         |    channel: stable
         |    status: active
         |    publishedAt: 2026-08-01T00:00:00Z
         |    file: repository/car/sample-component/0.0.9/sample-component-0.0.9.car
         |    checksum:
         |      sha256: $digest
         |""".stripMargin
    )
    _write(warehouse.resolve("repository/catalog/car/sample-component.cml"), "# legacy component\n")
    _write(
      warehouse.resolve("repository/catalog/sar/sample-server.yaml"),
      """schemaVersion: 1
        |kind: sar
        |artifactId: sample-server
        |status: active
        |aliases: []
        |versions:
        |  - version: 0.0.9
        |    channel: stable
        |    status: active
        |    file: repository/sar/sample-server/0.0.9/sample-server-0.0.9.sar
        |""".stripMargin
    )
    _write(warehouse.resolve("repository/sar/sample-server/0.0.9/sample-server-0.0.9.sar"), "legacy-sar")
    _write(
      warehouse.resolve("repository/catalog/index.json"),
      """{"schemaVersion":"cncf.component-repository-index.v1","generatedAt":"2026-08-01T00:00:00Z","artifacts":[{"kind":"car","artifactId":"sample-component","catalog":"car/sample-component.yaml","status":"active","recommended":"0.0.9","latestStable":"0.0.9"},{"kind":"sar","artifactId":"sample-server","catalog":"sar/sample-server.yaml","status":"active"}]}"""
    )
    mapping.foreach { value =>
      _write_mapping(
        warehouse,
        if (value == "default")
          """{"schema":"cozy.component-repository-migration.v1","cars":[{"artifactId":"sample-component","namespace":"org.sample","id":"Component"}]}"""
        else value
      )
    }
    Files.readAllBytes(archive)
  }

  private def _write_mapping(warehouse: Path, text: String): Path =
    _write(warehouse.resolve(".cozy/component-repository-migration.v1.json"), text)

  private def _write_project_yaml(
    projectdir: Path,
    name: String,
    componentversion: String,
    namespace: String = "org.sample",
    componentid: String = "Component"
  ): Unit = {
    _write(
      projectdir.resolve("project.yaml"),
      s"""project:
         |  namespace: $namespace
         |  id: $componentid
         |  name: $name
         |  component:
         |    version: $componentversion
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

  private def _publish_with_immutable_evidence(
    args: List[String],
    cncfversion: String,
    cozyversion: String
  ): Unit = {
    val evidence = _immutable_evidence(cncfversion, cozyversion)
    cozy.archive.CozyCarPublisher._publish_with_evidence(args, evidence, cozyversion)
  }

  private def _immutable_evidence(
    cncfversion: String,
    cozyversion: String
  ): GenerationCompatibilityEvidence = {
    val pair =
      GenerationCompatibilityBoundary.createPair(cncfversion, cozyversion)
    GenerationCompatibilityEvidence(
      GenerationCompatibility.evidenceSchema,
      GenerationEvidenceOwner(
        "CozyCarPublisherSpec immutable release fixture",
        "CozyCarPublisherSpec"
      ),
      Vector(GenerationPairEvidence(pair, GenerationPairStatus.Proven)),
      None
    )
  }

  private def _write_admitted_release_car(
    projectdir: Path,
    archive: Path,
    name: String,
    projectversion: String,
    transportversion: String,
    cncfversion: String,
    cozyversion: String
  ): Path = {
    val source = projectdir.resolve(s"src/main/cozy/$name.cml")
    val generated = _generated_fixture_path(projectdir, name)
    _write(generated, s"final class ${name.replace("-", "")}\n")
    val snapshot = GenerationProvenance.requireSourceSnapshot(
      source,
      s"src/main/cozy/$name.cml"
    )
    GenerationProvenance.write(
      projectdir,
      snapshot,
      GenerationProvenance.Inputs(
        cncfTargetVersion = cncfversion,
        runtimeDescriptorSha256 = "a" * 64,
        cozyGeneratorVersion = cozyversion,
        simpleModelerBackendVersion = "0.2.0",
        simpleModelingModelVersion = "0.2.0",
        sourceIdentity = snapshot.identity,
        sourceSha256 = snapshot.sha256
      )
    )
    _write(
      projectdir.resolve("src/main/car/abi-manifest.json"),
      s"""{
         |  "format": "cozy.car.abi-manifest.v2",
         |  "component": {
         |    "namespace": "org.sample",
         |    "id": "Component",
         |    "version": "$transportversion"
         |  },
         |  "abi": {
         |    "version": 1,
         |    "exports": {
         |      "components": [
         |        {
         |          "namespace": "org.sample",
         |          "id": "Component"
         |        }
         |      ],
         |      "operations": [],
         |      "entities": []
         |    },
         |    "dependencies": []
         |  }
         |}
         |""".stripMargin
    )
    val mainjar = _write(projectdir.resolve("artifacts/main.jar"), "main")
    val cncfjar = _write_runtime_jar(
      projectdir.resolve(s"artifacts/goldenport-cncf_3-$cncfversion.jar"),
      cncfversion
    )
    Option(archive.getParent).foreach(Files.createDirectories(_))
    cozy.archive.CozyArchivePackager._build_car(
      List(
        "--save",
        archive.toString,
        "--project-dir",
        projectdir.toString,
        "--main-jar",
        mainjar.toString,
        "--lib-jars",
        cncfjar.toString,
        "--name",
        "sample-component",
        "--version",
        transportversion,
        "--component",
        "Component"
      ),
      _immutable_evidence(cncfversion, cozyversion),
      cozyversion
    )
    archive
  }

  private def _generated_fixture_path(
    projectdir: Path,
    name: String
  ): Path =
    projectdir.resolve(
      s"target/scala-3.3.8/src_managed/main/${name.replace('-', '_')}.scala"
    )

  private def _write_runtime_jar(
    path: Path,
    cncfversion: String
  ): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val out = new ZipOutputStream(Files.newOutputStream(path))
    try {
      out.putNextEntry(new ZipEntry("META-INF/cncf/runtime.yaml"))
      out.write(
        s"""schemaVersion: 1
           |runtime: cncf
           |version: $cncfversion
           |module: org.goldenport:goldenport-cncf_3:$cncfversion
           |""".stripMargin.getBytes(StandardCharsets.UTF_8)
      )
      out.closeEntry()
    } finally {
      out.close()
    }
    path
  }

  private def _write_strict_project_yaml(
    projectdir: Path,
    name: String,
    excluded: Boolean,
    cncfversion: String = "0.5.17",
    componentversion: String,
    cozyversion: String = "0.3.1"
  ): Unit = {
    val exclusions =
      if (excluded)
        s"        excluded:\n          - $cncfversion"
      else
        "        excluded: []"
    _write(
      projectdir.resolve("project.yaml"),
      s"""project:
         |  kind: car
         |  namespace: org.sample
         |  id: Component
         |  name: $name
         |  component:
         |    version: $componentversion
         |packaging:
         |  kind: car
         |  car:
         |    runtime:
         |      cncf:
         |        minimum: $cncfversion
         |        maximum: 0.5.19
         |$exclusions
         |        tested:
         |          - $cncfversion
         |build:
         |  cozyVersion: $cozyversion
         |  dependencies:
         |    compile:
         |      - org.goldenport::goldenport-cncf:$cncfversion
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

  private def _zip_text(path: Path, entryname: String): String = {
    val zip = new ZipFile(path.toFile)
    try {
      val entry = zip.getEntry(entryname)
      val input = zip.getInputStream(entry)
      try scala.io.Source.fromInputStream(input, "UTF-8").mkString
      finally input.close()
    } finally zip.close()
  }

  private def _write_canonical_car(
    archive: Path,
    version: String,
    payload: String,
    namespace: String = "org.sample",
    componentid: String = "Component"
  ): Path =
    _archive(
      archive,
      Vector(
        "component-descriptor.json" ->
          s"""{"schemaVersion":3,"component":{"namespace":"$namespace","id":"$componentid","version":"$version"}}""",
        "abi-manifest.json" ->
          s"""{"format":"cozy.car.abi-manifest.v2","component":{"namespace":"$namespace","id":"$componentid","version":"$version"},"abi":{"version":1,"exports":{"components":[{"namespace":"$namespace","id":"$componentid"}],"operations":[],"entities":[]},"dependencies":[]}}""",
        "component/main.jar" -> payload
      )
    )

  private def _write_retained_car(
    warehouse: Path,
    version: String,
    payload: String
  ): String = {
    val archive = warehouse.resolve(
      s"repository/car/org/sample/sample-component/$version/sample-component-$version.car"
    )
    _write_canonical_car(archive, version, payload)
    val digest = cozy.archive.RepositoryArtifactPublisher.sha256(archive)
    _write(archive.resolveSibling(archive.getFileName.toString + ".sha256"), digest + "\n")
    digest
  }

  private def _write_car_catalog(
    projectdir: Path,
    entries: Vector[(String, String, String, String)],
    recommended: Option[String] = None,
    lateststable: Option[String] = None,
    latestsnapshot: Option[String] = None,
    aliases: Vector[String] = Vector.empty,
    tags: Vector[String] = Vector.empty,
    terms: Vector[String] = Vector.empty
  ): Path = {
    def _optional_line_(label: String, value: Option[String]): String = value.map(v => s"$label: $v\n").getOrElse("")
    def _list_lines_(label: String, values: Vector[String]): String =
      if (values.isEmpty) s"$label: []\n" else s"$label:\n${values.map(v => s"  - $v\n").mkString}"
    val versionlines = entries.map { case (version, channel, status, digest) =>
      s"""  - version: $version
         |    channel: $channel
         |    status: $status
         |    component: org.sample.Component
         |    file: repository/car/org/sample/sample-component/$version/sample-component-$version.car
         |    checksum:
         |      sha256: $digest
         |    integrityKey: org.sample:sample-component:$version@sha256:$digest
         |""".stripMargin
    }.mkString
    val text =
      s"""schemaVersion: 2
         |kind: car
         |namespace: org.sample
         |id: Component
         |artifactId: sample-component
         |${_optional_line_("recommended", recommended)}${_optional_line_("latestStable", lateststable)}${_optional_line_("latestSnapshot", latestsnapshot)}status: active
         |${_list_lines_("aliases", aliases)}${_list_lines_("tags", tags)}${_list_lines_("terms", terms)}versions:
         |$versionlines""".stripMargin
    _write(
      projectdir.resolve("src/main/catalog/car/org/sample/sample-component.yaml"),
      text
    )
  }

  private def _archive(archive: Path, entries: Vector[(String, String)]): Path = {
    Files.createDirectories(archive.getParent)
    val output = new ZipOutputStream(Files.newOutputStream(archive))
    try entries.foreach { case (entryname, value) =>
      output.putNextEntry(new ZipEntry(entryname))
      output.write(value.getBytes(StandardCharsets.UTF_8))
      output.closeEntry()
    } finally output.close()
    archive
  }

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val workroot = Path.of("target/cozy-test/work/cozy-car-publisher-spec").toAbsolutePath.normalize()
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
        stream
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(p => Files.deleteIfExists(p))
      } finally {
        stream.close()
      }
    }
}
