package cozy.publication

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import org.goldenport.cli.{Config => CliConfig, Environment}
import org.goldenport.realm.Realm
import org.goldenport.realm.Realm.StringData
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.smartdox.doxsite.DoxSite
import org.smartdox.generator.{Config => SmartDoxConfig, Context => SmartDoxContext}
import org.smartdox.generators.DoxSiteGenerator
import org.yaml.snakeyaml.Yaml
import cozy.bok.CozyBok

/*
 * @since   Aug.  5, 2026
 * @version Aug.  5, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyArticleMediaBokAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy BoK article-media publication" should {
    "project the persistent bilingual production fixture through the pinned DoxSite generator" in {
      Given("a generated BoK scaffold with its temporary doxsite source replaced by the persistent bilingual source, publication bundle, and sibling repository")
      _with_fixture { fixture =>
        val context = CozyArticleMediaBuildContext.withContext(
          fixture.project,
          fixture.publication,
          fixture.repository,
          "production"
        )(value => value)
        val source = Realm.create(DoxSite.realmConfig, fixture.source.toFile)

        When("the exact production-effective immutable snapshot is passed to the actual DoxSiteGenerator")
        val site = new DoxSiteGenerator(
          _smartdox_context,
          DoxSite.Config.default,
          Some(context.publicationPath.toFile),
          Some(fixture.repository.toFile),
          "fail"
        ).generate(source)
        val english = _string(site, "doxsite.d/en/development-process/example.html")
        val japanese = _string(site, "doxsite.d/ja/development-process/example.html")
        val englishglobal = _notice(site, "doxsite.d/WEB-INF/data/en", "development-process/example.html")
        val englishcategory = _notice(site, "doxsite.d/WEB-INF/data/en/development-process", "development-process/example.html")
        val japaneseglobal = _notice(site, "doxsite.d/WEB-INF/data/ja", "development-process/example.html")
        val japanesecategory = _notice(site, "doxsite.d/WEB-INF/data/ja/development-process", "development-process/example.html")
        val englishplain = _string(site, "doxsite.d/en/development-process/plain.html")
        val japaneseplain = _string(site, "doxsite.d/ja/development-process/plain.html")
        val englishplainnotice = _notice(site, "doxsite.d/WEB-INF/data/en", "development-process/plain.html")
        val japanesplainnotice = _notice(site, "doxsite.d/WEB-INF/data/ja", "development-process/plain.html")

        Then("each locale receives only its own localized source and exact-locale article video URL")
        english should include("This is the English effective lead paragraph.")
        english should not include("日本語の有効なリード段落です。")
        japanese should include("日本語の有効なリード段落です。")
        japanese should not include("This is the English effective lead paragraph.")
        english should include("/repository/video/article-media-bilingual-example-en-video/1.0.0/article-media-bilingual-example-en-video-1.0.0.mp4")
        english should not include("/repository/video/article-media-bilingual-example-ja-video/")
        japanese should include("/repository/video/article-media-bilingual-example-ja-video/1.0.0/article-media-bilingual-example-ja-video-1.0.0.mp4")
        japanese should not include("/repository/video/article-media-bilingual-example-en-video/")

        And("global and category Notices agree in each locale while retaining exact-locale infographic and video media")
        _media(englishglobal) shouldBe _media(englishcategory)
        _media(japaneseglobal) shouldBe _media(japanesecategory)
        _media(englishglobal) should not be _media(japaneseglobal)
        _media(englishglobal).flatMap(_.get("infographic")).flatMap(_map_value(_, "public_path")) shouldBe
          Some("/repository/article-media/development-process/example/en/infographic.png")
        _media(englishglobal).flatMap(_.get("video")).flatMap(_map_value(_, "content_url")) shouldBe
          Some("/repository/video/article-media-bilingual-example-en-video/1.0.0/article-media-bilingual-example-en-video-1.0.0.mp4")
        _media(japaneseglobal).flatMap(_.get("infographic")).flatMap(_map_value(_, "public_path")) shouldBe
          Some("/repository/article-media/development-process/example/ja/infographic.png")
        _media(japaneseglobal).flatMap(_.get("video")).flatMap(_map_value(_, "content_url")) shouldBe
          Some("/repository/video/article-media-bilingual-example-ja-video/1.0.0/article-media-bilingual-example-ja-video-1.0.0.mp4")

        And("the media-free article and both Notice projections remain free of projected media")
        englishplain should include("This is the media-free English lead.")
        japaneseplain should include("メディアなしの日本語リードです。")
        englishplain should not include("smartdox-article-media-video")
        japaneseplain should not include("smartdox-article-media-video")
        _media(englishplainnotice) shouldBe empty
        _media(japanesplainnotice) shouldBe empty
      }
    }

    "preserve complete generated Realm bytes when production publication metadata is empty or absent" in {
      Given("the same source and an empty production-effective Cozy snapshot")
      _with_fixture { fixture =>
        val emptypublication = fixture.project.resolve("target/empty-publication")
        val context = CozyArticleMediaBuildContext.withContext(
          fixture.project,
          emptypublication,
          fixture.repository,
          "production"
        )(value => value)
        val emptyrealm = new DoxSiteGenerator(
          _smartdox_context,
          DoxSite.Config.default,
          Some(context.publicationPath.toFile),
          Some(fixture.repository.toFile),
          "fail"
        ).generate(Realm.create(DoxSite.realmConfig, fixture.source.toFile))
        val absentrealm = new DoxSiteGenerator(
          _smartdox_context,
          DoxSite.Config.default,
          None
        ).generate(Realm.create(DoxSite.realmConfig, fixture.source.toFile))

        When("both actual generated Realms are exported to contained comparison roots")
        val emptybytes = _exported_bytes(emptyrealm, fixture.root.resolve("target/realm-empty"))
        val absentbytes = _exported_bytes(absentrealm, fixture.root.resolve("target/realm-absent"))

        Then("the same full relative path set, exact non-YAML bytes, and semantic YAML values are preserved")
        emptybytes.keySet shouldBe absentbytes.keySet
        emptybytes.keySet.toVector.sorted.foreach { path =>
          val emptyvalue = emptybytes(path)
          val absentvalue = absentbytes(path)
          if (path.endsWith(".yaml") || path.endsWith(".yml")) {
            val emptysyntax = new String(emptyvalue.toArray, StandardCharsets.UTF_8)
            val absentsyntax = new String(absentvalue.toArray, StandardCharsets.UTF_8)
            withClue(s"semantic YAML value for Realm path: $path") {
              _yaml_value(new Yaml().load[Any](emptysyntax)) shouldBe
                _yaml_value(new Yaml().load[Any](absentsyntax))
            }
          } else {
            withClue(s"exact bytes for Realm path: $path") {
              emptyvalue shouldBe absentvalue
            }
          }
        }
        emptybytes.values.foreach { bytes =>
          new String(bytes.toArray, StandardCharsets.UTF_8) should not include("smartdox-article-media-video")
        }
        _media(_notice(emptyrealm, "doxsite.d/WEB-INF/data/en", "development-process/plain.html")) shouldBe empty
        _media(_notice(absentrealm, "doxsite.d/WEB-INF/data/ja", "development-process/plain.html")) shouldBe empty
      }
    }

    "stage actual generated Realm bytes at a common URL root without deleting Git metadata" in {
      Given("a production-generated Realm, a sibling artifact repository, and the generated stage scaffold installed as a workflow")
      _with_fixture { fixture =>
        val context = CozyArticleMediaBuildContext.withContext(
          fixture.project,
          fixture.publication,
          fixture.repository,
          "production"
        )(value => value)
        val site = new DoxSiteGenerator(
          _smartdox_context,
          DoxSite.Config.default,
          Some(context.publicationPath.toFile),
          Some(fixture.repository.toFile),
          "fail"
        ).generate(Realm.create(DoxSite.realmConfig, fixture.source.toFile))
        val exported = fixture.root.resolve("target/realm-stage-source")
        Files.createDirectories(exported)
        site.export(exported.toFile)
        _copy_tree(exported.resolve("doxsite.d"), fixture.website)
        val stageproto = fixture.project.resolve("etc/website-stage.sh.proto")
        Files.readString(stageproto, StandardCharsets.UTF_8) should include("--exclude '/.git'")
        Files.readString(stageproto, StandardCharsets.UTF_8) should include("--exclude '/repository'")
        val installedstage = fixture.project.resolve("etc/website-stage.sh")
        Files.copy(stageproto, installedstage, StandardCopyOption.REPLACE_EXISTING)
        installedstage.toFile.setExecutable(true, true) shouldBe true
        _initialize_git(fixture.staging)

        When("the real stage workflow runs against a Git directory and then a Git worktree")
        CozyBok.runWorkflow(CozyBok.WorkflowConfig.create("stage", List(fixture.project.toString)), CozyBok.ProcessRunner)
        CozyBok.ProcessRunner.run(Vector("git", "status", "--short"), fixture.staging)
        Files.isDirectory(fixture.staging.resolve(".git"), LinkOption.NOFOLLOW_LINKS) shouldBe true
        val worktree = fixture.root.resolve("target/staging-worktree")
        CozyBok.ProcessRunner.run(Vector("git", "worktree", "add", worktree.toString), fixture.staging)
        _write_project_config(fixture, worktree)
        val stale = worktree.resolve("repository/stale-artifact.txt")
        Files.createDirectories(stale.getParent)
        Files.writeString(stale, "preserve repository non-delete semantics\n", StandardCharsets.UTF_8)
        CozyBok.runWorkflow(CozyBok.WorkflowConfig.create("stage", List(fixture.project.toString)), CozyBok.ProcessRunner)
        CozyBok.ProcessRunner.run(Vector("git", "status", "--short"), worktree)

        Then("website output is directly rooted at staging, article HTML retains exact-locale video URLs, Git directory and worktree-file metadata survive, and all four artifacts resolve below repository")
        Files.isRegularFile(worktree.resolve(".git"), LinkOption.NOFOLLOW_LINKS) shouldBe true
        Files.isRegularFile(stale, LinkOption.NOFOLLOW_LINKS) shouldBe true
        val records = _integrity_records(context.publicationPath.resolve("publication.json"))
        records should have size 4
        records.foreach { record =>
          val publicpath = record._1
          val repositorypath = record._2
          val expected = fixture.repository.resolve(repositorypath)
          val staged = worktree.resolve(publicpath.stripPrefix("/"))
          publicpath.stripPrefix("/") shouldBe s"repository/$repositorypath"
          staged.normalize.startsWith(worktree.resolve("repository").normalize) shouldBe true
          Files.readAllBytes(staged).toVector shouldBe Files.readAllBytes(expected).toVector
          _sha256(staged) shouldBe record._3
          _sha256(expected) shouldBe record._3
          Files.isRegularFile(staged, LinkOption.NOFOLLOW_LINKS) shouldBe true
          val mediatype = if (repositorypath.endsWith(".png")) "image/png" else "video/mp4"
          _media_signature(expected, mediatype)
          _media_signature(staged, mediatype)
        }
        val bundleentries = _canonical_bundle_entries(context.publicationPath.resolve("publication.json")).map { entry =>
          entry.path -> entry.metadata
        }.toMap
        Vector("en", "ja").foreach { locale =>
          val integritypath = s"metadata/article-media-integrity/development-process/example/$locale/infographic.json"
          val integrity = bundleentries.getOrElse(integritypath, fail(s"Missing infographic integrity entry: $integritypath"))
          integrity.hcursor.downField("schema").as[String].toOption shouldBe Some("cozy.article-media-integrity.v1")
          integrity.hcursor.downField("locale").as[String].toOption shouldBe Some(locale)
          integrity.hcursor.downField("role").as[String].toOption shouldBe Some("infographic")
          integrity.hcursor.downField("mediaType").as[String].toOption shouldBe Some("image/png")
          val publicpath = integrity.hcursor.downField("publicPath").as[String].toOption.getOrElse(fail(s"Missing infographic publicPath: $integritypath"))
          val repositorypath = integrity.hcursor.downField("repositoryPath").as[String].toOption.getOrElse(fail(s"Missing infographic repositoryPath: $integritypath"))
          val integritysha = integrity.hcursor.downField("sha256").as[String].toOption.getOrElse(fail(s"Missing infographic SHA-256: $integritypath"))
          val provenance = integrity.hcursor.downField("provenance")
          provenance.downField("kind").as[String].toOption shouldBe Some("media-package")
          val descriptor = provenance.downField("descriptor").as[String].toOption.getOrElse(fail(s"Missing infographic descriptor provenance: $integritypath"))
          val buildmanifest = provenance.downField("buildManifest").as[String].toOption.getOrElse(fail(s"Missing infographic build manifest provenance: $integritypath"))
          val resourceid = provenance.downField("resourceId").as[String].toOption.getOrElse(fail(s"Missing infographic resource ID provenance: $integritypath"))
          descriptor shouldBe s"src/main/doxsite/media/$locale/media.yaml"
          buildmanifest shouldBe s"src/main/doxsite/media/$locale/target/cozy-media/manifest.json"
          integrity.hcursor.downField("artifact").downField("identity").as[String].toOption shouldBe Some(resourceid)
          records should contain((publicpath, repositorypath, integritysha))

          val descriptorpath = fixture.project.resolve(descriptor)
          Files.isRegularFile(descriptorpath, LinkOption.NOFOLLOW_LINKS) shouldBe true
          val descriptormap = _yaml_map(_read_file(descriptorpath))
          val resources = descriptormap.get("resources") match {
            case Some(value: Vector[_]) => value.collect { case item: Map[_, _] => item.asInstanceOf[Map[String, Any]] }
            case _ => fail(s"Missing infographic resources: $descriptor")
          }
          val matchingresources = resources.filter(_.get("id").map(_.toString).contains(resourceid))
          matchingresources should have size 1
          val resource = matchingresources.head
          resource.get("id").map(_.toString) shouldBe Some(resourceid)
          resource.get("language").map(_.toString) shouldBe Some(locale)
          resource.get("role").map(_.toString) shouldBe Some("detailed-infographic")
          resource.get("kind").map(_.toString) shouldBe Some("image")
          resource.get("build").map(_.toString) shouldBe Some("prebuilt")
          val sourcepath = resource.get("source").map(_.toString).getOrElse(fail(s"Missing infographic prebuilt source: $descriptor"))
          val outputpath = resource.get("output").map(_.toString).getOrElse(fail(s"Missing infographic prebuilt output: $descriptor"))
          sourcepath shouldBe outputpath
          sourcepath should startWith("target/cozy-media/")
          val publicationmap = resource.get("publications") match {
            case Some(value: Map[_, _]) => value.asInstanceOf[Map[String, Any]]
            case _ => fail(s"Missing infographic publications: $descriptor")
          }
          publicationmap.get("site").map(_.toString) shouldBe Some(repositorypath)
          val prebuilt = descriptorpath.getParent.resolve(sourcepath).normalize
          prebuilt.startsWith(descriptorpath.getParent.normalize) shouldBe true
          Files.isRegularFile(prebuilt, LinkOption.NOFOLLOW_LINKS) shouldBe true

          val manifestpath = fixture.project.resolve(buildmanifest)
          Files.isRegularFile(manifestpath, LinkOption.NOFOLLOW_LINKS) shouldBe true
          val manifest = io.circe.parser.parse(_read_file(manifestpath)).toOption.getOrElse(fail(s"Invalid infographic build manifest: $buildmanifest"))
          manifest.hcursor.downField("schema").as[String].toOption shouldBe Some("cozy.media.v1")
          val manifestresources = manifest.hcursor.downField("resources").as[Vector[io.circe.Json]].toOption.getOrElse(fail(s"Missing infographic build manifest resources: $buildmanifest"))
          val matchingmanifestresources = manifestresources.filter(_.hcursor.downField("id").as[String].toOption.contains(resourceid))
          matchingmanifestresources should have size 1
          val manifestresource = matchingmanifestresources.head
          manifestresource.hcursor.downField("path").as[String].toOption shouldBe Some(sourcepath)
          manifestresource.hcursor.downField("sha256").as[String].toOption shouldBe Some(integritysha)
          _sha256(prebuilt) shouldBe integritysha
        }
        val expectedmanifestpaths = Vector(
          "metadata/video/article-media-bilingual-example-en-video/1.0.0/manifest.json",
          "metadata/video/article-media-bilingual-example-ja-video/1.0.0/manifest.json"
        )
        val expectedregistrypaths = Vector(
          "metadata/artifacts/repository/article-media-bilingual-example-en-video.json",
          "metadata/artifacts/repository/article-media-bilingual-example-ja-video.json"
        )
        expectedmanifestpaths.foreach(path => bundleentries.keySet should contain(path))
        expectedregistrypaths.foreach(path => bundleentries.keySet should contain(path))
        Vector(
          "en" -> "article-media-bilingual-example-en-video",
          "ja" -> "article-media-bilingual-example-ja-video"
        ).foreach { case (locale, videoname) =>
          val integritypath = s"metadata/article-media-integrity/development-process/example/$locale/video.json"
          val integrity = bundleentries.getOrElse(integritypath, fail(s"Missing video integrity entry: $integritypath"))
          val repositorypath = integrity.hcursor.downField("repositoryPath").as[String].toOption.getOrElse(fail(s"Missing video repositoryPath: $integritypath"))
          val integritysha = integrity.hcursor.downField("sha256").as[String].toOption.getOrElse(fail(s"Missing video SHA-256: $integritypath"))
          integrity.hcursor.downField("mediaType").as[String].toOption shouldBe Some("video/mp4")
          val provenance = integrity.hcursor.downField("provenance")
          val manifestpath = provenance.downField("videoManifest").as[String].toOption.getOrElse(fail(s"Missing video manifest provenance: $integritypath"))
          val registrypath = provenance.downField("repositoryRegistry").as[String].toOption.getOrElse(fail(s"Missing video registry provenance: $integritypath"))
          expectedmanifestpaths should contain(manifestpath)
          expectedregistrypaths should contain(registrypath)
          val warehousepath = s"repository/$repositorypath"
          val manifest = bundleentries.getOrElse(manifestpath, fail(s"Missing video manifest entry: $manifestpath"))
          val manifestartifact = manifest.hcursor.downField("artifact")
          manifest.hcursor.downField("schema").as[String].toOption shouldBe Some("cozy.publish-project.v1")
          manifest.hcursor.downField("type").as[String].toOption shouldBe Some("video-registry-manifest")
          manifest.hcursor.downField("video").downField("name").as[String].toOption shouldBe Some(videoname)
          manifest.hcursor.downField("video").downField("version").as[String].toOption shouldBe Some("1.0.0")
          manifestartifact.downField("warehousePath").as[String].toOption shouldBe Some(warehousepath)
          manifestartifact.downField("repositoryPublicPath").as[String].toOption shouldBe Some(warehousepath)
          manifestartifact.downField("sha256").as[String].toOption shouldBe Some(integritysha)
          val registry = bundleentries.getOrElse(registrypath, fail(s"Missing video registry entry: $registrypath"))
          registry.hcursor.downField("schema").as[String].toOption shouldBe Some("cozy.publish-project.v1")
          registry.hcursor.downField("type").as[String].toOption shouldBe Some("repository-artifact")
          registry.hcursor.downField("project").downField("name").as[String].toOption shouldBe Some(videoname)
          registry.hcursor.downField("project").downField("version").as[String].toOption shouldBe Some("1.0.0")
          val selectedfiles = registry.hcursor.downField("artifact").downField("files").as[Vector[io.circe.Json]].toOption.getOrElse(fail(s"Missing video registry files: $registrypath")).filter { file =>
            file.hcursor.downField("type").as[String].toOption.contains("video") &&
              file.hcursor.downField("warehousePath").as[String].toOption.contains(warehousepath)
          }
          selectedfiles should have size 1
          val selectedfile = selectedfiles.head
          selectedfile.hcursor.downField("sha256").as[String].toOption shouldBe Some(integritysha)
          val artifact = fixture.repository.resolve(repositorypath)
          Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS) shouldBe true
          _sha256(artifact) shouldBe integritysha
          Files.size(artifact) shouldBe selectedfile.hcursor.downField("size").as[Long].toOption.getOrElse(fail(s"Missing video registry size: $registrypath"))
        }
        val stagedenglish = _read_file(worktree.resolve("en/development-process/example.html"))
        val stagedjapanese = _read_file(worktree.resolve("ja/development-process/example.html"))
        stagedenglish should include("/repository/video/article-media-bilingual-example-en-video/1.0.0/article-media-bilingual-example-en-video-1.0.0.mp4")
        stagedjapanese should include("/repository/video/article-media-bilingual-example-ja-video/1.0.0/article-media-bilingual-example-ja-video-1.0.0.mp4")
      }
    }
  }

  private final class Fixture(val root: Path, val project: Path, val source: Path, val publication: Path, val repository: Path, val website: Path, val staging: Path)

  private def _smartdox_context: SmartDoxContext = {
    val environment = Environment.createJaJp()
    val cliconfig = CliConfig.buildJaJp()
    new SmartDoxContext(environment, SmartDoxConfig(cliconfig), environment.contextFoundation)
  }

  private def _with_fixture[A](f: Fixture => A): A = {
    val testroot = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize.resolve("target/cozy-article-media-bok-acceptance")
    Files.createDirectories(testroot)
    val root = Files.createTempDirectory(testroot, "fixture-")
    val project = root.resolve("project")
    val source = project.resolve("src/main/doxsite")
    val publication = project.resolve("src/main/publication")
    val repository = root.resolve("repository")
    val website = project.resolve("website.d")
    val staging = root.resolve("target/staging")
    val resource = Paths.get("src/test/resources/cozy/publication/article-media-bilingual-bok").toAbsolutePath.normalize
    val fixture = new Fixture(root, project, source, publication, repository, website, staging)
    try {
      Files.createDirectories(project)
      CozyBok.create(CozyBok.CreateConfig(project, "Article Media Bilingual BoK", "https://example.invalid/article-media-bilingual-bok", "ja", CozyBok.ProjectFilePolicy.Overwrite))
      _delete(source)
      _copy_tree(resource.resolve("src/main/doxsite"), source)
      _copy_tree(resource.resolve("publication"), publication)
      _copy_tree(resource.resolve("repository"), repository)
      _write_project_config(fixture, staging)
      f(fixture)
    } finally {
      _delete(root)
    }
  }

  private def _write_project_config(fixture: Fixture, staging: Path): Unit = {
    val config = fixture.project.resolve("conf/cozy/config.yaml")
    Files.createDirectories(config.getParent)
    Files.writeString(
      config,
      s"""bok:
         |  publication: src/main/publication
         |  repository: ../repository
         |  website: website.d
         |  website-staging: ${fixture.project.relativize(staging).toString.replace('\\', '/')}
         |  workflow:
         |    stage:
         |      command: "etc/website-stage.sh"
         |""".stripMargin,
      StandardCharsets.UTF_8
    )
  }

  private def _initialize_git(path: Path): Unit = {
    Files.createDirectories(path)
    CozyBok.ProcessRunner.run(Vector("git", "init"), path)
    CozyBok.ProcessRunner.run(Vector("git", "config", "user.email", "acceptance@example.invalid"), path)
    CozyBok.ProcessRunner.run(Vector("git", "config", "user.name", "Article Media Acceptance"), path)
    Files.writeString(path.resolve("seed.txt"), "seed\n", StandardCharsets.UTF_8)
    CozyBok.ProcessRunner.run(Vector("git", "add", "seed.txt"), path)
    CozyBok.ProcessRunner.run(Vector("git", "commit", "-m", "seed"), path)
  }

  private def _integrity_records(publication: Path): Vector[(String, String, String)] = {
    val value = io.circe.parser.parse(new String(Files.readAllBytes(publication), StandardCharsets.UTF_8)).toOption.get
    value.hcursor.downField("entries").as[Vector[io.circe.Json]].toOption.toVector.flatten.flatMap { entry =>
      val metadata = entry.hcursor.downField("metadata")
      for {
        schema <- metadata.downField("schema").as[String].toOption if schema == "cozy.article-media-integrity.v1"
        publicpath <- metadata.downField("publicPath").as[String].toOption
        repositorypath <- metadata.downField("repositoryPath").as[String].toOption
        sha256 <- metadata.downField("sha256").as[String].toOption
      } yield (publicpath, repositorypath, sha256)
    }.toVector
  }

  private final class BundleEntry(val path: String, val key: String, val metadata: io.circe.Json)

  private def _canonical_bundle_entries(publication: Path): Vector[BundleEntry] = {
    val value = io.circe.parser.parse(_read_file(publication)).toOption.getOrElse(fail(s"Invalid publication bundle: $publication"))
    value.hcursor.downField("schema").as[String].toOption shouldBe Some("cozy.publish-project.v1")
    value.hcursor.downField("type").as[String].toOption shouldBe Some("publication-bundle")
    val entries = value.hcursor.downField("entries").as[Vector[io.circe.Json]].toOption.getOrElse(fail(s"Publication bundle entries are invalid: $publication"))
    entries should have size 9
    val parsed = entries.map { entry =>
      val path = entry.hcursor.downField("path").as[String].toOption.getOrElse(fail(s"Publication bundle entry path is missing: $publication"))
      val key = entry.hcursor.downField("key").as[String].toOption.getOrElse(fail(s"Publication bundle entry key is missing: $publication"))
      val metadata = entry.hcursor.downField("metadata").focus.getOrElse(fail(s"Publication bundle entry metadata is missing: $publication"))
      new BundleEntry(path, key, metadata)
    }
    val paths = parsed.map(_.path)
    val keys = parsed.map(_.key)
    paths.distinct should have size parsed.size
    keys.distinct should have size parsed.size
    paths shouldBe paths.sorted
    keys shouldBe keys.sorted
    parsed.map(entry => _bundle_category(entry.metadata)).groupBy(identity).map { case (category, values) => category -> values.size } shouldBe Map(
      "article-media-publication" -> 1,
      "integrity" -> 4,
      "video-registry-manifest" -> 2,
      "repository-artifact" -> 2
    )
    parsed
  }

  private def _bundle_category(metadata: io.circe.Json): String =
    metadata.hcursor.downField("schema").as[String].toOption match {
      case Some("cozy.article-media-integrity.v1") => "integrity"
      case _ => metadata.hcursor.downField("type").as[String].toOption.getOrElse(fail("Publication bundle entry category is missing"))
    }

  private def _exported_bytes(realm: Realm, root: Path): Map[String, Vector[Byte]] = {
    _delete(root)
    Files.createDirectories(root)
    realm.export(root.toFile)
    val stream = Files.walk(root)
    try stream.iterator.asScala.filter(Files.isRegularFile(_)).map(path => root.relativize(path).toString -> Files.readAllBytes(path).toVector).toMap
    finally stream.close()
  }

  private def _copy_tree(source: Path, target: Path): Unit = {
    if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
      Files.createDirectories(target)
      val stream = Files.list(source)
      try stream.iterator.asScala.foreach(path => _copy_tree(path, target.resolve(path.getFileName.toString)))
      finally stream.close()
    } else {
      Files.createDirectories(target.getParent)
      Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private def _string(realm: Realm, path: String): String =
    realm.get(path).collect { case value: StringData => value.string }.getOrElse(fail(s"Missing generated content: $path"))

  private def _notice(realm: Realm, directory: String, uri: String): Map[String, Any] = {
    (1 to 99).flatMap { index =>
      realm.get(f"$directory/notice$index%02d.yaml").collect { case value: StringData => _yaml_map(value.string) }
    }.find(_.get("notice.uri").contains(uri)).getOrElse(fail(s"Missing Notice for URI: $uri"))
  }

  private def _media(notice: Map[String, Any]): Option[Map[String, Any]] =
    notice.get("notice.media").collect { case value: Map[_, _] => value.asInstanceOf[Map[String, Any]] }

  private def _map_value(value: Any, key: String): Option[String] =
    value match {
      case map: Map[_, _] => map.asInstanceOf[Map[String, Any]].get(key).map(_.toString)
      case _ => None
    }

  private def _yaml_map(yaml: String): Map[String, Any] =
    _yaml_value(new Yaml().load[Any](yaml)).asInstanceOf[Map[String, Any]]

  private def _yaml_value(value: Any): Any =
    value match {
      case map: java.util.Map[_, _] => map.asScala.map { case (key, item) => key.toString -> _yaml_value(item) }.toMap
      case list: java.util.List[_] => list.asScala.map(_yaml_value).toVector
      case other => other
    }

  private def _read_file(path: Path): String = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)

  private def _sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.digest(Files.readAllBytes(path)).map(value => f"${value & 0xff}%02x").mkString
  }

  private def _media_signature(path: Path, mediatype: String): Unit = {
    val bytes = Files.readAllBytes(path)
    mediatype match {
      case "image/png" =>
        bytes.toVector.take(8) shouldBe Vector(137.toByte, 80.toByte, 78.toByte, 71.toByte, 13.toByte, 10.toByte, 26.toByte, 10.toByte)
        bytes.toVector.slice(12, 16) shouldBe "IHDR".getBytes(StandardCharsets.US_ASCII).toVector
      case "video/mp4" =>
        val initial = bytes.toVector.take(64)
        initial.sliding(4).exists(_ == "ftyp".getBytes(StandardCharsets.US_ASCII).toVector) shouldBe true
      case _ =>
        fail(s"Unsupported media signature type: $mediatype")
    }
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val stream = Files.walk(path)
      try stream.iterator.asScala.toVector.sortBy(value => (-value.getNameCount, value.toString)).foreach(Files.deleteIfExists)
      finally stream.close()
    }
}
