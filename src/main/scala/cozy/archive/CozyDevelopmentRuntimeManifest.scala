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
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDevelopmentRuntimeManifest {
  val FILE_NAME = "car-runtime-manifest.json"
  val SCHEMA_VERSION = "cncf.car-development-runtime-manifest.v2"
  val SOURCE_KIND = "development-directory"
  val RUNTIME_CLASSPATH_IDENTITY = "target/cncf.d/runtime-classpath.txt"
  val COMPONENT_DESCRIPTOR_IDENTITY = "target/cncf.d/component-descriptor.json"
  val COMPONENT_KNOWLEDGE_IDENTITY = CozyComponentKnowledgeCarrier.DEVELOPMENT_IDENTITY
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
    CozyArchivePackager._write_development_component_descriptor(projectroot, descriptoroutput)
    val generateddescriptor = _require_evidence_file(
      projectroot,
      descriptoroutput,
      COMPONENT_DESCRIPTOR_IDENTITY
    )
    val schemaversion = _descriptor_schema(generateddescriptor)
    val descriptoridentity = COMPONENT_DESCRIPTOR_IDENTITY
    val descriptor = generateddescriptor
    val abi = _require_evidence_file(
      projectroot,
      projectroot.resolve(ABI_MANIFEST_IDENTITY),
      ABI_MANIFEST_IDENTITY
    )
    val coordinate = _coordinate(descriptor)
    _require_project_coordinate(metadata, coordinate)
    _require_abi_coordinate(abi, coordinate)
    val componentknowledge = CozyComponentKnowledgeCarrier.fromProject(projectroot, coordinate.coordinate).map { carrier =>
      val output = projectroot.resolve(COMPONENT_KNOWLEDGE_IDENTITY)
      carrier.copyTo(output)
      val prepared = _require_evidence_file(projectroot, output, COMPONENT_KNOWLEDGE_IDENTITY)
      CozyComponentKnowledgeCarrier.requireDeclaredArchiveCarrier(
        _read_json(descriptor, COMPONENT_DESCRIPTOR_IDENTITY),
        Some(Files.readAllBytes(prepared)),
        COMPONENT_DESCRIPTOR_IDENTITY
      )
      prepared
    }
    if (componentknowledge.isEmpty)
      CozyComponentKnowledgeCarrier.removeDevelopmentCopy(projectroot)
    val runtime = contract.runtimeCompatibility
    val classpathidentity = _classpath_identity(projectroot, classpath)
    val evidence = Vector(
      _evidence_entry(RUNTIME_CLASSPATH_IDENTITY, classpath, Some(classpathidentity)),
      _evidence_entry(descriptoridentity, descriptor, None)
    ) ++ componentknowledge.toVector.map { path =>
      _evidence_entry(COMPONENT_KNOWLEDGE_IDENTITY, path, None)
    } ++ Vector(
      _evidence_entry(ABI_MANIFEST_IDENTITY, abi, None)
    )
    val manifest = Json.obj(
      "schemaVersion" -> schemaversion,
      "sourceKind" -> SOURCE_KIND,
      "car" -> Json.obj(
        "name" -> coordinate.mavenArtifactId,
        "version" -> coordinate.version,
        "component" -> coordinate.id
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

  private final case class Coordinate(
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate
  ) {
    def id: String = coordinate.id
    def version: String = coordinate.version
    def mavenArtifactId: String = coordinate.mavenArtifactId
  }

  private def _descriptor_schema(path: Path): String = {
    val json = _read_json(path, COMPONENT_DESCRIPTOR_IDENTITY)
    (json \ "schemaVersion").asOpt[Int] match {
      case Some(3) => SCHEMA_VERSION
      case Some(value) =>
        RAISE.invalidArgumentFault(s"component.descriptor.schema.unsupported source=$COMPONENT_DESCRIPTOR_IDENTITY actual=$value")
      case None if (json \ "schemaVersion").toOption.isEmpty =>
        RAISE.invalidArgumentFault(s"component.descriptor.schema.unsupported source=$COMPONENT_DESCRIPTOR_IDENTITY actual=missing")
      case None =>
        RAISE.invalidArgumentFault(s"component.descriptor.schema.unsupported source=$COMPONENT_DESCRIPTOR_IDENTITY actual=non-numeric")
    }
  }

  private def _coordinate(path: Path): Coordinate = {
    val json = _read_json(path, COMPONENT_DESCRIPTOR_IDENTITY)
    Coordinate(CozyComponentReleaseCoordinateCodec.readComponent(json, COMPONENT_DESCRIPTOR_IDENTITY))
  }

  private def _require_abi_coordinate(path: Path, coordinate: Coordinate): Unit = {
    val json = _read_json(path, ABI_MANIFEST_IDENTITY)
    CozyCarAbiManifest._validate_source_manifest(json, coordinate.coordinate, ABI_MANIFEST_IDENTITY)
  }

  private def _require_project_coordinate(
    metadata: CozyProjectYamlConfig.Config,
    coordinate: Coordinate
  ): Unit = {
    CozyComponentReleaseCoordinateCodec.requireExact(
      CozyComponentReleaseCoordinateCodec.fromProjectMetadata(metadata, "development-runtime-project"),
      coordinate.coordinate,
      "development-runtime-project"
    )
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
