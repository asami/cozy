package cozy.modeler

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.TimeUnit
import scala.collection.mutable
import org.scalacheck.{Gen, Prop, Test}
import org.scalacheck.rng.Seed
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, Json}

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
class DartModelRuntimeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "A consumer of emitted Dart 3.0 model code" should {
    "observe immutable typed constructors and JSON contracts (DA-01, DA-04..10, DA-12)" which {
      "analyze and execute the generated sources with SDK libraries only" in {
        Given("a real direct Dart SDK and the CML plus canonical data fixture closure")
        val sdk = _direct_sdk
        val scratch = _scratch("contracts")
        val positive = _positive_tree(scratch)
        When("the actual SDK analyzes and runs the public-constructor and codec consumer")
        val analysis = _command(sdk, positive, Vector("analyze", "--fatal-infos", "--fatal-warnings"), scratch, "analyze")
        val execution = _command(sdk, positive, Vector("run", "bin/contract.dart"), scratch, "run")
        Then("analysis has no warnings or infos and all explicit runtime observations succeed")
        _expect_success(analysis)
        _expect_success(execution)
        execution.stdout should include("DA-01, DA-04..10, DA-12 emitted runtime contracts passed")
        When("source metadata is read from the successfully analyzed and consumed generated models")
        val output = new String(Files.readAllBytes(positive.resolve("lib/src/models.dart")), StandardCharsets.UTF_8)
        val metadata = output.linesIterator.map(_.trim).filter(_.startsWith("// source ")).
          map(line => Json.parse(line.stripPrefix("// source ")).as[JsObject]).toVector
        val declaration = metadata.find(x => (x \ "identity").as[String] == "dart.fixture.ConstrainedText").get
        val attribute = metadata.find(x => (x \ "identity").as[String] == "dart.fixture.Container.text").get
        Then("nominal and field identities retain exact names, values and distinct constraint categories")
        (declaration \ "identity").as[String] shouldBe "dart.fixture.ConstrainedText"
        ((declaration \ "constraints")(0) \ "name").as[String] shouldBe "pattern"
        ((declaration \ "constraints")(0) \ "value").as[String] shouldBe "quote'\\$\n日本"
        (attribute \ "identity").as[String] shouldBe "dart.fixture.Container.text"
        ((attribute \ "constraints")(0) \ "name").as[String] shouldBe "min_length"
        ((attribute \ "constraints")(0) \ "value").as[String] shouldBe "3"
        ((attribute \ "typeConstraints")(0) \ "name").as[String] shouldBe "pattern"
        ((attribute \ "typeConstraints")(0) \ "value").as[String] shouldBe "quote'\\$\n日本"
        (attribute \ "constraints") should not be (attribute \ "typeConstraints")
      }

      "round-trip ScalaCheck-generated safe integers, finite doubles and arbitrary escaped strings" in {
        Given("a direct SDK, emitted codecs and reproducible scalar-property input generators")
        val sdk = _direct_sdk
        val scratch = _scratch("properties")
        val positive = _positive_tree(scratch)
        val integers = Gen.listOfN(100, Gen.chooseNum(-9007199254740991L, 9007199254740991L))
        val strings = Gen.listOfN(100, Gen.listOf(Gen.oneOf('a', 'Z', '\u0000', '\n', '\r', '\t', '\'', '"', '\\', '$', '日', '本')).map(_.mkString))
        val doubles = Gen.listOfN(100, Gen.chooseNum(-1.0e100, 1.0e100))
        val observations = mutable.ArrayBuffer.empty[CommandReport]
        When("the SDK analyzes the positive tree and consumes every ScalaCheck-generated batch through actual codecs")
        val analysis = _command(sdk, positive, Vector("analyze", "--fatal-infos", "--fatal-warnings"), scratch, "analyze")
        val property = Prop.forAll(integers, strings, doubles) { (integrals, texts, floating) =>
          val index = observations.size
          val samples = scratch.resolve(s"samples-$index.json")
          _write(samples, Json.stringify(Json.obj("integers" -> integrals, "strings" -> texts, "doubles" -> floating)))
          val report = _command(sdk, positive, Vector("run", "bin/contract.dart", samples.toAbsolutePath.toString), scratch, s"property-$index")
          observations += report
          report.exit == 0 && report.stdout.contains("ScalaCheck roundtrips: 100 integers, 100 strings, 100 doubles")
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(3).withMaxDiscardRatio(1).
          withInitialSeed(Seed(6502L)), property)
        Then("each generated numeric and text value survives JSON encoding and its named model codec")
        _expect_success(analysis)
        observations.foreach(_expect_success)
        withClue(result.toString) { result.passed shouldBe true }
        result.succeeded should be >= 3
      }
    }

