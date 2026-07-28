package cozy.config

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class Phase51Cv01AcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy CV-01 version-source inventory" should {
    "register descriptor identity mismatch" in {
      Given("project and descriptor identities in distinct Cozy metadata fields")
      When("the production project parser prepares the two identity claims")
      val config = _config("project:", "  name: sample", "descriptor:", "  cncf-version: 0.5.0")
      val identities = (config.value("project.name"), config.value("descriptor.cncf-version"))
      Then("CV-04 owns runtime descriptor target validation")
      identities shouldBe (Some("sample"), Some("0.5.0"))
      cancel("CV-04 owns descriptor identity mismatch")
    }

    "register descriptor byte or digest tampering" in {
      Given("descriptor bytes and a separately recorded digest")
      When("the production project parser prepares provenance inputs")
      val config = _config("descriptor:", "  bytes: actual", "  digest: recorded")
      val evidence = config.mapUnder("descriptor")
      Then("CV-04 owns runtime descriptor digest validation")
      evidence shouldBe Map("bytes" -> "actual", "digest" -> "recorded")
      cancel("CV-04 owns runtime descriptor digest validation")
    }

    "register provenance and digest" in {
      Given("generation input and output identities plus generated safe scalar values")
      val scalar = Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)
      val property = Prop.forAll(scalar, scalar, scalar) { (input, output, digest) =>
        _config(
          "provenance:",
          s"  input: $input",
          s"  output: $output",
          s"  digest: $digest"
        ).mapUnder("provenance") ==
          Map("input" -> input, "output" -> output, "digest" -> digest)
      }
      When("the production project parser prepares provenance evidence")
      val config = _config("provenance:", "  input: descriptor.yaml", "  output: generated.scala", "  digest: abc123")
      val provenance = config.mapUnder("provenance")
      val propertyresult = Test.check(
        Test.Parameters.default.withMinSuccessfulTests(50),
        property
      )
      Then("CV-05 owns provenance and provenance digest tampering")
      provenance shouldBe Map("input" -> "descriptor.yaml", "output" -> "generated.scala", "digest" -> "abc123")
      propertyresult.passed shouldBe true
      cancel("CV-05 owns provenance and digest")
    }

    "register CAR compile target versus runtime range separation" in {
      Given("an exact compile target and an independent runtime range")
      val config = _config(
        "build:", "  cncf-version: 0.5.1",
        "packaging:", "  car:", "    runtime:", "      cncf:",
        "        minimum: 0.5.1", "        tested:", "          - 0.5.1"
      )
      When("the parsed project metadata exposes build and runtime identities")
      val buildtarget = config.value("build.cncf-version")
      val runtimetested = config.list("packaging.car.runtime.cncf.tested")
      Then("the later CAR metadata stage owns consistency")
      buildtarget shouldBe Some("0.5.1")
      runtimetested shouldBe Vector("0.5.1")
      cancel("CV-06 owns CAR compile-target and runtime-range consistency")
    }

    "register persisted scalar projection" in {
      Given("a project configuration containing a scalar persistence declaration")
      When("the production project parser prepares persistence metadata")
      val config = _config("persistence:", "  scalar: string", "  physical: json")
      val projection = config.mapUnder("persistence")
      Then("the later persistence stage owns scalar projection")
      projection shouldBe Map("scalar" -> "string", "physical" -> "json")
      cancel("SP-01 owns persisted scalar projection")
    }
  }

  private def _config(lines: String*): CozyProjectYamlConfig.Config = CozyProjectYamlConfig.parse(lines.toVector)
}
