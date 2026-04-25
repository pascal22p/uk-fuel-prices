package controllers

import actions.AuthAction

import javax.inject.*
import play.api.*
import play.api.i18n.I18nSupport
import play.api.mvc.*
import queries.GetSqlQueries
import views.html.{IndexView, StationView}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HomeController @Inject()(
                                val controllerComponents: ControllerComponents,
                                getSqlQueries: GetSqlQueries,
                                authAction: AuthAction,
                                indexView: IndexView,
                                stationView: StationView
                              )(implicit ec: ExecutionContext) extends BaseController with I18nSupport{

  def index(): Action[AnyContent] = authAction.async { implicit authenticatedRequest =>
    Future.successful(Ok(indexView()))
  }

  def fuelStationDetails(nodeId: String): Action[AnyContent] = authAction.async { implicit authenticatedRequest =>
    val nodeIdRegex = "^[0-9a-fA-F]{64}$".r

    if(nodeIdRegex.matches(nodeId)) {
      getSqlQueries.getFuelStation(nodeId).map {
        case None => NotFound(s"The nodeId $nodeId was not found")
        case Some(station) => Ok(stationView(station))
      }
    } else {
      Future. successful(BadRequest(s"The nodeId $nodeId is not a valid nodeId"))
    }
  }
}
