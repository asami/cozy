package cozy.archive

import cozy.config.CozyProjectYamlConfig
import java.nio.file.{Files, Path}

/*
 * @since   May. 20, 2026
 * @version May. 20, 2026
 * @author  ASAMI, Tomoharu
 */
object CozyCarPublisher {
  def publish(args: List[String]): Unit =
    RepositoryArtifactPublisher.publish(args, _policy)

  private val _policy = RepositoryArtifactPublisher.Policy(
    kind = "car",
    archiveOption = "car",
    missingProjectMessage = "Missing project directory for publish-car",
    missingArchiveMessage = "CAR archive does not exist",
    versionEntry = _version_entry,
    buildArchive = _build_temp_car
  )

  private def _build_temp_car(args: List[String]): Path = {
    val projectdir = RepositoryArtifactPublisher.projectDir(args, "Missing project directory for publish-car")
    val name = RepositoryArtifactPublisher.requiredValue(args, "name")
    val version = RepositoryArtifactPublisher.requiredValue(args, "version")
    val mainjar = RepositoryArtifactPublisher.requiredPath(args, "main-jar")
    val tempcar = Files.createTempFile("cozy-publish-car-", ".car")
    val buildargs =
      RepositoryArtifactPublisher.removePublishOnlyArgs(args, _publish_only_keys) ++
        Vector(
          s"--save=$tempcar",
          s"--project-dir=$projectdir",
          s"--name=$name",
          s"--version=$version",
          s"--main-jar=$mainjar"
        )
    CozyArchivePackager.buildCar(buildargs.toList)
    tempcar
  }

  private def _version_entry(
    version: String,
    channel: String,
    file: String,
    publishedcar: Path,
    args: List[String]
  ): RepositoryArtifactCatalogVersion = {
    val projectdir = RepositoryArtifactPublisher.projectDir(args, "Missing project directory for publish-car")
    val config = RepositoryArtifactPublisher.projectConfig(projectdir)
    val component = RepositoryArtifactPublisher.value(args, "component").
      orElse(config.value("packaging.car.manifest_metadata.component")).
      orElse(config.value("project.name")).
      getOrElse(RepositoryArtifactPublisher.requiredValue(args, "name"))
    RepositoryArtifactCatalogVersion(
      version = version,
      channel = Some(channel),
      status = Some(RepositoryArtifactPublisher.value(args, "status").getOrElse("active")),
      component = Some(component),
      publishedAt = Some(RepositoryArtifactPublisher.publishedAt(args)),
      file = Some(file),
      runtime = _runtime_requirement(config),
      checksumSha256 = Some(RepositoryArtifactPublisher.sha256(publishedcar))
    )
  }

  private def _runtime_requirement(config: CozyProjectYamlConfig.Config): Option[RepositoryArtifactRuntimeRequirement] = {
    val minimum = config.value("packaging.car.runtime.cncf.minimum")
    val maximum = config.value("packaging.car.runtime.cncf.maximum")
    val excluded = config.list("packaging.car.runtime.cncf.excluded")
    val tested = config.list("packaging.car.runtime.cncf.tested")
    if (minimum.isEmpty && maximum.isEmpty && excluded.isEmpty && tested.isEmpty)
      None
    else
      Some(RepositoryArtifactRuntimeRequirement(minimum, maximum, excluded, tested))
  }

  private val _publish_only_keys =
    Set("warehouse", "car", "name", "version", "channel", "status", "published-at", "recommended")
}
