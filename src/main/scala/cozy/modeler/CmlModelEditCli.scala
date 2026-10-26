package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import scala.util.control.NonFatal
import play.api.libs.json._
import CmlModelEditModel._
import CmlModelEditJson._

/*
 * @since   Oct. 27, 2026
 * @version Oct. 27, 2026
 */
object CmlModelEditCli {
  def main(args: Array[String]): Unit = {
    val result: Either[Vector[Diagnostic], JsValue] = args.toVector match {
      case Vector("produce", path, owner, identity, revision) =>
        CmlModelEditProduction.produce(Paths.get(path), Source(owner, identity, revision)).map(Json.toJson(_))
      case Vector("render-candidate", path, owner, identity, revision, candidate) =>
        _json_file(candidate).flatMap(CmlModelEditProduction.readCandidate).
          flatMap(CmlModelEditProduction.renderCandidate(Paths.get(path), Source(owner, identity, revision), _)).map(Json.toJson(_))
      case Vector("read", path) =>
        _json_file(path).flatMap(CmlModelEditProduction.read).map(Json.toJson(_))
      case _ => Left(Vector(Diagnostic("usage", "arguments", "produce <source.cml> <owner> <identity> <revision>; render-candidate <source.cml> <owner> <identity> <revision> <candidate.json>; read <publication.json>")))
    }
    result match {
      case Right(value) => println(Json.stringify(value))
      case Left(errors) =>
        Console.err.println(Json.stringify(Json.obj("diagnostics" -> Json.toJson(errors))))
        throw new IllegalArgumentException("Model-edit production did not complete; see typed diagnostics.")
    }
  }
  private def _json_file(path: String): Either[Vector[Diagnostic], JsValue] =
    try Right(Json.parse(new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)))
    catch {
      case _: java.io.IOException => Left(Vector(Diagnostic("unavailable", "input", "Selected JSON input is unavailable.")))
      case NonFatal(_) => Left(Vector(Diagnostic("malformed", "input", "Selected input is not a supported JSON document.")))
    }
}
