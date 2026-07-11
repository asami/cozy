package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import play.api.libs.json._

/*
 * @since   Jul. 12, 2026
 * @version Jul. 12, 2026
 * @author  ASAMI, Tomoharu
 */
object ComponentApiDescriptor {
  def write(
    modelpath: Path,
    descriptorpath: Path,
    module: String,
    version: String
  ): Unit = {
    val normalizedmodule = Option(module).map(_.trim).filter(_.nonEmpty)
      .getOrElse(org.goldenport.RAISE.invalidArgumentFault("Component API descriptor requires component module"))
    val normalizedversion = Option(version).map(_.trim).filter(_.nonEmpty)
      .getOrElse(org.goldenport.RAISE.invalidArgumentFault("Component API descriptor requires component version"))
    val model = Json.parse(Files.readString(modelpath, StandardCharsets.UTF_8))
    val provided = (model \ "provided").as[Vector[JsObject]].map { api =>
      api ++ Json.obj(
        "version" -> normalizedversion,
        "artifactPath" -> s"spi/${normalizedmodule}-api.jar"
      )
    }
    val required = (model \ "required").as[Vector[JsObject]]
    val descriptor = Json.obj(
      "schemaVersion" -> "cncf.component-api.v1",
      "component" -> Json.obj(
        "name" -> normalizedmodule,
        "version" -> normalizedversion
      ),
      "provided" -> provided,
      "required" -> required
    )
    Option(descriptorpath.getParent).foreach(Files.createDirectories(_))
    Files.writeString(descriptorpath, Json.stringify(descriptor) + "\n", StandardCharsets.UTF_8)
    ()
  }
}
