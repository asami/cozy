package cozy.bok

import java.nio.file.Files
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  1, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyBokArticleMediaCardSpec extends AnyWordSpec with GivenWhenThen with Matchers {
  import CozyBokArticleMediaCardFixture._

  "Cozy BoK article cards" should {
    "consume native bilingual all-role Notices on the matching card and leave the media-free card unchanged" in {
      Given("the persistent bilingual source and private publication copy augmented with exact-locale PDF roles, exported by the actual DoxSiteGenerator")
      CozyBokArticleMediaCardFixture.withFixture { fixture =>
        Vector("ja", "en").foreach { locale =>
          val native = read(fixture.notice(locale))
          native should include("notice.uri:")
          native should include("notice.media:")
          native should include(pdfPath(locale, "article_pdf"))
          native should include(pdfPath(locale, "summary_slides_pdf"))
          native should include(videoPath(locale))
          read(fixture.notice(locale, "development-process/plain.html")) should not include("notice.media:")
        }

        When("Cozy writes BoK pages from the generated native metadata")
        fixture.render()

        Then("both matching example cards contain their own PDFs and published video, with canonical hrefs and escaped or localized labels")
        Vector("ja", "en").foreach { locale =>
          val html = fixture.cards(locale)
          val example = _card(html, "example")
          example should include(s"""href="${pdfPath(locale, "article_pdf")}" type="application/pdf"""")
          example should include(s"""href="${pdfPath(locale, "summary_slides_pdf")}" type="application/pdf"""")
          example should include(s"""href="${videoPath(locale)}"""")
          val otherlocale = if (locale == "ja") "en" else "ja"
          example should not include(pdfPath(otherlocale, "article_pdf"))
          example should not include(pdfPath(otherlocale, "summary_slides_pdf"))
          example should not include(videoPath(otherlocale))
          example should include("href=\"../development-process/example.html\"")
          if (locale == "ja") {
            example should include(">記事 PDF</a>")
            example should include(">要約スライド PDF</a>")
          } else {
            example should include("Article &lt;PDF&gt; &amp; &quot;English&quot;")
            example should include(">Summary slides PDF</a>")
            example should not include("Article <PDF>")
          }
          val plain = _card(html, "plain")
          plain should include("href=\"../development-process/plain.html\"")
          plain should not include("bok-article-media-links")
          Vector("article_pdf", "summary_slides_pdf").foreach { role =>
            val pdf = fixture.config.websitePath.resolve(pdfPath(locale, role).stripPrefix("/"))
            read(pdf) should startWith("%PDF-1.4")
            read(pdf) should include(s"${locale.toUpperCase} $role fixture")
            read(pdf) should include("xref\n")
          }
          Files.size(fixture.config.websitePath.resolve(videoPath(locale).stripPrefix("/"))) should be > 0L
          Vector("bootstrap-grid.min.css", "site.css", "cozy-bok-dashboard.css").foreach { name =>
            html should include(s"""href="../_/css/$name"""")
            Files.isRegularFile(fixture.config.websitePath.resolve(s"$locale/_/css/$name")) shouldBe true
          }
        }
      }
    }

    "keep partial roles exact to locale and omit unpublished video" in {
      Given("native bilingual output whose English matching Notice alone is narrowed to a single PDF and an unpublished video")
      CozyBokArticleMediaCardFixture.withFixture { fixture =>
        write(fixture.notice("en"),
          s"""notice.uri: /development-process/example.html
             |notice.media:
             |  article_pdf:
             |    public_path: ${pdfPath("en", "article_pdf")}
             |    media_type: application/pdf
             |  video:
             |    presentation: site-hosted
             |    status: draft
             |    content_url: /repository/unpublished.mp4
             |""".stripMargin)

        When("Cozy writes both locales from their respective direct global Notices")
        fixture.render()

        Then("English has only its declared Article PDF while Japanese retains its complete roles")
        val english = _card(fixture.cards("en"), "example")
        english should include(s"""href="${pdfPath("en", "article_pdf")}"""")
        english should include(">Article PDF</a>")
        english should not include("summary_slides_pdf.pdf")
        english should not include("unpublished.mp4")
        english should not include(videoPath("en"))
        english should not include(pdfPath("ja", "summary_slides_pdf"))
        val japanese = _card(fixture.cards("ja"), "example")
        japanese should include(pdfPath("ja", "summary_slides_pdf"))
        japanese should include(videoPath("ja"))
      }
    }

    "accept HTTPS external video and escape attribute values while rejecting unsafe URLs" in {
      Given("a controlled exact-locale native Notice with safe HTTPS targets and the disallowed javascript, data, and protocol-relative URL forms")
      CozyBokArticleMediaCardFixture.withFixture { fixture =>
        val notice = fixture.notice("en")
        val safe = "https://example.invalid/watch?v=1&lang=en"
        write(notice,
          s"""notice.uri: development-process/example.html
             |notice.media:
             |  article_pdf:
             |    public_path: 'https://example.invalid/article.pdf?x=1&y=2'
             |    media_type: application/pdf
             |    label: 'Read <article> & "PDF"'
             |  video:
             |    presentation: external-link
             |    status: published
             |    watch_url: '$safe'
             |    content_url: /repository/unused.mp4
             |""".stripMargin)

        When("Cozy renders the safe Notice, then each unsafe URL in the same declared roles")
        fixture.render()
        val safehtml = _card(fixture.cards("en"), "example")
        val rejected = Vector("javascript:alert(1)", "data:text/html,unsafe", "//example.invalid/unsafe").map { url =>
          write(notice,
            s"""notice.uri: development-process/example.html
               |notice.media:
               |  article_pdf:
               |    public_path: '$url'
               |    media_type: application/pdf
               |  summary_slides_pdf:
               |    public_path: '$url'
               |    media_type: application/pdf
               |  video:
               |    presentation: external-link
               |    status: published
               |    watch_url: '$url'
               |""".stripMargin)
          fixture.render()
          _card(fixture.cards("en"), "example")
        }

        Then("safe URLs and labels are escaped, external video uses watch_url, and unsafe targets create no media links")
        safehtml should include("https://example.invalid/article.pdf?x=1&amp;y=2")
        safehtml should include("https://example.invalid/watch?v=1&amp;lang=en")
        safehtml should include("Read &lt;article&gt; &amp; &quot;PDF&quot;")
        safehtml should not include("/repository/unused.mp4")
        rejected.foreach(_ should not include "bok-article-media-links")
      }
    }

    "ignore category-recursive, root, opposite-locale, and non-Notice metadata when the exact global Notice is absent" in {
      Given("native bilingual output with its English global example Notice removed, while redundant category and Japanese Notices remain")
      CozyBokArticleMediaCardFixture.withFixture { fixture =>
        val selected = fixture.notice("en")
        val saved = read(selected)
        Files.delete(selected)
        write(fixture.config.doxsitePath.resolve("WEB-INF/data/notice99.yaml"), saved)
        write(selected.getParent.resolve("other.yaml"), saved)

        When("Cozy renders the English and Japanese article cards")
        fixture.render()

        Then("English does not infer any media from alternate metadata and Japanese still has its own article link")
        _card(fixture.cards("en"), "example") should not include("bok-article-media-links")
        _card(fixture.cards("ja"), "example") should include(pdfPath("ja", "article_pdf"))
      }
    }

    "fail descriptively for malformed selected native Notice metadata" in {
      Given("an exact-locale direct Notice whose media is a scalar instead of the native mapping")
      CozyBokArticleMediaCardFixture.withFixture { fixture =>
        val selected = fixture.notice("ja")
        write(selected, "notice.uri: development-process/example.html\nnotice.media: invalid\n")

        When("Cozy attempts to consume the malformed native Notice")
        val error = intercept[IllegalArgumentException](fixture.render())

        Then("the failure identifies the selected Notice and the malformed field")
        error.getMessage should include(selected.toString)
        error.getMessage should include("notice.media must be a mapping")
      }
    }

    "retain canonical media links and article navigation in single-locale root output" in {
      Given("actual bilingual native metadata selected for a single English root output")
      CozyBokArticleMediaCardFixture.withFixture { fixture =>
        val single = fixture.config.copy(localeMode = CozyBok.LocaleMode.SingleLocaleRoot, defaultLocale = "en", languages = Vector("en"))

        When("Cozy writes the existing single-locale page layout")
        CozyBokImplementation._write_bok_pages(single)

        Then("the root article card uses its English native Notice with canonical repository URLs and unchanged article navigation")
        val html = read(single.websitePath.resolve("articles/index.html"))
        val example = _card(html, "example")
        example should include("href=\"../development-process/example.html\"")
        example should include(pdfPath("en", "article_pdf"))
        example should include(pdfPath("en", "summary_slides_pdf"))
        example should include(videoPath("en"))
        example should not include(pdfPath("ja", "article_pdf"))
        _card(html, "plain") should not include("bok-article-media-links")
      }
    }
  }

  private def _card(html: String, article: String): String =
    "(?s)<article class=\"bok-article-tile\"[^>]*>.*?</article>".r.findAllIn(html).
      find(_.contains(s"""href="../development-process/$article.html"""")).getOrElse(fail(s"Missing $article card"))
}
