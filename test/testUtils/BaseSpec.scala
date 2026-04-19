package testUtils

import config.JobSchedulerModule
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Injecting

trait BaseSpec
    extends PlaySpec
    with GuiceOneAppPerSuite
    with ScalaFutures
    with Injecting
    with IntegrationPatience
    with MockitoSugar
    with BeforeAndAfterEach {

  protected def localGuiceApplicationBuilder(): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .disable[JobSchedulerModule]
      .configure(
        "scheduler.partial-update.isEnabled" -> false,
        "scheduler.partial-update.startDelayInSeconds" -> 2000,
        "scheduler.partial-update.schedulerIntervalInMinutes" -> 2000
      )

  implicit override lazy val app: Application = localGuiceApplicationBuilder().build()

}
