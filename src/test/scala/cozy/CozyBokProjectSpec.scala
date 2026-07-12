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
 *  version Jun. 27, 2026
 * @version Jul. 13, 2026
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
              |tags:
              |  - sie
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
          val diagnosticjson = parser.parse(metadata).fold(throw _, identity).
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
          index should include("Project/CAR接続診断")
          index should include("公開CARに対応するProject定義がありません。")
          index should include("Projectに対応する公開CAR catalogがありません。")
          index should include("Unpublished CAR Project")

          And("repository CAR module and version pages link back to the Project")
          val modulepage = _read(dir.resolve("website.d/repository/car/nict-knowledgehub/index.html"))
          modulepage should include("repository/catalog/car/nict-knowledgehub.yaml")
          modulepage should include("0.2.0.html")
          modulepage should include("0.3.0-SNAPSHOT.html")
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
