package controllers

import config.AppConfig
import models.{FuelPrice, FuelPriceForStation, FuelType, GeoLoc}
import org.mockito.ArgumentMatchers.any
import testUtils.{BaseSpec, FakeAuthAction}
import play.api.test.Helpers.*
import queries.GetSqlQueries
import views.html.{HomepageView, StationView}
import play.api.test.FakeRequest
import org.mockito.Mockito.{reset, verify, when}
import services.FuelStationsService

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class HomeControllerSpec extends BaseSpec {
  val fakeAuthAction = new FakeAuthAction

  val cc = stubControllerComponents()
  val mockGetSqlQueries: GetSqlQueries = mock[GetSqlQueries]
  val mockAppConfig: AppConfig = mock[AppConfig]
  val mockFuelStationsService: FuelStationsService = mock[FuelStationsService]

  val injectedStationView: StationView = app.injector.instanceOf[StationView]
  val injectedHomepageView: HomepageView = app.injector.instanceOf[HomepageView]
  implicit val ec: ExecutionContext = ExecutionContext.global

  val sut = new HomeController(cc, mockGetSqlQueries, mockFuelStationsService, fakeAuthAction, mockAppConfig, injectedStationView, injectedHomepageView)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockGetSqlQueries, mockAppConfig, mockFuelStationsService)
  }

  "homepage" must {
    "not  use geoloc" in {
      when(mockGetSqlQueries.getTotalFuelPrices).thenReturn(
        Future.successful(1)
      )
      when(mockGetSqlQueries.getTotalFuelStations).thenReturn(
        Future.successful(2)
      )

      when(mockFuelStationsService.getLatestFuelPricesWithStation(any(), any())).thenReturn(
        Future.successful(Seq(FuelPriceForStation(
          "1",
          None,
          "trading Name",
          Seq.empty,
          Seq(FuelPrice(2.0, FuelType.E10, Instant.now, Instant.now))
        )))
      )

      val result = sut.index().apply(FakeRequest())

      status(result) mustBe OK
    }

    "include the geoloc.js script tag and pass None to FuelStationsService when no loc parameter is supplied" in {
      when(mockGetSqlQueries.getTotalFuelPrices).thenReturn(
        Future.successful(1)
      )
      when(mockGetSqlQueries.getTotalFuelStations).thenReturn(
        Future.successful(2)
      )
      when(mockAppConfig.maxCountForLastUpdatedPrices).thenReturn(10)
      when(mockFuelStationsService.getLatestFuelPricesWithStation(any(), any())).thenReturn(
        Future.successful(Seq(FuelPriceForStation(
          "1",
          None,
          "trading Name",
          Seq.empty,
          Seq(FuelPrice(2.0, FuelType.E10, Instant.now, Instant.now))
        )))
      )

      val result = sut.index().apply(FakeRequest(GET, "/"))

      status(result) mustBe OK
      contentAsString(result) must include("""<script src="/assets/javascripts/geoloc.js" defer></script>""")

      val geoLocCaptor = org.mockito.ArgumentCaptor.forClass(classOf[Option[GeoLoc]])
      verify(mockFuelStationsService).getLatestFuelPricesWithStation(any(), geoLocCaptor.capture())
      geoLocCaptor.getValue mustBe None
    }

    "omit the geoloc.js script tag and pass the parsed GeoLoc to FuelStationsService when a valid loc parameter is supplied" in {
      when(mockGetSqlQueries.getTotalFuelPrices).thenReturn(
        Future.successful(1)
      )
      when(mockGetSqlQueries.getTotalFuelStations).thenReturn(
        Future.successful(2)
      )
      when(mockAppConfig.maxCountForLastUpdatedPrices).thenReturn(10)
      when(mockFuelStationsService.getLatestFuelPricesWithStation(any(), any())).thenReturn(
        Future.successful(Seq(FuelPriceForStation(
          "1",
          None,
          "trading Name",
          Seq.empty,
          Seq(FuelPrice(2.0, FuelType.E10, Instant.now, Instant.now))
        )))
      )

      val result = sut.index().apply(FakeRequest(GET, "/?loc=51.5,0.1"))

      status(result) mustBe OK
      contentAsString(result) must not include """<script src="/assets/javascripts/geoloc.js"></script>"""

      val geoLocCaptor = org.mockito.ArgumentCaptor.forClass(classOf[Option[GeoLoc]])
      verify(mockFuelStationsService).getLatestFuelPricesWithStation(any(), geoLocCaptor.capture())
      geoLocCaptor.getValue mustBe Some(GeoLoc(51.5, 0.1))
    }

    "treat a malformed loc parameter as absent, include the script tag, and pass None to FuelStationsService" in {
      when(mockGetSqlQueries.getTotalFuelPrices).thenReturn(
        Future.successful(1)
      )
      when(mockGetSqlQueries.getTotalFuelStations).thenReturn(
        Future.successful(2)
      )
      when(mockAppConfig.maxCountForLastUpdatedPrices).thenReturn(10)
      when(mockFuelStationsService.getLatestFuelPricesWithStation(any(), any())).thenReturn(
        Future.successful(Seq(FuelPriceForStation(
          "1",
          None,
          "trading Name",
          Seq.empty,
          Seq(FuelPrice(2.0, FuelType.E10, Instant.now, Instant.now))
        )))
      )

      val result = sut.index().apply(FakeRequest(GET, "/?loc=notanumber"))

      status(result) mustBe OK
      contentAsString(result) must include("""<script src="/assets/javascripts/geoloc.js" defer></script>""")

      val geoLocCaptor = org.mockito.ArgumentCaptor.forClass(classOf[Option[GeoLoc]])
      verify(mockFuelStationsService).getLatestFuelPricesWithStation(any(), geoLocCaptor.capture())
      geoLocCaptor.getValue mustBe None
    }
  }
}
