package models

final case class SearchByPostcodeViewModel(
                                            fuelStationWithPrices: Seq[FuelStationWithPrices], 
                                            centrePostcode: String, 
                                            centreLocation: GeoLoc,
                                            radius: Double,
                                            fuelType: FuelType)
