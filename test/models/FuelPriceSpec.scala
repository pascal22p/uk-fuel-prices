package models

import play.api.libs.json.{JsError, JsResultException, Json}
import testUtils.BaseSpec

import java.time.Instant

class FuelPriceSpec extends BaseSpec {

  val filterDate: Instant = Instant.parse("2026-01-01T00:00:00Z")

  def fuelPriceJson(priceLastUpdated: Instant, priceChangeEffectiveTimestamp: Instant): String =
    s"""
       |{
       |  "price": 1.45,
       |  "fuel_type": "E10",
       |  "price_last_updated": "$priceLastUpdated",
       |  "price_change_effective_timestamp": "$priceChangeEffectiveTimestamp"
       |}
       |""".stripMargin

  "fuelPriceReads" must {

    "successfully parse a FuelPrice when both timestamps are after minValidDate" in {
      val json = Json.parse(
        fuelPriceJson(
          priceLastUpdated = filterDate.plusSeconds(60),
          priceChangeEffectiveTimestamp = filterDate.plusSeconds(60)
        )
      )

      val result = json.validate[FuelPrice](using FuelPrice.fuelPriceReads(filterDate))

      result.isSuccess mustBe true
      result.get.price mustBe 1.45
      result.get.fuelType mustBe FuelType.E10
    }

    "fail to parse when price_last_updated is not after minValidDate" in {
      val json = Json.parse(
        fuelPriceJson(
          priceLastUpdated = filterDate.minusSeconds(60),
          priceChangeEffectiveTimestamp = filterDate.plusSeconds(60)
        )
      )

      val result = json.validate[FuelPrice](using FuelPrice.fuelPriceReads(filterDate))

      result.isError mustBe true
      result match {
        case JsError(errors) =>
          errors.flatMap(_._2).flatMap(_.messages) must contain("error.instant.tooOld")
        case _ => fail("expected JsError")
      }
    }

    "fail to parse when price_change_effective_timestamp is not after minValidDate" in {
      val json = Json.parse(
        fuelPriceJson(
          priceLastUpdated = filterDate.plusSeconds(60),
          priceChangeEffectiveTimestamp = filterDate.minusSeconds(60)
        )
      )

      val result = json.validate[FuelPrice](using FuelPrice.fuelPriceReads(filterDate))

      result.isError mustBe true
    }

    "fail to parse when both timestamps equal minValidDate exactly (not strictly after)" in {
      val json = Json.parse(
        fuelPriceJson(
          priceLastUpdated = filterDate,
          priceChangeEffectiveTimestamp = filterDate
        )
      )

      val result = json.validate[FuelPrice](using FuelPrice.fuelPriceReads(filterDate))

      result.isError mustBe true
      result match {
        case JsError(errors) =>
          val messages = errors.flatMap(_._2).flatMap(_.messages)
          messages must contain("error.instant.tooOld")
          // both timestamps are exactly on the boundary, so both fields should fail
          messages.count(_ == "error.instant.tooOld") mustBe 2
        case _ => fail("expected JsError")
      }
    }

    "throw a JsResultException when using .as with timestamps before minValidDate" in {
      val json = Json.parse(
        fuelPriceJson(
          priceLastUpdated = filterDate.minusSeconds(60),
          priceChangeEffectiveTimestamp = filterDate.minusSeconds(60)
        )
      )

      intercept[JsResultException] {
        json.as[FuelPrice](using FuelPrice.fuelPriceReads(filterDate))
      }
    }
  }
}