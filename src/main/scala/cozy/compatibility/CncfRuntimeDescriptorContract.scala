package cozy.compatibility

import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import scala.util.control.NonFatal
import org.goldenport.RAISE
import cozy.config.CozyProjectYamlConfig

/*
 * @since   Jul. 27, 2026
 * @version Jul. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CncfRuntimeDescriptorContract {
  val SUPPORTED_SCHEMA_VERSION = "1"
  val SUPPORTED_RUNTIME = "cncf"
  val SUPPORTED_PREDEFINED_RESULT_SCHEMA = "cncf.predefined-result.v1"

  sealed trait DiagnosticCode {
    def name: String
  }
  object DiagnosticCode {
    case object DescriptorMissing extends DiagnosticCode {
      val name = "CNCF_DESCRIPTOR_MISSING"
    }
    case object DescriptorUnreadable extends DiagnosticCode {
      val name = "CNCF_DESCRIPTOR_UNREADABLE"
    }
    case object DigestInvalid extends DiagnosticCode {
      val name = "CNCF_DESCRIPTOR_DIGEST_INVALID"
    }
    case object DigestMismatch extends DiagnosticCode {
      val name = "CNCF_DESCRIPTOR_DIGEST_MISMATCH"
    }
    case object DescriptorMalformed extends DiagnosticCode {
      val name = "CNCF_DESCRIPTOR_MALFORMED"
    }
    case object SchemaMismatch extends DiagnosticCode {
      val name = "CNCF_DESCRIPTOR_SCHEMA_MISMATCH"
    }
    case object RuntimeMismatch extends DiagnosticCode {
      val name = "CNCF_DESCRIPTOR_RUNTIME_MISMATCH"
    }
    case object TargetMismatch extends DiagnosticCode {
      val name = "CNCF_DESCRIPTOR_TARGET_MISMATCH"
    }
    case object PredefinedResultSchemaMismatch extends DiagnosticCode {
      val name = "CNCF_PREDEFINED_RESULT_SCHEMA_MISMATCH"
    }
    case object ArgumentMissing extends DiagnosticCode {
      val name = "CNCF_DESCRIPTOR_ARGUMENT_MISSING"
    }
    case object SourceConflict extends DiagnosticCode {
      val name = "CNCF_DESCRIPTOR_SOURCE_CONFLICT"
    }
  }

  final case class Diagnostic(
    code: DiagnosticCode,
    source: String,
    expected: String,
    actual: String,
    message: String,
    correctiveAction: String
  ) {
    def render: String =
      Vector(
        "code" -> code.name,
        "source" -> source,
        "expected" -> expected,
        "actual" -> actual,
        "message" -> message,
        "correctiveAction" -> correctiveAction
      ).map { case (key, value) =>
        s""""${_escape(key)}":"${_escape(value)}""""
      }.mkString("{", ",", "}")

    private def _escape(value: String): String =
      value.flatMap {
        case '"' => "\\\""
        case '\\' => "\\\\"
        case '\n' => "\\n"
        case '\r' => "\\r"
        case '\t' => "\\t"
        case character => character.toString
      }
  }

  final case class ValidatedDescriptor(
    path: Path,
    targetVersion: String,
    sha256: String,
    config: CozyProjectYamlConfig.Config
  )

  def requireValidInvocation(args: Seq[String], source: String): Option[ValidatedDescriptor] = {
    val command = args.find(x => Set("modeler-scala", "modeler-scala-value", "car-sbt-project").contains(x))
    if (command.isEmpty)
      None
    else {
      val target = _option(args, "cncf-version", source)
      val descriptor = _option(args, "cncf-runtime-descriptor", source)
      val digest = _option(args, "cncf-runtime-descriptor-sha256", source)
      if (descriptor.isEmpty && digest.isEmpty)
        None
      else {
        val missing = Vector(
          _required_diagnostic(source, "cncf-version", target),
          _required_diagnostic(source, "cncf-runtime-descriptor", descriptor),
          _required_diagnostic(source, "cncf-runtime-descriptor-sha256", digest)
        ).flatten
        if (missing.nonEmpty)
          _raise(missing)
        Some(requireValidDescriptor(
          Paths.get(descriptor.get),
          target.get,
          Some(digest.get),
          source
        ))
      }
    }
  }

  def requireConsistentSourceValues(
    source: String,
    optionName: String,
    values: Seq[(String, String)]
  ): Unit = {
    val normalized = values.map { case (label, value) => label -> value.trim }.
      filter(_._2.nonEmpty)
    if (normalized.map(_._2).distinct.size > 1)
      _raise(Vector(_source_conflict_diagnostic(source, optionName, normalized)))
  }

  def requireValidDescriptor(
    path: Path,
    targetVersion: String,
    expectedSha256: Option[String],
    source: String
  ): ValidatedDescriptor =
    validateDescriptor(path, targetVersion, expectedSha256, source) match {
      case Right(validated) =>
        validated
      case Left(diagnostics) =>
        _raise(diagnostics)
    }

  def validateDescriptor(
    path: Path,
    targetVersion: String,
    expectedSha256: Option[String],
    source: String
  ): Either[Vector[Diagnostic], ValidatedDescriptor] = {
    val normalized = path.toAbsolutePath.normalize()
    if (!Files.isRegularFile(normalized))
      Left(Vector(_diagnostic(
        DiagnosticCode.DescriptorMissing,
        source,
        normalized.toString,
        "missing",
        s"CNCF runtime descriptor does not exist: $normalized",
        "Generate or extract the selected CNCF runtime descriptor before source generation."
      )))
    else {
      _read_bytes(normalized, source).flatMap { bytes =>
        val actualsha = _sha256_bytes(bytes)
        val digestdiagnostics = expectedSha256.toVector.flatMap { expected =>
          if (!expected.matches("[0-9a-f]{64}"))
            Vector(_diagnostic(
              DiagnosticCode.DigestInvalid,
              source,
              "64 lowercase hexadecimal SHA-256 characters",
              expected,
              s"Invalid CNCF runtime descriptor SHA-256: $expected",
              "Pass the SHA-256 emitted by the owning build without rewriting it."
            ))
          else if (expected != actualsha)
            Vector(_diagnostic(
              DiagnosticCode.DigestMismatch,
              source,
              expected,
              actualsha,
              s"CNCF runtime descriptor SHA-256 does not match the selected descriptor: $normalized",
              "Regenerate or re-extract the descriptor and pass its exact SHA-256."
            ))
          else
            Vector.empty
        }
        if (digestdiagnostics.nonEmpty)
          Left(digestdiagnostics)
        else
          _load_and_validate(normalized, bytes, targetVersion, actualsha, source)
      }
    }
  }

  def sha256(path: Path): String =
    _sha256_bytes(Files.readAllBytes(path))

  private def _read_bytes(
    path: Path,
    source: String
  ): Either[Vector[Diagnostic], Array[Byte]] =
    try
      Right(Files.readAllBytes(path))
    catch {
      case NonFatal(exception) =>
        Left(Vector(_diagnostic(
          DiagnosticCode.DescriptorUnreadable,
          source,
          "readable CNCF runtime descriptor bytes",
          Option(exception.getMessage).getOrElse(exception.getClass.getName),
          s"CNCF runtime descriptor cannot be read: $path",
          "Restore or regenerate the descriptor and ensure it remains readable during generation."
        )))
    }

  private def _load_and_validate(
    path: Path,
    bytes: Array[Byte],
    targetversion: String,
    actualsha: String,
    source: String
  ): Either[Vector[Diagnostic], ValidatedDescriptor] =
    try {
      val config = CozyProjectYamlConfig.parsePublic(bytes)
      val diagnostics = Vector(
        _field_diagnostic(
          config.value("schemaVersion"),
          SUPPORTED_SCHEMA_VERSION,
          DiagnosticCode.SchemaMismatch,
          source,
          "CNCF runtime descriptor schemaVersion",
          s"Unsupported CNCF runtime descriptor schemaVersion",
          "Use the schema emitted by the selected CNCF runtime."
        ),
        _field_diagnostic(
          config.value("runtime"),
          SUPPORTED_RUNTIME,
          DiagnosticCode.RuntimeMismatch,
          source,
          "CNCF runtime descriptor runtime",
          "Unsupported CNCF runtime descriptor runtime",
          "Pass a CNCF runtime descriptor."
        ),
        _target_diagnostic(
          config.value("version"),
          targetversion,
          source
        ),
        _field_diagnostic(
          config.value("predefinedResults.schemaVersion"),
          SUPPORTED_PREDEFINED_RESULT_SCHEMA,
          DiagnosticCode.PredefinedResultSchemaMismatch,
          source,
          "CNCF predefined Result catalog schema",
          "Unsupported CNCF predefined Result catalog schema",
          "Use the predefined Result schema emitted by the selected CNCF runtime."
        )
      ).flatten
      if (diagnostics.nonEmpty)
        Left(diagnostics)
      else
        Right(ValidatedDescriptor(path, targetversion, actualsha, config))
    } catch {
      case NonFatal(exception) =>
        Left(Vector(_diagnostic(
          DiagnosticCode.DescriptorMalformed,
          source,
          "parseable CNCF runtime descriptor",
          Option(exception.getMessage).getOrElse(exception.getClass.getName),
          s"Malformed CNCF runtime descriptor: $path",
          "Regenerate the descriptor from the selected CNCF runtime."
        )))
    }

  private def _sha256_bytes(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").
      digest(bytes).
      map(byte => f"${byte & 0xff}%02x").
      mkString

  private def _field_diagnostic(
    actual: Option[String],
    expected: String,
    code: DiagnosticCode,
    source: String,
    label: String,
    messageprefix: String,
    correctiveaction: String
  ): Option[Diagnostic] =
    actual match {
      case Some(value) if value == expected =>
        None
      case Some(value) =>
        Some(_diagnostic(
          code,
          source,
          expected,
          value,
          s"$messageprefix $value; expected $expected",
          correctiveaction
        ))
      case None =>
        Some(_diagnostic(
          code,
          source,
          expected,
          "missing",
          s"CNCF runtime descriptor requires $label",
          correctiveaction
        ))
    }

  private def _required_diagnostic(
    source: String,
    option: String,
    value: Option[String]
  ): Option[Diagnostic] =
    value.filter(_.nonEmpty).fold[Option[Diagnostic]](
      Some(_diagnostic(
        DiagnosticCode.ArgumentMissing,
        source,
        s"--$option <value>",
        "missing",
        s"CNCF descriptor validation requires --$option",
        "Pass the exact target, descriptor path, and descriptor SHA-256 together."
      ))
    )(_ => None)

  private def _target_diagnostic(
    actual: Option[String],
    expected: String,
    source: String
  ): Option[Diagnostic] =
    actual match {
      case Some(value) if value == expected =>
        None
      case Some(value) =>
        Some(_diagnostic(
          DiagnosticCode.TargetMismatch,
          source,
          expected,
          value,
          s"CNCF runtime descriptor version $value does not match selected runtime $expected",
          "Select a descriptor whose exact version matches --cncf-version."
        ))
      case None =>
        Some(_diagnostic(
          DiagnosticCode.TargetMismatch,
          source,
          expected,
          "missing",
          "CNCF runtime descriptor requires version",
          "Select a descriptor whose exact version matches --cncf-version."
        ))
    }

  private def _option(args: Seq[String], name: String, source: String): Option[String] = {
    val inlineprefix = s"--$name="
    val values = args.zipWithIndex.flatMap {
      case (value, _) if value.startsWith(inlineprefix) =>
        Some(value.drop(inlineprefix.length).trim)
      case (value, index) if value == s"--$name" && index + 1 < args.length =>
        Some(args(index + 1).trim)
      case _ =>
        None
    }
    requireConsistentSourceValues(
      source,
      s"--$name",
      values.zipWithIndex.map { case (value, index) => s"argument-${index + 1}" -> value }
    )
    values.find(_.nonEmpty)
  }

  private def _source_conflict_diagnostic(
    source: String,
    optionname: String,
    values: Seq[(String, String)]
  ): Diagnostic =
    _diagnostic(
      DiagnosticCode.SourceConflict,
      source,
      s"one consistent $optionname value",
      values.map { case (label, value) => s"$label=$value" }.mkString(","),
      s"Conflicting CNCF descriptor contract values were supplied for $optionname",
      "Make project, owning-build bridge, and command-line generation values agree exactly."
    )

  private def _diagnostic(
    code: DiagnosticCode,
    source: String,
    expected: String,
    actual: String,
    message: String,
    correctiveaction: String
  ): Diagnostic =
    Diagnostic(code, source, expected, actual, message, correctiveaction)

  private def _raise(diagnostics: Vector[Diagnostic]): Nothing =
    RAISE.invalidArgumentFault(diagnostics.map(_.render).mkString("[", ",", "]"))
}
