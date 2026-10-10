package cozy.document

import io.circe.Json
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import java.util.Locale
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Sep.  2, 2026
 *  version Sep. 30, 2026
 * @version Oct.  8, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentContentCore {
  private final case class Dialogue(
    id: String,
    source: String,
    idea: String,
    provider: String,
    model: String,
    request: String,
    response: String,
    outcome: String,
    diagnostics: Vector[String],
    candidate: Option[Vector[CoreEntry]],
    supersedes: Option[String]
  )
  private final case class CoreEntry(id: String, text: String)
  private final case class Candidate(
    id: String,
    path: Path,
    entries: Vector[CoreEntry],
    supersedes: Option[String]
  )
  private final case class FeedbackRecord(id: String, candidate: String, decision: String, path: Path)
  private final case class CoreReference(path: String)
  private final case class AcceptanceRecord(
    id: String,
    candidate: String,
    reviewer: String,
    priorcore: CoreReference,
    resultingcore: CoreReference,
    path: Path
  )
  private final case class CandidateState(
    candidate: Candidate,
    superseded: Boolean,
    rejected: Boolean,
    accepted: Boolean,
    changesrequested: Boolean
  )
  private final case class Feedback(id: String, candidate: String, reviewer: String, decision: String, payload: String)
  private final case class Acceptance(id: String, candidate: String, reviewer: String)

  private val _slug_pattern = "[a-z0-9][a-z0-9._-]*".r
  private val _dialogue_schema = "cozy.content-core-dialogue.v1"
  private val _feedback_schema = "cozy.content-core-feedback.v1"
  private val _acceptance_schema = "cozy.content-core-acceptance.v1"
  private val _candidate_schema = "cozy.content-core-candidate.v1"
  private val _attempt_schema = "cozy.content-core-attempt.v1"
  private val _feedback_record_schema = "cozy.content-core-feedback-record.v1"
  private val _acceptance_record_schema = "cozy.content-core-acceptance-record.v1"

  private[cozy] def candidate(
    project: Path,
    dialogueValue: String
  ): String = {
    val dialoguepath = _admit_input(dialogueValue, "dialogue")
    val dialogue = _dialogue(dialoguepath)
    val descriptor = CozyDocumentProject._load_project(project)
    if (dialogue.outcome == "succeeded") {
      val entries = dialogue.candidate.getOrElse(_descriptor_failure("succeeded dialogue must contain a candidate Core replacement"))
      val superseded = dialogue.supersedes.map { candidateid =>
        val state = _candidate_state(project, descriptor, candidateid)
        if (state.superseded || state.rejected || state.accepted || !state.changesrequested) {
          _operation_failure(s"superseded candidate is not eligible for revision: $candidateid")
        }
        val feedback = _feedback_records(project).filter(record => record.candidate == candidateid && record.decision == "changes-requested").sortBy(_.id).lastOption.getOrElse {
          _operation_failure(s"superseded candidate has no changes-requested feedback: $candidateid")
        }
        (state.candidate, feedback)
      }
      val evidence = _append(
        project,
        "candidates",
        dialogue.id,
        _candidate_yaml(project, dialogue, entries, superseded)
      )
      s"Cozy Document Project Content Core Candidate\nproject: ${descriptor.id}\nschema: cozy.document-project.v2\noperation: content-core.compose\noutcome: succeeded\ncandidate: ${dialogue.id}\nevidence: ${CozyDocumentProject._project_relative(project, evidence)}"
    } else {
      val evidence = _append(project, "attempts", dialogue.id, _attempt_yaml(dialogue))
      s"Cozy Document Project Content Core Candidate\nproject: ${descriptor.id}\nschema: cozy.document-project.v2\noperation: content-core.compose\noutcome: failed\nattempt: ${CozyDocumentProject._project_relative(project, evidence)}"
    }
  }

  private[cozy] def feedback(
    project: Path,
    candidateId: String,
    feedbackValue: String
  ): String = {
    _admit_id(candidateId, "candidate id")
    val feedbackpath = _admit_input(feedbackValue, "feedback")
    val feedback = _feedback(feedbackpath)
    if (feedback.candidate != candidateId) {
      _descriptor_failure("feedback candidate must equal the command candidate id")
    }
    val descriptor = CozyDocumentProject._load_project(project)
    val state = _candidate_state(project, descriptor, candidateId)
    if (state.superseded || state.rejected || state.accepted) {
      _operation_failure(s"candidate is not eligible for feedback: $candidateId")
    }
    val evidence = _append(project, "feedback", feedback.id, _feedback_yaml(project, feedback, state.candidate))
    s"Cozy Document Project Content Core Feedback\nproject: ${descriptor.id}\nschema: cozy.document-project.v2\ncandidate: $candidateId\ndecision: ${feedback.decision}\nevidence: ${CozyDocumentProject._project_relative(project, evidence)}"
  }

  private[cozy] def accept(
    project: Path,
    candidateId: String,
    acceptanceValue: String
  ): String = {
    _admit_id(candidateId, "candidate id")
    val acceptancepath = _admit_input(acceptanceValue, "acceptance")
    val acceptance = _acceptance(acceptancepath)
    if (acceptance.candidate != candidateId) {
      _descriptor_failure("acceptance candidate must equal the command candidate id")
    }
    val descriptor = CozyDocumentProject._load_project(project)
    val core = CozyDocumentProject._direct_file(project, descriptor.contentCore, "Content Core")
    val current = CozyDocumentProject._load_json(core, "Content Core")
    CozyDocumentProject._validate_core(current, descriptor)
    val records = _acceptance_records(project, descriptor)
    records.find(_.id == acceptance.id) match {
      case Some(record) =>
        if (records.exists(value => value.candidate == candidateId && value.id != record.id)) {
          _operation_failure(s"candidate is not eligible for acceptance: $candidateId")
        }
        _resume_acceptance(project, descriptor, acceptance, candidateId, core, current, record)
      case None =>
        val state = _candidate_state(project, descriptor, candidateId)
        if (state.superseded || state.rejected || state.accepted) {
          _operation_failure(s"candidate is not eligible for acceptance: $candidateId")
        }
        _validate_replacement(current, descriptor, state.candidate.entries)
        val replacement = _core_yaml(current, state.candidate.entries)
        val evidenceyaml = _acceptance_yaml(project, acceptance, state.candidate, descriptor.contentCore)
        val evidence = _append(project, "acceptances", acceptance.id, evidenceyaml)
        _replace_core(core, replacement)
        _acceptance_output(project, descriptor, candidateId, evidence)
    }
  }

  private def _resume_acceptance(
    project: Path,
    descriptor: CozyDocumentProject.Descriptor,
    acceptance: Acceptance,
    candidateid: String,
    core: Path,
    current: Json,
    record: AcceptanceRecord
  ): String = {
    if (record.candidate != candidateid || record.reviewer != acceptance.reviewer) {
      _operation_failure(s"acceptance evidence does not match the requested human decision: ${acceptance.id}")
    }
    val state = _candidate_state(project, descriptor, candidateid)
    if (state.superseded || state.rejected) {
      _operation_failure(s"candidate is not eligible for acceptance: $candidateid")
    }
    val candidate = state.candidate
    _validate_replacement(current, descriptor, candidate.entries)
    val replacement = _core_yaml(current, candidate.entries)
    if (record.priorcore.path != descriptor.contentCore || record.resultingcore.path != descriptor.contentCore) {
      _operation_failure(s"acceptance evidence does not match the requested Core replacement: ${acceptance.id}")
    }
    _replace_core(core, replacement)
    _acceptance_output(project, descriptor, candidateid, record.path)
  }

  private def _acceptance_output(
    project: Path,
    descriptor: CozyDocumentProject.Descriptor,
    candidateid: String,
    evidence: Path
  ): String =
    s"Cozy Document Project Content Core Acceptance\nproject: ${descriptor.id}\nschema: cozy.document-project.v2\ncandidate: $candidateid\ndecision: accepted\ncontentCore: ${descriptor.contentCore}\nevidence: ${CozyDocumentProject._project_relative(project, evidence)}"

  private def _dialogue(path: Path): Dialogue = {
    val fields = _object(_load_input(path, "dialogue"), "dialogue")
    val common = Set("schema", "id", "source", "idea", "provider", "model", "request", "response", "outcome", "diagnostics")
    if (_string(fields, "schema", "dialogue") != _dialogue_schema) {
      _cli_failure(s"dialogue schema must be exactly ${_dialogue_schema}")
    }
    val id = _id(_string(fields, "id", "dialogue"), "dialogue id")
    val source = _raw(_string(fields, "source", "dialogue"), "dialogue source")
    val idea = _raw(_string(fields, "idea", "dialogue"), "dialogue idea")
    val provider = _exact(_string(fields, "provider", "dialogue"), "dialogue provider")
    val model = _exact(_string(fields, "model", "dialogue"), "dialogue model")
    val request = _raw(_string(fields, "request", "dialogue"), "dialogue request")
    val response = _raw(_string(fields, "response", "dialogue"), "dialogue response")
    val outcome = _string(fields, "outcome", "dialogue")
    if (!Set("succeeded", "failed").contains(outcome)) {
      _cli_failure("dialogue outcome must be succeeded or failed")
    }
    val diagnostics = _diagnostics(_field(fields, "diagnostics", "dialogue"), "dialogue diagnostics")
    outcome match {
      case "succeeded" =>
        val expected = common ++ Set("candidate") ++ (if (fields.contains("supersedes")) Set("supersedes") else Set.empty[String])
        if (fields.keySet != expected) {
          _cli_failure("succeeded dialogue has missing or extra fields")
        }
        val entries = _candidate_entries(_field(fields, "candidate", "dialogue"), "dialogue candidate")
        val supersedes = fields.get("supersedes").map(value => _id(value.asString.getOrElse(_cli_failure("dialogue supersedes must be a string")), "dialogue supersedes"))
        Dialogue(id, source, idea, provider, model, request, response, outcome, diagnostics, Some(entries), supersedes)
      case "failed" =>
        if (fields.keySet != common) {
          _cli_failure("failed dialogue has missing or extra fields")
        }
        if (diagnostics.isEmpty) {
          _cli_failure("failed dialogue diagnostics must be non-empty")
        }
        Dialogue(id, source, idea, provider, model, request, response, outcome, diagnostics, None, None)
    }
  }

  private def _feedback(path: Path): Feedback = {
    val fields = _object(_load_input(path, "feedback"), "feedback")
    if (_string(fields, "schema", "feedback") != _feedback_schema) {
      _cli_failure(s"feedback schema must be exactly ${_feedback_schema}")
    }
    val id = _id(_string(fields, "id", "feedback"), "feedback id")
    val candidate = _id(_string(fields, "candidate", "feedback"), "feedback candidate")
    val reviewer = _exact(_string(fields, "reviewer", "feedback"), "feedback reviewer")
    val decision = _string(fields, "decision", "feedback")
    val payload = decision match {
      case "changes-requested" =>
        if (fields.keySet != Set("schema", "id", "candidate", "reviewer", "decision", "feedback")) {
          _cli_failure("changes-requested feedback has missing or extra fields")
        }
        _exact(_string(fields, "feedback", "feedback"), "feedback text")
      case "rejected" =>
        if (fields.keySet != Set("schema", "id", "candidate", "reviewer", "decision", "rejectionReason")) {
          _cli_failure("rejected feedback has missing or extra fields")
        }
        _exact(_string(fields, "rejectionReason", "feedback"), "feedback rejectionReason")
      case _ => _cli_failure("feedback decision must be changes-requested or rejected")
    }
    Feedback(id, candidate, reviewer, decision, payload)
  }

  private def _acceptance(path: Path): Acceptance = {
    val fields = _object(_load_input(path, "acceptance"), "acceptance")
    if (fields.keySet != Set("schema", "id", "candidate", "reviewer", "decision") || _string(fields, "schema", "acceptance") != _acceptance_schema) {
      _cli_failure(s"acceptance must be a closed ${_acceptance_schema} document")
    }
    if (_string(fields, "decision", "acceptance") != "accepted") {
      _cli_failure("acceptance decision must be accepted")
    }
    Acceptance(
      _id(_string(fields, "id", "acceptance"), "acceptance id"),
      _id(_string(fields, "candidate", "acceptance"), "acceptance candidate"),
      _exact(_string(fields, "reviewer", "acceptance"), "acceptance reviewer")
    )
  }

  private def _candidate_state(project: Path, descriptor: CozyDocumentProject.Descriptor, candidateid: String): CandidateState = {
    val candidate = _candidate(project, candidateid)
    val candidates = _candidates(project)
    val feedbacks = _feedback_records(project).filter(_.candidate == candidateid)
    val acceptances = _acceptance_records(project, descriptor).filter(_.candidate == candidateid)
    CandidateState(
      candidate,
      candidates.exists(_.supersedes.contains(candidateid)),
      feedbacks.exists(_.decision == "rejected"),
      acceptances.nonEmpty,
      feedbacks.exists(_.decision == "changes-requested")
    )
  }

  private def _candidate(project: Path, candidateid: String): Candidate = {
    _admit_id(candidateid, "candidate id")
    val directory = _existing_directory(project, "candidates", "candidate evidence directory")
    val path = directory.resolve(s"$candidateid.yaml").normalize()
    if (!path.startsWith(directory) || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      _operation_failure(s"unknown candidate: $candidateid")
    }
    if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
      _path_failure("candidate evidence must be a direct regular non-symlink file")
    }
    val fields = _object(_load_evidence(path, "candidate evidence"), "candidate evidence")
    val required = Set("schema", "id", "operation", "source", "idea", "provider", "model", "request", "response", "outcome", "diagnostics", "candidate", "supersedes")
    if (!required.subsetOf(fields.keySet) || _string(fields, "schema", "candidate evidence") != _candidate_schema || _string(fields, "id", "candidate evidence") != candidateid || _string(fields, "operation", "candidate evidence") != "content-core.compose") {
      _descriptor_failure("candidate evidence is invalid")
    }
    _record_filename(path, candidateid, "candidate evidence")
    _text_payload(_field(fields, "source", "candidate evidence"), "candidate evidence source")
    _text_payload(_field(fields, "idea", "candidate evidence"), "candidate evidence idea")
    _named_reference(_field(fields, "provider", "candidate evidence"), "candidate evidence provider")
    _named_reference(_field(fields, "model", "candidate evidence"), "candidate evidence model")
    _text_payload(_field(fields, "request", "candidate evidence"), "candidate evidence request")
    _text_payload(_field(fields, "response", "candidate evidence"), "candidate evidence response")
    if (_outcome_value(_field(fields, "outcome", "candidate evidence"), "candidate evidence outcome") != "succeeded") {
      _descriptor_failure("candidate evidence outcome is invalid")
    }
    _diagnostics_payload(_field(fields, "diagnostics", "candidate evidence"), "candidate evidence diagnostics")
    val entries = _candidate_entries(_field(fields, "candidate", "candidate evidence"), "candidate evidence candidate")
    val supersedes = _supersedes_reference(project, _field(fields, "supersedes", "candidate evidence"), "candidate evidence supersedes")
    Candidate(candidateid, path, entries, supersedes)
  }

  private def _candidates(project: Path): Vector[Candidate] = {
    val directory = _existing_directory_optional(project, "candidates", "candidate evidence directory")
    directory.map { value =>
      _direct_files(value, "candidate evidence directory").map { path =>
        val name = path.getFileName.toString
        if (!name.endsWith(".yaml")) {
          _descriptor_failure("candidate evidence filename must end in .yaml")
        }
        _candidate(project, name.stripSuffix(".yaml"))
      }
    }.getOrElse(Vector.empty)
  }

  private def _feedback_records(project: Path): Vector[FeedbackRecord] =
    _records(project, "feedback", _feedback_record_schema) { (path, fields) =>
      val label = "feedback evidence"
      val id = _id(_string(fields, "id", "feedback evidence"), "feedback evidence id")
      _record_filename(path, id, label)
      val candidatereference = _nested_candidate_reference(_field(fields, "candidate", label), s"$label candidate").getOrElse(_descriptor_failure("feedback evidence candidate is missing"))
      _verify_candidate_reference(project, candidatereference, s"$label candidate")
      val candidate = candidatereference.id
      _named_reference(_field(fields, "reviewer", label), s"$label reviewer")
      val decision = _outcome_value(_field(fields, "decision", label), s"$label decision")
      val required = decision match {
        case "changes-requested" => Set("schema", "id", "candidate", "reviewer", "decision", "feedback")
        case "rejected" => Set("schema", "id", "candidate", "reviewer", "decision", "rejectionReason")
        case _ => _descriptor_failure("feedback evidence decision is invalid")
      }
      if (!required.subsetOf(fields.keySet)) {
        _descriptor_failure("feedback evidence has missing or extra fields")
      }
      _text_payload(_field(fields, if (decision == "rejected") "rejectionReason" else "feedback", label), s"$label payload")
      FeedbackRecord(id, candidate, decision, path)
    }

  private def _acceptance_records(project: Path, descriptor: CozyDocumentProject.Descriptor): Vector[AcceptanceRecord] =
    _records(project, "acceptances", _acceptance_record_schema) { (path, fields) =>
      val label = "acceptance evidence"
      val id = _id(_string(fields, "id", "acceptance evidence"), "acceptance evidence id")
      _record_filename(path, id, label)
      val required = Set("schema", "id", "candidate", "reviewer", "decision", "priorCore", "resultingCore")
      if (!required.subsetOf(fields.keySet)) {
        _descriptor_failure("acceptance evidence has missing or extra fields")
      }
      val candidatereference = _nested_candidate_reference(_field(fields, "candidate", label), s"$label candidate").getOrElse(_descriptor_failure("acceptance evidence candidate is missing"))
      _verify_candidate_reference(project, candidatereference, s"$label candidate")
      val candidate = candidatereference.id
      val reviewer = _named_reference(_field(fields, "reviewer", label), s"$label reviewer")
      if (_outcome_value(_field(fields, "decision", label), s"$label decision") != "accepted") {
        _descriptor_failure("acceptance evidence decision is invalid")
      }
      val prior = _core_reference(_field(fields, "priorCore", label), s"$label prior Core", descriptor.contentCore)
      val resulting = _core_reference(_field(fields, "resultingCore", label), s"$label resulting Core", descriptor.contentCore)
      AcceptanceRecord(id, candidate, reviewer, prior, resulting, path)
    }

  private def _records[A](
    project: Path,
    category: String,
    schema: String
  )(
    build: (Path, Map[String, Json]) => A
  ): Vector[A] = {
    _existing_directory_optional(project, category, s"$category evidence directory").map { directory =>
      _direct_files(directory, s"$category evidence directory").map { path =>
        if (!path.getFileName.toString.endsWith(".yaml")) {
          _descriptor_failure(s"$category evidence filename must end in .yaml")
        }
        val fields = _object(_load_evidence(path, s"$category evidence"), s"$category evidence")
        if (_string(fields, "schema", s"$category evidence") != schema) {
          _descriptor_failure(s"$category evidence schema is invalid")
        }
        build(path, fields)
      }
    }.getOrElse(Vector.empty)
  }

  private def _candidate_yaml(project: Path, dialogue: Dialogue, entries: Vector[CoreEntry], superseded: Option[(Candidate, FeedbackRecord)]): String = {
    val supersedes = superseded.map { case (candidate, feedback) =>
      Vector(
        "supersedes:",
        s"  id: ${_yaml_string(candidate.id)}",
        s"  path: ${_yaml_string(CozyDocumentProject._project_relative(project, candidate.path))}",
        "  feedback:",
        s"    id: ${_yaml_string(feedback.id)}",
        s"    path: ${_yaml_string(CozyDocumentProject._project_relative(project, feedback.path))}"
      )
    }.getOrElse(Vector("supersedes: none"))
    (Vector(
      s"schema: ${_candidate_schema}",
      s"id: ${_yaml_string(dialogue.id)}",
      "operation: content-core.compose"
    ) ++ _text_payload_yaml("source", dialogue.source) ++ _text_payload_yaml("idea", dialogue.idea) ++ _named_reference_yaml("provider", dialogue.provider) ++ _named_reference_yaml("model", dialogue.model) ++ _text_payload_yaml("request", dialogue.request) ++ _text_payload_yaml("response", dialogue.response) ++ _outcome_yaml(dialogue.outcome) ++ _diagnostics_yaml(dialogue.diagnostics) ++ Vector("candidate:") ++ _accepted_yaml("  ", entries) ++ supersedes).mkString("\n") + "\n"
  }

  private def _attempt_yaml(dialogue: Dialogue): String =
    (Vector(
      s"schema: ${_attempt_schema}",
      s"id: ${_yaml_string(dialogue.id)}",
      "operation: content-core.compose"
    ) ++ _text_payload_yaml("source", dialogue.source) ++ _text_payload_yaml("idea", dialogue.idea) ++ _named_reference_yaml("provider", dialogue.provider) ++ _named_reference_yaml("model", dialogue.model) ++ _text_payload_yaml("request", dialogue.request) ++ _text_payload_yaml("response", dialogue.response) ++ _outcome_yaml(dialogue.outcome) ++ _diagnostics_yaml(dialogue.diagnostics)).mkString("\n") + "\n"

  private def _feedback_yaml(project: Path, feedback: Feedback, candidate: Candidate): String = {
    val payloadname = if (feedback.decision == "rejected") "rejectionReason" else "feedback"
    (Vector(
      s"schema: ${_feedback_record_schema}",
      s"id: ${_yaml_string(feedback.id)}",
      "candidate:",
      s"  id: ${_yaml_string(candidate.id)}",
      s"  path: ${_yaml_string(CozyDocumentProject._project_relative(project, candidate.path))}"
    ) ++ _named_reference_yaml("reviewer", feedback.reviewer) ++ _outcome_yaml(feedback.decision).map(_.replaceFirst("outcome", "decision")) ++ _text_payload_yaml(payloadname, feedback.payload)).mkString("\n") + "\n"
  }

  private def _acceptance_yaml(
    project: Path,
    acceptance: Acceptance,
    candidate: Candidate,
    corepath: String
  ): String =
    (Vector(
      s"schema: ${_acceptance_record_schema}",
      s"id: ${_yaml_string(acceptance.id)}",
      "candidate:",
      s"  id: ${_yaml_string(candidate.id)}",
      s"  path: ${_yaml_string(CozyDocumentProject._project_relative(project, candidate.path))}"
    ) ++ _named_reference_yaml("reviewer", acceptance.reviewer) ++ Vector(
      "decision:",
      "  value: accepted",
      "priorCore:",
      s"  path: ${_yaml_string(corepath)}",
      "resultingCore:",
      s"  path: ${_yaml_string(corepath)}"
    )).mkString("\n") + "\n"

  private def _text_payload_yaml(name: String, value: String): Vector[String] =
    Vector(s"$name:", s"  text: ${_yaml_string(value)}")

  private def _named_reference_yaml(name: String, value: String): Vector[String] =
    Vector(s"$name:", s"  id: ${_yaml_string(value)}")

  private def _outcome_yaml(value: String): Vector[String] =
    Vector("outcome:", s"  value: ${_yaml_string(value)}")

  private def _diagnostics_yaml(values: Vector[String]): Vector[String] =
    if (values.isEmpty) Vector("diagnostics: []")
    else Vector("diagnostics:") ++ values.map(value => s"  - text: ${_yaml_string(value)}")

  private def _accepted_yaml(indent: String, entries: Vector[CoreEntry]): Vector[String] =
    if (entries.isEmpty) Vector(s"${indent}accepted: []")
    else Vector(s"${indent}accepted:") ++ entries.map { entry =>
      s"${indent}  - id: ${_yaml_string(entry.id)}\n${indent}    text: ${_yaml_string(entry.text)}"
    }

  private def _candidate_entries(value: Json, label: String): Vector[CoreEntry] = {
    val fields = _object(value, label)
    if (fields.keySet != Set("accepted")) {
      _descriptor_failure(s"$label must contain exactly accepted")
    }
    val entries = _field(fields, "accepted", label).asArray.getOrElse(_descriptor_failure(s"$label accepted must be an array"))
    val values = entries.map { entry =>
      val fields = _object(entry, s"$label entry")
      if (fields.keySet != Set("id", "text")) {
        _descriptor_failure(s"$label entries must contain exactly id and text")
      }
      CoreEntry(
        _id(_string(fields, "id", s"$label entry"), s"$label entry id"),
        _candidate_text(_string(fields, "text", s"$label entry"), s"$label entry text")
      )
    }.toVector
    if (values.map(_.id).distinct.size != values.size) {
      _descriptor_failure(s"$label entry ids must be unique")
    }
    values
  }

  private def _nested_candidate_reference(value: Json, label: String): Option[Candidate] = {
    if (value.isNull || value.asString.contains("none")) {
      None
    } else {
      val fields = _object(value, label)
      if (!Set("id", "path").subsetOf(fields.keySet)) {
        _descriptor_failure(s"$label must contain id and path")
      }
      val id = _id(_string(fields, "id", label), s"$label id")
      val relative = _string(fields, "path", label)
      val path = try Paths.get(relative) catch {
        case NonFatal(_) => _descriptor_failure(s"$label path is invalid")
      }
      Some(Candidate(id, path, Vector.empty, None))
    }
  }

  private def _supersedes_reference(project: Path, value: Json, label: String): Option[String] =
    if (value.isNull || value.asString.contains("none")) {
      None
    } else {
      val fields = _object(value, label)
      if (!Set("id", "path", "feedback").subsetOf(fields.keySet)) {
        _descriptor_failure(s"$label must contain id, path, and feedback")
      }
      val candidate = _nested_candidate_reference(
        Json.obj(
          "id" -> _field(fields, "id", label),
          "path" -> _field(fields, "path", label)
        ),
        label
      ).getOrElse(_descriptor_failure(s"$label candidate is missing"))
      _verify_candidate_reference(project, candidate, label)
      _feedback_reference(project, _field(fields, "feedback", label), candidate.id, label)
      Some(candidate.id)
    }

  private def _verify_candidate_reference(project: Path, reference: Candidate, label: String): Unit = {
    val expected = Paths.get("evidence", "content-core", "candidates", s"${reference.id}.yaml")
    if (reference.path.isAbsolute || reference.path.normalize() != expected) {
      _descriptor_failure(s"$label candidate path is invalid")
    }
    CozyDocumentProject._direct_file(project, reference.path.toString, s"$label candidate")
  }

  private def _feedback_reference(project: Path, value: Json, candidateid: String, label: String): Unit = {
    val fields = _object(value, s"$label feedback")
    if (!Set("id", "path").subsetOf(fields.keySet)) {
      _descriptor_failure(s"$label feedback must contain id and path")
    }
    val id = _id(_string(fields, "id", s"$label feedback"), s"$label feedback id")
    val relative = _string(fields, "path", s"$label feedback")
    val expected = Paths.get("evidence", "content-core", "feedback", s"$id.yaml")
    val pathvalue = try Paths.get(relative) catch {
      case NonFatal(_) => _descriptor_failure(s"$label feedback path is invalid")
    }
    if (pathvalue.isAbsolute || pathvalue.normalize() != expected) {
      _descriptor_failure(s"$label feedback reference is invalid")
    }
    CozyDocumentProject._direct_file(project, relative, s"$label feedback")
    val feedbacks = _feedback_records(project).filter(record => record.id == id && record.candidate == candidateid && record.decision == "changes-requested")
    if (feedbacks.size != 1) {
      _descriptor_failure(s"$label feedback must name one changes-requested record")
    }
  }

  private def _record_filename(path: Path, id: String, label: String): Unit = {
    if (path.getFileName.toString != s"$id.yaml") {
      _descriptor_failure(s"$label filename must match its id")
    }
  }

  private def _text_payload(value: Json, label: String): String = {
    val fields = _object(value, label)
    if (!fields.contains("text")) {
      _descriptor_failure(s"$label must contain text")
    }
    val text = _string(fields, "text", label)
    if (text.isEmpty) {
      _descriptor_failure(s"$label text must be non-empty")
    }
    text
  }

  private def _named_reference(value: Json, label: String): String = {
    val fields = _object(value, label)
    if (!fields.contains("id")) {
      _descriptor_failure(s"$label must contain id")
    }
    val id = _string(fields, "id", label)
    if (id.isEmpty || id != id.trim) {
      _descriptor_failure(s"$label id must be a non-empty exact string")
    }
    id
  }

  private def _outcome_value(value: Json, label: String): String = {
    val fields = _object(value, label)
    if (!fields.contains("value")) {
      _descriptor_failure(s"$label must contain value")
    }
    val outcome = _string(fields, "value", label)
    if (outcome.isEmpty || outcome != outcome.trim) {
      _descriptor_failure(s"$label value must be a non-empty exact string")
    }
    outcome
  }

  private def _core_reference(value: Json, label: String, contentcore: String): CoreReference = {
    val fields = _object(value, label)
    if (!fields.contains("path")) {
      _descriptor_failure(s"$label must contain path")
    }
    val path = _string(fields, "path", label)
    if (path != contentcore) {
      _descriptor_failure(s"$label path is invalid")
    }
    CoreReference(path)
  }

  private def _diagnostics_payload(value: Json, label: String): Unit =
    value.asArray.getOrElse(_descriptor_failure(s"$label must be an array")).foreach { item =>
      _text_payload(item, label)
    }

  private def _replace_core(destination: Path, content: String): Unit = {
    try {
      Files.writeString(destination, content, StandardCharsets.UTF_8)
    } catch {
      case NonFatal(_) => _path_failure("Content Core acceptance cannot be written")
    }
  }

  private def _core_yaml(current: Json, entries: Vector[CoreEntry]): String = {
    val fields = CozyDocumentProject._object(current, "Content Core")
    (Vector(
      s"schema: ${_yaml_string(CozyDocumentProject._string(fields, "schema", "Content Core"))}",
      s"id: ${_yaml_string(CozyDocumentProject._string(fields, "id", "Content Core"))}",
      s"language: ${_yaml_string(CozyDocumentProject._string(fields, "language", "Content Core"))}"
    ) ++ _accepted_yaml("", entries)).mkString("\n") + "\n"
  }

  private def _validate_replacement(
    current: Json,
    descriptor: CozyDocumentProject.Descriptor,
    entries: Vector[CoreEntry]
  ): Unit = {
    val fields = CozyDocumentProject._object(current, "Content Core")
    val accepted = Json.arr(entries.map { entry =>
      Json.obj("id" -> Json.fromString(entry.id), "text" -> Json.fromString(entry.text))
    }: _*)
    val replacement = Json.obj(
      "schema" -> CozyDocumentProject._field(fields, "schema", "Content Core"),
      "id" -> CozyDocumentProject._field(fields, "id", "Content Core"),
      "language" -> CozyDocumentProject._field(fields, "language", "Content Core"),
      "accepted" -> accepted
    )
    CozyDocumentProject._validate_core(replacement, descriptor)
  }

  private def _admit_input(value: String, label: String): Path = {
    val path = try Paths.get(value).toAbsolutePath.normalize() catch {
      case NonFatal(_) => _path_failure(s"$label input path is invalid")
    }
    if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
      _path_failure(s"$label input must be a direct regular non-symlink file")
    }
    val parent = Option(path.getParent).getOrElse(_path_failure(s"$label input parent is invalid"))
    if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
      _path_failure(s"$label input parent must be a direct non-symlink directory")
    }
    val suffix = Option(path.getFileName).map(_.toString.toLowerCase(Locale.ROOT)).flatMap { name =>
      val index = name.lastIndexOf('.')
      if (index >= 0 && index < name.length - 1) Some(name.substring(index)) else None
    }
    if (!Set(".json", ".yaml", ".yml").contains(suffix.getOrElse(""))) {
      _cli_failure(s"$label input must have a .json, .yaml, or .yml suffix")
    }
    path
  }

  private def _load_input(path: Path, label: String): Json =
    try {
      Files.readString(path, StandardCharsets.UTF_8)
      StructuredDocumentLoader.loadJson(InputSource(path.toFile)).take
    } catch {
      case NonFatal(_) => _cli_failure(s"$label input is unreadable or malformed JSON/YAML")
    }

  private def _load_evidence(path: Path, label: String): Json =
    try {
      Files.readString(path, StandardCharsets.UTF_8)
      StructuredDocumentLoader.loadJson(InputSource(path.toFile)).take
    } catch {
      case NonFatal(_) => _descriptor_failure(s"$label is unreadable or malformed")
    }

  private def _append(project: Path, category: String, id: String, content: String): Path = {
    _admit_id(id, s"$category evidence id")
    val directory = _content_core_directory(project, category)
    val destination = directory.resolve(s"$id.yaml").normalize()
    if (!destination.startsWith(directory) || Files.exists(destination, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(destination)) {
      _operation_failure(s"$category evidence already exists: $id")
    }
    try {
      Files.writeString(destination, content, StandardCharsets.UTF_8)
      destination
    } catch {
      case NonFatal(_) => _path_failure("Content Core evidence cannot be written")
    }
  }

  private def _content_core_directory(project: Path, category: String): Path = {
    val evidence = project.resolve("evidence").normalize()
    val contentcore = evidence.resolve("content-core").normalize()
    val directory = contentcore.resolve(category).normalize()
    if (!evidence.startsWith(project) || !contentcore.startsWith(project) || !directory.startsWith(project)) {
      _path_failure("Content Core evidence directory must be contained in the project")
    }
    _ensure_directory(project, evidence, "evidence directory")
    _ensure_directory(evidence, contentcore, "Content Core evidence directory")
    _ensure_directory(contentcore, directory, s"$category evidence directory")
    directory
  }

  private def _ensure_directory(parent: Path, path: Path, label: String): Unit = {
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      CozyDocumentProject._direct_directory(path, label)
    } else {
      CozyDocumentProject._direct_directory(parent, s"$label parent")
      try Files.createDirectory(path) catch {
        case NonFatal(_) => _path_failure(s"$label cannot be created")
      }
    }
  }

  private def _existing_directory(project: Path, category: String, label: String): Path =
    _existing_directory_optional(project, category, label).getOrElse(_operation_failure(s"unknown candidate: $category"))

  private def _existing_directory_optional(project: Path, category: String, label: String): Option[Path] = {
    val evidence = project.resolve("evidence").normalize()
    val contentcore = evidence.resolve("content-core").normalize()
    val directory = contentcore.resolve(category).normalize()
    if (!directory.startsWith(project)) {
      _path_failure(s"$label escapes the project")
    }
    if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
      if (Files.exists(evidence, LinkOption.NOFOLLOW_LINKS)) {
        CozyDocumentProject._direct_directory(evidence, "evidence directory")
      }
      if (Files.exists(contentcore, LinkOption.NOFOLLOW_LINKS)) {
        CozyDocumentProject._direct_directory(contentcore, "Content Core evidence directory")
      }
      None
    } else {
      CozyDocumentProject._direct_directory(evidence, "evidence directory")
      CozyDocumentProject._direct_directory(contentcore, "Content Core evidence directory")
      CozyDocumentProject._direct_directory(directory, label)
      Some(directory)
    }
  }

  private def _direct_files(directory: Path, label: String): Vector[Path] = {
    val stream = Files.list(directory)
    try {
      stream.iterator().asScala.toVector.sortBy(_.getFileName.toString).map { path =>
        if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
          _path_failure(s"$label must contain only direct regular non-symlink files")
        }
        path
      }
    } finally {
      stream.close()
    }
  }

  private def _diagnostics(value: Json, label: String): Vector[String] =
    value.asArray.getOrElse(_cli_failure(s"$label must be an array")).map { item =>
      _exact(item.asString.getOrElse(_cli_failure(s"$label must contain only strings")), label)
    }.toVector

  private def _object(value: Json, label: String): Map[String, Json] =
    value.asObject.map(_.toMap).getOrElse(_cli_failure(s"$label must be an object"))

  private def _field(fields: Map[String, Json], name: String, label: String): Json =
    fields.getOrElse(name, _cli_failure(s"$label is missing $name"))

  private def _string(fields: Map[String, Json], name: String, label: String): String =
    _field(fields, name, label).asString.getOrElse(_cli_failure(s"$label $name must be a string"))

  private def _raw(value: String, label: String): String = {
    if (value.isEmpty) _cli_failure(s"$label must be non-empty")
    value
  }

  private def _candidate_text(value: String, label: String): String = {
    if (value.isEmpty || value.trim != value) _cli_failure(s"$label must be a non-empty trimmed string")
    value
  }

  private def _exact(value: String, label: String): String = {
    if (value.isEmpty || value != value.trim) _cli_failure(s"$label must be a non-empty exact string")
    value
  }

  private def _id(value: String, label: String): String = {
    if (!_slug_pattern.pattern.matcher(value).matches()) _descriptor_failure(s"$label must be a slug")
    value
  }

  private def _admit_id(value: String, label: String): Unit = {
    _id(value, label)
    ()
  }

  private def _yaml_string(value: String): String = Json.fromString(value).noSpaces

  private def _cli_failure(message: String): Nothing = CozyDocumentProject._failure("DP-CLI-001", message)

  private def _path_failure(message: String): Nothing = CozyDocumentProject._failure("DP-PATH-001", message)

  private def _descriptor_failure(message: String): Nothing = CozyDocumentProject._failure("DP-DESC-001", message)

  private def _operation_failure(message: String): Nothing = CozyDocumentProject._failure("DP-OP-001", message)
}
