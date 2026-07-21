package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.Instant
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
class ComponentRepositoryIndexSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Component Repository index" should {
    "normalize the CNCF discovery contract" which {
      "renders CAR and SAR entries in deterministic identity order" in {
        Given("an index whose SAR entry precedes its CAR entry")
        val source = ComponentRepositoryIndex(
          ComponentRepositoryIndex.SchemaVersion,
          Instant.parse("2026-07-21T00:00:00Z"),
          Vector(
            _entry("sar", "sample-app"),
            _entry("car", "sample-component")
          )
        )

        When("the index is rendered and parsed")
        val result = ComponentRepositoryIndex.parse(source.render)

        Then("the canonical identity order and field names are preserved")
        result.artifacts.map(_.identity) shouldBe Vector("car" -> "sample-component", "sar" -> "sample-app")
        result.render should include("\"schemaVersion\" : \"cncf.component-repository-index.v1\"")
        result.render should include("\"artifactId\" : \"sample-component\"")
      }

      "rejects duplicate identities and unsafe catalog paths" in {
        Given("malformed repository index entries")
        val duplicate = _index_json(
          """{
            |      "kind": "car",
            |      "artifactId": "sample",
            |      "catalog": "car/sample.yaml",
            |      "status": "active"
            |    }, {
            |      "kind": "car",
            |      "artifactId": "sample",
            |      "catalog": "car/sample.yaml",
            |      "status": "active"
            |    }""".stripMargin
        )
        val traversal = _index_json(
          """{
            |      "kind": "car",
            |      "artifactId": "sample",
            |      "catalog": "../car/sample.yaml",
            |      "status": "active"
            |    }""".stripMargin
        )

        When("Cozy parses each malformed index")
        val duplicateerror = intercept[IllegalArgumentException](ComponentRepositoryIndex.parse(duplicate))
        val traversalerror = intercept[IllegalArgumentException](ComponentRepositoryIndex.parse(traversal))

        Then("the contract fails before publication")
        duplicateerror.getMessage should include("Duplicate")
        traversalerror.getMessage should include("Invalid component repository catalog path")
      }
    }

    "maintain publication state" which {
      "merges unrelated entries and removes an entry whose catalog is empty" in {
        Given("an existing SAR entry and a release CAR catalog")
        val timestamp = Instant.parse("2026-07-21T01:02:03Z")
        val existing = ComponentRepositoryIndex(
          ComponentRepositoryIndex.SchemaVersion,
          timestamp,
          Vector(_entry("sar", "sample-app"))
        )
        val car = _catalog("car", "sample-component", versions = Vector(_version("car", "sample-component")))

        When("the CAR is added and later removed")
        val added = ComponentRepositoryIndex.update(Some(existing), car, timestamp)
        val removed = ComponentRepositoryIndex.update(Some(added), car.copy(versions = Vector.empty, recommended = None, latestStable = None), timestamp)

        Then("the unrelated SAR remains throughout both updates")
        added.artifacts.map(_.identity) shouldBe Vector("car" -> "sample-component", "sar" -> "sample-app")
        removed.artifacts.map(_.identity) shouldBe Vector("sar" -> "sample-app")
      }

      "validates every summary against its detailed catalog" in {
        _with_temp_dir("component-repository-index-detail") { dir =>
          Given("a public index and matching detailed CAR catalog")
          val indexpath = dir.resolve("repository/catalog/index.json")
          val catalog = _catalog("car", "sample-component", versions = Vector(_version("car", "sample-component")))
          _write(dir.resolve("repository/catalog/car/sample-component.yaml"), catalog.toYaml)
          val index = ComponentRepositoryIndex.update(None, catalog, Instant.parse("2026-07-21T01:02:03Z"))

          When("Cozy validates and atomically writes the index")
          ComponentRepositoryIndex.validateCatalogs(index, indexpath)
          ComponentRepositoryIndex.writeAtomic(indexpath, index)

          Then("the persisted index remains valid and stale selectors are rejected")
          ComponentRepositoryIndex.load(indexpath) shouldBe index
          val stale = index.copy(artifacts = index.artifacts.map(_.copy(recommended = Some("0.0.9"))))
          val error = intercept[IllegalArgumentException](ComponentRepositoryIndex.validateCatalogs(stale, indexpath))
          error.getMessage should include("selector is stale")
        }
      }

      "preserves an unrelated CAR when Cozy publishes a SAR" in {
        _with_temp_dir("component-repository-index-publish-merge") { dir =>
          Given("a warehouse index containing a CAR and a separate SAR project")
          val warehouse = dir.resolve("warehouse")
          val indexpath = warehouse.resolve("repository/catalog/index.json")
          val car = _catalog("car", "sample-component", versions = Vector(_version("car", "sample-component")))
          _write(warehouse.resolve("repository/catalog/car/sample-component.yaml"), car.toYaml)
          ComponentRepositoryIndex.writeAtomic(
            indexpath,
            ComponentRepositoryIndex.update(None, car, Instant.parse("2026-07-21T01:02:03Z"))
          )
          val project = dir.resolve("sar-project")
          _write(project.resolve("project.yaml"), "project:\n  name: sample-app\npackaging:\n  kind: sar\n")
          val archive = _write(dir.resolve("sample-app.sar"), "sar-body")

          When("Cozy publishes the SAR into the same warehouse")
          CozySarPublisher.publish(List(
            project.toString,
            "--warehouse", warehouse.toString,
            "--name", "sample-app",
            "--version", "1.0.0",
            "--sar", archive.toString,
            "--published-at", "2026-07-21T02:03:04Z"
          ))

          Then("the index contains both artifact kinds in canonical order")
          ComponentRepositoryIndex.load(indexpath).artifacts.map(_.identity) shouldBe Vector(
            "car" -> "sample-component",
            "sar" -> "sample-app"
          )
        }
      }

      "rejects an invalid existing index before changing the warehouse" in {
        _with_temp_dir("component-repository-index-invalid-preflight") { dir =>
          Given("an invalid warehouse index and a publishable SAR")
          val warehouse = dir.resolve("warehouse")
          val indexpath = warehouse.resolve("repository/catalog/index.json")
          val invalid = _index_json(
            """{
              |      "kind": "car",
              |      "artifactId": "sample",
              |      "catalog": "car/sample.yaml",
              |      "status": "active"
              |    }, {
              |      "kind": "car",
              |      "artifactId": "sample",
              |      "catalog": "car/sample.yaml",
              |      "status": "active"
              |    }""".stripMargin
          )
          _write(indexpath, invalid)
          val project = dir.resolve("sar-project")
          _write(project.resolve("project.yaml"), "project:\n  name: sample-app\npackaging:\n  kind: sar\n")
          val archive = _write(dir.resolve("sample-app.sar"), "sar-body")

          When("Cozy attempts to publish the SAR")
          val error = intercept[IllegalArgumentException] {
            CozySarPublisher.publish(List(
              project.toString,
              "--warehouse", warehouse.toString,
              "--name", "sample-app",
              "--version", "1.0.0",
              "--sar", archive.toString,
              "--published-at", "2026-07-21T02:03:04Z"
            ))
          }

          Then("publication fails before copying the archive or replacing the index")
          error.getMessage should include("Duplicate")
          Files.exists(warehouse.resolve("repository/sar/sample-app/1.0.0/sample-app-1.0.0.sar")) shouldBe false
          Files.readString(indexpath) shouldBe invalid
        }
      }
    }
  }

  private def _entry(kind: String, id: String): ComponentRepositoryIndexEntry =
    ComponentRepositoryIndexEntry(kind, id, s"$kind/$id.yaml", "active", Some("1.0.0"), Some("1.0.0"), None)

  private def _catalog(kind: String, id: String, versions: Vector[RepositoryArtifactCatalogVersion]): RepositoryArtifactCatalog =
    RepositoryArtifactCatalog("1", kind, id, Some("1.0.0"), Some("1.0.0"), None, Some("active"), Vector.empty, versions)

  private def _version(kind: String, id: String): RepositoryArtifactCatalogVersion =
    RepositoryArtifactCatalogVersion(
      "1.0.0",
      Some("stable"),
      Some("active"),
      None,
      Some("2026-07-21T01:02:03Z"),
      Some(s"repository/$kind/$id/1.0.0/$id-1.0.0.$kind"),
      None,
      Some("abc")
    )

  private def _index_json(entries: String): String =
    s"""{
       |  "schemaVersion": "cncf.component-repository-index.v1",
       |  "generatedAt": "2026-07-21T00:00:00Z",
       |  "artifacts": [$entries]
       |}
       |""".stripMargin

  private def _write(path: Path, text: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, text.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val dir = Files.createTempDirectory(prefix)
    try body(dir)
    finally {
      val stream = Files.walk(dir)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
      finally stream.close()
    }
  }
}
