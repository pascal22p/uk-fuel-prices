package controllers

import config.AppConfig
import models.{FuelPrice, FuelPriceForStation, FuelType}
import org.mockito.ArgumentMatchers.any
import testUtils.{BaseSpec, FakeAuthAction}
import play.api.test.Helpers.*
import queries.GetSqlQueries
import views.html.{HomepageView, StationView}
import play.api.test.FakeRequest
import org.mockito.Mockito.{reset, when}

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class HomeControllerSpec extends BaseSpec {
  val fakeAuthAction = new FakeAuthAction

  val cc = stubControllerComponents()
  val mockGetSqlQueries: GetSqlQueries = mock[GetSqlQueries]
  val mockAppConfig: AppConfig = mock[AppConfig]
  val injectedStationView: StationView = app.injector.instanceOf[StationView]
  val injectedHomepageView:HomepageView = app.injector.instanceOf[HomepageView]
  implicit val ec: ExecutionContext = ExecutionContext.global

  val sut = new HomeController(cc, mockGetSqlQueries, fakeAuthAction, mockAppConfig, injectedStationView, injectedHomepageView)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockGetSqlQueries, mockAppConfig)
  }

  "test" in {
    when(mockGetSqlQueries.getTotalFuelPrices).thenReturn(
      Future.successful(1)
    )
    when(mockGetSqlQueries.getTotalFuelStations).thenReturn(
      Future.successful(3)
    )
    when(mockGetSqlQueries.getLatestFuelPricesWithStation(any())).thenReturn(
      Future.successful(Seq(FuelPriceForStation(
        "1",
        None,
        "trading Name",
        Seq(FuelPrice(2.0, FuelType.E10, Instant.now, Instant.now))
      )))
    )

    val result = sut.index().apply(FakeRequest())
    
    status(result) mustBe OK
  }
}
