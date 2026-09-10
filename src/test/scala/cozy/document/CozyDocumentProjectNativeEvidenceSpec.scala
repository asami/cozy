package cozy.document

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 10, 2026
 * @version Sep. 10, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentProjectNativeEvidenceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Document Project native evidence" should {
    "native accepted-evidence behavior" which {
    "close native accepted evidence while preserving dry-run and unavailable-provider contracts" in {
      _with_temp_dir("cozy-document-project-run") { root =>
        Given("an admitted standard Document Project with Article review selected")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")
        _activate_optional_work_products(project, "standard", Vector("article-review-html"))
        val authored = project.resolve("index.dox")
        val authoredbytes = Files.readAllBytes(authored)

        When("dry-run resolves the admitted native provider without invocation")
        val dryrun = _execute(List("document-project", "run", project.toString, "--operation", "article.render-review", "--dry-run"))

        Then("dry-run reports the typed declaration without target, attempt, or currentness mutation")
        dryrun should include("operation: article.render-review")
        dryrun should include("provider-binding: cozy-review-projection")
        dryrun should include("profile: standard")
        dryrun should include("outcome: resolved")
        dryrun should include("identity: article-review-html")
        dryrun should include("path: target/document-project/article-review.html")
        dryrun should include("mediaType: text/html")
        Files.exists(project.resolve("evidence/attempts"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false

        When("the admitted native operation is run")
        val output = _execute(List("document-project", "run", project.toString, "--operation", "article.render-review"))

        Then("the validated result carries the accepted attempt, exact output identity, and derived currentness")
        output should include("outcome: accepted")
        output should include("identity: article-review-html")
        output should include("path: target/document-project/article-review.html")
        output should include("mediaType: text/html")
        output should include("sha256:")
        output should include("native review projection rendered")
        output should include("receipt:")
        output should include("cozy.document-project.native-receipt.v1")
        output should include("evidence: accepted")
        output should include("currentness: current")
        val nativehtml = Files.readString(project.resolve("target/document-project/article-review.html"), StandardCharsets.UTF_8)
        nativehtml should include("Native article.render-review execution")
        nativehtml should include("separate append-only v2 Operation Attempt")
        nativehtml should include("writes no standalone receipt file or state authority")
        nativehtml should include("not a renderer, publication, compatibility adapter, or successor-owned persistence")
        nativehtml should not include("No provider execution, renderer input")
        Files.readAllBytes(authored) shouldBe authoredbytes
        Files.exists(project.resolve("target/document-project/article-review.html"), LinkOption.NOFOLLOW_LINKS) shouldBe true
        Files.exists(project.resolve("target/document-project/article-review.receipt.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        val attempts = _relative_files(project.resolve("evidence/attempts"))
        attempts.size shouldBe 1
        val attempt = project.resolve("evidence/attempts").resolve(attempts.head)
        val attemptyaml = Files.readString(attempt, StandardCharsets.UTF_8)
        attemptyaml should include("schema: cozy.document-operation-attempt.v2")
        attemptyaml should include("outcome: accepted")
        attemptyaml should include("identity: article-review-html")
        attemptyaml should include(s"sha256: ${_sha256(project.resolve("target/document-project/article-review.html"))}")
        attemptyaml should include("receipt:\n  identity: cozy.document-project.native-receipt.v1")
        Files.exists(project.resolve("state"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("operation-receipt-evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("target/document-project/state.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false

        When("a known binding without a native provider is run")
        val blocked = _execute(List("document-project", "run", project.toString, "--operation", "article.render-pdf"))

        Then("it is explicitly blocked without a new output or Operation Attempt")
        blocked should include("outcome: blocked")
        blocked should include("operation: article.render-pdf")
        blocked should include("provider-binding: smartdox-rendering")
        blocked should include("missing-capability: native typed provider execution is unavailable")
        _relative_files(project.resolve("evidence/attempts")) shouldBe attempts
      }
    }

    "derive native accepted evidence stale and recovered currentness from exact identities" in {
      _with_temp_dir("cozy-document-project-native-accepted-evidence-currentness") { root =>
        Given("a selected Article review project with one accepted native evidence record")
        val project = _scaffolded_project(root, "native-evidence-currentness")
        _activate_optional_work_products(project, "standard", Vector("article-review-html"))
        val article = project.resolve("index.dox")
        val first = _execute(List("document-project", "run", project.toString, "--operation", "article.render-review"))
        val attempts = project.resolve("evidence/attempts")
        val firstattempts = _relative_files(attempts)
        val firstbytes = firstattempts.map(path => path -> Files.readAllBytes(attempts.resolve(path))).toMap

        When("the captured direct Article identity changes and inspect derives currentness")
        Files.writeString(article, Files.readString(article, StandardCharsets.UTF_8) + "\nChanged after native acceptance.\n", StandardCharsets.UTF_8)
        _execute(List("document-project", "inspect", project.toString))
        val stale = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)

        Then("the accepted evidence is stale instead of treating the retained output as current")
        first should include("currentness: current")
        stale should include("id: article-review-html\n    role: review-projection\n    disposition: optional\n    selection: active-optional\n    criterion: article-review-rendered\n    coverage: missing\n    currentness: stale")
        _relative_files(attempts) shouldBe firstattempts
        firstattempts.foreach(path => Files.readAllBytes(attempts.resolve(path)) shouldBe firstbytes(path))

        When("the public native operation runs again against the changed direct identity")
        val recovered = _execute(List("document-project", "run", project.toString, "--operation", "article.render-review"))
        _execute(List("document-project", "inspect", project.toString))
        val current = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)

        Then("a second append-only accepted attempt recovers currentness without replacing the prior evidence")
        recovered should include("outcome: accepted")
        recovered should include("currentness: current")
        current should include("id: article-review-html\n    role: review-projection\n    disposition: optional\n    selection: active-optional\n    criterion: article-review-rendered\n    coverage: satisfied\n    currentness: current")
        _relative_files(attempts).size shouldBe 2
        firstattempts.foreach(path => Files.readAllBytes(attempts.resolve(path)) shouldBe firstbytes(path))
      }
    }

    "treat ancestor symbolic evidence paths as stale" in {
      _with_temp_dir("cozy-document-project-native-ancestor-symbolic-stale") { root =>
        Given("two selected Article review projects with accepted native evidence")
        val inputproject = _scaffolded_project(root, "native-input-ancestor")
        val outputproject = _scaffolded_project(root, "native-output-ancestor")
        _activate_optional_work_products(inputproject, "standard", Vector("article-review-html"))
        _activate_optional_work_products(outputproject, "standard", Vector("article-review-html"))

        When("the native Article review operation accepts evidence for both projects")
        _execute(List("document-project", "run", inputproject.toString, "--operation", "article.render-review"))
        _execute(List("document-project", "run", outputproject.toString, "--operation", "article.render-review"))
        val inputattempts = inputproject.resolve("evidence/attempts")
        val outputattempts = outputproject.resolve("evidence/attempts")
        val inputattemptpaths = _relative_files(inputattempts)
        val outputattemptpaths = _relative_files(outputattempts)
        val inputattemptbytes = inputattemptpaths.map(path => path -> Files.readAllBytes(inputattempts.resolve(path))).toMap
        val outputattemptbytes = outputattemptpaths.map(path => path -> Files.readAllBytes(outputattempts.resolve(path))).toMap
        val inputdescriptor = CozyDocumentProject._load_project(inputproject)
        val outputdescriptor = CozyDocumentProject._load_project(outputproject)

        Then("both accepted evidence snapshots initially report the Article review as current")
        CozyDocumentProjectEvidence.snapshot(inputproject, inputdescriptor).products.find(_.value.workProduct.id == "article-review-html").map(_.currentness) shouldBe Some("current")
        CozyDocumentProjectEvidence.snapshot(outputproject, outputdescriptor).products.find(_.value.workProduct.id == "article-review-html").map(_.currentness) shouldBe Some("current")

        Given("the accepted evidence bytes and attempt history are retained")
        val inputparent = inputproject.resolve("content")
        val movedinputparent = root.resolve("moved-input-content")
        val inputbytes = Files.readAllBytes(inputparent.resolve("core-en.yaml"))
        val outputparent = outputproject.resolve("target/document-project")
        val movedoutputparent = root.resolve("moved-output-document-project")
        val outputbytes = Files.readAllBytes(outputparent.resolve("article-review.html"))

        When("the direct input parent and declared output parent are replaced by symlinks to identical moved bytes")
        Files.move(inputparent, movedinputparent)
        Files.createSymbolicLink(inputparent, movedinputparent)
        Files.move(outputparent, movedoutputparent)
        Files.createSymbolicLink(outputparent, movedoutputparent)

        Then("both Article review snapshots report stale currentness without changing retained bytes")
        CozyDocumentProjectEvidence.snapshot(inputproject, inputdescriptor).products.find(_.value.workProduct.id == "article-review-html").map(_.currentness) shouldBe Some("stale")
        CozyDocumentProjectEvidence.snapshot(outputproject, outputdescriptor).products.find(_.value.workProduct.id == "article-review-html").map(_.currentness) shouldBe Some("stale")
        Files.readAllBytes(inputproject.resolve("content/core-en.yaml")) shouldBe inputbytes
        Files.readAllBytes(outputproject.resolve("target/document-project/article-review.html")) shouldBe outputbytes
        inputattemptpaths.foreach(path => Files.readAllBytes(inputattempts.resolve(path)) shouldBe inputattemptbytes(path))
        outputattemptpaths.foreach(path => Files.readAllBytes(outputattempts.resolve(path)) shouldBe outputattemptbytes(path))
      }
    }

    "retain failed v2 history for malformed native execution and provider failure without accepted evidence" in {
      _with_temp_dir("cozy-document-project-native-accepted-evidence-failure") { root =>
        Given("an admitted selected Article review project and its captured direct invocation identities")
        val project = _scaffolded_project(root, "native-evidence-failure")
        _activate_optional_work_products(project, "standard", Vector("article-review-html"))
        val descriptor = CozyDocumentProject._load_project(project)
        val operation = CozyDocumentWorkflow.declaredOperation("article.render-review") match {
          case Right(Some(value)) => value
          case _ => throw new RuntimeException("article.render-review must be declared")
        }
        val inputs = CozyDocumentProjectEvidence.captureNativeInputs(project, descriptor, operation)
        val output = project.resolve("target/document-project/article-review.html")
        Files.createDirectories(output.getParent)
        Files.writeString(output, "<html>forged output</html>\n", StandardCharsets.UTF_8)
        val forgedsha = "0" * 64
        val malformed = CozyDocumentProjectProvider.Executed(
          Vector(CozyDocumentProjectProvider.ProviderOutput("article-review-html", "target/document-project/article-review.html", "text/html", forgedsha)),
          Vector("forged executed result"),
          CozyDocumentProjectProvider.ProviderReceipt(
            CozyDocumentProjectProvider.NATIVE_RECEIPT_IDENTITY,
            CozyDocumentProjectProvider.nativeReceiptValue("article.render-review", "target/document-project/article-review.html", "text/html", forgedsha)
          )
        )

        When("a malformed executed result and then a provider failure close through the v2 evidence boundary")
        val malformedclosure = CozyDocumentProjectEvidence.closeNativeExecution(project, descriptor, operation, inputs, malformed)
        val attempts = project.resolve("evidence/attempts")
        val malformedattempt = _relative_files(attempts).head
        val malformedbytes = Files.readAllBytes(attempts.resolve(malformedattempt))
        val failedclosure = CozyDocumentProjectEvidence.closeNativeExecution(
          project,
          descriptor,
          operation,
          inputs,
          CozyDocumentProjectProvider.Failed("article.render-review", "cozy-review-projection", "Cozy review projection adapter", Vector("provider failed after admitted invocation"))
        )
        _execute(List("document-project", "inspect", project.toString))
        val state = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)

        Then("both closures retain failed history while no forged output becomes accepted or current")
        malformedclosure shouldBe a [CozyDocumentProjectEvidence.NativeFailedClosure]
        failedclosure shouldBe a [CozyDocumentProjectEvidence.NativeFailedClosure]
        Files.readAllBytes(attempts.resolve(malformedattempt)) shouldBe malformedbytes
        _relative_files(attempts).size shouldBe 2
        Files.readString(attempts.resolve(malformedattempt), StandardCharsets.UTF_8) should include("outcome: failed\ndiagnostics:\n  - \"native provider output sha256 does not match the declared output bytes\"\noutputs:\n  []\nreceipt: none")
        state should include("id: article-review-html\n    role: review-projection\n    disposition: optional\n    selection: active-optional\n    criterion: article-review-rendered\n    coverage: missing\n    currentness: failed")
      }
    }

    "admit Article review inputs and output destination before native provider invocation" in {
      _with_temp_dir("cozy-document-project-native-run-admission") { root =>
        Given("a selected Article review project whose authoritative article source is symbolic")
        val sourceparent = Files.createDirectory(root.resolve("source-parent"))
        _execute(List("document-project", "scaffold", "source", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", sourceparent.toString))
        val sourceproject = sourceparent.resolve("source.dox")
        _activate_optional_work_products(sourceproject, "standard", Vector("article-review-html"))
        val externalarticle = root.resolve("external-index.dox")
        Files.writeString(externalarticle, "external\n", StandardCharsets.UTF_8)
        Files.delete(sourceproject.resolve("index.dox"))
        Files.createSymbolicLink(sourceproject.resolve("index.dox"), externalarticle)

        When("the native operation is requested with the unsafe authoritative input")
        val sourcefailure = _failure(List("document-project", "run", sourceproject.toString, "--operation", "article.render-review"))

        Then("input admission rejects it before output or evidence mutation")
        _diagnostic_tokens(sourcefailure) shouldBe Vector("DP-PATH-001")
        Files.exists(sourceproject.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(sourceproject.resolve("evidence/attempts"), LinkOption.NOFOLLOW_LINKS) shouldBe false

        Given("a second selected Article review project whose declared output destination is symbolic")
        val outputparent = Files.createDirectory(root.resolve("output-parent"))
        _execute(List("document-project", "scaffold", "output", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", outputparent.toString))
        val outputproject = outputparent.resolve("output.dox")
        _activate_optional_work_products(outputproject, "standard", Vector("article-review-html"))
        val externaloutput = root.resolve("external-review.html")
        Files.writeString(externaloutput, "unchanged\n", StandardCharsets.UTF_8)
        Files.createDirectories(outputproject.resolve("target/document-project"))
        Files.createSymbolicLink(outputproject.resolve("target/document-project/article-review.html"), externaloutput)

        When("the native operation is requested with the unsafe declared output")
        val outputfailure = _failure(List("document-project", "run", outputproject.toString, "--operation", "article.render-review"))

        Then("output admission rejects it before provider invocation or an Operation Attempt")
        _diagnostic_tokens(outputfailure) shouldBe Vector("DP-PATH-001")
        Files.readString(externaloutput, StandardCharsets.UTF_8) shouldBe "unchanged\n"
        Files.exists(outputproject.resolve("evidence/attempts"), LinkOption.NOFOLLOW_LINKS) shouldBe false

        Given("a third selected Article review project whose infographic source is missing")
        val missingparent = Files.createDirectory(root.resolve("missing-parent"))
        _execute(List("document-project", "scaffold", "missing", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", missingparent.toString))
        val missingproject = missingparent.resolve("missing.dox")
        _activate_optional_work_products(missingproject, "standard", Vector("article-review-html"))
        Files.delete(missingproject.resolve("infographic/infographic.svg"))

        When("the native operation is requested with the missing infographic authority")
        val missingfailure = _failure(List("document-project", "run", missingproject.toString, "--operation", "article.render-review"))

        Then("infographic admission rejects it before output or an Operation Attempt")
        _diagnostic_tokens(missingfailure) shouldBe Vector("DP-PATH-001")
        missingfailure should include("must be a direct regular non-symlink file")
        Files.exists(missingproject.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(missingproject.resolve("evidence/attempts"), LinkOption.NOFOLLOW_LINKS) shouldBe false

        Given("a fourth selected Article review project whose infographic source is symbolic")
        val symbolicparent = Files.createDirectory(root.resolve("symbolic-parent"))
        _execute(List("document-project", "scaffold", "symbolic", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", symbolicparent.toString))
        val symbolicproject = symbolicparent.resolve("symbolic.dox")
        _activate_optional_work_products(symbolicproject, "standard", Vector("article-review-html"))
        val externalinfographic = root.resolve("external-infographic.svg")
        Files.writeString(externalinfographic, "<svg>external</svg>\n", StandardCharsets.UTF_8)
        Files.delete(symbolicproject.resolve("infographic/infographic.svg"))
        Files.createSymbolicLink(symbolicproject.resolve("infographic/infographic.svg"), externalinfographic)

        When("the native operation is requested with the symbolic infographic authority")
        val symbolicfailure = _failure(List("document-project", "run", symbolicproject.toString, "--operation", "article.render-review"))

        Then("symbolic infographic admission rejects it before output or an Operation Attempt")
        _diagnostic_tokens(symbolicfailure) shouldBe Vector("DP-PATH-001")
        symbolicfailure should include("must be a direct regular non-symlink file")
        Files.exists(symbolicproject.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(symbolicproject.resolve("evidence/attempts"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "reject empty native provider output declarations as failed results" in {
      _with_temp_dir("cozy-document-project-native-provider-result") { root =>
        Given("an admitted selected Article review project and an empty provider declaration")
        val project = _scaffolded_project(root, "empty-result")
        _activate_optional_work_products(project, "standard", Vector("article-review-html"))
        val descriptor = CozyDocumentProject._load_project(project)
        val operation = CozyDocumentWorkflow.declaredOperation("article.render-review") match {
          case Right(Some(value)) => value
          case _ => throw new RuntimeException("article.render-review must be declared")
        }
        val declaration = CozyDocumentWorkflow.NativeProviderDeclaration("article.render-review", "cozy-review-projection", Vector.empty)

        When("the provider receives no declared output")
        val result = CozyDocumentProjectProvider.execute(CozyDocumentProjectProvider.Request(project, descriptor, operation, declaration, Vector.empty))

        Then("it cannot represent execution success without outputs and a receipt")
        result shouldBe CozyDocumentProjectProvider.Failed(
          "article.render-review",
          "cozy-review-projection",
          "unresolved provider",
          Vector("native provider requires one admitted destination for every declared output")
        )
        Files.exists(project.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("evidence/attempts"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "reject unknown and profile-disabled operations without creating attempts" in {
      _with_temp_dir("cozy-document-project-run-admission") { root =>
        Given("admitted standard and standard-video Document Projects")
        val standardparent = Files.createDirectory(root.resolve("standard-parent"))
        val videoparent = Files.createDirectory(root.resolve("video-parent"))
        _execute(List("document-project", "scaffold", "standard", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", standardparent.toString))
        _execute(List("document-project", "scaffold", "video", "--profile", "standard-video", "--language", "en", "--workspace", "directory", "--save", videoparent.toString))
        val standard = standardparent.resolve("standard.dox")
        val video = videoparent.resolve("video.dox")

        When("unknown and disabled operations are requested")
        val unknown = _failure(List("document-project", "run", standard.toString, "--operation", "render"))
        val disabled = _failure(List("document-project", "run", standard.toString, "--operation", "video.render-review"))

        Then("both admissions reject with DP-OP-001 and create no evidence")
        unknown should include("DP-OP-001")
        unknown should include("undeclared logical operation: render")
        disabled should include("DP-OP-001")
        disabled should include("disabled for profile standard")
        Files.exists(standard.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(video.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }
    }
  }

  private def _scaffolded_project(root: Path, slug: String): Path = {
    val parent = Files.createDirectory(root.resolve(s"$slug-parent"))
    _execute(List("document-project", "scaffold", slug, "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
    parent.resolve(s"$slug.dox")
  }
  private def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(value => f"${value & 0xff}%02x").mkString

  private def _resolved(profile: String, activeoptionalworkproducts: Vector[String] = Vector.empty): CozyDocumentWorkflow.ResolvedWorkflow =
    CozyDocumentWorkflow.resolve(profile, activeoptionalworkproducts) match {
      case Right(value) => value
      case Left(cause) => throw new RuntimeException(cause)
    }

  private def _activate_optional_work_products(project: Path, profile: String, requested: Vector[String]): Vector[String] = {
    val selected = if (requested.nonEmpty) requested else _resolved(profile).workProducts.collect {
      case value if value.binding.disposition == CozyDocumentWorkflow.WorkProductDisposition.Optional => value.workProduct.id
    }
    val descriptor = project.resolve("document-project.yaml")
    val selection = if (selected.isEmpty) "activeOptionalWorkProducts: []" else "activeOptionalWorkProducts:\n" + selected.map(id => s"  - $id").mkString("\n")
    Files.writeString(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8).replace("activeOptionalWorkProducts: []", selection), StandardCharsets.UTF_8)
    selected
  }

  private def _execute(args: List[String]): String = {
    val bytes = new ByteArrayOutputStream()
    Console.withOut(new PrintStream(bytes, true, "UTF-8")) {
      CozyDocumentProject.execute(args) shouldBe true
    }
    bytes.toString("UTF-8").trim
  }

  private def _failure(args: List[String]): String =
    intercept[RuntimeException] {
      CozyDocumentProject.execute(args)
    }.getMessage

  private def _diagnostic_tokens(value: String): Vector[String] =
    """DP-[A-Z]+-\d{3}""".r.findAllIn(value).toVector

  private def _relative_files(root: Path): Set[String] = {
    val stream = Files.walk(root)
    try stream.iterator().asScala.filter(path => Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).map(root.relativize(_).toString.replace('\\', '/')).toSet
    finally stream.close()
  }

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val root = Files.createTempDirectory(name)
    try body(root)
    finally _delete(root)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach { item =>
        try Files.deleteIfExists(item) catch { case NonFatal(_) => () }
      }
      finally stream.close()
    }
}
