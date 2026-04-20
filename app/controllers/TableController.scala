package controllers

import actions.AuthAction
import models.FuelType
import play.api.*
import play.api.i18n.I18nSupport
import play.api.mvc.*
import services.FuelPriceService
import views.html.TableView

import javax.inject.*
import scala.concurrent.ExecutionContext

@Singleton
class TableController @Inject()(
                                val controllerComponents: ControllerComponents,
                                authAction: AuthAction,
                                fuelPriceService: FuelPriceService,
                                tableView: TableView
                              )(implicit ec: ExecutionContext) extends BaseController with I18nSupport{

  def index(postcode: String) = authAction.async { implicit authenticatedRequest =>
    fuelPriceService.getFuelPriceFromPostcode(postcode).map { fuelStations =>
      val fuelPricesSorted = fuelStations.map { fuelStation =>
        // only keep the latest price for each fuel type
        fuelStation.copy( fuelPrices = fuelStation.fuelPrices
          .groupBy(_.fuelType)
          .values
          .map(_.maxBy(_.priceLastUpdated))
          .toSeq)
      }
        // sort fuel stations by lowest price for E10
        .sortBy(_.fuelPrices.find(fp => s"${fp.fuelType}" == s"${FuelType.E10}").map(_.price))
      Ok(tableView(fuelPricesSorted, postcode))
    }
  }
}
