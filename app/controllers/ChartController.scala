package controllers

import actions.AuthAction
import play.api.*
import play.api.i18n.I18nSupport
import play.api.mvc.*
import play.api.libs.json.Json
import services.ChartService

import javax.inject.*
import scala.concurrent.ExecutionContext

@Singleton
class ChartController @Inject()(
                                val controllerComponents: ControllerComponents,
                                authAction: AuthAction,
                                chartService: ChartService
                              )(implicit ec: ExecutionContext) extends BaseController with I18nSupport {
  
  def priceHistoryJson(nodeId: String): Action[AnyContent] = authAction.async {
    chartService.priceHistoryData(nodeId).map { data =>
      Ok(Json.toJson(data))
    }
  }

}
