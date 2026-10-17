package cozy.ui

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import com.fasterxml.jackson.core.{JsonFactory, JsonParser, JsonToken}
import cozy.ui.CozyLogicalUi._
import cozy.ui.CozyLogicalUiCompactWeb._
import cozy.ui.CozyLogicalUiReview.ReviewDiagnostic
import cozy.ui.CozyLogicalUiSalesOrderFixtures._
import cozy.ui.CozyLogicalUiSemantics._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 8, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyLogicalUiCompactWebSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Compact candidate Web projection" should {
    "bind declared inputs without accepting or executing them" which {
      "align the app, structural review, complete receipt and opaque records" in {
        Given("the genuine shared SalesOrder tuple and its translated compact success scenario")
        val tuple = _right(CozyLogicalUiSalesOrderFixtures.candidate())
        val scenario = compactWebScenario
        val original = _right(CozyLogicalUiReview.renderCandidate(tuple._1, tuple._2, tuple._3))

        When("the compact target consumes the exact candidate, projection, semantics and declared scenario")
        val preview = _right(render(tuple._1, tuple._2, tuple._3, scenario))
        val json = _json(preview.receiptJson)
        val embedded = _embedded(preview.html)

        Then("both pages and machine data share the full source tuple, bindings and declaration with candidate authority")
        preview.receipt shouldBe CompactWebReceipt(
          "cozy.logical-ui-compact-web.v1", 1, "cozy.logical-ui-compact-web.renderer.v1",
          original.receipt, scenario, tuple._1.input.componentBindings, operationBinding,
          stateMachineBinding, powertypeBinding, "awaiting-confirmation", "compact", "navigate-to-detail", true,
          Vector("index.html", "review.html", "compact-preview-receipt.json"))
        embedded shouldBe json
        json("candidateReview") shouldBe _json(_candidate_json(original.receipt))
        val declared = json("scenario").asInstanceOf[Map[String, Any]]
        declared("fixtureId") shouldBe compactSuccess.fixtureId
        declared("coverageId") shouldBe compactSuccess.coverageId
        declared("initialOrder") shouldBe _order_value(scenario.initialOrder)
        declared("successfulOrder") shouldBe _order_value(scenario.successfulOrder)
        declared("successUiState") shouldBe "succeeded"
        val flow = declared("flow").asInstanceOf[Map[String, Any]]
        flow shouldBe Map("step" -> Map("uiUseCase" -> Map("layer" -> "ui", "id" -> uiUseCase.id),
          "stepId" -> "confirm", "path" -> "normal"), "listScreenId" -> "order-list", "listEntryInteractionId" -> "entry",
          "navigationInteractionId" -> "to-detail", "detailScreenId" -> "order-detail", "selectionInteractionId" -> "select",
          "commandInteractionId" -> "invoke", "observationInteractionId" -> "observe")
        json("componentBindings") shouldBe tuple._1.input.componentBindings.map(_binding_value)
        json("operationBinding") shouldBe _binding_value(operationBinding)
        json("stateMachineBinding") shouldBe _binding_value(stateMachineBinding)
        json("powertypeBinding") shouldBe _binding_value(powertypeBinding)
        json("outputs") shouldBe Vector("index.html", "review.html", "compact-preview-receipt.json")
        preview.htmlBytes shouldBe preview.html.getBytes(StandardCharsets.UTF_8).toVector
        preview.reviewHtmlBytes shouldBe preview.reviewHtml.getBytes(StandardCharsets.UTF_8).toVector
        preview.diagnostics shouldBe original.diagnostics
        preview.reviewHtml shouldBe original.html.replace("</main>",
          preview.reviewHtml.substring(preview.reviewHtml.indexOf("<section id=\"compact-scenario\""),
            preview.reviewHtml.indexOf("</main>")) + "</main>")
        Vector("Candidate preview", "candidate (not accepted)", "Compact simulated scenario",
          "workflowId=sales-order-workflow; stateId=awaiting", "initial-domain-state", "result-domain-state",
          "success-ui-state", "draft", "confirmed", "succeeded", scenario.fixtureId, scenario.coverageId).foreach { text =>
          preview.reviewHtml should include (text)
        }
        Vector(operationBinding, stateMachineBinding, powertypeBinding).foreach { binding =>
          preview.html should include (binding.component.canonicalIdentity + "#" + binding.exportId)
          preview.reviewHtml should include (binding.component.canonicalIdentity + "#" + binding.exportId)
        }
        scenario.initialOrder shouldBe OrderRecord(compactSuccess.initialOrder.orderId, compactSuccess.initialOrder.label,
          compactSuccess.initialOrder.amount, compactSuccess.initialOrder.domainState)
        scenario.successfulOrder shouldBe OrderRecord(compactSuccess.successfulOrder.orderId, compactSuccess.successfulOrder.label,
          compactSuccess.successfulOrder.amount, compactSuccess.successfulOrder.domainState)
        preview.reviewHtml should not include ("<script")
        preview.reviewHtml should not include ("<form")
        preview.receiptJson should not include ("acceptedIdentity")
      }
    }

    "fail closed on tuple and declaration mismatch" which {
      "reuse precise candidate tuple errors for foreign and missing authorities" in {
        Given("two admitted candidate tuples with different Business UseCases and one declared compact scenario")
        val tuple = _right(CozyLogicalUiSalesOrderFixtures.candidate())
        val business = businessUseCase.copy(id = "foreign-business")
        val foreign = _right(CozyLogicalUi.candidate(candidateInput.copy(useCases = useCases.copy(business = business,
          realizations = Vector(UseCaseRealization(business, systemUseCase), UseCaseRealization(systemUseCase, uiUseCase))))))
        val projection = _right(CozyLogicalUi.project(foreign, projectionInput(foreign)))
        val semantics = _right(CozyLogicalUiSemantics.project(projection, semanticsInput(projection)))
        val inputs = Vector((null, tuple._2, tuple._3), (tuple._1, null, tuple._3), (tuple._1, tuple._2, null),
          (tuple._1, null, null))

        When("each foreign or null tuple is rendered by both the source review and compact target")
        val results = inputs.map { case (candidate, projected, semantic) =>
          render(candidate, projected, semantic, compactWebScenario) -> CozyLogicalUiReview.renderCandidate(candidate, projected, semantic)
        }

        Then("the compact target returns exactly the existing tuple error instead of dereferencing or accepting it")
        results.foreach { case (compact, source) => _left(compact) shouldBe _left(source) }
      }

      "reject null nested values, untrimmed identifiers and changed records at precise scenario paths" in {
        Given("a valid source tuple and separately malformed declarations of its flow, orders and UI state")
        val tuple = _right(CozyLogicalUiSalesOrderFixtures.candidate())
        val scenario = compactWebScenario
        val flow = scenario.flow
        val initial = scenario.initialOrder
        val result = scenario.successfulOrder
        val invalid = Vector(
          (null, "scenario"), scenario.copy(flow = null) -> "scenario.flow",
          scenario.copy(fixtureId = null) -> "scenario.fixtureId", scenario.copy(coverageId = " ") -> "scenario.coverageId",
          scenario.copy(flow = flow.copy(step = null)) -> "scenario.flow.step",
          scenario.copy(flow = flow.copy(step = flow.step.copy(uiUseCase = null))) -> "scenario.flow.step.uiUseCase",
          scenario.copy(flow = flow.copy(step = flow.step.copy(path = null))) -> "scenario.flow.step.path",
          scenario.copy(flow = flow.copy(step = flow.step.copy(stepId = " confirm"))) -> "scenario.flow.step.stepId",
          scenario.copy(flow = flow.copy(listScreenId = null)) -> "scenario.flow.listScreenId",
          scenario.copy(flow = flow.copy(detailScreenId = "")) -> "scenario.flow.detailScreenId",
          scenario.copy(flow = flow.copy(listEntryInteractionId = " entry")) -> "scenario.flow.listEntryInteractionId",
          scenario.copy(flow = flow.copy(navigationInteractionId = null)) -> "scenario.flow.navigationInteractionId",
          scenario.copy(flow = flow.copy(selectionInteractionId = "")) -> "scenario.flow.selectionInteractionId",
          scenario.copy(flow = flow.copy(commandInteractionId = "invoke ")) -> "scenario.flow.commandInteractionId",
          scenario.copy(flow = flow.copy(observationInteractionId = null)) -> "scenario.flow.observationInteractionId",
          scenario.copy(initialOrder = null) -> "scenario.initialOrder", scenario.copy(successfulOrder = null) -> "scenario.successfulOrder",
          scenario.copy(initialOrder = initial.copy(orderId = "")) -> "scenario.initialOrder.orderId",
          scenario.copy(initialOrder = initial.copy(label = null)) -> "scenario.initialOrder.label",
          scenario.copy(initialOrder = initial.copy(amount = "")) -> "scenario.initialOrder.amount",
          scenario.copy(initialOrder = initial.copy(domainState = null)) -> "scenario.initialOrder.domainState",
          scenario.copy(successfulOrder = result.copy(domainState = null)) -> "scenario.successfulOrder.domainState",
          scenario.copy(successfulOrder = result.copy(orderId = "SO-002")) -> "scenario.successfulOrder.orderId",
          scenario.copy(successfulOrder = result.copy(label = "Different")) -> "scenario.successfulOrder.label",
          scenario.copy(successfulOrder = result.copy(amount = "200")) -> "scenario.successfulOrder.amount",
          scenario.copy(successUiState = null) -> "scenario.successUiState")

        When("each malformed declaration is supplied to the compact renderer")
        val failures = invalid.map { case (value, path) => render(tuple._1, tuple._2, tuple._3, value) -> path }

        Then("every invalid declaration fails without nested access at its relevant contract path")
        failures.foreach { case (value, path) => _failure(value, "LUI50_WEB_CONTRACT_INVALID", path) }
      }

      "reject wrong flow kinds, selection mapped to an alternative step, non-normal steps and domain or UI outcomes" in {
        Given("factory-admitted tuples with selection on the normal or alternative step and declarations that disagree with the normal flow")
        val tuple = _right(CozyLogicalUiSalesOrderFixtures.candidate())
        val scenario = compactWebScenario
        val flow = scenario.flow
        val invalid = Vector(
          scenario.copy(flow = flow.copy(listScreenId = "missing")) -> "scenario.flow.listScreenId",
          scenario.copy(flow = flow.copy(detailScreenId = flow.listScreenId)) -> "scenario.flow.detailScreenId",
          scenario.copy(flow = flow.copy(listEntryInteractionId = "to-detail")) -> "scenario.flow.listEntryInteractionId",
          scenario.copy(flow = flow.copy(navigationInteractionId = "entry")) -> "scenario.flow.navigationInteractionId",
          scenario.copy(flow = flow.copy(selectionInteractionId = "invoke")) -> "scenario.flow.selectionInteractionId",
          scenario.copy(flow = flow.copy(commandInteractionId = "select")) -> "scenario.flow.commandInteractionId",
          scenario.copy(flow = flow.copy(observationInteractionId = "feedback")) -> "scenario.flow.observationInteractionId",
          scenario.copy(flow = flow.copy(step = UiUseCaseStep(uiUseCase, "enter", AlternativePath))) -> "scenario.flow.step",
          scenario.copy(initialOrder = scenario.initialOrder.copy(domainState = scenario.successfulOrder.domainState)) -> "scenario.initialOrder.domainState",
          scenario.copy(successfulOrder = scenario.successfulOrder.copy(domainState = scenario.initialOrder.domainState)) -> "scenario.successfulOrder.domainState",
          scenario.copy(initialOrder = scenario.initialOrder.copy(domainState = DomainStateReference(operationBinding, "draft"))) -> "scenario.initialOrder.domainState",
          scenario.copy(successUiState = FailedInteractionState) -> "scenario.successUiState")
        val unmappedinput = projectionInput(tuple._1).copy(mappings = tuple._2.input.mappings.filterNot(value =>
          value.stepId == "confirm" && value.screenId == "order-detail" && value.interactionId == "select") :+
          ScreenInteractionMapping(uiUseCase, "enter", "order-detail", "select"))
        val unmapped = _right(CozyLogicalUi.project(tuple._1, unmappedinput))
        val unmappedsemantics = _right(CozyLogicalUiSemantics.project(unmapped, semanticsInput(unmapped)))

        When("the declarations or a separately admitted projection with selection mapped only to the alternative step are rendered")
        val failures = invalid.map { case (value, path) => render(tuple._1, tuple._2, tuple._3, value) -> path } :+
          (render(tuple._1, unmapped, unmappedsemantics, scenario) -> "scenario.flow.selectionInteractionId")

        Then("the target binds only the actual normal flow and its declared separate source outcomes")
        failures.foreach { case (value, path) => _failure(value, "LUI50_WEB_CONTRACT_INVALID", path) }
      }

      "require source typed Purpose, Display, Powertype variant, navigation target and command action" in {
        Given("separately factory-admitted tuples with altered semantic meanings or navigation and a declaration requiring a Powertype variant")
        val tuple = _right(CozyLogicalUiSalesOrderFixtures.candidate())
        val input = semanticsInput(tuple._2)
        val wrongpurpose = _right(CozyLogicalUiSemantics.project(tuple._2, input.copy(screens = input.screens.map(value =>
          if (value.screenId == "order-detail") value.copy(purpose = InspectPurpose) else value))))
        val wrongdisplay = _right(CozyLogicalUiSemantics.project(tuple._2, input.copy(screens = input.screens.map(value =>
          if (value.screenId == "order-list") value.copy(regions = value.regions.map(_.copy(display = DetailDisplay))) else value))))
        val noaction = _right(CozyLogicalUiSemantics.project(tuple._2, input.copy(transitionActions = Vector.empty)))
        val projectedinput = projectionInput(tuple._1)
        val navigation = _right(CozyLogicalUi.project(tuple._1, projectedinput.copy(screens = projectedinput.screens.map(screen =>
          if (screen.id == "order-list") screen.copy(interactions = screen.interactions.map(value =>
            if (value.id == "to-detail") value.copy(navigation = Some(NavigationEndpoint("to-detail", "order-list"))) else value)) else screen))))
        val navigationsemantics = _right(CozyLogicalUiSemantics.project(navigation, semanticsInput(navigation)))

        When("the source factory interprets a missing variant and each admitted source is consumed with the unchanged compact declaration")
        val novariant = CozyLogicalUiSemantics.project(tuple._2, input.copy(componentBindings = input.componentBindings.map(value =>
          if (value.role == PowertypeRole) value.copy(powertypeVariantId = None) else value)))
        val failures = Vector(
          render(tuple._1, tuple._2, wrongpurpose, compactWebScenario) -> "scenario.flow.detailScreenId",
          render(tuple._1, tuple._2, wrongdisplay, compactWebScenario) -> "scenario.flow.listScreenId",
          render(tuple._1, tuple._2, noaction, compactWebScenario) -> "scenario.flow.commandInteractionId",
          render(tuple._1, navigation, navigationsemantics, compactWebScenario) -> "scenario.flow.navigationInteractionId")

        Then("the source factory requires a Powertype variant and the target requires typed semantics and matching action from independently valid tuples")
        _failure(novariant, "LUI43_SEMANTICS_COMPONENT_BINDING_INVALID", "semantics.componentBindings[8].powertypeVariantId")
        failures.foreach { case (value, path) => _failure(value, "LUI50_WEB_CONTRACT_INVALID", path) }
      }
    }

    "verify the complete current projection" which {


      "preserve all observables across at least twenty normalized reordered constructions" in {
        Given("a canonical compact preview and logically equivalent reordered source inputs")
        val tuple = _right(CozyLogicalUiSalesOrderFixtures.candidate())
        val expected = _right(render(tuple._1, tuple._2, tuple._3, compactWebScenario))

        When("ScalaCheck repeatedly reconstructs the candidate, screen and semantic factories in permuted order")
        val property = Prop.forAll(Gen.oneOf(true, false)) { reversed =>
          val reordered = _right(CozyLogicalUiSalesOrderFixtures.candidate(reversed))
          val actual = _right(render(reordered._1, reordered._2, reordered._3, compactWebScenario))
          actual.html == expected.html && actual.reviewHtml == expected.reviewHtml && actual.receipt == expected.receipt &&
            actual.receiptJson == expected.receiptJson && actual.diagnostics == expected.diagnostics
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)

        Then("normalization makes both pages, the full receipt, JSON and diagnostics exactly equal")
        result.passed shouldBe true
      }

      "round-trip Unicode and closing-script text as opaque inert JSON and escaped presentation" in {
        Given("equal declared labels containing Unicode, quotes, angle brackets, ampersand and script closing text")
        val tuple = _right(CozyLogicalUiSalesOrderFixtures.candidate())
        val label = "注文 😀 <order> & \"confirm\" 'now' </script><script>alert(1)</script>\u2028\u2029"
        val amount = "  opaque & <amount>  "
        val scenario = compactWebScenario.copy(initialOrder = compactWebScenario.initialOrder.copy(label = label, amount = amount),
          successfulOrder = compactWebScenario.successfulOrder.copy(label = label, amount = amount))

        When("the target serializes and renders the complete declaration")
        val preview = _right(render(tuple._1, tuple._2, tuple._3, scenario))
        val json = _json(preview.receiptJson)
        val embedded = _embedded(preview.html)

        Then("parsing either JSON preserves exact opaque records and presentation cannot terminate the inert element")
        embedded shouldBe json
        val declared = embedded("scenario").asInstanceOf[Map[String, Any]]
        declared("initialOrder") shouldBe _order_value(scenario.initialOrder)
        declared("successfulOrder") shouldBe _order_value(scenario.successfulOrder)
        preview.receipt.scenario shouldBe scenario
        Vector(preview.html, preview.reviewHtml).foreach { html =>
          html should include ("注文 😀 &lt;order&gt; &amp; &quot;confirm&quot; &#39;now&#39; &lt;/script&gt;&lt;script&gt;alert(1)&lt;/script&gt;")
          html should not include (label)
        }
        val start = preview.html.indexOf("<script id=\"compact-data\" type=\"application/json\">")
        val data = preview.html.substring(start + "<script id=\"compact-data\" type=\"application/json\">".length,
          preview.html.indexOf("</script>", start))
        Vector("<", ">", "&", "\u2028", "\u2029").foreach(text => data should not include (text))
      }
    }

    "install only current preflighted outputs" which {
      "retain the exact production SalesOrder bundle for actual compact browser validation" in {
        Given("the genuine compact preview and the two bounded generation directories")
        val tuple = _right(CozyLogicalUiSalesOrderFixtures.candidate())
        val preview = _right(render(tuple._1, tuple._2, tuple._3, compactWebScenario))
        val root = Paths.get(sys.props("user.dir")).toRealPath().resolve("target/phase50-compact-preview")
        val site = Files.createDirectories(root.resolve("site"))
        val evidence = Files.createDirectories(root.resolve("evidence"))

        When("the production writer installs the app, structural review and declared receipt")
        val result = write(preview, site, evidence)

        Then("the retained files have exact bytes and the complete receipt binds the browser's input declaration")
        result shouldBe Right(())
        Files.readAllBytes(site.resolve("index.html")).toVector shouldBe preview.htmlBytes
        Files.readAllBytes(site.resolve("review.html")).toVector shouldBe preview.reviewHtmlBytes
        Files.readAllBytes(evidence.resolve("compact-preview-receipt.json")).toVector shouldBe preview.receiptJson.getBytes(StandardCharsets.UTF_8).toVector
        _embedded(new String(Files.readAllBytes(site.resolve("index.html")), StandardCharsets.UTF_8)) shouldBe _json(preview.receiptJson)
        preview.receipt.scenario shouldBe compactWebScenario
      }

      "replace exact direct files and preserve unrelated files without temporary residue" in {
        _with_temp_dir { root =>
          Given("current generated bytes, existing direct outputs and unrelated files in each parent")
          val tuple = _right(CozyLogicalUiSalesOrderFixtures.candidate())
          val preview = _right(render(tuple._1, tuple._2, tuple._3, compactWebScenario))
          val site = Files.createDirectory(root.resolve("site"))
          val evidence = Files.createDirectory(root.resolve("evidence"))
          Vector(site.resolve("index.html"), site.resolve("review.html"), evidence.resolve("compact-preview-receipt.json"),
            site.resolve("unrelated.txt"), evidence.resolve("unrelated.txt")).foreach(path => Files.write(path, _sentinel_bytes))

          When("the production writer installs all current outputs")
          val result = write(preview, site, evidence)

          Then("only the three exact files change and owned temporary files are removed")
          result shouldBe Right(())
          Files.readAllBytes(site.resolve("index.html")).toVector shouldBe preview.htmlBytes
          Files.readAllBytes(site.resolve("review.html")).toVector shouldBe preview.reviewHtmlBytes
          new String(Files.readAllBytes(evidence.resolve("compact-preview-receipt.json")), StandardCharsets.UTF_8) shouldBe preview.receiptJson
          Vector(site.resolve("unrelated.txt"), evidence.resolve("unrelated.txt")).foreach(path => Files.readAllBytes(path).toVector shouldBe _sentinel_bytes.toVector)
          _children(site) shouldBe Set("index.html", "review.html", "unrelated.txt")
          _children(evidence) shouldBe Set("compact-preview-receipt.json", "unrelated.txt")
        }
      }

      "preserve prior bytes on invalid parent paths before writing" in {
        _with_temp_dir { root =>
          Given("prior app, structural and receipt bytes plus stale observables and invalid or redirected parents")
          val tuple = _right(CozyLogicalUiSalesOrderFixtures.candidate())
          val preview = _right(render(tuple._1, tuple._2, tuple._3, compactWebScenario))
          val site = Files.createDirectory(root.resolve("site"))
          val evidence = Files.createDirectory(root.resolve("evidence"))
          val external = Files.createDirectory(root.resolve("external"))
          val nested = Files.createDirectory(external.resolve("nested"))
          val link = Files.createSymbolicLink(root.resolve("linked"), external)
          val targets = Vector(site.resolve("index.html"), site.resolve("review.html"), evidence.resolve("compact-preview-receipt.json"),
            nested.resolve("index.html"), nested.resolve("review.html"))
          targets.foreach(path => Files.write(path, _sentinel_bytes))
          When("the writer receives missing, relative, same, traversing or symlink-crossing parents")
          val missing = write(null, site, evidence)

          val invalid = Vector(write(preview, null, evidence), write(preview, site, null),
            write(preview, Paths.get("target"), evidence), write(preview, root.resolve("missing"), evidence),
            write(preview, site, site), write(preview, site.resolve(".."), evidence),
            write(preview, link.resolve("nested"), evidence), write(preview, site, link.resolve("nested")),
            write(preview, site.resolve("index.html"), evidence))

          Then("currentness or output rejection preserves every prior byte and creates no temporary file")
          _left(missing).code shouldBe "LUI50_WEB_CONTRACT_INVALID"
          invalid.foreach(value => _left(value).code shouldBe "LUI50_WEB_OUTPUT_INVALID")
          targets.foreach(path => Files.readAllBytes(path).toVector shouldBe _sentinel_bytes.toVector)
          _children(site) shouldBe Set("index.html", "review.html")
          _children(evidence) shouldBe Set("compact-preview-receipt.json")
          _children(nested) shouldBe Set("index.html", "review.html")
        }
      }

      "preflight every target symlink and directory before installing any output" in {
        Vector("index.html", "review.html", "compact-preview-receipt.json").foreach { filename =>
          Vector(true, false).foreach { symlink =>
            _with_temp_dir { root =>
              Given(s"prior outputs with a ${if (symlink) "symlink" else "directory"} destination at $filename")
              val tuple = _right(CozyLogicalUiSalesOrderFixtures.candidate())
              val preview = _right(render(tuple._1, tuple._2, tuple._3, compactWebScenario))
              val site = Files.createDirectory(root.resolve("site"))
              val evidence = Files.createDirectory(root.resolve("evidence"))
              val targets = Vector(site.resolve("index.html"), site.resolve("review.html"), evidence.resolve("compact-preview-receipt.json"))
              val invalid = targets.find(_.getFileName.toString == filename).get
              val sentinel = root.resolve("external.txt")
              Files.write(sentinel, _sentinel_bytes)
              targets.filterNot(_ == invalid).foreach(path => Files.write(path, _sentinel_bytes))
              if (symlink) Files.createSymbolicLink(invalid, sentinel) else Files.createDirectory(invalid)

              When("the production writer preflights the complete three-file destination set")
              val result = write(preview, site, evidence)

              Then("it rejects the named target before modifying earlier outputs or external data")
              _failure(result, "LUI50_WEB_OUTPUT_INVALID", "output." + filename)
              targets.filterNot(_ == invalid).foreach(path => Files.readAllBytes(path).toVector shouldBe _sentinel_bytes.toVector)
              Files.readAllBytes(sentinel).toVector shouldBe _sentinel_bytes.toVector
              _children(site) shouldBe Set("index.html", "review.html")
              _children(evidence) shouldBe Set("compact-preview-receipt.json")
              Files.isSymbolicLink(invalid) shouldBe symlink
            }
          }
        }
      }
    }
  }

  private val _sentinel_bytes = "preserve existing bytes".getBytes(StandardCharsets.UTF_8)

  private def _binding_value(binding: ComponentBinding): Map[String, Any] = Map(
    "component" -> Map("namespace" -> binding.component.namespace, "id" -> binding.component.id,
      "version" -> binding.component.version, "canonicalIdentity" -> binding.component.canonicalIdentity), "exportId" -> binding.exportId)

  private def _order_value(order: OrderRecord): Map[String, Any] = Map("orderId" -> order.orderId, "label" -> order.label,
    "amount" -> order.amount, "domainState" -> Map("stateMachineBinding" -> _binding_value(order.domainState.stateMachineBinding),
      "stateId" -> order.domainState.stateId))

  private def _candidate_json(value: CozyLogicalUiReview.CandidateReviewReceipt): String = {
    val writer = new java.io.StringWriter()
    val generator = new JsonFactory().createGenerator(writer)
    try {
      generator.writeStartObject()
      generator.writeStringField("schema", value.schema)
      generator.writeNumberField("version", value.version)
      generator.writeStringField("rendererProfile", value.rendererProfile)
      generator.writeEndObject()
    } finally generator.close()
    writer.toString
  }

  private def _embedded(html: String): Map[String, Any] = {
    val marker = "<script id=\"compact-data\" type=\"application/json\">"
    val start = html.indexOf(marker) + marker.length
    _json(html.substring(start, html.indexOf("</script>", start)))
  }

  private def _json(text: String): Map[String, Any] = {
    val parser = new JsonFactory().createParser(text)
    try {
      parser.nextToken()
      val result = _json_value(parser).asInstanceOf[Map[String, Any]]
      parser.nextToken() shouldBe null
      result
    } finally parser.close()
  }

  private def _json_value(parser: JsonParser): Any = parser.currentToken() match {
    case JsonToken.START_OBJECT =>
      var fields = Map.empty[String, Any]
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        val name = parser.currentName()
        parser.nextToken()
        fields += name -> _json_value(parser)
      }
      fields
    case JsonToken.START_ARRAY =>
      val values = Vector.newBuilder[Any]
      while (parser.nextToken() != JsonToken.END_ARRAY) values += _json_value(parser)
      values.result()
    case JsonToken.VALUE_STRING => parser.getText
    case JsonToken.VALUE_NUMBER_INT => parser.getIntValue
    case JsonToken.VALUE_TRUE => true
    case JsonToken.VALUE_FALSE => false
    case other => fail("Unexpected receipt JSON token: " + other)
  }

  private def _with_temp_dir[A](body: Path => A): A = {
    val work = Paths.get(sys.props("user.dir")).toRealPath().resolve("target")
    Files.createDirectories(work)
    val root = Files.createTempDirectory(work, "cozy-compact-web-")
    try body(root)
    finally {
      val paths = Files.walk(root)
      try paths.iterator().asScala.toVector.reverse.foreach(path => Files.deleteIfExists(path))
      finally paths.close()
    }
  }

  private def _children(parent: Path): Set[String] = {
    val paths = Files.list(parent)
    try paths.iterator().asScala.map(_.getFileName.toString).toSet
    finally paths.close()
  }

  private def _failure[A](value: Either[LogicalUiError, A], code: String, path: String): Unit = {
    val error = _left(value)
    error.code shouldBe code
    error.path shouldBe path
    error.reason should not be empty
  }

  private def _right[A](value: Either[LogicalUiError, A]): A = value match {
    case Right(result) => result
    case Left(error) => fail(s"Expected compact result but received ${error.code}: ${error.path}: ${error.reason}")
  }

  private def _left[A](value: Either[LogicalUiError, A]): LogicalUiError = value match {
    case Left(error) => error
    case Right(_) => fail("Expected a closed compact projection failure")
  }
}
