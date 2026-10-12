package cozy.video

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import io.circe.{Json, parser}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct. 12, 2026
 * @version Oct. 12, 2026
 * @author  ASAMI, Tomoharu
 */
object CozyVideoSceneLayoutSpec extends Matchers {
  private val _reference = Vector(80.0, 56.0, 24.0, 48.0, 64.0)
  private val _fields = Vector("padding", "fontSize", "labelFontSize", "labelBottom", "labelRight")

  private def _dimensions(width: Double, height: Double): Json =
    Json.obj("width" -> Json.fromDoubleOrNull(width), "height" -> Json.fromDoubleOrNull(height))

  private def _layouts(inputs: Vector[Json]): Vector[Vector[Double]] = {
    val directory = Files.createTempDirectory("cozy-video-scene-layout-")
    val script = directory.resolve("layout.mjs")
    val output = directory.resolve("stdout.txt")
    val errors = directory.resolve("stderr.txt")
    val executable = System.getProperty("cozy.video.quality.node", "node")
    val program = CozyVideoImplementation._remotion_scene_card_layout_js +
      "\nconst inputs = " + Json.fromValues(inputs).noSpaces + ";\n" +
      "console.log(JSON.stringify(inputs.map(input => _scene_card_layout(input))));\n"
    try {
      Files.writeString(script, program, StandardCharsets.UTF_8)
      val process = try {
        new ProcessBuilder(executable, script.toString)
          .directory(directory.toFile)
          .redirectOutput(output.toFile)
          .redirectError(errors.toFile)
          .start()
      } catch {
        case error: java.io.IOException =>
          throw new IllegalStateException(s"Mandatory scene layout specification requires Node: $executable", error)
      }
      val completed = try process.waitFor(10, TimeUnit.SECONDS)
      finally {
        if (process.isAlive) {
          process.destroyForcibly()
          process.waitFor(2, TimeUnit.SECONDS)
        }
      }
      val stdout = Files.readString(output, StandardCharsets.UTF_8)
      val stderr = Files.readString(errors, StandardCharsets.UTF_8)
      val diagnostics = s"Node=$executable completed=$completed stdout=$stdout stderr=$stderr"
      if (!completed || process.isAlive || process.exitValue() != 0)
        throw new IllegalStateException(s"Scene layout JavaScript execution failed: $diagnostics")
      val results = parser.parse(stdout).flatMap(_.as[Vector[Json]]).fold(
        error => throw new IllegalStateException(s"Scene layout output cannot be decoded: $diagnostics", error),
        identity
      )
      if (results.size != inputs.size)
        throw new IllegalStateException(s"Scene layout output does not cover every input: $diagnostics")
      results.map { result =>
        _fields.map { field =>
          result.hcursor.get[Double](field).fold(
            error => throw new IllegalStateException(s"Scene layout $field cannot be decoded: $diagnostics", error),
            identity
          )
        }
      }
    } finally {
      Files.deleteIfExists(script)
      Files.deleteIfExists(output)
      Files.deleteIfExists(errors)
      Files.deleteIfExists(directory)
    }
  }

  private def _should_scale(actual: Vector[Double], baseline: Vector[Double], ratio: Double): Unit = {
    actual.size shouldBe baseline.size
    actual.zip(baseline).zip(_fields).foreach { case ((value, reference), field) =>
      withClue(s"$field: ") { value shouldBe ((reference * ratio) +- 1e-9) }
    }
  }

  private def _check_property(property: Prop): Unit = {
    val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(10), property)
    withClue(s"ScalaCheck result: $result") { result.passed shouldBe true }
  }
}

final class CozyVideoSceneLayoutSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CozyVideoSceneLayoutSpec._

  "Generic SceneCard composition layout" should {
    "preserve the historical reference values" which {
      "retain every value at 720p and above without enlargement" in {
        Given("default dimensions, reference 720p, 1080p and an asymmetric larger composition")
        val inputs = Vector(Json.obj(), _dimensions(1280, 720), _dimensions(1920, 1080), _dimensions(2560, 720))
        When("the exact JavaScript shared with generated Root computes their layouts in Node")
        val layouts = _layouts(inputs)
        Then("padding, body font, label font, bottom and right retain 80, 56, 24, 48 and 64 pixels")
        layouts.foreach(_ shouldBe _reference)
      }

      "cap all five values for generated compositions larger in both axes" in {
        Given("batches of positive composition dimensions at least 1280 by 720")
        val dimensions = Gen.listOfN(16, for {
          width <- Gen.choose(1280, 7680)
          height <- Gen.choose(720, 4320)
        } yield (width, height))
        _check_property(Prop.forAll(dimensions) { batch =>
          val inputs = batch.map { case (width, height) => _dimensions(width, height) }.toVector
          When("Node executes the shared layout helper for the generated batch")
          val layouts = _layouts(inputs)
          Then("every composition has exactly the original reference layout")
          layouts.foreach(_ shouldBe _reference)
          true
        })
      }
    }

    "shrink typography and margins together" which {
      "use two thirds at canonical 480p and three quarters at explicit 540p" in {
        Given("canonical 854 by 480 and explicit 960 by 540 composition dimensions")
        val inputs = Vector(_dimensions(854, 480), _dimensions(960, 540))
        When("Node executes the exact shared layout JavaScript for both compositions")
        val layouts = _layouts(inputs)
        Then("all five values shrink by two thirds and three quarters respectively")
        _should_scale(layouts(0), _reference, 2.0 / 3)
        _should_scale(layouts(1), _reference, 3.0 / 4)
      }

      "preserve every typography and margin proportion under homothetic shrinking" in {
        Given("batches of lower resolution dimensions and positive shrinking divisors")
        val dimensions = Gen.listOfN(16, for {
          width <- Gen.choose(1, 1279)
          height <- Gen.choose(1, 719)
          divisor <- Gen.choose(2, 8)
        } yield (width, height, divisor))
        _check_property(Prop.forAll(dimensions) { batch =>
          val inputs = batch.flatMap { case (width, height, divisor) =>
            Vector(_dimensions(width, height), _dimensions(width.toDouble / divisor, height.toDouble / divisor))
          }.toVector
          When("the shared JavaScript evaluates original and proportionally smaller dimensions in one Node batch")
          val layouts = _layouts(inputs)
          Then("every pixel value shrinks by the dimension ratio and all five normalized proportions agree")
          batch.zip(layouts.grouped(2).toVector).foreach { case ((_, _, divisor), pair) =>
            _should_scale(pair(1), pair(0), 1.0 / divisor)
            val ratio = pair(0).head / _reference.head
            _should_scale(pair(0), _reference, ratio)
          }
          true
        })
      }

      "respect the limiting dimension and grow monotonically up to the reference cap" in {
        Given("generated dimensions across narrow, short and larger compositions with independent axis increases")
        val dimensions = Gen.listOfN(16, for {
          width <- Gen.choose(1, 2560)
          height <- Gen.choose(1, 1440)
          extra <- Gen.choose(1, 2560)
        } yield (width, height, extra))
        _check_property(Prop.forAll(dimensions) { batch =>
          val inputs = batch.flatMap { case (width, height, extra) =>
            Vector(_dimensions(width, height), _dimensions(width + extra, height), _dimensions(width, height + extra))
          }.toVector
          When("Node evaluates each base composition and each independent axis expansion")
          val layouts = _layouts(inputs)
          Then("values stay positive, fit both dimension bounds and the cap, and never decrease as an axis expands")
          batch.zip(layouts.grouped(3).toVector).foreach { case ((width, height, _), group) =>
            val baseline = group(0)
            baseline.zip(_reference).foreach { case (value, reference) =>
              val ratio = value / reference
              ratio should be > 0.0
              ratio should be <= (width.toDouble / 1280 + 1e-9)
              ratio should be <= (height.toDouble / 720 + 1e-9)
              ratio should be <= (1.0 + 1e-9)
              if (width.toDouble / 1280 <= height.toDouble / 720 && width < 1280)
                ratio shouldBe ((width.toDouble / 1280) +- 1e-9)
              else if (height < 720)
                ratio shouldBe ((height.toDouble / 720) +- 1e-9)
              else
                ratio shouldBe 1.0
            }
            group.tail.foreach { expanded =>
              expanded.zip(baseline).zip(_reference).foreach { case ((value, original), reference) =>
                value should be >= (original - 1e-9)
                value should be <= (reference + 1e-9)
              }
            }
          }
          true
        })
      }
    }
  }
}
