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
import java.time.Instant
import models.{GeoLoc, FuelStationLocation, FuelStationWithPrices, FuelPrice, FuelType}

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
  
  def fakeFuelStationWithPrices(
                               nodeId: String = "b739362af81acc9fec9eda6f155348125fa2d5c1772c96bf6855a1bad0179711",
                               tradingName: String = "Test Fuel Station",
                               isSameTradingAndBrandName: Option[Boolean] = Some(true),
                               brandName: String = "Test Brand",
                               temporaryClosure: Option[Boolean] = Some(false),
                               permanentClosure: Option[Boolean] = Some(false),
                               isMotorwayServiceStation: Option[Boolean] = Some(false),
                               isSupermarketServiceStation: Option[Boolean] = Some(false),
                               location: FuelStationLocation = FuelStationLocation(
                                 addressLine1 = Some("123 Test Street"),
                                 addressLine2 = None,
                                 city = "City",
                                 county = None,
                                 country = Some("UK"),
                                 postcode = "postcode",
                                 location = Some(GeoLoc(51.5014, -0.1419))
                               ),
                               fuelTypes: List[String] = List("PETROL", "DIESEL"),
                               fuelPrices: Seq[FuelPrice] = Seq(
                                 FuelPrice(1.45, FuelType.E10, Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-01T00:00:00Z")),
                                 FuelPrice(1.55, FuelType.B10, Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-01T00:00:00Z"))
                               ),
                               distance: Double = 0.0
                               ): FuelStationWithPrices = 
    FuelStationWithPrices(
      nodeId = nodeId,
      tradingName = tradingName,
      isSameTradingAndBrandName = isSameTradingAndBrandName,
      brandName = brandName,
      temporaryClosure = temporaryClosure,
      permanentClosure = permanentClosure,
      isMotorwayServiceStation = isMotorwayServiceStation,
      isSupermarketServiceStation = isSupermarketServiceStation,
      location = location,
      fuelTypes = fuelTypes,
      fuelPrices = fuelPrices,
      distance = distance
    )
    

}
