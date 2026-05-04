package models.forms

import models.journeyCache.UserAnswersItem
import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText}
import models.FuelType
import play.api.libs.json.{Format, JsError, JsString, JsSuccess, Json, OFormat, Reads, Writes}

final case class FuelTypeForm(fuelType: FuelType) extends UserAnswersItem

object FuelTypeForm {
  def unapply(u: FuelTypeForm): Option[(FuelType)] = Some((u.fuelType))

  @SuppressWarnings(Array("org.wartremover.warts.EnumValueOf"))
  val fuelTypeForm: Form[FuelTypeForm] = Form(
    mapping(
      "fuelType"  -> nonEmptyText
        .verifying("error.invalid.fuelType", formFuelTypeValue => FuelType.values.exists(fuelType => formFuelTypeValue == s"$fuelType"))
        .transform(s => FuelType.valueOf(s), (f: FuelType) => s"$f")
    )(FuelTypeForm.apply)(FuelTypeForm.unapply)
  )
  
  val formats = Format(
    Reads {
      case JsString(value) =>
        FuelType.values
          .find(fuelTypeValue => s"$fuelTypeValue" == value)
          .map(JsSuccess(_))
          .getOrElse(JsError(s"Invalid FuelType: $value"))

      case _ =>
        JsError("FuelType must be a string")
    },
    Writes(fuelType => JsString(s"$fuelType"))
  )

  val oFormats: OFormat[FuelTypeForm] = {
    given Format[FuelType] = formats
    Json.format[FuelTypeForm]
  }
}
