package cozy.modeler

import java.io.File
import java.nio.file.{Files, Path, Paths}
import scala.util.control.NonFatal

/*
 * @since   Oct. 13, 2026
 * @version Oct. 13, 2026
 */
private[modeler] object DartSdkSupport {
  private val _prerequisite =
    "Set cozy.dart.sdk to an SDK root or direct binary, cozy.dart.executable or " +
      "COZY_DART_EXECUTABLE to a direct binary, DART_SDK to an SDK root, or put dart on PATH."

  def resolve(properties: Map[String, String], environment: Map[String, String]): Either[String, Path] =
    try {
      val explicit = properties.get("cozy.dart.sdk").map(value => (_configured_path(value), true)).
        orElse(properties.get("cozy.dart.executable").map(value => (_configured_path(value), false))).
        orElse(environment.get("COZY_DART_EXECUTABLE").map(value => (_configured_path(value), false))).
        orElse(environment.get("DART_SDK").map(value => (_configured_path(value), true)))
      val selected = explicit.map { case (path, rootallowed) =>
        if (rootallowed && Files.isDirectory(path)) path.resolve("bin/dart") else path
      }.orElse {
        environment.getOrElse("PATH", "").split(java.util.regex.Pattern.quote(File.pathSeparator)).toVector.
          filter(_.nonEmpty).map(directory => Paths.get(directory).resolve("dart")).find(_executable)
      }
      selected match {
        case None => Left(s"A real Dart SDK executable is required. ${_prerequisite}")
        case Some(candidate) if !_executable(candidate) =>
          Left(s"Dart SDK prerequisite is not an executable file: $candidate. ${_prerequisite}")
        case Some(candidate) =>
          val resolved = candidate.toRealPath()
          // Resolve Flutter's adjacent SDK only for implicit PATH discovery; never run its wrapper.
          val direct = if (explicit.isEmpty && !_is_binary(resolved)) {
            resolved.getParent.resolve("cache/dart-sdk/bin/dart")
          } else resolved
          if (!_executable(direct) || !_is_binary(direct))
            Left(s"Dart SDK prerequisite must be a direct native binary, never a shell wrapper: $direct. ${_prerequisite}")
          else Right(direct.toRealPath())
      }
    } catch {
      case NonFatal(error) => Left(s"Unable to resolve the Dart SDK prerequisite: ${error.getMessage}. ${_prerequisite}")
    }

  private def _executable(path: Path): Boolean = Files.isRegularFile(path) && Files.isExecutable(path)

  private def _configured_path(value: String): Path = {
    require(value.nonEmpty, "Dart SDK setting must not be empty")
    Paths.get(value)
  }

  private def _is_binary(path: Path): Boolean = {
    val stream = Files.newInputStream(path)
    val header = new Array[Byte](4)
    val length = try stream.read(header) finally stream.close()
    val magic = header.map(value => f"${value & 0xff}%02x").mkString
    length == 4 && (Set("7f454c46", "feedface", "cefaedfe", "feedfacf", "cffaedfe",
      "cafebabe", "bebafeca", "cafebabf", "bfbafeca")(magic) || magic.startsWith("4d5a"))
  }
}
