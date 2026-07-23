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
 * @version Jul. 23, 2026
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
            """# COMPONENT
              |
              |## NictKnowledgeHub
              |
              |### SUMMARY
              |
              |NICT KnowledgeHub component.
              |
              |# SERVICE
              |
              |## Knowledge
              |
              |### SUMMARY
              |
              |KnowledgeHub service operations.
              |
              |### OPERATION
              |
              |#### ingestKnowledge
              |
              |##### SUMMARY
              |Ingest a knowledge item.
              |
              |##### DESCRIPTION
              |Ingests authored or imported knowledge content.
              |
              |##### TYPE
              |COMMAND
              |
              |##### INPUT
              |
              |###### TYPE
              |IngestKnowledge
              |
              |##### OUTPUT
              |
              |###### TYPE
              |IngestKnowledgeResult
              |
              |#### searchKnowledge
              |
              |##### SUMMARY
              |Search knowledge items.
              |
              |##### DESCRIPTION
              |Searches KnowledgeHub items by query text.
              |
              |##### TYPE
              |QUERY
              |
              |##### INPUT
              |
              |###### TYPE
              |SearchKnowledge
              |
              |##### OUTPUT
              |
              |###### TYPE
              |SearchKnowledgeResult
              |
              |# ENTITY
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
               |      repository: path
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
              |terms:
              |  - semantic integration
              |tags:
              |  - sie
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
          ) should be_regular_file
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
          bundle should include("\"status\" : \"source-only\"")
          bundle should include("\"status\" : \"undistributed\"")
          bundle should include("\"sourceOnly\" : true")
          bundle should not include (
            "CAR artifact is not registered in artifact repository"
          )
          bundle should not include ("Run cozy publish-car <nict-knowledgehub>")
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
          bundle should include(""""terms" : [ "semantic integration" ]""")
          bundle should include(""""tags" : [ "sie" ]""")
          val sourcefiles = Files
            .walk(pkg)
            .iterator()
            .asScala
            .toVector
            .filter(Files.isRegularFile(_))
            .map(_.getFileName.toString)
          sourcefiles should not_contain_where[String](_.endsWith(".car"))
          sourcefiles should not_contain_where[String](_.endsWith(".ttl"))
          sourcefiles should not_contain_where[String](_.endsWith(".jsonld"))
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
          ) shouldNot exist_path
          dir.resolve(
            "warehouse/repository/car/nict-knowledgehub/0.1.0/nict-knowledgehub-0.1.0.car"
          ) shouldNot exist_path
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

      "resolve project repository local from the CNCF local repository using project name and latest artifact version" in {
        _with_temp_dir("cozy-bok-project-local-repository-keyword") { dir =>
          Given(
            "a BoK CAR project configured to read a development CAR from the local CNCF repository"
          )
          val oldhome = System.getProperty("user.home")
          System.setProperty("user.home", dir.resolve("home").toString)
          try {
            val localrepo = dir.resolve("home/.cncf/local/repository")
            _write(
              localrepo.resolve(
                "car/nict-knowledgehub/0.3.0-SNAPSHOT/nict-knowledgehub-0.3.0-SNAPSHOT.car"
              ),
              "local-car-body"
            )
            _write(
              dir.resolve("conf/cozy/config.yaml"),
              """bok:
                |  projects:
                |    nict-knowledgehub:
                |      repository: local
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
                |title: NICT KnowledgeHub
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
              "the local repository keyword supplies the CAR name, latest artifact version, and artifact path"
            )
            results.head.project.module shouldBe "nict-knowledgehub"
            results.head.project.version shouldBe "0.3.0-SNAPSHOT"
            results.head.project.versionsource shouldBe "repository-artifact"
            results.head.artifact shouldBe localrepo
              .resolve(
                "car/nict-knowledgehub/0.3.0-SNAPSHOT/nict-knowledgehub-0.3.0-SNAPSHOT.car"
              )
              .toAbsolutePath
              .normalize()
            results.head.artifactexists shouldBe true
            val bundle =
              _read(dir.resolve("src/main/publication/nict-knowledgehub.json"))
            bundle should include("0.3.0-SNAPSHOT")
            bundle should include("\"versionSource\" : \"repository-artifact\"")
            bundle should include(
              "repository/car/nict-knowledgehub/0.3.0-SNAPSHOT/nict-knowledgehub-0.3.0-SNAPSHOT.car"
            )
          } finally {
            if (oldhome == null)
              System.clearProperty("user.home")
            else
              System.setProperty("user.home", oldhome)
          }
        }
      }

      "resolve explicit path project repository from the configured development directory" in {
        _with_temp_dir("cozy-bok-project-path-repository-keyword") { dir =>
          Given(
            "a BoK CAR project configured to read CML directly from a project directory"
          )
          val externalproject = dir.resolve("external/nict-knowledgehub")
          _write(
            externalproject.resolve("src/main/cozy/nict-knowledgehub.cml"),
            """# ENTITY
              |
              |## PathModeEntity
              |""".stripMargin
          )
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            s"""bok:
               |  projects:
               |    nict-knowledgehub:
               |      repository: path
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
              |version: 0.1.0-SNAPSHOT
              |title: NICT KnowledgeHub
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

          Then("the path repository keyword enables direct CML scanning")
          val bundle =
            _read(dir.resolve("src/main/publication/nict-knowledgehub.json"))
          bundle should include("direct-cml-scan")
          bundle should include("PathModeEntity")
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
              |  "surface": {
              |    "component": {
              |      "name": "CatalogComponent",
              |      "services": []
              |    }
              |  },
              |  "modelElements": [
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
               |      repository: path
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

    "publish SIE Project handoff metadata" which {
      "normalize the public handoff and exclude private local paths" in {
        _with_temp_dir("cozy-bok-sie-project") { dir =>
          Given("an SIE-linked BoK Project with public handoff metadata and a private local projection path")
          val localpath = _write_sie_project_source(
            dir,
            "https://sie.example.com/projections/../nict-knowledgehub"
          )

          When("Cozy registers the Project in the publication registry")
          CozyBok.publishProjects(CozyBok.PublicationConfig.create("publish-projects", List(dir.toString)))

          Then("the publication metadata contains the normalized SIE projection and manifest contract")
          val bundle = _read(dir.resolve("src/main/publication/nict-knowledgehub.json"))
          bundle should include(""""projection" : "nict-knowledgehub"""")
          bundle should include(""""component" : "textus-semantic-integration-engine"""")
          bundle should include(""""subsystem" : "nict-knowledgehub-runtime"""")
          bundle should include(""""handoffBase" : "https://sie.example.com/nict-knowledgehub/"""")
          bundle should include(""""manifest" : "https://sie.example.com/nict-knowledgehub/metadata/cncf/knowledge-source.json"""")
          bundle should include(""""kind" : "car"""")
          bundle should include(""""kind" : "sar"""")
          bundle should include(""""recommended" : "0.1.0"""")
          bundle should include(""""recommended" : "1.0.0"""")
          bundle should not include ("sie.project.component.unresolved")
          bundle should not include ("sie.project.subsystem.unresolved")
          bundle should not include ("sie.project.artifact.recommended.missing")
          bundle should not include ("sie.project.artifact.latest-stable.missing")

          And("private local SIE paths are not copied into public metadata")
          bundle should not include (localpath.toString)

          When("Cozy builds the SIE-linked Project page")
          val runner = new ProjectBuildRunner
          CozyBok.build(
            CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--no-bib-service")),
            runner
          )

          Then("the Project page exposes the registered SIE linkage without invoking an SIE client")
          val page = _read(dir.resolve("website.d/projects/technology/nict-knowledgehub/index.html"))
          page should include("SIE linkage")
          page should include("<code>nict-knowledgehub</code>")
          page should include("<code>textus-semantic-integration-engine</code>")
          page should include("<code>nict-knowledgehub-runtime</code>")
          page should include("href=\"https://sie.example.com/nict-knowledgehub/\"")
          page should include("href=\"https://sie.example.com/nict-knowledgehub/metadata/cncf/knowledge-source.json\"")
          page should include("../../../repository/car/textus-semantic-integration-engine/0.1.0.html")
          page should include("../../../repository/sar/nict-knowledgehub-runtime/1.0.0.html")
          page should include("Recommended")
          page should include("Latest stable")
          And("the resolved SAR catalog has deterministic index, module, and version pages linked back to the Project")
          val sarindex = dir.resolve("website.d/repository/sar/index.html")
          val sarmodule = dir.resolve("website.d/repository/sar/nict-knowledgehub-runtime/index.html")
          val sarversion = dir.resolve("website.d/repository/sar/nict-knowledgehub-runtime/1.0.0.html")
          sarindex should exist_path
          sarmodule should exist_path
          sarversion should exist_path
          _read(sarindex) should include("nict-knowledgehub-runtime")
          _read(sarmodule) should include("1.0.0.html")
          _read(sarversion) should include("../../../projects/technology/nict-knowledgehub/index.html")
          And("the SIE Project reuses the generic CML, term, scenario, tag, and RDF relations")
          page should include("Related BoK knowledge")
          page should include("#project-model-terms")
          page should include("Knowledge Item")
          page should include("../../../glossary/technology/knowledge-item.html")
          page should include("Knowledge Review Scenario")
          page should include("../../../scenario/technology/knowledge-review.html")
          page should include("../../../tags/technology/sie.html")
          page should include("../../../tags/workflow/review.html")
          page should include("../../../rdf/index.html?term=technology%3Aknowledge-item")
          val technologytag = _read(dir.resolve("website.d/tags/technology/sie.html"))
          technologytag should include("NICT KnowledgeHub")
          technologytag should include("../../projects/technology/nict-knowledgehub/index.html")
          val workflowtag = _read(dir.resolve("website.d/tags/workflow/review.html"))
          workflowtag should include("NICT KnowledgeHub")
          And("manifest-declared SIE Information metadata is merged into the effective BoK metadata and UI")
          val integration = _read(dir.resolve("website.d/metadata/sie/integration.json"))
          integration should include("cozy.bok.sie-integration.v1")
          integration should include("knowledge-item-information-v1")
          integration should include("knowledge-item-001")
          integration should not include (localpath.toString)
          val graph = _read(dir.resolve("website.d/metadata/rdf/graph.json"))
          graph should include("knowledge-item-information-v1")
          graph should include("knowledge-item-001")
          graph should include("\"projection\" : \"nict-knowledgehub\"")
          page should include("SIE Information")
          page should include("Knowledge Item Information")
          val termpage = _read(dir.resolve("website.d/glossary/technology/knowledge-item.html"))
          termpage should include("SmartDox term narrative")
          termpage should include("bok-term-sie-information")
          termpage should include("Knowledge Item Information")
          val scenariopage = _read(dir.resolve("website.d/scenario/technology/knowledge-review.html"))
          scenariopage should include("SmartDox scenario narrative")
          scenariopage should include("bok-scenario-sie-information")
          scenariopage should include("Knowledge Item Information")
          technologytag should include("Knowledge Item Information")
          technologytag should include("sie-information")
          _read(dir.resolve("website.d/rdf/node.html")) should include("<dt>Projection</dt>")
          runner.commands.count(_.take(2) == Vector("dox", "antora")) shouldBe 1
          runner.commands.count(_.take(2) == Vector("dox", "site")) shouldBe 1
          runner.commands.flatten should not contain ("https://sie.example.com/nict-knowledgehub/")
          runner.commands.flatten should not contain ("https://sie.example.com/nict-knowledgehub/metadata/cncf/knowledge-source.json")
        }
      }

      "omit CML relation links when the Project has no model rows" in {
        _with_temp_dir("cozy-bok-sie-project-empty-model") { dir =>
          Given("an SIE-linked BoK Project whose CML metadata contains no model rows")
          _write_sie_project_source(dir, "https://sie.example.com/nict-knowledgehub/")
          _write(
            dir.resolve("repository/catalog/car/nict-knowledgehub.model-metadata.json"),
            """{
              |  "source": {"path": "src/main/cozy/nict-knowledgehub.cml"},
              |  "modelElements": []
              |}
              |""".stripMargin
          )

          When("Cozy builds the SIE-linked Project page")
          CozyBok.build(
            CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--no-bib-service")),
            new ProjectBuildRunner
          )

          Then("the SIE handoff remains visible without a broken CML section link")
          val page = _read(dir.resolve("website.d/projects/technology/nict-knowledgehub/index.html"))
          page should include("SIE linkage")
          page should not include ("href=\"#project-model-terms\"")
          page should not include ("id=\"project-model-terms\"")
        }
      }

      "diagnose a configured local handoff whose manifest is missing" in {
        _with_temp_dir("cozy-bok-sie-handoff-missing") { dir =>
          Given("an SIE-linked Project with an explicit local handoff path but no handoff manifest")
          val localpath = _write_sie_project_source(dir, "https://sie.example.com/nict-knowledgehub/")
          Files.delete(localpath.resolve("metadata/cncf/knowledge-source.json"))

          When("Cozy builds without contacting the public SIE service")
          val runner = new ProjectBuildRunner
          CozyBok.build(
            CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--no-bib-service")),
            runner
          )

          Then("the machine-readable integration metadata and Project page expose the stable missing-handoff diagnostic")
          val integration = _read(dir.resolve("website.d/metadata/sie/integration.json"))
          integration should include("sie.handoff.missing")
          integration should include("metadata/cncf/knowledge-source.json")
          integration should not include (localpath.toString)
          val page = _read(dir.resolve("website.d/projects/technology/nict-knowledgehub/index.html"))
          page should include("SIE integration diagnostics")
          page should include("sie.handoff.missing")
          runner.commands.flatten should not contain ("https://sie.example.com/nict-knowledgehub/metadata/cncf/knowledge-source.json")
        }
      }

      "reject non-local resource hrefs at the SIE handoff boundary" in {
        Vector(
          "information-schema" -> "../private.json",
          "information-schema" -> "https://sie.example.com/private.json",
          "future-optional" -> "https://sie.example.com/future.json"
        ).zipWithIndex.foreach { case ((resourcekind, invalidhref), index) =>
          _with_temp_dir(s"cozy-bok-sie-handoff-path-${index}") { dir =>
            Given(s"an SIE manifest that declares non-local resource href ${invalidhref}")
            val localpath = _write_sie_project_source(dir, "https://sie.example.com/nict-knowledgehub/")
            _write(
              localpath.resolve("metadata/cncf/knowledge-source.json"),
              s"""{
                 |  "schemaVersion": "cncf.knowledge-source.v1",
                 |  "kind": "sie-projection",
                 |  "id": "nict-knowledgehub",
                 |  "sourceRef": {"kind": "sie-projection", "value": "nict-knowledgehub"},
                 |  "resources": [
                 |    {"kind": "${resourcekind}", "href": "${invalidhref}", "mediaType": "application/json"}
                 |  ]
                 |}
                 |""".stripMargin
            )

            When("Cozy validates the handoff during build")
            CozyBok.build(
              CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--no-bib-service")),
              new ProjectBuildRunner
            )

            Then("the href is reported as invalid and no local path is published")
            val integration = _read(dir.resolve("website.d/metadata/sie/integration.json"))
            integration should include("sie.handoff.invalid")
            integration should include("safe relative path")
            integration should not include (localpath.toString)
          }
        }
      }

      "reject duplicate singleton resources in an SIE handoff" in {
        _with_temp_dir("cozy-bok-sie-handoff-duplicate") { dir =>
          Given("an SIE manifest that declares two information schema resources")
          val localpath = _write_sie_project_source(dir, "https://sie.example.com/nict-knowledgehub/")
          val manifestpath = localpath.resolve("metadata/cncf/knowledge-source.json")
          val manifest = _read(manifestpath).replace(
            """{"kind": "information-schema", "href": "metadata/sie/information-schema.json", "mediaType": "application/json"},""",
            """{"kind": "information-schema", "href": "metadata/sie/information-schema.json", "mediaType": "application/json"},
              |    {"kind": "information-schema", "href": "metadata/sie/information-schema.json", "mediaType": "application/json"},""".stripMargin
          )
          _write(manifestpath, manifest)

          When("Cozy validates the handoff during build")
          CozyBok.build(
            CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--no-bib-service")),
            new ProjectBuildRunner
          )

          Then("the duplicate kind is reported instead of selecting one declaration")
          val integration = _read(dir.resolve("website.d/metadata/sie/integration.json"))
          integration should include("sie.handoff.invalid")
          integration should include("must be declared at most once: information-schema")
          integration should not include (localpath.toString)
        }
      }

      "skip an unknown optional resource without reading its href" in {
        _with_temp_dir("cozy-bok-sie-handoff-optional-resource") { dir =>
          Given("an SIE manifest that declares an unknown optional resource whose file is absent")
          val localpath = _write_sie_project_source(dir, "https://sie.example.com/nict-knowledgehub/")
          val manifestpath = localpath.resolve("metadata/cncf/knowledge-source.json")
          val manifest = _read(manifestpath).replace(
            """    {"kind": "rdf-graph-summary", "href": "metadata/rdf/graph.json", "mediaType": "application/json"}""",
            """    {"kind": "rdf-graph-summary", "href": "metadata/rdf/graph.json", "mediaType": "application/json"},
              |    {"kind": "future-optional", "href": "private/missing.json", "mediaType": "application/json"}""".stripMargin
          )
          _write(manifestpath, manifest)

          When("Cozy validates the handoff during build")
          CozyBok.build(
            CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--no-bib-service")),
            new ProjectBuildRunner
          )

          Then("the unknown kind is warning-only and does not authorize a filesystem read")
          val integration = _read(dir.resolve("website.d/metadata/sie/integration.json"))
          integration should include("sie.handoff.resource.unsupported")
          integration should include("future-optional")
          integration should not include ("sie.handoff.resource.missing")
        }
      }

      "require mediaType on every SIE handoff resource" in {
        _with_temp_dir("cozy-bok-sie-handoff-media-type") { dir =>
          Given("an SIE manifest resource without mediaType")
          val localpath = _write_sie_project_source(dir, "https://sie.example.com/nict-knowledgehub/")
          val manifestpath = localpath.resolve("metadata/cncf/knowledge-source.json")
          val manifest = _read(manifestpath).replace(
            """{"kind": "information-schema", "href": "metadata/sie/information-schema.json", "mediaType": "application/json"}""",
            """{"kind": "information-schema", "href": "metadata/sie/information-schema.json"}"""
          )
          _write(manifestpath, manifest)

          When("Cozy validates the manifest envelope")
          CozyBok.build(
            CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--no-bib-service")),
            new ProjectBuildRunner
          )

          Then("the malformed resource is rejected before handoff files are loaded")
          val integration = _read(dir.resolve("website.d/metadata/sie/integration.json"))
          integration should include("sie.handoff.invalid")
          integration should include("require kind, href, and mediaType")
        }
      }

      "exclude Information instances that reference an undefined schema" in {
        _with_temp_dir("cozy-bok-sie-handoff-undefined-schema") { dir =>
          Given("an SIE Information instance that names a schema absent from its handoff")
          val localpath = _write_sie_project_source(dir, "https://sie.example.com/nict-knowledgehub/")
          val instancespath = localpath.resolve("metadata/sie/information-instances.json")
          _write(
            instancespath,
            _read(instancespath).replace(
              "\"schema\": \"knowledge-item-information-v1\"",
              "\"schema\": \"undefined-information-v1\""
            )
          )

          When("Cozy validates Information instances against the declared schema resource")
          CozyBok.build(
            CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--no-bib-service")),
            new ProjectBuildRunner
          )

          Then("the invalid instance is diagnosed and omitted from effective SIE metadata")
          val integration = _read(dir.resolve("website.d/metadata/sie/integration.json"))
          integration should include("references undefined schema: undefined-information-v1")
          integration should not include ("\"id\" : \"knowledge-item-001\"")
          _read(dir.resolve("website.d/glossary/technology/knowledge-item.html")) should not include (
            "bok-term-sie-information"
          )
        }
      }

      "reject duplicate Information instance ids" in {
        _with_temp_dir("cozy-bok-sie-handoff-duplicate-instance") { dir =>
          Given("an SIE Information resource with two entries that share one logical id")
          val localpath = _write_sie_project_source(dir, "https://sie.example.com/nict-knowledgehub/")
          val instancespath = localpath.resolve("metadata/sie/information-instances.json")
          val instance = """    {
                           |      "id": "knowledge-item-001",
                           |      "schema": "knowledge-item-information-v1",
                           |      "label": "Knowledge Item Information",
                           |      "summary": "SIE materialized knowledge item.",
                           |      "rdfNode": "https://example.com/knowledge-item",
                           |      "category": "technology",
                           |      "termRefs": ["technology:knowledge-item"],
                           |      "scenarioRefs": ["scenario:knowledge-review"],
                           |      "projectRefs": ["nict-knowledgehub"],
                           |      "tags": ["technology.sie", "workflow.review"]
                           |    }""".stripMargin
          _write(
            instancespath,
            _read(instancespath).replace(instance, s"${instance},\n${instance}")
          )

          When("Cozy decodes the Information instance collection")
          CozyBok.build(
            CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--no-bib-service")),
            new ProjectBuildRunner
          )

          Then("the duplicate identity is rejected instead of being merged or repeated")
          val integration = _read(dir.resolve("website.d/metadata/sie/integration.json"))
          integration should include("Information instance ids must be unique: knowledge-item-001")
          integration should not include ("\"id\" : \"knowledge-item-001\"")
        }
      }

      "reject a manifest resource that escapes through a symbolic link" in {
        _with_temp_dir("cozy-bok-sie-handoff-symbolic-link") { dir =>
          Given("a manifest-local path whose symbolic link resolves outside the SIE handoff root")
          val localpath = _write_sie_project_source(dir, "https://sie.example.com/nict-knowledgehub/")
          val externalpath = dir.resolve("outside-information-schema.json")
          _write(externalpath, "{}\n")
          val linkpath = localpath.resolve("metadata/sie/outside-information-schema.json")
          Files.createSymbolicLink(linkpath, externalpath)
          val manifestpath = localpath.resolve("metadata/cncf/knowledge-source.json")
          _write(
            manifestpath,
            _read(manifestpath).replace(
              "metadata/sie/information-schema.json",
              "metadata/sie/outside-information-schema.json"
            )
          )

          When("Cozy resolves the declared resource below the trusted handoff root")
          CozyBok.build(
            CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--no-bib-service")),
            new ProjectBuildRunner
          )

          Then("the symlink escape is rejected without publishing the external path")
          val integration = _read(dir.resolve("website.d/metadata/sie/integration.json"))
          integration should include("sie.handoff.invalid")
          integration should include("safe relative path")
          integration should not include (externalpath.toString)
        }
      }

      "fail when duplicate RDF edges carry conflicting metadata" in {
        _with_temp_dir("cozy-bok-sie-handoff-edge-conflict") { dir =>
          Given("an SIE graph summary with one edge identity and two different metadata records")
          val localpath = _write_sie_project_source(dir, "https://sie.example.com/nict-knowledgehub/")
          val graphpath = localpath.resolve("metadata/rdf/graph.json")
          val edge = """{"source": "https://example.com/knowledge-item", "target": "https://schema.org/name", "predicate": "https://schema.org/name", "label": "name", "category": "technology"}"""
          _write(
            graphpath,
            _read(graphpath).replace(
              edge,
              s"""${edge},
                 |    {"source": "https://example.com/knowledge-item", "target": "https://schema.org/name", "predicate": "https://schema.org/name", "label": "name", "category": "concept"}""".stripMargin
            )
          )

          When("Cozy merges the SIE graph summary into the SmartDox graph")
          val e = intercept[Throwable] {
            CozyBok.build(
              CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--no-bib-service")),
              new ProjectBuildRunner
            )
          }

          Then("the conflicting edge is reported instead of choosing one record")
          e.getMessage should include("Conflicting RDF graph edge metadata")
        }
      }

      "keep relation tag links inside each locale subtree" in {
        _with_temp_dir("cozy-bok-sie-project-multi-locale") { dir =>
          Given("an SIE-linked BoK Project published into Japanese and English locale subdirectories")
          _write_sie_project_source(dir, "https://sie.example.com/nict-knowledgehub/")
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            """site {
              |  metadata {
              |    in_language = ["ja", "en"]
              |  }
              |  output {
              |    locale_mode = "multi_locale_subdirs"
              |    default_locale = "ja"
              |  }
              |}
              |""".stripMargin
          )

          When("Cozy builds each localized SIE Project page")
          CozyBok.build(
            CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--no-bib-service")),
            new ProjectBuildRunner
          )

          Then("Project, tag tree, and SIE Information links stay within the owning locale")
          Vector("ja", "en").foreach { locale =>
            val page = _read(dir.resolve(s"website.d/${locale}/projects/technology/nict-knowledgehub/index.html"))
            page should include("href=\"../../../tags/technology/sie.html\"")
            page should include("href=\"../../../tags/workflow/review.html\"")
            dir.resolve(s"website.d/${locale}/tags/technology/sie.html") should exist_path
            dir.resolve(s"website.d/${locale}/tags/workflow/review.html") should exist_path
            val tagindex = _read(dir.resolve(s"website.d/${locale}/tags/index.html"))
            tagindex should include("href=\"technology/index.html\"")
            val tagpage = _read(dir.resolve(s"website.d/${locale}/tags/technology/sie.html"))
            tagpage should include("href=\"../../rdf/node.html?id=https%3A%2F%2Fexample.com%2Fknowledge-item\"")
          }
        }
      }

      "materialize generic CAR pages from a development repository" in {
        _with_temp_dir("cozy-bok-sie-project-local-component") { dir =>
          Given("an SIE-linked Project whose component and subsystem catalogs come from the CNCF local repository")
          val oldhome = System.getProperty("user.home")
          System.setProperty("user.home", dir.resolve("home").toString)
          try {
            val localrepo = dir.resolve("home/.cncf/local/repository")
            val localpath = _write_sie_project_source(
              dir,
              "https://sie.example.com/nict-knowledgehub/",
              withcomponentcatalog = false,
              withsubsystemcatalog = false
            )
            _write(
              localrepo.resolve("catalog/car/textus-semantic-integration-engine.json"),
              _sie_component_catalog_json("0.1.0")
            )
            _write(
              localrepo.resolve("catalog/sar/nict-knowledgehub-runtime.json"),
              _sie_subsystem_catalog_json("1.0.0")
            )
            _write(
              dir.resolve("conf/cozy/config.yaml"),
              s"""bok:
                 |  projects:
                 |    nict-knowledgehub:
                 |      repository: local
                 |      sie:
                 |        path: ${localpath.toString}
                 |""".stripMargin
            )

            When("Cozy builds repository knowledge from the resolved SIE artifacts")
            CozyBok.build(
              CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--no-bib-service")),
              new ProjectBuildRunner
            )

            Then("the local SIE component is included in the generic CAR index and version pages")
            val projectpage = _read(dir.resolve("website.d/projects/technology/nict-knowledgehub/index.html"))
            projectpage should include("../../../repository/car/textus-semantic-integration-engine/0.1.0.html")
            val carindex = dir.resolve("website.d/repository/car/index.html")
            val carversion = dir.resolve("website.d/repository/car/textus-semantic-integration-engine/0.1.0.html")
            carindex should exist_path
            carversion should exist_path
            _read(carindex) should include("textus-semantic-integration-engine")
            _read(carversion) should include("../../../projects/technology/nict-knowledgehub/index.html")
            _read(carversion) should not include (localrepo.toString)

            And("the KnowledgeSource publishes CAR and SAR existence indexes without CBD detail")
            val carreferences = _read(dir.resolve("website.d/metadata/cncf/component-references/car.json"))
            val sarreferences = _read(dir.resolve("website.d/metadata/cncf/component-references/sar.json"))
            carreferences should include("cncf.component-reference-index.v1")
            carreferences should include("textus-semantic-integration-engine")
            sarreferences should include("cncf.component-reference-index.v1")
            sarreferences should include("nict-knowledgehub-runtime")
            val knowledgesource = _read(dir.resolve("website.d/metadata/cncf/knowledge-source.json"))
            knowledgesource should include("metadata/cncf/component-references/car.json")
            knowledgesource should include("metadata/cncf/component-references/sar.json")
          } finally {
            if (oldhome == null)
              System.clearProperty("user.home")
            else
              System.setProperty("user.home", oldhome)
          }
        }
      }

      "publish project-backed CAR component references without repository catalogs" in {
        _with_temp_dir("cozy-bok-project-backed-component-reference") { dir =>
          Given("a BoK CAR Project whose component identity exists only as project metadata")
          val oldhome = System.getProperty("user.home")
          System.setProperty("user.home", dir.resolve("home").toString)
          try {
            _write_sie_project_source(
              dir,
              "https://sie.example.com/nict-knowledgehub/",
              withcomponentcatalog = false,
              withsubsystemcatalog = false
            )

            When("Cozy builds the BoK site")
            CozyBok.build(
              CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--no-bib-service")),
              new ProjectBuildRunner
            )

            Then("the KnowledgeSource publishes a CAR component-reference index from the project metadata")
            val carreferences = _read(dir.resolve("website.d/metadata/cncf/component-references/car.json"))
            val carjson = parser.parse(carreferences).fold(throw _, identity)
            carjson.hcursor.get[String]("schemaVersion") shouldBe Right("cncf.component-reference-index.v1")
            val entries = carjson.hcursor.get[Vector[io.circe.Json]]("entries").fold(throw _, identity)
            entries should have size 1
            val entry = entries.head.hcursor
            entry.get[String]("name") shouldBe Right("nict-knowledgehub")
            entry.get[String]("title") shouldBe Right("NICT KnowledgeHub")
            entry.get[String]("source_path") shouldBe
              Right("src/main/doxsite/projects/technology/nict-knowledgehub/project.yaml")
            entry.get[String]("public_path") shouldBe
              Right("projects/technology/nict-knowledgehub/index.html")
            val versions = entry.get[Vector[io.circe.Json]]("versions").fold(throw _, identity)
            versions should have size 1
            versions.head.hcursor.get[String]("version") shouldBe Right("0.1.0")
            versions.head.hcursor.get[Option[String]]("file") shouldBe Right(None)

            And("the component-reference index is advertised without requiring CBD detail")
            val knowledgesource = _read(dir.resolve("website.d/metadata/cncf/knowledge-source.json"))
            knowledgesource should include("metadata/cncf/component-references/car.json")
            knowledgesource should not include ("metadata/cncf/component-references/sar.json")
          } finally {
            if (oldhome == null)
              System.clearProperty("user.home")
            else
              System.setProperty("user.home", oldhome)
          }
        }
      }

      "reject duplicate project-backed CAR component identities" in {
        _with_temp_dir("cozy-bok-project-backed-component-reference-duplicate") { dir =>
          Given("two BoK Project packages that claim the same CAR component identity")
          val oldhome = System.getProperty("user.home")
          System.setProperty("user.home", dir.resolve("home").toString)
          try {
            _write_sie_project_source(
              dir,
              "https://sie.example.com/nict-knowledgehub/",
              withcomponentcatalog = false,
              withsubsystemcatalog = false
            )
            val original = dir.resolve("src/main/doxsite/projects/technology/nict-knowledgehub")
            val duplicate = dir.resolve("src/main/doxsite/projects/concept/nict-knowledgehub-copy")
            _write(duplicate.resolve("index.dox"), "Duplicate NictKnowledgeHub\n==========================\n")
            _write(duplicate.resolve("project.yaml"), _read(original.resolve("project.yaml")))

            When("Cozy builds the component-reference index")
            val error = intercept[Throwable] {
              CozyBok.build(
                CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--no-bib-service")),
                new ProjectBuildRunner
              )
            }

            Then("the duplicate identity fails instead of selecting one project by path order")
            error.getMessage should include(
              "Conflicting BoK CAR project component-reference identities: nict-knowledgehub"
            )
          } finally {
            if (oldhome == null)
              System.clearProperty("user.home")
            else
              System.setProperty("user.home", oldhome)
          }
        }
      }

      "reject conflicting SIE catalogs for one artifact identity" in {
        _with_temp_dir("cozy-bok-sie-project-conflicting-subsystem") { dir =>
          Given("public and local SIE Projects resolve different SAR catalogs with the same artifact id")
          val oldhome = System.getProperty("user.home")
          System.setProperty("user.home", dir.resolve("home").toString)
          try {
            val localrepo = dir.resolve("home/.cncf/local/repository")
            val localpath = _write_sie_project_source(dir, "https://sie.example.com/nict-knowledgehub/")
            _write(
              localrepo.resolve("catalog/car/textus-semantic-integration-engine.json"),
              _sie_component_catalog_json("0.1.0")
            )
            _write(
              localrepo.resolve("catalog/sar/nict-knowledgehub-runtime.json"),
              _sie_subsystem_catalog_json("2.0.0")
            )
            val pkg = dir.resolve("src/main/doxsite/projects/technology/nict-local")
            _write(pkg.resolve("index.dox"), "NictLocal\n=========\n")
            _write(
              pkg.resolve("project.yaml"),
              """project:
                |  type: car
                |  name: nict-local
                |  mode: external
                |  ref: nict-local
                |car:
                |  module: nict-local
                |sie:
                |  projection: nict-local
                |  component: textus-semantic-integration-engine
                |  subsystem: nict-knowledgehub-runtime
                |  handoff_base: https://sie.example.com/nict-local/
                |title: NICT Local
                |version: 0.1.0
                |article: index.dox
                |""".stripMargin
            )
            _write(
              dir.resolve("conf/cozy/config.yaml"),
              s"""bok:
                 |  projects:
                 |    nict-knowledgehub:
                 |      sie:
                 |        path: ${localpath.toString}
                 |    nict-local:
                 |      repository: local
                 |""".stripMargin
            )

            When("Cozy builds the shared SIE repository knowledge pages")
            val error = intercept[Throwable] {
              CozyBok.build(
                CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--no-bib-service")),
                new ProjectBuildRunner
              )
            }

            Then("the conflicting catalog identity is rejected instead of selecting one path arbitrarily")
            error.getMessage should include("Conflicting repository SAR catalogs for SIE artifact: nict-knowledgehub-runtime")
          } finally {
            if (oldhome == null)
              System.clearProperty("user.home")
            else
              System.setProperty("user.home", oldhome)
          }
        }
      }

      "diagnose an SIE component that is absent from the repository catalog" in {
        _with_temp_dir("cozy-bok-sie-project-unresolved-component") { dir =>
          Given("an SIE-linked BoK Project whose component has no repository catalog entry")
          val localpath = _write_sie_project_source(
            dir,
            "https://sie.example.com/nict-knowledgehub/",
            withcomponentcatalog = false
          )

          When("Cozy registers the Project in the publication registry")
          CozyBok.publishProjects(CozyBok.PublicationConfig.create("publish-projects", List(dir.toString)))

          Then("the public manifest reports a stable unresolved-component diagnostic without leaking local paths")
          val bundle = _read(dir.resolve("src/main/publication/nict-knowledgehub.json"))
          bundle should include(""""code" : "sie.project.component.unresolved"""")
          bundle should include("SIE component is not registered in the repository CAR catalog: textus-semantic-integration-engine")
          bundle should include("repository/catalog/car/textus-semantic-integration-engine.yaml")
          bundle should not include (localpath.toString)
        }
      }

      "diagnose malformed and mismatched SIE component catalogs as unresolved" in {
        Vector(
          "malformed" -> "{ not-json",
          "mismatched" ->
            """{
              |  "schemaVersion": "1",
              |  "kind": "car",
              |  "artifactId": "another-component",
              |  "versions": []
              |}
              |""".stripMargin
        ).foreach { case (label, catalogbody) =>
          _with_temp_dir(s"cozy-bok-sie-project-${label}-component") { dir =>
            Given(s"an SIE-linked BoK Project with a ${label} component catalog")
            _write_sie_project_source(
              dir,
              "https://sie.example.com/nict-knowledgehub/",
              withcomponentcatalog = false
            )
            _write(
              dir.resolve("repository/catalog/car/textus-semantic-integration-engine.json"),
              catalogbody
            )

            When("Cozy registers the Project in the publication registry")
            CozyBok.publishProjects(CozyBok.PublicationConfig.create("publish-projects", List(dir.toString)))

            Then("the invalid catalog does not satisfy the SIE component reference")
            val bundle = _read(dir.resolve("src/main/publication/nict-knowledgehub.json"))
            bundle should include(""""code" : "sie.project.component.unresolved"""")
          }
        }
      }

      "diagnose an SIE subsystem that is absent from the repository catalog" in {
        _with_temp_dir("cozy-bok-sie-project-unresolved-subsystem") { dir =>
          Given("an SIE-linked BoK Project whose subsystem has no repository SAR catalog entry")
          _write_sie_project_source(
            dir,
            "https://sie.example.com/nict-knowledgehub/",
            withsubsystemcatalog = false
          )

          When("Cozy registers the Project in the publication registry")
          CozyBok.publishProjects(CozyBok.PublicationConfig.create("publish-projects", List(dir.toString)))

          Then("the public manifest reports a stable unresolved-subsystem diagnostic")
          val bundle = _read(dir.resolve("src/main/publication/nict-knowledgehub.json"))
          bundle should include(""""code" : "sie.project.subsystem.unresolved"""")
          bundle should include("SIE subsystem is not registered in the repository SAR catalog: nict-knowledgehub-runtime")
          bundle should include("repository/catalog/sar/nict-knowledgehub-runtime.yaml")
        }
      }

      "diagnose missing recommended and latest-stable selectors" in {
        _with_temp_dir("cozy-bok-sie-project-missing-selectors") { dir =>
          Given("an SIE-linked BoK Project whose SAR catalog has versions but no release selectors")
          _write_sie_project_source(dir, "https://sie.example.com/nict-knowledgehub/")
          _write(
            dir.resolve("repository/catalog/sar/nict-knowledgehub-runtime.json"),
            """{
              |  "schemaVersion": "1",
              |  "kind": "sar",
              |  "artifactId": "nict-knowledgehub-runtime",
              |  "versions": [
              |    {
              |      "version": "1.0.0",
              |      "channel": "stable",
              |      "file": "repository/sar/nict-knowledgehub-runtime/1.0.0/nict-knowledgehub-runtime-1.0.0.sar"
              |    }
              |  ]
              |}
              |""".stripMargin
          )

          When("Cozy registers the Project in the publication registry")
          CozyBok.publishProjects(CozyBok.PublicationConfig.create("publish-projects", List(dir.toString)))

          Then("the public manifest reports both missing selector diagnostics")
          val bundle = _read(dir.resolve("src/main/publication/nict-knowledgehub.json"))
          bundle should include(""""code" : "sie.project.artifact.recommended.missing"""")
          bundle should include(""""code" : "sie.project.artifact.latest-stable.missing"""")
          bundle should include("Set recommended in repository/catalog/sar/nict-knowledgehub-runtime")
          bundle should include("Set latestStable in repository/catalog/sar/nict-knowledgehub-runtime")
        }
      }

      "reject handoff values that cannot be safe HTTP resource bases" in {
        Vector(
          "https://sie.example.com/nict-knowledgehub?token=private",
          "https://sie.example.com/nict-knowledgehub#projection",
          "http:relative"
        ).foreach { handoffbase =>
          _with_temp_dir("cozy-bok-sie-project-invalid-handoff") { dir =>
            Given(s"an SIE-linked BoK Project with invalid handoff base $handoffbase")
            _write_sie_project_source(dir, handoffbase)

            When("Cozy registers the Project in the publication registry")
            val error = intercept[Throwable] {
              CozyBok.publishProjects(CozyBok.PublicationConfig.create("publish-projects", List(dir.toString)))
            }

            Then("the invalid public handoff is rejected before publication metadata is written")
            error.getMessage should include("SIE handoff base")
            dir.resolve("src/main/publication/nict-knowledgehub.json") shouldNot exist_path
          }
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
               |      repository: path
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
          ) should be_regular_file
          dir.resolve(
            "warehouse/repository/car/nict-knowledgehub/0.1.0/nict-knowledgehub-0.1.0.car"
          ) shouldNot exist_path
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
               |      repository: path
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
          dir.resolve("src/main/publication") shouldNot exist_path
          dir.resolve("warehouse") shouldNot exist_path
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
          catalogdir.resolve("nict-knowledgehub.yaml") should be_regular_file
          catalogdir.resolve("nict-knowledgehub.cml") should be_regular_file
          catalogdir.resolve(
            "nict-knowledgehub.model-metadata.json"
          ) should be_regular_file
          catalogdir.resolve(
            "nict-knowledgehub.model-metadata.yaml"
          ) should be_regular_file
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
              |  "surface": {
              |    "component": {
              |      "name": "CatalogComponent",
              |      "services": []
              |    }
              |  },
              |  "modelElements": [
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
            """# COMPONENT
              |
              |## NictKnowledgeHub
              |
              |### SUMMARY
              |
              |NICT KnowledgeHub component.
              |
              |# SERVICE
              |
              |## Knowledge
              |
              |### SUMMARY
              |
              |KnowledgeHub service operations.
              |
              |### OPERATION
              |
              |#### ingestKnowledge
              |
              |##### SUMMARY
              |Ingest a knowledge item.
              |
              |##### DESCRIPTION
              |Ingests authored or imported knowledge content.
              |
              |##### TYPE
              |COMMAND
              |
              |##### INPUT
              |
              |###### TYPE
              |IngestKnowledge
              |
              |##### OUTPUT
              |
              |###### TYPE
              |IngestKnowledgeResult
              |
              |#### searchKnowledge
              |
              |##### SUMMARY
              |Search knowledge items.
              |
              |##### DESCRIPTION
              |Searches KnowledgeHub items by query text.
              |
              |##### TYPE
              |QUERY
              |
              |##### INPUT
              |
              |###### TYPE
              |SearchKnowledge
              |
              |##### OUTPUT
              |
              |###### TYPE
              |SearchKnowledgeResult
              |
              |# ENTITY
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
               |      repository: path
               |      path: ${externalproject.toString}
               |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            "site { output { locale_mode = \"single_locale_root\" } }\n"
          )
          _write(dir.resolve("src/main/doxsite/index.dox"), "Home\n====\n")
          val termsjson =
            """{
              |  "terms": [
              |    {
              |      "id": "technology:knowledge-item",
              |      "slug": "knowledge-item",
              |      "title": "Knowledge Item",
              |      "reading": null,
              |      "category": "technology",
              |      "source_path": "glossary/technology/knowledge-item.dox",
              |      "public_path": "glossary/technology/knowledge-item.html",
              |      "definition_html": "<p>Knowledge Item term definition.</p>",
              |      "summary": "Glossary term linked to a CML entity.",
              |      "aliases": [],
              |      "term_type": "concept",
              |      "cml": [
              |        {"kind": "entity", "value": "KnowledgeItem"},
              |        {"kind": "operation", "value": "ingestKnowledge"}
              |      ],
              |      "article_refs": [],
              |      "term_refs": [],
              |      "rdf_refs": [],
              |      "video_refs": [],
              |      "quality": {"isolated": false, "unreferenced": false, "weakly_connected": false}
              |    }
              |  ]
              |}
              |""".stripMargin
          _write(dir.resolve("src/main/doxsite/metadata/glossary/terms.json"), termsjson)
          _write(dir.resolve("doxsite.d/metadata/glossary/terms.json"), termsjson)
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
          page should include("""<body class="bok-project-page""")
          page should not include ("""<body class="article""")
          page should include("プロジェクトDashboard")
          page should include("""class="bok-project-detail-dashboard"""")
          page should include("プロダクトメタデータ")
          page should not include ("知識リンク")
          page should include("提供インターフェース")
          page should not include ("Component / Service / Operation")
          page should include("""class="bok-project-component-tree"""")
          page should include("""class="bok-project-service-branch"""")
          page should include("""class="bok-project-operation-branch"""")
          page should include("Component")
          page should include("Service")
          page should include("Operation")
          page should include("Knowledge")
          page should include("ingestKnowledge")
          page should include("searchKnowledge")
          page should include("Ingest a knowledge item.")
          page should include("Search knowledge items.")
          page should not include ("CML metadataからserviceは検出されていません。")
          page should not include ("CML metadataからoperationは検出されていません。")
          page should include("NictKnowledgeHub CAR Product article body.")
          page should include("配布成果物")
          page should include("配布済")
          page should include("未配布")
          page should include("配布予定成果物")
          page should include("配布済成果物")
          page should include("配布済成果物は見つかりません。")
          page should include("CAR 1")
          page should include("SAR 0")
          page should include("JAR 0")
          page should include("repository/car/nict-knowledgehub/0.1.0/nict-knowledgehub-0.1.0.car")
          page should include("CMLモデル要素")
          page should include("種別内訳")
          page should include("Entity 1")
          page should include("CML要素と用語定義リンク")
          page should include("CMLソース")
          page should include("nict-knowledgehub.cml")
          page should include("src/main/cozy/nict-knowledgehub.cml")
          page should not include ("Source: direct-cml-scan")
          page should include("対象モデル要素")
          page should include("BoK用語リンク")
          page should include("あり")
          page should include("なし")
          page should include("Component(0/1)")
          page should include("Service(0/1)")
          page should include("Operation(1/2)")
          page should include("Entity(1/1)")
          page.indexOf("Component(0/1)") should be < page.indexOf("Service(0/1)")
          page.indexOf("Service(0/1)") should be < page.indexOf("Operation(1/2)")
          page.indexOf("Operation(1/2)") should be < page.indexOf("Entity(1/1)")
          page should include("""id="project-cml-panel-entity" class="bok-project-cml-tab-panel is-active"""")
          page should include("""data-project-cml-panel="Component" hidden""")
          page should include("<span class=\"bok-project-unlinked-term\">-</span>")
          page should include("""<thead><tr><th>モデル要素</th><th>BoK用語</th><th>シグネチャ</th><th>説明</th></tr></thead>""")
          page should include("""<thead><tr><th>モデル要素</th><th>BoK用語</th><th>説明</th></tr></thead>""")
          page should not include ("<th>区分</th>")
          page should not include ("<th>役割</th>")
          page should not include ("<th>機能</th>")
          page should include("提供インターフェース")
          page should include("COMMAND: IngestKnowledge -&gt; IngestKnowledgeResult")
          page should include("<td>Ingest a knowledge item.</td>")
          page should include("<td>Search knowledge items.</td>")
          page should not include ("機能 COMMAND: IngestKnowledge -&gt; IngestKnowledgeResult")
          page should not include ("COMMAND / IngestKnowledge / IngestKnowledgeResult")
          page should not include ("提供operation COMMAND / IngestKnowledge / IngestKnowledgeResult")
          page should include("KnowledgeItem")
          page should include("Knowledge Item")
          page should include("glossary/technology/knowledge-item.html")
          page should include("Knowledge item summary from CML.")
          dir.resolve("website.d/glossary/cml/knowledge-item.html") shouldNot exist_path
          val index = _read(dir.resolve("website.d/projects/index.html"))
          index should include("NICT KnowledgeHub")
          index should include("NICT KnowledgeHub CAR component project.")
          index should include("公開・連携状況")
          index should include("配布成果物")
          index should include("配布済 0")
          index should include("未配布 1")
          index should include("成果物種別")
          index should include("CAR 1")
          index should include("SAR 0")
          index should include("JAR 0")
          index should include("BoK内プロジェクト 0")
          index should include("外部プロジェクト 1")
          index should include("プロジェクト配置: 外部プロジェクト")
          index should include("CMLモデル要素 1")
          index should include("種別内訳")
          index should include("Entity 1")
          index should include("""class="bok-project-status bok-project-status-source-only"""")
          index should include("""data-project-category="concept"""")
          index should include("""href="../textus/components/nict-knowledgehub/index.html"""")
          index should include("nict-knowledgehub")
          val home = _read(dir.resolve("website.d/index.html"))
          home should include("""href="projects/index.html"""")
          home should include("プロジェクト")
          dir.resolve(
            "warehouse/repository/car/nict-knowledgehub/0.1.0/nict-knowledgehub-0.1.0.car"
          ) shouldNot exist_path
        }
      }

      "build materializes repository CAR catalog knowledge and diagnoses Project connection gaps" in {
        _with_temp_dir("cozy-bok-repository-car-build") { dir =>
          Given(
            "a BoK source tree with linked and unlinked repository CAR catalogs plus an unpublished Project"
          )
          val externalproject = dir.resolve("external/nict-knowledgehub")
          _write(
            externalproject.resolve("build.sbt"),
            "ThisBuild / version := \"0.2.0\"\n"
          )
          _write(
            externalproject.resolve("src/main/cozy/nict-knowledgehub.cml"),
            """# COMPONENT
              |
              |## NictKnowledgeHub
              |""".stripMargin
          )
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            s"""bok:
               |  projects:
               |    nict-knowledgehub:
               |      repository: path
               |      path: ${externalproject.toString}
               |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            "site { output { locale_mode = \"single_locale_root\" } }\n"
          )
          _write(dir.resolve("src/main/doxsite/index.dox"), "Home\n====\n")
          val pkg = dir.resolve(
            "src/main/doxsite/projects/technology/nict-knowledgehub"
          )
          _write(
            pkg.resolve("index.dox"),
            """NictKnowledgeHub
              |================
              |
              |NictKnowledgeHub project article body.
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
              |version: 0.2.0
              |summary: NICT KnowledgeHub CAR component project.
              |terms:
              |  - semantic integration
              |  - knowledge model
              |tags:
              |  - sie
              |  - knowledge
              |publication:
              |  path: textus/components/nict-knowledgehub
              |""".stripMargin
          )
          val unpublishedpkg = dir.resolve(
            "src/main/doxsite/projects/technology/unpublished-car"
          )
          _write(
            unpublishedpkg.resolve("index.dox"),
            """Unpublished CAR
              |===============
              |
              |A Project whose CAR has not been published.
              |""".stripMargin
          )
          _write(
            unpublishedpkg.resolve("project.yaml"),
            """project:
              |  type: car
              |  name: unpublished-car
              |  mode: internal
              |car:
              |  module: unpublished-car
              |title: Unpublished CAR Project
              |version: 0.1.0
              |publication:
              |  path: projects/technology/unpublished-car
              |""".stripMargin
          )
          _write(
            dir.resolve("repository/catalog/car/nict-knowledgehub.yaml"),
            """schemaVersion: 1
              |kind: car
              |artifactId: nict-knowledgehub
              |recommended: 0.2.0
              |latestStable: 0.2.0
              |latestSnapshot: 0.3.0-SNAPSHOT
              |aliases:
              |  - nict-kh
              |tags:
              |  - workflow.review
              |  - sie
              |terms:
              |  - Repository CAR
              |  - semantic integration
              |versions:
              |  - version: 0.2.0
              |    channel: stable
              |    status: active
              |    component: NictKnowledgeHub
              |    publishedAt: 2026-07-13T00:00:00Z
              |    file: repository/car/nict-knowledgehub/0.2.0/nict-knowledgehub-0.2.0.car
              |    runtime:
              |      cncf:
              |        minimum: 0.5.0
              |        tested:
              |          - 0.5.0
              |  - version: 0.3.0-SNAPSHOT
              |    channel: snapshot
              |    file: repository/car/nict-knowledgehub/0.3.0-SNAPSHOT/nict-knowledgehub-0.3.0-SNAPSHOT.car
              |""".stripMargin
          )
          _write(
            dir.resolve("repository/catalog/car/textus-sie.json"),
            """{
              |  "schemaVersion": "1",
              |  "kind": "car",
              |  "artifactId": "textus-sie",
              |  "latest_stable": "0.1.0",
              |  "aliases": ["semantic-integration-engine"],
              |  "tags": ["platform.sie"],
              |  "terms": ["semantic integration engine"],
              |  "versions": [
              |    {
              |      "version": "0.1.0",
              |      "channel": "stable",
              |      "file": "repository/car/textus-sie/0.1.0/textus-sie-0.1.0.car"
              |    }
              |  ]
              |}
              |""".stripMargin
          )
          _write(
            dir.resolve("repository/catalog/car/nict-knowledgehub.model-metadata.json"),
            """{
              |  "source": {
              |    "path": "repository/catalog/car/nict-knowledgehub.cml"
              |  },
              |  "component": {
              |    "name": "NictKnowledgeHub"
              |  }
              |}
              |""".stripMargin
          )
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--strategy", "preview", "--no-bib-service")
          )

          When("Cozy builds the BoK site")
          CozyBok.build(config, new ProjectBuildRunner)

          Then("repository CAR metadata is generated deterministically")
          val metadata = _read(
            dir.resolve("doxsite.d/metadata/repository/car/index.json")
          )
          metadata should include(""""artifact_id" : "nict-knowledgehub"""")
          metadata should include(""""artifact_id" : "textus-sie"""")
          metadata.indexOf("nict-knowledgehub") should be < metadata.indexOf("textus-sie")
          metadata should include(""""source_path" : "repository/catalog/car/nict-knowledgehub.yaml"""")
          metadata should include(""""latest_stable" : "0.2.0"""")
          metadata should include(""""runtime"""")
          metadata should include(""""minimum" : "0.5.0"""")
          metadata should not include ("nict-knowledgehub.cml")
          And("explicit CAR tags and terms remain first while Project metadata supplements them")
          val metadatajson = parser.parse(metadata).fold(throw _, identity)
          val carentries = metadatajson.hcursor.downField("entries").as[Vector[io.circe.Json]].fold(throw _, identity)
          def _car_entry_(artifactid: String): io.circe.Json =
            carentries.find(_.hcursor.get[String]("artifact_id").toOption.contains(artifactid)).
              getOrElse(fail(s"Missing repository CAR metadata entry: ${artifactid}"))
          val nictentry = _car_entry_("nict-knowledgehub")
          nictentry.hcursor.get[Vector[String]]("tags").fold(throw _, identity) shouldBe Vector("workflow.review", "sie", "knowledge")
          nictentry.hcursor.get[Vector[String]]("terms").fold(throw _, identity) shouldBe Vector("Repository CAR", "semantic integration", "knowledge model")
          val textussieentry = _car_entry_("textus-sie")
          textussieentry.hcursor.get[Vector[String]]("tags").fold(throw _, identity) shouldBe Vector("platform.sie")
          textussieentry.hcursor.get[Vector[String]]("terms").fold(throw _, identity) shouldBe Vector("semantic integration engine")
          val diagnosticjson = metadatajson.
            hcursor.downField("diagnostics").as[Vector[io.circe.Json]].fold(throw _, identity)
          val diagnostics = diagnosticjson.map { json =>
            val cursor = json.hcursor
            (
              cursor.downField("code").as[String].fold(throw _, identity),
              cursor.downField("artifact_id").as[String].fold(throw _, identity)
            )
          }.toSet
          diagnostics should contain("catalog-without-project" -> "textus-sie")
          diagnostics should contain("project-without-catalog" -> "unpublished-car")
          diagnostics should not contain ("catalog-without-project" -> "nict-knowledgehub")
          metadata.indexOf("catalog-without-project") should be < metadata.indexOf("project-without-catalog")

          And("repository CAR metadata is copied to the website")
          _read(
            dir.resolve("website.d/metadata/repository/car/index.json")
          ) should include(""""artifact_id" : "nict-knowledgehub"""")
          _read(
            dir.resolve("doxsite.d/metadata/repository/car/nict-knowledgehub.json")
          ) should include(""""artifact_id" : "nict-knowledgehub"""")
          _read(
            dir.resolve("website.d/metadata/repository/car/nict-knowledgehub.json")
          ) should include(""""source_path" : "repository/catalog/car/nict-knowledgehub.yaml"""")

          And("repository CAR catalog entries have a dedicated index page")
          val index = _read(dir.resolve("website.d/repository/car/index.html"))
          index should include("CARリポジトリ")
          index should include("nict-knowledgehub")
          index should include("textus-sie")
          index should include("nict-kh")
          index should include("repository/catalog/car/nict-knowledgehub.yaml")
          index should include("nict-knowledgehub/index.html")
          index should include("CARリポジトリ診断")
          index should include("公開CARに対応するProject定義がありません。")
          index should include("Projectに対応する公開CAR catalogがありません。")
          index should include("Unpublished CAR Project")

          And("repository CAR module and version pages link back to the Project")
          val modulepage = _read(dir.resolve("website.d/repository/car/nict-knowledgehub/index.html"))
          modulepage should include("repository/catalog/car/nict-knowledgehub.yaml")
          modulepage should include("0.2.0.html")
          modulepage should include("0.3.0-SNAPSHOT.html")
          modulepage should include("../../../tags/workflow/review.html")
          modulepage should include("../../../tags/technology/sie.html")
          modulepage should include("../../../tags/technology/knowledge.html")
          modulepage should include("Repository CAR, semantic integration, knowledge model")
          modulepage should include("関連Project")
          modulepage should include("NICT KnowledgeHub")
          val versionpage = _read(dir.resolve("website.d/repository/car/nict-knowledgehub/0.2.0.html"))
          versionpage should include("NictKnowledgeHub")
          versionpage should include("2026-07-13T00:00:00Z")
          versionpage should include("minimum: 0.5.0")
          versionpage should include("repository/car/nict-knowledgehub/0.2.0/nict-knowledgehub-0.2.0.car")
          versionpage should include("NICT KnowledgeHub")

          And("project detail page exposes the associated repository CAR versions")
          val page = _read(
            dir.resolve(
              "website.d/textus/components/nict-knowledgehub/index.html"
            )
          )
          page should include("CARリポジトリ")
          page should include("repository/car/nict-knowledgehub/0.2.0/nict-knowledgehub-0.2.0.car")
          page should include("repository/catalog/car/nict-knowledgehub.yaml")
          page should include("repository/car/nict-knowledgehub/0.2.0.html")
          page should include("tags/technology/sie.html")

          And("tag resource pages include related Project and repository CAR entries")
          val tagpage = _read(dir.resolve("website.d/tags/technology/sie.html"))
          tagpage should include("NICT KnowledgeHub")
          tagpage should include("repository/car/nict-knowledgehub/index.html")
          tagpage should include("CARリポジトリ")
          val explicittagpage = _read(dir.resolve("website.d/tags/workflow/review.html"))
          explicittagpage should include("repository/car/nict-knowledgehub/index.html")
          val unlinkedtagpage = _read(dir.resolve("website.d/tags/platform/sie.html"))
          unlinkedtagpage should include("repository/car/textus-sie/index.html")
          val inheritedtagpage = _read(dir.resolve("website.d/tags/technology/knowledge.html"))
          inheritedtagpage should include("repository/car/nict-knowledgehub/index.html")
        }
      }

      "reject repository CAR JSON catalogs whose filename and artifact id differ" in {
        _with_temp_dir("cozy-bok-repository-car-json-mismatch") { dir =>
          Given("a BoK source tree with a malformed repository CAR JSON catalog")
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            "site { output { locale_mode = \"single_locale_root\" } }\n"
          )
          _write(dir.resolve("src/main/doxsite/index.dox"), "Home\n====\n")
          _write(
            dir.resolve("repository/catalog/car/textus-sie.json"),
            """{
              |  "schemaVersion": "1",
              |  "kind": "car",
              |  "artifactId": "other-car",
              |  "versions": [
              |    {
              |      "version": "0.1.0",
              |      "channel": "stable",
              |      "file": "repository/car/other-car/0.1.0/other-car-0.1.0.car"
              |    }
              |  ]
              |}
              |""".stripMargin
          )
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--strategy", "preview", "--no-bib-service")
          )

          When("Cozy builds repository CAR knowledge")
          val e = intercept[IllegalArgumentException] {
            CozyBok.build(config, new ProjectBuildRunner)
          }

          Then("JSON catalogs use the same source path contract as YAML catalogs")
          e.getMessage should include("filename")
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
      if (command.headOption.contains("docker") && Files.isRegularFile(cwd.resolve("src/main/doxsite/glossary/technology/knowledge-item.dox"))) {
        _write(
          cwd.resolve("website.d/glossary/technology/knowledge-item.html"),
          "<html><body><article><h1 class=\"page\">Knowledge Item</h1><p>SmartDox term narrative</p></article></body></html>\n"
        )
        _write(
          cwd.resolve("website.d/scenario/technology/knowledge-review.html"),
          "<html><body><article><h1 class=\"page\">Knowledge Review Scenario</h1><p>SmartDox scenario narrative</p></article></body></html>\n"
        )
      }
      if (command.take(2) == Vector("dox", "site")) {
        val sourceterms = cwd.resolve("src/main/doxsite/metadata/glossary/terms.json")
        if (Files.isRegularFile(sourceterms))
          _write(
            cwd.resolve("doxsite.d/metadata/glossary/terms.json"),
            _read(sourceterms)
          )
        _write(
          cwd.resolve("doxsite.d/metadata/dashboard/site.json"),
          _dashboard_json
        )
        _write(
          cwd.resolve("doxsite.d/site.ttl"),
          "@prefix ex: <https://example.com/> .\n"
        )
        _write(cwd.resolve("doxsite.d/site.jsonld"), "{\"@graph\":[]}\n")
        _write(cwd.resolve("doxsite.d/metadata/rdf/graph.json"), "{\"nodes\":[],\"edges\":[],\"truncated\":false}\n")
      }
    }
  }

  private def _write_sie_project_source(
    dir: Path,
    handoffbase: String,
    withcomponentcatalog: Boolean = true,
    withsubsystemcatalog: Boolean = true
  ): Path = {
    val localpath = dir.resolve("target/sie/projections/nict-knowledgehub")
    _write(
      dir.resolve("src/main/doxsite/site.conf"),
      """site {
        |  output {
        |    locale_mode = "single_locale_root"
        |    default_locale = "en"
        |  }
        |}
        |""".stripMargin
    )
    _write(dir.resolve("src/main/doxsite/index.dox"), "Home\n====\n")
    _write(
      dir.resolve("src/main/doxsite/glossary/technology/knowledge-item.dox"),
      """Knowledge Item
        |==============
        |
        |# HEAD
        |
        |term_type = concept
        |
        |# BODY
        |
        |SmartDox term narrative.
        |""".stripMargin
    )
    if (withcomponentcatalog)
      _write(
        dir.resolve("repository/catalog/car/textus-semantic-integration-engine.json"),
        _sie_component_catalog_json("0.1.0")
      )
    if (withsubsystemcatalog)
      _write(
        dir.resolve("repository/catalog/sar/nict-knowledgehub-runtime.json"),
        _sie_subsystem_catalog_json("1.0.0")
      )
    _write(
      dir.resolve("repository/catalog/car/nict-knowledgehub.model-metadata.json"),
      """{
        |  "source": {"path": "src/main/cozy/nict-knowledgehub.cml"},
        |  "modelElements": [
        |    {
        |      "kind": "Entity",
        |      "name": "KnowledgeItem",
        |      "termId": "technology:knowledge-item",
        |      "glossaryPath": "glossary/technology/knowledge-item.html",
        |      "descriptive": {"label": "Knowledge Item", "summary": "Knowledge represented by SIE."}
        |    }
        |  ]
        |}
        |""".stripMargin
    )
    val termsjson =
      """{
        |  "terms": [
        |    {
        |      "id": "technology:knowledge-item",
        |      "slug": "knowledge-item",
        |      "title": "Knowledge Item",
        |      "category": "technology",
        |      "source_path": "glossary/technology/knowledge-item.dox",
        |      "public_path": "glossary/technology/knowledge-item.html",
        |      "definition_html": "<p>Knowledge represented by SIE.</p>",
        |      "summary": "Glossary term linked to an SIE Project CML entity.",
        |      "aliases": [],
        |      "term_type": "concept",
        |      "cml": [{"kind": "entity", "value": "KnowledgeItem"}],
        |      "article_refs": [],
        |      "term_refs": [],
        |      "rdf_refs": [{"resource": "https://example.com/knowledge-item", "label": "Knowledge Item RDF", "predicate": "schema:about", "direction": "outgoing"}],
        |      "video_refs": [],
        |      "quality": {"isolated": false, "unreferenced": false, "weakly_connected": false},
        |      "tags": ["sie"]
        |    }
        |  ]
        |}
        |""".stripMargin
    _write(dir.resolve("src/main/doxsite/metadata/glossary/terms.json"), termsjson)
    _write(dir.resolve("doxsite.d/metadata/glossary/terms.json"), termsjson)
    _write(
      dir.resolve("src/main/doxsite/scenario/technology/knowledge-review.md"),
      """---
        |title: Knowledge Review Scenario
        |brief: Review an SIE knowledge item.
        |scenario:
        |  type: use-case
        |  id: scenario:knowledge-review
        |  terms:
        |    - technology:knowledge-item
        |tags:
        |  - workflow.review
        |status: published
        |---
        |
        |# UseCase
        |
        |## Knowledge Review Scenario
        |""".stripMargin
    )
    _write(
      dir.resolve("conf/cozy/config.yaml"),
      s"""bok:
         |  projects:
         |    nict-knowledgehub:
         |      sie:
         |        path: ${localpath.toString}
         |""".stripMargin
    )
    _write_sie_handoff(localpath)
    val pkg = dir.resolve("src/main/doxsite/projects/technology/nict-knowledgehub")
    _write(pkg.resolve("index.dox"), "NictKnowledgeHub\n================\n")
    _write(
      pkg.resolve("project.yaml"),
      s"""project:
         |  type: car
         |  name: nict-knowledgehub
         |  mode: external
         |  ref: nict-knowledgehub
         |car:
         |  module: nict-knowledgehub
         |sie:
         |  projection: nict-knowledgehub
         |  component: textus-semantic-integration-engine
         |  subsystem: nict-knowledgehub-runtime
         |  handoff_base: ${handoffbase}
         |title: NICT KnowledgeHub
         |version: 0.1.0
         |summary: SIE-linked KnowledgeHub Project.
         |terms:
         |  - technology:knowledge-item
         |tags:
         |  - sie
         |  - workflow.review
         |cml:
         |  glossary:
         |    category: technology
         |article: index.dox
         |""".stripMargin
    )
    localpath
  }

  private def _write_sie_handoff(root: Path): Unit = {
    _write(
      root.resolve("metadata/cncf/knowledge-source.json"),
      """{
        |  "schemaVersion": "cncf.knowledge-source.v1",
        |  "kind": "sie-projection",
        |  "id": "nict-knowledgehub",
        |  "sourceRef": {"kind": "sie-projection", "value": "nict-knowledgehub"},
        |  "resources": [
        |    {"kind": "sie-provenance", "href": "metadata/sie/provenance.json", "mediaType": "application/json"},
        |    {"kind": "information-schema", "href": "metadata/sie/information-schema.json", "mediaType": "application/json"},
        |    {"kind": "information-instances", "href": "metadata/sie/information-instances.json", "mediaType": "application/json"},
        |    {"kind": "rdf-graph-summary", "href": "metadata/rdf/graph.json", "mediaType": "application/json"}
        |  ]
        |}
        |""".stripMargin
    )
    _write(
      root.resolve("metadata/sie/provenance.json"),
      """{
        |  "schemaVersion": "sie.provenance.v1",
        |  "projection": "nict-knowledgehub",
        |  "projectRef": "nict-knowledgehub",
        |  "producer": "textus-semantic-integration-engine",
        |  "generatedAt": "2026-07-13T00:00:00Z"
        |}
        |""".stripMargin
    )
    _write(
      root.resolve("metadata/sie/information-schema.json"),
      """{
        |  "schemaVersion": "sie.information-schema.v1",
        |  "projection": "nict-knowledgehub",
        |  "informationSchemas": [
        |    {
        |      "name": "knowledge-item-information-v1",
        |      "label": "Knowledge Item Information",
        |      "match": {"categories": ["technology"]},
        |      "requiredPredicates": ["rdf:type"],
        |      "descriptivePredicates": ["rdfs:label"]
        |    }
        |  ]
        |}
        |""".stripMargin
    )
    _write(
      root.resolve("metadata/sie/information-instances.json"),
      """{
        |  "schemaVersion": "sie.information-instances.v1",
        |  "projection": "nict-knowledgehub",
        |  "instances": [
        |    {
        |      "id": "knowledge-item-001",
        |      "schema": "knowledge-item-information-v1",
        |      "label": "Knowledge Item Information",
        |      "summary": "SIE materialized knowledge item.",
        |      "rdfNode": "https://example.com/knowledge-item",
        |      "category": "technology",
        |      "termRefs": ["technology:knowledge-item"],
        |      "scenarioRefs": ["scenario:knowledge-review"],
        |      "projectRefs": ["nict-knowledgehub"],
        |      "tags": ["technology.sie", "workflow.review"]
        |    }
        |  ]
        |}
        |""".stripMargin
    )
    _write(
      root.resolve("metadata/rdf/graph.json"),
      """{
        |  "nodes": [
        |    {"id": "https://example.com/knowledge-item", "label": "Knowledge Item", "node_type": "uri", "category": "technology", "degree": 1, "terms": ["technology:knowledge-item"], "tags": ["technology.sie"]},
        |    {"id": "https://schema.org/name", "label": "name", "node_type": "uri", "degree": 1}
        |  ],
        |  "edges": [
        |    {"source": "https://example.com/knowledge-item", "target": "https://schema.org/name", "predicate": "https://schema.org/name", "label": "name", "category": "technology"}
        |  ],
        |  "truncated": false
        |}
        |""".stripMargin
    )
  }

  private def _sie_component_catalog_json(version: String): String =
    s"""{
       |  "schemaVersion": "1",
       |  "kind": "car",
       |  "artifactId": "textus-semantic-integration-engine",
       |  "recommended": "${version}",
       |  "latestStable": "${version}",
       |  "versions": [
       |    {
       |      "version": "${version}",
       |      "channel": "stable",
       |      "file": "repository/car/textus-semantic-integration-engine/${version}/textus-semantic-integration-engine-${version}.car"
       |    }
       |  ]
       |}
       |""".stripMargin

  private def _sie_subsystem_catalog_json(version: String): String =
    s"""{
       |  "schemaVersion": "1",
       |  "kind": "sar",
       |  "artifactId": "nict-knowledgehub-runtime",
       |  "recommended": "${version}",
       |  "latestStable": "${version}",
       |  "status": "active",
       |  "versions": [
       |    {
       |      "version": "${version}",
       |      "channel": "stable",
       |      "status": "active",
       |      "file": "repository/sar/nict-knowledgehub-runtime/${version}/nict-knowledgehub-runtime-${version}.sar"
       |    }
       |  ]
       |}
       |""".stripMargin

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
