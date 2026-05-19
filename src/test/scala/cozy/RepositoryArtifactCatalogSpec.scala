package cozy

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import org.scalatest.funsuite.AnyFunSuite

class RepositoryArtifactCatalogSpec extends AnyFunSuite {
  test("parse and render valid CAR catalog deterministically") {
    val catalog = RepositoryArtifactCatalog.parse(_car_catalog_text)
    assert(catalog.kind == "car")
    assert(catalog.artifactId == "textus-semantic-integration-engine")
    assert(catalog.aliases == Vector("textus-sie"))
    assert(catalog.versions.map(_.version) == Vector("0.1.0", "0.1.1-SNAPSHOT"))
    assert(catalog.versions.head.runtime.flatMap(_.minimum) == Some("0.4.8"))
    assert(catalog.versions.head.runtime.map(_.tested).getOrElse(Vector.empty) == Vector("0.4.8"))
    assert(catalog.toYaml == RepositoryArtifactCatalog.parse(catalog.toYaml).toYaml)
  }

  test("parse and render valid SAR catalog deterministically") {
    val catalog = RepositoryArtifactCatalog.parse(
      """schemaVersion: 1
        |kind: sar
        |artifactId: sample-application
        |recommended: 1.0.0
        |latestStable: 1.0.0
        |status: active
        |aliases: []
        |versions:
        |  - version: 1.0.0
        |    channel: stable
        |    status: active
        |    file: repository/sar/sample-application/1.0.0/sample-application-1.0.0.sar
        |""".stripMargin
    )
    assert(catalog.kind == "sar")
    assert(catalog.versions.head.file.exists(_.endsWith(".sar")))
    assert(catalog.toYaml == RepositoryArtifactCatalog.parse(catalog.toYaml).toYaml)
  }

  test("parse inline aliases") {
    val catalog = RepositoryArtifactCatalog.parse(
      """schemaVersion: 1
        |kind: car
        |artifactId: sample
        |aliases: [sample-alias, sample-old]
        |versions:
        |  - version: 0.1.0
        |    file: repository/car/sample/0.1.0/sample-0.1.0.car
        |""".stripMargin
    )
    assert(catalog.aliases == Vector("sample-alias", "sample-old"))
  }

  test("validate selector references") {
    val catalog = RepositoryArtifactCatalog.parse(_car_catalog_text)
    assert(catalog.recommended == Some("0.1.0"))
    assert(catalog.latestStable == Some("0.1.0"))
    assert(catalog.latestSnapshot == Some("0.1.1-SNAPSHOT"))
  }

  test("reject duplicate versions") {
    val ex = intercept[IllegalArgumentException] {
      RepositoryArtifactCatalog.parse(
        """schemaVersion: 1
          |kind: car
          |artifactId: sample
          |versions:
          |  - version: 0.1.0
          |  - version: 0.1.0
          |""".stripMargin
      )
    }
    assert(ex.getMessage.contains("Duplicate"))
  }

  test("reject invalid kind status and channel") {
    _assert_invalid("kind: jar", "kind")
    _assert_invalid("kind: car\nstatus: hidden", "status")
    val ex = intercept[IllegalArgumentException] {
      RepositoryArtifactCatalog.parse(
        """schemaVersion: 1
          |kind: car
          |artifactId: sample
          |versions:
          |  - version: 0.1.0
          |    channel: nightly
          |""".stripMargin
      )
    }
    assert(ex.getMessage.contains("channel"))
  }

  test("reject disabled recommended version") {
    val ex = intercept[IllegalArgumentException] {
      RepositoryArtifactCatalog.parse(
        """schemaVersion: 1
          |kind: car
          |artifactId: sample
          |recommended: 0.1.0
          |versions:
          |  - version: 0.1.0
          |    status: disabled
          |    file: repository/car/sample/0.1.0/sample-0.1.0.car
          |""".stripMargin
      )
    }
    assert(ex.getMessage.contains("disabled"))
  }

  test("reject selector pointing to missing version") {
    val ex = intercept[IllegalArgumentException] {
      RepositoryArtifactCatalog.parse(
        """schemaVersion: 1
          |kind: car
          |artifactId: sample
          |latestStable: 0.2.0
          |versions:
          |  - version: 0.1.0
          |    status: active
          |    file: repository/car/sample/0.1.0/sample-0.1.0.car
          |""".stripMargin
      )
    }
    assert(ex.getMessage.contains("missing version"))
  }

