package services

import cats.data.EitherT
import connectors.PostcodesIOConnector
import models.{FuelPrice, FuelStation, FuelStationWithPrices, FuelType, GeoLoc, SearchByPostcodeViewModel}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import queries.GetSqlQueries
import testUtils.BaseSpec
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import java.time.Instant
import scala.concurrent.Future

class SearchByPostcodeServiceSpec extends BaseSpec {

  val mockPostcodesIOConnector: PostcodesIOConnector = mock[PostcodesIOConnector]
  val mockGetSqlQueries: GetSqlQueries = mock[GetSqlQueries]
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val sut = new SearchByPostcodeService(mockPostcodesIOConnector, mockGetSqlQueries)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPostcodesIOConnector, mockGetSqlQueries)
  }

  val postcode = "SW1A 1AA"
  val geoLoc = GeoLoc(51.5074, -0.1278) // London
  val radiusMiles = 10.0
  val farStation = FuelStation(
    "farNodeId", "farTradingName", None, "brandName", None, None, None, None,
    fakeFuelStationLocation(location = Some(GeoLoc(-33.8688, 151.2093))), // Sydney
    List.empty
  )
  val nearStation = FuelStation(
    "nearNodeId", "nearTradingName", None, "brandName", None, None, None, None,
    fakeFuelStationLocation(location = Some(GeoLoc(51.51, -0.13))), // ~1km away
    List.empty
  )
  val now = Instant.now
  val nearStationPrice = FuelPrice(140.0, FuelType.E10, now, now)
  val nearStationWithPrice = FuelStationWithPrices(nearStation, List(nearStationPrice), 1000.0)

  "getViewModel" must {

    "return a view model containing only stations within the radius, with distances and coordinates preserved" in {
      when(mockPostcodesIOConnector.getCoordinates(postcode)(using hc)).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](geoLoc)
      )
      when(mockGetSqlQueries.getFuelStations(any[utils.GeoBoundingBox])).thenReturn(
        Future.successful(Seq(nearStation, farStation))
      )
      when(mockGetSqlQueries.findPricesForStations(any[Seq[String]])).thenReturn(
        Future.successful(Seq(nearStationWithPrice))
      )

      val result = sut.getViewModel(postcode, FuelType.E10, radiusMiles).value.futureValue

      result.isRight mustBe true
      val viewModel = result.toOption.get

      viewModel.centrePostcode mustBe postcode
      viewModel.centreLocation mustBe geoLoc
      viewModel.radius mustBe radiusMiles
      viewModel.fuelType mustBe FuelType.E10

      viewModel.fuelStationWithPrices.map(_.nodeId) mustBe List("nearNodeId")
      viewModel.fuelStationWithPrices.head.fuelPrices mustBe List(nearStationPrice)
      viewModel.fuelStationWithPrices.head.distance must be < 2000.0 // metres, sanity check for the ~1km fixture

      verify(mockGetSqlQueries).findPricesForStations(Seq("nearNodeId"))
    }

    "propagate an UpstreamErrorResponse from the postcode lookup without querying stations" in {
      val error = UpstreamErrorResponse("postcode not found", 404)

      when(mockPostcodesIOConnector.getCoordinates(postcode)(using hc)).thenReturn(
        EitherT.leftT[Future, GeoLoc](error)
      )

      val result = sut.getViewModel(postcode, FuelType.E10, radiusMiles).value.futureValue

      result mustBe Left(error)
      verify(mockGetSqlQueries, times(0)).getFuelStations(any[utils.GeoBoundingBox])
      verify(mockGetSqlQueries, times(0)).findPricesForStations(any[Seq[String]])
    }

    "return a view model containing the station with an empty fuelPrices list when the station has prices but none match the requested fuel type" in {
      val wrongFuelTypePrice = FuelPrice(155.9, FuelType.B7_STANDARD, now, now)
      val nearStationWithPrice = FuelStationWithPrices(nearStation, List(wrongFuelTypePrice), 1000.0)

      when(mockPostcodesIOConnector.getCoordinates(postcode)(using hc)).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](geoLoc)
      )
      when(mockGetSqlQueries.getFuelStations(any[utils.GeoBoundingBox])).thenReturn(
        Future.successful(Seq(nearStation))
      )
      when(mockGetSqlQueries.findPricesForStations(any[Seq[String]])).thenReturn(
        Future.successful(Seq(nearStationWithPrice))
      )

      val result = sut.getViewModel(postcode, FuelType.E10, radiusMiles).value.futureValue

      result.isRight mustBe true
      val viewModel = result.toOption.get

      viewModel.centrePostcode mustBe postcode
      viewModel.centreLocation mustBe geoLoc
      viewModel.radius mustBe radiusMiles
      viewModel.fuelType mustBe FuelType.E10

      viewModel.fuelStationWithPrices.map(_.nodeId) mustBe List("nearNodeId")
      viewModel.fuelStationWithPrices.head.fuelPrices mustBe empty

      verify(mockGetSqlQueries).findPricesForStations(Seq("nearNodeId"))
    }

    "return a view model with an empty fuelStationWithPrices list when no prices are found for the fuel type, while preserving centre information" in {
      when(mockPostcodesIOConnector.getCoordinates(postcode)(using hc)).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](geoLoc)
      )
      when(mockGetSqlQueries.getFuelStations(any[utils.GeoBoundingBox])).thenReturn(
        Future.successful(Seq(nearStation))
      )
      when(mockGetSqlQueries.findPricesForStations(any[Seq[String]])).thenReturn(
        Future.successful(Seq.empty)
      )

      val result = sut.getViewModel(postcode, FuelType.E10, radiusMiles).value.futureValue

      result.isRight mustBe true
      val viewModel = result.toOption.get

      viewModel.centrePostcode mustBe postcode
      viewModel.centreLocation mustBe geoLoc
      viewModel.radius mustBe radiusMiles
      viewModel.fuelType mustBe FuelType.E10

      viewModel.fuelStationWithPrices mustBe empty

      verify(mockGetSqlQueries).findPricesForStations(Seq("nearNodeId"))
    }

    "return a view model with an empty fuelStationWithPrices list and call findPricesForStations with an empty Seq when all stations are filtered out by radius" in {
      when(mockPostcodesIOConnector.getCoordinates(postcode)(using hc)).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](geoLoc)
      )
      when(mockGetSqlQueries.getFuelStations(any[utils.GeoBoundingBox])).thenReturn(
        Future.successful(Seq(farStation))
      )
      when(mockGetSqlQueries.findPricesForStations(Seq.empty)).thenReturn(
        Future.successful(Seq.empty)
      )

      val result = sut.getViewModel(postcode, FuelType.E10, radiusMiles).value.futureValue

      result.isRight mustBe true
      val viewModel = result.toOption.get

      viewModel.centrePostcode mustBe postcode
      viewModel.centreLocation mustBe geoLoc
      viewModel.radius mustBe radiusMiles
      viewModel.fuelType mustBe FuelType.E10

      viewModel.fuelStationWithPrices mustBe empty

      verify(mockGetSqlQueries).findPricesForStations(Seq.empty)
    }

    "build the GeoBoundingBox passed to getFuelStations using the correct radius conversion and coordinates" in {
      when(mockPostcodesIOConnector.getCoordinates(postcode)(using hc)).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](geoLoc)
      )
      when(mockGetSqlQueries.getFuelStations(any[utils.GeoBoundingBox])).thenReturn(
        Future.successful(Seq.empty)
      )
      when(mockGetSqlQueries.findPricesForStations(Seq.empty)).thenReturn(
        Future.successful(Seq.empty)
      )

      sut.getViewModel(postcode, FuelType.E10, radiusMiles).value.futureValue

      val boundingBoxCaptor = org.mockito.ArgumentCaptor.forClass(classOf[utils.GeoBoundingBox])
      verify(mockGetSqlQueries).getFuelStations(boundingBoxCaptor.capture())
      val actualBoundingBox = boundingBoxCaptor.getValue

      val expectedBoundingBox = utils.GeoBoundingBox.fromRadius(
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