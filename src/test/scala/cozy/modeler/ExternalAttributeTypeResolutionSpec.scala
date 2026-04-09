package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.Comparator
import org.scalatest.funsuite.AnyFunSuite

/*
 * @since   Apr.  9, 2026
 * @version Apr.  9, 2026
 * @author  ASAMI, Tomoharu
 */
class ExternalAttributeTypeResolutionSpec extends AnyFunSuite {
  test("modeler-scala keeps delegate value composition for external textus UserProfile values") {
    val input = Paths.get("/Users/asami/src/dev2026/textus-user-account/src/main/cozy/user-account.cml").toAbsolutePath.normalize()
    val out = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/test-generated/modeler-scala-user-account-address")
    _delete_recursively(out)
    Files.createDirectories(out.getParent)

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generated = out.resolve("target/scala-3.3.7/src_managed/main/scala/org/simplemodeling/textus/useraccount/entity/UserProfile.scala")
    assert(Files.exists(generated), s"generated file not found: $generated")
    val content = Files.readString(generated)
    assert(content.contains("identityPresentation: Option[IdentityPresentation]"), s"IdentityPresentation delegate was not generated\n$content")
    assert(content.contains("personalProfile: Option[PersonalProfile]"), s"PersonalProfile delegate was not generated\n$content")
    assert(content.contains("organizationSupport: Option[OrganizationSupport]"), s"OrganizationSupport delegate was not generated\n$content")
  }


  test("modeler-scala preserves built-in urn/blob/clob runtime types") {
    val out = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/test-generated/modeler-scala-builtins")
    _delete_recursively(out)
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

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generated = out.resolve("target/scala-3.3.7/src_managed/main/scala/org/sample/builtin/entity/BuiltinHolder.scala")
    assert(Files.exists(generated), s"generated file not found: $generated")
    val content = Files.readString(generated)
    assert(content.contains("resourceUrn: Urn"), s"URN type was collapsed in generated output\n$content")
    assert(content.contains("payload: Option[BinaryBag]"), s"blob type was collapsed in generated output\n$content")
    assert(content.contains("description: Option[TextBag]"), s"clob type was collapsed in generated output\n$content")
  }



  test("modeler-scala uses component package for value models") {
    val out = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/test-generated/modeler-scala-value-package")
    _delete_recursively(out)
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

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generated = out.resolve("target/scala-3.3.7/src_managed/main/scala/org/simplemodeling/model/value/Address.scala")
    assert(Files.exists(generated), s"generated file not found: $generated")
  }

  test("modeler-scala supports DELEGATE section with required/optional composition") {
    val out = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/test-generated/modeler-scala-delegate-entity")
    _delete_recursively(out)
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

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generated = out.resolve("target/scala-3.3.7/src_managed/main/scala/org/sample/delegateentity/entity/UserProfile.scala")
    assert(Files.exists(generated), s"generated file not found: $generated")
    val content = Files.readString(generated)
    assert(content.contains("identityPresentation: IdentityPresentation"), s"required delegate should be generated as non-Option\n$content")
    assert(content.contains("organizationSupport: Option[OrganizationSupport]"), s"optional delegate should be generated as Option\n$content")
  }
  private def _delete_recursively(path: Path): Unit =
    if (Files.exists(path))
      Files.walk(path).sorted(Comparator.reverseOrder()).forEach(Files.delete)
}
