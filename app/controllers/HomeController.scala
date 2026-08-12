package controllers

import actions.AuthAction

import javax.inject.*
import play.api.*
import play.api.i18n.I18nSupport
import play.api.mvc.*
import queries.GetSqlQueries
import views.html.StationView

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HomeController @Inject()(
                                val controllerComponents: ControllerComponents,
                                getSqlQueries: GetSqlQueries,
                                authAction: AuthAction,
                                stationView: StationView
                              )(implicit ec: ExecutionContext) extends BaseController with I18nSupport{

  def index(): Action[AnyContent] = authAction { implicit authenticatedRequest =>
    val x = Future {
      Redirect(routes.SearchByPostcodeController.showPostcodeForm())
    }
    Await.result(x, Duration.Inf) // blocking + infinite timeout
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
