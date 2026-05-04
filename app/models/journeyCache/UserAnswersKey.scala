package models.journeyCache

import models.forms.*
import play.api.i18n.Messages
import play.api.libs.json.*
import play.api.mvc.Call

enum UserAnswersKey[A <: UserAnswersItem](
    val page: Call,
    val requirement: ItemRequirements,
    val journeyId: JourneyId,
    val index: Int = 0,
    val checkYourAnswerWrites: Option[Messages => OWrites[A]] = None // optional format for check your answers page.
)(using val format: OFormat[A]) { // format used to serialize/deserialize A. Also used as default if checkYourAnswerFormat is None.
  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  def writeUserAnswersItemAsJson(value: UserAnswersItem): JsValue =
    format.writes(value.asInstanceOf[A])

  def readUserAnswersItemFromJson(json: JsValue): JsResult[UserAnswersItem] =
    format.reads(json)

  case ChoosePostcodeQuestion
      extends UserAnswersKey[PostcodeForm](
        page = controllers.routes.SearchByPostcodeController.showPostcodeForm(),
        requirement = ItemRequirements.Always(),
        journeyId = JourneyId.SearByPostcode,
        index = 1,
      )(using Json.format[PostcodeForm])

  case ChooseFuelTypeQuestion
    extends UserAnswersKey[FuelTypeForm](
      page = controllers.routes.SearchByPostcodeController.showFuelTypeForm(),
      requirement = ItemRequirements.Always(),
      journeyId = JourneyId.SearByPostcode,
      index = 2,
    )(using FuelTypeForm.oFormats)

  case ChooseRadiusQuestion
    extends UserAnswersKey[RadiusForm](
      page = controllers.routes.SearchByPostcodeController.showRadiusForm(),
      requirement = ItemRequirements.Always(),
      journeyId = JourneyId.SearByPostcode,
      index = 3,
    )(using Json.format[RadiusForm])

}
