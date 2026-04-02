package actions

import com.google.inject.ImplementedBy
import models.{AuthenticatedRequest, LoggingWithRequest}
import play.api.mvc.*

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AuthActionImpl @Inject() (
    cc: MessagesControllerComponents,
)(implicit val ec: ExecutionContext)
    extends AuthAction
    with LoggingWithRequest {

  override val parser: BodyParser[AnyContent] = cc.parsers.defaultBodyParser
  protected override val executionContext: ExecutionContext = cc.executionContext

  override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {
    @SuppressWarnings(Array("org.wartremover.warts.ToString"))
    val uuid = UUID.randomUUID().toString
    val sessionId = request.session.get("sessionId").getOrElse(uuid)
    //val baseRegex = """^/base/([0-9]+)/.*""".r

    logger.info(s"AuthAction with session ID: $sessionId")

    block {
      AuthenticatedRequest(
        request
      )
    }
  }
}

@ImplementedBy(classOf[AuthActionImpl])
trait AuthAction
    extends ActionBuilder[AuthenticatedRequest, AnyContent]
    with ActionFunction[Request, AuthenticatedRequest]
