package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.Instant
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.collection.JavaConverters._

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug.  7, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
/** Focused CID-04C repository-coordinate and integrity publication contract. */
final class Phase56NamespaceQualifiedCarPublicationSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "E1 Canonical CAR publication" must afterWord(
    "in spec:phase-56-cid04c-cozy-repository-publication, example:E1, rules:R1,R4, phase:56"
  ) {
    "materialize every shared coordinate projection at the public publisher boundary" in {
      _with_temp_dir { root =>
        val warehouse = root.resolve("warehouse")
        val project = _project(root, "org.alpha.textus", "Shared", "0.6.0")
        val coordinate = _coordinate("org.alpha.textus", "Shared")
        val archive = _canonical_archive(root.resolve("alpha.car"), coordinate, "alpha")

        Given("a minimal canonical v3/v2 CAR and a project without a compatibility contract")
        When("Cozy publishes the prebuilt CAR with deterministic publication time")
        _publish_car(project, warehouse, archive, coordinate)

        Then("archive, checksum, Maven metadata, catalogs, CML sidecars, and v2 index use exact shared projections")
        val car = warehouse.resolve("repository/car").resolve(coordinate.carRepositoryRelativePath)
        val checksum = car.resolveSibling(car.getFileName.toString + ".sha256")
        val sourcecatalog = project.resolve("src/main/catalog").resolve(coordinate.carCatalogRelativePath)
        val publiccatalog = warehouse.resolve("repository/catalog").resolve(coordinate.carCatalogRelativePath)
        Files.readAllBytes(car).toVector shouldBe Files.readAllBytes(archive).toVector
        Files.readString(checksum, StandardCharsets.UTF_8) shouldBe RepositoryArtifactPublisher.sha256(car) + "\n"
        Files.readString(sourcecatalog, StandardCharsets.UTF_8) shouldBe Files.readString(publiccatalog, StandardCharsets.UTF_8)
        Files.readString(warehouse.resolve("repository/car").resolve(coordinate.groupPath).resolve(coordinate.mavenArtifactId).resolve("maven-metadata.xml"), StandardCharsets.UTF_8) should include(
          "<groupId>org.alpha.textus</groupId>"
        )
        Files.readString(warehouse.resolve("repository/car").resolve(coordinate.groupPath).resolve(coordinate.mavenArtifactId).resolve("maven-metadata.xml"), StandardCharsets.UTF_8) should include(
          "<artifactId>textus-shared</artifactId>"
        )
        Files.isRegularFile(warehouse.resolve("repository/catalog/car").resolve(coordinate.groupPath).resolve("textus-shared.cml")) shouldBe true
        Files.isRegularFile(warehouse.resolve("repository/catalog/car").resolve(coordinate.groupPath).resolve("textus-shared.model-metadata.json")) shouldBe true
        Files.isRegularFile(warehouse.resolve("repository/catalog/car").resolve(coordinate.groupPath).resolve("textus-shared.model-metadata.yaml")) shouldBe true
        val indexpath = warehouse.resolve(ComponentRepositoryIndex.PUBLIC_PATH)
        ComponentRepositoryIndex.validateCatalogs(ComponentRepositoryIndex.load(indexpath), indexpath).
          artifacts.map(_.identity) should contain(("car", "org.alpha.textus", "Shared"))
      }
    }
  }

  "E2 Namespace isolation and deterministic republish" must afterWord(
    "in spec:phase-56-cid04c-cozy-repository-publication, example:E2, rules:R2,R6,R8, phase:56"
  ) {
    "preserve equal CAR filenames, unrelated SAR output, and byte-identical selected publication evidence" in {
      _with_temp_dir { root =>
        val warehouse = root.resolve("warehouse")
        val alpha = _coordinate("org.alpha.textus", "Shared")
        val beta = _coordinate("org.beta.textus", "Shared")
        val alphaproject = _project(root.resolve("alpha"), alpha.namespace, alpha.id, alpha.version)
        val betaproject = _project(root.resolve("beta"), beta.namespace, beta.id, beta.version)
        val alphaarchive = _canonical_archive(root.resolve("alpha.car"), alpha, "alpha")
        val betaarchive = _canonical_archive(root.resolve("beta.car"), beta, "beta")
        val sarproject = root.resolve("sar-project")
        _write(sarproject.resolve("project.yaml"), "project:\n  name: sample-app\n")
        val sar = _write(root.resolve("sample-app.sar"), "schema-v1-sar")

        Given("two namespace-qualified CARs with one filename and a schema-v1 SAR")
        When("Cozy publishes SAR then both CARs into one warehouse and republishes alpha")
        CozySarPublisher.publish(List(
          sarproject.toString, "--warehouse", warehouse.toString,
          "--sar", sar.toString, "--name", "sample-app", "--version", "1.0.0",
          "--published-at", _published_at
        ))
        _publish_car(alphaproject, warehouse, alphaarchive, alpha)
        _publish_car(betaproject, warehouse, betaarchive, beta)
        val before = _publication_bytes(warehouse, alpha)
        _publish_car(alphaproject, warehouse, alphaarchive, alpha)

        Then("the two archives remain distinct, the selected evidence is deterministic, and valid SAR is retained")
        alpha.carFilename shouldBe beta.carFilename
        Files.readAllBytes(warehouse.resolve("repository/car").resolve(alpha.carRepositoryRelativePath)).toVector should not be
          Files.readAllBytes(warehouse.resolve("repository/car").resolve(beta.carRepositoryRelativePath)).toVector
        _publication_bytes(warehouse, alpha) shouldBe before
        val indexpath = warehouse.resolve(ComponentRepositoryIndex.PUBLIC_PATH)
        val index = ComponentRepositoryIndex.validateCatalogs(ComponentRepositoryIndex.load(indexpath), indexpath)
        index.artifacts.map(_.identity) should contain allOf (
          ("car", "org.alpha.textus", "Shared"),
          ("car", "org.beta.textus", "Shared"),
          ("sar", "", "sample-app")
        )
        index.artifacts.count(_.identity == ("car", "org.alpha.textus", "Shared")) shouldBe 1
        RepositoryArtifactCatalog.load(
          warehouse.resolve("repository/catalog").resolve(alpha.carCatalogRelativePath)
        ).versions.count(_.version == alpha.version) shouldBe 1
      }
    }
  }

  "E1A Version-scoped Component knowledge sidecar" must afterWord(
    "in spec:phase-59.6-catalog-carrier, example:E1A, rules:R1,R9, phase:59.6"
  ) {
    "copy only the declared raw consumer contract beside its exact release catalog" in {
      Given("a CAR whose archive declaration and project source share one digest-bound consumer contract")
      _with_temp_dir { root =>
        val warehouse = root.resolve("warehouse")
        val coordinate = _coordinate("org.alpha.textus", "Shared")
        val project = _project(root.resolve("project"), coordinate.namespace, coordinate.id, coordinate.version)
        val contract =
          s"""{"schema":"cncf.component-knowledge-consumer.v1","componentId":"${coordinate.qualifiedId}","logicalRelease":"${coordinate.version}","resources":[]}"""
        val source = _write(project.resolve("src/main/car/component-knowledge.json"), contract)
        val digest = RepositoryArtifactPublisher.sha256(source)
        val descriptor =
          s"""{"schemaVersion":3,"component":{"namespace":"${coordinate.namespace}","id":"${coordinate.id}","version":"${coordinate.version}"},"componentKnowledge":{"carrierSchema":"cncf.component-knowledge-carrier.v1","consumerContractSchema":"cncf.component-knowledge-consumer.v1","logicalPath":"component-knowledge.json","sha256":"$digest"}}"""
        val archive = _archive(root.resolve("carrier.car"), Vector(
          "component-descriptor.json" -> descriptor,
          "abi-manifest.json" -> _abi(coordinate),
          "component/main.jar" -> "carrier",
          "component-knowledge.json" -> contract
        ))

        When("Cozy publishes that exact release")
        _publish_car(project, warehouse, archive, coordinate)

        Then("the version-scoped catalog sidecar is byte-identical to the declared source contract")
        val sidecar = warehouse.resolve("repository/catalog/car").resolve(coordinate.groupPath).
          resolve(coordinate.mavenArtifactId).resolve(coordinate.version).resolve("component-knowledge.json")
        Files.readAllBytes(sidecar).toVector shouldBe Files.readAllBytes(source).toVector
        RepositoryArtifactPublisher.sha256(sidecar) shouldBe digest
      }
    }
  }

  "E3 Canonical prebuilt CAR admission" must afterWord(
    "in spec:phase-56-cid04c-cozy-repository-publication, example:E3, rules:R1,R7, phase:56"
  ) {
    "reject malformed, legacy, and mismatched inputs before warehouse mutation without requiring a compatibility contract" in {
      _with_temp_dir { root =>
        val coordinate = _coordinate("org.alpha.textus", "Shared")
        val failures = Vector(
          "text" -> (() => _write(root.resolve("text.car"), "not-a-zip")),
          "missing-descriptor" -> (() => _archive(root.resolve("missing-descriptor.car"), Vector("abi-manifest.json" -> _abi(coordinate)))),
          "legacy-descriptor" -> (() => _archive(root.resolve("legacy-descriptor.car"), Vector("component-descriptor.json" -> _descriptor(coordinate, 1), "abi-manifest.json" -> _abi(coordinate)))),
          "descriptor-root-name" -> (() => _archive(root.resolve("descriptor-root-name.car"), Vector("component-descriptor.json" -> _descriptor(coordinate, 3).replace("{\"schemaVersion\":3", "{\"schemaVersion\":3,\"name\":\"Shared\""), "abi-manifest.json" -> _abi(coordinate)))),
          "descriptor-component-name" -> (() => _archive(root.resolve("descriptor-component-name.car"), Vector("component-descriptor.json" -> _descriptor(coordinate, 3).replace("\"version\":\"0.6.0\"}", "\"version\":\"0.6.0\",\"name\":\"Shared\"}"), "abi-manifest.json" -> _abi(coordinate)))),
          "missing-abi" -> (() => _archive(root.resolve("missing-abi.car"), Vector("component-descriptor.json" -> _descriptor(coordinate, 3)))),
          "legacy-abi" -> (() => _archive(root.resolve("legacy-abi.car"), Vector("component-descriptor.json" -> _descriptor(coordinate, 3), "abi-manifest.json" -> _abi(coordinate, "cozy.car.abi-manifest.v1")))),
          "abi-legacy-car" -> (() => _archive(root.resolve("abi-legacy-car.car"), Vector("component-descriptor.json" -> _descriptor(coordinate, 3), "abi-manifest.json" -> _abi(coordinate).replace("{\"format\"", "{\"car\":{},\"format\"")))),
          "abi-malformed" -> (() => _archive(root.resolve("abi-malformed.car"), Vector("component-descriptor.json" -> _descriptor(coordinate, 3), "abi-manifest.json" -> "{\"format\":\"cozy.car.abi-manifest.v2\",\"component\":{}}"))),
          "coordinate-mismatch" -> (() => _archive(root.resolve("coordinate-mismatch.car"), Vector("component-descriptor.json" -> _descriptor(_coordinate("org.alpha.textus", "Other"), 3), "abi-manifest.json" -> _abi(coordinate))))
        )

        Given("prebuilt archives that violate canonical coordinate admission in six distinct ways")
        failures.zipWithIndex.foreach { case ((label, build), index) =>
          val warehouse = root.resolve(s"warehouse-$index-$label")
          val project = _project(root.resolve(s"project-$index-$label"), coordinate.namespace, coordinate.id, coordinate.version)
          When("the actual Cozy CAR publisher evaluates the supplied archive")
          intercept[Throwable] {
            _publish_car(project, warehouse, build(), coordinate)
          }
          Then("the failure occurs before this coordinate has warehouse output")
          Files.exists(warehouse.resolve("repository/car").resolve(coordinate.carRepositoryRelativePath)) shouldBe false
        }
      }
    }
  }

  "E4 Existing persistence and integrity disagreement" must afterWord(
    "in spec:phase-56-cid04c-cozy-repository-publication, example:E4, rules:R3,R5,R7, phase:56"
  ) {
    "reject transport name and version projection mismatches before mutation" in {
      _with_temp_dir { root =>
        val warehouse = root.resolve("warehouse")
        val coordinate = _coordinate("org.alpha.textus", "Shared")
        val project = _project(root, coordinate.namespace, coordinate.id, coordinate.version)
        val archive = _canonical_archive(root.resolve("valid.car"), coordinate, "valid")

        Given("a canonical CAR project and archive before publication")
        When("the transport name and version disagree with the canonical coordinate")
        intercept[Throwable] {
          _publish_car_with_transport(project, warehouse, archive, coordinate, "wrong-name", coordinate.version)
        }.getMessage should include("component.release-coordinate.projection-mismatch")
        intercept[Throwable] {
          _publish_car_with_transport(project, warehouse, archive, coordinate, coordinate.mavenArtifactId, "0.6.1")
        }.getMessage should include("component.release-coordinate.projection-mismatch")

        Then("no coordinate archive is written")
        Files.exists(warehouse.resolve("repository/car").resolve(coordinate.carRepositoryRelativePath)) shouldBe false
      }
    }

    "reject stored source-catalog coordinate disagreement without changing publication bytes" in {
      _with_temp_dir { root =>
        val warehouse = root.resolve("warehouse")
        val coordinate = _coordinate("org.alpha.textus", "Shared")
        val project = _project(root, coordinate.namespace, coordinate.id, coordinate.version)
        val archive = _canonical_archive(root.resolve("valid.car"), coordinate, "valid")

        Given("a published canonical CAR and its source catalog")
        _publish_car(project, warehouse, archive, coordinate)
        val catalogpath = project.resolve("src/main/catalog").resolve(coordinate.carCatalogRelativePath)
        val before = _publication_bytes(warehouse, coordinate)
        val originalcatalog = Files.readString(catalogpath, StandardCharsets.UTF_8)

        When("the stored source catalog namespace disagrees with the project coordinate")
        Files.writeString(catalogpath, originalcatalog.replace("namespace: org.alpha.textus", "namespace: org.beta.textus"), StandardCharsets.UTF_8)
        intercept[Throwable] {
          _publish_car(project, warehouse, archive, coordinate)
        }.getMessage should include("component.release-coordinate.mismatch")

        Then("the existing publication evidence remains unchanged")
        _publication_bytes(warehouse, coordinate) shouldBe before
      }
    }

    "reject stored index coordinate disagreement without changing publication bytes" in {
      _with_temp_dir { root =>
        val warehouse = root.resolve("warehouse")
        val coordinate = _coordinate("org.alpha.textus", "Shared")
        val project = _project(root, coordinate.namespace, coordinate.id, coordinate.version)
        val archive = _canonical_archive(root.resolve("valid.car"), coordinate, "valid")

        Given("a published canonical CAR and its public repository index")
        _publish_car(project, warehouse, archive, coordinate)
        val indexpath = warehouse.resolve(ComponentRepositoryIndex.PUBLIC_PATH)
        val originalindex = Files.readString(indexpath, StandardCharsets.UTF_8)

        When("the stored index namespace disagrees with the catalog coordinate")
        Files.writeString(indexpath, originalindex.replace("\"namespace\" : \"org.alpha.textus\"", "\"namespace\" : \"org.beta.textus\""), StandardCharsets.UTF_8)
        val tampered = _publication_bytes(warehouse, coordinate)
        intercept[Throwable] {
          _publish_car(project, warehouse, archive, coordinate)
        }.getMessage should include("component.release-coordinate.projection-mismatch")

        Then("the existing publication evidence remains unchanged")
        _publication_bytes(warehouse, coordinate) shouldBe tampered
      }
    }

    "reject malformed stored catalog shapes without changing publication bytes" in {
      _with_temp_dir { root =>
        val warehouse = root.resolve("warehouse")
        val coordinate = _coordinate("org.alpha.textus", "Shared")
        val project = _project(root, coordinate.namespace, coordinate.id, coordinate.version)
        val archive = _canonical_archive(root.resolve("valid.car"), coordinate, "valid")

        Given("a published canonical CAR and its canonical source catalog")
        _publish_car(project, warehouse, archive, coordinate)
        val catalogpath = project.resolve("src/main/catalog").resolve(coordinate.carCatalogRelativePath)
        val before = _publication_bytes(warehouse, coordinate)
        val originalcatalog = Files.readString(catalogpath, StandardCharsets.UTF_8)

        When("the catalog parser evaluates unknown, missing, legacy, projected, and duplicate shapes")
        intercept[Throwable] {
          RepositoryArtifactCatalog.parse(Files.readString(catalogpath, StandardCharsets.UTF_8) + "unknown: value\n")
        }.getMessage should include("projection-mismatch")
        intercept[Throwable] {
          RepositoryArtifactCatalog.parse(originalcatalog.replace("schemaVersion: 2", "schemaVersion: 1"))
        }.getMessage should include("repository.artifact.catalog.schema.unsupported")
        intercept[Throwable] {
          RepositoryArtifactCatalog.parse(originalcatalog.replace("namespace: org.alpha.textus\n", ""))
        }.getMessage should include("component.release-coordinate.mismatch")
        intercept[Throwable] {
          RepositoryArtifactCatalog.parse(originalcatalog.replace("artifactId: textus-shared", "artifactId: wrong"))
        }.getMessage should include("projection-mismatch")
        val catalog = RepositoryArtifactCatalog.load(catalogpath)
        intercept[Throwable] {
          catalog.copy(versions = catalog.versions ++ catalog.versions).validate
        }.getMessage should include("Duplicate repository artifact catalog versions")
        Then("the persisted publication evidence remains unchanged")
        _publication_bytes(warehouse, coordinate) shouldBe before
      }
    }

    "reject index traversal and duplicate entries without changing publication bytes" in {
      _with_temp_dir { root =>
        val warehouse = root.resolve("warehouse")
        val coordinate = _coordinate("org.alpha.textus", "Shared")
        val project = _project(root, coordinate.namespace, coordinate.id, coordinate.version)
        val archive = _canonical_archive(root.resolve("valid.car"), coordinate, "valid")

        Given("a published canonical CAR and its public repository index")
        _publish_car(project, warehouse, archive, coordinate)
        val indexpath = warehouse.resolve(ComponentRepositoryIndex.PUBLIC_PATH)
        val before = _publication_bytes(warehouse, coordinate)

        When("the index parser evaluates traversal and duplicate-entry shapes")
        intercept[Throwable] {
          ComponentRepositoryIndex.parse(Files.readString(indexpath, StandardCharsets.UTF_8).replace(
            coordinate.carCatalogRelativePath, "car/../escape.yaml"
          ))
        }.getMessage should include("component.release-coordinate.projection-mismatch")
        val index = ComponentRepositoryIndex.load(indexpath)
        intercept[Throwable] {
          ComponentRepositoryIndex(index.schemaVersion, index.generatedAt, index.artifacts ++ index.artifacts).render
        }.getMessage should include("Duplicate component repository artifacts")

        Then("the persisted publication evidence remains unchanged")
        _publication_bytes(warehouse, coordinate) shouldBe before
      }
    }

    "reject archive tampering without changing catalog and index evidence" in {
      _with_temp_dir { root =>
        val warehouse = root.resolve("warehouse")
        val coordinate = _coordinate("org.alpha.textus", "Shared")
        val project = _project(root, coordinate.namespace, coordinate.id, coordinate.version)
        val archive = _canonical_archive(root.resolve("valid.car"), coordinate, "valid")

        Given("a published canonical CAR and its integrity evidence")
        _publish_car(project, warehouse, archive, coordinate)
        val indexpath = warehouse.resolve(ComponentRepositoryIndex.PUBLIC_PATH)
        val before = _publication_bytes(warehouse, coordinate)
        val car = warehouse.resolve("repository/car").resolve(coordinate.carRepositoryRelativePath)
        Files.writeString(car, "tampered", StandardCharsets.UTF_8)

        When("the public index validates the tampered archive")
        intercept[Throwable] {
          ComponentRepositoryIndex.validateCatalogs(ComponentRepositoryIndex.load(indexpath), indexpath)
        }.getMessage should include("component.repository.integrity.mismatch")
        _publication_bytes(warehouse, coordinate).filterNot(_._1.endsWith(".car")) shouldBe before.filterNot(_._1.endsWith(".car"))
      }
    }

    "reject checksum sidecar tampering without changing archive and catalog evidence" in {
      _with_temp_dir { root =>
        val warehouse = root.resolve("warehouse")
        val coordinate = _coordinate("org.alpha.textus", "Shared")
        val project = _project(root, coordinate.namespace, coordinate.id, coordinate.version)
        val archive = _canonical_archive(root.resolve("valid.car"), coordinate, "valid")

        Given("a published canonical CAR and its checksum sidecar")
        _publish_car(project, warehouse, archive, coordinate)
        val indexpath = warehouse.resolve(ComponentRepositoryIndex.PUBLIC_PATH)
        val before = _publication_bytes(warehouse, coordinate)
        val car = warehouse.resolve("repository/car").resolve(coordinate.carRepositoryRelativePath)
        Files.writeString(car.resolveSibling(car.getFileName.toString + ".sha256"), "tampered\n", StandardCharsets.UTF_8)

        When("the public index validates the tampered checksum sidecar")
        intercept[Throwable] {
          ComponentRepositoryIndex.validateCatalogs(ComponentRepositoryIndex.load(indexpath), indexpath)
        }.getMessage should include("component.repository.integrity.mismatch")

        Then("archive, catalog, and index bytes remain unchanged")
        _publication_bytes(warehouse, coordinate).filterNot(_._1.endsWith(".sha256")) shouldBe before.filterNot(_._1.endsWith(".sha256"))
      }
    }
  }

  "E5 Prepared publication transaction" must afterWord(
    "in spec:phase-56-cid04c-cozy-repository-publication, example:E5, rules:R2,R5, phase:56"
  ) {
    "restore every original destination, remove a new-only destination, and never re-enter the forward seam when index commit fails" in {
      _with_temp_dir { root =>
        val warehouse = root.resolve("warehouse")
        val coordinate = _coordinate("org.alpha.textus", "Shared")
        val project = _project(root.resolve("project"), coordinate.namespace, coordinate.id, coordinate.version)
        val original = _canonical_archive(root.resolve("original.car"), coordinate, "original")
        val replacement = _canonical_archive(root.resolve("replacement.car"), coordinate, "replacement")
        _publish_car(project, warehouse, original, coordinate)
        val newonly = warehouse.resolve("repository/catalog/car").resolve(coordinate.groupPath).resolve(coordinate.mavenArtifactId + ".model-metadata.yaml")
        Files.delete(newonly)
        val before = _existing_publication_bytes(warehouse, coordinate)
        val indexpath = warehouse.resolve(ComponentRepositoryIndex.PUBLIC_PATH).toAbsolutePath.normalize()
        val warehouseroot = warehouse.toAbsolutePath.normalize()
        var seamcalls = Vector.empty[Path]

        Given("a new-only CML sidecar and an index replacement fault after Maven metadata has committed")
        RepositoryArtifactPublisher._publication_move_fault = Some { destination =>
          if (destination.toAbsolutePath.normalize().startsWith(warehouseroot)) {
            seamcalls :+= destination
            if (destination == indexpath) throw new IllegalStateException("injected index replacement failure")
          }
        }
        try {
          When("the actual publisher commits its prepared transaction")
          intercept[IllegalStateException] { _publish_car(project, warehouse, replacement, coordinate) }
          Then("every original byte is restored, the new-only sidecar is absent, and restore does not invoke the forward seam")
          _existing_publication_bytes(warehouse, coordinate) shouldBe before
          Files.exists(newonly) shouldBe false
          seamcalls.map(_.getFileName.toString) should contain("maven-metadata.xml")
          seamcalls should have size 8
          _transaction_debris(root) shouldBe empty
        } finally RepositoryArtifactPublisher._publication_move_fault = None
      }
    }

    "remove preparation and snapshot artifacts without mutating publication output when candidate construction fails" in {
      _with_temp_dir { root =>
        val warehouse = root.resolve("warehouse")
        val coordinate = _coordinate("org.alpha.textus", "Shared")
        val project = _project(root.resolve("project"), coordinate.namespace, coordinate.id, coordinate.version)
        val archive = _canonical_archive(root.resolve("candidate.car"), coordinate, "candidate")
        val warehouseroot = warehouse.toAbsolutePath.normalize()

        Given("a candidate copy fault before the transaction begins")
        RepositoryArtifactPublisher._publication_prepare_fault = Some { destination =>
          if (destination.toAbsolutePath.normalize().startsWith(warehouseroot))
            throw new IllegalStateException("injected candidate preparation failure")
        }
        try {
          When("the publisher prepares its first archive candidate")
          intercept[IllegalStateException] { _publish_car(project, warehouse, archive, coordinate) }
          Then("no publication path or prepare/rollback artifact remains")
          Files.exists(warehouse.resolve("repository/car").resolve(coordinate.carRepositoryRelativePath)) shouldBe false
          Files.exists(warehouse.resolve(ComponentRepositoryIndex.PUBLIC_PATH)) shouldBe false
          _transaction_debris(root) shouldBe empty
        } finally RepositoryArtifactPublisher._publication_prepare_fault = None
      }
    }

    "clean every earlier rollback snapshot when a later snapshot backup fails before forward mutation" in {
      _with_temp_dir { root =>
        val warehouse = root.resolve("warehouse")
        val coordinate = _coordinate("org.alpha.textus", "Shared")
        val project = _project(root.resolve("project"), coordinate.namespace, coordinate.id, coordinate.version)
        val original = _canonical_archive(root.resolve("original.car"), coordinate, "original")
        val replacement = _canonical_archive(root.resolve("replacement.car"), coordinate, "replacement")
        _publish_car(project, warehouse, original, coordinate)
        val before = _publication_bytes(warehouse, coordinate)
        val warehouseroot = warehouse.toAbsolutePath.normalize()

        Given("a snapshot failure after earlier archive, checksum, and catalog backups")
        RepositoryArtifactPublisher._publication_snapshot_fault = Some { destination =>
          if (
            destination.toAbsolutePath.normalize().startsWith(warehouseroot) &&
            destination.getFileName.toString == "maven-metadata.xml"
          )
            throw new IllegalStateException("injected snapshot backup failure")
        }
        try {
          When("the publisher snapshots prepared destinations before their forward installation")
          intercept[IllegalStateException] { _publish_car(project, warehouse, replacement, coordinate) }
          Then("publication bytes remain unchanged and all candidate and rollback debris is removed")
          _publication_bytes(warehouse, coordinate) shouldBe before
          _transaction_debris(root) shouldBe empty
        } finally RepositoryArtifactPublisher._publication_snapshot_fault = None
      }
    }

    "leave final bytes and transaction directories unchanged when candidate validation fails" in {
      _with_temp_dir { root =>
        val warehouse = root.resolve("warehouse")
        val coordinate = _coordinate("org.alpha.textus", "Shared")
        val project = _project(root.resolve("project"), coordinate.namespace, coordinate.id, coordinate.version)
        val original = _canonical_archive(root.resolve("original.car"), coordinate, "original")
        _publish_car(project, warehouse, original, coordinate)
        val before = _publication_bytes(warehouse, coordinate)
        val catalogpath = project.resolve("src/main/catalog").resolve(coordinate.carCatalogRelativePath)
        val catalog = RepositoryArtifactCatalog.load(catalogpath)
        val stale = CozyComponentReleaseCoordinateCodec.admit(coordinate.namespace, coordinate.id, "0.5.0", "phase-56-cid04c-stale")
        val staleversion = RepositoryArtifactCatalogVersion(
          version = stale.version,
          channel = Some("stable"),
          status = Some("active"),
          component = Some(stale.qualifiedId),
          publishedAt = Some(_published_at),
          file = Some(s"repository/car/${stale.carRepositoryRelativePath}"),
          runtime = None,
          checksumSha256 = Some("0" * 64),
          integrityKey = Some(stale.integrityKey("0" * 64))
        )
        Files.writeString(catalogpath, catalog.copy(versions = staleversion +: catalog.versions).toYaml, StandardCharsets.UTF_8)

        Given("a valid publication whose source catalog contains a missing archived release")
        When("a replacement candidate is validated before commit")
        intercept[Throwable] {
          _publish_car(project, warehouse, original, coordinate)
        }.getMessage should include("component.repository.integrity.mismatch")

        Then("final publication bytes and prepare/rollback siblings remain absent")
        _publication_bytes(warehouse, coordinate) shouldBe before
        val stream = Files.walk(warehouse)
        try {
          val debris = stream.iterator().asScala.map(_.getFileName.toString).filter(
            name => name.contains("-prepare-") || name.contains("-rollback-")
          ).toVector
          debris shouldBe empty
        } finally stream.close()
      }
    }
  }

  private val _published_at = "2026-08-07T00:00:00Z"

  private def _coordinate(namespace: String, id: String) =
    CozyComponentReleaseCoordinateCodec.admit(namespace, id, "0.6.0", "phase-56-cid04c-spec")

  private def _project(root: Path, namespace: String, id: String, version: String): Path = {
    val coordinate = CozyComponentReleaseCoordinateCodec.admit(namespace, id, version, "phase-56-cid04c-spec")
    _write(
      root.resolve("project.yaml"),
      s"""project:
         |  namespace: $namespace
         |  id: $id
         |  name: ${coordinate.mavenArtifactId}
         |  component:
         |    version: $version
         |""".stripMargin
    )
    _write(root.resolve("src/main/cozy").resolve(s"${coordinate.mavenArtifactId}.cml"), "# ENTITY\n\n## Shared\n")
    root
  }

  private def _publish_car(
    project: Path,
    warehouse: Path,
    archive: Path,
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate
  ): Unit =
    _publish_car_with_transport(
      project,
      warehouse,
      archive,
      coordinate,
      coordinate.mavenArtifactId,
      coordinate.version
    )

  private def _publish_car_with_transport(
    project: Path,
    warehouse: Path,
    archive: Path,
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    name: String,
    version: String
  ): Unit =
    CozyCarPublisher.publish(List(
      project.toString,
      "--warehouse", warehouse.toString,
      "--car", archive.toString,
      "--name", name,
      "--version", version,
      "--published-at", _published_at
    ))

  private def _canonical_archive(
    archive: Path,
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    payload: String
  ): Path =
    _archive(archive, Vector(
      "component-descriptor.json" -> _descriptor(coordinate, 3),
      "abi-manifest.json" -> _abi(coordinate),
      "component/main.jar" -> payload
    ))

  private def _archive(archive: Path, entries: Vector[(String, String)]): Path = {
    Option(archive.getParent).foreach(Files.createDirectories(_))
    val output = new ZipOutputStream(Files.newOutputStream(archive))
    try entries.foreach { case (name, value) =>
      output.putNextEntry(new ZipEntry(name))
      output.write(value.getBytes(StandardCharsets.UTF_8))
      output.closeEntry()
    } finally output.close()
    archive
  }

  private def _descriptor(
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    schema: Int
  ): String =
    s"""{"schemaVersion":$schema,"component":{"namespace":"${coordinate.namespace}","id":"${coordinate.id}","version":"${coordinate.version}"}}"""

  private def _abi(
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    format: String = "cozy.car.abi-manifest.v2"
  ): String =
    s"""{"format":"$format","component":{"namespace":"${coordinate.namespace}","id":"${coordinate.id}","version":"${coordinate.version}"},"abi":{"version":1,"exports":{"components":[{"namespace":"${coordinate.namespace}","id":"${coordinate.id}"}]},"dependencies":[]}}"""

  private def _publication_bytes(
    warehouse: Path,
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate
  ): Map[String, Vector[Byte]] =
    _publication_paths(warehouse, coordinate).map(path => path.toString -> Files.readAllBytes(path).toVector).toMap

  private def _existing_publication_bytes(
    warehouse: Path,
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate
  ): Map[String, Vector[Byte]] =
    _publication_paths(warehouse, coordinate).collect {
      case path if Files.exists(path) => path.toString -> Files.readAllBytes(path).toVector
    }.toMap

  private def _publication_paths(
    warehouse: Path,
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate
  ): Vector[Path] =
    Vector(
      warehouse.resolve("repository/car").resolve(coordinate.carRepositoryRelativePath),
      warehouse.resolve("repository/car").resolve(coordinate.carRepositoryRelativePath + ".sha256"),
      warehouse.resolve("repository/car").resolve(coordinate.groupPath).resolve(coordinate.mavenArtifactId).resolve("maven-metadata.xml"),
      warehouse.resolve("repository/catalog").resolve(coordinate.carCatalogRelativePath),
      warehouse.resolve("repository/catalog/car").resolve(coordinate.groupPath).resolve(coordinate.mavenArtifactId + ".cml"),
      warehouse.resolve("repository/catalog/car").resolve(coordinate.groupPath).resolve(coordinate.mavenArtifactId + ".model-metadata.json"),
      warehouse.resolve("repository/catalog/car").resolve(coordinate.groupPath).resolve(coordinate.mavenArtifactId + ".model-metadata.yaml"),
      warehouse.resolve(ComponentRepositoryIndex.PUBLIC_PATH)
    )

  private def _transaction_debris(root: Path): Vector[String] = {
    val paths = Files.walk(root)
    try paths.iterator().asScala.map(_.getFileName.toString).filter { name =>
      name.contains("-prepare-") || name.contains("-rollback-") || name.contains("-transaction-")
    }.toVector
    finally paths.close()
  }

  private def _write(path: Path, text: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, text, StandardCharsets.UTF_8)
    path
  }

  private def _with_temp_dir[A](body: Path => A): A = {
    val workroot = Path.of("target/cozy-test/work/phase56-namespace-qualified-car-publication-spec").toAbsolutePath.normalize()
    Files.createDirectories(workroot)
    val root = Files.createTempDirectory(workroot, "publication-")
    try body(root)
    finally _delete(root)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val paths = Files.walk(path)
      try paths.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
      finally paths.close()
    }
}
