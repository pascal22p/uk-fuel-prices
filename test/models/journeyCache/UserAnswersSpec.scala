package models.journeyCache

import models.FuelType.E10
import models.{AuthenticatedRequest, Session, SessionData, UserData}
import models.forms.*
import models.journeyCache.UserAnswersKey.*
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{AnyContentAsEmpty, Call}
import play.api.test.FakeRequest
import testUtils.BaseSpec

import java.time.LocalDateTime

class UserAnswersSpec extends BaseSpec {

  implicit val request: AuthenticatedRequest[AnyContentAsEmpty.type] =
    AuthenticatedRequest(
      FakeRequest(),
      Session("", SessionData(Some(UserData(0, "", "", true))), LocalDateTime.now())
    )

  val requiredKey: UserAnswersKey[PostcodeForm] = ChoosePostcodeQuestion
  val journey: JourneyId                                   = JourneyId.SearByPostcode

  case class TestItem(value: String) extends UserAnswersItem
  object TestItem {
    implicit val format: OFormat[TestItem] = Json.format[TestItem]
  }

  "UserAmswers.getItem" must {
    "get the item as instance of A" in {
      val record = PostcodeForm("postcode")

      val sut = UserAnswers(Map(ChoosePostcodeQuestion -> record))

      sut.getItem(ChoosePostcodeQuestion) mustBe record
    }

    "UserAnswers.getOptionalItem" must {
      "return Some when present" in {
        val ua = UserAnswers(Map(requiredKey -> PostcodeForm("postcode")))

        ua.getOptionalItem(requiredKey).map(_.postcode) mustBe Some("postcode")
      }

      "return None when missing" in {
        val ua = UserAnswers(Map.empty)

        ua.getOptionalItem(requiredKey) mustBe None
      }
    }

    "UserAnswers.upsert" must {
      "insert or replace an item" in {
        val ua = UserAnswers(Map.empty)
          .upsert(requiredKey, PostcodeForm("x"))
          .upsert(requiredKey, PostcodeForm("y"))

        ua.getItem(requiredKey).postcode mustBe "y"
      }
    }

    "UserAnswers.validated" must {

      "redirect when a required item is missing" in {
        val ua = UserAnswers(Map.empty)

        ua.validated(journey) mustBe Left(Call("GET", "/search/postcode"))
      }

      "keep required items and removed optional ones when valid" in {
        val ua = UserAnswers(
          Map(
            ChoosePostcodeQuestion -> PostcodeForm("x"),
            ChooseFuelTypeQuestion -> FuelTypeForm(E10),
            ChooseRadiusQuestion   -> RadiusForm(10.0)
          )
        )

        val expected = Set(
          ChoosePostcodeQuestion,
          ChooseFuelTypeQuestion,
          ChooseRadiusQuestion
        )

        ua.validated(journey).map(_.data.keySet) mustBe
          Right(expected)
      }
    }

    "UserAnswers.flattenByKey" must {
      "flatten simple values" in {
        val ua = UserAnswers(
          Map(requiredKey -> PostcodeForm("postcode"))
        )

        implicit def messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
        implicit lazy val messages: Messages  = MessagesImpl(Lang("en"), messagesApi)

        val result = ua.flattenByKey(journey)

        result(requiredKey) mustBe Map("postcode" -> "postcode")
      }
    }
  }
}
