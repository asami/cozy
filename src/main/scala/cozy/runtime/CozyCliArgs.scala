package cozy.runtime

import org.goldenport.RAISE
import org.goldenport.cli.{Request => CliRequest}
import org.goldenport.cli.spec
import java.nio.file.{Path, Paths}

/*
 * @since   Jun.  4, 2026
 * @version Jun.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyCliArgs {
  final case class Parsed(request: CliRequest) {
    def argument(name: String): Option[String] =
      request.arguments.find(_.name == name).
        orElse(request.arguments.headOption).
        map(_.asString).map(_.trim).filter(_.nonEmpty)

    def arguments: Vector[String] =
      request.arguments.map(_.asString).toVector

    def property(name: String): Option[String] =
      request.properties.find(_.name == name).map(_.asString).map(_.trim).filter(_.nonEmpty)

    def properties(name: String): Vector[String] =
      request.properties.filter(_.name == name).map(_.asString).map(_.trim).filter(_.nonEmpty).toVector

    def pathProperty(name: String): Option[Path] =
      request.properties.find(_.name == name).map(x => toPath(x.value))

    def requiredProperty(name: String): String =
      property(name).getOrElse(RAISE.invalidArgumentFault(s"Missing --$name"))

    def requiredPathProperty(name: String): Path =
      pathProperty(name).getOrElse(RAISE.invalidArgumentFault(s"Missing --$name"))

    def flag(name: String): Boolean =
      request.switches.exists(_.name == name) ||
        property(name).exists(x => x.equalsIgnoreCase("true") || x == "1" || x.equalsIgnoreCase("yes"))
  }

  def parse(parameters: spec.Parameter*)(args: List[String]): Parsed =
    Parsed(spec.Request(parameters: _*).build(CliRequest("cozy"), _normalize_property_assignments(args).map(identity[Any]).toVector))

  def parseStrict(parameters: spec.Parameter*)(args: List[String]): Parsed =
    Parsed(spec.Request(parameters: _*).buildStrict(CliRequest("cozy"), _normalize_property_assignments(args).map(identity[Any]).toVector))

  private def _normalize_property_assignments(args: List[String]): List[String] =
    args.flatMap { arg =>
      if (arg.startsWith("--") && arg.contains("=")) {
        val i = arg.indexOf('=')
        List(arg.take(i), arg.drop(i + 1))
      } else {
        List(arg)
      }
    }

  def toPath(value: Any): Path = value match {
    case m: java.io.File => m.toPath.toAbsolutePath.normalize()
    case m: Path => m.toAbsolutePath.normalize()
    case m => Paths.get(m.toString).toAbsolutePath.normalize()
  }
}
