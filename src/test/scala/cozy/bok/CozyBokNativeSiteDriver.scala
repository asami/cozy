package cozy.bok

import java.io.{File, StringReader}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths, StandardOpenOption}
import java.util.UUID
import java.util.concurrent.{Callable, Executors, ThreadFactory, TimeUnit}
import java.util.jar.JarFile
import javax.xml.parsers.DocumentBuilderFactory
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import org.eclipse.jgit.api.Git
import org.scalatest.{Args, GivenWhenThen, Reporter}
import org.scalatest.events._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.yaml.snakeyaml.Yaml
import org.xml.sax.InputSource
import play.api.libs.json.{JsObject, Json}

/*
 * @since   Oct.  7, 2026
 * @version Oct.  7, 2026
 * @author  ASAMI, Tomoharu
 */
object CozyBokNativeSiteDriver {
  private final case class Inputs(workspace: Path, smartdoxfile: Path, smartdoxclasspath: String,
    cozyfile: Path, cozyclasspath: String, image: String, javaexec: String)
  private final case class ProcessResult(exit: Int, stdout: Path, stderr: Path, reaped: Boolean)
  private final case class DiagramProduct(page: Path, text: String)
  private final case class AntoraProducts(root: Path, pages: Vector[Path], diagrams: Vector[DiagramProduct],
    endpointpage: Path, endpointlabel: String, endpointdiagrams: Vector[DiagramProduct])
  private val _image = "ghcr.io/asami/textus-toolchain:latest"
  private val _widgets = Vector("headline", "headline-wide", "headline-wide-reverse")
  private val _css_names = Vector("bootstrap-grid.min.css", "site.css", "cozy-bok-dashboard.css")
  private val _ordinary = "development-process/ordinary"
  private val _package = "development-process/package"
  private val _private_sentinel = "P751_NATIVE_PRIVATE_MARKDOWN_SENTINEL"

