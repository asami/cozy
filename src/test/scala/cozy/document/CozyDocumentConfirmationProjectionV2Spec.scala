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
 * @version Sep. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentConfirmationProjectionV2Spec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Document Confirmation Projection v2" should {
    "render deterministic self-disclosing output while escaping admitted text and attributes" in {
      _with_temp_dir("cozy-document-confirmation-v2-determinism") { root =>
        Given("one strictly loaded v2 Document with text that needs HTML escaping and test-only generic vocabulary")
        val fixture = _fixture(root)
        val vocabulary = _vocabulary(fixture.validated.core)

        When("the typed confirmation projection is rendered twice")
        val first = CozyDocumentConfirmationProjectionV2.render(fixture.validated, vocabulary)
        val second = CozyDocumentConfirmationProjectionV2.render(fixture.validated, vocabulary)

        Then("HTML and its disclosed identity are byte-stable while text and attribute values are escaped")
        first shouldBe second
        first.identity should startWith("sha256:")
        first.html should include(s"""data-output-identity="${first.identity}"""")
        first.html should include("Document &lt;title&gt; &amp; &quot;quoted&quot;")
        first.html should include("<title>Document &lt;title&gt; &amp; &quot;quoted&quot;</title>")
        first.html should include("<div class=\"kicker\">Document &lt;title&gt; &amp; &quot;quoted&quot;</div>")
        first.html should include("<h1>Document confirmation &lt;screen&gt; &amp; &quot;quoted&quot;</h1>")
        first.html should not include("<h1>Document &lt;title&gt;")
        first.html should include("<article class=\"article\"><h2>Step &lt;root&gt; &amp; &quot;quoted&quot;</h2>")
        first.html should include("Step &lt;root&gt; &amp; &quot;quoted&quot;")
        first.html should include("aria-label=\"Status &lt;heading&gt; &amp; &quot;quoted&quot;\"")
        first.html should not include("Document <title>")
        first.html should not include("Step <root>")
      }
    }

    "render recursive localized native Step controls with one accessible selection model" in {
      _with_temp_dir("cozy-document-confirmation-v2-containment") { root =>
        Given("a v2 Document whose admitted Core has a recursive root and child Step")
        val fixture = _fixture(root)
        val html = CozyDocumentConfirmationProjectionV2.render(fixture.validated, _vocabulary(fixture.validated.core)).html

        When("the containment workspace is projected")
        val rootcontrol = _tag(html, s"""data-step-id="${fixture.root.id}"""")
        val rootbutton = _button_content(html, s"""data-step-id="${fixture.root.id}"""")
        val childcontrol = _tag(html, s"""data-step-id="${fixture.child.id}"""")

        Then("nested native buttons retain localized labels, stable IDs, pressed state, and control-to-detail/prose associations")
        html should include("<section id=\"containment-region\" class=\"panel\"")
        rootcontrol should include("<button type=\"button\"")
        rootcontrol should include("data-step-control=\"true\"")
        rootbutton should include("<small>Generic wording mapping</small>")
        rootbutton should include("<b>Step &lt;root&gt; &amp; &quot;quoted&quot;</b>")
        rootcontrol should include(s"""title="${fixture.root.id}""" )
        rootcontrol should include("aria-pressed=\"true\"")
        rootcontrol should include(s"""aria-controls="flow-${fixture.root.id} structure-${fixture.root.id} prose-region"""")
        childcontrol should include("aria-pressed=\"false\"")
        html.indexOf(s"""data-step-id="${fixture.root.id}"""") should be < html.indexOf(s"""data-step-id="${fixture.child.id}"""")
      }
    }

    "keep containment direct Flow local Structure and identity-only prose highlights distinct" in {
      _with_temp_dir("cozy-document-confirmation-v2-semantics") { root =>
        Given("a strict v2 Document whose identical prose belongs to different exact Core reference sets")
        val fixture = _fixture(root)
        val html = CozyDocumentConfirmationProjectionV2.render(fixture.validated, _vocabulary(fixture.validated.core)).html

        When("the admitted root Step is the initial confirmation selection")
        val rootsection = _tag(html, "data-section-id=\"section-root\"")
        val childsection = _tag(html, "data-section-id=\"section-child\"")
        val rootparagraph = _tag(html, "data-block-id=\"paragraph-root\"")
        val childparagraph = _tag(html, "data-block-id=\"paragraph-unselected\"")
        val rootitem = _tag(html, "data-list-item-id=\"list-item-root\"")
        val childitem = _tag(html, "data-list-item-id=\"list-item-unselected\"")

        Then("only exact Block and List Item memberships highlight while Section wrappers stay flat and typed Flow and Structure stay separate")
        html should include("id=\"flow-region\"")
        html should include("id=\"structure-region\"")
        html.indexOf("id=\"structure-region\"") should be < html.indexOf("id=\"flow-region\"")
        html should include("<details id=\"flow-region\"")
        html should include("""<span class="edge-mark" aria-hidden="true">⇢</span>""")
        html should include("""<span class="edge-mark" aria-hidden="true">→</span>""")
        html should include(s"""data-flow-id="${fixture.root.flow.id}"""")
        fixture.root.flow.transitions.foreach(transition => html should include(s"""data-flow-transition-id="${transition.id}""""))
        fixture.root.structure.relations.foreach(relation => html should include(s"""data-relation-id="${relation.id}""""))
        fixture.root.flow.transitions.foreach { transition =>
          html should include(s"""<span class="identity">${transition.toStepId}</span></span></li>""")
        }
        rootsection should not include("is-highlighted")
        rootparagraph should include("is-highlighted")
        rootitem should include("is-highlighted")
        childsection should not include("is-highlighted")
        childparagraph should not include("is-highlighted")
        childitem should not include("is-highlighted")
        Vector(rootsection, childsection, rootparagraph, childparagraph, rootitem, childitem).foreach { tag =>
          tag should include regex """data-core-flows="[^"]*">"""
        }
        _occurrences(html, "<ul class=\"containment-list\">") shouldBe fixture.validated.core.depthFirstSteps.count(_.steps.nonEmpty) + 1
        html should include("<details class=\"document-block logical-structure-reference")
        html should include("<summary>Logical Structure reference: Step &lt;root&gt; &amp; &quot;quoted&quot;</summary>")
        _tag(html, "data-block-id=\"logical-root\"") should not include(" open")
        html should include("Root &lt;heading&gt; &amp; &quot;quoted&quot;")
        html should include("Child heading")
        html should include("Example &lt;text&gt; &amp; &quot;quoted&quot;")
        html should include("Child note text")
        html should not include("<code>claim/")
        html should not include("<code>node/")
        html should not include("<code>flow/")
        _occurrences(html, "Shared &lt;text&gt; &amp; &quot;quoted&quot;") shouldBe 2
      }
    }

    "reproduce the reference desktop hierarchy while keeping nested semantic evidence and identities secondary" in {
      _with_temp_dir("cozy-document-confirmation-v2-accessibility") { root =>
        Given("a complete current admitted v2 Document and a complete test-only vocabulary")
        val fixture = _fixture(root)
        val html = CozyDocumentConfirmationProjectionV2.render(fixture.validated, _vocabulary(fixture.validated.core)).html

        When("the content-height confirmation workspace and activation-only reveal are composed")
        val statusposition = html.indexOf("Complete Document coverage")
        val identityposition = html.indexOf("Core identity")

        Then("compact status, persistent desktop Core navigation, activation-only reveal, and nested Flow and Structure retain keyboard-operable self-contained evidence")
        html should include("Complete Document coverage")
        html should include("Current admitted sources")
        html should include("No unresolved references")
        statusposition should be >= 0
        identityposition should be > statusposition
        html should include("<details class=\"secondary\">")
        html should include(s"""data-core-id="${fixture.validated.core.core.id}"""")
        html should include(s"${fixture.validated.coreIdentity}</dd>")
        html should include(s"${fixture.validated.documentIdentity}</dd>")
        html should include(":focus-visible")
        html should include(".page{max-width:1500px;margin:0 auto;padding:24px}")
        html should include(".workspace{display:grid;grid-template-columns:minmax(280px,.82fr) 62px minmax(430px,1.4fr);gap:16px;align-items:start}")
        html should include(".binding{display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:320px")
        html should include(".containment-list li{position:relative;margin:6px 0}")
        html should include(".step-control{display:block;width:100%;padding:6px 10px")
        html should include("<div class=\"guide\">")
        html should include("<div class=\"binding\"")
        html should include("<details id=\"flow-region\" class=\"local\"")
        html should include("<section id=\"structure-region\" class=\"local\"")
        html should include("<summary>Direct child Flow</summary>")
        html should include("<details class=\"structure-audit\">")
        html should include("<div class=\"refs\">")
        html should include("class=\"relation\"")
        html should include(".step-control[aria-pressed=\"true\"]{border-left-color:var(--primary);background:var(--primary-soft);color:var(--primary-foreground)}")
        html should include("h1{margin:3px 0 0;font-size:clamp(25px,3vw,38px);letter-spacing:-.035em}")
        html should include(".step-control b{display:block;overflow-wrap:anywhere;font-size:13px}")
        html should include(".document-example,.document-note{background:transparent;color:var(--foreground)}")
        html should include(".logical-structure-reference{padding:0 0 0 17px;background:transparent;color:var(--foreground)}")
        html should include(".logical-structure-reference summary{display:inline;font-size:10px;cursor:pointer}")
        html should include("""@media screen and (min-width:851px){#containment-region{position:sticky;top:16px;max-height:calc(100vh - 32px);overflow-y:auto}}.document-block,.document-list-item{scroll-margin-top:16px}""")
        html should include("const select=(control,reveal=false)=>")
        html should include("""if(reveal){const target=targets.find(item=>item.matches('.document-block.is-highlighted,.document-list-item.is-highlighted'));if(target){""")
        html should include("""if(bounds.top<16||bounds.bottom>window.innerHeight-16)target.scrollIntoView({block:bounds.height>window.innerHeight-32?'start':'center',inline:'nearest',behavior:'auto'})""")
        html should include("addEventListener('click',()=>select(control,true))")
        html should include("""select(controls.find(control=>control.getAttribute('aria-pressed')==='true')||controls[0]);""")
        html should not include(".focus(")
        html should include("aria-live=\"polite\"")
        html should include("aria-describedby=\"status-region\"")
        html should not include("<link")
        html should not include("http://")
        html should not include("https://")
        html should not include("<canvas")
        html should not include("fetch(")
      }
    }

    "reject missing generic wording rather than inventing a Flow type label" in {
      _with_temp_dir("cozy-document-confirmation-v2-vocabulary") { root =>
        Given("one admitted v2 Document and a vocabulary that omits every required Flow type wording")
        val fixture = _fixture(root)
        val incomplete = _vocabulary(fixture.validated.core).copy(flowTypes = Map.empty)

        When("the caller requests a confirmation projection")
        val fault = intercept[CozyDocumentConfirmationProjectionV2.ProjectionFault] {
          CozyDocumentConfirmationProjectionV2.render(fixture.validated, incomplete)
        }

        Then("the renderer rejects the generic wording boundary without a fallback label")
        fault.code shouldBe "CONFIRMATION_V2_VOCABULARY"
        fault.reason should include("Flow type")
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
        |      coreRefs: $rootrefs
        |      blocks:
        |        - id: paragraph-root
        |          kind: paragraph
        |          text: 'Shared <text> & "quoted"'
        |          coreRefs: $rootrefs
        |        - id: list-root
        |          kind: list
        |          items:
        |            - id: list-item-root
        |              text: 'Root item <text> & "quoted"'
        |              coreRefs: $rootrefs
        |          coreRefs: $rootrefs
        |        - id: example-all
        |          kind: example
        |          title: 'Example <title> & "quoted"'
        |          text: 'Example <text> & "quoted"'
        |          coreRefs: $allrefs
        |        - id: note-child
        |          kind: note
        |          title: Child note
        |          text: Child note text
        |          coreRefs: $childrefs
        |        - id: logical-root
        |          kind: logical-structure
        |          stepRef: ${root.id}
        |      sections:
        |        - id: section-child
        |          heading: Child heading
        |          coreRefs: $childrefs
        |          blocks:
        |            - id: paragraph-unselected
        |              kind: paragraph
        |              text: 'Shared <text> & "quoted"'
        |              coreRefs: $childrefs
        |            - id: list-unselected
        |              kind: list
        |              items:
        |                - id: list-item-unselected
        |                  text: 'Unselected item <text> & "quoted"'
        |                  coreRefs: $childrefs
        |              coreRefs: $childrefs
        |          sections: []
        |labels:
        |  steps:
        |$steplabels
        |  nodes:
        |$nodelabels
        |""".stripMargin
  }

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

  private def _vocabulary(core: CozyDocumentLogicTree.ValidatedCore): CozyDocumentConfirmationProjectionV2.Vocabulary = {
    val chrome = CozyDocumentConfirmationProjectionV2.Chrome(
      "Document confirmation <screen> & \"quoted\"",
      "Status <heading> & \"quoted\"",
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

  private def _tag(html: String, marker: String): String = {
    val index = html.indexOf(marker)
    require(index >= 0, s"missing marker: $marker")
    html.substring(html.lastIndexOf("<", index), html.indexOf(">", index) + 1)
  }

  private def _button_content(html: String, marker: String): String = {
    val markerindex = html.indexOf(marker)
    require(markerindex >= 0, s"missing marker: $marker")
    val openingstart = html.lastIndexOf("<button", markerindex)
    require(openingstart >= 0, s"missing button opening tag for marker: $marker")
    val openingend = html.indexOf(">", openingstart)
    require(openingend >= 0, s"missing button opening tag terminator for marker: $marker")
    val opening = html.substring(openingstart, openingend + 1)
    require(opening == "<button>" || opening.startsWith("<button "), s"missing literal button element for marker: $marker")
    val closingtag = "</button>"
    val closingstart = html.indexOf(closingtag, openingend + 1)
    require(closingstart >= 0, s"missing button closing tag for marker: $marker")
    html.substring(openingstart, closingstart + closingtag.length)
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
