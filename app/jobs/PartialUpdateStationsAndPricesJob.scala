package jobs

import play.api.Logging
import services.FuelPriceService
import uk.gov.hmrc.http.HeaderCarrier
import config.AppConfig

import scala.concurrent.duration.DurationInt
import org.apache.pekko.actor.Actor
import play.api.db.Database
import anorm.*
import anorm.SqlParser.*
import models.LockId

import java.time.LocalDateTime
import java.time.ZoneOffset
import scala.concurrent.{Await, ExecutionContext}
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.{SpanKind, StatusCode}

class PartialUpdateStationsAndPricesJob(
                                            fuelPriceService: FuelPriceService,
                                            db: Database,
                                            appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends Actor
    with Logging {

  private val tracer = GlobalOpenTelemetry.getTracer(appConfig.appName)

  def receive: Receive = {
    case RunJob => {
      logger.info("\u001b[35m Starting scheduled job partial update\u001b[0m")
      val span = tracer
        .spanBuilder("PartialUpdateJob")
        .setSpanKind(SpanKind.INTERNAL)
        .startSpan()
      val scope = span.makeCurrent()

      implicit val hc: HeaderCarrier = HeaderCarrier()

      try {
        db.withTransaction { implicit conn =>
          val overlapInSeconds = 60
          val now = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(overlapInSeconds)

        val lastUpdate = SQL(
          """SELECT lastUpdate
            |FROM fuel_locks
            |WHERE id = {lockId}
            |FOR UPDATE NOWAIT""".stripMargin)
          .on("lockId" -> lockId)
          .as(scalar[LocalDateTime].singleOpt)

          lastUpdate match {
            case None =>
              span.setAttribute("job.skipped", true)
              span.setAttribute("job.skip_reason", "no_lock_row")
              logger.error(s"No lock row found for ${LockId.stationsAndPricesLock}, skipping job. Run initial import via admin")
              conn.rollback()

            case Some(lastUpdate) if lastUpdate.isAfter(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(appConfig.jobPartialUpdateInterval * 60)) =>
              span.setAttribute("job.skipped", true)
              span.setAttribute("job.skip_reason", "ran_recently")
              logger.info(s"Job ran recently at $lastUpdate, skipping")
              conn.rollback()

            case Some(lastUpdate) =>
              val execute = for {
                _ <- fuelPriceService.uploadAllFuelStations(1, effectiveStartDate = Some(lastUpdate.minusSeconds(overlapInSeconds))).leftMap(error => s"Error while retrieving stations: ${error.message}")
                _ <- fuelPriceService.uploadAllFuelPrices(1, effectiveStartDate = Some(lastUpdate.minusSeconds(overlapInSeconds))).leftMap(error => s"Error while retrieving fuel prices: ${error.message}")
              } yield ()

              Await.result(execute.fold(
                error => {
                  span.setStatus(StatusCode.ERROR, error)
                  logger.warn(error)
                  conn.rollback()
                },
                _ => {
                  span.setStatus(StatusCode.OK)
                  SQL(
                    """UPDATE fuel_locks
                      |SET lastUpdate = {now}
                      |WHERE id = {lockId}""".stripMargin)
                    .on("lockId" -> s"${LockId.stationsAndPricesLock}", "now" -> now)
                    .executeUpdate()
                  logger.info(s"Schedule job finished")
                }
              ), 20.minutes)
          }
        }
      } catch {
        case e: Exception =>
          span.recordException(e)
          span.setStatus(StatusCode.ERROR, e.getMessage)
          throw e
      } finally {
        scope.close()
        span.end()
      }
    }
  }
}