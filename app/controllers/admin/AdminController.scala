package controllers.admin

import actions.AuthJourney
import anorm.SQL
import anorm.SqlParser.scalar
import play.api.Logging
import play.api.db.Database
import play.api.i18n.I18nSupport
import play.api.mvc.*
import services.FuelPriceService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import views.html.admin.{IndexView, UpdateCompletedView}

import java.time.{LocalDateTime, ZoneOffset}
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}

@Singleton
class AdminController @Inject()(
    authJourney: AuthJourney,
    fuelPriceService: FuelPriceService,
    db: Database,
    updateCompletedView: UpdateCompletedView,
    indexView: IndexView,
    val controllerComponents: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BaseController
    with I18nSupport
    with Logging {

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

  def partialUpdate(): Action[AnyContent] = authJourney.authWithAdminRight.async { implicit authenticatedRequest =>
    implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequestAndSession(authenticatedRequest, authenticatedRequest.session)

    val lockId = "stationsAndPricesLock"

    Future.successful(db.withTransaction { implicit conn =>
      val overlapInSeconds = 60
      val now = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(overlapInSeconds)

      val lastUpdate = SQL(
        """SELECT lastUpdate
          |FROM fuel_locks
          |WHERE id = {lockId}
          |FOR UPDATE""".stripMargin)
        .on("lockId" -> lockId)
        .as(scalar[LocalDateTime].singleOpt)

      lastUpdate match {
        case None =>
          logger.error(s"No lock row found for $lockId, skipping job. Run initial import via admin")
          conn.rollback()
          InternalServerError(s"No lock row found for $lockId, skipping job. Run initial import via admin")

        case Some(lastUpdate) =>
          val execute = for {
            _ <- fuelPriceService.uploadAllFuelStations(1, effectiveStartDate = Some(lastUpdate.minusSeconds(overlapInSeconds))).leftMap(error => s"Error while retrieving stations: ${error.message}")
            _ <- fuelPriceService.uploadAllFuelPrices(1, effectiveStartDate = Some(lastUpdate.minusSeconds(overlapInSeconds))).leftMap(error => s"Error while retrieving fuel prices: ${error.message}")
          } yield ()

          Await.result(execute.fold(
            error => {
              conn.rollback()
              InternalServerError(error)
            },
            _ => {
              SQL(
                """UPDATE fuel_locks
                  |SET lastUpdate = {now}
                  |WHERE id = {lockId}""".stripMargin)
                .on("lockId" -> lockId, "now" -> now)
                .executeUpdate()
              Ok(updateCompletedView())
            }
          ), 20.minutes)
      }
    })

  }
}

