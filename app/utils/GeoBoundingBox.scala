package utils

final case class GeoBoundingBox(
                        minLat: Double,
                        maxLat: Double,
                        minLon: Double,
                        maxLon: Double
                      )

object GeoBoundingBox {

  def fromRadius(
                  latitude: Double,
                  longitude: Double,
                  radiusKm: Double
                ): GeoBoundingBox = {

    val latDelta = radiusKm / 111.32

    val lonDelta =
      radiusKm / (111.32 * math.cos(math.toRadians(latitude)))

    GeoBoundingBox(
      minLat = latitude - latDelta,
      maxLat = latitude + latDelta,
      minLon = longitude - lonDelta,
      maxLon = longitude + lonDelta
    )
  }
}
