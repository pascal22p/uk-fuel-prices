package models.forms

import models.journeyCache.UserAnswersItem
import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText}

import java.util.Locale

final case class PostcodeForm(postcode: String) extends UserAnswersItem

object PostcodeForm {
  def unapply(u: PostcodeForm): Option[(String)] = Some((u.postcode))

  // https://assets.publishing.service.gov.uk/government/uploads/system/uploads/attachment_data/file/488478/Bulk_Data_Transfer_-_additional_validation_valid_from_12_November_2015.pdf
  private val ukPostcodeRegex =
    """^([Gg][Ii][Rr] ?0[Aa]{2})|((([A-Za-z][0-9]{1,2})|(([A-Za-z][A-Ha-hJ-Yj-y][0-9]{1,2})|(([AZa-z][0-9][A-Za-z])|([A-Za-z][A-Ha-hJ-Yj-y][0-9]?[A-Za-z])))) ?[0-9][A-Za-z]{2})$""".r

  val postcodeForm: Form[PostcodeForm] = Form(
    mapping(
      "postcode"  -> nonEmptyText
        .transform[String](_.toUpperCase(Locale.ENGLISH).trim, identity)
        .verifying("error.invalid.postcode", p => ukPostcodeRegex.matches(p))
    )(PostcodeForm.apply)(PostcodeForm.unapply)
  )
}
