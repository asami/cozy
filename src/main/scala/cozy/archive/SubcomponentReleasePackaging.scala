package cozy.archive

import java.io.ByteArrayInputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.time.Instant
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
 * contracts. It packages semantic parent/child delivery metadata and payloads;
 * normal CAR/SAR publication remains responsible for its own index.
 *
 * @since   Aug. 20, 2026
 *  version Aug. 21, 2026
 * @version Oct. 10, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object SubcomponentReleasePackaging {
  private val _release_schema = "cozy.subcomponent-release.v1"
  private val _release_manifest_path = "release-manifest.json"
  private val _composition_path = "composition.json"
  private val _parent_payload_path = "payload/parent.car"

  private final case class CarPayload(
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    bytes: Array[Byte]
  )
  private final case class PayloadReference(
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    archivepath: String
  )
  private final case class CompositionCar(
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    artifactcoordinate: String
  )
  private final case class CompositionMember(
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    required: Boolean,
    role: String,
    technology: String,
    logicalresource: String
  )
  private final case class Composition(
    bytes: Array[Byte],
    parent: CompositionCar,
    members: Vector[CompositionMember]
  )
  private final case class CompositionReference(
    archivepath: String
  )
  private final case class Release(
    publishedat: String,
    parent: CarPayload,
    children: Vector[CarPayload],
    composition: Composition,
    bytes: Array[Byte]
  )
  private final case class PackageRequest(
    projectdir: Path,
    parentcar: Path,
    childcars: Vector[Path],
    composition: Path,
    requiredchildren: Vector[String],
    save: Path,
    publishedat: String
  )
  private final case class PublishRequest(
    projectdir: Path,
    warehouse: Path,
    release: Path,
    publishedat: String
  )
  private final case class ZipEntries(
    files: Map[String, Array[Byte]],
    directories: Set[String]
  )

  private val _package_parameters = Vector(
    spec.Parameter.propertyFileOption("project-dir"),
    spec.Parameter.propertyFileOption("parent-car"),
    spec.Parameter.propertyFileOption("child-car"),
    spec.Parameter.propertyFileOption("composition"),
    spec.Parameter.property("required-child"),
    spec.Parameter.propertyFileOption("save"),
    spec.Parameter.property("published-at")
  )
  private val _publish_parameters = Vector(
    spec.Parameter.propertyFileOption("project-dir"),
    spec.Parameter.propertyFileOption("warehouse"),
    spec.Parameter.propertyFileOption("release"),
    spec.Parameter.property("published-at")
  )

  def packageRelease(args: List[String]): Unit = {
    val request = _package_request(args)
    val projectcoordinate = _project_coordinate(request.projectdir)
    _require_disjoint_output_paths(
      Vector(request.parentcar, request.composition) ++ request.childcars,
      Vector(request.save),
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
    val manifest = _release_manifest(publishedat, parent, children.sortBy(_.coordinate.qualifiedId))

    _write_release_archive(request.save, manifest, parent, children, composition)
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
        s"publish-subcomponent-release --published-at does not match release metadata: expected=${release.publishedat} actual=$publishedat"
      )
    val destination = _warehouse_destination(request.warehouse, projectcoordinate)
    _require_disjoint_output_paths(
      Vector(request.release),
      Vector(destination),
      "publish-subcomponent-release"
    )
    Files.createDirectories(_parent(destination))
    Files.write(destination, release.bytes)
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
      _single_string(parsed, "published-at")
    )
  }

  private def _publish_request(args: List[String]): PublishRequest = {
    val parsed = CozyCliArgs.parseStrict(_publish_parameters: _*)(args)
    PublishRequest(
      _single_path(parsed, "project-dir"),
      _single_path(parsed, "warehouse"),
      _single_path(parsed, "release"),
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
    payload
  }

  private def _read_composition(path: Path): Composition = {
    val source = path.toAbsolutePath.normalize()
    if (!Files.isRegularFile(source))
      RAISE.invalidArgumentFault(s"RSC-02 composition document does not exist: $source")
    _read_composition_bytes(Files.readAllBytes(source), source.toString)
  }

  private def _read_composition_bytes(bytes: Array[Byte], source: String): Composition = {
    val root = _strict_json_object(bytes, source)
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
    Composition(bytes, parent, members)
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
    _read_composition_car(
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
    CompositionMember(coordinate, required, role, technology, logicalresource)
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
    _require_required_keys(artifact, Set("coordinate", "repository", "physicalPath"), s"$source artifact")
    val artifactcoordinate = _artifact_coordinate(
      _composition_text(artifact, "coordinate", s"$source artifact"),
      s"$source artifact coordinate"
    )
    _http_repository(_composition_text(artifact, "repository", s"$source artifact"), s"$source artifact repository")
    _safe_relative_path(_composition_text(artifact, "physicalPath", s"$source artifact"), s"$source artifact physicalPath")
    val provenance = _required_object(value, "provenance", source)
    _require_required_keys(provenance, Set("logicalSource", "physicalSource", "physicalPath"), s"$source provenance")
    _safe_provenance(_composition_text(provenance, "logicalSource", s"$source provenance"), s"$source provenance logicalSource")
    _safe_provenance(_composition_text(provenance, "physicalSource", s"$source provenance"), s"$source provenance physicalSource")
    _safe_relative_path(_composition_text(provenance, "physicalPath", s"$source provenance"), s"$source provenance physicalPath")
    CompositionCar(coordinate, artifactcoordinate)
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
  }

  private def _require_composition_member(
    member: CompositionMember,
    payload: CarPayload,
    source: String
  ): Unit = {
    CozyComponentReleaseCoordinateCodec.requireExact(member.coordinate, payload.coordinate, source)
  }

  private def _release_manifest(
    publishedat: String,
    parent: CarPayload,
    children: Vector[CarPayload]
  ): Array[Byte] = {
    val text =
      s"""{"children":[${children.map(x => _payload_reference_text(x, _child_payload_path(x.coordinate))).mkString(",")}],"composition":${_composition_reference_text},"parent":${_payload_reference_text(parent, _parent_payload_path)},"publishedAt":${_quote(publishedat)},"schemaVersion":${_quote(_release_schema)}}"""
    text.getBytes(StandardCharsets.UTF_8)
  }

  private def _payload_reference_text(payload: CarPayload, path: String): String =
    s"""{"archivePath":${_quote(path)},"canonicalId":${_quote(payload.coordinate.qualifiedId)},"coordinate":${_coordinate_text(payload.coordinate)}}"""

  private def _composition_reference_text: String =
    s"""{"archivePath":${_quote(_composition_path)},"membershipKind":"parent-child","schema":${_quote("cncf.component-subcomponent-composition.v1")}}"""

  private def _coordinate_text(coordinate: CozyComponentReleaseCoordinateCodec.Coordinate): String =
    s"""{"id":${_quote(coordinate.id)},"namespace":${_quote(coordinate.namespace)},"version":${_quote(coordinate.version)}}"""

  private def _quote(value: String): String = Json.stringify(JsString(value))

  private def _child_payload_path(coordinate: CozyComponentReleaseCoordinateCodec.Coordinate): String =
    s"payload/children/${coordinate.qualifiedId}.car"

  private def _write_release_archive(
    destination: Path,
    manifest: Array[Byte],
    parent: CarPayload,
    children: Vector[CarPayload],
    composition: Composition
  ): Unit = {
    val entries =
      Vector(_release_manifest_path -> manifest, _composition_path -> composition.bytes, _parent_payload_path -> parent.bytes) ++
        children.map(x => _child_payload_path(x.coordinate) -> x.bytes)
    val names = entries.map(_._1)
    if (names.distinct.size != names.size)
      RAISE.invalidArgumentFault("package-subcomponent-release generated duplicate release archive paths")
    val normalized = destination.toAbsolutePath.normalize()
    Files.createDirectories(_parent(normalized))
    val output = new ZipOutputStream(Files.newOutputStream(normalized))
    try entries.sortBy(_._1).foreach { case (name, bytes) =>
      _write_stored_zip_entry(output, name, bytes)
    }
    finally output.close()
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
    _read_release_bytes(bytes, source.toString)
  }

  private def _read_release_bytes(bytes: Array[Byte], source: String): Release = {
    val archive = _zip_entries(bytes, source)
    if (archive.directories.nonEmpty)
      RAISE.invalidArgumentFault(s"$source release CAR contains non-canonical directory entries")
    val manifestbytes = archive.files.getOrElse(
      _release_manifest_path,
      RAISE.invalidArgumentFault(s"$source release CAR requires ${_release_manifest_path}")
    )
    val manifest = _strict_json_object(manifestbytes, s"$source ${_release_manifest_path}")
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
    val parent = _payload_from_reference(parentreference, archive.files(parentreference.archivepath), s"$source parent payload")
    val childpayloads = children.map { reference =>
      _payload_from_reference(reference, archive.files(reference.archivepath), s"$source child payload")
    }
    _require_composition_membership(parent.coordinate, parent, childpayloads, composition, s"$source release composition")
    Release(publishedat, parent, childpayloads, composition, bytes)
  }

  private def _payload_reference(value: JsObject, source: String): PayloadReference = {
    _require_keys(value, Set("archivePath", "canonicalId", "coordinate"), source)
    val coordinate = _coordinate(_required_object(value, "coordinate", source), source)
    val canonicalid = _required_string(value, "canonicalId", source)
    if (canonicalid != coordinate.qualifiedId)
      RAISE.invalidArgumentFault(s"$source canonicalId does not match its coordinate")
    val archivepath = _required_string(value, "archivePath", source)
    _require_safe_archive_path(archivepath, source)
    PayloadReference(coordinate, archivepath)
  }

  private def _composition_reference(value: JsObject, source: String): CompositionReference = {
    _require_keys(value, Set("archivePath", "schema", "membershipKind"), source)
    _require_string(value, "archivePath", _composition_path, source)
    _require_string(value, "schema", "cncf.component-subcomponent-composition.v1", source)
    _require_string(value, "membershipKind", "parent-child", source)
    CompositionReference(_composition_path)
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
    CarPayload(coordinate, bytes)
  }

  private def _warehouse_destination(
    warehouse: Path,
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate
  ): Path = {
    val root = warehouse.toAbsolutePath.normalize().resolve("repository/subcomponent-release").
      resolve(coordinate.groupPath).resolve(coordinate.mavenArtifactId).resolve(coordinate.version)
    root.resolve(s"${coordinate.mavenArtifactId}-${coordinate.version}.release.car")
  }

  private def _require_disjoint_output_paths(
    inputs: Vector[Path],
    outputs: Vector[Path],
    command: String
  ): Unit = {
    val normalizedinputs = inputs.map(_.toAbsolutePath.normalize())
    val normalizedoutputs = outputs.map(_.toAbsolutePath.normalize())
    normalizedoutputs.foreach { output =>
      if (Files.exists(output, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS))
        RAISE.invalidArgumentFault(s"$command output must be a regular file, not a symbolic link or nonregular leaf: $output")
    }
    def _aliases_(first: Path, second: Path): Boolean =
      first == second || (Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second))
    if (normalizedoutputs.indices.exists { index =>
      normalizedoutputs.drop(index + 1).exists(_aliases_(normalizedoutputs(index), _))
    })
      RAISE.invalidArgumentFault(s"$command output paths must be distinct")
    val collisions = normalizedoutputs.filter(output => normalizedinputs.exists(_aliases_(output, _)))
    if (collisions.nonEmpty)
      RAISE.invalidArgumentFault(
        s"$command output paths cannot alias or replace their selected admitted inputs: ${collisions.mkString(", ")}"
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

  private def _strict_json_object(bytes: Array[Byte], source: String): JsObject = {
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

  private def _parent(path: Path): Path =
    Option(path.getParent).getOrElse(RAISE.invalidArgumentFault(s"Path has no parent: $path"))
}
