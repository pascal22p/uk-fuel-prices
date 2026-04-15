package config

import play.api.{Configuration, Environment}
import play.api.inject.{Binding, Module}
import uk.gov.hmrc.http.client.HttpClientV2
import jobs.JobScheduler

class AppModules extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[?]] = {
    Seq(
      bind[JobScheduler].toSelf.eagerly(),
      bind[HttpClientV2].toProvider[HttpClientV2Provider]
    )
  }
}
