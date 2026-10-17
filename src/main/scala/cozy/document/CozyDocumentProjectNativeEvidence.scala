package cozy.document

import cozy.document.CozyDocumentProjectEvidence.{Attempt, AttemptOutput, FileReference, NativeAcceptedClosure, NativeClosure, NativeFailedClosure}
import io.circe.Json
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths}
import java.util.UUID
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource

/*
 * @since   Sep. 10, 2026
 * @version Oct.  8, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyDocumentProjectNativeEvidence {
  private val _attempt_v2_schema = "cozy.document-operation-attempt.v2"

  def captureNativeInputs(
    project: Path,
    descriptor: CozyDocumentProject.Descriptor,
    operation: CozyDocumentWorkflow.LogicalOperation
  ): Vector[FileReference] =
    nativeInputPaths(descriptor, operation).map { relative =>
      CozyDocumentProject._direct_file(project, relative, "native provider direct input")
      FileReference(relative)
    }

  private[cozy] def nativeInputPaths(
    descriptor: CozyDocumentProject.Descriptor,
    operation: CozyDocumentWorkflow.LogicalOperation
  ): Vector[String] =
    _native_input_paths(descriptor, operation)

  def closeNativeExecution(
    project: Path,
    descriptor: CozyDocumentProject.Descriptor,
    operation: CozyDocumentWorkflow.LogicalOperation,
    inputs: Vector[FileReference],
    result: CozyDocumentProjectProvider.ProviderResult
  ): NativeClosure = result match {
    case CozyDocumentProjectProvider.Executed(outputs, diagnostics, receipt) =>
      val validation = try {
        _validate_native_executed(project, descriptor, operation, inputs, outputs, diagnostics, receipt)
      } catch {
        case NonFatal(error) => Left(s"native provider acceptance failed: ${Option(error.getMessage).getOrElse(error.getClass.getSimpleName)}")
      }
      validation match {
        case Right(_) =>
          val attempt = _append_v2_attempt(project, descriptor, operation, inputs, "accepted", diagnostics, outputs, Some(receipt))
          NativeAcceptedClosure(attempt, diagnostics)
        case Left(diagnostic) =>
          val diagnostics = Vector(diagnostic)
          val attempt = _append_v2_attempt(project, descriptor, operation, inputs, "failed", diagnostics, Vector.empty, None)
          NativeFailedClosure(attempt, diagnostics)
      }
    case CozyDocumentProjectProvider.Failed(_, _, _, diagnostics) =>
      val failurediagnostics = _failure_diagnostics(diagnostics, "native provider reported failure")
      val attempt = _append_v2_attempt(project, descriptor, operation, inputs, "failed", failurediagnostics, Vector.empty, None)
      NativeFailedClosure(attempt, failurediagnostics)
    case CozyDocumentProjectProvider.Blocked(_, _, _, _, _) =>
      _invalid("blocked native provider result must not close an execution attempt")
  }

  def parseRetainedAttempt(
    project: Path,
    descriptor: CozyDocumentProject.Descriptor,
    path: Path
  ): Attempt = {
    val label = "Document Project retained v2 attempt"
    val expectedorder = Vector("schema", "id", "operation", "provider", "profile", "inputs", "outcome", "diagnostics", "outputs", "receipt")
    _require_top_level_order(path, expectedorder, label)
    val fields = _object(_load_json(path, label), label)
    if (fields.keySet != expectedorder.toSet || _string(fields, "schema", label) != _attempt_v2_schema)
      _invalid("Document Project retained attempt is not a closed cozy.document-operation-attempt.v2 document")
    val attemptid = _string(fields, "id", label)
    val canonicalid = try UUID.fromString(attemptid).toString catch {
      case NonFatal(_) => _invalid("Document Project retained v2 attempt id must be a canonical UUID")
    }
    if (canonicalid != attemptid || path.getFileName.toString != s"$attemptid.yaml")
      _invalid("Document Project retained v2 attempt id must match its filename")
    val operationid = _string(fields, "operation", label)
    val operation = CozyDocumentWorkflow.declaredOperation(operationid) match {
      case Right(Some(value)) if value.id == "article.render-review" => value
      case _ => _invalid("Document Project retained v2 attempt operation must be article.render-review")
    }
    val declaration = CozyDocumentWorkflow.nativeProviderDeclaration(operation.id) match {
      case Right(Some(value)) => value
      case _ => _invalid("Document Project retained v2 attempt has no declared native provider")
    }
    if (_string(fields, "provider", label) != operation.providerBinding ||
        declaration.providerBinding != operation.providerBinding ||
        _string(fields, "profile", label) != descriptor.profile)
      _invalid("Document Project retained v2 attempt provider or profile is invalid")
    val resolved = CozyDocumentWorkflow.resolve(descriptor.profile, descriptor.activeOptionalWorkProducts) match {
      case Right(value) => value
      case Left(cause) => _invalid(cause)
    }
    if (!operation.produces.exists(id => resolved.workProducts.exists(value => value.workProduct.id == id && value.isParticipating)))
      _invalid("Document Project retained v2 attempt operation does not produce a selected Work Product")
    val inputs = _native_attempt_inputs(
      descriptor,
      operation,
      _field(fields, "inputs", label)
    )
    val diagnostics = _field(fields, "diagnostics", label).asArray.getOrElse(
      _invalid("Document Project retained v2 attempt diagnostics must be an array")
    ).map { value =>
      _nonempty(
        value.asString.getOrElse(_invalid("Document Project retained v2 attempt diagnostics must contain only strings")),
        "Document Project retained v2 attempt diagnostic"
      )
    }.toVector
    if (diagnostics.isEmpty)
      _invalid("Document Project retained v2 attempt diagnostics must be non-empty")
    val outcome = _string(fields, "outcome", label)
    val outputvalues = _field(fields, "outputs", label).asArray.getOrElse(
      _invalid("Document Project retained v2 attempt outputs must be an array")
    )
    val outputs = outcome match {
      case "accepted" =>
        if (outputvalues.size != declaration.outputs.size)
          _invalid("Document Project retained accepted v2 attempt outputs must match the native declaration")
        val parsed = outputvalues.zip(declaration.outputs).map {
          case (value, declared) => _native_attempt_output(value, declared)
        }.toVector
        val receiptfields = _object(
          _field(fields, "receipt", label),
          "Document Project retained accepted v2 attempt receipt"
        )
        if (receiptfields.keySet != Set("identity", "value"))
          _invalid("Document Project retained accepted v2 attempt receipt must have exactly identity and value")
        val receiptidentity = _nonempty(
          _string(receiptfields, "identity", "Document Project retained accepted v2 attempt receipt"),
          "Document Project retained accepted v2 attempt receipt identity"
        )
        val receiptvalue = _nonempty(
          _string(receiptfields, "value", "Document Project retained accepted v2 attempt receipt"),
          "Document Project retained accepted v2 attempt receipt value"
        )
        val output = parsed.head
        if (receiptidentity != CozyDocumentProjectProvider.NATIVE_RECEIPT_IDENTITY ||
            receiptvalue != CozyDocumentProjectProvider.nativeReceiptValue(operation.id, output.path, output.mediaType))
          _invalid("Document Project retained accepted v2 attempt receipt is not the canonical native receipt")
        parsed
      case "failed" =>
        if (outputvalues.nonEmpty || _string(fields, "receipt", label) != "none")
          _invalid("Document Project retained failed v2 attempt must have no outputs and receipt none")
        Vector.empty
      case _ => _invalid("Document Project retained v2 attempt outcome must be accepted or failed")
    }
    Attempt(
      FileReference(CozyDocumentProject._project_relative(project, path)),
      _attempt_v2_schema,
      operation.id,
      outcome,
      operation.produces,
      inputs,
      outputs
    )
  }

  def nativeOutputsAvailable(project: Path, attempt: Attempt): Boolean =
    attempt.inputs.forall(reference => fileAvailable(project, reference)) &&
      attempt.outputs.nonEmpty && attempt.outputs.forall { output =>
        fileAvailable(project, FileReference(output.path)) && Files.size(project.resolve(output.path)) > 0
      }

  def fileAvailable(project: Path, reference: FileReference): Boolean =
    _file_available(project, reference)

  private def _validate_native_executed(
    project: Path,
    descriptor: CozyDocumentProject.Descriptor,
    operation: CozyDocumentWorkflow.LogicalOperation,
    inputs: Vector[FileReference],
    outputs: Vector[CozyDocumentProjectProvider.ProviderOutput],
    diagnostics: Vector[String],
    receipt: CozyDocumentProjectProvider.ProviderReceipt
  ): Either[String, Unit] = {
    val declaration = CozyDocumentWorkflow.nativeProviderDeclaration(operation.id) match {
      case Right(Some(value)) => value
      case _ => return Left("native provider acceptance has no declared native output contract")
    }
    if (operation.id != "article.render-review" || declaration.providerBinding != operation.providerBinding)
      return Left("native provider acceptance is outside the sole article review binding")
    if (inputs.map(_.path) != nativeInputPaths(descriptor, operation) ||
        !inputs.forall(reference => _file_available(project, reference)))
      return Left("native provider direct input paths are invalid or unavailable")
    if (diagnostics.isEmpty || diagnostics.exists(value => value.trim.isEmpty))
      return Left("native provider returned empty diagnostics")
    if (outputs.size != declaration.outputs.size)
      return Left("native provider output count does not match the declared output contract")
    outputs.zip(declaration.outputs).foreach {
      case (output, declared) =>
        if (output.identity != declared.identity || output.path != declared.path || output.mediaType != declared.mediaType)
          return Left("native provider output identity, path, or media type does not match its declaration")
        val path = CozyDocumentProject._direct_file(project, output.path, "native provider accepted output")
        if (Files.size(path) == 0)
          return Left("native provider output must contain direct HTML bytes")
    }
    val output = outputs.head
    if (receipt.identity != CozyDocumentProjectProvider.NATIVE_RECEIPT_IDENTITY ||
        receipt.value != CozyDocumentProjectProvider.nativeReceiptValue(operation.id, output.path, output.mediaType))
      Left("native provider receipt must record operation, output path, and media type")
    else Right(())
  }

  private def _append_v2_attempt(
    project: Path,
    descriptor: CozyDocumentProject.Descriptor,
    operation: CozyDocumentWorkflow.LogicalOperation,
    inputs: Vector[FileReference],
    outcome: String,
    diagnostics: Vector[String],
    outputs: Vector[CozyDocumentProjectProvider.ProviderOutput],
    receipt: Option[CozyDocumentProjectProvider.ProviderReceipt]
  ): Attempt = {
    if (!Set("accepted", "failed").contains(outcome) || diagnostics.isEmpty || diagnostics.exists(_.trim.isEmpty))
      _invalid("native operation attempt outcome or diagnostics are invalid")
    if (outcome == "accepted" && (outputs.isEmpty || receipt.isEmpty))
      _invalid("accepted native operation attempt requires outputs and a receipt")
    if (outcome == "failed" && (outputs.nonEmpty || receipt.nonEmpty))
      _invalid("failed native operation attempt must not contain outputs or a receipt")
    val attemptid = UUID.randomUUID().toString
    val yaml = _v2_attempt_yaml(attemptid, descriptor, operation, inputs, outcome, diagnostics, outputs, receipt)
    val directory = _native_attempt_directory(project)
    val destination = directory.resolve(s"$attemptid.yaml").normalize()
    if (!destination.startsWith(directory) || Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
      CozyDocumentProject._failure("DP-PATH-001", "native operation attempt destination is unsafe or already exists")
    try {
      Files.writeString(destination, yaml, StandardCharsets.UTF_8)
      Attempt(
        FileReference(CozyDocumentProject._project_relative(project, destination)),
        _attempt_v2_schema,
        operation.id,
        outcome,
        operation.produces,
        inputs,
        outputs.map(output => AttemptOutput(output.identity, output.path, output.mediaType))
      )
    } catch {
      case NonFatal(_) => CozyDocumentProject._failure("DP-PATH-001", "native operation attempt cannot be written")
    }
  }

  private def _native_attempt_directory(project: Path): Path = {
    val evidence = project.resolve("evidence").normalize()
    val directory = evidence.resolve("attempts").normalize()
    if (!evidence.startsWith(project) || !directory.startsWith(evidence))
      CozyDocumentProject._failure("DP-PATH-001", "native operation evidence directory escapes the project")
    try {
      if (!Files.exists(evidence, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(evidence)
      CozyDocumentProject._direct_directory(evidence, "evidence directory")
      if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) Files.createDirectory(directory)
      CozyDocumentProject._direct_directory(directory, "attempts directory")
      directory
    } catch {
      case NonFatal(_) => CozyDocumentProject._failure("DP-PATH-001", "native operation evidence directory cannot be prepared")
    }
  }

  private def _v2_attempt_yaml(
    attemptid: String,
    descriptor: CozyDocumentProject.Descriptor,
    operation: CozyDocumentWorkflow.LogicalOperation,
    inputs: Vector[FileReference],
    outcome: String,
    diagnostics: Vector[String],
    outputs: Vector[CozyDocumentProjectProvider.ProviderOutput],
    receipt: Option[CozyDocumentProjectProvider.ProviderReceipt]
  ): String = {
    val inputyaml = inputs.flatMap { input =>
      Vector(s"  - path: ${input.path}")
    }
    val diagnosticyaml = diagnostics.map(value => s"  - ${_yaml_double_quoted(value)}")
    val outputyaml = if (outputs.isEmpty) Vector("  []") else outputs.flatMap { output =>
      Vector(
        s"  - identity: ${output.identity}",
        s"    path: ${output.path}",
        s"    mediaType: ${output.mediaType}"
      )
    }
    val receiptyaml = receipt match {
      case Some(value) => Vector("receipt:", "  identity: " + value.identity, "  value: " + _yaml_double_quoted(value.value))
      case None => Vector("receipt: none")
    }
    (Vector(
      s"schema: ${_attempt_v2_schema}",
      s"id: $attemptid",
      s"operation: ${operation.id}",
      s"provider: ${operation.providerBinding}",
      s"profile: ${descriptor.profile}",
      "inputs:"
    ) ++ inputyaml ++ Vector(
      s"outcome: $outcome",
      "diagnostics:"
    ) ++ diagnosticyaml ++ Vector("outputs:") ++ outputyaml ++ receiptyaml).mkString("\n") + "\n"
  }

  private def _native_attempt_inputs(
    descriptor: CozyDocumentProject.Descriptor,
    operation: CozyDocumentWorkflow.LogicalOperation,
    value: Json
  ): Vector[FileReference] = {
    val inputvalues = value.asArray.getOrElse(_invalid("Document Project retained v2 attempt inputs must be an array"))
    val expected = nativeInputPaths(descriptor, operation)
    if (inputvalues.size != expected.size)
      _invalid("Document Project retained v2 attempt inputs must match direct native input order")
    inputvalues.zip(expected).map { case (inputvalue, expectedpath) =>
      val identity = _retained_file_reference(inputvalue, "Document Project retained v2 attempt input")
      if (identity.path != expectedpath)
        _invalid("Document Project retained v2 attempt inputs must match direct native input order")
      identity
    }.toVector
  }

  private def _native_attempt_output(
    value: Json,
    declared: CozyDocumentWorkflow.OutputDeclaration
  ): AttemptOutput = {
    val fields = _object(value, "Document Project retained accepted v2 attempt output")
    if (fields.keySet != Set("identity", "path", "mediaType"))
      _invalid("Document Project retained accepted v2 attempt output must have exactly identity, path, mediaType")
    val identity = _nonempty(
      _string(fields, "identity", "Document Project retained accepted v2 attempt output"),
      "Document Project retained accepted v2 attempt output identity"
    )
    val path = _string(fields, "path", "Document Project retained accepted v2 attempt output")
    val mediatype = _nonempty(
      _string(fields, "mediaType", "Document Project retained accepted v2 attempt output"),
      "Document Project retained accepted v2 attempt output mediaType"
    )
    _retained_reference(path, "Document Project retained accepted v2 attempt output")
    if (identity != declared.identity || path != declared.path || mediatype != declared.mediaType)
      _invalid("Document Project retained accepted v2 attempt output does not match the native declaration")
    AttemptOutput(identity, path, mediatype)
  }

  private def _native_input_paths(
    descriptor: CozyDocumentProject.Descriptor,
    operation: CozyDocumentWorkflow.LogicalOperation
  ): Vector[String] =
    if (operation.id == "article.render-review")
      Vector(descriptor.contentCore, "index.dox", "presentation/visual-pages.yaml", "infographic/infographic.svg")
    else _invalid("native direct input paths are unsupported for this operation")

  private def _file_available(project: Path, reference: FileReference): Boolean = {
    val path = project.resolve(reference.path).normalize()
    if (!path.startsWith(project)) false
    else {
      val relative = project.relativize(path)
      var parent = project
      val directancestors = (0 until relative.getNameCount - 1).forall { index =>
        parent = parent.resolve(relative.getName(index))
        !Files.isSymbolicLink(parent) && Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
      }
      directancestors &&
        !Files.isSymbolicLink(path) &&
        Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
        Files.isReadable(path)
    }
  }

  private def _retained_file_reference(value: Json, label: String): FileReference = {
    val fields = _object(value, label)
    if (fields.keySet != Set("path")) _invalid(s"$label must have exactly path")
    _retained_reference(_string(fields, "path", label), label)
  }

  private def _retained_reference(path: String, label: String): FileReference = {
    if (!_relative_path(path)) _invalid(s"$label path is invalid")
    FileReference(path)
  }

  private def _failure_diagnostics(diagnostics: Vector[String], fallback: String): Vector[String] = {
    val retained = diagnostics.filter(_.trim.nonEmpty)
    if (retained.nonEmpty) retained else Vector(fallback)
  }

  private def _load_json(path: Path, label: String): Json = {
    try {
      Files.readString(path, StandardCharsets.UTF_8)
      StructuredDocumentLoader.loadJson(InputSource(path.toFile)).take
    } catch {
      case NonFatal(_) => _invalid(s"$label is missing, unreadable, or malformed")
    }
  }

  private def _require_top_level_order(path: Path, expected: Vector[String], label: String): Unit = {
    val keys = try {
      Files.readAllLines(path, StandardCharsets.UTF_8).asScala.collect {
        case line if line.nonEmpty && !line.startsWith(" ") && !line.startsWith("\t") && line.contains(":") =>
          line.takeWhile(_ != ':').trim
      }.toVector
    } catch {
      case NonFatal(_) => _invalid("Document Project evidence sidecar cannot be read")
    }
    if (keys != expected)
      _invalid(s"$label top-level keys must be ordered ${expected.mkString(", ")}")
  }

  private def _yaml_double_quoted(value: String): String = {
    val builder = new StringBuilder("\"")
    value.foreach {
      case '\\' => builder.append("\\\\")
      case '"' => builder.append("\\\"")
      case '\r' => builder.append("\\r")
      case '\n' => builder.append("\\n")
      case '\t' => builder.append("\\t")
      case character if Character.isISOControl(character) => builder.append(f"\\u${character.toInt}%04x")
      case character => builder.append(character)
    }
    builder.append('"').result()
  }

  private def _object(value: Json, label: String): Map[String, Json] =
    value.asObject.map(_.toMap).getOrElse(_invalid(s"$label must be an object"))

  private def _field(fields: Map[String, Json], name: String, label: String): Json =
    fields.getOrElse(name, _invalid(s"$label is missing $name"))

  private def _string(fields: Map[String, Json], name: String, label: String): String =
    _field(fields, name, label).asString.getOrElse(_invalid(s"$label $name must be a string"))

  private def _nonempty(value: String, label: String): String = {
    if (value == null || value.isEmpty || value != value.trim)
      _invalid(s"$label must be a non-empty exact string")
    value
  }
  private def _relative_path(value: String): Boolean = {
    val path = try {
      Paths.get(value)
    } catch {
      case NonFatal(_) => return false
    }
    value.nonEmpty && !path.isAbsolute && path.iterator().asScala.forall(part => part.toString != "." && part.toString != "..")
  }
  private def _invalid(message: String): Nothing = CozyDocumentProject._descriptor_failure(message)
}
