package cozy.document

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Sep. 12, 2026
 * @version Sep. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentConfirmationDriverSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Document Confirmation Driver" should {
    "render each real Article 9 v2 confirmation kind twice with deterministic bytes disclosed identities and native selection markup" in {
      _with_temp_dir("cozy-document-confirmation-driver-render") { root =>
        Given("the direct Article 9 Japanese v2 Core, Document, Summary, and generic vocabulary bundle")
        val fixture = _fixture(root)
        val documentvalidated = CozyDocumentDescriptionV2.loadDocument(fixture._1, fixture._2)
        val summaryvalidated = CozyDocumentDescriptionV2.loadSummary(fixture._1, fixture._2, fixture._3)
        val documentexpected = CozyDocumentConfirmationProjectionV2.render(documentvalidated, CozyDocumentConfirmationVocabulary.loadDocument(fixture._4, documentvalidated.description.locale))
        val summaryexpected = CozySummaryConfirmationProjectionV2.render(summaryvalidated, CozyDocumentConfirmationVocabulary.loadSummary(fixture._4, summaryvalidated.description.locale))
        val documentone = root.resolve("document-one.html")
        val documenttwo = root.resolve("document-two.html")
        val summaryone = root.resolve("summary-one.html")
        val summarytwo = root.resolve("summary-two.html")

        When("the closed command renders each matching confirmation kind twice")
        val documentreport = _execute(_document_command(fixture, documentone))
        _execute(_document_command(fixture, documenttwo))
        val summaryreport = _execute(_summary_command(fixture, summaryone))
        _execute(_summary_command(fixture, summarytwo))
        val documenthtml = Files.readString(documentone, StandardCharsets.UTF_8)
        val summaryhtml = Files.readString(summaryone, StandardCharsets.UTF_8)

        Then("each direct output is byte-identical, self-disclosing, traceable, and natively selectable")
        Files.readAllBytes(documentone).toVector shouldBe Files.readAllBytes(documenttwo).toVector
        Files.readAllBytes(summaryone).toVector shouldBe Files.readAllBytes(summarytwo).toVector
        documenthtml shouldBe documentexpected.html
        summaryhtml shouldBe summaryexpected.html
        documenthtml should include("data-output-identity=\"" + documentexpected.identity + "\"")
        summaryhtml should include("data-output-identity=\"" + summaryexpected.identity + "\"")
        documenthtml should include("<button type=\"button\" class=\"step-control\"")
        documenthtml should include("data-step-control=\"true\"")
        documenthtml should include("aria-controls=\"flow-application-modeling structure-application-modeling prose-region\"")
        documenthtml should include("画面設計へ進む前の確認")
        documenthtml should include("ロジックツリー")
        documenthtml should include("<h1>文書確認</h1>")
        documenthtml should include("<title>" + documentvalidated.description.document.title + "</title>")
        documenthtml should include("<div class=\"kicker\">" + documentvalidated.description.document.title + "</div>")
        val rootlabel = documentvalidated.description.labels.steps.find(_.stepRef == documentvalidated.core.core.root.id).get.text
        documenthtml should include("<article class=\"article\"><h2>" + rootlabel + "</h2>")
        summaryhtml should include("<button type=\"button\" class=\"unit-control\"")
        summaryhtml should include("data-summary-unit-control=\"true\"")
        summaryhtml should include("aria-controls=\"semantic-foundation-purpose evidence-foundation-purpose\"")
        summaryhtml should include("style=\"--summary-unit-count:6\"")
        summaryhtml should include("aria-controls=\"semantic-application-overview evidence-application-overview\"")
        summaryhtml should include("data-summary-overview-step=\"application-modeling\"")
        Vector("containment", "flow", "structure").foreach(region => summaryhtml should include(s"""data-overview-region="$region"""))
        summaryhtml should include("data-summary-diagram=\"true\"")
        summaryhtml should include("<h1>要約確認</h1>")
        summaryhtml should include("<title>" + summaryvalidated.description.summary.title + "</title>")
        summaryhtml should include("<div class=\"kicker\">" + summaryvalidated.description.summary.title + "</div>")
        summaryhtml should include("<h2>元情報と編集判断</h2>")
        summaryhtml should include("aria-label=\"元情報と編集判断\"")
        summaryhtml should include("<section><h3>ステップ</h3><ul class=\"primary-sources\">")
        summaryhtml should include("<ul class=\"retained-points\">")
        summaryhtml should include("<ul class=\"omission-list\">")
        summaryhtml should not include("-webkit-line-clamp")
        summaryhtml should include("このスライドで保持")
        summaryhtml should include("要約で省略")
        summaryhtml should include("詳細な出典と図の対応")
        summaryvalidated.description.summary.units should have size 6
        summaryvalidated.description.summary.units.head.overview shouldBe Some(CozyDocumentDescriptionV2.Overview("application-modeling"))
        summaryvalidated.description.summary.units.tail.map(_.id) shouldBe Vector("foundation-purpose", "use-case-model", "collaboration-interaction", "executable-elements", "review-and-realization")
        summaryvalidated.description.summary.units.head.diagram.get.focusItem shouldBe None
        summaryvalidated.description.summary.units.tail.map(_.diagram.get.focusItem) shouldBe Vector(
          Some("foundation-application-model"), Some("use-case-model-realization"), Some("collaboration-interaction-responsibility"), Some("executable-elements-state-machine"), Some("review-and-realization-review")
        )
        summaryvalidated.description.summary.units.foreach { unit =>
          unit.diagram should not be empty
          unit.diagram.get.items.foreach(item =>
            if (item.kind == "node") summaryvalidated.document.core.nodesById should contain key item.ref
            else summaryvalidated.document.core.depthFirstSteps.map(_.id) should contain(item.ref)
          )
          unit.diagram.get.edges.foreach(edge =>
            if (edge.kind == "relation") summaryvalidated.document.core.relationsById should contain key edge.ref
            else summaryvalidated.document.core.depthFirstSteps.flatMap(_.flow.transitions.map(_.id)) should contain(edge.ref)
          )
        }
        documentreport should include(documentvalidated.coreIdentity)
        documentreport should include(documentvalidated.documentIdentity)
        summaryreport should include(summaryvalidated.summaryIdentity)
        documentvalidated.coreIdentity shouldBe _identity(fixture._1)
        documentvalidated.documentIdentity shouldBe _identity(fixture._2)
        summaryvalidated.summaryIdentity shouldBe _identity(fixture._3)
      }
    }

    "render an admitted test-local sequence and next Flow through both strict confirmation commands" in {
      _with_temp_dir("cozy-confirmation-driver-sequence") { root =>
        Given("a test-only Catalog variant with Root sequence next edges and correctly rebound raw identities")
        val fixture = _fixture(root)
        val oldcoreidentity = _identity(fixture._1)
        val olddocumentidentity = _identity(fixture._2)
        val coretext = Files.readString(fixture._1, StandardCharsets.UTF_8)
        val boundary = coretext.indexOf("\n  steps:\n")
        boundary should be > 0
        val roottext = coretext.substring(0, boundary).replace("pattern: mapping", "pattern: sequence").replace("role: source", "role: step").replace("role: target", "role: step").replace("relationType: maps-to", "relationType: next").replace("relationType: depends-on", "relationType: next")
        Files.writeString(fixture._1, roottext + coretext.substring(boundary), StandardCharsets.UTF_8)
        Files.writeString(fixture._2, Files.readString(fixture._2, StandardCharsets.UTF_8).replace(oldcoreidentity, _identity(fixture._1)), StandardCharsets.UTF_8)
        Files.writeString(fixture._3, Files.readString(fixture._3, StandardCharsets.UTF_8).replace(oldcoreidentity, _identity(fixture._1)).replace(olddocumentidentity, _identity(fixture._2)).replace("direction: forward", "direction: inverse"), StandardCharsets.UTF_8)
        val documentoutput = root.resolve("sequence-document.html")
        val summaryoutput = root.resolve("sequence-summary.html")

        When("the Document and inverse Summary diagrams are rendered through the production command")
        _execute(_document_command(fixture, documentoutput))
        _execute(_summary_command(fixture, summaryoutput))
        val documenthtml = Files.readString(documentoutput, StandardCharsets.UTF_8)
        val summaryhtml = Files.readString(summaryoutput, StandardCharsets.UTF_8)

        Then("unchanged fixed Catalog sequence step and next meanings have complete canonical and inverse wording")
        documenthtml should include("data-logical-pattern=\"sequence\"")
        documenthtml should include("順序")
        documenthtml should include("次へ")
        summaryhtml should include("data-logical-pattern=\"sequence\"")
        summaryhtml should include("前へ")
        summaryhtml should include("data-core-edge-type=\"next\"")
        summaryhtml should include("data-core-from=\"root-domain-model\" data-core-to=\"root-application-model\"")
        summaryhtml should include("data-display-from=\"root-application-model\" data-display-to=\"root-domain-model\"")
      }
    }

    "retain article-grounded missing concepts and exact selected relationships across both review screens" in {
      _with_temp_dir("cozy-document-confirmation-driver-concepts") { root =>
        Given("the Article 9 v2 bundle with participants, StateMachine and reviewed proposal decisions")
        val fixture = _fixture(root)
        val validated = CozyDocumentDescriptionV2.loadSummary(fixture._1, fixture._2, fixture._3)
        val core = validated.document.core
        val vocabulary = CozyDocumentConfirmationVocabulary.loadSummary(fixture._4, "ja")

        When("the exact admitted Summary is rendered")
        val html = CozySummaryConfirmationProjectionV2.render(validated, vocabulary).html

        Then("all three concepts are selected explicitly while CML still depends on developer review")
        Vector("collaboration-participants", "execution-state-machine", "conclusion-ai-proposal-decision").foreach { node =>
          core.nodesById should contain key node
          validated.description.summary.units.flatMap(_.diagram.toVector.flatMap(_.items.map(_.ref))) should contain(node)
        }
        val dependency = core.relationsById("conclusion-cml-depends-on-review")
        dependency.from shouldBe "conclusion-cml"
        dependency.to shouldBe "conclusion-review"
        html should include("参加者・役割")
        html should include("状態機械")
        html should include("生成AIの提案の採否")
        html should include("data-logical-pattern=\"causal-chain\"")
        html should include("依存される")
        html should include("data-core-from=\"conclusion-cml\" data-core-to=\"conclusion-review\"")
        html should include("data-display-from=\"conclusion-review\" data-display-to=\"conclusion-cml\"")
      }
    }

    "reject stale v2 inputs, incompatible generic resources, and closed command forms before publication" in {
      _with_temp_dir("cozy-document-confirmation-driver-rejection") { root =>
        Given("copied Article 9 v2 authorities with separately stale or invalid confirmation inputs")
        val stalecore = _fixture(Files.createDirectories(root.resolve("stale-core")))
        val staledocument = _fixture(Files.createDirectories(root.resolve("stale-document")))
        val invalidvocabulary = _fixture(Files.createDirectories(root.resolve("invalid-vocabulary")))
        Files.writeString(stalecore._1, Files.readString(stalecore._1, StandardCharsets.UTF_8) + "\n", StandardCharsets.UTF_8)
        Files.writeString(staledocument._2, Files.readString(staledocument._2, StandardCharsets.UTF_8) + "\n", StandardCharsets.UTF_8)
        Files.writeString(invalidvocabulary._4, Files.readString(invalidvocabulary._4, StandardCharsets.UTF_8).replace("locale: ja", "locale: en"), StandardCharsets.UTF_8)
        val stalecoreoutput = root.resolve("stale-core.html")
        val staledocumentoutput = root.resolve("stale-document.html")
        val invalidvocabularyoutput = root.resolve("invalid-vocabulary.html")
        val missingoutput = root.resolve("missing.html")
        val crosskindoutput = root.resolve("cross-kind.html")
        val duplicateoutput = root.resolve("duplicate.html")

        When("the command receives stale upstream bytes, a mismatched resource, or an invalid closed form")
        val failures = Vector(
          _failure(_document_command(stalecore, stalecoreoutput)),
          _failure(_summary_command(staledocument, staledocumentoutput)),
          _failure(_document_command(invalidvocabulary, invalidvocabularyoutput)),
          _failure(List("document-project", "confirmation", "render", "--core", invalidvocabulary._1.toString, "--document", invalidvocabulary._2.toString, "--kind", "document", "--save", missingoutput.toString)),
          _failure(List("document-project", "confirmation", "render", "--core", invalidvocabulary._1.toString, "--document", invalidvocabulary._2.toString, "--vocabulary", invalidvocabulary._4.toString, "--summary", invalidvocabulary._3.toString, "--kind", "document", "--save", crosskindoutput.toString)),
          _failure(List("document-project", "confirmation", "render", "--core", invalidvocabulary._1.toString, "--document", invalidvocabulary._2.toString, "--vocabulary", invalidvocabulary._4.toString, "--vocabulary", invalidvocabulary._4.toString, "--kind", "document", "--save", duplicateoutput.toString))
        )

        Then("no stale or malformed request reaches a rendered output")
        val reasons = failures.map(_.getMessage).mkString("\n")
        reasons should include("DESCRIPTION_V2_DOCUMENT_CORE")
        reasons should include("DESCRIPTION_V2_SUMMARY_DOCUMENT")
        reasons should include("CONFIRMATION_VOCABULARY_LOCALE")
        reasons should include("CONFIRMATION_CLI")
        Vector(stalecoreoutput, staledocumentoutput, invalidvocabularyoutput, missingoutput, crosskindoutput, duplicateoutput).foreach(path => Files.exists(path, LinkOption.NOFOLLOW_LINKS) shouldBe false)
      }
    }

    "reject unsafe direct publication destinations while retaining generic vocabulary free of Article 9 wording" in {
      _with_temp_dir("cozy-document-confirmation-driver-publication") { root =>
        Given("the valid Article 9 v2 bundle and unsafe requested output destinations")
        val fixture = _fixture(root)
        val nonhtml = root.resolve("document.txt")
        val target = root.resolve("target.html")
        val symlink = root.resolve("output-link.html")
        Files.writeString(target, "outside", StandardCharsets.UTF_8)
        Files.createSymbolicLink(symlink, target)
        val externalparent = Files.createDirectories(root.resolve("external-parent"))
        val symlinkparent = root.resolve("linked-parent")
        Files.createSymbolicLink(symlinkparent, externalparent)
        val linkedoutput = symlinkparent.resolve("linked.html")
        val directoryoutput = Files.createDirectories(root.resolve("directory.html"))
        val vocabulary = Files.readString(fixture._4, StandardCharsets.UTF_8)

        When("the closed document command is directed to an unsafe output")
        val failures = Vector(
          _failure(_document_command(fixture, nonhtml)),
          _failure(_document_command(fixture, symlink)),
          _failure(_document_command(fixture, linkedoutput)),
          _failure(_document_command(fixture, directoryoutput))
        )

        Then("all publication attempts fail before replacing an external target or creating output")
        failures.foreach(_.getMessage should include("CONFIRMATION_PATH"))
        Files.readString(target, StandardCharsets.UTF_8) shouldBe "outside"
        Files.isSymbolicLink(symlink) shouldBe true
        Files.isSymbolicLink(symlinkparent) shouldBe true
        Files.exists(linkedoutput, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.isDirectory(directoryoutput, LinkOption.NOFOLLOW_LINKS) shouldBe true
        vocabulary should not include "application-modeling"
        vocabulary should not include "ユースケースから実現モデルへ"
        vocabulary should not include "アプリケーションモデリング"
      }
    }
  }

  private def _fixture(root: Path): (Path, Path, Path, Path) = {
    val content = Files.createDirectories(root.resolve("content-v2"))
    val locale = Files.createDirectories(content.resolve("ja"))
    val core = content.resolve("core.yaml")
    val document = locale.resolve("document.yaml")
    val summary = locale.resolve("summary.yaml")
    val vocabulary = locale.resolve("confirmation-vocabulary.yaml")
    Files.copy(_resource("/cozy/document/phase-58/application-modeling/content-v2/core.yaml"), core)
    Files.copy(_resource("/cozy/document/phase-58/application-modeling/content-v2/ja/document.yaml"), document)
    Files.copy(_resource("/cozy/document/phase-59/application-modeling/content-v2/ja/summary.yaml"), summary)
    Files.copy(_resource("/cozy/document/phase-58/application-modeling/content-v2/ja/confirmation-vocabulary.yaml"), vocabulary)
    (core, document, summary, vocabulary)
  }

  private def _document_command(fixture: (Path, Path, Path, Path), output: Path): List[String] =
    List("document-project", "confirmation", "render", "--core", fixture._1.toString, "--document", fixture._2.toString, "--vocabulary", fixture._4.toString, "--kind", "document", "--save", output.toString)

  private def _summary_command(fixture: (Path, Path, Path, Path), output: Path): List[String] =
    List("document-project", "confirmation", "render", "--core", fixture._1.toString, "--document", fixture._2.toString, "--vocabulary", fixture._4.toString, "--summary", fixture._3.toString, "--kind", "summary", "--save", output.toString)

  private def _execute(args: List[String]): String = {
    val bytes = new ByteArrayOutputStream()
    Console.withOut(new PrintStream(bytes, true, "UTF-8")) {
      CozyDocumentConfirmationCommand.execute(args) shouldBe true
    }
    bytes.toString("UTF-8").trim
  }

  private def _failure(args: List[String]): IllegalArgumentException =
    intercept[IllegalArgumentException](CozyDocumentConfirmationCommand.execute(args))

  private def _identity(path: Path): String =
    "sha256:" + java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(value => f"${value & 0xff}%02x").mkString

  private def _resource(value: String): Path = Paths.get(getClass.getResource(value).toURI)

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val root = Files.createTempDirectory(name)
    try body(root)
    finally _delete(root)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach { item =>
        try Files.deleteIfExists(item) catch { case NonFatal(_) => () }
      }
      finally stream.close()
    }
}
