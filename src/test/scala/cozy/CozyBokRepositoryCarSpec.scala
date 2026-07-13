package cozy

import cozy.bok.CozyBok
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 13, 2026
 * @version Jul. 13, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyBokRepositoryCarSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy BoK repository CAR knowledge" should {
    "resolve repository catalog roots" which {
      "read CAR catalogs from an explicit warehouse operation" in {
        _with_temp_dir("cozy-bok-repository-car-warehouse") { dir =>
          Given("a BoK source tree and a CAR catalog under an external warehouse repository")
          val warehouse = dir.resolve("warehouse")
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            "site { output { locale_mode = \"single_locale_root\" } }\n"
          )
          _write(dir.resolve("src/main/doxsite/index.dox"), "Home\n====\n")
          _write(
            warehouse.resolve("repository/catalog/car/textus-sie.yaml"),
            """schemaVersion: 1
              |kind: car
              |artifactId: textus-sie
              |recommended: 0.1.0
              |versions:
              |  - version: 0.1.0
              |    channel: stable
              |    file: repository/car/textus-sie/0.1.0/textus-sie-0.1.0.car
              |""".stripMargin
          )
          _write(
            warehouse.resolve("repository/catalog/car/textus-sie.cml"),
            "# COMPONENT\n\n## TextusSie\n"
          )
          _write(
            warehouse.resolve("repository/catalog/car/textus-sie.model-metadata.json"),
            "{\"schema\":\"cozy.cml.model-metadata.v1\"}\n"
          )
          _write(
            warehouse.resolve("repository/catalog/car/textus-sie.model-metadata.yaml"),
            "schema: cozy.cml.model-metadata.v1\n"
          )
          _write_car_archive(
            warehouse.resolve("repository/car/textus-sie/0.1.0/textus-sie-0.1.0.car")
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
          metadata should include(""""source_path" : "warehouse/repository/catalog/car/textus-sie.yaml"""")
          metadata should include(""""cml" : "repository/catalog/car/textus-sie.cml"""")
          metadata should include(""""model_metadata_json" : "repository/catalog/car/textus-sie.model-metadata.json"""")
          metadata should include(""""model_metadata_yaml" : "repository/catalog/car/textus-sie.model-metadata.yaml"""")
          metadata should include("\"component_descriptor\" : {")
          metadata should include(""""component" : "TextusSie"""")
          metadata should include("\"abi_manifest\" : {")
          metadata should include(""""format" : "cozy.car.abi-manifest.v1"""")

          And("the generated website exposes the CAR entry and its public sidecars")
          _read(dir.resolve("website.d/metadata/repository/car/index.json")) should include("textus-sie")
          val page = _read(dir.resolve("website.d/repository/car/index.html"))
          page should include("textus-sie")
          page should include("warehouse/repository/catalog/car/textus-sie.yaml")
          dir.resolve("website.d/repository/catalog/car/textus-sie.cml") should be_regular_file
          dir.resolve("website.d/repository/catalog/car/textus-sie.model-metadata.json") should be_regular_file
          dir.resolve("website.d/repository/catalog/car/textus-sie.model-metadata.yaml") should be_regular_file
          val modulepage = _read(dir.resolve("website.d/repository/car/textus-sie/index.html"))
          modulepage should include("../../catalog/car/textus-sie.cml")
          modulepage should include("モデルメタデータ (JSON)")
          modulepage should include("モデルメタデータ (YAML)")
          modulepage should include("コンポーネント記述子")
          modulepage should include("textus-sie 0.1.0 / TextusSie / entities 1")
          modulepage should include("ABIマニフェスト")
          modulepage should include("ABI 1 / components 1 / operations 1 / entities 1")
          val versionpage = _read(dir.resolve("website.d/repository/car/textus-sie/0.1.0.html"))
          versionpage should include("../../catalog/car/textus-sie.cml")
          versionpage should include("コンポーネント記述子")
          versionpage should include("ABIマニフェスト")
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

  private def _write_car_archive(path: Path): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val zip = new ZipOutputStream(Files.newOutputStream(path))
    try {
      def _entry_(name: String, content: String): Unit = {
        zip.putNextEntry(new ZipEntry(name))
        zip.write(content.getBytes(StandardCharsets.UTF_8))
        zip.closeEntry()
      }
      _entry_(
        "component-descriptor.json",
        """{
          |  "name": "textus-sie",
          |  "version": "0.1.0",
          |  "component": "TextusSie",
          |  "entities": [{"entity": "KnowledgeItem"}]
          |}
          |""".stripMargin
      )
      _entry_(
        "abi-manifest.json",
        """{
          |  "format": "cozy.car.abi-manifest.v1",
          |  "car": {"name": "textus-sie", "version": "0.1.0"},
          |  "abi": {
          |    "version": 1,
          |    "exports": {
          |      "components": [{"name": "TextusSie"}],
          |      "operations": [{"name": "knowledge.search", "kind": "query"}],
          |      "entities": [{"name": "KnowledgeItem", "fields": []}]
          |    },
          |    "dependencies": []
          |  }
          |}
          |""".stripMargin
      )
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
