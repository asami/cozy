package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._
import cozy.Cozy
import cozy.CozyCapabilityProductionSpec._
import cozy.modeler.CmlCapabilityProductionRequest._

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyCapabilityArchiveSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Normal canonical Capability CAR input (spec:R13)" should {
    "select actual normal product inputs" which {
      "C64B09 copy exact produced canonical metadata with original descriptor, ABI and runtime resources" in {
        _with_root { root =>
          Given("actual normal Modeler products and a private CAR project with the existing canonical identity/runtime contract")
          val fixture = _fixture(root)
          val archive = root.resolve("selected.car")
          val sourceentries = Vector("index.json", "Store.json").map(name => name -> Files.readAllBytes(fixture.metadata.resolve(name)).toVector).toMap
          When("actual Cozy.main package-car selects the explicit Capability bundle and independent package domain")
          Cozy.main((_args(fixture, archive) ++ _flags(fixture)).toArray)
          val entries = _entries(archive)
          Then("only the produced canonical index and resource bytes join unchanged descriptor/ABI/provenance/runtime entries")
          entries.filter(_.startsWith(METADATA_PREFIX)).sorted shouldBe Vector(METADATA_PREFIX + "Store.json", METADATA_PREFIX + "index.json").sorted
          sourceentries.foreach { case (name, bytes) => _bytes(archive, METADATA_PREFIX + name) shouldBe bytes }
          entries should contain allOf("component/main.jar", "component-descriptor.json", "abi-manifest.json", "generation-provenance.json", CozyCarRuntimeManifest.FILE_NAME)
          (Json.parse(_bytes(archive, "component-descriptor.json").toArray) \ "schemaVersion").as[Int] shouldBe 3
          (Json.parse(_bytes(archive, "abi-manifest.json").toArray) \ "component").as[JsObject] shouldBe fixture.coordinate.componentJson
        }
      }
      "C64B10 reject equal-content and unexpected canonical source-CAR collisions before destination creation" in {
        _with_root { root =>
          Given("actual produced metadata and source-CAR index, selected resource and unlisted canonical JSON collisions")
          val fixture = _fixture(root)
          val collisions = Vector("index.json", "Store.json", "unexpected.json", "nested/extra.json")
          val cardirs = collisions.zipWithIndex.map { case (name, index) =>
            val directory = root.resolve("car" + index)
            val text = if (name == "index.json" || name == "Store.json") Files.readString(fixture.metadata.resolve(name)) else "{}"
            _write(directory.resolve(METADATA_PREFIX + name), text)
            directory
          }
          When("actual normal package-car composes each source inventory with the selected canonical entries")
          val results = cardirs.zipWithIndex.map { case (directory, index) =>
            val archive = root.resolve("collision" + index + ".car")
            val error = intercept[Throwable](Cozy.main((_args(fixture, archive) ++ List("--car-dir", directory.toString) ++ _flags(fixture)).toArray))
            error -> archive
          }
          Then("all canonical-prefix JSON/name collisions fail even for identical bytes before ZIP creation")
          results.foreach { case (error, archive) =>
            error.getMessage should include("Capability")
            Files.exists(archive) shouldBe false
          }
          Files.readAllBytes(cardirs.head.resolve(METADATA_PREFIX + "index.json")).toVector shouldBe Files.readAllBytes(fixture.metadata.resolve("index.json")).toVector
        }
      }
      "C64B11 reject selected release, package, schema, ABI and malformed inventory disagreements" in {
        _with_root { root =>
          Given("actual generated metadata and private envelope/index/inventory mismatch variants")
          val fixture = _fixture(root)
          val resource = fixture.metadata.resolve("Store.json")
          val original = _json(resource)
          val variants = Vector(original + ("schemaVersion" -> JsNumber(2)), original + ("generatedAbiVersion" -> JsString("other.abi")),
            original + ("component" -> (fixture.coordinate.componentJson + ("version" -> JsString("1.2.4-SNAPSHOT")))),
            original + ("component" -> (fixture.coordinate.componentJson + ("id" -> JsString("Other")))),
            original + ("package" -> Json.obj("coordinate" -> fixture.bundle.packagecoordinate.coordinate, "version" -> "other:release")),
            original + ("package" -> Json.obj("coordinate" -> "other:coordinate", "version" -> fixture.bundle.packagecoordinate.version)),
            original + ("unexpected" -> JsNull), original + ("producer" -> Json.obj("id" -> "other", "version" -> "literal:version")))
          When("actual package-car evaluates each envelope, duplicate-key index and unexpected nested inventory")
          val envelopeerrors = variants.zipWithIndex.map { case (json, index) =>
            _write(resource, Json.stringify(json))
            val archive = root.resolve("mismatch" + index + ".car")
            intercept[Throwable](Cozy.main((_args(fixture, archive) ++ _flags(fixture)).toArray)) -> archive
          }
          _write(resource, Json.stringify(original) + "\n")
          val indexpath = fixture.metadata.resolve("index.json")
          val originalindex = Files.readString(indexpath)
          _write(indexpath, "{\"schemaVersion\":1,\"schemaVersion\":1,\"projections\":[\"Store.json\"]}")
          val duplicatearchive = root.resolve("duplicate.car")
          val duplicate = intercept[Throwable](Cozy.main((_args(fixture, duplicatearchive) ++ _flags(fixture)).toArray))
          _write(indexpath, originalindex)
          _write(fixture.metadata.resolve("nested/unlisted.json"), "{}")
          val nestedarchive = root.resolve("nested.car")
          val nested = intercept[Throwable](Cozy.main((_args(fixture, nestedarchive) ++ _flags(fixture)).toArray))
          Then("all selected contract and inventory failures preserve the destination absence")
          (envelopeerrors ++ Vector(duplicate -> duplicatearchive, nested -> nestedarchive)).foreach { case (error, archive) =>
            Option(error.getMessage) should not be empty
            Files.exists(archive) shouldBe false
          }
        }
      }
      "C64B12 require three literal flags together and preserve absent-flag normal packaging" in {
        _with_root { root =>
          Given("actual ordinary CAR inputs and partial, duplicate or missing Capability option values")
          val fixture = _fixture(root)
          val invalid = Vector(List("--capability-dir", fixture.project.resolve(fixture.bundle.path).toString),
            _flags(fixture) ++ List("--capability-dir=" + fixture.project.resolve(fixture.bundle.path)),
            List("--capability-dir"), _flags(fixture).dropRight(1),
            List("--capability-dir= padded", "--capability-package-coordinate=pkg", "--capability-package-version=v"))
          When("actual package-car runs without opt-in and tries each invalid flag set")
          val ordinary = root.resolve("ordinary.car")
          Cozy.main(_args(fixture, ordinary).toArray)
          val failures = invalid.zipWithIndex.map { case (flags, index) =>
            val archive = root.resolve("invalid" + index + ".car")
            intercept[Throwable](Cozy.main((_args(fixture, archive) ++ flags).toArray)) -> archive
          }
          Then("the old normal path produces its canonical CAR without added resources and invalid selections produce no ZIP")
          _entries(ordinary).filter(_.startsWith(METADATA_PREFIX)) shouldBe empty
          _entries(ordinary) should contain("component-descriptor.json")
          failures.foreach { case (_, archive) => Files.exists(archive) shouldBe false }
        }
      }
    }
  }

  private final case class Fixture(project: Path, bundle: PackageOutput, coordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    metadata: Path, mainjar: Path, runtimejar: Path)
  private def _fixture(root: Path): Fixture = {
    _copy_sources(root)
    val request = _request()
    Cozy.main(_normal_args(root, request, "modeler-scala", "project"))
    val project = root.resolve("project")
    _write(project.resolve(_primary_source.path), _primary_text)
    val bundle = request.packages.head
    val coordinate = CozyComponentReleaseCoordinateCodec.admit("org.simplemodeling.textus", "Store", bundle.component.version, "spec:R13")
    _write(project.resolve("project.yaml"), s"""project:
      |  namespace: ${coordinate.namespace}
      |  id: ${coordinate.id}
      |  name: capability-product
      |  kind: car
      |  component:
      |    version: ${coordinate.version}
      |build:
      |  cozyVersion: ${org.simplemodeling.cozy.BuildInfo.version}
      |  dependencies:
      |    compile:
      |      - org.goldenport::goldenport-cncf:0.5.2-SNAPSHOT
      |packaging:
      |  kind: car
      |  car:
      |    include_dependencies: true
      |    runtime:
      |      cncf:
      |        minimum: 0.5.2-SNAPSHOT
      |        tested:
      |          - 0.5.2-SNAPSHOT
      |""".stripMargin)
    val abi = Json.obj("format" -> "cozy.car.abi-manifest.v2", "component" -> coordinate.componentJson,
      "abi" -> Json.obj("version" -> 1, "exports" -> Json.obj("components" -> JsArray(Vector(Json.obj("namespace" -> coordinate.namespace, "id" -> coordinate.id))),
        "operations" -> JsArray(), "entities" -> JsArray()), "dependencies" -> JsArray()))
    _write(project.resolve("src/main/car/abi-manifest.json"), Json.stringify(abi))
    val mainjar = _jar(root.resolve("artifacts/main.jar"), "fixture.txt", "actual CAR input")
    val runtimejar = _jar(root.resolve("artifacts/goldenport-cncf_3.jar"), "META-INF/cncf/runtime.yaml",
      "schemaVersion: 1\nruntime: cncf\nversion: 0.5.2-SNAPSHOT\nmodule: org.goldenport:goldenport-cncf_3:0.5.2-SNAPSHOT\n")
    Fixture(project, bundle, coordinate, project.resolve(bundle.path + "/meta/component-capability"), mainjar, runtimejar)
  }
  private def _args(fixture: Fixture, archive: Path): List[String] = List("package-car", "--save", archive.toString, "--project-dir", fixture.project.toString,
    "--main-jar", fixture.mainjar.toString, "--lib-jars", fixture.runtimejar.toString, "--name", fixture.coordinate.mavenArtifactId,
    "--version", fixture.coordinate.version, "--component", fixture.coordinate.qualifiedId)
  private def _flags(fixture: Fixture): List[String] = List("--capability-dir", fixture.project.resolve(fixture.bundle.path).toString,
    "--capability-package-coordinate", fixture.bundle.packagecoordinate.coordinate, "--capability-package-version", fixture.bundle.packagecoordinate.version)
  private def _jar(path: Path, name: String, text: String): Path = {
    Files.createDirectories(path.getParent)
    val stream = new ZipOutputStream(Files.newOutputStream(path))
    try { stream.putNextEntry(new ZipEntry(name)); stream.write(text.getBytes(StandardCharsets.UTF_8)); stream.closeEntry() }
    finally stream.close()
    path
  }
  private def _entries(path: Path): Vector[String] = {
    val zip = new ZipFile(path.toFile)
    try zip.entries().asScala.map(_.getName).toVector finally zip.close()
  }
  private def _bytes(path: Path, name: String): Vector[Byte] = {
    val zip = new ZipFile(path.toFile)
    try {
      val stream = zip.getInputStream(zip.getEntry(name))
      try stream.readAllBytes().toVector finally stream.close()
    } finally zip.close()
  }
}
