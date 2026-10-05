package cozy.modeler

import java.nio.file.Paths
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.realm.Realm
import org.goldenport.record.v2.{MOne => _, MZeroOne => _, MZeroMore => _, MOneMore => _, _}
import org.goldenport.values.Designation
import org.simplemodeling.model._
import org.simplemodeling.model.domain.{MDomainComponent, MDomainResource, MDomainStateMachine, MDomainValue}
import org.smartdox.Description

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
class DartModelGenerationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "The Dart data-stage interface" should {
    "retain canonical models (DA-01, DA-07, DA-12)" which {
      "project actual CML plus canonical nominal, structured and entity fixtures" in {
        Given("the real Kaleidox CML loader and native canonical data objects")
        val model = DartModelSpecFixtures.model
        When("the built canonical record and independent entity identifiers are inspected by exact identity")
        val records = model.elements.collect { case source: MObject if source.qualifiedName == "dart.fixture.DartRecord" => source }
        val entities = model.elements.collect { case source: MObject if source.qualifiedName == "dart.fixture.DartEntity" => source }
        Then("the CML primitive id and typed recordId remain distinct required canonical fields")
        records should have size 1
        entities should have size 1
        val id = records.head.attributes.filter(_.name == "id")
        val recordid = records.head.attributes.filter(_.name == "recordId")
        val entityid = entities.head.attributes.filter(_.name == "id")
        id should have size 1
        recordid should have size 1
        entityid should have size 1
        id.head.attributeType shouldBe MDataType(XString)
        id.head.multiplicity shouldBe MOne
        recordid.head.attributeType shouldBe MObjectAttributeType(MObjectRef(DartModelSpecFixtures.packageRef, "DartText"))
        recordid.head.multiplicity shouldBe MOne
        And("the native entity independently retains its modeled identifier wrapper")
        entityid.head.attributeType shouldBe MObjectAttributeType(MObjectRef(DartModelSpecFixtures.packageRef, "DartText"))
        entityid.head.multiplicity shouldBe MOne
        When("the data-stage entry constructs its complete in-memory Realm")
        val realm = DartModelGenerator.generate(model, DartModelSpecFixtures.options)
        val models = DartModelSpecFixtures.text(realm, "lib/src/models.dart")
        Then("exactly the model and technical JSON sources preserve typed immutable fields")
        realm.get("lib/src/models.dart") shouldBe defined
        realm.get("lib/src/json.dart") shouldBe defined
        models should include("class DartText")
        models should include("final String id;")
        models should include("final DartText recordId;")
        models should include("final DartText id;")
        models should include("final DartText? detail;")
        models should include("final List<DartText> members;")
        models should include("class DartEntity")
        models should include("class DartStructured")
        models should include("class DartByteValue")
        models should include("required int count")
        models should include("List<String> tags = const []")
        models should include("'code':")
        models should include("'street':")
        models should not include "dynamic"
      }

    }

