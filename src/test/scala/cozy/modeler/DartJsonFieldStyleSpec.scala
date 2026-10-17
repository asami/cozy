package cozy.modeler

import org.goldenport.kaleidox.{Model => KaleidoxModel}
import org.simplemodeling.model.SimpleModel
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 */
class DartJsonFieldStyleSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "The explicit Dart JSON field profile (DA-22)" should {
    "reject an optional-null omission method collision by original canonical identity (DA-23)" in {
      Given("parsed non-scalar data with a field named toJsonOmittingNullFields")
      val model = DartPackageSpecFixtures.parse(DartJsonFieldStyleSpecFixtures.source.replace(
        "| some-key | string | ? |", "| toJsonOmittingNullFields | string | ? |"))
      val canonical = DartJsonFieldStyleSpecFixtures.canonical(model)
      Vector(DartJsonFieldStyle.Source, DartJsonFieldStyle.CncfSnakeCase).foreach { style =>
        When(s"direct and package generation admit model members under ${style.id}")
        val direct = intercept[IllegalArgumentException](DartModelGenerator.generate(canonical,
          DartModelGenerator.Options(jsonFieldStyle = style)))
        val packaged = intercept[IllegalArgumentException](DartPackageGenerator.generate(model,
          DartJsonFieldStyleSpecFixtures.options.copy(jsonFieldStyle = style)))
        Then("a structured model-member collision retains the original canonical field identity before output")
        Vector(direct, packaged).foreach { failure =>
          failure.getMessage should include("dart.profile.ProfileEnvelope.toJsonOmittingNullFields")
          failure.getMessage should include("reserved model member")
        }
      }
    }

    "preserve source keys, Dart symbols and canonical identities for default and explicit Source callers" in {
      Given("parsed canonical nested data and the same original canonical objects used by direct and package callers")
      val model = DartJsonFieldStyleSpecFixtures.model
      val canonical = DartJsonFieldStyleSpecFixtures.canonical(model)
      val sourceoptions = DartModelGenerator.Options(jsonFieldStyle = DartJsonFieldStyle.Source)
      val packageoptions = DartJsonFieldStyleSpecFixtures.options.copy(sealedTypeNames = Set.empty)
      When("default and explicit Source model/package entry points render their selected closure")
      val direct = DartPackageSpecFixtures.text(DartModelGenerator.generate(canonical), "lib/src/models.dart")
      val explicit = DartPackageSpecFixtures.text(DartModelGenerator.generate(canonical, sourceoptions), "lib/src/models.dart")
      val packaged = DartPackageSpecFixtures.products(DartPackageGenerator.generate(model, packageoptions))
      val explicitpackage = DartPackageSpecFixtures.products(DartPackageGenerator.generate(model,
        packageoptions.copy(jsonFieldStyle = DartJsonFieldStyle.Source)))
      val projection = DartModelProjection.select(canonical, sourceoptions)
      Then("both entry points agree exactly and canonical objects and source field identities stay intact")
      DartModelGenerator.Options().jsonFieldStyle shouldBe DartJsonFieldStyle.Source
      DartJsonFieldStyleSpecFixtures.options.jsonFieldStyle shouldBe DartJsonFieldStyle.Source
      direct shouldBe explicit
      packaged shouldBe explicitpackage
      packaged("lib/src/models.dart") shouldBe direct
      direct should include("final int sourceCount;")
      direct should include("final String? someKey;")
      direct should include("final String class_;")
      direct should include("'sourceCount':")
      direct should include("'some-key':")
      direct should include("\"identity\":\"dart.profile.ProfileEnvelope.sourceCount\",\"wireName\":\"sourceCount\"")
      projection.objects.foreach { source =>
        (canonical.elements.find(_.qualifiedName == source.qualifiedName).get.asInstanceOf[AnyRef] eq source) shouldBe true
        source.attributes.foreach(field => projection.wireName(source, field) shouldBe field.name)
      }
    }

    "select only exact identifiers and match selected runtime acronym, separator, keyword and trim spellings" in {
      Given("the public profile identifiers and independent selected-runtime spelling examples")
      val examples = Vector("sourceCount" -> "source_count", "readySourceCount" -> "ready_source_count",
        "source-count" -> "source_count", "source_count" -> "source_count", "source__count" -> "source_count",
        "URLValue" -> "u_r_l_value", "class" -> "class", "some-key" -> "some_key", " sourceCount " -> "source_count")
      When("the exact Source and CNCF identifiers select a field naming policy")
      val source = DartJsonFieldStyle.parse("source")
      val selected = DartJsonFieldStyle.parse("cncf-snake-case")
      val names = examples.map { case (original, _) => selected.fieldName(original) }
      Then("selected naming agrees with runtime examples and Source retains every original byte of each name")
      source shouldBe DartJsonFieldStyle.Source
      selected shouldBe DartJsonFieldStyle.CncfSnakeCase
      source.id shouldBe "source"
      selected.id shouldBe "cncf-snake-case"
      names shouldBe examples.map(_._2)
      examples.foreach { case (original, _) => source.fieldName(original) shouldBe original }
      Vector("", " ", "source ", " cncf-snake-case", "snake-case", "Source", "cncf_snake_case").foreach { invalid =>
        Given(s"the unadmitted explicit profile identifier '$invalid'")
        When("the public parser processes it")
        val failure = intercept[IllegalArgumentException](DartJsonFieldStyle.parse(invalid))
        Then("a structured invalid argument identifies the profile instead of choosing a fallback")
        failure.getMessage should include("json-field-style")
      }
    }

