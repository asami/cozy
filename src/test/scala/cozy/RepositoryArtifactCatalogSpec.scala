package cozy

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import cozy.archive.RepositoryArtifactMavenMetadata
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   May. 20, 2026
 *  version Jul. 13, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
class RepositoryArtifactCatalogSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Repository artifact catalog parser" should {
  "parse and render catalog documents" which {
  "compatibility extractors return None for null values" in {
    Given("null compatibility extractor inputs")
    When("the extractor methods inspect the values")
    Then("both extractors return no value")
    RepositoryArtifactCatalog.unapply(null) shouldBe None
    RepositoryArtifactCatalogVersion.unapply(null) shouldBe None
  }

  "reject duplicate YAML root and version catalog fields before overwrite" in {
    Given("YAML catalogs with repeated root and version fields")
    val root = intercept[IllegalArgumentException] {
      When("the repeated root field is parsed")
      RepositoryArtifactCatalog.parse("schemaVersion: 1\nschemaVersion: 1\nkind: sar\nartifactId: sample\nversions: []\n")
    }
    Then("the root duplicate reports its stable path")
    root.getMessage shouldBe "repository.artifact.catalog.duplicate-field source=catalog path=schemaVersion"
    val version = intercept[IllegalArgumentException] {
      When("the repeated version field is parsed")
      RepositoryArtifactCatalog.parse("schemaVersion: 1\nkind: sar\nartifactId: sample\nversions:\n  - version: 1.0.0\n    version: 1.0.1\n")
    }
    Then("the version duplicate reports its stable path")
    version.getMessage shouldBe "repository.artifact.catalog.duplicate-field source=catalog path=versions[0].version"
  }

  "reject duplicate YAML map headers before replacing parser state" in {
    Given("YAML catalogs that repeat empty-value map headers")
    val aliases = intercept[IllegalArgumentException] {
      When("the aliases map header is repeated")
      RepositoryArtifactCatalog.parse(
        "schemaVersion: 1\nkind: sar\nartifactId: sample\naliases:\n  - old\naliases:\n  - new\nversions: []\n"
      )
    }
    Then("the aliases header path is reported")
    aliases.getMessage shouldBe "repository.artifact.catalog.duplicate-field source=catalog path=aliases"

    val versions = intercept[IllegalArgumentException] {
      When("the versions map header is repeated")
      RepositoryArtifactCatalog.parse(
        "schemaVersion: 1\nkind: sar\nartifactId: sample\nversions:\n  - version: 1.0.0\n    file: sample.sar\nversions:\n  - version: 1.0.1\n    file: sample.sar\n"
      )
    }
    Then("the versions header path is reported")
    versions.getMessage shouldBe "repository.artifact.catalog.duplicate-field source=catalog path=versions"

    val runtime = intercept[IllegalArgumentException] {
      When("the version runtime map header is repeated")
      RepositoryArtifactCatalog.parse(
        "schemaVersion: 2\nkind: car\nnamespace: org.example\nid: Sample\nartifactId: example-sample\nversions:\n  - version: 0.1.0\n    runtime:\n      cncf:\n        minimum: 0.4.8\n    runtime:\n      cncf:\n        minimum: 0.4.9\n"
      )
    }
    Then("the runtime header path is reported")
    runtime.getMessage shouldBe "repository.artifact.catalog.duplicate-field source=catalog path=versions[0].runtime"

    val checksum = intercept[IllegalArgumentException] {
      When("the version checksum map header is repeated")
      RepositoryArtifactCatalog.parse(
        "schemaVersion: 2\nkind: car\nnamespace: org.example\nid: Sample\nartifactId: example-sample\nversions:\n  - version: 0.1.0\n    checksum:\n      sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n    checksum:\n      sha256: bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\n"
      )
    }
    Then("the checksum header path is reported")
    checksum.getMessage shouldBe "repository.artifact.catalog.duplicate-field source=catalog path=versions[0].checksum"
  }

  "reject duplicate JSON root and version catalog fields before overwrite" in {
    Given("JSON catalogs with repeated object fields")
    val rootpath = java.nio.file.Path.of("target/cozy-test/work/repository-artifact-catalog-spec/duplicate-root.json").toAbsolutePath.normalize()
    java.nio.file.Files.createDirectories(rootpath.getParent)
    java.nio.file.Files.writeString(rootpath, "{\"schemaVersion\":\"1\",\"schemaVersion\":\"1\",\"kind\":\"sar\",\"artifactId\":\"sample\"}")
    When("the duplicate root object is loaded")
    val root = intercept[IllegalArgumentException] { RepositoryArtifactCatalog.load(rootpath) }
    Then("the duplicate object field reports the catalog JSON boundary")
    root.getMessage shouldBe "repository.artifact.catalog.duplicate-field source=catalog path=json"
    val versionpath = rootpath.resolveSibling("duplicate-version.json")
    java.nio.file.Files.writeString(versionpath, "{\"schemaVersion\":\"1\",\"kind\":\"sar\",\"artifactId\":\"sample\",\"versions\":[{\"version\":\"1.0.0\",\"version\":\"1.0.1\"}]}")
    When("the duplicate version object is loaded")
    val version = intercept[IllegalArgumentException] { RepositoryArtifactCatalog.load(versionpath) }
    Then("the duplicate version field reports the same stable boundary")
    version.getMessage shouldBe "repository.artifact.catalog.duplicate-field source=catalog path=json"
  }

  "reject wrong-type and null JSON catalog fields" in {
    Given("canonical CAR JSON catalogs with malformed typed fields")
    When("each malformed field is loaded")
    _assert_json_type("status-number", _canonical_json("status", "7"), "status", "string", "number")
    _assert_json_type("aliases-scalar", _canonical_json("aliases", "\"alias\""), "aliases", "array", "string")
    _assert_json_type("aliases-null", _canonical_json("aliases", "null"), "aliases", "array", "null")
    _assert_json_type("runtime-array", _canonical_json("runtime", "[]"), "runtime", "object", "array")
    _assert_json_type("runtime-null", _canonical_json("runtime", "null"), "runtime", "object", "null")
    _assert_json_type("runtime-array-element", _canonical_json("runtime", "{\"cncf\":{\"tested\":[1]}}"), "tested[0]", "string", "number")
    _assert_json_type("runtime-array-scalar", _canonical_json("runtime", "{\"cncf\":{\"tested\":\"0.4.8\"}}"), "tested", "array", "string")
    _assert_json_type("runtime-array-null", _canonical_json("runtime", "{\"cncf\":{\"tested\":null}}"), "tested", "array", "null")
    _assert_json_type("checksum-scalar", _canonical_json("checksum", "\"digest\""), "checksum", "object", "string")
    _assert_json_type("checksum-null", _canonical_json("checksum", "null"), "checksum", "object", "null")
    _assert_json_type("checksum-value", _canonical_json("checksum", "{\"sha256\":7}"), "sha256", "string", "number")
    _assert_json_type("checksum-value-null", _canonical_json("checksum", "{\"sha256\":null}"), "sha256", "string", "null")
    _assert_json_type("integrity-map", _canonical_json("integrityKey", "{\"sha256\":\"digest\"}"), "integrityKey", "string", "object")
    _assert_json_type("integrity-null", _canonical_json("integrityKey", "null"), "integrityKey", "string", "null")
    _assert_json_type("schema-null", _canonical_json("schemaVersion", "null"), "schemaVersion", "string", "null")
    _assert_json_type("kind-null", _canonical_json("kind", "null"), "kind", "string", "null")
    _assert_json_type("namespace-null", _canonical_json("namespace", "null"), "namespace", "string", "null")
    _assert_json_type("id-null", _canonical_json("id", "null"), "id", "string", "null")
    _assert_json_type("artifact-id-null", _canonical_json("artifactId", "null"), "artifactId", "string", "null")
    Then("malformed fields report stable typed diagnostics instead of disappearing")
  }
  "parse and render valid CAR catalog deterministically" in {
    Given("a canonical CAR YAML catalog")
    When("the catalog is parsed and rendered")
    val catalog = RepositoryArtifactCatalog.parse(_car_catalog_text)
    Then("identity, aliases, runtime, and rendering remain deterministic")
    catalog.kind shouldBe "car"
    catalog.artifactId shouldBe "textus-semantic-integration-engine"
    catalog.aliases shouldBe Vector("textus-sie")
    catalog.versions.map(_.version) shouldBe Vector("0.1.0", "0.1.1-SNAPSHOT")
    catalog.versions.head.runtime.flatMap(_.minimum) shouldBe Some("0.4.8")
    catalog.versions.head.runtime.map(_.tested).getOrElse(Vector.empty) shouldBe Vector("0.4.8")
    catalog.toYaml shouldBe RepositoryArtifactCatalog.parse(catalog.toYaml).toYaml
  }

  "parse and render valid SAR catalog deterministically" in {
    Given("a SAR v1 YAML catalog")
    When("the catalog is parsed and rendered")
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
    Then("the SAR identity and version file are preserved")
    catalog.kind shouldBe "sar"
    catalog.versions.head.file shouldBe Some("repository/sar/sample-application/1.0.0/sample-application-1.0.0.sar")
    catalog.toYaml shouldBe RepositoryArtifactCatalog.parse(catalog.toYaml).toYaml
  }

  "parse inline aliases" in {
    Given("a canonical CAR catalog with inline aliases")
    When("the aliases are parsed")
    val catalog = RepositoryArtifactCatalog.parse(
      """schemaVersion: 2
        |kind: car
        |namespace: org.example
        |id: Sample
        |artifactId: example-sample
        |aliases: [sample-alias, sample-old]
        |versions:
        |  - version: 0.1.0
        |    channel: stable
        |    status: active
        |    component: org.example.Sample
        |    file: repository/car/org/example/example-sample/0.1.0/example-sample-0.1.0.car
        |    checksum:
        |      sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
        |    integrityKey: org.example:example-sample:0.1.0@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
        |""".stripMargin
    )
    Then("the aliases retain their declared order")
    catalog.aliases shouldBe Vector("sample-alias", "sample-old")
  }

  "validate selector references" in {
    Given("a CAR catalog with all selectors")
    When("selector references are inspected")
    val catalog = RepositoryArtifactCatalog.parse(_car_catalog_text)
    Then("each selector points to its declared release")
    catalog.recommended shouldBe Some("0.1.0")
    catalog.latestStable shouldBe Some("0.1.0")
    catalog.latestSnapshot shouldBe Some("0.1.1-SNAPSHOT")
  }

  }

  "reject invalid catalog documents" which {
  "rejects unknown and empty canonical YAML headers without weakening SAR v1" in {
    Given("canonical CAR YAML with empty unknown root, version, and runtime headers")
    val rooterror = intercept[IllegalArgumentException] {
      When("an unknown empty root header is parsed")
      RepositoryArtifactCatalog.parse(
        "schemaVersion: 2\nkind: car\nnamespace: org.example\nid: Sample\nartifactId: example-sample\nbogus:\nversions:\n"
      )
    }
    Then("the root header reports the stable canonical-shape diagnostic")
    rooterror.getMessage shouldBe "component.release-coordinate.projection-mismatch source=catalog field=unknown expected=canonical-fields actual=bogus"

    val versionerror = intercept[IllegalArgumentException] {
      When("an unknown empty version header is parsed")
      RepositoryArtifactCatalog.parse(
        "schemaVersion: 2\nkind: car\nnamespace: org.example\nid: Sample\nartifactId: example-sample\nversions:\n  - version: 0.1.0\n    bogus:\n"
      )
    }
    Then("the version header reports the stable canonical-shape diagnostic")
    versionerror.getMessage shouldBe "component.release-coordinate.projection-mismatch source=catalog field=versions expected=canonical-fields actual=bogus"

    val runtimeerror = intercept[IllegalArgumentException] {
      When("an empty known runtime header is parsed")
      RepositoryArtifactCatalog.parse(
        "schemaVersion: 2\nkind: car\nnamespace: org.example\nid: Sample\nartifactId: example-sample\nversions:\n  - version: 0.1.0\n    runtime:\n"
      )
    }
    Then("the empty runtime header reports its required canonical child")
    runtimeerror.getMessage shouldBe "component.release-coordinate.projection-mismatch source=catalog field=versions expected=runtime.cncf actual=runtime[0]"

    val sar = RepositoryArtifactCatalog.parse(
      "schemaVersion: 1\nkind: sar\nartifactId: sample\nbogus:\nversions:\n  - version: 1.0.0\n    file: sample.sar\n"
    )
    Then("SAR v1 still ignores unknown empty headers")
    sar.kind shouldBe "sar"
    sar.versions.head.version shouldBe "1.0.0"
  }

  "reject duplicate versions" in {
    Given("a CAR catalog with the same version twice")
    When("the catalog is parsed")
    val ex = intercept[IllegalArgumentException] {
      RepositoryArtifactCatalog.parse(
        """schemaVersion: 2
          |kind: car
          |namespace: org.example
          |id: Sample
          |artifactId: example-sample
          |versions:
          |  - version: 0.1.0
          |  - version: 0.1.0
          |""".stripMargin
      )
    }
    Then("duplicate versions are rejected")
    ex.getMessage should include("Duplicate")
  }

  "reject invalid kind status and channel" in {
    Given("catalogs with unsupported kind, status, and channel values")
    When("each invalid value is parsed")
    _assert_invalid("kind: jar", "kind")
    _assert_invalid("kind: car\nstatus: hidden", "status")
    val ex = intercept[IllegalArgumentException] {
      RepositoryArtifactCatalog.parse(
        """schemaVersion: 2
          |kind: car
          |namespace: org.example
          |id: Sample
          |artifactId: example-sample
          |versions:
          |  - version: 0.1.0
          |    channel: nightly
          |""".stripMargin
      )
    }
    Then("the diagnostics identify the invalid field")
    ex.getMessage should include("channel")
  }

  "reject disabled recommended version" in {
    Given("a selector pointing to a disabled release")
    When("the catalog is parsed")
    val ex = intercept[IllegalArgumentException] {
      RepositoryArtifactCatalog.parse(
        """schemaVersion: 2
          |kind: car
          |namespace: org.example
          |id: Sample
          |artifactId: example-sample
          |recommended: 0.1.0
          |versions:
          |  - version: 0.1.0
          |    channel: stable
          |    status: disabled
          |    component: org.example.Sample
          |    file: repository/car/org/example/example-sample/0.1.0/example-sample-0.1.0.car
          |    checksum:
          |      sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
          |    integrityKey: org.example:example-sample:0.1.0@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
        |""".stripMargin
      )
    }
    Then("the disabled selector is rejected")
    ex.getMessage should include("disabled")
  }

  "reject selector pointing to missing version" in {
    Given("a selector pointing to an absent release")
    When("the catalog is parsed")
    val ex = intercept[IllegalArgumentException] {
      RepositoryArtifactCatalog.parse(
        """schemaVersion: 2
          |kind: car
          |namespace: org.example
          |id: Sample
          |artifactId: example-sample
          |latestStable: 0.2.0
          |versions:
          |  - version: 0.1.0
          |    channel: stable
          |    status: active
          |    component: org.example.Sample
          |    file: repository/car/org/example/example-sample/0.1.0/example-sample-0.1.0.car
          |    checksum:
          |      sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
          |    integrityKey: org.example:example-sample:0.1.0@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
        |""".stripMargin
      )
    }
    Then("the missing release is reported")
    ex.getMessage should include("missing version")
  }

  "reject latest selectors with wrong channel" in {
    Given("latest selectors whose channels do not match")
    When("the stable selector is parsed")
    val stableex = intercept[IllegalArgumentException] {
      RepositoryArtifactCatalog.parse(
        """schemaVersion: 2
          |kind: car
          |namespace: org.example
          |id: Sample
          |artifactId: example-sample
          |latestStable: 0.2.0-SNAPSHOT
          |versions:
          |  - version: 0.2.0-SNAPSHOT
          |    channel: snapshot
          |    component: org.example.Sample
          |    file: repository/car/org/example/example-sample/0.2.0-SNAPSHOT/example-sample-0.2.0-SNAPSHOT.car
          |    checksum:
          |      sha256: bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
          |    integrityKey: org.example:example-sample:0.2.0-SNAPSHOT@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
        |""".stripMargin
      )
    }
    Then("the stable channel mismatch is reported")
    stableex.getMessage should include("stable")

    When("the snapshot selector is parsed")
    val snapshotex = intercept[IllegalArgumentException] {
      RepositoryArtifactCatalog.parse(
        """schemaVersion: 2
          |kind: car
          |namespace: org.example
          |id: Sample
          |artifactId: example-sample
          |latestSnapshot: 0.1.0
          |versions:
          |  - version: 0.1.0
          |    channel: stable
          |    component: org.example.Sample
          |    file: repository/car/org/example/example-sample/0.1.0/example-sample-0.1.0.car
          |    checksum:
          |      sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
          |    integrityKey: org.example:example-sample:0.1.0@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
        |""".stripMargin
      )
    }
    Then("the snapshot channel mismatch is reported")
    snapshotex.getMessage should include("snapshot")
  }

  "reject version without file" in {
    Given("a CAR version without an archive file")
    When("the catalog is parsed")
    val ex = intercept[IllegalArgumentException] {
      RepositoryArtifactCatalog.parse(
        """schemaVersion: 2
          |kind: car
          |namespace: org.example
          |id: Sample
          |artifactId: example-sample
          |versions:
          |  - version: 0.1.0
          |    status: active
          |""".stripMargin
      )
    }
    Then("the missing file is reported")
    ex.getMessage should include("requires file")
  }

  "reject wrong archive extension" in {
    Given("a CAR version pointing to a SAR archive")
    When("the catalog is parsed")
    val ex = intercept[IllegalArgumentException] {
      RepositoryArtifactCatalog.parse(
        """schemaVersion: 2
          |kind: car
          |namespace: org.example
          |id: Sample
          |artifactId: example-sample
          |versions:
          |  - version: 0.1.0
          |    file: repository/car/org/example/example-sample/0.1.0/example-sample-0.1.0.sar
        |""".stripMargin
      )
    }
    Then("the CAR extension requirement is reported")
    ex.getMessage should include(".car")
  }

  }

  }

  "Repository artifact catalog persistence" should {
  "validate source path against kind and artifact id" in {
    Given("a CAR catalog persisted at its canonical path")
    _with_temp_dir("repository-artifact-catalog") { dir =>
      val path = dir.resolve("src/main/catalog/car/org/example/textus/textus-semantic-integration-engine.yaml")
      _write(path, _car_catalog_text)
      When("the canonical path is loaded")
      val catalog = RepositoryArtifactCatalog.load(path)
      Then("the artifact identity is preserved")
      catalog.artifactId shouldBe "textus-semantic-integration-engine"

      val wrongpath = dir.resolve("src/main/catalog/car/org/example/textus/other.yaml")
      _write(wrongpath, _car_catalog_text)
      When("a path with the wrong filename is loaded")
      val ex = intercept[IllegalArgumentException] {
        RepositoryArtifactCatalog.load(wrongpath)
      }
      ex.getMessage should include("filename")

      val wrongkindpath = dir.resolve("src/main/catalog/sar/textus-semantic-integration-engine.yaml")
      _write(wrongkindpath, _car_catalog_text)
      When("a path with the wrong catalog kind is loaded")
      val ex2 = intercept[IllegalArgumentException] {
        RepositoryArtifactCatalog.load(wrongkindpath)
      }
      ex2.getMessage should include("component.release-coordinate.projection-mismatch")
      ex2.getMessage should include("field=catalogPath")
    }
  }

  "load and validate JSON catalog through the canonical loader" in {
    Given("a SAR v1 JSON catalog in its canonical path")
    _with_temp_dir("repository-artifact-json-catalog") { dir =>
      val path = dir.resolve("src/main/catalog/sar/sample-application.json")
      _write(
        path,
        """{
          |  "schemaVersion": "1",
          |  "kind": "sar",
          |  "artifactId": "sample-application",
          |  "latestStable": "0.1.0",
          |  "tags": ["platform.sie"],
          |  "versions": [{
          |    "version": "0.1.0",
          |    "channel": "stable",
          |    "file": "repository/sar/sample-application/0.1.0/sample-application-0.1.0.sar"
          |  }]
          |}
          |""".stripMargin
      )

      When("the JSON catalog is loaded")
      val catalog = RepositoryArtifactCatalog.load(path)

      Then("the artifact identity and tags are preserved")
      catalog.artifactId shouldBe "sample-application"
      catalog.latestStable shouldBe Some("0.1.0")
      catalog.tags shouldBe Vector("platform.sie")
    }
  }

  }

  "Repository artifact Maven metadata" should {
  "render Maven metadata from catalog selectors" in {
    Given("a CAR catalog with active, deprecated, snapshot, and disabled releases")
    When("Maven metadata is rendered")
    val catalog = RepositoryArtifactCatalog.parse(
      """schemaVersion: 2
        |kind: car
        |namespace: org.example
        |id: Sample
        |artifactId: example-sample
        |recommended: 0.0.9
        |latestStable: 0.1.0
        |latestSnapshot: 0.2.0-SNAPSHOT
        |versions:
        |  - version: 0.0.9
        |    channel: stable
        |    status: deprecated
        |    component: org.example.Sample
        |    publishedAt: 2026-05-19T10:11:12Z
        |    file: repository/car/org/example/example-sample/0.0.9/example-sample-0.0.9.car
        |    checksum:
        |      sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
        |    integrityKey: org.example:example-sample:0.0.9@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
        |  - version: 0.1.0
        |    channel: stable
        |    component: org.example.Sample
        |    publishedAt: 2026-05-20T10:11:12Z
        |    file: repository/car/org/example/example-sample/0.1.0/example-sample-0.1.0.car
        |    checksum:
        |      sha256: bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
        |    integrityKey: org.example:example-sample:0.1.0@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
        |  - version: 0.2.0-SNAPSHOT
        |    channel: snapshot
        |    component: org.example.Sample
        |    publishedAt: 2026-05-20T12:11:12Z
        |    file: repository/car/org/example/example-sample/0.2.0-SNAPSHOT/example-sample-0.2.0-SNAPSHOT.car
        |    checksum:
        |      sha256: cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
        |    integrityKey: org.example:example-sample:0.2.0-SNAPSHOT@sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
        |  - version: 0.3.0
        |    channel: stable
        |    status: disabled
        |    component: org.example.Sample
        |    publishedAt: 2026-05-20T13:11:12Z
        |    file: repository/car/org/example/example-sample/0.3.0/example-sample-0.3.0.car
        |    checksum:
        |      sha256: dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd
        |    integrityKey: org.example:example-sample:0.3.0@sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd
        |""".stripMargin
    )
    val metadata = RepositoryArtifactMavenMetadata.toXml(catalog, "2026-05-20T00:00:00Z")
    Then("selectors, active versions, and publication timestamps are projected")
    metadata should include("<groupId>org.example</groupId>")
    metadata should include("<artifactId>example-sample</artifactId>")
    metadata should include("<latest>0.0.9</latest>")
    metadata should include("<release>0.1.0</release>")
    metadata should include("<version>0.0.9</version>")
    metadata should include("<version>0.1.0</version>")
    metadata should include("<version>0.2.0-SNAPSHOT</version>")
    metadata should not include "<version>0.3.0</version>"
    metadata should include("<lastUpdated>20260519101112</lastUpdated>")
  }

  "render Maven metadata fallback latest by version order" in {
    Given("a CAR catalog without an explicit recommended selector")
    When("Maven metadata is rendered")
    val catalog = RepositoryArtifactCatalog.parse(
      """schemaVersion: 2
        |kind: car
        |namespace: org.example
        |id: Sample
        |artifactId: example-sample
        |versions:
        |  - version: 0.2.0
        |    channel: stable
        |    component: org.example.Sample
        |    publishedAt: 2026-05-20T02:00:00Z
        |    file: repository/car/org/example/example-sample/0.2.0/example-sample-0.2.0.car
        |    checksum:
        |      sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
        |    integrityKey: org.example:example-sample:0.2.0@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
        |  - version: 0.10.0
        |    channel: stable
        |    component: org.example.Sample
        |    publishedAt: 2026-05-20T10:00:00Z
        |    file: repository/car/org/example/example-sample/0.10.0/example-sample-0.10.0.car
        |    checksum:
        |      sha256: bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
        |    integrityKey: org.example:example-sample:0.10.0@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
        |  - version: 0.11.0
        |    channel: stable
        |    status: disabled
        |    component: org.example.Sample
        |    publishedAt: 2026-05-20T11:00:00Z
        |    file: repository/car/org/example/example-sample/0.11.0/example-sample-0.11.0.car
        |    checksum:
        |      sha256: cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
        |    integrityKey: org.example:example-sample:0.11.0@sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
        |""".stripMargin
    )
    val metadata = RepositoryArtifactMavenMetadata.toXml(catalog, "2026-05-20T00:00:00Z")
    Then("the highest active stable version is used as latest")
    metadata should include("<latest>0.10.0</latest>")
    metadata should not include "<version>0.11.0</version>"
    metadata should include("<lastUpdated>20260520100000</lastUpdated>")
  }

  }

  private def _assert_json_type(
    name: String,
    text: String,
    field: String,
    expected: String,
    actual: String
  ): Unit =
    _with_temp_dir("repository-artifact-json-type") { dir =>
      val path = dir.resolve(s"$name.json")
      _write(path, text)
      val error = intercept[IllegalArgumentException](RepositoryArtifactCatalog.load(path))
      error.getMessage should include(s"field=$field")
      error.getMessage should include(s"expected=$expected")
      error.getMessage should include(s"actual=$actual")
    }

  private def _canonical_json(field: String, value: String): String = {
    val schemavalue = if (field == "schemaVersion") value else "\"2\""
    val kindvalue = if (field == "kind") value else "\"car\""
    val namespacevalue = if (field == "namespace") value else "\"org.example\""
    val idvalue = if (field == "id") value else "\"Sample\""
    val artifactidvalue = if (field == "artifactId") value else "\"example-sample\""
    val rootfield = if (Set("schemaVersion", "kind", "namespace", "id", "artifactId", "runtime", "checksum", "integrityKey").contains(field)) "" else "\"" + field + "\":" + value + ","
    val versionruntime = if (field == "runtime") "\"runtime\":" + value + "," else ""
    val versionchecksum = if (field == "checksum") "\"checksum\":" + value + "," else "\"checksum\":{\"sha256\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"},"
    val versionintegrity = if (field == "integrityKey") "\"integrityKey\":" + value else "\"integrityKey\":\"org.example:example-sample:0.1.0@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\""
    s"""{
       |  "schemaVersion": $schemavalue,
       |  "kind": $kindvalue,
       |  "namespace": $namespacevalue,
       |  "id": $idvalue,
       |  "artifactId": $artifactidvalue,
       |  $rootfield
       |  "versions": [{
       |    "version": "0.1.0",
       |    "file": "repository/car/org/example/example-sample/0.1.0/example-sample-0.1.0.car",
       |    $versionruntime$versionchecksum$versionintegrity
       |  }]
       |}""".stripMargin
  }

  private def _assert_invalid(line: String, expected: String): Unit = {
    val ex = intercept[IllegalArgumentException] {
      RepositoryArtifactCatalog.parse(
        s"""schemaVersion: 2
           |$line
           |namespace: org.example
           |id: Sample
           |artifactId: example-sample
           |versions:
           |  - version: 0.1.0
           |""".stripMargin
      )
    }
    ex.getMessage should include(expected)
  }

  private def _with_temp_dir(prefix: String)(body: Path => Unit): Unit = {
    val root = Path.of("target/cozy-test/work/repository-artifact-catalog-spec").toAbsolutePath.normalize()
    Files.createDirectories(root)
    val dir = Files.createTempDirectory(root, prefix + "-")
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
    """schemaVersion: 2
      |kind: car
      |namespace: org.example.textus
      |id: SemanticIntegrationEngine
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
      |    component: org.example.textus.SemanticIntegrationEngine
      |    publishedAt: 2026-05-20T00:00:00Z
      |    file: repository/car/org/example/textus/textus-semantic-integration-engine/0.1.0/textus-semantic-integration-engine-0.1.0.car
      |    runtime:
      |      cncf:
      |        minimum: 0.4.8
      |        maximum:
      |        excluded: []
      |        tested:
      |          - 0.4.8
      |    checksum:
      |      sha256: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
      |    integrityKey: org.example.textus:textus-semantic-integration-engine:0.1.0@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
      |  - version: 0.1.1-SNAPSHOT
      |    channel: snapshot
      |    status: active
      |    component: org.example.textus.SemanticIntegrationEngine
      |    publishedAt: 2026-05-20T01:00:00Z
      |    file: repository/car/org/example/textus/textus-semantic-integration-engine/0.1.1-SNAPSHOT/textus-semantic-integration-engine-0.1.1-SNAPSHOT.car
      |    checksum:
      |      sha256: bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
      |    integrityKey: org.example.textus:textus-semantic-integration-engine:0.1.1-SNAPSHOT@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
      |""".stripMargin
}
