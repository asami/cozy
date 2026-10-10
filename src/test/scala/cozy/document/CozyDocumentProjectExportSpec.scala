package cozy.document

import java.io.{ByteArrayOutputStream, PrintStream}
import java.net.{StandardProtocolFamily, UnixDomainSocketAddress}
import java.nio.channels.ServerSocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import java.nio.file.attribute.BasicFileAttributes
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 11, 2026
 *  version Sep. 29, 2026
 * @version Oct.  8, 2026
 * @author  ASAMI, Tomoharu
 */
object CozyDocumentProjectExportSpec {
  private final case class BundleBytes(manifest: Vector[Byte], output: Vector[Byte])
}

final class CozyDocumentProjectExportSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CozyDocumentProjectExportSpec.BundleBytes

  "Cozy Document Project export" should {
    "export the portable Article review bundle" in {
      _with_temp_dir("cozy-document-project-export-bundle") { root =>
        Given("a selected Article review project with available accepted native evidence")
        val project = _accepted_project(root, "export-current")
        val output = project.resolve("target/document-project/article-review.html")
        val bundle = root.resolve("portable-bundle")

        When("export writes a new opaque-target bundle")
        val response = _execute(List("document-project", "export", project.toString, "--target", "preview", "--save", bundle.toString))
        val verified = CozyDocumentProjectExport.verifyBundle(bundle)

        Then("the manifest and selected normalized HTML form the portable bundle")
        response should include("target: preview")
        _bundle_files(bundle) shouldBe Set("manifest.yaml", "work-products/article-review-html/article-review.html")
        Files.readAllBytes(bundle.resolve("work-products/article-review-html/article-review.html")) shouldBe Files.readAllBytes(output)
        Files.readString(bundle.resolve("manifest.yaml"), StandardCharsets.UTF_8) should include("identity: cozy.document-project-export-manifest.v1")
        verified.target shouldBe "preview"
        val manifest = Files.readString(bundle.resolve("manifest.yaml"), StandardCharsets.UTF_8)
        manifest should include("role: article-review")
        manifest should include("mediaType: text/html")
        manifest should include("path: work-products/article-review-html/article-review.html")
        manifest should not include("content/core-en.yaml")
        manifest should not include("evidence/attempts")
        manifest should not include(project.toString)
      }
    }

    "require --save and a new direct destination without compatibility output" in {
      _with_temp_dir("cozy-document-project-export-destination") { root =>
        Given("an accepted project, an existing directory, and a symbolic-link destination")
        val project = _accepted_project(root, "export-destination")
        val existing = Files.createDirectory(root.resolve("existing-bundle"))
        val external = Files.createDirectory(root.resolve("external-bundle"))
        val symbolic = root.resolve("symbolic-bundle")
        Files.createSymbolicLink(symbolic, external)

        When("export omits --save or selects an existing or symbolic destination")
        val missing = _failure(List("document-project", "export", project.toString, "--target", "preview"))
        val existingfailure = _failure(List("document-project", "export", project.toString, "--target", "preview", "--save", existing.toString))
        val symbolicfailure = _failure(List("document-project", "export", project.toString, "--target", "preview", "--save", symbolic.toString))

        Then("each request rejects during destination admission before bundle writes")
        _diagnostic_tokens(missing) shouldBe Vector("DP-CLI-002")
        _diagnostic_tokens(existingfailure) shouldBe Vector("DP-PATH-001")
        _diagnostic_tokens(symbolicfailure) shouldBe Vector("DP-PATH-001")
        _bundle_files(existing) shouldBe Set.empty
        _bundle_files(external) shouldBe Set.empty
      }
    }

    "reject a symbolic ancestor of an export destination before writing output" in {
      _with_temp_dir("cozy-document-project-export-destination-ancestor") { root =>
        Given("an accepted project and a real nested parent reached through a symbolic ancestor")
        val project = _accepted_project(root, "export-destination-ancestor")
        val actual = Files.createDirectory(root.resolve("actual"))
        val nested = Files.createDirectory(actual.resolve("nested"))
        val alias = root.resolve("alias")
        Files.createSymbolicLink(alias, actual)
        val destination = alias.resolve("nested/new-bundle")
        val realdestination = nested.resolve("new-bundle")

        When("export saves beneath the symbolic ancestor")
        val failure = _failure(List("document-project", "export", project.toString, "--target", "preview", "--save", destination.toString))

        Then("destination admission fails before creating the destination directory")
        _diagnostic_tokens(failure) shouldBe Vector("DP-PATH-001")
        Files.exists(realdestination, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(destination, LinkOption.NOFOLLOW_LINKS) shouldBe false
        _relative_files(nested) shouldBe Vector.empty
      }
    }

    "reject missing and unsafe admitted inputs before bundle writes" in {
      _with_temp_dir("cozy-document-project-export-input-admission") { root =>
        Given("two accepted projects with missing and symbolic Article authority")
        val missing = _accepted_project(root, "export-missing")
        val unsafe = _accepted_project(root, "export-unsafe")
        Files.delete(missing.resolve("index.dox"))
        val external = root.resolve("external-index.dox")
        Files.writeString(external, "external\n", StandardCharsets.UTF_8)
        Files.delete(unsafe.resolve("index.dox"))
        Files.createSymbolicLink(unsafe.resolve("index.dox"), external)
        val missingdestination = root.resolve("missing-bundle")
        val unsafedestination = root.resolve("unsafe-bundle")

        When("export evaluates each unavailable accepted operation input")
        val missingfailure = _failure(List("document-project", "export", missing.toString, "--target", "preview", "--save", missingdestination.toString))
        val unsafefailure = _failure(List("document-project", "export", unsafe.toString, "--target", "preview", "--save", unsafedestination.toString))

        Then("both invalid source paths reject before destination creation")
        _diagnostic_tokens(missingfailure) shouldBe Vector("DP-OP-001")
        _diagnostic_tokens(unsafefailure) shouldBe Vector("DP-OP-001")
        Files.exists(missingdestination, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(unsafedestination, LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "reject missing and symbolic bundle members through the consumer-only verifier" in {
      _with_temp_dir("cozy-document-project-export-consumer") { root =>
        Given("portable bundles with a missing manifest, missing HTML, or symbolic HTML member")
        val manifestproject = _accepted_project(root, "export-missing-manifest")
        val outputproject = _accepted_project(root, "export-missing-output")
        val symbolicproject = _accepted_project(root, "export-symbolic-output")
        val manifestbundle = _export(manifestproject, root.resolve("manifest-bundle"))
        val outputbundle = _export(outputproject, root.resolve("output-bundle"))
        val symbolicbundle = _export(symbolicproject, root.resolve("symbolic-bundle"))
        Files.delete(manifestbundle.resolve("manifest.yaml"))
        Files.delete(outputbundle.resolve("work-products/article-review-html/article-review.html"))
        val external = root.resolve("external-exported-output.html")
        Files.writeString(external, "external\n", StandardCharsets.UTF_8)
        val symbolicoutput = symbolicbundle.resolve("work-products/article-review-html/article-review.html")
        Files.delete(symbolicoutput)
        Files.createSymbolicLink(symbolicoutput, external)

        When("a consumer without any Document Project verifies each bundle")
        val manifestfailure = _bundle_failure(manifestbundle)
        val outputfailure = _bundle_failure(outputbundle)
        val symbolicfailure = _bundle_failure(symbolicbundle)

        Then("the verifier reports the missing file shape and symbolic member failures")
        _diagnostic_tokens(manifestfailure) shouldBe Vector("DP-OP-001")
        _diagnostic_tokens(outputfailure) shouldBe Vector("DP-OP-001")
        _diagnostic_tokens(symbolicfailure) shouldBe Vector("DP-OP-001")
        manifestfailure should include("export bundle has an invalid file shape")
        outputfailure should include("export bundle has an invalid file shape")
        symbolicfailure should include("export bundle must not contain symbolic links")
      }
    }

    "reject a symbolic ancestor of a consumer bundle while accepting its direct path" in {
      _with_temp_dir("cozy-document-project-export-consumer-ancestor") { root =>
        Given("an otherwise valid bundle under a real nested parent with a symbolic ancestor alias")
        val project = _accepted_project(root, "export-consumer-ancestor")
        val actual = Files.createDirectory(root.resolve("actual"))
        val nested = Files.createDirectory(actual.resolve("nested"))
        val bundle = _export(project, nested.resolve("bundle"))
        val alias = root.resolve("alias")
        Files.createSymbolicLink(alias, actual)
        val aliasedbundle = alias.resolve("nested/bundle")
        val sourcebytes = Files.readAllBytes(project.resolve("index.dox"))
        val manifestbytes = Files.readAllBytes(bundle.resolve("manifest.yaml"))
        val outputbytes = Files.readAllBytes(bundle.resolve("work-products/article-review-html/article-review.html"))

        When("a consumer verifies the direct path and then its symbolic-ancestor alias")
        val verified = CozyDocumentProjectExport.verifyBundle(bundle)
        val failure = _bundle_failure(aliasedbundle)

        Then("the direct bundle succeeds, the alias fails once, and source and bundle bytes are unchanged")
        verified.target shouldBe "preview"
        _diagnostic_tokens(failure) shouldBe Vector("DP-OP-001")
        Files.readAllBytes(project.resolve("index.dox")) shouldBe sourcebytes
        Files.readAllBytes(bundle.resolve("manifest.yaml")) shouldBe manifestbytes
        Files.readAllBytes(bundle.resolve("work-products/article-review-html/article-review.html")) shouldBe outputbytes
      }
    }

    "verify a relocated genuine bundle after its private project is removed" in {
      _with_temp_dir("cozy-document-project-export-relocation") { root =>
        Given("a genuine accepted export and a second direct consumer directory")
        val project = _accepted_project(root, "export-relocation")
        val bundle = _export(project, root.resolve("original-bundle"))
        val baseline = CozyDocumentProjectExport.verifyBundle(bundle)
        val bytes = _bundle_bytes(bundle)
        val relocatedparent = Files.createDirectory(root.resolve("relocated"))
        val relocated = relocatedparent.resolve("bundle")

        When("the consumer relocates the bundle and deletes only its fixture project parent")
        Files.move(bundle, relocated)
        _delete(project.getParent)
        val verified = CozyDocumentProjectExport.verifyBundle(relocated)

        Then("bundle-only verification retains its identity and bytes without project authority")
        Files.exists(bundle, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.getParent, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("document-project.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        verified shouldBe baseline
        _bundle_bytes(relocated) shouldBe bytes
      }
    }

    "reject duplicate nested manifest mapping keys" in {
      val cases = Vector[(String, String, String => String)](
        ("identity", "repeated identical value", _.replace("  - identity: article-review-html", "  - identity: article-review-html\n    identity: article-review-html")),
        ("identity", "conflicting earlier value with original valid last value", _.replace("  - identity: article-review-html", "  - identity: another-product\n    identity: article-review-html")),
        ("role", "repeated identical value", _.replace("    role: article-review", "    role: article-review\n    role: article-review")),
        ("role", "conflicting earlier value with original valid last value", _.replace("    role: article-review", "    role: another-role\n    role: article-review")),
        ("mediaType", "repeated identical value", _.replace("    mediaType: text/html", "    mediaType: text/html\n    mediaType: text/html")),
        ("mediaType", "conflicting earlier value with original valid last value", _.replace("    mediaType: text/html", "    mediaType: text/plain\n    mediaType: text/html")),
        ("path", "repeated identical value", _.replace("    path: work-products/article-review-html/article-review.html", "    path: work-products/article-review-html/article-review.html\n    path: work-products/article-review-html/article-review.html")),
        ("path", "conflicting earlier value with original valid last value", _.replace("    path: work-products/article-review-html/article-review.html", "    path: work-products/article-review-html/other.html\n    path: work-products/article-review-html/article-review.html"))
      )
      cases.zipWithIndex.foreach { case ((key, form, mutate), index) =>
        _with_temp_dir(s"cozy-document-project-export-duplicate-manifest-$index") { root =>
          Given(s"a genuine accepted export with a $form duplicate manifest $key mapping key")
          val project = _accepted_project(root, s"export-duplicate-manifest-$index")
          val bundle = _export(project, root.resolve("bundle"))
          val manifestpath = bundle.resolve("manifest.yaml")
          val manifestbefore = Files.readString(manifestpath, StandardCharsets.UTF_8)

          When("the public manifest gains a duplicate mapping key")
          val manifestafter = mutate(manifestbefore)
          manifestafter should not be manifestbefore
          Files.writeString(manifestpath, manifestafter, StandardCharsets.UTF_8)
          val failure = _bundle_failure(bundle)

          Then("the consumer rejects the duplicate manifest grammar")
          _diagnostic_tokens(failure) shouldBe Vector("DP-OP-001")
          failure should include("export manifest contains duplicate mapping keys")
        }
      }
    }

    "reject every malformed manifest mapping" in {
      val cases = Vector[(String, String, String => String)](
        ("unsupported Work Product identity", "export manifest Work Product mapping is invalid", _.replace("identity: article-review-html", "identity: another-product")),
        ("unsupported Work Product role", "export manifest Work Product mapping is invalid", _.replace("role: article-review", "role: another-role")),
        ("unsupported Work Product media type", "export manifest Work Product mapping is invalid", _.replace("mediaType: text/html", "mediaType: text/plain")),
        ("traversing Work Product path", "export manifest Work Product mapping is invalid", _.replace("path: work-products/article-review-html/article-review.html", "path: ../article-review.html")),
        ("absolute Work Product path", "export manifest Work Product mapping is invalid", _.replace("path: work-products/article-review-html/article-review.html", "path: /article-review.html")),
        ("noncanonical Work Product path", "export manifest Work Product mapping is invalid", _.replace("path: work-products/article-review-html/article-review.html", "path: ./work-products/article-review-html/article-review.html")),
        ("unsupported manifest identity", "export manifest identity or fields are invalid", _.replace("identity: cozy.document-project-export-manifest.v1", "identity: forged-manifest")),
        ("non-slug target", "export manifest target is invalid", _.replace("target: preview", "target: invalid target")),
        ("unknown Work Product field", "export manifest Work Product mapping is invalid", _.replace("    path:", "    unsupported: value\n    path:")),
        ("duplicate Work Product", "export manifest must contain exactly one Work Product", value => value + value.substring(value.indexOf("  - identity:"))),
        ("unknown top-level field", "export manifest top-level keys are invalid", _.replace("target: preview\n", "target: preview\nunsupported: value\n")),
        ("reordered top-level fields", "export manifest top-level keys are invalid", _.replace("identity: cozy.document-project-export-manifest.v1\ntarget: preview", "target: preview\nidentity: cozy.document-project-export-manifest.v1"))
      )
      cases.zipWithIndex.foreach { case ((name, cause, mutate), index) =>
        _with_temp_dir(s"cozy-document-project-export-manifest-$index") { root =>
          Given(s"a genuine accepted export for the $name manifest mutation")
          val project = _accepted_project(root, s"export-manifest-$index")
          val bundle = _export(project, root.resolve("bundle"))
          CozyDocumentProjectExport.verifyBundle(bundle).target shouldBe "preview"
          val baseline = _bundle_bytes(bundle)
          val manifestpath = bundle.resolve("manifest.yaml")
          val before = Files.readString(manifestpath, StandardCharsets.UTF_8)

          When("one manifest field is changed")
          val after = mutate(before)
          after should not be before
          Files.writeString(manifestpath, after, StandardCharsets.UTF_8)
          val failure = _bundle_failure(bundle)

          Then("the consumer reports the frozen manifest diagnostic and leaves its fixture bytes intact")
          _diagnostic_tokens(failure) shouldBe Vector("DP-OP-001")
          failure should include(cause)
          Files.readString(manifestpath, StandardCharsets.UTF_8) shouldBe after
          Files.readAllBytes(bundle.resolve("work-products/article-review-html/article-review.html")).toVector shouldBe baseline.output
        }
      }
    }

    "reject each extra bundle entry while preserving the genuine components" in {
      val cases = Vector[(String, Path => Unit)](
        ("extra root file", bundle => Files.writeString(bundle.resolve("extra.yaml"), "extra\n", StandardCharsets.UTF_8)),
        ("extra work-products file", bundle => Files.writeString(bundle.resolve("work-products/private.yaml"), "extra\n", StandardCharsets.UTF_8)),
        ("extra article-review-html file", bundle => Files.writeString(bundle.resolve("work-products/article-review-html/unlisted.html"), "extra\n", StandardCharsets.UTF_8)),
        ("extra empty directory", bundle => Files.createDirectory(bundle.resolve("unlisted-directory")))
      )
      cases.zipWithIndex.foreach { case ((name, mutate), index) =>
        _with_temp_dir(s"cozy-document-project-export-extra-$index") { root =>
          Given(s"a genuine accepted export for the $name fixture")
          val project = _accepted_project(root, s"export-extra-$index")
          val bundle = _export(project, root.resolve("bundle"))
          CozyDocumentProjectExport.verifyBundle(bundle).target shouldBe "preview"
          val baseline = _bundle_bytes(bundle)

          When("one unlisted node is added to the closed bundle tree")
          mutate(bundle)
          val failure = _bundle_failure(bundle)

          Then("the consumer rejects the file shape and does not change the original components")
          _diagnostic_tokens(failure) shouldBe Vector("DP-OP-001")
          failure should include("export bundle has an invalid file shape")
          _bundle_bytes(bundle) shouldBe baseline
        }
      }
    }

    "reject UNIX socket nodes at every bundle tree level" in {
      val cases = Vector[(String, Path => Path)](
        ("bundle root", bundle => bundle.resolve("socket")),
        ("work-products directory", bundle => bundle.resolve("work-products/socket")),
        ("article-review-html directory", bundle => bundle.resolve("work-products/article-review-html/socket"))
      )
      cases.zipWithIndex.foreach { case ((name, destination), index) =>
        _with_temp_dir("socket") { root =>
          Given(s"a genuine accepted export and a UNIX socket for the $name")
          val project = _accepted_project(root, s"export-socket-$index")
          val bundle = _export(project, root.resolve("bundle"))
          val baseline = CozyDocumentProjectExport.verifyBundle(bundle)
          val bytes = _bundle_bytes(bundle)
          val socket = root.resolve("socket")
          val address = Paths.get("").toAbsolutePath.normalize().relativize(socket)
          val channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)

          When("the direct UNIX socket is moved into the bundle tree")
          try {
            channel.bind(UnixDomainSocketAddress.of(address))
            val socketpath = destination(bundle)
            Files.move(socket, socketpath)
            val attributes = Files.readAttributes(socketpath, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS)
            val failure = _bundle_failure(bundle)

            Then("the unsupported node is rejected and removal restores the unchanged valid bundle")
            attributes.isOther shouldBe true
            Files.isRegularFile(socketpath, LinkOption.NOFOLLOW_LINKS) shouldBe false
            Files.isDirectory(socketpath, LinkOption.NOFOLLOW_LINKS) shouldBe false
            Files.isSymbolicLink(socketpath) shouldBe false
            _diagnostic_tokens(failure) shouldBe Vector("DP-OP-001")
            failure should include("export bundle must contain only direct regular files and directories")
            Files.delete(socketpath)
            CozyDocumentProjectExport.verifyBundle(bundle) shouldBe baseline
            _bundle_bytes(bundle) shouldBe bytes
          } finally channel.close()
        }
      }
    }
  }

  private def _accepted_project(root: Path, slug: String): Path = {
    val parent = Files.createDirectory(root.resolve(s"$slug-parent"))
    _execute(List("document-project", "scaffold", slug, "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
    val project = parent.resolve(s"$slug.dox")
    val descriptor = project.resolve("document-project.yaml")
    Files.writeString(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8).replace("activeOptionalWorkProducts: []", "activeOptionalWorkProducts:\n  - article-review-html"), StandardCharsets.UTF_8)
    _execute(List("document-project", "run", project.toString, "--operation", "article.render-review"))
    project
  }

  private def _export(project: Path, bundle: Path): Path = { _execute(List("document-project", "export", project.toString, "--target", "preview", "--save", bundle.toString)); bundle }
  private def _execute(args: List[String]): String = { val bytes = new ByteArrayOutputStream(); Console.withOut(new PrintStream(bytes, true, "UTF-8")) { CozyDocumentProject.execute(args) shouldBe true }; bytes.toString("UTF-8").trim }
  private def _failure(args: List[String]): String = intercept[RuntimeException] { CozyDocumentProject.execute(args) }.getMessage
  private def _bundle_failure(bundle: Path): String = intercept[RuntimeException] { CozyDocumentProjectExport.verifyBundle(bundle) }.getMessage
  private def _diagnostic_tokens(value: String): Vector[String] = """DP-[A-Z]+-\d{3}""".r.findAllIn(value).toVector
  private def _bundle_bytes(bundle: Path): BundleBytes = BundleBytes(Files.readAllBytes(bundle.resolve("manifest.yaml")).toVector, Files.readAllBytes(bundle.resolve("work-products/article-review-html/article-review.html")).toVector)
  private def _relative_files(root: Path): Vector[String] = { val stream = Files.list(root); try stream.iterator().asScala.map(_.getFileName.toString).toVector.sorted finally stream.close() }
  private def _bundle_files(root: Path): Set[String] = { if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) Set.empty else { val stream = Files.walk(root); try stream.iterator().asScala.filter(path => Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).map(root.relativize(_).toString.replace('\\', '/')).toSet finally stream.close() } }
  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = { val work = Files.createDirectories(Paths.get("target/document-project-export-spec/work").toAbsolutePath.normalize()); val root = Files.createTempDirectory(work, s"$name-").toRealPath(); try body(root) finally _delete(root) }
  private def _delete(path: Path): Unit = if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) { val stream = Files.walk(path); try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach { item => try Files.deleteIfExists(item) catch { case NonFatal(_) => () } } finally stream.close() }
}
