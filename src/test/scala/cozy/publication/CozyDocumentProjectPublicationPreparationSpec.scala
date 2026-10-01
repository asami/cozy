package cozy.publication

import java.io.{ByteArrayOutputStream, PrintStream}
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import cozy.document.{CozyDocumentProject, CozyDocumentProjectExport}
import cozy.media.{CozyMedia, CozyMediaDispatcher}
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

  private def _with_fixture(name: String)(body: Fixture => Unit): Unit = {
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
      _execute(List("document-project", "run", project.toString, "--operation", "article.render-review"))
      val bundle = root.resolve("portable-bundle")
      _execute(List("document-project", "export", project.toString, "--target", "simplemodeling-org", "--save", bundle.toString))
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

  private def _execute(args: List[String]): Unit = {
    val bytes = new ByteArrayOutputStream()
    Console.withOut(new PrintStream(bytes, true, "UTF-8")) {
      CozyDocumentProject.execute(args) shouldBe true
    }
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
