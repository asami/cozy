package cozy.bok

import java.nio.file.{Files, LinkOption, Path, Paths}
import java.nio.file.attribute.FileTime
import scala.collection.JavaConverters._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyBokBuildTargetSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "BoK build target cleanup" should {
    "respect producer ownership" which {
      "preserve generated unrelated inputs and the article-media cache while deleting owned stale output" in {
        Given("bounded arbitrary unrelated target names and byte contents")
        val names = Gen.choose(1, 24).flatMap(length => Gen.listOfN(length, Gen.alphaNumChar)).map(_.mkString)
        val contents = Gen.choose(0, 256).flatMap(length => Gen.listOfN(length, Gen.choose(-128, 127))).map(_.map(_.toByte).toVector)
        val inputs = for {
          count <- Gen.choose(1, 6)
          entries <- Gen.listOfN(count, for {
            name <- names
            bytes <- contents
          } yield (name, bytes))
        } yield entries
        val property = Prop.forAll(inputs) { entries =>
          _with_fixture { root =>
            Given("real JA/EN media input paths, generated producer inputs, a symlink and retained snapshot cache")
            val project = Files.createDirectory(root.resolve("project"))
            val config = CozyBok.BuildConfig.create(List(project.toString, "--no-bib-service"))
            val target = Files.createDirectory(project.resolve("target"))
            val media = Vector(
              "media/development-process/application-modeling/ja/final.mp4",
              "media/development-process/application-modeling/en/final.mp4",
              "media/development-process/application-modeling/review/optimization-20260914/ja/part-remotion.mp4",
              "media/development-process/application-modeling/ja/audio.wav",
              "media/development-process/application-modeling/en/audio.wav"
            ).zipWithIndex.map { case (name, index) =>
              _write_input(target.resolve(name), Vector(index.toByte, 0, -1))
            }
            val generated = entries.zipWithIndex.map { case ((name, bytes), index) =>
              _write_input(target.resolve(s"producer-$index-$name/input.bin"), bytes)
            }
            val topfile = _write_input(target.resolve("unrelated.bin"), Vector(9, 8, 7))
            val cache = _write_input(target.resolve("cozy-bok/article-media/production/snapshots/example/publication.json"), Vector(1, 2, 3))
            val stale = _write_input(target.resolve("cozy-bok/obsolete/output.bin"), Vector(4, 5, 6))
            val external = _write_input(root.resolve("external/input.bin"), Vector(6, 7, 8))
            val link = Files.createSymbolicLink(target.resolve("unrelated-link"), external.getParent)
            val linkdestination = Files.readSymbolicLink(link)
            val linktime = Files.getLastModifiedTime(link, LinkOption.NOFOLLOW_LINKS)
            val observations = (media ++ generated ++ Vector(topfile, cache, external)).map(path => path -> _observe(path))

            When("BoK clears its actual configured build target")
            CozyBokImplementation._clear_build_target(config)

            Then("all unrelated bytes, FileTimes and link identity remain while owned stale output disappears")
            observations.foreach { case (path, observation) => _observe(path) shouldBe observation }
            Files.isSymbolicLink(link) shouldBe true
            Files.readSymbolicLink(link) shouldBe linkdestination
            Files.getLastModifiedTime(link, LinkOption.NOFOLLOW_LINKS) shouldBe linktime
            Files.exists(stale, LinkOption.NOFOLLOW_LINKS) shouldBe false
          }
          true
        }

        When("the ownership property is checked for at least fifty successful generated trials")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("every generated trial satisfies the ownership contract")
        result.passed shouldBe true
        result.succeeded should be >= 50
      }
    }

    "reject symlink ownership roots" which {
      "reject a target root symlink without changing external files" in {
        _with_fixture { root =>
          Given("a real project whose target points to external producer files")
          val project = Files.createDirectory(root.resolve("project"))
          val config = CozyBok.BuildConfig.create(List(project.toString, "--no-bib-service"))
          val external = _write_input(root.resolve("external/media/input.bin"), Vector(1, 2, 3))
          val observation = _observe(external)
          val link = Files.createSymbolicLink(project.resolve("target"), root.resolve("external"))
          val linktime = Files.getLastModifiedTime(link, LinkOption.NOFOLLOW_LINKS)

          When("BoK attempts to clear the configured target")
          val error = intercept[Throwable](CozyBokImplementation._clear_build_target(config))

          Then("the direct-directory guard rejects the root and preserves its link and external content")
          error.getMessage should include("BoK build target must be a direct directory")
          Files.isSymbolicLink(link) shouldBe true
          Files.readSymbolicLink(link) shouldBe root.resolve("external")
          Files.getLastModifiedTime(link, LinkOption.NOFOLLOW_LINKS) shouldBe linktime
          _observe(external) shouldBe observation
        }
      }

      "reject a cozy-bok symlink without unlinking it or touching unrelated inputs" in {
        _with_fixture { root =>
          Given("a direct target with a symlinked BoK cache and unrelated producer input")
          val project = Files.createDirectory(root.resolve("project"))
          val config = CozyBok.BuildConfig.create(List(project.toString, "--no-bib-service"))
          val unrelated = _write_input(project.resolve("target/media/ja/final.mp4"), Vector(3, 4, 5))
          val external = _write_input(root.resolve("external/stale.bin"), Vector(6, 7, 8))
          val observations = Vector(unrelated, external).map(path => path -> _observe(path))
          val link = Files.createSymbolicLink(project.resolve("target/cozy-bok"), external.getParent)
          val linktime = Files.getLastModifiedTime(link, LinkOption.NOFOLLOW_LINKS)

          When("BoK attempts to clear its configured cache")
          val error = intercept[Throwable](CozyBokImplementation._clear_build_target(config))

          Then("the cache guard rejects the symlink and preserves external and unrelated inputs")
          error.getMessage should include("BoK build target cache must be a direct directory")
          Files.isSymbolicLink(link) shouldBe true
          Files.readSymbolicLink(link) shouldBe external.getParent
          Files.getLastModifiedTime(link, LinkOption.NOFOLLOW_LINKS) shouldBe linktime
          observations.foreach { case (path, observation) => _observe(path) shouldBe observation }
        }
      }

      "reject an article-media symlink without following it or touching unrelated inputs" in {
        _with_fixture { root =>
          Given("direct target and BoK directories with a symlinked article-media cache")
          val project = Files.createDirectory(root.resolve("project"))
          val config = CozyBok.BuildConfig.create(List(project.toString, "--no-bib-service"))
          val unrelated = _write_input(project.resolve("target/media/en/audio.wav"), Vector(8, 9, 10))
          val external = _write_input(root.resolve("external/publication.json"), Vector(11, 12, 13))
          val observations = Vector(unrelated, external).map(path => path -> _observe(path))
          Files.createDirectory(project.resolve("target/cozy-bok"))
          val link = Files.createSymbolicLink(project.resolve("target/cozy-bok/article-media"), external.getParent)
          val linktime = Files.getLastModifiedTime(link, LinkOption.NOFOLLOW_LINKS)

          When("BoK attempts to retain its article-media cache during cleanup")
          val error = intercept[Throwable](CozyBokImplementation._clear_build_target(config))

          Then("the retained-cache guard rejects the symlink and preserves external and unrelated inputs")
          error.getMessage should include("BoK article-media snapshot cache must be a direct directory")
          Files.isSymbolicLink(link) shouldBe true
          Files.readSymbolicLink(link) shouldBe external.getParent
          Files.getLastModifiedTime(link, LinkOption.NOFOLLOW_LINKS) shouldBe linktime
          observations.foreach { case (path, observation) => _observe(path) shouldBe observation }
        }
      }
    }
  }

  private def _write_input(path: Path, bytes: Vector[Byte]): Path = {
    Files.createDirectories(path.getParent)
    Files.write(path, bytes.toArray)
    Files.setLastModifiedTime(path, FileTime.fromMillis(1700000000123L))
    path
  }

  private def _observe(path: Path): (Vector[Byte], FileTime) =
    (Files.readAllBytes(path).toVector, Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS))

  private def _with_fixture[A](f: Path => A): A = {
    val workroot = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize.resolve("target/cozy-bok-build-target-spec")
    Files.createDirectories(workroot)
    val root = Files.createTempDirectory(workroot, "fixture-")
    try f(root)
    finally {
      // Files.walk does not follow fixture symlinks; only this fresh fixture is removed.
      val stream = Files.walk(root)
      try stream.iterator.asScala.toVector.sortBy(path => (-path.getNameCount, path.toString)).foreach(Files.deleteIfExists)
      finally stream.close()
    }
  }
}
