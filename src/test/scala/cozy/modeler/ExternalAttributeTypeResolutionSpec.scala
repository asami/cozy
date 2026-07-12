package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr.  9, 2026
 *  version May. 21, 2026
 *  version Jun. 23, 2026
 * @version Jul. 13, 2026
 * @author  ASAMI, Tomoharu
 */
class ExternalAttributeTypeResolutionSpec extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  "External attribute type resolution" should {
    "keep delegate value composition for textus UserProfile values" in {
      Given("a UserProfile entity with optional delegate values")
      val out = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/test-generated/modeler-scala-user-account-address")
      delete_recursively(out)
      Files.createDirectories(out)
      val input = out.resolve("user-profile.cml")
      Files.writeString(
        input,
        """# COMPONENT

## UserAccount

### PACKAGE

org.simplemodeling.textus.useraccount

# ENTITY

## UserProfile

### features

extends = ["SimpleEntity"]

### DELEGATE

- name: IdentityPresentation
  multiplicity: "?"
- name: PersonalProfile
  multiplicity: "?"
- name: OrganizationSupport
  multiplicity: "?"

### ATTRIBUTE

| name          | type     | multiplicity |
|---------------+----------+--------------|
| userAccountId | entityid | 1            |

# VALUE

## IdentityPresentation

### ATTRIBUTE

| name        | type   | multiplicity |
|-------------+--------+--------------|
| displayName | string | ?            |

## PersonalProfile

### ATTRIBUTE

| name      | type   | multiplicity |
|-----------+--------+--------------|
| givenName | string | ?            |

## OrganizationSupport

### ATTRIBUTE

| name             | type   | multiplicity |
|------------------+--------+--------------|
| organizationName | string | ?            |
""",
        StandardCharsets.UTF_8
      )

      When("Cozy generates Scala code")
      cozy.Cozy.main(Array("modeler-scala", input.toString, "--save", out.toString.toString))

      Then("the generated entity keeps delegate fields as optional composed value objects")
      val generated = out.resolve("target/scala-3.3.7/src_managed/main/scala/org/simplemodeling/textus/useraccount/entity/UserProfile.scala")
      withClue(s"generated file not found: $generated") {
        Files.exists(generated) shouldBe true
      }
      val content = Files.readString(generated)
      withClue(content) {
        content should include ("identityPresentation: Option[IdentityPresentation]")
        content should include ("personalProfile: Option[PersonalProfile]")
        content should include ("organizationSupport: Option[OrganizationSupport]")
      }
    }


    "preserve built-in urn, blob, and clob runtime types" in {
      Given("an entity that uses built-in external runtime datatypes")
      val out = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/test-generated/modeler-scala-builtins")
      delete_recursively(out)
      Files.createDirectories(out)
      val input = out.resolve("builtins.cml")
      Files.writeString(
        input,
        """# COMPONENT

## BuiltinTypeSpec

### PACKAGE

org.sample.builtin

# ENTITY

## BuiltinHolder

### ATTRIBUTE

| name | type | multiplicity |
|------|------|--------------|
| id | entityid | 1 |
| resourceUrn | urn | 1 |
| payload | blob | ? |
| description | clob | ? |
""",
        StandardCharsets.UTF_8
      )

      When("Cozy generates Scala code")
      cozy.Cozy.main(Array("modeler-scala", input.toString, "--save", out.toString.toString))

      Then("the generated entity keeps each runtime datatype specialized")
      val generated = out.resolve("target/scala-3.3.7/src_managed/main/scala/org/sample/builtin/entity/BuiltinHolder.scala")
      withClue(s"generated file not found: $generated") {
        Files.exists(generated) shouldBe true
      }
      val content = Files.readString(generated)
      withClue(content) {
        content should include ("resourceUrn: Urn")
        content should include ("payload: Option[BinaryBag]")
        content should include ("description: Option[TextBag]")
      }
    }

