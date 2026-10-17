package cozy.media

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import scala.collection.JavaConverters._
import scala.sys.process.{Process, ProcessLogger}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 */
final class CozyMediaWarehouseUploadSpec
    extends AnyWordSpec with Matchers with GivenWhenThen {
  import CozyMediaWarehouseUploadSpec._

  "Selected canonical warehouse upload" should {
    "publish ordered JA and EN roles with exact keys and MIME while retaining unrelated files" in {
      Given("the actual upload programs and selected JA/EN infographics, article PDFs and slide PDFs")
      _with_fixture { fixture =>
        val selections = for {
          locale <- Vector("ja", "en")
          (filename, mime) <- formats
        } yield SelectedFile(s"media/$locale/development-process/example/$filename", mime)
        selections.foreach(selection => _write(fixture.root.resolve(selection.coordinate), "selected media"))
        val unrelated = fixture.root.resolve("media/ja/development-process/other/article.pdf")
        val car = fixture.root.resolve("repository/maven/example/example.car")
        _write(unrelated, "other article")
        _write(car, "CAR retained")

        When("the selected files are uploaded in their explicit argument order")
        val result = _run(fixture, selections.map(_.coordinate), Map("CLOUDFRONT_INVALIDATE" -> "false"))

        Then("only the exact selected bucket objects receive their fixed content types")
        result.exit shouldBe 0
        result.calls shouldBe selections.map(selection => _cp_call(fixture, selection))
        _read(unrelated) shouldBe "other article"
        _read(car) shouldBe "CAR retained"
        result.output should include("Local availability confirmed")
        result.output should include("Selected uploads completed")
        result.output should include("public retrieval has not yet been verified")
      }
    }

    "simulate only selected copies with AWS dryrun and no invalidation" in {
      Given("two available canonical files and the current warehouse programs")
      _with_fixture { fixture =>
        val selections = Vector(
          SelectedFile("media/ja/development-process/example/infographic.png", "image/png"),
          SelectedFile("media/en/development-process/example/slides.pdf", "application/pdf")
        )
        selections.foreach(selection => _write(fixture.root.resolve(selection.coordinate), "available"))

        When("literal --dry-run precedes the selected coordinates")
        val result = _run(fixture, Vector("--dry-run") ++ selections.map(_.coordinate))

        Then("AWS receives only the selected cp commands with --dryrun and no CloudFront call")
        result.exit shouldBe 0
        result.calls shouldBe selections.map(selection => _cp_call(fixture, selection) :+ "--dryrun")
        result.output should include("Dry-run simulation complete")
        result.output should include("no upload completion or public retrieval is established")
      }
    }

    "reject every malformed or unavailable complete-batch selection before any AWS action" in {
      val valid = "media/ja/development-process/example/article.pdf"
      val invalidselections = Vector(
        "missing file" -> Vector("media/en/development-process/example/slides.pdf"),
        "traversal" -> Vector("media/ja/development-process/../article.pdf"),
        "absolute path" -> Vector("/media/ja/development-process/example/article.pdf"),
        "empty segment" -> Vector("media/ja//example/article.pdf"),
        "wildcard" -> Vector("media/ja/development-process/*/article.pdf"),
        "unknown locale" -> Vector("media/fr/development-process/example/article.pdf"),
        "duplicate" -> Vector(valid),
        "video" -> Vector("media/ja/development-process/example/video.mp4"),
        "CAR path" -> Vector("repository/maven/example/example.car"),
        "extra segment" -> Vector("media/ja/development-process/example/extra/article.pdf"),
        "whitespace" -> Vector("media/ja/development-process/example name/article.pdf"),
        "dot category" -> Vector("media/ja/./example/article.pdf"),
        "destination argument" -> Vector("s3://another-bucket/media/article.pdf"),
        "symlink file" -> Vector("media/ja/development-process/example/slides.pdf"),
        "directory source" -> Vector("media/ja/development-process/example/slides.pdf")
      ) ++ Vector("media", "media/ja", "media/ja/development-process", "media/ja/development-process/example")
        .map(directory => s"symlink directory $directory" -> Vector(valid))
      invalidselections.foreach { case (label, arguments) =>
        Given(s"a valid first file followed by a $label selection in an isolated business fixture")
        _with_fixture { fixture =>
          _write(fixture.root.resolve(valid), "valid first file")
          if (label == "symlink file")
            Files.createSymbolicLink(fixture.root.resolve(arguments.head), fixture.root.resolve(valid))
          if (label == "directory source")
            Files.createDirectory(fixture.root.resolve(arguments.head))
          if (label.startsWith("symlink directory ")) {
            val directory = fixture.root.resolve(label.stripPrefix("symlink directory "))
            val redirect = fixture.root.resolve("redirect")
            Files.move(directory, redirect)
            Files.createSymbolicLink(directory, redirect)
          }

          When(s"the complete selection containing $label is submitted")
          val result = _run(fixture, Vector(valid) ++ arguments)

          Then(s"$label rejects the whole batch without uploading even the first valid file")
          withClue(label) {
            result.exit should not be 0
            result.calls shouldBe Vector.empty
            result.output should include("preflight failed")
          }
        }
      }
    }

    "invalidate exact successful paths and honor suppression and distribution overrides" in {
      Given("available selected PDFs and explicit CloudFront controls including a broad legacy path override")
      _with_fixture { fixture =>
        val selections = Vector(
          SelectedFile("media/en/development-process/example/article.pdf", "application/pdf"),
          SelectedFile("media/ja/development-process/example/slides.pdf", "application/pdf")
        )
        selections.foreach(selection => _write(fixture.root.resolve(selection.coordinate), "available"))
        val controls = Map("CLOUDFRONT_DISTRIBUTION_ID" -> "selected-distribution", "CLOUDFRONT_INVALIDATION_PATHS" -> "/media/*")

        When("all real uploads succeed with invalidation enabled")
        val result = _run(fixture, selections.map(_.coordinate), controls)

        Then("one invalidation follows all copies and contains only the selected paths in order")
        result.exit shouldBe 0
        result.calls shouldBe selections.map(selection => _cp_call(fixture, selection)) :+
          (Vector("cloudfront", "create-invalidation", "--distribution-id", "selected-distribution", "--paths") ++
            selections.map(selection => "/" + selection.coordinate))

        Given("the same selected files with explicit invalidation suppression")
        Files.delete(fixture.log)
        When("the same uploads run with CLOUDFRONT_INVALIDATE=false")
        val suppressed = _run(fixture, selections.map(_.coordinate), controls + ("CLOUDFRONT_INVALIDATE" -> "false"))
        Then("copies still complete and no invalidation is attempted")
        suppressed.exit shouldBe 0
        suppressed.calls shouldBe selections.map(selection => _cp_call(fixture, selection))

        Given("the same selected files with an injected CloudFront failure")
        Files.delete(fixture.log)
        When("copies succeed but the exact invalidation fails")
        val failed = _run(fixture, selections.map(_.coordinate), controls + ("AWS_FAIL_CLOUDFRONT" -> "true"))
        Then("the failure propagates after the completed uploads with no invalidation retry")
        failed.exit shouldBe 23
        failed.calls shouldBe result.calls
        failed.output should include("invalidation failed after upload completion")
      }
    }

    "stop after an AWS copy failure without retry or invalidation while retaining prior calls" in {
      Given("three selected files with the AWS stub failing the second exact bucket object")
      _with_fixture { fixture =>
        val selections = Vector("article.pdf", "slides.pdf", "infographic.png").map { filename =>
          SelectedFile(s"media/ja/development-process/example/$filename", if (filename.endsWith("pdf")) "application/pdf" else "image/png")
        }
        selections.foreach(selection => _write(fixture.root.resolve(selection.coordinate), "available"))

        When("the second cp returns a nonzero status")
        val result = _run(fixture, selections.map(_.coordinate), Map("AWS_FAIL_KEY" -> s"s3://simplemodeling.org/${selections(1).coordinate}"))

        Then("the first success and failed second attempt remain recorded with no later call")
        result.exit shouldBe 19
        result.calls shouldBe selections.take(2).map(selection => _cp_call(fixture, selection))
        result.output should include("prior uploads may remain visible")
        result.output should include("No retry or invalidation performed")
      }
    }

    "resolve selected sources relative to the script when called from another directory" in {
      Given("an available canonical PDF beside the script and a different caller directory")
      _with_fixture { fixture =>
        val selection = SelectedFile("media/en/development-process/example/article.pdf", "application/pdf")
        _write(fixture.root.resolve(selection.coordinate), "script-relative PDF")
        val caller = Files.createDirectory(fixture.root.resolve("caller"))

        When("the script is invoked by absolute path from that caller directory")
        val result = _run(fixture, Vector(selection.coordinate), Map("CLOUDFRONT_INVALIDATE" -> "false"), Some(caller))

        Then("the copy uses the canonical file beside the script rather than a caller-relative source")
        result.exit shouldBe 0
        result.calls shouldBe Vector(_cp_call(fixture, selection))
      }
    }

    "reject empty option-only and unknown-option commands without selecting the root" in {
      Vector(Vector.empty[String], Vector("--dry-run"), Vector("--unknown"), Vector("--dry-run", "--unknown"),
        Vector("media/ja/development-process/example/article.pdf", "--dry-run")).foreach { arguments =>
        Given(s"an available canonical file and an invalid command argument list: $arguments")
        _with_fixture { fixture =>
          _write(fixture.root.resolve("media/ja/development-process/example/article.pdf"), "available")

          When("the invalid command is invoked")
          val result = _run(fixture, arguments)

          Then("it fails without any AWS call or implicit rootwide selection")
          result.exit should not be 0
          result.calls shouldBe Vector.empty
        }
      }
    }
  }

  private def _with_fixture(body: Fixture => Unit): Unit = {
    if (!sys.props.get("cozy.media.warehouse.integration").contains("true"))
      cancel("Warehouse upload acceptance requires -Dcozy.media.warehouse.integration=true; cancellation is not acceptance.")
    val declared = sys.props.getOrElse("cozy.media.warehouse.uploadRoot", fail("Enabled upload acceptance requires explicit cozy.media.warehouse.uploadRoot"))
    val inputroot = Paths.get(declared)
    if (declared.trim.isEmpty || declared != declared.trim || !inputroot.isAbsolute ||
        !Files.isDirectory(inputroot, LinkOption.NOFOLLOW_LINKS))
      fail("cozy.media.warehouse.uploadRoot must be an exact absolute existing direct directory")
    val workroot = Paths.get("target/cozy-media-warehouse-upload/work").toAbsolutePath.normalize()
    Files.createDirectories(workroot)
    val root = Files.createTempDirectory(workroot, "selected-")
    val fixture = Fixture(root, Files.createDirectory(root.resolve("bin")), root.resolve("aws-calls.tsv"))
    try {
      Vector("upload-media.sh", "invalidate-cloudfront.sh").foreach { filename =>
        val source = inputroot.resolve(filename)
        if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(source))
          fail(s"Enabled upload acceptance requires readable direct product input: $source")
        Files.copy(source, root.resolve(filename))
        root.resolve(filename).toFile.setExecutable(true) shouldBe true
      }
      _write(fixture.bin.resolve("aws"), awsStub)
      fixture.bin.resolve("aws").toFile.setExecutable(true) shouldBe true
      body(fixture)
    } finally {
      val paths = Files.walk(root)
      try paths.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(path => Files.delete(path))
      finally paths.close()
    }
  }

  private def _run(fixture: Fixture, arguments: Vector[String], controls: Map[String, String] = Map.empty,
      caller: Option[Path] = None): RunResult = {
    val output = new StringBuilder
    val environment = Map(
      "PATH" -> (fixture.bin.toString + ":" + sys.env.getOrElse("PATH", "/usr/bin:/bin")),
      "AWS_CALL_LOG" -> fixture.log.toString,
      "AWS_FAIL_KEY" -> "",
      "AWS_FAIL_CLOUDFRONT" -> "false",
      "CLOUDFRONT_INVALIDATE" -> "true",
      "CLOUDFRONT_DISTRIBUTION_ID" -> "fixture-distribution",
      "CLOUDFRONT_INVALIDATION_PATHS" -> ""
    ) ++ controls
    val exit = Process(Vector("/bin/sh", fixture.root.resolve("upload-media.sh").toString) ++ arguments,
      caller.getOrElse(fixture.root).toFile, environment.toSeq: _*)
      .!(ProcessLogger(line => output.append(line).append('\n'), line => output.append(line).append('\n')))
    val calls = if (Files.exists(fixture.log))
      Files.readAllLines(fixture.log, StandardCharsets.UTF_8).asScala.toVector.map(_.split("\t", -1).toVector)
    else Vector.empty
    RunResult(exit, calls, output.toString)
  }

  private def _cp_call(fixture: Fixture, selection: SelectedFile): Vector[String] =
    Vector("s3", "cp", fixture.root.resolve(selection.coordinate).toString,
      "s3://simplemodeling.org/" + selection.coordinate, "--content-type", selection.mime)

  private def _write(path: Path, content: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    ()
  }

  private def _read(path: Path): String = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
}

object CozyMediaWarehouseUploadSpec {
  final case class Fixture(root: Path, bin: Path, log: Path)
  final case class SelectedFile(coordinate: String, mime: String)
  final case class RunResult(exit: Int, calls: Vector[Vector[String]], output: String)

  val formats: Vector[(String, String)] = Vector(
    "infographic.png" -> "image/png", "infographic.jpg" -> "image/jpeg",
    "infographic.jpeg" -> "image/jpeg", "infographic.svg" -> "image/svg+xml",
    "infographic.webp" -> "image/webp", "article.pdf" -> "application/pdf", "slides.pdf" -> "application/pdf"
  )
  val awsStub: String = """#!/bin/sh
set -eu
separator=''
for argument do
  printf '%s%s' "$separator" "$argument" >> "$AWS_CALL_LOG"
  separator=$(printf '\t')
done
printf '\n' >> "$AWS_CALL_LOG"
if [ "${1:-}" = s3 ] && [ "${2:-}" = cp ] && [ "${4:-}" = "${AWS_FAIL_KEY:-}" ]; then
  exit 19
fi
if [ "${1:-}" = cloudfront ] && [ "${AWS_FAIL_CLOUDFRONT:-false}" = true ]; then
  exit 23
fi
exit 0
"""
}
