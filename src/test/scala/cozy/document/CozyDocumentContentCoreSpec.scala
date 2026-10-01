package cozy.document

import io.circe.Json
import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import java.nio.file.attribute.FileTime
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Sep. 30, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentContentCoreSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Content Core" should {
    "emit Content Core candidate, failed attempt, feedback, and acceptance provenance without hash fields" in {
      _with_temp_dir("content-core-evidence") { root =>
        Given("a scaffolded project with succeeded and failed dialogue inputs plus explicit human feedback and acceptance")
        val project = _scaffolded_project(root, "content-core-evidence")
        val candidate = _write(root.resolve("candidate.json"), _succeeded_dialogue("candidate-evidence", "accepted semantic content"))
        val failed = _write(root.resolve("failed.yaml"), _failed_dialogue("attempt-evidence"))
        val feedback = _write(root.resolve("feedback.json"), _feedback("feedback-evidence", "candidate-evidence", "changes-requested"))
        val acceptance = _write(root.resolve("acceptance.json"), _acceptance("acceptance-evidence", "candidate-evidence"))

        When("the public Content Core candidate, feedback, and accept forms record their evidence")
        _execute(List("document-project", "content-core", "candidate", project.toString, candidate.toString))
        _execute(List("document-project", "content-core", "candidate", project.toString, failed.toString))
        _execute(List("document-project", "content-core", "feedback", project.toString, "candidate-evidence", feedback.toString))
        _execute(List("document-project", "content-core", "accept", project.toString, "candidate-evidence", acceptance.toString))
        val candidateevidence = _load(project.resolve("evidence/content-core/candidates/candidate-evidence.yaml"))
        val attemptevidence = _load(project.resolve("evidence/content-core/attempts/attempt-evidence.yaml"))
        val feedbackevidence = _load(project.resolve("evidence/content-core/feedback/feedback-evidence.yaml"))
        val acceptanceevidence = _load(project.resolve("evidence/content-core/acceptances/acceptance-evidence.yaml"))

        Then("parsed evidence retains semantic payloads and references without any hash property")
        candidateevidence.hcursor.downField("source").downField("text").focus.flatMap(_.asString) shouldBe Some("source document")
        candidateevidence.hcursor.downField("provider").downField("id").focus.flatMap(_.asString) shouldBe Some("provider-local")
        attemptevidence.hcursor.downField("outcome").downField("value").focus.flatMap(_.asString) shouldBe Some("failed")
        feedbackevidence.hcursor.downField("candidate").downField("path").focus.flatMap(_.asString) shouldBe Some("evidence/content-core/candidates/candidate-evidence.yaml")
        acceptanceevidence.hcursor.downField("priorCore").downField("modifiedTime").focus.flatMap(_.asString).nonEmpty shouldBe true
        acceptanceevidence.hcursor.downField("resultingCore").downField("path").focus.flatMap(_.asString) shouldBe Some("content/core-en.yaml")
        Vector(candidateevidence, attemptevidence, feedbackevidence, acceptanceevidence).foreach(value => _has_hash_field(value) shouldBe false)
      }
    }

    "load Content Core candidate and feedback historical hash extras for feedback-linked revision and explicit acceptance" in {
      _with_temp_dir("content-core-historical-extras") { root =>
        Given("a candidate with changes-requested feedback and arbitrary obsolete nested hash properties")
        val project = _scaffolded_project(root, "content-core-historical-extras")
        val first = _write(root.resolve("first.json"), _succeeded_dialogue("candidate-historical-extra", "first semantic content"))
        val feedback = _write(root.resolve("feedback.json"), _feedback("feedback-historical-extra", "candidate-historical-extra", "changes-requested"))
        val revised = _write(root.resolve("revised.json"), _succeeded_dialogue("candidate-historical-revised", "revised semantic content", Some("candidate-historical-extra")))
        val acceptance = _write(root.resolve("acceptance.json"), _acceptance("acceptance-historical-extra", "candidate-historical-revised"))
        _execute(List("document-project", "content-core", "candidate", project.toString, first.toString))
        _execute(List("document-project", "content-core", "feedback", project.toString, "candidate-historical-extra", feedback.toString))
        _with_nested_legacy_hash(project.resolve("evidence/content-core/candidates/candidate-historical-extra.yaml"), "source", "[obsolete, malformed, hash]")
        _with_nested_legacy_hash(project.resolve("evidence/content-core/feedback/feedback-historical-extra.yaml"), "reviewer", "{legacy: true}")

        When("the revised candidate supersedes the historical record and an explicit reviewer accepts it")
        val revisedoutput = _execute(List("document-project", "content-core", "candidate", project.toString, revised.toString))
        val acceptedoutput = _execute(List("document-project", "content-core", "accept", project.toString, "candidate-historical-revised", acceptance.toString))

        Then("ordinary semantic loading ignores the obsolete values and preserves the valid acceptance flow")
        revisedoutput should include("candidate: candidate-historical-revised")
        acceptedoutput should include("decision: accepted")
        Files.readString(project.resolve("content/core-en.yaml"), StandardCharsets.UTF_8) should include("revised semantic content")
      }
    }

    "treat an already-applied metadata-free historical Content Core acceptance as idempotent without mutation" in {
      _with_temp_dir("content-core-metadata-free-applied") { root =>
        Given("a completed acceptance whose historical evidence has no prior timestamp")
        val project = _scaffolded_project(root, "content-core-metadata-free-applied")
        val candidate = _write(root.resolve("candidate.json"), _succeeded_dialogue("candidate-metadata-free-applied", "metadata-free applied content"))
        val acceptance = _write(root.resolve("acceptance.json"), _acceptance("acceptance-metadata-free-applied", "candidate-metadata-free-applied"))
        _execute(List("document-project", "content-core", "candidate", project.toString, candidate.toString))
        _execute(List("document-project", "content-core", "accept", project.toString, "candidate-metadata-free-applied", acceptance.toString))
        val core = project.resolve("content/core-en.yaml")
        val evidence = project.resolve("evidence/content-core/acceptances/acceptance-metadata-free-applied.yaml")
        Files.writeString(evidence, _without_modified_time(Files.readString(evidence, StandardCharsets.UTF_8)), StandardCharsets.UTF_8)
        val corebefore = Files.readAllBytes(core)
        val modifiedbefore = Files.getLastModifiedTime(core, LinkOption.NOFOLLOW_LINKS)
        val evidencebefore = Files.readAllBytes(evidence)

        When("the exact Content Core acceptance is retried")
        val output = _execute(List("document-project", "content-core", "accept", project.toString, "candidate-metadata-free-applied", acceptance.toString))

        Then("semantic equality succeeds without changing Core bytes, Core time, or historical evidence")
        output should include("decision: accepted")
        Files.readAllBytes(core) shouldBe corebefore
        Files.getLastModifiedTime(core, LinkOption.NOFOLLOW_LINKS) shouldBe modifiedbefore
        Files.readAllBytes(evidence) shouldBe evidencebefore
      }
    }

    "reject a metadata-free pending Content Core acceptance before overwriting a different current Core" in {
      _with_temp_dir("content-core-metadata-free-pending") { root =>
        Given("a pending historical acceptance without timestamp metadata and an independently changed valid Core")
        val project = _scaffolded_project(root, "content-core-metadata-free-pending")
        val candidate = _write(root.resolve("candidate.json"), _succeeded_dialogue("candidate-metadata-free-pending", "pending replacement"))
        val acceptance = _write(root.resolve("acceptance.json"), _acceptance("acceptance-metadata-free-pending", "candidate-metadata-free-pending"))
        _execute(List("document-project", "content-core", "candidate", project.toString, candidate.toString))
        val evidence = project.resolve("evidence/content-core/acceptances/acceptance-metadata-free-pending.yaml")
        Files.createDirectories(evidence.getParent)
        Files.writeString(evidence, _acceptance_record("acceptance-metadata-free-pending", "candidate-metadata-free-pending", "human-reviewer", "content/core-en.yaml", None), StandardCharsets.UTF_8)
        val core = project.resolve("content/core-en.yaml")
        Files.writeString(core, _core_yaml("content-core-metadata-free-pending", "independent current content"), StandardCharsets.UTF_8)
        val corebefore = Files.readAllBytes(core)
        val evidencebefore = Files.readAllBytes(evidence)

        When("the pending Content Core acceptance is retried")
        val failure = _failure(List("document-project", "content-core", "accept", project.toString, "candidate-metadata-free-pending", acceptance.toString))

        Then("the conservative historical boundary rejects without replacing Core or evidence")
        failure should include("DP-OP-001")
        Files.readAllBytes(core) shouldBe corebefore
        Files.readAllBytes(evidence) shouldBe evidencebefore
      }
    }

    "reject a timestamp-mismatched pending Content Core replay when a copied Core retains valid semantics" in {
      _with_temp_dir("content-core-timestamp-mismatch") { root =>
        Given("a pending acceptance with a captured Core FileTime and a copied valid Core at a deterministic later time")
        val project = _scaffolded_project(root, "content-core-timestamp-mismatch")
        val candidate = _write(root.resolve("candidate.json"), _succeeded_dialogue("candidate-timestamp-mismatch", "timestamp replacement"))
        val acceptance = _write(root.resolve("acceptance.json"), _acceptance("acceptance-timestamp-mismatch", "candidate-timestamp-mismatch"))
        _execute(List("document-project", "content-core", "candidate", project.toString, candidate.toString))
        val core = project.resolve("content/core-en.yaml")
        val prior = Files.getLastModifiedTime(core, LinkOption.NOFOLLOW_LINKS)
        val evidence = project.resolve("evidence/content-core/acceptances/acceptance-timestamp-mismatch.yaml")
        Files.createDirectories(evidence.getParent)
        Files.writeString(evidence, _acceptance_record("acceptance-timestamp-mismatch", "candidate-timestamp-mismatch", "human-reviewer", "content/core-en.yaml", Some(prior)), StandardCharsets.UTF_8)
        Files.writeString(core, "schema: \"cozy.content-core.v1\"\nid: \"content-core-timestamp-mismatch:core:en\"\nlanguage: \"en\"\naccepted: []\n", StandardCharsets.UTF_8)
        Files.setLastModifiedTime(core, FileTime.from(prior.toInstant.plusSeconds(1)))
        val corebefore = Files.readAllBytes(core)
        val evidencebefore = Files.readAllBytes(evidence)

        When("the timestamp-mismatched Content Core acceptance is retried")
        val failure = _failure(List("document-project", "content-core", "accept", project.toString, "candidate-timestamp-mismatch", acceptance.toString))

        Then("timestamp currentness rejects the replay while preserving the copied valid Core and evidence")
        failure should include("DP-OP-001")
        Files.readAllBytes(core) shouldBe corebefore
        Files.readAllBytes(evidence) shouldBe evidencebefore
      }
    }

    "keep a formatting-only already-applied Content Core acceptance idempotent with its bytes preserved" in {
      _with_temp_dir("content-core-formatting-idempotent") { root =>
        Given("an accepted candidate whose direct Core has only a comment-formatting change")
        val project = _scaffolded_project(root, "content-core-formatting-idempotent")
        val candidate = _write(root.resolve("candidate.json"), _succeeded_dialogue("candidate-formatting-idempotent", "formatting semantic content"))
        val acceptance = _write(root.resolve("acceptance.json"), _acceptance("acceptance-formatting-idempotent", "candidate-formatting-idempotent"))
        _execute(List("document-project", "content-core", "candidate", project.toString, candidate.toString))
        _execute(List("document-project", "content-core", "accept", project.toString, "candidate-formatting-idempotent", acceptance.toString))
        val core = project.resolve("content/core-en.yaml")
        Files.writeString(core, "# retained formatting-only comment\n" + Files.readString(core, StandardCharsets.UTF_8), StandardCharsets.UTF_8)
        val corebefore = Files.readAllBytes(core)
        val modifiedbefore = Files.getLastModifiedTime(core, LinkOption.NOFOLLOW_LINKS)

        When("the exact Content Core acceptance is retried after the formatting-only change")
        val output = _execute(List("document-project", "content-core", "accept", project.toString, "candidate-formatting-idempotent", acceptance.toString))

        Then("ordered accepted-entry equality succeeds and preserves the supplied Core bytes and FileTime")
        output should include("decision: accepted")
        Files.readAllBytes(core) shouldBe corebefore
        Files.getLastModifiedTime(core, LinkOption.NOFOLLOW_LINKS) shouldBe modifiedbefore
      }
    }

    "preserve successful Content Core evidence when logical references, direct paths, reviewer decisions, or replacements are invalid" in {
      _with_temp_dir("content-core-rejections") { root =>
        Given("a successfully accepted Core and independent malformed candidate, path, reviewer, and decision inputs")
        val project = _scaffolded_project(root, "content-core-rejections")
        val candidate = _write(root.resolve("candidate.json"), _succeeded_dialogue("candidate-success", "successful Core content"))
        val acceptance = _write(root.resolve("acceptance.json"), _acceptance("acceptance-success", "candidate-success"))
        _execute(List("document-project", "content-core", "candidate", project.toString, candidate.toString))
        _execute(List("document-project", "content-core", "accept", project.toString, "candidate-success", acceptance.toString))
        val missingreference = _write(root.resolve("missing-reference.json"), _succeeded_dialogue("candidate-missing-reference", "missing reference", Some("candidate-absent")))
        val invalidreplacement = _write(root.resolve("invalid-replacement.json"), _invalid_replacement_dialogue("candidate-invalid-replacement"))
        val linked = root.resolve("linked-candidate.json")
        Files.createSymbolicLink(linked, candidate)
        val differentreviewer = _write(root.resolve("different-reviewer.json"), _acceptance("acceptance-success", "candidate-success", reviewer = "other-reviewer"))
        val invaliddecision = _write(root.resolve("invalid-decision.json"), _acceptance("acceptance-invalid-decision", "candidate-success", decision = "rejected"))
        val core = project.resolve("content/core-en.yaml")
        val evidence = project.resolve("evidence/content-core/acceptances/acceptance-success.yaml")
        val corebefore = Files.readAllBytes(core)
        val evidencebefore = Files.readAllBytes(evidence)

        When("the invalid Content Core forms are admitted through their public boundaries")
        val failures = Vector(
          _failure(List("document-project", "content-core", "candidate", project.toString, missingreference.toString)),
          _failure(List("document-project", "content-core", "candidate", project.toString, invalidreplacement.toString)),
          _failure(List("document-project", "content-core", "candidate", project.toString, linked.toString)),
          _failure(List("document-project", "content-core", "accept", project.toString, "candidate-success", differentreviewer.toString)),
          _failure(List("document-project", "content-core", "accept", project.toString, "candidate-success", invaliddecision.toString))
        )

        Then("all failures precede mutation of the successful Core and acceptance evidence")
        failures(0) should include("DP-OP-001")
        failures(1) should include("DP-DESC-001")
        failures(2) should include("DP-PATH-001")
        failures(3) should include("DP-OP-001")
        failures(4) should include("DP-CLI-001")
        Files.readAllBytes(core) shouldBe corebefore
        Files.readAllBytes(evidence) shouldBe evidencebefore
      }
    }

    "preserve Content Core semantic acceptance for generated obsolete nested evidence properties" in {
      Given("bounded YAML values for obsolete historical hash properties")
      val values = Gen.oneOf("null", "true", "17", "[]", "[old, malformed]", "{legacy: stale}", "\"not-a-digest\"")

      When("ScalaCheck applies at least twenty generated historical values to nested candidate and feedback evidence")
      val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), Prop.forAll(values) { value =>
        _with_temp_dir("content-core-historical-property") { root =>
          Given("a feedback-linked candidate revision with one generated obsolete nested property value")
          val project = _scaffolded_project(root, "content-core-historical-property")
          val first = _write(root.resolve("first.json"), _succeeded_dialogue("candidate-property-first", "first property content"))
          val feedback = _write(root.resolve("feedback.json"), _feedback("feedback-property-first", "candidate-property-first", "changes-requested"))
          val revised = _write(root.resolve("revised.json"), _succeeded_dialogue("candidate-property-revised", "revised property content", Some("candidate-property-first")))
          val acceptance = _write(root.resolve("acceptance.json"), _acceptance("acceptance-property-revised", "candidate-property-revised"))
          _execute(List("document-project", "content-core", "candidate", project.toString, first.toString))
          _execute(List("document-project", "content-core", "feedback", project.toString, "candidate-property-first", feedback.toString))
          _with_nested_legacy_hash(project.resolve("evidence/content-core/candidates/candidate-property-first.yaml"), "source", value)
          _with_nested_legacy_hash(project.resolve("evidence/content-core/feedback/feedback-property-first.yaml"), "reviewer", value)

          When("the generated historical Content Core evidence is read for revision and explicit acceptance")
          _execute(List("document-project", "content-core", "candidate", project.toString, revised.toString))
          val output = _execute(List("document-project", "content-core", "accept", project.toString, "candidate-property-revised", acceptance.toString))

          Then("the accepted Core retains only the generated-independent semantic replacement")
          output.contains("decision: accepted") && Files.readString(project.resolve("content/core-en.yaml"), StandardCharsets.UTF_8).contains("revised property content")
        }
      })

      Then("all generated Content Core checks pass with the required bounded case count")
      check.passed shouldBe true
      check.succeeded should be >= 20
    }
  }

  private def _scaffolded_project(root: Path, slug: String): Path = {
    val parent = Files.createDirectory(root.resolve("parent"))
    _execute(List("document-project", "scaffold", slug, "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
    parent.resolve(s"$slug.dox")
  }

  private def _succeeded_dialogue(candidateid: String, text: String, supersedes: Option[String] = None): String = {
    val supersedesfield = supersedes.map(value => ",\n  \"supersedes\": \"" + value + "\"").getOrElse("")
    s"""{
  "schema": "cozy.content-core-dialogue.v1",
  "id": "$candidateid",
  "source": "source document",
  "idea": "content-core idea",
  "provider": "provider-local",
  "model": "model-local",
  "request": "completed provider request",
  "response": "completed provider response",
  "outcome": "succeeded",
  "diagnostics": [],
  "candidate": {"accepted": [{"id": "accepted-$candidateid", "text": "$text"}]}$supersedesfield
}
"""
  }

  private def _invalid_replacement_dialogue(candidateid: String): String =
    s"""{
  "schema": "cozy.content-core-dialogue.v1",
  "id": "$candidateid",
  "source": "source document",
  "idea": "content-core idea",
  "provider": "provider-local",
  "model": "model-local",
  "request": "completed provider request",
  "response": "completed provider response",
  "outcome": "succeeded",
  "diagnostics": [],
  "candidate": {"accepted": [{"id": "duplicate", "text": "one"}, {"id": "duplicate", "text": "two"}]}
}
"""

  private def _failed_dialogue(attemptid: String): String =
    s"""schema: cozy.content-core-dialogue.v1
id: $attemptid
source: source document
idea: content-core idea
provider: provider-local
model: model-local
request: completed provider request
response: provider declined the draft
outcome: failed
diagnostics:
  - provider declined the draft
"""

  private def _feedback(feedbackid: String, candidateid: String, decision: String): String = {
    val payload = decision match {
      case "changes-requested" => "\"feedback\": \"please revise the Core\""
      case "rejected" => "\"rejectionReason\": \"human reviewer rejected the Core\""
      case _ => throw new IllegalArgumentException(s"unsupported feedback fixture decision: $decision")
    }
    s"""{
  "schema": "cozy.content-core-feedback.v1",
  "id": "$feedbackid",
  "candidate": "$candidateid",
  "reviewer": "human-reviewer",
  "decision": "$decision",
  $payload
}
"""
  }

  private def _acceptance(acceptanceid: String, candidateid: String, reviewer: String = "human-reviewer", decision: String = "accepted"): String =
    s"""{
  "schema": "cozy.content-core-acceptance.v1",
  "id": "$acceptanceid",
  "candidate": "$candidateid",
  "reviewer": "$reviewer",
  "decision": "$decision"
}
"""

  private def _acceptance_record(acceptanceid: String, candidateid: String, reviewer: String, corepath: String, modifiedtime: Option[FileTime]): String = {
    val time = modifiedtime.map(value => "  modifiedTime: \"" + value.toInstant.toString + "\"\n").getOrElse("")
    s"""schema: cozy.content-core-acceptance-record.v1
id: "$acceptanceid"
candidate:
  id: "$candidateid"
  path: "evidence/content-core/candidates/$candidateid.yaml"
reviewer:
  id: "$reviewer"
decision:
  value: accepted
priorCore:
  path: "$corepath"
${time}resultingCore:
  path: "$corepath"
"""
  }

  private def _core_yaml(slug: String, text: String): String =
    s"schema: cozy.content-core.v1\nid: $slug:core:en\nlanguage: en\naccepted:\n  - id: independent-core\n    text: $text\n"

  private def _with_nested_legacy_hash(path: Path, field: String, value: String): Unit = {
    val original = Files.readString(path, StandardCharsets.UTF_8)
    val marker = s"$field:\n"
    Files.writeString(path, original.replace(marker, marker + s"  sha256: $value\n"), StandardCharsets.UTF_8)
  }

  private def _without_modified_time(value: String): String =
    value.replaceFirst("(?m)^  modifiedTime: .*\\n", "")

  private def _has_hash_field(value: Json): Boolean =
    value.asObject.exists(_.toMap.exists { case (name, item) => name == "sha256" || _has_hash_field(item) }) ||
      value.asArray.exists(_.exists(_has_hash_field))

  private def _load(path: Path): Json =
    StructuredDocumentLoader.loadJson(InputSource(path.toFile)).take

  private def _write(path: Path, value: String): Path = {
    Files.writeString(path, value, StandardCharsets.UTF_8)
    path
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

  private def _with_temp_dir[A](name: String)(body: Path => A): A = {
    val parent = Paths.get("target", "test-generated", "cozy-document-content-core").toAbsolutePath.normalize()
    Files.createDirectories(parent)
    val root = Files.createTempDirectory(parent, s"$name-").toRealPath()
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
