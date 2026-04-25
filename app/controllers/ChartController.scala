package controllers

import actions.AuthAction
import play.api.*
import play.api.i18n.I18nSupport
import play.api.mvc.*
import play.api.libs.json.Json
import queries.GetSqlQueries
import services.ChartService

import javax.inject.*
import scala.concurrent.{Future, ExecutionContext}

@Singleton
class ChartController @Inject()(
                                val controllerComponents: ControllerComponents,
                                authAction: AuthAction,
                                chartService: ChartService,
                                getSqlQueries: GetSqlQueries
                              )(implicit ec: ExecutionContext) extends BaseController with I18nSupport {

  def svgChart(nodeId: String) = authAction.async { implicit authenticatedRequest =>
    getSqlQueries.getFuelStation(nodeId).flatMap {
      case None => Future.successful(NotFound(s"The nodeId $nodeId was not found"))
      case Some(fuelStation) =>
        chartService.priceHistoryChart(fuelStation).map(Ok(_).as("image/svg+xml"))
    }
  }

  def priceHistoryJson(nodeId: String) = Action.async {
    chartService.priceHistoryData(nodeId).map { data =>
      Ok(Json.toJson(data))
    }
  }

}
