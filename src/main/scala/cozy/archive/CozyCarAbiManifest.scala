package cozy.archive

import org.goldenport.RAISE
import play.api.libs.json._
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.util.Try

/*
 * @since   Jul. 15, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyCarAbiManifest {
  private val _model_metadata_schema = "cozy.cml.model-metadata.v1"

  final case class Dependency(
    identity: CozyComponentReleaseCoordinateCodec.Identity,
    abiRange: String
  ) {
    def toJson: JsObject =
      Json.obj(
        "namespace" -> identity.namespace,
        "id" -> identity.id,
        "abiRange" -> abiRange
      )
  }
  object Dependency {
    def apply(namespace: String, id: String, abiRange: String): Dependency =
      Dependency(CozyComponentReleaseCoordinateCodec.admitIdentity(namespace, id, "car-abi-dependency"), abiRange)

    /** Retained for source compatibility only; no namespace can be inferred from a legacy name. */
    def apply(name: String, abiRange: String): Dependency =
      RAISE.invalidArgumentFault(s"component.release-coordinate.mismatch source=car-abi-dependency expected=namespace,id actual=legacy-name:$name")
  }

  def create(
    paths: Vector[Path],
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    dependencies: Vector[Dependency] = Vector.empty
  ): String = {
    val metadata = paths.map(_metadata)
    val services = _merge_by_name(metadata.flatMap(_services), "service")
    val operations = _merge_operations(metadata.flatMap(_operations))
    val types = _merge_by_name(metadata.flatMap(_types), "type")
    val entities = _merge_by_name(metadata.flatMap(_entities), "entity")
    val abidependencies = _merge_dependencies(dependencies)
    Json.prettyPrint(Json.obj(
      "format" -> "cozy.car.abi-manifest.v2",
      "component" -> coordinate.componentJson,
      "abi" -> Json.obj(
        "version" -> 1,
        "exports" -> Json.obj(
          "components" -> Json.arr(Json.obj("namespace" -> coordinate.namespace, "id" -> coordinate.id)),
          "services" -> JsArray(services),
          "operations" -> JsArray(operations),
          "types" -> JsArray(types),
          "entities" -> JsArray(entities)
        ),
        "dependencies" -> JsArray(abidependencies.map(_.toJson))
      )
    ))
  }

  private[cozy] def _normalized_dependencies_json(dependencies: Vector[Dependency]): JsArray =
    JsArray(_merge_dependencies(dependencies).map(_.toJson))

  private[cozy] def _validate_source_manifest(
    json: JsValue,
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    source: String
  ): Unit = {
    val root = json.asOpt[JsObject].getOrElse(RAISE.invalidArgumentFault(s"$source ABI manifest must be a JSON object."))
    if (root.keys.contains("car"))
      RAISE.invalidArgumentFault(s"$source ABI manifest v2 forbids legacy car.")
    if ((root \ "format").asOpt[String] != Some("cozy.car.abi-manifest.v2"))
      RAISE.invalidArgumentFault(s"car.abi.schema.unsupported source=$source")
    val component = root.value.get("component").collect { case value: JsObject => value }.getOrElse(
      RAISE.invalidArgumentFault(s"$source ABI manifest v2 requires a component object.")
    )
    if (component.keys != Set("namespace", "id", "version"))
      RAISE.invalidArgumentFault(s"$source ABI manifest v2 component must be exact namespace, id, and version.")
    CozyComponentReleaseCoordinateCodec.requireExact(
      coordinate,
      CozyComponentReleaseCoordinateCodec.readComponent(root, source),
      source
    )
    val abi = root.value.get("abi").collect { case value: JsObject => value }.getOrElse(
      RAISE.invalidArgumentFault(s"$source ABI manifest must contain abi.")
    )
    if (abi.value.get("version") != Some(JsNumber(1)))
      RAISE.invalidArgumentFault(s"$source ABI manifest v2 requires numeric abi.version=1.")
    val exports = abi.value.get("exports").collect { case value: JsObject => value }.getOrElse(
      RAISE.invalidArgumentFault(s"$source ABI manifest must contain abi.exports.")
    )
    val components = exports.value.get("components").collect { case JsArray(values) => values.toVector }.getOrElse(
      RAISE.invalidArgumentFault(s"$source ABI manifest must contain abi.exports.components.")
    )
    val identities = components.map(_read_export_identity(_, source))
    if (!identities.exists(_.qualifiedId == coordinate.qualifiedId))
      RAISE.invalidArgumentFault(s"$source ABI manifest does not export component ${coordinate.qualifiedId}.")
    val dependencies = abi.value.get("dependencies").collect { case JsArray(values) => values.toVector }.getOrElse(
      RAISE.invalidArgumentFault(s"$source ABI manifest must contain abi.dependencies.")
    )
    _merge_dependencies(dependencies.map(_read_dependency(_, source)))
  }

  private def _read_export_identity(value: JsValue, source: String): CozyComponentReleaseCoordinateCodec.Identity = {
    val entry = value.asOpt[JsObject].getOrElse(RAISE.invalidArgumentFault(s"$source ABI export component must be an object."))
    if (entry.keys != Set("namespace", "id"))
      RAISE.invalidArgumentFault(s"$source ABI export component must be exact namespace and id.")
    CozyComponentReleaseCoordinateCodec.admitIdentity(
      _required_string(entry, "namespace", source),
      _required_string(entry, "id", source),
      source
    )
  }

  private def _read_dependency(value: JsValue, source: String): Dependency = {
    val entry = value.asOpt[JsObject].getOrElse(RAISE.invalidArgumentFault(s"$source ABI dependency must be an object."))
    if (entry.keys != Set("namespace", "id", "abiRange"))
      RAISE.invalidArgumentFault(s"$source ABI dependency must be exact namespace, id, and abiRange.")
    Dependency(
      _required_string(entry, "namespace", source),
      _required_string(entry, "id", source),
      _required_string(entry, "abiRange", source)
    )
  }

  /** Legacy overload deliberately fails closed: a local name cannot admit a namespace. */
  def create(
    paths: Vector[Path],
    name: String,
    version: String,
    component: String
  ): String =
    create(paths, name, version, component, Vector.empty)

  /** Legacy overload deliberately fails closed: a local name cannot admit a namespace. */
  def create(
    paths: Vector[Path],
    name: String,
    version: String,
    component: String,
    dependencies: Vector[Dependency]
  ): String =
    RAISE.invalidArgumentFault(
      s"component.release-coordinate.mismatch source=car-abi-manifest expected=canonical-coordinate actual=$name:$version:$component"
    )

  private def _services(metadata: JsValue): Vector[JsObject] =
    (metadata \ "surface" \ "component" \ "services").asOpt[JsArray].map(_.value.toVector).getOrElse(Vector.empty).map { service =>
      Json.obj("name" -> _required_string(service, "name", "service"))
    }

  private def _metadata(path: Path): JsValue = {
    val json = Try(Json.parse(Files.readString(path, StandardCharsets.UTF_8))).
      getOrElse(RAISE.invalidArgumentFault(s"Invalid CML model metadata JSON: ${path}"))
    val schema = (json \ "schema").asOpt[String].getOrElse("")
    if (schema != _model_metadata_schema)
      RAISE.invalidArgumentFault(s"CML model metadata ${path} must declare schema '${_model_metadata_schema}', but was '${schema}'.")
    json
  }

  private def _operations(metadata: JsValue): Vector[JsObject] = {
    val services = (metadata \ "surface" \ "component" \ "services").asOpt[JsArray].
      map(_.value.toVector).
      getOrElse(Vector.empty)
    services.flatMap { service =>
      val servicename = _required_string(service, "name", "service")
      (service \ "operations").asOpt[JsArray].map(_.value.toVector).getOrElse(Vector.empty).map(_operation(servicename, _))
    }
  }

  private def _operation(servicename: String, operation: JsValue): JsObject = {
    val name = _required_string(operation, "name", "operation")
    val kind = _required_string(operation, "operationType", s"operation '${name}'")
    val input = _required_string(operation, "inputType", s"operation '${name}'")
    val output = _required_string(operation, "outputType", s"operation '${name}'")
    Json.obj(
      "service" -> servicename,
      "name" -> name,
      "kind" -> kind,
      "input" -> input,
      "output" -> output
    )
  }

  private def _types(metadata: JsValue): Vector[JsObject] =
    (metadata \ "modelElements").asOpt[JsArray].map(_.value.toVector).getOrElse(Vector.empty).
      filterNot(x => (x \ "kind").asOpt[String].contains("entity")).
      map { modelelement =>
        val name = _required_string(modelelement, "name", "type")
        Json.obj(
          "name" -> name,
          "kind" -> _canonical_type_kind(_required_string(modelelement, "kind", s"type '${name}'")),
          "fields" -> JsArray(_fields(modelelement, s"type '${name}'"))
        )
      }

  private def _canonical_type_kind(p: String): String =
    p.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "command" | "query" => "value"
      case value => value
    }

  private def _entities(metadata: JsValue): Vector[JsObject] =
    (metadata \ "modelElements").asOpt[JsArray].map(_.value.toVector).getOrElse(Vector.empty).
      filter(x => (x \ "kind").asOpt[String].contains("entity")).
      map { entity =>
        val name = _required_string(entity, "name", "entity")
        Json.obj(
          "name" -> name,
          "fields" -> JsArray(_fields(entity, s"entity '${name}'"))
        )
      }

  private def _fields(modelelement: JsValue, owner: String): Vector[JsObject] =
    (modelelement \ "fields").asOpt[JsArray].map(_.value.toVector).getOrElse(Vector.empty).map { field =>
      val name = _required_string(field, "name", owner)
      val typename = _required_string(field, "type", s"${owner} field '${name}'")
      val multiplicity = _required_string(field, "multiplicity", s"${owner} field '${name}'")
      val required = (field \ "required").asOpt[Boolean].getOrElse(
        RAISE.invalidArgumentFault(s"CML model metadata ${owner} field '${name}' must declare boolean 'required'.")
      )
      Json.obj(
        "name" -> name,
        "type" -> typename,
        "multiplicity" -> multiplicity,
        "required" -> required
      )
    }

  private def _required_string(value: JsValue, key: String, owner: String): String =
    (value \ key).asOpt[String].map(_.trim).filter(_.nonEmpty).
      getOrElse(RAISE.invalidArgumentFault(s"CML model metadata ${owner} must declare non-empty '${key}'."))

  private def _merge_by_name(values: Vector[JsObject], kind: String): Vector[JsObject] =
    values.foldLeft(Vector.empty[JsObject]) { (z, value) =>
      val name = _required_string(value, "name", kind)
      z.find(x => (x \ "name").asOpt[String].contains(name)) match {
        case None => z :+ value
        case Some(existing) if existing == value => z
        case Some(_) =>
          RAISE.invalidArgumentFault(s"CML model metadata declares conflicting ${kind} '${name}' ABI surfaces.")
      }
    }

  private def _merge_operations(values: Vector[JsObject]): Vector[JsObject] =
    values.foldLeft(Vector.empty[JsObject]) { (z, value) =>
      val service = _required_string(value, "service", "operation")
      val name = _required_string(value, "name", s"service '${service}' operation")
      z.find { existing =>
        (existing \ "service").asOpt[String].contains(service) &&
          (existing \ "name").asOpt[String].contains(name)
      } match {
        case None => z :+ value
        case Some(existing) if existing == value => z
        case Some(_) =>
          RAISE.invalidArgumentFault(s"CML model metadata declares conflicting operation '${service}.${name}' ABI surfaces.")
      }
    }

  private def _merge_dependencies(values: Vector[Dependency]): Vector[Dependency] =
    values.foldLeft(Vector.empty[Dependency]) { (z, dependency) =>
      val key = dependency.identity.qualifiedId
      val abirange = dependency.abiRange.trim
      if (abirange.isEmpty)
        RAISE.invalidArgumentFault("CAR ABI dependencies must declare non-empty namespace, id, and abiRange values.")
      z.find(_.identity.qualifiedId == key) match {
        case None => z :+ dependency.copy(abiRange = abirange)
        case Some(existing) if existing.abiRange == abirange => z
        case Some(_) =>
          RAISE.invalidArgumentFault(s"CAR ABI declares conflicting dependency ranges for '$key'.")
      }
    }
}
