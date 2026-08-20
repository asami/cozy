package cozy.archive

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

import scala.collection.JavaConverters._

import cozy.Cozy
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 20, 2026
 * @version Aug. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final class SubcomponentReleasePackagingSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "RSC03-AC-01 deterministic parent/child CAR and payload packaging with exact post-package integrity evidence" in {
    _with_temp_dir("rsc03-ac01") { root =>
      Given("a complete parent CAR and independently identified Documentation, SourceCode, and external-platform presentation CARs")
      val fixture = _fixture(root)
      val first = root.resolve("package-first.car")
      val firstintegrity = root.resolve("package-first.integrity")
      val second = root.resolve("package-second.car")
      val secondintegrity = root.resolve("package-second.integrity")

      When("the public package-subcomponent-release command is invoked twice with the same complete release")
      Cozy.main(_package_arguments(fixture, first, firstintegrity, childcars = fixture.childcars))
      Cozy.main(_package_arguments(fixture, second, secondintegrity, childcars = fixture.childcars))

      Then("both package CARs and their exact post-package integrity evidence are byte-equivalent")
      Files.isRegularFile(first) shouldBe true
      Files.isRegularFile(second) shouldBe true
      Files.isRegularFile(firstintegrity) shouldBe true
      Files.isRegularFile(secondintegrity) shouldBe true
      Files.readAllBytes(first).toVector shouldBe Files.readAllBytes(second).toVector
      Files.readString(firstintegrity, StandardCharsets.UTF_8) shouldBe
        Files.readString(secondintegrity, StandardCharsets.UTF_8)
      val integrity = Files.readString(firstintegrity, StandardCharsets.UTF_8)
      integrity should include(_parent_id)
      integrity should include(_documentation_id)
      integrity should include(_source_code_id)
      integrity should include(_web_presentation_id)
      integrity should include(_composition_schema)
      integrity should include(_sha256(fixture.composition))
      integrity should include(_parent_signature)
      integrity should include(_documentation_signature)
      integrity should include(_source_code_signature)
      integrity should include(_web_presentation_signature)
      integrity should include(_sha256(fixture.parentcar))
      fixture.childcars.map(_sha256).foreach(digest => integrity should include(digest))
      val packaged = _zip_texts(first).mkString("\n")
      packaged should include(_composition_schema)
      packaged should include(_documentation_role)
      packaged should include(_source_code_role)
      packaged should include(_web_presentation_role)
      packaged should include("\"fixtureExtension\":{\"accepted\":true}")
      packaged should include(_documentation_payload_marker)
      packaged should include(_source_code_payload_marker)
      packaged should include(_web_presentation_payload_marker)
    }
  }

  "RSC03-AC-02 incomplete or invalid required membership is never repository-visible" in {
    _with_temp_dir("rsc03-ac02") { root =>
      Given("a complete release already admitted to an isolated warehouse and two invalid required-membership candidates")
      val fixture = _fixture(root)
      val baseline = root.resolve("baseline.car")
      val baselineintegrity = root.resolve("baseline.integrity")
      val warehouse = root.resolve("warehouse")
      Cozy.main(_package_arguments(fixture, baseline, baselineintegrity, childcars = fixture.childcars))
      Cozy.main(_publish_arguments(fixture, baseline, baselineintegrity, warehouse))
      val before = _tree_bytes(warehouse)
      val candidates = Vector(
        ("incomplete", Vector(fixture.childcars(0)), Vector(_documentation_id, _source_code_id)),
        ("invalid", fixture.childcars, Vector(_documentation_id, "org.goldenport.cncf.phase58.RscMissing"))
      )

      When("the public package-subcomponent-release command evaluates each incomplete or invalid membership")
      candidates.foreach { case (label, childcars, requiredchildren) =>
        val output = root.resolve(s"$label.car")
        val integrity = root.resolve(s"$label.integrity")
        val error = intercept[Throwable] {
          Cozy.main(_package_arguments(fixture, output, integrity, requiredchildren, childcars))
        }

        Then("the candidate is rejected atomically and cannot alter repository visibility")
        error.getMessage should not be empty
        Files.exists(output) shouldBe false
        Files.exists(integrity) shouldBe false
        _tree_bytes(warehouse) shouldBe before
      }
    }
  }

  "RSC03-AC-04 duplicate logical resources and JSON object fields are rejected atomically at package time" in {
    _with_temp_dir("rsc03-ac04") { root =>
      Given("a valid RSC-02 composition and hostile variants with a duplicate logical resource or duplicate schema field")
      val fixture = _fixture(root)
      val hostile = _hostile_compositions(root, fixture)

      When("the public package-subcomponent-release command evaluates each hostile composition")
      hostile.foreach { case (label, compositionpath) =>
        val output = root.resolve(s"$label.car")
        val integrity = root.resolve(s"$label.integrity")
        val error = intercept[Throwable] {
          Cozy.main(
            _package_arguments(
              fixture,
              output,
              integrity,
              childcars = fixture.childcars,
              compositionpath = Some(compositionpath)
            )
          )
        }

        Then("the hostile composition is rejected before either package output becomes visible")
        error.getMessage should not be empty
        Files.exists(output) shouldBe false
        Files.exists(integrity) shouldBe false
      }
    }
  }

  "RSC03-AC-05 hostile composition archives are rejected before warehouse mutation" in {
    _with_temp_dir("rsc03-ac05") { root =>
      Given("a valid release archive and hostile copies that replace only composition.json")
      val fixture = _fixture(root)
      val validrelease = root.resolve("valid-release.car")
      val validintegrity = root.resolve("valid-release.integrity")
      Cozy.main(_package_arguments(fixture, validrelease, validintegrity, childcars = fixture.childcars))
      val hostile = _hostile_compositions(root, fixture).map { case (label, compositionpath) =>
        label -> _replace_zip_entry(validrelease, root.resolve(s"$label-release.car"), "composition.json", Files.readString(compositionpath))
      }

      When("publish-subcomponent-release re-reads each hostile archive")
      hostile.foreach { case (label, release) =>
        val warehouse = root.resolve(s"$label-warehouse")
        val before = _tree_bytes(warehouse)
        val error = intercept[Throwable] {
          Cozy.main(_publish_arguments(fixture, release, validintegrity, warehouse))
        }

        Then("the hostile composition is rejected before the isolated warehouse changes")
        error.getMessage should not be empty
        _tree_bytes(warehouse) shouldBe before
      }
    }
  }

  "RSC03-AC-02 an omitted optional composition member remains metadata-only while all required members are packaged" in {
    _with_temp_dir("rsc03-ac02-optional") { root =>
      Given("a canonical RSC-02 composition with two required child CARs and one optional external-platform member")
      val fixture = _fixture(root)
      val release = root.resolve("optional-omitted.car")
      val integrity = root.resolve("optional-omitted.integrity")

      When("the public package-subcomponent-release command receives exactly the required child CARs")
      Cozy.main(_package_arguments(fixture, release, integrity, childcars = fixture.childcars.take(2)))

      Then("the optional member remains in copied composition metadata but is absent from the payload archive")
      Files.isRegularFile(release) shouldBe true
      Files.isRegularFile(integrity) shouldBe true
      val packaged = _zip_texts(release).mkString("\n")
      packaged should include(_web_presentation_id)
      packaged should include(_web_presentation_role)
      packaged should not include (_web_presentation_payload_marker)
      Files.readString(integrity, StandardCharsets.UTF_8) should not include (_web_presentation_id)
    }
  }

  "RSC03-AC-03 local/remote publication, repeated-build, and source/archive evidence remain equivalent" in {
    _with_temp_dir("rsc03-ac03") { root =>
      Given("a complete release fixture and two isolated local warehouse roots standing in for local and remote publication")
      val fixture = _fixture(root)
      val first = root.resolve("release-first.car")
      val firstintegrity = root.resolve("release-first.integrity")
      val second = root.resolve("release-second.car")
      val secondintegrity = root.resolve("release-second.integrity")
      val localwarehouse = root.resolve("local-warehouse")
      val remotewarehouse = root.resolve("remote-warehouse")

      When("the release is packaged repeatedly and published through each isolated warehouse boundary")
      Cozy.main(_package_arguments(fixture, first, firstintegrity, childcars = fixture.childcars))
      Cozy.main(_package_arguments(fixture, second, secondintegrity, childcars = fixture.childcars))
      Cozy.main(_publish_arguments(fixture, first, firstintegrity, localwarehouse))
      Cozy.main(_publish_arguments(fixture, second, secondintegrity, remotewarehouse))

      Then("repeated package bytes, integrity evidence, and local/remote publication bytes remain equivalent")
      Files.readAllBytes(first).toVector shouldBe Files.readAllBytes(second).toVector
      Files.readString(firstintegrity, StandardCharsets.UTF_8) shouldBe
        Files.readString(secondintegrity, StandardCharsets.UTF_8)
      val localbytes = _tree_bytes(localwarehouse)
      val remotebytes = _tree_bytes(remotewarehouse)
      localbytes should not be empty
      localbytes shouldBe remotebytes
      localbytes.values.toVector should contain(Files.readAllBytes(first).toVector)
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
  private val _parent_signature = "cGFyZW50LXNpZw=="
  private val _documentation_signature = "ZG9jdW1lbnRhdGlvbi1zaWc="
  private val _source_code_signature = "c291cmNlLXNpZw=="
  private val _web_presentation_signature = "d2ViLXNpZw=="
  private val _published_at = "2026-08-20T00:00:00Z"

  private final case class Fixture(
    parentproject: Path,
    parentcar: Path,
    childcars: Vector[Path],
    composition: Path
  )

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
    val composition = _write(root.resolve("input/composition.json"), _composition(parentcar, childcars))
    Fixture(parentproject, parentcar, childcars, composition)
  }

  private def _package_arguments(
    fixture: Fixture,
    output: Path,
    integrity: Path,
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
      "--integrity", integrity.toString,
      "--published-at", _published_at
    ) ++ childcars.flatMap(child => Vector("--child-car", child.toString)) ++
      requiredchildren.flatMap(child => Vector("--required-child", child))).toArray
  }

  private def _publish_arguments(
    fixture: Fixture,
    release: Path,
    integrity: Path,
    warehouse: Path
  ): Array[String] = Array(
    "publish-subcomponent-release",
    "--project-dir", fixture.parentproject.toString,
    "--warehouse", warehouse.toString,
    "--release", release.toString,
    "--integrity", integrity.toString,
    "--published-at", _published_at
  )

  private def _composition(parentcar: Path, childcars: Vector[Path]): String =
    s"""{"schema":"${_composition_schema}","membershipKind":"parent-child","parent":${_parent_composition(parentcar)},"members":[${_member_composition("RscDocumentation", true, _documentation_role, "documentation", "documentation/README.md", childcars(0), _documentation_signature)},${_member_composition("RscSourceCode", true, _source_code_role, "source-code", "source-code/release-inputs.txt", childcars(1), _source_code_signature)},${_member_composition("RscWebPresentation", false, _web_presentation_role, "external-platform-presentation", "web-presentation/platform.json", childcars(2), _web_presentation_signature)}],"fixtureExtension":{"accepted":true}}"""

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

  private def _parent_composition(parentcar: Path): String =
    s"""{"componentId":"${_parent_id}","logicalRelease":"${_version}","primaryCar":${_car_composition("primary", "RscParent", parentcar, _parent_signature)}}"""

  private def _member_composition(
    id: String,
    required: Boolean,
    role: String,
    resource: String,
    path: String,
    car: Path,
    signature: String
  ): String =
    s"""{"componentId":"${_namespace}.${id}","logicalRelease":"${_version}","required":${required},"role":"${role}","implementationTechnology":"CAR","logicalResource":"https://example.test/phase58/${resource}","logicalPath":"${path}","subcomponentCar":${_car_composition("subcomponent", id, car, signature)},"payload":{"authoritative":false,"executable":false},"authorization":{"state":"not-granted"},"integrity":{"state":"verified"},"availability":{"state":"available"},"deployment":{"platform":"external-platform","mode":"external","requiresExplicitPlatformAction":true,"authority":{"activation":false,"operation":false,"mcp":false,"disclosure":false,"deployment":false}},"access":{"visibility":"public"},"disclosure":{"mode":"metadata-only"},"license":{"spdx":"Apache-2.0"},"media":{"type":"application/vnd.cozy.car"},"profile":{"id":"phase58"}}"""

  private def _car_composition(classification: String, id: String, car: Path, signature: String): String =
    s"""{"classification":"${classification}","artifact":{"coordinate":"org.example:${_physical_artifact(id)}-car:0.1.0","sha256":"${_sha256(car)}","signature":"${signature}","repository":"https://repository.example.test/releases","physicalPath":"input/${id}.car"},"provenance":{"logicalSource":"git:phase58","physicalSource":"build:phase58","physicalPath":"src/main/cozy/${id}.cml"}}"""

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

  private def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(byte => f"${byte & 0xff}%02x").mkString

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
