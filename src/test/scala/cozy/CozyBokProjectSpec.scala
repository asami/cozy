package cozy

import cozy.bok.CozyBok
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
import io.circe.parser
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jun. 23, 2026
 * @version Jun. 24, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyBokProjectSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy BoK CAR project publication" should {
    "register CAR project source packages" which {
      "discover projects/<category>/<slug> packages and write publication registry metadata" in {
        _with_temp_dir("cozy-bok-project") { dir =>
          Given(
            "a BoK source tree with a CAR project package and an external CAR project reference"
          )
          val externalproject = dir.resolve("external/nict-knowledgehub")
          _write(
            externalproject.resolve("build.sbt"),
            "ThisBuild / version := \"0.1.0\"\n"
          )
          _write(
            externalproject.resolve("src/main/cozy/nict-knowledgehub.cml"),
            """# ENTITY
              |
              |## KnowledgeItem
              |
              |# VALUE
              |
              |## KnowledgeScore
              |
              |# POWERTYPE
              |
              |## DecisionState
              |
              |# STATEMACHINE
              |
              |## KnowledgeLifecycle
              |""".stripMargin
          )
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            s"""bok:
               |  projects:
               |    nict-knowledgehub:
               |      path: ${externalproject.toString}
               |""".stripMargin
          )
          val pkg = dir.resolve(
            "src/main/doxsite/projects/concept/nict-knowledgehub"
          )
          _write(
            pkg.resolve("index.dox"),
            "NictKnowledgeHub\n================\n"
          )
          _write(
            pkg.resolve("project.yaml"),
            """project:
              |  type: car
              |  name: nict-knowledgehub
              |  mode: external
              |  ref: nict-knowledgehub
              |car:
              |  module: nict-knowledgehub
              |title: NICT KnowledgeHub
              |version: 0.1.0
              |summary: NICT KnowledgeHub CAR project.
              |article: index.dox
              |publication:
              |  path: projects/concept/nict-knowledgehub
              |""".stripMargin
          )
          val config = CozyBok.PublicationConfig.create(
            "publish-projects",
            List(dir.toString)
          )

          When("Cozy registers CAR project publication metadata")
          val results = CozyBok.publishProjects(config)

          Then(
            "the publication registry is written without creating generated files inside the source package"
          )
          results.map(_.project.name) shouldBe Vector("nict-knowledgehub")
          dir.resolve(
            "src/main/publication/nict-knowledgehub.json"
          ) should beRegularFile
          val bundle =
            _read(dir.resolve("src/main/publication/nict-knowledgehub.json"))
          bundle should include(
            "metadata/projects/car/nict-knowledgehub/metadata.json"
          )
          bundle should include(
            "metadata/projects/car/nict-knowledgehub/0.1.0/manifest.json"
          )
          bundle should include(
            "metadata/artifacts/repository/nict-knowledgehub.json"
          )
          bundle should include("projects/concept/nict-knowledgehub")
          bundle should include("\"status\" : \"missing\"")
          bundle should include(
            "CAR artifact is not registered in artifact repository"
          )
          bundle should include("Run cozy publish-car <nict-knowledgehub>")
          bundle should not include (externalproject.toAbsolutePath
            .normalize()
            .toString)
          bundle should include("\"cml\"")
          bundle should include("\"kind\" : \"entity\"")
          bundle should include("\"name\" : \"KnowledgeItem\"")
          bundle should include("\"termId\" : \"cml:knowledge-item\"")
          bundle should include("\"kind\" : \"value\"")
          bundle should include("\"kind\" : \"powertype\"")
          bundle should include("\"kind\" : \"statemachine\"")
          val sourcefiles = Files
            .walk(pkg)
            .iterator()
            .asScala
            .toVector
            .filter(Files.isRegularFile(_))
            .map(_.getFileName.toString)
          sourcefiles should notContainWhere[String](_.endsWith(".car"))
          sourcefiles should notContainWhere[String](_.endsWith(".ttl"))
          sourcefiles should notContainWhere[String](_.endsWith(".jsonld"))
        }
      }

      "record published artifact metadata when the CAR already exists in the repository" in {
        _with_temp_dir("cozy-bok-project-artifact") { dir =>
          Given(
            "a CAR project package and a pre-existing repository CAR artifact"
          )
          val externalproject = dir.resolve("external/nict-knowledgehub")
          _write(
            externalproject.resolve("build.sbt"),
            "ThisBuild / version := \"0.1.0\"\n"
          )
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            s"""bok:
               |  projects:
               |    nict-knowledgehub:
               |      path: ${externalproject.toString}
               |""".stripMargin
          )
          val pkg = dir.resolve(
            "src/main/doxsite/projects/concept/nict-knowledgehub"
          )
          _write(
            pkg.resolve("index.dox"),
            "NictKnowledgeHub\n================\n"
          )
          _write(
            pkg.resolve("project.yaml"),
            """project:
              |  type: car
              |  name: nict-knowledgehub
              |  mode: external
              |  ref: nict-knowledgehub
              |car:
              |  module: nict-knowledgehub
              |title: NICT KnowledgeHub
              |version: 0.1.0
              |publication:
              |  path: projects/concept/nict-knowledgehub
              |""".stripMargin
          )
          _write(
            dir.resolve(
              "repository/car/nict-knowledgehub/0.1.0/nict-knowledgehub-0.1.0.car"
            ),
            "car-body"
          )
          val config = CozyBok.PublicationConfig.create(
            "publish-projects",
            List(dir.toString)
          )

          When("Cozy registers CAR project publication metadata")
          val results = CozyBok.publishProjects(config)

          Then("the artifact is represented as a published repository artifact")
          results.head.artifactexists shouldBe true
          val bundle =
            _read(dir.resolve("src/main/publication/nict-knowledgehub.json"))
          bundle should include("\"status\" : \"published\"")
          bundle should include("\"sha256\"")
          bundle should include(
            "repository/car/nict-knowledgehub/0.1.0/nict-knowledgehub-0.1.0.car"
          )
        }
      }

      "record project-local repository artifact metadata when bok.repository points to a separate directory" in {
        _with_temp_dir("cozy-bok-project-local-repository") { dir =>
          Given(
            "a CAR project package and a project-local public repository artifact in a non-default directory"
          )
          val externalproject = dir.resolve("external/nict-knowledgehub")
          _write(
            externalproject.resolve("build.sbt"),
            "ThisBuild / version := \"0.1.0\"\n"
          )
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            s"""bok:
               |  repository: public-repository
               |  projects:
               |    nict-knowledgehub:
               |      path: ${externalproject.toString}
               |""".stripMargin
          )
          val pkg = dir.resolve(
            "src/main/doxsite/projects/concept/nict-knowledgehub"
          )
          _write(
            pkg.resolve("index.dox"),
            "NictKnowledgeHub\n================\n"
          )
          _write(
            pkg.resolve("project.yaml"),
            """project:
              |  type: car
              |  name: nict-knowledgehub
              |  mode: external
              |  ref: nict-knowledgehub
              |car:
              |  module: nict-knowledgehub
              |title: NICT KnowledgeHub
              |publication:
              |  path: projects/concept/nict-knowledgehub
              |""".stripMargin
          )
          _write(
            dir.resolve(
              "public-repository/car/nict-knowledgehub/0.1.0/nict-knowledgehub-0.1.0.car"
            ),
            "car-body"
          )
          _write(
            dir.resolve("public-repository/catalog/car/nict-knowledgehub.yaml"),
            """schemaVersion: 1
              |kind: car
              |artifactId: nict-knowledgehub
              |recommended: 0.1.0
              |latestStable: 0.1.0
              |status: active
              |aliases: []
              |versions:
              |  - version: 0.1.0
              |    channel: stable
              |    status: active
              |    component: nict-knowledgehub
              |    file: repository/car/nict-knowledgehub/0.1.0/nict-knowledgehub-0.1.0.car
              |""".stripMargin
          )
          val config = CozyBok.PublicationConfig.create(
            "publish-projects",
            List(dir.toString)
          )

          When("Cozy registers CAR project publication metadata")
          val results = CozyBok.publishProjects(config)

          Then(
            "the configured repository root is used while public metadata keeps repository paths"
          )
          results.head.artifact shouldBe dir
            .resolve(
              "public-repository/car/nict-knowledgehub/0.1.0/nict-knowledgehub-0.1.0.car"
            )
            .toAbsolutePath
            .normalize()
          results.head.artifactexists shouldBe true
          dir.resolve(
            "repository/car/nict-knowledgehub/0.1.0/nict-knowledgehub-0.1.0.car"
          ) shouldNot existPath
          dir.resolve(
            "warehouse/repository/car/nict-knowledgehub/0.1.0/nict-knowledgehub-0.1.0.car"
          ) shouldNot existPath
          val bundle =
            _read(dir.resolve("src/main/publication/nict-knowledgehub.json"))
          bundle should include("\"versionSource\" : \"repository-catalog\"")
          bundle should include("repository/catalog/car/nict-knowledgehub.yaml")
          bundle should include(
            "\"warehousePath\" : \"repository/car/nict-knowledgehub/0.1.0/nict-knowledgehub-0.1.0.car\""
          )
          bundle should include(
            "\"publicPath\" : \"repository/car/nict-knowledgehub/0.1.0/nict-knowledgehub-0.1.0.car\""
          )
          bundle should not include ("repository/repository/car")
          bundle should not include ("public-repository/car")
        }
      }

      "derive CAR version and artifact references from the repository catalog when descriptor version is omitted" in {
        _with_temp_dir("cozy-bok-project-catalog") { dir =>
          Given(
            "a CAR project package that relies on the repository CAR catalog"
          )
          val externalproject = dir.resolve("external/nict-knowledgehub")
          _write(
            externalproject.resolve("build.sbt"),
            "ThisBuild / version := \"0.2.0\"\n"
          )
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            s"""bok:
               |  projects:
               |    nict-knowledgehub:
               |      path: ${externalproject.toString}
               |""".stripMargin
          )
          _write(
            dir.resolve(
              "repository/car/nict-knowledgehub/0.2.0/nict-knowledgehub-0.2.0.car"
            ),
            "catalog-car-body"
          )
          _write(
            dir.resolve("repository/catalog/car/nict-knowledgehub.yaml"),
            """schemaVersion: 1
              |kind: car
              |artifactId: nict-knowledgehub
              |recommended: 0.2.0
              |latestStable: 0.2.0
              |status: active
              |aliases: []
              |versions:
              |  - version: 0.2.0
              |    channel: stable
              |    status: active
              |    component: nict-knowledgehub
              |    file: repository/car/nict-knowledgehub/0.2.0/nict-knowledgehub-0.2.0.car
              |""".stripMargin
          )
          val pkg = dir.resolve(
            "src/main/doxsite/projects/concept/nict-knowledgehub"
          )
          _write(
            pkg.resolve("index.dox"),
            "NictKnowledgeHub\n================\n"
          )
          _write(
            pkg.resolve("project.yaml"),
            """project:
              |  type: car
              |  name: nict-knowledgehub
              |  mode: external
              |  ref: nict-knowledgehub
              |car:
              |  module: nict-knowledgehub
              |title: NICT KnowledgeHub
              |summary: NICT KnowledgeHub CAR project.
              |article: index.dox
              |publication:
              |  path: projects/concept/nict-knowledgehub
              |""".stripMargin
          )
          val config = CozyBok.PublicationConfig.create(
            "publish-projects",
            List(dir.toString)
          )

          When("Cozy registers the CAR project from the BoK package")
          val results = CozyBok.publishProjects(config)

          Then(
            "the project version and artifact metadata are sourced from the repository catalog"
          )
          results.head.project.version shouldBe "0.2.0"
          results.head.project.versionsource shouldBe "repository-catalog"
          results.head.artifactexists shouldBe true
          val bundle =
            _read(dir.resolve("src/main/publication/nict-knowledgehub.json"))
          bundle should include("\"version\" : \"0.2.0\"")
          bundle should include("\"versionSource\" : \"repository-catalog\"")
          bundle should include("repository/catalog/car/nict-knowledgehub.yaml")
          bundle should include(
            "repository/car/nict-knowledgehub/0.2.0/nict-knowledgehub-0.2.0.car"
          )
        }
      }

      "register external projects from repository metadata without a local project path" in {
        _with_temp_dir("cozy-bok-project-catalog-only") { dir =>
          Given(
            "a CAR project package whose repository catalog has model metadata but no local external project path"
          )
          _write(
            dir.resolve(
              "repository/catalog/car/nict-knowledgehub.model-metadata.json"
            ),
            """{
              |  "schema": "cozy.cml.model-metadata.v1",
              |  "source": {
              |    "path": "repository/catalog/car/nict-knowledgehub.cml",
              |    "sha256": "catalog-only",
              |    "compiler": "cozy-modeler",
              |    "cozyVersion": "test"
              |  },
              |  "elements": [
              |    {
              |      "kind": "entity",
              |      "name": "CatalogOnlyEntity",
              |      "descriptive": {
              |        "label": "Catalog Only Entity"
              |      }
              |    }
              |  ]
              |}
              |""".stripMargin
          )
          val pkg = dir.resolve(
            "src/main/doxsite/projects/concept/nict-knowledgehub"
          )
          _write(
            pkg.resolve("index.dox"),
            "NictKnowledgeHub\n================\n"
          )
          _write(
            pkg.resolve("project.yaml"),
            """project:
              |  type: car
              |  name: nict-knowledgehub
              |  mode: external
              |  ref: nict-knowledgehub
              |car:
              |  module: nict-knowledgehub
              |title: NICT KnowledgeHub
              |version: 0.1.0
              |publication:
              |  path: projects/concept/nict-knowledgehub
              |""".stripMargin
          )
          val config = CozyBok.PublicationConfig.create(
            "publish-projects",
            List(dir.toString)
          )

          When("Cozy registers CAR project publication metadata")
          CozyBok.publishProjects(config)

          Then("the repository model metadata is enough to register the project")
          val bundle =
            _read(dir.resolve("src/main/publication/nict-knowledgehub.json"))
          bundle should include("repository-model-metadata")
          bundle should include("CatalogOnlyEntity")
          bundle should not include ("Missing BoK project reference path")
        }
      }

      "resolve external project paths from local .cozy overrides" in {
        _with_temp_dir("cozy-bok-project-local-config") { dir =>
          Given(
            "public config points to a non-local reference and .cozy contains the local sensitive path"
          )
          val externalproject = dir.resolve("local/nict-knowledgehub")
          _write(
            externalproject.resolve("build.sbt"),
            "ThisBuild / version := \"0.1.0\"\n"
          )
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            """bok:
              |  projects:
              |    nict-knowledgehub:
              |      path: /not/local/nict-knowledgehub
              |""".stripMargin
          )
          _write(
            dir.resolve(".cozy/config.yaml"),
            s"""bok:
               |  projects:
               |    nict-knowledgehub:
               |      path: ${externalproject.toString}
               |""".stripMargin
          )
          val pkg = dir.resolve(
            "src/main/doxsite/projects/concept/nict-knowledgehub"
          )
          _write(
            pkg.resolve("index.dox"),
            "NictKnowledgeHub\n================\n"
          )
          _write(
            pkg.resolve("project.yaml"),
            """project:
              |  type: car
              |  name: nict-knowledgehub
              |  mode: external
              |  ref: nict-knowledgehub
              |car:
              |  module: nict-knowledgehub
              |title: NICT KnowledgeHub
              |version: 0.1.0
              |publication:
              |  path: projects/concept/nict-knowledgehub
              |""".stripMargin
          )
          val config = CozyBok.PublicationConfig.create(
            "publish-projects",
            List(dir.toString)
          )

          When("Cozy resolves the CAR project reference")
          val results = CozyBok.publishProjects(config)

          Then(
            "the local sensitive .cozy path overrides the public config path"
          )
          results.head.project.projectpath.map(_.toString) shouldBe Some(
            externalproject.toAbsolutePath.normalize().toString
          )
          val bundle =
            _read(dir.resolve("src/main/publication/nict-knowledgehub.json"))
          bundle should not include (externalproject.toAbsolutePath
            .normalize()
            .toString)
          bundle should not include ("/not/local/nict-knowledgehub")
        }
      }

      "reject generated .car-product.d work directories" in {
        _with_temp_dir("cozy-bok-project-workdir") { dir =>
          Given(
            "a BoK source tree containing a reserved CAR project work directory"
          )
          Files.createDirectories(
            dir.resolve("src/main/doxsite/projects/concept/generated.car-product.d")
          )
          val config = CozyBok.PublicationConfig.create(
            "publish-projects",
            List(dir.toString)
          )

          When("Cozy discovers CAR project packages")
          val e = intercept[Throwable] {
            CozyBok.publishProjects(config)
          }

          Then("the reserved generated/work directory naming is rejected")
          e.getMessage should include(".car-product")
        }
      }

      "reject project package directories without project descriptors" in {
        _with_temp_dir("cozy-bok-project-missing-descriptor") { dir =>
          Given(
            "a projects/<category>/<slug> directory that looks like a project package but has no descriptor"
          )
          _write(
            dir.resolve(
              "src/main/doxsite/projects/concept/nict-knowledgehub/index.dox"
            ),
            "NictKnowledgeHub\n================\n"
          )
          val config = CozyBok.PublicationConfig.create(
            "publish-projects",
            List(dir.toString)
          )

          When("Cozy discovers CAR project packages")
          val e = intercept[Throwable] {
            CozyBok.publishProjects(config)
          }

          Then("the missing descriptor is reported explicitly")
          e.getMessage should include("Missing project descriptor")
        }
      }
    }

    "integrate CAR projects with BoK publication commands" which {
      "update-publication handles CAR projects without invoking CAR artifact publishing" in {
        _with_temp_dir("cozy-bok-project-update") { dir =>
          Given(
            "a BoK source tree with one CAR project and no warehouse artifact"
          )
          val externalproject = dir.resolve("external/nict-knowledgehub")
          _write(
            externalproject.resolve("build.sbt"),
            "ThisBuild / version := \"0.1.0\"\n"
          )
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            s"""bok:
               |  projects:
               |    nict-knowledgehub:
               |      path: ${externalproject.toString}
               |""".stripMargin
          )
          val pkg = dir.resolve(
            "src/main/doxsite/projects/concept/nict-knowledgehub"
          )
          _write(
            pkg.resolve("index.dox"),
            "NictKnowledgeHub\n================\n"
          )
          _write(
            pkg.resolve("project.yaml"),
            """project:
              |  type: car
              |  name: nict-knowledgehub
              |  mode: external
              |  ref: nict-knowledgehub
              |car:
              |  module: nict-knowledgehub
              |title: NICT KnowledgeHub
              |version: 0.1.0
              |publication:
              |  path: projects/concept/nict-knowledgehub
              |""".stripMargin
          )
          val config = CozyBok.PublicationConfig.create(
            "update-publication",
            List(dir.toString)
          )

          When("Cozy updates the BoK publication registry")
          val updated = CozyBok.updatePublication(
            config,
            RecordingVoicevoxClient,
            NoopVideoRunner
          )

          Then(
            "only publication metadata is written; no CAR artifact is generated"
          )
          updated should contain("nict-knowledgehub")
          dir.resolve(
            "src/main/publication/nict-knowledgehub.json"
          ) should beRegularFile
          dir.resolve(
            "warehouse/repository/car/nict-knowledgehub/0.1.0/nict-knowledgehub-0.1.0.car"
          ) shouldNot existPath
        }
      }

      "publish dry-run reports both video and CAR project packages" in {
        _with_temp_dir("cozy-bok-project-dry-run") { dir =>
          Given("a BoK project with upload workflow and a CAR project package")
          val externalproject = dir.resolve("external/nict-knowledgehub")
          _write(
            externalproject.resolve("build.sbt"),
            "ThisBuild / version := \"0.1.0\"\n"
          )
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            s"""bok:
               |  projects:
               |    nict-knowledgehub:
               |      path: ${externalproject.toString}
               |  workflow:
               |    upload:
               |      command: "etc/upload.sh"
               |""".stripMargin
          )
          val pkg = dir.resolve(
            "src/main/doxsite/projects/concept/nict-knowledgehub"
          )
          _write(
            pkg.resolve("index.dox"),
            "NictKnowledgeHub\n================\n"
          )
          _write(
            pkg.resolve("project.yaml"),
            """project:
              |  type: car
              |  name: nict-knowledgehub
              |  mode: external
              |  ref: nict-knowledgehub
              |car:
              |  module: nict-knowledgehub
              |title: NICT KnowledgeHub
              |version: 0.1.0
              |publication:
              |  path: projects/concept/nict-knowledgehub
              |""".stripMargin
          )
          val config = CozyBok.PublicationConfig.create(
            "publish",
            List(dir.toString, "--dry-run")
          )

          When("Cozy prints the one-stop publish plan")
          val out = _capture {
            CozyBok.publish(
              config,
              new RecordingRunner,
              RecordingVoicevoxClient,
              NoopVideoRunner
            )
          }

          Then(
            "the plan and manifest include CAR project package counts without side effects"
          )
          out should include("0 .video package(s), 1 project package(s)")
          dir.resolve("src/main/publication") shouldNot existPath
          dir.resolve("warehouse") shouldNot existPath
          val manifest =
            _read(dir.resolve("target/cozy-bok/publish/latest/manifest.json"))
          manifest should include("projectPackages")
          manifest should include("projects/concept/nict-knowledgehub")
          parser
            .parse(manifest)
            .fold(throw _, identity)
            .hcursor
            .downField("dryRun")
            .as[Boolean]
            .fold(throw _, identity) shouldBe true
        }
      }

      "publish-car writes CML and model-metadata sidecars into the warehouse catalog" in {
        _with_temp_dir("cozy-bok-project-publish-car-sidecars") { dir =>
          Given(
            "a CAR project with a CML source file and a prebuilt CAR archive"
          )
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          val car =
            _write(dir.resolve("input/nict-knowledgehub.car"), "car-body")
          _write(
            projectdir.resolve("project.yaml"),
            """project:
              |  name: nict-knowledgehub
              |packaging:
              |  car:
              |    runtime:
              |      cncf:
              |        minimum: 0.4.8
              |""".stripMargin
          )
          _write(
            projectdir.resolve("src/main/cozy/nict-knowledgehub.cml"),
            """# ENTITY
              |
              |## KnowledgeItem
              |
              |### SUMMARY
              |
              |Knowledge item managed by the component.
              |
              |### DESCRIPTION
              |
              |CML description for the KnowledgeItem term.
              |
              |# VALUE
              |
              |## KnowledgeScore
              |""".stripMargin
          )

          When("Cozy publishes the CAR artifact")
          CozyCarPublisher.publish(
            List(
              projectdir.toString,
              "--warehouse",
              warehouse.toString,
              "--name",
              "nict-knowledgehub",
              "--version",
              "0.1.0",
              "--car",
              car.toString
            )
          )

          Then(
            "the warehouse CAR catalog contains the CML source and machine-readable model metadata sidecars"
          )
          val catalogdir = warehouse.resolve("repository/catalog/car")
          catalogdir.resolve("nict-knowledgehub.yaml") should beRegularFile
          catalogdir.resolve("nict-knowledgehub.cml") should beRegularFile
          catalogdir.resolve(
            "nict-knowledgehub.model-metadata.json"
          ) should beRegularFile
          catalogdir.resolve(
            "nict-knowledgehub.model-metadata.yaml"
          ) should beRegularFile
          val metadata =
            _read(catalogdir.resolve("nict-knowledgehub.model-metadata.json"))
          metadata should include("cozy.cml.model-metadata.v1")
          metadata should include(
            "\"path\" : \"src/main/cozy/nict-knowledgehub.cml\""
          )
          metadata should not include (projectdir.toAbsolutePath
            .normalize()
            .toString)
          metadata should include("\"kind\" : \"entity\"")
          metadata should include("\"name\" : \"KnowledgeItem\"")
          metadata should include("Knowledge item managed by the component.")
          metadata should include("CML description for the KnowledgeItem term.")
          metadata should include("\"kind\" : \"value\"")
        }
      }

      "prefer repository model-metadata over direct CML scan for CAR project registration" in {
        _with_temp_dir("cozy-bok-project-model-metadata-priority") { dir =>
          Given(
            "a BoK CAR project whose repository catalog has generated model metadata"
          )
          val externalproject = dir.resolve("external/nict-knowledgehub")
          _write(
            externalproject.resolve("build.sbt"),
            "ThisBuild / version := \"0.1.0\"\n"
          )
          _write(
            externalproject.resolve("src/main/cozy/nict-knowledgehub.cml"),
            """# ENTITY
              |
              |## DirectOnlyEntity
              |""".stripMargin
          )
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            s"""bok:
               |  projects:
               |    nict-knowledgehub:
               |      path: ${externalproject.toString}
               |""".stripMargin
          )
          _write(
            dir.resolve(
              "repository/catalog/car/nict-knowledgehub.model-metadata.json"
            ),
            """{
              |  "schema": "cozy.cml.model-metadata.v1",
              |  "source": {
              |    "path": "repository-sidecar.cml",
              |    "sha256": "sidecar",
              |    "compiler": "cozy-modeler",
              |    "cozyVersion": "test"
              |  },
              |  "elements": [
              |    {
              |      "kind": "entity",
              |      "name": "CatalogEntity",
              |      "termId": "technology:catalog-entity",
              |      "glossaryPath": "glossary/technology/catalog-entity.html",
              |      "descriptive": {
              |        "label": "Catalog Entity",
              |        "summary": "Repository metadata summary.",
              |        "description": "Repository metadata description."
              |      },
              |      "narrative": "Repository metadata narrative."
              |    }
              |  ]
              |}
              |""".stripMargin
          )
          val pkg = dir.resolve(
            "src/main/doxsite/projects/concept/nict-knowledgehub"
          )
          _write(
            pkg.resolve("index.dox"),
            "NictKnowledgeHub\n================\n"
          )
          _write(
            pkg.resolve("project.yaml"),
            """project:
              |  type: car
              |  name: nict-knowledgehub
              |  mode: external
              |  ref: nict-knowledgehub
              |car:
              |  module: nict-knowledgehub
              |cml:
              |  glossary:
              |    category: technology
              |title: NICT KnowledgeHub
              |version: 0.1.0
              |publication:
              |  path: projects/concept/nict-knowledgehub
              |""".stripMargin
          )
          val config = CozyBok.PublicationConfig.create(
            "publish-projects",
            List(dir.toString)
          )

          When("Cozy registers CAR project publication metadata")
          CozyBok.publishProjects(config)

          Then(
            "the registry uses the repository model-metadata and does not fall back to direct CML scan"
          )
          val bundle =
            _read(dir.resolve("src/main/publication/nict-knowledgehub.json"))
          bundle should include("repository-model-metadata")
          bundle should include("CatalogEntity")
          bundle should include("Repository metadata summary.")
          bundle should include("Repository metadata narrative.")
          bundle should not include ("DirectOnlyEntity")
        }
      }

      "help lists CAR project publication commands" in {
        Given("the Cozy help surface")
        When("Cozy renders command help")
        val help = _capture {
          Cozy.main(Array("--help"))
        }
        val bokhelp = _capture {
          CozyBok.execute(List("bok", "publish-projects", "--help"))
        }

        Then("CAR project publication commands are discoverable")
        help should include("bok publish-projects <project-dir>")
        help should include("projects/<category>/<slug>")
        bokhelp should include("Usage: cozy bok publish-projects")
        bokhelp should include(
          "CAR artifact publishing remains the responsibility of cozy publish-car"
        )
      }

      "build renders CAR project article pages at their publication path" in {
        _with_temp_dir("cozy-bok-project-build") { dir =>
          Given(
            "a BoK source tree with a CAR project package and publication path"
          )
          val externalproject = dir.resolve("external/nict-knowledgehub")
          _write(
            externalproject.resolve("build.sbt"),
            "ThisBuild / version := \"0.1.0\"\n"
          )
          _write(
            externalproject.resolve("src/main/cozy/nict-knowledgehub.cml"),
            """# ENTITY
              |
              |## KnowledgeItem
              |
              |### SUMMARY
              |
              |Knowledge item summary from CML.
              |
              |### NARRATIVE
              |
              |Knowledge item narrative from CML.
              |""".stripMargin
          )
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            s"""bok:
               |  projects:
               |    nict-knowledgehub:
               |      path: ${externalproject.toString}
               |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            "site { output { locale_mode = \"single_locale_root\" } }\n"
          )
          _write(dir.resolve("src/main/doxsite/index.dox"), "Home\n====\n")
          val pkg = dir.resolve(
            "src/main/doxsite/projects/concept/nict-knowledgehub"
          )
          _write(
            pkg.resolve("index.dox"),
            """NictKnowledgeHub CAR Product
              |============================
              |
              |## HEADLINE
              |
              |NictKnowledgeHub CAR Product
              |
              |## BRIEF
              |
              |NICT KnowledgeHub CAR project.
              |
              |# Overview
              |
              |NictKnowledgeHub CAR Product article body.
              |""".stripMargin
          )
          _write(
            pkg.resolve("project.yaml"),
            """project:
              |  type: car
              |  name: nict-knowledgehub
              |  mode: external
              |  ref: nict-knowledgehub
              |car:
              |  module: nict-knowledgehub
              |title: NICT KnowledgeHub
              |version: 0.1.0
              |summary: NICT KnowledgeHub CAR component project.
              |publication:
              |  path: textus/components/nict-knowledgehub
              |""".stripMargin
          )
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--strategy", "preview")
          )

          When("Cozy builds the BoK site")
          CozyBok.build(config, new ProjectBuildRunner)

          Then(
            "the CAR project article is rendered as a normal BoK page without generating CAR artifacts"
          )
          val page = _read(
            dir.resolve(
              "website.d/textus/components/nict-knowledgehub/index.html"
            )
          )
          page should include("NICT KnowledgeHub")
          page should include("NictKnowledgeHub CAR Product article body.")
          page should include("Artifact status")
          page should include("missing")
          page should include("CML Model Vocabulary")
          page should include("CMLで定義したEntity, Value, Powertype, Statemachine")
          page should include("KnowledgeItem")
          page should include("Knowledge item summary from CML.")
          page should include("glossary/cml/knowledge-item.html")
          val termhub =
            _read(dir.resolve("website.d/glossary/cml/knowledge-item.html"))
          termhub should include("generated-from-cml")
          termhub should include("needs-curation")
          termhub should include("Descriptive Attributes")
          termhub should include("Knowledge item summary from CML.")
          termhub should include("CML-derived narrative")
          termhub should include("Knowledge item narrative from CML.")
          dir.resolve(
            "warehouse/repository/car/nict-knowledgehub/0.1.0/nict-knowledgehub-0.1.0.car"
          ) shouldNot existPath
        }
      }
    }
  }

  private object RecordingVoicevoxClient
      extends cozy.video.CozyVideo.VoicevoxClient {
    def speakers(baseurl: String): io.circe.Json =
      io.circe.Json.arr()
    def audioQuery(
        baseurl: String,
        text: String,
        speakerid: Int
    ): io.circe.Json =
      io.circe.Json.obj()
    def synthesis(
        baseurl: String,
        speakerid: Int,
        audioquery: io.circe.Json
    ): Array[Byte] =
      Array.emptyByteArray
  }

  private object NoopVideoRunner
      extends cozy.video.CozyVideo.VideoProcessRunner {
    def run(
        command: Vector[String],
        cwd: Path
    ): cozy.video.CozyVideo.VideoCommandResult =
      cozy.video.CozyVideo.VideoCommandResult(0, "", "")
  }

  private class RecordingRunner extends CozyBok.Runner {
    var commands = Vector.empty[Vector[String]]
    def run(command: Vector[String], cwd: Path): Unit =
      commands :+= command
  }

  private class ProjectBuildRunner extends RecordingRunner {
    override def run(command: Vector[String], cwd: Path): Unit = {
      super.run(command, cwd)
      if (command.take(2) == Vector("dox", "site")) {
        _write(
          cwd.resolve("doxsite.d/metadata/dashboard/site.json"),
          _dashboard_json
        )
        _write(
          cwd.resolve("doxsite.d/site.ttl"),
          "@prefix ex: <https://example.com/> .\n"
        )
        _write(cwd.resolve("doxsite.d/site.jsonld"), "{\"@graph\":[]}\n")
      }
    }
  }

  private def _with_temp_dir[A](prefix: String)(f: Path => A): A = {
    val dir = Files.createTempDirectory(prefix)
    try {
      f(dir)
    } finally {
      _delete(dir)
    }
  }

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _read(path: Path): String =
    new String(Files.readAllBytes(path), StandardCharsets.UTF_8)

  private def _capture(body: => Unit): String = {
    val out = new ByteArrayOutputStream()
    Console.withOut(out) {
      body
    }
    out.toString(StandardCharsets.UTF_8.name())
  }

  private def _dashboard_json: String =
    """{
      |  "counts": {
      |    "category_count": 1,
      |    "article_count": 1,
      |    "glossary_term_count": 0,
      |    "total_item_count": 1
      |  },
      |  "rdf": {
      |    "resource_count": 0,
      |    "triple_count": 0,
      |    "subject_count": 0,
      |    "predicate_count": 0
      |  },
      |  "increments": {
      |    "scale": "day",
      |    "buckets": []
      |  },
      |  "categories": []
      |}
      |""".stripMargin

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator.asScala.toVector.reverse.foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }
}
