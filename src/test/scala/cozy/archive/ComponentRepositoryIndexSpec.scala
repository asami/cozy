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
 *  version Aug.  7, 2026
 * @version Oct.  4, 2026
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

    "admit SAR distribution bytes" which {
      "accepts schema1 records without CAR coordinates or integrity keys" in {
        _with_temp_dir("sar-schema1") { dir =>
          Given("a schema1 SAR catalog, its actual release bytes and exact SHA sidecar")
          val catalog = _sar_fixture(dir)
          val index = _sar_index(catalog)
          When("the distribution index admits the release")
          val admitted = ComponentRepositoryIndex.validateCatalogs(index, dir.resolve(ComponentRepositoryIndex.PUBLIC_PATH))
          Then("SAR identity and schema remain unchanged")
          admitted shouldBe index
          catalog.schemaVersion shouldBe "1"
          catalog.namespace shouldBe empty
          catalog.id shouldBe empty
          catalog.versions.head.integrityKey shouldBe empty
        }
      }

      "rejects archive tampering that preserves size and modification time" in {
        _with_temp_dir("sar-tampered") { dir =>
          Given("an installed SAR and its original checksum records")
          val catalog = _sar_fixture(dir)
          val archive = dir.resolve(catalog.versions.head.file.get)
          val originaltime = Files.getLastModifiedTime(archive)
          val originalsize = Files.size(archive)
          When("actual archive bytes change while size and time remain equal and admission is requested")
          Files.write(archive, "SAR-BODY".getBytes(StandardCharsets.UTF_8))
          Files.setLastModifiedTime(archive, originaltime)
          val error = intercept[IllegalArgumentException] {
            ComponentRepositoryIndex.validateCatalogs(_sar_index(catalog), dir.resolve(ComponentRepositoryIndex.PUBLIC_PATH))
          }
          Then("actual-byte disagreement rejects with the SAR checksum diagnostic")
          Files.size(archive) shouldBe originalsize
          Files.getLastModifiedTime(archive) shouldBe originaltime
          error.getMessage should include("component.repository.integrity.mismatch")
          error.getMessage should include("kind=sar")
          error.getMessage should include("field=checksum.sha256")
        }
      }

      "rejects a missing archive and missing or wrong sidecars" in {
        Vector("archive", "missing-sidecar", "wrong-sidecar").foreach { defect =>
          _with_temp_dir("sar-missing-record") { dir =>
            Given(s"an installed SAR with $defect")
            val catalog = _sar_fixture(dir)
            val archive = dir.resolve(catalog.versions.head.file.get)
            val sidecar = archive.resolveSibling(archive.getFileName.toString + ".sha256")
            defect match {
              case "archive" => Files.delete(archive)
              case "missing-sidecar" => Files.delete(sidecar)
              case _ => _write(sidecar, "0" * 64 + "\n")
            }
            When("the installed distribution is admitted")
            val error = intercept[IllegalArgumentException] {
              ComponentRepositoryIndex.validateCatalogs(_sar_index(catalog), dir.resolve(ComponentRepositoryIndex.PUBLIC_PATH))
            }
            Then("the missing or mismatched record is rejected for the named SAR")
            error.getMessage should include("component.repository.integrity.mismatch")
            error.getMessage should include("kind=sar artifact=sample-app")
            error.getMessage should include(if (defect == "archive") "field=archive" else "field=sha256-sidecar")
          }
        }
      }

      "rejects missing invalid and wrong catalog checksums" in {
        Vector(None, Some("invalid"), Some("0" * 64)).foreach { checksum =>
          _with_temp_dir("sar-catalog-checksum") { dir =>
            Given("actual SAR bytes with an incomplete or disagreeing catalog checksum")
            val original = _sar_fixture(dir)
            val catalog = original.copy(versions = original.versions.map(_.copy(checksumSha256 = checksum)))
            _write(dir.resolve("repository/catalog/sar/sample-app.yaml"), catalog.toYaml)
            When("the distribution index admits the catalog")
            val error = intercept[IllegalArgumentException] {
              ComponentRepositoryIndex.validateCatalogs(_sar_index(catalog), dir.resolve(ComponentRepositoryIndex.PUBLIC_PATH))
            }
            Then("checksum admission rejects rather than skipping incomplete releases")
            error.getMessage should include("kind=sar")
            error.getMessage should include("field=checksum.sha256")
          }
        }
      }

      "rejects unsafe versions and noncanonical archive paths" in {
        val cases = Vector(".", "..", "nested/version", "nested\\version").map { version =>
          version -> s"repository/sar/sample-app/$version/sample-app-$version.sar"
        } ++ Vector(
          "1.0.0" -> "../sample-app-1.0.0.sar",
          "1.0.0" -> "repository/sar/other/1.0.0/sample-app-1.0.0.sar",
          "1.0.0" -> "repository/sar/sample-app/1.0.0/wrong.sar"
        )
        cases.foreach { case (version, file) =>
          _with_temp_dir("sar-unsafe-path") { dir =>
            Given("a schema1 SAR catalog naming an unsafe version or wrong file path")
            val original = _sar_fixture(dir)
            val catalog = original.copy(recommended = Some(version), latestStable = Some(version),
              versions = original.versions.map(_.copy(version = version, file = Some(file))))
            _write(dir.resolve("repository/catalog/sar/sample-app.yaml"), catalog.toYaml)
            When("the distribution is admitted")
            val error = intercept[IllegalArgumentException] {
              ComponentRepositoryIndex.validateCatalogs(_sar_index(catalog), dir.resolve(ComponentRepositoryIndex.PUBLIC_PATH))
            }
            Then("the canonical SAR path contract rejects before reading another archive")
            error.getMessage should include("kind=sar")
            error.getMessage should include(if (version == "1.0.0") "field=file" else "field=version")
          }
        }
      }

      "admits prospective archive and sidecar bytes and rejects their disagreement" in {
        _with_temp_dir("sar-candidates") { dir =>
          Given("installed SAR records and matching prospective archive, sidecar and catalog siblings")
          val original = _sar_fixture(dir)
          val archive = dir.resolve(original.versions.head.file.get)
          val sidecar = archive.resolveSibling(archive.getFileName.toString + ".sha256")
          val catalogpath = dir.resolve("repository/catalog/sar/sample-app.yaml")
          val candidatearchive = _write(dir.resolve("candidate/archive.sar"), "candidate-\u65e5\u672c\u8a9e")
          val digest = _sha256(Files.readAllBytes(candidatearchive))
          val catalog = original.copy(versions = original.versions.map(_.copy(checksumSha256 = Some(digest))))
          val candidatecatalog = _write(dir.resolve("candidate/sample-app.yaml"), catalog.toYaml)
          val candidatesidecar = _write(dir.resolve("candidate/archive.sar.sha256"), digest + "\n")
          val overlay = Map(archive -> Some(candidatearchive), sidecar -> Some(candidatesidecar), catalogpath -> Some(candidatecatalog))
          When("prospective admission uses all prepared bytes")
          val admitted = ComponentRepositoryIndex.validateCatalogs(_sar_index(catalog), dir.resolve(ComponentRepositoryIndex.PUBLIC_PATH), overlay)
          Then("matching candidates are admitted without changing installed records")
          admitted shouldBe _sar_index(catalog)
          Files.readString(archive) shouldBe "sar-body"
          RepositoryArtifactCatalog.load(catalogpath) shouldBe original

          When("a prospective sidecar disagrees with its archive")
          _write(candidatesidecar, "0" * 64 + "\n")
          val sidecarerror = intercept[IllegalArgumentException] {
            ComponentRepositoryIndex.validateCatalogs(_sar_index(catalog), dir.resolve(ComponentRepositoryIndex.PUBLIC_PATH), overlay)
          }
          Then("candidate sidecar disagreement rejects")
          sidecarerror.getMessage should include("field=sha256-sidecar")

          When("prospective archive bytes disagree with the catalog checksum")
          _write(candidatearchive, "different-candidate")
          val archiveerror = intercept[IllegalArgumentException] {
            ComponentRepositoryIndex.validateCatalogs(_sar_index(catalog), dir.resolve(ComponentRepositoryIndex.PUBLIC_PATH), overlay)
          }
          Then("candidate archive disagreement rejects")
          archiveerror.getMessage should include("field=checksum.sha256")
        }
      }

      "rejects explicit candidate absence for archive sidecar and catalog" in {
        _with_temp_dir("sar-candidate-absence") { dir =>
          Given("valid installed SAR records that would otherwise provide fallback bytes")
          val catalog = _sar_fixture(dir)
          val archive = dir.resolve(catalog.versions.head.file.get)
          val paths = Vector(archive, archive.resolveSibling(archive.getFileName.toString + ".sha256"),
            dir.resolve("repository/catalog/sar/sample-app.yaml"))
          paths.foreach { path =>
            When("a prospective transaction explicitly removes a required SAR record")
            val error = intercept[IllegalArgumentException] {
              ComponentRepositoryIndex.validateCatalogs(_sar_index(catalog), dir.resolve(ComponentRepositoryIndex.PUBLIC_PATH), Map(path -> None))
            }
            Then("intended absence rejects without falling back to installed bytes")
            error.getMessage should include("kind=sar")
            error.getMessage should include("actual=intended-absence")
          }
        }
      }

      "restores installed publication bytes after a SAR sidecar forward-install failure" in {
        _with_temp_dir("sar-sidecar-rollback") { dir =>
          Given("an installed public SAR release and captured catalog, index, archive and sidecar bytes")
          val project = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          _write(project.resolve("project.yaml"), "project:\n  name: sample-app\npackaging:\n  kind: sar\n")
          val input = _write(dir.resolve("input.sar"), "first-sar-body")
          _publish_sar(project, warehouse, input)
          val archive = warehouse.resolve("repository/sar/sample-app/1.0.0/sample-app-1.0.0.sar")
          val sidecar = archive.resolveSibling(archive.getFileName.toString + ".sha256")
          val paths = Vector(archive, sidecar, project.resolve("src/main/catalog/sar/sample-app.yaml"),
            warehouse.resolve("repository/catalog/sar/sample-app.yaml"), warehouse.resolve(ComponentRepositoryIndex.PUBLIC_PATH),
            warehouse.resolve("repository/sar/sample-app/maven-metadata.xml"))
          val originals = paths.map(path => path -> Files.readAllBytes(path).toVector).toMap
          _write(input, "replacement-sar-body")
          When("republishing fails while installing the prepared SAR sidecar")
          val previousfault = RepositoryArtifactPublisher._publication_move_fault
          val error = try {
            RepositoryArtifactPublisher._publication_move_fault = Some { destination =>
              if (destination == sidecar) throw new IllegalStateException("SAR sidecar forward-install failure")
            }
            intercept[IllegalStateException](_publish_sar(project, warehouse, input))
          } finally RepositoryArtifactPublisher._publication_move_fault = previousfault
          Then("all prior visible records are restored and prepared and rollback artifacts are removed")
          error.getMessage shouldBe "SAR sidecar forward-install failure"
          paths.foreach(path => Files.readAllBytes(path).toVector shouldBe originals(path))
          val stream = Files.walk(dir)
          val remaining = try stream.iterator().asScala.filter(_ != dir).map(_.getFileName.toString).toVector finally stream.close()
          remaining.filter(name => name.contains("-prepare-") || name.contains("-rollback-")) shouldBe empty
          val indexpath = warehouse.resolve(ComponentRepositoryIndex.PUBLIC_PATH)
          When("the restored distribution is admitted again")
          val admitted = ComponentRepositoryIndex.validateCatalogs(ComponentRepositoryIndex.load(indexpath), indexpath)
          Then("the restored SAR records remain admissible")
          admitted.artifacts.head.kind shouldBe "sar"
        }
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

  private def _sar_fixture(root: Path): RepositoryArtifactCatalog = {
    val archive = _write(root.resolve("repository/sar/sample-app/1.0.0/sample-app-1.0.0.sar"), "sar-body")
    val digest = _sha256(Files.readAllBytes(archive))
    _write(archive.resolveSibling(archive.getFileName.toString + ".sha256"), digest + "\n")
    val catalog = _catalog("sar", "sample-app", Vector(_version("sar", "sample-app").copy(checksumSha256 = Some(digest))))
    _write(root.resolve("repository/catalog/sar/sample-app.yaml"), catalog.toYaml)
    catalog
  }

  private def _sar_index(catalog: RepositoryArtifactCatalog): ComponentRepositoryIndex =
    ComponentRepositoryIndex.update(None, catalog, Instant.parse("2026-10-04T00:00:00Z"))

  private def _publish_sar(project: Path, warehouse: Path, archive: Path): Unit =
    CozySarPublisher.publish(List(project.toString, "--warehouse", warehouse.toString, "--name", "sample-app",
      "--version", "1.0.0", "--sar", archive.toString, "--published-at", "2026-10-04T00:00:00Z"))

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
