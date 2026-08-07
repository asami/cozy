package cozy

import java.nio.file.Path

/*
 * @since   May. 20, 2026
 *  version Jul. 13, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
object RepositoryArtifactCatalog {
  def apply(
    schemaVersion: String,
    kind: String,
    artifactId: String,
    recommended: Option[String],
    latestStable: Option[String],
    latestSnapshot: Option[String],
    status: Option[String],
    aliases: Vector[String],
    versions: Vector[RepositoryArtifactCatalogVersion],
    tags: Vector[String] = Vector.empty,
    terms: Vector[String] = Vector.empty,
    namespace: Option[String] = None,
    id: Option[String] = None
  ): RepositoryArtifactCatalog =
    _root_.cozy.archive.RepositoryArtifactCatalog(
      schemaVersion,
      kind,
      artifactId,
      recommended,
      latestStable,
      latestSnapshot,
      status,
      aliases,
      versions,
      tags,
      terms,
      namespace,
      id
    )

  def unapply(value: RepositoryArtifactCatalog): Option[
    (String, String, String, Option[String], Option[String], Option[String], Option[String], Vector[String], Vector[RepositoryArtifactCatalogVersion])
  ] = {
    // Keep the established extractor arity while tags and terms remain additive fields.
    Option(value).map(x => (x.schemaVersion, x.kind, x.artifactId, x.recommended, x.latestStable, x.latestSnapshot, x.status, x.aliases, x.versions))
  }

  def load(path: Path): RepositoryArtifactCatalog =
    _root_.cozy.archive.RepositoryArtifactCatalog.load(path)

  def parse(text: String): RepositoryArtifactCatalog =
    _root_.cozy.archive.RepositoryArtifactCatalog.parse(text)

  def validate(catalog: RepositoryArtifactCatalog, sourcePath: Option[Path]): Unit =
    _root_.cozy.archive.RepositoryArtifactCatalog.validate(catalog, sourcePath)

  def toYaml(catalog: RepositoryArtifactCatalog): String =
    _root_.cozy.archive.RepositoryArtifactCatalog.toYaml(catalog)
}

object RepositoryArtifactCatalogVersion {
  def apply(
    version: String,
    channel: Option[String],
    status: Option[String],
    component: Option[String],
    publishedAt: Option[String],
    file: Option[String],
    runtime: Option[RepositoryArtifactRuntimeRequirement],
    checksumSha256: Option[String],
    integrityKey: Option[String] = None
  ): RepositoryArtifactCatalogVersion =
    _root_.cozy.archive.RepositoryArtifactCatalogVersion(
      version,
      channel,
      status,
      component,
      publishedAt,
      file,
      runtime,
      checksumSha256,
      integrityKey
    )

  def unapply(value: RepositoryArtifactCatalogVersion): Option[
    (String, Option[String], Option[String], Option[String], Option[String], Option[String], Option[RepositoryArtifactRuntimeRequirement], Option[String])
  ] =
    Option(value).map(x => (x.version, x.channel, x.status, x.component, x.publishedAt, x.file, x.runtime, x.checksumSha256))
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
