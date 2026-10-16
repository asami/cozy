package cozy.archive

import cozy.config.CozyProjectYamlConfig
import org.goldenport.RAISE

/*
 * @since   Oct. 17, 2026
 * @version Oct. 17, 2026
 */
private[cozy] object CozySourceBuildPackaging {
  def project(
    projectMetadata: CozyProjectYamlConfig.Config,
    sourceCncfVersion: Option[String],
    sourceCozyVersion: Option[String]
  ): CozyProjectYamlConfig.Config = (sourceCncfVersion, sourceCozyVersion) match {
    case (None, None) => projectMetadata
    case (Some(cncfinput), Some(cozyinput)) =>
      val cncfversion = _require_snapshot(Some(cncfinput), "CNCF")
      val cozyversion = _require_snapshot(Some(cozyinput), "Cozy")
      _require_snapshot(projectMetadata.value("project.component.version"), "component")
      val coordinates = projectMetadata.lists.getOrElse("build.dependencies.compile", Vector.empty)
      val selected = coordinates.zipWithIndex.filter { case (coordinate, _) => _is_cncf_coordinate(coordinate) }
      if (selected.size != 1)
        RAISE.invalidArgumentFault("Source CAR packaging requires exactly one canonical CNCF compile coordinate.")
      val (coordinate, index) = selected.head
      val revised = coordinates.updated(index, coordinate.substring(0, coordinate.lastIndexOf(':') + 1) + cncfversion)
      projectMetadata.copy(
        values = projectMetadata.values.updated("build.cozyVersion", cozyversion),
        lists = projectMetadata.lists.updated("build.dependencies.compile", revised)
      )
    case _ =>
      RAISE.invalidArgumentFault("Source CAR packaging requires --source-cncf-version and --source-cozy-version together.")
  }

  private def _require_snapshot(value: Option[String], label: String): String =
    value.map(_.trim).filter(version => version.nonEmpty && version.endsWith("-SNAPSHOT")).getOrElse(
      RAISE.invalidArgumentFault(s"Source CAR packaging requires a Snapshot $label version.")
    )

  private def _is_cncf_coordinate(coordinate: String): Boolean = {
    val parts = coordinate.split(":", -1).toList.map(_.trim)
    parts match {
      case organization :: "" :: artifact :: version :: Nil => _is_cncf_module(organization, artifact, version)
      case organization :: artifact :: version :: Nil => _is_cncf_module(organization, artifact, version)
      case _ => false
    }
  }

  private def _is_cncf_module(organization: String, artifact: String, version: String): Boolean =
    organization == "org.goldenport" &&
      (artifact == "goldenport-cncf" || artifact == "goldenport-cncf_3") && version.nonEmpty
}
