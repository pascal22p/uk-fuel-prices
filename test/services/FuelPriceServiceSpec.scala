package services

import connectors.FuelPriceConnector
import models.{FuelPrice, FuelPriceForStation, FuelStation, FuelStationLocation, FuelType}
import queries.{DeleteSqlQueries, GetSqlQueries, InsertSqlQueries}
import testUtils.BaseSpec
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.http.Status.{INTERNAL_SERVER_ERROR, NOT_FOUND}

import scala.jdk.CollectionConverters.*
import java.time.Instant
import scala.concurrent.Future
import cats.data.EitherT
import org.mockito.ArgumentCaptor

class FuelPriceServiceSpec extends BaseSpec {

  val mockFuelPriceConnector: FuelPriceConnector = mock[FuelPriceConnector]
  val mockInsertSqlQueries: InsertSqlQueries = mock[InsertSqlQueries]
  val mockGetSqlQueries: GetSqlQueries = mock[GetSqlQueries]
  val mockDeleteSqlQueries: DeleteSqlQueries = mock[DeleteSqlQueries]
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val sut = new FuelPriceService(mockFuelPriceConnector, mockInsertSqlQueries, mockGetSqlQueries, mockDeleteSqlQueries)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockFuelPriceConnector, mockInsertSqlQueries, mockGetSqlQueries)
  }

  "uploadAllFuelStations" must {
    "upload data recursively until not found" in {
      when(mockFuelPriceConnector.fuelStations(any(), any())(using any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](Seq(
          FuelStation("nodeId1", "tradingName", None, "brandName", None, None, None, None, FuelStationLocation(None, None, "city", None, None, "postcode", 0.0, 0.0), List.empty)
        )),
        EitherT.rightT[Future, UpstreamErrorResponse](Seq(
          FuelStation("nodeId2", "tradingName", None, "brandName", None, None, None, None, FuelStationLocation(None, None, "city", None, None, "postcode", 0.0, 0.0), List.empty)
        )),
        EitherT.leftT[Future, Seq[FuelStation]](UpstreamErrorResponse("not found", NOT_FOUND))
      )

      when(mockInsertSqlQueries.insertStations(any())).thenReturn(
        Future.successful(1)
      )

      val result = sut.uploadAllFuelStations().value.futureValue

      result mustBe Right(true)
      verify(mockFuelPriceConnector, times(3)).fuelStations(any(), any())(using any())
      verify(mockInsertSqlQueries, times(2)).insertStations(any())
    }

    "upload data recursively until empty" in {
      when(mockFuelPriceConnector.fuelStations(any(), any())(using any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](Seq(
          FuelStation("nodeId1", "tradingName", None, "brandName", None, None, None, None, FuelStationLocation(None, None, "city", None, None, "postcode", 0.0, 0.0), List.empty)
        )),
        EitherT.rightT[Future, UpstreamErrorResponse](Seq.empty)
      )

      when(mockInsertSqlQueries.insertStations(any())).thenReturn(
        Future.successful(1)
      )

      val result = sut.uploadAllFuelStations().value.futureValue

      result mustBe Right(true)
      verify(mockFuelPriceConnector, times(2)).fuelStations(any(), any())(using any())
      verify(mockInsertSqlQueries, times(1)).insertStations(any())
    }

    "return UpstreamErrorResponse" in {
      when(mockFuelPriceConnector.fuelStations(any(), any())(using any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](Seq(
          FuelStation("nodeId1", "tradingName", None, "brandName", None, None, None, None, FuelStationLocation(None, None, "city", None, None, "postcode", 0.0, 0.0), List.empty)
        )),
        EitherT.rightT[Future, UpstreamErrorResponse](Seq(
          FuelStation("nodeId2", "tradingName", None, "brandName", None, None, None, None, FuelStationLocation(None, None, "city", None, None, "postcode", 0.0, 0.0), List.empty)
        )),
        EitherT.leftT[Future, Seq[FuelStation]](UpstreamErrorResponse("server error", INTERNAL_SERVER_ERROR))
      )

      when(mockInsertSqlQueries.insertStations(any())).thenReturn(
        Future.successful(1)
      )

      val result = sut.uploadAllFuelStations().value.futureValue

      result mustBe a[Left[UpstreamErrorResponse, ?]]
      verify(mockFuelPriceConnector, times(3)).fuelStations(any(), any())(using any())
      verify(mockInsertSqlQueries, times(2)).insertStations(any())
    }
  }

  "uploadAllFuelPrices" must {
    "upload data recursively until not found" in {
      when(mockFuelPriceConnector.fuelPrices(any(), any())(using any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](Seq(
          FuelPriceForStation("nodeId1", None, "tradingName", Seq(FuelPrice(150.0, FuelType.E10, Instant.now, Instant.now)))
        )),
        EitherT.rightT[Future, UpstreamErrorResponse](Seq(
          FuelPriceForStation("nodeId2", None, "tradingName", Seq(FuelPrice(1.50, FuelType.E10, Instant.now, Instant.now), FuelPrice(1500, FuelType.E10, Instant.now, Instant.now)))
        )),
        EitherT.leftT[Future, Seq[FuelPriceForStation]](UpstreamErrorResponse("not found", NOT_FOUND))
      )

      when(mockInsertSqlQueries.insertFuelPrices(any())).thenReturn(
        Future.successful(1)
      )

      when(mockDeleteSqlQueries.deleteFuelPrices(any())).thenReturn(
        Future.successful(1)
      )
      when(mockDeleteSqlQueries.deleteStations(any())).thenReturn(
        Future.successful(1)
      )

      val argumentCaptorFuelPrices: ArgumentCaptor[Seq[FuelPriceForStation]] =
        ArgumentCaptor.forClass(classOf[Seq[FuelPriceForStation]])

      val result = sut.uploadAllFuelPrices().value.futureValue

      result mustBe Right(true)
      verify(mockFuelPriceConnector, times(3)).fuelPrices(any(), any())(using any())
      verify(mockInsertSqlQueries, times(2)).insertFuelPrices(argumentCaptorFuelPrices.capture())

      argumentCaptorFuelPrices.getAllValues.asScala.toSeq.flatten.flatMap(_.fuelPrices).map(_.price) mustBe Seq(150.0, 150.0, 150.0)
    }

    "upload data recursively until empty" in {
      when(mockFuelPriceConnector.fuelPrices(any(), any())(using any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](Seq(
          FuelPriceForStation("nodeId", None, "tradingName", Seq.empty)
        )),
        EitherT.rightT[Future, UpstreamErrorResponse](Seq.empty)
      )

      when(mockInsertSqlQueries.insertFuelPrices(any())).thenReturn(
        Future.successful(1)
      )

      when(mockDeleteSqlQueries.deleteFuelPrices(any())).thenReturn(
        Future.successful(1)
      )
      when(mockDeleteSqlQueries.deleteStations(any())).thenReturn(
        Future.successful(1)
      )

      val result = sut.uploadAllFuelPrices().value.futureValue

      result mustBe Right(true)
      verify(mockFuelPriceConnector, times(2)).fuelPrices(any(), any())(using any())
      verify(mockInsertSqlQueries, times(1)).insertFuelPrices(any())
    }

    "return UpstreamErrorResponse" in {
      when(mockFuelPriceConnector.fuelPrices(any(), any())(using any())).thenReturn(
        EitherT.leftT[Future, Seq[FuelPriceForStation]](UpstreamErrorResponse("server error", INTERNAL_SERVER_ERROR))
      )

      when(mockInsertSqlQueries.insertFuelPrices(any())).thenReturn(
        Future.successful(1)
      )

      val result = sut.uploadAllFuelPrices().value.futureValue

      result mustBe a[Left[UpstreamErrorResponse, ?]]
      verify(mockFuelPriceConnector, times(1)).fuelPrices(any(), any())(using any())
      verify(mockInsertSqlQueries, times(0)).insertFuelPrices(any())
    }
  }

  "getFuelPriceFromPostcode" must {
    "return prices for a postcode" in {
      val now = Instant.now
      when(mockGetSqlQueries.findFuelStations(any())).thenReturn(
        Future.successful(Seq(
          FuelStation("nodeId1", "tradingName", None, "brandName", None, None, None, None, FuelStationLocation(None, None, "city", None, None, "postcode", 0.0, 0.0), List.empty),
          FuelStation("nodeId2", "tradingName", None, "brandName", None, None, None, None, FuelStationLocation(None, None, "city", None, None, "postcode", 0.0, 0.0), List.empty)
        ))
      )

      when(mockGetSqlQueries.findPricesForStation(any())).thenReturn(
        Future.successful(Seq(FuelPrice(150.0, FuelType.E10, now, now))),
        Future.successful(Seq(FuelPrice(110.0, FuelType.E5, now, now)))
      )

      val result = sut.getFuelPriceFromPostcode("NE").futureValue

      result.length mustBe 2
      result.find(_.nodeId == "nodeId1").map(_.fuelPrices) mustBe Some(Seq(FuelPrice(150.0, FuelType.E10, now, now)))
      result.find(_.nodeId == "nodeId2").map(_.fuelPrices) mustBe Some(Seq(FuelPrice(110.0, FuelType.E5, now, now)))
      
      verify(mockGetSqlQueries, times(2)).findPricesForStation(any())
    }
  }
}
