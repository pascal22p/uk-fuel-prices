package config

import jobs.JobScheduler
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}

class JobSchedulerModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[?]] = {
    Seq(
      bind[JobScheduler].toSelf
    )
  }
}
