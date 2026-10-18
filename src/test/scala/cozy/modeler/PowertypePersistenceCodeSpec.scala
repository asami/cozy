package cozy.modeler

import java.nio.file.{Files, Paths}
import org.goldenport.cli.Environment
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.record.v2.XString
import org.goldenport.values.Designation
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model._
import org.simplemodeling.model.domain.MDomainResource
import scala.util.Try

/*
 * @since   Oct. 19, 2026
 * @version Oct. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class PowertypePersistenceCodeSpec extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  "POWERTYPE persistence-code admission" should {
    "preserve explicit model-owned codes" which {
      "accept zero and both Short boundaries without interpreting zero as absence" in {
        Given("a persisted State with explicit minimum, zero, and maximum codes")
        val source = _powertype("State", Vector("minimum" -> Some(Short.MinValue.toString), "zero" -> Some("0"), "maximum" -> Some(Short.MaxValue.toString))) + _entity("State", "1")

        When("the ordinary CML parser and ModelBuilder admit the model")
        val model = _build(source)

        Then("the semantic declaration retains all three exact codes")
        _codes(model) shouldBe Map("minimum" -> Some(Short.MinValue.toString), "zero" -> Some("0"), "maximum" -> Some(Short.MaxValue.toString))
      }

      "preserve every generated Short code under source reordering and label changes" in {
        Given("a finite vocabulary whose explicitly assigned codes are distinct")
        val property = Prop.forAll(Gen.choose(Short.MinValue.toInt, Short.MaxValue.toInt - 1)) { code =>
          val kinds = Vector("ready" -> Some(code.toString), "pending" -> Some((code + 1).toString))
          val before = _build(_powertype("State", kinds, "Before") + _entity("State", "1"))
          val after = _build(_powertype("State", kinds.reverse, "Changed label") + _entity("State", "1"))
          _codes(before) == _codes(after) && _codes(after)("ready").contains(code.toString)
        }

        When("the model is rebuilt across arbitrary Short assignments, reordered kinds, and changed labels")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(40), property)

        Then("explicit kind-to-code identity is stable")
        withClue(result.toString) { result.passed shouldBe true }
      }
    }

    "require complete codes at persistent uses" which {
      "retain transient Int codes but reject their out-of-Short-range persistent reuse" in {
        Given("explicit transient Int codes outside the signed16 persistence range")
        val codes = Vector(Int.MinValue, Short.MinValue.toInt - 1, Short.MaxValue.toInt + 1, Int.MaxValue)
        val vocabularies = codes.map(code => _powertype("State", Vector("ready" -> Some(code.toString))))
        val transient = vocabularies.map(_build)

        When("each declaration is reused directly or under an optional or repeated Entity attribute")
        val errors = for {
          vocabulary <- vocabularies
          multiplicity <- Vector("1", "?", "*", "+")
        } yield intercept[Exception] { _build(vocabulary + _entity("State", multiplicity)) }

        Then("transient declarations retain their Int correspondence and persistence diagnoses the XShort use")
        transient.zip(codes).foreach { case (model, code) =>
          _codes(model) shouldBe Map("ready" -> Some(code.toString))
        }
        errors.foreach { error =>
          error.getMessage should include("State")
          error.getMessage should include("ready")
          error.getMessage should include("XShort")
          error.getMessage should include("Ticket.state")
        }
      }

      "reject nested and explicitly imported out-of-range persistence codes" in {
        Given("valid transient declarations with a code immediately outside either signed16 boundary")
        val nested = _powertype("State", Vector("ready" -> Some("32768"))) +
          "\n# VALUE\n\n## Envelope\n\n### ATTRIBUTE\n\n| name | type | multiplicity |\n| --- | --- | --- |\n| status | State | ? |\n" + _entity("Envelope", "*")
        val entity = _build(_entity("imported.State", "?"))
        val imported = _build(_powertype("State", Vector("ready" -> Some("-32769")), pkg = "imported"))
        val local = _build(_powertype("State", Vector("local" -> Some("9")), pkg = "domain.entity"))

        When("resolved admission traverses the nested value and the explicitly imported declaration")
        val nestederror = intercept[Exception] { _build(nested) }
        val importederror = intercept[Exception] {
          PowertypePersistenceCodes.requireValid(SimpleModel(entity.elements ++ local.elements ++ imported.elements))
        }

        Then("both diagnostics retain their actual owning type, kind and persistent attribute path")
        nestederror.getMessage should include("Ticket.state -> domain.value.Envelope.status")
        nestederror.getMessage should include("32768")
        importederror.getMessage should include("imported.State")
        importederror.getMessage should include("-32769")
        Vector(nestederror, importederror).foreach { error =>
          error.getMessage should include("ready")
          error.getMessage should include("Ticket.state")
          error.getMessage should include("XShort")
        }
      }

      "accept transient omission and reject direct, optional, and repeated Entity reuse" in {
        Given("a display vocabulary with no explicitly written code")
        val vocabulary = _powertype("State", Vector("ready" -> None))
        val transient = _build(vocabulary)

        When("the same declaration is reused by each persistent attribute multiplicity")
        val errors = Vector("1", "?", "*", "+").map { multiplicity =>
          intercept[Exception] { _build(vocabulary + _entity("State", multiplicity)) }
        }

        Then("omission stays absent in the transient model and every persistent use identifies its path")
        _codes(transient) shouldBe Map("ready" -> None)
        errors.foreach { error =>
          error.getMessage should include("State")
          error.getMessage should include("ready")
          error.getMessage should include("Ticket.state")
        }
      }

      "report the complete Entity-to-VALUE attribute path" in {
        Given("a persisted Envelope VALUE containing an optional vocabulary with a missing code")
        val source = _powertype("State", Vector("ready" -> None)) +
          "\n# VALUE\n\n## Envelope\n\n### ATTRIBUTE\n\n| name | type | multiplicity |\n| --- | --- | --- |\n| status | State | ? |\n" + _entity("Envelope", "*")

        When("ModelBuilder follows the actual nested declaration reference")
        val error = intercept[Exception] { _build(source) }

        Then("the missing-code diagnostic retains both attribute edges and the kind")
        error.getMessage should include("Ticket.state -> domain.value.Envelope.status")
        error.getMessage should include("State")
        error.getMessage should include("ready")
      }

      "follow declared MDataType references while respecting resolveDeclaredType" in {
        Given("actual parsed Entity and POWERTYPE declarations joined by a declared datatype attribute")
        val vocabulary = _build(_powertype("State", Vector("ready" -> None)))
        val entity = _ticket(_build(_entity("string", "?")))
        val datatype = MDataType(Designation("State"), XString, MPackageRef.default)
        val attribute = entity.attributes.find(_.name == "state").get.copy(attributeType = datatype)
        val persisted = entity.copy(attributes = List(attribute))
        val model = vocabulary.add(Vector(persisted))

        When("validation traverses the declared type and separately checks a primitive attribute")
        val error = intercept[Exception] { PowertypePersistenceCodes.requireValid(model) }
        val primitive = persisted.copy(attributes = List(attribute.copy(attributeType = datatype.copy(resolveDeclaredType = false))))
        PowertypePersistenceCodes.requireValid(vocabulary.add(Vector(primitive)))

        Then("only the declaration-resolving attribute requires a complete code mapping")
        error.getMessage should include("Ticket.state")
        error.getMessage should include("ready")
      }

      "visit inherited base and trait attributes and stop recursive VALUE traversal" in {
        Given("an Entity inheriting an actual attribute-bearing declaration with a recursive value graph")
        val vocabulary = _build(_powertype("State", Vector("ready" -> None)))
        val holdermodel = _build("# VALUE\n\n## Holder\n\n### ATTRIBUTE\n\n| name | type | multiplicity |\n| --- | --- | --- |\n| state | State | 1 |\n")
        val holder = holdermodel.elements.collectFirst { case p: MValue => p }.get
        val entity = _ticket(_build(_entity("string", "1"))).copy(attributes = Nil)
        val base = entity.copy(base = Some(holder.toObjectRef))
        val mixin = entity.copy(traits = List(MTraitRef(holder.affiliation, holder.name)))
        val recursive = _build("# VALUE\n\n## Loop\n\n### ATTRIBUTE\n\n| name | type | multiplicity |\n| --- | --- | --- |\n| next | Loop | ? |\n" + _entity("Loop", "1"))

        When("validation follows both inheritance routes and the recursive declaration")
        val errors = Vector(base, mixin).map { child =>
          intercept[Exception] { PowertypePersistenceCodes.requireValid(vocabulary.add(Vector(holder, child))) }
        }
        PowertypePersistenceCodes.requireValid(recursive)

        Then("inherited persistence uses are rejected and recursion remains finite")
        errors.foreach(_.getMessage should include("Holder.state"))
      }
    }

    "resolve actual declarations without unrelated short-name guesses" which {
      "require the explicitly imported cross-package declaration, even beside another State" in {
        Given("an Entity referring explicitly to imported.State and a separate local State vocabulary")
        val entity = _build(_entity("imported.State", "?"))
        val imported = _build(_powertype("State", Vector("ready" -> None), pkg = "imported"))
        val local = _build(_powertype("State", Vector("local" -> Some("9")), pkg = "domain.entity"))
        val model = SimpleModel(entity.elements ++ local.elements ++ imported.elements)

        When("admission resolves the explicit qualified reference")
        val error = intercept[Exception] { PowertypePersistenceCodes.requireValid(model) }

        Then("the diagnostic identifies the imported declaration and persistent use")
        error.getMessage should include("imported.State")
        error.getMessage should include("ready")
        error.getMessage should include("Ticket.state")
      }

      "keep unresolved qualified external types isolated from local short names" in {
        Given("an unresolved external.State reference and a local transient State without codes")
        val entity = _build(_entity("external.State", "1"))
        val local = _build(_powertype("State", Vector("ready" -> None), pkg = "domain.entity"))
        val model = SimpleModel(entity.elements ++ local.elements)

        When("admission and the public Scala generator consume the actual declarations")
        PowertypePersistenceCodes.requireValid(model)
        val generated = new ScalaGenerator(Environment.createJaJp(), model).generate(model.root).tree
        val source = generated.get("target/scala-3.3.8/src_managed/main/scala/domain/entity/Ticket.scala")
          .collect { case data: org.goldenport.realm.Realm.StringData => data.string }
          .getOrElse(fail("Missing generated Ticket source"))

        Then("the unrelated local vocabulary stays transient and the explicit foreign application type is preserved")
        _codes(model) shouldBe Map("ready" -> None)
        source should include("import external.State")
        source should include("state: State")
        source should include("EntityStoreFieldBinding.application[external.State]")
        source should not include "domain.entity.State.numericCodeCodec"
      }

      "walk packaged declarations and resolve an unambiguous short reference" in {
        Given("actual Entity and imported POWERTYPE declarations inside package containers")
        val entity = _ticket(_build(_entity("State", "1")))
        val vocabulary = _build(_powertype("State", Vector("ready" -> None), pkg = "imported"))
        val wrapped = SimpleModel(Vector(MPackage.default(entity +: vocabulary.elements)))

        When("admission traverses package contents and resolves the sole State declaration")
        val error = intercept[Exception] { PowertypePersistenceCodes.requireValid(wrapped) }

        Then("the imported code obligation remains visible")
        error.getMessage should include("imported.State")
        error.getMessage should include("Ticket.state")
      }
    }

    "reject invalid declarations before generation" which {
      "retain parser diagnostics for malformed and overflowing explicit values" in {
        Given("explicit nonnumeric, fractional, and overflowing CML persistence codes")
        val parsed = Vector("unknown", "1.5", "2147483648", "-2147483649").map { raw =>
          KaleidoxModel.parse(KaleidoxConfig.default, _powertype("State", Vector("ready" -> Some(raw))))
        }

        When("ModelBuilder receives each parser result before taking submodels")
        val errors = parsed.map { model => intercept[Exception] { Modeler.ModelBuilder(model).build() } }

        Then("original contextual parser messages survive instead of yielding an apparently usable model")
        parsed.zip(errors).foreach { case (model, error) =>
          model.errors.nonEmpty shouldBe true
          model.errors.foreach(message => error.getMessage should include(message.en))
        }
      }

      "reject duplicate codes from the ordinary CML route even without an Entity use" in {
        Given("a transient vocabulary with two explicit assignments to the same numeric code")
        val source = _powertype("State", Vector("ready" -> Some("7"), "pending" -> Some("7")))

        When("the ordinary parser result enters ModelBuilder")
        val error = intercept[Exception] { _build(source) }

        Then("duplicate explicit assignments are rejected with their owning type")
        error.getMessage should include("State")
        error.getMessage.toLowerCase should include("duplicate")
      }

      "reject duplicate numeric codes including signed aliases in direct semantic declarations" in {
        Given("a parsed valid model changed by a direct semantic caller to duplicate or invalid explicit codes")
        val original = _build(_powertype("State", Vector("ready" -> Some("1"), "pending" -> Some("2"))))
        val powertype = original.elements.collectFirst { case p: MPowertype => p }.get
        val invalid = Vector("+1", "1.5", "bad", "2147483648", null).map { raw =>
          SimpleModel(Vector(powertype.copy(kinds = List(MPowertypeKind("ready", Some("1")), MPowertypeKind("pending", Some(raw))))))
        }

        When("direct semantic callers invoke the public Scala generation boundary")
        val errors = invalid.map { model =>
          intercept[Exception] { new ScalaGenerator(Environment.createJaJp(), model).generate(model.root) }
        }

        Then("every invalid mapping fails before a usable generated realm can be returned")
        errors.foreach(_.getMessage should include("State"))
        errors.head.getMessage should include("duplicate")
        errors.head.getMessage should include("pending")
        errors.last.getMessage should include("invalid explicit")
        errors.last.getMessage should include("pending")
      }

      "produce numeric codec bindings through the public modeler route and no invalid Scala product" in {
        Given("valid and missing-code CML input files under the generated-test directory")
        val base = Paths.get(sys.props("user.dir")).resolve("target/test-generated/powertype-persistence-code")
        val input = base.resolve("valid.cml")
        val output = base.resolve("valid")
        val invalidinput = base.resolve("invalid.cml")
        val invalidoutput = base.resolve("invalid")
        delete_recursively(base)
        val kinds = Vector("ready" -> Some("0"), "pending" -> Some("2"))
        write_file(input, _powertype("State", kinds) + _entity("State", "?"))
        write_file(invalidinput, _powertype("State", Vector("ready" -> None)) + _entity("State", "?"))

        When("Cozy generates both models through modeler-scala")
        val log = run_modeler_scala(input, output)
        val invalidresult = Try(run_modeler_scala(invalidinput, invalidoutput))
        val products = tree_snapshot(output).filter(_._1.endsWith(".scala"))

        Then("the valid Entity has its declared numeric codec and the invalid model has no Scala product")
        withClue(log) { products.nonEmpty shouldBe true }
        products.filter(_._1.endsWith("/Ticket.scala")).map(_._2).mkString("\n") should include("State.numericCodeCodec")
        products.filter(_._1.endsWith("/Ticket.scala")).map(_._2).mkString("\n") should include("org.goldenport.schema.XShort, EntityStoreScalarRepresentation.Number")
        tree_snapshot(invalidoutput).filter(_._1.endsWith(".scala")) shouldBe empty
        withClue(invalidresult.toString) { Files.exists(invalidoutput.resolve("target/scala-3.3.8/src_managed/main/scala/domain/entity/Ticket.scala")) shouldBe false }
      }
    }
  }

  private def _build(source: String): SimpleModel =
    Modeler.ModelBuilder(KaleidoxModel.parse(KaleidoxConfig.default, source)).build()

  private def _ticket(model: SimpleModel): MDomainResource =
    model.elements.collectFirst { case p: MDomainResource if p.name == "Ticket" => p }.get

  private def _codes(model: SimpleModel): Map[String, Option[String]] =
    model.elements.collectFirst { case p: MPowertype => p }.get.kinds.map(p => p.name -> p.value).toMap

  private def _entity(typename: String, multiplicity: String): String =
    s"\n# ENTITY\n\n## Ticket\n\n### ATTRIBUTE\n\n| name | type | multiplicity |\n| --- | --- | --- |\n| id | entityid | 1 |\n| state | $typename | $multiplicity |\n"

  private def _powertype(
    name: String,
    kinds: Vector[(String, Option[String])],
    label: String = "Display",
    pkg: String = "domain"
  ): String = {
    val rows = kinds.map { case (kind, code) => s"| $kind | ${code.getOrElse("")} | $label |" }.mkString("\n")
    s"\n# POWERTYPE\n\n## $name\n\npackage = $pkg\n\n| name | value | label |\n| --- | --- | --- |\n$rows\n"
  }
}
