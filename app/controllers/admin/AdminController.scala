package controllers.admin

import actions.AuthJourney
import play.api.i18n.I18nSupport
import play.api.mvc.*
import services.FuelPriceService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import views.html.admin.{IndexView, UpdateCompletedView}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AdminController @Inject()(
    authJourney: AuthJourney,
    fuelPriceService: FuelPriceService,
    updateCompletedView: UpdateCompletedView,
    indexView: IndexView,
    val controllerComponents: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BaseController
    with I18nSupport {

  def index: Action[AnyContent] = authJourney.authWithAdminRight.async { implicit request =>
    Future.successful(Ok(indexView()))
  }
  
  def updateFuelPrices(): Action[AnyContent] = authJourney.authWithAdminRight.async { implicit authenticatedRequest =>
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(authenticatedRequest, authenticatedRequest.session)
    fuelPriceService.uploadAllFuelPrices().fold(
      error => InternalServerError(error.message),
      _ => Ok(updateCompletedView())
    )
  }

  def updateFuelStations(): Action[AnyContent] = authJourney.authWithAdminRight.async { implicit authenticatedRequest =>
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(authenticatedRequest, authenticatedRequest.session)
    fuelPriceService.uploadAllFuelStations().fold(
      error => InternalServerError(error.message),
      _ => Ok(updateCompletedView())
    )
  }
}
