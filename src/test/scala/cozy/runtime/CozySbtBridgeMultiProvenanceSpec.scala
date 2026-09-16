package cozy.runtime

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.Comparator
import scala.collection.JavaConverters._
import cozy.modeler.GenerationProvenance
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

/*
 * @since   Sep. 17, 2026
 * @version Sep. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozySbtBridgeMultiProvenanceSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "sbt-bridge v1 aggregate-rebind-generation-provenance" should {
    "dispatch two explicit delegated V1 inputs to V2 aggregate evidence" in {
      _with_temp_dir("cozy-sbt-bridge-multi-provenance") { directory =>
        Given("two delegated V1 manifests and their installed project outputs")
        val projectroot = directory.resolve("project")
        val alpha = _write(
          projectroot.resolve("src/main/cozy/alpha.cml"),
          "# VALUE\n\n## Alpha\n"
        )
        val beta = _write(
          projectroot.resolve("src/main/cozy/beta.cml"),
          "# VALUE\n\n## Beta\n"
        )
        val alphadelegated = _make_delegated(
          directory.resolve("delegate-alpha"),
          alpha,
          "src/main/cozy/alpha.cml",
          "target/generated/Alpha.scala" -> "object Alpha\n"
        )
        val betadelegated = _make_delegated(
          directory.resolve("delegate-beta"),
          beta,
          "src/main/cozy/beta.cml",
          "target/generated/Beta.scala" -> "object Beta\n"
        )
        _install(projectroot, alphadelegated)
        _install(projectroot, betadelegated)
        val pairs = Vector(
          Json.obj(
            "delegatedProvenance" -> alphadelegated.provenance.toString,
            "delegatedOutputRoot" -> alphadelegated.root.toString
          ),
          Json.obj(
            "delegatedProvenance" -> betadelegated.provenance.toString,
            "delegatedOutputRoot" -> betadelegated.root.toString
          )
        )
        val request = _write_request(
          directory.resolve("aggregate-request.json"),
          Vector(
            "--delegated-inputs-json", Json.stringify(Json.toJson(pairs)),
            "--project-root", projectroot.toString
          )
        )

        When("the actual V1 bridge dispatches the explicit pair transport")
        CozySbtBridge.execute(List("v1", "--request", request.toString))

        Then("the aggregate authority publishes V2 evidence for both forwarded sources")
        val aggregate = GenerationProvenance.requireValidForPackaging(
          provenancePath = projectroot.resolve(GenerationProvenance.METADATA_PATH),
          projectRoot = projectroot,
          expectedCncfTargetVersion = Some("0.5.2-SNAPSHOT"),
          expectedCozyGeneratorVersion = Some("0.3.2-SNAPSHOT")
        )
        aggregate.schemaVersion shouldBe GenerationProvenance.AGGREGATE_SCHEMA_VERSION
        val published = Json.parse(Files.readString(
          projectroot.resolve(GenerationProvenance.METADATA_PATH),
          StandardCharsets.UTF_8
        ))
        (published \ "sources").as[Vector[play.api.libs.json.JsObject]].map { source =>
          (source \ "identity").as[String]
        } shouldBe Vector(
          "src/main/cozy/alpha.cml",
          "src/main/cozy/beta.cml"
        )
        (published \ "output" \ "artifacts").as[Vector[play.api.libs.json.JsObject]].map { artifact =>
          (artifact \ "path").as[String]
        } shouldBe Vector(
          "target/generated/Alpha.scala",
          "target/generated/Beta.scala"
        )
      }
    }

    "reject malformed or empty delegated pair transport" in {
      _with_temp_dir("cozy-sbt-bridge-invalid-multi-provenance") { directory =>
        Given("empty and malformed aggregate-rebind bridge requests")
        val projectroot = directory.resolve("project")
        val emptyrequest = _write_request(
          directory.resolve("empty-request.json"),
          Vector("--delegated-inputs-json", "[]", "--project-root", projectroot.toString)
        )
        val malformedrequest = _write_request(
          directory.resolve("malformed-request.json"),
          Vector(
            "--delegated-inputs-json", "[{\"delegatedProvenance\":\"/tmp/delegate.json\"}]",
            "--project-root", projectroot.toString
          )
        )

        When("the actual bridge parses each invalid transport")
        val emptyerror = intercept[Exception] {
          CozySbtBridge.execute(List("v1", "--request", emptyrequest.toString))
        }
        val malformederror = intercept[Exception] {
          CozySbtBridge.execute(List("v1", "--request", malformedrequest.toString))
        }

        Then("both requests fail before aggregate publication")
        emptyerror.getMessage should include("Invalid --delegated-inputs-json")
        malformederror.getMessage should include("Invalid --delegated-inputs-json")
        Files.exists(projectroot.resolve(GenerationProvenance.METADATA_PATH)) shouldBe false
      }
    }
  }

  private final case class Delegated(
    provenance: Path,
    root: Path,
    manifest: GenerationProvenance.Manifest
  )

  private def _make_delegated(
    outputroot: Path,
    source: Path,
    sourceidentity: String,
    artifact: (String, String)
  ): Delegated = {
    _write(outputroot.resolve(artifact._1), artifact._2)
    val manifest = GenerationProvenance.write(
      outputroot,
      GenerationProvenance.requireSourceSnapshot(source, sourceidentity),
      GenerationProvenance.Inputs(
        cncfTargetVersion = "0.5.2-SNAPSHOT",
        runtimeDescriptorSha256 = "a" * 64,
        cozyGeneratorVersion = "0.3.2-SNAPSHOT",
        simpleModelerBackendVersion = "0.5.2-SNAPSHOT",
        simpleModelingModelVersion = "0.5.2-SNAPSHOT",
        sourceIdentity = sourceidentity,
        sourceSha256 = _sha256(source)
      )
    )
    Delegated(
      outputroot.resolve(GenerationProvenance.METADATA_PATH),
      outputroot,
      manifest
    )
  }

  private def _install(projectroot: Path, delegated: Delegated): Unit =
    delegated.manifest.artifacts.foreach { artifact =>
      _write(
        projectroot.resolve(artifact.path),
        Files.readString(delegated.root.resolve(artifact.path), StandardCharsets.UTF_8)
      )
    }

  private def _write_request(path: Path, arguments: Vector[String]): Path =
    _write(
      path,
      Json.prettyPrint(Json.obj(
        "version" -> "v1",
        "action" -> "aggregate-rebind-generation-provenance",
        "arguments" -> arguments,
        "settings" -> Json.obj()
      )) + "\n"
    )

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, content, StandardCharsets.UTF_8)
    path.toAbsolutePath.normalize()
  }

  private def _sha256(path: Path): String =
    MessageDigest
      .getInstance("SHA-256")
      .digest(Files.readAllBytes(path))
      .map(byte => f"${byte & 0xff}%02x")
      .mkString

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val workroot = Path.of("target/cozy-test/work/cozy-sbt-bridge-multi-provenance-spec").toAbsolutePath.normalize()
    Files.createDirectories(workroot)
    val directory = Files.createTempDirectory(workroot, s"$prefix-")
    try body(directory)
    finally _delete_tree(directory)
  }

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try
        stream.sorted(Comparator.reverseOrder()).iterator().asScala.foreach(Files.deleteIfExists(_))
      finally stream.close()
    }
}
