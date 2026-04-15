package models

enum FuelType(val displayText: String) {

  case B7_PREMIUM extends FuelType("B7 premium")
  case B7_STANDARD extends FuelType("B7 premium")
  case B10 extends FuelType("B10")
  case HVO extends FuelType("HVO")
  case E10 extends FuelType("Petrol E10")
  case E5 extends FuelType("Petrol E5")
}