package jobs

import anorm.*
import anorm.SqlParser.*
import cats.data.EitherT
import org.apache.pekko.actor.Actor
import play.api.Logging
import play.api.db.Database
import services.FuelPriceService
import uk.gov.hmrc.http.HeaderCarrier
import config.AppConfig

import java.time.{LocalDateTime, ZoneOffset}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext}

class FullUpdateStationsAndPricesJob(
                                            fuelPriceService: FuelPriceService,
                                            db: Database,
                                            appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends Actor
    with Logging {

  private val lockId = "stationsAndPricesLock"

  def receive: Receive = {
    case RunJob => {
      if(appConfig.jobFullUpdateIsEnabled) {
        logger.info("\u001b[35m Starting scheduled job full update\u001b[0m")
        implicit val hc: HeaderCarrier = HeaderCarrier()

        db.withTransaction { implicit conn =>

          // insert lock row if not exists
          SQL(
            """INSERT INTO fuel_locks (id, lastUpdate)
              |VALUES ({lockId}, NOW())
              |ON DUPLICATE KEY UPDATE lastUpdate = NOW()""".stripMargin)
            .on("lockId" -> lockId)
            .executeUpdate()

          // In case the job overrun, also lock the row to prevent other job from running at the same time
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

            case Some(_) =>

              // Full update is done without clearing the data so the app is still working
              // Records that have not been updated can be deleted after the update is done
              val execute = for {
                now = LocalDateTime.now(ZoneOffset.UTC)
                _ <- fuelPriceService.uploadAllFuelStations().leftMap(error => s"Error while retrieving stations: ${error.message}")
                _ <- fuelPriceService.uploadAllFuelPrices().leftMap(error => s"Error while retrieving fuel prices: ${error.message}")
                fuelPricesDeleted <- EitherT(fuelPriceService.removeOldFuelPrices(now).map(Right(_)).recover { case error => Left(s"Error while removing old stations: ${error.getMessage}") })
                stationsDeleted <- EitherT(fuelPriceService.removeOldFuelStations(now).map(Right(_)).recover { case error => Left(s"Error while removing old stations: ${error.getMessage}") })
              } yield {
                logger.info(s"Deleted $stationsDeleted stations and $fuelPricesDeleted fuel prices")
                ()
              }

              Await.result(execute.fold(
                error => {
                  logger.error(error)
                  conn.rollback()
                },
                _ => logger.info(s"Schedule job finished")
              ), 2.minutes)
          }
        }
      } else {
        logger.info("Full update job is disabled, skipping")
      }
    }
  }
}
