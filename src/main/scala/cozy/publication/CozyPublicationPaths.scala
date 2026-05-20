package cozy.publication

import org.goldenport.RAISE

/*
 * @since   May. 20, 2026
 * @version May. 20, 2026
 * @author  ASAMI, Tomoharu
 */
private object CozyPublicationPaths {
  private val _slug_segment_pattern = "^[a-z0-9][a-z0-9-]*$".r
  private val _reserved_publication_roots = Set("metadata", "repository")

  def validatePublicationPath(value: String): String = {
    val path = value.trim.stripPrefix("/").stripSuffix("/")
    if (path.isEmpty || path.split('/').exists(segment => _slug_segment_pattern.findFirstIn(segment).forall(_ != segment)))
      RAISE.invalidArgumentFault(s"Invalid publication path: ${value}. Expected slash-separated slug segments")
    else if (_reserved_publication_roots.contains(path.split('/').headOption.getOrElse("")))
      RAISE.invalidArgumentFault(s"Invalid publication path: ${value}. Reserved top-level path: ${path.split('/').head}")
    else
      path
  }

  def downloadBase(publicationname: String, publicationpath: Option[String]): String =
    publicationpath.map(validatePublicationPath).getOrElse(s"samples/${publicationname}")

  def collectionDownloadPath(publicationname: String, publicationpath: Option[String], version: String): String =
    s"repository/download/${downloadBase(publicationname, publicationpath)}/${version}/${publicationname}-${version}.zip"

  def sampleDownloadPath(publicationname: String, publicationpath: Option[String], samplename: String, version: String): String =
    s"repository/download/${downloadBase(publicationname, publicationpath)}/${version}/${samplename}/${samplename}-${version}.zip"
}
