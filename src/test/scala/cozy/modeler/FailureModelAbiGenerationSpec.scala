package cozy.modeler

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import org.goldenport.cli.Environment
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.realm.Realm
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, JsNull, JsNumber, JsObject, JsString, JsValue, Json}

/*
 * @since   Oct.  3, 2026
 * @version Oct. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class FailureModelAbiGenerationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
  private val _resources = _base.resolve("src/test/resources/modeler")
  private val _reader_projection_amendment =
    Json.parse(Files.readString(_resources.resolve("failure-model-reader-projection-amendment.json"))).as[JsObject]
  private val _root = "target/scala-3.3.8/src_managed/main/scala/cozy/generated/failuremodel"
  private val _scala_path = s"${_root}/FailureModelMetadata.scala"
  private val _probe_path = s"${_root}/FailureModelConsumerProbe.scala"
  private val _json_path = "target/cozy/failure-model.json"
  private val _workflow_root = "target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow"
  private val _escaped = "quote\"slash\\line\nreturn\rtab\tback\bform\fcontrol\u0000\u0001\u0085審査😀\u2028\u2029"

  "Failure Model generated development ABI" should {
    "retain complete real inputs on normal and value routes" in {
      Given("the accepted local single-user single-writer CML with component, service, operation and sibling contracts")
      val input = _resources.resolve("failure-model.cml")
      val definitions = FailureModelCml.requireDefinitions(_load(input))
      val outputs = Vector("modeler-scala" -> _output("normal"), "modeler-scala-value" -> _output("value"))

      When("each public CLI route generates twice and actual metadata receives a typed consumer probe")
      val products = outputs.flatMap { case (route, output) =>
        _run(route, input, output)
        val first = _products(output)
        _run(route, input, output)
        val second = _products(output)
        _write(output.resolve(_probe_path), _probe(mixed = false))
        Vector(first, second)
      }
      val decoded = Json.parse(Files.readString(outputs.head._2.resolve(_json_path)))
      val actual = (decoded \ "components").as[Vector[JsValue]].map(_decode_component)
      val component = actual.head
      val store = component.services.find(_.identity == "Store").get
      val save = store.operations.find(_.identity == "save").get
      val remove = store.operations.find(_.identity == "remove").get
      val archive = component.services.find(_.identity == "Archive").get

      Then("every new product agrees byte for byte and the decoded complete IR preserves ordered effective chains")
      products.foreach(_ shouldBe products.head)
      actual shouldBe definitions
      _expect_order(decoded, _expected(definitions))
      Files.readString(outputs.head._2.resolve(_scala_path)) shouldBe _realm_string(FailureModelAbiGenerator.generate(definitions), _scala_path)
      component.executionModel.defaults.map(x => x.identity -> x.scope.name) shouldBe Vector(
        "malformed-input" -> "IN_SCOPE", "concurrent-write" -> "OUT_OF_SCOPE", "network-partition" -> "OUT_OF_SCOPE")
      _scopes(component.effective) shouldBe Vector("IN_SCOPE", "IN_SCOPE", "OUT_OF_SCOPE")
      _scopes(store.effective) shouldBe Vector("IN_SCOPE", "OUT_OF_SCOPE", "OUT_OF_SCOPE")
      _scopes(save.effective) shouldBe Vector("IN_SCOPE", "OUT_OF_SCOPE", "IN_SCOPE")
      save.effective.entries(1).origins.map(_.scope.name) shouldBe Vector("OUT_OF_SCOPE", "IN_SCOPE", "OUT_OF_SCOPE")
      save.effective.entries(1).winningOrigin shouldBe store.delta.entries.head.source
      save.effective.entries(2).winningOrigin shouldBe save.delta.entries.head.source
      remove.delta.source shouldBe None
      remove.effective.entries shouldBe store.effective.entries
      archive.delta.source shouldBe None
      archive.effective.entries shouldBe component.effective.entries
      archive.operations.head.effective.entries shouldBe component.effective.entries
      component.source.sectionPath shouldBe "COMPONENT/LocalStore"
      component.executionSelectionSource.sectionPath shouldBe "COMPONENT/LocalStore/EXECUTION-MODEL"
      save.delta.entries.head.source.sectionPath shouldBe "SERVICE/Store/OPERATION/save/FAILURE-MODEL/network-partition"
      component.source.line shouldBe Some(_line(Files.readString(input), "## LocalStore"))
      save.source.line shouldBe Some(_line(Files.readString(input), "#### save"))
    }

    "preserve original products with declared source identity, operation metadata and reader/projection amendments when declarations are absent" in {
      Given("the immutable SOURCE baseline with removed source hashes, the selected fixture path, accepted operation content and fixed reader/projection amendments")
      val baseline = Json.parse(Files.readString(_resources.resolve("failure-model-legacy-baseline.json")))
      val groups = (baseline \ "groups").as[Vector[JsObject]]
      val expected = groups.map(group => _legacy_expected(group, _base.resolve((group \ "fixture").as[String])))

      When("the four original fixture and route groups generate into fresh output directories twice")
      val actual = groups.zipWithIndex.map { case (group, index) =>
        val input = _base.resolve((group \ "fixture").as[String])
        val route = (group \ "route").as[String]
        val outputs = Vector(1, 2).map { repetition =>
          val output = _output(s"legacy-$index-$repetition")
          _run(route, input, output)
          Then("generated JSON provenance equals the normalized actual input path")
          val metadata = Json.parse(Files.readString(output.resolve("target/cozy/model-metadata.json")))
          (metadata \ "source" \ "path").as[String] shouldBe input.toAbsolutePath.normalize().toString
          _all_bytes(output)
        }
        (FailureModelCml.requireDefinitions(_load(input)), outputs)
      }
      val empty = FailureModelAbiGenerator.generate(Vector.empty)
      val emptyjson = Json.parse(FailureModelAbiGenerator.canonicalJson(Vector.empty))

      Then("all inventories and UTF-8 bytes equal the independently adapted baseline and empty input adds no artifact")
      groups.size shouldBe 4
      expected.map(_.size).sum shouldBe 80
      (baseline \ "producerCommit").as[String] shouldBe "6c15b65cd83181fdf61aa8674c9a847f79751805"
      actual.zip(expected).foreach { case ((definitions, outputs), files) =>
        definitions shouldBe Vector.empty
        outputs.foreach { bytes =>
          bytes.keySet shouldBe files.keySet
          bytes shouldBe files
          bytes.keySet should not contain _json_path
          bytes.keySet should not contain _scala_path
        }
      }
      empty.get(_scala_path) shouldBe None
      empty.get(_json_path) shouldBe None
      emptyjson shouldBe Json.obj("schemaVersion" -> "cozy.failure-model.v1", "components" -> Json.arr())
    }

    "adapt only selected source identity, four accepted operation content declarations and declared Component readers" in {
      Given("the historical Workflow fixture and a selected source path containing spaces and Unicode")
      val baseline = Json.parse(Files.readString(_resources.resolve("failure-model-legacy-baseline.json")))
      val groups = (baseline \ "groups").as[Vector[JsObject]]
      val group = groups(2)
      val files = (group \ "files").as[Vector[JsObject]].map(file =>
        (file \ "path").as[String] -> (file \ "text").as[String]).toMap
      val source = _base.resolve("target/relocated checkout/審査/workflow-semantic-outcome.cml")
      val jsonpath = "target/cozy/model-metadata.json"
      val yamlpath = "target/cozy/model-metadata.yaml"
      val componentpath = "target/scala-3.3.8/src_managed/main/scala/domain/DomainComponent.scala"
      val original = Json.parse(files(jsonpath)).as[JsObject]
      val originalsource = (original \ "source").as[JsObject]
      val originalpath = (originalsource \ "path").as[String]
      val annotated = original + ("unrelatedSourceText" -> JsString(originalpath))

      When("the historical expected products are adapted without consulting newly generated content")
      val json = Json.parse(new String(_legacy_expected_bytes(jsonpath, Json.prettyPrint(annotated), source).toArray, UTF_8))
      val yaml = new String(_legacy_expected_bytes(yamlpath, files(yamlpath), source).toArray, UTF_8)
      val component = new String(_legacy_expected_bytes(componentpath, files(componentpath), source).toArray, UTF_8)
      val unchanged = _legacy_expected_bytes("unrelated.txt", originalpath, source)

      Then("only source.path is rebound, the obsolete source hash is removed and all unrelated metadata remains exact")
      json shouldBe annotated + ("source" -> ((originalsource - "sha256") + ("path" -> JsString(source.toString))))
      yaml should include(s"  path: ${Json.stringify(JsString(source.toString))}\n")
      yaml should not include "  sha256:"
      new String(unchanged.toArray, UTF_8) shouldBe originalpath

      And("the exact four operation content additions compose with only the four declared Component reader amendments")
      val additions = Vector("beginReview", "submitReview", "supplyInput", "reviseChange").map(name =>
        s"""          content = org.goldenport.value.BaseContent.Builder("$name"),""")
      val replacements = (_reader_projection_amendment \ "replacements").as[Vector[JsObject]]
        .filterNot(replacement => (replacement \ "symbol").as[String] == "_to_external_value")
      val declared = replacements.foldLeft(files(componentpath)) { (current, replacement) =>
        current.replace((replacement \ "before").as[String], (replacement \ "after").as[String])
      }
      replacements.size shouldBe 4
      component.linesIterator.filterNot(additions.contains).mkString("\n") shouldBe declared.linesIterator.mkString("\n")
      additions.foreach(line => component.linesIterator.count(_ == line) shouldBe 1)
      component should include("_record_get_as_c[Record](record, keys)")
      component should include("ValueReader.vector(vr, allowSingle = true), ctx)")
      component should not include "private def _record_get_vector_of_record_c"
      component.replace("allowSingle = true", "allowSingle = false") should not be component
      component.replace("(vr, allowSingle = true), ctx)", "(vr, allowSingle = true), otherContext)") should not be component
      component.replace("BaseContent.Builder(\"beginReview\")", "BaseContent.Builder(\"wrongOperation\")") should not be component
      component.replace("\"ReviewResult\"", "\"ChangedResult\"") should not be component
    }

    "apply five declared value amendments or four Component reader amendments and reject missing or duplicate historical anchors" in {
      Given("the independent historical producer and fixed before/after blocks for two value paths and two Component reader paths")
      val baseline = Json.parse(Files.readString(_resources.resolve("failure-model-legacy-baseline.json")))
      val groups = (baseline \ "groups").as[Vector[JsObject]]
      val paths = (_reader_projection_amendment \ "applicablePaths").as[Vector[String]]
      val readerpaths = (_reader_projection_amendment \ "readerOnlyPaths").as[Vector[String]]
      val replacements = (_reader_projection_amendment \ "replacements").as[Vector[JsObject]]
      val files = groups.flatMap(group => (group \ "files").as[Vector[JsObject]])
      val drift = "\n// arbitrary source drift\n"

      And("the amendment retains historical provenance and declares exactly two value paths, two Component paths and five helper changes")
      (_reader_projection_amendment \ "historicalProducerCommit").as[String] shouldBe (baseline \ "producerCommit").as[String]
      paths.toSet shouldBe Set(
        "target/scala-3.3.8/src_managed/main/scala/domain/value/CountryCode.scala",
        "target/scala-3.3.8/src_managed/main/scala/domain/value/ReviewContext.scala")
      paths.size shouldBe 2
      readerpaths.toSet shouldBe Set(
        "src/main/scala/domain/impl/ComponentFactory.scala",
        "target/scala-3.3.8/src_managed/main/scala/domain/DomainComponent.scala")
      readerpaths.size shouldBe 2
      replacements.map(replacement => (replacement \ "symbol").as[String]) shouldBe Vector(
        "_to_external_value", "_record_get_record", "_record_get_vector_as_c",
        "_record_get_vector_as_context_c", "_record_get_vector_of_record_c")

      (paths ++ readerpaths).foreach { path =>
        Given(s"the raw historical source and its declared value or Component reader amendment at $path")
        val raw = (files.find(file => (file \ "path").as[String] == path).get \ "text").as[String]
        val selected = if (paths.contains(path)) replacements
          else replacements.filterNot(replacement => (replacement \ "symbol").as[String] == "_to_external_value")
        val declared = selected.foldLeft(raw) { (current, replacement) =>
          current.replace((replacement \ "before").as[String], (replacement \ "after").as[String])
        }

        When("the fixed amendment is applied to declared, unrelated and independently drifted source")
        val amended = _legacy_reader_projection(path, raw)
        val unrelated = _legacy_reader_projection("unrelated/" + path, raw)
        val drifted = _legacy_reader_projection(path, raw + drift)

        Then("only the selected five value blocks or four Component reader blocks change and all other source drift stays decisive")
        selected.size shouldBe (if (paths.contains(path)) 5 else 4)
        amended shouldBe declared
        amended shouldBe _current_conversion_delegates(path, raw)
        unrelated shouldBe raw
        drifted shouldBe declared + drift
        drifted should not be amended

        selected.foreach { replacement =>
          Given("one declared historical block whose presence must be unique")
          val before = (replacement \ "before").as[String]
          val symbol = (replacement \ "symbol").as[String]
          val absent = raw.replace(before, "")
          val duplicate = raw + before

          When("the declared path contains a missing or duplicated historical block")
          val missingerror = intercept[IllegalArgumentException](_legacy_reader_projection(path, absent))
          val duplicateerror = intercept[IllegalArgumentException](_legacy_reader_projection(path, duplicate))

          Then("both invalid anchors are rejected with the affected path and helper identity")
          Vector(missingerror, duplicateerror).foreach { error =>
            error.getMessage should include(path)
            error.getMessage should include(symbol)
          }
        }
      }
    }

    "bind historical expected metadata to one hundred independent locations while preserving every other byte" taggedAs org.scalatest.tagobjects.Slow in {
      Given("four historical raw groups and independently chosen input locations containing spaces and Unicode")
      val baseline = Json.parse(Files.readString(_resources.resolve("failure-model-legacy-baseline.json")))
      val groups = (baseline \ "groups").as[Vector[JsObject]]
      val locations = Gen.chooseNum(1, 1000000).map(suffix =>
        _base.resolve("target/test-generated/legacy-oracle-location-" + suffix + " 日本語"))
      val metadatapath = "target/cozy/model-metadata.json"
      val metadatapaths = Set(metadatapath, "target/cozy/model-metadata.yaml")
      val componentpath = "target/scala-3.3.8/src_managed/main/scala/domain/DomainComponent.scala"
      val operations = Vector("beginReview", "submitReview", "supplyInput", "reviseChange")
      val valuepaths = (_reader_projection_amendment \ "applicablePaths").as[Set[String]]
      val readerpaths = (_reader_projection_amendment \ "readerOnlyPaths").as[Set[String]]
      val replacements = (_reader_projection_amendment \ "replacements").as[Vector[JsObject]]

      When("ScalaCheck binds expected input paths, fixed Operation content and declared value or Component reader blocks across all four groups")
      val property = Prop.forAll(locations) { location =>
        groups.zipWithIndex.foreach { case (group, index) =>
          val fixture = (group \ "fixture").as[String]
          val input = location.resolve(fixture)
          val original = (group \ "files").as[Vector[JsObject]].map(file =>
            (file \ "path").as[String] -> (file \ "text").as[String].getBytes(UTF_8).toVector).toMap
          val expected = _legacy_expected(group, input)
          val metadata = Json.parse(original(metadatapath).toArray).as[JsObject]
          val source = (metadata \ "source").as[JsObject]
          val boundmetadata = metadata + ("source" -> ((source - "sha256") + ("path" -> JsString(input.toAbsolutePath.normalize().toString))))
          Json.parse(expected(metadatapath).toArray) shouldBe boundmetadata
          expected.keySet shouldBe original.keySet
          val componentchanged = fixture == "src/test/resources/modeler/workflow-semantic-outcome.cml" &&
            (group \ "route").as[String] == "modeler-scala"
          val amendedvaluepaths = original.keySet.intersect(valuepaths)
          val amendedreaderpaths = original.keySet.intersect(readerpaths)
          val amendedpaths = amendedvaluepaths ++ amendedreaderpaths
          amendedvaluepaths.size shouldBe 1
          amendedreaderpaths shouldBe (if (index == 2) readerpaths else Set.empty[String])
          componentchanged shouldBe (index == 2)
          val changedpaths = metadatapaths ++ amendedpaths ++ (if (componentchanged) Set(componentpath) else Set.empty[String])
          original.filterNot { case (path, _) => changedpaths.contains(path) }.foreach { case (path, bytes) =>
            expected(path) shouldBe _current_conversion_delegates(path, new String(bytes.toArray, UTF_8)).getBytes(UTF_8).toVector
          }
          amendedpaths.foreach { path =>
            val raw = new String(original(path).toArray, UTF_8)
            val selected = if (valuepaths.contains(path)) replacements
              else replacements.filterNot(replacement => (replacement \ "symbol").as[String] == "_to_external_value")
            val readers = selected.foldLeft(raw) { (current, replacement) =>
              current.replace((replacement \ "before").as[String], (replacement \ "after").as[String])
            }
            val declared = if (componentchanged && path == componentpath)
              operations.foldLeft(readers) { (current, name) =>
                val anchor = s"""val specification = OperationDefinition.Specification.Builder("$name").
        copy(
"""
                current.replace(anchor, anchor + s"""          content = org.goldenport.value.BaseContent.Builder("$name"),
""")
              }
            else readers
            expected(path) shouldBe declared.getBytes(UTF_8).toVector
          }
          if (componentchanged) {
            val component = new String(expected(componentpath).toArray, UTF_8)
            val contentlines = operations.map(name => s"""          content = org.goldenport.value.BaseContent.Builder("$name"),""")
            val raw = new String(original(componentpath).toArray, UTF_8)
            val declared = replacements.filterNot(replacement => (replacement \ "symbol").as[String] == "_to_external_value")
              .foldLeft(raw) { (current, replacement) =>
                current.replace((replacement \ "before").as[String], (replacement \ "after").as[String])
              }
            contentlines.foreach(line => component.linesIterator.count(_ == line) shouldBe 1)
            component.linesIterator.filterNot(contentlines.contains).mkString("", "\n", "\n").getBytes(UTF_8).toVector shouldBe
              declared.getBytes(UTF_8).toVector
          }
        }
        true
      }
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withInitialSeed(Seed(73L)), property)

      Then("one hundred reproducible samples preserve all bytes outside source identity, fixed operation content and the declared five value or four Component reader blocks, without discards")
      withClue(result.toString) { result.passed shouldBe true }
      result.succeeded should be >= 100
      result.discarded shouldBe 0
    }

    "retain null, optional provenance and safely escaped pure IR values" in {
      Given("complete typed IR containing quoted, backslash, Unicode and control text with absent and present locations")
      val component = _sample(_escaped, Vector(FailureScope.OutOfScope, FailureScope.InScope, FailureScope.OutOfScope, FailureScope.InScope), None)
      val definitions = Vector(component, component.copy(identity = "second", source = component.source.copy(line = Some(7))))

      When("the same immutable input is projected twice into canonical JSON and self-contained Scala")
      val json = FailureModelAbiGenerator.canonicalJson(definitions)
      val decoded = Json.parse(json)
      val scala = _realm_string(FailureModelAbiGenerator.generate(definitions), _scala_path)
      val repeated = _realm_string(FailureModelAbiGenerator.generate(definitions), _scala_path)
      val roundtrip = (decoded \ "components").as[Vector[JsValue]].map(_decode_component)

      Then("ordered complete values survive decoding and Scala literals escape text without manufacturing an identifier")
      roundtrip shouldBe definitions
      _expect_order(decoded, _expected(definitions))
      json shouldBe FailureModelAbiGenerator.canonicalJson(definitions)
      scala shouldBe repeated
      scala should include(_literal(_escaped))
      scala should include("FailureModelSourceIdentity(")
      scala should include("None")
      scala should include("Some(7)")
      (decoded \ "components").as[Vector[JsObject]].head.value("source").as[JsObject].value("line") shouldBe JsNull
      roundtrip.head.services.last.delta.source shouldBe None
      roundtrip.head.services.head.delta.source shouldBe Some(component.services.head.source)
      roundtrip.head.services.head.operations.head.effective.entries.head.winningOrigin.line shouldBe None
    }

    "roundtrip one hundred deterministic ordered contracts with semantic counterexamples" in {
      Given("independent execution, component, service and operation scopes, locations and escaped identity samples")
      val scope = Gen.oneOf(FailureScope.InScope: FailureScope, FailureScope.OutOfScope: FailureScope)
      val samples = for {
        suffix <- Gen.chooseNum(1, 1000000)
        scopes <- Gen.listOfN(4, scope)
        line <- Gen.option(Gen.chooseNum(1, 5000))
      } yield (_escaped + suffix, scopes.toVector, line)

      When("ScalaCheck compares complete decoded IR, every recursive field order and independent sibling inheritance")
      val property = Prop.forAll(samples) { case (identity, scopes, line) =>
        val component = _sample(identity, scopes, line)
        val definitions = Vector(component, component.copy(identity = identity + "-second"))
        val text = FailureModelAbiGenerator.canonicalJson(definitions)
        val json = Json.parse(text)
        val decoded = (json \ "components").as[Vector[JsValue]].map(_decode_component)
        val leaf = decoded.head.services.head.operations.head.effective.entries.head
        val sibling = decoded.head.services.last.effective.entries.head
        val wrongscope = component.copy(effective = component.effective.copy(entries = component.effective.entries.reverse))
        val wrongorigin = component.copy(services = component.services.map(s => s.copy(effective = component.effective)))
        decoded == definitions && _same_order(json, _expected(definitions)) &&
          text == FailureModelAbiGenerator.canonicalJson(definitions) &&
          leaf.scope == scopes(3) && leaf.origins.map(_.scope) == scopes &&
          leaf.winningOrigin == component.services.head.operations.head.delta.entries.head.source &&
          sibling.scope == scopes(1) && sibling.origins.size == 2 &&
          json != _expected(Vector(wrongscope, definitions.last)) &&
          json != _expected(Vector(wrongorigin, definitions.last))
      }
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withInitialSeed(Seed(72L)), property)

      Then("at least one hundred reproducible samples preserve data and reject reordered entries or substituted owner contracts")
      withClue(result.toString) { result.passed shouldBe true }
      result.succeeded should be >= 100
    }

    "execute old positional and named generator and library policy callers" in {
      Given("the original value-only model and constructor argument positions, labels and defaults")
      val input = _resources.resolve("value-only-literate.dox")
      val builder = Modeler.ModelBuilder(_load(input))
      val model = builder.buildValue()
      val env = Environment.createJaJp()
      val generators = Vector(
        new ScalaGenerator(env, model),
        new ScalaGenerator(env, model, Vector.empty, Vector.empty, ModelGenerationTarget.Cncf),
        new ScalaGenerator(environment = env, model = model),
        new ScalaGenerator(environment = env, model = model, compositeStateMachines = Vector.empty, workflows = Vector.empty,
          generationTarget = ModelGenerationTarget.Cncf),
        new ScalaGenerator(env, model, Vector.empty, Vector.empty, ModelGenerationTarget.Library),
        new ScalaGenerator(environment = env, model = model, generationTarget = ModelGenerationTarget.Library))

      When("all old constructors generate and both library policy methods execute with old positional and named arguments")
      val generated = generators.map(_.generate(model.root).tree)
      ModelGenerationTarget.requireLibraryModel(builder, Vector.empty, Vector.empty)
      ModelGenerationTarget.requireLibraryModel(builder = builder, compositeStateMachines = Vector.empty, workflows = Vector.empty)
      ModelGenerationTarget.requireLibraryGeneratorInput(model, Vector.empty, Vector.empty)
      ModelGenerationTarget.requireLibraryGeneratorInput(model = model, compositeStateMachines = Vector.empty, workflows = Vector.empty)
      ModelGenerationTarget.requireLibrarySource(input)
      val library = _output("library-old-callers")
      _run_library(_resources.resolve("library/address.cml"), library)

      Then("original pure source remains generated and empty defaults introduce no Failure Model files")
      generated.foreach { realm =>
        realm.get(_scala_path) shouldBe None
        realm.get(_json_path) shouldBe None
        _realm_string(realm, "target/scala-3.3.8/src_managed/main/scala/domain/value/CountryCode.scala") should include("CountryCode")
      }
      generated.take(4).map(_realm_string(_, "target/scala-3.3.8/src_managed/main/scala/domain/value/CountryCode.scala")).distinct.size shouldBe 1
      Files.isRegularFile(library.resolve("target/scala-3.3.8/src_managed/main/scala/org/simplemodeling/model/value/Address.scala")) shouldBe true
      Files.exists(library.resolve(_json_path)) shouldBe false
      Files.exists(library.resolve(_scala_path)) shouldBe false
    }

    "reject nonempty Failure Model input through every library admission boundary" in {
      Given("a nonempty accepted Failure Model vector and the unchanged pure value library model")
      val input = _resources.resolve("failure-model.cml")
      val definitions = FailureModelCml.requireDefinitions(_load(input))
      val builder = Modeler.ModelBuilder(_load(_resources.resolve("value-only-literate.dox")))
      val model = builder.buildValue()

      When("model, generator-input, direct generator, source and actual value CLI library routes admit the declarations")
      val errors = Vector(
        intercept[Exception](ModelGenerationTarget.requireLibraryModel(builder, Vector.empty, Vector.empty, definitions)),
        intercept[Exception](ModelGenerationTarget.requireLibraryGeneratorInput(model, Vector.empty, Vector.empty, definitions)),
        intercept[Exception](new ScalaGenerator(Environment.createJaJp(), model, generationTarget = ModelGenerationTarget.Library,
          failureModels = definitions).generate(model.root)),
        intercept[Exception](ModelGenerationTarget.requireLibrarySource(input)),
        intercept[Exception](_run_library(input, _output("library-rejection"))))

      Then("every route names the existing unsupported-runtime error and the additive failure-model category")
      errors.foreach { error =>
        error.getMessage should include("LIBRARY_GENERATION_UNSUPPORTED_RUNTIME")
        error.getMessage should include("failure-model")
      }
      errors.take(3).foreach(_.getMessage should include("category=failure-model"))
      Files.exists(_output("library-rejection").resolve(_json_path)) shouldBe false
    }

    "coexist with actual Workflow v5 products in the original mixed source" in {
      Given("the original Domain, ReviewService and submitReview declarations with only scoped Failure Model additions")
      val original = Files.readString(_resources.resolve("workflow-semantic-outcome.cml"))
      val input = _base.resolve("target/test-generated/failure-model-mixed.cml")
      val source = _mixed_source(original)
      _write(input, source)
      val model = _load(input)
      val definitions = FailureModelCml.requireDefinitions(model)
      val workflows = CompositeStateMachineCml.workflowDefinitions(model)
      val originalworkflows = CompositeStateMachineCml.workflowDefinitions(_load(_resources.resolve("workflow-semantic-outcome.cml")))
      val outputs = Vector("modeler-scala" -> _output("mixed"), "modeler-scala-value" -> _output("mixed-value"))
      val workflowpaths = Vector("StateMachineWorkflowAbi.scala", "ReviewWorkflowStateMachineWorkflow1.scala",
        "StateMachineWorkflowComponentFactoryBootstrap.scala").map(name => s"${_workflow_root}/$name")

      When("normal and value CLI routes generate the combined real source twice with a typed mixed consumer")
      val products = outputs.flatMap { case (route, output) =>
        _run(route, input, output)
        val first = _products(output)
        _run(route, input, output)
        val second = _products(output)
        _write(output.resolve(_probe_path), _probe(mixed = true))
        Vector(first, second)
      }
      val realm = StateMachineWorkflowAbiGenerator.generate(workflows)
      val component = definitions.head
      val service = component.services.head
      val submit = service.operations.find(_.identity == "submitReview").get

      Then("the new metadata has no route drift and unchanged Workflow v5 declarations, value source and source correlation coexist")
      products.foreach(_ shouldBe products.head)
      definitions.map(_.identity) shouldBe Vector("Domain")
      component.services.map(_.identity) shouldBe Vector("ReviewService")
      service.operations.map(_.identity) shouldBe Vector("beginReview", "submitReview", "supplyInput", "reviseChange")
      _scopes(component.effective) shouldBe Vector("IN_SCOPE", "IN_SCOPE", "OUT_OF_SCOPE")
      _scopes(service.effective) shouldBe Vector("IN_SCOPE", "OUT_OF_SCOPE", "OUT_OF_SCOPE")
      _scopes(submit.effective) shouldBe Vector("IN_SCOPE", "OUT_OF_SCOPE", "IN_SCOPE")
      workflows.head.providedOperations.map(_.operation.name) should contain allElementsOf Vector("beginReview", "submitReview")
      workflows.head.providedOperations.foreach(operation => service.operations.map(_.identity) should contain(operation.operation.name))
      val workflowjson = Json.parse(StateMachineWorkflowAbiGenerator.canonicalJson(workflows))
      _without_sources(workflowjson) shouldBe _without_sources(Json.parse(StateMachineWorkflowAbiGenerator.canonicalJson(originalworkflows)))
      (workflowjson \ "schemaVersion").as[String] shouldBe "cozy.cml.statemachine-workflow-abi.v5"
      outputs.foreach { case (_, output) =>
        val json = Json.parse(Files.readString(output.resolve(_json_path)))
        (json \ "components").as[Vector[JsValue]].map(_decode_component) shouldBe definitions
        _expect_order(json, _expected(definitions))
        workflowpaths.foreach(path => Files.readString(output.resolve(path)) shouldBe _realm_string(realm, path))
        Json.parse(Files.readString(output.resolve(StateMachineWorkflowAbiGenerator.metadataPath))) shouldBe workflowjson
        val metadata = Json.parse(Files.readString(output.resolve("target/cozy/model-metadata.json")))
        (metadata \ "source" \ "path").as[String] shouldBe input.toAbsolutePath.normalize().toString
        Files.readString(output.resolve("target/scala-3.3.8/src_managed/main/scala/domain/value/ReviewContext.scala")) shouldBe
          _legacy_text("workflow-semantic-outcome.cml", "modeler-scala", "target/scala-3.3.8/src_managed/main/scala/domain/value/ReviewContext.scala")
      }
      submit.delta.entries.head.source.sectionPath shouldBe "SERVICE/ReviewService/OPERATION/submitReview/FAILURE-MODEL/network-partition"
    }
  }

  private def _load(path: Path): KaleidoxModel = KaleidoxModel.load(KaleidoxConfig.default, path.toFile)
  private def _output(group: String): Path = _base.resolve(s"target/test-generated/failure-model-$group")
  private def _run(route: String, input: Path, output: Path): Unit =
    cozy.Cozy.main(Array(route, input.toString, "--save", output.toString))
  private def _run_library(input: Path, output: Path): Unit =
    cozy.Cozy.main(Array("modeler-scala-value", input.toString, "--save", output.toString,
      "--generation-target", "library", "--cozy-generator-version", org.simplemodeling.cozy.BuildInfo.version))
  private def _write(path: Path, value: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, value, UTF_8)
    ()
  }
  private def _all_bytes(root: Path): Map[String, Vector[Byte]] = {
    val stream = Files.walk(root)
    try stream.iterator().asScala.filter(Files.isRegularFile(_)).map(path =>
      root.relativize(path).toString.replace(java.io.File.separatorChar, '/') -> Files.readAllBytes(path).toVector).toMap
    finally stream.close()
  }
  private def _products(output: Path): Map[String, Vector[Byte]] =
    Vector(_scala_path, _json_path).map(path => path -> Files.readAllBytes(output.resolve(path)).toVector).toMap
  private def _realm_string(realm: Realm, path: String): String =
    realm.get(path).collect { case value: Realm.StringData => value.string }.getOrElse(fail(s"Missing generated artifact: $path"))
  private def _line(source: String, heading: String): Int = source.linesIterator.toVector.indexOf(heading) + 1
  private def _scopes(contract: EffectiveFailureContract): Vector[String] = contract.entries.map(_.scope.name)
  private def _legacy_reader_projection(path: String, text: String): String = {
    val paths = (_reader_projection_amendment \ "applicablePaths").as[Set[String]]
    val readerpaths = (_reader_projection_amendment \ "readerOnlyPaths").as[Set[String]]
    val replacements = (_reader_projection_amendment \ "replacements").as[Vector[JsObject]]
    val selected = if (paths.contains(path)) replacements
      else if (readerpaths.contains(path)) replacements.filterNot(replacement => (replacement \ "symbol").as[String] == "_to_external_value")
      else Vector.empty[JsObject]
    selected.foldLeft(text) { (current, replacement) =>
      val before = (replacement \ "before").as[String]
      val after = (replacement \ "after").as[String]
      val symbol = (replacement \ "symbol").as[String]
      val index = current.indexOf(before)
      require(before.nonEmpty && index >= 0 && index == current.lastIndexOf(before),
        s"Expected exactly one historical reader/projection anchor for $path: $symbol")
      current.take(index) + after + current.drop(index + before.length)
    }
  }
  private def _legacy_expected_bytes(path: String, text: String, source: Path): Vector[Byte] = {
    val expected = path match {
      case "target/cozy/model-metadata.json" =>
        val oldpath = Json.stringify((Json.parse(text) \ "source" \ "path").as[JsValue])
        val oldline = s"""    "path" : $oldpath,
"""
        text should include(oldline)
        text.replace(oldline, s"""    "path" : ${Json.stringify(JsString(source.toString))},
""")
          .replaceAll("(?m)^    \"sha256\" : \"[0-9a-f]{64}\",\n", "")
      case "target/cozy/model-metadata.yaml" =>
        val oldline = text.linesIterator.find(_.startsWith("  path: ")).getOrElse(fail("Missing baseline source.path"))
        text.replace(oldline + "\n", s"  path: ${Json.stringify(JsString(source.toString))}\n")
          .replaceAll("(?m)^  sha256: [0-9a-f]{64}\n", "")
      case "target/scala-3.3.8/src_managed/main/scala/domain/DomainComponent.scala" =>
        // SimpleModeler 549a38c intentionally names operation content, even without descriptions.
        Vector("beginReview", "submitReview", "supplyInput", "reviseChange").foldLeft(text) { (current, name) =>
          val anchor = s"""val specification = OperationDefinition.Specification.Builder("$name").
        copy(
"""
          current should include(anchor)
          current.replace(anchor, anchor + s"""          content = org.goldenport.value.BaseContent.Builder("$name"),
""")
        }
      case _ => text
    }
    _legacy_reader_projection(path, expected).getBytes(UTF_8).toVector
  }
  // Fixed expected-source adaptations from SimpleModeler 52aea03, never from generated output.
  // Only these four historical products contain the superseded private conversion helpers.
  private def _current_conversion_delegates(path: String, text: String): String = {
    val values = Set(
      "target/scala-3.3.8/src_managed/main/scala/domain/value/CountryCode.scala",
      "target/scala-3.3.8/src_managed/main/scala/domain/value/ReviewContext.scala")
    val components = Set(
      "src/main/scala/domain/impl/ComponentFactory.scala",
      "target/scala-3.3.8/src_managed/main/scala/domain/DomainComponent.scala")
    def _replace_method_(source: String, name: String, declaration: String): String = {
      val pattern = ("(?ms)^  private def " + java.util.regex.Pattern.quote(name) +
        "(?=[\\[(]).*?^  }\\n(?:  \\n)?").r
      pattern.findAllMatchIn(source).size shouldBe 1
      pattern.replaceFirstIn(source, java.util.regex.Matcher.quoteReplacement(declaration))
    }
    val projected = if (values.contains(path))
      _replace_method_(text, "_to_external_value",
        "  private def _to_external_value(v: Any): Any =\n" +
        "    org.simplemodeling.model.value.ModelValueEncoder.encodeC(v).TAKE\n  \n")
    else text
    if ((values ++ components).contains(path) && projected.contains("  private def _record_get_record(")) {
      val record = _replace_method_(projected, "_record_get_record",
        "  private def _record_get_record(record: Record, keys: List[String]): Consequence[Option[Record]] =\n" +
        "    _record_get_as_c[Record](record, keys)\n  \n")
      val vector = _replace_method_(record, "_record_get_vector_as_c",
        "  private def _record_get_vector_as_c[A](record: Record, keys: List[String])(using vr: org.goldenport.convert.ValueReader[A]): Consequence[Option[Vector[A]]] =\n" +
        "    _record_get_as_c[Vector[A]](record, keys)(using org.goldenport.convert.ValueReader.vector(vr, allowSingle = true))\n  \n")
      val contextual = _replace_method_(vector, "_record_get_vector_as_context_c",
        "  private def _record_get_vector_as_context_c[A](record: Record, keys: List[String])(using vr: org.goldenport.convert.ValueReader[A], ctx: org.goldenport.context.ExecutionContext): Consequence[Option[Vector[A]]] =\n" +
        "    _record_get_as_context_c[Vector[A]](record, keys)(using org.goldenport.convert.ValueReader.vector(vr, allowSingle = true), ctx)\n  \n")
      _replace_method_(contextual, "_record_get_vector_of_record_c", "")
    } else projected
  }
  private def _legacy_expected(group: JsObject, input: Path): Map[String, Vector[Byte]] = {
    val operationgroup = (group \ "fixture").as[String] == "src/test/resources/modeler/workflow-semantic-outcome.cml" &&
      (group \ "route").as[String] == "modeler-scala"
    (group \ "files").as[Vector[JsObject]].map { file =>
      val path = (file \ "path").as[String]
      val text = (file \ "text").as[String]
      val bytes = if (path == "target/scala-3.3.8/src_managed/main/scala/domain/DomainComponent.scala" && !operationgroup)
        text.getBytes(UTF_8).toVector
      else
        _legacy_expected_bytes(path, text, input.toAbsolutePath.normalize())
      path -> bytes
    }.toMap
  }
  private def _legacy_text(fixture: String, route: String, path: String): String = {
    val groups = (Json.parse(Files.readString(_resources.resolve("failure-model-legacy-baseline.json"))) \ "groups").as[Vector[JsObject]]
    val group = groups.find(g => (g \ "fixture").as[String].endsWith("/" + fixture) && (g \ "route").as[String] == route).get
    val text = (group \ "files").as[Vector[JsObject]].find(f => (f \ "path").as[String] == path).map(f => (f \ "text").as[String]).get
    _legacy_reader_projection(path, text)
  }
  private def _mixed_source(original: String): String = {
    val defaults = Files.readString(_resources.resolve("failure-model.cml")).split("# COMPONENT", 2).head
    original.replace("domain\n\n# VALUE", "domain\n\n### EXECUTION-MODEL\nmodel = LocalSingleWriter\n\n### FAILURE-MODEL\n#### concurrent-write\nscope = IN_SCOPE\n\n# VALUE")
      .replace("## ReviewService\n\n### OPERATION", "## ReviewService\n\n### FAILURE-MODEL\n#### concurrent-write\nscope = OUT_OF_SCOPE\n\n### OPERATION")
      .replace("#### submitReview\n\n##### TYPE", "#### submitReview\n\n##### FAILURE-MODEL\n###### network-partition\nscope = IN_SCOPE\n\n##### TYPE") + "\n" + defaults
  }

  private def _sample(identity: String, scopes: Vector[FailureScope], line: Option[Int]): ComponentFailureModel = {
    def _source_(kind: String, owner: String) = FailureModelSourceIdentity(kind, kind + "/" + owner, line)
    val executionsource = _source_("EXECUTION-MODEL", identity)
    val defaults = Vector(FailureModelEntry(identity, scopes(0), executionsource),
      FailureModelEntry(identity + "-other", FailureScope.OutOfScope, _source_("FAILURE", identity + "-other")))
    val execution = ExecutionFailureModel(identity, executionsource, defaults)
    def _delta_(kind: String, owner: String, scope: FailureScope) = {
      val location = _source_(kind, owner)
      FailureModelDelta(kind, owner, Some(location), Vector(FailureModelEntry(identity, scope, location)))
    }
    val componentdelta = _delta_("COMPONENT", identity, scopes(1))
    val servicedelta = _delta_("SERVICE", identity + "-service", scopes(2))
    val operationdelta = _delta_("OPERATION", identity + "-operation", scopes(3))
    def _effective_(kind: String, owner: String, deltas: Vector[FailureModelDelta]) =
      FailureModel.effective(kind, owner, _source_(kind, owner), defaults, deltas)
    val operation = OperationFailureModel(operationdelta.ownerIdentity, operationdelta.source.get, operationdelta,
      _effective_("OPERATION", operationdelta.ownerIdentity, Vector(componentdelta, servicedelta, operationdelta)))
    val service = ServiceFailureModel(servicedelta.ownerIdentity, servicedelta.source.get, servicedelta,
      _effective_("SERVICE", servicedelta.ownerIdentity, Vector(componentdelta, servicedelta)), Vector(operation))
    val siblingsource = _source_("SERVICE", identity + "-sibling")
    val sibling = ServiceFailureModel(identity + "-sibling", siblingsource,
      FailureModelDelta("SERVICE", identity + "-sibling", None, Vector.empty),
      _effective_("SERVICE", identity + "-sibling", Vector(componentdelta)), Vector.empty)
    ComponentFailureModel(identity, componentdelta.source.get, _source_("EXECUTION-SELECTION", identity), execution,
      componentdelta, _effective_("COMPONENT", identity, Vector(componentdelta)), Vector(service, sibling))
  }

  // Independent public-shape oracle and decoder: neither calls the producer serializer.
  private def _source_json(p: FailureModelSourceIdentity): JsObject = Json.obj(
    "declarationKind" -> p.declarationKind, "sectionPath" -> p.sectionPath, "line" -> p.line.map[JsValue](n => JsNumber(n)).getOrElse[JsValue](JsNull))
  private def _entry_json(p: FailureModelEntry): JsObject = Json.obj("identity" -> p.identity, "scope" -> p.scope.name, "source" -> _source_json(p.source))
  private def _delta_json(p: FailureModelDelta): JsObject = Json.obj("ownerKind" -> p.ownerKind, "ownerIdentity" -> p.ownerIdentity,
    "source" -> p.source.map[JsValue](_source_json).getOrElse[JsValue](JsNull), "entries" -> p.entries.map(_entry_json))
  private def _effective_json(p: EffectiveFailureContract): JsObject = Json.obj("ownerKind" -> p.ownerKind, "ownerIdentity" -> p.ownerIdentity,
    "source" -> _source_json(p.source), "entries" -> p.entries.map(entry => Json.obj("identity" -> entry.identity, "scope" -> entry.scope.name,
      "origins" -> entry.origins.map(_entry_json), "winningOrigin" -> _source_json(entry.winningOrigin))))
  private def _operation_json(p: OperationFailureModel): JsObject = Json.obj("identity" -> p.identity, "source" -> _source_json(p.source),
    "delta" -> _delta_json(p.delta), "effective" -> _effective_json(p.effective))
  private def _service_json(p: ServiceFailureModel): JsObject = Json.obj("identity" -> p.identity, "source" -> _source_json(p.source),
    "delta" -> _delta_json(p.delta), "effective" -> _effective_json(p.effective), "operations" -> p.operations.map(_operation_json))
  private def _component_json(p: ComponentFailureModel): JsObject = Json.obj("identity" -> p.identity, "source" -> _source_json(p.source),
    "executionSelectionSource" -> _source_json(p.executionSelectionSource),
    "executionModel" -> Json.obj("identity" -> p.executionModel.identity, "source" -> _source_json(p.executionModel.source), "defaults" -> p.executionModel.defaults.map(_entry_json)),
    "delta" -> _delta_json(p.delta), "effective" -> _effective_json(p.effective), "services" -> p.services.map(_service_json))
  private def _expected(definitions: Vector[ComponentFailureModel]): JsObject =
    Json.obj("schemaVersion" -> "cozy.failure-model.v1", "components" -> definitions.map(_component_json))
  private def _same_order(actual: JsValue, expected: JsValue): Boolean = (actual, expected) match {
    case (a: JsObject, e: JsObject) => a.fields.map(_._1) == e.fields.map(_._1) && a.fields.zip(e.fields).forall { case ((_, av), (_, ev)) => _same_order(av, ev) }
    case (JsArray(a), JsArray(e)) => a.size == e.size && a.zip(e).forall { case (av, ev) => _same_order(av, ev) }
    case (a, e) => a == e
  }
  private def _expect_order(actual: JsValue, expected: JsValue): Unit = {
    actual shouldBe expected
    _same_order(actual, expected) shouldBe true
  }
  private def _decode_source(p: JsValue): FailureModelSourceIdentity = FailureModelSourceIdentity(
    (p \ "declarationKind").as[String], (p \ "sectionPath").as[String], (p \ "line").asOpt[Int])
  private def _decode_scope(p: JsValue): FailureScope = p.as[String] match {
    case "IN_SCOPE" => FailureScope.InScope
    case "OUT_OF_SCOPE" => FailureScope.OutOfScope
    case other => throw new IllegalArgumentException(s"Unexpected scope $other")
  }
  private def _decode_entry(p: JsValue): FailureModelEntry = FailureModelEntry((p \ "identity").as[String],
    _decode_scope((p \ "scope").get), _decode_source((p \ "source").get))
  private def _decode_delta(p: JsValue): FailureModelDelta = FailureModelDelta((p \ "ownerKind").as[String], (p \ "ownerIdentity").as[String],
    (p \ "source").toOption.filterNot(_ == JsNull).map(_decode_source), (p \ "entries").as[Vector[JsValue]].map(_decode_entry))
  private def _decode_effective(p: JsValue): EffectiveFailureContract = EffectiveFailureContract((p \ "ownerKind").as[String],
    (p \ "ownerIdentity").as[String], _decode_source((p \ "source").get), (p \ "entries").as[Vector[JsValue]].map { entry =>
      EffectiveFailureEntry((entry \ "identity").as[String], _decode_scope((entry \ "scope").get),
        (entry \ "origins").as[Vector[JsValue]].map(_decode_entry), _decode_source((entry \ "winningOrigin").get))
    })
  private def _decode_operation(p: JsValue): OperationFailureModel = OperationFailureModel((p \ "identity").as[String],
    _decode_source((p \ "source").get), _decode_delta((p \ "delta").get), _decode_effective((p \ "effective").get))
  private def _decode_service(p: JsValue): ServiceFailureModel = ServiceFailureModel((p \ "identity").as[String],
    _decode_source((p \ "source").get), _decode_delta((p \ "delta").get), _decode_effective((p \ "effective").get),
    (p \ "operations").as[Vector[JsValue]].map(_decode_operation))
  private def _decode_component(p: JsValue): ComponentFailureModel = {
    val execution = (p \ "executionModel").get
    ComponentFailureModel((p \ "identity").as[String], _decode_source((p \ "source").get), _decode_source((p \ "executionSelectionSource").get),
      ExecutionFailureModel((execution \ "identity").as[String], _decode_source((execution \ "source").get),
        (execution \ "defaults").as[Vector[JsValue]].map(_decode_entry)), _decode_delta((p \ "delta").get),
      _decode_effective((p \ "effective").get), (p \ "services").as[Vector[JsValue]].map(_decode_service))
  }
  private def _without_sources(p: JsValue): JsValue = p match {
    case obj: JsObject => JsObject(obj.fields.filterNot(_._1 == "source").map { case (key, value) => key -> _without_sources(value) })
    case JsArray(values) => JsArray(values.map(_without_sources))
    case other => other
  }
  private def _literal(value: String): String = Json.stringify(JsString(value)).flatMap { c =>
    if (Character.isISOControl(c) || Character.isSurrogate(c) || c == '\u2028' || c == '\u2029') f"\\u${c.toInt}%04x" else c.toString
  }

  private def _probe(mixed: Boolean): String = {
    val workflow = if (mixed) """  val workflow: domain.statemachine.workflow.StateMachineWorkflowAbi.WorkflowDescriptor =
      |    domain.statemachine.workflow.ReviewWorkflowStateMachineWorkflow1.workflow
      |  val factory: domain.statemachine.workflow.StateMachineWorkflowAbi.ComponentFactoryMetadata =
      |    domain.statemachine.workflow.ReviewWorkflowStateMachineWorkflow1.componentFactoryMetadata
      |  val bootstrap: Vector[domain.statemachine.workflow.StateMachineWorkflowAbi.ComponentFactoryMetadata] =
      |    domain.statemachine.workflow.StateMachineWorkflowComponentFactoryBootstrap.componentFactoryMetadata
      |  val oldSource = domain.statemachine.workflow.StateMachineWorkflowAbi.WorkflowSourceCorrelation(
      |    domain.statemachine.workflow.StateMachineWorkflowAbi.SourceIdentity(None),
      |    domain.statemachine.workflow.StateMachineWorkflowAbi.SourceIdentity(Some(1)))
      |  val oldWorkflow = domain.statemachine.workflow.StateMachineWorkflowAbi.WorkflowDescriptor(
      |    "v1", "old", "v1", oldSource, Vector.empty, Vector.empty, Vector.empty)
      |  val oldDefaults: Vector[domain.statemachine.workflow.StateMachineWorkflowAbi.SemanticOutcomeDeclaration] = oldWorkflow.semanticOutcomes
      |  val actualOutcomes: Vector[String] = workflow.semanticOutcomes.map(_.outcome.canonicalValue)
      |  val workflowMatches: Boolean = bootstrap == Vector(factory) && bootstrap.head.workflow == workflow && oldDefaults.isEmpty
      |  val callableOperations: Vector[String] = workflow.actions.map(_.operation.name)
      |""".stripMargin else ""
    """package cozy.generated.failuremodel
      |
      |import FailureModelMetadata._
      |
      |object FailureModelConsumerProbe {
      |  val absent: FailureModelSourceIdentity = FailureModelSourceIdentity("COMPONENT", "component/example", None)
      |  val located: FailureModelSourceIdentity = FailureModelSourceIdentity(declarationKind = "OPERATION", sectionPath = "operation/example", line = Some(3))
      |  val inScope: FailureScope = FailureScope.InScope
      |  val outOfScope: FailureScope = FailureScope.OutOfScope
      |  val entry: FailureModelEntry = FailureModelEntry("explicit", outOfScope, absent)
      |  val execution: ExecutionFailureModel = ExecutionFailureModel("authored", absent, Vector(entry))
      |  val emptyDelta: FailureModelDelta = FailureModelDelta("SERVICE", "inherited", None, Vector.empty)
      |  val delta: FailureModelDelta = FailureModelDelta(ownerKind = "OPERATION", ownerIdentity = "operation", source = Some(located), entries = Vector(entry.copy(scope = inScope, source = located)))
      |  val effectiveEntry: EffectiveFailureEntry = EffectiveFailureEntry("explicit", inScope, Vector(entry, delta.entries.head), located)
      |  val effective: EffectiveFailureContract = EffectiveFailureContract("OPERATION", "operation", located, Vector(effectiveEntry))
      |  val operation: OperationFailureModel = OperationFailureModel("operation", located, delta, effective)
      |  val service: ServiceFailureModel = ServiceFailureModel("service", absent, emptyDelta, effective, Vector(operation))
      |  val component: ComponentFailureModel = ComponentFailureModel("component", absent, located, execution, emptyDelta, effective, Vector(service))
      |  val actual: Vector[ComponentFailureModel] = components
      |  val version: String = schemaVersion
      |  val selections: Vector[FailureModelSourceIdentity] = actual.map(_.executionSelectionSource)
      |  val sources: Vector[(String, String, Option[Int])] = actual.map(c => (c.source.declarationKind, c.source.sectionPath, c.source.line))
      |  val defaults: Vector[FailureModelEntry] = actual.flatMap(_.executionModel.defaults)
      |  val deltas: Vector[FailureModelDelta] = actual.flatMap(c => Vector(c.delta) ++ c.services.flatMap(s => Vector(s.delta) ++ s.operations.map(_.delta)))
      |  val deltaLocations: Vector[Option[FailureModelSourceIdentity]] = deltas.map(_.source)
      |  val componentEffective: Vector[EffectiveFailureContract] = actual.map(_.effective)
      |  val serviceEffective: Vector[EffectiveFailureContract] = actual.flatMap(_.services.map(_.effective))
      |  val operationEffective: Vector[EffectiveFailureContract] = actual.flatMap(_.services.flatMap(_.operations.map(_.effective)))
      |  val origins: Vector[Vector[FailureModelEntry]] = (componentEffective ++ serviceEffective ++ operationEffective).flatMap(_.entries.map(_.origins))
      |  val winners: Vector[FailureModelSourceIdentity] = (componentEffective ++ serviceEffective ++ operationEffective).flatMap(_.entries.map(_.winningOrigin))
      |  val scopes: Vector[String] = defaults.map(e => scopeName(e.scope))
      |  val names: Vector[String] = defaults.map(_.identity)
      |  val scopeNames: Vector[String] = Vector(inScope.name, outOfScope.name)
      |  def scopeName(value: FailureScope): String = value match {
      |    case FailureScope.InScope => "IN_SCOPE"
      |    case FailureScope.OutOfScope => "OUT_OF_SCOPE"
      |  }
      |""".stripMargin + workflow + "}\n"
  }
}
