package cozy.review

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 16, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyCarReviewProviderSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy CAR Review Provider" should {
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
}