    "retain compiler-observed finite exhaustiveness (DA-08, DA-09)" which {
      "accept complete switches and diagnose one missing enum or sealed alternative" in {
        Given("a direct SDK and a positive consumer with exhaustive enum and sealed switches")
        val sdk = _direct_sdk
        val scratch = _scratch("exhaustiveness")
        val positive = _positive_tree(scratch)
        When("positive analysis and execution precede controlled negative analysis outside that tree")
        val analysis = _command(sdk, positive, Vector("analyze", "--fatal-infos", "--fatal-warnings"), scratch, "positive-analyze")
        val execution = _command(sdk, positive, Vector("run", "bin/contract.dart"), scratch, "positive-run")
        _expect_success(analysis)
        _expect_success(execution)
        val negative = scratch.resolve("negative")
        _write(negative.resolve("enum.dart"),
          "// @dart=3.0\nimport '../positive/lib/src/models.dart';\n" +
          "String incomplete(DartFinite value) => switch (value) { DartFinite.alpha => 'alpha' };\n")
        _write(negative.resolve("sealed.dart"),
          "// @dart=3.0\nimport '../positive/lib/src/models.dart';\n" +
          "String incomplete(DartSealed value) => switch (value) { DartSealedAlpha() => 'alpha' };\n")
        val enumreport = _command(sdk, scratch, Vector("analyze", "--format", "machine", "negative/enum.dart"), scratch, "negative-enum")
        val sealedreport = _command(sdk, scratch, Vector("analyze", "--format", "machine", "negative/sealed.dart"), scratch, "negative-sealed")
        Then("both incomplete switches fail with intended-type NON_EXHAUSTIVE_SWITCH diagnostics")
        Vector(enumreport -> "DartFinite", sealedreport -> "DartSealed").foreach { case (report, typename) =>
          withClue(_diagnostics(report)) {
            report.exit should not be 0
            (report.stdout + report.stderr) should include("NON_EXHAUSTIVE_SWITCH_")
            (report.stdout + report.stderr) should include(typename)
          }
        }
      }
    }

