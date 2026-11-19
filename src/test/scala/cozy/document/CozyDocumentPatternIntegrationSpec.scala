package cozy.document

import java.nio.file.Files
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A synthetic observation model, independent of any user's manuscript. */
final class CozyDocumentPatternIntegrationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _core = {
    val source = scala.io.Source.fromResource("phase91/observed-core.json")
    try source.mkString finally source.close()
  }
  private def _roles(pattern: CozyDocumentPatternCatalog.Pattern, prefix: String, claim: String): String =
    pattern.roles.filter(_.min > 0).zipWithIndex.map { case (role, index) =>
      s"""{"id":"$prefix$index","role":"${role.id}","target":{"kind":"claim","id":"$claim"}}"""
    }.mkString(",")
  private def _source(document: CozyDocumentPatternCatalog.Pattern, section: CozyDocumentPatternCatalog.Pattern): String = {
    val docroles = document.roles.filter(_.min > 0).zipWithIndex.map { case (role, index) =>
      s"""{"id":"d$index","role":"${role.id}","step":"child"}"""
    }.mkString(",")
    val child = if (section.id == "mechanism") CozyDocumentPatternCatalog.sections.head else CozyDocumentPatternCatalog.sections.find(_.id == "mechanism").get
    val inherited = section.id == "decomposition"
    val rootroles = (if (inherited) """{"id":"rp","role":"purpose","target":{"kind":"claim","id":"root-claim"}},""" else "") + (if (inherited) _roles(section, "r", "root-claim").replace("""{"id":"r1","role":"components","target":{"kind":"claim","id":"root-claim"}}""", """{"id":"r1","role":"components","target":{"kind":"child","id":"child","explainsClaim":"root-claim"}}""") else _roles(section, "r", "root-claim"))
    val context = if (inherited) """{"ancestor":"root","role":"purpose","target":"root-claim"}""" else ""
    s"""{"schema":"cozy.content-core.logic-tree.v2","core":${_core},"catalog":{"id":"cozy.document-section-patterns","version":1},
      "documentPattern":{"pattern":"${document.id}","roles":[$docroles]},"sections":[
      {"step":"root","pattern":"${section.id}","question":"qr","answer":"root-claim","roles":[${rootroles}],"inheritedContext":[]},
      {"step":"child","pattern":"${child.id}","question":"qc","answer":"child-claim","roles":[${_roles(child, "c", "child-claim")}],"inheritedContext":[${context}]}]}"""
  }
  private def _format(locale: String): String = {
    val observation = if (locale == "ja") "観測" else "Observation"
    val evidence = if (locale == "ja") "根拠" else "Evidence"
    val claim = if (locale == "ja") "観測の主張" else "Observation claim"
    val detail = if (locale == "ja") "根拠の主張" else "Evidence claim"
    val question = if (locale == "ja") "何を観測したか？" else "What was observed?"
    val childquestion = if (locale == "ja") "根拠は何か？" else "What is the evidence?"
    s"""{"schema":"cozy.content-core.logic-tree-format.v2","coreId":"observation","locale":"$locale",
    "base":{"schema":"cozy.content-core.logic-tree-format.v1","id":"format","coreId":"observation","locale":"$locale",
      "stepBindings":[{"id":"root","title":"$observation"},{"id":"child","title":"$evidence"}],
      "claimBindings":[{"id":"root-claim","text":"$claim"},{"id":"child-claim","text":"$detail"}],
      "nodeBindings":[{"id":"rs","label":"RS"},{"id":"rt","label":"RT"},{"id":"cs","label":"CS"},{"id":"ct","label":"CT"}],
      "chrome":{"overviewLabel":"Overview","overviewDocumentTitle":"Overview","slidesDocumentTitle":"Slides","pageCountLabel":"Pages","claimsHeading":"Claims","localStructureHeading":"Structure","directChildrenHeading":"Children","directChildFlowHeading":"Flow","noDirectChildren":"None","noDirectChildFlowTransitions":"None","previous":"Previous","next":"Next","deck":"Deck","navigationAriaLabel":"Navigation"}},
    "questionBindings":[{"id":"qr","text":"$question"},{"id":"qc","text":"$childquestion"}]}"""
  }
  private def _document(locale: String): String = {
    val observation = if (locale == "ja") "観測" else "Observation"
    val evidence = if (locale == "ja") "根拠" else "Evidence"
    val prose = if (locale == "ja") "観測結果を説明します。" else "Reader-authored observation."
    s"""{"schema":"cozy.document-description.v2","id":"doc","core":{"id":"observation"},"locale":"$locale",
    "document":{"title":"$observation","sections":[{"id":"chapter","heading":"$observation","coreRefs":{"steps":["root","child"],"claims":["root-claim","child-claim"],"nodes":["rs","rt","cs","ct"],"relations":["rm","cm"],"flows":["rf","cf"]},
    "blocks":[{"id":"paragraph","kind":"paragraph","text":"$prose","coreRefs":{"steps":["child"],"claims":["root-claim","child-claim"],"nodes":[],"relations":[],"flows":[]}}],"sections":[]}]},
    "labels":{"steps":[{"stepRef":"root","text":"$observation"},{"stepRef":"child","text":"$evidence"}],"nodes":[{"nodeRef":"rs","text":"RS"},{"nodeRef":"rt","text":"RT"},{"nodeRef":"cs","text":"CS"},{"nodeRef":"ct","text":"CT"}]}}"""
  }
  private def _mapping(source: String, locale: String): String = {
    val envelope = CozyDocumentPatternCodec.decode(io.circe.parser.parse(source).toOption.get)
    val entries = (envelope.documentRoles.map(_.id) ++ envelope.sections.flatMap(_.roles.map(_.id))).map(id => s"""{"roleAssignment":"$id","targetKind":"section","targetId":"chapter"}""").mkString(",")
    s"""{"schema":"cozy.document-pattern-correspondence.v1","coreId":"observation","documentId":"doc","locale":"$locale","assignments":[$entries]}"""
  }
  "The observation model integration" should {
    "take every catalog selection from scaffold through localized validation and overview" in {
      Given("a portable synthetic observation Core and its independently authored Document")
      val root = Files.createTempDirectory("p91-integration")
      val core = root.resolve("core.yaml")
      val format = root.resolve("format.yaml")
      val ja = Files.createDirectory(root.resolve("ja"))
      val en = Files.createDirectory(root.resolve("en"))
      val jadocument = ja.resolve("document.yaml")
      val endocument = en.resolve("document.yaml")
      val mapping = root.resolve("mapping.yaml")
      val output = root.resolve("overview.html")
      try {
        for (doc <- CozyDocumentPatternCatalog.documents; section <- CozyDocumentPatternCatalog.sections) {
          When("the normal command scaffolds each selection and the author supplies the fixture")
          Files.deleteIfExists(core)
          CozyDocumentLogicTreeCommand.execute(List("document-project", "logic-tree", "scaffold", "--catalog", CozyDocumentPatternCatalog.id, "--version", "1", "--document-pattern", doc.id, "--section-pattern", section.id, "--save", core.toString)) shouldBe true
          val draft = intercept[CozyDocumentLogicTree.LogicTreeFault] { CozyDocumentLogicTreeCommand.execute(List("document-project", "logic-tree", "validate", "--core", core.toString, "--format", format.toString)) }
          draft.code shouldBe "draft-incomplete"
          val source = _source(doc, section)
          Files.writeString(core, source)
          for (locale <- Vector("ja", "en")) {
            val document = if (locale == "ja") jadocument else endocument
            Files.writeString(format, _format(locale))
            Files.writeString(document, _document(locale))
            Files.writeString(mapping, _mapping(source, locale))
            Then("validation and rendered localized structure retain the selected identities")
            CozyDocumentLogicTreeCommand.execute(List("document-project", "logic-tree", "validate", "--core", core.toString, "--format", format.toString, "--document", document.toString, "--correspondence", mapping.toString)) shouldBe true
            CozyDocumentLogicTreeCommand.execute(List("document-project", "logic-tree", "render", "--core", core.toString, "--format", format.toString, "--kind", "overview", "--save", output.toString)) shouldBe true
            val html = Files.readString(output)
            html should include (if (locale == "ja") doc.label.ja else doc.label.en)
            html should include (if (locale == "ja") section.label.ja else section.label.en)
            html should include (if (locale == "ja") "何を観測したか？" else "What was observed?")
            html should include (if (locale == "ja") "観測の主張" else "Observation claim")
            CozyDocumentDescriptionV2.loadDocument(core, document).description.document.sections.head.heading shouldBe (if (locale == "ja") "観測" else "Observation")
            html should include ("id=\"step-child\"")
            if (section.id == "decomposition") {
              html should include (if (locale == "ja") "継承元" else "Inherited from")
              html should include ("href=\"#claim-root-claim\"")
            }
          }
        }
      } finally {
        Vector(output, mapping, jadocument, endocument, ja, en, format, core, root).foreach(Files.deleteIfExists(_))
      }
    }
    "keep authoring vocabulary out of the bilingual native article" in {
      Given("the observation fixture and separately authored Japanese and English articles")
      val root = Files.createTempDirectory("p91-article")
      val project = Files.createDirectory(root.resolve("article.dox"))
      val content = Files.createDirectory(project.resolve("content"))
      val ja = Files.createDirectory(content.resolve("ja"))
      val en = Files.createDirectory(content.resolve("en"))
      val core = content.resolve("core.yaml")
      val jadocument = ja.resolve("document.yaml")
      val endocument = en.resolve("document.yaml")
      val article = project.resolve("index.dox")
      try {
        Files.writeString(core, _source(CozyDocumentPatternCatalog.documents.head, CozyDocumentPatternCatalog.sections.head))
        Files.writeString(jadocument, _document("ja"))
        Files.writeString(endocument, _document("en"))
        for (prose <- Vector("観測結果を説明します。", "The observation is explained.")) {
          When("the native article renderer consumes the reader-authored article")
          Files.writeString(article, s"# Observation\n\n$prose\n")
          val html = CozyDocumentProjectSmartDoxArticleHtml.render(project).html
          Then("the result contains prose without pattern identifiers or authoring labels")
          html should include (prose)
          Vector("background-first", "Background First", "Roles and reading order", "root-claim").foreach { label => html should not include (label) }
        }
      } finally {
        Vector(article, jadocument, endocument, core, ja, en, content, project, root).foreach(Files.deleteIfExists(_))
      }
    }
    "reject missing roles, nonlocal references and stale correspondence after a pattern switch" in {
      Given("a complete observation model and a correspondence to its first pattern")
      val doc = CozyDocumentPatternCatalog.documents.head
      val original = _source(doc, CozyDocumentPatternCatalog.sections.head)
      val changed = _source(doc, CozyDocumentPatternCatalog.sections.last)
      val missing = original.replaceFirst("\\{\\\"id\\\":\\\"r0\\\"[^}]+}\\},", "")
      val nonlocal = original.replace("\"id\":\"root-claim\"}}", "\"id\":\"child-claim\"}}")
      When("invalid variants are admitted")
      val faults = Vector(missing, nonlocal).map(value => intercept[CozyDocumentLogicTree.LogicTreeFault] { CozyDocumentPatternCodec.decode(io.circe.parser.parse(value).toOption.get) })
      Then("they identify missing structure and nonlocal references")
      faults.map(_.code) shouldBe Vector("PATTERN_ROLE_CARDINALITY", "PATTERN_REFERENCE")
      val root = Files.createTempDirectory("p91-switch")
      val core = root.resolve("core.yaml")
      val format = root.resolve("format.yaml")
      val en = Files.createDirectory(root.resolve("en"))
      val document = en.resolve("document.yaml")
      val mapping = root.resolve("mapping.yaml")
      try {
        Files.writeString(core, changed)
        Files.writeString(format, _format("en"))
        Files.writeString(document, _document("en"))
        Files.writeString(mapping, _mapping(original, "en"))
        val stale = intercept[CozyDocumentLogicTree.LogicTreeFault] {
          CozyDocumentLogicTreeCommand.execute(List("document-project", "logic-tree", "validate", "--core", core.toString, "--format", format.toString, "--document", document.toString, "--correspondence", mapping.toString))
        }
        stale.code shouldBe "PATTERN_CORRESPONDENCE"
      } finally {
        Vector(mapping, document, en, format, core, root).foreach(Files.deleteIfExists(_))
      }
    }
  }
}