    "resolve only the exact selected canonical closure (DA-02)" which {
      "include transitive typed dependencies and tolerate unrelated unsupported kinds" in {
        Given("a typed root and an unrelated opaque object")
        val text = DartModelSpecFixtures.value("Text", List(DartModelSpecFixtures.field("value", MDataType(XString))))
        val root = DartModelSpecFixtures.value("Root", List(DartModelSpecFixtures.field("text", MObjectAttributeType(text.toObjectRef))))
        val unrelated = DartModelSpecFixtures.value("Unrelated", List(DartModelSpecFixtures.field("date", MDataType(XDate))))
        val model = SimpleModel(Vector(root, text, unrelated, MObject(DartModelSpecFixtures.packageRef, "Opaque")))
        When("the root is selected by exact qualified identity")
        val output = DartModelSpecFixtures.text(DartModelGenerator.generate(model,
          DartModelGenerator.Options(typeNames = Vector(root.qualifiedName))), "lib/src/models.dart")
        Then("only the root and its referenced named wrapper are generated")
        output should include("class Root")
        output should include("class Text")
        output should not include "Opaque"
        output should not include "Unrelated"
        And("default selection validates every data candidate's constituents")
        intercept[IllegalArgumentException](DartModelGenerator.generate(model)).getMessage should include(unrelated.qualifiedName)
      }

      "select exact qualified roots without choosing between duplicated short names" in {
        Given("two source types sharing a short name in different packages")
        val first = DartModelSpecFixtures.value("Shared", Nil)
        val second = first.copy(affiliation = MPackageRef("another.fixture"))
        val model = SimpleModel(Vector(first, second))
        When("one qualified root and then an unresolved short root are requested")
        val output = DartModelSpecFixtures.text(DartModelGenerator.generate(model,
          DartModelGenerator.Options(Vector(first.qualifiedName))), "lib/src/models.dart")
        val failure = intercept[IllegalArgumentException](DartModelGenerator.generate(model,
          DartModelGenerator.Options(Vector("Shared"))))
        Then("only the exact selected source emits and short-name first-match resolution is absent")
        output should include("dart.fixture.Shared")
        output should not include "another.fixture.Shared"
        failure.getMessage should include("Shared")
      }

      "resolve default-package references without a leading-dot path assumption" in {
        Given("a root referencing a default-package canonical value")
        val target = DartModelSpecFixtures.value("DefaultText", Nil).copy(affiliation = MPackageRef.default)
        val root = DartModelSpecFixtures.value("DefaultOwner", List(DartModelSpecFixtures.field("text", MObjectAttributeType(target.toObjectRef))))
        When("the packageRef/objectName identity is traversed")
        val output = DartModelSpecFixtures.text(DartModelGenerator.generate(SimpleModel(Vector(root, target))), "lib/src/models.dart")
        Then("the reference retains the default-package target's typed identity")
        output should include("final DefaultText text;")
      }

      "admit a finite cyclic typed closure without inventing a JSON cycle codec" in {
        Given("two supported values with optional references to each other")
        val first = DartModelSpecFixtures.value("First", List(DartModelSpecFixtures.field("second",
          MObjectAttributeType(MObjectRef(DartModelSpecFixtures.packageRef, "Second")), MZeroOne)))
        val second = DartModelSpecFixtures.value("Second", List(DartModelSpecFixtures.field("first",
          MObjectAttributeType(first.toObjectRef), MZeroOne)))
        When("the first value selects the finite dependency closure")
        val output = DartModelSpecFixtures.text(DartModelGenerator.generate(SimpleModel(Vector(first, second)),
          DartModelGenerator.Options(Vector(first.qualifiedName))), "lib/src/models.dart")
        Then("both declarations preserve typed nullable references")
        output should include("final Second? second;")
        output should include("final First? first;")
      }

      "fail missing, ambiguous and unresolved selected identities before a Realm exists" in {
        Given("missing roots, duplicated canonical identities and an unresolved selected field")
        val value = DartModelSpecFixtures.value("Root", Nil)
        val missingref = value.copy(attributes = List(DartModelSpecFixtures.field("unknown",
          MObjectAttributeType(MObjectRef.create("dart.fixture.Missing")))))
        val cases = Vector(
          (SimpleModel(Vector(value)), DartModelGenerator.Options(Vector("dart.fixture.Missing")), "dart.fixture.Missing"),
          (SimpleModel(Vector(value, value)), DartModelGenerator.Options(Vector(value.qualifiedName)), value.qualifiedName),
          (SimpleModel(Vector(missingref)), DartModelGenerator.Options(), "dart.fixture.Root.unknown"))
        cases.foreach { case (model, options, location) =>
          When(s"the invalid closure at $location is requested")
          val failure = intercept[IllegalArgumentException](DartModelGenerator.generate(model, options))
          Then("the structured fault vocabulary carries the precise qualified source location")
          failure.getMessage should include(location)
        }
      }
    }

