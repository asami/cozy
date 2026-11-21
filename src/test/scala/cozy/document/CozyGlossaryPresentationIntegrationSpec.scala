package cozy.document

import java.net.URI
import io.circe.Json
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.smartdox.semanticweb.TermPresentation.{Emphasis, Role}

final class CozyGlossaryPresentationIntegrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "The SmartDox term handoff" should {
    "preserve resolved identities and labels for each display profile" in {
      Given("one resolved named pattern with an explicitly emphasized Core binding")
      val binding = CozyDocumentPatternCodec.TermBinding("retry", "claim", "sales", "https://example.org/term", "https://example.org/concept", "https://example.org/page", "named-pattern", Some("bold"), Some("pattern"))
      val identity = CozyGlossaryPresentation.PageIdentity(binding.space, binding.term, binding.concept, binding.page)
      val page = CozyGlossaryPresentation.Page(identity, Vector.empty, Vector.empty, None, None, Json.Null, "/glossary/retry")
      def _selected_(text: String, language: String): CozyGlossaryPresentation.ResolvedSelection =
        CozyGlossaryPresentation.ResolvedSelection(identity, page.link, Some(CozyGlossaryPresentation.Selection(text, Some(language), None)), page)
      val term = CozyDocumentDescriptionV2.ResolvedTerm("block", Some("item"), 2, binding, "再試行",
        _selected_("Retry", "en"), Vector("original" -> _selected_("Retry", "en"),
          "established-japanese" -> _selected_("リトライ", "ja"), "japanese" -> _selected_("再試行", "ja")))
      When("it is converted for the shared renderer")
      val result = CozyDocumentTermPresentation.occurrence(term, "ja", Some(new URI("https://example.org/")))
      val options = org.smartdox.semanticweb.TermPresentation.Options(
        org.smartdox.semanticweb.TermPresentation.Profile.Japanese, true, Vector(result))
      val document = org.smartdox.Document(org.smartdox.Head(), org.smartdox.Body(List(
        org.smartdox.Paragraph(List(org.smartdox.Text("Before "),
          org.smartdox.semanticweb.TermPresentation.marker(result), org.smartdox.Code(org.smartdox.Text("RetryCode")))))))
      val html = CozyDocumentProjectSmartDoxArticleHtml.renderTyped(java.nio.file.Paths.get("index.dox"),
        CozyDocumentTermPresentation.RenderInput(document, options), CozyDocumentFigureAssets.empty, "ja").html
      Then("the original identity, resolved link, style role, emphasis and three labels survive")
      result.termId shouldBe binding.term
      result.conceptId shouldBe binding.concept
      result.uri.map(_.toString) shouldBe Some("https://example.org/glossary/retry")
      html should include ("再試行")
      html should include ("RetryCode")
      html should include ("smartdox-term-named-pattern")
      html should include ("https://example.org/glossary/retry")
      result.role shouldBe Role.Named
      result.emphasis shouldBe Emphasis.Bold
      Vector(result.original, result.establishedJapanese, result.japanese).map(_.map(_.text)) shouldBe
        Vector(Some("Retry"), Some("リトライ"), Some("再試行"))
      result.id shouldBe CozyDocumentTermPresentation.occurrence(term, "ja", Some(new URI("https://example.org/"))).id
      intercept[IllegalArgumentException] { CozyDocumentTermPresentation.occurrence(term, "ja", None) }
    }
  }
}
