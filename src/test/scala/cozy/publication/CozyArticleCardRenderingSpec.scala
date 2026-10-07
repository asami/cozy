package cozy.publication

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
import com.typesafe.config.ConfigFactory
import arcadia.context.PlatformExecutionContext
import arcadia.standalone.service.generators.{ArcadiaSiteGenerator, Context => ArcadiaContext}
import cozy.media.CozyMediaWarehouse
import org.goldenport.cli.{Config => CliConfig, Environment}
import org.goldenport.collection.NonEmptyVector
import org.goldenport.realm.Realm
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.smartdox.doxsite.DoxSite
import org.smartdox.generator.{Config => SmartDoxConfig, Context => SmartDoxContext}
import org.smartdox.generators.DoxSiteGenerator
import org.smartdox.metadata.PublishMetadata.{ImageReference, PdfDocumentReference, VideoPresentation, VideoReference, VideoStatus}
import org.yaml.snakeyaml.Yaml
import play.api.libs.json.{Json, JsArray}

/*
 * @since   Oct.  7, 2026
 * @version Oct.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyArticleCardRenderingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private final case class Fixture(root: Path, publication: Path, warehouse: Path, source: Path)
  private final case class Rendered(doxsite: Path, arcadiasite: Path)
  private final case class Anchor(href: String, classes: Set[String], caption: String)
  private val _identity = "development-process/example"
  private val _plain = "development-process/plain"
  private val _other = "development-process/other"
  private val _locales = Vector("ja", "en")
  private val _roles = Vector("infographic", "article_pdf", "summary_slides_pdf")
  // Ordinary classpath inputs originating in SimpleModeling's
  // src/main/arcadialib/WEB-INF/widgets; no external checkout is read at runtime.
  private val _widgets = Vector("headline", "headline-wide", "headline-wide-reverse")

  "Native article-card publication" should {
    "P751-02C-01 render every available bilingual role in all home and category widgets" in {
      Given("a physical bilingual public package, placed canonical images/PDFs and published external videos with explicit PDF labels")
      _with_fixture { fixture =>
        When("normal availability dispatch, actual SmartDox export and native Arcadia composition render the cards")
        val rendered = _render(fixture, _identity)
        Then("each widget has the exact localized canonical article and all four media href/class/caption sets")
        _assert_pages(rendered, _identity, _locales.map(locale => locale -> _media(locale)).toMap)
        _locales.foreach(locale => Files.exists(fixture.warehouse.resolve(s"media/$locale/${_identity}/video.mp4")) shouldBe false)
      }
    }

    "P751-02C-02 omit only the physically missing nonvideo role in its exact locale" in {
      for (locale <- _locales; role <- _roles) {
        Given(s"only the $locale $role canonical file is missing before normal context construction")
        _with_fixture { fixture =>
          Files.delete(fixture.warehouse.resolve(_path(locale, role).drop(1)))
          val expected = _locales.map(language => language -> _media(language, omitted = if (language == locale) Set(role) else Set.empty)).toMap
          When("the available effective publication passes through both native generators")
          val rendered = _render(fixture, _identity)
          Then("all three home/category widgets omit exactly that role while the other roles and opposite locale survive")
          _assert_pages(rendered, _identity, expected)
        }
      }
    }

    "P751-02C-03 retain only published external video and render a media-free ordinary article without a wrapper" in {
      val selections = Vector(VideoStatus.Published -> _identity, VideoStatus.Draft -> _identity,
        VideoStatus.Withdrawn -> _identity, VideoStatus.Published -> _plain)
      selections.foreach { case (status, identity) =>
        Given(s"all nonvideo files absent, no MP4, external status ${status.name} and selected public article $identity")
        _with_fixture { fixture =>
          for (locale <- _locales; role <- _roles) Files.delete(fixture.warehouse.resolve(_path(locale, role).drop(1)))
          _write_publication(fixture, status = status)
          val expected = _locales.map(locale => locale ->
            (if (identity == _identity && status == VideoStatus.Published) _media(locale, omitted = _roles.toSet) else Set.empty[Anchor])).toMap
          When("normal context dispatch and both actual generators render the selected article")
          val rendered = _render(fixture, identity)
          Then("only a published external video is a media link and an ordinary article has its correct link with no media wrapper")
          _assert_pages(rendered, identity, expected)
          _locales.foreach(locale => Files.exists(fixture.warehouse.resolve(s"media/$locale/${_identity}/video.mp4")) shouldBe false)
        }
      }
    }

    "P751-02C-04 keep bilingual article identity when only a Japanese native media variant is declared" in {
      Given("a bilingual published package whose native publication declares only JA media")
      _with_fixture { fixture =>
        _write_publication(fixture, locales = Vector("ja"))
        When("actual global/category Notice products are exported and consumed by native Arcadia")
        val rendered = _render(fixture, _identity)
        Then("Japanese cards retain every role while English cards retain their English title/route with no Japanese fallback")
        _assert_pages(rendered, _identity, Map("ja" -> _media("ja"), "en" -> Set.empty[Anchor]))
      }
    }

    "P751-02C-05 preserve declared PDF labels and use actual localized defaults when optional labels are absent" in {
      Vector(true, false).foreach { labels =>
        Given(s"normal bilingual publication with explicit PDF labels = $labels and all three ordinary widget inputs")
        _with_fixture { fixture =>
          _write_publication(fixture, labels = labels)
          When("native SmartDox Notice and Arcadia expression/optional rendering process the cards")
          val rendered = _render(fixture, _identity)
          Then("custom labels or JA/EN defaults are exact and no unresolved value or tag remains in any widget")
          _assert_pages(rendered, _identity, _locales.map(locale => locale -> _media(locale, labels = labels)).toMap)
        }
      }
    }

    "P751-02C-06 render public package and ordinary controls without exposing nested private Markdown" in {
      Vector(_identity, _plain, _other).foreach { identity =>
        Given(s"selected public article $identity, another ordinary published control and private direct/deep package Markdown")
        _with_fixture { fixture =>
          _write(fixture.source.resolve(s"${_other}.dox"), _dox("Other｜別の記事", "Other public control content."))
          _write(fixture.source.resolve(s"${_identity}.dox/review.md"),
            "---\ntitle: Private Review Sentinel\nstatus: published\n---\n\nPrivate review content sentinel.\n")
          _write(fixture.source.resolve(s"${_identity}.dox/arbitrary/deeper/private.markdown"),
            "---\ntitle: Private Deep Sentinel\nstatus: published\n---\n\nPrivate deep content sentinel.\n")
          val expected = _locales.map(locale => locale -> (if (identity == _identity) _media(locale) else Set.empty[Anchor])).toMap
          When("both native generators consume the selected public index and ordinary controls")
          val rendered = _render(fixture, identity)
          Then("the selected positive public card renders and neither generator exposes private titles/content/source or public URIs")
          _assert_pages(rendered, identity, expected)
          _locales.foreach { locale =>
            _notice_names(rendered.doxsite, locale, false).keySet shouldBe Set(_identity + ".html", _plain + ".html", _other + ".html")
            _notice_names(rendered.doxsite, locale, true).keySet shouldBe Set(_identity + ".html", _plain + ".html", _other + ".html")
            Vector(_identity, _plain, _other).foreach { publicidentity =>
              _read(rendered.doxsite.resolve(s"$locale/$publicidentity.html")) should include(_title(publicidentity, locale))
            }
            Vector("index.html", "development-process/index.html").foreach { page =>
              val cardhtml = _read(rendered.arcadiasite.resolve(s"$locale/$page"))
              _private_tokens.foreach(token => cardhtml should not include token)
            }
            Vector("review", "arbitrary/deeper/private").foreach { stem =>
              Files.exists(rendered.doxsite.resolve(s"$locale/${_identity}.dox/$stem.html")) shouldBe false
              Files.exists(rendered.arcadiasite.resolve(s"$locale/${_identity}.dox/$stem.html")) shouldBe false
            }
          }
          _html_files(rendered.doxsite).foreach(path => _private_tokens.foreach(token => _read(path) should not include token))
          _html_files(rendered.arcadiasite).foreach(path => _private_tokens.foreach(token => _read(path) should not include token))
        }
      }
    }
  }

  private def _with_fixture(consumer: Fixture => Unit): Unit = {
    val parent = Files.createDirectories(Path.of("target/cozy-article-card-rendering-spec").toAbsolutePath.normalize())
    val root = Files.createTempDirectory(parent, "fixture-")
    try {
      val fixture = Fixture(root, Files.createDirectory(root.resolve("publication")),
        Files.createDirectory(root.resolve("warehouse")), Files.createDirectory(root.resolve("source")))
      for (locale <- _locales; role <- _roles)
        _write(fixture.warehouse.resolve(_path(locale, role).drop(1)), if (role == "infographic") _svg else _pdf)
      _write_publication(fixture)
      _write(fixture.source.resolve("site.conf"),
        "site.output.locale_mode = \"multi_locale_subdirs\"\nsite.output.default_locale = \"ja\"\n")
      _write(fixture.source.resolve("development-process/category.yaml"),
        "name: development-process\ntitle: Development Process\ndescription:\n  en: Development process articles.\n  ja: 開発プロセスの記事です。\n")
      _write(fixture.source.resolve(s"${_identity}.dox/index.dox"), _dox("Example｜サンプル", "Public package index content."))
      _write(fixture.source.resolve(s"${_plain}.dox"), _dox("Plain｜普通", "Ordinary control content."))
      consumer(fixture)
    } finally _delete(root)
  }

  private def _write_publication(fixture: Fixture, locales: Vector[String] = Vector("ja", "en"),
    status: VideoStatus = VideoStatus.Published, labels: Boolean = true): Unit = {
    val publication = CozyArticleMediaPublication.produce(_identity, locales.map { locale =>
      CozyArticleMediaPublication.Variant(locale,
        infographic = Some(ImageReference(new URI(_path(locale, "infographic")), Some("image/svg+xml"), Some(s"Image $locale"))),
        video = Some(VideoReference(VideoPresentation.ExternalLink, status, Some("youtube"), Some(new URI(_watch(locale))), None)),
        articlePdf = Some(PdfDocumentReference(new URI(_path(locale, "article_pdf")), "application/pdf", if (labels) Some(s"Article $locale") else None)),
        summarySlidesPdf = Some(PdfDocumentReference(new URI(_path(locale, "summary_slides_pdf")), "application/pdf", if (labels) Some(s"Slides $locale") else None)))
    })
    val entry = Json.obj("path" -> publication.entryPath,
      "key" -> publication.entryPath.stripPrefix("metadata/").stripSuffix(".json"), "metadata" -> publication.metadata)
    _write(fixture.publication.resolve("owner.json"), Json.stringify(Json.obj("schema" -> "cozy.publish-project.v1",
      "type" -> "publication-bundle", "publication" -> Json.obj("name" -> "owner"), "entries" -> JsArray(Vector(entry)))))
  }

  private def _render(fixture: Fixture, identity: String): Rendered =
    CozyArticleMediaBuildContext.withContext(fixture.root, fixture.publication, fixture.warehouse, "production") { context =>
      val environment = Environment.createJaJp()
      val smartdox = new SmartDoxContext(environment, SmartDoxConfig(CliConfig.buildJaJp()), environment.contextFoundation)
      val generated = new DoxSiteGenerator(smartdox, DoxSite.Config.default.copy(strategy = DoxSite.Strategy.Production),
        Some(context.publicationPath.toFile), Some(fixture.warehouse.toFile), "fail")
        .generate(Realm.create(DoxSite.realmConfig, fixture.source.toFile))
      val exported = Files.createDirectory(fixture.root.resolve("generated"))
      generated.export(exported.toFile)
      val doxsite = exported.resolve("doxsite.d")
      val authored = Files.createDirectory(fixture.root.resolve("authored"))
      _widgets.foreach { widget =>
        val stream = Option(getClass.getResourceAsStream(s"/cozy/article-cards/$widget.html"))
          .getOrElse(throw new IllegalStateException(s"Missing classpath widget: $widget"))
        try _write(authored.resolve(s"WEB-INF/widgets/$widget.html"), new String(stream.readAllBytes(), StandardCharsets.UTF_8))
        finally stream.close()
      }
      for (locale <- _locales; category <- Vector(false, true)) {
        val notices = _notice_names(doxsite, locale, category)
        val data = notices.getOrElse(identity + ".html", throw new IllegalStateException(s"Missing actual Notice: $locale $category $identity"))
        val sections = _widgets.map(widget => s"""<section id="$widget"><c:widget name="$widget" data="$data"/></section>""").mkString("\n")
        val page = if (category) "development-process/index.html" else "index.html"
        _write(authored.resolve(s"$locale/$page"), s"<html><head><title>Card fixture</title></head><body>$sections</body></html>\n")
      }
      val tmp = Files.createDirectory(fixture.root.resolve("arcadia-tmp"))
      val properties = ConfigFactory.parseMap(Map[String, AnyRef]("tmp.directory" -> tmp.toString).asJava)
      val arcadia = ArcadiaContext.create(Environment.create(Array.empty[String]), PlatformExecutionContext.develop, properties, Nil)
      val output = new ArcadiaSiteGenerator(arcadia).generate(Nil,
        NonEmptyVector(Realm.create(authored.toFile), Vector(Realm.create(doxsite.toFile))))
      val rendered = Files.createDirectory(fixture.root.resolve("rendered"))
      output.export(rendered.toFile)
      Rendered(doxsite, rendered.resolve("arcadiasite.d"))
    }

  private def _notice_names(doxsite: Path, locale: String, category: Boolean): Map[String, String] = {
    val dataroot = doxsite.resolve("WEB-INF/data")
    val directory = if (category) dataroot.resolve(s"$locale/development-process") else dataroot.resolve(locale)
    val stream = Files.list(directory)
    try stream.iterator.asScala.filter(path => path.getFileName.toString.endsWith(".yaml")).flatMap { path =>
      val yaml = new Yaml().load[java.util.Map[String, Any]](_read(path))
      Option(yaml.get("notice.uri")).map(uri => uri.toString ->
        dataroot.relativize(path).toString.replace(java.io.File.separatorChar, '/').stripSuffix(".yaml"))
    }.toMap
    finally stream.close()
  }

  private def _assert_pages(rendered: Rendered, identity: String, expected: Map[String, Set[Anchor]]): Unit = {
    for (locale <- _locales; category <- Vector(false, true)) {
      val page = if (category) "development-process/index.html" else "index.html"
      val html = _read(rendered.arcadiasite.resolve(s"$locale/$page"))
      val prefix = if (category) "../" else "./"
      _widgets.foreach { widget =>
        val section = _section(html, widget)
        val anchors = _anchors(section)
        val title = _title(identity, locale)
        val article = anchors.filter(_.caption == title)
        article shouldBe Vector(Anchor(prefix + identity + ".html",
          if (widget == "headline") Set("h5", "d-block", "text-dark") else Set("text-dark"), title))
        article.foreach(anchor => new URI(s"/$locale/$page").resolve(anchor.href).toString shouldBe s"/$locale/$identity.html")
        if (widget != "headline") {
          anchors.filter(_.classes.contains("icon-move-right")) shouldBe Vector(Anchor(prefix + identity + ".html",
            Set("text-darker", "icon-move-right", "text-sm"), if (locale == "ja") "続きを読む" else "Read More"))
        }
        val media = anchors.filter(_.classes.exists(_.startsWith("simplemodeling-article-media-")))
        media.size shouldBe expected(locale).size
        media.toSet shouldBe expected(locale)
        """class="([^"]*)""".r.findAllMatchIn(section).count(_.group(1).split("\\s+").contains("simplemodeling-article-media-links")) shouldBe
          (if (expected(locale).isEmpty) 0 else 1)
        anchors.foreach { anchor =>
          anchor.href should not include "_images"
          if (anchor.href.startsWith("/media/")) anchor.href should startWith(s"/media/$locale/$identity/")
        }
        Vector("N/A", "${", "c:widget", "c:optional").foreach(residue => section should not include residue)
      }
    }
  }

  private def _media(locale: String, omitted: Set[String] = Set.empty, labels: Boolean = true): Set[Anchor] = {
    val classes = Set("btn", "btn-sm", "btn-outline-info")
    val roles = _roles.filterNot(omitted).map { role =>
      val caption = role match {
        case "infographic" => if (locale == "ja") "インフォグラフィックを見る" else "View infographic"
        case "article_pdf" => if (labels) s"Article $locale" else if (locale == "ja") "記事 PDF" else "Article PDF"
        case "summary_slides_pdf" => if (labels) s"Slides $locale" else if (locale == "ja") "まとめスライド PDF" else "Summary slides PDF"
      }
      Anchor(_path(locale, role), classes + ("simplemodeling-article-media-" + role.replace('_', '-')), caption)
    }
    (roles :+ Anchor(_watch(locale), classes + "simplemodeling-article-media-video", if (locale == "ja") "動画を見る" else "Watch video")).toSet
  }

  private def _section(html: String, widget: String): String =
    ("(?s)<section\\b[^>]*\\bid=\"" + widget + "\"[^>]*>(.*?)</section>").r.findFirstMatchIn(html)
      .map(_.group(1)).getOrElse(fail(s"Missing rendered widget section: $widget"))

  private def _anchors(html: String): Vector[Anchor] =
    "(?s)<a\\b([^>]*)>(.*?)</a>".r.findAllMatchIn(html).flatMap { matched =>
      val attributes = "([\\w-]+)=\"([^\"]*)\"".r.findAllMatchIn(matched.group(1)).map(item => item.group(1) -> item.group(2)).toMap
      attributes.get("href").map(href => Anchor(href, attributes.get("class").toVector.flatMap(_.split("\\s+")).toSet,
        matched.group(2).replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim))
    }.toVector

  private def _title(identity: String, locale: String): String = (identity, locale) match {
    case (`_identity`, "ja") => "サンプル"
    case (`_identity`, _) => "Example"
    case (`_plain`, "ja") => "普通"
    case (`_plain`, _) => "Plain"
    case (`_other`, "ja") => "別の記事"
    case (`_other`, _) => "Other"
  }
  private def _path(locale: String, role: String): String =
    CozyMediaWarehouse.canonicalPublicPath(_identity, locale, role, if (role == "infographic") "svg" else "pdf")
  private def _watch(locale: String): String = s"https://www.youtube.com/watch?v=${locale}_Example-1"
  private def _dox(title: String, body: String): String =
    s"$title\n========================\n\n# HEAD\n\nstatus=published\npublished_at=2026-10-07\n\n# Body\n\n$body\n"
  private def _svg: String = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1\" height=\"1\"><rect width=\"1\" height=\"1\"/></svg>"
  private def _pdf: String = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF\n"
  private def _private_tokens: Vector[String] = Vector("Private Review Sentinel", "Private Deep Sentinel",
    "Private review content sentinel.", "Private deep content sentinel.", s"${_identity}.dox/review",
    s"${_identity}.dox/arbitrary/deeper/private")
  private def _html_files(root: Path): Vector[Path] = {
    val stream = Files.walk(root)
    try stream.iterator.asScala.filter(path => Files.isRegularFile(path) && path.toString.endsWith(".html")).toVector
    finally stream.close()
  }
  private def _read(path: Path): String = Files.readString(path, StandardCharsets.UTF_8)
  private def _write(path: Path, content: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    ()
  }
  private def _delete(root: Path): Unit = {
    val stream = Files.walk(root)
    try stream.iterator.asScala.toVector.sortBy(path => (-path.getNameCount, path.toString)).foreach(Files.deleteIfExists)
    finally stream.close()
  }
}
