package testUtils

import actions.AuthAction
import models.AuthenticatedRequest
import play.api.mvc.{AnyContent, BodyParser, Request, Result}

import scala.concurrent.{ExecutionContext, Future}

class FakeAuthAction extends AuthAction {
  def parser: BodyParser[AnyContent] = play.api.test.Helpers.stubBodyParser()

  def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {
    block(AuthenticatedRequest(request))
  }

  protected def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.global
}