    "use selected keys throughout the parsed object closure while preserving scalar and finite discriminator values" in {
      Given("parsed required, optional, nested and list fields plus scalar wrappers and finite enum/sealed classifications")
      val model = DartJsonFieldStyleSpecFixtures.model
      val options = DartJsonFieldStyleSpecFixtures.options.copy(jsonFieldStyle = DartJsonFieldStyle.CncfSnakeCase)
      When("the package renders the explicitly selected profile")
      val products = DartPackageSpecFixtures.products(DartPackageGenerator.generate(model, options))
      val generated = products("lib/src/models.dart")
      Then("encode, decode and wire metadata share transformed keys while Dart names and original source metadata survive")
      Vector("source_count", "some_key", "detail_text", "primary_item", "item_list", "item_name", "state_code", "shape_code").foreach { key =>
        generated should include(s"'$key':")
        generated should include(s""""wireName":"$key"""")
      }
      Vector("source_count", "primary_item", "item_list", "item_name", "state_code", "shape_code").foreach { key =>
        generated should include(s"_json, '$key'")
      }
      generated should include("_json['some_key']")
      generated should include("_json['detail_text']")
      generated should include("final List<ProfileItem> itemList;")
      generated should include("final ProfileText? detailText;")
      generated should include("\"identity\":\"dart.profile.ProfileEnvelope.sourceCount\"")
      generated should include("\"type\":\"int\",\"multiplicity\":\"1\"")
      generated should include("enum ProfileState")
      generated should include("sealed class ProfileShape")
      generated should include("case 'readyNow':")
      generated should include("case 'roundShape':")
      generated should include("String get wireName => 'roundShape';")
      generated should include("Object? toJson() => _dartJson.DartJsonCodec.string(value,")
      products("README.md") should include("JSON field profile: `cncf-snake-case`")
    }

    "reject transformed key collisions by original canonical identity before symbol admission or emission" in {
      Given("parsed fields sourceCount and source-count that collide under the selected runtime profile")
      val model = DartPackageSpecFixtures.parse(DartJsonFieldStyleSpecFixtures.source.replace(
        "| some-key | string | ? |", "| source-count | string | ? |"))
      val canonical = DartJsonFieldStyleSpecFixtures.canonical(model)
      When("direct and package generation select the colliding profile")
      val direct = intercept[IllegalArgumentException](DartModelGenerator.generate(canonical,
        DartModelGenerator.Options(jsonFieldStyle = DartJsonFieldStyle.CncfSnakeCase)))
      val packaged = intercept[IllegalArgumentException](DartPackageGenerator.generate(model,
        DartJsonFieldStyleSpecFixtures.options.copy(jsonFieldStyle = DartJsonFieldStyle.CncfSnakeCase)))
      Then("both fail before returning a Realm with the same transformed key and both original field identities")
      Vector(direct, packaged).foreach { failure =>
        failure.getMessage should include("duplicate JSON wire key 'source_count'")
        failure.getMessage should include("dart.profile.ProfileEnvelope.sourceCount")
        failure.getMessage should include("dart.profile.ProfileEnvelope.source-count")
        failure.getMessage should not include "normalized field"
      }
    }

    "render deterministically under reordered elements and selectors without leaking a selected profile to later default callers" in {
      Given("the parsed canonical closure and two root selectors in either order")
      val model = DartJsonFieldStyleSpecFixtures.model
      val canonical = DartJsonFieldStyleSpecFixtures.canonical(model)
      val roots = Vector("dart.profile.ProfileEnvelope", "dart.profile.ProfileShape")
      Vector(DartJsonFieldStyle.Source, DartJsonFieldStyle.CncfSnakeCase).foreach { style =>
        Given(s"an explicit ${style.id} selection over the same canonical source")
        val options = DartModelGenerator.Options(roots, Set("dart.profile.ProfileShape"), style)
        val packageoptions = DartJsonFieldStyleSpecFixtures.options.copy(typeNames = roots, jsonFieldStyle = style)
        When("repeated direct and package generation reorders canonical elements, divisions and selectors")
        val first = DartModelGenerator.generate(canonical, options)
        val repeated = DartModelGenerator.generate(canonical, options)
        val reordered = DartModelGenerator.generate(SimpleModel(canonical.elements.reverse), options.copy(typeNames = roots.reverse))
        val packaged = DartPackageSpecFixtures.products(DartPackageGenerator.generate(model, packageoptions))
        val packageagain = DartPackageSpecFixtures.products(DartPackageGenerator.generate(model.copy(divisions = model.divisions.reverse),
          packageoptions.copy(typeNames = roots.reverse)))
        Then("both source products and complete packages are identical for that selected policy")
        Vector("lib/src/models.dart", "lib/src/json.dart").foreach { path =>
          DartPackageSpecFixtures.text(first, path) shouldBe DartPackageSpecFixtures.text(repeated, path)
          DartPackageSpecFixtures.text(first, path) shouldBe DartPackageSpecFixtures.text(reordered, path)
        }
        packaged shouldBe packageagain
      }
      When("later callers omit profile selection after CNCF generation")
      val directdefault = DartPackageSpecFixtures.text(DartModelGenerator.generate(canonical), "lib/src/models.dart")
      val packagedefault = DartPackageSpecFixtures.products(DartPackageGenerator.generate(model, DartJsonFieldStyleSpecFixtures.options))
      Then("the default still emits sourceCount and some-key rather than selected wire keys")
      Vector(directdefault, packagedefault("lib/src/models.dart")).foreach { generated =>
        generated should include("'sourceCount':")
        generated should include("'some-key':")
        generated should not include "'source_count':"
      }
      Given("fixed-seed variations of canonical element order, selector order and the explicit field policy")
      val variations = for {
        style <- Gen.oneOf[DartJsonFieldStyle](Vector(DartJsonFieldStyle.Source, DartJsonFieldStyle.CncfSnakeCase))
        seed <- Gen.choose(0, 10000)
        reversed <- Gen.oneOf(false, true)
      } yield (style, seed, reversed)
      val property = Prop.forAll(variations) { case (style, seed, reversed) =>
        val options = DartModelGenerator.Options(roots, Set("dart.profile.ProfileShape"), style)
        val shuffled = SimpleModel(new scala.util.Random(seed).shuffle(canonical.elements))
        val expected = DartPackageSpecFixtures.text(DartModelGenerator.generate(canonical, options), "lib/src/models.dart")
        val actual = DartPackageSpecFixtures.text(DartModelGenerator.generate(shuffled,
          options.copy(typeNames = if (reversed) roots.reverse else roots)), "lib/src/models.dart")
        expected == actual && DartJsonFieldStyleSpecFixtures.canonical(model).elements == canonical.elements
      }
      When("ScalaCheck generates independent element permutations and selector/policy combinations")
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(24).withInitialSeed(Seed(65422L)), property)
      Then("every generated ordering preserves the same selected output and original canonical state")
      withClue(checked.toString) { checked.passed shouldBe true }
    }
  }
}

