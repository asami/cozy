package cozy.archive

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import scala.util.Try
import scala.xml.Utility

/*
 * @since   May. 20, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object RepositoryArtifactMavenMetadata {
  def toXml(catalog: RepositoryArtifactCatalog, fallbackPublishedAt: String): String = {
    val versions = _metadata_versions(catalog)
    val latest = _latest(catalog, versions)
    val release = _release(catalog, versions)
    val lastupdated = _last_updated(catalog, latest, fallbackPublishedAt)
    Vector(
      """<?xml version="1.0" encoding="UTF-8"?>""",
      "<metadata>",
      s"  <groupId>${_escape(catalog.namespace.getOrElse(s"org.simplemodeling.repository.${catalog.kind}"))}</groupId>",
      s"  <artifactId>${_escape(catalog.artifactId)}</artifactId>",
      "  <versioning>",
      latest.map(value => s"    <latest>${_escape(value)}</latest>").getOrElse(""),
      release.map(value => s"    <release>${_escape(value)}</release>").getOrElse(""),
      "    <versions>",
      versions.map(value => s"      <version>${_escape(value.version)}</version>").mkString("\n"),
      "    </versions>",
      s"    <lastUpdated>${_escape(lastupdated)}</lastUpdated>",
      "  </versioning>",
      "</metadata>"
    ).filter(_.nonEmpty).mkString("\n") + "\n"
  }

  private def _metadata_versions(catalog: RepositoryArtifactCatalog): Vector[RepositoryArtifactCatalogVersion] =
    catalog.versions.filterNot(_.effectiveStatus == "disabled")

  private def _latest(
    catalog: RepositoryArtifactCatalog,
    versions: Vector[RepositoryArtifactCatalogVersion]
  ): Option[String] =
    _selector_version(catalog.recommended, versions).
      orElse(_selector_version(catalog.latestStable, versions)).
      orElse(_selector_version(catalog.latestSnapshot, versions)).
      orElse(_newest_version(versions).map(_.version))

  private def _release(
    catalog: RepositoryArtifactCatalog,
    versions: Vector[RepositoryArtifactCatalogVersion]
  ): Option[String] =
    _selector_version(catalog.latestStable, versions)

  private def _selector_version(
    selector: Option[String],
    versions: Vector[RepositoryArtifactCatalogVersion]
  ): Option[String] =
    selector.filter(value => versions.exists(_.version == value))

  private def _newest_version(versions: Vector[RepositoryArtifactCatalogVersion]): Option[RepositoryArtifactCatalogVersion] =
    _sort_versions(versions).lastOption

  private def _sort_versions(versions: Vector[RepositoryArtifactCatalogVersion]): Vector[RepositoryArtifactCatalogVersion] =
    versions.sortBy(version => _version_sort_key(version.version))

  private def _version_sort_key(version: String): String =
    version.split("[.-]").toVector.map { part =>
      f"${Try(part.toInt).getOrElse(0)}%08d:$part"
    }.mkString("|")

  private def _last_updated(
    catalog: RepositoryArtifactCatalog,
    latest: Option[String],
    fallbackpublishedat: String
  ): String = {
    val publishedat =
      latest.flatMap(version => catalog.versions.find(_.version == version).flatMap(_.publishedAt)).
        getOrElse(fallbackpublishedat)
    _maven_timestamp(publishedat).getOrElse(_digits_only(publishedat).take(14).padTo(14, '0').mkString)
  }

  private def _maven_timestamp(value: String): Option[String] =
    try {
      Some(OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")))
    } catch {
      case _: Throwable => None
    }

  private def _digits_only(value: String): String =
    value.filter(_.isDigit)

  private def _escape(value: String): String =
    Utility.escape(value)
}
