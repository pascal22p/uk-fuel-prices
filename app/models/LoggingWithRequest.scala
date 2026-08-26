package models

import models.Attrs
import org.slf4j.{MDC, MarkerFactory}
import play.api.{Logging, MarkerContext}
import play.api.mvc.RequestHeader

trait LoggingWithRequest extends Logging {

  @SuppressWarnings(Array("org.wartremover.warts.RedundantConversions"))
  implicit def requestHeaderToMarkerContext(implicit request: RequestHeader): MarkerContext = {
    val requestId = request.attrs.get(Attrs.RequestId)
    val sessionId = request.attrs.get(Attrs.SessionId)

    MDC.put("request_id", requestId.fold("Unknown")(_.toString))
    MDC.put("session_id", sessionId.fold("Unknown")(_.toString))

    val marker = MarkerFactory.getDetachedMarker(s"requestId=$requestId, sessionId=$sessionId")
    MarkerContext(marker)
  }

  def withRequestLogging[A](block: => A)(implicit request: RequestHeader): A = {
    requestHeaderToMarkerContext
    try block
    finally {
      MDC.remove("request_id")
      MDC.remove("session_id")
    }
  }

}
