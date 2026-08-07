package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import cozy.archive.CozyComponentReleaseCoordinateCodec
import play.api.libs.json._

/*
 * @since   Jul. 12, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
object ComponentApiDescriptor {
  /** Legacy entry point retained only for source compatibility; a module is not a namespace-qualified ID. */
  def write(
    modelPath: Path,
    descriptorPath: Path,
    module: String,
    version: String
  ): Unit =
    org.goldenport.RAISE.invalidArgumentFault(
      s"component.release-coordinate.mismatch source=component-api-generation expected=namespace,id actual=module:$module:$version"
    )

  def write(
    modelPath: Path,
    descriptorPath: Path,
    namespace: String,
    id: String,
    version: String
  ): Unit = {
    val coordinate = CozyComponentReleaseCoordinateCodec.admit(namespace, id, version, "component-api-generation")
    val model = Json.parse(Files.readString(modelPath, StandardCharsets.UTF_8))
    val provided = (model \ "provided").as[Vector[JsObject]].map { api =>
      api ++ Json.obj(
        "version" -> coordinate.version,
        "artifactPath" -> coordinate.apiArtifactPath
      )
    }
    val required = (model \ "required").as[Vector[JsObject]]
    val descriptor = Json.obj(
      "schemaVersion" -> "cncf.component-api.v2",
      "component" -> coordinate.componentJson,
      "provided" -> provided,
      "required" -> required
    )
    Option(descriptorPath.getParent).foreach(Files.createDirectories(_))
    Files.writeString(descriptorPath, Json.stringify(descriptor) + "\n", StandardCharsets.UTF_8)
    ()
  }
}
