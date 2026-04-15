package jobs

import config.AppConfig

import javax.inject.{Inject, Singleton}
import org.apache.pekko.actor.{ActorRef, ActorSystem, Props}
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
                                 ) {

  import actorSystem.dispatcher

  private val partialUpdateActorRef: ActorRef =
    actorSystem.actorOf(Props(new PartialUpdateStationsAndPricesJob(fuelPriceService, db, appConfig)), "partial-update-actor")

  private val partialUpdateCancellable =
    actorSystem.scheduler.scheduleWithFixedDelay(
      initialDelay = appConfig.schedulerPartialUpdateStartDelay.seconds,
      delay = appConfig.schedulerPartialUpdateInterval.minutes,
      receiver = partialUpdateActorRef,
      message = RunJob
    )

  private val fullUpdateActorRef: ActorRef =
    actorSystem.actorOf(Props(new FullUpdateStationsAndPricesJob(fuelPriceService, db, appConfig)), "full-update-actor")

  private val fullUpdateCancellable =
    actorSystem.scheduler.scheduleWithFixedDelay(
      initialDelay = appConfig.schedulerFullUpdateStartDelay.seconds,
      delay = appConfig.schedulerFullUpdateInterval.minutes,
      receiver = fullUpdateActorRef,
      message = RunJob
    )

  // Stop scheduler when app shuts down
  lifecycle.addStopHook { () =>
    partialUpdateCancellable.cancel()
    actorSystem.stop(partialUpdateActorRef)
    fullUpdateCancellable.cancel()
    actorSystem.stop(fullUpdateActorRef)
    scala.concurrent.Future.successful(())
  }
}