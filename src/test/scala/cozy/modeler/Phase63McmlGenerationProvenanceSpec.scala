package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.util.Comparator
import scala.collection.JavaConverters._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

/*
 * @since   Sep. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase63McmlGenerationProvenanceSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Phase 63 MCML generation provenance aggregation" should {
    "publish a sorted V2 union from two explicit delegated sources" in {
      _with_temp_dir("cozy-phase63-mcml-aggregate") { directory =>
        Given("two distinct CML identities with one identical shared generated artifact")
        val projectroot = directory.resolve("project")
        val alpha = _write_source(
          projectroot,
          "src/main/cozy/alpha.cml",
          "# VALUE\n\n## Alpha\n"
        )
        val beta = _write_source(
          projectroot,
          "src/main/cozy/beta.cml",
          "# VALUE\n\n## Beta\n"
        )
        val alphaoutput = _make_delegated(
          projectroot,
          projectroot.resolve("target/sbt-cozy/delegate-alpha"),
          alpha,
          "src/main/cozy/alpha.cml",
          Vector(
            "target/scala-3.3.8/src_managed/main/scala/domain/Alpha.scala" ->
              "package domain\nobject Alpha\n",
            "target/scala-3.3.8/src_managed/main/scala/domain/Shared.scala" ->
              "package domain\nobject Shared\n"
          )
        )
        val betaoutput = _make_delegated(
          projectroot,
          projectroot.resolve("target/sbt-cozy/delegate-beta"),
          beta,
          "src/main/cozy/beta.cml",
          Vector(
            "target/scala-3.3.8/src_managed/main/scala/domain/Beta.scala" ->
              "package domain\nobject Beta\n",
            "target/scala-3.3.8/src_managed/main/scala/domain/Shared.scala" ->
              "package domain\nobject Shared\n"
          )
        )
        _install(projectroot, alphaoutput)
        _install(projectroot, betaoutput)

        When("Cozy aggregates the explicitly selected delegated V1 inputs")
        val aggregate = GenerationProvenance.aggregateForPackaging(
          delegatedInputs = Vector(
            GenerationProvenance.DelegatedInput(
              alphaoutput.provenance,
              alphaoutput.root
            ),
            GenerationProvenance.DelegatedInput(
              betaoutput.provenance,
              betaoutput.root
            )
          ),
          projectRoot = projectroot
        )
        val orders = Gen.oneOf(Vector(
          Vector(_input(alphaoutput), _input(betaoutput)),
          Vector(_input(betaoutput), _input(alphaoutput))
        ))
        val property = Prop.forAll(orders) { order =>
          val candidate = GenerationProvenance.aggregateForPackaging(
            delegatedInputs = order,
            projectRoot = projectroot
          )
          candidate.sources.map(_.identity) == aggregate.sources.map(_.identity) &&
          candidate.artifacts == aggregate.artifacts
        }
        val propertyresult = Test.check(
          Test.Parameters.default.withMinSuccessfulTests(30),
          property
        )
        _delete_tree(projectroot.resolve("target/sbt-cozy"))

        Then("the V2 sources and output union are canonical, sorted, and package-admissible")
        val provenance = _provenance(projectroot)
        aggregate.schemaVersion shouldBe GenerationProvenance.AGGREGATE_SCHEMA_VERSION
        aggregate.sources.map(_.identity) shouldBe Vector(
          "src/main/cozy/alpha.cml",
          "src/main/cozy/beta.cml"
        )
        aggregate.artifacts.map(_.path) shouldBe Vector(
          "target/scala-3.3.8/src_managed/main/scala/domain/Alpha.scala",
          "target/scala-3.3.8/src_managed/main/scala/domain/Beta.scala",
          "target/scala-3.3.8/src_managed/main/scala/domain/Shared.scala"
        )
        (provenance \ "schemaVersion").as[String] shouldBe
          GenerationProvenance.AGGREGATE_SCHEMA_VERSION
        (provenance \ "sources").as[Vector[play.api.libs.json.JsObject]]
          .map(source => (source \ "identity").as[String]) shouldBe
          aggregate.sources.map(_.identity)
        propertyresult.passed shouldBe true
        GenerationProvenance.requireValidForPackaging(
          provenancePath = _provenance_path(projectroot),
          projectRoot = projectroot,
          expectedCncfTargetVersion = Some("0.5.2-SNAPSHOT"),
          expectedCozyGeneratorVersion = Some("0.3.2-SNAPSHOT")
        ) shouldBe aggregate
        GenerationProvenance.requireValidPackagedEvidence(
          provenancePath = _provenance_path(projectroot),
          expectedCncfTargetVersion = "0.5.2-SNAPSHOT",
          expectedCozyGeneratorVersion = "0.3.2-SNAPSHOT"
        ) shouldBe aggregate
      }
    }

    "preserve V1 package admission while legacy one-input rebinding publishes V2" in {
      _with_temp_dir("cozy-phase63-mcml-legacy") { directory =>
        Given("one direct V1 result and one matching delegated V1 result for one CML source")
        val projectroot = directory.resolve("project")
        val source = _write_source(
          projectroot,
          "src/main/cozy/information.cml",
          "# VALUE\n\n## Information\n"
        )
        val artifact =
          "target/scala-3.3.8/src_managed/main/scala/domain/Information.scala"
        _write(projectroot.resolve(artifact), "package domain\nobject Information\n")
        val snapshot = GenerationProvenance.requireSourceSnapshot(
          source,
          "src/main/cozy/information.cml"
        )
        val direct = GenerationProvenance.write(
          projectroot,
          snapshot,
          _inputs(source, "src/main/cozy/information.cml")
        )
        val delegated = _make_delegated(
          projectroot,
          directory.resolve("delegate-information"),
          source,
          "src/main/cozy/information.cml",
          Vector(artifact -> "package domain\nobject Information\n")
        )

        When("the unchanged legacy rebind API receives its one delegated input")
        GenerationProvenance.requireValidForPackaging(
          provenancePath = _provenance_path(projectroot),
          projectRoot = projectroot,
          expectedCncfTargetVersion = Some("0.5.2-SNAPSHOT"),
          expectedCozyGeneratorVersion = Some("0.3.2-SNAPSHOT")
        ) shouldBe direct
        val rebound = GenerationProvenance.rebindForPackaging(
          delegatedProvenancePath = delegated.provenance,
          delegatedOutputRoot = delegated.root,
          projectRoot = projectroot
        )
        _delete_tree(delegated.root)

        Then("the compatibility wrapper publishes package-valid one-source V2 evidence")
        rebound.schemaVersion shouldBe GenerationProvenance.AGGREGATE_SCHEMA_VERSION
        rebound.sources.map(_.identity) shouldBe Vector("src/main/cozy/information.cml")
        GenerationProvenance.requireValidForPackaging(
          provenancePath = _provenance_path(projectroot),
          projectRoot = projectroot,
          expectedCncfTargetVersion = Some("0.5.2-SNAPSHOT"),
          expectedCozyGeneratorVersion = Some("0.3.2-SNAPSHOT")
        ) shouldBe rebound
      }
    }

    "classify unreadable delegated V1 evidence as missing" in {
      _with_temp_dir("cozy-phase63-mcml-unreadable") { directory =>
        Given("a regular delegated V1 provenance file whose read permission is removed")
        val projectroot = directory.resolve("project")
        val source = _write_source(
          projectroot,
          "src/main/cozy/unreadable.cml",
          "# VALUE\n\n## Unreadable\n"
        )
        val delegated = _make_delegated(
          projectroot,
          directory.resolve("delegate-unreadable"),
          source,
          "src/main/cozy/unreadable.cml",
          Vector("target/generated/Unreadable.scala" -> "object Unreadable\n")
        )
        val permissions = Files.getPosixFilePermissions(delegated.provenance)
        val unreadable = permissions.asScala.
          filterNot(permission => permission == PosixFilePermission.OWNER_READ).
          toSet.
          asJava
        Files.setPosixFilePermissions(delegated.provenance, unreadable)

        When("Cozy aggregates the selected delegated input")
        val error = try {
          intercept[Exception] {
            GenerationProvenance.aggregateForPackaging(
              Vector(_input(delegated)),
              projectroot
            )
          }
        } finally {
          Files.setPosixFilePermissions(delegated.provenance, permissions)
        }

        Then("the unreadable regular evidence follows the missing-evidence diagnostic")
        error.getMessage should include("GENERATION_PROVENANCE_MISSING")
        Files.exists(_provenance_path(projectroot)) shouldBe false
      }
    }

    "admit the serialized V2 bytes only after round-trip validation" in {
      _with_temp_dir("cozy-phase63-mcml-serialized") { directory =>
        Given("a valid serialized V2 aggregate with its project source and output")
        val projectroot = directory.resolve("project")
        val source = _write_source(
          projectroot,
          "src/main/cozy/serialized.cml",
          "# VALUE\n\n## Serialized\n"
        )
        val delegated = _make_delegated(
          projectroot,
          directory.resolve("delegate-serialized"),
          source,
          "src/main/cozy/serialized.cml",
          Vector("target/generated/Serialized.scala" -> "object Serialized\n")
        )
        _install(projectroot, delegated)

        When("Cozy reads the exact serialized V2 artifact through package admission")
        val aggregate = GenerationProvenance.aggregateForPackaging(
          Vector(_input(delegated)),
          projectroot
        )
        val serialized = Files.readString(
          _provenance_path(projectroot),
          StandardCharsets.UTF_8
        )

        Then("the serialized document remains V2 and admits only its validated representation")
        Json.parse(serialized) shouldBe _provenance(projectroot)
        GenerationProvenance.requireValidForPackaging(
          provenancePath = _provenance_path(projectroot),
          projectRoot = projectroot,
          expectedCncfTargetVersion = Some("0.5.2-SNAPSHOT"),
          expectedCozyGeneratorVersion = Some("0.3.2-SNAPSHOT")
        ) shouldBe aggregate
      }
    }

    "reject every invalid aggregate boundary before V2 publication" in {
      _with_temp_dir("cozy-phase63-mcml-rejections") { directory =>
        Given("empty, missing, malformed, duplicate, stale, mismatched, contradictory, and conflicting delegated evidence")
        val emptyproject = directory.resolve("empty-project")
        val missingroot = directory.resolve("missing-project")
        Files.createDirectories(missingroot.resolve("delegate"))
        val malformedproject = directory.resolve("malformed-project")
        val malformedroot = malformedproject.resolve("delegate")
        Files.createDirectories(malformedroot)
        _write(_provenance_path(malformedroot), "{ malformed\n")
        val duplicateproject = directory.resolve("duplicate-project")
        val duplicatesource = _write_source(
          duplicateproject,
          "src/main/cozy/duplicate.cml",
          "# VALUE\n\n## Duplicate\n"
        )
        val duplicateone = _make_delegated(
          duplicateproject,
          duplicateproject.resolve("delegate-one"),
          duplicatesource,
          "src/main/cozy/duplicate.cml",
          Vector("target/generated/Duplicate.scala" -> "object Duplicate\n")
        )
        val duplicatetwo = _make_delegated(
          duplicateproject,
          duplicateproject.resolve("delegate-two"),
          duplicatesource,
          "src/main/cozy/duplicate.cml",
          Vector("target/generated/Duplicate.scala" -> "object Duplicate\n")
        )
        val staleproject = directory.resolve("stale-project")
        val stalesource = _write_source(
          staleproject,
          "src/main/cozy/stale.cml",
          "# VALUE\n\n## Stale\n"
        )
        val stale = _make_delegated(
          staleproject,
          staleproject.resolve("delegate"),
          stalesource,
          "src/main/cozy/stale.cml",
          Vector("target/generated/Stale.scala" -> "object Stale\n")
        )
        Files.writeString(stalesource, "# VALUE\n\n## Changed\n", StandardCharsets.UTF_8)
        val mismatchproject = directory.resolve("mismatch-project")
        val mismatchone = _make_delegated(
          mismatchproject,
          mismatchproject.resolve("delegate-one"),
          _write_source(mismatchproject, "src/main/cozy/one.cml", "# VALUE\n\n## One\n"),
          "src/main/cozy/one.cml",
          Vector("target/generated/One.scala" -> "object One\n")
        )
        val mismatchtwo = _make_delegated(
          mismatchproject,
          mismatchproject.resolve("delegate-two"),
          _write_source(mismatchproject, "src/main/cozy/two.cml", "# VALUE\n\n## Two\n"),
          "src/main/cozy/two.cml",
          Vector("target/generated/Two.scala" -> "object Two\n"),
          cozyversion = "0.3.3-SNAPSHOT"
        )
        val contradictoryproject = directory.resolve("contradictory-project")
        val contradictory = _make_delegated(
          contradictoryproject,
          contradictoryproject.resolve("delegate"),
          _write_source(
            contradictoryproject,
            "src/main/cozy/contradictory.cml",
            "# VALUE\n\n## Contradictory\n"
          ),
          "src/main/cozy/contradictory.cml",
          Vector("target/generated/Contradictory.scala" -> "object Contradictory\n")
        )
        val altered = _provenance(contradictory.root).as[play.api.libs.json.JsObject] ++
          Json.obj("evidenceDigest" -> ("0" * 64))
        _write(contradictory.provenance, Json.prettyPrint(altered) + "\n")
        val conflictproject = directory.resolve("conflict-project")
        val conflictone = _make_delegated(
          conflictproject,
          conflictproject.resolve("delegate-one"),
          _write_source(conflictproject, "src/main/cozy/left.cml", "# VALUE\n\n## Left\n"),
          "src/main/cozy/left.cml",
          Vector("target/generated/Shared.scala" -> "object SharedLeft\n")
        )
        val conflicttwo = _make_delegated(
          conflictproject,
          conflictproject.resolve("delegate-two"),
          _write_source(conflictproject, "src/main/cozy/right.cml", "# VALUE\n\n## Right\n"),
          "src/main/cozy/right.cml",
          Vector("target/generated/Shared.scala" -> "object SharedRight\n")
        )

        When("the aggregate API evaluates each selected invalid boundary")
        val emptyerror = intercept[Exception] {
          GenerationProvenance.aggregateForPackaging(Vector.empty, emptyproject)
        }
        val missingerror = intercept[Exception] {
          GenerationProvenance.aggregateForPackaging(
            Vector(GenerationProvenance.DelegatedInput(
              missingroot.resolve("missing.json"),
              missingroot.resolve("delegate")
            )),
            missingroot
          )
        }
        val malformederror = intercept[Exception] {
          GenerationProvenance.aggregateForPackaging(
            Vector(GenerationProvenance.DelegatedInput(
              _provenance_path(malformedroot),
              malformedroot
            )),
            malformedproject
          )
        }
        val duplicateerror = intercept[Exception] {
          GenerationProvenance.aggregateForPackaging(
            Vector(_input(duplicateone), _input(duplicatetwo)),
            duplicateproject
          )
        }
        val staleerror = intercept[Exception] {
          GenerationProvenance.aggregateForPackaging(Vector(_input(stale)), staleproject)
        }
        val mismatcherror = intercept[Exception] {
          GenerationProvenance.aggregateForPackaging(
            Vector(_input(mismatchone), _input(mismatchtwo)),
            mismatchproject
          )
        }
        val contradictoryerror = intercept[Exception] {
          GenerationProvenance.aggregateForPackaging(
            Vector(_input(contradictory)),
            contradictoryproject
          )
        }
        val conflicterror = intercept[Exception] {
          GenerationProvenance.aggregateForPackaging(
            Vector(_input(conflictone), _input(conflicttwo)),
            conflictproject
          )
        }

        Then("each failure has its typed diagnostic and none publishes a final V2 document")
        emptyerror.getMessage should include("GENERATION_PROVENANCE_INPUT_MISMATCH")
        missingerror.getMessage should include("GENERATION_PROVENANCE_MISSING")
        malformederror.getMessage should include("GENERATION_PROVENANCE_MALFORMED")
        duplicateerror.getMessage should include("GENERATION_PROVENANCE_SOURCE_AMBIGUOUS")
        staleerror.getMessage should include("GENERATION_PROVENANCE_SOURCE_TAMPERED")
        mismatcherror.getMessage should include("GENERATION_PROVENANCE_INPUT_MISMATCH")
        contradictoryerror.getMessage should include("GENERATION_PROVENANCE_EVIDENCE_TAMPERED")
        conflicterror.getMessage should include("GENERATION_PROVENANCE_OUTPUT_CONFLICT")
        Vector(
          emptyproject,
          missingroot,
          malformedproject,
          duplicateproject,
          staleproject,
          mismatchproject,
          contradictoryproject,
          conflictproject
        ).foreach { projectroot =>
          Files.exists(_provenance_path(projectroot)) shouldBe false
        }
      }
    }
  }

  private final case class Delegated(
    provenance: Path,
    root: Path,
    manifest: GenerationProvenance.Manifest
  )

  private def _make_delegated(
    projectroot: Path,
    outputroot: Path,
    source: Path,
    sourceidentity: String,
    artifacts: Vector[(String, String)],
    cncfversion: String = "0.5.2-SNAPSHOT",
    cozyversion: String = "0.3.2-SNAPSHOT"
  ): Delegated = {
    artifacts.foreach { case (path, content) =>
      _write(outputroot.resolve(path), content)
    }
    val snapshot = GenerationProvenance.requireSourceSnapshot(source, sourceidentity)
    val manifest = GenerationProvenance.write(
      outputroot,
      snapshot,
      _inputs(source, sourceidentity, cncfversion, cozyversion)
    )
    Delegated(_provenance_path(outputroot), outputroot, manifest)
  }

  private def _input(delegated: Delegated): GenerationProvenance.DelegatedInput =
    GenerationProvenance.DelegatedInput(delegated.provenance, delegated.root)

  private def _inputs(
    source: Path,
    sourceidentity: String,
    cncfversion: String = "0.5.2-SNAPSHOT",
    cozyversion: String = "0.3.2-SNAPSHOT"
  ): GenerationProvenance.Inputs =
    GenerationProvenance.Inputs(
      cncfTargetVersion = cncfversion,
      runtimeDescriptorSha256 = "a" * 64,
      cozyGeneratorVersion = cozyversion,
      simpleModelerBackendVersion = "0.5.2-SNAPSHOT",
      simpleModelingModelVersion = "0.5.2-SNAPSHOT",
      sourceIdentity = sourceidentity,
      sourceSha256 = _sha256(source)
    )

  private def _install(projectroot: Path, delegated: Delegated): Unit =
    delegated.manifest.artifacts.foreach { artifact =>
      val source = delegated.root.resolve(artifact.path)
      _write(
        projectroot.resolve(artifact.path),
        Files.readString(source, StandardCharsets.UTF_8)
      )
    }

  private def _write_source(
    projectroot: Path,
    identity: String,
    content: String
  ): Path =
    _write(projectroot.resolve(identity), content)

  private def _provenance(projectroot: Path) =
    Json.parse(Files.readString(_provenance_path(projectroot), StandardCharsets.UTF_8))

  private def _provenance_path(projectroot: Path): Path =
    projectroot.resolve(GenerationProvenance.METADATA_PATH)

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
    val directory = Files.createTempDirectory(prefix)
    try body(directory)
    finally _delete_tree(directory)
  }

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try
        stream
          .sorted(Comparator.reverseOrder())
          .iterator()
          .asScala
          .foreach(Files.deleteIfExists(_))
      finally stream.close()
    }
}
