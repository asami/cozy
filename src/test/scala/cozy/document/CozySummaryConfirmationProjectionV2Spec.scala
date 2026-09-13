package cozy.document

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import java.security.MessageDigest
import org.scalacheck.{Gen, Prop, Test}
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
final class CozySummaryConfirmationProjectionV2Spec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Summary Confirmation Projection v2" should {
    "preserve explicit item focus in ordinary and overview partitions under reordered items and inverse readings" in {
      _with_temp_dir("cozy-summary-v2-explicit-focus") { root =>
        Given("an admitted ordinary mixed diagram and an explicit Root overview with no inferred focus")
        val validated = _fixture(root).validatedsummary
        val units = Vector(validated.description.summary.units.head, _overview_unit(validated))
        val vocabulary = _vocabulary(validated.document.core)

        When("ScalaCheck varies diagram kind selected focus item order and edge direction")
        val choices = units.flatMap(unit => unit.diagram.get.items.map(item => (unit, item)))
        val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(60), Prop.forAll(Gen.oneOf(choices), Gen.oneOf(true, false), Gen.oneOf("forward", "inverse")) { (choice, reversed, direction) =>
          val (unit, item) = choice
          val diagram = unit.diagram.get.copy(focusItem = Some(item.id), items = if (reversed) unit.diagram.get.items.reverse else unit.diagram.get.items, edges = unit.diagram.get.edges.map(_.copy(direction = direction)))
          val selected = validated.copy(description = validated.description.copy(summary = validated.description.summary.copy(units = Vector(unit.copy(diagram = Some(diagram))))))
          val html = CozySummaryConfirmationProjectionV2.render(selected, vocabulary).html
          val slide = _slide(html, unit.id)
          val evidence = _evidence(html, unit.id)
          val concept = _tag(slide, s"""data-diagram-item-id="${item.id}""" )
          val audit = _tag(evidence, s"""data-diagram-item-id="${item.id}""" )
          concept.contains("class=\"concept key\"") && concept.contains("data-diagram-item-focus=\"true\"") &&
            audit.contains("data-diagram-item-focus=\"true\"") &&
            _occurrences(slide, "data-diagram-item-focus=") == 1 && _occurrences(evidence, "data-diagram-item-focus=") == 1 &&
            _occurrences(slide, "class=\"concept key\"") == 1 && slide.contains("<small class=\"concept-focus\">Emphasis</small>") &&
            _occurrences(slide, "data-diagram-item-id=") == diagram.items.size && _occurrences(slide, "data-diagram-edge-id=") == diagram.edges.size &&
            diagram.edges.forall(edge => _tag(slide, s"""data-diagram-edge-id="${edge.id}""" ).contains(s"""data-direction="$direction"""))
        })

        Then("one explicit concept and its audit entry carry readable emphasis without adding or dropping graph records")
        check.passed shouldBe true
        check.succeeded should be >= 60
      }
    }

    "separate one explicit top-level overview into containment Flow and local Structure without changing ordinary slides" in {
      _with_temp_dir("cozy-summary-v2-overview-regions") { root =>
        Given("an explicit Root overview preceding two admitted ordinary units")
        val validated = _fixture(root).validatedsummary
        val unit = _overview_unit(validated)
        val selected = validated.copy(description = validated.description.copy(summary = validated.description.summary.copy(units = unit +: validated.description.summary.units)))
        val vocabulary = _vocabulary(validated.document.core)

        When("ordinary and overview-bearing Summaries are projected")
        val before = CozySummaryConfirmationProjectionV2.render(validated, vocabulary).html
        val html = CozySummaryConfirmationProjectionV2.render(selected, vocabulary).html
        val containment = _overview_region(html, "containment")
        val flow = _overview_region(html, "flow")
        val structure = _overview_region(html, "structure")

        Then("the explicit first slide retains exact Root scope with no containment-as-Flow and no implicit unit")
        _tag(html, s"""id="semantic-${unit.id}""") should include("data-summary-overview-step")
        _tag(html, s"""id="semantic-${unit.id}""") should not include(" hidden")
        _occurrences(html, "data-summary-overview-step=") shouldBe 1
        containment should include("Step &lt;root&gt; &amp; &quot;quoted&quot;")
        unit.coreRefs.steps.tail.foreach(step => containment should include(s"""data-overview-child-step="$step"""))
        containment should not include("data-diagram-edge-id")
        containment should not include("edge-mark")
        flow should include("data-core-flow-id")
        flow should include("⇢")
        flow should include("""<span class="structure-mark" data-structure-kind="flow" aria-hidden="true">⇢</span> Flows</h3>""")
        flow should not include("data-diagram-edge-kind=\"relation\"")
        flow should not include("data-diagram-item-kind=\"node\"")
        structure should include("→")
        structure should include("""<span class="structure-mark" data-structure-kind="structure" aria-hidden="true">→</span> Relations</h3>""")
        structure should not include("data-diagram-edge-kind=\"flow-transition\"")
        structure should not include("data-diagram-item-kind=\"step\"")
        validated.description.summary.units.foreach { detail =>
          _slide(html, detail.id).replaceAll("data-summary-unit-(index|total)=\"[0-9]+\"", "").replaceAll(" hidden(?=>)", "") shouldBe _slide(before, detail.id).replaceAll("data-summary-unit-(index|total)=\"[0-9]+\"", "").replaceAll(" hidden(?=>)", "")
          _evidence(html, detail.id).replaceAll(" hidden(?=>)", "") shouldBe _evidence(before, detail.id).replaceAll(" hidden(?=>)", "")
        }
        before should not include("data-overview-region")
        html should include("aspect-ratio:16 / 9")
      }
    }

    "retain explicit overview partition order and typed Core provenance under generated inverse readings" in {
      _with_temp_dir("cozy-summary-v2-overview-provenance") { root =>
        Given("a coordinate-free explicit overview of only Root local sources and direct children")
        val validated = _fixture(root).validatedsummary
        val unit = _overview_unit(validated)
        val vocabulary = _vocabulary(validated.document.core)

        When("ScalaCheck varies item order containment order and declared direction")
        val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), Prop.forAll(Gen.oneOf("forward", "inverse"), Gen.oneOf(true, false)) { (direction, reversed) =>
          val diagram = unit.diagram.get.copy(items = if (reversed) unit.diagram.get.items.reverse else unit.diagram.get.items, edges = unit.diagram.get.edges.map(_.copy(direction = direction)))
          val overview = unit.copy(diagram = Some(diagram), coreRefs = unit.coreRefs.copy(steps = if (reversed) unit.coreRefs.steps.reverse else unit.coreRefs.steps))
          val selected = validated.copy(description = validated.description.copy(summary = validated.description.summary.copy(units = overview +: validated.description.summary.units)))
          val html = CozySummaryConfirmationProjectionV2.render(selected, vocabulary).html
          val slide = _slide(html, overview.id)
          val containment = _overview_region(html, "containment")
          val groups = Vector("flow" -> "step", "structure" -> "node")
          val order = groups.forall { case (region, kind) =>
            val group = _overview_region(html, region)
            val positions = diagram.items.filter(_.kind == kind).map(item => group.indexOf(s"""data-diagram-item-id="${item.id}"""))
            positions.forall(_ >= 0) && positions == positions.sorted
          }
          val children = overview.coreRefs.steps.filterNot(_ == overview.overview.get.stepRef).map(step => containment.indexOf(s"""data-overview-child-step="$step"""))
          val exact = diagram.edges.forall { edge =>
            val source = if (edge.kind == "relation") {
              val relation = validated.document.core.relationsById(edge.ref)
              (relation.from, relation.to, relation.relationType)
            } else {
              val transition = validated.document.core.core.root.flow.transitions.find(_.id == edge.ref).get
              (transition.fromStepId, transition.toStepId, transition.relationType)
            }
            val tag = _tag(slide, s"""data-diagram-edge-id="${edge.id}""")
            val from = if (direction == "forward") source._1 else source._2
            val to = if (direction == "forward") source._2 else source._1
            tag.contains(s"""data-core-from="${source._1}" data-core-to="${source._2}""") &&
            tag.contains(s"""data-core-edge-type="${source._3}""") &&
            tag.contains(s"""data-display-from="$from" data-display-to="$to""") &&
            (edge.kind == "relation" || tag.contains(s"""data-core-flow-id="${validated.document.core.core.root.flow.id}"""))
          }
          order && children.forall(_ >= 0) && children == children.sorted && exact && _occurrences(slide, "data-diagram-edge-id=") == diagram.edges.size
        })

        Then("containment and typed partitions preserve author order while direction reverses only display endpoints")
        check.passed shouldBe true
        check.succeeded should be >= 30
      }
    }

    "classify ordinary diagram regions with scope marks without mixing Flow and local Structure" in {
      _with_temp_dir("cozy-summary-v2-ordinary-scope-marks") { root =>
        Given("an admitted ordinary unit explicitly selecting both Node/Relation and Step/Flow records")
        val validated = _fixture(root).validatedsummary
        val unit = validated.description.summary.units.head
        val diagram = unit.diagram.get
        val vocabulary = _vocabulary(validated.document.core)

        When("ScalaCheck varies authored item order and declared edge direction")
        val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), Prop.forAll(Gen.oneOf(true, false), Gen.oneOf("forward", "inverse")) { (reversed, direction) =>
          val selecteddiagram = diagram.copy(items = if (reversed) diagram.items.reverse else diagram.items, edges = diagram.edges.map(_.copy(direction = direction)))
          val selected = validated.copy(description = validated.description.copy(summary = validated.description.summary.copy(units = Vector(unit.copy(diagram = Some(selecteddiagram))))))
          val slide = _slide(CozySummaryConfirmationProjectionV2.render(selected, vocabulary).html, unit.id)
          val groups = Vector(("structure", "node", "relation", "→", "Relations"), ("flow", "step", "flow-transition", "⇢", "Flows"))
          val partitions = groups.forall { case (scope, itemkind, edgekind, mark, wording) =>
            val region = _diagram_region(slide, scope)
            val positions = selecteddiagram.items.filter(_.kind == itemkind).map(item => region.indexOf(s"""data-diagram-item-id="${item.id}"""))
            region.contains(s"""<span class="structure-mark" data-structure-kind="$scope" aria-hidden="true">$mark</span> $wording</h3>""") &&
              positions.forall(_ >= 0) && positions == positions.sorted &&
              selecteddiagram.items.filterNot(_.kind == itemkind).forall(item => !region.contains(s"""data-diagram-item-id="${item.id}""")) &&
              selecteddiagram.edges.filter(_.kind == edgekind).forall(edge => region.contains(s"""data-diagram-edge-id="${edge.id}""")) &&
              selecteddiagram.edges.filterNot(_.kind == edgekind).forall(edge => !region.contains(s"""data-diagram-edge-id="${edge.id}"""))
          }
          val scopes = selecteddiagram.items.map(item => if (item.kind == "step") "flow" else "structure").distinct
          val positions = scopes.map(scope => slide.indexOf(s"""data-diagram-scope="$scope"""))
          partitions && positions == positions.sorted && _occurrences(slide, "data-diagram-edge-id=") == diagram.edges.size
        })

        Then("each scope is marked and isolated while authored partition order and exact selected records are retained")
        check.passed shouldBe true
        check.succeeded should be >= 30
      }
    }

    "mark only the selected scope for a single-kind ordinary diagram" in {
      _with_temp_dir("cozy-summary-v2-single-scope-marks") { root =>
        Given("an admitted unit whose diagram can select either only local Structure or only Flow")
        val validated = _fixture(root).validatedsummary
        val unit = validated.description.summary.units.head
        val vocabulary = _vocabulary(validated.document.core)

        When("each explicit single-kind selection is projected")
        val slides = Vector(("node", "relation", "structure", "flow"), ("step", "flow-transition", "flow", "structure")).map { case (itemkind, edgekind, scope, other) =>
          val diagram = unit.diagram.get.copy(items = unit.diagram.get.items.filter(_.kind == itemkind), edges = unit.diagram.get.edges.filter(_.kind == edgekind))
          val selected = validated.copy(description = validated.description.copy(summary = validated.description.summary.copy(units = Vector(unit.copy(diagram = Some(diagram))))))
          (scope, other, diagram, _slide(CozySummaryConfirmationProjectionV2.render(selected, vocabulary).html, unit.id))
        }

        Then("there is one marked region and no synthesized region or edge of the other kind")
        slides.foreach { case (scope, other, diagram, slide) =>
          slide should include(s"""data-diagram-scope="$scope""" )
          slide should include(s"""data-structure-kind="$scope""" )
          slide should not include(s"""data-diagram-scope="$other""" )
          _occurrences(slide, "data-diagram-item-id=") shouldBe diagram.items.size
          _occurrences(slide, "data-diagram-edge-id=") shouldBe diagram.edges.size
        }
      }
    }

    "show exact selected pattern tags and unique endpoint-addressable diagram items without arbitrary emphasis" in {
      _with_temp_dir("cozy-summary-v2-text-tags") { root =>
        Given("one admitted ordinary unit selecting local Structure and child Flow")
        val validated = _fixture(root).validatedsummary
        val unit = validated.description.summary.units.head
        val vocabulary = _vocabulary(validated.document.core)

        When("the selected unit is projected")
        val html = CozySummaryConfirmationProjectionV2.render(validated, vocabulary).html
        val slide = _slide(html, unit.id)

        Then("readable tags carry exact pattern/type identities and connectors address endpoints instead of adjacent items")
        slide should include("class=\"structure-tag\"")
        slide should include("data-logical-pattern=\"mapping\"")
        slide should include("Generic wording mapping")
        slide should include("class=\"diagram-connections\"")
        slide should not include("class=\"concept key\"")
        _occurrences(slide, "data-diagram-item-id=") shouldBe unit.diagram.get.items.size
        html should include("edge.getAttribute('data-display-from')")
        html should include("edge.getAttribute('data-display-to')")
        html should include("path.setAttribute('data-connection-edge-id'")
      }
    }

    "reject missing inverse wording rather than rendering a reversed edge with its canonical label" in {
      _with_temp_dir("cozy-summary-v2-inverse-wording") { root =>
        Given("an admitted unit with an explicitly inverse Relation edge")
        val validated = _fixture(root).validatedsummary
        val unit = validated.description.summary.units.head
        val diagram = unit.diagram.get.copy(edges = unit.diagram.get.edges.map(_.copy(direction = "inverse")))
        val selected = validated.copy(description = validated.description.copy(summary = validated.description.summary.copy(units = Vector(unit.copy(diagram = Some(diagram))))))
        val vocabulary = _vocabulary(validated.document.core).copy(inverseRelationTypes = Map.empty)

        When("the caller omits inverse type wording")
        val fault = intercept[CozySummaryConfirmationProjectionV2.ProjectionFault](CozySummaryConfirmationProjectionV2.render(selected, vocabulary))

        Then("the vocabulary boundary rejects before output without grammatical inference")
        fault.code shouldBe "SUMMARY_CONFIRMATION_V2_VOCABULARY"
        fault.reason should include("inverse Relation type")
      }
    }

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
        first.html should include("<title>Summary &lt;title&gt; &amp; &quot;quoted&quot;</title>")
        first.html should include("<div class=\"kicker\">Summary &lt;title&gt; &amp; &quot;quoted&quot;</div>")
        first.html should include("<h1>Summary confirmation &lt;screen&gt; &amp; &quot;quoted&quot;</h1>")
        first.html should not include("<h1>Summary &lt;title&gt;")
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
        html should include("<div class=\"flow-label\" id=\"summary-navigation-region\"")
        html should include("<ol class=\"flow\" style=\"--summary-unit-count:2\"")
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
        val evidenceposition = html.indexOf(s"""id="evidence-${unit.id}"""")
        val auditposition = html.indexOf("<details class=\"audit\">")

        Then("the browser view retains exact typed evidence without inferred diagram or Document content")
        Vector("steps", "claims", "nodes", "relations", "flows").foreach(kind => html should include(s"""data-source-category="$kind"""))
        unit.retainedPoints.foreach(point => html should include(s"""data-retained-point-id="${point.id}""""))
        relationtag should include(s"""data-diagram-edge-kind="${relationedge.kind}"""")
        relationtag should include(s"""data-core-ref="${relationedge.ref}"""")
        relationtag should include("data-direction=\"inverse\"")
        val relation = fixture.validatedsummary.document.core.relationsById(relationedge.ref)
        relationtag should include(s"""data-core-edge-type="${relation.relationType}"""")
        relationtag should include(s"""data-core-from="${relation.from}" data-core-to="${relation.to}"""")
        relationtag should include(s"""data-display-from="${relation.to}" data-display-to="${relation.from}"""")
        relationtag should not include("data-core-flow-id")
        flowtag should include(s"""data-diagram-edge-kind="${flowedge.kind}"""")
        flowtag should include(s"""data-core-ref="${flowedge.ref}"""")
        flowtag should include("data-direction=\"forward\"")
        val owner = fixture.validatedsummary.document.core.depthFirstSteps.find(_.flow.transitions.exists(_.id == flowedge.ref)).get
        val transition = owner.flow.transitions.find(_.id == flowedge.ref).get
        flowtag should include(s"""data-core-edge-type="${transition.relationType}"""")
        flowtag should include(s"""data-core-from="${transition.fromStepId}" data-core-to="${transition.toStepId}"""")
        flowtag should include(s"""data-display-from="${transition.fromStepId}" data-display-to="${transition.toStepId}"""")
        flowtag should include(s"""data-core-flow-id="${owner.flow.id}"""")
        html should include(s"""data-structure-type="${relation.relationType}">Relations · Inverse Generic wording ${relation.relationType}</span>""")
        html should include(s"""data-structure-type="${transition.relationType}">Flows · Generic wording ${transition.relationType}</span>""")
        html should include("""<span class="edge-mark" aria-hidden="true">→</span>""")
        html should include("""<span class="edge-mark" aria-hidden="true">⇢</span>""")
        html should include(s"""data-diagram-item-kind="step"""")
        html should include(s"""data-diagram-item-kind="node"""")
        omissiontag should include("data-document-kind=\"section\"")
        omissiontag should include("data-document-ref=\"document-root\"")
        omissiontag should include("data-omission-disposition=\"condensed\"")
        html should include("Section &lt;rationale&gt; &amp; &quot;quoted&quot;")
        html should include("<ul class=\"primary-sources\">")
        html should include("<ul class=\"retained-points\">")
        html should include("Root heading")
        html should include("<details class=\"audit\"><summary>Authored diagram</summary>")
        auditposition should be > evidenceposition
        html.substring(evidenceposition, auditposition) should not include(s"<h3>${unit.heading}")
      }
    }

    "preserve exact adopted edge provenance under generated directions and item order" in {
      _with_temp_dir("cozy-summary-confirmation-v2-edge-provenance") { root =>
        Given("an admitted Summary with explicit Relation and Flow edges and unselected Core edges")
        val fixture = _fixture(root)
        val validated = fixture.validatedsummary
        val unit = validated.description.summary.units.head
        val diagram = unit.diagram.get
        val core = validated.document.core
        val vocabulary = _vocabulary(core)

        When("ScalaCheck varies both declared directions and reverses diagram item order")
        val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), Prop.forAll(Gen.oneOf("forward", "inverse"), Gen.oneOf(true, false)) { (direction, reversed) =>
          val selecteddiagram = diagram.copy(
            items = if (reversed) diagram.items.reverse else diagram.items,
            edges = diagram.edges.map(_.copy(direction = direction))
          )
          val selected = validated.copy(description = validated.description.copy(
            summary = validated.description.summary.copy(units = unit.copy(diagram = Some(selecteddiagram)) +: validated.description.summary.units.tail)
          ))
          val html = CozySummaryConfirmationProjectionV2.render(selected, vocabulary).html
          val exact = selecteddiagram.edges.forall { edge =>
            val (from, to, edgetype, flowid, mark) = edge.kind match {
              case "relation" =>
                val relation = core.relationsById(edge.ref)
                (relation.from, relation.to, relation.relationType, "", "→")
              case "flow-transition" =>
                val owner = core.depthFirstSteps.find(_.flow.transitions.exists(_.id == edge.ref)).get
                val transition = owner.flow.transitions.find(_.id == edge.ref).get
                (transition.fromStepId, transition.toStepId, transition.relationType, owner.flow.id, "⇢")
            }
            val displayfrom = if (direction == "forward") from else to
            val displayto = if (direction == "forward") to else from
            val flowtag = if (flowid.isEmpty) "" else {
              val flowwording = if (direction == "forward") vocabulary.flowTypes(edgetype) else vocabulary.inverseFlowTypes(edgetype)
              s"""data-structure-kind="flow" data-structure-type="$edgetype">$flowwording</span>"""
            }
            val flowedgetag = if (flowid.isEmpty) "" else {
              val flowwording = if (direction == "forward") vocabulary.flowTypes(edgetype) else vocabulary.inverseFlowTypes(edgetype)
              s"""data-structure-kind="flow" data-structure-type="$edgetype">Flows · $flowwording</span>"""
            }
            val marker = s"""data-diagram-edge-id="${edge.id}"""
            val parts = html.split(java.util.regex.Pattern.quote(marker), -1).tail
            val tags = parts.map(part => part.takeWhile(_ != '>'))
            tags.length == 2 && tags.forall(tag =>
              tag.contains(s"""data-core-ref="${edge.ref}""" ) &&
              tag.contains(s"""data-core-edge-type="$edgetype""" ) &&
              tag.contains(s"""data-core-from="$from" data-core-to="$to""" ) &&
              tag.contains(s"""data-display-from="$displayfrom" data-display-to="$displayto""" ) &&
              (if (flowid.isEmpty) !tag.contains("data-core-flow-id") else tag.contains(s"""data-core-flow-id="$flowid""" ))
            ) && (if (flowid.isEmpty) true else {
              val slide = _slide(html, unit.id)
              _occurrences(slide, flowtag) == 1 && _occurrences(slide, flowedgetag) == 1
            }) &&
              parts.head.substring(0, parts.head.indexOf("</div>")).contains(s"""<span class="edge-mark" aria-hidden="true">$mark</span>""") &&
              parts.forall(part => part.substring(0, part.indexOf("</div>")).contains((if (direction == "inverse") "Inverse " else "") + s"Generic wording $edgetype"))
          }
          exact && _occurrences(html, "data-diagram-edge-id=") == selecteddiagram.edges.size * 2
        })

        Then("slide and audit retain exact typed sources and direction-adjusted endpoints without adding edges")
        check.passed shouldBe true
        check.succeeded should be >= 30
      }
    }

    "show a reference-shaped selected slide and inspector with deliberate selective status" in {
      _with_temp_dir("cozy-summary-confirmation-v2-accessibility") { root =>
        Given("an admitted Summary whose second unit has no authored diagram")
        val fixture = _fixture(root)
        val html = CozySummaryConfirmationProjectionV2.render(fixture.validatedsummary, _vocabulary(fixture.validatedsummary.document.core)).html
        val secondunit = fixture.validatedsummary.description.summary.units(1)
        val secondpanel = _tag(html, s"""id="semantic-${secondunit.id}"""")

        When("the self-contained reference-shaped workspace is composed")
        val statusposition = html.indexOf("Selected explicit Summary sources are current and admitted")
        val identityposition = html.indexOf("Core identity")

        Then("status stays selective, identities stay secondary, and the connected native flow remains accessible")
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
        html should include("@media (max-width:850px){.page{padding:14px}.header{align-items:flex-start;flex-direction:column}.flow{grid-template-columns:1fr}")
        html should include(":focus-visible")
        html should include(".flow{display:grid;grid-template-columns:repeat(var(--summary-unit-count),minmax(0,1fr));margin:0 0 18px;padding:0;border:1px solid var(--border);background:var(--card)}")
        html should include("style=\"--summary-unit-count:2\"")
        html should include(".flow li:not(:last-child)::after{content:\"›\"")
        html should include(".workspace{display:grid;grid-template-columns:minmax(0,1.55fr) minmax(250px,.55fr);gap:18px;align-items:start}")
        html should include("class=\"detail-panel semantic-panel slide\"")
        html should include("class=\"inspector\"")
        html should include("<h2>Selected sources</h2>")
        html should include("aria-label=\"Selected sources\"")
        html should include("<section><h3>Steps</h3>")
        html should include("data-summary-diagram=\"true\"")
        html should include("aria-live=\"polite\"")
        html should include("aria-describedby=\"status-region\"")
        html should not include("<link")
        html.replace("http://www.w3.org/2000/svg", "") should not include("http://")
        html should not include("https://")
        html should not include("<canvas")
        html should not include("fetch(")
      }
    }

    "separate compact exact omission labels from full target content in the collapsed audit" in {
      _with_temp_dir("cozy-summary-confirmation-v2-omission-presentation") { root =>
        Given("an admitted Summary with a paragraph and long list item owned directly by Root heading")
        val fixture = _fixture(root)
        val unit = fixture.validatedsummary.description.summary.units(1)
        val vocabulary = _vocabulary(fixture.validatedsummary.document.core)

        When("the selected unit evidence is projected without changing its authored target content")
        val html = CozySummaryConfirmationProjectionV2.render(fixture.validatedsummary, vocabulary).html
        val evidence = _evidence(html, unit.id)
        val auditposition = evidence.indexOf("<details class=\"audit\">")
        val primary = evidence.substring(0, auditposition)
        val audit = evidence.substring(auditposition)

        Then("exactly three ordered bullet sections show owner headings and rationale while audit preserves all original text and provenance")
        _occurrences(primary, "<section>") shouldBe 3
        primary.indexOf("<h3>Steps</h3>") should be < primary.indexOf("<h3>Retained points</h3>")
        primary.indexOf("<h3>Retained points</h3>") should be < primary.indexOf("<h3>Document omissions</h3>")
        primary should include("<ul class=\"retained-points\">")
        primary should include("<ul class=\"omission-list\">")
        primary should not include("<ol")
        _occurrences(primary, "<span class=\"omission-label\">Root heading</span>") shouldBe 2
        primary should include("<p>Paragraph is omitted</p>")
        primary should include("<p>List item is condensed</p>")
        primary should not include("Paragraph wording")
        primary should not include("List item wording")
        primary should not include("(Omitted)")
        primary.indexOf("data-omission-id=\"omission-block\"") should be < primary.indexOf("data-omission-id=\"omission-list-item\"")
        audit should include(_paragraph_text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"))
        audit should include(_long_item_text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"))
        audit should include("data-document-kind=\"block\" data-document-ref=\"paragraph-one\" data-omission-disposition=\"omitted\"")
        audit should include("data-document-kind=\"list-item\" data-document-ref=\"list-item-one\" data-omission-disposition=\"condensed\"")
        audit should include("<p>Rationale: Paragraph is omitted</p>")
        audit should include("<p>Rationale: List item is condensed</p>")
        Vector("steps", "claims", "nodes", "relations", "flows").foreach(kind => audit should include(s"""data-source-category="$kind"""))
        html should not include("-webkit-line-clamp")
        html should not include("omission-label-paragraph")
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

  private def _overview_unit(validated: CozyDocumentDescriptionV2.ValidatedSummary): CozyDocumentDescriptionV2.SummaryUnit = {
    val root = validated.document.core.core.root
    val refs = CozyDocumentDescriptionV2.References(root.id +: root.steps.map(_.id), root.claims.map(_.id), root.structure.nodes.map(_.id), root.structure.relations.map(_.id), Vector(root.flow.id))
    val items = root.structure.nodes.map(node => CozyDocumentDescriptionV2.DiagramItem(s"overview-node-${node.id}", "node", node.id)) ++ root.steps.map(step => CozyDocumentDescriptionV2.DiagramItem(s"overview-step-${step.id}", "step", step.id))
    val edges = root.structure.relations.map(relation => CozyDocumentDescriptionV2.DiagramEdge(s"overview-relation-${relation.id}", "relation", relation.id, "forward")) ++ root.flow.transitions.map(transition => CozyDocumentDescriptionV2.DiagramEdge(s"overview-transition-${transition.id}", "flow-transition", transition.id, "forward"))
    validated.description.summary.units.head.copy(id = "explicit-overview", heading = "Whole document overview", navigationLabel = "Overview", coreRefs = refs, retainedPoints = Vector(CozyDocumentDescriptionV2.RetainedPoint("overview-point", "Root meaning retained", refs)), diagram = Some(CozyDocumentDescriptionV2.Diagram(items, edges)), overview = Some(CozyDocumentDescriptionV2.Overview(root.id)))
  }

  private def _overview_region(html: String, kind: String): String = {
    val marker = html.indexOf(s"""data-overview-region="$kind""")
    require(marker >= 0, s"missing overview region: $kind")
    val start = html.lastIndexOf("<section", marker)
    val end = html.indexOf("</section>", marker)
    html.substring(start, end + "</section>".length)
  }

  private def _diagram_region(html: String, scope: String): String = {
    val marker = html.indexOf(s"""data-diagram-scope="$scope""" )
    require(marker >= 0, s"missing diagram scope: $scope")
    val start = html.lastIndexOf("<section", marker)
    val end = html.indexOf("</section>", marker)
    html.substring(start, end + "</section>".length)
  }

  private def _slide(html: String, unitid: String): String = {
    val start = html.indexOf(s"""<article id="semantic-$unitid""")
    require(start >= 0, s"missing semantic panel: $unitid")
    val end = html.indexOf("</article>", start)
    html.substring(start, end + "</article>".length)
  }

  private val _paragraph_text = "Paragraph wording <full> & \"quoted\" stays exactly as authored, including this detailed explanation of the source content and the reason it must remain available in the audit without clipping or condensation."
  private val _long_item_text = "List item wording <full> & \"quoted\" stays exactly as authored, including this detailed item explanation that exceeds the existing compact-target threshold and remains fully available in the audit."

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
        |          text: '${_paragraph_text}'
        |          coreRefs: $refs
        |        - id: list-one
        |          kind: list
        |          items:
        |            - id: list-item-one
        |              text: '${_long_item_text}'
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
      "Summary confirmation <screen> & \"quoted\"",
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
      directions,
      _wording(core.depthFirstSteps.map(_.structure.pattern).toSet),
      relationtypes.map { case (key, wording) => key -> s"Inverse $wording" },
      flowtypes.map { case (key, wording) => key -> s"Inverse $wording" }
    )
  }

  private def _wording(values: Set[String]): Map[String, String] = values.toVector.sorted.map(value => value -> s"Generic wording $value").toMap
  private def _evidence(html: String, unitid: String): String = {
    val start = html.indexOf(s"""<article id="evidence-$unitid"""")
    require(start >= 0, s"missing evidence panel: $unitid")
    val end = html.indexOf("</article>", start)
    require(end >= 0, s"missing evidence closing tag: $unitid")
    html.substring(start, end + "</article>".length)
  }
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
