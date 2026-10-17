package cozy.ui

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import cozy.ui.CozyLogicalUi._
import cozy.ui.CozyLogicalUiReadiness._
import cozy.ui.CozyLogicalUiReadinessReview.{CompleteBinding, ReadinessScenario}
import cozy.ui.CozyLogicalUiReadinessWeb._
import cozy.ui.CozyLogicalUiReview.ReviewDiagnostic
import cozy.ui.CozyLogicalUiSelection._
import cozy.ui.CozyLogicalUiSemantics._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

/*
 * @since   Oct. 8, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyLogicalUiReadinessWebSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "One complete Progressive Static Web projection" should {
    "complete source and target binding" which {
      "align app, structural review, comparison and receipts with one complete candidate and all admitted DOM roles" in {
        Given("the complete candidate with absent optional acceptance values and one shared 21-case SalesOrder scenario")
        val base = _ready
        val scenario = _scenario
        val review = _right(CozyLogicalUiReadinessReview.renderCandidate(base, scenario))

        When("the one Web target consumes the shared complete structural binding")
        val preview = _right(renderCandidate(base, scenario))
        val json = Json.parse(preview.receiptJson)
        val embedded = _embedded(preview.html)

        Then("the target binds full source declarations and fixed source hooks without granting acceptance or browser assurance")
        preview.receipt shouldBe WebReceipt("cozy.logical-ui-target-ready-web.v1", 1,
          "cozy.logical-ui-target-ready-web.renderer.v1", review.receipt, true, _outputs)
        preview.reviewHtml shouldBe review.html
        preview.reviewReceiptJson shouldBe review.receiptJson
        preview.diagnostics shouldBe review.diagnostics
        (json \ "reviewReceipt").get shouldBe Json.parse(review.receiptJson)
        embedded shouldBe json
        (json \ "simulated").as[Boolean] shouldBe true
        (json \ "reviewReceipt" \ "binding" \ "decision").get shouldBe JsNull
        val binding = review.receipt.binding
        Vector("readiness-preview", "order-list-screen", "order-detail-screen", "open-detail", "order-reference",
          "confirmation-option", "confirm-order", "domain-state", "ui-state", "workflow-evidence", "feedback",
          "input-error", "observed-result", "complete-confirmation", "fixture-outcome", "reset-fixture", "replay-fixture",
          "recovery-controls", "preview-diagnostics").foreach { id => preview.html should include ("id=\"" + id + "\"") }
        base.input.controls.foreach { control =>
          preview.html should include ("data-control-role=\"" + control.role.id + "\"")
          preview.html should include ("data-interaction-id=\"" + control.control.interactionId + "\"")
          preview.html should include ("data-label-text-id=\"" + control.labelTextId + "\"")
          control.focusOrder.foreach(rank => preview.html should include ("data-focus-order=\"" + rank + "\""))
          val label = base.input.textSources.find(_.textId == control.labelTextId).get.defaultText
          preview.html should include (label)
        }
        preview.html should include ("inputmode=\"text\"")
        preview.html should include ("aria-required=\"true\"")
        preview.html should include ("aria-invalid=\"false\"")
        preview.html should include ("aria-describedby=\"input-error\"")
        preview.html should include ("aria-live=\"polite\"")
        preview.html should not include ("tabindex=\"")
        "src=\"index.html\"".r.findAllIn(preview.compareHtml).size shouldBe 3
        Vector("390", "834", "1440").foreach(width => preview.compareHtml should include ("width=\"" + width + "\""))
        Vector("844", "1112", "900").foreach(height => preview.compareHtml should include ("height=\"" + height + "\""))
        Then("the emitted candidate comparison remains commit-clean without trailing ASCII space or tab on any line")
        preview.compareHtml.split("\n", -1).foreach { line =>
          line should not endWith (" ")
          line should not endWith ("\t")
        }
        preview.htmlBytes shouldBe preview.html.getBytes(StandardCharsets.UTF_8).toVector
        preview.reviewHtmlBytes shouldBe preview.reviewHtml.getBytes(StandardCharsets.UTF_8).toVector
        preview.compareHtmlBytes shouldBe preview.compareHtml.getBytes(StandardCharsets.UTF_8).toVector
        preview.receiptJsonBytes shouldBe preview.receiptJson.getBytes(StandardCharsets.UTF_8).toVector
        preview.reviewReceiptJsonBytes shouldBe preview.reviewReceiptJson.getBytes(StandardCharsets.UTF_8).toVector
      }

      "propagate genuine accepted complete attribution through every target surface" in {
        Given("an explicit specification decision from an identified reviewer naming this exact complete candidate")
        val base = _ready
        val decision = TargetReadyAcceptanceDecision("web-spec-decision", "web-spec-reviewer")
        val accepted = _right(CozyLogicalUiReadiness.accept(base, decision))
        val candidate = _right(renderCandidate(base, _scenario))

        When("the accepted complete target and invalid or mismatched modes are consumed")
        val preview = _right(renderAccepted(accepted, _scenario))
        val json = _embedded(preview.html)
        val nullerrors = Vector(renderCandidate(null, _scenario), renderAccepted(null, _scenario), renderCandidate(base, null)).map(_left)

        Then("the complete accepted identity and reviewer remain exact while the input is shared with candidate mode")
        preview.receipt.reviewReceipt.binding shouldBe CompleteBinding(Some(decision))
        preview.receipt.reviewReceipt.sourceInputs shouldBe candidate.receipt.reviewReceipt.sourceInputs
        (json \ "reviewReceipt" \ "binding" \ "decision" \ "reviewerId").as[String] shouldBe decision.reviewerId
        Vector(preview.html, preview.reviewHtml, preview.compareHtml, preview.receiptJson, preview.reviewReceiptJson).foreach { surface =>
          surface should include (decision.decisionId)
          surface should include (decision.reviewerId)
        }
        nullerrors.map(_.path) shouldBe Vector("completeCandidate", "accepted", "scenario")
        nullerrors.map(_.code).distinct shouldBe Vector("LUI501_WEB_CONTRACT_INVALID")
      }

      "retain all seven exact source outcome and recovery declarations across all three admitted profiles" in {
        Given("the seven declared UI/domain/Workflow outcomes and their exact recovery targets")
        val base = _ready
        val expected = Vector(
          ("normal", NormalFeedback, SucceededInteractionState, "confirmed", "invoke", Vector.empty[String]),
          ("loading", LoadingFeedback, PendingInteractionState, "draft", "invoke", Vector.empty[String]),
          ("empty", EmptyFeedback, IdleInteractionState, "draft", "entry", Vector("retry-interaction:order-list:entry")),
          ("validation-failed", ValidationFailedFeedback, FailedInteractionState, "draft", "input", Vector("edit-input:order-detail:input")),
          ("conflict", ConflictFeedback, FailedInteractionState, "draft", "invoke", Vector("edit-input:order-detail:input")),
          ("unavailable", UnavailableFeedback, FailedInteractionState, "draft", "invoke", Vector("retry-interaction:order-detail:invoke")),
          ("operation-failed", OperationFailedFeedback, FailedInteractionState, "draft", "invoke", Vector("retry-interaction:order-detail:invoke")))

        When("the typed target projects all declared cases into its complete inert payload")
        val preview = _right(renderCandidate(base, _scenario))
        val receipt = preview.receipt.reviewReceipt
        val input = (_embedded(preview.html) \ "reviewReceipt" \ "sourceInputs" \ "readinessContent" \ "readiness").get
        val outcomes = (input \ "outcomes").as[Vector[JsObject]]

        Then("every profile/outcome pair retains exact source state, trigger, message, step and recovery without executing it")
        receipt.scenario.coverage.size shouldBe 21
        receipt.scenario.coverage.map(value => (value.windowClass, value.outcomeId)).toSet shouldBe
          Vector(CompactIntent, MediumIntent, ExpandedIntent).flatMap(profile => expected.map(value => (profile, value._1))).toSet
        expected.foreach { case (id, feedbackstate, uistate, domainstate, trigger, recoveries) =>
          val declaration = base.input.outcomes.find(_.outcomeId == id).get
          val outcome = outcomes.find(value => (value \ "outcomeId").as[String] == id).get
          declaration.feedbackState shouldBe feedbackstate
          declaration.uiState shouldBe uistate
          declaration.domainState.stateId shouldBe domainstate
          declaration.trigger.interactionId shouldBe trigger
          declaration.recoveries.map(value => value.kind.id + ":" + value.target.screenId + ":" + value.target.interactionId) shouldBe recoveries
          (outcome \ "feedbackState").as[String] shouldBe feedbackstate.id
          (outcome \ "uiState").as[String] shouldBe uistate.id
          (outcome \ "domainState" \ "stateId").as[String] shouldBe domainstate
          (outcome \ "trigger" \ "interactionId").as[String] shouldBe trigger
          (outcome \ "messageTextId").as[String] shouldBe declaration.messageTextId
          (outcome \ "step" \ "stepId").as[String] shouldBe declaration.step.stepId
          (outcome \ "recoveries").as[Vector[JsObject]].map(value => (value \ "step" \ "stepId").as[String]).distinct shouldBe
            declaration.recoveries.map(_.step.stepId).distinct
          declaration.workflowState shouldBe (if (trigger == "invoke") Some(WorkflowStateReference("sales-order-workflow", "awaiting")) else None)
        }
        val profiles = (input \ "profiles").as[Vector[JsObject]]
        profiles.map(profile => (profile \ "windowClass").as[String]).toSet shouldBe Set("compact", "medium", "expanded")
        profiles.foreach { profile =>
          val expanded = (profile \ "windowClass").as[String] == "expanded"
          (profile \ "detailPlacement").as[String] shouldBe (if (expanded) "update-detail-region" else "navigate-to-detail")
          (profile \ "persistentNavigation").as[Boolean] shouldBe ((profile \ "windowClass").as[String] != "compact")
          (profile \ "regions").as[Vector[JsObject]].map(region => (region \ "persistent").as[Boolean]).distinct shouldBe Vector(expanded)
        }
      }

      "reject contradictory source state and unsupported or incomplete bounded driver metadata" in {
        Given("factory-admitted inputs that exceed or contradict the frozen one-flow driver contract")
        val base = _ready
        val variants = Vector(
          base.input.copy(outcomes = base.input.outcomes.filterNot(_.feedbackState == LoadingFeedback)) -> "input.outcomes",
          base.input.copy(controls = base.input.controls.map(control =>
            if (control.role == NavigationControl) control.copy(focusOrder = Some(5)) else control)) -> "input.controls.focusOrder",
          base.input.copy(profiles = base.input.profiles.map(profile =>
            if (profile.windowClass == CompactIntent) profile.copy(persistentNavigation = true) else profile)) -> "input.profiles",
          base.input.copy(fields = Vector(base.input.fields.head.copy(labelTextId = "navigation-label"))) -> "input.fields[0].labelTextId",
          base.input.copy(profiles = base.input.profiles.map(profile => profile.copy(regions = profile.regions.map(region =>
            if (region.role == SecondaryRegion) region.copy(role = PrimaryRegion) else region)))) -> "input.profiles",
          base.input.copy(outcomes = base.input.outcomes.map(outcome =>
            if (outcome.feedbackState == ConflictFeedback) outcome.copy(domainState = _scenario.normal.successfulOrder.domainState) else outcome)) -> "domainState")
        val candidates = variants.map { case (input, _) => _admit(base, input) }
        val foreignflow = _scenario.copy(normal = _scenario.normal.copy(flow = _scenario.normal.flow.copy(commandInteractionId = "foreign")))
        val absentcoverage = _scenario.copy(coverage = _scenario.coverage.tail)

        When("the target consumes unsupported declarations, incomplete coverage and a foreign source flow")
        val errors = candidates.map(candidate => _left(renderCandidate(candidate, _scenario)))
        val scenarioerrors = Vector(renderCandidate(base, foreignflow), renderCandidate(base, absentcoverage)).map(_left)

        Then("the target reports structured paths instead of dropping source meaning or fabricating defaults")
        errors.dropRight(1).map(_.path) shouldBe variants.dropRight(1).map(_._2)
        errors.last.path should endWith (".domainState")
        errors.map(_.code).distinct shouldBe Vector("LUI501_WEB_CONTRACT_INVALID")
        scenarioerrors.map(_.path) shouldBe Vector("scenario.normal.flow.commandInteractionId", "scenario.coverage")
      }

      "retain mapped return-to-entry and decimal intent without replacing source declarations" in {
        Given("a declared decimal input and an admitted return-to-entry recovery for conflict")
        val base = _ready
        val entry = InteractionRef("order-list", "entry")
        val input: ReadinessInput = base.input.copy(fields = Vector(base.input.fields.head.copy(inputIntent = DecimalInputIntent)),
          outcomes = base.input.outcomes.map(outcome => if (outcome.feedbackState == ConflictFeedback)
            outcome.copy(recoveries = Vector(RecoveryBinding(ReturnToEntry,
              UiUseCaseStep(CozyLogicalUiSalesOrderFixtures.uiUseCase, "recover", AlternativePath), entry))) else outcome))
        val revised = _admit(base, input)

        When("the complete target renders these supported source metadata alternatives")
        val preview = _right(renderCandidate(revised, _scenario))
        val outcomes = (_embedded(preview.html) \ "reviewReceipt" \ "sourceInputs" \ "readinessContent" \ "readiness" \ "outcomes").as[Vector[JsObject]]

        Then("the payload retains the exact return step and target and the native field exposes decimal keyboard meaning")
        preview.html should include ("inputmode=\"decimal\"")
        val recovery = (outcomes.find(value => (value \ "outcomeId").as[String] == "conflict").get \ "recoveries").as[Vector[JsObject]].head
        (recovery \ "kind").as[String] shouldBe "return-to-entry"
        (recovery \ "step" \ "stepId").as[String] shouldBe "recover"
        (recovery \ "target" \ "screenId").as[String] shouldBe entry.screenId
        (recovery \ "target" \ "interactionId").as[String] shouldBe entry.interactionId
      }
    }

    "determinism, revisions and safe inert payload" which {
      "produce identical complete target observables for independent input and coverage permutations" in {
        Given("a complete target baseline and independent random orderings of source and coverage declarations")
        val base = _ready
        val baseline = _right(renderCandidate(base, _scenario))
        val property = Prop.forAll(Gen.choose(0, Int.MaxValue), Gen.choose(0, Int.MaxValue)) { (inputseed, coverageseed) =>
          val random = new scala.util.Random(inputseed)
          val kernel = _right(CozyLogicalUi.candidate(base.kernelCandidate.input.copy(
            componentBindings = random.shuffle(base.kernelCandidate.input.componentBindings),
            componentSurfaces = random.shuffle(base.kernelCandidate.input.componentSurfaces).map(surface => surface.copy(exportIds = random.shuffle(surface.exportIds))))))
          val projection = _right(CozyLogicalUi.project(kernel, base.projection.input.copy(
            screens = random.shuffle(base.projection.input.screens).map(screen => screen.copy(regions = random.shuffle(screen.regions), interactions = random.shuffle(screen.interactions))),
            steps = random.shuffle(base.projection.input.steps), mappings = random.shuffle(base.projection.input.mappings))))
          val semantics = _right(CozyLogicalUiSemantics.project(projection, base.semantics.input.copy(
            screens = random.shuffle(base.semantics.input.screens), componentBindings = random.shuffle(base.semantics.input.componentBindings),
            constraints = random.shuffle(base.semantics.input.constraints), feedbackAssociations = random.shuffle(base.semantics.input.feedbackAssociations),
            transitionActions = random.shuffle(base.semantics.input.transitionActions))))
          val input = base.input.copy(profiles = random.shuffle(base.input.profiles).map(profile => profile.copy(regions = random.shuffle(profile.regions))),
            controls = random.shuffle(base.input.controls), fields = random.shuffle(base.input.fields).map(field => field.copy(localRules = random.shuffle(field.localRules))),
            textSources = random.shuffle(base.input.textSources), outcomes = random.shuffle(base.input.outcomes).map(outcome => outcome.copy(recoveries = random.shuffle(outcome.recoveries))))
          val ready = _right(CozyLogicalUiReadiness.candidate(kernel, projection, semantics, input))
          val scenario = _scenario.copy(coverage = new scala.util.Random(coverageseed).shuffle(_scenario.coverage))
          val current = _right(renderCandidate(ready, scenario))
          current.html == baseline.html && current.reviewHtml == baseline.reviewHtml && current.compareHtml == baseline.compareHtml &&
            current.receipt == baseline.receipt && current.receiptJson == baseline.receiptJson &&
            current.reviewReceiptJson == baseline.reviewReceiptJson && current.diagnostics == baseline.diagnostics
        }

        When("ScalaCheck constructs at least 30 independent complete target permutations")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), property)

        Then("input declaration order and coverage order do not alter any target observable")
        result.passed shouldBe true
        result.succeeded should be >= 30
      }



      "round-trip script-breaking, Unicode, control and quote source text through a complete inert payload" in {
        Given("opaque source labels, attribution, field text and record text containing executable-looking markup")
        val opaque = "  日本語 \"<&> </script><script>source()</script>\n\t\u0001\u2028\u2029  "
        val base = _ready
        val input: ReadinessInput = base.input.copy(textSources = base.input.textSources.map(text =>
          text.copy(sourceIdentity = text.sourceIdentity + "\"<&>日本語", defaultText = opaque)),
          fields = Vector(base.input.fields.head.copy(initialValue = opaque)))
        val revised = _admit(base, input)
        val scenario = _scenario.copy(normal = _scenario.normal.copy(initialOrder = _scenario.normal.initialOrder.copy(label = opaque, amount = opaque),
          successfulOrder = _scenario.normal.successfulOrder.copy(label = opaque, amount = opaque)))

        When("the target emits one safe data block and a fixed separate interpreter")
        val preview = _right(renderCandidate(revised, scenario))
        val embedded = _embedded(preview.html)
        val baseline = _right(renderCandidate(base, _scenario))

        Then("all supplied data round-trips without changing executable code or creating source markup")
        embedded shouldBe Json.parse(preview.receiptJson)
        val fields = (embedded \ "reviewReceipt" \ "sourceInputs" \ "readinessContent" \ "readiness" \ "fields").as[Vector[JsObject]]
        (fields.head \ "initialValue").as[String] shouldBe opaque
        val texts = (embedded \ "reviewReceipt" \ "sourceInputs" \ "readinessContent" \ "readiness" \ "textSources").as[Vector[JsObject]]
        texts.map(text => (text \ "defaultText").as[String]).distinct shouldBe Vector(opaque)
        (embedded \ "reviewReceipt" \ "scenario" \ "normal" \ "initialOrder" \ "label").as[String] shouldBe opaque
        (embedded \ "reviewReceipt" \ "scenario" \ "normal" \ "successfulOrder" \ "amount").as[String] shouldBe opaque
        val raw = _data_text(preview.html)
        raw should not include ("<")
        raw should not include (">")
        raw should not include ("&")
        raw should not include ("\u2028")
        raw should not include ("\u2029")
        _fixed_script(preview.html) shouldBe _fixed_script(baseline.html)
        "<script".r.findAllIn(preview.html).size shouldBe 2
        preview.html should include ("&lt;/script&gt;&lt;script&gt;source()&lt;/script&gt;")
        preview.html should not include ("<script>source()</script>")
        preview.reviewHtml should not include ("<script")
      }
    }

    "five-file installation" which {
      "retain the production candidate bundle for the next actual browser Slice" in {
        Given("the actual complete candidate target and distinct canonical production site/evidence directories")
        val preview = _right(renderCandidate(_ready, _scenario))
        val parent = Paths.get("target", "phase501-readiness-preview").toAbsolutePath.normalize()
        val site = Files.createDirectories(parent.resolve("site")).toRealPath()
        val evidence = Files.createDirectories(parent.resolve("evidence")).toRealPath()

        When("the production writer installs exactly its five direct output files")
        val result = write(preview, site, evidence)

        Then("the exact generated bytes are retained for actual browser observation, with no browser acceptance claimed here")
        result shouldBe Right(())
        _content(site.resolve("index.html")) shouldBe preview.html
        _content(site.resolve("review.html")) shouldBe preview.reviewHtml
        _content(site.resolve("compare.html")) shouldBe preview.compareHtml
        _content(evidence.resolve("readiness-review-receipt.json")) shouldBe preview.reviewReceiptJson
        _content(evidence.resolve("readiness-web-receipt.json")) shouldBe preview.receiptJson
      }

      "replace only exact direct outputs, preserve unrelated files and remove owned temporary residue" in {
        Given("fresh owned directories containing old output bytes and unrelated sentinels")
        _with_dirs { (site, evidence, _) =>
          val preview = _right(renderCandidate(_ready, _scenario))
          val targets = _targets(site, evidence)
          targets.foreach(path => Files.write(path, "old".getBytes(StandardCharsets.UTF_8)))
          val sitesentinel = site.resolve("keep.txt")
          val evidencesentinel = evidence.resolve("keep.json")
          Files.write(sitesentinel, "preserve-site".getBytes(StandardCharsets.UTF_8))
          Files.write(evidencesentinel, "preserve-evidence".getBytes(StandardCharsets.UTF_8))

          When("the production writer installs and replaces the same exact candidate bundle twice")
          val first = write(preview, site, evidence)
          val second = write(preview, site, evidence)

          Then("only owned bytes change and no temporary artifact remains")
          first shouldBe Right(())
          second shouldBe Right(())
          targets.map(_content) shouldBe Vector(preview.html, preview.reviewHtml, preview.compareHtml, preview.reviewReceiptJson, preview.receiptJson)
          _content(sitesentinel) shouldBe "preserve-site"
          _content(evidencesentinel) shouldBe "preserve-evidence"
          _names(site) shouldBe Set("index.html", "review.html", "compare.html", "keep.txt")
          _names(evidence) shouldBe Set("readiness-review-receipt.json", "readiness-web-receipt.json", "keep.json")
        }
      }

      "reject null previews and invalid parents before mutating any existing output" in {
        Given("fresh owned output sentinels and null, relative, missing, shared, traversing and symlink-crossing parents")
        _with_dirs { (site, evidence, owned) =>
          val preview = _right(renderCandidate(_ready, _scenario))
          val targets = _targets(site, evidence)
          targets.foreach(path => Files.write(path, "sentinel".getBytes(StandardCharsets.UTF_8)))
          val link = owned.resolve("site-link")
          Files.createSymbolicLink(link, site)
          val variants = Vector(
            ((null: ReadinessWebPreview), site, evidence, "preview"),
            (preview, (null: Path), evidence, "output.siteParent"),
            (preview, site, (null: Path), "output.receiptParent"),
            (preview, Paths.get("target"), evidence, "output.siteParent"),
            (preview, site, owned.resolve("absent"), "output.receiptParent"),
            (preview, site, site, "output.receiptParent"),
            (preview, site.resolve("..").resolve("site"), evidence, "output.siteParent"),
            (preview, link, evidence, "output.siteParent"),
            (preview, link.resolve("child"), evidence, "output.siteParent"))

          When("each rejected write is preflighted before output installation")
          val errors = variants.map { case (value, siteparent, receiptparent, _) => _left(write(value, siteparent, receiptparent)) }

          Then("structured errors name the failing boundary and preserve all prior destination bytes")
          errors.map(_.path) shouldBe variants.map(_._4)
          errors.take(1).map(_.code).distinct shouldBe Vector("LUI501_WEB_CONTRACT_INVALID")
          errors.drop(1).map(_.code).distinct shouldBe Vector("LUI501_WEB_OUTPUT_INVALID")
          targets.map(_content).distinct shouldBe Vector("sentinel")
          _names(site) shouldBe Set("index.html", "review.html", "compare.html")
          _names(evidence) shouldBe Set("readiness-review-receipt.json", "readiness-web-receipt.json")
        }
      }

      "preflight every output as a symlink or directory before earlier files or outside sentinels can change" in {
        Given("each of the five exact targets in turn is an invalid symlink or directory")
        val preview = _right(renderCandidate(_ready, _scenario))
        val cases = _outputs.indices.flatMap(index => Vector(index -> true, index -> false))

        cases.foreach { case (index, symlink) =>
          _with_dirs { (site, evidence, owned) =>
            Given("fresh owned sentinels and the invalid target " + _outputs(index))
            val targets = _targets(site, evidence)
            val outside = owned.resolve("external-sentinel.txt")
            Files.write(outside, "external".getBytes(StandardCharsets.UTF_8))
            targets.zipWithIndex.filterNot(_._2 == index).foreach { case (path, _) => Files.write(path, "old".getBytes(StandardCharsets.UTF_8)) }
            if (symlink) Files.createSymbolicLink(targets(index), outside) else Files.createDirectory(targets(index))

            When("the production writer preflights the complete five-target destination set")
            val error = _left(write(preview, site, evidence))

            Then("this target is rejected before prior bytes, the external sentinel or any file inventory changes")
            error.code shouldBe "LUI501_WEB_OUTPUT_INVALID"
            error.path shouldBe "output." + _outputs(index)
            targets.zipWithIndex.filterNot(_._2 == index).map(value => _content(value._1)).distinct shouldBe Vector("old")
            _content(outside) shouldBe "external"
            _names(site) shouldBe Set("index.html", "review.html", "compare.html")
            _names(evidence) shouldBe Set("readiness-review-receipt.json", "readiness-web-receipt.json")
          }
        }
      }
    }
  }

  private val _outputs = Vector("index.html", "review.html", "compare.html", "readiness-review-receipt.json", "readiness-web-receipt.json")
  private def _ready: TargetReadyCandidate = _right(CozyLogicalUiSalesOrderFixtures.readyCandidate())
  private def _scenario: ReadinessScenario = CozyLogicalUiSalesOrderFixtures.readinessWebScenario()
  private def _admit(base: TargetReadyCandidate, input: ReadinessInput): TargetReadyCandidate =
    _right(CozyLogicalUiReadiness.candidate(base.kernelCandidate, base.projection, base.semantics, input))
  private def _data_text(html: String): String = {
    val marker = "<script id=\"readiness-data\" type=\"application/json\">"
    val start = html.indexOf(marker) + marker.length
    html.substring(start, html.indexOf("</script>", start))
  }
  private def _embedded(html: String): JsValue = Json.parse(_data_text(html))
  private def _fixed_script(html: String): String = {
    val start = html.indexOf("</script><script>") + "</script><script>".length
    html.substring(start, html.indexOf("</script>", start))
  }
  private def _targets(site: Path, evidence: Path): Vector[Path] = _outputs.take(3).map(site.resolve(_)) ++ _outputs.drop(3).map(evidence.resolve(_))
  private def _content(path: Path): String = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
  private def _names(parent: Path): Set[String] = {
    val stream = Files.list(parent)
    try stream.iterator().asScala.map(_.getFileName.toString).toSet finally stream.close()
  }
  private def _with_dirs[A](f: (Path, Path, Path) => A): A = {
    val target = Files.createDirectories(Paths.get("target").toAbsolutePath.normalize()).toRealPath()
    val owned = Files.createTempDirectory(target, "readiness-web-spec-")
    val site = Files.createDirectory(owned.resolve("site"))
    val evidence = Files.createDirectory(owned.resolve("evidence"))
    try f(site, evidence, owned)
    finally {
      val stream = Files.walk(owned)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(path => Files.deleteIfExists(path))
      finally stream.close()
    }
  }
  private def _right[A](value: Either[LogicalUiError, A]): A = value match {
    case Right(result) => result
    case Left(error) => fail(error.toString)
  }
  private def _left[A](value: Either[LogicalUiError, A]): LogicalUiError = value match {
    case Left(error) => error
    case Right(_) => fail("expected structured rejection")
  }
}
