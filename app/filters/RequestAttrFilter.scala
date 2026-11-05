package filters

import config.ErrorHandler
import models.Attrs
import org.apache.pekko.stream.Materializer
import play.api.Logging
import play.api.mvc.*
import uk.gov.hmrc.http.HeaderNames

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class RequestAttrFilter @Inject() (
    errorHandler: ErrorHandler,
    implicit val mat: Materializer
)(implicit ec: ExecutionContext)
    extends Filter
    with Logging {

  override def apply(next: RequestHeader => Future[Result])(request: RequestHeader): Future[Result] = {
    val requestId = request.headers.get(HeaderNames.xRequestId).getOrElse(UUID.randomUUID().toString)

    val sessionId =
      request.attrs
        .get(Attrs.SessionId)
        .orElse(request.session.get("sessionId"))
        .getOrElse {
          throw new IllegalStateException("Session ID missing from request")
        }

    // Attach requestId into attrs (server-side context)
    val enrichedRequest = request
      .addAttr(Attrs.RequestId, requestId)
      .addAttr(Attrs.SessionId, sessionId)
      .withHeaders(
        request.headers
          .remove(HeaderNames.xRequestId)
          .add(HeaderNames.xRequestId -> requestId)
          .remove(HeaderNames.xSessionId)
          .add(HeaderNames.xSessionId -> sessionId)
      )

    logger.info(
      s"RequestAttrFilter assigned sessionId=`$sessionId` requestId=`$requestId` to ${request.method} ${request.uri}"
    )

    // Also put the request id on the response headers (optional, useful for clients/debugging)
    next(enrichedRequest)
      .map { result =>
        result.withHeaders(
          result.header.headers
            .filterNot(_._1.equalsIgnoreCase(HeaderNames.xRequestId))
            .filterNot(_._1.equalsIgnoreCase(HeaderNames.xSessionId))
            .toSeq ++ Seq(
            HeaderNames.xRequestId -> requestId
          ) *
        )
        .discardingHeader(HeaderNames.xSessionId)
      }
      .recoverWith {
        // workaround so that the new attributes are available in the error handler
        // from https://github.com/playframework/playframework/issues/7968
        case NonFatal(e) => errorHandler.onServerError(enrichedRequest, e)
      }
  }
}
