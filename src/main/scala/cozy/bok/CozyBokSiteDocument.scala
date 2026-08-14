package cozy.bok

import org.goldenport.RAISE
import org.goldenport.cli.{Request => CliRequest}
import org.goldenport.cli.spec
import cozy.bok.scenario.ScenarioMetadata
import cozy.bok.BibliographyEntry._
import cozy.config.CozyProjectYamlConfig
import cozy.publication.{CozyArticleMediaBuildContext, CozyArticleMediaInfographicCommand, CozyArticleMediaInfographicEvidence, CozyArticleMediaVideoCommand}
import cozy.video.{CozyVideo, CozyVideoPublisher}
import org.smartdox.{Body, Document, Dox}
import org.smartdox.parser.Dox2Parser
import org.smartdox.transformers.Dox2HtmlTransformer
import org.smartdox.generator.{Context => SmartDoxContext}
import org.smartdox.metadata.DocumentMetaData
import org.goldenport.i18n.I18NContext
import java.net.URLEncoder
import java.time.{Instant, LocalDate, LocalDateTime, YearMonth, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import java.util.zip.{ZipEntry, ZipFile, ZipInputStream, ZipOutputStream}
import scala.collection.JavaConverters._
import scala.util.matching.Regex
import scala.util.control.NonFatal
import scala.sys.process._
import io.circe.{Decoder, HCursor, Json}
import io.circe.parser
import io.circe.syntax._

/*
 * @since   Aug. 14, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */

private[cozy] trait CozyBokSiteDocument {
  self: CozyBokImplementation.type =>
  private val _source_document_suffixes = Vector(".dox", ".md", ".markdown")

  private[bok] def _source_document(dir: Path, stem: String): Option[Path] =
    _source_document_suffixes.
      map(suffix => dir.resolve(s"${stem}${suffix}")).
      find(Files.isRegularFile(_))

  private[bok] def _is_source_document(path: Path): Boolean =
    _source_document_suffixes.exists(path.getFileName.toString.endsWith)

  private[bok] def _is_markdown_source_document(path: Path): Boolean = {
    val name = path.getFileName.toString.toLowerCase(java.util.Locale.ROOT)
    name.endsWith(".md") || name.endsWith(".markdown")
  }

  private[bok] def _is_index_source_document(path: Path): Boolean = {
    val name = path.getFileName.toString
    _source_document_suffixes.exists(suffix => name == s"index${suffix}")
  }

  private[bok] def _source_document_html_href(rel: String): String =
    _source_document_suffixes.
      find(rel.endsWith).
      map(suffix => rel.dropRight(suffix.length) + ".html").
      getOrElse(rel + ".html")

  private[bok] def _source_document_stem(name: String): String =
    _source_document_suffixes.
      find(name.endsWith).
      map(suffix => name.dropRight(suffix.length)).
      getOrElse(name)

  private[bok] def _source_narrative_section(config: BuildConfig, path: Option[Path], locale: String): String = {
    val body = _source_document_fragment(config, path, locale).
      map(x => _effective_document_fragment_body(x.bodyhtml)).
      getOrElse("")
    if (body.isEmpty)
      ""
    else
      s"""<div class="bok-narrative-corner" id="narrative">
         |  <div class="sectionbody">
         |    ${body}
         |  </div>
         |</div>""".stripMargin
  }

  private def _effective_document_fragment_body(body: String): String = {
    val trimmed = body.trim
    if (trimmed.isEmpty)
      ""
    else if (trimmed.toLowerCase(Locale.ROOT).startsWith("<html"))
      _article_body(trimmed).map(_empty_html_to_blank).getOrElse(_empty_html_to_blank(trimmed))
    else
      _empty_html_to_blank(trimmed)
  }

  private def _article_body(html: String): Option[String] = {
    val pattern = Pattern.compile("(?is)<article\\b[^>]*>(.*)</article>")
    val matcher = pattern.matcher(html)
    if (matcher.find())
      Some(matcher.group(1).trim)
    else
      None
  }

  private def _empty_html_to_blank(html: String): String =
    if (_is_effectively_empty_html(html))
      ""
    else
      html

  private def _is_effectively_empty_html(html: String): Boolean = {
    val text = html.
      replaceAll("(?is)<script\\b[^>]*>.*?</script>", "").
      replaceAll("(?is)<style\\b[^>]*>.*?</style>", "").
      replaceAll("(?is)<[^>]+>", "").
      replace("&nbsp;", " ").
      trim
    text.isEmpty
  }

  private[bok] def _source_document_fragment(config: BuildConfig, path: Option[Path], locale: String): Option[DocumentFragment] =
    for {
      source <- path.filter(Files.isRegularFile(_))
      rel <- _source_document_relative(config, source)
      index <- _document_fragment_index(config)
      fragment <- index.get(rel, locale).orElse(_source_document_fragment_by_public_path(index, rel, locale))
    } yield fragment

  private def _source_document_fragment_by_public_path(index: DocumentFragmentIndex, sourcepath: String, locale: String): Option[DocumentFragment] =
    _source_document_public_path(sourcepath).flatMap(index.get(_, locale))

  private def _source_document_public_path(sourcepath: String): Option[String] =
    _source_document_suffixes.find(sourcepath.endsWith).map { suffix =>
      sourcepath.stripSuffix(suffix) + ".html"
    }

  private def _source_document_relative(config: BuildConfig, source: Path): Option[String] =
    try {
      Some(config.sourcepath.relativize(source).toString.replace(java.io.File.separatorChar, '/'))
    } catch {
      case NonFatal(_) => None
    }

  private[bok] def _source_narrative_html(path: Option[Path], locale: String): String =
    path.filter(Files.isRegularFile(_)).map { source =>
      val content = Files.readString(source, StandardCharsets.UTF_8)
      _source_narrative_html(content, source.toString, locale)
    }.getOrElse("")

  private[bok] def _source_narrative_html(path: Path, locale: String): String =
    _source_narrative_html(Option(path), locale)

  private[bok] def _source_narrative_html(content: String, name: String, locale: String): String = {
    val dox = Dox2Parser.parseWithFilename(Dox2Parser.Config.default, name, content)
    val rule = Dox2HtmlTransformer.Rule(isDocument = false, sectionBaseNumber = Some(2), isDefaultCss = false)
    val body = _source_narrative_dox(dox)
    _rendered_body_fragment(Dox2HtmlTransformer(SmartDoxContext.create(), rule).transform(body).take).trim
  }

  private def _source_narrative_dox(dox: Dox): Dox =
    dox match {
      case m: Document => Dox.toDox(m.body.contents)
      case m: Body => Dox.toDox(m.contents)
      case m => m
    }

  private[bok] def _ui(locale: String, key: String): String =
    _ui_context(_to_locale(locale)).message(key)

  private[bok] def _uif(locale: String, key: String, args: Any*): String =
    _ui_context(_to_locale(locale)).message(key, args: _*)

  private[bok] def _to_locale(value: String): Locale =
    Locale.forLanguageTag(value.replace('_', '-'))

  private def _ui_context(locale: Locale): I18NContext = {
    val bundle = I18NContext.loadResourceBundle(_ui_resource_base, locale, _ui_resource_config)
    I18NContext.default.copy(locale = locale, resourceBundle = bundle)
  }


  private[bok] def _cozy_config(): String =
    _cozy_config(None)

  private[bok] def _cozy_config(config: Option[CreateConfig]): String =
    s"""cozy:
       |  docker-image: ${_default_docker_image}
       |
       |bok:
       |  source: src/main/doxsite
       |  publication: src/main/publication
       |  repository: repository
       |  strategy: production
       |  website: website.d
       |  website-staging: ${config.map(_default_website_staging).getOrElse("../website-staging")}
       |  backup:
       |    enabled: false
       |    dir: website.backup
       |    compressed: true
       |  antora: antora.d
       |  doxsite: doxsite.d
       |  ui-bundle: src/main/antora-ui/build/ui-bundle.zip
       |  rdf:
       |    merge-publication-artifacts: true
       |  video:
       |    enabled: true
       |    force: false
       |  arcadia:
       |    enabled: false
       |    source: src/main/arcadiasite
       |  direct-assets:
       |    enabled: false
       |    items: []
       |  workflow:
       |    stage:
       |      # Copy etc/website-stage.sh.proto to etc/website-stage.sh and configure it.
       |      command: ""
       |    upload:
       |      # Copy etc/website-upload.sh.proto to etc/website-upload.sh and configure it.
       |      command: ""
       |""".stripMargin

  private[bok] def _readme(config: CreateConfig): String =
    s"""# ${config.name}
       |
       |This project is a SmartDox BoK source project for ${config.name}.
       |
       |- Operation URL: ${config.url}
       |- Main language: ${config.language}
       |- Site source: `src/main/doxsite`
       |- Generated public site files and publication repositories are intentionally outside this source scaffold.
       |
       |Category structure is defined by directories that contain `category.yaml`.
       |""".stripMargin

  private[bok] def _structure(config: CreateConfig): String =
    s"""# BoK Structure
       |
       |`src/main/doxsite` is the cozy-generated SmartDox source tree.
       |`site.conf` holds site metadata such as name, URL, language, license, navigation mode, and output mode.
       |
       |Directories with `category.yaml` are BoK categories.
       |`knowledgehub/` contains KnowledgeHub framework articles.
       |`book-knowledge/` contains Book/RDF/embedding articles.
       |`glossary/` contains terms used for automatic glossary linking.
       |`history/` contains operation history entries.
       |`manual/` contains Cozy BoK operation guidance.
       |`rdf/` contains minimal RDF and JSON-LD machine-readable placeholders.
       |`src/main/antora-ui/build/ui-bundle.zip` is a minimal local Antora UI bundle for offline BoK generation.
       |`assets/css/` contains restrained reading CSS only.
       |
       |Only source files, metadata, RDF seeds, glossary terms, and minimal CSS are generated here.
       |""".stripMargin

  private[bok] def _site_conf(config: CreateConfig): String =
    s"""site {
       |  metadata {
       |    id = "${_site_identifier(config.name)}"
       |    name = "${config.name}"
       |    url = "${config.url}"
       |    in_language = ["${config.language}"]
       |    license = "CC-BY-SA-4.0"
       |    # Select one of: aurora, lagoon, meadow, ocean, ember, slate
       |    dashboard_color_group = "aurora"
       |    vision = "Build a shared knowledge base for ${config.name}."
       |    goals = [
       |      {
       |        title = "Organize concepts and technology knowledge"
       |        subgoals = ["Maintain category dashboards"]
       |      },
       |      {
       |        title = "Keep knowledge searchable and reusable"
       |        subgoals = ["Maintain glossary and history"]
       |      }
       |    ]
       |  }
       |  navigation {
       |    mode = "category"
       |  }
       |  output {
       |    locale_mode = "single_locale_root"
       |    default_locale = "${config.language}"
       |  }
       |  header {
       |    language_toggle = false
       |  }
       |}
       |
       |output.scope.policy = home_only
       |""".stripMargin

  private def _today: String =
    LocalDate.now.toString

  private[bok] def _site_index(config: CreateConfig): String =
    s"""Home
       |======
       |
       |${_dox_head(config.name, _site_index_brief(config))}
       |
       |# Overview
       |
       |${_site_index_overview(config)}
       |""".stripMargin

  private def _site_index_brief(config: CreateConfig): String =
    if (_is_japanese(config.language))
      s"${config.name} のカテゴリ、用語、RDFから知識を探索するための短い導入。"
    else
      s"A short introduction for exploring ${config.name} through categories, terms, and RDF."

  private def _site_index_overview(config: CreateConfig): String =
    if (_is_japanese(config.language))
      s"${config.name}は、カテゴリ、用語、RDFのつながりから知識を探索するためのBoKです。"
    else
      s"${config.name} is a BoK site for exploring knowledge through categories, terms, and RDF relationships."

  private def _is_japanese(language: String): Boolean =
    language.toLowerCase(Locale.ROOT).startsWith("ja")

  private[bok] def _category(
    name: String,
    title: String,
    description: String,
    purpose: BokPurpose = BokPurpose.empty
  ): String =
    s"""name: ${name}
       |title: ${title}
       |description:
       |  en: ${description}
       |  ja: ${description}
       |${_purpose_yaml(purpose)}
       |""".stripMargin

  private[bok] def _category_index(
    category: String,
    title: String,
    purpose: String,
    articles: Vector[CategoryArticle] = Vector.empty,
    terms: Vector[CategoryTerm] = Vector.empty
  ): String =
    s"""${title}
       |======
       |
       |${_dox_head(title, purpose)}
       |
       |# Overview
       |
       |${purpose}
       |""".stripMargin

  private def _dox_head(headline: String, brief: String): String =
    s"""# HEAD
       |
       |status=work-in-progress
       |published_at=${_today}
       |
       |${_dox_metadata_section("HEADLINE", headline)}
       |
       |${_dox_metadata_section("BRIEF", brief)}""".stripMargin

  private def _dox_metadata_section(name: String, body: String): String =
    s"""## ${name}
       |
       |${body}""".stripMargin

  private def _purpose_yaml(purpose: BokPurpose): String =
    if (purpose.isEmpty)
      ""
    else
      Vector(
        purpose.vision.map(x => s"vision: ${_yaml_quote(x)}"),
        if (purpose.goals.nonEmpty) Some(_yaml_goal_tree_block("goals", purpose.goals)) else None,
        if (purpose.flatGoals.nonEmpty) Some(_yaml_list_block("goals", purpose.flatGoals)) else None,
        if (purpose.flatSubgoals.nonEmpty) Some(_yaml_list_block("subgoals", purpose.flatSubgoals)) else None
      ).flatten.mkString("", "\n", "\n")

  private def _yaml_goal_tree_block(key: String, values: Vector[BokGoal]): String =
    values.filterNot(_.isEmpty).map { goal =>
      val subgoals =
        if (goal.subgoals.isEmpty)
          ""
        else
          goal.subgoals.map(x => s"      - ${_yaml_quote(x)}").mkString("\n    subgoals:\n", "\n", "")
      s"  - title: ${_yaml_quote(goal.title)}${subgoals}"
    }.mkString(s"${key}:\n", "\n", "")

  private def _yaml_list_block(key: String, values: Vector[String]): String =
    values.map(x => s"  - ${_yaml_quote(x)}").mkString(s"${key}:\n", "\n", "")

  private def _yaml_quote(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private[bok] def _dashboard_bar_width(value: Int, total: Int): Int =
    if (total <= 0)
      8
    else
      math.max(8, math.round(value.toDouble / total.toDouble * 100.0).toInt)

  private def _glossary_index(): String =
    s"""用語集
      |======
      |
      |${_dox_head("用語集", "BoK全体で共有する用語と概念のDashboard。")}
      |
      |# Dashboard
      |
      |用語集はBoK全体の語彙、カテゴリ横断の概念、ドメイン固有語を集約します。
      |
      |## Quick Links
      |
      |- <a href="../index.html">BoK Home</a>
      |- <a href="../history/index.html">History</a>
      |
      |## Term Groups
      |
      |- Terms in categories: `glossary/<category>/` にあるカテゴリ別用語。
      |- Shared terms: この用語集直下に置くBoK横断用語。
      |
      |## Operation Notes
      |
      |用語はSmartDoxの自動リンクとRDF/JSON-LD連携の基盤です。新しいカテゴリ用語を追加した場合は、カテゴリトップページと用語集Dashboardの両方から辿れるようにします。
      |""".stripMargin

  private[bok] def _history_index(): String =
    s"""History
      |=======
      |
      |${_dox_head("History", "BoK運用、更新履歴、公開履歴のDashboard。")}
      |
      |# Dashboard
      |
      |HistoryはBoKの変更、公開、運用イベントを集約するページです。
      |
      |## Quick Links
      |
      |- <a href="../index.html">BoK Home</a>
      |- <a href="../glossary/index.html">Glossary</a>
      |
      |## Timeline
      |
      |- 2026: Cozy BoK source and site operations started.
      |
      |## Operation Notes
      |
      |公開、構成変更、カテゴリ追加、重要な用語変更はここに記録します。
      |""".stripMargin

  private[bok] def _manual_index(locale: String): String =
    if (_is_japanese(locale))
      s"""BoK Manual
         |==========
         |
         |${_dox_head("BoK Manual", "Cozy BoK source and site operation manual.")}
         |
         |# Dashboard
         |
         |このManualは、BoK標準運用の手順と責務分担をまとめる技術マニュアルです。プロジェクト固有のルールは Local Rules に記録します。
         |
         |## Quick Links
         |
         |- <a href="../index.html">BoK Home</a>
         |- <a href="../glossary/index.html">Glossary</a>
         |- <a href="../history/index.html">History</a>
         |- <a href="local-rules.html">Local Rules</a>
         |
         |## Basic Operations
         |
         |- `cozy bok create --save <dir>`: BoK source scaffoldを作成します。
         |- `cozy bok create-category <name> --project <dir>`: カテゴリDashboard、記事seed、用語seedを追加します。
         |- `cozy bok doctor <dir>`: BoK source treeを検査します。
         |- `cozy bok build <dir> --strategy preview`: SmartDox/Antoraを使って `website.d` を生成します。
         |- `cozy bok preview <dir> --port <port>`: 生成済み `website.d` をローカルWebサーバーで確認します。
         |- `cozy bok publish <dir> --dry-run`: 公開前の計画と副作用境界を確認します。
         |
         |## Source Document Formats
         |
         |BoKのsource文書形式はGitHub MarkdownとSmartDoxです。一般のKnowledge ContributorにはGitHub Markdownを推奨します。記事本文を通常のMarkdownとして書けるため、GitHub上の編集、レビュー、Pull Requestとの相性が良いからです。
         |
         |BoKのフル機能を使いたい上級者にはSmartDoxを推奨します。SmartDoxはBoK metadata、用語連携、RDF連携、SmartDox固有の構造化表現を扱えます。
         |
         |マルチリンガルBoKはSmartDoxのみを対象にします。GitHub Markdownは単一言語の通常記事向けとして扱います。
         |
         |## Glossary Term Classification
         |
         |用語分類は、未分類の候補を確認し、モノ/コトの補助線で整理した上で、BoK内での役割に基づいて用語タイプを決める作業です。
         |
         |1. 未分類の用語を確認します。
         |2. 記事、シナリオ、参考資料、Project/CML情報から、その語がBoK内で何を担うかを確認します。
         |3. 存在として扱う対象は、concept、entity、actor、role、resource、artifactから選びます。
         |4. 起きる、行う、変化する、制御する対象は、event、action、process、task、rule、state、scenarioから選びます。
         |5. 判定に迷う場合は未分類のまま残し、根拠記事や関連用語を先に整備します。
         |6. 分類後は用語ハブで定義、関連記事、RDF接続、Project/CML接続を確認します。
         |
         |## Actor Operations
         |
         |BoK運用では、Knowledge Contributor、BoK管理者、サイト管理者の責務を分けます。Knowledge Contributorは、ある知識のKnowledge OwnerとしてGit sourceを編集します。自分がownerではない知識への修正はPull Requestで提案します。BoK管理者とサイト管理者はPull Requestを境界にレビュー、merge、公開判断を行います。
         |
         |### 知識提供者 / Knowledge Contributor
         |
         |1. 自分がKnowledge Ownerである記事、用語、カテゴリ、RDF seedなどのGit sourceを編集します。
         |2. ownerではない知識への修正は、作業branchで差分を作りPull Requestとして提案します。
         |3. `cozy bok doctor` で構造、メタデータ、リンク、用語、RDFの基本品質を確認します。
         |4. `cozy bok build --strategy preview` と `cozy bok preview` でDashboard、Category Pages、Glossary、Term Hub、RDF Graph、Recent Changesを確認します。
         |5. Git commitし、作業branchをpushします。
         |6. 必要に応じてPull Requestを作成し、該当Knowledge OwnerまたはBoK管理者へレビューを依頼します。
         |
         |### BoK管理者 / BoK Manager
         |
         |1. Pull Requestを受け取り、内容、構造、用語、RDF、整合性をレビューします。
         |2. 必要に応じてPull Request上で修正依頼し、承認後にmainへmergeします。
         |3. `cozy bok publish --dry-run` で公開前のビルド、配備、検証計画を確認します。
         |4. 高品質で一貫性のある知識だけを公開フローへ渡します。
         |
         |### サイト管理者 / Site Administrator
         |
         |1. ホスティング環境、upload設定、secret、アクセス制御を管理します。
         |2. 公開対象の変更はPull Requestで確認できる状態を前提に、stage/upload workflowを運用します。
         |3. 監視、バックアップ、障害対応、キャッシュ削除などのサイト運用を担当します。
         |
         |## Source / Generated Boundary
         |
         |Knowledge Contributorが編集するのはGit sourceだけです。Knowledge Ownerである知識は直接保守し、ownerではない知識はPull Requestで提案します。
         |
         |### 編集するSource
         |
         |- `src/main/doxsite/**/*.dox`
         |- `src/main/doxsite/**/*.md`
         |- `src/main/doxsite/**/category.yaml`
         |- `src/main/doxsite/glossary/**/*.dox`
         |- `src/main/doxsite/rdf/**` when RDF seed is project-owned
         |
         |### 生成物は編集しない
         |
         |- `website.d/`
         |- `doxsite.d/`
         |- `antora.d/`
         |- `target/`
         |- `repository/`
         |
         |## Page Types
         |
         |- Home: BoK全体Dashboard。
         |- Category Dashboard: カテゴリ単位のKPI、記事、用語、運用メモ。
         |- Glossary: BoK全体の語彙Dashboard。
         |- History: BoK運用と公開履歴Dashboard。
         |- Manual: BoK運用手順の入口。
         |
         |## Operation Notes
         |
         |BoKの標準ページはDashboardとして扱い、通常記事とは異なる情報集約ページにします。Glossary、History、Manualはカテゴリ一覧ではなくBoK Consoleとして扱います。
         |Manualは運用手順ページなので、SmartDoxの自動用語リンク対象外です。
         |""".stripMargin
    else
      s"""BoK Manual
         |==========
         |
         |${_dox_head("BoK Manual", "Cozy BoK source and site operation manual.")}
         |
         |# Dashboard
         |
         |This manual describes the standard BoK operation workflow and responsibilities. Project-local rules belong to Local Rules.
         |
         |## Quick Links
         |
         |- <a href="../index.html">BoK Home</a>
         |- <a href="../glossary/index.html">Glossary</a>
         |- <a href="../history/index.html">History</a>
         |- <a href="local-rules.html">Local Rules</a>
         |
         |## Basic Operations
         |
         |- `cozy bok create --save <dir>`: Create a BoK source scaffold.
         |- `cozy bok create-category <name> --project <dir>`: Add a category dashboard, article seed, and term seed.
         |- `cozy bok doctor <dir>`: Inspect the BoK source tree.
         |- `cozy bok build <dir> --strategy preview`: Generate `website.d` through SmartDox and Antora.
         |- `cozy bok preview <dir> --port <port>`: Serve generated `website.d` through a local Web server.
         |- `cozy bok publish <dir> --dry-run`: Verify the publication plan and side-effect boundary.
         |
         |## Source Document Formats
         |
         |BoK source documents can be written in GitHub Markdown or SmartDox. GitHub Markdown is recommended for general Knowledge Contributors because it keeps ordinary article authoring close to GitHub editing, review, and Pull Request workflows.
         |
         |SmartDox is recommended for advanced contributors who need the full BoK feature set: BoK metadata, glossary linkage, RDF linkage, and SmartDox-specific structured authoring.
         |
         |Multilingual BoK authoring is SmartDox-only. GitHub Markdown is treated as the normal single-language article format.
         |
         |## Glossary Term Classification
         |
         |Term classification starts from unclassified candidates, uses mono/koto as an analysis aid, and then assigns a term type based on the term's role in the BoK knowledge space.
         |
         |1. Review unclassified terms.
         |2. Check articles, scenarios, bibliography, and Project/CML metadata to understand the term's role.
         |3. For existence-like terms, choose concept, entity, actor, role, resource, or artifact.
         |4. For terms that happen, are performed, change, or control decisions, choose event, action, process, task, rule, state, or scenario.
         |5. Keep uncertain candidates unclassified until evidence, related articles, or related terms are clearer.
         |6. After classification, verify definition, related articles, RDF links, and Project/CML links in the Term Hub.
         |
         |## Actor Operations
         |
         |BoK operation separates responsibilities among Knowledge Contributors, BoK Managers, and Site Administrators. A Knowledge Contributor is the Knowledge Owner for some knowledge. For knowledge they own, they edit Git source and push a branch. For knowledge they do not own, they propose changes through a Pull Request. BoK Managers and Site Administrators use Pull Requests as the review, merge, and publication decision boundary.
         |
         |### Knowledge Contributor
         |
         |1. Edit Git source for knowledge they own, such as articles, terms, categories, and RDF seeds.
         |2. For knowledge they do not own, prepare the change on a working branch and propose it through a Pull Request.
         |3. Run `cozy bok doctor` to check structure, metadata, links, terms, and RDF basics.
         |4. Run `cozy bok build --strategy preview` and `cozy bok preview` to verify dashboards, category pages, glossary, term hub, RDF graph, and recent changes.
         |5. Commit and push the working branch.
         |6. Open a Pull Request when ownership review, BoK Manager review, or publication review is needed.
         |
         |### BoK Manager
         |
         |1. Review Pull Requests for content, structure, terms, RDF, and consistency.
         |2. Request fixes in the Pull Request when needed, then merge approved changes into main.
         |3. Run `cozy bok publish --dry-run` to verify the publication plan.
         |4. Pass only consistent, high-quality knowledge to the publication flow.
         |
         |### Site Administrator
         |
         |1. Manage hosting, upload settings, secrets, and access control.
         |2. Operate stage/upload workflows after the publication change is reviewable through Pull Requests.
         |3. Handle monitoring, backups, incident response, and cache invalidation.
         |
         |## Source / Generated Boundary
         |
         |Knowledge Contributors edit Git source only. They maintain knowledge they own directly and propose changes to knowledge they do not own through Pull Requests.
         |
         |### Editable Source
         |
         |- `src/main/doxsite/**/*.dox`
         |- `src/main/doxsite/**/*.md`
         |- `src/main/doxsite/**/category.yaml`
         |- `src/main/doxsite/glossary/**/*.dox`
         |- `src/main/doxsite/rdf/**` when RDF seed is project-owned
         |
         |### Do Not Edit Generated Artifacts
         |
         |- `website.d/`
         |- `doxsite.d/`
         |- `antora.d/`
         |- `target/`
         |- `repository/`
         |
         |## Page Types
         |
         |- Home: whole-BoK dashboard.
         |- Category Dashboard: category-local KPI, articles, terms, and operation notes.
         |- Glossary: BoK-wide vocabulary dashboard.
         |- History: BoK operation and publication history dashboard.
         |- Manual: BoK operation entry point.
         |
         |## Operation Notes
         |
         |Standard BoK pages are dashboards, not ordinary articles. Glossary, History, and Manual are BoK console pages rather than normal categories.
         |Manual pages are excluded from automatic glossary linking.
         |""".stripMargin

  private[bok] def _manual_local_rules(config: CreateConfig): String =
    if (_is_japanese(config.language))
      s"""Local Rules
         |===========
         |
         |${_dox_head("Local Rules", s"${config.name} project-local BoK operation rules.")}
         |
         |# Dashboard
         |
         |このページは`${config.name}`固有の運用ルールを記録します。BoK標準運用は現在のCozy runtimeが持つ標準Manualから生成されます。
         |
         |## Project Scope
         |
         |- BoK name: `${config.name}`
         |- Source root: `src/main/doxsite`
         |- Generated outputs: `website.d`, `doxsite.d`, `antora.d`, `target`
         |
         |## Local Rules
         |
         |- このBoK固有のカテゴリ、レビュー基準、公開判断、アップロード手順をここに記録します。
         |- 機微情報やsecretは`.cozy/`または外部の安全な管理場所に置きます。
         |
         |## Upload And Publication
         |
         |- `cozy bok publish --dry-run`で公開計画を確認します。
         |- 実uploadはプロジェクト所有のworkflow scriptで行います。
         |""".stripMargin
    else
      s"""Local Rules
         |===========
         |
         |${_dox_head("Local Rules", s"${config.name} project-local BoK operation rules.")}
         |
         |# Dashboard
         |
         |This page records operation rules specific to `${config.name}`. The standard BoK workflow is generated from the current Cozy runtime manual.
         |
         |## Project Scope
         |
         |- BoK name: `${config.name}`
         |- Source root: `src/main/doxsite`
         |- Generated outputs: `website.d`, `doxsite.d`, `antora.d`, `target`
         |
         |## Local Rules
         |
         |- Record project-specific categories, review criteria, publication decisions, and upload procedures here.
         |- Keep sensitive values and secrets in `.cozy/` or another safe external location.
         |
         |## Upload And Publication
         |
         |- Run `cozy bok publish --dry-run` to verify the publication plan.
         |- Actual upload is handled by project-owned workflow scripts.
         |""".stripMargin

  private[bok] def _category_name(name: String): String =
    name.split("[^A-Za-z0-9]+").toVector.filter(_.nonEmpty).map { part =>
      part.head.toUpper + part.tail
    }.mkString match {
      case "" => "Category"
      case x => x
    }

  private[bok] def _article(title: String, purpose: String): String =
    s"""${title}
       |======
       |
       |${_dox_head(title, purpose)}
       |
       |# 目的
       |
       |${purpose}
       |
       |# 執筆メモ
       |
       |This is a cozy-generated BoK article seed.
       |""".stripMargin

  private[bok] def _glossary(title: String, definition: String, reading: Option[String]): String = {
    val headproperties =
      (Vector("status=work-in-progress", s"published_at=${_today}") ++ reading.toVector.map(x => s"reading=${x}")).mkString("\n")
    s"""${title}
       |======
       |
       |# HEAD
       |
       |${headproperties}
       |
       |# Definition
       |
       |${definition}
       |""".stripMargin
  }

  private[bok] def _site_ttl(config: CreateConfig): String =
    s"""@prefix schema: <https://schema.org/> .
       |@prefix kh: <${config.url}/rdf/ontology/knowledgehub#> .
       |
       |<${config.url}/>
       |  a schema:WebSite ;
       |  schema:name "${config.name}" ;
       |  schema:inLanguage "${config.language}" .
       |""".stripMargin

  private[bok] def _site_jsonld(config: CreateConfig): String =
    s"""{
       |  "@context": "https://schema.org",
       |  "@type": "WebSite",
       |  "name": "${config.name}",
       |  "url": "${config.url}",
       |  "inLanguage": "${config.language}"
       |}
       |""".stripMargin

  private[bok] def _schema_ttl(config: CreateConfig): String =
    s"""@prefix schema: <https://schema.org/> .
      |@prefix kh: <${config.url}/rdf/schema/knowledgehub#> .
      |
      |kh:KnowledgeItem a schema:DefinedTerm .
      |""".stripMargin

  private[bok] def _schema_jsonld(): String =
    """{
      |  "@context": "https://schema.org",
      |  "@type": "DefinedTermSet",
      |  "name": "KnowledgeHub Schema"
      |}
      |""".stripMargin

  private[bok] def _ontology_ttl(config: CreateConfig): String =
    s"""@prefix owl: <http://www.w3.org/2002/07/owl#> .
      |@prefix kh: <${config.url}/rdf/ontology/knowledgehub#> .
      |
      |kh:KnowledgeHubOntology a owl:Ontology .
      |""".stripMargin

  private[bok] def _ontology_jsonld(): String =
    """{
      |  "@context": {
      |    "owl": "http://www.w3.org/2002/07/owl#"
      |  },
      |  "@type": "owl:Ontology",
      |  "name": "KnowledgeHub Ontology"
      |}
      |""".stripMargin

  private[bok] def _css(): String =
    """body {
      |  line-height: 1.7;
      |}
      |
      |main {
      |  max-width: 78rem;
      |}
      |""".stripMargin

  private def _default_website_staging(config: CreateConfig): String = {
    val name = Option(config.save.getFileName).map(_.toString).filter(_.nonEmpty).getOrElse("bok")
    s"../${name}-website"
  }

  private[bok] def _website_stage_script(config: CreateConfig): String = {
    val staging = _default_website_staging(config)
    s"""#!/bin/sh
       |set -eu
       |
       |# Prototype staging workflow.
       |# Copy this file to etc/website-stage.sh, review the paths, and set it in
       |# bok.workflow.stage.command when the project needs a persistent published
       |# website working tree. Direct upload from website.d is usually simpler.
       |
       |PROJECT_DIR=$$(cd "$$(dirname "$$0")/.." && pwd)
       |cd "$$PROJECT_DIR"
       |
       |WEBSITE_BUILD_DIR=$${WEBSITE_SOURCE_DIR:-website.d}
       |REPOSITORY_SOURCE_DIR=$${REPOSITORY_SOURCE_DIR:-repository}
       |WEBSITE_STAGING_DIR=$${WEBSITE_STAGING_DIR:-$staging}
       |
       |if [ ! -d "$$WEBSITE_BUILD_DIR" ]; then
       |  echo "Website build directory is missing: $$WEBSITE_BUILD_DIR" >&2
       |  echo "Run: cozy bok build" >&2
       |  exit 2
       |fi
       |
       |mkdir -p "$$WEBSITE_STAGING_DIR"
       |rsync -av --checksum --delete --exclude '/.git' --exclude '/repository' "$$WEBSITE_BUILD_DIR"/ "$$WEBSITE_STAGING_DIR"/
       |
       |if [ -d "$$WEBSITE_BUILD_DIR/repository" ]; then
       |  mkdir -p "$$WEBSITE_STAGING_DIR/repository"
       |  rsync -av --checksum "$$WEBSITE_BUILD_DIR/repository"/ "$$WEBSITE_STAGING_DIR/repository"/
       |fi
       |
       |if [ -d "$$REPOSITORY_SOURCE_DIR" ]; then
       |  mkdir -p "$$WEBSITE_STAGING_DIR/repository"
       |  rsync -av --checksum "$$REPOSITORY_SOURCE_DIR"/ "$$WEBSITE_STAGING_DIR/repository"/
       |else
       |  echo "Repository source directory is not present; skipping artifact repository staging: $$REPOSITORY_SOURCE_DIR" >&2
       |fi
       |
       |if [ -d "$$WEBSITE_STAGING_DIR/.git" ] || [ -f "$$WEBSITE_STAGING_DIR/.git" ]; then
       |  git -C "$$WEBSITE_STAGING_DIR" status --short
       |fi
       |""".stripMargin
  }

  private[bok] def _website_upload_script(config: CreateConfig): String =
    """#!/bin/sh
      |set -eu
      |
      |# Prototype AWS S3 upload workflow.
      |# Copy this file to etc/website-upload.sh and set it in
      |# bok.workflow.upload.command. Cozy reads conf/cozy/config.* and .cozy/config.*
      |# and passes bok.workflow.upload.env.* values to this script as environment
      |# variables. Put sensitive values in .cozy/config.*.
      |
      |PROJECT_DIR=$(cd "$(dirname "$0")/.." && pwd)
      |cd "$PROJECT_DIR"
      |
      |WEBSITE_SOURCE_DIR=${WEBSITE_SOURCE_DIR:-website.d}
      |REPOSITORY_SOURCE_DIR=${REPOSITORY_SOURCE_DIR:-repository}
      |AWS_S3_URI=${AWS_S3_URI:-}
      |AWS_S3_SYNC_DELETE=${AWS_S3_SYNC_DELETE:-true}
      |AWS_CLOUDFRONT_DISTRIBUTION_ID=${AWS_CLOUDFRONT_DISTRIBUTION_ID:-}
      |
      |if [ ! -d "$WEBSITE_SOURCE_DIR" ]; then
      |  echo "Website source directory is missing: $WEBSITE_SOURCE_DIR" >&2
      |  echo "Run: cozy bok build" >&2
      |  echo "Or set WEBSITE_SOURCE_DIR via bok.workflow.upload.env.WEBSITE_SOURCE_DIR." >&2
      |  exit 2
      |fi
      |
      |if [ -z "$AWS_S3_URI" ]; then
      |  cat >&2 <<'MSG'
      |AWS_S3_URI is not configured.
      |
      |Configure the upload target through Cozy workflow environment settings.
      |Use conf/cozy/config.yaml for public defaults and .cozy/config.yaml for
      |sensitive local overrides. Example:
      |
      |bok:
      |  workflow:
      |    upload:
      |      env:
      |        WEBSITE_SOURCE_DIR: website.d
      |        AWS_S3_URI: s3://example-bucket/path/
      |        AWS_S3_SYNC_DELETE: "true"
      |        AWS_CLOUDFRONT_DISTRIBUTION_ID: EXAMPLE123
      |
      |Cozy intentionally does not embed hosting credentials or provider policy.
      |MSG
      |  exit 2
      |fi
      |
      |if ! command -v aws >/dev/null 2>&1; then
      |  echo "aws CLI is required for this upload workflow." >&2
      |  exit 2
      |fi
      |
      |if [ "$AWS_S3_SYNC_DELETE" = "true" ]; then
      |  aws s3 sync "$WEBSITE_SOURCE_DIR"/ "$AWS_S3_URI" --delete --exclude "repository/*"
      |else
      |  aws s3 sync "$WEBSITE_SOURCE_DIR"/ "$AWS_S3_URI" --exclude "repository/*"
      |fi
      |
      |if [ -d "$WEBSITE_SOURCE_DIR/repository" ]; then
      |  aws s3 sync "$WEBSITE_SOURCE_DIR/repository"/ "$AWS_S3_URI/repository/"
      |fi
      |
      |if [ -d "$REPOSITORY_SOURCE_DIR" ]; then
      |  aws s3 sync "$REPOSITORY_SOURCE_DIR"/ "$AWS_S3_URI/repository/"
      |else
      |  echo "Repository source directory is not present; skipping artifact repository upload: $REPOSITORY_SOURCE_DIR" >&2
      |fi
      |
      |if [ -n "$AWS_CLOUDFRONT_DISTRIBUTION_ID" ]; then
      |  aws cloudfront create-invalidation --distribution-id "$AWS_CLOUDFRONT_DISTRIBUTION_ID" --paths "/*"
      |fi
      |""".stripMargin


}
