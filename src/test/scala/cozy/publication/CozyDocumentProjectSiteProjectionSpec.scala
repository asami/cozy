package cozy.publication

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
import io.circe.parser.parse
import org.goldenport.cli.{Config => CliConfig, Environment}
import org.goldenport.realm.Realm
import org.goldenport.realm.Realm.StringData
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.smartdox.doxsite.DoxSite
import org.smartdox.generator.{Config => SmartDoxConfig, Context => SmartDoxContext}
import org.smartdox.generators.DoxSiteGenerator

/*
 * @since   Sep.  8, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentProjectSiteProjectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Document Project site projection" should {
    "canonical package and ordinary pages" which {
    "pass a Document Project package unchanged to the actual SmartDox generated Realm" in {
      val root = _fixture()
      try {
        Given("a bilingual Document Project package and an ordinary sibling article")
        _write(root.resolve("site.conf"),
          """|site.output.locale_mode = "multi_locale_subdirs"
             |site.output.default_locale = "ja"
             |""".stripMargin)
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
             |include::parts/content.dox[]
             |
             |Ordinary sibling: site:[literate-modeling.dox]
             |""".stripMargin)
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
             |include::parts/content.dox[]
             |
             |Package sibling: site:[domain-modeling.dox/index.html]
             |""".stripMargin)

        And("each physical parent supplies its own include while unselected package descendants remain private")
        _write(root.resolve("development-process/domain-modeling.dox/parts/content.dox"),
          "Package physical include sentinel.\n")
        _write(root.resolve("development-process/parts/content.dox"),
          "Ordinary physical include sentinel.\n")
        val privatepaths = Vector(
          "development-process/domain-modeling.dox/review/private.md",
          "development-process/domain-modeling.dox/review/private.dox")
        _write(root.resolve(privatepaths.head), "_source\n\n# Private review\n\nPrivate Markdown review sentinel.\n")
        _write(root.resolve(privatepaths(1)),
          _published_document("Private｜非公開", "Private Dox review sentinel."))
        val before = privatepaths.map(path => Files.readAllBytes(root.resolve(path)).toVector)

        When("Cozy passes the source Realm unchanged to its pinned DoxSiteGenerator dependency")
        val generated = new DoxSiteGenerator(
          _smartdox_context,
          DoxSite.Config.default.copy(strategy = DoxSite.Strategy.Full)
        ).generate(Realm.create(DoxSite.realmConfig, root.toFile))

        Then("each locale exposes the flattened Document Project URL while ordinary pages retain their URL shape")
        _string(generated, "doxsite.d/ja/development-process/domain-modeling.html") should include("ドメインモデリング")
        _string(generated, "doxsite.d/en/development-process/domain-modeling.html") should include("Domain Modeling")
        _string(generated, "doxsite.d/ja/development-process/literate-modeling.html") should include("文芸モデリング")
        _string(generated, "doxsite.d/en/development-process/literate-modeling.html") should include("Literate Modeling")
        generated.get("doxsite.d/ja/development-process/domain-modeling.dox/index.html") shouldBe None
        generated.get("doxsite.d/en/development-process/domain-modeling.dox/index.html") shouldBe None

        And("includes retain their physical base and sibling links use canonical public routes in both locales")
        for (locale <- Vector("ja", "en")) {
          val packagehtml = _string(generated, s"doxsite.d/$locale/development-process/domain-modeling.html")
          val ordinaryhtml = _string(generated, s"doxsite.d/$locale/development-process/literate-modeling.html")
          packagehtml should include("Package physical include sentinel.")
          packagehtml should not include "Ordinary physical include sentinel."
          packagehtml should include("literate-modeling.html")
          ordinaryhtml should include("Ordinary physical include sentinel.")
          ordinaryhtml should not include "Package physical include sentinel."
          ordinaryhtml should include("domain-modeling.html")
          Vector(packagehtml, ordinaryhtml).foreach { html =>
            html should not include "domain-modeling.dox/index.html"
            html should not include "domain-modeling.dox/index.dox"
            html should not include "Private Markdown review sentinel."
            html should not include "Private Dox review sentinel."
          }
          generated.get(s"doxsite.d/$locale/development-process/domain-modeling.dox/review/private.html") shouldBe None
        }
        And("generation preserves every private input byte")
        privatepaths.map(path => Files.readAllBytes(root.resolve(path)).toVector) shouldBe before
      } finally {
        _delete(root)
      }
    }
    }

    "admitted Markdown publication" which {
      "publish both suffixes with YAML authority and default Pure body semantics" in {
        Given("ordinary .md and .markdown sources with published YAML metadata under an explicit bilingual site")
        val root = _fixture()
        try {
          _write_multilocale_config(root)
          val sources = Vector("pure-md.md" -> "Pure MD", "pure-markdown.markdown" -> "Pure Markdown")
          sources.foreach { case (path, title) =>
            _write(root.resolve(path),
              s"""|---
                  |title: $title
                  |status: published
                  |---
                  |# HEAD
                  |status=draft
                  |
                  |# Ordinary heading
                  |
                  |Ordinary *emphasis*.
                  |
                  || Name | Value |
                  || --- | --- |
                  || Native | Table |
                  |
                  |site:[missing.dox]
                  |
                  |include::missing.dox[]
                  |""".stripMargin)
          }
          And("the literal include target is absent")
          Files.exists(root.resolve("missing.dox")) shouldBe false

          When("Cozy passes the unchanged Realm to the actual default DoxSiteGenerator")
          val generated = new DoxSiteGenerator(
            _smartdox_context,
            DoxSite.Config.default
          ).generate(Realm.create(DoxSite.realmConfig, root.toFile))

          Then("YAML publication admits each suffix in both locales while HEAD and draft-looking text remain visible body")
          for {
            locale <- Vector("ja", "en")
            slug <- Vector("pure-md", "pure-markdown")
          } {
            val html = _string(generated, s"doxsite.d/$locale/$slug.html")
            html should include("HEAD")
            html should include("status=draft")
            html should include("Ordinary heading")
            html should include("<em>emphasis</em>")
            html should include regex "(?i:<table)"
            html should include regex "(?i:<thead>)"
            html should include regex "(?i:<th>)Name(?i:</th>)"
            html should include regex "(?i:<tbody>)"
            html should include regex "(?i:<td>)Native(?i:</td>)"
            html should include regex "(?i:<td>)Table(?i:</td>)"
            html should include("site:[missing.dox]")
            html should include("include::missing.dox[]")
          }
          And("generation completes without resolving or creating the missing include target")
          Files.exists(root.resolve("missing.dox")) shouldBe false
        } finally {
          _delete(root)
        }
      }
    }

    "public document metadata" which {
      "retain physical package evidence with one logical identity and canonical route per locale" in {
        Given("published bilingual ordinary and package documents beside unselected Markdown and published Dox descendants")
        val root = _fixture()
        try {
          _write_multilocale_config(root)
          _write(root.resolve("guide/ordinary.dox"),
            _published_document("Ordinary｜通常記事", "Package: site:[article.dox/index.html]"))
          _write(root.resolve("guide/article.dox/index.dox"),
            _published_document("Article｜記事", "Ordinary: site:[ordinary.dox]"))
          val privatepaths = Vector("guide/article.dox/review/private.md", "guide/article.dox/review/private.dox")
          _write(root.resolve(privatepaths.head), "_source\n\n# Private review\n\nMetadata private Markdown sentinel.\n")
          _write(root.resolve(privatepaths(1)),
            _published_document("Private｜非公開", "Metadata private Dox sentinel."))
          val before = privatepaths.map(path => Files.readAllBytes(root.resolve(path)).toVector)
          val context = _smartdox_context

          When("the actual public SmartDox site generates Full metadata with the existing bilingual compatibility output")
          val site = DoxSite.create(context, root.toFile, Some("site"),
            DoxSite.Config.default.copy(
              strategy = DoxSite.Strategy.Full,
              siteOutput = DoxSite.Config.SiteOutput.simplemodelingOrgCompatibility))
          val generated = site.toRealm(context)
          implicit val i18ncontext = context.i18NContext
          val fragmentjson = _string(generated, "metadata/documents/fragments.json")
          val fragments = parse(fragmentjson).toOption.get.hcursor.downField("fragments").as[Vector[io.circe.Json]].toOption.get
          val article = fragments.filter(_.hcursor.get[String]("public_path").toOption.contains("guide/article.html"))
          val links = site.metadata.linkCollection.get

          Then("exactly one package fragment per locale retains the physical source and canonical public path")
          article.size shouldBe 2
          article.map(_.hcursor.get[String]("locale").toOption.get).sorted shouldBe Vector("en", "ja")
          article.foreach { fragment =>
            fragment.hcursor.get[String]("source_path").toOption.get shouldBe "guide/article.dox/index.dox"
            fragment.hcursor.get[String]("public_path").toOption.get shouldBe "guide/article.html"
          }
          And("LinkCollection exposes the logical package identity without its physical index or private descendants")
          links.get("guide/article.dox") should not be empty
          links.get("guide/ordinary.dox") should not be empty
          links.get("guide/article.dox/index.dox") shouldBe None
          privatepaths.foreach { path =>
            links.get(path) shouldBe None
            fragmentjson should not include path
          }
          And("generated pages use canonical routes and exclude private descendant content in both locales")
          for (locale <- Vector("ja", "en")) {
            val articlehtml = _string(generated, s"$locale/guide/article.html")
            val ordinaryhtml = _string(generated, s"$locale/guide/ordinary.html")
            articlehtml should include("ordinary.html")
            ordinaryhtml should include("article.html")
            generated.get(s"$locale/guide/article.dox/index.html") shouldBe None
            generated.get(s"$locale/guide/article.dox/review/private.html") shouldBe None
            Vector(articlehtml, ordinaryhtml).foreach { html =>
              html should not include "article.dox/index.html"
              html should not include "Metadata private Markdown sentinel."
              html should not include "Metadata private Dox sentinel."
            }
          }
          And("metadata and page generation preserve all private input bytes")
          privatepaths.map(path => Files.readAllBytes(root.resolve(path)).toVector) shouldBe before
        } finally {
          _delete(root)
        }
      }
    }
  }

  private def _fixture(): Path = {
    val target = Path.of("target", "phase75", "cozy-document-project-site-projection").toAbsolutePath.normalize
    Files.createDirectories(target)
    Files.createTempDirectory(target, "site-")
  }

  private def _write_multilocale_config(root: Path): Unit =
    _write(root.resolve("site.conf"),
      """|site.output.locale_mode = "multi_locale_subdirs"
         |site.output.default_locale = "ja"
         |""".stripMargin)

  private def _published_document(title: String, body: String): String =
    s"$title\n====================================\n\n# HEAD\n\nstatus=published\npublished_at=2026-10-01\n\n# Body\n\n$body\n"

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
