package cozy.publication

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
import org.goldenport.cli.{Config => CliConfig, Environment}
import org.goldenport.realm.Realm
import org.goldenport.realm.Realm.StringData
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.smartdox.doxsite.DoxSite
import org.smartdox.generator.{Config => SmartDoxConfig, Context => SmartDoxContext}
import org.smartdox.generators.{AntoraGenerator, DoxSiteGenerator}

/*
 * @since   Sep.  8, 2026
 * @version Oct.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentProjectSiteProjectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Document Project site projection" should {
    "P751-02A-SPEC-01 retain physical relative includes and canonical bilingual package and sibling pages" in {
      Given("a bilingual package and ordinary sibling with relative includes under an absolute root containing spaces and Japanese")
      _with_fixture("physical 日本語 root -") { root =>
        _write_config(root)
        _write(root.resolve("development-process/domain-modeling.dox/index.dox"),
          """|Domain Modeling｜ドメインモデリング
             |====================================
             |
             |# HEAD
             |
             |status=published
             |published_at=2026-09-08
             |
             |# Body
             |
             |Document Project package content.
             |
             |include::_fragment.dox[]
             |""".stripMargin)
        _write(root.resolve("development-process/domain-modeling.dox/_fragment.dox"),
          "Physical package include sentinel.\n")
        _write(root.resolve("development-process/literate-modeling.dox"),
          """|Literate Modeling｜文芸モデリング
             |==================================
             |
             |# HEAD
             |
             |status=published
             |published_at=2026-09-08
             |
             |# Body
             |
             |Ordinary article content.
             |
             |include::_fragment.dox[]
             |""".stripMargin)
        _write(root.resolve("development-process/_fragment.dox"),
          "Physical ordinary include sentinel.\n")

        When("Cozy passes the native physical Realm unchanged to the accepted Full-strategy HTML and Antora generators")
        val source = Realm.create(DoxSite.realmConfig, root.toFile)
        val generated = new DoxSiteGenerator(_smartdox_context, _full_config).generate(source)
        val antora = new AntoraGenerator(_smartdox_context, _full_config).generate(source)

        Then("each locale exposes the flattened package and ordinary sibling with its real included content")
        _string(generated, "doxsite.d/ja/development-process/domain-modeling.html") should include("ドメインモデリング")
        _string(generated, "doxsite.d/en/development-process/domain-modeling.html") should include("Domain Modeling")
        _string(generated, "doxsite.d/ja/development-process/literate-modeling.html") should include("文芸モデリング")
        _string(generated, "doxsite.d/en/development-process/literate-modeling.html") should include("Literate Modeling")
        generated.get("doxsite.d/ja/development-process/domain-modeling.dox/index.html") shouldBe None
        generated.get("doxsite.d/en/development-process/domain-modeling.dox/index.html") shouldBe None
        Vector("ja", "en").foreach { language =>
          _string(generated, s"doxsite.d/$language/development-process/domain-modeling.html") should include("Physical package include sentinel.")
          _string(generated, s"doxsite.d/$language/development-process/literate-modeling.html") should include("Physical ordinary include sentinel.")
          _string(antora, s"antora.d/$language/docs/development-process/modules/ROOT/pages/domain-modeling.adoc") should include("Physical package include sentinel.")
          _string(antora, s"antora.d/$language/docs/development-process/modules/ROOT/pages/literate-modeling.adoc") should include("Physical ordinary include sentinel.")
          antora.get(s"antora.d/$language/docs/development-process/modules/domain-modeling.dox/pages/index.adoc") shouldBe None
        }
      }
    }

    "P751-02A-SPEC-02 exclude direct and arbitrarily nested private Markdown from public pages and card metadata" in {
      Given("a public package index with uniquely titled direct and deeply nested private md/markdown notes and bare _source notes")
      _with_fixture("private-notes-") { root =>
        _write_config(root)
        _write(root.resolve("development-process/domain-modeling.dox/index.dox"),
          _dox_source("Domain Modeling｜ドメインモデリング", "Public package index sentinel."))
        val notes = Vector(
          ("private-direct.md", "private-direct", "Private direct MD title", "Private direct MD content."),
          ("private-direct.markdown", "private-direct", "Private direct Markdown title", "Private direct Markdown content."),
          ("arbitrary/deeper/elsewhere/private-nested.md", "arbitrary/deeper/elsewhere/private-nested", "Private nested MD title", "Private nested MD content."),
          ("arbitrary/deeper/elsewhere/private-nested.markdown", "arbitrary/deeper/elsewhere/private-nested", "Private nested Markdown title", "Private nested Markdown content.")
        )
        notes.foreach { case (path, _, title, content) =>
          _write(root.resolve(s"development-process/domain-modeling.dox/$path"), _markdown_source(title, content))
        }
        val barepaths = Vector("bare-source.md", "arbitrary/deeper/elsewhere/bare-source.markdown")
        barepaths.foreach { path =>
          _write(root.resolve(s"development-process/domain-modeling.dox/$path"),
            "---\n_source\n---\n\nPrivate bare source sentinel.\n")
        }

        When("the actual public generators admit only the package index before parsing and producing notice/card inputs")
        val source = Realm.create(DoxSite.realmConfig, root.toFile)
        val generated = new DoxSiteGenerator(_smartdox_context, _full_config).generate(source)
        val antora = new AntoraGenerator(_smartdox_context, _full_config).generate(source)

        Then("public content and localized notice cards remain observable without any private title content or URL")
        val metadata = Vector(
          "metadata/dashboard/site.json", "metadata/rdf/graph.json", "metadata/documents/fragments.json",
          "metadata/bibliography/bibliography.json", "site.ttl", "WEB-INF/data/notice01.yaml",
          "WEB-INF/data/ja/notice01.yaml", "WEB-INF/data/en/notice01.yaml"
        ).map(path => _string(generated, s"doxsite.d/$path"))
        _string(generated, "doxsite.d/metadata/documents/fragments.json") should include("Public package index sentinel.")
        Vector("ja" -> "ドメインモデリング", "en" -> "Domain Modeling").foreach { case (language, title) =>
          val html = _string(generated, s"doxsite.d/$language/development-process/domain-modeling.html")
          html should include("Public package index sentinel.")
          val notice = _string(generated, s"doxsite.d/WEB-INF/data/$language/notice01.yaml")
          notice should include(title)
          notice should include("development-process/domain-modeling.html")
          generated.get(s"doxsite.d/WEB-INF/data/$language/notice02.yaml") shouldBe None
          _string(antora, s"antora.d/$language/docs/development-process/modules/ROOT/pages/domain-modeling.adoc") should include("Public package index sentinel.")
          notes.foreach { case (path, stem, privatetitle, content) =>
            generated.get(s"doxsite.d/$language/development-process/domain-modeling.dox/$stem.html") shouldBe None
            antora.get(s"antora.d/$language/docs/development-process/modules/domain-modeling.dox/pages/$stem.adoc") shouldBe None
            (metadata :+ html).foreach { output =>
              output should not include privatetitle
              output should not include content
              output should not include path
              output should not include s"domain-modeling.dox/$stem.html"
            }
          }
          Vector("bare-source", "arbitrary/deeper/elsewhere/bare-source").foreach { stem =>
            generated.get(s"doxsite.d/$language/development-process/domain-modeling.dox/$stem.html") shouldBe None
            antora.get(s"antora.d/$language/docs/development-process/modules/domain-modeling.dox/pages/$stem.adoc") shouldBe None
          }
          (metadata :+ html).foreach { output =>
            output should not include "Private bare source sentinel."
            barepaths.foreach(path => output should not include path)
            output should not include "domain-modeling.dox/bare-source.html"
            output should not include "domain-modeling.dox/arbitrary/deeper/elsewhere/bare-source.html"
          }
        }
      }
    }

    "P751-02A-SPEC-03 use YAML admission and genuine Markdown while leaving Dox body directives literal" in {
      Given("published ordinary md and markdown sources with bilingual YAML titles, emphasis, and contradictory literal Dox directives")
      _with_fixture("markdown-grammar-") { root =>
        _write_config(root)
        val body = "# HEAD\n\nstatus=draft\n\ninclude::missing[]\n\nsite:[missing.dox]\n\n**Markdown strong** and *Markdown emphasis*.\n"
        Vector("md", "markdown").foreach { suffix =>
          _write(root.resolve(s"development-process/literal-$suffix.$suffix"),
            _markdown_source(s"Markdown $suffix｜マークダウン $suffix", body))
        }
        val config = DoxSite.Config.default.copy(strategy = DoxSite.Strategy.Production)

        When("the actual Production-strategy HTML and Antora generators consume the admitted Markdown without fetching literal includes")
        val source = Realm.create(DoxSite.realmConfig, root.toFile)
        val generated = new DoxSiteGenerator(_smartdox_context, config).generate(source)
        val antora = new AntoraGenerator(_smartdox_context, config).generate(source)

        Then("both published suffixes render at canonical JA/EN routes with YAML titles, Markdown emphasis, and literal directives")
        Vector("md", "markdown").foreach { suffix =>
          Vector("ja" -> s"マークダウン $suffix", "en" -> s"Markdown $suffix").foreach { case (language, title) =>
            val html = _string(generated, s"doxsite.d/$language/development-process/literal-$suffix.html")
            html should include(title)
            html should include("<strong>Markdown strong</strong>")
            html should include("<em>Markdown emphasis</em>")
            val page = _string(antora, s"antora.d/$language/docs/development-process/modules/ROOT/pages/literal-$suffix.adoc")
            page should include(title)
            Vector("HEAD", "status=draft", "include::missing[]", "site:[missing.dox]").foreach { literal =>
              html should include(literal)
              page should include(literal)
            }
            page should include("Markdown strong")
            page should include("Markdown emphasis")
          }
        }
      }
    }

    "P751-02A-SPEC-04 resolve localized incoming Dox and Markdown aliases to the actual flattened package fragment" in {
      Given("a bilingual package with a real target fragment and ordinary Dox/md/markdown sources using physical-source and HTML aliases")
      _with_fixture("incoming-aliases-") { root =>
        _write_config(root)
        _write(root.resolve("development-process/domain-modeling.dox/index.dox"),
          _dox_source("Domain Modeling｜ドメインモデリング",
            "<dfn id=\"package-part\">Actual package target fragment.</dfn>\n\nSibling site:[literate-modeling.dox]."))
        _write(root.resolve("development-process/literate-modeling.dox"),
          _dox_source("Literate Modeling｜文芸モデリング",
            "Direct site:[domain-modeling.dox/index.dox#package-part]. Alias site:[domain-modeling.dox/index.html#package-part]."))
        Vector("md", "markdown").foreach { suffix =>
          _write(root.resolve(s"development-process/incoming-$suffix.$suffix"),
            _markdown_source(s"Incoming $suffix｜参照元 $suffix",
              "[Direct source](domain-modeling.dox/index.dox#package-part)\n\n[HTML alias](domain-modeling.dox/index.html#package-part)"))
        }

        When("the accepted public generators resolve incoming aliases and render canonical localized source and target pages")
        val source = Realm.create(DoxSite.realmConfig, root.toFile)
        val generated = new DoxSiteGenerator(_smartdox_context, _full_config).generate(source)
        val antora = new AntoraGenerator(_smartdox_context, _full_config).generate(source)

        Then("each JA/EN incoming page links to the canonical package URL and preserves its actual target fragment")
        Vector("ja" -> ("ドメインモデリング", "文芸モデリング"), "en" -> ("Domain Modeling", "Literate Modeling")).foreach { case (language, (packagecaption, siblingcaption)) =>
          val target = _string(generated, s"doxsite.d/$language/development-process/domain-modeling.html")
          target should include(packagecaption)
          target should include("id=\"package-part\"")
          target should include("Actual package target fragment.")
          target should include("href=\"literate-modeling.html\"")
          target should include(siblingcaption)
          Vector("literate-modeling", "incoming-md", "incoming-markdown").foreach { stem =>
            val html = _string(generated, s"doxsite.d/$language/development-process/$stem.html")
            html should include("href=\"domain-modeling.html#package-part\"")
            html should include(packagecaption)
            html should not include "href=\"domain-modeling.dox/index."
          }
          _string(generated, s"doxsite.d/$language/development-process/literate-modeling.html") should include(siblingcaption)
          _string(antora, s"antora.d/$language/docs/development-process/modules/ROOT/pages/domain-modeling.adoc") should include("Actual package target fragment.")
          generated.get(s"doxsite.d/$language/development-process/domain-modeling.dox/index.html") shouldBe None
          antora.get(s"antora.d/$language/docs/development-process/modules/domain-modeling.dox/pages/index.adoc") shouldBe None
        }
      }
    }
  }

  private def _full_config: DoxSite.Config = DoxSite.Config.default.copy(strategy = DoxSite.Strategy.Full)

  private def _with_fixture(prefix: String)(body: Path => Unit): Unit = {
    val target = Files.createDirectories(Path.of("target", "cozy-document-project-site-projection").toAbsolutePath)
    val root = Files.createTempDirectory(target, prefix)
    try body(root) finally _delete(root)
  }

  private def _write_config(root: Path): Unit =
    _write(root.resolve("site.conf"),
      """|site.output.locale_mode = "multi_locale_subdirs"
         |site.output.default_locale = "ja"
         |""".stripMargin)

  private def _dox_source(title: String, body: String): String =
    s"$title\n====================================\n\n# HEAD\n\nstatus=published\npublished_at=2026-09-08\n\n# Body\n\n$body\n"

  private def _markdown_source(title: String, body: String): String =
    s"---\ntitle: $title\nstatus: published\npublished_at: 2026-09-08\n---\n\n$body\n"

  private def _smartdox_context: SmartDoxContext = {
    val environment = Environment.createJaJp()
    val cliconfig = CliConfig.buildJaJp()
    new SmartDoxContext(environment, SmartDoxConfig(cliconfig), environment.contextFoundation)
  }

  private def _string(realm: Realm, path: String): String =
    realm.get(path).collect { case value: StringData => value.string }.getOrElse(fail(s"Missing generated content: $path"))

  private def _write(path: Path, content: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
  }

  private def _delete(root: Path): Unit = {
    if (Files.exists(root)) {
      val stream = Files.walk(root)
      try stream.iterator.asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.delete)
      finally stream.close()
    }
  }
}
