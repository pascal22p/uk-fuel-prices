package jobs

import play.api.Logging
import services.FuelPriceService
import uk.gov.hmrc.http.HeaderCarrier
import config.AppConfig

import scala.concurrent.duration.DurationInt
import org.apache.pekko.actor.Actor
import play.api.db.Database

import anorm._
import anorm.SqlParser._
import java.time.LocalDateTime
import java.time.ZoneOffset

import scala.concurrent.{Await, ExecutionContext}

class PartialUpdateStationsAndPricesJob(
                                            fuelPriceService: FuelPriceService,
                                            db: Database,
                                            appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends Actor
    with Logging {

  private val lockId = "stationsAndPricesLock"

  def receive: Receive = {
    case RunJob => {
      logger.info("\u001b[35m Starting scheduled job partial update\u001b[0m")
      implicit val hc: HeaderCarrier = HeaderCarrier()

      db.withTransaction { implicit conn =>
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

          case Some(lastUpdate) if lastUpdate.isAfter(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(appConfig.jobPartialUpdateInterval * 60)) =>
            logger.info(s"Job ran recently at $lastUpdate, skipping")
            conn.rollback()

          case Some(lastUpdate) =>
            val execute = for {
              _ <- fuelPriceService.uploadAllFuelStations(1, effectiveStartDate = Some(lastUpdate.minusSeconds(overlapInSeconds))).leftMap(error => s"Error while retrieving stations: ${error.message}")
              _ <- fuelPriceService.uploadAllFuelPrices(1, effectiveStartDate = Some(lastUpdate.minusSeconds(overlapInSeconds))).leftMap(error => s"Error while retrieving fuel prices: ${error.message}")
            } yield ()

            Await.result(execute.fold(
              error => {
                logger.error(error)
                conn.rollback()
              },
              _ => {
                SQL(
                  """UPDATE fuel_locks
                    |SET lastUpdate = {now}
                    |WHERE id = {lockId}""".stripMargin)
                  .on("lockId" -> lockId, "now" -> now)
                  .executeUpdate()
                logger.info(s"Schedule job finished")
              }
            ), 20.minutes)
        }
      }
    }
  }
}
