package cozy.modeler

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import scala.util.Try
import scala.util.control.NonFatal
import play.api.libs.json._
import org.goldenport.RAISE
import cozy.compatibility.CncfRuntimeDescriptorContract

/*
 * CNCF-owned ComponentStyle metadata crosses the Scala 3 runtime / Scala 2
 * generator boundary through the SHA-256-pinned runtime descriptor.  This is
 * a consumer view; it never owns a second catalog source.
 *
 * @since   Jul. 31, 2026
 * @version Jul. 31, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] final case class ComponentStyleDefinition(
  selection: String,
  canonicalid: String,
  snapshot: ComponentStyleSnapshot
)

private[cozy] final case class ComponentStyleSnapshot(
  apiVersion: String,
  provider: String,
  id: String,
  version: Int,
  bundles: Vector[String],
  capabilities: Vector[String],
  effectiveCapabilities: Vector[String],
  subsystemCapabilities: Vector[String]
) {
  def toJson: JsObject =
    Json.obj(
      "apiVersion" -> apiVersion,
      "provider" -> provider,
      "id" -> id,
      "version" -> version,
      "parameterSchema" -> Json.obj(
        "type" -> "object",
        "properties" -> Json.obj(),
        "required" -> Json.arr(),
        "additionalProperties" -> false
      ),
      "parameters" -> Json.obj(),
      "provides" -> Json.obj(
        "bundles" -> bundles,
        "capabilities" -> capabilities,
        "effective" -> effectiveCapabilities
      ),
      "requires" -> Json.obj(
        "subsystemCapabilities" -> subsystemCapabilities
      )
    )
}

private[cozy] final case class ComponentCapabilityBundleDefinition(
  canonicalid: String,
  bundles: Vector[String],
  capabilities: Vector[String]
)

private[cozy] final case class ComponentStyleCatalog(
  styles: Map[String, ComponentStyleDefinition]
) {
  def requireSelection(selection: String): ComponentStyleDefinition =
    styles.get(selection.trim).getOrElse(
      RAISE.invalidArgumentFault(s"CNCF component style is unknown or unavailable: ${selection.trim}")
    )
}

private[cozy] object ComponentStyleCatalog {
  val EMPTY: ComponentStyleCatalog = ComponentStyleCatalog(Map.empty)
  val CARRIER_SCHEMA_VERSION = "cncf.component-style-catalog-carrier.v1"
  val RESOURCE_PATH = "META-INF/cncf/component-style-catalog.json"
  val API_VERSION = "cncf.textus/v1"
  val PROVIDER = "cncf"
  val KIND = "ComponentStyleCatalog"
  private val IDENTITY_PART = "[a-z0-9-]+"
  private val STYLE_IDENTITY = s"(?:$IDENTITY_PART\\.)?$IDENTITY_PART@[1-9][0-9]*"
  private val QUALIFIED_IDENTITY = s"$IDENTITY_PART\\.$IDENTITY_PART@[1-9][0-9]*"

  def fromValidatedDescriptor(
    validatedDescriptor: CncfRuntimeDescriptorContract.ValidatedDescriptor
  ): ComponentStyleCatalog = {
    val config = validatedDescriptor.config
    val prefix = "componentStyleCatalog"
    val root = config.json.flatMap(_.asObject).getOrElse(
      _invalid("CNCF runtime descriptor must be a JSON object")
    )
    val carrierjson = root(prefix)
    if (carrierjson.isEmpty)
      return EMPTY
    if (!validatedDescriptor.digestpinned)
      _invalid("CNCF component style catalog requires a digest-pinned runtime descriptor")
    val carrier = carrierjson.flatMap(_.asObject).getOrElse(
      _invalid("CNCF component style catalog carrier must be an object")
    )
    val expectedfields = Set("schemaVersion", "resource", "encoding", "sha256", "bytes")
    val unknownfields = carrier.keys.toSet -- expectedfields
    if (unknownfields.nonEmpty)
      _invalid(s"Unknown CNCF component style catalog carrier fields: ${unknownfields.toVector.sorted.mkString(", ")}")
    val schema = _required(config.value(s"$prefix.schemaVersion"), s"$prefix.schemaVersion")
    val resource = _required(config.value(s"$prefix.resource"), s"$prefix.resource")
    val encoding = _required(config.value(s"$prefix.encoding"), s"$prefix.encoding")
    val digest = _required(config.value(s"$prefix.sha256"), s"$prefix.sha256")
    val payload = _required(config.value(s"$prefix.bytes"), s"$prefix.bytes")
    if (schema != CARRIER_SCHEMA_VERSION)
      _invalid(s"Unsupported CNCF component style catalog carrier schema: $schema")
    if (resource != RESOURCE_PATH)
      _invalid(s"Unsupported CNCF component style catalog resource: $resource")
    if (encoding != "base64")
      _invalid(s"Unsupported CNCF component style catalog encoding: $encoding")
    if (!digest.matches("[0-9a-f]{64}"))
      _invalid(s"Invalid CNCF component style catalog SHA-256: $digest")
    val bytes = try Base64.getDecoder.decode(payload) catch {
      case NonFatal(_) => _invalid("Invalid CNCF component style catalog base64 payload")
    }
    val actualdigest = _sha256(bytes)
    if (actualdigest != digest)
      _invalid(s"CNCF component style catalog SHA-256 mismatch: expected $digest, actual $actualdigest")
    _parse_catalog(new String(bytes, StandardCharsets.UTF_8))
  }

  private def _parse_catalog(text: String): ComponentStyleCatalog = {
    val root = Json.parse(text).asOpt[JsObject].getOrElse(_invalid("CNCF component style catalog must be a JSON object"))
    _only(root, Set("apiVersion", "kind", "provider", "capabilityBundles", "componentStyles"), "catalog")
    if (_string(root, "apiVersion") != API_VERSION)
      _invalid(s"Unsupported CNCF component style catalog apiVersion: ${_string(root, "apiVersion")}")
    if (_string(root, "kind") != KIND)
      _invalid(s"Invalid CNCF component style catalog kind: ${_string(root, "kind")}")
    if (_string(root, "provider") != PROVIDER)
      _invalid(s"Unsupported CNCF component style catalog provider: ${_string(root, "provider")}")
    val bundledefinitions = _objects(root, "capabilityBundles", "catalog").map { bundle =>
      _only(bundle, Set("id", "bundles", "capabilities"), "capability bundle")
      val id = _string(bundle, "id")
      _identity(id, "component capability bundle")
      val nestedbundles = _strings(bundle, "bundles", "component capability bundle")
      val capabilities = _strings(bundle, "capabilities", "component capability bundle")
      _canonical_vector(nestedbundles, "component capability bundle bundles")
      _canonical_vector(capabilities, "component capability bundle capabilities")
      _major_compatible(nestedbundles, "component capability bundle bundles")
      _major_compatible(capabilities, "component capability bundle capabilities")
      ComponentCapabilityBundleDefinition(id, nestedbundles, capabilities)
    }
    _unique(bundledefinitions.map(_.canonicalid), "component capability bundle")
    val bundledefinitionsbyid = bundledefinitions.map(x => x.canonicalid -> x).toMap
    _validate_bundle_graph(bundledefinitionsbyid)
    val styles = _objects(root, "componentStyles", "catalog").map { style =>
      _only(style, Set("id", "parameterSchema", "provides", "requires"), "component style")
      val id = _string(style, "id")
      val selection = _selection(id)
      _closed_empty_schema(style.value.getOrElse("parameterSchema", _invalid("Component style requires parameterSchema")))
      val provides = style.value.get("provides").collect { case x: JsObject => x }.getOrElse(_invalid("Component style requires provides"))
      _only(provides, Set("bundles", "capabilities"), "component style provides")
      val stylebundles = _strings(provides, "bundles", "component style provides")
      _canonical_vector(stylebundles, "component style bundles")
      _major_compatible(stylebundles, "component style bundles")
      val stylecapabilities = _strings(provides, "capabilities", "component style provides")
      _canonical_vector(stylecapabilities, "component style capabilities")
      _major_compatible(stylecapabilities, "component style capabilities")
      val expandedcapabilities = _expand_bundles(bundledefinitionsbyid, stylebundles, Set.empty)
      val effectivecapabilities = (expandedcapabilities ++ stylecapabilities).sorted
      _canonical_vector(effectivecapabilities, "effective component capabilities")
      _major_compatible(expandedcapabilities ++ stylecapabilities, "effective component capabilities")
      val requires = style.value.get("requires").collect { case x: JsObject => x }.getOrElse(_invalid("Component style requires requires"))
      _only(requires, Set("subsystemCapabilities"), "component style requires")
      val subsystemcapabilities = _strings(requires, "subsystemCapabilities", "component style requires")
      _canonical_vector(subsystemcapabilities, "subsystem capabilities")
      _major_compatible(subsystemcapabilities, "subsystem capabilities")
      selection -> ComponentStyleDefinition(
        selection,
        id,
        ComponentStyleSnapshot(
          API_VERSION,
          PROVIDER,
          id,
          id.dropWhile(_ != '@').drop(1).toInt,
          stylebundles,
          stylecapabilities,
          effectivecapabilities,
          subsystemcapabilities
        )
      )
    }
    _unique(styles.map(_._1), "unversioned component style selection")
    ComponentStyleCatalog(styles.toMap)
  }

  private def _closed_empty_schema(value: JsValue): Unit = {
    val schema = value.asOpt[JsObject].getOrElse(_invalid("Component style parameterSchema must be an object"))
    _only(schema, Set("type", "properties", "required", "additionalProperties"), "component style parameterSchema")
    if (_string(schema, "type") != "object" || schema.value.get("properties") != Some(Json.obj()) ||
      _strings(schema, "required", "component style parameterSchema").nonEmpty ||
      schema.value.get("additionalProperties") != Some(JsBoolean(false)))
      _invalid("Component style parameter schema must be the closed empty object schema")
  }

  private def _objects(root: JsObject, field: String, context: String): Vector[JsObject] =
    root.value.get(field).flatMap(_.asOpt[JsArray]).map(_.value.toVector.map(_.asOpt[JsObject].getOrElse(_invalid(s"CNCF component style $context.$field must contain objects")))).getOrElse(_invalid(s"CNCF component style $context requires $field"))

  private def _strings(root: JsObject, field: String, context: String): Vector[String] =
    root.value.get(field).flatMap(_.asOpt[JsArray]).map(_.value.toVector.map(_.asOpt[String].getOrElse(_invalid(s"CNCF component style $context.$field must contain strings")))).getOrElse(_invalid(s"CNCF component style $context requires $field"))

  private def _string(root: JsObject, field: String): String =
    root.value.get(field).flatMap(_.asOpt[String]).getOrElse(_invalid(s"CNCF component style requires $field"))

  private def _required(value: Option[String], field: String): String =
    value.getOrElse(_invalid(s"CNCF runtime descriptor requires $field"))

  private def _selection(id: String): String = {
    if (!id.matches(STYLE_IDENTITY) || !_canonical_major(id.dropWhile(_ != '@').drop(1)))
      _invalid(s"Invalid CNCF component style identity: $id")
    id.takeWhile(_ != '@')
  }

  private def _identity(value: String, label: String): Unit =
    if (!value.matches(QUALIFIED_IDENTITY) || !_canonical_major(value.dropWhile(_ != '@').drop(1)))
      _invalid(s"Invalid $label identity: $value")

  private def _canonical_major(value: String): Boolean =
    Try(value.toInt).toOption.exists(major => major > 0 && value == major.toString)

  private def _canonical_vector(values: Vector[String], label: String): Unit = {
    values.foreach(value => _identity(value, label))
    if (values != values.distinct || values != values.sorted)
      _invalid(s"$label must be unique and canonical-order")
  }

  private def _unique(values: Vector[String], label: String): Unit =
    if (values.distinct.size != values.size)
      _invalid(s"Duplicate $label identities")

  private def _validate_bundle_graph(bundles: Map[String, ComponentCapabilityBundleDefinition]): Unit =
    bundles.keys.toVector.sorted.foreach { id =>
      _validate_capability_closure(
        _expand_bundles(bundles, Vector(id), Set.empty),
        s"component capability bundle $id closure"
      )
    }

  private def _expand_bundles(
    bundles: Map[String, ComponentCapabilityBundleDefinition],
    ids: Vector[String],
    ancestors: Set[String]
  ): Vector[String] = {
    _major_compatible(_bundle_closure(bundles, ids, ancestors), "component capability bundle closure")
    ids.flatMap { id =>
      if (ancestors.contains(id))
        _invalid(s"Cyclic component capability bundle reference: $id")
      val definition = bundles.getOrElse(id, _invalid(s"Unknown component capability bundle: $id"))
      _canonical_vector(definition.bundles, s"component capability bundle $id bundles")
      _canonical_vector(definition.capabilities, s"component capability bundle $id capabilities")
      _major_compatible(definition.bundles, s"component capability bundle $id bundles")
      _major_compatible(definition.capabilities, s"component capability bundle $id capabilities")
      _expand_bundles(bundles, definition.bundles, ancestors + id) ++ definition.capabilities
    }
  }

  private def _bundle_closure(
    bundles: Map[String, ComponentCapabilityBundleDefinition],
    ids: Vector[String],
    ancestors: Set[String]
  ): Vector[String] =
    ids.flatMap { id =>
      if (ancestors.contains(id))
        _invalid(s"Cyclic component capability bundle reference: $id")
      val definition = bundles.getOrElse(id, _invalid(s"Unknown component capability bundle: $id"))
      id +: _bundle_closure(bundles, definition.bundles, ancestors + id)
    }

  private def _major_compatible(values: Vector[String], label: String): Unit = {
    val incompatible = values.groupBy(_.takeWhile(_ != '@')).collect {
      case (identity, versions) if versions.distinct.size > 1 => identity
    }.toVector.sorted
    if (incompatible.nonEmpty)
      _invalid(s"$label contains version-incompatible identities: ${incompatible.mkString(", ")}")
  }

  private def _validate_capability_closure(values: Vector[String], label: String): Unit = {
    _unique(values, label)
    _major_compatible(values, label)
  }

  private def _only(root: JsObject, expected: Set[String], context: String): Unit = {
    val unknown = root.keys.filterNot(expected).toVector.sorted
    if (unknown.nonEmpty)
      _invalid(s"Unknown CNCF component style $context fields: ${unknown.mkString(", ")}")
  }

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString

  private def _invalid(message: String): Nothing =
    RAISE.invalidArgumentFault(message)
}
