package controllers

import actions.AuthAction
import config.AppConfig
import models.GeoLoc

import javax.inject.*
import play.api.*
import play.api.i18n.I18nSupport
import play.api.mvc.*
import queries.GetSqlQueries
import services.FuelStationsService
import views.html.{HomepageView, StationView}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class HomeController @Inject()(
                                val controllerComponents: ControllerComponents,
                                getSqlQueries: GetSqlQueries,
                                fuelStationsService: FuelStationsService,
                                authAction: AuthAction,
                                appConfig: AppConfig,
                                stationView: StationView,
                                homepageView: HomepageView
                              )(implicit ec: ExecutionContext) extends BaseController with I18nSupport{

  def index(): Action[AnyContent] = authAction.async { implicit authenticatedRequest =>
    val geoloc = authenticatedRequest.queryString.get("loc")
      .flatMap(_.headOption)
      .flatMap { locString =>
        val pattern = "([-0-9.]+)[^0-9-.]([-0-9.]+)".r
        locString match {
          case pattern(lat, long) =>
            for {
              latD  <- Try(lat.toDouble).toOption
              longD <- Try(long.toDouble).toOption
            } yield GeoLoc(latitude = latD, longitude = longD)
          case _ => None
        }
      }
    for {
      totalFuelStations <- getSqlQueries.getTotalFuelStations
      totalFuelPrices <- getSqlQueries.getTotalFuelPrices
      lastUpdates <- fuelStationsService.getLatestFuelPricesWithStation(appConfig.maxCountForLastUpdatedPrices, geoloc)
      cheapestPrices <- fuelStationsService.getCheapestPricesWithStation(appConfig.maxCountForLastUpdatedPrices, geoloc)
    } yield {
     Ok(homepageView(
       totalFuelStations,
       totalFuelPrices,
       lastUpdates,
       cheapestPrices
     ))
    }
  }

  def fuelStationDetails(nodeId: String): Action[AnyContent] = authAction.async { implicit authenticatedRequest =>
    val nodeIdRegex = "^[0-9a-fA-F]{64}$".r

    if(nodeIdRegex.matches(nodeId)) {
      fuelStationsService.getFuelStationWithLatestPrices(nodeId).map {
        case None => NotFound(s"The nodeId $nodeId was not found")
        case Some(station) => Ok(stationView(station))
      }
    } else {
      Future. successful(BadRequest(s"The nodeId $nodeId is not a valid nodeId"))
    }
  }
}
