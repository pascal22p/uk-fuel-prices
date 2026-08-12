package controllers

import actions.AuthAction
import config.AppConfig

import javax.inject.*
import play.api.*
import play.api.i18n.I18nSupport
import play.api.mvc.*
import queries.GetSqlQueries
import views.html.{HomepageView, StationView}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HomeController @Inject()(
                                val controllerComponents: ControllerComponents,
                                getSqlQueries: GetSqlQueries,
                                authAction: AuthAction,
                                appConfig: AppConfig,
                                stationView: StationView,
                                homepageView: HomepageView
                              )(implicit ec: ExecutionContext) extends BaseController with I18nSupport{

  def index(): Action[AnyContent] = authAction.async { implicit authenticatedRequest =>
    for {
      totalFuelStations <- getSqlQueries.getTotalFuelStations
      totalFuelPrices <- getSqlQueries.getTotalFuelPrices
      lastUpdates <- getSqlQueries.getLatestFuelPricesWithStation(appConfig.maxCountForLastUpdatedPrices)
    } yield {
     Ok(homepageView(totalFuelStations, totalFuelPrices, lastUpdates))
    }

    //Future.successful(Redirect(routes.SearchByPostcodeController.showPostcodeForm()))
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
