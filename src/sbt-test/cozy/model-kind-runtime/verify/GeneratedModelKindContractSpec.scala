import domain.TicketComponent
import domain.datatype.{GeoPoint, TicketCode}
import domain.value.{CreateTicket, TicketStatus}
import io.circe.{Decoder, Json}
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class GeneratedModelKindContractSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Generated CML model kind contracts" should {
    "execute distinct Value and Datatype serialization" in {
      Given("compiled generated Value, nominal Datatype, and structured Datatype classes")
      val code = TicketCode("TICKET-001")
      val location = GeoPoint(35.681236, 139.767125)
      val value = CreateTicket(code, location, TicketStatus.Open)

      When("their external and datastore projections are executed")
      val external = value.toRecord()
      val datastore = value.toDataStore()

      Then("the nominal Datatype uses a scalar codec and scalar datastore value")
      code.toRecord().getString("value") shouldBe Some("TICKET-001")
      code.toDataStore() shouldBe "TICKET-001"
      summon[org.goldenport.convert.ValueReader[TicketCode]].readC("TICKET-002").TAKE shouldBe TicketCode("TICKET-002")
      summon[Decoder[TicketCode]].decodeJson(Json.fromString("TICKET-003")) shouldBe Right(TicketCode("TICKET-003"))

      And("the complex Datatype keeps record-shaped external and datastore values")
      location.toRecord().getDouble("latitude") shouldBe Some(35.681236)
      location.toDataStore().getDouble("longitude") shouldBe Some(139.767125)

      And("the Value preserves its record boundary while delegating each datastore representation")
      external.getString("code") shouldBe Some("TICKET-001")
      external.getAny("location").get shouldBe a[Record]
      external.getAny("status").get shouldBe a[Record]
      datastore.getString("code") shouldBe Some("TICKET-001")
      datastore.getAny("location").get shouldBe a[Record]
      datastore.getString("status") shouldBe Some("Open")
    }

    "execute the closed Powertype scalar contract" in {
      Given("a compiled generated Powertype with logical and datastore values")

      When("logical and database representations are decoded")
      val logical = TicketStatus.from("Open")
      val database = TicketStatus.fromDbValue(2)

      Then("only declared vocabulary members are accepted")
      logical shouldBe Some(TicketStatus.Open)
      database shouldBe Some(TicketStatus.Closed)
      TicketStatus.from("Unknown") shouldBe None
      TicketStatus.Open.toRecord().getString("value") shouldBe Some("Open")
      TicketStatus.Open.toDataStore() shouldBe "Open"
    }

    "execute the StateMachine topology contract without a serialized lifecycle value" in {
      Given("the compiled generated component containing an entity StateMachine")
      val component = new TicketComponent()

      When("the CNCF lifecycle definitions and transition rules are inspected")
      val definition = component.stateMachineDefinitions.find(_.name == "ticketLifecycle").get
      val transition = component.stateMachineTransitionRules.find(_.eventName == "close").get

      Then("the StateMachine is executable topology distinct from the TicketStatus scalar")
      definition.states should contain theSameElementsInOrderAs Vector("Open", "Closed")
      definition.events shouldBe Vector("close")
      transition.fromState shouldBe Some("Open")
      transition.toState shouldBe Some("Closed")
      transition.fromStateValue shouldBe Some(1)
      transition.toStateValue shouldBe Some(2)
    }
  }
}
