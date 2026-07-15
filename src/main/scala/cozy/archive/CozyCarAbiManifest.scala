package cozy.archive

import org.goldenport.RAISE
import play.api.libs.json._
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.util.Try

/*
 * @since   Jul. 15, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyCarAbiManifest {
  private val _model_metadata_schema = "cozy.cml.model-metadata.v1"

  def create(
    paths: Vector[Path],
    name: String,
    version: String,
    component: String
  ): String = {
    val metadata = paths.map(_metadata)
    val operations = _merge_by_name(metadata.flatMap(_operations), "operation")
    val entities = _merge_by_name(metadata.flatMap(_entities), "entity")
    Json.prettyPrint(Json.obj(
      "format" -> "cozy.car.abi-manifest.v1",
      "car" -> Json.obj(
        "name" -> name,
        "version" -> version
      ),
      "abi" -> Json.obj(
        "version" -> 1,
        "exports" -> Json.obj(
          "components" -> Json.arr(Json.obj("name" -> component)),
          "operations" -> JsArray(operations),
          "entities" -> JsArray(entities)
        ),
        "dependencies" -> Json.arr()
      )
    ))
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
      (service \ "operations").asOpt[JsArray].map(_.value.toVector).getOrElse(Vector.empty).map(_operation)
    }
  }

  private def _operation(operation: JsValue): JsObject = {
    val name = _required_string(operation, "name", "operation")
    val kind = _required_string(operation, "operationType", s"operation '${name}'")
    val input = _required_string(operation, "inputType", s"operation '${name}'")
    val output = _required_string(operation, "outputType", s"operation '${name}'")
    Json.obj(
      "name" -> name,
      "kind" -> kind,
      "input" -> input,
      "output" -> output
    )
  }

  private def _entities(metadata: JsValue): Vector[JsObject] =
    (metadata \ "modelElements").asOpt[JsArray].map(_.value.toVector).getOrElse(Vector.empty).
      filter(x => (x \ "kind").asOpt[String].contains("entity")).
      map { entity =>
        Json.obj(
          "name" -> _required_string(entity, "name", "entity"),
          "fields" -> Json.arr()
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
}