  def main(args: Array[String]): Unit = {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1800)
    val inputs = _inputs(args)
    Files.createDirectory(inputs.workspace)
    val state = new NativeState(inputs, deadline)
    val reporter = new NativeReporter(state)
    val executor = Executors.newSingleThreadExecutor(new ThreadFactory {
      override def newThread(r: Runnable): Thread = {
        val thread = new Thread(r, "p751-native-site-owned-suite")
        thread.setDaemon(true)
        thread
      }
    })
    var success = false
    var problem: Option[String] = None
    try {
      val future = executor.submit(new Callable[java.lang.Boolean] {
        override def call(): java.lang.Boolean = {
          val suite = new NativeChecks(inputs, state)
          val status = suite.run(None, Args(reporter = reporter))
          status.waitUntilCompleted()
          java.lang.Boolean.valueOf(status.succeeds() && suite.testNames.size == 2 && reporter.accepted)
        }
      })
      val remaining = math.max(1L, TimeUnit.NANOSECONDS.toSeconds(deadline - System.nanoTime()) - 60)
      success = future.get(remaining, TimeUnit.SECONDS).booleanValue()
      if (!success) problem = Some("Native specification failed or did not complete exactly two successful scenarios")
    } catch {
      case NonFatal(error) => problem = Some(error.toString)
    } finally {
      executor.shutdownNow()
      try state.close() catch { case NonFatal(error) => success = false; problem = Some(error.toString) }
      val waittime = math.max(0L, math.min(TimeUnit.SECONDS.toNanos(5), deadline - System.nanoTime()))
      val terminated = executor.awaitTermination(waittime, TimeUnit.NANOSECONDS)
      if (!terminated) { success = false; problem = Some("Owned specification thread did not terminate") }
      state.finish(success && reporter.accepted && terminated, problem, reporter.results)
    }
    if (!success || !reporter.accepted) {
      System.err.println(s"Native site prerequisite failed; retained evidence: ${inputs.workspace}/native-summary.json")
      sys.exit(1)
    }
    println(s"Native site prerequisite passed two scenarios; retained evidence: ${inputs.workspace}/native-summary.json")
  }

  private def _inputs(args: Array[String]): Inputs = {
    require(args.length == 4, "Expected four literal arguments: fresh absolute workspace, SmartDox export, Cozy Runtime export, installed image")
    val workspace = Paths.get(args(0))
    require(workspace.isAbsolute && workspace == workspace.normalize(), "Workspace must be an absolute normalized literal path")
    require(!Files.exists(workspace, LinkOption.NOFOLLOW_LINKS), s"Workspace must not exist: $workspace")
    val parent = workspace.getParent
    require(Files.isDirectory(parent) && parent.toRealPath() == parent, s"Workspace parent must exist and be canonical: $parent")
    val smartdoxfile = _canonical_file(args(1))
    val cozyfile = _canonical_file(args(2))
    require(smartdoxfile.endsWith(Paths.get("target/classpath.txt")), "SmartDox input must be its normal target/classpath.txt export")
    require(cozyfile.endsWith(Paths.get("target/cozy.d/runtime-classpath.txt")), "Cozy input must be its normal Runtime export")
    val smartdoxclasspath = _classpath(smartdoxfile)
    val cozyclasspath = _classpath(cozyfile)
    val producerclasses = smartdoxfile.getParent.resolve("scala-2.12/classes")
    require(smartdoxclasspath.split(File.pathSeparator).headOption.contains(producerclasses.toString) &&
      Files.isRegularFile(producerclasses.resolve("org/smartdox/service/SmartDoxService.class")),
      "SmartDox export must select its current canonical compiled producer first")
    val cozyclasses = cozyfile.getParent.getParent.resolve("scala-2.12/classes")
    require(cozyclasspath.split(File.pathSeparator).contains(cozyclasses.toString) &&
      Files.isRegularFile(cozyclasses.resolve("cozy/Cozy.class")), "Cozy Runtime export must include the current compiled Cozy source")
    val arcadia = cozyclasspath.split(File.pathSeparator).map(Paths.get(_)).filter(_.getFileName.toString == "arcadia_2.12-1.0.3.jar")
    require(arcadia.length == 1, "Cozy Runtime must select exactly one pinned Arcadia 1.0.3 artifact")
    val jar = new JarFile(arcadia.head.toFile)
    try require(jar.getEntry("arcadia/standalone/Standalone.class") != null, "Pinned Arcadia must contain the native Standalone entry")
    finally jar.close()
    require(args(3) == _image, s"Native proof uses the unchanged installed image ${_image}")
    val javaexec = Paths.get(System.getProperty("java.home"), "bin", "java")
    require(Files.isExecutable(javaexec), s"Current Java executable missing: $javaexec")
    Inputs(workspace, smartdoxfile, smartdoxclasspath, cozyfile, cozyclasspath, args(3), javaexec.toString)
  }

  private def _canonical_file(value: String): Path = {
    val path = Paths.get(value)
    require(path.isAbsolute && Files.isRegularFile(path) && path == path.toRealPath(), s"Export must be an existing canonical absolute file: $value")
    path
  }

  private def _classpath(file: Path): String = {
    val lines = _read(file).linesIterator.map(_.trim).filter(_.nonEmpty).toVector
    require(lines.size == 1, s"Expected one exported classpath line: $file")
    lines.head.split(File.pathSeparator, -1).foreach { entry =>
      val path = Paths.get(entry)
      require(path.isAbsolute && Files.exists(path), s"Missing absolute exported classpath entry: $entry")
    }
    lines.head
  }

  private final class NativeReporter(state: NativeState) extends Reporter {
    private var _results = Vector.empty[JsObject]
    private var _successful = 0
    private var _unsuccessful = 0
    override def apply(event: Event): Unit = synchronized {
      val result = event match {
        case value: TestSucceeded =>
          _successful += 1
          Some(Json.obj("kind" -> "succeeded", "scenario" -> value.testName))
        case value: TestFailed =>
          _unsuccessful += 1
          Some(Json.obj("kind" -> "failed", "scenario" -> value.testName, "message" -> value.message,
            "cause" -> value.throwable.map(_.toString)))
        case value: TestCanceled =>
          _unsuccessful += 1
          Some(Json.obj("kind" -> "canceled", "scenario" -> value.testName, "message" -> value.message))
        case value: TestIgnored =>
          _unsuccessful += 1
          Some(Json.obj("kind" -> "ignored", "scenario" -> value.testName))
        case value: TestPending =>
          _unsuccessful += 1
          Some(Json.obj("kind" -> "pending", "scenario" -> value.testName))
        case value: SuiteAborted =>
          _unsuccessful += 1
          Some(Json.obj("kind" -> "aborted", "message" -> value.message))
        case value: InfoProvided => println(value.message); None
        case _ => None
      }
      result.foreach { value => _results :+= value; state.record("scenario", value); println(Json.stringify(value)) }
    }
    def accepted: Boolean = synchronized { _successful == 2 && _unsuccessful == 0 }
    def results: Vector[JsObject] = synchronized { _results }
  }

  private final class NativeChecks(inputs: Inputs, state: NativeState)
      extends AnyWordSpec with Matchers with GivenWhenThen {
    "Real native BoK composition" should {
      "preserve public content, native page owners and shared assets" which {
        "P751-02F-NATIVE-01 build SingleLocaleRoot JA with a real labeled diagram and owned cleanup" in {
          Given("a fresh SingleLocaleRoot JA project, public Dox/package/include controls and actual classpath Arcadia widgets")
          state.admit()
          val config = _fixture(inputs, "single-ja", false)
          val runner = new NativeRunner(inputs, state, config, "P751_NATIVE_SINGLE_JA")
          When("the normal CozyBok build composes the selected actual SmartDox, installed Antora/Kroki and pinned Arcadia producers")
          CozyBok.build(config, runner)
          Then("real native products retain canonical inline SVG and separate freshly fetched endpoint SVG, final website articles and HomeOnly exported Notice, home/category ownership, root CSS and completed owned cleanup")
          _expect_products(config, runner, "P751_NATIVE_SINGLE_JA")
        }
        "P751-02F-NATIVE-02 build MultiLocaleSubdirs JA/EN with real labeled diagrams and owned cleanup" in {
          Given("a fresh MultiLocaleSubdirs JA/EN project, public Dox/package/include controls and actual classpath Arcadia widgets")
          state.admit()
          val config = _fixture(inputs, "multi-ja-en", true)
          val runner = new NativeRunner(inputs, state, config, "P751_NATIVE_MULTI_JA_EN")
          When("the normal CozyBok build composes both locales through actual SmartDox, installed Antora/Kroki and pinned Arcadia producers")
          CozyBok.build(config, runner)
          Then("both real locale trees retain canonical inline SVG and separate freshly fetched endpoint SVG, final website articles and HomeOnly exported Notice, native page copies, their own CSS and completed owned cleanup")
          _expect_products(config, runner, "P751_NATIVE_MULTI_JA_EN")
        }
      }
    }

    private def _expect_products(config: CozyBok.BuildConfig, runner: NativeRunner, label: String): Unit = {
      val multilocale = config.localeMode == CozyBok.LocaleMode.MultiLocaleSubdirs
      val locales = if (multilocale) Vector("ja", "en") else Vector("ja")
      config.siteOutputScopePolicy shouldBe "home_only"
      val sitecommands = runner.commands.filter(_._1.take(2) == Vector("dox", "site"))
      sitecommands should have size 1
      sitecommands.head._1.sliding(2).toVector should contain(Vector("-output.scope.policy", "home_only"))
      runner.antoraProducts should have size locales.size
      runner.commands.map(_._1.head) shouldBe (Vector("dox") ++ Vector.fill(locales.size)("docker") ++ Vector("dox", "arcadia", "dox"))
      runner.commands.foreach { case (_, cwd, _) => cwd shouldBe config.project }
      runner.commands.filter(_._1.take(2) == Vector("dox", "antora")).foreach { case (_, _, env) =>
        Vector("SMARTDOX_KROKI_DOCKER_IMAGE", "SMARTDOX_PDF_DOCKER_IMAGE", "SMARTDOX_COZY_TOOLCHAIN_IMAGE")
          .foreach(key => env(key) shouldBe inputs.image)
      }
      runner.commands.filter(_._1.head == "docker").foreach { case (command, _, _) =>
        command.sliding(2).toVector should contain(Vector("-e", "SMARTDOX_KROKI_PORT=8000"))
        command.sliding(2).toVector should contain(Vector("-e", "KROKI_LISTEN=127.0.0.1:8000"))
        command.sliding(2).toVector should contain(Vector(inputs.image, "antora"))
        command should not contain "-p"
        command should not contain "--publish"
        command.mkString(" ") should not include "9609"
      }
      runner.antoraProducts.foreach { products =>
        val playbook = _yaml(products.root.resolve("antora-playbook.yml"))
        val asciidoc = playbook.get("asciidoc").asInstanceOf[java.util.Map[String, Any]]
        val attributes = asciidoc.get("attributes").asInstanceOf[java.util.Map[String, Any]]
        attributes.get("kroki-server-url").toString shouldBe "http://127.0.0.1:8000"
        products.pages should not be empty
        products.diagrams should not be empty
        products.pages.foreach(path => Files.isRegularFile(path) shouldBe true)
        _expect_diagrams(products.diagrams, label)
        Files.isRegularFile(products.endpointpage) shouldBe true
        products.pages should contain(products.endpointpage)
        products.endpointlabel should startWith(label + "_ANTORA_ENDPOINT_")
        products.endpointdiagrams should not be empty
        products.endpointdiagrams.foreach(product => product.page shouldBe products.endpointpage)
        _expect_diagrams(products.endpointdiagrams, products.endpointlabel)
      }
      locales.foreach { locale =>
        val prefix = if (multilocale) s"$locale/" else ""
        val root = config.websitePath.resolve(prefix)
        val doxroot = config.doxsitePath.resolve(prefix)
        _read(root.resolve(s"${_ordinary}.html")) should include("P751 ordinary public content.")
        _read(root.resolve(s"${_package}.html")) should include("P751 public package content.")
        _read(root.resolve(s"${_package}.html")) should include("P751 relative include content.")
        Files.exists(doxroot.resolve(s"${_ordinary}.html")) shouldBe false
        Files.exists(doxroot.resolve(s"${_package}.html")) shouldBe false
        Vector(false, true).foreach { category =>
          val notices = _notice_names(config.doxsitePath, locale, category)
          notices should contain key (_ordinary + ".html")
          notices should contain key (_package + ".html")
        }
        Vector("index.html", "development-process/index.html").foreach { relative =>
          val page = root.resolve(relative)
          val nativepage = config.arcadiaSitePath.resolve(prefix + relative)
          Files.readAllBytes(page).toVector shouldBe Files.readAllBytes(nativepage).toVector
          val html = _read(page)
          _widgets.foreach { widget =>
            val section = ("(?s)<section[^>]*id=\"" + widget + "\"[^>]*>(.*?)</section>").r
              .findFirstMatchIn(html).map(_.group(1)).getOrElse(fail(s"Missing actual widget: $page $widget"))
            section should include(_title(locale))
            val hrefs = "href=\"([^\"]+)\"".r.findAllMatchIn(section).map(_.group(1)).toVector
            hrefs.exists(href => page.getParent.resolve(href).normalize() == root.resolve(s"${_package}.html")) shouldBe true
            Vector("N/A", "${", "c:widget", "c:optional").foreach(token => section should not include token)
          }
          _expect_css(page, root)
        }
        _expect_css(root.resolve("glossary/index.html"), root)
        _css_names.foreach(name => Files.isRegularFile(root.resolve(s"_/css/$name")) shouldBe true)
        Files.exists(doxroot.resolve(s"${_package}.dox/private.html")) shouldBe false
      }
      Vector(config.doxsitePath, config.antoraPath, config.arcadiaSitePath, config.websitePath).foreach { root =>
        _files(root, Set("html", "adoc", "yaml", "json", "ttl")).foreach { path =>
          _read(path) should not include _private_sentinel
          _read(path) should not include "package.dox/private"
        }
      }
      runner.cleanupComplete shouldBe true
      runner.processesReaped shouldBe true
    }

    private def _expect_diagrams(products: Vector[DiagramProduct], label: String): Unit = {
      products.map(_.text).exists(_.contains(label)) shouldBe true
      products.foreach { product =>
        Files.isRegularFile(product.page) shouldBe true
        product.text should include(label)
        product.text should include("Alice")
        product.text should include("Bob")
        product.text.toLowerCase(java.util.Locale.ROOT) should not include "error"
        product.text.toLowerCase(java.util.Locale.ROOT) should not include "exception"
        product.text.toLowerCase(java.util.Locale.ROOT) should not include "failed to generate"
        product.text.toLowerCase(java.util.Locale.ROOT) should not include "fallback"
      }
    }

    private def _expect_css(page: Path, root: Path): Unit = {
      val targets = "<link\\b[^>]*href=\"([^\"]+\\.css)\"".r.findAllMatchIn(_read(page))
        .map(matched => page.getParent.resolve(matched.group(1)).normalize()).toVector
      _css_names.foreach { name =>
        val target = root.resolve(s"_/css/$name").normalize()
        targets should contain(target)
        Files.isRegularFile(target) shouldBe true
      }
    }
  }

  private def _fixture(inputs: Inputs, name: String, multilocale: Boolean): CozyBok.BuildConfig = {
    val project = Files.createDirectory(inputs.workspace.resolve(name))
    val label = if (multilocale) "P751_NATIVE_MULTI_JA_EN" else "P751_NATIVE_SINGLE_JA"
    _write(project.resolve(".cozy/config.yaml"),
      s"bok:\n  docker-image: ${inputs.image}\n  arcadia-site: generated/native-arcadia\n  arcadia:\n    enabled: true\n    source: src/main/arcadiasite\n")
    _write(project.resolve("src/main/doxsite/site.conf"),
      "site.output.locale_mode = \"" + (if (multilocale) "multi_locale_subdirs" else "single_locale_root") + "\"\n" +
        s"site.metadata.in_language = [${if (multilocale) "\"ja\", \"en\"" else "\"ja\""}]\n" +
        "site.output.default_locale = \"ja\"\nsite.metadata.name = \"P751 native proof\"\nsite.metadata.vision = \"Build shared knowledge.\"\n")
    _write(project.resolve("src/main/doxsite/development-process/category.yaml"),
      "name: DevelopmentProcess\ntitle: Development Process\ndescription: Native proof articles.\nvision: Build shared knowledge.\n")
    _write(project.resolve(s"src/main/doxsite/${_ordinary}.dox"),
      _dox("Ordinary｜普通", "P751 ordinary public content.\n\n```plantuml\n@startuml\nAlice -> Bob: " + label + "\n@enduml\n```"))
    _write(project.resolve(s"src/main/doxsite/${_package}.dox/index.dox"),
      _dox("Native package｜ネイティブ・パッケージ", "P751 public package content.\n\ninclude::_part.dox[]"))
    _write(project.resolve(s"src/main/doxsite/${_package}.dox/_part.dox"), "P751 relative include content.\n")
    _write(project.resolve(s"src/main/doxsite/${_package}.dox/private.md"),
      s"---\ntitle: ${_private_sentinel}\nstatus: published\n---\n\n${_private_sentinel}\n")
    _widgets.foreach { widget =>
      val stream = Option(getClass.getResourceAsStream(s"/cozy/article-cards/$widget.html"))
        .getOrElse(throw new IllegalStateException(s"Missing actual classpath widget: $widget"))
      try _write(project.resolve(s"src/main/arcadiasite/WEB-INF/widgets/$widget.html"), new String(stream.readAllBytes(), StandardCharsets.UTF_8))
      finally stream.close()
    }
    CozyBok.BuildConfig.create(List(project.toString, "--strategy", "production", "--no-bib-service"))
  }

  private final class NativeRunner(inputs: Inputs, state: NativeState, config: CozyBok.BuildConfig, label: String)
      extends CozyBok.Runner {
    private var _commands = Vector.empty[(Vector[String], Path, Map[String, String])]
    private var _antora_products = Vector.empty[AntoraProducts]
    private var _containers = Vector.empty[String]
    private var _reaped = true
    private var _image_observed = false
    def commands: Vector[(Vector[String], Path, Map[String, String])] = _commands
    def antoraProducts: Vector[AntoraProducts] = _antora_products
    def cleanupComplete: Boolean = _containers.forall(state.cleaned)
    def processesReaped: Boolean = _reaped

    override def run(command: Vector[String], cwd: Path): Unit = run(command, cwd, Map.empty)
    override def run(command: Vector[String], cwd: Path, env: Map[String, String]): Unit = {
      require(cwd == config.project, s"Unexpected native cwd: $cwd")
      _commands :+= ((command, cwd, env))
      try command.headOption match {
        case Some("dox") =>
          _run(command, Vector(inputs.javaexec, "-cp", inputs.smartdoxclasspath, "org.smartdox.service.SmartDoxService") ++ command.tail, cwd, env)
        case Some("arcadia") =>
          _prepare_authored(config, state)
          _run(command, Vector(inputs.javaexec, "-cp", inputs.cozyclasspath, "arcadia.standalone.Standalone") ++ command.tail, cwd, env)
        case Some("docker") =>
          require(command.take(3) == Vector("docker", "run", "--rm") && command.contains(inputs.image), "Unexpected normal Docker Antora command")
          if (!_image_observed) {
            val observation = state.execute(Vector("docker", "image", "inspect", inputs.image),
              Vector("docker", "image", "inspect", inputs.image), cwd, Map.empty, false)
            require(observation.exit == 0, s"Selected image is not installed; see ${observation.stderr}")
            state.record("installedImage", Json.obj("image" -> inputs.image, "inspection" -> observation.stdout.toString,
              "observed" -> Json.parse(_read(observation.stdout))))
            _image_observed = true
          }
          val workdir = command(command.indexOf("-w") + 1).stripPrefix("/workspace/")
          val output = command(command.indexOf("--to-dir") + 1).stripPrefix("/workspace/")
          val antora = cwd.resolve(workdir)
          val (endpointsource, endpointlabel) = _prepare_endpoint(antora.resolve("docs"), label, state)
          _prepare_git(antora.resolve("docs"), endpointsource, state)
          state.record("endpointSource", Json.obj("page" -> endpointsource.toString, "label" -> endpointlabel,
            "playbook" -> antora.resolve("antora-playbook.yml").toString,
            "sourceKind" -> "fresh-test-only-raw-PlantUML", "image" -> inputs.image))
          val container = "p751-native-" + config.project.getFileName.toString + "-" + UUID.randomUUID().toString
          state.allocate(container, cwd)
          _containers :+= container
          try {
            _run(command, command.take(3) ++ Vector("--name", container) ++ command.drop(3), cwd, env)
            val site = cwd.resolve(output)
            val pages = _files(site, Set("html"))
            val diagrams = _inline_diagrams(site.resolve(_ordinary + ".html"), label)
            val endpointpage = site.resolve("development-process/p751-kroki-endpoint.html")
            val endpointdiagrams = _inline_diagrams(endpointpage, endpointlabel)
            require(pages.nonEmpty && diagrams.nonEmpty && endpointdiagrams.nonEmpty,
              s"Native Antora pages, canonical inline SVG or endpoint inline SVG missing: $site")
            val combined = pages.map(_read).mkString("\n")
            require(combined.contains("P751 ordinary public content.") && combined.contains("P751 public package content.") &&
              combined.contains("P751 relative include content.") && !combined.contains(_private_sentinel),
              s"Native Antora public content/closure failed: $site")
            _antora_products :+= AntoraProducts(antora, pages, diagrams, endpointpage, endpointlabel, endpointdiagrams)
            state.record("antoraProducts", Json.obj("root" -> site.toString, "pages" -> pages.map(_.toString),
              "canonicalDiagramProducts" -> diagrams.map(product => Json.obj("page" -> product.page.toString, "text" -> product.text)),
              "nativeLabel" -> label, "endpointSource" -> endpointsource.toString,
              "endpointPage" -> endpointpage.toString, "endpointLabel" -> endpointlabel,
              "endpointDiagramProducts" -> endpointdiagrams.map(product => Json.obj("page" -> product.page.toString, "text" -> product.text)),
              "playbook" -> antora.resolve("antora-playbook.yml").toString))
          } finally state.clean(container)
        case _ => throw new IllegalArgumentException(s"Unexpected normal build child: $command")
      } catch {
        case NonFatal(error) => state.reject(error.toString); throw error
      }
    }

    private def _run(emitted: Vector[String], effective: Vector[String], cwd: Path, env: Map[String, String]): Unit = {
      val result = state.execute(emitted, effective, cwd, env, false)
      _reaped = _reaped && result.reaped
      require(result.exit == 0 && result.reaped, s"Native child failed (${result.exit}); see ${result.stdout} and ${result.stderr}")
    }
  }

  private def _prepare_authored(config: CozyBok.BuildConfig, state: NativeState): Unit = {
    val multilocale = config.localeMode == CozyBok.LocaleMode.MultiLocaleSubdirs
    val locales = if (multilocale) config.languages else Vector(config.defaultLocale)
    for (locale <- locales; category <- Vector(false, true)) {
      val notices = _notice_names(config.doxsitePath, locale, category)
      val data = notices.getOrElse(_package + ".html", throw new IllegalStateException(s"Missing actual exported Notice: $locale $category ${_package}"))
      val relative = if (category) "development-process/index.html" else "index.html"
      val page = (if (multilocale) s"$locale/" else "") + relative
      val cssprefix = if (category) "../" else "./"
      val css = _css_names.map(name => s"""<link rel="stylesheet" href="${cssprefix}_/css/$name"/>""").mkString("\n")
      val sections = _widgets.map(widget => s"""<section id="$widget"><c:widget name="$widget" data="$data"/></section>""").mkString("\n")
      _write(config.project.resolve(config.arcadia.source).resolve(page),
        s"""<html lang="$locale"><head><title>P751 native authored page</title>$css</head><body>$sections</body></html>""" + "\n")
      state.record("authoredNoticeInput", Json.obj("locale" -> locale, "category" -> category,
        "notice" -> data, "publicUri" -> (_package + ".html"), "page" -> page))
    }
  }

  private def _notice_names(doxsite: Path, locale: String, category: Boolean): Map[String, String] = {
    val dataroot = doxsite.resolve("WEB-INF/data")
    val directory = if (category) dataroot.resolve(s"$locale/development-process") else dataroot.resolve(locale)
    val stream = Files.list(directory)
    try stream.iterator.asScala.filter(path => path.getFileName.toString.endsWith(".yaml")).flatMap { path =>
      Option(_yaml(path).get("notice.uri")).map(uri => uri.toString ->
        dataroot.relativize(path).toString.replace(File.separatorChar, '/').stripSuffix(".yaml"))
    }.toMap
    finally stream.close()
  }

  private def _prepare_endpoint(docs: Path, label: String, state: NativeState): (Path, String) = {
    val workspace = state.workspace.toRealPath()
    require(Files.isDirectory(docs) && docs == docs.toRealPath() && docs.startsWith(workspace),
      s"Generated Antora docs must belong canonically to the fresh workspace: $docs")
    val page = docs.resolve("development-process/modules/ROOT/pages/p751-kroki-endpoint.adoc")
    val parent = page.getParent.toRealPath()
    require(parent == page.getParent && parent.startsWith(docs) && parent.startsWith(workspace),
      s"Endpoint page parent must belong canonically to generated Antora docs: $parent")
    require(!Files.exists(page, LinkOption.NOFOLLOW_LINKS), s"Fresh endpoint source must not exist: $page")
    val endpointlabel = label + "_ANTORA_ENDPOINT_" + UUID.randomUUID().toString
    val source = "= P751 native Antora endpoint probe\n\n[plantuml,p751-endpoint,svg,opts=inline]\n----\n@startuml\nAlice -> Bob: " +
      endpointlabel + "\n@enduml\n----\n"
    Files.write(page, source.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
    page -> endpointlabel
  }

  private def _prepare_git(docs: Path, endpointsource: Path, state: NativeState): Unit = {
    val workspace = state.workspace.toRealPath()
    require(Files.isDirectory(docs) && docs == docs.toRealPath() && docs.startsWith(workspace),
      s"Generated Antora docs must be in the canonical fresh workspace: $docs")
    require(endpointsource == endpointsource.toRealPath() && endpointsource.startsWith(docs),
      s"Only the fresh generated endpoint source may be registered: $endpointsource")
    val gitpath = docs.resolve(".git")
    val existing = Files.exists(gitpath, LinkOption.NOFOLLOW_LINKS)
    if (existing) require(Files.isDirectory(gitpath) && gitpath == gitpath.toRealPath() && gitpath.startsWith(workspace),
      s"Fixture Git must belong canonically to generated docs: $gitpath")
    val git = if (existing) Git.open(docs.toFile) else Git.init().setDirectory(docs.toFile).call()
    try {
      require(git.getRepository.getDirectory.toPath.toRealPath() == gitpath && gitpath.startsWith(workspace),
        s"Fixture Git location must be the fresh generated docs repository: $gitpath")
      val relative = docs.relativize(endpointsource).toString.replace(File.separatorChar, '/')
      git.add().addFilepattern(if (existing) relative else ".").call()
      val command = git.commit().setMessage("Fresh P751 native Antora fixture")
        .setAuthor("P751 native fixture", "native-fixture@example.invalid")
        .setCommitter("P751 native fixture", "native-fixture@example.invalid")
      if (existing) command.setOnly(relative)
      val commit = command.call()
      state.record("fixtureGit", Json.obj("path" -> docs.toString, "gitPath" -> gitpath.toString,
        "preparation" -> (if (existing) "actual-producer-existing-endpoint-only" else "fresh-generated-docs-only"),
        "endpointSource" -> endpointsource.toString, "registeredPath" -> relative, "fixtureCommit" -> commit.getName))
    } finally git.close()
  }

  private final class NativeState(inputs: Inputs, deadline: Long) {
    private val _log_root = Files.createDirectory(inputs.workspace.resolve("logs"))
    private var _events = Vector.empty[JsObject]
    private var _active = Map.empty[Process, Vector[ProcessHandle]]
    private var _containers = Map.empty[String, (Path, Boolean)]
    private var _cleanup_attempted = Set.empty[String]
    private var _sequence = 0
    private var _stopping = false
    private var _cleanup_failed = false
    record("provenance", Json.obj("workspace" -> inputs.workspace.toString,
      "smartdoxExport" -> inputs.smartdoxfile.toString, "smartdoxClasspath" -> inputs.smartdoxclasspath,
      "cozyRuntimeExport" -> inputs.cozyfile.toString, "cozyRuntimeClasspath" -> inputs.cozyclasspath,
      "arcadiaVersion" -> "1.0.3", "image" -> inputs.image, "java" -> inputs.javaexec,
      "childLimitSeconds" -> 300, "wholeLimitSeconds" -> 1800))
    def workspace: Path = inputs.workspace
    def admit(): Unit = synchronized {
      require(!_stopping && !_cleanup_failed, "An earlier native execution failed; do not restart uncertain execution")
    }
    def reject(message: String): Unit = {
      synchronized { _stopping = true }
      record("nativeExecutionStopped", Json.obj("failure" -> message, "restartAllowed" -> false))
    }
    def record(kind: String, value: JsObject): Unit = synchronized {
      _events :+= Json.obj("kind" -> kind, "evidence" -> value)
      _write(inputs.workspace.resolve("native-events.json"), Json.prettyPrint(Json.obj("events" -> _events)) + "\n")
    }

    def execute(emitted: Vector[String], effective: Vector[String], cwd: Path,
      env: Map[String, String], cleaning: Boolean): ProcessResult = {
      val sequence = synchronized {
        require((!_stopping && !_cleanup_failed) || cleaning, "Native run is stopping; no new producer execution")
        _sequence += 1
        _sequence
      }
      val stdout = _log_root.resolve(f"$sequence%03d.stdout.log")
      val stderr = _log_root.resolve(f"$sequence%03d.stderr.log")
      val started = System.nanoTime()
      val childdeadline = math.min(deadline - TimeUnit.SECONDS.toNanos(5), started + TimeUnit.SECONDS.toNanos(if (cleaning) 20 else 295))
      require(childdeadline > started, "Native lifetime budget exhausted")
      val evidence = Json.obj("sequence" -> sequence, "emittedArgv" -> emitted, "effectiveArgv" -> effective,
        "cwd" -> cwd.toString, "environmentOverrides" -> env, "stdout" -> stdout.toString, "stderr" -> stderr.toString,
        "cleanupCommand" -> cleaning)
      record("processStarted", evidence)
      var process: Option[Process] = None
      var exit = -1
      var reaped = false
      var problem: Option[String] = None
      try {
        val builder = new ProcessBuilder(effective.asJava).directory(cwd.toFile)
          .redirectOutput(stdout.toFile).redirectError(stderr.toFile)
        env.foreach { case (key, value) => builder.environment().put(key, value) }
        val child = builder.start()
        process = Some(child)
        synchronized { _active += child -> Vector(child.toHandle) }
        var completed = false
        while (!completed && System.nanoTime() < childdeadline) {
          val descendants = child.descendants()
          try synchronized { _active += child -> (_active.getOrElse(child, Vector(child.toHandle)) ++ descendants.iterator().asScala).distinct }
          finally descendants.close()
          val remaining = math.min(TimeUnit.SECONDS.toNanos(1), childdeadline - System.nanoTime())
          if (remaining > 0) completed = child.waitFor(remaining, TimeUnit.NANOSECONDS)
        }
        if (!completed) throw new IllegalStateException(s"Native child exceeded its bounded lifetime: $effective")
        exit = child.exitValue()
      } catch {
        case NonFatal(error) => problem = Some(error.toString); throw error
      } finally {
        process.foreach { child =>
          val interrupted = Thread.interrupted()
          try reaped = _reap(child)
          finally if (interrupted) Thread.currentThread().interrupt()
        }
        record("processFinished", evidence ++ Json.obj("exit" -> exit, "reaped" -> reaped,
          "elapsedMillis" -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started), "failure" -> problem))
        if (process.isDefined && !reaped) throw new IllegalStateException(s"Owned child cleanup uncertain; see $stderr")
      }
      ProcessResult(exit, stdout, stderr, reaped)
    }

    private def _reap(process: Process): Boolean = {
      val handles = synchronized { _active.getOrElse(process, Vector(process.toHandle)) }
      handles.reverse.filter(_.isAlive).foreach(_.destroyForcibly())
      val stopdeadline = math.min(deadline - TimeUnit.SECONDS.toNanos(5), System.nanoTime() + TimeUnit.SECONDS.toNanos(5))
      handles.foreach { handle =>
        if (handle.isAlive && System.nanoTime() < stopdeadline) {
          try handle.onExit().get(math.max(1L, stopdeadline - System.nanoTime()), TimeUnit.NANOSECONDS)
          catch { case NonFatal(_) => () }
        }
      }
      if (process.isAlive) process.waitFor(math.max(1L, stopdeadline - System.nanoTime()), TimeUnit.NANOSECONDS)
      val reaped = handles.forall(!_.isAlive) && !process.isAlive
      synchronized { if (reaped) _active -= process else _cleanup_failed = true }
      reaped
    }

    def allocate(name: String, cwd: Path): Unit = {
      require(_absent(name, cwd), s"Allocated unique container name already exists: $name")
      synchronized { _containers += name -> (cwd -> false) }
      record("containerAllocated", Json.obj("name" -> name, "cwd" -> cwd.toString))
    }
    def cleaned(name: String): Boolean = synchronized { _containers.get(name).exists(_._2) && !_cleanup_failed }

    private def _absent(name: String, cwd: Path): Boolean = {
      val command = Vector("docker", "container", "inspect", name)
      val result = execute(command, command, cwd, Map.empty, true)
      if (result.exit == 0) false
      else {
        val error = _read(result.stderr)
        require(result.exit == 1 && (error.contains("No such container:") || error.contains("No such object:")),
          s"Container inspection uncertain for exact allocated name $name; see ${result.stderr}")
        true
      }
    }

    def clean(name: String): Unit = {
      val cwd = synchronized {
        require(!_cleanup_attempted.contains(name), s"Do not replay uncertain container cleanup: $name")
        val path = _containers.getOrElse(name, throw new IllegalArgumentException(s"Unowned container: $name"))._1
        _cleanup_attempted += name
        path
      }
      val interrupted = Thread.interrupted()
      try {
        if (!_absent(name, cwd)) {
          val command = Vector("docker", "container", "rm", "--force", name)
          val result = execute(command, command, cwd, Map.empty, true)
          require(result.exit == 0 && result.reaped, s"Exact owned container removal failed: $name; see ${result.stderr}")
          require(_absent(name, cwd), s"Exact owned container remains: $name")
        }
        synchronized { _containers += name -> (cwd -> true) }
        record("containerCleaned", Json.obj("name" -> name, "confirmedAbsent" -> true))
      } catch {
        case NonFatal(error) =>
          synchronized { _cleanup_failed = true }
          record("containerCleanupFailed", Json.obj("name" -> name, "failure" -> error.toString))
          throw error
      } finally if (interrupted) Thread.currentThread().interrupt()
    }

    def close(): Unit = {
      val processes = synchronized { _stopping = true; _active.keys.toVector }
      processes.foreach { process => require(_reap(process), "Owned process cleanup uncertain") }
      val containers = synchronized { _containers.toVector.filter { case (name, (_, absent)) => !absent && !_cleanup_attempted.contains(name) } }
      containers.foreach { case (name, _) => clean(name) }
      require(synchronized { _active.isEmpty && !_cleanup_failed && _containers.values.forall(_._2) }, "Native cleanup remains failed or uncertain")
    }
    def finish(success: Boolean, problem: Option[String], results: Vector[JsObject]): Unit = synchronized {
      val clean = _active.isEmpty && !_cleanup_failed && _containers.values.forall(_._2)
      _write(inputs.workspace.resolve("native-summary.json"), Json.prettyPrint(Json.obj(
        "success" -> (success && clean), "failure" -> problem, "scenarios" -> results,
        "cleanupComplete" -> clean, "ownedProcessesRemaining" -> _active.size,
        "containers" -> _containers.toVector.map { case (name, (_, absent)) => Json.obj("name" -> name, "confirmedAbsent" -> absent) },
        "eventsFile" -> inputs.workspace.resolve("native-events.json").toString,
        "productsRetained" -> true)) + "\n")
    }
  }

  private def _dox(title: String, body: String): String =
    s"$title\n========================\n\n# HEAD\n\nstatus=published\npublished_at=2026-10-07\n\n## HEADLINE\n\n$title\n\n## BRIEF\n\nNative proof content.\n\n# Body\n\n$body\n"
  private def _title(locale: String): String = if (locale == "ja") "ネイティブ・パッケージ" else "Native package"
  private def _yaml(path: Path): java.util.Map[String, Any] = new Yaml().load[java.util.Map[String, Any]](_read(path))
  private def _inline_diagrams(page: Path, label: String): Vector[DiagramProduct] = {
    require(Files.isRegularFile(page), s"Actual native diagram page missing: $page")
    val fragments = "(?s)<svg\\b[^>]*>.*?</svg\\s*>".r.findAllIn(_read(page)).toVector
    require(fragments.nonEmpty, s"Native page has no complete inline SVG: $page")
    val products = fragments.map(fragment => DiagramProduct(page, _svg_text(fragment, page))).filter(_.text.contains(label))
    require(products.nonEmpty, s"Native inline SVG lacks the distinctive diagram label: $page $label")
    products.foreach { product =>
      val text = product.text.toLowerCase(java.util.Locale.ROOT)
      require(product.text.contains("Alice") && product.text.contains("Bob") &&
        !Vector("error", "exception", "failed to generate", "fallback").exists(text.contains),
        s"Native inline SVG contains invalid diagram output: $page $label")
    }
    products
  }
  private def _svg_text(fragment: String, page: Path): String = {
    val factory = DocumentBuilderFactory.newInstance()
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    factory.setXIncludeAware(false)
    factory.setExpandEntityReferences(false)
    factory.setNamespaceAware(true)
    val document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(fragment)))
    val root = document.getDocumentElement
    require(root.getLocalName == "svg" && root.getNamespaceURI == "http://www.w3.org/2000/svg",
      s"Native diagram is not a namespaced SVG root: $page")
    root.getTextContent
  }
  private def _files(root: Path, suffixes: Set[String]): Vector[Path] = {
    require(Files.isDirectory(root), s"Actual native product directory missing: $root")
    val stream = Files.walk(root)
    try stream.iterator.asScala.filter(path => Files.isRegularFile(path) && suffixes.exists(suffix => path.toString.endsWith("." + suffix))).toVector.sortBy(_.toString)
    finally stream.close()
  }
  private def _read(path: Path): String = Files.readString(path, StandardCharsets.UTF_8)
  private def _write(path: Path, text: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.write(path, text.getBytes(StandardCharsets.UTF_8))
    ()
  }
}
