package services

import connectors.FuelPriceConnector
import models.{FuelPrice, FuelPriceForStation, FuelStation, FuelStationLocation, FuelType, GeoLoc}
import queries.GetSqlQueries
import testUtils.BaseSpec
import uk.gov.hmrc.http.HeaderCarrier
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import utils.GeoBoundingBox

import java.time.Instant
import scala.concurrent.Future
import config.AppConfig

class FuelStationsServiceSpec extends BaseSpec {

  val mockFuelPriceConnector: FuelPriceConnector = mock[FuelPriceConnector]
  val mockGetSqlQueries: GetSqlQueries = mock[GetSqlQueries]
  val mockAppConfig: AppConfig = mock[AppConfig]
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val sut = new FuelStationsService(mockFuelPriceConnector, mockGetSqlQueries, mockAppConfig)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockFuelPriceConnector, mockGetSqlQueries, mockAppConfig)
  }

  "getLatestFuelPricesWithStation" must {
    val geoLoc = GeoLoc(51.5074, -0.1278)
    val radiusMiles = 10

    val nearStation = FuelStation(
      "nearNodeId", "nearTradingName", None, "brandName", None, None, None, None,
      FuelStationLocation(None, None, "city", None, None, "postcode", 51.51, -0.13), // ~1km away
      List.empty
    )

    val farStation = FuelStation(
      "farNodeId", "farTradingName", None, "brandName", None, None, None, None,
      FuelStationLocation(None, None, "city", None, None, "postcode", -33.8688, 151.2093), // Sydney
      List.empty
    )

    "return prices for stations within the actual geodesic radius when geoloc is provided" in {
      when(mockAppConfig.localStationsRadius).thenReturn(radiusMiles)
      when(mockGetSqlQueries.getFuelStations(any[GeoBoundingBox])).thenReturn(
        Future.successful(Seq(nearStation))
      )
      when(mockGetSqlQueries.getLatestFuelPricesWithStation(any[Int], any[Seq[String]])).thenReturn(
        Future.successful(Seq(
          fakeFuelStationWithPrices(
            nodeId = "nearNodeId",
            fuelPrices = Seq(FuelPrice(140.0, FuelType.E10, Instant.now, Instant.now))
          )
        ))
      )

      val result = sut.getLatestFuelPricesWithStation(5, Some(geoLoc)).futureValue

      result.map(_.nodeId) mustBe Seq("nearNodeId")
      verify(mockGetSqlQueries).getFuelStations(any[GeoBoundingBox])
      verify(mockGetSqlQueries).getLatestFuelPricesWithStation(5, Seq("nearNodeId"))
    }

    "exclude stations returned in the bounding box but outside the actual geodesic radius" in {
      when(mockAppConfig.localStationsRadius).thenReturn(radiusMiles)
      when(mockGetSqlQueries.getFuelStations(any[GeoBoundingBox])).thenReturn(
        Future.successful(Seq(nearStation, farStation))
      )
      when(mockGetSqlQueries.getLatestFuelPricesWithStation(any[Int], any[Seq[String]])).thenReturn(
        Future.successful(Seq.empty)
      )

      sut.getLatestFuelPricesWithStation(5, Some(geoLoc)).futureValue

      verify(mockGetSqlQueries).getLatestFuelPricesWithStation(5, Seq("nearNodeId"))
    }

    "return an empty result without querying prices when no candidate stations are within radius" in {
      when(mockAppConfig.localStationsRadius).thenReturn(radiusMiles)
      when(mockGetSqlQueries.getFuelStations(any[GeoBoundingBox])).thenReturn(
        Future.successful(Seq(farStation))
      )

      val result = sut.getLatestFuelPricesWithStation(5, Some(geoLoc)).futureValue

      result mustBe Seq.empty
      verify(mockGetSqlQueries).getFuelStations(any[GeoBoundingBox])
      verify(mockGetSqlQueries, times(0)).getLatestFuelPricesWithStation(any[Int], any[Seq[String]])
    }

    "return an empty result without querying prices when the bounding box query itself returns no stations" in {
      when(mockAppConfig.localStationsRadius).thenReturn(radiusMiles)
      when(mockGetSqlQueries.getFuelStations(any[GeoBoundingBox])).thenReturn(
        Future.successful(Seq.empty)
      )

      val result = sut.getLatestFuelPricesWithStation(5, Some(geoLoc)).futureValue

      result mustBe Seq.empty
      verify(mockGetSqlQueries).getFuelStations(any[GeoBoundingBox])
      verify(mockGetSqlQueries, times(0)).getLatestFuelPricesWithStation(any[Int], any[Seq[String]])
    }

    "fall back to an unfiltered query when geoloc is None" in {
      when(mockGetSqlQueries.getLatestFuelPricesWithStation(any[Int], any[Seq[String]])).thenReturn(
        Future.successful(Seq(
          fakeFuelStationWithPrices(
            nodeId = "anyNodeId",
            fuelPrices = Seq(FuelPrice(145.0, FuelType.E10, Instant.now, Instant.now)))
        ))
      )

      val result = sut.getLatestFuelPricesWithStation(5, None).futureValue

      result.map(_.nodeId) mustBe Seq("anyNodeId")
      verify(mockGetSqlQueries, times(0)).getFuelStations(any[GeoBoundingBox])
      verify(mockGetSqlQueries).getLatestFuelPricesWithStation(5, Seq.empty)
    }

    "build the GeoBoundingBox passed to getFuelStations using the correct miles-to-kilometres radius conversion" in {
      when(mockAppConfig.localStationsRadius).thenReturn(radiusMiles)
      when(mockGetSqlQueries.getFuelStations(any[GeoBoundingBox])).thenReturn(
        Future.successful(Seq(nearStation))
      )
      when(mockGetSqlQueries.getLatestFuelPricesWithStation(any[Int], any[Seq[String]])).thenReturn(
        Future.successful(Seq.empty)
      )

      sut.getLatestFuelPricesWithStation(5, Some(geoLoc)).futureValue

      val boundingBoxCaptor = org.mockito.ArgumentCaptor.forClass(classOf[GeoBoundingBox])
      verify(mockGetSqlQueries).getFuelStations(boundingBoxCaptor.capture())
      val actualBoundingBox = boundingBoxCaptor.getValue

      val expectedBoundingBox = GeoBoundingBox.fromRadius(
        geoLoc.latitude,
        geoLoc.longitude,
        radiusMiles * 1.60934
      )

      actualBoundingBox.minLat mustBe expectedBoundingBox.minLat
      actualBoundingBox.maxLat mustBe expectedBoundingBox.maxLat
      actualBoundingBox.minLon mustBe expectedBoundingBox.minLon
      actualBoundingBox.maxLon mustBe expectedBoundingBox.maxLon
    }
  }
}
