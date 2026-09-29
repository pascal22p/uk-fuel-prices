package models

import play.api.libs.json.{Reads, JsPath}
import play.api.libs.functional.syntax.*


final case class GeoLoc(latitude: Double, longitude: Double)

object GeoLoc {
  private val UkMinLatitude = 49.8
  private val UkMaxLatitude = 59.0
  private val UkMinLongitude = -8.2
  private val UkMaxLongitude = 2.0

  private def isInUk(geoLoc: GeoLoc): Boolean =
    geoLoc.latitude >= UkMinLatitude &&
      geoLoc.latitude <= UkMaxLatitude &&
      geoLoc.longitude >= UkMinLongitude &&
      geoLoc.longitude <= UkMaxLongitude
      
  val geoLocJsonReads: Reads[Option[GeoLoc]] = (
    (JsPath \ "latitude").readNullable[Double] and
      (JsPath \ "longitude").readNullable[Double]
    ).tupled.map {
    case (Some(lat), Some(lon)) if isInUk(GeoLoc(lat, lon)) =>
      Some(GeoLoc(lat, lon))
    case _ =>
      None
  }
}