package jobs

import config.AppConfig

import javax.inject.{Inject, Singleton}
import org.apache.pekko.actor.{ActorRef, ActorSystem, Props}
import play.api.Logging
import play.api.db.Database

import scala.concurrent.duration.*
import play.api.inject.ApplicationLifecycle
import services.FuelPriceService

@Singleton
class JobScheduler @Inject() (
                                   actorSystem: ActorSystem,
                                   lifecycle: ApplicationLifecycle,
                                   appConfig: AppConfig,
                                   fuelPriceService: FuelPriceService,
                                   db: Database
                                 ) extends Logging {

  import actorSystem.dispatcher

  logger.info("Registering partial update job scheduler")
  private val partialUpdateActorRef: ActorRef =
    actorSystem.actorOf(Props(new PartialUpdateStationsAndPricesJob(fuelPriceService, db, appConfig)), "partial-update-actor")

  private val partialUpdateCancellable =
    actorSystem.scheduler.scheduleWithFixedDelay(
      initialDelay = appConfig.schedulerPartialUpdateStartDelay.seconds,
      delay = appConfig.schedulerPartialUpdateInterval.minutes,
      receiver = partialUpdateActorRef,
      message = RunJob
    )
  
  // Stop scheduler when app shuts down
  lifecycle.addStopHook { () =>
    partialUpdateCancellable.cancel()
    actorSystem.stop(partialUpdateActorRef)
    scala.concurrent.Future.successful(())
  }
}