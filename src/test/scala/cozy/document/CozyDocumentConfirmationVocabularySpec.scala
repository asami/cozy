package cozy.document

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Sep. 12, 2026
 * @version Sep. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentConfirmationVocabularySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Document Confirmation Vocabulary" should {
    "adapt one strict generic Japanese resource to both accepted v2 renderer vocabulary types" in {
      _with_temp_dir("cozy-document-confirmation-vocabulary-admission") { root =>
        Given("a direct generic Japanese confirmation vocabulary resource")
        val vocabularypath = _copy_vocabulary(root.resolve("ja"))

        When("Document and Summary callers bind that resource to the Japanese v2 locale")
        val documentvocabulary = CozyDocumentConfirmationVocabulary.loadDocument(vocabularypath, "ja")
        val summaryvocabulary = CozyDocumentConfirmationVocabulary.loadSummary(vocabularypath, "ja")
        val resource = Files.readString(vocabularypath, StandardCharsets.UTF_8)

        Then("only complete generic chrome and controlled wording reach the renderer adapters")
        documentvocabulary.chrome.statusHeading shouldBe "確認状態"
        documentvocabulary.chrome.pageHeading shouldBe "文書確認"
        documentvocabulary.chrome.flowHeading shouldBe "ステップ間の構造"
        documentvocabulary.chrome.structureHeading shouldBe "ステップ内の構造"
        documentvocabulary.logicalPatterns("causal-chain") shouldBe "因果連鎖"
        documentvocabulary.logicalPatterns("sequence") shouldBe "順序"
        documentvocabulary.nodeRoles("step") shouldBe "ステップ"
        documentvocabulary.relationTypes("next") shouldBe "次へ"
        documentvocabulary.flowTypes("next") shouldBe "次へ"
        documentvocabulary.nodeRoles("dependency") shouldBe "依存先"
        documentvocabulary.relationTypes("maps-to") shouldBe "対応付け"
        summaryvocabulary.chrome.navigationHeading shouldBe "要約の移動"
        summaryvocabulary.chrome.pageHeading shouldBe "要約確認"
        summaryvocabulary.chrome.sourcesHeading shouldBe "元情報と編集判断"
        summaryvocabulary.sourceCategories("flows") shouldBe "ステップ間の構造"
        summaryvocabulary.sourceCategories("relations") shouldBe "ステップ内の構造"
        summaryvocabulary.documentTargetKinds("list-item") shouldBe "リスト項目"
        summaryvocabulary.directions("inverse") shouldBe "逆方向"
        summaryvocabulary.logicalPatterns shouldBe documentvocabulary.logicalPatterns
        summaryvocabulary.inverseRelationTypes("depends-on") shouldBe "依存される"
        summaryvocabulary.inverseFlowTypes("causes") shouldBe "引き起こされる"
        summaryvocabulary.inverseRelationTypes("next") shouldBe "前へ"
        summaryvocabulary.inverseFlowTypes("next") shouldBe "前へ"
        val catalog = cozy.media.CozyVisualPage.fixedCatalog
        documentvocabulary.logicalPatterns.keySet shouldBe catalog.logicalPatterns.map(_.id).toSet
        documentvocabulary.nodeRoles.keySet shouldBe catalog.logicalPatterns.flatMap(_.nodeRoles.map(_.role)).toSet
        documentvocabulary.relationTypes.keySet shouldBe catalog.relations.map(_.id).toSet
        documentvocabulary.flowTypes.keySet shouldBe catalog.relations.map(_.id).toSet
        summaryvocabulary.inverseRelationTypes.keySet shouldBe catalog.relations.map(_.id).toSet
        summaryvocabulary.inverseFlowTypes.keySet shouldBe catalog.relations.map(_.id).toSet
        resource should not include "application-modeling"
        resource should not include "アプリケーションモデリング"
        resource should not include "ユースケースから実現モデルへ"
      }
    }

    "reject incomplete Catalog wording rather than silently omitting sequence step or next" in {
      _with_temp_dir("cozy-confirmation-vocabulary-catalog-completeness") { root =>
        Given("a complete resource with one existing Catalog term missing or an invented term added")
        val text = Files.readString(_copy_vocabulary(root.resolve("ja")), StandardCharsets.UTF_8)
        val variants = Vector(text.replace("    sequence: 順序\n", ""), text.replace("    step: ステップ\n", ""), text.replace("    next: 次へ\n", ""), text.replace("    next: 前へ\n", ""), text.replace("    sequence: 順序\n", "    sequence: 順序\n    invented: 未定義\n"))
        val paths = variants.zipWithIndex.map { case (value, index) => _write(root.resolve(s"catalog-$index/ja/confirmation-vocabulary.yaml"), value) }

        When("either renderer adapter loads each incomplete or extra-key resource")
        val faults = paths.flatMap(path => Vector(_failure(CozyDocumentConfirmationVocabulary.loadDocument(path, "ja")), _failure(CozyDocumentConfirmationVocabulary.loadSummary(path, "ja"))))

        Then("closed complete key sets reject without best-effort fallback or a new Catalog meaning")
        faults.foreach(_.code shouldBe "CONFIRMATION_VOCABULARY_FIELDS")
      }
    }

    "require nonblank page headings in both closed private chrome adapters without accepting missing or unknown keys" in {
      _with_temp_dir("cozy-document-confirmation-vocabulary-page-heading") { root =>
        Given("direct generic resource variants with blank, missing, or unknown Document and Summary chrome fields")
        val vocabularypath = _copy_vocabulary(root.resolve("ja"))
        val text = Files.readString(vocabularypath, StandardCharsets.UTF_8)
        val documentblank = _write(root.resolve("document-blank/ja/confirmation-vocabulary.yaml"), text.replace("pageHeading: 文書確認", "pageHeading: ' '"))
        val summaryblank = _write(root.resolve("summary-blank/ja/confirmation-vocabulary.yaml"), text.replace("pageHeading: 要約確認", "pageHeading: ''"))
        val documentmissing = _write(root.resolve("document-missing/ja/confirmation-vocabulary.yaml"), text.replace("    pageHeading: 文書確認\n", ""))
        val summarymissing = _write(root.resolve("summary-missing/ja/confirmation-vocabulary.yaml"), text.replace("    pageHeading: 要約確認\n", ""))
        val documentunknown = _write(root.resolve("document-unknown/ja/confirmation-vocabulary.yaml"), text.replace("pageHeading: 文書確認", "pageHeading: 文書確認\n    unexpectedHeading: 未知"))
        val summaryunknown = _write(root.resolve("summary-unknown/ja/confirmation-vocabulary.yaml"), text.replace("pageHeading: 要約確認", "pageHeading: 要約確認\n    unexpectedHeading: 未知"))

        When("each exact private adapter receives its invalid screen-heading resource")
        val documentblankfault = _failure(CozyDocumentConfirmationVocabulary.loadDocument(documentblank, "ja"))
        val summaryblankfault = _failure(CozyDocumentConfirmationVocabulary.loadSummary(summaryblank, "ja"))
        val documentfieldfaults = Vector(documentmissing, documentunknown).map(path => _failure(CozyDocumentConfirmationVocabulary.loadDocument(path, "ja")))
        val summaryfieldfaults = Vector(summarymissing, summaryunknown).map(path => _failure(CozyDocumentConfirmationVocabulary.loadSummary(path, "ja")))

        Then("blank wording and closed-field violations reject at the exact chrome path without generic fallback")
        documentblankfault.code shouldBe "CONFIRMATION_VOCABULARY_WORDING"
        documentblankfault.path shouldBe "$.document.chrome.pageHeading"
        summaryblankfault.code shouldBe "CONFIRMATION_VOCABULARY_WORDING"
        summaryblankfault.path shouldBe "$.summary.chrome.pageHeading"
        documentfieldfaults.foreach { fault =>
          fault.code shouldBe "CONFIRMATION_VOCABULARY_FIELDS"
          fault.path shouldBe "$.document.chrome"
        }
        summaryfieldfaults.foreach { fault =>
          fault.code shouldBe "CONFIRMATION_VOCABULARY_FIELDS"
          fault.path shouldBe "$.summary.chrome"
        }
      }
    }

    "reject unsafe paths, closed-schema violations, non-generic wording, and locale mismatches before adaptation" in {
      _with_temp_dir("cozy-document-confirmation-vocabulary-rejection") { root =>
        Given("a generic resource and direct variants that violate one strict admission rule")
        val vocabularypath = _copy_vocabulary(root.resolve("ja"))
        val text = Files.readString(vocabularypath, StandardCharsets.UTF_8)
        val unknown = _write(root.resolve("unknown/ja/confirmation-vocabulary.yaml"), text + "project: forbidden\n")
        val duplicate = _write(root.resolve("duplicate/ja/confirmation-vocabulary.yaml"), text.replace("locale: ja\n", "locale: ja\nlocale: ja\n"))
        val blank = _write(root.resolve("blank/ja/confirmation-vocabulary.yaml"), text.replace("statusHeading: 確認状態", "statusHeading: ' '"))
        val anchor = _write(root.resolve("anchor/ja/confirmation-vocabulary.yaml"), text.replace("schema: cozy.document-confirmation-vocabulary.v1", "schema: &schema cozy.document-confirmation-vocabulary.v1"))
        val wrongname = _write(root.resolve("wrong/ja/vocabulary.yaml"), text)
        val symlinktarget = _write(root.resolve("target/ja/confirmation-vocabulary.yaml"), text)
        val wrongdirectory = _write(root.resolve("en/confirmation-vocabulary.yaml"), text)
        val rootparent = Paths.get("/confirmation-vocabulary.yaml")
        val symlink = root.resolve("link/ja/confirmation-vocabulary.yaml")
        Files.createDirectories(symlink.getParent)
        Files.createSymbolicLink(symlink, symlinktarget)

        When("each variant is bound as a Japanese Document vocabulary")
        val wrongdirectoryfailure = _failure(CozyDocumentConfirmationVocabulary.loadDocument(wrongdirectory, "ja"))
        val rootparentfailure = _failure(CozyDocumentConfirmationVocabulary.loadDocument(rootparent, "ja"))
        val failures = Vector(
          _failure(CozyDocumentConfirmationVocabulary.loadDocument(unknown, "ja")),
          _failure(CozyDocumentConfirmationVocabulary.loadDocument(duplicate, "ja")),
          _failure(CozyDocumentConfirmationVocabulary.loadDocument(blank, "ja")),
          _failure(CozyDocumentConfirmationVocabulary.loadDocument(anchor, "ja")),
          _failure(CozyDocumentConfirmationVocabulary.loadDocument(wrongname, "ja")),
          _failure(CozyDocumentConfirmationVocabulary.loadDocument(symlink, "ja")),
          _failure(CozyDocumentConfirmationVocabulary.loadDocument(vocabularypath, "en")),
          wrongdirectoryfailure,
          rootparentfailure
        )

        Then("the resource rejects without a fallback, lossy parse, or unsafe traversal")
        wrongdirectoryfailure.code shouldBe "CONFIRMATION_VOCABULARY_LOCALE"
        rootparentfailure.code shouldBe "CONFIRMATION_VOCABULARY_PATH"
        failures.map(_.code) should contain allOf (
          "CONFIRMATION_VOCABULARY_FIELDS", "CONFIRMATION_VOCABULARY_SOURCE",
          "CONFIRMATION_VOCABULARY_WORDING", "CONFIRMATION_VOCABULARY_PATH",
          "CONFIRMATION_VOCABULARY_LOCALE"
        )
        Files.isSymbolicLink(symlink) shouldBe true
      }
    }
  }

  private def _copy_vocabulary(directory: Path): Path = {
    Files.createDirectories(directory)
    val destination = directory.resolve("confirmation-vocabulary.yaml")
    Files.copy(_resource("/cozy/document/phase-58/application-modeling/content-v2/ja/confirmation-vocabulary.yaml"), destination)
    destination
  }

  private def _write(path: Path, value: String): Path = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, value, StandardCharsets.UTF_8)
    path
  }

  private def _failure(body: => Any): CozyDocumentConfirmationVocabulary.VocabularyFault =
    intercept[CozyDocumentConfirmationVocabulary.VocabularyFault](body)

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
