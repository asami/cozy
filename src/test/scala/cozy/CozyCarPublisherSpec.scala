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
 * @version Jul. 28, 2026
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
          Given("a valid CAR publication request with an unsupported option")
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          val car = _write(dir.resolve("input/sample.car"), "car-body")
          _write_project_yaml(projectdir, "sample-component")

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
          val car = _write(dir.resolve("input/sample.car"), "car-body")
          _write_project_yaml(projectdir, "sample-component")

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

          And("the public discovery index exposes the CAR")
          val index = ComponentRepositoryIndex.load(
            warehouse.resolve("repository/catalog/index.json")
          )
          index.artifacts.map(_.identity) shouldBe Vector(
            "car" -> "sample-component"
          )
          index.artifacts.head.catalog shouldBe "car/sample-component.yaml"
        }
      }

      "projects the accepted unmerged project decision despite ambient defaults" in {
        _with_temp_dir("cozy-publish-car-compatibility") { dir =>
          Given("a declared CAR contract and contradictory operation-default runtime metadata")
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          _write_strict_project_yaml(projectdir, "sample-component", excluded = false)
          val car = _write_admitted_release_car(
            projectdir,
            dir.resolve("input/sample.car"),
            "sample-component",
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
            warehouse.resolve("repository/catalog/car/sample-component.yaml")
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
              warehouse.resolve("repository/catalog/car/sample-component.yaml")
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
          Given("a strict generated release project and an arbitrary archive")
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          _write_strict_project_yaml(
            projectdir,
            "sample-component",
            excluded = false
          )
          val car = _write_admitted_release_car(
            projectdir,
            dir.resolve("input/sample.car"),
            "sample-component",
            "0.1.0",
            "0.5.17",
            "0.3.1"
          )
          _write(car, "car-body")

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

          Then("publication rejects it before creating warehouse state")
          error.getMessage should include("Prebuilt CAR is not a readable archive")
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
            excluded = false
          )
          val car = _write_admitted_release_car(
            projectdir,
            dir.resolve("input/sample.car"),
            "sample-component",
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
          error.getMessage should include(
            "CAR publication version disagrees with project.yaml"
          )
          error.getMessage should include("project=0.1.1-SNAPSHOT")
          error.getMessage should include("publish=0.1.1")
          Files.exists(warehouse) shouldBe false
        }
      }

      "rejects a contradictory project decision before publication output" in {
        _with_temp_dir("cozy-publish-car-compatibility-rejected") { dir =>
          Given("a declared CAR whose runtime exclusions contain its compile target")
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          val car = _write(dir.resolve("input/sample.car"), "car-body")
          _write_strict_project_yaml(projectdir, "sample-component", excluded = true)

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
      "builds a snapshot CAR without adding the snapshot to the catalog" in {
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
          Given("a release catalog contaminated by an older snapshot entry")
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
          Given("a catalog with deprecated and current release versions")
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
          |tags:
          |  - platform.component
          |terms:
          |  - Sample Component
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
          catalog.tags shouldBe Vector("platform.component")
          catalog.terms shouldBe Vector("Sample Component")
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

  private def _publish_with_immutable_evidence(
    args: List[String],
    cncfversion: String,
    cozyversion: String
  ): Unit = {
    val evidence = _immutable_evidence(cncfversion, cozyversion)
    cozy.archive.CozyCarPublisher.publish(args, evidence, cozyversion)
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
    version: String,
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
         |  "format": "cozy.car.abi-manifest.v1",
         |  "car": {
         |    "name": "$name",
         |    "version": "$version"
         |  },
         |  "abi": {
         |    "version": 1,
         |    "exports": {
         |      "components": [
         |        {
         |          "name": "$name"
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
    cozy.archive.CozyArchivePackager.buildCar(
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
        name,
        "--version",
        version,
        "--component",
        name
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
    componentversion: String = "0.1.0",
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

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
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
