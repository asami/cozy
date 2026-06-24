package cozy.bok.scenario

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Locale
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import com.typesafe.config.{Config => Hocon}
import io.circe._
import io.circe.syntax._
import io.circe.generic.extras._
import io.circe.generic.extras.semiauto._
import org.smartdox._
import org.smartdox.metadata.DocumentMetaData
import org.smartdox.parser.Dox2Parser

/*
 * @since   Jun. 24, 2026
 * @version Jun. 24, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ScenarioMetadata(scenarios: Vector[ScenarioMetadata.ScenarioEntry] = Vector.empty)

object ScenarioMetadata {
  final case class SourceDocument(
    sourcepath: String,
    publicpath: String,
    category: Option[String],
    dox: Document
  )

  final case class ScenarioEntry(
    id: String,
    slug: String,
    scenarioType: String,
    title: String,
    summary: Option[String],
    category: Option[String],
    sourcePath: String,
    publicPath: String,
    terms: Vector[String] = Vector.empty,
    status: Option[String] = None,
    quality: ScenarioQuality = ScenarioQuality.empty,
    steps: Vector[ScenarioStep] = Vector.empty,
    useCase: Option[UseCaseScenario] = None,
    personaJourney: Option[PersonaJourneyScenario] = None,
    relationships: Vector[ScenarioRelationship] = Vector.empty
  )

  final case class ScenarioQuality(
    needsCuration: Boolean = false,
    missingTerms: Boolean = false,
    missingFlows: Boolean = false
  )
  object ScenarioQuality {
    val empty: ScenarioQuality = ScenarioQuality()
  }

  final case class ScenarioStep(
    id: Option[String],
    actor: Option[String],
    action: String,
    directive: Option[String] = None,
    target: Option[String] = None
  )

  final case class UseCaseScenario(
    useCaseId: Option[String],
    name: String,
    goal: Option[String],
    primaryActor: Option[String],
    secondaryActors: Vector[String] = Vector.empty,
    supportingActors: Vector[String] = Vector.empty,
    trigger: Option[String] = None,
    preconditions: Vector[String] = Vector.empty,
    postconditions: Vector[String] = Vector.empty,
    priority: Option[String] = None,
    status: Option[String] = None,
    flows: Vector[UseCaseFlow] = Vector.empty
  )

  final case class UseCaseFlow(
    name: String,
    flowType: String,
    steps: Vector[ScenarioStep] = Vector.empty
  )

  final case class PersonaJourneyScenario(
    persona: Option[String],
    journeyStages: Vector[String] = Vector.empty,
    goals: Vector[String] = Vector.empty,
    painPoints: Vector[String] = Vector.empty,
    touchpoints: Vector[String] = Vector.empty
  )

  final case class ScenarioRelationship(
    relation: String,
    target: String,
    condition: Option[String] = None
  )

  private implicit val _circe_config: Configuration = Configuration.default.withDefaults.withSnakeCaseMemberNames
  private implicit val _scenario_quality_encoder: Encoder.AsObject[ScenarioQuality] = deriveConfiguredEncoder
  private implicit val _scenario_step_encoder: Encoder.AsObject[ScenarioStep] = deriveConfiguredEncoder
  private implicit val _use_case_flow_encoder: Encoder.AsObject[UseCaseFlow] = deriveConfiguredEncoder
  private implicit val _use_case_scenario_encoder: Encoder.AsObject[UseCaseScenario] = deriveConfiguredEncoder
  private implicit val _persona_journey_scenario_encoder: Encoder.AsObject[PersonaJourneyScenario] = deriveConfiguredEncoder
  private implicit val _scenario_relationship_encoder: Encoder.AsObject[ScenarioRelationship] = deriveConfiguredEncoder
  private implicit val _scenario_entry_encoder: Encoder.AsObject[ScenarioEntry] = deriveConfiguredEncoder
  private implicit val _scenario_metadata_encoder: Encoder.AsObject[ScenarioMetadata] = deriveConfiguredEncoder

  def create(source: Path): ScenarioMetadata = {
    val documents = _source_documents(source.toAbsolutePath.normalize())
    val entries = documents.flatMap(_scenario_entry).sortBy(x => (x.category.getOrElse(""), x.slug, x.id))
    ScenarioMetadata(entries)
  }

  def write(source: Path, save: Path): ScenarioMetadata = {
    val metadata = create(source)
    Files.createDirectories(save.getParent)
    Files.writeString(save, toJsonString(metadata), StandardCharsets.UTF_8)
    metadata
  }

  def toJsonString(metadata: ScenarioMetadata): String =
    metadata.asJson.spaces2 + "\n"

  private def _source_documents(source: Path): Vector[SourceDocument] = {
    if (!Files.isDirectory(source)) {
      Vector.empty
    } else {
      val stream = Files.walk(source)
      try {
        stream.iterator.asScala.toVector.filter(Files.isRegularFile(_)).filter(_is_source_document).sortBy(_.toString).flatMap { path =>
          val relative = source.relativize(path).toString.replace(java.io.File.separatorChar, '/')
          if (_is_scenario_document_location(relative)) {
            val text = Files.readString(path, StandardCharsets.UTF_8)
            val dox = Dox.toDocument(Dox2Parser.parseWithFilename(path.getFileName.toString, text))
            Some(SourceDocument(relative, _public_path(relative), _category(relative), dox))
          } else {
            None
          }
        }
      } finally {
        stream.close()
      }
    }
  }

  private def _is_source_document(path: Path): Boolean = {
    val name = path.getFileName.toString.toLowerCase(Locale.ROOT)
    name.endsWith(".dox") || name.endsWith(".md") || name.endsWith(".markdown")
  }

  private def _public_path(sourcepath: String): String =
    sourcepath.replaceAll("\\.(dox|md|markdown)$", ".html")

  private def _is_scenario_document_location(sourcepath: String): Boolean = {
    val parts = sourcepath.split('/').toVector.filter(_.nonEmpty)
    parts match {
      case Vector("scenario", category, _*) if !_special_roots.contains(category) => true
      case _ => false
    }
  }

  private def _category(sourcepath: String): Option[String] = {
    val parts = sourcepath.split('/').toVector.filter(_.nonEmpty)
    parts match {
      case Vector("scenario", category, _*) if !_special_roots.contains(category) => Some(category)
      case _ => None
    }
  }

  private val _special_roots = Set("assets", "glossary", "history", "manual", "rdf", "scenario", "scenarios")

  private def _scenario_entry(document: SourceDocument): Option[ScenarioEntry] = {
    val metadata = document.dox.head.metadata
    for {
      scenariotype <- _scenario_type(metadata)
    } yield {
      val slug = _slug(document.publicpath)
      val id = _metadata_string(metadata, "scenario.id", "scenario_id", "id").getOrElse(slug)
      val title = _metadata_string(metadata, "scenario.name", "name").
        orElse(metadata.getTitleString(Locale.ENGLISH)).
        orElse(metadata.getTitleStringDefault).
        getOrElse(_title_from_slug(slug))
      val summary = metadata.getEffectiveBriefString(Locale.ENGLISH).
        orElse(_metadata_string(metadata, "scenario.summary", "summary", "description"))
      val category = _metadata_string(metadata, "scenario.category", "category").orElse(document.category)
      val terms = _metadata_string_list(metadata, "scenario.terms", "terms")
      val status = _metadata_string(metadata, "scenario.status", "status")
      val sections = _sections(document.dox.body.contents)
      val steps = if (scenariotype == "simple") _steps(document.dox.body.contents) else Vector.empty
      val flows = if (scenariotype == "use-case") _use_case_flows(sections) else Vector.empty
      val relationships = _relationships(document.dox.body.contents)
      val usecase = if (scenariotype == "use-case") Some(_use_case(metadata, id, title, status, flows)) else None
      val journey = if (scenariotype == "persona-journey") Some(_persona_journey(metadata)) else None
      ScenarioEntry(
        id,
        slug,
        scenariotype,
        title,
        summary,
        category,
        document.sourcepath,
        document.publicpath,
        terms,
        status,
        ScenarioQuality(terms.isEmpty, terms.isEmpty, scenariotype == "use-case" && flows.isEmpty),
        steps,
        usecase,
        journey,
        relationships
      )
    }
  }

  private def _scenario_type(metadata: DocumentMetaData): Option[String] =
    _metadata_string(metadata, "scenario.type", "scenario_type").map(_normalize_scenario_type)

  private def _normalize_scenario_type(value: String): String =
    value.trim.toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-') match {
      case "usecase" => "use-case"
      case "use-case" => "use-case"
      case "persona" => "persona-journey"
      case "persona-journey" => "persona-journey"
      case "simple" => "simple"
      case other => other
    }

  private def _use_case(
    metadata: DocumentMetaData,
    id: String,
    title: String,
    status: Option[String],
    flows: Vector[UseCaseFlow]
  ): UseCaseScenario =
    UseCaseScenario(
      _metadata_string(metadata, "scenario.use_case_id", "scenario.useCaseId", "use_case_id", "useCaseId").orElse(Some(id)),
      _metadata_string(metadata, "scenario.name", "name").getOrElse(title),
      _metadata_string(metadata, "scenario.goal", "goal"),
      _metadata_string(metadata, "scenario.primary_actor", "scenario.primaryActor", "primary_actor", "primaryActor"),
      _metadata_string_list(metadata, "scenario.secondary_actors", "scenario.secondaryActors", "secondary_actors", "secondaryActors"),
      _metadata_string_list(metadata, "scenario.supporting_actors", "scenario.supportingActors", "supporting_actors", "supportingActors"),
      _metadata_string(metadata, "scenario.trigger", "trigger"),
      _metadata_string_list(metadata, "scenario.preconditions", "preconditions"),
      _metadata_string_list(metadata, "scenario.postconditions", "postconditions"),
      _metadata_string(metadata, "scenario.priority", "priority"),
      _metadata_string(metadata, "scenario.status", "status").orElse(status),
      flows
    )

  private def _persona_journey(metadata: DocumentMetaData): PersonaJourneyScenario =
    PersonaJourneyScenario(
      _metadata_string(metadata, "scenario.persona", "persona"),
      _metadata_string_list(metadata, "scenario.journey_stages", "scenario.journeyStages", "journey_stages", "journeyStages"),
      _metadata_string_list(metadata, "scenario.goals", "goals"),
      _metadata_string_list(metadata, "scenario.pain_points", "scenario.painPoints", "pain_points", "painPoints"),
      _metadata_string_list(metadata, "scenario.touchpoints", "touchpoints")
    )

  private def _use_case_flows(sections: Vector[Section]): Vector[UseCaseFlow] =
    sections.collect {
      case section if _flow_type(section.titleName).nonEmpty =>
        UseCaseFlow(section.titleName, _flow_type(section.titleName).get, _steps(section.contents))
    }

  private def _flow_type(name: String): Option[String] =
    name.trim.toLowerCase(Locale.ROOT).replace(" ", "") match {
      case "mainflow" => Some("main")
      case "alternateflow" | "alternativeflow" => Some("alternate")
      case "exceptionflow" => Some("exception")
      case _ => None
    }

  private def _steps(xs: Seq[Dox]): Vector[ScenarioStep] =
    xs.toVector.flatMap {
      case Ul(contents, _, _) => contents.map(x => _step(x.toText)).toVector
      case Ol(contents, _, _) => contents.map(x => _step(x.toText)).toVector
      case section: Section => _steps(section.contents)
      case _ => Vector.empty
    }

  private def _step(raw: String): ScenarioStep = {
    val text = raw.trim.replaceAll("\\s+", " ")
    val (id, body0) = """^\[([^\]]+)\]\s*(.*)$""".r.findFirstMatchIn(text).
      map(m => (Some(m.group(1).trim), m.group(2).trim)).getOrElse((None, text))
    val (id1, actor, body) = body0.split(":", 2) match {
      case Array(a, b) if a.trim.nonEmpty && b.trim.nonEmpty =>
        val (stepid, actorname) = _actor_with_optional_step_id(a.trim)
        (id.orElse(stepid), Some(actorname), b.trim)
      case _ => (id, None, body0)
    }
    val parts = body.split("\\s+").toVector
    val directive = parts.headOption.map(_.toLowerCase(Locale.ROOT)).filter(_scenario_directives.contains)
    ScenarioStep(id1, actor, body, directive, directive.flatMap(_ => parts.drop(1).headOption))
  }

  private def _actor_with_optional_step_id(value: String): (Option[String], String) =
    value.split("\\s+", 2) match {
      case Array(stepid, actorname) if _looks_like_step_id(stepid) && actorname.nonEmpty => (Some(stepid), actorname)
      case _ => (None, value)
    }

  private def _looks_like_step_id(value: String): Boolean =
    value.matches("""[a-z0-9][A-Za-z0-9_-]*""")

  private val _scenario_directives = Set(
    "goto", "call", "include", "extend", "generalize", "precedes", "triggers", "requires",
    "ensures", "collaborates", "conflicts", "supersedes", "references", "end", "abort", "repeat"
  )

  private def _relationships(xs: Seq[Dox]): Vector[ScenarioRelationship] =
    _steps(xs).flatMap { step =>
      step.directive.filter(_relationship_directives.contains).flatMap(d => step.target.map(t => ScenarioRelationship(d, t, _condition(step.action))))
    }

  private val _relationship_directives = Set("include", "extend", "generalize", "precedes", "triggers", "requires", "ensures", "collaborates", "conflicts", "supersedes", "references")

  private def _condition(action: String): Option[String] =
    action.split("\\s+when\\s+", 2) match {
      case Array(_, condition) if condition.trim.nonEmpty => Some(condition.trim)
      case _ => None
    }

  private def _sections(xs: Seq[Dox]): Vector[Section] =
    xs.toVector.flatMap {
      case section: Section => Vector(section) ++ _sections(section.contents)
      case other => _sections(other.elements)
    }

  private def _metadata_string(metadata: DocumentMetaData, keys: String*): Option[String] =
    metadata.properties.flatMap { hocon =>
      keys.toStream.flatMap { key =>
        try {
          if (hocon.hasPath(key))
            Some(hocon.getString(key)).filter(_.trim.nonEmpty)
          else
            None
        } catch {
          case NonFatal(_) => None
        }
      }.headOption
    }

  private def _metadata_string_list(metadata: DocumentMetaData, keys: String*): Vector[String] =
    metadata.properties.map { hocon =>
      keys.toStream.flatMap(key => _hocon_string_list(hocon, key)).headOption.getOrElse(Vector.empty)
    }.getOrElse(Vector.empty)

  private def _hocon_string_list(hocon: Hocon, key: String): Option[Vector[String]] =
    try {
      if (!hocon.hasPath(key))
        None
      else
        Some(hocon.getStringList(key).asScala.toVector.filter(_.trim.nonEmpty))
    } catch {
      case NonFatal(_) =>
        try {
          val value = hocon.getString(key)
          Some(_string_list(value)).filter(_.nonEmpty)
        } catch {
          case NonFatal(_) => None
        }
    }

  private def _string_list(value: String): Vector[String] = {
    val trimmed = value.trim
    if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
      trimmed.drop(1).dropRight(1).split(',').toVector.map(_.trim.stripPrefix("\"").stripSuffix("\"")).filter(_.nonEmpty)
    } else if (trimmed.nonEmpty) {
      Vector(trimmed)
    } else {
      Vector.empty
    }
  }

  private def _slug(publicpath: String): String =
    publicpath.split('/').filter(_.nonEmpty).lastOption.getOrElse(publicpath).stripSuffix(".html")

  private def _title_from_slug(slug: String): String =
    slug.split('-').filter(_.nonEmpty).map(_.capitalize).mkString(" ")
}
