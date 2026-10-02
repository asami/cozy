package cozy.video

import java.awt.image.BufferedImage
import java.nio.{ByteBuffer, ByteOrder}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.nio.file.attribute.{FileTime, PosixFilePermission}
import javax.imageio.ImageIO
import scala.collection.JavaConverters._
import io.circe.{Json, parser}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import cozy.CozySpecVocabulary

/*
 * @since   Oct.  2, 2026
 * @version Oct.  2, 2026
 */
final class CozyVideoDialogueContinuitySpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  private val _regions = Vector(
    "heading" -> (118, 90, 1150, 140),
    "central_visual" -> (290, 145, 995, 410),
    "background_left" -> (0, 0, 70, 330),
    "background_right" -> (1210, 0, 1280, 330)
  )

  "An author continuing a visual across dialogue" should {
    "preserve the selected visual while speakers change" which {
      "stage fixed palettes and authored silence without adding an answer entrance" in {
        Given("positive scene durations, authored lead silence and frame rates from 12 through 30")
        val cases = for {
          fps <- Gen.choose(12, 30)
          duration <- Gen.choose(20, 40).map(_.toDouble / 10)
          lead <- Gen.choose(0, 5).map(_.toDouble / 10)
        } yield (fps, duration, lead)
        val property = Prop.forAll(cases) { case (fps, duration, lead) =>
          Vector("title", "concept", "closing").foreach { preset =>
            Given(s"diagram and item-slide question/answer pairs with the fixed $preset preset at $fps fps")
            val dir = _fixture(fps, duration, lead, preset)
            val before = _source_snapshot(dir)
            When("the public render operation stages the declared legacy dialogue through the production adapter")
            val output = CozyVideo.render(
              CozyVideo.RenderConfig(dir.resolve("video.json"), "remotion", toolMode = Some("host")),
              CozyVideo.VideoToolRegistry(Vector.empty), ProfileRenderRunner()
            )
            And("the generated props and staged assets are interpreted as the dialogue result")
            val props = _json(dir.resolve("target/cozy-video/remotion/dialogue/props.json"))
            val scenes = _scenes(props)
            val images = Vector(0, 1).map { index =>
              val image = scenes(index).hcursor.downField("visual").get[String]("image").toOption.get
              Files.readAllBytes(dir.resolve("target/cozy-video/remotion/dialogue/public").resolve(image)).toVector
            }
            val diagram = Files.readAllBytes(dir.resolve("assets/diagram.svg")).toVector
            val after = _source_snapshot(dir)
            Then("both answers retain their base visual and authored silence, with adjacent scene boundaries")
            output should include_text("Cozy Video Render")
            Vector(0 -> 1, 2 -> 3).foreach { case (baseindex, answerindex) =>
              val base = scenes(baseindex)
              val answer = scenes(answerindex)
              answer.hcursor.downField("visual").get[String]("baseSceneId").toOption shouldBe
                base.hcursor.get[String]("id").toOption
              Vector("heading", "items", "kind", "layout").foreach { field =>
                answer.hcursor.downField("visual").downField(field).focus shouldBe
                  base.hcursor.downField("visual").downField(field).focus
              }
              answer.hcursor.downField("effects").get[String]("preset").toOption shouldBe Some(preset)
              answer.hcursor.get[String]("section").toOption shouldBe base.hcursor.get[String]("section").toOption
              _int(answer, "startFrame") shouldBe _int(base, "startFrame") + _int(base, "durationFrames")
              _int(answer, "sectionTransitionFrames") shouldBe 0
              _int(answer, "leadInFrames") shouldBe math.round(lead * fps).toInt
              _int(answer, "durationFrames") shouldBe math.round(duration * fps).toInt
            }
            And("the genuine section change adds its entrance separately and source staging is read-only")
            _int(scenes(2), "sectionTransitionFrames") shouldBe math.round(1.2 * fps).toInt
            _int(scenes(2), "leadInFrames") shouldBe math.round(lead * fps).toInt + math.round(1.2 * fps).toInt
            _int(scenes(2), "durationFrames") shouldBe math.round(duration * fps).toInt + math.round(1.2 * fps).toInt
            images shouldBe Vector(diagram, diagram)
            after shouldBe before
          }
          true
        }
        When("the continuity use case is interpreted for twelve randomized timing cases")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(12), property)
        Then("every randomized case satisfies the dialogue contract")
        result.passed shouldBe true
      }

      "keep settled diagram and item-slide regions continuous in the actual Remotion output" in {
        Given("the explicitly enabled Docker Remotion regression with four short scenes and declared SVG assets")
        if (!sys.props.get("cozy.video.remotion.integration").contains("true"))
          cancel("Set -Dcozy.video.remotion.integration=true to execute the native continuity regression.")
        val image = sys.props.getOrElse("cozy.video.remotion.image", CozyVideo.VideoToolSettings.DEFAULT_DOCKER_IMAGE)
        val dir = _fixture(18, 2.5, 0.0, "concept")
        val before = _source_snapshot(dir)
        When("the public render operation uses the actual bundled renderer and Docker toolchain")
        CozyVideo.render(
          CozyVideo.RenderConfig(dir.resolve("video.json"), "remotion", checkTools = true,
            toolMode = Some("docker"), dockerImage = Some(image)),
          CozyVideo.VideoToolRegistry.default, CozyVideo.VideoProcessRunner.default
        )
        And("absolute boundary and entrance frames are extracted and measured before expectations")
        val props = _json(dir.resolve("target/cozy-video/remotion/dialogue/props.json"))
        val scenes = _scenes(props)
        val opening = props.hcursor.downField("timing").get[Int]("openingFrames").toOption.get
        val measurements = Vector(0 -> 1, 2 -> 3).map { case (baseindex, answerindex) =>
          val first = opening + _int(scenes(baseindex), "startFrame")
          val boundary = opening + _int(scenes(answerindex), "startFrame")
          val initial = _frame(dir, image, first)
          val settled = _frame(dir, image, boundary - 1)
          val answer = _frame(dir, image, boundary)
          val continuity = _regions.map { case (name, region) => name -> _difference(settled, answer, region) }
          val entrance = _regions.take(2).map { case (name, region) => name -> _difference(initial, settled, region) }
          (boundary - 1, boundary, continuity, entrance)
        }
        val sectionstart = opening + _int(scenes(2), "startFrame")
        val midframe = sectionstart + _int(scenes(2), "sectionTransitionFrames") / 2
        val mid = _frame(dir, image, midframe)
        val sectionsettled = _frame(dir, image, opening + _int(scenes(3), "startFrame") - 1)
        val sweepdifference = _difference(mid, sectionsettled, (0, 0, 1280, 720))
        val after = _source_snapshot(dir)
        val record = Json.obj(
          "boundaries" -> Json.fromValues(measurements.map { case (last, first, continuity, entrance) =>
            Json.obj("lastBaseFrame" -> Json.fromInt(last), "firstAnswerFrame" -> Json.fromInt(first),
              "continuity" -> _measurement_json(continuity), "entrance" -> _measurement_json(entrance))
          }),
          "midSectionFrame" -> Json.fromInt(midframe),
          "sectionSweepMeanRgbDifference" -> Json.fromDoubleOrNull(sweepdifference._1)
        )
        _write(dir.resolve("measurements.json"), record.spaces2)
        info(s"Native frame/region measurements retained at ${dir.resolve("measurements.json")}: ${record.noSpaces}")
        Then("both settled visuals remain within the declared lossy-video bounds across the speaker change")
        measurements.foreach { case (_, _, continuity, entrance) =>
          continuity.foreach { case (name, (mean, fraction)) =>
            withClue(s"$name continuity: ") {
              mean should be <= 2.0
              fraction should be <= 0.01
            }
          }
          And("heading and central content are each non-vacuous because their own entrance changes the image")
          entrance.foreach { case (name, (mean, _)) =>
            withClue(s"$name entrance: ") { mean should be > 1.0 }
          }
        }
        And("captions and speakers differ while the new section retains its distinct sweep")
        Vector(0 -> 1, 2 -> 3).foreach { case (baseindex, answerindex) =>
          scenes(baseindex).hcursor.get[String]("caption").toOption should not be
            scenes(answerindex).hcursor.get[String]("caption").toOption
          scenes(baseindex).hcursor.get[String]("speaker").toOption should not be
            scenes(answerindex).hcursor.get[String]("speaker").toOption
          _int(scenes(answerindex), "leadInFrames") shouldBe 0
          _int(scenes(answerindex), "sectionTransitionFrames") shouldBe 0
        }
        _int(scenes(2), "sectionTransitionFrames") shouldBe math.round(1.2 * 18).toInt
        sweepdifference._1 should be > 1.0
        after shouldBe before
      }
    }
  }

  private def _fixture(fps: Int, duration: Double, lead: Double, preset: String): Path = {
    val root = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/test-generated/video-dialogue-continuity")
    Files.createDirectories(root)
    val dir = Files.createTempDirectory(root, "dialogue-")
    _write(dir.resolve("assets/guide.svg"), """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 240"><rect width="160" height="240" fill="#284b63"/><circle cx="80" cy="70" r="45" fill="#f3c6a8"/></svg>""")
    _write(dir.resolve("assets/reviewer.svg"), """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 240"><rect width="160" height="240" fill="#5b2c6f"/><circle cx="80" cy="70" r="45" fill="#e8b894"/></svg>""")
    _write(dir.resolve("assets/diagram.svg"), """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 700 250"><rect width="700" height="250" rx="20" fill="#2451ff"/><rect x="35" y="30" width="260" height="190" fill="#f4d35e"/><rect x="405" y="30" width="260" height="190" fill="#82c0cc"/><path d="M305 125h85" stroke="#fff" stroke-width="12"/></svg>""")
    val diagram = Json.obj("heading" -> Json.fromString("A stable diagram"), "image" -> Json.fromString("assets/diagram.svg"))
    val items = Json.obj("kind" -> Json.fromString("item-slide"), "layout" -> Json.fromString("compact"),
      "heading" -> Json.fromString("A stable item slide"), "items" -> Json.arr(Json.obj("title" -> Json.fromString("Review the model"), "subtitle" -> Json.fromString("Check the structure")), Json.obj("title" -> Json.fromString("Execute the model"), "subtitle" -> Json.fromString("Observe the behavior"))))
    val scenes = Vector(("diagram-question", "guide", "Question about the diagram", "foundation", diagram),
      ("diagram-answer", "reviewer", "Answer about the diagram", "foundation", diagram.mapObject(_.add("baseSceneId", Json.fromString("diagram-question")))),
      ("items-question", "guide", "Question about the items", "next-section", items),
      ("items-answer", "reviewer", "Answer about the items", "next-section", items.mapObject(_.add("baseSceneId", Json.fromString("items-question")))))
    val characters = Json.obj(Vector("guide" -> "left", "reviewer" -> "right").map { case (id, side) =>
      id -> Json.obj("asset" -> Json.fromString(s"assets/$id.svg"), "mouthClosedAsset" -> Json.fromString(s"assets/$id.svg"),
        "mouthOpenAsset" -> Json.fromString(s"assets/$id.svg"), "side" -> Json.fromString(side))
    }: _*)
    _write(dir.resolve("script.json"), Json.obj("characters" -> characters,
      "sections" -> Json.arr(Json.obj("id" -> Json.fromString("foundation"), "title" -> Json.fromString("Foundation")),
        Json.obj("id" -> Json.fromString("next-section"), "title" -> Json.fromString("Next section"))),
      "scenes" -> Json.fromValues(scenes.map { case (id, speaker, caption, section, visual) =>
        Json.obj("id" -> Json.fromString(id), "speaker" -> Json.fromString(speaker), "line" -> Json.fromString(caption),
          "caption" -> Json.fromString(caption), "section" -> Json.fromString(section), "duration" -> Json.fromDoubleOrNull(duration),
          "effects" -> Json.obj("preset" -> Json.fromString(preset)), "visual" -> visual)
      })).spaces2)
    _write(dir.resolve("video.json"), s"""{"renderer":{"engine":"remotion","fps":$fps,"width":1280,"height":720,"strategy":"slide","effectProfile":"compact"},"visualEffects":{"opening":"title-hold-subtle-motion","sectionStart":"line-sweep"},"parts":[{"id":"dialogue","type":"dialogue","script":"script.json","audioDir":"build/audio/dialogue","output":"build/parts/dialogue.mp4"}]}""")
    val audio = dir.resolve("build/audio/dialogue")
    Files.createDirectories(audio)
    val entries = scenes.zipWithIndex.map { case ((id, speaker, _, _, _), index) =>
      val name = f"${index + 1}%02d-$id.wav"
      Files.write(audio.resolve(name), _wav_bytes(0.2))
      Json.obj("sceneId" -> Json.fromString(id), "speaker" -> Json.fromString(speaker), "file" -> Json.fromString(name),
        "leadSilence" -> Json.fromDoubleOrNull(lead), "audioDuration" -> Json.fromDoubleOrNull(0.2),
        "targetDuration" -> Json.fromDoubleOrNull(duration), "tailSilence" -> Json.fromDoubleOrNull(0.0))
    }
    _write(audio.resolve("manifest.json"), Json.fromValues(entries).spaces2)
    dir
  }

  private def _source_snapshot(dir: Path): Vector[(String, Vector[Byte], Set[PosixFilePermission], FileTime)] = {
    val paths = Vector("script.json", "video.json", "assets/guide.svg", "assets/reviewer.svg", "assets/diagram.svg")
    paths.map { name =>
      val path = dir.resolve(name)
      (name, Files.readAllBytes(path).toVector, Files.getPosixFilePermissions(path).asScala.toSet, Files.getLastModifiedTime(path))
    }
  }

  private def _frame(dir: Path, image: String, frame: Int): BufferedImage = {
    val output = dir.resolve(s"frame-$frame.png")
    val args = Vector("docker", "run", "--rm", "--network=none", "-v", s"$dir:/workspace", "-w", "/workspace", image,
      "ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-i", "/workspace/build/parts/dialogue.mp4",
      "-vf", s"select=eq(n\\,$frame)", "-frames:v", "1", "-y", s"/workspace/frame-$frame.png")
    val result = CozyVideo.VideoProcessRunner.default.run(args, dir)
    if (result.exitCode != 0) throw new IllegalStateException(s"Frame $frame extraction failed: $result")
    Option(ImageIO.read(output.toFile)).getOrElse(throw new IllegalStateException(s"Unreadable frame: $output"))
  }

  private def _difference(before: BufferedImage, after: BufferedImage, region: (Int, Int, Int, Int)): (Double, Double) = {
    val (left, top, right, bottom) = region
    var total = 0L
    var changed = 0L
    for (y <- top until bottom; x <- left until right) {
      val a = before.getRGB(x, y)
      val b = after.getRGB(x, y)
      val differences = Vector(16, 8, 0).map(shift => math.abs(((a >> shift) & 255) - ((b >> shift) & 255)))
      total += differences.sum
      if (differences.exists(_ > 20)) changed += 1
    }
    val pixels = (right - left).toLong * (bottom - top)
    (total.toDouble / (pixels * 3), changed.toDouble / pixels)
  }

  private def _measurement_json(values: Vector[(String, (Double, Double))]): Json =
    Json.obj(values.map { case (name, (mean, fraction)) =>
      name -> Json.obj("region" -> Json.fromValues(_regions.find(_._1 == name).get._2.productIterator.map(x => Json.fromInt(x.asInstanceOf[Int])).toVector),
        "meanRgbDifference" -> Json.fromDoubleOrNull(mean), "fractionAbove20" -> Json.fromDoubleOrNull(fraction))
    }: _*)

  private def _wav_bytes(duration: Double): Array[Byte] = {
    val samples = (24000 * duration).toInt
    val buffer = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + samples * 2)
    buffer.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort(1.toShort).putShort(1.toShort)
    buffer.putInt(24000).putInt(48000).putShort(2.toShort).putShort(16.toShort)
    buffer.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(samples * 2)
    buffer.array()
  }

  private def _scenes(props: Json): Vector[Json] = props.hcursor.get[Vector[Json]]("scenes").toOption.get
  private def _int(value: Json, field: String): Int = value.hcursor.get[Int](field).toOption.get
  private def _json(path: Path): Json = parser.parse(Files.readString(path, StandardCharsets.UTF_8)).toOption.get
  private def _write(path: Path, contents: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, contents, StandardCharsets.UTF_8)
    ()
  }
}
