package cozy

import cozy.bok.CozyBok
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
import org.scalatest.funsuite.AnyFunSuite

/*
 * @since   Jun.  3, 2026
 * @version Jun.  4, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyBokSpec extends AnyFunSuite {
  test("bok create writes KnowledgeHub BoK source scaffold") {
    _with_temp_dir("cozy-bok-create") { dir =>
      CozyBok.create(CozyBok.CreateConfig.create(List(
        "--save",
        dir.toString,
        "--name",
        "KnowledgeHub BoK",
        "--url",
        "https://www.asamioffice.com/kokubunji/knowledgehub",
        "--language",
        "ja"
      )))

      assert(Files.isRegularFile(dir.resolve("README.md")))
      assert(Files.isRegularFile(dir.resolve("STRUCTURE.md")))
      assert(Files.isRegularFile(dir.resolve(".cozy/config.yaml")))
      assert(Files.isRegularFile(dir.resolve("src/main/doxsite/site.conf")))
      assert(Files.isRegularFile(dir.resolve("src/main/doxsite/glossary/category.yaml")))
      assert(Files.isRegularFile(dir.resolve("src/main/doxsite/glossary/index.dox")))
      assert(Files.isRegularFile(dir.resolve("src/main/doxsite/rdf/site.ttl")))
      assert(Files.isRegularFile(dir.resolve("src/main/doxsite/assets/css/knowledgehub.css")))
      assert(Files.isRegularFile(dir.resolve("src/main/antora-ui/build/ui-bundle.zip")))
      assert(!Files.exists(dir.resolve("src/main/doxsite/knowledgehub/category.yaml")))
      assert(!Files.exists(dir.resolve("src/main/doxsite/site-structure.yaml")))
      assert(!Files.exists(dir.resolve("website.d")))
      assert(_read(dir.resolve("src/main/doxsite/index.dox")).startsWith("Home\n======"))
      assert(_read(dir.resolve(".cozy/config.yaml")).contains("cozy-toolchain"))
      assert(_read(dir.resolve("src/main/doxsite/site.conf")).contains("""locale_mode = "single_locale_root""""))
      assert(!_read(dir.resolve("README.md")).contains("site-structure"))
    }
  }

  test("bok create-category writes a category with article and term seeds") {
    _with_temp_dir("cozy-bok-create-category") { dir =>
      CozyBok.create(CozyBok.CreateConfig.create(List("--save", dir.toString)))
      CozyBok.createCategory(CozyBok.CategoryConfig.create(List(
        "knowledgehub",
        "--project",
        dir.toString,
        "--title",
        "KnowledgeHub",
        "--description",
        "KnowledgeHub category.",
        "--article",
        "knowledgehub-overview:KnowledgeHub Overview:Overview article.",
        "--term",
        "glossary/knowledgehub:KnowledgeHub:KnowledgeHub definition."
      )))

      assert(Files.isRegularFile(dir.resolve("src/main/doxsite/knowledgehub/category.yaml")))
      assert(Files.isRegularFile(dir.resolve("src/main/doxsite/knowledgehub/index.dox")))
      assert(Files.isRegularFile(dir.resolve("src/main/doxsite/knowledgehub/knowledgehub-overview.dox")))
      assert(Files.isRegularFile(dir.resolve("src/main/doxsite/knowledgehub/glossary/knowledgehub.dox")))
      assert(_read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")).contains("""<a href="knowledgehub-overview.html">KnowledgeHub Overview</a>"""))
      assert(_read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")).contains("""<a href="glossary/knowledgehub.html">KnowledgeHub</a>"""))
    }
  }

  test("bok equals form does not satisfy canonical metadata") {
    _with_temp_dir("cozy-bok-equals-options") { dir =>
      val e = intercept[Throwable] {
        CozyBok.CreateConfig.create(List(s"--save=${dir}"))
      }
      assert(e.getMessage.contains("--save <dir>"))
    }
  }

  test("bok build maps strategy and uses docker image from config") {
    _with_temp_dir("cozy-bok-build") { dir =>
      _write(dir.resolve(".cozy/config.yaml"), "bok:\n  docker-image: smartdox-antora:test\n")
      _write(dir.resolve("src/main/doxsite/site.conf"), "site {\n  output {\n    locale_mode = \"single_locale_root\"\n  }\n}\n")
      _write(dir.resolve("doxsite-cache-work-in-progress.d/stale.error_msg"), "stale\n")
      val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "wip"))
      val runner = new RecordingRunner

      CozyBok.build(config, runner)

      assert(config.strategy == "work-in-progress")
      assert(runner.commands.exists(_ == Vector("dox", "antora", "-strategy", "work-in-progress", "src/main/doxsite")))
      assert(runner.commands.exists(_.contains("smartdox-antora:test")))
      assert(runner.commands.exists(_.contains("/workspace/website.d")))
      assert(_read(dir.resolve("website.d/index.html")).contains("KnowledgeHub BoKのHome画面"))
      assert(Files.isRegularFile(dir.resolve("src/main/antora-ui/build/ui-bundle.zip")))
      assert(!Files.exists(dir.resolve("doxsite-cache-work-in-progress.d/stale.error_msg")))
      assert(!runner.commands.exists(_.headOption.contains("arcadia")))
    }
  }

  test("bok build CLI docker image overrides config") {
    _with_temp_dir("cozy-bok-docker-override") { dir =>
      _write(dir.resolve(".cozy/config.yaml"), "bok:\n  docker-image: config-image\n")
      val config = CozyBok.BuildConfig.create(List(dir.toString, "--docker-image", "cli-image"))
      assert(config.dockerImage == "cli-image")
    }
  }

  test("bok build defaults to the standard Cozy toolchain Docker image") {
    _with_temp_dir("cozy-bok-default-docker") { dir =>
      val config = CozyBok.BuildConfig.create(List(dir.toString))
      assert(config.dockerImage == "simplemodeling/cozy-toolchain:latest")
    }
  }

  test("bok build can use standard cozy docker image config when bok docker image is omitted") {
    _with_temp_dir("cozy-bok-standard-docker") { dir =>
      _write(dir.resolve(".cozy/config.yaml"), "cozy:\n  docker-image: cozy-config-image\n")
      val config = CozyBok.BuildConfig.create(List(dir.toString))
      assert(config.dockerImage == "cozy-config-image")
    }
  }

  test("bok build can use pdf docker image config as compatibility fallback") {
    _with_temp_dir("cozy-bok-pdf-docker") { dir =>
      _write(dir.resolve(".cozy/config.yaml"), "pdf:\n  docker-image: pdf-config-image\n")
      val config = CozyBok.BuildConfig.create(List(dir.toString))
      assert(config.dockerImage == "pdf-config-image")
    }
  }

  test("bok build uses simplemodelingorg compatibility locale default from site.conf") {
    _with_temp_dir("cozy-bok-simplemodeling") { dir =>
      _write(dir.resolve(".cozy/config.yaml"), "bok:\n  docker-image: antora-image\n")
      _write(dir.resolve("src/main/doxsite/site.conf"),
        """simplemodelingorg = true
          |site {
          |  metadata {
          |    in_language = ["ja", "en"]
          |  }
          |}
          |""".stripMargin)
      val config = CozyBok.BuildConfig.create(List(dir.toString))
      val runner = new RecordingRunner

      CozyBok.build(config, runner)

      assert(runner.commands.exists(_.contains("/workspace/website.d/ja")))
      assert(runner.commands.exists(_.contains("/workspace/website.d/en")))
    }
  }

  test("bok build includes arcadia step only when enabled") {
    _with_temp_dir("cozy-bok-arcadia") { dir =>
      _write(dir.resolve(".cozy/config.yaml"),
        """bok:
          |  docker-image: antora-image
          |  arcadia:
          |    enabled: true
          |    source: src/main/arcadiasite
          |""".stripMargin)
      val config = CozyBok.BuildConfig.create(List(dir.toString))
      val runner = new RecordingRunner

      CozyBok.build(config, runner)

      assert(runner.commands.exists(_ == Vector("arcadia", "site", "src/main/arcadiasite", "arcadiasite.d")))
    }
  }

  test("bok build copies direct assets only in production when configured") {
    _with_temp_dir("cozy-bok-direct-assets") { dir =>
      _write(dir.resolve(".cozy/config.yaml"),
        """bok:
          |  docker-image: antora-image
          |  direct-assets:
          |    enabled: true
          |    items:
          |      - source: src/main/javascript/knowledge-graph
          |        destination: website.d/knowledge-graph
          |""".stripMargin)
      _write(dir.resolve("src/main/javascript/knowledge-graph/app.js"), "console.log('graph')\n")
      val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "production"))
      val runner = new RecordingRunner

      CozyBok.build(config, runner)

      assert(runner.commands.exists(_ == Vector("dox", "site-mark", "-strategy", "production", "-output.scope.policy", "all", "src/main/doxsite")))
      assert(Files.isRegularFile(dir.resolve("website.d/knowledge-graph/app.js")))
    }
  }

  test("bok build ignores unrelated yaml items when direct assets are enabled") {
    _with_temp_dir("cozy-bok-direct-assets-scope") { dir =>
      _write(dir.resolve(".cozy/config.yaml"),
        """bok:
          |  docker-image: antora-image
          |  direct-assets:
          |    enabled: true
          |publication:
          |  items:
          |    - source: unrelated/source
          |      destination: website.d/unrelated
          |""".stripMargin)
      _write(dir.resolve("unrelated/source/app.js"), "console.log('unrelated')\n")
      val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "production"))

      CozyBok.build(config, new RecordingRunner)

      assert(!Files.exists(dir.resolve("website.d/unrelated/app.js")))
    }
  }

  test("bok preview validates port as integer metadata") {
    _with_temp_dir("cozy-bok-preview-port") { dir =>
      val e = intercept[Throwable] {
        CozyBok.preview(List(dir.toString, "--port", "not-int"), new RecordingRunner)
      }
      assert(e.getMessage.contains("not-int"))
    }
  }

  test("bok commit and upload require registered workflow commands") {
    _with_temp_dir("cozy-bok-workflow-missing") { dir =>
      val e = intercept[Throwable] {
        CozyBok.runWorkflow(CozyBok.WorkflowConfig.create("commit", List(dir.toString)), new RecordingRunner)
      }
      assert(e.getMessage.contains("bok.workflow.commit.command"))
    }
  }

  test("bok commit and upload run only registered workflow command") {
    _with_temp_dir("cozy-bok-workflow") { dir =>
      _write(dir.resolve(".cozy/config.yaml"),
        """bok:
          |  workflow:
          |    commit:
          |      command: "etc/website-commit.sh"
          |    upload:
          |      command: "etc/website-upload.sh"
          |""".stripMargin)
      val runner = new RecordingRunner

      CozyBok.runWorkflow(CozyBok.WorkflowConfig.create("commit", List(dir.toString)), runner)
      CozyBok.runWorkflow(CozyBok.WorkflowConfig.create("upload", List(dir.toString)), runner)

      assert(runner.commands == Vector(
        Vector("sh", "-c", "etc/website-commit.sh"),
        Vector("sh", "-c", "etc/website-upload.sh")
      ))
    }
  }

  private class RecordingRunner extends CozyBok.Runner {
    var calls = Vector.empty[(Vector[String], Path)]
    def commands: Vector[Vector[String]] = calls.map(_._1)
    def run(command: Vector[String], cwd: Path): Unit =
      calls = calls :+ (command -> cwd)
  }

  private def _with_temp_dir[A](prefix: String)(f: Path => A): A = {
    val dir = Files.createTempDirectory(prefix)
    try {
      f(dir)
    } finally {
      _delete(dir)
    }
  }

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _read(path: Path): String =
    new String(Files.readAllBytes(path), StandardCharsets.UTF_8)

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator.asScala.toVector.reverse.foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }
}
