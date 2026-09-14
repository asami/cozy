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
 * @since   Sep. 14, 2026
 * @version Sep. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentReaderProjectionV2Spec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Document Reader Projection v2" should {
    "render deterministic escaped output with a disclosed sha256 identity" in {
      _with_temp_dir("cozy-document-reader-v2-determinism") { root =>
        Given("an admitted v2 Document with escaped title, headings, prose, labels, and a complete test vocabulary")
        val fixture = _fixture(root)
        val vocabulary = _vocabulary(fixture.validated.core)

        When("the pure reader projection is rendered twice")
        val first = CozyDocumentReaderProjectionV2.render(fixture.validated, vocabulary)
        val second = CozyDocumentReaderProjectionV2.render(fixture.validated, vocabulary)

        Then("the HTML and identity are byte-stable and every author-controlled value is escaped")
        first shouldBe second
        first.identity should startWith("sha256:")
        first.html should include(s"""data-output-identity="${first.identity}"""")
        first.html should include("<title>Document &lt;title&gt; &amp; &quot;quoted&quot;</title>")
        first.html should include("<h1>Document &lt;title&gt; &amp; &quot;quoted&quot;</h1>")
        first.html should include("Root &lt;heading&gt; &amp; &quot;quoted&quot;")
        first.html should include("Shared &lt;text&gt; &amp; &quot;quoted&quot;")
        first.html should not include("Document <title>")
        first.html should not include("Root <heading>")
        first.html should not include("Shared <text>")
        first.html should not include("<link")
        first.html should not include("http://")
        first.html should not include("https://")
      }
    }

    "preserve authored heading prose list order and recursive hierarchy in the primary reader" in {
      _with_temp_dir("cozy-document-reader-v2-order") { root =>
        Given("a v2 Document with a root section, authored blocks, a list, and a child section")
        val fixture = _fixture(root)

        When("the document-centered primary surface is projected")
        val html = CozyDocumentReaderProjectionV2.render(fixture.validated, _vocabulary(fixture.validated.core)).html
        val rootheading = html.indexOf("Root &lt;heading&gt; &amp; &quot;quoted&quot;")
        val rootparagraph = html.indexOf("Shared &lt;text&gt; &amp; &quot;quoted&quot;")
        val rootitem = html.indexOf("Root item &lt;text&gt; &amp; &quot;quoted&quot;")
        val childheading = html.indexOf("Child heading")
        val childparagraph = html.indexOf("Child prose")

        Then("the primary prose keeps exact text and source order while nested headings retain their levels")
        html should include("id=\"prose-region\"")
        html should include("id=\"document-content\"")
        html should include("<h2>Root &lt;heading&gt; &amp; &quot;quoted&quot;</h2>")
        html should include("<h3>Child heading</h3>")
        html should include("<ul class=\"document-list\">")
        html should include("<li id=\"document-list-item-list-item-root\"")
        rootheading should be >= 0
        rootparagraph should be > rootheading
        rootitem should be > rootparagraph
        childheading should be > rootitem
        childparagraph should be > childheading
        html should include("data-document-kind=\"section\"")
        html should include("data-document-kind=\"block\"")
        html should include("data-document-kind=\"list-item\"")
      }
    }

    "retain every explicit passage-to-Core and Step-to-prose correspondence including multiple targets" in {
      _with_temp_dir("cozy-document-reader-v2-correspondence") { root =>
        Given("an admitted v2 Document whose root and child passages retain exact typed Core reference sets")
        val fixture = _fixture(root)

        When("the reader creates exact links in both directions")
        val html = CozyDocumentReaderProjectionV2.render(fixture.validated, _vocabulary(fixture.validated.core)).html
        val allrefs = _references_for(fixture.validated.core)
        val rootparagraph = _between(html, "id=\"document-block-paragraph-root\"", "</div>")
        val rootstep = _between(html, s"""id="core-step-${fixture.root.id}"""", "</article>")
        Then("each explicit Core category is visible on the passage and each Step retains all matching prose targets")
        allrefs.steps.foreach(value => rootparagraph should include(s"""data-reference-kind="steps" data-reference-id="$value""""))
        allrefs.claims.foreach(value => rootparagraph should include(s"""data-reference-kind="claims" data-reference-id="$value""""))
        allrefs.nodes.foreach(value => rootparagraph should include(s"""data-reference-kind="nodes" data-reference-id="$value""""))
        allrefs.relations.foreach(value => rootparagraph should include(s"""data-reference-kind="relations" data-reference-id="$value""""))
        allrefs.flows.foreach(value => rootparagraph should include(s"""data-reference-kind="flows" data-reference-id="$value""""))
        rootstep should include("data-prose-target-kind=\"section\" data-prose-target-id=\"section-root\"")
        rootstep should include("data-prose-target-kind=\"block\" data-prose-target-id=\"paragraph-root\"")
        rootstep should include("data-prose-target-kind=\"block\" data-prose-target-id=\"list-root\"")
        rootstep should include("data-prose-target-kind=\"list-item\" data-prose-target-id=\"list-item-root\"")
        rootstep should include("data-prose-target-kind=\"block\" data-prose-target-id=\"logical-root\"")
        _occurrences(rootstep, "data-prose-target-kind=") shouldBe 5
        html should include(s"""href="#core-step-${fixture.root.id}"""")
        html should include(s"""href="#core-relation-${fixture.root.structure.relations.head.id}"""")
        html should include(s"""href="#core-flow-${fixture.root.flow.id}"""")
      }
    }

    "keep recursive containment direct-child Flow and Step-local Structure as distinct native surfaces" in {
      _with_temp_dir("cozy-document-reader-v2-surfaces") { root =>
        Given("a recursive admitted Core with local Nodes and Relations plus a direct-child Flow")
        val fixture = _fixture(root)

        When("secondary Core details and anchors are projected beside the primary prose")
        val html = CozyDocumentReaderProjectionV2.render(fixture.validated, _vocabulary(fixture.validated.core)).html
        val containment = _between(html, "id=\"containment-surface\"", "</details>")
        val flowposition = html.indexOf("id=\"flow-surface\"")
        val structureposition = html.indexOf("id=\"structure-surface\"")

        Then("native details expose exact hierarchy, Flow arrows, Structure arrows, and keyboard-operable anchors without a replacement prose column")
        html should include("<aside id=\"core-secondary\"")
        html should include("data-core-surface=\"containment\"")
        html should include("data-core-surface=\"flow\"")
        html should include("data-core-surface=\"structure\"")
        containment should include(s"""data-core-parent="${fixture.root.id}"""")
        containment should include(s"""data-core-parent="${fixture.child.id}"""")
        html should include("<span class=\"edge-mark\" aria-hidden=\"true\">⇢</span>")
        html should include("<span class=\"edge-mark\" aria-hidden=\"true\">→</span>")
        html should include(s"""data-core-transition-id="${fixture.root.flow.transitions.head.id}"""")
        html should include(s"""data-core-relation-id="${fixture.root.structure.relations.head.id}"""")
        html should include("<details")
        flowposition should be > 0
        structureposition should be > flowposition
        html should not include("<script")
      }
    }
  }

  private final class Fixture(
    val validated: CozyDocumentDescriptionV2.ValidatedDocument,
    val root: CozyDocumentLogicTree.Step,
    val child: CozyDocumentLogicTree.Step
  )

  private def _fixture(root: Path): Fixture = {
    val content = Files.createDirectories(root.resolve("content"))
    val locale = Files.createDirectories(content.resolve("ja"))
    val core = content.resolve("core.yaml")
    Files.copy(_resource("/cozy/document/phase-58/application-modeling/content/core.yaml"), core)
    val validatedcore = CozyDocumentLogicTree.loadCore(core)
    val document = locale.resolve("document.yaml")
    _write(document, _document(validatedcore, _identity(core)))
    val validated = CozyDocumentDescriptionV2.loadDocument(core, document)
    val corestep = validated.core.core.root
    new Fixture(validated, corestep, corestep.steps.head)
  }

  private def _document(core: CozyDocumentLogicTree.ValidatedCore, coreidentity: String): String = {
    val root = core.core.root
    val child = root.steps.head
    val allrefs = _references(core.depthFirstSteps, core.claimsById.keys.toVector, core.nodesById.keys.toVector, core.relationsById.keys.toVector, core.flowsById.keys.toVector)
    val rootrefs = _references(Vector(root), root.claims.map(_.id), root.structure.nodes.map(_.id), root.structure.relations.map(_.id), Vector(root.flow.id))
    val childrefs = _references(Vector(child), child.claims.map(_.id), child.structure.nodes.map(_.id), child.structure.relations.map(_.id), Vector(child.flow.id))
    val steplabels = core.depthFirstSteps.map { step =>
      val text = if (step.id == root.id) "Step <root> & \"quoted\"" else s"Step ${step.id}"
      s"    - stepRef: ${step.id}\n      text: '$text'"
    }.mkString("\n")
    val nodelabels = core.nodesById.keys.toVector.sorted.map { node =>
      val text = if (node == root.structure.nodes.head.id) "Node <root> & \"quoted\"" else s"Node $node"
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
        |    - id: section-root
        |      heading: 'Root <heading> & "quoted"'
        |      coreRefs: $allrefs
        |      blocks:
        |        - id: paragraph-root
        |          kind: paragraph
        |          text: 'Shared <text> & "quoted"'
        |          coreRefs: $allrefs
        |        - id: list-root
        |          kind: list
        |          items:
        |            - id: list-item-root
        |              text: 'Root item <text> & "quoted"'
        |              coreRefs: $allrefs
        |          coreRefs: $rootrefs
        |        - id: logical-root
        |          kind: logical-structure
        |          stepRef: ${root.id}
        |      sections:
        |        - id: section-child
        |          heading: Child heading
        |          coreRefs: $childrefs
        |          blocks:
        |            - id: paragraph-child
        |              kind: paragraph
        |              text: Child prose
        |              coreRefs: $childrefs
        |          sections: []
        |labels:
        |  steps:
        |$steplabels
        |  nodes:
        |$nodelabels
        |""".stripMargin
  }

  private def _vocabulary(core: CozyDocumentLogicTree.ValidatedCore): CozyDocumentConfirmationProjectionV2.Vocabulary = {
    val chrome = CozyDocumentConfirmationProjectionV2.Chrome(
      "Document reader <screen> & \"quoted\"",
      "Reader status",
      "Complete Document coverage",
      "Current admitted sources",
      "Admitted state",
      "No unresolved references",
      "Step containment",
      "Direct child Flow",
      "Local Structure",
      "Document prose",
      "Logical Pattern",
      "Nodes",
      "Node role",
      "Relations",
      "No direct child Flow transitions",
      "Logical Structure reference",
      "Identities",
      "Core identity",
      "Document identity",
      "Output identity"
    )
    val patterns = _wording(core.depthFirstSteps.map(_.structure.pattern).toSet)
    val roles = _wording(core.depthFirstSteps.flatMap(_.structure.nodes.map(_.role)).toSet)
    val relations = _wording(core.depthFirstSteps.flatMap(_.structure.relations.map(_.relationType)).toSet)
    val flows = _wording(core.depthFirstSteps.flatMap(_.flow.transitions.map(_.relationType)).toSet)
    CozyDocumentConfirmationProjectionV2.Vocabulary(chrome, patterns, roles, relations, flows)
  }

  private def _wording(values: Set[String]): Map[String, String] = values.toVector.sorted.map(value => value -> s"Generic wording $value").toMap

  private def _references_for(core: CozyDocumentLogicTree.ValidatedCore): CozyDocumentDescriptionV2.References =
    CozyDocumentDescriptionV2.References(
      core.depthFirstSteps.map(_.id),
      core.claimsById.keys.toVector,
      core.nodesById.keys.toVector,
      core.relationsById.keys.toVector,
      core.flowsById.keys.toVector
    )

  private def _references(
    steps: Vector[CozyDocumentLogicTree.Step],
    claims: Vector[String],
    nodes: Vector[String],
    relations: Vector[String],
    flows: Vector[String]
  ): String = {
    def _values_(values: Vector[String]): String = values.sorted.mkString("[", ", ", "]")
    s"{ steps: ${_values_(steps.map(_.id))}, claims: ${_values_(claims)}, nodes: ${_values_(nodes)}, relations: ${_values_(relations)}, flows: ${_values_(flows)} }"
  }

  private def _between(value: String, start: String, end: String): String = {
    val startindex = value.indexOf(start)
    startindex should be >= 0
    val endindex = value.indexOf(end, startindex)
    endindex should be > startindex
    value.substring(startindex, endindex)
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
