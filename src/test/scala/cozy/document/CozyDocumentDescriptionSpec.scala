package cozy.document

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import java.security.MessageDigest
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Sep. 12, 2026
 * @version Sep. 12, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentDescriptionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Document Description" should {
    "retain the direct Article 9 authorities as typed semantic models" in {
      _with_temp_dir("cozy-document-description-typed") { root =>
        Given("a direct Article 9 Core with complete Japanese Document and Summary descriptions")
        val fixture = _fixture(root)

        When("the same three authorities are loaded twice through the strict codecs")
        val firstdocument = CozyDocumentDescription.loadDocument(fixture._1, fixture._2)
        val seconddocument = CozyDocumentDescription.loadDocument(fixture._1, fixture._2)
        val firstsummary = CozyDocumentDescription.loadSummary(fixture._1, fixture._2, fixture._3)
        val secondsummary = CozyDocumentDescription.loadSummary(fixture._1, fixture._2, fixture._3)

        Then("typed sections, every closed block form, references, units, order, wording, emphasis, and original-byte identities are retained exactly")
        firstdocument shouldBe seconddocument
        firstsummary shouldBe secondsummary
        firstdocument.description.locale shouldBe "ja"
        firstdocument.description.document.title shouldBe "Article 9 document"
        firstdocument.description.document.sections.map(_.id) shouldBe Vector("document-root")
        firstdocument.description.document.sections.head.blocks.map(_.getClass.getSimpleName) shouldBe Vector("Paragraph", "ListBlock", "Example", "Note", "LogicalStructure")
        firstdocument.description.document.sections.head.blocks.collect { case value: CozyDocumentDescription.ListBlock => value.items.map(_.text) } shouldBe Vector(Vector("List item wording"))
        firstdocument.coreIdentity shouldBe _identity(fixture._1)
        firstdocument.documentIdentity shouldBe _identity(fixture._2)
        firstsummary.description.summary.units.map(_.id) shouldBe Vector("summary-primary", "summary-conclusion")
        firstsummary.description.summary.units.map(_.emphasis) shouldBe Vector("primary", "conclusion")
        firstsummary.summaryIdentity shouldBe _identity(fixture._3)
      }
    }

    "bind current direct bytes rather than parsed contents or stable IDs alone" in {
      _with_temp_dir("cozy-document-description-currentness") { root =>
        Given("a valid direct Core, Document, and Summary bound to their original raw-byte identities")
        val fixture = _fixture(root)
        val originalcore = Files.readString(fixture._1, StandardCharsets.UTF_8)
        val originaldocument = Files.readString(fixture._2, StandardCharsets.UTF_8)

        When("otherwise parseable Core bytes and then Document bytes change")
        Files.writeString(fixture._1, originalcore + "\n", StandardCharsets.UTF_8)
        val corefailure = _failure(CozyDocumentDescription.loadDocument(fixture._1, fixture._2))
        Files.writeString(fixture._1, originalcore, StandardCharsets.UTF_8)
        Files.writeString(fixture._2, originaldocument + "\n", StandardCharsets.UTF_8)
        val documentfailure = _failure(CozyDocumentDescription.loadSummary(fixture._1, fixture._2, fixture._3))

        Then("stale upstream identity bindings fail before either validated model can be consumed")
        corefailure.code shouldBe "DESCRIPTION_DOCUMENT_CORE"
        documentfailure.code shouldBe "DESCRIPTION_SUMMARY_DOCUMENT"
      }
    }

    "parse each supplied byte snapshot even after its backing path changes" in {
      _with_temp_dir("cozy-document-description-snapshot") { root =>
        Given("direct Core and Document authorities whose original bytes have been captured")
        val fixture = _fixture(root)
        val corebytes = Files.readAllBytes(fixture._1)
        val documentbytes = Files.readAllBytes(fixture._2)
        Files.write(fixture._1, "changed core source".getBytes(StandardCharsets.UTF_8))
        Files.write(fixture._2, "changed document source".getBytes(StandardCharsets.UTF_8))

        When("both strict loaders normalize their already captured snapshots")
        val corevalue = CozyDocumentLogicTree._load_document(fixture._1, corebytes, "core")
        val documentvalue = CozyDocumentDescription._load_document(fixture._2, documentbytes, "document")

        Then("the parsed models come from the supplied snapshots rather than the changed paths")
        corevalue.hcursor.get[String]("schema") shouldBe Right("cozy.content-core.logic-tree.v1")
        documentvalue.hcursor.get[String]("id") shouldBe Right("document-ja")
      }
    }

    "reject YAML anchors aliases and explicit tags as DescriptionFaults" in {
      _with_temp_dir("cozy-document-description-yaml-policy") { root =>
        Given("a valid Article 9 Document Description and three prohibited YAML authority forms")
        val fixture = _fixture(root)
        val document = Files.readString(fixture._2, StandardCharsets.UTF_8)
        val anchor = _write(root.resolve("anchor/ja/document.yaml"), document.replace("id: document-ja", "id: &document-id document-ja"))
        val alias = _write(root.resolve("alias/ja/document.yaml"), document.replace("id: document-ja", "id: *document-id"))
        val tag = _write(root.resolve("tag/ja/document.yaml"), document.replace("id: document-ja", "id: !!str document-ja"))

        When("each prohibited authority form is loaded")
        val failures = Vector(anchor, alias, tag).map(value => _failure(CozyDocumentDescription.loadDocument(fixture._1, value)))

        Then("every indirection or explicit tag is rejected by the owning typed fault")
        failures.map(_.code).distinct shouldBe Vector("DESCRIPTION_SOURCE")
        failures.map(_.reason).exists(_.contains("anchor")) shouldBe true
        failures.map(_.reason).exists(_.contains("alias")) shouldBe true
        failures.map(_.reason).exists(_.contains("tag")) shouldBe true
      }
    }

    "reject closed, unsafe, and lossy authority forms before producing models" in {
      _with_temp_dir("cozy-document-description-rejection") { root =>
        Given("Article 9 authority variants with closed-schema, path, locale, vocabulary, reference, coverage, duplicate-key, and UTF-8 violations")
        val fixture = _fixture(root)
        val document = Files.readString(fixture._2, StandardCharsets.UTF_8)
        val summary = Files.readString(fixture._3, StandardCharsets.UTF_8)
        val unknown = _write(root.resolve("unknown/ja/document.yaml"), document + "unknown: forbidden\n")
        val duplicate = _write(root.resolve("duplicate/ja/document.yaml"), document.replace("id: document-ja\n", "id: document-ja\nid: duplicate-document-ja\n"))
        val wrongpath = _write(root.resolve("wrong-path/ja/document.yml"), document)
        val badlocale = _write(root.resolve("en/document.yaml"), document)
        val badkind = _write(root.resolve("bad-kind/ja/document.yaml"), document.replace("kind: paragraph", "kind: diagram"))
        val unresolved = _write(root.resolve("unresolved/ja/document.yaml"), document.replace("steps: [application-modeling", "steps: [missing-step"))
        val incomplete = _write(root.resolve("incomplete/ja/document.yaml"), document.replaceAll("claims: \\[[^\\]]*\\]", "claims: []"))
        val bademphasis = _write(root.resolve("bad-emphasis/ja/summary.yaml"), summary.replace("emphasis: primary", "emphasis: dominant"))
        val malformed = root.resolve("malformed/ja/document.yaml")
        Files.createDirectories(malformed.getParent)
        Files.write(malformed, Array[Byte](0xC3.toByte, 0x28.toByte))

        When("each invalid authority is admitted through its exact required filename")
        val failures = Vector(
          _failure(CozyDocumentDescription.loadDocument(fixture._1, unknown)),
          _failure(CozyDocumentDescription.loadDocument(fixture._1, duplicate)),
          _failure(CozyDocumentDescription.loadDocument(fixture._1, wrongpath)),
          _failure(CozyDocumentDescription.loadDocument(fixture._1, badlocale)),
          _failure(CozyDocumentDescription.loadDocument(fixture._1, badkind)),
          _failure(CozyDocumentDescription.loadDocument(fixture._1, unresolved)),
          _failure(CozyDocumentDescription.loadDocument(fixture._1, incomplete)),
          _failure(CozyDocumentDescription.loadSummary(fixture._1, fixture._2, bademphasis)),
          _failure(CozyDocumentDescription.loadDocument(fixture._1, malformed))
        )

        Then("unknown or duplicate fields, mismatched locale, invalid vocabulary or references, incomplete coverage, and malformed bytes fail closed")
        failures.map(_.code) should contain allOf (
          "DESCRIPTION_FIELDS", "DESCRIPTION_PATH", "DESCRIPTION_LOCALE", "DESCRIPTION_BLOCK_KIND", "DESCRIPTION_REFERENCE",
          "DESCRIPTION_COVERAGE", "DESCRIPTION_EMPHASIS", "DESCRIPTION_SOURCE"
        )
      }
    }
  }

  private def _fixture(root: Path): (Path, Path, Path) = {
    val content = Files.createDirectories(root.resolve("content"))
    val locale = Files.createDirectories(content.resolve("ja"))
    val core = content.resolve("core.yaml")
    Files.copy(_resource("/cozy/document/phase-58/application-modeling/content/core.yaml"), core)
    val validated = CozyDocumentLogicTree.loadCore(core)
    val refs = _references(validated)
    val document = locale.resolve("document.yaml")
    _write(document, _document(validated.core.id, _identity(core), refs))
    val summary = locale.resolve("summary.yaml")
    _write(summary, _summary(validated.core.id, _identity(core), _identity(document), validated.depthFirstSteps.head.id))
    (core, document, summary)
  }

  private def _document(coreid: String, coreidentity: String, refs: String): String =
    s"""|schema: cozy.document-description.v1
        |id: document-ja
        |core:
        |  id: $coreid
        |  identity: $coreidentity
        |locale: ja
        |document:
        |  title: Article 9 document
        |  sections:
        |    - id: document-root
        |      heading: Root heading
        |      coreRefs: $refs
        |      blocks:
        |        - id: paragraph-one
        |          kind: paragraph
        |          text: Paragraph wording
        |          coreRefs: $refs
        |        - id: list-one
        |          kind: list
        |          items:
        |            - id: list-item-one
        |              text: List item wording
        |              coreRefs: $refs
        |          coreRefs: $refs
        |        - id: example-one
        |          kind: example
        |          title: Example title
        |          text: Example wording
        |          coreRefs: $refs
        |        - id: note-one
        |          kind: note
        |          title: Note title
        |          text: Note wording
        |          coreRefs: $refs
        |        - id: logical-one
        |          kind: logical-structure
        |          stepRef: application-modeling
        |      sections:
        |        - id: document-child
        |          heading: Child heading
        |          coreRefs: $refs
        |          blocks: []
        |          sections: []
        |""".stripMargin

  private def _summary(coreid: String, coreidentity: String, documentidentity: String, step: String): String =
    s"""|schema: cozy.summary-description.v1
        |id: summary-ja
        |core:
        |  id: $coreid
        |  identity: $coreidentity
        |document:
        |  id: document-ja
        |  identity: $documentidentity
        |locale: ja
        |summary:
        |  title: Article 9 summary
        |  units:
        |    - id: summary-primary
        |      heading: Primary heading
        |      message: Primary message
        |      emphasis: primary
        |      coreRefs:
        |        steps: [$step]
        |        claims: []
        |        nodes: []
        |        relations: []
        |        flows: []
        |    - id: summary-conclusion
        |      heading: Conclusion heading
        |      message: Conclusion message
        |      emphasis: conclusion
        |      coreRefs:
        |        steps: [$step]
        |        claims: []
        |        nodes: []
        |        relations: []
        |        flows: []
        |""".stripMargin

  private def _references(core: CozyDocumentLogicTree.ValidatedCore): String = {
    def _values_(values: Vector[String]): String = values.mkString("[", ", ", "]")
    s"""|{ steps: ${_values_(core.depthFirstSteps.map(_.id))}, claims: ${_values_(core.claimsById.keys.toVector.sorted)}, nodes: ${_values_(core.nodesById.keys.toVector.sorted)}, relations: ${_values_(core.relationsById.keys.toVector.sorted)}, flows: ${_values_(core.flowsById.keys.toVector.sorted)} }""".stripMargin
  }

  private def _identity(path: Path): String =
    "sha256:" + MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(value => f"${value & 0xff}%02x").mkString

  private def _resource(value: String): Path = Paths.get(getClass.getResource(value).toURI)

  private def _write(path: Path, value: String): Path = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, value, StandardCharsets.UTF_8)
    path
  }

  private def _failure(body: => Any): CozyDocumentDescription.DescriptionFault = intercept[CozyDocumentDescription.DescriptionFault](body)

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
