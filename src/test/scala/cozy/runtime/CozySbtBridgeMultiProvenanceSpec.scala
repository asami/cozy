package cozy.runtime

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.nio.file.attribute.PosixFilePermission
import java.util.Comparator
import scala.collection.JavaConverters._
import cozy.modeler.GenerationProvenance
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

/*
 * @since   Sep. 17, 2026
 * @version Oct. 17, 2026
 * @author  ASAMI, Tomoharu
 */
object CozySbtBridgeMultiProvenanceSpec {
  private final case class Delegated(
    provenance: Path,
    root: Path,
    manifest: GenerationProvenance.Manifest
  )
}

final class CozySbtBridgeMultiProvenanceSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  import CozySbtBridgeMultiProvenanceSpec.Delegated

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

        val aggregate = GenerationProvenance.requireValidForPackaging(
          provenancePath = projectroot.resolve(GenerationProvenance.METADATA_PATH),
          projectRoot = projectroot,
          expectedCncfTargetVersion = Some("0.5.2-SNAPSHOT"),
          expectedCozyGeneratorVersion = Some("0.3.2-SNAPSHOT")
        )
        val published = Json.parse(Files.readString(
          projectroot.resolve(GenerationProvenance.METADATA_PATH),
          StandardCharsets.UTF_8
        ))
        Then("the actual bridge publishes exactly the known V2 sources and artifact paths")
        aggregate.schemaVersion shouldBe GenerationProvenance.AGGREGATE_SCHEMA_VERSION
        published shouldBe aggregate.toJson
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

        val published = Files.exists(projectroot.resolve(GenerationProvenance.METADATA_PATH))

        Then("both requests fail before aggregate publication")
        emptyerror.getMessage should include("Invalid --delegated-inputs-json")
        malformederror.getMessage should include("Invalid --delegated-inputs-json")
        published shouldBe false
      }
    }

    "V2 output ownership" which {
      "admit delegated output beside independently changing build-generated Scala" in {
        _with_temp_dir("cozy-sbt-bridge-independent-output") { directory =>
          Given("two delegated sources and an independent Supervisor BuildVersion sharing target")
          val fixture = _ownership_fixture(directory, _supervisor_build_version, "object BuildVersion\n")

          When("the actual bridge publishes the selected union and packaging admits it")
          val admitted = _publish_and_admit(fixture)
          val published = _evidence_text(fixture)
          val independentcontent = Files.readString(fixture.independent, StandardCharsets.UTF_8)
          val packaged = GenerationProvenance.requireValidPackagedMetadata(
            provenancePath = fixture.projectroot.resolve(GenerationProvenance.METADATA_PATH),
            expectedCncfTargetVersion = "0.5.2-SNAPSHOT",
            expectedCozyGeneratorVersion = "0.3.2-SNAPSHOT"
          )

          Then("only the delegated claims are recorded and the independent file is preserved")
          admitted.schemaVersion shouldBe GenerationProvenance.AGGREGATE_SCHEMA_VERSION
          (admitted.toJson \ "output" \ "artifacts").as[Vector[play.api.libs.json.JsObject]] shouldBe
            fixture.claims.map(_.toJson)
          (Json.parse(published) \ "output" \ "artifacts").as[Vector[play.api.libs.json.JsObject]] shouldBe
            fixture.claims.map(_.toJson)
          (Json.parse(published) \ "sources").as[Vector[play.api.libs.json.JsObject]].map { source =>
            (source \ "identity").as[String]
          } shouldBe Vector("src/main/cozy/alpha.cml", "src/main/cozy/beta.cml")
          independentcontent shouldBe "object BuildVersion\n"
          packaged.toJson shouldBe admitted.toJson

          When("another independent Scala file is added beside the claimed output")
          val added = _write(fixture.projectroot.resolve("target/generated/Independent.scala"), "object Independent\n")
          val afteraddition = _admit(fixture)
          val additionevidence = _evidence_text(fixture)

          Then("package admission and the published evidence remain unchanged")
          afteraddition.toJson shouldBe admitted.toJson
          additionevidence shouldBe published

          When("the independent BuildVersion bytes change")
          _write(fixture.independent, "object BuildVersion { val version = \"changed\" }\n")
          val afterchange = _admit(fixture)
          val changeevidence = _evidence_text(fixture)

          Then("package admission and the published evidence still remain unchanged")
          afterchange.toJson shouldBe admitted.toJson
          changeevidence shouldBe published

          When("the independent build-generated Scala files are removed")
          Files.delete(added)
          Files.delete(fixture.independent)
          val afterremoval = _admit(fixture)
          val removalevidence = _evidence_text(fixture)

          Then("removing unclaimed files preserves package admission and V2 evidence")
          afterremoval.toJson shouldBe admitted.toJson
          removalevidence shouldBe published
        }
      }

      "preserve exact claims across forty independent path and content variations" in {
        Given("independent Scala identities and contents spanning shared and separate managed trees")
        val variations = for {
          suffix <- Gen.listOfN(12, Gen.alphaLowerChar).map(_.mkString)
          content <- Gen.listOfN(24, Gen.alphaLowerChar).map(_.mkString)
        } yield (suffix, "object Independent { val value = \"" + content + "\" }\n")

        When("forty variations pass through the actual bridge and package admission")
        val propertyresult = Test.check(
          Test.Parameters.default.withMinSuccessfulTests(40),
          Prop.forAll(variations) { case (suffix, content) =>
            _with_temp_dir("cozy-sbt-bridge-independent-property") { directory =>
              Given("two selected delegated sources and independent Scala in three output locations")
              val paths = Vector(
                s"target/generated/Independent$suffix.scala",
                "target/scala-3.3.8/src_managed/main/org/simplemodeling/textus/supervisor/meta/" +
                  s"BuildVersion$suffix.scala",
                s"target/independent/$suffix/Independent.scala"
              )
              val fixture = _ownership_fixture(directory, paths.head, content)
              paths.tail.foreach(path => _write(fixture.projectroot.resolve(path), content))

              When("the actual bridge aggregates the explicit inputs and packaging admits their output")
              val admitted = _publish_and_admit(fixture)
              val published = Json.parse(_evidence_text(fixture))
              val independentcontents = paths.map { path =>
                Files.readString(fixture.projectroot.resolve(path), StandardCharsets.UTF_8)
              }

              Then("returned and published claims equal only the delegated union and foreign bytes survive")
              admitted.schemaVersion shouldBe GenerationProvenance.AGGREGATE_SCHEMA_VERSION
              (admitted.toJson \ "output" \ "artifacts").as[Vector[play.api.libs.json.JsObject]] shouldBe
                fixture.claims.map(_.toJson)
              (published \ "output" \ "artifacts").as[Vector[play.api.libs.json.JsObject]] shouldBe
                fixture.claims.map(_.toJson)
              independentcontents shouldBe Vector.fill(paths.size)(content)
              true
            }
          }
        )

        Then("all forty successful ownership cases satisfy the same contract")
        propertyresult.passed shouldBe true
      }

      "retain path claims when readable generated content changes" in {
        _with_temp_dir("cozy-sbt-bridge-claimed-change") { directory =>
          Given("two selected delegated sources and an independent build-generated Scala file")
          val fixture = _ownership_fixture(directory, _supervisor_build_version, "object BuildVersion\n")

          When("the actual bridge publishes their aggregate and packaging admits it")
          val admitted = _publish_and_admit(fixture)

          Then("the aggregate is a valid V2 starting point")
          admitted.schemaVersion shouldBe GenerationProvenance.AGGREGATE_SCHEMA_VERSION

          When("a claimed generated file changes before package admission")
          _write(fixture.projectroot.resolve(fixture.claims.head.path), "object Changed\n")
          val afterchange = _admit(fixture)

          Then("path-based package admission retains the same claims for readable changed content")
          afterchange.toJson shouldBe admitted.toJson
        }
      }

      "reject removal of a claimed generated file" in {
        _with_temp_dir("cozy-sbt-bridge-claimed-removal") { directory =>
          Given("two selected delegated sources and an independent build-generated Scala file")
          val fixture = _ownership_fixture(directory, _supervisor_build_version, "object BuildVersion\n")

          When("the actual bridge publishes their aggregate and packaging admits it")
          val admitted = _publish_and_admit(fixture)

          Then("the aggregate is a valid V2 starting point")
          admitted.schemaVersion shouldBe GenerationProvenance.AGGREGATE_SCHEMA_VERSION

          When("a claimed generated file is removed before package admission")
          Files.delete(fixture.projectroot.resolve(fixture.claims.head.path))
          val error = intercept[Exception] { _admit(fixture) }

          Then("package admission reports output tampering")
          error.getMessage should include("GENERATION_PROVENANCE_OUTPUT_TAMPERED")
        }
      }

      "reject an unreadable regular claimed generated file" in {
        _with_temp_dir("cozy-sbt-bridge-claimed-unreadable") { directory =>
          Given("two selected delegated sources and an independent build-generated Scala file")
          val fixture = _ownership_fixture(directory, _supervisor_build_version, "object BuildVersion\n")

          When("the actual bridge publishes their aggregate and packaging admits it")
          val admitted = _publish_and_admit(fixture)

          Then("the aggregate is a valid V2 starting point")
          admitted.schemaVersion shouldBe GenerationProvenance.AGGREGATE_SCHEMA_VERSION

          When("a regular claimed file loses its read permission before package admission")
          val claimed = fixture.projectroot.resolve(fixture.claims.head.path)
          val permissions = Files.getPosixFilePermissions(claimed)
          val unreadable = permissions.asScala.
            filterNot(permission => permission == PosixFilePermission.OWNER_READ).
            toSet.
            asJava
          val error = try {
            Files.setPosixFilePermissions(claimed, unreadable)
            intercept[Exception] { _admit(fixture) }
          } finally {
            Files.setPosixFilePermissions(claimed, permissions)
          }

          Then("package admission reports output tampering and fixture permissions are restored")
          error.getMessage should include("GENERATION_PROVENANCE_OUTPUT_TAMPERED")
          Files.getPosixFilePermissions(claimed) shouldBe permissions
        }
      }

      "retain source identity when readable original CML content changes" in {
        _with_temp_dir("cozy-sbt-bridge-source-change") { directory =>
          Given("two selected delegated sources and an independent build-generated Scala file")
          val fixture = _ownership_fixture(directory, _supervisor_build_version, "object BuildVersion\n")

          When("the actual bridge publishes their aggregate and packaging admits it")
          val admitted = _publish_and_admit(fixture)

          Then("the aggregate is a valid V2 starting point")
          admitted.schemaVersion shouldBe GenerationProvenance.AGGREGATE_SCHEMA_VERSION

          When("the original Alpha CML changes before package admission")
          _write(fixture.projectroot.resolve("src/main/cozy/alpha.cml"), "# VALUE\n\n## Changed\n")
          val afterchange = _admit(fixture)

          Then("path-based package admission preserves the same canonical source identity")
          afterchange.toJson shouldBe admitted.toJson
        }
      }
    }
  }

  private val _supervisor_build_version =
    "target/scala-3.3.8/src_managed/main/org/simplemodeling/textus/supervisor/meta/BuildVersion.scala"

  private final case class OwnershipFixture(
    projectroot: Path,
    request: Path,
    independent: Path,
    claims: Vector[GenerationProvenance.Artifact]
  )

  private def _ownership_fixture(
    directory: Path,
    independentpath: String,
    independentcontent: String
  ): OwnershipFixture = {
    val projectroot = directory.resolve("project")
    val alpha = _write(projectroot.resolve("src/main/cozy/alpha.cml"), "# VALUE\n\n## Alpha\n")
    val beta = _write(projectroot.resolve("src/main/cozy/beta.cml"), "# VALUE\n\n## Beta\n")
    val delegated = Vector(
      _make_delegated(
        directory.resolve("delegate-alpha"), alpha, "src/main/cozy/alpha.cml",
        "target/generated/Alpha.scala" -> "object Alpha\n"
      ),
      _make_delegated(
        directory.resolve("delegate-beta"), beta, "src/main/cozy/beta.cml",
        "target/generated/Beta.scala" -> "object Beta\n"
      )
    )
    delegated.foreach(_install(projectroot, _))
    val independent = _write(projectroot.resolve(independentpath), independentcontent)
    val pairs = delegated.map { input =>
      Json.obj(
        "delegatedProvenance" -> input.provenance.toString,
        "delegatedOutputRoot" -> input.root.toString
      )
    }
    val request = _write_request(
      directory.resolve("aggregate-request.json"),
      Vector(
        "--delegated-inputs-json", Json.stringify(Json.toJson(pairs)),
        "--project-root", projectroot.toString
      )
    )
    OwnershipFixture(
      projectroot, request, independent,
      delegated.flatMap(_.manifest.artifacts).sortBy(_.path)
    )
  }

  private def _publish_and_admit(fixture: OwnershipFixture): GenerationProvenance.ValidatedManifest = {
    CozySbtBridge.execute(List("v1", "--request", fixture.request.toString))
    _admit(fixture)
  }

  private def _admit(fixture: OwnershipFixture): GenerationProvenance.ValidatedManifest =
    GenerationProvenance.requireValidForPackaging(
      provenancePath = fixture.projectroot.resolve(GenerationProvenance.METADATA_PATH),
      projectRoot = fixture.projectroot,
      expectedCncfTargetVersion = Some("0.5.2-SNAPSHOT"),
      expectedCozyGeneratorVersion = Some("0.3.2-SNAPSHOT")
    )

  private def _evidence_text(fixture: OwnershipFixture): String =
    Files.readString(fixture.projectroot.resolve(GenerationProvenance.METADATA_PATH), StandardCharsets.UTF_8)

  private def _make_delegated(
    outputroot: Path,
    source: Path,
    sourceidentity: String,
    artifact: (String, String)
  ): Delegated = {
    _write(outputroot.resolve(artifact._1), artifact._2)
    val manifest = GenerationProvenance.write(
      outputroot,
      GenerationProvenance.requireSourceInput(source, sourceidentity),
      GenerationProvenance.Inputs(
        cncfTargetVersion = "0.5.2-SNAPSHOT",
        cozyGeneratorVersion = "0.3.2-SNAPSHOT",
        simpleModelerBackendVersion = "0.5.2-SNAPSHOT",
        simpleModelingModelVersion = "0.5.2-SNAPSHOT",
        sourceIdentity = sourceidentity
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
