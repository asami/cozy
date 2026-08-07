package cozy.archive

import cozy.config.CozyProjectYamlConfig
import org.goldenport.RAISE
import org.goldenport.cncf.component.identity.{ComponentId, ComponentIdentityResult, ComponentLocalId, ComponentNamespace, ComponentReleaseCoordinate}
import play.api.libs.json.{JsObject, JsValue, Json}

/*
 * @since   Aug.  7, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
/** The sole Cozy boundary for the shared canonical CAR release coordinate. */
private[cozy] object CozyComponentReleaseCoordinateCodec {
  final case class Identity private (value: ComponentId) {
    def namespace: String = value.namespace().value()
    def id: String = value.localId().value()
    def qualifiedId: String = value.qualifiedName()
  }

  final case class Coordinate private (value: ComponentReleaseCoordinate) {
    def namespace: String = value.componentId().namespace().value()
    def id: String = value.componentId().localId().value()
    def version: String = value.release()
    def qualifiedId: String = value.qualifiedId()
    def dependencyKey: String = value.dependencyKey()
    def mavenArtifactId: String = value.mavenArtifactId()
    def mavenGroupId: String = value.mavenGroupId()
    def mavenReleaseKey: String = value.mavenReleaseKey()
    def groupPath: String = value.groupPath()
    def carFilename: String = value.carFilename()
    def carRepositoryRelativePath: String = value.carRepositoryRelativePath()
    def carCatalogRelativePath: String = value.carCatalogRelativePath()
    def carIndexKey: String = value.carIndexKey()
    def integrityKey(sha256: String): String = value.requireIntegrityKey(sha256)
    def apiArtifactPath: String = s"spi/${mavenArtifactId}-api.jar"
    def componentJson: JsObject = Json.obj(
      "namespace" -> namespace,
      "id" -> id,
      "version" -> version
    )
  }

  def fromProjectMetadata(metadata: CozyProjectYamlConfig.Config, source: String): Coordinate = {
    val namespace = metadata.value("project.namespace").orNull
    val id = metadata.value("project.id").orNull
    val version = metadata.value("project.component.version").orNull
    admit(namespace, id, version, source)
  }

  def admit(namespace: String, id: String, version: String, source: String): Coordinate = {
    val componentid = admitIdentity(namespace, id, source).value
    Coordinate(_result(ComponentReleaseCoordinate.create(componentid, version), source))
  }

  def admitIdentity(namespace: String, id: String, source: String): Identity = {
    val parsednamespace = _result(ComponentNamespace.parse(namespace), source)
    val parsedid = _result(ComponentLocalId.parse(id), source)
    Identity(ComponentId.of(parsednamespace, parsedid))
  }

  def readComponent(json: JsValue, source: String): Coordinate = {
    val component = (json \ "component").asOpt[JsObject].getOrElse(
      RAISE.invalidArgumentFault(s"component.release-coordinate.mismatch source=$source expected=component-object actual=missing")
    )
    val unexpected = component.keys.diff(Set("namespace", "id", "version")).toVector.sorted
    if (unexpected.nonEmpty || component.keys.size != 3)
      RAISE.invalidArgumentFault(
        s"component.release-coordinate.mismatch source=$source expected=component{namespace,id,version} actual=${component.keys.toVector.sorted.mkString(",")}"
      )
    admit(
      _required(component, "namespace", source),
      _required(component, "id", source),
      _required(component, "version", source),
      source
    )
  }

  def requireExact(expected: Coordinate, actual: Coordinate, source: String): Unit =
    if (expected.dependencyKey != actual.dependencyKey)
      RAISE.invalidArgumentFault(
        s"component.release-coordinate.mismatch source=$source expected=${expected.dependencyKey} actual=${actual.dependencyKey}"
      )

  def requireProjection(expected: String, actual: String, field: String, source: String): Unit =
    if (expected != actual)
      RAISE.invalidArgumentFault(
        s"component.release-coordinate.projection-mismatch source=$source field=$field expected=$expected actual=$actual"
      )

  private def _required(objectvalue: JsObject, key: String, source: String): String =
    (objectvalue \ key).asOpt[String].map(_.trim).filter(_.nonEmpty).getOrElse(
      RAISE.invalidArgumentFault(
        s"component.release-coordinate.mismatch source=$source expected=component.$key actual=missing"
      )
    )

  private def _result[A](result: ComponentIdentityResult[A], source: String): A =
    if (result.isSuccess()) result.value().get()
    else {
      val error = result.error().get()
      RAISE.invalidArgumentFault(s"${error.code()} source=$source ${error.message()}")
    }
}
