package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.Instant
import java.security.MessageDigest
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 21, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
class ComponentRepositoryIndexSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Component Repository index" should {
    "normalize the CNCF discovery contract" which {
      "renders CAR and SAR entries in deterministic identity order" in {
        Given("an index whose SAR entry precedes its CAR entry")
        val source = ComponentRepositoryIndex(
          ComponentRepositoryIndex.SCHEMA_VERSION,
          Instant.parse("2026-07-21T00:00:00Z"),
          Vector(
            _entry("sar", "sample-app"),
            _entry("car", _car_artifact_id)
          )
        )

        When("the index is rendered and parsed")
        val result = ComponentRepositoryIndex.parse(source.render)

        Then("the canonical identity order and field names are preserved")
        result.artifacts.map(_.identity) shouldBe Vector(
          ("car", "org.example", "Sample"),
          ("sar", "", "sample-app")
        )
        result.render should include(s""""schemaVersion" : "${ComponentRepositoryIndex.SCHEMA_VERSION}"""")
        result.render should include("\"artifactId\" : \"example-sample\"")
      }

      "rejects duplicate identities and unsafe catalog paths" in {
        Given("malformed repository index entries")
        val duplicate = _index_json(
          s"""${_car_index_entry_json()},
             |${_car_index_entry_json()}""".stripMargin
        )
        val traversal = _index_json(
          _car_index_entry_json("../car/org/example/example-sample.yaml")
        )

        When("Cozy parses each malformed index")
        val duplicateerror = intercept[IllegalArgumentException](ComponentRepositoryIndex.parse(duplicate))
        val traversalerror = intercept[IllegalArgumentException](ComponentRepositoryIndex.parse(traversal))

        Then("the contract fails before publication")
        duplicateerror.getMessage should include("Duplicate")
        traversalerror.getMessage should include("projection-mismatch")
      }

      "rejects duplicate JSON object keys before Circe projection" in {
        Given("index JSON with repeated root, entry, and identity fields")
        val schemakey = "\"schemaVersion\": \"" + ComponentRepositoryIndex.SCHEMA_VERSION + "\""
        val rootduplicate = _index_json(
          "{\"kind\":\"car\",\"namespace\":\"org.example\",\"id\":\"Sample\",\"artifactId\":\"example-sample\",\"catalog\":\"car/org/example/example-sample.yaml\",\"status\":\"active\"}"
        ).replace(
          schemakey,
          s"$schemakey, $schemakey"
        )
        val entryduplicate = _index_json(
          s"""{"kind":"car","namespace":"org.example","id":"Sample","artifactId":"example-sample","catalog":"car/org/example/example-sample.yaml","catalog":"car/org/example/example-sample.yaml","status":"active"}"""
        )
        val identityduplicate = _index_json(
          s"""{"kind":"car","artifactId":"example-sample","catalog":"car/org/example/example-sample.yaml","status":"active","identity":{"namespace":"org.example","namespace":"org.example","id":"Sample","version":"1.0.0","version":"1.0.0"}}"""
        )

        When("each duplicate object is parsed")
        val rooterror = intercept[IllegalArgumentException](ComponentRepositoryIndex.parse(rootduplicate))
        val entryerror = intercept[IllegalArgumentException](ComponentRepositoryIndex.parse(entryduplicate))
        val identityerror = intercept[IllegalArgumentException](ComponentRepositoryIndex.parse(identityduplicate))

        Then("the scanner rejects duplicates with deterministic field diagnostics")
        rooterror.getMessage should include("field=schemaVersion")
        entryerror.getMessage should include("field=catalog")
        identityerror.getMessage should include("field=namespace")
      }

      "reports malformed JSON with the stable index diagnostic" in {
        Given("malformed root JSON and malformed entry-object JSON")
        val malformedroot = "{\"schemaVersion\":\"cncf.component-repository-index.v2\","
        val malformedentry = _index_json(
          "{\"kind\":\"car\",\"artifactId\":\"example-sample\",\"catalog\":\"car/org/example/example-sample.yaml\""
        )

        When("each malformed index document is parsed")
        val rooterror = intercept[IllegalArgumentException](ComponentRepositoryIndex.parse(malformedroot))
        val entryerror = intercept[IllegalArgumentException](ComponentRepositoryIndex.parse(malformedentry))

        Then("both parser failures retain the stable production diagnostic")
        rooterror.getMessage should include("Invalid component repository index JSON")
        entryerror.getMessage should include("Invalid component repository index JSON")
      }
    }

    "maintain publication state" which {
      "merges unrelated entries and removes an entry whose catalog is empty" in {
        Given("an existing SAR entry and a release CAR catalog")
        val timestamp = Instant.parse("2026-07-21T01:02:03Z")
        val existing = ComponentRepositoryIndex(
          ComponentRepositoryIndex.SCHEMA_VERSION,
          timestamp,
          Vector(_entry("sar", "sample-app"))
        )
        val car = _catalog("car", _car_artifact_id, versions = Vector(_version("car", _car_artifact_id)))

        When("the CAR is added and later removed")
        val added = ComponentRepositoryIndex.update(Some(existing), car, timestamp)
        val removed = ComponentRepositoryIndex.update(Some(added), car.copy(versions = Vector.empty, recommended = None, latestStable = None), timestamp)

        Then("the unrelated SAR remains throughout both updates")
        added.artifacts.map(_.identity) shouldBe Vector(
          ("car", "org.example", "Sample"),
          ("sar", "", "sample-app")
        )
        removed.artifacts.map(_.identity) shouldBe Vector(("sar", "", "sample-app"))
      }

      "validates every summary against its detailed catalog" in {
        _with_temp_dir("component-repository-index-detail") { dir =>
          Given("a public index and matching detailed CAR catalog")
          val indexpath = dir.resolve("repository/catalog/index.json")
          val catalog = _car_fixture(dir)
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
          val car = _car_fixture(warehouse)
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
            ("car", "org.example", "Sample"),
            ("sar", "", "sample-app")
          )
        }
      }

      "rejects an invalid existing index before changing the warehouse" in {
        _with_temp_dir("component-repository-index-invalid-preflight") { dir =>
          Given("an invalid warehouse index and a publishable SAR")
          val warehouse = dir.resolve("warehouse")
          val indexpath = warehouse.resolve("repository/catalog/index.json")
          val invalid = _index_json(s"${_car_index_entry_json()}, ${_car_index_entry_json()}")
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

  private val _car_namespace = "org.example"
  private val _car_id = "Sample"
  private val _car_artifact_id = "example-sample"
  private val _car_catalog_path = "car/org/example/example-sample.yaml"
  private val _car_archive_path = "repository/car/org/example/example-sample/1.0.0/example-sample-1.0.0.car"
  private val _car_archive_bytes = "component-repository-index-car".getBytes(StandardCharsets.UTF_8)
  private val _car_digest = _sha256(_car_archive_bytes)

  private def _entry(kind: String, artifactid: String): ComponentRepositoryIndexEntry =
    if (kind == "car")
      ComponentRepositoryIndexEntry(
        "car",
        _car_artifact_id,
        _car_catalog_path,
        "active",
        Some("1.0.0"),
        Some("1.0.0"),
        None,
        Some(_car_namespace),
        Some(_car_id)
      )
    else
      ComponentRepositoryIndexEntry("sar", artifactid, s"sar/$artifactid.yaml", "active", Some("1.0.0"), Some("1.0.0"), None)

  private def _catalog(kind: String, id: String, versions: Vector[RepositoryArtifactCatalogVersion]): RepositoryArtifactCatalog =
    if (kind == "car")
      RepositoryArtifactCatalog(
        "2",
        "car",
        _car_artifact_id,
        Some("1.0.0"),
        Some("1.0.0"),
        None,
        Some("active"),
        Vector.empty,
        versions,
        namespace = Some(_car_namespace),
        id = Some(_car_id)
      )
    else
      RepositoryArtifactCatalog("1", "sar", id, Some("1.0.0"), Some("1.0.0"), None, Some("active"), Vector.empty, versions)

  private def _version(kind: String, id: String): RepositoryArtifactCatalogVersion =
    if (kind == "car")
      RepositoryArtifactCatalogVersion(
        "1.0.0",
        Some("stable"),
        Some("active"),
        Some(s"${_car_namespace}.${_car_id}"),
        Some("2026-07-21T01:02:03Z"),
        Some(_car_archive_path),
        None,
        Some(_car_digest),
        Some(s"${_car_namespace}:${_car_artifact_id}:1.0.0@sha256:${_car_digest}")
      )
    else
      RepositoryArtifactCatalogVersion(
        "1.0.0",
        Some("stable"),
        Some("active"),
        None,
        Some("2026-07-21T01:02:03Z"),
        Some(s"repository/sar/$id/1.0.0/$id-1.0.0.sar"),
        None,
        Some("abc")
      )

  private def _car_index_entry_json(catalog: String = _car_catalog_path): String =
    s"""{
       |  "kind": "car",
       |  "namespace": "${_car_namespace}",
       |  "id": "${_car_id}",
       |  "artifactId": "${_car_artifact_id}",
       |  "catalog": "$catalog",
       |  "status": "active",
       |  "recommended": "1.0.0",
       |  "latestStable": "1.0.0"
       |}""".stripMargin

  private def _index_json(entries: String): String =
    s"""{
       |  "schemaVersion": "${ComponentRepositoryIndex.SCHEMA_VERSION}",
       |  "generatedAt": "2026-07-21T00:00:00Z",
       |  "artifacts": [$entries]
       |}
       |""".stripMargin

  private def _materialize_car(root: Path, catalog: RepositoryArtifactCatalog): Unit =
    catalog.versions.foreach { release =>
      val archive = root.resolve(release.file.get)
      Files.createDirectories(archive.getParent)
      Files.write(archive, _car_archive_bytes)
      Files.write(
        archive.resolveSibling(archive.getFileName.toString + ".sha256"),
        (_car_digest + "\n").getBytes(StandardCharsets.UTF_8)
      )
    }

  private def _car_fixture(root: Path): RepositoryArtifactCatalog = {
    val catalog = _catalog("car", _car_artifact_id, versions = Vector(_version("car", _car_artifact_id)))
    _write(root.resolve("repository/catalog").resolve(_car_catalog_path), catalog.toYaml)
    _materialize_car(root, catalog)
    catalog
  }

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(value => "%02x".format(value & 0xff)).mkString

  private def _write(path: Path, text: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, text.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val root = Path.of("target/cozy-test/work/component-repository-index-spec").toAbsolutePath.normalize()
    Files.createDirectories(root)
    val dir = Files.createTempDirectory(root, prefix + "-")
    try body(dir)
    finally {
      val stream = Files.walk(dir)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
      finally stream.close()
    }
  }
}
