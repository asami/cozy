package cozy.modeler

import java.io.{File, IOException}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.Comparator
import cozy.build.CozySimpleModelerDevelopmentBuild
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._

/*
 * @since   Oct.  6, 2026
 * @version Oct. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozySimpleModelerDevelopmentBuildSpec
    extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _marker = "src/main/scala/org/simplemodeling/SimpleModeler/SimpleModeler.scala"
  private val _expected_version = org.simplemodeling.cozy.BuildInfo.simpleModelerVersion
  private val _expected_scala_version = org.simplemodeling.cozy.BuildInfo.scalaVersion

  "SimpleModeler development selection" should {
    "preserve the published default" which {
      "avoid inspecting any source or consumer when selection is unset" in {
        Given("no explicit selection and a consumer whose canonicalization would fail")
        val consumer = new File("target/cozy-test/work/cozy-simplemodeler-development-build-spec/uninspected") {
          override def getCanonicalFile: File = throw new IOException("must not inspect")
        }

        When("the source directory is resolved")
        val result = CozySimpleModelerDevelopmentBuild.resolveSourceDirectory(consumer, None)

        Then("the absence of a source override is preserved without filesystem inspection")
        result shouldBe Right(None)
      }
    }

    "admit canonical disjoint source roots" which {
      "preserve spaces and canonicalize an external alias" in {
        _with_fixture { directory =>
          Given("a disjoint source checkout with spaces and a symlink spelling")
          val consumer = Files.createDirectories(directory.resolve("consumer"))
          val source = _source(directory.resolve("source with spaces"))
          val alias = Files.createSymbolicLink(directory.resolve("source alias"), source)

          When("both source spellings are explicitly selected")
          val results = Vector(source, alias).map(path =>
            CozySimpleModelerDevelopmentBuild.resolveSourceDirectory(consumer.toFile, Some(path.toString)))

          Then("both select exactly the same canonical source directory")
          results shouldBe Vector.fill(2)(Right(Some(source.toFile.getCanonicalFile)))
        }
      }

      "admit only a direct lexical and canonical checkout child inside the consumer" in {
        _with_fixture { directory =>
          Given("a consumer and a direct checkout child with a legal ASCII leaf")
          val consumer = Files.createDirectories(directory.resolve("consumer"))
          val source = _source(consumer.resolve(".codex-workflow/checkouts/source_1.2-3"))

          When("the direct child is selected")
          val result = CozySimpleModelerDevelopmentBuild.resolveSourceDirectory(consumer.toFile, Some(source.toString))

          Then("the bounded checkout placement is admitted")
          result shouldBe Right(Some(source.toFile.getCanonicalFile))
        }
      }
    }

    "reject explicit invalid selection without fallback" which {
      Vector(
        ("empty", "", "empty"),
        ("whitespace-only", " \t ", "whitespace-only"),
        ("relative", "relative/source", "absolute"),
        ("uncanonicalizable", "/invalid\u0000source", "cannot canonicalize")
      ).foreach { case (label, selection, diagnostic) =>
        s"reject a $label path" in {
          _with_fixture { directory =>
            Given(s"an explicit $label source selection")
            val consumer = Files.createDirectories(directory.resolve("consumer"))

            When("the explicit source directory is resolved")
            val result = CozySimpleModelerDevelopmentBuild.resolveSourceDirectory(consumer.toFile, Some(selection))

            Then("a descriptive rejection preserves the explicit selection failure")
            _diagnostics(result).mkString("\n") should include(diagnostic)
          }
        }
      }

      Vector("build.sbt", _marker).foreach { marker =>
        s"reject a checkout missing $marker" in {
          _with_fixture { directory =>
            Given("a disjoint source checkout missing one required marker")
            val consumer = Files.createDirectories(directory.resolve("consumer"))
            val source = _source(directory.resolve("source"))
            Files.delete(source.resolve(marker))

            When("the incomplete checkout is selected")
            val result = CozySimpleModelerDevelopmentBuild.resolveSourceDirectory(consumer.toFile, Some(source.toString))

            Then("the missing marker is named instead of choosing a published backend")
            _diagnostics(result).mkString("\n") should include(s"missing $marker")
          }
        }
      }

      "reject a missing source directory" in {
        _with_fixture { directory =>
          Given("an absolute selected directory that does not exist")
          val consumer = Files.createDirectories(directory.resolve("consumer"))
          val source = directory.resolve("missing")

          When("the absent checkout is selected")
          val result = CozySimpleModelerDevelopmentBuild.resolveSourceDirectory(consumer.toFile, Some(source.toString))

          Then("both required source markers are reported missing")
          _diagnostics(result) should have size 2
          _diagnostics(result).mkString("\n") should include("missing build.sbt")
          _diagnostics(result).mkString("\n") should include(s"missing ${_marker}")
        }
      }

      Vector(
        ("self", "", "itself"),
        ("ancestor", "..", "inside source"),
        ("ordinary nested source", "nested/source", "roots must be disjoint"),
        ("deep checkout descendant", ".codex-workflow/checkouts/source/deeper", "roots must be disjoint"),
        ("non-ASCII checkout leaf", ".codex-workflow/checkouts/モデル", "roots must be disjoint"),
        ("illegal checkout leaf", ".codex-workflow/checkouts/-source", "roots must be disjoint")
      ).foreach { case (label, relative, diagnostic) =>
        s"reject $label overlap" in {
          _with_fixture { directory =>
            Given(s"a consumer with a $label source placement")
            val consumer = Files.createDirectories(directory.resolve("consumer"))
            val source = _source(consumer.resolve(relative).normalize)

            When("the overlapping source is selected")
            val result = CozySimpleModelerDevelopmentBuild.resolveSourceDirectory(consumer.toFile, Some(source.toString))

            Then("root admission rejects the overlap descriptively")
            _diagnostics(result).mkString("\n") should include(diagnostic)
          }
        }
      }

      "reject a checkout namespace symlink escaping to a disjoint external source" in {
        _with_fixture { directory =>
          Given("a lexical checkout namespace redirected outside the consumer")
          val consumer = Files.createDirectories(directory.resolve("consumer"))
          val external = Files.createDirectories(directory.resolve("external"))
          _source(external.resolve("source"))
          val namespace = Files.createDirectories(consumer.resolve(".codex-workflow"))
          Files.createSymbolicLink(namespace.resolve("checkouts"), external)
          val selected = namespace.resolve("checkouts/source")

          When("the lexically contained checkout is selected")
          val result = CozySimpleModelerDevelopmentBuild.resolveSourceDirectory(consumer.toFile, Some(selected.toString))

          Then("canonical disjointness cannot admit the lexical escape")
          _diagnostics(result).mkString("\n") should include("canonically escape")
        }
      }

      "reject a contained source-directory symlink escaping the consumer" in {
        _with_fixture { directory =>
          Given("a direct checkout child redirected to a disjoint external source")
          val consumer = Files.createDirectories(directory.resolve("consumer"))
          val source = _source(directory.resolve("external"))
          val namespace = Files.createDirectories(consumer.resolve(".codex-workflow/checkouts"))
          val selected = Files.createSymbolicLink(namespace.resolve("source"), source)

          When("the redirected child is selected")
          val result = CozySimpleModelerDevelopmentBuild.resolveSourceDirectory(consumer.toFile, Some(selected.toString))

          Then("lexical containment rejects the source-directory escape")
          _diagnostics(result).mkString("\n") should include("canonically escape")
        }
      }

      "reject a canonical-spelling source-directory escape through an aliased consumer" in {
        _with_fixture { directory =>
          Given("an aliased consumer with a canonical direct checkout child redirected to an external source")
          val consumer = Files.createDirectories(directory.resolve("consumer")).toFile.getCanonicalFile.toPath
          val alias = Files.createSymbolicLink(directory.resolve("consumer-alias"), consumer)
          val source = _source(directory.resolve("external"))
          val namespace = Files.createDirectories(consumer.resolve(".codex-workflow/checkouts"))
          val selected = Files.createSymbolicLink(namespace.resolve("source"), source)

          When("the aliased consumer selects the redirected child through its canonical consumer spelling")
          val result = CozySimpleModelerDevelopmentBuild.resolveSourceDirectory(alias.toFile, Some(selected.toString))

          Then("canonical consumer containment rejects the source-directory escape")
          _diagnostics(result).mkString("\n") should include("canonically escape")
        }
      }

      "reject a canonical-spelling checkout-namespace escape through an aliased consumer" in {
        _with_fixture { directory =>
          Given("an aliased consumer with its canonical checkout namespace redirected to an external source parent")
          val consumer = Files.createDirectories(directory.resolve("consumer")).toFile.getCanonicalFile.toPath
          val alias = Files.createSymbolicLink(directory.resolve("consumer-alias"), consumer)
          val external = Files.createDirectories(directory.resolve("external"))
          _source(external.resolve("source"))
          val namespace = Files.createDirectories(consumer.resolve(".codex-workflow"))
          Files.createSymbolicLink(namespace.resolve("checkouts"), external)
          val selected = namespace.resolve("checkouts/source")

          When("the aliased consumer selects the redirected checkout through its canonical consumer spelling")
          val result = CozySimpleModelerDevelopmentBuild.resolveSourceDirectory(alias.toFile, Some(selected.toString))

          Then("canonical consumer containment rejects the checkout-namespace escape")
          _diagnostics(result).mkString("\n") should include("canonically escape")
        }
      }

      "reject an external source alias of the consumer" in {
        _with_fixture { directory =>
          Given("a source spelling that canonically aliases the consumer itself")
          val consumer = _source(directory.resolve("consumer"))
          val alias = Files.createSymbolicLink(directory.resolve("source-alias"), consumer)

          When("the source alias is selected")
          val result = CozySimpleModelerDevelopmentBuild.resolveSourceDirectory(consumer.toFile, Some(alias.toString))

          Then("canonical equality rejects self selection")
          _diagnostics(result).mkString("\n") should include("itself")
        }
      }

      "reject a nested source hidden by a consumer alias" in {
        _with_fixture { directory =>
          Given("a symlink spelling of the consumer and a source nested in its canonical directory")
          val consumer = Files.createDirectories(directory.resolve("consumer"))
          val alias = Files.createSymbolicLink(directory.resolve("consumer-alias"), consumer)
          val source = _source(consumer.resolve("nested/source"))

          When("the aliased consumer selects the canonical nested source")
          val result = CozySimpleModelerDevelopmentBuild.resolveSourceDirectory(alias.toFile, Some(source.toString))

          Then("canonical overlap remains rejected through consumer aliases")
          _diagnostics(result).mkString("\n") should include("roots must be disjoint")
        }
      }

      Vector("build.sbt", _marker).foreach { marker =>
        s"reject $marker linked into the consumer" in {
          _with_fixture { directory =>
            Given("a disjoint source root whose required marker links into the consumer")
            val consumer = _source(directory.resolve("consumer"))
            val source = _source(directory.resolve("source"))
            Files.delete(source.resolve(marker))
            Files.createSymbolicLink(source.resolve(marker), consumer.resolve(marker))

            When("the marker-linked source is selected")
            val result = CozySimpleModelerDevelopmentBuild.resolveSourceDirectory(consumer.toFile, Some(source.toString))

            Then("marker aliases cannot disguise source ownership")
            _diagnostics(result).mkString("\n") should include("marker must remain within selected source")
          }
        }
      }
    }
  }

  "Loaded SimpleModeler identity" should {
    "agree with the owning Cozy build" which {
      "admit exact organization module version and Scala equality" in {
        Given("actual identity values equal the owning build's backend and Scala versions")
        val identity = Vector("org.simplemodeling", "simplemodeler", _expected_version, _expected_scala_version)

        When("the loaded identity is validated")
        val result = _validate(identity)

        Then("all four exact identity requirements are admitted")
        result shouldBe Right(())
      }

      Vector(
        (0, "organization", "org.other", "org.simplemodeling"),
        (1, "moduleName", "another-modeler", "simplemodeler"),
        (2, "version", "0.0.0-SNAPSHOT", _expected_version),
        (3, "scalaVersion", "2.12.19", _expected_scala_version)
      ).foreach { case (index, label, actual, expected) =>
        s"reject an actual $label contradiction with expected and actual values" in {
          Given(s"a loaded identity with only $label contradicting the owning build")
          val identity = Vector("org.simplemodeling", "simplemodeler", _expected_version, _expected_scala_version)
            .updated(index, actual)

          When("the loaded coordinates are validated")
          val result = _validate(identity)

          Then("one deterministic diagnostic names the mismatched field and both values")
          _diagnostics(result) should have size 1
          _diagnostics(result).head should include(s"$label expected '$expected', actual '$actual'")
        }
      }

      "report simultaneous contradictions in stable identity order" in {
        Given("organization module version and Scala all contradict the owning build")
        val identity = Vector("org.other", "other", "other-version", "other-scala")

        When("all loaded coordinates are validated together")
        val result = _validate(identity)

        Then("diagnostics retain organization module version and Scala order")
        _diagnostics(result).map(_.split("source checkout: ").last.takeWhile(_ != ' ')) shouldBe
          Vector("organization", "moduleName", "version", "scalaVersion")
      }

      "preserve canonical directory and exact identity across at least fifty legal spellings" in {
        Given("a property generator of legal ASCII source names and explicit path spellings")
        val names = for {
          first <- Gen.oneOf(('A' to 'Z') ++ ('a' to 'z') ++ ('0' to '9') ++ Vector('_'))
          rest <- Gen.nonEmptyListOf(Gen.oneOf(('a' to 'z') ++ ('0' to '9') ++ Vector('_', '.', '-')))
          spelling <- Gen.oneOf("direct", "normalized", "alias")
        } yield (first.toString + rest.mkString, spelling)
        val property = Prop.forAll(names) { case (name, spelling) =>
          _with_fixture { directory =>
            Given("a legal checkout name, an explicit path spelling, and matching loaded identity")
            val consumer = Files.createDirectories(directory.resolve("consumer"))
            val source = _source(directory.resolve("sources").resolve(name))
            val selected = spelling match {
              case "normalized" => source.resolve("../").resolve(name)
              case "alias" => Files.createSymbolicLink(directory.resolve("source-alias"), source)
              case _ => source
            }
            val identity = Vector("org.simplemodeling", "simplemodeler", _expected_version, _expected_scala_version)

            When("the explicit directory and loaded coordinates are resolved")
            val resolved = CozySimpleModelerDevelopmentBuild.resolveSourceDirectory(consumer.toFile, Some(selected.toString))
            val validated = _validate(identity)

            Then("all legal spellings preserve canonical source and exact identity admission")
            resolved shouldBe Right(Some(source.toFile.getCanonicalFile))
            validated shouldBe Right(())
            true
          }
        }
        When("ScalaCheck resolves at least fifty generated source and identity selections")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("at least fifty successful cases document canonical directory and identity preservation")
        checked.passed shouldBe true
        checked.succeeded should be >= 50
      }
    }

    "identify the executing generator implementation" which {
      "load the actual generator with one source owner when source mode is selected" in {
        Given("the actual source selection and Scala3ClassGeneratorExecutor class identity")
        val selection = sys.env.get("TEXTUS_SIMPLEMODELER_CNCF_SOURCE_ROOT")
        val classname = "org.simplemodeling.SimpleModeler.generator.scala.Scala3ClassGeneratorExecutor"
        val resource = classname.replace('.', '/') + ".class"
        val selected = CozySimpleModelerDevelopmentBuild.resolveSourceDirectory(new File("."), selection)
          .fold(errors => fail(errors.mkString("\n")), identity)

        When("the running classloader loads the generator and enumerates its class resource")
        val loaded = Class.forName(classname, false, getClass.getClassLoader)
        val resources = loaded.getClassLoader.getResources(resource).asScala.toVector
        val owner = new File(loaded.getProtectionDomain.getCodeSource.getLocation.toURI).getCanonicalFile

        Then("source mode has exactly one selected target owner while the unset mode retains an available generator")
        loaded.getName shouldBe classname
        resources should not be empty
        selected.foreach { source =>
          resources should have size 1
          owner.toPath.startsWith(new File(source, "target").getCanonicalFile.toPath) shouldBe true
          owner.isDirectory shouldBe true
        }
      }
    }
  }

  private def _validate(identity: Vector[String]): Either[Vector[String], Unit] =
    CozySimpleModelerDevelopmentBuild.validateCoordinates(
      identity(0), identity(1), identity(2), identity(3), _expected_version, _expected_scala_version)

  private def _diagnostics[A](result: Either[Vector[String], A]): Vector[String] =
    result.fold(identity, value => fail(s"Expected explicit rejection, received $value"))

  private def _source(directory: Path): Path = {
    Files.createDirectories(directory)
    Files.write(directory.resolve("build.sbt"), Array.emptyByteArray)
    val marker = directory.resolve(_marker)
    Files.createDirectories(marker.getParent)
    Files.write(marker, "package org.simplemodeling.SimpleModeler\n".getBytes(StandardCharsets.UTF_8))
    directory
  }

  private def _with_fixture[A](body: Path => A): A = {
    val parent = Paths.get("target/cozy-test/work/cozy-simplemodeler-development-build-spec").toAbsolutePath.normalize
    Files.createDirectories(parent)
    val directory = Files.createTempDirectory(parent, "selection-").toFile.getCanonicalFile.toPath
    try body(directory)
    finally {
      val stream = Files.walk(directory)
      try stream.sorted(Comparator.reverseOrder[Path]()).iterator.asScala.foreach(path => Files.deleteIfExists(path))
      finally stream.close()
    }
  }
}
