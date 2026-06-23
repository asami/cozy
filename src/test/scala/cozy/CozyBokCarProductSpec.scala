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
class CozyBokCarProductSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy BoK CAR product publication" should {
    "register CAR product source packages" which {
      "discover .car-product packages and write publication registry metadata" in {
        _with_temp_dir("cozy-bok-car-product") { dir =>
          Given(
            "a BoK source tree with a CAR product package and an external CAR project reference"
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
            "src/main/doxsite/concept/nict-knowledgehub.car-product"
          )
          _write(
            pkg.resolve("index.dox"),
            "NictKnowledgeHub\n================\n"
          )
          _write(
            pkg.resolve("product.yaml"),
            """product:
              |  type: car
              |  name: nict-knowledgehub
              |  project:
              |    mode: external
              |    ref: nict-knowledgehub
              |  car:
              |    module: nict-knowledgehub
              |title: NICT KnowledgeHub
              |version: 0.1.0
              |summary: NICT KnowledgeHub CAR product.
              |article: index.dox
              |publication:
              |  path: products/nict-knowledgehub
              |""".stripMargin
          )
          val config = CozyBok.PublicationConfig.create(
            "publish-car-products",
            List(dir.toString)
          )

          When("Cozy registers CAR product publication metadata")
          val results = CozyBok.publishCarProducts(config)

          Then(
            "the publication registry is written without creating generated files inside the source package"
          )
          results.map(_.product.name) shouldBe Vector("nict-knowledgehub")
          dir.resolve(
            "src/main/publication/nict-knowledgehub.json"
          ) should beRegularFile
          val bundle =
            _read(dir.resolve("src/main/publication/nict-knowledgehub.json"))
          bundle should include(
            "metadata/products/car/nict-knowledgehub/metadata.json"
          )
          bundle should include(
            "metadata/products/car/nict-knowledgehub/0.1.0/manifest.json"
          )
          bundle should include(
            "metadata/artifacts/repository/nict-knowledgehub.json"
          )
          bundle should include("products/nict-knowledgehub")
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
        _with_temp_dir("cozy-bok-car-product-artifact") { dir =>
          Given(
            "a CAR product package and a pre-existing repository CAR artifact"
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
            "src/main/doxsite/concept/nict-knowledgehub.car-product"
          )
          _write(
            pkg.resolve("index.dox"),
            "NictKnowledgeHub\n================\n"
          )
          _write(
            pkg.resolve("product.yaml"),
            """product:
              |  type: car
              |  name: nict-knowledgehub
              |  project:
              |    mode: external
              |    ref: nict-knowledgehub
              |  car:
              |    module: nict-knowledgehub
              |title: NICT KnowledgeHub
              |version: 0.1.0
              |publication:
              |  path: products/nict-knowledgehub
              |""".stripMargin
          )
          _write(
            dir.resolve(
              "repository/car/nict-knowledgehub/0.1.0/nict-knowledgehub-0.1.0.car"
            ),
            "car-body"
          )
          val config = CozyBok.PublicationConfig.create(
            "publish-car-products",
            List(dir.toString)
          )

          When("Cozy registers CAR product publication metadata")
          val results = CozyBok.publishCarProducts(config)

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
        _with_temp_dir("cozy-bok-car-product-local-repository") { dir =>
          Given(
            "a CAR product package and a project-local public repository artifact in a non-default directory"
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
            "src/main/doxsite/concept/nict-knowledgehub.car-product"
          )
          _write(
            pkg.resolve("index.dox"),
            "NictKnowledgeHub\n================\n"
          )
          _write(
            pkg.resolve("product.yaml"),
            """product:
              |  type: car
              |  name: nict-knowledgehub
              |  project:
              |    mode: external
              |    ref: nict-knowledgehub
              |  car:
              |    module: nict-knowledgehub
              |title: NICT KnowledgeHub
              |publication:
              |  path: products/nict-knowledgehub
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
            "publish-car-products",
            List(dir.toString)
          )

          When("Cozy registers CAR product publication metadata")
          val results = CozyBok.publishCarProducts(config)

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
        _with_temp_dir("cozy-bok-car-product-catalog") { dir =>
          Given(
            "a CAR product package that relies on the repository CAR catalog"
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
            "src/main/doxsite/concept/nict-knowledgehub.car-product"
          )
          _write(
            pkg.resolve("index.dox"),
            "NictKnowledgeHub\n================\n"
          )
          _write(
            pkg.resolve("product.yaml"),
            """product:
              |  type: car
              |  name: nict-knowledgehub
              |  project:
              |    mode: external
              |    ref: nict-knowledgehub
              |  car:
              |    module: nict-knowledgehub
              |title: NICT KnowledgeHub
              |summary: NICT KnowledgeHub CAR product.
              |article: index.dox
              |publication:
              |  path: products/nict-knowledgehub
              |""".stripMargin
          )
          val config = CozyBok.PublicationConfig.create(
            "publish-car-products",
            List(dir.toString)
          )

          When("Cozy registers the product from the BoK package")
          val results = CozyBok.publishCarProducts(config)

          Then(
            "the product version and artifact metadata are sourced from the repository catalog"
          )
          results.head.product.version shouldBe "0.2.0"
          results.head.product.versionsource shouldBe "repository-catalog"
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

      "resolve external project paths from local .cozy overrides" in {
        _with_temp_dir("cozy-bok-car-product-local-config") { dir =>
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
            "src/main/doxsite/concept/nict-knowledgehub.car-product"
          )
          _write(
            pkg.resolve("index.dox"),
            "NictKnowledgeHub\n================\n"
          )
          _write(
            pkg.resolve("product.yaml"),
            """product:
              |  type: car
              |  name: nict-knowledgehub
              |  project:
              |    mode: external
              |    ref: nict-knowledgehub
              |  car:
              |    module: nict-knowledgehub
              |title: NICT KnowledgeHub
              |version: 0.1.0
              |publication:
              |  path: products/nict-knowledgehub
              |""".stripMargin
          )
          val config = CozyBok.PublicationConfig.create(
            "publish-car-products",
            List(dir.toString)
          )

          When("Cozy resolves the CAR product reference")
          val results = CozyBok.publishCarProducts(config)

          Then(
            "the local sensitive .cozy path overrides the public config path"
          )
          results.head.product.projectpath.map(_.toString) shouldBe Some(
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
        _with_temp_dir("cozy-bok-car-product-workdir") { dir =>
          Given(
            "a BoK source tree containing a reserved CAR product work directory"
          )
          Files.createDirectories(
            dir.resolve("src/main/doxsite/concept/generated.car-product.d")
          )
          val config = CozyBok.PublicationConfig.create(
            "publish-car-products",
            List(dir.toString)
          )

          When("Cozy discovers CAR product packages")
          val e = intercept[Throwable] {
            CozyBok.publishCarProducts(config)
          }

          Then("the reserved generated/work directory naming is rejected")
          e.getMessage should include("*.car-product.d is reserved")
        }
      }
    }

    "integrate CAR products with BoK publication commands" which {
      "update-publication handles CAR products without invoking CAR artifact publishing" in {
        _with_temp_dir("cozy-bok-car-product-update") { dir =>
          Given(
            "a BoK source tree with one CAR product and no warehouse artifact"
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
            "src/main/doxsite/concept/nict-knowledgehub.car-product"
          )
          _write(
            pkg.resolve("index.dox"),
            "NictKnowledgeHub\n================\n"
          )
          _write(
            pkg.resolve("product.yaml"),
            """product:
              |  type: car
              |  name: nict-knowledgehub
              |  project:
              |    mode: external
              |    ref: nict-knowledgehub
              |  car:
              |    module: nict-knowledgehub
              |title: NICT KnowledgeHub
              |version: 0.1.0
              |publication:
              |  path: products/nict-knowledgehub
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

      "publish dry-run reports both video and CAR product packages" in {
        _with_temp_dir("cozy-bok-car-product-dry-run") { dir =>
          Given("a BoK project with upload workflow and a CAR product package")
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
            "src/main/doxsite/concept/nict-knowledgehub.car-product"
          )
          _write(
            pkg.resolve("index.dox"),
            "NictKnowledgeHub\n================\n"
          )
          _write(
            pkg.resolve("product.yaml"),
            """product:
              |  type: car
              |  name: nict-knowledgehub
              |  project:
              |    mode: external
              |    ref: nict-knowledgehub
              |  car:
              |    module: nict-knowledgehub
              |title: NICT KnowledgeHub
              |version: 0.1.0
              |publication:
              |  path: products/nict-knowledgehub
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
            "the plan and manifest include CAR product package counts without side effects"
          )
          out should include("0 .video package(s), 1 .car-product package(s)")
          dir.resolve("src/main/publication") shouldNot existPath
          dir.resolve("warehouse") shouldNot existPath
          val manifest =
            _read(dir.resolve("target/cozy-bok/publish/latest/manifest.json"))
          manifest should include("carProductPackages")
          manifest should include("nict-knowledgehub.car-product")
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
        _with_temp_dir("cozy-bok-car-product-publish-car-sidecars") { dir =>
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

      "prefer repository model-metadata over direct CML scan for CAR product registration" in {
        _with_temp_dir("cozy-bok-car-product-model-metadata-priority") { dir =>
          Given(
            "a BoK CAR product whose repository catalog has generated model metadata"
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
            "src/main/doxsite/concept/nict-knowledgehub.car-product"
          )
          _write(
            pkg.resolve("index.dox"),
            "NictKnowledgeHub\n================\n"
          )
          _write(
            pkg.resolve("product.yaml"),
            """product:
              |  type: car
              |  name: nict-knowledgehub
              |  project:
              |    mode: external
              |    ref: nict-knowledgehub
              |  car:
              |    module: nict-knowledgehub
              |  cml:
              |    glossary:
              |      category: technology
              |title: NICT KnowledgeHub
              |version: 0.1.0
              |publication:
              |  path: products/nict-knowledgehub
              |""".stripMargin
          )
          val config = CozyBok.PublicationConfig.create(
            "publish-car-products",
            List(dir.toString)
          )

          When("Cozy registers CAR product publication metadata")
          CozyBok.publishCarProducts(config)

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

      "help lists CAR product publication commands" in {
        Given("the Cozy help surface")
        When("Cozy renders command help")
        val help = _capture {
          Cozy.main(Array("--help"))
        }
        val bokhelp = _capture {
          CozyBok.execute(List("bok", "publish-car-products", "--help"))
        }

        Then("CAR product publication commands are discoverable")
        help should include("bok publish-car-products <project-dir>")
        help should include(".car-product")
        bokhelp should include("Usage: cozy bok publish-car-products")
        bokhelp should include(
          "CAR artifact publishing remains the responsibility of cozy publish-car"
        )
      }

      "build renders CAR product article pages at their publication path" in {
        _with_temp_dir("cozy-bok-car-product-build") { dir =>
          Given(
            "a BoK source tree with a CAR product package and publication path"
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
            "src/main/doxsite/concept/nict-knowledgehub.car-product"
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
              |NICT KnowledgeHub CAR product.
              |
              |# Overview
              |
              |NictKnowledgeHub CAR Product article body.
              |""".stripMargin
          )
          _write(
            pkg.resolve("product.yaml"),
            """product:
              |  type: car
              |  name: nict-knowledgehub
              |  project:
              |    mode: external
              |    ref: nict-knowledgehub
              |  car:
              |    module: nict-knowledgehub
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
          CozyBok.build(config, new CarProductBuildRunner)

          Then(
            "the CAR product article is rendered as a normal BoK page without generating CAR artifacts"
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

  private class CarProductBuildRunner extends RecordingRunner {
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
