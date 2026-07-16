package cozy.review

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import cozy.lint.CozyCarLint
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, Json}
import scala.collection.JavaConverters._

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyCarReviewProviderSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy CAR Review Provider" should {
    "emit its neutral descriptor without receiving a project path" in {
      Given("one provider version")

      When("a local client invokes the descriptor-only command")
      val result = CozyCarReviewProviderCommand.describe(List("--provider-version", "0.3.0-SNAPSHOT", "--descriptor"))

      Then("the command returns only provider identity and does not need a workspace argument")
      val descriptor = Json.parse(result.toOption.get).as[JsObject]
      (descriptor \ "provider" \ "id").as[String] shouldBe "cozy"
      (descriptor \ "provider" \ "version").as[String] shouldBe "0.3.0-SNAPSHOT"
    }

    "emit a CBD-neutral v1 bundle from CAR project, CML, build, lint, ABI, and documentation evidence" in {
      Given("one local CAR project with CML, build metadata, and an ABI lint input")
      val root = Files.createTempDirectory("cozy-car-review-provider")
      try {
        _write(root.resolve("project.yaml"),
          """project:
            |  kind: car
            |  name: sample-car
            |  component:
            |    name: sample-component
            |    version: 0.1.0-SNAPSHOT
            |packaging:
            |  car:
            |    runtime:
            |      cncf:
            |        minimum: 0.5.1
            |        tested:
            |          - 0.5.1
            |""".stripMargin)
        _write(root.resolve("build.sbt"), "name := \"sample-car\"\n")
        _write(root.resolve("src/main/cozy/sample-car.cml"), "# COMPONENT\n\n## sample-component\n")
        _write(root.resolve("src/main/car/abi-manifest.json"), "{}\n")
        _write(root.resolve("target/sample-car-0.1.0-SNAPSHOT.car"), "car archive\n")
        val descriptor = CozyCarReviewProvider.descriptor("0.3.0-SNAPSHOT")

        When("Cozy projects its static analysis through the generic provider contract")
        val bundle = CozyCarReviewProvider.evidenceBundle(
          CozyCarReviewProvider.Request(
            "review-example-001",
            CozyCarReviewProvider.Target("project", Some("org.textus"), "sample-car", Some("0.1.0-SNAPSHOT"), "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            "sha256:d88fe085924cc9d234d233963bb624e584826bb8f02f19fc47253888a7c21d97",
            CozyCarReviewProvider.Limits(2000, 1000, 16777216L, 120000L),
            Vector("cozy.car-analysis"),
            Vector.empty,
            Vector("cozy.car.*"),
            Vector.empty
          ),
          root,
          "0.3.0-SNAPSHOT"
        )

        Then("provider, rule set, digests, static evidence kinds, lint observations, and runtime limitation remain explicit")
        (bundle \ "schemaVersion").as[String] shouldBe "textus.cbd.review-provider.v1"
        (descriptor \ "provider" \ "id").as[String] shouldBe "cozy"
        (descriptor \ "capabilities").as[Vector[play.api.libs.json.JsObject]].flatMap(x => (x \ "evidenceKinds").as[Vector[String]]).toSet should contain allOf ("car-project", "cml-model", "build", "car-package", "abi", "documentation")
        (bundle \ "provider" \ "id").as[String] shouldBe "cozy"
        (bundle \ "ruleSet" \ "id").as[String] shouldBe "cozy.car-review"
        (bundle \ "requestDigest").as[String] shouldBe "sha256:d88fe085924cc9d234d233963bb624e584826bb8f02f19fc47253888a7c21d97"
        (bundle \ "bundleDigest").as[String] should fullyMatch regex "sha256:[0-9a-f]{64}"
        CozyCarReviewProvider.bundleDigest(bundle) shouldBe (bundle \ "bundleDigest").as[String]
        (bundle \ "evidence").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "kind").as[String]).toSet should contain allOf ("car-project", "cml-model", "build", "car-package", "abi", "documentation")
        ((bundle \ "evidence").as[Vector[play.api.libs.json.JsObject]].find(x => (x \ "id").as[String] == "evidence-project-yaml").get \ "facts" \ "supportedCncfVersions").as[Vector[String]] should contain("0.5.1")
        (bundle \ "limitations").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "code").as[String]) should contain("runtime-evidence-not-supported")
      } finally {
        _delete(root)
      }
    }

    "preserve every integrated CAR lint finding while the independent lint command keeps the same result" in {
      Given("one CAR project that produces build, CML, ABI, and documentation lint results")
      val root = Files.createTempDirectory("cozy-car-review-provider-lint")
      try {
        _write(root.resolve("project.yaml"), "project:\n  kind: car\n  name: sample-car\n")
        _write(root.resolve("build.sbt"), "name := \"sample-car\"\n")
        _write(root.resolve("src/main/cozy/sample-car.cml"), "# COMPONENT\n\n## sample-car\n")
        _write(root.resolve("src/main/car/abi-manifest.json"), "{}\n")
        val request = CozyCarReviewProvider.Request(
          "review-example-001",
          CozyCarReviewProvider.Target("project", None, "sample-car", None, "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
          "sha256:d88fe085924cc9d234d233963bb624e584826bb8f02f19fc47253888a7c21d97",
          CozyCarReviewProvider.Limits(2000, 1000, 16777216L, 120000L),
          Vector("cozy.car-analysis"),
          Vector.empty,
          Vector("cozy.car.*"),
          Vector.empty
        )

        When("Cozy emits provider evidence and its focused lint command runs independently")
        val bundle = CozyCarReviewProvider.evidenceBundle(request, root, "0.3.0-SNAPSHOT")
        val direct = CozyCarLint.lint(root, None, noabi = false)
        val stdout = new ByteArrayOutputStream()
        val exitcode = Console.withOut(new PrintStream(stdout, true, StandardCharsets.UTF_8.name())) {
          CozyCarLint.execute(List(root.toString, "--format", "json"))
        }

        Then("all provider lint evidence preserves the exact focused lint result and command JSON")
        val evidence = (bundle \ "evidence").as[Vector[JsObject]].filter(x => (x \ "subject" \ "kind").as[String] == "lint-rule")
        evidence.map(_adapter_finding) shouldBe direct.map(_lint_finding(_, root))
        Json.parse(stdout.toString(StandardCharsets.UTF_8.name())) shouldBe Json.parse(CozyCarLint.toJson(direct))
        exitcode shouldBe (if (direct.exists(_.level == CozyCarLint.Level.Fail)) 1 else 0)
      } finally {
        _delete(root)
      }
    }

    "accept the neutral provider request through the bounded command without CBD Support classes" in {
      Given("one admitted CAR project and a complete provider request JSON")
      val root = Files.createTempDirectory("cozy-car-review-provider-command")
      try {
        _write(root.resolve("project.yaml"), "project:\n  kind: car\n  name: sample-car\n")
        _write(root.resolve("src/main/cozy/sample-car.cml"), "# COMPONENT\n\n## sample-car\n")
        val request = Json.stringify(Json.obj(
          "schemaVersion" -> CozyCarReviewProvider.schemaVersion,
          "documentType" -> "provider-request",
          "reviewId" -> "review-example-001",
          "target" -> Json.obj(
            "kind" -> "project",
            "name" -> "sample-car",
            "digest" -> "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
          ),
          "limits" -> Json.obj(
            "maxEvidenceItems" -> 2000,
            "maxObservations" -> 1000,
            "maxInputBytes" -> 16777216,
            "timeoutMillis" -> 120000
          ),
          "requestedCapabilities" -> Json.arr("cozy.car-analysis"),
          "requestedEvidenceKinds" -> Json.arr("car-project", "cml-model"),
          "rules" -> Json.obj("include" -> Json.arr("cozy.car.*"), "exclude" -> Json.arr())
        ))

        When("a transport-neutral local command supplies the request on standard input")
        val result = CozyCarReviewProviderCommand.execute(
          List("--project-root", root.toString, "--provider-version", "0.3.0-SNAPSHOT", "--request-stdin"),
          request
        )

        Then("Cozy produces a CBD-neutral evidence bundle bound to the computed request digest")
        val bundle = Json.parse(result.toOption.get).as[JsObject]
        (bundle \ "reviewId").as[String] shouldBe "review-example-001"
        (bundle \ "provider" \ "id").as[String] shouldBe "cozy"
        (bundle \ "requestDigest").as[String] shouldBe CozyCarReviewProvider.request(request).toOption.get.requestDigest
      } finally {
        _delete(root)
      }
    }

    "preserve request bounds as attributable input and result limitations" in {
      Given("one CAR project whose direct provider inputs exceed a one-byte request limit")
      val root = Files.createTempDirectory("cozy-car-review-provider-bounds")
      try {
        _write(root.resolve("project.yaml"), "project:\n  name: sample-car\n")
        _write(root.resolve("build.sbt"), "name := \"sample-car\"\n")

        When("Cozy receives an admitted request with minimal finite limits")
        val bundle = CozyCarReviewProvider.evidenceBundle(
          CozyCarReviewProvider.Request(
            "review-example-001",
            CozyCarReviewProvider.Target("project", None, "sample-car", None, "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            "sha256:d88fe085924cc9d234d233963bb624e584826bb8f02f19fc47253888a7c21d97",
            CozyCarReviewProvider.Limits(1, 1, 1L, 1L),
            Vector("cozy.car-analysis"),
            Vector.empty,
            Vector("cozy.car.*"),
            Vector.empty
          ),
          root,
          "0.3.0-SNAPSHOT"
        )

        Then("the static inputs are withheld and the byte limit remains visible without a permissive fallback")
        (bundle \ "evidence").as[Vector[play.api.libs.json.JsObject]] shouldBe empty
        (bundle \ "observations").as[Vector[play.api.libs.json.JsObject]].size shouldBe 1
        (bundle \ "limitations").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "code").as[String]) should contain("provider-input-byte-limit")
      } finally {
        _delete(root)
      }
    }

    "omit an empty root-relative lint location rather than emitting an unsafe path" in {
      Given("one CAR project whose ABI lint finding belongs to the project root")
      val root = Files.createTempDirectory("cozy-car-review-provider-root-location")
      try {
        _write(root.resolve("project.yaml"), "project:\n  kind: car\n  name: sample-car\n")

        When("Cozy projects the root-owned finding through a provider evidence bundle")
        val bundle = CozyCarReviewProvider.evidenceBundle(
          CozyCarReviewProvider.Request(
            "review-example-001",
            CozyCarReviewProvider.Target("project", None, "sample-car", None, "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            "sha256:d88fe085924cc9d234d233963bb624e584826bb8f02f19fc47253888a7c21d97",
            CozyCarReviewProvider.Limits(2000, 1000, 16777216L, 120000L),
            Vector("cozy.car-analysis"),
            Vector.empty,
            Vector.empty,
            Vector.empty
          ),
          root,
          "0.3.0-SNAPSHOT"
        )

        Then("the finding remains attributable but has no empty location path")
        val finding = (bundle \ "evidence").as[Vector[JsObject]].find(x => (x \ "facts" \ "code").as[String] == "abi.manifest.missing").get
        (finding \ "location").toOption shouldBe None
      } finally {
        _delete(root)
      }
    }

    "refuse unrequested capability work without selecting another provider behavior" in {
      Given("one CAR project and a request for a capability Cozy does not own")
      val root = Files.createTempDirectory("cozy-car-review-provider-selection")
      try {
        _write(root.resolve("project.yaml"), "project:\n  name: sample-car\n")

        When("the provider receives no Cozy capability selection")
        val bundle = CozyCarReviewProvider.evidenceBundle(
          CozyCarReviewProvider.Request(
            "review-example-001",
            CozyCarReviewProvider.Target("project", None, "sample-car", None, "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            "sha256:d88fe085924cc9d234d233963bb624e584826bb8f02f19fc47253888a7c21d97",
            CozyCarReviewProvider.Limits(10, 10, 1024L, 1000L),
            Vector("other.provider"),
            Vector.empty,
            Vector.empty,
            Vector.empty
          ),
          root,
          "0.3.0-SNAPSHOT"
        )

        Then("no static evidence is produced and the missing capability remains attributable")
        (bundle \ "evidence").as[Vector[play.api.libs.json.JsObject]] shouldBe empty
        (bundle \ "limitations").as[Vector[play.api.libs.json.JsObject]].map(x => (x \ "code").as[String]) should contain("provider-capability-not-requested")
      } finally {
        _delete(root)
      }
    }
  }

  private def _write(path: Path, text: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.write(path, text.getBytes(StandardCharsets.UTF_8))
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.sorted(java.util.Comparator.reverseOrder()).forEach(x => Files.deleteIfExists(x))
      finally stream.close()
    }

  private def _adapter_finding(value: JsObject): (String, String, String, String, Int, String) =
    (
      (value \ "facts" \ "category").as[String],
      (value \ "facts" \ "code").as[String],
      (value \ "facts" \ "level").as[String],
      (value \ "location" \ "path").as[String],
      (value \ "facts" \ "line").as[Int],
      (value \ "facts" \ "message").as[String]
    )

  private def _lint_finding(finding: CozyCarLint.Finding, root: Path): (String, String, String, String, Int, String) =
    (
      finding.category,
      finding.code,
      finding.level.name,
      root.relativize(finding.path.toAbsolutePath.normalize()).iterator().asScala.map(_.toString).mkString("/"),
      finding.line,
      finding.message
    )
}
