package models

class FuelStationWithPricesSpec extends testUtils.BaseSpec {

  val location: FuelStationLocation = fakeFuelStationLocation(
    location = Some(GeoLoc(51.5074, -0.1278)) // London
  )

  val station: FuelStationWithPrices = fakeFuelStationWithPrices(
    nodeId = "nodeId",
    location = location,
    fuelTypes = List("E10"),
    fuelPrices = Seq.empty
  )

  "distanceFromCentre" must {

    "return 0 when the centre coincides with the station's own location" in {
      val centre = location.location.get

      station.distanceFromCentre(centre) mustBe Some(0.0)
    }

    "correctly forward centre latitude and longitude (not swapped) to the geodesic calculation" in {
      // Manchester — known, well-documented WGS84 geodesic distance from London (~51.5074,-0.1278)
      // is approximately 262km. If lat/lon were swapped in distanceFromCentre, this would produce
      // a wildly different (or NaN/out-of-range) result instead.
      val manchester = GeoLoc(53.4808, -2.2426)

      val distance = station.distanceFromCentre(manchester).get

      distance mustBe (262000.0 +- 3000.0) // metres, approximate — sanity check, not an exact fixture
    }
  }
}