    "reject selected semantics without a fallback (DA-03)" which {
      "reject unsupported primitive kinds, ranges and derived expressions" in {
        Given("canonical fields whose semantics this target does not admit")
        val fields = Vector(XDate, XDecimal, XEntityId).map(datatype => DartModelSpecFixtures.field("bad", MDataType(datatype))) ++
          Vector(DartModelSpecFixtures.field("bad", MDataType(XString), MMultiplicity(org.goldenport.record.v2.MRange(2, 3))),
            DartModelSpecFixtures.field("bad", MDataType(XString)).copy(derived = Some("count + 1")))
        fields.foreach { field =>
          val model = SimpleModel(Vector(DartModelSpecFixtures.value("Unsupported", List(field))))
          When("the unsupported field is selected")
          val failure = intercept[IllegalArgumentException](DartModelGenerator.generate(model))
          Then("a qualified failure replaces neither the field nor its type")
          failure.getMessage should include("dart.fixture.Unsupported.bad")
        }
      }

      "reject inheritance, traits and every unsupported relationship responsibility" in {
        Given("canonical objects with independently unsupported semantic attachments")
        val root = DartModelSpecFixtures.value("Root", Nil)
        val pkg = DartModelSpecFixtures.packageRef
        val core = MObject.Core(pkg)
        val association = MAssociation(Designation("related"), Description.empty, Some(pkg),
          MObjectRef(pkg, "Related"), MAssociation.Association, MOne, Nil)
        val service = MService.Instance(MElement.Core("Service"), core, MService.Core())
        val fixtures = Vector[MObject](
          root.copy(base = Some(MObjectRef.create("dart.fixture.Base"))),
          root.copy(traits = List(MTraitRef(pkg, "Trait"))),
          root.copy(powertypes = List(MPowertypeRef(pkg, "Kind", MOne)))) ++
          Vector(core.copy(associations = List(association)),
            core.copy(stateMachines = List(MStateMachineRef(pkg, "State"))),
            core.copy(ports = List(MPort(Designation("port"), pkg))),
            core.copy(roles = List(MRoleRef(pkg, "Role"))),
            core.copy(services = List(service)),
            core.copy(rules = List(MRuleRef(pkg, "Rule"))),
            core.copy(vouchers = List(MVoucherRef(pkg, "Voucher")))).map(DartModelSpecFixtures.entityWith)
        fixtures.foreach { source =>
          When("a selected data object carries an unsupported source responsibility")
          val failure = intercept[IllegalArgumentException](DartModelGenerator.generate(SimpleModel(Vector(source))))
          Then("the failure identifies the data object before rendering")
          failure.getMessage should include(source.qualifiedName)
          failure.getMessage should include("unsupported semantics")
        }
      }

      "retain readonly fields and leave canonical operations for the separate operation projection" in {
        Given("a data value with a readonly field and a canonical operation")
        val source = DartModelSpecFixtures.value("OperationalValue", List(
          DartModelSpecFixtures.field("text", MDataType(XString)).copy(readonly = true))).copy(
          operations = List(MOperation.command("ignoredOperation", MParameter.record("input"))))
        When("the data stage projects its fields")
        val output = DartModelSpecFixtures.text(DartModelGenerator.generate(SimpleModel(Vector(source))), "lib/src/models.dart")
        Then("the ordinary field remains immutable and no operation becomes a field")
        output should include("final String text;")
        output should not include "ignoredOperation"
      }

      "exclude nondata kinds by default and fail their explicit selection" in {
        Given("canonical component, state-machine and plain object kinds")
        val pkg = DartModelSpecFixtures.packageRef
        val sources = Vector[MObject](MObject(pkg, "PlainObject"),
          MDomainComponent(Description.name("Component"), MObject.Core(pkg), MComponent.Core(Vector.empty)),
          new MDomainStateMachine(Description.name("StateMachine"), pkg))
        When("default data selection is requested")
        val default = DartModelSpecFixtures.text(DartModelGenerator.generate(SimpleModel(sources)), "lib/src/models.dart")
        Then("the default is empty of domain declarations")
        sources.foreach(source => default should not include source.name)
        sources.foreach { source =>
          When("a nondata kind is explicitly selected")
          val failure = intercept[IllegalArgumentException](DartModelGenerator.generate(SimpleModel(sources),
            DartModelGenerator.Options(Vector(source.qualifiedName))))
          Then("the source kind fails with its qualified identity")
          failure.getMessage should include(source.qualifiedName)
        }
      }
    }

