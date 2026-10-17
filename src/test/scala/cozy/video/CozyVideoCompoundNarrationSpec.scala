package cozy.video

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import java.nio.file.attribute.FileTime
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import io.circe.{Json, parser}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import cozy.CozySpecVocabulary

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoCompoundNarrationSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  import CozyVideoCompoundNarrationSpec._
  import CozyVideoSpec.RecordingVoicevoxClient

  private val _surface = "モデル・アップ・ダウン"
  private val _reading = "モデルアップダウン"
  private val _target_id = "updown-question"
  private val _target_line = "モデル・アップ・ダウンは、どんな考え方なの？"
  private val _tuning_fields = Vector(
    "speedScale", "pitchScale", "intonationScale", "volumeScale", "prePhonemeLength", "postPhonemeLength"
  )

  "An author preserving compound terms on screen" should {
    "supply their intended reading through public narration synthesis" which {
      "preserve display and configuration across generated contexts and dictionary-only regeneration" in {
        Given("numeric ASCII contexts and requested timing, with omitted, disabled and enabled middle-dot normalization")
        val cases = for {
          prefix <- Gen.choose(10, 9999)
          suffix <- Gen.choose(10, 9999)
          duration <- Gen.choose(3, 10).map(_.toDouble / 10)
          tail <- Gen.choose(0, 4).map(_.toDouble / 10)
        } yield (prefix, suffix, duration, tail)
        val property = Prop.forAll(cases) { case (prefix, suffix, duration, tail) =>
          Vector(None, Some(false), Some(true)).foreach { enabled =>
            withClue(s"context=$prefix/$suffix duration=$duration tail=$tail removeMiddleDots=$enabled: ") {
              Given("an authored dotted display term and a dictionary reading in a retained working script")
              val dir = _directory("property-")
              val script = dir.resolve("script.json")
              val output = dir.resolve("audio")
              val line = s"$prefix${_surface}!?、。$suffix"
              val base = Json.obj(
                "narration" -> Json.obj("provider" -> Json.fromString("voicevox")),
                "voice" -> Json.obj("fallbackSpeakerId" -> Json.fromInt(42), "speedScale" -> Json.fromDoubleOrNull(0.98)),
                "pronunciations" -> Json.obj(_surface -> Json.fromString(_reading)),
                "scenes" -> Json.arr(Json.obj(
                  "id" -> Json.fromString("compound"), "line" -> Json.fromString(line),
                  "caption" -> Json.fromString(line),
                  "visual" -> Json.obj("heading" -> Json.fromString(_surface)),
                  "duration" -> Json.fromDoubleOrNull(duration), "tailSilence" -> Json.fromDoubleOrNull(tail)
                ))
              )
              val original = enabled.map { option =>
                base.deepMerge(Json.obj("voiceTextNormalization" -> Json.obj("removeMiddleDots" -> Json.fromBoolean(option))))
              }.getOrElse(base)
              _write_json(script, original)
              val before = _snapshot(script)
              val firstclient = RecordingVoicevoxClient()
              When("the public synthesis operation interprets the compound reading and requested timing")
              CozyVideo.synthesize(CozyVideo.SynthesizeConfig(script, output, Some("http://voicevox.example")), firstclient)
              val firstmanifest = _json(output.resolve("manifest.json"))
              _retain_audio(output, dir.resolve("baseline"))

              Then("explicit final speech preserves punctuation, and source, display, configuration and actual outputs are retained")
              firstclient.calls.filter(_.kind == "audio_query").flatMap(_.text).toVector shouldBe
                Vector(s"$prefix${_reading}!?、。$suffix")
              _snapshot(script) shouldBe before
              _json(script) shouldBe original
              _entries(firstmanifest).head.hcursor.get[Double]("targetDuration").toOption shouldBe Some(duration)
              _entries(firstmanifest).head.hcursor.get[Double]("tailSilence").toOption.get should be >= tail
              _expect_wav(output.resolve("01-compound.wav"), nonsilent = false)
              _expect_wav(output.resolve("script.wav"), nonsilent = false)
              firstclient.audioQueries.head.hcursor.get[Double]("speedScale").toOption shouldBe Some(0.98)

              Given("only the dictionary reading changes to a reading that itself contains U+30FB")
              val changed = original.deepMerge(Json.obj("pronunciations" -> Json.obj(_surface -> Json.fromString("モデル・アップダウン"))))
              _write_json(script, changed)
              val edited = _snapshot(script)
              val secondclient = RecordingVoicevoxClient()
              When("public synthesis is requested again at the same script and save paths")
              CozyVideo.synthesize(CozyVideo.SynthesizeConfig(script, output, Some("http://voicevox.example")), secondclient)
              val secondmanifest = _json(output.resolve("manifest.json"))
              _retain_audio(output, dir.resolve("corrected"))
              Then("new provider calls and written audio use the selected speech-only policy while display and tuning remain authored")
              val expected = if (enabled.contains(true)) s"$prefix${_reading}!?、。$suffix" else s"${prefix}モデル・アップダウン!?、。$suffix"
              secondclient.calls.filter(_.kind == "audio_query").flatMap(_.text).toVector shouldBe Vector(expected)
              secondclient.calls.count(_.kind == "synthesis") shouldBe 1
              _without_pronunciations(_json(script)) shouldBe _without_pronunciations(original)
              _snapshot(script) shouldBe edited
              _entries(secondmanifest).map(_.hcursor.get[String]("sceneId").toOption) shouldBe Vector(Some("compound"))
              _expect_wav(output.resolve("01-compound.wav"), nonsilent = false)
              _expect_wav(output.resolve("script.wav"), nonsilent = false)
              secondclient.audioQueries.head.hcursor.get[Double]("speedScale").toOption shouldBe Some(0.98)
            }
          }
          true
        }
        When("ScalaCheck interprets twenty generated compound-narration contexts")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)
        Then("every generated context satisfies the public compound-narration contract")
        withClue(s"compound-narration property result: $result") { result.passed shouldBe true }
      }

      "forward the actual full private driver to VOICEVOX and regenerate after a pronunciation-only edit" in {
        Given("explicit opt-in and the parent-selected safe repository-relative full Model Harness driver")
        if (!java.lang.Boolean.getBoolean("cozy.video.narration.integration"))
          cancel("Set -Dcozy.video.narration.integration=true and -Dcozy.video.narration.driver=<repo-relative script.json> for actual VOICEVOX evidence.")
        val source = _driver()
        val sourcebefore = _snapshot(source)
        val original = _json(source)
        val scenes = _scenes(original)
        val sceneids = scenes.map(_.hcursor.get[String]("id").toOption.get)
        val targetindex = sceneids.indexOf(_target_id)
        if (scenes.size != 34 || targetindex < 0 || sceneids.distinct.size != 34)
          fail("The actual driver must contain all 34 distinct scenes including updown-question.")
        if (scenes(targetindex).hcursor.get[String]("line").toOption != Some(_target_line))
          fail("The actual updown-question authored line differs from the frozen driver.")
        val dictionary = original.hcursor.downField("pronunciations").focus.getOrElse(fail("Missing original pronunciation map."))
        if (dictionary.hcursor.get[String](_surface).toOption != Some(_reading))
          fail("The actual driver must retain its original compound pronunciation mapping.")
        val endpoint = original.hcursor.downField("narration").get[String]("voicevoxUrl").toOption.getOrElse(fail("Missing declared VOICEVOX endpoint."))
        if (endpoint != "http://127.0.0.1:50021" || original.hcursor.downField("narration").get[String]("provider").toOption != Some("voicevox"))
          fail("The frozen driver must retain its declared local VOICEVOX provider.")
        if (original.hcursor.downField("voiceTextNormalization").get[Boolean]("removeMiddleDots").toOption.contains(true))
          fail("The actual-driver scenario must not enable global middle-dot removal.")
        val dir = _directory("native-")
        Files.copy(source, dir.resolve("original-source.json"), StandardCopyOption.COPY_ATTRIBUTES)
        val script = dir.resolve("script.json")
        val output = dir.resolve("audio")
        val baseline = original.mapObject(_.add("pronunciations", dictionary.mapObject(_.remove(_surface))))
        _write_json(script, baseline)
        _write_json(dir.resolve("working-before.json"), baseline)
        val config = CozyVideo.SynthesizeConfig(script, output, Some(endpoint))
        val firstclient = new NativeRecordingClient(dir.resolve("baseline/provider"))

        When("public synthesis delegates all 34 baseline scenes synchronously to VoicevoxClient.default")
        val firstresult = CozyVideo.synthesize(config, firstclient)
        val firstqueries = firstclient.queries
        val firstsyntheses = firstclient.syntheses
        _retain_audio(output, dir.resolve("baseline/audio"))
        val firstpass = _pass_observation(dir.resolve("baseline/audio"), sceneids, firstclient, firstresult)
        _write_json(dir.resolve("baseline/observations.json"), firstpass)
        _write_json(dir.resolve("observations.json"), Json.obj(
          "source" -> Json.fromString(source.toString), "sceneIds" -> Json.fromValues(sceneids.map(Json.fromString)),
          "baseline" -> firstpass, "listening" -> Json.fromString("pending")
        ))

        Given("the original full pronunciation map restored at the same working script path, with all other fields unchanged")
        _write_json(script, original)
        val workingbefore = _snapshot(script)
        _write_json(dir.resolve("working-after.json"), _json(script))
        val secondclient = new NativeRecordingClient(dir.resolve("corrected/provider"))
        When("public synthesis delegates 34 new queries and syntheses into the same audio directory")
        val secondresult = CozyVideo.synthesize(config, secondclient)
        val secondqueries = secondclient.queries
        val secondsyntheses = secondclient.syntheses
        _retain_audio(output, dir.resolve("corrected/audio"))
        val secondpass = _pass_observation(dir.resolve("corrected/audio"), sceneids, secondclient, secondresult)
        val phonetics = _prefix_phonetics(secondqueries(targetindex).query)
        val sourceafter = _snapshot(source)
        val workingafter = _snapshot(script)
        val record = Json.obj(
          "source" -> Json.fromString(source.toString), "retainedSource" -> Json.fromString(dir.resolve("original-source.json").toString),
          "workingScript" -> Json.fromString(script.toString), "saveDirectory" -> Json.fromString(output.toString),
          "sceneIds" -> Json.fromValues(sceneids.map(Json.fromString)), "targetSceneId" -> Json.fromString(_target_id),
          "targetIndex" -> Json.fromInt(targetindex), "originalJson" -> original,
          "workingBeforeJson" -> baseline, "workingAfterJson" -> _json(script),
          "sourceBefore" -> _snapshot_json(sourcebefore), "sourceAfter" -> _snapshot_json(sourceafter),
          "workingBeforeSynthesis" -> _snapshot_json(workingbefore), "workingAfterSynthesis" -> _snapshot_json(workingafter),
          "baseline" -> firstpass, "corrected" -> secondpass,
          "queryCount" -> Json.fromInt(firstqueries.size + secondqueries.size),
          "synthesisCount" -> Json.fromInt(firstsyntheses.size + secondsyntheses.size),
          "correctedPrefixPhonetics" -> phonetics, "listening" -> Json.fromString("pending")
        )
        _write_json(dir.resolve("corrected/observations.json"), secondpass)
        _write_json(dir.resolve("observations.json"), record)
        info(s"Actual compound-narration artifacts: ${dir.resolve("observations.json")}")

        Then("68 successful real query/synthesis pairs preserve the driver and regenerate current outputs from the corrected provider speech")
        firstqueries.size shouldBe 34
        firstsyntheses.size shouldBe 34
        secondqueries.size shouldBe 34
        secondsyntheses.size shouldBe 34
        firstqueries.map(_.speakerid) shouldBe secondqueries.map(_.speakerid)
        firstsyntheses.map(_.speakerid) shouldBe firstqueries.map(_.speakerid)
        secondsyntheses.map(_.speakerid) shouldBe secondqueries.map(_.speakerid)
        firstqueries(targetindex).text shouldBe _target_line
        secondqueries(targetindex).text shouldBe "モデルアップダウンは、どんな考え方なの？"
        firstqueries.zip(secondqueries).zipWithIndex.foreach { case ((before, after), index) =>
          if (index != targetindex) after.text shouldBe before.text
          before.baseurl shouldBe endpoint
          after.baseurl shouldBe endpoint
          val voice = _scene_voice(original, scenes(index))
          _tuning_fields.foreach { field =>
            val first = firstsyntheses(index).query.hcursor.downField(field).focus
            val second = secondsyntheses(index).query.hcursor.downField(field).focus
            second shouldBe first
            voice.hcursor.downField(field).focus.foreach { authored => second shouldBe Some(authored) }
          }
          val entry = _entries(_json(output.resolve("manifest.json")))(index)
          entry.hcursor.get[String]("sceneId").toOption shouldBe Some(sceneids(index))
          val current = output.resolve(entry.hcursor.get[String]("file").toOption.get)
          val returned = CozyVideoImplementation._normalize_provider_wav(secondsyntheses(index).wavbytes.toArray, sceneids(index))
          CozyVideoImplementation._read_wav(current).data.toVector shouldBe returned.data.toVector
        }
        _without_pronunciations(baseline) shouldBe _without_pronunciations(original)
        _json(script) shouldBe original
        _snapshot(dir.resolve("original-source.json")) shouldBe sourcebefore
        sourceafter shouldBe sourcebefore
        workingafter shouldBe workingbefore
        Vector(dir.resolve("baseline/audio"), dir.resolve("corrected/audio"), output).foreach { audio =>
          val entries = _entries(_json(audio.resolve("manifest.json")))
          entries.map(_.hcursor.get[String]("sceneId").toOption.get) shouldBe sceneids
          entries.foreach { entry => entry.hcursor.get[Double]("audioDuration").toOption.get should be > 0.0 }
          _expect_wav(audio.resolve(entries(targetindex).hcursor.get[String]("file").toOption.get), nonsilent = true)
          _expect_wav(audio.resolve("script.wav"), nonsilent = true)
        }
        And("the actual corrected query has nine uninterrupted compound moras, with listening still a separate acceptance requirement")
        phonetics.hcursor.get[Vector[String]]("moras").toOption shouldBe Some(Vector("モ", "デ", "ル", "ア", "ッ", "プ", "ダ", "ウ", "ン"))
        phonetics.hcursor.get[Boolean]("pauseBeforePrefixEnd").toOption shouldBe Some(false)
      }
    }
  }

  private def _directory(prefix: String): Path = {
    val root = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/test-generated/video-compound-narration")
    Files.createDirectories(root)
    Files.createTempDirectory(root, prefix)
  }

  private def _driver(): Path = {
    val root = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val supplied = sys.props.getOrElse("cozy.video.narration.driver", fail("Missing explicit cozy.video.narration.driver."))
    val relative = Paths.get(supplied)
    if (supplied.trim != supplied || supplied.isEmpty || relative.isAbsolute || relative.iterator().asScala.exists(x => Set(".", "..").contains(x.toString)))
      fail("The driver must be a safe repository-relative path.")
    val source = root.resolve(relative).normalize()
    if (!source.startsWith(root) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
      fail("The explicit driver is missing or outside the repository.")
    val components = relative.iterator().asScala.scanLeft(root)((parent, component) => parent.resolve(component))
    if (components.exists(Files.isSymbolicLink(_))) fail("Symbolic-link driver components are not admitted.")
    source
  }

  private def _snapshot(path: Path): SourceSnapshot = SourceSnapshot(
    Files.readAllBytes(path).toVector,
    Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS).asScala.map(_.toString).toSet,
    Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS)
  )

  private def _snapshot_json(snapshot: SourceSnapshot): Json = Json.obj(
    "bytes" -> Json.fromInt(snapshot.bytes.size),
    "mode" -> Json.fromValues(snapshot.mode.toVector.sorted.map(Json.fromString)),
    "modified" -> Json.fromString(snapshot.modified.toString)
  )

  private def _json(path: Path): Json = parser.parse(Files.readString(path, StandardCharsets.UTF_8)).fold(throw _, identity)
  private def _write_json(path: Path, json: Json): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, json.spaces2, StandardCharsets.UTF_8)
  }
  private def _scenes(json: Json): Vector[Json] = json.hcursor.get[Vector[Json]]("scenes").toOption.get
  private def _entries(json: Json): Vector[Json] = json.asArray.get
  private def _without_pronunciations(json: Json): Json = json.mapObject(_.remove("pronunciations"))
  private def _scene_voice(script: Json, scene: Json): Json = {
    val speaker = scene.hcursor.get[String]("speaker").toOption.get
    script.hcursor.downField("characters").downField(speaker).downField("voice").focus.get
  }

  private def _retain_audio(source: Path, destination: Path): Unit = {
    Files.createDirectories(destination)
    val files = Files.list(source)
    try files.iterator().asScala.foreach { file =>
      Files.copy(file, destination.resolve(file.getFileName), StandardCopyOption.COPY_ATTRIBUTES)
    } finally files.close()
  }

  private def _wav_observation(path: Path): Json = {
    val wave = CozyVideoImplementation._read_wav(path)
    Json.obj(
      "path" -> Json.fromString(path.toString), "bytes" -> Json.fromLong(Files.size(path)),
      "audioFormat" -> Json.fromInt(wave.audioformat), "sampleRate" -> Json.fromInt(wave.samplerate),
      "channels" -> Json.fromInt(wave.channels), "bitsPerSample" -> Json.fromInt(wave.bitspersample),
      "durationSeconds" -> Json.fromDoubleOrNull(wave.durationSeconds),
      "nonSilent" -> Json.fromBoolean(wave.data.exists(_ != 0.toByte))
    )
  }

  private def _expect_wav(path: Path, nonsilent: Boolean): Unit = {
    val wave = CozyVideoImplementation._read_wav(path)
    wave.audioformat shouldBe 1
    wave.samplerate shouldBe 24000
    wave.channels shouldBe 1
    wave.bitspersample shouldBe 16
    wave.data.size should be > 0
    wave.durationSeconds should be > 0.0
    if (nonsilent) wave.data.exists(_ != 0.toByte) shouldBe true
  }

  private def _pass_observation(audio: Path, sceneids: Vector[String], client: NativeRecordingClient, result: String): Json = {
    val manifest = _json(audio.resolve("manifest.json"))
    Json.obj(
      "result" -> Json.fromString(result), "manifestPath" -> Json.fromString(audio.resolve("manifest.json").toString),
      "manifest" -> manifest, "combinedWav" -> _wav_observation(audio.resolve("script.wav")),
      "queryCount" -> Json.fromInt(client.queries.size), "synthesisCount" -> Json.fromInt(client.syntheses.size),
      "calls" -> Json.fromValues(client.calls),
      "sceneAudio" -> Json.fromValues(_entries(manifest).zipWithIndex.map { case (entry, index) =>
        Json.obj("sceneId" -> Json.fromString(sceneids(index)),
          "wav" -> _wav_observation(audio.resolve(entry.hcursor.get[String]("file").toOption.get)))
      })
    )
  }

  private def _prefix_phonetics(query: Json): Json = {
    val phrases = query.hcursor.get[Vector[Json]]("accent_phrases").toOption.getOrElse(Vector.empty)
    var spoken = Vector.empty[String]
    var pause = false
    phrases.foreach { phrase =>
      if (spoken.size < 9) {
        spoken ++= phrase.hcursor.get[Vector[Json]]("moras").toOption.getOrElse(Vector.empty).
          flatMap(_.hcursor.get[String]("text").toOption)
        if (spoken.size < 9 && phrase.hcursor.downField("pause_mora").focus.exists(!_.isNull)) pause = true
      }
    }
    Json.obj("moras" -> Json.fromValues(spoken.take(9).map(Json.fromString)),
      "pauseBeforePrefixEnd" -> Json.fromBoolean(pause), "accentPhrases" -> Json.fromValues(phrases))
  }
}