    "generate the built-in record datatype as the CNCF Record runtime type" in {
      Given("a value model with required, optional, and repeated record attributes")
      val out = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/test-generated/modeler-scala-record-builtin")
      delete_recursively(out)
      Files.createDirectories(out)
      val input = out.resolve("record-builtin.cml")
      Files.writeString(
        input,
        """# COMPONENT

## RecordBuiltinSpec

### PACKAGE

org.sample.recordbuiltin

# VALUE

## RecordEnvelope

### ATTRIBUTE

| name             | type   | multiplicity |
|------------------+--------+--------------|
| payload          | record | 1            |
| optional_payload | record | ?            |
| required_records | record | +            |
| records          | record | *            |
""",
        StandardCharsets.UTF_8
      )

      When("Cozy generates Scala code")
      cozy.Cozy.main(Array("modeler-scala", input.toString, "--save", out.toString))

      Then("the generated value uses Record without a generated datatype wrapper")
      val generated = out.resolve("target/scala-3.3.7/src_managed/main/scala/org/sample/recordbuiltin/value/RecordEnvelope.scala")
      withClue(s"generated file not found: $generated") {
        Files.exists(generated) shouldBe true
      }
      val content = Files.readString(generated)
      withClue(content) {
        content should include ("payload: Record")
        content should include ("optional_payload: Option[Record]")
        content should include ("required_records: NonEmptyVector[Record]")
        content should include ("records: Vector[Record]")
        content should include ("required_records: Option[NonEmptyVector[Record]]")
        content should include ("case m: cats.data.NonEmptyVector[?] => m.toVector.map(_to_external_value)")
        content should include ("case Some(xs) => Consequence.successOrPropertyNotFound(PROP_REQUIRED_RECORDS, NonEmptyVector.fromVector(xs))")
        content should include ("case None => Consequence.successOrPropertyNotFound(PROP_REQUIRED_RECORDS, required_records)")
        content should not include ("NonEmptyVector.fromVector(xs).map(Some(_))")
        content should not include ("org.goldenport.datatype.Record")
      }
    }



    "use the component package for value models" in {
      Given("a value model with only a component package")
      val out = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/test-generated/modeler-scala-value-package")
      delete_recursively(out)
      Files.createDirectories(out)
      val input = out.resolve("address-package.cml")
      Files.writeString(
        input,
        """# COMPONENT

## SimpleModelingModel

### PACKAGE

org.simplemodeling.model

# VALUE

## Address

### ATTRIBUTE

- name: value
  type: String
  multiplicity: "1"
""",
        StandardCharsets.UTF_8
      )

      When("Cozy generates Scala code")
      cozy.Cozy.main(Array("modeler-scala", input.toString, "--save", out.toString.toString))

      Then("the value class is generated under the component package")
      val generated = out.resolve("target/scala-3.3.7/src_managed/main/scala/org/simplemodeling/model/value/Address.scala")
      withClue(s"generated file not found: $generated") {
        Files.exists(generated) shouldBe true
      }
    }

    "support DELEGATE section required and optional composition" in {
      Given("an entity with required and optional delegate value composition")
      val out = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/test-generated/modeler-scala-delegate-entity")
      delete_recursively(out)
      Files.createDirectories(out)
      val input = out.resolve("delegate-entity.cml")
      Files.writeString(
        input,
        """# COMPONENT

## DelegateEntity

### PACKAGE

org.sample.delegateentity

# VALUE

## IdentityPresentation

### ATTRIBUTE

| name | type | multiplicity |
|------|------|--------------|
| displayName | string | 1 |

## OrganizationSupport

### ATTRIBUTE

| name | type | multiplicity |
|------|------|--------------|
| organization | string | 1 |

# ENTITY

## UserProfile

### DELEGATE

- name: IdentityPresentation
  multiplicity: "1"
- name: OrganizationSupport
  multiplicity: "?"

### ATTRIBUTE

| name | type | multiplicity |
|------|------|--------------|
| userAccountId | entityid | 1 |
""",
        StandardCharsets.UTF_8
      )

      When("Cozy generates Scala code")
      cozy.Cozy.main(Array("modeler-scala", input.toString, "--save", out.toString.toString))

      Then("required delegates are generated as values and optional delegates as Option values")
      val generated = out.resolve("target/scala-3.3.7/src_managed/main/scala/org/sample/delegateentity/entity/UserProfile.scala")
      withClue(s"generated file not found: $generated") {
        Files.exists(generated) shouldBe true
      }
      val content = Files.readString(generated)
      withClue(content) {
        content should include ("identityPresentation: IdentityPresentation")
        content should include ("organizationSupport: Option[OrganizationSupport]")
      }
    }
  }
}