  test("reject latest selectors with wrong channel") {
    val stableex = intercept[IllegalArgumentException] {
      RepositoryArtifactCatalog.parse(
        """schemaVersion: 1
          |kind: car
          |artifactId: sample
          |latestStable: 0.2.0-SNAPSHOT
          |versions:
          |  - version: 0.2.0-SNAPSHOT
          |    channel: snapshot
          |    file: repository/car/sample/0.2.0-SNAPSHOT/sample-0.2.0-SNAPSHOT.car
          |""".stripMargin
      )
    }
    assert(stableex.getMessage.contains("stable"))

    val snapshotex = intercept[IllegalArgumentException] {
      RepositoryArtifactCatalog.parse(
        """schemaVersion: 1
          |kind: car
          |artifactId: sample
          |latestSnapshot: 0.1.0
          |versions:
          |  - version: 0.1.0
          |    channel: stable
          |    file: repository/car/sample/0.1.0/sample-0.1.0.car
          |""".stripMargin
      )
    }
    assert(snapshotex.getMessage.contains("snapshot"))
  }

  test("reject version without file") {
    val ex = intercept[IllegalArgumentException] {
      RepositoryArtifactCatalog.parse(
        """schemaVersion: 1
          |kind: car
          |artifactId: sample
          |versions:
          |  - version: 0.1.0
          |    status: active
          |""".stripMargin
      )
    }
    assert(ex.getMessage.contains("requires file"))
  }

  test("reject wrong archive extension") {
    val ex = intercept[IllegalArgumentException] {
      RepositoryArtifactCatalog.parse(
        """schemaVersion: 1
          |kind: car
          |artifactId: sample
          |versions:
          |  - version: 0.1.0
          |    file: repository/car/sample/0.1.0/sample-0.1.0.sar
          |""".stripMargin
      )
    }
    assert(ex.getMessage.contains(".car"))
  }

  test("validate source path against kind and artifact id") {
    _with_temp_dir("repository-artifact-catalog") { dir =>
      val path = dir.resolve("src/main/catalog/car/textus-semantic-integration-engine.yaml")
      _write(path, _car_catalog_text)
      val catalog = RepositoryArtifactCatalog.load(path)
      assert(catalog.artifactId == "textus-semantic-integration-engine")

      val wrongpath = dir.resolve("src/main/catalog/car/other.yaml")
      _write(wrongpath, _car_catalog_text)
      val ex = intercept[IllegalArgumentException] {
        RepositoryArtifactCatalog.load(wrongpath)
      }
      assert(ex.getMessage.contains("filename"))

      val wrongkindpath = dir.resolve("src/main/catalog/sar/textus-semantic-integration-engine.yaml")
      _write(wrongkindpath, _car_catalog_text)
      val ex2 = intercept[IllegalArgumentException] {
        RepositoryArtifactCatalog.load(wrongkindpath)
      }
      assert(ex2.getMessage.contains("path kind"))
    }
  }

  private def _assert_invalid(line: String, expected: String): Unit = {
    val ex = intercept[IllegalArgumentException] {
      RepositoryArtifactCatalog.parse(
        s"""schemaVersion: 1
           |$line
           |artifactId: sample
           |versions:
           |  - version: 0.1.0
           |""".stripMargin
      )
    }
    assert(ex.getMessage.contains(expected))
  }

  private def _with_temp_dir(prefix: String)(body: Path => Unit): Unit = {
    val dir = Files.createTempDirectory(prefix)
    try {
      body(dir)
    } finally {
      _delete_recursively(dir)
    }
  }

  private def _write(path: Path, text: String): Path = {
    Files.createDirectories(path.getParent)
    Files.write(path, text.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _delete_recursively(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.sorted(java.util.Comparator.reverseOrder()).forEach(p => Files.deleteIfExists(p))
      } finally {
        stream.close()
      }
    }

  private val _car_catalog_text: String =
    """schemaVersion: 1
      |kind: car
      |artifactId: textus-semantic-integration-engine
      |recommended: 0.1.0
      |latestStable: 0.1.0
      |latestSnapshot: 0.1.1-SNAPSHOT
      |status: active
      |aliases:
      |  - textus-sie
      |versions:
      |  - version: 0.1.0
      |    channel: stable
      |    status: active
      |    component: textus-semantic-integration-engine
      |    publishedAt: 2026-05-20T00:00:00Z
      |    file: repository/car/textus-semantic-integration-engine/0.1.0/textus-semantic-integration-engine-0.1.0.car
      |    runtime:
      |      cncf:
      |        minimum: 0.4.8
      |        maximum:
      |        excluded: []
      |        tested:
      |          - 0.4.8
      |    checksum:
      |      sha256: abc123
      |  - version: 0.1.1-SNAPSHOT
      |    channel: snapshot
      |    status: active
      |    component: textus-semantic-integration-engine
      |    publishedAt: 2026-05-20T01:00:00Z
      |    file: repository/car/textus-semantic-integration-engine/0.1.1-SNAPSHOT/textus-semantic-integration-engine-0.1.1-SNAPSHOT.car
      |""".stripMargin
}
