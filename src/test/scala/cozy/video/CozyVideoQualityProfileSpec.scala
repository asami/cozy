package cozy.video

import io.circe.{Decoder, Json}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import CozyVideoModel.{ResolvedEncodingSettings, VideoEncodingPolicy, VideoProject, VideoRenderer}
import CozyVideoTools.{VideoExecutionConfig, VideoToolMode}

/*
 * @since   Oct. 12, 2026
 * @version Oct. 12, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoQualityProfileSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CozyVideoQualityProfileSpec._

  "Joint quality selection" should {
    "resolve canonical profiles" which {
      "supply the four picture, candidate audio and hardware presets with complete origins" in {
        Given("the four exact canonical tokens and their contracted joint candidate settings")
        _profiles.foreach { case (name, fps, width, height, crf, preset, audio, bitrate, policy) =>
          val descriptor = Json.obj("qualityProfile" -> Json.fromString(name))
          val execution = _hardware
          When(s"the $name descriptor is decoded and resolved for hardware encoding")
          val settings = _renderer(descriptor).toOption.get.resolveEncoding
          val encoder = CozyVideoEncoder.resolve(execution, settings)
          Then("picture, provisional audio, hardware bitrate and all seven origins describe the selected profile")
          settings.effectiveQualityProfile.name shouldBe name
          settings.qualityProfile.map(_.name) shouldBe Some(name)
          settings.policy.name shouldBe policy
          (settings.fps, settings.width, settings.height, settings.crf, settings.x264Preset, settings.audioBitrate) shouldBe
            ((fps, width, height, crf, preset, audio))
          settings.origins shouldBe _origins("quality-profile")
          encoder.bitrate shouldBe Some(bitrate)
          encoder.bitrateOrigin shouldBe "profile-default"
        }
      }

      "select standard when the selection or root renderer is omitted" in {
        Given("an empty renderer and a project with no root renderer")
        val empty = Json.obj()
        When("both descriptors are decoded and their root encoding is resolved")
        val renderer = _renderer(empty).toOption.get.resolveEncoding
        val project = empty.as[VideoProject].toOption.get
        val absent = VideoRenderer.resolveEncoding(project.renderer)
        Then("both resolve new standard while the compatibility carrier keeps historical lightweight picture meaning")
        Vector(renderer, absent).foreach { settings =>
          settings.effectiveQualityProfile shouldBe CozyVideoQuality.Standard
          settings.policy shouldBe VideoEncodingPolicy.Lightweight
          (settings.fps, settings.width, settings.height, settings.crf, settings.x264Preset, settings.audioBitrate) shouldBe
            ((18, 1280, 720, 32, None, "128k"))
          settings.origins shouldBe _origins("default")
        }
      }

      "preserve every explicitly authored historical policy and its AAC320 intent" in {
        Given("the original three policy tokens and their unshifted picture settings")
        val policies = Vector(
          ("lightweight", "standard", 18, 1280, 720, 32),
          ("standard", "quality", 30, 1280, 720, 23),
          ("quality", "high-quality", 30, 1920, 1080, 18)
        )
        policies.foreach { case (policy, canonical, fps, width, height, crf) =>
          val descriptor = Json.obj("policy" -> Json.fromString(policy))
          When(s"the legacy $policy descriptor is decoded and resolved")
          val settings = _renderer(descriptor).toOption.get.resolveEncoding
          Then("its old picture and audio intent survives with an unambiguous canonical tier")
          settings.policy.name shouldBe policy
          settings.effectiveQualityProfile.name shouldBe canonical
          (settings.fps, settings.width, settings.height, settings.crf, settings.audioBitrate) shouldBe
            ((fps, width, height, crf, "320k"))
          settings.origins shouldBe _origins("legacy-policy")
        }
      }

      "give valid canonical selection precedence over every valid historical policy" in {
        Given("all canonical profiles paired with each valid old policy")
        val descriptors = for {
          profile <- _profiles.map(_._1)
          policy <- VideoEncodingPolicy.values
        } yield Json.obj("qualityProfile" -> Json.fromString(profile), "policy" -> Json.fromString(policy.name))
        descriptors.foreach { descriptor =>
          When("both authored selections are decoded before joint resolution")
          val decoded = _renderer(descriptor).toOption.get
          val settings = decoded.resolveEncoding
          val canonical = _renderer(descriptor.mapObject(_.remove("policy"))).toOption.get.resolveEncoding
          Then("the canonical settings win while the authored legacy selection remains available")
          settings shouldBe canonical
          decoded.policy should not be empty
          settings.origins shouldBe _origins("quality-profile")
        }
      }

      "reject invalid canonical tokens and shadowed historical or picture fields as values" in {
        Given("invalid exact-token selections and invalid old fields alongside valid canonical selection")
        val invalidprofiles = Vector(Json.Null, Json.True, Json.fromInt(1)) ++
          Vector("", "Standard", " standard ", "high_quality", "archive").map(Json.fromString)
        val invalidfields = Vector(
          "policy" -> Json.fromString("high-quality"), "policy" -> Json.fromInt(1),
          "fps" -> Json.fromInt(0), "width" -> Json.fromInt(-1), "height" -> Json.fromInt(0),
          "crf" -> Json.fromInt(-1), "x264Preset" -> Json.fromString("ultrafast"),
          "x264Preset" -> Json.fromString(" slow ")
        )
        val descriptors = invalidprofiles.map(value => Json.obj("qualityProfile" -> value, "policy" -> Json.fromString("quality"))) ++
          invalidfields.map { case (field, value) => Json.obj("qualityProfile" -> Json.fromString("standard"), field -> value) }
        When("every descriptor is decoded without ignoring shadowed authored values")
        val results = descriptors.map(_renderer)
        Then("each interpretation returns a decoding failure rather than selected fallback settings")
        results.foreach(result => result.isLeft shouldBe true)
      }
    }

    "resolve individual overrides" which {
      "change only the selected picture or audio field and its origin across generated values" in {
        Given("generated profiles, positive dimensions and bitrates, nonnegative CRF and original accepted presets")
        val cases = for {
          profile <- Gen.oneOf(_profiles.map(_._1))
          field <- Gen.oneOf("fps", "width", "height", "crf", "x264Preset", "audioBitrate")
          positive <- Gen.choose(1, 2000000)
          crf <- Gen.choose(0, 100)
          preset <- Gen.oneOf(VideoEncodingPolicy.validX264Presets.toVector.sorted)
        } yield (profile, field, positive, crf, preset)
        val property = Prop.forAll(cases) { case (profile, field, positive, crf, preset) =>
          val baseline = _renderer(Json.obj("qualityProfile" -> Json.fromString(profile))).toOption.get.resolveEncoding
          val value = field match {
            case "x264Preset" => Json.fromString(preset)
            case "crf" => Json.fromInt(crf)
            case _ => Json.fromInt(positive)
          }
          val expected = field match {
            case "fps" => baseline.copy(fps = positive)
            case "width" => baseline.copy(width = positive)
            case "height" => baseline.copy(height = positive)
            case "crf" => baseline.copy(crf = crf)
            case "x264Preset" => baseline.copy(x264Preset = Some(preset))
            case "audioBitrate" => baseline.copy(audioBitrate = positive.toString)
          }
          val resolved = _renderer(Json.obj("qualityProfile" -> Json.fromString(profile), field -> value)).toOption.get.resolveEncoding
          resolved == expected.copy(origins = baseline.origins.updated(field, "explicit"))
        }
        When("ScalaCheck interprets individual overrides across the generated selections")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withInitialSeed(71801L), property)
        Then("every explicit override preserves all other resolved fields and origins")
        result.passed shouldBe true
        result.succeeded should be >= 100
      }

      "override legacy audio independently of picture and retain its explicit origin" in {
        Given("a historical quality policy with an authored audio bitrate")
        val descriptor = Json.obj("policy" -> Json.fromString("quality"), "audioBitrate" -> Json.fromString(" 128k "))
        When("the legacy renderer resolves its joint settings")
        val settings = _renderer(descriptor).toOption.get.resolveEncoding
        Then("only audio replaces the historical AAC320 intent")
        (settings.fps, settings.width, settings.height, settings.crf) shouldBe ((30, 1920, 1080, 18))
        settings.audioBitrate shouldBe "128k"
        settings.origins shouldBe _origins("legacy-policy").updated("audioBitrate", "explicit")
      }

      "leave presentation, strategy, effects and operating choices independent of root quality" in {
        Given("a root quality selection, presentation fields, part quality and separate host hardware or Docker software choices")
        val rootrenderer = Json.obj("qualityProfile" -> Json.fromString("lightweight"),
          "engine" -> Json.fromString("remotion"), "strategy" -> Json.fromString("narration-card"),
          "effectProfile" -> Json.fromString("dialogue"))
        val descriptor = Json.obj("profile" -> Json.fromString("explanation"), "renderer" -> rootrenderer,
          "tools" -> Json.obj("toolMode" -> Json.fromString("docker")),
          "parts" -> Json.arr(Json.obj("renderer" -> Json.obj("qualityProfile" -> Json.fromString("high-quality")))))
        When("the project and both independent encoder policies are interpreted")
        val project = descriptor.as[VideoProject].toOption.get
        val settings = VideoRenderer.resolveEncoding(project.renderer)
        val hardware = CozyVideoEncoder.resolve(_hardware, settings)
        val software = CozyVideoEncoder.resolve(VideoExecutionConfig(VideoToolMode.Docker, "image", "url"), settings)
        Then("root lightweight quality survives all presentation, part and operating selections")
        settings.effectiveQualityProfile shouldBe CozyVideoQuality.Lightweight
        project.profile shouldBe Some("explanation")
        project.renderer.get.strategyOrPolicy shouldBe Some("narration-card")
        project.renderer.get.effectProfile shouldBe Some("dialogue")
        settings.audioBitrate shouldBe "96k"
        hardware.bitrate shouldBe Some("1M")
        software.crf shouldBe Some(36)
        software.x264Preset shouldBe Some("superfast")
      }
    }
  }

  "Audio bitrate interpretation" should {
    "normalize positive bitrate values" which {
      "accept trimmed decimal and k/M strings and integral JSON up to signed 64-bit maximum" in {
        Given("valid strings, scaled boundaries and integral JSON bitrate values")
        val values = Vector(
          Json.fromString(" 96k ") -> "96k", Json.fromString("1M") -> "1M",
          Json.fromString("1") -> "1", Json.fromString(Long.MaxValue.toString) -> Long.MaxValue.toString,
          Json.fromString("9223372036854775k") -> "9223372036854775k",
          Json.fromString("9223372036854M") -> "9223372036854M",
          Json.fromInt(128000) -> "128000", Json.fromBigDecimal(BigDecimal("192000.0")) -> "192000",
          Json.fromLong(Long.MaxValue) -> Long.MaxValue.toString
        )
        When("audio bitrate fields are decoded through the renderer boundary")
        val results = values.map { case (value, expected) => (_renderer(Json.obj("audioBitrate" -> value)), expected) }
        Then("successful values provide normalized individual argv elements")
        results.foreach { case (result, expected) => result.toOption.get.audioBitrate shouldBe Some(expected) }
      }

      "normalize generated positive bps and scaled strings without imposing a bitrate cap" in {
        Given("generated positive bps and k/M values within signed 64-bit range")
        val cases = for {
          number <- Gen.choose(1, 2000000000)
          suffix <- Gen.oneOf("", "k", "M")
        } yield (number, suffix)
        val property = Prop.forAll(cases) { case (number, suffix) =>
          val text = s"$number$suffix"
          val stringresult = _renderer(Json.obj("audioBitrate" -> Json.fromString(s" $text ")))
          val numberresult = _renderer(Json.obj("audioBitrate" -> Json.fromInt(number)))
          stringresult.toOption.flatMap(_.audioBitrate).contains(text) &&
            numberresult.toOption.flatMap(_.audioBitrate).contains(number.toString)
        }
        When("ScalaCheck interprets positive integer and suffix combinations")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withInitialSeed(71802L), property)
        Then("valid authored bitrates retain normalized text without an arbitrary upper audio cap")
        result.passed shouldBe true
        result.succeeded should be >= 100
      }
    }

    "reject malformed authored bitrate values" which {
      "return failures for null, types, fractions, suffixes, nonpositive values and scaled overflow" in {
        Given("explicit null, malformed types, invalid grammar and signed 64-bit overflow")
        val values = Vector(Json.Null, Json.True, Json.obj(), Json.arr(Json.fromInt(1)),
          Json.fromInt(0), Json.fromInt(-1), Json.fromBigDecimal(BigDecimal("1.5")),
          Json.fromBigInt(BigInt(Long.MaxValue) + 1)) ++
          Vector("", " ", "0", "-1", "1.5", "1m", "1K", "1G", "+1", "1e3", "1 k",
            "9223372036854775808", "9223372036854776k", "9223372036855M").map(Json.fromString)
        When("invalid fields are decoded even alongside a valid canonical selection")
        val results = values.map(value => _renderer(Json.obj("qualityProfile" -> Json.fromString("high-quality"), "audioBitrate" -> value)))
        Then("all failures remain Decoder results identifying the audio field")
        results.foreach { result =>
          result.isLeft shouldBe true
          result.left.toOption.get.message should include("renderer.audioBitrate")
        }
      }
    }
  }

  "Encoder compatibility" should {
    "interpret canonical and legacy props" which {
      "prefer canonical hardware defaults while retaining old props and explicit operating bitrate" in {
        Given("canonical props over a different valid old policy, original legacy props and explicit video bitrate")
        val canonicalprops = Json.obj("qualityProfile" -> Json.fromString("lightweight"), "encodingPolicy" -> Json.fromString("quality"))
        val canonicalonlyprops = Json.obj("qualityProfile" -> Json.fromString("lightweight"))
        val legacyprops = VideoEncodingPolicy.values.map(policy => Json.obj("encodingPolicy" -> Json.fromString(policy.name)))
        val explicit = _hardware.copy(encoderBitrate = Some(" 4500k "), encoderBitrateOrigin = "operating-explicit")
        When("hardware encoding interprets canonical, historical and explicit operating selections")
        val canonical = CozyVideoEncoder.fromProps(_hardware, canonicalprops)
        val canonicalonly = CozyVideoEncoder.fromProps(_hardware, canonicalonlyprops)
        val legacy = legacyprops.map(props => CozyVideoEncoder.fromProps(_hardware, props))
        val overrideencoder = CozyVideoEncoder.fromProps(explicit, canonicalprops)
        Then("canonical practice uses 1M, old mappings stay 2M/6M/12M and operating bitrate wins independently")
        canonical.bitrate shouldBe Some("1M")
        canonicalonly shouldBe canonical
        legacy.map(_.bitrate) shouldBe Vector(Some("2M"), Some("6M"), Some("12M"))
        overrideencoder.bitrate shouldBe Some("4500k")
        overrideencoder.bitrateOrigin shouldBe "operating-explicit"
        canonical.arguments should contain allOf ("-allow_sw", "-require_sw", "0")
        canonical.crf shouldBe None
        canonical.x264Preset shouldBe None
        canonical.tune shouldBe None
      }

      "reject an invalid authored canonical prop even with a valid legacy or explicit operating selection" in {
        Given("invalid canonical prop tokens and types with otherwise valid old policy and bitrate")
        val values = Vector(Json.Null, Json.True, Json.fromInt(1), Json.fromString("Standard"), Json.fromString("archive"))
        val execution = _hardware.copy(encoderBitrate = Some("3M"))
        values.foreach { value =>
          val props = Json.obj("qualityProfile" -> value, "encodingPolicy" -> Json.fromString("quality"), "crf" -> Json.fromInt(18))
          When("hardware and software encoders interpret the malformed canonical prop")
          val hardwarefailure = intercept[Throwable](CozyVideoEncoder.fromProps(execution, props))
          val softwarefailure = intercept[Throwable](CozyVideoEncoder.fromProps(VideoExecutionConfig(VideoToolMode.Host, "image", "url"), props))
          Then("both reject invalid canonical intent rather than falling through to legacy or operating values")
          hardwarefailure.getMessage should include("qualityProfile")
          softwarefailure.getMessage should include("qualityProfile")
        }
      }
    }

    "preserve encoder execution constraints" which {
      "require host hardware with no fallback and keep software CRF, preset and stillimage tune" in {
        Given("practice settings with host software, host hardware and contradictory operating selections")
        val settings = _renderer(Json.obj("qualityProfile" -> Json.fromString("lightweight"))).toOption.get.resolveEncoding
        val softwareexecution = VideoExecutionConfig(VideoToolMode.Host, "image", "url")
        val dockerhardware = _hardware.copy(toolMode = VideoToolMode.Docker)
        val softwarebitrate = softwareexecution.copy(encoderBitrate = Some("2M"))
        When("encoder policy interprets valid software and hardware plus the two contradictions")
        val software = CozyVideoEncoder.resolve(softwareexecution, settings, stillImage = true)
        val hardware = CozyVideoEncoder.resolve(_hardware, settings, stillImage = true)
        val hostfailure = intercept[Throwable](CozyVideoEncoder.resolve(dockerhardware, settings))
        val bitratefailure = intercept[Throwable](CozyVideoEncoder.resolve(softwarebitrate, settings))
        Then("software controls remain active, hardware disables fallback, and contradictions fail")
        software.crf shouldBe Some(36)
        software.x264Preset shouldBe Some("superfast")
        software.tune shouldBe Some("stillimage")
        software.bitrate shouldBe None
        hardware.arguments shouldBe Vector("-c:v", "h264_videotoolbox", "-b:v", "1M", "-allow_sw", "0", "-require_sw", "0")
        hostfailure.getMessage should include("requires explicitly selected host FFmpeg")
        bitratefailure.getMessage should include("contradicts libx264")
      }

      "retain nine-field renderer and six-field resolved settings construction semantics" in {
        Given("original positional constructors using each historical policy")
        val constructed = VideoEncodingPolicy.values.map { policy =>
          val renderer = VideoRenderer(None, None, Some(policy), None, None, None, None, None, None)
          val encoding = ResolvedEncodingSettings(policy, policy.fps, policy.width, policy.height, policy.crf, policy.x264Preset)
          (renderer, encoding)
        }
        When("legacy internal settings are interpreted for joint resolution and hardware defaults")
        val results = constructed.map { case (renderer, encoding) =>
          (renderer.resolveEncoding, encoding, CozyVideoEncoder.resolve(_hardware, encoding))
        }
        Then("source constructors remain usable with historical picture, canonical fallback and AAC320 audio")
        results.zip(Vector("standard" -> "2M", "quality" -> "6M", "high-quality" -> "12M")).foreach {
          case ((resolved, legacy, encoder), (canonical, bitrate)) =>
            legacy.qualityProfile shouldBe None
            legacy.origins shouldBe empty
            legacy.effectiveQualityProfile.name shouldBe canonical
            legacy.audioBitrate shouldBe "320k"
            resolved.audioBitrate shouldBe "320k"
            (resolved.fps, resolved.width, resolved.height, resolved.crf) shouldBe ((legacy.fps, legacy.width, legacy.height, legacy.crf))
            encoder.bitrate shouldBe Some(bitrate)
        }
      }
    }
  }
}

private object CozyVideoQualityProfileSpec {
  private val _profiles = Vector(
    ("lightweight", 12, 854, 480, 36, Some("superfast"), "96k", "1M", "lightweight"),
    ("standard", 18, 1280, 720, 32, None, "128k", "2M", "lightweight"),
    ("quality", 30, 1280, 720, 23, None, "192k", "6M", "standard"),
    ("high-quality", 30, 1920, 1080, 18, None, "320k", "12M", "quality")
  )

  private def _origins(source: String): Map[String, String] =
    Vector("qualityProfile", "fps", "width", "height", "crf", "x264Preset", "audioBitrate").map(_ -> source).toMap

  private def _renderer(descriptor: Json): Decoder.Result[VideoRenderer] = descriptor.as[VideoRenderer]

  private def _hardware: VideoExecutionConfig =
    VideoExecutionConfig(VideoToolMode.Host, "image", "url", encoderName = "h264_videotoolbox")
}
