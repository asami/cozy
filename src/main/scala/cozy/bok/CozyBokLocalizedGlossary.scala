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

private[cozy] trait CozyBokLocalizedGlossary {
  self: CozyBokImplementation.type =>
  private[bok] def _localized_glossary_index_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    lang: String
  ): String = {
    val categorytitles = categories.map(x => x.slug -> x.title).toMap
    val terms = _localized_glossary_terms(lang, _terms(config).map { term =>
      val categoryslug = term.categorySlug
      LocalizedGlossaryItem(
        s"../../${term.publicpath}",
        term.title,
        term.reading,
        categoryslug,
        categorytitles.getOrElse(categoryslug, categoryslug)
      )
    })
    val uilang = config.defaultLocale
    val title = _localized_glossary_index_title(uilang, lang)
    val description = _localized_glossary_index_description(uilang, lang)
    val indexheading = _localized_glossary_index_heading(uilang)
    val termsheading = _localized_glossary_terms_heading(uilang)
    val indexnav = _localized_glossary_index_nav(lang, terms)
    val sections = _localized_glossary_index_sections(lang, terms)
    s"""<!doctype html>
       |<html lang="${_html_escape(lang)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(title)} - ${_html_escape(config.siteTitle)}</title>
       |  <link rel="stylesheet" href="../../_/css/bootstrap-grid.min.css">
       |  <link rel="stylesheet" href="../../_/css/site.css">
       |  <link rel="stylesheet" href="../../_/css/cozy-bok-dashboard.css">
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, uilang, "../../")}
       |<div class="body">
       |  <main class="article">
       |    <div class="toolbar" role="navigation">
       |      <a href="../../index.html" class="home-link"></a>
       |      <nav class="breadcrumbs" aria-label="breadcrumbs">
       |        <ul>
       |          <li><a href="../../index.html">${_html_escape(config.siteTitle)}</a></li>
       |          <li><a href="../../glossary/index.html">Glossary</a></li>
       |          <li>${_html_escape(title)}</li>
       |        </ul>
       |      </nav>
       |    </div>
       |    <div class="content">
       |      ${_localized_glossary_toc_panel(config)}
       |      <article class="doc">
       |        <h1 class="page">${_html_escape(title)}</h1>
       |        <p>${_html_escape(description)}</p>
       |        <div class="sect1" id="index">
       |          <h2>${_html_escape(indexheading)}</h2>
       |          <div class="sectionbody">
       |            ${indexnav}
       |          </div>
       |        </div>
       |        <div class="sect1" id="terms">
       |          <h2>${_html_escape(termsheading)}</h2>
       |          <div class="sectionbody">
       |            ${sections}
       |          </div>
       |        </div>
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin
  }

  private def _localized_glossary_index_title(
    uilang: String,
    indexlang: String
  ): String =
    uilang match {
      case "ja" =>
        indexlang match {
          case "ja" => "日本語用語索引"
          case "en" => "英語用語索引"
          case other => s"${other} 用語索引"
        }
      case _ =>
        indexlang match {
          case "ja" => "Japanese Glossary Index"
          case "en" => "English Glossary Index"
          case other => s"${other} Glossary Index"
        }
    }

  private def _localized_glossary_index_description(
    uilang: String,
    indexlang: String
  ): String =
    uilang match {
      case "ja" =>
        indexlang match {
          case "ja" => "日本語で用語を探すための索引ページです。"
          case "en" => "英語表記の用語を探すための索引ページです。"
          case other => s"${other} 表記の用語を探すための索引ページです。"
        }
      case _ => "This page is a language-specific entry point for glossary browsing."
    }

  private def _localized_glossary_index_heading(uilang: String): String =
    uilang match {
      case "ja" => "索引"
      case _ => "Index"
    }

  private def _localized_glossary_terms_heading(uilang: String): String =
    uilang match {
      case "ja" => "用語"
      case _ => "Terms"
    }

  private def _localized_glossary_terms(
    lang: String,
    terms: Vector[LocalizedGlossaryItem]
  ): Vector[LocalizedGlossaryItem] =
    lang match {
      case "ja" => terms.filter(term => _has_japanese_character(_ja_index_text(term)))
      case _ => terms
    }

  private def _localized_glossary_index_nav(
    lang: String,
    terms: Vector[LocalizedGlossaryItem]
  ): String = {
    val grouped = terms.groupBy(term => _localized_index_key(lang, term))
    _localized_index_keys(lang).map { key =>
      if (grouped.get(key).exists(_.nonEmpty))
        s"""<a class="bok-index-link" href="#${_html_escape(_localized_index_anchor(key))}">${_html_escape(key)}</a>"""
      else
        s"""<span class="bok-index-link is-disabled">${_html_escape(key)}</span>"""
    }.mkString("""<div class="bok-index-nav">""", "\n", "</div>")
  }

  private def _localized_glossary_index_sections(
    lang: String,
    terms: Vector[LocalizedGlossaryItem]
  ): String =
    if (terms.isEmpty)
      "<p>No glossary terms yet.</p>"
    else {
      val keys = _localized_index_keys(lang)
      val grouped = terms.groupBy(term => _localized_index_key(lang, term))
      val activekeys = keys.filter(key => grouped.get(key).exists(_.nonEmpty))
      activekeys.map { key =>
        val items = grouped.getOrElse(key, Vector.empty).sortBy { term =>
          (_localized_index_sort_key(lang, term), term.categoryslug)
        }.map { term =>
          val category =
            s""" <span class="bok-index-term-category">[${_html_escape(term.categorytitle)}]</span>"""
          s"""<li><a href="${_html_escape(term.href)}">${_html_escape(_localized_term_title(lang, term))}</a>${_localized_reading_label(lang, term)}${category}</li>"""
        }.mkString("<ul>\n", "\n", "\n</ul>")
        s"""<section class="bok-index-section" id="${_html_escape(_localized_index_anchor(key))}">
           |  <h3>${_html_escape(key)}</h3>
           |  ${items}
           |</section>""".stripMargin
      }.mkString("\n")
    }

  private def _localized_index_keys(lang: String): Vector[String] =
    lang match {
      case "ja" =>
        "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをん".map(_.toString).toVector :+ "その他"
      case "en" =>
        ('A' to 'Z').map(_.toString).toVector :+ "Other"
      case _ =>
        ('A' to 'Z').map(_.toString).toVector :+ "Other"
    }

  private def _localized_index_key(lang: String, term: LocalizedGlossaryItem): String =
    lang match {
      case "ja" => _ja_index_key(_ja_index_text(term))
      case "en" => _en_index_key(term.title)
      case _ => _en_index_key(term.title)
    }

  private def _localized_index_sort_key(lang: String, term: LocalizedGlossaryItem): String =
    lang match {
      case "ja" => _ja_index_text(term)
      case _ => term.title.toLowerCase(java.util.Locale.ROOT)
    }

  private def _ja_index_text(term: LocalizedGlossaryItem): String =
    term.reading.getOrElse(term.title)

  private def _localized_term_title(lang: String, term: LocalizedGlossaryItem): String =
    lang match {
      case "ja" => term.reading.getOrElse(term.title)
      case _ => term.title
    }

  private def _localized_reading_label(lang: String, term: LocalizedGlossaryItem): String =
    if (_localized_term_title(lang, term) == term.reading.getOrElse(""))
      ""
    else
      term.reading.filterNot(_ == term.title).map { reading =>
        s""" <span class="bok-term-reading">(${_html_escape(reading)})</span>"""
      }.getOrElse("")

  private[bok] def _reading_label(term: CategoryPageItem): String =
    term.reading.filterNot(_ == term.title).map { reading =>
      s""" <span class="bok-term-reading">(${_html_escape(reading)})</span>"""
    }.getOrElse("")

  private[bok] def _reading_label(term: TermEntry): String =
    term.reading.filterNot(_ == term.title).map { reading =>
      s""" <span class="bok-term-reading">(${_html_escape(reading)})</span>"""
    }.getOrElse("")

  private def _localized_index_anchor(key: String): String =
    key match {
      case "その他" => "index-other"
      case "Other" => "index-other"
      case other => s"index-${other.toLowerCase(java.util.Locale.ROOT)}"
    }

  private def _ja_index_key(title: String): String = {
    val keys = _localized_index_keys("ja").toSet
    title.trim.headOption.map(_normalize_kana).flatMap { ch =>
      val value = ch.toString
      if (keys.contains(value))
        Some(value)
      else
        _voiced_kana_base.get(ch).map(_.toString).filter(keys.contains)
    }.getOrElse("その他")
  }

  private def _en_index_key(title: String): String =
    title.trim.find(_.isLetter).map(_.toUpper).filter(ch => ch >= 'A' && ch <= 'Z').
      map(_.toString).getOrElse("Other")

  private def _has_japanese_character(value: String): Boolean =
    value.exists(ch =>
      (ch >= '\u3040' && ch <= '\u309f') ||
      (ch >= '\u30a0' && ch <= '\u30ff') ||
      (ch >= '\u4e00' && ch <= '\u9fff')
    )

  private def _normalize_kana(ch: Char): Char =
    if (ch >= '\u30a1' && ch <= '\u30f6')
      (ch - 0x60).toChar
    else
      ch match {
        case 'ぁ' => 'あ'
        case 'ぃ' => 'い'
        case 'ぅ' => 'う'
        case 'ぇ' => 'え'
        case 'ぉ' => 'お'
        case 'ゃ' => 'や'
        case 'ゅ' => 'ゆ'
        case 'ょ' => 'よ'
        case 'ゎ' => 'わ'
        case other => other
      }

  private val _voiced_kana_base: Map[Char, Char] =
    Map(
      'が' -> 'か', 'ぎ' -> 'き', 'ぐ' -> 'く', 'げ' -> 'け', 'ご' -> 'こ',
      'ざ' -> 'さ', 'じ' -> 'し', 'ず' -> 'す', 'ぜ' -> 'せ', 'ぞ' -> 'そ',
      'だ' -> 'た', 'ぢ' -> 'ち', 'づ' -> 'つ', 'で' -> 'て', 'ど' -> 'と',
      'ば' -> 'は', 'び' -> 'ひ', 'ぶ' -> 'ふ', 'べ' -> 'へ', 'ぼ' -> 'ほ',
      'ぱ' -> 'は', 'ぴ' -> 'ひ', 'ぷ' -> 'ふ', 'ぺ' -> 'へ', 'ぽ' -> 'ほ'
    )

}
