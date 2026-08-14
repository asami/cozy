package cozy.bok

import org.goldenport.RAISE
import org.goldenport.cli.{Request => CliRequest}
import org.goldenport.cli.spec
import cozy.bok.scenario.ScenarioMetadata
import cozy.bok.BibliographyEntry._
import cozy.config.CozyProjectYamlConfig
import cozy.publication.{CozyArticleMediaBuildContext, CozyArticleMediaInfographicCommand, CozyArticleMediaInfographicEvidence, CozyArticleMediaVideoCommand}
import cozy.video.{CozyVideo, CozyVideoPublisher}
import org.smartdox.{Body, Document, Dox}
import org.smartdox.parser.Dox2Parser
import org.smartdox.transformers.Dox2HtmlTransformer
import org.smartdox.generator.{Context => SmartDoxContext}
import org.smartdox.metadata.DocumentMetaData
import org.goldenport.i18n.I18NContext
import java.net.URLEncoder
import java.time.{Instant, LocalDate, LocalDateTime, YearMonth, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import java.util.zip.{ZipEntry, ZipFile, ZipInputStream, ZipOutputStream}
import scala.collection.JavaConverters._
import scala.util.matching.Regex
import scala.util.control.NonFatal
import scala.sys.process._
import io.circe.{Decoder, HCursor, Json}
import io.circe.parser
import io.circe.syntax._

/*
 * @since   Aug. 14, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */

private[cozy] trait CozyBokBibliographyPages {
  self: CozyBokImplementation.type =>
  private[bok] def _bibliography_dedicated_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    entries: Vector[BibliographyEntry]
  ): String =
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(_ui(locale, "bibliography.title"))} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale)}
       |<div class="body body-dashboard bok-bibliography-body">
       |  <main class="article bok-bibliography-main">
       |    <div class="content">
       |      <article class="doc bok-bibliography-doc">
       |        <section class="bok-dashboard-shell bok-bibliography-dashboard" id="dashboard">
       |          ${_dashboard_hero(
                    _ui(locale, "bibliography.title"),
                    _ui(locale, "bibliography.description"),
                    Vector(
                      _ui(locale, "bibliography.metric.total") -> entries.size.toString,
                      _ui(locale, "bibliography.metric.types") -> entries.map(_.entrytype).distinct.size.toString,
                      _ui(locale, "bibliography.metric.unresolved") -> entries.count(_.needsresolution).toString
                    )
                  )}
       |          ${_bibliography_dashboard_body(locale, entries)}
       |        </section>
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin

  private def _bibliography_dashboard_body(locale: String, entries: Vector[BibliographyEntry]): String =
    if (entries.isEmpty)
      s"""<div class="bok-dashboard container-fluid bok-dashboard-command-center">
         |  <div class="row g-3">
         |    ${_dashboard_card("col-12", "bok-card-map bok-card-bibliography-map", _ui(locale, "bibliography.title"), s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "bibliography.empty"))}</p>""", Vector("reader", "contributor", "project_manager"))}
         |  </div>
         |</div>""".stripMargin
    else {
      val bytype = entries.groupBy(_.entrytype).toVector.sortBy(_._1)
      val metrics = bytype.map {
        case (kind, xs) =>
          s"""<div class="bok-metric-card"><div class="bok-metric-label">${_html_escape(kind)}</div><div class="bok-metric-value">${xs.size}</div><div class="bok-metric-note">${_html_escape(_ui(locale, "bibliography.metric.note"))}</div></div>"""
      }.mkString("""<div class="bok-dashboard-grid bok-bibliography-metrics">""", "", "</div>")
      val items = entries.take(40).map { entry =>
        val summary = entry.summary.map(x => s"""<p>${_html_escape(x)}</p>""").getOrElse("")
        val authors = if (entry.authors.isEmpty) "" else s"""<span>${_html_escape(entry.authors.mkString(", "))}</span>"""
        val identifiers = Vector(entry.identifiers.doi.map("DOI " + _), entry.identifiers.isbn.map("ISBN " + _), entry.identifiers.github.map("GitHub " + _)).flatten.mkString(", ")
        val ids = if (identifiers.isEmpty) "" else s"""<code>${_html_escape(identifiers)}</code>"""
        val source = entry.sourceurl.orElse(entry.identifiers.url).map(x => s"""<a href="${_html_escape(x)}">${_html_escape(_ui(locale, "bibliography.open.source"))}</a>""").getOrElse("")
        val resolution = if (entry.needsresolution) s"""<span class="badge bok-badge-warning">${_html_escape(_ui(locale, "bibliography.unresolved"))}</span>""" else ""
        s"""<article class="bok-bibliography-tile" data-bibliography-category="${_html_escape(entry.categorySlug)}">
           |  <div class="bok-bibliography-tile-head">
           |    <span>${_html_escape(entry.entrytype)}</span>
           |    <span class="badge bok-badge-info">${_html_escape(entry.sourcekind)}</span>
           |    ${resolution}
           |  </div>
           |  <h3><a href="../${_html_escape(entry.publicpath)}">${_html_escape(entry.title)}</a></h3>
           |  <div class="bok-bibliography-meta">${authors}${ids}</div>
           |  ${summary}
           |  <div class="bok-bibliography-actions">${source}</div>
           |</article>""".stripMargin
      }.mkString("""<div class="bok-bibliography-grid">""", "", "</div>")
      s"""<div class="bok-dashboard container-fluid bok-dashboard-command-center">
         |  <div class="row g-3">
         |    ${_dashboard_card("col-12 col-xl-4", "bok-card-kpi bok-card-bibliography-summary", _ui(locale, "bibliography.metric.summary"), metrics, Vector("reader", "contributor", "project_manager"))}
         |    ${_dashboard_card("col-12 col-xl-8", "bok-card-map bok-card-bibliography-map", _ui(locale, "bibliography.title"), items, Vector("reader", "contributor", "project_manager"))}
         |  </div>
         |</div>
         |<script>
         |(() => {
         |  const category = new URLSearchParams(window.location.search).get('category');
         |  if (!category) return;
         |  document.querySelectorAll('[data-bibliography-category]').forEach((item) => {
         |    item.hidden = item.dataset.bibliographyCategory !== category;
         |  });
         |})();
         |</script>""".stripMargin
    }

  private[bok] def _bibliography_entry_html_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    entry: BibliographyEntry
  ): String = {
    val rootprefix = _site_root_prefix(config, page)
    val body = _bibliography_entry_body(config, page, locale, entry, rootprefix)
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(entry.title)} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article">
       |${_category_header(config, categories, locale, rootprefix)}
       |<div class="body">
       |  <main class="article">
       |    <div class="toolbar" role="navigation">
       |      <button class="nav-toggle"></button>
       |      <a href="${_html_escape(rootprefix)}index.html" class="home-link"></a>
       |      <nav class="breadcrumbs" aria-label="breadcrumbs">
       |        <ul>
       |          <li><a href="${_html_escape(rootprefix)}index.html">${_html_escape(config.siteTitle)}</a></li>
       |          <li><a href="${_html_escape(rootprefix)}bibliography/index.html">${_html_escape(_ui(locale, "bibliography.title"))}</a></li>
       |          <li>${_html_escape(entry.title)}</li>
       |        </ul>
       |      </nav>
       |    </div>
       |    <div class="content">
       |      <article class="doc bok-bibliography-entry">
       |        <h1 class="page">${_html_escape(entry.title)}</h1>
       |        ${body}
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin
  }

  private def _bibliography_entry_body(config: BuildConfig, page: Path, locale: String, entry: BibliographyEntry, rootprefix: String): String = {
    val summary = entry.summary.map(x => s"""<p>${_html_escape(x)}</p>""").getOrElse("")
    val tagchips = _tag_chips(config, page, entry.tags, entry.category, locale)
    val body = if (entry.bodyhtml.trim.isEmpty) "" else s"""<section><h2>${_html_escape(_ui(locale, "bibliography.narrative"))}</h2>${entry.bodyhtml}</section>"""
    val citedby = _bibliography_cited_by_body(locale, entry, rootprefix)
    val source = entry.sourceurl.orElse(entry.identifiers.url).map { url =>
      s"""<a href="${_html_escape(url)}">${_html_escape(_ui(locale, "bibliography.open.source"))}</a>"""
    }.getOrElse("-")
    val resolution = if (entry.needsresolution) _ui(locale, "bibliography.unresolved") else "resolved"
    val identifiers = Vector(
      entry.identifiers.doi.map("DOI " + _),
      entry.identifiers.isbn.map("ISBN " + _),
      entry.identifiers.issn.map("ISSN " + _),
      entry.identifiers.url.map("URL " + _),
      entry.identifiers.github.map("GitHub " + _),
      entry.identifiers.wikidata.map("Wikidata " + _)
    ).flatten
    val authorbody = if (entry.authors.isEmpty) "-" else entry.authors.map(_html_escape).mkString(", ")
    val termbody = if (entry.terms.isEmpty) "-" else entry.terms.map(_html_escape).mkString(", ")
    val identifierbody = if (identifiers.isEmpty) "-" else identifiers.map(x => s"<code>${_html_escape(x)}</code>").mkString(" ")
    val rows = Vector(
      _ui(locale, "bibliography.metadata.id") -> s"<code>${_html_escape(entry.id)}</code>",
      _ui(locale, "bibliography.metadata.key") -> s"<code>${_html_escape(entry.key.getOrElse("-"))}</code>",
      _ui(locale, "bibliography.metadata.type") -> _html_escape(entry.entrytype),
      _ui(locale, "bibliography.metadata.source.kind") -> _html_escape(entry.sourcekind),
      _ui(locale, "bibliography.metadata.status") -> _html_escape(resolution),
      _ui(locale, "bibliography.metadata.authors") -> authorbody,
      _ui(locale, "bibliography.metadata.published") -> _html_escape(entry.publishedat.getOrElse("-")),
      _ui(locale, "bibliography.metadata.publisher") -> _html_escape(entry.publisher.getOrElse("-")),
      _ui(locale, "bibliography.metadata.identifiers") -> identifierbody,
      _ui(locale, "bibliography.metadata.terms") -> termbody,
      _ui(locale, "bibliography.metadata.source") -> source,
      _ui(locale, "bibliography.metadata.dashboard") ->
        s"""<a href="${_html_escape(rootprefix)}bibliography/index.html">${_html_escape(_ui(locale, "bibliography.title"))}</a>"""
    ).map { case (label, value) =>
      s"""<tr><th scope="row">${_html_escape(label)}</th><td>${value}</td></tr>"""
    }.mkString("\n")
    s"""${summary}
       |${tagchips}
       |<section>
       |  <h2>${_html_escape(_ui(locale, "bibliography.metadata"))}</h2>
       |  <table class="table bok-bibliography-metadata-table">
       |    <tbody>
       |${rows}
       |    </tbody>
       |  </table>
       |</section>
       |${citedby}
       |${body}""".stripMargin
  }

  private def _bibliography_cited_by_body(locale: String, entry: BibliographyEntry, rootprefix: String): String =
    if (entry.sourcerefs.isEmpty)
      ""
    else {
      val items = entry.sourcerefs.sortBy(x => (x.sourcepath, x.ordinal, x.citationkey)).map { ref =>
        val href = rootprefix + ref.publicpath
        val category = ref.category.map(x => s""" <span class="badge bok-badge-info">${_html_escape(x)}</span>""").getOrElse("")
        s"""<li><a href="${_html_escape(href)}">${_html_escape(ref.sourcepath)}</a>${category} <code>${_html_escape(ref.citationkey)}</code></li>"""
      }.mkString("\n")
      s"""<section class="bok-bibliography-cited-by">
         |  <h2>${_html_escape(_ui(locale, "bibliography.cited.by"))}</h2>
         |  <ul>${items}</ul>
         |</section>""".stripMargin
    }

}
