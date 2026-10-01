package cozy.publication

import java.io.{ByteArrayOutputStream, PrintStream}
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import java.security.MessageDigest
import cozy.document.{CozyDocumentProject, CozyDocumentProjectEvidence, CozyDocumentProjectExport, CozyDocumentWorkflow}
import cozy.media.{CozyExplanation, CozyMedia, CozyMediaDispatcher, CozyVisualPage}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.smartdox.metadata.PublishMetadata.ImageReference
import scala.collection.JavaConverters._

/*
 * @since   Oct.  1, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentProjectPublicationPreparationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CozyDocumentProjectPublicationPreparation" should {
    "consume the real native command sequence" which {
      "prepare the accepted Article review export while unrelated required PDF remains blocked" in {
        Given("a real selected standard project with complete authored presentation semantics, original media authorities and no accepted run or export")
        _with_fixture("native-sequence", accepted = false, structural = true) { fixture =>
          val destination = fixture.taskroot.resolve("prepared")

          When("native Article review runs, structural verification records its snapshot, and public export completes")
          val run = _execute_output(List("document-project", "run", fixture.project.toString, "--operation", "article.render-review"))
          val verification = _execute_output(List("document-project", "verify", fixture.project.toString, "--mode", "structural"))
          val exported = _execute_output(_export_args(fixture))
          val snapshot = CozyDocumentProjectEvidence.snapshot(fixture.project, CozyDocumentProject._load_project(fixture.project))
          val review = snapshot.products.find(_.value.workProduct.id == "article-review-html").get
          val pdf = snapshot.products.find(_.value.workProduct.id == "article-pdf").get
          val pdfoperation = snapshot.nativeOperations.find(_.operation.id == "article.render-pdf").get
          val evidence = CozyDocumentProjectExport.verifyBundle(fixture.bundle)
          val before = _authorities(fixture)

          Then("native acceptance and export identify the current selected Article review without requiring PDF completion")
          run should include("outcome: accepted")
          run should include("evidence: accepted")
          run should include("currentness: current")
          run should include("identity: article-review-html")
          verification should include("mode: structural")
          verification should include("state: target/document-project/state.yaml")
          exported should include("work-product: article-review-html")
          snapshot.attempts.map(_.outcome) shouldBe Vector("accepted")
          review.value.selection shouldBe CozyDocumentWorkflow.WorkProductSelection.ActiveOptional
          review.currentness shouldBe "current"
          pdf.value.binding.disposition shouldBe CozyDocumentWorkflow.WorkProductDisposition.Required
          pdf.value.selection shouldBe CozyDocumentWorkflow.WorkProductSelection.Required
          pdf.readiness shouldBe "blocked"
          pdfoperation.providerAvailability shouldBe CozyDocumentWorkflow.NativeProviderAvailability.Unavailable
          pdfoperation.immediateExecutability shouldBe CozyDocumentWorkflow.NativeImmediateExecutability.Blocked

          When("the actual media dispatcher prepares the current export into the absent task child")
          val dispatched = _dispatch(_preparation_args(fixture, destination))

          Then("PREPARED names verified installed export and registry paths and preserves every original authority")
          dispatched._1 shouldBe true
          dispatched._2 should include("status: PREPARED")
          dispatched._2 should include(s"root: $destination")
          dispatched._2 should include(s"export: ${destination.resolve("export")}")
          dispatched._2 should include(s"  - ${destination.resolve("article-media.json")}")
          CozyDocumentProjectExport.verifyBundle(destination.resolve("export")) shouldBe evidence
          CozyDocumentProjectPublicationTarget.admit(destination.resolve("export")).evidence shouldBe evidence
          _require_registry(destination, CozyDocumentProjectPublicationTarget.planCurrentRegistration(
            _config(fixture, destination, paired = true).registration).registration.siteBinding)
          _authorities(fixture) shouldBe before
          _children(fixture.taskroot) shouldBe Vector(destination)
        }
      }

      "retain the missing PDF provider and required Work Product causes without preparation writes" in {
        Given("a selected standard project with no accepted attempt, native review output or export")
        _with_fixture("unavailable", accepted = false) { fixture =>
          val destination = fixture.taskroot.resolve("prepared")
          val config = _config(fixture, destination, paired = true)
          val before = _tree(fixture.root)

          When("the native PDF operation and read-only evidence producer expose unavailable capability")
          val blocked = _execute_output(List("document-project", "run", fixture.project.toString, "--operation", "article.render-pdf"))
          val snapshot = CozyDocumentProjectEvidence.snapshot(fixture.project, CozyDocumentProject._load_project(fixture.project))
          val pdf = snapshot.products.find(_.value.workProduct.id == "article-pdf").get
          val operation = snapshot.nativeOperations.find(_.operation.id == "article.render-pdf").get
          val exportfailure = _capture_failure { _execute(_export_args(fixture)) }
          val producer = intercept[RuntimeException] { CozyDocumentProjectPublicationTarget.planCurrentRegistration(config.registration) }
          val api = intercept[RuntimeException] { CozyDocumentProjectPublicationPreparation.prepare(config) }
          val cli = _dispatch_failure(_preparation_args(fixture, destination))

          Then("the native blocked reason survives and missing accepted evidence and bundle reject export and preparation")
          blocked should include("operation: article.render-pdf")
          blocked should include("provider-binding: smartdox-rendering")
          blocked should include("outcome: blocked")
          blocked should include("missing-capability: native typed provider execution is unavailable")
          blocked should include("evidence: none")
          pdf.value.binding.disposition shouldBe CozyDocumentWorkflow.WorkProductDisposition.Required
          pdf.value.selection shouldBe CozyDocumentWorkflow.WorkProductSelection.Required
          pdf.readiness shouldBe "blocked"
          pdf.reason should not be empty
          operation.operation.providerBinding shouldBe "smartdox-rendering"
          operation.providerAvailability shouldBe CozyDocumentWorkflow.NativeProviderAvailability.Unavailable
          operation.immediateExecutability shouldBe CozyDocumentWorkflow.NativeImmediateExecutability.Blocked
          snapshot.attempts shouldBe empty
          exportfailure._1.getMessage should include("DP-OP-001")
          exportfailure._1.getMessage should include("export requires current accepted article.render-review native evidence")
          api.getMessage shouldBe producer.getMessage
          cli._1.getMessage shouldBe producer.getMessage
          exportfailure._2 should not include "status: PREPARED"
          cli._2 should not include "status: PREPARED"
          _require_unexecuted(fixture)
          _require_no_write(fixture, destination, before)
        }
      }

      "preserve structural state while dry-run leaves production evidence unresolved" in {
        Given("a selected real project with complete authored presentation semantics and no accepted attempt, native review output or public export")
        _with_fixture("dry-run", accepted = false, structural = true) { fixture =>
          val destination = fixture.taskroot.resolve("prepared")
          val config = _config(fixture, destination, paired = true)

          When("native structural verification writes its legitimate derived state")
          val verification = _execute_output(List("document-project", "verify", fixture.project.toString, "--mode", "structural"))

          Then("the structural snapshot exists without accepted native production evidence")
          verification should include("mode: structural")
          val state = fixture.project.resolve("target/document-project/state.yaml")
          Files.isRegularFile(state, LinkOption.NOFOLLOW_LINKS) shouldBe true
          _require_unexecuted(fixture)

          Given("the complete baseline after structural verification and its original derived state bytes")
          val before = _tree(fixture.root)
          val statebytes = Files.readAllBytes(state).toVector

          When("native dry-run resolves Article review and export and preparation are requested without accepted evidence")
          val dryrun = _execute_output(List("document-project", "run", fixture.project.toString, "--operation", "article.render-review", "--dry-run"))
          val exportfailure = _capture_failure { _execute(_export_args(fixture)) }
          val producer = intercept[RuntimeException] { CozyDocumentProjectPublicationTarget.planCurrentRegistration(config.registration) }
          val api = intercept[RuntimeException] { CozyDocumentProjectPublicationPreparation.prepare(config) }
          val cli = _dispatch_failure(_preparation_args(fixture, destination))

          Then("resolution remains pending without writes or PREPARED and the producer rejection is exact")
          dryrun should include("operation: article.render-review")
          dryrun should include("provider-binding: cozy-review-projection")
          dryrun should include("outcome: resolved")
          dryrun should include("identity: article-review-html")
          dryrun should include("receipt: pending")
          dryrun should include("evidence: none")
          exportfailure._1.getMessage should include("export requires current accepted article.render-review native evidence")
          producer.getMessage should include("DP-OP-001")
          api.getMessage shouldBe producer.getMessage
          cli._1.getMessage shouldBe producer.getMessage
          cli._2 should not include "status: PREPARED"
          Files.readAllBytes(state).toVector shouldBe statebytes
          _require_unexecuted(fixture)
          _require_no_write(fixture, destination, before)
        }
      }

      "retain native admission throws for unknown disabled and missing-source operations" in {
        val cases = Vector(
          ("unknown", "render", "DP-OP-001", "undeclared logical operation: render"),
          ("disabled", "video.render-review", "DP-OP-001", "logical operation video.render-review is disabled for profile standard"),
          ("missing-source", "article.render-review", "DP-PATH-001", "initial authored source must be a direct regular non-symlink file")
        )
        cases.foreach { case (name, operation, code, diagnostic) =>
          Given(s"an independent unexecuted selected project for $name native admission")
          _with_fixture(name, accepted = false) { fixture =>
            if (name == "missing-source") Files.delete(fixture.project.resolve("index.dox"))
            val destination = fixture.taskroot.resolve("prepared")
            val before = _tree(fixture.root)

            When("the actual native run admits the requested logical operation and original authored source")
            val failure = _capture_failure {
              _execute(List("document-project", "run", fixture.project.toString, "--operation", operation))
            }

            Then("the unchanged admission diagnostic throws before any accepted attempt or client success")
            failure._1.getMessage should include(code)
            failure._1.getMessage should include(diagnostic)
            failure._2 should not include "outcome: accepted"
            failure._2 should not include "status: PREPARED"
            _require_unexecuted(fixture)
            _require_no_write(fixture, destination, before)
          }
        }
      }
    }

    "propagate live and portable producer failures" which {
      "preserve each stale facet through API and real dispatch while portable admission succeeds" in {
        val cases: Vector[(String, String, Fixture => Unit)] = Vector(
          ("source", "sourceauthority=stale", fixture => _write(fixture.project.resolve("index.dox"), "Changed source\n")),
          ("selection", "selection=stale", fixture => {
            val path = fixture.project.resolve("document-project.yaml")
            _write(path, Files.readString(path, StandardCharsets.UTF_8).replace(
              "activeOptionalWorkProducts:\n  - article-review-html", "activeOptionalWorkProducts: []"))
          }),
          ("receipt", "retainedproductionevidence=stale", fixture => {
            val path = _children(fixture.project.resolve("evidence/attempts")).head
            _write(path, Files.readString(path, StandardCharsets.UTF_8).replace(
              "cozy.document-project.native-receipt.v1", "changed-receipt"))
          }),
          ("missing-attempt", "retainedproductionevidence=stale", fixture => {
            Files.delete(_children(fixture.project.resolve("evidence/attempts")).head)
            ()
          })
        )
        cases.foreach { case (name, diagnostic, mutate) =>
          Given(s"genuine accepted and exported authorities followed by an independent $name mutation and sibling sentinel")
          _with_fixture(s"stale-$name") { fixture =>
            val destination = fixture.taskroot.resolve("prepared")
            val config = _config(fixture, destination, paired = true)
            val original = CozyDocumentProjectPublicationTarget.planCurrentRegistration(config.registration)
            _write(fixture.taskroot.resolve("existing/sentinel"), "keep existing destination bytes\n")
            mutate(fixture)
            val before = _tree(fixture.root)

            When("portable admission and the fresh original producer precede preparation API and actual dispatch")
            val portable = CozyDocumentProjectPublicationTarget.admit(fixture.bundle)
            val producer = intercept[RuntimeException] { CozyDocumentProjectPublicationTarget.planCurrentRegistration(config.registration) }
            val api = intercept[RuntimeException] { CozyDocumentProjectPublicationPreparation.prepare(config) }
            val cli = _dispatch_failure(_preparation_args(fixture, destination))

            Then("both client failures equal the exact live stale producer diagnostic and preserve the mutated baseline")
            portable shouldBe original.registration.export
            producer.getMessage should include("DP-OP-001")
            producer.getMessage should include(diagnostic)
            api.getMessage shouldBe producer.getMessage
            cli._1.getMessage shouldBe producer.getMessage
            cli._2 should not include "status: PREPARED"
            _require_no_write(fixture, destination, before)
          }
        }
      }

      "reject partial private tampered and unreceipted exports with exact producer diagnostics" in {
        val cases: Vector[(String, Fixture => Unit)] = Vector(
          ("malformed-manifest", fixture => _write(fixture.bundle.resolve("manifest.yaml"), "damaged manifest\n")),
          ("changed-html", fixture => _write(fixture.bundle.resolve("work-products/article-review-html/article-review.html"), "damaged output\n")),
          ("missing-manifest", fixture => { Files.delete(fixture.bundle.resolve("manifest.yaml")); () }),
          ("missing-receipt", fixture => { Files.delete(fixture.bundle.resolve("receipt.yaml")); () }),
          ("missing-html", fixture => { Files.delete(fixture.bundle.resolve("work-products/article-review-html/article-review.html")); () }),
          ("private-entry", fixture => _write(fixture.bundle.resolve("work-products/private.yaml"), "private: preserved\n"))
        )
        cases.foreach { case (name, mutate) =>
          Given(s"a genuine current native export with $name damage and an existing sibling destination sentinel")
          _with_fixture(s"bundle-$name") { fixture =>
            val destination = fixture.taskroot.resolve("prepared")
            val config = _config(fixture, destination, paired = true)
            CozyDocumentProjectPublicationTarget.planCurrentRegistration(config.registration)
            _write(fixture.taskroot.resolve("existing/sentinel"), "keep existing destination bytes\n")
            mutate(fixture)
            val before = _tree(fixture.root)

            When("the unchanged original producer and both actual preparation entry points consume the damaged bundle")
            val producer = intercept[RuntimeException] { CozyDocumentProjectPublicationTarget.planCurrentRegistration(config.registration) }
            val api = intercept[RuntimeException] { CozyDocumentProjectPublicationPreparation.prepare(config) }
            val cli = _dispatch_failure(_preparation_args(fixture, destination))

            Then("exact portable rejection prevents all copied private or partial output and preserves sentinel bytes")
            producer.getMessage should include("DP-OP-001")
            api.getMessage shouldBe producer.getMessage
            cli._1.getMessage shouldBe producer.getMessage
            cli._2 should not include "status: PREPARED"
            _require_no_write(fixture, destination, before)
          }
        }
      }

      "reject missing duplicate unsupported and unpaired options through the actual dispatcher" in {
        Given("a genuine current native fixture, absent preparation destination and an existing sibling sentinel")
        _with_fixture("cli-rejection") { fixture =>
          val destination = fixture.taskroot.resolve("prepared")
          _write(fixture.taskroot.resolve("existing/sentinel"), "keep existing destination bytes\n")
          val args = _preparation_args(fixture, destination).drop(2)
          val cases = Vector(
            args.patch(args.indexOf("--task-root"), Nil, 2),
            args ++ List("--save", destination.toString),
            args ++ List("--profile", "standard"),
            args :+ "--dry-run",
            args.patch(args.indexOf("--site-config"), Nil, 2)
          )
          val before = _tree(fixture.root)
          cases.foreach { arguments =>
            When("the real dispatcher receives the same rejected arguments as unchanged native Config.create")
            val parser = intercept[RuntimeException] { CozyDocumentProjectPublicationPreparationCommand.Config.create(arguments) }
            val cli = _dispatch_failure(List("media", "prepare-publication") ++ arguments)

            Then("the exact native parser failure escapes without PREPARED or any granted write effect")
            cli._1.getMessage shouldBe parser.getMessage
            cli._2 should not include "status: PREPARED"
            _require_no_write(fixture, destination, before)
          }
        }
      }
    }

    "install verified native export and registry scope" which {
      "retain original paired or absent context and all or exact English resource selection" in {
        Given("a real scaffold, selected native Article review run, public export and prebuilt infographic authority")
        _with_fixture("happy") { fixture =>
          val before = _authorities(fixture)
          val evidence = CozyDocumentProjectExport.verifyBundle(fixture.bundle)
          for (paired <- Vector(false, true); target <- Vector(None, Some("summary-en"))) {
            val destination = fixture.taskroot.resolve(s"prepared-$paired-${target.getOrElse("all")}")
            val config = _config(fixture, destination, paired, target)
            val original = CozyDocumentProjectPublicationTarget.planCurrentRegistration(config.registration)

            When("preparation consumes the original live registration and installs into its absent direct child")
            val prepared = CozyDocumentProjectPublicationPreparation.prepare(config)

            Then("all returned paths name installed files with exactly the original portable evidence and selected metadata")
            prepared.root shouldBe destination
            prepared.root.toRealPath() shouldBe destination
            prepared.exportRoot shouldBe destination.resolve("export")
            CozyDocumentProjectExport.verifyBundle(prepared.exportRoot) shouldBe evidence
            CozyDocumentProjectPublicationTarget.admit(prepared.exportRoot).evidence shouldBe evidence
            _files(prepared.exportRoot) shouldBe Set("manifest.yaml", "receipt.yaml", "work-products/article-review-html/article-review.html")
            _tree(prepared.exportRoot) shouldBe _tree(fixture.bundle)
            prepared.registryPaths shouldBe Vector(destination.resolve("article-media.json"))
            prepared.registryPaths.foreach(path => Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) shouldBe true)
            _require_registry(destination, original.registration.siteBinding)
            original.registration.siteContext.nonEmpty shouldBe paired
            _authorities(fixture) shouldBe before
            _children(fixture.taskroot).exists(_.getFileName.toString.startsWith("publication-preparation-")) shouldBe false
          }
          _children(fixture.taskroot).size shouldBe 4
        }
      }

      "dispatch six equivalent option orders and split or equal-value aliases into the same scope" in {
        Given("one real native fixture and generated permutations of paired options with unique absent destinations")
        _with_fixture("cli") { fixture =>
          val before = _authorities(fixture)
          val evidence = CozyDocumentProjectExport.verifyBundle(fixture.bundle)
          val binding = CozyDocumentProjectPublicationTarget.planCurrentRegistration(
            _config(fixture, fixture.taskroot.resolve("unused"), paired = true, target = Some("summary-en")).registration
          ).registration.siteBinding
          val names = Vector("--project", "--bundle", "--task-root", "--save", "--target", "--site-root", "--site-config")
          val generator = for {
            order <- Gen.oneOf(names.permutations.toVector)
            aliases <- Gen.listOfN(names.size, Gen.oneOf(false, true))
          } yield order.toVector.zip(aliases)
          var count = 0
          val property = Prop.forAll(generator) { generated =>
            val destination = fixture.taskroot.resolve(s"cli-$count")
            count += 1
            val values = Map(
              "--project" -> fixture.project.toString, "--bundle" -> fixture.bundle.toString,
              "--task-root" -> fixture.taskroot.toString, "--save" -> destination.toString,
              "--target" -> "summary-en", "--site-root" -> fixture.site.toString,
              "--site-config" -> fixture.site.resolve("site.conf").toString
            )
            val options = generated.flatMap { case (name, equals) =>
              if (equals) Vector(s"$name=${values(name)}") else Vector(name, values(name))
            }

            When("the actual media dispatcher executes a generated spelling and ordering")
            val dispatched = _dispatch(List("media", "prepare-publication", fixture.descriptor.toString) ++ options)

            Then("dispatch succeeds with PREPARED and final paths while evidence and strict English metadata stay identical")
            dispatched._1 shouldBe true
            dispatched._2 should include("status: PREPARED")
            dispatched._2 should include(s"root: $destination")
            dispatched._2 should include(s"export: ${destination.resolve("export")}")
            dispatched._2 should include(s"  - ${destination.resolve("article-media.json")}")
            _children(fixture.taskroot).exists(_.getFileName.toString.startsWith("publication-preparation-")) shouldBe false
            CozyDocumentProjectExport.verifyBundle(destination.resolve("export")) shouldBe evidence
            _require_registry(destination, binding)
            _authorities(fixture) shouldBe before
            true
          }

          When("ScalaCheck executes six bounded successful dispatcher cases")
          val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(6).withWorkers(1), property)

          Then("all six generated invocations pass and leave only their installed destinations")
          result.passed shouldBe true
          result.succeeded shouldBe 6
          count shouldBe 6
          _children(fixture.taskroot).map(_.getFileName.toString).toSet shouldBe (0 until 6).map(index => s"cli-$index").toSet
        }
      }
    }

    "refuse unauthorized output boundaries" which {
      "preserve an existing destination without creating temporary output" in {
        Given("genuine current authorities and an existing destination containing sentinel bytes and an empty directory")
        _with_fixture("existing") { fixture =>
          val destination = Files.createDirectory(fixture.taskroot.resolve("existing"))
          _write(destination.resolve("sentinel"), "keep these bytes\n")
          Files.createDirectory(destination.resolve("empty"))
          val before = _tree(fixture.root)

          When("preparation is asked to install over the existing destination")
          val failure = intercept[RuntimeException] {
            CozyDocumentProjectPublicationPreparation.prepare(_config(fixture, destination))
          }

          Then("DP-OP-001 refuses the request and preserves the complete preexisting tree")
          failure.getMessage should include("DP-OP-001")
          failure.getMessage should include("destination must be absent")
          _tree(fixture.root) shouldBe before
          _children(fixture.taskroot) shouldBe Vector(destination)
        }
      }

      "reject missing file aliased non-direct and source-overlapping output roots without writes" in {
        Given("a valid native fixture plus deterministic invalid roots, aliases and overlapping source boundaries")
        _with_fixture("roots") { fixture =>
          val file = fixture.root.resolve("task-file")
          _write(file, "not a directory\n")
          val alias = fixture.root.resolve("task-alias")
          Files.createSymbolicLink(alias, fixture.taskroot)
          val ancestor = fixture.root.resolve("ancestor-alias")
          Files.createSymbolicLink(ancestor, fixture.root)
          val missing = fixture.root.resolve("missing")
          val configs = Vector(
            _config(fixture, missing.resolve("prepared")).copy(taskRoot = missing),
            _config(fixture, file.resolve("prepared")).copy(taskRoot = file),
            _config(fixture, alias.resolve("prepared")).copy(taskRoot = alias),
            _config(fixture, ancestor.resolve("task/prepared")).copy(taskRoot = ancestor.resolve("task")),
            _config(fixture, fixture.taskroot.resolve("nested/prepared")),
            _config(fixture, fixture.root.resolve("outside"))
          ) ++ Vector(fixture.root, fixture.project, fixture.bundle, fixture.media, fixture.site).map { root =>
            _config(fixture, root.resolve("prepared")).copy(taskRoot = root)
          }
          val before = _tree(fixture.root)

          When("preparation evaluates each invalid task root or destination")
          val failures = configs.map(config => intercept[RuntimeException] {
            CozyDocumentProjectPublicationPreparation.prepare(config)
          })

          Then("each local DP-OP-001 rejection preserves sources, destinations, alias targets and the entire existing tree")
          failures.foreach(_.getMessage should include("DP-OP-001"))
          _tree(fixture.root) shouldBe before
          Files.readSymbolicLink(alias) shouldBe fixture.taskroot
          Files.readSymbolicLink(ancestor) shouldBe fixture.root
          _children(fixture.taskroot) shouldBe empty
          Files.exists(missing, LinkOption.NOFOLLOW_LINKS) shouldBe false
        }
      }
    }

    "revalidate before final installation" which {
      "propagate fresh producer failure and remove only owned temporary output after an authored source change" in {
        Given("a real current export and a callback that changes authored source after native registry preparation")
        _with_fixture("final") { fixture =>
          val destination = fixture.taskroot.resolve("prepared")
          val config = _config(fixture, destination, paired = true)
          val original = CozyDocumentProjectPublicationTarget.planCurrentRegistration(config.registration)
          val source = fixture.project.resolve("index.dox")
          val beforeproject = _tree(fixture.project)
          val beforebundle = _tree(fixture.bundle)
          val beforemedia = _tree(fixture.media)
          var callbackran = false
          var observedregistry = false
          val callback = () => {
            val temporary = _children(fixture.taskroot).head
            observedregistry = CozyArticleMediaRegistry.load(temporary).bundleDigests.nonEmpty
            callbackran = true
            _write(source, "Changed source\n")
          }

          When("ordinary preparation invokes its callback immediately before the final original revalidator")
          val failure = intercept[RuntimeException] {
            CozyDocumentProjectPublicationPreparation.prepare(config, callback)
          }
          val producer = intercept[RuntimeException] {
            CozyDocumentProjectPublicationTarget.revalidateCurrentRegistration(original)
          }

          Then("the exact fresh producer diagnostic escapes and only the controlled authored mutation remains")
          callbackran shouldBe true
          observedregistry shouldBe true
          failure.getMessage shouldBe producer.getMessage
          failure.getMessage should include("sourceauthority=stale")
          Files.readString(source, StandardCharsets.UTF_8) shouldBe "Changed source\n"
          _tree(fixture.project) shouldBe beforeproject.updated("index.dox", "Changed source\n".getBytes(StandardCharsets.UTF_8).toVector)
          _tree(fixture.bundle) shouldBe beforebundle
          _tree(fixture.media) shouldBe beforemedia
          Files.exists(destination, LinkOption.NOFOLLOW_LINKS) shouldBe false
          _children(fixture.taskroot) shouldBe empty
        }
      }
    }
  }

  private final case class Fixture(root: Path, project: Path, bundle: Path, media: Path, descriptor: Path, site: Path, taskroot: Path)

  private def _config(
    fixture: Fixture,
    destination: Path,
    paired: Boolean = false,
    target: Option[String] = None
  ): CozyDocumentProjectPublicationPreparation.Config =
    CozyDocumentProjectPublicationPreparation.Config(
      CozyDocumentProjectPublicationTarget.CurrentRegistrationConfig(fixture.project,
        CozyDocumentProjectPublicationTarget.RegistrationConfig(fixture.bundle,
          CozyArticleMediaSiteBinding.Config(fixture.descriptor, target,
            if (paired) Some(fixture.site) else None,
            if (paired) Some(fixture.site.resolve("site.conf")) else None))),
      fixture.taskroot, destination
    )

  private def _require_registry(root: Path, binding: CozyArticleMediaSiteBinding.Plan): Unit = {
    val snapshot = CozyArticleMediaRegistry.load(root)
    val expected = CozyArticleMediaPublication.produce(binding.articleIdentity, binding.candidates.map(_.variant))
    snapshot.bundleDigests.keySet shouldBe Set("article-media")
    snapshot.entries.map(_.path) shouldBe Vector(expected.entryPath)
    snapshot.entries.head.metadata shouldBe expected.metadata
    val strict = CozyArticleMediaRegistry.canonicalProjection(snapshot).strictPublications
    strict.map(_.publication) shouldBe Vector(expected.publication)
    strict.head.publication.variants.map(_.locale).toSet shouldBe binding.candidates.map(_.locale).toSet
    strict.head.publication.variants.foreach { variant =>
      variant.infographic shouldBe Some(ImageReference(
        new URI(s"/${variant.locale}/development-process/images/registration/summary.png"), Some("image/png"), Some("Original summary")
      ))
      variant.video shouldBe None
      variant.articlePdf shouldBe None
      variant.summarySlidesPdf shouldBe None
    }
  }

  private def _with_fixture(name: String, accepted: Boolean = true, structural: Boolean = false)(body: Fixture => Unit): Unit = {
    val work = Files.createDirectories(Paths.get("target/document-project-publication-preparation-spec/work").toAbsolutePath.normalize())
    val root = Files.createTempDirectory(work, name + "-").toRealPath()
    try {
      val parent = Files.createDirectory(root.resolve("document"))
      _execute(List("document-project", "scaffold", "registration", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
      val project = parent.resolve("registration.dox")
      val projectdescriptor = project.resolve("document-project.yaml")
      _write(projectdescriptor, Files.readString(projectdescriptor, StandardCharsets.UTF_8).replace(
        "activeOptionalWorkProducts: []", "activeOptionalWorkProducts:\n  - article-review-html"
      ))
      if (structural) _write_valid_presentation_semantics(project)
      val bundle = root.resolve("portable-bundle")
      if (accepted) {
        _execute(List("document-project", "run", project.toString, "--operation", "article.render-review"))
        _execute(List("document-project", "export", project.toString, "--target", "simplemodeling-org", "--save", bundle.toString))
      }
      val media = Files.createDirectory(root.resolve("original-media"))
      val site = media.resolve("publication")
      _write(media.resolve("conf/cozy/config.yaml"), """project:
        |  id: simplemodeling-org
        |  kind: smartdox-site
        |media:
        |  publication-profiles:
        |    release:
        |      root: publication
        |      site-kind: smartdox
        |""".stripMargin)
      val descriptor = media.resolve("media.yaml")
      _write(descriptor, s"""schema: cozy.media.v1
        |knowledge:
        |  id: original-package/article
        |  source: publication/content/article.dox
        |languages:
        |  - en
        |  - ja
        |articleMedia:
        |  articleIdentity: development-process/registration
        |  publicationProfile: release
        |resources:
        |${_infographic_yaml("en")}${_infographic_yaml("ja")}""".stripMargin)
      _write(site.resolve("site.conf"), "site.title = Registration\n")
      _write(site.resolve("content/article.dox"), "= Original media article =\n\nOriginal knowledge source.\n")
      // This prebuilt PNG is admitted media evidence, never a fabricated native receipt.
      val image = java.util.Base64.getDecoder.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=")
      Vector("en", "ja").foreach { locale =>
        val path = site.resolve(s"images/summary-$locale.png")
        Files.createDirectories(path.getParent)
        Files.write(path, image)
      }
      val taskroot = Files.createDirectory(root.resolve("task"))
      body(Fixture(root, project, bundle, media, descriptor, site, taskroot))
    } finally _delete(root)
  }

  private def _write_valid_presentation_semantics(project: Path, includeproblemstructure: Boolean = true): Unit = {
    val slug = project.getFileName.toString.stripSuffix(".dox")
    val core = project.resolve("content/core-en.yaml")
    val composition = _fixed_presentation_composition()
    val problemstructure = _presentation_structure_yaml("problem-structure", "problem-step", "Problem", "The prior reader path was permissive.", "Show the reader-facing problem.")
    val solutionstructure = _presentation_structure_yaml("solution-structure", "solution-step", "Solution", "The typed path preserves declared content.", "Show the reader-facing solution.")
    val structures = if (includeproblemstructure) Vector(problemstructure, solutionstructure) else Vector(solutionstructure)
    val bindings = for {
      medium <- Vector("article", "slides", "video")
      logicalpattern <- Vector("sequence", "causal-chain")
    } yield _presentation_policy_binding_yaml(medium, logicalpattern)
    val value = s"""schema: cozy.content-core.presentation-semantics.v2
id: $slug-presentation-en
contentCore:
  id: $slug:core:en
  language: en
  identity: sha256:${_sha256(core)}
composition: ${CozyExplanation.canonicalCompositionJson(composition)}
storyFlow:
  id: $slug-story-flow-en
  transitions:
    - id: problem-causes-solution
      relationType: causes
      fromStepId: problem-step
      toStepId: solution-step
structures:
${structures.map(_indent(_, 2)).mkString("\n")}
projectionPolicy:
  schema: cozy.content-core.projection-policy.v1
  id: $slug-projection-policy-en
  revision: 1
  bindings:
${bindings.map(_indent(_, 4)).mkString("\n")}
"""
    Files.writeString(project.resolve("content/presentation-semantics-en.yaml"), value, StandardCharsets.UTF_8)
  }

  private def _fixed_presentation_composition(): CozyExplanation.Composition = {
    val catalog = CozyExplanation.fixedCatalog
    val none = Vector.empty[String]
    val facts = Vector(
      CozyExplanation.Fact("name", CozyExplanation.JsonString("Cozy"), none, none),
      CozyExplanation.Fact("vision", CozyExplanation.JsonString("Make presentation semantics explicit"), none, none),
      CozyExplanation.Fact("goals", CozyExplanation.JsonArray(Vector(_labeled_value("goal", "Reliable plans"))), none, none),
      CozyExplanation.Fact("context", CozyExplanation.JsonString("Typed document workflow"), none, none),
      CozyExplanation.Fact("useCases", CozyExplanation.JsonArray(Vector(_labeled_value("use-case", "Explain document semantics"))), none, none),
      CozyExplanation.Fact("mainScenario", CozyExplanation.JsonObject(Vector(
        "id" -> CozyExplanation.JsonString("scenario"),
        "label" -> CozyExplanation.JsonString("Normalize a document"),
        "steps" -> CozyExplanation.JsonArray(Vector(_labeled_value("scenario-step", "Validate semantics")))
      )), none, none),
      CozyExplanation.Fact("mechanisms", CozyExplanation.JsonArray(Vector(_labeled_value("mechanism", "Typed validation"))), none, none)
    )
    val problem = CozyExplanation.CompositionStep(
      "problem-step", 1, "problem",
      Vector(CozyExplanation.Claim("problem-claim", "The prior reader path was permissive.", "primary", none, none)),
      _presentation_sequence(), none, none, Vector(CozyExplanation.ParameterSelection("problem"))
    )
    val solution = CozyExplanation.CompositionStep(
      "solution-step", 2, "solution",
      Vector(CozyExplanation.Claim("solution-claim", "The typed path preserves declared content.", "primary", none, none)),
      _presentation_causal_chain(), none, none, Vector(CozyExplanation.ParameterSelection("solution"))
    )
    CozyExplanation.Composition(
      "document-composition",
      CozyExplanation.CatalogSelector(catalog.catalog.id, catalog.catalog.revision, catalog.identity),
      CozyExplanation.Subject(CozyExplanation.PatternReference("software-product", 1), facts),
      CozyExplanation.Explanation(
        CozyExplanation.PatternReference("problem-solution", 1),
        Vector(
          CozyExplanation.Parameter("problem", CozyExplanation.JsonString("The reader-facing path was permissive.")),
          CozyExplanation.Parameter("solution", CozyExplanation.JsonString("The sibling schema is typed and closed."))
        ),
        Vector(problem, solution)
      ),
      Vector.empty,
      Vector.empty
    )
  }

  private def _labeled_value(id: String, label: String): CozyExplanation.JsonValue = CozyExplanation.JsonObject(Vector(
    "id" -> CozyExplanation.JsonString(id),
    "label" -> CozyExplanation.JsonString(label),
    "sourceRefs" -> CozyExplanation.JsonArray(Vector.empty),
    "assetRefs" -> CozyExplanation.JsonArray(Vector.empty)
  ))

  private def _presentation_sequence(): CozyVisualPage.Logical = CozyVisualPage.Logical(
    "sequence",
    Vector(
      CozyVisualPage.Node("first", "step", "Reader input", Vector.empty),
      CozyVisualPage.Node("second", "step", "Typed boundary", Vector.empty)
    ),
    Vector(CozyVisualPage.Relation("next", "next", "first", "second", Vector.empty))
  )

  private def _presentation_causal_chain(): CozyVisualPage.Logical = CozyVisualPage.Logical(
    "causal-chain",
    Vector(
      CozyVisualPage.Node("cause", "cause", "Closed schema", Vector.empty),
      CozyVisualPage.Node("effect", "effect", "Faithful projection", Vector.empty)
    ),
    Vector(
      CozyVisualPage.Relation("causes", "causes", "cause", "effect", Vector.empty),
      CozyVisualPage.Relation("enables", "enables", "cause", "effect", Vector.empty)
    )
  )

  private def _presentation_structure_yaml(id: String, stepid: String, heading: String, text: String, intent: String): String =
    s"""- id: $id
  storyStepId: $stepid
  article:
    articleHeading: "$heading"
    visibleText:
      - "$text"
    visualIntent: "$intent"
  visualOverrides: []"""

  private def _presentation_policy_binding_yaml(medium: String, logicalpattern: String): String =
    s"""- medium: $medium
  logicalPattern: $logicalpattern
  visual:
    pattern: flow-horizontal
    parameters:
      showRelationLabels: true"""

  private def _indent(value: String, spaces: Int): String =
    value.linesIterator.map(line => (" " * spaces) + line).mkString("\n")

  private def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(value => f"${value & 0xff}%02x").mkString

  private def _infographic_yaml(locale: String): String =
    s"""  - id: summary-$locale
       |    kind: infographic
       |    language: $locale
       |    role: article-summary
       |    source: publication/images/summary-$locale.png
       |    build: prebuilt
       |    publications:
       |      release: images/summary-$locale.png
       |    articleMedia:
       |      role: infographic
       |      publicPath: /$locale/development-process/images/registration/summary.png
       |      mediaType: image/png
       |      alt: Original summary
       |""".stripMargin

  private def _execute(args: List[String]): Unit = { _execute_output(args); () }

  private def _execute_output(args: List[String]): String = {
    val bytes = new ByteArrayOutputStream()
    Console.withOut(new PrintStream(bytes, true, "UTF-8")) {
      CozyDocumentProject.execute(args) shouldBe true
    }
    bytes.toString("UTF-8")
  }

  private def _capture_failure(action: => Unit): (RuntimeException, String) = {
    val bytes = new ByteArrayOutputStream()
    val failure = Console.withOut(new PrintStream(bytes, true, "UTF-8")) {
      intercept[RuntimeException] { action }
    }
    failure -> bytes.toString("UTF-8")
  }

  private def _dispatch_failure(args: List[String]): (RuntimeException, String) =
    _capture_failure { CozyMediaDispatcher.execute(args, CozyMedia.ProcessRunner.default) shouldBe true }

  private def _export_args(fixture: Fixture): List[String] =
    List("document-project", "export", fixture.project.toString, "--target", "simplemodeling-org", "--save", fixture.bundle.toString)

  private def _preparation_args(fixture: Fixture, destination: Path): List[String] =
    List("media", "prepare-publication", fixture.descriptor.toString, "--project", fixture.project.toString,
      "--bundle", fixture.bundle.toString, "--task-root", fixture.taskroot.toString, "--save", destination.toString,
      "--site-root", fixture.site.toString, "--site-config", fixture.site.resolve("site.conf").toString)

  private def _require_unexecuted(fixture: Fixture): Unit = {
    Files.exists(fixture.project.resolve("evidence/attempts"), LinkOption.NOFOLLOW_LINKS) shouldBe false
    Files.exists(fixture.project.resolve("target/document-project/article-review.html"), LinkOption.NOFOLLOW_LINKS) shouldBe false
    Files.exists(fixture.bundle, LinkOption.NOFOLLOW_LINKS) shouldBe false
  }

  private def _require_no_write(fixture: Fixture, destination: Path, before: Map[String, Vector[Byte]]): Unit = {
    _tree(fixture.root) shouldBe before
    Files.exists(destination, LinkOption.NOFOLLOW_LINKS) shouldBe false
    _children(fixture.taskroot).exists(_.getFileName.toString.startsWith("publication-preparation-")) shouldBe false
  }

  private def _dispatch(args: List[String]): (Boolean, String) = {
    val bytes = new ByteArrayOutputStream()
    val recognized = Console.withOut(new PrintStream(bytes, true, "UTF-8")) {
      CozyMediaDispatcher.execute(args, CozyMedia.ProcessRunner.default)
    }
    recognized -> bytes.toString("UTF-8")
  }

  private def _write(path: Path, value: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, value, StandardCharsets.UTF_8)
  }

  private def _authorities(fixture: Fixture): Vector[Map[String, Vector[Byte]]] =
    Vector(fixture.project, fixture.bundle, fixture.media).map(_tree)

  private def _children(root: Path): Vector[Path] = {
    val stream = Files.list(root)
    try stream.iterator.asScala.toVector.sortBy(_.toString)
    finally stream.close()
  }

  private def _files(root: Path): Set[String] = {
    val stream = Files.walk(root)
    try stream.iterator.asScala.filter(path => Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      .map(path => root.relativize(path).toString.replace('\\', '/')).toSet
    finally stream.close()
  }

  private def _tree(root: Path): Map[String, Vector[Byte]] = {
    val stream = Files.walk(root)
    try stream.iterator.asScala.map { path =>
      val relative = root.relativize(path).toString.replace('\\', '/')
      if (Files.isSymbolicLink(path))
        (relative + "@") -> Files.readSymbolicLink(path).toString.getBytes(StandardCharsets.UTF_8).toVector
      else if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) (relative + "/") -> Vector.empty[Byte]
      else relative -> Files.readAllBytes(path).toVector
    }.toMap
    finally stream.close()
  }

  private def _delete(path: Path): Unit = if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
    val stream = Files.walk(path)
    try stream.iterator.asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.delete(_))
    finally stream.close()
  }
}
