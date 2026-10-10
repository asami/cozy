package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.nio.file.attribute.PosixFilePermission
import java.util.Comparator
import scala.collection.JavaConverters._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsBoolean, JsNull, JsNumber, JsObject, JsString, JsValue, Json}

/*
 * @since   Sep. 16, 2026
 * @version Oct.  9, 2026
 * @author  ASAMI, Tomoharu
 */
object Phase63McmlGenerationProvenanceSpec {
  private final case class Delegated(
    provenance: Path,
    root: Path,
    manifest: GenerationProvenance.Manifest
  )
}

final class Phase63McmlGenerationProvenanceSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  import Phase63McmlGenerationProvenanceSpec.Delegated

  "Phase 63 MCML generation provenance aggregation" should {
    "explicit source aggregation" which {
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

          val provenance = _provenance(projectroot)
          val admitted = GenerationProvenance.requireValidForPackaging(
            provenancePath = _provenance_path(projectroot), projectRoot = projectroot,
            expectedCncfTargetVersion = Some("0.5.2-SNAPSHOT"),
            expectedCozyGeneratorVersion = Some("0.3.2-SNAPSHOT")
          )
          val packaged = GenerationProvenance.requireValidPackagedMetadata(
            provenancePath = _provenance_path(projectroot),
            expectedCncfTargetVersion = "0.5.2-SNAPSHOT",
            expectedCozyGeneratorVersion = "0.3.2-SNAPSHOT"
          )

          Then("the V2 sources and output union are canonical, sorted, and package-admissible")
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
          admitted shouldBe aggregate
          packaged shouldBe aggregate
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
          val sourceinput = GenerationProvenance.requireSourceInput(
            source,
            "src/main/cozy/information.cml"
          )
          val direct = GenerationProvenance.write(
            projectroot,
            sourceinput,
            _inputs("src/main/cozy/information.cml")
          )
          val delegated = _make_delegated(
            projectroot,
            directory.resolve("delegate-information"),
            source,
            "src/main/cozy/information.cml",
            Vector(artifact -> "package domain\nobject Information\n")
          )

          When("the unchanged legacy rebind API receives its one delegated input")
          val admittedv1 = GenerationProvenance.requireValidForPackaging(
            provenancePath = _provenance_path(projectroot),
            projectRoot = projectroot,
            expectedCncfTargetVersion = Some("0.5.2-SNAPSHOT"),
            expectedCozyGeneratorVersion = Some("0.3.2-SNAPSHOT")
          )
          val rebound = GenerationProvenance.rebindForPackaging(
            delegatedProvenancePath = delegated.provenance,
            delegatedOutputRoot = delegated.root,
            projectRoot = projectroot
          )
          _delete_tree(delegated.root)

          val admittedv2 = GenerationProvenance.requireValidForPackaging(
            provenancePath = _provenance_path(projectroot), projectRoot = projectroot,
            expectedCncfTargetVersion = Some("0.5.2-SNAPSHOT"),
            expectedCozyGeneratorVersion = Some("0.3.2-SNAPSHOT")
          )

          Then("the compatibility wrapper publishes package-valid one-source V2 metadata")
          admittedv1 shouldBe direct
          rebound.schemaVersion shouldBe GenerationProvenance.AGGREGATE_SCHEMA_VERSION
          rebound.sources.map(_.identity) shouldBe Vector("src/main/cozy/information.cml")
          admittedv2 shouldBe rebound
        }
      }

    }

    "stage validation and metadata preservation" which {
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

          val published = Files.exists(_provenance_path(projectroot))

          Then("the unreadable regular evidence follows the missing-evidence diagnostic")
          error.getMessage should include("GENERATION_PROVENANCE_MISSING")
          published shouldBe false
        }
    }

      "reject missing and unreadable delegated output roots without replacing previous metadata" in {
        _with_temp_dir("cozy-phase63-mcml-output-access") { directory =>
          Given("a selected regular manifest and a delegated output root with removable read access")
          val projectroot = directory.resolve("project")
          val source = _write_source(projectroot, "src/model.cml", "# VALUE\n\n## Model\n")
          val delegated = _make_delegated(projectroot, directory.resolve("delegate"), source, "src/model.cml",
            Vector("target/generated/Model.scala" -> "object Model\n"))
          _write(_provenance_path(projectroot), "previous successful metadata\n")
          val permissions = Files.getPosixFilePermissions(delegated.root)
          val unreadable = permissions.asScala.filterNot(permission => Set(
            PosixFilePermission.OWNER_READ, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ
          ).contains(permission)).toSet.asJava

          When("the aggregate stage receives a missing root and then the selected unreadable root")
          val missingerror = intercept[Exception] {
            GenerationProvenance.aggregateForPackaging(Vector(GenerationProvenance.DelegatedInput(
              delegated.provenance, directory.resolve("missing-root")
            )), projectroot)
          }
          Files.setPosixFilePermissions(delegated.root, unreadable)
          val unreadableerror = try intercept[Exception] {
            GenerationProvenance.aggregateForPackaging(Vector(_input(delegated)), projectroot)
          } finally Files.setPosixFilePermissions(delegated.root, permissions)
          val current = Files.readString(_provenance_path(projectroot), StandardCharsets.UTF_8)

          Then("both actual root failures keep their missing-evidence diagnostic and preserve the existing document")
          missingerror.getMessage should include("GENERATION_PROVENANCE_MISSING")
          unreadableerror.getMessage should include("GENERATION_PROVENANCE_MISSING")
          current shouldBe "previous successful metadata\n"
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

          val loaded = _provenance(projectroot)
          val admitted = GenerationProvenance.requireValidForPackaging(
            provenancePath = _provenance_path(projectroot), projectRoot = projectroot,
            expectedCncfTargetVersion = Some("0.5.2-SNAPSHOT"),
            expectedCozyGeneratorVersion = Some("0.3.2-SNAPSHOT")
          )

          Then("the serialized document retains exactly the known V2 projection")
          Json.parse(serialized) shouldBe loaded
          loaded shouldBe aggregate.toJson
          loaded.as[JsObject].keys shouldBe Set("schemaVersion", "target", "generator", "sources", "output")
          admitted shouldBe aggregate
        }
      }

      "reject every invalid aggregate boundary before writing final V2 metadata" in {
        _with_temp_dir("cozy-phase63-mcml-rejections") { directory =>
          Given("empty, missing, malformed, duplicate, unavailable-source, mismatched, malformed-known-field, and missing-output delegated inputs")
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
          Files.delete(stalesource)
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
            Json.obj("target" -> Json.obj("cncfVersion" -> false))
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

          Files.delete(conflicttwo.root.resolve("target/generated/Shared.scala"))
          val projects = Vector(emptyproject, missingroot, malformedproject, duplicateproject,
            staleproject, mismatchproject, contradictoryproject, conflictproject)
          projects.foreach(root => _write(_provenance_path(root), "previous successful metadata\n"))
          val previous = projects.map(root => Files.readString(_provenance_path(root), StandardCharsets.UTF_8))

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

          val current = projects.map(root => Files.readString(_provenance_path(root), StandardCharsets.UTF_8))
          Then("each genuine stage rejection precedes the final write and previous metadata remains unchanged")
          emptyerror.getMessage should include("GENERATION_PROVENANCE_INPUT_MISMATCH")
          missingerror.getMessage should include("GENERATION_PROVENANCE_MISSING")
          malformederror.getMessage should include("GENERATION_PROVENANCE_MALFORMED")
          duplicateerror.getMessage should include("GENERATION_PROVENANCE_SOURCE_AMBIGUOUS")
          staleerror.getMessage should include("GENERATION_PROVENANCE_SOURCE_TAMPERED")
          mismatcherror.getMessage should include("GENERATION_PROVENANCE_INPUT_MISMATCH")
          contradictoryerror.getMessage should include("GENERATION_PROVENANCE_MALFORMED")
          conflicterror.getMessage should include("GENERATION_PROVENANCE_OUTPUT_TAMPERED")
          current shouldBe previous
        }
      }

    }

    "content independence and historical metadata projection" which {
      "deduplicate shared paths independently of differing readable source and generated contents" in {
        _with_temp_dir("cozy-phase63-mcml-shared-path") { directory =>
          Given("two distinct delegated sources claim one shared Scala path with different readable contents")
          val projectroot = directory.resolve("project")
          val shared = "target/generated/Shared.scala"
          val leftsource = _write_source(projectroot, "src/left.cml", "# VALUE\n\n## Left\n")
          val rightsource = _write_source(projectroot, "src/right.cml", "# VALUE\n\n## Right\n")
          val left = _make_delegated(projectroot, directory.resolve("left"), leftsource, "src/left.cml",
            Vector(shared -> "object SharedLeft\n"))
          val right = _make_delegated(projectroot, directory.resolve("right"), rightsource, "src/right.cml",
            Vector(shared -> "object SharedRight\n"))
          _write(projectroot.resolve(shared), "object InstalledShared\n")

          When("both readable sources change and aggregation receives each explicit pair in both orders")
          _write(leftsource, "# VALUE\n\n## ChangedLeft\n")
          _write(right.root.resolve(shared), "object ChangedRight\n")
          val forward = GenerationProvenance.aggregateForPackaging(Vector(_input(left), _input(right)), projectroot)
          val reverse = GenerationProvenance.aggregateForPackaging(Vector(_input(right), _input(left)), projectroot)
          val admitted = GenerationProvenance.requireValidForPackaging(_provenance_path(projectroot), projectroot,
            Some("0.5.2-SNAPSHOT"), Some("0.3.2-SNAPSHOT"))
          val stream = Files.walk(projectroot.resolve("target"))
          val installed = try stream.iterator().asScala.filter(path =>
            Files.isRegularFile(path) && path.getFileName.toString.endsWith(".scala")
          ).map(projectroot.relativize(_).toString.replace(java.io.File.separatorChar, '/')).toVector.sorted
          finally stream.close()

          Then("the ordered union claims the single installed path without selecting any source contents")
          forward shouldBe reverse
          forward.sources.map(_.identity) shouldBe Vector("src/left.cml", "src/right.cml")
          forward.sources.map(_.artifacts) shouldBe Vector.fill(2)(Vector(GenerationProvenance.Artifact(shared)))
          forward.artifacts shouldBe Vector(GenerationProvenance.Artifact(shared))
          installed shouldBe Vector(shared)
          admitted shouldBe forward
        }
    }

      "ignore unrelated target Scala files while admitting the selected installed artifact union" in {
        _with_temp_dir("cozy-phase63-mcml-unrelated-output") { directory =>
          Given("one selected installed generation artifact and unrelated retained Scala fixture outputs")
          val projectroot = directory.resolve("project")
          val source = _write_source(projectroot, "src/model.cml", "# VALUE\n\n## Model\n")
          val artifact = "target/generated/Model.scala"
          val delegated = _make_delegated(projectroot, directory.resolve("delegate"), source, "src/model.cml",
            Vector(artifact -> "object Model\n"))
          _install(projectroot, delegated)
          val unrelated = _write(projectroot.resolve("target/test-work/repository/one.scala"), "object TestOwned\n")
          val other = _write(projectroot.resolve("target/other-task/Other.scala"), "object OtherTask\n")

          When("Cozy aggregates selected delegation and independently validates installed packaging metadata")
          val aggregate = GenerationProvenance.aggregateForPackaging(Vector(_input(delegated)), projectroot)
          val admitted = GenerationProvenance.requireValidForPackaging(_provenance_path(projectroot), projectroot,
            Some("0.5.2-SNAPSHOT"), Some("0.3.2-SNAPSHOT"))

          Then("only declared generation paths are attributed and unrelated files remain untouched")
          aggregate.artifacts shouldBe Vector(GenerationProvenance.Artifact(artifact))
          admitted shouldBe aggregate
          Files.readString(unrelated, StandardCharsets.UTF_8) shouldBe "object TestOwned\n"
          Files.readString(other, StandardCharsets.UTF_8) shouldBe "object OtherTask\n"
        }
      }

      "reject an incomplete installed output inventory before writing final aggregate metadata" in {
        _with_temp_dir("cozy-phase63-mcml-installed-failure") { directory =>
          Given("a valid delegated input and previous aggregate metadata for its installed output")
          val projectroot = directory.resolve("project")
          val source = _write_source(projectroot, "src/model.cml", "# VALUE\n\n## Model\n")
          val artifact = "target/generated/Model.scala"
          val delegated = _make_delegated(projectroot, directory.resolve("delegate"), source, "src/model.cml",
            Vector(artifact -> "object Model\n"))
          _install(projectroot, delegated)
          When("Cozy writes the initial successful installed-output metadata")
          GenerationProvenance.aggregateForPackaging(Vector(_input(delegated)), projectroot)
          val previous = Files.readString(_provenance_path(projectroot), StandardCharsets.UTF_8)

          Given("the actual installed output path is now missing")
          Files.delete(projectroot.resolve(artifact))
          When("the next aggregation validates its installed-output inventory before writing metadata")
          val error = intercept[Exception] {
            GenerationProvenance.aggregateForPackaging(Vector(_input(delegated)), projectroot)
          }
          val current = Files.readString(_provenance_path(projectroot), StandardCharsets.UTF_8)
          Then("the path rejection precedes the final write and prior successful metadata remains unchanged")
          error.getMessage should include("GENERATION_PROVENANCE_OUTPUT_TAMPERED")
          current shouldBe previous
        }
      }

      "project historical V2 extras without source access and reject known-field and union contradictions" in {
        _with_temp_dir("cozy-phase63-mcml-extras") { directory =>
          Given("one valid aggregate, historical nonscalar extras, and arbitrary safe source identities")
          val projectroot = directory.resolve("project")
          val source = _write_source(projectroot, "src/model.cml", "# VALUE\n\n## Model\n")
          val delegated = _make_delegated(projectroot, directory.resolve("delegate"), source, "src/model.cml",
            Vector("target/generated/Model.scala" -> "object Model\n"))
          _install(projectroot, delegated)
          When("the aggregate producer writes the known V2 projection")
          val aggregate = GenerationProvenance.aggregateForPackaging(Vector(_input(delegated)), projectroot)
          val document = aggregate.toJson
          val historical = _with_extras(_with_extras(document, "sha256", Json.obj("old" -> Json.arr(1, false))),
            "digest", Json.arr("retired", false)) ++
            Json.obj("evidenceDigest" -> Json.arr(true, Json.obj("old" -> 3)))
          val names = Gen.nonEmptyListOf(Gen.alphaLowerChar).map(chars => "extra_" + chars.mkString)
          val values: Gen[JsValue] = Gen.oneOf(
            Gen.alphaStr.map(JsString.apply), Gen.oneOf(true, false).map(JsBoolean.apply),
            Gen.choose(-100, 100).map(number => JsNumber(BigDecimal(number))), Gen.const(JsNull),
            Gen.alphaStr.map(value => Json.arr(Json.obj("nested" -> value), false))
          )
          val identities = Gen.nonEmptyListOf(Gen.alphaLowerChar).map(chars => s"src/${chars.mkString}.cml")
          val property = Prop.forAll(names, values, identities) { (name, value, sourceidentity) =>
            val current = aggregate.copy(sources = aggregate.sources.map(_.copy(identity = sourceidentity)))
            _write(_provenance_path(projectroot), Json.prettyPrint(_with_extras(current.toJson, name, value)))
            val loaded = GenerationProvenance.requireValidPackagedMetadata(_provenance_path(projectroot),
              "0.5.2-SNAPSHOT", "0.3.2-SNAPSHOT")
            loaded == current && loaded.toJson == current.toJson
          }
          val invalid = Vector(
            document - "generator", document ++ Json.obj("target" -> Json.obj("cncfVersion" -> false)),
            document ++ Json.obj("sources" -> Json.arr(4)),
            document ++ Json.obj("sources" -> Json.arr(Json.obj("identity" -> "src/model.cml", "output" -> true))),
            document ++ Json.obj("schemaVersion" -> "unsupported"), document - "schemaVersion",
            document ++ Json.obj("output" -> Json.obj("artifacts" -> Json.arr(Json.obj("path" -> "target/generated/Other.scala"))))
          )

          When("packaged metadata is loaded with no local source and twenty arbitrary extras are projected")
          Files.delete(source)
          _delete_tree(delegated.root)
          _write(_provenance_path(projectroot), Json.prettyPrint(historical))
          val loaded = GenerationProvenance.requireValidPackagedMetadata(_provenance_path(projectroot),
            "0.5.2-SNAPSHOT", "0.3.2-SNAPSHOT")
          val invaliderrors = invalid.map { variant =>
            _write(_provenance_path(projectroot), Json.prettyPrint(variant))
            intercept[Exception] {
              GenerationProvenance.requireValidPackagedMetadata(_provenance_path(projectroot),
                "0.5.2-SNAPSHOT", "0.3.2-SNAPSHOT")
            }
          }
          val propertyresult = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)

          Then("unknown extras disappear from the typed projection while required facts and source-output union remain strict")
          loaded shouldBe aggregate
          loaded.toJson shouldBe document
          invaliderrors.take(4).foreach(_.getMessage should include("GENERATION_PROVENANCE_MALFORMED"))
          invaliderrors(4).getMessage should include("GENERATION_PROVENANCE_SCHEMA_MISMATCH")
          invaliderrors(5).getMessage should include("GENERATION_PROVENANCE_SCHEMA_MISMATCH")
          invaliderrors(6).getMessage should include("GENERATION_PROVENANCE_OUTPUT_TAMPERED")
          propertyresult.passed shouldBe true
        }
      }
    }
  }

  private def _with_extras(document: JsObject, name: String, value: JsValue): JsObject = {
    val extra = Json.obj(name -> value)
    def _output_(output: JsObject): JsObject =
      output ++ extra ++ Json.obj("artifacts" -> (output \ "artifacts").as[Vector[JsObject]].map(_ ++ extra))
    val sources = (document \ "sources").as[Vector[JsObject]].map { source =>
      source ++ extra ++ Json.obj("output" -> _output_((source \ "output").as[JsObject]))
    }
    document ++ extra ++ Json.obj(
      "target" -> ((document \ "target").as[JsObject] ++ extra),
      "generator" -> ((document \ "generator").as[JsObject] ++ extra),
      "sources" -> sources,
      "output" -> _output_((document \ "output").as[JsObject])
    )
  }

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
    val sourceinput = GenerationProvenance.requireSourceInput(source, sourceidentity)
    val manifest = GenerationProvenance.write(
      outputroot,
      sourceinput,
      _inputs(sourceidentity, cncfversion, cozyversion)
    )
    Delegated(_provenance_path(outputroot), outputroot, manifest)
  }

  private def _input(delegated: Delegated): GenerationProvenance.DelegatedInput =
    GenerationProvenance.DelegatedInput(delegated.provenance, delegated.root)

  private def _inputs(
    sourceidentity: String,
    cncfversion: String = "0.5.2-SNAPSHOT",
    cozyversion: String = "0.3.2-SNAPSHOT"
  ): GenerationProvenance.Inputs =
    GenerationProvenance.Inputs(
      cncfTargetVersion = cncfversion,
      cozyGeneratorVersion = cozyversion,
      simpleModelerBackendVersion = "0.5.2-SNAPSHOT",
      simpleModelingModelVersion = "0.5.2-SNAPSHOT",
      sourceIdentity = sourceidentity
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

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val workroot = Path.of("target/cozy-test/work/phase63-mcml-generation-provenance-spec").toAbsolutePath.normalize()
    Files.createDirectories(workroot)
    val directory = Files.createTempDirectory(workroot, s"$prefix-")
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