object CozyVideoCompoundNarrationSpec {
  private final case class SourceSnapshot(bytes: Vector[Byte], mode: Set[String], modified: FileTime)
  private final case class NativeQuery(baseurl: String, text: String, speakerid: Int, query: Json)
  private final case class NativeSynthesis(speakerid: Int, query: Json, wavbytes: Vector[Byte])

  private final class NativeRecordingClient(directory: Path) extends CozyVideo.VoicevoxClient {
    private var _queries = Vector.empty[NativeQuery]
    private var _syntheses = Vector.empty[NativeSynthesis]
    private var _calls = Vector.empty[Json]
    def queries: Vector[NativeQuery] = _queries
    def syntheses: Vector[NativeSynthesis] = _syntheses
    def calls: Vector[Json] = _calls

    override def speakers(baseurl: String): Json = _record("speakers", baseurl, Json.obj()) {
      val returned = CozyVideo.VoicevoxClient.default.speakers(baseurl)
      (returned, Json.obj("returned" -> returned))
    }

    override def audioQuery(baseurl: String, text: String, speakerid: Int): Json =
      _record("audio_query", baseurl, Json.obj("text" -> Json.fromString(text), "speakerId" -> Json.fromInt(speakerid))) {
        val returned = CozyVideo.VoicevoxClient.default.audioQuery(baseurl, text, speakerid)
        _queries :+= NativeQuery(baseurl, text, speakerid, returned)
        (returned, Json.obj("returnedQuery" -> returned))
      }