    "represent classification closure and reject symbol ambiguity (DA-08..11)" which {
      "keep finite enum, selected sealed units and open metadata distinct" in {
        Given("the CML finite classifications plus an explicitly open canonical powertype")
        val model = DartModelSpecFixtures.model
        When("finite sealed representation is selected by qualified identity")
        val output = DartModelSpecFixtures.text(DartModelGenerator.generate(model, DartModelSpecFixtures.options), "lib/src/models.dart")
        Then("enhanced enum and final units share exact wire kinds while open names remain extensible")
        output should include("enum DartFinite")
        output should include("sealed class DartSealed")
        output should include("final class DartSealedAlpha extends DartSealed")
        output should include("class DartOpen")
        output should include("'alpha', '1', 'Alpha'")
        output should include("default: return null;")
      }

      "reject sealed selection outside selected finite closure" in {
        Given("an open classification and a finite type absent from explicit root selection")
        val model = DartModelSpecFixtures.model
        val cases = Vector(DartModelGenerator.Options(sealedTypeNames = Set("dart.fixture.DartOpen")),
          DartModelGenerator.Options(Vector("dart.fixture.DartText"), Set("dart.fixture.DartSealed")))
        cases.foreach { options =>
          When("the invalid sealed representation is requested")
          val failure = intercept[IllegalArgumentException](DartModelGenerator.generate(model, options))
          Then("the selected finite boundary is enforced explicitly")
          failure.getMessage should include("sealed selection")
        }
      }

      "reject empty finite sets and duplicate exact or normalized kinds, including open metadata" in {
        Given("empty, duplicate and normalization-colliding classifications")
        val cases = Vector(
          DartModelSpecFixtures.powertype("Empty", Nil),
          DartModelSpecFixtures.powertype("Duplicate", List(MPowertypeKind("same", None), MPowertypeKind("same", None))),
          DartModelSpecFixtures.powertype("Normalized", List(MPowertypeKind("some-name", None), MPowertypeKind("some_name", None))),
          DartModelSpecFixtures.powertype("OpenDuplicate", List(MPowertypeKind("same", None), MPowertypeKind("same", None)), true))
        cases.foreach { source =>
          When("the classification enters the selected data closure")
          val failure = intercept[IllegalArgumentException](DartModelGenerator.generate(SimpleModel(Vector(source))))
          Then("no arbitrary kind wins and the source identity is reported")
          failure.getMessage should include(source.qualifiedName)
        }
      }

      "reject primitive SDK member names before returning a Realm for every supported multiplicity" in {
        val primitives = Vector("int" -> MDataType(XInt), "double" -> MDataType(XDouble), "bool" -> MDataType(XBoolean))
        val multiplicities = Vector[MMultiplicity](MOne, MZeroOne, MZeroMore, MOneMore)
        primitives.foreach { case (name, datatype) =>
          multiplicities.foreach { multiplicity =>
            Given(s"a canonical $name field with ${multiplicity.mark} multiplicity")
            val source = DartModelSpecFixtures.value("PrimitiveMembers", List(
              DartModelSpecFixtures.field(name, datatype, multiplicity)))
            val model = SimpleModel(Vector(source))
            When("the data-stage interface is asked to generate its Realm")
            val failure = intercept[IllegalArgumentException](DartModelGenerator.generate(model))
            Then("generation rejects the reserved model member with its exact canonical source location")
            failure.getMessage should include(s"${source.qualifiedName}.$name")
            failure.getMessage should include("reserved model member")
          }
        }
      }

      "reject normalized type, alternative, member, keyword-escape and SDK collisions" in {
        Given("distinct canonical identities whose emitted symbols cannot coexist")
        val empty = DartModelSpecFixtures.value("some-name", Nil)
        val finite = DartModelSpecFixtures.powertype("Choice", List(MPowertypeKind("alpha", None)))
        val collisionmodels = Vector(
          SimpleModel(Vector(empty, empty.copy(description = Description.name("some_name")))),
          SimpleModel(Vector(empty.copy(description = Description.name("String")))),
          SimpleModel(Vector(empty.copy(description = Description.name("DartJsonCodec")))),
          SimpleModel(Vector(DartModelSpecFixtures.value("Fields", List(
            DartModelSpecFixtures.field("some-name", MDataType(XString)), DartModelSpecFixtures.field("some_name", MDataType(XString)))))),
          SimpleModel(Vector(DartModelSpecFixtures.value("DuplicateFields", List(
            DartModelSpecFixtures.field("same", MDataType(XString)), DartModelSpecFixtures.field("same", MDataType(XString)))))),
          SimpleModel(Vector(DartModelSpecFixtures.value("Reserved", List(DartModelSpecFixtures.field("toJson", MDataType(XString)))))),
          SimpleModel(Vector(empty.copy(description = Description.name("Function")), empty.copy(description = Description.name("FunctionValue")))))
        collisionmodels.foreach { model =>
          When("collisions are checked after normalization and keyword escaping")
          val failure = intercept[IllegalArgumentException](DartModelGenerator.generate(model))
          Then("generation fails without selecting or renaming a winner")
          failure.getMessage should (include("collid") or include("duplicate") or include("reserved"))
        }
        When("a sealed alternative collides with another canonical type")
        val failure = intercept[IllegalArgumentException](DartModelGenerator.generate(SimpleModel(Vector(finite,
          DartModelSpecFixtures.value("ChoiceAlpha", Nil))), DartModelGenerator.Options(sealedTypeNames = Set(finite.qualifiedName))))
        Then("global alternative symbols also participate in collision admission")
        failure.getMessage should include("ChoiceAlpha")
      }
    }

