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
 *  version Jul. 15, 2026
 * @version Oct. 19, 2026
 * @author  ASAMI, Tomoharu
 */
class ExternalAttributeTypeResolutionSpec extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  "External attribute type resolution" should {
    "project structured builtin spellings through ordinary Scala generation" which {
      Vector(
        ("json", "Json", "io.circe.Json"),
        ("XJson", "Json", "io.circe.Json"),
        ("semiStructuredData", "Record", "org.goldenport.record.Record"),
        ("XSemiStructuredData", "Record", "org.goldenport.record.Record"),
        ("semi-structured-data", "Record", "org.goldenport.record.Record"),
        ("record", "Record", "org.goldenport.record.Record")
      ).foreach { case (spelling, runtime, qualified) =>
        s"retain $runtime for required, optional, repeated and nonempty $spelling fields" in {
          Given(s"an entity and a value using $spelling at every supported multiplicity")
          val out = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve(s"target/test-generated/modeler-scala-structured-$spelling")
          delete_recursively(out)
          val input = out.resolve("structured.cml")
          val attributes = s"""### ATTRIBUTE

| name | type | multiplicity |
|------|------|--------------|
| payload | $spelling | 1 |
| optional_payload | $spelling | ? |
| payloads | $spelling | * |
| required_payloads | $spelling | + |
"""
          write_file(input, s"""# COMPONENT

## StructuredBuiltinSpec

### PACKAGE

org.sample.structured

# ENTITY

## StructuredHolder

$attributes
# VALUE

## StructuredEnvelope

$attributes
""")

          When("Cozy generates the Scala entity and value through modeler-scala")
          run_modeler_scala(input, out)

          Then(s"both generated models import $qualified and preserve each collection contract")
          Vector("entity/StructuredHolder.scala", "value/StructuredEnvelope.scala").foreach { relative =>
            val generated = out.resolve(s"target/scala-3.3.8/src_managed/main/scala/org/sample/structured/$relative")
            withClue(s"generated file not found: $generated") {
              Files.exists(generated) shouldBe true
            }
            val content = Files.readString(generated)
            withClue(content) {
              content should include (s"import $qualified")
              content should include (s"payload: $runtime")
              content should include (s"optional_payload: Option[$runtime]")
              content should include (s"payloads: Vector[$runtime]")
              content should include (s"required_payloads: NonEmptyVector[$runtime]")
              content should not include ("model.value.XJson")
              content should not include ("model.value.XSemiStructuredData")
              content should not include ("payload: XJson")
              content should not include ("payload: XSemiStructuredData")
              content should not include ("org.goldenport.datatype.Record")
            }
          }
        }
      }
    }

    "preserve nominal types and scalar priority beside structured builtin fields" in {
      Given("a JSON-bearing declared value, a nominal datatype and semantic and ordinary scalar controls")
      val out = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/test-generated/modeler-scala-structured-nominal-controls")
      delete_recursively(out)
      val input = out.resolve("nominal-controls.cml")
      write_file(input, """# COMPONENT

## StructuredNominalSpec

### PACKAGE

org.sample.structurednominal

# ENTITY

## NominalHolder

### ATTRIBUTE

| name | type | multiplicity |
|------|------|--------------|
| document | AuthoredDocument | 1 |
| optional_document | AuthoredDocument | ? |
| documents | AuthoredDocument | * |
| required_documents | AuthoredDocument | + |
| reference | ReferenceToken | 1 |
| email | email | 1 |
| text_value | string | 1 |
| count | int | 1 |
| amount | decimal | 1 |

# VALUE

## AuthoredDocument

### ATTRIBUTE

| name | type | multiplicity |
|------|------|--------------|
| payload | json | 1 |

# DATATYPE

## ReferenceToken

### ATTRIBUTE

| name | type | multiplicity |
|------|------|--------------|
| value | string | 1 |
""")

      When("Cozy generates Scala through the ordinary CML modeler command")
      run_modeler_scala(input, out)

      Then("the consuming entity retains declared names, semantic scalars and ordinary runtime scalars")
      val generatedroot = out.resolve("target/scala-3.3.8/src_managed/main/scala/org/sample/structurednominal")
      val content = Files.readString(generatedroot.resolve("entity/NominalHolder.scala"))
      withClue(content) {
        content should include ("document: AuthoredDocument")
        content should include ("optional_document: Option[AuthoredDocument]")
        content should include ("documents: Vector[AuthoredDocument]")
        content should include ("required_documents: NonEmptyVector[AuthoredDocument]")
        content should include ("reference: ReferenceToken")
        content should include ("email: EmailAddress")
        content should include ("text_value: String")
        content should include ("count: Int")
        content should include ("amount: BigDecimal")
        content should not include ("model.value.XJson")
        content should not include ("model.value.XSemiStructuredData")
      }
      And("the declared value contains the canonical JSON runtime without losing its own identity")
      val document = Files.readString(generatedroot.resolve("value/AuthoredDocument.scala"))
      document should include ("import io.circe.Json")
      document should include ("payload: Json")
      document should not include ("model.value.XJson")
      Files.exists(generatedroot.resolve("datatype/ReferenceToken.scala")) shouldBe true
    }

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
      val generated = out.resolve("target/scala-3.3.8/src_managed/main/scala/org/simplemodeling/textus/useraccount/entity/UserProfile.scala")
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
      val generated = out.resolve("target/scala-3.3.8/src_managed/main/scala/org/sample/builtin/entity/BuiltinHolder.scala")
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
      val generated = out.resolve("target/scala-3.3.8/src_managed/main/scala/org/sample/recordbuiltin/value/RecordEnvelope.scala")
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
        content should include ("ModelValueEncoder.encodeC(v).TAKE")
        content should include ("_record_get_as_c[cats.data.NonEmptyVector[org.goldenport.record.Record]](record, INPUT_KEYS_REQUIRED_RECORDS)")
        content should include ("case Some(value) => Consequence.success(value)")
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
      val generated = out.resolve("target/scala-3.3.8/src_managed/main/scala/org/simplemodeling/model/value/Address.scala")
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
      val generated = out.resolve("target/scala-3.3.8/src_managed/main/scala/org/sample/delegateentity/entity/UserProfile.scala")
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
