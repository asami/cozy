package cozy.modeler

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._
import CmlModelEditModel._
import CmlModelEditJson._

/*
 * @since   Oct. 27, 2026
 * @version Oct. 27, 2026
 */
final class CmlModelEditCreationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _root = Paths.get("src/test/resources/cozy/modeler")
  private val _basis = _root.resolve("model-edit-creation-basis.cml")
  private val _candidate_path = _root.resolve("model-edit-creation-candidate-v2.json")
  private val _source = Source("example", "models/order.cml", "r1")
  private val _json = Json.parse(new String(Files.readAllBytes(_candidate_path), StandardCharsets.UTF_8))
  private def _right[A](result: Either[Vector[Diagnostic], A]): A = result match {
    case Right(value) => value
    case Left(errors) => fail(errors.mkString("; "))
  }
  private def _write(text: String): java.nio.file.Path = {
    val path = Paths.get("target/test-generated/model-edit-creation/proposed.cml")
    Files.createDirectories(path.getParent)
    Files.write(path, text.getBytes(StandardCharsets.UTF_8))
    path
  }

  "Explicit source-owned candidate v2" should {
    "create Goal, Slice and all five trace edges from an actual basis that has none" in {
      Given("a real owned source with a shared Actor and UseCase, no Goal/Slice/trace edges, and an explicit v2 candidate")
      val bytes = Files.readAllBytes(_basis).toVector
      val before = _right(CmlModelEditProduction.produce(_basis, _source))
      val candidate = _right(CmlModelEditProduction.readCandidateV2(_json))
      When("the source owner renders the additions, produces actual proposed CML and reads its versioned output")
      val rendering = _right(CmlModelEditProduction.renderCandidateV2(_basis, _source, candidate))
      val after = _right(CmlModelEditProduction.produce(_write(rendering.text), candidate.source))
      val read = _right(CmlModelEditProduction.read(Json.toJson(after)))
      Then("explicit new identities, fields, lifecycle and independent links coexist with every unchanged basis record")
      before.document.elements.filter(x => Set[ElementKind](Goal, Slice)(x.kind)) shouldBe empty
      before.document.relationships.filter(x => Set[RelationKind](ActorGoal, GoalUseCase, UseCaseSlice, SliceSpecification, SliceTicket)(x.kind)) shouldBe empty
      before.document.coverage.find(_.family == "goal").get.status shouldBe Missing
      after.document.elements shouldBe before.document.elements ++ candidate.addedElements
      after.document.relationships shouldBe before.document.relationships ++ candidate.addedRelationships
      after.document.elements.count(_.kind == Actor) shouldBe 1
      after.document.coverage.find(_.family == "goal").get.status shouldBe Present
      after.document.elements.find(_.kind == Slice).get.lifecycle shouldBe Some("Proposed")
      candidate.addedElements.foreach(_.source shouldBe candidate.source)
      candidate.addedRelationships.foreach(_.source shouldBe candidate.source)
      rendering.schemaVersion shouldBe candidateV2Version
      rendering.text should endWith ("# NOTE\n\nCreation must preserve this authored note.\n")
      Files.readAllBytes(_basis).toVector shouldBe bytes
      read shouldBe after
    }

    "reject occupied stable IDs, duplicate additions and identity reuse after removal" in {
      Given("an admitted basis and explicit additions")
      val basis = _right(CmlModelEditProduction.produce(_basis, _source))
      val candidate = _right(CmlModelEditProduction.readCandidateV2(_json))
      val goal = candidate.addedElements.head
      val edge = candidate.addedRelationships.head
      val occupied = basis.document.relationships.head
      val invalid = Vector(
        candidate.copy(addedElements = candidate.addedElements :+ goal),
        candidate.copy(addedRelationships = candidate.addedRelationships :+ edge),
        candidate.copy(addedElements = Vector(goal.copy(ref = basis.document.elements.head.ref.copy(revision = 9)))),
        candidate.copy(removedRelationships = Vector(occupied.ref),
          addedRelationships = Vector(edge.copy(ref = occupied.ref.copy(revision = 9)))))
      When("each attempted addition is rendered against the real source")
      val results = invalid.map(CmlModelEditProduction.renderCandidateV2(_basis, _source, _))
      Then("no new ID can overwrite a stable existing or newly occupied identity")
      results.foreach(_.isLeft shouldBe true)
    }

    "reject foreign source ownership, invalid types or endpoints and undeclared evidence without writing source" in {
      Given("complete explicit records and an owned source")
      val candidate = _right(CmlModelEditProduction.readCandidateV2(_json))
      val original = Files.readAllBytes(_basis).toVector
      val goal = candidate.addedElements.head
      val edge = candidate.addedRelationships.head
      val invalid = Vector(
        candidate.copy(source = candidate.source.copy(owner = "foreign")),
        candidate.copy(addedElements = candidate.addedElements.updated(0, goal.copy(ref = goal.ref.copy(owner = "foreign")))),
        candidate.copy(addedElements = candidate.addedElements.updated(0, goal.copy(source = goal.source.copy(identity = "foreign.cml")))),
        candidate.copy(addedElements = candidate.addedElements.updated(0, goal.copy(ref = goal.ref.copy(revision = 0)))),
        candidate.copy(addedRelationships = candidate.addedRelationships.updated(0, edge.copy(source = _source))),
        candidate.copy(addedRelationships = candidate.addedRelationships.updated(0, edge.copy(kind = UseCaseSlice))),
        candidate.copy(addedRelationships = candidate.addedRelationships.updated(0,
          edge.copy(to = LocalEndpoint(goal.ref.copy(revision = 99))))),
        candidate.copy(coverage = candidate.coverage.filterNot(_.family == "goal")),
        candidate.copy(coverage = candidate.coverage.map(x => if (x.family == "goal") x.copy(status = Unsupported, detail = Some("Unavailable semantics")) else x)))
      When("these invalid owned-source candidates are rendered")
      val results = invalid.map(CmlModelEditProduction.renderCandidateV2(_basis, _source, _))
      Then("strict source, revision, graph, kind and evidence admission rejects every candidate")
      results.foreach(_.isLeft shouldBe true)
      Files.readAllBytes(_basis).toVector shouldBe original
    }

    "require complete explicit coverage even when no records are added" in {
      Given("a basis with no Goal and a no-addition v2 proposal")
      val basis = _right(CmlModelEditProduction.produce(_basis, _source))
      val candidate = _right(CmlModelEditProduction.readCandidateV2(_json)).copy(
        addedElements = Vector.empty, addedRelationships = Vector.empty, coverage = basis.document.coverage)
      val invalid = Vector(
        candidate.copy(coverage = candidate.coverage.filterNot(_.family == "goal")),
        candidate.copy(coverage = candidate.coverage :+ candidate.coverage.head),
        candidate.copy(coverage = candidate.coverage.updated(0, candidate.coverage.head.copy(family = "unknown"))))
      When("incomplete, repeated or unknown coverage is read and rendered")
      val read = invalid.map(x => CmlModelEditProduction.readCandidateV2(Json.toJson(x)))
      val rendered = invalid.map(CmlModelEditProduction.renderCandidateV2(_basis, _source, _))
      val complete = CmlModelEditProduction.renderCandidateV2(_basis, _source, candidate)
      Then("v2 rejects every implicit coverage proposal and accepts the complete no-addition proposal")
      read.foreach(_.isLeft shouldBe true)
      rendered.foreach(_.isLeft shouldBe true)
      complete.isRight shouldBe true
    }

    "keep v1 and v2 entry points explicit with no alias or fallback" in {
      Given("a real v2 wire and its distinct typed field set")
      val v1 = Candidate(candidateVersion, _source.copy(revision = "r2"), Vector.empty, Vector.empty, Vector.empty)
      When("the public readers receive another version, an unknown field or a missing additions field")
      val v1onv2 = CmlModelEditProduction.readCandidate(_json)
      val v2onv1 = CmlModelEditProduction.readCandidateV2(Json.toJson(v1))
      val unknown = CmlModelEditProduction.readCandidateV2(_json.as[JsObject] + ("unknown" -> JsBoolean(true)))
      val missing = CmlModelEditProduction.readCandidateV2(_json.as[JsObject] - "addedElements")
      val acceptedv1 = CmlModelEditProduction.readCandidate(Json.toJson(v1))
      Then("only the declared existing v1 entry accepts v1, and malformed or cross-version wires are rejected")
      Vector(v1onv2, v2onv1, unknown, missing).foreach(_.isLeft shouldBe true)
      acceptedv1 shouldBe Right(v1)
    }

    "execute the explicit v2 CLI operation with actual source and candidate files" in {
      Given("the actual creation basis and caller-owned v2 candidate fixture")
      val buffer = new ByteArrayOutputStream()
      When("the explicit v2 CLI renders and its returned actual CML is produced")
      Console.withOut(new PrintStream(buffer, true, "UTF-8")) {
        CmlModelEditCli.main(Array("render-candidate-v2", _basis.toString, _source.owner, _source.identity, _source.revision, _candidate_path.toString))
      }
      val rendering = Json.parse(buffer.toString("UTF-8")).as[Rendering]
      val after = _right(CmlModelEditProduction.produce(_write(rendering.text), rendering.candidateSource))
      Then("new Goal/Slice and their explicit links are available from the normal source producer")
      rendering.schemaVersion shouldBe candidateV2Version
      after.document.elements.find(_.kind == Goal).get.ref.identity shouldBe "repeat-purchase"
      after.document.elements.find(_.kind == Slice).get.ref.identity shouldBe "submit-repeat"
      after.document.relationships.count(_.kind == SliceTicket) shouldBe 1
    }
  }
}
