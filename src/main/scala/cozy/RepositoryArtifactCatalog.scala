package cozy

import java.nio.file.Path

/*
 * @since   May. 20, 2026
 * @version May. 20, 2026
 * @author  ASAMI, Tomoharu
 */
object RepositoryArtifactCatalog {
  def apply(
    schemaversion: String,
    kind: String,
    artifactid: String,
    recommended: Option[String],
    lateststable: Option[String],
    latestsnapshot: Option[String],
    status: Option[String],
    aliases: Vector[String],
    versions: Vector[RepositoryArtifactCatalogVersion]
  ): RepositoryArtifactCatalog =
    _root_.cozy.archive.RepositoryArtifactCatalog(
      schemaversion,
      kind,
      artifactid,
      recommended,
      lateststable,
      latestsnapshot,
      status,
      aliases,
      versions
    )

  def unapply(value: RepositoryArtifactCatalog): Option[
    (String, String, String, Option[String], Option[String], Option[String], Option[String], Vector[String], Vector[RepositoryArtifactCatalogVersion])
  ] =
    _root_.cozy.archive.RepositoryArtifactCatalog.unapply(value)

  def load(path: Path): RepositoryArtifactCatalog =
    _root_.cozy.archive.RepositoryArtifactCatalog.load(path)

  def parse(text: String): RepositoryArtifactCatalog =
    _root_.cozy.archive.RepositoryArtifactCatalog.parse(text)

  def validate(catalog: RepositoryArtifactCatalog, sourcepath: Option[Path]): Unit =
    _root_.cozy.archive.RepositoryArtifactCatalog.validate(catalog, sourcepath)

  def toYaml(catalog: RepositoryArtifactCatalog): String =
    _root_.cozy.archive.RepositoryArtifactCatalog.toYaml(catalog)
}

object RepositoryArtifactCatalogVersion {
  def apply(
    version: String,
    channel: Option[String],
    status: Option[String],
    component: Option[String],
    publishedat: Option[String],
    file: Option[String],
    runtime: Option[RepositoryArtifactRuntimeRequirement],
    checksumsha256: Option[String]
  ): RepositoryArtifactCatalogVersion =
    _root_.cozy.archive.RepositoryArtifactCatalogVersion(
      version,
      channel,
      status,
      component,
      publishedat,
      file,
      runtime,
      checksumsha256
    )

  def unapply(value: RepositoryArtifactCatalogVersion): Option[
    (String, Option[String], Option[String], Option[String], Option[String], Option[String], Option[RepositoryArtifactRuntimeRequirement], Option[String])
  ] =
    _root_.cozy.archive.RepositoryArtifactCatalogVersion.unapply(value)
}

object RepositoryArtifactRuntimeRequirement {
  def apply(
    minimum: Option[String],
    maximum: Option[String],
    excluded: Vector[String],
    tested: Vector[String]
  ): RepositoryArtifactRuntimeRequirement =
    _root_.cozy.archive.RepositoryArtifactRuntimeRequirement(
      minimum,
      maximum,
      excluded,
      tested
    )

  def unapply(value: RepositoryArtifactRuntimeRequirement): Option[(Option[String], Option[String], Vector[String], Vector[String])] =
    _root_.cozy.archive.RepositoryArtifactRuntimeRequirement.unapply(value)
}
