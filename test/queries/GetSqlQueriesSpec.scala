package queries

import anorm.SQL
import models.*
import play.api.Logging
import play.api.mvc.Request
import play.api.test.FakeRequest
import testUtils.MariadbHelper

import java.time.Instant
import scala.concurrent.Future

class GetSqlQueriesSpec extends MariadbHelper with Logging {
  lazy val sut: GetSqlQueries = app.injector.instanceOf[GetSqlQueries]
  lazy val insertSqlQueries: InsertSqlQueries = app.injector.instanceOf[InsertSqlQueries]

  implicit val request: Request[?] = FakeRequest()
    .withHeaders("X-Request-Id" -> "requestID")
    .addAttr(Attrs.RequestId, "requestID")
    .addAttr(Attrs.SessionId, "sessionID")

  def fakeFuelPrice(
                     price: Double = 1.45,
                     fuelType: FuelType = FuelType.E10,
                     priceLastUpdated: Instant = Instant.parse("2024-01-01T00:00:00Z"),
                     priceChangeEffectiveTimestamp: Instant = Instant.parse("2024-01-01T00:00:00Z")
                   ): FuelPrice =
    FuelPrice(
      price = price,
      fuelType = fuelType,
      priceLastUpdated = priceLastUpdated,
      priceChangeEffectiveTimestamp = priceChangeEffectiveTimestamp
    )

  "getLatestFuelPricesWithStation" must {
    "populates the address sequence from addressLine1, addressLine2, city and postcode, dropping empty components" in {
      val nodeId = "B739362AF81ACC9FEC9EDA6F155348125FA2D5C1772C96BF6855A1BAD0179733"

      val location = fakeFuelStationLocation(
        addressLine1 = Some("addressLine1"),
        city = "London",
        location = None
      )

      val result = (for {
        _ <- insertSqlQueries.insertStations(Seq(
          fakeFuelStation(nodeId = nodeId, location = location)
        ))
        _ <- Future {
          db.withConnection { implicit conn =>
            SQL(
              """INSERT INTO fuel_types (name)
                |VALUES ({name})""".stripMargin
            ).on(
              "name" -> "E10"
            ).executeInsert()
          }
        }
        _ <- insertSqlQueries.insertFuelPrices(Seq(
          FuelPriceForStation(
            nodeId = nodeId,
            fuelPrices = Seq(
              fakeFuelPrice(price = 1.45, fuelType = FuelType.E10, priceChangeEffectiveTimestamp = Instant.now)
            )
          )
        ))
        result <- sut.getLatestFuelPricesWithStation(numberOfResult = 10, stationsFilter = Seq(nodeId))
      } yield result).futureValue

      result must have size 1
      result.head.nodeId mustBe nodeId
    }
  }

  "findAbsentFuelStations" must {
    "returns list of nodeIds not in the station table" in {


      val result = (for {
        _ <- insertSqlQueries.insertStations(Seq(
          fakeFuelStation(nodeId = "B739362AF81ACC9FEC9EDA6F155348125FA2D5C1772C96BF6855A1BAD0179711"),
          fakeFuelStation(nodeId = "B739362AF81ACC9FEC9EDA6F155348125FA2D5C1772C96BF6855A1BAD0179722")
        ))
        result <- sut.findAbsentFuelStations(Seq(
          "B739362AF81ACC9FEC9EDA6F155348125FA2D5C1772C96BF6855A1BAD0179711",
          "B739362AF81ACC9FEC9EDA6F155348125FA2D5C1772C96BF6855A1BAD0179722"
        ))
      } yield result).futureValue

      result mustBe Seq.empty
    }
  }

  "findPricesForStation" must {
    "returns list of prices for a fuel station" in {
      val nodeId1 = "B739362AF81ACC9FEC9EDA6F155348125FA2D5C1772C96BF6855A1BAD0179711"
      val nodeId2 = "B739362AF81ACC9FEC9EDA6F155348125FA2D5C1772C96BF6855A1BAD0179722"

      val result = (for {
        _ <- insertSqlQueries.insertStations(Seq(
          fakeFuelStation(nodeId = nodeId1),
          fakeFuelStation(nodeId = nodeId2)
        ))
        _ <- Future {
          db.withConnection { implicit conn =>
            SQL(
              """INSERT INTO fuel_types (name)
                |VALUES ({name})""".stripMargin
            ).on(
              "name" -> "E10"
            ).executeInsert()
          }
        }
        _ <- Future {
          db.withConnection { implicit conn =>
            SQL(
              """INSERT INTO fuel_types (name)
                |VALUES ({name})""".stripMargin
            ).on(
              "name" -> "E5"
            ).executeInsert()
          }
        }
        _ <- insertSqlQueries.insertFuelPrices(Seq(
          FuelPriceForStation(
            nodeId = nodeId1,
            fuelPrices = Seq(
              fakeFuelPrice(price = 1.45, fuelType = FuelType.E10),
              fakeFuelPrice(price = 1.55, fuelType = FuelType.E5)
            )
          )
        ))
        result <- sut.findHistoricalPricesForStation(
          nodeId1
        )
      } yield result).futureValue

      result mustBe Some(
        fakeFuelStationWithPrices(
          nodeId = nodeId1,
          isSameTradingAndBrandName = None,
          brandName = "",
          location = fakeFuelStationLocation(country = None),
          fuelTypes = List(),
          fuelPrices = List(
            FuelPrice(
              1.45,
              FuelType.E10,
              Instant.parse("2024-01-01T00:00:00Z"),
              Instant.parse("2024-01-01T00:00:00Z")
            ),
            FuelPrice(
              1.55,
              FuelType.E5,
              Instant.parse("2024-01-01T00:00:00Z"),
              Instant.parse("2024-01-01T00:00:00Z")
            )
          )
        )
      )
    }

    "returns None for nullable station flags when the database values are NULL" in {
      val nodeId =
        "B739362AF81ACC9FEC9EDA6F155348125FA2D5C1772C96BF6855A1BAD0179733"

      val result = (for {
        _ <- insertSqlQueries.insertStations(
          Seq(
            fakeFuelStation(
              nodeId = nodeId,
              temporaryClosure = None,
              permanentClosure = None,
              isMotorwayServiceStation = None,
              isSupermarketServiceStation = None
            )
          )
        )

        _ <- Future {
          db.withConnection { implicit conn =>
            SQL(
              """INSERT INTO fuel_types (name)
                |VALUES ({name})""".stripMargin
            ).on(
              "name" -> "E10"
            ).executeInsert()
          }
        }

        _ <- insertSqlQueries.insertFuelPrices(
          Seq(
            FuelPriceForStation(
              nodeId = nodeId,
              fuelPrices = Seq(
                fakeFuelPrice(fuelType = FuelType.E10)
              )
            )
          )
        )

        result <- sut.findHistoricalPricesForStation(nodeId)
      } yield result).futureValue

      result mustBe Some(
        fakeFuelStationWithPrices(
          nodeId = nodeId,
          isSameTradingAndBrandName = None,
          brandName = "",
          temporaryClosure = None,
          permanentClosure = None,
          isMotorwayServiceStation = None,
          isSupermarketServiceStation = None,
          location = fakeFuelStationLocation(country = None),
          fuelTypes = List(),
          fuelPrices = List(
            FuelPrice(
              1.45,
              FuelType.E10,
              Instant.parse("2024-01-01T00:00:00Z"),
              Instant.parse("2024-01-01T00:00:00Z")
            )
          )
        )
      )
    }
  }
}
