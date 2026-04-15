package controllers

import actions.AuthAction

import javax.inject.*
import play.api.*
import play.api.i18n.I18nSupport
import play.api.mvc.*
import views.html.IndexView

import scala.concurrent.Future

@Singleton
class HomeController @Inject()(
                                val controllerComponents: ControllerComponents,
                                authAction: AuthAction,
                                indexView: IndexView
                              ) extends BaseController with I18nSupport{

  def index() = authAction.async { implicit authenticatedRequest =>
    //implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(authenticatedRequest, authenticatedRequest.session)
    Future.successful(Ok(indexView()))
  }
}
