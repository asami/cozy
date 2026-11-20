package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Nov. 16, 2026
 * @version Nov. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class ModelerNestedValueCodecSpec extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  "CML nested values" should {
    "generate codecs for a transitive composite used by an entity" in {
      Given("a model containing optional and ordered nested values")
      val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      val out = base.resolve("target/test-generated/modeler-nested-value-codecs")
      delete_recursively(out)
      Files.createDirectories(out)
      val input = out.resolve("nested.cml")
      val cml = """# COMPONENT
                  |
                  |## NestedCodec
                  |
                  |### PACKAGE
                  |
                  |domain
                  |
                  |# VALUE
                  |
                  |## Leaf
                  |
                  |### ATTRIBUTE
                  |
                  || name | type | multiplicity |
                  ||------|------|--------------|
                  || text | string | 1 |
                  |
                  |## Branch
                  |
                  |### ATTRIBUTE
                  |
                  || name | type | multiplicity |
                  ||------|------|--------------|
                  || leaf | Leaf | 1 |
                  |
                  |## Envelope
                  |
                  |### ATTRIBUTE
                  |
                  || name | type | multiplicity |
                  ||------|------|--------------|
                  || optional | Branch | ? |
                  || ordered | Branch | * |
                  |
                  |# ENTITY
                  |
                  |## Document
                  |
                  |### ATTRIBUTE
                  |
                  || name | type | multiplicity |
                  ||------|------|--------------|
                  || id | entityid | 1 |
                  || target | Envelope | 1 |
                  |""".stripMargin
      Files.write(input, cml.getBytes(StandardCharsets.UTF_8))

      When("Cozy generates the Scala source")
      cozy.Cozy.main(Array("modeler-scala", input.toString, "--save", out.toString))

      Then("each nested value declares a codec")
      val root = out.resolve("target/scala-3.3.8/src_managed/main/scala/domain/value")
      Vector("Leaf", "Branch", "Envelope").foreach { name =>
        Files.readString(root.resolve(s"$name.scala")) should include ("derives Codec.AsObject")
      }
      val envelope = Files.readString(root.resolve("Envelope.scala"))
      envelope should include ("Option[Branch]")
      envelope should include ("Vector[Branch]")
      val entity = Files.readString(root.getParent.resolve("entity/Document.scala"))
      entity should include ("EntityStoreFieldBinding.application[domain.value.Envelope]")
    }
  }
}
