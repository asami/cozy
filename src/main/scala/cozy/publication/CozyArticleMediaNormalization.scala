package cozy.publication

import java.net.URI
import java.util.Locale
import org.goldenport.RAISE

/*
 * @since   Aug.  4, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArticleMediaNormalization {
  def normalizeArticleIdentity(articleIdentity: String): String = {
    val normalized = Option(articleIdentity).getOrElse("").trim.replace('\\', '/')
    val segments = normalized.split("/", -1).toVector
    if (normalized.isEmpty || normalized.startsWith("/") || segments.exists(_.isEmpty) ||
      segments.contains(".") || segments.contains("..") || segments.exists(!_is_valid_path_segment(_)))
      _invalid(s"Invalid article-media article identity: $articleIdentity")
    if (segments.headOption.exists(_is_locale_prefix))
      _invalid(s"Article-media article identity must not have a locale prefix: $articleIdentity")
    val identity = segments.mkString("/")
    if (identity.endsWith(".dox") || identity.endsWith(".html"))
      _invalid(s"Article-media article identity must not have a generated suffix: $articleIdentity")
    identity
  }

  def normalizeLocale(locale: String): String = {
    val raw = Option(locale).getOrElse("")
    if (raw.isEmpty || raw != raw.trim)
      _invalid(s"Invalid article-media locale: $locale")
    val canonical =
      try {
        new Locale.Builder().setLanguageTag(raw).build().toLanguageTag
      } catch {
        case _: java.util.IllformedLocaleException => _invalid(s"Invalid article-media locale: $locale")
      }
    if (canonical == "und" || canonical.isEmpty || raw != canonical)
      _invalid(s"Article-media locale must be canonical: $locale")
    canonical
  }

  def validateSiteVisibleUri(uri: URI, label: String): Unit = {
    if (uri == null)
      _invalid(s"$label must be a site-visible path")
    val value = Option(uri).map(_.toString).getOrElse("")
    val path = Option(uri).flatMap(x => Option(x.getPath)).getOrElse("")
    if (value.isEmpty || value != value.trim || uri.isAbsolute || uri.getAuthority != null ||
      !path.startsWith("/") || path.startsWith("//") || path == "/" ||
      path.split("/", -1).contains(".") || path.split("/", -1).contains(".."))
      _invalid(s"$label must be a site-visible path: $value")
  }

  def normalizeRelativePath(value: String, label: String): String = {
    val path = Option(value).getOrElse("")
    val segments = path.split("/", -1).toVector
    val isabsolute =
      try {
        new URI(path).isAbsolute
      } catch {
        case _: java.net.URISyntaxException => true
      }
    if (path.isEmpty || path != path.trim || path.startsWith("/") || path.contains('\\') || isabsolute ||
      segments.exists(_.isEmpty) || segments.contains(".") || segments.contains(".."))
      _invalid(s"$label must be a normalized relative path: $value")
    path
  }

  def requireTrimmed(value: String, label: String): String = {
    val normalized = Option(value).getOrElse("").trim
    if (normalized.isEmpty)
      _invalid(s"$label must be non-empty")
    normalized
  }

  def requireExactTrimmed(value: String, label: String): String = {
    val raw = Option(value).getOrElse("")
    if (raw.isEmpty || raw != raw.trim)
      _invalid(s"$label must be non-empty and trimmed")
    raw
  }

  private def _is_locale_prefix(value: String): Boolean = {
    val raw = Option(value).getOrElse("")
    if (raw.isEmpty || raw != raw.trim)
      false
    else
      try {
        val canonical = new Locale.Builder().setLanguageTag(raw).build().toLanguageTag
        canonical != "und" && canonical.nonEmpty && raw.equalsIgnoreCase(canonical) && {
          val language = Locale.forLanguageTag(canonical).getLanguage
          language == "en" || language == "ja"
        }
      } catch {
        case _: java.util.IllformedLocaleException => false
      }
  }

  private def _is_valid_path_segment(segment: String): Boolean =
    segment.matches("[A-Za-z0-9._-]+") && segment != "." && segment != ".."

  private def _invalid(message: String): Nothing =
    RAISE.invalidArgumentFault(message)
}
