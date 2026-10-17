package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Comparator
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}
import scala.collection.JavaConverters._
import cozy.compatibility.CarMetadataCompatibility
import cozy.config.CozyProjectYamlConfig
import cozy.modeler.GenerationProvenance
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, Json}

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 */
final class CozySourceBuildPackagingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _cncf_version = "0.5.3-SNAPSHOT"
  private val _cozy_version = "0.3.3-SNAPSHOT"
  private val _source_arguments = List(
    "--source-cncf-version", _cncf_version, "--source-cozy-version", _cozy_version
  )

  "Explicit Snapshot source CAR packaging" should {
    "admit actual package inputs" which {
      "packages matching native evidence and preserves canonical runtime, identity and generation inputs" in {
        _with_fixture { root =>
          Given("canonical project metadata, an explicitly tested source runtime JAR and matching generation provenance")
          val fixture = _fixture(root)
          val provenance = _provenance(root)
          val sourcebytes = Files.readAllBytes(fixture.project).toVector
          val provenancebytes = Files.readAllBytes(provenance).toVector
          When("the production package entry receives the explicit Snapshot pair")
          CozyArchivePackager.buildCar(fixture.arguments ++ _source_arguments)
          val projected = CozySourceBuildPackaging.project(
            CozyProjectYamlConfig.loadProjectMetadata(root), Some(_cncf_version), Some(_cozy_version)
          )
          val contract = CarMetadataCompatibility.requireValidCarProject(projected, Vector(
            CarMetadataCompatibility.ResolvedCncfArtifact(
              fixture.runtimejar.toString, Some("cncf"), Some(s"org.goldenport:goldenport-cncf_3:${_cncf_version}"), Some(_cncf_version)
            )
          ))
          Then("the CAR records the authored runtime contract and canonical identity without rewriting the project")
          val manifest = Json.parse(_zip_text(fixture.archive, "car-runtime-manifest.json"))
          (manifest \ "runtime" \ "cncf" \ "minimum").as[String] shouldBe "0.5.2"
          (manifest \ "runtime" \ "cncf" \ "maximum").as[String] shouldBe "0.6.0"
          (manifest \ "runtime" \ "cncf" \ "tested").as[Vector[String]] shouldBe Vector("0.5.2", _cncf_version)
          (manifest \ "runtime" \ "cncf" \ "excluded").as[Vector[String]] shouldBe Vector("0.5.4")
          (manifest \ "car" \ "version").as[String] shouldBe "0.1.1-SNAPSHOT"
          val descriptor = Json.parse(_zip_text(fixture.archive, "component-descriptor.json"))
          (descriptor \ "component" \ "namespace").as[String] shouldBe "org.example"
          (descriptor \ "component" \ "id").as[String] shouldBe "SourceFixture"
          val packagedprovenance = _zip_text(fixture.archive, "generation-provenance.json")
          packagedprovenance.getBytes(StandardCharsets.UTF_8).toVector shouldBe provenancebytes
          GenerationProvenance.requireValidPackagedMetadata(provenance, _cncf_version, _cozy_version)
          Files.readAllBytes(fixture.project).toVector shouldBe sourcebytes
          _zip_bytes(fixture.archive, "lib/native.jar").toVector shouldBe Files.readAllBytes(fixture.runtimejar).toVector
          And("the accepted generation contract names the selected inputs while runtime policy stays independent")
          contract.cncfCompileTarget.version shouldBe _cncf_version
          contract.cozyVersion shouldBe _cozy_version
        }
      }

      "retains ordinary canonical packaging when neither source option is supplied" in {
        _with_fixture { root =>
          Given("canonical published compile inputs and their matching native runtime JAR")
          val fixture = _fixture(root, runtimeversion = "0.5.2")
          val original = Files.readAllBytes(fixture.project).toVector
          val metadata = CozyProjectYamlConfig.loadProjectMetadata(root)
          When("the production package entry receives no source flags")
          CozyArchivePackager.buildCar(fixture.arguments)
          Then("ordinary package admission succeeds and canonical metadata is unchanged")
          Files.isRegularFile(fixture.archive) shouldBe true
          CozySourceBuildPackaging.project(metadata, None, None) shouldBe metadata
          Files.readAllBytes(fixture.project).toVector shouldBe original
          (Json.parse(_zip_text(fixture.archive, "car-runtime-manifest.json")) \ "runtime" \ "cncf" \ "tested")
            .as[Vector[String]] shouldBe Vector("0.5.2", _cncf_version)
        }
      }
    }

    "reject invalid source admission before archive output" which {
      "rejects partial, blank and release version pairs and release component identities" in {
        val requests = Vector(
          List("--source-cncf-version", _cncf_version),
          List("--source-cozy-version", _cozy_version),
          List("--source-cncf-version", "", "--source-cozy-version", _cozy_version),
          List("--source-cncf-version", _cncf_version, "--source-cozy-version", " "),
          List("--source-cncf-version", " ", "--source-cozy-version", " "),
          List("--source-cncf-version", "0.5.3", "--source-cozy-version", _cozy_version),
          List("--source-cncf-version", _cncf_version, "--source-cozy-version", "0.3.3")
        )
        requests.foreach { request =>
          _with_fixture { root =>
            Given("a canonical Snapshot component with a partial, blank or release source pair")
            val fixture = _fixture(root)
            When("the production entry admits the invalid source options")
            val failure = intercept[Throwable] { CozyArchivePackager.buildCar(fixture.arguments ++ request) }
            Then("source input admission fails before an archive exists")
            failure.getMessage should include("Source CAR packaging requires")
            Files.exists(fixture.archive) shouldBe false
          }
        }
        _with_fixture { root =>
          Given("a canonical release component with a complete Snapshot source pair")
          val fixture = _fixture(root, componentversion = "0.1.1")
          When("the production entry admits the source build")
          val failure = intercept[Throwable] { CozyArchivePackager.buildCar(fixture.arguments ++ _source_arguments) }
          Then("the release component cannot take the development-only projection")
          failure.getMessage should include("Snapshot component")
          Files.exists(fixture.archive) shouldBe false
        }
      }

      "requires exactly one matching native CNCF descriptor despite source flags" in {
        val cases = Vector("missing", "multiple", "version", "root-version", "organization", "artifact", "runtime")
        cases.foreach { defect =>
          _with_fixture { root =>
            Given(s"an explicitly selected source pair with $defect native runtime evidence")
            val fixture = _fixture(root)
            val arguments = defect match {
              case "missing" => fixture.arguments.dropRight(2)
              case "multiple" =>
                val other = _jar(root.resolve("target/other-native.jar"), "META-INF/cncf/runtime.yaml", _runtime_descriptor())
                fixture.arguments.updated(fixture.arguments.size - 1, s"${fixture.runtimejar},$other")
              case "version" =>
                _jar(fixture.runtimejar, "META-INF/cncf/runtime.yaml", _runtime_descriptor(version = "0.5.9-SNAPSHOT", rootversion = "0.5.9-SNAPSHOT"))
                fixture.arguments
              case "root-version" =>
                _jar(fixture.runtimejar, "META-INF/cncf/runtime.yaml", _runtime_descriptor(rootversion = "0.5.9-SNAPSHOT"))
                fixture.arguments
              case "organization" =>
                _jar(fixture.runtimejar, "META-INF/cncf/runtime.yaml", _runtime_descriptor(organization = "org.other"))
                fixture.arguments
              case "artifact" =>
                _jar(fixture.runtimejar, "META-INF/cncf/runtime.yaml", _runtime_descriptor(artifact = "other-runtime"))
                fixture.arguments
              case "runtime" =>
                _jar(fixture.runtimejar, "META-INF/cncf/runtime.yaml", _runtime_descriptor(runtime = "other"))
                fixture.arguments
            }
            When("the production entry evaluates resolved native evidence")
            val failure = intercept[Throwable] { CozyArchivePackager.buildCar(arguments ++ _source_arguments) }
            Then("the existing descriptor identity, version and multiplicity gates reject the CAR")
            failure.getMessage should include("CAR_METADATA_RESOLVED_CNCF")
            Files.exists(fixture.archive) shouldBe false
          }
        }
      }

      "requires independently authored tested, minimum, maximum and exclusion support" in {
        val policies = Vector(
          _project_json(tested = Vector("0.5.2")),
          _project_json(minimum = "0.5.4"),
          _project_json(maximum = "0.5.2"),
          _project_json(excluded = Vector(_cncf_version))
        )
        policies.foreach { policy =>
          _with_fixture { root =>
            Given("a valid native source artifact outside one independently authored runtime policy dimension")
            val fixture = _fixture(root)
            _write(fixture.project, Json.stringify(policy))
            When("the production entry admits the explicit source pair")
            val failure = intercept[Throwable] { CozyArchivePackager.buildCar(fixture.arguments ++ _source_arguments) }
            Then("the source pair cannot manufacture runtime support and no archive exists")
            failure.getMessage should include("CAR_METADATA_")
            Files.exists(fixture.archive) shouldBe false
            Files.readString(fixture.project, StandardCharsets.UTF_8) shouldBe Json.stringify(policy)
          }
        }
      }

      "rejects missing and multiple canonical CNCF compile coordinates" in {
        Vector(Vector("org.example:other:1.0"), Vector(
          "org.goldenport::goldenport-cncf:0.5.2", "org.goldenport:goldenport-cncf_3:0.5.2"
        )).foreach { coordinates =>
          _with_fixture { root =>
            Given("canonical metadata without exactly one CNCF compile coordinate")
            val fixture = _fixture(root)
            val project = _project_json(compile = coordinates)
            _write(fixture.project, Json.stringify(project))
            When("the production entry projects the source pair")
            val failure = intercept[Throwable] { CozyArchivePackager.buildCar(fixture.arguments ++ _source_arguments) }
            Then("the missing or ambiguous compile authority fails before archive output")
            failure.getMessage should include("exactly one canonical CNCF compile coordinate")
            Files.exists(fixture.archive) shouldBe false
          }
        }
      }

      "keeps existing provenance validation mandatory with explicit source flags" in {
        _with_fixture { root =>
          Given("valid native source evidence but provenance for a different generator input")
          val fixture = _fixture(root)
          _provenance(root, cozyversion = "0.3.2-SNAPSHOT")
          When("the production entry validates generation evidence after source projection")
          val failure = intercept[Throwable] { CozyArchivePackager.buildCar(fixture.arguments ++ _source_arguments) }
          Then("the existing provenance gate rejects mismatched generation input before archive output")
          failure.getMessage should include("GENERATION_PROVENANCE_INPUT_MISMATCH")
          Files.exists(fixture.archive) shouldBe false
        }
      }
    }

    "project only explicit build inputs" which {
      "preserves unrelated metadata, coordinate spelling and all runtime policy across Snapshot pairs" in {
        Given("varying Snapshot versions, accepted colon notations and unrelated canonical metadata and dependencies")
        val inputs = for {
          cncfpatch <- Gen.choose(1, 1000)
          cozypatch <- Gen.choose(1, 1000)
          spelling <- Gen.oneOf("org.goldenport::goldenport-cncf:", "org.goldenport:goldenport-cncf:", "org.goldenport:goldenport-cncf_3:")
          unrelated <- Gen.alphaNumStr
        } yield (cncfpatch, cozypatch, spelling, unrelated)
        When("the explicit pair is projected onto each canonical metadata value")
        val result = Test.check(Test.Parameters.default, Prop.forAll(inputs) { case (cncfpatch, cozypatch, spelling, unrelated) =>
          val cncfversion = s"0.5.$cncfpatch-SNAPSHOT"
          val cozyversion = s"0.3.$cozypatch-SNAPSHOT"
          val coordinates = Vector("org.example::before:1.0", spelling + "0.5.2", "org.example:after:2.0")
          val originaljson = _project_json(compile = coordinates) ++ Json.obj("unrelated" -> unrelated)
          val original = CozyProjectYamlConfig.parsePublic(Json.stringify(originaljson).getBytes(StandardCharsets.UTF_8))
          val projected = CozySourceBuildPackaging.project(original, Some(cncfversion), Some(cozyversion))
          projected.values == original.values.updated("build.cozyVersion", cozyversion) &&
            projected.lists == original.lists.updated("build.dependencies.compile", coordinates.updated(1, spelling + cncfversion)) &&
            projected.authoredkeys == original.authoredkeys && projected.json == original.json
        })
        Then("only the exact compile version and generator value change; identity and runtime policy remain canonical")
        result.passed shouldBe true
      }
    }
  }

  private final case class Fixture(project: Path, archive: Path, runtimejar: Path, arguments: List[String])

  private def _fixture(
    root: Path,
    runtimeversion: String = "0.5.3-SNAPSHOT",
    componentversion: String = "0.1.1-SNAPSHOT"
  ): Fixture = {
    val project = _write(root.resolve("project.yaml"), Json.stringify(_project_json(componentversion = componentversion)))
    val mainjar = _jar(root.resolve("target/main.jar"), "main.txt", "compiled component")
    val runtimejar = _jar(root.resolve("target/native.jar"), "META-INF/cncf/runtime.yaml", _runtime_descriptor(version = runtimeversion, rootversion = runtimeversion))
    _write(root.resolve("src/main/car/abi-manifest.json"),
      s"""{"format":"cozy.car.abi-manifest.v2","component":{"namespace":"org.example","id":"SourceFixture","version":"$componentversion"},"abi":{"version":1,"exports":{"components":[{"namespace":"org.example","id":"SourceFixture"}],"operations":[],"entities":[]},"dependencies":[]}}""")
    val archive = root.resolve("target/source.car")
    Fixture(project, archive, runtimejar, List(
      "--save", archive.toString, "--main-jar", mainjar.toString, "--project-dir", root.toString,
      "--name", "example-source-fixture", "--version", componentversion, "--component", "SourceFixture",
      "--lib-jars", runtimejar.toString
    ))
  }

  private def _project_json(
    componentversion: String = "0.1.1-SNAPSHOT",
    minimum: String = "0.5.2",
    maximum: String = "0.6.0",
    tested: Vector[String] = Vector("0.5.2", "0.5.3-SNAPSHOT"),
    excluded: Vector[String] = Vector("0.5.4"),
    compile: Vector[String] = Vector("org.goldenport::goldenport-cncf:0.5.2", "org.example:other:1.0")
  ): JsObject = Json.obj(
    "project" -> Json.obj("namespace" -> "org.example", "id" -> "SourceFixture", "kind" -> "car",
      "name" -> "unrelated-human-name", "component" -> Json.obj("version" -> componentversion)),
    "build" -> Json.obj("cozyVersion" -> "0.3.2.2", "dependencies" -> Json.obj(
      "compile" -> compile, "test" -> Vector("org.example::testing:1.0"))),
    "packaging" -> Json.obj("kind" -> "car", "car" -> Json.obj(
      "include_dependencies" -> true, "source_dir" -> "src/main/car",
      "runtime" -> Json.obj("cncf" -> Json.obj("minimum" -> minimum, "maximum" -> maximum,
        "tested" -> tested, "excluded" -> excluded))))
  )

  private def _runtime_descriptor(
    version: String = "0.5.3-SNAPSHOT",
    rootversion: String = "0.5.3-SNAPSHOT",
    organization: String = "org.goldenport",
    artifact: String = "goldenport-cncf_3",
    runtime: String = "cncf"
  ): String = s"runtime: $runtime\nmodule: $organization:$artifact:$version\nversion: $rootversion\n"

  private def _provenance(root: Path, cozyversion: String = "0.3.3-SNAPSHOT"): Path = {
    val source = _write(root.resolve("src/main/cozy/sample.cml"), "component SourceFixture\n")
    _write(root.resolve("target/generated/fixture/Generated.scala"), "package fixture\nfinal class Generated\n")
    val sourceinput = GenerationProvenance.captureSourceInput(source, "src/main/cozy/sample.cml").fold(errors => fail(errors.toString), identity)
    GenerationProvenance.write(root, sourceinput, GenerationProvenance.Inputs(
      _cncf_version, cozyversion,
      "1.1.26-SNAPSHOT", "0.3.0-SNAPSHOT", sourceinput.identity
    ))
    root.resolve(GenerationProvenance.METADATA_PATH)
  }

  private def _write(path: Path, text: String): Path = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, text, StandardCharsets.UTF_8)
    path
  }

  private def _jar(path: Path, entry: String, content: String): Path = {
    Files.createDirectories(path.getParent)
    val output = new ZipOutputStream(Files.newOutputStream(path))
    try {
      output.putNextEntry(new ZipEntry(entry))
      output.write(content.getBytes(StandardCharsets.UTF_8))
      output.closeEntry()
    } finally output.close()
    path
  }

  private def _zip_text(path: Path, entry: String): String =
    new String(_zip_bytes(path, entry), StandardCharsets.UTF_8)

  private def _zip_bytes(path: Path, entry: String): Array[Byte] = {
    val zip = new ZipFile(path.toFile)
    try {
      val input = zip.getInputStream(zip.getEntry(entry))
      try input.readAllBytes()
      finally input.close()
    } finally zip.close()
  }

  private def _with_fixture[A](body: Path => A): A = {
    val work = Path.of("target/cozy-test/work/source-build-packaging").toAbsolutePath.normalize()
    Files.createDirectories(work)
    val root = Files.createTempDirectory(work, "admission-")
    try body(root)
    finally {
      val stream = Files.walk(root)
      try stream.sorted(Comparator.reverseOrder()).iterator().asScala.foreach(Files.deleteIfExists(_))
      finally stream.close()
    }
  }
}
