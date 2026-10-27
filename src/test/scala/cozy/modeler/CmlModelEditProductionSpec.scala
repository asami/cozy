package cozy.modeler

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}
import play.api.libs.json._
import CmlModelEditModel._
import CmlModelEditJson._

/*
 * @since   Oct. 27, 2026
 * @version Oct. 27, 2026
 */
final class CmlModelEditProductionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _source = Source("example", "models/order.cml", "r1")
  private val _successor = _source.copy(revision = "r2")
  private val _fixture = Paths.get("src/test/resources/cozy/modeler/model-edit-order.cml")
  private val _work = Paths.get("target/test-generated/model-edit-production")
  private val _text = new String(Files.readAllBytes(_fixture), StandardCharsets.UTF_8)
  private def _ref(identity: String, revision: Long = 1): RecordRef = RecordRef("example", identity, revision)
  private def _right[A](result: Either[Vector[Diagnostic], A]): A = result match {
    case Right(value) => value
    case Left(errors) => fail(errors.mkString("; "))
  }
  private def _write(name: String, text: String): Path = {
    Files.createDirectories(_work)
    val path = _work.resolve(name)
    Files.write(path, text.getBytes(StandardCharsets.UTF_8))
    path
  }
  private def _rewrite(key: String)(change: JsValue => JsValue): String =
    _text.linesIterator.map { line =>
      if (line.startsWith(key + " = ")) key + " = " + Json.stringify(change(Json.parse(line.substring(key.length + 3))))
      else line
    }.mkString("\n") + "\n"
  private def _slice_candidate(basis: Publication): Candidate = {
    val original = basis.document.elements.find(_.kind == Slice).get
    val replacement = original.copy(ref = _ref("submit-order", 2), lifecycle = Some("InDevelopment"),
      fields = Vector(Field("requestId", "string", "1"), Field("note", "string", "?")), source = _successor)
    val links = basis.document.relationships.filter(x => Set[RelationKind](UseCaseSlice, SliceSpecification, SliceTicket)(x.kind)).map { link =>
      val from = if (link.from == LocalEndpoint(original.ref)) LocalEndpoint(replacement.ref) else link.from
      val to = if (link.to == LocalEndpoint(original.ref)) LocalEndpoint(replacement.ref) else link.to
      RelationshipReplacement(link.ref, link.copy(ref = link.ref.copy(revision = 2), from = from, to = to, source = _successor))
    }
    Candidate(candidateVersion, _successor, Vector(ElementReplacement(original.ref, replacement)), links, Vector.empty)
  }

  "The source-owned model-edit producer" should {
    "produce and read explicit trace, behavioral and causal semantics from actual CML" in {
      Given("a real selected CML document with an independent shared Actor, Goal, UseCase, Slice and nine explicit edges")
      val original = Files.readAllBytes(_fixture).toVector
      When("the running producer acquires and parses it and the public reader admits its versioned output")
      val produced = _right(CmlModelEditProduction.produce(_fixture, _source))
      val read = _right(CmlModelEditProduction.read(Json.parse(Json.stringify(Json.toJson(produced)))))
      Then("the exact explicit identities, revisions, attributions and kinds remain available together")
      read shouldBe produced
      read.source shouldBe _source
      read.document.elements.map(x => x.kind -> x.ref.identity) shouldBe Vector(
        Actor -> "customer", Goal -> "purchase", UseCase -> "place-order", Slice -> "submit-order",
        Command -> "submit", Operation -> "validate", Aggregate -> "order", Entity -> "line",
        DomainEvent -> "order-placed", Policy -> "fulfil", Component -> "sales", Query -> "orders", View -> "order-view")
      read.document.elements.foreach(_.source shouldBe _source)
      read.document.elements.find(_.kind == Goal).get.intent shouldBe Some("Buy the selected products")
      read.document.relationships.map(_.kind) shouldBe Vector(ActorGoal, GoalUseCase, UseCaseSlice,
        SliceSpecification, SliceTicket, Cause, Reaction, Dependency, Context)
      read.document.relationships.find(_.kind == SliceSpecification).get.to shouldBe
        SpecificationEndpoint(Specification("example/order", "OrderSubmissionSpec", "spec-r7"))
      read.document.relationships.find(_.kind == SliceTicket).get.to shouldBe
        TicketEndpoint(Ticket("github", "example/order", "I_kwDOOrderIssue42", Some(TicketObservation("open", Some("observed-r4")))))
      read.document.relationships.find(_.kind == Dependency).get.to shouldBe
        ExternalEndpoint(RecordRef("logistics", "warehouse", 3))
      read.producer.name shouldBe "cozy"
      read.producer.version shouldBe org.simplemodeling.cozy.BuildInfo.version
      read.producer.revision should fullyMatch regex "[0-9a-f]{40}"
      read.document.coverage.find(_.family == "query").get.status shouldBe Present
      read.limitations shouldBe empty
      Files.readAllBytes(_fixture).toVector shouldBe original
    }

    "edit, render and reparse the actual candidate while preserving source and independent edge ownership" in {
      Given("an actual selected CML file and explicit higher Slice and relationship revisions")
      val path = _write("roundtrip.cml", _text)
      val basis = _right(CmlModelEditProduction.produce(path, _source))
      val candidate = _slice_candidate(basis)
      When("candidate rendering returns proposed text and that text is acquired and reprojected as the successor source")
      val rendering = _right(CmlModelEditProduction.renderCandidate(path, _source, candidate))
      val proposed = _write("proposed.cml", rendering.text)
      val after = _right(CmlModelEditProduction.produce(proposed, _successor))
      val read = _right(CmlModelEditProduction.read(Json.toJson(after)))
      Then("canonical bytes and untouched meaning remain, while exact selected Slice fields/lifecycle and explicit edges change")
      new String(Files.readAllBytes(path), StandardCharsets.UTF_8) shouldBe _text
      rendering.text should startWith ("# COMPONENT\n\n## OrderModel\n\n### PACKAGE\nexample.order\n\n")
      rendering.text should endWith ("# NOTE\n\nThis authored note must survive candidate rendering unchanged.\n")
      after.source shouldBe _successor
      after.document.elements.find(_.kind == Slice).get shouldBe candidate.elements.head.replacement
      after.document.elements.filterNot(_.kind == Slice) shouldBe basis.document.elements.filterNot(_.kind == Slice)
      after.document.relationships.map(_.ref.identity) shouldBe basis.document.relationships.map(_.ref.identity)
      candidate.relationships.foreach(x => after.document.relationships.find(_.ref == x.replacement.ref) shouldBe Some(x.replacement))
      after.document.relationships.filter(x => Set[RelationKind](ActorGoal, GoalUseCase, Cause, Reaction, Dependency, Context)(x.kind)) shouldBe
        basis.document.relationships.filter(x => Set[RelationKind](ActorGoal, GoalUseCase, Cause, Reaction, Dependency, Context)(x.kind))
      read shouldBe after
    }

    "preserve parsed roots with mixed outline headings during candidate rendering" in {
      val tails = Vector("* NOTE\n\nKeep this mixed outline note unchanged.\n",
        "* NOTE\n\nKeep this final note without a trailing newline.")
      tails.zipWithIndex.foreach { case (tail, index) =>
        Given("an actual CML source whose next parsed root uses another supported heading form")
        val text = _text.take(_text.indexOf("# NOTE")) + tail
        val path = _write(s"mixed-$index.cml", text)
        val basis = _right(CmlModelEditProduction.produce(path, _source))
        val candidate = _slice_candidate(basis)
        When("the candidate is rendered and the proposed actual CML is produced again")
        val rendering = _right(CmlModelEditProduction.renderCandidate(path, _source, candidate))
        val after = _right(CmlModelEditProduction.produce(_write(s"mixed-proposed-$index.cml", rendering.text), _successor))
        Then("unrelated source bytes survive and the exact selected Slice is refined")
        rendering.text should endWith (tail)
        new String(Files.readAllBytes(path), StandardCharsets.UTF_8) shouldBe text
        after.document.elements.find(_.kind == Slice).get shouldBe candidate.elements.head.replacement
      }
    }

    "report existing CML operation declarations as unsupported rather than missing model-edit evidence" in {
      Given("the repository's actual legacy CML fixture declaring COMMAND, OPERATION and QUERY")
      val path = Paths.get("src/test/resources/modeler/operation-grammar.dox")
      When("the source producer acquires the declarations without a MODEL-EDIT identity block")
      val result = _right(CmlModelEditProduction.produce(path, _source))
      Then("the present declarations remain unsupported, undeclared families remain missing, and no records are inferred")
      Vector("command", "operation", "query").foreach { family =>
        result.document.coverage.find(_.family == family).get.status shouldBe Unsupported
      }
      result.document.coverage.find(_.family == "actor").get.status shouldBe Missing
      result.document.elements shouldBe empty
      result.document.relationships shouldBe empty
    }

    "refuse guessed, foreign, stale or incomplete target edits without changing source" in {
      Given("a valid basis and candidate whose Slice reference changes")
      val path = _write("rejections.cml", _text)
      val basis = _right(CmlModelEditProduction.produce(path, _source))
      val candidate = _slice_candidate(basis)
      val edit = candidate.elements.head
      val invalid = Vector(
        candidate.copy(elements = Vector(edit.copy(expected = edit.expected.copy(revision = 9)))),
        candidate.copy(elements = Vector(edit.copy(replacement = edit.replacement.copy(ref = edit.replacement.ref.copy(owner = "other"))))),
        candidate.copy(elements = Vector(edit.copy(replacement = edit.replacement.copy(ref = edit.expected)))),
        candidate.copy(relationships = Vector.empty),
        candidate.copy(source = _source),
        candidate.copy(elements = Vector(edit, edit)))
      When("edits select a stale revision, foreign identity, non-increasing version or omit required exact edge edits")
      val rejected = invalid.map(CmlModelEditProduction.renderCandidate(path, _source, _))
      val mismatched = CmlModelEditProduction.produce(path, _source.copy(revision = "wrong"))
      Then("every unsupported candidate is a typed rejection and no canonical write occurs")
      rejected.foreach(_.isLeft shouldBe true)
      new String(Files.readAllBytes(path), StandardCharsets.UTF_8) shouldBe _text
      mismatched.isLeft shouldBe true
    }

    "distinguish present-empty, omitted, unavailable, unsupported and external coverage" in {
      Given("actual source declarations with independently bounded Query/View coverage")
      val states = Vector(Present, Missing, Unavailable, Unsupported, External)
      states.foreach { state =>
        val elementsremoved = _rewrite("elements") { value => JsArray(value.as[Vector[JsObject]].filterNot(x => (x \ "kind").as[String] == "view")) }
        val line = elementsremoved.linesIterator.find(_.startsWith("elements = ")).get
        val text = _rewrite("coverage") { value =>
          val others = value.as[Vector[JsObject]].filterNot(x => (x \ "family").as[String] == "view")
          JsArray(if (state == Missing) others else others :+ Json.obj("family" -> "view", "status" -> state.name, "detail" -> "Selected scope declaration"))
        }.linesIterator.map(x => if (x.startsWith("elements = ")) line else x).mkString("\n") + "\n"
        When(s"the actual CML is produced with ${state.name} View evidence")
        val result = _right(CmlModelEditProduction.produce(_write(s"coverage-${state.name}.cml", text), _source))
        Then("the source declaration remains distinct and no absence is promoted to a fact")
        result.document.coverage.find(_.family == "view").get.status shouldBe state
        result.document.elements.filter(_.kind == View) shouldBe empty
      }
    }

    "report malformed and unsupported source forms instead of manufacturing empty supported meaning" in {
      Given("the owned syntax and complete literal source selection")
      val variants = Vector(
        _text.replace("cozy.cml.model-edit.v1", "cozy.cml.model-edit.v99"),
        _text.replace("coverage = ", "unknown = "),
        _text.replace("# NOTE\n", "NOTE\n====\n"),
        _text.replace("# MODEL-EDIT\n", "# MODEL-EDIT\n\n## Nested\n"),
        _text + "\n# MODEL-EDIT\n\nsource = {}\n",
        _text.replace("\"kind\":\"actor\"", "\"kind\":\"guessed-actor\""))
      When("unsupported owned declarations are actually parsed")
      val results = variants.zipWithIndex.map { case (text, index) =>
        CmlModelEditProduction.produce(_write(s"unsupported-$index.cml", text), _source)
      }
      val legacy = _right(CmlModelEditProduction.produce(_write("legacy.cml", "# COMPONENT\n\n## Example\n"), _source))
      val unavailable = CmlModelEditProduction.produce(_work.resolve("does-not-exist.cml"), _source)
      Then("each is rejected, while legacy declaration and missing file return their distinct boundary outcomes")
      results.foreach(_.isLeft shouldBe true)
      legacy.limitations.map(_.code) should contain ("missing-model-edit")
      legacy.document.coverage.find(_.family == "component").get.status shouldBe Unsupported
      legacy.document.elements shouldBe empty
      unavailable.left.toOption.get.map(_.code) should contain ("unavailable")
    }

    "retain explicit semantics under independent label variation without inferring causal links" in {
      Given("independently generated source labels, fixed stable IDs and a context relation")
      val labels = Gen.nonEmptyListOf(Gen.alphaChar).map(_.mkString.take(40))
      When("100 real source productions vary labels rather than identity or relationship declarations")
      val property = Prop.forAll(labels) { label =>
        val text = _text.replace("Purchase products", label).replace("Place order", label)
        val result = CmlModelEditProduction.produce(_write("property.cml", text), _source)
        result.exists { publication =>
          publication.document.elements.find(_.kind == Goal).exists(_.ref == _ref("purchase")) &&
          publication.document.elements.find(_.kind == UseCase).exists(_.ref == _ref("place-order")) &&
          publication.document.relationships.filter(_.kind == Cause).map(_.ref.identity) == Vector("submit-causes-placed") &&
          publication.document.relationships.filter(_.kind == Context).map(_.ref.identity) == Vector("place-query-context")
        }
      }
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1), property)
      Then("all samples pass with zero discarded samples and contextual references stay contextual")
      result.passed shouldBe true
      result.succeeded should be >= 100
      result.discarded shouldBe 0
    }

    "execute the public CLI producer and supported read operation on actual files" in {
      Given("the running CLI main and actual selected CML")
      val buffer = new ByteArrayOutputStream()
      When("the public produce operation executes against the file")
      Console.withOut(new PrintStream(buffer, true, "UTF-8")) {
        CmlModelEditCli.main(Array("produce", _fixture.toString, _source.owner, _source.identity, _source.revision))
      }
      val output = _right(CmlModelEditProduction.read(Json.parse(buffer.toString("UTF-8"))))
      val publication = _write("cli-publication.json", buffer.toString("UTF-8"))
      val reread = new ByteArrayOutputStream()
      Console.withOut(new PrintStream(reread, true, "UTF-8")) { CmlModelEditCli.main(Array("read", publication.toString)) }
      val read = _right(CmlModelEditProduction.read(Json.parse(reread.toString("UTF-8"))))
      Then("its actual output is admitted by the supported reader and retains executing producer provenance")
      output.producer shouldBe CmlModelEditProduction.executingProducer
      output.document.elements.find(_.kind == Slice).get.lifecycle shouldBe Some("Proposed")
      read shouldBe output
    }
  }
}
