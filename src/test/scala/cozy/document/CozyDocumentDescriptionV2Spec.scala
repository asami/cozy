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
final class CozyDocumentDescriptionV2Spec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Document Description v2" should {
    "retain repeated strict loads, raw identities, and one typed localized label for every Core Step and Node" in {
      _with_temp_dir("cozy-document-description-v2-typed") { root =>
        Given("a temporary v2 Document and Summary derived from the rich Article 9 Core fixture")
        val fixture = _fixture(root)

        When("the v2 authorities are loaded repeatedly")
        val firstdocument = CozyDocumentDescriptionV2.loadDocument(fixture.core, fixture.document)
        val seconddocument = CozyDocumentDescriptionV2.loadDocument(fixture.core, fixture.document)
        val firstsummary = CozyDocumentDescriptionV2.loadSummary(fixture.core, fixture.document, fixture.summary)
        val secondsummary = CozyDocumentDescriptionV2.loadSummary(fixture.core, fixture.document, fixture.summary)

        Then("typed labels, recursive prose, exact bindings, and original-byte identities remain equal")
        firstdocument shouldBe seconddocument
        firstsummary shouldBe secondsummary
        firstdocument.coreIdentity shouldBe _identity(fixture.core)
        firstdocument.documentIdentity shouldBe _identity(fixture.document)
        firstsummary.summaryIdentity shouldBe _identity(fixture.summary)
        firstdocument.description.labels.steps.map(_.stepRef).toSet shouldBe firstdocument.core.stepsById.keySet
        firstdocument.description.labels.nodes.map(_.nodeRef).toSet shouldBe firstdocument.core.nodesById.keySet
        firstdocument.description.labels.steps.map(_.text).forall(_.nonEmpty) shouldBe true
        firstdocument.description.labels.nodes.map(_.text).forall(_.nonEmpty) shouldBe true
      }
    }

    "retain authored retained points, forward and inverse typed edges, and typed omission targets" in {
      _with_temp_dir("cozy-document-description-v2-summary-semantics") { root =>
        Given("one admitted temporary v2 Summary with explicit coordinate-free selection")
        val fixture = _fixture(root)

        When("the Summary is loaded")
        val validated = CozyDocumentDescriptionV2.loadSummary(fixture.core, fixture.document, fixture.summary)
        val unit = validated.description.summary.units.head
        val diagram = unit.diagram.get

        Then("its ordered retained points, declared edge readings, and Document omissions are kept as typed values")
        unit.navigationLabel shouldBe "Primary navigation"
        unit.retainedPoints.map(_.id) shouldBe Vector("retained-root", "retained-relation")
        unit.retainedPoints.foreach(_.coreRefs.steps.nonEmpty shouldBe true)
        diagram.items.map(value => value.kind -> value.ref) should contain allOf (
          "node" -> "root-domain-model",
          "node" -> "root-application-model",
          "step" -> "application-conclusion",
          "step" -> "use-case-realization"
        )
        diagram.edges.map(value => value.ref -> value.direction) should contain allOf (
          "root-domain-maps-to-application" -> "forward",
          "root-domain-maps-to-application" -> "inverse",
          "conclusion-depends-on-realization" -> "forward"
        )
        unit.omissions.map(value => value.documentKind -> value.documentRef) shouldBe Vector(
          "section" -> "document-root",
          "block" -> "paragraph-one",
          "list-item" -> "list-item-one"
        )
        unit.omissions.map(_.disposition) shouldBe Vector("condensed", "omitted", "condensed")
      }
    }

    "reject unknown and duplicate source fields, duplicate semantic identities, and unsafe YAML forms before a v2 model exists" in {
      _with_temp_dir("cozy-document-description-v2-source") { root =>
        Given("one valid temporary v2 authority and closed-source variants")
        val fixture = _fixture(root)
        val document = Files.readString(fixture.document, StandardCharsets.UTF_8)
        val summary = Files.readString(fixture.summary, StandardCharsets.UTF_8)
        val unknown = _write(root.resolve("unknown/ja/document.yaml"), document + "unknown: forbidden\n")
        val duplicatefield = _write(root.resolve("duplicate/ja/document.yaml"), document.replace("id: document-ja\n", "id: document-ja\nid: duplicate-document-ja\n"))
        val duplicatepoint = _write(root.resolve("duplicate-point/ja/summary.yaml"), summary.replace("id: retained-relation", "id: retained-root"))
        val anchor = _write(root.resolve("anchor/ja/document.yaml"), document.replace("id: document-ja", "id: &document-id document-ja"))
        val alias = _write(root.resolve("alias/ja/document.yaml"), document.replace("id: document-ja", "id: *document-id"))
        val tag = _write(root.resolve("tag/ja/document.yaml"), document.replace("id: document-ja", "id: !!str document-ja"))
        val malformed = root.resolve("malformed/ja/document.yaml")
        Files.createDirectories(malformed.getParent)
        Files.write(malformed, Array[Byte](0xC3.toByte, 0x28.toByte))

        When("each unknown, duplicate, indirection, explicit-tag, or lossy source is admitted")
        val failures = Vector(
          _failure(CozyDocumentDescriptionV2.loadDocument(fixture.core, unknown)),
          _failure(CozyDocumentDescriptionV2.loadDocument(fixture.core, duplicatefield)),
          _failure(CozyDocumentDescriptionV2.loadSummary(fixture.core, fixture.document, duplicatepoint)),
          _failure(CozyDocumentDescriptionV2.loadDocument(fixture.core, anchor)),
          _failure(CozyDocumentDescriptionV2.loadDocument(fixture.core, alias)),
          _failure(CozyDocumentDescriptionV2.loadDocument(fixture.core, tag)),
          _failure(CozyDocumentDescriptionV2.loadDocument(fixture.core, malformed))
        )

        Then("closed-schema, unique identity, and strict YAML admission faults reject before consumer use")
        failures.map(_.code) should contain allOf ("DESCRIPTION_V2_FIELDS", "DESCRIPTION_V2_SOURCE", "DESCRIPTION_V2_IDENTITY")
        failures.filter(_.code == "DESCRIPTION_V2_SOURCE").size should be >= 5
      }
    }

    "reject unsafe paths, stale raw-byte bindings, incomplete labels, and unresolved typed references while preserving the v1 rejection boundary" in {
      _with_temp_dir("cozy-document-description-v2-binding") { root =>
        Given("valid v2 fixture bytes and strict v1 loader ownership")
        val fixture = _fixture(root)
        val document = Files.readString(fixture.document, StandardCharsets.UTF_8)
        val summary = Files.readString(fixture.summary, StandardCharsets.UTF_8)
        val wrongpath = _write(root.resolve("wrong-path/ja/document.yml"), document)
        val missinglabels = _write(
          root.resolve("missing-labels/ja/document.yaml"),
          document.replace("    - nodeRef: conclusion-cml\n      text: Node label conclusion-cml\n", "")
        )
        val unresolved = _write(root.resolve("unresolved/ja/document.yaml"), document.replace("stepRef: application-foundation", "stepRef: missing-step"))
        val originalcore = Files.readString(fixture.core, StandardCharsets.UTF_8)
        val originaldocument = Files.readString(fixture.document, StandardCharsets.UTF_8)

        When("path, label, reference, Core, Document, and v1-schema boundaries are exercised")
        val pathfailure = _failure(CozyDocumentDescriptionV2.loadDocument(fixture.core, wrongpath))
        val labelfailure = _failure(CozyDocumentDescriptionV2.loadDocument(fixture.core, missinglabels))
        val referencefailure = _failure(CozyDocumentDescriptionV2.loadDocument(fixture.core, unresolved))
        Files.writeString(fixture.core, originalcore + "\n", StandardCharsets.UTF_8)
        val corefailure = _failure(CozyDocumentDescriptionV2.loadDocument(fixture.core, fixture.document))
        Files.writeString(fixture.core, originalcore, StandardCharsets.UTF_8)
        Files.writeString(fixture.document, originaldocument + "\n", StandardCharsets.UTF_8)
        val documentfailure = _failure(CozyDocumentDescriptionV2.loadSummary(fixture.core, fixture.document, fixture.summary))
        Files.writeString(fixture.document, originaldocument, StandardCharsets.UTF_8)
        val v1failure = _v1_failure(CozyDocumentDescription.loadDocument(fixture.core, fixture.document))

        Then("all authority boundaries remain strict and v1 does not convert a v2 source")
        pathfailure.code shouldBe "DESCRIPTION_V2_PATH"
        labelfailure.code shouldBe "DESCRIPTION_V2_LABEL_COVERAGE"
        referencefailure.code shouldBe "DESCRIPTION_V2_REFERENCE"
        corefailure.code shouldBe "DESCRIPTION_V2_DOCUMENT_CORE"
        documentfailure.code shouldBe "DESCRIPTION_V2_SUMMARY_DOCUMENT"
        v1failure.code shouldBe "DESCRIPTION_FIELDS"
      }
    }

    "reject missing retained points or omissions, unresolved sources, and ungrounded or endpoint-invalid diagrams" in {
      _with_temp_dir("cozy-document-description-v2-summary-rejection") { root =>
        Given("a valid v2 Summary with individually mutated editorial and diagram selections")
        val fixture = _fixture(root)
        val summary = Files.readString(fixture.summary, StandardCharsets.UTF_8)
        val nopoints = _write(
          root.resolve("no-points/ja/summary.yaml"),
          _replace_between(summary, "      retainedPoints:\n", "      diagram:\n", "      retainedPoints: []\n")
        )
        val noomissions = _write(
          root.resolve("no-omissions/ja/summary.yaml"),
          _replace_between(summary, "      omissions:\n", "    - id: summary-conclusion", "      omissions: []\n")
        )
        val unresolved = _write(
          root.resolve("unresolved/ja/summary.yaml"),
          summary.replace("steps: [application-modeling]", "steps: [missing-step]")
        )
        val ungrounded = _write(
          root.resolve("ungrounded/ja/summary.yaml"),
          summary.replace("ref: root-domain-maps-to-application\n            direction: forward", "ref: missing-relation\n            direction: forward")
        )
        val endpoint = _write(
          root.resolve("endpoint/ja/summary.yaml"),
          summary.replace("ref: root-domain-model\n", "ref: foundation-static-view\n")
        )
        val direction = _write(
          root.resolve("direction/ja/summary.yaml"),
          summary.replace("direction: inverse", "direction: sideways")
        )

        When("missing authoring records and invalid typed diagram variants are loaded")
        val failures = Vector(
          _failure(CozyDocumentDescriptionV2.loadSummary(fixture.core, fixture.document, nopoints)),
          _failure(CozyDocumentDescriptionV2.loadSummary(fixture.core, fixture.document, noomissions)),
          _failure(CozyDocumentDescriptionV2.loadSummary(fixture.core, fixture.document, unresolved)),
          _failure(CozyDocumentDescriptionV2.loadSummary(fixture.core, fixture.document, ungrounded)),
          _failure(CozyDocumentDescriptionV2.loadSummary(fixture.core, fixture.document, endpoint)),
          _failure(CozyDocumentDescriptionV2.loadSummary(fixture.core, fixture.document, direction))
        )

        Then("the loader does not infer editorial content or repair a typed visible edge")
        failures.map(_.code) should contain allOf (
          "DESCRIPTION_V2_RETAINED_POINTS",
          "DESCRIPTION_V2_OMISSIONS",
          "DESCRIPTION_V2_REFERENCE",
          "DESCRIPTION_V2_DIAGRAM_REFERENCE",
          "DESCRIPTION_V2_DIAGRAM_ENDPOINT",
          "DESCRIPTION_V2_DIAGRAM_DIRECTION"
        )
      }
    }

    "reject Relation and Flow edge grounding failures and omission target or kind faults" in {
      _with_temp_dir("cozy-document-description-v2-grounding") { root =>
        Given("one valid v2 Summary and variants that retain an edge or omission without its declared source")
        val fixture = _fixture(root)
        val summary = Files.readString(fixture.summary, StandardCharsets.UTF_8)
        val relationids = fixture.core
        val validated = CozyDocumentLogicTree.loadCore(relationids)
        val relations = validated.relationsById.keys.toVector.sorted.mkString("[", ", ", "]")
        val flows = validated.flowsById.keys.toVector.sorted.mkString("[", ", ", "]")
        val missingrelationgrounding = _write(
          root.resolve("missing-relation-grounding/ja/summary.yaml"),
          summary.replace(s"relations: $relations", "relations: []")
        )
        val missingflowgrounding = _write(
          root.resolve("missing-flow-grounding/ja/summary.yaml"),
          summary.replace(s"flows: $flows", "flows: []")
        )
        val missingtarget = _write(
          root.resolve("missing-target/ja/summary.yaml"),
          summary.replace("documentRef: document-root", "documentRef: missing-section")
        )
        val wrongkind = _write(
          root.resolve("wrong-kind/ja/summary.yaml"),
          summary.replace("documentKind: section\n          documentRef: document-root", "documentKind: block\n          documentRef: document-root")
        )

        When("the Summary names an edge without its source or a mismatched Document target")
        val failures = Vector(
          _failure(CozyDocumentDescriptionV2.loadSummary(fixture.core, fixture.document, missingrelationgrounding)),
          _failure(CozyDocumentDescriptionV2.loadSummary(fixture.core, fixture.document, missingflowgrounding)),
          _failure(CozyDocumentDescriptionV2.loadSummary(fixture.core, fixture.document, missingtarget)),
          _failure(CozyDocumentDescriptionV2.loadSummary(fixture.core, fixture.document, wrongkind))
        )

        Then("grounded source membership and exact typed Document target kinds are mandatory")
        failures.map(_.code) should contain allOf ("DESCRIPTION_V2_DIAGRAM_GROUNDING", "DESCRIPTION_V2_OMISSION_REFERENCE")
      }
    }
  }

  private final class Fixture(val core: Path, val document: Path, val summary: Path)

  private def _fixture(root: Path): Fixture = {
    val content = Files.createDirectories(root.resolve("content"))
    val locale = Files.createDirectories(content.resolve("ja"))
    val core = content.resolve("core.yaml")
    Files.copy(_resource("/cozy/document/phase-58/application-modeling/content/core.yaml"), core)
    val validated = CozyDocumentLogicTree.loadCore(core)
    val document = locale.resolve("document.yaml")
    _write(document, _document(validated, _identity(core)))
    val summary = locale.resolve("summary.yaml")
    _write(summary, _summary(validated, _identity(core), _identity(document)))
    new Fixture(core, document, summary)
  }

  private def _document(core: CozyDocumentLogicTree.ValidatedCore, coreidentity: String): String = {
    val refs = _references(core)
    val steplabels = core.depthFirstSteps.map { step =>
      s"    - stepRef: ${step.id}\n      text: Step label ${step.id}"
    }.mkString("\n")
    val nodelabels = core.nodesById.keys.toVector.sorted.map { node =>
      s"    - nodeRef: $node\n      text: Node label $node"
    }.mkString("\n")
    s"""|schema: cozy.document-description.v2
        |id: document-ja
        |core:
        |  id: ${core.core.id}
        |  identity: $coreidentity
        |locale: ja
        |document:
        |  title: Article 9 v2 document
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
        |labels:
        |  steps:
        |$steplabels
        |  nodes:
        |$nodelabels
        |""".stripMargin
  }

  private def _summary(core: CozyDocumentLogicTree.ValidatedCore, coreidentity: String, documentidentity: String): String = {
    val refs = _references(core)
    s"""|schema: cozy.summary-description.v2
        |id: summary-ja
        |core:
        |  id: ${core.core.id}
        |  identity: $coreidentity
        |document:
        |  id: document-ja
        |  identity: $documentidentity
        |locale: ja
        |summary:
        |  title: Article 9 v2 summary
        |  units:
        |    - id: summary-primary
        |      heading: Primary heading
        |      message: Primary message
        |      emphasis: primary
        |      coreRefs: $refs
        |      navigationLabel: Primary navigation
        |      retainedPoints:
        |        - id: retained-root
        |          text: Root retained point
        |          coreRefs: { steps: [application-modeling], claims: [], nodes: [], relations: [], flows: [] }
        |        - id: retained-relation
        |          text: Relation retained point
        |          coreRefs: { steps: [application-modeling], claims: [], nodes: [root-domain-model, root-application-model], relations: [root-domain-maps-to-application], flows: [] }
        |      diagram:
        |        items:
        |          - id: item-root-domain
        |            kind: node
        |            ref: root-domain-model
        |          - id: item-root-application
        |            kind: node
        |            ref: root-application-model
        |          - id: item-conclusion
        |            kind: step
        |            ref: application-conclusion
        |          - id: item-realization
        |            kind: step
        |            ref: use-case-realization
        |        edges:
        |          - id: edge-root-forward
        |            kind: relation
        |            ref: root-domain-maps-to-application
        |            direction: forward
        |          - id: edge-root-inverse
        |            kind: relation
        |            ref: root-domain-maps-to-application
        |            direction: inverse
        |          - id: edge-flow-forward
        |            kind: flow-transition
        |            ref: conclusion-depends-on-realization
        |            direction: forward
        |      omissions:
        |        - id: omission-section
        |          documentKind: section
        |          documentRef: document-root
        |          disposition: condensed
        |          rationale: Section is condensed for the summary
        |        - id: omission-block
        |          documentKind: block
        |          documentRef: paragraph-one
        |          disposition: omitted
        |          rationale: Paragraph is omitted from the summary
        |        - id: omission-list-item
        |          documentKind: list-item
        |          documentRef: list-item-one
        |          disposition: condensed
        |          rationale: List item is condensed for the summary
        |    - id: summary-conclusion
        |      heading: Conclusion heading
        |      message: Conclusion message
        |      emphasis: conclusion
        |      coreRefs: { steps: [application-conclusion], claims: [], nodes: [], relations: [], flows: [] }
        |      navigationLabel: Conclusion navigation
        |      retainedPoints:
        |        - id: retained-conclusion
        |          text: Conclusion retained point
        |          coreRefs: { steps: [application-conclusion], claims: [], nodes: [], relations: [], flows: [] }
        |      omissions:
        |        - id: omission-note
        |          documentKind: block
        |          documentRef: note-one
        |          disposition: condensed
        |          rationale: Note is condensed for the conclusion
        |""".stripMargin
  }

  private def _references(core: CozyDocumentLogicTree.ValidatedCore): String = {
    def _values_(values: Vector[String]): String = values.mkString("[", ", ", "]")
    s"{ steps: ${_values_(core.depthFirstSteps.map(_.id))}, claims: ${_values_(core.claimsById.keys.toVector.sorted)}, nodes: ${_values_(core.nodesById.keys.toVector.sorted)}, relations: ${_values_(core.relationsById.keys.toVector.sorted)}, flows: ${_values_(core.flowsById.keys.toVector.sorted)} }"
  }

  private def _replace_between(text: String, start: String, end: String, replacement: String): String = {
    val from = text.indexOf(start)
    val until = text.indexOf(end, from + start.length)
    require(from >= 0 && until >= 0, s"fixture boundary not found: $start / $end")
    text.substring(0, from) + replacement + text.substring(until)
  }

  private def _identity(path: Path): String =
    "sha256:" + MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(value => f"${value & 0xff}%02x").mkString

  private def _resource(value: String): Path = Paths.get(getClass.getResource(value).toURI)

  private def _write(path: Path, value: String): Path = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, value, StandardCharsets.UTF_8)
    path
  }

  private def _failure(body: => Any): CozyDocumentDescriptionV2.DescriptionV2Fault = intercept[CozyDocumentDescriptionV2.DescriptionV2Fault](body)

  private def _v1_failure(body: => Any): CozyDocumentDescription.DescriptionFault = intercept[CozyDocumentDescription.DescriptionFault](body)

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
