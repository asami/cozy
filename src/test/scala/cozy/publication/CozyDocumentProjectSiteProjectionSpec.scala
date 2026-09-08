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
import org.smartdox.generators.DoxSiteGenerator

/*
 * @since   Sep.  8, 2026
 * @version Sep.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentProjectSiteProjectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Document Project site projection" should {
    "pass a Document Project package unchanged to the actual SmartDox generated Realm" in {
      val target = Path.of("target", "cozy-document-project-site-projection")
      Files.createDirectories(target)
      val root = Files.createTempDirectory(target, "site-")
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
             |""".stripMargin)

        When("Cozy passes the source Realm unchanged to its pinned DoxSiteGenerator dependency")
        val generated = new DoxSiteGenerator(
          _smartdox_context,
          DoxSite.Config.default
        ).generate(Realm.create(DoxSite.realmConfig, root.toFile))

        Then("each locale exposes the flattened Document Project URL while ordinary pages retain their URL shape")
        _string(generated, "doxsite.d/ja/development-process/domain-modeling.html") should include("ドメインモデリング")
        _string(generated, "doxsite.d/en/development-process/domain-modeling.html") should include("Domain Modeling")
        _string(generated, "doxsite.d/ja/development-process/literate-modeling.html") should include("文芸モデリング")
        _string(generated, "doxsite.d/en/development-process/literate-modeling.html") should include("Literate Modeling")
        generated.get("doxsite.d/ja/development-process/domain-modeling.dox/index.html") shouldBe None
        generated.get("doxsite.d/en/development-process/domain-modeling.dox/index.html") shouldBe None
      } finally {
        _delete(root)
      }
    }
  }

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
