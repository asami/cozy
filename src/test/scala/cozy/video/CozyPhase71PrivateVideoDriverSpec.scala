package cozy.video

import cozy.CozySpecVocabulary
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.FileTime
import java.nio.file.{Files, LinkOption, Path, Paths}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._

/*
 * @since   Sep. 29, 2026
 * @version Sep. 30, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyPhase71PrivateVideoDriverSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "The PHASE-71 private video driver" should {
    "record confined immutable snapshots without changing private sources" in {
      _with_fixture("snapshots") { fixture =>
        Given("a task-private project with source files, generated products, and excluded repository metadata")
        val source = fixture.storyboard
        val sourcebytes = Files.readAllBytes(source)
        val sourcetime = Files.getLastModifiedTime(source, LinkOption.NOFOLLOW_LINKS)

        When("the observer records before and after snapshots")
        val before = _run(fixture, "snapshot", "baseline", "before")
        val after = _run(fixture, "snapshot", "baseline", "after")

        Then("both snapshots succeed, stay below the private evidence root, and leave authored sources unchanged")
        before.exitcode shouldBe 0
        after.exitcode shouldBe 0
        Files.readAllBytes(source) shouldBe sourcebytes
        Files.getLastModifiedTime(source, LinkOption.NOFOLLOW_LINKS) shouldBe sourcetime
        _read(fixture.before) should include("path\tsha256\tbytes\tfiletime")
        _read(fixture.before) should include("src/main/media/development-process/ai-development-harness/video/ja/video.yaml")
        _read(fixture.before) should not include ".git/ignored.txt"
        _read(fixture.before) should not include ".codex-workflow/ignored.txt"
        _read(fixture.before) should not include("target/phase71-private-driver/cases")
      }
    }

    "admit a successful baseline terminal result with exact command, output, and handoff evidence" in {
      _with_fixture("baseline") { fixture =>
        Given("a prior final product and a terminal Cozy result for the frozen final build command")
        _run(fixture, "snapshot", "baseline", "before").exitcode shouldBe 0
        _write(fixture.output, "new-final-mp4-sentinel\n")
        _write_native_result(fixture, 0, "cache: miss\n")
        _run(fixture, "snapshot", "baseline", "after").exitcode shouldBe 0

        When("the observer verifies the synthetic native result and products")
        val result = _run(fixture, "verify", "baseline", fixture.nativeresult.toString)

        Then("the baseline is accepted only after validating the schema, final manifest, nine-scene handoff, and native ffprobe record")
        result.exitcode shouldBe 0
        result.output should include("baseline: verified")
      }
    }

    "preserve historical baseline snapshots while recording cache-isolation corrective evidence" in {
      _with_fixture("cache-isolation") { fixture =>
        Given("a historical baseline with a successful final product and an authentic-shaped terminal result")
        val historicalbeforeresult = _run(fixture, "snapshot", "baseline", "before")
        _write(fixture.output, "historical-after-mp4-sentinel\n")
        _write_native_result(fixture, 0, "cache: miss\n")
        val historicalafterresult = _run(fixture, "snapshot", "baseline", "after")
        val historicalbefore = _read(fixture.before)
        val historicalafterpath = fixture.root.resolve("target/phase71-private-driver/cases/baseline/after.tsv")
        val historicalafter = _read(historicalafterpath)
        val historicalbeforefiletime = Files.getLastModifiedTime(fixture.before, LinkOption.NOFOLLOW_LINKS)
        val historicalafterfiletime = Files.getLastModifiedTime(historicalafterpath, LinkOption.NOFOLLOW_LINKS)
        val outputrelative = "src/main/media/development-process/ai-development-harness/video/ja/build/ai-development-harness-ja.mp4"
        val correctivebeforepath = fixture.root.resolve("target/phase71-private-driver/cases/baseline/cache-isolation-003/before.tsv")
        val correctiveafterpath = fixture.root.resolve("target/phase71-private-driver/cases/baseline/cache-isolation-003/after.tsv")

        When("the observer verifies baseline and records a distinct corrective before-and-after pair")
        val baselineverification = _run(fixture, "verify", "baseline", fixture.nativeresult.toString)
        _write(fixture.output, "corrective-before-mp4-sentinel\n")
        val correctivebeforetime = Files.getLastModifiedTime(fixture.output, LinkOption.NOFOLLOW_LINKS).toMillis + 5000L
        Files.setLastModifiedTime(fixture.output, FileTime.fromMillis(correctivebeforetime))
        val correctivebeforeresult = _run(fixture, "snapshot", "baseline", "before", "cache-isolation-003")
        _write(fixture.output, "corrective-after-mp4-sentinel\n")
        Files.setLastModifiedTime(fixture.output, FileTime.fromMillis(correctivebeforetime + 5000L))
        _write_native_result(fixture, 0, "cache: miss\n")
        val correctiveafterresult = _run(fixture, "snapshot", "baseline", "after", "cache-isolation-003")
        val correctiveverification = _run(
          fixture,
          "verify",
          "baseline",
          fixture.nativeresult.toString,
          "cache-isolation-003"
        )
        val overwrite = _run(fixture, "snapshot", "baseline", "before", "cache-isolation-003")

        Then("baseline remains verifiable, corrective evidence is distinct, and historical snapshots cannot be overwritten")
        historicalbeforeresult.exitcode shouldBe 0
        historicalafterresult.exitcode shouldBe 0
        baselineverification.exitcode shouldBe 0
        correctivebeforeresult.exitcode shouldBe 0
        correctiveafterresult.exitcode shouldBe 0
        correctiveverification.exitcode shouldBe 0
        overwrite.exitcode should not be 0
        _read(fixture.before) shouldBe historicalbefore
        _read(historicalafterpath) shouldBe historicalafter
        Files.getLastModifiedTime(fixture.before, LinkOption.NOFOLLOW_LINKS) shouldBe historicalbeforefiletime
        Files.getLastModifiedTime(historicalafterpath, LinkOption.NOFOLLOW_LINKS) shouldBe historicalafterfiletime
        val correctivebeforeline = _read(correctivebeforepath).split("\n").find(_.startsWith(outputrelative + "\t")).get
        val correctiveafterline = _read(correctiveafterpath).split("\n").find(_.startsWith(outputrelative + "\t")).get
        correctivebeforeline should not be correctiveafterline
      }
    }

    "reject invalid corrective-attempt tags and symlink paths before writing" in {
      _with_fixture("cache-isolation-rejections") { fixture =>
        Given("a clean observer evidence root and a symlink occupying the admitted corrective directory")
        val attemptdirectory = fixture.root.resolve("target/phase71-private-driver/cases/baseline/cache-isolation-003")
        val symlinktarget = fixture.root.resolve("corrective-symlink-target")
        Files.createDirectories(attemptdirectory.getParent)
        Files.createDirectories(symlinktarget)
        Files.createSymbolicLink(attemptdirectory, symlinktarget)

        When("the observer receives unknown, foreign, traversal, and symlink corrective-attempt paths")
        val unknown = _run(fixture, "snapshot", "baseline", "before", "cache-isolation-004")
        val foreign = _run(fixture, "snapshot", "current", "before", "cache-isolation-003")
        val traversal = _run(fixture, "snapshot", "baseline", "before", "../cache-isolation-003")
        val symlink = _run(fixture, "snapshot", "baseline", "before", "cache-isolation-003")

        Then("every invalid attempt is rejected before any historical or corrective snapshot is written")
        unknown.exitcode should not be 0
        foreign.exitcode should not be 0
        traversal.exitcode should not be 0
        symlink.exitcode should not be 0
        Files.exists(fixture.before, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(fixture.root.resolve("target/phase71-private-driver/cases/baseline/after.tsv"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(symlinktarget.resolve("before.tsv"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "reject symlinks inside private inputs without leaving evidence and allow a corrected retry" in {
      Vector("", "cache-isolation-003").foreach { attempt =>
        _with_fixture("private-input-symlink-" + (if (attempt.isEmpty) "default" else attempt)) { fixture =>
          Given("a private project containing a symlink and an absent snapshot directory")
          val inputlink = fixture.privateroot.resolve("linked-storyboard.md")
          val sourcebytes = Files.readAllBytes(fixture.storyboard)
          val sourcetime = Files.getLastModifiedTime(fixture.storyboard, LinkOption.NOFOLLOW_LINKS)
          val snapshotpath = if (attempt.isEmpty) fixture.before
            else fixture.before.getParent.resolve(attempt).resolve("before.tsv")
          Files.createSymbolicLink(inputlink, fixture.storyboard)

          When("the observer attempts to record the invalid private input")
          val rejected = _run(fixture, "snapshot", "baseline", "before", attempt)

          Then("it rejects the input before creating a snapshot directory or partial evidence")
          rejected.exitcode should not be 0
          rejected.output should include("private project contains a symlink")
          Files.exists(snapshotpath, LinkOption.NOFOLLOW_LINKS) shouldBe false
          Files.exists(snapshotpath.getParent, LinkOption.NOFOLLOW_LINKS) shouldBe false
          Files.readAllBytes(fixture.storyboard) shouldBe sourcebytes
          Files.getLastModifiedTime(fixture.storyboard, LinkOption.NOFOLLOW_LINKS) shouldBe sourcetime

          When("the invalid fixture symlink is removed and the same snapshot is retried")
          Files.delete(inputlink)
          val retried = _run(fixture, "snapshot", "baseline", "before", attempt)

          Then("the retry succeeds without deleting or overwriting any evidence")
          retried.exitcode shouldBe 0
          _read(snapshotpath) should include("path\tsha256\tbytes\tfiletime")
          _read(snapshotpath) should include("storyboard.md")
          Files.readAllBytes(fixture.storyboard) shouldBe sourcebytes
          Files.getLastModifiedTime(fixture.storyboard, LinkOption.NOFOLLOW_LINKS) shouldBe sourcetime
        }
      }
    }

    "reject an all-current result after an input or artifact identity changes" in {
      Vector("input", "artifact").foreach { variant =>
        _with_fixture("current-" + variant) { fixture =>
          Given("identical before and after snapshots for a current private build")
          _run(fixture, "snapshot", "current", "before").exitcode shouldBe 0
          if (variant == "input")
            _write(fixture.storyboard, "changed authored Storyboard\n")
          else
            _write(fixture.output, "changed artifact bytes\n")
          _write_native_result(fixture, 0, "cache: hit\n")
          _run(fixture, "snapshot", "current", "after").exitcode shouldBe 0

          When("the observer checks the cache-hit claim against the complete inventory")
          val result = _run(fixture, "verify", "current", fixture.nativeresult.toString)

          Then("the changed input or artifact is rejected")
          result.exitcode should not be 0
        }
      }
    }

    "accept an all-current repeat with retained products and native ffprobe evidence" in {
      _with_fixture("current-success") { fixture =>
        Given("a prior successful product set and unchanged before and after inventories")
        _run(fixture, "snapshot", "current", "before").exitcode shouldBe 0
        _write_native_result(fixture, 0, "cache: hit\n")
        _run(fixture, "snapshot", "current", "after").exitcode shouldBe 0

        When("the observer verifies the native cache-hit result")
        val result = _run(fixture, "verify", "current", fixture.nativeresult.toString)

        Then("the current repeat is accepted only with unchanged products and the validated native ffprobe record")
        result.exitcode shouldBe 0
      }
    }

    "accept a regeneration with identical output bytes and a later FileTime" in {
      _with_fixture("regen") { fixture =>
        Given("a final output whose bytes remain equal while its modification time advances")
        _run(fixture, "snapshot", "storyboard", "before").exitcode shouldBe 0
        val oldtime = Files.getLastModifiedTime(fixture.output, LinkOption.NOFOLLOW_LINKS)
        Files.setLastModifiedTime(fixture.output, FileTime.fromMillis(oldtime.toMillis + 5000L))
        _write_native_result(fixture, 0, "cache: miss\n")
        _run(fixture, "snapshot", "storyboard", "after").exitcode shouldBe 0

        When("the observer verifies the regeneration evidence")
        val result = _run(fixture, "verify", "storyboard", fixture.nativeresult.toString)

        Then("the changed FileTime is sufficient even though the final bytes are identical")
        result.exitcode shouldBe 0
      }
    }

    "preserve successful products when a missing prerequisite fails and reject changed products" in {
      Vector(false, true).foreach { changeproduct =>
        _with_fixture("missing-" + changeproduct) { fixture =>
          Given("a prior successful final and part product set before a declared Storyboard prerequisite disappears")
          _run(fixture, "snapshot", "missing", "before").exitcode shouldBe 0
          Files.delete(fixture.storyboard)
          if (changeproduct)
            _write(fixture.output, "changed-after-failure\n")
          _write_native_result(fixture, 1, "native failure\n")
          _run(fixture, "snapshot", "missing", "after").exitcode shouldBe 0

          When("the observer verifies the nonzero terminal failure")
          val result = _run(fixture, "verify", "missing", fixture.nativeresult.toString)

          Then("only the missing source is excluded, while retained successful products must remain byte and FileTime identical")
          if (changeproduct)
            result.exitcode should not be 0
          else
            result.exitcode shouldBe 0
        }
      }
    }

    "reject malformed case, command, handoff scene, and ffprobe evidence" in {
      val variants = Vector("case", "command", "scene", "nonpositive", "nonfinite")
      variants.foreach { variant =>
        _with_fixture("reject-" + variant) { fixture =>
          Given("a synthetic verifier fixture with one malformed frozen acceptance condition")
          _run(fixture, "snapshot", "baseline", "before").exitcode shouldBe 0
          _write(fixture.output, "new-final-mp4-sentinel\n")
          variant match {
            case "command" => _write_native_result(fixture, 0, "cache: miss\n", command = "wrong")
            case _ => _write_native_result(fixture, 0, "cache: miss\n")
          }
          if (variant == "scene")
            _write(fixture.handoff, _handoff_text.replace("speed", "wrong-scene"))
          if (variant == "nonpositive")
            _write(fixture.manifest, _manifest_text("0"))
          if (variant == "nonfinite")
            _write(fixture.manifest, _manifest_text("NaN"))
          _run(fixture, "snapshot", "baseline", "after").exitcode shouldBe 0

          When("the verifier receives the malformed native evidence")
          val result = if (variant == "case")
            _run(fixture, "verify", "wrong-case", fixture.nativeresult.toString)
          else
            _run(fixture, "verify", "baseline", fixture.nativeresult.toString)

          Then("the malformed acceptance condition is rejected without treating a sentinel as real video evidence")
          result.exitcode should not be 0
        }
      }
    }

    "accept exactly the six frozen case names and reject arbitrary names by property" in {
      Given("the frozen six-case vocabulary and generated valid and invalid case names")
      val validproperty = Prop.forAll(Gen.oneOf(_valid_cases)) { casename =>
        var accepted = false
        _with_fixture("property-valid-" + casename) { fixture =>
          val result = _run(fixture, "snapshot", casename, "before")
          accepted = result.exitcode == 0
        }
        accepted
      }
      val invalidproperty = Prop.forAll(Gen.oneOf("", "baseline-extra", "BASELINE", "unknown", "../baseline")) { casename =>
        var rejected = false
        _with_fixture("property-invalid") { fixture =>
          val result = _run(fixture, "snapshot", casename, "before")
          rejected = result.exitcode != 0
        }
        rejected
      }

      When("ScalaCheck evaluates valid and invalid case names")
      val validresult = Test.check(Test.Parameters.default.withMinSuccessfulTests(12), validproperty)
      val invalidresult = Test.check(Test.Parameters.default.withMinSuccessfulTests(12), invalidproperty)

      Then("all generated valid names are admitted and all generated invalid names are rejected")
      validresult.passed shouldBe true
      invalidresult.passed shouldBe true
    }
  }

  private val _script_path = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("scripts/test/phase71-private-video-driver.sh")
  private val _valid_cases = Vector("baseline", "core", "intermediate", "storyboard", "current", "missing")

  private final case class Fixture(
    root: Path,
    privateroot: Path,
    storyboard: Path,
    output: Path,
    manifest: Path,
    handoff: Path,
    nativeresult: Path,
    before: Path,
    bin: Path
  )

  private final case class ScriptResult(exitcode: Int, output: String)

  private def _run(fixture: Fixture, args: String*): ScriptResult = {
    val command = Vector("sh", _script_path.toString, args.head, fixture.root.toString) ++ args.drop(1)
    val process = new ProcessBuilder(command.toArray: _*)
    val environment = process.environment()
    environment.put("PATH", fixture.bin.toString + ":" + environment.get("PATH"))
    process.redirectErrorStream(true)
    val started = process.start()
    val output = new String(started.getInputStream.readAllBytes(), StandardCharsets.UTF_8)
    ScriptResult(started.waitFor(), output)
  }

  private def _with_fixture[A](label: String)(body: Fixture => A): A = {
    val root = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/phase71-private-video-driver-spec").resolve(label)
    _delete(root)
    Files.createDirectories(root)
    val fixture = _write_fixture(root)
    try body(fixture)
    finally _delete(root)
  }

  private def _write_fixture(root: Path): Fixture = {
    val privateroot = root.resolve("target/phase71-private-driver/simplemodeling-org")
    val videoroot = privateroot.resolve("src/main/media/development-process/ai-development-harness/video/ja")
    val storyboard = videoroot.resolve("storyboard.md")
    val output = videoroot.resolve("build/ai-development-harness-ja.mp4")
    val manifest = videoroot.resolve("target/cozy-video/final/manifest.json")
    val handoff = videoroot.resolve("target/cozy-video/storyboard/dialogue/handoff.json")
    val nativeresult = root.resolve("native-result.json")
    val stdout = root.resolve("native-stdout.log")
    val before = root.resolve("target/phase71-private-driver/cases/baseline/before.tsv")
    val bin = root.resolve("bin")
    _write(storyboard, "# synthetic Storyboard\n")
    _write(videoroot.resolve("video.yaml"), "schema: synthetic\n")
    _write(videoroot.resolve("build/parts/dialogue.mp4"), "part-mp4-sentinel\n")
    _write(output, "prior-final-mp4-sentinel\n")
    _write(manifest, _manifest_text("3.5"))
    _write(handoff, _handoff_text)
    _write(videoroot.resolve("target/cozy-video/final/part-artifacts/dialogue.json"), "{\"status\":\"validated\"}\n")
    _write(privateroot.resolve(".git/ignored.txt"), "ignored\n")
    _write(privateroot.resolve(".codex-workflow/ignored.txt"), "ignored\n")
    _write_native_result(Fixture(root, privateroot, storyboard, output, manifest, handoff, nativeresult, before, bin), 0, "cache: miss\n")
    Fixture(root, privateroot, storyboard, output, manifest, handoff, nativeresult, before, bin)
  }

  private def _write_native_result(fixture: Fixture, exitcode: Int, stdout: String, command: String = "final"): Unit = {
    val stdoutfile = fixture.root.resolve("native-stdout.log")
    _write(stdoutfile, stdout)
    val videofile = fixture.privateroot.resolve("src/main/media/development-process/ai-development-harness/video/ja/video.yaml").toString
    val argv = if (command == "final")
      s"""["/synthetic/bin/cozy","video","build","$videofile","--mode","final","--check-tools"]"""
    else
      s"""["/synthetic/bin/cozy","video","build","$videofile.wrong","--mode","final","--check-tools"]"""
    _write(fixture.nativeresult,
      s"""{"schema":"cozy.exact-cli-command-result.v4","command_completion":"terminal","timed_out":false,"cli_kind":"cozy","argv":$argv,"exit_code":$exitcode,"stdout_file":"${stdoutfile.toString}"}""")
  }

  private def _manifest_text(duration: String): String =
    s"""{"schema":"cozy.video.final.v2","status":"validated","mode":"final","output":{"path":"build/ai-development-harness-ja.mp4"},"ffprobe":{"format":{"duration":"$duration"},"streams":[{"codec_type":"video"}]}}"""

  private val _handoff_text =
    """{"schema":"cozy.video.storyboard-build-handoff.v2","status":"validated","partId":"dialogue","sourcePath":"storyboard.md","storyboardSection":"explanation","storyboard":{"schema":"cozy.video.storyboard.v1","version":1,"scenes":[{"id":"opening","order":1},{"id":"speed","order":2},{"id":"business-wall","order":3},{"id":"convergence","order":4},{"id":"harness-definition","order":5},{"id":"broad-concept","order":6},{"id":"responsibility-boundary","order":7},{"id":"technology-context","order":8},{"id":"series-roadmap","order":9}]}}"""

  private def _write(path: Path, value: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, value.getBytes(StandardCharsets.UTF_8))
  }

  private def _read(path: Path): String =
    new String(Files.readAllBytes(path), StandardCharsets.UTF_8)

  private def _delete(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
      finally stream.close()
    }
}
