package cozy.media

import java.nio.file.{Files, Path}
import io.circe.parser.parse
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 25, 2026
 * @version Aug. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyMediaArticleScaffoldSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy media article scaffold" should {
    "create the canonical authored package without fabricating a renderer or PPTX output" in {
      _with_temp_dir("article-media-scaffold") { root =>
        Given("an absent package destination and canonical separate-token command values")
        val target = root.resolve("article-media")

        When("Cozy scaffolds an article media package through canonical separate-token options")
        val result = CozyMediaArticleScaffold.execute(List(
          "article-example", "--profile", "business", "--language", "en", "--save", target.toString
        ))

        Then("the authored sources, review state, template directory, and deterministic output directories exist")
        Vector(
          "article.dox", "brief.json", "media.yaml", "infographic/infographic.svg",
          "presentation/slide-ir.yaml", "review/state.yaml", "review/manifest.json",
          "presentation/template", "target/cozy-media/slides", "target/cozy-media/rendered"
        ).foreach(path => Files.exists(target.resolve(path)) shouldBe true)
        result should include("status: created")
        Files.exists(target.resolve("presentation/template/approved-template.pptx")) shouldBe false
        Files.exists(target.resolve("target/cozy-media/rendered/article.pptx")) shouldBe false

        And("the JSON contracts carry only the documented scaffold identity")
        parse(Files.readString(target.resolve("brief.json"))).fold(_ => None, _.asObject).map(_.keys.toSet) shouldBe Some(Set("knowledge", "language"))
        parse(Files.readString(target.resolve("review/manifest.json"))).fold(_ => None, _.asObject).map(_.keys.toSet) shouldBe Some(Set("schema", "target", "knowledge", "language", "status"))
        Files.readString(target.resolve("review/manifest.json")) should include("cozy.media.presentation-review.scaffold.v1")

        And("the YAML descriptor and semantic IR parse into their documented identities")
        val media = _structured_document(target.resolve("media.yaml"))
        val mediaobject = media.asObject.get
        mediaobject("schema").flatMap(_.asString) shouldBe Some("cozy.media.v1")
        mediaobject("knowledge").flatMap(_.asObject).flatMap(_("id")).flatMap(_.asString) shouldBe Some("article-example")
        mediaobject("profiles").flatMap(_.asObject).flatMap(_("business")).flatMap(_.asObject).flatMap(_("presentation")).flatMap(_.asObject).flatMap(_("renderer")).flatMap(_.asObject).flatMap(_("name")).flatMap(_.asString) shouldBe Some("approved-presentation-renderer")
        mediaobject("resources").flatMap(_.asArray).map(_.map(_.asObject.flatMap(_("id")).flatMap(_.asString))) shouldBe Some(Vector(Some("infographic-en"), Some("article-pdf-en"), Some("presentation-en")))
        val slideir = _structured_document(target.resolve("presentation/slide-ir.yaml"))
        val slideobject = slideir.asObject.get
        slideobject("schema").flatMap(_.asString) shouldBe Some("cozy.slide-ir.v1")
        slideobject("knowledge").flatMap(_.asString) shouldBe Some("article-example")
        slideobject("language").flatMap(_.asString) shouldBe Some("en")
        slideobject("slides").flatMap(_.asArray).flatMap(_.headOption).flatMap(_.asObject).flatMap(_("id")).flatMap(_.asString) shouldBe Some("overview")
      }
    }

    "reject non-business profiles and unsafe language values before creating a destination" in {
      _with_temp_dir("article-media-invalid") { root =>
        Given("absent destinations and values outside the scaffold grammar")
        val profiletarget = root.resolve("invalid-profile")
        val languagetarget = root.resolve("invalid-language")

        When("Cozy receives an unsupported profile or injection-shaped language")
        val profilefailure = intercept[RuntimeException](CozyMediaArticleScaffold.scaffold("article-example", "public", "en", profiletarget))
        val languagefailure = intercept[RuntimeException](CozyMediaArticleScaffold.scaffold("article-example", "business", "en\"\ninvalid: true", languagetarget))

        Then("both inputs are rejected before any destination is created")
        profilefailure.getMessage should include("exactly business")
        languagefailure.getMessage should include("language must match")
        Files.exists(profiletarget) shouldBe false
        Files.exists(languagetarget) shouldBe false
      }
    }

    "reject an existing destination instead of merging or overwriting it" in {
      _with_temp_dir("article-media-existing") { root =>
        Given("an existing destination directory")
        val target = root.resolve("article-media")
        Files.createDirectories(target)

        When("Cozy attempts the same scaffold")
        val failure = intercept[RuntimeException](CozyMediaArticleScaffold.scaffold("article-example", "business", "en", target))

        Then("the existing destination remains protected")
        failure.getMessage should include("already exists")
        Files.isDirectory(target) shouldBe true
      }
    }
  }

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val root = Files.createTempDirectory(name)
    try body(root)
    finally Files.walk(root).sorted(java.util.Comparator.reverseOrder()).forEach(path => Files.deleteIfExists(path))
  }

  private def _structured_document(path: Path): io.circe.Json =
    StructuredDocumentLoader.loadJson(InputSource(path.toFile)).take
}
