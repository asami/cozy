package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, Files, Path, StandardCopyOption}
import java.security.MessageDigest
import scala.collection.JavaConverters._

import cozy.compatibility.CarMetadataCompatibility
import cozy.config.CozyProjectYamlConfig
import org.goldenport.RAISE
import play.api.libs.json.{JsArray, JsObject, Json}

/*
 * CNCF-owned runtime admission evidence for a mutable CAR development root.
 *
 * This deliberately differs from CozyCarRuntimeManifest.  A development root
 * proves its stable project contract and prepared runtime inputs; it never
 * claims that compiled class bytes or the whole directory are immutable.
 *
 * @since   Jul. 29, 2026
 * @version Jul. 31, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDevelopmentRuntimeManifest {
  val FILE_NAME = "car-runtime-manifest.json"
  val LEGACY_SCHEMA_VERSION = "cncf.car-development-runtime-manifest.v1"
  val SCHEMA_VERSION = "cncf.car-development-runtime-manifest.v2"
  val SOURCE_KIND = "development-directory"
  val RUNTIME_CLASSPATH_IDENTITY = "target/cncf.d/runtime-classpath.txt"
  val LEGACY_COMPONENT_DESCRIPTOR_IDENTITY = "src/main/car/component-descriptor.json"
  val COMPONENT_DESCRIPTOR_IDENTITY = "target/cncf.d/component-descriptor.json"
  val ABI_MANIFEST_IDENTITY = "src/main/car/abi-manifest.json"

  def write(
    projectRoot: Path,
    runtimeClasspathFile: Path,
    output: Path
  ): Unit = {
    val projectroot = projectRoot.toAbsolutePath.normalize()
    val classpath = _require_evidence_file(projectroot, runtimeClasspathFile, RUNTIME_CLASSPATH_IDENTITY)
    val metadata = CozyProjectYamlConfig.loadProjectMetadata(projectroot)
    val contract = CarMetadataCompatibility.requireValidDevelopmentCarProject(metadata)
    val descriptoroutput = projectroot.resolve(COMPONENT_DESCRIPTOR_IDENTITY)
    CozyArchivePackager.writeDevelopmentComponentDescriptor(projectroot, descriptoroutput)
    val generateddescriptor = _require_evidence_file(
      projectroot,
      descriptoroutput,
      COMPONENT_DESCRIPTOR_IDENTITY
    )
    val schemaversion = _descriptor_schema(generateddescriptor)
    val descriptoridentity = if (schemaversion == SCHEMA_VERSION) COMPONENT_DESCRIPTOR_IDENTITY else LEGACY_COMPONENT_DESCRIPTOR_IDENTITY
    val descriptor =
      if (descriptoridentity == COMPONENT_DESCRIPTOR_IDENTITY) generateddescriptor
      else _require_evidence_file(projectroot, projectroot.resolve(descriptoridentity), descriptoridentity)
    val abi = _require_evidence_file(
      projectroot,
      projectroot.resolve(ABI_MANIFEST_IDENTITY),
      ABI_MANIFEST_IDENTITY
    )
    val coordinate = _coordinate(descriptor)
    _require_project_coordinate(metadata, coordinate)
    _require_abi_coordinate(abi, coordinate)
    val runtime = contract.runtimeCompatibility
    val classpathidentity = _classpath_identity(projectroot, classpath)
    val evidence = Vector(
      _evidence_entry(RUNTIME_CLASSPATH_IDENTITY, classpath, Some(classpathidentity)),
      _evidence_entry(descriptoridentity, descriptor, None),
      _evidence_entry(ABI_MANIFEST_IDENTITY, abi, None)
    )
    val manifest = Json.obj(
      "schemaVersion" -> schemaversion,
      "sourceKind" -> SOURCE_KIND,
      "car" -> Json.obj(
        "name" -> coordinate.name,
        "version" -> coordinate.version,
        "component" -> coordinate.component
      ),
      "runtime" -> Json.obj(
        "cncf" -> Json.obj(
          "minimum" -> runtime.minimum,
          "maximum" -> runtime.maximum,
          "excluded" -> runtime.excluded,
          "tested" -> runtime.tested
        )
      ),
      "evidence" -> JsArray(evidence),
      "integrity" -> Json.obj(
        "algorithm" -> "SHA-256",
        "evidenceSha256" -> _evidence_digest(evidence)
      )
    )
    _write_atomically(output.toAbsolutePath.normalize(), Json.prettyPrint(manifest) + "\n")
  }

  private final case class Coordinate(name: String, version: String, component: String)

  private def _descriptor_schema(path: Path): String = {
    val json = _read_json(path, COMPONENT_DESCRIPTOR_IDENTITY)
    (json \ "schemaVersion").asOpt[Int] match {
      case Some(2) => SCHEMA_VERSION
      case Some(1) => LEGACY_SCHEMA_VERSION
      case Some(value) =>
        RAISE.invalidArgumentFault(s"$COMPONENT_DESCRIPTOR_IDENTITY has unsupported schemaVersion: $value")
      case None if (json \ "schemaVersion").toOption.isEmpty => LEGACY_SCHEMA_VERSION
      case None =>
        RAISE.invalidArgumentFault(s"$COMPONENT_DESCRIPTOR_IDENTITY has non-numeric schemaVersion")
    }
  }

  private def _coordinate(path: Path): Coordinate = {
    val json = _read_json(path, COMPONENT_DESCRIPTOR_IDENTITY)
    Coordinate(
      _required_string(json, "name", COMPONENT_DESCRIPTOR_IDENTITY),
      _required_string(json, "version", COMPONENT_DESCRIPTOR_IDENTITY),
      _component_name(json)
    )
  }

  private def _component_name(json: play.api.libs.json.JsValue): String =
    (json \ "component").asOpt[String].map(_.trim).filter(_.nonEmpty).orElse {
      (json \ "component" \ "name").asOpt[String].map(_.trim).filter(_.nonEmpty)
    }.getOrElse(RAISE.invalidArgumentFault(s"$COMPONENT_DESCRIPTOR_IDENTITY requires non-empty component."))

  private def _require_abi_coordinate(path: Path, coordinate: Coordinate): Unit = {
    val json = _read_json(path, ABI_MANIFEST_IDENTITY)
    val format = _required_string(json, "format", ABI_MANIFEST_IDENTITY)
    if (format != "cozy.car.abi-manifest.v1")
      RAISE.invalidArgumentFault(s"$ABI_MANIFEST_IDENTITY format is unsupported: $format")
    val car = (json \ "car").asOpt[JsObject].getOrElse(
      RAISE.invalidArgumentFault(s"$ABI_MANIFEST_IDENTITY must contain car metadata.")
    )
    _require_equal(_required_string(car, "name", s"$ABI_MANIFEST_IDENTITY car"), coordinate.name, "name")
    _require_equal(_required_string(car, "version", s"$ABI_MANIFEST_IDENTITY car"), coordinate.version, "version")
    val exports = (json \ "abi" \ "exports" \ "components").asOpt[Vector[JsObject]].getOrElse(
      RAISE.invalidArgumentFault(s"$ABI_MANIFEST_IDENTITY must contain abi.exports.components.")
    )
    val names = exports.flatMap(entry => (entry \ "name").asOpt[String].map(_.trim)).filter(_.nonEmpty)
    if (!names.contains(coordinate.component))
      RAISE.invalidArgumentFault(s"$ABI_MANIFEST_IDENTITY does not export component ${coordinate.component}.")
  }

  private def _require_project_coordinate(
    metadata: CozyProjectYamlConfig.Config,
    coordinate: Coordinate
  ): Unit = {
    val projectname = metadata.value("project.name").getOrElse(
      RAISE.invalidArgumentFault("project.yaml requires project.name for development runtime evidence.")
    )
    val componentname = metadata.value("project.component.name").getOrElse(projectname)
    val componentversion = metadata.value("project.component.version").getOrElse(
      RAISE.invalidArgumentFault("project.yaml requires project.component.version for development runtime evidence.")
    )
    _require_project_equal(coordinate.name, projectname, "name")
    _require_project_equal(coordinate.version, componentversion, "version")
    _require_project_equal(coordinate.component, componentname, "component")
  }

  private def _require_evidence_file(root: Path, path: Path, identity: String): Path = {
    _require_safe_identity(identity)
    val normalized = path.toAbsolutePath.normalize()
    if (!normalized.startsWith(root))
      RAISE.invalidArgumentFault(s"Development runtime evidence escapes project root: $identity")
    if (!Files.isRegularFile(normalized) || Files.size(normalized) == 0L)
      RAISE.invalidArgumentFault(s"Development runtime evidence is missing or empty: $normalized")
    normalized
  }

  private def _evidence_entry(identity: String, path: Path, logicaldigest: Option[String]): JsObject =
    Json.obj(
      "path" -> identity,
      "sha256" -> _sha256(path)
    ) ++ logicaldigest.map(value => Json.obj("logicalSha256" -> value)).getOrElse(Json.obj())

  private def _classpath_identity(root: Path, path: Path): String = {
    val identities = Files.readAllLines(path, StandardCharsets.UTF_8).asScala.toVector
      .flatMap(_.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)).toVector)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map { value =>
        val entry = try Path.of(value).toAbsolutePath.normalize()
        catch {
          case exception: Exception =>
            RAISE.invalidArgumentFault(s"Development runtime classpath entry is invalid: $value (declared by $path): ${Option(exception.getMessage).getOrElse(exception.getClass.getName)}")
        }
        if (!Files.exists(entry))
          RAISE.invalidArgumentFault(s"Development runtime classpath entry is missing: $entry (declared by $path)")
        if (entry.startsWith(root))
          s"project:${root.relativize(entry).toString.replace('\\', '/')}"
        else
          s"external:${entry.getFileName.toString}"
      }
      .distinct
      .sorted
    _sha256(identities.mkString("\n").getBytes(StandardCharsets.UTF_8))
  }

  private def _read_json(path: Path, source: String): play.api.libs.json.JsValue =
    try Json.parse(Files.readString(path, StandardCharsets.UTF_8))
    catch {
      case exception: Exception =>
        RAISE.invalidArgumentFault(s"$source is not valid JSON: ${Option(exception.getMessage).getOrElse(exception.getClass.getName)}")
    }

  private def _required_string(json: play.api.libs.json.JsValue, key: String, source: String): String =
    (json \ key).asOpt[String].map(_.trim).filter(_.nonEmpty).getOrElse(
      RAISE.invalidArgumentFault(s"$source requires non-empty $key.")
    )

  private def _require_equal(actual: String, expected: String, field: String): Unit =
    if (actual != expected)
      RAISE.invalidArgumentFault(s"Development runtime ABI $field mismatch: expected=$expected actual=$actual")

  private def _require_project_equal(actual: String, expected: String, field: String): Unit =
    if (actual != expected)
      RAISE.invalidArgumentFault(s"Development runtime project $field mismatch: expected=$expected actual=$actual")

  private def _require_safe_identity(value: String): Unit =
    if (value.isEmpty || value.startsWith("/") || value.contains("\\") || value.split('/').contains(".."))
      RAISE.invalidArgumentFault(s"Development runtime evidence has unsafe identity: $value")

  private def _write_atomically(path: Path, content: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val temporary = Files.createTempFile(path.getParent, s".${path.getFileName.toString}.", ".tmp")
    try {
      Files.writeString(temporary, content, StandardCharsets.UTF_8)
      try
        Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      catch {
        case _: AtomicMoveNotSupportedException =>
          Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
      }
    } finally {
      Files.deleteIfExists(temporary)
    }
  }

  private def _sha256(path: Path): String =
    _sha256(Files.readAllBytes(path))

  private def _evidence_digest(entries: Vector[JsObject]): String =
    _sha256(entries.map { entry =>
      val path = (entry \ "path").as[String]
      val digest = (entry \ "sha256").as[String]
      val logical = (entry \ "logicalSha256").asOpt[String].getOrElse("")
      s"$path\t$digest\t$logical"
    }.mkString("\n").getBytes(StandardCharsets.UTF_8))

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString
}
