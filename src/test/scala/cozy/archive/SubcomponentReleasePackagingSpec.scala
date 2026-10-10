package cozy.archive

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

import scala.collection.JavaConverters._

import cozy.Cozy
import org.scalacheck.{Gen, Prop, Test}
import play.api.libs.json.{JsObject, Json}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 20, 2026
 *  version Aug. 21, 2026
 * @version Oct. 10, 2026
 * @author  ASAMI, Tomoharu
 */
object SubcomponentReleasePackagingSpec {
  private final case class Fixture(
    parentproject: Path,
    parentcar: Path,
    childcars: Vector[Path],
    composition: Path
  )
}

final class SubcomponentReleasePackagingSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  import SubcomponentReleasePackagingSpec.Fixture

  "Subcomponent release" should {
    "package and publish semantic parent/child delivery" which {
      "reject package source aliases and a symbolic canonical publication leaf" in {
        Vector("hard", "symbolic").foreach { kind =>
          _with_temp_dir(s"rsc03-output-ownership-$kind") { root =>
            Given(s"a complete release fixture with a $kind package output alias to its parent CAR")
            val fixture = _fixture(root)
            val output = root.resolve("selected-release.car")
            if (kind == "hard") Files.createLink(output, fixture.parentcar)
            else Files.createSymbolicLink(output, fixture.parentcar)

            When("the package command admits the selected output")
            val error = intercept[Throwable] {
              Cozy.main(_package_arguments(fixture, output, childcars = fixture.childcars))
            }

            Then("the package output is rejected with its selected path and ownership diagnostic")
            error.getMessage should include("package-subcomponent-release output")
            error.getMessage should include(output.toString)
            if (kind == "hard") error.getMessage should include("selected admitted inputs")
            else error.getMessage should include("symbolic link or nonregular leaf")
          }
        }

        _with_temp_dir("rsc03-publish-output-ownership") { root =>
          Given("a complete fixture with an ordinary selected release destination")
          val fixture = _fixture(root)
          val release = root.resolve("selected-release.car")
          When("the package command creates the selected release")
          Cozy.main(_package_arguments(fixture, release, childcars = fixture.childcars))
          Then("the release is available for publication")
          Files.isRegularFile(release) shouldBe true

          Given("a canonical publication destination linked to that selected release")
          val warehouse = root.resolve("warehouse")
          val coordinate = CozyComponentReleaseCoordinateCodec.admit(_namespace, "RscParent", _version, "release fixture")
          val destination = warehouse.resolve("repository/subcomponent-release").resolve(coordinate.groupPath).
            resolve(coordinate.mavenArtifactId).resolve(coordinate.version).
            resolve(s"${coordinate.mavenArtifactId}-${coordinate.version}.release.car")
          Files.createDirectories(destination.getParent)
          Files.createSymbolicLink(destination, release)

          When("the publish command admits its canonical destination")
          val error = intercept[Throwable] {
            Cozy.main(_publish_arguments(fixture, release, warehouse))
          }

          Then("publication rejects the symbolic output with its selected path diagnostic")
          error.getMessage should include("publish-subcomponent-release output")
          error.getMessage should include("symbolic link or nonregular leaf")
          error.getMessage should include(destination.toString)
        }
      }

      "RSC03-AC-01 deterministic parent/child CAR and payload packaging" in {
        _with_temp_dir("rsc03-ac01") { root =>
          Given("a complete parent CAR and independently identified Documentation, SourceCode, and external-platform presentation CARs")
          val fixture = _fixture(root)
          val first = root.resolve("package-first.car")
          val second = root.resolve("package-second.car")

          When("the public package-subcomponent-release command is invoked twice with the same complete release")
          Cozy.main(_package_arguments(fixture, first, childcars = fixture.childcars))
          Cozy.main(_package_arguments(fixture, second, childcars = fixture.childcars))

          Then("both release CARs contain equivalent canonical metadata and actual payloads")
          Files.isRegularFile(first) shouldBe true
          Files.isRegularFile(second) shouldBe true
          val expectedbytes = Files.readAllBytes(first).toVector
          expectedbytes shouldBe Files.readAllBytes(second).toVector
          val manifest = Json.parse(_zip_entry_text(first, "release-manifest.json")).as[JsObject]
          manifest.keys shouldBe Set("schemaVersion", "publishedAt", "parent", "children", "composition")
          (manifest \ "schemaVersion").as[String] shouldBe "cozy.subcomponent-release.v1"
          (manifest \ "publishedAt").as[String] shouldBe _published_at
          (manifest \ "parent").as[JsObject].keys shouldBe Set("archivePath", "canonicalId", "coordinate")
          (manifest \ "parent" \ "canonicalId").as[String] shouldBe _parent_id
          (manifest \ "children").as[Vector[JsObject]].foreach { child =>
            child.keys shouldBe Set("archivePath", "canonicalId", "coordinate")
          }
          (manifest \ "children").as[Vector[JsObject]].map(child => (child \ "canonicalId").as[String]) shouldBe
            Vector(_documentation_id, _source_code_id, _web_presentation_id)
          (manifest \ "composition").as[JsObject] shouldBe Json.obj(
            "archivePath" -> "composition.json",
            "schema" -> _composition_schema,
            "membershipKind" -> "parent-child"
          )
          val copiedcomposition = _zip_entry_text(first, "composition.json")
          copiedcomposition shouldBe Files.readString(fixture.composition, StandardCharsets.UTF_8)
          val packaged = _zip_texts(first).mkString("\n")
          packaged should include(_composition_schema)
          packaged should include(_documentation_role)
          packaged should include(_source_code_role)
          packaged should include(_web_presentation_role)
          packaged should include("\"fixtureExtension\":{\"accepted\":true}")
          packaged should include(_documentation_payload_marker)
          packaged should include(_source_code_payload_marker)
          packaged should include(_web_presentation_payload_marker)
          val expectedpaths = Vector(
            "composition.json",
            s"payload/children/${_documentation_id}.car",
            s"payload/children/${_source_code_id}.car",
            s"payload/children/${_web_presentation_id}.car",
            "payload/parent.car",
            "release-manifest.json"
          )
          _zip_entry_names(first) shouldBe expectedpaths

          Given("generated permutations of all three actual child CAR inputs")
          val permutations = Gen.oneOf(fixture.childcars.permutations.toVector)
          val permuted = root.resolve("package-permuted.car")
          When("fifty generated child-input permutations are packaged through the public command")
          val result = Test.check(
            Test.Parameters.default.withMinSuccessfulTests(50),
            Prop.forAll(permutations) { childcars =>
              Cozy.main(_package_arguments(fixture, permuted, childcars = childcars))
              val contents = _zip_texts(permuted).mkString("\n")
              Files.readAllBytes(permuted).toVector == expectedbytes &&
                _zip_entry_names(permuted) == expectedpaths &&
                contents.contains(_documentation_payload_marker) &&
                contents.contains(_source_code_payload_marker) &&
                contents.contains(_web_presentation_payload_marker)
            }
          )
          Then("every permutation preserves canonical ZIP order and the actual parent/child payload content")
          result.passed shouldBe true
        }
      }

      "RSC03-AC-02 incomplete or invalid required membership is rejected" in {
        _with_temp_dir("rsc03-ac02") { root =>
          Given("a complete release fixture and two incomplete or invalid required-membership candidates")
          val fixture = _fixture(root)
          val candidates = Vector(
            ("incomplete", Vector(fixture.childcars(0)), Vector(_documentation_id, _source_code_id)),
            ("invalid", fixture.childcars, Vector(_documentation_id, "org.goldenport.cncf.phase58.RscMissing"))
          )
          candidates.foreach { case (label, childcars, requiredchildren) =>
            val output = root.resolve(s"$label.car")
            When(s"the public package-subcomponent-release command evaluates $label required membership")
            val error = intercept[Throwable] {
              Cozy.main(_package_arguments(fixture, output, requiredchildren, childcars))
            }
            Then("the candidate is rejected with a required-membership diagnostic")
            error.getMessage should include("required")
          }
        }
      }

      "RSC03-AC-04 duplicate logical resources and JSON object fields are rejected at package time" in {
        _with_temp_dir("rsc03-ac04") { root =>
          Given("a valid RSC-02 composition and hostile variants with a duplicate logical resource or duplicate schema field")
          val fixture = _fixture(root)
          val hostile = _hostile_compositions(root, fixture)
          hostile.foreach { case (label, compositionpath) =>
            val output = root.resolve(s"$label.car")
            When(s"the public package-subcomponent-release command evaluates $label composition")
            val error = intercept[Throwable] {
              Cozy.main(_package_arguments(fixture, output, childcars = fixture.childcars, compositionpath = Some(compositionpath)))
            }
            Then("the hostile composition is rejected with its resource or JSON diagnostic")
            error.getMessage should not be empty
          }
        }
      }

      "RSC03-AC-05 hostile composition archives are rejected during publication" in {
        _with_temp_dir("rsc03-ac05") { root =>
          Given("a complete release fixture and hostile compositions")
          val fixture = _fixture(root)
          val validrelease = root.resolve("valid-release.car")
          val compositions = _hostile_compositions(root, fixture)
          When("the valid release is packaged and composition.json is replaced by each hostile variant")
          Cozy.main(_package_arguments(fixture, validrelease, childcars = fixture.childcars))
          val hostile = compositions.map { case (label, compositionpath) =>
            label -> _replace_zip_entry(validrelease, root.resolve(s"$label-release.car"), "composition.json", Files.readString(compositionpath))
          }
          Then("the generated release contains the original valid composition")
          _zip_entry_text(validrelease, "composition.json") shouldBe Files.readString(fixture.composition)
          hostile.foreach { case (label, release) =>
            Given(s"the $label composition variant in the selected release archive")
            val warehouse = root.resolve(s"$label-warehouse")
            When("publish-subcomponent-release parses the selected hostile archive")
            val error = intercept[Throwable] {
              Cozy.main(_publish_arguments(fixture, release, warehouse))
            }
            Then("the hostile composition is rejected with a parsing or resource diagnostic")
            error.getMessage should not be empty
          }
        }
      }

      "RSC03-AC-04 duplicate release-manifest fields are rejected during publication" in {
        _with_temp_dir("rsc03-ac04-release-manifest") { root =>
          Given("a complete release fixture")
          val fixture = _fixture(root)
          val release = root.resolve("valid-release.car")
          val warehouse = root.resolve("warehouse")
          When("the valid release is packaged")
          Cozy.main(_package_arguments(fixture, release, childcars = fixture.childcars))
          Then("the generated release manifest identifies its actual schema")
          _zip_entry_text(release, "release-manifest.json") should include("cozy.subcomponent-release.v1")
          Given("a selected release manifest that repeats its schemaVersion field")
          val hostile = _replace_zip_entry(
            release,
            root.resolve("duplicate-release-manifest.car"),
            "release-manifest.json",
            _duplicate_json_field(_zip_entry_text(release, "release-manifest.json"), "schemaVersion")
          )
          When("publish-subcomponent-release receives the duplicate-field release manifest")
          val error = intercept[Throwable] {
            Cozy.main(_publish_arguments(fixture, hostile, warehouse))
          }
          Then("it rejects the manifest with the duplicate-field diagnostic")
          error.getMessage should include("contains duplicate JSON object field")
        }
      }

      "RSC03-AC-02 an omitted optional composition member remains metadata-only while all required members are packaged" in {
        _with_temp_dir("rsc03-ac02-optional") { root =>
          Given("a canonical RSC-02 composition with two required child CARs and one optional external-platform member")
          val fixture = _fixture(root)
          val release = root.resolve("optional-omitted.car")
          When("the public package-subcomponent-release command receives exactly the required child CARs")
          Cozy.main(_package_arguments(fixture, release, childcars = fixture.childcars.take(2)))
          Then("the optional member remains in copied composition metadata but is absent from the payload archive")
          Files.isRegularFile(release) shouldBe true
          val packaged = _zip_texts(release).mkString("\n")
          packaged should include(_web_presentation_id)
          packaged should include(_web_presentation_role)
          packaged should not include (_web_presentation_payload_marker)
        }
      }

      "RSC03-AC-03 local/remote-root publication and repeated-build payloads remain equivalent" in {
        _with_temp_dir("rsc03-ac03") { root =>
          Given("a complete release fixture and two isolated local warehouse roots standing in for local and remote publication")
          val fixture = _fixture(root)
          val first = root.resolve("release-first.car")
          val second = root.resolve("release-second.car")
          val localwarehouse = root.resolve("local-warehouse")
          val remotewarehouse = root.resolve("remote-warehouse")
          When("the release is packaged repeatedly and copied through each isolated warehouse boundary")
          Cozy.main(_package_arguments(fixture, first, childcars = fixture.childcars))
          Cozy.main(_package_arguments(fixture, second, childcars = fixture.childcars))
          Cozy.main(_publish_arguments(fixture, first, localwarehouse))
          Cozy.main(_publish_arguments(fixture, second, remotewarehouse))
          Then("repeated package bytes and local/remote-root publication bytes and actual payloads remain equivalent")
          Files.readAllBytes(first).toVector shouldBe Files.readAllBytes(second).toVector
          val localbytes = _tree_bytes(localwarehouse)
          val remotebytes = _tree_bytes(remotewarehouse)
          localbytes should not be empty
          localbytes shouldBe remotebytes
          localbytes.values.toVector should contain(Files.readAllBytes(first).toVector)
          val published = _zip_texts(localbytes.values.head.toArray).mkString("\n")
          published should include(_documentation_payload_marker)
          published should include(_source_code_payload_marker)
          published should include(_web_presentation_payload_marker)
        }
      }
    }
  }

  private val _namespace = "org.goldenport.cncf.phase58"
  private val _version = "0.1.0-SNAPSHOT"
  private val _parent_id = s"${_namespace}.RscParent"
  private val _documentation_id = s"${_namespace}.RscDocumentation"
  private val _source_code_id = s"${_namespace}.RscSourceCode"
  private val _web_presentation_id = s"${_namespace}.RscWebPresentation"
  private val _documentation_payload_marker = "rsc03-documentation-payload"
  private val _source_code_payload_marker = "rsc03-source-code-payload"
  private val _web_presentation_payload_marker = "rsc03-external-platform-presentation"
  private val _composition_schema = "cncf.component-subcomponent-composition.v1"
  private val _documentation_role = "Documentation"
  private val _source_code_role = "SourceCode"
  private val _web_presentation_role = "ExternalPlatformPresentation"
  private val _published_at = "2026-08-20T00:00:00Z"

  private def _fixture(root: Path): Fixture = {
    val parentproject = root.resolve("parent-project")
    _write(
      parentproject.resolve("project.yaml"),
      s"""project:
         |  namespace: ${_namespace}
         |  id: RscParent
         |  name: rsc-parent
         |  component:
         |    version: ${_version}
         |""".stripMargin
    )
    _write(parentproject.resolve("src/main/cozy/rsc-parent.cml"), "# RSC03 parent fixture\n")
    val parentcar = root.resolve("input/rsc-parent.car")
    _archive(
      parentcar,
      Vector(
        "component-descriptor.json" -> _descriptor("RscParent"),
        "abi-manifest.json" -> _abi("RscParent"),
        "release-membership.txt" -> Vector(
          _documentation_id,
          _source_code_id,
          _web_presentation_id
        ).mkString("\n")
      )
    )
    val documentationcar = root.resolve("input/rsc-documentation.car")
    _archive(
      documentationcar,
      Vector(
        "component-descriptor.json" -> _descriptor("RscDocumentation"),
        "abi-manifest.json" -> _abi("RscDocumentation"),
        "payload/documentation/README.md" -> _documentation_payload_marker
      )
    )
    val sourcecodecar = root.resolve("input/rsc-source-code.car")
    _archive(
      sourcecodecar,
      Vector(
        "component-descriptor.json" -> _descriptor("RscSourceCode"),
        "abi-manifest.json" -> _abi("RscSourceCode"),
        "payload/source-code/release-inputs.txt" -> _source_code_payload_marker
      )
    )
    val webpresentationcar = root.resolve("input/rsc-web-presentation.car")
    _archive(
      webpresentationcar,
      Vector(
        "component-descriptor.json" -> _descriptor("RscWebPresentation"),
        "abi-manifest.json" -> _abi("RscWebPresentation"),
        "payload/web-presentation/platform.json" ->
          s"""{"platform":"external","marker":"${_web_presentation_payload_marker}"}"""
      )
    )
    val childcars = Vector(documentationcar, sourcecodecar, webpresentationcar)
    val composition = _write(root.resolve("input/composition.json"), _composition)
    Fixture(parentproject, parentcar, childcars, composition)
  }

  private def _package_arguments(
    fixture: Fixture,
    output: Path,
    requiredchildren: Vector[String] = Vector(
      _documentation_id,
      _source_code_id
    ),
    childcars: Vector[Path],
    compositionpath: Option[Path] = None
  ): Array[String] = {
    val selectedcomposition = compositionpath.getOrElse(fixture.composition)
    (Vector(
      "package-subcomponent-release",
      "--project-dir", fixture.parentproject.toString,
      "--parent-car", fixture.parentcar.toString,
      "--composition", selectedcomposition.toString,
      "--save", output.toString,
      "--published-at", _published_at
    ) ++ childcars.flatMap(child => Vector("--child-car", child.toString)) ++
      requiredchildren.flatMap(child => Vector("--required-child", child))).toArray
  }

  private def _publish_arguments(
    fixture: Fixture,
    release: Path,
    warehouse: Path
  ): Array[String] = Array(
    "publish-subcomponent-release",
    "--project-dir", fixture.parentproject.toString,
    "--warehouse", warehouse.toString,
    "--release", release.toString,
    "--published-at", _published_at
  )

  private def _composition: String =
    s"""{"schema":"${_composition_schema}","membershipKind":"parent-child","parent":${_parent_composition},"members":[${_member_composition("RscDocumentation", true, _documentation_role, "documentation", "documentation/README.md")},${_member_composition("RscSourceCode", true, _source_code_role, "source-code", "source-code/release-inputs.txt")},${_member_composition("RscWebPresentation", false, _web_presentation_role, "external-platform-presentation", "web-presentation/platform.json")}],"fixtureExtension":{"accepted":true}}"""

  private def _hostile_compositions(root: Path, fixture: Fixture): Vector[(String, Path)] = {
    val valid = Files.readString(fixture.composition, StandardCharsets.UTF_8)
    val duplicateresource = valid.replace(
      "\"logicalResource\":\"https://example.test/phase58/source-code\"",
      "\"logicalResource\":\"https://example.test/phase58/documentation\""
    )
    val schemaprefix = "{\"schema\":\"" + _composition_schema + "\",\"membershipKind\":\"parent-child\""
    val duplicateschema = valid.replace(
      schemaprefix,
      "{\"schema\":\"" + _composition_schema + "\",\"schema\":\"" + _composition_schema + "\",\"membershipKind\":\"parent-child\""
    )
    Vector(
      "duplicate-logical-resource" -> _write(root.resolve("input/duplicate-logical-resource.json"), duplicateresource),
      "duplicate-schema-key" -> _write(root.resolve("input/duplicate-schema-key.json"), duplicateschema)
    )
  }

  private def _duplicate_json_field(json: String, field: String): String = {
    val token = "\"" + field + "\":"
    val start = json.lastIndexOf(token)
    val openingquote = json.indexOf('"', start + token.length)
    val closingquote = json.indexOf('"', openingquote + 1)
    if (start < 0 || openingquote < 0 || closingquote < 0)
      sys.error(s"JSON field not found for duplicate fixture: $field")
    val fieldtext = json.substring(start, closingquote + 1)
    json.substring(0, closingquote + 1) + "," + fieldtext + json.substring(closingquote + 1)
  }

  private def _zip_entry_names(source: Path): Vector[String] = {
    val input = new ZipInputStream(Files.newInputStream(source))
    val names = scala.collection.mutable.ArrayBuffer.empty[String]
    try {
      var entry = input.getNextEntry
      while (entry != null) {
        names += entry.getName
        input.closeEntry()
        entry = input.getNextEntry
      }
    } finally input.close()
    names.toVector
  }

  private def _zip_entry_text(source: Path, target: String): String = {
    val input = new ZipInputStream(Files.newInputStream(source))
    var value: Option[String] = None
    try {
      var entry = input.getNextEntry
      while (entry != null) {
        if (entry.getName == target)
          value = Some(new String(input.readAllBytes(), StandardCharsets.UTF_8))
        input.closeEntry()
        entry = input.getNextEntry
      }
    } finally input.close()
    value.getOrElse(sys.error(s"zip entry not found: $target"))
  }

  private def _replace_zip_entry(source: Path, destination: Path, target: String, content: String): Path = {
    val input = new ZipInputStream(Files.newInputStream(source))
    val output = new ZipOutputStream(Files.newOutputStream(destination))
    var found = false
    try {
      var entry = input.getNextEntry
      while (entry != null) {
        val bytes = if (entry.getName == target) {
          found = true
          content.getBytes(StandardCharsets.UTF_8)
        } else input.readAllBytes()
        output.putNextEntry(new ZipEntry(entry.getName))
        output.write(bytes)
        output.closeEntry()
        input.closeEntry()
        entry = input.getNextEntry
      }
    } finally {
      input.close()
      output.close()
    }
    if (!found) sys.error(s"zip entry not found: $target")
    destination
  }

  private def _parent_composition: String =
    s"""{"componentId":"${_parent_id}","logicalRelease":"${_version}","primaryCar":${_car_composition("primary", "RscParent")}}"""

  private def _member_composition(
    id: String,
    required: Boolean,
    role: String,
    resource: String,
    path: String
  ): String =
    s"""{"componentId":"${_namespace}.${id}","logicalRelease":"${_version}","required":${required},"role":"${role}","implementationTechnology":"CAR","logicalResource":"https://example.test/phase58/${resource}","logicalPath":"${path}","subcomponentCar":${_car_composition("subcomponent", id)},"payload":{"authoritative":false,"executable":false},"authorization":{"state":"not-granted"},"integrity":{"state":"verified"},"availability":{"state":"available"},"deployment":{"platform":"external-platform","mode":"external","requiresExplicitPlatformAction":true,"authority":{"activation":false,"operation":false,"mcp":false,"disclosure":false,"deployment":false}},"access":{"visibility":"public"},"disclosure":{"mode":"metadata-only"},"license":{"spdx":"Apache-2.0"},"media":{"type":"application/vnd.cozy.car"},"profile":{"id":"phase58"}}"""

  private def _car_composition(classification: String, id: String): String =
    s"""{"classification":"${classification}","artifact":{"coordinate":"org.example:${_physical_artifact(id)}-car:0.1.0","repository":"https://repository.example.test/releases","physicalPath":"input/${id}.car"},"provenance":{"logicalSource":"git:phase58","physicalSource":"build:phase58","physicalPath":"src/main/cozy/${id}.cml"}}"""

  private def _physical_artifact(id: String): String =
    id.foldLeft("") { (result, character) =>
      if (character.isUpper && result.nonEmpty) s"$result-${character.toLower}"
      else s"$result${character.toLower}"
    }

  private def _descriptor(id: String): String =
    s"""{"schemaVersion":3,"component":{"namespace":"${_namespace}","id":"$id","version":"${_version}"}}"""

  private def _abi(id: String): String =
    s"""{"format":"cozy.car.abi-manifest.v2","component":{"namespace":"${_namespace}","id":"$id","version":"${_version}"},"abi":{"version":1,"exports":{"components":[{"namespace":"${_namespace}","id":"$id"}],"services":[],"operations":[],"types":[],"entities":[]},"dependencies":[]}}"""

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

  private def _zip_texts(path: Path): Vector[String] = _zip_texts(Files.readAllBytes(path))

  private def _zip_texts(bytes: Array[Byte]): Vector[String] = {
    val input = new ZipInputStream(new ByteArrayInputStream(bytes))
    val texts = scala.collection.mutable.ArrayBuffer.empty[String]
    try {
      var entry = input.getNextEntry
      while (entry != null) {
        val entrybytes = input.readAllBytes()
        texts += new String(entrybytes, StandardCharsets.UTF_8)
        if (entrybytes.length >= 4 && entrybytes(0) == 'P'.toByte && entrybytes(1) == 'K'.toByte && entrybytes(2) == 3 && entrybytes(3) == 4)
          texts ++= _zip_texts(entrybytes)
        entry = input.getNextEntry
      }
    } finally input.close()
    texts.toVector
  }

  private def _tree_bytes(root: Path): Map[String, Vector[Byte]] = {
    if (!Files.isDirectory(root)) Map.empty
    else {
      val stream = Files.walk(root)
      try stream.iterator().asScala.collect {
        case path if Files.isRegularFile(path) =>
          root.relativize(path).toString -> Files.readAllBytes(path).toVector
      }.toMap
      finally stream.close()
    }
  }

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val workroot = Paths.get("target/cozy-test/work/rsc03-subcomponent-release").toAbsolutePath.normalize()
    Files.createDirectories(workroot)
    val directory = Files.createTempDirectory(workroot, s"$prefix-")
    try body(directory)
    finally _delete_recursively(directory)
  }

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _delete_recursively(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.sorted(java.util.Comparator.reverseOrder()).forEach(path => Files.deleteIfExists(path))
      finally stream.close()
    }
}
