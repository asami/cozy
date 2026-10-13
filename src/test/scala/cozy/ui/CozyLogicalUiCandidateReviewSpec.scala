package cozy.ui

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._

import cozy.ui.CozyLogicalUi._
import cozy.ui.CozyLogicalUiReview._
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
final class CozyLogicalUiCandidateReviewSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Logical UI candidate preview" should {
    "bind genuine candidate inputs" which {
      "render structural evidence with candidate status and its exact receipt tuple" in {
        Given("the shared SalesOrder candidate, screen projection, and semantic projection without an acceptance decision")
        val fixture = _right(CozyLogicalUiSalesOrderFixtures.candidate())

        When("the declared tuple is rendered as a candidate preview")
        val reviewvalue = _right(renderCandidate(fixture._1, fixture._2, fixture._3))

        Then("the read-only page exposes source structure and bindings with candidate authority only")
        reviewvalue.receipt shouldBe CandidateReviewReceipt(
          "cozy.logical-ui-candidate-review.v1", 1, "cozy.logical-ui-candidate-review.renderer.v1"
        )
        reviewvalue.htmlBytes shouldBe reviewvalue.html.getBytes(StandardCharsets.UTF_8).toVector
        reviewvalue.html should include ("Candidate preview")
        reviewvalue.html should include ("candidate (not accepted)")
        Vector(reviewvalue.receipt.schema, reviewvalue.receipt.rendererProfile).foreach { value =>
          reviewvalue.html should include (value)
        }
        Vector("Business -&gt; System -&gt; UI", "Catalog membership", "Step-to-screen interaction coverage",
          "Navigation and reachability", "Logical Screens", "Component semantic bindings",
          "Constraints, validation authority", "StateMachine transition admission evidence",
          "Review diagnostics", "order-list", "order-detail", "confirm", "system-only (no screen mapping)",
          "workflowId=sales-order-workflow; stateId=awaiting", "draft -&gt; confirmed").foreach { value =>
          reviewvalue.html should include (value)
        }
        allBindings.foreach { binding =>
          reviewvalue.html should include (binding.component.canonicalIdentity + "#" + binding.exportId)
        }
        Vector("Accepted identity", "accepted-sales-order", "Acceptance decision", "<script", "<form", "http://", "https://").foreach { value =>
          reviewvalue.html should not include (value)
        }
        reviewvalue.html should include ("<style>")
        reviewvalue.diagnostics shouldBe empty
      }

      "reject missing candidate inputs with precise input errors" in {
        Given("the shared SalesOrder tuple")
        val fixture = _right(CozyLogicalUiSalesOrderFixtures.candidate())
        When("a required input is absent")
        val failures = Vector(renderCandidate(null, fixture._2, fixture._3) -> "candidate",
          renderCandidate(fixture._1, null, fixture._3) -> "projection",
          renderCandidate(fixture._1, fixture._2, null) -> "semantics")
        Then("each missing input reports its actual path")
        failures.foreach { case (result, path) =>
          val error = _left(result)
          error.code shouldBe "LUI50_CANDIDATE_REVIEW_INPUT_INVALID"
          error.path shouldBe path
        }
      }
    }

    "deterministic rendering" which {


      "produce equal HTML, receipt, and diagnostics for at least twenty reordered constructions" in {
        Given("one canonical SalesOrder preview and equivalent permutations of candidate, screen, and semantic inputs")
        val fixture = _right(CozyLogicalUiSalesOrderFixtures.candidate())
        val expected = _right(renderCandidate(fixture._1, fixture._2, fixture._3))

        When("ScalaCheck repeatedly admits and renders reordered constructions")
        val property = Prop.forAll(Gen.oneOf(true, false)) { reversed =>
          val reordered = _right(CozyLogicalUiSalesOrderFixtures.candidate(reversed))
          val actual = _right(renderCandidate(reordered._1, reordered._2, reordered._3))
          actual.html == expected.html && actual.receipt == expected.receipt && actual.diagnostics == expected.diagnostics
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)

        Then("equivalent logical values have exactly equal preview observables")
        result.passed shouldBe true
      }

      "escape factory-admitted purpose text without changing its logical meaning" in {
        Given("a screen purpose containing angle brackets, ampersand, and both quote characters")
        val fixture = _right(CozyLogicalUiSalesOrderFixtures.candidate())
        val purpose = "Inspect <order> & \"confirm\" 'now'"
        val input = projectionInput(fixture._1)
        val projection = _right(CozyLogicalUi.project(fixture._1, input.copy(screens = input.screens.map(screen =>
          if (screen.id == "order-detail") screen.copy(primaryPurpose = purpose) else screen
        ))))
        val semantics = _right(CozyLogicalUiSemantics.project(projection, semanticsInput(projection)))

        When("the purpose-bearing tuple is rendered")
        val reviewvalue = _right(renderCandidate(fixture._1, projection, semantics))

        Then("the HTML escapes the admitted text and the projection retains the exact purpose")
        projection.input.screens.find(_.id == "order-detail").get.primaryPurpose shouldBe purpose
        reviewvalue.html should include ("Inspect &lt;order&gt; &amp; &quot;confirm&quot; &#39;now&#39;")
        reviewvalue.html should not include (purpose)
      }
    }

    "write only current direct HTML output" which {
      "install exact UTF-8 HTML without a receipt side file" in {
        _with_temp_dir { root =>
          Given("a current candidate review and an existing direct output parent")
          val fixture = _right(CozyLogicalUiSalesOrderFixtures.candidate())
          val reviewvalue = _right(renderCandidate(fixture._1, fixture._2, fixture._3))
          val parent = Files.createDirectory(root.resolve("parent"))
          val target = parent.resolve("candidate.html")

          When("the current declared tuple is written")
          val result = writeCandidate(reviewvalue, parent, target)

          Then("the only installed file contains the exact HTML bytes")
          result shouldBe Right(())
          Files.readAllBytes(target).toVector shouldBe reviewvalue.htmlBytes
          _children(parent) shouldBe Vector(target)
        }
      }

      "preserve prior bytes for invalid suffix, traversal, and symlinks" in {
        _with_temp_dir { root =>
          Given("a candidate preview, existing output files, and invalid path fixtures")
          val fixture = _right(CozyLogicalUiSalesOrderFixtures.candidate())
          val reviewvalue = _right(renderCandidate(fixture._1, fixture._2, fixture._3))
          val parent = Files.createDirectory(root.resolve("parent"))
          val target = parent.resolve("candidate.html")
          val invalidsuffix = parent.resolve("candidate.txt")
          val external = Files.createDirectory(root.resolve("external"))
          val nested = Files.createDirectory(external.resolve("nested"))
          val sentinel = nested.resolve("candidate.html")
          val link = Files.createSymbolicLink(parent.resolve("link"), external)
          val targetlink = Files.createSymbolicLink(parent.resolve("linked.html"), sentinel)
          val traversingparent = parent.resolve("..")
          val traversingsentinel = root.resolve("candidate.html")
          val sentinels = Vector(target, invalidsuffix, sentinel, traversingsentinel)
          sentinels.foreach(path => Files.write(path, "preserve-me".getBytes(StandardCharsets.UTF_8)))
          When("a required input or an output path is invalid")
          val nullresult = writeCandidate(null, parent, target)
          val pathresults = Vector(
            writeCandidate(reviewvalue, parent, invalidsuffix),
            writeCandidate(reviewvalue, traversingparent, traversingparent.resolve("candidate.html")),
            writeCandidate(reviewvalue, link.resolve("nested"), link.resolve("nested").resolve("candidate.html")),
            writeCandidate(reviewvalue, parent, targetlink)
          )

          Then("input and output diagnostics preserve all existing direct and external files")
          Vector(nullresult).foreach(result =>
            _left(result).code shouldBe "LUI50_CANDIDATE_REVIEW_INPUT_INVALID"
          )
          pathresults.foreach(result => _left(result).code shouldBe "LUI43_REVIEW_OUTPUT_INVALID")
          sentinels.foreach(path => Files.readAllBytes(path).toVector shouldBe "preserve-me".getBytes(StandardCharsets.UTF_8).toVector)
          _children(parent).map(_.getFileName.toString).toSet shouldBe Set("candidate.html", "candidate.txt", "link", "linked.html")
          Files.isSymbolicLink(targetlink) shouldBe true
        }
      }
    }

    "share compact simulated success inputs" which {
      "retain one repeatable order and the existing normal confirm action without creating acceptance" in {
        Given("the target-neutral immutable compact success fixture and shared candidate semantic tuple")
        val fixture = compactSuccess
        val tuple = _right(CozyLogicalUiSalesOrderFixtures.candidate())

        When("the declared initial/result records and normal transition admission are consumed")
        val repeated = compactSuccess
        val action = tuple._3.input.transitionActions.head
        val normalmappings = tuple._2.input.mappings.filter(_.stepId == "confirm")

        Then("only explicit domain state differs, UI success remains distinct, and action evidence uses exact public bindings")
        repeated shouldBe fixture
        fixture.fixtureId shouldBe "sales-order-compact-v1"
        fixture.coverageId shouldBe "P50-COMPACT-NORMAL-001"
        fixture.initialOrder.orderId shouldBe "SO-001"
        fixture.initialOrder.label shouldBe "Sales order SO-001"
        fixture.initialOrder.amount shouldBe "100.00"
        fixture.successfulOrder shouldBe fixture.initialOrder.copy(domainState = DomainStateReference(stateMachineBinding, "confirmed"))
        fixture.initialOrder.domainState shouldBe DomainStateReference(stateMachineBinding, "draft")
        fixture.successUiState shouldBe SucceededInteractionState
        action.step shouldBe UiUseCaseStep(uiUseCase, "confirm", NormalPath)
        action.screenId shouldBe "order-detail"
        action.interactionId shouldBe "invoke"
        action.operation shouldBe operationBinding
        action.stateMachine shouldBe stateMachineBinding
        action.from shouldBe fixture.initialOrder.domainState
        action.to shouldBe fixture.successfulOrder.domainState
        action.interactionState shouldBe fixture.successUiState
        tuple._1.input.componentBindings should contain allElementsOf Vector(operationBinding, stateMachineBinding)
        normalmappings.map(value => value.screenId -> value.interactionId).toSet shouldBe Set(
          "order-list" -> "entry", "order-list" -> "to-detail", "order-detail" -> "select",
          "order-detail" -> "invoke", "order-detail" -> "observe"
        )
      }
    }
  }

  private def _changed_projection(candidate: LogicalUiCandidate): LogicalUiProjection = {
    val input = projectionInput(candidate)
    _right(CozyLogicalUi.project(candidate, input.copy(screens = input.screens.map(screen =>
      if (screen.id == "order-detail") screen.copy(primaryPurpose = "inspect sales order") else screen
    ))))
  }

  private def _with_temp_dir[A](body: Path => A): A = {
    val work = Paths.get(sys.props("user.dir")).resolve("target")
    Files.createDirectories(work)
    val root = Files.createTempDirectory(work, "cozy-candidate-review-")
    try body(root)
    finally {
      val paths = Files.walk(root)
      try paths.iterator().asScala.toVector.reverse.foreach(path => Files.deleteIfExists(path))
      finally paths.close()
    }
  }

  private def _children(parent: Path): Vector[Path] = {
    val paths = Files.list(parent)
    try paths.iterator().asScala.toVector.sortBy(_.getFileName.toString)
    finally paths.close()
  }

  private def _right[A](value: Either[LogicalUiError, A]): A = value match {
    case Right(result) => result
    case Left(error) => fail(s"Expected a Logical UI result but received ${error.code}: ${error.reason}")
  }

  private def _left[A](value: Either[LogicalUiError, A]): LogicalUiError = value match {
    case Left(error) => error
    case Right(_) => fail("Expected a closed Logical UI failure")
  }
}
