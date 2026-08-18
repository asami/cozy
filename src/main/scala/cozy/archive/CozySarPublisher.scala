package cozy.archive

import java.nio.file.{Files, Path}

/*
 * @since   May. 20, 2026
 * @version Jun.  4, 2026
 * @author  ASAMI, Tomoharu
 */
object CozySarPublisher {
  def publish(args: List[String]): Unit =
    RepositoryArtifactPublisher.publish(args, _policy)

  private val _policy = RepositoryArtifactPublisher.Policy(
    kind = "sar",
    archiveOption = "sar",
    missingProjectMessage = "Missing project directory for publish-sar",
    missingArchiveMessage = "SAR archive does not exist",
    versionEntry = _version_entry,
    buildArchive = _build_temp_sar
  )

  private def _build_temp_sar(args: List[String]): Path = {
    val projectdir = RepositoryArtifactPublisher.projectDir(args, "Missing project directory for publish-sar")
    val sourcedir = RepositoryArtifactPublisher.requiredPath(args, "source-dir")
    val workroot = projectdir.resolve("target/cozy-publish-sar")
    Files.createDirectories(workroot)
    val tempsar = Files.createTempFile(workroot, "cozy-publish-sar-", ".sar")
    val buildargs =
      RepositoryArtifactPublisher.removePublishOnlyArgs(args, _publish_only_keys) ++
        Vector(
          "--save", tempsar.toString,
          "--source-dir", sourcedir.toString
        )
    CozyArchivePackager.buildSar(buildargs.toList)
    tempsar
  }

  private def _version_entry(
    version: String,
    channel: String,
    file: String,
    publishedsar: Path,
    args: List[String]
  ): RepositoryArtifactCatalogVersion =
    RepositoryArtifactCatalogVersion(
      version = version,
      channel = Some(channel),
      status = Some(RepositoryArtifactPublisher.value(args, "status").getOrElse("active")),
      component = None,
      publishedAt = Some(RepositoryArtifactPublisher.publishedAt(args)),
      file = Some(file),
      runtime = None,
      checksumSha256 = Some(RepositoryArtifactPublisher.sha256(publishedsar))
    )

  private val _publish_only_keys =
    Set("warehouse", "sar", "name", "version", "channel", "status", "published-at", "recommended")
}
