package views

import models.*
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.FakeRequest
import testUtils.BaseSpec
import views.html.StationView
import play.api.test.Helpers.*
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}

import java.time.{Instant, LocalDateTime}

class StationViewSpec extends BaseSpec {

  lazy val stationView: StationView =
    app.injector.instanceOf[StationView]

  val request: Request[AnyContentAsEmpty.type] = FakeRequest()
  implicit val authenticatedRequest: AuthenticatedRequest[AnyContentAsEmpty.type] =
    AuthenticatedRequest(request, Session("sessionId", SessionData(), LocalDateTime.now()))
  implicit def messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
  implicit lazy val messages: Messages = MessagesImpl(Lang("en"), messagesApi)

  def fakeStation(
                   temporaryClosure: Option[Boolean] = None,
                   permanentClosure: Option[Boolean] = None
                 ): FuelStationWithPrices =
    FuelStationWithPrices(
      nodeId =
        "B739362AF81ACC9FEC9EDA6F155348125FA2D5C1772C96BF6855A1BAD0179711",
      tradingName = "Test Fuel Station",
      isSameTradingAndBrandName = None,
      brandName = "",
      temporaryClosure = temporaryClosure,
      permanentClosure = permanentClosure,
      isMotorwayServiceStation = None,
      isSupermarketServiceStation = None,
      location = FuelStationLocation(
        addressLine1 = Some("123 Test Street"),
        addressLine2 = None,
        city = "London",
        county = None,
        country = None,
        postcode = "SW1A 1AA",
        location = Some(GeoLoc(51.5014,-0.1419))
      ),
      fuelTypes = List.empty,
      fuelPrices = Seq(
        FuelPrice(
          price = 1.45,
          fuelType = FuelType.E10,
          priceLastUpdated =
            Instant.parse("2024-01-01T00:00:00Z"),
          priceChangeEffectiveTimestamp =
            Instant.parse("2024-01-01T00:00:00Z")
        )
      ),
      distance = 0.0
    )

  "StationView" must {

    "render the permanent closure banner when the station is permanently closed" in {
      val html = contentAsString(
        stationView(
          fakeStation(permanentClosure = Some(true))
        )
      )

      html must include(
        "This fuel station is permanently closed."
      )
    }

    "render the temporary closure banner when the station is temporarily closed" in {
      val html = contentAsString(
        stationView(
          fakeStation(temporaryClosure = Some(true))
        )
      )

      html must include(
        "This fuel station is temporarily closed."
      )
    }

    "not render a closure banner when both closure flags are None" in {
      val html = contentAsString(
        stationView(
          fakeStation(
            temporaryClosure = None,
            permanentClosure = None
          )
        )
      )

      html must not include(
        "This fuel station is permanently closed."
        )

      html must not include(
        "This fuel station is temporarily closed."
        )
    }

    "prefer the permanent closure banner when both closure flags are true" in {
      val html = contentAsString(
        stationView(
          fakeStation(
            temporaryClosure = Some(true),
            permanentClosure = Some(true)
          )
        )
      )

      html must include(
        "This fuel station is permanently closed."
      )

      html must not include(
        "This fuel station is temporarily closed."
        )
    }
  }
}