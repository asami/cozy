package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

/*
 * @since   Oct.  5, 2026
 * @version Oct.  6, 2026
 */
class DartPackageRuntimeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Independent consumers of a generated Dart package" should {
    "omit optional nulls recursively without changing default codecs under either field profile (DA-23)" in {
      Given("a locally extended profile fixture with optional itemNote and independent expected wire spellings")
      val model = DartPackageSpecFixtures.parse(DartJsonFieldStyleSpecFixtures.source.replace(
        "| itemName | string | 1 |", "| itemName | string | 1 |\n| itemNote | string | ? |"))
      val sdk = _direct_sdk
      Vector(DartJsonFieldStyle.Source, DartJsonFieldStyle.CncfSnakeCase).foreach { style =>
        Given(s"a fresh ${style.id} SDK and a separate consumer using generated contracts only")
        val cncf = style == DartJsonFieldStyle.CncfSnakeCase
        val scratch = _scratch(if (cncf) "null-omission-cncf-consumer" else "null-omission-source-consumer")
        val generated = scratch.resolve("sdk")
        val consumer = scratch.resolve("consumer")
        _consumer(consumer, "phase65_omission_consumer", "../sdk", _null_omission_consumer_source(cncf),
          packagename = "phase65_profile")
        val options = DartJsonFieldStyleSpecFixtures.options.copy(jsonFieldStyle = style)
        When("the extended canonical fixture generates its complete SDK and resolves offline")
        _package(generated, DartPackageSpecFixtures.products(DartPackageGenerator.generate(model, options)))
        val sdkget = _command(sdk, generated, Vector("pub", "get", "--offline"), scratch, "sdk-pub-get")
        Then("the generated SDK dependency configuration succeeds")
        _expect_success(sdkget)
        When("the SDK analyzes and the separate consumer resolves offline")
        val sdkanalysis = _command(sdk, generated, Vector("analyze", "--fatal-infos", "--fatal-warnings"), scratch, "sdk-analyze")
        val consumerget = _command(sdk, consumer, Vector("pub", "get", "--offline"), scratch, "consumer-pub-get")
        Then("both prerequisites succeed with the normal direct SDK")
        Vector(sdkanalysis, consumerget).foreach(_expect_success)
        When("the independent consumer analyzes against the generated barrel")
        val analysis = _command(sdk, consumer, Vector("analyze", "--fatal-infos", "--fatal-warnings"), scratch, "consumer-analyze")
        Then("the additional map-returning method compiles cleanly")
        _expect_success(analysis)
        When("the consumer executes complete default, omission and decode observations")
        val execution = _command(sdk, consumer, Vector("run", "bin/contract.dart"), scratch, "consumer-run")
        Then("recursive absence, nonnull scalars/discriminators, required keys, empty lists and unchanged default encoding hold")
        _expect_success(execution)
        execution.stdout should include("DA-23 independent optional-null omission consumer passed")
      }
    }

    "round-trip the explicit CNCF profile using generated constructors and codecs (DA-22)" which {
      "analyze a separate package and enforce nested, list, null, scalar and finite wire contracts" in {
        Given("parsed canonical profile CML, a direct SDK and an independent consumer with no authored DTO or codec")
        val sdk = _direct_sdk
        val scratch = _scratch("cncf-profile-consumer")
        val generated = scratch.resolve("sdk-cncf-snake")
        val consumer = scratch.resolve("consumer")
        val options = DartJsonFieldStyleSpecFixtures.options.copy(jsonFieldStyle = DartJsonFieldStyle.CncfSnakeCase)
        _consumer(consumer, "phase65_profile_consumer", "../sdk-cncf-snake", _profile_consumer_source, packagename = "phase65_profile")
        When("the complete profile SDK is generated and resolves offline")
        _package(generated, DartPackageSpecFixtures.products(DartPackageGenerator.generate(DartJsonFieldStyleSpecFixtures.model, options)))
        val sdkget = _command(sdk, generated, Vector("pub", "get", "--offline"), scratch, "sdk-pub-get")
        Then("the actual generated profile package resolves with its SDK-only contract")
        _expect_success(sdkget)
        When("the generated SDK is analyzed and the independent consumer resolves offline")
        val sdkanalysis = _command(sdk, generated, Vector("analyze", "--fatal-infos", "--fatal-warnings"), scratch, "sdk-analyze")
        val consumerget = _command(sdk, consumer, Vector("pub", "get", "--offline"), scratch, "consumer-pub-get")
        Then("SDK analysis and the consumer's path dependency succeed")
        Vector(sdkanalysis, consumerget).foreach(_expect_success)
        When("the separate typed consumer is analyzed against the generated barrel")
        val analysis = _command(sdk, consumer, Vector("analyze", "--fatal-infos", "--fatal-warnings"), scratch, "consumer-analyze")
        Then("the original Dart symbols and selected generated codec compile cleanly")
        _expect_success(analysis)
        When("the actual consumer executes constructor, complete snake JSON and controlled rejection observations")
        val execution = _command(sdk, consumer, Vector("run", "bin/contract.dart"), scratch, "consumer-run")
        Then("nested/list roundtrips, optional missing/null, scalar and finite values hold and invalid required keys/types/kinds fail")
        _expect_success(execution)
        execution.stdout should include("DA-22 independent CNCF profile consumer passed")
      }
    }

    "compile and consume the actual shared typed API contracts (DA-14, DA-16, DA-17)" which {
      "analyze the complete SDK and run a separate package using its barrel" in {
        Given("a direct SDK, parsed canonical CML, and an independent consumer that owns no DTO classes")
        val sdk = _direct_sdk
        val model = DartPackageSpecFixtures.model
        val scratch = _scratch("typed-consumer")
        val generated = scratch.resolve("sdk")
        val consumer = scratch.resolve("consumer")
        _consumer(consumer, "phase65_consumer", "../sdk", _consumer_source)
        When("the generated Realm is written, both packages resolve offline and the actual SDK analyzes and runs the consumer")
        _package(generated, DartPackageSpecFixtures.products(DartPackageGenerator.generate(model, DartPackageSpecFixtures.options)))
        val sdkget = _command(sdk, generated, Vector("pub", "get", "--offline"), scratch, "sdk-pub-get")
        Then("offline resolution establishes the generated SDK package configuration")
        _expect_success(sdkget)
        When("the SDK is analyzed and the separate consumer resolves its path dependency offline")
        val sdkanalysis = _command(sdk, generated, Vector("analyze", "--fatal-infos", "--fatal-warnings"), scratch, "sdk-analyze")
        val consumerget = _command(sdk, consumer, Vector("pub", "get", "--offline"), scratch, "consumer-pub-get")
        Then("SDK analysis and independent offline package resolution succeed")
        Vector(sdkanalysis, consumerget).foreach(_expect_success)
        When("the independent package is analyzed against the generated barrel")
        val consumeranalysis = _command(sdk, consumer, Vector("analyze", "--fatal-infos", "--fatal-warnings"), scratch, "consumer-analyze")
        Then("the typed consumer compiles without warnings or infos")
        _expect_success(consumeranalysis)
        When("the resolved consumer runs its typed Future, void, keyword, codec and modeled-error observations")
        val execution = _command(sdk, consumer, Vector("run", "bin/contract.dart"), scratch, "consumer-run")
        Then("all offline resolutions, SDK/consumer analyses and typed runtime observations succeed")
        Vector(sdkget, sdkanalysis, consumerget, consumeranalysis, execution).foreach(_expect_success)
        execution.stdout should include("DA-14, DA-16, DA-17 independent typed API consumer passed")
        Files.isRegularFile(generated.resolve("lib/phase65_api.dart")) shouldBe true
        Files.isRegularFile(consumer.resolve(".dart_tool/package_config.json")) shouldBe true
      }
    }

    "enforce the generated method input and return types" which {
      "diagnose controlled wrong input and return types outside the positive analysis trees" in {
        Given("the actual generated SDK and a separate negative consumer violating only API input/return types")
        val sdk = _direct_sdk
        val scratch = _scratch("negative-consumer")
        val generated = scratch.resolve("sdk")
        val positive = scratch.resolve("consumer")
        val negative = scratch.resolve("negative")
        _consumer(positive, "phase65_consumer", "../sdk", _consumer_source)
        _consumer(negative, "phase65_negative", "../sdk",
          "// @dart=3.0\nimport 'package:phase65_api/phase65_api.dart';\n" +
          "Future<SharedResponse> wrongInput(SampleComponentCatalogApi api) => api.lookup('wrong');\n" +
          "Future<String> wrongReturn(SampleComponentCatalogApi api, SharedRequest request) => api.lookup(request);\n" +
          "void main() {}\n")
        When("positive SDK/consumer analysis succeeds and the independently resolved negative package is analyzed")
        _package(generated, DartPackageSpecFixtures.products(DartPackageGenerator.generate(DartPackageSpecFixtures.model, DartPackageSpecFixtures.options)))
        val sdkget = _command(sdk, generated, Vector("pub", "get", "--offline"), scratch, "sdk-pub-get")
        Then("the generated SDK resolves offline before dependent analysis")
        _expect_success(sdkget)
        When("the SDK is analyzed and the positive and negative independent packages resolve offline")
        val sdkanalysis = _command(sdk, generated, Vector("analyze", "--fatal-infos", "--fatal-warnings"), scratch, "sdk-analyze")
        val positiveget = _command(sdk, positive, Vector("pub", "get", "--offline"), scratch, "consumer-pub-get")
        val negativeget = _command(sdk, negative, Vector("pub", "get", "--offline"), scratch, "negative-pub-get")
        Then("all three prerequisites succeed using normal SDK caches")
        Vector(sdkanalysis, positiveget, negativeget).foreach(_expect_success)
        When("the separate positive and controlled negative packages are analyzed against the same generated API")
        val positiveanalysis = _command(sdk, positive, Vector("analyze", "--fatal-infos", "--fatal-warnings"), scratch, "consumer-analyze")
        val rejected = _command(sdk, negative, Vector("analyze", "--format", "machine", "bin/contract.dart"), scratch, "negative-analyze")
        Then("the intended analyzer type diagnostics identify the generated request and response contracts")
        Vector(sdkget, sdkanalysis, positiveget, positiveanalysis, negativeget).foreach(_expect_success)
        withClue(_diagnostics(rejected)) {
          rejected.timedout shouldBe false
          rejected.exit should not be 0
          val diagnostics = rejected.stdout + rejected.stderr
          diagnostics should include("ARGUMENT_TYPE_NOT_ASSIGNABLE")
          diagnostics should include("RETURN_OF_INVALID_TYPE")
          diagnostics should include("SharedRequest")
          diagnostics should include("SharedResponse")
        }
      }
    }

    "analyze a package whose selected operations are all void" which {
      "avoid unused model imports and consume the no-argument Future<void> interface" in {
        Given("canonical source ping, no selected data roots, a direct SDK and a separate void consumer")
        val sdk = _direct_sdk
        val scratch = _scratch("void-consumer")
        val generated = scratch.resolve("sdk")
        val consumer = scratch.resolve("consumer")
        val options = DartPackageGenerator.Options("phase65_void", operationNames = Vector("SampleComponent.Catalog.ping"))
        _consumer(consumer, "phase65_void_consumer", "../sdk",
          "// @dart=3.0\nimport 'package:phase65_void/phase65_void.dart';\n" +
          "final class VoidCatalog implements SampleComponentCatalogApi {\n" +
          "  int calls = 0;\n  @override\n  Future<void> ping() async { calls++; }\n}\n" +
          "Future<void> main() async {\n  final implementation = VoidCatalog();\n" +
          "  final SampleComponentCatalogApi api = implementation;\n  await api.ping();\n" +
          "  if (implementation.calls != 1) throw StateError('void API not called');\n  print('all-void API consumer passed');\n}\n",
          packagename = "phase65_void")
        When("the all-void SDK and independent consumer resolve offline, analyze and execute")
        _package(generated, DartPackageSpecFixtures.products(DartPackageGenerator.generate(DartPackageSpecFixtures.model, options)))
        val sdkget = _command(sdk, generated, Vector("pub", "get", "--offline"), scratch, "sdk-pub-get")
        Then("the import-free SDK package resolves offline")
        _expect_success(sdkget)
        When("the all-void SDK is analyzed and its independent consumer resolves offline")
        val sdkanalysis = _command(sdk, generated, Vector("analyze", "--fatal-infos", "--fatal-warnings"), scratch, "sdk-analyze")
        val consumerget = _command(sdk, consumer, Vector("pub", "get", "--offline"), scratch, "consumer-pub-get")
        Then("the SDK analysis and consumer dependency configuration succeed")
        Vector(sdkanalysis, consumerget).foreach(_expect_success)
        When("the independent all-void consumer is analyzed")
        val consumeranalysis = _command(sdk, consumer, Vector("analyze", "--fatal-infos", "--fatal-warnings"), scratch, "consumer-analyze")
        Then("the typed void consumer compiles cleanly")
        _expect_success(consumeranalysis)
        When("the independently resolved void consumer runs")
        val execution = _command(sdk, consumer, Vector("run", "bin/contract.dart"), scratch, "consumer-run")
        Then("the real analyzer accepts the import-free API and the typed void consumer runs")
        Vector(sdkget, sdkanalysis, consumerget, consumeranalysis, execution).foreach(_expect_success)
        execution.stdout should include("all-void API consumer passed")
      }
    }
  }

  private def _null_omission_consumer_source(cncf: Boolean): String = {
    // Expected wire spellings are independent literals, never the production field policy.
    val (sourcecount, somekey, detailtext, primaryitem, itemlist, itemname, itemnote, statecode, shapecode) =
      if (cncf) ("source_count", "some_key", "detail_text", "primary_item", "item_list", "item_name", "item_note", "state_code", "shape_code")
      else ("sourceCount", "some-key", "detailText", "primaryItem", "itemList", "itemName", "itemNote", "stateCode", "shapeCode")
    s"""// @dart=3.0
      |import 'dart:convert';
      |import 'package:phase65_profile/phase65_profile.dart';
      |
      |void expectJson(Object? actual, Object? expected, String message) {
      |  if (jsonEncode(actual) != jsonEncode(expected)) throw StateError(message);
      |}
      |
      |void main() {
      |  // Given top-level, required nested and list-item optional nulls.
      |  final value = ProfileEnvelope(sourceCount: 3, class_: 'fixture',
      |    primaryItem: ProfileItem(itemName: 'primary'),
      |    itemList: [ProfileItem(itemName: 'first'), ProfileItem(itemName: 'second', itemNote: 'kept')],
      |    stateCode: ProfileState.readyNow, shapeCode: const ProfileShapeRoundShape());
      |  final canonical = <String, Object?>{
      |    '$sourcecount': 3, 'class': 'fixture', '$somekey': null, '$detailtext': null,
      |    '$primaryitem': {'$itemname': 'primary', '$itemnote': null},
      |    '$itemlist': [{'$itemname': 'first', '$itemnote': null}, {'$itemname': 'second', '$itemnote': 'kept'}],
      |    '$statecode': 'readyNow', '$shapecode': 'roundShape',
      |  };
      |  final omitted = <String, Object?>{
      |    '$sourcecount': 3, 'class': 'fixture', '$primaryitem': {'$itemname': 'primary'},
      |    '$itemlist': [{'$itemname': 'first'}, {'$itemname': 'second', '$itemnote': 'kept'}],
      |    '$statecode': 'readyNow', '$shapecode': 'roundShape',
      |  };
      |  // When default encoding surrounds a separate omission call.
      |  expectJson(value.toJson(), canonical, 'default before omission');
      |  final actual = value.toJsonOmittingNullFields();
      |  // Then only optional null keys disappear recursively and default output stays exact.
      |  expectJson(actual, omitted, 'complete recursive omission');
      |  expectJson(value.toJson(), canonical, 'default after omission');
      |
      |  // Given omitted JSON with independently specified required and finite values.
      |  // When generated decoding reconstructs the same canonical optional state.
      |  final decoded = ProfileEnvelope.fromJson(omitted);
      |  // Then omission is stable and default decode/re-encode restores explicit nulls.
      |  expectJson(decoded.toJsonOmittingNullFields(), omitted, 'omission decode roundtrip');
      |  expectJson(decoded.toJson(), canonical, 'canonical null restoration');
      |
      |  // Given nonnull optional string/scalar fields and an empty required-emitted list.
      |  final nonnull = ProfileEnvelope(sourceCount: 4, class_: 'nonnull', someKey: 'optional',
      |    detailText: ProfileText(value: 'scalar-detail'),
      |    primaryItem: ProfileItem(itemName: 'primary', itemNote: 'note'),
      |    stateCode: ProfileState.waiting, shapeCode: const ProfileShapeSquare());
      |  final expected = <String, Object?>{
      |    '$sourcecount': 4, 'class': 'nonnull', '$somekey': 'optional', '$detailtext': 'scalar-detail',
      |    '$primaryitem': {'$itemname': 'primary', '$itemnote': 'note'}, '$itemlist': [],
      |    '$statecode': 'waiting', '$shapecode': 'square',
      |  };
      |  // When both generated methods encode all nonnull values.
      |  expectJson(nonnull.toJson(), expected, 'nonnull default before');
      |  final nonnullOmitted = nonnull.toJsonOmittingNullFields();
      |  // Then scalar/discriminator values, every required key and the empty list stay exact.
      |  expectJson(nonnullOmitted, expected, 'nonnull omission');
      |  expectJson(nonnull.toJson(), expected, 'nonnull default after');
      |  expectJson(ProfileEnvelope.fromJson(expected).toJsonOmittingNullFields(), expected, 'nonnull decode');
      |  print('DA-23 independent optional-null omission consumer passed');
      |}
      |""".stripMargin
  }

  private def _profile_consumer_source: String = """// @dart=3.0
    |import 'dart:convert';
    |import 'package:phase65_profile/phase65_profile.dart';
    |
    |void expect(bool condition, String message) {
    |  if (!condition) throw StateError(message);
    |}
    |
    |void rejectDecode(Object? payload, String message) {
    |  try { ProfileEnvelope.fromJson(payload); }
    |  on DartJsonDecodingException { return; }
    |  throw StateError(message);
    |}
    |
    |void main() {
    |  final payload = <String, Object?>{
    |    'source_count': 3, 'class': 'fixture', 'some_key': 'optional',
    |    'detail_text': 'scalar-detail', 'primary_item': {'item_name': 'primary'},
    |    'item_list': [{'item_name': 'first'}, {'item_name': 'second'}],
    |    'state_code': 'readyNow', 'shape_code': 'roundShape',
    |  };
    |  // Given complete snake-key JSON with original finite discriminator values.
    |  // When generated codecs consume the full object and re-encode it.
    |  final decoded = ProfileEnvelope.fromJson(payload);
    |  // Then named scalar wrappers, nested/list values and complete JSON correspond.
    |  expect(decoded.sourceCount == 3 && decoded.class_ == 'fixture', 'typed fields');
    |  expect(decoded.detailText?.value == 'scalar-detail', 'scalar wrapper');
    |  expect(decoded.primaryItem.itemName == 'primary' && decoded.itemList[1].itemName == 'second', 'nested/list');
    |  expect(decoded.stateCode == ProfileState.readyNow && decoded.shapeCode is ProfileShapeRoundShape, 'finite values');
    |  expect(jsonEncode(decoded.toJson()) == jsonEncode(payload), 'complete snake JSON roundtrip');
    |
    |  // Given original typed constructor names and generated nested/scalar/finite types.
    |  // When the generated constructor builds the same complete value.
    |  final constructed = ProfileEnvelope(sourceCount: 3, class_: 'fixture', someKey: 'optional',
    |    detailText: ProfileText(value: 'scalar-detail'), primaryItem: ProfileItem(itemName: 'primary'),
    |    itemList: [ProfileItem(itemName: 'first'), ProfileItem(itemName: 'second')],
    |    stateCode: ProfileState.readyNow, shapeCode: const ProfileShapeRoundShape());
    |  // Then the profile affects wire keys without changing constructor or discriminator contracts.
    |  expect(jsonEncode(constructed.toJson()) == jsonEncode(payload), 'typed constructor roundtrip');
    |
    |  // Given missing optional/list keys or explicit optional nulls.
    |  final missing = Map<String, Object?>.of(payload)..remove('detail_text')..remove('some_key')..remove('item_list');
    |  final nulls = Map<String, Object?>.of(payload)..['detail_text'] = null..['some_key'] = null;
    |  // When the same generated codec decodes both representations.
    |  final absent = ProfileEnvelope.fromJson(missing);
    |  final explicit = ProfileEnvelope.fromJson(nulls);
    |  // Then missing and null optionals share null and missing lists become empty.
    |  expect(absent.detailText == null && absent.someKey == null && absent.itemList.isEmpty, 'missing defaults');
    |  expect(explicit.detailText == null && explicit.someKey == null, 'explicit null');
    |  expect(jsonEncode(absent.toJson()) == jsonEncode({...payload, 'some_key': null, 'detail_text': null, 'item_list': []}), 'missing re-encode');
    |
    |  // Given camel-only required input, wrong primitive/nested/list types and unknown finite kinds.
    |  final camel = Map<String, Object?>.of(payload)..remove('source_count')..['sourceCount'] = 3;
    |  final invalid = <Map<String, Object?>>[
    |    camel, {...payload, 'source_count': '3'}, {...payload, 'source_count': null},
    |    {...payload, 'source_count': 1.5}, {...payload, 'detail_text': 9},
    |    {...payload, 'primary_item': {'item_name': false}}, {...payload, 'item_list': 'wrong'},
    |    {...payload, 'item_list': null},
    |    {...payload, 'item_list': [null]}, {...payload, 'state_code': 'unknown'},
    |    {...payload, 'shape_code': 'round_shape'},
    |  ];
    |  // When the independent consumer invokes only the generated decoder.
    |  for (final value in invalid) { rejectDecode(value, 'invalid wire input accepted'); }
    |  // Then every invalid input raises the generated technical decoding exception.
    |  print('DA-22 independent CNCF profile consumer passed');
    |}
    |""".stripMargin

  private case class CommandReport(argv: Vector[String], exit: Int, stdout: String, stderr: String, timedout: Boolean, elapsedmillis: Long)

  private def _direct_sdk: Path = {
    val selected = Paths.get(sys.props.getOrElse("cozy.dart.sdk", "/opt/homebrew/Caskroom/flutter/3.47.5/flutter/bin/cache/dart-sdk/bin/dart"))
    val executable = if (Files.isDirectory(selected)) selected.resolve("bin/dart") else selected
    if (!Files.isRegularFile(executable) || !Files.isExecutable(executable))
      fail(s"A direct Dart SDK is required; set cozy.dart.sdk to the SDK or direct binary: $executable")
    val direct = executable.toRealPath()
    val stream = Files.newInputStream(direct)
    val bytes = new Array[Byte](4)
    val length = try stream.read(bytes) finally stream.close()
    val magic = bytes.map(value => f"${value & 0xff}%02x").mkString
    val binary = length == 4 && (Set("7f454c46", "feedface", "cefaedfe", "feedfacf", "cffaedfe", "cafebabe", "bebafeca", "cafebabf", "bfbafeca")(magic) || magic.startsWith("4d5a"))
    if (!binary) fail(s"Dart SDK must be a direct native executable: $direct")
    direct
  }

  private def _scratch(prefix: String): Path = {
    val root = Paths.get("target/phase65/s653/runtime").toAbsolutePath
    Files.createDirectories(root)
    Files.createTempDirectory(root, prefix + "-")
  }

  private def _package(directory: Path, products: Map[String, String]): Unit =
    products.toVector.sortBy(_._1).foreach { case (path, source) => _write(directory.resolve(path), source) }

  private def _consumer(directory: Path, name: String, dependency: String, source: String, packagename: String = "phase65_api"): Unit = {
    _write(directory.resolve("pubspec.yaml"), s"name: $name\nenvironment:\n  sdk: '>=3.0.0 <4.0.0'\ndependencies:\n  $packagename:\n    path: $dependency\n")
    _write(directory.resolve("bin/contract.dart"), source)
  }

  private def _consumer_source: String = {
    val resource = getClass.getResourceAsStream("/modeler/dart-api-consumer.dart")
    if (resource == null) fail("Missing independent Dart API consumer resource")
    val source = scala.io.Source.fromInputStream(resource, "UTF-8")
    try source.mkString finally source.close()
  }

  private def _write(path: Path, content: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    ()
  }

  private def _command(sdk: Path, directory: Path, arguments: Vector[String], scratch: Path, name: String): CommandReport = {
    val argv = sdk.toString +: arguments
    val stdoutpath = scratch.resolve(name + ".stdout.txt")
    val stderrpath = scratch.resolve(name + ".stderr.txt")
    val started = System.nanoTime()
    val process = new ProcessBuilder(argv: _*).directory(directory.toFile).
      redirectOutput(stdoutpath.toFile).redirectError(stderrpath.toFile).start()
    val completed = try process.waitFor(120, TimeUnit.SECONDS) catch {
      case interrupted: InterruptedException =>
        _stop_children(process)
        Thread.currentThread().interrupt()
        throw interrupted
    }
    if (!completed) _stop_children(process)
    val report = CommandReport(argv, if (completed) process.exitValue() else -1,
      new String(Files.readAllBytes(stdoutpath), StandardCharsets.UTF_8),
      new String(Files.readAllBytes(stderrpath), StandardCharsets.UTF_8), !completed,
      TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started))
    _write(scratch.resolve(name + ".report.json"), Json.prettyPrint(Json.obj(
      "argv" -> report.argv, "workingDirectory" -> directory.toString, "exit" -> report.exit,
      "stdout" -> report.stdout, "stderr" -> report.stderr, "timedOut" -> report.timedout,
      "timeoutSeconds" -> 120, "elapsedMillis" -> report.elapsedmillis)))
    report
  }

  private def _stop_children(process: Process): Unit = {
    val descendants = process.descendants()
    val children = try descendants.iterator().asScala.toVector finally descendants.close()
    children.reverse.foreach(_.destroyForcibly())
    process.destroyForcibly()
    process.waitFor(10, TimeUnit.SECONDS)
    ()
  }

  private def _diagnostics(report: CommandReport): String =
    s"${report.argv.mkString(" ")}\nexit=${report.exit}, timedOut=${report.timedout}, elapsedMillis=${report.elapsedmillis}\nstdout:\n${report.stdout}\nstderr:\n${report.stderr}\n"

  private def _expect_success(report: CommandReport): Unit = {
    withClue(_diagnostics(report)) {
      report.timedout shouldBe false
      report.exit shouldBe 0
    }
  }
}
