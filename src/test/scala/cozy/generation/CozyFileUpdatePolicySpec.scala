package cozy.generation

import java.nio.file.attribute.FileTime
import java.util.concurrent.TimeUnit

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyFileUpdatePolicySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CozyFileUpdatePolicy.Decision
  import CozyFileUpdatePolicy.FileState
  import CozyFileUpdatePolicy.GenerationReason
  import CozyFileUpdatePolicy.Intent
  import CozyFileUpdatePolicy.UnavailableReason

  "CozyFileUpdatePolicy" should {
    "classify generation requests" which {
      "generate for an absent output" in {
        Given("a generation request with a valid input and an absent output")
        val request = _generation_request(
          Vector(_observation("source", FileState.Valid(Some(_file_time(10L))))),
          _observation("target", FileState.Absent)
        )

        When("the file-update request is evaluated")
        val decision = CozyFileUpdatePolicy.evaluate(request)

        Then("the declared producer is selected for a missing output")
        decision shouldBe Decision.Generate("renderer", GenerationReason.MissingOutput)
      }

      "generate when a declared input is strictly newer" in {
        Given("a valid output and a valid declared input with a later FileTime")
        val request = _generation_request(
          Vector(_observation("source", FileState.Valid(Some(_file_time(20L))))),
          _observation("target", FileState.Valid(Some(_file_time(10L))))
        )

        When("the file-update request is evaluated")
        val decision = CozyFileUpdatePolicy.evaluate(request)

        Then("the first newer dependency explains generation")
        decision shouldBe Decision.Generate(
          "renderer",
          GenerationReason.DependencyNewer("source")
        )
      }

      "generate when forced even if the output is current" in {
        Given("known equal input and output times with force enabled")
        val request = _generation_request(
          Vector(_observation("source", FileState.Valid(Some(_file_time(10L))))),
          _observation("target", FileState.Valid(Some(_file_time(10L)))),
          force = true
        )

        When("the file-update request is evaluated")
        val decision = CozyFileUpdatePolicy.evaluate(request)

        Then("force selects generation before currentness reuse")
        decision shouldBe Decision.Generate("renderer", GenerationReason.Forced)
      }

      "reuse for equal older and empty known dependencies" in {
        Given("a valid output and declared inputs no newer than that output")
        val request = _generation_request(
          Vector(
            _observation("equal", FileState.Valid(Some(_file_time(10L)))),
            _observation("older", FileState.Valid(Some(_file_time(5L))))
          ),
          _observation("target", FileState.Valid(Some(_file_time(10L))))
        )

        When("the file-update request is evaluated")
        val decision = CozyFileUpdatePolicy.evaluate(request)

        Then("strict newer-than semantics reuse the output")
        decision shouldBe Decision.Reuse("target")
      }

      "regenerate conservatively for unknown timestamps" in {
        Given("a valid output with an unknown timestamp")
        val outputrequest = _generation_request(
          Vector(_observation("source", FileState.Valid(Some(_file_time(10L))))),
          _observation("target", FileState.Valid(None))
        )

        When("the output timestamp is evaluated")
        val outputdecision = CozyFileUpdatePolicy.evaluate(outputrequest)

        Then("the output path identifies the uncertain reuse")
        outputdecision shouldBe Decision.Generate(
          "renderer",
          GenerationReason.UnknownTimestamp("target")
        )

        Given("a known output and a valid input with an unknown timestamp")
        val inputrequest = _generation_request(
          Vector(_observation("source", FileState.Valid(None))),
          _observation("target", FileState.Valid(Some(_file_time(10L))))
        )

        When("the input timestamp is evaluated")
        val inputdecision = CozyFileUpdatePolicy.evaluate(inputrequest)

        Then("the first uncertain input identifies conservative generation")
        inputdecision shouldBe Decision.Generate(
          "renderer",
          GenerationReason.UnknownTimestamp("source")
        )
      }

      "generate for an invalid output when force is disabled" in {
        Given("a valid input and an invalid output with a semantic reason")
        val request = _generation_request(
          Vector(_observation("source", FileState.Valid(Some(_file_time(10L))))),
          _observation("target", FileState.Invalid("corrupt format"))
        )

        When("the invalid output request is evaluated")
        val decision = CozyFileUpdatePolicy.evaluate(request)

        Then("the invalid-output reason is preserved")
        decision shouldBe Decision.Generate(
          "renderer",
          GenerationReason.InvalidOutput("corrupt format")
        )
      }

      "choose the first uncertain or newer input in declared order" in {
        Given("two valid inputs with unknown timestamps in a declared order")
        val unknownrequest = _generation_request(
          Vector(
            _observation("first-unknown", FileState.Valid(None)),
            _observation("second-unknown", FileState.Valid(None))
          ),
          _observation("target", FileState.Valid(Some(_file_time(10L))))
        )

        When("the unknown input timestamps are evaluated")
        val unknowndecision = CozyFileUpdatePolicy.evaluate(unknownrequest)

        Then("the first declared uncertain input supplies the reason")
        unknowndecision shouldBe Decision.Generate(
          "renderer",
          GenerationReason.UnknownTimestamp("first-unknown")
        )

        Given("two newer inputs in a declared order")
        val newerrequest = _generation_request(
          Vector(
            _observation("first-newer", FileState.Valid(Some(_file_time(20L)))),
            _observation("second-newer", FileState.Valid(Some(_file_time(30L))))
          ),
          _observation("target", FileState.Valid(Some(_file_time(10L))))
        )

        When("the newer input timestamps are evaluated")
        val newerdecision = CozyFileUpdatePolicy.evaluate(newerrequest)

        Then("the first declared newer input supplies the reason")
        newerdecision shouldBe Decision.Generate(
          "renderer",
          GenerationReason.DependencyNewer("first-newer")
        )
      }

      "reuse a valid output when no inputs are declared" in {
        Given("a valid output, no declared inputs, and no producer")
        val request = _generation_request(
          Vector.empty,
          _observation("target", FileState.Valid(Some(_file_time(10L)))),
          producer = None
        )

        When("the empty-input request is evaluated")
        val decision = CozyFileUpdatePolicy.evaluate(request)

        Then("all-current reuse does not require an input or producer")
        decision shouldBe Decision.Reuse("target")
      }

      "give required-input failures precedence in declared order" in {
        Given("an invalid first input, a missing second input, and force enabled")
        val firstrequest = _generation_request(
          Vector(
            _observation("first", FileState.Invalid("bad source")),
            _observation("second", FileState.Absent)
          ),
          _observation("target", FileState.Absent),
          force = true
        )

        When("the declared inputs are examined")
        val firstdecision = CozyFileUpdatePolicy.evaluate(firstrequest)

        Then("the first invalid input is reported before force or output state")
        firstdecision shouldBe Decision.Unavailable(
          UnavailableReason.InvalidRequiredInput("first", "bad source")
        )

        Given("the same failures with the missing input declared first")
        val secondrequest = firstrequest.copy(
          inputs = Vector(
            _observation("second", FileState.Absent),
            _observation("first", FileState.Invalid("bad source"))
          )
        )

        When("the reordered declared inputs are examined")
        val seconddecision = CozyFileUpdatePolicy.evaluate(secondrequest)

        Then("the declared order remains the deterministic tie breaker")
        seconddecision shouldBe Decision.Unavailable(
          UnavailableReason.MissingRequiredInput("second")
        )
      }

      "report an unavailable producer only when generation is needed" in {
        Given("a missing output and a blank producer declaration")
        val buildrequest = _generation_request(
          Vector(_observation("source", FileState.Valid(Some(_file_time(10L))))),
          _observation("target", FileState.Absent),
          producer = Some("  ")
        )

        When("the missing-output request is evaluated")
        val builddecision = CozyFileUpdatePolicy.evaluate(buildrequest)

        Then("the output path reports the unavailable producer")
        builddecision shouldBe Decision.Unavailable(UnavailableReason.MissingProducer("target"))

        Given("a missing output and an absent producer declaration")
        val absentproducerrequest = buildrequest.copy(producer = None)

        When("the absent-producer request is evaluated")
        val absentproducerdecision = CozyFileUpdatePolicy.evaluate(absentproducerrequest)

        Then("an absent producer reports the same explicit unavailable reason")
        absentproducerdecision shouldBe Decision.Unavailable(
          UnavailableReason.MissingProducer("target")
        )

        Given("a current output and the same blank producer declaration")
        val reuserequest = buildrequest.copy(
          output = _observation("target", FileState.Valid(Some(_file_time(10L))))
        )

        When("the current request is evaluated")
        val reusedecision = CozyFileUpdatePolicy.evaluate(reuserequest)

        Then("current reuse does not require an external producer")
        reusedecision shouldBe Decision.Reuse("target")
      }

      "preserve a nonblank producer identifier exactly" in {
        Given("a request whose producer contains surrounding spaces")
        val request = _generation_request(
          Vector(_observation("source", FileState.Valid(Some(_file_time(20L))))),
          _observation("target", FileState.Valid(Some(_file_time(10L)))),
          producer = Some("  renderer-v1  ")
        )

        When("the dependency-newer request is evaluated")
        val decision = CozyFileUpdatePolicy.evaluate(request)

        Then("the nonblank producer declaration is unchanged")
        decision shouldBe Decision.Generate(
          "  renderer-v1  ",
          GenerationReason.DependencyNewer("source")
        )
      }
    }

    "classify explicit prebuilt adoption" which {
      "adopt a valid supplied output without generation prerequisites" in {
        Given("an explicit prebuilt request with a valid output and invalid generation inputs")
        val request = _generation_request(
          Vector(_observation("source", FileState.Absent)),
          _observation("target", FileState.Valid(None)),
          intent = Intent.ExplicitPrebuilt,
          producer = None,
          force = true
        )

        When("the explicit prebuilt request is evaluated")
        val decision = CozyFileUpdatePolicy.evaluate(request)

        Then("valid adoption ignores generation timestamps, force, producer, and inputs")
        decision shouldBe Decision.Adopt("target")
      }

      "reject absent or invalid supplied outputs" in {
        Given("an explicit prebuilt request with an absent supplied output")
        val absentrequest = _generation_request(
          Vector.empty,
          _observation("target", FileState.Absent),
          intent = Intent.ExplicitPrebuilt
        )

        When("the absent prebuilt request is evaluated")
        val absentdecision = CozyFileUpdatePolicy.evaluate(absentrequest)

        Then("the missing prebuilt path is reported")
        absentdecision shouldBe Decision.Unavailable(UnavailableReason.MissingPrebuilt("target"))

        Given("an explicit prebuilt request with an invalid supplied output")
        val invalidrequest = absentrequest.copy(
          output = _observation("target", FileState.Invalid("wrong format"))
        )

        When("the invalid prebuilt request is evaluated")
        val invaliddecision = CozyFileUpdatePolicy.evaluate(invalidrequest)

        Then("the invalid prebuilt reason is preserved")
        invaliddecision shouldBe Decision.Unavailable(
          UnavailableReason.InvalidPrebuilt("target", "wrong format")
        )
      }
    }

    "satisfy timestamp properties" which {
      "select generation for every strictly newer input" in {
        Given("independently generated input and output FileTimes")
        val times = Gen.chooseNum(-100000L, 100000L)
        val property = Prop.forAll(times, times) { (inputvalue, outputvalue) =>
          val inputtime = _file_time(inputvalue)
          val outputtime = _file_time(outputvalue)
          val request = _generation_request(
            Vector(_observation("source", FileState.Valid(Some(inputtime)))),
            _observation("target", FileState.Valid(Some(outputtime)))
          )
          val decision = CozyFileUpdatePolicy.evaluate(request)
          if (inputtime.compareTo(outputtime) > 0) {
            decision == Decision.Generate(
              "renderer",
              GenerationReason.DependencyNewer("source")
            )
          } else {
            true
          }
        }

        When("the strict newer-than property is checked")
        val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("every strictly newer input selects generation")
        check.passed shouldBe true
        check.succeeded should be >= 50
      }

      "reuse whenever every known input is no newer than the output" in {
        Given("independently generated input and output FileTimes constrained to input<=output")
        val values = Gen.chooseNum(-100000L, 100000L)
        val property = Prop.forAll(values, values) { (inputvalue, deltavalue) =>
          val outputtime = _file_time(inputvalue)
          val inputtime = _file_time(inputvalue - math.abs(deltavalue))
          val request = _generation_request(
            Vector(_observation("source", FileState.Valid(Some(inputtime)))),
            _observation("target", FileState.Valid(Some(outputtime)))
          )
          CozyFileUpdatePolicy.evaluate(request) == Decision.Reuse("target")
        }

        When("the known-input currentness property is checked")
        val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("every known input at or before the output reuses it")
        check.passed shouldBe true
        check.succeeded should be >= 50
      }

      "adopt every valid explicit prebuilt independent of timestamps" in {
        Given("arbitrary input and output timestamp options for explicit adoption")
        val timestamps = Gen.option(Gen.chooseNum(-100000L, 100000L))
        val property = Prop.forAll(timestamps, timestamps) { (inputvalue, outputvalue) =>
          val inputstate = FileState.Valid(inputvalue.map(_file_time))
          val outputstate = FileState.Valid(outputvalue.map(_file_time))
          val request = _generation_request(
            Vector(_observation("source", inputstate)),
            _observation("target", outputstate),
            intent = Intent.ExplicitPrebuilt,
            producer = None,
            force = true
          )
          CozyFileUpdatePolicy.evaluate(request) == Decision.Adopt("target")
        }

        When("the timestamp-independent adoption property is checked")
        val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("every valid supplied output is adopted")
        check.passed shouldBe true
        check.succeeded should be >= 50
      }
    }
  }

  private def _file_time(value: Long): FileTime =
    FileTime.from(value, TimeUnit.NANOSECONDS)

  private def _observation(path: String, state: CozyFileUpdatePolicy.FileState): CozyFileUpdatePolicy.FileObservation =
    CozyFileUpdatePolicy.FileObservation(path, state)

  private def _generation_request(
    inputs: Vector[CozyFileUpdatePolicy.FileObservation],
    output: CozyFileUpdatePolicy.FileObservation,
    intent: CozyFileUpdatePolicy.Intent = Intent.Generation,
    producer: Option[String] = Some("renderer"),
    force: Boolean = false
  ): CozyFileUpdatePolicy.Request =
    CozyFileUpdatePolicy.Request(intent, producer, inputs, output, force)
}
