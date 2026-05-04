package models

import play.api.mvc.PathBindable

enum FuelType(val displayText: String) {

  case B7_PREMIUM extends FuelType("B7 premium")
  case B7_STANDARD extends FuelType("B7 standard")
  case B10 extends FuelType("B10")
  case HVO extends FuelType("HVO")
  case E10 extends FuelType("Petrol E10")
  case E5 extends FuelType("Petrol E5")
}

object FuelType {
  given CanEqual[FuelType, FuelType] = CanEqual.derived
  
  given PathBindable[FuelType] with {

    @SuppressWarnings(Array("org.wartremover.warts.ToString"))
    override def bind(
                       key: String,
                       value: String
                     ): Either[String, FuelType] = {

      FuelType.values
        .find(_.toString.equalsIgnoreCase(value))
        .toRight(s"Invalid fuel type: $value")
    }

    override def unbind(
                         key: String,
                         fuelType: FuelType
                       ): String =
      s"$fuelType"
  }
}