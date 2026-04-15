package models

import play.api.MarkerContext
import play.api.mvc.{Request, WrappedRequest}

final case class AuthenticatedRequest[A](
    request: Request[A],
    localSession: Session
)(implicit val markerContext: MarkerContext)
    extends WrappedRequest[A](request)
