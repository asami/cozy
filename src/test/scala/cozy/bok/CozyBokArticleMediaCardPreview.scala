package cozy.bok

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import scala.collection.JavaConverters._
import io.circe.Json
import org.goldenport.cli.{Config => CliConfig, Environment}
import org.goldenport.realm.Realm
import org.smartdox.doxsite.DoxSite
import org.smartdox.generator.{Config => SmartDoxConfig, Context => SmartDoxContext}
import org.smartdox.generators.DoxSiteGenerator
import org.yaml.snakeyaml.{LoaderOptions, Yaml}
import org.yaml.snakeyaml.constructor.SafeConstructor
import cozy.publication.CozyArticleMediaBuildContext

/*
 * @since   Oct.  1, 2026
 * @version Oct.  1, 2026
 * @author  ASAMI, Tomoharu
 */
object CozyBokArticleMediaCardPreview {
  def main(args: Array[String]): Unit = {
    require(args.length <= 1, "Usage: CozyBokArticleMediaCardPreview [target/preview-directory]")
    val target = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize.resolve("target")
    Files.createDirectories(target)
    val root = Paths.get(args.headOption.getOrElse("target/phase75.3-card-preview")).toAbsolutePath.normalize
    require(root.startsWith(target) && root != target, "Preview must be below target")
    require(!Files.exists(root, LinkOption.NOFOLLOW_LINKS), s"Preview requires a fresh directory: $root")
    require(root.getParent.toAbsolutePath.normalize.startsWith(target), "Preview parent must be below target")
    Files.createDirectories(root.getParent)
    require(root.getParent.toRealPath().startsWith(target.toRealPath()), "Preview parent must resolve below target")
    Files.createDirectory(root)
    val fixture = CozyBokArticleMediaCardFixture.create(root)
    fixture.render()
    println(s"Native DoxSite/card preview website: ${fixture.config.websitePath}")
  }
}

