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

private[cozy] trait CozyBokTagPages {
  self: CozyBokImplementation.type =>
  private[bok] def _terms(config: BuildConfig): Vector[TermEntry] =
    _term_index(config).map(_.terms).filter(_.nonEmpty).getOrElse(Vector.empty)

  private def _term_index(config: BuildConfig): Option[TermIndex] = {
    val paths = Vector(
      config.doxsitePath.resolve("metadata/glossary/terms.json"),
      config.sourcepath.resolve("metadata/glossary/terms.json")
    ).distinct
    val indexes = paths.flatMap(_read_term_index)
    indexes.find(_.terms.nonEmpty).orElse(indexes.headOption)
  }

  private def _read_term_index(path: Path): Option[TermIndex] =
    if (!Files.isRegularFile(path))
      None
    else
      parser.parse(Files.readString(path, StandardCharsets.UTF_8)).toOption.flatMap(_.as[TermIndex].toOption)

  private[bok] def _bibliography_index(config: BuildConfig): Option[BibliographyIndex] = {
    val path = config.doxsitePath.resolve("metadata/bibliography/bibliography.json")
    if (!Files.isRegularFile(path))
      None
    else
      parser.parse(Files.readString(path, StandardCharsets.UTF_8)).toOption.flatMap(_.as[BibliographyIndex].toOption)
  }

  private[bok] def _tag_index(config: BuildConfig, locale: String): TagIndex = {
    val usage = _usage_derived_tag_index(config, locale)
    _tag_handoff_index(config, locale).map(_merge_tag_indexes(_, usage)).getOrElse(usage)
  }

  private def _merge_tag_indexes(handoff: TagIndex, usage: TagIndex): TagIndex = {
    val handoffbykey = handoff.tags.map(x => x.key -> x).toMap
    val usagebykey = usage.tags.map(x => x.key -> x).toMap
    val entries = (handoffbykey.keySet ++ usagebykey.keySet).toVector.map { key =>
      (handoffbykey.get(key), usagebykey.get(key)) match {
        case (Some(handofftag), Some(usagetag)) =>
          handofftag.copy(
            refs = _distinct_tag_refs(handofftag.refs ++ usagetag.refs),
            children = (handofftag.children ++ usagetag.children).distinct.sorted
          )
        case (Some(handofftag), None) =>
          handofftag.copy(refs = _distinct_tag_refs(handofftag.refs))
        case (None, Some(usagetag)) =>
          usagetag.copy(refs = _distinct_tag_refs(usagetag.refs))
        case _ =>
          _tag_entry_from_usage(key, Vector.empty)
      }
    }.sortBy(x => (x.key, x.publicpath))
    TagIndex(entries)
  }

  private def _tag_handoff_index(config: BuildConfig, locale: String): Option[TagIndex] = {
    val path = config.doxsitePath.resolve("metadata/tags/tags.json")
    if (!Files.isRegularFile(path))
      None
    else
      parser.parse(Files.readString(path, StandardCharsets.UTF_8)).toOption.flatMap(_.as[TagIndex].toOption).map { index =>
        val tags = index.tags.
          filter(tag => tag.locale.forall(_ == locale)).
          map(tag => tag.copy(refs = _distinct_tag_refs(tag.refs))).
          sortBy(tag => (tag.key, tag.publicpath))
        TagIndex(tags)
      }
  }

  private def _usage_derived_tag_index(config: BuildConfig, locale: String): TagIndex = {
    val projects = _safe_resolved_project_packages(config)
    val documentrefs = _document_fragment_index(config).toVector.flatMap(_.fragments).filter(_.locale == locale).flatMap { fragment =>
      val title = fragment.effectiveHeadline.orElse(fragment.effectiveBrief).getOrElse(fragment.publicpath)
      _tag_refs(fragment.tags, TagReference(fragment.kind.getOrElse("article"), title, fragment.publicpath, fragment.category))
    }
    val termrefs = _terms(config).flatMap { term =>
      _tag_refs(term.tags, TagReference("term", term.title, term.publicpath, term.category))
    }
    val scenariorefs = _scenario_index(config).toVector.flatMap(_.scenarios).flatMap { scenario =>
      _tag_refs(scenario.tags, TagReference("scenario", scenario.title, scenario.publicpath, scenario.category))
    }
    val bibliographyrefs = _bibliography_index(config).toVector.flatMap(_.entries).flatMap { entry =>
      _tag_refs(entry.tags, TagReference("bibliography", entry.title, entry.publicpath, entry.category))
    }
    val projectrefs = projects.flatMap { project =>
      val category = Some(_project_category(config, project))
      _tag_refs(project.tags, TagReference("project", project.title, s"${project.publicationpath}/index.html", category))
    }
    val repositorycarrefs = _repository_car_index(config).entries.flatMap { entry =>
      val category = _repository_car_related_projects(entry, projects).headOption.map(_project_category(config, _))
      _tag_refs(entry.tags, TagReference("repository-car", entry.title, entry.publicPath, category))
    }
    val sieinformationrefs = _sie_index(config).informationinstances.flatMap { instance =>
      _tag_refs(
        instance.tags,
        TagReference(
          "sie-information",
          instance.label,
          s"rdf/node.html?id=${_url_query_escape(instance.graphNodeId)}",
          instance.category
        )
      )
    }
    val entries = (documentrefs ++ termrefs ++ scenariorefs ++ bibliographyrefs ++ projectrefs ++ repositorycarrefs ++ sieinformationrefs).
      groupBy(_._1).
      toVector.
      map { case (key, refs) =>
        val distinctrefs = _distinct_tag_refs(refs.map(_._2))
        _tag_entry_from_usage(key, distinctrefs)
      }.
      sortBy(x => (x.key, x.publicpath))
    TagIndex(entries)
  }

  private def _tag_refs(tags: Vector[String], ref: TagReference): Vector[(String, TagReference)] =
    tags.map(tag => _tag_key(tag, ref.category)).filter(_.nonEmpty).distinct.map(_ -> ref)

  private[bok] def _locale_website_root(config: BuildConfig, locale: String): Path =
    config.localeMode match {
      case LocaleMode.SingleLocaleRoot => config.websitePath
      case LocaleMode.MultiLocaleSubdirs => config.websitePath.resolve(locale)
    }

  private[bok] def _tag_chips(config: BuildConfig, page: Path, tags: Vector[String], category: Option[String], locale: String): String = {
    val target = _locale_website_root(config, locale)
    val chips = tags.map(tag => _tag_key(tag, category)).filter(_.nonEmpty).distinct.map { key =>
      val entry = _tag_entry_from_usage(key, Vector.empty)
      val href = _relative_href(page, target.resolve(entry.publicpath))
      s"""<a class="bok-tag-chip" href="${_html_escape(href)}"><span>${_html_escape(entry.effectiveTitle)}</span></a>"""
    }
    if (chips.isEmpty)
      ""
    else
      chips.mkString(s"""<div class="bok-tag-chip-list" aria-label="${_html_escape(_ui(locale, "tag.title"))}">""", "", "</div>")
  }

  private def _distinct_tag_refs(refs: Vector[TagReference]): Vector[TagReference] =
    refs.groupBy(x => (x.kind, x.href)).values.map(_.last).toVector.
      sortBy(x => (x.kind, x.category.getOrElse(""), x.title, x.href))

  private[bok] def _tag_entry_from_usage(key: String, refs: Vector[TagReference]): TagEntry = {
    val segments = key.split('.').toVector.filter(_.nonEmpty)
    val slug = segments.mkString("/")
    TagEntry(
      _tag_id(key),
      key,
      segments,
      segments.headOption,
      if (segments.size > 1) Some(s"tag:${segments.dropRight(1).mkString(".")}") else None,
      slug,
      segments.lastOption.getOrElse(key),
      Some(key),
      None,
      Vector.empty,
      None,
      None,
      s"tags/${slug}.html",
      None,
      refs,
      Vector.empty
    )
  }

  private def _tag_id(value: String): String =
    s"tag:${_tag_key(value)}"

  private[bok] def _tag_key(value: String): String =
    _tag_key(value, None)

  private[bok] def _tag_key(value: String, category: Option[String]): String = {
    val segments = value.trim.split("[./]+").toVector.map(_tag_segment).filter(_.nonEmpty)
    val normalized = segments.mkString(".")
    if (normalized.isEmpty)
      ""
    else if (segments.size > 1)
      normalized
    else
      category.map(c => Vector(_tag_segment(c), normalized).filter(_.nonEmpty).mkString(".")).filter(_.nonEmpty).getOrElse(normalized)
  }

  private[bok] def _tag_segment(value: String): String =
    value.trim.toLowerCase(Locale.ROOT).replaceAll("\\s+", "-").
      replaceAll("[\\\\/]+", "-").
      replaceAll("[^\\p{L}\\p{N}_-]+", "-").
      stripPrefix("-").
      stripSuffix("-")

  private def _tag_slug(value: String): String = {
    _tag_key(value).replace('.', '-') match {
      case "" => "tag"
      case x => x
    }
  }

  private[bok] def _tag_slug_path(value: String): String =
    value.trim.split("[./]+").toVector.map(_tag_segment).filter(_.nonEmpty).mkString("/") match {
      case "" => "tag"
      case x => x
    }

  private[bok] def _tag_dedicated_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    index: TagIndex,
    focus: Option[TagEntry]
  ): String =
    focus.map(tag => _tag_detail_page(config, categories, locale, page, tag)).getOrElse(
      _tag_index_page(config, categories, locale, page, index)
    )

  private def _tag_index_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    index: TagIndex
  ): String = {
    val title = _ui(locale, "tag.title")
    val description = _ui(locale, "tag.description")
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(title)} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale)}
       |<div class="body body-dashboard bok-tag-body">
       |  <main class="article bok-tag-main">
       |    <div class="content">
       |      <article class="doc bok-tag-doc">
       |        <section class="bok-dashboard-shell bok-tag-dashboard" id="dashboard">
       |          ${_dashboard_hero(
                    title,
                    description,
                    Vector(
                      _ui(locale, "tag.metric.total") -> index.tags.size.toString,
                      _ui(locale, "tag.metric.references") -> index.tags.map(_.count).sum.toString,
                      _ui(locale, "tag.metric.types") -> index.tags.flatMap(_.refs.map(_.kind)).distinct.size.toString
                    )
                  )}
       |          ${_tag_dashboard_body(config, page, locale, index)}
       |        </section>
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin
  }

  private def _tag_detail_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    tag: TagEntry
  ): String = {
    val title = _tag_display_title(tag)
    val description = tag.summary.getOrElse(_uif(locale, "tag.detail.description", title))
    val prefix = _site_root_prefix(config, page)
    val hierarchy = _tag_breadcrumb_items(prefix, tag, title)
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(title)} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale)}
       |<div class="body bok-tag-detail-body">
       |  ${_special_nav_container(config, categories, prefix)}
       |  <main class="article">
       |    <div class="toolbar" role="navigation">
       |      <button class="nav-toggle"></button>
       |      <a href="${_html_escape(prefix)}index.html" class="home-link"></a>
       |      <nav class="breadcrumbs" aria-label="breadcrumbs">
       |        <ul>
       |          <li><a href="${_html_escape(prefix)}index.html">${_html_escape(config.siteTitle)}</a></li>
       |          <li><a href="${_html_escape(prefix)}tags/index.html">${_html_escape(_ui(locale, "tag.title"))}</a></li>
       |          ${hierarchy}
       |        </ul>
       |      </nav>
       |    </div>
       |    <div class="content">
       |      ${_tag_detail_toc_panel(config, prefix, locale)}
       |      <article class="doc bok-tag-detail-doc">
       |        <header class="bok-tag-detail-header">
       |          <h1 class="page">${_html_escape(title)}</h1>
       |          <p class="bok-tag-detail-lead">${_html_escape(description)}</p>
       |          ${_tag_detail_properties(tag, locale)}
       |        </header>
       |        <section class="bok-tag-detail-section bok-tag-detail-purpose" id="purpose">
       |          <h2>${_html_escape(_ui(locale, "tag.detail.purpose"))}</h2>
       |          ${_tag_purpose_body(tag, locale)}
       |        </section>
       |        <section class="bok-tag-detail-section bok-tag-detail-links" id="links">
       |          <h2>${_html_escape(_ui(locale, "tag.detail.links"))}</h2>
       |          ${_tag_rdf_link(config, page, locale, tag)}
       |          ${_tag_refs_body(config, page, locale, tag.refs)}
       |        </section>
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin
  }

  private def _tag_breadcrumb_items(prefix: String, tag: TagEntry, title: String): String = {
    val namespace = tag.namespace.orElse(tag.segments.headOption).getOrElse("")
    val namespaceitem =
      if (namespace.isEmpty)
        ""
      else
        s"""<li><a href="${_html_escape(prefix)}tags/${_html_escape(namespace)}/index.html">${_html_escape(namespace)}</a></li>"""
    val intermediate = tag.segments.drop(1).dropRight(1).map { segment =>
      s"""<li>${_html_escape(_tag_segment_display_label(segment))}</li>"""
    }.mkString
    s"${namespaceitem}${intermediate}<li>${_html_escape(title)}</li>"
  }

  private[bok] def _tag_detail_properties(tag: TagEntry, locale: String): String =
    s"""<table class="tableblock frame-all grid-all stretch bok-tag-detail-properties">
       |  <tbody>
       |    <tr><th>${_html_escape(_ui(locale, "tag.detail.fqn"))}</th><td><code>${_html_escape(tag.key)}</code></td></tr>
       |  </tbody>
       |</table>""".stripMargin

  private[bok] def _tag_rdf_link(config: BuildConfig, page: Path, locale: String, tag: TagEntry): String = {
    val href = s"${_relative_href(page, config.websitePath.resolve("rdf/index.html"))}?tag=${_url_query_escape(tag.key)}"
    s"""<p class="bok-tag-rdf-link"><a href="${_html_escape(href)}">${_html_escape(_ui(locale, "rdf.graph.title"))}</a></p>"""
  }

  private def _tag_display_title(tag: TagEntry): String =
    tag.label.trim match {
      case "" => tag.segments.lastOption.getOrElse(tag.key)
      case x => x
    }

  private def _tag_segment_display_label(segment: String): String =
    segment.trim

  private def _tag_detail_toc_panel(config: BuildConfig, prefix: String, locale: String): String =
    s"""<aside class="toc sidebar" data-title="Contents" data-levels="2">
       |  <div class="toc-menu">
       |    <h3>On this page</h3>
       |    <ul>
       |      <li><a href="#purpose">${_html_escape(_ui(locale, "tag.detail.purpose"))}</a></li>
       |      <li><a href="#links">${_html_escape(_ui(locale, "tag.detail.links"))}</a></li>
       |    </ul>
       |    <div class="bok-special-links">
       |      <h3>BoK Console</h3>
       |      <a class="bok-special-link" href="${_html_escape(prefix)}tags/index.html">${_html_escape(_ui(locale, "tag.title"))}</a>
       |      <a class="bok-special-link" href="${_html_escape(prefix)}glossary/index.html">${_html_escape(_ui(locale, "glossary.title"))}</a>
       |      <a class="bok-special-link" href="${_html_escape(_history_href(config, prefix))}">${_html_escape(_ui(locale, "history.title"))}</a>
       |    </div>
       |  </div>
       |</aside>""".stripMargin

  private def _tag_dashboard_body(config: BuildConfig, page: Path, locale: String, index: TagIndex): String =
    if (index.isEmpty)
      s"""<div class="bok-dashboard container-fluid bok-dashboard-command-center">
         |  <div class="row g-3">
         |    ${_dashboard_card("col-12", "bok-card-map bok-card-tag-map", _ui(locale, "tag.title"), s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "tag.empty"))}</p>""", Vector("reader", "contributor", "project_manager"))}
         |  </div>
         |</div>""".stripMargin
    else {
      val tagtree = _tag_tree_body(config, page, locale, index.tags)
      val refs = _tag_overview_body(config, page, locale, index.tags)
      s"""<div class="bok-dashboard container-fluid bok-dashboard-command-center">
         |  <div class="row g-3">
         |    ${_dashboard_card("col-12 col-xl-4", "bok-card-map bok-card-tag-summary", _ui(locale, "tag.metric.summary"), tagtree, Vector("reader", "contributor", "project_manager"))}
         |    ${_dashboard_card("col-12 col-xl-8", "bok-card-map bok-card-tag-map", _ui(locale, "tag.title"), refs, Vector("reader", "contributor", "project_manager"))}
         |  </div>
         |</div>
         |<script>
         |(() => {
         |  const category = new URLSearchParams(window.location.search).get('category');
         |  if (!category) return;
         |  document.querySelectorAll('[data-tag-categories]').forEach((item) => {
         |    item.hidden = !item.dataset.tagCategories.split(' ').includes(category);
         |  });
         |  document.querySelectorAll('[data-tag-category]').forEach((item) => {
         |    item.hidden = item.dataset.tagCategory !== category;
         |  });
         |})();
         |</script>""".stripMargin
    }

  private def _tag_purpose_body(tag: TagEntry, locale: String): String =
    tag.bodyhtml.filter(_.trim.nonEmpty).map { html =>
      s"""<section class="bok-tag-definition">${html}</section>"""
    }.getOrElse(s"""<p>${_html_escape(tag.summary.getOrElse(_uif(locale, "tag.detail.description", tag.effectiveTitle)))}</p>""")

  private def _tag_tree_body(config: BuildConfig, page: Path, locale: String, tags: Vector[TagEntry]): String = {
    val target = _locale_website_root(config, locale)
    val bynamespace = tags.groupBy(tag => tag.segments.headOption.getOrElse(tag.namespace.getOrElse("")))
    val items = bynamespace.toVector.sortBy(_._1).map { case (namespace, xs) =>
      val namespacehref =
        if (namespace.isEmpty) "#"
        else _relative_href(page, target.resolve(s"tags/${namespace}/index.html"))
      val namespacebody = _tag_tree_children(page, target, xs, 1)
      s"""<li><a class="bok-tag-tree-namespace" href="${_html_escape(namespacehref)}">${_html_escape(if (namespace.isEmpty) "tags" else namespace)}</a>${namespacebody}</li>"""
    }.mkString
    s"""<nav class="bok-tag-tree" aria-label="tag tree"><ul>${items}</ul></nav>"""
  }

  private def _tag_tree_children(page: Path, target: Path, tags: Vector[TagEntry], depth: Int): String = {
    val branches = tags.filter(_.segments.length > depth).groupBy(_.segments(depth)).toVector.sortBy(_._1).map {
      case (segment, xs) =>
        val exact = xs.find(_.segments.length == depth + 1)
        val children = xs.filter(_.segments.length > depth + 1)
        val label = exact.map(_tag_display_title).getOrElse(_tag_segment_display_label(segment))
        if (children.isEmpty)
          exact.map { tag =>
            val href = _relative_href(page, target.resolve(tag.publicpath))
            s"""<li class="bok-tag-tree-leaf"><a href="${_html_escape(href)}">${_html_escape(label)}</a><span>${tag.count}</span></li>"""
          }.getOrElse("")
        else {
          val heading = exact.map { tag =>
            val href = _relative_href(page, target.resolve(tag.publicpath))
            s"""<a class="bok-tag-tree-segment" href="${_html_escape(href)}">${_html_escape(label)}</a><span>${tag.count}</span>"""
          }.getOrElse(s"""<span class="bok-tag-tree-segment">${_html_escape(label)}</span>""")
          s"""<li class="bok-tag-tree-branch"><div class="bok-tag-tree-branch-heading">${heading}</div>${_tag_tree_children(page, target, children, depth + 1)}</li>"""
        }
    }
    branches.mkString("<ul>", "", "</ul>")
  }

  private def _tag_overview_body(config: BuildConfig, page: Path, locale: String, tags: Vector[TagEntry]): String = {
    val target = _locale_website_root(config, locale)
    tags.take(40).map { tag =>
      val kindsummary = tag.refs.groupBy(_.kind).toVector.sortBy(_._1).map { case (kind, refs) =>
        s"${kind}: ${refs.size}"
      }.mkString(", ")
      val categories = tag.refs.flatMap(_.category).distinct.sorted.mkString(" ")
      val href = _relative_href(page, target.resolve(tag.publicpath))
      s"""<article class="bok-tag-tile" data-tag-categories="${_html_escape(categories)}">
         |  <h3><a href="${_html_escape(href)}">${_html_escape(_tag_display_title(tag))}</a></h3>
         |  <code>${_html_escape(tag.key)}</code>
         |  <p>${_html_escape(_uif(locale, "tag.reference.count", tag.count.toString))}</p>
         |  <code>${_html_escape(kindsummary)}</code>
         |</article>""".stripMargin
    }.mkString("""<div class="bok-tag-grid">""", "", "</div>")
  }

  private[bok] def _tag_refs_body(config: BuildConfig, page: Path, locale: String, refs: Vector[TagReference]): String = {
    val target = _locale_website_root(config, locale)
    refs.groupBy(_.kind).toVector.sortBy(_._1).map { case (kind, xs) =>
      val items = xs.take(20).map { ref =>
        val category = ref.category.map(x => s""" <span class="badge bok-badge-info">${_html_escape(x)}</span>""").getOrElse("")
        val categorydata = ref.category.map(x => s""" data-tag-category="${_html_escape(x)}"""").getOrElse("")
        val queryindex = ref.href.indexOf('?')
        val pathpart = if (queryindex < 0) ref.href else ref.href.substring(0, queryindex)
        val querypart = if (queryindex < 0) "" else ref.href.substring(queryindex)
        val href = _relative_href(page, target.resolve(pathpart)) + querypart
        s"""<li class="list-group-item"${categorydata}><a href="${_html_escape(href)}">${_html_escape(ref.title)}</a>${category}<span>${_html_escape(kind)}</span></li>"""
      }.mkString("\n")
      s"""<section class="bok-tag-reference-group">
         |  <h3>${_html_escape(_tag_kind_label(kind, locale))}</h3>
         |  <ul class="list-group bok-map-list">${items}</ul>
         |</section>""".stripMargin
    }.mkString("\n")
  }

  private def _tag_kind_label(kind: String, locale: String): String =
    kind match {
      case "term" => _ui(locale, "dashboard.kpi.terms")
      case "scenario" => _ui(locale, "scenario.title")
      case "bibliography" => _ui(locale, "bibliography.title")
      case "project" => _ui(locale, "project.title")
      case "repository-car" => _repository_car_title(locale)
      case "sie-information" => _ui(locale, "project.section.sie.information")
      case "article" | "document" => _ui(locale, "dashboard.kpi.articles")
      case other => other
    }

}
