package controllers.admin

import actions.AuthJourney
import models.{FuelPriceWithNodeId, FuelStation}
import play.api.Logging
import play.api.i18n.I18nSupport
import play.api.libs.json.{Json, Writes}
import play.api.mvc.*
import queries.GetSqlQueries

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ExportController @Inject()(
    authJourney: AuthJourney,
    getSqlQueries: GetSqlQueries,
    val controllerComponents: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BaseController
    with I18nSupport
    with Logging {

  def exportFuelPrices: Action[AnyContent] = authJourney.authWithAdminRight.async { implicit request =>
    getSqlQueries.getAllFuelPrices.map { fuelPrices =>
      Ok(Json.toJson(fuelPrices)(using Writes.seq(using FuelPriceWithNodeId.writesExport)))
        .withHeaders(CONTENT_DISPOSITION -> """attachment; filename="fuel-prices.json"""")
    }
  }

  def exportFuelStations: Action[AnyContent] = authJourney.authWithAdminRight.async { implicit request =>
    getSqlQueries.getAllFuelStations.map { fuelStations =>
      Ok(Json.toJson(fuelStations)(using Writes.seq(using FuelStation.writesExport)))
        .withHeaders(CONTENT_DISPOSITION -> """attachment; filename="fuel-stations.json"""")
    }
  }

}

