package cozy.document

import java.net.URI
import io.circe.Json
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.smartdox.semanticweb.TermPresentation.{Emphasis, Role}

final class CozyGlossaryPresentationIntegrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "The SmartDox term handoff" should {
    "build the packaged bilingual Core through producer and public document entry points" in {
      Given("authored LexiDox and localized DDD inputs")
      val root = java.nio.file.Files.createTempDirectory("cozy-p93-portable").toRealPath()
      try {
        val resource = java.nio.file.Paths.get(getClass.getResource("/lexidox/phase93-acceptance").toURI)
        val sources = Vector("site.conf", "presentation/retry.dox", "presentation/data-mapper.dox",
          "presentation/aggregate.dox", "presentation/repository.dox", "presentation/references.dox")
        val realm = sources.foldLeft(org.goldenport.realm.Realm.create()) { (current, name) =>
          current.setContent(name, java.nio.file.Files.readString(resource.resolve("lexidox-source").resolve(name)))
        }
        val context = org.smartdox.generator.Context.create()
        val catalog = java.nio.file.Files.createDirectory(root.resolve("catalog"))
        org.smartdox.doxsite.DoxSite.create(context, realm, "site",
          org.smartdox.doxsite.DoxSite.Config.default.copy(strategy = org.smartdox.doxsite.DoxSite.Strategy.Full))
          .toRealm(context).export(catalog.toFile)
        When("each locale and profile uses the normal build command")
        Vector("ja", "en").foreach { locale =>
          val project = java.nio.file.Files.createDirectory(root.resolve(s"$locale.dox"))
          def _copy_(source: String, destination: String): Unit = {
            val target = project.resolve(destination)
            java.nio.file.Files.createDirectories(target.getParent)
            java.nio.file.Files.copy(resource.resolve(source), target)
          }
          _copy_("content/core.yaml", "content/core.yaml")
          _copy_(s"content/$locale/document.yaml", s"content/$locale/document.yaml")
          _copy_(s"content/$locale/confirmation-vocabulary.yaml", s"content/$locale/confirmation-vocabulary.yaml")
          val figure = io.circe.parser.parse(java.nio.file.Files.readString(resource.resolve("expected-labels.json"))).toOption.get
            .hcursor.get[String]("figurePngBase64").toOption.get
          val image = project.resolve(s"images/order-model-execution-$locale.png")
          java.nio.file.Files.createDirectories(image.getParent)
          java.nio.file.Files.write(image, java.util.Base64.getDecoder.decode(figure))
          _copy_(s"project/$locale/index.dox", "index.dox")
          val targets = java.nio.file.Files.readString(resource.resolve("project/document-project-targets.yaml"))
            .replace("content/ja/", s"content/$locale/")
          java.nio.file.Files.createDirectories(project.resolve("build"))
          java.nio.file.Files.writeString(project.resolve("build/document-project-targets.yaml"), targets)
          val settings = java.nio.file.Files.readString(resource.resolve("settings/terms.yaml"))
          Vector("original" -> "Retry", "established-japanese" -> "リトライ", "japanese" -> "再試行").foreach { case (profile, label) =>
            java.nio.file.Files.writeString(project.resolve("build/terms.yaml"), settings.replace("\"original\"", "\"" + profile + "\""))
            CozyDocumentProject.execute(List("document-project", "build", project.toString, "--target", "smartdox-article-html",
              "--locale", locale, "--lexidox-root", catalog.toString, "--term-presentation", "build/terms.yaml", "--force")) shouldBe true
            val html = java.nio.file.Files.readString(project.resolve("target/document-project/local-build/smartdox-article-html/index.html"))
            Then("selected and alternative labels retain identity, link and code")
            html should include (s">$label</a>")
            html should include ("data-label-original=\"Retry\"")
            html should include ("data-label-established-japanese=\"リトライ\"")
            html should include ("data-label-japanese=\"再試行\"")
            html should include ("data-term-id=\"https://example.org/term/retry\"")
            html should include ("EntityStore")
            html should include ("font-weight:bold")
          }
          CozyDocumentProject.execute(List("document-project", "build", project.toString, "--target", "smartdox-article-html", "--force")) shouldBe true
          java.nio.file.Files.readString(project.resolve("target/document-project/local-build/smartdox-article-html/index.html")) should not include ("data-smartdox-term-control")
        }
      } finally {
        import scala.collection.JavaConverters._
        val stream = java.nio.file.Files.walk(root)
        try stream.iterator().asScala.toVector.reverse.foreach(java.nio.file.Files.deleteIfExists)
        finally stream.close()
      }
    }

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

/** Explicit, offline comparison against a retained Phase 92 producer fixture and baseline. */
object CozyPhase92CompatibilityDriver {
  def main(args: Array[String]): Unit = {
    require(args.length == 3, "usage: CozyPhase92CompatibilityDriver <fixture-root> <baseline-metadata-root> <new-output-root>")
    val fixture = java.nio.file.Paths.get(args(0)).toAbsolutePath.normalize()
    val baseline = java.nio.file.Paths.get(args(1)).toAbsolutePath.normalize()
    val output = java.nio.file.Paths.get(args(2)).toAbsolutePath.normalize()
    require(!java.nio.file.Files.exists(output), "output root must not already exist")
    java.nio.file.Files.createDirectories(output)
    val names = Vector("site.conf", "spaces/sales.dox", "spaces/history.dox", "spaces/pattern.dox",
      "spaces/reference.dox", "spaces/legacy.dox")
    val realm = names.sorted.foldLeft(org.goldenport.realm.Realm.create()) { (current, name) =>
      current.setContent(name, java.nio.file.Files.readString(fixture.resolve(name)))
    }
    val config = cozy.bok.CozyBok.BuildConfig.create(List(output.toString, "--strategy", "preview"))
    java.nio.file.Files.createDirectories(config.doxsitePath)
    val context = org.smartdox.generator.Context.create()
    org.smartdox.doxsite.DoxSite.create(context, realm, "site",
      org.smartdox.doxsite.DoxSite.Config.default.copy(strategy = org.smartdox.doxsite.DoxSite.Strategy.Full))
      .toRealm(context).export(config.doxsitePath.toFile)
    val generated = config.doxsitePath.resolve("metadata/lexidox")
    def _json_(path: java.nio.file.Path): Json =
      io.circe.parser.parse(java.nio.file.Files.readString(path)).fold(throw _, identity)
    // Only presentation additions are removed; all baseline fields and array order remain compared.
    def _canonical_(value: Json): Json = value.mapObject { root =>
      val pages = root("pages").flatMap(_.asArray).map(_.map(_.mapObject(_.remove("presentation"))))
      val references = root("references").flatMap(_.asArray).map(_.map(_.mapObject(_.remove("visible_text"))))
      val without = root.remove("presentation_diagnostics")
      val withpages = pages.fold(without)(xs => without.add("pages", Json.fromValues(xs)))
      references.fold(withpages)(xs => withpages.add("references", Json.fromValues(xs)))
    }
    val oldwire = _json_(baseline.resolve("knowledge-v1.json"))
    val newwire = _json_(generated.resolve("knowledge-v1.json"))
    require(_canonical_(oldwire) == _canonical_(newwire), "Phase 92 canonical knowledge envelope differs")
    Vector("ttl", "jsonld").foreach { extension =>
      val name = s"knowledge-v1.$extension"
      require(java.util.Arrays.equals(java.nio.file.Files.readAllBytes(baseline.resolve(name)),
        java.nio.file.Files.readAllBytes(generated.resolve(name))), s"Phase 92 RDF differs: $name")
    }
    val catalog = cozy.bok.CozyBokImplementation._presentation_catalog(config).getOrElse(
      throw new IllegalStateException("Cozy did not admit generated LexiDox metadata"))
    require(catalog.pages.nonEmpty, "no admitted pages")
    val profiles = Vector("original", "established-japanese", "japanese")
    catalog.pages.foreach { page =>
      val id = page.identity
      val role = page.presentation.map(_.role).getOrElse("ordinary-term")
      val binding = CozyDocumentPatternCodec.TermBinding("fixture", "fixture", id.space, id.term, id.concept,
        id.page, role, None, None)
      val visible = page.titles.headOption.map(_.text).getOrElse(throw new IllegalStateException("missing title"))
      val term = CozyDocumentDescriptionV2.InlineTerm(binding, visible)
      Vector("ja", "en").foreach { locale =>
        val selections = profiles.map { profile =>
          CozyGlossaryPresentation.resolve(term, catalog, locale, Some(profile)).fold(
            problem => throw new IllegalStateException(s"$locale/$profile: ${problem.code}: ${problem.message}"), identity)
        }
        require(selections.forall(_.identity == id), s"identity changed for ${id.page}")
        require(selections.forall(_.link == page.link), s"link changed for ${id.page}")
      }
    }
    java.nio.file.Files.writeString(output.resolve("compatibility-result.txt"),
      s"Phase 92 canonical JSON, Turtle and JSON-LD matched; ${catalog.pages.size} admitted pages retained identity and link across ja/en and three profiles.\n")
  }
}
