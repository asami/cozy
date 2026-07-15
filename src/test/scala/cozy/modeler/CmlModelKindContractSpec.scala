package cozy.modeler

import java.nio.file.Files
import java.nio.file.Paths
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.simplemodeling.model.{MEntity, MNominalDataType, MPowertype, MStructuredDataType, MValue}

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class CmlModelKindContractSpec extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  "CML model kind contracts" should {
    "retain distinct generated and serialization responsibilities" in {
      Given("one CML model containing a Value, two Datatype forms, a Powertype, and a StateMachine")
      val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      val input = base.resolve("src/sbt-test/cozy/model-kind-runtime/model-kind-contract.cml")
      val out = base.resolve("target/test-generated/cml-model-kind-contract")
      delete_recursively(out)

      When("Cozy parses and generates the model")
      val kaleidoxmodel = KaleidoxModel.parseWitoutLocation(KaleidoxConfig.log.debug, Files.readString(input))
      val simplemodel = Modeler.ModelBuilder(kaleidoxmodel).build()
      cozy.Cozy.main(Array("modeler-scala", input.toString, "--save", out.toString))

      Then("the normalized model retains five distinct semantic kinds")
      simplemodel.elements.collect { case m: MValue => m.name } should contain ("CreateTicket")
      simplemodel.elements.collect { case m: MNominalDataType => m.name } should contain ("TicketCode")
      simplemodel.elements.collect { case m: MStructuredDataType => m.name } should contain ("GeoPoint")
      simplemodel.elements.collect { case m: MPowertype => m.name } should contain ("TicketStatus")
      val ticket = simplemodel.elements.collectFirst { case m: MEntity if m.name == "Ticket" => m }.get
      ticket.stateMachines.map(_.stateMachineName) should contain ("ticketLifecycle")

      val generatedroot = out.resolve("target/scala-3.3.8/src_managed/main/scala/domain")
      val value = Files.readString(generatedroot.resolve("value/CreateTicket.scala"))
      val nominaldatatype = Files.readString(generatedroot.resolve("datatype/TicketCode.scala"))
      val structureddatatype = Files.readString(generatedroot.resolve("datatype/GeoPoint.scala"))
      val powertype = Files.readString(generatedroot.resolve("value/TicketStatus.scala"))
      val component = Files.readString(generatedroot.resolve("TicketComponent.scala"))

      And("the Value remains an externally and persistently structured record")
      value should include ("case class CreateTicket(")
      value should include ("def toRecord(): Record")
      value should include ("def toDataStore(): Record")
      value should include ("case m: Record => createC(m)")
      value should not include ("extends org.simplemodeling.model.value.NominalScalar")
      value should not include ("extends Powertype")

      And("the plain Datatype remains a validated nominal scalar")
      nominaldatatype should include ("case class TicketCode(value: String")
      nominaldatatype should include ("extends org.simplemodeling.model.value.NominalScalar")
      nominaldatatype should include ("def toDataStore(): String")
      nominaldatatype should include ("given Codec[TicketCode] = Codec.from(")
      nominaldatatype should not include ("derives Codec.AsObject")
      nominaldatatype should not include ("extends Powertype")

      And("the complex Datatype remains a structured record")
      structureddatatype should include ("case class GeoPoint(")
      structureddatatype should include ("latitude: Double")
      structureddatatype should include ("longitude: Double")
      structureddatatype should include ("def toRecord(): Record")
      structureddatatype should include ("def toDataStore(): Record")
      structureddatatype should include ("derives Codec.AsObject")
      structureddatatype should not include ("extends org.simplemodeling.model.value.NominalScalar")
      structureddatatype should not include ("extends Powertype")

      And("the Powertype remains a closed scalar vocabulary")
      powertype should include ("case class TicketStatus(value: String) extends Powertype")
      powertype should include ("val Open: TicketStatus = TicketStatus(\"Open\")")
      powertype should include ("val Closed: TicketStatus = TicketStatus(\"Closed\")")
      powertype should include ("def from(value: String): Option[TicketStatus]")
      powertype should include ("def fromDbValue(value: Int): Option[TicketStatus]")
      powertype should include ("def toDataStore(): String")
      powertype should not include ("extends org.simplemodeling.model.value.NominalScalar")

      And("the StateMachine remains lifecycle topology rather than another serialized value class")
      component should include ("override def stateMachineDefinitions")
      component should include ("name = \"ticketLifecycle\"")
      component should include ("fromState = Some(\"Open\")")
      component should include ("eventName = \"close\"")
      component should include ("toState = Some(\"Closed\")")
      Files.exists(generatedroot.resolve("statemachine/ticketLifecycle.scala")) shouldBe false
    }
  }
}