    override def synthesis(baseurl: String, speakerid: Int, audioquery: Json): Array[Byte] =
      _record("synthesis", baseurl, Json.obj("speakerId" -> Json.fromInt(speakerid), "tunedQuery" -> audioquery)) {
        val returned = CozyVideo.VoicevoxClient.default.synthesis(baseurl, speakerid, audioquery)
        Files.createDirectories(directory)
        val path = directory.resolve(f"${_syntheses.size + 1}%02d-provider.wav")
        Files.write(path, returned)
        _syntheses :+= NativeSynthesis(speakerid, audioquery, returned.toVector)
        (returned, Json.obj("returnedWav" -> Json.fromString(path.toString), "returnedBytes" -> Json.fromInt(returned.length)))
      }

    private def _record[A](kind: String, baseurl: String, request: Json)(body: => (A, Json)): A = {
      val call = Json.obj("kind" -> Json.fromString(kind), "baseUrl" -> Json.fromString(baseurl)).deepMerge(request)
      try {
        val (returned, observation) = body
        _append(call.deepMerge(observation).deepMerge(Json.obj("result" -> Json.fromString("success"))))
        returned
      } catch {
        case NonFatal(error) =>
          _append(call.deepMerge(Json.obj("result" -> Json.fromString("failure"), "error" -> Json.fromString(error.toString))))
          throw error
      }
    }

    private def _append(call: Json): Unit = {
      _calls :+= call
      Files.createDirectories(directory)
      Files.writeString(directory.resolve("calls.json"), Json.fromValues(_calls).spaces2, StandardCharsets.UTF_8)
    }
  }
}
