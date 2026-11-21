package cozy.document

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class CozyGlossaryPresentationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CozyGlossaryPresentation._

  "Glossary presentation" should {
    "select the profile candidate without changing the admitted labels" in {
      Given("an original, a canonical Japanese label and an explicitly established translation")
      val labels = Labels(Vector(Label("Retry", "en"), Label("再試行", "ja")), Vector(Label("リトライ", "ja")))
      val original = Selector("titles", "en", "Retry")
      val value = Presentation("named-pattern", original, Vector(Selector("titles", "ja", "再試行")), Some(Selector("aliases", "ja", "リトライ")), Vector.empty)
      When("each profile is applied to the same resolved page")
      val results = Vector("original", "established-japanese", "japanese").map(x => select(labels, Some(value), "ja", Some(x), "Retry"))
      Then("the labels follow the specified precedence")
      results.map(_.toOption.flatten.map(_.label)) shouldBe Vector(Some("Retry"), Some("リトライ"), Some("再試行"))
      select(labels, Some(value.copy(establishedJapanese = None)), "ja", Some("established-japanese"), "Retry").toOption.flatten.map(_.label) shouldBe Some("Retry")
    }

    "match complete document language tags and preserve explicit preference order" in {
      Given("a regional Japanese preference and no canonical Japanese selector")
      val labels = Labels(Vector(Label("Retry", "en")), Vector(Label("再試行", "ja-JP")))
      val original = Selector("titles", "en", "Retry")
      val preference = Preference("ja-JP", Selector("aliases", "ja-jp", "再試行"))
      val value = Presentation("named-pattern", original, Vector.empty, None, Vector(preference))
      When("the Japanese profile is selected for exact and different language tags")
      val exact = select(labels, Some(value), "JA-jp", Some("japanese"), "Retry")
      val different = select(labels, Some(value), "ja", Some("japanese"), "Retry")
      Then("only the complete matching tag selects the preference")
      exact.toOption.flatten.map(_.label) shouldBe Some("再試行")
      different.toOption.flatten.map(_.label) shouldBe Some("Retry")
    }

    "preserve baseline pages and report malformed or unsupported optional metadata" in {
      Given("a schema-shaped wire file with untagged legacy labels")
      val path = java.nio.file.Files.createTempFile("lexidox-presentation", ".json")
      val page = """{"space":"sales","term_iri":"https://example.org/t","concept_iri":"https://example.org/c","page_iri":"https://example.org/p","titles":[{"text":"Work"}],"aliases":[]}"""
      val source = s"""{"schema":"lexidox-knowledge/1","pages":[$page],"references":[],"diagnostics":[],"presentation_diagnostics":[]}"""
      try {
        java.nio.file.Files.writeString(path, source)
        When("legacy, unsupported, malformed and duplicate-key files are read")
        val legacy = read(path)
        java.nio.file.Files.writeString(path, source.replace("\"aliases\":[]", "\"aliases\":[],\"presentation\":{\"version\":2}"))
        val unsupported = read(path)
        java.nio.file.Files.writeString(path, source.replace("\"aliases\":[]", "\"aliases\":[],\"presentation\":null"))
        val malformed = read(path)
        java.nio.file.Files.writeString(path, source.replace("\"schema\":", "\"schema\":\"lexidox-knowledge/1\",\"schema\":"))
        val duplicate = intercept[WireFault](read(path))
        Then("legacy labels remain untagged, failures retain the opaque page and duplicates are structurally rejected")
        legacy.pages.head.titles.head.language shouldBe None
        unsupported.pages.head.presentationIssue.map(_.code) shouldBe Some("LEX_PRESENTATION_VERSION")
        malformed.pages.head.presentationIssue.map(_.code) shouldBe Some("LEX_PRESENTATION_METADATA")
        unsupported.pages.head.identity shouldBe legacy.pages.head.identity
        duplicate.code shouldBe "LEX_PRESENTATION_METADATA"
      } finally java.nio.file.Files.deleteIfExists(path)
    }

    "retain legacy output and reject ambiguous selectors" in {
      Given("an ordinary term and a selector matching duplicate labels")
      val label = Label("Retry", "en")
      val selector = Selector("titles", "en", "Retry")
      val value = Presentation("ordinary-term", selector, Vector.empty, None, Vector.empty)
      When("no profile applies or the selector is ambiguous")
      val legacy = select(Labels(Vector(label), Vector.empty), Some(value), "en", None, "Retry")
      val ambiguous = select(Labels(Vector(label, label), Vector.empty), Some(value), "en", Some("original"), "Retry")
      Then("legacy display remains in control and ambiguity is an error")
      legacy shouldBe Right(None)
      ambiguous.isLeft shouldBe true
      select(Labels(Vector(label), Vector.empty), Some(value.copy(original = selector.copy(language = "en--US"))), "en", Some("original"), "Retry").isLeft shouldBe true
    }
  }
}
