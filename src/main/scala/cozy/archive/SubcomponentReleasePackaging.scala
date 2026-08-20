package cozy.archive

import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.channels.FileChannel
import java.nio.file.{AtomicMoveNotSupportedException, Files, Path, StandardCopyOption, StandardOpenOption}
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.zip.{CRC32, ZipEntry, ZipInputStream, ZipOutputStream}

import scala.collection.mutable
import scala.util.control.NonFatal

import cozy.config.CozyProjectYamlConfig
import cozy.runtime.CozyCliArgs
import com.fasterxml.jackson.core.{JsonFactory, JsonParseException, JsonParser => JacksonParser}
import org.goldenport.RAISE
import org.goldenport.cli.spec
import play.api.libs.json.{JsArray, JsBoolean, JsObject, JsString, JsValue, Json}

/*
 * A release CAR is deliberately outside the ordinary CAR catalog and resolver
 * contracts.  It is an immutable, admission-marked parent/child delivery
 * envelope; normal CAR/SAR publication remains responsible for its own index.
 *
 * @since   Aug. 20, 2026
 * @version Aug. 21, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object SubcomponentReleasePackaging {
  private val _release_schema = "cozy.subcomponent-release.v1"
  private val _integrity_schema = "cozy.subcomponent-release.integrity.v1"
  private val _admission_schema = "cozy.subcomponent-release-admission.v1"
  private val _release_manifest_path = "release-manifest.json"
  private val _composition_path = "composition.json"
  private val _parent_payload_path = "payload/parent.car"

  private final case class CarPayload(
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    bytes: Array[Byte],
    sha256: String
  )
  private final case class PayloadReference(
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    sha256: String,
    archivepath: String,
    signature: String
  )
  private final case class CompositionCar(
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    artifactcoordinate: String,
    sha256: String,
    signature: String
  )
  private final case class CompositionMember(
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    required: Boolean,
    role: String,
    technology: String,
    logicalresource: String,
    sha256: String,
    signature: String
  )
  private final case class Composition(
    bytes: Array[Byte],
    sha256: String,
    parent: CompositionCar,
    members: Vector[CompositionMember]
  )
  private final case class CompositionReference(
    archivepath: String,
    sha256: String
  )
  private final case class Release(
    publishedat: String,
    parent: CarPayload,
    children: Vector[CarPayload],
    composition: Composition,
    bytes: Array[Byte],
    sha256: String
  )
  private final case class Integrity(
    bytes: Array[Byte],
    sha256: String
  )
  private final case class PackageRequest(
    projectdir: Path,
    parentcar: Path,
    childcars: Vector[Path],
    composition: Path,
    requiredchildren: Vector[String],
    save: Path,
    integrity: Path,
    publishedat: String
  )
  private final case class PublishRequest(
    projectdir: Path,
    warehouse: Path,
    release: Path,
    integrity: Path,
    publishedat: String
  )
  private final case class ZipEntries(
    files: Map[String, Array[Byte]],
    directories: Set[String]
  )
  private final case class Staged(destination: Path, temporary: Path)
  private final case class Snapshot(destination: Path, backup: Option[Path])

  private val _package_parameters = Vector(
    spec.Parameter.propertyFileOption("project-dir"),
    spec.Parameter.propertyFileOption("parent-car"),
    spec.Parameter.propertyFileOption("child-car"),
    spec.Parameter.propertyFileOption("composition"),
    spec.Parameter.property("required-child"),
    spec.Parameter.propertyFileOption("save"),
    spec.Parameter.propertyFileOption("integrity"),
    spec.Parameter.property("published-at")
  )
  private val _publish_parameters = Vector(
    spec.Parameter.propertyFileOption("project-dir"),
    spec.Parameter.propertyFileOption("warehouse"),
    spec.Parameter.propertyFileOption("release"),
    spec.Parameter.propertyFileOption("integrity"),
    spec.Parameter.property("published-at")
  )
  private val _warehouse_lock_monitor = new Object

  def packageRelease(args: List[String]): Unit = {
    val request = _package_request(args)
    val projectcoordinate = _project_coordinate(request.projectdir)
    _require_disjoint_output_paths(
      Vector(request.parentcar, request.composition) ++ request.childcars,
      Vector(request.save, request.integrity),
      "package-subcomponent-release"
    )
    val parent = _admit_input_car(request.parentcar, "parent CAR")
    CozyComponentReleaseCoordinateCodec.requireExact(
      projectcoordinate,
      parent.coordinate,
      "package-subcomponent-release parent CAR"
    )
    val children = request.childcars.map(_admit_input_car(_, "child CAR"))
    val composition = _read_composition(request.composition)
    _require_complete_membership(projectcoordinate, parent, children, composition, request.requiredchildren)
    val publishedat = _canonical_instant(request.publishedat, "package-subcomponent-release --published-at")
    val manifest = _release_manifest(publishedat, parent, children.sortBy(_.coordinate.qualifiedId), composition)

    // Every source, identity, and membership check precedes either output
    // staging operation.  Only fully admitted bytes reach a temporary file.
    val stagedrelease = _stage_release_archive(request.save, manifest, parent, children, composition)
    var stagedintegrity: Option[Staged] = None
    try {
      val packaged = _read_release(stagedrelease.temporary)
      val integritybytes = _integrity_document(
        publishedat,
        packaged.sha256,
        parent,
        children.sortBy(_.coordinate.qualifiedId),
        composition
      )
      val preparedintegrity = _stage_bytes(request.integrity, integritybytes)
      stagedintegrity = Some(preparedintegrity)
      _read_integrity(preparedintegrity.temporary, packaged)
      _commit(Vector(stagedrelease, preparedintegrity))
    } finally {
      Files.deleteIfExists(stagedrelease.temporary)
      stagedintegrity.foreach(x => Files.deleteIfExists(x.temporary))
    }
  }

  def publishRelease(args: List[String]): Unit = {
    val request = _publish_request(args)
    val projectcoordinate = _project_coordinate(request.projectdir)
    val release = _read_release(request.release)
    CozyComponentReleaseCoordinateCodec.requireExact(
      projectcoordinate,
      release.parent.coordinate,
      "publish-subcomponent-release parent CAR"
    )
    val publishedat = _canonical_instant(request.publishedat, "publish-subcomponent-release --published-at")
    if (publishedat != release.publishedat)
      RAISE.invalidArgumentFault(
        s"publish-subcomponent-release --published-at does not match package evidence: expected=${release.publishedat} actual=$publishedat"
      )
    val integrity = _read_integrity(request.integrity, release)
    val destinations = _warehouse_destinations(request.warehouse, projectcoordinate)
    _require_disjoint_output_paths(
      Vector(request.release, request.integrity),
      destinations.take(2),
      "publish-subcomponent-release"
    )

    _with_warehouse_lock(request.warehouse) {
      val archive = destinations(0)
      val evidence = destinations(1)
      val admission = destinations(2)
      if (Files.exists(admission)) {
        _require_exact_existing_admission(
          admission,
          archive,
          evidence,
          release,
          integrity,
          projectcoordinate
        )
      } else {
        val admissionbytes = _admission_document(
          projectcoordinate,
          release.publishedat,
          release.sha256,
          integrity.sha256
        )
        val stagedarchive = _stage_bytes(archive, release.bytes)
        var stageintegrity: Option[Staged] = None
        var stageadmission: Option[Staged] = None
        try {
          val preparedintegrity = _stage_bytes(evidence, integrity.bytes)
          stageintegrity = Some(preparedintegrity)
          val preparedadmission = _stage_bytes(admission, admissionbytes)
          stageadmission = Some(preparedadmission)
          // The admission marker is installed last.  It is the sole logical
          // visibility point for this separate release namespace.
          _commit(Vector(stagedarchive, preparedintegrity, preparedadmission))
        } finally {
          Files.deleteIfExists(stagedarchive.temporary)
          stageintegrity.foreach(x => Files.deleteIfExists(x.temporary))
          stageadmission.foreach(x => Files.deleteIfExists(x.temporary))
        }
      }
    }
  }

  private def _package_request(args: List[String]): PackageRequest = {
    val parsed = CozyCliArgs.parseStrict(_package_parameters: _*)(args)
    val children = _multiple_paths(parsed, "child-car")
    if (children.isEmpty)
      RAISE.invalidArgumentFault("package-subcomponent-release requires at least one --child-car")
    val required = _required_child_values(args)
    if (required.isEmpty)
      RAISE.invalidArgumentFault("package-subcomponent-release requires at least one --required-child")
    PackageRequest(
      _single_path(parsed, "project-dir"),
      _single_path(parsed, "parent-car"),
      children,
      _single_path(parsed, "composition"),
      required,
      _single_path(parsed, "save"),
      _single_path(parsed, "integrity"),
      _single_string(parsed, "published-at")
    )
  }

  private def _publish_request(args: List[String]): PublishRequest = {
    val parsed = CozyCliArgs.parseStrict(_publish_parameters: _*)(args)
    PublishRequest(
      _single_path(parsed, "project-dir"),
      _single_path(parsed, "warehouse"),
      _single_path(parsed, "release"),
      _single_path(parsed, "integrity"),
      _single_string(parsed, "published-at")
    )
  }

  private def _single_path(parsed: CozyCliArgs.Parsed, key: String): Path = {
    val values = parsed.properties(key)
    if (values.size != 1)
      RAISE.invalidArgumentFault(s"Expected exactly one --$key")
    CozyCliArgs.toPath(values.head)
  }

  private def _multiple_paths(parsed: CozyCliArgs.Parsed, key: String): Vector[Path] =
    parsed.properties(key).map(CozyCliArgs.toPath)

  private def _single_string(parsed: CozyCliArgs.Parsed, key: String): String = {
    val values = parsed.properties(key)
    if (values.size != 1)
      RAISE.invalidArgumentFault(s"Expected exactly one --$key")
    values.head
  }

  private def _required_child_values(args: List[String]): Vector[String] = {
    val values = _raw_option_values(args, "required-child")
    val identities = values.map { raw =>
      val value = raw.trim
      if (value.isEmpty || value != raw)
        RAISE.invalidArgumentFault("package-subcomponent-release --required-child must be a non-empty canonical identity")
      val separator = value.lastIndexOf('.')
      if (separator <= 0 || separator == value.length - 1)
        RAISE.invalidArgumentFault(s"Invalid required child identity: $value")
      val identity = CozyComponentReleaseCoordinateCodec.admitIdentity(
        value.substring(0, separator),
        value.substring(separator + 1),
        "package-subcomponent-release --required-child"
      )
      if (identity.qualifiedId != value)
        RAISE.invalidArgumentFault(s"Invalid required child identity: $value")
      identity.qualifiedId
    }
    val duplicates = identities.groupBy(identity).collect {
      case (identity, entries) if entries.size > 1 => identity
    }.toVector.sorted
    if (duplicates.nonEmpty)
      RAISE.invalidArgumentFault(
        s"package-subcomponent-release repeats required child identities: ${duplicates.mkString(", ")}"
      )
    identities
  }

  private def _raw_option_values(args: List[String], key: String): Vector[String] = {
    val option = s"--$key"
    val inline = option + "="
    def _go_(values: List[String], result: Vector[String]): Vector[String] =
      values match {
        case Nil => result
        case head :: tail if head.startsWith(inline) =>
          _go_(tail, result :+ head.substring(inline.length))
        case head :: value :: tail if head == option && !value.startsWith("--") =>
          _go_(tail, result :+ value)
        case head :: _ if head == option =>
          RAISE.invalidArgumentFault(s"Missing value for $option")
        case _ :: tail => _go_(tail, result)
      }
    _go_(args, Vector.empty)
  }

  private def _project_coordinate(projectdir: Path): CozyComponentReleaseCoordinateCodec.Coordinate =
    CozyComponentReleaseCoordinateCodec.fromProjectMetadata(
      CozyProjectYamlConfig.loadProjectMetadata(projectdir),
      "project.yaml"
    )

  private def _admit_input_car(path: Path, label: String): CarPayload = {
    val archive = path.toAbsolutePath.normalize()
    if (!Files.isRegularFile(archive))
      RAISE.invalidArgumentFault(s"$label does not exist: $archive")
    val bytes = Files.readAllBytes(archive)
    val payload = _read_car_payload(bytes, archive.toString)
    // Preserve the existing CAR admission boundary for supplied CAR files.
    CozyCarRuntimeManifest.requireCanonicalArchiveAdmission(archive, payload.coordinate)
    val digest = _sha256(bytes)
    if (RepositoryArtifactPublisher.sha256(archive) != digest)
      RAISE.invalidArgumentFault(s"$label changed while it was being admitted: $archive")
    payload
  }

  private def _read_composition(path: Path): Composition = {
    val source = path.toAbsolutePath.normalize()
    if (!Files.isRegularFile(source))
      RAISE.invalidArgumentFault(s"RSC-02 composition document does not exist: $source")
    _read_composition_bytes(Files.readAllBytes(source), source.toString)
  }

  private def _read_composition_bytes(bytes: Array[Byte], source: String): Composition = {
    val root = _composition_json_object(bytes, source)
    _require_compact_json(bytes, source)
    _require_required_keys(root, Set("schema", "membershipKind", "parent", "members"), source)
    _require_string(root, "schema", "cncf.component-subcomponent-composition.v1", source)
    _require_string(root, "membershipKind", "parent-child", source)
    val parent = _read_composition_parent(_required_object(root, "parent", source), s"$source parent")
    val members = _required_array(root, "members", source).zipWithIndex.map { case (value, index) =>
      _read_composition_member(_object(value, s"$source members[$index]"), s"$source members[$index]")
    }
    if (members.isEmpty)
      RAISE.invalidArgumentFault(s"$source requires at least one composition member")
    val identities = members.map(_.coordinate.qualifiedId)
    if (identities.distinct.size != identities.size)
      RAISE.invalidArgumentFault(s"$source repeats composition member identities")
    if (identities.contains(parent.coordinate.qualifiedId))
      RAISE.invalidArgumentFault(s"$source composition member cannot equal the parent")
    val roles = members.map(_.role)
    if (roles.distinct.size != roles.size)
      RAISE.invalidArgumentFault(s"$source repeats composition member roles")
    val logicalresources = members.map(_.logicalresource)
    if (logicalresources.distinct.size != logicalresources.size)
      RAISE.invalidArgumentFault(s"$source repeats composition member logical resources")
    val ordered = members.sortBy(x => (x.coordinate.qualifiedId, x.coordinate.version, x.role))
    if (members.map(_.coordinate.qualifiedId) != ordered.map(_.coordinate.qualifiedId))
      RAISE.invalidArgumentFault(s"$source composition members are not canonically ordered")
    Composition(bytes, _sha256(bytes), parent, members)
  }

  private def _read_composition_parent(value: JsObject, source: String): CompositionCar = {
    _require_required_keys(value, Set("componentId", "logicalRelease", "primaryCar"), source)
    val coordinate = _composition_coordinate(value, source)
    _read_composition_car(
      _required_object(value, "primaryCar", source),
      coordinate,
      "primary",
      s"$source primaryCar"
    )
  }

  private def _read_composition_member(value: JsObject, source: String): CompositionMember = {
    _require_required_keys(
      value,
      Set(
        "componentId", "logicalRelease", "required", "role", "implementationTechnology", "logicalResource", "logicalPath",
        "subcomponentCar", "payload", "authorization", "integrity", "availability", "deployment", "access",
        "disclosure", "license", "media", "profile"
      ),
      source
    )
    val coordinate = _composition_coordinate(value, source)
    val required = _required_boolean(value, "required", source)
    val role = _composition_text(value, "role", source)
    if (!role.matches("[A-Za-z][A-Za-z0-9._-]*"))
      RAISE.invalidArgumentFault(s"$source role must be a canonical role token")
    val technology = _composition_text(value, "implementationTechnology", source)
    val logicalresource = _composition_text(value, "logicalResource", source)
    _absolute_uri(logicalresource, s"$source logicalResource")
    _safe_relative_path(_composition_text(value, "logicalPath", source), s"$source logicalPath")
    val car = _read_composition_car(
      _required_object(value, "subcomponentCar", source),
      coordinate,
      "subcomponent",
      s"$source subcomponentCar"
    )
    _read_payload(_required_object(value, "payload", source), s"$source payload")
    _read_state(_required_object(value, "authorization", source), Set("not-granted", "granted"), s"$source authorization")
    _read_state(_required_object(value, "integrity", source), Set("verified", "unverified"), s"$source integrity")
    _read_state(_required_object(value, "availability", source), Set("available", "unavailable"), s"$source availability")
    _read_deployment(_required_object(value, "deployment", source), s"$source deployment")
    _read_single_text(_required_object(value, "access", source), "visibility", s"$source access")
    _read_single_text(_required_object(value, "disclosure", source), "mode", s"$source disclosure")
    _read_single_text(_required_object(value, "license", source), "spdx", s"$source license")
    _read_single_text(_required_object(value, "media", source), "type", s"$source media")
    _read_single_text(_required_object(value, "profile", source), "id", s"$source profile")
    CompositionMember(coordinate, required, role, technology, logicalresource, car.sha256, car.signature)
  }

  private def _composition_coordinate(value: JsObject, source: String): CozyComponentReleaseCoordinateCodec.Coordinate = {
    val componentid = _composition_text(value, "componentId", source)
    val separator = componentid.lastIndexOf('.')
    if (separator <= 0 || separator == componentid.length - 1)
      RAISE.invalidArgumentFault(s"$source componentId must be a canonical ComponentId")
    val coordinate = CozyComponentReleaseCoordinateCodec.admit(
      componentid.substring(0, separator),
      componentid.substring(separator + 1),
      _composition_text(value, "logicalRelease", source),
      source
    )
    if (coordinate.qualifiedId != componentid)
      RAISE.invalidArgumentFault(s"$source componentId must be canonical")
    coordinate
  }

  private def _read_composition_car(
    value: JsObject,
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    classification: String,
    source: String
  ): CompositionCar = {
    _require_required_keys(value, Set("classification", "artifact", "provenance"), source)
    _require_string(value, "classification", classification, source)
    val artifact = _required_object(value, "artifact", source)
    _require_required_keys(artifact, Set("coordinate", "sha256", "signature", "repository", "physicalPath"), s"$source artifact")
    val artifactcoordinate = _artifact_coordinate(
      _composition_text(artifact, "coordinate", s"$source artifact"),
      s"$source artifact coordinate"
    )
    val sha256 = _digest(_composition_text(artifact, "sha256", s"$source artifact"), s"$source artifact sha256")
    val signature = _base64_signature(_composition_text(artifact, "signature", s"$source artifact"), s"$source artifact signature")
    _http_repository(_composition_text(artifact, "repository", s"$source artifact"), s"$source artifact repository")
    _safe_relative_path(_composition_text(artifact, "physicalPath", s"$source artifact"), s"$source artifact physicalPath")
    val provenance = _required_object(value, "provenance", source)
    _require_required_keys(provenance, Set("logicalSource", "physicalSource", "physicalPath"), s"$source provenance")
    _safe_provenance(_composition_text(provenance, "logicalSource", s"$source provenance"), s"$source provenance logicalSource")
    _safe_provenance(_composition_text(provenance, "physicalSource", s"$source provenance"), s"$source provenance physicalSource")
    _safe_relative_path(_composition_text(provenance, "physicalPath", s"$source provenance"), s"$source provenance physicalPath")
    CompositionCar(coordinate, artifactcoordinate, sha256, signature)
  }

  private def _read_payload(value: JsObject, source: String): Unit = {
    _require_required_keys(value, Set("authoritative", "executable"), source)
    if (_required_boolean(value, "authoritative", source) || _required_boolean(value, "executable", source))
      RAISE.invalidArgumentFault(s"$source must be non-authoritative and non-executable")
  }

  private def _read_state(value: JsObject, allowed: Set[String], source: String): Unit = {
    _require_required_keys(value, Set("state"), source)
    val state = _composition_text(value, "state", source)
    if (!allowed.contains(state))
      RAISE.invalidArgumentFault(s"$source state is unsupported: $state")
  }

  private def _read_deployment(value: JsObject, source: String): Unit = {
    _require_required_keys(value, Set("platform", "mode", "requiresExplicitPlatformAction", "authority"), source)
    _composition_text(value, "platform", source)
    _require_string(value, "mode", "external", source)
    if (!_required_boolean(value, "requiresExplicitPlatformAction", source))
      RAISE.invalidArgumentFault(s"$source requires explicit platform action")
    val authority = _required_object(value, "authority", source)
    val fields = Set("activation", "operation", "mcp", "disclosure", "deployment")
    _require_required_keys(authority, fields, s"$source authority")
    if (fields.exists(field => _required_boolean(authority, field, s"$source authority")))
      RAISE.invalidArgumentFault(s"$source authority must not grant activation, operation, MCP, disclosure, or deployment")
  }

  private def _read_single_text(value: JsObject, key: String, source: String): Unit = {
    _require_required_keys(value, Set(key), source)
    _composition_text(value, key, source)
  }

  private def _composition_text(value: JsObject, key: String, source: String): String =
    value.value.get(key).collect {
      case JsString(text) if text.nonEmpty && text == text.trim && !text.exists(_.isControl) => text
    }.getOrElse(RAISE.invalidArgumentFault(s"$source requires canonical non-empty string $key"))

  private def _required_boolean(value: JsObject, key: String, source: String): Boolean =
    value.value.get(key).collect { case JsBoolean(flag) => flag }.getOrElse(
      RAISE.invalidArgumentFault(s"$source requires boolean $key")
    )

  private def _require_required_keys(value: JsObject, required: Set[String], source: String): Unit = {
    val missing = required -- value.keys
    if (missing.nonEmpty)
      RAISE.invalidArgumentFault(s"$source requires fields: ${missing.toVector.sorted.mkString(",")}")
  }

  private def _require_compact_json(bytes: Array[Byte], source: String): Unit = {
    val text = new String(bytes, StandardCharsets.UTF_8)
    var instring = false
    var escaped = false
    text.foreach { character =>
      if (instring) {
        if (escaped) escaped = false
        else if (character == '\\') escaped = true
        else if (character == '"') instring = false
      } else if (character == '"') instring = true
      else if (character.isWhitespace)
        RAISE.invalidArgumentFault(s"$source must be canonical compact RSC-02 JSON")
    }
    if (instring || escaped)
      RAISE.invalidArgumentFault(s"$source must be canonical compact RSC-02 JSON")
  }

  private def _base64_signature(value: String, source: String): String =
    try {
      if (Base64.getDecoder.decode(value).isEmpty)
        RAISE.invalidArgumentFault(s"$source must be non-empty Base64 signature material")
      value
    } catch {
      case NonFatal(_) => RAISE.invalidArgumentFault(s"$source must be Base64 signature material")
    }

  private def _safe_relative_path(value: String, source: String): Unit = {
    val segments = value.split("/", -1).toVector
    if (value.isEmpty || value.startsWith("/") || value.startsWith("\\") || value.matches("^[A-Za-z]:.*") ||
        value.contains('\\') || segments.exists(x => x.isEmpty || x == "." || x == ".."))
      RAISE.invalidArgumentFault(s"$source must be a non-empty safe relative path")
  }

  private def _safe_provenance(value: String, source: String): Unit =
    if (!value.matches("[A-Za-z][A-Za-z0-9+._-]*:[A-Za-z0-9][A-Za-z0-9+._/:=-]*"))
      RAISE.invalidArgumentFault(s"$source must be safe provenance evidence")

  private def _artifact_coordinate(value: String, source: String): String = {
    if (!value.matches("[A-Za-z0-9][A-Za-z0-9._-]*:[A-Za-z0-9][A-Za-z0-9._-]*:[A-Za-z0-9][A-Za-z0-9._-]*"))
      RAISE.invalidArgumentFault(s"$source must be a three-part artifact coordinate")
    value
  }

  private def _absolute_uri(value: String, source: String): Unit =
    try {
      val uri = URI.create(value)
      if (!uri.isAbsolute || uri.getScheme == null || uri.getSchemeSpecificPart == null || uri.getSchemeSpecificPart.isEmpty)
        RAISE.invalidArgumentFault(s"$source must be an absolute logical resource URI")
    } catch {
      case NonFatal(_) => RAISE.invalidArgumentFault(s"$source must be an absolute logical resource URI")
    }

  private def _http_repository(value: String, source: String): Unit =
    try {
      val uri = URI.create(value)
      if (!Set("http", "https").contains(uri.getScheme) || Option(uri.getHost).forall(_.isEmpty) || uri.getUserInfo != null)
        RAISE.invalidArgumentFault(s"$source must be an absolute HTTP(S) repository URI without user information")
    } catch {
      case NonFatal(_) => RAISE.invalidArgumentFault(s"$source must be an absolute HTTP(S) repository URI")
    }

  private def _require_complete_membership(
    projectcoordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    parent: CarPayload,
    children: Vector[CarPayload],
    composition: Composition,
    required: Vector[String]
  ): Unit = {
    val childidentities = children.map(_.coordinate.qualifiedId)
    val duplicatechildren = childidentities.groupBy(identity).collect {
      case (identity, entries) if entries.size > 1 => identity
    }.toVector.sorted
    if (duplicatechildren.nonEmpty)
      RAISE.invalidArgumentFault(
        s"package-subcomponent-release repeats child CAR identities: ${duplicatechildren.mkString(", ")}"
      )
    if (childidentities.contains(parent.coordinate.qualifiedId))
      RAISE.invalidArgumentFault(
        s"package-subcomponent-release child CAR cannot equal parent: ${parent.coordinate.qualifiedId}"
      )
    val supplied = childidentities.toSet
    val declaredrequired = composition.members.filter(_.required).map(_.coordinate.qualifiedId).toSet
    if (required.toSet != declaredrequired) {
      val missing = (declaredrequired -- required.toSet).toVector.sorted
      val extra = (required.toSet -- declaredrequired).toVector.sorted
      RAISE.invalidArgumentFault(
        s"package-subcomponent-release --required-child set does not match composition required membership: missing=${missing.mkString(",")} extra=${extra.mkString(",")}"
      )
    }
    _require_composition_membership(projectcoordinate, parent, children, composition, "package-subcomponent-release")
  }

  private def _require_composition_membership(
    projectcoordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    parent: CarPayload,
    children: Vector[CarPayload],
    composition: Composition,
    source: String
  ): Unit = {
    _require_composition_car(composition.parent, projectcoordinate, parent, s"$source parent composition")
    val supplied = children.map(_.coordinate.qualifiedId).toSet
    val members = composition.members.map(x => x.coordinate.qualifiedId -> x).toMap
    val unexpected = supplied -- members.keySet
    if (unexpected.nonEmpty)
      RAISE.invalidArgumentFault(s"$source supplies child CARs absent from composition: ${unexpected.toVector.sorted.mkString(",")}")
    val missing = composition.members.filter(_.required).map(_.coordinate.qualifiedId).toSet -- supplied
    if (missing.nonEmpty)
      RAISE.invalidArgumentFault(s"$source omits required composition members: ${missing.toVector.sorted.mkString(",")}")
    children.foreach { child =>
      val member = members(child.coordinate.qualifiedId)
      _require_composition_member(member, child, s"$source child composition ${child.coordinate.qualifiedId}")
    }
  }

  private def _require_composition_car(
    compositioncar: CompositionCar,
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    payload: CarPayload,
    source: String
  ): Unit = {
    CozyComponentReleaseCoordinateCodec.requireExact(coordinate, compositioncar.coordinate, source)
    CozyComponentReleaseCoordinateCodec.requireExact(compositioncar.coordinate, payload.coordinate, source)
    if (compositioncar.sha256 != payload.sha256)
      RAISE.invalidArgumentFault(s"$source SHA-256 does not match the CAR payload")
  }

  private def _require_composition_member(
    member: CompositionMember,
    payload: CarPayload,
    source: String
  ): Unit = {
    CozyComponentReleaseCoordinateCodec.requireExact(member.coordinate, payload.coordinate, source)
    if (member.sha256 != payload.sha256)
      RAISE.invalidArgumentFault(s"$source SHA-256 does not match the CAR payload")
  }

  private def _release_manifest(
    publishedat: String,
    parent: CarPayload,
    children: Vector[CarPayload],
    composition: Composition
  ): Array[Byte] = {
    val text =
      s"""{"children":[${children.map(x => _payload_reference_text(x, _child_payload_path(x.coordinate), _member_signature(composition, x))).mkString(",")}],"composition":${_composition_reference_text(composition)},"parent":${_payload_reference_text(parent, _parent_payload_path, composition.parent.signature)},"publishedAt":${_quote(publishedat)},"schemaVersion":${_quote(_release_schema)}}"""
    text.getBytes(StandardCharsets.UTF_8)
  }

  private def _integrity_document(
    publishedat: String,
    releasesha256: String,
    parent: CarPayload,
    children: Vector[CarPayload],
    composition: Composition
  ): Array[Byte] = {
    val text =
      s"""{"children":[${children.map(x => _integrity_reference_text(x, _member_signature(composition, x))).mkString(",")}],"composition":${_composition_reference_text(composition)},"parent":${_integrity_reference_text(parent, composition.parent.signature)},"publishedAt":${_quote(publishedat)},"releaseSha256":${_quote(releasesha256)},"schemaVersion":${_quote(_integrity_schema)}}""" + "\n"
    text.getBytes(StandardCharsets.UTF_8)
  }

  private def _admission_document(
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    publishedat: String,
    archivesha256: String,
    integritysha256: String
  ): Array[Byte] = {
    val text =
      s"""{"archiveSha256":${_quote(archivesha256)},"canonicalParent":${_quote(coordinate.qualifiedId)},"integritySha256":${_quote(integritysha256)},"publishedAt":${_quote(publishedat)},"schemaVersion":${_quote(_admission_schema)}}""" + "\n"
    text.getBytes(StandardCharsets.UTF_8)
  }

  private def _payload_reference_text(payload: CarPayload, path: String, signature: String): String =
    s"""{"archivePath":${_quote(path)},"canonicalId":${_quote(payload.coordinate.qualifiedId)},"coordinate":${_coordinate_text(payload.coordinate)},"sha256":${_quote(payload.sha256)},"signature":${_quote(signature)}}"""

  private def _integrity_reference_text(payload: CarPayload, signature: String): String =
    s"""{"canonicalId":${_quote(payload.coordinate.qualifiedId)},"sha256":${_quote(payload.sha256)},"signature":${_quote(signature)}}"""

  private def _composition_reference_text(composition: Composition): String =
    s"""{"archivePath":${_quote(_composition_path)},"membershipKind":"parent-child","schema":${_quote("cncf.component-subcomponent-composition.v1")},"sha256":${_quote(composition.sha256)}}"""

  private def _member_signature(composition: Composition, payload: CarPayload): String =
    composition.members.find(_.coordinate == payload.coordinate).map(_.signature).getOrElse(
      RAISE.invalidArgumentFault(s"Missing composition signature for ${payload.coordinate.qualifiedId}")
    )

  private def _coordinate_text(coordinate: CozyComponentReleaseCoordinateCodec.Coordinate): String =
    s"""{"id":${_quote(coordinate.id)},"namespace":${_quote(coordinate.namespace)},"version":${_quote(coordinate.version)}}"""

  private def _quote(value: String): String = Json.stringify(JsString(value))

  private def _child_payload_path(coordinate: CozyComponentReleaseCoordinateCodec.Coordinate): String =
    s"payload/children/${coordinate.qualifiedId}.car"

  private def _stage_release_archive(
    destination: Path,
    manifest: Array[Byte],
    parent: CarPayload,
    children: Vector[CarPayload],
    composition: Composition
  ): Staged = {
    val entries =
      Vector(_release_manifest_path -> manifest, _composition_path -> composition.bytes, _parent_payload_path -> parent.bytes) ++
        children.map(x => _child_payload_path(x.coordinate) -> x.bytes)
    val names = entries.map(_._1)
    if (names.distinct.size != names.size)
      RAISE.invalidArgumentFault("package-subcomponent-release generated duplicate release archive paths")
    val staged = _stage_empty(destination)
    try {
      val output = new ZipOutputStream(Files.newOutputStream(staged.temporary))
      try {
        entries.sortBy(_._1).foreach { case (name, bytes) =>
          _write_stored_zip_entry(output, name, bytes)
        }
      } finally output.close()
      staged
    } catch {
      case error: Throwable =>
        Files.deleteIfExists(staged.temporary)
        throw error
    }
  }

  private def _write_stored_zip_entry(output: ZipOutputStream, name: String, bytes: Array[Byte]): Unit = {
    _require_safe_archive_path(name, "generated release archive")
    val crc = new CRC32
    crc.update(bytes)
    val entry = new ZipEntry(name)
    entry.setTime(0L)
    entry.setMethod(ZipEntry.STORED)
    entry.setSize(bytes.length.toLong)
    entry.setCompressedSize(bytes.length.toLong)
    entry.setCrc(crc.getValue)
    output.putNextEntry(entry)
    output.write(bytes)
    output.closeEntry()
  }

  private def _read_release(path: Path): Release = {
    val source = path.toAbsolutePath.normalize()
    if (!Files.isRegularFile(source))
      RAISE.invalidArgumentFault(s"Release CAR does not exist: $source")
    val bytes = Files.readAllBytes(source)
    val release = _read_release_bytes(bytes, source.toString)
    val digest = _sha256(bytes)
    if (RepositoryArtifactPublisher.sha256(source) != digest)
      RAISE.invalidArgumentFault(s"Release CAR changed while it was being admitted: $source")
    release.copy(bytes = bytes, sha256 = digest)
  }

  private def _read_release_bytes(bytes: Array[Byte], source: String): Release = {
    val archive = _zip_entries(bytes, source)
    if (archive.directories.nonEmpty)
      RAISE.invalidArgumentFault(s"$source release CAR contains non-canonical directory entries")
    val manifestbytes = archive.files.getOrElse(
      _release_manifest_path,
      RAISE.invalidArgumentFault(s"$source release CAR requires ${_release_manifest_path}")
    )
    val manifest = _json_object(manifestbytes, s"$source ${_release_manifest_path}")
    _require_keys(manifest, Set("schemaVersion", "publishedAt", "parent", "children", "composition"), s"$source release manifest")
    _require_string(manifest, "schemaVersion", _release_schema, s"$source release manifest")
    val publishedat = _canonical_evidence_instant(
      _required_string(manifest, "publishedAt", s"$source release manifest"),
      s"$source release manifest publishedAt"
    )
    val parentreference = _payload_reference(_required_object(manifest, "parent", s"$source release manifest"), s"$source release parent")
    val children = _required_array(manifest, "children", s"$source release manifest").zipWithIndex.map { case (value, index) =>
      _payload_reference(_object(value, s"$source release child[$index]"), s"$source release child[$index]")
    }
    if (children.isEmpty)
      RAISE.invalidArgumentFault(s"$source release manifest requires at least one child")
    _require_manifest_membership(parentreference, children, source)
    val compositionreference = _composition_reference(
      _required_object(manifest, "composition", s"$source release manifest"),
      s"$source release composition"
    )
    val expectedpaths = Set(_release_manifest_path, compositionreference.archivepath, parentreference.archivepath) ++ children.map(_.archivepath)
    if (archive.files.keySet != expectedpaths)
      RAISE.invalidArgumentFault(
        s"$source release CAR path set mismatch: expected=${expectedpaths.toVector.sorted.mkString(",")} actual=${archive.files.keySet.toVector.sorted.mkString(",")}"
      )
    val composition = _read_composition_bytes(
      archive.files(compositionreference.archivepath),
      s"$source ${compositionreference.archivepath}"
    )
    if (composition.sha256 != compositionreference.sha256)
      RAISE.invalidArgumentFault(s"$source release composition SHA-256 does not match the manifest")
    val parent = _payload_from_reference(parentreference, archive.files(parentreference.archivepath), s"$source parent payload")
    val childpayloads = children.map { reference =>
      _payload_from_reference(reference, archive.files(reference.archivepath), s"$source child payload")
    }
    _require_composition_membership(parent.coordinate, parent, childpayloads, composition, s"$source release composition")
    _require_string_signature(parentreference.signature, composition.parent.signature, s"$source parent release signature")
    children.zip(childpayloads).foreach { case (reference, payload) =>
      _require_string_signature(
        reference.signature,
        _member_signature(composition, payload),
        s"$source child release signature ${payload.coordinate.qualifiedId}"
      )
    }
    Release(publishedat, parent, childpayloads, composition, bytes, _sha256(bytes))
  }

  private def _payload_reference(value: JsObject, source: String): PayloadReference = {
    _require_keys(value, Set("archivePath", "canonicalId", "coordinate", "sha256", "signature"), source)
    val coordinate = _coordinate(_required_object(value, "coordinate", source), source)
    val canonicalid = _required_string(value, "canonicalId", source)
    if (canonicalid != coordinate.qualifiedId)
      RAISE.invalidArgumentFault(s"$source canonicalId does not match its coordinate")
    val archivepath = _required_string(value, "archivePath", source)
    _require_safe_archive_path(archivepath, source)
    PayloadReference(
      coordinate,
      _digest(_required_string(value, "sha256", source), source),
      archivepath,
      _base64_signature(_required_string(value, "signature", source), s"$source signature")
    )
  }

  private def _composition_reference(value: JsObject, source: String): CompositionReference = {
    _require_keys(value, Set("archivePath", "schema", "membershipKind", "sha256"), source)
    _require_string(value, "archivePath", _composition_path, source)
    _require_string(value, "schema", "cncf.component-subcomponent-composition.v1", source)
    _require_string(value, "membershipKind", "parent-child", source)
    CompositionReference(_composition_path, _digest(_required_string(value, "sha256", source), source))
  }

  private def _require_composition_reference(value: JsObject, source: String, composition: Composition): Unit = {
    val reference = _composition_reference(value, source)
    if (reference.sha256 != composition.sha256)
      RAISE.invalidArgumentFault(s"$source SHA-256 does not match the release composition")
  }

  private def _require_string_signature(actual: String, expected: String, source: String): Unit = {
    if (_base64_signature(actual, source) != expected)
      RAISE.invalidArgumentFault(s"$source does not match the declared composition signature")
  }

  private def _require_manifest_membership(
    parent: PayloadReference,
    children: Vector[PayloadReference],
    source: String
  ): Unit = {
    if (parent.archivepath != _parent_payload_path)
      RAISE.invalidArgumentFault(s"$source release parent payload path is not canonical")
    val identities = children.map(_.coordinate.qualifiedId)
    if (identities.distinct.size != identities.size)
      RAISE.invalidArgumentFault(s"$source release manifest repeats child identities")
    if (identities.contains(parent.coordinate.qualifiedId))
      RAISE.invalidArgumentFault(s"$source release manifest declares the parent as a child")
    val expectedchildren = children.sortBy(_.coordinate.qualifiedId)
    if (children.map(_.coordinate.qualifiedId) != expectedchildren.map(_.coordinate.qualifiedId))
      RAISE.invalidArgumentFault(s"$source release manifest child ordering is not canonical")
    children.foreach { child =>
      if (child.archivepath != _child_payload_path(child.coordinate))
        RAISE.invalidArgumentFault(s"$source release child payload path is not canonical: ${child.coordinate.qualifiedId}")
    }
  }

  private def _payload_from_reference(
    reference: PayloadReference,
    bytes: Array[Byte],
    source: String
  ): CarPayload = {
    val payload = _read_car_payload(bytes, source)
    CozyComponentReleaseCoordinateCodec.requireExact(reference.coordinate, payload.coordinate, source)
    if (payload.sha256 != reference.sha256)
      RAISE.invalidArgumentFault(s"$source payload SHA-256 does not match the release manifest")
    payload
  }

  private def _read_car_payload(bytes: Array[Byte], source: String): CarPayload = {
    val archive = _zip_entries(bytes, source)
    val descriptorbytes = archive.files.getOrElse(
      "component-descriptor.json",
      RAISE.invalidArgumentFault(s"$source CAR requires component-descriptor.json")
    )
    val abibytes = archive.files.getOrElse(
      "abi-manifest.json",
      RAISE.invalidArgumentFault(s"$source CAR requires abi-manifest.json")
    )
    val descriptor = _json_object(descriptorbytes, s"$source component-descriptor.json")
    val coordinate = CozyComponentReleaseCoordinateCodec.readComponent(descriptor, s"$source component-descriptor.json")
    CozyArchivePackager._require_canonical_component_descriptor_shape(
      descriptor,
      s"$source component-descriptor.json"
    )
    CozyCarAbiManifest._validate_source_manifest(
      _json_object(abibytes, s"$source abi-manifest.json"),
      coordinate,
      s"$source abi-manifest.json"
    )
    CarPayload(coordinate, bytes, _sha256(bytes))
  }

  private def _read_integrity(path: Path, release: Release): Integrity = {
    val source = path.toAbsolutePath.normalize()
    if (!Files.isRegularFile(source))
      RAISE.invalidArgumentFault(s"Release integrity evidence does not exist: $source")
    val bytes = Files.readAllBytes(source)
    val evidence = _json_object(bytes, source.toString)
    _require_keys(evidence, Set("schemaVersion", "publishedAt", "releaseSha256", "parent", "children", "composition"), source.toString)
    _require_string(evidence, "schemaVersion", _integrity_schema, source.toString)
    val publishedat = _canonical_evidence_instant(
      _required_string(evidence, "publishedAt", source.toString),
      s"$source publishedAt"
    )
    if (publishedat != release.publishedat)
      RAISE.invalidArgumentFault(s"$source publishedAt does not match the release CAR")
    if (_digest(_required_string(evidence, "releaseSha256", source.toString), source.toString) != release.sha256)
      RAISE.invalidArgumentFault(s"$source release SHA-256 does not match the release CAR")
    _require_integrity_reference(
      _required_object(evidence, "parent", source.toString),
      release.parent,
      release.composition.parent.signature,
      s"$source parent"
    )
    _require_composition_reference(
      _required_object(evidence, "composition", source.toString),
      s"$source composition",
      release.composition
    )
    val children = _required_array(evidence, "children", source.toString)
    if (children.size != release.children.size)
      RAISE.invalidArgumentFault(s"$source child membership does not match the release CAR")
    children.zip(release.children).zipWithIndex.foreach { case ((value, payload), index) =>
      _require_integrity_reference(
        _object(value, s"$source child[$index]"),
        payload,
        _member_signature(release.composition, payload),
        s"$source child[$index]"
      )
    }
    Integrity(bytes, _sha256(bytes))
  }

  private def _require_integrity_reference(value: JsObject, payload: CarPayload, signature: String, source: String): Unit = {
    _require_keys(value, Set("canonicalId", "sha256", "signature"), source)
    _require_string(value, "canonicalId", payload.coordinate.qualifiedId, source)
    if (_digest(_required_string(value, "sha256", source), source) != payload.sha256)
      RAISE.invalidArgumentFault(s"$source SHA-256 does not match the release CAR")
    _require_string_signature(_required_string(value, "signature", source), signature, s"$source signature")
  }

  private def _warehouse_destinations(
    warehouse: Path,
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate
  ): Vector[Path] = {
    val root = warehouse.toAbsolutePath.normalize().resolve("repository/subcomponent-release").
      resolve(coordinate.groupPath).resolve(coordinate.mavenArtifactId).resolve(coordinate.version)
    val stem = s"${coordinate.mavenArtifactId}-${coordinate.version}"
    Vector(
      root.resolve(s"$stem.release.car"),
      root.resolve(s"$stem.release.integrity"),
      root.resolve("admission.json")
    )
  }

  private def _with_warehouse_lock[A](warehouse: Path)(body: => A): A =
    _warehouse_lock_monitor.synchronized {
      val root = warehouse.toAbsolutePath.normalize()
      val lockpath = root.resolve(".cozy/locks/subcomponent-release.lock")
      Files.createDirectories(lockpath.getParent)
      val channel = FileChannel.open(lockpath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
      try {
        val lock = channel.lock()
        try body
        finally lock.release()
      } finally channel.close()
    }

  private def _require_exact_existing_admission(
    marker: Path,
    archive: Path,
    evidence: Path,
    release: Release,
    integrity: Integrity,
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate
  ): Unit = {
    if (!Files.isRegularFile(marker) || !Files.isRegularFile(archive) || !Files.isRegularFile(evidence))
      RAISE.invalidArgumentFault(s"Existing subcomponent release admission is incomplete: $marker")
    val value = _json_object(Files.readAllBytes(marker), marker.toString)
    _require_keys(value, Set("schemaVersion", "canonicalParent", "publishedAt", "archiveSha256", "integritySha256"), marker.toString)
    _require_string(value, "schemaVersion", _admission_schema, marker.toString)
    _require_string(value, "canonicalParent", coordinate.qualifiedId, marker.toString)
    _require_string(value, "publishedAt", release.publishedat, marker.toString)
    val archivedigest = RepositoryArtifactPublisher.sha256(archive)
    val integritydigest = RepositoryArtifactPublisher.sha256(evidence)
    _require_string(value, "archiveSha256", archivedigest, marker.toString)
    _require_string(value, "integritySha256", integritydigest, marker.toString)
    if (!Files.readAllBytes(archive).sameElements(release.bytes) ||
      !Files.readAllBytes(evidence).sameElements(integrity.bytes))
      RAISE.invalidArgumentFault(
        s"A different subcomponent release is already admitted for ${coordinate.dependencyKey}"
      )
  }

  private def _stage_empty(destination: Path): Staged = {
    val normalized = destination.toAbsolutePath.normalize()
    val parent = _parent(normalized)
    Files.createDirectories(parent)
    val temporary = Files.createTempFile(parent, s".${normalized.getFileName.toString}-prepare-", ".tmp")
    Staged(normalized, temporary)
  }

  private def _stage_bytes(destination: Path, bytes: Array[Byte]): Staged = {
    val staged = _stage_empty(destination)
    try {
      Files.write(staged.temporary, bytes)
      staged
    } catch {
      case error: Throwable =>
        Files.deleteIfExists(staged.temporary)
        throw error
    }
  }

  private def _commit(staged: Vector[Staged]): Unit = {
    val destinations = staged.map(_.destination)
    if (destinations.distinct.size != destinations.size)
      RAISE.invalidArgumentFault("Subcomponent release transaction repeats a destination")
    var snapshots = Vector.empty[Snapshot]
    var started = false
    try {
      snapshots = staged.map(x => _snapshot(x.destination))
      started = true
      staged.foreach(x => _replace(x.temporary, x.destination))
    } catch {
      case error: Throwable =>
        if (started)
          _restore(snapshots.reverse).foreach(x => error.addSuppressed(x))
        throw error
    } finally {
      staged.foreach(x => Files.deleteIfExists(x.temporary))
      snapshots.flatMap(_.backup).foreach(x => Files.deleteIfExists(x))
    }
  }

  private def _snapshot(destination: Path): Snapshot = {
    if (Files.exists(destination)) {
      if (!Files.isRegularFile(destination))
        RAISE.invalidArgumentFault(s"Subcomponent release destination is not a regular file: $destination")
      val backup = Files.createTempFile(_parent(destination), s".${destination.getFileName.toString}-rollback-", ".bak")
      try {
        Files.copy(destination, backup, StandardCopyOption.REPLACE_EXISTING)
        Snapshot(destination, Some(backup))
      } catch {
        case error: Throwable =>
          Files.deleteIfExists(backup)
          throw error
      }
    } else
      Snapshot(destination, None)
  }

  private def _restore(snapshots: Vector[Snapshot]): Vector[Throwable] =
    snapshots.flatMap { snapshot =>
      try {
        snapshot.backup match {
          case Some(backup) => _replace(backup, snapshot.destination)
          case None => Files.deleteIfExists(snapshot.destination)
        }
        None
      } catch {
        case error: Throwable => Some(error)
      }
    }

  private def _replace(source: Path, destination: Path): Unit =
    try Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    catch {
      case _: AtomicMoveNotSupportedException =>
        Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING)
    }

  private def _require_disjoint_output_paths(
    inputs: Vector[Path],
    outputs: Vector[Path],
    command: String
  ): Unit = {
    val normalizedinputs = inputs.map(_.toAbsolutePath.normalize())
    val normalizedoutputs = outputs.map(_.toAbsolutePath.normalize())
    if (normalizedoutputs.distinct.size != normalizedoutputs.size)
      RAISE.invalidArgumentFault(s"$command output paths must be distinct")
    val collisions = normalizedoutputs.filter(normalizedinputs.contains)
    if (collisions.nonEmpty)
      RAISE.invalidArgumentFault(
        s"$command output paths cannot replace their admitted inputs: ${collisions.mkString(", ")}"
      )
  }

  private def _zip_entries(bytes: Array[Byte], source: String): ZipEntries = {
    val input = new ZipInputStream(new ByteArrayInputStream(bytes))
    val files = mutable.LinkedHashMap.empty[String, Array[Byte]]
    val directories = mutable.LinkedHashSet.empty[String]
    val names = mutable.HashSet.empty[String]
    try {
      var entry = input.getNextEntry
      while (entry != null) {
        val rawname = entry.getName
        _require_safe_archive_path(rawname, source)
        val normalized = rawname.replace('\\', '/')
        if (!names.add(normalized))
          RAISE.invalidArgumentFault(s"$source archive contains duplicate paths: $normalized")
        if (entry.isDirectory)
          directories += normalized
        else
          files += (normalized -> input.readAllBytes())
        input.closeEntry()
        entry = input.getNextEntry
      }
    } finally input.close()
    val fileprefixes = files.keys.flatMap(name => files.keys.filter(other => other.startsWith(name + "/"))).toVector
    if (fileprefixes.nonEmpty)
      RAISE.invalidArgumentFault(s"$source archive contains file/directory path conflicts")
    ZipEntries(files.toMap, directories.toSet)
  }

  private def _require_safe_archive_path(path: String, source: String): Unit = {
    val normalized = Option(path).map(_.replace('\\', '/')).getOrElse("")
    if (normalized.isEmpty || normalized.startsWith("/") || normalized.split('/').contains(".."))
      RAISE.invalidArgumentFault(s"$source archive contains an unsafe path: $path")
  }

  private def _composition_json_object(bytes: Array[Byte], source: String): JsObject = {
    val text = new String(bytes, StandardCharsets.UTF_8)
    val input = new JsonFactory().enable(JacksonParser.Feature.STRICT_DUPLICATE_DETECTION).createParser(text)
    try {
      try while (input.nextToken() != null) {}
      catch {
        case error: JsonParseException if Option(error.getOriginalMessage).exists(_.contains("Duplicate field")) =>
          RAISE.invalidArgumentFault(
            s"$source contains duplicate JSON object field: ${error.getOriginalMessage}"
          )
        case _: JsonParseException =>
          ()
      }
    } finally input.close()
    _json_object(bytes, source)
  }

  private def _json_object(bytes: Array[Byte], source: String): JsObject = {
    val json =
      try Json.parse(bytes)
      catch {
        case NonFatal(error) =>
          RAISE.invalidArgumentFault(s"$source is not valid JSON: ${Option(error.getMessage).getOrElse(error.getClass.getName)}")
      }
    _object(json, source)
  }

  private def _object(value: JsValue, source: String): JsObject =
    value.asOpt[JsObject].getOrElse(RAISE.invalidArgumentFault(s"$source must be a JSON object"))

  private def _required_object(value: JsObject, key: String, source: String): JsObject =
    value.value.get(key).map(_object(_, s"$source $key")).getOrElse(
      RAISE.invalidArgumentFault(s"$source requires $key")
    )

  private def _required_array(value: JsObject, key: String, source: String): Vector[JsValue] =
    value.value.get(key).collect { case JsArray(values) => values.toVector }.getOrElse(
      RAISE.invalidArgumentFault(s"$source requires array $key")
    )

  private def _required_string(value: JsObject, key: String, source: String): String =
    value.value.get(key).collect { case JsString(text) if text.trim.nonEmpty => text.trim }.getOrElse(
      RAISE.invalidArgumentFault(s"$source requires non-empty string $key")
    )

  private def _require_string(value: JsObject, key: String, expected: String, source: String): Unit = {
    val actual = _required_string(value, key, source)
    if (actual != expected)
      RAISE.invalidArgumentFault(s"$source $key mismatch: expected=$expected actual=$actual")
  }

  private def _require_keys(value: JsObject, expected: Set[String], source: String): Unit =
    if (value.keys != expected)
      RAISE.invalidArgumentFault(
        s"$source fields mismatch: expected=${expected.toVector.sorted.mkString(",")} actual=${value.keys.toVector.sorted.mkString(",")}"
      )

  private def _coordinate(value: JsObject, source: String): CozyComponentReleaseCoordinateCodec.Coordinate =
    CozyComponentReleaseCoordinateCodec.readComponent(Json.obj("component" -> value), source)

  private def _digest(value: String, source: String): String = {
    if (!value.matches("[0-9a-f]{64}"))
      RAISE.invalidArgumentFault(s"$source requires a lower-case SHA-256 digest")
    value
  }

  private def _canonical_instant(value: String, source: String): String =
    try Instant.parse(value.trim).toString
    catch {
      case NonFatal(error) =>
        RAISE.invalidArgumentFault(s"$source must be an RFC3339 instant: ${Option(error.getMessage).getOrElse(error.getClass.getName)}")
    }

  private def _canonical_evidence_instant(value: String, source: String): String = {
    val canonical = _canonical_instant(value, source)
    if (value != canonical)
      RAISE.invalidArgumentFault(s"$source must use canonical RFC3339 instant $canonical")
    canonical
  }

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString

  private def _parent(path: Path): Path =
    Option(path.getParent).getOrElse(RAISE.invalidArgumentFault(s"Path has no parent: $path"))
}
