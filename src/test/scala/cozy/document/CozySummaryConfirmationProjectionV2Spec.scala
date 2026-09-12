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
final class CozySummaryConfirmationProjectionV2Spec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Summary Confirmation Projection v2" should {
    "render deterministic self-disclosing output while escaping Summary and Document wording" in {
      _with_temp_dir("cozy-summary-confirmation-v2-determinism") { root =>
        Given("one strictly admitted v2 Summary with text and labels that require HTML escaping")
        val fixture = _fixture(root)
        val vocabulary = _vocabulary(fixture.validatedsummary.document.core)

        When("the typed Summary confirmation projection is rendered twice")
        val first = CozySummaryConfirmationProjectionV2.render(fixture.validatedsummary, vocabulary)
        val second = CozySummaryConfirmationProjectionV2.render(fixture.validatedsummary, vocabulary)

        Then("the escaped self-disclosing HTML and identity are byte-stable")
        first shouldBe second
        first.identity should startWith("sha256:")
        first.html should include(s"""data-output-identity="${first.identity}"""")
        first.html should include("Summary &lt;title&gt; &amp; &quot;quoted&quot;")
        first.html should include("Step &lt;root&gt; &amp; &quot;quoted&quot;")
        first.html should include("Status &lt;heading&gt; &amp; &quot;quoted&quot;")
        first.html should not include("Summary <title>")
        first.html should not include("Step <root>")
      }
    }

    "retain ordered native unit navigation with one selected semantic and evidence association" in {
      _with_temp_dir("cozy-summary-confirmation-v2-navigation") { root =>
        Given("an admitted v2 Summary with two author-ordered units")
        val fixture = _fixture(root)
        val html = CozySummaryConfirmationProjectionV2.render(fixture.validatedsummary, _vocabulary(fixture.validatedsummary.document.core)).html
        val firstunit = fixture.validatedsummary.description.summary.units.head
        val secondunit = fixture.validatedsummary.description.summary.units(1)

        When("the confirmation navigation workspace is projected")
        val firstcontrol = _tag(html, s"""data-summary-unit-id="${firstunit.id}"""")
        val secondcontrol = _tag(html, s"""data-summary-unit-id="${secondunit.id}"""")
        val firstslide = _tag(html, s"""id="semantic-${firstunit.id}"""")
        val firstevidence = _tag(html, s"""id="evidence-${firstunit.id}"""")
        val secondslide = _tag(html, s"""id="semantic-${secondunit.id}"""")

        Then("source order, native selection state, and selected panel associations remain explicit")
        html should include("<nav id=\"summary-navigation-region\" class=\"panel\"")
        firstcontrol should include("<button type=\"button\"")
        firstcontrol should include("data-summary-unit-control=\"true\"")
        firstcontrol should include("aria-pressed=\"true\"")
        firstcontrol should include(s"""aria-controls="semantic-${firstunit.id} evidence-${firstunit.id}"""")
        secondcontrol should include("aria-pressed=\"false\"")
        firstslide should include("data-summary-slide-panel=\"true\"")
        firstslide should not include(" hidden")
        firstevidence should include("data-summary-evidence-panel=\"true\"")
        secondslide should include(" hidden")
        html.indexOf(s"""data-summary-unit-id="${firstunit.id}"""") should be < html.indexOf(s"""data-summary-unit-id="${secondunit.id}"""")
      }
    }

    "project only authored selected sources retained points omissions and typed diagram edges" in {
      _with_temp_dir("cozy-summary-confirmation-v2-evidence") { root =>
        Given("a strict v2 Summary whose first unit selects exact sources, retained points, and Relation and Flow edges")
        val fixture = _fixture(root)
        val html = CozySummaryConfirmationProjectionV2.render(fixture.validatedsummary, _vocabulary(fixture.validatedsummary.document.core)).html
        val unit = fixture.validatedsummary.description.summary.units.head
        val relationedge = unit.diagram.get.edges.find(_.kind == "relation").get
        val flowedge = unit.diagram.get.edges.find(_.kind == "flow-transition").get

        When("the first author-selected unit is rendered as the initial evidence view")
        val relationtag = _tag(html, s"""data-diagram-edge-id="${relationedge.id}"""")
        val flowtag = _tag(html, s"""data-diagram-edge-id="${flowedge.id}"""")
        val omissiontag = _tag(html, "data-omission-id=\"omission-section\"")

        Then("the browser view retains exact typed evidence without inferred diagram or Document content")
        Vector("steps", "claims", "nodes", "relations", "flows").foreach(kind => html should include(s"""data-source-category="$kind"""))
        unit.retainedPoints.foreach(point => html should include(s"""data-retained-point-id="${point.id}""""))
        relationtag should include(s"""data-diagram-edge-kind="${relationedge.kind}"""")
        relationtag should include(s"""data-core-ref="${relationedge.ref}"""")
        relationtag should include("data-direction=\"inverse\"")
        flowtag should include(s"""data-diagram-edge-kind="${flowedge.kind}"""")
        flowtag should include(s"""data-core-ref="${flowedge.ref}"""")
        flowtag should include("data-direction=\"forward\"")
        html should include(s"""data-diagram-item-kind="step"""")
        html should include(s"""data-diagram-item-kind="node"""")
        omissiontag should include("data-document-kind=\"section\"")
        omissiontag should include("data-document-ref=\"document-root\"")
        omissiontag should include("data-omission-disposition=\"condensed\"")
        html should include("Section &lt;rationale&gt; &amp; &quot;quoted&quot;")
      }
    }

    "show a single responsive selected semantic panel and deliberate selective status" in {
      _with_temp_dir("cozy-summary-confirmation-v2-accessibility") { root =>
        Given("an admitted Summary whose second unit has no authored diagram")
        val fixture = _fixture(root)
        val html = CozySummaryConfirmationProjectionV2.render(fixture.validatedsummary, _vocabulary(fixture.validatedsummary.document.core)).html
        val secondunit = fixture.validatedsummary.description.summary.units(1)
        val secondpanel = _tag(html, s"""id="semantic-${secondunit.id}"""")

        When("the self-contained responsive workspace is composed")
        val statusposition = html.indexOf("Selected explicit Summary sources are current and admitted")
        val identityposition = html.indexOf("Core identity")

        Then("status stays selective, identities stay secondary, and the native workspace remains accessible")
        secondpanel should include("data-emphasis=\"conclusion\"")
        html should include("data-empty-diagram=\"true\"")
        html should include("Selected explicit Summary sources are current and admitted")
        html should include("No unresolved selected references")
        html should not include("Complete coverage")
        statusposition should be >= 0
        identityposition should be > statusposition
        html should include("<details class=\"secondary\">")
        html should include(fixture.validatedsummary.document.coreIdentity)
        html should include(fixture.validatedsummary.document.documentIdentity)
        html should include(fixture.validatedsummary.summaryIdentity)
        html should include("aspect-ratio:16 / 9")
        html should not include("aspect-ratio:auto")
        html should include(":focus-visible")
        html should include("@media (max-width:56rem)")
        html should include("aria-live=\"polite\"")
        html should include("aria-describedby=\"status-region\"")
        html should not include("<link")
        html should not include("http://")
        html should not include("https://")
        html should not include("<canvas")
        html should not include("fetch(")
      }
    }

    "reject missing generic Flow wording rather than inventing it" in {
      _with_temp_dir("cozy-summary-confirmation-v2-vocabulary") { root =>
        Given("one admitted v2 Summary and a vocabulary that omits rendered Flow type wording")
        val fixture = _fixture(root)
        val incomplete = _vocabulary(fixture.validatedsummary.document.core).copy(flowTypes = Map.empty)

        When("the caller requests the Summary confirmation projection")
        val fault = intercept[CozySummaryConfirmationProjectionV2.ProjectionFault] {
          CozySummaryConfirmationProjectionV2.render(fixture.validatedsummary, incomplete)
        }

        Then("the generic vocabulary boundary fails without a fallback wording")
        fault.code shouldBe "SUMMARY_CONFIRMATION_V2_VOCABULARY"
        fault.reason should include("Flow type")
      }
    }

    "reject an empty admitted Summary unit selection deterministically" in {
      _with_temp_dir("cozy-summary-confirmation-v2-empty-units") { root =>
        Given("an otherwise admitted v2 Summary whose typed unit selection is empty")
        val fixture = _fixture(root)
        val empty = fixture.validatedsummary.copy(
          description = fixture.validatedsummary.description.copy(
            summary = CozyDocumentDescriptionV2.Summary(fixture.validatedsummary.description.summary.title, Vector.empty)
          )
        )

        When("the caller requests a Summary confirmation projection")
        val fault = intercept[CozySummaryConfirmationProjectionV2.ProjectionFault] {
          CozySummaryConfirmationProjectionV2.render(empty, _vocabulary(fixture.validatedsummary.document.core))
        }

        Then("the renderer rejects before page selection can leak a collection failure")
        fault.code shouldBe "SUMMARY_CONFIRMATION_V2_UNITS"
        fault.reason shouldBe "admitted Summary must contain at least one unit"
      }
    }
  }

  private final case class Fixture(validatedsummary: CozyDocumentDescriptionV2.ValidatedSummary)

  private def _fixture(root: Path): Fixture = {
    val content = Files.createDirectories(root.resolve("content"))
    val locale = Files.createDirectories(content.resolve("ja"))
    val core = content.resolve("core.yaml")
    Files.copy(_resource("/cozy/document/phase-58/application-modeling/content/core.yaml"), core)
    val validatedcore = CozyDocumentLogicTree.loadCore(core)
    val relation = validatedcore.depthFirstSteps.flatMap(_.structure.relations).head
    val transition = validatedcore.depthFirstSteps.flatMap(_.flow.transitions).head
    val document = locale.resolve("document.yaml")
    _write(document, _document(validatedcore, _identity(core)))
    val summary = locale.resolve("summary.yaml")
    _write(summary, _summary(validatedcore, relation, transition, _identity(core), _identity(document)))
    new Fixture(CozyDocumentDescriptionV2.loadSummary(core, document, summary))
  }

  private def _document(core: CozyDocumentLogicTree.ValidatedCore, coreidentity: String): String = {
    val refs = _references(core)
    val steplabels = core.depthFirstSteps.map { step =>
      val text = if (step == core.core.root) "Step <root> & \"quoted\"" else s"Step ${step.id}"
      s"    - stepRef: ${step.id}\n      text: '$text'"
    }.mkString("\n")
    val nodelabels = core.nodesById.keys.toVector.sorted.map { node =>
      val text = if (node == core.core.root.structure.nodes.head.id) "Node <root> & \"quoted\"" else s"Node $node"
      s"    - nodeRef: $node\n      text: '$text'"
    }.mkString("\n")
    s"""|schema: cozy.document-description.v2
        |id: document-ja
        |core:
        |  id: ${core.core.id}
        |  identity: $coreidentity
        |locale: ja
        |document:
        |  title: 'Document <title> & "quoted"'
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
        |      sections: []
        |labels:
        |  steps:
        |$steplabels
        |  nodes:
        |$nodelabels
        |""".stripMargin
  }

  private def _summary(
    core: CozyDocumentLogicTree.ValidatedCore,
    relation: CozyDocumentLogicTree.Relation,
    transition: CozyDocumentLogicTree.Transition,
    coreidentity: String,
    documentidentity: String
  ): String = {
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
        |  title: 'Summary <title> & "quoted"'
        |  units:
        |    - id: summary-primary
        |      heading: 'Primary <heading> & "quoted"'
        |      message: 'Primary <message> & "quoted"'
        |      emphasis: primary
        |      coreRefs: $refs
        |      navigationLabel: 'Primary <navigation> & "quoted"'
        |      retainedPoints:
        |        - id: retained-primary
        |          text: 'Retained <point> & "quoted"'
        |          coreRefs: $refs
        |      diagram:
        |        items:
        |          - id: relation-from
        |            kind: node
        |            ref: ${relation.from}
        |          - id: relation-to
        |            kind: node
        |            ref: ${relation.to}
        |          - id: flow-from
        |            kind: step
        |            ref: ${transition.fromStepId}
        |          - id: flow-to
        |            kind: step
        |            ref: ${transition.toStepId}
        |        edges:
        |          - id: relation-inverse
        |            kind: relation
        |            ref: ${relation.id}
        |            direction: inverse
        |          - id: flow-forward
        |            kind: flow-transition
        |            ref: ${transition.id}
        |            direction: forward
        |      omissions:
        |        - id: omission-section
        |          documentKind: section
        |          documentRef: document-root
        |          disposition: condensed
        |          rationale: 'Section <rationale> & "quoted"'
        |    - id: summary-conclusion
        |      heading: Conclusion heading
        |      message: Conclusion message
        |      emphasis: conclusion
        |      coreRefs: $refs
        |      navigationLabel: Conclusion navigation
        |      retainedPoints:
        |        - id: retained-conclusion
        |          text: Conclusion retained point
        |          coreRefs: $refs
        |      omissions:
        |        - id: omission-block
        |          documentKind: block
        |          documentRef: paragraph-one
        |          disposition: omitted
        |          rationale: Paragraph is omitted
        |        - id: omission-list-item
        |          documentKind: list-item
        |          documentRef: list-item-one
        |          disposition: condensed
        |          rationale: List item is condensed
        |""".stripMargin
  }

  private def _references(core: CozyDocumentLogicTree.ValidatedCore): String = {
    val steps = core.depthFirstSteps.map(_.id).mkString("[", ", ", "]")
    val claims = core.claimsById.keys.toVector.sorted.mkString("[", ", ", "]")
    val nodes = core.nodesById.keys.toVector.sorted.mkString("[", ", ", "]")
    val relations = core.relationsById.keys.toVector.sorted.mkString("[", ", ", "]")
    val flows = core.flowsById.keys.toVector.sorted.mkString("[", ", ", "]")
    s"{ steps: $steps, claims: $claims, nodes: $nodes, relations: $relations, flows: $flows }"
  }

  private def _vocabulary(core: CozyDocumentLogicTree.ValidatedCore): CozySummaryConfirmationProjectionV2.Vocabulary = {
    val chrome = CozySummaryConfirmationProjectionV2.Chrome(
      "Status <heading> & \"quoted\"",
      "Selected explicit Summary sources are current and admitted",
      "No unresolved selected references",
      "Summary navigation",
      "Selected semantic explanation",
      "Emphasis",
      "Retained points",
      "Authored diagram",
      "Diagram items",
      "Diagram edges",
      "No authored diagram for this selected unit",
      "Selected sources",
      "Document omissions",
      "Rationale",
      "Identities",
      "Core identity",
      "Document identity",
      "Summary identity",
      "Output identity"
    )
    val sourcecategories = Map("steps" -> "Steps", "claims" -> "Claims", "nodes" -> "Nodes", "relations" -> "Relations", "flows" -> "Flows")
    val diagramitemkinds = Map("step" -> "Step", "node" -> "Node")
    val relationtypes = _wording(core.depthFirstSteps.flatMap(_.structure.relations.map(_.relationType)).toSet)
    val flowtypes = _wording(core.depthFirstSteps.flatMap(_.flow.transitions.map(_.relationType)).toSet)
    val documenttargetkinds = Map("section" -> "Section", "block" -> "Block", "list-item" -> "List item")
    val omissiondispositions = Map("omitted" -> "Omitted", "condensed" -> "Condensed")
    val directions = Map("forward" -> "Forward", "inverse" -> "Inverse")
    CozySummaryConfirmationProjectionV2.Vocabulary(
      chrome,
      sourcecategories,
      diagramitemkinds,
      relationtypes,
      flowtypes,
      documenttargetkinds,
      omissiondispositions,
      directions
    )
  }

  private def _wording(values: Set[String]): Map[String, String] = values.toVector.sorted.map(value => value -> s"Generic wording $value").toMap
  private def _tag(html: String, marker: String): String = {
    val index = html.indexOf(marker)
    require(index >= 0, s"missing marker: $marker")
    html.substring(html.lastIndexOf("<", index), html.indexOf(">", index) + 1)
  }
  private def _occurrences(value: String, part: String): Int = value.sliding(part.length).count(_ == part)
  private def _identity(path: Path): String = "sha256:" + MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(byte => f"${byte & 0xff}%02x").mkString
  private def _resource(value: String): Path = Paths.get(getClass.getResource(value).toURI)

  private def _write(path: Path, value: String): Path = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, value, StandardCharsets.UTF_8)
    path
  }

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
