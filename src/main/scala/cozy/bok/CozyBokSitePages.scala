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
import org.yaml.snakeyaml.{LoaderOptions, Yaml}
import org.yaml.snakeyaml.constructor.SafeConstructor

/*
 * @since   Aug. 14, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */

private[cozy] trait CozyBokSitePages {
  self: CozyBokImplementation.type =>
  private[bok] def _write_bok_pages(config: BuildConfig): Unit =
    config.localeMode match {
      case LocaleMode.SingleLocaleRoot =>
        _write_home_page(config, config.websitePath, config.defaultLocale)
        _write_special_pages(config, config.websitePath, config.defaultLocale, writelocalizedglossaryindexes = true)
        _write_category_pages(config, config.websitePath, config.defaultLocale)
      case LocaleMode.MultiLocaleSubdirs =>
        config.languages.foreach { lang =>
          val target = config.websitePath.resolve(lang)
          _write_home_page(config, target, lang)
          _write_special_pages(config, target, lang, writelocalizedglossaryindexes = false)
          _write_category_pages(config, target, lang)
        }
        // Machine-readable publication paths remain canonical across locale modes.
        _copy_machine_metadata_artifacts(config, config.websitePath)
    }

  private def _write_home_page(config: BuildConfig, target: Path, locale: String): Unit =
    {
      val page = target.resolve("index.html")
      if (!_arcadia_owns_page(config, page))
        _write_text(
        page,
      s"""<!doctype html>
         |<html lang="${_html_escape(locale)}">
         |<head>
         |  <meta charset="utf-8">
         |  <meta name="viewport" content="width=device-width, initial-scale=1">
         |  <title>${_html_escape(_uif(locale, "home.document.title", config.siteTitle))}</title>
         |${_site_css_links(config, page)}
         |</head>
         |<body class="article ${_html_escape(_dashboard_theme_class(config))}">
         |<header class="header">
         |  <nav class="navbar">
         |    <div class="navbar-brand">
         |      <a class="navbar-item" href="index.html">${_html_escape(config.siteTitle)}</a>
         |      <button class="navbar-burger" aria-controls="topbar-nav" aria-expanded="false" aria-label="Toggle main menu">
         |        <span></span>
         |        <span></span>
         |        <span></span>
         |      </button>
         |    </div>
         |    <div id="topbar-nav" class="navbar-menu">
         |      <div class="navbar-end">
         |        <a class="navbar-item" href="index.html">${_html_escape(_ui(locale, "nav.home"))}</a>
         |        ${_bok_nav_menu(config, locale, "")}
         |        ${_category_nav_menu(locale, _regular_category_summaries(config.sourcepath), "")}
         |      </div>
         |    </div>
         |  </nav>
         |</header>
         |<div class="body body-dashboard">
         |  <main class="article">
         |    <div class="toolbar" role="navigation">
         |      <button class="nav-toggle"></button>
         |      <a href="index.html" class="home-link is-current"></a>
         |      <nav class="breadcrumbs" aria-label="breadcrumbs">
         |        <ul>
         |          <li><a href="index.html">${_html_escape(config.siteTitle)}</a></li>
         |          <li>${_html_escape(_ui(locale, "dashboard"))}</li>
         |        </ul>
         |      </nav>
         |    </div>
         |    <div class="content">
         |      <article class="doc">
         |        ${_home_dashboard(config, locale)}
         |        ${_source_narrative_section(config, _source_document(config.sourcepath, "index"), locale)}
         |      </article>
         |    </div>
         |  </main>
         |</div>
         |</body>
         |</html>
         |""".stripMargin
      )
    }

  private def _write_category_pages(config: BuildConfig, target: Path, locale: String): Unit = {
    val categories = _category_contents(config.sourcepath)
    categories.foreach { category =>
      val page = target.resolve(category.slug).resolve("index.html")
      if (!_arcadia_owns_page(config, page))
        _write_text(
          page,
          _category_html_page(config, category, categories, locale, page)
        )
    }
  }

  private def _arcadia_owns_page(config: BuildConfig, page: Path): Boolean = {
    val website = config.websitePath.toAbsolutePath.normalize
    val destination = page.toAbsolutePath.normalize
    config.arcadia.enabled && destination.startsWith(website) &&
      Files.isRegularFile(config.arcadiaSitePath.resolve(website.relativize(destination)))
  }

  private def _write_special_pages(
    config: BuildConfig,
    target: Path,
    locale: String,
    writelocalizedglossaryindexes: Boolean
  ): Unit = {
    val categories = _category_contents(config.sourcepath)
    val terms = _terms(config)
    val glossarybody = _glossary_dashboard_body(config, categories, terms, _language_index_root_prefix(config), locale)
    _write_category_index_page(config, target, locale, categories)
    _write_article_page(config, target, locale, categories)
    _write_project_pages(config, target, locale, categories)
    _write_component_repository_page(config, target, locale, categories)
    _write_repository_car_page(config, target, locale, categories)
    _write_repository_sar_pages(config, target, locale, categories)
    _write_rdf_page(config, target, locale, categories)
    _write_scenario_page(config, target, locale, categories)
    _write_bibliography_page(config, target, locale, categories)
    _write_tag_pages(config, target, locale, categories)
    _write_term_hub_pages(config, target, locale, categories, terms)
    _write_text(
      target.resolve("glossary").resolve("index.html"),
      _glossary_dedicated_page(
        config,
        categories,
        locale,
        target.resolve("glossary").resolve("index.html"),
        glossarybody,
        terms
      )
    )
    val historypage = target.resolve("history").resolve("index.html")
    _write_text(
      historypage,
      _special_html_page_with_toc(
        config,
        categories,
        locale,
        historypage,
        _ui(locale, "history.title"),
        _ui(locale, "history.description"),
        _history_dashboard_body(locale),
        Vector("dashboard" -> "Dashboard", "timeline" -> "Timeline", "operation-notes" -> "Operation Notes")
      )
    )
    val manualpage = target.resolve("manual").resolve("index.html")
    val manualbody = _manual_body_with_anchors(
      _source_narrative_html(_manual_index(locale), "cozy-bok-manual.dox", locale)
    )
    _write_text(
      manualpage,
      _manual_html_page(
        config,
        categories,
        locale,
        manualpage,
        _ui(locale, "manual.title"),
        _ui(locale, "manual.description"),
        manualbody
      )
    )
    _post_process_knowledge_pages(config, target, locale, categories)
    if (writelocalizedglossaryindexes) {
      _write_text(
        target.resolve("ja").resolve("glossary").resolve("index.html"),
        _localized_glossary_index_page(config, categories, "ja")
      )
      _write_text(
        target.resolve("en").resolve("glossary").resolve("index.html"),
        _localized_glossary_index_page(config, categories, "en")
      )
    }
  }

  private def _post_process_knowledge_pages(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent]
  ): Unit = {
    _remove_category_index_nav_items(target, categories)
    _inject_antora_knowledge_tag_chips(config, target, locale, categories)
    _inject_antora_sie_term_links(config, target, locale)
    _inject_antora_sie_scenario_links(config, target, locale)
  }

  private def _remove_category_index_nav_items(target: Path, categories: Vector[CategoryContent]): Unit =
    categories.foreach { category =>
      val dir = target.resolve(category.slug)
      if (Files.isDirectory(dir)) {
        val stream = Files.walk(dir)
        try {
          stream.iterator.asScala.toVector.
            filter(Files.isRegularFile(_)).
            filter(_.getFileName.toString.endsWith(".html")).
            filterNot(_.getFileName.toString == "index.html").
            foreach { page =>
              val content = Files.readString(page, StandardCharsets.UTF_8)
              val title = Pattern.quote(_html_escape(category.title))
              val regex = ("""(?s)\s*<li class="nav-item" data-depth="1">\s*<a class="nav-link" href="index\.html">""" + title + """</a>\s*</li>""").r
              val updated = regex.replaceAllIn(content, "")
              if (updated != content)
                _write_text(page, updated)
            }
        } finally {
          stream.close()
        }
      }
    }

  private def _inject_antora_knowledge_tag_chips(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent]
  ): Unit = {
    val protectedpages = (Vector(target.resolve("index.html"), target.resolve("category/index.html")) ++
      categories.map(category => target.resolve(category.slug).resolve("index.html"))).
      map(_.toAbsolutePath.normalize).toSet
    val tagsbyhref = _tag_index(config, locale).tags.flatMap { tag =>
      tag.refs.collect {
        case ref if _is_antora_knowledge_tag_ref(ref) => ref.href -> tag
      }
    }.groupBy(_._1).map {
      case (href, xs) => href -> xs.map(_._2).distinct.sortBy(_.key)
    }
    tagsbyhref.foreach {
      case (href, tags) =>
        val page = target.resolve(href)
        if (Files.isRegularFile(page) &&
            !(protectedpages.contains(page.toAbsolutePath.normalize) && _arcadia_owns_page(config, page))) {
          val content = Files.readString(page, StandardCharsets.UTF_8)
          if (!content.contains("bok-knowledge-tag-chip-list")) {
            val chips = _knowledge_tag_chips_for_entries(target, page, tags, locale)
            val updated = _insert_after_page_title(content, chips)
            if (updated != content)
              _write_text(page, updated)
          }
        }
    }
  }

  private def _inject_antora_sie_term_links(config: BuildConfig, target: Path, locale: String): Unit = {
    val index = _sie_index(config)
    _terms(config).foreach { term =>
      val instances = _sie_information_for_term(index, term)
      val page = target.resolve(term.publicpath)
      if (instances.nonEmpty && Files.isRegularFile(page)) {
        val content = Files.readString(page, StandardCharsets.UTF_8)
        if (!content.contains("bok-term-sie-information")) {
          val section = _sie_information_section(config, page, locale, instances, "bok-term-sie-information")
          val updated = _insert_before_article_end(content, section)
          if (updated != content)
            _write_text(page, updated)
        }
      }
    }
  }

  private def _inject_antora_sie_scenario_links(config: BuildConfig, target: Path, locale: String): Unit = {
    val instances = _sie_index(config).informationinstances
    _scenario_index(config).toVector.flatMap(_.scenarios).foreach { scenario =>
      val related = instances.filter { instance =>
        instance.scenariorefs.exists { ref =>
          ref == scenario.id || ref == scenario.slug || ref == scenario.publicpath
        }
      }.sortBy(x => (x.label, x.id, x.projection))
      val page = target.resolve(scenario.publicpath)
      if (related.nonEmpty && Files.isRegularFile(page)) {
        val content = Files.readString(page, StandardCharsets.UTF_8)
        if (!content.contains("bok-scenario-sie-information")) {
          val section = _sie_information_section(config, page, locale, related, "bok-scenario-sie-information")
          val updated = _insert_before_article_end(content, section)
          if (updated != content)
            _write_text(page, updated)
        }
      }
    }
  }

  private def _is_antora_knowledge_tag_ref(ref: TagReference): Boolean =
    ref.kind == "article" || ref.kind == "document" || ref.kind == "term" || ref.kind == "scenario"

  private def _knowledge_tag_chips_for_entries(target: Path, page: Path, tags: Vector[TagEntry], locale: String): String = {
    val groups = tags.groupBy(_tag_parent_label).toVector.sortBy(_._1).map {
      case (namespace, entries) =>
        val links = entries.sortBy(_.key).map { tag =>
          val href = _relative_href(page, target.resolve(tag.publicpath))
          val label = tag.segments.lastOption.filter(_.nonEmpty).getOrElse(tag.label)
          s"""<a class="bok-knowledge-tag-leaf" href="${_html_escape(href)}">${_html_escape(label)}</a>"""
        }.mkString
        s"""<div class="bok-knowledge-tag-group"><span class="bok-knowledge-tag-namespace">${_html_escape(namespace)}</span><span class="bok-knowledge-tag-leaves">${links}</span></div>"""
    }.mkString
    s"""<div class="bok-knowledge-tag-bar bok-knowledge-tag-chip-list" aria-label="${_html_escape(_ui(locale, "tag.title"))}">${groups}</div>"""
  }

  private def _tag_parent_label(tag: TagEntry): String =
    tag.segments.dropRight(1).mkString(".") match {
      case "" => tag.namespace.getOrElse("tags")
      case x => x
    }

  private def _insert_after_page_title(content: String, html: String): String = {
    val heading = """(?s)(<h1 class="page"[^>]*>.*?</h1>)""".r
    heading.findFirstMatchIn(content).map { m =>
      heading.replaceFirstIn(content, Regex.quoteReplacement(m.group(1) + "\n" + html))
    }.getOrElse(content)
  }

  private def _insert_before_article_end(content: String, html: String): String = {
    val end = """(?s)</article>""".r
    end.findFirstMatchIn(content).map { _ =>
      end.replaceFirstIn(content, Regex.quoteReplacement(html + "\n</article>"))
    }.getOrElse(content + "\n" + html)
  }

  private def _write_rdf_page(config: BuildConfig, target: Path, locale: String, categories: Vector[CategoryContent]): Unit = {
    _copy_machine_metadata_artifacts(config, target)
    val page = target.resolve("rdf").resolve("index.html")
    _write_text(
      page,
      _rdf_dedicated_page(config, categories, locale, page)
    )
    val nodepage = target.resolve("rdf").resolve("node.html")
    _write_text(
      nodepage,
      _rdf_node_detail_page(config, categories, locale, nodepage)
    )
  }

  private def _write_category_index_page(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent]
  ): Unit = {
    val page = target.resolve("category").resolve("index.html")
    if (!_arcadia_owns_page(config, page))
      _write_text(
        page,
        _category_index_page(config, categories, locale, page)
      )
  }

  private def _category_index_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path
  ): String = {
    val dashboard = _dashboard(config)
    val articlecount = _source_article_count(config)
    val termcount = dashboard.map(_.counts.glossarytermcount).getOrElse(_terms(config).size)
    val rdfcount = dashboard.map(_.rdf.triplecount).getOrElse(0)
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(_ui(locale, "dashboard.kpi.categories"))} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale)}
       |<div class="body body-dashboard bok-category-index-body">
       |  <main class="article bok-category-index-main">
       |    <div class="content">
       |      <article class="doc bok-category-index-doc">
       |        <section class="bok-dashboard-shell bok-category-index-dashboard" id="dashboard">
       |          ${_dashboard_hero(
                    _ui(locale, "dashboard.kpi.categories"),
                    _ui(locale, "dashboard.kpi.categories.note"),
                    Vector(
                      _ui(locale, "dashboard.kpi.categories") -> categories.size.toString,
                      _ui(locale, "dashboard.kpi.articles") -> articlecount.toString,
                      _ui(locale, "dashboard.kpi.terms") -> termcount.toString,
                      _ui(locale, "dashboard.kpi.rdf.triples") -> rdfcount.toString
                    )
                  )}
       |          <div class="bok-dashboard container-fluid bok-dashboard-command-center">
       |            <div class="row g-3">
       |              ${_dashboard_card("col-12", "bok-card-matrix", _ui(locale, "dashboard.card.category.matrix"), dashboard.map(_category_matrix_body(config, locale, _, "../")).getOrElse(_category_source_matrix_body(locale, categories, "../")), Vector("reader", "contributor", "project_manager"))}
       |            </div>
       |          </div>
       |        </section>
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin
  }

  private def _write_article_page(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent]
  ): Unit = {
    val page = target.resolve("articles").resolve("index.html")
    _write_text(
      page,
      _article_index_page(config, categories, locale, page)
    )
  }

  private def _article_index_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path
  ): String = {
    val articles = _article_index_items(config, categories, locale)
    val articlecount = articles.size
    val categorycount = articles.map(_.categoryslug).distinct.size
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(_ui(locale, "dashboard.kpi.articles"))} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale)}
       |<div class="body body-dashboard bok-article-body">
       |  <main class="article bok-article-main">
       |    <div class="content">
       |      <article class="doc bok-article-doc">
       |        <section class="bok-dashboard-shell bok-article-dashboard" id="dashboard">
       |          ${_dashboard_hero(
                    _ui(locale, "dashboard.kpi.articles"),
                    _ui(locale, "dashboard.kpi.articles.note"),
                    Vector(
                      _ui(locale, "dashboard.kpi.articles") -> articlecount.toString,
                      _ui(locale, "dashboard.kpi.categories") -> categorycount.toString
                    )
                  )}
       |          ${_article_dashboard_body(locale, articles, _article_notice_media(config, locale))}
       |        </section>
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin
  }

  private def _article_index_items(config: BuildConfig, categories: Vector[CategoryContent], locale: String): Vector[ArticleIndexItem] = {
    val sourceitems = categories.flatMap { category =>
      category.articles.map { article =>
        ArticleIndexItem(
          category.slug,
          category.title,
          s"../${category.slug}/${article.href}",
          article.title,
          article.brief,
          TermExtraction.empty
        )
      }
    }
    val sourcehrefs = sourceitems.map(_.hreffromarticleindex).toSet
    val categorytitles = categories.map(x => x.slug -> x.title).toMap
    val fragmentitems = _document_fragment_index(config).toVector.flatMap(_.fragments).filter { fragment =>
      fragment.locale == locale &&
      fragment.category.nonEmpty &&
      _is_article_fragment_public_path(fragment.publicpath) &&
      !fragment.category.exists(category => _is_category_index_fragment_public_path(fragment.publicpath, category))
    }.map { fragment =>
      val category = fragment.category.get
      ArticleIndexItem(
        category,
        categorytitles.getOrElse(category, _titleize(category)),
        s"../${fragment.publicpath}",
        fragment.effectiveHeadline.getOrElse(_titleize(_source_document_stem(Paths.get(fragment.publicpath).getFileName.toString))),
        fragment.effectiveBrief.getOrElse(""),
        fragment.termextraction
      )
    }.filterNot(x => sourcehrefs.contains(x.hreffromarticleindex))
    (sourceitems ++ fragmentitems).sortBy(x => (x.categoryslug, x.title, x.hreffromarticleindex))
  }

  private def _is_category_index_fragment_public_path(value: String, category: String): Boolean =
    value.stripPrefix("/") == s"${category}/index.html"

  private def _is_article_fragment_public_path(value: String): Boolean = {
    val path = value.stripPrefix("/")
    path.endsWith(".html") &&
    !path.startsWith("glossary/") &&
    !path.startsWith("history/") &&
    !path.startsWith("manual/") &&
    !path.startsWith("scenario/") &&
    !path.startsWith("scenarios/") &&
    !path.startsWith("projects/") &&
    !path.startsWith("bibliography/") &&
    path != "index.html"
  }

  private final case class ArticleCardMediaLink(href: String, label: String, mediatype: Option[String])

  private def _article_notice_media(config: BuildConfig, locale: String): Map[String, Vector[ArticleCardMediaLink]] = {
    val directory = config.doxsitePath.resolve("WEB-INF/data").resolve(locale)
    if (!Files.isDirectory(directory))
      Map.empty
    else {
      val stream = Files.list(directory)
      try {
        val entries = stream.iterator.asScala.filter(path =>
          Files.isRegularFile(path) && path.getFileName.toString.matches("notice[0-9]+\\.yaml")
        ).toVector.sortBy(_.getFileName.toString).map { path =>
          try {
            val options = new LoaderOptions()
            options.setAllowDuplicateKeys(false)
            val notice = _article_notice_map(new Yaml(new SafeConstructor(options)).load[Any](Files.readString(path, StandardCharsets.UTF_8)), "Notice")
            val uri = _article_notice_string(notice, "notice.uri").getOrElse(
              throw new IllegalArgumentException("Missing notice.uri")
            )
            val links = notice.get("notice.media").map { value =>
              val media = _article_notice_map(value, "notice.media")
              val pdfs = Vector(
                "article_pdf" -> (if (locale == "ja") "記事 PDF" else "Article PDF"),
                "summary_slides_pdf" -> (if (locale == "ja") "要約スライド PDF" else "Summary slides PDF")
              ).flatMap { case (role, defaultlabel) =>
                media.get(role).flatMap { value =>
                  val pdf = _article_notice_map(value, role)
                  val href = _article_notice_string(pdf, "public_path").getOrElse(throw new IllegalArgumentException(s"Missing $role public_path"))
                  if (!_article_notice_string(pdf, "media_type").contains("application/pdf"))
                    throw new IllegalArgumentException(s"$role media_type must be application/pdf")
                  val label = _article_notice_string(pdf, "label").getOrElse(defaultlabel)
                  _article_media_safe_url(href).map(ArticleCardMediaLink(_, label, Some("application/pdf")))
                }
              }
              val video = media.get("video").flatMap { value =>
                val item = _article_notice_map(value, "video")
                if (!_article_notice_string(item, "status").contains("published")) None
                else {
                  val key = _article_notice_string(item, "presentation") match {
                    case Some("external-link") => "watch_url"
                    case Some("site-hosted") => "content_url"
                    case _ => throw new IllegalArgumentException("Unknown published video presentation")
                  }
                  val href = _article_notice_string(item, key).getOrElse(throw new IllegalArgumentException(s"Missing video $key"))
                  _article_media_safe_url(href).map(ArticleCardMediaLink(_, if (locale == "ja") "動画" else "Video", None))
                }
              }
              pdfs ++ video.toVector
            }.getOrElse(Vector.empty)
            uri.stripPrefix("/") -> links
          } catch {
            case NonFatal(error) => throw new IllegalArgumentException(s"Malformed article media Notice $path: ${error.getMessage}", error)
          }
        }
        if (entries.map(_._1).distinct.size != entries.size)
          throw new IllegalArgumentException(s"Duplicate article URI in native Notices: $directory")
        entries.toMap
      } finally stream.close()
    }
  }

  private def _article_notice_map(value: Any, field: String): Map[String, Any] = value match {
    case map: java.util.Map[_, _] => map.asScala.map {
      case (key: String, item) => key -> item
      case _ => throw new IllegalArgumentException(s"$field keys must be strings")
    }.toMap
    case _ => throw new IllegalArgumentException(s"$field must be a mapping")
  }

  private def _article_notice_string(value: Map[String, Any], field: String): Option[String] =
    value.get(field).map {
      case text: String if text.trim.nonEmpty => text
      case _ => throw new IllegalArgumentException(s"$field must be a nonblank string")
    }

  private def _article_media_safe_url(value: String): Option[String] = {
    try {
      val uri = new java.net.URI(value)
      val safe = !value.exists(c => c.isControl || c.isWhitespace || c == '\\') &&
        ((value.startsWith("/") && !value.startsWith("//") && uri.getRawAuthority == null && uri.getScheme == null) ||
          (Option(uri.getScheme).exists(_.equalsIgnoreCase("https")) && uri.getHost != null))
      if (safe) Some(value) else None
    } catch { case NonFatal(_) => None }
  }

  private def _article_dashboard_body(locale: String, articles: Vector[ArticleIndexItem], media: Map[String, Vector[ArticleCardMediaLink]]): String = {
    val progress = _term_extraction_progress_card(
      locale,
      articles.count(x => x.termextraction.isPlanned && _is_term_extraction_done(x.termextraction)),
      articles.count(_.termextraction.isPlanned)
    )
    val articlecards = articles.map { article =>
      val brief = if (article.brief.trim.isEmpty) "" else s"""<p>${_html_escape(article.brief)}</p>"""
      val medialinks = media.getOrElse(article.hreffromarticleindex.stripPrefix("../").stripPrefix("/"), Vector.empty).map { link =>
        val mediatype = link.mediatype.map(x => s""" type="${_html_escape(x)}"""").getOrElse("")
        s"""<li><a href="${_html_escape(link.href)}"${mediatype}>${_html_escape(link.label)}</a></li>"""
      }
      val mediahtml = if (medialinks.isEmpty) "" else medialinks.mkString("<ul class=\"bok-article-media-links\">", "", "</ul>")
      s"""<article class="bok-article-tile" data-article-category="${_html_escape(article.categoryslug)}">
         |  <div class="bok-article-tile-head">
         |    <span class="badge bok-badge-info">${_html_escape(article.categorytitle)}</span>
         |  </div>
         |  <h3><a href="${_html_escape(article.hreffromarticleindex)}">${_html_escape(article.title)}</a></h3>
         |  ${brief}
         |  ${mediahtml}
         |</article>""".stripMargin
    }
    val body =
      if (articlecards.isEmpty)
        s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "dashboard.article.empty"))}</p>"""
      else
        articlecards.mkString("""<div class="bok-article-grid">""", "\n", "</div>")
    s"""<div class="bok-dashboard container-fluid bok-dashboard-command-center">
       |  <div class="row g-3">
       |    ${_dashboard_card("col-12 col-xl-4", "bok-card-kpi bok-card-term-extraction-progress", _ui(locale, "term.extraction.progress.title"), progress, Vector("contributor", "project_manager"))}
       |    ${_dashboard_card("col-12", "bok-card-map bok-card-article-map", _ui(locale, "dashboard.card.article.map"), body, Vector("reader", "contributor", "project_manager"))}
       |  </div>
       |</div>
       |<script>
       |(() => {
       |  const category = new URLSearchParams(window.location.search).get('category');
       |  if (!category) return;
       |  document.querySelectorAll('[data-article-category]').forEach((item) => {
       |    item.hidden = item.dataset.articleCategory !== category;
       |  });
       |})();
       |</script>""".stripMargin
  }

  private def _write_scenario_page(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent]
  ): Unit = {
    val scenarios = _scenario_index(config).map(_.scenarios).getOrElse(Vector.empty)
    val page = target.resolve("scenarios").resolve("index.html")
    _write_text(
      page,
      _scenario_dedicated_page(
        config,
        categories,
        locale,
        page,
        scenarios
      )
    )
  }

  private def _write_bibliography_page(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent]
  ): Unit = {
    val entries = _bibliography_index(config).map(_.entries).getOrElse(Vector.empty)
    val page = target.resolve("bibliography").resolve("index.html")
    _write_text(
      page,
      _bibliography_dedicated_page(
        config,
        categories,
        locale,
        page,
        entries
      )
    )
    _write_bibliography_entry_pages(config, target, locale, categories, entries)
  }

  private def _write_bibliography_entry_pages(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent],
    entries: Vector[BibliographyEntry]
  ): Unit =
    entries.foreach { entry =>
      val page = target.resolve(entry.publicpath)
      _write_text(
        page,
        _bibliography_entry_html_page(
          config,
          categories,
          locale,
          page,
          entry
        )
      )
    }

  private def _write_tag_pages(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent]
  ): Unit = {
    val index = _tag_index(config, locale)
    val page = target.resolve("tags").resolve("index.html")
    _write_text(
      page,
      _tag_dedicated_page(config, categories, locale, page, index, None)
    )
    index.namespaces.foreach { namespace =>
      val namespacepage = target.resolve("tags").resolve(namespace).resolve("index.html")
      val namespaceindex = TagIndex(index.tags.filter(_.namespace.contains(namespace)))
      _write_text(
        namespacepage,
        _tag_dedicated_page(config, categories, locale, namespacepage, namespaceindex, None)
      )
    }
    index.tags.foreach { tag =>
      val tagpage = target.resolve(tag.publicpath)
      if (tag.sourcepath.exists(_.startsWith("tags/")) && Files.isRegularFile(tagpage))
        _post_process_tag_antora_page(config, tagpage, locale, tag)
      else
        _write_text(
          tagpage,
          _tag_dedicated_page(config, categories, locale, tagpage, TagIndex(Vector(tag)), Some(tag))
        )
    }
  }

  private def _post_process_tag_antora_page(
    config: BuildConfig,
    page: Path,
    locale: String,
    tag: TagEntry
  ): Unit = {
    val content = Files.readString(page, StandardCharsets.UTF_8)
    val withproperties =
      if (content.contains("bok-tag-detail-properties"))
        content
      else
        _insert_after_page_title(content, _tag_detail_properties(tag, locale))
    val updated =
      if (withproperties.contains("bok-tag-detail-links"))
        withproperties
      else {
        val links =
          s"""<section class="bok-tag-detail-section bok-tag-detail-links" id="links">
             |  <h2>${_html_escape(_ui(locale, "tag.detail.links"))}</h2>
             |  ${_tag_rdf_link(config, page, locale, tag)}
             |  ${_tag_refs_body(config, page, locale, tag.refs)}
             |</section>""".stripMargin
        _insert_before_article_end(withproperties, links)
      }
    if (updated != content)
      _write_text(page, updated)
  }

}