    "render deterministic escaped source identities" which {
      "retain identical bytes under ScalaCheck permutations of input declaration order" in {
        Given("all fixture declarations and reproducible permutations")
        val model = DartModelSpecFixtures.model
        val expected = DartModelSpecFixtures.products(DartModelGenerator.generate(model, DartModelSpecFixtures.options))
        val orders = Gen.listOfN(model.elements.size, Gen.chooseNum(0, 1000000)).map { ranks =>
          model.elements.zip(ranks).sortBy(_._2).map(_._1)
        }
        When("ScalaCheck generates each permuted canonical input with unchanged options")
        val property = Prop.forAll(orders) { elements =>
          DartModelSpecFixtures.products(DartModelGenerator.generate(SimpleModel(elements), DartModelSpecFixtures.options)) == expected
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(50).withInitialSeed(Seed(652L)), property)
        Then("every declaration permutation yields the same two LF-only products")
        withClue(result.toString) { result.passed shouldBe true }
        expected.keySet shouldBe Set("lib/src/models.dart", "lib/src/json.dart")
        expected.values.foreach(_ should not include "\r")
      }

      "normalize ASCII acronyms and keywords while escaping literal text without data loss" in {
        Given("keyword, acronym, punctuation and escaped source strings")
        val source = DartModelSpecFixtures.value("Function", List(DartModelSpecFixtures.field("class", MDataType(XString))))
        When("the source is normalized for Dart declarations and exact literals")
        val output = DartModelSpecFixtures.text(DartModelGenerator.generate(SimpleModel(Vector(source))), "lib/src/models.dart")
        val literal = DartNaming.literal("'\\$\n\t日本")
        Then("identifier escaping does not alter raw wire keys and literal interpolation is escaped")
        output should include("class FunctionValue")
        output should include("final String class_;")
        output should include("'class':")
        DartNaming.typeName("HTTP-response_ID", "fixture") shouldBe "HttpResponseId"
        DartNaming.fileName("HTTPResponse-ID", "fixture") shouldBe "http_response_id"
        literal shouldBe "'\\'\\\\\\$\\n\\t日本'"
        Vector("", "123-name", "日本").foreach { name =>
          intercept[IllegalArgumentException](DartNaming.typeName(name, "fixture")).getMessage should include("fixture")
        }
      }
    }
  }
}