    "retain required constructor nullability (DA-04)" which {
      "diagnose omitted and explicitly null nonnullable constructor parameters" in {
        Given("a direct SDK and a positive public-constructor consumer")
        val sdk = _direct_sdk
        val scratch = _scratch("requiredness")
        val positive = _positive_tree(scratch)
        When("positive analysis and execution precede constructor contract counterexamples")
        val analysis = _command(sdk, positive, Vector("analyze", "--fatal-infos", "--fatal-warnings"), scratch, "positive-analyze")
        val execution = _command(sdk, positive, Vector("run", "bin/contract.dart"), scratch, "positive-run")
        _expect_success(analysis)
        _expect_success(execution)
        val negative = scratch.resolve("negative")
        _write(negative.resolve("missing.dart"),
          "// @dart=3.0\nimport '../positive/lib/src/models.dart';\nDartText missing() => DartText();\n")
        _write(negative.resolve("null.dart"),
          "// @dart=3.0\nimport '../positive/lib/src/models.dart';\nDartText nulled() => DartText(value: null);\n")
        val missing = _command(sdk, scratch, Vector("analyze", "--format", "machine", "negative/missing.dart"), scratch, "missing")
        val nulled = _command(sdk, scratch, Vector("analyze", "--format", "machine", "negative/null.dart"), scratch, "null")
        Then("the compiler rejects both absence and null at the required constructor boundary")
        Vector((missing, "MISSING_REQUIRED_ARGUMENT", "value"),
          (nulled, "ARGUMENT_TYPE_NOT_ASSIGNABLE", "String")).foreach { case (report, diagnostic, source) =>
          withClue(_diagnostics(report)) {
            report.exit should not be 0
            (report.stdout + report.stderr) should include(diagnostic)
            (report.stdout + report.stderr) should include(source)
          }
        }
      }
    }
  }

  private case class CommandReport(argv: Vector[String], exit: Int, stdout: String, stderr: String, timedout: Boolean)

  private def _direct_sdk: Path = {
    val explicit = sys.props.get("cozy.dart.executable").orElse(sys.env.get("COZY_DART_EXECUTABLE"))
    val fromsdk = sys.env.get("DART_SDK").map(value => Paths.get(value).resolve("bin/dart"))
    val candidate = explicit.map(Paths.get(_)).orElse(fromsdk).getOrElse {
      sys.env.getOrElse("PATH", "").split(java.util.regex.Pattern.quote(File.pathSeparator)).toVector.
        map(directory => Paths.get(directory).resolve("dart")).find(path => Files.isRegularFile(path) && Files.isExecutable(path)).
        getOrElse(fail("A real Dart SDK executable is required: set cozy.dart.executable, COZY_DART_EXECUTABLE or DART_SDK, or put dart on PATH."))
    }
    if (!Files.isRegularFile(candidate) || !Files.isExecutable(candidate))
      fail(s"Dart SDK prerequisite is not an executable file: $candidate")
    val resolved = candidate.toRealPath()
    val direct = if (explicit.isEmpty && fromsdk.isEmpty && !_is_binary(resolved)) {
      // Flutter's bin/dart is a shell wrapper. Inspect its adjacent SDK; never run it.
      val adjacent = resolved.getParent.resolve("cache/dart-sdk/bin/dart")
      if (Files.isRegularFile(adjacent) && Files.isExecutable(adjacent)) adjacent.toRealPath() else resolved
    } else resolved
    if (!Files.isRegularFile(direct) || !Files.isExecutable(direct) || !_is_binary(direct))
      fail(s"Dart SDK prerequisite must be a direct native binary, never a shell wrapper: $direct")
    direct
  }

  private def _is_binary(path: Path): Boolean = {
    val stream = Files.newInputStream(path)
    val header = new Array[Byte](4)
    val length = try stream.read(header) finally stream.close()
    val magic = header.map(value => f"${value & 0xff}%02x").mkString
    length == 4 && (Set("7f454c46", "feedface", "cefaedfe", "feedfacf", "cffaedfe", "cafebabe", "bebafeca", "cafebabf", "bfbafeca")(magic) || magic.startsWith("4d5a"))
  }

  private def _scratch(prefix: String): Path = {
    val root = Paths.get("target/test-generated/phase65-dart-models").toAbsolutePath
    Files.createDirectories(root)
    Files.createTempDirectory(root, prefix + "-")
  }

  private def _positive_tree(scratch: Path): Path = {
    val positive = scratch.resolve("positive")
    val products = DartModelSpecFixtures.products(DartModelGenerator.generate(DartModelSpecFixtures.model, DartModelSpecFixtures.options))
    products.toVector.sortBy(_._1).foreach { case (path, source) => _write(positive.resolve(path), source) }
    _write(positive.resolve("pubspec.yaml"), "name: phase65_dart_models\nenvironment:\n  sdk: '>=3.0.0 <4.0.0'\n")
    val resource = getClass.getResourceAsStream("/modeler/dart-model-runtime.dart")
    if (resource == null) fail("Missing emitted-model consumer resource")
    val input = scala.io.Source.fromInputStream(resource, "UTF-8")
    val consumer = try input.mkString finally input.close()
    _write(positive.resolve("bin/contract.dart"), consumer)
    positive
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
    val process = new ProcessBuilder(argv: _*).directory(directory.toFile).
      redirectOutput(stdoutpath.toFile).redirectError(stderrpath.toFile).start()
    val completed = try process.waitFor(120, TimeUnit.SECONDS) catch {
      case interrupted: InterruptedException =>
        process.destroyForcibly()
        Thread.currentThread().interrupt()
        throw interrupted
    }
    if (!completed) {
      process.destroyForcibly()
      process.waitFor(10, TimeUnit.SECONDS)
    }
    val report = CommandReport(argv, if (completed) process.exitValue() else -1,
      new String(Files.readAllBytes(stdoutpath), StandardCharsets.UTF_8),
      new String(Files.readAllBytes(stderrpath), StandardCharsets.UTF_8), !completed)
    _write(scratch.resolve(name + ".report.json"), Json.prettyPrint(Json.obj(
      "argv" -> report.argv, "workingDirectory" -> directory.toString, "exit" -> report.exit,
      "stdout" -> report.stdout, "stderr" -> report.stderr, "timedOut" -> report.timedout)))
    report
  }

  private def _diagnostics(report: CommandReport): String =
    s"${report.argv.mkString(" ")}\nexit=${report.exit}, timedOut=${report.timedout}\nstdout:\n${report.stdout}\nstderr:\n${report.stderr}\n"

  private def _expect_success(report: CommandReport): Unit = {
    withClue(_diagnostics(report)) {
      report.timedout shouldBe false
      report.exit shouldBe 0
    }
  }
}
