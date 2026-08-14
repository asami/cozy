package cozy.modeler

import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import scala.util.control.NonFatal
import org.goldenport.cli._
import org.goldenport.context.Conclusion
import org.goldenport.kaleidox.{Model => KaleidoxModel}
import org.goldenport.sexpr.{SError, SExpr, SImage, SList, SModel, SAtom, STree}
import cozy._

/*
 * @since   Aug. 20, 2025
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */
case object ModelerServiceClass extends ServiceClass {
  val name = "modeler"
  val defaultOperation = None

  private val _model = spec.Parameter.argumentFile("model")
  private val _save = spec.Parameter.propertyFileOption("save")
  private val _charset = spec.Parameter(
    "charset",
    spec.Parameter.PropertyKind,
    spec.XString,
    spec.Multiplicity.ZeroOne
  )
  private val _request = spec.Request(_model, _save, _charset)

  val operations = Operations(
    ClassOperationClass,
    ProjectOperationClass
  )

  case object ClassOperationClass extends OperationClassWithOperation {
    val request = _request
    val response = spec.Response.empty
    val specification = spec.Operation("class", request, response)

    override def makeRequest(req: Request, args: Seq[String]): Option[Either[Response, Request]] =
      if (req.operation == specification.name)
        _normalize_make_request(req, args, super.makeRequest(req, args))
      else
        None

    def apply(env: Environment, req: Request): Response =
      _apply(env, req, "modeler-diagram") {
        case m: SImage => FileResponse(m.binary)
        case m: SError => ConclusionResponse(m.conclusion)
        case m => _unexpected_result("class", m)
      }
  }

  case object ProjectOperationClass extends OperationClassWithOperation {
    val request = _request
    val response = spec.Response.empty
    val specification = spec.Operation("project", request, response)

    override def makeRequest(req: Request, args: Seq[String]): Option[Either[Response, Request]] =
      if (req.operation == specification.name)
        _normalize_make_request(req, args, super.makeRequest(req, args))
      else
        None

    def apply(env: Environment, req: Request): Response =
      _apply(env, req, "modeler-scala") {
        case m: STree => FileRealmResponse(m.tree)
        case m: SError => ConclusionResponse(m.conclusion)
        case m => _unexpected_result("project", m)
      }
  }

  private def _normalize_make_request(
    req: Request,
    args: Seq[String],
    delegated: => Option[Either[Response, Request]]
  ): Option[Either[Response, Request]] =
    try {
      delegated.map {
        case Left(response) => Left(response)
        case Right(_) => Right(_request.buildStrict(req, args))
      }
    } catch {
      case NonFatal(e) => Some(Left(ConclusionResponse(Conclusion.make(e))))
    }

  private def _apply(
    env: Environment,
    req: Request,
    function: String
  )(response: SExpr => Response): Response =
    try {
      val ctx = env.toAppEnvironment[Context]
      _load_model(ctx, req) match {
        case Left(r) => r
        case Right(model) =>
          response(_evaluate(ctx, model, function))
      }
    } catch {
      case NonFatal(e) => ConclusionResponse(Conclusion.make(e))
    }

  private def _load_model(ctx: Context, req: Request): Either[Response, KaleidoxModel] =
    req.get(_model) match {
      case Some(file: File) =>
        _parse_model(ctx, req, file)
      case Some(value: String) =>
        _parse_model(ctx, req, new File(value))
      case Some(value) =>
        Left(ConclusionResponse(Conclusion.invalidArgumentFault(s"Invalid model file: $value")))
      case None =>
        Left(ConclusionResponse(Conclusion.missingArgumentFault("model")))
    }

  private def _parse_model(
    ctx: Context,
    req: Request,
    file: File
  ): Either[Response, KaleidoxModel] =
    try {
      val charset = Charset.forName(req.getPropertyString("charset").getOrElse("UTF-8"))
      Right(KaleidoxModel.parse(ctx.kaleidox.config, Files.readString(file.toPath, charset)))
    } catch {
      case NonFatal(e) => Left(ConclusionResponse(Conclusion.make(e)))
    }

  private def _evaluate(ctx: Context, model: KaleidoxModel, function: String): SExpr =
    ctx.kaleidox.createEngine().applyModelScript(
      model,
      SList(SAtom(function), SModel(model))
    )

  private def _unexpected_result(operation: String, result: SExpr): Response =
    ConclusionResponse(
      Conclusion.InternalServerError.withMessage(
        s"modeler $operation produced an unexpected result: ${result.getClass.getSimpleName}"
      )
    )
}
