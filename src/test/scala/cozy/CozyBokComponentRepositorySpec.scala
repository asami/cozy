package cozy.bok

import cozy.CozySpecVocabulary
import cozy.archive.RepositoryArtifactPublisher
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.collection.JavaConverters._
import io.circe.parser
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 13, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyBokComponentRepositorySpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy BoK Component Repository knowledge" should {
    "resolve repository catalog roots" which {
      "read CAR catalogs from an explicit warehouse operation" in {
        _with_temp_dir("cozy-bok-repository-car-warehouse") { dir =>
          Given("a BoK source tree and a CAR catalog under an external warehouse repository")
          val warehouse = dir.resolve("warehouse")
          val archivepath = warehouse.resolve("repository/car/org/example/textus/textus-sie/0.1.0/textus-sie-0.1.0.car")
          _write_car_archive(archivepath)
          val archivedigest = RepositoryArtifactPublisher.sha256(archivepath)
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            "site { output { locale_mode = \"single_locale_root\" } }\n"
          )
          _write(dir.resolve("src/main/doxsite/index.dox"), "Home\n====\n")
          _write(
            warehouse.resolve("repository/catalog/car/org/example/textus/textus-sie.yaml"),
            s"""schemaVersion: 2
              |kind: car
              |namespace: org.example.textus
              |id: Sie
              |artifactId: textus-sie
              |recommended: 0.1.0
              |versions:
              |  - version: 0.1.0
              |    channel: stable
              |    component: org.example.textus.Sie
              |    file: repository/car/org/example/textus/textus-sie/0.1.0/textus-sie-0.1.0.car
              |    checksum:
              |      sha256: $archivedigest
              |    integrityKey: org.example.textus:textus-sie:0.1.0@sha256:$archivedigest
              |""".stripMargin
          )
          _write(
            warehouse.resolve("repository/catalog/sar/textus-app.yaml"),
            """schemaVersion: 1
              |kind: sar
              |artifactId: textus-app
              |recommended: 0.1.0
              |versions:
              |  - version: 0.1.0
              |    channel: stable
              |    file: repository/sar/textus-app/0.1.0/textus-app-0.1.0.sar
              |""".stripMargin
          )
          _write(
            warehouse.resolve("repository/catalog/car/org/example/textus/textus-sie.cml"),
            "# COMPONENT\n\n## TextusSie\n"
          )
          _write(
            warehouse.resolve("repository/catalog/car/org/example/textus/textus-sie.model-metadata.json"),
            "{\"schema\":\"cozy.cml.model-metadata.v1\"}\n"
          )
          _write(
            warehouse.resolve("repository/catalog/car/org/example/textus/textus-sie.model-metadata.yaml"),
            "schema: cozy.cml.model-metadata.v1\n"
          )
          val config = CozyBok.BuildConfig.create(
            List(
              dir.toString,
              "--strategy",
              "preview",
              "--no-bib-service",
              "--warehouse",
              warehouse.toString
            )
          )

          When("Cozy builds repository CAR knowledge in warehouse mode")
          CozyBok.build(config, new RepositoryCarBuildRunner)

          Then("the warehouse repository becomes the catalog source of truth")
          config.publication.repositoryPath(dir) shouldBe warehouse.resolve("repository").toAbsolutePath.normalize()
          val metadata = _read(dir.resolve("doxsite.d/metadata/repository/car/index.json"))
          metadata should include(""""artifact_id" : "textus-sie"""")
          metadata should include(""""source_path" : "warehouse/repository/catalog/car/org/example/textus/textus-sie.yaml"""")
          metadata should include(""""cml" : "repository/catalog/car/org/example/textus/textus-sie.cml"""")
          metadata should include(""""model_metadata_json" : "repository/catalog/car/org/example/textus/textus-sie.model-metadata.json"""")
          metadata should include(""""model_metadata_yaml" : "repository/catalog/car/org/example/textus/textus-sie.model-metadata.yaml"""")
          metadata should include("\"component_descriptor\" : {")
          metadata should include(""""namespace" : "org.example.textus"""")
          metadata should include(""""id" : "Sie"""")
          metadata should include(""""version" : "0.1.0"""")
          metadata should include(""""help" : "/help/TextusSie"""")
          metadata should include(""""manual" : "/man/TextusSie"""")
          metadata should include(""""openapi" : "/openapi.json"""")
          metadata should include(""""mcp" : "/mcp"""")
          metadata should include("\"abi_manifest\" : {")
          metadata should include(""""format" : "cozy.car.abi-manifest.v2"""")

          And("the generated website exposes the CAR entry and its public sidecars")
          _read(dir.resolve("website.d/metadata/repository/car/index.json")) should include("textus-sie")
          val page = _read(dir.resolve("website.d/repository/car/index.html"))
          page should include("textus-sie")
          page should include("warehouse/repository/catalog/car/org/example/textus/textus-sie.yaml")
          dir.resolve("website.d/repository/catalog/car/org/example/textus/textus-sie.cml") should be_regular_file
          dir.resolve("website.d/repository/catalog/car/org/example/textus/textus-sie.model-metadata.json") should be_regular_file
          dir.resolve("website.d/repository/catalog/car/org/example/textus/textus-sie.model-metadata.yaml") should be_regular_file
          val modulepage = _read(dir.resolve("website.d/repository/car/textus-sie/index.html"))
          modulepage should include("../../catalog/car/org/example/textus/textus-sie.cml")
          modulepage should include("モデルメタデータ (JSON)")
          modulepage should include("モデルメタデータ (YAML)")
          modulepage should include("コンポーネント記述子")
          modulepage should include("org.example.textus.Sie 0.1.0 / org.example.textus.Sie / entities 1")
          modulepage should include("ABIマニフェスト")
          modulepage should include("org.example.textus.Sie 0.1.0 / ABI 1 / components 1 / operations 1 / entities 1")
          modulepage should include("href=\"0.1.0/component-descriptor.json\"")
          modulepage should include("href=\"0.1.0/abi-manifest.json\"")
          modulepage should include("コンポーネント公開面")
          modulepage should include("href=\"/help/TextusSie\"")
          modulepage should include("href=\"/man/TextusSie\"")
          modulepage should include("href=\"/openapi.json\"")
          modulepage should include("href=\"/mcp\"")
          val versionpage = _read(dir.resolve("website.d/repository/car/textus-sie/0.1.0.html"))
          versionpage should include("../../catalog/car/org/example/textus/textus-sie.cml")
          versionpage should include("コンポーネント記述子")
          versionpage should include("ABIマニフェスト")
          versionpage should include("href=\"0.1.0/component-descriptor.json\"")
          versionpage should include("href=\"0.1.0/abi-manifest.json\"")
          dir.resolve("website.d/repository/car/textus-sie/0.1.0/component-descriptor.json") should be_regular_file
          dir.resolve("website.d/repository/car/textus-sie/0.1.0/abi-manifest.json") should be_regular_file
          val sarpage = _read(dir.resolve("website.d/repository/sar/index.html"))
          sarpage should include("textus-app")
          dir.resolve("website.d/repository/sar/textus-app/index.html") should be_regular_file
        }
      }

      "publish a version-scoped Component knowledge carrier and byte-identical consumer contract" in {
        _with_temp_dir("cozy-bok-repository-component-knowledge") { dir =>
          Given("one exact CAR archive declaration and its version-scoped repository sidecar")
          val warehouse = dir.resolve("warehouse")
          val contract =
            """{"schema":"cncf.component-knowledge-consumer.v1","componentId":"org.example.textus.Sie","logicalRelease":"0.1.0","resources":[]}"""
          val sidecar = warehouse.resolve("repository/catalog/car/org/example/textus/textus-sie/0.1.0/component-knowledge.json")
          _write(sidecar, contract)
          val digest = RepositoryArtifactPublisher.sha256(sidecar)
          val descriptor =
            s"""{"schemaVersion":3,"component":{"namespace":"org.example.textus","id":"Sie","version":"0.1.0"},"componentKnowledge":{"carrierSchema":"cncf.component-knowledge-carrier.v1","consumerContractSchema":"cncf.component-knowledge-consumer.v1","logicalPath":"component-knowledge.json","sha256":"$digest"}}"""
          val archivepath = warehouse.resolve("repository/car/org/example/textus/textus-sie/0.1.0/textus-sie-0.1.0.car")
          _write_car_archive_entries(archivepath, Some(descriptor), None, Vector("component-knowledge.json" -> contract))
          val archivedigest = RepositoryArtifactPublisher.sha256(archivepath)
          _write(dir.resolve("src/main/doxsite/site.conf"), "site { output { locale_mode = \"single_locale_root\" } }\n")
          _write(dir.resolve("src/main/doxsite/index.dox"), "Home\n====\n")
          _write(
            warehouse.resolve("repository/catalog/car/org/example/textus/textus-sie.yaml"),
            s"""schemaVersion: 2
              |kind: car
              |namespace: org.example.textus
              |id: Sie
              |artifactId: textus-sie
              |recommended: 0.1.0
              |versions:
              |  - version: 0.1.0
              |    channel: stable
              |    component: org.example.textus.Sie
              |    file: repository/car/org/example/textus/textus-sie/0.1.0/textus-sie-0.1.0.car
              |    checksum:
              |      sha256: $archivedigest
              |    integrityKey: org.example.textus:textus-sie:0.1.0@sha256:$archivedigest
              |""".stripMargin
          )
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--no-bib-service", "--warehouse", warehouse.toString))

          When("Cozy publishes BOK repository metadata and public repository assets")
          CozyBok.build(config, new RepositoryCarBuildRunner)

          Then("metadata declares only the canonical carrier and the public contract retains the declared digest")
          val metadata = _read(dir.resolve("doxsite.d/metadata/repository/car/index.json"))
          metadata should include("\"component_knowledge\" : {")
          metadata should include("\"consumer_contract\" : \"repository/car/textus-sie/0.1.0/component-knowledge.json\"")
          metadata should include("\"sha256\" : \"" + digest + "\"")
          val publiccontract = dir.resolve("website.d/repository/car/textus-sie/0.1.0/component-knowledge.json")
          publiccontract should be_regular_file
          _read(publiccontract) shouldBe contract
          RepositoryArtifactPublisher.sha256(publiccontract) shouldBe digest
        }
      }

      "use the public index as the CAR and SAR discovery source" in {
        _with_temp_dir("cozy-bok-component-repository-index") { dir =>
          Given("a public component index with multiple CARs and SARs across lifecycle states plus invalid and unindexed catalogs")
          _write(dir.resolve("src/main/doxsite/site.conf"), "site { output { locale_mode = \"single_locale_root\" } }\n")
          _write(dir.resolve("src/main/doxsite/index.dox"), "Home\n====\n")
          _write(
            dir.resolve("repository/catalog/index.json"),
            """{
              |  "schemaVersion": "cncf.component-repository-index.v2",
              |  "generatedAt": "2026-07-21T00:00:00Z",
              |  "artifacts": [
              |    {"kind":"car","namespace":"org.example.indexed","id":"Car","artifactId":"indexed-car","catalog":"car/org/example/indexed/indexed-car.yaml","status":"active","recommended":"1.0.0","latestStable":"1.0.0","latestSnapshot":"1.1.0-SNAPSHOT"},
              |    {"kind":"car","namespace":"org.example.disabled","id":"Car","artifactId":"disabled-car","catalog":"car/org/example/disabled/disabled-car.yaml","status":"disabled"},
              |    {"kind":"car","namespace":"org.example.mismatched","id":"Car","artifactId":"mismatched-car","catalog":"car/org/example/mismatched/mismatched-car.yaml","status":"active"},
              |    {"kind":"sar","artifactId":"indexed-sar","catalog":"sar/indexed-sar.yaml","status":"active","recommended":"2.0.0","latestStable":"2.0.0"},
              |    {"kind":"sar","artifactId":"snapshot-sar","catalog":"sar/snapshot-sar.yaml","status":"active","recommended":"2.1.0-SNAPSHOT","latestSnapshot":"2.1.0-SNAPSHOT"},
              |    {"kind":"sar","artifactId":"missing-sar","catalog":"sar/missing-sar.yaml","status":"active"},
              |    {"kind":"sar","artifactId":"invalid-sar","catalog":"sar/invalid-sar.yaml","status":"active"}
              |  ]
              |}
              |""".stripMargin
          )
          _write(dir.resolve("repository/catalog/sar/invalid-sar.yaml"), "schemaVersion: [\n")
          _write(
            dir.resolve("repository/catalog/car/org/example/indexed/indexed-car.yaml"),
            _repository_catalog("car", "indexed-car", "active", Vector(
              ("1.0.0", "stable", "active"),
              ("1.1.0-SNAPSHOT", "snapshot", "active")
            ), Some("1.0.0"), Some("1.0.0"), Some("1.1.0-SNAPSHOT"))
          )
          _write(
            dir.resolve("repository/catalog/car/org/example/disabled/disabled-car.yaml"),
            _repository_catalog("car", "disabled-car", "disabled", Vector(
              ("0.9.0", "stable", "disabled")
            ), None, None, None)
          )
          _write(
            dir.resolve("repository/catalog/sar/indexed-sar.yaml"),
            _repository_catalog("sar", "indexed-sar", "active", Vector(
              ("2.0.0", "stable", "active")
            ), Some("2.0.0"), Some("2.0.0"), None)
          )
          _write(
            dir.resolve("repository/catalog/sar/snapshot-sar.yaml"),
            _repository_catalog("sar", "snapshot-sar", "active", Vector(
              ("2.1.0-SNAPSHOT", "snapshot", "active")
            ), Some("2.1.0-SNAPSHOT"), None, Some("2.1.0-SNAPSHOT"))
          )
          Vector(
            "car/org/example/mismatched/mismatched-car.yaml" -> ("car" -> "mismatched-car"),
            "car/org/example/unindexed/unindexed-car.yaml" -> ("car" -> "unindexed-car"),
            "sar/unindexed-sar.yaml" -> ("sar" -> "unindexed-sar")
          ).foreach { case (relative, (kind, artifactid)) =>
            _write(
              dir.resolve("repository/catalog").resolve(relative),
              if (kind == "car")
                _repository_catalog(kind, artifactid, if (artifactid == "mismatched-car") "deprecated" else "active", Vector.empty, None, None, None)
              else
                s"""schemaVersion: 1
                   |kind: $kind
                   |artifactId: $artifactid
                   |status: active
                   |versions: []
                   |""".stripMargin
            )
          }
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--strategy", "preview", "--no-bib-service")
          )

          When("Cozy builds Component Repository knowledge without SIE Project references")
          CozyBok.build(config, new RepositoryCarBuildRunner)

          Then("only indexed CAR and SAR identities are rendered")
          val carmetadata = _read(dir.resolve("doxsite.d/metadata/repository/car/index.json"))
          val carentries = _metadata_entries(carmetadata, "artifact_id")
          carentries.keySet shouldBe Set("disabled-car", "indexed-car")
          carentries("disabled-car").hcursor.get[String]("status").fold(throw _, identity) shouldBe "disabled"
          carentries("indexed-car").hcursor.get[String]("latest_snapshot").fold(throw _, identity) shouldBe "1.1.0-SNAPSHOT"
          carmetadata should not include "unindexed-car"
          carmetadata should include("index-catalog-mismatch")
          val sarmetadata = _read(dir.resolve("doxsite.d/metadata/cncf/component-references/sar.json"))
          val sarentries = _metadata_entries(sarmetadata, "name")
          sarentries.keySet shouldBe Set("indexed-sar", "snapshot-sar")
          sarentries("snapshot-sar").hcursor.get[String]("latest_snapshot").fold(throw _, identity) shouldBe "2.1.0-SNAPSHOT"
          sarmetadata should not include "unindexed-sar"
          sarmetadata should include("index-catalog-unavailable")
          val carindex = _read(dir.resolve("website.d/repository/car/index.html"))
          carindex should include("indexed-car")
          carindex should include("disabled-car")
          val sarindex = _read(dir.resolve("website.d/repository/sar/index.html"))
          sarindex should include("indexed-sar")
          sarindex should include("snapshot-sar")
          dir.resolve("website.d/repository/sar/indexed-sar/index.html") should be_regular_file
          dir.resolve("website.d/repository/sar/snapshot-sar/index.html") should be_regular_file
          sarindex should not include "SIE SAR"

          And("the Component Repository dashboard exposes CAR and SAR counts and navigation")
          val dashboard = _read(dir.resolve("website.d/repository/index.html"))
          dashboard should include("data-component-kind=\"car\"")
          dashboard should include("data-component-kind=\"sar\"")
          dashboard should include("href=\"car/index.html\"")
          dashboard should include("href=\"sar/index.html\"")
          dashboard should include("<span class=\"bok-component-repository-count\">2</span>")
          dashboard should include("href=\"../repository/index.html\"")

          And("index and catalog diagnostics remain visible on repository maintenance surfaces")
          dashboard should include("data-repository-diagnostic-code=\"index-catalog-mismatch\"")
          dashboard should include("data-repository-diagnostic-code=\"index-catalog-unavailable\"")
          dashboard should include("data-repository-diagnostic-code=\"index-catalog-invalid\"")
          dashboard should include("car/org/example/mismatched/mismatched-car.yaml")
          dashboard should include("sar/missing-sar.yaml")
          dashboard should include("sar/invalid-sar.yaml")
          sarindex should include("data-repository-diagnostic-code=\"index-catalog-unavailable\"")
          sarindex should include("data-repository-diagnostic-code=\"index-catalog-invalid\"")
          sarindex should include("SAR missing-sar")
          sarindex should include("SAR invalid-sar")

          And("repository detail navigation resolves from each generated page depth")
          _read(dir.resolve("website.d/repository/car/indexed-car/index.html")) should include("href=\"../../../repository/index.html\"")
          _read(dir.resolve("website.d/repository/sar/indexed-sar/index.html")) should include("href=\"../../../repository/index.html\"")
        }
      }

      "report a malformed public index without failing the build" in {
        _with_temp_dir("cozy-bok-component-repository-invalid-index") { dir =>
          Given("a BoK source tree with a malformed Component Repository index")
          _write(dir.resolve("src/main/doxsite/site.conf"), "site { output { locale_mode = \"single_locale_root\" } }\n")
          _write(dir.resolve("src/main/doxsite/index.dox"), "Home\n====\n")
          _write(dir.resolve("repository/catalog/index.json"), "{not-json")
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--strategy", "preview", "--no-bib-service")
          )

          When("Cozy builds Component Repository knowledge")
          CozyBok.build(config, new RepositoryCarBuildRunner)

          Then("CAR and SAR metadata report the invalid index without directory fallback")
          _read(dir.resolve("doxsite.d/metadata/repository/car/index.json")) should include("repository-index-invalid")
          _read(dir.resolve("doxsite.d/metadata/cncf/component-references/sar.json")) should include("repository-index-invalid")
          _read(dir.resolve("website.d/repository/car/index.html")) should include("Component Repository index")

          And("the repository navigation remains available with explicit empty CAR and SAR surfaces")
          val dashboard = _read(dir.resolve("website.d/repository/index.html"))
          dashboard should include("data-component-kind=\"car\"")
          dashboard should include("data-component-kind=\"sar\"")
          dashboard should include("<span class=\"bok-component-repository-count\">0</span>")
          _read(dir.resolve("website.d/repository/sar/index.html")) should include("Repository SAR catalogはまだありません。")

          And("the malformed index diagnostic is visible on repository maintenance surfaces")
          dashboard should include("data-repository-diagnostic-code=\"repository-index-invalid\"")
          _read(dir.resolve("website.d/repository/sar/index.html")) should include("data-repository-diagnostic-code=\"repository-index-invalid\"")
        }
      }

      "exclude symbolic-link catalogs from fallback discovery" in {
        _with_temp_dir("cozy-bok-component-repository-no-follow") { dir =>
          Given("a fallback repository with catalog-file and ancestor-directory links to external CAR catalogs")
          _write(dir.resolve("src/main/doxsite/site.conf"), "site { output { locale_mode = \"single_locale_root\" } }\n")
          _write(dir.resolve("src/main/doxsite/index.dox"), "Home\n====\n")
          val externalleaf = dir.resolve("external/leaf/org/example/unindexed/unindexed-car.yaml")
          _write(externalleaf, _repository_catalog("car", "unindexed-car", "active", Vector.empty, None, None, None))
          val leaflink = dir.resolve("repository/catalog/car/leaf-link.yaml")
          Option(leaflink.getParent).foreach(Files.createDirectories(_))
          Files.createSymbolicLink(leaflink, externalleaf)
          val externalancestor = dir.resolve("external/ancestor")
          _write(
            externalancestor.resolve("example/unindexed/unindexed-car.yaml"),
            _repository_catalog("car", "unindexed-car", "active", Vector.empty, None, None, None)
          )
          val ancestorlink = dir.resolve("repository/catalog/car/org")
          Files.createSymbolicLink(ancestorlink, externalancestor)
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--no-bib-service"))

          When("Cozy discovers fallback CAR catalogs")
          CozyBok.build(config, new RepositoryCarBuildRunner)

          Then("neither external catalog is consumed through a symbolic link")
          val metadata = _read(dir.resolve("doxsite.d/metadata/repository/car/index.json"))
          metadata should not include "unindexed-car"
          _read(dir.resolve("website.d/repository/car/index.html")) should not include "unindexed-car"
        }
      }

      "report indexed symbolic-link catalogs as unavailable" in {
        _with_temp_dir("cozy-bok-component-repository-index-no-follow") { dir =>
          Given("a public index whose canonical catalog path is a symbolic link")
          _write(dir.resolve("src/main/doxsite/site.conf"), "site { output { locale_mode = \"single_locale_root\" } }\n")
          _write(dir.resolve("src/main/doxsite/index.dox"), "Home\n====\n")
          _write(
            dir.resolve("repository/catalog/index.json"),
            """{"schemaVersion":"cncf.component-repository-index.v2","generatedAt":"2026-07-21T00:00:00Z","artifacts":[{"kind":"car","namespace":"org.example.unindexed","id":"Car","artifactId":"unindexed-car","catalog":"car/org/example/unindexed/unindexed-car.yaml","status":"active"}]}"""
          )
          val external = dir.resolve("external/unindexed-car.yaml")
          _write(external, _repository_catalog("car", "unindexed-car", "active", Vector.empty, None, None, None))
          val link = dir.resolve("repository/catalog/car/org/example/unindexed/unindexed-car.yaml")
          Option(link.getParent).foreach(Files.createDirectories(_))
          Files.createSymbolicLink(link, external)
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--no-bib-service"))

          When("Cozy resolves indexed CAR catalogs")
          CozyBok.build(config, new RepositoryCarBuildRunner)

          Then("the linked catalog is diagnosed as unavailable and excluded")
          val metadata = _read(dir.resolve("doxsite.d/metadata/repository/car/index.json"))
          metadata should include("index-catalog-unavailable")
          metadata should not include "unindexed-car\" :"
        }
      }
    }
    "diagnose CAR archive metadata" which {
      "report missing and mismatched descriptor coordinates without rejecting the build" in {
        _with_temp_dir("cozy-bok-repository-car-diagnostics") { dir =>
          Given("a repository catalog with incomplete, mismatched, and matching CAR archive metadata")
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            "site { output { locale_mode = \"single_locale_root\" } }\n"
          )
          _write(dir.resolve("src/main/doxsite/index.dox"), "Home\n====\n")
          _write(
            dir.resolve("repository/catalog/car/org/example/sample/sample-car.yaml"),
            """schemaVersion: 2
              |kind: car
              |namespace: org.example.sample
              |id: Car
              |artifactId: sample-car
              |recommended: 0.2.0
              |versions:
              |  - version: 0.1.0
              |    component: org.example.sample.Car
              |    file: repository/car/org/example/sample/sample-car/0.1.0/sample-car-0.1.0.car
              |    checksum:
              |      sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
              |    integrityKey: org.example.sample:sample-car:0.1.0@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
              |  - version: 0.2.0
              |    component: org.example.sample.Car
              |    file: repository/car/org/example/sample/sample-car/0.2.0/sample-car-0.2.0.car
              |    checksum:
              |      sha256: bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
              |    integrityKey: org.example.sample:sample-car:0.2.0@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
              |  - version: 0.3.0
              |    component: org.example.sample.Car
              |    file: repository/car/org/example/sample/sample-car/0.3.0/sample-car-0.3.0.car
              |    checksum:
              |      sha256: cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
              |    integrityKey: org.example.sample:sample-car:0.3.0@sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
              |  - version: 0.4.0
              |    component: org.example.sample.Car
              |    file: repository/car/org/example/sample/sample-car/0.4.0/sample-car-0.4.0.car
              |    checksum:
              |      sha256: dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd
              |    integrityKey: org.example.sample:sample-car:0.4.0@sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd
              |""".stripMargin
          )
          _write_car_archive_entries(
            dir.resolve("repository/car/org/example/sample/sample-car/0.1.0/sample-car-0.1.0.car"),
            None,
            None
          )
          _write_car_archive_entries(
            dir.resolve("repository/car/org/example/sample/sample-car/0.2.0/sample-car-0.2.0.car"),
            Some("""{"schemaVersion":3,"component":{"namespace":"org.example.other","id":"Car","version":"9.0.0"}}"""),
            Some("""{"format":"cozy.car.abi-manifest.v2","component":{"namespace":"org.example.other","id":"Car","version":"9.0.0"},"abi":{"version":1,"exports":{"components":[]}}}""")
          )
          _write_car_archive_entries(
            dir.resolve("repository/car/org/example/sample/sample-car/0.3.0/sample-car-0.3.0.car"),
            Some("""{"component":{"name":"org.example.sample.Car","version":"0.3.0","kind":"component"}}"""),
            Some("""{"car":{"name":"sample-car","version":"0.3.0"},"abi":{"version":1,"exports":{}}}""")
          )
          _write_car_archive_entries(
            dir.resolve("repository/car/org/example/sample/sample-car/0.4.0/sample-car-0.4.0.car"),
            Some("""{"schemaVersion":3,"component":{"namespace":"org.example.sample","id":"Car","version":"0.4.0"}}"""),
            Some("""{"format":"cozy.car.abi-manifest.v2","component":{"namespace":"org.example.sample","id":"Car","version":"0.4.0"},"abi":{"version":1,"exports":{"components":[]}}}""")
          )
          val archivechecksums = Vector("0.1.0", "0.2.0", "0.3.0", "0.4.0").map { version =>
            val path = dir.resolve(s"repository/car/org/example/sample/sample-car/$version/sample-car-$version.car")
            version -> RepositoryArtifactPublisher.sha256(path)
          }.toMap
          _write(
            dir.resolve("repository/catalog/car/org/example/sample/sample-car.yaml"),
            _repository_catalog(
              "car",
              "sample-car",
              "active",
              Vector(
                ("0.1.0", "stable", "active"),
                ("0.2.0", "stable", "active"),
                ("0.3.0", "stable", "active"),
                ("0.4.0", "stable", "active")
              ),
              Some("0.2.0"),
              None,
              None,
              archivechecksums
            )
          )
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--strategy", "preview", "--no-bib-service")
          )

          When("Cozy builds repository CAR knowledge")
          CozyBok.build(config, new RepositoryCarBuildRunner)

          Then("machine metadata distinguishes missing entries from coordinate mismatches")
          val metadata = parser.parse(
            _read(dir.resolve("doxsite.d/metadata/repository/car/index.json"))
          ).fold(throw _, identity)
          val diagnostics = metadata.hcursor.downField("diagnostics").as[Vector[io.circe.Json]].fold(throw _, identity)
          val codes = diagnostics.map { diagnostic =>
            val cursor = diagnostic.hcursor
            (
              cursor.get[String]("code").fold(throw _, identity),
              cursor.get[Option[String]]("version").fold(throw _, identity)
            )
          }.toSet
          codes should contain("archive-without-component-descriptor" -> Some("0.1.0"))
          codes should contain("archive-without-abi-manifest" -> Some("0.1.0"))
          codes should contain("component-descriptor-coordinate-mismatch" -> Some("0.2.0"))
          codes should contain("abi-manifest-coordinate-mismatch" -> Some("0.2.0"))
          codes should not contain ("component-descriptor-coordinate-mismatch" -> Some("0.3.0"))
          codes should not contain ("abi-manifest-coordinate-mismatch" -> Some("0.3.0"))
          codes should not contain ("component-descriptor-coordinate-mismatch" -> Some("0.4.0"))
          codes should not contain ("abi-manifest-coordinate-mismatch" -> Some("0.4.0"))
          codes should not contain ("archive-without-component-descriptor" -> Some("0.4.0"))
          diagnostics.map(_.noSpaces).mkString should include("\"metadata_name\":\"org.example.other.Car\"")
          diagnostics.map(_.noSpaces).mkString should include("\"metadata_version\":\"9.0.0\"")

          And("the maintainer diagnostic card explains the archive problems")
          val page = _read(dir.resolve("website.d/repository/car/index.html"))
          page should include("CARリポジトリ診断")
          page should include("CARにcomponent-descriptor.jsonがありません。")
          page should include("ABI manifestの座標がcatalogと一致しません。")
          page should include("metadata座標")
          page should include("data-bok-actors=\"contributor project_manager\"")
        }
      }

      "reject a substituted CAR archive before descriptor metadata is extracted" in {
        _with_temp_dir("cozy-bok-repository-car-integrity") { dir =>
          Given("a canonical CAR catalog whose available archive bytes do not match its catalog digest")
          _write(dir.resolve("src/main/doxsite/site.conf"), "site { output { locale_mode = \"single_locale_root\" } }\n")
          _write(dir.resolve("src/main/doxsite/index.dox"), "Home\n====\n")
          val archive = dir.resolve("repository/car/org/example/sample/sample-car/1.0.0/sample-car-1.0.0.car")
          _write_car_archive_entries(
            archive,
            Some("""{"schemaVersion":3,"component":{"namespace":"org.example.sample","id":"Car","version":"1.0.0"}}"""),
            None
          )
          _write(
            dir.resolve("repository/catalog/car/org/example/sample/sample-car.yaml"),
            _repository_catalog(
              "car",
              "sample-car",
              "active",
              Vector(("1.0.0", "stable", "active")),
              Some("1.0.0"),
              Some("1.0.0"),
              None
            )
          )
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--no-bib-service"))

          When("Cozy reads repository CAR archive metadata")
          val error = intercept[IllegalArgumentException] {
            CozyBok.build(config, new RepositoryCarBuildRunner)
          }

          Then("the archive is rejected at checksum admission before descriptor or ABI diagnostics are accepted")
          error.getMessage should include("component.repository.integrity.mismatch source=archive field=checksum.sha256")
          error.getMessage should not include "component-descriptor-coordinate-mismatch"
        }
      }
    }
  }

  private class RepositoryCarBuildRunner extends CozyBok.Runner {
    def run(command: Vector[String], cwd: Path): Unit =
      if (command.take(2) == Vector("dox", "site")) {
        _write(
          cwd.resolve("doxsite.d/metadata/dashboard/site.json"),
          """{
            |  "counts": {
            |    "category_count": 0,
            |    "article_count": 0,
            |    "glossary_term_count": 0,
            |    "total_item_count": 0
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
        )
        _write(cwd.resolve("doxsite.d/site.ttl"), "@prefix ex: <https://example.com/> .\n")
        _write(cwd.resolve("doxsite.d/site.jsonld"), "{\"@graph\":[]}\n")
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

  private def _metadata_entries(content: String, identity_field: String): Map[String, io.circe.Json] = {
    val metadata = parser.parse(content).fold(throw _, identity)
    val entries = metadata.hcursor.downField("entries").as[Vector[io.circe.Json]].fold(throw _, identity).map { entry =>
      entry.hcursor.get[String](identity_field).fold(throw _, identity) -> entry
    }
    withClue(s"metadata entries must have unique $identity_field values: ") {
      entries.map(_._1).distinct should have size entries.size
    }
    entries.toMap
  }

  private def _repository_catalog(
    kind: String,
    artifact_id: String,
    status: String,
    versions: Vector[(String, String, String)],
    recommended: Option[String],
    latest_stable: Option[String],
    latest_snapshot: Option[String],
    checksums: Map[String, String] = Map.empty
  ): String = {
    val caridentity = Map(
      "indexed-car" -> ("org.example.indexed" -> "Car"),
      "disabled-car" -> ("org.example.disabled" -> "Car"),
      "mismatched-car" -> ("org.example.mismatched" -> "Car"),
      "unindexed-car" -> ("org.example.unindexed" -> "Car"),
      "sample-car" -> ("org.example.sample" -> "Car")
    ).get(artifact_id)
    val namespace = caridentity.map(_._1)
    val id = caridentity.map(_._2)
    val selectors = Vector(
      recommended.map(value => s"recommended: $value"),
      latest_stable.map(value => s"latestStable: $value"),
      latest_snapshot.map(value => s"latestSnapshot: $value")
    ).flatten.mkString("\n")
    val version_entries = versions.map { case (version, channel, version_status) =>
      if (kind == "car") {
        val checksum = checksums.getOrElse(version, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        s"""  - version: $version
           |    channel: $channel
           |    status: $version_status
           |    component: ${namespace.get}.${id.get}
           |    file: repository/car/${namespace.get.replace('.', '/')}/$artifact_id/$version/$artifact_id-$version.car
           |    checksum:
           |      sha256: $checksum
           |    integrityKey: ${namespace.get}:$artifact_id:$version@sha256:$checksum""".stripMargin
      }
      else
        s"""  - version: $version
           |    channel: $channel
           |    status: $version_status
           |    file: repository/$kind/$artifact_id/$version/$artifact_id-$version.$kind""".stripMargin
    }.mkString("\n")
    if (kind == "car")
      s"""schemaVersion: 2
         |kind: car
         |namespace: ${namespace.get}
         |id: ${id.get}
         |artifactId: $artifact_id
         |$selectors
         |status: $status
         |versions:
         |$version_entries
         |""".stripMargin
    else
      s"""schemaVersion: 1
         |kind: $kind
         |artifactId: $artifact_id
         |$selectors
         |status: $status
         |versions:
         |$version_entries
         |""".stripMargin
  }

  private def _write_car_archive(path: Path): Path = {
    _write_car_archive_entries(
      path,
      Some(
        """{
          |  "schemaVersion": 3,
          |  "component": {"namespace": "org.example.textus", "id": "Sie", "version": "0.1.0"},
          |  "links": {
          |    "help": "/help/TextusSie",
          |    "manual": "/man/TextusSie",
          |    "openapi": "/openapi.json",
          |    "mcp": "/mcp"
          |  },
          |  "entities": [{"entity": "KnowledgeItem"}]
          |}
          |""".stripMargin
      ),
      Some(
        """{
          |  "format": "cozy.car.abi-manifest.v2",
          |  "component": {"namespace": "org.example.textus", "id": "Sie", "version": "0.1.0"},
          |  "abi": {
            |    "version": 1,
            |    "exports": {
            |      "components": [{"namespace": "org.example.textus", "id": "Sie"}],
          |      "operations": [{"name": "knowledge.search", "kind": "query"}],
          |      "entities": [{"name": "KnowledgeItem", "fields": []}]
          |    },
          |    "dependencies": []
          |  }
          |}
          |""".stripMargin
      )
    )
  }

  private def _write_car_archive_entries(
    path: Path,
    componentdescriptor: Option[String],
    abimanifest: Option[String],
    extraentries: Vector[(String, String)] = Vector.empty
  ): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val zip = new ZipOutputStream(Files.newOutputStream(path))
    try {
      def _entry_(name: String, content: String): Unit = {
        zip.putNextEntry(new ZipEntry(name))
        zip.write(content.getBytes(StandardCharsets.UTF_8))
        zip.closeEntry()
      }
      componentdescriptor.foreach(_entry_("component-descriptor.json", _))
      abimanifest.foreach(_entry_("abi-manifest.json", _))
      extraentries.foreach { case (name, content) => _entry_(name, content) }
    } finally {
      zip.close()
    }
    path
  }

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