private[modeler] object DartJsonFieldStyleSpecFixtures {
  def source: String = """# VALUE
    |
    |## ProfileText
    |package = dart.profile
    |### ATTRIBUTE
    || name | type | multiplicity |
    || --- | --- | --- |
    || value | string | 1 |
    |
    |## ProfileItem
    |package = dart.profile
    |### ATTRIBUTE
    || name | type | multiplicity |
    || --- | --- | --- |
    || itemName | string | 1 |
    |
    |## ProfileEnvelope
    |package = dart.profile
    |### ATTRIBUTE
    || name | type | multiplicity |
    || --- | --- | --- |
    || sourceCount | int | 1 |
    || class | string | 1 |
    || some-key | string | ? |
    || detailText | dart.profile.ProfileText | ? |
    || primaryItem | dart.profile.ProfileItem | 1 |
    || itemList | dart.profile.ProfileItem | * |
    || stateCode | dart.profile.ProfileState | 1 |
    || shapeCode | dart.profile.ProfileShape | 1 |
    |
    |# POWERTYPE
    |
    |## ProfileState
    |package = dart.profile
    |### readyNow
    |value = 1
    |### waiting
    |value = 2
    |
    |## ProfileShape
    |package = dart.profile
    |### roundShape
    |value = 1
    |### square
    |value = 2
    |""".stripMargin

  def model: KaleidoxModel = DartPackageSpecFixtures.parse(source)
  def options: DartPackageGenerator.Options = DartPackageGenerator.Options("phase65_profile",
    sealedTypeNames = Set("dart.profile.ProfileShape"))
  def canonical(model: KaleidoxModel): SimpleModel =
    DartCanonicalInputProjection.enrich(model, Modeler.ModelBuilder(model)).buildValue()
}
