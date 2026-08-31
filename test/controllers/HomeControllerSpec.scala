package controllers

import config.AppConfig
import models.{FuelPrice, FuelType, GeoLoc}
import org.mockito.ArgumentMatchers.any
import testUtils.{BaseSpec, FakeAuthAction}
import play.api.test.Helpers.*
import queries.GetSqlQueries
import views.html.{HomepageView, StationView}
import play.api.test.FakeRequest
import org.mockito.Mockito.{never, reset, verify, when}
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
        Future.successful(Seq(fakeFuelStationWithPrices(
          nodeId = "1",
          fuelPrices = Seq(FuelPrice(2.0, FuelType.E10, Instant.now, Instant.now))
        )))
      )

      when(mockFuelStationsService.getCheapestPricesWithStation(any(), any())).thenReturn(
        Future.successful(Seq(fakeFuelStationWithPrices(
          nodeId = "1",
          fuelPrices = Seq(FuelPrice(2.0, FuelType.E10, Instant.now, Instant.now))
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
        Future.successful(Seq(fakeFuelStationWithPrices(
          nodeId = "1",
          fuelPrices = Seq(FuelPrice(2.0, FuelType.E10, Instant.now, Instant.now))
        )))
      )
      when(mockFuelStationsService.getCheapestPricesWithStation(any(), any())).thenReturn(
        Future.successful(Seq(fakeFuelStationWithPrices(
          nodeId = "1",
          fuelPrices = Seq(FuelPrice(2.0, FuelType.E10, Instant.now, Instant.now))
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
        Future.successful(Seq(fakeFuelStationWithPrices(
          nodeId = "1",
          fuelPrices = Seq(FuelPrice(2.0, FuelType.E10, Instant.now, Instant.now))
        )))
      )
      when(mockFuelStationsService.getCheapestPricesWithStation(any(), any())).thenReturn(
        Future.successful(Seq(fakeFuelStationWithPrices(
          nodeId = "1",
          fuelPrices = Seq(FuelPrice(2.0, FuelType.E10, Instant.now, Instant.now))
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
        Future.successful(Seq(fakeFuelStationWithPrices(
          nodeId = "1",
          fuelPrices = Seq(FuelPrice(2.0, FuelType.E10, Instant.now, Instant.now))
        )))
      )
      when(mockFuelStationsService.getCheapestPricesWithStation(any(), any())).thenReturn(
        Future.successful(Seq(fakeFuelStationWithPrices(
          nodeId = "1",
          fuelPrices = Seq(FuelPrice(2.0, FuelType.E10, Instant.now, Instant.now))
        )))
      )

      val result = sut.index().apply(FakeRequest(GET, "/?loc=notanumber"))

      status(result) mustBe OK
      contentAsString(result) must include("""<script src="/assets/javascripts/geoloc.js" defer></script>""")

      val geoLocCaptor = org.mockito.ArgumentCaptor.forClass(classOf[Option[GeoLoc]])
      verify(mockFuelStationsService).getLatestFuelPricesWithStation(any(), geoLocCaptor.capture())
      geoLocCaptor.getValue mustBe None
    }

    "treat a loc parameter that matches the pattern but fails numeric conversion as absent, include the script tag, and pass None to FuelStationsService" in {
      when(mockGetSqlQueries.getTotalFuelPrices).thenReturn(
        Future.successful(1)
      )
      when(mockGetSqlQueries.getTotalFuelStations).thenReturn(
        Future.successful(2)
      )
      when(mockAppConfig.maxCountForLastUpdatedPrices).thenReturn(10)
      when(mockFuelStationsService.getLatestFuelPricesWithStation(any(), any())).thenReturn(
        Future.successful(Seq(fakeFuelStationWithPrices(
          nodeId = "1",
          fuelPrices = Seq(FuelPrice(2.0, FuelType.E10, Instant.now, Instant.now))
        )))
      )
      when(mockFuelStationsService.getCheapestPricesWithStation(any(), any())).thenReturn(
        Future.successful(Seq(fakeFuelStationWithPrices(
          nodeId = "1",
          fuelPrices = Seq(FuelPrice(2.0, FuelType.E10, Instant.now, Instant.now))
        )))
      )

      val result = sut.index().apply(FakeRequest(GET, "/?loc=51.5,abc"))

      status(result) mustBe OK
      contentAsString(result) must include("""<script src="/assets/javascripts/geoloc.js" defer></script>""")

      val geoLocCaptor = org.mockito.ArgumentCaptor.forClass(classOf[Option[GeoLoc]])
      verify(mockFuelStationsService).getLatestFuelPricesWithStation(any(), geoLocCaptor.capture())
      geoLocCaptor.getValue mustBe None
    }
  }

  "fuelStationDetails" must {

    "return 404 when a valid nodeId does not have a fuel station" in {
      val nodeId =
        "B739362AF81ACC9FEC9EDA6F155348125FA2D5C1772C96BF6855A1BAD0179711"

      when(mockFuelStationsService.getFuelStationWithLatestPrices(nodeId))
        .thenReturn(Future.successful(None))

      val result = sut
        .fuelStationDetails(nodeId)
        .apply(FakeRequest(GET, s"/fuel-stations/$nodeId"))

      status(result) mustBe NOT_FOUND
      contentAsString(result) mustBe s"The nodeId $nodeId was not found"

      verify(mockFuelStationsService)
        .getFuelStationWithLatestPrices(nodeId)
    }

    "return 400 when the nodeId is invalid" in {
      val nodeId = "not-a-valid-node-id"

      val result = sut
        .fuelStationDetails(nodeId)
        .apply(FakeRequest(GET, s"/fuel-stations/$nodeId"))

      status(result) mustBe BAD_REQUEST
      contentAsString(result) mustBe
        s"The nodeId $nodeId is not a valid nodeId"

      verify(mockFuelStationsService, never())
        .getFuelStationWithLatestPrices(any[String])
    }

    "return 200 and render the station when a valid nodeId is found" in {
      val nodeId =
        "B739362AF81ACC9FEC9EDA6F155348125FA2D5C1772C96BF6855A1BAD0179711"

      val station = fakeFuelStationWithPrices(
        nodeId = nodeId,
        fuelPrices = Seq(
          FuelPrice(
            145.0,
            FuelType.E10,
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2024-01-01T00:00:00Z")
          )
        )
      )

      when(mockFuelStationsService.getFuelStationWithLatestPrices(nodeId))
        .thenReturn(Future.successful(Some(station)))

      val result = sut
        .fuelStationDetails(nodeId)
        .apply(FakeRequest(GET, s"/fuel-stations/$nodeId"))

      status(result) mustBe OK
      contentAsString(result) must include(station.tradingName)
      contentAsString(result) must include("const lat = 51.5014;")
      contentAsString(result) must include("const lng = -0.1419;")

      verify(mockFuelStationsService)
        .getFuelStationWithLatestPrices(nodeId)
    }
  }
}