private[bok] object CozyBokArticleMediaCardFixture {
  final class Fixture(val root: Path, val project: Path, val source: Path, val publication: Path, val repository: Path, val config: CozyBok.BuildConfig) {
    def render(): Unit = CozyBokImplementation._write_bok_pages(config)
    def cards(locale: String): String = read(config.websitePath.resolve(locale).resolve("articles/index.html"))
    def notice(locale: String, uri: String = "development-process/example.html"): Path = {
      val directory = config.doxsitePath.resolve(s"WEB-INF/data/$locale")
      val stream = Files.list(directory)
      try stream.iterator.asScala.filter(path => path.getFileName.toString.matches("notice[0-9]+\\.yaml")).find { path =>
        val parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load[java.util.Map[String, Any]](read(path))
        parsed.get("notice.uri") == uri
      }.getOrElse(throw new IllegalArgumentException(s"No native Notice for $locale/$uri"))
      finally stream.close()
    }
  }

  def pdfPath(locale: String, role: String): String = s"/repository/article-media/development-process/example/$locale/$role.pdf"
  def videoPath(locale: String): String = s"/repository/video/article-media-bilingual-example-$locale-video/1.0.0/article-media-bilingual-example-$locale-video-1.0.0.mp4"

  def withFixture[A](f: Fixture => A): A = {
    val target = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize.resolve("target/cozy-bok-article-media-card")
    Files.createDirectories(target)
    val root = Files.createTempDirectory(target, "fixture-")
    try f(create(root)) finally _delete(root)
  }

  def create(root: Path): Fixture = {
    val project = root.resolve("project")
    val source = project.resolve("src/main/doxsite")
    val publication = project.resolve("src/main/publication")
    val repository = root.resolve("repository")
    val resource = Paths.get("src/test/resources/cozy/publication/article-media-bilingual-bok").toAbsolutePath.normalize
    Files.createDirectories(project)
    CozyBok.create(CozyBok.CreateConfig(project, "Article Media Cards", "https://example.invalid/cards", "ja", CozyBok.ProjectFilePolicy.Overwrite))
    _delete(source)
    _copy_tree(resource.resolve("src/main/doxsite"), source)
    _copy_tree(resource.resolve("publication"), publication)
    _copy_tree(resource.resolve("repository"), repository)
    write(project.resolve("conf/cozy/config.yaml"), "bok:\n  publication: src/main/publication\n  repository: ../repository\n  website: website.d\n")
    val bundlepath = publication.resolve("publication.json")
    val bundle = io.circe.parser.parse(read(bundlepath)).fold(error => throw error, identity)
    val entries = bundle.hcursor.downField("entries").as[Vector[Json]].fold(error => throw error, identity).map { entry =>
      if (entry.hcursor.downField("key").as[String].toOption.contains("article-media/development-process/example")) {
        val metadata = entry.hcursor.downField("metadata").focus.get
        val variants = metadata.hcursor.downField("variants").focus.get
        val augmented = Vector("ja", "en").foldLeft(variants) { (result, locale) =>
          val original = variants.hcursor.downField(locale).focus.get
          val pdfs = Vector("article_pdf", "summary_slides_pdf").map { role =>
            val fields = Vector("public_path" -> Json.fromString(pdfPath(locale, role)), "media_type" -> Json.fromString("application/pdf")) ++
              (if (locale == "en" && role == "article_pdf") Vector("label" -> Json.fromString("Article <PDF> & \"English\"")) else Vector.empty)
            _write_pdf(repository.resolve(pdfPath(locale, role).stripPrefix("/repository/")), s"${locale.toUpperCase} $role fixture")
            role -> Json.obj(fields: _*)
          }
          result.deepMerge(Json.obj(locale -> original.deepMerge(Json.obj(pdfs: _*))))
        }
        entry.deepMerge(Json.obj("metadata" -> metadata.deepMerge(Json.obj("variants" -> augmented))))
      } else entry
    }
    write(bundlepath, bundle.deepMerge(Json.obj("entries" -> Json.arr(entries: _*))).spaces2)
    val config = CozyBok.BuildConfig.create(List("--project", project.toString))
    val fixture = new Fixture(root, project, source, publication, repository, config)
    val environment = Environment.createJaJp()
    val context = new SmartDoxContext(environment, SmartDoxConfig(CliConfig.buildJaJp()), environment.contextFoundation)
    CozyArticleMediaBuildContext.withContext(project, publication, repository, "production") { effective =>
      new DoxSiteGenerator(context, DoxSite.Config.default, Some(effective.publicationPath.toFile), Some(repository.toFile), "fail").
        generate(Realm.create(DoxSite.realmConfig, source.toFile)).export(project.toFile)
    }
    Vector("ja", "en").foreach { locale =>
      _copy_tree(config.doxsitePath.resolve(locale), config.websitePath.resolve(locale))
      Vector("bootstrap-grid.min.css", "site.css", "cozy-bok-dashboard.css").foreach { name =>
        val input = Option(getClass.getClassLoader.getResourceAsStream(s"cozy/antora-ui/css/$name")).getOrElse(
          throw new IllegalArgumentException(s"Missing Cozy CSS resource: $name")
        )
        val destination = config.websitePath.resolve(s"$locale/_/css/$name")
        Files.createDirectories(destination.getParent)
        try Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING) finally input.close()
      }
    }
    _copy_tree(repository, config.websitePath.resolve("repository"))
    fixture
  }

  def read(path: Path): String = Files.readString(path, StandardCharsets.UTF_8)
  def write(path: Path, value: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, value, StandardCharsets.UTF_8)
  }

  private def _write_pdf(path: Path, text: String): Unit = {
    val content = s"BT /F1 18 Tf 40 100 Td ($text) Tj ET\n"
    val objects = Vector(
      "<< /Type /Catalog /Pages 2 0 R >>",
      "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
      "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 400 160] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
      "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
      s"<< /Length ${content.getBytes(StandardCharsets.US_ASCII).length} >>\nstream\n${content}endstream"
    )
    val output = new StringBuilder("%PDF-1.4\n")
    val offsets = objects.zipWithIndex.map { case (obj, index) =>
      val offset = output.length
      output.append(s"${index + 1} 0 obj\n$obj\nendobj\n")
      offset
    }
    val xref = output.length
    output.append(s"xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
    offsets.foreach(offset => output.append(f"$offset%010d 00000 n \n"))
    output.append(s"trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
    write(path, output.toString)
  }

  private def _copy_tree(source: Path, destination: Path): Unit = {
    if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
      Files.createDirectories(destination)
      val stream = Files.list(source)
      try stream.iterator.asScala.foreach(path => _copy_tree(path, destination.resolve(path.getFileName.toString)))
      finally stream.close()
    } else {
      Files.createDirectories(destination.getParent)
      Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private def _delete(path: Path): Unit = if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
    val stream = Files.walk(path)
    try stream.iterator.asScala.toVector.sortBy(path => -path.getNameCount).foreach(Files.deleteIfExists)
    finally stream.close()
  }
}
