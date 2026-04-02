package controllers

import javax.inject.*
import play.api.*
import play.api.mvc.*
import services.FuelPriceService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.ExecutionContext

@Singleton
class HomeController @Inject()(
                                val controllerComponents: ControllerComponents,
                                fuelPriceService: FuelPriceService
                              )(
                              implicit ec: ExecutionContext
) extends BaseController {

  def index() = Action.async { implicit request: Request[AnyContent] =>
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(request, request.session)
    fuelPriceService.uploadAllFps().fold(
      error => InternalServerError(s"Call to fuel API went wrong: $error"),
        _   => Ok(views.html.index())
    )
  }
}
