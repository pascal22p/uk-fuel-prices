package connectors

import cats.data.EitherT
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{ok, urlEqualTo}
import config.AppConfig
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import testUtils.{BaseSpec, WireMockHelper}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import models.{FuelStation, FuelStationLocation}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FuelPriceConnectorSpec extends BaseSpec with WireMockHelper {

  val mockOAuthConnector: OAuthConnector = mock[OAuthConnector]
  val mockAppConfig: AppConfig = mock[AppConfig]

  protected override def localGuiceApplicationBuilder(): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .overrides(
        bind[OAuthConnector].toInstance(mockOAuthConnector),
        bind[AppConfig].toInstance(mockAppConfig)
      )

  lazy val sut: FuelPriceConnector = app.injector.instanceOf[FuelPriceConnector]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val fuelStationOkResponse: String =
    """
      |[
      |  {
      |    "node_id": "9b275ab576eeba3c6677984be15ee22a74e54fdfe8e5ea700e84a03178dc4ac1",
      |    "public_phone_number": null,
      |    "trading_name": "TEST",
      |    "is_same_trading_and_brand_name": true,
      |    "brand_name": "TEST",
      |    "temporary_closure": false,
      |    "permanent_closure": false,
      |    "permanent_closure_date": null,
      |    "is_motorway_service_station": false,
      |    "is_supermarket_service_station": false,
      |    "location": {
      |      "address_line_1": "address line 1",
      |      "address_line_2": null,
      |      "city": "City",
      |      "country": "England",
      |      "county": null,
      |      "postcode": "post code",
      |      "latitude": 51.5268585,
      |      "longitude": -0.700361
      |    },
      |    "amenities": [
      |      "water_filling"
      |    ],
      |    "opening_times": {
      |      "usual_days": {
      |        "monday": {
      |          "open": "00:00:00",
      |          "close": "00:00:00",
      |          "is_24_hours": false
      |        },
      |        "tuesday": {
      |          "open": "00:00:00",
      |          "close": "00:00:00",
      |          "is_24_hours": false
      |        },
      |        "wednesday": {
      |          "open": "00:00:00",
      |          "close": "00:00:00",
      |          "is_24_hours": false
      |        },
      |        "thursday": {
      |          "open": "00:00:00",
      |          "close": "00:00:00",
      |          "is_24_hours": false
      |        },
      |        "friday": {
      |          "open": "00:00:00",
      |          "close": "00:00:00",
      |          "is_24_hours": false
      |        },
      |        "saturday": {
      |          "open": "00:00:00",
      |          "close": "00:00:00",
      |          "is_24_hours": false
      |        },
      |        "sunday": {
      |          "open": "00:00:00",
      |          "close": "23:59:00",
      |          "is_24_hours": true
      |        }
      |      },
      |      "bank_holiday": {
      |        "type": "bank holiday",
      |        "open_time": "00:00:00",
      |        "close_time": "00:00:00",
      |        "is_24_hours": false
      |      }
      |    },
      |    "fuel_types": [
      |      "E10",
      |      "E5",
      |      "HVO",
      |      "B10"
      |    ]
      |  },
      |  {
      |    "node_id": "4fd9a4c6b48358b9b5c95989fba100fdcbb87c9e909ed4ce1ad96f64ffb8b56a",
      |    "public_phone_number": "phone number",
      |    "trading_name": "TEST FORECOURT 1",
      |    "is_same_trading_and_brand_name": true,
      |    "brand_name": "Brand name",
      |    "temporary_closure": false,
      |    "permanent_closure": null,
      |    "permanent_closure_date": null,
      |    "is_motorway_service_station": false,
      |    "is_supermarket_service_station": false,
      |    "location": {
      |      "address_line_1": "address line different",
      |      "address_line_2": "",
      |      "city": "City again",
      |      "country": "ENGLAND",
      |      "county": "EAST YORKSHIRE",
      |      "postcode": "post code",
      |      "latitude": 51.258503,
      |      "longitude": -3.417567
      |    },
      |    "amenities": [
      |      "adblue_packaged",
      |      "adblue_pumps",
      |      "car_wash",
      |      "customer_toilets"
      |    ],
      |    "opening_times": {
      |      "usual_days": {
      |        "monday": {
      |          "open": "06:00:01",
      |          "close": "23:00:01",
      |          "is_24_hours": false
      |        },
      |        "tuesday": {
      |          "open": "06:00:01",
      |          "close": "23:00:01",
      |          "is_24_hours": false
      |        },
      |        "wednesday": {
      |          "open": "06:00:01",
      |          "close": "23:00:01",
      |          "is_24_hours": false
      |        },
      |        "thursday": {
      |          "open": "06:00:01",
      |          "close": "23:00:01",
      |          "is_24_hours": false
      |        },
      |        "friday": {
      |          "open": "06:00:01",
      |          "close": "23:00:01",
      |          "is_24_hours": false
      |        },
      |        "saturday": {
      |          "open": "06:00:01",
      |          "close": "23:00:01",
      |          "is_24_hours": false
      |        },
      |        "sunday": {
      |          "open": "06:00:01",
      |          "close": "23:00:01",
      |          "is_24_hours": false
      |        }
      |      },
      |      "bank_holiday": {
      |        "type": "standard",
      |        "open_time": "06:00:01",
      |        "close_time": "23:00:01",
      |        "is_24_hours": false
      |      }
      |    },
      |    "fuel_types": [
      |      "B10"
      |    ]
      |  },
      |  {
      |    "node_id": "91bdda1c07fa05110a31639cc66932f9ed8bd388d4f6be542a423365bcfd53e1",
      |    "public_phone_number": "phone number 2",
      |    "trading_name": "trading name",
      |    "is_same_trading_and_brand_name": true,
      |    "brand_name": "brand name again",
      |    "temporary_closure": false,
      |    "permanent_closure": null,
      |    "permanent_closure_date": null,
      |    "is_motorway_service_station": false,
      |    "is_supermarket_service_station": false,
      |    "location": {
      |      "address_line_1": "address line 3",
      |      "address_line_2": "second address line",
      |      "city": "City 3",
      |      "country": "ENGLAND",
      |      "county": "LEICESTERSHIRE",
      |      "postcode": "postcode 5",
      |      "latitude": 50.503343,
      |      "longitude": -2.12444
      |    },
      |    "amenities": [
      |      "adblue_packaged",
      |      "adblue_pumps",
      |      "car_wash",
      |      "customer_toilets",
      |      "water_filling"
      |    ],
      |    "opening_times": {
      |      "usual_days": {
      |        "monday": {
      |          "open": "06:00:00",
      |          "close": "22:00:00",
      |          "is_24_hours": false
      |        },
      |        "tuesday": {
      |          "open": "06:00:00",
      |          "close": "22:00:00",
      |          "is_24_hours": false
      |        },
      |        "wednesday": {
      |          "open": "06:00:00",
      |          "close": "22:00:00",
      |          "is_24_hours": false
      |        },
      |        "thursday": {
      |          "open": "06:00:00",
      |          "close": "22:00:00",
      |          "is_24_hours": false
      |        },
      |        "friday": {
      |          "open": "06:00:00",
      |          "close": "22:00:00",
      |          "is_24_hours": false
      |        },
      |        "saturday": {
      |          "open": "06:00:00",
      |          "close": "22:00:00",
      |          "is_24_hours": false
      |        },
      |        "sunday": {
      |          "open": "06:00:00",
      |          "close": "22:00:00",
      |          "is_24_hours": false
      |        }
      |      },
      |      "bank_holiday": {
      |        "type": "standard",
      |        "open_time": "08:00:00",
      |        "close_time": "20:00:00",
      |        "is_24_hours": false
      |      }
      |    },
      |    "fuel_types": [
      |      "E5",
      |      "HVO",
      |      "B10",
      |      "B7_PREMIUM",
      |      "B7_STANDARD"
      |    ]
      |  }
      |]
      |""".stripMargin

  "fuelStations" must {
    "return a list of fuel stations" in {

      when(mockOAuthConnector.getValidToken()(using any())).thenReturn(EitherT.rightT[Future, UpstreamErrorResponse]("valid-token"))
      when(mockAppConfig.fuelApiHost).thenReturn(s"http://localhost:${server.port()}")

      server.stubFor(
        WireMock
          .get(urlEqualTo("/api/v1/pfs?batch-number=1"))
          .willReturn(ok(fuelStationOkResponse))
      )

      val result = sut.fuelStations(1).value.futureValue

      result mustBe
        Right(List(
          FuelStation("9b275ab576eeba3c6677984be15ee22a74e54fdfe8e5ea700e84a03178dc4ac1", "TEST", Some(true), "TEST", Some(false), Some(false), Some(false), Some(false), FuelStationLocation(Some("address line 1"), None, "City", Some("England"), None, "post code", 51.5268585, -0.700361), List("E10", "E5", "HVO", "B10")),
          FuelStation("4fd9a4c6b48358b9b5c95989fba100fdcbb87c9e909ed4ce1ad96f64ffb8b56a", "TEST FORECOURT 1", Some(true), "Brand name", Some(false), None, Some(false), Some(false), FuelStationLocation(Some("address line different"), Some(""), "City again", Some("ENGLAND"), Some("EAST YORKSHIRE"), "post code", 51.258503, -3.417567), List("B10")),
          FuelStation("91bdda1c07fa05110a31639cc66932f9ed8bd388d4f6be542a423365bcfd53e1", "trading name", Some(true), "brand name again", Some(false), None, Some(false), Some(false), FuelStationLocation(Some("address line 3"), Some("second address line"), "City 3", Some("ENGLAND"), Some("LEICESTERSHIRE"), "postcode 5", 50.503343, -2.12444), List("E5", "HVO", "B10", "B7_PREMIUM", "B7_STANDARD"))
        ))
    }
  }
}
