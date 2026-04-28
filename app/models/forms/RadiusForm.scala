package models.forms

import models.journeyCache.UserAnswersItem
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.data.Forms.of
import play.api.data.format.Formats.doubleFormat

final case class RadiusForm(radiusInMiles: Double) extends UserAnswersItem

object RadiusForm {
  def unapply(u: RadiusForm): Option[(Double)] = Some((u.radiusInMiles))

  val radiusForm: Form[RadiusForm] = Form(
    mapping(
      "radius"  -> of(using doubleFormat)
    )(RadiusForm.apply)(RadiusForm.unapply)
  )
}
