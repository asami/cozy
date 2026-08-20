package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.Comparator
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}
import scala.collection.JavaConverters._

import cozy.compatibility.{
  GenerationCompatibility,
  GenerationCompatibilityBoundary,
  GenerationCompatibilityEvidence,
  GenerationEvidenceOwner,
  GenerationPairEvidence,
  GenerationPairStatus
}
import cozy.modeler.GenerationProvenance
import cozy.scaffold.CozyScaffold
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

/*
 * @since   Jul. 28, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyArchivePackagerCv06Spec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "CAR archive compatibility admission" should {
    "preserve the scaffold project contract despite ambient operation defaults" in {
      _with_temp_dir("cozy-cv06-scaffold-package") { dir =>
        Given("a generated CAR project and stale operation-default compatibility values")
        val projectyaml = _project_yaml(dir)
        val buildsbt = _build_sbt(dir)
        _write(dir.resolve("project.yaml"), projectyaml)
        _write(
          dir.resolve(".cozy/config.yaml"),
          """build:
            |  cozyVersion: "ambient-generator"
            |  dependencies:
            |    compile:
            |      - "org.goldenport::goldenport-cncf:9.9.9"
            |packaging:
            |  car:
            |    runtime:
            |      cncf:
            |        version: "9.9.9"
            |        minimum: "9.9.9"
            |        tested:
            |          - "9.9.9"
            |""".stripMargin
        )
        val mainjar = _write(dir.resolve("artifacts/sample.jar"), "sample")
        val generationprovenance = _write_generation_provenance(dir, "0.5.17")
        val cncfjar = _write_runtime_jar(
          dir.resolve("artifacts/goldenport-cncf_3.jar"),
          "cncf",
          "org.goldenport:goldenport-cncf_3:0.5.17",
          Some("0.5.17")
        )
        val archive = dir.resolve("target/sample.car")
        val snapshotsbefore = _generation_provenance_snapshots

        When("Cozy packages against the resolved CNCF artifact")
        _build_car(dir, mainjar, cncfjar, archive)

        Then("only project-owned generator, compile, and runtime metadata govern admission")
        projectyaml should include("""cozyVersion: """ + _quoted(org.simplemodeling.cozy.BuildInfo.version))
        projectyaml should include("org.goldenport::goldenport-cncf:0.5.17")
        buildsbt should include(
          """ProjectYamlBuild.requiredValue(cozyProjectMetadata.value, "build.cozyVersion")"""
        )
        _zip_entries(archive) should contain allOf (
          "component/main.jar",
          "component-descriptor.json",
          "abi-manifest.json",
          "generation-provenance.json",
          CozyCarRuntimeManifest.FILE_NAME
        )
        _zip_text(archive, "generation-provenance.json") shouldBe
          Files.readString(generationprovenance, StandardCharsets.UTF_8)
        val runtimemanifest = Json.parse(
          _zip_text(archive, CozyCarRuntimeManifest.FILE_NAME)
        )
        (runtimemanifest \ "schemaVersion").as[String] shouldBe
          CozyCarRuntimeManifest.SCHEMA_VERSION
        (runtimemanifest \ "car" \ "name").as[String] shouldBe _artifact
        (runtimemanifest \ "car" \ "version").as[String] shouldBe
          "0.0.1-SNAPSHOT"
        (runtimemanifest \ "car" \ "component").as[String] shouldBe _id
        (runtimemanifest \ "runtime" \ "cncf" \ "minimum").as[String] shouldBe
          "0.5.17"
        (runtimemanifest \ "runtime" \ "cncf" \ "excluded").as[Vector[String]] shouldBe
          Vector.empty
        (runtimemanifest \ "runtime" \ "cncf" \ "tested").as[Vector[String]] shouldBe
          Vector("0.5.17")
        val integrityentries =
          (runtimemanifest \ "integrity" \ "entries")
            .as[Vector[play.api.libs.json.JsObject]]
            .map { entry =>
              (entry \ "path").as[String] -> (entry \ "sha256").as[String]
            }
            .toMap
        integrityentries.keySet shouldBe
          (_zip_entries(archive).toSet - CozyCarRuntimeManifest.FILE_NAME)
        integrityentries("component/main.jar") shouldBe
          _sha256(_zip_bytes(archive, "component/main.jar"))
        integrityentries("generation-provenance.json") shouldBe
          _sha256(_zip_bytes(archive, "generation-provenance.json"))
        (_generation_provenance_snapshots -- snapshotsbefore) shouldBe empty
      }
    }

    "reject packaging without explicit CAR project classification" in {
      _with_temp_dir("cozy-cv06-project-kind") { dir =>
        Given("project metadata that describes a library instead of a CAR")
        _write(
          dir.resolve("project.yaml"),
          """project:
            |  name: sample
            |  kind: library
            |packaging:
            |  kind: jar
            |""".stripMargin
        )
        val mainjar = _write(dir.resolve("artifacts/sample.jar"), "sample")
        val cncfjar = _write_runtime_jar(
          dir.resolve("artifacts/goldenport-cncf_3.jar"),
          "cncf",
          "org.goldenport:goldenport-cncf_3:0.5.17",
          Some("0.5.17")
        )
        val archive = dir.resolve("target/sample.car")

        When("the CAR packaging boundary evaluates the project")
        val error = intercept[Throwable] {
          _build_car(dir, mainjar, cncfjar, archive)
        }

        Then("packaging rejects the project before producing an unusable CAR")
        error.getMessage should include("CAR_METADATA_CAR_CLASSIFICATION_REQUIRED")
        error.getMessage should include("project.kind=library")
        error.getMessage should include("packaging.kind=jar")
        Files.exists(archive) shouldBe false
      }
    }

    "reject package-car without its required project directory" in {
      _with_temp_dir("cozy-cv06-project-dir") { dir =>
        Given("otherwise complete package arguments without --project-dir")
        val mainjar = _write(dir.resolve("artifacts/sample.jar"), "sample")
        val archive = dir.resolve("target/sample.car")

        When("the public CAR packaging boundary parses the request")
        val error = intercept[Throwable] {
          CozyArchivePackager.buildCar(List(
            "--save", archive.toString,
            "--main-jar", mainjar.toString,
            "--name", "sample",
            "--version", "0.0.1-SNAPSHOT",
            "--component", "sample"
          ))
        }

        Then("the missing required argument is reported before metadata admission")
        error.getMessage should include("Missing --project-dir")
        Files.exists(archive) shouldBe false
      }
    }

    "reject a generated release CAR that selects a mutable Cozy generator" in {
      _with_temp_dir("cozy-cv07-release-generator") { dir =>
        Given("a release CAR project with a proven development generation pair")
        val mutablegeneratorversion = "0.3.1-SNAPSHOT"
        val projectyaml =
          _project_yaml(dir).
            replace("0.5.17", "0.5.1").
            replace(org.simplemodeling.cozy.BuildInfo.version, mutablegeneratorversion).
            replace("0.0.1-SNAPSHOT", "0.0.1")
        _write(dir.resolve("project.yaml"), projectyaml)
        _write(
          dir.resolve("src/main/cozy/sample.cml"),
          "component Sample\n"
        )
        val mainjar = _write(dir.resolve("artifacts/sample.jar"), "sample")
        val cncfjar = _write_runtime_jar(
          dir.resolve("artifacts/goldenport-cncf_3.jar"),
          "cncf",
          "org.goldenport:goldenport-cncf_3:0.5.1",
          Some("0.5.1")
        )
        val archive = dir.resolve("target/sample.car")
        val mutablepair =
          GenerationCompatibilityBoundary.createPair("0.5.1", mutablegeneratorversion)
        val evidence = GenerationCompatibilityEvidence(
          GenerationCompatibility.evidenceSchema,
          GenerationEvidenceOwner(
            "CV-07 archive executable specification",
            "CozyArchivePackagerCv06Spec"
          ),
          Vector(GenerationPairEvidence(mutablepair, GenerationPairStatus.Proven)),
          None
        )

        When("the release package gate evaluates the project-owned pair")
        val error = intercept[Throwable] {
          _build_car(
            dir,
            mainjar,
            cncfjar,
            archive,
            outputversion = "0.0.1",
            acceptanceoverride = Some(evidence -> mutablegeneratorversion)
          )
        }

        Then("the mutable generator is rejected before archive output")
        error.getMessage should include("SnapshotNotAllowedForRelease")
        error.getMessage should include(
          s"org.simplemodeling:cozy_2.12:$mutablegeneratorversion"
        )
        Files.exists(archive) shouldBe false
      }
    }

    "reject contradictory package evidence" which {
    "reject generation provenance that contradicts the scaffold compile target" in {
      _with_temp_dir("cozy-cv06-provenance-target") { dir =>
        Given("a generated CAR project whose provenance records another CNCF target")
        _write(dir.resolve("project.yaml"), _project_yaml(dir))
        val mainjar = _write(dir.resolve("artifacts/sample.jar"), "sample")
        val cncfjar = _write_runtime_jar(
          dir.resolve("artifacts/goldenport-cncf_3.jar"),
          "cncf",
          "org.goldenport:goldenport-cncf_3:0.5.17",
          Some("0.5.17")
        )
        _write_generation_provenance(dir, "0.5.16")
        val archive = dir.resolve("target/sample.car")
        val snapshotsbefore = _generation_provenance_snapshots

        When("Cozy attempts to package the contradictory evidence")
        val error = intercept[Throwable] {
          _build_car(dir, mainjar, cncfjar, archive)
        }

        Then("the provenance gate fails before writing the CAR")
        error.getMessage should include("GENERATION_PROVENANCE_INPUT_MISMATCH")
        Files.exists(archive) shouldBe false
        (_generation_provenance_snapshots -- snapshotsbefore) shouldBe empty
      }
    }

    "reject source-managed provenance that could bypass target validation" in {
      _with_temp_dir("cozy-cv06-source-provenance") { dir =>
        Given("a CAR source directory containing forged generation provenance")
        _write(dir.resolve("project.yaml"), _project_yaml(dir))
        _write(
          dir.resolve("src/main/car/generation-provenance.json"),
          """{"schemaVersion":"forged"}"""
        )
        val mainjar = _write(dir.resolve("artifacts/sample.jar"), "sample")
        val cncfjar = _write_runtime_jar(
          dir.resolve("artifacts/goldenport-cncf_3.jar"),
          "cncf",
          "org.goldenport:goldenport-cncf_3:0.5.17",
          Some("0.5.17")
        )
        val archive = dir.resolve("target/sample.car")

        When("Cozy evaluates generic CAR source entries")
        val error = intercept[Throwable] {
          _build_car(dir, mainjar, cncfjar, archive)
        }

        Then("the reserved provenance name fails instead of entering the CAR")
        error.getMessage should include("Source-managed generation provenance is forbidden")
        Files.exists(archive) shouldBe false
      }
    }

    "reject source-managed runtime evidence that could bypass CNCF admission" in {
      _with_temp_dir("cozy-cv06-source-runtime-manifest") { dir =>
        Given("a CAR source directory containing forged runtime and integrity evidence")
        _write(dir.resolve("project.yaml"), _project_yaml(dir))
        _write(
          dir.resolve("src/main/car").resolve(CozyCarRuntimeManifest.FILE_NAME),
          """{"schemaVersion":"forged"}"""
        )
        val mainjar = _write(dir.resolve("artifacts/sample.jar"), "sample")
        val cncfjar = _write_runtime_jar(
          dir.resolve("artifacts/goldenport-cncf_3.jar"),
          "cncf",
          "org.goldenport:goldenport-cncf_3:0.5.17",
          Some("0.5.17")
        )
        val archive = dir.resolve("target/sample.car")

        When("Cozy evaluates generic CAR source entries")
        val error = intercept[Throwable] {
          _build_car(dir, mainjar, cncfjar, archive)
        }

        Then("the CNCF-owned reserved name fails instead of entering the CAR")
        error.getMessage should include("Source-managed CAR runtime manifest is forbidden")
        Files.exists(archive) shouldBe false
      }
    }

    "reject a matching version from a non-CNCF JAR descriptor" in {
      _with_temp_dir("cozy-cv06-artifact-identity") { dir =>
        Given("a valid CAR project but a resolved JAR claiming another runtime and module")
        _write(dir.resolve("project.yaml"), _project_yaml(dir))
        val mainjar = _write(dir.resolve("artifacts/sample.jar"), "sample")
        val fakejar = _write_runtime_jar(
          dir.resolve("artifacts/fake-runtime.jar"),
          "other",
          "com.example:other-runtime_3:0.5.17",
          Some("0.5.17")
        )
        val archive = dir.resolve("target/sample.car")

        When("Cozy attempts package admission")
        val error = intercept[Throwable] {
          _build_car(dir, mainjar, fakejar, archive)
        }

        Then("artifact identity fails before any archive is written")
        error.getMessage should include("CAR_METADATA_RESOLVED_CNCF_IDENTITY_MISMATCH")
        Files.exists(archive) shouldBe false
      }
    }

    "reject a CNCF JAR descriptor without its root version" in {
      _with_temp_dir("cozy-cv06-artifact-version") { dir =>
        Given("a valid CAR project but a resolved CNCF JAR whose descriptor omits version")
        _write(dir.resolve("project.yaml"), _project_yaml(dir))
        val mainjar = _write(dir.resolve("artifacts/sample.jar"), "sample")
        val cncfjar = _write_runtime_jar(
          dir.resolve("artifacts/goldenport-cncf_3.jar"),
          "cncf",
          "org.goldenport:goldenport-cncf_3:0.5.17",
          None
        )
        val archive = dir.resolve("target/sample.car")

        When("Cozy attempts package admission")
        val error = intercept[Throwable] {
          _build_car(dir, mainjar, cncfjar, archive)
        }

        Then("the missing descriptor version fails before any archive is written")
        error.getMessage should include("CAR_METADATA_RESOLVED_CNCF_VERSION_MISMATCH")
        Files.exists(archive) shouldBe false
      }
    }

    "reject a project range contradiction through the archive gate" in {
      _with_temp_dir("cozy-cv06-range-gate") { dir =>
        Given("a CAR project whose runtime exclusions contain its exact compile target")
        val projectyaml = _project_yaml(dir).replace(
          "        excluded: []",
          "        excluded:\n          - \"0.5.17\""
        )
        _write(dir.resolve("project.yaml"), projectyaml)
        val mainjar = _write(dir.resolve("artifacts/sample.jar"), "sample")
        val cncfjar = _write_runtime_jar(
          dir.resolve("artifacts/goldenport-cncf_3.jar"),
          "cncf",
          "org.goldenport:goldenport-cncf_3:0.5.17",
          Some("0.5.17")
        )
        val archive = dir.resolve("target/sample.car")

        When("Cozy attempts package admission")
        val error = intercept[Throwable] {
          _build_car(dir, mainjar, cncfjar, archive)
        }

        Then("the package gate reports the typed range diagnostic before writing output")
        error.getMessage should include("CAR_METADATA_CNCF_COMPILE_TARGET_EXCLUDED")
        Files.exists(archive) shouldBe false
      }
    }
    }

    "enforce complete immutable release evidence" which {
      "reject missing provenance and package the same proven pair after provenance is supplied" in {
        _with_temp_dir("cozy-cv07-immutable-release") { dir =>
          Given("a release CAR project using one explicitly proven immutable generation pair")
          _write(
            dir.resolve("project.yaml"),
            s"""project:
              |  namespace: ${_namespace}
              |  id: ${_id}
              |  kind: car
              |  component:
              |    version: 0.0.1
              |build:
              |  cozyVersion: 0.3.0
              |  dependencies:
              |    compile:
              |      - "org.goldenport::goldenport-cncf:0.5.1"
              |packaging:
              |  kind: car
              |  car:
              |    abi:
              |      dependencies: []
              |    runtime:
              |      cncf:
              |        minimum: 0.5.1
              |        excluded: []
              |        tested:
              |          - 0.5.1
              |""".stripMargin
          )
          _write(
            dir.resolve("src/main/cozy/sample.cml"),
            "component Sample\n"
          )
          val mainjar = _write(dir.resolve("artifacts/sample.jar"), "sample")
          val cncfjar = _write_runtime_jar(
            dir.resolve("artifacts/goldenport-cncf_3.jar"),
            "cncf",
            "org.goldenport:goldenport-cncf_3:0.5.1",
            Some("0.5.1")
          )
          val archive = dir.resolve("target/sample.car")
          val pair =
            GenerationCompatibilityBoundary.createPair("0.5.1", "0.3.0")
          val evidence = GenerationCompatibilityEvidence(
            GenerationCompatibility.evidenceSchema,
            GenerationEvidenceOwner(
              "CV-07 archive executable specification",
              "CozyArchivePackagerCv06Spec"
            ),
            Vector(GenerationPairEvidence(pair, GenerationPairStatus.Proven)),
            None
          )

          When("the proven release is packaged before provenance exists")
          val missingerror = intercept[Throwable] {
            _build_car(
              dir,
              mainjar,
              cncfjar,
              archive,
              outputversion = "0.0.1",
              acceptanceoverride = Some(evidence -> "0.3.0")
            )
          }

          Then("the release is rejected before archive output")
          missingerror.getMessage should include(
            "Generated release CAR requires target/cozy/generation-provenance.json"
          )
          Files.exists(archive) shouldBe false

          When("valid provenance for the same immutable pair is supplied")
          _write_generation_provenance(
            dir,
            "0.5.1",
            cozyversion = "0.3.0",
            outputversion = "0.0.1"
          )
          _build_car(
            dir,
            mainjar,
            cncfjar,
            archive,
            outputversion = "0.0.1",
            acceptanceoverride = Some(evidence -> "0.3.0")
          )

          Then("the release CAR contains the validated immutable provenance")
          Files.exists(archive) shouldBe true
          _zip_entries(archive) should contain("generation-provenance.json")
        }
      }
    }
  }

  private val _namespace = "org.example"
  private val _id = "Sample"
  private val _artifact = "example-sample"

  private def _project_yaml(dir: Path): String =
    CozyScaffold.carProjectYaml(_init(dir), _versions).
      replace("project:\n", s"project:\n  namespace: ${_namespace}\n  id: ${_id}\n")

  private def _build_sbt(dir: Path): String =
    CozyScaffold.carBuildSbt(_versions, _init(dir).scaffold)

  private def _init(dir: Path): CozyScaffold.ComponentInitConfig = {
    val scaffold = CozyScaffold.CarScaffoldConfig(
      componentName = "Sample",
      serviceName = "Notice",
      entityName = "Notice",
      commandOperationName = "PostNotice",
      queryOperationName = "SearchNotices",
      packageName = "domain",
      artifactName = "sample",
      organization = "com.example",
      version = "0.0.1-SNAPSHOT",
      boundedContext = "default",
      domain = "default",
      gitignore = false,
      readme = false,
      tests = false
    )
    CozyScaffold.ComponentInitConfig(
      dir,
      CozyScaffold.ProjectLayoutStyle.CarOnly,
      scaffold,
      "Sample"
    )
  }

  private def _versions: CozyScaffold.CarDependencyVersions =
    CozyScaffold.CarDependencyVersions(
      "0.5.17",
      "0.2.0",
      "0.3.0"
    )

  private def _build_car(
    dir: Path,
    mainjar: Path,
    cncfjar: Path,
    archive: Path,
    outputversion: String = "0.0.1-SNAPSHOT",
    acceptanceoverride: Option[(GenerationCompatibilityEvidence, String)] = None
  ): Unit = {
    val args = List(
      "--save", archive.toString,
      "--project-dir", dir.toString,
      "--main-jar", mainjar.toString,
      "--lib-jars", cncfjar.toString,
      "--name", _artifact,
      "--version", outputversion,
      "--component", _id
    )
    acceptanceoverride match {
      case Some((evidence, executingcozyversion)) =>
          CozyArchivePackager._build_car(args, evidence, executingcozyversion)
      case None =>
        CozyArchivePackager.buildCar(args)
    }
  }

  private def _quoted(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val workroot = Path.of("target/cozy-test/work/cozy-archive-packager-cv06-spec").toAbsolutePath.normalize()
    Files.createDirectories(workroot)
    val dir = Files.createTempDirectory(workroot, s"$prefix-")
    try body(dir)
    finally _delete_tree(dir)
  }

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _write_runtime_jar(
    path: Path,
    runtime: String,
    modulecoordinate: String,
    version: Option[String]
  ): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val out = new ZipOutputStream(Files.newOutputStream(path))
    try {
      out.putNextEntry(new ZipEntry("META-INF/cncf/runtime.yaml"))
      val versionline = version.map(x => s"version: $x\n").getOrElse("")
      out.write(
        s"""schemaVersion: 1
           |runtime: $runtime
           |${versionline}module: $modulecoordinate
           |""".stripMargin.getBytes(StandardCharsets.UTF_8)
      )
      out.closeEntry()
    } finally {
      out.close()
    }
    path
  }

  private def _write_generation_provenance(
    dir: Path,
    cncfversion: String,
    cozyversion: String = org.simplemodeling.cozy.BuildInfo.version,
    outputversion: String = "0.0.1-SNAPSHOT"
  ): Path = {
    _write(
      dir.resolve("src/main/car/abi-manifest.json"),
      """{
        |  "format": "cozy.car.abi-manifest.v2",
        |  "component": {"namespace":"org.example","id":"Sample","version":"%s"},
        |  "abi": {
        |    "version": 1,
        |    "exports": {
        |      "components": [
        |        {
        |          "namespace": "org.example",
        |          "id": "Sample"
        |        }
        |      ],
        |      "operations": [],
        |      "entities": []
        |    },
        |    "dependencies": []
        |  }
        |}
        |""".stripMargin.format(outputversion)
    )
    val source = _write(
      dir.resolve("src/main/cozy/sample.cml"),
      "component Sample\n"
    )
    _write(
      dir.resolve("target/scala-3.3.8/src_managed/main/domain/Sample.scala"),
      "package domain\nfinal class Sample\n"
    )
    val snapshot =
      GenerationProvenance.requireSourceSnapshot(
        sourcePath = source,
        sourceIdentity = "src/main/cozy/sample.cml"
      )
    GenerationProvenance.write(
      outputRoot = dir,
      sourceSnapshot = snapshot,
      inputs = GenerationProvenance.Inputs(
        cncfTargetVersion = cncfversion,
        runtimeDescriptorSha256 = "a" * 64,
        cozyGeneratorVersion = cozyversion,
        simpleModelerBackendVersion = "0.2.0",
        simpleModelingModelVersion = "0.2.0",
        sourceIdentity = snapshot.identity,
        sourceSha256 = snapshot.sha256
      )
    )
    dir.resolve(GenerationProvenance.METADATA_PATH)
  }

  private def _zip_entries(path: Path): Vector[String] = {
    val zip = new ZipFile(path.toFile)
    try zip.entries().asScala.map(_.getName).toVector
    finally zip.close()
  }

  private def _zip_text(path: Path, entryname: String): String = {
    new String(_zip_bytes(path, entryname), StandardCharsets.UTF_8)
  }

  private def _zip_bytes(path: Path, entryname: String): Array[Byte] = {
    val zip = new ZipFile(path.toFile)
    try {
      val entry = Option(zip.getEntry(entryname)).getOrElse {
        fail(s"missing ZIP entry: $entryname")
      }
      val input = zip.getInputStream(entry)
      try input.readAllBytes()
      finally input.close()
    } finally {
      zip.close()
    }
  }

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).
      map(byte => f"${byte & 0xff}%02x").mkString

  private def _generation_provenance_snapshots: Set[Path] = {
    val temporary = Path.of(System.getProperty("java.io.tmpdir"))
    val stream = Files.list(temporary)
    try {
      stream.iterator().asScala.
        filter(path =>
          path.getFileName.toString.startsWith("generation-provenance") &&
            path.getFileName.toString.endsWith(".json")
        ).
        toSet
    } finally {
      stream.close()
    }
  }

  private def _delete_tree(path: Path): Unit = {
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.sorted(Comparator.reverseOrder()).iterator().asScala.foreach(Files.deleteIfExists(_))
      } finally {
        stream.close()
      }
    }
  }
}
