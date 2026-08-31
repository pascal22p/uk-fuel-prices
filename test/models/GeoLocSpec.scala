package models

import testUtils.BaseSpec
import play.api.libs.json.{JsNull, JsSuccess, Json}

class GeoLocSpec extends BaseSpec {

  "GeoLoc.geoLocJsonReads" must {

    "read valid UK coordinates" in {
      val json = Json.obj(
        "latitude" -> 51.5074,
        "longitude" -> -0.1278
      )

      val result = json.as[Option[GeoLoc]](using GeoLoc.geoLocJsonReads).get

      result.latitude mustBe (51.5074 +- 0.1)
      result.longitude mustBe (-0.1278 +- 0.1)
    }

    "return None for coordinates outside the UK bounds" in {
      val json = Json.obj(
        "latitude" -> -33.8688,
        "longitude" -> 151.2093
      )

      json.validate[Option[GeoLoc]](using GeoLoc.geoLocJsonReads) mustBe
        JsSuccess(None)
    }

    "return None when latitude is null" in {
      val json = Json.obj(
        "latitude" -> JsNull,
        "longitude" -> -0.1278
      )

      json.validate[Option[GeoLoc]](using GeoLoc.geoLocJsonReads) mustBe
        JsSuccess(None)
    }

    "return None when longitude is null" in {
      val json = Json.obj(
        "latitude" -> 51.5074,
        "longitude" -> JsNull
      )

      json.validate[Option[GeoLoc]](using GeoLoc.geoLocJsonReads) mustBe
        JsSuccess(None)
    }
  }
}