private[modeler] object DartModelSpecFixtures {
  val packageRef: MPackageRef = MPackageRef("dart.fixture")
  val options: DartModelGenerator.Options = DartModelGenerator.Options(sealedTypeNames = Set("dart.fixture.DartSealed"))

  def field(name: String, attributeType: MAttributeType, multiplicity: MMultiplicity = MOne): MAttribute =
    MAttribute(Designation(name), attributeType, multiplicity, Nil, None)

  def value(name: String, fields: List[MAttribute]): MDomainValue =
    MDomainValue(Description.name(name), packageRef, Nil, None, Nil, Nil, fields, Nil)

  def powertype(name: String, kinds: List[MPowertypeKind], isKnowledge: Boolean = false): MPowertype =
    MPowertype(Description.name(name), packageRef, kinds, isKnowledge = isKnowledge)

  def entityWith(core: MObject.Core): MEntity = new MEntity with MObject.Core.Holder with MElement.Core.Holder {
    val objectCore: MObject.Core = core
    val elementCore: MElement.Core = MElement.Core("AttachedEntity")
    override def ports: List[MPort] = objectCore.ports
    override def roles: List[MRoleRef] = objectCore.roles
    override def services: List[MService] = objectCore.services
    override def rules: List[MRuleRef] = objectCore.rules
    override def vouchers: List[MVoucherRef] = objectCore.vouchers
  }

  def model: SimpleModel = {
    val input = Paths.get("src/test/resources/modeler/dart-models.cml").toAbsolutePath
    val canonical = new Modeler().buildValueModel(KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile))
    val nominal = Vector("DartByteValue" -> XByte, "DartShortValue" -> XShort, "DartIntValue" -> XInt,
      "DartSafeIntValue" -> XLong, "DartIntegerValue" -> XInteger, "DartFloatValue" -> XFloat,
      "DartDoubleValue" -> XDouble).map { case (name, datatype) =>
      MNominalDataType(Description.name(name), packageRef, datatype, Nil)
    }
    val structured = MStructuredDataType(Description.name("DartStructured"), packageRef, Nil, None, Nil, Nil,
      List(field("title", MDataType(XString)), field("address", MObjectAttributeType(MObjectRef(packageRef, "DartAddress")))), Nil)
    val entity = MDomainResource(Description.name("DartEntity"), packageRef, Nil, None, Nil, Nil,
      List(field("id", MObjectAttributeType(MObjectRef(packageRef, "DartText"))), field("enabled", MDataType(XBoolean))), Nil, Nil, Nil)
    val optional = value("DartOptionalValue", List(field("value", MDataType(XString), MZeroOne)))
    val numbers = value("DartNumbers", List(field("integers", MDataType(XInt), MZeroMore),
      field("doubles", MDataType(XDouble), MOneMore)))
    val keyword = value("Function", List(field("class", MDataType(XString)), field("some-key", MDataType(XString))))
    val open = powertype("DartOpen", List(MPowertypeKind("alpha", Some("1"), Some("Alpha"))), true)
    val escaped = powertype("DartEscaped", List(MPowertypeKind("quoted-kind", Some("'\\$\n日本"), Some("Label ' \\ $\t日本"))))
    val fallback = powertype("DartFallback", List(MPowertypeKind("name-only", None)))
    val constraints = List(LiteralConstraint("pattern", "quote'\\$\n日本"))
    val constrained = MNominalDataType(Description.name("ConstrainedText"), packageRef, XString, constraints)
    val constrainedfield = field("text", MObjectAttributeType(constrained.toObjectRef)).copy(
      constraints = List(LiteralConstraint("min_length", "3")), typeConstraints = constraints, readonly = true)
    val container = value("Container", List(constrainedfield))
    canonical.add(nominal ++ Vector(structured, entity, optional, numbers, keyword, open, escaped, fallback, constrained, container))
  }

  def text(realm: Realm, path: String): String = realm.get(path).collect {
    case value: Realm.StringData => value.string
  }.getOrElse(throw new IllegalStateException(s"Missing generated product: $path"))

  def products(realm: Realm): Map[String, String] = Vector("lib/src/models.dart", "lib/src/json.dart").
    map(path => path -> text(realm, path)).toMap
}
