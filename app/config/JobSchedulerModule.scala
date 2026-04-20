package config

import jobs.JobScheduler
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment, Logging}

class JobSchedulerModule extends Module with Logging {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[?]] = {
    logger.info("JobSchedulerModule bindings")
    if (configuration.get[Boolean]("scheduler.partial-update.isEnabled")) {
      Seq(
        bind[JobScheduler].toSelf.eagerly()
      )
    } else {
      logger.info("JobSchedulerModule is disabled via configuration")
      Seq.empty
    }
  }
}
