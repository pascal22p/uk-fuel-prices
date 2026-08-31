package views

import models.*
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.FakeRequest
import testUtils.BaseSpec
import views.html.StationView
import play.api.test.Helpers.*
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}

import java.time.LocalDateTime

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
    fakeFuelStationWithPrices(
      temporaryClosure = temporaryClosure,
      permanentClosure = permanentClosure
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

    "not render the map when the station has no location" in {
      val station = fakeStation().copy(
        location = fakeFuelStationLocation(location = None)
      )

      val html = contentAsString(
        stationView(station)
      )

      html must not include """id="map""""
    }
  }
}