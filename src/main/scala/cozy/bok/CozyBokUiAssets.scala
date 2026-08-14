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

private[cozy] trait CozyBokUiAssets {
  self: CozyBokImplementation.type =>
  private[bok] def _default_ui_layout(): String =
    """<!doctype html>
      |<html lang="{{site.keys.lang}}">
      |<head>
      |  <meta charset="utf-8">
      |  <meta name="viewport" content="width=device-width, initial-scale=1">
      |  <title>{{page.title}} - {{site.title}}</title>
      |  <link rel="stylesheet" href="{{uiRootPath}}/css/bootstrap-grid.min.css">
      |  <link rel="stylesheet" href="{{uiRootPath}}/css/site.css">
      |  <link rel="stylesheet" href="{{uiRootPath}}/css/cozy-bok-dashboard.css">
      |</head>
      |<body class="article">
      |  {{> header-content}}
      |  <div class="body">
      |{{> nav}}
      |    <main class="article">
      |      <div class="toolbar" role="navigation">
      |        <button class="nav-toggle"></button>
      |        <a href="{{siteRootPath}}/index.html" class="home-link{{#if page.home}} is-current{{/if}}"></a>
      |        <nav class="breadcrumbs" aria-label="breadcrumbs">
      |          <ul>
      |            <li><a href="{{siteRootPath}}/index.html">{{site.title}}</a></li>
      |            <li>{{page.title}}</li>
      |          </ul>
      |        </nav>
      |      </div>
      |      <div class="content">
      |        <aside class="toc sidebar" data-title="Contents" data-levels="2">
      |          <div class="toc-menu"></div>
      |        </aside>
      |        <article class="doc">
      |          <h1 class="page">{{page.title}}</h1>
      |          {{{page.contents}}}
      |        </article>
      |      </div>
      |    </main>
      |  </div>
      |  {{> footer-content}}
      |  <script src="{{uiRootPath}}/js/site.js"></script>
      |</body>
      |</html>
      |""".stripMargin

  private[bok] def _default_ui_nav(): String =
    """    <div class="nav-container"{{#if page.component}} data-component="{{page.component.name}}" data-version="{{page.version}}"{{/if}}>
      |      <aside class="nav">
      |        <div class="panels">
      |{{> nav-menu}}
      |        </div>
      |      </aside>
      |    </div>
      |""".stripMargin

  private[bok] def _default_ui_nav_menu(): String =
    """{{#with page.navigation}}
      |          <div class="nav-panel-menu is-active" data-panel="menu">
      |            <nav class="nav-menu">
      |              <button class="nav-menu-toggle" aria-label="Toggle expand/collapse all" style="display: none"></button>
      |              {{#with @root.page.componentVersion}}
      |              <h3 class="title"><a href="{{{relativize ./url}}}">{{./title}}</a></h3>
      |              {{/with}}
      |{{> nav-tree navigation=this}}
      |            </nav>
      |          </div>
      |{{/with}}
      |""".stripMargin

  private[bok] def _default_ui_nav_tree(): String =
    """{{#if navigation.length}}
      |              <ul class="nav-list">
      |                {{#each navigation}}
      |                <li class="nav-item{{#if (eq ./url @root.page.url)}} is-current-page{{/if}}" data-depth="{{or ../level 0}}">
      |                  {{#if ./content}}
      |                  {{#if ./items.length}}
      |                  <button class="nav-item-toggle"></button>
      |                  {{/if}}
      |                  {{#if ./url}}
      |                  <a class="nav-link" href="
      |                    {{~#if (eq ./urlType 'internal')}}{{{relativize ./url}}}
      |                    {{~else}}{{{./url}}}{{~/if}}">{{{./content}}}</a>
      |                  {{else}}
      |                  <span class="nav-text">{{{./content}}}</span>
      |                  {{/if}}
      |                  {{/if}}
      |{{> nav-tree navigation=./items level=(increment ../level)}}
      |                </li>
      |                {{/each}}
      |              </ul>
      |{{/if}}
      |""".stripMargin

  private[bok] def _default_ui_header(): String =
    """<header class="header">
      |  <nav class="navbar">
      |    <div class="navbar-brand">
      |      <a class="navbar-item" href="{{{or site.url siteRootPath}}}/">{{site.title}}</a>
      |      <button class="navbar-burger" aria-controls="topbar-nav" aria-expanded="false" aria-label="Toggle main menu">
      |        <span></span>
      |        <span></span>
      |        <span></span>
      |      </button>
      |    </div>
      |    <div id="topbar-nav" class="navbar-menu">
      |      <div class="navbar-end">
      |        <a class="navbar-item" href="{{siteRootPath}}/index.html">Home</a>
      |        <div class="navbar-item has-dropdown is-hoverable navbar-bok-nav navbar-bok-dropdown" aria-label="BoK">
      |          <a class="navbar-link navbar-bok-toggle" href="#">BoK</a>
      |          <div class="navbar-dropdown navbar-bok-menu">
      |            <a class="navbar-item navbar-dropdown-item" href="{{siteRootPath}}/glossary/index.html">Glossary</a>
      |            <a class="navbar-item navbar-dropdown-item" href="{{siteRootPath}}/history/index.html">History</a>
      |            <a class="navbar-item navbar-dropdown-item" href="{{siteRootPath}}/manual/index.html">BoK Manual</a>
      |          </div>
      |        </div>
      |      </div>
      |    </div>
      |  </nav>
      |</header>
      |""".stripMargin

  private[bok] def _default_ui_header(config: BuildConfig): String = {
    val locale = config.defaultLocale
    val prefix = "{{siteRootPath}}/"
    s"""<header class="header">
       |  <nav class="navbar">
       |    <div class="navbar-brand">
       |      <a class="navbar-item" href="{{siteRootPath}}/index.html">{{site.title}}</a>
       |      <button class="navbar-burger" aria-controls="topbar-nav" aria-expanded="false" aria-label="Toggle main menu">
       |        <span></span>
       |        <span></span>
       |        <span></span>
       |      </button>
       |    </div>
       |    <div id="topbar-nav" class="navbar-menu">
       |      <div class="navbar-end">
       |        <a class="navbar-item" href="{{siteRootPath}}/index.html">${_html_escape(_ui(locale, "nav.home"))}</a>
       |        ${_bok_nav_menu(config, locale, prefix)}
       |        ${_category_nav_menu(locale, _regular_category_summaries(config.sourcepath), prefix)}
       |      </div>
       |    </div>
       |  </nav>
       |</header>
       |""".stripMargin
  }

  private[bok] def _default_ui_eq_helper(): String =
    """'use strict'
      |
      |module.exports = (a, b) => a === b
      |""".stripMargin

  private[bok] def _default_ui_increment_helper(): String =
    """'use strict'
      |
      |module.exports = (value) => (value || 0) + 1
      |""".stripMargin

  private[bok] def _default_ui_or_helper(): String =
    """'use strict'
      |
      |module.exports = (...args) => {
      |  const numArgs = args.length
      |  if (numArgs === 3) return args[0] || args[1]
      |  if (numArgs < 3) throw new Error('{{or}} helper expects at least 2 arguments')
      |  args.pop()
      |  return args.some((it) => it)
      |}
      |""".stripMargin

  private[bok] def _default_ui_relativize_helper(): String =
    """'use strict'
      |
      |const { posix: path } = require('path')
      |
      |module.exports = (to, from, ctx) => {
      |  if (!to) return '#'
      |  if (to.charAt() !== '/') return to
      |  if (!ctx) from = (ctx = from).data.root.page.url
      |  if (!from) return (ctx.data.root.site.path || '') + to
      |  let hash = ''
      |  const hashIdx = to.indexOf('#')
      |  if (~hashIdx) {
      |    hash = to.slice(hashIdx)
      |    to = to.slice(0, hashIdx)
      |  }
      |  if (to === from) return hash || (isDir(to) ? './' : path.basename(to))
      |  const rel = path.relative(path.dirname(from + '.'), to)
      |  return rel ? (isDir(to) ? rel + '/' : rel) + hash : (isDir(to) ? './' : '../' + path.basename(to)) + hash
      |}
      |
      |function isDir (str) {
      |  return str.charAt(str.length - 1) === '/'
      |}
      |""".stripMargin

  private[bok] def _default_ui_css(): String =
    _resource_text("cozy/bok/default-ui.css").getOrElse("")
}
