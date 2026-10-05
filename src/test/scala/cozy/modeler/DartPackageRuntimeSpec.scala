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
 * @version Oct.  5, 2026
 */
class DartPackageRuntimeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Independent consumers of a generated Dart package" should {
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
